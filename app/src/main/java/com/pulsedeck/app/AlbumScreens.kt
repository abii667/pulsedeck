package com.pulsedeck.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.library.normalizedFolderPath
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val AlbumHeroOpenMillis = 460
private const val AlbumHeroReturnBaseMillis = 380
private const val AlbumHeroReturnMaxMillis = 560
private const val AlbumDetailSwipeDismissDistance = 520f
private const val AlbumDetailSwipeDismissThreshold = 118f
private const val AlbumDetailSwipeMaxDistance = 680f

private fun LayoutCoordinates.albumBoundsInRoot(): AlbumTileBounds {
    val position = positionInRoot()
    return AlbumTileBounds(
        left = position.x,
        top = position.y,
        width = size.width.toFloat(),
        height = size.height.toFloat(),
    )
}

@Composable
internal fun Albums(
    albums: List<Album>,
    gridState: LazyGridState,
    onBack: (Float) -> Unit,
    onBackDrag: (Float) -> Unit = {},
    onBackDragCancel: () -> Unit = {},
    onAlbum: (Album) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    interactionEnabled: Boolean = true,
    presentationAlpha: Float = 1f,
    hiddenCategoryHeaderName: String? = null,
    hiddenAlbumKey: String? = null,
    onCategoryHeaderBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
    onAlbumBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
) {
    val motion = LocalPulseMotionSpec.current
    val haptic = LocalHapticFeedback.current
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var alphabetActive by remember { mutableStateOf(false) }
    var alphabetPreviewLabel by remember { mutableStateOf("A") }
    var alphabetScrollJob by remember { mutableStateOf<Job?>(null) }
    val alphabetScope = rememberCoroutineScope()
    val alphabetLabels = remember { listOf("^", "0") + ('A'..'Z').map { it.toString() } + listOf("#") }
    val alphabetItemHeight = 14.5f
    val alphabetItemSpacing = 2.3f
    val alphabetVerticalPadding = 6f
    val alphabetTouchHeight = alphabetLabels.size * alphabetItemHeight + (alphabetLabels.size - 1) * alphabetItemSpacing + alphabetVerticalPadding * 2f
    val alphabetRailWidth by animateFloatAsState(if (alphabetActive) 38f else 15f, tween(motion.duration(PulseMotion.Duration.Short), easing = PulseMotion.Easing.Standard), label = "alphabetRailWidth")
    val alphabetFontSize by animateFloatAsState(if (alphabetActive) 11.2f else 7.2f, tween(motion.duration(PulseMotion.Duration.Short), easing = PulseMotion.Easing.Standard), label = "alphabetFontSize")
    val alphabetRailAlpha by animateFloatAsState(if (alphabetActive) 0.62f else 0.30f, tween(motion.duration(PulseMotion.Duration.Short), easing = PulseMotion.Easing.Standard), label = "alphabetRailAlpha")
    val easedX by animateFloatAsState(
        targetValue = dragX,
        animationSpec = if (motion.disabled) tween<Float>(0) else spring(stiffness = PulseMotion.Spring.RouteStiffness, dampingRatio = PulseMotion.Spring.RouteDamping),
        label = "albumsBackDrag",
    )
    val displayedX = if (dragging) dragX else easedX
    val dragProgress = (abs(displayedX) / 520f).coerceIn(0f, 1f)
    fun leaveToLibrary() {
        dragX = 0f
        dragging = false
        onBackDragCancel()
        onBack(0f)
    }
    fun targetAlbumIndexFor(label: String): Int = when (label) {
            "^" -> 0
            "0" -> albums.indexOfFirst { it.title.trim().firstOrNull()?.isDigit() == true }
            "#" -> albums.indexOfFirst {
                val first = it.title.trim().firstOrNull()
                first != null && !first.isLetterOrDigit()
            }
            else -> albums.indexOfFirst {
                it.title.trim().firstOrNull()?.uppercaseChar()?.toString() == label
            }
        }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .graphicsLayer {
                    translationX = displayedX
                    alpha = presentationAlpha * (1f - dragProgress * 0.12f)
                    scaleX = 1f - dragProgress * 0.01f
                    scaleY = 1f - dragProgress * 0.01f
                }
                .then(
                    if (interactionEnabled) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    onBackDrag(0f)
                                },
                                onHorizontalDrag = { _, amount ->
                                    dragX = (dragX + amount).coerceIn(-520f, 520f)
                                    onBackDrag((abs(dragX) / 520f).coerceIn(0f, 1f))
                                },
                                onDragEnd = {
                                    val shouldLeave = abs(dragX) > 130f
                                    val releaseProgress = (abs(dragX) / 520f).coerceIn(0f, 1f)
                                    dragging = false
                                    dragX = 0f
                                    if (shouldLeave) {
                                        onBack(releaseProgress)
                                    } else {
                                        onBackDragCancel()
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    dragX = 0f
                                    onBackDragCancel()
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 190.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)) {
                        PageNavigationHeader("Albums", onBack = { leaveToLibrary() }, subtitle = "Library")
                        Row(
                            modifier = Modifier
                                .graphicsLayer { alpha = if (hiddenCategoryHeaderName == "Albums") 0f else 1f }
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    onCategoryHeaderBoundsChanged(
                                        "Albums",
                                        AlbumTileBounds(
                                            left = position.x,
                                            top = position.y,
                                            width = coordinates.size.width.toFloat(),
                                            height = coordinates.size.height.toFloat(),
                                        ),
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryArtworkDisc("Albums", Color(0xFF6E63F2), DeckIcon.Disc, Modifier.size(48.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Albums", color = Color.White, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconPill(DeckIcon.Shuffle, { albums.firstOrNull()?.let { onAlbum(it) } }, Modifier.width(48.dp))
                            IconPill(DeckIcon.Play, { albums.firstOrNull()?.let { onAlbum(it) } }, Modifier.width(48.dp))
                            IconPill(DeckIcon.Search, onSearch, Modifier.width(48.dp))
                            Spacer(Modifier.weight(1f))
                            IconCircle(DeckIcon.More, onSettings, 44.dp)
                        }
                    }
                }
                albums.forEachIndexed { index, album ->
                    item(key = album.key) {
                        AnimatedEntrance(index + 1, animate = false) {
                            val hidden = album.key == hiddenAlbumKey
                            AlbumTile(
                                album = album,
                                enabled = interactionEnabled && !hidden,
                                visible = !hidden,
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    onAlbumBoundsChanged(
                                        album.key,
                                        AlbumTileBounds(
                                            left = position.x,
                                            top = position.y,
                                            width = coordinates.size.width.toFloat(),
                                            height = coordinates.size.height.toFloat(),
                                        ),
                                    )
                                },
                            ) {
                                onAlbum(album)
                            }
                        }
                    }
                }
            }
            if (alphabetActive) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = (alphabetRailWidth + 24f).dp)
                        .size(70.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(0.22f),
                                    Color.Black.copy(0.78f),
                                    Color.Black.copy(0.92f),
                                ),
                            ),
                        )
                        .border(1.dp, Color.White.copy(0.26f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(alphabetPreviewLabel, color = Color.White, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(56.dp)
                    .height(alphabetTouchHeight.dp)
                    .then(
                        if (interactionEnabled) {
                            Modifier.pointerInput(Unit) {
                                fun labelForOffset(y: Float): String {
                                    val itemHeightPx = alphabetItemHeight.dp.toPx()
                                    val spacingPx = alphabetItemSpacing.dp.toPx()
                                    val topPaddingPx = alphabetVerticalPadding.dp.toPx()
                                    val index = ((y - topPaddingPx) / (itemHeightPx + spacingPx)).toInt().coerceIn(0, alphabetLabels.lastIndex)
                                    return alphabetLabels[index]
                                }
                                fun jumpImmediately(label: String) {
                                    alphabetPreviewLabel = label
                                    val targetAlbumIndex = targetAlbumIndexFor(label)
                                    if (targetAlbumIndex >= 0) {
                                        alphabetScrollJob?.cancel()
                                        alphabetScrollJob = alphabetScope.launch {
                                            gridState.scrollToItem(targetAlbumIndex + 1)
                                        }
                                    }
                                }
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    alphabetActive = true
                                    var lastLabel: String? = null
                                    fun updateAt(y: Float) {
                                        val label = labelForOffset(y)
                                        alphabetPreviewLabel = label
                                        if (label != lastLabel) {
                                            lastLabel = label
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            jumpImmediately(label)
                                        }
                                    }
                                    updateAt(down.position.y)
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                        if (change != null && change.pressed) {
                                            updateAt(change.position.y)
                                        }
                                    } while (event.changes.any { it.pressed })
                                    alphabetActive = false
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(
                    Modifier
                        .padding(end = 1.dp)
                        .width(alphabetRailWidth.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alphabetRailAlpha * 0.30f),
                                    Color.Black.copy(alphabetRailAlpha),
                                    Color.Black.copy(alphabetRailAlpha * 0.86f),
                                ),
                            ),
                        )
                        .border(1.dp, Color.White.copy(if (alphabetActive) 0.24f else 0.10f), RoundedCornerShape(14.dp))
                        .padding(vertical = alphabetVerticalPadding.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(alphabetItemSpacing.dp),
                ) {
                    alphabetLabels.forEach { label ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(alphabetItemHeight.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = Color.White.copy(if (alphabetActive) 0.92f else 0.50f), fontSize = alphabetFontSize.sp, lineHeight = (alphabetFontSize + 1f).sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(
    album: Album,
    enabled: Boolean = true,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .pressScaleEffect(interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Art(album, Modifier.fillMaxWidth().aspectRatio(1f), 20.dp)
        Text(album.title, color = Color.White, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
        Text(album.artist, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, end = 4.dp))
    }
}

@Composable
internal fun AlbumDetail(
    album: Album,
    tracks: List<Track>,
    currentTrackKey: String,
    playing: Boolean,
    closeRequestKey: Int,
    closeRequestStartProgress: Float = 0f,
    returnTargetBounds: AlbumTileBounds?,
    listState: LazyListState = rememberLazyListState(),
    handoffToPlayer: Boolean = false,
    handoffTrackKey: String? = null,
    onRequestCloseToGrid: (Float) -> Unit,
    onBack: () -> Unit,
    onTrackHandoffBoundsChanged: (PlayerLaunchHandoff) -> Unit = {},
    onTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onDeleteSelected: (List<Track>) -> Unit,
    onChangeArt: (Album, List<Uri>) -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    var dragX by remember(album.key) { mutableFloatStateOf(0f) }
    var dragging by remember(album.key) { mutableStateOf(false) }
    var closing by remember(album.key) { mutableStateOf(false) }
    var closeDirection by remember(album.key) { mutableFloatStateOf(0f) }
    var closeStartProgress by remember(album.key) { mutableFloatStateOf(0f) }
    var entered by remember(album.key) { mutableStateOf(false) }
    var searchOpen by remember(album.key) { mutableStateOf(false) }
    var optionsOpen by remember(album.key) { mutableStateOf(false) }
    var selectionMode by remember(album.key) { mutableStateOf(false) }
    var selectedTrackKeys by remember(album.key) { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeleteSelectedOpen by remember(album.key) { mutableStateOf(false) }
    var albumInfoDialog by remember(album.key) { mutableStateOf<InfoDialogState?>(null) }
    var observedCloseRequestKey by remember(album.key) { mutableIntStateOf(closeRequestKey) }
    val density = LocalDensity.current
    val albumArtSourceUris = remember(album.key, tracks) {
        (album.artSourceUris + listOfNotNull(album.sourceUri) + tracks.mapNotNull { it.uri }).distinct()
    }
    val paletteSource = rememberAlbumPalette(album)
    val selectedTracks = remember(tracks, selectedTrackKeys) {
        tracks.filter { it.stableKey() in selectedTrackKeys }
    }
    val selectedDeletableCount = selectedTracks.count { it.uri != null }
    fun clearSelection() {
        selectionMode = false
        selectedTrackKeys = emptySet()
        confirmDeleteSelectedOpen = false
    }
    fun toggleTrackSelection(target: Track) {
        val key = target.stableKey()
        selectedTrackKeys = if (key in selectedTrackKeys) {
            selectedTrackKeys - key
        } else {
            selectedTrackKeys + key
        }
    }
    LaunchedEffect(tracks) {
        val availableKeys = tracks.map { it.stableKey() }.toSet()
        selectedTrackKeys = selectedTrackKeys.intersect(availableKeys)
    }
    val targetAlbumPalette = remember(album.key, paletteSource) {
        adaptivePaletteFromColors(paletteSource.first, paletteSource.second, album.tint, album.alt)
    }
    val albumDominant by animateColorAsState(
        targetValue = targetAlbumPalette.dominant,
        animationSpec = tween(durationMillis = motion.duration(PulseMotion.Duration.Medium), easing = PulseMotion.Easing.Standard),
        label = "albumDetailDominant",
    )
    val albumMuted by animateColorAsState(
        targetValue = targetAlbumPalette.muted,
        animationSpec = tween(durationMillis = motion.duration(PulseMotion.Duration.Medium), easing = PulseMotion.Easing.Standard),
        label = "albumDetailMuted",
    )
    val albumAccent by animateColorAsState(
        targetValue = targetAlbumPalette.accent,
        animationSpec = tween(durationMillis = motion.duration(PulseMotion.Duration.Medium), easing = PulseMotion.Easing.Standard),
        label = "albumDetailAccent",
    )
    val albumDeep by animateColorAsState(
        targetValue = targetAlbumPalette.deep,
        animationSpec = tween(durationMillis = motion.duration(PulseMotion.Duration.Medium), easing = PulseMotion.Easing.Standard),
        label = "albumDetailDeep",
    )
    val albumPalette = remember(albumDominant, albumMuted, albumAccent, albumDeep) {
        AdaptivePalette(albumDominant, albumMuted, albumAccent, albumDeep)
    }
    var heroReturnStartBounds by remember(album.key) { mutableStateOf<AlbumTileBounds?>(null) }
    var detailRootOffset by remember(album.key) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(album.key) { entered = true }
    val returnTravelPx = remember(heroReturnStartBounds, returnTargetBounds) {
        val start = heroReturnStartBounds
        val target = returnTargetBounds
        if (start == null || target == null) {
            0f
        } else {
            max(abs(start.left - target.left), abs(start.top - target.top))
        }
    }
    val returnDuration = (AlbumHeroReturnBaseMillis + (returnTravelPx / 8f).roundToInt())
        .coerceIn(AlbumHeroReturnBaseMillis, AlbumHeroReturnMaxMillis)
    val closeProgress by animateFloatAsState(
        targetValue = if (closing) 1f else 0f,
        animationSpec = when {
            motion.disabled -> tween<Float>(0)
            else -> tween(durationMillis = motion.duration(returnDuration), easing = PulseMotion.Easing.Standard)
        },
        label = "albumCoverReturn",
        finishedListener = { if (it >= 1f) onBack() },
    )
    val openProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = motion.duration(AlbumHeroOpenMillis), easing = PulseMotion.Easing.Standard),
        label = "albumCoverOpen",
    )
    val easedX by animateFloatAsState(
        targetValue = dragX,
        animationSpec = if (motion.disabled) tween<Float>(0) else spring(stiffness = PulseMotion.Spring.GestureStiffness, dampingRatio = PulseMotion.Spring.GestureDamping),
        label = "albumBackDrag",
    )
    val displayedX = if (dragging) dragX else easedX
    val dragProgress = (abs(displayedX) / AlbumDetailSwipeDismissDistance).coerceIn(0f, 1f)
    val fingerProgress = (abs(dragX) / AlbumDetailSwipeDismissDistance).coerceIn(0f, 1f)
    val interactiveProgress = if (dragging) fingerProgress else dragProgress
    val returningToGrid = closing || dragging || interactiveProgress > 0.001f
    val returnProgress = (if (closing) closeStartProgress + (1f - closeStartProgress) * closeProgress else interactiveProgress).coerceIn(0f, 1f)
    val sceneProgress = if (returningToGrid) (1f - returnProgress).coerceIn(0f, 1f) else openProgress.coerceIn(0f, 1f)
    val pageFade = sceneProgress
    val panelLiftPx = with(density) { PulseMotion.Distance.PanelLift.dp.toPx() }
    val returnEase = PulseMotion.Easing.Standard.transform(returnProgress)
    val actionFade = albumMotionStage(sceneProgress, 0.52f, 0.88f)
    val trackFade = albumMotionStage(sceneProgress, 0.10f, 0.78f)
    val listRevealProgress = albumMotionStage(sceneProgress, 0.04f, 0.82f)
    val detailHorizontalOffset = if (closing) {
        closeDirection * returnProgress * AlbumDetailSwipeDismissDistance
    } else {
        displayedX
    }
    val activeReturnDirection = when {
        closeDirection < 0f || displayedX < 0f -> -1f
        closeDirection > 0f || displayedX > 0f -> 1f
        else -> 0f
    }
    val handoffAlpha by animateFloatAsState(
        targetValue = if (handoffToPlayer) 0f else 1f,
        animationSpec = tween(durationMillis = motion.duration(PulseMotion.Duration.Short), easing = PulseMotion.Easing.Standard),
        label = "albumPlayerHandoff",
    )
    fun closeToGrid(startProgress: Float = 0f, direction: Float = 0f) {
        dragging = false
        closeDirection = direction.coerceIn(-1f, 1f)
        closeStartProgress = startProgress.coerceIn(0f, 1f)
        dragX = 0f
        closing = true
    }
    fun requestCloseToGrid(startProgress: Float = 0f, direction: Float = 0f) {
        val progress = startProgress.coerceIn(0f, 1f)
        onRequestCloseToGrid(progress)
        closeToGrid(progress, direction)
    }
    LaunchedEffect(closeRequestKey, album.key) {
        if (closeRequestKey != observedCloseRequestKey) {
            observedCloseRequestKey = closeRequestKey
            if (!closing) closeToGrid(closeRequestStartProgress)
        }
    }
    BackHandler(enabled = selectionMode) {
        clearSelection()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                detailRootOffset = coordinates.positionInRoot()
            }
            .pointerInput(album.key, closing) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        closeDirection = 0f
                    },
                    onHorizontalDrag = { _, amount ->
                        if (!closing) dragX = (dragX + amount).coerceIn(-AlbumDetailSwipeMaxDistance, AlbumDetailSwipeMaxDistance)
                    },
                    onDragEnd = {
                        val releaseProgress = (abs(dragX) / AlbumDetailSwipeDismissDistance).coerceIn(0f, 1f)
                        val releaseDirection = when {
                            dragX < 0f -> -1f
                            dragX > 0f -> 1f
                            else -> 0f
                        }
                        dragging = false
                        if (abs(dragX) > AlbumDetailSwipeDismissThreshold) {
                            requestCloseToGrid(releaseProgress, releaseDirection)
                        } else {
                            closeDirection = 0f
                            dragX = 0f
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        closeDirection = 0f
                        dragX = 0f
                    },
                )
            },
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val heroWidthPx = screenWidthPx
        val coverWidthPx = heroWidthPx
        val coverHeightPx = with(density) { 294.dp.toPx() }
        val coverLeftPx = 0f
        val coverTopPx = with(density) { 10.dp.toPx() }
        val actionDockLeftPx = with(density) { 20.dp.toPx() }
        val actionDockTopPx = coverTopPx + coverHeightPx + with(density) { 14.dp.toPx() }
        val actionDockWidthPx = screenWidthPx - with(density) { 40.dp.toPx() }
        val collapseRangePx = with(density) { 260.dp.toPx() }
        val heroSpacerHeight = 334.dp
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val heroSpacerPx = with(density) { heroSpacerHeight.toPx() }
        val trackRowsHeightPx = with(density) {
            80.dp.toPx() * tracks.size + 8.dp.toPx() * (tracks.size - 1).coerceAtLeast(0)
        }
        val albumListBottomPadding = if (tracks.size <= 4) 288.dp else 184.dp
        val estimatedScrollablePx = (
            heroSpacerPx +
                with(density) { 18.dp.toPx() } +
                trackRowsHeightPx +
                with(density) { albumListBottomPadding.toPx() } -
                viewportHeightPx
            ).coerceAtLeast(0f)
        val effectiveCollapseRangePx = if (estimatedScrollablePx > 0f && estimatedScrollablePx < collapseRangePx) {
            estimatedScrollablePx
        } else {
            collapseRangePx
        }
        val scrollPx = if (listState.firstVisibleItemIndex == 0) {
            listState.firstVisibleItemScrollOffset.toFloat()
        } else {
            effectiveCollapseRangePx
        }
        val hubProgress = if (returningToGrid) 0f else (scrollPx / effectiveCollapseRangePx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val compactCoverSizePx = with(density) { 92.dp.toPx() }
        val compactLeftPx = with(density) { 14.dp.toPx() }
        val compactTopPx = with(density) { 12.dp.toPx() }
        val currentCoverLeftPx = coverLeftPx + (compactLeftPx - coverLeftPx) * hubProgress
        val currentCoverTopPx = coverTopPx + (compactTopPx - coverTopPx) * hubProgress
        val currentCoverWidthPx = coverWidthPx + (compactCoverSizePx - coverWidthPx) * hubProgress
        val currentCoverHeightPx = coverHeightPx + (compactCoverSizePx - coverHeightPx) * hubProgress
        val currentCoverCorner = with(density) { (28.dp.toPx() + (22.dp.toPx() - 28.dp.toPx()) * hubProgress).toDp() }
        val heroLabelLeftPx = with(density) { 22.dp.toPx() }
        val heroLabelTopPx = coverTopPx + coverHeightPx - with(density) { 78.dp.toPx() }
        val compactLabelLeftPx = compactLeftPx + compactCoverSizePx + with(density) { 12.dp.toPx() }
        val compactLabelTopPx = compactTopPx + with(density) { 14.dp.toPx() }
        val labelLeftPx = heroLabelLeftPx + (compactLabelLeftPx - heroLabelLeftPx) * hubProgress
        val labelTopPx = heroLabelTopPx + (compactLabelTopPx - heroLabelTopPx) * hubProgress
        val compactMetaWidthPx = (screenWidthPx * 0.28f).coerceIn(
            with(density) { 88.dp.toPx() },
            with(density) { 116.dp.toPx() },
        )
        val compactMetaRightPx = with(density) { 20.dp.toPx() }
        val compactMetaLeftPx = screenWidthPx - compactMetaWidthPx - compactMetaRightPx
        val compactMetaTopPx = with(density) { 30.dp.toPx() }
        val expandedLabelWidthPx = heroWidthPx - with(density) { 44.dp.toPx() }
        val compactLabelWidthPx = (compactMetaLeftPx - compactLabelLeftPx - with(density) { 8.dp.toPx() })
            .coerceAtLeast(with(density) { 64.dp.toPx() })
        val labelWidthPx = expandedLabelWidthPx + (compactLabelWidthPx - expandedLabelWidthPx) * hubProgress
        val titleFontSp = 22f + (16f - 22f) * hubProgress
        val titleLineHeightSp = 25f + (19f - 25f) * hubProgress
        val artistFontSp = 14f + (11.5f - 14f) * hubProgress
        val dockAlpha = actionFade * (1f - hubProgress * 1.7f).coerceIn(0f, 1f) * handoffAlpha
        val backAlpha = (1f - hubProgress * 1.3f).coerceIn(0f, 1f) * handoffAlpha
        val trackListAlpha = trackFade * (0.72f + handoffAlpha * 0.28f)
        val listRevealDistancePx = with(density) { 54.dp.toPx() }
        val listPageLiftPx = with(density) { 18.dp.toPx() }
        val rowCascadeBasePx = with(density) { 12.dp.toPx() }
        val rowCascadeStepPx = with(density) { 2.dp.toPx() }
        val rowExitSlidePx = with(density) { 28.dp.toPx() }
        val listReturnSkewPx = with(density) { 24.dp.toPx() }
        val hubBackdropAlpha = ((hubProgress - 0.16f) / 0.58f).coerceIn(0f, 1f) * handoffAlpha
        val hubBackdropTopPx = with(density) { 7.dp.toPx() }
        val hubBackdropLeftPx = with(density) { 8.dp.toPx() }
        val hubBackdropHeightPx = with(density) { 110.dp.toPx() }
        val hubBackdropWidthPx = screenWidthPx - with(density) { 16.dp.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (returningToGrid) pageFade else 1f }
                .background(Color.Black),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val maxSide = max(size.width, size.height)
                drawRect(
                    Brush.verticalGradient(
                        0.00f to lerp(albumPalette.dominant, Color.White, 0.06f).copy(alpha = 0.94f),
                        0.35f to albumPalette.dominant.copy(alpha = 0.84f),
                        0.68f to albumPalette.deep.copy(alpha = 0.94f),
                        1.00f to Color.Black,
                        startY = 0f,
                        endY = size.height,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to albumPalette.accent.copy(alpha = 0.42f),
                        0.58f to albumPalette.muted.copy(alpha = 0.16f),
                        1.00f to Color.Transparent,
                        center = Offset(size.width * 0.74f, size.height * 0.18f),
                        radius = maxSide * 0.70f,
                    ),
                    radius = maxSide * 0.70f,
                    center = Offset(size.width * 0.74f, size.height * 0.18f),
                )
                drawRect(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.08f),
                        0.52f to Color.Black.copy(alpha = 0.12f),
                        0.84f to Color.Black.copy(alpha = 0.66f),
                        1.00f to Color.Black.copy(alpha = 0.98f),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.12f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.14f),
                            ),
                        ),
                    ),
            )
        }
        val collapsingHeroReady = returningToGrid && returnTargetBounds != null && heroReturnStartBounds != null
        val computedHeroBounds = AlbumTileBounds(
            left = detailRootOffset.x + currentCoverLeftPx,
            top = detailRootOffset.y + currentCoverTopPx,
            width = currentCoverWidthPx,
            height = currentCoverHeightPx,
        )
        val transitionHeroBounds = heroReturnStartBounds ?: computedHeroBounds
        val openingHeroReady = !returningToGrid && openProgress < 0.995f && returnTargetBounds != null
        val sharedHeroActive = collapsingHeroReady || openingHeroReady
        val heroContentAlpha = (if (sharedHeroActive) 0f else 1f) * handoffAlpha
        val pinnedHubAlpha = if (sharedHeroActive) 0f else hubBackdropAlpha * pageFade

        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(
                        pivotFractionX = when {
                            activeReturnDirection < 0f -> 0f
                            activeReturnDirection > 0f -> 1f
                            else -> 0.5f
                        },
                        pivotFractionY = 0f,
                    )
                    translationX = if (returningToGrid) {
                        detailHorizontalOffset + activeReturnDirection * returnEase * listReturnSkewPx
                    } else {
                        0f
                    }
                    translationY = if (returningToGrid) {
                        returnEase * listPageLiftPx
                    } else {
                        (1f - listRevealProgress) * listPageLiftPx + panelLiftPx * (1f - sceneProgress)
                    }
                    alpha = if (returningToGrid) pageFade * (1f - dragProgress * 0.22f) else 1f
                    scaleX = if (returningToGrid) 1f - returnEase * 0.055f else 1f
                    scaleY = if (returningToGrid) 1f - returnEase * 0.025f else 1f
                },
            state = listState,
            contentPadding = PaddingValues(bottom = albumListBottomPadding),
        ) {
            item {
                Spacer(Modifier.height(heroSpacerHeight))
            }
            if (selectionMode) {
                item(key = "albumSelectionBar", contentType = "albumSelectionBar") {
                    AlbumSelectionBar(
                        selectedCount = selectedTracks.size,
                        totalCount = tracks.size,
                        deletableCount = selectedDeletableCount,
                        onSelectAll = {
                            selectedTrackKeys = tracks.map { it.stableKey() }.toSet()
                        },
                        onClear = { clearSelection() },
                        onDelete = {
                            if (selectedDeletableCount > 0) confirmDeleteSelectedOpen = true
                        },
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(top = 18.dp, bottom = 2.dp),
                    )
                }
            }
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.stableKey() },
                contentType = { _, _ -> "albumTrack" },
            ) { index, track ->
                val active = track.stableKey() == currentTrackKey
                val rowDelay = (index * 0.055f).coerceAtMost(0.30f)
                val rowProgress = if (returningToGrid) {
                    trackListAlpha
                } else {
                    ((trackListAlpha - rowDelay) / (1f - rowDelay).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                }
                Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = if (index == 0) 18.dp else 8.dp)
                        .graphicsLayer {
                            alpha = if (returningToGrid) trackListAlpha * rowProgress else rowProgress
                            translationY = if (returningToGrid) {
                                returnEase * rowExitSlidePx
                            } else {
                                (1f - listRevealProgress) * listRevealDistancePx
                            } + (1f - rowProgress) * (rowCascadeBasePx + index.coerceAtMost(4) * rowCascadeStepPx)
                        },
                ) {
                    AlbumTrackRow(
                        track = track,
                        index = index,
                        active = active,
                        playing = playing && active,
                        accent = album.alt,
                        contentAlpha = if (handoffToPlayer && handoffTrackKey == track.stableKey()) 0f else 1f,
                        selectionMode = selectionMode,
                        selected = track.stableKey() in selectedTrackKeys,
                        onHandoffBoundsChanged = onTrackHandoffBoundsChanged,
                        onClick = {
                            if (selectionMode) {
                                toggleTrackSelection(track)
                            } else {
                                onTrack(track)
                            }
                        },
                    )
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(hubBackdropLeftPx.roundToInt(), hubBackdropTopPx.roundToInt()) }
                .size(
                    width = with(density) { hubBackdropWidthPx.toDp() },
                    height = with(density) { hubBackdropHeightPx.toDp() },
                )
                .graphicsLayer {
                    alpha = pinnedHubAlpha
                    shadowElevation = 22f * pinnedHubAlpha
                    ambientShadowColor = Color.Black.copy(alpha = 0.54f)
                    spotShadowColor = Color.Black.copy(alpha = 0.70f)
                }
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(albumPalette.dominant, Color.Black, 0.30f).copy(alpha = 0.88f),
                            albumPalette.deep.copy(alpha = 0.76f),
                            Color.Black.copy(alpha = 0.70f),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(30.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.16f),
                            ),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(currentCoverLeftPx.roundToInt(), currentCoverTopPx.roundToInt()) }
                .size(
                    width = with(density) { currentCoverWidthPx.toDp() },
                    height = with(density) { currentCoverHeightPx.toDp() },
                )
                .onGloballyPositioned { coordinates ->
                    if (!returningToGrid || heroReturnStartBounds == null) {
                        val position = coordinates.positionInRoot()
                        heroReturnStartBounds = AlbumTileBounds(
                            left = position.x,
                            top = position.y,
                            width = coordinates.size.width.toFloat(),
                            height = coordinates.size.height.toFloat(),
                        )
                    }
                }
                .graphicsLayer {
                    alpha = if (sharedHeroActive) 0f else 1f - closeProgress * 0.02f
                    shadowElevation = 24f * (1f - hubProgress * 0.35f) * pageFade
                    ambientShadowColor = Color.Black.copy(alpha = 0.58f)
                    spotShadowColor = Color.Black.copy(alpha = 0.70f)
                }
                .clip(RoundedCornerShape(currentCoverCorner)),
        ) {
            AlbumHeroArtwork(album, Modifier.fillMaxSize(), currentCoverCorner)
            Box(
                Modifier.fillMaxSize(),
            ) {
                AlbumHeroBackPill(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp, top = 16.dp)
                        .graphicsLayer { alpha = backAlpha },
                    onClick = { requestCloseToGrid(0f, 0f) },
                )
            }
        }
        AlbumPinnedStatsPanel(
            tracks = tracks,
            modifier = Modifier
                .offset { IntOffset(compactMetaLeftPx.roundToInt(), compactMetaTopPx.roundToInt()) }
                .width(with(density) { compactMetaWidthPx.toDp() })
                .graphicsLayer {
                    alpha = pinnedHubAlpha
                    translationX = detailHorizontalOffset * 0.72f
                },
        )
        AlbumHeroActionDock(
            modifier = Modifier
                .offset { IntOffset(actionDockLeftPx.roundToInt(), actionDockTopPx.roundToInt()) }
                .width(with(density) { actionDockWidthPx.toDp() })
                .graphicsLayer {
                    alpha = dockAlpha
                    translationX = detailHorizontalOffset * 0.82f
                    translationY = (1f - actionFade) * 18f + if (returningToGrid) returnEase * 10f else 0f
                    val dockScale = if (returningToGrid) 1f - returnEase * 0.035f else 0.96f + actionFade * 0.04f
                    scaleX = dockScale
                    scaleY = dockScale
                },
            onShuffle = onShuffleAll,
            onPlay = onPlayAll,
            onSearch = { searchOpen = true },
            selectLabel = if (selectionMode) "Cancel" else "Select",
            onSelect = {
                if (selectionMode) {
                    clearSelection()
                } else {
                    selectionMode = true
                    selectedTrackKeys = emptySet()
                }
            },
            onMore = { optionsOpen = true },
        )
        AlbumHeroLabelText(
            album = album,
            titleFontSp = titleFontSp,
            titleLineHeightSp = titleLineHeightSp,
            artistFontSp = artistFontSp,
            artistLineHeightSp = artistFontSp + 3f,
            artistTopPadding = (6f * (1f - hubProgress * 0.7f)).dp,
            modifier = Modifier
                .offset { IntOffset(labelLeftPx.roundToInt(), labelTopPx.roundToInt()) }
                .width(with(density) { labelWidthPx.toDp() })
                .graphicsLayer {
                    alpha = heroContentAlpha
                    translationX = detailHorizontalOffset * 0.96f
                    translationY = if (returningToGrid) returnEase * 8f else 0f
                },
        )
        if (openingHeroReady) {
            ExpandingAlbumHero(
                album = album,
                tileBounds = returnTargetBounds,
                heroBounds = transitionHeroBounds,
                rootOffset = detailRootOffset,
                progress = openProgress,
            )
        }
        if (collapsingHeroReady) {
            CollapsingAlbumHero(
                album = album,
                startBounds = heroReturnStartBounds,
                targetBounds = returnTargetBounds,
                rootOffset = detailRootOffset,
                progress = returnProgress,
            )
        }
        if (searchOpen) {
            AlbumSearchDialog(
                album = album,
                tracks = tracks,
                currentTrackKey = currentTrackKey,
                playing = playing,
                onDismiss = { searchOpen = false },
                onTrack = {
                    searchOpen = false
                    onTrack(it)
                },
            )
        }
        if (optionsOpen) {
            AlbumOptionsDialog(
                album = album,
                tracks = tracks,
                onDismiss = { optionsOpen = false },
                onPlay = {
                    optionsOpen = false
                    onPlayAll()
                },
                onShuffle = {
                    optionsOpen = false
                    onShuffleAll()
                },
                onSearch = {
                    optionsOpen = false
                    searchOpen = true
                },
                onArtist = {
                    albumInfoDialog = InfoDialogState(
                        title = "Artist",
                        subtitle = album.artist,
                        rows = listOf(
                            "Album" to album.title,
                            "Tracks" to tracks.size.toString(),
                            "Total time" to formatDuration(tracks.sumOf { it.durationMillis }),
                        ),
                    )
                    optionsOpen = false
                },
                onMetadata = {
                    albumInfoDialog = albumInfoFor(album, tracks)
                    optionsOpen = false
                },
                onChangeArt = {
                    optionsOpen = false
                    onChangeArt(album, albumArtSourceUris)
                },
            )
        }
        if (confirmDeleteSelectedOpen) {
            ConfirmDeleteSelectedTracksDialog(
                selectedCount = selectedTracks.size,
                deletableCount = selectedDeletableCount,
                onDismiss = { confirmDeleteSelectedOpen = false },
                onConfirm = {
                    val targets = selectedTracks
                    clearSelection()
                    onDeleteSelected(targets)
                },
            )
        }
        albumInfoDialog?.let { dialog ->
            InfoDialog(dialog, onDismiss = { albumInfoDialog = null })
        }
    }
}

