package com.pulsedeck.app

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.navigation.Screen
import com.pulsedeck.app.settings.model.MiniPlayerAppearanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private val trackQualityCache = ConcurrentHashMap<String, String>()
@Composable
internal fun TrackRow(track: Track, active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Color.White.copy(0.10f) else Color.Black.copy(0.12f))
            .pressScaleEffect(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(track.album, Modifier.size(52.dp), 13.dp, useCase = ArtworkUseCase.SongListThumbnail)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(track.duration, color = if (active) Color.White else Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}
@Composable
internal fun Mini(
    track: Track,
    trackDirection: Int,
    screen: Screen,
    durationMillis: Long,
    progressController: PlaybackProgressController,
    playing: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekPreview: (Float?) -> Unit = {},
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onDismiss: () -> Unit,
    onScreen: (Screen) -> Unit,
    miniPlayerAppearance: MiniPlayerAppearanceSettings = MiniPlayerAppearanceSettings(),
    modifier: Modifier = Modifier,
) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("mini_player")
    }
    val motion = LocalPulseMotionSpec.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val dismissThresholdPx = with(density) { 72.dp.toPx() }
    var dismissDragY by remember { mutableFloatStateOf(0f) }
    val dismissDrag = Modifier.pointerInput(track.uri, track.title) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            dismissDragY = 0f
            val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var stillPressed = true
                while (stillPressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    stillPressed = event.changes.any { it.id == down.id && it.pressed }
                    if (!stillPressed) return@withTimeoutOrNull false
                }
                false
            } == null
            if (!held) {
                dismissDragY = 0f
                return@awaitEachGesture
            }
            var dragY = 0f
            var active = true
            while (active) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                if (change == null || !change.pressed) {
                    active = false
                } else {
                    dragY = (dragY + change.positionChange().y).coerceAtLeast(0f)
                    dismissDragY = dragY
                }
            }
            if (dragY > dismissThresholdPx) onDismiss()
            dismissDragY = 0f
        }
    }
    val titleSwipe = Modifier.pointerInput(track.uri, track.title) {
        var dragX = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragX = 0f },
            onHorizontalDrag = { _, amount -> dragX += amount },
            onDragEnd = {
                when {
                    dragX < -68f -> onNext()
                    dragX > 68f -> onPrev()
                }
                dragX = 0f
            },
            onDragCancel = { dragX = 0f },
        )
    }
    var displayedTrack by remember { mutableStateOf(track) }
    var outgoingTrack by remember { mutableStateOf<Track?>(null) }
    var slideDirection by remember { mutableIntStateOf(trackDirection) }
    val slide = remember { Animatable(1f) }
    LaunchedEffect(track.uri, track.title, track.artist) {
        val changed = displayedTrack.uri != track.uri || displayedTrack.title != track.title || displayedTrack.artist != track.artist
        if (changed) {
            outgoingTrack = displayedTrack
            displayedTrack = track
            slideDirection = if (trackDirection >= 0) 1 else -1
            slide.snapTo(0f)
            slide.animateTo(1f, tween(durationMillis = motion.duration(PulseMotion.Duration.Medium), easing = PulseMotion.Easing.Standard))
            outgoingTrack = null
        }
    }
    val miniShape = RoundedCornerShape(30.dp)
    val miniAppearance = miniPlayerAppearance.normalized()
    val paletteSource = rememberAlbumPalette(track.album)
    val miniPalette = remember(track.album.key, paletteSource) {
        miniPlayerGlassPalette(track.album, paletteSource)
    }
    val textColors = miniPlayerTextColors(miniAppearance)
    fun playWithHaptic() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onPlay()
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .graphicsLayer {
                translationY = dismissDragY
                alpha = 1f - (dismissDragY / (dismissThresholdPx * 1.55f)).coerceIn(0f, 0.45f)
            }
            .then(dismissDrag)
            .clip(miniShape)
            .background(miniPlayerSurfaceBaseBrush(miniPalette, miniAppearance))
            .border(
                1.dp,
                miniPlayerSurfaceBorderBrush(miniPalette, miniAppearance),
                miniShape,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(miniPlayerSurfaceOverlayBrush(miniPalette, miniAppearance)),
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .then(titleSwipe)
                        .clickable(onClick = onOpen),
                ) {
                    val widthPx = with(LocalDensity.current) { 260.dp.toPx() }
                    outgoingTrack?.let { previous ->
                        MiniTrackStrip(
                            track = previous,
                            primaryTextColor = textColors.primary,
                            secondaryTextColor = textColors.secondary,
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - slide.value
                                translationX = -slideDirection * widthPx * slide.value
                            },
                        )
                    }
                    MiniTrackStrip(
                        track = displayedTrack,
                        primaryTextColor = textColors.primary,
                        secondaryTextColor = textColors.secondary,
                        modifier = Modifier.graphicsLayer {
                            alpha = slide.value
                            translationX = slideDirection * widthPx * (1f - slide.value)
                        },
                    )
                }
                Spacer(Modifier.width(10.dp))
                IconTransport(if (playing) DeckIcon.Pause else DeckIcon.Play, ::playWithHaptic, 40.dp, transparent = true)
            }
            Spacer(Modifier.height(2.dp))
            MiniProgressStrip(
                durationMillis = durationMillis,
                progressController = progressController,
                onSeek = onSeek,
                onSeekPreview = onSeekPreview,
            )
            Spacer(Modifier.height(4.dp))
            MiniNavGlass(
                containerAlpha = miniPlayerNavContainerAlpha(miniAppearance),
                borderAlpha = miniPlayerNavBorderAlpha(miniAppearance),
            ) {
                BottomNav(screen, onScreen, height = 48.dp, iconWidth = 54.dp, iconHeight = 48.dp, iconSize = 25.dp, plainSelection = true)
            }
        }
    }
}

