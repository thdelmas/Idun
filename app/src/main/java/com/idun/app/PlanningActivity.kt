package com.idun.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.idun.app.bios.BiosClient
import com.idun.app.data.IdunDatabase
import com.idun.app.data.MealLogEntry
import com.idun.app.data.PlanEntry
import com.idun.app.data.PlanSlot
import com.idun.app.data.Recipe
import com.idun.app.data.RecipeRepository
import com.idun.app.databinding.ActivityPlanningBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Week view planner. Days run in natural chronological order from today out
 * for [DAYS_AHEAD] days. Each day card holds three slot rows (breakfast /
 * lunch / dinner). Empty slot → tap to pick a recipe. Filled slot → tap to
 * open an edit dialog (change / guests / mark eaten / remove).
 *
 * The FAB jumps to the shopping list aggregated across the visible window,
 * which is the practical answer to "what do I need to buy for the next N
 * days".
 */
class PlanningActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlanningBinding
    private lateinit var pickLauncher: ActivityResultLauncher<Intent>
    private lateinit var recipeRepo: RecipeRepository
    private var pendingPick: Pair<LocalDate, PlanSlot>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlanningBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        recipeRepo = RecipeRepository(this)

        pickLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val recipeId = result.data?.getStringExtra(PickRecipeActivity.RESULT_RECIPE_ID) ?: return@registerForActivityResult
            val target = pendingPick ?: return@registerForActivityResult
            pendingPick = null
            assignRecipe(target.first, target.second, recipeId)
        }

        binding.fabShoppingList.setOnClickListener {
            startActivity(
                Intent(this, ShoppingListActivity::class.java)
                    .putExtra(ShoppingListActivity.EXTRA_FROM_ISO, today().toString())
                    .putExtra(ShoppingListActivity.EXTRA_TO_ISO, today().plusDays(DAYS_AHEAD - 1L).toString())
            )
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        lifecycleScope.launch {
            val from = today()
            val to = from.plusDays(DAYS_AHEAD - 1L)
            val plans = withContext(Dispatchers.IO) {
                IdunDatabase.get(this@PlanningActivity).planDao().inRange(from.toString(), to.toString())
            }
            val plansByDate = plans.groupBy { it.dateIso }
            val recipesById = recipeRepo.all().associateBy { it.id }

            binding.daysContainer.removeAllViews()
            val inflater = LayoutInflater.from(this@PlanningActivity)
            for (i in 0 until DAYS_AHEAD) {
                val date = from.plusDays(i.toLong())
                val dayPlans = plansByDate[date.toString()].orEmpty().associateBy { it.slot }
                binding.daysContainer.addView(buildDayCard(inflater, date, dayPlans, recipesById))
            }
        }
    }

    private fun buildDayCard(
        inflater: LayoutInflater,
        date: LocalDate,
        plansBySlot: Map<PlanSlot, PlanEntry>,
        recipesById: Map<String, Recipe>,
    ): View {
        val card = inflater.inflate(R.layout.item_plan_day, binding.daysContainer, false)
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        card.findViewById<TextView>(R.id.day_weekday).text =
            weekday.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        card.findViewById<TextView>(R.id.day_date).text = date.format(DATE_FORMATTER)
        card.findViewById<TextView>(R.id.day_today_badge).visibility =
            if (date == today()) View.VISIBLE else View.GONE

        val slotsContainer = card.findViewById<android.widget.LinearLayout>(R.id.slots_container)
        for (slot in PlanSlot.values()) {
            slotsContainer.addView(
                buildSlotRow(inflater, slotsContainer, date, slot, plansBySlot[slot], recipesById),
            )
        }
        return card
    }

    private fun buildSlotRow(
        inflater: LayoutInflater,
        parent: android.widget.LinearLayout,
        date: LocalDate,
        slot: PlanSlot,
        entry: PlanEntry?,
        recipesById: Map<String, Recipe>,
    ): View {
        val row = inflater.inflate(R.layout.item_plan_slot, parent, false)
        val label = row.findViewById<TextView>(R.id.slot_label)
        val main = row.findViewById<TextView>(R.id.slot_main)
        val meta = row.findViewById<TextView>(R.id.slot_meta)
        val eatenCheck = row.findViewById<TextView>(R.id.slot_eaten_check)

        label.setText(slotLabel(slot))

        if (entry == null) {
            main.text = getString(R.string.planning_add_meal)
            main.setTextColor(getColor(R.color.text_secondary))
            meta.visibility = View.GONE
            eatenCheck.visibility = View.GONE
            row.setOnClickListener {
                pendingPick = date to slot
                pickLauncher.launch(Intent(this, PickRecipeActivity::class.java))
            }
        } else {
            val recipe = recipesById[entry.recipeId]
            main.text = recipe?.nameEn ?: getString(R.string.planning_missing_recipe)
            main.setTextColor(getColor(R.color.text_primary))

            val parts = mutableListOf<String>()
            parts.add(resources.getQuantityString(R.plurals.servings_count, entry.servings, entry.servings))
            if (entry.guestCount > 0) {
                parts.add(resources.getQuantityString(R.plurals.guests_count, entry.guestCount, entry.guestCount))
            }
            meta.text = parts.joinToString("  ·  ")
            meta.visibility = View.VISIBLE
            eatenCheck.visibility = if (entry.eatenAtMs != null) View.VISIBLE else View.GONE

            row.setOnClickListener { openEditDialog(entry, recipe) }
        }
        return row
    }

    private fun openEditDialog(entry: PlanEntry, recipe: Recipe?) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (recipe != null) {
            labels += getString(R.string.planning_view_recipe)
            actions += {
                startActivity(
                    Intent(this, RecipeDetailActivity::class.java)
                        .putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id)
                )
            }
        }
        if (entry.eatenAtMs == null) {
            labels += getString(R.string.planning_mark_eaten)
            actions += { markEaten(entry) }
        }
        labels += getString(R.string.planning_change_servings)
        actions += { promptServings(entry) }

        labels += getString(R.string.planning_change_guests)
        actions += { promptGuests(entry) }

        labels += getString(R.string.planning_change_recipe)
        actions += {
            pendingPick = LocalDate.parse(entry.dateIso) to entry.slot
            pickLauncher.launch(Intent(this, PickRecipeActivity::class.java))
        }

        labels += getString(R.string.planning_remove)
        actions += { removeEntry(entry) }

        AlertDialog.Builder(this)
            .setTitle(recipe?.nameEn ?: getString(R.string.planning_missing_recipe))
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun promptServings(entry: PlanEntry) {
        promptIntStepper(
            title = R.string.planning_change_servings,
            initial = entry.servings,
            min = 1,
        ) { newValue ->
            updateEntry(entry.copy(servings = newValue))
        }
    }

    private fun promptGuests(entry: PlanEntry) {
        promptIntStepper(
            title = R.string.planning_change_guests,
            initial = entry.guestCount,
            min = 0,
        ) { newValue ->
            updateEntry(entry.copy(guestCount = newValue))
        }
    }

    private fun promptIntStepper(
        title: Int,
        initial: Int,
        min: Int,
        onConfirm: (Int) -> Unit,
    ) {
        val values = (min..min + 20).map { it.toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(values, initial - min) { dialog, which ->
                onConfirm(which + min)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun assignRecipe(date: LocalDate, slot: PlanSlot, recipeId: String) {
        lifecycleScope.launch {
            val dao = IdunDatabase.get(this@PlanningActivity).planDao()
            val recipe = recipeRepo.byId(recipeId)
            val defaultServings = recipe?.servings ?: 1
            withContext(Dispatchers.IO) {
                val existing = dao.forDate(date.toString()).firstOrNull { it.slot == slot }
                if (existing == null) {
                    dao.upsert(
                        PlanEntry(
                            dateIso = date.toString(),
                            slot = slot,
                            recipeId = recipeId,
                            servings = defaultServings,
                        )
                    )
                } else {
                    dao.update(existing.copy(recipeId = recipeId))
                }
            }
            render()
        }
    }

    private fun updateEntry(entry: PlanEntry) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                IdunDatabase.get(this@PlanningActivity).planDao().update(entry)
            }
            render()
        }
    }

    private fun removeEntry(entry: PlanEntry) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                IdunDatabase.get(this@PlanningActivity).planDao().delete(entry)
            }
            render()
        }
    }

    private fun markEaten(entry: PlanEntry) {
        val now = System.currentTimeMillis()
        val servingsEaten = (entry.servings + entry.guestCount).coerceAtLeast(1).toDouble()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = IdunDatabase.get(this@PlanningActivity)
                db.planDao().update(entry.copy(eatenAtMs = now))
                db.mealLogDao().insert(
                    MealLogEntry(
                        recipeId = entry.recipeId,
                        timestampMs = now,
                        servingsEaten = servingsEaten,
                    )
                )
                BiosClient(this@PlanningActivity).pushMealIntake(now, servingsEaten)
            }
            render()
        }
    }

    private fun slotLabel(slot: PlanSlot): Int = when (slot) {
        PlanSlot.BREAKFAST -> R.string.slot_breakfast
        PlanSlot.LUNCH -> R.string.slot_lunch
        PlanSlot.DINNER -> R.string.slot_dinner
    }

    private fun today(): LocalDate = LocalDate.now()

    companion object {
        const val DAYS_AHEAD = 7
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
