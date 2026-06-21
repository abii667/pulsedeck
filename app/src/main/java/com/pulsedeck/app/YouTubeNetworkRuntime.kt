package com.pulsedeck.app

import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

private const val PIPED_INSTANCES_ENDPOINT = ""
private const val PIPED_ENDPOINT_CACHE_TTL_MILLIS = 10L * 60L * 1000L
private const val YOUTUBE_SEARCH_SUGGESTION_CACHE_TTL_MILLIS = 5L * 60L * 1000L
private const val YOUTUBE_SEARCH_PRIMARY_TIMEOUT_MILLIS = 3_400L
private const val YOUTUBE_SEARCH_SUGGESTION_TIMEOUT_MILLIS = 1_000L
private const val YOUTUBE_SEARCH_FALLBACK_CONCURRENCY = 2
internal val PIPED_API_ENDPOINTS = emptyList<String>()
@Volatile private var cachedPipedApiEndpoints: Pair<Long, List<String>>? = null
private val cachedYouTubeSuggestions = ConcurrentHashMap<String, Pair<Long, List<YouTubeSearchSuggestion>>>()

internal fun detectYouTubeSource(input: String): YouTubeDetection? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val normalized = when {
        trimmed.startsWith("@") -> "https://www.youtube.com/$trimmed"
        trimmed.startsWith("youtube.com", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("music.youtube.com", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("m.youtube.com", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("youtu.be", ignoreCase = true) -> "https://$trimmed"
        trimmed.startsWith("www.youtube.com", ignoreCase = true) -> "https://$trimmed"
        else -> trimmed
    }
    Regex("""(?:[?&]list=)([^&#]+)""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.let { return YouTubeDetection(normalized, YouTubeSourceKind.Playlist, it) }
    listOf(
        Regex("""(?:https?://)?(?:www\.)?youtu\.be/([^/?#&]+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/(?:shorts|live)/([^/?#&]+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/watch\?(?:[^#]*&)?v=([^&#]+)""", RegexOption.IGNORE_CASE),
    ).firstNotNullOfOrNull { regex ->
        regex.find(normalized)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }?.let { return YouTubeDetection(normalized, YouTubeSourceKind.Video, it) }
    Regex("""(?:https?://)?(?:www\.)?youtube\.com/(?:@|channel/|c/|user/)([^/?#&]+)""", RegexOption.IGNORE_CASE)
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.let { return YouTubeDetection(normalized, YouTubeSourceKind.Channel, it) }
    val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
    val host = uri.host.orEmpty().lowercase()
    if ("youtube.com" !in host && "youtu.be" !in host) return null
    val path = uri.path.orEmpty()
    val playlistId = uri.getQueryParameter("list")
    if (!playlistId.isNullOrBlank()) {
        return YouTubeDetection(normalized, YouTubeSourceKind.Playlist, playlistId)
    }
    val videoId = when {
        "youtu.be" in host -> path.trim('/').takeIf { it.isNotBlank() }
        path.startsWith("/watch") -> uri.getQueryParameter("v")
        path.startsWith("/shorts/") -> path.substringAfter("/shorts/").substringBefore('/').takeIf { it.isNotBlank() }
        path.startsWith("/live/") -> path.substringAfter("/live/").substringBefore('/').takeIf { it.isNotBlank() }
        else -> null
    }
    if (!videoId.isNullOrBlank()) {
        return YouTubeDetection(normalized, YouTubeSourceKind.Video, videoId)
    }
    val handle = when {
        path.startsWith("/@") -> path.substringAfter("/@").substringBefore('/').takeIf { it.isNotBlank() }
        path.startsWith("/channel/") -> path.substringAfter("/channel/").substringBefore('/').takeIf { it.isNotBlank() }
        path.startsWith("/c/") -> path.substringAfter("/c/").substringBefore('/').takeIf { it.isNotBlank() }
        path.startsWith("/user/") -> path.substringAfter("/user/").substringBefore('/').takeIf { it.isNotBlank() }
        else -> null
    }
    return handle?.let { YouTubeDetection(normalized, YouTubeSourceKind.Channel, it) }
}

internal suspend fun resolveYouTubeSource(detection: YouTubeDetection, quality: YouTubeQuality): YouTubeSource =
    withContext(Dispatchers.IO) {
        val fallback = fallbackYouTubeSource(detection, quality)
        if (detection.kind != YouTubeSourceKind.Video) return@withContext fallback
        runCatching {
            val encodedUrl = URLEncoder.encode(detection.normalizedUrl, "UTF-8")
            val connection = URL("https://www.youtube.com/oembed?url=$encodedUrl&format=json").openConnection() as HttpURLConnection
            connection.connectTimeout = 2500
            connection.readTimeout = 2500
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val title = json.optString("title", fallback.title).ifBlank { fallback.title }
                val author = json.optString("author_name", fallback.author).ifBlank { fallback.author }
                val inferred = smartYouTubeMusicMetadata(title, author)
                fallback.copy(
                    title = inferred.title.ifBlank { title },
                    author = inferred.artist.takeIf { inferred.confidence >= 70 }
                        ?: author.cleanYouTubeArtistCandidate().ifBlank { author },
                    thumbnailUrl = normalizeYouTubeThumbnailUrl(json.optString("thumbnail_url")),
                    channelTitle = author,
                    status = YouTubeSourceStatus.StreamReady,
                )
            }
        }.getOrElse { fallback.copy(status = YouTubeSourceStatus.ResolverNeeded) }
    }

internal data class PipedAudioStream(
    val url: String,
    val mimeType: String?,
    val quality: String,
    val format: String,
    val bitrate: Long,
    val audioOnly: Boolean = true,
)

internal data class InnertubeAudioFormat(
    val url: String,
    val mimeType: String?,
    val quality: String,
    val bitrate: Long,
    val durationMillis: Long,
)

private val newPipeInitLock = Any()
@Volatile private var newPipeInitialized = false

internal fun ensureNewPipeExtractorInitialized() {
    if (newPipeInitialized) return
    synchronized(newPipeInitLock) {
        if (newPipeInitialized) return
        val locale = Locale.getDefault()
        val language = locale.language.takeIf { it.isNotBlank() } ?: "en"
        val country = locale.country.takeIf { it.isNotBlank() } ?: "US"
        NewPipe.init(PulseDeckNewPipeDownloader(), Localization(language, country), ContentCountry(country))
        newPipeInitialized = true
    }
}

private class PulseDeckNewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 7000
        connection.readTimeout = 15000
        connection.requestMethod = request.httpMethod()
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36",
        )
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

        val requestHeaders = linkedMapOf<String, List<String>>()
        request.localization()?.let { requestHeaders.putAll(Request.getHeadersFromLocalization(it)) }
        request.headers()?.forEach { (name, values) ->
            if (!name.isNullOrBlank() && !values.isNullOrEmpty()) requestHeaders[name] = values
        }
        requestHeaders.forEach { (name, values) ->
            connection.setRequestProperty(name, values.joinToString(","))
        }

        val data = request.dataToSend()
        if (data != null && data.isNotEmpty()) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Length", data.size.toString())
            connection.outputStream.use { it.write(data) }
        }

        val responseCode = connection.responseCode
        val body = if (request.httpMethod().equals("HEAD", ignoreCase = true)) {
            ""
        } else {
            val stream = if (responseCode in 200..399) connection.inputStream else connection.errorStream
            stream?.decodedFor(connection.contentEncoding)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        val responseHeaders = buildMap<String, List<String>> {
            connection.headerFields.forEach { (name, values) ->
                if (!name.isNullOrBlank() && !values.isNullOrEmpty()) put(name, values)
            }
        }
        return Response(
            responseCode,
            connection.responseMessage.orEmpty(),
            responseHeaders,
            body,
            connection.url?.toString().orEmpty(),
        )
    }
}

