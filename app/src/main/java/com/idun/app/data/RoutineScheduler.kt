package com.idun.app.data

import java.time.LocalDate
import kotlin.math.abs

/**
 * Applies recurring [Routine] templates onto the concrete plan.
 *
 * Pure and side-effect-free so it can be unit-tested without Room: the caller
 * loads the routines + existing entries, asks [plan] for the rows to insert,
 * then persists them. Walking a date range, each routine matching the day's ISO
 * weekday becomes a [PlanEntry] — unless that (date, time) is already occupied
 * within ±[COLLISION_WINDOW_MINUTES] by an existing entry or by another routine
 * inserted earlier in the same pass. That keeps "apply my usual week" from
 * stomping meals the user already planned by hand, and stops two near-identical
 * routines from doubling up.
 */
object RoutineScheduler {

    const val COLLISION_WINDOW_MINUTES = 30

    fun plan(
        routines: List<Routine>,
        existing: List<PlanEntry>,
        from: LocalDate,
        to: LocalDate,
        defaultServings: (recipeId: String) -> Int = { 1 },
    ): List<PlanEntry> {
        if (routines.isEmpty() || from.isAfter(to)) return emptyList()

        // Times already taken per ISO date — seeded with existing entries, then
        // grown as we insert so routines collide against each other too.
        val occupied = HashMap<String, MutableList<Int>>()
        for (e in existing) occupied.getOrPut(e.dateIso) { mutableListOf() }.add(e.timeMinutes)

        val byWeekday = routines.groupBy { it.weekday }
        val result = mutableListOf<PlanEntry>()

        var date = from
        while (!date.isAfter(to)) {
            val iso = date.toString()
            val slots = occupied.getOrPut(iso) { mutableListOf() }
            for (routine in byWeekday[date.dayOfWeek.value].orEmpty().sortedBy { it.timeMinutes }) {
                val clashes = slots.any { abs(it - routine.timeMinutes) <= COLLISION_WINDOW_MINUTES }
                if (clashes) continue
                result += PlanEntry(
                    dateIso = iso,
                    timeMinutes = routine.timeMinutes,
                    recipeId = routine.recipeId,
                    servings = defaultServings(routine.recipeId).coerceAtLeast(1),
                )
                slots += routine.timeMinutes
            }
            date = date.plusDays(1)
        }
        return result
    }
}
