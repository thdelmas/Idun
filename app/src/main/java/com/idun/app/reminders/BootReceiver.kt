package com.idun.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms don't survive a reboot, so re-arm them once the device finishes
 * booting. Uses goAsync() to keep the process alive while the scheduler reads
 * the plan off the main thread.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler(appContext).reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
