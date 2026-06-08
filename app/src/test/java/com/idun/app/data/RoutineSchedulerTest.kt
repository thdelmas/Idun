package com.idun.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RoutineSchedulerTest {

    // 2026-06-08 is a Monday → ISO weekday 1; the week runs Mon..Sun.
    private val monday = LocalDate.of(2026, 6, 8)
    private val sunday = monday.plusDays(6)

    private fun routine(weekday: Int, time: Int, recipe: String = "r$weekday") =
        Routine(name = "n", weekday = weekday, timeMinutes = time, recipeId = recipe)

    @Test
    fun `routine fills every matching weekday in the range`() {
        val out = RoutineScheduler.plan(
            routines = listOf(routine(weekday = 1, time = 8 * 60, recipe = "porridge")),
            existing = emptyList(),
            from = monday,
            to = sunday.plusDays(7), // two Mondays in the window
        )
        assertEquals(2, out.size)
        assertTrue(out.all { it.recipeId == "porridge" && it.timeMinutes == 8 * 60 })
        assertEquals(listOf("2026-06-08", "2026-06-15"), out.map { it.dateIso })
    }

    @Test
    fun `existing entry within the collision window blocks insertion`() {
        val existing = PlanEntry(dateIso = "2026-06-08", timeMinutes = 8 * 60 + 20, recipeId = "hand-picked")
        val out = RoutineScheduler.plan(
            routines = listOf(routine(weekday = 1, time = 8 * 60)),
            existing = listOf(existing),
            from = monday,
            to = sunday,
        )
        assertTrue("routine should yield to the hand-planned meal", out.isEmpty())
    }

    @Test
    fun `existing entry outside the window does not block`() {
        val existing = PlanEntry(dateIso = "2026-06-08", timeMinutes = 13 * 60, recipeId = "lunch")
        val out = RoutineScheduler.plan(
            routines = listOf(routine(weekday = 1, time = 8 * 60)),
            existing = listOf(existing),
            from = monday,
            to = sunday,
        )
        assertEquals(1, out.size)
        assertEquals(8 * 60, out.first().timeMinutes)
    }

    @Test
    fun `two near routines on the same day do not double up`() {
        val out = RoutineScheduler.plan(
            routines = listOf(
                routine(weekday = 1, time = 8 * 60, recipe = "a"),
                routine(weekday = 1, time = 8 * 60 + 15, recipe = "b"),
            ),
            existing = emptyList(),
            from = monday,
            to = monday,
        )
        assertEquals(1, out.size)
        assertEquals("a", out.first().recipeId) // earlier time wins, second collides
    }

    @Test
    fun `routines exactly at the window edge collide`() {
        val out = RoutineScheduler.plan(
            routines = listOf(
                routine(weekday = 1, time = 8 * 60, recipe = "a"),
                routine(weekday = 1, time = 8 * 60 + RoutineScheduler.COLLISION_WINDOW_MINUTES, recipe = "b"),
            ),
            existing = emptyList(),
            from = monday,
            to = monday,
        )
        assertEquals(1, out.size)
    }

    @Test
    fun `default servings hook is applied and floored at one`() {
        val out = RoutineScheduler.plan(
            routines = listOf(routine(weekday = 1, time = 8 * 60, recipe = "soup")),
            existing = emptyList(),
            from = monday,
            to = monday,
            defaultServings = { if (it == "soup") 4 else 0 },
        )
        assertEquals(4, out.first().servings)
    }

    @Test
    fun `empty routines or inverted range yields nothing`() {
        assertTrue(RoutineScheduler.plan(emptyList(), emptyList(), monday, sunday).isEmpty())
        assertTrue(RoutineScheduler.plan(listOf(routine(1, 480)), emptyList(), sunday, monday).isEmpty())
    }
}
