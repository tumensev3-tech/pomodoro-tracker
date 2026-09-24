package com.drklo.pomodoro.timer

import android.util.Log
import com.drklo.pomodoro.data.LogicalDay
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.util.loggingExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

/**
 * The single global pomodoro timer (PRD F-003). Owns all timing/phase logic; the UI and the
 * foreground service merely observe [state] and forward user actions.
 *
 * **Threading.** The engine is driven from two directions at once: the main thread (the UI and the
 * notification's Pause/Reset actions) and its own ticking coroutine on a background dispatcher.
 * Every field below and every publication to [_state] happens under [lock], so the two can never
 * interleave halfway through a transition. Nothing suspends while holding it — `synchronized` takes
 * a non-suspending lambda, so the compiler enforces that rather than a comment.
 */
class TimerEngine(
    private val settingsSource: SettingsSource,
    private val stats: PomodoroStats,
    private val effects: PhaseFeedback,
    private val time: TimeSource,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler(TAG))
) {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    /** Guards everything mutable in this class. See the class comment. */
    private val lock = Any()

    private var settings: GlobalSettings = GlobalSettings()

    private var idleJob: Job? = null

    /**
     * Identifies the interval currently being counted down. Anything that ends or replaces an
     * interval bumps it; a tick loop from an older generation notices and exits on its own.
     */
    private var tickGeneration: Int = 0

    /** Logical day for which [TimerState.completedToday]/[TimerState.completedInSession] were computed. */
    private var sessionDayKey: String? = null

    /** Monotonic (elapsedRealtime) deadline at which the running phase reaches zero. */
    private var deadlineElapsed: Long = 0L

    /** Serializes database work: a read must not overtake a write that was queued before it. */
    private val statsMutex = Mutex()

    init {
        launchGuarded("observe settings") {
            settingsSource.settings.collect { value -> synchronized(lock) { settings = value } }
        }
        // The day boundary has to be noticed even when nobody touches the phone. With the screen
        // kept on (F-011) the app never leaves the foreground, so the ON_START check never fires:
        // you come back in the morning to yesterday's filled bullets and the feeling of having
        // already done something. A minute's granularity is plenty for a boundary measured in days.
        launchGuarded("watch for the day boundary") {
            while (true) {
                delay(DAY_WATCH_INTERVAL_MS)
                refreshForNewDayIfNeeded()
            }
        }
    }

    // --- User actions ---------------------------------------------------------------------

    /**
     * Selects the active project and resets the session. Allowed while stopped or paused
     * (carousel F-008); switching away from a paused interval discards it.
     */
    fun setActiveProject(project: Project): Unit = synchronized(lock) {
        if (_state.value.status == TimerStatus.RUNNING) return
        selectProject(project)
    }

    /**
     * The active project was archived out from under the session (F-R5-01) — switch to
     * [replacement]. Unlike [setActiveProject] this cannot be refused, because leaving the timer
     * pointed at a project that is gone locks the screen: no carousel page matches it, so play,
     * reset and swipe all stop responding. The unfinished interval is lost on purpose; it does not
     * survive a process death either.
     */
    fun onActiveProjectArchived(replacement: Project): Unit = synchronized(lock) {
        selectProject(replacement)
    }

    /**
     * Re-applies edited data for the currently active project (e.g. changed durations/colors)
     * without resetting session progress. Adopts a new duration only when idle; a paused interval
     * keeps its remaining time.
     */
    fun refreshActiveProject(updated: Project): Unit = synchronized(lock) {
        val s = _state.value
        if (s.project?.id != updated.id) return
        _state.value = when (s.status) {
            TimerStatus.RUNNING, TimerStatus.PAUSED -> s.copy(project = updated)
            TimerStatus.IDLE -> {
                val total = updated.durationSecondsFor(s.phase)
                // Room re-emits the whole project list whenever any row changes, so editing a
                // *different* project used to silently undo a scrub here. Only a duration that
                // actually changed is worth resetting the interval for.
                if (total == s.totalSeconds) {
                    s.copy(project = updated)
                } else {
                    s.copy(project = updated, totalSeconds = total, remainingSeconds = total)
                }
            }
        }
    }

    fun togglePlayPause(): Unit = synchronized(lock) {
        when (_state.value.status) {
            TimerStatus.IDLE -> start(userInitiated = true)
            TimerStatus.RUNNING -> pause()
            TimerStatus.PAUSED -> resume()
        }
    }

    /** Resets the current interval back to its full duration (F-020). Keeps session progress. */
    fun reset(): Unit = synchronized(lock) {
        val s = _state.value
        val project = s.project ?: return
        supersedeTick()
        stopIdleAlert()
        val total = project.durationSecondsFor(s.phase)
        _state.value = s.copy(
            status = TimerStatus.IDLE,
            totalSeconds = total,
            remainingSeconds = total,
            awaitingNext = false,
            idleAlertActive = false
        )
    }

    /**
     * Rolls the in-memory session/today counters onto the current logical day (F-022). The counters
     * live only in [TimerState] and are otherwise recomputed solely on project (re)selection, so when
     * the process survives past the day-end boundary (e.g. overnight) yesterday's filled bullets stay
     * on screen. Call this when the app returns to the foreground.
     *
     * Only a stopped/between-phase state rolls over; a running or paused (parked) interval is left
     * untouched. A stale "awaiting next break" prompt from yesterday is cleared back to a pomodoro.
     */
    fun refreshForNewDayIfNeeded(): Unit = synchronized(lock) {
        val s = _state.value
        val project = s.project ?: return
        if (s.status != TimerStatus.IDLE) return
        if (sessionDayKey != null && sessionDayKey == currentDayKey()) return
        stopIdleAlert()
        _state.value = if (s.awaitingNext || s.phase != Phase.POMODORO) {
            val total = project.durationSecondsFor(Phase.POMODORO)
            s.copy(
                phase = Phase.POMODORO,
                totalSeconds = total,
                remainingSeconds = total,
                awaitingNext = false,
                pomodorosSinceLongBreak = 0
            )
        } else {
            s.copy(pomodorosSinceLongBreak = 0)
        }
        refreshCompletedToday(project)
    }

    /**
     * Scrubs the current interval to [fraction] of its total remaining (0 = almost done, 1 = full),
     * letting the user "fast-forward" a phase they started late (e.g. forgot to start the timer for
     * an already-running meeting). Allowed while RUNNING, PAUSED or IDLE. Clamped to at least 1s so a
     * stray drag can't auto-complete the phase. Session/today progress is untouched.
     */
    fun seek(fraction: Float): Unit = synchronized(lock) {
        val s = _state.value
        if (s.project == null || s.totalSeconds <= 0) return
        val target = (fraction.coerceIn(0f, 1f) * s.totalSeconds).toInt().coerceIn(1, s.totalSeconds)
        if (s.status == TimerStatus.RUNNING) {
            deadlineElapsed = time.elapsedRealtimeMs() + target * MILLIS_PER_SECOND
        }
        _state.value = s.copy(remainingSeconds = target)
    }

    /** Manually changes the phase type (F-021). Allowed while IDLE or PAUSED. */
    fun setPhase(phase: Phase): Unit = synchronized(lock) {
        val s = _state.value
        if (s.status == TimerStatus.RUNNING) return
        val project = s.project ?: return
        supersedeTick()
        stopIdleAlert()
        val total = project.durationSecondsFor(phase)
        _state.value = s.copy(
            status = TimerStatus.IDLE,
            phase = phase,
            totalSeconds = total,
            remainingSeconds = total,
            awaitingNext = false,
            idleAlertActive = false
        )
    }

    // --- Internal transitions -------------------------------------------------------------

    private fun selectProject(project: Project) {
        supersedeTick()
        stopIdleAlert()
        val total = project.durationSecondsFor(Phase.POMODORO)
        _state.value = TimerState(
            status = TimerStatus.IDLE,
            phase = Phase.POMODORO,
            project = project,
            totalSeconds = total,
            remainingSeconds = total
        )
        refreshCompletedToday(project)
    }

    private fun start(userInitiated: Boolean) {
        val s = _state.value
        if (s.project == null || s.remainingSeconds <= 0) return
        stopIdleAlert()
        _state.value = runningFrom(s, userInitiated)
        startTicking()
    }

    /**
     * Turns [from] into a running interval: arms the deadline, plays the start feedback and
     * announces the phase. Returning the state instead of publishing it is what lets
     * [completePhase] hand over to an autostarted phase in a single frame.
     */
    private fun runningFrom(from: TimerState, userInitiated: Boolean): TimerState {
        deadlineElapsed = time.elapsedRealtimeMs() + from.remainingSeconds * MILLIS_PER_SECOND
        if (userInitiated && settings.soundEnabled) effects.playStart()
        if (userInitiated && settings.vibrateEnabled) effects.vibrate()
        _events.tryEmit(TimerEvent.PhaseStarted(from.phase))
        return from.copy(
            status = TimerStatus.RUNNING,
            awaitingNext = false,
            idleAlertActive = false
        )
    }

    private fun pause() {
        supersedeTick()
        val remaining = remainingFromDeadline()
        _state.value = _state.value.copy(status = TimerStatus.PAUSED, remainingSeconds = remaining)
        // Idle alert also applies to a forgotten pause (#2).
        startIdleAlert()
    }

    private fun resume() {
        stopIdleAlert()
        _state.value = runningFrom(_state.value, userInitiated = false)
        startTicking()
    }

    private fun completePhase() {
        val finished = _state.value
        val project = finished.project ?: return
        val finishedPhase = finished.phase

        // Read the clock once: the database row and the in-memory counters must not end up
        // disagreeing about which logical day this phase belongs to.
        val dayKey = currentDayKey()
        // A phase that started before the end-of-day boundary and ended after it belongs to the new
        // day (F-022). Without carrying the counters over, the row lands on the new day while
        // "today" and the session bullets keep growing yesterday's tally — the main screen and the
        // reports then disagree for the rest of the night, and the daily goal never fires again.
        val rolledOverToNewDay = sessionDayKey != null && sessionDayKey != dayKey
        sessionDayKey = dayKey
        val s = if (rolledOverToNewDay) {
            finished.copy(completedToday = 0, completedInSession = 0, pomodorosSinceLongBreak = 0)
        } else {
            finished
        }

        if (settings.soundEnabled) effects.playEnd()
        if (settings.vibrateEnabled) effects.vibrate(settings.vibrationPattern)
        _events.tryEmit(TimerEvent.PhaseFinished(finishedPhase))

        val next = if (finishedPhase == Phase.POMODORO) {
            afterPomodoro(s, project, dayKey)
        } else {
            val total = project.durationSecondsFor(Phase.POMODORO)
            s.copy(
                status = TimerStatus.IDLE,
                phase = Phase.POMODORO,
                totalSeconds = total,
                remainingSeconds = total,
                awaitingNext = true,
                idleAlertActive = false
            )
        }

        val autostart = if (next.phase.isBreak) settings.autostartBreaks else settings.autostartPomodoros
        if (autostart) {
            // Publish "the next phase is already running" as one frame. An intermediate IDLE would
            // tell the foreground service the timer had stopped — and it stops itself on IDLE,
            // leaving the autostarted phase to run unprotected in the background.
            _state.value = runningFrom(next, userInitiated = false)
            startTicking()
        } else {
            _state.value = next
            startIdleAlert()
        }
    }

    private fun afterPomodoro(s: TimerState, project: Project, dayKey: String): TimerState {
        persistCompletedPomodoro(project, dayKey, durationSeconds = s.totalSeconds)
        val newToday = s.completedToday + 1
        val perSession = project.pomodorosPerSession.coerceAtLeast(1)
        val newSession = (s.completedInSession % perSession) + 1
        val sinceLong = s.pomodorosSinceLongBreak + 1

        if (project.dailyGoal in 1..newToday && s.completedToday < project.dailyGoal) {
            _events.tryEmit(TimerEvent.GoalReached(project.id))
        }

        val takeLong = project.longBreakEnabled && sinceLong >= project.longBreakInterval
        val nextPhase = if (takeLong) Phase.LONG_BREAK else Phase.SHORT_BREAK
        val total = project.durationSecondsFor(nextPhase)
        return s.copy(
            status = TimerStatus.IDLE,
            phase = nextPhase,
            totalSeconds = total,
            remainingSeconds = total,
            completedInSession = newSession,
            completedToday = newToday,
            pomodorosSinceLongBreak = if (takeLong) 0 else sinceLong,
            awaitingNext = true,
            idleAlertActive = false
        )
    }

    // --- Ticking & idle alert -------------------------------------------------------------

    private fun startTicking() {
        val generation = supersedeTick()
        scope.launch {
            while (synchronized(lock) { tick(generation) }) {
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * One pass of the countdown, under the lock. Returns false when this loop is done — either the
     * interval it was counting has been superseded (paused, reset, replaced) or it just ended.
     */
    private fun tick(generation: Int): Boolean {
        if (generation != tickGeneration) return false
        val remaining = remainingFromDeadline()
        val expired = remaining <= 0
        if (expired) {
            completePhase()
        } else {
            _state.value = _state.value.copy(remainingSeconds = remaining)
        }
        return !expired
    }

    /**
     * Invalidates the interval being counted down and returns the new generation. Nothing cancels
     * the tick coroutine: the loop checks its own generation and steps out. That matters because
     * [completePhase] runs *inside* that loop — cancelling from there used to mean the method was
     * finishing a transition inside a coroutine it had already cancelled, which worked only as long
     * as nobody added a suspension point to it.
     */
    private fun supersedeTick(): Int = ++tickGeneration

    private fun remainingFromDeadline(): Int {
        val remainingMs = deadlineElapsed - time.elapsedRealtimeMs()
        return ceil(remainingMs / MILLIS_PER_SECOND.toDouble()).toInt().coerceAtLeast(0)
    }

    /** The timer is stalled when paused or between phases awaiting the next one. */
    private fun isStalled(): Boolean {
        val s = _state.value
        return s.status == TimerStatus.PAUSED || (s.status == TimerStatus.IDLE && s.awaitingNext)
    }

    private fun startIdleAlert() {
        stopIdleAlert()
        val minutes = settings.idleAlertMinutes
        if (minutes <= 0) return
        idleJob = scope.launch {
            while (synchronized(lock) { isStalled() }) {
                delay(minutes * MILLIS_PER_MINUTE)
                if (synchronized(lock) { !isStalled() }) break
                // Gentle strobe: a few quick color flips over ~2s (not a permanent switch, #5).
                repeat(IDLE_STROBE_BLINKS) {
                    if (synchronized(lock) { !isStalled() }) return@launch
                    setIdleAlertActive(true)
                    delay(IDLE_STROBE_HALF_MS)
                    setIdleAlertActive(false)
                    delay(IDLE_STROBE_HALF_MS)
                }
            }
            setIdleAlertActive(false)
        }
    }

    private fun setIdleAlertActive(active: Boolean) = synchronized(lock) {
        _state.value = _state.value.copy(idleAlertActive = active)
    }

    private fun stopIdleAlert() {
        idleJob?.cancel()
        idleJob = null
        if (_state.value.idleAlertActive) {
            _state.value = _state.value.copy(idleAlertActive = false)
        }
    }

    // --- Stats ----------------------------------------------------------------------------

    private fun currentDayKey(): String =
        LogicalDay.keyFor(time.now(), settings.dayEndHour, settings.dayEndMinute)

    /**
     * Fire-and-forget work. A failed query costs the user one pomodoro in the history; it must
     * never cost them the running session, so the failure is logged and the timer carries on. The
     * engine cannot rely on [scope]'s exception handler for this — the scope is supplied from
     * outside and may have none.
     */
    private fun launchGuarded(what: String, block: suspend () -> Unit) {
        scope.launch { guard(what, block) }
    }

    /** As [launchGuarded], but database calls also run one at a time, in the order they were queued. */
    private fun launchStats(what: String, block: suspend () -> Unit) {
        scope.launch { statsMutex.withLock { guard(what, block) } }
    }

    private suspend fun guard(what: String, block: suspend () -> Unit) {
        runCatching { block() }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.e(TAG, "Failed to $what", error)
        }
    }

    private fun persistCompletedPomodoro(project: Project, dayKey: String, durationSeconds: Int) {
        val end = time.wallClockMs()
        val start = end - durationSeconds * MILLIS_PER_SECOND
        launchStats("record a completed pomodoro") {
            stats.recordCompletedPomodoro(project.id, dayKey, start, end, durationSeconds)
        }
    }

    private fun refreshCompletedToday(project: Project) {
        val dayKey = currentDayKey()
        sessionDayKey = dayKey
        launchStats("read today's completed count") {
            val today = stats.completedCount(project.id, dayKey)
            // Restore session bullets from today's completed count so progress survives a restart
            // (PRD: restore the count of completed pomodoros).
            val perSession = project.pomodorosPerSession.coerceAtLeast(1)
            val sessionBullets = if (today <= 0) 0 else ((today - 1) % perSession) + 1
            synchronized(lock) {
                val s = _state.value
                if (s.project?.id == project.id) {
                    _state.value = s.copy(completedToday = today, completedInSession = sessionBullets)
                }
            }
        }
    }

    private companion object {
        const val TAG = "TimerEngine"
        const val TICK_INTERVAL_MS = 200L
        const val DAY_WATCH_INTERVAL_MS = 60_000L
        const val MILLIS_PER_SECOND = 1000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val IDLE_STROBE_BLINKS = 3
        const val IDLE_STROBE_HALF_MS = 320L
    }
}
