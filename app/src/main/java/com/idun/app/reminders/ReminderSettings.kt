package com.idun.app.reminders

import android.content.Context

/**
 * User preferences for meal reminders, stored in the shared "IdunPrefs" file
 * (same store BiosClient uses). Off by default — reminders are opt-in, and
 * enabling them is what triggers the notification-permission request.
 *
 * [leadMinutes] is how far before a planned meal the nudge fires; the picker
 * offers a few sensible steps and the value is clamped on read so a corrupt
 * pref can never schedule something absurd.
 */
class ReminderSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES).coerceIn(0, MAX_LEAD_MINUTES)
        set(value) = prefs.edit().putInt(KEY_LEAD_MINUTES, value.coerceIn(0, MAX_LEAD_MINUTES)).apply()

    companion object {
        private const val PREFS_NAME = "IdunPrefs"
        private const val KEY_ENABLED = "reminders_enabled"
        private const val KEY_LEAD_MINUTES = "reminders_lead_minutes"

        const val DEFAULT_LEAD_MINUTES = 30
        const val MAX_LEAD_MINUTES = 24 * 60

        /** Lead-time options offered in Settings, in minutes. */
        val LEAD_OPTIONS = listOf(0, 15, 30, 60, 120)
    }
}
