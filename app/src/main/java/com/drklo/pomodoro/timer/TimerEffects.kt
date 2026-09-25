package com.drklo.pomodoro.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.drklo.pomodoro.data.model.VibrationPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Sound and vibration feedback for phase boundaries (global settings F-010, F-016).
 * Plays crisp "ding" tones synthesized at runtime by [ToneSynth] (no bundled audio assets).
 */
class TimerEffects(context: Context) : PhaseFeedback {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var startSoundId: Int = 0

    @Volatile
    private var endSoundId: Int = 0

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        // Synthesizing two WAVs is tens of thousands of sin/exp samples plus disk writes, and this
        // object is built lazily from the first ViewModel — i.e. on the main thread, at cold start.
        // Off the main thread it is, and already-generated files are reused instead of rewritten.
        scope.launch {
            runCatching {
                val dir = appContext.cacheDir
                val startFile = File(dir, "tone_start.wav")
                val endFile = File(dir, "tone_end.wav")
                if (!startFile.exists()) {
                    // Short higher click for start.
                    ToneSynth.writeBell(
                        startFile,
                        freqs = doubleArrayOf(1568.0),
                        weights = doubleArrayOf(1.0),
                        durationSec = 0.16,
                        decay = 22.0
                    )
                }
                if (!endFile.exists()) {
                    // Clear two-harmonic "ding" for the end of an interval.
                    ToneSynth.writeBell(
                        endFile,
                        freqs = doubleArrayOf(1318.5, 2637.0),
                        weights = doubleArrayOf(1.0, 0.45),
                        durationSec = 0.6,
                        decay = 6.5
                    )
                }
                startSoundId = soundPool.load(startFile.absolutePath, 1)
                endSoundId = soundPool.load(endFile.absolutePath, 1)
            }.onFailure { Log.e(TAG, "Could not prepare the timer tones", it) }
        }
    }

    override fun playStart() {
        if (startSoundId != 0) soundPool.play(startSoundId, 1f, 1f, 1, 0, 1f)
    }

    override fun playEnd() {
        if (endSoundId != 0) soundPool.play(endSoundId, 1f, 1f, 1, 0, 1f)
    }

    /** Short acknowledgement for a manual timer start. */
    override fun vibrate() = vibrate(DEFAULT_VIBRATION_MS)

    /** Noticeable phase-end vibration selected in Settings. */
    override fun vibrate(pattern: VibrationPattern) {
        val effect = when (pattern) {
            VibrationPattern.SHORT -> oneShot(DEFAULT_VIBRATION_MS)
            VibrationPattern.MEDIUM -> VibrationEffect.createWaveform(MEDIUM_PATTERN_MS, -1)
            VibrationPattern.LONG -> VibrationEffect.createWaveform(LONG_PATTERN_MS, -1)
            VibrationPattern.CALL -> VibrationEffect.createWaveform(CALL_PATTERN_MS, -1)
        }
        vibrate(effect)
    }

    fun vibrate(durationMs: Long) = vibrate(oneShot(durationMs))

    override fun cancelVibration() {
        vibrator?.cancel()
    }

    private fun oneShot(durationMs: Long): VibrationEffect =
        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(effect)
    }

    private companion object {
        const val TAG = "TimerEffects"
        const val DEFAULT_VIBRATION_MS = 400L

        // Waveforms alternate pause / vibration, starting immediately with the leading zero.
        val MEDIUM_PATTERN_MS = longArrayOf(
            0L, 700L,
            250L, 700L
        )
        val LONG_PATTERN_MS = longArrayOf(
            0L, 900L,
            300L, 900L,
            300L, 900L
        )
        private const val CALL_PULSE_MS = 900L
        private const val CALL_PAUSE_MS = 450L
        private const val CALL_CYCLES = 89

        // About two minutes of incoming-call-style vibration. A manual start replaces this
        // waveform with the normal short acknowledgement, so starting the next phase stops it.
        val CALL_PATTERN_MS = LongArray(1 + CALL_CYCLES * 2) { index ->
            when {
                index == 0 -> 0L
                index % 2 == 1 -> CALL_PULSE_MS
                else -> CALL_PAUSE_MS
            }
        }
    }
}
