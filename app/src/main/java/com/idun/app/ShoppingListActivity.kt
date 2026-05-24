package com.idun.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.data.IngredientCategory
import com.idun.app.data.RecipeRepository
import com.idun.app.databinding.ActivityShoppingListBinding
import com.idun.app.util.ShoppingListAggregator

/**
 * Consolidated shopping list for a selected set of recipes — v1 lead feature.
 * Renders the [ShoppingListAggregator] output as category-grouped sections,
 * each line a checkbox that strikes through when ticked ("have it").
 */
class ShoppingListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShoppingListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShoppingListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val recipeIds = intent.getStringArrayListExtra(EXTRA_RECIPE_IDS).orEmpty()
        val repo = RecipeRepository(this)
        val recipes = recipeIds.mapNotNull { repo.byId(it) }
        val result = ShoppingListAggregator.aggregate(recipes)

        if (result.byCategory.isEmpty()) {
            binding.summary.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            return
        }

        binding.summary.text = getString(
            R.string.shopping_list_summary,
            result.totalRecipes,
            result.totalServings,
        )

        for ((category, lines) in result.byCategory) {
            binding.listContainer.addView(makeCategoryHeader(category))
            for (line in lines) {
                binding.listContainer.addView(makeLineRow(line))
            }
        }
    }

    private fun makeCategoryHeader(category: IngredientCategory): TextView {
        return TextView(this).apply {
            text = getString(categoryLabel(category))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isAllCaps = true
            alpha = 0.7f
            setPadding(0, 24, 0, 8)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun makeLineRow(line: ShoppingListAggregator.ShoppingLine): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val checkbox = CheckBox(this)
        val label = TextView(this).apply {
            text = formatLine(line)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        checkbox.setOnCheckedChangeListener { _, checked ->
            label.paintFlags = if (checked) {
                label.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                label.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            label.alpha = if (checked) 0.5f else 1f
        }
        row.addView(checkbox)
        row.addView(label)
        return row
    }

    private fun formatLine(line: ShoppingListAggregator.ShoppingLine): String {
        val parts = mutableListOf<String>()
        line.quantity?.let { q ->
            val pretty = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
            parts.add(pretty)
        }
        line.unit?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        parts.add(line.nameEn)
        return parts.joinToString(" ")
    }

    private fun categoryLabel(category: IngredientCategory): Int = when (category) {
        IngredientCategory.PRODUCE -> R.string.category_produce
        IngredientCategory.HERBS -> R.string.category_herbs
        IngredientCategory.FRUIT -> R.string.category_fruit
        IngredientCategory.LEGUMES_GRAINS_PASTA -> R.string.category_legumes_grains_pasta
        IngredientCategory.FLOURS_BREAD -> R.string.category_flours_bread
        IngredientCategory.NUTS_SEEDS -> R.string.category_nuts_seeds
        IngredientCategory.DAIRY_EGGS -> R.string.category_dairy_eggs
        IngredientCategory.SEAFOOD -> R.string.category_seafood
        IngredientCategory.OILS_VINEGARS_WINE -> R.string.category_oils_vinegars_wine
        IngredientCategory.MILKS_BROTHS -> R.string.category_milks_broths
        IngredientCategory.PANTRY_SWEETS -> R.string.category_pantry_sweets
        IngredientCategory.SPICES -> R.string.category_spices
        IngredientCategory.OTHER -> R.string.category_other
    }

    companion object {
        const val EXTRA_RECIPE_IDS = "recipe_ids"
    }
}
