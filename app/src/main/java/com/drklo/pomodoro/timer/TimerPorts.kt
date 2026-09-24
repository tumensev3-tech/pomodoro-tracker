package com.drklo.pomodoro.timer

import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.VibrationPattern
import kotlinx.coroutines.flow.Flow

/**
 * What [TimerEngine] needs from the rest of the app — nothing more. The concrete collaborators
 * cannot be built off a device (settings need a `Context` and DataStore, feedback opens a
 * `SoundPool`), so the engine depends on these three ports instead and the real classes implement
 * them. Each port is deliberately as narrow as the engine's actual usage.
 */

/** Read-only view of the global settings the engine reacts to. */
interface SettingsSource {
    val settings: Flow<GlobalSettings>
}

/** The counters a finished pomodoro is written to, and read back from on project selection. */
interface PomodoroStats {
    suspend fun completedCount(projectId: Long, dayKey: String): Int

    suspend fun recordCompletedPomodoro(
        projectId: Long,
        dayKey: String,
        startEpochMs: Long,
        endEpochMs: Long,
        durationSeconds: Int
    )
}

/** Sound and vibration at phase boundaries. */
interface PhaseFeedback {
    fun playStart()
    fun playEnd()
    fun vibrate()

    fun vibrate(pattern: VibrationPattern) = vibrate()
}
