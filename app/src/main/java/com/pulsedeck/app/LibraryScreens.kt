package com.pulsedeck.app

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.library.FolderSummary
import com.pulsedeck.app.library.LocalLibraryGroup
import com.pulsedeck.app.library.childFoldersFor
import com.pulsedeck.app.library.normalizedFolderPath
import com.pulsedeck.app.library.tracksUnderFolder
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun Library(
    categoryMetas: Map<String, String>,
    permissionGranted: Boolean,
    onRequestAudio: () -> Unit,
    onCategory: (LibraryCategoryKind, String) -> Unit,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    hiddenCategoryName: String? = null,
    onCategoryBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
) {
    val libraryCategories = remember { libraryCategoriesForDisplay() }
    val lastSpecialFeatureName = remember(libraryCategories) {
        libraryCategories.lastOrNull { it.isSpecialLibraryFeatureCategory() }?.name
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Ink).statusBarsPadding().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Library", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black)
                    IconCircle(DeckIcon.More, onSettings, 54.dp)
                }
            }
            if (!permissionGranted) {
                item { Pill("Allow audio", onRequestAudio, Modifier.width(150.dp)) }
            }
            itemsIndexed(libraryCategories, key = { _, item -> item.name }) { index, cat ->
                val item = cat.copy(meta = categoryMetas[cat.name])
                val kind = libraryCategoryKindForName(item.name)
                val openCategory = kind?.let { { onCategory(it, item.name) } }
                AnimatedEntrance(index + 1, animate = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CategoryRow(
                            cat = item,
                            onClick = openCategory,
                            visible = item.name != hiddenCategoryName,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                onCategoryBoundsChanged(
                                    item.name,
                                    AlbumTileBounds(
                                        left = position.x,
                                        top = position.y,
                                        width = coordinates.size.width.toFloat(),
                                        height = coordinates.size.height.toFloat(),
                                    ),
                                )
                            },
                        )
                        if (item.name == lastSpecialFeatureName) {
                            LibraryFeatureDivider()
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 3.dp)
                .width(8.dp)
                .height(148.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.48f)),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 184.dp),
        ) {
            IconCircle(DeckIcon.StreamAdd, onQuickAdd, 58.dp)
        }
    }
}

