package com.pulsedeck.app

import kotlin.math.max

internal data class YouTubeSource(
    val id: String,
    val url: String,
    val kind: YouTubeSourceKind,
    val title: String,
    val author: String,
    val thumbnailUrl: String? = null,
    val channelTitle: String = author,
    val channelUrl: String = "",
    val channelVerified: Boolean = false,
    val albumTitleHint: String = "",
    val albumTrackNumberHint: Int = 0,
    val albumTrackTotalHint: Int = 0,
    val albumYearHint: Int = 0,
    val durationMillis: Long = 0L,
    val quality: YouTubeQuality = YouTubeQuality.High,
    val playCount: Int = 0,
    val addedMillis: Long = System.currentTimeMillis(),
    val lastPlayedMillis: Long = 0L,
    val reaction: YouTubeReaction = YouTubeReaction.Neutral,
    val bookmarked: Boolean = false,
    val isPodcast: Boolean = false,
    val status: YouTubeSourceStatus = YouTubeSourceStatus.StreamReady,
    val cachedUri: String? = null,
    val cachedMillis: Long = 0L,
    val downloadedUri: String? = null,
    val downloadState: YouTubeDownloadState = YouTubeDownloadState.None,
    val downloadProgress: Int = 0,
    val playbackPositionMillis: Long = 0L,
    val chapters: List<YouTubeChapter> = emptyList(),
    val sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    val neverPromptCache: Boolean = false,
    val skipSegmentsEnabled: Boolean = true,
    val trimSilenceOnDownload: Boolean = false,
    val reviewState: YouTubeReviewState = YouTubeReviewState.Accepted,
)

internal data class YouTubeSearchResult(
    val source: YouTubeSource,
    val durationMillis: Long = 0L,
    val uploadedDate: String = "",
    val views: Long = 0L,
    val videoId: String = "",
    val score: Int = 0,
    val cachedMillis: Long = 0L,
    val matchReason: String? = null,
)

internal data class SmartYouTubeMusicMetadata(
    val artist: String,
    val title: String,
    val confidence: Int,
    val reason: String,
)

internal data class YouTubeSearchSuggestion(
    val text: String,
)

internal data class YouTubeSearchResponse(
    val results: List<YouTubeSearchResult> = emptyList(),
    val suggestions: List<YouTubeSearchSuggestion> = emptyList(),
)

internal data class AlbumDownloadTrack(
    val position: Int,
    val title: String,
    val durationMillis: Long = 0L,
    val recordingId: String = "",
    val downloadUrl: String = "",
    val source: String = "",
    val mimeType: String = "",
    val downloadAllowed: Boolean = false,
    val matchedSource: YouTubeSource? = null,
    val matchScore: Int = 0,
    val matchReason: String = "",
    val matchVerified: Boolean = false,
    val matchCandidates: List<YouTubeSource> = emptyList(),
)

internal data class AlbumDownloadRelease(
    val id: String,
    val title: String,
    val artist: String,
    val date: String = "",
    val country: String = "",
    val format: String = "",
    val label: String = "",
    val trackCount: Int = 0,
    val tracks: List<AlbumDownloadTrack> = emptyList(),
    val coverUrl: String = "",
    val source: String = "",
    val license: String = "",
    val downloadQuality: String = "",
    val score: Int = 0,
)

internal data class AlbumDownloadDraft(
    val release: AlbumDownloadRelease,
    val savedMillis: Long = System.currentTimeMillis(),
)

internal enum class AlbumAudioDownloadStatus { NeedsProvider, Queued, Downloading, Downloaded, Failed }
internal enum class AlbumProjectTaskPhase { TracklistLoading, Matching, OfflineSaving }

internal data class AlbumAudioDownloadJob(
    val id: String,
    val releaseId: String,
    val title: String,
    val artist: String,
    val provider: String = "",
    val quality: String = "Highest",
    val status: AlbumAudioDownloadStatus = AlbumAudioDownloadStatus.NeedsProvider,
    val progress: Int = 0,
    val message: String = "",
    val startedMillis: Long = System.currentTimeMillis(),
)

