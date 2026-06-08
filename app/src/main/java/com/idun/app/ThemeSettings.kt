package com.idun.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Appearance preference: Light / Dark / follow-system. Stored in the shared
 * "IdunPrefs" file (same store BiosClient and ReminderSettings use).
 *
 * Default is [Mode.SYSTEM] — Idun's home palette is Light, but a fresh install
 * follows the OS so a phone in dark mode lands in Idun's warm dark without the
 * user hunting for a toggle. The chosen mode is applied process-wide via
 * [AppCompatDelegate.setDefaultNightMode]; call [apply] from [IdunApp.onCreate]
 * so the choice survives across launches and before the first activity inflates.
 */
class ThemeSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: Mode
        get() = Mode.fromKey(prefs.getString(KEY_MODE, null))
        set(value) = prefs.edit().putString(KEY_MODE, value.key).apply()

    /** Push the stored mode into AppCompatDelegate. Recreates running activities. */
    fun apply() {
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    enum class Mode(val key: String, val nightMode: Int) {
        SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

        companion object {
            fun fromKey(key: String?): Mode = entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }

    companion object {
        private const val PREFS_NAME = "IdunPrefs"
        private const val KEY_MODE = "theme_mode"
    }
}
