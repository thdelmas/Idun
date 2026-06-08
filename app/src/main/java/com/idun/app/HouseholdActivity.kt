package com.idun.app

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.idun.app.data.IdunDatabase
import com.idun.app.data.Person
import com.idun.app.databinding.ActivityHouseholdBinding
import com.idun.app.util.Attendees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manage household members — the named people you can attach to a planned meal.
 *
 * Reached from the planner's overflow menu. Each member has a name and optional
 * comma-separated dietary tags (vegetarian, no nuts…), which the planner rolls
 * up to flag a meal's dietary needs. Deleting a member also clears them from any
 * meals they were attending so no dangling join rows survive.
 */
class HouseholdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHouseholdBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHouseholdBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.addButton.setOnClickListener { editPerson(null) }
        render()
    }

    private fun render() {
        lifecycleScope.launch {
            val people = withContext(Dispatchers.IO) {
                IdunDatabase.get(this@HouseholdActivity).personDao().all()
            }
            binding.householdEmpty.visibility = if (people.isEmpty()) View.VISIBLE else View.GONE
            binding.peopleContainer.removeAllViews()
            val inflater = LayoutInflater.from(this@HouseholdActivity)
            for (person in people) {
                binding.peopleContainer.addView(buildRow(inflater, person))
            }
        }
    }

    private fun buildRow(inflater: LayoutInflater, person: Person): View {
        val row = inflater.inflate(R.layout.item_person, binding.peopleContainer, false)
        row.findViewById<TextView>(R.id.person_name).text = person.name
        val tags = row.findViewById<TextView>(R.id.person_tags)
        val tagText = Attendees.tagsOf(person).joinToString(" · ")
        tags.text = tagText
        tags.visibility = if (tagText.isEmpty()) View.GONE else View.VISIBLE
        row.setOnClickListener { showPersonMenu(person) }
        return row
    }

    private fun showPersonMenu(person: Person) {
        AlertDialog.Builder(this)
            .setTitle(person.name)
            .setItems(
                arrayOf(getString(R.string.household_edit), getString(R.string.household_delete)),
            ) { _, which -> if (which == 0) editPerson(person) else delete(person) }
            .show()
    }

    private fun editPerson(existing: Person?) {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val nameField = EditText(this).apply {
            hint = getString(R.string.household_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(existing?.name.orEmpty())
        }
        val tagsField = EditText(this).apply {
            hint = getString(R.string.household_tags_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existing?.dietaryTags.orEmpty())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(nameField)
            addView(tagsField)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.household_add else R.string.household_edit)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val person = (existing ?: Person(name = name)).copy(
                    name = name,
                    dietaryTags = tagsField.text.toString().trim(),
                )
                save(person)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save(person: Person) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                IdunDatabase.get(this@HouseholdActivity).personDao().upsert(person)
            }
            render()
        }
    }

    private fun delete(person: Person) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = IdunDatabase.get(this@HouseholdActivity)
                db.planAttendeeDao().clearForPerson(person.id)
                db.personDao().delete(person)
            }
            render()
        }
    }
}
