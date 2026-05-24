package com.idun.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.idun.app.data.Recipe
import com.idun.app.data.RecipeRepository
import com.idun.app.data.RecipeSource
import com.idun.app.databinding.ActivityMainBinding
import java.util.Locale

/**
 * Recipe browse list. Both Blueprint and Longo sets render equal-weight —
 * grouped into two sections of equal visual treatment, never one above the
 * other in dominance. Per the locked design constraints in CLAUDE.md.
 *
 * Multi-select + "generate shopping list" is the v1 lead feature; the FAB
 * is hidden until at least one recipe is selected.
 *
 * Search filters across recipe name and tags, case-insensitive, and rebuilds
 * the section list to drop any section that has no matches.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val selected = mutableSetOf<String>()
    private lateinit var adapter: RecipeListAdapter
    private var allRecipes: List<Recipe> = emptyList()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        allRecipes = RecipeRepository(this).all()

        adapter = RecipeListAdapter(
            selected = selected,
            onSelectionChanged = ::updateFab,
            onClick = { recipe ->
                startActivity(
                    Intent(this, RecipeDetailActivity::class.java)
                        .putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id)
                )
            },
        )
        binding.recipeList.layoutManager = LinearLayoutManager(this)
        binding.recipeList.adapter = adapter
        applyFilter()

        binding.fabShoppingList.setOnClickListener {
            startActivity(
                Intent(this, ShoppingListActivity::class.java)
                    .putStringArrayListExtra(
                        ShoppingListActivity.EXTRA_RECIPE_IDS,
                        ArrayList(selected),
                    )
            )
        }

        updateFab()
    }

    private fun applyFilter() {
        val q = query.trim().lowercase(Locale.getDefault())
        val filtered = if (q.isEmpty()) allRecipes else allRecipes.filter { r ->
            r.nameEn.lowercase(Locale.getDefault()).contains(q) ||
                r.tags.any { it.lowercase(Locale.getDefault()).contains(q) }
        }
        adapter.setRecipes(filtered)
    }

    private fun updateFab() {
        if (selected.isEmpty()) binding.fabShoppingList.hide()
        else binding.fabShoppingList.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_planning -> {
                startActivity(Intent(this, PlanningActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

private sealed class Row {
    data class Header(val source: RecipeSource, val count: Int) : Row()
    data class Item(val recipe: Recipe) : Row()
}

private class RecipeListAdapter(
    private val selected: MutableSet<String>,
    private val onSelectionChanged: () -> Unit,
    private val onClick: (Recipe) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<Row> = emptyList()

    fun setRecipes(recipes: List<Recipe>) {
        val grouped = recipes.groupBy { it.source }
        rows = buildList {
            // Both sources surfaced equal-weight — Blueprint first only because
            // it's the order they're declared in RecipeSource enum.
            for (source in RecipeSource.values()) {
                val items = grouped[source].orEmpty()
                if (items.isEmpty()) continue
                add(Row.Header(source, items.size))
                items.forEach { add(Row.Item(it)) }
            }
        }
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false))
        } else {
            ItemHolder(inflater.inflate(R.layout.item_recipe, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row)
            is Row.Item -> (holder as ItemHolder).bind(row.recipe, selected, onSelectionChanged, onClick)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.section_label)
        private val count: TextView = view.findViewById(R.id.section_count)
        fun bind(row: Row.Header) {
            val labelRes = when (row.source) {
                RecipeSource.BLUEPRINT -> R.string.source_blueprint
                RecipeSource.LONGO -> R.string.source_longo
            }
            label.text = label.context.getString(labelRes)
            count.text = count.context.getString(R.string.section_count_label, row.count)
        }
    }

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.recipe_title)
        private val meta: TextView = view.findViewById(R.id.recipe_meta)
        private val tags: TextView = view.findViewById(R.id.recipe_tags)
        private val checkbox: CheckBox = view.findViewById(R.id.recipe_checkbox)

        fun bind(
            recipe: Recipe,
            selected: MutableSet<String>,
            onSelectionChanged: () -> Unit,
            onClick: (Recipe) -> Unit,
        ) {
            title.text = recipe.nameEn
            meta.text = buildMeta(recipe)
            if (recipe.tags.isEmpty()) {
                tags.visibility = View.GONE
            } else {
                tags.visibility = View.VISIBLE
                tags.text = recipe.tags.joinToString("  ·  ")
            }
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = selected.contains(recipe.id)
            checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(recipe.id) else selected.remove(recipe.id)
                onSelectionChanged()
            }
            itemView.setOnClickListener { onClick(recipe) }
        }

        private fun buildMeta(recipe: Recipe): String {
            val ctx = itemView.context
            val parts = mutableListOf<String>()
            parts.add(ctx.resources.getQuantityString(R.plurals.ingredients_count, recipe.ingredients.size, recipe.ingredients.size))
            recipe.servings?.let { s ->
                parts.add(ctx.resources.getQuantityString(R.plurals.servings_count, s, s))
            }
            return parts.joinToString("  ·  ")
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }
}
