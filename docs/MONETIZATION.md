# Idun — Monetization Groundwork (paid-build scoping brief)

**Status:** scoping — decisions open, no code yet. **Owner:** delegated agent. **Created:** 2026-06-09.
**Goal:** turn Idun into a sellable paid Android app. Commercial clearance has landed (v0.3.1), so this
workstream — the one named as "the real gap" in [COMMERCIAL-CLEARANCE.md](COMMERCIAL-CLEARANCE.md) — is
now **unblocked**.

> Self-contained brief. Graduates the "After clearance — monetization roadmap" stub from the clearance
> doc into an ordered plan with the open decisions surfaced. **Not legal advice.** Read fully before
> touching billing code; two product/pricing decisions gate the implementation.

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

## The two decisions that gate everything

These are **yours to make** — implementation can't start without them.

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

---

## Workstream (ordered; start after Decisions 1–2)

### A. Play Billing integration (code)
- [ ] Add Play Billing Library dependency.
- [ ] `billing/BillingManager` wrapper: connect + retry, `queryProductDetails`, `launchBillingFlow`,
      purchase listener, **acknowledge** (mandatory within 3 days or Play auto-refunds), and
      `queryPurchasesAsync` on launch for restore.
- [ ] Local entitlement store (e.g. a `BillingSettings` mirroring `ReminderSettings`/`ThemeSettings`)
      — cache the verified entitlement; source of truth stays Play.
- [ ] Entitlement gate at the chosen free/paid boundary (Decision 2). Gate UX: a clean upsell screen,
      not a nag. i18n the paywall strings ×4 locales from commit 1, per the i18n lock.
- [ ] Product IDs configured in Play Console (account action — not code).

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

## Open decisions (blocking)

1. **Pricing model** — one-time unlock vs subscription vs freemium. *(Lean: freemium one-time.)*
2. **Free/paid line** — what's gated. *(Lean: the planning layer.)*

Everything in §A/§B is mechanical once these two are set.