internal data class AlbumProjectTaskState(
    val releaseId: String,
    val title: String,
    val artist: String,
    val phase: AlbumProjectTaskPhase,
    val total: Int,
    val completed: Int = 0,
    val activePosition: Int? = null,
    val message: String = "",
    val startedMillis: Long = System.currentTimeMillis(),
)

internal data class AlbumProjectProgressSummary(
    val matched: Int,
    val saving: Int,
    val saved: Int,
    val failed: Int,
    val progress: Int,
)

internal data class AlbumProjectTaskViewState(
    val task: AlbumProjectTaskState,
    val release: AlbumDownloadRelease,
    val summary: AlbumProjectProgressSummary,
)

internal enum class YouTubeSourceKind { Video, Playlist, Channel, Unknown }
internal enum class YouTubeQuality { Standard, High }
internal enum class YouTubeSourceStatus { StreamReady, Cached, Downloaded, ResolverNeeded }
internal enum class YouTubeDownloadState { None, Prompted, Downloading, Downloaded, Failed }
internal enum class YouTubeReviewState { Inbox, Accepted }
internal enum class YouTubeReaction { Neutral, Liked, Disliked }
internal enum class YouTubeLibraryTab { Sources, Playlists, Downloads, Podcasts }
internal enum class YouTubeSmartShelf { Inbox, Recent, Liked, MostPlayed, SavedOffline, Podcasts, NeedsMetadata }
internal enum class SearchScope { Local, YouTube }
internal enum class StreamHeaderMenu { Filter, Sort, More }
internal enum class StreamSourceSort { Recent, MostPlayed, Title }
internal enum class RadioStationFilter { Popular, Saved, Recent, LowData, Quality, Reliable, Name }
internal enum class StreamCollectionKind { Recent, Mix, AlbumLike, Artist, Playlist }
internal enum class YouTubePlaylistOrigin { Manual, SavedMix, YouTubeImport, SpotifyImport }
internal enum class PremiumDeckChartProvider(val label: String) {
    Auto("Auto"),
    AppleMusic("Apple Music"),
    Billboard("Billboard"),
    LastFm("Last.fm"),
}

internal enum class PremiumDeckChartGenre(val label: String, val lastFmTag: String?) {
    Global("Global", null),
    Pop("Pop", "pop"),
    Rnb("R&B", "rnb"),
    HipHop("Hip-Hop", "hip-hop"),
    Afrobeats("Afrobeats", "afrobeats"),
    Jazz("Jazz", "jazz"),
    Rock("Rock", "rock"),
    Electronic("Electronic", "electronic"),
    Latin("Latin", "latin"),
    Country("Country", "country"),
    EastAfrican("East African", "east african"),
}

internal data class PremiumDeckChartEntry(
    val key: String,
    val title: String,
    val artist: String,
    val provider: PremiumDeckChartProvider,
    val genre: PremiumDeckChartGenre,
    val rank: Int,
    val score: Int = 0,
    val releaseDate: String = "",
    val releaseMillis: Long = 0L,
    val artworkUrl: String = "",
    val sourceUrl: String = "",
    val country: String = "",
)

internal data class PremiumDeckChartMatch(
    val entry: PremiumDeckChartEntry,
    val result: YouTubeSearchResult,
)

internal data class StreamPlayHistoryItem(
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val durationMillis: Long,
    val playedAtMillis: Long,
)

internal data class StreamDiscoverySnapshot(
    val savedMillis: Long = 0L,
    val results: List<YouTubeSearchResult> = emptyList(),
)

internal data class FollowedStreamArtist(
    val name: String,
    val key: String = name.cleanStreamArtistName().normalizedSearchText(),
    val followedAtMillis: Long = System.currentTimeMillis(),
    val officialName: String = name,
    val officialKey: String = officialName.cleanStreamArtistName().normalizedSearchText().ifBlank { key },
    val officialChannelUrl: String = "",
    val officialChannelKey: String = officialChannelUrl.youtubeChannelIdentityKey(),
)

