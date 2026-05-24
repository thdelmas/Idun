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
 * Household members and guests. Stubbed for v1.1 — the planning round only
 * tracks guest *count* on each PlanEntry; the household-member UI and the
 * attendee picker on plan entries land in the social follow-up.
 *
 * Schema is in place now so adding the social UI later doesn't need a Room
 * migration.
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

    @Query("SELECT * FROM person WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Person?
}
