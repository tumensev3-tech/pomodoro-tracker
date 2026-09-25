package com.drklo.pomodoro.data

import android.content.Context
import androidx.room.Room
import com.drklo.pomodoro.data.db.AppDatabase
import com.drklo.pomodoro.data.repository.ActivitySessionRepository
import com.drklo.pomodoro.data.repository.BackupRepository
import com.drklo.pomodoro.data.repository.ProjectRepository
import com.drklo.pomodoro.data.repository.ProjectUsageRepository
import com.drklo.pomodoro.data.repository.SettingsRepository
import com.drklo.pomodoro.data.repository.StatsRepository
import com.drklo.pomodoro.timer.FreeTimerEngine
import com.drklo.pomodoro.timer.SystemTimeSource
import com.drklo.pomodoro.timer.TimerEffects
import com.drklo.pomodoro.timer.TimerEngine

/**
 * Manual dependency container (simple service locator). Held by [com.drklo.pomodoro.PomodoroApp]
 * and reused across ViewModels and the timer service.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.NAME
    ).addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5
    ).build()

    val projectRepository: ProjectRepository by lazy { ProjectRepository(database) }
    val projectUsageRepository: ProjectUsageRepository by lazy {
        ProjectUsageRepository(context.applicationContext)
    }
    val statsRepository: StatsRepository by lazy { StatsRepository(database) }
    val activitySessionRepository: ActivitySessionRepository by lazy {
        ActivitySessionRepository(database)
    }
    val backupRepository: BackupRepository by lazy { BackupRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context.applicationContext) }

    private val timerEffects: TimerEffects by lazy { TimerEffects(context.applicationContext) }

    /** The single, app-wide timer instance (guarantees one active timer, F-003). */
    val timerEngine: TimerEngine by lazy {
        TimerEngine(settingsRepository, statsRepository, timerEffects, SystemTimeSource)
    }

    val freeTimerEngine: FreeTimerEngine by lazy {
        FreeTimerEngine(settingsRepository, activitySessionRepository, SystemTimeSource)
    }
}
