package com.idun.app.billing

/**
 * The free/paid boundary for the planning suite, in one place.
 *
 * Idun monetizes as freemium with a one-time unlock (see docs/MONETIZATION.md).
 * The planning layer is the premium tier, **soft-gated**: free users get a real
 * taste of planning, and the unlock removes the limits and adds the automation.
 *
 * This class is pure policy — no Android, no billing, no database. Callers pass
 * the entitlement flag (from [Entitlement]) and any counts; this returns the
 * decision. Keeping the boundary here means tuning it — or proving it in tests —
 * never touches the activities, the same single-source-of-truth idiom
 * `RecipeSource` uses for recipe sets.
 *
 * **Boundary (reconciled to the real UI, 2026-06-09):** the planner is a rolling
 * 7-day window from today with no multi-week navigation, so the brief's
 * "current week vs plan-ahead" line doesn't map. The faithful translation:
 *
 * | Surface | Free (taste) | Premium |
 * |---|---|---|
 * | Plan entries | up to [FREE_UPCOMING_PLAN_ENTRIES] upcoming | unlimited |
 * | Routines (recurring) | locked | full |
 * | Household + attendees | self only | members + per-meal attendees |
 * | Planned-meal reminders | locked | full |
 *
 * The plan-entry cap is the *soft* part (you plan freely, then hit a ceiling);
 * the automation/coordination features are the premium depth. The cap counts
 * only *upcoming* entries, so capacity renews as days pass — a free user is
 * never permanently wedged.
 */
class PlanningLimits(private val entitled: Boolean) {

    /** Whether a premium-only feature is unlocked. Free → false for every one. */
    fun allows(feature: PremiumFeature): Boolean = entitled

    /**
     * Whether another plan entry may be created. Premium is unlimited; free is
     * capped at [FREE_UPCOMING_PLAN_ENTRIES] *upcoming* entries (the caller
     * supplies that count — typically future, uneaten entries).
     */
    fun canAddPlanEntry(currentUpcomingCount: Int): Boolean =
        entitled || currentUpcomingCount < FREE_UPCOMING_PLAN_ENTRIES

    /**
     * How many more free plan entries remain before the cap, for messaging
     * ("2 left on the free plan"). [Int.MAX_VALUE] when entitled (no cap).
     */
    fun remainingFreePlanEntries(currentUpcomingCount: Int): Int =
        if (entitled) Int.MAX_VALUE
        else (FREE_UPCOMING_PLAN_ENTRIES - currentUpcomingCount).coerceAtLeast(0)

    companion object {
        /**
         * Free-tier cap on upcoming plan entries. Tunable — the pricing brief
         * calls these numbers a starting point, not a lock. Sized to let a free
         * user genuinely plan a few days before the upsell, not just one meal.
         */
        const val FREE_UPCOMING_PLAN_ENTRIES = 6
    }
}

/** Premium-only capabilities, used as the key for gating and upsell messaging. */
enum class PremiumFeature {
    ROUTINES,
    HOUSEHOLD,
    REMINDERS,
}