private fun InputStream.decodedFor(contentEncoding: String?): InputStream =
    when {
        contentEncoding?.contains("gzip", ignoreCase = true) == true -> GZIPInputStream(this)
        contentEncoding?.contains("deflate", ignoreCase = true) == true -> InflaterInputStream(this)
        else -> this
    }

internal fun httpJson(url: String, connectTimeout: Int = 2600, readTimeout: Int = 7000): JSONObject? =
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "PulseDeck/0.1")
        if (connection.responseCode !in 200..299) return@runCatching null
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()

internal fun httpJsonArray(url: String, connectTimeout: Int = 2600, readTimeout: Int = 7000): JSONArray? =
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "PulseDeck/0.1")
        if (connection.responseCode !in 200..299) return@runCatching null
        JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()

internal fun httpText(url: String, connectTimeout: Int = 2600, readTimeout: Int = 7000): String? =
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "text/plain,text/html,application/json")
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36",
        )
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.decodedFor(connection.contentEncoding).bufferedReader().use { it.readText() }
    }.getOrNull()

internal fun httpPostJson(url: String, payload: JSONObject, connectTimeout: Int = 2600, readTimeout: Int = 7000): JSONObject? =
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty(
            "User-Agent",
            "com.google.ios.youtube/19.09.3 (iPhone16,2; U; CPU iOS 17_3 like Mac OS X)",
        )
        connection.outputStream.use { output -> output.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) return@runCatching null
        val stream = connection.inputStream.decodedFor(connection.contentEncoding)
        JSONObject(stream.bufferedReader().use { it.readText() })
    }.getOrNull()

