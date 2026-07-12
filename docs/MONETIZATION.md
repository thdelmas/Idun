# Idun — Monetization Groundwork (paid-build scoping brief)

**Status:** decided — pricing locked, implementation pending. **Owner:** delegated agent.
**Created:** 2026-06-09. **Decisions made:** 2026-06-09.
**Goal:** turn Idun into a sellable paid Android app. Commercial clearance has landed (v0.3.1), so this
workstream — the one named as "the real gap" in [COMMERCIAL-CLEARANCE.md](COMMERCIAL-CLEARANCE.md) — is
now **unblocked**.

> Self-contained brief. Graduates the "After clearance — monetization roadmap" stub from the clearance
> doc into an ordered plan with the open decisions surfaced. **Not legal advice.** Read fully before
> touching billing code; two product/pricing decisions gate the implementation.

---

## Doctrine alignment

This plan is a *down-reference* of the portfolio
[monetization-doctrine](../../Miam/miam-knowledge-base/docs/monetization-doctrine.md) — it **inherits**
the philosophy rather than re-deriving it.

- **Archetype: 2 — Consumer product** (doctrine §3, where Idun is named in the Archetype-2 row). Idun is
  an honest app the *individual* pays to use; here, **freemium + one-time unlock** (below).
- **Values inherited (universal, non-negotiable):** no ads, no surveillance, no dark patterns, no selling
  the user; free-to-the-person where possible; owner/user decides. The local-first "collects nothing"
  posture (§B) is this doctrine made literal — a selling point, not a cost.
- **Mechanism follows nature (§2).** Idun is a recipe/longevity *tool you buy*, so it earns as a consumer
  Pro app. It does **not** carry a data-consent / federated-learning rail — that is **Archetype 1
  (instrument / B2B)**, the lane of **Bios / Fil / W2F**, where an *institution* pays and the person
  consents free of charge. §2 forbids cargo-culting that model onto a recipe app: **don't.**
- **Privacy is never a price.** Sovereignty (§1) means privacy is the default for *every* user, free —
  never a paid tier, never a discount-for-data lever. The unlock gates *planning depth*, never privacy.
- **Not (currently) open-core.** This is a Play-Store paid build. If F-Droid / de-Googled distribution is
  pursued later, that is the doctrine's open-core delivery modifier (free Apache code + paid Pro) — a
  **separate, unmade decision**; record and decide it before acting, don't drift into it.
- **Funnel:** Open Roots (content brand) → Idun, per the doctrine's content/audience meta-layer.

---

## Where we are

- **Clearance:** done. Recipes re-derived, set labels de-branded, attribution + disclaimer in place.
- **Billing infra:** **none.** Zero billing code in the tree today. This is the whole gap.
- **Brand frame:** Idun is the **Open Roots** longevity-recipe product — the strategic paid build
  (vs SoulRadio = the fast launch).

## Hard constraint from the locked design

Idun is **local-first: no cloud, no auth, no sync** (CLAUDE.md). That shapes billing:

- **Entitlement is stored locally**, not on a server. Google Play Billing purchases are tied to the
  user's *Google* account at the Play layer — that does **not** violate Idun's "no auth" lock, because
  Idun itself still has no account system; Play is the payment rail, not an app login.
- **Restore-purchases** works by re-querying Play (`queryPurchasesAsync`) on launch, not by syncing our
  own server. No backend to build — consistent with the local-first posture.

---

## The two decisions that gate everything — ✅ DECIDED 2026-06-09

- **Decision 1 — Pricing model:** **Freemium + one-time unlock.** Free install + free core; a single
  one-time IAP (non-consumable) unlocks the premium tier. Delivered as free-on-store, *not* paid-upfront.
- **Decision 2 — Free/paid line:** **the planning suite is premium, soft-gated.** Free users get a
  *taste* of planning; depth and automation are behind the unlock. Spec below.

The original analysis that led here is retained for the record.

### Decision 1 — Pricing model

| Model | Fits Idun because | Friction |
|---|---|---|
| **One-time unlock** | local-first app has *no recurring server cost to fund*; a recipe/longevity app reads as a "tool you buy", not a service; simplest billing (one non-consumable, no churn/renewal UX) | caps revenue per user; no recurring income |
| **Subscription** | on-brand with Open Roots recurring model; funds ongoing recipe/translation editorial | harder to justify with no cloud/ongoing service; renewal + grace-period + lapse UX; churn |
| **Freemium (one-time unlock of premium tier)** | lets users try the core, pay to unlock depth | requires a clean free/paid line (see Decision 2) |

**Lean:** for a local-first, no-server app whose value is a curated reference corpus, **one-time unlock
(freemium)** is the most coherent and lowest-friction. Subscription is defensible only if you commit to
*continuous* editorial that users are paying to keep receiving. Decide explicitly.

### Decision 2 — The free / paid line (only if freemium)

A local-first app has to pick what's gated. Candidate boundaries (pick one, or combine):

