package com.pulsedeck.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DisabledAlpha = 77
private const val TipFadePeriods = 0.7f
private const val StartFadePeriods = 0.8f
private const val TwoPi = (PI * 2.0).toFloat()
private val SquigglyHeightEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun PulseDeckSquigglySeekBar(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color.White.copy(alpha = 0.72f),
    height: Dp = 12.dp,
    waveLength: Dp = 28.dp,
    lineAmplitude: Dp = 2.4.dp,
    strokeWidth: Dp = 4.2.dp,
    phaseSpeed: Dp = 34.dp,
    onSeek: ((Float) -> Unit)? = null,
    onSeekPreview: ((Float?) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val waveLengthPx = with(density) { waveLength.toPx() }.coerceAtLeast(1f)
    val lineAmplitudePx = with(density) { lineAmplitude.toPx() }.coerceAtLeast(0f)
    val strokeWidthPx = with(density) { strokeWidth.toPx() }.coerceAtLeast(1f)
    val phaseSpeedPx = with(density) { phaseSpeed.toPx() }.coerceAtLeast(0f)
    val strokeStyle = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val squigglePath = remember { Path() }
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnSeekPreview by rememberUpdatedState(onSeekPreview)
    val safeProgress = progress
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f)
        ?: 0f
    var phaseOffset by remember(waveLengthPx) { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(safeProgress) }

    LaunchedEffect(safeProgress, scrubbing) {
        if (!scrubbing) scrubProgress = safeProgress
    }

    val heightFraction by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = SquigglyHeightEasing),
        label = "squigglySeekHeight",
    )

    LaunchedEffect(isPlaying, enabled, scrubbing, waveLengthPx, phaseSpeedPx) {
        if (!isPlaying || !enabled || scrubbing || phaseSpeedPx <= 0f) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
            phaseOffset = (phaseOffset + deltaSeconds * phaseSpeedPx) % waveLengthPx
            lastFrameNanos = frameNanos
        }
    }

    val displayProgress = (if (scrubbing) scrubProgress else safeProgress).coerceIn(0f, 1f)
    val inactiveColor = activeColor.copy(alpha = activeColor.alpha * (DisabledAlpha / 255f))
    val semanticSeek = latestOnSeek

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "Playback progress"
                progressBarRangeInfo = ProgressBarRangeInfo(displayProgress, 0f..1f)
                stateDescription = "${(displayProgress * 100f).roundToInt()} percent"
                if (enabled && semanticSeek != null) {
                    setProgress { target ->
                        semanticSeek?.invoke(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled || latestOnSeek == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    fun fractionAt(x: Float): Float = (x / width).coerceIn(0f, 1f)
                    fun updatePreview(value: Float) {
                        scrubProgress = value
                        latestOnSeekPreview?.invoke(value)
                    }

                    scrubbing = true
                    var latest = fractionAt(down.position.x)
                    updatePreview(latest)
                    down.consume()

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null) break
                            latest = fractionAt(change.position.x)
                            updatePreview(latest)
                            if (change.positionChange() != Offset.Zero) {
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                        latestOnSeek?.invoke(latest)
                    } finally {
                        latestOnSeekPreview?.invoke(null)
                        scrubbing = false
                    }
                }
            },
    ) {
        val width = size.width
        if (width <= 0f) return@Canvas

        val centerY = size.height / 2f
        val totalProgressPx = width * displayProgress
        val waveEnd = totalProgressPx.coerceIn(0f, width)
        val tipFadeLength = waveLengthPx * TipFadePeriods
        val startFadeLength = waveLengthPx * StartFadePeriods
        val maxDrawAmplitude = lineAmplitudePx * heightFraction

        squigglePath.buildSquigglyProgressPath(
            width = width,
            centerY = centerY,
            waveEnd = waveEnd,
            waveLength = waveLengthPx,
            lineAmplitude = lineAmplitudePx,
            heightFraction = heightFraction,
            phaseOffset = phaseOffset,
            tipFadeLength = tipFadeLength,
            startFadeLength = startFadeLength,
        )

        val clipTop = centerY - maxDrawAmplitude - strokeWidthPx * 1.5f
        val clipBottom = centerY + maxDrawAmplitude + strokeWidthPx * 1.5f
        val activeRight = totalProgressPx.coerceIn(0f, width)

        clipRect(left = 0f, top = clipTop, right = activeRight, bottom = clipBottom) {
            drawPath(path = squigglePath, color = activeColor, style = strokeStyle)
        }
        clipRect(left = activeRight, top = clipTop, right = width, bottom = clipBottom) {
            drawPath(path = squigglePath, color = inactiveColor, style = strokeStyle)
        }

        if (activeRight > 0.5f) {
            drawCircle(
                color = activeColor.copy(alpha = (activeColor.alpha * 0.34f).coerceIn(0f, 1f)),
                radius = strokeWidthPx * 1.45f,
                center = Offset(activeRight, centerY),
            )
            drawCircle(
                color = activeColor,
                radius = strokeWidthPx * 0.82f,
                center = Offset(activeRight, centerY),
            )
        }
    }
}

