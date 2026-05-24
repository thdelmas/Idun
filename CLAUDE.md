# Idun — agent guidance

Longevity meal app, Bios-ecosystem specialist. See [README.md](README.md) for product framing.

## Identity

- **Package:** `com.idun.app`
- **Repo:** `git@github.com-thdelmas:thdelmas/Idun.git`
- **Local path:** `/home/mia/Idun`
- **Pattern:** Bios-ecosystem specialist (Smokeless / Fil / W2F / Virgil / SoulRadio family)
- **Platform:** Android-only Kotlin

## Locked design constraints

- **No cloud, no auth, no sync.** Local-first. Bios is the only external integration.
- **i18n from commit 1:** EN / ES / CA / IT in `res/values*/strings.xml`. Don't hardcode UI strings.
- **Both recipe sets equal-weight in UI.** Blueprint and Longo are surfaced equally; don't lead with one.
- **Shopping-list generation is the v1 lead feature.** Not cost estimation, not recipe authoring, not community.
- **Planning is the v1.1 layer.** Week-view planner backed by a `plan_entry` table; the shopping list learns to aggregate from a date range alongside the existing multi-select path. Routines (recurring templates) and household/guest social land on top of the same plan table.
- **PreuJust integration is out of scope for v1.** Cost estimation lands when PreuJust-as-service revives (decision 2026-08-11).

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

Never edit the JSON in isolation — the KB is source of truth.

## Out-of-scope until further notice

- Cost estimation (PreuJust dependency)
- Cheapest-store comparison
- Recipe authoring / community submissions
- Recipe import from URLs (schema.org)
- Cloud sync / accounts
- Nutrition scoring (Nutri-Score, NOVA) — deferred to v2

Notifications / reminders are now in-scope for Idun (decision 2026-05-24, overrides the earlier "Smokeless owns reminder patterns" lock). Reminder wiring for planned meals lands in a follow-up; for now the planning data layer ships without notifications.
