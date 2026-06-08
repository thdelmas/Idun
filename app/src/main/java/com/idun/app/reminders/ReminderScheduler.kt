package com.idun.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.idun.app.data.IdunDatabase
import com.idun.app.data.RecipeRepository
import com.idun.app.data.displayName
import com.idun.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Reconciles AlarmManager with the current plan: one exact alarm per future,
 * not-yet-eaten meal, firing [ReminderSettings.leadMinutes] beforehand.
 *
 * [reschedule] is the single entry point — call it after any change that can
 * affect the plan (add/edit/remove/mark-eaten, routine apply, settings toggle,
 * device boot). It is idempotent: it cancels everything it scheduled last time
 * (tracked by entry id in prefs) and re-lays the alarms from scratch, so edits
 * and deletions can't leave orphan alarms behind.
 *
 * Exact alarms degrade gracefully — if the OS withholds the exact-alarm
 * permission (API 31+), we fall back to an inexact windowed alarm rather than
 * dropping the reminder.
 */
class ReminderScheduler(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val settings = ReminderSettings(context)

    /** Horizon for which we pre-arm alarms; re-run extends it as days roll by. */
    private val horizonDays = 14L

    suspend fun reschedule() = withContext(Dispatchers.IO) {
        cancelTracked()
        if (!settings.enabled || alarmManager == null) {
            saveTracked(emptySet())
            return@withContext
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val db = IdunDatabase.get(context)
        val entries = db.planDao().inRange(today.toString(), today.plusDays(horizonDays).toString())

        val names = RecipeRepository(context).all().associate { it.id to it.displayName() }
        val due = ReminderPlanner.due(
            entries = entries,
            nowMillis = System.currentTimeMillis(),
            leadMinutes = settings.leadMinutes,
            zone = zone,
        )

        for (reminder in due) {
            val recipeName = names[reminder.recipeId]
                ?: context.getString(R.string.planning_missing_recipe)
            val timeLabel = Instant.ofEpochMilli(reminder.mealAtMillis)
                .atZone(zone).toLocalTime()
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
            schedule(reminder, recipeName, timeLabel)
        }
        saveTracked(due.map { it.entryId }.toSet())
    }

    private fun schedule(reminder: ReminderPlanner.MealReminder, recipeName: String, timeLabel: String) {
        val intent = Intent(context, MealReminderReceiver::class.java).apply {
            putExtra(ReminderNotifier.EXTRA_ENTRY_ID, reminder.entryId)
            putExtra(ReminderNotifier.EXTRA_RECIPE_NAME, recipeName)
            putExtra(ReminderNotifier.EXTRA_TIME_LABEL, timeLabel)
        }
        val pending = broadcast(reminder.entryId, intent)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager!!.canScheduleExactAlarms()
        if (canExact) {
            alarmManager!!.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
        } else {
            // No exact-alarm permission — a windowed inexact alarm still nudges, just looser.
            alarmManager!!.setWindow(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                5 * 60_000L,
                pending,
            )
        }
    }

    /** Cancel every alarm we recorded scheduling last time. */
    private fun cancelTracked() {
        val mgr = alarmManager ?: return
        for (id in trackedIds()) {
            val intent = Intent(context, MealReminderReceiver::class.java)
            mgr.cancel(broadcast(id, intent))
        }
    }

    private fun broadcast(entryId: Long, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            entryId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun trackedIds(): Set<Long> =
        prefs.getStringSet(KEY_TRACKED, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private fun saveTracked(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_TRACKED, ids.map { it.toString() }.toSet()).apply()
    }

    companion object {
        private const val PREFS_NAME = "IdunPrefs"
        private const val KEY_TRACKED = "reminders_scheduled_ids"
    }
}
