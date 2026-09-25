package com.drklo.pomodoro.data.repository

import com.drklo.pomodoro.data.db.ActivitySessionDao
import com.drklo.pomodoro.data.db.ActivitySessionEntity
import com.drklo.pomodoro.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

enum class ActivitySessionKind {
    FREE,
    WORK,
    BREAK
}

class ActivitySessionRepository(db: AppDatabase) {

    private val dao: ActivitySessionDao = db.activitySessionDao()

    suspend fun record(
        name: String,
        kind: ActivitySessionKind,
        projectId: Long?,
        startEpochMs: Long,
        endEpochMs: Long,
        durationSeconds: Int,
        dayKey: String
    ) {
        dao.insert(
            ActivitySessionEntity(
                name = name,
                kind = kind.name,
                projectId = projectId,
                startEpochMs = startEpochMs,
                endEpochMs = endEpochMs,
                durationSeconds = durationSeconds,
                dayKey = dayKey
            )
        )
    }

    fun observeAll(): Flow<List<ActivitySessionEntity>> =
        dao.observeAll().flowOn(Dispatchers.IO)

    suspend fun betweenDays(
        fromDayKey: String,
        toDayKey: String
    ): List<ActivitySessionEntity> = dao.betweenDays(fromDayKey, toDayKey)
}