internal fun JSONObject.optLongFlexible(key: String): Long {
    val value = opt(key) ?: return 0L
    return when (value) {
        is Number -> value.toLong()
        is String -> value.filter { it.isDigit() }.toLongOrNull() ?: 0L
        else -> 0L
    }
}

internal suspend fun searchYouTubeVideos(
    query: String,
    requestLabel: String = "general",
    debounceMillis: Long = 0L,
): YouTubeSearchResponse = withContext(Dispatchers.IO) {
    val needle = query.trim()
    val queryId = YouTubeNetworkDiagnostics.nextSearchId()
    val trace = YouTubeSearchNetworkTrace(
        queryId = queryId,
        queryHash = youtubeSearchCacheKey(needle).networkQueryHash(),
        queryLength = needle.length,
        label = requestLabel,
        debounceMillis = debounceMillis,
    )
    try {
        if (needle.length < 2 || PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
            val response = YouTubeSearchResponse()
            trace.setStrategy(if (needle.length < 2) "too_short" else "offline_deck")
            trace.finish(response.results.size, response.suggestions.size)
            return@withContext response
        }
        val encodedQuery = URLEncoder.encode(needle, "UTF-8")
        val endpoints = activePipedApiEndpoints()
        if (endpoints.isEmpty()) {
            val suggestions = fetchCachedOrNetworkSuggestions(encodedQuery, endpoints, trace, allowNetwork = false)
            val response = YouTubeSearchResponse(suggestions = suggestions)
            trace.setStrategy("no_endpoints")
            trace.finish(response.results.size, response.suggestions.size)
            return@withContext response
        }

        val primary = endpoints.first()
        trace.setStrategy(if (needle.length <= 2) "short_preferred_only" else "preferred_then_fallback")
        val primaryResults = withTimeoutOrNull(YOUTUBE_SEARCH_PRIMARY_TIMEOUT_MILLIS) {
            searchPipedEndpoint(primary, encodedQuery, needle, trace)
        }.orEmpty()
        val results = if (primaryResults.isNotEmpty() || needle.length <= 2) {
            primaryResults
        } else {
            val fallbackBudget = when {
                needle.length <= 4 -> 1
                else -> endpoints.size - 1
            }
            searchFallbackPipedEndpoints(
                endpoints = endpoints.drop(1).take(fallbackBudget),
                encodedQuery = encodedQuery,
                query = needle,
                trace = trace,
            )
        }
        val suggestions = fetchCachedOrNetworkSuggestions(
            encodedQuery = encodedQuery,
            endpoints = endpoints,
            trace = trace,
            allowNetwork = results.isEmpty() || needle.length >= 3,
        )
        val response = YouTubeSearchResponse(results = results, suggestions = suggestions)
        trace.finish(response.results.size, response.suggestions.size)
        response
    } catch (error: CancellationException) {
        trace.markCancelled()
        trace.finish(0, 0)
        throw error
    }
}