private fun Path.buildSquigglyProgressPath(
    width: Float,
    centerY: Float,
    waveEnd: Float,
    waveLength: Float,
    lineAmplitude: Float,
    heightFraction: Float,
    phaseOffset: Float,
    tipFadeLength: Float,
    startFadeLength: Float,
) {
    reset()
    moveTo(width, centerY)
    lineTo(waveEnd, centerY)

    if (waveLength <= 0f || waveEnd <= 0f) {
        lineTo(0f, centerY)
        return
    }

    var currentX = waveEnd
    val stepPx = (waveLength / 8f).coerceAtLeast(3f)
    while (currentX > 0f) {
        val nextX = (currentX - stepPx).coerceAtLeast(0f)
        val midX = (currentX + nextX) / 2f
        val nextY = squigglyYAt(
            x = nextX,
            centerY = centerY,
            waveEnd = waveEnd,
            lineAmplitude = lineAmplitude,
            heightFraction = heightFraction,
            waveLength = waveLength,
            phaseOffset = phaseOffset,
            tipFadeLength = tipFadeLength,
            startFadeLength = startFadeLength,
        )
        val midY = squigglyYAt(
            x = midX,
            centerY = centerY,
            waveEnd = waveEnd,
            lineAmplitude = lineAmplitude,
            heightFraction = heightFraction,
            waveLength = waveLength,
            phaseOffset = phaseOffset,
            tipFadeLength = tipFadeLength,
            startFadeLength = startFadeLength,
        )
        cubicTo(
            x1 = currentX - (currentX - nextX) / 3f,
            y1 = midY,
            x2 = nextX + (currentX - nextX) / 3f,
            y2 = midY,
            x3 = nextX,
            y3 = nextY,
        )

        currentX = nextX
    }

    if (currentX > 0f) lineTo(0f, centerY)
}

private fun squigglyYAt(
    x: Float,
    centerY: Float,
    waveEnd: Float,
    lineAmplitude: Float,
    heightFraction: Float,
    waveLength: Float,
    phaseOffset: Float,
    tipFadeLength: Float,
    startFadeLength: Float,
): Float {
    if (x <= 0f || x >= waveEnd || waveLength <= 0f) return centerY
    val distanceBehindTip = waveEnd - x
    val tipFade = inverseLerpSaturated(0f, tipFadeLength, distanceBehindTip)
    val startFade = inverseLerpSaturated(0f, startFadeLength, x)
    val amplitude = lineAmplitude * heightFraction * minOf(tipFade, startFade)
    val phase = ((distanceBehindTip + phaseOffset) / waveLength) * TwoPi
    return centerY + sin(phase) * amplitude
}

private fun inverseLerp(start: Float, stop: Float, value: Float): Float {
    val range = stop - start
    return if (range == 0f) 0f else (value - start) / range
}

private fun inverseLerpSaturated(start: Float, stop: Float, value: Float): Float =
    inverseLerp(start, stop, value).coerceIn(0f, 1f)
