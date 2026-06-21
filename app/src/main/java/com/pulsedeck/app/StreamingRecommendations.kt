package com.pulsedeck.app

import android.net.Uri
import com.pulsedeck.app.premiumdeck.personalization.UserPreferenceProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val PREMIUM_DECK_CHART_TAG_PREFIX = "premiumdeck-chart:"
private const val PREMIUM_DECK_YOUTUBE_EXPLORE_TAG_PREFIX = "premiumdeck-youtube-explore:"
// Public export intentionally leaves these endpoints disconnected.
private const val PREMIUM_DECK_PUBLIC_CHARTS_URL = ""
private const val PREMIUM_DECK_HOSTED_CHARTS_FALLBACK_URL = ""
private const val PREMIUM_DECK_BILLBOARD_JSON_BASE_URL = ""
private const val PREMIUM_DECK_HOSTED_CHART_CACHE_TTL_MILLIS = 30L * 60L * 1000L
private const val APPLE_MUSIC_CHART_LIMIT = 50
private const val PREMIUM_DECK_CHART_RESOLVE_LIMIT = 18
internal const val PREMIUM_DECK_CHART_SAFE_LIMIT = 25
internal const val PREMIUM_DECK_CHART_PAGE_SIZE = 25
internal const val PREMIUM_DECK_CHART_SESSION_LIMIT = 100
private const val YOUTUBE_MUSIC_EXPLORE_CHANNEL_URL = "https://www.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ"
private const val YOUTUBE_EXPLORE_COLLECTION_LIMIT = 10
private const val YOUTUBE_EXPLORE_PLAYLIST_FETCH_LIMIT = 8
private const val YOUTUBE_EXPLORE_TRACK_LIMIT = 18
private const val YOUTUBE_PLAYLIST_IMPORT_LIMIT = 100
private const val YOUTUBE_EXPLORE_SNAPSHOT_RESULT_LIMIT = 140
private const val SPOTIFY_PLAYLIST_IMPORT_LIMIT = 50
private const val SPOTIFY_PLAYLIST_SEARCH_QUERY_LIMIT = 2
private const val SPOTIFY_PLAYLIST_SEARCH_TIMEOUT_MILLIS = 5200L
private const val SEARCH_DISCOVERY_GENRE_PREVIEW_LIMIT = 18
private const val SEARCH_DISCOVERY_GENRE_QUERY_LIMIT = 2
private const val SEARCH_PICK_RELATED_SAME_ARTIST_CAP = 1
private const val SEARCH_PICK_RELATED_LONG_TRACK_LIMIT_MILLIS = 12L * 60L * 1000L
private val YOUTUBE_PLAYLIST_INVIDIOUS_ENDPOINTS = emptyList<String>()

internal object PremiumDeckChartRuntime {
    @Volatile
    var lastFmApiKey: String = ""
}

@Volatile private var premiumDeckHostedChartCache: Pair<Long, JSONObject>? = null

private val premiumDeckBillboardChartSlugs = mapOf(
    PremiumDeckChartGenre.Global to "billboard-hot-100",
    PremiumDeckChartGenre.Pop to "adult-pop-songs",
    PremiumDeckChartGenre.Rnb to "r-and-b-streaming-songs",
    PremiumDeckChartGenre.HipHop to "rap-song",
    PremiumDeckChartGenre.Afrobeats to "billboard-u-s-afrobeats-songs",
    PremiumDeckChartGenre.Jazz to "jazz-songs",
    PremiumDeckChartGenre.Rock to "hot-rock-songs",
    PremiumDeckChartGenre.Electronic to "hot-dance-pop-songs",
    PremiumDeckChartGenre.Latin to "latin-songs",
    PremiumDeckChartGenre.Country to "country-songs",
)

private val premiumDeckChartCountries = listOf("us", "gb", "ca", "au", "ng", "za", "in", "jp", "kr", "br", "mx", "de", "fr", "ph")
private val youtubeMusicExploreShelfPriority = listOf(
    "New & Trending Songs",
    "Artist On The Rise Trending",
    "Today's Biggest Hits",
)

private data class PremiumDeckChartTrack(
    val key: String,
    val title: String,
    val artist: String,
    val releaseDate: String,
    val releaseMillis: Long,
    val artworkUrl: String,
    val appleUrl: String,
    val country: String,
    val rank: Int,
)

private data class RankedPremiumDeckChartTrack(
    val key: String,
    val title: String,
    val artist: String,
    val releaseDate: String,
    val releaseMillis: Long,
    val artworkUrl: String,
    val appleUrl: String,
    val countries: Set<String>,
    val bestRank: Int,
    val score: Int,
)

private data class PremiumDeckChartCollectionSpec(
    val id: String,
    val title: String,
    val subtitle: String,
)

private data class YouTubeExploreShelf(
    val title: String,
    val rank: Int,
    val cards: List<YouTubeExploreCard>,
)

private data class YouTubeExploreCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val contentId: String,
    val playlistId: String,
    val videoId: String,
    val thumbnailUrl: String?,
    val shelfTitle: String,
    val shelfRank: Int,
    val cardRank: Int,
)

private data class YouTubeExploreTag(
    val id: String,
    val title: String,
    val subtitle: String,
    val shelfTitle: String,
    val coverUrl: String,
)

private data class SearchDiscoveryPlaylistCard(
    val card: YouTubeExploreCard,
    val score: Int,
)

internal val premiumDeckSearchDiscoveryGenres: List<StreamDiscoveryGenre> = listOf(
    StreamDiscoveryGenre(
        id = "beast-mode-hip-hop",
        title = "Beast Mode Hip-Hop",
        subtitle = "Hard-hitting tracks with aggressive rap energy",
        seedQueries = listOf("beast mode hip hop official audio", "youtube music beast mode hip hop playlist"),
        accentColor = accentForKey("beast mode hip hop"),
    ),
    StreamDiscoveryGenre(
        id = "pump-up-pop",
        title = "Pump-Up Pop",
        subtitle = "Big pop hooks with high-energy momentum",
        seedQueries = listOf("pump up pop official audio", "youtube music pump up pop playlist"),
        accentColor = accentForKey("pump up pop"),
    ),
    StreamDiscoveryGenre(
        id = "country-tailgate",
        title = "Country Tailgate",
        subtitle = "Country tracks built for a loud weekend drive",
        seedQueries = listOf("country tailgate official audio", "youtube music country tailgate playlist"),
        accentColor = accentForKey("country tailgate"),
    ),
    StreamDiscoveryGenre(
        id = "lofi-loft",
        title = "Lofi Loft",
        subtitle = "Chillhop, lofi beats, and soft-focus grooves",
        seedQueries = listOf("lofi loft chillhop beats", "lofi beats official audio"),
        accentColor = accentForKey("lofi loft"),
    ),
    StreamDiscoveryGenre(
        id = "cuffin-season",
        title = "Cuffin' Season",
        subtitle = "Smooth R&B and warm late-night vocals",
        seedQueries = listOf("cuffin season r&b official audio", "smooth r&b new songs official audio"),
        accentColor = accentForKey("cuffin season"),
    ),
    StreamDiscoveryGenre(
        id = "relaxing-evening",
        title = "Relaxing Evening",
        subtitle = "Pop, soul, indie, jazz, and easy-listening cuts",
        seedQueries = listOf("relaxing evening music official audio", "chill evening pop soul indie official audio"),
        accentColor = accentForKey("relaxing evening"),
    ),
    StreamDiscoveryGenre(
        id = "low-key",
        title = "Low Key",
        subtitle = "Chill rhythmic pop and understated R&B",
        seedQueries = listOf("low key r&b pop official audio", "chill rhythmic pop r&b official audio"),
        accentColor = accentForKey("low key r&b"),
    ),
    StreamDiscoveryGenre(
        id = "rnb-party",
        title = "R&B Party",
        subtitle = "Always-updated floor-fillers and smooth club cuts",
        seedQueries = listOf("r&b party official audio", "r&b floor fillers official audio"),
        accentColor = accentForKey("r&b party"),
    ),
    StreamDiscoveryGenre(
        id = "take-it-easy-rock",
        title = "Take It Easy Rock",
        subtitle = "Classic rock, alternative, and easygoing guitars",
        seedQueries = listOf("take it easy rock official audio", "easygoing rock alternative official audio"),
        accentColor = accentForKey("take it easy rock"),
    ),
    StreamDiscoveryGenre(
        id = "dance-pop-bangers",
        title = "Dance Pop Bangers",
        subtitle = "Main-stage dance pop and festival-ready hooks",
        seedQueries = listOf("dance pop bangers official audio", "dance pop official audio new hits"),
        accentColor = accentForKey("dance pop bangers"),
    ),
)
internal fun buildStreamArtistFollowOptions(
    sources: List<YouTubeSource>,
    discoveryResults: List<YouTubeSearchResult>,
    playHistory: List<StreamPlayHistoryItem>,
    followedArtists: List<FollowedStreamArtist>,
): List<StreamArtistFollowOption> {
    data class ArtistAccumulator(
        var name: String,
        var sourceCount: Int = 0,
        var score: Int = 0,
        var lastSeenMillis: Long = 0L,
        var thumbnailUrl: String? = null,
        var officialName: String = name,
        var officialKey: String = name.normalizedSearchText(),
        var officialChannelUrl: String = "",
        var officialChannelKey: String = "",
        var channelVerified: Boolean = false,
    )

    val artists = linkedMapOf<String, ArtistAccumulator>()
    fun addArtist(
        rawName: String,
        sourceCount: Int,
        score: Int,
        lastSeenMillis: Long = 0L,
        thumbnailUrl: String? = null,
        channelTitle: String = rawName,
        channelUrl: String = "",
        channelVerified: Boolean = false,
    ) {
        val name = rawName.cleanStreamArtistName()
        val key = name.normalizedSearchText()
        if (key.isBlank() || key in genericStreamAlbumArtistKeys) return
        val current = artists.getOrPut(key) { ArtistAccumulator(name = name) }
        if (name.length < current.name.length || current.name.isBlank()) current.name = name
        current.sourceCount += sourceCount
        current.score += score
        current.lastSeenMillis = max(current.lastSeenMillis, lastSeenMillis)
        if (current.thumbnailUrl.isNullOrBlank() && !thumbnailUrl.isNullOrBlank()) current.thumbnailUrl = thumbnailUrl
        val channelKey = channelUrl.youtubeChannelIdentityKey()
        if ((current.officialChannelKey.isBlank() && channelKey.isNotBlank()) || channelVerified) {
            current.officialChannelUrl = channelUrl
            current.officialChannelKey = channelKey
            current.channelVerified = channelVerified
        }
        if (isOfficialStreamArtistName(channelTitle, key) || isOfficialStreamArtistName(rawName, key) || channelVerified) {
            current.officialName = name
            current.officialKey = key
        }
    }

    followedArtists.forEach { artist ->
        addArtist(artist.name, sourceCount = 0, score = 10_000, lastSeenMillis = artist.followedAtMillis)
        artists[artist.key]?.let {
            it.officialName = artist.officialName
            it.officialKey = artist.officialKey
            it.officialChannelUrl = artist.officialChannelUrl
            it.officialChannelKey = artist.officialChannelKey
        }
    }
    sources
        .filter { it.kind == YouTubeSourceKind.Video && it.reviewState == YouTubeReviewState.Accepted && it.reaction != YouTubeReaction.Disliked }
        .forEach { source ->
            val affinity = 1 + source.playCount +
                (if (source.reaction == YouTubeReaction.Liked) 5 else 0) +
                (if (source.bookmarked) 3 else 0)
            addArtist(
                rawName = source.author,
                sourceCount = 1,
                score = affinity,
                lastSeenMillis = max(source.lastPlayedMillis, source.addedMillis),
                thumbnailUrl = source.thumbnailUrl,
                channelTitle = source.channelTitle,
                channelUrl = source.channelUrl,
                channelVerified = source.channelVerified,
            )
        }
    discoveryResults.forEach { result ->
        addArtist(
            rawName = result.source.author,
            sourceCount = 1,
            score = max(1, result.score / 12),
            lastSeenMillis = result.cachedMillis,
            thumbnailUrl = result.source.thumbnailUrl,
            channelTitle = result.source.channelTitle,
            channelUrl = result.source.channelUrl,
            channelVerified = result.source.channelVerified,
        )
    }
    playHistory.forEach { item ->
        addArtist(item.author, sourceCount = 0, score = 2, lastSeenMillis = item.playedAtMillis)
    }

    val followedKeys = followedArtists.map { it.key }.toSet()
    return artists
        .map { (key, value) ->
            StreamArtistFollowOption(
                name = value.name,
                key = key,
                sourceCount = value.sourceCount,
                lastSeenMillis = value.lastSeenMillis,
                thumbnailUrl = value.thumbnailUrl,
                officialName = value.officialName,
                officialKey = value.officialKey,
                officialChannelUrl = value.officialChannelUrl,
                officialChannelKey = value.officialChannelKey,
                channelVerified = value.channelVerified,
            ) to value.score
        }
        .sortedWith(
            compareByDescending<Pair<StreamArtistFollowOption, Int>> { if (it.first.key in followedKeys) 1 else 0 }
                .thenByDescending { it.second }
                .thenByDescending { it.first.sourceCount }
                .thenByDescending { it.first.lastSeenMillis }
                .thenBy { it.first.name.lowercase(Locale.US) },
        )
        .map { it.first }
        .take(60)
}

internal suspend fun fetchStreamDiscoverySnapshot(
    sources: List<YouTubeSource>,
    history: List<StreamPlayHistoryItem>,
    followedArtists: List<FollowedStreamArtist> = emptyList(),
): StreamDiscoverySnapshot {
    val youtubeExploreSnapshot = fetchYouTubeMusicExploreSnapshot(sources)
    if (youtubeExploreSnapshot.results.isNotEmpty()) return youtubeExploreSnapshot

    val chartSnapshot = fetchPremiumDeckExternalChartSnapshot(sources)
    if (chartSnapshot.results.isNotEmpty()) return chartSnapshot

    val seeds = streamDiscoverySeeds(sources, history, followedArtists)
    if (seeds.isEmpty()) return StreamDiscoverySnapshot(savedMillis = System.currentTimeMillis())
    val localKeys = sources.map { it.streamDistinctKey() }.toSet()
    val dislikedArtists = sources.filter { it.reaction == YouTubeReaction.Disliked }.map { it.artistKey() }.toSet()
    val results = buildList {
        for (seed in seeds.take(8)) {
            val response = withTimeoutOrNull(5200L) { searchYouTubeVideos(seed) } ?: YouTubeSearchResponse()
            response.results
                .filter { result -> result.source.streamDistinctKey() !in localKeys }
                .filterNot { result -> result.source.artistKey() in dislikedArtists }
                .take(10)
                .forEach(::add)
        }
    }
        .sortedByDescending { it.score }
        .distinctBy { it.videoId.ifBlank { it.source.streamDistinctKey() } }
        .take(80)
    return StreamDiscoverySnapshot(savedMillis = System.currentTimeMillis(), results = results)
}

private suspend fun fetchYouTubeMusicExploreSnapshot(savedSources: List<YouTubeSource>): StreamDiscoverySnapshot =
    coroutineScope {
        if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) return@coroutineScope StreamDiscoverySnapshot()
        val html = withTimeoutOrNull(7800L) {
            httpText(YOUTUBE_MUSIC_EXPLORE_CHANNEL_URL, connectTimeout = 2200, readTimeout = 6400)
        } ?: return@coroutineScope StreamDiscoverySnapshot()
        val shelves = parseYouTubeMusicExploreShelves(html)
        if (shelves.isEmpty()) return@coroutineScope StreamDiscoverySnapshot()

        val now = System.currentTimeMillis()
        val results = mutableListOf<YouTubeSearchResult>()
        var playlistFetches = 0
        var collectionRank = 0

        for (shelf in shelves) {
            val playlistCards = shelf.cards.filter { it.playlistId.isNotBlank() }
            for (card in playlistCards) {
                if (collectionRank >= YOUTUBE_EXPLORE_COLLECTION_LIMIT || playlistFetches >= YOUTUBE_EXPLORE_PLAYLIST_FETCH_LIMIT) break
                val tag = youtubeExploreStorageTag(
                    id = card.id,
                    title = card.title,
                    subtitle = card.subtitle.ifBlank { "From ${card.shelfTitle}" },
                    shelfTitle = card.shelfTitle,
                    coverUrl = card.thumbnailUrl.orEmpty(),
                )
                val playlistSources = fetchYouTubeExplorePlaylistSources(card, savedSources, tag)
                if (playlistSources.isNotEmpty()) {
                    playlistSources.forEachIndexed { index, source ->
                        results += YouTubeSearchResult(
                            source = source,
                            durationMillis = source.durationMillis,
                            uploadedDate = "",
                            views = (10_000 - collectionRank * 500 - index).toLong(),
                            videoId = detectYouTubeSource(source.url)?.sourceId.orEmpty().ifBlank { source.streamDistinctKey() },
                            score = (12_000 - collectionRank * 700 - index).coerceAtLeast(1),
                            cachedMillis = now,
                            matchReason = "${card.shelfTitle} / ${card.title}  |  YouTube Music Explore",
                        )
                    }
                    collectionRank += 1
                }
                playlistFetches += 1
                kotlinx.coroutines.delay(90L)
            }

            if (collectionRank >= YOUTUBE_EXPLORE_COLLECTION_LIMIT) break
            val videoCards = shelf.cards.filter { it.videoId.isNotBlank() && it.playlistId.isBlank() }
            if (videoCards.isNotEmpty()) {
                val collectionId = youtubeExploreStableId("videos-${shelf.title}", shelf.title)
                val tag = youtubeExploreStorageTag(
                    id = collectionId,
                    title = shelf.title,
                    subtitle = "Featured videos from YouTube Music Explore",
                    shelfTitle = shelf.title,
                    coverUrl = videoCards.firstNotNullOfOrNull { it.thumbnailUrl }.orEmpty(),
                )
                val sources = videoCards
                    .mapIndexedNotNull { index, card ->
                        card.toYouTubeExploreVideoSource(
                            savedSources = savedSources,
                            tag = tag,
                            rank = index + 1,
                            total = videoCards.size,
                        )
                    }
                    .distinctBy { it.streamDistinctKey() }
                    .take(YOUTUBE_EXPLORE_TRACK_LIMIT)
                if (sources.isNotEmpty()) {
                    sources.forEachIndexed { index, source ->
                        results += YouTubeSearchResult(
                            source = source,
                            durationMillis = source.durationMillis,
                            uploadedDate = "",
                            views = (10_000 - collectionRank * 500 - index).toLong(),
                            videoId = detectYouTubeSource(source.url)?.sourceId.orEmpty().ifBlank { source.streamDistinctKey() },
                            score = (12_000 - collectionRank * 700 - index).coerceAtLeast(1),
                            cachedMillis = now,
                            matchReason = "${shelf.title}  |  YouTube Music Explore",
                        )
                    }
                    collectionRank += 1
                }
            }
            if (collectionRank >= YOUTUBE_EXPLORE_COLLECTION_LIMIT) break
        }

        StreamDiscoverySnapshot(
            savedMillis = now,
            results = results
                .distinctBy { "${it.source.youtubeExploreTag()?.id.orEmpty()}|${it.videoId.ifBlank { it.source.streamDistinctKey() }}" }
                .take(YOUTUBE_EXPLORE_SNAPSHOT_RESULT_LIMIT),
        )
    }

private suspend fun fetchYouTubeExplorePlaylistSources(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    limit: Int = YOUTUBE_EXPLORE_TRACK_LIMIT,
    preferredUrl: String = "",
    publicApiFallback: Boolean = false,
): List<YouTubeSource> {
    val playlistId = card.playlistId.trim()
    if (playlistId.isBlank()) return emptyList()
    val encodedId = Uri.encode(playlistId)
    val urls = (listOf(preferredUrl.trim()) + listOf(
        "https://www.youtube.com/playlist?list=$encodedId",
        "https://music.youtube.com/playlist?list=$encodedId",
    ))
        .mapNotNull { url -> url.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) } }
        .distinct()
    for (url in urls) {
        val html = withTimeoutOrNull(8200L) { httpText(url, connectTimeout = 2600, readTimeout = 7200) } ?: continue
        val root = extractYouTubeInitialData(html) ?: continue
        val sources = root.extractYouTubePlaylistSources(
            card = card,
            savedSources = savedSources,
            tag = tag,
            limit = limit,
        )
        if (sources.isNotEmpty()) return sources.withPlaylistTrackNumbers()
    }
    if (publicApiFallback) {
        val publicApiSources = fetchYouTubePublicPlaylistSources(
            playlistId = playlistId,
            card = card,
            savedSources = savedSources,
            tag = tag,
            limit = limit,
        )
        if (publicApiSources.isNotEmpty()) return publicApiSources.withPlaylistTrackNumbers()
    }
    return emptyList()
}

private suspend fun fetchYouTubePublicPlaylistSources(
    playlistId: String,
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    limit: Int,
): List<YouTubeSource> {
    val pipedSources = fetchYouTubePipedPlaylistSources(playlistId, card, savedSources, tag, limit)
    if (pipedSources.isNotEmpty()) return pipedSources
    val invidiousPlaylistSources = fetchYouTubeInvidiousPlaylistSources(playlistId, card, savedSources, tag, limit)
    if (invidiousPlaylistSources.isNotEmpty()) return invidiousPlaylistSources
    return fetchYouTubeInvidiousMixSources(playlistId, card, savedSources, tag, limit)
}

private suspend fun fetchYouTubePipedPlaylistSources(
    playlistId: String,
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    limit: Int,
): List<YouTubeSource> {
    val safeLimit = limit.coerceAtLeast(1)
    val encodedId = Uri.encode(playlistId)
    for (endpoint in activePipedApiEndpoints().take(8)) {
        val base = endpoint.trimEnd('/')
        val collected = mutableListOf<YouTubeSource>()
        var page = withTimeoutOrNull(7200L) {
            httpJson("$base/playlists/$encodedId", connectTimeout = 1800, readTimeout = 5600)
        }
        var pageCount = 0
        while (page != null && pageCount < 5 && collected.size < safeLimit) {
            val offset = collected.size
            collected += page.toPipedPlaylistVideoSources(
                card = card,
                savedSources = savedSources,
                tag = tag,
                startRank = offset + 1,
                limit = safeLimit - offset,
            )
            val nextPage = page.optString("nextpage").trim()
            if (nextPage.isBlank() || collected.distinctBy { it.streamDistinctKey() }.size >= safeLimit) break
            val encodedNext = URLEncoder.encode(nextPage, "UTF-8")
            page = withTimeoutOrNull(7200L) {
                httpJson("$base/nextpage/playlists/$encodedId?nextpage=$encodedNext", connectTimeout = 1800, readTimeout = 5600)
            }
            pageCount += 1
        }
        val result = collected.distinctBy { it.streamDistinctKey() }.take(safeLimit)
        if (result.isNotEmpty()) return result
    }
    return emptyList()
}