@Composable
private fun ExpandingAlbumHero(
    album: Album,
    tileBounds: AlbumTileBounds?,
    heroBounds: AlbumTileBounds?,
    rootOffset: Offset,
    progress: Float,
) {
    if (tileBounds == null || heroBounds == null || tileBounds.width <= 0f || heroBounds.width <= 0f) return
    val density = LocalDensity.current
    val eased = progress.coerceIn(0f, 1f)
    val tileLeft = tileBounds.left - rootOffset.x
    val tileTop = tileBounds.top - rootOffset.y
    val heroLeft = heroBounds.left - rootOffset.x
    val heroTop = heroBounds.top - rootOffset.y
    val left = tileLeft + (heroLeft - tileLeft) * eased
    val top = tileTop + (heroTop - tileTop) * eased
    val width = tileBounds.width + (heroBounds.width - tileBounds.width) * eased
    val height = tileBounds.height + (heroBounds.height - tileBounds.height) * eased
    val coverHeight = tileBounds.width + (heroBounds.height - tileBounds.width) * eased
    val coverCornerPx = with(density) {
        val start = 20.dp.toPx()
        val end = 28.dp.toPx()
        start + (end - start) * eased
    }
    val labelLeft = with(density) {
        val start = 4.dp.toPx()
        val end = (22.dp.toPx() - heroLeft).coerceAtLeast(0f)
        start + (end - start) * eased
    }
    val tileLabelTop = tileBounds.width + with(density) { 8.dp.toPx() }
    val heroLabelTop = (heroBounds.height - with(density) { 78.dp.toPx() }).coerceAtLeast(0f)
    val labelTop = tileLabelTop + (heroLabelTop - tileLabelTop) * eased
    val labelWidth = with(density) {
        val start = (tileBounds.width - 8.dp.toPx()).coerceAtLeast(0f)
        val end = (heroBounds.width - 44.dp.toPx()).coerceAtLeast(0f)
        start + (end - start) * eased
    }
    val titleFont = 18f + (22f - 18f) * eased
    val titleLineHeight = 20f + (25f - 20f) * eased
    val artistFont = 13f + (14f - 13f) * eased
    val artistLineHeight = 15f + (17f - 15f) * eased
    val artistTopPadding = (6f * eased).dp

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(width = with(density) { width.toDp() }, height = with(density) { height.toDp() }),
    ) {
        Box(
            Modifier
                .size(width = with(density) { width.toDp() }, height = with(density) { coverHeight.toDp() })
                .clip(RoundedCornerShape(with(density) { coverCornerPx.toDp() })),
        ) {
            Art(album, Modifier.fillMaxSize(), with(density) { coverCornerPx.toDp() })
        }
        AlbumHeroLabelText(
            album = album,
            titleFontSp = titleFont,
            titleLineHeightSp = titleLineHeight,
            artistFontSp = artistFont,
            artistLineHeightSp = artistLineHeight,
            artistTopPadding = artistTopPadding,
            modifier = Modifier
                .offset { IntOffset(labelLeft.roundToInt(), labelTop.roundToInt()) }
                .width(with(density) { labelWidth.toDp() }),
        )
    }
}

