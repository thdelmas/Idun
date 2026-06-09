package com.idun.app.data

import androidx.annotation.StringRes
import com.idun.app.R
import java.util.Locale

/**
 * One recipe loaded from the bundled JSON assets. Source-of-truth for the
 * recipe corpus lives in the Miam knowledge base under `docs/life/` —
 * the JSON here is derived. See CLAUDE.md for the sync discipline.
 *
 * EN is the canonical fallback. Per-locale overrides live in the
 * `*ByLocale` maps and may be partially populated as KB translations
 * land one locale at a time.
 */
data class Recipe(
    val id: String,
    val source: RecipeSource,
    val nameEn: String,
    val servings: Int?,
    val ingredients: List<Ingredient>,
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val notesEn: String? = null,
    val nameByLocale: Map<String, String> = emptyMap(),
    val notesByLocale: Map<String, String> = emptyMap(),
    val stepsByLocale: Map<String, List<String>> = emptyMap(),
)

data class Ingredient(
    val nameEn: String,
    val quantity: Double?,
    val unit: String?,
    val noteEn: String? = null,
    val category: IngredientCategory = IngredientCategory.OTHER,
    val nameByLocale: Map<String, String> = emptyMap(),
    val noteByLocale: Map<String, String> = emptyMap(),
)

enum class RecipeSource { BLUEPRINT, LONGO }

/**
 * The user-facing (de-branded) set label for a recipe source. Single source
 * of truth — adding a value to [RecipeSource] breaks this exhaustive `when`
 * at compile time, which is the one site to update rather than the several
 * label `when`s that used to be scattered across the activities.
 */
@StringRes
fun RecipeSource.labelRes(): Int = when (this) {
    RecipeSource.BLUEPRINT -> R.string.source_blueprint
    RecipeSource.LONGO -> R.string.source_longo
}

/**
 * Categories used by the shopping-list aggregator to group ingredients.
 * Order matters: groups render in the UI in declaration order.
 */
enum class IngredientCategory {
    PRODUCE,
    HERBS,
    FRUIT,
    LEGUMES_GRAINS_PASTA,
    FLOURS_BREAD,
    NUTS_SEEDS,
    DAIRY_EGGS,
    SEAFOOD,
    OILS_VINEGARS_WINE,
    MILKS_BROTHS,
    PANTRY_SWEETS,
    SPICES,
    OTHER,
}

/** Resolve the current Android locale to one of Idun's supported language codes. */
fun currentLanguage(locale: Locale = Locale.getDefault()): String = when (locale.language) {
    "es", "ca", "fr" -> locale.language
    else -> "en"
}

fun Recipe.displayName(lang: String = currentLanguage()): String =
    nameByLocale[lang] ?: nameEn

fun Recipe.displayNotes(lang: String = currentLanguage()): String? =
    notesByLocale[lang] ?: notesEn

fun Recipe.displaySteps(lang: String = currentLanguage()): List<String> =
    stepsByLocale[lang] ?: steps

fun Ingredient.displayName(lang: String = currentLanguage()): String =
    nameByLocale[lang] ?: nameEn

fun Ingredient.displayNote(lang: String = currentLanguage()): String? =
    noteByLocale[lang] ?: noteEn
