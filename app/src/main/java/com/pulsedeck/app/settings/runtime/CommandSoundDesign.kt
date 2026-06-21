package com.pulsedeck.app.settings.runtime

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pulsedeck.app.settings.model.HeadsetBluetoothSettings
import com.pulsedeck.app.settings.model.MediaAction
import java.io.Closeable
import kotlin.math.roundToInt

enum class CommandCueSource {
    HeadsetButton,
    MediaSessionButton,
    RejectedCommand,
}

data class CommandSoundCue(
    val toneType: Int,
    val durationMs: Int,
    val delayMs: Long = 0L,
)

class CommandSoundCuePlayer(
    private val streamType: Int = AudioManager.STREAM_MUSIC,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : Closeable {
    private var generator: ToneGenerator? = null
    private var generatorVolumePercent: Int = -1
    private val scheduled = mutableListOf<Runnable>()
    @Volatile private var closed = false

    fun play(
        cues: List<CommandSoundCue>,
        volumePercent: Int,
        interruptPrevious: Boolean = true,
    ): Long {
        if (closed || cues.isEmpty() || volumePercent <= 0) return 0L
        val safeVolume = volumePercent.coerceIn(0, 40)
        if (interruptPrevious) cancelPending()
        val toneGenerator = ensureGenerator(safeVolume) ?: return 0L
        var nextDelay = 0L
        cues.forEach { cue ->
            nextDelay += cue.delayMs
            val runnable = Runnable {
                if (!closed) {
                    runCatching { toneGenerator.startTone(cue.toneType, cue.durationMs) }
                }
            }
            scheduled += runnable
            handler.postDelayed(runnable, nextDelay)
            nextDelay += cue.durationMs.toLong()
        }
        return nextDelay
    }

    fun cancelPending() {
        scheduled.forEach(handler::removeCallbacks)
        scheduled.clear()
    }

    override fun close() {
        closed = true
        cancelPending()
        runCatching { generator?.release() }
        generator = null
        generatorVolumePercent = -1
    }

    private fun ensureGenerator(volumePercent: Int): ToneGenerator? {
        val safeVolume = volumePercent.coerceIn(0, 40)
        val existing = generator
        if (existing != null && generatorVolumePercent == safeVolume) return existing
        runCatching { existing?.release() }
        generator = null
        generatorVolumePercent = -1
        return runCatching { ToneGenerator(streamType, safeVolume) }
            .getOrNull()
            ?.also {
                generator = it
                generatorVolumePercent = safeVolume
            }
    }
}

class CommandHapticFeedback(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun play(
        settings: HeadsetBluetoothSettings,
        source: CommandCueSource,
        rejected: Boolean = false,
    ) {
        if (!shouldPlayCommandHaptic(settings, source)) return
        val target = vibrator ?: return
        if (!target.hasVibrator()) return
        val durationMs = if (rejected) 36L else 22L
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                target.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(durationMs)
            }
        }
    }
}

fun HeadsetBluetoothSettings.commandCueVolumePercent(): Int =
    (beepVolume * 100f).roundToInt().coerceIn(0, 40)

fun commandCuesForMediaAction(
    action: MediaAction,
    settings: HeadsetBluetoothSettings,
    source: CommandCueSource,
): List<CommandSoundCue> {
    if (!shouldPlayCommandCue(settings, source)) return emptyList()
    return when (action) {
        MediaAction.SeekBack10,
        MediaAction.PreviousCategory -> doubleCue(ToneGenerator.TONE_PROP_BEEP, ToneGenerator.TONE_PROP_BEEP)
        MediaAction.SeekForward10,
        MediaAction.NextCategory -> doubleCue(ToneGenerator.TONE_PROP_BEEP2, ToneGenerator.TONE_PROP_BEEP2)
        MediaAction.Close -> listOf(CommandSoundCue(ToneGenerator.TONE_PROP_PROMPT, 90))
        MediaAction.Repeat,
        MediaAction.Shuffle,
        MediaAction.Like,
        MediaAction.Unlike,
        MediaAction.Rating -> listOf(CommandSoundCue(ToneGenerator.TONE_PROP_ACK, 70))
        MediaAction.None -> emptyList()
    }
}

fun commandCuesForKeyCommand(
    command: String,
    settings: HeadsetBluetoothSettings,
    source: CommandCueSource,
): List<CommandSoundCue> {
    if (!shouldPlayCommandCue(settings, source)) return emptyList()
    val normalized = command.uppercase()
    return when {
        "NEXT" in normalized || "FORWARD" in normalized ->
            doubleCue(ToneGenerator.TONE_PROP_BEEP2, ToneGenerator.TONE_PROP_BEEP2)
        "PREVIOUS" in normalized || "REWIND" in normalized ->
            doubleCue(ToneGenerator.TONE_PROP_BEEP, ToneGenerator.TONE_PROP_BEEP)
        "STOP" in normalized || "CLOSE" in normalized ->
            listOf(CommandSoundCue(ToneGenerator.TONE_PROP_PROMPT, 90))
        "PLAY" in normalized || "PAUSE" in normalized ->
            listOf(CommandSoundCue(ToneGenerator.TONE_PROP_ACK, 70))
        else ->
            listOf(CommandSoundCue(ToneGenerator.TONE_PROP_BEEP, 55))
    }
}

fun rejectedCommandCue(settings: HeadsetBluetoothSettings): List<CommandSoundCue> =
    if (settings.beep && settings.beepMore) {
        listOf(CommandSoundCue(ToneGenerator.TONE_PROP_NACK, 70))
    } else {
        emptyList()
    }

private fun shouldPlayCommandCue(settings: HeadsetBluetoothSettings, source: CommandCueSource): Boolean =
    settings.beep && when (source) {
        CommandCueSource.HeadsetButton -> true
        CommandCueSource.MediaSessionButton,
        CommandCueSource.RejectedCommand -> settings.beepMore
    }

fun shouldPlayCommandHaptic(settings: HeadsetBluetoothSettings, source: CommandCueSource): Boolean =
    settings.vibrate && when (source) {
        CommandCueSource.HeadsetButton -> true
        CommandCueSource.MediaSessionButton,
        CommandCueSource.RejectedCommand -> settings.beepMore
    }

private fun doubleCue(firstTone: Int, secondTone: Int): List<CommandSoundCue> =
    listOf(
        CommandSoundCue(firstTone, 45),
        CommandSoundCue(secondTone, 45, delayMs = 35),
    )
