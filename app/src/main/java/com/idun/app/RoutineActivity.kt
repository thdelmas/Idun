package com.idun.app

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.idun.app.data.IdunDatabase
import com.idun.app.data.Recipe
import com.idun.app.data.RecipeRepository
import com.idun.app.data.Routine
import com.idun.app.data.RoutineScheduler
import com.idun.app.data.displayName
import com.idun.app.reminders.ReminderScheduler
import com.idun.app.databinding.ActivityRoutinesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Manage recurring meal templates and apply them onto the plan.
 *
 * Reached from the planner's overflow menu — a sub-screen, not a bottom-nav
 * destination. Each routine is "(weekday, time) → recipe"; the list renders
 * flat in weekday/time order. Adding chains the same three pickers the planner
 * uses (weekday → time → recipe) so the interaction stays familiar. "Apply"
 * runs [RoutineScheduler] across the next [PlanningActivity.DAYS_AHEAD] days and
 * inserts the non-colliding rows, leaving hand-planned meals untouched.
 */
class RoutineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoutinesBinding
    private lateinit var pickLauncher: ActivityResultLauncher<Intent>
    private lateinit var recipeRepo: RecipeRepository

    /** When non-null, the next recipe-pick creates a routine at this (weekday, time). */
    private var pendingAdd: Pair<Int, Int>? = null
    /** When non-null, the next recipe-pick replaces this routine's recipe. */
    private var pendingReplace: Routine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoutinesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        recipeRepo = RecipeRepository(this)

        pickLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val recipeId = result.data?.getStringExtra(PickRecipeActivity.RESULT_RECIPE_ID)
                ?: return@registerForActivityResult
            val replace = pendingReplace
            val add = pendingAdd
            pendingReplace = null
            pendingAdd = null
            when {
                replace != null -> save(replace.copy(recipeId = recipeId))
                add != null -> save(Routine(name = "", weekday = add.first, timeMinutes = add.second, recipeId = recipeId))
            }
        }

        binding.addButton.setOnClickListener { startAdd() }
        binding.applyButton.setOnClickListener { applyToPlan() }
        render()
    }

    private fun render() {
        lifecycleScope.launch {
            val routines = withContext(Dispatchers.IO) {
                IdunDatabase.get(this@RoutineActivity).routineDao().all()
            }
            val recipesById = recipeRepo.all().associateBy { it.id }

            binding.routinesEmpty.visibility = if (routines.isEmpty()) View.VISIBLE else View.GONE
            binding.applyButton.isEnabled = routines.isNotEmpty()

            binding.routinesContainer.removeAllViews()
            val inflater = LayoutInflater.from(this@RoutineActivity)
            for (routine in routines) {
                binding.routinesContainer.addView(buildRow(inflater, routine, recipesById[routine.recipeId]))
            }
        }
    }

    private fun buildRow(inflater: LayoutInflater, routine: Routine, recipe: Recipe?): View {
        val row = inflater.inflate(R.layout.item_routine, binding.routinesContainer, false)
        row.findViewById<TextView>(R.id.routine_weekday).text = weekdayName(routine.weekday)
        row.findViewById<TextView>(R.id.routine_time).text = formatTimeOfDay(routine.timeMinutes)
        row.findViewById<TextView>(R.id.routine_recipe).text =
            recipe?.displayName() ?: getString(R.string.planning_missing_recipe)
        row.setOnClickListener { openEditDialog(routine, recipe) }
        return row
    }

    private fun startAdd() {
        promptWeekday(DEFAULT_WEEKDAY) { weekday ->
            promptTimePicker(DEFAULT_TIME) { minutes ->
                pendingAdd = weekday to minutes
                pickLauncher.launch(Intent(this, PickRecipeActivity::class.java))
            }
        }
    }

    private fun openEditDialog(routine: Routine, recipe: Recipe?) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        labels += getString(R.string.routine_change_weekday)
        actions += { promptWeekday(routine.weekday) { save(routine.copy(weekday = it)) } }

        labels += getString(R.string.planning_change_time)
        actions += { promptTimePicker(routine.timeMinutes) { save(routine.copy(timeMinutes = it)) } }

        labels += getString(R.string.planning_change_recipe)
        actions += {
            pendingReplace = routine
            pickLauncher.launch(Intent(this, PickRecipeActivity::class.java))
        }

        labels += getString(R.string.routine_delete)
        actions += { delete(routine) }

        AlertDialog.Builder(this)
            .setTitle(recipe?.displayName() ?: getString(R.string.planning_missing_recipe))
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun promptWeekday(initial: Int, onPicked: (Int) -> Unit) {
        val names = (1..7).map { weekdayName(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.routine_change_weekday)
            .setSingleChoiceItems(names, initial - 1) { dialog, which ->
                dialog.dismiss()
                onPicked(which + 1)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptTimePicker(initialMinutes: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinutes / 60,
            initialMinutes % 60,
            DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun applyToPlan() {
        AlertDialog.Builder(this)
            .setTitle(R.string.routines_apply)
            .setMessage(getString(R.string.routines_apply_confirm, PlanningActivity.DAYS_AHEAD))
            .setPositiveButton(R.string.routines_apply_confirm_yes) { _, _ -> runApply() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runApply() {
        lifecycleScope.launch {
            val from = LocalDate.now()
            val to = from.plusDays(PlanningActivity.DAYS_AHEAD - 1L)
            val recipeServings = recipeRepo.all().associate { it.id to it.servings }
            val inserted = withContext(Dispatchers.IO) {
                val db = IdunDatabase.get(this@RoutineActivity)
                val rows = RoutineScheduler.plan(
                    routines = db.routineDao().all(),
                    existing = db.planDao().inRange(from.toString(), to.toString()),
                    from = from,
                    to = to,
                    defaultServings = { recipeServings[it] ?: 1 },
                )
                rows.forEach { db.planDao().upsert(it) }
                rows.size
            }
            if (inserted > 0) ReminderScheduler(applicationContext).reschedule()
            val msg = if (inserted == 0) getString(R.string.routines_applied_none)
            else resources.getQuantityString(R.plurals.routines_applied_count, inserted, inserted)
            Toast.makeText(this@RoutineActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun save(routine: Routine) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                IdunDatabase.get(this@RoutineActivity).routineDao().upsert(routine)
            }
            render()
        }
    }

    private fun delete(routine: Routine) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                IdunDatabase.get(this@RoutineActivity).routineDao().delete(routine)
            }
            render()
        }
    }

    private fun weekdayName(isoWeekday: Int): String =
        DayOfWeek.of(isoWeekday).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    private fun formatTimeOfDay(minutes: Int): String {
        val local = LocalTime.of(minutes / 60, minutes % 60)
        return local.format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
        )
    }

    companion object {
        private const val DEFAULT_WEEKDAY = 1
        private const val DEFAULT_TIME = 8 * 60
    }
}
