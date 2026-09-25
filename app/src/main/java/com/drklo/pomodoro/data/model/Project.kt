package com.drklo.pomodoro.data.model

/**
 * A user project (e.g. Work, Reading, Study) with its own pomodoro preset, colors and goals.
 * Durations are kept in minutes for editing; the timer converts to seconds.
 *
 * Per-project settings (PRD US-005): name, durations, pomodoro count, colors, daily goal,
 * long break (toggle + duration + interval).
 */
data class Project(
    val id: Long = 0,
    val name: String,
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    /** How many pomodoros make up one session (the bullets row). */
    val pomodorosPerSession: Int,
    /** ARGB color used while a pomodoro (focus) interval is running. */
    val pomodoroColor: Int,
    /** ARGB color used during breaks / idle. */
    val breakColor: Int,
    /** Target completed pomodoros per logical day; triggers the "fanfare" when reached. */
    val dailyGoal: Int,
    val longBreakEnabled: Boolean,
    val longBreakMinutes: Int,
    /** Number of pomodoros after which a long break replaces the short one. */
    val longBreakInterval: Int,
    /** Position in the carousel. */
    val orderIndex: Int,
    /** Optional pictogram selected by the user for faster visual recognition. */
    val icon: ProjectIcon = ProjectIcon.NONE,
    /** Set when the project was removed; archived projects live on in the reports only. */
    val archivedAt: Long? = null
) {

    val isArchived: Boolean get() = archivedAt != null

    /** Duration in seconds for the given phase of this project. */
    fun durationSecondsFor(phase: Phase): Int = when (phase) {
        Phase.POMODORO -> focusMinutes * 60
        Phase.SHORT_BREAK -> shortBreakMinutes * 60
        Phase.LONG_BREAK -> longBreakMinutes * 60
    }

    fun colorFor(phase: Phase): Int =
        if (phase == Phase.POMODORO) pomodoroColor else breakColor
}
