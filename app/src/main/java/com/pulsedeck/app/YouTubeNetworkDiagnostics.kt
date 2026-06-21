package com.pulsedeck.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val NETWORK_LOG_TAG = "PulseDeckNetwork"
private const val STREAM_LOG_TAG = "PulseDeckStream"
private const val DATA_LOG_TAG = "PulseDeckData"
private const val RESOLVER_LOG_TAG = "PulseDeckResolver"
private const val NETWORK_ENDPOINT_SUCCESS_WEIGHT = 3
private const val NETWORK_ENDPOINT_FAILURE_WEIGHT = 2
private const val NETWORK_ENDPOINT_RECENT_SUCCESS_BONUS_MS = 10L * 60L * 1000L

internal enum class YouTubeResolverStrategy {
    Cached,
    NewPipe,
    Innertube,
    PipedFallback,
}

internal class YouTubeSearchNetworkTrace(
    val queryId: Long,
    val queryHash: String,
    val queryLength: Int,
    val label: String,
    val debounceMillis: Long,
) {
    private val startedAtMillis = SystemClock.elapsedRealtime()
    private val lock = Any()
    private val endpointHashes = linkedSetOf<String>()
    private var searchRequests = 0
    private var suggestionRequests = 0
    private var endpointFailures = 0
    private var endpointEmptyResponses = 0
    private var suggestionCacheHits = 0
    private var cancelled = false
    private var strategy = "preferred"

    fun recordEndpoint(endpoint: String) {
        synchronized(lock) {
            searchRequests += 1
            endpointHashes += endpoint.networkHash()
        }
    }

    fun recordSuggestion(endpoint: String) {
        synchronized(lock) {
            suggestionRequests += 1
            endpointHashes += endpoint.networkHash()
        }
    }

    fun recordEndpointResult(success: Boolean, empty: Boolean) {
        synchronized(lock) {
            if (!success) endpointFailures += 1
            if (success && empty) endpointEmptyResponses += 1
        }
    }

    fun recordSuggestionCacheHit() {
        synchronized(lock) {
            suggestionCacheHits += 1
        }
    }

    fun setStrategy(value: String) {
        synchronized(lock) {
            strategy = value
        }
    }

    fun appendStrategySuffix(suffix: String) {
        synchronized(lock) {
            strategy += suffix
        }
    }

    fun markCancelled() {
        synchronized(lock) {
            cancelled = true
        }
    }

    fun finish(resultCount: Int, suggestionCount: Int) {
        val summary = synchronized(lock) {
            "search_summary id=$queryId label=$label query_len=$queryLength query_hash=$queryHash debounce_ms=$debounceMillis " +
                "strategy=$strategy cancelled=$cancelled requests=$searchRequests piped_endpoints=${endpointHashes.size} " +
                "suggestion_requests=$suggestionRequests suggestion_cache_hits=$suggestionCacheHits failures=$endpointFailures " +
                "empty=$endpointEmptyResponses results=$resultCount suggestions=$suggestionCount duration_ms=${SystemClock.elapsedRealtime() - startedAtMillis}"
        }
        YouTubeNetworkDiagnostics.reportSearch(
            summary,
        )
    }
}

internal object YouTubeNetworkDiagnostics {
    private val searchIds = AtomicLong(0L)
    private val streamSessionIds = AtomicLong(0L)
    @Volatile private var enabled = false
    private val thumbnailRequests = AtomicLong(0L)
    private val thumbnailDuplicates = AtomicLong(0L)
    private val thumbnailObserverCancels = AtomicLong(0L)
    private val thumbnailCompleted = AtomicLong(0L)
    private val thumbnailFailed = AtomicLong(0L)
    private val thumbnailCancelled = AtomicLong(0L)

