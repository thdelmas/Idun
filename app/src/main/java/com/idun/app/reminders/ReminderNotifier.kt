package com.idun.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.idun.app.PlanningActivity
import com.idun.app.R

/**
 * Builds and posts the meal-reminder notification, and owns its channel.
 *
 * The channel is created idempotently from [IdunApp.onCreate] so it exists
 * before any alarm fires (Application.onCreate runs on every process start,
 * including the one that delivers a broadcast). Posting is best-effort and
 * mirrors the BiosClient stance: if the user revoked POST_NOTIFICATIONS we
 * silently no-op rather than crash the receiver.
 *
 * Tapping a reminder opens the planner. The notification id is the plan entry's
 * id, so a re-scheduled or stale reminder for the same meal replaces rather
 * than stacks.
 */
object ReminderNotifier {

    const val CHANNEL_ID = "meal_reminders"

    const val EXTRA_ENTRY_ID = "entry_id"
    const val EXTRA_RECIPE_NAME = "recipe_name"
    const val EXTRA_TIME_LABEL = "time_label"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, entryId: Long, recipeName: String, timeLabel: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val tapIntent = Intent(context, PlanningActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            entryId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_plan)
            .setContentTitle(recipeName)
            .setContentText(context.getString(R.string.reminder_body, timeLabel))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(entryId.toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the guard and the call — ignore.
        }
    }
}
