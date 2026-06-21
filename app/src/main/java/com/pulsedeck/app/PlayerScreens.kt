package com.pulsedeck.app

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.pulsedeck.app.library.normalizedFolderPath
import com.pulsedeck.app.navigation.Screen
import com.pulsedeck.app.player.PlaybackRepeatMode
import com.pulsedeck.app.player.ShuffleMode
import com.pulsedeck.app.player.badge
import com.pulsedeck.app.player.subtitle
import com.pulsedeck.app.player.title
import com.pulsedeck.app.settings.model.FullPlayerContentSettings
import com.pulsedeck.app.settings.model.MiniPlayerAppearanceSettings
import com.pulsedeck.app.settings.model.MiniPlayerAppearanceStyle
import com.pulsedeck.app.settings.model.PlayerButtonAction
import com.pulsedeck.app.settings.model.PlayerButtonSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private data class QueueUndoItem(val index: Int, val track: Track)

private sealed class PlayerUtilityDockControl {
    object Expander : PlayerUtilityDockControl()
    object Save : PlayerUtilityDockControl()
    data class Action(val action: PlayerButtonAction) : PlayerUtilityDockControl()
}

internal data class ArtistCandidate(
    val displayName: String,
    val normalizedName: String,
    val role: ArtistRole,
    val evidence: Set<ArtistEvidence>,
    val sourceChannelId: String?,
    val sourceChannelTitle: String?,
    val confidence: ArtistSourceConfidence,
)

internal enum class ArtistRole {
    Primary,
    Featured,
    Collaborator,
    SeedInterest,
    ChannelOwner,
    Unknown,
}

internal enum class ArtistEvidence {
    TrackArtistMetadata,
    TrackTitleFeaturingText,
    SourceChannelMetadata,
    PremiumDeckSourceMetadata,
    UserInterestSeed,
    LocalLibraryMetadata,
    OfficialChannelMatch,
    TopicChannelMatch,
    SearchResultMatch,
}

internal enum class ArtistSourceConfidence {
    VerifiedOfficial,
    HighConfidence,
    TopicOrAutoGenerated,
    Unverified,
    None,
}

internal data class ArtistContinuationSource(
    val source: YouTubeSource,
    val artist: ArtistCandidate,
    val reason: String,
    val resultScore: Int = 0,
    val views: Long = 0L,
    val rank: Int = 0,
)

internal enum class ArtistWorksSourceMode {
    PremiumDeckOnline,
    PremiumDeckOffline,
    LocalFile,
    PulseRadio,
    Unknown,
}

internal enum class ArtistWorksFetchPolicy {
    AutoShowCachedOnly,
    UserTriggeredOnlineAllowed,
    UserConfirmationRequired,
    OfflineOnly,
    BlockedBetaLocked,
}

internal enum class ArtistContinuationSection {
    Recent,
    LatestProject,
    TopResult,
    Collaboration,
    Saved,
}

internal data class ArtistContinuationItem(
    val source: YouTubeSource,
    val artist: ArtistCandidate,
    val reason: String,
    val section: ArtistContinuationSection,
    val resultScore: Int = 0,
    val views: Long = 0L,
    val rank: Int = 0,
)

internal data class OfficialArtistResolution(
    val displayName: String,
    val sourceChannelId: String?,
    val sourceChannelTitle: String?,
    val confidence: ArtistSourceConfidence,
    val evidence: Set<ArtistEvidence>,
)

internal enum class ArtistReleaseType(val label: String) {
    Album("Album"),
    Ep("EP"),
    Single("Single"),
    Project("Project"),
    Unknown("Release"),
}

internal data class ArtistReleaseGroup(
    val id: String = "",
    val title: String,
    val artist: String,
    val items: List<ArtistContinuationItem>,
    val releaseType: ArtistReleaseType = ArtistReleaseType.Unknown,
    val releaseYearHint: Int = 0,
    val trackCountHint: Int = 0,
    val tracklistPreview: List<AlbumDownloadTrack> = emptyList(),
    val coverUrl: String? = null,
    val confidence: ArtistSourceConfidence,
)

internal data class DiscographyCarouselItem(
    val releaseId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val releaseType: ArtistReleaseType,
    val year: Int?,
    val thumbnailUrl: String?,
    val localArtworkUri: Uri? = null,
    val previewTracks: List<AlbumDownloadTrack>,
    val confidence: ArtistSourceConfidence,
    val sourceLabel: String,
    val fromCache: Boolean,
)

internal data class ArtistWorksSnapshot(
    val artist: ArtistCandidate,
    val source: OfficialArtistResolution?,
    val recentTracks: List<ArtistContinuationItem>,
    val topTracks: List<ArtistContinuationItem>,
    val latestProject: ArtistReleaseGroup?,
    val discographyReleases: List<ArtistReleaseGroup> = emptyList(),
    val collaborations: List<ArtistContinuationItem>,
    val savedInPulseDeck: List<ArtistContinuationItem>,
    val loadedAtEpochMs: Long,
    val confidence: ArtistSourceConfidence,
    val fromCache: Boolean,
)

internal data class PlayerArtistContext(
    val artist: String,
    val primaryArtist: ArtistCandidate? = null,
    val savedPremiumDeckSources: List<ArtistContinuationSource> = emptyList(),
    val collaborationSources: List<ArtistContinuationSource> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val totalTrackCount: Int = 0,
    val albumCount: Int = 0,
    val totalDurationMillis: Long = 0L,
    val genres: List<String> = emptyList(),
)

internal data class ArtistContinuationFetchState(
    val artistKey: String = "",
    val sources: List<ArtistContinuationSource> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val message: String? = null,
    val catalogIdentity: ArtistCatalogIdentity? = null,
    val discographyReleases: List<ArtistReleaseGroup> = emptyList(),
    val loadedAtEpochMs: Long = 0L,
    val fromCache: Boolean = false,
)

private const val NextTeaserInitialDelayMillis = 900L
private const val NextTeaserVisibleMillis = 5_000L
private const val NextTeaserHiddenMillis = 60_000L
private const val QueueMiniPreviewLimit = 24
private const val QueueExpandedPreviewLimit = 48

private fun LayoutCoordinates.playerBoundsInRoot(): AlbumTileBounds {
    val position = positionInRoot()
    return AlbumTileBounds(
        left = position.x,
        top = position.y,
        width = size.width.toFloat(),
        height = size.height.toFloat(),
    )
}

@Composable
internal fun SleepTimerDialog(
    currentMinutes: Int,
    fadeOutEnabled: Boolean,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    var visible by remember { mutableStateOf(false) }
    var selectedMinutes by remember(currentMinutes) { mutableIntStateOf(if (currentMinutes > 0) currentMinutes else 30) }
    var fadeEnabled by remember(fadeOutEnabled) { mutableStateOf(fadeOutEnabled) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(motion.duration(if (visible) PulseMotion.Duration.PopupIn else PulseMotion.Duration.PopupOut), easing = PulseMotion.Easing.Standard), label = "sleepDialogAlpha")
    LaunchedEffect(Unit) { visible = true }
    val dismissInteraction = remember { MutableInteractionSource() }
    val contentInteraction = remember { MutableInteractionSource() }
    BackHandler { onDismiss() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = PulseMotion.Alpha.ModalScrim * alpha))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = PulseMotion.Scale.ModalStart + (1f - PulseMotion.Scale.ModalStart) * alpha
                    scaleY = PulseMotion.Scale.ModalStart + (1f - PulseMotion.Scale.ModalStart) * alpha
                }
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF242018).copy(alpha = 0.96f),
                            Color(0xFF121111).copy(alpha = 0.98f),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = contentInteraction,
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sleep Timer", color = Color.White, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black)
            Text("Fade music before playback stops", color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            SleepTimerDial(minutes = selectedMinutes, onMinutes = { selectedMinutes = it })
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SleepPresetButton("15", selectedMinutes == 15, Modifier.weight(1f)) { selectedMinutes = 15 }
                SleepPresetButton("30", selectedMinutes == 30, Modifier.weight(1f)) { selectedMinutes = 30 }
                SleepPresetButton("60", selectedMinutes == 60, Modifier.weight(1f)) { selectedMinutes = 60 }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("30s fade-out", color = Color.White, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
                    Text("Lower volume smoothly at the end", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                }
                Switch(checked = fadeEnabled, onCheckedChange = { fadeEnabled = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SleepDialogButton("Disable", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.08f), onClick = onDisable)
                SleepDialogButton("Cancel", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.08f), onClick = onDismiss)
                SleepDialogButton("OK", Modifier.weight(1f), tone = Blue.copy(alpha = 0.58f)) { onConfirm(selectedMinutes, fadeEnabled) }
            }
        }
    }
}