internal fun activePipedApiEndpoints(nowMillis: Long = System.currentTimeMillis()): List<String> {
    cachedPipedApiEndpoints
        ?.takeIf { (savedMillis, endpoints) -> endpoints.isNotEmpty() && nowMillis - savedMillis <= PIPED_ENDPOINT_CACHE_TTL_MILLIS }
        ?.let { return PipedEndpointRuntime.ranked(it.second) }
    val endpoints = runCatching {
        val connection = URL(PIPED_INSTANCES_ENDPOINT).openConnection() as HttpURLConnection
        connection.connectTimeout = 1000
        connection.readTimeout = 1800
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "PulseDeck/0.1")
        if (connection.responseCode !in 200..299) return@runCatching emptyList()
        val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val apiUrl = item.optString("api_url").trim().trimEnd('/')
                val uptime = item.optDouble("uptime_24h", 0.0)
                if (apiUrl.startsWith("https://") && uptime >= 80.0) add(apiUrl)
            }
        }
    }.getOrDefault(emptyList())
        .let { live -> (live + PIPED_API_ENDPOINTS).distinct().take(8) }
    cachedPipedApiEndpoints = nowMillis to endpoints
    return PipedEndpointRuntime.ranked(endpoints)
}

private suspend fun searchPipedEndpoint(
    endpoint: String,
    encodedQuery: String,
    query: String,
    trace: YouTubeSearchNetworkTrace,
): List<YouTubeSearchResult> {
    trace.recordEndpoint(endpoint)
    var endpointResponded = false
    val results = runCatching {
        val json = httpJson("$endpoint/search?q=$encodedQuery&filter=videos", connectTimeout = 1400, readTimeout = 2800)
            ?: return@runCatching emptyList()
        endpointResponded = true
        parsePipedSearchResults(json, query)
    }.getOrElse { emptyList() }
    if (endpointResponded) {
        PipedEndpointRuntime.record(endpoint, success = true)
        trace.recordEndpointResult(success = true, empty = results.isEmpty())
    } else {
        PipedEndpointRuntime.record(endpoint, success = false)
        trace.recordEndpointResult(success = false, empty = false)
    }
    return results
}

private suspend fun searchFallbackPipedEndpoints(
    endpoints: List<String>,
    encodedQuery: String,
    query: String,
    trace: YouTubeSearchNetworkTrace,
): List<YouTubeSearchResult> {
    if (endpoints.isEmpty()) return emptyList()
    trace.appendStrategySuffix("_budget_${endpoints.size}")
    return coroutineScope {
        val deferreds = endpoints.take(YOUTUBE_SEARCH_FALLBACK_CONCURRENCY).map { endpoint ->
            async { searchPipedEndpoint(endpoint, encodedQuery, query, trace) }
        }
        try {
            val pending = deferreds.toMutableList()
            while (pending.isNotEmpty()) {
                val (completedIndex, results) = select<Pair<Int, List<YouTubeSearchResult>>> {
                    pending.forEachIndexed { index, deferred ->
                        deferred.onAwait { index to it }
                    }
                }
                pending.removeAt(completedIndex)
                if (results.isNotEmpty()) {
                    deferreds.forEach { it.cancel() }
                    return@coroutineScope results
                }
            }
            val remaining = endpoints.drop(YOUTUBE_SEARCH_FALLBACK_CONCURRENCY)
            for (endpoint in remaining) {
                val results = searchPipedEndpoint(endpoint, encodedQuery, query, trace)
                if (results.isNotEmpty()) return@coroutineScope results
            }
            emptyList()
        } finally {
            deferreds.forEach { it.cancel() }
        }
    }
}

