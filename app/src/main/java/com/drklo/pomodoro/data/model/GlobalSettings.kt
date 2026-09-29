package com.drklo.pomodoro.data.model

enum class VibrationPattern {
    SHORT,
    MEDIUM,
    LONG,
    CALL;

    companion object {
        fun fromName(value: String?): VibrationPattern =
            entries.firstOrNull { it.name == value } ?: CALL
    }
}

/**
 * App-wide settings (PRD US-005, "Глобально"): sounds, vibration, always-on, idle alert,
 * autostart, end-of-day time and UI language.
 */
data class GlobalSettings(
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val vibrationPattern: VibrationPattern = VibrationPattern.CALL,
    val alwaysOnDisplay: Boolean = false,
    /** Auto-start the next pomodoro after a break ends (F-015). */
    val autostartPomodoros: Boolean = false,
    /** Auto-start the break after a pomodoro ends. */
    val autostartBreaks: Boolean = false,
    /** Idle alert period in minutes; 0 disables the alert (F-012). */
    val idleAlertMinutes: Int = 0,
    /**
     * While waiting to start the next phase (no autostart), keep the background colored as the
     * just-finished phase instead of the upcoming one, so it doesn't read as "already running".
     */
    val holdFinishedPhaseColor: Boolean = true,
    /** Time of day (local) at which a new logical day begins (F-022). Default = midnight. */
    val dayEndHour: Int = 0,
    val dayEndMinute: Int = 0,
    val language: AppLanguage = AppLanguage.RUSSIAN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
