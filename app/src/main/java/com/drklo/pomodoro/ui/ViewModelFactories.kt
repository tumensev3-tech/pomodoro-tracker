package com.drklo.pomodoro.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.ui.main.MainViewModel
import com.drklo.pomodoro.ui.reports.ReportsViewModel
import com.drklo.pomodoro.ui.settings.ProjectEditViewModel
import com.drklo.pomodoro.ui.settings.SettingsViewModel

/**
 * Where the ViewModels' dependencies come from — the one place that knows about [PomodoroApp] and
 * its container.
 *
 * The ViewModels used to reach into that container themselves, which left nothing to substitute:
 * testing any of them meant standing up a real Application, Room and DataStore, and that was the
 * obstacle behind the untestable findings around them (F-R6-06, step T3). Now the wiring lives out
 * here, and each ViewModel takes what it needs through its constructor.
 */
object ViewModelFactories {

    val main: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            MainViewModel(
                app = app,
                engine = app.container.timerEngine,
                freeTimerEngine = app.container.freeTimerEngine,
                projectStore = app.container.projectRepository,
                usageRepository = app.container.projectUsageRepository,
                settingsSource = app.container.settingsRepository
            )
        }
    }

    val reports: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            ReportsViewModel(
                projectStore = app.container.projectRepository,
                statsRepository = app.container.statsRepository,
                settingsSource = app.container.settingsRepository
            )
        }
    }

    val settings: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            SettingsViewModel(
                settingsRepo = app.container.settingsRepository,
                projectRepo = app.container.projectRepository,
                backupRepo = app.container.backupRepository,
                contentResolver = app.contentResolver,
                // From the package manager rather than BuildConfig, which this module does not
                // generate. It is written into the file for a human reading it later, nothing more.
                appVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty(),
                // Asked at the moment of the import, not captured now.
                timerIsBusy = {
                    app.container.timerEngine.state.value.status != TimerStatus.IDLE ||
                        app.container.timerEngine.state.value.awaitingDecision ||
                        app.container.freeTimerEngine.state.value.running
                }
            )
        }
    }

    val projectEdit: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = app()
            ProjectEditViewModel(repo = app.container.projectRepository)
        }
    }

    private fun CreationExtras.app(): PomodoroApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PomodoroApp
}
