package com.idun.app

import android.app.Activity
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
import com.idun.app.data.Recipe
import com.idun.app.data.RecipeRepository
import com.idun.app.data.RecipeSource
import com.idun.app.data.displayName
import com.idun.app.data.labelRes
import com.idun.app.databinding.ActivityMainBinding
import java.util.Locale

/**
 * Single-select recipe picker. Reuses the activity_main layout (toolbar +
 * recycler + FAB) but hides the FAB and returns the picked recipe id via
 * setResult instead of opening detail. Sectioned + searchable, same as the
 * main browse list — same code shape with selection turned off and a
 * single-tap-returns flow.
 */
class PickRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PickAdapter
    private var allRecipes: List<Recipe> = emptyList()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.pick_recipe_title)
        binding.fabShoppingList.hide()
        // The picker reuses the browse layout but doesn't filter by set.
        binding.filterChipScroll.visibility = View.GONE

        allRecipes = RecipeRepository(this).all()
        adapter = PickAdapter { recipe ->
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(RESULT_RECIPE_ID, recipe.id),
            )
            finish()
        }
        binding.recipeList.layoutManager = LinearLayoutManager(this)
        binding.recipeList.adapter = adapter
        applyFilter()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_pick_recipe, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
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
        val filtered = if (q.isEmpty()) allRecipes else allRecipes.filter { r ->
            r.searchableNames().any { it.lowercase(Locale.getDefault()).contains(q) } ||
                r.tags.any { it.lowercase(Locale.getDefault()).contains(q) }
        }
        adapter.setRecipes(filtered)
    }

    private fun Recipe.searchableNames(): Sequence<String> =
        sequenceOf(nameEn) + nameByLocale.values.asSequence()

    companion object {
        const val RESULT_RECIPE_ID = "recipe_id"
    }
}

private sealed class PickRow {
    data class Header(val source: RecipeSource, val count: Int) : PickRow()
    data class Item(val recipe: Recipe) : PickRow()
}

private class PickAdapter(
    private val onPick: (Recipe) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<PickRow> = emptyList()

    fun setRecipes(recipes: List<Recipe>) {
        val grouped = recipes.groupBy { it.source }
        rows = buildList {
            for (source in RecipeSource.values()) {
                val items = grouped[source].orEmpty()
                if (items.isEmpty()) continue
                add(PickRow.Header(source, items.size))
                items.forEach { add(PickRow.Item(it)) }
            }
        }
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size
    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is PickRow.Header -> 0
        is PickRow.Item -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderH(inflater.inflate(R.layout.item_section_header, parent, false))
        } else {
            ItemH(inflater.inflate(R.layout.item_recipe_pick, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PickRow.Header -> (holder as HeaderH).bind(row)
            is PickRow.Item -> (holder as ItemH).bind(row.recipe, onPick)
        }
    }

    class HeaderH(v: View) : RecyclerView.ViewHolder(v) {
        private val label: TextView = v.findViewById(R.id.section_label)
        private val count: TextView = v.findViewById(R.id.section_count)
        fun bind(row: PickRow.Header) {
            label.text = label.context.getString(row.source.labelRes())
            count.text = count.context.getString(R.string.section_count_label, row.count)
        }
    }

    class ItemH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.pick_title)
        private val meta: TextView = v.findViewById(R.id.pick_meta)
        fun bind(recipe: Recipe, onPick: (Recipe) -> Unit) {
            title.text = recipe.displayName()
            val ctx = itemView.context
            val parts = mutableListOf<String>()
            parts.add(ctx.resources.getQuantityString(R.plurals.ingredients_count, recipe.ingredients.size, recipe.ingredients.size))
            recipe.servings?.let { s ->
                parts.add(ctx.resources.getQuantityString(R.plurals.servings_count, s, s))
            }
            meta.text = parts.joinToString("  ·  ")
            itemView.setOnClickListener { onPick(recipe) }
        }
    }
}