@Composable
internal fun AllSongsScreen(
    tracks: List<Track>,
    currentTrackKey: String,
    playing: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onTrack: (Track) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    hiddenCategoryHeaderName: String? = null,
    onCategoryHeaderBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
) {
    val totalDuration = remember(tracks) { tracks.sumOf { it.durationMillis } }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Ink).statusBarsPadding().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(bottom = 184.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageNavigationHeader("All Songs", onBack = onBack, subtitle = "Library")
                Row(
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenCategoryHeaderName == "All Songs") 0f else 1f }
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            onCategoryHeaderBoundsChanged(
                                "All Songs",
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
                    CategoryArtworkDisc("All Songs", Color(0xFF85A3FF), DeckIcon.MusicList, Modifier.size(52.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("All Songs", color = Color.White, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
                        Text("${tracks.size} tracks  ${formatDuration(totalDuration)}", color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconPill(DeckIcon.Shuffle, onShuffleAll, Modifier.width(72.dp))
                    IconPill(DeckIcon.Play, onPlayAll, Modifier.width(72.dp))
                    IconPill(DeckIcon.Search, onSearch, Modifier.width(72.dp))
                    Spacer(Modifier.weight(1f))
                    IconCircle(DeckIcon.More, onBack, 52.dp)
                }
            }
        }
        if (tracks.isEmpty()) {
            item { YouTubeEmptyState(onBack, "No songs found", "Scan local folders or add streams to start filling PulseDeck.", "Back") }
        } else {
            itemsIndexed(tracks, key = { _, track -> track.stableKey() }) { index, track ->
                AnimatedEntrance(index + 1, animate = false) {
                    TrackRow(
                        track = track,
                        active = track.stableKey() == currentTrackKey,
                        onClick = { onTrack(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryFeatureDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 8.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.16f)),
    )
}

@Composable
internal fun LibraryGroupScreen(
    categoryName: String,
    subtitle: String,
    tint: Color,
    icon: DeckIcon,
    groups: List<LocalLibraryGroup>,
    allTracks: List<Track>,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onGroup: (LocalLibraryGroup) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    hiddenCategoryHeaderName: String? = null,
    onCategoryHeaderBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
) {
    val totalDuration = remember(allTracks) { allTracks.sumOf { it.durationMillis } }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Ink).statusBarsPadding().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(bottom = 184.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageNavigationHeader(categoryName, onBack = onBack, subtitle = "Library")
                Row(
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenCategoryHeaderName == categoryName) 0f else 1f }
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            onCategoryHeaderBoundsChanged(
                                categoryName,
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
                    CategoryArtworkDisc(categoryName, tint, icon, Modifier.size(52.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(categoryName, color = Color.White, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$subtitle  ${groups.size} groups  ${allTracks.size} tracks  ${formatDuration(totalDuration)}", color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconPill(DeckIcon.Shuffle, onShuffleAll, Modifier.width(72.dp))
                    IconPill(DeckIcon.Play, onPlayAll, Modifier.width(72.dp))
                    IconPill(DeckIcon.Search, onSearch, Modifier.width(72.dp))
                    Spacer(Modifier.weight(1f))
                    IconCircle(DeckIcon.More, onBack, 52.dp)
                }
            }
        }
        if (groups.isEmpty()) {
            item { YouTubeEmptyState(onBack, "No $categoryName yet", "This category will fill when matching local metadata is available.", "Back") }
        } else {
            itemsIndexed(groups, key = { _, group -> group.key }) { index, group ->
                AnimatedEntrance(index + 1, animate = false) {
                    LibraryGroupRow(group = group, tint = tint, icon = icon, onClick = { onGroup(group) })
                }
            }
        }
    }
}

@Composable
internal fun LibraryFilteredTracksScreen(
    title: String,
    subtitle: String,
    categoryNameForMotion: String?,
    tint: Color,
    icon: DeckIcon,
    tracks: List<Track>,
    currentTrackKey: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onTrack: (Track) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    hiddenCategoryHeaderName: String? = null,
    onCategoryHeaderBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
) {
    val totalDuration = remember(tracks) { tracks.sumOf { it.durationMillis } }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Ink).statusBarsPadding().padding(horizontal = 22.dp),
        contentPadding = PaddingValues(bottom = 184.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageNavigationHeader(title, onBack = onBack, subtitle = "Library")
                Row(
                    modifier = Modifier
                        .graphicsLayer { alpha = if (categoryNameForMotion != null && hiddenCategoryHeaderName == categoryNameForMotion) 0f else 1f }
                        .then(
                            if (categoryNameForMotion == null) {
                                Modifier
                            } else {
                                Modifier.onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    onCategoryHeaderBoundsChanged(
                                        categoryNameForMotion,
                                        AlbumTileBounds(
                                            left = position.x,
                                            top = position.y,
                                            width = coordinates.size.width.toFloat(),
                                            height = coordinates.size.height.toFloat(),
                                        ),
                                    )
                                }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryArtworkDisc(categoryNameForMotion ?: title, tint, icon, Modifier.size(52.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$subtitle  ${tracks.size} tracks  ${formatDuration(totalDuration)}", color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconPill(DeckIcon.Shuffle, onShuffleAll, Modifier.width(72.dp))
                    IconPill(DeckIcon.Play, onPlayAll, Modifier.width(72.dp))
                    IconPill(DeckIcon.Search, onSearch, Modifier.width(72.dp))
                    Spacer(Modifier.weight(1f))
                    IconCircle(DeckIcon.More, onBack, 52.dp)
                }
            }
        }
        if (tracks.isEmpty()) {
            item { YouTubeEmptyState(onBack, "No $title yet", "Tracks will appear here when they match this category.", "Back") }
        } else {
            itemsIndexed(tracks, key = { _, track -> track.stableKey() }) { index, track ->
                AnimatedEntrance(index + 1, animate = false) {
                    TrackRow(
                        track = track,
                        active = track.stableKey() == currentTrackKey,
                        onClick = { onTrack(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGroupRow(group: LocalLibraryGroup, tint: Color, icon: DeckIcon, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDisc(tint, icon, Modifier.size(54.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(group.title, color = Color.White, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.trackCount} tracks  ${formatDuration(group.durationMillis)}  ${group.subtitle}", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun categoryIcon(name: String): DeckIcon = when (name) {
    "All Songs" -> DeckIcon.MusicList
    "Folders" -> DeckIcon.Folder
    "Folders Hierarchy" -> DeckIcon.Hierarchy
    "Albums" -> DeckIcon.Disc
    "Artists" -> DeckIcon.Person
    "Album Artists" -> DeckIcon.People
    "Genres" -> DeckIcon.Tag
    "Years" -> DeckIcon.Calendar
    "Composers" -> DeckIcon.Pencil
    "Playlists" -> DeckIcon.Playlist
    PREMIUMDECK_SOURCE_CATEGORY -> DeckIcon.StreamSource
    "PulseRadio" -> DeckIcon.Stream
    "Streams" -> DeckIcon.Stream
    "Queue" -> DeckIcon.Queue
    "Bookmarks" -> DeckIcon.Bookmark
    "Most Played" -> DeckIcon.MusicList
    else -> DeckIcon.More
}

@DrawableRes
internal fun categoryArtworkRes(name: String): Int? = when (name) {
    PREMIUMDECK_SOURCE_CATEGORY -> R.drawable.library_premiumdeck_premium
    "PulseRadio" -> R.drawable.library_pulseradio_premium
    "Album Artists" -> R.drawable.library_album_artists_premium
    "Albums" -> R.drawable.library_albums_premium
    "All Songs" -> R.drawable.library_all_songs_premium
    "Artists" -> R.drawable.library_artists_premium
    "Bookmarks" -> R.drawable.library_bookmarks_premium
    "Composers" -> R.drawable.library_composers_premium
    "Folders" -> R.drawable.library_folders_premium
    "Folders Hierarchy" -> R.drawable.library_folder_hierarchy_premium
    "Genres" -> R.drawable.library_genres_premium
    "Most Played" -> R.drawable.library_most_played_premium
    "Playlists" -> R.drawable.library_playlists_premium
    "Years" -> R.drawable.library_years_premium
    else -> null
}

@Composable
internal fun CategoryArtworkDisc(
    categoryName: String,
    tint: Color,
    fallbackIcon: DeckIcon,
    modifier: Modifier = Modifier,
) {
    val artworkRes = categoryArtworkRes(categoryName)
    if (artworkRes == null) {
        IconDisc(tint, fallbackIcon, modifier)
    } else {
        Image(
            painter = painterResource(artworkRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    }
}

@Composable
private fun CategoryRow(cat: Category, onClick: (() -> Unit)?, modifier: Modifier = Modifier, visible: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .clip(RoundedCornerShape(28.dp))
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .pressScaleEffect(interactionSource)
                        .clickable(
                            enabled = visible,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                },
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryArtworkDisc(cat.name, cat.tint, categoryIcon(cat.name), Modifier.size(50.dp))
        Spacer(Modifier.width(18.dp))
        Text(cat.name, color = Color.White, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        cat.meta?.let { Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 20.dp)) }
    }
}

@Composable
internal fun CategorySharedElement(motion: CategorySharedMotion, onFinished: () -> Unit) {
    val motionSpec = LocalPulseMotionSpec.current
    val density = LocalDensity.current
    val manualProgress = motion.progressOverride
    var entered by remember(motion.nonce) { mutableStateOf(manualProgress != null) }
    LaunchedEffect(motion.nonce, manualProgress) {
        if (manualProgress == null) entered = true
    }
    val rawProgress by animateFloatAsState(
        targetValue = manualProgress ?: if (entered) 1f else motion.startProgress,
        animationSpec = if (manualProgress != null) {
            tween(0)
        } else {
            tween(
                durationMillis = motionSpec.duration(
                    (PulseMotion.Duration.Panel * (1f - motion.startProgress.coerceIn(0f, 0.92f))).roundToInt().coerceAtLeast(90),
                ),
                easing = PulseMotion.Easing.Emphasized,
            )
        },
        label = "categorySharedElement",
        finishedListener = { if (manualProgress == null && it >= 1f) onFinished() },
    )
    val progress = rawProgress.coerceIn(0f, 1f)
    val from = motion.fromBounds
    val to = motion.toBounds
    val left = from.left + (to.left - from.left) * progress
    val top = from.top + (to.top - from.top) * progress
    val width = from.width + (to.width - from.width) * progress
    val height = from.height + (to.height - from.height) * progress
    val lift = (1f - abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)
    val travelTop = top - 16f * lift
    val travelScale = 1f + 0.035f * lift
    val headerFraction = when (motion.direction) {
        CategoryMotionDirection.Opening -> progress
        CategoryMotionDirection.Closing -> 1f - progress
    }
    val iconSize = 50f + (48f - 50f) * headerFraction
    val textSize = 18f + (22f - 18f) * headerFraction
    val lineHeight = 20f + (25f - 20f) * headerFraction
    Row(
        Modifier
            .offset { IntOffset(left.roundToInt(), travelTop.roundToInt()) }
            .size(width = with(density) { width.toDp() }, height = with(density) { height.toDp() })
            .clip(RoundedCornerShape((28f - 8f * headerFraction).dp))
            .background(Color.Black.copy(alpha = 0.10f * lift))
            .graphicsLayer {
                scaleX = travelScale
                scaleY = travelScale
                shadowElevation = 18f * lift
                ambientShadowColor = Color.Black.copy(alpha = 0.50f)
                spotShadowColor = Color.Black.copy(alpha = 0.62f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryArtworkDisc(motion.category.name, motion.category.tint, categoryIcon(motion.category.name), Modifier.size(iconSize.dp))
        Spacer(Modifier.width((18f - 2f * headerFraction).dp))
        Text(
            motion.category.name,
            color = Color.White,
            fontSize = textSize.sp,
            lineHeight = lineHeight.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
internal fun ArtistsScreen(
    artists: List<ArtistSummary>,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onPlay: (ArtistSummary) -> Unit,
    onShuffle: (ArtistSummary) -> Unit,
    onArtistInfo: (ArtistSummary) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    hiddenCategoryHeaderName: String? = null,
    onCategoryHeaderBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
) {
    var sortMode by rememberSaveable { mutableStateOf(ArtistSortMode.Name) }
    var sortDialogOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sortedArtists = remember(artists, sortMode) {
        when (sortMode) {
            ArtistSortMode.Name -> artists.sortedBy { it.name.lowercase() }
            ArtistSortMode.Songs -> artists.sortedWith(compareByDescending<ArtistSummary> { it.songCount }.thenBy { it.name.lowercase() })
            ArtistSortMode.Duration -> artists.sortedWith(compareByDescending<ArtistSummary> { it.durationMillis }.thenBy { it.name.lowercase() })
        }
    }
    val alphabetLabels = remember { listOf("^", "0") + ('A'..'Z').map { it.toString() } + listOf("#") }
    var alphabetActive by remember { mutableStateOf(false) }
    var alphabetPreviewLabel by remember { mutableStateOf("A") }
    fun targetArtistIndexFor(label: String): Int = when (label) {
        "^" -> 0
        "0" -> sortedArtists.indexOfFirst { it.name.trim().firstOrNull()?.isDigit() == true }
        "#" -> sortedArtists.indexOfFirst {
            val first = it.name.trim().firstOrNull()
            first != null && !first.isLetterOrDigit()
        }
        else -> sortedArtists.indexOfFirst { it.name.trim().firstOrNull()?.uppercaseChar()?.toString() == label }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Ink).statusBarsPadding().padding(horizontal = 22.dp),
            contentPadding = PaddingValues(bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(Modifier.padding(top = 16.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    PageNavigationHeader("Artists", onBack = onBack, subtitle = "Library")
                    Row(
                        modifier = Modifier
                            .graphicsLayer { alpha = if (hiddenCategoryHeaderName == "Artists") 0f else 1f }
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                onCategoryHeaderBoundsChanged(
                                    "Artists",
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
                        CategoryArtworkDisc("Artists", Color(0xFF5D58A7), DeckIcon.Person, Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Artists", color = Color.White, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black)
                            Text("${artists.size} artists", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconPill(DeckIcon.Shuffle, { sortedArtists.firstOrNull()?.let(onShuffle) }, Modifier.width(72.dp))
                        IconPill(DeckIcon.Play, { sortedArtists.firstOrNull()?.let(onPlay) }, Modifier.width(72.dp))
                        IconPill(DeckIcon.Search, onSearch, Modifier.width(72.dp))
                        Pill("Select", {}, Modifier.width(92.dp))
                        Spacer(Modifier.weight(1f))
                        IconCircle(DeckIcon.More, { sortDialogOpen = true }, 52.dp)
                    }
                }
            }
            itemsIndexed(sortedArtists, key = { _, artist -> artist.name }) { index, artist ->
                AnimatedEntrance(index + 1, animate = false) {
                    ArtistRow(artist, onClick = { onArtistInfo(artist) }, onPlay = { onPlay(artist) })
                }
            }
        }
        if (alphabetActive) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 54.dp)
                    .size(70.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color.Black.copy(0.78f), Color.Black.copy(0.92f))))
                    .border(1.dp, Color.White.copy(0.26f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(alphabetPreviewLabel, color = Color.White, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
            }
        }
        AlphabetRail(
            labels = alphabetLabels,
            active = alphabetActive,
            onActive = { alphabetActive = it },
            onLabel = { label ->
                alphabetPreviewLabel = label
                val index = targetArtistIndexFor(label)
                if (index >= 0) scope.launch { listState.scrollToItem(index + 1) }
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        if (sortDialogOpen) {
            ArtistSortDialog(
                selected = sortMode,
                onDismiss = { sortDialogOpen = false },
                onSelect = {
                    sortMode = it
                    sortDialogOpen = false
                },
            )
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistSummary, onClick: () -> Unit, onPlay: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val coverAlbum = remember(artist.tracks) {
        artist.tracks.firstOrNull { it.album.coverUri != null || it.album.sourceUri != null }?.album
            ?: artist.tracks.firstOrNull()?.album
    }
    val coverBitmap = if (coverAlbum != null) rememberAlbumArtBitmap(coverAlbum, ArtworkUseCase.SongListThumbnail) else null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(artistColor(artist.name), lerp(artistColor(artist.name), Color.Black, 0.32f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.26f)))))
            } else {
                Text(artist.name.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, color = Color.White, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${artist.songCount} songs  ${formatDuration(artist.durationMillis)}", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
        }
        IconTransport(DeckIcon.Play, onPlay, 42.dp, transparent = true)
    }
}

@Composable
private fun ArtistSortDialog(selected: ArtistSortMode, onDismiss: () -> Unit, onSelect: (ArtistSortMode) -> Unit) {
    BasicInfoModal("Sort Artists", selected.label, onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtistSortMode.entries.forEach { mode ->
                ModeOptionRow(mode.label, mode.subtitle, mode == selected, null) { onSelect(mode) }
            }
        }
        SleepDialogButton("Cancel", Modifier.fillMaxWidth().padding(top = 14.dp), tone = Color.White.copy(alpha = 0.08f), onClick = onDismiss)
    }
}

@Composable
internal fun FolderHierarchyScreen(
    tracks: List<Track>,
    currentPath: String,
    onBack: () -> Unit,
    onRoot: () -> Unit,
    onFolder: (String) -> Unit,
    onSearch: () -> Unit,
    onTrack: (Track) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    hiddenCategoryHeaderName: String? = null,
    onCategoryHeaderBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
) {
    val normalizedPath = currentPath.trim('/')
    val visibleTracks = remember(tracks, normalizedPath) { tracksUnderFolder(tracks, normalizedPath) }
    val childFolders = remember(tracks, normalizedPath) { childFoldersFor(tracks, normalizedPath) }
    val directTracks = remember(visibleTracks, normalizedPath, childFolders) {
        visibleTracks.filter { normalizedFolderPath(it) == normalizedPath || childFolders.isEmpty() }
    }
    val folderTitle = if (normalizedPath.isBlank()) "Folders Hierarchy" else normalizedPath.substringAfterLast('/')
    val folderSubtitle = if (normalizedPath.isBlank()) "Music storage" else normalizedPath
    val motionCategoryName = if (normalizedPath.isBlank()) "Folders Hierarchy" else null

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Ink).statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 184.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageNavigationHeader(folderTitle, onBack = onBack, subtitle = if (normalizedPath.isBlank()) "Library" else folderSubtitle)
                if (motionCategoryName != null) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer { alpha = if (hiddenCategoryHeaderName == motionCategoryName) 0f else 1f }
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                onCategoryHeaderBoundsChanged(
                                    motionCategoryName,
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
                        CategoryArtworkDisc(motionCategoryName, Color(0xFF5868E8), DeckIcon.Hierarchy, Modifier.size(52.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(motionCategoryName, color = Color.White, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${visibleTracks.size} tracks  ${formatDuration(visibleTracks.sumOf { it.durationMillis })}", color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF5868E8).copy(alpha = 0.90f),
                                    Color(0xFF2A1F49).copy(alpha = 0.96f),
                                    Color.Black.copy(alpha = 0.92f),
                                ),
                            ),
                        )
                        .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(28.dp))
                        .padding(18.dp),
                ) {
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text(folderTitle, color = Color.White, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(folderSubtitle, color = Color.White.copy(0.70f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${visibleTracks.size} tracks  ${formatDuration(visibleTracks.sumOf { it.durationMillis })}", color = Color.White.copy(0.82f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                    }
                    IconDisc(Color.White.copy(0.20f), DeckIcon.Hierarchy, Modifier.align(Alignment.TopEnd).size(52.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconPill(DeckIcon.Shuffle, { visibleTracks.randomOrNull()?.let(onTrack) }, Modifier.width(72.dp))
                    IconPill(DeckIcon.Play, { visibleTracks.firstOrNull()?.let(onTrack) }, Modifier.width(72.dp))
                    IconPill(DeckIcon.Search, onSearch, Modifier.width(72.dp))
                    Pill("Select", {}, Modifier.width(92.dp))
                    Spacer(Modifier.weight(1f))
                    IconCircle(DeckIcon.More, onRoot, 52.dp)
                }
            }
        }
        if (childFolders.isNotEmpty()) {
            item(key = "subfoldersHeader") { Text("Subfolders", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp)) }
            itemsIndexed(childFolders, key = { _, folder -> folder.path }) { index, folder ->
                AnimatedEntrance(index + 1, animate = false) {
                    FolderRow(folder, onClick = { onFolder(folder.path) })
                }
            }
        }
        if (directTracks.isNotEmpty()) {
            item(key = "tracksHeader") { Text("Tracks", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(directTracks, key = { _, track -> track.stableKey() }) { index, track ->
                AnimatedEntrance(index + 1, animate = false) {
                    TrackRow(track, active = false) { onTrack(track) }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderSummary, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDisc(Color(0xFF5868E8), DeckIcon.Folder, Modifier.size(54.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, color = Color.White, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${folder.trackCount} tracks  ${formatDuration(folder.durationMillis)}", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
