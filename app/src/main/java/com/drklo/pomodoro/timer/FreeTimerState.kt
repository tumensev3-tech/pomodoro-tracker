package com.drklo.pomodoro.timer

data class FreeTimerState(
    val running: Boolean = false,
    val name: String = "",
    val elapsedSeconds: Int = 0,
    val startedWallClockMs: Long = 0L
)
