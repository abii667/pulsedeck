package com.pulsedeck.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StreamCollectionActionsDialog(
    collection: StreamCollection,
    resolvingSourceId: String?,
    activeSourceId: String?,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onSave: () -> Unit,
    onKeepOffline: () -> Unit,
    followedArtistKeys: Set<String>,
    onToggleFollowArtist: (String) -> Unit,
    onSource: (YouTubeSource) -> Unit,
    onSourceActions: (YouTubeSource, List<String>) -> Unit,
) {
    var visibleLimit by rememberSaveable(collection.id, collection.sources.size) { mutableIntStateOf(PREMIUM_DECK_CHART_SAFE_LIMIT) }
    var loadMoreConfirmOpen by rememberSaveable(collection.id) { mutableStateOf(false) }
    val allPlayableSources = remember(collection.id, collection.sources) {
        collection.sources
            .filter { it.kind == YouTubeSourceKind.Video }
            .distinctBy { it.url.ifBlank { it.id } }
    }
    val playableSources = remember(allPlayableSources, visibleLimit) {
        allPlayableSources.take(visibleLimit)
    }
    val albumLike = collection.kind == StreamCollectionKind.AlbumLike
    val artistName = when {
        collection.kind == StreamCollectionKind.Artist -> collection.title
        albumLike -> collection.sources.firstOrNull()?.author?.cleanStreamArtistName().orEmpty()
        else -> ""
    }
    val artistKey = artistName.normalizedSearchText()
    val canFollowArtist = artistKey.isNotBlank() && artistKey !in genericStreamAlbumArtistKeys
    val artistFollowed = artistKey in followedArtistKeys
    val releaseTypeLabel = when {
        collection.subtitle.contains("EP", ignoreCase = true) -> "EP"
        collection.subtitle.contains("Single", ignoreCase = true) -> "Single"
        collection.subtitle.contains("Project", ignoreCase = true) -> "Project"
        else -> "Album"
    }
    val collectionBadge = when {
        albumLike -> "$releaseTypeLabel tracklist - lazily matched to this artist release"
        collection.canSave -> "Generated mix - pin it to keep this exact queue"
        else -> collection.kind.label
    }
    StreamCollectionDetailOverlay(
        collection = collection,
        playableSources = playableSources,
        resolvingSourceId = resolvingSourceId,
        activeSourceId = activeSourceId,
        collectionBadge = collectionBadge,
        canFollowArtist = canFollowArtist,
        artistFollowed = artistFollowed,
        artistName = artistName,
        albumLike = albumLike,
        onDismiss = onDismiss,
        onPlayAll = onPlayAll,
        onSave = onSave,
        onKeepOffline = onKeepOffline,
        onToggleFollowArtist = onToggleFollowArtist,
        onSource = onSource,
        onSourceActions = { source -> onSourceActions(source, playableSources.map { it.id }) },
        totalPlayableSourceCount = allPlayableSources.size,
        canLoadMore = playableSources.size < allPlayableSources.size,
        loadMoreConfirmOpen = loadMoreConfirmOpen,
        onRequestLoadMore = { loadMoreConfirmOpen = true },
        onDismissLoadMore = { loadMoreConfirmOpen = false },
        onConfirmLoadMore = {
            visibleLimit = (visibleLimit + PREMIUM_DECK_CHART_PAGE_SIZE).coerceAtMost(allPlayableSources.size)
            loadMoreConfirmOpen = false
        },
    )
}

