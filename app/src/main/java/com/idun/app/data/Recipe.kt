package com.idun.app.data

/**
 * One recipe loaded from the bundled JSON assets. Source-of-truth for the
 * recipe corpus lives in the Miam knowledge base under `docs/life/` —
 * the JSON here is derived. See CLAUDE.md for the sync discipline.
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
)

data class Ingredient(
    val nameEn: String,
    val quantity: Double?,
    val unit: String?,
    val noteEn: String? = null,
    val category: IngredientCategory = IngredientCategory.OTHER,
)

enum class RecipeSource { BLUEPRINT, LONGO }

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
