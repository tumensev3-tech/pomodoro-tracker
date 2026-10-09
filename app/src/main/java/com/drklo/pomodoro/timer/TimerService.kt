package com.drklo.pomodoro.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.drklo.pomodoro.MainActivity
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and shows an ongoing notification while the timer runs or is paused
 * (PRD: reliable background timer via Foreground Service).
 *
 * **Who owns the lifecycle.** One rule, in one direction: the service is *started* when the user
 * starts a phase from the UI, and it *stops itself* the moment the timer reports IDLE. Nothing else
 * calls [stopService] — an owner split between the ViewModel and the service is how a phase ends up
 * running with no notification behind it. Autostarted phases need no start of their own: the engine
 * hands over from one phase to the next without ever publishing an IDLE frame in between, so the
 * service that was already running simply keeps going.
 */
@Suppress("TooManyFunctions")
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var eventJob: Job? = null

    /** Last posted appearance; see [notificationSignature]. */
    private var lastSignature: String? = null

    /**
     * The Fit2 companion is intentionally separate from the foreground notification, but unlike the
     * phone notification it must not be refreshed for progress updates: every refresh becomes the
     * newest item on the band and can mask unrelated incoming notifications.
     */
    private var lastWearableSignature: String? = null

    private val engine: TimerEngine
        get() = (application as PomodoroApp).container.timerEngine

    /**
     * Strings for the notification. The service's own resources follow the *system* language, so
     * without this the one element visible on a locked screen would ignore the language chosen in
     * the app (F-023) — a Russian phone with the app set to English kept announcing «Помодоро».
     */
    private lateinit var localized: Context

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val tag = (application as PomodoroApp).languageTag
        localized = LocaleHelper.wrap(this, tag)
        createChannel()
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WEARABLE_REPLY -> handleWearableReply(intent)
            ACTION_TOGGLE -> engine.togglePlayPause()
            ACTION_RESET -> engine.reset()
            ACTION_STOP_ACTIVITY -> {
                engine.stopCurrentActivity()
                notificationManager().cancel(PHASE_END_NOTIFICATION_ID)
            }
            ACTION_ACCEPT_PHASE_END -> {
                engine.acceptPhaseEnd()
                notificationManager().cancel(PHASE_END_NOTIFICATION_ID)
            }
            ACTION_EXTEND_5 -> {
                engine.extendCurrentPhase(EXTEND_5_MINUTES)
                notificationManager().cancel(PHASE_END_NOTIFICATION_ID)
            }
            ACTION_EXTEND_10 -> {
                engine.extendCurrentPhase(EXTEND_10_MINUTES)
                notificationManager().cancel(PHASE_END_NOTIFICATION_ID)
            }
            ACTION_EXTEND_15 -> {
                engine.extendCurrentPhase(EXTEND_15_MINUTES)
                notificationManager().cancel(PHASE_END_NOTIFICATION_ID)
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (observeJob == null) {
            observeJob = scope.launch {
                // The delay after each post is the rate limit: a StateFlow conflates by nature, so
                // whatever the timer does while we wait collapses into a single latest value.
                // Scrubbing the dial emits on every touch move, and one notify() per move floods
                // NotificationManager until Android starts dropping updates ("has posted too many
                // notifications") — paying IPC and a shade redraw for each one on the way there.
                engine.state.collect { state ->
                    if (state.status == TimerStatus.IDLE && !state.awaitingDecision) {
                        notificationManager().cancel(PHASE_END_NOTIFICATION_ID)
                        notificationManager().cancel(WEARABLE_TIMER_NOTIFICATION_ID)
                        stopSelf()
                        return@collect
                    }
                    if (!state.awaitingDecision) {
                        notificationManager().cancel(PHASE_END_NOTIFICATION_ID)
                    } else {
                        // The phase-end alert replaces the running timer on the band while the
                        // user is choosing what to do next.
                        notificationManager().cancel(WEARABLE_TIMER_NOTIFICATION_ID)
                    }
                    // The countdown itself is drawn by the system chronometer, so a re-post is only
                    // worth it when something else moved: the phase, the run state, or the progress
                    // bar's next step. Re-posting every second is what made the shade flicker.
                    val signature = state.notificationSignature()
                    if (signature != lastSignature) {
                        lastSignature = signature
                        notificationManager().notify(NOTIFICATION_ID, buildNotification())
                    }

                    if (!state.awaitingDecision) {
                        val wearableSignature = state.wearableNotificationSignature()
                        if (wearableSignature != lastWearableSignature) {
                            lastWearableSignature = wearableSignature
                            // Galaxy Fit2 does not reliably mirror the foreground-service
                            // notification. Send a short-lived companion only when the timer's
                            // meaningful state changes (start, pause/resume, phase/project change,
                            // extension), never for ordinary progress ticks. This lets the band show
                            // the slot end time without permanently becoming the "latest"
                            // notification and hiding messages from other apps.
                            notificationManager().notify(
                                WEARABLE_TIMER_NOTIFICATION_ID,
                                buildWearableTimerNotification(state)
                            )
                        }
                    }
                    delay(MIN_NOTIFICATION_INTERVAL_MS)
                }
            }
        }
        if (eventJob == null) {
            eventJob = scope.launch {
                engine.events.collect { event ->
                    if (event is TimerEvent.PhaseFinished) {
                        notificationManager().cancel(WEARABLE_TIMER_NOTIFICATION_ID)
                        notificationManager().notify(
                            PHASE_END_NOTIFICATION_ID,
                            buildPhaseEndNotification(event.phase)
                        )
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        eventJob?.cancel()
        notificationManager().cancel(WEARABLE_TIMER_NOTIFICATION_ID)
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("LongMethod")
    private fun buildNotification(): Notification {
        val state = engine.state.value
        val phaseText = when (state.phase) {
            Phase.POMODORO -> localized.getString(R.string.phase_pomodoro)
            Phase.SHORT_BREAK -> localized.getString(R.string.phase_short_break)
            Phase.LONG_BREAK -> localized.getString(R.string.phase_long_break)
        }
        val title = state.project?.name?.let { "$it · $phaseText" } ?: phaseText
        val elapsed = (state.totalSeconds - state.remainingSeconds).coerceAtLeast(0)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // How far through the phase, without having to read the digits.
            .setProgress(state.totalSeconds.coerceAtLeast(1), elapsed, false)
            // Public: the timer is the reason the phone is on the desk, so it belongs on the lock
            // screen. There is nothing private in "Работа · Помодоро".
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state.status == TimerStatus.RUNNING) {
            // Hand the second-by-second countdown to the system: it ticks the chronometer itself,
            // so the app does not have to re-post the notification once a second to keep it honest.
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + state.remainingSeconds * MILLIS_PER_SECOND)
                .setShowWhen(true)
        } else {
            // Parked: a chronometer would keep counting, so the frozen time is plain text.
            builder.setUsesChronometer(false)
                .setShowWhen(false)
                .setContentText(formatMmSs(state.remainingSeconds))
        }

        // Pause/Resume + Reset controls (F-101). While waiting for a phase-end decision, the
        // separate high-priority alert owns the controls.
        if (!state.awaitingDecision) {
            if (state.status == TimerStatus.RUNNING) {
                builder.addAction(
                    R.drawable.ic_notif_pause,
                    localized.getString(R.string.notif_pause),
                    actionPendingIntent(ACTION_TOGGLE)
                )
            } else {
                builder.addAction(
                    R.drawable.ic_notif_play,
                    localized.getString(R.string.notif_resume),
                    actionPendingIntent(ACTION_TOGGLE)
                )
            }
            builder.addAction(
                R.drawable.ic_notif_reset,
                localized.getString(R.string.notif_reset),
                actionPendingIntent(ACTION_RESET)
            )
            builder.addAction(
                R.drawable.ic_notif_reset,
                localized.getString(R.string.action_stop_activity),
                actionPendingIntent(ACTION_STOP_ACTIVITY)
            )
        }

        return builder.build()
    }

    /**
     * Mirror-friendly companion for Galaxy Fit2.
     *
     * The foreground notification above is intentionally ongoing because it owns the service, but
     * Samsung's notification bridge commonly leaves ongoing foreground-service notifications on
     * the phone. This ordinary notification carries Android's native countdown fields instead.
     * If Fit2 renders those fields, the digits tick on the band without per-second app updates.
     */
    private fun buildWearableTimerNotification(state: TimerState): Notification {
        val phaseText = when (state.phase) {
            Phase.POMODORO -> localized.getString(R.string.phase_pomodoro)
            Phase.SHORT_BREAK -> localized.getString(R.string.phase_short_break)
            Phase.LONG_BREAK -> localized.getString(R.string.phase_long_break)
        }
        val title = state.project?.name?.let { "$it · $phaseText" } ?: phaseText
        val openIntent = PendingIntent.getActivity(
            this,
            WEARABLE_TIMER_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, WEARABLE_TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setLocalOnly(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // Give Samsung's bridge enough time to copy it to Fit2, then remove the duplicate from
            // the phone and the band so normal incoming notifications can take over.
            .setTimeoutAfter(WEARABLE_TIMER_TIMEOUT_MS)

        if (state.status == TimerStatus.RUNNING) {
            // Fit2 renders the future 'when' value as the slot finish time. A live Android
            // chronometer is unnecessary here and tends to keep the companion looking active.
            builder.setUsesChronometer(false)
                .setWhen(System.currentTimeMillis() + state.remainingSeconds * MILLIS_PER_SECOND)
                .setShowWhen(true)
        } else {
            builder.setUsesChronometer(false)
                .setShowWhen(false)
                .setContentText(formatMmSs(state.remainingSeconds))
        }

        return builder.build()
    }

    @Suppress("LongMethod")
    private fun buildPhaseEndNotification(finishedPhase: Phase): Notification {
        val state = engine.state.value
        val finishedWork = finishedPhase == Phase.POMODORO
        val title = localized.getString(
            if (finishedWork) R.string.phase_end_work_title else R.string.phase_end_break_title
        )
        val continueLabel = localized.getString(
            if (finishedWork) R.string.phase_end_start_break else R.string.phase_end_return_to_work
        )
        val projectName = state.project?.name.orEmpty()

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = wearableReplyAction(finishedWork)
        val sender = Person.Builder()
            .setName(localized.getString(R.string.app_name))
            .build()
        val messageText = listOf(title, projectName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        return NotificationCompat.Builder(this, PHASE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentText(projectName)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Fit2 exposes Quick responses for message-style notifications. Keeping the visual
            // content identical while declaring a reply-capable message gives Samsung's bridge a
            // chance to surface those replies on the band.
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(
                NotificationCompat.MessagingStyle(sender)
                    .addMessage(messageText, System.currentTimeMillis(), sender)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Finished-phase alerts must be bridgeable to Galaxy Fit2. Android does not bridge
            // ongoing notifications to paired wearables, so keep this as a normal alert.
            .setOngoing(false)
            .setLocalOnly(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(replyAction)
            .addAction(
                R.drawable.ic_notif_play,
                continueLabel,
                actionPendingIntent(ACTION_ACCEPT_PHASE_END)
            )
            .addAction(
                R.drawable.ic_notif_reset,
                localized.getString(R.string.action_stop_activity),
                actionPendingIntent(ACTION_STOP_ACTIVITY)
            )
            .addAction(
                R.drawable.ic_notif_play,
                localized.getString(R.string.notif_extend_5),
                actionPendingIntent(ACTION_EXTEND_5)
            )
            .addAction(
                R.drawable.ic_notif_play,
                localized.getString(R.string.notif_extend_10),
                actionPendingIntent(ACTION_EXTEND_10)
            )
            .addAction(
                R.drawable.ic_notif_play,
                localized.getString(R.string.notif_extend_15),
                actionPendingIntent(ACTION_EXTEND_15)
            )
            .build()
    }

    private fun wearableReplyAction(finishedWork: Boolean): NotificationCompat.Action {
        val choices = arrayOf(
            localized.getString(
                if (finishedWork) R.string.wearable_reply_break else R.string.wearable_reply_work
            ),
            localized.getString(R.string.notif_extend_5),
            localized.getString(R.string.notif_extend_10),
            localized.getString(R.string.notif_extend_15),
            localized.getString(R.string.action_stop_activity)
        )
        val remoteInput = RemoteInput.Builder(KEY_WEARABLE_REPLY)
            .setLabel(localized.getString(R.string.wearable_reply_label))
            .setChoices(choices)
            .build()
        val intent = Intent(this, TimerService::class.java)
            .setAction(ACTION_WEARABLE_REPLY)
        val pendingIntent = PendingIntent.getService(
            this,
            WEARABLE_REPLY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notif_play,
            localized.getString(R.string.wearable_reply_label),
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun handleWearableReply(intent: Intent) {
        val raw = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_WEARABLE_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        val command = raw.lowercase()
            .replace(" ", "")
            .replace("мин.", "")
            .replace("мин", "")

        val handled = when (command) {
            "дальше", "продолжить", "перерыв", "работа",
            "начатьперерыв", "вернутьсякработе",
            "next", "continue", "break", "work" -> {
                engine.acceptPhaseEnd()
                true
            }
            "+5", "5" -> {
                engine.extendCurrentPhase(EXTEND_5_MINUTES)
                true
            }
            "+10", "10" -> {
                engine.extendCurrentPhase(EXTEND_10_MINUTES)
                true
            }
            "+15", "15" -> {
                engine.extendCurrentPhase(EXTEND_15_MINUTES)
                true
            }
            "стоп", "stop" -> {
                engine.stopCurrentActivity()
                true
            }
            else -> false
        }

        if (handled) {
            notificationManager().cancel(PHASE_END_NOTIFICATION_ID)
        }
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localized.getString(R.string.timer_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager().createNotificationChannel(channel)

        val wearableTimerChannel = NotificationChannel(
            WEARABLE_TIMER_CHANNEL_ID,
            localized.getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = localized.getString(R.string.timer_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            // Keep the companion quiet on the phone. Its purpose is to be bridgeable to Fit2;
            // the phase-end channel remains the one that deliberately vibrates.
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager().createNotificationChannel(wearableTimerChannel)

        val phaseAlertChannel = NotificationChannel(
            PHASE_ALERT_CHANNEL_ID,
            localized.getString(R.string.phase_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = localized.getString(R.string.phase_alert_channel_desc)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            // This short alert is intentionally a real vibrating notification: Samsung Wearable
            // mirrors alerting app notifications to Galaxy Fit2 much more reliably than silent ones.
            // TimerEngine starts the longer custom phone pattern a moment later.
            enableVibration(true)
            vibrationPattern = PHASE_ALERT_VIBRATION_PATTERN
            setSound(null, null)
        }
        notificationManager().createNotificationChannel(phaseAlertChannel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * What the notification would look like. Everything the user can see except the ticking
     * seconds, which the system draws on its own — so an unchanged signature means a re-post would
     * redraw the shade for nothing.
     */
    private fun TimerState.notificationSignature(): String {
        val step = if (totalSeconds > 0) {
            (totalSeconds - remainingSeconds) * PROGRESS_STEPS / totalSeconds
        } else {
            0
        }
        return "$status|$phase|${project?.id}|$step"
    }

    private fun TimerState.wearableNotificationSignature(): String {
        val pausedTime = if (status == TimerStatus.PAUSED) remainingSeconds else 0
        return "$status|$phase|${project?.id}|$totalSeconds|$pausedTime"
    }

    companion object {
        private const val CHANNEL_ID = "timer_channel"
        private const val PHASE_ALERT_CHANNEL_ID = "phase_alert_channel_v2"
        private const val WEARABLE_TIMER_CHANNEL_ID = "wearable_timer_channel_v1"
        private const val NOTIFICATION_ID = 1001
        private const val PHASE_END_NOTIFICATION_ID = 1002
        private const val WEARABLE_TIMER_NOTIFICATION_ID = 1003

        /** The countdown only ever shows whole seconds, so posting faster than this buys nothing. */
        private const val MIN_NOTIFICATION_INTERVAL_MS = 250L

        private const val MILLIS_PER_SECOND = 1000L
        private const val WEARABLE_TIMER_TIMEOUT_MS = 10_000L
        private const val EXTEND_5_MINUTES = 5
        private const val EXTEND_10_MINUTES = 10
        private const val EXTEND_15_MINUTES = 15
        private const val WEARABLE_ALERT_PULSE_MS = 120L
        private const val WEARABLE_ALERT_GAP_MS = 100L
        private val PHASE_ALERT_VIBRATION_PATTERN = longArrayOf(
            0L,
            WEARABLE_ALERT_PULSE_MS,
            WEARABLE_ALERT_GAP_MS,
            WEARABLE_ALERT_PULSE_MS
        )

        /** Progress-bar resolution; finer steps would only mean more re-posts nobody can see. */
        private const val PROGRESS_STEPS = 100
        private const val ACTION_TOGGLE = "com.drklo.pomodoro.action.TOGGLE"
        private const val ACTION_RESET = "com.drklo.pomodoro.action.RESET"
        private const val ACTION_STOP_ACTIVITY = "com.drklo.pomodoro.action.STOP_ACTIVITY"
        private const val ACTION_ACCEPT_PHASE_END = "com.drklo.pomodoro.action.ACCEPT_PHASE_END"
        private const val ACTION_EXTEND_5 = "com.drklo.pomodoro.action.EXTEND_5"
        private const val ACTION_EXTEND_10 = "com.drklo.pomodoro.action.EXTEND_10"
        private const val ACTION_EXTEND_15 = "com.drklo.pomodoro.action.EXTEND_15"
        private const val ACTION_WEARABLE_REPLY = "com.drklo.pomodoro.action.WEARABLE_REPLY"
        private const val KEY_WEARABLE_REPLY = "pomodoro_wearable_reply"
        private const val WEARABLE_REPLY_REQUEST_CODE = 2002

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TimerService::class.java))
        }
    }
}
