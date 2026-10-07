package com.drklo.pomodoro.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Real elapsed-time sessions. Free timers land here first; regular work/break phases will be added
 * in the weekly-statistics pass so all actual time can be reported from one journal.
 */
@Entity(tableName = "activity_session")
data class ActivitySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val projectId: Long?,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationSeconds: Int,
    val dayKey: String
)
