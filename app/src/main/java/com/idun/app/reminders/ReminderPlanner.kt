package com.idun.app.reminders

import com.idun.app.data.PlanEntry
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure logic for "which planned meals deserve a reminder, and when."
 *
 * Kept free of Android/AlarmManager so it can be unit-tested: [ReminderScheduler]
 * loads the plan and feeds it here, then commits the result to AlarmManager.
 *
 * A meal earns a reminder when it is still in the future (trigger time strictly
 * after now) and has not already been marked eaten. The trigger fires
 * [leadMinutes] before the meal's wall-clock time, resolved in the device zone
 * — past meals and already-eaten ones are dropped so we never buzz for
 * something that has come and gone.
 */
object ReminderPlanner {

    data class MealReminder(
        val entryId: Long,
        val recipeId: String,
        val triggerAtMillis: Long,
        val mealAtMillis: Long,
    )

    fun due(
        entries: List<PlanEntry>,
        nowMillis: Long,
        leadMinutes: Int,
        zone: ZoneId,
    ): List<MealReminder> {
        val leadMillis = leadMinutes.coerceAtLeast(0) * 60_000L
        return entries.asSequence()
            .filter { it.eatenAtMs == null }
            .mapNotNull { entry ->
                val mealAt = mealInstant(entry, zone) ?: return@mapNotNull null
                val triggerAt = mealAt - leadMillis
                if (triggerAt <= nowMillis) null
                else MealReminder(entry.id, entry.recipeId, triggerAt, mealAt)
            }
            .sortedBy { it.triggerAtMillis }
            .toList()
    }

    private fun mealInstant(entry: PlanEntry, zone: ZoneId): Long? = try {
        LocalDate.parse(entry.dateIso)
            .atTime(entry.timeMinutes / 60, entry.timeMinutes % 60)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        null // malformed date_iso — skip rather than crash the scheduler
    }
}
