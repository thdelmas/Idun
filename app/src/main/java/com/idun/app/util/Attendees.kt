package com.idun.app.util

import com.idun.app.data.Person

/**
 * Pure display helpers for a meal's named attendees. Kept out of the Activity so
 * the name/dietary formatting is unit-testable without inflating views.
 *
 * Dietary tags are stored per person as a free comma-separated string; [tagsOf]
 * normalises them and [dietaryNote] rolls the distinct tags across all attendees
 * into one short line so the planner can flag "someone here is vegetarian"
 * without repeating the tag per name.
 */
object Attendees {

    fun tagsOf(person: Person): List<String> =
        person.dietaryTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun names(persons: List<Person>): String =
        persons.joinToString(", ") { it.name }

    fun dietaryNote(persons: List<Person>): String =
        persons.flatMap { tagsOf(it) }
            .distinctBy { it.lowercase() }
            .joinToString(" · ")
}
