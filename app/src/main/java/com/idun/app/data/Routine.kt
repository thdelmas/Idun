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
 * Recurring meal templates. Stubbed for v1.1.
 *
 * weekday is ISO: 1 = Monday … 7 = Sunday. A routine row says "on this
 * weekday at this slot, plan this recipe by default". The apply-routine
 * action (next round) walks a date range and inserts PlanEntry rows for
 * any (date, slot) without an existing entry.
 *
 * No reminders — CLAUDE.md keeps notifications out of scope; Smokeless
 * owns reminder patterns in the Bios ecosystem.
 */
@Entity(tableName = "routine")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val weekday: Int,
    val slot: PlanSlot,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
)

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routine: Routine): Long

    @Delete
    suspend fun delete(routine: Routine)

    @Query("SELECT * FROM routine ORDER BY weekday, slot")
    suspend fun all(): List<Routine>

    @Query("SELECT * FROM routine WHERE weekday = :weekday")
    suspend fun forWeekday(weekday: Int): List<Routine>
}