@Composable
private fun CollapsingAlbumHero(
    album: Album,
    startBounds: AlbumTileBounds?,
    targetBounds: AlbumTileBounds?,
    rootOffset: Offset,
    progress: Float,
) {
    if (startBounds == null || targetBounds == null || targetBounds.width <= 0f) return
    val density = LocalDensity.current
    val eased = progress.coerceIn(0f, 1f)
    val startLeft = startBounds.left - rootOffset.x
    val startTop = startBounds.top - rootOffset.y
    val targetLeft = targetBounds.left - rootOffset.x
    val targetTop = targetBounds.top - rootOffset.y
    val left = startLeft + (targetLeft - startLeft) * eased
    val top = startTop + (targetTop - startTop) * eased
    val width = startBounds.width + (targetBounds.width - startBounds.width) * eased
    val height = startBounds.height + (targetBounds.height - startBounds.height) * eased
    val coverHeight = startBounds.height + (targetBounds.width - startBounds.height) * eased
    val coverCornerPx = with(density) {
        val start = 28.dp.toPx()
        val end = 20.dp.toPx()
        start + (end - start) * eased
    }
    val labelLeft = with(density) {
        val start = (22.dp.toPx() - startLeft).coerceAtLeast(0f)
        val end = 4.dp.toPx()
        start + (end - start) * eased
    }
    val labelTop = with(density) {
        val start = (startBounds.height - 78.dp.toPx()).coerceAtLeast(0f)
        val end = coverHeight + 8.dp.toPx()
        start + (end - start) * eased
    }
    val labelWidth = with(density) {
        val start = (startBounds.width - 44.dp.toPx()).coerceAtLeast(0f)
        val end = (width - 8.dp.toPx()).coerceAtLeast(0f)
        start + (end - start) * eased
    }
    val titleFont = 22f + (18f - 22f) * eased
    val titleLineHeight = 25f + (20f - 25f) * eased
    val artistFont = 14f + (13f - 14f) * eased
    val artistLineHeight = 17f + (15f - 17f) * eased
    val artistTopPadding = (6f * (1f - eased).coerceIn(0f, 1f)).dp

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(width = with(density) { width.toDp() }, height = with(density) { height.toDp() }),
    ) {
        Box(
            Modifier
                .size(width = with(density) { width.toDp() }, height = with(density) { coverHeight.toDp() })
                .clip(RoundedCornerShape(with(density) { coverCornerPx.toDp() })),
        ) {
            Art(album, Modifier.fillMaxSize(), with(density) { coverCornerPx.toDp() })
        }
        AlbumHeroLabelText(
            album = album,
            titleFontSp = titleFont,
            titleLineHeightSp = titleLineHeight,
            artistFontSp = artistFont,
            artistLineHeightSp = artistLineHeight,
            artistTopPadding = artistTopPadding,
            modifier = Modifier
                .offset { IntOffset(labelLeft.roundToInt(), labelTop.roundToInt()) }
                .width(with(density) { labelWidth.toDp() }),
        )
    }
}

