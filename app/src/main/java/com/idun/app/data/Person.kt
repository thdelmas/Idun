package com.idun.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Household members (shipped v0.6.0). [com.idun.app.HouseholdActivity] is the
 * member-management UI; [com.idun.app.PlanningActivity] picks attendees per
 * meal (linked through the PlanAttendee join table).
 *
 * Two distinct notions of "who's eating" coexist on a PlanEntry: guest_count is
 * an ad-hoc *count* of unnamed guests, while named household members are linked
 * via PlanAttendee — used for per-meal dietary notes.
 */
@Entity(tableName = "person")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "is_household") val isHousehold: Boolean = true,
    @ColumnInfo(name = "dietary_tags") val dietaryTags: String = "",
)

@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: Person): Long

    @Delete
    suspend fun delete(person: Person)

    @Query("SELECT * FROM person WHERE is_household = 1 ORDER BY name")
    suspend fun household(): List<Person>

    @Query("SELECT * FROM person ORDER BY name")
    suspend fun all(): List<Person>

    @Query("SELECT * FROM person WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Person?
}
