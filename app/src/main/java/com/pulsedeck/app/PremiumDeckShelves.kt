package com.pulsedeck.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun StreamSourcesSectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = StreamTextPrimary, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
internal fun PremiumDeckAlbumProjectShelf(
    drafts: List<AlbumDownloadDraft>,
    sources: List<YouTubeSource>,
    tasks: Map<String, AlbumProjectTaskState>,
    onOpen: (AlbumDownloadDraft) -> Unit,
    onNew: () -> Unit,
) {
    val visibleDrafts = remember(drafts) { drafts.distinctBy { it.release.id }.take(10) }
    Column(Modifier.fillMaxWidth()) {
        StreamSourcesSectionHeader(
            title = "Album projects",
            subtitle = "Metadata and tracklists staged for PremiumDeck matching",
        )
        Spacer(Modifier.height(12.dp))
        if (visibleDrafts.isEmpty()) {
            StreamInlineEmpty(
                message = "Build an album from artist and title, then match its tracklist in the next stage.",
                actionLabel = "Album Builder",
                showIcon = false,
                onAction = onNew,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    PremiumDeckAlbumProjectCreateCard(onNew)
                }
                items(visibleDrafts, key = { it.release.id }) { draft ->
                    PremiumDeckAlbumProjectCard(
                        draft = draft,
                        sources = sources,
                        taskState = tasks[draft.release.id],
                        onOpen = { onOpen(draft) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PremiumDeckAlbumTaskMiniBar(taskView: AlbumProjectTaskViewState) {
    val progress = when (taskView.task.phase) {
        AlbumProjectTaskPhase.TracklistLoading -> if (taskView.task.total > 0) ((taskView.task.completed.toFloat() / taskView.task.total.toFloat()) * 100f).roundToInt() else 8
        AlbumProjectTaskPhase.Matching -> if (taskView.task.total > 0) ((taskView.task.completed.toFloat() / taskView.task.total.toFloat()) * 100f).roundToInt() else 1
        AlbumProjectTaskPhase.OfflineSaving -> taskView.summary.progress
    }.coerceIn(1, 100)
    val label = when (taskView.task.phase) {
        AlbumProjectTaskPhase.TracklistLoading -> "Loading tracklist"
        AlbumProjectTaskPhase.Matching -> "Matching album"
        AlbumProjectTaskPhase.OfflineSaving -> "Saving album offline"
    }
    val status = when (taskView.task.phase) {
        AlbumProjectTaskPhase.TracklistLoading -> taskView.task.message.ifBlank { "Preparing album tracklist" }
        AlbumProjectTaskPhase.Matching -> "${taskView.task.completed.coerceIn(0, taskView.task.total)}/${taskView.task.total} checked"
        AlbumProjectTaskPhase.OfflineSaving -> "${taskView.summary.saved}/${taskView.summary.matched} saved  |  ${taskView.summary.saving} saving  |  ${taskView.summary.failed} failed"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDisc(Color(0xFFFFD166).copy(alpha = 0.25f), if (taskView.task.phase == AlbumProjectTaskPhase.OfflineSaving) DeckIcon.StreamOffline else DeckIcon.Timer, Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${taskView.release.artist} - ${taskView.release.title}", color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Text(status, color = StreamTextSecondary.copy(alpha = 0.82f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        OfflineDownloadProgress(progress, Modifier.padding(top = 10.dp), label = label)
    }
}

@Composable
internal fun PremiumDeckAlbumProjectCreateCard(onClick: () -> Unit) {
    PremiumDeckCreateActionCard(
        title = "Album Builder",
        subtitle = "Find tracklist",
        icon = DeckIcon.Disc,
        accent = StreamAccentRed,
        onClick = onClick,
    )
}

@Composable
internal fun PremiumDeckPlaylistCreateCard(onClick: () -> Unit) {
    PremiumDeckCreateActionCard(
        title = "Playlist Builder",
        subtitle = "Create playlist",
        icon = DeckIcon.Playlist,
        accent = StreamAccentRed,
        onClick = onClick,
    )
}

@Composable
private fun PremiumDeckCreateActionCard(
    title: String,
    subtitle: String,
    icon: DeckIcon,
    accent: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .width(154.dp)
            .height(182.dp)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PremiumDeckCreateCardSoftGlow(accent, Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconDisc(accent.copy(alpha = 0.24f), icon, Modifier.size(56.dp))
            Text(title, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 13.dp))
            Text(subtitle, color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun PremiumDeckCreateCardSoftGlow(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(horizontal = 3.dp, vertical = 2.dp)) {
        val center = Offset(size.width * 0.52f, size.height * 0.54f)
        val radius = size.maxDimension * 0.54f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 0.24f),
                    accent.copy(alpha = 0.105f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            center = center,
            radius = radius,
        )
        val edgeLight = Offset(size.width * 0.72f, size.height * 0.28f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    accent.copy(alpha = 0.085f),
                    Color.Transparent,
                ),
                center = edgeLight,
                radius = size.maxDimension * 0.34f,
            ),
            center = edgeLight,
            radius = size.maxDimension * 0.34f,
        )
    }
}

@Composable
internal fun PremiumDeckAlbumProjectCard(
    draft: AlbumDownloadDraft,
    sources: List<YouTubeSource>,
    taskState: AlbumProjectTaskState?,
    onOpen: () -> Unit,
) {
    val release = draft.release
    val interactionSource = remember(release.id) { MutableInteractionSource() }
    val summary = remember(release, sources) { release.albumProjectProgressSummary(sources) }
    Row(
        Modifier
            .width(292.dp)
            .heightIn(min = 164.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            YouTubeThumbnailBox(release.coverUrl, Modifier.size(104.dp), DeckIcon.Disc)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.Black.copy(alpha = 0.44f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            ) {
                Text("ALBUM", color = Color.White.copy(alpha = 0.88f), fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(release.title, color = StreamTextPrimary, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(release.artist, color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Text(release.albumDownloaderMetaLine(), color = StreamTextSecondary.copy(alpha = 0.78f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            if (taskState != null || summary.matched > 0) {
                val progress = when (taskState?.phase) {
                    AlbumProjectTaskPhase.TracklistLoading -> if (taskState.total > 0) ((taskState.completed.toFloat() / taskState.total.toFloat()) * 100f).roundToInt() else 8
                    AlbumProjectTaskPhase.Matching -> if (taskState.total > 0) ((taskState.completed.toFloat() / taskState.total.toFloat()) * 100f).roundToInt() else 1
                    AlbumProjectTaskPhase.OfflineSaving -> summary.progress
                    null -> summary.progress.takeIf { it > 0 } ?: 1
                }.coerceIn(1, 100)
                val label = when (taskState?.phase) {
                    AlbumProjectTaskPhase.TracklistLoading -> "Loading"
                    AlbumProjectTaskPhase.Matching -> "Matching"
                    AlbumProjectTaskPhase.OfflineSaving -> "Offline"
                    null -> "${summary.matched}/${release.tracks.size} matched"
                }
                OfflineDownloadProgress(progress, Modifier.padding(top = 8.dp), label = label)
            }
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                SourceActionButton("Tracklist", DeckIcon.MusicList, Modifier.weight(1f), onOpen)
                IconTap(DeckIcon.More, onOpen, 36.dp)
            }
        }
    }
}

@Composable
internal fun StreamSourceShelf(title: String, subtitle: String, sources: List<YouTubeSource>, emptyTitle: String, emptyIconRes: Int? = null, showEmptyIcon: Boolean = true, onSource: (YouTubeSource) -> Unit) {
    val visibleSources = remember(sources) { sources.distinctBy { it.streamDistinctKey() }.take(12) }
    Column(Modifier.fillMaxWidth()) {
        StreamSourcesSectionHeader(title, subtitle)
        Spacer(Modifier.height(12.dp))
        if (visibleSources.isEmpty()) {
            StreamInlineEmpty(emptyTitle, iconRes = emptyIconRes, showIcon = showEmptyIcon)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleSources, key = { it.streamDistinctKey() }) { source ->
                    StreamSourceMiniCard(source = source, onClick = { onSource(source) })
                }
            }
        }
    }
}

@Composable
internal fun StreamSourceMiniCard(source: YouTubeSource, onClick: () -> Unit) {
    val interactionSource = remember(source.id) { MutableInteractionSource() }
    Column(
        Modifier
            .width(142.dp)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box {
            YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.fillMaxWidth().aspectRatio(1f), fallbackIcon = DeckIcon.Play)
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f)))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(DeckIcon.Play, Color.White, Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(source.title, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(source.author, color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp))
    }
}

@Composable
internal fun StreamCollectionShelf(
    title: String,
    subtitle: String,
    collections: List<StreamCollection>,
    emptyTitle: String,
    onOpen: (StreamCollection) -> Unit,
    onPlay: (StreamCollection) -> Unit,
    onSave: (StreamCollection) -> Unit,
) {
    val visibleCollections = remember(collections) { collections.distinctBy { it.id }.take(8) }
    Column(Modifier.fillMaxWidth()) {
        StreamSourcesSectionHeader(title, subtitle)
        Spacer(Modifier.height(12.dp))
        if (visibleCollections.isEmpty()) {
            StreamInlineEmpty(emptyTitle)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleCollections, key = { it.id }) { collection ->
                    StreamCollectionCard(collection = collection, onOpen = { onOpen(collection) }, onPlay = { onPlay(collection) }, onSave = { onSave(collection) })
                }
            }
        }
    }
}

@Composable
internal fun StreamCollectionCard(collection: StreamCollection, onOpen: () -> Unit, onPlay: () -> Unit, onSave: () -> Unit) {
    val interactionSource = remember(collection.id) { MutableInteractionSource() }
    val badgeIcon = if (collection.kind == StreamCollectionKind.Artist) DeckIcon.StreamRadio else DeckIcon.StreamMix
    val badgeLabel = if (collection.canSave) "Generated mix" else collection.kind.label
    val albumLike = collection.kind == StreamCollectionKind.AlbumLike
    val cardWidth = if (albumLike) 178.dp else 204.dp
    val artHeight = if (albumLike) 126.dp else 96.dp
    Column(
        Modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(collection.accentColor).copy(alpha = 0.14f),
                        Color.Black.copy(alpha = 0.12f),
                        Color.Black.copy(alpha = 0.18f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(10.dp),
    ) {
        Box {
            StreamCoverCollage(collection.sources, Modifier.fillMaxWidth().height(artHeight))
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseIcon(badgeIcon, Color.White, Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(badgeLabel, color = Color.White.copy(alpha = 0.88f), fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
            if (!albumLike) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text("MIX", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(collection.title, color = StreamTextPrimary, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(collection.subtitle, color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceActionButton("Play", DeckIcon.Play, Modifier.weight(1f), onPlay)
            if (collection.canSave) {
                IconTap(DeckIcon.Bookmark, onSave, 38.dp)
            }
            IconTap(DeckIcon.More, onOpen, 38.dp)
        }
    }
}

@Composable
internal fun StreamCoverCollage(sources: List<YouTubeSource>, modifier: Modifier = Modifier) {
    val covers = sources.filter { !it.bestThumbnailUrl().isNullOrBlank() }.take(4).ifEmpty { sources.take(1) }
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        if (covers.size >= 4) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    YouTubeThumbnailBox(covers[0].bestThumbnailUrl(), Modifier.weight(1f).fillMaxSize(), DeckIcon.StreamMix)
                    YouTubeThumbnailBox(covers[1].bestThumbnailUrl(), Modifier.weight(1f).fillMaxSize(), DeckIcon.StreamMix)
                }
                Row(Modifier.weight(1f)) {
                    YouTubeThumbnailBox(covers[2].bestThumbnailUrl(), Modifier.weight(1f).fillMaxSize(), DeckIcon.StreamMix)
                    YouTubeThumbnailBox(covers[3].bestThumbnailUrl(), Modifier.weight(1f).fillMaxSize(), DeckIcon.StreamMix)
                }
            }
        } else {
            YouTubeThumbnailBox(covers.firstOrNull()?.bestThumbnailUrl(), Modifier.fillMaxSize(), DeckIcon.StreamMix)
        }
    }
}

@Composable
internal fun StreamNewReleaseShelf(
    followedArtists: List<FollowedStreamArtist>,
    collection: StreamCollection?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpen: (StreamCollection) -> Unit,
    onPlay: (StreamCollection) -> Unit,
    onSave: (StreamCollection) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("New Releases", color = StreamTextPrimary, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (followedArtists.isEmpty()) "Follow artists to build a weekly release playlist" else "Weekly playlist from followed PremiumDeck artists",
                    color = StreamTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            SourceActionButton(if (loading) "Refreshing" else "Refresh", DeckIcon.StreamRecent, Modifier.width(104.dp), onRefresh, enabled = !loading)
        }
        Spacer(Modifier.height(12.dp))
        if (collection == null) {
            StreamInlineEmpty(
                if (followedArtists.isEmpty()) {
                    "Use the heart on an artist card or Follow Artist in a source menu"
                } else if (loading) {
                    "Checking followed artists for new PremiumDeck releases"
                } else {
                    "No new releases found yet. PremiumDeck refreshes this weekly"
                },
                actionLabel = if (followedArtists.isEmpty()) null else "Refresh",
                onAction = if (followedArtists.isEmpty()) null else onRefresh,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = collection.id) {
                    StreamCollectionCard(
                        collection = collection,
                        onOpen = { onOpen(collection) },
                        onPlay = { onPlay(collection) },
                        onSave = { onSave(collection) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun VerifiedAlbumRecommendationShelf(
    snapshot: RecommendedAlbumsSnapshot?,
    generatedCollections: List<StreamCollection>,
    onAlbum: (VerifiedAlbumCandidate) -> Unit,
    onGeneratedOpen: (StreamCollection) -> Unit,
    onGeneratedPlay: (StreamCollection) -> Unit,
    onGeneratedSave: (StreamCollection) -> Unit,
) {
    val albums = remember(snapshot) { snapshot?.albums.orEmpty().take(RECOMMENDED_ALBUM_CARD_COUNT) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.fillMaxWidth()) {
            StreamSourcesSectionHeader(
                title = "Albums for you",
                subtitle = "Five verified releases, refreshed every few days",
            )
            Spacer(Modifier.height(12.dp))
            if (albums.isEmpty()) {
                StreamInlineEmpty("Verified album recommendations appear after a cached refresh.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums, key = { it.stableAlbumId }) { album ->
                        VerifiedAlbumRecommendationCard(
                            album = album,
                            onOpen = { onAlbum(album) },
                            onSave = { onAlbum(album) },
                        )
                    }
                }
            }
        }
        if (generatedCollections.isNotEmpty()) {
            StreamCollectionShelf(
                title = "Generated for you",
                subtitle = "Generated mixes and album-style source groups stay separate from verified releases",
                collections = generatedCollections,
                emptyTitle = "Generated mixes appear after PremiumDeck listens or discovery refreshes",
                onOpen = onGeneratedOpen,
                onPlay = onGeneratedPlay,
                onSave = onGeneratedSave,
            )
        }
    }
}

@Composable
private fun VerifiedAlbumRecommendationCard(
    album: VerifiedAlbumCandidate,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    val interactionSource = remember(album.stableAlbumId) { MutableInteractionSource() }
    Column(
        Modifier
            .width(178.dp)
            .heightIn(min = 232.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(StreamGlassFill)
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(10.dp),
    ) {
        Box {
            YouTubeThumbnailBox(
                album.artworkUrl,
                Modifier.fillMaxWidth().aspectRatio(1f),
                fallbackIcon = DeckIcon.Disc,
                artworkUseCase = ArtworkUseCase.AlbumGridThumbnail,
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VerifiedAlbumPill("Album")
            }
        }
        Text(
            album.title,
            color = StreamTextPrimary,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(
            album.artistName,
            color = StreamTextSecondary,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        val year = album.releaseYear?.toString() ?: album.releaseDate?.take(4).orEmpty()
        if (year.isNotBlank()) {
            Text(
                year,
                color = StreamTextMuted,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        VerifiedAlbumPill(
            label = album.recommendationReason.displayLabel,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceActionButton("Open", DeckIcon.Disc, Modifier.weight(1f), onOpen)
            IconTap(DeckIcon.Bookmark, onSave, 36.dp)
        }
    }
}

@Composable
private fun VerifiedAlbumPill(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(13.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.88f), fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private val AlbumRecommendationReason.displayLabel: String
    get() = when (this) {
        AlbumRecommendationReason.BecauseYouListened -> "Because you listened"
        AlbumRecommendationReason.NewFromArtist -> "New from artist"
        AlbumRecommendationReason.PopularNow -> "Popular now"
        AlbumRecommendationReason.SimilarArtist -> "Similar artist"
        AlbumRecommendationReason.ExploreSomethingNew -> "Explore"
    }

@Composable
internal fun StreamArtistShelf(
    artists: List<StreamCollection>,
    followedArtistKeys: Set<String>,
    onOpen: (StreamCollection) -> Unit,
    onPlay: (StreamCollection) -> Unit,
    onToggleFollowArtist: (String) -> Unit,
) {
    val visibleArtists = remember(artists) { artists.distinctBy { it.id }.take(10) }
    Column(Modifier.fillMaxWidth()) {
        StreamSourcesSectionHeader("Favorite artists", "Follow artists to track weekly PremiumDeck releases")
        Spacer(Modifier.height(12.dp))
        if (visibleArtists.isEmpty()) {
            StreamInlineEmpty("Artists will appear after you replay or like streams")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleArtists, key = { it.id }) { artist ->
                    StreamArtistChip(
                        artist = artist,
                        followed = artist.title.cleanStreamArtistName().normalizedSearchText() in followedArtistKeys,
                        onOpen = { onOpen(artist) },
                        onPlay = { onPlay(artist) },
                        onFollow = { onToggleFollowArtist(artist.title) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun StreamArtistChip(artist: StreamCollection, followed: Boolean, onOpen: () -> Unit, onPlay: () -> Unit, onFollow: () -> Unit) {
    val interactionSource = remember(artist.id) { MutableInteractionSource() }
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            YouTubeThumbnailBox(artist.sources.firstOrNull()?.bestThumbnailUrl(), Modifier.size(88.dp).clip(CircleShape), DeckIcon.Person)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(if (followed) DeckIcon.StreamLike else DeckIcon.Heart, Color.White.copy(alpha = 0.86f), Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(artist.title, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${artist.sources.size} tracks", color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconTap(DeckIcon.Play, onPlay, 32.dp)
            IconTap(if (followed) DeckIcon.StreamLike else DeckIcon.Heart, onFollow, 32.dp)
            IconTap(DeckIcon.More, onOpen, 32.dp)
        }
    }
}

@Composable
internal fun StreamPlaylistShelf(
    playlists: List<YouTubePlaylist>,
    sources: List<YouTubeSource>,
    onPlaylist: (YouTubePlaylist) -> Unit,
    onPlaylistActions: (YouTubePlaylist) -> Unit,
    onPlay: (YouTubePlaylist) -> Unit,
    onShuffle: (YouTubePlaylist) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    val sourceById = remember(sources) { streamSourceIdentityLookup(sources) }
    val visiblePlaylists = remember(playlists) { playlists.distinctBy { it.id }.take(10) }
    Column(Modifier.fillMaxWidth()) {
        StreamSourcesSectionHeader("Saved playlists", "Manual playlists and saved virtual mixes")
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PremiumDeckPlaylistCreateCard(onNewPlaylist)
            }
            items(visiblePlaylists, key = { it.id }) { playlist ->
                val playlistSources = remember(playlist.sourceIds, sourceById) {
                    playlist.sourceIds.mapNotNull { sourceById[it] }.take(4)
                }
                StreamPlaylistCard(
                    playlist = playlist,
                    sources = playlistSources,
                    onOpen = { onPlaylist(playlist) },
                    onActions = { onPlaylistActions(playlist) },
                    onPlay = { onPlay(playlist) },
                    onShuffle = { onShuffle(playlist) },
                )
            }
        }
    }
}

@Composable
internal fun StreamPlaylistCard(playlist: YouTubePlaylist, sources: List<YouTubeSource>, onOpen: () -> Unit, onActions: () -> Unit, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val interactionSource = remember(playlist.id) { MutableInteractionSource() }
    val originIcon = if (playlist.origin == YouTubePlaylistOrigin.SavedMix) DeckIcon.StreamPin else DeckIcon.Playlist
    Row(
        Modifier
            .width(276.dp)
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(98.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(playlist.accentColor).copy(alpha = 0.50f), StreamDeepRed.copy(alpha = 0.50f))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (sources.isEmpty()) {
                PulseIcon(originIcon, Color.White, Modifier.size(34.dp))
            } else {
                StreamCoverCollage(sources, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.Black.copy(alpha = 0.24f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(15.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIcon(originIcon, Color.White.copy(alpha = 0.88f), Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(playlist.origin.label, color = Color.White.copy(alpha = 0.82f), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Text(playlist.title, color = StreamTextPrimary, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            Text("${playlist.sourceIds.size} sources", color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                IconTap(DeckIcon.Play, onPlay, 36.dp)
                IconTap(DeckIcon.Shuffle, onShuffle, 36.dp)
                IconTap(DeckIcon.More, onActions, 36.dp)
            }
        }
    }
}

@Composable
internal fun CompactSourceRow(
    source: YouTubeSource,
    resolving: Boolean,
    isPlaying: Boolean = false,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onBookmark: () -> Unit,
    onInfo: () -> Unit,
) {
    val interactionSource = remember(source.id) { MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compactActions = maxWidth < 390.dp
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isPlaying) StreamAccentRed.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f))
                .border(
                    1.dp,
                    if (isPlaying) StreamAccentRed.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.04f),
                    RoundedCornerShape(18.dp),
                )
                .pressScaleEffect(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
                .padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(52.dp), DeckIcon.Play)
            Spacer(Modifier.width(10.dp))
            if (isPlaying) {
                PremiumDeckNowPlayingIndicator()
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(source.title, color = Color.White, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(source.author, color = Muted, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                val meta = buildList {
                    if (source.durationMillis > 0L) add(formatDuration(source.durationMillis))
                    if (source.playCount > 0) add("${source.playCount} plays")
                    if (source.downloadState == YouTubeDownloadState.Downloaded || source.status == YouTubeSourceStatus.Downloaded) add("saved")
                }.joinToString("  |  ")
                Text(meta.ifBlank { source.quality.label }, color = Muted.copy(alpha = 0.68f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                if (source.downloadState == YouTubeDownloadState.Downloading) {
                    OfflineDownloadProgress(source.downloadProgress, Modifier.padding(top = 5.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(if (compactActions) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
                StreamSourceActionButton(if (resolving) DeckIcon.Timer else DeckIcon.Play, selected = true, onClick = onOpen)
                StreamSourceActionButton(DeckIcon.Heart, selected = source.reaction == YouTubeReaction.Liked, onClick = onLike)
                StreamSourceActionButton(DeckIcon.Bookmark, selected = source.bookmarked, onClick = onBookmark)
                if (!compactActions) {
                    StreamSourceActionButton(DeckIcon.ThumbDown, selected = source.reaction == YouTubeReaction.Disliked, onClick = onDislike)
                    StreamSourceActionButton(DeckIcon.Info, selected = false, onClick = onInfo)
                } else {
                    StreamSourceActionButton(DeckIcon.More, selected = false, onClick = onInfo)
                }
            }
        }
    }
}

@Composable
internal fun PremiumDeckNowPlayingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "premiumDeckNowPlaying")
    Row(
        modifier
            .size(width = 24.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, StreamAccentRed.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val level by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 430 + index * 90, delayMillis = index * 70),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "premiumDeckNowPlayingBar$index",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height((7f + 9f * level).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.84f)),
            )
        }
    }
}

@Composable
internal fun StreamSourceActionButton(icon: DeckIcon, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember(icon, selected) { MutableInteractionSource() }
    Box(
        Modifier
            .size(31.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent, CircleShape)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, if (selected) Color.White else Color.White.copy(alpha = 0.74f), Modifier.size(17.dp))
    }
}

@Composable
internal fun StreamInlineEmpty(message: String, actionLabel: String? = null, iconRes: Int? = null, showIcon: Boolean = true, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(StreamGlassFill)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                PulseIcon(DeckIcon.EmptyBox, Color.White.copy(alpha = 0.72f), Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(message, color = StreamTextSecondary, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            SleepDialogButton(actionLabel, Modifier.width(126.dp), tone = Color.White.copy(alpha = 0.10f), onClick = onAction)
        }
    }
}

@Composable
internal fun YouTubeEmptyState(onAdd: () -> Unit, title: String = "Paste a link to start", subtitle: String = "Videos, playlists, and channel handles stay in this virtual shelf before they become local files.", buttonLabel: String = "Quick Add", iconRes: Int? = null, showIcon: Boolean = true) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(0.07f), RoundedCornerShape(28.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showIcon) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                IconDisc(Color(0xFF7FE7C3), DeckIcon.EmptyBox, Modifier.size(72.dp))
            }
        }
        Text(title, color = StreamTextPrimary, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = if (showIcon) 16.dp else 0.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = StreamTextSecondary, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        SleepDialogButton(buttonLabel, Modifier.fillMaxWidth().padding(top = 18.dp), tone = Color.White.copy(alpha = 0.12f), onClick = onAdd)
    }
}

@Composable
internal fun YouTubeTabRow(selected: YouTubeLibraryTab, onTab: (YouTubeLibraryTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        YouTubeLibraryTab.entries.forEach { tab ->
            val active = tab == selected
            val interactionSource = remember(tab) { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                    .pressScaleEffect(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) { onTab(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(tab.label, color = Color.White.copy(alpha = if (active) 0.96f else 0.54f), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun YouTubeSmartShelves(sources: List<YouTubeSource>, activeShelf: YouTubeSmartShelf?, onShelf: (YouTubeSmartShelf?) -> Unit) {
    val counts = remember(sources) {
        mapOf(
            YouTubeSmartShelf.Inbox to sources.count { it.reviewState == YouTubeReviewState.Inbox },
            YouTubeSmartShelf.Recent to sources.sortedByDescending { it.addedMillis }.take(12).size,
            YouTubeSmartShelf.MostPlayed to sources.count { it.playCount > 0 },
            YouTubeSmartShelf.SavedOffline to sources.count { it.downloadState == YouTubeDownloadState.Downloaded || it.status == YouTubeSourceStatus.Downloaded },
            YouTubeSmartShelf.Podcasts to sources.count { it.isPodcast },
            YouTubeSmartShelf.NeedsMetadata to sources.count { it.needsYouTubeMetadataReview() },
        )
    }
    LazyRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            YouTubeShelfCompactChip(
                label = "All",
                count = sources.count { it.reviewState == YouTubeReviewState.Accepted },
                icon = DeckIcon.StreamSource,
                active = activeShelf == null,
            ) { onShelf(null) }
        }
        items(YouTubeSmartShelf.entries, key = { it.name }) { shelf ->
            YouTubeShelfCompactChip(
                label = shelf.label,
                count = counts[shelf] ?: 0,
                icon = shelf.icon,
                active = activeShelf == shelf,
            ) { onShelf(shelf) }
        }
    }
}

@Composable
internal fun YouTubeShelfCompactChip(label: String, count: Int, icon: DeckIcon, active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    Row(
        Modifier
            .height(38.dp)
            .width(116.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (active) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.16f else 0.06f), RoundedCornerShape(19.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = if (active) 0.92f else 0.64f), Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White.copy(alpha = if (active) 0.96f else 0.66f), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(count.toString(), color = Color.White.copy(alpha = if (active) 0.90f else 0.54f), fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun YouTubePlaylistRow(
    playlist: YouTubePlaylist,
    sources: List<YouTubeSource>,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val sourceById = remember(sources) { streamSourceIdentityLookup(sources) }
    val playlistSources = remember(playlist.sourceIds, sourceById) { playlist.sourceIds.mapNotNull { sourceById[it] } }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDisc(Color(playlist.accentColor), if (playlist.origin == YouTubePlaylistOrigin.SavedMix) DeckIcon.StreamPin else DeckIcon.StreamEdit, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, color = StreamTextPrimary, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.origin.label}  |  ${playlist.description.ifBlank { "Stream playlist" }}", color = StreamTextSecondary, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlistSources.size} sources  |  ${playlistSources.count { it.downloadState == YouTubeDownloadState.Downloaded }} saved  |  ${playlistSources.count { it.isPodcast }} podcasts", color = StreamTextMuted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconTap(DeckIcon.Play, onPlay, 34.dp)
        IconTap(DeckIcon.Shuffle, onShuffle, 34.dp)
        IconTap(DeckIcon.More, onActions, 34.dp)
    }
}

@Composable
internal fun YouTubeSourceRow(
    source: YouTubeSource,
    resolving: Boolean,
    capabilities: ResolverCapabilities,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onKeepOffline: () -> Unit,
    onToggleSkip: () -> Unit,
    onToggleTrim: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(58.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(source.title, color = Color.White, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    YouTubeStatusBadge(source.status, source.downloadState, source.downloadProgress)
                }
                Text(source.author, color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val durationLabel = if (source.durationMillis > 0L) "${formatDuration(source.durationMillis)}  |  " else ""
                Text(
                    "${if (source.reviewState == YouTubeReviewState.Inbox) "Inbox  |  " else ""}$durationLabel${source.kind.label}  |  ${source.quality.label}  |  ${if (source.isPodcast) "Podcast" else "Music"}",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconTap(if (resolving) DeckIcon.Timer else DeckIcon.Play, onOpen, 34.dp)
            IconTap(if (source.downloadState == YouTubeDownloadState.Downloading) DeckIcon.Timer else DeckIcon.StreamOffline, onKeepOffline, 34.dp)
            IconTap(DeckIcon.Info, onInfo, 34.dp)
            IconTap(DeckIcon.StreamRemove, onRemove, 34.dp)
        }
        if (source.reviewState == YouTubeReviewState.Inbox) {
            val suggestion = source.cleanedSuggestion()
            Text("Suggested: ${suggestion.first} - ${suggestion.second}", color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (source.downloadState == YouTubeDownloadState.Downloading) {
            OfflineDownloadProgress(source.downloadProgress, Modifier.padding(top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (source.reviewState == YouTubeReviewState.Inbox) {
                SourceTogglePill("Accept", true, Modifier.weight(1f), onClick = onAccept)
            }
            SourceTogglePill(if (source.skipSegmentsEnabled) "Skip segments" else "Segments off", source.skipSegmentsEnabled, Modifier.weight(1f), onClick = onToggleSkip)
            SourceTogglePill(
                if (capabilities.ffmpegAvailable) "Trim silence" else "No trim service",
                source.trimSilenceOnDownload,
                Modifier.weight(1f),
                enabled = capabilities.ffmpegAvailable,
                onClick = onToggleTrim,
            )
        }
    }
}

@Composable
internal fun SourceTogglePill(label: String, active: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (active) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.20f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.13f else 0.05f), RoundedCornerShape(15.dp))
            .then(if (enabled) Modifier.pressScaleEffect(interactionSource).clickable(interactionSource = interactionSource, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 0.78f else 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}