@Composable
private fun AlbumHeroLabelText(
    album: Album,
    titleFontSp: Float,
    titleLineHeightSp: Float,
    artistFontSp: Float,
    artistLineHeightSp: Float,
    artistTopPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val titlePillShape = RoundedCornerShape(14.dp)
    val artistPillShape = RoundedCornerShape(12.dp)
    Column(modifier) {
        Text(
            album.title,
            color = Color.White,
            fontSize = titleFontSp.sp,
            lineHeight = titleLineHeightSp.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = albumHeroTextShadow(0.92f)),
            modifier = Modifier
                .clip(titlePillShape)
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Text(
            album.artist,
            color = Color.White.copy(0.92f),
            fontSize = artistFontSp.sp,
            lineHeight = artistLineHeightSp.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = albumHeroTextShadow(0.88f)),
            modifier = Modifier
                .padding(top = artistTopPadding)
                .clip(artistPillShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AlbumPinnedStatsPanel(
    tracks: List<Track>,
    modifier: Modifier = Modifier,
) {
    val totalDuration = tracks.sumOf { it.durationMillis }
    val year = albumPinnedYearLabel(tracks)
    Column(
        modifier,
        horizontalAlignment = Alignment.End,
    ) {
        AlbumPinnedMetaLine("${tracks.size} tracks")
        AlbumPinnedMetaLine(formatDuration(totalDuration))
        year?.let { AlbumPinnedMetaLine(it) }
    }
}

@Composable
private fun AlbumPinnedMetaLine(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.78f),
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun albumPinnedYearLabel(tracks: List<Track>): String? {
    val years = tracks.mapNotNull { it.year?.takeIf { year -> year > 0 } }.distinct().sorted()
    return when (years.size) {
        0 -> null
        1 -> years.first().toString()
        else -> "${years.first()}-${years.last()}"
    }
}

private fun albumHeroTextShadow(alpha: Float): Shadow =
    Shadow(
        color = Color.Black.copy(alpha = alpha),
        offset = Offset(0f, 3f),
        blurRadius = 9f,
    )

@Composable
private fun AlbumHeroBackPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.64f))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(DeckIcon.Back, Color.White, Modifier.size(19.dp))
    }
}

