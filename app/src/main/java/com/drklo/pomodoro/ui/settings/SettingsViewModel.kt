package com.drklo.pomodoro.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drklo.pomodoro.data.backup.ProjectBackup
import com.drklo.pomodoro.data.model.AppLanguage
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.ThemeMode
import com.drklo.pomodoro.data.model.VibrationPattern
import com.drklo.pomodoro.data.repository.BackupRepository
import com.drklo.pomodoro.data.repository.ProjectStore
import com.drklo.pomodoro.data.repository.SettingsRepository
import com.drklo.pomodoro.util.launchSafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import java.time.LocalDate

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val projectRepo: ProjectStore,
    private val backupRepo: BackupRepository,
    private val contentResolver: ContentResolver,
    private val appVersion: String,
    /**
     * Whether a timer is counting right now. A lambda rather than the engine itself, because this
     * is the only thing settings needs to know about it — and it must be asked at the moment of the
     * import, not captured when the screen opened.
     */
    private val timerIsBusy: () -> Boolean
) : ViewModel() {

    private val _backup = MutableStateFlow<BackupOutcome?>(null)

    /** What the last export or import did, for the screen to report once and then forget. */
    val backup: StateFlow<BackupOutcome?> = _backup.asStateFlow()

    val settings: StateFlow<GlobalSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettings())

    val projects: StateFlow<List<Project>> = projectRepo.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSound(value: Boolean) = launchSafely { settingsRepo.setSoundEnabled(value) }
    fun setVibrate(value: Boolean) = launchSafely { settingsRepo.setVibrateEnabled(value) }
    fun setVibrationPattern(value: VibrationPattern) =
        launchSafely { settingsRepo.setVibrationPattern(value) }
    fun setAlwaysOn(value: Boolean) = launchSafely { settingsRepo.setAlwaysOnDisplay(value) }
    fun setAutostartPomodoros(value: Boolean) = launchSafely { settingsRepo.setAutostartPomodoros(value) }
    fun setAutostartBreaks(value: Boolean) = launchSafely { settingsRepo.setAutostartBreaks(value) }
    fun setIdleAlertMinutes(value: Int) = launchSafely { settingsRepo.setIdleAlertMinutes(value) }
    fun setHoldFinishedPhaseColor(value: Boolean) = launchSafely { settingsRepo.setHoldFinishedPhaseColor(value) }
    fun setDayEnd(hour: Int, minute: Int) = launchSafely { settingsRepo.setDayEnd(hour, minute) }

    /**
     * Persists the language and only then runs [onSaved]. The caller recreates the activity there,
     * and the new language is picked up in `attachBaseContext`, which re-reads the setting: firing
     * both at once let the activity come back up on the old language, and nothing would correct it
     * until the next launch, because rotation is intercepted by configChanges.
     */
    fun setLanguage(language: AppLanguage, onSaved: () -> Unit) = launchSafely {
        settingsRepo.setLanguage(language)
        onSaved()
    }
    fun setThemeMode(mode: ThemeMode) = launchSafely { settingsRepo.setThemeMode(mode) }

    /** Soft delete: the project leaves the list, its pomodoros stay in the reports. */
    fun deleteProject(project: Project) = launchSafely { projectRepo.archive(project) }

    /**
     * Writes every project and the last year of its history to [uri], which the user picked through
     * the system file dialog — so no storage permission is involved and the file lands wherever they
     * keep things, including outside the app, where it survives an uninstall.
     */
    fun exportTo(uri: Uri) = launchSafely {
        _backup.value = BackupOutcome.Working
        _backup.value = try {
            val stream = contentResolver.openOutputStream(uri)
                ?: return@launchSafely run { _backup.value = BackupOutcome.FileUnavailable() }
            val written = stream.use { out ->
                out.bufferedWriter().use { writer ->
                    backupRepo.export(writer, System.currentTimeMillis(), LocalDate.now(), appVersion)
                }
            }
            BackupOutcome.Exported(written)
        } catch (e: IOException) {
            BackupOutcome.FileUnavailable(e.message)
        }
    }

    /**
     * Replaces everything with the contents of [uri].
     *
     * Refused outright while a timer runs: the engine holds the active project, and pulling its row
     * out from under it would leave a timer counting for something that no longer exists.
     */
    fun importFrom(uri: Uri) = launchSafely {
        if (timerIsBusy()) {
            _backup.value = BackupOutcome.RefusedTimerRunning
            return@launchSafely
        }
        _backup.value = BackupOutcome.Working
        _backup.value = try {
            val stream = contentResolver.openInputStream(uri)
                ?: return@launchSafely run { _backup.value = BackupOutcome.FileUnavailable() }
            val restored = stream.use { input ->
                input.bufferedReader().use { reader -> backupRepo.import(reader) }
            }
            BackupOutcome.Imported(restored)
        } catch (e: ProjectBackup.BackupException) {
            BackupOutcome.Rejected(e.failure)
        } catch (e: IOException) {
            BackupOutcome.FileUnavailable(e.message)
        }
    }

    /** Called once the screen has shown the outcome, so reopening settings does not replay it. */
    fun acknowledgeBackup() {
        _backup.value = null
    }
}

/** What the settings screen tells the user after an export or an import. */
sealed interface BackupOutcome {
    data object Working : BackupOutcome
    data class Exported(val projects: Int) : BackupOutcome
    data class Imported(val projects: Int) : BackupOutcome

    /** The file was readable but is not an acceptable backup; [failure] says what was wrong. */
    data class Rejected(val failure: ProjectBackup.Failure) : BackupOutcome

    /** The document could not be opened or read at all — deleted, revoked, offline cloud file. */
    data class FileUnavailable(val detail: String? = null) : BackupOutcome

    data object RefusedTimerRunning : BackupOutcome
}
