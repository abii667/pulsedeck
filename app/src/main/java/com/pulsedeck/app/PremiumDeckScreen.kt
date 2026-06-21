package com.pulsedeck.app

import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.premiumdeck.personalization.UserPreferenceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
internal fun YouTubeSearchStatusCard(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.StreamSource, Color.White.copy(alpha = 0.62f), Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun YouTubeSuggestionChips(suggestions: List<YouTubeSearchSuggestion>, onQuery: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(3).forEach { suggestion ->
                    YouTubeSuggestionChip(suggestion.text, Modifier.weight(1f)) { onQuery(suggestion.text) }
                }
            }
        }
        if (suggestions.size > 3) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.drop(3).take(3).forEach { suggestion ->
                        YouTubeSuggestionChip(suggestion.text, Modifier.weight(1f)) { onQuery(suggestion.text) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun YouTubeSuggestionChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun premiumDeckSearchReadinessLabel(
    source: YouTubeSource,
    prepareState: PremiumDeckStreamPrepareState?,
    streamPolicy: StreamingDataPolicy,
): String? =
    when {
        prepareState == PremiumDeckStreamPrepareState.Preparing -> "Preparing audio"
        prepareState == PremiumDeckStreamPrepareState.Refreshing -> "Refreshing stream"
        prepareState == PremiumDeckStreamPrepareState.Ready -> "Audio ready"
        prepareState == PremiumDeckStreamPrepareState.BackupReady -> "Backup ready"
        prepareState == PremiumDeckStreamPrepareState.Failed || source.status == YouTubeSourceStatus.ResolverNeeded -> "Tap to resolve audio"
        source.isOfflineSaved() -> "Saved offline"
        source.freshCachedStreamUrl(policy = streamPolicy) != null -> "Cached audio ready"
        else -> null
    }

private fun premiumDeckSearchReadinessIcon(
    source: YouTubeSource,
    prepareState: PremiumDeckStreamPrepareState?,
    resolving: Boolean,
    streamPolicy: StreamingDataPolicy,
): DeckIcon =
    when {
        resolving || prepareState == PremiumDeckStreamPrepareState.Preparing || prepareState == PremiumDeckStreamPrepareState.Refreshing -> DeckIcon.Timer
        prepareState == PremiumDeckStreamPrepareState.Ready || source.freshCachedStreamUrl(policy = streamPolicy) != null -> DeckIcon.Check
        prepareState == PremiumDeckStreamPrepareState.BackupReady -> DeckIcon.Signal
        prepareState == PremiumDeckStreamPrepareState.Failed || source.status == YouTubeSourceStatus.ResolverNeeded -> DeckIcon.Info
        source.isOfflineSaved() -> DeckIcon.StreamOffline
        else -> DeckIcon.Headphones
    }

@Composable
internal fun YouTubeSearchResultRow(
    result: YouTubeSearchResult,
    resolving: Boolean,
    isPlaying: Boolean = false,
    prepareState: PremiumDeckStreamPrepareState?,
    streamPolicy: StreamingDataPolicy,
    onPlay: () -> Unit,
) {
    val source = result.source
    val interactionSource = remember(source.id) { MutableInteractionSource() }
    val readinessLabel = remember(source, prepareState, streamPolicy) { premiumDeckSearchReadinessLabel(source, prepareState, streamPolicy) }
    val readinessIcon = premiumDeckSearchReadinessIcon(source, prepareState, resolving, streamPolicy)
    val scoreTint = when {
        result.score >= 180 -> Color(0xFF5CE4D0)
        result.score >= 100 -> Color(0xFFFFD166)
        else -> Color.White.copy(alpha = 0.72f)
    }
    val meta = remember(result) {
        buildList {
            if (result.durationMillis > 0L) add(formatDuration(result.durationMillis))
            if (result.uploadedDate.isNotBlank()) add(result.uploadedDate)
            if (result.views > 0L) add(formatCompactCount(result.views) + " views")
        }.joinToString("  |  ")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isPlaying) StreamAccentRed.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f))
            .border(
                1.dp,
                when {
                    isPlaying -> StreamAccentRed.copy(alpha = 0.34f)
                    resolving || prepareState != null -> Color.White.copy(alpha = 0.12f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
                RoundedCornerShape(18.dp),
            )
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(52.dp), fallbackIcon = DeckIcon.StreamSource)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(readinessIcon, Color.White, Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        if (isPlaying) {
            PremiumDeckNowPlayingIndicator()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(source.title, color = Color.White, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.author, color = Muted, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (resolving) "Resolving audio in PulseDeck" else readinessLabel ?: meta.ifBlank { result.matchReason ?: "Playable result" }, color = Color.White.copy(alpha = 0.62f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!result.matchReason.isNullOrBlank()) {
                Text(result.matchReason, color = scoreTint.copy(alpha = 0.72f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        IconTap(if (resolving) DeckIcon.Timer else DeckIcon.Play, onPlay, 36.dp)
    }
}

@Composable
internal fun YouTubeSourcesScreen(
    sources: List<YouTubeSource>,
    playlists: List<YouTubePlaylist>,
    albumDrafts: List<AlbumDownloadDraft>,
    albumProjectTasks: Map<String, AlbumProjectTaskState>,
    playHistory: List<StreamPlayHistoryItem>,
    discoveryResults: List<YouTubeSearchResult>,
    personalizationMixes: List<StreamCollection>,
    followedArtists: List<FollowedStreamArtist>,
    newReleaseResults: List<YouTubeSearchResult>,
    releaseNotifications: List<StreamReleaseNotification>,
    podcastSnapshot: PremiumDeckPodcastSnapshot,
    recommendedAlbumsSnapshot: RecommendedAlbumsSnapshot?,
    discoveryLoading: Boolean,
    newReleaseLoading: Boolean,
    podcastDiscoveryLoading: Boolean,
    podcastLoadingShowId: String?,
    podcastAddLoading: Boolean,
    mixSessionSeed: Long,
    personalizationProfile: UserPreferenceProfile,
    searchQuery: String,
    searchResults: List<YouTubeSearchResult>,
    searchSuggestions: List<YouTubeSearchSuggestion>,
    discoveryGenres: List<StreamDiscoveryGenre>,
    discoveryGenreCollections: List<StreamCollection>,
    discoveryPreviewCollection: StreamCollection?,
    discoveryLoadingGenreId: String?,
    premiumChartProvider: PremiumDeckChartProvider,
    premiumChartGenre: PremiumDeckChartGenre,
    premiumChartRequestedLimit: Int,
    premiumChartEntries: List<PremiumDeckChartEntry>,
    premiumChartMatches: List<PremiumDeckChartMatch>,
    premiumChartCollection: StreamCollection?,
    premiumChartLoading: Boolean,
    premiumChartMatching: Boolean,
    premiumChartError: String?,
    activePreviewSourceId: String?,
    searchLoading: Boolean,
    searchError: String?,
    offlineDeckActive: Boolean,
    offlineDeckRecommendation: String?,
    selectedTab: YouTubeLibraryTab,
    activeShelf: YouTubeSmartShelf?,
    resolvingSourceId: String?,
    prepareStates: Map<String, PremiumDeckStreamPrepareState>,
    capabilities: ResolverCapabilities,
    streamPolicy: StreamingDataPolicy,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onAlbumBuilder: () -> Unit,
    onAlbumProject: (AlbumDownloadDraft) -> Unit,
    onVerifiedAlbum: (VerifiedAlbumCandidate) -> Unit,
    onNewPlaylist: () -> Unit,
    onRules: () -> Unit,
    onRefreshDiscovery: () -> Unit,
    onRefreshPodcasts: () -> Unit,
    onAddPodcast: (String) -> Unit,
    onPodcastShow: (String) -> Unit,
    onTab: (YouTubeLibraryTab) -> Unit,
    onSearchQuery: (String) -> Unit,
    onSearchResult: (YouTubeSearchResult) -> Unit,
    onDiscoveryGenrePreview: (StreamDiscoveryGenre) -> Unit,
    onDiscoveryGenre: (StreamDiscoveryGenre) -> Unit,
    onPremiumChartProvider: (PremiumDeckChartProvider) -> Unit,
    onPremiumChartGenre: (PremiumDeckChartGenre) -> Unit,
    onPremiumChartRefresh: () -> Unit,
    onPremiumChartShowMore: () -> Unit,
    onPremiumChartMatchNext: () -> Unit,
    onDismissDiscoveryPreview: () -> Unit,
    onDiscoveryPreview: (StreamCollection, YouTubeSource) -> Unit,
    onDiscoveryPlayFull: (StreamCollection, YouTubeSource) -> Unit,
    onCollectionOpen: (StreamCollection) -> Unit,
    onCollectionPlay: (StreamCollection) -> Unit,
    onCollectionSave: (StreamCollection) -> Unit,
    onToggleFollowArtist: (String) -> Unit,
    onToggleFollowArtistOption: (StreamArtistFollowOption) -> Unit,
    onRefreshNewReleases: () -> Unit,
    onReleaseNotification: (StreamReleaseNotification, StreamCollection) -> Unit,
    onMarkAllReleaseNotificationsRead: () -> Unit,
    onShelf: (YouTubeSmartShelf?) -> Unit,
    onPlayInApp: (YouTubeSource, List<YouTubeSource>) -> Unit,
    onAccept: (YouTubeSource) -> Unit,
    onKeepOffline: (YouTubeSource) -> Unit,
    onReaction: (YouTubeSource, YouTubeReaction) -> Unit,
    onBookmark: (YouTubeSource) -> Unit,
    onToggleSkip: (YouTubeSource) -> Unit,
    onToggleTrim: (YouTubeSource) -> Unit,
    onSourceInfo: (YouTubeSource, List<String>) -> Unit,
    onRemove: (YouTubeSource) -> Unit,
    onPlaylist: (YouTubePlaylist) -> Unit,
    onPlaylistActions: (YouTubePlaylist) -> Unit,
    onPlaylistPlay: (YouTubePlaylist) -> Unit,
    onPlaylistShuffle: (YouTubePlaylist) -> Unit,
    onOfflineDeckChange: (Boolean) -> Unit,
) {
    val displaySources = remember(sources, offlineDeckActive) {
        if (offlineDeckActive) sources.filter { it.isOfflineSaved() } else sources
    }
    val acceptedSources = remember(displaySources) { displaySources.filter { it.reviewState == YouTubeReviewState.Accepted } }
    val inboxSources = remember(displaySources, offlineDeckActive) {
        if (offlineDeckActive) emptyList() else displaySources.filter { it.reviewState == YouTubeReviewState.Inbox }
    }
    val downloadSources = remember(displaySources) { displaySources.filter { it.isOfflineSaved() } }
    val recentPodcastEpisodes = remember(podcastSnapshot, displaySources, offlineDeckActive) {
        val discovered = if (offlineDeckActive) emptyList() else podcastSnapshot.recentEpisodes(limit = 20)
        if (discovered.isNotEmpty()) {
            discovered
        } else {
            displaySources
                .filter { it.isPodcast && (!offlineDeckActive || it.isOfflineSaved()) }
                .sortedByDescending { max(it.lastPlayedMillis, it.addedMillis) }
                .take(20)
                .map { source -> YouTubeSearchResult(source = source, durationMillis = source.durationMillis, cachedMillis = source.addedMillis) }
        }
    }
    val podcastSources = remember(displaySources, recentPodcastEpisodes) {
        (displaySources.filter { it.isPodcast } + recentPodcastEpisodes.map { it.source.copy(isPodcast = true, reviewState = YouTubeReviewState.Accepted) })
            .distinctBy { it.streamDistinctKey() }
    }
    val likedSources = remember(displaySources) { displaySources.filter { it.reaction == YouTubeReaction.Liked } }
    val needsMetadataSources = remember(displaySources, offlineDeckActive) {
        if (offlineDeckActive) emptyList() else displaySources.filter { it.needsYouTubeMetadataReview() }
    }
    val activeAlbumProjectTask = remember(albumDrafts, albumProjectTasks, displaySources, offlineDeckActive) {
        if (offlineDeckActive) null else findActiveAlbumProjectTask(albumDrafts, albumProjectTasks, displaySources)
    }
    val followedArtistKeys = remember(followedArtists) { followedArtists.map { it.key }.toSet() }
    val newReleaseCollection = remember(followedArtists, newReleaseResults, displaySources, offlineDeckActive) {
        if (offlineDeckActive) null else buildFollowedArtistNewReleaseCollection(followedArtists, newReleaseResults, displaySources)
    }
    val discoveryStackCollections = remember(discoveryResults, newReleaseCollection, offlineDeckActive) {
        if (offlineDeckActive) emptyList() else buildPremiumDeckDiscoveryCollections(discoveryResults, newReleaseCollection)
    }
    val sourceBrowseSignature = remember(displaySources) {
        streamSourceListSignature(displaySources)
    }
    val discoveryBrowseSignature = remember(discoveryResults) {
        streamSearchResultListSignature(discoveryResults)
    }
    val searchBrowseSignature = remember(searchResults) {
        streamSearchResultListSignature(searchResults)
    }
    val artistFollowOptions = remember(sourceBrowseSignature, discoveryBrowseSignature, searchBrowseSignature, playHistory, followedArtists, offlineDeckActive) {
        buildStreamArtistFollowOptions(
            sources = displaySources,
            discoveryResults = if (offlineDeckActive) searchResults else discoveryResults + searchResults,
            playHistory = playHistory,
            followedArtists = followedArtists,
        )
    }
    val shelfSources = remember(displaySources, activeShelf, podcastSources) {
        when (activeShelf) {
            YouTubeSmartShelf.Inbox -> inboxSources
            YouTubeSmartShelf.Recent -> displaySources.sortedByDescending { max(it.lastPlayedMillis, it.addedMillis) }.take(24)
            YouTubeSmartShelf.Liked -> likedSources
            YouTubeSmartShelf.MostPlayed -> displaySources.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(12)
            YouTubeSmartShelf.SavedOffline -> downloadSources
            YouTubeSmartShelf.Podcasts -> podcastSources
            YouTubeSmartShelf.NeedsMetadata -> needsMetadataSources
            null -> emptyList()
        }
    }
    val visibleSources = when {
        activeShelf != null && selectedTab != YouTubeLibraryTab.Playlists -> shelfSources
        selectedTab == YouTubeLibraryTab.Downloads -> downloadSources
        selectedTab == YouTubeLibraryTab.Podcasts -> podcastSources
        else -> acceptedSources
    }
    var sourceSort by rememberSaveable { mutableStateOf(StreamSourceSort.Recent) }
    var openHeaderMenu by remember { mutableStateOf<StreamHeaderMenu?>(null) }
    var releaseNotificationsOpen by rememberSaveable { mutableStateOf(false) }
    var artistFollowOpen by rememberSaveable { mutableStateOf(false) }
    var expandedPodcastShowId by rememberSaveable { mutableStateOf<String?>(null) }
    var podcastAddOpen by rememberSaveable { mutableStateOf(false) }
    var podcastAddQuery by rememberSaveable { mutableStateOf("") }
    var premiumSearchDiscoveryOpen by rememberSaveable { mutableStateOf(false) }
    var premiumChartDetailOpen by rememberSaveable { mutableStateOf(false) }
    var premiumChartMoreConfirmOpen by rememberSaveable { mutableStateOf(false) }
    val safeSearchResults = remember(searchResults) { searchResults.distinctBy { it.source.streamDistinctKey() } }
    val visiblePlaylists = remember(playlists, displaySources, offlineDeckActive) {
        val offlineIds = displaySources.flatMap { it.streamIdentityKeys() }.toSet()
        playlists
            .distinctBy { it.id }
            .filter { playlist -> !offlineDeckActive || playlist.sourceIds.any { it in offlineIds } }
    }
    val sortedVisibleSources = remember(visibleSources, sourceSort) {
        sortStreamSources(visibleSources.distinctBy { it.streamDistinctKey() }, sourceSort)
    }
    val visibleQueueSources = remember(sortedVisibleSources) { sortedVisibleSources.distinctBy { it.url.ifBlank { it.id } } }
    val visibleQueueIds = remember(visibleQueueSources) { visibleQueueSources.map { it.id }.distinct() }
    val releaseNotificationUnreadCount = remember(releaseNotifications) { releaseNotifications.count { !it.read } }
    val externalRecommendations = PulseOnlineRuntime.settings.externalRecommendations && !offlineDeckActive
    val quickBrowseModel = remember(sortedVisibleSources) {
        StreamBrowseModel(
            recentSources = sortedVisibleSources.take(12),
            filteredSources = sortedVisibleSources,
        )
    }
    var homeBrowseModel by remember(quickBrowseModel) { mutableStateOf(quickBrowseModel) }
    LaunchedEffect(
        sourceBrowseSignature,
        playlists,
        playHistory,
        discoveryBrowseSignature,
        personalizationMixes,
        followedArtists,
        personalizationProfile,
        mixSessionSeed,
        externalRecommendations,
        sortedVisibleSources,
        offlineDeckActive,
    ) {
        homeBrowseModel = quickBrowseModel
        homeBrowseModel = withContext(Dispatchers.Default) {
            val base = buildStreamBrowseModel(
                sources = displaySources,
                playlists = visiblePlaylists,
                playHistory = playHistory,
                discoveryResults = if (externalRecommendations) discoveryResults else emptyList(),
                visibleSources = emptyList(),
                followedArtists = followedArtists,
                personalizationProfile = personalizationProfile,
                mixSessionSeed = mixSessionSeed,
            )
            val localMixes = if (offlineDeckActive) emptyList() else personalizationMixes
            base.copy(mixes = (localMixes + base.mixes).distinctBy { it.title }.take(8))
        }
    }
    val focusedPodcastList = selectedTab == YouTubeLibraryTab.Podcasts || activeShelf == YouTubeSmartShelf.Podcasts
    val podcastDirectoryShows = remember(podcastSnapshot, offlineDeckActive) {
        if (offlineDeckActive) emptyList() else podcastSnapshot.directoryShows()
    }
    val browseModel = remember(homeBrowseModel, sortedVisibleSources) {
        homeBrowseModel.copy(
            filteredSources = sortedVisibleSources
                .filter { it.reaction != YouTubeReaction.Disliked || it.reviewState == YouTubeReviewState.Accepted }
                .distinctBy { it.streamDistinctKey() },
        )
    }
    val generatedAlbumCollections = remember(browseModel.albums, albumDrafts) {
        filterAlbumRecommendationsAgainstProjects(browseModel.albums, albumDrafts)
    }
    val selectAll = {
        onTab(YouTubeLibraryTab.Sources)
        onShelf(null)
    }
    val selectRecent = {
        onTab(YouTubeLibraryTab.Sources)
        onShelf(YouTubeSmartShelf.Recent)
    }
    val selectLiked = {
        onTab(YouTubeLibraryTab.Sources)
        onShelf(YouTubeSmartShelf.Liked)
    }
    val selectSaved = {
        onTab(YouTubeLibraryTab.Downloads)
        onShelf(YouTubeSmartShelf.SavedOffline)
    }
    val selectPlaylists = {
        onTab(YouTubeLibraryTab.Playlists)
        onShelf(null)
    }
    val selectPodcasts = {
        onTab(YouTubeLibraryTab.Podcasts)
        onShelf(YouTubeSmartShelf.Podcasts)
    }
    val selectInbox = {
        onTab(YouTubeLibraryTab.Sources)
        onShelf(YouTubeSmartShelf.Inbox)
    }
    val browseListState = rememberLazyListState()
    val browseScrollScope = rememberCoroutineScope()
    val podcastDirectoryHeaderIndex = remember(activeAlbumProjectTask) {
        8 + if (activeAlbumProjectTask != null) 1 else 0
    }
    val openPodcastDirectory: () -> Unit = {
        selectPodcasts()
        browseScrollScope.launch {
            delay(80L)
            browseListState.animateScrollToItem(podcastDirectoryHeaderIndex)
        }
    }
    BackHandler(enabled = premiumSearchDiscoveryOpen && searchQuery.isBlank() && discoveryPreviewCollection == null) {
        if (premiumChartDetailOpen) {
            premiumChartDetailOpen = false
        } else {
            premiumSearchDiscoveryOpen = false
        }
    }
    LaunchedEffect(premiumSearchDiscoveryOpen, premiumChartDetailOpen, searchQuery, premiumChartProvider, premiumChartGenre, offlineDeckActive) {
        if (
            !offlineDeckActive &&
            premiumSearchDiscoveryOpen &&
            premiumChartDetailOpen &&
            searchQuery.isBlank() &&
            premiumChartEntries.isEmpty() &&
            !premiumChartLoading &&
            premiumDeckChartAvailabilityMessage(premiumChartProvider, premiumChartGenre) == null
        ) {
            onPremiumChartRefresh()
        }
    }
    val visibleShelfCount = remember(
        discoveryStackCollections,
        activeAlbumProjectTask,
        albumDrafts,
        recentPodcastEpisodes,
        recommendedAlbumsSnapshot,
        generatedAlbumCollections,
        browseModel.recentSources,
        browseModel.mixes,
        browseModel.artists,
        visiblePlaylists,
        browseModel.filteredSources,
    ) {
        listOf(
            discoveryStackCollections.isNotEmpty(),
            activeAlbumProjectTask != null,
            albumDrafts.isNotEmpty(),
            recentPodcastEpisodes.isNotEmpty(),
            recommendedAlbumsSnapshot?.albums?.isNotEmpty() == true || generatedAlbumCollections.isNotEmpty(),
            browseModel.recentSources.isNotEmpty(),
            browseModel.mixes.isNotEmpty(),
            browseModel.artists.isNotEmpty(),
            visiblePlaylists.isNotEmpty(),
            browseModel.filteredSources.isNotEmpty(),
        ).count { it }
    }
    val routeCompositionStats = remember(
        displaySources.size,
        acceptedSources.size,
        browseModel.filteredSources.size,
        visiblePlaylists.size,
        albumDrafts.size,
        discoveryResults.size,
        personalizationMixes.size,
        followedArtists.size,
        releaseNotifications.size,
        recentPodcastEpisodes.size,
        safeSearchResults.size,
        prepareStates.size,
        visibleShelfCount,
    ) {
        PremiumDeckRouteCompositionStats(
            sourceCount = displaySources.size,
            acceptedSourceCount = acceptedSources.size,
            visibleSourceCount = browseModel.filteredSources.size,
            playlistCount = visiblePlaylists.size,
            albumDraftCount = albumDrafts.size,
            discoveryResultCount = discoveryResults.size,
            personalizationMixCount = personalizationMixes.size,
            followedArtistCount = followedArtists.size,
            releaseNotificationCount = releaseNotifications.size,
            podcastEpisodeCount = recentPodcastEpisodes.size,
            searchResultCount = safeSearchResults.size,
            prepareStateCount = prepareStates.size,
            visibleShelfCount = visibleShelfCount,
        )
    }
    val latestRouteCompositionStats by rememberUpdatedState(routeCompositionStats)
    LaunchedEffect(routeCompositionStats) {
        routeCompositionStats.log("shelves_loaded")
    }
    DisposableEffect(Unit) {
        latestRouteCompositionStats.log("first_composition")
        onDispose {
            latestRouteCompositionStats.log("composition_dispose")
        }
    }

    CompositionLocalProvider(LocalYouTubeThumbnailNetworkAllowed provides !offlineDeckActive) {
        Box(
            Modifier
                .fillMaxSize()
                .background(StreamBase)
                .statusBarsPadding(),
        ) {
        Column(Modifier.fillMaxSize().padding(horizontal = StreamHorizontalPadding)) {
            StreamLibraryHeader(
                onBack = onBack,
                unreadReleaseCount = releaseNotificationUnreadCount,
                hasReleaseNotifications = releaseNotifications.isNotEmpty(),
                onNotifications = { releaseNotificationsOpen = true },
                onArtists = { artistFollowOpen = true },
                onMore = { openHeaderMenu = StreamHeaderMenu.More },
            )
            Spacer(Modifier.height(7.dp))
            StreamSearchBar(
                query = searchQuery,
                onQuery = {
                    premiumSearchDiscoveryOpen = true
                    onSearchQuery(it)
                },
                onFocus = { premiumSearchDiscoveryOpen = true },
            )
            if (offlineDeckActive) {
                Spacer(Modifier.height(8.dp))
                PremiumDeckOfflineDeckCard(
                    title = "Offline Deck",
                    message = "PremiumDeck is showing saved music only. Online search, discovery, charts, podcasts, stream resolving, and downloads are paused.",
                    active = true,
                    onToggle = { onOfflineDeckChange(false) },
                )
            } else if (offlineDeckRecommendation != null) {
                Spacer(Modifier.height(8.dp))
                PremiumDeckOfflineDeckCard(
                    title = "Offline Deck Available",
                    message = offlineDeckRecommendation,
                    active = false,
                    onToggle = { onOfflineDeckChange(true) },
                )
            }
            Spacer(Modifier.height(10.dp))
            val searchingYouTube = searchQuery.trim().isNotBlank()
            if (searchingYouTube) {
                LazyColumn(contentPadding = PaddingValues(bottom = 184.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (searchSuggestions.isNotEmpty()) {
                        item { YouTubeSuggestionChips(searchSuggestions, onSearchQuery) }
                    }
                    when {
                        searchLoading -> item {
                            YouTubeSearchStatusCard(
                                when {
                                    offlineDeckActive -> "Searching saved PremiumDeck music..."
                                    safeSearchResults.isNotEmpty() -> "Refreshing cached results..."
                                    else -> "Searching $PREMIUMDECK_SOURCE_NAME..."
                                },
                            )
                        }
                        searchQuery.trim().length < 2 -> item { YouTubeSearchStatusCard("Type at least two characters") }
                        searchError != null -> item { YouTubeSearchStatusCard(searchError) }
                    }
                    items(safeSearchResults, key = { it.source.streamDistinctKey() }) { result ->
                        YouTubeSearchResultRow(
                            result = result,
                            resolving = resolvingSourceId == result.source.id,
                            isPlaying = activePreviewSourceId == result.source.id,
                            prepareState = prepareStates[result.source.id],
                            streamPolicy = streamPolicy,
                            onPlay = { onSearchResult(result) },
                        )
                    }
                }
            } else if (premiumSearchDiscoveryOpen && !offlineDeckActive) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 184.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "premiumChartDiscovery") {
                        if (premiumChartDetailOpen) {
                            PremiumDeckChartDetailPage(
                                provider = premiumChartProvider,
                                genre = premiumChartGenre,
                                requestedLimit = premiumChartRequestedLimit,
                                entries = premiumChartEntries,
                                matches = premiumChartMatches,
                                collection = premiumChartCollection,
                                loading = premiumChartLoading,
                                matching = premiumChartMatching,
                                error = premiumChartError,
                                activeSourceId = activePreviewSourceId,
                                onBack = { premiumChartDetailOpen = false },
                                onRefresh = onPremiumChartRefresh,
                                onShowMore = { premiumChartMoreConfirmOpen = true },
                                onMatchNext = onPremiumChartMatchNext,
                                onOpenCollection = { collection -> onCollectionOpen(collection) },
                                onPlayCollection = { collection -> onCollectionPlay(collection) },
                            )
                        } else {
                            PremiumDeckChartDiscoverySurface(
                                provider = premiumChartProvider,
                                genre = premiumChartGenre,
                                onOpenOption = { option ->
                                    onPremiumChartProvider(option.provider)
                                    onPremiumChartGenre(option.genre)
                                    premiumChartDetailOpen = true
                                },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(state = browseListState, contentPadding = PaddingValues(bottom = 184.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (!offlineDeckActive) {
                        item(key = "discoveryStackShelf") {
                            PremiumDeckDiscoveryStackShelf(
                                collections = discoveryStackCollections,
                                loading = discoveryLoading,
                                onRefresh = onRefreshDiscovery,
                                onOpen = onCollectionOpen,
                                onPlay = onCollectionPlay,
                            )
                        }
                    }
                    activeAlbumProjectTask?.let { taskView ->
                        item(key = "activeAlbumProjectTask") {
                            PremiumDeckAlbumTaskMiniBar(taskView)
                        }
                    }
                    item(key = "albumProjectShelf") {
                        PremiumDeckAlbumProjectShelf(
                            drafts = albumDrafts,
                            sources = displaySources,
                            tasks = albumProjectTasks,
                            onOpen = onAlbumProject,
                            onNew = onAlbumBuilder,
                        )
                    }
                    if (!offlineDeckActive) {
                        item(key = "podcastShelf") {
                            PremiumDeckPodcastShelf(
                                episodes = recentPodcastEpisodes.take(4),
                                loading = podcastDiscoveryLoading,
                                onDiscover = onRefreshPodcasts,
                                onOpenPodcasts = openPodcastDirectory,
                                onSource = { source -> onPlayInApp(source, recentPodcastEpisodes.map { it.source }) },
                            )
                        }
                    }
                    item(key = "recommendedAlbumsShelf") {
                        VerifiedAlbumRecommendationShelf(
                            snapshot = recommendedAlbumsSnapshot,
                            generatedCollections = generatedAlbumCollections,
                            onAlbum = onVerifiedAlbum,
                            onGeneratedOpen = onCollectionOpen,
                            onGeneratedPlay = onCollectionPlay,
                            onGeneratedSave = onCollectionSave,
                        )
                    }
                    item(key = "jumpBackInShelf") {
                        StreamSourceShelf(
                            title = "Jump back in",
                            subtitle = "Recent listens are ready to resume",
                            sources = browseModel.recentSources,
                            emptyTitle = "Play a stream to build your recent shelf",
                            showEmptyIcon = false,
                            onSource = { source -> onPlayInApp(source, browseModel.recentSources) },
                        )
                    }
                    item(key = "topMixesShelf") {
                        StreamCollectionShelf(
                            title = "Your top mixes",
                            subtitle = "Generated each visit from history, likes, saves, and discovery",
                            collections = browseModel.mixes,
                            emptyTitle = "Your mixes will appear after a few listens",
                            onOpen = onCollectionOpen,
                            onPlay = onCollectionPlay,
                            onSave = onCollectionSave,
                        )
                    }
                    item(key = "artistShelf") {
                        StreamArtistShelf(
                            artists = browseModel.artists,
                            followedArtistKeys = followedArtistKeys,
                            onOpen = onCollectionOpen,
                            onPlay = onCollectionPlay,
                            onToggleFollowArtist = onToggleFollowArtist,
                        )
                    }
                    item(key = "playlistShelf") {
                        StreamPlaylistShelf(
                            playlists = visiblePlaylists,
                            sources = displaySources,
                            onPlaylist = onPlaylist,
                            onPlaylistActions = onPlaylistActions,
                            onPlay = onPlaylistPlay,
                            onShuffle = onPlaylistShuffle,
                            onNewPlaylist = onNewPlaylist,
                        )
                    }
                    item(key = "sourcesSectionHeader") {
                        StreamSourcesSectionHeader(
                            title = when {
                                selectedTab == YouTubeLibraryTab.Playlists -> "Your playlists"
                                focusedPodcastList -> "Podcasts"
                                else -> "Your sources"
                            },
                            subtitle = when {
                                selectedTab == YouTubeLibraryTab.Playlists -> "Manual playlists and saved virtual mixes"
                                focusedPodcastList -> "Top shows plus your saved podcast channels"
                                offlineDeckActive -> "Offline Deck - saved PremiumDeck only"
                                else -> "${activeShelf?.label ?: selectedTab.label} - ${sourceSort.label}"
                            },
                        )
                    }
                    if (focusedPodcastList) {
                        item(key = "podcast-add") {
                            PremiumDeckPodcastAddPanel(
                                expanded = podcastAddOpen,
                                query = podcastAddQuery,
                                loading = podcastAddLoading,
                                onExpand = { podcastAddOpen = true },
                                onQuery = { podcastAddQuery = it.take(80) },
                                onCancel = { podcastAddOpen = false },
                                onSubmit = {
                                    val cleanQuery = podcastAddQuery.trim()
                                    if (cleanQuery.length >= 2) onAddPodcast(cleanQuery)
                                },
                            )
                        }
                        podcastDirectoryShows.forEach { show ->
                            item(key = "podcast-show-${show.id}") {
                                PremiumDeckPodcastShowRow(
                                    show = show,
                                    expanded = expandedPodcastShowId == show.id,
                                    loading = podcastLoadingShowId == show.id,
                                    episodeCount = podcastSnapshot.episodesForShow(show.id).size,
                                    onClick = {
                                        expandedPodcastShowId = if (expandedPodcastShowId == show.id) null else show.id
                                        if (expandedPodcastShowId == show.id) onPodcastShow(show.id)
                                    },
                                )
                            }
                            if (expandedPodcastShowId == show.id) {
                                val episodes = podcastSnapshot.episodesForShow(show.id)
                                if (podcastLoadingShowId == show.id && episodes.isEmpty()) {
                                    item(key = "podcast-show-${show.id}-loading") {
                                        YouTubeSearchStatusCard("Loading latest ${show.title} episodes...")
                                    }
                                } else if (episodes.isEmpty()) {
                                    item(key = "podcast-show-${show.id}-empty") {
                                        YouTubeEmptyState(
                                            onAdd = { onPodcastShow(show.id) },
                                            title = "No recent episodes yet",
                                            subtitle = "PremiumDeck will search this show's YouTube channel and register playable episodes.",
                                            buttonLabel = "Try Again",
                                        )
                                    }
                                } else {
                                    items(episodes, key = { "${show.id}-${it.videoId.ifBlank { it.source.streamDistinctKey() }}" }) { episode ->
                                        PremiumDeckPodcastEpisodeRow(
                                            show = show,
                                            episode = episode,
                                            resolving = resolvingSourceId == episode.source.id,
                                            onPlay = { onPlayInApp(episode.source, episodes.map { it.source }) },
                                            onInfo = { onSourceInfo(episode.source, episodes.map { it.source.id }) },
                                        )
                                    }
                                }
                            }
                        }
                    } else if (selectedTab == YouTubeLibraryTab.Playlists) {
                        item(key = "playlistCreateCard") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                PremiumDeckPlaylistCreateCard(onNewPlaylist)
                            }
                        }
                        if (visiblePlaylists.isEmpty()) {
                            item(key = "emptyPlaylists") {
                                YouTubeEmptyState(
                                    onNewPlaylist,
                                    "Create a stream playlist",
                                    "Save virtual mixes or organize sources into playlists.",
                                    "New Playlist",
                                    showIcon = false,
                                )
                            }
                        } else {
                            items(visiblePlaylists, key = { it.id }) { playlist ->
                                YouTubePlaylistRow(
                                    playlist = playlist,
                                    sources = displaySources,
                                    onOpen = { onPlaylist(playlist) },
                                    onActions = { onPlaylistActions(playlist) },
                                    onPlay = { onPlaylistPlay(playlist) },
                                    onShuffle = { onPlaylistShuffle(playlist) },
                                )
                            }
                        }
                    } else if (browseModel.filteredSources.isEmpty()) {
                        item(key = "emptySources") {
                            if (offlineDeckActive) {
                                YouTubeEmptyState(
                                    { onOfflineDeckChange(false) },
                                    "No saved PremiumDeck music yet",
                                    "Turn off Offline Deck to search or save PremiumDeck music.",
                                    "Turn Off",
                                    iconRes = R.drawable.premium_empty_no_sources,
                                )
                            } else {
                                val title = activeShelf?.label ?: selectedTab.label
                                YouTubeEmptyState(
                                    onAdd,
                                    "No $title yet",
                                    "Use search, Quick Add, or recommendations to fill this area.",
                                    iconRes = R.drawable.premium_empty_no_sources,
                                )
                            }
                        }
                    } else {
                        items(browseModel.filteredSources.take(if (focusedPodcastList) 15 else 36), key = { it.streamDistinctKey() }) { source ->
                            CompactSourceRow(
                                source = source,
                                resolving = resolvingSourceId == source.id,
                                isPlaying = activePreviewSourceId == source.id,
                                onOpen = { onPlayInApp(source, visibleQueueSources) },
                                onLike = { onReaction(source, YouTubeReaction.Liked) },
                                onDislike = { onReaction(source, YouTubeReaction.Disliked) },
                                onBookmark = { onBookmark(source) },
                                onInfo = { onSourceInfo(source, visibleQueueIds) },
                            )
                        }
                    }
                }
            }
        }
        openHeaderMenu?.let { menu ->
            StreamHeaderMenuDialog(
                menu = menu,
                selectedSort = sourceSort,
                selectedTab = selectedTab,
                activeShelf = activeShelf,
                onDismiss = { openHeaderMenu = null },
                onAll = {
                    openHeaderMenu = null
                    selectAll()
                },
                onRecent = {
                    openHeaderMenu = null
                    selectRecent()
                },
                onLiked = {
                    openHeaderMenu = null
                    selectLiked()
                },
                onSaved = {
                    openHeaderMenu = null
                    selectSaved()
                },
                onPlaylists = {
                    openHeaderMenu = null
                    selectPlaylists()
                },
                onPodcasts = {
                    openHeaderMenu = null
                    openPodcastDirectory()
                },
                onInbox = {
                    openHeaderMenu = null
                    selectInbox()
                },
                onSort = {
                    sourceSort = it
                    openHeaderMenu = null
                },
                onQuickAdd = {
                    openHeaderMenu = null
                    onAdd()
                },
                onAlbumBuilder = {
                    openHeaderMenu = null
                    onAlbumBuilder()
                },
                onNewPlaylist = {
                    openHeaderMenu = null
                    onNewPlaylist()
                },
                onRefreshDiscovery = {
                    openHeaderMenu = null
                    onRefreshDiscovery()
                },
                onRules = {
                    openHeaderMenu = null
                    onRules()
                },
            )
        }
        if (releaseNotificationsOpen) {
            BackHandler { releaseNotificationsOpen = false }
            StreamReleaseNotificationCenter(
                notifications = releaseNotifications,
                releaseCollection = newReleaseCollection,
                followedArtistCount = followedArtists.size,
                loading = newReleaseLoading,
                onRefresh = onRefreshNewReleases,
                onMarkAllRead = onMarkAllReleaseNotificationsRead,
                onOpen = { notification ->
                    releaseNotificationsOpen = false
                    newReleaseCollection?.let { collection -> onReleaseNotification(notification, collection) }
                },
                onDismiss = { releaseNotificationsOpen = false },
            )
        }
        if (artistFollowOpen) {
            BackHandler { artistFollowOpen = false }
            StreamArtistFollowScreen(
                followedArtists = followedArtists,
                options = artistFollowOptions,
                newReleaseLoading = newReleaseLoading,
                onToggleFollowArtist = onToggleFollowArtist,
                onToggleFollowArtistOption = onToggleFollowArtistOption,
                onRefreshNewReleases = onRefreshNewReleases,
                onDismiss = { artistFollowOpen = false },
            )
        }
        if (premiumChartMoreConfirmOpen) {
            BasicInfoModal(
                title = "Load more chart entries",
                subtitle = "The next batch adds chart metadata only. Matching playable tracks still runs in explicit 25 item batches.",
                onDismiss = { premiumChartMoreConfirmOpen = false },
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Keeping the first 25 as the default protects memory and network use. Continue when you want a wider chart window.",
                        color = StreamTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SourceActionButton("Cancel", DeckIcon.Close, Modifier.weight(1f), { premiumChartMoreConfirmOpen = false })
                        SourceActionButton(
                            "Continue",
                            DeckIcon.Plus,
                            Modifier.weight(1f),
                            {
                                premiumChartMoreConfirmOpen = false
                                onPremiumChartShowMore()
                            },
                            active = true,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PremiumDeckOfflineDeckCard(
    title: String,
    message: String,
    active: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color(0xFFB8C4D3).copy(alpha = 0.11f),
                        Color.Black.copy(alpha = 0.13f),
                        (if (active) Color(0xFF4EC7FF) else StreamAccentRed).copy(alpha = 0.12f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.13f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f),
                        ),
                    ),
                ),
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9DA8B8).copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(DeckIcon.StreamOffline, Color.White.copy(alpha = 0.80f), Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = StreamTextPrimary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    message,
                    color = StreamTextSecondary.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            PremiumDeckOfflineGlassToggle(active = active, onClick = onToggle)
        }
    }
}

@Composable
private fun PremiumDeckOfflineGlassToggle(active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember(active) { MutableInteractionSource() }
    Box(
        Modifier
            .width(58.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        (if (active) Color(0xFF57D6FF) else StreamAccentRed).copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.10f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(19.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(4.dp),
        contentAlignment = if (active) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
        )
    }
}

@Composable
private fun PremiumDeckChartDiscoverySurface(
    provider: PremiumDeckChartProvider,
    genre: PremiumDeckChartGenre,
    onOpenOption: (PremiumDeckChartOption) -> Unit,
) {
    val globalOptions = remember {
        listOf(
            PremiumDeckChartOption(
                provider = PremiumDeckChartProvider.Billboard,
                genre = PremiumDeckChartGenre.Global,
                title = "US Hot 100",
                subtitle = "No-key Billboard JSON chart",
                icon = DeckIcon.MusicList,
                accentColor = 0xFF2F8CFF.toInt(),
                enabled = true,
            ),
            PremiumDeckChartOption(
                provider = PremiumDeckChartProvider.AppleMusic,
                genre = PremiumDeckChartGenre.Global,
                title = "Global Top Songs",
                subtitle = "Apple Music public chart feed",
                icon = DeckIcon.MusicList,
                accentColor = 0xFF2F8CFF.toInt(),
                enabled = true,
            ),
        )
    }
    val billboardGenreOptions = remember {
        PremiumDeckChartGenre.entries
            .filter { it != PremiumDeckChartGenre.Global && premiumDeckBillboardChartAvailable(it) }
            .map { chartGenre ->
                PremiumDeckChartOption(
                    provider = PremiumDeckChartProvider.Billboard,
                    genre = chartGenre,
                    title = chartGenre.label,
                    subtitle = "No-key Billboard JSON chart",
                    icon = DeckIcon.StreamRadio,
                    accentColor = premiumDeckChartGenreAccent(chartGenre),
                    enabled = true,
                )
            }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF2F8CFF).copy(alpha = 0.62f), Color.Black.copy(alpha = 0.38f))))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(DeckIcon.StreamRadio, Color.White.copy(alpha = 0.92f), Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Charts", color = StreamTextPrimary, fontSize = 23.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black)
                Text(
                    "Open a chart like an album page; matching stays in safe batches",
                    color = StreamTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        PremiumDeckChartOptionShelf(
            title = "Global",
            subtitle = "Choose the chart source",
            options = globalOptions,
            selectedProvider = provider,
            selectedGenre = genre,
            onSelect = onOpenOption,
        )
        PremiumDeckChartOptionShelf(
            title = "Billboard Genres",
            subtitle = "No API key required",
            options = billboardGenreOptions,
            selectedProvider = provider,
            selectedGenre = genre,
            onSelect = onOpenOption,
        )
    }
}

private data class PremiumDeckChartOption(
    val provider: PremiumDeckChartProvider,
    val genre: PremiumDeckChartGenre,
    val title: String,
    val subtitle: String,
    val icon: DeckIcon,
    val accentColor: Int,
    val enabled: Boolean,
    val unavailableReason: String? = null,
)

@Composable
private fun PremiumDeckChartOptionShelf(
    title: String,
    subtitle: String,
    options: List<PremiumDeckChartOption>,
    selectedProvider: PremiumDeckChartProvider,
    selectedGenre: PremiumDeckChartGenre,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onSelect: (PremiumDeckChartOption) -> Unit,
    onUnavailable: (PremiumDeckChartOption) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, color = StreamTextPrimary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(subtitle, color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (actionLabel != null && onAction != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulseIcon(DeckIcon.Settings, StreamAccentRed, Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(actionLabel, color = StreamAccentRed, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = ((maxWidth - 12.dp) / 2f).coerceIn(154.dp, 184.dp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 2.dp)) {
                items(options, key = { "${it.provider.name}-${it.genre.name}" }) { option ->
                    val active = selectedGenre == option.genre &&
                        (selectedProvider == option.provider || (selectedProvider == PremiumDeckChartProvider.Auto && option.provider == PremiumDeckChartProvider.Billboard && option.genre == PremiumDeckChartGenre.Global))
                    PremiumDeckChartOptionCard(
                        option = option,
                        active = active,
                        width = cardWidth,
                        onClick = { if (option.enabled) onSelect(option) else onUnavailable(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumDeckChartOptionCard(
    option: PremiumDeckChartOption,
    active: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember(option.provider, option.genre, option.enabled) { MutableInteractionSource() }
    val accent = Color(option.accentColor)
    val cardAlpha = if (option.enabled) 1f else 0.46f
    Box(
        Modifier
            .width(width)
            .height(178.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.20f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.16f else 0.07f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.72f * cardAlpha),
                            Color(0xFF141821).copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.98f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(13.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = if (active) 0.30f else 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(if (option.enabled) option.icon else DeckIcon.Info, Color.White.copy(alpha = 0.90f * cardAlpha), Modifier.size(15.dp))
        }
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(StreamAccentRed)
                    .border(1.dp, Color.White.copy(alpha = 0.58f), CircleShape),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 15.dp, end = 15.dp, bottom = 15.dp),
        ) {
            Text(option.title, color = Color.White.copy(alpha = 0.96f * cardAlpha), fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(option.subtitle, color = Color.White.copy(alpha = 0.68f * cardAlpha), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = if (active) 0.22f else 0.08f), RoundedCornerShape(11.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        active -> "selected"
                        option.enabled -> "open"
                        else -> "locked"
                    },
                    color = Color.White.copy(alpha = 0.86f * cardAlpha),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PremiumDeckChartDetailPage(
    provider: PremiumDeckChartProvider,
    genre: PremiumDeckChartGenre,
    requestedLimit: Int,
    entries: List<PremiumDeckChartEntry>,
    matches: List<PremiumDeckChartMatch>,
    collection: StreamCollection?,
    loading: Boolean,
    matching: Boolean,
    error: String?,
    activeSourceId: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShowMore: () -> Unit,
    onMatchNext: () -> Unit,
    onOpenCollection: (StreamCollection) -> Unit,
    onPlayCollection: (StreamCollection) -> Unit,
) {
    val availabilityMessage = premiumDeckChartAvailabilityMessage(provider, genre)
    val effectiveProvider = entries.firstOrNull()?.provider ?: effectivePremiumDeckChartProvider(provider, genre) ?: provider
    val matchedKeys = remember(matches) { matches.map { it.entry.key }.toSet() }
    val matchedSourceIdsByEntryKey = remember(matches) { matches.associate { it.entry.key to it.result.source.id } }
    val unmatchedCount = remember(entries, matchedKeys) { entries.count { it.key !in matchedKeys } }
    val nextMatchCount = unmatchedCount.coerceAtMost(PREMIUM_DECK_CHART_PAGE_SIZE)
    val canShowMore = entries.isNotEmpty() && entries.size >= requestedLimit && requestedLimit < PREMIUM_DECK_CHART_SESSION_LIMIT
    val statusMessage = availabilityMessage ?: error

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumDeckChartDetailPanel(
            provider = effectiveProvider,
            genre = genre,
            requestedLimit = requestedLimit,
            entries = entries,
            matchedKeys = matchedKeys,
            matchedCount = matches.size,
            nextMatchCount = nextMatchCount,
            canShowMore = canShowMore,
            collection = collection,
            loading = loading,
            matching = matching,
            activeSourceId = activeSourceId,
            matchedSourceIdsByEntryKey = matchedSourceIdsByEntryKey,
            onBack = onBack,
            onShowMore = onShowMore,
            onMatchNext = onMatchNext,
            onOpenCollection = onOpenCollection,
            onPlayCollection = onPlayCollection,
        )

        if (!statusMessage.isNullOrBlank()) {
            YouTubeSearchStatusCard(statusMessage)
            SourceActionButton(
                if (loading) "Loading" else "Refresh",
                if (loading) DeckIcon.Timer else DeckIcon.Repeat,
                Modifier.fillMaxWidth(),
                onRefresh,
                enabled = !loading && !matching && availabilityMessage == null,
            )
        } else if (loading && entries.isEmpty()) {
            YouTubeSearchStatusCard("Loading chart entries...")
        } else if (entries.isEmpty()) {
            YouTubeSearchStatusCard("Refresh to load the first 25 chart tracks.")
            SourceActionButton(
                "Refresh",
                DeckIcon.Repeat,
                Modifier.fillMaxWidth(),
                onRefresh,
                enabled = !matching,
            )
        }
    }
}

@Composable
private fun PremiumDeckChartDetailPanel(
    provider: PremiumDeckChartProvider,
    genre: PremiumDeckChartGenre,
    requestedLimit: Int,
    entries: List<PremiumDeckChartEntry>,
    matchedKeys: Set<String>,
    matchedCount: Int,
    nextMatchCount: Int,
    canShowMore: Boolean,
    collection: StreamCollection?,
    loading: Boolean,
    matching: Boolean,
    activeSourceId: String?,
    matchedSourceIdsByEntryKey: Map<String, String>,
    onBack: () -> Unit,
    onShowMore: () -> Unit,
    onMatchNext: () -> Unit,
    onOpenCollection: (StreamCollection) -> Unit,
    onPlayCollection: (StreamCollection) -> Unit,
) {
    val accent = Color(premiumDeckChartGenreAccent(genre))
    val title = premiumDeckChartDetailTitle(provider, genre)
    val chartDate = entries.firstOrNull()?.releaseDate.orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumDeckChartHero(
            title = title,
            provider = provider,
            genre = genre,
            chartDate = chartDate,
            entries = entries,
            matchedCount = matchedCount,
            requestedLimit = requestedLimit,
            accent = accent,
            onBack = onBack,
        )

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 7.dp
            val circleSize = 42.dp
            val metricWidth = 66.dp
            val matchWidth = (maxWidth - (circleSize * 3f) - metricWidth - (gap * 4f)).coerceIn(88.dp, 116.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                PremiumDeckChartCircleAction(
                    icon = DeckIcon.Shuffle,
                    enabled = collection != null && !loading && !matching,
                    size = circleSize,
                    onClick = { collection?.let(onPlayCollection) },
                )
                PremiumDeckChartCircleAction(
                    icon = DeckIcon.Play,
                    enabled = collection != null && !loading && !matching,
                    active = collection != null,
                    size = circleSize,
                    onClick = { collection?.let(onPlayCollection) },
                )
                PremiumDeckChartMetricButton(
                    icon = DeckIcon.Search,
                    primary = "${entries.size}",
                    secondary = "tracks",
                    modifier = Modifier.width(metricWidth),
                )
                PremiumDeckChartMatchButton(
                    label = when {
                        matching -> "Matching"
                        nextMatchCount > 0 -> "Match $nextMatchCount"
                        else -> "Matched"
                    },
                    active = matchedCount > 0,
                    enabled = !loading && !matching && nextMatchCount > 0,
                    loading = matching,
                    modifier = Modifier.width(matchWidth),
                    onClick = onMatchNext,
                )
                PremiumDeckChartCircleAction(
                    icon = DeckIcon.Plus,
                    enabled = !loading && !matching && canShowMore,
                    size = circleSize,
                    onClick = onShowMore,
                )
            }
        }

        if (collection != null) {
            PremiumDeckChartCollectionCard(
                collection = collection,
                onOpen = { onOpenCollection(collection) },
                onPlay = { onPlayCollection(collection) },
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumDeckChartStatPill("${entries.size}", "loaded", Modifier.weight(1f))
            PremiumDeckChartStatPill("$matchedCount", "matched", Modifier.weight(1f))
            PremiumDeckChartStatPill("${nextMatchCount.coerceAtLeast(0)}", "next", Modifier.weight(1f))
        }

        Text(
            "Showing ${entries.size} loaded chart entries",
            color = StreamTextSecondary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                PremiumDeckChartEntryDetailRow(
                    entry = entry,
                    matched = entry.key in matchedKeys,
                    isPlaying = activeSourceId != null && matchedSourceIdsByEntryKey[entry.key] == activeSourceId,
                )
            }
        }

        if (canShowMore) {
            SourceActionButton(
                if (loading) "Loading" else "Load More",
                if (loading) DeckIcon.Timer else DeckIcon.Plus,
                Modifier.fillMaxWidth(),
                onShowMore,
                enabled = !loading && !matching,
            )
        }
    }
}

@Composable
private fun PremiumDeckChartHero(
    title: String,
    provider: PremiumDeckChartProvider,
    genre: PremiumDeckChartGenre,
    chartDate: String,
    entries: List<PremiumDeckChartEntry>,
    matchedCount: Int,
    requestedLimit: Int,
    accent: Color,
    onBack: () -> Unit,
) {
    val heroArt = remember(entries) {
        entries.mapNotNull { it.artworkUrl.takeIf { url -> url.startsWith("http", ignoreCase = true) } }
            .distinct()
            .take(3)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(218.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.86f),
                        Color(0xFF21151B).copy(alpha = 0.94f),
                        Color.Black.copy(alpha = 0.98f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp)),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.64f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.46f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(DeckIcon.Back, Color.White, Modifier.size(24.dp))
        }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (heroArt.isNotEmpty()) {
                heroArt.forEach { artworkUrl ->
                    PremiumDeckChartArtworkTile(
                        artworkUrl = artworkUrl,
                        modifier = Modifier.size(54.dp),
                        fallbackIcon = DeckIcon.MusicList,
                    )
                }
            } else {
                PremiumDeckChartArtworkTile(
                    artworkUrl = "",
                    modifier = Modifier.size(62.dp),
                    fallbackIcon = DeckIcon.MusicList,
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 102.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.24f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIcon(DeckIcon.MusicList, Color.White.copy(alpha = 0.86f), Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(provider.label, color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Text(
                title,
                color = Color.White,
                fontSize = 27.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    genre.label,
                    "${entries.size} loaded",
                    "$matchedCount matched",
                    "top ${requestedLimit.coerceAtMost(PREMIUM_DECK_CHART_SESSION_LIMIT)}",
                    chartDate.takeIf { it.isNotBlank() },
                ).filterNotNull().joinToString("  |  "),
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PremiumDeckChartCircleAction(
    icon: DeckIcon,
    enabled: Boolean,
    active: Boolean = false,
    size: Dp = 42.dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember(icon, enabled) { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) StreamAccentRed.copy(alpha = 0.92f) else Color.Black.copy(alpha = if (enabled) 0.32f else 0.16f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.10f else 0.04f), CircleShape)
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = if (enabled) 0.94f else 0.32f), Modifier.size(size * 0.42f))
    }
}

@Composable
private fun PremiumDeckChartMetricButton(
    icon: DeckIcon,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(21.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = 0.78f), Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(primary, color = StreamTextPrimary, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(secondary, color = StreamTextSecondary, fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PremiumDeckChartMatchButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(label, enabled) { MutableInteractionSource() }
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (active) StreamAccentRed.copy(alpha = 0.90f) else Color.Black.copy(alpha = if (enabled) 0.30f else 0.16f))
            .border(1.dp, Color.White.copy(alpha = if (enabled || active) 0.10f else 0.04f), RoundedCornerShape(21.dp))
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PulseIcon(if (loading) DeckIcon.Timer else DeckIcon.Check, Color.White.copy(alpha = if (enabled || active) 0.92f else 0.34f), Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = Color.White.copy(alpha = if (enabled || active) 0.94f else 0.34f),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PremiumDeckChartEntryDetailRow(entry: PremiumDeckChartEntry, matched: Boolean, isPlaying: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isPlaying) StreamAccentRed.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f))
            .border(
                1.dp,
                when {
                    isPlaying -> StreamAccentRed.copy(alpha = 0.34f)
                    matched -> Color.White.copy(alpha = 0.12f)
                    else -> Color.White.copy(alpha = 0.045f)
                },
                RoundedCornerShape(18.dp),
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (matched) Color(0xFF5CE4D0).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.075f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("#${entry.rank}", color = Color.White.copy(alpha = 0.90f), fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        if (isPlaying) {
            PremiumDeckNowPlayingIndicator()
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = StreamTextPrimary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isPlaying) "Now playing" else if (matched) "Matched" else entry.provider.label,
                color = if (isPlaying) Color.White.copy(alpha = 0.90f) else if (matched) Color(0xFF5CE4D0) else StreamTextSecondary.copy(alpha = 0.72f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun PremiumDeckChartArtworkTile(
    artworkUrl: String,
    modifier: Modifier = Modifier,
    fallbackIcon: DeckIcon = DeckIcon.MusicList,
) {
    val artworkUri = remember(artworkUrl) {
        artworkUrl.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
    }
    val artwork = rememberAlbumArtBitmap(artworkUri, null, useCase = ArtworkUseCase.YouTubeSearchThumbnail)
    Box(
        modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Color.Black.copy(alpha = 0.26f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
        } else {
            PulseIcon(fallbackIcon, Color.White.copy(alpha = 0.52f), Modifier.size(24.dp))
        }
    }
}

private fun premiumDeckChartDetailTitle(provider: PremiumDeckChartProvider, genre: PremiumDeckChartGenre): String =
    when {
        provider == PremiumDeckChartProvider.Billboard && genre == PremiumDeckChartGenre.Global -> "US Hot 100"
        genre == PremiumDeckChartGenre.Global -> "Global Chart"
        else -> "${genre.label} Chart"
    }

private fun premiumDeckChartGenreAccent(genre: PremiumDeckChartGenre): Int =
    when (genre) {
        PremiumDeckChartGenre.Global -> 0xFF2F8CFF.toInt()
        PremiumDeckChartGenre.Pop -> 0xFFFF4D8D.toInt()
        PremiumDeckChartGenre.Rnb -> 0xFF9D6CFF.toInt()
        PremiumDeckChartGenre.HipHop -> 0xFFFFB24A.toInt()
        PremiumDeckChartGenre.Afrobeats -> 0xFF35D07F.toInt()
        PremiumDeckChartGenre.Jazz -> 0xFF50C7FF.toInt()
        PremiumDeckChartGenre.Rock -> 0xFFFF5A4E.toInt()
        PremiumDeckChartGenre.Electronic -> 0xFF5CE4D0.toInt()
        PremiumDeckChartGenre.Latin -> 0xFFFFD166.toInt()
        PremiumDeckChartGenre.Country -> 0xFFC18B58.toInt()
        PremiumDeckChartGenre.EastAfrican -> 0xFF68E0A6.toInt()
    }

@Composable
private fun PremiumDeckChartStatPill(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.13f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = StreamTextPrimary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PremiumDeckChartCollectionCard(collection: StreamCollection, onOpen: () -> Unit, onPlay: () -> Unit) {
    val interactionSource = remember(collection.id) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2F8CFF).copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(DeckIcon.MusicList, Color.White.copy(alpha = 0.92f), Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(collection.title, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(collection.subtitle, color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        IconTap(DeckIcon.Play, onPlay, 34.dp)
    }
}

@Composable
private fun PremiumDeckChartEntryRow(entry: PremiumDeckChartEntry, matched: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = if (matched) 0.10f else 0.04f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (matched) Color(0xFF5CE4D0).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("#${entry.rank}", color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = StreamTextPrimary, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        val meta = when {
            matched -> "Matched"
            entry.provider == PremiumDeckChartProvider.LastFm && entry.score > 0 -> formatCompactCount(entry.score.toLong())
            entry.provider == PremiumDeckChartProvider.AppleMusic && entry.country.isNotBlank() -> entry.provider.label
            else -> entry.provider.label
        }
        Text(meta, color = if (matched) Color(0xFF5CE4D0) else StreamTextSecondary, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private data class PremiumDeckRouteCompositionStats(
    val sourceCount: Int,
    val acceptedSourceCount: Int,
    val visibleSourceCount: Int,
    val playlistCount: Int,
    val albumDraftCount: Int,
    val discoveryResultCount: Int,
    val personalizationMixCount: Int,
    val followedArtistCount: Int,
    val releaseNotificationCount: Int,
    val podcastEpisodeCount: Int,
    val searchResultCount: Int,
    val prepareStateCount: Int,
    val visibleShelfCount: Int,
) {
    fun log(reason: String) {
        AlbumArtworkRuntime.onPremiumDeckComposition(
            reason = reason,
            sourceCount = sourceCount,
            acceptedSourceCount = acceptedSourceCount,
            visibleSourceCount = visibleSourceCount,
            playlistCount = playlistCount,
            albumDraftCount = albumDraftCount,
            discoveryResultCount = discoveryResultCount,
            personalizationMixCount = personalizationMixCount,
            followedArtistCount = followedArtistCount,
            releaseNotificationCount = releaseNotificationCount,
            podcastEpisodeCount = podcastEpisodeCount,
            searchResultCount = searchResultCount,
            prepareStateCount = prepareStateCount,
            visibleShelfCount = visibleShelfCount,
        )
    }
}

@Composable
internal fun StreamLibraryHeader(
    onBack: () -> Unit,
    unreadReleaseCount: Int,
    hasReleaseNotifications: Boolean,
    onNotifications: () -> Unit,
    onArtists: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap(DeckIcon.Back, onBack, 36.dp)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                PREMIUMDECK_SOURCE_CATEGORY,
                color = StreamTextPrimary,
                fontSize = 22.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Discover",
                color = StreamTextSecondary.copy(alpha = 0.82f),
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        StreamHeaderGlassCapsule(
            unreadCount = unreadReleaseCount,
            hasNotifications = hasReleaseNotifications,
            onNotifications = onNotifications,
            onArtists = onArtists,
            onMore = onMore,
        )
    }
}

@Composable
private fun StreamHeaderGlassCapsule(
    unreadCount: Int,
    hasNotifications: Boolean,
    onNotifications: () -> Unit,
    onArtists: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier
            .width(116.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color(0xFF9CA8B7).copy(alpha = 0.20f),
                        Color.Black.copy(alpha = 0.20f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(23.dp))
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StreamHeaderGlassIcon(
            icon = DeckIcon.Notification,
            onClick = onNotifications,
            unreadCount = unreadCount,
            hasNotifications = hasNotifications,
        )
        StreamHeaderGlassIcon(DeckIcon.Person, onArtists)
        StreamHeaderGlassIcon(DeckIcon.More, onMore)
    }
}

@Composable
private fun StreamHeaderGlassIcon(
    icon: DeckIcon,
    onClick: () -> Unit,
    unreadCount: Int = 0,
    hasNotifications: Boolean = false,
) {
    val interactionSource = remember(icon) { MutableInteractionSource() }
    Box(
        Modifier
            .size(34.dp)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = 0.92f), Modifier.size(17.dp))
        if (unreadCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 1.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(StreamAccentRed)
                    .border(1.dp, Color.White.copy(alpha = 0.72f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    unreadCount.coerceAtMost(9).toString(),
                    color = Color.White,
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        } else if (hasNotifications) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.72f)),
            )
        }
    }
}

@Composable
private fun StreamReleaseNotificationCenter(
    notifications: List<StreamReleaseNotification>,
    releaseCollection: StreamCollection?,
    followedArtistCount: Int,
    loading: Boolean,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: (StreamReleaseNotification) -> Unit,
    onDismiss: () -> Unit,
) {
    val unreadCount = remember(notifications) { notifications.count { !it.read } }
    val subtitle = when {
        followedArtistCount == 0 -> "Followed artists power this feed"
        unreadCount > 0 -> "$unreadCount unread from followed artists"
        notifications.isNotEmpty() -> "${notifications.size} recent followed-artist releases"
        loading -> "Checking followed artists"
        else -> "$followedArtistCount followed artists"
    }
    BasicInfoModal(title = "Release Notifications", subtitle = subtitle, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceActionButton(
                    if (loading) "Refreshing" else "Refresh",
                    if (loading) DeckIcon.Timer else DeckIcon.StreamRecent,
                    Modifier.weight(1f),
                    onRefresh,
                    enabled = !loading && followedArtistCount > 0,
                )
                SourceActionButton(
                    "Mark Read",
                    DeckIcon.Check,
                    Modifier.weight(1f),
                    onMarkAllRead,
                    enabled = unreadCount > 0,
                )
            }
            when {
                notifications.isNotEmpty() && releaseCollection != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(notifications, key = { it.id }) { notification ->
                            StreamReleaseNotificationRow(notification = notification, onOpen = { onOpen(notification) })
                        }
                    }
                }
                followedArtistCount == 0 -> StreamInlineEmpty("Follow artists from the artist page to receive release alerts here")
                loading -> StreamInlineEmpty("Checking followed artists for new releases")
                notifications.isNotEmpty() -> StreamInlineEmpty("Release alerts are ready after the followed-artist lane refreshes")
                else -> StreamInlineEmpty("No new release alerts yet")
            }
        }
    }
}

@Composable
private fun StreamReleaseNotificationRow(notification: StreamReleaseNotification, onOpen: () -> Unit) {
    val interactionSource = remember(notification.id) { MutableInteractionSource() }
    val meta = remember(notification) {
        listOf(notification.activityLabel, notification.uploadedDate)
            .filter { it.isNotBlank() }
            .joinToString("  |  ")
            .ifBlank { "Followed artist activity" }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (notification.read) StreamGlassFill else Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = if (notification.read) 0.07f else 0.16f), RoundedCornerShape(20.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            YouTubeThumbnailBox(notification.source.bestThumbnailUrl(), Modifier.size(54.dp), fallbackIcon = DeckIcon.StreamRecent)
            if (!notification.read) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(StreamAccentRed)
                        .border(1.dp, Color.White.copy(alpha = 0.72f), CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(notification.title, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(notification.artistName, color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(meta, color = StreamTextSecondary.copy(alpha = 0.76f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(8.dp))
        IconTap(DeckIcon.Info, onOpen, 34.dp)
    }
}

@Composable
internal fun StreamHeaderActionButton(icon: DeckIcon, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(StreamGlassFill)
            .border(1.dp, Color.White.copy(alpha = 0.09f), CircleShape)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, StreamTextPrimary.copy(alpha = 0.84f), Modifier.size(15.dp))
    }
}

private fun streamSourceListSignature(sources: List<YouTubeSource>): String {
    var hash = 1125899906842597L
    sources.forEach { source ->
        hash = hash * 31L + source.streamBrowseFingerprint().hashCode()
    }
    return "${sources.size}:$hash"
}

private fun streamSearchResultListSignature(results: List<YouTubeSearchResult>): String {
    var hash = 1125899906842597L
    results.forEach { result ->
        hash = hash * 31L + result.score
        hash = hash * 31L + result.videoId.hashCode()
        hash = hash * 31L + result.source.streamBrowseFingerprint().hashCode()
    }
    return "${results.size}:$hash"
}

@Composable
private fun PremiumDeckDiscoveryStackShelf(
    collections: List<StreamCollection>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpen: (StreamCollection) -> Unit,
    onPlay: (StreamCollection) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Premium Deck Today", color = StreamTextPrimary, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Curated for you",
                    color = StreamTextSecondary,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !loading, onClick = onRefresh)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIcon(if (loading) DeckIcon.Timer else DeckIcon.StreamReplace, StreamAccentRed, Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (loading) "Refreshing" else "Refresh", color = StreamAccentRed, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            collections.isNotEmpty() -> {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = ((maxWidth - 12.dp) / 2f).coerceIn(170.dp, 188.dp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(collections, key = { it.id }) { collection ->
                            PremiumDeckDiscoveryCoverCard(
                                collection = collection,
                                width = cardWidth,
                                onOpen = { onOpen(collection) },
                                onPlay = { onPlay(collection) },
                            )
                        }
                    }
                }
            }
            loading -> StreamInlineEmpty("Reading YouTube Music Explore shelves")
            else -> StreamInlineEmpty(
                "Refresh to load New & Trending Songs, Artist On The Rise, and Today's Biggest Hits",
                actionLabel = "Refresh",
                onAction = onRefresh,
            )
        }
    }
}

@Composable
private fun PremiumDeckDiscoveryCoverCard(
    collection: StreamCollection,
    width: Dp,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val interactionSource = remember(collection.id) { MutableInteractionSource() }
    val accent = Color(collection.accentColor)
    val coverSources = remember(collection.id, collection.sources) {
        collection.sources
            .filter { it.kind == YouTubeSourceKind.Video }
            .distinctBy { it.streamDistinctKey() }
            .take(3)
    }
    Box(
        Modifier
            .width(width)
            .height(218.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.20f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.78f),
                            StreamDeepRed.copy(alpha = 0.48f),
                            Color.Black.copy(alpha = 0.96f),
                        ),
                    ),
                ),
        ) {
            if (coverSources.isNotEmpty()) {
                PremiumDeckTodayCoverSlices(coverSources, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.06f),
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Black.copy(alpha = 0.62f),
                                    Color.Black.copy(alpha = 0.96f),
                                ),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.54f),
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                StreamAccentRed.copy(alpha = 0.34f),
                                Color.Black.copy(alpha = 0.34f),
                            ),
                        ),
                    )
                    .border(1.dp, StreamAccentRed.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(premiumDeckTodayIcon(collection), StreamAccentRed.copy(alpha = 0.92f), Modifier.size(14.dp))
            }
            Text(
                premiumDeckTodayTitle(collection),
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 70.dp).fillMaxWidth(),
            )
            Text(
                premiumDeckTodaySubtitle(collection),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 42.dp).fillMaxWidth(),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(1.dp, StreamAccentRed.copy(alpha = 0.38f), RoundedCornerShape(11.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (collection.sources.isEmpty()) "loading" else "${collection.sources.size.coerceAtMost(30)} songs",
                    color = StreamAccentRed.copy(alpha = 0.96f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 13.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(StreamAccentRed)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                    .clickable(enabled = collection.sources.isNotEmpty(), onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(if (collection.sources.isEmpty()) DeckIcon.Timer else DeckIcon.Play, Color.White, Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun PremiumDeckTodayCoverSlices(sources: List<YouTubeSource>, modifier: Modifier = Modifier) {
    Row(modifier) {
        sources.take(3).forEach { source ->
            PremiumDeckTodayCoverSlice(
                source = source,
                Modifier.weight(1f).fillMaxSize(),
            )
        }
        repeat((3 - sources.size).coerceAtLeast(0)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f)),
            )
        }
    }
}

@Composable
private fun PremiumDeckTodayCoverSlice(source: YouTubeSource, modifier: Modifier = Modifier) {
    val normalizedThumbnailUrl = remember(source.thumbnailUrl) {
        normalizeYouTubeThumbnailUrl(source.bestThumbnailUrl()) ?: source.bestThumbnailUrl()?.takeIf { it.startsWith("http", ignoreCase = true) }
    }
    val thumbnailUri = remember(normalizedThumbnailUrl) {
        normalizedThumbnailUrl?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
    }
    val thumbnail = rememberAlbumArtBitmap(thumbnailUri, null, useCase = ArtworkUseCase.YouTubeSearchThumbnail)
    Box(
        modifier.background(Color.Black.copy(alpha = 0.24f)),
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
            PulseIcon(DeckIcon.StreamMix, Color.White.copy(alpha = 0.48f), Modifier.size(24.dp))
        }
    }
}

private fun premiumDeckTodayTitle(collection: StreamCollection): String {
    val key = collection.title.normalizedSearchText()
    return when {
        collection.isFollowedArtistReleaseCollection() -> "From Artists You Follow"
        key.contains("new") || key.contains("released") || key.contains("trending") -> "Released"
        else -> collection.title
    }
}

private fun premiumDeckTodaySubtitle(collection: StreamCollection): String {
    val key = collection.title.normalizedSearchText()
    return when {
        collection.isFollowedArtistReleaseCollection() -> "Fresh tracks from the artists you follow"
        key.contains("new") || key.contains("released") || key.contains("trending") -> "The hottest new songs this week"
        else -> collection.subtitle
    }
}

private fun premiumDeckTodayIcon(collection: StreamCollection): DeckIcon {
    val key = collection.title.normalizedSearchText()
    return when {
        collection.isFollowedArtistReleaseCollection() -> DeckIcon.People
        key.contains("new") || key.contains("released") || key.contains("trending") -> DeckIcon.Discover
        else -> discoveryCollectionIcon(collection.id)
    }
}

private fun discoveryCollectionIcon(id: String): DeckIcon =
    when {
        id.contains("new") -> DeckIcon.StreamRecent
        id.contains("trending") -> DeckIcon.Discover
        id.contains("chart") -> DeckIcon.MusicList
        id.contains("rising") -> DeckIcon.Compass
        else -> DeckIcon.StreamMix
    }

@Composable
internal fun StreamHero(sourceCount: Int, playlistCount: Int, likedCount: Int, savedCount: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconDisc(Color(0xFF7FE7C3), DeckIcon.StreamSource, Modifier.size(44.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                PREMIUMDECK_SOURCE_CATEGORY,
                color = StreamTextPrimary,
                fontSize = 24.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$sourceCount sources  $playlistCount playlists  $likedCount liked  $savedCount saved",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun StreamSearchBar(query: String, onQuery: (String) -> Unit, onFocus: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.26f),
                        Color(0xFF9BA7B8).copy(alpha = 0.17f),
                        Color.Black.copy(alpha = 0.16f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
            .padding(start = 11.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Search, Color.White.copy(alpha = 0.62f), Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = StreamTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black),
            cursorBrush = SolidColor(Color.White.copy(alpha = 0.92f)),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocus() },
            decorationBox = { inner ->
                if (query.isBlank()) Text("Search for a song or artist", color = Color.White.copy(alpha = 0.54f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            },
        )
        if (query.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            IconTap(DeckIcon.Close, { onQuery("") }, 24.dp)
        }
    }
}

@Composable
internal fun StreamArtistFollowScreen(
    followedArtists: List<FollowedStreamArtist>,
    options: List<StreamArtistFollowOption>,
    newReleaseLoading: Boolean,
    onToggleFollowArtist: (String) -> Unit,
    onToggleFollowArtistOption: (StreamArtistFollowOption) -> Unit,
    onRefreshNewReleases: () -> Unit,
    onDismiss: () -> Unit,
) {
    var artistQuery by rememberSaveable { mutableStateOf("") }
    var artistSearchResults by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
    var artistSearchLoading by remember { mutableStateOf(false) }
    var artistSearchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artistQuery) {
        val needle = artistQuery.trim()
        if (needle.length < 2) {
            artistSearchResults = emptyList()
            artistSearchLoading = false
            artistSearchError = null
            return@LaunchedEffect
        }
        delay(350L)
        if (artistQuery.trim() != needle) return@LaunchedEffect
        artistSearchLoading = true
        artistSearchError = null
        val response = runCatching {
            searchYouTubeVideos("$needle official audio", requestLabel = "artist_follow", debounceMillis = 350L)
        }.getOrDefault(YouTubeSearchResponse())
        if (artistQuery.trim() == needle) {
            artistSearchResults = response.results
            artistSearchLoading = false
            artistSearchError = if (response.results.isEmpty()) "No official artist matches found" else null
        }
    }

    val query = artistQuery.trim()
    val queryKey = query.cleanStreamArtistName().normalizedSearchText()
    val followedKeys = remember(followedArtists) { followedArtists.map { it.key }.toSet() }
    val followedOfficialKeys = remember(followedArtists) { followedArtists.map { it.officialKey }.toSet() }
    val liveOptions = remember(options, artistSearchResults, followedArtists) {
        val searchOptions = buildStreamArtistFollowOptions(
            sources = emptyList(),
            discoveryResults = artistSearchResults,
            playHistory = emptyList(),
            followedArtists = followedArtists,
        )
        (searchOptions + options).distinctBy { it.key }
    }
    val visibleOptions = remember(liveOptions, query) {
        val needle = query.normalizedSearchText()
        val filtered = if (needle.isBlank()) {
            liveOptions
        } else {
            liveOptions.filter { option ->
                option.name.normalizedSearchText().contains(needle) ||
                    option.officialName.normalizedSearchText().contains(needle) ||
                    needle.contains(option.key) ||
                    needle.contains(option.officialKey)
            }
        }
        filtered.distinctBy { it.key }.take(24)
    }
    val followedRows = remember(followedArtists, options) {
        followedArtists.map { artist ->
            options.firstOrNull { it.key == artist.key || it.officialKey == artist.officialKey }
                ?: StreamArtistFollowOption(
                    name = artist.name,
                    key = artist.key,
                    sourceCount = 0,
                    lastSeenMillis = artist.followedAtMillis,
                    officialName = artist.officialName,
                    officialKey = artist.officialKey,
                )
        }.distinctBy { it.key }
    }
    val queryAlreadyFollowed = queryKey.isNotBlank() && (queryKey in followedKeys || queryKey in followedOfficialKeys)
    val showManualFollow = queryKey.isNotBlank() &&
        queryKey !in genericStreamAlbumArtistKeys &&
        visibleOptions.none { it.key == queryKey || it.officialKey == queryKey }

    Box(
        Modifier
            .fillMaxSize()
            .background(StreamBase)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(DeckIcon.Back, onDismiss, 50.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Artists to Follow", color = StreamTextPrimary, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${followedArtists.size} followed  |  monthly New Releases", color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (followedArtists.isNotEmpty()) {
                IconCircle(if (newReleaseLoading) DeckIcon.Timer else DeckIcon.StreamRecent, onRefreshNewReleases, 50.dp)
            }
        }
            StreamArtistSearchField(query = artistQuery, onQuery = { artistQuery = it })
            Spacer(Modifier.height(14.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 184.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showManualFollow) {
                    item {
                        StreamManualArtistFollowRow(
                            query = query,
                            followed = queryAlreadyFollowed,
                            onToggle = { onToggleFollowArtist(query) },
                        )
                    }
                }
                if (artistSearchLoading && query.length >= 2) {
                    item { YouTubeSearchStatusCard("Searching official PremiumDeck artists...") }
                }
                if (!artistSearchLoading && artistSearchError != null && query.length >= 2 && visibleOptions.isEmpty()) {
                    item { YouTubeSearchStatusCard(artistSearchError.orEmpty()) }
                }
                if (followedRows.isNotEmpty()) {
                    item { StreamSourcesSectionHeader("Following", "Artists used for monthly New Releases") }
                    items(followedRows, key = { "followed-${it.key}-${it.officialKey}" }) { option ->
                        StreamArtistFollowRow(
                            option = option,
                            followed = true,
                            onToggle = { onToggleFollowArtistOption(option) },
                        )
                    }
                }
                item {
                    StreamSourcesSectionHeader(
                        title = if (query.isBlank()) "Suggested artists" else "Official artist matches",
                        subtitle = if (query.isBlank()) "Based on PremiumDeck sources, discovery, and listening" else "Tap Follow on the artist page that matches your search",
                    )
                }
                if (visibleOptions.isEmpty() && !artistSearchLoading) {
                    item {
                        StreamInlineEmpty(
                            if (query.isBlank()) "Search an artist name to follow their official PremiumDeck page" else "No artist match yet. You can still follow $query above",
                        )
                    }
                } else {
                    items(visibleOptions, key = { "option-${it.key}-${it.officialKey}" }) { option ->
                        StreamArtistFollowRow(
                            option = option,
                            followed = option.key in followedKeys || option.officialKey in followedOfficialKeys,
                            onToggle = { onToggleFollowArtistOption(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StreamArtistSearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(25.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Search, Color.White.copy(alpha = 0.72f), Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = StreamTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isBlank()) Text("Search official artist page", color = Color.White.copy(alpha = 0.48f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            },
        )
        if (query.isNotBlank()) {
            Spacer(Modifier.width(7.dp))
            IconTap(DeckIcon.Close, { onQuery("") }, 28.dp)
        }
    }
}

@Composable
internal fun StreamManualArtistFollowRow(query: String, followed: Boolean, onToggle: () -> Unit) {
    StreamArtistFollowSurface(
        title = query,
        subtitle = if (followed) "Following this artist" else "Follow typed artist",
        thumbnailUrl = null,
        followed = followed,
        onToggle = onToggle,
    )
}

@Composable
internal fun StreamArtistFollowRow(option: StreamArtistFollowOption, followed: Boolean, onToggle: () -> Unit) {
    val subtitle = buildList {
        add(if (option.officialChannelKey.isNotBlank()) "Official channel linked" else option.officialName.ifBlank { option.name })
        if (option.sourceCount > 0) add("${option.sourceCount} sources")
    }.distinct().joinToString("  |  ")
    StreamArtistFollowSurface(
        title = option.name,
        subtitle = subtitle,
        thumbnailUrl = option.thumbnailUrl,
        followed = followed,
        onToggle = onToggle,
    )
}

@Composable
internal fun StreamArtistFollowSurface(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    followed: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember(title) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (followed) Color.White.copy(alpha = 0.12f) else StreamGlassFill)
            .border(1.dp, Color.White.copy(alpha = if (followed) 0.14f else 0.07f), RoundedCornerShape(21.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp)) {
            YouTubeThumbnailBox(thumbnailUrl, Modifier.fillMaxSize().clip(CircleShape), DeckIcon.Person)
            if (followed) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(StreamAccentRed)
                        .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PulseIcon(DeckIcon.Check, Color.White, Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(10.dp))
        SourceActionButton(
            label = if (followed) "Following" else "Follow",
            icon = if (followed) DeckIcon.Check else DeckIcon.Heart,
            modifier = Modifier.width(96.dp),
            onClick = onToggle,
            active = followed,
        )
    }
}

@Composable
internal fun StreamArtistFollowPanel(
    followedArtists: List<FollowedStreamArtist>,
    options: List<StreamArtistFollowOption>,
    newReleaseLoading: Boolean,
    onToggleFollowArtist: (String) -> Unit,
    onRefreshNewReleases: () -> Unit,
) {
    var artistQuery by rememberSaveable { mutableStateOf("") }
    val query = artistQuery.trim()
    val queryKey = query.cleanStreamArtistName().normalizedSearchText()
    val followedKeys = remember(followedArtists) { followedArtists.map { it.key }.toSet() }
    val canUseQuery = queryKey.isNotBlank() && queryKey !in genericStreamAlbumArtistKeys
    val queryIsFollowed = canUseQuery && queryKey in followedKeys
    val visibleOptions = remember(options, query) {
        val needle = query.normalizedSearchText()
        val filtered = if (needle.isBlank()) {
            options
        } else {
            options.filter { option ->
                option.name.normalizedSearchText().contains(needle) || needle.contains(option.key)
            }
        }
        filtered.take(10)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StreamCardRadius))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(StreamCardRadius))
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconDisc(Color.White.copy(alpha = 0.16f), DeckIcon.Person, Modifier.size(46.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Artists to Follow", color = StreamTextPrimary, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${followedArtists.size} followed  |  New Releases updates weekly",
                    color = StreamTextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (followedArtists.isNotEmpty()) {
                SourceActionButton(
                    label = if (newReleaseLoading) "Refreshing" else "Refresh",
                    icon = if (newReleaseLoading) DeckIcon.Timer else DeckIcon.StreamRecent,
                    modifier = Modifier.width(96.dp),
                    onClick = onRefreshNewReleases,
                    enabled = !newReleaseLoading,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(21.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIcon(DeckIcon.Search, Color.White.copy(alpha = 0.66f), Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = artistQuery,
                    onValueChange = { artistQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = StreamTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (artistQuery.isBlank()) Text("Search artist to follow", color = Color.White.copy(alpha = 0.46f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    },
                )
                if (artistQuery.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    IconTap(DeckIcon.Close, { artistQuery = "" }, 26.dp)
                }
            }
            SourceActionButton(
                label = if (queryIsFollowed) "Following" else "Follow",
                icon = if (queryIsFollowed) DeckIcon.StreamLike else DeckIcon.Heart,
                modifier = Modifier.width(92.dp),
                onClick = { onToggleFollowArtist(query) },
                active = queryIsFollowed,
                enabled = canUseQuery,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (visibleOptions.isEmpty()) {
            StreamInlineEmpty(
                if (query.isBlank()) "Search any artist name, then tap Follow" else "Tap Follow to add $query",
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleOptions, key = { it.key }) { option ->
                    StreamArtistFollowChip(
                        option = option,
                        followed = option.key in followedKeys,
                        onToggle = { onToggleFollowArtist(option.name) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun StreamArtistFollowChip(option: StreamArtistFollowOption, followed: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember(option.key) { MutableInteractionSource() }
    Row(
        Modifier
            .height(38.dp)
            .widthIn(min = 112.dp, max = 196.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (followed) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (followed) 0.14f else 0.07f), RoundedCornerShape(19.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(if (followed) DeckIcon.StreamLike else DeckIcon.Heart, Color.White.copy(alpha = 0.84f), Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(option.name, color = StreamTextPrimary, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (option.sourceCount > 0) {
                Text("${option.sourceCount} sources", color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun StreamHomeHero(
    sources: List<YouTubeSource>,
    playlists: List<YouTubePlaylist>,
    discoveryLoading: Boolean,
    onPlay: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(StreamCardRadius))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(StreamCardRadius))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDisc(Color.White.copy(alpha = 0.18f), DeckIcon.Discover, Modifier.size(50.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Made for your streams", color = StreamTextPrimary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = "${sources.count { it.playCount > 0 }} played  |  ${sources.count { it.reaction == YouTubeReaction.Liked }} liked  |  ${playlists.size} playlists"
            Text(meta, color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            if (discoveryLoading) {
                Text("Refreshing recommendations", color = StreamAmber.copy(alpha = 0.88f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 5.dp), maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(if (discoveryLoading) DeckIcon.Timer else DeckIcon.Play, Color.White, Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun StreamFilterChipRow(
    sources: List<YouTubeSource>,
    playlists: List<YouTubePlaylist>,
    podcastCount: Int,
    selectedTab: YouTubeLibraryTab,
    activeShelf: YouTubeSmartShelf?,
    onAll: () -> Unit,
    onRecent: () -> Unit,
    onLiked: () -> Unit,
    onSaved: () -> Unit,
    onPlaylists: () -> Unit,
    onPodcasts: () -> Unit,
    onInbox: () -> Unit,
) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { StreamFilterChip("All", sources.count { it.reviewState == YouTubeReviewState.Accepted }, DeckIcon.Grid, activeShelf == null && selectedTab == YouTubeLibraryTab.Sources, onAll) }
        item { StreamFilterChip("Recent", sources.count { it.lastPlayedMillis > 0L }, DeckIcon.StreamRecent, activeShelf == YouTubeSmartShelf.Recent, onRecent) }
        item { StreamFilterChip("Liked", sources.count { it.reaction == YouTubeReaction.Liked }, DeckIcon.Heart, activeShelf == YouTubeSmartShelf.Liked, onLiked) }
        item { StreamFilterChip("Offline Saved", sources.count { it.downloadState == YouTubeDownloadState.Downloaded || it.status == YouTubeSourceStatus.Downloaded }, DeckIcon.StreamOffline, selectedTab == YouTubeLibraryTab.Downloads || activeShelf == YouTubeSmartShelf.SavedOffline, onSaved) }
        item { StreamFilterChip("Playlists", playlists.size, DeckIcon.Playlist, selectedTab == YouTubeLibraryTab.Playlists, onPlaylists) }
        item { StreamFilterChip("Podcasts", podcastCount, DeckIcon.StreamRadio, selectedTab == YouTubeLibraryTab.Podcasts || activeShelf == YouTubeSmartShelf.Podcasts, onPodcasts) }
        item { StreamFilterChip("Inbox", sources.count { it.reviewState == YouTubeReviewState.Inbox }, DeckIcon.LibraryStack, activeShelf == YouTubeSmartShelf.Inbox, onInbox) }
    }
}

@Composable
private fun PremiumDeckPodcastShelf(
    episodes: List<YouTubeSearchResult>,
    loading: Boolean,
    onDiscover: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onSource: (YouTubeSource) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Podcasts", color = StreamTextPrimary, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Four long-form picks, with discovery inline", color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StreamShelfActionButton(if (loading) "Scanning" else "Discover", if (loading) DeckIcon.Timer else DeckIcon.Discover, onDiscover)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(4, key = { index -> episodes.getOrNull(index)?.source?.streamDistinctKey() ?: "podcast-empty-$index" }) { index ->
                val episode = episodes.getOrNull(index)
                if (episode == null) {
                    PodcastPlaceholderCard(loading = loading, onClick = onDiscover)
                } else {
                    PodcastSourceCard(episode = episode, onClick = { onSource(episode.source) })
                }
            }
        }
        StreamShelfActionButton("Show all", DeckIcon.StreamRadio, onOpenPodcasts)
    }
}

@Composable
private fun PodcastSourceCard(episode: YouTubeSearchResult, onClick: () -> Unit) {
    val source = episode.source
    val interaction = remember(source.streamDistinctKey()) { MutableInteractionSource() }
    Column(
        Modifier
            .width(154.dp)
            .heightIn(min = 160.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(StreamGlassFill)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.fillMaxWidth().height(76.dp), fallbackIcon = DeckIcon.StreamRadio)
        Text(source.title, color = StreamTextPrimary, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
        Text(source.author, color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        val meta = buildList {
            if (episode.uploadedDate.isNotBlank()) add(episode.uploadedDate)
            if (source.durationMillis > 0L) add(formatDuration(source.durationMillis))
            if (source.isOfflineSaved()) add("Saved")
        }.joinToString("  |  ").ifBlank { "Podcast" }
        Text(meta, color = StreamAmber.copy(alpha = 0.82f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun PremiumDeckPodcastAddPanel(
    expanded: Boolean,
    query: String,
    loading: Boolean,
    onExpand: () -> Unit,
    onQuery: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val interaction = remember(expanded) { MutableInteractionSource() }
    if (!expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(StreamGlassFill)
                .border(1.dp, StreamAmber.copy(alpha = 0.13f), RoundedCornerShape(18.dp))
                .pressScaleEffect(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onExpand)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(StreamAmber.copy(alpha = 0.16f))
                    .border(1.dp, StreamAmber.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(DeckIcon.Plus, Color.White, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Add podcast", color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Search a channel and save recent episodes", color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            PulseIcon(DeckIcon.Search, Color.White.copy(alpha = 0.84f), Modifier.size(18.dp))
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StreamGlassFill)
            .border(1.dp, StreamAmber.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseIcon(if (loading) DeckIcon.Timer else DeckIcon.Search, StreamAmber, Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("Add podcast", color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            IconTap(DeckIcon.Close, onCancel, 30.dp)
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text("Podcast or channel name", color = StreamTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        inner()
                    }
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamShelfActionButton(if (loading) "Searching" else "Save", if (loading) DeckIcon.Timer else DeckIcon.Check, onSubmit)
            StreamShelfActionButton("Close", DeckIcon.Close, onCancel)
        }
    }
}

@Composable
private fun PremiumDeckPodcastShowRow(
    show: PremiumDeckPodcastShow,
    expanded: Boolean,
    loading: Boolean,
    episodeCount: Int,
    onClick: () -> Unit,
) {
    val interaction = remember(show.id) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (expanded) Color.White.copy(alpha = 0.11f) else StreamGlassFill)
            .border(1.dp, Color.White.copy(alpha = if (expanded) 0.13f else 0.07f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(StreamAmber.copy(alpha = 0.16f))
                .border(1.dp, StreamAmber.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (show.custom) {
                PulseIcon(DeckIcon.StreamRadio, Color.White, Modifier.size(18.dp))
            } else {
                Text(show.rank.toString(), color = Color.White, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(show.title, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = when {
                loading -> "Loading latest episodes"
                episodeCount > 0 -> "$episodeCount recent episodes ready"
                show.custom -> "Saved podcast channel"
                else -> "Tap to fetch latest YouTube episodes"
            }
            Text(subtitle, color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(8.dp))
        PulseIcon(
            when {
                loading -> DeckIcon.Timer
                expanded -> DeckIcon.Minus
                else -> DeckIcon.Plus
            },
            Color.White.copy(alpha = 0.84f),
            Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PremiumDeckPodcastEpisodeRow(
    show: PremiumDeckPodcastShow,
    episode: YouTubeSearchResult,
    resolving: Boolean,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
) {
    val source = episode.source
    val interaction = remember(source.streamDistinctKey()) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.13f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(54.dp), fallbackIcon = DeckIcon.StreamRadio)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, color = StreamTextPrimary, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val channelLabel = source.author.ifBlank { source.channelTitle }.ifBlank { show.title }
            Text(channelLabel, color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            val meta = buildList {
                if (episode.uploadedDate.isNotBlank()) add(episode.uploadedDate)
                if (source.durationMillis > 0L) add(formatDuration(source.durationMillis))
                if (source.isOfflineSaved()) add("Saved")
            }.joinToString("  |  ").ifBlank { "Latest episode" }
            Text(meta, color = StreamAmber.copy(alpha = 0.82f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        IconTap(if (resolving) DeckIcon.Timer else DeckIcon.Info, onInfo, 34.dp)
        IconTap(if (resolving) DeckIcon.Timer else DeckIcon.Play, onPlay, 36.dp)
    }
}

@Composable
private fun PodcastPlaceholderCard(loading: Boolean, onClick: () -> Unit) {
    val interaction = remember(loading) { MutableInteractionSource() }
    Column(
        Modifier
            .width(154.dp)
            .heightIn(min = 160.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.15f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (loading) "Scanning" else "Discover", color = StreamTextPrimary, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("Podcast source", color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun StreamShelfActionButton(label: String, icon: DeckIcon, onClick: () -> Unit) {
    val interaction = remember(label) { MutableInteractionSource() }
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(17.dp))
            .pressScaleEffect(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = 0.88f), Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
internal fun StreamFilterChip(label: String, count: Int, icon: DeckIcon, active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (active) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.10f else 0.04f), RoundedCornerShape(17.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = if (active) 0.96f else 0.64f), Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = if (active) 0.96f else 0.68f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        Text(count.toString(), color = Color.White.copy(alpha = if (active) 0.92f else 0.50f), fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun StreamHeaderMenuDialog(
    menu: StreamHeaderMenu,
    selectedSort: StreamSourceSort,
    selectedTab: YouTubeLibraryTab,
    activeShelf: YouTubeSmartShelf?,
    onDismiss: () -> Unit,
    onAll: () -> Unit,
    onRecent: () -> Unit,
    onLiked: () -> Unit,
    onSaved: () -> Unit,
    onPlaylists: () -> Unit,
    onPodcasts: () -> Unit,
    onInbox: () -> Unit,
    onSort: (StreamSourceSort) -> Unit,
    onQuickAdd: () -> Unit,
    onAlbumBuilder: () -> Unit,
    onNewPlaylist: () -> Unit,
    onRefreshDiscovery: () -> Unit,
    onRules: () -> Unit,
) {
    val title = when (menu) {
        StreamHeaderMenu.Filter -> "Filter Library"
        StreamHeaderMenu.Sort -> "Sort Sources"
        StreamHeaderMenu.More -> "Stream Actions"
    }
    val subtitle = when (menu) {
        StreamHeaderMenu.Filter -> "Choose a focused library view"
        StreamHeaderMenu.Sort -> "Apply local ordering to source rows"
        StreamHeaderMenu.More -> "Quick add, playlists, discovery, and rules"
    }
    BasicInfoModal(title = title, subtitle = subtitle, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (menu) {
                StreamHeaderMenu.Filter -> {
                    StreamMenuOption("All", "Accepted stream sources", DeckIcon.Grid, activeShelf == null && selectedTab == YouTubeLibraryTab.Sources, onAll)
                    StreamMenuOption("Recent", "Sources with listening history", DeckIcon.StreamRecent, activeShelf == YouTubeSmartShelf.Recent, onRecent)
                    StreamMenuOption("Liked", "Streams you marked as favorites", DeckIcon.Heart, activeShelf == YouTubeSmartShelf.Liked, onLiked)
                    StreamMenuOption("Offline Saved", "Sources saved for offline listening", DeckIcon.StreamOffline, selectedTab == YouTubeLibraryTab.Downloads || activeShelf == YouTubeSmartShelf.SavedOffline, onSaved)
                    StreamMenuOption("Playlists", "Manual and generated playlists", DeckIcon.Playlist, selectedTab == YouTubeLibraryTab.Playlists, onPlaylists)
                    StreamMenuOption("Podcasts", "Podcast-style sources", DeckIcon.StreamRadio, selectedTab == YouTubeLibraryTab.Podcasts || activeShelf == YouTubeSmartShelf.Podcasts, onPodcasts)
                    StreamMenuOption("Inbox", "Sources waiting for review", DeckIcon.LibraryStack, activeShelf == YouTubeSmartShelf.Inbox, onInbox)
                }
                StreamHeaderMenu.Sort -> {
                    StreamSourceSort.entries.forEach { sort ->
                        StreamMenuOption(sort.label, sort.subtitle, DeckIcon.Sliders, selectedSort == sort) { onSort(sort) }
                    }
                }
                StreamHeaderMenu.More -> {
                    StreamMenuOption("Quick Add", "Paste or scan a new stream source", DeckIcon.StreamAdd, false, onQuickAdd)
                    StreamMenuOption("Album Builder", "Search album metadata and save tracklist projects", DeckIcon.Disc, false, onAlbumBuilder)
                    StreamMenuOption("New Playlist", "Create a manual stream playlist", DeckIcon.Playlist, false, onNewPlaylist)
                    StreamMenuOption("Refresh Discovery", "Regenerate recommendations", DeckIcon.Discover, false, onRefreshDiscovery)
                    StreamMenuOption("Library Rules", "Storage and resolver details", DeckIcon.Info, false, onRules)
                }
            }
        }
    }
}

@Composable
internal fun StreamMenuOption(title: String, subtitle: String, icon: DeckIcon, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember(title) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else StreamGlassFill)
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.13f else 0.07f), RoundedCornerShape(17.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (selected) 0.11f else 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(icon, Color.White.copy(alpha = if (selected) 0.94f else 0.82f), Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = StreamTextPrimary, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.84f)))
        }
    }
}
