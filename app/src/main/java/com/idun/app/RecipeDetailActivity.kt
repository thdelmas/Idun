package com.idun.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
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
        renderTags(recipe.tags)
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
        val parts = mutableListOf(source)
        recipe.servings?.let {
            parts.add(resources.getQuantityString(R.plurals.servings_count, it, it))
        }
        parts.add(resources.getQuantityString(
            R.plurals.ingredients_count,
            recipe.ingredients.size,
            recipe.ingredients.size,
        ))
        return parts.joinToString("  ·  ")
    }

    private fun renderTags(tags: List<String>) {
        if (tags.isEmpty()) {
            binding.recipeTags.visibility = View.GONE
            return
        }
        binding.recipeTags.visibility = View.VISIBLE
        binding.recipeTags.removeAllViews()
        for (tag in tags) {
            val chip = Chip(this).apply {
                text = tag.replace('_', ' ')
                isClickable = false
                isCheckable = false
            }
            binding.recipeTags.addView(chip)
        }
    }

    private fun renderIngredients(ingredients: List<Ingredient>) {
        val inflater = LayoutInflater.from(this)
        binding.ingredientsContainer.removeAllViews()
        for (ing in ingredients) {
            val row = inflater.inflate(
                R.layout.item_recipe_ingredient,
                binding.ingredientsContainer,
                false,
            )
            row.findViewById<TextView>(R.id.ingredient_quantity).text = formatQuantity(ing)
            row.findViewById<TextView>(R.id.ingredient_name).text = formatName(ing)
            binding.ingredientsContainer.addView(row)
        }
    }

    private fun renderSteps(steps: List<String>) {
        if (steps.isEmpty()) {
            binding.stepsHeader.visibility = View.GONE
            binding.stepsContainer.visibility = View.GONE
            return
        }
        val inflater = LayoutInflater.from(this)
        binding.stepsContainer.removeAllViews()
        for ((index, step) in steps.withIndex()) {
            val row = inflater.inflate(R.layout.item_recipe_step, binding.stepsContainer, false)
            row.findViewById<TextView>(R.id.step_number).text = (index + 1).toString()
            row.findViewById<TextView>(R.id.step_text).text = step
            binding.stepsContainer.addView(row)
        }
    }

    private fun renderNotes(notes: String?) {
        if (notes.isNullOrBlank()) return
        binding.notesHeader.visibility = View.VISIBLE
        binding.recipeNotes.visibility = View.VISIBLE
        binding.recipeNotes.text = notes
    }

    private fun formatQuantity(ing: Ingredient): String {
        val q = ing.quantity ?: return ""
        val pretty = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
        return ing.unit?.takeIf { it.isNotBlank() }?.let { "$pretty $it" } ?: pretty
    }

    private fun formatName(ing: Ingredient): String {
        val base = ing.nameEn
        return ing.noteEn?.takeIf { it.isNotBlank() }?.let { "$base  ($it)" } ?: base
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