internal data class StreamArtistFollowOption(
    val name: String,
    val key: String,
    val sourceCount: Int,
    val lastSeenMillis: Long = 0L,
    val thumbnailUrl: String? = null,
    val officialName: String = name,
    val officialKey: String = officialName.cleanStreamArtistName().normalizedSearchText().ifBlank { key },
    val officialChannelUrl: String = "",
    val officialChannelKey: String = officialChannelUrl.youtubeChannelIdentityKey(),
    val channelVerified: Boolean = false,
)

internal data class StreamNewReleaseSnapshot(
    val savedMillis: Long = 0L,
    val followedArtistKeys: Set<String> = emptySet(),
    val results: List<YouTubeSearchResult> = emptyList(),
)

internal data class StreamReleaseNotification(
    val id: String,
    val artistName: String,
    val artistKey: String,
    val title: String,
    val activityLabel: String,
    val uploadedDate: String,
    val source: YouTubeSource,
    val result: YouTubeSearchResult,
    val read: Boolean = false,
)

internal data class StreamCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: StreamCollectionKind,
    val sources: List<YouTubeSource>,
    val accentColor: Int = 0xFF4A82FF.toInt(),
    val canSave: Boolean = true,
)

internal data class StreamDiscoveryGenre(
    val id: String,
    val title: String,
    val subtitle: String,
    val seedQueries: List<String>,
    val accentColor: Int = 0xFF4A82FF.toInt(),
)

internal fun StreamCollection.isFollowedArtistReleaseCollection(): Boolean =
    id.startsWith("followed-new-releases") ||
        title.normalizedSearchText().contains("artists you follow")

internal fun StreamCollection.isPremiumDeckChartCollection(): Boolean =
    id.startsWith("premium-chart-")

internal enum class PremiumDeckPlaybackOrigin {
    Search,
    NewReleases,
    Artist,
    AlbumProject,
    StreamCollection,
    SearchDiscoveryPreview,
    Playlist,
    ManualSource,
}

internal data class PremiumDeckQueueReason(
    val label: String,
    val detail: String = "",
) {
    val displayText: String
        get() = detail.takeIf { it.isNotBlank() } ?: label
}

internal enum class PremiumDeckStreamPrepareState(val statusText: String?) {
    Idle(null),
    Preparing("Preparing next"),
    Ready("Next ready"),
    Refreshing("Refreshing stream"),
    BackupReady("Backup match ready"),
    Failed(null),
}

internal data class PremiumDeckPlaybackSession(
    val id: String,
    val origin: PremiumDeckPlaybackOrigin,
    val title: String,
    val subtitle: String = "",
    val sourceIds: List<String>,
    val startedSourceId: String,
    val searchQuery: String = "",
    val originKey: String = "",
    val reasonBySourceId: Map<String, PremiumDeckQueueReason> = emptyMap(),
    val startedAtMillis: Long = System.currentTimeMillis(),
) {
    fun reasonFor(sourceId: String): PremiumDeckQueueReason? = reasonBySourceId[sourceId]
}

internal data class StreamRecommendationSection(
    val title: String,
    val subtitle: String,
    val collections: List<StreamCollection>,
)

internal data class StreamBrowseModel(
    val recentSources: List<YouTubeSource> = emptyList(),
    val mixes: List<StreamCollection> = emptyList(),
    val albums: List<StreamCollection> = emptyList(),
    val artists: List<StreamCollection> = emptyList(),
    val filteredSources: List<YouTubeSource> = emptyList(),
)

internal data class YouTubePlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val accentColor: Int = 0xFF4A82FF.toInt(),
    val sourceIds: List<String> = emptyList(),
    val createdMillis: Long = System.currentTimeMillis(),
    val updatedMillis: Long = System.currentTimeMillis(),
    val origin: YouTubePlaylistOrigin = YouTubePlaylistOrigin.Manual,
)

internal data class PremiumDeckPlaylistImportProgress(
    val id: String,
    val provider: String,
    val title: String,
    val phase: String,
    val processed: Int = 0,
    val total: Int = 0,
    val matched: Int = 0,
    val complete: Boolean = false,
)

