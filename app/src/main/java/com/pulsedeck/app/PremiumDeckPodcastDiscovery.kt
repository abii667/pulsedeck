package com.pulsedeck.app

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val PREF_PREMIUMDECK_PODCAST_DISCOVERY = "premiumdeck_podcast_discovery"
private const val PREMIUMDECK_PODCAST_CACHE_SCHEMA_VERSION = 3
private const val PREMIUMDECK_PODCAST_GROUPED_SCHEMA_VERSION = 2
private const val PREMIUMDECK_PODCAST_DISCOVERY_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
private const val PREMIUMDECK_PODCAST_SHOW_TTL_MILLIS = 12L * 60L * 60L * 1000L
private val podcastSignalWords = listOf("podcast", "episode", "interview", "conversation", "full episode", "talks", "show", "guest")
private val podcastRejectWords = listOf("official audio", "official music video", "lyrics", "lyric video", "full album", "karaoke", "cover", "mix", "playlist")
private val podcastEpisodeRejectWords = listOf("shorts", "#shorts", "highlight", "highlights", "clip", "clips", "trailer", "preview", "reaction")

internal data class PremiumDeckPodcastShow(
    val id: String,
    val rank: Int,
    val title: String,
    val aliases: List<String>,
    val channelHints: List<String>,
    val searchQueries: List<String>,
    val custom: Boolean = false,
)

internal data class PremiumDeckPodcastSnapshot(
    val savedMillis: Long = 0L,
    val episodesByShow: Map<String, List<YouTubeSearchResult>> = emptyMap(),
    val customShows: List<PremiumDeckPodcastShow> = emptyList(),
) {
    val results: List<YouTubeSearchResult>
        get() = recentEpisodes(limit = 20)
}

internal data class PremiumDeckCustomPodcastDiscovery(
    val show: PremiumDeckPodcastShow,
    val episodes: List<YouTubeSearchResult>,
)

