package com.drklo.pomodoro.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.Preset
import com.drklo.pomodoro.data.model.ProjectIcon
import com.drklo.pomodoro.data.model.ProjectLimits
import com.drklo.pomodoro.ui.ViewModelFactories
import com.drklo.pomodoro.ui.common.ConfirmDeleteProjectDialog
import com.drklo.pomodoro.ui.common.Stepper
import com.drklo.pomodoro.ui.common.labelRes
import com.drklo.pomodoro.ui.common.symbol

private val ColorPalette = listOf(
    // Bright / saturated
    Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF5E35B1),
    Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF039BE5), Color(0xFF00ACC1),
    Color(0xFF00897B), Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFC0CA33),
    Color(0xFFFDD835), Color(0xFFFFB300), Color(0xFFFB8C00), Color(0xFFF4511E),
    // Muted / desaturated
    Color(0xFFB07A6E), Color(0xFF9C6E8E), Color(0xFF7E8AA2), Color(0xFF6E94A0),
    Color(0xFF6FA08A), Color(0xFF8DA06E), Color(0xFFB39B6E), Color(0xFFB58A8A),
    // Dark / dull / neutral
    Color(0xFF6D4C41), Color(0xFF546E7A), Color(0xFF607D8B), Color(0xFF455A64),
    Color(0xFF5D4037), Color(0xFF424242), Color(0xFF757575), Color(0xFF37474F)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditScreen(
    projectId: Long,
    onDone: () -> Unit,
    viewModel: ProjectEditViewModel = viewModel(factory = ViewModelFactories.projectEdit)
) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val project by viewModel.project.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val fallbackName = stringResource(R.string.default_project_name)

    // Edits live in the ViewModel until "Save", so walking out with the arrow — or the system back
    // gesture, which people use by reflex — used to drop them without a word.
    val leave = { if (viewModel.hasUnsavedChanges) confirmDiscard = true else onDone() }
    BackHandler(enabled = true) { leave() }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.dialog_discard_title)) },
            text = { Text(stringResource(R.string.dialog_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDone()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (confirmDelete) {
        ConfirmDeleteProjectDialog(
            projectName = project?.name.orEmpty(),
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDone)
            },
            onDismiss = { confirmDelete = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isNew) R.string.title_new_project else R.string.title_edit_project
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete))
                        }
                    }
                    TextButton(onClick = { viewModel.save(fallbackName, onDone) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { padding ->
        val p = project ?: return@Scaffold
        val minUnit = stringResource(R.string.minutes_unit)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = p.name,
                onValueChange = { name -> viewModel.edit { it.copy(name = name) } },
                label = { Text(stringResource(R.string.label_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FieldLabel(stringResource(R.string.label_project_icon))
            ProjectIconRow(selected = p.icon) { icon ->
                viewModel.edit { it.copy(icon = icon) }
            }
            Text(
                text = stringResource(p.icon.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FieldLabel(stringResource(R.string.label_presets))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Preset.entries.forEach { preset ->
                    AssistChip(
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text("${preset.focusMinutes}/${preset.shortBreakMinutes}") }
                    )
                }
            }

            Stepper(
                label = stringResource(R.string.label_focus),
                value = p.focusMinutes,
                onValueChange = { v -> viewModel.edit { it.copy(focusMinutes = v) } },
                min = ProjectLimits.focusMinutes.first,
                max = ProjectLimits.focusMinutes.last,
                sliderStep = 5,
                valueText = { "$it $minUnit" }
            )
            Stepper(
                label = stringResource(R.string.label_short_break),
                value = p.shortBreakMinutes,
                onValueChange = { v -> viewModel.edit { it.copy(shortBreakMinutes = v) } },
                min = ProjectLimits.shortBreakMinutes.first,
                max = ProjectLimits.shortBreakMinutes.last,
                sliderStep = 5,
                valueText = { "$it $minUnit" }
            )
            Stepper(
                label = stringResource(R.string.label_pomodoros),
                value = p.pomodorosPerSession,
                onValueChange = { v -> viewModel.edit { it.copy(pomodorosPerSession = v) } },
                min = ProjectLimits.pomodorosPerSession.first,
                max = ProjectLimits.pomodorosPerSession.last
            )
            Stepper(
                label = stringResource(R.string.label_daily_goal),
                value = p.dailyGoal,
                onValueChange = { v -> viewModel.edit { it.copy(dailyGoal = v) } },
                min = ProjectLimits.dailyGoal.first,
                max = ProjectLimits.dailyGoal.last
            )

            FieldLabel(stringResource(R.string.label_pomodoro_color))
            ColorRow(selected = p.pomodoroColor) { color ->
                viewModel.edit { it.copy(pomodoroColor = color.toArgb()) }
            }
            FieldLabel(stringResource(R.string.label_break_color))
            ColorRow(selected = p.breakColor) { color ->
                viewModel.edit { it.copy(breakColor = color.toArgb()) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_long_break), modifier = Modifier.weight(1f))
                Switch(
                    checked = p.longBreakEnabled,
                    onCheckedChange = { on -> viewModel.edit { it.copy(longBreakEnabled = on) } }
                )
            }
            if (p.longBreakEnabled) {
                Stepper(
                    label = stringResource(R.string.label_long_break_minutes),
                    value = p.longBreakMinutes,
                    onValueChange = { v -> viewModel.edit { it.copy(longBreakMinutes = v) } },
                    min = ProjectLimits.longBreakMinutes.first,
                    max = ProjectLimits.longBreakMinutes.last,
                    sliderStep = 5,
                    valueText = { "$it $minUnit" }
                )
                Stepper(
                    label = stringResource(R.string.label_long_break_interval),
                    value = p.longBreakInterval,
                    onValueChange = { v -> viewModel.edit { it.copy(longBreakInterval = v) } },
                    min = ProjectLimits.longBreakInterval.first,
                    max = ProjectLimits.longBreakInterval.last
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProjectIconRow(selected: ProjectIcon, onPick: (ProjectIcon) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ProjectIcon.entries.forEach { icon ->
            val selectedIcon = icon == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (selectedIcon) {
                            Modifier.border(
                                3.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onPick(icon) }
            ) {
                Text(
                    text = icon.symbol(),
                    fontSize = 24.sp
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(selected: Int, onPick: (Color) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ColorPalette.forEach { color ->
            val isSelected = color.toArgb() == selected
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onPick(color) }
            )
        }
    }
}
