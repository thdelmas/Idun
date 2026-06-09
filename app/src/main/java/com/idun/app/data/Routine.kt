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
 * Recurring meal templates (shipped v0.5.0).
 *
 * weekday is ISO: 1 = Monday … 7 = Sunday. time_minutes is minutes from
 * midnight (same shape as PlanEntry.timeMinutes). A routine row says "on this
 * weekday at this time, plan this recipe by default". [RoutineScheduler.plan]
 * walks a date range and inserts PlanEntry rows for any (date, time) without an
 * existing entry within ±30 minutes (collision rule lives in [RoutineScheduler],
 * which is unit-tested); [com.idun.app.RoutineActivity] is the manage + apply UI.
 */
@Entity(tableName = "routine")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val weekday: Int,
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
)

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routine: Routine): Long

    @Delete
    suspend fun delete(routine: Routine)

    @Query("SELECT * FROM routine ORDER BY weekday, time_minutes")
    suspend fun all(): List<Routine>

    @Query("SELECT * FROM routine WHERE weekday = :weekday ORDER BY time_minutes")
    suspend fun forWeekday(weekday: Int): List<Routine>
}