@Composable
private fun SleepTimerDial(minutes: Int, onMinutes: (Int) -> Unit) {
    val sweep = (minutes / 120f).coerceIn(0f, 1f)
    Box(
        Modifier
            .size(214.dp)
            .pointerInput(Unit) {
                fun updateFromOffset(offset: Offset) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val angle = (Math.toDegrees(atan2(offset.y - center.y, offset.x - center.x).toDouble()).toFloat() + 90f + 360f) % 360f
                    val snapped = ((angle / 360f * 120f) / 5f).roundToInt().coerceIn(0, 24) * 5
                    onMinutes(snapped)
                }
                detectTapGestures(onPress = { updateFromOffset(it) })
            }
            .pointerInput(Unit) {
                fun updateFromOffset(offset: Offset) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val angle = (Math.toDegrees(atan2(offset.y - center.y, offset.x - center.x).toDouble()).toFloat() + 90f + 360f) % 360f
                    val snapped = ((angle / 360f * 120f) / 5f).roundToInt().coerceIn(0, 24) * 5
                    onMinutes(snapped)
                }
                detectDragGestures(
                    onDragStart = { updateFromOffset(it) },
                    onDrag = { change, _ -> updateFromOffset(change.position) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.075f
            val radius = size.minDimension / 2f - strokeWidth * 1.35f
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawCircle(Color.White.copy(alpha = 0.08f), radius, center, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFFFFB377),
                        Color(0xFFFF7A32),
                        Color(0xFFFFD6A8),
                    ),
                    center = center,
                ),
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            val dotAngle = Math.toRadians((-90f + 360f * sweep).toDouble())
            val dotCenter = Offset(
                center.x + cos(dotAngle).toFloat() * radius,
                center.y + sin(dotAngle).toFloat() * radius,
            )
            drawCircle(Color.White, strokeWidth * 0.56f, dotCenter)
            drawCircle(Color.Black.copy(alpha = 0.36f), strokeWidth * 0.28f, dotCenter)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (minutes <= 0) "OFF" else "$minutes", color = Color.White, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
            Text(if (minutes <= 0) "timer" else "minutes", color = Muted, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SleepPresetButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.20f else 0.06f), RoundedCornerShape(21.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("${label}m", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun SleepDialogButton(label: String, modifier: Modifier = Modifier, tone: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(tone)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}


@Composable
internal fun Player(
    track: Track,
    screen: Screen,
    durationMillis: Long,
    progressController: PlaybackProgressController,
    waveform: List<Float>,
    playing: Boolean,
    shuffleMode: ShuffleMode,
    repeatMode: PlaybackRepeatMode,
    sleepTimerLabel: String?,
    waveformVisible: Boolean,
    waveformAnimating: Boolean = playing && waveformVisible,
    utilityButtons: List<PlayerButtonAction>,
    landscapeUtilityButtons: List<PlayerButtonAction>,
    portraitButtonRows: Int,
    landscapeButtonRows: Int,
    utilityButtonSize: PlayerButtonSize,
    utilityButtonsScrollable: Boolean,
    showGestureHints: Boolean,
    landscapeSplitEnabled: Boolean,
    miniPlayerAppearance: MiniPlayerAppearanceSettings = MiniPlayerAppearanceSettings(),
    fullPlayerContent: FullPlayerContentSettings = FullPlayerContentSettings(),
    liked: Boolean,
    bookmarked: Boolean,
    savedAnywhere: Boolean,
    queueTracks: List<Track>,
    queueTrackReasons: Map<String, PremiumDeckQueueReason> = emptyMap(),
    playbackContextLine: String? = null,
    artistContext: PlayerArtistContext,
    artistContinuationFetchState: ArtistContinuationFetchState = ArtistContinuationFetchState(),
    queueIsCustom: Boolean,
    openFromMini: Boolean,
    launchHandoff: PlayerLaunchHandoff? = null,
    openKey: Int,
    closeRequestKey: Int,
    onPlaying: (Boolean) -> Unit,
    onArtistContinuationSource: (YouTubeSource, List<YouTubeSource>) -> Unit,
    artistWorksSourceMode: ArtistWorksSourceMode,
    artistWorksFetchPolicy: ArtistWorksFetchPolicy,
    onLoadArtistContinuation: (ArtistCandidate, Boolean) -> Unit,
    onOpenArtistDiscography: (ArtistWorksSnapshot) -> Unit,
    onOpenArtistRelease: (ArtistReleaseGroup) -> Unit,
    onDeviceArtistTrack: (Track) -> Unit,
    onTrackMenu: () -> Unit,
    onShuffleClick: () -> Unit,
    onShuffleLongClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onRepeatLongClick: () -> Unit,
    onTimer: () -> Unit,
    onVisualizer: () -> Unit,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
    onSave: () -> Unit,
    onInfo: () -> Unit,
    onLyrics: () -> Unit,
    onShowCategory: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onClose: () -> Unit,
    onBackGesture: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onNextAlbum: () -> Unit,
    onPrevAlbum: () -> Unit,
    onSeek: (Float) -> Unit,
    onQueueMove: (Int, Int) -> Unit,
    onQueueRemove: (Int) -> Unit,
    onQueueInsert: (Int, Track) -> Unit,
    onQueueClear: () -> Unit,
    onScreen: (Screen) -> Unit,
) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("full_player")
    }
    val motion = LocalPulseMotionSpec.current
    val haptic = LocalHapticFeedback.current
    val paletteSource = rememberAlbumPalette(track.album)
    val trackQuality = rememberTrackQuality(track)
    val albumPalette = remember(track.album.key, paletteSource) {
        adaptivePaletteFromColors(paletteSource.first, paletteSource.second, track.album.tint, track.album.alt)
    }
    var dragX by remember(openKey) { mutableFloatStateOf(0f) }
    var dragY by remember(openKey) { mutableFloatStateOf(0f) }
    var edgeBackDragX by remember(openKey) { mutableFloatStateOf(0f) }
    var closing by remember(openKey) { mutableStateOf(false) }
    var closeDispatched by remember(openKey) { mutableStateOf(false) }
    var entered by remember(openKey) { mutableStateOf(false) }
    val openedAtMillis = remember(openKey) { SystemClock.uptimeMillis() }
    var openTransitionLogged by remember(openKey) { mutableStateOf(false) }
    var closeStartedAtMillis by remember(openKey) { mutableStateOf(0L) }
    var observedCloseRequestKey by remember(openKey) { mutableIntStateOf(closeRequestKey) }
    var queueOpen by remember(openKey) { mutableStateOf(false) }
    var queueExpanded by remember(openKey) { mutableStateOf(false) }
    var queueHintSeen by remember(openKey) { mutableStateOf(false) }
    var queueUndoItem by remember(openKey) { mutableStateOf<QueueUndoItem?>(null) }
    var playerRootOffset by remember(openKey) { mutableStateOf(Offset.Zero) }
    var heroArtworkBounds by remember(openKey) { mutableStateOf<AlbumTileBounds?>(null) }
    var playerTitleBounds by remember(openKey) { mutableStateOf<AlbumTileBounds?>(null) }
    var playerArtistBounds by remember(openKey) { mutableStateOf<AlbumTileBounds?>(null) }
    LaunchedEffect(openKey, openFromMini) { entered = true }
    LaunchedEffect(queueOpen) { if (!queueOpen) queueExpanded = false }
    LaunchedEffect(queueOpen, queueTracks.size) {
        if (queueOpen && queueTracks.isNotEmpty() && !queueHintSeen) {
            delay(2600L)
            queueHintSeen = true
        }
    }
    fun removeQueueItem(index: Int) {
        queueTracks.getOrNull(index)?.let { queueUndoItem = QueueUndoItem(index, it) }
        onQueueRemove(index)
    }
    fun undoQueueRemove(item: QueueUndoItem) {
        onQueueInsert(item.index, item.track)
        queueUndoItem = null
    }
    fun playerHaptic() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun togglePlaying() {
        playerHaptic()
        onPlaying(!playing)
    }
    fun setQueueOpenState(open: Boolean) {
        if (queueOpen != open) playerHaptic()
        queueOpen = open
    }
    fun setQueueExpandedState(expanded: Boolean) {
        if (queueExpanded != expanded) playerHaptic()
        queueExpanded = expanded
    }
    fun dispatchClose() {
        if (closeDispatched) return
        closeDispatched = true
        onClose()
    }
    val openProgress by animateFloatAsState(
        targetValue = if (closing) 0f else if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.duration(if (closing) PulseMotion.Duration.PopupOut else PulseMotion.Duration.Panel),
            easing = if (closing) PulseMotion.Easing.Standard else PulseMotion.Easing.Emphasized,
        ),
        label = "playerPanel",
        finishedListener = { value ->
            if (closing && value <= 0.01f) {
                val closeStartedAt = closeStartedAtMillis
                if (closeStartedAt > 0L) {
                    PulseDeckPlaybackDiagnostics.recordPlayerTransition("close", SystemClock.uptimeMillis() - closeStartedAt)
                }
                dispatchClose()
            } else if (!closing && value >= 0.99f && !openTransitionLogged) {
                openTransitionLogged = true
                PulseDeckPlaybackDiagnostics.recordPlayerTransition("open", SystemClock.uptimeMillis() - openedAtMillis)
            }
        },
    )
    val playerDragSpec = if (motion.disabled) tween<Float>(0) else spring(stiffness = PulseMotion.Spring.PlayerGestureStiffness, dampingRatio = PulseMotion.Spring.PlayerDamping)
    val easedX by animateFloatAsState(dragX, playerDragSpec, label = "artDragX")
    val easedY by animateFloatAsState(dragY, playerDragSpec, label = "artDragY")
    fun beginClose() {
        if (closing) return
        onBackGesture()
        dragX = 0f
        dragY = 0f
        closeStartedAtMillis = SystemClock.uptimeMillis()
        closing = true
    }
    BackHandler {
        if (queueExpanded) {
            setQueueExpandedState(false)
        } else if (queueOpen) {
            setQueueOpenState(false)
        } else {
            beginClose()
        }
    }
    LaunchedEffect(closing, openKey) {
        if (closing) {
            delay(motion.delay(PulseMotion.Duration.PopupOut) + 48L)
            dispatchClose()
        }
    }
    LaunchedEffect(closeRequestKey, openKey) {
        if (closeRequestKey != observedCloseRequestKey) {
            observedCloseRequestKey = closeRequestKey
            beginClose()
        }
    }
    val activeProgress = openProgress.coerceIn(0f, 1f)
    val sharedLaunchHandoff = launchHandoff?.takeIf { !openFromMini && it.trackKey == track.stableKey() }
    val sharedLaunchActive = sharedLaunchHandoff != null && !closing
    val sharedDestinationReveal = if (sharedLaunchActive) {
        ((activeProgress - 0.72f) / 0.28f).coerceIn(0f, 1f)
    } else {
        1f
    }
    val contentAlpha = activeProgress
    val heroAlpha = activeProgress * sharedDestinationReveal
    val closePreviewProgress = if (closing) (1f - activeProgress).coerceIn(0f, 1f) else 0f
    val miniPreviewAlpha = closePreviewProgress
    val presentationProgress = activeProgress
    val density = LocalDensity.current
    val defaultUtilityButtons = listOf(
        PlayerButtonAction.Visualization,
        PlayerButtonAction.SleepTimer,
        PlayerButtonAction.Repeat,
        PlayerButtonAction.Shuffle,
    )
    fun utilityIcon(action: PlayerButtonAction): DeckIcon =
        when (action) {
            PlayerButtonAction.PlayPause -> if (playing) DeckIcon.Pause else DeckIcon.Play
            PlayerButtonAction.None -> DeckIcon.Close
            PlayerButtonAction.PreviousTrack -> DeckIcon.Previous
            PlayerButtonAction.NextTrack -> DeckIcon.Next
            PlayerButtonAction.Queue -> DeckIcon.Queue
            PlayerButtonAction.Visualization -> DeckIcon.Wave
            PlayerButtonAction.SleepTimer -> DeckIcon.Timer
            PlayerButtonAction.Repeat -> DeckIcon.Repeat
            PlayerButtonAction.Shuffle -> DeckIcon.Shuffle
            PlayerButtonAction.Like -> DeckIcon.ThumbUp
            PlayerButtonAction.Bookmark -> DeckIcon.Bookmark
            PlayerButtonAction.Info -> DeckIcon.Info
            PlayerButtonAction.Lyrics -> DeckIcon.Pencil
            PlayerButtonAction.CurrentCategory -> DeckIcon.Disc
            PlayerButtonAction.PreviousCategory -> DeckIcon.Rewind
            PlayerButtonAction.NextCategory -> DeckIcon.Forward
            PlayerButtonAction.SeekBack10 -> DeckIcon.Previous
            PlayerButtonAction.SeekForward10 -> DeckIcon.Next
            PlayerButtonAction.TrackMenu -> DeckIcon.More
            PlayerButtonAction.Library -> DeckIcon.Grid
            PlayerButtonAction.AudioSettings -> DeckIcon.Bars
            PlayerButtonAction.Search -> DeckIcon.Search
            PlayerButtonAction.ClosePlayer -> DeckIcon.Close
        }
    fun utilityActive(action: PlayerButtonAction): Boolean =
        when (action) {
            PlayerButtonAction.PlayPause -> playing
            PlayerButtonAction.Visualization -> waveformVisible
            PlayerButtonAction.SleepTimer -> sleepTimerLabel != null
            PlayerButtonAction.Repeat -> repeatMode != PlaybackRepeatMode.AllOnce
            PlayerButtonAction.Shuffle -> shuffleMode != ShuffleMode.Off
            PlayerButtonAction.Queue -> queueOpen
            PlayerButtonAction.Like -> liked
            PlayerButtonAction.Bookmark -> bookmarked
            else -> false
        }
    fun utilityDescription(action: PlayerButtonAction): String =
        when (action) {
            PlayerButtonAction.PlayPause -> if (playing) "Pause" else "Play"
            PlayerButtonAction.None -> "No button"
            PlayerButtonAction.PreviousTrack -> "Previous track"
            PlayerButtonAction.NextTrack -> "Next track"
            PlayerButtonAction.Queue -> if (queueOpen) "Close up next queue" else "Open up next queue"
            PlayerButtonAction.Visualization -> if (waveformVisible) "Visualization on" else "Visualization off"
            PlayerButtonAction.SleepTimer -> sleepTimerLabel?.let { "Sleep timer, $it remaining" } ?: "Sleep timer"
            PlayerButtonAction.Repeat -> "Repeat mode, ${repeatMode.title}. Long press for modes"
            PlayerButtonAction.Shuffle -> "Shuffle mode, ${shuffleMode.title}. Long press for modes"
            PlayerButtonAction.Like -> if (liked) "Unlike track" else "Like track"
            PlayerButtonAction.Bookmark -> if (bookmarked) "Remove bookmark" else "Bookmark track"
            PlayerButtonAction.Info -> "Track information"
            PlayerButtonAction.Lyrics -> "Lyrics"
            PlayerButtonAction.CurrentCategory -> "Current category"
            PlayerButtonAction.PreviousCategory -> "Previous category"
            PlayerButtonAction.NextCategory -> "Next category"
            PlayerButtonAction.SeekBack10 -> "Seek back 10 seconds"
            PlayerButtonAction.SeekForward10 -> "Seek forward 10 seconds"
            PlayerButtonAction.TrackMenu -> "Track menu"
            PlayerButtonAction.Library -> "Library"
            PlayerButtonAction.AudioSettings -> "Audio"
            PlayerButtonAction.Search -> "Search"
            PlayerButtonAction.ClosePlayer -> "Close player"
        }
    fun utilityClick(action: PlayerButtonAction): () -> Unit =
        when (action) {
            PlayerButtonAction.PlayPause -> { -> togglePlaying() }
            PlayerButtonAction.None -> ({ })
            PlayerButtonAction.PreviousTrack -> { -> onPrev() }
            PlayerButtonAction.NextTrack -> { -> onNext() }
            PlayerButtonAction.Queue -> { -> setQueueOpenState(!queueOpen) }
            PlayerButtonAction.Visualization -> { -> onVisualizer() }
            PlayerButtonAction.SleepTimer -> { -> onTimer() }
            PlayerButtonAction.Repeat -> { -> playerHaptic(); onRepeatClick() }
            PlayerButtonAction.Shuffle -> { -> playerHaptic(); onShuffleClick() }
            PlayerButtonAction.Like -> { -> onLike() }
            PlayerButtonAction.Bookmark -> { -> onBookmark() }
            PlayerButtonAction.Info -> { -> onInfo() }
            PlayerButtonAction.Lyrics -> { -> onLyrics() }
            PlayerButtonAction.CurrentCategory -> { -> onShowCategory() }
            PlayerButtonAction.PreviousCategory -> { -> onPrevAlbum() }
            PlayerButtonAction.NextCategory -> { -> onNextAlbum() }
            PlayerButtonAction.SeekBack10 -> { -> onSeekRelative(-10_000L) }
            PlayerButtonAction.SeekForward10 -> { -> onSeekRelative(10_000L) }
            PlayerButtonAction.TrackMenu -> { -> onTrackMenu() }
            PlayerButtonAction.Library -> { -> onScreen(Screen.Library) }
            PlayerButtonAction.AudioSettings -> { -> onScreen(Screen.Audio) }
            PlayerButtonAction.Search -> { -> onScreen(Screen.Search) }
            PlayerButtonAction.ClosePlayer -> { -> beginClose() }
        }
    fun utilityLongClick(action: PlayerButtonAction): (() -> Unit)? =
        when (action) {
            PlayerButtonAction.Repeat -> ({ playerHaptic(); onRepeatLongClick() })
            PlayerButtonAction.Shuffle -> ({ playerHaptic(); onShuffleLongClick() })
            else -> null
        }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                playerRootOffset = coordinates.positionInRoot()
            }
            .graphicsLayer {
                alpha = if (sharedLaunchActive) 1f else presentationProgress
                translationY = if (sharedLaunchActive) 0f else PulseMotion.Distance.PanelLift * 2f * (1f - presentationProgress)
            },
    ) {
        val containerTopPadding = 0.dp
        val containerBottomPadding = 0.dp
        val containerHorizontalPadding = 0.dp
        val containerCornerRadius = 0.dp
        val splitLayout = landscapeSplitEnabled && maxWidth >= 720.dp && maxWidth > maxHeight
        val contentPaneWidth = if (splitLayout) maxWidth * 0.46f else maxWidth
        val detailPaneWidth = if (splitLayout) maxWidth * 0.46f else maxWidth
        val configuredUtilityButtons = if (splitLayout) landscapeUtilityButtons else utilityButtons
        val utilitySlots = configuredUtilityButtons
            .take(6)
            .ifEmpty { defaultUtilityButtons }
        val hasUtilityButtons = utilitySlots.any { it != PlayerButtonAction.None }
        val utilityButtonRows = if (splitLayout) landscapeButtonRows else portraitButtonRows
        val utilityButtonWidth = when (utilityButtonSize) {
            PlayerButtonSize.Small -> 52.dp
            PlayerButtonSize.Normal -> 62.dp
            PlayerButtonSize.Large -> 72.dp
        }

        val titleProgress = activeProgress
        val titleAlpha = titleProgress * contentAlpha * sharedDestinationReveal
        val titleTranslationY = with(density) { PulseMotion.Distance.PanelLift.dp.toPx() * (1f - titleProgress) }

        val controlsProgress = activeProgress
        val controlsAlpha = controlsProgress * contentAlpha
        val controlsTranslationY = with(density) { PulseMotion.Distance.PanelLift.dp.toPx() * (1f - controlsProgress) }
        val queueReveal by animateFloatAsState(
            targetValue = if (queueOpen) 1f else 0f,
            animationSpec = tween(motion.duration(PulseMotion.Duration.Panel), easing = PulseMotion.Easing.Emphasized),
            label = "playerQueueReveal",
        )
        val queueOutsideDismissInteraction = remember { MutableInteractionSource() }
        val queueOutsideDismissModifier = if (queueOpen && !queueExpanded) {
            Modifier.clickable(
                interactionSource = queueOutsideDismissInteraction,
                indication = null,
                onClick = { setQueueOpenState(false) },
            )
        } else {
            Modifier
        }
        val containerShape = RoundedCornerShape(containerCornerRadius)
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = containerHorizontalPadding,
                    end = containerHorizontalPadding,
                    top = containerTopPadding,
                    bottom = containerBottomPadding
                )
                .clip(containerShape)
                .graphicsLayer { alpha = if (sharedLaunchActive) activeProgress else 1f }
                .background(Color.Black)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val renderStartedAt = PulseDeckPlaybackDiagnostics.beginRenderSection("full_player_background")
                try {
                val primary = albumPalette.dominant.fullPlayerColorBoost(saturationMultiplier = 1.30f, valueLift = 0.10f)
                val secondary = albumPalette.muted.fullPlayerColorBoost(saturationMultiplier = 1.22f, valueLift = 0.08f)
                val warmAccent = albumPalette.accent.fullPlayerColorBoost(saturationMultiplier = 1.36f, valueLift = 0.10f)
                val deepTone = lerp(albumPalette.deep.fullPlayerColorBoost(saturationMultiplier = 1.16f, valueLift = 0.04f), Color.Black, 0.12f)
                val upperGlow = lerp(primary, Color.White, 0.14f)
                val maxSide = max(size.width, size.height)
                drawRect(
                    Brush.verticalGradient(
                        0.00f to upperGlow,
                        0.24f to primary.copy(alpha = 1f),
                        0.54f to lerp(primary, secondary, 0.42f).copy(alpha = 0.98f),
                        0.78f to deepTone.copy(alpha = 0.94f),
                        1.00f to Color.Black,
                        startY = 0f,
                        endY = size.height,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to warmAccent.copy(alpha = 0.66f),
                        0.46f to primary.copy(alpha = 0.28f),
                        1.00f to Color.Transparent,
                        center = Offset(size.width * 0.72f, size.height * 0.18f),
                        radius = maxSide * 0.76f,
                    ),
                    radius = maxSide * 0.76f,
                    center = Offset(size.width * 0.72f, size.height * 0.18f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to secondary.copy(alpha = 0.52f),
                        0.62f to Color.Transparent,
                        center = Offset(size.width * 0.20f, size.height * 0.50f),
                        radius = maxSide * 0.62f,
                    ),
                    radius = maxSide * 0.62f,
                    center = Offset(size.width * 0.20f, size.height * 0.50f),
                )
                drawRect(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.58f to Color.Black.copy(alpha = 0.03f),
                        0.82f to Color.Black.copy(alpha = 0.48f),
                        1.00f to Color.Black.copy(alpha = 0.96f),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
                } finally {
                    PulseDeckPlaybackDiagnostics.endRenderSection("full_player_background", renderStartedAt)
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.07f),
                            ),
                        ),
                    ),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (0.01f + 0.025f * activeProgress).coerceIn(0f, 0.04f))))
        }

        val contentModifier = if (splitLayout) {
            Modifier
                .fillMaxHeight()
                .width(contentPaneWidth)
                .align(Alignment.CenterStart)
        } else {
            Modifier.fillMaxSize()
        }
        val playerScrollState = rememberScrollState()
        Column(
            contentModifier
                .statusBarsPadding()
                .verticalScroll(playerScrollState)
                .padding(horizontal = 14.dp)
                .padding(bottom = 94.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .aspectRatio(1f)
                    .onGloballyPositioned { coordinates ->
                        heroArtworkBounds = coordinates.playerBoundsInRoot()
                    }
                    .graphicsLayer {
                        shadowElevation = 30f * openProgress
                        shape = RoundedCornerShape(28.dp)
                        clip = false
                        ambientShadowColor = Color.Black.copy(0.72f)
                        spotShadowColor = Color.Black.copy(0.86f)
                        translationX = easedX
                        translationY = easedY
                        rotationZ = easedX / 48f
                        alpha = heroAlpha
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = if (queueOpen) {
                            "Up next queue for ${track.title}"
                        } else {
                            "Album art for ${track.title}. Swipe left or right to change track, tap to open current category, swipe up for lyrics."
                        }
                    }
                    .pointerInput(track.uri, track.title, queueOpen) {
                        detectTapGestures(
                            onTap = { if (!queueOpen) onShowCategory() },
                            onLongPress = { if (!queueOpen) onTrackMenu() },
                        )
                    }
                    .pointerInput(track.uri, track.title, queueOpen) {
                        detectDragGestures(
                            onDrag = { _, amount ->
                                if (!closing && !queueOpen) {
                                    dragX = (dragX + amount.x).coerceIn(-620f, 620f)
                                    dragY = (dragY + amount.y).coerceIn(-620f, 620f)
                                }
                            },
                            onDragEnd = {
                                if (!closing && !queueOpen) {
                                    when {
                                        dragY > 170f && dragY > abs(dragX) -> {
                                            onShowCategory()
                                        }
                                        dragY < -150f && abs(dragY) > abs(dragX) -> {
                                            onLyrics()
                                        }
                                        dragX < -150f && abs(dragX) > abs(dragY) -> onNext()
                                        dragX > 150f && abs(dragX) > abs(dragY) -> onPrev()
                                    }
                                }
                                dragX = 0f
                                dragY = 0f
                            },
                            onDragCancel = {
                                dragX = 0f
                                dragY = 0f
                            },
                        )
                    },
            ) {
                AlbumHeroArtwork(
                    track.album,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (1f - queueReveal).coerceIn(0f, 1f)
                            scaleX = 1f - 0.035f * queueReveal
                            scaleY = 1f - 0.035f * queueReveal
                        },
                    28.dp,
                )
                if (queueOpen || queueReveal > 0.01f) {
                    UpNextQueuePanel(
                        currentTrack = track,
                        queueTracks = queueTracks,
                        queueReasons = queueTrackReasons,
                        canClear = queueIsCustom,
                        reveal = queueReveal,
                        previewLimit = QueueMiniPreviewLimit,
                        onMove = onQueueMove,
                        onRemove = ::removeQueueItem,
                        onClear = onQueueClear,
                        onExpand = { setQueueExpandedState(true) },
                        showDragHint = !queueHintSeen && queueTracks.isNotEmpty(),
                        undoItem = queueUndoItem,
                        onUndoRemove = ::undoQueueRemove,
                        onDone = { setQueueOpenState(false) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (queueReveal < 0.5f) {
                    IconCircle(
                        DeckIcon.Queue,
                        { setQueueOpenState(true) },
                        46.dp,
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .graphicsLayer { alpha = (1f - queueReveal * 2f).coerceIn(0f, 1f) },
                    )
                    IconCircle(
                        DeckIcon.More,
                        onTrackMenu,
                        46.dp,
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 12.dp)
                            .graphicsLayer { alpha = (1f - queueReveal * 2f).coerceIn(0f, 1f) },
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = titleTranslationY
                    }
                    .then(queueOutsideDismissModifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(14.dp))
                Text(
                    track.title,
                    color = Color.White.copy(0.92f),
                    fontSize = 19.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .onGloballyPositioned { coordinates ->
                            playerTitleBounds = coordinates.playerBoundsInRoot()
                        },
                )
                Text(
                    track.artist,
                    color = Color.White.copy(0.64f),
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                        .onGloballyPositioned { coordinates ->
                            playerArtistBounds = coordinates.playerBoundsInRoot()
                        },
                )
                playbackContextLine?.takeIf { it.isNotBlank() }?.let { contextLine ->
                    Text(
                        contextLine,
                        color = Color.White.copy(alpha = 0.44f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = controlsAlpha
                            translationY = controlsTranslationY
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(if (hasUtilityButtons) 14.dp else 4.dp))
                    if (hasUtilityButtons) {
                        ExpandablePlayerUtilityDock(
                            slots = utilitySlots,
                            buttonWidth = utilityButtonWidth,
                            buttonRows = utilityButtonRows,
                            nextTrack = queueTracks.firstOrNull(),
                            nextReason = queueTracks.firstOrNull()?.let { queueTrackReasons[it.stableKey()] },
                            palette = albumPalette,
                            saveActive = savedAnywhere,
                            saveDescription = if (savedAnywhere) "Manage saved locations" else "Add to Liked Songs or playlists",
                            onSave = { playerHaptic(); onSave() },
                            iconFor = ::utilityIcon,
                            activeFor = ::utilityActive,
                            descriptionFor = ::utilityDescription,
                            clickFor = ::utilityClick,
                            longClickFor = ::utilityLongClick,
                            sleepTimerLabel = sleepTimerLabel,
                            repeatBadge = repeatMode.badge,
                            shuffleBadge = shuffleMode.badge,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().height(138.dp), contentAlignment = Alignment.Center) {
                        val compactControls = maxWidth < 380.dp
                        val albumButtonSize = if (compactControls) 46.dp else 50.dp
                        val trackButtonSize = if (compactControls) 58.dp else 62.dp
                        val playButtonSize = if (compactControls) 80.dp else 86.dp
                        val transportYOffset = if (compactControls) (-36).dp else (-40).dp
                        FullPlayerSeekSurface(
                            durationMillis = durationMillis,
                            durationLabel = track.duration,
                            progressController = progressController,
                            waveform = waveform,
                            playing = playing,
                            waveformAnimating = waveformAnimating && activeProgress > 0.01f,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(84.dp)
                                .graphicsLayer { alpha = 0.90f },
                            onSeek = onSeek,
                            style = WaveformStyle.LiquidCapsule,
                            palette = albumPalette,
                            showBadges = true,
                        )
                        PulsePlaybackControlsRow(
                            playing = playing,
                            playSize = playButtonSize,
                            trackSize = trackButtonSize,
                            albumSize = albumButtonSize,
                            spacing = if (compactControls) 8.dp else 12.dp,
                            onPreviousAlbum = onPrevAlbum,
                            onPreviousTrack = onPrev,
                            onPlayPause = ::togglePlaying,
                            onNextTrack = onNext,
                            onNextAlbum = onNextAlbum,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = transportYOffset),
                        )
                    }
                    Spacer(Modifier.height(8.dp)); Text(trackQuality, color = Color.White.copy(0.70f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    PlayerSupplementalContent(
                        track = track,
                        progressController = progressController,
                        progressEnabled = true,
                        artistContext = artistContext,
                        artistContinuationFetchState = artistContinuationFetchState,
                        fullPlayerContent = fullPlayerContent,
                        sourceMode = artistWorksSourceMode,
                        fetchPolicy = artistWorksFetchPolicy,
                        onOpenLyrics = onLyrics,
                        onArtistContinuationSource = onArtistContinuationSource,
                        onLoadArtistContinuation = onLoadArtistContinuation,
                        onOpenArtistDiscography = onOpenArtistDiscography,
                        onOpenArtistRelease = onOpenArtistRelease,
                        onDeviceArtistTrack = onDeviceArtistTrack,
                    )
                    Spacer(Modifier.height(20.dp))
                }
                if (queueOpen && !queueExpanded) {
                    Box(Modifier.matchParentSize().then(queueOutsideDismissModifier))
                }
            }
        }
        if (splitLayout) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(detailPaneWidth)
                    .padding(end = 26.dp, bottom = 94.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = controlsAlpha
                            translationY = controlsTranslationY * 0.55f
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(track.title, color = Color.White.copy(0.92f), fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    Text(track.artist, color = Color.White.copy(0.64f), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    Text(trackQuality, color = Color.White.copy(0.52f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PulsePlaybackControlsRow(
                            playing = playing,
                            playSize = 76.dp,
                            trackSize = 56.dp,
                            albumSize = 46.dp,
                            spacing = 8.dp,
                            onPreviousAlbum = onPrevAlbum,
                            onPreviousTrack = onPrev,
                            onPlayPause = ::togglePlaying,
                            onNextTrack = onNext,
                            onNextAlbum = onNextAlbum,
                        )
                    }
                    FullPlayerSeekSurface(
                        durationMillis = durationMillis,
                        durationLabel = track.duration,
                        progressController = progressController,
                        waveform = waveform,
                        playing = playing,
                        waveformAnimating = waveformAnimating && activeProgress > 0.01f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .padding(top = 6.dp),
                        onSeek = onSeek,
                        style = WaveformStyle.LiquidCapsule,
                        palette = albumPalette,
                        showBadges = false,
                    )
                }
                if (queueOpen && !queueExpanded) {
                    Box(Modifier.matchParentSize().then(queueOutsideDismissModifier))
                }
            }
        }
        val dockHandoffHorizontalPadding = 18.dp + 14.dp * closePreviewProgress
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .zIndex(3f)
                .navigationBarsPadding()
                .padding(horizontal = dockHandoffHorizontalPadding, vertical = 8.dp),
        ) {
            PlayerBottomDock(
                screen,
                onScreen,
                Modifier.graphicsLayer {
                    alpha = if (closing) 1f else controlsAlpha
                    scaleX = 1f - 0.012f * closePreviewProgress
                    scaleY = 1f - 0.012f * closePreviewProgress
                },
            )
            if (queueOpen && !queueExpanded) {
                Box(Modifier.matchParentSize().then(queueOutsideDismissModifier))
            }
        }
        if (miniPreviewAlpha > 0.01f) {
            PlayerMiniPreview(
                track = track,
                durationMillis = durationMillis,
                progressController = progressController,
                playing = playing,
                screen = screen,
                appearance = miniPlayerAppearance,
                dockAlpha = 0f,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
                    .graphicsLayer {
                        alpha = miniPreviewAlpha
                        translationY = 18f * (1f - miniPreviewAlpha)
                    },
            )
        }
        sharedLaunchHandoff?.takeIf { !closing && activeProgress < 0.995f }?.let { handoff ->
            PlayerLaunchHandoffOverlay(
                track = track,
                handoff = handoff,
                rootOffset = playerRootOffset,
                progress = activeProgress,
                heroBounds = heroArtworkBounds,
                titleBounds = playerTitleBounds,
                artistBounds = playerArtistBounds,
                modifier = Modifier.zIndex(8f),
            )
        }
        if (queueExpanded) {
            ExpandedQueueOverlay(
                visible = queueExpanded,
                currentTrack = track,
                queueTracks = queueTracks,
                queueReasons = queueTrackReasons,
                canClear = queueIsCustom,
                onMove = onQueueMove,
                onRemove = ::removeQueueItem,
                onClear = onQueueClear,
                undoItem = queueUndoItem,
                onUndoRemove = ::undoQueueRemove,
                onDismiss = { setQueueExpandedState(false) },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(54.dp)
                .height(maxHeight)
                .pointerInput(openKey) {
                    detectHorizontalDragGestures(
                        onDragStart = { edgeBackDragX = 0f },
                        onHorizontalDrag = { _, amount ->
                            edgeBackDragX = (edgeBackDragX + amount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (edgeBackDragX > 92f) beginClose()
                            edgeBackDragX = 0f
                        },
                        onDragCancel = { edgeBackDragX = 0f },
                    )
                },
        )
    }
}

@Composable
private fun rememberLyricsUiState(track: Track): LyricsUiState {
    val context = LocalContext.current
    val settings = LyricsRuntime.settings.normalized()
    val revision = LyricsRuntime.cacheRevision
    var state by remember(track.stableKey(), settings, revision) {
        mutableStateOf<LyricsUiState>(LyricsUiState.Loading)
    }
    LaunchedEffect(track.stableKey(), settings, revision) {
        state = LyricsUiState.Loading
        val startedAt = SystemClock.uptimeMillis()
        val loadedState = loadLyricsForTrack(context, track, settings)
        PulseDeckPlaybackDiagnostics.recordLyricsLoad(SystemClock.uptimeMillis() - startedAt, loadedState)
        state = loadedState
    }
    return state
}

@Composable
private fun PlayerSupplementalContent(
    track: Track,
    progressController: PlaybackProgressController,
    progressEnabled: Boolean,
    artistContext: PlayerArtistContext,
    artistContinuationFetchState: ArtistContinuationFetchState,
    fullPlayerContent: FullPlayerContentSettings,
    sourceMode: ArtistWorksSourceMode,
    fetchPolicy: ArtistWorksFetchPolicy,
    onOpenLyrics: () -> Unit,
    onArtistContinuationSource: (YouTubeSource, List<YouTubeSource>) -> Unit,
    onLoadArtistContinuation: (ArtistCandidate, Boolean) -> Unit,
    onOpenArtistDiscography: (ArtistWorksSnapshot) -> Unit,
    onOpenArtistRelease: (ArtistReleaseGroup) -> Unit,
    onDeviceArtistTrack: (Track) -> Unit,
) {
    val contentSettings = fullPlayerContent.normalized()
    val artistWorksSnapshot = remember(artistContext, artistContinuationFetchState) {
        buildArtistWorksSnapshot(artistContext, artistContinuationFetchState)
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (contentSettings.compactCards) 8.dp else 10.dp),
    ) {
        if (contentSettings.showLyrics) {
            val lyricsState = rememberLyricsUiState(track)
            PlayerLyricsCard(
                state = lyricsState,
                progressController = progressController,
                progressEnabled = progressEnabled,
                onOpenLyrics = onOpenLyrics,
            )
        }
        if (contentSettings.showMoreFromArtist) {
            PlayerMoreFromArtistCard(
                snapshot = artistWorksSnapshot,
                fetchState = artistContinuationFetchState,
                sourceMode = sourceMode,
                fetchPolicy = fetchPolicy,
                onSource = onArtistContinuationSource,
                onLoadOfficialResults = onLoadArtistContinuation,
            )
            if (artistWorksSnapshot.topTracks.isNotEmpty()) {
                PlayerArtistContinuationCard(
                    title = "Top results",
                    icon = DeckIcon.StreamLike,
                    items = artistWorksSnapshot.topTracks,
                    onSource = onArtistContinuationSource,
                )
            }
        }
        if (contentSettings.showDiscography) {
            artistWorksSnapshot.latestProject?.let { latestProject ->
                PlayerLatestProjectCard(
                    release = latestProject,
                    onSource = onArtistContinuationSource,
                    onOpenRelease = { onOpenArtistRelease(latestProject) },
                )
            }
            PlayerDiscographyCard(
                snapshot = artistWorksSnapshot,
                fetchState = artistContinuationFetchState,
                sourceMode = sourceMode,
                fetchPolicy = fetchPolicy,
                onOpenRelease = onOpenArtistRelease,
                onOpenDiscography = { onOpenArtistDiscography(artistWorksSnapshot) },
                onLoadOfficialResults = onLoadArtistContinuation,
            )
        }
        if (artistContext.savedPremiumDeckSources.isNotEmpty() || artistContext.localTracks.isNotEmpty()) {
            PlayerAlsoSavedCard(
                artistContext = artistContext,
                snapshot = artistWorksSnapshot,
                onSource = onArtistContinuationSource,
                onTrack = onDeviceArtistTrack,
            )
        }
        if (contentSettings.showAboutArtist) {
            PlayerAboutArtistCard(artistContext)
        }
    }
}

@Composable
private fun PlayerLyricsCard(
    state: LyricsUiState,
    progressController: PlaybackProgressController,
    progressEnabled: Boolean,
    onOpenLyrics: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .graphicsLayer {
                shadowElevation = 0f
                this.shape = shape
                clip = false
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.060f))
            .border(1.dp, Color.White.copy(alpha = 0.190f), shape)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpenLyrics),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Lyrics",
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Open",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(12.dp))
            PlayerLyricsGlassPreviewContent(
                state = state,
                progressController = progressController,
                progressEnabled = progressEnabled,
            )
        }
    }
}

