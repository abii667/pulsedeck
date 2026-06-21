package com.pulsedeck.app

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WAVEFORM_PHASE_TICK_MS = 250L
private const val LIQUID_AURA_STEPS = 28
private const val LIQUID_WAVE_STEPS = 12
private const val LIQUID_BUBBLE_COUNT = 3

@Composable
private fun rememberSeekScrubModifier(
    onSeek: ((Float) -> Unit)?,
    onSeekPreview: ((Float?) -> Unit)?,
    onScrubbing: (Boolean) -> Unit,
    onScrubProgress: (Float) -> Unit,
): Modifier {
    val currentSeek by rememberUpdatedState(onSeek)
    val currentSeekPreview by rememberUpdatedState(onSeekPreview)
    val currentOnScrubbing by rememberUpdatedState(onScrubbing)
    val currentOnScrubProgress by rememberUpdatedState(onScrubProgress)
    if (onSeek == null) return Modifier

    return Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val gestureStartedAtMillis = SystemClock.uptimeMillis()
            val width = size.width.toFloat().coerceAtLeast(1f)
            fun fractionAt(x: Float): Float = (x / width).coerceIn(0f, 1f)

            var latest = fractionAt(down.position.x)
            var shouldCommit = true
            var updateCount = 0
            var updateTotalNanos = 0L
            var updateMaxNanos = 0L
            var commitNanos = 0L

            fun updateScrubPreview(fraction: Float) {
                val startedAt = System.nanoTime()
                currentOnScrubProgress(fraction)
                currentSeekPreview?.invoke(fraction)
                val elapsed = System.nanoTime() - startedAt
                updateCount += 1
                updateTotalNanos += elapsed
                if (elapsed > updateMaxNanos) updateMaxNanos = elapsed
            }

            currentOnScrubbing(true)
            updateScrubPreview(latest)
            down.consume()

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                    if (change == null) {
                        shouldCommit = false
                        break
                    }
                    latest = fractionAt(change.position.x)
                    updateScrubPreview(latest)
                    change.consume()
                    if (!change.pressed) break
                }
                if (shouldCommit) {
                    val commitStartedAt = System.nanoTime()
                    currentSeek?.invoke(latest)
                    commitNanos = System.nanoTime() - commitStartedAt
                }
            } finally {
                currentSeekPreview?.invoke(null)
                currentOnScrubbing(false)
                PulseDeckPlaybackDiagnostics.recordSeekGesture(
                    durationMillis = SystemClock.uptimeMillis() - gestureStartedAtMillis,
                    updateCount = updateCount,
                    updateTotalNanos = updateTotalNanos,
                    updateMaxNanos = updateMaxNanos,
                    commitNanos = commitNanos,
                    committed = shouldCommit,
                )
            }
        }
    }
}

