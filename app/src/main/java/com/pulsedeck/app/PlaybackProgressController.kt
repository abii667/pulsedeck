package com.pulsedeck.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PlaybackProgressController(initialPositionMillis: Long = 0L) {
    private val _positionMillis = MutableStateFlow(initialPositionMillis.coerceAtLeast(0L))

    val positionMillis: StateFlow<Long> = _positionMillis.asStateFlow()

    val currentPositionMillis: Long
        get() = _positionMillis.value

    fun updateFromPlayback(positionMillis: Long) {
        _positionMillis.value = positionMillis.coerceAtLeast(0L)
    }

    fun updateFromSeek(positionMillis: Long) {
        _positionMillis.value = positionMillis.coerceAtLeast(0L)
    }

    fun reset() {
        updateFromSeek(0L)
    }
}

internal fun playbackProgressFraction(positionMillis: Long, durationMillis: Long): Float =
    if (durationMillis > 0L) {
        (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0.33f
    }

internal fun playbackPositionFromProgress(durationMillis: Long, progress: Float): Long =
    if (durationMillis > 0L) {
        (durationMillis * progress.coerceIn(0f, 1f)).toLong()
    } else {
        0L
    }
