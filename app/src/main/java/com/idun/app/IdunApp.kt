package com.idun.app

import android.app.Application
import com.idun.app.reminders.ReminderNotifier

/**
 * Application entry point. Applies the saved appearance (Light/Dark/System)
 * before any activity inflates, and creates the meal-reminder notification
 * channel on every process start so it exists before any alarm or boot
 * broadcast fires.
 */
class IdunApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeSettings(this).apply()
        ReminderNotifier.ensureChannel(this)
    }
}