@Composable
internal fun IconDisc(color: Color, icon: DeckIcon, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        PulseIcon(icon, Color(0xFF211D24), Modifier.fillMaxSize().padding(14.dp))
    }
}
@Composable
private fun Disc(color: Color, label: String, modifier: Modifier = Modifier) { Box(modifier.clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Text(label, color = Color(0xFF211D24), fontSize = 20.sp, fontWeight = FontWeight.Black) } }
@Composable
internal fun Pill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(0.18f))
            .pressScaleEffect(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun Progress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
    thumbRadius: Dp = 8.dp,
    onSeek: ((Float) -> Unit)? = null,
    onSeekPreview: ((Float?) -> Unit)? = null,
    minimal: Boolean = false,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    LaunchedEffect(progress, scrubbing) {
        if (!scrubbing) scrubProgress = progress.coerceIn(0f, 1f)
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val seekModifier = rememberSeekScrubModifier(
            onSeek = onSeek,
            onSeekPreview = onSeekPreview,
            onScrubbing = { scrubbing = it },
            onScrubProgress = { scrubProgress = it },
        )
        val semanticProgress = (if (scrubbing) scrubProgress else progress).coerceIn(0f, 1f)
        val seekAction = onSeek
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Playback progress"
                    progressBarRangeInfo = ProgressBarRangeInfo(semanticProgress, 0f..1f)
                    stateDescription = "${(semanticProgress * 100f).roundToInt()} percent"
                    if (seekAction != null) {
                        setProgress { target ->
                            seekAction(target.coerceIn(0f, 1f))
                            true
                        }
                    }
                }
                .then(seekModifier)
        ) {
            val renderStartedAt = PulseDeckPlaybackDiagnostics.beginRenderSection("progress_canvas")
            try {
                val y = size.height / 2f
                val clampedProgress = semanticProgress
                if (minimal) {
                    val startX = 0f
                    val endX = size.width
                    val progressX = (startX + (endX - startX) * clampedProgress).coerceIn(startX, endX)
                    val lineHeight = (size.height * 0.18f).coerceAtLeast(2.25f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = lineHeight,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.72f),
                        start = Offset(startX, y),
                        end = Offset(progressX, y),
                        strokeWidth = lineHeight,
                        cap = StrokeCap.Round,
                    )
                    return@Canvas
                }
                val trackHeight = size.height * 0.54f
                val trackTop = y - trackHeight / 2f
                val trackCorner = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f)
                val activeWidth = size.width * clampedProgress
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.48f),
                        ),
                    ),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = trackCorner,
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.13f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = trackCorner,
                    style = Stroke(size.height * 0.045f),
                )
                if (activeWidth > 0.5f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.72f),
                                Color.White.copy(alpha = 0.42f),
                            ),
                        ),
                        topLeft = Offset(0f, trackTop),
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = trackCorner,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(2f, trackTop + 1f),
                        size = Size((activeWidth - 4f).coerceAtLeast(0f), trackHeight * 0.30f),
                        cornerRadius = trackCorner,
                    )
                }

                val thumbOuterRadius = thumbRadius.toPx() * 1.18f
                val thumbCenter = Offset((size.width * clampedProgress).coerceIn(thumbOuterRadius, size.width - thumbOuterRadius), y)
                drawCircle(Color.Black.copy(alpha = 0.30f), radius = thumbOuterRadius * 1.18f, center = thumbCenter + Offset(0f, thumbOuterRadius * 0.14f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.Black.copy(alpha = 0.74f),
                            Color.Black.copy(alpha = 0.92f),
                        ),
                        center = thumbCenter + Offset(-thumbOuterRadius * 0.34f, -thumbOuterRadius * 0.38f),
                        radius = thumbOuterRadius * 1.6f,
                    ),
                    radius = thumbOuterRadius,
                    center = thumbCenter,
                )
                drawCircle(Color.White.copy(alpha = 0.34f), radius = thumbOuterRadius, center = thumbCenter, style = Stroke(size.height * 0.055f))
                drawCircle(Color.White.copy(alpha = 0.26f), radius = thumbOuterRadius * 0.38f, center = thumbCenter + Offset(-thumbOuterRadius * 0.18f, -thumbOuterRadius * 0.20f))
            } finally {
                PulseDeckPlaybackDiagnostics.endRenderSection("progress_canvas", renderStartedAt)
            }
        }
    }
}

internal enum class WaveformStyle {
    Classic,
    PulseGlow,
    LiquidCapsule,
}

private class WaveformRenderGeometry(
    val bars: Int,
    val fractions: FloatArray,
    val amplitudes: FloatArray,
    val liquidAuraFractions: FloatArray,
    val liquidAuraSamples: FloatArray,
    val bubbleSeeds: FloatArray,
)

private data class LiquidWaveformColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val deep: Color,
    val bright: Color,
    val alt: Color,
    val upperAura: List<Color>,
    val lowerAura: List<Color>,
    val capsuleFill: List<Color>,
    val activeFill: List<Color>,
    val waveFill: List<Color>,
)

