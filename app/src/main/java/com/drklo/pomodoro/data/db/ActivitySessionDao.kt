package com.drklo.pomodoro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivitySessionDao {

    @Insert
    suspend fun insert(entry: ActivitySessionEntity)

    @Query("SELECT * FROM activity_session ORDER BY startEpochMs ASC")
    fun observeAll(): Flow<List<ActivitySessionEntity>>

    @Query(
        "SELECT * FROM activity_session WHERE dayKey >= :fromDayKey AND dayKey <= :toDayKey " +
            "ORDER BY startEpochMs ASC"
    )
    suspend fun betweenDays(fromDayKey: String, toDayKey: String): List<ActivitySessionEntity>

    @Query("DELETE FROM activity_session")
    suspend fun deleteAll()
}
