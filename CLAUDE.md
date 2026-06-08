# Idun — agent guidance

Longevity meal app, Bios-ecosystem specialist. See [README.md](README.md) for product framing.

> **✅ Commercial clearance — landed (v0.3.1, 2026-06-08):** the recipe corpus has been
> **re-derived** off third-party copyright/brand dependencies and the set labels de-branded to
> **"Protocol"/"Mediterranean"**. Standing rules from that work: do **not** add verbatim upstream
> recipe prose, and never restore "Blueprint"/"Longo" as user-facing *set labels* (name attribution
> on Credits only). Record of the work: [docs/COMMERCIAL-CLEARANCE.md](docs/COMMERCIAL-CLEARANCE.md).

## Identity

- **Package:** `com.idun.app`
- **Repo:** `git@github.com-thdelmas:thdelmas/Idun.git`
- **Local path:** `/home/mia/Idun`
- **Pattern:** Bios-ecosystem specialist (Smokeless / Fil / W2F / Virgil / SoulRadio family)
- **Platform:** Android-only Kotlin

## Locked design constraints

- **No cloud, no auth, no sync.** Local-first. Bios is the only external integration.
- **i18n from commit 1:** EN / ES / CA / FR in `res/values*/strings.xml`. Don't hardcode UI strings. Recipe data (ingredient names, notes, cooking steps) is localized via parallel `*_es` / `*_ca` / `*_fr` fields in the JSON corpus, with English as the canonical fallback when a locale's translation hasn't landed yet. Translations land KB-first (see seed-data discipline). FR replaced IT on 2026-05-24.
- **Both recipe sets equal-weight in UI.** The two sets — **Protocol** (ex-"Blueprint") and **Mediterranean** (ex-"Longo") — are surfaced equally; don't lead with one. Set labels are de-branded for commercial clearance; Johnson/Longo remain credited by name on the Credits screen only (nominative fair use). See [docs/COMMERCIAL-CLEARANCE.md](docs/COMMERCIAL-CLEARANCE.md).
- **Shopping-list generation is the v1 lead feature.** Not cost estimation, not recipe authoring, not community.
- **Planning is the v1.1 layer.** Week-view planner backed by a `plan_entry` table; the shopping list learns to aggregate from a date range alongside the existing multi-select path. Routines (recurring templates) and household/guest social land on top of the same plan table.
- **No fixed meal slots.** A day holds 0..N meal entries at arbitrary times (`time_minutes` 0..1439), not breakfast / lunch / dinner. Longo's 12h-window guidance and Johnson's 1-2-meal patterns both need variable meal counts at arbitrary times — fixed slots would have made one of the two corpuses awkward to follow. The day card shows an eating-window summary when there are 2+ meals so the user can see their own time-restricted-eating pattern.
- **PreuJust integration is out of scope for v1.** Cost estimation lands when PreuJust-as-service revives (decision 2026-08-11).

## Design system

Idun follows the [Bios Ecosystem Design System](../Bios/docs/DESIGN_SYSTEM.md). Identity palette = Apple-red `#B33A3A` + W2F-cyan `#00BCD4` on parchment `#FAF6EE`. Light is the home palette; **dark mode is opt-in** (v0.7.0, Settings → Appearance → Light/Dark/System, persisted via `ThemeSettings`/`AppCompatDelegate`). Dark is a **warm** dark — espresso/dark-brown surfaces in `res/values-night/colors.xml` that retain the orchard warmth, **not** a navy/cockpit inversion (a cold reskin was rejected before for losing Idun's identity). Every theme color role resolves through a named `@color`, so the night-qualified palette adapts the single `Base.Theme.Idun` style as-is — keep it that way; don't fork the style per config. The W2F-cyan secondary is the ecosystem kinship marker — keep it. Don't invent color roles or component anatomy outside the canonical [design-tokens/](../Bios/docs/design-tokens/) 14-role set; raise it to the canonical doc instead.

## Bios integration

- Writes `meal_intake` events via `content://com.bios.app.health/companion/meal_intake`
- Fire-and-forget, best-effort. Three silent failure modes per Smokeless's `BiosClient`: Bios not installed, user opted out, Bios rejects metric.
- Idun keeps working identically whether or not Bios writes land.
- Companion whitelist entry in Bios (`CompanionContract.PACKAGES`) requires a paired Bios update; until then, `PENDING_APPROVAL` is the expected first-write outcome.

## Seed-data discipline

The 53 recipes in `app/src/main/assets/recipes_*.json` are mirrored from the Miam knowledge base:
- `Miam/miam-knowledge-base/docs/life/blueprint-recipes.md`
- `Miam/miam-knowledge-base/docs/life/longo-recipes.md`

When recipes are added, removed, or edited:
1. Update the canonical markdown in Miam KB first
2. Regenerate the JSON assets here
3. Bump versionCode

Never edit the JSON in isolation — the KB is source of truth. **Note:** there is no recipe md→JSON
generator (only i18n + foods scripts exist), so KB markdown and `recipes_*.json` must be edited in
tandem with schema parity. **For commercial clearance, recipe methods are being re-derived** (original
technique, not upstream prose) — see [docs/COMMERCIAL-CLEARANCE.md](docs/COMMERCIAL-CLEARANCE.md);
do not restore verbatim valterlongo.com / Blueprint wording into the KB or JSON.

### Ingredients pedagogy (Learn screen)

The 145 canonical food entries in `app/src/main/assets/foods.json` are likewise generated from a KB doc:
- `Miam/miam-knowledge-base/docs/life/ingredients-pedagogy.md`

Pipeline: `scripts/foods/build_catalog.py` defines the canonical food IDs, regex patterns, and category, and maps the 466 recipe ingredient lines to them (must remain at zero unmatched). It writes `app/src/main/assets/foods_catalog.json`, which ships as an asset and is consumed by `FoodResolver` at runtime to power the recipe-ingredient → Food-detail jump. `scripts/foods/extract_foods_json.py` parses the KB markdown into `foods.json`. The KB doc is source of truth; never edit `foods.json` or `foods_catalog.json` directly. Editorial register is health-adjacent — see the doc's "Editorial register" section before touching content (no medical-diagnosis framing, no RDA tables; cite Blueprint/Longo). Bump versionCode on edits.

Food images live at `app/src/main/assets/foods/images/<food_id>.jpg`, fetched by `scripts/foods/download_images.py` from Wikimedia Commons (preferred — every file there is free-licensed by policy) with a license-verified Wikipedia lead-image fallback. Provenance per image is recorded in `_manifest.json` alongside the JPGs. The Android side resolves `<id>.jpg` by convention and silently hides the ImageView when missing — no JSON pipeline change required, no runtime network. Re-run the script (idempotent) when foods are added; bump versionCode.

## Out-of-scope until further notice

- Cost estimation (PreuJust dependency)
- Cheapest-store comparison
- Recipe authoring / community submissions
- Recipe import from URLs (schema.org)
- Cloud sync / accounts
- Nutrition scoring (Nutri-Score, NOVA) — deferred to v2

Notifications / reminders are now in-scope for Idun (decision 2026-05-24, overrides the earlier "Smokeless owns reminder patterns" lock). Reminder wiring for planned meals lands in a follow-up; for now the planning data layer ships without notifications.