    fun configure(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun nextSearchId(): Long = searchIds.incrementAndGet()

    fun nextStreamSessionId(): Long = streamSessionIds.incrementAndGet()

    fun reportSearch(message: String) {
        if (enabled) Log.d(NETWORK_LOG_TAG, message)
    }

    fun reportResolver(
        source: YouTubeSource,
        strategy: YouTubeResolverStrategy,
        policy: StreamingDataPolicy,
        requestCount: Int,
        fallbackCount: Int,
        failureCount: Int,
        timeoutCount: Int,
        cached: Boolean,
        cacheDecision: StreamCacheDecision,
        success: Boolean,
        durationMillis: Long,
        resolved: YouTubeResolvedAudio? = null,
        cancelled: Boolean = false,
    ) {
        if (!enabled) return
        Log.d(
            RESOLVER_LOG_TAG,
            "resolver_summary source=${source.streamDistinctKey().networkHash()} strategy=${strategy.name} " +
                "policy=${policy.policyLabel} quality=${policy.quality.name} data_saver=${policy.effectiveDataSaver} " +
                "cache=$cacheDecision cached=$cached success=$success cancelled=$cancelled requests=$requestCount " +
                "fallbacks=$fallbackCount failures=$failureCount timeouts=$timeoutCount duration_ms=$durationMillis",
        )
        if (resolved != null) {
            reportDataSelection(source, strategy, policy, cached, resolved)
        }
    }

    fun reportStreamSession(
        sessionId: Long,
        source: YouTubeSource,
        event: String,
        policy: StreamingDataPolicy? = null,
        queueSize: Int = 0,
        generation: Long = 0L,
        cached: Boolean = false,
        cacheDecision: StreamCacheDecision? = null,
        durationMillis: Long? = null,
        resolved: YouTubeResolvedAudio? = null,
        reason: String = "",
    ) {
        if (!enabled) return
        Log.d(
            STREAM_LOG_TAG,
            "stream_session id=$sessionId event=$event source=${source.streamDistinctKey().networkHash()} " +
                "queue=$queueSize generation=$generation cached=$cached cache=${cacheDecision?.name ?: "n/a"} " +
                "policy=${policy?.policyLabel ?: "n/a"} quality=${policy?.quality?.name ?: "n/a"} " +
                "data_saver=${policy?.effectiveDataSaver ?: false} reason=${reason.ifBlank { "n/a" }} " +
                "duration_ms=${durationMillis ?: -1} format=${resolved?.selectedFormat?.ifBlank { "n/a" } ?: "n/a"} " +
                "bitrate_kbps=${resolved?.bitrateKbps ?: -1} audio_only=${resolved?.audioOnly ?: true}",
        )
    }

    fun reportMediaPlaybackState(mediaId: String, isPlaying: Boolean, playbackState: Int) {
        if (!enabled) return
        Log.d(
            STREAM_LOG_TAG,
            "media_state media=${mediaId.networkHash()} is_playing=$isPlaying playback_state=$playbackState",
        )
    }

    private fun reportDataSelection(
        source: YouTubeSource,
        strategy: YouTubeResolverStrategy,
        policy: StreamingDataPolicy,
        cached: Boolean,
        resolved: YouTubeResolvedAudio,
    ) {
        Log.d(
            DATA_LOG_TAG,
            "data_summary source=${source.streamDistinctKey().networkHash()} strategy=${strategy.name} " +
                "policy=${policy.policyLabel} quality=${policy.quality.name} data_saver=${policy.effectiveDataSaver} " +
                "cached=$cached audio_only=${resolved.audioOnly} muxed=${!resolved.audioOnly} " +
                "bitrate_kbps=${resolved.bitrateKbps ?: -1} mime=${resolved.mimeType.orEmpty().ifBlank { "n/a" }} " +
                "format=${resolved.selectedFormat.ifBlank { "n/a" }} label=${resolved.qualityLabel.ifBlank { "n/a" }} " +
                "available=${resolved.availableFormats.take(6).joinToString(";").ifBlank { "n/a" }}",
        )
    }

    fun reportResolverHealth(requests: Int, failures: Int, cached: Boolean, capabilities: ResolverCapabilities) {
        if (!enabled) return
        Log.d(
            NETWORK_LOG_TAG,
            "resolver_health requests=$requests failures=$failures cached=$cached " +
                "yt_dlp=${capabilities.ytDlpAvailable} ffmpeg=${capabilities.ffmpegAvailable} sponsor=${capabilities.sponsorBlockAvailable}",
        )
    }

    fun reportWebViewRelease(source: YouTubeSource) {
        if (enabled) {
            Log.d(NETWORK_LOG_TAG, "webview_release source=${source.streamDistinctKey().networkHash()}")
        }
    }

    fun reportYouTubeThumbnailLifecycle(event: String, activeRequests: Int) {
        if (!enabled) return
        val requestCount = when (event) {
            "start" -> thumbnailRequests.incrementAndGet()
            else -> thumbnailRequests.get()
        }
        when (event) {
            "duplicate" -> thumbnailDuplicates.incrementAndGet()
            "observer_cancel" -> thumbnailObserverCancels.incrementAndGet()
            "complete" -> thumbnailCompleted.incrementAndGet()
            "failed" -> thumbnailFailed.incrementAndGet()
            "cancelled" -> thumbnailCancelled.incrementAndGet()
        }
        if (event == "start" && requestCount % 12L != 1L) return
        Log.d(
            NETWORK_LOG_TAG,
            "thumbnail_summary use_case=youtube_search event=$event requests=${thumbnailRequests.get()} " +
                "duplicates=${thumbnailDuplicates.get()} observer_cancels=${thumbnailObserverCancels.get()} " +
                "completed=${thumbnailCompleted.get()} failed=${thumbnailFailed.get()} cancelled=${thumbnailCancelled.get()} active=$activeRequests",
        )
    }
}

internal object PipedEndpointRuntime {
    private data class EndpointStats(
        val successes: Int = 0,
        val failures: Int = 0,
        val lastSuccessMillis: Long = 0L,
        val lastFailureMillis: Long = 0L,
    )

