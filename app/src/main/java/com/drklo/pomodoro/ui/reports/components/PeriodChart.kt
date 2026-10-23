package com.drklo.pomodoro.ui.reports.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.ui.reports.Aggregation
import com.drklo.pomodoro.ui.reports.bucketsFor
import com.drklo.pomodoro.ui.reports.buildStackedBars
import com.drklo.pomodoro.ui.reports.earliestLogDate
import com.drklo.pomodoro.ui.reports.elapsedDaysIn
import com.drklo.pomodoro.ui.reports.periodLabel
import com.drklo.pomodoro.ui.reports.sumInRange
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A stacked bar chart over time with its own Day/Week/Month aggregation and ‹ period › navigation.
 * Reused for focus-time (B3) and pomodoro-count (C3) by swapping [valueOf].
 */
@Composable
fun PeriodChart(
    title: String,
    logs: List<PomodoroLog>,
    projects: List<Project>,
    today: LocalDate,
    valueOf: (PomodoroLog) -> Float,
    summaryFormatter: (Float) -> String
) {
    val locale = LocalLocale.current.platformLocale
    var aggregation by remember { mutableStateOf(Aggregation.DAY) }
    var page by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }

    val buckets = remember(aggregation, page, today) { bucketsFor(aggregation, today, page, locale) }
    val bars = remember(logs, buckets, projects) {
        buildStackedBars(logs, buckets, projects, locale, valueOf)
    }

    // Compare the newest bucket currently on screen with the immediately preceding bucket.
    // The old implementation compared two whole chart pages (6 months vs the previous 6 months,
    // 8 weeks vs the previous 8 weeks, etc.), which made "Month" fail to report the expected
    // month-to-month change.
    val currentBucket = buckets.last()
    val previousStart = when (aggregation) {
        Aggregation.DAY -> currentBucket.start.minusDays(1)
        Aggregation.WEEK -> currentBucket.start.minusWeeks(1)
        Aggregation.MONTH -> currentBucket.start.minusMonths(1)
    }
    val previousEnd = when (aggregation) {
        Aggregation.DAY -> previousStart
        Aggregation.WEEK -> previousStart.plusDays(6)
        Aggregation.MONTH -> previousStart.plusMonths(1).minusDays(1)
    }
    val elapsed = elapsedDaysIn(currentBucket.start, currentBucket.endInclusive, today)
    val currentEnd = minOf(currentBucket.start.plusDays(elapsed - 1), currentBucket.endInclusive)
    val previousComparableEnd = minOf(previousStart.plusDays(elapsed - 1), previousEnd)
    val currentTotal = sumInRange(logs, currentBucket.start, currentEnd, valueOf)
    val prevTotal = sumInRange(logs, previousStart, previousComparableEnd, valueOf)

    // The average is over the buckets that actually finished; counting today's half-done one as a
    // full one drags the average down every morning.
    val finished = bars.filterIndexed { i, _ -> buckets[i].endInclusive.isBefore(today) }
    val averageBasis = finished.ifEmpty { bars }
    val average =
        if (averageBasis.isNotEmpty()) averageBasis.sumOf { it.total.toDouble() } / averageBasis.size else 0.0
    val maxTotal = bars.maxOfOrNull { it.total } ?: 0f
    val barsTotal = bars.sumOf { it.total.toDouble() }.toFloat()
    val earliest = remember(logs) { earliestLogDate(logs) }

    val avgMax = stringResource(
        R.string.chart_avg_max,
        summaryFormatter(average.toFloat()),
        summaryFormatter(maxTotal)
    )
    val compareLabel = stringResource(
        when (aggregation) {
            Aggregation.DAY -> R.string.prev_day
            Aggregation.WEEK -> R.string.prev_week
            Aggregation.MONTH -> R.string.prev_month
        }
    )
    val compare = comparisonLine(currentTotal, prevTotal, compareLabel, summaryFormatter)

    ChartCard(
        title = title,
        subtitle = if (compare.isEmpty()) avgMax else "$avgMax\n$compare"
    ) {
        AggregationBar(
            aggregation = aggregation,
            onAggregationChange = {
                aggregation = it
                page = 0
                selected = null
            },
            paging = Paging(
                page = page,
                canGoBack = earliest != null && buckets.first().start.isAfter(earliest),
                onChange = {
                    page = it.coerceAtLeast(0)
                    selected = null
                }
            ),
            periodLabel = periodLabel(buckets, locale)
        )
        StackedBarChart(
            columns = bars,
            selectedIndex = selected?.takeIf { it in bars.indices },
            onSelect = { selected = it }
        )
        val column = selected?.let { bars.getOrNull(it) }
        ChartTooltip(
            title = column?.rangeLabel,
            focusSec = column?.focusSec ?: 0,
            pomodoros = column?.pomodoros ?: 0,
            hint = stringResource(R.string.chart_tap_hint_bar),
            note = column?.let { col ->
                val share = if (barsTotal > 0f) (col.total / barsTotal * 100f).roundToInt() else 0
                stringResource(R.string.chart_share_of_total, share)
            },
            rows = column?.segments.orEmpty().map {
                TooltipRow(it.name, it.colorArgb, it.focusSec, it.pomodoros)
            }
        )
    }
}
