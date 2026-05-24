package com.idun.app

import android.content.Intent
import android.os.Bundle
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
 * Multi-select + "generate shopping list" is the v1 lead feature; this
 * scaffold stubs the selection state and routes to ShoppingListActivity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repo = RecipeRepository(this)
        val recipes = repo.all()

        binding.recipeList.layoutManager = LinearLayoutManager(this)
        binding.recipeList.adapter = RecipeAdapter(recipes, selected) { recipe ->
            // Tapping the row opens detail; selection toggle is on the checkbox.
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
    }
}

class RecipeAdapter(
    private val recipes: List<Recipe>,
    private val selected: MutableSet<String>,
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
        source.text = recipe.source.name.lowercase().replaceFirstChar { it.uppercase() }

        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = selected.contains(recipe.id)
        checkbox.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(recipe.id) else selected.remove(recipe.id)
        }

        holder.view.setOnClickListener { onClick(recipe) }
    }
}
