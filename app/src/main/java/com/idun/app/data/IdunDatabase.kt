package com.idun.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Single Room database for the local-first store.
 *
 * v2 bump adds plan_entry, person, and routine — fallbackToDestructiveMigration
 * is fine while v1 is still pre-release; once we ship to anything beyond
 * personal devices we'll need real migrations.
 */
@Database(
    entities = [
        MealLogEntry::class,
        PlanEntry::class,
        Person::class,
        Routine::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class IdunDatabase : RoomDatabase() {
    abstract fun mealLogDao(): MealLogDao
    abstract fun planDao(): PlanDao
    abstract fun personDao(): PersonDao
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile private var INSTANCE: IdunDatabase? = null

        fun get(context: Context): IdunDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                IdunDatabase::class.java,
                "idun.db",
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