private fun waveformRenderGeometry(source: List<Float>): WaveformRenderGeometry {
    val bars = source.size.coerceAtLeast(18)
    val fractions = FloatArray(bars) { index ->
        if (bars <= 1) 0f else index / (bars - 1).toFloat()
    }
    val amplitudes = FloatArray(bars) { index ->
        source.getOrElse(index) { source[index % source.size] }.coerceIn(0.08f, 1f)
    }
    val liquidAuraFractions = FloatArray(LIQUID_AURA_STEPS + 1) { step ->
        step / LIQUID_AURA_STEPS.toFloat()
    }
    val liquidAuraSamples = FloatArray(LIQUID_AURA_STEPS + 1) { step ->
        val sampleIndex = (liquidAuraFractions[step] * (bars - 1)).roundToInt().coerceIn(0, bars - 1)
        amplitudes[sampleIndex]
    }
    val bubbleSeeds = FloatArray(LIQUID_BUBBLE_COUNT) { index ->
        (index + 1) / (LIQUID_BUBBLE_COUNT + 1f)
    }
    return WaveformRenderGeometry(
        bars = bars,
        fractions = fractions,
        amplitudes = amplitudes,
        liquidAuraFractions = liquidAuraFractions,
        liquidAuraSamples = liquidAuraSamples,
        bubbleSeeds = bubbleSeeds,
    )
}

private fun liquidWaveformColors(palette: AdaptivePalette?): LiquidWaveformColors {
    val primary = palette?.dominant?.let { lerp(it, Color.White, 0.10f) } ?: Color(0xFF21D8C4)
    val secondary = palette?.muted?.let { muted -> palette.dominant.let { lerp(muted, it, 0.36f) } } ?: Color(0xFF8AF8E8)
    val accent = palette?.accent?.let { lerp(it, Color.White, 0.08f) } ?: Color(0xFFFFD166)
    val deep = palette?.deep ?: Color(0xFF071916)
    val bright = lerp(primary, Color.White, 0.32f)
    val alt = lerp(secondary, accent, 0.42f)
    return LiquidWaveformColors(
        primary = primary,
        secondary = secondary,
        accent = accent,
        deep = deep,
        bright = bright,
        alt = alt,
        upperAura = listOf(
            primary.copy(alpha = 0.20f),
            bright.copy(alpha = 0.34f),
            accent.copy(alpha = 0.24f),
            alt.copy(alpha = 0.18f),
        ),
        lowerAura = listOf(
            primary.copy(alpha = 0.11f),
            accent.copy(alpha = 0.17f),
            bright.copy(alpha = 0.18f),
            secondary.copy(alpha = 0.11f),
        ),
        capsuleFill = listOf(
            Color.White.copy(alpha = 0.16f),
            lerp(deep, secondary, 0.20f).copy(alpha = 0.54f),
            Color.Black.copy(alpha = 0.28f),
        ),
        activeFill = listOf(primary, bright, accent),
        waveFill = listOf(
            Color.White.copy(alpha = 0.34f),
            primary.copy(alpha = 0.14f),
            Color.Black.copy(alpha = 0.08f),
        ),
    )
}

