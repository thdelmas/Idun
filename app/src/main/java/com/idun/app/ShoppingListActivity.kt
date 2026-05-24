package com.idun.app

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.data.IngredientCategory
import com.idun.app.data.RecipeRepository
import com.idun.app.databinding.ActivityShoppingListBinding
import com.idun.app.util.ShoppingListAggregator

/**
 * Consolidated shopping list for a selected set of recipes — v1 lead feature.
 * Renders [ShoppingListAggregator] output as category-grouped sections,
 * each line a checkbox; ticking strikes through and dims the row to model
 * "got it already". Tick state survives rotation via savedInstanceState.
 */
class ShoppingListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShoppingListBinding
    private val ticked = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShoppingListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        savedInstanceState?.getStringArrayList(STATE_TICKED)?.let {
            ticked.clear()
            ticked.addAll(it)
        }

        val recipeIds = intent.getStringArrayListExtra(EXTRA_RECIPE_IDS).orEmpty()
        val recipes = RecipeRepository(this).let { repo -> recipeIds.mapNotNull { repo.byId(it) } }
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

        val inflater = LayoutInflater.from(this)
        for ((category, lines) in result.byCategory) {
            val header = inflater.inflate(
                R.layout.item_shopping_category,
                binding.listContainer,
                false,
            )
            header.findViewById<TextView>(R.id.category_label).setText(categoryLabel(category))
            header.findViewById<View>(R.id.category_stripe).setBackgroundColor(stripeColor(category))
            binding.listContainer.addView(header)

            for (line in lines) {
                binding.listContainer.addView(makeLineRow(inflater, line))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_TICKED, ArrayList(ticked))
    }

    private fun makeLineRow(
        inflater: LayoutInflater,
        line: ShoppingListAggregator.ShoppingLine,
    ): View {
        val row = inflater.inflate(R.layout.item_shopping_line, binding.listContainer, false)
        val key = lineKey(line)
        val check = row.findViewById<CheckBox>(R.id.line_check)
        val quantity = row.findViewById<TextView>(R.id.line_quantity)
        val name = row.findViewById<TextView>(R.id.line_name)

        quantity.text = formatQuantity(line)
        name.text = line.nameEn
        applyStrike(check, name, quantity, ticked.contains(key))

        row.setOnClickListener {
            val newChecked = !check.isChecked
            if (newChecked) ticked.add(key) else ticked.remove(key)
            applyStrike(check, name, quantity, newChecked)
        }
        return row
    }

    private fun applyStrike(
        check: CheckBox,
        name: TextView,
        quantity: TextView,
        struck: Boolean,
    ) {
        check.isChecked = struck
        listOf(name, quantity).forEach { tv ->
            tv.paintFlags = if (struck) {
                tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            tv.alpha = if (struck) 0.4f else 1f
        }
    }

    private fun lineKey(line: ShoppingListAggregator.ShoppingLine): String =
        "${line.category.name}|${line.nameEn}|${line.unit ?: ""}"

    private fun formatQuantity(line: ShoppingListAggregator.ShoppingLine): String {
        val q = line.quantity ?: return ""
        val pretty = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()
        return line.unit?.takeIf { it.isNotBlank() }?.let { "$pretty $it" } ?: pretty
    }

    private fun stripeColor(category: IngredientCategory): Int = getColor(
        when (category) {
            IngredientCategory.PRODUCE, IngredientCategory.HERBS, IngredientCategory.FRUIT ->
                R.color.accent_norse_gold
            IngredientCategory.SEAFOOD, IngredientCategory.DAIRY_EGGS ->
                R.color.accent_apple_red
            else -> R.color.text_secondary
        }
    )

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
        private const val STATE_TICKED = "ticked"
    }
}
