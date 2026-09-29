package com.drklo.pomodoro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drklo.pomodoro.data.model.AppLanguage
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.ThemeMode
import com.drklo.pomodoro.data.model.VibrationPattern
import com.drklo.pomodoro.timer.SettingsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) : SettingsSource {

    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATE = booleanPreferencesKey("vibrate_enabled")
        val VIBRATION_PATTERN = stringPreferencesKey("vibration_pattern")
        val ALWAYS_ON = booleanPreferencesKey("always_on_display")
        val AUTOSTART = booleanPreferencesKey("autostart")
        val AUTOSTART_BREAKS = booleanPreferencesKey("autostart_breaks")
        val IDLE_ALERT_MIN = intPreferencesKey("idle_alert_minutes")
        val HOLD_FINISHED_COLOR = booleanPreferencesKey("hold_finished_phase_color")
        val DAY_END_HOUR = intPreferencesKey("day_end_hour")
        val DAY_END_MINUTE = intPreferencesKey("day_end_minute")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme_mode")

        /** Set once the default projects have been created; see ProjectRepository.ensureSeeded. */
        val SEEDED = booleanPreferencesKey("projects_seeded")
    }

    override val settings: Flow<GlobalSettings> = context.dataStore.data.catch { e ->
        // A preferences file that cannot be read (corrupted, or the disk is unhappy) must not take
        // the timer down with it: fall back to defaults so the app keeps running, and let the user
        // re-set what they need. Anything that is not an I/O problem is a real bug — rethrow it.
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p ->
        val defaults = GlobalSettings()
        GlobalSettings(
            soundEnabled = p[Keys.SOUND] ?: defaults.soundEnabled,
            vibrateEnabled = p[Keys.VIBRATE] ?: defaults.vibrateEnabled,
            vibrationPattern = VibrationPattern.fromName(p[Keys.VIBRATION_PATTERN]),
            alwaysOnDisplay = p[Keys.ALWAYS_ON] ?: defaults.alwaysOnDisplay,
            autostartPomodoros = p[Keys.AUTOSTART] ?: defaults.autostartPomodoros,
            autostartBreaks = p[Keys.AUTOSTART_BREAKS] ?: defaults.autostartBreaks,
            idleAlertMinutes = p[Keys.IDLE_ALERT_MIN] ?: defaults.idleAlertMinutes,
            holdFinishedPhaseColor = p[Keys.HOLD_FINISHED_COLOR] ?: defaults.holdFinishedPhaseColor,
            dayEndHour = p[Keys.DAY_END_HOUR] ?: defaults.dayEndHour,
            dayEndMinute = p[Keys.DAY_END_MINUTE] ?: defaults.dayEndMinute,
            language = AppLanguage.fromTag(p[Keys.LANGUAGE]),
            themeMode = ThemeMode.fromName(p[Keys.THEME])
        )
    }

    /**
     * The chosen language, read synchronously. Needed where there is nothing to suspend in and no
     * time to wait: [android.content.ContextWrapper.attachBaseContext] and a service's `onCreate`
     * both have to resolve resources before anything else runs.
     */
    fun currentLanguage(): AppLanguage = runBlocking { settings.first().language }

    suspend fun setSoundEnabled(value: Boolean) = edit { it[Keys.SOUND] = value }
    suspend fun setVibrateEnabled(value: Boolean) = edit { it[Keys.VIBRATE] = value }
    suspend fun setVibrationPattern(value: VibrationPattern) =
        edit { it[Keys.VIBRATION_PATTERN] = value.name }
    suspend fun setAlwaysOnDisplay(value: Boolean) = edit { it[Keys.ALWAYS_ON] = value }
    suspend fun setAutostartPomodoros(value: Boolean) = edit { it[Keys.AUTOSTART] = value }
    suspend fun setAutostartBreaks(value: Boolean) = edit { it[Keys.AUTOSTART_BREAKS] = value }
    suspend fun setIdleAlertMinutes(value: Int) = edit { it[Keys.IDLE_ALERT_MIN] = value.coerceAtLeast(0) }
    suspend fun setHoldFinishedPhaseColor(value: Boolean) = edit { it[Keys.HOLD_FINISHED_COLOR] = value }

    suspend fun setDayEnd(hour: Int, minute: Int) = edit {
        it[Keys.DAY_END_HOUR] = hour.coerceIn(0, 23)
        it[Keys.DAY_END_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun wasSeeded(): Boolean = context.dataStore.data.first()[Keys.SEEDED] ?: false

    suspend fun markSeeded() = edit { it[Keys.SEEDED] = true }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.tag }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
