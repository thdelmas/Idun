package com.idun.app.util

import com.idun.app.data.Ingredient
import com.idun.app.data.IngredientCategory
import com.idun.app.data.Recipe
import com.idun.app.data.currentLanguage

/**
 * Aggregates ingredient lines across a selected set of recipes into a
 * shopping list grouped by category.
 *
 * Aggregation rule: ingredients with identical normalized EN name + unit
 * are summed. Ingredients with conflicting units (e.g. "1 onion" vs
 * "75 g onion") are kept as separate lines — sum-by-unit only.
 *
 * The EN name is the canonical dedup key so the same line is recognized
 * across locale switches; localized variants are carried on the line so
 * the UI can render in the current locale (with EN fallback when a
 * translation is missing).
 *
 * This is the v1 lead feature. Keep it tight, deterministic, and
 * unit-test-friendly (no Android dependencies in this file).
 */
object ShoppingListAggregator {

    data class ShoppingLine(
        val nameEn: String,
        val quantity: Double?,
        val unit: String?,
        val category: IngredientCategory,
        val sourceRecipeIds: List<String>,
        val nameByLocale: Map<String, String> = emptyMap(),
    ) {
        fun displayName(lang: String = currentLanguage()): String =
            nameByLocale[lang] ?: nameEn
    }

    data class ShoppingList(
        val byCategory: Map<IngredientCategory, List<ShoppingLine>>,
        val totalRecipes: Int,
        val totalServings: Int,
    )

    fun aggregate(recipes: List<Recipe>): ShoppingList {
        // Bucket: (normalizedEnName, unit) -> running totals
        data class Bucket(
            var quantity: Double?,
            val unit: String?,
            val category: IngredientCategory,
            val displayName: String,
            val nameByLocale: MutableMap<String, String> = mutableMapOf(),
            val sourceIds: MutableList<String> = mutableListOf(),
        )

        val buckets = LinkedHashMap<Pair<String, String?>, Bucket>()

        for (recipe in recipes) {
            for (ing in recipe.ingredients) {
                val key = normalize(ing.nameEn) to ing.unit
                val bucket = buckets.getOrPut(key) {
                    Bucket(
                        quantity = null,
                        unit = ing.unit,
                        category = ing.category,
                        displayName = ing.nameEn,
                    )
                }
                bucket.sourceIds.add(recipe.id)
                // First non-blank localized value per locale wins. If two recipes
                // disagree, the corpus is inconsistent — fix upstream in the KB.
                for ((lang, name) in ing.nameByLocale) {
                    if (name.isNotBlank()) bucket.nameByLocale.putIfAbsent(lang, name)
                }
                val q = ing.quantity
                if (q != null) {
                    bucket.quantity = (bucket.quantity ?: 0.0) + q
                }
                // If we never see a quantity for this bucket it stays null →
                // shopping line renders as "buy some" rather than "0 g".
            }
        }

        val lines = buckets.values.map {
            ShoppingLine(
                nameEn = it.displayName,
                quantity = it.quantity,
                unit = it.unit,
                category = it.category,
                sourceRecipeIds = it.sourceIds.toList(),
                nameByLocale = it.nameByLocale.toMap(),
            )
        }

        val grouped = lines.groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })

        return ShoppingList(
            byCategory = grouped,
            totalRecipes = recipes.size,
            totalServings = recipes.sumOf { it.servings ?: 0 },
        )
    }

    private fun normalize(name: String): String =
        name.trim().lowercase()
}
