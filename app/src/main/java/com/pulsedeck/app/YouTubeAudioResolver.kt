package com.pulsedeck.app

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private const val YOUTUBE_DIRECT_RESOLVER_TIMEOUT_MILLIS = 4_800L
private const val YOUTUBE_PIPED_RESOLVER_TIMEOUT_MILLIS = 8_500L
private const val YOUTUBE_INNERTUBE_PLAYER_ENDPOINT = ""

internal data class YouTubeResolvedAudio(
    val streamUrl: String,
    val title: String,
    val artist: String,
    val durationMillis: Long,
    val mimeType: String?,
    val thumbnailUrl: String?,
    val qualityLabel: String,
    val isPodcast: Boolean,
    val chapters: List<YouTubeChapter> = emptyList(),
    val sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    val availableFormats: List<String> = emptyList(),
    val bitrateKbps: Int? = null,
    val audioOnly: Boolean = true,
    val selectedFormat: String = "",
)

internal fun extractVideoIdForSponsorBlock(url: String, fallbackId: String? = null): String? =
    fallbackId?.takeIf { it.isNotBlank() }
        ?: detectYouTubeSource(url)?.takeIf { it.kind == YouTubeSourceKind.Video }?.sourceId

internal enum class StreamCacheDecision {
    Hit,
    Missing,
    Invalid,
    PolicyBypass,
    Expired,
    Stale,
}

internal data class StreamCacheCheck(
    val decision: StreamCacheDecision,
    val url: String? = null,
    val expiresAtMillis: Long? = null,
)

internal fun YouTubeSource.freshCachedStreamUrl(
    nowMillis: Long = System.currentTimeMillis(),
    policy: StreamingDataPolicy? = null,
): String? =
    cachedStreamUrlCheck(nowMillis, policy).url

internal fun YouTubeSource.cachedStreamUrlCheck(
    nowMillis: Long = System.currentTimeMillis(),
    policy: StreamingDataPolicy? = null,
): StreamCacheCheck {
    if (policy?.shouldBypassCachedStreamUrl == true) return StreamCacheCheck(StreamCacheDecision.PolicyBypass)
    val rawCached = cachedUri?.takeIf { it.isNotBlank() } ?: return StreamCacheCheck(StreamCacheDecision.Missing)
    if (!rawCached.startsWith("http", ignoreCase = true)) return StreamCacheCheck(StreamCacheDecision.Invalid)
    if (cachedMillis <= 0L) return StreamCacheCheck(StreamCacheDecision.Missing)
    val expiryMillis = rawCached.streamUrlExpiryMillis()
    if (expiryMillis != null && nowMillis >= expiryMillis - YOUTUBE_STREAM_EXPIRY_BUFFER_MILLIS) {
        return StreamCacheCheck(StreamCacheDecision.Expired, expiresAtMillis = expiryMillis)
    }
    if (nowMillis - cachedMillis > YOUTUBE_STREAM_CACHE_TTL_MILLIS) {
        return StreamCacheCheck(StreamCacheDecision.Stale, expiresAtMillis = expiryMillis)
    }
    return StreamCacheCheck(StreamCacheDecision.Hit, url = rawCached, expiresAtMillis = expiryMillis)
}