private fun albumMotionStage(progress: Float, start: Float, end: Float): Float {
    val span = (end - start).coerceAtLeast(0.001f)
    val normalized = ((progress - start) / span).coerceIn(0f, 1f)
    return PulseMotion.Easing.Standard.transform(normalized)
}

@Composable
private fun AlbumHeroActionDock(
    modifier: Modifier = Modifier,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onSearch: () -> Unit,
    selectLabel: String = "Select",
    onSelect: () -> Unit,
    onMore: () -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumHeroActionButton(icon = DeckIcon.Shuffle, label = null, width = 50.dp, onClick = onShuffle)
        AlbumHeroActionButton(icon = DeckIcon.Play, label = null, width = 50.dp, onClick = onPlay)
        AlbumHeroActionButton(icon = DeckIcon.Search, label = null, width = 54.dp, onClick = onSearch)
        AlbumHeroActionButton(icon = null, label = selectLabel, width = 88.dp, onClick = onSelect)
        Spacer(Modifier.weight(1f))
        AlbumHeroRoundButton(DeckIcon.More, onMore)
    }
}

@Composable
private fun AlbumHeroActionButton(icon: DeckIcon?, label: String?, width: Dp, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .width(width)
            .height(44.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.70f))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) PulseIcon(icon, Color.White, Modifier.size(23.dp))
        if (label != null) Text(label, color = Color.White, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun AlbumHeroRoundButton(icon: DeckIcon, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.70f))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, Color.White, Modifier.size(23.dp))
    }
}

