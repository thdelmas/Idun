package com.idun.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.data.RecipeRepository
import com.idun.app.util.ShoppingListAggregator

/**
 * Renders the consolidated shopping list for a selected set of recipes.
 * v1 lead feature.
 *
 * Scaffold-only — layout is a placeholder. The aggregation logic in
 * [ShoppingListAggregator] is the load-bearing piece; the UI here just
 * iterates `result.byCategory` and renders each group.
 */
class ShoppingListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recipeIds = intent.getStringArrayListExtra(EXTRA_RECIPE_IDS) ?: emptyList<String>()
        val repo = RecipeRepository(this)
        val recipes = recipeIds.mapNotNull { repo.byId(it) }
        val result = ShoppingListAggregator.aggregate(recipes)

        // TODO: render result.byCategory as grouped sections.
        // TODO: per-line "have it" checkbox to strike-through items already on hand.
        // TODO: share / export to text.
    }

    companion object {
        const val EXTRA_RECIPE_IDS = "recipe_ids"
    }
}
