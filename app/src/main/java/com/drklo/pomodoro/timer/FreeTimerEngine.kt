package com.drklo.pomodoro.timer

import android.util.Log
import com.drklo.pomodoro.data.LogicalDay
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.repository.ActivitySessionKind
import com.drklo.pomodoro.data.repository.ActivitySessionRepository
import com.drklo.pomodoro.util.loggingExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Open-ended stopwatch used for activities that do not have a predefined duration or required rest.
 */
class FreeTimerEngine(
    settingsSource: SettingsSource,
    private val sessions: ActivitySessionRepository,
    private val time: TimeSource,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler(TAG))
) {
    private val _state = MutableStateFlow(FreeTimerState())
    val state: StateFlow<FreeTimerState> = _state.asStateFlow()

    private val lock = Any()
    private var settings = GlobalSettings()
    private var startedElapsedMs = 0L
    private var ticker: Job? = null

    init {
        scope.launch {
            settingsSource.settings.collect { value ->
                synchronized(lock) { settings = value }
            }
        }
    }

    fun start(rawName: String): Boolean = synchronized(lock) {
        val name = rawName.trim()
        if (name.isBlank() || _state.value.running) return false

        startedElapsedMs = time.elapsedRealtimeMs()
        _state.value = FreeTimerState(
            running = true,
            name = name,
            elapsedSeconds = 0,
            startedWallClockMs = time.wallClockMs()
        )
        startTicker()
        true
    }

    fun stop(): Boolean = synchronized(lock) {
        val current = _state.value
        if (!current.running) return false

        val elapsedSeconds = elapsedSecondsNow().coerceAtLeast(1)
        val endEpochMs = time.wallClockMs()
        val dayKey = LogicalDay.keyFor(
            now = time.now(),
            dayEndHour = settings.dayEndHour,
            dayEndMinute = settings.dayEndMinute
        )

        ticker?.cancel()
        ticker = null
        _state.value = FreeTimerState()

        scope.launch {
            runCatching {
                sessions.record(
                    name = current.name,
                    kind = ActivitySessionKind.FREE,
                    projectId = null,
                    startEpochMs = current.startedWallClockMs,
                    endEpochMs = endEpochMs,
                    durationSeconds = elapsedSeconds,
                    dayKey = dayKey
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "Failed to record free timer session", error)
            }
        }
        true
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                synchronized(lock) {
                    val current = _state.value
                    if (!current.running) return@launch
                    _state.value = current.copy(elapsedSeconds = elapsedSecondsNow())
                }
                delay(TICK_MS)
            }
        }
    }

    private fun elapsedSecondsNow(): Int {
        val elapsedMs = time.elapsedRealtimeMs() - startedElapsedMs
        return ceil(elapsedMs / MILLIS_PER_SECOND.toDouble()).toInt().coerceAtLeast(0)
    }

    private companion object {
        const val TAG = "FreeTimerEngine"
        const val TICK_MS = 500L
        const val MILLIS_PER_SECOND = 1000L
    }
}
