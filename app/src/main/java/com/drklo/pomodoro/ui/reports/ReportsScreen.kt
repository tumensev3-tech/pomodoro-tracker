package com.drklo.pomodoro.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.ViewModelFactories
import com.drklo.pomodoro.ui.reports.components.ChartCard
import com.drklo.pomodoro.ui.reports.components.DonutChart
import com.drklo.pomodoro.ui.reports.components.MonthCalendar
import com.drklo.pomodoro.ui.reports.components.PeriodChart
import com.drklo.pomodoro.ui.reports.components.ProjectBars
import com.drklo.pomodoro.ui.reports.components.WeekJournal
import com.drklo.pomodoro.ui.reports.components.WindowChart
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = viewModel(factory = ViewModelFactories.reports)
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val activityTotals by viewModel.activityTotals.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_reports)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // PrimaryTabRow, not the deprecated TabRow: these are the screen's top-level tabs.
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.tab_tomatoes)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.tab_projects)) }
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text(stringResource(R.string.tab_actual_time)) }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                val hLabel = stringResource(R.string.unit_hours)
                val mLabel = stringResource(R.string.unit_minutes)
                when (tab) {
                    0 -> {
                        SummaryRow(
                            stringResource(R.string.stat_focus_time),
                            stat(
                                stringResource(R.string.stat_total),
                                formatFocus(summary.totalFocusSec, hLabel, mLabel)
                            ),
                            stat(
                                stringResource(R.string.stat_today),
                                formatFocus(summary.todayFocusSec, hLabel, mLabel)
                            ),
                            stat(stringResource(R.string.stat_week), formatFocus(summary.weekFocusSec, hLabel, mLabel))
                        )
                        ChartCard(
                            title = stringResource(R.string.chart_week_journal),
                            subtitle = stringResource(
                                R.string.chart_week_journal_sub,
                                formatFocus(summary.weekFocusSec, hLabel, mLabel)
                            )
                        ) {
                            WeekJournal(logs = logs, today = today, projects = projects)
                        }

                        ChartCard(title = stringResource(R.string.chart_goals_calendar)) {
                            MonthCalendar(logs = logs, projects = projects, today = today)
                        }

                        PeriodChart(
                            title = stringResource(R.string.chart_focus_over_time),
                            logs = logs,
                            projects = projects,
                            today = today,
                            valueOf = { it.durationSeconds.toFloat() },
                            summaryFormatter = { formatFocus(it.toInt(), hLabel, mLabel) }
                        )
                    }
                    1 -> {
                        SummaryRow(
                            stringResource(R.string.stat_pomodoros),
                            stat(stringResource(R.string.stat_total), summary.totalPomodoros.toString()),
                            stat(stringResource(R.string.stat_today), summary.todayPomodoros.toString()),
                            stat(stringResource(R.string.stat_week), summary.weekPomodoros.toString())
                        )
                        WindowChart(
                            title = stringResource(R.string.chart_time_by_project),
                            logs = logs,
                            projects = projects,
                            today = today,
                            valueOf = { it.durationSeconds.toFloat() },
                            formatValue = { formatFocus(it.toInt(), hLabel, mLabel) },
                            subtitle = { vals ->
                                stringResource(
                                    R.string.chart_total,
                                    formatFocus(vals.sumOf { it.value.toDouble() }.toInt(), hLabel, mLabel)
                                )
                            }
                        ) { vals ->
                            ProjectBars(
                                values = vals,
                                valueFormatter = { formatFocus(it.toInt(), hLabel, mLabel) }
                            )
                        }

                        WindowChart(
                            title = stringResource(R.string.chart_time_distribution),
                            logs = logs,
                            projects = projects,
                            today = today,
                            valueOf = { it.durationSeconds.toFloat() },
                            formatValue = { formatFocus(it.toInt(), hLabel, mLabel) },
                            subtitle = { vals ->
                                stringResource(
                                    R.string.chart_total,
                                    formatFocus(vals.sumOf { it.value.toDouble() }.toInt(), hLabel, mLabel)
                                )
                            }
                        ) { vals ->
                            DonutChart(
                                values = vals,
                                centerLabel = formatFocus(vals.sumOf { it.value.toDouble() }.toInt(), hLabel, mLabel)
                            )
                        }

                        PeriodChart(
                            title = stringResource(R.string.chart_pomodoros_over_time),
                            logs = logs,
                            projects = projects,
                            today = today,
                            valueOf = { 1f },
                            summaryFormatter = { it.roundToInt().toString() }
                        )
                    }
                    else -> {
                        val total = activityTotals.sumOf { it.totalSeconds }
                        val todaySeconds = activityTotals.sumOf { it.todaySeconds }
                        val weekSeconds = activityTotals.sumOf { it.weekSeconds }
                        SummaryRow(
                            stringResource(R.string.stat_actual_time),
                            stat(stringResource(R.string.stat_total), formatFocus(total, hLabel, mLabel)),
                            stat(stringResource(R.string.stat_today), formatFocus(todaySeconds, hLabel, mLabel)),
                            stat(stringResource(R.string.stat_week), formatFocus(weekSeconds, hLabel, mLabel))
                        )
                        ActivityTimeCard(
                            totals = activityTotals,
                            hLabel = hLabel,
                            mLabel = mLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTimeCard(
    totals: List<ActivityTimeTotal>,
    hLabel: String,
    mLabel: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.chart_actual_activities),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (totals.isEmpty()) {
                Text(
                    text = stringResource(R.string.actual_time_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                totals.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatFocus(item.weekSeconds, hLabel, mLabel))
                            Text(
                                text = stringResource(
                                    R.string.actual_time_today,
                                    formatFocus(item.todaySeconds, hLabel, mLabel)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Stat(val label: String, val value: String)

private fun stat(label: String, value: String) = Stat(label, value)

@Composable
private fun SummaryRow(title: String, vararg stats: Stat) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                stats.forEachIndexed { i, s ->
                    if (i > 0) {
                        VerticalDivider(modifier = Modifier.height(36.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            s.value,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            s.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
