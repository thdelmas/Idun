package com.idun.app.util

import com.idun.app.data.Ingredient
import com.idun.app.data.IngredientCategory
import com.idun.app.data.Recipe
import com.idun.app.data.RecipeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListAggregatorTest {

    @Test
    fun `identical name and unit sum quantities`() {
        val r1 = recipe("r1", Ingredient("onion", 100.0, "g", category = IngredientCategory.PRODUCE))
        val r2 = recipe("r2", Ingredient("onion", 75.0, "g", category = IngredientCategory.PRODUCE))

        val result = ShoppingListAggregator.aggregate(listOf(r1, r2))
        val produce = result.byCategory[IngredientCategory.PRODUCE]!!

        assertEquals(1, produce.size)
        assertEquals(175.0, produce.first().quantity!!, 0.001)
        assertEquals("g", produce.first().unit)
        assertEquals(listOf("r1", "r2"), produce.first().sourceRecipeIds)
    }

    @Test
    fun `different units stay separate even with same name`() {
        val r1 = recipe("r1", Ingredient("onion", 75.0, "g", category = IngredientCategory.PRODUCE))
        val r2 = recipe("r2", Ingredient("onion", 1.0, "whole", category = IngredientCategory.PRODUCE))

        val result = ShoppingListAggregator.aggregate(listOf(r1, r2))
        val produce = result.byCategory[IngredientCategory.PRODUCE]!!

        assertEquals(2, produce.size)
    }

    @Test
    fun `null quantity stays null after aggregation if no quantity ever seen`() {
        val r1 = recipe("r1", Ingredient("salt", null, null, category = IngredientCategory.SPICES))
        val r2 = recipe("r2", Ingredient("salt", null, null, category = IngredientCategory.SPICES))

        val result = ShoppingListAggregator.aggregate(listOf(r1, r2))
        val spices = result.byCategory[IngredientCategory.SPICES]!!

        assertEquals(1, spices.size)
        assertNull(spices.first().quantity)
    }

    @Test
    fun `groups render in category enum order`() {
        val r = recipe(
            "r1",
            Ingredient("salt", null, null, category = IngredientCategory.SPICES),
            Ingredient("onion", 1.0, "whole", category = IngredientCategory.PRODUCE),
            Ingredient("olive oil", 30.0, "ml", category = IngredientCategory.OILS_VINEGARS_WINE),
        )

        val result = ShoppingListAggregator.aggregate(listOf(r))
        val categories = result.byCategory.keys.toList()

        assertEquals(IngredientCategory.PRODUCE, categories[0])
        assertEquals(IngredientCategory.OILS_VINEGARS_WINE, categories[1])
        assertEquals(IngredientCategory.SPICES, categories[2])
    }

    @Test
    fun `case insensitive name normalization merges variants`() {
        val r1 = recipe("r1", Ingredient("Onion", 50.0, "g", category = IngredientCategory.PRODUCE))
        val r2 = recipe("r2", Ingredient("onion", 25.0, "g", category = IngredientCategory.PRODUCE))

        val result = ShoppingListAggregator.aggregate(listOf(r1, r2))
        val produce = result.byCategory[IngredientCategory.PRODUCE]!!

        assertEquals(1, produce.size)
        assertEquals(75.0, produce.first().quantity!!, 0.001)
    }

    @Test
    fun `total servings sums recipe servings ignoring nulls`() {
        val r1 = recipe("r1", Ingredient("x", 1.0, "g", category = IngredientCategory.PRODUCE), servings = 4)
        val r2 = recipe("r2", Ingredient("y", 1.0, "g", category = IngredientCategory.PRODUCE), servings = null)
        val r3 = recipe("r3", Ingredient("z", 1.0, "g", category = IngredientCategory.PRODUCE), servings = 6)

        val result = ShoppingListAggregator.aggregate(listOf(r1, r2, r3))

        assertEquals(10, result.totalServings)
        assertEquals(3, result.totalRecipes)
    }

    private fun recipe(
        id: String,
        vararg ingredients: Ingredient,
        servings: Int? = 4,
    ): Recipe = Recipe(
        id = id,
        source = RecipeSource.BLUEPRINT,
        nameEn = id,
        servings = servings,
        ingredients = ingredients.toList(),
    )
}