private suspend fun fetchCachedOrNetworkSuggestions(
    encodedQuery: String,
    endpoints: List<String>,
    trace: YouTubeSearchNetworkTrace,
    allowNetwork: Boolean,
): List<YouTubeSearchSuggestion> {
    val key = encodedQuery.lowercase(Locale.US)
    val nowMillis = SystemClock.elapsedRealtime()
    cachedYouTubeSuggestions[key]
        ?.takeIf { (savedMillis, suggestions) -> suggestions.isNotEmpty() && nowMillis - savedMillis <= YOUTUBE_SEARCH_SUGGESTION_CACHE_TTL_MILLIS }
        ?.let {
            trace.recordSuggestionCacheHit()
            return it.second
        }
    if (!allowNetwork || endpoints.isEmpty()) return emptyList()
    val suggestions = withTimeoutOrNull(YOUTUBE_SEARCH_SUGGESTION_TIMEOUT_MILLIS) {
        fetchYouTubeSearchSuggestions(encodedQuery, endpoints.take(2), trace)
    }.orEmpty()
    if (suggestions.isNotEmpty()) cachedYouTubeSuggestions[key] = nowMillis to suggestions
    return suggestions
}

private suspend fun fetchYouTubeSearchSuggestions(
    encodedQuery: String,
    endpoints: List<String> = activePipedApiEndpoints(),
    trace: YouTubeSearchNetworkTrace? = null,
): List<YouTubeSearchSuggestion> = withContext(Dispatchers.IO) {
    for (endpoint in endpoints) {
        trace?.recordSuggestion(endpoint)
        val suggestions = runCatching {
            val connection = URL("$endpoint/suggestions?query=$encodedQuery").openConnection() as HttpURLConnection
            connection.connectTimeout = 1000
            connection.readTimeout = 1800
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "PulseDeck/0.1")
            if (connection.responseCode !in 200..299) return@runCatching emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = if (body.trim().startsWith("[")) {
                JSONArray(body)
            } else {
                JSONObject(body).optJSONArray("suggestions") ?: JSONArray()
            }
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.length >= 2 }?.let { add(YouTubeSearchSuggestion(it)) }
                }
            }.distinctBy { it.text.lowercase(Locale.US) }.take(6)
        }.getOrDefault(emptyList())
        if (suggestions.isNotEmpty()) {
            PipedEndpointRuntime.record(endpoint, success = true)
            return@withContext suggestions
        }
        PipedEndpointRuntime.record(endpoint, success = false)
    }
    emptyList()
}

private fun String.networkQueryHash(): String =
    sha256(this).take(10)

private fun parsePipedSearchResults(json: JSONObject, query: String): List<YouTubeSearchResult> {
    val items = json.optJSONArray("items")
        ?: json.optJSONArray("results")
        ?: return emptyList()
    return buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val durationMillis = item.optLong("duration", 0L) * 1000L
            val source = item.toYouTubeSearchSource(durationMillis) ?: continue
            val videoId = detectYouTubeSource(source.url)?.sourceId.orEmpty()
            val score = scoreYouTubeMusicResult(query, source, durationMillis, item)
            add(
                YouTubeSearchResult(
                    source = source.copy(isPodcast = durationMillis > 10 * 60_000L || source.isPodcast),
                    durationMillis = durationMillis,
                    uploadedDate = item.optString("uploadedDate").ifBlank { item.optString("uploaded") },
                    views = item.optLongFlexible("views"),
                    videoId = videoId,
                    score = score,
                    cachedMillis = System.currentTimeMillis(),
                    matchReason = musicMatchReason(score, durationMillis, source),
                ),
            )
        }
    }
        .sortedByDescending { it.score }
        .distinctBy { "${it.videoId.ifBlank { it.source.id }}|${it.source.title.normalizedSearchText()}|${it.source.author.normalizedSearchText()}" }
        .take(40)
}

