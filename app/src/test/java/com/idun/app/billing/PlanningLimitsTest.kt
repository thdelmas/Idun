package com.idun.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningLimitsTest {

    private val free = PlanningLimits(entitled = false)
    private val premium = PlanningLimits(entitled = true)

    @Test
    fun `premium unlocks every premium feature`() {
        for (feature in PremiumFeature.values()) {
            assertTrue(feature.name, premium.allows(feature))
        }
    }

    @Test
    fun `free locks every premium feature`() {
        for (feature in PremiumFeature.values()) {
            assertFalse(feature.name, free.allows(feature))
        }
    }

    @Test
    fun `free may add plan entries up to but not at the cap`() {
        val cap = PlanningLimits.FREE_UPCOMING_PLAN_ENTRIES
        assertTrue(free.canAddPlanEntry(currentUpcomingCount = 0))
        assertTrue(free.canAddPlanEntry(currentUpcomingCount = cap - 1))
        assertFalse(free.canAddPlanEntry(currentUpcomingCount = cap))
        assertFalse(free.canAddPlanEntry(currentUpcomingCount = cap + 5))
    }

    @Test
    fun `premium may always add plan entries`() {
        assertTrue(premium.canAddPlanEntry(currentUpcomingCount = 0))
        assertTrue(premium.canAddPlanEntry(currentUpcomingCount = PlanningLimits.FREE_UPCOMING_PLAN_ENTRIES))
        assertTrue(premium.canAddPlanEntry(currentUpcomingCount = 10_000))
    }

    @Test
    fun `remaining free entries counts down and floors at zero`() {
        val cap = PlanningLimits.FREE_UPCOMING_PLAN_ENTRIES
        assertEquals(cap, free.remainingFreePlanEntries(currentUpcomingCount = 0))
        assertEquals(1, free.remainingFreePlanEntries(currentUpcomingCount = cap - 1))
        assertEquals(0, free.remainingFreePlanEntries(currentUpcomingCount = cap))
        assertEquals(0, free.remainingFreePlanEntries(currentUpcomingCount = cap + 3))
    }

    @Test
    fun `premium has effectively unlimited remaining entries`() {
        assertEquals(Int.MAX_VALUE, premium.remainingFreePlanEntries(currentUpcomingCount = 100))
    }
}
