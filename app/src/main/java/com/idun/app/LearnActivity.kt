package com.idun.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.idun.app.data.Food
import com.idun.app.data.FoodRepository
import com.idun.app.data.IngredientCategory
import com.idun.app.data.displayBenefits
import com.idun.app.data.displayName
import com.idun.app.databinding.ActivityLearnBinding
import com.idun.app.ui.setupBottomNav
import com.idun.app.util.AssetImages
import java.util.Locale

/**
 * "Learn" screen — pedagogical index of every canonical ingredient appearing
 * across the Blueprint and Longo corpuses. Foods are grouped by category and
 * surfaced alphabetically within. Each row carries a Wikimedia thumbnail
 * (bundled in assets) when available. Tapping a row opens
 * [FoodDetailActivity].
 *
 * The toolbar search filters by display name across the active locale and
 * across English (so "garlic" still matches Spanish "ajo en polvo"). An
 * empty result swaps the list for an empty-state pane.
 *
 * Source-of-truth for the entry text is the Miam KB markdown at
 * `docs/life/ingredients-pedagogy.md`; the bundled `foods.json` asset is a
 * derived artifact (see `scripts/foods/extract_foods_json.py`).
 */
class LearnActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLearnBinding
    private lateinit var adapter: FoodListAdapter
    private var allFoods: List<Food> = emptyList()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearnBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupBottomNav(binding.bottomNav, R.id.nav_learn)

        allFoods = FoodRepository(this).all()
        adapter = FoodListAdapter { food ->
            startActivity(
                Intent(this, FoodDetailActivity::class.java)
                    .putExtra(FoodDetailActivity.EXTRA_FOOD_ID, food.id)
            )
        }
        binding.foodList.layoutManager = LinearLayoutManager(this)
        binding.foodList.adapter = adapter
        applyFilter()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_learn, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.queryHint = getString(R.string.learn_search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                query = newText.orEmpty()
                applyFilter()
                return true
            }
        })
        return true
    }

    private fun applyFilter() {
        val q = query.trim().lowercase(Locale.getDefault())
        val filtered = if (q.isEmpty()) allFoods else allFoods.filter { food ->
            food.searchableNames().any { it.lowercase(Locale.getDefault()).contains(q) }
        }
        adapter.setRows(buildLearnRows(filtered))
        val empty = filtered.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.foodList.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun Food.searchableNames(): Sequence<String> =
        sequenceOf(nameEn) + nameByLocale.values.asSequence()

    private fun buildLearnRows(foods: List<Food>): List<LearnRow> = buildList {
        val grouped = foods.groupBy { it.category }
        for (category in IngredientCategory.values()) {
            val items = grouped[category].orEmpty().sortedBy { it.displayName().lowercase() }
            if (items.isEmpty()) continue
            add(LearnRow.Header(category, items.size))
            items.forEach { add(LearnRow.Item(it)) }
        }
    }
}

private sealed class LearnRow {
    data class Header(val category: IngredientCategory, val count: Int) : LearnRow()
    data class Item(val food: Food) : LearnRow()
}

private class FoodListAdapter(
    private val onClick: (Food) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<LearnRow> = emptyList()

    fun setRows(newRows: List<LearnRow>) {
        rows = newRows
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is LearnRow.Header -> TYPE_HEADER
        is LearnRow.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false))
        } else {
            ItemHolder(inflater.inflate(R.layout.item_food, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is LearnRow.Header -> (holder as HeaderHolder).bind(row)
            is LearnRow.Item -> (holder as ItemHolder).bind(row.food, onClick)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.section_label)
        private val count: TextView = view.findViewById(R.id.section_count)
        fun bind(row: LearnRow.Header) {
            label.text = label.context.getString(categoryLabel(row.category))
            count.text = count.context.resources.getQuantityString(
                R.plurals.foods_count, row.count, row.count,
            )
        }
    }

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.food_name)
        private val teaser: TextView = view.findViewById(R.id.food_teaser)
        private val thumb: ShapeableImageView = view.findViewById(R.id.food_thumb)

        fun bind(food: Food, onClick: (Food) -> Unit) {
            name.text = food.displayName()
            teaser.text = food.displayBenefits()
            AssetImages.applyFoodImage(thumb, food.imageFilename)
            itemView.setOnClickListener { onClick(food) }
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1

        fun categoryLabel(category: IngredientCategory): Int = when (category) {
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
    }
}
