package com.idun.app.reminders

import com.idun.app.data.PlanEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderPlannerTest {

    private val zone: ZoneId = ZoneOffset.UTC

    /** Epoch millis for a wall-clock date/time in [zone]. */
    private fun at(dateIso: String, minutes: Int): Long =
        LocalDate.parse(dateIso).atTime(minutes / 60, minutes % 60)
            .atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: Long, dateIso: String, minutes: Int, eaten: Long? = null) =
        PlanEntry(id = id, dateIso = dateIso, timeMinutes = minutes, recipeId = "r$id", eatenAtMs = eaten)

    @Test
    fun `future meal fires lead minutes before meal time`() {
        val now = at("2026-06-10", 8 * 60) // 08:00
        val meal = entry(1, "2026-06-10", 12 * 60) // 12:00
        val out = ReminderPlanner.due(listOf(meal), now, leadMinutes = 30, zone = zone)
        assertEquals(1, out.size)
        assertEquals(at("2026-06-10", 12 * 60 - 30), out.first().triggerAtMillis)
        assertEquals(at("2026-06-10", 12 * 60), out.first().mealAtMillis)
    }

    @Test
    fun `meal already past its trigger is dropped`() {
        val now = at("2026-06-10", 12 * 60)
        // trigger would be 11:30, already behind now
        val out = ReminderPlanner.due(listOf(entry(1, "2026-06-10", 12 * 60)), now, 30, zone)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `eaten meal is dropped even if still in the future`() {
        val now = at("2026-06-10", 8 * 60)
        val eaten = entry(1, "2026-06-10", 12 * 60, eaten = now)
        assertTrue(ReminderPlanner.due(listOf(eaten), now, 30, zone).isEmpty())
    }

    @Test
    fun `zero lead fires exactly at meal time`() {
        val now = at("2026-06-10", 8 * 60)
        val out = ReminderPlanner.due(listOf(entry(1, "2026-06-10", 12 * 60)), now, 0, zone)
        assertEquals(at("2026-06-10", 12 * 60), out.first().triggerAtMillis)
    }

    @Test
    fun `results are sorted by trigger time`() {
        val now = at("2026-06-10", 6 * 60)
        val later = entry(1, "2026-06-11", 9 * 60)
        val sooner = entry(2, "2026-06-10", 20 * 60)
        val out = ReminderPlanner.due(listOf(later, sooner), now, 15, zone)
        assertEquals(listOf(2L, 1L), out.map { it.entryId })
    }

    @Test
    fun `malformed date is skipped not crashed`() {
        val now = at("2026-06-10", 8 * 60)
        val bad = PlanEntry(id = 9, dateIso = "not-a-date", timeMinutes = 600, recipeId = "x")
        val good = entry(1, "2026-06-10", 12 * 60)
        val out = ReminderPlanner.due(listOf(bad, good), now, 30, zone)
        assertEquals(listOf(1L), out.map { it.entryId })
    }
}
