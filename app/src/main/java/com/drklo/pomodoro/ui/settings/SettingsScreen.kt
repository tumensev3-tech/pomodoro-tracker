package com.drklo.pomodoro.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.backup.ProjectBackup
import com.drklo.pomodoro.data.model.AppLanguage
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.ThemeMode
import com.drklo.pomodoro.data.model.VibrationPattern
import com.drklo.pomodoro.ui.ViewModelFactories
import com.drklo.pomodoro.ui.common.ConfirmDeleteProjectDialog
import com.drklo.pomodoro.ui.common.SegmentedChoice
import com.drklo.pomodoro.ui.common.Stepper
import com.drklo.pomodoro.ui.common.TimeOfDayField
import com.drklo.pomodoro.util.BatteryOptimization
import com.drklo.pomodoro.util.findActivity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProject: (Long) -> Unit,
    onAddProject: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = ViewModelFactories.settings)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The project the user asked to delete, held until they confirm.
    var pendingDelete by remember { mutableStateOf<Project?>(null) }
    pendingDelete?.let { project ->
        ConfirmDeleteProjectDialog(
            projectName = project.name,
            onConfirm = {
                pendingDelete = null
                viewModel.deleteProject(project)
            },
            onDismiss = { pendingDelete = null }
        )
    }

    var batteryExempt by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { batteryExempt = BatteryOptimization.isIgnoring(context) }
    // Also re-checked whenever the screen comes back: the setting can be changed from Android's own
    // settings, and the launcher callback only ever fires for the trip this screen started.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryExempt = BatteryOptimization.isIgnoring(context)
    }

    // The file the user picked, held until they confirm the replacement.
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    val suggestedFileName = stringResource(R.string.backup_file_name, LocalDate.now().toString())

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        viewModel.importFrom(uri)
                    }
                ) { Text(stringResource(R.string.backup_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Reported in a dialog rather than a transient message: a failed restore is not something to
    // let scroll past, and a successful one is worth a positive acknowledgement.
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    backup?.takeIf { it != BackupOutcome.Working }?.let { outcome ->
        AlertDialog(
            onDismissRequest = viewModel::acknowledgeBackup,
            text = { Text(backupMessage(outcome)) },
            confirmButton = {
                TextButton(onClick = viewModel::acknowledgeBackup) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Projects ---
            item {
                SettingsGroup(stringResource(R.string.section_projects)) {
                    projects.forEachIndexed { index, project ->
                        if (index > 0) RowDivider()
                        ProjectRow(
                            project = project,
                            onClick = { onEditProject(project.id) },
                            // The last project may not be deleted: an empty carousel leaves the
                            // main screen with nothing to act on.
                            canDelete = projects.size > 1,
                            onDelete = { pendingDelete = project }
                        )
                    }
                    RowDivider()
                    TextButton(
                        onClick = onAddProject,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(
                            stringResource(R.string.action_add_project),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // --- General ---
            item {
                SettingsGroup(stringResource(R.string.section_general)) {
                    SwitchRow(stringResource(R.string.setting_sound), settings.soundEnabled, viewModel::setSound)
                    SwitchRow(stringResource(R.string.setting_vibrate), settings.vibrateEnabled, viewModel::setVibrate)
                    if (settings.vibrateEnabled) {
                        Text(
                            stringResource(R.string.setting_vibration_pattern),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                        SegmentedChoice(
                            options = listOf(
                                VibrationPattern.SHORT to stringResource(R.string.vibration_short),
                                VibrationPattern.MEDIUM to stringResource(R.string.vibration_medium),
                                VibrationPattern.LONG to stringResource(R.string.vibration_long),
                                VibrationPattern.CALL to stringResource(R.string.vibration_call)
                            ),
                            selected = settings.vibrationPattern,
                            onSelect = viewModel::setVibrationPattern,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Caption(stringResource(R.string.setting_vibration_pattern_summary))
                    }
                    SwitchRow(
                        stringResource(R.string.setting_always_on),
                        settings.alwaysOnDisplay,
                        viewModel::setAlwaysOn
                    )
                    RowDivider()
                    // Resolved here rather than inside valueText: that lambda is not composable, so
                    // reading resources through the context there is invisible to recomposition and
                    // would keep the old language after an in-app locale switch.
                    val offLabel = stringResource(R.string.value_off)
                    val minutesUnit = stringResource(R.string.minutes_unit)
                    Stepper(
                        label = stringResource(R.string.setting_idle_alert),
                        value = settings.idleAlertMinutes,
                        onValueChange = viewModel::setIdleAlertMinutes,
                        min = 0,
                        max = 120,
                        sliderStep = 5,
                        valueText = { v -> if (v == 0) offLabel else "$v $minutesUnit" }
                    )
                    Caption(stringResource(R.string.setting_idle_alert_summary))
                    // A clock reading, not a duration: 287 stepper positions replaced by the
                    // platform picker, which also settles 12- versus 24-hour on its own.
                    TimeOfDayField(
                        label = stringResource(R.string.setting_day_end),
                        hour = settings.dayEndHour,
                        minute = settings.dayEndMinute,
                        onTimeChange = viewModel::setDayEnd
                    )
                    Caption(stringResource(R.string.setting_day_end_summary))
                }
            }

            // --- Background reliability ---
            item {
                SettingsGroup(stringResource(R.string.section_background)) {
                    if (batteryExempt) {
                        Text(
                            stringResource(R.string.battery_ok),
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching { batteryLauncher.launch(BatteryOptimization.requestIntent(context)) }
                                        .onFailure { batteryLauncher.launch(BatteryOptimization.settingsListIntent()) }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(stringResource(R.string.battery_action))
                            Caption(stringResource(R.string.battery_summary))
                        }
                    }
                }
            }

            // --- Data ---
            item {
                SettingsGroup(stringResource(R.string.section_data)) {
                    ActionRow(
                        label = stringResource(R.string.action_export),
                        summary = stringResource(R.string.action_export_summary),
                        onClick = { exportLauncher.launch(suggestedFileName) }
                    )
                    RowDivider()
                    ActionRow(
                        label = stringResource(R.string.action_import),
                        summary = stringResource(R.string.action_import_summary),
                        // Any type: a JSON file arrives as application/json from one file manager
                        // and as text/plain or octet-stream from another, and a filter that guesses
                        // wrong greys out the very file the user came to pick.
                        onClick = { importLauncher.launch(arrayOf("*/*")) }
                    )
                }
            }

            // --- Appearance ---
            item {
                SettingsGroup(stringResource(R.string.section_appearance)) {
                    SegmentedChoice(
                        options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark)
                        ),
                        selected = settings.themeMode,
                        onSelect = viewModel::setThemeMode,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // --- Language ---
            item {
                SettingsGroup(stringResource(R.string.setting_language)) {
                    SegmentedChoice(
                        options = listOf(
                            AppLanguage.ENGLISH to "English",
                            AppLanguage.RUSSIAN to "Русский"
                        ),
                        selected = settings.language,
                        onSelect = { lang ->
                            if (settings.language != lang) {
                                viewModel.setLanguage(lang) { context.findActivity()?.recreate() }
                            }
                        },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** A tappable line with a title and an explanation under it. */
@Composable
private fun ActionRow(label: String, summary: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(label)
        Caption(summary)
    }
}

/**
 * One sentence per outcome, saying what happened and — when something went wrong — which of the two
 * possible culprits it was: the file, or the state the app is in.
 */
@Composable
private fun backupMessage(outcome: BackupOutcome): String = when (outcome) {
    is BackupOutcome.Working -> stringResource(R.string.backup_working)
    is BackupOutcome.Exported -> stringResource(R.string.backup_exported, outcome.projects)
    is BackupOutcome.Imported -> stringResource(R.string.backup_imported, outcome.projects)
    is BackupOutcome.RefusedTimerRunning -> stringResource(R.string.backup_error_timer)
    is BackupOutcome.FileUnavailable -> stringResource(R.string.backup_error_file)
    is BackupOutcome.Rejected -> stringResource(
        when (outcome.failure) {
            ProjectBackup.Failure.UNSUPPORTED_VERSION -> R.string.backup_error_version
            ProjectBackup.Failure.MALFORMED -> R.string.backup_error_malformed
            ProjectBackup.Failure.INVALID_VALUE -> R.string.backup_error_values
            ProjectBackup.Failure.NO_PROJECTS -> R.string.backup_error_empty
        }
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    onClick: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(project.pomodoroColor))
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(project.name, fontWeight = FontWeight.Medium)
            Text(
                "${project.focusMinutes}/${project.shortBreakMinutes} · ${project.pomodorosPerSession}🍅",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.cd_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