@Composable
private fun MiniProgressStrip(
    durationMillis: Long,
    progressController: PlaybackProgressController,
    onSeek: (Float) -> Unit,
    onSeekPreview: (Float?) -> Unit,
) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("mini_progress")
    }
    val positionMillis by progressController.positionMillis.collectAsState()
    val progress = if (durationMillis > 0L) {
        playbackProgressFraction(positionMillis, durationMillis)
    } else {
        0f
    }
    MiniCoverProgressLine(
        progress = progress,
        enabled = durationMillis > 0L,
        onSeek = onSeek,
        onSeekPreview = onSeekPreview,
    )
}

@Composable
private fun MiniCoverProgressLine(
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    onSeekPreview: (Float?) -> Unit,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    LaunchedEffect(progress, scrubbing) {
        if (!scrubbing) scrubProgress = progress.coerceIn(0f, 1f)
    }
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnSeekPreview by rememberUpdatedState(onSeekPreview)
    val displayProgress = (if (scrubbing) scrubProgress else progress).coerceIn(0f, 1f)
    val inactiveColor = Color.White.copy(alpha = 0.22f)
    val activeColor = Color.White.copy(alpha = 0.86f)
    val thumbColor = Color.White.copy(alpha = 0.92f)
    val seekModifier = Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val width = size.width.toFloat().coerceAtLeast(1f)
            fun fractionAt(x: Float): Float = (x / width).coerceIn(0f, 1f)
            fun updatePreview(value: Float) {
                scrubProgress = value
                latestOnSeekPreview(value)
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
                latestOnSeek(latest)
            } finally {
                latestOnSeekPreview(null)
                scrubbing = false
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics {
                contentDescription = "Playback progress"
                progressBarRangeInfo = ProgressBarRangeInfo(displayProgress, 0f..1f)
                stateDescription = "${(displayProgress * 100f).roundToInt()} percent"
                if (enabled) {
                    setProgress { target ->
                        onSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .then(seekModifier),
    ) {
        val y = size.height / 2f
        val progressX = (size.width * displayProgress).coerceIn(0f, size.width)
        val strokeWidth = 4.8.dp.toPx()
        val thumbRadius = 4.2.dp.toPx()

        drawLine(
            color = inactiveColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        if (progressX > 0.5f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, y),
                end = Offset(progressX, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(progressX, y),
        )
    }
}

@Composable
internal fun rememberTrackQuality(track: Track): String {
    val context = LocalContext.current
    val cacheKey = remember(track.uri, track.title, track.artist) { track.uri?.toString() ?: "${track.title}/${track.artist}" }
    var quality by remember(cacheKey) { mutableStateOf(trackQualityCache[cacheKey] ?: track.quality) }
    LaunchedEffect(cacheKey, track.uri) {
        val uri = track.uri ?: return@LaunchedEffect
        trackQualityCache[cacheKey]?.let {
            quality = it
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { readTrackQuality(context, uri, track.quality) }
        trackQualityCache[cacheKey] = loaded
        quality = loaded
    }
    return quality
}

private fun readTrackQuality(context: Context, uri: Uri, fallback: String): String {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return fallback
        val format = extractor.getTrackFormat(audioTrack)
        val mime = format.getString(MediaFormat.KEY_MIME)
        val type = audioTypeLabel(mime)
        val bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) format.getInteger(MediaFormat.KEY_BIT_RATE) else 0
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
        buildAudioQualityLabel(type, bitrate, sampleRate, channels).ifBlank { fallback }
    } catch (_: Throwable) {
        fallback
    } finally {
        runCatching { extractor.release() }
    }
}

internal fun audioTypeLabel(mime: String?): String = when (mime?.lowercase()) {
    "audio/mpeg" -> "MP3"
    "audio/flac" -> "FLAC"
    "audio/mp4", "audio/aac", "audio/aac-adts" -> "AAC"
    "audio/x-wav", "audio/wav" -> "WAV"
    "audio/ogg" -> "OGG"
    "audio/opus" -> "OPUS"
    else -> mime?.substringAfter('/')?.uppercase()?.replace('-', ' ') ?: "AUDIO"
}

private fun buildAudioQualityLabel(type: String, bitrate: Int, sampleRate: Int, channels: Int): String {
    val parts = mutableListOf(type)
    if (bitrate > 0) parts += "${((bitrate / 1000f).roundToInt()).coerceAtLeast(1)} KBPS"
    if (sampleRate > 0) {
        val khz = sampleRate / 1000f
        val formatted = if (sampleRate % 1000 == 0) khz.toInt().toString() else String.format(java.util.Locale.US, "%.1f", khz)
        parts += "$formatted KHZ"
    }
    if (channels > 0) parts += when (channels) {
        1 -> "MONO"
        2 -> "STEREO"
        else -> "$channels CH"
    }
    return parts.joinToString("  ")
}

