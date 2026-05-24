package com.idun.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader

/**
 * Loads the bundled recipe corpus from app assets. JSON layout:
 *
 *   { "recipes": [ { "id": ..., "source": "blueprint"|"longo", ... }, ... ] }
 *
 * Recipe and ingredient text fields follow the `*_en` / `*_es` / `*_ca` / `*_fr`
 * pattern (e.g. `name_en` is required, `name_es` is an optional override).
 * Cooking steps use a top-level `steps` array (EN canonical) plus optional
 * `steps_es` / `steps_ca` / `steps_fr` arrays. Missing locales fall back to
 * EN at render time.
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
                        nameByLocale = readLocaleStringMap(ing, "name"),
                        noteByLocale = readLocaleStringMap(ing, "note"),
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
                    nameByLocale = readLocaleStringMap(r, "name"),
                    notesByLocale = readLocaleStringMap(r, "notes"),
                    stepsByLocale = readLocaleStepsMap(r),
                )
            )
        }
        return out
    }

    /** Collect non-EN overrides for a given prefix (e.g. `name` → `name_es`, `name_ca`, `name_fr`). */
    private fun readLocaleStringMap(obj: JSONObject, prefix: String): Map<String, String> {
        val map = HashMap<String, String>(3)
        for (lang in LOCALIZED_LANGS) {
            val key = "${prefix}_$lang"
            if (!obj.has(key) || obj.isNull(key)) continue
            val v = obj.optString(key)
            if (v.isNotBlank()) map[lang] = v
        }
        return map
    }

    private fun readLocaleStepsMap(obj: JSONObject): Map<String, List<String>> {
        val map = HashMap<String, List<String>>(3)
        for (lang in LOCALIZED_LANGS) {
            val arr: JSONArray = obj.optJSONArray("steps_$lang") ?: continue
            val list = (0 until arr.length()).map { arr.getString(it) }
            if (list.isNotEmpty()) map[lang] = list
        }
        return map
    }

    private companion object {
        val LOCALIZED_LANGS = listOf("es", "ca", "fr")
    }
}
