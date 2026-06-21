package com.pulsedeck.app.settings.runtime

import androidx.media3.common.C
import com.pulsedeck.app.settings.model.ButtonPressMode

enum class HardwareButtonKind {
    AmbiguousPlayPause,
    Play,
    Pause,
    PlayPause,
    Stop,
    Next,
    Previous,
    SeekForward,
    SeekBack,
    Unknown,
}

enum class HardwarePlaybackAction {
    Play,
    Pause,
    PlayPause,
    Stop,
    Next,
    Previous,
    SeekForward10,
    SeekBack10,
}

fun actionForDedicatedButton(kind: HardwareButtonKind): HardwarePlaybackAction? =
    when (kind) {
        HardwareButtonKind.Play -> HardwarePlaybackAction.Play
        HardwareButtonKind.Pause -> HardwarePlaybackAction.Pause
        HardwareButtonKind.PlayPause -> HardwarePlaybackAction.PlayPause
        HardwareButtonKind.Stop -> HardwarePlaybackAction.Stop
        HardwareButtonKind.Next -> HardwarePlaybackAction.Next
        HardwareButtonKind.Previous -> HardwarePlaybackAction.Previous
        HardwareButtonKind.SeekForward -> HardwarePlaybackAction.SeekForward10
        HardwareButtonKind.SeekBack -> HardwarePlaybackAction.SeekBack10
        HardwareButtonKind.AmbiguousPlayPause,
        HardwareButtonKind.Unknown -> null
    }

fun actionForAmbiguousButton(
    mode: ButtonPressMode,
    completedClickCount: Int,
    longPress: Boolean,
): HardwarePlaybackAction =
    when (mode) {
        ButtonPressMode.SinglePress -> HardwarePlaybackAction.PlayPause
        ButtonPressMode.DoubleTripleNextPrev -> when {
            completedClickCount >= 3 -> HardwarePlaybackAction.Previous
            completedClickCount == 2 -> HardwarePlaybackAction.Next
            else -> HardwarePlaybackAction.PlayPause
        }
        ButtonPressMode.LongPressNext -> if (longPress) {
            HardwarePlaybackAction.Next
        } else {
            HardwarePlaybackAction.PlayPause
        }
    }

fun shouldConsumeStrictNoop(
    action: HardwarePlaybackAction,
    isPlaying: Boolean,
    strict: Boolean,
): Boolean =
    strict && when (action) {
        HardwarePlaybackAction.Play -> isPlaying
        HardwarePlaybackAction.Pause,
        HardwarePlaybackAction.Stop -> !isPlaying
        HardwarePlaybackAction.PlayPause,
        HardwarePlaybackAction.Next,
        HardwarePlaybackAction.Previous,
        HardwarePlaybackAction.SeekForward10,
        HardwarePlaybackAction.SeekBack10 -> false
    }

fun commandNameForHardwareAction(action: HardwarePlaybackAction): String =
    when (action) {
        HardwarePlaybackAction.Play -> "PLAY"
        HardwarePlaybackAction.Pause -> "PAUSE"
        HardwarePlaybackAction.PlayPause -> "PLAY_PAUSE"
        HardwarePlaybackAction.Stop -> "STOP"
        HardwarePlaybackAction.Next -> "NEXT"
        HardwarePlaybackAction.Previous -> "PREVIOUS"
        HardwarePlaybackAction.SeekForward10 -> "FORWARD"
        HardwarePlaybackAction.SeekBack10 -> "REWIND"
    }

fun relativeSeekTarget(currentPositionMs: Long, durationMs: Long, deltaMs: Long): Long {
    val target = (currentPositionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs == C.TIME_UNSET || durationMs <= 0L) {
        target
    } else {
        target.coerceAtMost(durationMs)
    }
}