internal val TopPremiumDeckPodcastShows = listOf(
    PremiumDeckPodcastShow(
        id = "joe-rogan",
        rank = 1,
        title = "The Joe Rogan Experience",
        aliases = listOf("joe rogan experience", "jre", "joe rogan"),
        channelHints = listOf("powerfuljre", "joe rogan"),
        searchQueries = listOf("PowerfulJRE latest full episode", "The Joe Rogan Experience full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "crime-junkie",
        rank = 2,
        title = "Crime Junkie",
        aliases = listOf("crime junkie", "audiochuck"),
        channelHints = listOf("crime junkie", "audiochuck"),
        searchQueries = listOf("Crime Junkie podcast latest episode", "Crime Junkie full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "the-daily",
        rank = 3,
        title = "The Daily",
        aliases = listOf("the daily", "new york times", "nyt podcasts"),
        channelHints = listOf("new york times podcasts", "the daily"),
        searchQueries = listOf("The Daily New York Times podcast latest episode", "NYT The Daily podcast full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "call-her-daddy",
        rank = 4,
        title = "Call Her Daddy",
        aliases = listOf("call her daddy", "alex cooper"),
        channelHints = listOf("call her daddy", "alex cooper"),
        searchQueries = listOf("Call Her Daddy latest episode", "Call Her Daddy full podcast episode"),
    ),
    PremiumDeckPodcastShow(
        id = "smartless",
        rank = 5,
        title = "SmartLess",
        aliases = listOf("smartless", "jason bateman", "sean hayes", "will arnett"),
        channelHints = listOf("smartless"),
        searchQueries = listOf("SmartLess podcast latest episode", "SmartLess full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "stuff-you-should-know",
        rank = 6,
        title = "Stuff You Should Know",
        aliases = listOf("stuff you should know", "sysk"),
        channelHints = listOf("stuff you should know", "sysk"),
        searchQueries = listOf("Stuff You Should Know latest episode", "SYSK podcast full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "dateline-nbc",
        rank = 7,
        title = "Dateline NBC",
        aliases = listOf("dateline nbc", "dateline"),
        channelHints = listOf("dateline nbc", "nbc news"),
        searchQueries = listOf("Dateline NBC podcast latest episode", "Dateline full episode podcast"),
    ),
    PremiumDeckPodcastShow(
        id = "this-past-weekend",
        rank = 8,
        title = "This Past Weekend with Theo Von",
        aliases = listOf("this past weekend", "theo von"),
        channelHints = listOf("theo von", "this past weekend"),
        searchQueries = listOf("This Past Weekend Theo Von latest episode", "Theo Von podcast full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "mrballen",
        rank = 9,
        title = "MrBallen Podcast",
        aliases = listOf("mrballen podcast", "mrballen"),
        channelHints = listOf("mrballen"),
        searchQueries = listOf("MrBallen Podcast latest episode", "MrBallen podcast full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "new-heights",
        rank = 10,
        title = "New Heights",
        aliases = listOf("new heights", "jason kelce", "travis kelce"),
        channelHints = listOf("new heights"),
        searchQueries = listOf("New Heights latest episode", "New Heights full podcast episode"),
    ),
    PremiumDeckPodcastShow(
        id = "good-hang",
        rank = 11,
        title = "Good Hang with Amy Poehler",
        aliases = listOf("good hang", "amy poehler"),
        channelHints = listOf("good hang", "amy poehler"),
        searchQueries = listOf("Good Hang Amy Poehler latest episode", "Good Hang with Amy Poehler full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "mel-robbins",
        rank = 12,
        title = "The Mel Robbins Podcast",
        aliases = listOf("mel robbins podcast", "mel robbins"),
        channelHints = listOf("mel robbins"),
        searchQueries = listOf("The Mel Robbins Podcast latest episode", "Mel Robbins full podcast episode"),
    ),
    PremiumDeckPodcastShow(
        id = "up-first",
        rank = 13,
        title = "Up First",
        aliases = listOf("up first", "npr"),
        channelHints = listOf("npr", "up first"),
        searchQueries = listOf("NPR Up First podcast latest episode", "Up First full episode"),
    ),
    PremiumDeckPodcastShow(
        id = "armchair-expert",
        rank = 14,
        title = "Armchair Expert",
        aliases = listOf("armchair expert", "dax shepard"),
        channelHints = listOf("armchair expert", "dax shepard"),
        searchQueries = listOf("Armchair Expert latest episode", "Armchair Expert full podcast episode"),
    ),
    PremiumDeckPodcastShow(
        id = "counterclock",
        rank = 15,
        title = "CounterClock",
        aliases = listOf("counterclock", "audiochuck"),
        channelHints = listOf("counterclock", "audiochuck"),
        searchQueries = listOf("CounterClock podcast latest episode", "CounterClock full episode"),
    ),
)

internal fun PremiumDeckPodcastSnapshot.isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
    results.isNotEmpty() && savedMillis > 0L && nowMillis - savedMillis <= PREMIUMDECK_PODCAST_DISCOVERY_TTL_MILLIS

internal fun PremiumDeckPodcastSnapshot.isShowFresh(showId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
    episodesByShow[showId].orEmpty().size >= 10 &&
        savedMillis > 0L &&
        nowMillis - savedMillis <= PREMIUMDECK_PODCAST_SHOW_TTL_MILLIS

internal fun PremiumDeckPodcastSnapshot.episodesForShow(showId: String): List<YouTubeSearchResult> =
    episodesByShow[showId].orEmpty()
        .sortedByDescending { it.podcastPublishedMillis() }
        .take(10)

internal fun PremiumDeckPodcastSnapshot.recentEpisodes(limit: Int): List<YouTubeSearchResult> =
    episodesByShow.values
        .flatten()
        .distinctBy { it.videoId.ifBlank { it.source.streamDistinctKey() } }
        .sortedByDescending { it.podcastPublishedMillis() }
        .take(limit.coerceAtLeast(1))

internal fun PremiumDeckPodcastSnapshot.withShowEpisodes(showId: String, episodes: List<YouTubeSearchResult>): PremiumDeckPodcastSnapshot =
    copy(
        savedMillis = System.currentTimeMillis(),
        episodesByShow = episodesByShow + (showId to episodes.take(10)),
    )

internal fun PremiumDeckPodcastSnapshot.withCustomShow(show: PremiumDeckPodcastShow): PremiumDeckPodcastSnapshot {
    val normalizedTitle = show.title.normalizedSearchText()
    val nextShows = (listOf(show.copy(custom = true)) + customShows)
        .distinctBy { it.id }
        .filterIndexed { index, item -> index == 0 || item.title.normalizedSearchText() != normalizedTitle }
        .take(30)
    return copy(savedMillis = System.currentTimeMillis(), customShows = nextShows)
}

internal fun PremiumDeckPodcastSnapshot.directoryShows(): List<PremiumDeckPodcastShow> =
    (customShows + TopPremiumDeckPodcastShows).distinctBy { it.id }

internal fun findPremiumDeckPodcastShow(showId: String, customShows: List<PremiumDeckPodcastShow> = emptyList()): PremiumDeckPodcastShow? =
    (customShows + TopPremiumDeckPodcastShows).firstOrNull { it.id == showId }

internal fun loadPremiumDeckPodcastSnapshot(context: Context): PremiumDeckPodcastSnapshot {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_PREMIUMDECK_PODCAST_DISCOVERY, "{}")
        .orEmpty()
    return runCatching {
        val json = JSONObject(raw.ifBlank { "{}" })
        val schemaVersion = json.optInt("schemaVersion", 0)
        val grouped = if (schemaVersion >= PREMIUMDECK_PODCAST_GROUPED_SCHEMA_VERSION) {
            parsePodcastEpisodesByShow(json.optJSONArray("episodesByShow"))
        } else {
            emptyMap()
        }
        val customShows = if (schemaVersion >= PREMIUMDECK_PODCAST_CACHE_SCHEMA_VERSION) {
            parsePodcastShows(json.optJSONArray("customShows"))
        } else {
            emptyList()
        }
        PremiumDeckPodcastSnapshot(
            savedMillis = json.optLong("savedMillis", 0L),
            episodesByShow = grouped,
            customShows = customShows,
        )
    }.getOrDefault(PremiumDeckPodcastSnapshot())
}

internal fun savePremiumDeckPodcastSnapshot(context: Context, snapshot: PremiumDeckPodcastSnapshot) {
    val json = JSONObject()
        .put("schemaVersion", PREMIUMDECK_PODCAST_CACHE_SCHEMA_VERSION)
        .put("savedMillis", snapshot.savedMillis)
        .put("results", snapshot.results.take(20).toYouTubeSearchJsonArray())
        .put("episodesByShow", podcastEpisodesByShowToJson(snapshot.episodesByShow))
        .put("customShows", podcastShowsToJson(snapshot.customShows))
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_PREMIUMDECK_PODCAST_DISCOVERY, json.toString())
        .apply()
}

internal suspend fun fetchPremiumDeckPodcastSnapshot(existingSources: List<YouTubeSource>): PremiumDeckPodcastSnapshot {
    val episodesByShow = linkedMapOf<String, List<YouTubeSearchResult>>()
    for (show in TopPremiumDeckPodcastShows) {
        val episodes = fetchPremiumDeckPodcastShowEpisodes(show.id, existingSources, limit = 2)
        if (episodes.isNotEmpty()) {
            episodesByShow[show.id] = episodes
        }
    }
    return PremiumDeckPodcastSnapshot(savedMillis = System.currentTimeMillis(), episodesByShow = episodesByShow)
}

internal suspend fun fetchPremiumDeckPodcastShowEpisodes(
    showId: String,
    existingSources: List<YouTubeSource>,
    customShows: List<PremiumDeckPodcastShow> = emptyList(),
    limit: Int = 10,
): List<YouTubeSearchResult> {
    val show = findPremiumDeckPodcastShow(showId, customShows) ?: return emptyList()
    val results = buildList {
        for (query in show.searchQueries.take(2)) {
            val response = withTimeoutOrNull(6200L) { searchYouTubeVideos(query) } ?: YouTubeSearchResponse()
            response.results.forEach(::add)
            if (size >= 40) break
        }
    }
    return filterPremiumDeckPodcastShowEpisodes(show, results, existingSources, limit)
}

internal suspend fun fetchPremiumDeckCustomPodcastShow(
    query: String,
    existingSources: List<YouTubeSource>,
    limit: Int = 10,
): PremiumDeckCustomPodcastDiscovery? {
    val cleanQuery = query.trim().take(80)
    if (cleanQuery.length < 2) return null
    val rawResults = buildList {
        for (searchQuery in buildCustomPodcastSearchQueries(cleanQuery)) {
            val response = withTimeoutOrNull(6200L) { searchYouTubeVideos(searchQuery) } ?: YouTubeSearchResponse()
            response.results.forEach(::add)
            if (size >= 60) break
        }
    }.distinctBy { it.videoId.ifBlank { it.source.streamDistinctKey() } }
    if (rawResults.isEmpty()) return null
    val show = buildCustomPremiumDeckPodcastShow(cleanQuery, rawResults)
    val episodes = filterPremiumDeckPodcastShowEpisodes(show, rawResults, existingSources, limit)
    return PremiumDeckCustomPodcastDiscovery(show = show, episodes = episodes)
}

internal fun buildCustomPremiumDeckPodcastShow(query: String, results: List<YouTubeSearchResult> = emptyList()): PremiumDeckPodcastShow {
    val cleanQuery = query.trim().take(80)
    val bestChannelName = inferCustomPodcastChannelName(cleanQuery, results)
    val title = bestChannelName.ifBlank { cleanQuery }
    val aliases = listOf(cleanQuery, title)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.normalizedSearchText() }
    val channelHints = aliases
    return PremiumDeckPodcastShow(
        id = "custom-${title.normalizedSearchText().replace(' ', '-').filter { it.isLetterOrDigit() || it == '-' }.take(64).ifBlank { cleanQuery.hashCode().toString() }}",
        rank = 0,
        title = title,
        aliases = aliases,
        channelHints = channelHints,
        searchQueries = buildCustomPodcastSearchQueries(title).distinctBy { it.normalizedSearchText() },
        custom = true,
    )
}

internal fun filterPremiumDeckPodcastShowEpisodes(
    show: PremiumDeckPodcastShow,
    results: List<YouTubeSearchResult>,
    existingSources: List<YouTubeSource>,
    limit: Int = 10,
): List<YouTubeSearchResult> {
    val existingByKey = existingSources.associateBy { it.streamDistinctKey() }
    return results
        .asSequence()
        .filter { it.source.kind == YouTubeSourceKind.Video }
        .filter { it.looksLikeEpisodeFor(show) }
        .map { result ->
            val key = result.source.streamDistinctKey()
            val existing = existingByKey[key]
            val source = result.source.copy(
                playCount = existing?.playCount ?: result.source.playCount,
                addedMillis = existing?.addedMillis ?: result.source.addedMillis,
                lastPlayedMillis = existing?.lastPlayedMillis ?: result.source.lastPlayedMillis,
                reaction = existing?.reaction ?: result.source.reaction,
                bookmarked = existing?.bookmarked ?: result.source.bookmarked,
                isPodcast = true,
                status = existing?.status ?: if (result.source.status == YouTubeSourceStatus.ResolverNeeded) YouTubeSourceStatus.StreamReady else result.source.status,
                cachedUri = existing?.cachedUri ?: result.source.cachedUri,
                cachedMillis = existing?.cachedMillis ?: result.source.cachedMillis,
                downloadedUri = existing?.downloadedUri ?: result.source.downloadedUri,
                downloadState = existing?.downloadState ?: result.source.downloadState,
                downloadProgress = existing?.downloadProgress ?: result.source.downloadProgress,
                playbackPositionMillis = existing?.playbackPositionMillis ?: result.source.playbackPositionMillis,
                chapters = existing?.chapters?.ifEmpty { result.source.chapters } ?: result.source.chapters,
                sponsorSegments = existing?.sponsorSegments?.ifEmpty { result.source.sponsorSegments } ?: result.source.sponsorSegments,
                reviewState = YouTubeReviewState.Accepted,
                albumTitleHint = show.title,
            )
            result.copy(source = source, matchReason = result.matchReason ?: "Latest ${show.title} episode")
        }
        .distinctBy { it.videoId.ifBlank { it.source.streamDistinctKey() } }
        .sortedWith(
            compareByDescending<YouTubeSearchResult> { it.podcastPublishedMillis() }
                .thenByDescending { it.views }
                .thenByDescending { it.score },
        )
        .take(limit.coerceAtLeast(1))
        .toList()
}

internal fun filterPremiumDeckPodcastResults(
    results: List<YouTubeSearchResult>,
    existingSources: List<YouTubeSource>,
    limit: Int = 20,
): List<YouTubeSearchResult> {
    val existingKeys = existingSources.map { it.streamDistinctKey() }.toSet()
    return results
        .asSequence()
        .filter { it.source.kind == YouTubeSourceKind.Video }
        .filter { it.source.streamDistinctKey() !in existingKeys || it.source.isPodcast }
        .filter { it.looksLikePremiumDeckPodcast() }
        .map { result ->
            val source = result.source.copy(
                isPodcast = true,
                reviewState = YouTubeReviewState.Accepted,
                status = if (result.source.status == YouTubeSourceStatus.ResolverNeeded) YouTubeSourceStatus.StreamReady else result.source.status,
            )
            result.copy(source = source, matchReason = result.matchReason ?: "Podcast-style PremiumDeck source")
        }
        .distinctBy { result -> result.source.podcastDiscoveryDistinctKey() }
        .sortedWith(
            compareByDescending<YouTubeSearchResult> { it.views }
                .thenByDescending { it.score }
                .thenBy { it.source.title.lowercase(Locale.US) },
        )
        .take(limit.coerceAtLeast(1))
        .toList()
}

private fun YouTubeSearchResult.looksLikePremiumDeckPodcast(): Boolean {
    val duration = durationMillis.takeIf { it > 0L } ?: source.durationMillis
    if (duration < 12L * 60L * 1000L) return false
    val text = "${source.title} ${source.author} ${source.channelTitle}".lowercase(Locale.US)
    if (podcastRejectWords.any { text.contains(it) }) return false
    return podcastSignalWords.any { text.contains(it) }
}

private fun YouTubeSearchResult.looksLikeEpisodeFor(show: PremiumDeckPodcastShow): Boolean {
    val duration = durationMillis.takeIf { it > 0L } ?: source.durationMillis
    if (duration < 12L * 60L * 1000L) return false
    val text = "${source.title} ${source.author} ${source.channelTitle}".lowercase(Locale.US)
    if (podcastRejectWords.any { text.contains(it) }) return false
    if (podcastEpisodeRejectWords.any { text.contains(it) }) return false
    val titleText = source.title.normalizedSearchText()
    val normalizedText = text.normalizedSearchText()
    val channelText = "${source.author} ${source.channelTitle} ${source.channelUrl}".normalizedSearchText()
    val channelMatch = show.channelHints.any { hint -> channelText.contains(hint.normalizedSearchText()) }
    val channelAliasMatch = show.aliases.any { alias -> channelText.contains(alias.normalizedSearchText()) }
    if (!channelMatch && !channelAliasMatch) return false
    val titleAliasMatch = show.aliases.any { alias -> titleText.contains(alias.normalizedSearchText()) }
    val episodeSignal = podcastSignalWords.any { normalizedText.contains(it.normalizedSearchText()) } ||
        Regex("""(?i)\b(ep|episode)\s*#?\d+\b""").containsMatchIn(source.title) ||
        Regex("""#\d{2,}""").containsMatchIn(source.title)
    return episodeSignal || titleAliasMatch || duration >= 45L * 60L * 1000L
}

private fun buildCustomPodcastSearchQueries(query: String): List<String> =
    listOf(
        "$query latest podcast episode",
        "$query full podcast episode",
        "$query latest interview",
    )

private fun inferCustomPodcastChannelName(query: String, results: List<YouTubeSearchResult>): String {
    val queryKey = query.normalizedSearchText()
    return results
        .asSequence()
        .filter { it.source.kind == YouTubeSourceKind.Video }
        .filter {
            val duration = it.durationMillis.takeIf { duration -> duration > 0L } ?: it.source.durationMillis
            duration >= 12L * 60L * 1000L
        }
        .filter {
            val text = "${it.source.title} ${it.source.author} ${it.source.channelTitle}".lowercase(Locale.US)
            podcastRejectWords.none { reject -> text.contains(reject) } &&
                podcastEpisodeRejectWords.none { reject -> text.contains(reject) }
        }
        .sortedWith(
            compareByDescending<YouTubeSearchResult> {
                val channelKey = "${it.source.author} ${it.source.channelTitle}".normalizedSearchText()
                if (channelKey.contains(queryKey)) 1 else 0
            }.thenByDescending { it.podcastPublishedMillis() }
                .thenByDescending { it.views },
        )
        .map { it.source.author.ifBlank { it.source.channelTitle }.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

internal fun YouTubeSearchResult.podcastPublishedMillis(nowMillis: Long = System.currentTimeMillis()): Long =
    parsePremiumDeckUploadDateMillis(uploadedDate, nowMillis)
        ?: cachedMillis.takeIf { it > 0L }
        ?: source.addedMillis

private fun YouTubeSource.podcastDiscoveryDistinctKey(): String {
    val channelKey = channelUrl.youtubeChannelIdentityKey()
        .ifBlank { channelTitle.normalizedSearchText() }
        .ifBlank { author.normalizedSearchText() }
    return channelKey.ifBlank { streamDistinctKey() }
}

private fun parsePodcastEpisodesByShow(array: JSONArray?): Map<String, List<YouTubeSearchResult>> =
    buildMap {
        if (array == null) return@buildMap
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val showId = item.optString("showId").takeIf { it.isNotBlank() } ?: continue
            put(
                showId,
                parseCachedYouTubeSearchResults(item.optJSONArray("results")).map { result ->
                    result.copy(source = result.source.copy(isPodcast = true, reviewState = YouTubeReviewState.Accepted))
                }.take(10),
            )
        }
    }

private fun parsePodcastShows(array: JSONArray?): List<PremiumDeckPodcastShow> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            val id = item.optString("id").trim()
            if (title.isBlank() || id.isBlank()) continue
            add(
                PremiumDeckPodcastShow(
                    id = id,
                    rank = item.optInt("rank", 0),
                    title = title,
                    aliases = parseStringList(item.optJSONArray("aliases")).ifEmpty { listOf(title) },
                    channelHints = parseStringList(item.optJSONArray("channelHints")).ifEmpty { listOf(title) },
                    searchQueries = parseStringList(item.optJSONArray("searchQueries")).ifEmpty { buildCustomPodcastSearchQueries(title) },
                    custom = item.optBoolean("custom", true),
                ),
            )
        }
    }

private fun podcastShowsToJson(shows: List<PremiumDeckPodcastShow>): JSONArray =
    JSONArray().apply {
        shows.filter { it.custom }.forEach { show ->
            put(
                JSONObject()
                    .put("id", show.id)
                    .put("rank", show.rank)
                    .put("title", show.title)
                    .put("aliases", show.aliases.toJsonArray())
                    .put("channelHints", show.channelHints.toJsonArray())
                    .put("searchQueries", show.searchQueries.toJsonArray())
                    .put("custom", true),
            )
        }
    }

private fun parseStringList(array: JSONArray?): List<String> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }.distinctBy { it.normalizedSearchText() }

private fun List<String>.toJsonArray(): JSONArray =
    JSONArray().apply {
        this@toJsonArray.forEach { value -> put(value) }
    }

private fun podcastEpisodesByShowToJson(episodesByShow: Map<String, List<YouTubeSearchResult>>): JSONArray =
    JSONArray().apply {
        episodesByShow.forEach { (showId, results) ->
            put(
                JSONObject()
                    .put("showId", showId)
                    .put("results", results.take(10).toYouTubeSearchJsonArray()),
            )
        }
    }
