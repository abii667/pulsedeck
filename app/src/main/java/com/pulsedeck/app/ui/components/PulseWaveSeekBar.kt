package com.pulsedeck.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

private val PulseWaveEaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
private val PulseWaveEaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

@Composable
fun PulseWaveSeekBar(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    waveHeight: Dp = 28.dp,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 7.dp,
    enabled: Boolean = true,
) {
    val safeProgress = progress
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f)
        ?: 0f
    val latestOnSeek by rememberUpdatedState(onSeek)
    val transition = rememberInfiniteTransition(label = "pulseWaveSeekBar")
    val phaseOffset by transition.animateFloat(
        initialValue = -0.08f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = PulseWaveEaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseWavePhase",
    )
    val ampMultiplier by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = PulseWaveEaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseWaveAmplitude",
    )
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = PulseWaveEaseOutCubic),
        label = "pulseWavePlayPause",
    )

    Canvas(
        modifier
            .height(waveHeight * 2 + thumbRadius * 2)
            .graphicsLayer { alpha = if (enabled) 1f else 0.48f }
            .semantics {
                contentDescription = "Playback progress"
                progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
                stateDescription = "${(safeProgress * 100f).roundToInt()} percent"
                if (enabled) {
                    setProgress { target ->
                        latestOnSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                fun seekFromTouch(touchX: Float) {
                    val width = size.width.toFloat()
                    if (width <= 0f) return
                    latestOnSeek((touchX / width).coerceIn(0f, 1f))
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    seekFromTouch(down.position.x)
                    down.consume()

                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                seekFromTouch(change.position.x)
                                if (change.positionChange() != Offset.Zero) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * safeProgress
        val waveHeightPx = waveHeight.toPx()
        val trackHeightPx = trackHeight.toPx()
        val thumbRadiusPx = thumbRadius.toPx()
        val effectiveWaveH = waveHeightPx * ampMultiplier * amplitudeFactor
        val trackTop = centerY - trackHeightPx / 2f
        val trackCorner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

        drawRoundRect(
            color = Color(0x33FFFFFF),
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeightPx),
            cornerRadius = trackCorner,
        )

        if (progressX > 0.5f) {
            drawRoundRect(
                color = Color(0x556DD5FA),
                topLeft = Offset(0f, trackTop),
                size = Size(progressX, trackHeightPx),
                cornerRadius = trackCorner,
            )
        }

        if (progressX > 2f && effectiveWaveH > 0.5f) {
            drawPath(
                path = buildWavePath(
                    centerY = centerY,
                    progressX = progressX,
                    waveHeight = effectiveWaveH * 0.55f,
                    phaseShift = -phaseOffset * 0.7f + 0.035f,
                ),
                color = Color(0x806DD5FA),
            )
            drawPath(
                path = buildWavePath(
                    centerY = centerY,
                    progressX = progressX,
                    waveHeight = effectiveWaveH,
                    phaseShift = phaseOffset,
                ),
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6DD5FA),
                        Color(0xCC6DD5FA),
                    ),
                    startY = centerY - effectiveWaveH,
                    endY = centerY,
                ),
            )
        }

        val thumbCenter = Offset(progressX, centerY)
        drawCircle(
            color = Color(0x406DD5FA),
            radius = thumbRadiusPx * 2.35f,
            center = thumbCenter,
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadiusPx,
            center = thumbCenter,
        )
    }
}

private fun buildWavePath(
    centerY: Float,
    progressX: Float,
    waveHeight: Float,
    phaseShift: Float,
): Path {
    val effectiveProgressX = max(1f, progressX)
    val cp1X = (effectiveProgressX * (0.30f + phaseShift))
        .coerceIn(0f, effectiveProgressX)
    val cp2X = (effectiveProgressX * (0.70f - phaseShift))
        .coerceIn(0f, effectiveProgressX)
    val cpY = centerY - waveHeight

    return Path().apply {
        moveTo(0f, centerY)
        cubicTo(
            x1 = cp1X,
            y1 = cpY,
            x2 = cp2X,
            y2 = cpY,
            x3 = effectiveProgressX,
            y3 = centerY,
        )
        close()
    }
}