private suspend fun fetchYouTubeInvidiousPlaylistSources(
    playlistId: String,
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    limit: Int,
): List<YouTubeSource> {
    val safeLimit = limit.coerceAtLeast(1)
    val encodedId = Uri.encode(playlistId)
    for (endpoint in YOUTUBE_PLAYLIST_INVIDIOUS_ENDPOINTS) {
        val base = endpoint.trimEnd('/')
        val collected = mutableListOf<YouTubeSource>()
        var pageNumber = 1
        while (pageNumber <= 5 && collected.size < safeLimit) {
            val page = withTimeoutOrNull(7200L) {
                httpJson("$base/api/v1/playlists/$encodedId?page=$pageNumber", connectTimeout = 1800, readTimeout = 5600)
            } ?: break
            val offset = collected.size
            val pageSources = page.toInvidiousPlaylistVideoSources(
                card = card,
                savedSources = savedSources,
                tag = tag,
                startRank = offset + 1,
                limit = safeLimit - offset,
            )
            if (pageSources.isEmpty()) break
            collected += pageSources
            pageNumber += 1
        }
        val result = collected.distinctBy { it.streamDistinctKey() }.take(safeLimit)
        if (result.isNotEmpty()) return result
    }
    return emptyList()
}

private suspend fun fetchYouTubeInvidiousMixSources(
    playlistId: String,
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    limit: Int,
): List<YouTubeSource> {
    val safeLimit = limit.coerceAtLeast(1)
    val encodedId = Uri.encode(playlistId)
    for (endpoint in YOUTUBE_PLAYLIST_INVIDIOUS_ENDPOINTS) {
        val base = endpoint.trimEnd('/')
        val page = withTimeoutOrNull(7200L) {
            httpJson("$base/api/v1/mixes/$encodedId", connectTimeout = 1800, readTimeout = 5600)
        } ?: continue
        val result = page.toInvidiousPlaylistVideoSources(
            card = card,
            savedSources = savedSources,
            tag = tag,
            startRank = 1,
            limit = safeLimit,
        )
            .distinctBy { it.streamDistinctKey() }
            .take(safeLimit)
        if (result.isNotEmpty()) return result
    }
    return emptyList()
}

private fun List<YouTubeSource>.withPlaylistTrackNumbers(): List<YouTubeSource> {
    val total = size
    return mapIndexed { index, source -> source.copy(albumTrackNumberHint = index + 1, albumTrackTotalHint = total) }
}

private fun JSONObject.extractYouTubePlaylistSources(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    limit: Int,
): List<YouTubeSource> {
    val safeLimit = limit.coerceAtLeast(1)
    val playlistSources = collectNestedObjects("playlistVideoRenderer", limit = safeLimit * 2)
        .mapIndexedNotNull { index, renderer -> renderer.toYouTubeExplorePlaylistVideoSource(card, savedSources, tag, index + 1) }
    val panelSources = collectNestedObjects("playlistPanelVideoRenderer", limit = safeLimit * 2)
        .mapIndexedNotNull { index, renderer -> renderer.toYouTubePlaylistPanelVideoSource(card, savedSources, tag, playlistSources.size + index + 1) }
    val musicSources = collectNestedObjects("musicResponsiveListItemRenderer", limit = safeLimit * 2)
        .mapIndexedNotNull { index, renderer -> renderer.toYouTubeMusicPlaylistItemSource(card, savedSources, tag, playlistSources.size + panelSources.size + index + 1) }
    val lockupSources = collectNestedObjects("lockupViewModel", limit = safeLimit * 3)
        .mapIndexedNotNull { index, renderer -> renderer.toYouTubeExploreLockupVideoSource(card, savedSources, tag, playlistSources.size + panelSources.size + musicSources.size + index + 1) }
    val compactSources = collectNestedObjects("compactVideoRenderer", limit = safeLimit * 2)
        .mapIndexedNotNull { index, renderer -> renderer.toYouTubeCompactVideoSource(card, savedSources, tag, playlistSources.size + panelSources.size + musicSources.size + lockupSources.size + index + 1) }
    return (playlistSources + panelSources + musicSources + lockupSources + compactSources)
        .distinctBy { it.streamDistinctKey() }
        .take(safeLimit)
}

private fun JSONObject.toPipedPlaylistVideoSources(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    startRank: Int,
    limit: Int,
): List<YouTubeSource> {
    val streams = optJSONArray("relatedStreams")
        ?: optJSONArray("videos")
        ?: optJSONArray("items")
        ?: return emptyList()
    return buildList {
        for (index in 0 until streams.length()) {
            if (size >= limit) break
            val item = streams.optJSONObject(index) ?: continue
            item.toPipedPlaylistVideoSource(
                card = card,
                savedSources = savedSources,
                tag = tag,
                rank = startRank + index,
            )?.let(::add)
        }
    }
}

private fun JSONObject.toPipedPlaylistVideoSource(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    rank: Int,
): YouTubeSource? {
    val videoId = optString("videoId").trim()
        .ifBlank { youtubeApiVideoIdFromUrl(optString("url")) }
        .takeIf { it.isNotBlank() }
        ?: return null
    val rawTitle = optString("title").trim()
    if (rawTitle.isBlank() || rawTitle.equals("Deleted video", ignoreCase = true) || rawTitle.equals("Private video", ignoreCase = true)) return null
    val rawAuthor = optString("uploaderName")
        .ifBlank { optString("uploader") }
        .ifBlank { optString("author") }
        .ifBlank { card.subtitle }
    val duration = optLong("duration", 0L).coerceAtLeast(0L) * 1000L
    return buildYouTubeExploreVideoSource(
        videoId = videoId,
        rawTitle = rawTitle,
        rawAuthor = rawAuthor,
        thumbnailUrl = normalizeYouTubeThumbnailUrl(
            optString("thumbnail").ifBlank { optString("thumbnailUrl") },
        ) ?: youtubeThumbnailUrlForVideoId(videoId),
        durationMillis = duration,
        savedSources = savedSources,
        tag = tag,
        rank = rank,
        total = 0,
    )
}

private fun JSONObject.toInvidiousPlaylistVideoSources(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    startRank: Int,
    limit: Int,
): List<YouTubeSource> {
    val defaultAuthor = optString("author").ifBlank { card.subtitle }
    val videos = optJSONArray("videos")
        ?: optJSONArray("items")
        ?: return emptyList()
    return buildList {
        for (index in 0 until videos.length()) {
            if (size >= limit) break
            val item = videos.optJSONObject(index) ?: continue
            item.toInvidiousPlaylistVideoSource(
                card = card,
                savedSources = savedSources,
                tag = tag,
                defaultAuthor = defaultAuthor,
                rank = item.optInt("index", 0).takeIf { it > 0 } ?: (startRank + index),
            )?.let(::add)
        }
    }
}

private fun JSONObject.toInvidiousPlaylistVideoSource(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    defaultAuthor: String,
    rank: Int,
): YouTubeSource? {
    val videoId = optString("videoId").trim()
        .ifBlank { youtubeApiVideoIdFromUrl(optString("url")) }
        .takeIf { it.isNotBlank() }
        ?: return null
    val rawTitle = optString("title").trim()
    if (rawTitle.isBlank() || rawTitle.equals("Deleted video", ignoreCase = true) || rawTitle.equals("Private video", ignoreCase = true)) return null
    val rawAuthor = optString("author")
        .ifBlank { optString("uploader") }
        .ifBlank { defaultAuthor }
    val duration = optLong("lengthSeconds", 0L).coerceAtLeast(0L) * 1000L
    return buildYouTubeExploreVideoSource(
        videoId = videoId,
        rawTitle = rawTitle,
        rawAuthor = rawAuthor.ifBlank { card.subtitle },
        thumbnailUrl = optJSONArray("videoThumbnails")?.bestPlaylistThumbnailUrl()
            ?: normalizeYouTubeThumbnailUrl(optString("thumbnail").ifBlank { optString("thumbnailUrl") })
            ?: youtubeThumbnailUrlForVideoId(videoId),
        durationMillis = duration,
        savedSources = savedSources,
        tag = tag,
        rank = rank,
        total = 0,
    )
}

private fun youtubeApiVideoIdFromUrl(rawUrl: String): String {
    val normalizedUrl = normalizeYouTubeSearchUrl(rawUrl) ?: rawUrl.trim()
    detectYouTubeSource(normalizedUrl)
        ?.takeIf { it.kind == YouTubeSourceKind.Video }
        ?.sourceId
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    Regex("""(?:^|[?&])v=([^&#/]+)""")
        .find(rawUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    Regex("""/(?:shorts|live)/([^/?#&]+)""", RegexOption.IGNORE_CASE)
        .find(rawUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return ""
}

private fun JSONArray.bestPlaylistThumbnailUrl(): String? {
    data class ThumbnailCandidate(val url: String, val width: Int, val height: Int)

    val candidates = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val url = normalizeYouTubeThumbnailUrl(item.optString("url")) ?: continue
            add(
                ThumbnailCandidate(
                    url = url,
                    width = item.optInt("width", 0),
                    height = item.optInt("height", 0),
                ),
            )
        }
    }
    return candidates
        .maxByOrNull { it.width.coerceAtLeast(1) * it.height.coerceAtLeast(1) }
        ?.url
}

internal data class YouTubePlaylistImportResult(
    val playlistTitle: String,
    val playlistUrl: String,
    val trackCount: Int,
    val sources: List<YouTubeSource>,
)

internal suspend fun fetchPremiumDeckPlaylistLinkSources(
    playlistId: String,
    title: String,
    playlistUrl: String = "",
    savedSources: List<YouTubeSource>,
    progress: (PremiumDeckPlaylistImportProgress) -> Unit = {},
): YouTubePlaylistImportResult {
    val cleanId = playlistId.trim()
    val progressId = "youtube-import-${System.currentTimeMillis()}"
    val cleanTitle = title.trim().ifBlank { "YouTube Playlist" }
    val cleanUrl = playlistUrl.trim()
    val canonicalUrl = if (cleanId.isBlank()) "" else "https://www.youtube.com/playlist?list=${Uri.encode(cleanId)}"
    progress(
        PremiumDeckPlaylistImportProgress(
            id = progressId,
            provider = "YouTube",
            title = cleanTitle,
            phase = "Reading playlist",
        ),
    )
    if (cleanId.isBlank() || PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
        return YouTubePlaylistImportResult(cleanTitle, canonicalUrl, 0, emptyList())
    }
    val card = YouTubeExploreCard(
        id = youtubeExploreStableId("playlist-import", cleanTitle, cleanId),
        title = cleanTitle,
        subtitle = "Imported playlist",
        contentId = cleanId,
        playlistId = cleanId,
        videoId = "",
        thumbnailUrl = null,
        shelfTitle = "Playlist Import",
        shelfRank = 0,
        cardRank = 0,
    )
    progress(
        PremiumDeckPlaylistImportProgress(
            id = progressId,
            provider = "YouTube",
            title = cleanTitle,
            phase = "Loading tracks",
        ),
    )
    val tag = youtubeExploreStorageTag(
        id = card.id,
        title = cleanTitle,
        subtitle = "Imported from YouTube playlist link",
        shelfTitle = "Playlist Import",
        coverUrl = "",
    )
    val sources = fetchYouTubeExplorePlaylistSources(
        card = card,
        savedSources = savedSources,
        tag = tag,
        limit = YOUTUBE_PLAYLIST_IMPORT_LIMIT,
        preferredUrl = cleanUrl,
        publicApiFallback = true,
    )
        .distinctBy { it.streamDistinctKey() }
    progress(
        PremiumDeckPlaylistImportProgress(
            id = progressId,
            provider = "YouTube",
            title = cleanTitle,
            phase = if (sources.isEmpty()) "No tracks found" else "Importing tracks",
            processed = 0,
            total = sources.size,
            matched = 0,
            complete = sources.isEmpty(),
        ),
    )
    val imported = mutableListOf<YouTubeSource>()
    sources.forEachIndexed { index, source ->
        imported += source
        progress(
            PremiumDeckPlaylistImportProgress(
                id = progressId,
                provider = "YouTube",
                title = cleanTitle,
                phase = "Importing tracks",
                processed = index + 1,
                total = sources.size,
                matched = imported.size,
            ),
        )
        if (index < sources.lastIndex) kotlinx.coroutines.delay(45L)
    }
    return YouTubePlaylistImportResult(
        playlistTitle = cleanTitle,
        playlistUrl = cleanUrl.ifBlank { canonicalUrl },
        trackCount = sources.size,
        sources = imported,
    )
}

internal data class SpotifyPlaylistImportResult(
    val playlistTitle: String,
    val playlistUrl: String,
    val trackCount: Int,
    val sources: List<YouTubeSource>,
)

private data class SpotifyPlaylistImport(
    val title: String,
    val url: String,
    val tracks: List<SpotifyPlaylistTrack>,
)

private data class SpotifyPlaylistTrack(
    val title: String,
    val artist: String,
    val durationMillis: Long,
)

internal suspend fun fetchPremiumDeckSpotifyPlaylistSources(
    playlistUrl: String,
    titleHint: String,
    savedSources: List<YouTubeSource>,
    progress: (PremiumDeckPlaylistImportProgress) -> Unit = {},
): SpotifyPlaylistImportResult {
    val normalizedUrl = normalizeSpotifyPlaylistUrl(playlistUrl)
    val progressId = "spotify-import-${System.currentTimeMillis()}"
    val initialTitle = titleHint.trim().ifBlank { "Spotify Playlist" }
    progress(
        PremiumDeckPlaylistImportProgress(
            id = progressId,
            provider = "Spotify",
            title = initialTitle,
            phase = "Reading playlist",
        ),
    )
    if (normalizedUrl.isBlank() || PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
        return SpotifyPlaylistImportResult(initialTitle, normalizedUrl, 0, emptyList())
    }
    val playlistId = spotifyPlaylistIdFromUrl(normalizedUrl)
        ?: spotifyPlaylistIdFromUrl(expandSpotifyPlaylistUrl(normalizedUrl))
        ?: return SpotifyPlaylistImportResult(initialTitle, normalizedUrl, 0, emptyList())
    val canonicalUrl = "https://open.spotify.com/playlist/$playlistId"
    val playlist = fetchSpotifyPlaylistFromPublicPages(playlistId, canonicalUrl)
        ?: fetchSpotifyPlaylistFromWebApi(playlistId)
        ?: SpotifyPlaylistImport(title = initialTitle, url = canonicalUrl, tracks = emptyList())
    val tracks = playlist.tracks
        .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
        .distinctBy { "${it.artist.normalizedSearchText()}|${it.title.normalizedSearchText()}" }
        .take(SPOTIFY_PLAYLIST_IMPORT_LIMIT)
    val importTitle = playlist.title.ifBlank { initialTitle }
    progress(
        PremiumDeckPlaylistImportProgress(
            id = progressId,
            provider = "Spotify",
            title = importTitle,
            phase = if (tracks.isEmpty()) "No tracks found" else "Matching tracks",
            processed = 0,
            total = tracks.size,
            matched = 0,
            complete = tracks.isEmpty(),
        ),
    )
    val imported = mutableListOf<YouTubeSource>()
    val storageTag = "premiumdeck-spotify-playlist:$playlistId"
    tracks.forEachIndexed { index, track ->
        if (!PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
            val source = resolveSpotifyPlaylistTrack(track, savedSources + imported)
            if (source != null) {
                imported += source.copy(
                    reviewState = YouTubeReviewState.Accepted,
                    thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
                    albumTitleHint = storageTag,
                    albumTrackNumberHint = index + 1,
                    albumTrackTotalHint = tracks.size,
                )
            }
        }
        progress(
            PremiumDeckPlaylistImportProgress(
                id = progressId,
                provider = "Spotify",
                title = importTitle,
                phase = "Matching tracks",
                processed = index + 1,
                total = tracks.size,
                matched = imported.size,
            ),
        )
        if (index < tracks.lastIndex) kotlinx.coroutines.delay(90L)
    }
    return SpotifyPlaylistImportResult(
        playlistTitle = importTitle,
        playlistUrl = playlist.url.ifBlank { canonicalUrl },
        trackCount = tracks.size,
        sources = imported.distinctBy { it.streamDistinctKey() },
    )
}

private fun normalizeSpotifyPlaylistUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("spotify:playlist:", ignoreCase = true)) return trimmed
    if (trimmed.startsWith("open.spotify.com", ignoreCase = true) ||
        trimmed.startsWith("spotify.link", ignoreCase = true) ||
        trimmed.startsWith("spotify.app.link", ignoreCase = true)
    ) {
        return "https://$trimmed"
    }
    return trimmed
}

