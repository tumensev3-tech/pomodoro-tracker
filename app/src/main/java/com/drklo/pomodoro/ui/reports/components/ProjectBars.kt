package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.reports.ProjectValue
import kotlin.math.roundToInt

/** Horizontal bars of per-project value within a window (C1); tap a row to inspect it. */
@Composable
fun ProjectBars(
    values: List<ProjectValue>,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) {
        Text(
            stringResource(R.string.chart_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val max = values.maxOf { it.value }.coerceAtLeast(1f)
    val total = values.sumOf { it.value.toDouble() }.toFloat()
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant

    var selectedId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.chart_tap_hint_project),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            values.forEach { v ->
                val isSelected = v.projectId == selectedId
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedId = if (isSelected) null else v.projectId }
                            .background(if (isSelected) highlightColor else Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                v.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(valueFormatter(v.value), fontWeight = FontWeight.Medium)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(trackColor)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(v.value / max)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(v.colorArgb))
                            )
                        }
                    }
                    if (isSelected) {
                        val share = if (total > 0f) (v.value / total * 100f).roundToInt() else 0
                        ChartTooltip(
                            title = v.name,
                            focusSec = v.focusSec,
                            pomodoros = v.pomodoros,
                            hint = "",
                            note = stringResource(R.string.chart_share_of_total, share)
                        )
                    }
                }
            }
        }
    }
}