@Composable
private fun StreamCollectionDetailOverlay(
    collection: StreamCollection,
    playableSources: List<YouTubeSource>,
    resolvingSourceId: String?,
    activeSourceId: String?,
    collectionBadge: String,
    canFollowArtist: Boolean,
    artistFollowed: Boolean,
    artistName: String,
    albumLike: Boolean,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onSave: () -> Unit,
    onKeepOffline: () -> Unit,
    onToggleFollowArtist: (String) -> Unit,
    onSource: (YouTubeSource) -> Unit,
    onSourceActions: (YouTubeSource) -> Unit,
    totalPlayableSourceCount: Int,
    canLoadMore: Boolean,
    loadMoreConfirmOpen: Boolean,
    onRequestLoadMore: () -> Unit,
    onDismissLoadMore: () -> Unit,
    onConfirmLoadMore: () -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    var visible by remember(collection.id) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = motion.duration(PulseMotion.Duration.Panel), easing = PulseMotion.Easing.Emphasized),
        label = "streamCollectionDetail",
    )
    LaunchedEffect(collection.id) { visible = true }
    BackHandler { onDismiss() }

    Box(
        Modifier
            .fillMaxSize()
            .background(StreamBase.copy(alpha = 0.98f)),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 34f
                    scaleX = 0.985f + progress * 0.015f
                    scaleY = 0.985f + progress * 0.015f
                }
                .padding(horizontal = StreamHorizontalPadding),
            contentPadding = PaddingValues(top = 14.dp, bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PageNavigationHeader(
                    title = collection.title,
                    subtitle = collection.subtitle.ifBlank { collection.kind.label },
                    onBack = onDismiss,
                ) {
                    if (collection.canSave) {
                        IconCircle(DeckIcon.StreamPin, onSave, 46.dp)
                    }
                }
            }
            item {
                StreamCollectionDetailHero(collection, playableSources)
            }
            item {
                StreamCollectionDetailActions(
                    albumLike = albumLike,
                    canSave = collection.canSave,
                    canFollowArtist = canFollowArtist,
                    artistFollowed = artistFollowed,
                    artistName = artistName,
                    onPlayAll = onPlayAll,
                    onSave = onSave,
                    onKeepOffline = onKeepOffline,
                    onToggleFollowArtist = onToggleFollowArtist,
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulseIcon(if (albumLike) DeckIcon.Disc else if (collection.canSave) DeckIcon.StreamMix else DeckIcon.StreamPin, Color.White.copy(alpha = 0.80f), Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(collectionBadge, color = Color.White.copy(alpha = 0.74f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                StreamSourcesSectionHeader(
                    title = "Tracklist",
                    subtitle = "${playableSources.size} of $totalPlayableSourceCount tracks  |  ${collection.kind.label}",
                )
            }
            if (playableSources.isEmpty()) {
                item { StreamInlineEmpty("This collection has no playable streams yet") }
            } else {
                itemsIndexed(playableSources, key = { _, source -> "${collection.id}-${source.albumTrackNumberHint}-${source.streamDistinctKey()}" }) { index, source ->
                    StreamCollectionAlbumTrackRow(
                        index = source.albumTrackNumberHint.takeIf { it > 0 } ?: index + 1,
                        source = source,
                        resolving = resolvingSourceId == source.id,
                        isPlaying = activeSourceId == source.id,
                        onClick = { onSource(source) },
                        onLongPress = { onSourceActions(source) },
                    )
                }
                if (canLoadMore) {
                    item {
                        SourceActionButton(
                            "Load More",
                            DeckIcon.Plus,
                            Modifier.fillMaxWidth(),
                            onRequestLoadMore,
                        )
                    }
                }
            }
        }
        if (loadMoreConfirmOpen) {
            PremiumDeckListLoadMoreDialog(
                shownCount = playableSources.size,
                totalCount = totalPlayableSourceCount,
                onDismiss = onDismissLoadMore,
                onConfirm = onConfirmLoadMore,
            )
        }
    }
}

@Composable
private fun StreamCollectionDetailHero(collection: StreamCollection, sources: List<YouTubeSource>) {
    val accent = Color(collection.accentColor)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(138.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.94f), StreamDeepRed.copy(alpha = 0.74f), Color.Black.copy(alpha = 0.92f))))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp)),
        ) {
            if (sources.isNotEmpty()) {
                StreamCoverCollage(sources.take(4), Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)))
            }
            PulseIcon(DeckIcon.StreamMix, Color.White.copy(alpha = 0.78f), Modifier.size(24.dp).align(Alignment.TopStart).padding(start = 14.dp, top = 14.dp))
            Text(
                collection.title.uppercase(),
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(collection.title, color = Color.White, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(collection.subtitle, color = StreamTextSecondary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            Text("${sources.size} tracks", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun PremiumDeckListLoadMoreDialog(
    shownCount: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val nextCount = (shownCount + PREMIUM_DECK_CHART_PAGE_SIZE).coerceAtMost(totalCount)
    BasicInfoModal(
        title = "Load more tracks",
        subtitle = "Showing $shownCount of $totalCount",
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "The first 25 protects memory on the list screen. Loading more keeps extra row state and thumbnails visible only while this list is open.",
                color = StreamTextSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceActionButton("Cancel", DeckIcon.Close, Modifier.weight(1f), onDismiss)
                SourceActionButton(
                    "Show $nextCount",
                    DeckIcon.Plus,
                    Modifier.weight(1f),
                    onConfirm,
                    active = true,
                )
            }
        }
    }
}

@Composable
private fun StreamCollectionDetailActions(
    albumLike: Boolean,
    canSave: Boolean,
    canFollowArtist: Boolean,
    artistFollowed: Boolean,
    artistName: String,
    onPlayAll: () -> Unit,
    onSave: () -> Unit,
    onKeepOffline: () -> Unit,
    onToggleFollowArtist: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SourceActionButton("Play", DeckIcon.Play, Modifier.weight(1f), onPlayAll)
        SourceActionButton(if (albumLike) "Download" else "Offline", DeckIcon.StreamOffline, Modifier.weight(1f), onKeepOffline)
        if (canSave) {
            SourceActionButton(if (albumLike) "Pin" else "Save", DeckIcon.StreamPin, Modifier.weight(1f), onSave)
        }
    }
    if (canFollowArtist) {
        SourceActionButton(if (artistFollowed) "Following Artist" else "Follow Artist", if (artistFollowed) DeckIcon.StreamLike else DeckIcon.Heart, Modifier.fillMaxWidth().padding(top = 10.dp), { onToggleFollowArtist(artistName) }, active = artistFollowed)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StreamCollectionAlbumTrackRow(index: Int, source: YouTubeSource, resolving: Boolean, isPlaying: Boolean = false, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
    val interactionSource = remember(source.streamDistinctKey()) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                when {
                    isPlaying -> StreamAccentRed.copy(alpha = 0.15f)
                    resolving -> Color.White.copy(alpha = 0.12f)
                    else -> Color.White.copy(alpha = 0.045f)
                },
            )
            .border(
                1.dp,
                when {
                    isPlaying -> StreamAccentRed.copy(alpha = 0.34f)
                    resolving -> Color.White.copy(alpha = 0.12f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
                RoundedCornerShape(26.dp),
            )
            .pressScaleEffect(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(60.dp), DeckIcon.Play)
        Spacer(Modifier.width(11.dp))
        if (isPlaying) {
            PremiumDeckNowPlayingIndicator()
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(source.title, color = Color.White, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.author, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            val meta = buildList {
                add("#$index")
                if (source.albumYearHint > 0) add(source.albumYearHint.toString())
                if (source.reaction == YouTubeReaction.Liked) add("liked")
                if (source.bookmarked) add("saved")
            }.joinToString("  |  ")
            Text(meta, color = Muted.copy(alpha = 0.74f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(10.dp))
        if (resolving) {
            PulseIcon(DeckIcon.Timer, Color.White.copy(alpha = 0.72f), Modifier.size(21.dp))
        } else {
            PulseIcon(DeckIcon.Play, Color.White.copy(alpha = 0.72f), Modifier.size(21.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun StreamCollectionTrackRow(index: Int, source: YouTubeSource, resolving: Boolean, isPlaying: Boolean = false, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
    val interactionSource = remember(source.id) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isPlaying) StreamAccentRed.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.065f))
            .border(
                1.dp,
                if (isPlaying) StreamAccentRed.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.055f),
                RoundedCornerShape(18.dp),
            )
            .pressScaleEffect(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(20.dp),
            maxLines = 1,
        )
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(48.dp), DeckIcon.Play)
        Spacer(Modifier.width(10.dp))
        if (isPlaying) {
            PremiumDeckNowPlayingIndicator()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(source.title, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.author, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                if (source.durationMillis > 0L) add(formatDuration(source.durationMillis))
                if (source.reaction == YouTubeReaction.Liked) add("liked")
                if (source.bookmarked) add("saved")
            }.joinToString("  |  ")
            Text(meta.ifBlank { PREMIUMDECK_STREAM_TITLE }, color = Color.White.copy(alpha = 0.48f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        IconTap(if (resolving) DeckIcon.Timer else DeckIcon.Play, onClick, 34.dp)
    }
}

@Composable
internal fun YouTubeSourceRenameDialog(source: YouTubeSource, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable(source.id) { mutableStateOf(source.cleanedSuggestion().second) }
    var author by rememberSaveable(source.id) { mutableStateOf(source.cleanedSuggestion().first) }
    BasicInfoModal(title = "Clean Metadata", subtitle = source.title, onDismiss = onDismiss) {
        GlassTextField(title, "Title") { title = it }
        Spacer(Modifier.height(10.dp))
        GlassTextField(author, "Artist or channel") { author = it }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepDialogButton("Cancel", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.10f), onClick = onDismiss)
            SleepDialogButton("Save", Modifier.weight(1f), tone = StreamAccentRed.copy(alpha = 0.62f)) {
                onSave(title.trim().ifBlank { source.title }, author.trim().ifBlank { source.author })
            }
        }
    }
}

@Composable
internal fun AddSourceToPlaylistDialog(source: YouTubeSource, playlists: List<YouTubePlaylist>, onDismiss: () -> Unit, onNewPlaylist: () -> Unit, onPlaylist: (YouTubePlaylist) -> Unit) {
    BasicInfoModal(title = "Add to Playlist", subtitle = source.title, onDismiss = onDismiss) {
        if (playlists.isEmpty()) {
            Text("Create a stream playlist first, then add this source.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            playlists.forEach { playlist ->
                PlaylistChoiceRow(playlist, playlist.sourceIds.contains(source.id)) { onPlaylist(playlist) }
                Spacer(Modifier.height(8.dp))
            }
        }
        SleepDialogButton("New Playlist", Modifier.fillMaxWidth().padding(top = 8.dp), tone = StreamAccentRed.copy(alpha = 0.62f), onClick = onNewPlaylist)
    }
}

private enum class PlaylistCreateMode(val label: String) {
    Empty("Empty"),
    YouTube("YouTube"),
    Spotify("Spotify"),
}

@Composable
internal fun YouTubePlaylistEditorDialog(
    playlist: YouTubePlaylist?,
    onDismiss: () -> Unit,
    onSave: (YouTubePlaylist) -> Unit,
    onImportYouTubePlaylist: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    onImportSpotifyPlaylist: (String, String, Int) -> Unit = { _, _, _ -> },
) {
    val freshCreate = playlist == null
    val editing = playlist != null && playlist.title.isNotBlank()
    var createMode by rememberSaveable { mutableStateOf(PlaylistCreateMode.Empty) }
    var title by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.title.orEmpty()) }
    var description by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.description.orEmpty()) }
    var youtubeUrl by rememberSaveable { mutableStateOf("") }
    var spotifyUrl by rememberSaveable { mutableStateOf("") }
    var accent by rememberSaveable(playlist?.id) { mutableIntStateOf(playlist?.accentColor ?: 0xFF4A82FF.toInt()) }
    val modalTitle = when {
        editing -> "Edit Playlist"
        freshCreate -> "Create Playlist"
        else -> "New Playlist"
    }
    BasicInfoModal(title = modalTitle, subtitle = "PremiumDeck streams only", onDismiss = onDismiss) {
        if (freshCreate) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaylistCreateMode.entries.forEach { mode ->
                    PlaylistCreateTab(
                        label = mode.label,
                        selected = createMode == mode,
                        modifier = Modifier.weight(1f),
                    ) {
                        createMode = mode
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        when (createMode.takeIf { freshCreate } ?: PlaylistCreateMode.Empty) {
            PlaylistCreateMode.Empty -> {
                GlassTextField(title, "Playlist name") { title = it }
                Spacer(Modifier.height(10.dp))
                GlassTextField(description, "Description optional") { description = it }
                Spacer(Modifier.height(12.dp))
                PlaylistAccentPicker(accent) { accent = it }
                PlaylistEditorActions(
                    confirmLabel = if (editing) "Save" else "Create",
                    onDismiss = onDismiss,
                ) {
                    val now = System.currentTimeMillis()
                    val base = playlist ?: YouTubePlaylist(id = newYouTubePlaylistId(), title = "", createdMillis = now)
                    onSave(base.copy(title = title, description = description, accentColor = accent, updatedMillis = now))
                }
            }
            PlaylistCreateMode.YouTube -> {
                GlassTextField(title, "Playlist name optional") { title = it }
                Spacer(Modifier.height(10.dp))
                GlassTextField(youtubeUrl, "YouTube playlist link") { youtubeUrl = it }
                Spacer(Modifier.height(12.dp))
                PlaylistAccentPicker(accent) { accent = it }
                val detection = remember(youtubeUrl) { detectYouTubeSource(youtubeUrl) }
                val error = youtubeUrl.isNotBlank() && detection?.kind != YouTubeSourceKind.Playlist
                if (error) {
                    Text("Paste a YouTube playlist link.", color = StreamAccentRed.copy(alpha = 0.92f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
                PlaylistEditorActions(
                    confirmLabel = "Import",
                    onDismiss = onDismiss,
                    enabled = detection?.kind == YouTubeSourceKind.Playlist,
                ) {
                    onImportYouTubePlaylist(
                        title.trim().ifBlank { "YouTube Playlist" },
                        detection?.normalizedUrl ?: youtubeUrl.trim(),
                        detection?.sourceId.orEmpty(),
                        accent,
                    )
                }
            }
            PlaylistCreateMode.Spotify -> {
                GlassTextField(title, "Playlist name optional") { title = it }
                Spacer(Modifier.height(10.dp))
                GlassTextField(spotifyUrl, "Spotify playlist link") { spotifyUrl = it }
                Spacer(Modifier.height(12.dp))
                PlaylistAccentPicker(accent) { accent = it }
                val validSpotifyLink = remember(spotifyUrl) { isSpotifyPlaylistLink(spotifyUrl) }
                if (spotifyUrl.isNotBlank() && !validSpotifyLink) {
                    Text("Paste a Spotify playlist link.", color = StreamAccentRed.copy(alpha = 0.92f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
                PlaylistEditorActions(
                    confirmLabel = "Import",
                    onDismiss = onDismiss,
                    enabled = validSpotifyLink,
                ) {
                    onImportSpotifyPlaylist(
                        title.trim().ifBlank { "Spotify Playlist" },
                        spotifyUrl.trim(),
                        accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCreateTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) StreamAccentRed.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.24f else 0.08f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = if (selected) 0.96f else 0.70f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun PlaylistAccentPicker(accent: Int, onAccent: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        youtubeAccentChoices.forEach { color ->
            val selected = color == accent
            val interactionSource = remember(color) { MutableInteractionSource() }
            Box(
                Modifier
                    .size(if (selected) 38.dp else 34.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(2.dp, Color.White.copy(alpha = if (selected) 0.85f else 0.18f), CircleShape)
                    .pressScaleEffect(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) { onAccent(color) },
            )
        }
    }
}

@Composable
private fun PlaylistEditorActions(
    confirmLabel: String,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    onConfirm: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SleepDialogButton("Cancel", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.10f), onClick = onDismiss)
        SleepDialogButton(
            confirmLabel,
            Modifier.weight(1f),
            tone = if (enabled) StreamAccentRed.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.10f),
        ) {
            if (enabled) onConfirm()
        }
    }
}

@Composable
internal fun PremiumDeckPlaylistImportProgressDialog(
    progress: PremiumDeckPlaylistImportProgress,
    onDismiss: () -> Unit,
) {
    val targetFraction = when {
        progress.total > 0 -> progress.processed.toFloat() / progress.total.toFloat()
        progress.complete -> 1f
        else -> 0.12f
    }.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetFraction, animationSpec = tween(260), label = "playlistImportProgress")
    BasicInfoModal(
        title = if (progress.complete) "Import Complete" else "Importing ${progress.provider}",
        subtitle = progress.title.ifBlank { "${progress.provider} playlist" },
        onDismiss = { if (progress.complete) onDismiss() },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseIcon(if (progress.complete) DeckIcon.Check else DeckIcon.Timer, Color.White.copy(alpha = 0.86f), Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(progress.phase, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = if (progress.total > 0) {
                    "${progress.processed.coerceAtMost(progress.total)} of ${progress.total} checked  |  ${progress.matched} matched"
                } else {
                    "Preparing playlist metadata"
                }
                Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedFraction.coerceAtLeast(if (progress.complete) 1f else 0.05f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.horizontalGradient(listOf(StreamAccentRed.copy(alpha = 0.95f), Color.White.copy(alpha = 0.76f)))),
            )
        }
        if (progress.complete) {
            SleepDialogButton("Done", Modifier.fillMaxWidth().padding(top = 16.dp), tone = StreamAccentRed.copy(alpha = 0.62f), onClick = onDismiss)
        }
    }
}

private fun isSpotifyPlaylistLink(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return false
    if (trimmed.startsWith("spotify:playlist:", ignoreCase = true)) return true
    val lower = trimmed.lowercase()
    return lower.startsWith("https://open.spotify.com/playlist/") ||
        lower.startsWith("http://open.spotify.com/playlist/") ||
        lower.startsWith("open.spotify.com/playlist/") ||
        lower.startsWith("https://spotify.link/") ||
        lower.startsWith("http://spotify.link/") ||
        lower.startsWith("spotify.link/") ||
        lower.startsWith("https://spotify.app.link/") ||
        lower.startsWith("http://spotify.app.link/") ||
        lower.startsWith("spotify.app.link/")
}

@Composable
internal fun YouTubePlaylistActionsDialog(
    playlist: YouTubePlaylist,
    sources: List<YouTubeSource>,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddSources: () -> Unit,
    onEditMix: () -> Unit,
    onKeepOffline: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val sourceById = remember(sources) { streamSourceLookup(sources) }
    val playlistSources = remember(playlist.sourceIds, sourceById) { playlist.sourceIds.mapNotNull { sourceById[it] } }
    BasicInfoModal(title = playlist.title, subtitle = playlist.description.ifBlank { playlist.origin.label }, onDismiss = onDismiss) {
        Text("${playlistSources.size} sources  |  ${playlistSources.count { it.downloadState == YouTubeDownloadState.Downloaded }} saved  |  Updated ${formatModifiedDate(playlist.updatedMillis)}", color = Muted, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton("Play", DeckIcon.Play, Modifier.weight(1f), onPlay)
            SourceActionButton("Shuffle", DeckIcon.Shuffle, Modifier.weight(1f), onShuffle)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton(if (playlist.origin == YouTubePlaylistOrigin.SavedMix) "Edit Mix" else "Edit List", DeckIcon.StreamEdit, Modifier.weight(1f), onEditMix)
            SourceActionButton("Add Sources", DeckIcon.StreamAdd, Modifier.weight(1f), onAddSources)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton("Keep Offline", DeckIcon.StreamOffline, Modifier.weight(1f), onKeepOffline)
            SourceActionButton("Rename", DeckIcon.Pencil, Modifier.weight(1f), onRename)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton("Delete", DeckIcon.Trash, Modifier.weight(1f), onDelete, destructive = true)
        }
    }
}

@Composable
internal fun YouTubePlaylistSourcePickerDialog(playlist: YouTubePlaylist, sources: List<YouTubeSource>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var selected by remember(playlist.id, sources.size) { mutableStateOf(playlist.sourceIds.toSet()) }
    BasicInfoModal(title = "Add Sources", subtitle = playlist.title, onDismiss = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources, key = { it.id }) { source ->
                PlaylistSourceSelectRow(source, source.id in selected) {
                    selected = if (source.id in selected) selected - source.id else selected + source.id
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepDialogButton("Cancel", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.10f), onClick = onDismiss)
            SleepDialogButton("Save", Modifier.weight(1f), tone = StreamAccentRed.copy(alpha = 0.62f)) { onSave(sources.filter { it.id in selected }.map { it.id }) }
        }
    }
}

@Composable
internal fun YouTubePlaylistMixEditorDialog(
    playlist: YouTubePlaylist,
    sources: List<YouTubeSource>,
    discoveryResults: List<YouTubeSearchResult>,
    playHistory: List<StreamPlayHistoryItem>,
    resolvingSourceId: String?,
    activeSourceId: String?,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onKeepOffline: () -> Unit,
    onActions: () -> Unit,
    onPlaySource: (YouTubeSource) -> Unit,
    onSourceActions: (YouTubeSource, List<String>) -> Unit,
    onRemoveSource: (YouTubeSource) -> Unit,
    onAddSource: (YouTubeSource) -> Unit,
) {
    var query by rememberSaveable(playlist.id) { mutableStateOf("") }
    val sourceById = remember(sources, playHistory) {
        streamSourceLookup(sources + playHistory.mapNotNull { it.toYouTubeSource() })
    }
    val playlistSources = remember(playlist.sourceIds, sourceById) {
        playlist.sourceIds.mapNotNull { sourceById[it] }
    }
    val suggestions = remember(playlist, sources, discoveryResults, playHistory, query) {
        val base = suggestStreamPlaylistAdditions(playlist, sources, discoveryResults, playHistory, limit = 24)
        val needle = query.normalizedSearchText()
        if (needle.isBlank()) {
            base
        } else {
            val searchable = (base + sources + discoveryResults.map { it.source.copy(reviewState = YouTubeReviewState.Accepted) })
                .filter { it.kind == YouTubeSourceKind.Video }
                .filter { it.reaction != YouTubeReaction.Disliked }
                .filterNot { it.id in playlist.sourceIds }
                .distinctBy { it.streamDistinctKey() }
            searchable.filter {
                "${it.title} ${it.author}".normalizedSearchText().contains(needle)
            }.take(24)
        }
    }
    val motion = LocalPulseMotionSpec.current
    var visible by remember(playlist.id) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = motion.duration(PulseMotion.Duration.Panel), easing = PulseMotion.Easing.Emphasized),
        label = "streamPlaylistDetail",
    )
    LaunchedEffect(playlist.id) { visible = true }
    BackHandler { onDismiss() }

    Box(
        Modifier
            .fillMaxSize()
            .background(StreamBase.copy(alpha = 0.98f)),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 34f
                    scaleX = 0.985f + progress * 0.015f
                    scaleY = 0.985f + progress * 0.015f
                }
                .padding(horizontal = StreamHorizontalPadding),
            contentPadding = PaddingValues(top = 14.dp, bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PageNavigationHeader(
                    title = playlist.title,
                    subtitle = playlist.description.ifBlank { playlist.origin.label },
                    onBack = onDismiss,
                ) {
                    IconCircle(DeckIcon.More, onActions, 46.dp)
                }
            }
            item {
                StreamPlaylistDetailHero(playlist, playlistSources)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceActionButton("Play", DeckIcon.Play, Modifier.weight(1f), onPlayAll)
                    SourceActionButton("Shuffle", DeckIcon.Shuffle, Modifier.weight(1f), onShuffle)
                    SourceActionButton("Offline", DeckIcon.StreamOffline, Modifier.weight(1f), onKeepOffline)
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulseIcon(DeckIcon.Playlist, Color.White.copy(alpha = 0.80f), Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${playlistSources.size} loaded from ${playlist.sourceIds.size} saved tracks  |  ${playlist.origin.label}",
                        color = Color.White.copy(alpha = 0.74f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                StreamSourcesSectionHeader(
                    title = "Tracklist",
                    subtitle = "${playlistSources.size} tracks  |  ${playlistSources.count { it.isOfflineSaved() }} saved offline",
                )
            }
            if (playlistSources.isEmpty()) {
                item { StreamInlineEmpty("This playlist has no loaded tracks yet. Add a related stream below.") }
            } else {
                itemsIndexed(playlistSources, key = { index, source -> "playlist-${playlist.id}-$index-${source.streamDistinctKey()}" }) { index, source ->
                    StreamCollectionAlbumTrackRow(
                        index = index + 1,
                        source = source,
                        resolving = resolvingSourceId == source.id,
                        isPlaying = activeSourceId == source.id,
                        onClick = { onPlaySource(source) },
                        onLongPress = { onSourceActions(source, playlistSources.map { it.id }) },
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulseIcon(DeckIcon.StreamEdit, Color.White.copy(alpha = 0.78f), Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Suggested additions use your library, listening history, and discovery matches. Long press a track above for source actions.", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                GlassTextField(query, "Add to playlist") { query = it }
            }
            item {
                Text(if (query.isBlank()) "Suggested additions" else "Search additions", color = Color.White, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
            }
            if (suggestions.isEmpty()) {
                item { StreamInlineEmpty("No related additions found yet") }
            } else {
                items(suggestions, key = { "playlist-suggestion-${it.streamDistinctKey()}" }) { source ->
                    PlaylistSuggestionRow(source = source, onAdd = { onAddSource(source) })
                }
            }
        }
    }
}

@Composable
private fun StreamPlaylistDetailHero(playlist: YouTubePlaylist, sources: List<YouTubeSource>) {
    val accent = Color(playlist.accentColor)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(138.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.94f), StreamDeepRed.copy(alpha = 0.74f), Color.Black.copy(alpha = 0.92f))))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp)),
        ) {
            if (sources.isNotEmpty()) {
                StreamCoverCollage(sources.take(4), Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PulseIcon(DeckIcon.Playlist, Color.White.copy(alpha = 0.82f), Modifier.size(48.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(playlist.origin.label, color = Color.White.copy(alpha = 0.88f), fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, color = Color.White, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                "${sources.size} tracks  |  ${sources.count { it.reaction == YouTubeReaction.Liked }} liked  |  ${sources.count { it.isOfflineSaved() }} saved",
                color = StreamTextSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun streamSourceLookup(sources: List<YouTubeSource>): Map<String, YouTubeSource> =
    buildMap {
        sources.forEach { source ->
            listOf(source.id, source.url, source.streamDistinctKey())
                .filter { it.isNotBlank() }
                .forEach { key -> putIfAbsent(key, source) }
        }
    }

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PlaylistEditTrackRow(source: YouTubeSource, resolving: Boolean, isPlaying: Boolean = false, onPlay: () -> Unit, onActions: () -> Unit, onRemove: () -> Unit) {
    val interactionSource = remember(source.id) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isPlaying) StreamAccentRed.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.065f))
            .border(
                1.dp,
                if (isPlaying) StreamAccentRed.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.055f),
                RoundedCornerShape(18.dp),
            )
            .pressScaleEffect(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
                onLongClick = onActions,
            )
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(46.dp), DeckIcon.StreamSource)
        Spacer(Modifier.width(10.dp))
        if (isPlaying) {
            PremiumDeckNowPlayingIndicator()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(source.title, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.author, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconTap(if (resolving) DeckIcon.Timer else DeckIcon.Play, onPlay, 32.dp)
        IconTap(DeckIcon.StreamRemove, onRemove, 32.dp)
    }
}

@Composable
internal fun PlaylistSuggestionRow(source: YouTubeSource, onAdd: () -> Unit) {
    val interactionSource = remember(source.id) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onAdd)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(46.dp), DeckIcon.Discover)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.author, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PulseIcon(DeckIcon.StreamAdd, Color.White.copy(alpha = 0.82f), Modifier.size(24.dp))
    }
}

@Composable
internal fun PlaylistChoiceRow(playlist: YouTubePlaylist, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (selected) 0.14f else 0.07f))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(if (playlist.origin == YouTubePlaylistOrigin.SavedMix) DeckIcon.StreamPin else DeckIcon.StreamEdit, Color.White.copy(alpha = if (selected) 0.92f else 0.70f), Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(playlist.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Text("Added", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun PlaylistSourceSelectRow(source: YouTubeSource, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (selected) 0.14f else 0.06f))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(if (selected) DeckIcon.StreamPin else DeckIcon.StreamSource, Color.White.copy(alpha = 0.82f), Modifier.size(21.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.author, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun GlassTextField(value: String, placeholder: String, onValue: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isBlank()) Text(placeholder, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                inner()
            },
        )
    }
}
