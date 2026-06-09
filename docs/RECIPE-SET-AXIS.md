# Idun — Recipe-Set Axis Decision (organize by regime, not by figure)

**Status:** decided, scaffolding in progress. **Owner:** delegated agent. **Created:** 2026-06-09.
**Decision:** Idun's recipe sets are organized along a **dietary-regime** axis (e.g. *Protocol*,
*Mediterranean*, *Whole-Food Plant-Based*), **not** along a *named-longevity-figure* axis. Named
figures are credited where one exists, on the Credits screen only — but **credit-by-person is no
longer load-bearing**: a set may legitimately have no single author.

> Self-contained record. This generalizes the relabel principle from
> [COMMERCIAL-CLEARANCE.md](COMMERCIAL-CLEARANCE.md) into a standing design axis, and it gates how
> the third (and later) recipe sets get chosen. Read it before adding a set.

---

## Why this exists

We were about to add a third recipe set and got stuck on what to *call* it. Every candidate label
felt wrong, and the reason turned out to be structural, not cosmetic: we were implicitly organizing
the collection by **named figure** (Johnson, Longo) — but that axis only survives on one screen.

**The user-facing labels are already regimes.** The commercial-clearance work (v0.3.1) renamed the
sets to **"Protocol"** (ex-Blueprint/Johnson) and **"Mediterranean"** (ex-Longo). Those are
*pattern* names. The people now live only on the Credits screen (nominative fair use). So the app
*already presents* a regime axis; the figure axis is a vestige. This decision finishes that shift
and makes the model coherent.

### The two axes, compared

| | **Figure axis** (rejected) | **Regime axis** (adopted) |
|---|---|---|
| What a set *is* | one person's authored protocol | a dietary pattern / tradition |
| New set requires | a creditable named author | nothing — a regime needs no figurehead |
| Credits screen | mandatory person per set | optional; cite sources where they exist |
| Editorial story | "curated by specific obsessives" (sharp) | "the longevity-relevant regimes" (broader) |
| IP footing | inherits each figure's trademark/brand baggage | descriptive names = weak/unprotectable marks |
| Consistency w/ shipped labels | contradicts them | matches them |

---

## IP consequence (why the regime axis is the safer footing)

Recipe rights still have the layers from [COMMERCIAL-CLEARANCE.md](COMMERCIAL-CLEARANCE.md)
(facts/method free; dish public-domain; creative prose re-derived; compilation re-curated). The
regime axis adds one clarifying rule about the **label layer**:

| Label kind | Mark risk | Rule |
|---|---|---|
| Descriptive regime ("Mediterranean", "Whole-Food Plant-Based", "Okinawan") | low — descriptive/generic terms are weak or unprotectable | **usable as a set label** |
| Registered brand ("Blue Zones®", "Daily Dozen") | high — registered / competing-app overlap | **never a set label**; de-brand it |
| Personal name ("Bryan Johnson", "Michael Greger") | nominative fair use | **Credits attribution only**, never the label |

This is the rule we *already* follow for Blueprint/Longo — the regime axis just promotes it from a
per-source workaround to the design principle. Net effect: adding sets becomes IP-additive rather
than IP-fraught.

---

## What this unblocks (corpus candidates)

- **Whole-Food Plant-Based** — clean descriptive regime. Draws pedagogy from Michael Greger's work,
  *cited as a source* on Credits, recipes re-derived. Because the *label* is the generic regime (not
  "Daily Dozen" and not branded around Greger), the trademark / competing-app exposure that a
  Greger-branded set would carry largely evaporates. **Guardrails:** never use "Daily Dozen" as a
  label; do **not** replicate the Daily-Dozen checklist/tracker UI (that is the competing app's
  distinctive feature — Idun being a shopping-list app makes this easy to avoid).
- **Okinawan** — a single coherent regional tradition (not the whole "Blue Zones" meta-category,
  which is a registered mark and five unrelated cuisines). Public-domain heritage cuisine: lowest IP
  friction of any candidate. Under the figure axis its blocker was "no person to credit" — which this
  decision makes a non-issue. Ties into the existing eating-window feature via *hara hachi bu*.

Rejected as **labels**: "Blue Zones" (registered mark, incoherent as one set), "Daily Dozen"
(registered, competing app), "MIND" (a cognition *pattern*, not a recipe corpus; fails distinctiveness).

---

## Curation guardrail (the cost, and how we pay it)

The regime axis trades editorial sharpness for breadth and IP-safety. Two named obsessives feel
*opinionated*; an open list of diets feels like a commodity app. We recover the edge by **capping
the count and curating hard** — these are *the* longevity-relevant regimes, not a directory.

- **Ship trio:** Protocol · Mediterranean · Whole-Food Plant-Based.
- **Natural fourth (when wanted):** Okinawan.
- **Not a directory.** Resist set #5+ unless it earns a distinct, defensible place.

---

## Consequences for the code (what the scaffold does)

1. **`creditInfo()` becomes optional per source.** Today it is a *total* function (every
   `RecipeSource` → a person). It becomes nullable / cited-sources-capable so a regime with no
   headline author renders a sources card instead of a person card. The data-driven Credits refactor
   (2026-06-09) is the substrate that makes this a small change, not a rework.
2. **Adding a set stays a compile-time checklist** — the exhaustive `when`s over `RecipeSource`
   (`labelRes()`, `creditInfo()`) flag every site. See
   [docs/research/longevity-corpus-landscape.md](research/longevity-corpus-landscape.md) §3 for the
   full add-a-set procedure.
3. **No recipe content changes in the scaffold step.** New sets ship structurally first (enum value,
   repo load-list, strings ×4, credits entry, stub asset); the re-derived KB-first recipes
   (EN canon + ES/CA/FR) are a deliberate later editorial pass under the
   [COMMERCIAL-CLEARANCE.md](COMMERCIAL-CLEARANCE.md) re-derivation method.

---

## Not changing

- The two shipped sets' content, IDs, or labels (Protocol / Mediterranean stay).
- Credits attribution to Johnson + Longo, or the "independent / not affiliated" disclaimer.
- The KB-first seed-data discipline ([CLAUDE.md](../CLAUDE.md)).