@Composable
internal fun Waveform(
    samples: List<Float>,
    progress: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
    onSeekPreview: ((Float?) -> Unit)? = null,
    style: WaveformStyle = WaveformStyle.Classic,
    palette: AdaptivePalette? = null,
    animate: Boolean = playing,
) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("waveform")
    }
    val motion = LocalPulseMotionSpec.current
    var phaseTick by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    val animationActive = playing && animate
    LaunchedEffect(animationActive, motion.disabled) {
        if (motion.disabled || !animationActive) {
            phaseTick = 0f
        } else {
            val phaseStep = WAVEFORM_PHASE_TICK_MS / motion.duration(5200).coerceAtLeast(1).toFloat()
            while (true) {
                delay(WAVEFORM_PHASE_TICK_MS)
                phaseTick = (phaseTick + phaseStep) % 1f
            }
        }
    }
    val phase = if (animationActive) phaseTick else 0f
    LaunchedEffect(progress, scrubbing) {
        if (!scrubbing) scrubProgress = progress.coerceIn(0f, 1f)
    }
    val targetProgress = if (scrubbing) scrubProgress else progress.coerceIn(0f, 1f)
    val progressAnimationDurationMs = if (playing || scrubbing || motion.disabled) {
        0
    } else {
        motion.duration(220)
    }
    val visualProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = progressAnimationDurationMs, easing = PulseMotion.Easing.Standard),
        label = "waveProgress",
    )
    val source = remember(samples) {
        samples.ifEmpty { defaultWaveform("empty", 72) }.map { it.coerceIn(0.08f, 1f) }
    }
    val geometry = remember(source) { waveformRenderGeometry(source) }
    val liquidColors = remember(palette) { liquidWaveformColors(palette) }
    val upperAuraPath = remember { Path() }
    val lowerAuraPath = remember { Path() }
    val wavePath = remember { Path() }
    val seek = onSeek
    val seekModifier = rememberSeekScrubModifier(
        onSeek = onSeek,
        onSeekPreview = onSeekPreview,
        onScrubbing = { scrubbing = it },
        onScrubProgress = { scrubProgress = it },
    )
    val semanticProgress = targetProgress
    Canvas(
        modifier
            .semantics {
                contentDescription = "Waveform seekbar"
                progressBarRangeInfo = ProgressBarRangeInfo(semanticProgress, 0f..1f)
                stateDescription = "${(semanticProgress * 100f).roundToInt()} percent"
                if (seek != null) {
                    setProgress { target ->
                        seek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .then(seekModifier)
    ) {
        val renderStartedAt = PulseDeckPlaybackDiagnostics.beginRenderSection("waveform_canvas")
        try {
        PulseDeckPlaybackDiagnostics.recordWaveformDraw(playing, animationActive)
        val bars = geometry.bars
        val safeProgress = (if (scrubbing) scrubProgress else visualProgress).coerceIn(0f, 1f)
        val gap = size.width / bars
        val barWidth = gap * 0.50f
        val usableWidth = (size.width - barWidth).coerceAtLeast(1f)
        val centerY = size.height / 2f
        val activeIndex = (safeProgress * (bars - 1)).roundToInt().coerceIn(0, bars - 1)
        val activeAmplitude = geometry.amplitudes[activeIndex]
        val playheadX = barWidth / 2f + safeProgress * usableWidth
        if (style == WaveformStyle.LiquidCapsule) {
            val capsuleStart = barWidth * 1.2f
            val capsuleEnd = (size.width - barWidth * 1.2f).coerceAtLeast(capsuleStart + 1f)
            val capsuleWidth = capsuleEnd - capsuleStart
            val capsuleHeight = (size.height * 0.42f).coerceIn(24f, 38f)
            val capsuleTop = centerY - capsuleHeight * 0.42f
            val capsuleBottom = capsuleTop + capsuleHeight
            val capsuleRadius = capsuleHeight / 2f
            val activeX = (capsuleStart + capsuleWidth * safeProgress).coerceIn(capsuleStart, capsuleEnd)
            val activeWidth = (activeX - capsuleStart).coerceAtLeast(0f)
            val pulse = if (animationActive) ((sin(phase * 6.28318f) + 1f) * 0.5f) else 0.18f
            val corner = androidx.compose.ui.geometry.CornerRadius(capsuleRadius, capsuleRadius)
            upperAuraPath.reset()
            lowerAuraPath.reset()
            repeat(LIQUID_AURA_STEPS + 1) { step ->
                val fraction = geometry.liquidAuraFractions[step]
                val sample = geometry.liquidAuraSamples[step]
                val x = capsuleStart + capsuleWidth * fraction
                val playheadDistance = abs(fraction - safeProgress)
                val activeBand = (1f - (playheadDistance / 0.24f).coerceIn(0f, 1f))
                val flow = sin(phase * 6.28318f * 1.08f + fraction * 6.28318f * 2.35f)
                val slowFlow = sin(phase * 6.28318f * 0.58f + fraction * 6.28318f * 1.15f + 0.85f)
                val height = capsuleHeight * (0.20f + sample * 0.40f) * (0.82f + activeBand * 0.34f)
                val upperY = capsuleTop - capsuleHeight * 0.16f - height * (0.48f + flow * 0.20f + slowFlow * 0.08f)
                val lowerY = capsuleBottom + capsuleHeight * 0.12f + height * (0.34f - flow * 0.12f + slowFlow * 0.06f)
                if (step == 0) {
                    upperAuraPath.moveTo(x, upperY)
                    lowerAuraPath.moveTo(x, lowerY)
                } else {
                    upperAuraPath.lineTo(x, upperY)
                    lowerAuraPath.lineTo(x, lowerY)
                }
            }
            val auraBrush = Brush.horizontalGradient(
                liquidColors.upperAura,
                startX = capsuleStart,
                endX = capsuleEnd,
            )
            val lowerAuraBrush = Brush.horizontalGradient(
                liquidColors.lowerAura,
                startX = capsuleStart,
                endX = capsuleEnd,
            )
            drawPath(
                path = upperAuraPath,
                brush = auraBrush,
                style = Stroke((capsuleHeight * 0.090f).coerceIn(2.2f, 4.2f), cap = StrokeCap.Round),
            )
            drawPath(
                path = upperAuraPath,
                color = Color.White.copy(alpha = if (animationActive) 0.10f + pulse * 0.06f else 0.07f),
                style = Stroke((capsuleHeight * 0.035f).coerceAtLeast(1.1f), cap = StrokeCap.Round),
            )
            drawPath(
                path = lowerAuraPath,
                brush = lowerAuraBrush,
                style = Stroke((capsuleHeight * 0.055f).coerceIn(1.6f, 3.0f), cap = StrokeCap.Round),
            )
            drawRoundRect(
                color = lerp(liquidColors.deep, Color.Black, 0.38f).copy(alpha = 0.56f),
                topLeft = Offset(capsuleStart, capsuleTop + capsuleHeight * 0.10f),
                size = Size(capsuleWidth, capsuleHeight),
                cornerRadius = corner,
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    liquidColors.capsuleFill,
                ),
                topLeft = Offset(capsuleStart, capsuleTop),
                size = Size(capsuleWidth, capsuleHeight),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(capsuleStart, capsuleTop),
                size = Size(capsuleWidth, capsuleHeight),
                cornerRadius = corner,
                style = Stroke((capsuleHeight * 0.055f).coerceAtLeast(1.25f)),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(capsuleStart + capsuleHeight * 0.18f, capsuleTop + capsuleHeight * 0.16f),
                size = Size((capsuleWidth - capsuleHeight * 0.36f).coerceAtLeast(0f), capsuleHeight * 0.18f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(capsuleHeight * 0.10f, capsuleHeight * 0.10f),
            )
            if (activeWidth > 1f) {
                drawRoundRect(
                    color = liquidColors.primary.copy(alpha = if (animationActive) 0.15f + pulse * 0.08f else 0.12f),
                    topLeft = Offset(capsuleStart, capsuleTop - capsuleHeight * 0.30f),
                    size = Size(activeWidth, capsuleHeight * 1.60f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(capsuleRadius * 1.2f, capsuleRadius * 1.2f),
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        liquidColors.activeFill,
                        startX = capsuleStart,
                        endX = activeX.coerceAtLeast(capsuleStart + 1f),
                    ),
                    topLeft = Offset(capsuleStart, capsuleTop),
                    size = Size(activeWidth, capsuleHeight),
                    cornerRadius = corner,
                )
                val waveTopBase = capsuleTop + capsuleHeight * (0.34f + activeAmplitude * 0.08f)
                val waveAmp = capsuleHeight * (0.08f + activeAmplitude * 0.045f)
                wavePath.reset()
                wavePath.moveTo(capsuleStart, capsuleBottom)
                wavePath.lineTo(capsuleStart, waveTopBase)
                repeat(LIQUID_WAVE_STEPS + 1) { step ->
                    val fraction = step / LIQUID_WAVE_STEPS.toFloat()
                    val x = capsuleStart + activeWidth * fraction
                    val wave = sin(phase * 6.28318f + fraction * 6.28318f * 1.7f)
                    val y = waveTopBase + wave * waveAmp
                    wavePath.lineTo(x, y)
                }
                wavePath.lineTo(activeX, capsuleBottom)
                wavePath.close()
                drawPath(
                    path = wavePath,
                    brush = Brush.verticalGradient(
                        liquidColors.waveFill,
                        startY = capsuleTop,
                        endY = capsuleBottom,
                    ),
                )
                drawLine(
                    color = Color.White.copy(alpha = if (animationActive) 0.40f + pulse * 0.12f else 0.30f),
                    start = Offset(capsuleStart + capsuleHeight * 0.26f, waveTopBase - waveAmp * 0.72f),
                    end = Offset((activeX - capsuleHeight * 0.22f).coerceAtLeast(capsuleStart), waveTopBase - waveAmp * 0.18f),
                    strokeWidth = (capsuleHeight * 0.035f).coerceAtLeast(1.2f),
                    cap = StrokeCap.Round,
                )
                repeat(LIQUID_BUBBLE_COUNT) { index ->
                    val seed = geometry.bubbleSeeds[index]
                    val drift = if (animationActive) sin(phase * 6.28318f + index * 1.9f) * 0.035f else 0f
                    val bubbleFraction = (seed + drift).coerceIn(0.06f, 0.94f)
                    val bubbleX = capsuleStart + activeWidth * bubbleFraction
                    if (bubbleX < activeX - capsuleHeight * 0.12f) {
                        val bubbleY = capsuleTop + capsuleHeight * (0.62f - (index % 3) * 0.13f + pulse * 0.03f)
                        val bubbleRadius = capsuleHeight * (0.045f + (index % 2) * 0.020f)
                        drawCircle(lerp(liquidColors.bright, Color.White, 0.20f).copy(alpha = 0.16f + pulse * 0.08f), bubbleRadius, Offset(bubbleX, bubbleY))
                    }
                }
            }
            return@Canvas
        }
        if (style == WaveformStyle.PulseGlow) {
            val railStart = barWidth * 1.15f
            val railEnd = (size.width - barWidth * 1.15f).coerceAtLeast(railStart + 1f)
            val railWidth = railEnd - railStart
            val activeX = (railStart + railWidth * safeProgress).coerceIn(railStart, railEnd)
            val pulse = if (animationActive) ((sin(phase * 6.28318f) + 1f) * 0.5f) else 0.18f
            val railHeight = (size.height * 0.085f).coerceIn(5f, 10f)
            val railTop = centerY - railHeight / 2f
            val railCorner = androidx.compose.ui.geometry.CornerRadius(railHeight, railHeight)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.13f),
                        Color.Black.copy(alpha = 0.20f),
                    ),
                ),
                topLeft = Offset(railStart, railTop),
                size = Size(railWidth, railHeight),
                cornerRadius = railCorner,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(railStart, railTop),
                size = Size(railWidth, railHeight),
                cornerRadius = railCorner,
                style = Stroke((railHeight * 0.24f).coerceAtLeast(1f)),
            )
            val activeWidth = (activeX - railStart).coerceAtLeast(0f)
            if (activeWidth > 0.5f) {
                val glowAlpha = if (animationActive) 0.12f + pulse * 0.08f else 0.10f
                drawRoundRect(
                    color = Color(0xFF59E6D2).copy(alpha = glowAlpha),
                    topLeft = Offset(railStart, centerY - railHeight * 2.10f),
                    size = Size(activeWidth, railHeight * 4.20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(railHeight * 2.1f, railHeight * 2.1f),
                )
                drawRoundRect(
                    color = Color(0xFFFFD166).copy(alpha = if (animationActive) 0.08f + pulse * 0.05f else 0.05f),
                    topLeft = Offset((activeX - railWidth * 0.14f).coerceAtLeast(railStart), centerY - railHeight * 1.55f),
                    size = Size((railWidth * 0.18f).coerceAtMost(activeWidth), railHeight * 3.10f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(railHeight * 1.55f, railHeight * 1.55f),
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF56F3D5),
                            Color.White.copy(alpha = 0.92f),
                            Color(0xFFFFD166),
                        ),
                        startX = railStart,
                        endX = activeX,
                    ),
                    topLeft = Offset(railStart, railTop),
                    size = Size(activeWidth, railHeight),
                    cornerRadius = railCorner,
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.32f),
                    topLeft = Offset(railStart + 2f, railTop + railHeight * 0.16f),
                    size = Size((activeWidth - 4f).coerceAtLeast(0f), railHeight * 0.22f),
                    cornerRadius = railCorner,
                )
            }
            repeat(bars) { i ->
                val normalized = geometry.fractions[i]
                val x = railStart + normalized * railWidth
                val base = geometry.amplitudes[i]
                val playheadDistance = abs(normalized - safeProgress)
                val activeBand = (1f - (playheadDistance / 0.20f).coerceIn(0f, 1f))
                val waveLift = if (animationActive) activeBand * (0.08f + activeAmplitude * 0.05f) * sin(phase * 6.28318f + i * 0.19f) else 0f
                val barHeight = size.height * (0.13f + base * 0.48f + waveLift).coerceIn(0.10f, 0.72f)
                val played = normalized <= safeProgress
                val color = when {
                    playheadDistance < 0.026f -> Color.White.copy(alpha = if (animationActive) 0.90f else 0.66f)
                    played -> Color(0xFF7CF5DF).copy(alpha = 0.34f + activeBand * 0.28f)
                    else -> Color.White.copy(alpha = 0.18f + base * 0.15f)
                }
                val topStart = centerY - railHeight * 1.35f
                val bottomStart = centerY + railHeight * 1.35f
                drawLine(
                    color = color.copy(alpha = color.alpha * 0.78f),
                    start = Offset(x, topStart - barHeight * 0.44f),
                    end = Offset(x, topStart),
                    strokeWidth = (barWidth * 0.42f).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(x, bottomStart),
                    end = Offset(x, bottomStart + barHeight * 0.44f),
                    strokeWidth = (barWidth * 0.42f).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                )
            }
            return@Canvas
        }
        drawLine(
            color = Color.White.copy(alpha = if (animationActive) 0.16f + activeAmplitude * 0.16f else 0.10f),
            start = Offset(playheadX, centerY - size.height * 0.50f),
            end = Offset(playheadX, centerY + size.height * 0.50f),
            strokeWidth = barWidth * (2.2f + activeAmplitude * 1.8f),
            cap = StrokeCap.Round,
        )
        repeat(bars) { i ->
            val normalized = geometry.fractions[i]
            val x = barWidth / 2f + normalized * usableWidth
            val base = geometry.amplitudes[i]
            val playheadDistance = abs(normalized - safeProgress)
            val activeBand = (1f - (playheadDistance / 0.16f).coerceIn(0f, 1f))
            val lift = if (animationActive) activeBand * activeAmplitude * (0.035f + sin(phase * 6.28318f + i * 0.18f) * 0.010f) else 0f
            val shimmer = if (animationActive) activeBand * ((sin(phase * 6.28318f + i * 0.20f) + 1f) * 0.035f) else 0f
            val usedScale = if (normalized <= safeProgress) 0.70f else 1f
            val height = size.height * (0.18f + base * 0.76f) * (1f + lift) * usedScale
            val nearPlayhead = playheadDistance < 0.022f
            val played = normalized <= safeProgress
            val remainingFade = 1f - (normalized - safeProgress).coerceIn(0f, 1f) * 0.34f
            val color = when {
                nearPlayhead -> Color.White.copy(alpha = if (animationActive) 0.94f else 0.70f)
                played -> Color(0xFF1D120E).copy(alpha = 0.30f + activeBand * 0.16f)
                else -> Color.White.copy(alpha = ((0.64f + base * 0.22f + shimmer) * remainingFade).coerceIn(0.42f, 0.92f))
            }
            drawLine(
                color,
                Offset(x, centerY - height / 2f),
                Offset(x, centerY + height / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
        if (safeProgress < 0.995f) {
            drawLine(Color.White.copy(alpha = if (animationActive) 0.62f else 0.28f), Offset(playheadX, centerY - size.height * 0.46f), Offset(playheadX, centerY + size.height * 0.46f), strokeWidth = barWidth * 0.72f, cap = StrokeCap.Round)
        }
        } finally {
            PulseDeckPlaybackDiagnostics.endRenderSection("waveform_canvas", renderStartedAt)
        }
    }
}
@Composable
private fun Knob(label: String, value: String, sweep: Float, size: Dp, active: Color = Blue, dimmed: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(size)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(sweep.coerceIn(0f, 1f), 0f..1f)
                    stateDescription = value.ifBlank { "${(sweep.coerceIn(0f, 1f) * 100f).roundToInt()} percent" }
                },
        ) { val stroke = this.size.minDimension * 0.045f; val radius = this.size.minDimension / 2f - stroke; val center = Offset(this.size.width / 2f, this.size.height / 2f); drawCircle(Color.White.copy(if (dimmed) 0.10f else 0.13f), radius, center); drawCircle(Color.White.copy(if (dimmed) 0.12f else 0.24f), radius, center, style = Stroke(stroke)); if (sweep > 0f) drawArc(active, 142f, 256f * sweep, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), style = Stroke(stroke * 1.55f, cap = StrokeCap.Round)); val angle = Math.toRadians((142f + 256f * sweep).toDouble()); val a = Offset(center.x + cos(angle).toFloat() * radius * 0.72f, center.y + sin(angle).toFloat() * radius * 0.72f); val b = Offset(center.x + cos(angle).toFloat() * radius * 0.86f, center.y + sin(angle).toFloat() * radius * 0.86f); drawLine(Color.White.copy(if (dimmed) 0.14f else 0.78f), a, b, strokeWidth = stroke * 1.6f, cap = StrokeCap.Round) }
        Spacer(Modifier.height(14.dp)); Text(label, color = if (dimmed) Color.White.copy(0.18f) else Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); if (value.isNotBlank()) Text(value, color = Muted, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun Badge(text: String, modifier: Modifier = Modifier) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.78f)).padding(horizontal = 10.dp, vertical = 5.dp)) }

