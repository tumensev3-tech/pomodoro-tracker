package com.drklo.pomodoro.timer

import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus

/** Immutable snapshot of the single global timer, observed by the UI and the service. */
data class TimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    val phase: Phase = Phase.POMODORO,
    val project: Project? = null,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    /** Filled bullets in the current session (0..pomodorosPerSession). */
    val completedInSession: Int = 0,
    /** Completed pomodoros toward today's goal for the active project. */
    val completedToday: Int = 0,
    /** Pomodoros done since the last long break (per project), drives long-break scheduling. */
    val pomodorosSinceLongBreak: Int = 0,
    /** Legacy between-phase waiting state. */
    val awaitingNext: Boolean = false,
    /**
     * The current phase reached zero and is waiting for an explicit decision:
     * accept the transition, or extend this same phase by 5/10/15 minutes.
     */
    val awaitingDecision: Boolean = false,
    /** Toggled by the idle alert to flip on-screen colors (F-012). */
    val idleAlertActive: Boolean = false
) {
    val progress: Float
        get() = if (totalSeconds <= 0) {
            0f
        } else {
            ((totalSeconds - remainingSeconds).toFloat() / totalSeconds).coerceIn(0f, 1f)
        }

    val isRunning: Boolean get() = status == TimerStatus.RUNNING
}
