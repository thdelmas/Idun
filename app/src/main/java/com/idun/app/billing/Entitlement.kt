package com.idun.app.billing

import android.content.Context

/**
 * Local cache of the one-time premium unlock.
 *
 * Idun is local-first (no cloud, no auth, no sync), so entitlement lives on the
 * device in the shared "IdunPrefs" file — the same store [ThemeSettings] and
 * [ReminderSettings] use. The *source of truth* is Google Play Billing: when the
 * billing layer lands it will verify purchases via `queryPurchasesAsync` and
 * write [premiumUnlocked] here; this class is just the fast, offline read the
 * UI consults. Play accounts are the payment rail, not an Idun login — the
 * no-auth lock holds.
 *
 * Default is **locked** (false). Note: nothing consults this yet — the
 * [PlanningLimits] gate is not wired into the planner/routine/household/reminder
 * entry points until the billing layer and the upsell screen exist, so today's
 * shipped features stay fully available. Wiring + the default flip happen
 * together at the paid launch (see docs/MONETIZATION.md §A).
 */
class Entitlement(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var premiumUnlocked: Boolean
        get() = prefs.getBoolean(KEY_PREMIUM, false)
        set(value) = prefs.edit().putBoolean(KEY_PREMIUM, value).apply()

    /** The planning free/paid boundary for the current entitlement state. */
    fun planningLimits(): PlanningLimits = PlanningLimits(premiumUnlocked)

    companion object {
        private const val PREFS_NAME = "IdunPrefs"
        private const val KEY_PREMIUM = "premium_unlocked"
    }
}
