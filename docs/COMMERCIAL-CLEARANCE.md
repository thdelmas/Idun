# Idun — Commercial Clearance Plan (recipe re-derivation + relabel)

**Status:** ready to execute. **Owner:** delegated agent. **Created:** 2026-06-08.
**Goal:** make Idun legally clear to sell as a paid app by removing third-party content/brand dependencies from the recipe corpus.

> This is a self-contained execution brief. An agent should be able to run it end-to-end
> without re-deriving the strategy. Read it fully before touching files.

---

## Why this exists

A commercial-rights audit (2026-06-08) found Idun **not clear to sell as-is**, with two blockers,
**both stemming from leaning on other people's branded content** rather than owning our own:

1. **Longo recipe prose is copied near-verbatim** from valterlongo.com (the KB source admits it in
   its "Method-step provenance" note). Plus 2 Blueprint recipes carry Johnson's method verbatim.
2. **"Blueprint" and "Longo" are used as in-app product labels** — trademark / false-endorsement risk
   in a *paid* product.

### The legal frame (not legal advice)

Recipe rights have **three layers**, and rewording only touches one:

| Layer | Protected? | What clears it |
|---|---|---|
| **Ingredient lists + functional method** | **No** — facts/procedures (17 USC §102(b)) | already free |
| **The dish itself** (Panzanella, minestrone…) | **No** — public-domain culinary heritage Longo *compiled*, not created | already free; our strongest ground |
| **Creative prose / expression** | **Yes** | rewriting in original words |
| **Compilation (selection+arrangement) + EU sui generis DATABASE right** (Dir 96/9/EC, applies in Spain) | **Yes, thinly** — survives rewording | **re-curating our own selection** + dishes being traditional |

**Conclusion: RE-DERIVE, don't reword.** Rebuild each recipe from traditional / public-domain
technique, keep only the *dish idea* (free) and the *research inspiration* (uncopyrightable),
attribute but stay independent. A synonym-swap of Longo's prose is **not** sufficient — it leaves
the compilation/database exposure and reads as a derivative.

### Permission emails (insurance, not a gate)

Permission/blessing emails were **sent 2026-06-08** to Create Cures Foundation (Longo) and the
Blueprint team (Johnson). **Do not wait on replies.** The re-derivation ships clean regardless of
their answer; a positive reply only widens what we're allowed to do.

---

## Scope

- **Re-derive 39 "Mediterranean" (Longo) recipes** — `steps` (EN) + `steps_es` (ES).
- **Re-derive 2 "Protocol" (Blueprint) recipes** that carry verbatim method: **#10 Porridge** and
  **#14 Smoothie**. (Audit found the other 12 Blueprint methods were locally authored — spot-check,
  but they're likely fine.)
- **Relabel** both recipe-set display names off the person/brand names.
- **Keep** the Credits-screen attribution + "independent / not affiliated or endorsed" disclaimer.

---

## The re-derivation method (voice spec)

Rewrite each method into **original, functional technique language** — the way any cook would
describe a public-domain dish. The output must be:

- **Faithful to the actual ingredients and dish** (same recipe, same result) — don't invent or drop ingredients.
- **Terse, food-first, Open Roots voice** — numbered imperative steps. No headnotes, no marketing, no story.
- **Free of source-specific phrasing** — strip distinctive expression. Not synonym-swapped: *re-derived*.
- **No health claims in steps** (no "for longevity", "anti-inflammatory", etc. — steps are cooking only).
- **Bilingual:** write EN first, then translate the **new** EN into ES (`steps_es`). Never translate the old prose.

### Gold-standard worked example (#1 Panzanella)

**BEFORE — Longo verbatim (the liability):**
> 1. Soak the bread in a bowl of cold water — it must be Tuscan bread, ideally wood-oven baked and a bit stale; let sit 30 minutes in the refrigerator to reconstitute.
> 2. Squeeze out the water; repeat 3 or 4 times.
> 3. Place in a large serving bowl and cover with all the other ingredients.
> 4. Dress with salt, oil, and a little vinegar; mix well.
> 5. Let rest for half an hour; taste and adjust oil or vinegar before serving.

**AFTER — re-derived (ship this):**
> 1. Tear the stale bread into rough chunks and soak in cold water until softened, 20–30 minutes.
> 2. Press firmly to wring out the water; repeat once or twice until damp but not dripping, then crumble into a large bowl.
> 3. Add the tomatoes, cucumber, celery, onion, and basil.
> 4. Dress with olive oil, a splash of vinegar, and salt; toss to coat.
> 5. Rest 30 minutes at room temperature, then taste and adjust oil, vinegar, or salt before serving.

**AFTER (ES — translated from the new EN):**
> 1. Trocea el pan duro en pedazos irregulares y remójalo en agua fría hasta que se ablande, 20–30 minutos.
> 2. Aprieta con firmeza para escurrir; repite una o dos veces hasta que quede húmedo pero sin gotear, y desmenúzalo en un bol grande.
> 3. Añade los tomates, el pepino, el apio, la cebolla y la albahaca.
> 4. Aliña con aceite de oliva, un chorrito de vinagre y sal; mezcla bien.
> 5. Deja reposar 30 minutos a temperatura ambiente; prueba y ajusta de aceite, vinagre o sal antes de servir.

