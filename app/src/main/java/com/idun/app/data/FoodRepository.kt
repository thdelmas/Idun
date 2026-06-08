package com.idun.app.data

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader

/**
 * Loads the 145-entry ingredients-pedagogy corpus from `foods.json`.
 *
 * JSON layout:
 *
 *   { "foods": [ { "id": "...", "category": "PRODUCE",
 *                  "name_en": "...", "benefits_en": "...",
 *                  "how_to_consume_en": "...", "when_to_consume_en": "...",
 *                  "deficiency_signs_en": "...", "excess_signs_en": "...",
 *                  "name_es": "...", ... }, ... ] }
 *
 * The asset is bundled (static reference content) and parsed once on
 * first access, mirroring [RecipeRepository].
 */
class FoodRepository(private val context: Context) {

    private var cached: List<Food>? = null

    fun all(): List<Food> {
        cached?.let { return it }
        val parsed = parseAsset("foods.json")
        cached = parsed
        return parsed
    }

    fun byId(id: String): Food? = all().firstOrNull { it.id == id }

    fun byCategory(): Map<IngredientCategory, List<Food>> =
        all().groupBy { it.category }

    private fun parseAsset(filename: String): List<Food> {
        val raw = context.assets.open(filename).bufferedReader().use(BufferedReader::readText)
        val arr = JSONObject(raw).getJSONArray("foods")
        val out = ArrayList<Food>(arr.length())
        for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i)
            out.add(
                Food(
                    id = f.getString("id"),
                    category = runCatching {
                        IngredientCategory.valueOf(f.optString("category", "OTHER").uppercase())
                    }.getOrDefault(IngredientCategory.OTHER),
                    nameEn = f.getString("name_en"),
                    benefitsEn = f.getString("benefits_en"),
                    howToConsumeEn = f.getString("how_to_consume_en"),
                    whenToConsumeEn = f.getString("when_to_consume_en"),
                    deficiencySignsEn = f.getString("deficiency_signs_en"),
                    excessSignsEn = f.getString("excess_signs_en"),
                    // Convention: every food may have a bundled JPG at
                    // `assets/foods/images/<id>.jpg` (Wikimedia source, fetched
                    // by scripts/foods/download_images.py). If the file isn't
                    // present, AssetImages silently hides the view — no manifest.
                    imageFilename = "${f.getString("id")}.jpg",
                    nameByLocale = readLocaleMap(f, "name"),
                    benefitsByLocale = readLocaleMap(f, "benefits"),
                    howToConsumeByLocale = readLocaleMap(f, "how_to_consume"),
                    whenToConsumeByLocale = readLocaleMap(f, "when_to_consume"),
                    deficiencySignsByLocale = readLocaleMap(f, "deficiency_signs"),
                    excessSignsByLocale = readLocaleMap(f, "excess_signs"),
                )
            )
        }
        return out
    }

    private fun readLocaleMap(obj: JSONObject, prefix: String): Map<String, String> {
        val map = HashMap<String, String>(3)
        for (lang in LOCALIZED_LANGS) {
            val key = "${prefix}_$lang"
            if (!obj.has(key) || obj.isNull(key)) continue
            val v = obj.optString(key)
            if (v.isNotBlank()) map[lang] = v
        }
        return map
    }

    private companion object {
        val LOCALIZED_LANGS = listOf("es", "ca", "fr")
    }
}
