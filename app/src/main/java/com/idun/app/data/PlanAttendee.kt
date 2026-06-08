package com.idun.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Links a planned meal to the household members eating it — the named-attendee
 * layer on top of PlanEntry's anonymous `guest_count`. A meal can have any
 * number of attendees; the same person can attend many meals, so this is a
 * plain join table keyed on (plan_entry_id, person_id).
 *
 * No Room foreign keys — the app does the cleanup explicitly ([clearForEntry]
 * when a meal is removed, [clearForPerson] when a member is deleted) to keep
 * the schema simple and the deletes obvious at the call site.
 */
@Entity(
    tableName = "plan_attendee",
    primaryKeys = ["plan_entry_id", "person_id"],
    indices = [Index("person_id")],
)
data class PlanAttendee(
    @ColumnInfo(name = "plan_entry_id") val planEntryId: Long,
    @ColumnInfo(name = "person_id") val personId: Long,
)

@Dao
interface PlanAttendeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attendee: PlanAttendee)

    @Query("DELETE FROM plan_attendee WHERE plan_entry_id = :entryId")
    suspend fun clearForEntry(entryId: Long)

    @Query("DELETE FROM plan_attendee WHERE person_id = :personId")
    suspend fun clearForPerson(personId: Long)

    @Query("SELECT person_id FROM plan_attendee WHERE plan_entry_id = :entryId")
    suspend fun personIdsForEntry(entryId: Long): List<Long>

    @Query("SELECT * FROM plan_attendee")
    suspend fun all(): List<PlanAttendee>

    /** Replace an entry's whole attendee set in one shot. */
    @Transaction
    suspend fun setAttendees(entryId: Long, personIds: List<Long>) {
        clearForEntry(entryId)
        for (personId in personIds) insert(PlanAttendee(entryId, personId))
    }
}
