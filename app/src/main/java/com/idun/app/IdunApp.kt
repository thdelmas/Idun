package com.idun.app

import android.app.Application
import com.idun.app.reminders.ReminderNotifier

/**
 * Application entry point. Creates the meal-reminder notification channel on
 * every process start so it exists before any alarm or boot broadcast fires.
 */
class IdunApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.ensureChannel(this)
    }
}