- **By recipe set:** one set free, the rest paid. (Tension with the "both sets equal-weight" UI lock —
  would need care, or applies only to set #3+.)
- **By feature depth:** shopping-list + browsing free; **planner / routines / household** paid. Clean
  story ("the app is free; the planning suite is premium"), and those are the v1.1 features.
- **By corpus size:** a free sample of recipes, full corpus paid.

**Lean:** gate **the planning layer** (planner, routines, household/attendees) — it's a discrete,
clearly-premium capability bundle, it doesn't touch the equal-weight recipe-set lock, and the core
shopping-list lead feature stays free as a funnel.

### Soft-gate spec (DECIDED: soft gate)

Free users **taste** planning; the unlock removes the limits and adds the automation. Proposed
boundary (tunable before launch — these numbers are the starting point, not a lock):

| Capability | Free (taste) | Premium (unlock) |
|---|---|---|
| **Plan entries** | up to **`FREE_UPCOMING_PLAN_ENTRIES`** (6) upcoming | unlimited |
| **Routines** (recurring templates) | — locked | full |
| **Household + attendees** | self only | add members + per-entry attendees |
| **Planned-meal reminders** | — locked | full |
| Recipes, shopping list, Learn, Bios writes, dark mode | **free** | (same) |

Rationale: "plan freely, automate with routines, coordinate your household, and get reminders" — a
real taste of planning at a natural high-intent upgrade moment, while the depth that engaged users
want is the paid value.

> **Boundary reconciled to the real UI (2026-06-09).** The planner is a *rolling 7-day window from
> today* with no multi-week navigation (`PlanningActivity.DAYS_AHEAD = 7`), so the original
> "current week vs plan-ahead" line had nothing to gate. The faithful translation of the *taste*
> intent is a cap on **upcoming** plan entries (renews as days pass, so a free user is never
> permanently wedged), plus the automation/coordination features as premium depth. Encoded in
> `billing/PlanningLimits` (pure, unit-tested); the number is tunable.

**Enforcement layer (new work this implies):** a single `entitlement` check + a thin
`PlanningLimits` gate (is-current-week? / is-self-only? / routines-and-reminders-allowed?) consulted
at the planner, routine, household, and reminder entry points. Keep it centralized so the boundary
is one place to tune, mirroring how `RecipeSource` centralizes the set logic.

---

## Workstream (ordered; start after Decisions 1–2)

### A. Play Billing integration (code)
- [x] Add Play Billing Library dependency. *(`com.android.billingclient:billing-ktx:7.1.1`, 2026-06-09.)*
- [x] `billing/BillingManager` wrapper: connect + retry, `queryProductDetails`, `launchBillingFlow`,
      purchase listener, **acknowledge** (mandatory within 3 days or Play auto-refunds), and
      `queryPurchasesAsync` on launch for restore. *(Done 2026-06-09; writes [Entitlement]; exposes a
      `premiumUnlocked` StateFlow for the upsell UI. Product ID = `BillingManager.PREMIUM_PRODUCT_ID`
      = `idun_premium_unlock` — must match Play Console.)*
- [x] Local entitlement store — `billing/Entitlement` (mirrors `ReminderSettings`/`ThemeSettings` in the
      shared `IdunPrefs`); caches the verified flag, source of truth stays Play.
- [ ] **`PlanningLimits` gate** (centralized) consulted at the planner, routine, household, and reminder
      entry points — enforces the soft-gate spec above (current-week / self-only / routines+reminders).
- [ ] Entitlement gate wiring + upsell screen at the soft-gate boundaries. Gate UX: a clean upsell at
      the high-intent moment (plan-ahead / add-routine / add-member / enable-reminder), not a nag.
      i18n the paywall strings ×4 locales from commit 1, per the i18n lock.
- [ ] One non-consumable product ID configured in Play Console (account action — not code).

### B. Legal + store assets (mostly non-code)
- [ ] **Privacy policy** — *easy here*: local-first means "Idun collects nothing, stores nothing off
      device; Bios writes are local and opt-in." Still required by Play even when minimal.
- [ ] **EULA / terms.**
- [ ] **Play Data-safety form** — declare no data collection (true, and a selling point).
- [ ] **Store listing:** screenshots (light + warm-dark), description, content rating questionnaire.

### C. Loose ends
- [ ] **Bios companion approval:** `com.idun.app` is `PENDING_APPROVAL` until the paired Bios update
      lands — **non-blocking**, fire-and-forget, ship without it.

---

## Critical path

```
Decision 1 (pricing) ─┐
                      ├─→ A. Billing code ──┐
Decision 2 (gate) ────┘                     ├─→ closed-testing release → production
B. Legal + listing (parallel, no code dep) ─┘
```

Legal/listing can proceed in parallel the moment Decision 1 is made; billing code needs both decisions.

---

## Decisions — resolved

1. ✅ **Pricing model** — **freemium + one-time unlock** (free install, single non-consumable IAP).
2. ✅ **Free/paid line** — **planning suite is premium, soft-gated** (free = current-week planning,
   self only; unlock = plan-ahead + routines + household/attendees + reminders). See soft-gate spec.

Both gating decisions are made; everything in §A/§B is now mechanical. Only tunable detail left is the
exact free-taste numbers (week window vs entry count), which can be adjusted before launch without
reopening the model.

## Next step

Implementation (§A). Suggested order: `BillingManager` + local entitlement → `PlanningLimits` gate →
upsell screen + paywall strings ×4 locales → Play Console product + closed testing. Legal/listing (§B)
can run in parallel now that the model is fixed.
