package com.drklo.pomodoro.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.data.repository.ProjectStore
import com.drklo.pomodoro.data.repository.ProjectUsageRepository
import com.drklo.pomodoro.timer.SettingsSource
import com.drklo.pomodoro.timer.TimerEngine
import com.drklo.pomodoro.timer.TimerEvent
import com.drklo.pomodoro.timer.TimerService
import com.drklo.pomodoro.timer.TimerState
import com.drklo.pomodoro.util.launchSafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    app: Application,
    private val engine: TimerEngine,
    projectStore: ProjectStore,
    private val usageRepository: ProjectUsageRepository,
    settingsSource: SettingsSource
) : AndroidViewModel(app) {

    val timerState: StateFlow<TimerState> = engine.state

    val projects: StateFlow<List<Project>> = projectStore.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<GlobalSettings> = settingsSource.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettings())

    val recentStartCounts: StateFlow<Map<Long, Int>> = usageRepository.recentStartCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Incremented each time the daily goal is reached, and cleared once the celebration has played
     * (see [onFanfareShown]). A counter that only grows would replay 🎉 on every return to this
     * screen for the rest of the day, because entering the composition re-runs the effect.
     */
    private val _fanfareTrigger = MutableStateFlow(0)
    val fanfareTrigger: StateFlow<Int> = _fanfareTrigger.asStateFlow()

    init {
        // Keep the engine's active project in sync with the database: select a default on first
        // run, and re-apply edits (durations/colors) to the active project as they are saved.
        launchSafely {
            projects.collect { list ->
                if (list.isEmpty()) return@collect
                val active = engine.state.value.project
                val stillThere = list.firstOrNull { it.id == active?.id }
                when {
                    active == null -> engine.setActiveProject(list.first())
                    stillThere != null -> engine.refreshActiveProject(stillThere)
                    // The active project was archived (possibly mid-session): move the timer to the
                    // first remaining one, or the screen stays pointed at something that is gone.
                    // The service needs no nudge here: the engine goes idle, and idle is
                    // precisely when the service stops itself.
                    else -> engine.onActiveProjectArchived(list.first())
                }
            }
        }
        launchSafely {
            engine.events.collect { event ->
                if (event is TimerEvent.GoalReached) {
                    _fanfareTrigger.value += 1
                }
            }
        }
    }

    /** The celebration has played; drop it so re-entering the screen does not replay it. */
    fun onFanfareShown() {
        _fanfareTrigger.value = 0
    }

    /**
     * Called when the app returns to the foreground. Re-evaluates the session/today counters against
     * the current logical day so yesterday's bullets don't linger after the process survives overnight.
     */
    fun onAppForegrounded() {
        engine.refreshForNewDayIfNeeded()
    }

    /** Index of the currently active project within [projects], or 0. */
    fun indexOfActiveProject(): Int {
        val id = engine.state.value.project?.id ?: return 0
        return projects.value.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }

    fun onPlayPause() {
        val wasIdle = engine.state.value.status == TimerStatus.IDLE
        engine.togglePlayPause()
        if (wasIdle && engine.state.value.status == TimerStatus.RUNNING) {
            engine.state.value.project?.id?.let { projectId ->
                launchSafely { usageRepository.recordManualStart(projectId) }
            }
            TimerService.start(getApplication())
        }
    }

    fun onReset() {
        engine.reset()
    }

    /** Scrubs the active interval's remaining time to [fraction] of its total (dial drag). */
    fun onSeek(fraction: Float) {
        engine.seek(fraction)
    }

    /**
     * Carousel selection (F-008). While paused the timer is "parked" on its project, so swiping
     * only browses (no rebind) — the paused session is preserved and shown as a bookmark instead.
     */
    fun onSelectProject(index: Int) {
        val list = projects.value
        val project = list.getOrNull(index) ?: return
        val status = engine.state.value.status
        if (status == TimerStatus.RUNNING || status == TimerStatus.PAUSED) return
        if (project.id == engine.state.value.project?.id) return
        engine.setActiveProject(project)
    }

    /** Cycles the phase type manually (F-021): pomodoro -> short break -> long break -> pomodoro. */
    fun onChangePhase() {
        val next = when (engine.state.value.phase) {
            Phase.POMODORO -> Phase.SHORT_BREAK
            Phase.SHORT_BREAK -> Phase.LONG_BREAK
            Phase.LONG_BREAK -> Phase.POMODORO
        }
        engine.setPhase(next)
    }
}
