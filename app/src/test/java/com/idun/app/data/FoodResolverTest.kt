package com.idun.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Sanity checks for the catalog-driven ingredient → food_id resolver.
 * Loads the same `foods_catalog.json` asset that ships in the APK so the
 * test exercises the real regex set (no fixtures, no drift).
 */
class FoodResolverTest {

    private val entries = FoodResolver.parseCatalog(CATALOG_JSON.readText())

    @Test
    fun `specific compound wins over generic`() {
        assertEquals("garlic-powder", FoodResolver.match(entries, "1 tsp garlic powder"))
        assertEquals("onion-powder", FoodResolver.match(entries, "onion powder"))
        assertEquals("milk-almond", FoodResolver.match(entries, "1 cup almond milk"))
        assertEquals("flour-almond", FoodResolver.match(entries, "almond flour"))
    }

    @Test
    fun `produce singletons resolve`() {
        assertEquals("garlic", FoodResolver.match(entries, "2 garlic cloves"))
        assertEquals("onion", FoodResolver.match(entries, "1 large onion, diced"))
        assertEquals("spring-onion", FoodResolver.match(entries, "spring onion, sliced"))
        assertEquals("tomato", FoodResolver.match(entries, "cherry tomatoes, halved"))
        assertEquals("olive-oil", FoodResolver.match(entries, "Extra virgin olive oil"))
    }

    @Test
    fun `case insensitive`() {
        assertEquals("salt", FoodResolver.match(entries, "SALT"))
        assertEquals("salt", FoodResolver.match(entries, "Salt"))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(FoodResolver.match(entries, ""))
        assertNull(FoodResolver.match(entries, "   "))
    }

    @Test
    fun `unknown ingredient returns null`() {
        assertNull(FoodResolver.match(entries, "moon dust"))
    }

    @Test
    fun `every recipe ingredient resolves`() {
        // Mirrors the build_catalog.py guarantee — runtime resolver must
        // match every line in the shipped corpus, with the same priorities
        // as the build-time check.
        for (recipeFile in listOf("recipes_blueprint.json", "recipes_longo.json")) {
            val json = File(ASSETS_DIR, recipeFile).readText()
            val recipes = org.json.JSONObject(json).getJSONArray("recipes")
            for (i in 0 until recipes.length()) {
                val ings = recipes.getJSONObject(i).getJSONArray("ingredients")
                for (j in 0 until ings.length()) {
                    val name = ings.getJSONObject(j).getString("name_en")
                    val resolved = FoodResolver.match(entries, name)
                    assertNotNull("Unresolved: '$name'", resolved)
                }
            }
        }
    }

    companion object {
        private val ASSETS_DIR = File("src/main/assets")
        private val CATALOG_JSON = File(ASSETS_DIR, "foods_catalog.json")
    }
}
