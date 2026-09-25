package com.drklo.pomodoro.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.timer.TimerState
import com.drklo.pomodoro.timer.formatMmSs
import com.drklo.pomodoro.ui.ViewModelFactories
import com.drklo.pomodoro.ui.main.components.Fanfare
import com.drklo.pomodoro.ui.main.components.PausedBookmark
import com.drklo.pomodoro.ui.main.components.SessionBullets
import com.drklo.pomodoro.ui.main.components.TimerDial
import com.drklo.pomodoro.ui.theme.DefaultPomodoroColor
import com.drklo.pomodoro.ui.theme.PomodoroTheme
import com.drklo.pomodoro.util.findActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Hide the system bars after this much inactivity on the main screen. */
private const val IDLE_HIDE_MS = 3_000L
private const val FREQUENT_PROJECT_LIMIT = 5
private const val MIN_STARTS_FOR_FREQUENT = 3
private val PHASE_EXTENSION_MINUTES = listOf(5, 10, 15)

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(factory = ViewModelFactories.main)
) {
    val state by viewModel.timerState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recentStartCounts by viewModel.recentStartCounts.collectAsStateWithLifecycle()
    val fanfareTrigger by viewModel.fanfareTrigger.collectAsStateWithLifecycle()

    // Roll session bullets onto the current logical day whenever the app comes to the foreground,
    // so yesterday's progress doesn't linger when the process survives past the day-end boundary.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppForegrounded() }

    // Always-on display (F-011): while the setting is on, the main screen does not sleep at all —
    // plain IDLE before the first start included. F-011 originally scoped this to "an active
    // timer", which left exactly one gap: sitting in front of the dial about to start, the screen
    // locks and the app looks broken. It is also a precondition for F-012 — the idle alert is a
    // colour change on an already-lit screen, so a screen that sleeps makes it unobservable.
    val keepOn = settings.alwaysOnDisplay
    val view = LocalView.current
    // Cleared on the way out too: leaving for Reports used to leave the flag stuck on the View,
    // so the screen kept burning even after the timer had stopped.
    DisposableEffect(view, keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    // Auto-hide system bars after inactivity so the Samsung nav buttons stop overlapping the
    // top-right icons (especially in landscape). Any touch reveals them; edge-swipe shows transiently.
    val window = view.context.findActivity()?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, view) }?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    var barsVisible by remember { mutableStateOf(true) }
    // Touches are reported through a channel rather than a counter in state. A counter has to be
    // read in composition to key the effect, so every pointer event — dozens a second during a drag
    // — recomposed the whole screen, pager pages included, and restarted the hide coroutine.
    val interactions = remember { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(interactions) {
        while (true) {
            interactions.receive()
            barsVisible = true
            // Every further touch restarts the countdown; only silence for the whole IDLE_HIDE_MS
            // hides the bars.
            while (withTimeoutOrNull(IDLE_HIDE_MS) { interactions.receive() } != null) {
                // keep waiting
            }
            barsVisible = false
        }
    }
    LaunchedEffect(barsVisible, insetsController) {
        val bars = WindowInsetsCompat.Type.systemBars()
        if (barsVisible) insetsController?.show(bars) else insetsController?.hide(bars)
    }
    DisposableEffect(insetsController) {
        onDispose { insetsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Deleting the last project is refused, so an empty list only happens in the sliver before the
    // first-run seeding lands. Even then the screen must stay usable: a blank frame with no controls
    // used to be a dead end, escapable only by restarting the app.
    if (projects.isEmpty()) {
        EmptyProjects(onOpenSettings = onOpenSettings)
        return
    }

    val isRunning = state.status == TimerStatus.RUNNING
    val canSwipe =
        (state.status == TimerStatus.IDLE || state.status == TimerStatus.PAUSED) &&
            !state.awaitingDecision

    // Infinite carousel: a huge virtual page count starting in the middle; the real project index
    // is page % size, so swiping past the ends wraps around instead of hitting a boundary.
    val infinite = projects.size > 1
    val pageCount = if (infinite) Int.MAX_VALUE else 1
    val startPage = remember(projects.size) {
        if (infinite) {
            (Int.MAX_VALUE / 2) / projects.size * projects.size + viewModel.indexOfActiveProject()
        } else {
            0
        }
    }
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount }

    // The pager keeps its page number when the project count changes, but the project on screen is
    // `page % size` — so deleting a project silently slides the carousel onto a different one.
    // Re-aim it at whatever the engine still considers active.
    LaunchedEffect(projects.size) {
        val target = if (infinite) {
            val base = pagerState.currentPage - (pagerState.currentPage % projects.size)
            base + viewModel.indexOfActiveProject()
        } else {
            0
        }
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
    }

    // React only to genuine user-driven page settles (drop the initial value so returning from
    // another screen does not reset a paused timer) — F-008, fix for settings round-trip.
    LaunchedEffect(pagerState, projects.size) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { page -> viewModel.onSelectProject(page % projects.size) }
    }

    // Paused session "parked" on its project; shown as a bookmark while browsing other projects.
    val pausedProject = if (state.status == TimerStatus.PAUSED) state.project else null
    val viewedProject = projects.getOrNull(pagerState.currentPage % projects.size)
    val showBookmark = pausedProject != null && viewedProject?.id != pausedProject.id
    var shakeTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showProjectPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        interactions.trySend(Unit)
                    }
                }
            }
    ) {
        // Measured here rather than read from LocalWindowInfo.containerSize. That value did not
        // change when the phone was turned: the activity handles the rotation itself
        // (android:configChanges), so nothing recreates it, and the screen went on drawing the
        // layout for the orientation it had started in — a landscape arrangement squeezed into a
        // portrait window and the other way round. Constraints cannot go stale that way: they come
        // from the measurement that has just happened.
        val isLandscape = maxWidth > maxHeight

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canSwipe,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val project = projects[page % projects.size]
            val isActive = project.id == state.project?.id
            ProjectPage(
                project = project,
                state = state.takeIf { isActive },
                isLandscape = isLandscape,
                holdFinishedColor = settings.holdFinishedPhaseColor,
                actions = PageActions(
                    // Tapping a different project while one is paused is blocked; nudge the bookmark.
                    onTap = {
                        if (isActive) {
                            viewModel.onPlayPause()
                        } else if (pausedProject != null) shakeTrigger++
                    },
                    onReset = { if (isActive) viewModel.onReset() },
                    onSeek = { if (isActive) viewModel.onSeek(it) },
                    onChangePhase = { if (isActive) viewModel.onChangePhase() },
                    onChooseProject = {
                        if (!isRunning && !state.awaitingDecision) showProjectPicker = true
                    }
                )
            )
        }

        // Bookmark for the paused project (bottom-right, sticks out from the edge).
        // `showBookmark` already implies a non-null paused project, hence no second check.
        if (showBookmark) {
            PausedBookmark(
                color = Color(pausedProject.pomodoroColor),
                timeText = formatMmSs(state.remainingSeconds),
                shakeTrigger = shakeTrigger,
                onClick = {
                    val pausedIndex = projects.indexOfFirst { it.id == pausedProject.id }
                    if (pausedIndex >= 0) {
                        val base = pagerState.currentPage - (pagerState.currentPage % projects.size)
                        scope.launch { pagerState.animateScrollToPage(base + pausedIndex) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp)
            )
        }

        // Reports + burger — hidden during the active session for minimalism (F-019).
        if (!isRunning) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // Every system bar, not just the status bar. The window is drawn edge to edge,
                    // and in landscape the navigation bar sits on the right — exactly where these
                    // icons are. With only statusBarsPadding they went under it: the reports icon
                    // was reduced to a sliver and the settings one was entirely off-screen, so
                    // there was no way into settings while the phone was sideways. The auto-hide
                    // below used to mask it after three seconds; a control has to be reachable
                    // before that too.
                    .safeDrawingPadding()
                    .padding(4.dp)
            ) {
                IconButton(onClick = onOpenReports) {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = stringResource(R.string.cd_reports),
                        tint = Color.White
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = Color.White
                    )
                }
            }
        }

        Fanfare(
            trigger = fanfareTrigger,
            onShown = viewModel::onFanfareShown,
            modifier = Modifier.fillMaxSize()
        )

        if (showProjectPicker && !isRunning && !state.awaitingDecision) {
            ProjectPickerDialog(
                projects = projects,
                recentStartCounts = recentStartCounts,
                selectedProjectId = viewedProject?.id,
                onDismiss = { showProjectPicker = false },
                onSelect = { index ->
                    showProjectPicker = false
                    val base = pagerState.currentPage -
                        (pagerState.currentPage % projects.size)
                    scope.launch { pagerState.scrollToPage(base + index) }
                }
            )
        }

        if (state.awaitingDecision) {
            PhaseEndDecisionDialog(
                state = state,
                onContinue = viewModel::onAcceptPhaseEnd,
                onExtend = viewModel::onExtendPhase
            )
        }
    }
}