private fun JSONObject.toYouTubeSearchSource(durationMillis: Long): YouTubeSource? {
    val normalizedUrl = normalizeYouTubeSearchUrl(optString("url")) ?: return null
    val detection = detectYouTubeSource(normalizedUrl)?.takeIf { it.kind == YouTubeSourceKind.Video } ?: return null
    val title = optString("title").ifBlank { return null }
    val channelTitle = optString("uploaderName")
        .ifBlank { optString("uploader") }
        .ifBlank { optString("author") }
        .ifBlank { PREMIUMDECK_SOURCE_NAME }
    val channelUrl = normalizeYouTubeSearchUrl(
        optString("uploaderUrl")
            .ifBlank { optString("uploaderURL") }
            .ifBlank { optString("channelUrl") }
            .ifBlank { optString("channelURL") },
    ).orEmpty()
    val inferred = smartYouTubeMusicMetadata(title, channelTitle)
    val displayTitle = inferred.title.ifBlank { title }
    val displayAuthor = inferred.artist.takeIf { inferred.confidence >= 70 }
        ?: channelTitle.cleanYouTubeArtistCandidate().ifBlank { channelTitle }
    return YouTubeSource(
        id = "video-${detection.sourceId}",
        url = detection.normalizedUrl,
        kind = YouTubeSourceKind.Video,
        title = displayTitle,
        author = displayAuthor,
        thumbnailUrl = normalizeYouTubeThumbnailUrl(
            optString("thumbnail").ifBlank { optString("thumbnailUrl") },
        ),
        channelTitle = channelTitle,
        channelUrl = channelUrl,
        channelVerified = optBoolean("uploaderVerified", false) || optBoolean("verified", false),
        albumTitleHint = premiumDeckAlbumTitleHint(),
        durationMillis = durationMillis,
        status = YouTubeSourceStatus.StreamReady,
        reviewState = YouTubeReviewState.Accepted,
    )
}

internal fun normalizeYouTubeThumbnailUrl(rawUrl: String?): String? {
    val trimmed = rawUrl?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return when {
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/vi/") -> "https://i.ytimg.com${trimmed.substringBefore("?")}"
        trimmed.startsWith("/") -> "https://i.ytimg.com$trimmed"
        else -> null
    }
}

internal fun youtubeThumbnailUrlForVideoId(videoId: String): String? =
    videoId.trim().takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

internal fun YouTubeSource.bestThumbnailUrl(): String? =
    normalizeYouTubeThumbnailUrl(thumbnailUrl)
        ?: detectYouTubeSource(url)
            ?.takeIf { it.kind == YouTubeSourceKind.Video }
            ?.sourceId
            ?.let(::youtubeThumbnailUrlForVideoId)
        ?: id.removePrefix("video-")
            .takeIf { it != id && it.isNotBlank() }
            ?.let(::youtubeThumbnailUrlForVideoId)

internal fun normalizeYouTubeSearchUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    return when {
        trimmed.startsWith("http", ignoreCase = true) -> trimmed
        trimmed.startsWith("/") -> "https://www.youtube.com$trimmed"
        trimmed.startsWith("watch", ignoreCase = true) -> "https://www.youtube.com/$trimmed"
        trimmed.startsWith("playlist", ignoreCase = true) -> "https://www.youtube.com/$trimmed"
        else -> null
    }
}
