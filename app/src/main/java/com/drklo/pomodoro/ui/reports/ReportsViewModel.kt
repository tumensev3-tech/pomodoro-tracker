package com.drklo.pomodoro.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drklo.pomodoro.data.LogicalDay
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.repository.ActivitySessionRepository
import com.drklo.pomodoro.data.repository.ProjectStore
import com.drklo.pomodoro.data.repository.StatsRepository
import com.drklo.pomodoro.timer.SettingsSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime

/** Aggregated totals shown in the header rows of both report tabs. */
data class ActivityTimeTotal(
    val name: String,
    val todaySeconds: Int,
    val weekSeconds: Int,
    val totalSeconds: Int
)

data class ReportsSummary(
    val totalFocusSec: Int = 0,
    val todayFocusSec: Int = 0,
    val weekFocusSec: Int = 0,
    val totalPomodoros: Int = 0,
    val todayPomodoros: Int = 0,
    val weekPomodoros: Int = 0
)

class ReportsViewModel(
    projectStore: ProjectStore,
    statsRepository: StatsRepository,
    activitySessionRepository: ActivitySessionRepository,
    settingsSource: SettingsSource
) : ViewModel() {

    val projects: StateFlow<List<Project>> = projectStore.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<PomodoroLog>> = statsRepository.observeLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val activitySessions = activitySessionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val settings: StateFlow<GlobalSettings> = settingsSource.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettings())

    /**
     * The logical "today" every element of this screen is drawn against, honoring the end-of-day
     * boundary. Recomputed when the settings change or a pomodoro lands — a new log entry is the
     * cheapest signal available that the clock has moved and the day may have rolled over.
     *
     * There is deliberately only one of these. The summary used to work out its own date, so a
     * pomodoro finished at 00:05 counted as today in the header while every chart still belonged to
     * yesterday: one screen, two answers to the same question.
     */
    val today: StateFlow<LocalDate> = combine(settings, logs) { s, _ ->
        LogicalDay.dateFor(LocalDateTime.now(), s.dayEndHour, s.dayEndMinute)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    val activityTotals: StateFlow<List<ActivityTimeTotal>> =
        combine(activitySessions, today) { sessions, today ->
            val todayKey = today.toString()
            val weekKeys = (0..6).map { today.minusDays(it.toLong()).toString() }.toSet()
            sessions
                .groupBy { it.name }
                .map { (name, rows) ->
                    ActivityTimeTotal(
                        name = name,
                        todaySeconds = rows.filter { it.dayKey == todayKey }.sumOf { it.durationSeconds },
                        weekSeconds = rows.filter { it.dayKey in weekKeys }.sumOf { it.durationSeconds },
                        totalSeconds = rows.sumOf { it.durationSeconds }
                    )
                }
                .sortedWith(
                    compareByDescending<ActivityTimeTotal> { it.weekSeconds }
                        .thenByDescending { it.totalSeconds }
                        .thenBy { it.name }
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<ReportsSummary> =
        combine(logs, today) { log, today ->
            val todayKey = today.toString()
            val weekKeys = (0..6).map { today.minusDays(it.toLong()).toString() }.toSet()
            ReportsSummary(
                totalFocusSec = log.sumOf { it.durationSeconds },
                todayFocusSec = log.filter { it.dayKey == todayKey }.sumOf { it.durationSeconds },
                weekFocusSec = log.filter { it.dayKey in weekKeys }.sumOf { it.durationSeconds },
                totalPomodoros = log.size,
                todayPomodoros = log.count { it.dayKey == todayKey },
                weekPomodoros = log.count { it.dayKey in weekKeys }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsSummary())
}
