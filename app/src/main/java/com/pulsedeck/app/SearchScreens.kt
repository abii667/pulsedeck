package com.pulsedeck.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlin.math.absoluteValue

internal val LocalYouTubeThumbnailNetworkAllowed = staticCompositionLocalOf { true }

@Composable
internal fun SearchScreen(
    results: List<Track>,
    query: String,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onTrack: (Track) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
        PageNavigationHeader("Search", onBack = onBack, subtitle = "Local Library", modifier = Modifier.padding(top = 24.dp, bottom = 14.dp))
        Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black.copy(0.28f)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            PulseIcon(DeckIcon.Search, Color.White.copy(0.72f), Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Track, artist, album", color = Muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(22.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 184.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.stableKey() }) { TrackRow(it, active = false) { onTrack(it) } }
        }
    }
}

@Composable
internal fun SearchDiscoverySectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            val actionModifier = if (onAction != null) {
                Modifier.clickable(onClick = onAction).padding(start = 12.dp)
            } else {
                Modifier.padding(start = 12.dp)
            }
            Text(
                actionLabel,
                color = StreamAccentRed,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = actionModifier,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchDiscoveryCarousel(
    genres: List<StreamDiscoveryGenre>,
    collectionByGenre: Map<String, StreamCollection>,
    loadingGenreId: String?,
    activePreviewSourceId: String?,
    onLoadGenre: (StreamDiscoveryGenre) -> Unit,
    onPreview: (StreamCollection, YouTubeSource) -> Unit,
    onOpenGenre: (StreamDiscoveryGenre) -> Unit,
) {
    if (genres.isEmpty()) return
    val visibleGenres = remember(genres) { genres.take(6) }
    val pagerState = rememberPagerState(pageCount = { visibleGenres.size })
    var carouselActivated by remember { mutableStateOf(false) }
    val currentGenre = visibleGenres.getOrNull(pagerState.currentPage)
    val currentCollection = currentGenre?.let { collectionByGenre[it.id] }
    val previewSource = remember(currentCollection?.id, currentCollection?.sources) {
        currentCollection?.sources
            .orEmpty()
            .firstOrNull { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) carouselActivated = true
        }
    }
    LaunchedEffect(currentGenre?.id, carouselActivated) {
        if (carouselActivated) currentGenre?.let(onLoadGenre)
    }
    LaunchedEffect(currentCollection?.id, previewSource?.streamDistinctKey(), activePreviewSourceId, carouselActivated) {
        if (!carouselActivated) return@LaunchedEffect
        val collection = currentCollection ?: return@LaunchedEffect
        val source = previewSource ?: return@LaunchedEffect
        if (activePreviewSourceId != source.id) {
            onPreview(collection, source)
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SearchDiscoverySectionHeader("Swipe to Find Your Vibe")
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(170.dp),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 3.dp),
        ) { page ->
            val genre = visibleGenres[page]
            val collection = collectionByGenre[genre.id]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            SearchDiscoveryCarouselCard(
                genre = genre,
                collection = collection,
                loading = loadingGenreId == genre.id,
                active = collection?.sources?.any { it.id == activePreviewSourceId } == true,
                modifier = Modifier.graphicsLayer {
                    val scale = 0.94f + (1f - pageOffset) * 0.06f
                    alpha = 0.66f + (1f - pageOffset) * 0.34f
                    scaleX = scale
                    scaleY = scale
                },
                onClick = { onOpenGenre(genre) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            visibleGenres.forEachIndexed { index, _ ->
                val active = index == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) StreamAccentRed else Color.White.copy(alpha = 0.24f)),
                )
            }
        }
    }
}

