package com.idun.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

/**
 * Local meal log. One row per eaten meal. The Bios write is fire-and-forget
 * on top of this — the local row is the truth, the Bios write is the
 * companion-stream side-effect (may fail silently).
 */
@Entity(tableName = "meal_log")
data class MealLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    @ColumnInfo(name = "servings_eaten") val servingsEaten: Double = 1.0,
    @ColumnInfo(name = "note") val note: String? = null,
)

@Dao
interface MealLogDao {
    @Insert
    suspend fun insert(entry: MealLogEntry): Long

    @Query("SELECT * FROM meal_log ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<MealLogEntry>

    @Query("SELECT * FROM meal_log WHERE timestamp_ms BETWEEN :startMs AND :endMs ORDER BY timestamp_ms DESC")
    suspend fun inRange(startMs: Long, endMs: Long): List<MealLogEntry>
}

@Database(entities = [MealLogEntry::class], version = 1, exportSchema = false)
abstract class IdunDatabase : RoomDatabase() {
    abstract fun mealLogDao(): MealLogDao

    companion object {
        @Volatile private var INSTANCE: IdunDatabase? = null

        fun get(context: Context): IdunDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                IdunDatabase::class.java,
                "idun.db"
            ).build().also { INSTANCE = it }
        }
    }
}