Note what changed: the Longo-specific expression ("*must* be Tuscan bread, ideally wood-oven baked",
"3 or 4 times", "in the refrigerator") is **gone** — not synonym-swapped. Ingredients (facts) stay.

---

## Files to edit (exact list)

Recipes are **KB-first** (see [CLAUDE.md](../CLAUDE.md) seed-data discipline) and there is **no
recipe md→JSON generator** — so edit the KB source **and** the app JSON in tandem, keeping schema parity.

### 1. KB source of truth (rewrite here first)
- `Miam/miam-knowledge-base/docs/life/longo-recipes.md` — re-derive all 39 `**Method:**` blocks.
  **Also replace** the "Method-step provenance (added 2026-05-24)" disclaimer (it currently admits
  verbatim copying) with a re-derivation note, e.g.: *"Methods are re-derived from traditional
  technique for each dish; dishes are public-domain Mediterranean cooking, cited to Longo's research
  as inspiration only."*
- `Miam/miam-knowledge-base/docs/life/blueprint-recipes.md` — re-derive the #10 + #14 methods;
  spot-check the other 12 are locally authored.

### 2. App JSON (keep schema identical: `id, source, name_en, servings, ingredients, steps, name_es, steps_es`)
- `app/src/main/assets/recipes_longo.json` — replace `steps` + `steps_es` for all 39. **Do not touch**
  `ingredients`, `servings`, `name_*`, `id`.
- `app/src/main/assets/recipes_blueprint.json` — replace `steps` + `steps_es` for #10 + #14.

### 3. Relabel (display strings only — keep internal JSON `source` keys `"longo"`/`"blueprint"` so the
source→label mapping in code doesn't break; verify no UI surface prints the raw `source` value)
- In **all four** locales (`res/values/`, `values-es/`, `values-ca/`, `values-fr/`):
  - `source_blueprint`: `Blueprint` → **`Protocol`** (localize: Protocolo / Protocol / Protocole)
  - `source_longo`: `Longo` → **`Mediterranean`** (localize: Mediterránea / Mediterrània / Méditerranéenne)
- **Keep** the `credits_johnson_*` and Longo credit strings (factual attribution by name = nominative
  fair use). Keep the "independent project, not affiliated/endorsed" disclaimer string.

### 4. Housekeeping
- Update [CLAUDE.md](../CLAUDE.md): the "Both recipe sets equal-weight (Blueprint / Longo)" line →
  "Protocol / Mediterranean"; ensure no guidance says "preserve upstream wording".
- **Bump `versionCode`** (recipe-content change).

---

## Do NOT change

- Ingredient lists, quantities, servings, recipe IDs (facts — keep; changing them breaks the
  `foods_catalog` ingredient→Food mapping and ShoppingListAggregator).
- Food images / `foods.json` pedagogy (audited clear — original + properly caveated).
- Credits-screen attribution to Johnson + Longo, or the disclaimer (these *protect* us).

---

## Acceptance criteria (validate before done)

1. **No residual verbatim phrasing.** For each of the 41, diff the new method against the old
   valterlongo.com / Blueprint wording — no shared distinctive phrases (signature tells:
   "it must be Tuscan bread", "repeat 3 or 4 times", etc.). When in doubt, re-derive harder.
2. **JSON parses** and schema is unchanged (same keys, same `ingredients`/`servings`/`id`).
3. **EN/ES parity:** every edited recipe has both `steps` and `steps_es`, same step count.
4. **Tests pass:** the 53-recipe parse-validation + `ShoppingListAggregator` unit tests + `make assemble` (or `./gradlew assembleDebug`).
5. **Relabel complete:** no user-facing "Blueprint"/"Longo" as a *set label* remains in any of the 4
   locales (attribution-by-name in Credits is fine and intended).
6. **KB ⇄ JSON consistency:** KB markdown and JSON methods match.

---

## After clearance — monetization roadmap (separate workstream, do NOT start until cleared)

Idun is the *strategic* paid build (vs SoulRadio = the fast launch). ~2–3 months out because it
needs billing it doesn't have yet:

- **Play Billing + subscription UX** (the real gap — no billing infra today).
- **Privacy policy + EULA**, Play Store listing assets, content rating.
- **Pricing model:** subscription (recurring, on-brand with Open Roots) vs one-time. Decide at billing time.
- **Bios companion approval:** `com.idun.app` still `PENDING_APPROVAL` until the paired Bios update lands (non-blocking, fire-and-forget).

Brand frame: Idun is the **Open Roots** longevity-recipe product — *Mía's own replicable playbook*,
inspired by Longo & Johnson, not a reprint of either. The re-derivation above is what makes that true.