internal sealed class YouTubeDialogState {
    data class SourceActions(val source: YouTubeSource, val queueIds: List<String> = emptyList()) : YouTubeDialogState()
    data class SourceOfflineSave(val source: YouTubeSource) : YouTubeDialogState()
    data class SourceRename(val source: YouTubeSource) : YouTubeDialogState()
    data class AddSourceToPlaylist(val source: YouTubeSource) : YouTubeDialogState()
    data class PlaylistEditor(val playlist: YouTubePlaylist? = null) : YouTubeDialogState()
    data class PlaylistActions(val playlist: YouTubePlaylist) : YouTubeDialogState()
    data class PlaylistSourcePicker(val playlist: YouTubePlaylist) : YouTubeDialogState()
    data class PlaylistMixEditor(val playlist: YouTubePlaylist) : YouTubeDialogState()
    data class StreamCollectionActions(val collection: StreamCollection) : YouTubeDialogState()
}

internal data class YouTubeChapter(
    val title: String,
    val startMillis: Long,
    val endMillis: Long = 0L,
)

internal data class SponsorBlockSegment(
    val category: String,
    val startMillis: Long,
    val endMillis: Long,
)

internal fun YouTubeSource.streamBrowseFingerprint(): String =
    listOf(
        id,
        url,
        kind.name,
        title,
        author,
        thumbnailUrl.orEmpty(),
        channelTitle,
        channelUrl,
        channelVerified.toString(),
        albumTitleHint,
        albumTrackNumberHint.toString(),
        albumTrackTotalHint.toString(),
        albumYearHint.toString(),
        durationMillis.toString(),
        quality.name,
        playCount.toString(),
        addedMillis.toString(),
        lastPlayedMillis.toString(),
        reaction.name,
        bookmarked.toString(),
        isPodcast.toString(),
        status.name,
        downloadedUri.orEmpty(),
        downloadState.name,
        reviewState.name,
    ).joinToString("|")

internal fun YouTubeSource.premiumDeckCandidateFingerprint(): String =
    listOf(
        id,
        streamDistinctKey(),
        title,
        author,
        albumTitleHint,
        albumTrackNumberHint.toString(),
        albumTrackTotalHint.toString(),
        albumYearHint.toString(),
        durationMillis.toString(),
        quality.name,
        playCount.toString(),
        addedMillis.toString(),
        lastPlayedMillis.toString(),
        reaction.name,
        bookmarked.toString(),
        isPodcast.toString(),
        status.name,
        downloadedUri.orEmpty(),
        downloadState.name,
        reviewState.name,
        url,
    ).joinToString("|")

internal data class ResolverCapabilities(
    val ytDlpAvailable: Boolean = false,
    val ffmpegAvailable: Boolean = false,
    val sponsorBlockAvailable: Boolean = false,
)

internal val YouTubeSourceKind.label: String
    get() = when (this) {
        YouTubeSourceKind.Video -> "Video"
        YouTubeSourceKind.Playlist -> "Playlist"
        YouTubeSourceKind.Channel -> "Channel"
        YouTubeSourceKind.Unknown -> "Source"
    }

internal val StreamCollectionKind.label: String
    get() = when (this) {
        StreamCollectionKind.Recent -> "Recent"
        StreamCollectionKind.Mix -> "Mix"
        StreamCollectionKind.AlbumLike -> "Album"
        StreamCollectionKind.Artist -> "Artist"
        StreamCollectionKind.Playlist -> "Playlist"
    }

internal val YouTubePlaylistOrigin.label: String
    get() = when (this) {
        YouTubePlaylistOrigin.Manual -> "Playlist"
        YouTubePlaylistOrigin.SavedMix -> "Pinned mix"
        YouTubePlaylistOrigin.YouTubeImport -> "YouTube import"
        YouTubePlaylistOrigin.SpotifyImport -> "Spotify import"
    }

internal val YouTubeQuality.label: String
    get() = when (this) {
        YouTubeQuality.Standard -> "128 kbps"
        YouTubeQuality.High -> "256 kbps"
    }

internal val YouTubeSourceStatus.label: String
    get() = when (this) {
        YouTubeSourceStatus.StreamReady -> "cloud"
        YouTubeSourceStatus.Cached -> "cache"
        YouTubeSourceStatus.Downloaded -> "saved"
        YouTubeSourceStatus.ResolverNeeded -> "link"
    }

