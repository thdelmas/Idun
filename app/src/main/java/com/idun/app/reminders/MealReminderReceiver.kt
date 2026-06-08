package com.idun.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when a meal's alarm goes off and posts the notification. The payload
 * (recipe name + time label) is baked into the alarm intent at schedule time so
 * this receiver stays trivial — no DB or asset reads on the alarm path.
 */
class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(ReminderNotifier.EXTRA_ENTRY_ID, -1L)
        if (entryId < 0) return
        val recipeName = intent.getStringExtra(ReminderNotifier.EXTRA_RECIPE_NAME).orEmpty()
        val timeLabel = intent.getStringExtra(ReminderNotifier.EXTRA_TIME_LABEL).orEmpty()
        ReminderNotifier.show(context, entryId, recipeName, timeLabel)
    }
}