private fun spotifyPlaylistIdFromUrl(value: String): String? {
    val trimmed = value.trim()
    Regex("""spotify:playlist:([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    Regex("""(?:open\.)?spotify\.com/(?:embed/)?playlist/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return null
}

private fun expandSpotifyPlaylistUrl(url: String): String {
    var current = normalizeSpotifyPlaylistUrl(url)
    repeat(4) {
        val next = runCatching {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 1800
            connection.readTimeout = 2200
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/html,*/*")
            connection.setRequestProperty("User-Agent", "PulseDeck/0.1")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location").orEmpty()
                if (location.isBlank()) current else URL(URL(current), location).toString()
            } else {
                connection.url.toString()
            }
        }.getOrNull()
        if (next.isNullOrBlank() || next == current) return current
        current = next
        if (spotifyPlaylistIdFromUrl(current) != null) return current
    }
    return current
}

private fun fetchSpotifyPlaylistFromWebApi(playlistId: String): SpotifyPlaylistImport? {
    val token = fetchSpotifyAnonymousAccessToken() ?: return null
    val encodedId = URLEncoder.encode(playlistId, "UTF-8")
    val tracks = mutableListOf<SpotifyPlaylistTrack>()
    val rootUrl = "https://api.spotify.com/v1/playlists/$encodedId?fields=name,external_urls,tracks(total,next,items(track(name,artists(name),duration_ms,is_local,type)))&market=from_token"
    val root = spotifyHttpJson(rootUrl, token) ?: return null
    val playlistTitle = root.optString("name").trim()
    val playlistUrl = root.optJSONObject("external_urls")?.optString("spotify").orEmpty()
        .ifBlank { "https://open.spotify.com/playlist/$playlistId" }
    val firstTracks = root.optJSONObject("tracks")
    tracks += parseSpotifyTrackItems(firstTracks)
    var nextUrl = firstTracks?.optString("next").orEmpty()
    while (nextUrl.isNotBlank() && tracks.size < SPOTIFY_PLAYLIST_IMPORT_LIMIT) {
        val page = spotifyHttpJson(nextUrl, token) ?: break
        tracks += parseSpotifyTrackItems(page)
        nextUrl = page.optString("next").orEmpty()
    }
    return SpotifyPlaylistImport(
        title = playlistTitle,
        url = playlistUrl,
        tracks = tracks.distinctBy { "${it.artist.normalizedSearchText()}|${it.title.normalizedSearchText()}" },
    )
}

private fun fetchSpotifyAnonymousAccessToken(): String? {
    val json = httpJson("https://open.spotify.com/get_access_token?reason=transport&productType=web_player", connectTimeout = 2600, readTimeout = 5200)
        ?: return null
    return json.optString("accessToken").ifBlank { json.optString("access_token") }.takeIf { it.isNotBlank() }
}

private fun spotifyHttpJson(url: String, token: String): JSONObject? =
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2600
        connection.readTimeout = 7000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("User-Agent", "PulseDeck/0.1")
        if (connection.responseCode !in 200..299) return@runCatching null
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()

private fun parseSpotifyTrackItems(container: JSONObject?): List<SpotifyPlaylistTrack> {
    val items = container?.optJSONArray("items") ?: return emptyList()
    return buildList {
        for (index in 0 until items.length()) {
            val track = items.optJSONObject(index)?.optJSONObject("track")
                ?: items.optJSONObject(index)
                ?: continue
            if (track.optBoolean("is_local", false)) continue
            val type = track.optString("type").lowercase(Locale.US)
            if (type.isNotBlank() && type != "track") continue
            val title = track.optString("name").trim()
            val artist = track.spotifyArtistNames().joinToString(", ")
            if (title.isBlank() || artist.isBlank()) continue
            add(
                SpotifyPlaylistTrack(
                    title = title.cleanStreamTitle(),
                    artist = artist.cleanStreamArtistName(),
                    durationMillis = track.optLong("duration_ms", 0L),
                ),
            )
        }
    }
}

private fun fetchSpotifyPlaylistFromPublicPages(playlistId: String, canonicalUrl: String): SpotifyPlaylistImport? {
    val embedUrl = "https://open.spotify.com/embed/playlist/${URLEncoder.encode(playlistId, "UTF-8")}"
    listOf(embedUrl, canonicalUrl).forEach { pageUrl ->
        val playlist = fetchSpotifyPlaylistFromPage(pageUrl)
        if (playlist != null && playlist.tracks.isNotEmpty()) {
            return playlist.copy(url = canonicalUrl)
        }
    }
    return fetchSpotifyPlaylistFromPage(canonicalUrl)?.copy(url = canonicalUrl)
}

private fun fetchSpotifyPlaylistFromPage(pageUrl: String): SpotifyPlaylistImport? {
    val html = httpText(pageUrl, connectTimeout = 2600, readTimeout = 6200) ?: return null
    val nextData = extractSpotifyNextDataJson(html)
    val title = spotifyPageTitle(html)
        .ifBlank { nextData?.spotifyPlaylistTitle().orEmpty() }
        .ifBlank { "Spotify Playlist" }
    val tracks = nextData?.collectSpotifyPlaylistTracks(SPOTIFY_PLAYLIST_IMPORT_LIMIT).orEmpty()
    return SpotifyPlaylistImport(title = title, url = pageUrl, tracks = tracks)
}

private fun JSONObject.spotifyPlaylistTitle(): String {
    val queue = java.util.ArrayDeque<Any>()
    queue.add(this)
    while (!queue.isEmpty()) {
        when (val current = queue.removeFirst()) {
            is JSONObject -> {
                val type = current.optString("type").lowercase(Locale.US)
                val uri = current.optString("uri")
                if (type == "playlist" || uri.startsWith("spotify:playlist:", ignoreCase = true)) {
                    current.optString("name").trim().takeIf { it.isNotBlank() }?.let { return it }
                    current.optString("title").trim().takeIf { it.isNotBlank() }?.let { return it }
                }
                val names = current.names()
                if (names != null) {
                    for (index in 0 until names.length()) {
                        when (val child = current.opt(names.optString(index))) {
                            is JSONObject -> queue.add(child)
                            is JSONArray -> queue.add(child)
                        }
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until current.length()) {
                    when (val child = current.opt(index)) {
                        is JSONObject -> queue.add(child)
                        is JSONArray -> queue.add(child)
                    }
                }
            }
        }
    }
    return ""
}

private fun spotifyPageTitle(html: String): String {
    val metaTitle = listOf(
        Regex("""<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']""", RegexOption.IGNORE_CASE),
        Regex("""<title>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    ).firstNotNullOfOrNull { regex ->
        regex.find(html)?.groupValues?.getOrNull(1)?.spotifyHtmlDecoded()?.trim()
    }.orEmpty()
    return metaTitle
        .substringBefore(" - playlist", metaTitle)
        .substringBefore(" | Spotify", metaTitle)
        .trim()
}

private fun extractSpotifyNextDataJson(html: String): JSONObject? {
    val raw = Regex(
        """<script[^>]+id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.spotifyHtmlDecoded()
        ?: return null
    return runCatching { JSONObject(raw) }.getOrNull()
}

private fun JSONObject.collectSpotifyPlaylistTracks(limit: Int): List<SpotifyPlaylistTrack> {
    val results = mutableListOf<SpotifyPlaylistTrack>()
    val queue = java.util.ArrayDeque<Any>()
    queue.add(this)
    while (!queue.isEmpty() && results.size < limit) {
        when (val current = queue.removeFirst()) {
            is JSONObject -> {
                current.toSpotifyPlaylistTrackOrNull()?.let { track ->
                    if (results.none { "${it.artist.normalizedSearchText()}|${it.title.normalizedSearchText()}" == "${track.artist.normalizedSearchText()}|${track.title.normalizedSearchText()}" }) {
                        results += track
                    }
                }
                val names = current.names()
                if (names != null) {
                    for (index in 0 until names.length()) {
                        when (val child = current.opt(names.optString(index))) {
                            is JSONObject -> queue.add(child)
                            is JSONArray -> queue.add(child)
                        }
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until current.length()) {
                    when (val child = current.opt(index)) {
                        is JSONObject -> queue.add(child)
                        is JSONArray -> queue.add(child)
                    }
                }
            }
        }
    }
    return results
}

private fun JSONObject.toSpotifyPlaylistTrackOrNull(): SpotifyPlaylistTrack? {
    val type = optString("type").ifBlank { optString("__typename") }.lowercase(Locale.US)
    val uri = optString("uri")
    val durationMillis = optLong("duration_ms", 0L)
        .takeIf { it > 0L }
        ?: optLong("durationMilliseconds", 0L).takeIf { it > 0L }
        ?: optLong("duration", 0L).takeIf { it > 0L }
        ?: optJSONObject("duration")?.optLong("totalMilliseconds", 0L)
        ?: 0L
    val artists = spotifyArtistNames().ifEmpty {
        optString("subtitle").split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val title = optString("name")
        .ifBlank { optString("title") }
        .ifBlank { optJSONObject("data")?.optString("name").orEmpty() }
        .trim()
    val likelyTrack = type.contains("track") ||
        uri.startsWith("spotify:track:", ignoreCase = true) ||
        (durationMillis > 0L && title.isNotBlank() && artists.isNotEmpty())
    if (!likelyTrack || title.isBlank() || artists.isEmpty()) return null
    return SpotifyPlaylistTrack(
        title = title.cleanStreamTitle(),
        artist = artists.joinToString(", ").cleanStreamArtistName(),
        durationMillis = durationMillis,
    )
}

private fun JSONObject.spotifyArtistNames(): List<String> {
    val direct = optJSONArray("artists")?.spotifyArtistNamesFromArray().orEmpty()
    if (direct.isNotEmpty()) return direct
    val artistItems = optJSONObject("artists")?.optJSONArray("items")?.spotifyArtistNamesFromArray().orEmpty()
    if (artistItems.isNotEmpty()) return artistItems
    val dataArtistItems = optJSONObject("data")?.optJSONObject("artists")?.optJSONArray("items")?.spotifyArtistNamesFromArray().orEmpty()
    if (dataArtistItems.isNotEmpty()) return dataArtistItems
    return emptyList()
}

private fun JSONArray.spotifyArtistNamesFromArray(): List<String> =
    buildList {
        for (index in 0 until length()) {
            val artist = optJSONObject(index) ?: continue
            val name = artist.optString("name")
                .ifBlank { artist.optJSONObject("profile")?.optString("name").orEmpty() }
                .ifBlank { artist.optJSONObject("data")?.optJSONObject("profile")?.optString("name").orEmpty() }
                .trim()
            if (name.isNotBlank()) add(name)
        }
    }

private suspend fun resolveSpotifyPlaylistTrack(track: SpotifyPlaylistTrack, savedSources: List<YouTubeSource>): YouTubeSource? {
    val localMatch = savedSources
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        .mapNotNull { source ->
            val score = scoreSpotifyTrackSource(track, source)
            if (score >= 78) source to score else null
        }
        .maxByOrNull { it.second }
        ?.first
    if (localMatch != null) return localMatch

    val queries = listOf(
        "${track.artist} ${track.title} official audio",
        "${track.artist} ${track.title}",
        "${track.artist} ${track.title} topic",
    )
        .filter { it.normalizedSearchText().length >= 3 }
        .distinctBy { it.normalizedSearchText() }
        .take(SPOTIFY_PLAYLIST_SEARCH_QUERY_LIMIT)
    val candidates = mutableListOf<YouTubeSource>()
    for ((index, query) in queries.withIndex()) {
        val found = withTimeoutOrNull(SPOTIFY_PLAYLIST_SEARCH_TIMEOUT_MILLIS) {
            searchYouTubeVideos(query, requestLabel = "spotify-import").results.map { it.source }
        }.orEmpty()
        candidates += found
        val best = candidates
            .distinctBy { it.streamDistinctKey() }
            .mapNotNull { source ->
                val score = scoreSpotifyTrackSource(track, source)
                if (score > 0) source to score else null
            }
            .maxByOrNull { it.second }
        if (best != null && best.second >= 70) return best.first
        if (found.isNotEmpty() && index < queries.lastIndex) kotlinx.coroutines.delay(80L)
    }
    return candidates
        .distinctBy { it.streamDistinctKey() }
        .mapNotNull { source ->
            val score = scoreSpotifyTrackSource(track, source)
            if (score >= 58) source to score else null
        }
        .maxByOrNull { it.second }
        ?.first
}

private fun scoreSpotifyTrackSource(track: SpotifyPlaylistTrack, source: YouTubeSource): Int {
    val trackTitle = track.title.cleanStreamTitle().normalizedSearchText()
    val trackArtist = track.artist.cleanStreamArtistName().normalizedSearchText()
    val sourceTitle = source.title.cleanStreamTitle().normalizedSearchText()
    val sourceArtist = source.author.cleanStreamArtistName().normalizedSearchText()
    val sourceText = "${source.title} ${source.author} ${source.channelTitle}".normalizedSearchText()
    var score = 0
    if (sourceTitle == trackTitle) score += 52
    else if (sourceTitle.contains(trackTitle) || trackTitle.contains(sourceTitle)) score += 34
    else if (premiumDeckTokenOverlap(sourceTitle, trackTitle) >= 0.70f) score += 28

    if (sourceArtist == trackArtist) score += 34
    else if (sourceText.contains(trackArtist) || premiumDeckTokenOverlap(sourceArtist, trackArtist) >= 0.62f) score += 22

    if (sourceText.contains("official audio") || sourceText.contains("topic") || sourceText.contains("vevo")) score += 10
    if (source.channelVerified) score += 6
    if (track.durationMillis > 0L && source.durationMillis > 0L) {
        val delta = kotlin.math.abs(track.durationMillis - source.durationMillis)
        score += when {
            delta <= 4_000L -> 10
            delta <= 12_000L -> 6
            delta <= 30_000L -> 2
            delta > 75_000L -> -14
            else -> 0
        }
    }
    if (sourceText.contains("lyrics") || sourceText.contains("lyric video")) score -= 3
    if (listOf("reaction", "cover", "karaoke", "tutorial", "live stream", "hour", "mix", "full album").any { sourceText.contains(it) }) score -= 22
    return score.coerceIn(0, 100)
}

private fun String.spotifyHtmlDecoded(): String =
    replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

private fun parseYouTubeMusicExploreShelves(html: String): List<YouTubeExploreShelf> {
    val root = extractYouTubeInitialData(html) ?: return emptyList()
    val shelves = root.collectNestedObjects("richShelfRenderer", limit = 40)
        .mapIndexedNotNull { index, renderer ->
            val title = renderer.optJSONObject("title")?.readYouTubeText().orEmpty()
            val contents = renderer.optJSONArray("contents") ?: return@mapIndexedNotNull null
            val cards = buildList {
                for (cardIndex in 0 until contents.length()) {
                    val lockup = contents.optJSONObject(cardIndex)
                        ?.collectNestedObjects("lockupViewModel", limit = 1)
                        ?.firstOrNull()
                        ?: continue
                    lockup.toYouTubeExploreCard(title, index, cardIndex)?.let(::add)
                }
            }
            if (title.isBlank() || cards.isEmpty()) return@mapIndexedNotNull null
            YouTubeExploreShelf(title = title, rank = index, cards = cards)
        }
    val targeted = shelves.filter { shelf ->
        youtubeMusicExploreShelfPriority.any { target -> shelf.title.equals(target, ignoreCase = true) }
    }
    return (targeted.ifEmpty { shelves.take(3) })
        .sortedWith(
            compareBy<YouTubeExploreShelf> { shelf ->
                youtubeMusicExploreShelfPriority.indexOfFirst { it.equals(shelf.title, ignoreCase = true) }
                    .takeIf { it >= 0 }
                    ?: Int.MAX_VALUE
            }.thenBy { it.rank },
        )
        .take(6)
}

private fun JSONObject.toYouTubeExploreCard(shelfTitle: String, shelfRank: Int, cardRank: Int): YouTubeExploreCard? {
    val title = lockupTitle().ifBlank { return null }
    val contentType = optString("contentType")
    val contentId = optString("contentId").trim()
    val endpointPlaylistId = collectNestedObjects("watchPlaylistEndpoint", limit = 1)
        .firstOrNull()
        ?.optString("playlistId")
        ?.trim()
        .orEmpty()
    val endpointVideoId = collectNestedObjects("watchEndpoint", limit = 1)
        .firstOrNull()
        ?.optString("videoId")
        ?.trim()
        .orEmpty()
    val playlistId = when {
        endpointPlaylistId.isNotBlank() -> endpointPlaylistId
        contentType.contains("PLAYLIST", ignoreCase = true) || contentType.contains("ALBUM", ignoreCase = true) -> contentId
        contentId.startsWith("RD", ignoreCase = true) || contentId.startsWith("VL", ignoreCase = true) -> contentId
        else -> ""
    }
    val videoId = when {
        endpointVideoId.isNotBlank() -> endpointVideoId
        playlistId.isBlank() && contentId.length in 8..16 -> contentId
        else -> ""
    }
    if (playlistId.isBlank() && videoId.isBlank()) return null
    val subtitle = lockupMetadataLine()
    return YouTubeExploreCard(
        id = youtubeExploreStableId(title, contentId.ifBlank { playlistId.ifBlank { videoId } }, shelfTitle),
        title = title,
        subtitle = subtitle,
        contentId = contentId,
        playlistId = playlistId,
        videoId = videoId,
        thumbnailUrl = optJSONObject("contentImage")?.bestYouTubeImageUrl() ?: bestYouTubeImageUrl(),
        shelfTitle = shelfTitle,
        shelfRank = shelfRank,
        cardRank = cardRank,
    )
}

private fun JSONObject.toYouTubeExplorePlaylistVideoSource(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    rank: Int,
): YouTubeSource? {
    val videoId = optString("videoId").trim().takeIf { it.isNotBlank() } ?: return null
    val rawTitle = optJSONObject("title")?.readYouTubeText()
        ?.ifBlank { optString("title") }
        .orEmpty()
        .trim()
    if (rawTitle.isBlank() || rawTitle.equals("Deleted video", ignoreCase = true) || rawTitle.equals("Private video", ignoreCase = true)) return null
    val rawAuthor = optJSONObject("shortBylineText")?.readYouTubeText()
        ?.ifBlank { optJSONObject("longBylineText")?.readYouTubeText().orEmpty() }
        ?.ifBlank { optJSONObject("ownerText")?.readYouTubeText().orEmpty() }
        ?.ifBlank { card.subtitle }
        .orEmpty()
    val duration = optString("lengthSeconds").toLongOrNull()?.times(1000L)
        ?: parseYouTubeDurationTextMillis(optJSONObject("lengthText")?.readYouTubeText().orEmpty())
    val thumbnail = optJSONObject("thumbnail")?.bestYouTubeImageUrl()
        ?: optJSONObject("thumbnailOverlays")?.bestYouTubeImageUrl()
        ?: youtubeThumbnailUrlForVideoId(videoId)
    return buildYouTubeExploreVideoSource(
        videoId = videoId,
        rawTitle = rawTitle,
        rawAuthor = rawAuthor,
        thumbnailUrl = thumbnail,
        durationMillis = duration,
        savedSources = savedSources,
        tag = tag,
        rank = rank,
        total = 0,
    )
}

private fun JSONObject.toYouTubeExploreLockupVideoSource(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    rank: Int,
): YouTubeSource? {
    val contentType = optString("contentType")
    if (!contentType.contains("VIDEO", ignoreCase = true)) return null
    val videoId = collectNestedObjects("watchEndpoint", limit = 1)
        .firstOrNull()
        ?.optString("videoId")
        ?.trim()
        .orEmpty()
        .ifBlank { optString("contentId").trim() }
    if (videoId.isBlank()) return null
    val rawTitle = lockupTitle().ifBlank { card.title }
    val rawAuthor = lockupMetadataLine().ifBlank { card.subtitle }
    val duration = parseYouTubeDurationTextMillis(lockupDurationText())
    return buildYouTubeExploreVideoSource(
        videoId = videoId,
        rawTitle = rawTitle,
        rawAuthor = rawAuthor,
        thumbnailUrl = optJSONObject("contentImage")?.bestYouTubeImageUrl() ?: youtubeThumbnailUrlForVideoId(videoId),
        durationMillis = duration,
        savedSources = savedSources,
        tag = tag,
        rank = rank,
        total = 0,
    )
}

private fun JSONObject.toYouTubePlaylistPanelVideoSource(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    rank: Int,
): YouTubeSource? {
    val videoId = optString("videoId").trim().takeIf { it.isNotBlank() } ?: return null
    val rawTitle = optJSONObject("title")?.readYouTubeText()
        ?.ifBlank { optString("title") }
        .orEmpty()
        .trim()
    if (rawTitle.isBlank() || rawTitle.equals("Deleted video", ignoreCase = true) || rawTitle.equals("Private video", ignoreCase = true)) return null
    val rawAuthor = optJSONObject("longBylineText")?.readYouTubeText()
        ?.ifBlank { optJSONObject("shortBylineText")?.readYouTubeText().orEmpty() }
        ?.ifBlank { optJSONObject("ownerText")?.readYouTubeText().orEmpty() }
        ?.ifBlank { card.subtitle }
        .orEmpty()
    val duration = optString("lengthSeconds").toLongOrNull()?.times(1000L)
        ?: parseYouTubeDurationTextMillis(optJSONObject("lengthText")?.readYouTubeText().orEmpty())
    return buildYouTubeExploreVideoSource(
        videoId = videoId,
        rawTitle = rawTitle,
        rawAuthor = rawAuthor,
        thumbnailUrl = optJSONObject("thumbnail")?.bestYouTubeImageUrl() ?: youtubeThumbnailUrlForVideoId(videoId),
        durationMillis = duration,
        savedSources = savedSources,
        tag = tag,
        rank = rank,
        total = 0,
    )
}

private fun JSONObject.toYouTubeMusicPlaylistItemSource(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    rank: Int,
): YouTubeSource? {
    val videoId = optJSONObject("playlistItemData")?.optString("videoId")?.trim().orEmpty()
        .ifBlank {
            collectNestedObjects("watchEndpoint", limit = 1)
                .firstOrNull()
                ?.optString("videoId")
                ?.trim()
                .orEmpty()
        }
    if (videoId.isBlank()) return null
    val columns = musicResponsiveColumnTexts()
    val rawTitle = columns.firstOrNull { it.isNotBlank() }
        ?: optJSONObject("title")?.readYouTubeText().orEmpty()
    if (rawTitle.isBlank()) return null
    val durationText = musicResponsiveDurationText().ifBlank { columns.firstOrNull { it.contains(':') }.orEmpty() }
    val rawAuthor = columns
        .drop(1)
        .firstOrNull { text -> text.isNotBlank() && !text.contains(':') }
        .orEmpty()
        .ifBlank { card.subtitle }
    return buildYouTubeExploreVideoSource(
        videoId = videoId,
        rawTitle = rawTitle,
        rawAuthor = rawAuthor,
        thumbnailUrl = bestYouTubeImageUrl() ?: youtubeThumbnailUrlForVideoId(videoId),
        durationMillis = parseYouTubeDurationTextMillis(durationText),
        savedSources = savedSources,
        tag = tag,
        rank = rank,
        total = 0,
    )
}

private fun JSONObject.toYouTubeCompactVideoSource(
    card: YouTubeExploreCard,
    savedSources: List<YouTubeSource>,
    tag: String,
    rank: Int,
): YouTubeSource? {
    val videoId = optString("videoId").trim().takeIf { it.isNotBlank() } ?: return null
    val rawTitle = optJSONObject("title")?.readYouTubeText()
        ?.ifBlank { optString("title") }
        .orEmpty()
    if (rawTitle.isBlank() || rawTitle.equals("Deleted video", ignoreCase = true) || rawTitle.equals("Private video", ignoreCase = true)) return null
    val rawAuthor = optJSONObject("longBylineText")?.readYouTubeText()
        ?.ifBlank { optJSONObject("shortBylineText")?.readYouTubeText().orEmpty() }
        ?.ifBlank { card.subtitle }
        .orEmpty()
    val duration = optString("lengthSeconds").toLongOrNull()?.times(1000L)
        ?: parseYouTubeDurationTextMillis(optJSONObject("lengthText")?.readYouTubeText().orEmpty())
    return buildYouTubeExploreVideoSource(
        videoId = videoId,
        rawTitle = rawTitle,
        rawAuthor = rawAuthor,
        thumbnailUrl = optJSONObject("thumbnail")?.bestYouTubeImageUrl() ?: youtubeThumbnailUrlForVideoId(videoId),
        durationMillis = duration,
        savedSources = savedSources,
        tag = tag,
        rank = rank,
        total = 0,
    )
}

private fun YouTubeExploreCard.toYouTubeExploreVideoSource(
    savedSources: List<YouTubeSource>,
    tag: String,
    rank: Int,
    total: Int,
): YouTubeSource? {
    if (videoId.isBlank()) return null
    return buildYouTubeExploreVideoSource(
        videoId = videoId,
        rawTitle = title,
        rawAuthor = subtitle.ifBlank { PREMIUMDECK_SOURCE_NAME },
        thumbnailUrl = thumbnailUrl ?: youtubeThumbnailUrlForVideoId(videoId),
        durationMillis = 0L,
        savedSources = savedSources,
        tag = tag,
        rank = rank,
        total = total,
    )
}

private fun buildYouTubeExploreVideoSource(
    videoId: String,
    rawTitle: String,
    rawAuthor: String,
    thumbnailUrl: String?,
    durationMillis: Long,
    savedSources: List<YouTubeSource>,
    tag: String,
    rank: Int,
    total: Int,
): YouTubeSource? {
    val normalizedVideoId = videoId.trim()
    if (normalizedVideoId.isBlank()) return null
    val inferred = smartYouTubeMusicMetadata(rawTitle, rawAuthor)
    val displayTitle = inferred.title.cleanStreamTitle().ifBlank { rawTitle.cleanStreamTitle() }
    val displayAuthor = inferred.artist.takeIf { inferred.confidence >= 60 }
        ?: rawAuthor.cleanYouTubeArtistCandidate().ifBlank { rawAuthor.ifBlank { PREMIUMDECK_SOURCE_NAME } }
    val normalizedUrl = "https://www.youtube.com/watch?v=$normalizedVideoId"
    val candidate = YouTubeSource(
        id = "video-$normalizedVideoId",
        url = normalizedUrl,
        kind = YouTubeSourceKind.Video,
        title = displayTitle.ifBlank { PREMIUMDECK_STREAM_TITLE },
        author = displayAuthor,
        thumbnailUrl = normalizeYouTubeThumbnailUrl(thumbnailUrl) ?: youtubeThumbnailUrlForVideoId(normalizedVideoId),
        channelTitle = rawAuthor.ifBlank { displayAuthor },
        channelUrl = "",
        channelVerified = false,
        albumTitleHint = tag,
        albumTrackNumberHint = rank,
        albumTrackTotalHint = total,
        durationMillis = durationMillis,
        status = YouTubeSourceStatus.StreamReady,
        reviewState = YouTubeReviewState.Accepted,
    )
    val existing = savedSources.firstOrNull { source ->
        source.id == candidate.id ||
            source.url == normalizedUrl ||
            detectYouTubeSource(source.url)?.sourceId == normalizedVideoId ||
            source.streamDistinctKey() == candidate.streamDistinctKey()
    }
    return (existing ?: candidate).copy(
        id = existing?.id?.takeIf { it.isNotBlank() } ?: candidate.id,
        url = existing?.url?.takeIf { it.isNotBlank() } ?: candidate.url,
        kind = YouTubeSourceKind.Video,
        title = existing?.title?.takeIf { it.isNotBlank() } ?: candidate.title,
        author = existing?.author?.takeIf { it.isNotBlank() && !it.equals(PREMIUMDECK_SOURCE_NAME, ignoreCase = true) } ?: candidate.author,
        thumbnailUrl = candidate.thumbnailUrl ?: existing?.bestThumbnailUrl(),
        channelTitle = existing?.channelTitle?.takeIf { it.isNotBlank() } ?: candidate.channelTitle,
        channelUrl = existing?.channelUrl.orEmpty(),
        channelVerified = existing?.channelVerified ?: false,
        albumTitleHint = tag,
        albumTrackNumberHint = rank,
        albumTrackTotalHint = total,
        durationMillis = existing?.durationMillis?.takeIf { it > 0L } ?: candidate.durationMillis,
        status = existing?.status ?: candidate.status,
        reviewState = YouTubeReviewState.Accepted,
    )
}

private suspend fun fetchPremiumDeckExternalChartSnapshot(savedSources: List<YouTubeSource>): StreamDiscoverySnapshot =
    coroutineScope {
        if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) return@coroutineScope StreamDiscoverySnapshot()
        val rawTracks = premiumDeckChartCountries
            .map { country ->
                async {
                    runCatching { fetchAppleMusicMostPlayedChart(country) }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
        val ranked = rankPremiumDeckChartTracks(rawTracks)
        if (ranked.isEmpty()) return@coroutineScope StreamDiscoverySnapshot()

        val selectedByChart = selectPremiumDeckChartTracks(ranked)
        val priorityTracks = selectedByChart.roundRobinChartTracks()
            .take(PREMIUM_DECK_CHART_RESOLVE_LIMIT)
        val resolvedByKey = mutableMapOf<String, YouTubeSource>()
        for ((index, track) in priorityTracks.withIndex()) {
            val source = resolvePremiumDeckChartTrack(track, savedSources)
            if (source != null) resolvedByKey[track.key] = source
            if (index < priorityTracks.lastIndex) kotlinx.coroutines.delay(110L)
        }

        val specsById = premiumDeckChartCollectionSpecs.associateBy { it.id }
        val results = selectedByChart.flatMap { (chartId, tracks) ->
            val spec = specsById[chartId] ?: return@flatMap emptyList()
            tracks.mapIndexedNotNull { index, track ->
                val source = resolvedByKey[track.key] ?: return@mapIndexedNotNull null
                val chartRank = index + 1
                val chartSource = source.copy(
                    reviewState = YouTubeReviewState.Accepted,
                    thumbnailUrl = track.artworkUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                        ?: source.bestThumbnailUrl()
                        ?: source.thumbnailUrl,
                    albumTitleHint = premiumDeckChartStorageTag(chartId),
                    albumTrackNumberHint = chartRank,
                    albumTrackTotalHint = tracks.size,
                    albumYearHint = track.releaseDate.take(4).toIntOrNull() ?: 0,
                )
                YouTubeSearchResult(
                    source = chartSource,
                    durationMillis = chartSource.durationMillis,
                    uploadedDate = track.releaseDate,
                    views = track.score.toLong(),
                    videoId = chartSource.streamDistinctKey(),
                    score = (track.score + (100 - chartRank)).coerceAtLeast(1),
                    cachedMillis = System.currentTimeMillis(),
                    matchReason = "${spec.title} #$chartRank  |  Apple Music public chart",
                )
            }
        }
        StreamDiscoverySnapshot(savedMillis = System.currentTimeMillis(), results = results)
    }

private val premiumDeckChartCollectionSpecs = listOf(
    PremiumDeckChartCollectionSpec(
        id = "global-hot-right-now",
        title = "Hot Right Now",
        subtitle = "Top 15 songs from public chart feeds",
    ),
    PremiumDeckChartCollectionSpec(
        id = "global-new-releases",
        title = "New Releases",
        subtitle = "Fresh charting songs with recent release dates",
    ),
    PremiumDeckChartCollectionSpec(
        id = "global-trending-music",
        title = "Trending Music",
        subtitle = "Music moving across multiple markets",
    ),
    PremiumDeckChartCollectionSpec(
        id = "global-charts",
        title = "Global Charts",
        subtitle = "Multi-market chart ranking, resolved through YouTube",
    ),
    PremiumDeckChartCollectionSpec(
        id = "rising-this-week",
        title = "Rising This Week",
        subtitle = "Fresh regional chart breakouts",
    ),
)

private fun Map<String, List<RankedPremiumDeckChartTrack>>.roundRobinChartTracks(): List<RankedPremiumDeckChartTrack> {
    val maxSize = values.maxOfOrNull { it.size } ?: 0
    val seen = mutableSetOf<String>()
    return buildList {
        for (index in 0 until maxSize) {
            values.forEach { tracks ->
                val track = tracks.getOrNull(index) ?: return@forEach
                if (seen.add(track.key)) add(track)
            }
        }
    }
}

private suspend fun fetchAppleMusicMostPlayedChart(country: String): List<PremiumDeckChartTrack> {
    val url = "https://rss.marketingtools.apple.com/api/v2/$country/music/most-played/$APPLE_MUSIC_CHART_LIMIT/songs.json"
    val json = withTimeoutOrNull(5200L) { httpJson(url, connectTimeout = 2200, readTimeout = 4200) } ?: return emptyList()
    return parseAppleMusicMostPlayedTracks(json, country)
}

private fun parseAppleMusicMostPlayedTracks(json: JSONObject, fallbackCountry: String): List<PremiumDeckChartTrack> {
    val feed = json.optJSONObject("feed") ?: return emptyList()
    val country = feed.optString("country", fallbackCountry).ifBlank { fallbackCountry }
    val results = feed.optJSONArray("results") ?: return emptyList()
    return buildList {
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val title = item.optString("name").trim()
            val artist = item.optString("artistName").trim()
            if (title.length < 2 || artist.length < 2) continue
            val releaseDate = item.optString("releaseDate").trim()
            val releaseMillis = parsePremiumDeckUploadDateMillis(releaseDate) ?: 0L
            val key = premiumDeckChartTrackKey(title, artist)
            add(
                PremiumDeckChartTrack(
                    key = key,
                    title = title,
                    artist = artist,
                    releaseDate = releaseDate,
                    releaseMillis = releaseMillis,
                    artworkUrl = item.optString("artworkUrl100").trim(),
                    appleUrl = item.optString("url").trim(),
                    country = country.lowercase(Locale.US),
                    rank = index + 1,
                ),
            )
        }
    }
}

private fun rankPremiumDeckChartTracks(tracks: List<PremiumDeckChartTrack>): List<RankedPremiumDeckChartTrack> {
    data class Accumulator(
        var title: String,
        var artist: String,
        var releaseDate: String,
        var releaseMillis: Long,
        var artworkUrl: String,
        var appleUrl: String,
        val countries: MutableSet<String> = linkedSetOf(),
        var bestRank: Int = Int.MAX_VALUE,
        var score: Int = 0,
    )

    val byKey = linkedMapOf<String, Accumulator>()
    tracks.forEach { track ->
        val current = byKey.getOrPut(track.key) {
            Accumulator(track.title, track.artist, track.releaseDate, track.releaseMillis, track.artworkUrl, track.appleUrl)
        }
        current.countries += track.country
        current.bestRank = min(current.bestRank, track.rank)
        current.score += (APPLE_MUSIC_CHART_LIMIT + 1 - track.rank).coerceAtLeast(1)
        if (track.releaseMillis > current.releaseMillis) {
            current.releaseDate = track.releaseDate
            current.releaseMillis = track.releaseMillis
        }
        if (current.artworkUrl.isBlank() && track.artworkUrl.isNotBlank()) current.artworkUrl = track.artworkUrl
        if (current.appleUrl.isBlank() && track.appleUrl.isNotBlank()) current.appleUrl = track.appleUrl
    }

    return byKey.map { (key, value) ->
        RankedPremiumDeckChartTrack(
            key = key,
            title = value.title,
            artist = value.artist,
            releaseDate = value.releaseDate,
            releaseMillis = value.releaseMillis,
            artworkUrl = value.artworkUrl,
            appleUrl = value.appleUrl,
            countries = value.countries.toSet(),
            bestRank = value.bestRank.takeIf { it != Int.MAX_VALUE } ?: APPLE_MUSIC_CHART_LIMIT,
            score = value.score + value.countries.size * 8,
        )
    }.sortedWith(
        compareByDescending<RankedPremiumDeckChartTrack> { it.score }
            .thenBy { it.bestRank }
            .thenByDescending { it.releaseMillis },
    )
}

private fun selectPremiumDeckChartTracks(ranked: List<RankedPremiumDeckChartTrack>): Map<String, List<RankedPremiumDeckChartTrack>> {
    val now = System.currentTimeMillis()
    val recentCutoff = now - 90L * 24L * 60L * 60L * 1000L
    val global = ranked.take(30)
    val hot = ranked.sortedWith(
        compareBy<RankedPremiumDeckChartTrack> { it.bestRank }
            .thenByDescending { it.countries.size }
            .thenByDescending { it.score },
    ).take(15)
    val newReleases = ranked
        .filter { it.releaseMillis >= recentCutoff }
        .sortedWith(compareByDescending<RankedPremiumDeckChartTrack> { it.releaseMillis }.thenByDescending { it.score })
        .ifEmpty { ranked.drop(8) }
        .take(20)
    val trending = ranked
        .filter { it.countries.size >= 2 || it.releaseMillis >= recentCutoff }
        .sortedWith(
            compareByDescending<RankedPremiumDeckChartTrack> { it.score + it.countries.size * 28 + if (it.releaseMillis >= recentCutoff) 40 else 0 }
                .thenBy { it.bestRank },
        )
        .ifEmpty { global }
        .take(20)
    val rising = ranked
        .filter { it.countries.size <= 2 && it.bestRank <= 42 }
        .sortedWith(compareByDescending<RankedPremiumDeckChartTrack> { it.releaseMillis }.thenBy { it.bestRank }.thenByDescending { it.score })
        .ifEmpty { ranked.drop(15) }
        .take(15)

    return linkedMapOf(
        "global-hot-right-now" to hot,
        "global-new-releases" to newReleases,
        "global-trending-music" to trending,
        "global-charts" to global,
        "rising-this-week" to rising,
    )
}

internal fun premiumDeckLastFmApiKey(): String =
    PremiumDeckChartRuntime.lastFmApiKey.trim()

internal fun premiumDeckDirectLastFmChartConfigured(): Boolean =
    premiumDeckLastFmApiKey().isNotBlank()

internal fun premiumDeckBillboardChartAvailable(genre: PremiumDeckChartGenre): Boolean =
    premiumDeckBillboardChartSlugs.containsKey(genre)

internal fun premiumDeckChartAvailabilityMessage(provider: PremiumDeckChartProvider, genre: PremiumDeckChartGenre): String? =
    when (provider) {
        PremiumDeckChartProvider.Auto -> when {
            genre == PremiumDeckChartGenre.Global -> null
            premiumDeckBillboardChartAvailable(genre) -> null
            genre.lastFmTag != null && premiumDeckDirectLastFmChartConfigured() -> null
            else -> "Choose a chart genre first."
        }
        PremiumDeckChartProvider.AppleMusic -> if (genre == PremiumDeckChartGenre.Global) {
            null
        } else {
            "Apple Music public chart feeds do not expose this genre here. Use Global or a no-key Billboard genre chart."
        }
        PremiumDeckChartProvider.Billboard -> if (premiumDeckBillboardChartAvailable(genre)) {
            null
        } else {
            "No no-key Billboard chart is available for ${genre.label} yet."
        }
        PremiumDeckChartProvider.LastFm -> when {
            genre.lastFmTag == null -> "Last.fm needs a genre tag. Choose Pop, R&B, Hip-Hop, or another genre."
            !premiumDeckDirectLastFmChartConfigured() -> "Add a Last.fm API key to use the direct advanced fallback."
            else -> null
        }
    }

internal fun effectivePremiumDeckChartProvider(provider: PremiumDeckChartProvider, genre: PremiumDeckChartGenre): PremiumDeckChartProvider? =
    when (provider) {
        PremiumDeckChartProvider.Auto -> when {
            premiumDeckBillboardChartAvailable(genre) -> PremiumDeckChartProvider.Billboard
            genre.lastFmTag != null && premiumDeckDirectLastFmChartConfigured() -> PremiumDeckChartProvider.LastFm
            else -> null
        }
        PremiumDeckChartProvider.AppleMusic -> PremiumDeckChartProvider.AppleMusic.takeIf { genre == PremiumDeckChartGenre.Global }
        PremiumDeckChartProvider.Billboard -> PremiumDeckChartProvider.Billboard.takeIf { premiumDeckBillboardChartAvailable(genre) }
        PremiumDeckChartProvider.LastFm -> PremiumDeckChartProvider.LastFm.takeIf { genre.lastFmTag != null && premiumDeckDirectLastFmChartConfigured() }
    }

internal suspend fun fetchPremiumDeckChartEntries(
    provider: PremiumDeckChartProvider,
    genre: PremiumDeckChartGenre,
    requestedLimit: Int,
): List<PremiumDeckChartEntry> {
    if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) return emptyList()
    val limit = requestedLimit.coerceIn(PREMIUM_DECK_CHART_SAFE_LIMIT, PREMIUM_DECK_CHART_SESSION_LIMIT)
    if (provider == PremiumDeckChartProvider.Auto || provider == PremiumDeckChartProvider.LastFm || provider == PremiumDeckChartProvider.AppleMusic) {
        val hosted = fetchPremiumDeckHostedChartEntries(genre, limit)
        if (hosted.isNotEmpty()) return hosted
    }
    return when (effectivePremiumDeckChartProvider(provider, genre)) {
        PremiumDeckChartProvider.AppleMusic -> fetchAppleMusicGlobalChartEntries(limit)
        PremiumDeckChartProvider.Billboard -> fetchBillboardJsonChartEntries(genre, limit)
        PremiumDeckChartProvider.LastFm -> if (premiumDeckDirectLastFmChartConfigured()) fetchLastFmTagChartEntries(genre, limit) else emptyList()
        PremiumDeckChartProvider.Auto, null -> emptyList()
    }
}

private suspend fun fetchPremiumDeckHostedChartEntries(genre: PremiumDeckChartGenre, limit: Int): List<PremiumDeckChartEntry> {
    val snapshot = fetchPremiumDeckHostedChartSnapshot() ?: return emptyList()
    val charts = snapshot.optJSONObject("charts") ?: return emptyList()
    val entries = charts.optJSONArray(genre.name) ?: return emptyList()
    val fallbackProvider = if (genre == PremiumDeckChartGenre.Global) PremiumDeckChartProvider.AppleMusic else PremiumDeckChartProvider.LastFm
    return buildList {
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            val artist = item.optString("artist").trim()
            if (title.length < 2 || artist.length < 2) continue
            val providerName = item.optString("provider").trim()
            val entryProvider = PremiumDeckChartProvider.entries.firstOrNull { it.name == providerName } ?: fallbackProvider
            val entryGenre = PremiumDeckChartGenre.entries.firstOrNull { it.name == item.optString("genre").trim() } ?: genre
            add(
                PremiumDeckChartEntry(
                    key = item.optString("key").trim().ifBlank { premiumDeckChartTrackKey(title, artist) },
                    title = title,
                    artist = artist,
                    provider = entryProvider,
                    genre = entryGenre,
                    rank = item.optInt("rank", index + 1).coerceAtLeast(1),
                    score = item.optInt("score", 0).coerceAtLeast(0),
                    releaseDate = item.optString("releaseDate").trim(),
                    releaseMillis = item.optLong("releaseMillis", 0L).coerceAtLeast(0L),
                    artworkUrl = item.optString("artworkUrl").trim().takeIf { it.startsWith("http", ignoreCase = true) }.orEmpty(),
                    sourceUrl = item.optString("sourceUrl").trim().takeIf { it.startsWith("http", ignoreCase = true) }.orEmpty(),
                    country = item.optString("country").trim(),
                ),
            )
        }
    }.distinctBy { it.key }.sortedBy { it.rank }.take(limit)
}

private suspend fun fetchBillboardJsonChartEntries(genre: PremiumDeckChartGenre, limit: Int): List<PremiumDeckChartEntry> {
    val slug = premiumDeckBillboardChartSlugs[genre] ?: return emptyList()
    val url = "$PREMIUM_DECK_BILLBOARD_JSON_BASE_URL/${slug.urlParam()}/recent.json"
    val json = withTimeoutOrNull(5200L) {
        httpJson(url, connectTimeout = 2200, readTimeout = 4200)
    } ?: return emptyList()
    val date = json.optString("date").trim()
    val releaseMillis = parsePremiumDeckUploadDateMillis(date) ?: 0L
    val data = json.optJSONArray("data") ?: return emptyList()
    return buildList {
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val title = item.optString("name").trim()
            val artist = item.optString("artist").trim()
            if (title.length < 2 || artist.length < 2) continue
            val rank = item.optInt("rank", index + 1).coerceAtLeast(1)
            val weeks = item.optInt("weeks_on_chart", 0).coerceAtLeast(0)
            val peak = item.optInt("peak_rank", 0).takeIf { it > 0 } ?: rank
            val score = ((101 - rank).coerceAtLeast(1) * 100 + weeks * 4 + (101 - peak).coerceAtLeast(0)).coerceAtLeast(1)
            add(
                PremiumDeckChartEntry(
                    key = premiumDeckChartTrackKey(title, artist),
                    title = title,
                    artist = artist,
                    provider = PremiumDeckChartProvider.Billboard,
                    genre = genre,
                    rank = rank,
                    score = score,
                    releaseDate = date,
                    releaseMillis = releaseMillis,
                    artworkUrl = item.optString("image").trim().takeIf { it.startsWith("http", ignoreCase = true) }.orEmpty(),
                    sourceUrl = "https://github.com/KoreanThinker/billboard-json/tree/main/$slug",
                    country = if (genre == PremiumDeckChartGenre.Global) "us" else "",
                ),
            )
        }
    }.distinctBy { it.key }.sortedBy { it.rank }.take(limit)
}

private suspend fun fetchPremiumDeckHostedChartSnapshot(): JSONObject? {
    val now = System.currentTimeMillis()
    val cached = premiumDeckHostedChartCache
    if (cached != null && now - cached.first <= PREMIUM_DECK_HOSTED_CHART_CACHE_TTL_MILLIS) return cached.second
    val fresh = withTimeoutOrNull(4200L) {
        httpJson(PREMIUM_DECK_PUBLIC_CHARTS_URL, connectTimeout = 1800, readTimeout = 3400)
            ?: httpJson(PREMIUM_DECK_HOSTED_CHARTS_FALLBACK_URL, connectTimeout = 1800, readTimeout = 3400)
    }
    if (fresh != null) {
        premiumDeckHostedChartCache = now to fresh
        return fresh
    }
    return cached?.second
}

private suspend fun fetchAppleMusicGlobalChartEntries(limit: Int): List<PremiumDeckChartEntry> =
    coroutineScope {
        premiumDeckChartCountries
            .map { country ->
                async {
                    runCatching { fetchAppleMusicMostPlayedChart(country) }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .let(::rankPremiumDeckChartTracks)
            .take(limit.coerceAtMost(APPLE_MUSIC_CHART_LIMIT))
            .mapIndexed { index, track ->
                PremiumDeckChartEntry(
                    key = track.key,
                    title = track.title,
                    artist = track.artist,
                    provider = PremiumDeckChartProvider.AppleMusic,
                    genre = PremiumDeckChartGenre.Global,
                    rank = index + 1,
                    score = track.score,
                    releaseDate = track.releaseDate,
                    releaseMillis = track.releaseMillis,
                    artworkUrl = track.artworkUrl,
                    sourceUrl = track.appleUrl,
                    country = track.countries.joinToString(","),
                )
            }
    }

private suspend fun fetchLastFmTagChartEntries(genre: PremiumDeckChartGenre, limit: Int): List<PremiumDeckChartEntry> {
    val apiKey = premiumDeckLastFmApiKey()
    val tag = genre.lastFmTag?.trim().orEmpty()
    if (apiKey.isBlank() || tag.isBlank()) return emptyList()
    val url = "https://ws.audioscrobbler.com/2.0/?method=tag.gettoptracks&tag=${tag.urlParam()}&api_key=${apiKey.urlParam()}&format=json&limit=${limit.coerceIn(1, PREMIUM_DECK_CHART_SESSION_LIMIT)}"
    val json = withTimeoutOrNull(5200L) { httpJson(url, connectTimeout = 2200, readTimeout = 4200) } ?: return emptyList()
    val tracks = json.optJSONObject("tracks")?.optJSONArray("track") ?: return emptyList()
    return buildList {
        for (index in 0 until tracks.length()) {
            val item = tracks.optJSONObject(index) ?: continue
            val title = item.optString("name").trim()
            val artist = item.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
            if (title.length < 2 || artist.length < 2) continue
            val attrRank = item.optJSONObject("@attr")?.optString("rank")?.toIntOrNull()
            val rank = attrRank ?: (index + 1)
            val listeners = item.optString("listeners").toIntOrNull() ?: item.optInt("listeners", 0)
            add(
                PremiumDeckChartEntry(
                    key = premiumDeckChartTrackKey(title, artist),
                    title = title,
                    artist = artist,
                    provider = PremiumDeckChartProvider.LastFm,
                    genre = genre,
                    rank = rank,
                    score = listeners.coerceAtLeast(0),
                    artworkUrl = item.bestLastFmImageUrl(),
                    sourceUrl = item.optString("url").trim(),
                ),
            )
        }
    }.distinctBy { it.key }.sortedBy { it.rank }.take(limit)
}

private fun JSONObject.bestLastFmImageUrl(): String {
    val images = optJSONArray("image") ?: return ""
    return (0 until images.length())
        .mapNotNull { images.optJSONObject(it)?.optString("#text")?.trim() }
        .lastOrNull { it.startsWith("http", ignoreCase = true) }
        .orEmpty()
}

internal suspend fun matchPremiumDeckChartEntries(
    entries: List<PremiumDeckChartEntry>,
    savedSources: List<YouTubeSource>,
    alreadyMatchedKeys: Set<String>,
    maxToResolve: Int = PREMIUM_DECK_CHART_PAGE_SIZE,
): List<PremiumDeckChartMatch> {
    if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) return emptyList()
    val pending = entries
        .filterNot { it.key in alreadyMatchedKeys }
        .take(maxToResolve.coerceIn(1, PREMIUM_DECK_CHART_PAGE_SIZE))
    if (pending.isEmpty()) return emptyList()
    val matches = mutableListOf<PremiumDeckChartMatch>()
    for ((index, entry) in pending.withIndex()) {
        val track = entry.toRankedPremiumDeckChartTrack()
        val source = resolvePremiumDeckChartTrack(track, savedSources) ?: continue
        val chartSource = source.copy(
            reviewState = YouTubeReviewState.Accepted,
            thumbnailUrl = entry.artworkUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                ?: source.bestThumbnailUrl()
                ?: source.thumbnailUrl,
            albumTitleHint = premiumDeckChartStorageTag(entry.premiumDeckInteractiveChartId()),
            albumTrackNumberHint = entry.rank,
            albumTrackTotalHint = entries.size,
            albumYearHint = entry.releaseDate.take(4).toIntOrNull() ?: 0,
        )
        matches += PremiumDeckChartMatch(
            entry = entry,
            result = YouTubeSearchResult(
            source = chartSource,
            durationMillis = chartSource.durationMillis,
            uploadedDate = entry.releaseDate,
            views = entry.score.toLong(),
            videoId = chartSource.streamDistinctKey(),
            score = (entry.score + (PREMIUM_DECK_CHART_SESSION_LIMIT - entry.rank)).coerceAtLeast(1),
            cachedMillis = System.currentTimeMillis(),
            matchReason = "${entry.provider.label} ${entry.genre.label} #${entry.rank}",
            ),
        )
        if (index < pending.lastIndex) kotlinx.coroutines.delay(110L)
    }
    return matches
}

internal fun PremiumDeckChartEntry.premiumDeckInteractiveChartId(): String =
    "interactive-${provider.name.lowercase(Locale.US)}-${genre.name.lowercase(Locale.US)}"

internal fun buildPremiumDeckInteractiveChartCollection(
    provider: PremiumDeckChartProvider,
    genre: PremiumDeckChartGenre,
    entries: List<PremiumDeckChartEntry>,
    matches: List<PremiumDeckChartMatch>,
): StreamCollection? {
    val sources = matches
        .sortedBy { it.entry.rank }
        .map { it.result.source }
        .distinctBy { it.streamDistinctKey() }
    if (sources.isEmpty()) return null
    val sourceProvider = entries.firstOrNull()?.provider ?: effectivePremiumDeckChartProvider(provider, genre) ?: provider
    val title = if (genre == PremiumDeckChartGenre.Global) {
        "Global Chart"
    } else {
        "${genre.label} Chart"
    }
    return StreamCollection(
        id = "premium-chart-${sourceProvider.name.lowercase(Locale.US)}-${genre.name.lowercase(Locale.US)}",
        title = title,
        subtitle = "${sourceProvider.label} - ${sources.size} matched from ${entries.size.coerceAtLeast(PREMIUM_DECK_CHART_SAFE_LIMIT)} chart entries",
        kind = StreamCollectionKind.Playlist,
        sources = sources,
        accentColor = 0xFF2F8CFF.toInt(),
        canSave = true,
    )
}

private fun PremiumDeckChartEntry.toRankedPremiumDeckChartTrack(): RankedPremiumDeckChartTrack =
    RankedPremiumDeckChartTrack(
        key = key,
        title = title,
        artist = artist,
        releaseDate = releaseDate,
        releaseMillis = releaseMillis,
        artworkUrl = artworkUrl,
        appleUrl = sourceUrl,
        countries = country.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
        bestRank = rank,
        score = score,
    )

private suspend fun resolvePremiumDeckChartTrack(track: RankedPremiumDeckChartTrack, savedSources: List<YouTubeSource>): YouTubeSource? {
    val localMatch = savedSources
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        .mapNotNull { source ->
            val score = scorePremiumDeckChartSource(track, source)
            if (score >= 76) source to score else null
        }
        .maxByOrNull { it.second }
        ?.first
    if (localMatch != null) return localMatch

    val queries = listOf(
        "${track.artist} ${track.title} official audio",
        "${track.artist} ${track.title}",
        "${track.artist} ${track.title} topic",
    ).distinctBy { it.normalizedSearchText() }
    val candidates = mutableListOf<YouTubeSource>()
    for ((index, query) in queries.withIndex()) {
        val found = withTimeoutOrNull(5200L) { searchYouTubeVideos(query).results.map { it.source } }.orEmpty()
        candidates += found
        val best = candidates
            .distinctBy { it.streamDistinctKey() }
            .mapNotNull { source ->
                val score = scorePremiumDeckChartSource(track, source)
                if (score > 0) source to score else null
            }
            .maxByOrNull { it.second }
        if (best != null && best.second >= 70) return best.first
        if (found.isNotEmpty() && index < queries.lastIndex) kotlinx.coroutines.delay(90L)
    }
    return candidates
        .distinctBy { it.streamDistinctKey() }
        .maxByOrNull { scorePremiumDeckChartSource(track, it) }
}

private fun scorePremiumDeckChartSource(track: RankedPremiumDeckChartTrack, source: YouTubeSource): Int {
    val trackTitle = track.title.cleanStreamTitle().normalizedSearchText()
    val trackArtist = track.artist.cleanStreamArtistName().normalizedSearchText()
    val sourceTitle = source.title.cleanStreamTitle().normalizedSearchText()
    val sourceArtist = source.author.cleanStreamArtistName().normalizedSearchText()
    val sourceText = "${source.title} ${source.author} ${source.channelTitle}".normalizedSearchText()
    var score = 0
    if (sourceTitle == trackTitle) score += 52
    else if (sourceTitle.contains(trackTitle) || trackTitle.contains(sourceTitle)) score += 34
    else if (premiumDeckTokenOverlap(sourceTitle, trackTitle) >= 0.70f) score += 26

    if (sourceArtist == trackArtist) score += 32
    else if (sourceText.contains(trackArtist) || premiumDeckTokenOverlap(sourceArtist, trackArtist) >= 0.60f) score += 20

    if (sourceText.contains("official audio") || sourceText.contains("topic")) score += 10
    if (source.channelVerified) score += 6
    if (sourceText.contains("lyrics") || sourceText.contains("lyric video")) score -= 4
    if (listOf("reaction", "cover", "karaoke", "tutorial", "live stream", "hour", "mix").any { sourceText.contains(it) }) score -= 22
    return score.coerceIn(0, 100)
}

private fun premiumDeckChartTrackKey(title: String, artist: String): String =
    "${artist.cleanStreamArtistName().normalizedSearchText()}|${title.cleanStreamTitle().normalizedSearchText()}"

private fun premiumDeckTokenOverlap(candidateKey: String, queryKey: String): Float {
    val candidateTokens = candidateKey.split(" ").filter { it.length >= 2 }.toSet()
    val queryTokens = queryKey.split(" ").filter { it.length >= 2 }.toSet()
    if (candidateTokens.isEmpty() || queryTokens.isEmpty()) return 0f
    return queryTokens.count { it in candidateTokens }.toFloat() / queryTokens.size.toFloat()
}

internal fun premiumDeckChartStorageTag(id: String): String = "$PREMIUM_DECK_CHART_TAG_PREFIX$id"

private fun YouTubeSource.premiumDeckChartId(): String? =
    albumTitleHint.takeIf { it.startsWith(PREMIUM_DECK_CHART_TAG_PREFIX) }?.removePrefix(PREMIUM_DECK_CHART_TAG_PREFIX)

private fun youtubeExploreStorageTag(
    id: String,
    title: String,
    subtitle: String,
    shelfTitle: String,
    coverUrl: String,
): String =
    "$PREMIUM_DECK_YOUTUBE_EXPLORE_TAG_PREFIX" + listOf(id, title, subtitle, shelfTitle, coverUrl)
        .joinToString("|") { it.replace("|", " ").replace('\n', ' ').trim() }

private fun YouTubeSource.youtubeExploreTag(): YouTubeExploreTag? {
    val raw = albumTitleHint.takeIf { it.startsWith(PREMIUM_DECK_YOUTUBE_EXPLORE_TAG_PREFIX) }
        ?.removePrefix(PREMIUM_DECK_YOUTUBE_EXPLORE_TAG_PREFIX)
        ?: return null
    val parts = raw.split("|")
    val id = parts.getOrNull(0).orEmpty().trim()
    if (id.isBlank()) return null
    return YouTubeExploreTag(
        id = id,
        title = parts.getOrNull(1).orEmpty().trim().ifBlank { "YouTube Music" },
        subtitle = parts.getOrNull(2).orEmpty().trim(),
        shelfTitle = parts.getOrNull(3).orEmpty().trim(),
        coverUrl = parts.getOrNull(4).orEmpty().trim(),
    )
}

private fun youtubeExploreStableId(title: String, vararg parts: String): String {
    val label = title.normalizedSearchText()
        .replace(" ", "-")
        .filter { it.isLetterOrDigit() || it == '-' }
        .trim('-')
        .take(44)
        .ifBlank { "youtube-explore" }
    val hash = Math.floorMod((listOf(title) + parts).joinToString("|").hashCode(), Int.MAX_VALUE)
    return "$label-$hash"
}

private fun extractYouTubeInitialData(html: String): JSONObject? {
    val markers = listOf("var ytInitialData =", "ytInitialData =", "window[\"ytInitialData\"] =")
    for (marker in markers) {
        val markerIndex = html.indexOf(marker)
        if (markerIndex < 0) continue
        val objectStart = html.indexOf('{', markerIndex)
        if (objectStart < 0) continue
        val objectEnd = findMatchingJsonObjectEnd(html, objectStart)
        if (objectEnd <= objectStart) continue
        val json = html.substring(objectStart, objectEnd + 1)
        runCatching { return JSONObject(json) }
    }
    return null
}

private fun findMatchingJsonObjectEnd(text: String, objectStart: Int): Int {
    var depth = 0
    var inString = false
    var escaping = false
    for (index in objectStart until text.length) {
        val char = text[index]
        when {
            escaping -> escaping = false
            char == '\\' && inString -> escaping = true
            char == '"' -> inString = !inString
            !inString && char == '{' -> depth += 1
            !inString && char == '}' -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }
    return -1
}

private fun JSONObject.collectNestedObjects(key: String, limit: Int = Int.MAX_VALUE): List<JSONObject> {
    val results = mutableListOf<JSONObject>()
    val queue = java.util.ArrayDeque<Any>()
    queue.add(this)
    while (!queue.isEmpty() && results.size < limit) {
        when (val current = queue.removeFirst()) {
            is JSONObject -> {
                current.optJSONObject(key)?.let { results += it }
                val names = current.names()
                if (names != null) {
                    for (index in 0 until names.length()) {
                        when (val child = current.opt(names.optString(index))) {
                            is JSONObject -> queue.add(child)
                            is JSONArray -> queue.add(child)
                        }
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until current.length()) {
                    when (val child = current.opt(index)) {
                        is JSONObject -> queue.add(child)
                        is JSONArray -> queue.add(child)
                    }
                }
            }
        }
    }
    return results
}

private fun JSONObject.readYouTubeText(): String {
    optString("simpleText").trim().takeIf { it.isNotBlank() }?.let { return it }
    optString("content").trim().takeIf { it.isNotBlank() }?.let { return it }
    optJSONObject("text")?.readYouTubeText()?.takeIf { it.isNotBlank() }?.let { return it }
    val runs = optJSONArray("runs")
    if (runs != null) {
        return buildString {
            for (index in 0 until runs.length()) {
                append(runs.optJSONObject(index)?.optString("text").orEmpty())
            }
        }.trim()
    }
    return ""
}

private fun JSONObject.lockupTitle(): String =
    optJSONObject("metadata")
        ?.optJSONObject("lockupMetadataViewModel")
        ?.optJSONObject("title")
        ?.readYouTubeText()
        .orEmpty()

private fun JSONObject.lockupMetadataLine(): String {
    val rows = optJSONObject("metadata")
        ?.optJSONObject("lockupMetadataViewModel")
        ?.optJSONObject("metadata")
        ?.optJSONObject("contentMetadataViewModel")
        ?.optJSONArray("metadataRows")
        ?: return ""
    for (rowIndex in 0 until rows.length()) {
        val parts = rows.optJSONObject(rowIndex)?.optJSONArray("metadataParts") ?: continue
        val line = buildList {
            for (partIndex in 0 until parts.length()) {
                val text = parts.optJSONObject(partIndex)
                    ?.optJSONObject("text")
                    ?.readYouTubeText()
                    .orEmpty()
                    .trim()
                if (text.isNotBlank()) add(text)
            }
        }.joinToString("  |  ")
        if (line.isNotBlank()) return line
    }
    return ""
}

private fun JSONObject.lockupDurationText(): String =
    collectNestedObjects("thumbnailBadgeViewModel", limit = 8)
        .firstNotNullOfOrNull { badge ->
            badge.optJSONObject("text")?.readYouTubeText()?.takeIf { it.contains(':') }
        }
        .orEmpty()

private fun JSONObject.musicResponsiveColumnTexts(): List<String> {
    val columns = optJSONArray("flexColumns") ?: return emptyList()
    return buildList {
        for (index in 0 until columns.length()) {
            val text = columns.optJSONObject(index)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.readYouTubeText()
                .orEmpty()
                .trim()
            if (text.isNotBlank()) add(text)
        }
    }
}

private fun JSONObject.musicResponsiveDurationText(): String {
    val columns = optJSONArray("fixedColumns") ?: return ""
    for (index in 0 until columns.length()) {
        val text = columns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?.optJSONObject("text")
            ?.readYouTubeText()
            .orEmpty()
            .trim()
        if (text.contains(':')) return text
    }
    return ""
}

private fun JSONObject.bestYouTubeImageUrl(): String? {
    data class ImageCandidate(val url: String, val width: Int, val height: Int)

    val candidates = mutableListOf<ImageCandidate>()
    val queue = java.util.ArrayDeque<Any>()
    queue.add(this)
    while (!queue.isEmpty() && candidates.size < 80) {
        when (val current = queue.removeFirst()) {
            is JSONObject -> {
                val directUrl = current.optString("url").trim()
                if (directUrl.isNotBlank()) {
                    normalizeYouTubeThumbnailUrl(directUrl)?.let { url ->
                        candidates += ImageCandidate(
                            url = url,
                            width = current.optInt("width", 0),
                            height = current.optInt("height", 0),
                        )
                    }
                }
                val names = current.names()
                if (names != null) {
                    for (index in 0 until names.length()) {
                        when (val child = current.opt(names.optString(index))) {
                            is JSONObject -> queue.add(child)
                            is JSONArray -> queue.add(child)
                        }
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until current.length()) {
                    when (val child = current.opt(index)) {
                        is JSONObject -> queue.add(child)
                        is JSONArray -> queue.add(child)
                    }
                }
            }
        }
    }
    return candidates
        .maxByOrNull { it.width.coerceAtLeast(1) * it.height.coerceAtLeast(1) }
        ?.url
}

private fun parseYouTubeDurationTextMillis(raw: String): Long {
    val parts = raw.trim()
        .split(":")
        .mapNotNull { it.trim().toLongOrNull() }
    if (parts.isEmpty()) return 0L
    val seconds = parts.fold(0L) { total, value -> total * 60L + value }
    return seconds * 1000L
}

internal fun streamDiscoverySeeds(sources: List<YouTubeSource>, history: List<StreamPlayHistoryItem>, followedArtists: List<FollowedStreamArtist> = emptyList()): List<String> {
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val globalDiscoverySeeds = listOf(
        "youtube music global top songs $year",
        "hot music right now official audio",
        "trending songs right now official audio",
        "new music releases this week official audio",
        "global charts music top 15",
        "rising songs this week music",
        "top songs worldwide official audio",
        "youtube music charts global",
    )
    val sourceArtists = sources
        .filter { it.reviewState == YouTubeReviewState.Accepted && it.reaction != YouTubeReaction.Disliked }
        .sortedWith(compareByDescending<YouTubeSource> { it.reaction == YouTubeReaction.Liked }.thenByDescending { it.playCount }.thenByDescending { it.lastPlayedMillis })
        .map { it.author.cleanStreamArtistName() }
    val historyArtists = history.map { it.author.cleanStreamArtistName() }
    val topArtists = (sourceArtists + historyArtists)
        .filter {
            it.length >= 2 &&
                !it.equals(LEGACY_YOUTUBE_SOURCE_NAME, ignoreCase = true) &&
                !it.equals(PREMIUMDECK_SOURCE_NAME, ignoreCase = true)
        }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(6)
    val likedTitles = sources
        .filter { it.reaction == YouTubeReaction.Liked }
        .sortedByDescending { it.lastPlayedMillis }
        .map { it.title.cleanStreamTitle() }
        .take(4)
    return buildList {
        globalDiscoverySeeds.forEach(::add)
        followedArtists.take(10).forEach { artist ->
            val name = artist.officialName.ifBlank { artist.name }
            add("$name album official audio")
            add("$name full album tracks")
            add("$name latest album")
            add("$name topic album")
        }
        topArtists.forEach { artist ->
            add("$artist official audio")
            add("$artist album")
            add("$artist topic")
            add("$artist mix")
        }
        topArtists.take(4).forEachIndexed { index, artist ->
            likedTitles.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { title -> add("$artist $title") }
        }
    }.distinctBy { it.normalizedSearchText() }.take(16)
}

internal suspend fun fetchStreamNewReleaseSnapshot(
    artists: List<FollowedStreamArtist>,
    sources: List<YouTubeSource>,
): StreamNewReleaseSnapshot {
    val followed = artists.filter { it.key.isNotBlank() }.distinctBy { it.key }.take(20)
    if (followed.isEmpty()) return StreamNewReleaseSnapshot(savedMillis = System.currentTimeMillis())
    val localKeys = sources.map { it.streamDistinctKey() }.toSet()
    val existingByKey = sources.associateBy { it.streamDistinctKey() }
    val results = buildList {
        followed.forEach { artist ->
            streamNewReleaseSearchSeeds(artist).take(3).forEach { seed ->
                val response = runCatching { withTimeoutOrNull(5200L) { searchYouTubeVideos(seed) } }
                    .getOrNull()
                    ?: YouTubeSearchResponse()
                response.results
                    .filter { it.isLikelyNewReleaseFor(artist) }
                    .filter { it.wasUploadedWithinLastMonth() }
                    .map { result ->
                        val existing = existingByKey[result.source.streamDistinctKey()]
                        if (existing != null) result.copy(source = existing) else result
                    }
                    .take(6)
                    .forEach(::add)
            }
        }
    }
        .sortedWith(
            compareByDescending<YouTubeSearchResult> { if (it.source.streamDistinctKey() !in localKeys) 1 else 0 }
                .thenByDescending { it.score }
                .thenByDescending { it.cachedMillis },
        )
        .distinctBy { it.videoId.ifBlank { it.source.streamDistinctKey() } }
        .take(80)
    return StreamNewReleaseSnapshot(
        savedMillis = System.currentTimeMillis(),
        followedArtistKeys = followed.map { it.key }.toSet(),
        results = results,
    )
}

internal fun streamNewReleaseSearchSeeds(artist: FollowedStreamArtist, nowMillis: Long = System.currentTimeMillis()): List<String> {
    val year = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
    val official = artist.officialName.ifBlank { artist.name }
    return listOf(
        "$official new release $year official audio",
        "$official latest single $year",
        "$official official music video this month",
        "$official topic newest",
    ).distinctBy { it.normalizedSearchText() }
}

internal fun YouTubeSearchResult.isLikelyNewReleaseFor(artist: FollowedStreamArtist): Boolean {
    val sourceArtistKey = source.artistKey()
    val titleKey = source.title.normalizedSearchText()
    val officialSource = source.matchesFollowedOfficialArtist(artist)
    val titleMentioned = titleKey.contains(artist.key) || titleKey.contains(artist.officialKey)
    if (!officialSource && !(artist.allowsLabelReleasedMusic() && titleMentioned)) return false
    val duration = durationMillis.takeIf { it > 0L } ?: source.durationMillis
    if (duration > 0L && duration !in 90_000L..600_000L) return false
    val text = "${source.title} ${source.author} ${matchReason.orEmpty()}".lowercase(Locale.US)
    val reject = listOf("reaction", "interview", "podcast", "karaoke", "cover", "tutorial", "lesson", "full album", "hour", "mix", "live stream")
    if (reject.any { text.contains(it) }) return false
    val releaseHints = listOf("official audio", "official music video", "visualizer", "lyrics", "lyric video", "topic", "single", "premiere", "new")
    return sourceArtistKey == artist.officialKey ||
        officialSource ||
        score >= 80 ||
        releaseHints.any { text.contains(it) }
}

internal fun YouTubeSearchResult.wasUploadedWithinLastMonth(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val uploadedMillis = parsePremiumDeckUploadDateMillis(uploadedDate, nowMillis) ?: return false
    return uploadedMillis in (nowMillis - STREAM_NEW_RELEASE_MAX_AGE_MILLIS)..(nowMillis + 24L * 60L * 60L * 1000L)
}

internal fun parsePremiumDeckUploadDateMillis(rawDate: String, nowMillis: Long = System.currentTimeMillis()): Long? {
    val raw = rawDate.trim()
    if (raw.isBlank()) return null
    val lower = raw.lowercase(Locale.US)
    if (lower == "today" || lower == "just now" || lower == "now") return nowMillis
    if (lower == "yesterday") return nowMillis - 24L * 60L * 60L * 1000L

    raw.toLongOrNull()?.let { numeric ->
        return when {
            numeric > 1_000_000_000_000L -> numeric
            numeric > 1_000_000_000L -> numeric * 1000L
            else -> null
        }
    }

    Regex("""(?i)\b(\d+)\s*(second|minute|hour|day|week|month|year)s?(?:\s+ago)?\b""")
        .find(raw)
        ?.let { match ->
            val amount = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
            val unit = match.groupValues.getOrNull(2)?.lowercase(Locale.US).orEmpty()
            val millis = when (unit) {
                "second" -> amount * 1000L
                "minute" -> amount * 60L * 1000L
                "hour" -> amount * 60L * 60L * 1000L
                "day" -> amount * 24L * 60L * 60L * 1000L
                "week" -> amount * 7L * 24L * 60L * 60L * 1000L
                "month" -> amount * 30L * 24L * 60L * 60L * 1000L
                "year" -> amount * 365L * 24L * 60L * 60L * 1000L
                else -> return null
            }
            return nowMillis - millis
        }

    val cleaned = raw.replace(Regex("""(?i)^(premiered|published|uploaded|released|streamed)\s+"""), "")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "MMM d, yyyy",
        "MMM d yyyy",
        "MMMM d, yyyy",
        "MMMM d yyyy",
        "d MMM yyyy",
        "d MMMM yyyy",
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(cleaned)?.time
        }.getOrNull()
    }
}

private fun YouTubeSource.matchesFollowedOfficialArtist(artist: FollowedStreamArtist): Boolean {
    val sourceKey = author.cleanStreamArtistName().normalizedSearchText()
    val sourceCompact = sourceKey.compactStreamArtistKey()
    val channelKey = channelIdentityKey()
    val officialKeys = listOf(artist.officialKey, artist.key, artist.officialName.normalizedSearchText(), artist.name.normalizedSearchText())
        .filter { it.isNotBlank() }
        .distinct()
    val displayMatches = officialKeys.any { key ->
        sourceKey == key ||
            sourceCompact == key.compactStreamArtistKey() ||
            isOfficialStreamArtistName(author, key)
    }
    if (!displayMatches) return false
    if (artist.allowsLabelReleasedMusic()) return true
    val requiredChannelKey = artist.requiredOfficialChannelKey()
    if (requiredChannelKey.isNotBlank()) {
        return channelKey.isNotBlank() && channelKey == requiredChannelKey
    }
    return officialKeys.any { key -> isOfficialStreamArtistName(channelTitle, key) } ||
        (channelVerified && officialKeys.any { key -> channelTitle.cleanStreamArtistName().normalizedSearchText() == key })
}

private fun YouTubeSource.channelIdentityKey(): String =
    channelUrl.youtubeChannelIdentityKey()

private fun FollowedStreamArtist.requiredOfficialChannelKey(): String =
    officialChannelKey.ifBlank { officialChannelUrl.youtubeChannelIdentityKey() }

internal fun String.youtubeChannelIdentityKey(): String {
    val raw = trim()
    if (raw.isBlank()) return ""
    val normalized = when {
        raw.startsWith("@") -> "https://www.youtube.com/$raw"
        raw.startsWith("/", ignoreCase = true) -> "https://www.youtube.com$raw"
        raw.startsWith("youtube.com", ignoreCase = true) -> "https://$raw"
        raw.startsWith("www.youtube.com", ignoreCase = true) -> "https://$raw"
        else -> raw
    }
    listOf("/channel/", "/@", "/c/", "/user/").forEach { marker ->
        val index = normalized.indexOf(marker, ignoreCase = true)
        if (index >= 0) {
            return "channel:${normalized.substring(index + marker.length).substringBefore('/').substringBefore('?').normalizedSearchText()}"
        }
    }
    val detection = detectYouTubeSource(normalized)
    if (detection?.kind == YouTubeSourceKind.Channel) return "channel:${detection.sourceId.normalizedSearchText()}"
    val uri = runCatching { Uri.parse(normalized) }.getOrNull()
    val path = uri?.path.orEmpty().trim('/')
    return path.takeIf { it.isNotBlank() }?.normalizedSearchText()?.let { "channel:$it" }.orEmpty()
}

private fun FollowedStreamArtist.allowsLabelReleasedMusic(): Boolean =
    listOf(name, officialName, key, officialKey).any { it.hasEthiopianMusicMarker() }

private fun String.hasEthiopianMusicMarker(): Boolean {
    val normalized = lowercase(Locale.US)
    return any { it in '\u1200'..'\u137F' } ||
        listOf("ethiopia", "ethiopian", "amharic", "oromo", "tigrigna", "tigray", "habesha", "addis").any { normalized.contains(it) }
}

private fun isOfficialStreamArtistName(rawName: String, artistKey: String): Boolean {
    val cleanedKey = rawName.cleanStreamArtistName().normalizedSearchText()
    val targetCompact = artistKey.compactStreamArtistKey()
    if (cleanedKey == artistKey) return true
    if (targetCompact.isBlank()) return false
    val raw = rawName.lowercase(Locale.US)
    val compact = cleanedKey.compactStreamArtistKey()
    return compact == targetCompact ||
        (compact.contains(targetCompact) && (raw.contains("vevo") || raw.contains("topic") || raw.contains("official")))
}

private fun String.compactStreamArtistKey(): String =
    cleanStreamArtistName().normalizedSearchText().replace(" ", "")

internal fun buildFollowedArtistNewReleaseCollection(
    artists: List<FollowedStreamArtist>,
    results: List<YouTubeSearchResult>,
    savedSources: List<YouTubeSource>,
): StreamCollection? {
    if (artists.isEmpty()) return null
    val followedKeys = artists.map { it.key }.toSet()
    val artistByKey = artists.flatMap { artist -> listOf(artist.key to artist, artist.officialKey to artist) }.toMap()
    val savedByKey = savedSources.associateBy { it.streamDistinctKey() }
    val sources = results
        .filter { result -> artistByKey.values.any { artist -> result.isLikelyNewReleaseFor(artist) && result.wasUploadedWithinLastMonth() } }
        .map { result -> savedByKey[result.source.streamDistinctKey()] ?: result.source.copy(reviewState = YouTubeReviewState.Accepted) }
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        .distinctBy { it.streamDistinctKey() }
        .take(30)
    if (sources.isEmpty()) return null
    val artistLabel = if (artists.size == 1) artists.first().name else "${artists.size} followed artists"
    return StreamCollection(
        id = "followed-new-releases-${followedKeys.sorted().joinToString("-").hashCode()}",
        title = "From Artists You Follow",
        subtitle = "New PremiumDeck releases from $artistLabel",
        kind = StreamCollectionKind.Playlist,
        sources = sources,
        accentColor = accentForKey("artists you follow ${followedKeys.joinToString()}"),
        canSave = true,
    )
}

internal fun buildStreamReleaseNotifications(
    artists: List<FollowedStreamArtist>,
    results: List<YouTubeSearchResult>,
    savedSources: List<YouTubeSource>,
    readNotificationIds: Set<String>,
    nowMillis: Long = System.currentTimeMillis(),
): List<StreamReleaseNotification> {
    val followed = artists.filter { it.key.isNotBlank() }.distinctBy { it.key }
    if (followed.isEmpty() || results.isEmpty()) return emptyList()
    val savedByKey = savedSources.associateBy { it.streamDistinctKey() }
    return results
        .flatMap { result ->
            followed
                .filter { artist -> result.isLikelyNewReleaseFor(artist) && result.wasUploadedWithinLastMonth(nowMillis) }
                .map { artist ->
                    val source = savedByKey[result.source.streamDistinctKey()]
                        ?: result.source.copy(
                            reviewState = YouTubeReviewState.Accepted,
                            thumbnailUrl = result.source.bestThumbnailUrl() ?: result.source.thumbnailUrl,
                        )
                    val notificationId = streamReleaseNotificationId(artist.key, result)
                    StreamReleaseNotification(
                        id = notificationId,
                        artistName = artist.name.ifBlank { artist.officialName },
                        artistKey = artist.key,
                        title = source.title.ifBlank { "New PremiumDeck release" },
                        activityLabel = result.streamReleaseActivityLabel(),
                        uploadedDate = result.uploadedDate,
                        source = source,
                        result = result.copy(source = source),
                        read = notificationId in readNotificationIds,
                    )
                }
        }
        .distinctBy { it.id }
        .sortedWith(
            compareBy<StreamReleaseNotification> { it.read }
                .thenByDescending { parsePremiumDeckUploadDateMillis(it.uploadedDate, nowMillis) ?: it.result.cachedMillis }
                .thenByDescending { it.result.score },
        )
        .take(80)
}

internal fun streamReleaseNotificationId(artistKey: String, result: YouTubeSearchResult): String {
    val releaseKey = result.videoId
        .ifBlank { result.source.streamDistinctKey() }
        .ifBlank { "${result.source.title}|${result.source.author}" }
        .normalizedSearchText()
    val artist = artistKey.normalizedSearchText().ifBlank { "artist" }
    return "release:$artist:$releaseKey"
}

private fun YouTubeSearchResult.streamReleaseActivityLabel(): String {
    val text = "${source.title} ${source.author} ${matchReason.orEmpty()}".lowercase(Locale.US)
    return when {
        text.contains("music video") || text.contains("official video") -> "New music video"
        text.contains("visualizer") -> "New visualizer"
        text.contains("lyric") -> "New lyric video"
        else -> "New song"
    }
}

internal fun buildPremiumDeckDiscoveryCollections(
    discoveryResults: List<YouTubeSearchResult>,
    newReleaseCollection: StreamCollection?,
): List<StreamCollection> {
    val followedArtistLane = listOfNotNull(newReleaseCollection)
    val youtubeExploreCollections = discoveryResults
        .mapNotNull { result ->
            val tag = result.source.youtubeExploreTag() ?: return@mapNotNull null
            tag to result.source.copy(
                reviewState = YouTubeReviewState.Accepted,
                thumbnailUrl = result.source.bestThumbnailUrl() ?: result.source.thumbnailUrl,
            )
        }
        .groupBy({ it.first.id }, { it })
        .mapNotNull { (id, taggedSources) ->
            val tag = taggedSources.firstOrNull()?.first ?: return@mapNotNull null
            val sources = taggedSources
                .map { it.second }
                .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
                .sortedWith(
                    compareBy<YouTubeSource> { it.albumTrackNumberHint.takeIf { rank -> rank > 0 } ?: Int.MAX_VALUE }
                        .thenBy { it.title.normalizedSearchText() },
                )
                .distinctBy { it.streamDistinctKey() }
                .take(STREAM_MIX_CONTEXT_LIMIT)
            if (sources.isEmpty()) return@mapNotNull null
            val subtitle = listOf(
                tag.subtitle,
                tag.shelfTitle.takeIf { it.isNotBlank() && !it.equals(tag.title, ignoreCase = true) }.orEmpty(),
            )
                .filter { it.isNotBlank() }
                .distinctBy { it.normalizedSearchText() }
                .joinToString("  |  ")
                .ifBlank { "YouTube Music Explore" }
            StreamCollection(
                id = "youtube-explore-$id",
                title = tag.title,
                subtitle = subtitle,
                kind = StreamCollectionKind.Playlist,
                sources = sources,
                accentColor = accentForKey("youtube music explore $id"),
                canSave = true,
            )
        }
    val chartSourcesById = discoveryResults
        .mapNotNull { result ->
            val chartId = result.source.premiumDeckChartId() ?: return@mapNotNull null
            chartId to result.source.copy(
                reviewState = YouTubeReviewState.Accepted,
                thumbnailUrl = result.source.bestThumbnailUrl() ?: result.source.thumbnailUrl,
            )
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, sources) ->
            sources
                .sortedWith(
                    compareBy<YouTubeSource> { it.albumTrackNumberHint.takeIf { rank -> rank > 0 } ?: Int.MAX_VALUE }
                        .thenBy { it.title.normalizedSearchText() },
                )
                .distinctBy { it.streamDistinctKey() }
        }
    val hasChartSources = chartSourcesById.values.any { it.isNotEmpty() }

    fun globalCollection(
        id: String,
        title: String,
        subtitle: String,
        sources: List<YouTubeSource>,
        canSave: Boolean = true,
        allowEmpty: Boolean = false,
    ): StreamCollection? {
        val queue = sources.distinctBy { it.streamDistinctKey() }.take(STREAM_MIX_CONTEXT_LIMIT)
        if (queue.isEmpty() && !allowEmpty) return null
        return StreamCollection(
            id = id,
            title = title,
            subtitle = subtitle,
            kind = StreamCollectionKind.Playlist,
            sources = queue,
            accentColor = accentForKey(id),
            canSave = canSave && queue.isNotEmpty(),
        )
    }

    val chartHot = chartSourcesById["global-hot-right-now"].orEmpty()
    val chartNewReleases = chartSourcesById["global-new-releases"].orEmpty()
    val chartTrending = chartSourcesById["global-trending-music"].orEmpty()
    val chartGlobal = chartSourcesById["global-charts"].orEmpty()
    val chartRising = chartSourcesById["rising-this-week"].orEmpty()
    val chartCollections = listOfNotNull(
        globalCollection(
            id = "global-hot-right-now",
            title = "Hot Right Now",
            subtitle = if (chartHot.isNotEmpty()) "Top 15 from public music charts, resolved on YouTube" else "Loading public chart tracklist",
            sources = chartHot,
            allowEmpty = hasChartSources,
        ),
        globalCollection(
            id = "global-new-releases",
            title = "New Releases",
            subtitle = if (chartNewReleases.isNotEmpty()) "Fresh charting songs with recent release dates" else "Fresh global music drops and official audio",
            sources = chartNewReleases,
            allowEmpty = hasChartSources,
        ),
        globalCollection(
            id = "global-trending-music",
            title = "Trending Music",
            subtitle = if (chartTrending.isNotEmpty()) "Music moving across multiple markets" else "Loading public chart tracklist",
            sources = chartTrending,
            allowEmpty = hasChartSources,
        ),
        globalCollection(
            id = "global-charts",
            title = "Global Charts",
            subtitle = if (chartGlobal.isNotEmpty()) "Multi-market public chart ranking, resolved on YouTube" else "Loading public chart tracklist",
            sources = chartGlobal,
            allowEmpty = hasChartSources,
        ),
        globalCollection(
            id = "rising-this-week",
            title = "Rising This Week",
            subtitle = if (chartRising.isNotEmpty()) "Fresh regional chart breakouts" else "Loading public chart tracklist",
            sources = chartRising,
            allowEmpty = hasChartSources,
        ),
    )

    return (followedArtistLane + youtubeExploreCollections + chartCollections)
        .distinctBy { it.id }
        .take(YOUTUBE_EXPLORE_COLLECTION_LIMIT)
}

internal fun buildSearchDiscoveryGenreCollections(
    genres: List<StreamDiscoveryGenre> = premiumDeckSearchDiscoveryGenres,
    candidates: List<YouTubeSource>,
): List<StreamCollection> =
    genres.map { genre -> buildSearchDiscoveryGenreCollection(genre, candidates) }

internal fun buildSearchDiscoveryGenreCollection(
    genre: StreamDiscoveryGenre,
    candidates: List<YouTubeSource>,
): StreamCollection {
    val genreSources = candidates
        .asSequence()
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked && !it.isPodcast }
        .filter { it.durationMillis <= 0L || it.durationMillis <= 8L * 60L * 1000L }
        .map { it.copy(reviewState = YouTubeReviewState.Accepted, thumbnailUrl = it.bestThumbnailUrl() ?: it.thumbnailUrl) }
        .filter { sourceMatchesDiscoveryGenre(it, genre) }
        .distinctBy { it.streamDistinctKey() }
        .take(SEARCH_DISCOVERY_GENRE_PREVIEW_LIMIT)
        .toList()
    return StreamCollection(
        id = "search-discovery-${genre.id}",
        title = genre.title,
        subtitle = genre.subtitle,
        kind = StreamCollectionKind.Playlist,
        sources = genreSources,
        accentColor = genre.accentColor,
        canSave = genreSources.isNotEmpty(),
    )
}

internal suspend fun fetchSearchDiscoveryGenreCollection(
    genre: StreamDiscoveryGenre,
    savedSources: List<YouTubeSource>,
): StreamCollection {
    val fetched = mutableListOf<YouTubeSource>()
    val playlistCards = genre.seedQueries
        .take(SEARCH_DISCOVERY_GENRE_QUERY_LIMIT)
        .flatMap { query -> searchYouTubeDiscoveryPlaylistCards(query, genre) }
        .distinctBy { it.card.playlistId }
        .sortedByDescending { it.score }
        .take(3)

    for ((index, playlist) in playlistCards.withIndex()) {
        val tag = youtubeExploreStorageTag(
            id = "search-discovery-${genre.id}-${playlist.card.id}",
            title = playlist.card.title.ifBlank { genre.title },
            subtitle = playlist.card.subtitle.ifBlank { genre.subtitle },
            shelfTitle = genre.title,
            coverUrl = playlist.card.thumbnailUrl.orEmpty(),
        )
        fetched += fetchYouTubeExplorePlaylistSources(playlist.card, savedSources, tag)
            .filterSearchDiscoveryPreviewSource()
        if (fetched.distinctBy { it.streamDistinctKey() }.size >= SEARCH_DISCOVERY_GENRE_PREVIEW_LIMIT) break
        if (index < playlistCards.lastIndex) kotlinx.coroutines.delay(90L)
    }

    if (fetched.distinctBy { it.streamDistinctKey() }.size < 8) {
        for ((index, query) in genre.seedQueries.take(SEARCH_DISCOVERY_GENRE_QUERY_LIMIT).withIndex()) {
            val response = withTimeoutOrNull(6200L) { searchYouTubeVideos(query) } ?: YouTubeSearchResponse()
            fetched += response.results
                .map { it.source }
                .filterSearchDiscoveryPreviewSource()
            if (index < SEARCH_DISCOVERY_GENRE_QUERY_LIMIT - 1) kotlinx.coroutines.delay(110L)
        }
    }

    val sources = fetched
        .map { source ->
            source.copy(
                reviewState = YouTubeReviewState.Accepted,
                thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
            )
        }
        .distinctBy { it.streamDistinctKey() }
        .take(SEARCH_DISCOVERY_GENRE_PREVIEW_LIMIT)

    return searchDiscoveryGenreCollectionFromSources(genre, sources)
}

private fun searchDiscoveryGenreCollectionFromSources(
    genre: StreamDiscoveryGenre,
    sources: List<YouTubeSource>,
): StreamCollection =
    StreamCollection(
        id = "search-discovery-${genre.id}",
        title = genre.title,
        subtitle = genre.subtitle,
        kind = StreamCollectionKind.Playlist,
        sources = sources,
        accentColor = genre.accentColor,
        canSave = sources.isNotEmpty(),
    )

private suspend fun searchYouTubeDiscoveryPlaylistCards(
    query: String,
    genre: StreamDiscoveryGenre,
): List<SearchDiscoveryPlaylistCard> =
    coroutineScope {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        if (encodedQuery.isBlank()) return@coroutineScope emptyList()
        activePipedApiEndpoints()
            .take(5)
            .map { endpoint ->
                async {
                    runCatching {
                        val json = httpJson("$endpoint/search?q=$encodedQuery&filter=playlists", connectTimeout = 1500, readTimeout = 3000)
                            ?: return@runCatching emptyList()
                        parseSearchDiscoveryPlaylistCards(json, genre)
                    }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { it.card.playlistId.ifBlank { it.card.id } }
            .sortedByDescending { it.score }
    }

private fun parseSearchDiscoveryPlaylistCards(
    json: JSONObject,
    genre: StreamDiscoveryGenre,
): List<SearchDiscoveryPlaylistCard> {
    val items = json.optJSONArray("items") ?: json.optJSONArray("results") ?: return emptyList()
    return buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val rawUrl = item.optString("url")
            val normalizedUrl = normalizeYouTubeSearchUrl(rawUrl) ?: continue
            val playlistId = detectYouTubeSource(normalizedUrl)
                ?.takeIf { it.kind == YouTubeSourceKind.Playlist }
                ?.sourceId
                .orEmpty()
            if (playlistId.isBlank()) continue
            val title = item.optString("name")
                .ifBlank { item.optString("title") }
                .ifBlank { genre.title }
            val subtitle = item.optString("uploaderName")
                .ifBlank { item.optString("uploader") }
                .ifBlank { item.optString("author") }
                .ifBlank { "YouTube playlist" }
            val thumbnail = normalizeYouTubeThumbnailUrl(
                item.optString("thumbnail").ifBlank { item.optString("thumbnailUrl") },
            )
            val normalizedTitle = title.normalizedSearchText()
            val normalizedSubtitle = subtitle.normalizedSearchText()
            val genreText = "${genre.title} ${genre.subtitle} ${genre.seedQueries.joinToString(" ")}".normalizedSearchText()
            val exactTitleBonus = if (normalizedTitle == genre.title.normalizedSearchText()) 80 else 0
            val titleTokenBonus = genreText.split(" ")
                .filter { it.length >= 4 && it !in genericDiscoveryGenreTokens }
                .count { token -> normalizedTitle.contains(token) }
                .coerceAtMost(6) * 10
            val officialBonus = when {
                normalizedSubtitle.contains("youtube music") -> 60
                normalizedSubtitle.contains("youtube") -> 30
                else -> 0
            }
            add(
                SearchDiscoveryPlaylistCard(
                    card = YouTubeExploreCard(
                        id = youtubeExploreStableId("search-discovery-${genre.id}", title, playlistId),
                        title = title,
                        subtitle = subtitle,
                        contentId = playlistId,
                        playlistId = playlistId,
                        videoId = "",
                        thumbnailUrl = thumbnail,
                        shelfTitle = genre.title,
                        shelfRank = 0,
                        cardRank = index,
                    ),
                    score = 1_000 - index + exactTitleBonus + titleTokenBonus + officialBonus,
                ),
            )
        }
    }
}

private fun List<YouTubeSource>.filterSearchDiscoveryPreviewSource(): List<YouTubeSource> =
    asSequence()
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked && !it.isPodcast }
        .filter { it.durationMillis <= 0L || it.durationMillis <= 8L * 60L * 1000L }
        .toList()

private fun sourceMatchesDiscoveryGenre(source: YouTubeSource, genre: StreamDiscoveryGenre): Boolean {
    val sourceText = "${source.title} ${source.author} ${source.channelTitle} ${source.albumTitleHint}".normalizedSearchText()
    val genreTokens = (listOf(genre.title, genre.subtitle) + genre.seedQueries)
        .flatMap { it.normalizedSearchText().split(" ") }
        .map { it.trim() }
        .filter { it.length >= 3 && it !in genericDiscoveryGenreTokens }
        .toSet()
    if (genreTokens.isEmpty()) return false
    if (genreTokens.any { sourceText.contains(it) }) return true
    val titleArtistTokens = "${source.title} ${source.author}".normalizedSearchText()
        .split(" ")
        .filter { it.length >= 3 }
        .toSet()
    return genreTokens.intersect(titleArtistTokens).size >= 2
}

private val genericDiscoveryGenreTokens = setOf(
    "official",
    "audio",
    "music",
    "songs",
    "playlist",
    "youtube",
    "tracks",
    "with",
    "and",
    "new",
    "hits",
)

internal fun buildStreamBrowseModel(
    sources: List<YouTubeSource>,
    playlists: List<YouTubePlaylist>,
    playHistory: List<StreamPlayHistoryItem>,
    discoveryResults: List<YouTubeSearchResult>,
    visibleSources: List<YouTubeSource>,
    followedArtists: List<FollowedStreamArtist> = emptyList(),
    personalizationProfile: UserPreferenceProfile = UserPreferenceProfile(),
    mixSessionSeed: Long = 0L,
): StreamBrowseModel {
    val accepted = sources.filter { it.reviewState == YouTubeReviewState.Accepted && it.kind == YouTubeSourceKind.Video }
    val playable = accepted.filter { it.reaction != YouTubeReaction.Disliked }
    val byId = sources.associateBy { it.id }
    val historicalSources = playHistory.mapNotNull { item -> byId[item.sourceId] ?: item.toYouTubeSource() }
    val discoverySources = discoveryResults
        .map { it.source.copy(reviewState = YouTubeReviewState.Accepted) }
        .filter { it.kind == YouTubeSourceKind.Video }
    val candidates = (playable + historicalSources + discoverySources)
        .filter { it.reaction != YouTubeReaction.Disliked }
        .distinctBy { it.streamDistinctKey() }
    val recent = (playable.sortedByDescending { max(it.lastPlayedMillis, it.addedMillis) } + historicalSources.sortedByDescending { it.lastPlayedMillis })
        .distinctBy { it.streamDistinctKey() }
        .take(12)
    val mixes = buildStreamMixes(playable, discoverySources, playlists, playHistory, mixSessionSeed)
    val albums = rankAlbumLikeCollections(
        albums = buildAlbumLikeCollections(playable, discoverySources),
        followedArtists = followedArtists,
        profile = personalizationProfile,
        playHistory = playHistory,
    )
    val artists = buildArtistCollections(candidates)
    return StreamBrowseModel(
        recentSources = recent,
        mixes = mixes.take(6),
        albums = albums.take(8),
        artists = artists.take(10),
        filteredSources = visibleSources.filter { it.reaction != YouTubeReaction.Disliked || it.reviewState == YouTubeReviewState.Accepted },
    )
}

internal fun buildStreamMixes(
    localSources: List<YouTubeSource>,
    discoverySources: List<YouTubeSource>,
    playlists: List<YouTubePlaylist>,
    playHistory: List<StreamPlayHistoryItem> = emptyList(),
    mixSessionSeed: Long = 0L,
): List<StreamCollection> {
    val candidates = (localSources + discoverySources).distinctBy { it.streamDistinctKey() }
    val liked = localSources.filter { it.reaction == YouTubeReaction.Liked || it.bookmarked }
    val heavy = localSources.filter { it.playCount > 0 }.sortedWith(compareByDescending<YouTubeSource> { it.playCount }.thenByDescending { it.lastPlayedMillis })
    val saved = localSources.filter { it.bookmarked || it.downloadState == YouTubeDownloadState.Downloaded || it.status == YouTubeSourceStatus.Downloaded }
    return buildList {
        addVirtualMix("Heavy Rotation", "Generated from your most replayed stream queue", StreamCollectionKind.Mix, heavy, candidates, localSources, playHistory, mixSessionSeed)
        addVirtualMix("Liked Radio", "Generated from likes, bookmarks, and nearby discoveries", StreamCollectionKind.Mix, liked, candidates, localSources, playHistory, mixSessionSeed)
        addVirtualMix("Saved Stream Mix", "Generated from offline and bookmarked sources", StreamCollectionKind.Mix, saved, candidates, localSources, playHistory, mixSessionSeed)
        addVirtualMix("Fresh Finds", "Generated discoveries from your artists", StreamCollectionKind.Mix, discoverySources, candidates, localSources, playHistory, mixSessionSeed)
        playlists.take(3).forEach { playlist ->
            val playlistSources = playlist.sourceIds.mapNotNull { id -> localSources.firstOrNull { it.id == id } }
            addVirtualMix(
                title = "${playlist.title} Radio",
                subtitle = playlist.description.ifBlank { "Generated from this playlist's artists and titles" },
                kind = StreamCollectionKind.Mix,
                seedSources = playlistSources,
                candidates = candidates,
                localSources = localSources,
                playHistory = playHistory,
                mixSessionSeed = mixSessionSeed,
                playlistSourceIds = playlist.sourceIds.toSet(),
            )
        }
        topArtistGroups(localSources, candidates).take(4).forEach { (artist, artistSources) ->
            addVirtualMix("$artist Mix", "Generated songs and discoveries around $artist", StreamCollectionKind.Artist, artistSources, candidates, localSources, playHistory, mixSessionSeed)
        }
    }.distinctBy { it.id }
}

private fun MutableList<StreamCollection>.addVirtualMix(
    title: String,
    subtitle: String,
    kind: StreamCollectionKind,
    seedSources: List<YouTubeSource>,
    candidates: List<YouTubeSource>,
    localSources: List<YouTubeSource>,
    playHistory: List<StreamPlayHistoryItem>,
    mixSessionSeed: Long,
    playlistSourceIds: Set<String> = emptySet(),
) {
    val queue = mixSources(
        seedSources = seedSources,
        candidates = candidates,
        localSources = localSources,
        playHistory = playHistory,
        playlistSourceIds = playlistSourceIds,
        sessionSeed = mixSessionSeed,
    )
    if (queue.isEmpty()) return
    add(
        StreamCollection(
            id = "mix-${title.normalizedSearchText()}-${Math.floorMod(mixSessionSeed, 1_000_000L)}",
            title = title,
            subtitle = subtitle,
            kind = kind,
            sources = queue,
            accentColor = accentForKey(title),
            canSave = true,
        ),
    )
}

internal fun mixSources(
    seedSources: List<YouTubeSource>,
    candidates: List<YouTubeSource>,
    localSources: List<YouTubeSource> = candidates,
    playHistory: List<StreamPlayHistoryItem> = emptyList(),
    playlistSourceIds: Set<String> = emptySet(),
    sessionSeed: Long = 0L,
    targetSize: Int = STREAM_MIX_TARGET_SIZE,
    discoveryLimit: Int = STREAM_MIX_DISCOVERY_LIMIT,
): List<YouTubeSource> {
    val context = streamMixAffinityContext(localSources, playHistory, sessionSeed)
    val cleanSeeds = seedSources
        .filter { it.kind == YouTubeSourceKind.Video }
        .filter { it.reaction != YouTubeReaction.Disliked }
        .filterNot { it.artistKey().isNotBlank() && it.artistKey() in context.dislikedArtistKeys }
        .distinctBy { it.streamDistinctKey() }
        .filter { source ->
            source.streamDistinctKey() in context.localKeys ||
                source.id in playlistSourceIds ||
                streamCandidateTouchesHistory(source, context)
        }
    if (cleanSeeds.isEmpty()) return emptyList()

    val seedKeys = cleanSeeds.map { it.streamDistinctKey() }.toSet()
    val seedArtists = cleanSeeds.map { it.artistKey() }.filter { it.isNotBlank() }.toSet()
    val seedTitleTokenSets = cleanSeeds.map { streamTitleTokens(it.title) }.filter { it.isNotEmpty() }
    val scored = (cleanSeeds + candidates)
        .asSequence()
        .filter { it.kind == YouTubeSourceKind.Video }
        .filter { it.reaction != YouTubeReaction.Disliked }
        .filterNot { it.artistKey().isNotBlank() && it.artistKey() in context.dislikedArtistKeys }
        .distinctBy { it.streamDistinctKey() }
        .mapNotNull { candidate ->
            val key = candidate.streamDistinctKey()
            val isSeed = key in seedKeys
            val relatedToSeed = isSeed ||
                candidate.id in playlistSourceIds ||
                (candidate.artistKey().isNotBlank() && candidate.artistKey() in seedArtists) ||
                streamTitleOverlap(candidate, seedTitleTokenSets) >= 0.24f
            if (!relatedToSeed) return@mapNotNull null
            val baseScore = streamMixCandidateScore(candidate, context, seedKeys, seedArtists, seedTitleTokenSets, playlistSourceIds)
            if (baseScore <= 0f) null else candidate to baseScore
        }
        .sortedWith(
            compareByDescending<Pair<YouTubeSource, Float>> { it.second }
                .thenBy { it.first.title.normalizedSearchText() },
        )
        .toList()

    val limit = targetSize.coerceAtLeast(1)
    val discoveryCap = min(discoveryLimit, max(1, limit / 3))
    val result = mutableListOf<YouTubeSource>()
    var discoveryOnlyCount = 0
    scored.forEach { (source, _) ->
        if (result.size >= limit) return@forEach
        val discoveryOnly = source.streamDistinctKey() !in context.localKeys
        if (discoveryOnly && discoveryOnlyCount >= discoveryCap) return@forEach
        result += source.copy(reviewState = YouTubeReviewState.Accepted, thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl)
        if (discoveryOnly) discoveryOnlyCount += 1
    }
    return result
}

internal fun buildSearchPickedRelatedQueue(
    selected: YouTubeSource,
    searchResults: List<YouTubeSearchResult>,
    savedSources: List<YouTubeSource>,
    discoveryResults: List<YouTubeSearchResult>,
    playHistory: List<StreamPlayHistoryItem> = emptyList(),
    searchQuery: String = "",
    relatedResults: List<YouTubeSearchResult> = emptyList(),
    sessionSeed: Long = 0L,
    targetSize: Int = STREAM_MIX_TARGET_SIZE,
): List<YouTubeSource> {
    val selectedSource = selected.asAcceptedStreamCandidate()
    val selectedKey = selectedSource.streamDistinctKey()
    val selectedArtist = selectedSource.artistKey()
    val savedPlayable = savedSources
        .filter { it.kind == YouTubeSourceKind.Video && it.reviewState == YouTubeReviewState.Accepted }
        .filter { it.reaction != YouTubeReaction.Disliked }
    val savedByKey = savedPlayable.associateBy { it.streamDistinctKey() }
    fun YouTubeSearchResult.toCandidateSource(): YouTubeSource =
        savedByKey[source.streamDistinctKey()] ?: source.asAcceptedStreamCandidate()

    val relatedSources = relatedResults.map { it.toCandidateSource() }
    val searchSources = searchResults.map { it.toCandidateSource() }
    val discoverySources = discoveryResults.map { it.toCandidateSource() }
    val historySources = playHistory.mapNotNull { it.toYouTubeSource()?.asAcceptedStreamCandidate() }
    val relatedKeys = relatedSources.map { it.streamDistinctKey() }.toSet()
    val searchKeys = searchSources.map { it.streamDistinctKey() }.toSet()
    val discoveryKeys = discoverySources.map { it.streamDistinctKey() }.toSet()
    val searchScoreByKey = (searchResults + relatedResults)
        .groupBy { it.source.streamDistinctKey() }
        .mapValues { (_, matches) -> matches.maxOf { it.score } }
    val context = streamMixAffinityContext(savedPlayable, playHistory, sessionSeed)
    val seedTitleTokenSets = listOf(
        streamTitleTokens(selectedSource.title),
        streamTitleTokens("${selectedSource.title} ${selectedSource.author}"),
    ).filter { it.isNotEmpty() }
    val queryTokens = streamTitleTokens(searchQuery)

    val candidates = (relatedSources + discoverySources + savedPlayable + historySources + searchSources)
        .asSequence()
        .filter { it.kind == YouTubeSourceKind.Video }
        .filter { it.reaction != YouTubeReaction.Disliked }
        .filter { selectedSource.isPodcast || !it.isPodcast }
        .filter { candidate -> selectedSource.isPodcast || candidate.durationMillis <= 0L || candidate.durationMillis <= SEARCH_PICK_RELATED_LONG_TRACK_LIMIT_MILLIS }
        .filterNot { it.artistKey().isNotBlank() && it.artistKey() in context.dislikedArtistKeys }
        .filterNot { candidate -> candidate.streamLooksLikeSameRadioSong(selectedSource) }
        .distinctBy { it.streamDistinctKey() }
        .toList()

    fun relatedScore(candidate: YouTubeSource, relaxed: Boolean): Float {
        val key = candidate.streamDistinctKey()
        val artist = candidate.artistKey()
        val candidateTokens = streamTitleTokens("${candidate.title} ${candidate.author}")
        val seedOverlap = streamTitleOverlap(candidate, seedTitleTokenSets)
        val queryOverlap = streamTokenOverlap(candidateTokens, queryTokens)
        val sameArtist = selectedArtist.isNotBlank() && artist == selectedArtist
        val relatedSignal = (key in relatedKeys && !sameArtist) ||
            seedOverlap >= 0.22f ||
            queryOverlap >= 0.34f ||
            (key in searchKeys && (searchScoreByKey[key] ?: 0) >= 100) ||
            (key in discoveryKeys && streamCandidateTouchesHistory(candidate, context))
        if (!relatedSignal && !relaxed) return Float.NEGATIVE_INFINITY
        val mixScore = streamMixCandidateScore(
            candidate = candidate,
            context = context,
            seedKeys = setOf(selectedKey),
            seedArtists = listOf(selectedArtist).filter { it.isNotBlank() }.toSet(),
            seedTitleTokenSets = seedTitleTokenSets,
            playlistSourceIds = emptySet(),
        )
        var score = if (relaxed) 12f else 34f
        if (key in relatedKeys) score += 72f
        if (key in searchKeys) score += 18f
        if (key in discoveryKeys) score += 10f
        if (sameArtist) score -= 34f else score += 24f
        score += seedOverlap * 72f
        score += queryOverlap * 20f
        score += (searchScoreByKey[key] ?: 0).coerceIn(0, 180) / 8f
        score += mixScore.coerceAtLeast(0f) * 0.42f
        score += streamSearchRadioQualityScore(candidate)
        if (sameArtist && key !in relatedKeys && seedOverlap < 0.18f) score -= 24f
        return score + streamMixJitter(sessionSeed, key)
    }

    val scored = candidates
        .mapNotNull { candidate ->
            relatedScore(candidate, relaxed = false)
                .takeIf { it.isFinite() }
                ?.let { score -> candidate to score }
        }
        .sortedWith(
            compareByDescending<Pair<YouTubeSource, Float>> { it.second }
                .thenBy { it.first.title.normalizedSearchText() },
        )
    val relaxed = candidates
        .map { it to relatedScore(it, relaxed = true) }
        .sortedWith(
            compareByDescending<Pair<YouTubeSource, Float>> { it.second }
                .thenBy { it.first.title.normalizedSearchText() },
        )

    val limit = targetSize.coerceAtLeast(1)
    val result = mutableListOf(selectedSource)
    fun appendCandidates(pool: List<Pair<YouTubeSource, Float>>, enforceArtistCap: Boolean) {
        for ((source, _) in pool) {
            if (result.size >= limit) return
            val key = source.streamDistinctKey()
            if (result.any { it.streamDistinctKey() == key }) continue
            val radioKey = source.streamRadioRecommendationKey()
            if (radioKey.isNotBlank() && result.any { it.streamRadioRecommendationKey() == radioKey }) continue
            val sameArtist = selectedArtist.isNotBlank() && source.artistKey() == selectedArtist
            val sameArtistCount = result.count { selectedArtist.isNotBlank() && it.artistKey() == selectedArtist }
            if (enforceArtistCap && sameArtist && sameArtistCount >= SEARCH_PICK_RELATED_SAME_ARTIST_CAP) continue
            result += source.asAcceptedStreamCandidate()
        }
    }
    appendCandidates(scored, enforceArtistCap = true)
    appendCandidates(relaxed, enforceArtistCap = true)
    appendCandidates(scored, enforceArtistCap = false)
    appendCandidates(relaxed, enforceArtistCap = false)
    return result.take(limit)
}

internal fun searchPickedRelatedQueries(selected: YouTubeSource, searchQuery: String): List<String> {
    val artist = selected.author.cleanStreamArtistName()
    val cleanQuery = searchQuery.trim()
    val queryIsExactPick = selected.streamSearchQueryLooksLikePickedSong(cleanQuery)
    return buildList {
        if (cleanQuery.isNotBlank() && !queryIsExactPick) {
            add("$cleanQuery songs official audio")
            add("$cleanQuery music mix")
        }
        if (artist.isNotBlank()) {
            add("artists similar to $artist official audio")
            add("$artist genre similar artists songs")
        }
    }
        .filter { it.normalizedSearchText().length >= 3 }
        .distinctBy { it.normalizedSearchText() }
        .take(3)
}

internal suspend fun fetchSearchPickedRelatedResults(
    selected: YouTubeSource,
    searchQuery: String,
    limit: Int = STREAM_MIX_CONTEXT_LIMIT,
): List<YouTubeSearchResult> = coroutineScope {
    val queries = searchPickedRelatedQueries(selected, searchQuery)
    if (queries.isEmpty()) return@coroutineScope emptyList()
    queries
        .map { query ->
            async {
                withTimeoutOrNull(5200L) { searchYouTubeVideos(query).results }.orEmpty()
            }
        }
        .awaitAll()
        .flatten()
        .filter { it.source.kind == YouTubeSourceKind.Video }
        .distinctBy { it.videoId.ifBlank { it.source.streamDistinctKey() } }
        .take(limit.coerceAtLeast(1))
}

private fun YouTubeSource.asAcceptedStreamCandidate(): YouTubeSource =
    copy(reviewState = YouTubeReviewState.Accepted, thumbnailUrl = bestThumbnailUrl() ?: thumbnailUrl)

private fun streamSearchRadioQualityScore(source: YouTubeSource): Float {
    val text = "${source.title} ${source.author} ${source.channelTitle}".lowercase(Locale.US)
    var score = 0f
    if (text.contains("official audio")) score += 18f
    if (text.contains("official music video")) score += 12f
    if (text.contains("visualizer") || text.contains("lyric")) score += 8f
    if (text.contains("topic") || text.contains("vevo")) score += 8f
    score += when (source.durationMillis) {
        in 120_000L..540_000L -> 18f
        in 1L..119_999L -> -16f
        in 540_001L..SEARCH_PICK_RELATED_LONG_TRACK_LIMIT_MILLIS -> -4f
        else -> 0f
    }
    val penalties = listOf("reaction", "tutorial", "lesson", "karaoke", "podcast", "interview", "full album", "hour", "live stream", "cover dance")
    score -= penalties.count { text.contains(it) } * 20f
    if (text.contains("live") && !text.contains("live session")) score -= 10f
    if (source.reaction == YouTubeReaction.Liked) score += 16f
    if (source.bookmarked) score += 10f
    if (source.downloadState == YouTubeDownloadState.Downloaded || source.status == YouTubeSourceStatus.Downloaded) score += 8f
    return score
}

private fun YouTubeSource.streamLooksLikeSameRadioSong(selected: YouTubeSource): Boolean {
    if (streamDistinctKey() == selected.streamDistinctKey()) return true
    val selectedTitleKeys = selected.streamRadioTitleKeys()
    val candidateTitleKeys = streamRadioTitleKeys()
    val exactTitleMatch = selectedTitleKeys.any { it.isNotBlank() && it in candidateTitleKeys }
    if (exactTitleMatch) return true
    val selectedTokenSets = selectedTitleKeys.map { it.radioSongTokens() }.filter { it.isNotEmpty() }
    val candidateTokenSets = candidateTitleKeys.map { it.radioSongTokens() }.filter { it.isNotEmpty() }
    val overlap = selectedTokenSets.maxOfOrNull { selectedTokens ->
        candidateTokenSets.maxOfOrNull { candidateTokens -> streamTokenOverlap(selectedTokens, candidateTokens) } ?: 0f
    } ?: 0f
    if (overlap < 0.62f) return false
    val sharedArtist = streamRadioArtistKeys().any { it in selected.streamRadioArtistKeys() }
    return sharedArtist || overlap >= 0.84f || hasRadioVersionLanguage()
}

private fun YouTubeSource.streamSearchQueryLooksLikePickedSong(query: String): Boolean {
    val queryTokens = query.radioSongTokens()
    if (queryTokens.isEmpty()) return false
    val titleOverlap = streamRadioTitleKeys()
        .map { it.radioSongTokens() }
        .filter { it.isNotEmpty() }
        .maxOfOrNull { tokens -> streamTokenOverlap(tokens, queryTokens) }
        ?: 0f
    val queryKey = query.normalizedSearchText()
    val artistMentioned = streamRadioArtistKeys().any { artist ->
        artist.isNotBlank() && (queryKey.contains(artist) || streamTokenOverlap(artist.radioSongTokens(), queryTokens) >= 0.72f)
    }
    return titleOverlap >= 0.72f || (artistMentioned && titleOverlap >= 0.42f)
}

private fun YouTubeSource.streamRadioRecommendationKey(): String {
    val titleKey = streamRadioTitleKeys().firstOrNull().orEmpty()
    if (titleKey.isBlank()) return streamDistinctKey()
    val artistKey = streamRadioArtistKeys().firstOrNull { it !in streamRadioGenericArtistKeys }.orEmpty()
    return if (artistKey.isBlank()) "title:$titleKey" else "$artistKey|$titleKey"
}

private fun YouTubeSource.streamRadioTitleKeys(): Set<String> {
    val artistKeys = streamRadioArtistKeys()
    val rawTitle = title.cleanStreamTitle()
    val candidates = mutableListOf(rawTitle)
    val parts = rawTitle.split(Regex("""\s+(?:-|:|\||/|\u2013|\u2014)\s+"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.size >= 2) {
        val left = parts.first()
        val right = parts.drop(1).joinToString(" ")
        if (!right.hasOnlyRadioVersionLanguage()) candidates += right
        if (right.hasOnlyRadioVersionLanguage()) candidates += left
    }
    artistKeys.forEach { artist ->
        if (artist.isBlank()) return@forEach
        val prefixPattern = Regex("""^\s*${Regex.escape(artist)}\s+(?:-|:|\||/|\u2013|\u2014)?\s*""", RegexOption.IGNORE_CASE)
        candidates += rawTitle.normalizedSearchText().replace(prefixPattern, "")
    }
    return candidates
        .map { it.cleanRadioSongTitleText().normalizedSearchText() }
        .map { key -> key.radioSongTokens().joinToString(" ") }
        .filter { it.length >= 3 }
        .filterNot { it in streamRadioGenericTitleKeys }
        .distinct()
        .toSet()
}

private fun YouTubeSource.streamRadioArtistKeys(): Set<String> {
    val leadingTitleArtist = title.cleanStreamTitle()
        .split(Regex("""\s+(?:-|:|\||/|\u2013|\u2014)\s+"""))
        .firstOrNull()
        .orEmpty()
        .takeIf { it.length in 2..48 }
        .orEmpty()
    return listOf(author, channelTitle, leadingTitleArtist)
        .map { it.cleanStreamArtistName().normalizedSearchText() }
        .filter { it.length >= 2 && it !in streamRadioGenericArtistKeys }
        .distinct()
        .toSet()
}

private fun YouTubeSource.hasRadioVersionLanguage(): Boolean =
    "${title} ${author} ${channelTitle}".normalizedSearchText()
        .split(" ")
        .any { it in streamRadioVersionWords }

private fun String.hasOnlyRadioVersionLanguage(): Boolean {
    val tokens = radioSongTokens()
    if (tokens.isEmpty()) return true
    return tokens.all { it in streamRadioVersionWords || it in streamTitleStopWords }
}

private fun String.cleanRadioSongTitleText(): String =
    replace(Regex("""\([^)]*\)|\[[^]]*]""")) { match ->
        if (match.value.normalizedSearchText().split(" ").any { it in streamRadioVersionWords }) " " else match.value
    }
        .replace(Regex("""(?i)\b(?:feat|ft|featuring|with)\b.*$"""), " ")
        .replace(Regex("""(?i)\b(?:official|music|video|audio|lyrics?|lyric|visualizer|remaster(?:ed)?|live|performance|session|cover|karaoke|instrumental|slowed|reverb|sped\s+up|topic|version|edit|hd|4k)\b"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', ':', '|', '/', '"', '\'', '.', ',')

private fun String.radioSongTokens(): Set<String> =
    cleanRadioSongTitleText()
        .normalizedSearchText()
        .split(" ")
        .filter { it.length >= 3 }
        .filter { it !in streamTitleStopWords && it !in streamRadioVersionWords }
        .toSet()

private val streamRadioVersionWords = setOf(
    "official",
    "audio",
    "video",
    "music",
    "lyrics",
    "lyric",
    "visualizer",
    "remaster",
    "remastered",
    "live",
    "performance",
    "session",
    "cover",
    "karaoke",
    "instrumental",
    "slowed",
    "reverb",
    "sped",
    "topic",
    "version",
    "edit",
    "extended",
    "shorts",
)

private val streamRadioGenericArtistKeys = setOf(
    "",
    "lyrics",
    "lyric",
    "music",
    "official",
    "official music",
    "various artists",
    "various",
    "unknown",
    PREMIUMDECK_SOURCE_NAME.normalizedSearchText(),
    LEGACY_YOUTUBE_SOURCE_NAME.normalizedSearchText(),
)

private val streamRadioGenericTitleKeys = setOf(
    "",
    "song",
    "track",
    "official",
    "official audio",
    "official video",
    "music",
    "audio",
    "video",
)

internal fun streamPlaylistSourceIdsAfterRemove(playlist: YouTubePlaylist, sourceId: String): List<String> =
    playlist.sourceIds.filterNot { it == sourceId }

internal fun streamPlaylistSourceIdsAfterAdd(playlist: YouTubePlaylist, source: YouTubeSource): List<String> =
    if (source.id in playlist.sourceIds) playlist.sourceIds else playlist.sourceIds + source.id

internal fun suggestStreamPlaylistAdditions(
    playlist: YouTubePlaylist,
    sources: List<YouTubeSource>,
    discoveryResults: List<YouTubeSearchResult>,
    playHistory: List<StreamPlayHistoryItem>,
    limit: Int = 18,
): List<YouTubeSource> {
    val savedSources = sources.filter { it.kind == YouTubeSourceKind.Video && it.reviewState == YouTubeReviewState.Accepted }
    val discoverySources = discoveryResults.map { it.source.copy(reviewState = YouTubeReviewState.Accepted) }
        .filter { it.kind == YouTubeSourceKind.Video }
    val candidates = (savedSources + discoverySources)
        .filter { it.reaction != YouTubeReaction.Disliked }
        .distinctBy { it.streamDistinctKey() }
    val candidateById = candidates.associateBy { it.id }
    val currentSources = playlist.sourceIds.mapNotNull { candidateById[it] }
    val playlistKeys = currentSources.map { it.streamDistinctKey() }.toSet()
    val context = streamMixAffinityContext(savedSources, playHistory, sessionSeed = 0L)
    val seedArtists = currentSources.map { it.artistKey() }.filter { it.isNotBlank() }.toSet()
    val seedKeys = currentSources.map { it.streamDistinctKey() }.toSet()
    val seedTitleTokenSets = currentSources.map { streamTitleTokens(it.title) }.filter { it.isNotEmpty() }
    return candidates
        .asSequence()
        .filterNot { it.id in playlist.sourceIds || it.streamDistinctKey() in playlistKeys }
        .filterNot { it.artistKey().isNotBlank() && it.artistKey() in context.dislikedArtistKeys }
        .mapNotNull { candidate ->
            val artistKey = candidate.artistKey()
            val relatedToPlaylist = currentSources.isNotEmpty() && (
                (artistKey.isNotBlank() && artistKey in seedArtists) ||
                    streamTitleOverlap(candidate, seedTitleTokenSets) >= 0.24f
                )
            val relatedToHistory = streamCandidateTouchesHistory(candidate, context)
            if (!relatedToPlaylist && !relatedToHistory) return@mapNotNull null
            val score = streamMixCandidateScore(candidate, context, seedKeys, seedArtists, seedTitleTokenSets, playlist.sourceIds.toSet())
            if (score <= 0f) null else candidate to score
        }
        .sortedWith(
            compareByDescending<Pair<YouTubeSource, Float>> { it.second }
                .thenBy { it.first.title.normalizedSearchText() },
        )
        .map { (source, _) -> source.copy(reviewState = YouTubeReviewState.Accepted, thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl) }
        .take(limit.coerceAtLeast(1))
        .toList()
}

private data class StreamMixAffinityContext(
    val localKeys: Set<String>,
    val historySourceIds: Set<String>,
    val likedSourceIds: Set<String>,
    val savedSourceIds: Set<String>,
    val topArtistKeys: Set<String>,
    val likedArtistKeys: Set<String>,
    val savedArtistKeys: Set<String>,
    val dislikedArtistKeys: Set<String>,
    val historyTitleTokenSets: List<Set<String>>,
    val sessionSeed: Long,
)

private fun streamMixAffinityContext(
    localSources: List<YouTubeSource>,
    playHistory: List<StreamPlayHistoryItem>,
    sessionSeed: Long,
): StreamMixAffinityContext {
    val playable = localSources.filter { it.kind == YouTubeSourceKind.Video }
    val historySources = playHistory.take(100).mapNotNull { it.toYouTubeSource() }
    val artistScores = mutableMapOf<String, Int>()
    val positiveArtistKeys = mutableSetOf<String>()
    playable.forEach { source ->
        val artist = source.artistKey().takeIf { it.isNotBlank() } ?: return@forEach
        val score =
            source.playCount.coerceAtMost(12) * 4 +
            (if (source.lastPlayedMillis > 0L) 10 else 0) +
            (if (source.reaction == YouTubeReaction.Liked) 18 else 0) +
            (if (source.bookmarked) 12 else 0) +
            (if (source.downloadState == YouTubeDownloadState.Downloaded || source.status == YouTubeSourceStatus.Downloaded) 10 else 0)
        artistScores[artist] = (artistScores[artist] ?: 0) + score
        if (score > 0) positiveArtistKeys += artist
    }
    historySources.forEachIndexed { index, source ->
        val artist = source.artistKey().takeIf { it.isNotBlank() } ?: return@forEachIndexed
        artistScores[artist] = (artistScores[artist] ?: 0) + (18 - min(index, 15)).coerceAtLeast(2)
        positiveArtistKeys += artist
    }
    val liked = playable.filter { it.reaction == YouTubeReaction.Liked }
    val saved = playable.filter { it.bookmarked || it.downloadState == YouTubeDownloadState.Downloaded || it.status == YouTubeSourceStatus.Downloaded }
    val historySignals = (playable.filter { it.playCount > 0 || it.lastPlayedMillis > 0L || it.reaction == YouTubeReaction.Liked || it.bookmarked } + historySources)
        .distinctBy { it.streamDistinctKey() }
    return StreamMixAffinityContext(
        localKeys = playable.map { it.streamDistinctKey() }.toSet(),
        historySourceIds = (playHistory.map { it.sourceId } + historySignals.map { it.id }).filter { it.isNotBlank() }.toSet(),
        likedSourceIds = liked.map { it.id }.filter { it.isNotBlank() }.toSet(),
        savedSourceIds = saved.map { it.id }.filter { it.isNotBlank() }.toSet(),
        topArtistKeys = artistScores.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(16).map { it.key }.toSet(),
        likedArtistKeys = liked.map { it.artistKey() }.filter { it.isNotBlank() }.toSet(),
        savedArtistKeys = saved.map { it.artistKey() }.filter { it.isNotBlank() }.toSet(),
        dislikedArtistKeys = playable.filter { it.reaction == YouTubeReaction.Disliked }.map { it.artistKey() }.filter { it.isNotBlank() && it !in positiveArtistKeys }.toSet(),
        historyTitleTokenSets = historySignals.map { streamTitleTokens(it.title) }.filter { it.size >= 2 }.take(80),
        sessionSeed = sessionSeed,
    )
}

private fun streamMixCandidateScore(
    candidate: YouTubeSource,
    context: StreamMixAffinityContext,
    seedKeys: Set<String>,
    seedArtists: Set<String>,
    seedTitleTokenSets: List<Set<String>>,
    playlistSourceIds: Set<String>,
): Float {
    val key = candidate.streamDistinctKey()
    val artist = candidate.artistKey()
    var score = 0f
    if (key in seedKeys) score += 92f
    if (candidate.id in playlistSourceIds) score += 30f
    if (candidate.id in context.historySourceIds) score += 22f
    if (candidate.id in context.likedSourceIds || candidate.reaction == YouTubeReaction.Liked) score += 24f
    if (candidate.id in context.savedSourceIds || candidate.bookmarked) score += 16f
    if (candidate.downloadState == YouTubeDownloadState.Downloaded || candidate.status == YouTubeSourceStatus.Downloaded) score += 12f
    if (artist.isNotBlank() && artist in seedArtists) score += 34f
    if (artist.isNotBlank() && artist in context.topArtistKeys) score += 15f
    if (artist.isNotBlank() && artist in context.likedArtistKeys) score += 16f
    if (artist.isNotBlank() && artist in context.savedArtistKeys) score += 12f
    score += candidate.playCount.coerceAtMost(12) * 3f
    if (candidate.lastPlayedMillis > 0L) score += 8f
    val seedOverlap = streamTitleOverlap(candidate, seedTitleTokenSets)
    if (seedOverlap >= 0.24f) score += seedOverlap * 32f
    val historyOverlap = streamTitleOverlap(candidate, context.historyTitleTokenSets)
    if (historyOverlap >= 0.30f) score += historyOverlap * 18f
    if (key !in context.localKeys) score -= 10f
    return score + streamMixJitter(context.sessionSeed, key)
}

private fun streamCandidateTouchesHistory(source: YouTubeSource, context: StreamMixAffinityContext): Boolean {
    val artist = source.artistKey()
    return source.id in context.historySourceIds ||
        source.id in context.likedSourceIds ||
        source.id in context.savedSourceIds ||
        (artist.isNotBlank() && (artist in context.topArtistKeys || artist in context.likedArtistKeys || artist in context.savedArtistKeys)) ||
        streamTitleOverlap(source, context.historyTitleTokenSets) >= 0.30f
}

private fun streamTitleOverlap(source: YouTubeSource, tokenSets: List<Set<String>>): Float {
    val tokens = streamTitleTokens(source.title)
    if (tokens.isEmpty() || tokenSets.isEmpty()) return 0f
    return tokenSets.maxOfOrNull { other -> streamTokenOverlap(tokens, other) } ?: 0f
}

private fun streamTokenOverlap(left: Set<String>, right: Set<String>): Float {
    if (left.isEmpty() || right.isEmpty()) return 0f
    val shared = left.count { it in right }.toFloat()
    return shared / min(left.size, right.size).coerceAtLeast(1)
}

internal fun streamTitleTokens(title: String): Set<String> =
    title.cleanStreamTitle()
        .normalizedSearchText()
        .split(" ")
        .filter { it.length >= 3 && it !in streamTitleStopWords }
        .toSet()

private fun streamMixJitter(sessionSeed: Long, key: String): Int {
    val mixed = key.normalizedSearchText().hashCode().toLong() xor sessionSeed xor (sessionSeed ushr 32)
    return Math.floorMod(mixed, 9L).toInt()
}

private val streamTitleStopWords = setOf(
    "official",
    "audio",
    "video",
    "music",
    "lyrics",
    "lyric",
    "visualizer",
    "remaster",
    "remastered",
    "live",
    "topic",
    "album",
    "mix",
    "feat",
    "ft",
    "the",
    "and",
    "with",
)

private data class StreamAlbumMatch(
    val source: YouTubeSource,
    val artist: String,
    val artistKey: String,
    val albumTitle: String,
    val albumKey: String,
    val trackNumber: Int?,
)

internal fun buildAlbumLikeCollections(localSources: List<YouTubeSource>, discoverySources: List<YouTubeSource>): List<StreamCollection> {
    val matches = (discoverySources + localSources)
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        .mapNotNull { it.strictStreamAlbumMatch() }
    return matches
        .groupBy { "${it.artistKey}|${it.albumKey}" }
        .mapNotNull { (_, group) ->
            val cleanGroup = group
                .distinctBy { it.source.streamDistinctKey() }
                .filter { match -> match.artistKey == group.first().artistKey && match.albumKey == group.first().albumKey }
            if (cleanGroup.size < 2) return@mapNotNull null
            val representative = cleanGroup.maxBy { it.source.playCount + if (it.source.reaction == YouTubeReaction.Liked) 4 else 0 }
            val ordered = cleanGroup
                .sortedWith(
                    compareBy<StreamAlbumMatch> { it.trackNumber ?: 999 }
                        .thenByDescending { it.source.reaction == YouTubeReaction.Liked }
                        .thenByDescending { it.source.playCount }
                        .thenBy { it.source.title.normalizedSearchText() },
                )
                .map { it.source }
                .take(STREAM_ALBUM_COLLECTION_TRACK_LIMIT)
            StreamCollection(
                id = "album-${representative.artistKey}-${representative.albumKey}",
                title = representative.albumTitle,
                subtitle = "${representative.artist} - ${ordered.size} matched album tracks",
                kind = StreamCollectionKind.AlbumLike,
                sources = ordered,
                accentColor = accentForKey("${representative.artist}|${representative.albumTitle}"),
            )
        }
        .sortedByDescending { it.sources.count { source -> source.reaction == YouTubeReaction.Liked } * 3 + it.sources.sumOf { source -> source.playCount } }
}

internal fun filterAlbumRecommendationsAgainstProjects(
    albums: List<StreamCollection>,
    drafts: List<AlbumDownloadDraft>,
): List<StreamCollection> {
    if (albums.isEmpty() || drafts.isEmpty()) return albums
    val projectAlbumKeys = drafts.map { it.release.albumRecommendationKey() }.filter { it.isNotBlank() }.toSet()
    val projectSourceKeys = drafts
        .flatMap { draft -> draft.release.matchedPremiumDeckSources().map { it.streamDistinctKey() } }
        .toSet()
    return albums.filterNot { album ->
        val albumKey = album.albumRecommendationKey()
        val sourceKeys = album.sources.map { it.streamDistinctKey() }.toSet()
        val sourceOverlap = sourceKeys.count { it in projectSourceKeys }
        albumKey.isNotBlank() && albumKey in projectAlbumKeys ||
            sourceOverlap >= min(2, sourceKeys.size.coerceAtLeast(2))
    }
}

internal fun AlbumDownloadRelease.albumRecommendationKey(): String =
    albumRecommendationKey(artist, title)

private fun StreamCollection.albumRecommendationKey(): String {
    val representative = sources.firstOrNull()
    val artist = representative?.author?.cleanStreamArtistName().orEmpty()
    return albumRecommendationKey(artist, title)
}

internal fun albumRecommendationKey(artist: String, album: String): String {
    val artistKey = artist.cleanStreamArtistName().normalizedSearchText()
    val albumKey = album.cleanStrictStreamAlbumTitle().normalizedSearchText().ifBlank { album.normalizedSearchText() }
    if (artistKey.isBlank() || albumKey.isBlank()) return ""
    return "$artistKey|$albumKey"
}

internal fun rankAlbumLikeCollections(
    albums: List<StreamCollection>,
    followedArtists: List<FollowedStreamArtist>,
    profile: UserPreferenceProfile = UserPreferenceProfile(),
    playHistory: List<StreamPlayHistoryItem> = emptyList(),
): List<StreamCollection> {
    if (albums.isEmpty()) return emptyList()
    val followedKeys = followedArtists
        .flatMap { listOf(it.key, it.officialKey, it.name.normalizedSearchText(), it.officialName.normalizedSearchText()) }
        .filter { it.isNotBlank() }
        .toSet()
    val followedAffinityKeys = followedKeys.map { it.personalizationAffinityKey() }.toSet()
    val historyArtistKeys = playHistory
        .map { it.author.cleanStreamArtistName().normalizedSearchText() }
        .filter { it.isNotBlank() }
        .toSet()
    return albums
        .mapNotNull { album ->
            val representative = album.sources.firstOrNull()
            val artistKey = representative?.artistKey().orEmpty()
            val followedMatch = followedArtists.firstOrNull { artist ->
                artistKey == artist.key ||
                    artistKey == artist.officialKey ||
                    representative?.author.orEmpty().personalizationAffinityKey() == artist.officialName.personalizationAffinityKey()
            }
            if (
                followedMatch != null &&
                !followedMatch.allowsLabelReleasedMusic() &&
                followedMatch.requiredOfficialChannelKey().isNotBlank() &&
                album.sources.any { !it.matchesFollowedOfficialArtist(followedMatch) }
            ) {
                return@mapNotNull null
            }
            val profileArtistKey = representative?.author.orEmpty().personalizationAffinityKey()
            val sourceScore = album.sources.sumOf { source ->
                source.playCount.coerceAtMost(12) * 4 +
                    (if (source.reaction == YouTubeReaction.Liked) 30 else 0) +
                    (if (source.bookmarked) 18 else 0) +
                    (if (source.downloadState == YouTubeDownloadState.Downloaded || source.status == YouTubeSourceStatus.Downloaded) 12 else 0)
            }
            val followedBoost = if (artistKey in followedKeys || profileArtistKey in followedAffinityKeys) 160 else 0
            val profileBoost = ((profile.artistAffinity[profileArtistKey] ?: 0f).coerceIn(-2f, 3f) * 46f).toInt()
            val historyBoost = if (artistKey in historyArtistKeys) 24 else 0
            val likedTrackCoverage = album.sources.count { it.reaction == YouTubeReaction.Liked || it.bookmarked } * 18
            val tracklistConfidence = min(album.sources.size, 12) * 8
            val score = followedBoost + profileBoost + historyBoost + likedTrackCoverage + tracklistConfidence + sourceScore
            album to score
        }
        .sortedWith(
            compareByDescending<Pair<StreamCollection, Int>> { it.second }
                .thenByDescending { it.first.sources.size }
                .thenBy { it.first.title.normalizedSearchText() },
        )
        .map { (album, score) ->
            val representative = album.sources.firstOrNull()
            val artistKey = representative?.artistKey().orEmpty()
            val profileArtistKey = representative?.author.orEmpty().personalizationAffinityKey()
            val modelMatched = (profile.artistAffinity[profileArtistKey] ?: 0f) > 0.35f
            val followedMatched = artistKey in followedKeys || profileArtistKey in followedAffinityKeys
            val prefix = when {
                followedMatched -> "Followed artist"
                modelMatched -> "Smart match"
                score > 80 -> "Recommended"
                else -> null
            }
            if (prefix == null || album.subtitle.startsWith(prefix)) {
                album
            } else {
                album.copy(subtitle = "$prefix - ${album.subtitle}")
            }
        }
}

private fun YouTubeSource.strictStreamAlbumMatch(): StreamAlbumMatch? {
    val artist = author.cleanStreamArtistName()
    val artistKey = artist.normalizedSearchText()
    if (artistKey.isBlank() || artistKey in genericStreamAlbumArtistKeys) return null
    val albumTitle = strictStreamAlbumTitle(artist) ?: return null
    val albumKey = albumTitle.normalizedSearchText()
    if (albumKey.isBlank() || albumKey in genericStreamAlbumTitleKeys) return null
    return StreamAlbumMatch(
        source = this,
        artist = artist,
        artistKey = artistKey,
        albumTitle = albumTitle,
        albumKey = albumKey,
        trackNumber = streamAlbumTrackNumber(title),
    )
}

internal fun YouTubeSource.strictStreamAlbumTitle(artist: String = author.cleanStreamArtistName()): String? =
    albumTitleHint
        .cleanStrictStreamAlbumTitle()
        .takeIf { it.isReliableStrictAlbumTitle() }
        ?: inferStructuredTopicAlbumTitle(title, artist)
        ?: inferStrictStreamAlbumTitle(title)

private fun inferStructuredTopicAlbumTitle(rawTitle: String, artistName: String): String? {
    val parts = rawTitle
        .split(Regex("\\s+(?:\\u00B7|\\u2022)\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.size < 3) return null
    val artistKey = artistName.cleanStreamArtistName().normalizedSearchText()
    val titleArtistKey = parts.getOrNull(1).orEmpty().cleanYouTubeArtistCandidate().cleanStreamArtistName().normalizedSearchText()
    if (artistKey.isBlank() || titleArtistKey.isBlank() || artistKey != titleArtistKey) return null
    return parts
        .drop(2)
        .joinToString(" ")
        .cleanStrictStreamAlbumTitle()
        .takeIf { it.isReliableStrictAlbumTitle() }
}

private fun inferStrictStreamAlbumTitle(rawTitle: String): String? {
    val patterns = listOf(
        Regex("""\bfrom\s+(?:the\s+)?album\s+["â€œ]?(.{2,80})""", RegexOption.IGNORE_CASE),
        Regex("""\btaken\s+from\s+["â€œ](.{2,80})["â€]""", RegexOption.IGNORE_CASE),
        Regex("""\balbum\s*[:\-]\s*["â€œ]?(.{2,80})""", RegexOption.IGNORE_CASE),
        Regex("""["â€œ](.{2,80})["â€]\s+(?:album|lp)\b""", RegexOption.IGNORE_CASE),
    )
    patterns.forEach { pattern ->
        pattern.find(rawTitle)?.groupValues?.getOrNull(1)?.cleanStrictStreamAlbumTitle()?.let { candidate ->
            if (candidate.isReliableStrictAlbumTitle()) return candidate
        }
    }
    Regex("""\(([^)]{2,80})\)|\[([^]]{2,80})]""")
        .findAll(rawTitle)
        .map { match -> match.groupValues.getOrNull(1).orEmpty().ifBlank { match.groupValues.getOrNull(2).orEmpty() } }
        .map { it.cleanStrictStreamAlbumTitle() }
        .firstOrNull { candidate ->
            candidate.isReliableStrictAlbumTitle() &&
                rawTitle.contains("album", ignoreCase = true) &&
                !candidate.contains("official", ignoreCase = true)
        }
        ?.let { return it }
    return null
}

internal fun String.cleanStrictStreamAlbumTitle(): String =
    trim()
        .replace(Regex("""^(?:from\s+(?:the\s+)?album|taken\s+from|album|lp)\s*[:\-]?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(?:official|audio|video|lyrics?|visualizer|out now).*$""", RegexOption.IGNORE_CASE), "")
        .substringBefore(" | ")
        .substringBefore(" / ")
        .substringBefore(",")
        .trim(' ', '"', '\'', '\u201c', '\u201d', '[', ']', '(', ')', '-', ':')
        .replace(Regex("""\s+"""), " ")

internal fun String.isReliableStrictAlbumTitle(): Boolean {
    val key = normalizedSearchText()
    return key.length >= 3 &&
        key !in genericStreamAlbumTitleKeys &&
        streamTitleTokens(this).isNotEmpty() &&
        !key.contains("single") &&
        !key.contains("playlist") &&
        !key.contains("full album")
}

internal fun streamAlbumTrackNumber(rawTitle: String): Int? =
    Regex("""(?i)\b(?:track|#)\s*(\d{1,2})\b""").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Regex("""^\s*(\d{1,2})[\).\-\s]""").find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()

internal val genericStreamAlbumArtistKeys = setOf(
    "",
    "various artists",
    "various",
    PREMIUMDECK_SOURCE_NAME.normalizedSearchText(),
    LEGACY_YOUTUBE_SOURCE_NAME.normalizedSearchText(),
)

private val genericStreamAlbumTitleKeys = setOf(
    "",
    "album",
    "official audio",
    "official video",
    "music",
    "single",
    "unknown album",
)

private fun buildArtistCollections(candidates: List<YouTubeSource>): List<StreamCollection> =
    topArtistGroups(candidates, candidates)
        .map { (artist, group) ->
            StreamCollection(
                id = "artist-${artist.normalizedSearchText()}",
                title = artist,
                subtitle = "${group.size.coerceAtMost(15)} tracks and discoveries",
                kind = StreamCollectionKind.Artist,
                sources = group.distinctBy { it.streamDistinctKey() }.take(15),
                accentColor = accentForKey(artist),
            )
        }

private fun topArtistGroups(localSources: List<YouTubeSource>, candidates: List<YouTubeSource>): List<Pair<String, List<YouTubeSource>>> {
    val candidateByArtist = candidates.groupBy { it.artistKey() }
    return localSources
        .filter {
            it.artistKey().isNotBlank() &&
                !it.author.equals(LEGACY_YOUTUBE_SOURCE_NAME, ignoreCase = true) &&
                !it.author.equals(PREMIUMDECK_SOURCE_NAME, ignoreCase = true)
        }
        .groupBy { it.artistKey() }
        .map { (artistKey, localGroup) ->
            val artist = localGroup.maxByOrNull { it.playCount + if (it.reaction == YouTubeReaction.Liked) 4 else 0 }?.author?.cleanStreamArtistName().orEmpty()
            artist to (localGroup + candidateByArtist[artistKey].orEmpty())
                .filter { it.reaction != YouTubeReaction.Disliked }
                .distinctBy { it.streamDistinctKey() }
                .sortedWith(compareByDescending<YouTubeSource> { it.reaction == YouTubeReaction.Liked }.thenByDescending { it.playCount }.thenByDescending { it.lastPlayedMillis })
        }
        .filter { it.first.isNotBlank() && it.second.isNotEmpty() }
        .sortedByDescending { (_, group) -> group.sumOf { it.playCount } + group.count { it.reaction == YouTubeReaction.Liked } * 4 + group.count { it.bookmarked } * 2 }
}

internal fun YouTubeSource.streamDistinctKey(): String =
    detectYouTubeSource(url)?.sourceId ?: url.ifBlank { "$title|$author" }.normalizedSearchText()

internal fun YouTubeSource.streamIdentityKeys(): List<String> =
    listOf(id, url, streamDistinctKey()).filter { it.isNotBlank() }.distinct()

internal fun streamSourceIdentityLookup(sources: List<YouTubeSource>): Map<String, YouTubeSource> =
    buildMap {
        sources.forEach { source ->
            source.streamIdentityKeys().forEach { key -> putIfAbsent(key, source) }
        }
    }

internal fun YouTubeSource.artistKey(): String =
    author.cleanStreamArtistName().normalizedSearchText()

internal fun String.cleanStreamArtistName(): String =
    replace(" - Topic", "", ignoreCase = true)
        .replace("VEVO", "", ignoreCase = true)
        .replace(Regex("""\s+"""), " ")
        .trim()

internal fun String.cleanStreamTitle(): String =
    cleanYouTubeDisplayTitle(this).ifBlank { this }.take(80)

internal fun accentForKey(key: String): Int =
    youtubeAccentChoices[Math.floorMod(key.normalizedSearchText().hashCode(), youtubeAccentChoices.size)]