@Composable
private fun SearchDiscoveryCarouselCard(
    genre: StreamDiscoveryGenre,
    collection: StreamCollection?,
    loading: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = Color(genre.accentColor)
    val interactionSource = remember(genre.id) { MutableInteractionSource() }
    val coverSources = remember(collection?.id, collection?.sources) {
        collection?.sources
            .orEmpty()
            .filter { it.kind == YouTubeSourceKind.Video }
            .distinctBy { it.streamDistinctKey() }
            .take(4)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.90f), StreamDeepRed.copy(alpha = 0.58f))))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.20f else 0.10f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        if (coverSources.isNotEmpty()) {
            StreamCoverCollage(coverSources, Modifier.fillMaxSize())
        } else {
            PulseIcon(DeckIcon.Discover, Color.White.copy(alpha = 0.22f), Modifier.size(82.dp).align(Alignment.Center))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.16f),
                            Color.Black.copy(alpha = 0.34f),
                            Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 84.dp)) {
            Text(genre.title, color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(discoveryPreviewCountLabel(genre, collection), color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Box(Modifier.padding(horizontal = 8.dp).size(4.dp).clip(CircleShape).background(StreamAccentRed))
                Text(discoveryShortSubtitle(genre), color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 15.dp, bottom = 14.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(StreamAccentRed.copy(alpha = if (loading) 0.56f else 0.88f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(if (loading) DeckIcon.Timer else DeckIcon.Play, Color.White, Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun SearchDiscoveryGenreCard(
    genre: StreamDiscoveryGenre,
    collection: StreamCollection?,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(genre.id) { MutableInteractionSource() }
    val accent = Color(genre.accentColor)
    val coverSources = remember(collection?.id, collection?.sources) {
        collection?.sources
            .orEmpty()
            .filter { it.kind == YouTubeSourceKind.Video }
            .distinctBy { it.streamDistinctKey() }
            .take(4)
    }
    Box(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(0.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.80f),
                            Color.Black.copy(alpha = 0.82f),
                            StreamDeepRed.copy(alpha = 0.54f),
                        ),
                    ),
                ),
        )
        if (coverSources.isNotEmpty()) {
            StreamCoverCollage(coverSources, Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.88f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 44.dp, bottom = 9.dp)) {
            Text(genre.title, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(discoveryShortSubtitle(genre), color = Color.White.copy(alpha = 0.70f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Text(if (loading) "loading" else discoveryPreviewCountLabel(genre, collection), color = Color.White.copy(alpha = 0.80f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(29.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f))
                .border(1.dp, StreamAccentRed.copy(alpha = 0.58f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(if (loading) DeckIcon.Timer else DeckIcon.Play, Color.White, Modifier.size(14.dp))
        }
    }
}

private fun discoveryShortSubtitle(genre: StreamDiscoveryGenre): String =
    when (genre.id) {
        "beast-mode-hip-hop" -> "Hard-hitting rap energy"
        "pump-up-pop" -> "High-energy pop hooks"
        "country-tailgate" -> "Loud weekend anthems"
        "lofi-loft" -> "Chillhop & soft-focus grooves"
        "cuffin-season" -> "R&B vibes for late nights"
        "relaxing-evening" -> "Calm playlists to unwind"
        else -> genre.subtitle
    }

private fun discoveryPreviewCountLabel(genre: StreamDiscoveryGenre, collection: StreamCollection?): String {
    val loadedCount = collection?.sources?.size?.takeIf { it > 0 }
    return previewCountLabel((loadedCount ?: discoveryExpectedPreviewCount(genre)).coerceAtMost(18))
}

private fun discoveryExpectedPreviewCount(genre: StreamDiscoveryGenre): Int =
    when (genre.id) {
        "beast-mode-hip-hop" -> 8
        "pump-up-pop" -> 18
        "country-tailgate" -> 8
        "lofi-loft" -> 1
        "cuffin-season" -> 6
        "relaxing-evening" -> 12
        else -> 8
    }

private fun previewCountLabel(count: Int?): String {
    val safeCount = count?.coerceAtMost(18) ?: 0
    return "$safeCount preview${if (safeCount == 1) "" else "s"}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchDiscoveryPreviewDeck(
    collection: StreamCollection,
    activePreviewSourceId: String?,
    followedArtistKeys: Set<String>,
    onDismiss: () -> Unit,
    onPreview: (YouTubeSource) -> Unit,
    onPlayFull: (YouTubeSource) -> Unit,
    onToggleFollowArtist: (String) -> Unit,
) {
    val sources = remember(collection.id, collection.sources) {
        collection.sources
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
            .distinctBy { it.streamDistinctKey() }
            .take(18)
    }
    if (sources.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { sources.size })
    val motion = LocalPulseMotionSpec.current
    var visible by remember(collection.id) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = motion.duration(PulseMotion.Duration.Panel), easing = PulseMotion.Easing.Emphasized),
        label = "searchDiscoveryDeck",
    )
    LaunchedEffect(collection.id) { visible = true }
    LaunchedEffect(collection.id, pagerState.currentPage) {
        val page = pagerState.currentPage
        val source = sources.getOrNull(page) ?: return@LaunchedEffect
        onPreview(source)
        delay(32_000L)
        if (pagerState.currentPage == page && !pagerState.isScrollInProgress && page < sources.lastIndex) {
            pagerState.animateScrollToPage(page + 1)
        }
    }
    BackHandler { onDismiss() }

    Box(
        Modifier
            .fillMaxSize()
            .background(StreamBase.copy(alpha = 0.98f))
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 42f
                scaleX = 0.985f + progress * 0.015f
                scaleY = 0.985f + progress * 0.015f
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(collection.title, color = Color.White, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${sources.size} previews  |  ${collection.subtitle}", color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconCircle(DeckIcon.Close, onDismiss, 44.dp)
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                pageSpacing = 16.dp,
            ) { page ->
                val source = sources[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                SearchDiscoveryPreviewPage(
                    source = source,
                    collection = collection,
                    active = source.id == activePreviewSourceId,
                    modifier = Modifier
                        .fillMaxHeight()
                        .graphicsLayer {
                            val scale = 0.92f + (1f - pageOffset) * 0.08f
                            alpha = 0.56f + (1f - pageOffset) * 0.44f
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
            val selected = sources.getOrNull(pagerState.currentPage) ?: sources.first()
            SearchDiscoveryPreviewBottomBar(
                source = selected,
                collection = collection,
                followed = selected.author.cleanStreamArtistName().normalizedSearchText() in followedArtistKeys,
                active = selected.id == activePreviewSourceId,
                onPlayFull = { onPlayFull(selected) },
                onToggleFollowArtist = { onToggleFollowArtist(selected.author) },
            )
        }
    }
}

@Composable
private fun SearchDiscoveryPreviewPage(
    source: YouTubeSource,
    collection: StreamCollection,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = Color(collection.accentColor)
    Box(
        modifier
            .clip(RoundedCornerShape(34.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.34f), Color.Black.copy(alpha = 0.86f))))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.20f else 0.08f), RoundedCornerShape(34.dp))
            .padding(16.dp),
    ) {
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.fillMaxSize(), DeckIcon.StreamSource)
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f)))))
        PulseIcon(if (active) DeckIcon.Wave else DeckIcon.Play, Color.White.copy(alpha = 0.88f), Modifier.size(26.dp).align(Alignment.TopStart).padding(start = 14.dp, top = 14.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(source.title, color = Color.White, fontSize = 27.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(source.author, color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun SearchDiscoveryPreviewBottomBar(
    source: YouTubeSource,
    collection: StreamCollection,
    followed: Boolean,
    active: Boolean,
    onPlayFull: () -> Unit,
    onToggleFollowArtist: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.075f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
                .clickable(onClick = onPlayFull)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(54.dp), DeckIcon.Play)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.title, color = Color.White, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(source.author, color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (active) "Selected in player" else "Tap Play Full Song when this one feels right", color = Color.White.copy(alpha = 0.48f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(10.dp))
            SourceActionButton(if (followed) "Following" else "Follow", if (followed) DeckIcon.StreamLike else DeckIcon.Heart, Modifier.width(104.dp), onToggleFollowArtist, active = followed)
        }
        SourceActionButton("Play Full Song", DeckIcon.Play, Modifier.fillMaxWidth().padding(top = 10.dp), onPlayFull)
    }
}

@Composable
private fun YouTubeSearchField(query: String, onQuery: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(StreamAccentRed.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.30f), Color.White.copy(alpha = 0.045f))))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(DeckIcon.Discover, Color.White.copy(alpha = 0.78f), Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Search songs, artists, or mixes", color = Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                },
            )
            if (query.isNotBlank()) {
                IconTap(DeckIcon.Close, { onQuery("") }, 32.dp)
            }
        }
        Text(
            if (query.isBlank()) "PulseDeck audio search" else "Resolving playable audio results",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp, top = 7.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun YouTubeThumbnailBox(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    fallbackIcon: DeckIcon = DeckIcon.Stream,
    artworkUseCase: ArtworkUseCase = ArtworkUseCase.YouTubeSearchThumbnail,
) {
    val allowNetworkThumbnail = LocalYouTubeThumbnailNetworkAllowed.current
    val normalizedThumbnailUrl = remember(thumbnailUrl, allowNetworkThumbnail) {
        if (!allowNetworkThumbnail && thumbnailUrl?.let(::isNetworkThumbnailReference) == true) {
            null
        } else {
            normalizeYouTubeThumbnailUrl(thumbnailUrl) ?: thumbnailUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
        }
    }
    val thumbnailUri = remember(normalizedThumbnailUrl) {
        normalizedThumbnailUrl?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
    }
    val thumbnail = rememberAlbumArtBitmap(thumbnailUri, null, useCase = artworkUseCase)
    Box(
        modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
        } else {
            PulseIcon(fallbackIcon, Color.White, Modifier.size(31.dp))
        }
    }
}

private fun isNetworkThumbnailReference(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("//") ||
        trimmed.startsWith("/")
}

@Composable
private fun QueueScreen(tracks: List<Track>, currentIndex: Int, onTrack: (Track) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
        Text("Queue", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
        Text("${tracks.size} tracks", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 22.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 184.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(tracks, key = { _, track -> track.stableKey() }) { index, track ->
                TrackRow(track, active = index == currentIndex) { onTrack(track) }
            }
        }
    }
}

