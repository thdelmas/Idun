package com.idun.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Play Billing rail for Idun's one-time premium unlock (see
 * docs/MONETIZATION.md §A). It owns the *online* half of entitlement — connect,
 * load the product, launch the purchase, restore prior purchases — and on a
 * confirmed purchase writes the flag into [Entitlement], the offline cache that
 * everything else reads.
 *
 * **Separation of concerns:** gates never touch this class. A planner/routine/
 * household/reminder check reads `Entitlement(context).premiumUnlocked` (sync,
 * offline) and asks [PlanningLimits]. `BillingManager` exists only on the screens
 * that *buy* or *restore* — typically the upsell screen and one long-lived owner
 * (Application or the main Activity). Keeping it off the hot path means the app
 * stays fully usable with Play unavailable, consistent with the local-first lock.
 *
 * **Lifecycle:** hold one instance at a lifecycle-aware [scope] (e.g. an
 * Activity's `lifecycleScope`), call [start] in `onStart`/`onResume` and [end]
 * in `onDestroy`. [start] is idempotent and also triggers a restore, so a user
 * who paid on another device is re-granted on next launch.
 *
 * **Security note:** Play already gates purchase integrity, and a local-first app
 * has no server to do the recommended server-side signature check. Acknowledged,
 * PURCHASED state from `queryPurchasesAsync` is treated as source of truth. If a
 * stronger client-side signature verification is wanted later, add it in
 * [handlePurchase] using the Play Console licensing public key (an account
 * action; not available until the product is published).
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val appContext = context.applicationContext
    private val entitlement = Entitlement(appContext)

    private val _premiumUnlocked = MutableStateFlow(entitlement.premiumUnlocked)
    /** Reactive entitlement signal for the upsell UI to observe the unlock land. */
    val premiumUnlocked: StateFlow<Boolean> = _premiumUnlocked.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var reconnectAttempts = 0

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                Unit // user backed out of the Play sheet — not an error
            else ->
                Log.w(TAG, "Purchase update failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private val client = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Connect to Play (if needed), then restore purchases and load the product. */
    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectAttempts = 0
                    refresh()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode}")
                }
            }

            override fun onBillingServiceDisconnected() {
                if (reconnectAttempts++ < MAX_RECONNECTS) start()
            }
        })
    }

    fun end() {
        if (client.isReady) client.endConnection()
    }

    /**
     * Launch the Play purchase UI for the premium unlock. Returns false if the
     * product isn't loaded yet (offline, or not configured in Play Console) — the
     * caller should surface a "try again in a moment" message; a reconnect is
     * kicked off so a retry can succeed.
     */
    fun launchPurchase(activity: Activity): Boolean {
        val details = productDetails ?: run {
            Log.w(TAG, "launchPurchase before product loaded")
            start()
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode ==
            BillingClient.BillingResponseCode.OK
    }

    private fun refresh() {
        scope.launch { queryPurchases() }
        scope.launch { loadProduct() }
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            productDetails = result.productDetailsList?.firstOrNull()
            if (productDetails == null) {
                Log.w(TAG, "Product '$PREMIUM_PRODUCT_ID' not found — publish it in Play Console")
            }
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.responseCode}")
        }
    }

    /** Restore: authoritative re-sync of entitlement from Play's owned-purchases. */
    private suspend fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
            .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            // Transient failure (e.g. offline): never revoke on a failed query.
            return
        }
        // Acknowledge anything owned-but-unacknowledged (e.g. bought elsewhere)...
        result.purchasesList.forEach { handlePurchase(it) }
        // ...then let Play's owned set be the source of truth, so a refund revokes.
        val owns = result.purchasesList.any { purchase ->
            purchase.products.contains(PREMIUM_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        setEntitled(owns)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return // PENDING: wait
        if (!purchase.products.contains(PREMIUM_PRODUCT_ID)) return
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            // Mandatory within 3 days or Play auto-refunds.
            client.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Acknowledge failed: ${result.responseCode}")
                }
            }
        }
        setEntitled(true)
    }

    private fun setEntitled(value: Boolean) {
        entitlement.premiumUnlocked = value
        _premiumUnlocked.value = value
    }

    companion object {
        private const val TAG = "BillingManager"
        private const val MAX_RECONNECTS = 3

        /**
         * Product ID for the one-time premium unlock. MUST match the
         * non-consumable in-app product configured in Play Console (account
         * action — see docs/MONETIZATION.md §A). Until that product is published,
         * the purchase flow returns "item unavailable".
         */
        const val PREMIUM_PRODUCT_ID = "idun_premium_unlock"
    }
}
