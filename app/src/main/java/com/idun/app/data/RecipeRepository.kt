package com.idun.app.data

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader

/**
 * Loads the bundled recipe corpus from app assets. JSON layout:
 *
 *   { "recipes": [ { "id": ..., "source": "blueprint"|"longo", ... }, ... ] }
 *
 * Two files (`recipes_blueprint.json`, `recipes_longo.json`) are read and
 * merged into a single in-memory list. No DB persistence — the corpus is
 * static reference content, so the asset read on demand is enough.
 */
class RecipeRepository(private val context: Context) {

    private var cached: List<Recipe>? = null

    fun all(): List<Recipe> {
        cached?.let { return it }
        val merged = listOf("recipes_blueprint.json", "recipes_longo.json")
            .flatMap { parseAsset(it) }
        cached = merged
        return merged
    }

    fun byId(id: String): Recipe? = all().firstOrNull { it.id == id }

    fun bySource(source: RecipeSource): List<Recipe> =
        all().filter { it.source == source }

    private fun parseAsset(filename: String): List<Recipe> {
        val raw = context.assets.open(filename).bufferedReader().use(BufferedReader::readText)
        val root = JSONObject(raw)
        val arr = root.getJSONArray("recipes")
        val out = ArrayList<Recipe>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val ingArr = r.getJSONArray("ingredients")
            val ings = ArrayList<Ingredient>(ingArr.length())
            for (j in 0 until ingArr.length()) {
                val ing = ingArr.getJSONObject(j)
                ings.add(
                    Ingredient(
                        nameEn = ing.getString("name_en"),
                        quantity = ing.optDouble("quantity").takeUnless { it.isNaN() },
                        unit = ing.optString("unit", null),
                        noteEn = ing.optString("note_en", null),
                        category = runCatching {
                            IngredientCategory.valueOf(
                                ing.optString("category", "OTHER").uppercase()
                            )
                        }.getOrDefault(IngredientCategory.OTHER),
                    )
                )
            }
            out.add(
                Recipe(
                    id = r.getString("id"),
                    source = RecipeSource.valueOf(r.getString("source").uppercase()),
                    nameEn = r.getString("name_en"),
                    servings = r.optInt("servings").takeIf { r.has("servings") && !r.isNull("servings") },
                    ingredients = ings,
                    steps = r.optJSONArray("steps")
                        ?.let { stepsArr -> (0 until stepsArr.length()).map { stepsArr.getString(it) } }
                        ?: emptyList(),
                    tags = r.optJSONArray("tags")
                        ?.let { tagsArr -> (0 until tagsArr.length()).map { tagsArr.getString(it) } }
                        ?: emptyList(),
                    notesEn = r.optString("notes_en", null),
                )
            )
        }
        return out
    }
}
