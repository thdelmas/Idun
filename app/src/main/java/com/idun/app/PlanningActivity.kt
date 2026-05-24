package com.idun.app

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
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
import com.idun.app.data.Recipe
import com.idun.app.data.RecipeRepository
import com.idun.app.data.displayName
import com.idun.app.databinding.ActivityPlanningBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Week-view planner with no fixed meal slots.
 *
 * Each day holds a chronological list of meal entries (0..N). Empty day shows
 * a single "+ Add the day's first meal" CTA; otherwise the meals render in
 * time order with a small "+ Add another meal" row at the bottom. A
 * monospace subtitle on each day card shows the eating window when there are
 * 2+ meals — useful for Longo's 12h-window guidance and the broader
 * time-restricted-eating crowd.
 *
 * Tapping an empty CTA opens a TimePicker, then routes to the recipe picker.
 * Tapping a filled meal opens an edit dialog: view recipe / mark eaten /
 * servings / guests / time / change recipe / remove.
 *
 * The FAB jumps to the shopping list aggregated across the visible window.
 */
class PlanningActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlanningBinding
    private lateinit var pickLauncher: ActivityResultLauncher<Intent>
    private lateinit var recipeRepo: RecipeRepository

    /** When non-null, [pickLauncher]'s next result lands on this date at this time. */
    private var pendingPick: Pair<LocalDate, Int>? = null
    /** When non-null, [pickLauncher]'s next result replaces this entry's recipe. */
    private var pendingReplace: PlanEntry? = null

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
            val replace = pendingReplace
            val add = pendingPick
            pendingReplace = null
            pendingPick = null
            when {
                replace != null -> updateEntry(replace.copy(recipeId = recipeId))
                add != null -> createMeal(add.first, add.second, recipeId)
            }
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
                IdunDatabase.get(this@PlanningActivity)
                    .planDao().inRange(from.toString(), to.toString())
            }
            val plansByDate = plans.groupBy { it.dateIso }
            val recipesById = recipeRepo.all().associateBy { it.id }

            binding.daysContainer.removeAllViews()
            val inflater = LayoutInflater.from(this@PlanningActivity)
            for (i in 0 until DAYS_AHEAD) {
                val date = from.plusDays(i.toLong())
                val dayPlans = plansByDate[date.toString()].orEmpty().sortedBy { it.timeMinutes }
                binding.daysContainer.addView(buildDayCard(inflater, date, dayPlans, recipesById))
            }
        }
    }

    private fun buildDayCard(
        inflater: LayoutInflater,
        date: LocalDate,
        meals: List<PlanEntry>,
        recipesById: Map<String, Recipe>,
    ): View {
        val card = inflater.inflate(R.layout.item_plan_day, binding.daysContainer, false)
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        card.findViewById<TextView>(R.id.day_weekday).text =
            weekday.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        card.findViewById<TextView>(R.id.day_date).text = date.format(DATE_FORMATTER)
        card.findViewById<TextView>(R.id.day_today_badge).visibility =
            if (date == today()) View.VISIBLE else View.GONE

        renderWindowSummary(card.findViewById(R.id.day_window_summary), meals)

        val slots = card.findViewById<LinearLayout>(R.id.slots_container)
        if (meals.isEmpty()) {
            slots.addView(makeAddMealRow(inflater, slots, date, isFirst = true, lastTime = null))
        } else {
            for (meal in meals) {
                slots.addView(makeMealRow(inflater, slots, meal, recipesById[meal.recipeId]))
            }
            slots.addView(
                makeAddMealRow(
                    inflater,
                    slots,
                    date,
                    isFirst = false,
                    lastTime = meals.last().timeMinutes,
                )
            )
        }
        return card
    }

    private fun renderWindowSummary(view: TextView, meals: List<PlanEntry>) {
        if (meals.size < 2) {
            view.visibility = View.GONE
            return
        }
        val first = meals.minOf { it.timeMinutes }
        val last = meals.maxOf { it.timeMinutes }
        val windowMinutes = last - first
        val fastingMinutes = 1440 - windowMinutes
        view.visibility = View.VISIBLE
        view.text = getString(
            R.string.planning_window_summary,
            formatDuration(windowMinutes),
            formatDuration(fastingMinutes),
        )
    }

    private fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h" else "${h}h${m.toString().padStart(2, '0')}"
    }

    private fun makeMealRow(
        inflater: LayoutInflater,
        parent: LinearLayout,
        entry: PlanEntry,
        recipe: Recipe?,
    ): View {
        val row = inflater.inflate(R.layout.item_plan_meal, parent, false)
        row.findViewById<TextView>(R.id.meal_time).text = formatTimeOfDay(entry.timeMinutes)
        val main = row.findViewById<TextView>(R.id.meal_recipe)
        val meta = row.findViewById<TextView>(R.id.meal_meta)
        val eatenCheck = row.findViewById<TextView>(R.id.meal_eaten_check)

        main.text = recipe?.displayName() ?: getString(R.string.planning_missing_recipe)

        val parts = mutableListOf<String>()
        parts.add(resources.getQuantityString(R.plurals.servings_count, entry.servings, entry.servings))
        if (entry.guestCount > 0) {
            parts.add(resources.getQuantityString(R.plurals.guests_count, entry.guestCount, entry.guestCount))
        }
        meta.text = parts.joinToString("  ·  ")
        meta.visibility = View.VISIBLE
        eatenCheck.visibility = if (entry.eatenAtMs != null) View.VISIBLE else View.GONE

        row.setOnClickListener { openEditDialog(entry, recipe) }
        return row
    }

    private fun makeAddMealRow(
        inflater: LayoutInflater,
        parent: LinearLayout,
        date: LocalDate,
        isFirst: Boolean,
        lastTime: Int?,
    ): View {
        val row = inflater.inflate(R.layout.item_plan_add_meal, parent, false)
        val label = row.findViewById<TextView>(R.id.add_meal_label)
        label.setText(
            if (isFirst) R.string.planning_add_first_meal
            else R.string.planning_add_another_meal,
        )
        row.setOnClickListener {
            val defaultTime = nextDefaultTime(lastTime)
            promptTimePicker(defaultTime) { picked ->
                pendingPick = date to picked
                pickLauncher.launch(Intent(this, PickRecipeActivity::class.java))
            }
        }
        return row
    }

    private fun nextDefaultTime(lastTime: Int?): Int = when {
        lastTime == null -> DEFAULT_FIRST_TIME
        else -> (lastTime + 4 * 60).coerceAtMost(22 * 60)
    }

    private fun promptTimePicker(initialMinutes: Int, onPicked: (Int) -> Unit) {
        val hour = initialMinutes / 60
        val minute = initialMinutes % 60
        val is24h = DateFormat.is24HourFormat(this)
        TimePickerDialog(
            this,
            { _, pickedHour, pickedMinute -> onPicked(pickedHour * 60 + pickedMinute) },
            hour,
            minute,
            is24h,
        ).show()
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
        labels += getString(R.string.planning_change_time)
        actions += {
            promptTimePicker(entry.timeMinutes) { newMinutes ->
                updateEntry(entry.copy(timeMinutes = newMinutes))
            }
        }
        labels += getString(R.string.planning_change_servings)
        actions += { promptIntStepper(R.string.planning_change_servings, entry.servings, 1) {
            updateEntry(entry.copy(servings = it))
        } }
        labels += getString(R.string.planning_change_guests)
        actions += { promptIntStepper(R.string.planning_change_guests, entry.guestCount, 0) {
            updateEntry(entry.copy(guestCount = it))
        } }
        labels += getString(R.string.planning_change_recipe)
        actions += {
            pendingReplace = entry
            pickLauncher.launch(Intent(this, PickRecipeActivity::class.java))
        }
        labels += getString(R.string.planning_remove)
        actions += { removeEntry(entry) }

        AlertDialog.Builder(this)
            .setTitle(recipe?.displayName() ?: getString(R.string.planning_missing_recipe))
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
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

    private fun createMeal(date: LocalDate, timeMinutes: Int, recipeId: String) {
        lifecycleScope.launch {
            val recipe = recipeRepo.byId(recipeId)
            val defaultServings = recipe?.servings ?: 1
            withContext(Dispatchers.IO) {
                IdunDatabase.get(this@PlanningActivity).planDao().upsert(
                    PlanEntry(
                        dateIso = date.toString(),
                        timeMinutes = timeMinutes,
                        recipeId = recipeId,
                        servings = defaultServings,
                    )
                )
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

    private fun formatTimeOfDay(minutes: Int): String {
        val local = LocalTime.of(minutes / 60, minutes % 60)
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
        return local.format(formatter)
    }

    private fun today(): LocalDate = LocalDate.now()

    companion object {
        const val DAYS_AHEAD = 7
        private const val DEFAULT_FIRST_TIME = 8 * 60
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