internal val YouTubeSourceStatus.description: String
    get() = when (this) {
        YouTubeSourceStatus.StreamReady -> "Ready for lightweight stream resolver"
        YouTubeSourceStatus.Cached -> "Temporary smart cache"
        YouTubeSourceStatus.Downloaded -> "Permanent download"
        YouTubeSourceStatus.ResolverNeeded -> "Metadata saved; stream resolver required"
    }

internal val YouTubeDownloadState.label: String
    get() = when (this) {
        YouTubeDownloadState.None -> "cloud"
        YouTubeDownloadState.Prompted -> "cache"
        YouTubeDownloadState.Downloading -> "downloading"
        YouTubeDownloadState.Downloaded -> "saved"
        YouTubeDownloadState.Failed -> "failed"
    }

internal val YouTubeLibraryTab.label: String
    get() = when (this) {
        YouTubeLibraryTab.Sources -> "Sources"
        YouTubeLibraryTab.Playlists -> "Playlists"
        YouTubeLibraryTab.Downloads -> "Offline Saved"
        YouTubeLibraryTab.Podcasts -> "Podcasts"
    }

internal val YouTubeSmartShelf.label: String
    get() = when (this) {
        YouTubeSmartShelf.Inbox -> "Inbox"
        YouTubeSmartShelf.Recent -> "Recent"
        YouTubeSmartShelf.Liked -> "Liked"
        YouTubeSmartShelf.MostPlayed -> "Most Played"
        YouTubeSmartShelf.SavedOffline -> "Offline Saved"
        YouTubeSmartShelf.Podcasts -> "Podcasts"
        YouTubeSmartShelf.NeedsMetadata -> "Needs Metadata"
    }

internal val YouTubeSmartShelf.icon: DeckIcon
    get() = when (this) {
        YouTubeSmartShelf.Inbox -> DeckIcon.StreamSource
        YouTubeSmartShelf.Recent -> DeckIcon.StreamRecent
        YouTubeSmartShelf.Liked -> DeckIcon.StreamLike
        YouTubeSmartShelf.MostPlayed -> DeckIcon.MusicList
        YouTubeSmartShelf.SavedOffline -> DeckIcon.StreamOffline
        YouTubeSmartShelf.Podcasts -> DeckIcon.StreamRadio
        YouTubeSmartShelf.NeedsMetadata -> DeckIcon.StreamEdit
    }

internal val SearchScope.label: String
    get() = when (this) {
        SearchScope.Local -> "Local"
        SearchScope.YouTube -> PREMIUMDECK_SOURCE_NAME
    }

internal val StreamSourceSort.label: String
    get() = when (this) {
        StreamSourceSort.Recent -> "Recent activity"
        StreamSourceSort.MostPlayed -> "Most played"
        StreamSourceSort.Title -> "Title A-Z"
    }

internal val StreamSourceSort.subtitle: String
    get() = when (this) {
        StreamSourceSort.Recent -> "Latest plays and saves first"
        StreamSourceSort.MostPlayed -> "Highest replay count first"
        StreamSourceSort.Title -> "Clean alphabetical source order"
    }

internal fun sortStreamSources(sources: List<YouTubeSource>, sort: StreamSourceSort): List<YouTubeSource> =
    when (sort) {
        StreamSourceSort.Recent -> sources.sortedWith(
            compareByDescending<YouTubeSource> { max(it.lastPlayedMillis, it.addedMillis) }
                .thenByDescending { it.playCount }
                .thenBy { it.title.normalizedSearchText() },
        )
        StreamSourceSort.MostPlayed -> sources.sortedWith(
            compareByDescending<YouTubeSource> { it.playCount }
                .thenByDescending { max(it.lastPlayedMillis, it.addedMillis) }
                .thenBy { it.title.normalizedSearchText() },
        )
        StreamSourceSort.Title -> sources.sortedWith(
            compareBy<YouTubeSource> { it.title.normalizedSearchText() }
                .thenBy { it.author.normalizedSearchText() },
        )
    }

internal data class YouTubeDetection(
    val normalizedUrl: String,
    val kind: YouTubeSourceKind,
    val sourceId: String,
)

