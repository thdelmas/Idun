# Idun

Longevity meal app — Bios-ecosystem specialist. Named after the Norse goddess Iðunn who guarded the apples of immortality that the gods ate to stay young.

## What it does

- Browse 53 seed recipes: 14 from Bryan Johnson's Blueprint, 39 from Valter Longo's *Recipes of Longevity*
- Select recipes for a meal plan and generate a consolidated shopping list
- Log meals — writes `meal_intake` events to Bios via `content://com.bios.app.health/companion/meal_intake`
- i18n: EN / ES / CA / IT
- Local-first. No cloud, no auth, no sync.

## Architecture

Android-only Kotlin. Sibling to Smokeless, Fil, W2F, Virgil, SoulRadio in the Bios ecosystem. Reads from Bios ContentProvider (sleep/wake anchors), writes companion events fire-and-forget.

A future v2 will integrate with PreuJust-as-service (price oracle, revival decision 2026-08-11) for ingredient cost-estimation and cheapest-store comparison. **Not in v1.**

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── recipes_blueprint.json
│   └── recipes_longo.json
├── java/com/idun/app/
│   ├── MainActivity.kt
│   ├── bios/BiosClient.kt
│   ├── data/Recipe.kt
│   ├── data/RecipeRepository.kt
│   ├── data/MealLog.kt
│   └── util/ShoppingListAggregator.kt
└── res/
    ├── values/         (EN)
    ├── values-es/      (Spanish)
    ├── values-ca/      (Catalan)
    └── values-it/      (Italian)
```

## Seed-data source

Recipes mirrored from the Miam knowledge base:
- `/home/mia/Miam/miam-knowledge-base/docs/life/blueprint-recipes.md`
- `/home/mia/Miam/miam-knowledge-base/docs/life/longo-recipes.md`
- `/home/mia/Miam/miam-knowledge-base/docs/life/longevity-meal-plan.md`

JSON assets are derived from those markdown sources — keep them in sync when the canonical docs change.

## Bios integration

Writes `meal_intake` events via the companion URI. **Requires a paired Bios update** to whitelist `com.idun.app` in `CompanionContract.PACKAGES`. Until that lands, writes return `PENDING_APPROVAL`. The app keeps working identically — Bios writes are best-effort.

## Build

```
./gradlew assembleDebug
```

Signing config follows the shared `~/.miam-secrets/` keystore convention (see Miam KB Android release pipeline).
