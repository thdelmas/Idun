package com.idun.app.bios

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

/**
 * Companion writer that forwards meal-intake events from Idun to Bios via the
 * BiosHealthProvider companion URI:
 *
 *   content://com.bios.app.health/companion/{metric_type}
 *
 * Best-effort and fire-and-forget. Three failure modes are all silent:
 *  - Bios not installed (resolver returns null type)
 *  - User has not opted in (settings flag off)
 *  - Bios rejects the metric type (SecurityException — paired Bios update lands later
 *    to whitelist com.idun.app in CompanionContract.PACKAGES)
 *
 * Idun keeps working identically whether or not any of those succeed.
 *
 * Pattern mirrored from Smokeless's BiosClient (Phase 2.1, currently the only
 * specialist shipping Bios writes). Same shape, same silent-failure semantics.
 */
class BiosClient(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isAvailable: Boolean
        get() = try {
            context.contentResolver.getType(BASE_URI) != null
        } catch (_: Exception) {
            false
        }

    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun status(): Status = when {
        !isAvailable -> Status.NOT_INSTALLED
        !isEnabled -> Status.NOT_ENABLED
        else -> Status.CONNECTED
    }

    val lastPushOutcome: LastPushOutcome
        get() = prefs.getString(KEY_LAST_PUSH_OUTCOME, null)
            ?.let { runCatching { LastPushOutcome.valueOf(it) }.getOrNull() }
            ?: LastPushOutcome.NEVER_TRIED

    private fun recordOutcome(outcome: LastPushOutcome) {
        prefs.edit().putString(KEY_LAST_PUSH_OUTCOME, outcome.name).apply()
    }

    /**
     * Logs a meal-intake event. `servings` defaults to 1.0 — the canonical
     * "ate one portion" event. Caller can pass a fractional value for
     * partial portions (half a smoothie, two helpings of soup, etc.).
     */
    fun pushMealIntake(timestamp: Long, servings: Double = 1.0): Boolean =
        push(METRIC_MEAL_INTAKE, timestamp, servings)

    private fun push(metricType: String, timestamp: Long, value: Double): Boolean {
        if (!isEnabled) return false
        if (!isAvailable) return false
        val uri = COMPANION_URI.buildUpon().appendPath(metricType).build()
        val values = ContentValues().apply {
            put("value", value)
            put("timestamp", timestamp)
        }
        return try {
            context.contentResolver.insert(uri, values)
            recordOutcome(LastPushOutcome.OK)
            true
        } catch (e: SecurityException) {
            val msg = e.message.orEmpty()
            val outcome = if (msg.contains("not approved", ignoreCase = true)) {
                LastPushOutcome.PENDING_APPROVAL
            } else {
                LastPushOutcome.OTHER_FAILURE
            }
            recordOutcome(outcome)
            Log.d(TAG, "Bios rejected $metricType: $msg (outcome=$outcome)")
            false
        } catch (e: Exception) {
            recordOutcome(LastPushOutcome.OTHER_FAILURE)
            Log.d(TAG, "Bios push failed for $metricType: ${e.message}")
            false
        }
    }

    enum class Status { NOT_INSTALLED, NOT_ENABLED, CONNECTED }

    enum class LastPushOutcome {
        NEVER_TRIED,
        OK,
        PENDING_APPROVAL,
        OTHER_FAILURE,
    }

    companion object {
        private const val TAG = "IdunBiosClient"
        private const val PREFS_NAME = "IdunPrefs"
        private const val KEY_ENABLED = "biosIntegrationEnabled"
        private const val KEY_LAST_PUSH_OUTCOME = "biosLastPushOutcome"

        const val BIOS_PACKAGE = "com.bios.app"
        const val BIOS_EXTRA_NAVIGATE_TO_COMPANIONS = "navigate_to_companions"

        const val METRIC_MEAL_INTAKE = "meal_intake"

        private val BASE_URI: Uri = Uri.parse("content://com.bios.app.health")
        private val COMPANION_URI: Uri = BASE_URI.buildUpon().appendPath("companion").build()
    }
}