    private val statsByEndpoint = ConcurrentHashMap<String, EndpointStats>()

    fun ranked(endpoints: List<String>, nowMillis: Long = SystemClock.elapsedRealtime()): List<String> =
        endpoints.distinct().sortedWith(
            compareByDescending<String> { endpointScore(it, nowMillis) }
                .thenBy { it },
        )

    fun record(endpoint: String, success: Boolean, nowMillis: Long = SystemClock.elapsedRealtime()) {
        statsByEndpoint.compute(endpoint) { _, old ->
            val current = old ?: EndpointStats()
            if (success) {
                current.copy(successes = current.successes + 1, lastSuccessMillis = nowMillis)
            } else {
                current.copy(failures = current.failures + 1, lastFailureMillis = nowMillis)
            }
        }
    }

    private fun endpointScore(endpoint: String, nowMillis: Long): Int {
        val stats = statsByEndpoint[endpoint] ?: return 0
        val recentBonus = if (stats.lastSuccessMillis > 0L && nowMillis - stats.lastSuccessMillis <= NETWORK_ENDPOINT_RECENT_SUCCESS_BONUS_MS) 4 else 0
        return stats.successes * NETWORK_ENDPOINT_SUCCESS_WEIGHT - stats.failures * NETWORK_ENDPOINT_FAILURE_WEIGHT + recentBonus
    }
}

internal object YouTubeResolverRuntime {
    private data class ResolverStats(
        val successes: Int = 0,
        val failures: Int = 0,
        val lastSuccessMillis: Long = 0L,
    )

    private val statsByStrategy = ConcurrentHashMap<YouTubeResolverStrategy, ResolverStats>()

    fun directOrder(nowMillis: Long = SystemClock.elapsedRealtime()): List<YouTubeResolverStrategy> {
        val direct = listOf(YouTubeResolverStrategy.Innertube, YouTubeResolverStrategy.NewPipe)
        return direct.sortedWith(
            compareByDescending<YouTubeResolverStrategy> { strategyScore(it, nowMillis) }
                .thenBy { strategy ->
                    when (strategy) {
                        YouTubeResolverStrategy.Innertube -> 0
                        YouTubeResolverStrategy.NewPipe -> 1
                        else -> 2
                    }
                },
        )
    }

    fun record(strategy: YouTubeResolverStrategy, success: Boolean, nowMillis: Long = SystemClock.elapsedRealtime()) {
        statsByStrategy.compute(strategy) { _, old ->
            val current = old ?: ResolverStats()
            if (success) {
                current.copy(successes = current.successes + 1, lastSuccessMillis = nowMillis)
            } else {
                current.copy(failures = current.failures + 1)
            }
        }
    }

    private fun strategyScore(strategy: YouTubeResolverStrategy, nowMillis: Long): Int {
        val stats = statsByStrategy[strategy] ?: return 0
        val recentBonus = if (stats.lastSuccessMillis > 0L && nowMillis - stats.lastSuccessMillis <= NETWORK_ENDPOINT_RECENT_SUCCESS_BONUS_MS) 5 else 0
        return stats.successes * NETWORK_ENDPOINT_SUCCESS_WEIGHT - stats.failures * NETWORK_ENDPOINT_FAILURE_WEIGHT + recentBonus
    }
}

private fun String.networkHash(): String =
    sha256(this).take(10)