@Composable
internal fun SwitchSetting(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (checked) Blue.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = if (checked) 0.13f else 0.055f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(if (checked) "ON" else "OFF", color = if (checked) Green else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 8.dp))
        PulseToggle(checked = checked, onChange = onChange)
    }
}

@Composable
internal fun SettingSlider(
    title: String,
    valueText: String,
    left: String,
    right: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    footer: String? = null,
    steps: Int = 0,
    snapStepDb: Float? = null,
) {
    val accent = audioCircleAccentForValue(value, range)
    val accentBright = lerp(accent, Color.White, 0.16f)
    SettingSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                valueText,
                color = accentBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        if (left.isNotBlank() || right.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(left, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(right, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { raw ->
                onChange(if (snapStepDb != null) quantizeAudioDb(raw, range) else raw)
            },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentBright,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
        footer?.let { Text(it, color = Muted.copy(0.72f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
internal fun PulseToggle(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val motion = LocalPulseMotionSpec.current
    val interactionSource = remember { MutableInteractionSource() }
    val travelPx = with(LocalDensity.current) { 24.dp.toPx() }
    val thumbX by animateFloatAsState(
        targetValue = if (checked) travelPx else 0f,
        animationSpec = tween(motion.duration(PulseMotion.Duration.Short), easing = PulseMotion.Easing.Standard),
        label = "pulseToggle",
    )
    Box(
        Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) Green.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.26f))
            .border(1.dp, Color.White.copy(alpha = if (checked) 0.18f else 0.08f), RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(interactionSource = interactionSource, indication = null) { onChange(!checked) } else Modifier)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .graphicsLayer { translationX = thumbX }
                .clip(CircleShape)
                .background(if (checked) Color.White else Color.White.copy(alpha = 0.62f)),
        )
    }
}

@Composable
internal fun NoteBlock(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.050f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(4.dp).height(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.62f)))
        Spacer(Modifier.width(14.dp))
        Text(text, color = Muted, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}
@Composable
internal fun BackgroundPreview(album: Album, settings: BackgroundSettings) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp)),
    ) {
        Background(album, settings)
        Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(album, Modifier.size(54.dp), 14.dp, useCase = ArtworkUseCase.SongListThumbnail)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Live Background", color = Color.White, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
                Text(album.title, color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun ColorSwatch(album: Album, settings: BackgroundSettings) {
    val top = tunedBackgroundColor(album.tint, settings)
    val mid = tunedBackgroundColor(album.alt, settings)
    Column {
        Text("Background Gradient Color", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Text("Album adaptive palette", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(width = 74.dp, height = 38.dp).clip(RoundedCornerShape(20.dp)).background(top))
            Box(Modifier.size(width = 74.dp, height = 38.dp).clip(RoundedCornerShape(20.dp)).background(mid))
            Box(Modifier.size(width = 74.dp, height = 38.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black))
        }
    }
}

