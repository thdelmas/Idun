package com.idun.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.bios.BiosClient
import com.idun.app.data.IdunDatabase
import com.idun.app.data.MealLogEntry
import com.idun.app.data.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recipe detail screen. "Mark as eaten" inserts a meal-log row locally
 * and fire-and-forget pushes a `meal_intake` event to Bios.
 *
 * Scaffold-only — layout is a placeholder until the recipe-detail UI is
 * designed. See CLAUDE.md for v1 scope: shopping-list is the lead feature,
 * detail UI can be minimal in v1.
 */
class RecipeDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recipeId = intent.getStringExtra(EXTRA_RECIPE_ID) ?: run {
            finish(); return
        }
        val repo = RecipeRepository(this)
        val recipe = repo.byId(recipeId) ?: run {
            finish(); return
        }
        title = recipe.nameEn

        // TODO: render recipe.ingredients, recipe.steps, recipe.notesEn
        // TODO: "Mark as eaten" button → logEaten(recipe.id)
    }

    private fun logEaten(recipeId: String, servings: Double = 1.0) {
        val now = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            IdunDatabase.get(this@RecipeDetailActivity)
                .mealLogDao()
                .insert(
                    MealLogEntry(
                        recipeId = recipeId,
                        timestampMs = now,
                        servingsEaten = servings,
                    )
                )
            // Bios write is best-effort; local row is the truth.
            BiosClient(this@RecipeDetailActivity).pushMealIntake(now, servings)
        }
    }

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }
}