@Composable
private fun PhaseEndDecisionDialog(
    state: TimerState,
    onContinue: () -> Unit,
    onExtend: (Int) -> Unit
) {
    val finishedWork = state.phase == Phase.POMODORO
    val title = stringResource(
        if (finishedWork) R.string.phase_end_work_title else R.string.phase_end_break_title
    )
    val continueLabel = stringResource(
        if (finishedWork) R.string.phase_end_start_break else R.string.phase_end_return_to_work
    )
    val activityName = state.project?.name.orEmpty()
    val phaseEmoji = if (finishedWork) "💼" else "☕"

    AlertDialog(
        onDismissRequest = { /* A phase-end decision is required. */ },
        icon = {
            Text(
                text = phaseEmoji,
                fontSize = 56.sp
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (activityName.isNotBlank()) {
                    Text(
                        text = activityName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.phase_end_question),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PHASE_EXTENSION_MINUTES.forEach { minutes ->
                        TextButton(onClick = { onExtend(minutes) }) {
                            Text("+$minutes " + stringResource(R.string.minutes_unit))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(continueLabel)
            }
        }
    )
}

/** What a carousel page can do; they always travel together, so they travel as one. */
/** Internal, not private: the landscape layout test lays a page out directly. */
internal data class PageActions(
    val onTap: () -> Unit,
    val onReset: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onChangePhase: () -> Unit,
    val onChooseProject: () -> Unit
)

/** Placeholder shown while there is no project to draw — never a blank, uncontrollable screen. */
@Composable
private fun EmptyProjects(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = stringResource(R.string.empty_no_projects),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center)
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Same reasoning as the main screen's icons: in landscape the navigation bar is on
                // the right. This is the only control on the placeholder, so losing it under the
                // bar would leave the screen with no way out at all.
                .safeDrawingPadding()
                .padding(4.dp)
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = stringResource(R.string.cd_settings),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ProjectPickerDialog(
    projects: List<Project>,
    recentStartCounts: Map<Long, Int>,
    selectedProjectId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val totalRecentStarts = recentStartCounts.values.sum()
    val frequentProjects = if (totalRecentStarts >= MIN_STARTS_FOR_FREQUENT) {
        projects
            .mapIndexed { index, project ->
                Triple(index, project, recentStartCounts[project.id] ?: 0)
            }
            .filter { it.third > 0 }
            .sortedWith(
                compareByDescending<Triple<Int, Project, Int>> { it.third }
                    .thenBy { it.first }
            )
            .take(FREQUENT_PROJECT_LIMIT)
    } else {
        emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_choose_project)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                if (frequentProjects.isNotEmpty()) {
                    item(key = "frequent_header") {
                        Text(
                            text = stringResource(R.string.title_frequent_projects),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                    itemsIndexed(
                        items = frequentProjects,
                        key = { _, item -> "frequent-${item.second.id}" }
                    ) { _, item ->
                        ProjectPickerRow(
                            project = item.second,
                            selected = item.second.id == selectedProjectId,
                            onClick = { onSelect(item.first) }
                        )
                    }
                    item(key = "all_header") {
                        Text(
                            text = stringResource(R.string.title_all_projects),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                }

                itemsIndexed(
                    items = projects,
                    key = { _, project -> "all-${project.id}" }
                ) { index, project ->
                    ProjectPickerRow(
                        project = project,
                        selected = project.id == selectedProjectId,
                        onClick = { onSelect(index) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ProjectPickerRow(
    project: Project,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = if (selected) "✓  ${project.name}" else "    ${project.name}",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp)
    )
}

@Composable
internal fun ProjectPage(
    project: Project,
    /**
     * Live timer state, or null for a page that is merely being browsed. Passing the whole state to
     * every page meant all of them recomposed once a second, redrawing canvases for projects that
     * only ever show a static preview.
     */
    state: TimerState?,
    isLandscape: Boolean,
    holdFinishedColor: Boolean,
    actions: PageActions
) {
    val onTap = actions.onTap
    val onReset = actions.onReset
    val onSeek = actions.onSeek
    val onChangePhase = actions.onChangePhase
    val onChooseProject = actions.onChooseProject
    // The page reflects live state only for the active project; others show an idle preview.
    val isActive = state != null
    val phase = state?.phase ?: Phase.POMODORO
    val status = state?.status ?: TimerStatus.IDLE
    val fraction =
        if (state != null && state.totalSeconds > 0) {
            state.remainingSeconds.toFloat() / state.totalSeconds
        } else {
            1f
        }
    val filledBullets = state?.completedInSession ?: 0

    // Phase color is the BACKGROUND; foreground stays white (#3).
    // While awaiting the next phase (a phase finished but isn't auto-started) keep showing the COLOR
    // of the JUST-FINISHED phase, so peripheral vision doesn't read the screen as a new phase already
    // running. state.phase is already the NEXT phase here, so the finished color is the opposite one.
    // The idle-alert strobe (#6) alternates against that base; idleAlertActive is only ever true while
    // stalled (paused or awaiting), so this is safe.
    val phaseColor = Color(project.colorFor(phase))
    val oppositeColor =
        Color(if (phase == Phase.POMODORO) project.breakColor else project.pomodoroColor)
    val awaiting = holdFinishedColor && state?.awaitingNext == true && status == TimerStatus.IDLE
    val flip = state?.idleAlertActive == true
    val bgColor: Color = when {
        awaiting -> if (flip) phaseColor else oppositeColor // hold finished color; strobe to upcoming
        flip -> oppositeColor
        else -> phaseColor
    }.takeIf { it.alpha > 0f } ?: DefaultPomodoroColor
    val fg = Color.White

    val phaseName = stringResource(
        when (phase) {
            Phase.POMODORO -> R.string.phase_pomodoro
            Phase.SHORT_BREAK -> R.string.phase_short_break
            Phase.LONG_BREAK -> R.string.phase_long_break
        }
    )
    val minutes = project.durationSecondsFor(phase) / 60
    val timeText = if (state != null && status != TimerStatus.IDLE) {
        formatMmSs(state.remainingSeconds)
    } else {
        "$minutes ${stringResource(R.string.minutes_short)}"
    }
    val canChangePhase = isActive && status != TimerStatus.RUNNING
    val showReset = isActive && status == TimerStatus.PAUSED

    // Accessibility for the dial (F-R0-01). It is the app's primary control — tap to start or
    // pause — but it is built from raw pointer input over a Canvas, so without this a screen reader
    // finds an unlabelled box and the main function of the app is unreachable. The description is
    // assembled here rather than inside TimerDial because this is where the phase and the remaining
    // time are known.
    val dialLabel = stringResource(R.string.cd_play_pause)
    val totalText = "$minutes ${stringResource(R.string.minutes_unit)}"
    val dialState = when (status) {
        TimerStatus.RUNNING -> stringResource(R.string.cd_dial_running, timeText, totalText)
        TimerStatus.PAUSED -> stringResource(R.string.cd_dial_paused, timeText, totalText)
        TimerStatus.IDLE -> stringResource(R.string.cd_dial_idle, phaseName, totalText)
    }
    val tapActionLabel = when (status) {
        TimerStatus.RUNNING -> stringResource(R.string.cd_action_pause)
        TimerStatus.PAUSED -> stringResource(R.string.cd_action_resume)
        TimerStatus.IDLE -> stringResource(R.string.cd_action_start)
    }
    val changePhaseLabel = stringResource(R.string.cd_action_change_phase)

    val dial = @Composable { dialModifier: Modifier ->
        TimerDial(
            fraction = fraction,
            color = fg,
            status = status,
            showHand = status == TimerStatus.RUNNING,
            onTap = onTap,
            onSeek = onSeek,
            tapLabel = tapActionLabel,
            // Role and the click action come from the dial's own `clickable`; what only this scope
            // knows is what the timer is currently doing, so that is what it contributes.
            modifier = dialModifier.semantics(mergeDescendants = true) {
                contentDescription = dialLabel
                stateDescription = dialState
            }
        )
    }

    val details = @Composable { compact: Boolean ->
        ProjectDetails(
            project = project,
            filledBullets = filledBullets,
            phaseName = phaseName,
            timeText = timeText,
            color = fg,
            compact = compact,
            onChangePhase = onChangePhase.takeIf { canChangePhase },
            changePhaseLabel = changePhaseLabel,
            onChooseProject = onChooseProject.takeIf { status != TimerStatus.RUNNING }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // The dial gets a half of the row to sit in, and picks a square inside it. It used
                // to take `weight(1f).fillMaxHeight()` directly: that fixes width *and* height, and
                // `aspectRatio` cannot choose a side when both are already decided — it passes the
                // constraints through and the circle comes out as an ellipse. Wide-and-short
                // landscapes stretched it badly (reported on a Galaxy S23). Inside a Box the
                // constraints are loose, so aspectRatio settles on min(width, height).
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    dial(Modifier.aspectRatio(1f))
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { details(true) }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                dial(Modifier.fillMaxWidth().widthIn(max = 360.dp).padding(8.dp))
                Spacer(Modifier.height(32.dp))
                details(false)
            }
        }

        // Small reset label at the bottom, visible only while paused (F-020, reference UX).
        if (showReset) {
            Text(
                text = stringResource(R.string.action_reset),
                color = fg.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clickable { onReset() }
            )
        }
    }
}

/**
 * The text beside (landscape) or below (portrait) the dial.
 *
 * `compact` is landscape: the height there is a fraction of the portrait one, and at the portrait
 * type sizes the phase and the clock fell off the bottom of a Galaxy S23. Shrinking rather than
 * scrolling, because the point of this screen is a time readable at a glance, and a time you have
 * to scroll to is not one.
 */
@Composable
private fun ProjectDetails(
    project: Project,
    filledBullets: Int,
    phaseName: String,
    timeText: String,
    color: Color,
    compact: Boolean,
    /** Null when the phase cannot be changed right now — while running (F-021). */
    onChangePhase: (() -> Unit)?,
    changePhaseLabel: String,
    onChooseProject: (() -> Unit)?
) {
    // A row of circles says nothing out loud. Merged into one node so a screen reader announces the
    // progress once instead of walking eight unlabelled dots.
    val bulletsDescription = stringResource(
        R.string.cd_session_progress,
        filledBullets,
        project.pomodorosPerSession
    )
    SessionBullets(
        filled = filledBullets,
        total = project.pomodorosPerSession,
        color = color,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = bulletsDescription
        }
    )
    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
    Text(
        text = if (onChooseProject != null) "${project.name} ▾" else project.name,
        fontSize = if (compact) 18.sp else 22.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = if (onChooseProject != null) {
            Modifier.clickable(onClickLabel = stringResource(R.string.cd_choose_project)) {
                onChooseProject()
            }
        } else {
            Modifier
        }
    )
    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
    // Phase type — always visible so the user knows pomodoro vs break (#7); tap to change (F-021).
    Text(
        text = phaseName,
        fontSize = if (compact) 15.sp else 18.sp,
        fontWeight = FontWeight.Medium,
        color = color.copy(alpha = 0.9f),
        // The label reads as "Pomodoro"; onClickLabel is what tells a screen reader that activating
        // it switches phase, which is not guessable from the text.
        modifier = if (onChangePhase != null) {
            Modifier.clickable(onClickLabel = changePhaseLabel) { onChangePhase() }
        } else {
            Modifier
        }
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = timeText,
        fontSize = if (compact) 30.sp else 40.sp,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

/**
 * Landscape at a Galaxy S23's window size. Landscape is the orientation nobody remembers to open,
 * which is how a stretched dial and a clipped clock reached a published release; a preview puts it
 * in front of whoever edits this file. ProjectPageLandscapeTest is what actually enforces it.
 */
@Preview(name = "Landscape (S23)", widthDp = 892, heightDp = 412)
@Composable
private fun ProjectPageLandscapePreview() {
    PomodoroTheme {
        ProjectPage(
            project = Project(
                id = 1,
                name = "Work",
                focusMinutes = 25,
                shortBreakMinutes = 5,
                pomodorosPerSession = 4,
                pomodoroColor = 0xFFB74B4B.toInt(),
                breakColor = 0xFF4B7BB7.toInt(),
                dailyGoal = 8,
                longBreakEnabled = true,
                longBreakMinutes = 15,
                longBreakInterval = 4,
                orderIndex = 0
            ),
            state = null,
            isLandscape = true,
            holdFinishedColor = false,
            actions = PageActions(
                onTap = {},
                onReset = {},
                onSeek = {},
                onChangePhase = {},
                onChooseProject = {}
            )
        )
    }
}
