package com.idun.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.bios.BiosClient
import com.idun.app.data.IdunDatabase
import com.idun.app.data.Ingredient
import com.idun.app.data.MealLogEntry
import com.idun.app.data.Recipe
import com.idun.app.data.RecipeRepository
import com.idun.app.data.RecipeSource
import com.idun.app.databinding.ActivityRecipeDetailBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recipe detail screen. "Mark as eaten" inserts a meal-log row locally
 * and fire-and-forget pushes a `meal_intake` event to Bios.
 */
class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recipeId = intent.getStringExtra(EXTRA_RECIPE_ID) ?: run {
            finish(); return
        }
        val recipe = RecipeRepository(this).byId(recipeId) ?: run {
            finish(); return
        }

        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = recipe.nameEn

        binding.recipeMeta.text = buildMeta(recipe)
        renderIngredients(recipe.ingredients)
        renderSteps(recipe.steps)
        renderNotes(recipe.notesEn)

        binding.fabMarkEaten.setOnClickListener {
            logEaten(recipe.id)
        }
    }

    private fun buildMeta(recipe: Recipe): String {
        val source = getString(
            when (recipe.source) {
                RecipeSource.BLUEPRINT -> R.string.source_blueprint
                RecipeSource.LONGO -> R.string.source_longo
            }
        )
        val servings = recipe.servings
            ?.let { " · ${getString(R.string.recipe_servings)}: $it" }
            ?: ""
        return "$source$servings"
    }

    private fun renderIngredients(ingredients: List<Ingredient>) {
        binding.ingredientsContainer.removeAllViews()
        for (ing in ingredients) {
            binding.ingredientsContainer.addView(makeBulletLine(formatIngredient(ing)))
        }
    }

    private fun renderSteps(steps: List<String>) {
        if (steps.isEmpty()) {
            binding.stepsHeader.visibility = View.GONE
            binding.stepsContainer.visibility = View.GONE
            return
        }
        binding.stepsContainer.removeAllViews()
        for ((index, step) in steps.withIndex()) {
            binding.stepsContainer.addView(makeStepLine(index + 1, step))
        }
    }

    private fun renderNotes(notes: String?) {
        if (notes.isNullOrBlank()) return
        binding.notesHeader.visibility = View.VISIBLE
        binding.recipeNotes.visibility = View.VISIBLE
        binding.recipeNotes.text = notes
    }

    private fun formatIngredient(ing: Ingredient): String {
        val parts = mutableListOf<String>()
        ing.quantity?.let { q ->
            val pretty = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
            parts.add(pretty)
        }
        ing.unit?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        parts.add(ing.nameEn)
        val base = parts.joinToString(" ")
        return ing.noteEn?.takeIf { it.isNotBlank() }?.let { "$base ($it)" } ?: base
    }

    private fun makeBulletLine(text: String): TextView {
        return TextView(this).apply {
            this.text = "•  $text"
            textSize = 14f
            setPadding(0, 6, 0, 6)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun makeStepLine(number: Int, text: String): TextView {
        return TextView(this).apply {
            this.text = "$number.  $text"
            textSize = 14f
            setPadding(0, 6, 0, 6)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
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
            BiosClient(this@RecipeDetailActivity).pushMealIntake(now, servings)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@RecipeDetailActivity,
                    R.string.recipe_eaten_logged,
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }
}
