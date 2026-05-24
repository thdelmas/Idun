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
 * Which slot of the day a plan entry belongs to. SNACK is omitted from v1 —
 * three slots cover the canonical Mediterranean / Blue-Zone day; we can add
 * snack later without a migration since the enum is stored by name.
 */
enum class PlanSlot { BREAKFAST, LUNCH, DINNER }

/**
 * One planned meal. The plan is local-first like everything else; no sync.
 *
 * date_iso is stored as a "YYYY-MM-DD" string rather than millis so date-range
 * queries can be exact string comparisons and ignore timezone drift entirely —
 * a planned dinner on the 24th stays a dinner on the 24th if the user travels.
 *
 * guest_count + servings together determine the shopping-list scaling factor.
 * Household attendees (foreign keys into the Person table) land in the social
 * pass; for v1 we only persist a head-count of guests.
 */
@Entity(tableName = "plan_entry")
data class PlanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_iso") val dateIso: String,
    val slot: PlanSlot,
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

    @Query("SELECT * FROM plan_entry WHERE date_iso = :dateIso ORDER BY slot")
    suspend fun forDate(dateIso: String): List<PlanEntry>

    @Query("SELECT * FROM plan_entry WHERE date_iso BETWEEN :fromIso AND :toIso ORDER BY date_iso, slot")
    suspend fun inRange(fromIso: String, toIso: String): List<PlanEntry>

    @Query("SELECT * FROM plan_entry WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): PlanEntry?

    @Query("DELETE FROM plan_entry WHERE id = :id")
    suspend fun deleteById(id: Long)
}