@Composable
private fun PlayerLyricsGlassPreviewContent(
    state: LyricsUiState,
    progressController: PlaybackProgressController,
    progressEnabled: Boolean,
) {
    when (val current = state) {
        LyricsUiState.Loading -> PlayerLyricsGlassMessage("Looking for lyrics...")
        LyricsUiState.Missing -> PlayerLyricsGlassMessage("Lyrics not found for this track.")
        LyricsUiState.BlockedOffline -> PlayerLyricsGlassMessage("Cached lyrics not found. Offline mode is on.")
        LyricsUiState.BlockedDataSaver -> PlayerLyricsGlassMessage("Cached lyrics not found. Data Saver is on.")
        is LyricsUiState.Error -> PlayerLyricsGlassMessage(current.message)
        is LyricsUiState.Synced -> {
            if (progressEnabled) {
                PlayerSyncedLyricsPreview(current.lines, progressController)
            } else {
                PlayerLyricsLinesPreview(current.lines.map { it.text })
            }
        }
        is LyricsUiState.Plain -> PlayerLyricsLinesPreview(current.text.lines())
    }
}

@Composable
private fun PlayerLyricsGlassMessage(message: String) {
    Text(
        message,
        color = Color.White.copy(alpha = 0.88f),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Black,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlayerLyricsPreviewContent(
    state: LyricsUiState,
    progressController: PlaybackProgressController,
    progressEnabled: Boolean,
) {
    when (val current = state) {
        LyricsUiState.Loading -> PlayerSupplementalMessage(DeckIcon.Timer, "Looking for lyrics...")
        LyricsUiState.Missing -> PlayerSupplementalMessage(DeckIcon.EmptyBox, "Lyrics not found for this track.")
        LyricsUiState.BlockedOffline -> PlayerSupplementalMessage(DeckIcon.StreamOffline, "Cached lyrics not found. Offline mode is on.")
        LyricsUiState.BlockedDataSaver -> PlayerSupplementalMessage(DeckIcon.StreamOffline, "Cached lyrics not found. Data Saver is on.")
        is LyricsUiState.Error -> PlayerSupplementalMessage(DeckIcon.Info, current.message)
        is LyricsUiState.Synced -> {
            if (progressEnabled) {
                PlayerSyncedLyricsPreview(current.lines, progressController)
            } else {
                PlayerLyricsLinesPreview(current.lines.map { it.text })
            }
        }
        is LyricsUiState.Plain -> PlayerLyricsLinesPreview(current.text.lines())
    }
}

@Composable
private fun PlayerSyncedLyricsPreview(lines: List<SyncedLyricLine>, progressController: PlaybackProgressController) {
    if (lines.isEmpty()) {
        PlayerSupplementalMessage(DeckIcon.EmptyBox, "Lyrics not found for this track.")
        return
    }
    val positionMillis by progressController.positionMillis.collectAsState()
    val activeIndex = lines.indexOfLast { positionMillis >= it.startMillis }.coerceAtLeast(0)
    val start = (activeIndex - 1).coerceAtLeast(0)
    val visibleLines = lines.drop(start).take(5)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleLines.forEachIndexed { index, line ->
            val active = start + index == activeIndex
            Text(
                line.text,
                color = if (active) Color.White else Color.White.copy(alpha = 0.62f),
                fontSize = if (active) 14.sp else 12.sp,
                lineHeight = if (active) 18.sp else 16.sp,
                fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlayerLyricsLinesPreview(lines: List<String>) {
    val cleanLines = remember(lines) {
        lines.map { it.trim() }.filter { it.isNotBlank() }.take(6)
    }
    if (cleanLines.isEmpty()) {
        PlayerSupplementalMessage(DeckIcon.EmptyBox, "Lyrics not found for this track.")
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cleanLines.forEach { line ->
            Text(
                line,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlayerMoreFromArtistCard(
    snapshot: ArtistWorksSnapshot,
    fetchState: ArtistContinuationFetchState,
    sourceMode: ArtistWorksSourceMode,
    fetchPolicy: ArtistWorksFetchPolicy,
    onSource: (YouTubeSource, List<YouTubeSource>) -> Unit,
    onLoadOfficialResults: (ArtistCandidate, Boolean) -> Unit,
) {
    val primaryArtist = snapshot.artist
    val identityLabel = snapshot.confidence.label
    val localOnlineLookup = sourceMode == ArtistWorksSourceMode.LocalFile &&
        fetchPolicy == ArtistWorksFetchPolicy.UserConfirmationRequired
    val canLoadOfficial = when (fetchPolicy) {
        ArtistWorksFetchPolicy.UserTriggeredOnlineAllowed -> primaryArtist.canFetchOfficialContinuation
        ArtistWorksFetchPolicy.UserConfirmationRequired -> primaryArtist.normalizedName.isNotBlank()
        else -> false
    }
    var confirmOnlineLookup by remember(primaryArtist.normalizedName, fetchPolicy) { mutableStateOf(false) }
    PlayerArtistContinuationCard(
        title = "More from this artist",
        icon = DeckIcon.MusicList,
        items = snapshot.recentTracks,
        emptyMessage = when {
            fetchState.loading -> "Loading artist snapshot..."
            fetchState.loaded -> "No recent official artist results found yet."
            fetchPolicy == ArtistWorksFetchPolicy.OfflineOnly && sourceMode == ArtistWorksSourceMode.PremiumDeckOffline -> "Offline Deck is on. Artist works stays saved/offline only."
            fetchPolicy == ArtistWorksFetchPolicy.OfflineOnly && sourceMode == ArtistWorksSourceMode.LocalFile -> "Online artist discovery for local tracks is off for this network."
            fetchPolicy == ArtistWorksFetchPolicy.OfflineOnly && sourceMode == ArtistWorksSourceMode.PulseRadio -> "PulseRadio playback does not load online artist catalogs."
            fetchPolicy == ArtistWorksFetchPolicy.AutoShowCachedOnly -> "Showing cached artist works only for this source."
            fetchPolicy == ArtistWorksFetchPolicy.BlockedBetaLocked -> "Artist works lookup is not available in this beta state."
            localOnlineLookup -> "Local file playback. Online artist works are optional and only load if you choose."
            canLoadOfficial -> "Artist identity: $identityLabel. Tap Load artist snapshot."
            else -> "Artist identity needs a stronger source before loading official results."
        },
        statusMessage = fetchState.message,
        actionLabel = when {
            !canLoadOfficial -> null
            fetchState.loading -> "Loading"
            localOnlineLookup && fetchState.loaded -> "Refresh online artist works"
            localOnlineLookup -> "Search online artist works"
            fetchState.loaded -> "Refresh artist snapshot"
            else -> "Load artist snapshot"
        },
        actionIcon = if (fetchState.loading) DeckIcon.Timer else DeckIcon.Search,
        actionEnabled = canLoadOfficial && !fetchState.loading,
        onAction = {
            if (localOnlineLookup) {
                confirmOnlineLookup = true
            } else {
                onLoadOfficialResults(primaryArtist, false)
            }
        },
        onSource = onSource,
    )
    if (confirmOnlineLookup) {
        BasicInfoModal(
            title = "Search online artist works?",
            subtitle = primaryArtist.displayName,
            onDismiss = { confirmOnlineLookup = false },
        ) {
            Text(
                "This is a local file. PulseDeck will search online artist catalogs only for this request.",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SleepDialogButton(
                    label = "Cancel",
                    modifier = Modifier.weight(1f),
                    tone = Color.White.copy(alpha = 0.08f),
                    onClick = { confirmOnlineLookup = false },
                )
                SleepDialogButton(
                    label = "Search",
                    modifier = Modifier.weight(1f),
                    tone = Blue.copy(alpha = 0.52f),
                    onClick = {
                        confirmOnlineLookup = false
                        onLoadOfficialResults(primaryArtist, true)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerArtistContinuationCard(
    title: String,
    icon: DeckIcon,
    items: List<ArtistContinuationItem>,
    emptyMessage: String = "",
    statusMessage: String? = null,
    actionLabel: String? = null,
    actionIcon: DeckIcon = DeckIcon.Search,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
    onSource: (YouTubeSource, List<YouTubeSource>) -> Unit,
) {
    val queueSources = remember(items) { items.map { it.source }.distinctBy { it.streamDistinctKey() } }
    PlayerSupplementalCard(
        title = title,
        icon = icon,
        trailing = items.size.takeIf { it > 0 }?.let { "$it tracks" },
    ) {
        if (items.isEmpty()) {
            PlayerSupplementalMessage(DeckIcon.EmptyBox, emptyMessage.ifBlank { "No high-confidence sources found yet." })
        }
        statusMessage?.takeIf { it.isNotBlank() }?.let {
            if (items.isEmpty()) Spacer(Modifier.height(7.dp))
            PlayerSupplementalMessage(DeckIcon.Info, it)
        }
        if (actionLabel != null && onAction != null) {
            if (items.isEmpty() || !statusMessage.isNullOrBlank()) Spacer(Modifier.height(7.dp))
            PlayerSupplementalActionRow(
                icon = actionIcon,
                label = actionLabel,
                enabled = actionEnabled,
                onClick = onAction,
            )
        }
        if (items.isNotEmpty()) {
            if (actionLabel != null && onAction != null || !statusMessage.isNullOrBlank()) Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items.forEach { item ->
                    PlayerArtistSourceRow(
                        item = item,
                        onSource = { onSource(item.source, queueSources) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerLatestProjectCard(
    release: ArtistReleaseGroup,
    onSource: (YouTubeSource, List<YouTubeSource>) -> Unit,
    onOpenRelease: () -> Unit,
) {
    val queueSources = remember(release.items) { release.items.map { it.source }.distinctBy { it.streamDistinctKey() } }
    PlayerSupplementalCard(
        title = "Latest project / release",
        icon = DeckIcon.Disc,
        trailing = release.releaseYearHint.takeIf { it > 0 }?.toString(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YouTubeThumbnailBox(release.coverUrl, Modifier.size(56.dp), DeckIcon.Disc)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(release.title, color = Color.White, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        release.releaseType.label,
                        release.trackCountHint.takeIf { it > 0 }?.let { "$it tracks" },
                        release.artist,
                        release.confidence.label,
                    )
                        .filter { it.isNotBlank() }
                        .joinToString("  |  "),
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            release.items.take(3).forEach { item ->
                PlayerArtistSourceRow(
                    item = item,
                    onSource = { onSource(item.source, queueSources) },
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        PlayerSupplementalActionRow(
            icon = DeckIcon.MusicList,
            label = "Open Tracklist",
            enabled = release.id.isNotBlank(),
            onClick = onOpenRelease,
        )
    }
}

@Composable
private fun PlayerArtistSourceRow(item: ArtistContinuationItem, onSource: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val source = item.source
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(17.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onSource() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(38.dp), DeckIcon.StreamSource)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.reason, color = Color.White.copy(alpha = 0.54f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(source.durationMillis.takeIf { it > 0L }?.let(::formatDuration) ?: source.quality.label, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun PlayerDiscographyCard(
    snapshot: ArtistWorksSnapshot,
    fetchState: ArtistContinuationFetchState,
    sourceMode: ArtistWorksSourceMode,
    fetchPolicy: ArtistWorksFetchPolicy,
    onOpenRelease: (ArtistReleaseGroup) -> Unit,
    onOpenDiscography: () -> Unit,
    onLoadOfficialResults: (ArtistCandidate, Boolean) -> Unit,
) {
    val releases = remember(snapshot.discographyReleases) {
        snapshot.discographyReleases
            .filter { it.title.isNotBlank() }
            .distinctBy { it.id.ifBlank { "${it.releaseType.name}|${it.title}|${it.releaseYearHint}".normalizedSearchText() } }
            .take(24)
    }
    val carouselItems = remember(releases, snapshot.source, snapshot.fromCache) {
        releases.map { it.toDiscographyCarouselItem(snapshot) }
    }
    val releaseCount = releases.size
    val primaryArtist = snapshot.artist
    val localOnlineLookup = sourceMode == ArtistWorksSourceMode.LocalFile &&
        fetchPolicy == ArtistWorksFetchPolicy.UserConfirmationRequired
    val canLoadOfficial = when (fetchPolicy) {
        ArtistWorksFetchPolicy.UserTriggeredOnlineAllowed -> primaryArtist.canFetchOfficialContinuation
        ArtistWorksFetchPolicy.UserConfirmationRequired -> primaryArtist.normalizedName.isNotBlank()
        else -> false
    }
    var confirmOnlineLookup by remember(primaryArtist.normalizedName, fetchPolicy) { mutableStateOf(false) }
    PlayerSupplementalCard(
        title = "Discography",
        icon = DeckIcon.LibraryStack,
        trailing = releaseCount.takeIf { it > 0 }?.let { if (it == 1) "1 release" else "$it releases" },
    ) {
        if (releases.isEmpty()) {
            PlayerSupplementalMessage(
                DeckIcon.EmptyBox,
                if (fetchState.loading) {
                    "Loading artist releases..."
                } else if (snapshot.loadedAtEpochMs > 0L) {
                    "No albums or projects found for this artist yet. Try refreshing the artist snapshot."
                } else {
                    "Load the artist snapshot to show albums and projects by this artist."
                },
            )
            if (canLoadOfficial) {
                Spacer(Modifier.height(7.dp))
                PlayerSupplementalActionRow(
                    icon = if (fetchState.loading) DeckIcon.Timer else DeckIcon.Search,
                    label = when {
                        fetchState.loading -> "Loading"
                        localOnlineLookup -> "Search online releases"
                        else -> "Load artist releases"
                    },
                    enabled = !fetchState.loading,
                    onClick = {
                        if (localOnlineLookup) {
                            confirmOnlineLookup = true
                        } else {
                            onLoadOfficialResults(primaryArtist, false)
                        }
                    },
                )
            }
        } else {
            DiscographyCoverflowCarousel(
                items = carouselItems,
                releases = releases,
                onOpenRelease = onOpenRelease,
                onOpenDiscography = onOpenDiscography,
            )
        }
    }
    if (confirmOnlineLookup) {
        BasicInfoModal(
            title = "Search online albums?",
            subtitle = primaryArtist.displayName,
            onDismiss = { confirmOnlineLookup = false },
        ) {
            Text(
                "This is a local file. PulseDeck will search online artist catalogs only for this request.",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SleepDialogButton(
                    label = "Cancel",
                    modifier = Modifier.weight(1f),
                    tone = Color.White.copy(alpha = 0.08f),
                    onClick = { confirmOnlineLookup = false },
                )
                SleepDialogButton(
                    label = "Search",
                    modifier = Modifier.weight(1f),
                    tone = Blue.copy(alpha = 0.52f),
                    onClick = {
                        confirmOnlineLookup = false
                        onLoadOfficialResults(primaryArtist, true)
                    },
                )
            }
        }
    }
}

@Composable
private fun DiscographyCoverflowCarousel(
    items: List<DiscographyCarouselItem>,
    releases: List<ArtistReleaseGroup>,
    onOpenRelease: (ArtistReleaseGroup) -> Unit,
    onOpenDiscography: () -> Unit,
) {
    if (items.isEmpty()) return
    val motion = LocalPulseMotionSpec.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { items.size })
    val activeIndex = pagerState.currentPage.coerceIn(0, items.lastIndex)
    val activeItem = items[activeIndex]
    val activeRelease = releases.getOrNull(activeIndex)
    val activeTint = remember(activeItem.releaseId, activeItem.title) { activeItem.discographyTint() }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .padding(top = 14.dp, bottom = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardSize = when {
                    maxWidth < 330.dp -> 116.dp
                    maxWidth < 390.dp -> 132.dp
                    else -> 146.dp
                }
                val sidePadding = if (maxWidth > cardSize) (maxWidth - cardSize) / 2 else 0.dp
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(cardSize),
                    pageSpacing = 10.dp,
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardSize + 20.dp),
                ) { page ->
                    val item = items[page]
                    val release = releases.getOrNull(page)
                    val pageOffset = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)
                    val focused = pageOffset < 0.42f
                    DiscographyCoverflowCard(
                        item = item,
                        pageOffset = pageOffset,
                        focused = focused,
                        cardSize = cardSize,
                        motionDisabled = motion.disabled,
                        modifier = Modifier.zIndex(3f - pageOffset),
                        onClick = {
                            if (focused && release != null) {
                                onOpenRelease(release)
                            } else {
                                coroutineScope.launch {
                                    if (motion.disabled) {
                                        pagerState.scrollToPage(page)
                                    } else {
                                        pagerState.animateScrollToPage(page)
                                    }
                                }
                            }
                        },
                    )
                }
            }
            DiscographyTitlePlate(
                item = activeItem,
                releaseCount = items.size,
                onOpenRelease = activeRelease?.let { { onOpenRelease(it) } },
                onOpenDiscography = onOpenDiscography,
            )
        }
    }
}

@Composable
private fun DiscographyCoverflowCard(
    item: DiscographyCarouselItem,
    pageOffset: Float,
    focused: Boolean,
    cardSize: Dp,
    motionDisabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(item.releaseId, item.title) { MutableInteractionSource() }
    val density = LocalDensity.current
    val visualDistance = pageOffset.coerceIn(0f, 1f)
    val scale = if (motionDisabled) {
        if (focused) 1f else 0.88f
    } else {
        0.84f + (1f - visualDistance) * 0.16f
    }
    val alphaValue = if (focused) 1f else 0.58f + (1f - visualDistance) * 0.16f
    val rotation = if (motionDisabled) 0f else (pageOffset.coerceIn(-1f, 1f) * 8f)
    Box(
        modifier
            .size(cardSize)
            .graphicsLayer {
                alpha = alphaValue.coerceIn(0.52f, 1f)
                scaleX = scale
                scaleY = scale
                rotationY = rotation
                cameraDistance = 18f * density.density
                shadowElevation = if (focused) 18f else 5f
                shape = RoundedCornerShape(24.dp)
                clip = false
            }
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (focused) 0.18f else 0.11f),
                            item.discographyTint().copy(alpha = if (focused) 0.22f else 0.12f),
                            Color.Black.copy(alpha = 0.28f),
                        ),
                    ),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (focused) 0.26f else 0.13f),
                    RoundedCornerShape(24.dp),
                )
                .padding(7.dp),
        ) {
            YouTubeThumbnailBox(
                item.thumbnailUrl,
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp)),
                DeckIcon.Disc,
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (focused) 0.58f else 0.42f),
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun DiscographyTitlePlate(
    item: DiscographyCarouselItem,
    releaseCount: Int,
    onOpenRelease: (() -> Unit)?,
    onOpenDiscography: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                item.title,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                item.artist,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier.padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseInfoPill(item.releaseType.label, style = PulseInfoPillStyle.Info)
                item.year?.let { PulseInfoPill(it.toString(), style = PulseInfoPillStyle.Neutral) }
                PulseInfoPill(item.sourceLabel, style = if (item.confidence == ArtistSourceConfidence.Unverified) PulseInfoPillStyle.Warning else PulseInfoPillStyle.Success)
                if (item.fromCache) PulseInfoPill("Cached", style = PulseInfoPillStyle.Neutral)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PlayerSupplementalActionRow(
                icon = DeckIcon.MusicList,
                label = "Open in Album Builder",
                enabled = onOpenRelease != null,
                onClick = { onOpenRelease?.invoke() },
            )
            if (releaseCount > 1) {
                PlayerSupplementalActionRow(
                    icon = DeckIcon.Forward,
                    label = "Open Discography",
                    enabled = true,
                    onClick = onOpenDiscography,
                )
            }
        }
    }
}

@Composable
private fun PlayerReleaseRow(release: ArtistReleaseGroup, onOpen: () -> Unit) {
    val interactionSource = remember(release.id, release.title) { MutableInteractionSource() }
    val subtitle = when {
        release.items.isNotEmpty() -> "${release.releaseType.label}  |  ${release.items.size} preview tracks"
        release.trackCountHint > 0 -> "${release.releaseType.label}  |  ${release.trackCountHint} tracks  |  opens on demand"
        else -> "${release.releaseType.label}  |  tracklist opens on demand"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnailBox(release.coverUrl, Modifier.size(42.dp), DeckIcon.Disc)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(release.title, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PulseIcon(DeckIcon.MusicList, Color.White.copy(alpha = 0.58f), Modifier.size(17.dp))
    }
}

@Composable
private fun PlayerAlsoSavedCard(
    artistContext: PlayerArtistContext,
    snapshot: ArtistWorksSnapshot,
    onSource: (YouTubeSource, List<YouTubeSource>) -> Unit,
    onTrack: (Track) -> Unit,
) {
    val savedItems = snapshot.savedInPulseDeck
    val savedQueue = remember(savedItems) { savedItems.map { it.source }.distinctBy { it.streamDistinctKey() } }
    var expanded by remember(artistContext.artist, savedItems.size, artistContext.localTracks.size) { mutableStateOf(false) }
    val title = if (savedItems.isNotEmpty()) "Also saved in PulseDeck" else "On this device"
    val trailingParts = buildList {
        if (savedItems.isNotEmpty()) add("${savedItems.size} saved")
        if (artistContext.localTracks.isNotEmpty()) add("${artistContext.localTracks.size} local")
    }.joinToString("  |  ")
    PlayerSupplementalCard(
        title = title,
        icon = DeckIcon.LibraryStack,
        trailing = trailingParts.ifBlank { null },
    ) {
        val totalCount = savedItems.size + artistContext.localTracks.size
        PlayerSupplementalMessage(
            DeckIcon.LibraryStack,
            if (totalCount == 1) {
                "1 saved or local match for this artist."
            } else {
                "$totalCount saved or local matches for this artist."
            },
        )
        Spacer(Modifier.height(7.dp))
        PlayerSupplementalActionRow(
            icon = if (expanded) DeckIcon.Minus else DeckIcon.Plus,
            label = if (expanded) "Hide saved/device matches" else "Show saved/device matches",
            enabled = totalCount > 0,
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                savedItems.forEach { item ->
                    PlayerArtistSourceRow(
                        item = item,
                        onSource = { onSource(item.source, savedQueue.ifEmpty { listOf(item.source) }) },
                    )
                }
                artistContext.localTracks.forEach { track ->
                    PlayerDeviceTrackRow(track = track, onTrack = onTrack)
                }
            }
        }
    }
}

@Composable
private fun PlayerOpenDiscographyCard(
    snapshot: ArtistWorksSnapshot,
    onOpen: () -> Unit,
) {
    val itemCount = remember(snapshot) { snapshot.discographyItems().size }
    val enabled = itemCount > 0
    PlayerSupplementalCard(
        title = "Open Discography",
        icon = DeckIcon.LibraryStack,
        trailing = itemCount.takeIf { it > 0 }?.let { "$it items" },
    ) {
        PlayerSupplementalMessage(
            icon = if (enabled) DeckIcon.Disc else DeckIcon.EmptyBox,
            message = if (enabled) {
                "Open the loaded artist snapshot as a collection."
            } else {
                "Load the artist snapshot first to open discography results."
            },
        )
        Spacer(Modifier.height(7.dp))
        PlayerSupplementalActionRow(
            icon = DeckIcon.Forward,
            label = "Open Discography",
            enabled = enabled,
            onClick = onOpen,
        )
    }
}

@Composable
private fun PlayerDeviceTrackRow(track: Track, onTrack: (Track) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(17.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onTrack(track) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(track.album, Modifier.size(38.dp), 10.dp, useCase = ArtworkUseCase.SongListThumbnail)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.album.title, color = Color.White.copy(alpha = 0.54f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(track.duration, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun PlayerAboutArtistCard(artistContext: PlayerArtistContext) {
    var expanded by remember(artistContext.artist) { mutableStateOf(false) }
    val summary = remember(artistContext) {
        buildList {
            artistContext.primaryArtist?.confidence?.label?.let { add("Identity: $it") }
            add("${artistContext.savedPremiumDeckSources.size} saved")
            if (artistContext.collaborationSources.isNotEmpty()) add("${artistContext.collaborationSources.size} collaborations")
            if (artistContext.localTracks.isNotEmpty()) add("${artistContext.localTracks.size} on device")
        }.joinToString("  |  ").ifBlank { "Artist identity unavailable" }
    }
    PlayerSupplementalCard(
        title = "About artist",
        icon = DeckIcon.Person,
        trailing = if (expanded) "Hide" else "Show",
        onClick = { expanded = !expanded },
    ) {
        Text(
            artistContext.artist,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            summary,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        )
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackMetaChip("Identity", artistContext.primaryArtist?.confidence?.label ?: "Unknown", Modifier.weight(1f))
                TrackMetaChip("Saved", artistContext.savedPremiumDeckSources.size.toString(), Modifier.weight(1f))
            }
            if (artistContext.localTracks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackMetaChip("On Device", artistContext.localTracks.size.toString(), Modifier.weight(1f))
                    TrackMetaChip("Albums", artistContext.albumCount.toString(), Modifier.weight(1f))
                }
            }
            if (artistContext.primaryArtist?.sourceChannelTitle?.isNotBlank() == true) {
                Spacer(Modifier.height(8.dp))
                TrackMetaChip("Source", artistContext.primaryArtist.sourceChannelTitle.orEmpty(), Modifier.fillMaxWidth())
            }
            if (artistContext.genres.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TrackMetaChip("Genres", artistContext.genres.joinToString(", "), Modifier.fillMaxWidth())
            }
        }
    }
}

private fun buildArtistWorksSnapshot(
    artistContext: PlayerArtistContext,
    fetchState: ArtistContinuationFetchState,
): ArtistWorksSnapshot {
    val artist = artistContext.primaryArtist ?: ArtistCandidate(
        displayName = artistContext.artist,
        normalizedName = artistContext.artist.normalizedSearchText(),
        role = ArtistRole.Unknown,
        evidence = emptySet(),
        sourceChannelId = null,
        sourceChannelTitle = null,
        confidence = ArtistSourceConfidence.None,
    )
    val officialItems = fetchState.sources
        .map { it.toArtistContinuationItem(ArtistContinuationSection.Recent) }
        .distinctBy { it.source.streamDistinctKey() }
    val recentTracks = officialItems
        .sortedWith(
            compareBy<ArtistContinuationItem> { it.rank.takeIf { rank -> rank > 0 } ?: Int.MAX_VALUE }
                .thenByDescending { it.source.addedMillis }
                .thenBy { it.source.title.normalizedSearchText() }
        )
        .take(3)
        .map { it.copy(section = ArtistContinuationSection.Recent) }
    val catalogReleases = fetchState.discographyReleases
        .distinctBy { it.id.ifBlank { "${it.releaseType.name}|${it.title}".normalizedSearchText() } }
        .sortedWith(
            compareByDescending<ArtistReleaseGroup> { it.releaseType.releaseSortWeight }
                .thenByDescending { it.releaseYearHint }
                .thenByDescending { it.items.size }
                .thenByDescending { it.confidence.discographyRank }
                .thenBy { it.title.normalizedSearchText() }
        )
    val latestProject = catalogReleases.firstOrNull { it.releaseType != ArtistReleaseType.Single }
        ?: catalogReleases.firstOrNull()
        ?: buildLatestArtistProject(artist, officialItems)
    val latestKeys = latestProject?.items.orEmpty().map { it.source.streamDistinctKey() }.toSet()
    val recentLeadKeys = recentTracks.take(2).map { it.source.streamDistinctKey() }.toSet()
    val topTracks = officialItems
        .filterNot { it.source.streamDistinctKey() in latestKeys }
        .filterNot { it.source.streamDistinctKey() in recentLeadKeys && officialItems.size > 4 }
        .sortedWith(
            compareByDescending<ArtistContinuationItem> { it.artist.confidence.snapshotRank }
                .thenByDescending { it.resultScore }
                .thenByDescending { it.views }
                .thenBy { it.rank.takeIf { rank -> rank > 0 } ?: Int.MAX_VALUE }
                .thenBy { it.source.title.normalizedSearchText() }
        )
        .take(3)
        .map { it.copy(section = ArtistContinuationSection.TopResult) }
    val collaborationItems = (
        artistContext.collaborationSources.map { it.toArtistContinuationItem(ArtistContinuationSection.Collaboration) } +
            officialItems
                .filter { it.source.playerLooksLikeCollaborationResult(artist.normalizedName) }
                .map { it.copy(section = ArtistContinuationSection.Collaboration, reason = "Collaboration / featured result") }
        )
        .distinctBy { it.source.streamDistinctKey() }
        .take(4)
    val savedItems = artistContext.savedPremiumDeckSources
        .map { it.toArtistContinuationItem(ArtistContinuationSection.Saved) }
        .distinctBy { it.source.streamDistinctKey() }
        .take(8)
    val confidence = listOf(
        artist.confidence,
        latestProject?.confidence,
        catalogReleases.maxByOrNull { it.confidence.discographyRank }?.confidence,
        officialItems.maxByOrNull { it.artist.confidence.snapshotRank }?.artist?.confidence,
    )
        .filterNotNull()
        .maxByOrNull { it.snapshotRank }
        ?: ArtistSourceConfidence.None
    val resolution = artist.takeIf { it.confidence.playerArtistContinuationAcceptedForSnapshot() }?.let {
        OfficialArtistResolution(
            displayName = it.displayName,
            sourceChannelId = it.sourceChannelId,
            sourceChannelTitle = it.sourceChannelTitle,
            confidence = it.confidence,
            evidence = it.evidence,
        )
    }
    return ArtistWorksSnapshot(
        artist = artist.copy(confidence = confidence),
        source = resolution,
        recentTracks = recentTracks,
        topTracks = topTracks,
        latestProject = latestProject,
        discographyReleases = catalogReleases,
        collaborations = collaborationItems,
        savedInPulseDeck = savedItems,
        loadedAtEpochMs = fetchState.loadedAtEpochMs.takeIf { it > 0L }
            ?: if (fetchState.loaded) System.currentTimeMillis() else 0L,
        confidence = confidence,
        fromCache = fetchState.fromCache,
    )
}

private fun buildLatestArtistProject(
    artist: ArtistCandidate,
    officialItems: List<ArtistContinuationItem>,
): ArtistReleaseGroup? {
    val groups = officialItems
        .mapNotNull { item ->
            val inferredTitle = item.source.artistReleaseTitleHint(artist.displayName) ?: return@mapNotNull null
            inferredTitle to item
        }
        .groupBy { (title, _) -> title.normalizedSearchText() }
        .values
        .mapNotNull { group ->
            val title = group.firstOrNull()?.first.orEmpty()
            val items = group.map { it.second }
            if (title.isBlank()) return@mapNotNull null
            val releaseType = inferArtistReleaseType(title, items)
            val confident = releaseType == ArtistReleaseType.Album ||
                releaseType == ArtistReleaseType.Ep ||
                items.size >= 2 ||
                items.any { item -> item.source.albumTrackTotalHint > 1 || item.source.albumTrackNumberHint > 0 && item.source.albumYearHint > 0 }
            if (!confident) return@mapNotNull null
            val ordered = items
                .sortedWith(
                    compareBy<ArtistContinuationItem> { it.source.albumTrackNumberHint.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
                        .thenBy { it.rank.takeIf { rank -> rank > 0 } ?: Int.MAX_VALUE }
                        .thenBy { it.source.title.normalizedSearchText() }
                )
                .map { it.copy(section = ArtistContinuationSection.LatestProject) }
            val confidence = ordered
                .maxByOrNull { it.artist.confidence.snapshotRank }
                ?.artist
                ?.confidence
                ?: artist.confidence
            ArtistReleaseGroup(
                title = title,
                artist = artist.displayName,
                items = ordered,
                releaseType = releaseType,
                releaseYearHint = ordered.maxOfOrNull { it.source.albumYearHint } ?: 0,
                trackCountHint = ordered.maxOfOrNull { it.source.albumTrackTotalHint } ?: ordered.size,
                coverUrl = ordered.firstNotNullOfOrNull { it.source.bestThumbnailUrl() ?: it.source.thumbnailUrl },
                confidence = confidence,
            )
        }
    return groups
        .sortedWith(
            compareByDescending<ArtistReleaseGroup> { it.releaseYearHint }
                .thenByDescending { it.items.size }
                .thenByDescending { it.confidence.snapshotRank }
                .thenBy { it.title.normalizedSearchText() }
        )
        .firstOrNull()
}

private fun YouTubeSource.artistReleaseTitleHint(artistName: String): String? {
    val cleanAlbumHint = albumTitleHint.cleanStrictStreamAlbumTitle().takeIf { it.isReliableStrictAlbumTitle() }
    if (cleanAlbumHint != null) return cleanAlbumHint
    return strictStreamAlbumTitle(artistName.cleanStreamArtistName())
}

private fun inferArtistReleaseType(title: String, items: List<ArtistContinuationItem>): ArtistReleaseType {
    val text = (listOf(title) + items.flatMap { item ->
        listOf(item.source.title, item.source.albumTitleHint)
    }).joinToString(" ").normalizedSearchText()
    val maxTotal = items.maxOfOrNull { it.source.albumTrackTotalHint } ?: 0
    return when {
        Regex("""\b(album|lp)\b""").containsMatchIn(text) || maxTotal >= 7 || items.size >= 7 -> ArtistReleaseType.Album
        Regex("""\b(ep)\b""").containsMatchIn(text) || maxTotal in 3..6 || items.size in 3..6 -> ArtistReleaseType.Ep
        Regex("""\b(single)\b""").containsMatchIn(text) || maxTotal == 1 || items.size == 1 -> ArtistReleaseType.Single
        items.size >= 2 -> ArtistReleaseType.Project
        else -> ArtistReleaseType.Unknown
    }
}

internal fun ArtistContinuationSource.toArtistContinuationItem(section: ArtistContinuationSection): ArtistContinuationItem =
    ArtistContinuationItem(
        source = source,
        artist = artist,
        reason = reason,
        section = section,
        resultScore = resultScore,
        views = views,
        rank = rank,
    )

internal fun ArtistContinuationItem.toArtistContinuationSource(): ArtistContinuationSource =
    ArtistContinuationSource(
        source = source,
        artist = artist,
        reason = reason,
        resultScore = resultScore,
        views = views,
        rank = rank,
    )

internal fun ArtistWorksSnapshot.discographyItems(): List<ArtistContinuationItem> =
    discographyReleases
        .flatMap { it.items }
        .distinctBy { it.source.streamDistinctKey() }

private fun ArtistReleaseGroup.toDiscographyCarouselItem(snapshot: ArtistWorksSnapshot): DiscographyCarouselItem =
    DiscographyCarouselItem(
        releaseId = id.ifBlank { "${artist.ifBlank { snapshot.artist.displayName }}|$title|${releaseType.name}|$releaseYearHint".normalizedSearchText() },
        providerId = snapshot.source?.sourceChannelId?.takeIf { it.isNotBlank() } ?: "artist_catalog",
        title = title,
        artist = artist.ifBlank { snapshot.artist.displayName },
        releaseType = releaseType,
        year = releaseYearHint.takeIf { it > 0 },
        thumbnailUrl = coverUrl ?: items.firstNotNullOfOrNull { it.source.bestThumbnailUrl() ?: it.source.thumbnailUrl },
        localArtworkUri = null,
        previewTracks = tracklistPreview.take(3),
        confidence = confidence,
        sourceLabel = discographySourceLabel(snapshot),
        fromCache = snapshot.fromCache,
    )

private fun ArtistReleaseGroup.discographySourceLabel(snapshot: ArtistWorksSnapshot): String =
    when {
        confidence == ArtistSourceConfidence.VerifiedOfficial -> "Official"
        confidence == ArtistSourceConfidence.HighConfidence -> "Catalog"
        confidence == ArtistSourceConfidence.TopicOrAutoGenerated -> "Topic source"
        snapshot.fromCache -> "Cached"
        confidence == ArtistSourceConfidence.Unverified -> "Catalog"
        else -> "Catalog"
    }

private fun DiscographyCarouselItem.discographyTint(): Color {
    val palette = listOf(
        Color(0xFF4A82FF),
        Color(0xFF7A5CFF),
        Color(0xFFFF7A4B),
        Color(0xFF32D979),
        Color(0xFFFF5F6E),
        Color(0xFF46C7FF),
    )
    val seed = (releaseId.ifBlank { "$artist|$title" }.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[seed]
}

private fun YouTubeSource.playerLooksLikeCollaborationResult(primaryArtistKey: String): Boolean {
    if (primaryArtistKey.isBlank()) return false
    val text = listOf(title, author, channelTitle)
        .joinToString(" ")
        .normalizedSearchText()
    val hasCollaborationMarker = Regex("""\b(feat\.?|ft\.?|featuring|with)\b""", RegexOption.IGNORE_CASE).containsMatchIn(title)
    return hasCollaborationMarker && text.contains(primaryArtistKey)
}

private val ArtistSourceConfidence.snapshotRank: Int
    get() = when (this) {
        ArtistSourceConfidence.VerifiedOfficial -> 4
        ArtistSourceConfidence.HighConfidence -> 3
        ArtistSourceConfidence.TopicOrAutoGenerated -> 2
        ArtistSourceConfidence.Unverified -> 1
        ArtistSourceConfidence.None -> 0
    }

private fun ArtistSourceConfidence.playerArtistContinuationAcceptedForSnapshot(): Boolean =
    this == ArtistSourceConfidence.VerifiedOfficial ||
        this == ArtistSourceConfidence.HighConfidence ||
        this == ArtistSourceConfidence.TopicOrAutoGenerated

internal val ArtistSourceConfidence.label: String
    get() = when (this) {
        ArtistSourceConfidence.VerifiedOfficial -> "Verified official"
        ArtistSourceConfidence.HighConfidence -> "High confidence"
        ArtistSourceConfidence.TopicOrAutoGenerated -> "Topic channel"
        ArtistSourceConfidence.Unverified -> "Unverified"
        ArtistSourceConfidence.None -> "No source"
    }

private val ArtistCandidate.canFetchOfficialContinuation: Boolean
    get() = confidence == ArtistSourceConfidence.VerifiedOfficial ||
        confidence == ArtistSourceConfidence.HighConfidence ||
        confidence == ArtistSourceConfidence.TopicOrAutoGenerated

@Composable
private fun PlayerSupplementalActionRow(
    icon: DeckIcon,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.42f
    val clickModifier = if (enabled) {
        Modifier
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer {
                shadowElevation = 0f
                shape = RoundedCornerShape(18.dp)
                clip = false
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.050f * alpha))
            .border(1.dp, Color.White.copy(alpha = 0.205f * alpha), RoundedCornerShape(18.dp))
            .then(clickModifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(25.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.035f * alpha))
                .border(1.dp, Color.White.copy(alpha = 0.145f * alpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(icon, Color.White.copy(alpha = 0.92f * alpha), Modifier.size(15.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.92f * alpha),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlayerSupplementalCard(
    title: String,
    icon: DeckIcon,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier = if (onClick != null) {
        Modifier
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.060f))
            .border(1.dp, Color.White.copy(alpha = 0.190f), shape)
            .then(clickableModifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun PlayerSupplementalMessage(icon: DeckIcon, message: String) {
    Text(
        message,
        color = Color.White.copy(alpha = 0.88f),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Black,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun playerLerpBounds(start: AlbumTileBounds, end: AlbumTileBounds, progress: Float): AlbumTileBounds {
    val t = progress.coerceIn(0f, 1f)
    return AlbumTileBounds(
        left = start.left + (end.left - start.left) * t,
        top = start.top + (end.top - start.top) * t,
        width = start.width + (end.width - start.width) * t,
        height = start.height + (end.height - start.height) * t,
    )
}

@Composable
private fun PlayerLaunchHandoffOverlay(
    track: Track,
    handoff: PlayerLaunchHandoff,
    rootOffset: Offset,
    progress: Float,
    heroBounds: AlbumTileBounds?,
    titleBounds: AlbumTileBounds?,
    artistBounds: AlbumTileBounds?,
    modifier: Modifier = Modifier,
) {
    val targetHero = heroBounds ?: return
    if (handoff.artBounds.width <= 0f || targetHero.width <= 0f) return
    val density = LocalDensity.current
    val eased = PulseMotion.Easing.Emphasized.transform(progress.coerceIn(0f, 1f))
    val textEased = PulseMotion.Easing.Standard.transform(((progress - 0.04f) / 0.90f).coerceIn(0f, 1f))
    val localSourceArt = handoff.artBounds.offsetBy(-rootOffset.x, -rootOffset.y)
    val localTargetHero = targetHero.offsetBy(-rootOffset.x, -rootOffset.y)
    val artBounds = playerLerpBounds(localSourceArt, localTargetHero, eased)
    val cornerPx = with(density) {
        val start = 16.dp.toPx()
        val end = 28.dp.toPx()
        start + (end - start) * eased
    }
    val sourceTextLeftFallback = handoff.artBounds.left + handoff.artBounds.width + with(density) { 14.dp.toPx() }
    val sourceTextWidthFallback = (targetHero.width - with(density) { 48.dp.toPx() }).coerceAtLeast(1f)
    val sourceTitle = (handoff.titleBounds ?: AlbumTileBounds(
        left = sourceTextLeftFallback,
        top = handoff.artBounds.top + with(density) { 6.dp.toPx() },
        width = sourceTextWidthFallback,
        height = with(density) { 20.dp.toPx() },
    )).offsetBy(-rootOffset.x, -rootOffset.y)
    val sourceArtist = (handoff.artistBounds ?: AlbumTileBounds(
        left = sourceTextLeftFallback,
        top = handoff.artBounds.top + with(density) { 29.dp.toPx() },
        width = sourceTextWidthFallback,
        height = with(density) { 16.dp.toPx() },
    )).offsetBy(-rootOffset.x, -rootOffset.y)
    val targetTitle = (titleBounds ?: AlbumTileBounds(
        left = targetHero.left + with(density) { 24.dp.toPx() },
        top = targetHero.top + targetHero.height + with(density) { 14.dp.toPx() },
        width = (targetHero.width - with(density) { 48.dp.toPx() }).coerceAtLeast(1f),
        height = with(density) { 24.dp.toPx() },
    )).offsetBy(-rootOffset.x, -rootOffset.y)
    val targetArtist = (artistBounds ?: AlbumTileBounds(
        left = targetTitle.left + rootOffset.x,
        top = targetTitle.top + rootOffset.y + with(density) { 23.dp.toPx() },
        width = targetTitle.width,
        height = with(density) { 18.dp.toPx() },
    )).offsetBy(-rootOffset.x, -rootOffset.y)
    val titleMotionBounds = playerLerpBounds(sourceTitle, targetTitle, textEased)
    val artistMotionBounds = playerLerpBounds(sourceArtist, targetArtist, textEased)

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset { IntOffset(artBounds.left.roundToInt(), artBounds.top.roundToInt()) }
                .size(
                    width = with(density) { artBounds.width.toDp() },
                    height = with(density) { artBounds.height.toDp() },
                )
                .graphicsLayer {
                    shadowElevation = 28f * eased
                    ambientShadowColor = Color.Black.copy(alpha = 0.58f)
                    spotShadowColor = Color.Black.copy(alpha = 0.74f)
                },
        ) {
            AlbumHeroArtwork(
                track.album,
                Modifier.fillMaxSize(),
                with(density) { cornerPx.toDp() },
            )
        }
        PlayerLaunchText(
            text = track.title,
            bounds = titleMotionBounds,
            progress = textEased,
            startFontSp = 17f,
            endFontSp = 19f,
            startLineHeightSp = 19f,
            endLineHeightSp = 21f,
            color = Color.White.copy(alpha = 0.92f),
        )
        PlayerLaunchText(
            text = track.artist,
            bounds = artistMotionBounds,
            progress = textEased,
            startFontSp = 13f,
            endFontSp = 13f,
            startLineHeightSp = 15f,
            endLineHeightSp = 15f,
            color = Color.White.copy(alpha = 0.70f),
        )
    }
}

private fun AlbumTileBounds.offsetBy(dx: Float, dy: Float): AlbumTileBounds =
    copy(left = left + dx, top = top + dy)

@Composable
private fun PlayerLaunchText(
    text: String,
    bounds: AlbumTileBounds,
    progress: Float,
    startFontSp: Float,
    endFontSp: Float,
    startLineHeightSp: Float,
    endLineHeightSp: Float,
    color: Color,
) {
    val density = LocalDensity.current
    val fontSp = startFontSp + (endFontSp - startFontSp) * progress.coerceIn(0f, 1f)
    val lineHeightSp = startLineHeightSp + (endLineHeightSp - startLineHeightSp) * progress.coerceIn(0f, 1f)
    Text(
        text,
        color = color,
        fontSize = fontSp.sp,
        lineHeight = lineHeightSp.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .width(with(density) { bounds.width.coerceAtLeast(1f).toDp() }),
    )
}

@Composable
private fun ExpandedQueueOverlay(
    visible: Boolean,
    currentTrack: Track,
    queueTracks: List<Track>,
    queueReasons: Map<String, PremiumDeckQueueReason>,
    canClear: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    undoItem: QueueUndoItem?,
    onUndoRemove: (QueueUndoItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalPulseMotionSpec.current
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(motion.duration(PulseMotion.Duration.PopupIn), easing = PulseMotion.Easing.Emphasized),
        label = "expandedQueueAlpha",
    )
    val dismissInteraction = remember { MutableInteractionSource() }
    val contentInteraction = remember { MutableInteractionSource() }
    BackHandler { onDismiss() }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f * alpha))
            .clickable(interactionSource = dismissInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val panelWidth = when {
            maxWidth < 420.dp -> maxWidth - 28.dp
            maxWidth < 720.dp -> maxWidth - 48.dp
            else -> 560.dp
        }
        val panelHeight = when {
            maxHeight < 620.dp -> maxHeight * 0.80f
            maxHeight < 840.dp -> maxHeight * 0.72f
            else -> 620.dp
        }
        UpNextQueuePanel(
            currentTrack = currentTrack,
            queueTracks = queueTracks,
            queueReasons = queueReasons,
            canClear = canClear,
            reveal = alpha,
            previewLimit = QueueExpandedPreviewLimit,
            onMove = onMove,
            onRemove = onRemove,
            onClear = onClear,
            onExpand = null,
            showDragHint = false,
            undoItem = undoItem,
            onUndoRemove = onUndoRemove,
            onDone = onDismiss,
            modifier = Modifier
                .width(panelWidth)
                .height(panelHeight)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = 0.92f + 0.08f * alpha
                    scaleY = 0.92f + 0.08f * alpha
                    translationY = 36f * (1f - alpha)
                }
                .clickable(interactionSource = contentInteraction, indication = null, onClick = {}),
        )
    }
}

@Composable
private fun UpNextQueuePanel(
    currentTrack: Track,
    queueTracks: List<Track>,
    queueReasons: Map<String, PremiumDeckQueueReason>,
    canClear: Boolean,
    reveal: Float,
    previewLimit: Int,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onExpand: (() -> Unit)?,
    showDragHint: Boolean,
    undoItem: QueueUndoItem?,
    onUndoRemove: (QueueUndoItem) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val rowStepPx = with(density) { 67.dp.toPx() }
    val removeThresholdPx = with(density) { 112.dp.toPx() }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var removeArmed by remember { mutableStateOf(false) }
    val previewCount = previewLimit.coerceAtLeast(0)
    val displayedQueueTracks = remember(queueTracks, previewCount) { queueTracks.take(previewCount) }
    val hiddenQueueCount = (queueTracks.size - displayedQueueTracks.size).coerceAtLeast(0)
    val totalQueueCount = if (queueTracks.isEmpty()) 1 else queueTracks.size + 1
    fun clearDragState() {
        draggedKey = null
        dragStartIndex = -1
        dragTargetIndex = -1
        dragOffsetX = 0f
        dragOffsetY = 0f
        removeArmed = false
    }
    fun startQueueDrag(key: String, index: Int) {
        draggedKey = key
        dragStartIndex = index
        dragTargetIndex = index
        dragOffsetX = 0f
        dragOffsetY = 0f
        removeArmed = false
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun updateQueueDrag(amount: Offset) {
        if (draggedKey == null || dragStartIndex !in displayedQueueTracks.indices) return
        val previousTarget = dragTargetIndex
        dragOffsetX = (dragOffsetX + amount.x).coerceIn(-removeThresholdPx * 1.55f, 42f)
        dragOffsetY += amount.y
        dragTargetIndex = (dragStartIndex + (dragOffsetY / rowStepPx).roundToInt())
            .coerceIn(0, displayedQueueTracks.lastIndex.coerceAtLeast(0))
        if (dragTargetIndex != previousTarget) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val nextRemoveArmed = dragOffsetX < -removeThresholdPx
        if (nextRemoveArmed && !removeArmed) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        removeArmed = nextRemoveArmed
    }
    fun finishQueueDrag() {
        val from = dragStartIndex
        val to = dragTargetIndex
        val remove = dragOffsetX < -removeThresholdPx
        if (remove) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        clearDragState()
        when {
            remove && from in displayedQueueTracks.indices -> onRemove(from)
            from in displayedQueueTracks.indices && to in displayedQueueTracks.indices && from != to -> onMove(from, to)
        }
    }
    val headerInteraction = remember { MutableInteractionSource() }
    val headerModifier = onExpand?.let { expand ->
        Modifier.clickable(
            interactionSource = headerInteraction,
            indication = null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                expand()
            },
        )
    } ?: Modifier
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .graphicsLayer {
                alpha = reveal.coerceIn(0f, 1f)
                translationY = -86f * (1f - reveal)
                scaleX = 0.97f + reveal * 0.03f
                scaleY = 0.97f + reveal * 0.03f
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        currentTrack.album.tint.copy(alpha = 0.94f),
                        lerp(currentTrack.album.tint, Color.Black, 0.42f).copy(alpha = 0.96f),
                        Color.Black.copy(alpha = 0.98f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().then(headerModifier), verticalAlignment = Alignment.CenterVertically) {
            Art(currentTrack.album, Modifier.size(42.dp), 11.dp, useCase = ArtworkUseCase.SongListThumbnail)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("UP NEXT", color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
                Text(
                    if (queueTracks.isEmpty()) "Queue ends after this track" else "$totalQueueCount tracks in queue",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canClear) {
                QueueIconButton(DeckIcon.Trash, "Clear queue", onClick = onClear)
                Spacer(Modifier.width(6.dp))
            }
            onExpand?.let { expand ->
                QueueIconButton(DeckIcon.Grid, "Expand queue", onClick = expand)
                Spacer(Modifier.width(6.dp))
            }
            QueueIconButton(DeckIcon.Check, "Done editing queue", onClick = onDone)
        }
        if (showDragHint || undoItem != null) {
            Spacer(Modifier.height(8.dp))
            QueueNoticeRow(
                showDragHint = showDragHint,
                undoItem = undoItem,
                onUndoRemove = onUndoRemove,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item(key = "now-playing") {
                QueuePinnedTrackRow(currentTrack)
            }
            if (displayedQueueTracks.isEmpty()) {
                item(key = "empty-queue") {
                    QueueEmptyState()
                }
            } else {
                itemsIndexed(
                    displayedQueueTracks,
                    key = { _, track -> track.stableKey() },
                ) { index, queuedTrack ->
                    QueueTrackRow(
                        index = index,
                        track = queuedTrack,
                        reason = queueReasons[queuedTrack.stableKey()],
                        isFirst = index == 0,
                        isDragging = draggedKey == queuedTrack.stableKey(),
                        dragX = if (draggedKey == queuedTrack.stableKey()) dragOffsetX else 0f,
                        dragY = if (draggedKey == queuedTrack.stableKey()) dragOffsetY else 0f,
                        peerShiftY = queuePeerShiftY(index, dragStartIndex, dragTargetIndex, rowStepPx),
                        removeThresholdPx = removeThresholdPx,
                        hintPulse = showDragHint,
                        onDragStart = { startQueueDrag(queuedTrack.stableKey(), index) },
                        onDrag = ::updateQueueDrag,
                        onDragEnd = ::finishQueueDrag,
                        onDragCancel = ::clearDragState,
                    )
                }
                if (hiddenQueueCount > 0) {
                    item(key = "hidden-queue-count") {
                        QueueHiddenTailRow(hiddenQueueCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueNoticeRow(
    showDragHint: Boolean,
    undoItem: QueueUndoItem?,
    onUndoRemove: (QueueUndoItem) -> Unit,
) {
    val noticeAlpha by animateFloatAsState(
        targetValue = if (showDragHint || undoItem != null) 1f else 0f,
        animationSpec = tween(180),
        label = "queueNoticeAlpha",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .graphicsLayer {
                alpha = noticeAlpha
                translationY = 6f * (1f - noticeAlpha)
            }
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(17.dp))
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(if (undoItem != null) DeckIcon.Check else DeckIcon.Queue, Color.White.copy(alpha = 0.72f), Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            undoItem?.let { "Removed ${it.track.title}" } ?: "Drag a row to reorder. Swipe left to remove.",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        undoItem?.let { item ->
            Box(
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Blue.copy(alpha = 0.34f))
                    .pressScaleEffect(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) { onUndoRemove(item) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Undo", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

private fun queuePeerShiftY(index: Int, dragStartIndex: Int, dragTargetIndex: Int, rowStepPx: Float): Float =
    when {
        dragStartIndex < 0 || dragTargetIndex < 0 || dragStartIndex == dragTargetIndex -> 0f
        dragStartIndex < dragTargetIndex && index in (dragStartIndex + 1)..dragTargetIndex -> -rowStepPx
        dragTargetIndex < dragStartIndex && index in dragTargetIndex until dragStartIndex -> rowStepPx
        else -> 0f
    }

@Composable
private fun QueuePinnedTrackRow(track: Track) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(track.album, Modifier.size(38.dp), 9.dp, useCase = ArtworkUseCase.SongListThumbnail)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Now playing", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QueueTrackRow(
    index: Int,
    track: Track,
    reason: PremiumDeckQueueReason?,
    isFirst: Boolean,
    isDragging: Boolean,
    dragX: Float,
    dragY: Float,
    peerShiftY: Float,
    removeThresholdPx: Float,
    hintPulse: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val latestDragCancel by rememberUpdatedState(onDragCancel)
    val dragSettleSpec = spring<Float>(
        stiffness = PulseMotion.Spring.PlayerGestureStiffness,
        dampingRatio = PulseMotion.Spring.PlayerDamping,
    )
    val animatedPeerShiftY by animateFloatAsState(
        targetValue = peerShiftY,
        animationSpec = dragSettleSpec,
        label = "queuePeerShiftY",
    )
    val rowX = if (isDragging) dragX else 0f
    val rowY = if (isDragging) dragY else animatedPeerShiftY
    val removeReveal = if (isDragging) (-rowX / removeThresholdPx).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .zIndex(if (isDragging) 1f else 0f),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFE5484D).copy(alpha = 0.18f + removeReveal * 0.42f))
                .border(1.dp, Color.White.copy(alpha = 0.08f + removeReveal * 0.16f), RoundedCornerShape(18.dp))
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text("REMOVE", color = Color.White.copy(alpha = 0.45f + removeReveal * 0.48f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black)
        }
        Row(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = rowX
                    translationY = rowY
                    shadowElevation = if (isDragging) 22f else 0f
                    scaleX = if (isDragging) 1.015f else 1f
                    scaleY = if (isDragging) 1.015f else 1f
                }
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = if (isFirst) 0.44f else 0.28f))
                .border(1.dp, if (isFirst) Blue.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Queue track ${track.title}"
                    stateDescription = if (isDragging) "Moving" else if (isFirst) "Next" else "Queued"
                }
                .pointerInput(track.stableKey()) {
                    detectDragGestures(
                        onDragStart = { latestDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            latestDrag(amount)
                        },
                        onDragEnd = { latestDragEnd() },
                        onDragCancel = { latestDragCancel() },
                    )
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(track.album, Modifier.size(40.dp), 10.dp, useCase = ArtworkUseCase.SongListThumbnail)
            Spacer(Modifier.width(8.dp))
            QueueGripIndicator(
                pulsing = hintPulse,
                modifier = Modifier.graphicsLayer { alpha = (1f - removeReveal * 1.35f).coerceIn(0f, 1f) },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    reason?.displayText ?: if (isFirst) "Next" else track.artist,
                    color = Color.White.copy(alpha = if (isFirst) 0.78f else 0.58f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QueueGripIndicator(pulsing: Boolean, modifier: Modifier = Modifier) {
    var pulseOn by remember(pulsing) { mutableStateOf(false) }
    LaunchedEffect(pulsing) {
        if (!pulsing) {
            pulseOn = false
            return@LaunchedEffect
        }
        repeat(6) {
            pulseOn = !pulseOn
            delay(260L)
        }
        pulseOn = false
    }
    val pulse by animateFloatAsState(
        targetValue = if (pulseOn) 1f else 0f,
        animationSpec = tween(220),
        label = "queueGripPulse",
    )
    Canvas(
        modifier
            .size(18.dp, 38.dp)
            .semantics { contentDescription = "Drag to reorder" },
    ) {
        val dotRadius = size.minDimension * (0.12f + pulse * 0.03f)
        val dotAlpha = 0.38f + pulse * 0.28f
        val left = size.width * 0.34f
        val right = size.width * 0.66f
        val top = size.height * 0.26f
        val center = size.height * 0.50f
        val bottom = size.height * 0.74f
        listOf(top, center, bottom).forEach { y ->
            drawCircle(Color.White.copy(alpha = dotAlpha), dotRadius, Offset(left, y))
            drawCircle(Color.White.copy(alpha = dotAlpha), dotRadius, Offset(right, y))
        }
    }
}

@Composable
private fun QueueIconButton(
    icon: DeckIcon,
    contentDescription: String,
    enabled: Boolean = true,
    rotationZ: Float = 0f,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val clickModifier = if (enabled) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
    } else {
        Modifier
    }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.13f else 0.05f))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                stateDescription = if (enabled) "Available" else "Unavailable"
            }
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(
            icon,
            Color.White.copy(alpha = if (enabled) 0.88f else 0.28f),
            Modifier
                .size(16.dp)
                .graphicsLayer { this.rotationZ = rotationZ },
        )
    }
}

@Composable
private fun QueueEmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 86.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PulseIcon(DeckIcon.Queue, Color.White.copy(alpha = 0.50f), Modifier.size(24.dp))
        Text("Queue ends here", color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
        Text("Playback source will resume when available", color = Color.White.copy(alpha = 0.54f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QueueHiddenTailRow(hiddenCount: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Queue, Color.White.copy(alpha = 0.48f), Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            "$hiddenCount more queued",
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExpandablePlayerUtilityDock(
    slots: List<PlayerButtonAction>,
    buttonWidth: Dp,
    buttonRows: Int,
    nextTrack: Track?,
    nextReason: PremiumDeckQueueReason?,
    palette: AdaptivePalette,
    saveActive: Boolean,
    saveDescription: String,
    onSave: () -> Unit,
    iconFor: (PlayerButtonAction) -> DeckIcon,
    activeFor: (PlayerButtonAction) -> Boolean,
    descriptionFor: (PlayerButtonAction) -> String,
    clickFor: (PlayerButtonAction) -> () -> Unit,
    longClickFor: (PlayerButtonAction) -> (() -> Unit)?,
    sleepTimerLabel: String?,
    repeatBadge: String,
    shuffleBadge: String?,
) {
    val motion = LocalPulseMotionSpec.current
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var showNextTeaser by remember { mutableStateOf(false) }
    val displaySlots = remember(slots) { slots.take(6).filter { it != PlayerButtonAction.None } }
    val expandedControls = remember(displaySlots) {
        listOf(PlayerUtilityDockControl.Expander, PlayerUtilityDockControl.Save) +
            displaySlots.map { PlayerUtilityDockControl.Action(it) }
    }
    val utilityDockSpring = if (motion.disabled) {
        tween<Float>(0)
    } else {
        spring(stiffness = PulseMotion.Spring.PlayerGestureStiffness, dampingRatio = 0.68f)
    }
    val reveal by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = utilityDockSpring,
        label = "utilityDockReveal",
    )
    val showUtilities = expanded || reveal > 0.01f
    LaunchedEffect(nextTrack?.stableKey(), expanded) {
        showNextTeaser = false
        if (nextTrack != null && !expanded) {
            delay(NextTeaserInitialDelayMillis)
            if (expanded) return@LaunchedEffect
            showNextTeaser = true
            while (true) {
                delay(NextTeaserVisibleMillis)
                showNextTeaser = false
                delay(NextTeaserHiddenMillis)
                if (expanded) return@LaunchedEffect
                showNextTeaser = true
            }
        } else {
            showNextTeaser = false
        }
    }
    val visibleControls = if (showUtilities) {
        expandedControls
    } else {
        listOf(PlayerUtilityDockControl.Expander, PlayerUtilityDockControl.Save)
    }
    val rowCount = buttonRows.coerceIn(1, 2)
    val useTwoRows = showUtilities && rowCount > 1 && visibleControls.size > 4
    val targetUsesTwoRows = expanded && rowCount > 1 && expandedControls.size > 4
    val targetDockHeight = when {
        targetUsesTwoRows -> 106.dp
        expanded -> 62.dp
        else -> 58.dp
    }
    val utilityDockHeightSpec = if (motion.disabled) {
        tween<Dp>(0)
    } else {
        spring(stiffness = PulseMotion.Spring.PlayerGestureStiffness, dampingRatio = 0.68f)
    }
    val dockHeight by animateDpAsState(
        targetValue = targetDockHeight,
        animationSpec = utilityDockHeightSpec,
        label = "utilityDockHeight",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(dockHeight),
    ) {
        val horizontalPadding = 20.dp
        val gap = if (maxWidth < 380.dp) 8.dp else 12.dp
        val controlWidth = with(density) {
            val visibleCount = visibleControls.size.coerceAtLeast(1)
            val availablePx = (maxWidth.toPx() - horizontalPadding.toPx() * 2f - gap.toPx() * (visibleCount - 1))
                .coerceAtLeast(48.dp.toPx() * visibleCount)
            min(buttonWidth.toPx(), availablePx / visibleCount).toDp()
        }

        @Composable
        fun UtilityControl(control: PlayerUtilityDockControl, index: Int) {
            val controlHeight = if (showUtilities) 60.dp else 52.dp
            Box(
                Modifier
                    .width(controlWidth)
                    .height(controlHeight)
                    .graphicsLayer {
                        val alphaProgress = if (!showUtilities) 1f else reveal
                        alpha = alphaProgress
                        translationY = 8f * (1f - alphaProgress)
                        scaleX = 0.94f + alphaProgress * 0.06f
                        scaleY = 0.94f + alphaProgress * 0.06f
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (control) {
                    PlayerUtilityDockControl.Expander -> {
                        IconPill(
                            icon = if (expanded || reveal > 0.5f) DeckIcon.Close else DeckIcon.More,
                            onClick = { expanded = !expanded },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(controlWidth),
                            active = expanded,
                            contentDescription = if (expanded) "Collapse player tools" else "Expand player tools",
                            height = 44.dp,
                            iconSize = 23.dp,
                        )
                    }
                    PlayerUtilityDockControl.Save -> {
                        PlayerSaveUtilityButton(
                            width = controlWidth,
                            active = saveActive,
                            contentDescription = saveDescription,
                            palette = palette,
                            onClick = onSave,
                        )
                    }
                    is PlayerUtilityDockControl.Action -> {
                        PlayerUtilityButton(
                            action = control.action,
                            icon = iconFor(control.action),
                            width = controlWidth,
                            active = activeFor(control.action),
                            contentDescription = descriptionFor(control.action),
                            onClick = clickFor(control.action),
                            onLongClick = longClickFor(control.action),
                            sleepTimerLabel = sleepTimerLabel,
                            repeatBadge = repeatBadge,
                            shuffleBadge = shuffleBadge,
                        )
                    }
                }
            }
        }

        if (!showUtilities) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(dockHeight)
                    .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UtilityControl(PlayerUtilityDockControl.Expander, 0)
                Spacer(Modifier.width(gap))
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    NextTrackGlance(
                        track = nextTrack,
                        reason = nextReason,
                        visible = showNextTeaser,
                        palette = palette,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(gap))
                UtilityControl(PlayerUtilityDockControl.Save, 1)
            }
        } else if (visibleControls.size <= 4 && !useTwoRows) {
            val leftCount = (visibleControls.size / 2).coerceAtLeast(1)
            val leftControls = visibleControls.take(leftCount)
            val rightControls = visibleControls.drop(leftCount)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(dockHeight)
                    .padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    leftControls.forEachIndexed { index, control -> UtilityControl(control, index) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rightControls.forEachIndexed { index, control -> UtilityControl(control, leftCount + index) }
                }
            }
        } else {
            val perRow = if (useTwoRows) (visibleControls.size + 1) / 2 else visibleControls.size
            val rows = visibleControls.chunked(perRow.coerceAtLeast(1))
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(dockHeight)
                    .padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(if (useTwoRows) 4.dp else 0.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                rows.forEachIndexed { rowIndex, rowControls ->
                    Row(
                        Modifier.fillMaxWidth().height(50.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowControls.forEachIndexed { index, control ->
                            UtilityControl(control, rowIndex * perRow + index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextTrackGlance(track: Track?, reason: PremiumDeckQueueReason?, visible: Boolean, palette: AdaptivePalette, modifier: Modifier = Modifier) {
    if (track == null) return
    val density = LocalDensity.current
    val slideDistance = with(density) { 34.dp.toPx() }
    val teaserAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visible) 520 else 460,
            easing = if (visible) PulseMotion.Easing.Emphasized else PulseMotion.Easing.Standard,
        ),
        label = "nextTrackTeaserAlpha",
    )
    val labelColor = lerp(palette.accent, Color.White, 0.42f)
    val titleColor = lerp(Color.White, palette.muted, 0.08f)
    val reasonColor = lerp(Color.White, palette.muted, 0.34f)
    Column(
        modifier
            .graphicsLayer {
                alpha = teaserAlpha
                translationX = -slideDistance * (1f - teaserAlpha)
                translationY = 2f * (1f - teaserAlpha)
                scaleX = 0.90f + teaserAlpha * 0.10f
                scaleY = 0.96f + teaserAlpha * 0.04f
            }
            .semantics {
                contentDescription = listOf("Next track ${track.title}", reason?.displayText).filterNotNull().joinToString(", ")
            }
            .padding(start = 2.dp, end = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "UP NEXT",
            color = labelColor.copy(alpha = 0.72f),
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.title,
            color = titleColor.copy(alpha = 0.96f),
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            reason?.displayText ?: track.artist,
            color = reasonColor.copy(alpha = 0.68f),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerUtilityButton(
    action: PlayerButtonAction,
    icon: DeckIcon,
    width: Dp,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    sleepTimerLabel: String?,
    repeatBadge: String,
    shuffleBadge: String?,
) {
    val badgeText = when (action) {
        PlayerButtonAction.SleepTimer -> sleepTimerLabel
        PlayerButtonAction.Repeat -> repeatBadge
        PlayerButtonAction.Shuffle -> shuffleBadge
        else -> null
    }
    Box(
        Modifier
            .width(width)
            .height(60.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconPill(
            icon,
            onClick,
            Modifier
                .align(Alignment.BottomCenter)
                .width(width),
            active = active,
            contentDescription = contentDescription,
            onLongClick = onLongClick,
            height = 44.dp,
            iconSize = 24.dp,
        )
        badgeText?.let { UtilityStatusBadge(it, Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun PlayerSaveUtilityButton(
    width: Dp,
    active: Boolean,
    contentDescription: String,
    palette: AdaptivePalette,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val motion = LocalPulseMotionSpec.current
    val morph by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(motion.duration(PulseMotion.Duration.PopupIn), easing = PulseMotion.Easing.Emphasized),
        label = "playerSavePillMorph",
    )
    Box(
        Modifier
            .width(width)
            .height(60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .width(width)
                .height(44.dp)
                .widthIn(min = 48.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(Color.White.copy(0.16f), palette.accent.copy(alpha = 0.24f), morph),
                            lerp(Color.Black.copy(0.20f), Color.Black.copy(alpha = 0.30f), morph),
                        ),
                    ),
                )
                .border(
                    1.dp,
                    lerp(Color.White.copy(alpha = 0.04f), palette.accent.copy(alpha = 0.34f), morph),
                    RoundedCornerShape(28.dp),
                )
                .pressScaleEffect(interactionSource)
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                    stateDescription = if (active) "Saved" else "Not saved"
                }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(
                DeckIcon.Plus,
                Color.White.copy(alpha = 0.88f * (1f - morph)),
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = 1f - morph
                        scaleX = 1f - 0.18f * morph
                        scaleY = 1f - 0.18f * morph
                        rotationZ = -18f * morph
                    },
            )
            PulseIcon(
                DeckIcon.Check,
                lerp(Color.White, palette.accent, 0.42f),
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = morph
                        scaleX = 0.74f + 0.26f * morph
                        scaleY = 0.74f + 0.26f * morph
                        rotationZ = 16f * (1f - morph)
                    },
            )
        }
    }
}

@Composable
private fun UtilityStatusBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.horizontalGradient(listOf(Blue.copy(alpha = 0.84f), Color.Black.copy(alpha = 0.78f))))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(9.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun Color.fullPlayerColorBoost(saturationMultiplier: Float, valueLift: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255f).roundToInt().coerceIn(0, 255),
        (green * 255f).roundToInt().coerceIn(0, 255),
        (blue * 255f).roundToInt().coerceIn(0, 255),
        hsv,
    )
    hsv[1] = (hsv[1] * saturationMultiplier + 0.04f).coerceIn(0.26f, 0.96f)
    hsv[2] = (hsv[2] + valueLift).coerceIn(0.32f, 0.98f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
internal fun PlaybackModeDialog(
    menu: PlayerModeMenu,
    shuffleMode: ShuffleMode,
    repeatMode: PlaybackRepeatMode,
    onDismiss: () -> Unit,
    onShuffleMode: (ShuffleMode) -> Unit,
    onRepeatMode: (PlaybackRepeatMode) -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(motion.duration(if (visible) PulseMotion.Duration.PopupIn else PulseMotion.Duration.PopupOut), easing = PulseMotion.Easing.Standard),
        label = "playbackModeDialogAlpha",
    )
    LaunchedEffect(Unit) { visible = true }
    val dismissInteraction = remember { MutableInteractionSource() }
    val contentInteraction = remember { MutableInteractionSource() }
    val title = if (menu == PlayerModeMenu.Shuffle) "Shuffle" else "Repeat"
    val subtitle = if (menu == PlayerModeMenu.Shuffle) "Choose playback order" else "Choose stop or loop behavior"

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = PulseMotion.Alpha.ModalScrim * alpha))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = PulseMotion.Scale.ModalStart + (1f - PulseMotion.Scale.ModalStart) * alpha
                    scaleY = PulseMotion.Scale.ModalStart + (1f - PulseMotion.Scale.ModalStart) * alpha
                }
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF242018).copy(alpha = 0.96f),
                            Color(0xFF121111).copy(alpha = 0.98f),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = contentInteraction,
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (menu == PlayerModeMenu.Shuffle) {
                    ShuffleMode.entries.forEach { mode ->
                        ModeOptionRow(
                            title = mode.title,
                            subtitle = mode.subtitle,
                            selected = mode == shuffleMode,
                            badge = mode.badge,
                        ) { onShuffleMode(mode) }
                    }
                } else {
                    PlaybackRepeatMode.entries.forEach { mode ->
                        ModeOptionRow(
                            title = mode.title,
                            subtitle = mode.subtitle,
                            selected = mode == repeatMode,
                            badge = mode.badge,
                        ) { onRepeatMode(mode) }
                    }
                }
            }
            SleepDialogButton(
                "Cancel",
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                tone = Color.White.copy(alpha = 0.08f),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
internal fun TrackActionsDialog(
    track: Track,
    liked: Boolean,
    disliked: Boolean,
    bookmarked: Boolean,
    inPlaylist: Boolean,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onBookmark: () -> Unit,
    onPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onMetadata: (String) -> Unit,
    onLyrics: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onFolder: () -> Unit,
    onGenre: () -> Unit,
    onChangeArt: () -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    val quality = rememberTrackQuality(track)
    val type = track.audioTypeLabel()
    val actions = listOf(
        TrackActionItem("Delete", "Remove file", DeckIcon.Trash, destructive = true, onClick = onDelete),
        TrackActionItem(if (inPlaylist) "In Playlist" else "Playlist", "Pulse list", DeckIcon.Playlist, active = inPlaylist, onClick = onPlaylist),
        TrackActionItem(if (bookmarked) "Bookmarked" else "Bookmark", "Save point", DeckIcon.Bookmark, active = bookmarked, onClick = onBookmark),
        TrackActionItem("Change Art", "Future edit", DeckIcon.Disc, onClick = onChangeArt),
        TrackActionItem("Info & Tags", "Metadata", DeckIcon.Info, onClick = { onMetadata(quality) }),
        TrackActionItem("Lyrics", "Embedded text", DeckIcon.Pencil, onClick = onLyrics),
        TrackActionItem("Artist", track.artist, DeckIcon.Person, onClick = onArtist),
        TrackActionItem("Album", track.album.title, DeckIcon.Disc, onClick = onAlbum),
        TrackActionItem("Folder", normalizedFolderPath(track).ifBlank { "Music" }, DeckIcon.Folder, onClick = onFolder),
        TrackActionItem("Genre", track.genre ?: "Unknown", DeckIcon.Tag, onClick = onGenre),
    )
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(motion.duration(if (visible) PulseMotion.Duration.PopupIn else PulseMotion.Duration.PopupOut), easing = PulseMotion.Easing.Standard),
        label = "trackActionsDialogAlpha",
    )
    LaunchedEffect(Unit) { visible = true }
    val dismissInteraction = remember { MutableInteractionSource() }
    val contentInteraction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = PulseMotion.Alpha.ModalScrim * alpha))
            .clickable(interactionSource = dismissInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = PulseMotion.Scale.ModalStart + (1f - PulseMotion.Scale.ModalStart) * alpha
                    scaleY = PulseMotion.Scale.ModalStart + (1f - PulseMotion.Scale.ModalStart) * alpha
                }
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF272018).copy(alpha = 0.97f),
                            Color(0xFF121214).copy(alpha = 0.99f),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
                .clickable(interactionSource = contentInteraction, indication = null, onClick = {})
                .padding(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Art(track.album, Modifier.size(72.dp), 18.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Color.White.copy(0.72f), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(quality, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
                IconTap(DeckIcon.Close, onDismiss, 38.dp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackMetaChip("Duration", track.duration, Modifier.weight(1f))
                TrackMetaChip("Type", type, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReactionButton(DeckIcon.ThumbUp, "Like", liked, Modifier.weight(1f), onLike)
                ReactionButton(DeckIcon.ThumbDown, "Dislike", disliked, Modifier.weight(1f), onDislike)
            }
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { action ->
                            TrackActionButton(action, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullPlayerSaveSheet(
    state: FullPlayerSaveSheetState,
    onDismiss: () -> Unit,
    onToggleLiked: () -> Unit,
    onToggleLocalPlaylist: () -> Unit,
    onToggleRadioFavorite: () -> Unit,
    onTogglePremiumPlaylist: (String) -> Unit,
    onCreatePremiumPlaylist: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    BasicInfoModal(title = "Add to PulseDeck", subtitle = "${state.title} - ${state.subtitle}", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (state.likedAvailable) {
                SaveLocationRow(
                    title = "Liked Songs",
                    subtitle = when (state.sourceKind) {
                        FullPlayerSaveSourceKind.PremiumDeck -> "PremiumDeck liked source"
                        FullPlayerSaveSourceKind.LocalFile -> "Local library favorite"
                        FullPlayerSaveSourceKind.PulseRadio -> "Not available for live radio"
                    },
                    icon = DeckIcon.Heart,
                    selected = state.liked,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleLiked()
                }
            }
            if (state.localPlaylistAvailable) {
                SaveLocationRow(
                    title = "Pulse List",
                    subtitle = "Local library playlist",
                    icon = DeckIcon.Playlist,
                    selected = state.localPlaylistSaved,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleLocalPlaylist()
                }
            }
            if (state.radioFavoriteAvailable) {
                SaveLocationRow(
                    title = "Saved Station",
                    subtitle = "PulseRadio favorites",
                    icon = DeckIcon.Heart,
                    selected = state.radioFavoriteSaved,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleRadioFavorite()
                }
            }
        }
        if (state.sourceKind == FullPlayerSaveSourceKind.PremiumDeck) {
            Text(
                "Playlists",
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 8.dp),
            )
            if (state.premiumPlaylists.isEmpty()) {
                Text(
                    "No editable PremiumDeck playlists yet.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 266.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(state.premiumPlaylists, key = { it.id }) { playlist ->
                        SaveLocationRow(
                            title = playlist.title,
                            subtitle = playlist.subtitle,
                            icon = DeckIcon.Playlist,
                            selected = playlist.saved,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePremiumPlaylist(playlist.id)
                        }
                    }
                }
            }
            if (state.canCreatePremiumPlaylist) {
                SleepDialogButton(
                    "New Playlist",
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    tone = StreamAccentRed.copy(alpha = 0.62f),
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCreatePremiumPlaylist()
                }
            }
        }
        SleepDialogButton("Done", Modifier.fillMaxWidth().padding(top = 14.dp), tone = Color.White.copy(alpha = 0.12f), onClick = onDismiss)
    }
}

@Composable
private fun SaveLocationRow(
    title: String,
    subtitle: String,
    icon: DeckIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) StreamAccentRed.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.075f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.18f else 0.07f), RoundedCornerShape(19.dp))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Checkbox
                contentDescription = title
                stateDescription = if (selected) "Saved" else "Not saved"
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(icon, if (selected) StreamAccentRed else Color.White.copy(alpha = 0.82f), Modifier.size(22.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PulseIcon(if (selected) DeckIcon.Check else DeckIcon.Plus, if (selected) StreamAccentRed else Color.White.copy(alpha = 0.56f), Modifier.size(20.dp))
    }
}

@Composable
private fun TrackMetaChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(label, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black)
        Text(value, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReactionButton(icon: DeckIcon, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Blue.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.20f else 0.06f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PulseIcon(icon, Color.White, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TrackActionButton(action: TrackActionItem, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (action.active) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (action.destructive) 0.22f else if (action.active) 0.16f else 0.06f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = action.onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(action.icon, if (action.destructive) Color(0xFFFF8B7E) else Color.White, Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(action.title, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(action.subtitle, color = Muted, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ConfirmDeleteDialog(track: Track, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    BasicInfoModal(
        title = "Delete File",
        subtitle = track.title,
        onDismiss = onDismiss,
    ) {
        Text("This removes the audio file from your device after Android confirms the request.", color = Muted, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepDialogButton("Cancel", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.08f), onClick = onDismiss)
            SleepDialogButton("Delete", Modifier.weight(1f), tone = Color(0xFFB8322A).copy(alpha = 0.78f), onClick = onConfirm)
        }
    }
}

@Composable
internal fun InfoDialog(dialog: InfoDialogState, onDismiss: () -> Unit) {
    BasicInfoModal(title = dialog.title, subtitle = dialog.subtitle, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dialog.rows.forEach { (label, value) ->
                TrackMetaChip(label, value.ifBlank { "Unknown" }, Modifier.fillMaxWidth())
            }
        }
        SleepDialogButton("OK", Modifier.fillMaxWidth().padding(top = 16.dp), tone = Blue.copy(alpha = 0.50f), onClick = onDismiss)
    }
}

@Composable
internal fun LyricsDialog(
    track: Track,
    progressController: PlaybackProgressController,
    progressEnabled: Boolean,
    onSyncedActiveChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("lyrics_dialog")
    }
    val settings = LyricsRuntime.settings.normalized()
    val state = rememberLyricsUiState(track)
    val syncedActive = (state as? LyricsUiState.Synced)
        ?.lines
        ?.isNotEmpty() == true && settings.preferSynced && progressEnabled
    LaunchedEffect(syncedActive) {
        onSyncedActiveChange(syncedActive)
    }
    DisposableEffect(Unit) {
        onDispose { onSyncedActiveChange(false) }
    }
    BasicInfoModal(title = "Lyrics", subtitle = "${track.artist} - ${track.title}", onDismiss = onDismiss) {
        when (val current = state) {
            LyricsUiState.Loading -> {
                Text("Looking for lyrics...", color = Muted, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            }
            LyricsUiState.Missing -> {
                Text("No lyrics found for this track.", color = Muted, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                SleepDialogButton("OK", Modifier.fillMaxWidth().padding(top = 16.dp), tone = Blue.copy(alpha = 0.50f), onClick = onDismiss)
            }
            LyricsUiState.BlockedOffline -> {
                Text("Cached lyrics were not found while offline.", color = Muted, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                SleepDialogButton("OK", Modifier.fillMaxWidth().padding(top = 16.dp), tone = Blue.copy(alpha = 0.50f), onClick = onDismiss)
            }
            LyricsUiState.BlockedDataSaver -> {
                Text("Cached lyrics were not found and Data Saver is active.", color = Muted, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                SleepDialogButton("OK", Modifier.fillMaxWidth().padding(top = 16.dp), tone = Blue.copy(alpha = 0.50f), onClick = onDismiss)
            }
            is LyricsUiState.Error -> {
                Text(current.message, color = Muted, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                SleepDialogButton("OK", Modifier.fillMaxWidth().padding(top = 16.dp), tone = Blue.copy(alpha = 0.50f), onClick = onDismiss)
            }
            is LyricsUiState.Synced -> {
                val lines = current.lines
                val showSynced = settings.preferSynced && lines.isNotEmpty()
                Text("Synced lyrics", color = Blue.copy(alpha = 0.78f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 10.dp))
                if (showSynced && progressEnabled) {
                    SyncedLyricsList(lines = lines, progressController = progressController)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(lines.map { it.text }.filter { it.isNotBlank() }) { line ->
                            Text(line, color = Muted, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                SleepDialogButton("Close", Modifier.fillMaxWidth().padding(top = 16.dp), tone = Blue.copy(alpha = 0.50f), onClick = onDismiss)
            }
            is LyricsUiState.Plain -> {
                Text("Plain lyrics", color = Blue.copy(alpha = 0.78f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 10.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current.text.lines().filter { it.isNotBlank() }) { line ->
                        Text(line, color = Muted, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    }
                }
                SleepDialogButton("Close", Modifier.fillMaxWidth().padding(top = 16.dp), tone = Blue.copy(alpha = 0.50f), onClick = onDismiss)
            }
        }
    }
}

@Composable
internal fun BasicInfoModal(title: String, subtitle: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val motion = LocalPulseMotionSpec.current
        var visible by remember { mutableStateOf(false) }
        val alpha by animateFloatAsState(
            if (visible) 1f else 0f,
            tween(motion.duration(if (visible) PulseMotion.Duration.PopupIn else PulseMotion.Duration.PopupOut), easing = PulseMotion.Easing.Standard),
            label = "basicInfoModalAlpha",
        )
        LaunchedEffect(Unit) { visible = true }
        val dismissInteraction = remember { MutableInteractionSource() }
        val contentInteraction = remember { MutableInteractionSource() }
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dialogWindow != null) {
                val previousBlur = dialogWindow.attributes.blurBehindRadius
                val attributes = dialogWindow.attributes
                attributes.blurBehindRadius = 30
                dialogWindow.attributes = attributes
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                onDispose {
                    val restored = dialogWindow.attributes
                    restored.blurBehindRadius = previousBlur
                    dialogWindow.attributes = restored
                    dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            } else {
                onDispose {}
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF03060B).copy(alpha = 0.58f * alpha))
                .clickable(interactionSource = dismissInteraction, indication = null, onClick = onDismiss)
                .padding(horizontal = 26.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = PulseMotion.Scale.ModalStart + (1f - PulseMotion.Scale.ModalStart) * alpha
                        scaleY = PulseMotion.Scale.ModalStart + (1f - PulseMotion.Scale.ModalStart) * alpha
                    }
                    .fillMaxWidth()
                    .widthIn(max = 430.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF2D3850).copy(alpha = 0.94f),
                                Color(0xFF1A2335).copy(alpha = 0.91f),
                                Color(0xFF111827).copy(alpha = 0.94f),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
                    .clickable(interactionSource = contentInteraction, indication = null, onClick = {})
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = Color.White, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                content()
            }
        }
    }
}

@Composable
internal fun ModeOptionRow(title: String, subtitle: String, selected: Boolean, badge: String?, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.14f else 0.00f), RoundedCornerShape(16.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        badge?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = if (selected) 0.16f else 0.08f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
    }
}

private enum class PulsePlaybackGlyph {
    Play,
    Pause,
    PreviousTrack,
    NextTrack,
    PreviousAlbum,
    NextAlbum,
}

@Composable
private fun PulsePlaybackControlsRow(
    playing: Boolean,
    playSize: Dp,
    trackSize: Dp,
    albumSize: Dp,
    spacing: Dp,
    onPreviousAlbum: () -> Unit,
    onPreviousTrack: () -> Unit,
    onPlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onNextAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseAlbumNavButton(
            next = false,
            buttonSize = albumSize,
            enabled = enabled,
            onClick = onPreviousAlbum,
        )
        PulseTrackNavButton(
            next = false,
            buttonSize = trackSize,
            enabled = enabled,
            onClick = onPreviousTrack,
        )
        PulsePlayPauseButton(
            playing = playing,
            buttonSize = playSize,
            enabled = enabled,
            onClick = onPlayPause,
        )
        PulseTrackNavButton(
            next = true,
            buttonSize = trackSize,
            enabled = enabled,
            onClick = onNextTrack,
        )
        PulseAlbumNavButton(
            next = true,
            buttonSize = albumSize,
            enabled = enabled,
            onClick = onNextAlbum,
        )
    }
}

@Composable
private fun PulsePlayPauseButton(
    playing: Boolean,
    buttonSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    forcePressed: Boolean = false,
    onClick: () -> Unit,
) {
    val pauseBlend by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "pulsePlayPauseBlend",
    )
    PulsePlaybackButton(
        glyph = if (playing) PulsePlaybackGlyph.Pause else PulsePlaybackGlyph.Play,
        contentDescription = if (playing) "Pause" else "Play",
        buttonSize = buttonSize,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        forcePressed = forcePressed,
        playPauseBlend = pauseBlend,
        onClick = onClick,
    )
}

@Composable
private fun PulseTrackNavButton(
    next: Boolean,
    buttonSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    forcePressed: Boolean = false,
    onClick: () -> Unit,
) {
    PulsePlaybackButton(
        glyph = if (next) PulsePlaybackGlyph.NextTrack else PulsePlaybackGlyph.PreviousTrack,
        contentDescription = if (next) "Next track" else "Previous track",
        buttonSize = buttonSize,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        forcePressed = forcePressed,
        onClick = onClick,
    )
}

@Composable
private fun PulseAlbumNavButton(
    next: Boolean,
    buttonSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    forcePressed: Boolean = false,
    onClick: () -> Unit,
) {
    PulsePlaybackButton(
        glyph = if (next) PulsePlaybackGlyph.NextAlbum else PulsePlaybackGlyph.PreviousAlbum,
        contentDescription = if (next) "Next album" else "Previous album",
        buttonSize = buttonSize,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        forcePressed = forcePressed,
        onClick = onClick,
    )
}

@Composable
private fun PulsePlaybackButton(
    glyph: PulsePlaybackGlyph,
    contentDescription: String,
    buttonSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    forcePressed: Boolean = false,
    playPauseBlend: Float? = null,
    onClick: () -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressed = (isPressed || forcePressed) && enabled
    val targetScale = when {
        !pressed -> 1f
        buttonSize >= 72.dp -> 0.95f
        else -> 0.965f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (motion.disabled) {
            tween(0)
        } else {
            spring(dampingRatio = 0.78f, stiffness = 640f)
        },
        label = "pulsePlaybackPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.35f,
        animationSpec = tween(motion.duration(PulseMotion.Duration.Tap), easing = PulseMotion.Easing.Standard),
        label = "pulsePlaybackAlpha",
    )
    val hitSize = if (buttonSize < 48.dp) 48.dp else buttonSize

    Box(
        modifier
            .size(hitSize)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (!enabled) {
                    stateDescription = "Disabled"
                }
            }
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
        ) {
            drawPulseButtonBase(pressed = pressed)
            if (loading) {
                drawPulseLoadingArc()
            }
            playPauseBlend?.let { blend ->
                drawPulsePlaybackGlyph(PulsePlaybackGlyph.Play, (1f - blend).coerceIn(0f, 1f))
                drawPulsePlaybackGlyph(PulsePlaybackGlyph.Pause, blend.coerceIn(0f, 1f))
            } ?: drawPulsePlaybackGlyph(glyph, 1f)
        }
    }
}

private fun DrawScope.drawPulseButtonBase(pressed: Boolean) {
    val radius = size.minDimension * 0.5f
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    drawCircle(
        color = Color.Black.copy(alpha = 0.34f),
        radius = radius * 0.94f,
        center = center.copy(y = center.y + radius * 0.06f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (pressed) {
                listOf(Color(0xFF171717), Color(0xFF070707), Color.Black)
            } else {
                listOf(Color(0xFF101010), Color(0xFF030303), Color.Black)
            },
            center = center.copy(y = center.y - radius * 0.24f),
            radius = radius * 1.18f,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(alpha = if (pressed) 0.030f else 0.045f),
        radius = radius * 0.58f,
        center = center.copy(x = center.x - radius * 0.16f, y = center.y - radius * 0.22f),
    )
    drawCircle(
        color = Color.Black.copy(alpha = if (pressed) 0.20f else 0.13f),
        radius = radius * 0.86f,
        center = center.copy(y = center.y + radius * 0.34f),
    )
}

private fun DrawScope.drawPulseLoadingArc() {
    val diameter = size.minDimension * 0.76f
    val topLeft = Offset((size.width - diameter) * 0.5f, (size.height - diameter) * 0.5f)
    drawArc(
        color = Color.White.copy(alpha = 0.16f),
        startAngle = -92f,
        sweepAngle = 124f,
        useCenter = false,
        topLeft = topLeft,
        size = Size(diameter, diameter),
        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawPulsePlaybackGlyph(glyph: PulsePlaybackGlyph, alpha: Float) {
    if (alpha <= 0.01f) return
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val iconSize = when (glyph) {
        PulsePlaybackGlyph.Play -> size.minDimension * 0.48f
        PulsePlaybackGlyph.Pause -> size.minDimension * 0.52f
        PulsePlaybackGlyph.PreviousTrack,
        PulsePlaybackGlyph.NextTrack -> size.minDimension * 0.54f
        PulsePlaybackGlyph.PreviousAlbum,
        PulsePlaybackGlyph.NextAlbum -> size.minDimension * 0.58f
    }

    drawPulseGlyphLayer(
        glyph = glyph,
        center = center,
        iconSize = iconSize,
        color = Color(0xFFBFC3CC),
        alpha = alpha * 0.28f,
        offset = Offset(0f, iconSize * 0.050f),
        scale = 1.015f,
    )
    drawPulseGlyphLayer(
        glyph = glyph,
        center = center,
        iconSize = iconSize,
        color = Color.White,
        alpha = alpha,
        offset = Offset.Zero,
        scale = 1f,
    )
    drawPulseGlyphLayer(
        glyph = glyph,
        center = center,
        iconSize = iconSize,
        color = Color.White,
        alpha = alpha * 0.22f,
        offset = Offset(0f, -iconSize * 0.055f),
        scale = 0.83f,
    )
}

private fun DrawScope.drawPulseGlyphLayer(
    glyph: PulsePlaybackGlyph,
    center: Offset,
    iconSize: Float,
    color: Color,
    alpha: Float,
    offset: Offset,
    scale: Float,
) {
    if (alpha <= 0.01f) return
    when (glyph) {
        PulsePlaybackGlyph.Play -> drawPulseTriangle(
            center = center,
            iconSize = iconSize,
            direction = 1f,
            color = color,
            alpha = alpha,
            offset = offset.copy(x = offset.x + iconSize * 0.040f),
            scale = scale,
        )
        PulsePlaybackGlyph.Pause -> drawPulsePauseBars(center, iconSize, color, alpha, offset, scale)
        PulsePlaybackGlyph.PreviousTrack -> drawPulseTrackSkip(next = false, center, iconSize, color, alpha, offset, scale)
        PulsePlaybackGlyph.NextTrack -> drawPulseTrackSkip(next = true, center, iconSize, color, alpha, offset, scale)
        PulsePlaybackGlyph.PreviousAlbum -> drawPulseAlbumSkip(next = false, center, iconSize, color, alpha, offset, scale)
        PulsePlaybackGlyph.NextAlbum -> drawPulseAlbumSkip(next = true, center, iconSize, color, alpha, offset, scale)
    }
}

private fun DrawScope.drawPulseTriangle(
    center: Offset,
    iconSize: Float,
    direction: Float,
    color: Color,
    alpha: Float,
    offset: Offset,
    scale: Float,
) {
    val path = pulseTrianglePath(
        center = center + offset,
        width = iconSize * 0.82f * scale,
        height = iconSize * 0.96f * scale,
        direction = direction,
    )
    drawPath(path, color.copy(alpha = alpha.coerceIn(0f, 1f)))
}

private fun pulseTrianglePath(
    center: Offset,
    width: Float,
    height: Float,
    direction: Float,
): Path {
    val halfW = width * 0.5f
    val halfH = height * 0.5f
    fun x(local: Float) = center.x + local * direction
    fun y(local: Float) = center.y + local
    return Path().apply {
        moveTo(x(-halfW * 0.72f), y(-halfH * 0.68f))
        quadraticTo(x(-halfW * 0.72f), y(-halfH * 0.98f), x(-halfW * 0.40f), y(-halfH * 0.78f))
        lineTo(x(halfW * 0.70f), y(-halfH * 0.13f))
        quadraticTo(x(halfW * 0.98f), y(0f), x(halfW * 0.70f), y(halfH * 0.13f))
        lineTo(x(-halfW * 0.40f), y(halfH * 0.78f))
        quadraticTo(x(-halfW * 0.72f), y(halfH * 0.98f), x(-halfW * 0.72f), y(halfH * 0.68f))
        close()
    }
}

private fun DrawScope.drawPulsePauseBars(
    center: Offset,
    iconSize: Float,
    color: Color,
    alpha: Float,
    offset: Offset,
    scale: Float,
) {
    val barWidth = iconSize * 0.22f * scale
    val barHeight = iconSize * 0.82f * scale
    val gap = iconSize * 0.22f * scale
    val top = center.y + offset.y - barHeight * 0.5f
    val corner = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
    listOf(-1f, 1f).forEach { side ->
        drawRoundRect(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            topLeft = Offset(center.x + offset.x + side * (gap + barWidth) * 0.5f - barWidth * 0.5f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = corner,
        )
    }
}

private fun DrawScope.drawPulseTrackSkip(
    next: Boolean,
    center: Offset,
    iconSize: Float,
    color: Color,
    alpha: Float,
    offset: Offset,
    scale: Float,
) {
    val direction = if (next) 1f else -1f
    drawPulseTriangle(
        center = center.copy(x = center.x - direction * iconSize * 0.09f),
        iconSize = iconSize * 0.78f,
        direction = direction,
        color = color,
        alpha = alpha,
        offset = offset,
        scale = scale,
    )
    drawPulseStopBar(
        center = center.copy(x = center.x + direction * iconSize * 0.34f),
        iconSize = iconSize,
        color = color,
        alpha = alpha,
        offset = offset,
        scale = scale,
        widthFraction = 0.12f,
        heightFraction = 0.72f,
    )
}

private fun DrawScope.drawPulseAlbumSkip(
    next: Boolean,
    center: Offset,
    iconSize: Float,
    color: Color,
    alpha: Float,
    offset: Offset,
    scale: Float,
) {
    val direction = if (next) 1f else -1f
    drawPulseTriangle(
        center = center.copy(x = center.x - direction * iconSize * 0.16f),
        iconSize = iconSize * 0.60f,
        direction = direction,
        color = color,
        alpha = alpha,
        offset = offset,
        scale = scale,
    )
    drawPulseTriangle(
        center = center.copy(x = center.x + direction * iconSize * 0.08f),
        iconSize = iconSize * 0.60f,
        direction = direction,
        color = color,
        alpha = alpha,
        offset = offset,
        scale = scale,
    )
    drawPulseStopBar(
        center = center.copy(x = center.x + direction * iconSize * 0.37f),
        iconSize = iconSize,
        color = color,
        alpha = alpha,
        offset = offset,
        scale = scale,
        widthFraction = 0.095f,
        heightFraction = 0.66f,
    )
}

private fun DrawScope.drawPulseStopBar(
    center: Offset,
    iconSize: Float,
    color: Color,
    alpha: Float,
    offset: Offset,
    scale: Float,
    widthFraction: Float,
    heightFraction: Float,
) {
    val barWidth = iconSize * widthFraction * scale
    val barHeight = iconSize * heightFraction * scale
    drawRoundRect(
        color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
        topLeft = Offset(center.x + offset.x - barWidth * 0.5f, center.y + offset.y - barHeight * 0.5f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f),
    )
}

@Preview(name = "Pulse Playback Controls", showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun PulsePlaybackControlsPreview() {
    Box(
        Modifier
            .background(Color(0xFF0A0A0C))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        PulsePlaybackControlsRow(
            playing = false,
            playSize = 86.dp,
            trackSize = 62.dp,
            albumSize = 50.dp,
            spacing = 12.dp,
            onPreviousAlbum = {},
            onPreviousTrack = {},
            onPlayPause = {},
            onNextTrack = {},
            onNextAlbum = {},
        )
    }
}

@Preview(name = "Pulse Playback States", showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun PulsePlaybackControlsStatesPreview() {
    Row(
        Modifier
            .background(Color(0xFF0A0A0C))
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseAlbumNavButton(next = false, buttonSize = 50.dp, enabled = false, onClick = {})
        PulseTrackNavButton(next = false, buttonSize = 62.dp, forcePressed = true, onClick = {})
        PulsePlayPauseButton(playing = true, buttonSize = 86.dp, forcePressed = true, onClick = {})
        PulseTrackNavButton(next = true, buttonSize = 62.dp, loading = true, onClick = {})
        PulseAlbumNavButton(next = true, buttonSize = 50.dp, enabled = false, onClick = {})
    }
}

@Composable
private fun FullPlayerSeekSurface(
    durationMillis: Long,
    durationLabel: String,
    progressController: PlaybackProgressController,
    waveform: List<Float>,
    playing: Boolean,
    waveformAnimating: Boolean,
    modifier: Modifier,
    onSeek: (Float) -> Unit,
    style: WaveformStyle,
    palette: AdaptivePalette,
    showBadges: Boolean,
) {
    PlayerPulseDotsSeekBarSurface(
        durationMillis = durationMillis,
        durationLabel = durationLabel,
        progressController = progressController,
        playing = playing && waveformAnimating,
        modifier = modifier,
        onSeek = onSeek,
        palette = palette,
        showBadges = showBadges,
    )
}

@Composable
private fun PlayerPulseDotsSeekBarSurface(
    durationMillis: Long,
    durationLabel: String,
    progressController: PlaybackProgressController,
    playing: Boolean,
    modifier: Modifier,
    onSeek: (Float) -> Unit,
    palette: AdaptivePalette,
    showBadges: Boolean,
) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("player_pulse_dots_seek")
    }
    val positionMillis by progressController.positionMillis.collectAsState()
    var phase by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPreviewProgress by remember(durationMillis) { mutableStateOf<Float?>(null) }
    val latestOnSeek by rememberUpdatedState(onSeek)
    val progress = scrubPreviewProgress ?: playbackProgressFraction(positionMillis, durationMillis)
    val displayedPositionMillis = scrubPreviewProgress
        ?.let { preview -> playbackPositionFromProgress(durationMillis, preview) }
        ?: if (durationMillis > 0L) {
            positionMillis.coerceIn(0L, durationMillis)
        } else {
            0L
        }

    LaunchedEffect(playing, scrubbing) {
        if (!playing && !scrubbing) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
            phase = (phase + deltaSeconds * (if (scrubbing) 2.12f else 1.72f)) % 10_000f
            lastFrameNanos = frameNanos
        }
    }

    val seekModifier = Modifier.pointerInput(durationMillis) {
        if (durationMillis <= 0L) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val width = size.width.toFloat().coerceAtLeast(1f)
            fun fractionAt(x: Float): Float = (x / width).coerceIn(0f, 1f)
            fun updatePreview(value: Float) {
                scrubPreviewProgress = value
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
                scrubPreviewProgress = null
                scrubbing = false
            }
        }
    }

    Box(modifier) {
        val canvasHeight = if (showBadges) 66.dp else 58.dp
        val railModifier = Modifier
            .align(if (showBadges) Alignment.TopCenter else Alignment.Center)
            .fillMaxWidth()
            .height(canvasHeight)
            .semantics {
                contentDescription = "Playback progress"
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                stateDescription = "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()} percent"
                if (durationMillis > 0L) {
                    setProgress { target ->
                        latestOnSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .then(seekModifier)

        Canvas(railModifier) {
            val trackWidth = size.width
            if (trackWidth <= 0f) return@Canvas

            val centerY = size.height * 0.50f
            val displayProgress = progress.coerceIn(0f, 1f)
            val energy = if (playing || scrubbing) {
                (0.66f +
                    sin(phase * 2.9f) * 0.10f +
                    sin(phase * 5.7f + 1.4f) * 0.08f)
                    .coerceIn(0.46f, 0.92f)
            } else {
                0.38f
            }

            val primary = lerp(palette.dominant, Color.White, 0.10f)
            val secondary = lerp(palette.muted, palette.dominant, 0.20f)
            val accent = lerp(palette.accent, Color.White, 0.08f)
            val dotStep = 8.4.dp.toPx()
            val dotCount = max(38, (trackWidth / dotStep).roundToInt()).coerceAtMost(128)
            val maxDotIndex = (dotCount - 1).coerceAtLeast(1)

            for (index in 0 until dotCount) {
                val u = index / maxDotIndex.toFloat()
                val x = trackWidth * u
                val active = u <= displayProgress
                val distanceToHead = abs(u - displayProgress)
                val nearHead = (1f - distanceToHead / 0.07f).coerceIn(0f, 1f)
                val phaseA = sin(phase * 4.2f + index * 0.42f) * 0.5f + 0.5f
                val phaseB = sin(phase * 8.1f + index * 0.91f) * 0.5f + 0.5f
                val y = centerY + (phaseA - 0.5f) * 9.2.dp.toPx() * energy * if (active) 1f else 0.18f
                val radius = if (active) {
                    2.25.dp.toPx() +
                        phaseA * 1.35.dp.toPx() * energy +
                        nearHead * 1.75.dp.toPx() +
                        if (scrubbing) nearHead * 0.8.dp.toPx() else 0f
                } else {
                    1.85.dp.toPx() + phaseB * 0.28.dp.toPx()
                }
                val dotColor = when {
                    !active -> Color.White.copy(alpha = 0.23f)
                    u < 0.48f -> lerp(primary, secondary, (u / 0.48f).coerceIn(0f, 1f))
                    else -> lerp(secondary, accent, ((u - 0.48f) / 0.52f).coerceIn(0f, 1f))
                }

                drawCircle(
                    color = dotColor.copy(alpha = if (active) 0.72f + nearHead * 0.20f else 0.48f),
                    radius = radius,
                    center = Offset(x, y),
                )
                if (active && (index % 5 == 0 || nearHead > 0.35f)) {
                    drawLine(
                        color = dotColor.copy(alpha = 0.07f + nearHead * 0.10f),
                        start = Offset(x, y + radius + 4.dp.toPx()),
                        end = Offset(x, centerY + 24.dp.toPx() + phaseA * 6.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        if (showBadges) {
            Badge(formatDuration(displayedPositionMillis), Modifier.align(Alignment.BottomStart))
            Badge(durationLabel, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun PlayerProgressSurface(
    durationMillis: Long,
    durationLabel: String,
    progressController: PlaybackProgressController,
    waveform: List<Float>,
    playing: Boolean,
    waveformAnimating: Boolean,
    modifier: Modifier,
    onSeek: (Float) -> Unit,
    style: WaveformStyle,
    palette: AdaptivePalette,
    showBadges: Boolean,
) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("player_progress")
    }
    val positionMillis by progressController.positionMillis.collectAsState()
    var scrubPreviewProgress by remember(durationMillis) { mutableStateOf<Float?>(null) }
    val displayedProgress = scrubPreviewProgress ?: playbackProgressFraction(positionMillis, durationMillis)
    val displayedPositionMillis = scrubPreviewProgress
        ?.let { preview -> playbackPositionFromProgress(durationMillis, preview) }
        ?: positionMillis
    Box(modifier) {
        Waveform(
            waveform,
            displayedProgress,
            playing,
            Modifier.fillMaxSize(),
            onSeek = onSeek,
            onSeekPreview = { preview -> scrubPreviewProgress = preview?.coerceIn(0f, 1f) },
            style = style,
            palette = palette,
            animate = waveformAnimating,
        )
        if (showBadges) {
            Badge(formatDuration(displayedPositionMillis), Modifier.align(Alignment.BottomStart))
            Badge(durationLabel, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun SyncedLyricsList(lines: List<LyricsLine>, progressController: PlaybackProgressController) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("lyrics_progress")
    }
    val positionMillis by progressController.positionMillis.collectAsState()
    val activeLine = lines.indexOfLast { positionMillis >= it.startMillis }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(activeLine) {
        if (activeLine >= 0) listState.animateScrollToItem((activeLine - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lines) { line ->
            val active = line == lines.getOrNull(activeLine)
            Text(
                line.text,
                color = if (active) Color.White else Muted,
                fontSize = if (active) 17.sp else 14.sp,
                lineHeight = if (active) 21.sp else 18.sp,
                fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlayerMiniPreview(
    track: Track,
    durationMillis: Long,
    progressController: PlaybackProgressController,
    playing: Boolean,
    screen: Screen,
    appearance: MiniPlayerAppearanceSettings = MiniPlayerAppearanceSettings(),
    dockAlpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val miniShape = RoundedCornerShape(30.dp)
    val miniAppearance = appearance.normalized()
    val paletteSource = rememberAlbumPalette(track.album)
    val miniPalette = remember(track.album.key, paletteSource) {
        miniPlayerGlassPalette(track.album, paletteSource)
    }
    val textColors = miniPlayerTextColors(miniAppearance)
    val positionMillis by progressController.positionMillis.collectAsState()
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Art(track.album, Modifier.size(38.dp), 9.dp, useCase = ArtworkUseCase.SongListThumbnail)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = textColors.primary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = textColors.secondary, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PulseIcon(if (playing) DeckIcon.Pause else DeckIcon.Play, textColors.primary, Modifier.size(25.dp))
            }
            Spacer(Modifier.height(5.dp))
            Progress(playbackProgressFraction(positionMillis, durationMillis), height = 10.dp, thumbRadius = 4.dp, minimal = true)
            Spacer(Modifier.height(6.dp))
            MiniNavGlass(
                modifier = Modifier.graphicsLayer { alpha = dockAlpha },
                containerAlpha = miniPlayerNavContainerAlpha(miniAppearance),
                borderAlpha = miniPlayerNavBorderAlpha(miniAppearance),
            ) {
                BottomNav(screen, {}, height = 48.dp, iconWidth = 54.dp, iconHeight = 48.dp, iconSize = 25.dp, plainSelection = true)
            }
        }
    }
}

internal fun miniPlayerGlassPalette(album: Album, paletteSource: Pair<Color, Color>): AdaptivePalette =
    adaptivePaletteFromColors(paletteSource.first, paletteSource.second, album.tint, album.alt)

internal data class MiniPlayerTextColors(
    val primary: Color,
    val secondary: Color,
)

private const val DefaultMiniPlayerTransparency = 0.72f
private const val DefaultMiniPlayerAlbumTintStrength = 0.85f
private const val DefaultMiniPlayerBorderStrength = 0.35f

private fun Color.scaledAlpha(scale: Float): Color =
    copy(alpha = (alpha * scale).coerceIn(0f, 1f))

private fun MiniPlayerAppearanceSettings.alphaScale(): Float =
    (transparency / DefaultMiniPlayerTransparency).coerceIn(0f, 1.45f)

private fun MiniPlayerAppearanceSettings.borderScale(): Float =
    (borderStrength / DefaultMiniPlayerBorderStrength).coerceIn(0f, 2f)

private fun MiniPlayerAppearanceSettings.tintScale(): Float =
    (albumTintStrength / DefaultMiniPlayerAlbumTintStrength).coerceIn(0f, 1f)

private fun adaptiveMiniColor(color: Color, appearance: MiniPlayerAppearanceSettings): Color =
    lerp(Color.Black, color, appearance.tintScale())

internal fun miniPlayerGlassBaseColors(palette: AdaptivePalette, alphaScale: Float = 1f): List<Color> =
    listOf(
        lerp(palette.dominant, Color.White, 0.14f).copy(alpha = 0.56f).scaledAlpha(alphaScale),
        lerp(palette.muted, palette.dominant, 0.22f).copy(alpha = 0.48f).scaledAlpha(alphaScale),
        palette.deep.copy(alpha = 0.78f).scaledAlpha(alphaScale),
    )

internal fun miniPlayerGlassOverlayColors(palette: AdaptivePalette, alphaScale: Float = 1f): List<Color> =
    listOf(
        lerp(palette.dominant, Color.White, 0.08f).copy(alpha = 0.38f).scaledAlpha(alphaScale),
        palette.accent.copy(alpha = 0.30f).scaledAlpha(alphaScale),
        Color.Black.copy(alpha = 0.30f).scaledAlpha(alphaScale),
    )

internal fun miniPlayerGlassBaseBrush(palette: AdaptivePalette): Brush =
    Brush.verticalGradient(miniPlayerGlassBaseColors(palette))

internal fun miniPlayerGlassOverlayBrush(palette: AdaptivePalette): Brush =
    Brush.verticalGradient(miniPlayerGlassOverlayColors(palette))

internal fun miniPlayerGlassBorderBrush(palette: AdaptivePalette): Brush =
    Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.38f),
            palette.accent.copy(alpha = 0.26f),
            Color.Black.copy(alpha = 0.18f),
        ),
    )

internal fun miniPlayerSurfaceBaseBrush(
    palette: AdaptivePalette,
    appearance: MiniPlayerAppearanceSettings,
): Brush =
    Brush.verticalGradient(miniPlayerSurfaceBaseColors(palette, appearance.normalized()))

internal fun miniPlayerSurfaceOverlayBrush(
    palette: AdaptivePalette,
    appearance: MiniPlayerAppearanceSettings,
): Brush =
    Brush.verticalGradient(miniPlayerSurfaceOverlayColors(palette, appearance.normalized()))

internal fun miniPlayerSurfaceBorderBrush(
    palette: AdaptivePalette,
    appearance: MiniPlayerAppearanceSettings,
): Brush {
    val normalized = appearance.normalized()
    if (normalized.style == MiniPlayerAppearanceStyle.SolidBlack) {
        return Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val borderScale = normalized.borderScale()
    val accent = if (normalized.style == MiniPlayerAppearanceStyle.AdaptiveGlass) {
        adaptiveMiniColor(palette.accent, normalized)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    return Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.38f).scaledAlpha(borderScale),
            accent.copy(alpha = if (normalized.style == MiniPlayerAppearanceStyle.AdaptiveGlass) 0.26f else 0.16f).scaledAlpha(borderScale),
            Color.Black.copy(alpha = 0.18f).scaledAlpha(borderScale),
        ),
    )
}

internal fun miniPlayerSurfaceBaseColors(
    palette: AdaptivePalette,
    appearance: MiniPlayerAppearanceSettings,
): List<Color> {
    val normalized = appearance.normalized()
    if (normalized.style == MiniPlayerAppearanceStyle.SolidBlack) {
        return listOf(Color.Black, Color.Black)
    }
    val alphaScale = normalized.alphaScale()
    return when (normalized.style) {
        MiniPlayerAppearanceStyle.AdaptiveGlass -> {
            val dominant = adaptiveMiniColor(palette.dominant, normalized)
            val muted = adaptiveMiniColor(palette.muted, normalized)
            val deep = adaptiveMiniColor(palette.deep, normalized)
            listOf(
                lerp(dominant, Color.White, 0.14f).copy(alpha = 0.56f).scaledAlpha(alphaScale),
                lerp(muted, dominant, 0.22f).copy(alpha = 0.48f).scaledAlpha(alphaScale),
                deep.copy(alpha = 0.78f).scaledAlpha(alphaScale),
            )
        }
        MiniPlayerAppearanceStyle.DeepBlackGlass -> listOf(
            Color(0xFF090A0D).copy(alpha = 0.68f).scaledAlpha(alphaScale),
            Color(0xFF050506).copy(alpha = 0.74f).scaledAlpha(alphaScale),
            Color.Black.copy(alpha = 0.88f).scaledAlpha(alphaScale),
        )
        MiniPlayerAppearanceStyle.SolidBlack -> listOf(Color.Black, Color.Black)
    }
}

internal fun miniPlayerSurfaceOverlayColors(
    palette: AdaptivePalette,
    appearance: MiniPlayerAppearanceSettings,
): List<Color> {
    val normalized = appearance.normalized()
    if (normalized.style == MiniPlayerAppearanceStyle.SolidBlack) {
        return listOf(Color.Transparent, Color.Transparent)
    }
    val alphaScale = normalized.alphaScale()
    return when (normalized.style) {
        MiniPlayerAppearanceStyle.AdaptiveGlass -> {
            val dominant = adaptiveMiniColor(palette.dominant, normalized)
            val accent = adaptiveMiniColor(palette.accent, normalized)
            listOf(
                lerp(dominant, Color.White, 0.08f).copy(alpha = 0.38f).scaledAlpha(alphaScale),
                accent.copy(alpha = 0.30f).scaledAlpha(alphaScale),
                Color.Black.copy(alpha = 0.30f).scaledAlpha(alphaScale),
            )
        }
        MiniPlayerAppearanceStyle.DeepBlackGlass -> listOf(
            Color.White.copy(alpha = 0.07f).scaledAlpha(alphaScale),
            Color.White.copy(alpha = 0.035f).scaledAlpha(alphaScale),
            Color.Black.copy(alpha = 0.34f).scaledAlpha(alphaScale),
        )
        MiniPlayerAppearanceStyle.SolidBlack -> listOf(Color.Transparent, Color.Transparent)
    }
}

internal fun miniPlayerTextColors(appearance: MiniPlayerAppearanceSettings): MiniPlayerTextColors {
    val normalized = appearance.normalized()
    return if (normalized.useHighContrastText || normalized.style == MiniPlayerAppearanceStyle.SolidBlack) {
        MiniPlayerTextColors(Color.White, Color.White.copy(alpha = 0.92f))
    } else {
        MiniPlayerTextColors(Color.White.copy(alpha = 0.88f), Color.White.copy(alpha = 0.70f))
    }
}

internal fun miniPlayerNavContainerAlpha(appearance: MiniPlayerAppearanceSettings): Float {
    val normalized = appearance.normalized()
    if (normalized.style == MiniPlayerAppearanceStyle.SolidBlack) return 1f
    return (0.16f + 0.80f * normalized.alphaScale().coerceIn(0f, 1f)).coerceIn(0f, 1f)
}

internal fun miniPlayerNavBorderAlpha(appearance: MiniPlayerAppearanceSettings): Float {
    val normalized = appearance.normalized()
    if (normalized.style == MiniPlayerAppearanceStyle.SolidBlack) return 0f
    return (0.08f * normalized.borderScale()).coerceIn(0f, 0.18f)
}

