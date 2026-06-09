package com.idun.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * One planned meal. The plan is local-first like everything else; no sync.
 *
 * A day holds 0..N meal entries — no fixed breakfast / lunch / dinner slots.
 * Longo's 12h-window guidance and Johnson's 1-2 meals/day pattern both want
 * arbitrary meal counts at arbitrary times, so the model is just (date, time).
 *
 * date_iso is "YYYY-MM-DD"; queries are exact string comparisons that ignore
 * timezone drift. time_minutes is minutes from midnight, 0..1439, sorted
 * within a day to produce chronological ordering.
 *
 * guest_count tracks ad-hoc guests; the social follow-up adds household
 * attendees via the Person table.
 */
@Entity(tableName = "plan_entry")
data class PlanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_iso") val dateIso: String,
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val servings: Int = 1,
    @ColumnInfo(name = "guest_count") val guestCount: Int = 0,
    @ColumnInfo(name = "eaten_at_ms") val eatenAtMs: Long? = null,
)

@Dao
interface PlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlanEntry): Long

    @Update
    suspend fun update(entry: PlanEntry)

    @Delete
    suspend fun delete(entry: PlanEntry)

    @Query("SELECT * FROM plan_entry WHERE date_iso = :dateIso ORDER BY time_minutes")
    suspend fun forDate(dateIso: String): List<PlanEntry>

    @Query("SELECT * FROM plan_entry WHERE date_iso BETWEEN :fromIso AND :toIso ORDER BY date_iso, time_minutes")
    suspend fun inRange(fromIso: String, toIso: String): List<PlanEntry>

    /**
     * How many *upcoming* meals are planned: dated today-or-later and not yet
     * eaten. This is the count the free/paid soft cap consumes
     * ([com.idun.app.billing.PlanningLimits.canAddPlanEntry]) — counting only
     * upcoming, uneaten entries means a free user's capacity renews as days
     * pass and meals are checked off, so they're never permanently wedged.
     * Pass today's ISO date as [fromIso].
     */
    @Query("SELECT COUNT(*) FROM plan_entry WHERE date_iso >= :fromIso AND eaten_at_ms IS NULL")
    suspend fun countUpcoming(fromIso: String): Int

    @Query("SELECT * FROM plan_entry WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): PlanEntry?

    @Query("DELETE FROM plan_entry WHERE id = :id")
    suspend fun deleteById(id: Long)
}