private fun String.streamUrlExpiryMillis(): Long? =
    runCatching {
        Uri.parse(this).getQueryParameter("expire")?.toLongOrNull()?.times(1000L)
    }.getOrNull()
        ?: Regex("""(?:[?&])expire=(\d+)""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.times(1000L)

internal fun YouTubeSource.toCachedResolvedAudio(streamUrl: String): YouTubeResolvedAudio =
    YouTubeResolvedAudio(
        streamUrl = streamUrl,
        title = title.ifBlank { PREMIUMDECK_STREAM_TITLE },
        artist = author.ifBlank { PREMIUMDECK_SOURCE_NAME },
        durationMillis = durationMillis,
        mimeType = null,
        thumbnailUrl = bestThumbnailUrl(),
        qualityLabel = quality.label,
        isPodcast = isPodcast,
        chapters = chapters,
        sponsorSegments = sponsorSegments,
        selectedFormat = "cached",
    )

private fun <T> Result<T>.rethrowCancellation(): Result<T> =
    onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
    }

internal fun fetchSponsorBlockSegments(videoId: String?): List<SponsorBlockSegment> {
    if (videoId.isNullOrBlank()) return emptyList()
    return runCatching {
        val categories = URLEncoder.encode("""["sponsor","selfpromo","music_offtopic"]""", "UTF-8")
        val connection = URL("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categories")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 2200
        connection.readTimeout = 3200
        connection.requestMethod = "GET"
        if (connection.responseCode !in 200..299) return@runCatching emptyList()
        val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val segment = item.optJSONArray("segment") ?: continue
                val start = (segment.optDouble(0, -1.0) * 1000.0).toLong()
                val end = (segment.optDouble(1, -1.0) * 1000.0).toLong()
                if (start >= 0L && end > start) add(SponsorBlockSegment(item.optString("category", "sponsor"), start, end))
            }
        }
    }.getOrDefault(emptyList())
}

internal suspend fun resolveYouTubeAudioWithNewPipe(
    source: YouTubeSource,
    policy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
): YouTubeResolvedAudio? =
    withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val detection = detectYouTubeSource(source.url)?.takeIf { it.kind == YouTubeSourceKind.Video } ?: return@withContext null
        val watchUrl = "https://www.youtube.com/watch?v=${detection.sourceId}"
        runCatching {
            currentCoroutineContext().ensureActive()
            ensureNewPipeExtractorInitialized()
            val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
            currentCoroutineContext().ensureActive()
            val audioStreams = info.audioStreams.orEmpty()
            val muxedStreams = info.videoStreams.orEmpty()
            val audioStream = selectNewPipeAudioStream(audioStreams, source.quality, policy)
            val muxedStream = if (audioStream == null) selectNewPipeMuxedStream(muxedStreams, source.quality, policy) else null
            val streamUrl = (audioStream?.content ?: muxedStream?.content)
                ?.takeIf { it.startsWith("http", ignoreCase = true) }
                ?: return@runCatching null
            val durationMillis = info.duration.coerceAtLeast(0L) * 1000L
            val title = info.name?.takeIf { it.isNotBlank() } ?: source.title
            val author = info.uploaderName?.takeIf { it.isNotBlank() } ?: source.author
            val parsed = smartYouTubeMusicMetadata(title, author)
            val description = info.description?.content
            val chapters = parseChaptersFromDescription(description, durationMillis)
            YouTubeResolvedAudio(
                streamUrl = streamUrl,
                title = parsed.title,
                artist = parsed.artist.ifBlank { author.cleanYouTubeArtistCandidate().ifBlank { author } },
                durationMillis = durationMillis,
                mimeType = audioStream?.format?.mimeType ?: muxedStream?.format?.mimeType,
                thumbnailUrl = info.thumbnails
                    .filter { it.url.isNotBlank() }
                    .maxByOrNull { image -> image.width.coerceAtLeast(0) * image.height.coerceAtLeast(0) }
                    ?.url
                    ?: source.thumbnailUrl,
                qualityLabel = audioStream?.qualityLabel()
                    ?: muxedStream?.qualityLabel()
                    ?: source.quality.label,
                isPodcast = durationMillis > 10 * 60_000L || source.isPodcast,
                chapters = chapters.ifEmpty { source.chapters },
                sponsorSegments = source.sponsorSegments,
                availableFormats = (audioStreams.map { it.qualityLabel() } + muxedStreams.map { it.qualityLabel() }).distinct(),
                bitrateKbps = audioStream?.qualityKbpsPreference() ?: muxedStream?.bitrateKbpsPreference(),
                audioOnly = audioStream != null,
                selectedFormat = audioStream?.getFormat()?.getSuffix()
                    ?: muxedStream?.format?.suffix
                    ?: audioStream?.format?.mimeType
                    ?: muxedStream?.format?.mimeType
                    ?: "",
            )
        }.rethrowCancellation().onFailure { throwable ->
            Log.w(YOUTUBE_LOG_TAG, "APK-local YouTube stream resolve failed for source=${sha256(source.streamDistinctKey()).take(10)}", throwable)
        }.getOrNull()
    }

internal suspend fun resolveYouTubeAudioWithInnertube(
    source: YouTubeSource,
    policy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
): YouTubeResolvedAudio? =
    withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val detection = detectYouTubeSource(source.url)?.takeIf { it.kind == YouTubeSourceKind.Video } ?: return@withContext null
        val payload = JSONObject()
            .put(
                "context",
                JSONObject()
                    .put(
                        "client",
                        JSONObject()
                            .put("clientName", "IOS")
                            .put("clientVersion", "19.09.3")
                            .put("deviceMake", "Apple")
                            .put("deviceModel", "iPhone16,2")
                            .put("platform", "MOBILE")
                            .put("osName", "iOS")
                            .put("osVersion", "17.3"),
                    ),
            )
            .put("videoId", detection.sourceId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val json = httpPostJson(
            YOUTUBE_INNERTUBE_PLAYER_ENDPOINT,
            payload,
            connectTimeout = 3200,
            readTimeout = 7200,
        ) ?: return@withContext null
        currentCoroutineContext().ensureActive()
        val status = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        if (status.isNotBlank() && status != "OK") return@withContext null
        val videoDetails = json.optJSONObject("videoDetails")
        val title = videoDetails?.optString("title", source.title)?.ifBlank { source.title } ?: source.title
        val author = videoDetails?.optString("author", source.author)?.ifBlank { source.author } ?: source.author
        val durationMillis = videoDetails?.optLong("lengthSeconds", 0L)?.times(1000L)?.takeIf { it > 0L } ?: source.durationMillis
        val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnailUrl = thumbnails?.let { items ->
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val url = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                    add(item.optInt("width", 0) * item.optInt("height", 0) to normalizeYouTubeThumbnailUrl(url))
                }
            }.filter { it.second != null }
                .maxByOrNull { it.first }
                ?.second
        } ?: source.thumbnailUrl
        val adaptiveFormats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return@withContext null
        val formats = buildList {
            for (index in 0 until adaptiveFormats.length()) {
                val item = adaptiveFormats.optJSONObject(index) ?: continue
                val mimeType = item.optString("mimeType").takeIf { it.startsWith("audio/", ignoreCase = true) } ?: continue
                val streamUrl = item.optString("url").takeIf { it.startsWith("http", ignoreCase = true) }
                    ?: item.optString("signatureCipher").directInnertubeUrl()
                    ?: item.optString("cipher").directInnertubeUrl()
                    ?: continue
                add(
                    InnertubeAudioFormat(
                        url = streamUrl,
                        mimeType = mimeType.substringBefore(";"),
                        quality = item.optString("audioQuality")
                            .removePrefix("AUDIO_QUALITY_")
                            .lowercase(Locale.US)
                            .replace('_', ' ')
                            .ifBlank { item.optString("quality") },
                        bitrate = item.optLongFlexible("bitrate"),
                        durationMillis = item.optLongFlexible("approxDurationMs").takeIf { it > 0L } ?: durationMillis,
                    ),
                )
            }
        }
        val stream = selectInnertubeAudioFormat(formats, source.quality, policy) ?: return@withContext null
        val parsed = smartYouTubeMusicMetadata(title, author)
        YouTubeResolvedAudio(
            streamUrl = stream.url,
            title = parsed.title,
            artist = parsed.artist.ifBlank { author.cleanYouTubeArtistCandidate().ifBlank { author } },
            durationMillis = stream.durationMillis,
            mimeType = stream.mimeType,
            thumbnailUrl = thumbnailUrl,
            qualityLabel = stream.quality.ifBlank { source.quality.label },
            isPodcast = stream.durationMillis > 10 * 60_000L || source.isPodcast,
            chapters = source.chapters,
            sponsorSegments = source.sponsorSegments,
            availableFormats = formats.map { format -> format.quality.ifBlank { format.mimeType.orEmpty() } }.filter { it.isNotBlank() }.distinct(),
            bitrateKbps = stream.bitrateKbps(),
            audioOnly = true,
            selectedFormat = stream.mimeType.orEmpty(),
        )
    }

private fun String.directInnertubeUrl(): String? {
    if (isBlank()) return null
    val query = runCatching { Uri.parse("https://pulsedeck.local/?$this") }.getOrNull() ?: return null
    if (!query.getQueryParameter("s").isNullOrBlank()) return null
    return query.getQueryParameter("url")?.takeIf { it.startsWith("http", ignoreCase = true) }
}

internal suspend fun resolveYouTubeAudioOnDevice(
    context: Context,
    source: YouTubeSource,
    policy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
): YouTubeResolvedAudio? =
    withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val detection = detectYouTubeSource(source.url)?.takeIf { it.kind == YouTubeSourceKind.Video } ?: return@withContext null
        val encodedVideoId = URLEncoder.encode(detection.sourceId, "UTF-8")
        for (endpoint in activePipedApiEndpoints()) {
            currentCoroutineContext().ensureActive()
            val resolved = runCatching {
                val json = httpJson("$endpoint/streams/$encodedVideoId") ?: return@runCatching null
                currentCoroutineContext().ensureActive()
                val audioStreams = json.optJSONArray("audioStreams")
                val streams = buildList {
                    if (audioStreams != null) {
                        for (index in 0 until audioStreams.length()) {
                            val item = audioStreams.optJSONObject(index) ?: continue
                            val url = item.optString("url").takeIf { it.startsWith("http", ignoreCase = true) } ?: continue
                            add(
                                PipedAudioStream(
                                    url = url,
                                    mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
                                    quality = item.optString("quality"),
                                    format = item.optString("format"),
                                    bitrate = item.optLongFlexible("bitrate"),
                                ),
                            )
                        }
                    }
                }
                val muxedStreams = buildList {
                    val videoStreams = json.optJSONArray("videoStreams")
                    if (videoStreams != null) {
                        for (index in 0 until videoStreams.length()) {
                            val item = videoStreams.optJSONObject(index) ?: continue
                            if (item.optBoolean("videoOnly", false)) continue
                            val url = item.optString("url").takeIf { it.startsWith("http", ignoreCase = true) } ?: continue
                            add(
                                PipedAudioStream(
                                    url = url,
                                    mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
                                    quality = item.optString("quality"),
                                    format = item.optString("format"),
                                    bitrate = item.optLongFlexible("bitrate"),
                                    audioOnly = false,
                                ),
                            )
                        }
                    }
                }
                val stream = selectPipedAudioStream(streams, source.quality, policy)
                    ?: selectPipedMuxedStream(muxedStreams, source.quality, policy)
                    ?: run {
                        Log.d(
                            YOUTUBE_LOG_TAG,
                            "Piped resolver returned no playable streams at $endpoint for ${detection.sourceId}; audio=${streams.size}, muxed=${muxedStreams.size}",
                        )
                        return@runCatching null
                    }
                val durationMillis = json.optLong("duration", 0L) * 1000L
                val title = json.optString("title", source.title).ifBlank { source.title }
                val author = json.optString("uploader", source.author).ifBlank { source.author }
                val parsed = smartYouTubeMusicMetadata(title, author)
                val chapters = parseChaptersFromDescription(json.optString("description"), durationMillis)
                YouTubeResolvedAudio(
                    streamUrl = stream.url,
                    title = parsed.title,
                    artist = parsed.artist.ifBlank { author.cleanYouTubeArtistCandidate().ifBlank { author } },
                    durationMillis = durationMillis,
                    mimeType = stream.mimeType,
                    thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() } ?: source.thumbnailUrl,
                    qualityLabel = buildList {
                        add(stream.quality.ifBlank { source.quality.label })
                        if (!stream.audioOnly) add("muxed fallback")
                    }.joinToString(" "),
                    isPodcast = durationMillis > 10 * 60_000L || source.isPodcast,
                    chapters = chapters.ifEmpty { source.chapters },
                    sponsorSegments = source.sponsorSegments,
                    availableFormats = (streams + muxedStreams).mapNotNull { candidate ->
                        listOf(candidate.format, candidate.quality)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .takeIf { it.isNotBlank() }
                    }.distinct(),
                    bitrateKbps = stream.bitrateKbps(),
                    audioOnly = stream.audioOnly,
                    selectedFormat = stream.format.ifBlank { stream.mimeType.orEmpty() },
                )
            }.rethrowCancellation().getOrNull()
            PipedEndpointRuntime.record(endpoint, success = resolved != null)
            if (resolved != null) return@withContext resolved
        }
        null
    }

internal suspend fun resolveYouTubeAudio(
    context: Context,
    source: YouTubeSource,
    policy: StreamingDataPolicy? = null,
): YouTubeResolvedAudio? =
    withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) return@withContext null
        val streamPolicy = policy ?: NetworkPolicyController.currentPolicy(context, PulseOnlineRuntime.settings)
        val startedAtMillis = SystemClock.elapsedRealtime()
        var requestCount = 0
        var fallbackCount = 0
        var failureCount = 0
        var timeoutCount = 0

        val cacheCheck = source.cachedStreamUrlCheck(policy = streamPolicy)
        cacheCheck.url?.let { cachedUrl ->
            val resolved = source.toCachedResolvedAudio(cachedUrl)
            YouTubeResolverRuntime.record(YouTubeResolverStrategy.Cached, success = true)
            YouTubeNetworkDiagnostics.reportResolver(
                source = source,
                strategy = YouTubeResolverStrategy.Cached,
                policy = streamPolicy,
                requestCount = 0,
                fallbackCount = 0,
                failureCount = 0,
                timeoutCount = 0,
                cached = true,
                cacheDecision = cacheCheck.decision,
                success = true,
                durationMillis = SystemClock.elapsedRealtime() - startedAtMillis,
                resolved = resolved,
            )
            return@withContext resolved
        }

        suspend fun resolveDirect(strategy: YouTubeResolverStrategy): YouTubeResolvedAudio? {
            currentCoroutineContext().ensureActive()
            requestCount += 1
            var completed = false
            val resolved = withTimeoutOrNull(YOUTUBE_DIRECT_RESOLVER_TIMEOUT_MILLIS) {
                currentCoroutineContext().ensureActive()
                val value = when (strategy) {
                    YouTubeResolverStrategy.NewPipe -> runCatching { resolveYouTubeAudioWithNewPipe(source, streamPolicy) }.rethrowCancellation().getOrNull()
                    YouTubeResolverStrategy.Innertube -> runCatching { resolveYouTubeAudioWithInnertube(source, streamPolicy) }.rethrowCancellation().getOrNull()
                    else -> null
                }
                completed = true
                value
            }
            if (resolved != null) {
                YouTubeResolverRuntime.record(strategy, success = true)
            } else {
                failureCount += 1
                if (!completed) timeoutCount += 1
                YouTubeResolverRuntime.record(strategy, success = false)
            }
            return resolved
        }

        suspend fun resolvePipedFallback(): YouTubeResolvedAudio? {
            currentCoroutineContext().ensureActive()
            fallbackCount += 1
            requestCount += 1
            var completed = false
            val resolved = withTimeoutOrNull(YOUTUBE_PIPED_RESOLVER_TIMEOUT_MILLIS) {
                currentCoroutineContext().ensureActive()
                val value = runCatching { resolveYouTubeAudioOnDevice(context, source, streamPolicy) }.rethrowCancellation().getOrNull()
                completed = true
                value
            }
            YouTubeResolverRuntime.record(YouTubeResolverStrategy.PipedFallback, success = resolved != null)
            if (resolved == null) {
                failureCount += 1
                if (!completed) timeoutCount += 1
            }
            return resolved
        }

        for (strategy in YouTubeResolverRuntime.directOrder()) {
            currentCoroutineContext().ensureActive()
            val resolved = resolveDirect(strategy)
            if (resolved != null) {
                YouTubeNetworkDiagnostics.reportResolver(
                    source = source,
                    strategy = strategy,
                    policy = streamPolicy,
                    requestCount = requestCount,
                    fallbackCount = fallbackCount,
                    failureCount = failureCount,
                    timeoutCount = timeoutCount,
                    cached = false,
                    cacheDecision = cacheCheck.decision,
                    success = true,
                    durationMillis = SystemClock.elapsedRealtime() - startedAtMillis,
                    resolved = resolved,
                )
                return@withContext resolved
            }
        }

        if (!PulseOnlineRuntime.settings.useProxyFallback) {
            YouTubeNetworkDiagnostics.reportResolver(
                source = source,
                strategy = YouTubeResolverRuntime.directOrder().firstOrNull() ?: YouTubeResolverStrategy.Innertube,
                policy = streamPolicy,
                requestCount = requestCount,
                fallbackCount = fallbackCount,
                failureCount = failureCount,
                timeoutCount = timeoutCount,
                cached = false,
                cacheDecision = cacheCheck.decision,
                success = false,
                durationMillis = SystemClock.elapsedRealtime() - startedAtMillis,
            )
            return@withContext null
        }

        val piped = resolvePipedFallback()
        YouTubeNetworkDiagnostics.reportResolver(
            source = source,
            strategy = YouTubeResolverStrategy.PipedFallback,
            policy = streamPolicy,
            requestCount = requestCount,
            fallbackCount = fallbackCount,
            failureCount = failureCount,
            timeoutCount = timeoutCount,
            cached = false,
            cacheDecision = cacheCheck.decision,
            success = piped != null,
            durationMillis = SystemClock.elapsedRealtime() - startedAtMillis,
            resolved = piped,
        )
        piped
    }
