package com.idun.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.idun.app.data.Recipe
import com.idun.app.data.RecipeRepository
import com.idun.app.databinding.ActivityMainBinding

/**
 * Recipe browse list. Both Blueprint and Longo sets render equal-weight —
 * no segmentation, no leading-set bias. Per the locked design constraints
 * in CLAUDE.md.
 *
 * Multi-select + "generate shopping list" is the v1 lead feature; the FAB
 * is hidden until at least one recipe is selected.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val repo = RecipeRepository(this)
        val recipes = repo.all()

        binding.recipeList.layoutManager = LinearLayoutManager(this)
        binding.recipeList.adapter = RecipeAdapter(recipes, selected, ::updateFab) { recipe ->
            startActivity(
                Intent(this, RecipeDetailActivity::class.java)
                    .putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id)
            )
        }

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

    private fun updateFab() {
        if (selected.isEmpty()) binding.fabShoppingList.hide()
        else binding.fabShoppingList.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class RecipeAdapter(
    private val recipes: List<Recipe>,
    private val selected: MutableSet<String>,
    private val onSelectionChanged: () -> Unit,
    private val onClick: (Recipe) -> Unit,
) : RecyclerView.Adapter<RecipeAdapter.ViewHolder>() {

    class ViewHolder(val view: android.view.View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = recipes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recipe = recipes[position]
        val title = holder.view.findViewById<android.widget.TextView>(R.id.recipe_title)
        val source = holder.view.findViewById<android.widget.TextView>(R.id.recipe_source)
        val checkbox = holder.view.findViewById<android.widget.CheckBox>(R.id.recipe_checkbox)

        title.text = recipe.nameEn
        val sourceRes = when (recipe.source) {
            com.idun.app.data.RecipeSource.BLUEPRINT -> R.string.source_blueprint
            com.idun.app.data.RecipeSource.LONGO -> R.string.source_longo
        }
        source.text = holder.view.context.getString(sourceRes)

        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = selected.contains(recipe.id)
        checkbox.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(recipe.id) else selected.remove(recipe.id)
            onSelectionChanged()
        }

        holder.view.setOnClickListener { onClick(recipe) }
    }
}
