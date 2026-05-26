package com.idun.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.data.FoodRepository
import com.idun.app.data.IngredientCategory
import com.idun.app.data.displayBenefits
import com.idun.app.data.displayDeficiencySigns
import com.idun.app.data.displayExcessSigns
import com.idun.app.data.displayHowToConsume
import com.idun.app.data.displayName
import com.idun.app.data.displayWhenToConsume
import com.idun.app.databinding.ActivityFoodDetailBinding
import com.idun.app.util.AssetImages

/**
 * Detail page for a single canonical food. Above the fold: hero photo
 * (Wikimedia Commons, bundled in `assets/foods/images/<id>.jpg`) + the food
 * name + its category. Below: the five teaching blocks rendered as cards
 * with a category-tinted left stripe, plus the global disclaimer.
 *
 * Missing hero image is the normal degraded state — the ImageView simply
 * hides itself and the page flows as before.
 */
class FoodDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOOD_ID = "food_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityFoodDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val foodId = intent.getStringExtra(EXTRA_FOOD_ID)
        val food = foodId?.let { FoodRepository(this).byId(it) }
        if (food == null) {
            finish()
            return
        }

        supportActionBar?.title = food.displayName()
        binding.foodName.text = food.displayName()
        binding.foodCategory.text = getString(categoryLabel(food.category))
        AssetImages.applyFoodImage(binding.foodHero, food.imageFilename)
        binding.foodImageCredit.visibility =
            if (binding.foodHero.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        binding.foodImageCredit.setText(R.string.food_image_credit)

        bindSection(binding.sectionBenefits.root, R.string.food_section_benefits, food.displayBenefits(), food.category)
        bindSection(binding.sectionHowToConsume.root, R.string.food_section_how_to_consume, food.displayHowToConsume(), food.category)
        bindSection(binding.sectionWhenToConsume.root, R.string.food_section_when_to_consume, food.displayWhenToConsume(), food.category)
        bindSection(binding.sectionDeficiencySigns.root, R.string.food_section_deficiency_signs, food.displayDeficiencySigns(), food.category)
        bindSection(binding.sectionExcessSigns.root, R.string.food_section_excess_signs, food.displayExcessSigns(), food.category)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindSection(card: View, labelRes: Int, body: String, category: IngredientCategory) {
        card.findViewById<TextView>(R.id.section_label).setText(labelRes)
        card.findViewById<TextView>(R.id.section_body).text = body
        card.findViewById<View>(R.id.section_stripe).setBackgroundColor(getColor(categoryStripe(category)))
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

    private fun categoryStripe(category: IngredientCategory): Int = when (category) {
        IngredientCategory.PRODUCE, IngredientCategory.HERBS, IngredientCategory.FRUIT ->
            R.color.accent_cyan
        IngredientCategory.SEAFOOD, IngredientCategory.DAIRY_EGGS ->
            R.color.accent_apple_red
        else -> R.color.text_secondary
    }
}
