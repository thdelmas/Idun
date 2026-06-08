package com.idun.app.util

import com.idun.app.data.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendeesTest {

    private fun person(name: String, tags: String = "") = Person(name = name, dietaryTags = tags)

    @Test
    fun `tags are split trimmed and blanks dropped`() {
        assertEquals(listOf("vegetarian", "no nuts"), Attendees.tagsOf(person("L", " vegetarian , no nuts ,")))
    }

    @Test
    fun `empty tag string yields no tags`() {
        assertEquals(emptyList<String>(), Attendees.tagsOf(person("L", "")))
    }

    @Test
    fun `names join in order`() {
        assertEquals("Léa, Mum", Attendees.names(listOf(person("Léa"), person("Mum"))))
    }

    @Test
    fun `dietary note dedupes across attendees case-insensitively`() {
        val people = listOf(
            person("Léa", "vegetarian, no nuts"),
            person("Sam", "Vegetarian, dairy-free"),
        )
        assertEquals("vegetarian · no nuts · dairy-free", Attendees.dietaryNote(people))
    }

    @Test
    fun `dietary note is empty when nobody has tags`() {
        assertEquals("", Attendees.dietaryNote(listOf(person("Léa"), person("Mum"))))
    }
}