@Composable
private fun AlbumTrackGlassPanel(
    album: Album,
    tracks: List<Track>,
    currentTrackKey: String,
    playing: Boolean,
    trackFade: Float,
    returningToGrid: Boolean,
    returnProgress: Float,
    onTrack: (Track) -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .graphicsLayer {
                alpha = if (returningToGrid) trackFade else 1f
                translationY = if (returningToGrid) returnProgress * 28f else (1f - trackFade) * 28f
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tracks.forEachIndexed { index, track ->
            val active = track.stableKey() == currentTrackKey
            val rowDelay = (index * 0.085f).coerceAtMost(0.42f)
            val rowProgress = if (returningToGrid) {
                trackFade
            } else {
                ((trackFade - rowDelay) / (1f - rowDelay).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            }
            Box(
                Modifier.graphicsLayer {
                    alpha = rowProgress
                    translationY = (1f - rowProgress) * (18f + index.coerceAtMost(4) * 2f)
                },
            ) {
                AlbumTrackRow(
                    track = track,
                    index = index,
                    active = active,
                    playing = playing && active,
                    accent = album.alt,
                    onClick = { onTrack(track) },
                )
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    track: Track,
    index: Int,
    active: Boolean,
    playing: Boolean,
    accent: Color,
    contentAlpha: Float = 1f,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onHandoffBoundsChanged: (PlayerLaunchHandoff) -> Unit = {},
    onClick: () -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    val interactionSource = remember { MutableInteractionSource() }
    val trackKey = track.stableKey()
    var artBounds by remember(trackKey) { mutableStateOf<AlbumTileBounds?>(null) }
    var titleBounds by remember(trackKey) { mutableStateOf<AlbumTileBounds?>(null) }
    var artistBounds by remember(trackKey) { mutableStateOf<AlbumTileBounds?>(null) }
    LaunchedEffect(trackKey, artBounds, titleBounds, artistBounds) {
        val art = artBounds ?: return@LaunchedEffect
        onHandoffBoundsChanged(
            PlayerLaunchHandoff(
                trackKey = trackKey,
                artBounds = art,
                titleBounds = titleBounds,
                artistBounds = artistBounds,
            ),
        )
    }
    val pulse = if (playing && !motion.disabled) {
        val animatedPulse by rememberInfiniteTransition(label = "albumTrackPulse").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(motion.duration(820), easing = PulseMotion.Easing.Standard), RepeatMode.Reverse),
            label = "albumTrackPulseValue",
        )
        animatedPulse
    } else {
        0.35f
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    )
                } else if (active) {
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.11f),
                            Color.White.copy(alpha = 0.09f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .border(
                1.dp,
                when {
                    selected -> accent.copy(alpha = 0.40f)
                    active -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(28.dp),
            )
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(
            track.album,
            Modifier
                .size(64.dp)
                .onGloballyPositioned { artBounds = it.albumBoundsInRoot() }
                .graphicsLayer { alpha = contentAlpha },
            16.dp,
            useCase = ArtworkUseCase.SongListThumbnail,
        )
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .graphicsLayer { alpha = contentAlpha },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                track.title,
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { titleBounds = it.albumBoundsInRoot() },
            )
            Text(
                track.artist,
                color = Color.White.copy(if (active) 0.94f else 0.84f),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { artistBounds = it.albumBoundsInRoot() },
            )
            Text(
                "${track.duration} | ${track.audioTypeLabel().lowercase(Locale.US)}",
                color = if (active) Color.White.copy(alpha = 0.58f) else Muted.copy(alpha = 0.72f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (selectionMode) {
            AlbumTrackSelectionDot(selected = selected, accent = accent, modifier = Modifier.graphicsLayer { alpha = contentAlpha })
        } else if (active) {
            Box(Modifier.graphicsLayer { alpha = contentAlpha }) {
                PlayingBars(accent = accent, animated = playing, pulse = pulse)
            }
        }
    }
}

@Composable
private fun AlbumTrackSelectionDot(selected: Boolean, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            PulseIcon(DeckIcon.Check, Color.White, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AlbumSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    deletableCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("$selectedCount selected", color = Color.White, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            Text("$deletableCount can be deleted", color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
        }
        AlbumSelectionChip("All", enabled = selectedCount < totalCount && totalCount > 0, onClick = onSelectAll)
        AlbumSelectionChip("Cancel", enabled = true, onClick = onClear)
        AlbumSelectionChip("Delete", enabled = deletableCount > 0, destructive = true, onClick = onDelete)
    }
}

@Composable
private fun AlbumSelectionChip(
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tone = if (destructive) Color(0xFFB8322A) else Color.White
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(tone.copy(alpha = if (enabled) if (destructive) 0.70f else 0.10f else 0.05f))
            .border(1.dp, tone.copy(alpha = if (enabled) 0.18f else 0.06f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.38f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ConfirmDeleteSelectedTracksDialog(
    selectedCount: Int,
    deletableCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BasicInfoModal(
        title = "Delete Selected",
        subtitle = "$deletableCount of $selectedCount selected files",
        onDismiss = onDismiss,
    ) {
        Text(
            "PulseDeck will ask Android to delete the selected local audio files. Android shows the final system confirmation before files are removed from the device.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepDialogButton("Cancel", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.08f), onClick = onDismiss)
            SleepDialogButton("Delete", Modifier.weight(1f), tone = Color(0xFFB8322A).copy(alpha = 0.78f), onClick = onConfirm)
        }
    }
}

@Composable
private fun PlayingBars(accent: Color, animated: Boolean, pulse: Float) {
    Row(Modifier.width(22.dp).height(22.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0.45f, 0.82f, 0.62f).forEachIndexed { index, base ->
            val height = if (animated) 7.dp + (12.dp * ((base * pulse + index * 0.12f) % 1f)) else 10.dp
            Box(
                Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = if (animated) 0.94f else 0.70f)),
            )
        }
    }
}

@Composable
private fun AlbumSearchDialog(
    album: Album,
    tracks: List<Track>,
    currentTrackKey: String,
    playing: Boolean,
    onDismiss: () -> Unit,
    onTrack: (Track) -> Unit,
) {
    var query by remember(album.key) { mutableStateOf("") }
    val results = remember(tracks, query) {
        val needle = query.trim()
        if (needle.isBlank()) tracks else tracks.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.artist.contains(needle, ignoreCase = true) ||
                it.audioTypeLabel().contains(needle, ignoreCase = true)
        }
    }
    BasicInfoModal(title = "Search Album", subtitle = album.title, onDismiss = onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseIcon(DeckIcon.Search, Color.White.copy(0.76f), Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Track in this album", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 390.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(results, key = { _, track -> track.stableKey() }) { index, track ->
                AlbumTrackRow(
                    track = track,
                    index = index,
                    active = track.stableKey() == currentTrackKey,
                    playing = playing && track.stableKey() == currentTrackKey,
                    accent = album.alt,
                    onClick = { onTrack(track) },
                )
            }
        }
    }
}

@Composable
private fun AlbumOptionsDialog(
    album: Album,
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSearch: () -> Unit,
    onArtist: () -> Unit,
    onMetadata: () -> Unit,
    onChangeArt: () -> Unit,
) {
    BasicInfoModal(title = "Album", subtitle = album.title, onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Art(album, Modifier.size(72.dp), 18.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(album.title, color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, color = Color.White.copy(0.72f), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} tracks  |  ${formatDuration(tracks.sumOf { it.durationMillis })}", color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlbumOptionButton("Play Album", DeckIcon.Play, Modifier.weight(1f), onPlay)
                AlbumOptionButton("Shuffle", DeckIcon.Shuffle, Modifier.weight(1f), onShuffle)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlbumOptionButton("Search", DeckIcon.Search, Modifier.weight(1f), onSearch)
                AlbumOptionButton("Metadata", DeckIcon.Info, Modifier.weight(1f), onMetadata)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlbumOptionButton("Artist", DeckIcon.Person, Modifier.weight(1f), onArtist)
                AlbumOptionButton("Change Art", DeckIcon.Disc, Modifier.weight(1f), onChangeArt)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlbumOptionButton("Close", DeckIcon.Close, Modifier.weight(1f), onDismiss)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlbumOptionButton(label: String, icon: DeckIcon, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(icon, Color.White, Modifier.size(21.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun albumInfoFor(album: Album, tracks: List<Track>): InfoDialogState {
    val totalDuration = tracks.sumOf { it.durationMillis }
    val folders = tracks.map { normalizedFolderPath(it) }.distinct().take(3).joinToString(", ").ifBlank { "Unknown" }
    val types = tracks.map { it.audioTypeLabel() }.distinct().joinToString(", ").ifBlank { "Audio" }
    return InfoDialogState(
        title = "Album Metadata",
        subtitle = album.title,
        rows = listOf(
            "Artist" to album.artist,
            "Tracks" to tracks.size.toString(),
            "Total time" to formatDuration(totalDuration),
            "Types" to types,
            "Folders" to folders,
        ),
    )
}
