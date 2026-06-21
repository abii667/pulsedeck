package com.pulsedeck.app

import android.app.ActivityManager
import android.content.ContentValues
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.provider.MediaStore
import android.util.LruCache
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.settings.model.AlbumArtworkProviderId
import com.pulsedeck.app.settings.model.AlbumArtworkProviderMode
import com.pulsedeck.app.settings.model.AlbumArtQuality
import com.pulsedeck.app.settings.model.AlbumArtSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PREF_ALBUM_ART_OVERRIDES = "album_art_overrides"
private const val ARTWORK_LOG_TAG = "PulseDeckArtwork"
private const val MEMORY_LOG_TAG = "PulseDeckMemory"
private const val PREMIUMDECK_MEMORY_LOG_TAG = "PulseDeckPremiumDeck"
private const val NATIVE_MEMORY_LOG_TAG = "PulseDeckNative"
private const val NORMAL_ART_MEMORY_CACHE_BYTES = 28 * 1024 * 1024
private const val LOW_RAM_ART_MEMORY_CACHE_BYTES = 14 * 1024 * 1024
private const val MAX_REMOTE_ARTWORK_BYTES = 8L * 1024L * 1024L
private const val MAX_REMOTE_PREVIEW_BYTES = 4L * 1024L * 1024L
private const val MAX_REMOTE_THUMBNAIL_BYTES = 3L * 1024L * 1024L
private const val MAX_REMOTE_THUMBNAIL_LOADS = 4
private const val IMAGE_HEAVY_ROUTE_CACHE_TRIM_FRACTION = 0.25f
private const val IMAGE_HEAVY_ROUTE_POST_LEAVE_CACHE_FRACTION = 0.10f
private const val FAILED_ARTWORK_URL_TTL_MS = 10 * 60 * 1000L
private const val FAILED_ARTWORK_URL_CACHE_MAX = 256
private const val MISSING_ARTWORK_KEY_TTL_MS = 5 * 60 * 1000L
private const val MAX_EMBEDDED_ART_SOURCE_PROBES = 2
private val IMAGE_HEAVY_ROUTE_GC_DELAYS_MS = longArrayOf(0L, 1_200L, 4_200L)
private val IMAGE_HEAVY_ROUTE_POST_LEAVE_CLEANUP_DELAYS_MS = longArrayOf(900L, 2_600L, 6_000L)

internal enum class ArtworkDiskBucket(
    val directoryName: String,
) {
    HighQuality("full"),
    Thumbnail("thumb"),
    RemoteThumbnail("remote"),
}

internal enum class ArtworkUseCase(
    val cacheToken: String,
    private val highQualityMaxEdge: Int,
    private val efficientMaxEdge: Int,
    private val originalMaxEdge: Int = highQualityMaxEdge,
    private val placeholderEdge: Int,
    private val cacheBucket: ArtworkDiskBucket,
    val remoteByteLimit: Long,
) {
    FullPlayerArtwork(
        cacheToken = "full_player",
        highQualityMaxEdge = 1080,
        efficientMaxEdge = 720,
        placeholderEdge = 512,
        cacheBucket = ArtworkDiskBucket.HighQuality,
        remoteByteLimit = MAX_REMOTE_ARTWORK_BYTES,
    ),
    AlbumDetailHeroArtwork(
        cacheToken = "album_detail_hero",
        highQualityMaxEdge = 1080,
        efficientMaxEdge = 720,
        placeholderEdge = 512,
        cacheBucket = ArtworkDiskBucket.HighQuality,
        remoteByteLimit = MAX_REMOTE_ARTWORK_BYTES,
    ),
    AlbumGridThumbnail(
        cacheToken = "album_grid",
        highQualityMaxEdge = 384,
        efficientMaxEdge = 320,
        originalMaxEdge = 480,
        placeholderEdge = 192,
        cacheBucket = ArtworkDiskBucket.Thumbnail,
        remoteByteLimit = MAX_REMOTE_PREVIEW_BYTES,
    ),
    SongListThumbnail(
        cacheToken = "song_list",
        highQualityMaxEdge = 160,
        efficientMaxEdge = 128,
        placeholderEdge = 128,
        cacheBucket = ArtworkDiskBucket.Thumbnail,
        remoteByteLimit = MAX_REMOTE_THUMBNAIL_BYTES,
    ),
    YouTubeSearchThumbnail(
        cacheToken = "youtube_search",
        highQualityMaxEdge = 256,
        efficientMaxEdge = 192,
        placeholderEdge = 128,
        cacheBucket = ArtworkDiskBucket.RemoteThumbnail,
        remoteByteLimit = MAX_REMOTE_THUMBNAIL_BYTES,
    ),
    RadioStationThumbnail(
        cacheToken = "radio_station",
        highQualityMaxEdge = 192,
        efficientMaxEdge = 160,
        placeholderEdge = 128,
        cacheBucket = ArtworkDiskBucket.RemoteThumbnail,
        remoteByteLimit = MAX_REMOTE_THUMBNAIL_BYTES,
    ),
    NotificationArtwork(
        cacheToken = "notification",
        highQualityMaxEdge = 512,
        efficientMaxEdge = 384,
        placeholderEdge = 256,
        cacheBucket = ArtworkDiskBucket.Thumbnail,
        remoteByteLimit = MAX_REMOTE_PREVIEW_BYTES,
    ),
    AlbumPickerPreview(
        cacheToken = "album_picker_preview",
        highQualityMaxEdge = 384,
        efficientMaxEdge = 320,
        placeholderEdge = 192,
        cacheBucket = ArtworkDiskBucket.RemoteThumbnail,
        remoteByteLimit = MAX_REMOTE_PREVIEW_BYTES,
    );

    fun maxEdgeFor(settings: AlbumArtSettings): Int =
        when (settings.quality) {
            AlbumArtQuality.Efficient -> efficientMaxEdge
            AlbumArtQuality.High -> highQualityMaxEdge
            AlbumArtQuality.Original -> originalMaxEdge
        }

    fun placeholderEdge(): Int = placeholderEdge

    fun diskBucket(): ArtworkDiskBucket = cacheBucket
}

internal enum class AlbumArtRenderSize(
    val cacheToken: String,
    val thumbnailEdge: Int,
    val defaultUseCase: ArtworkUseCase,
) {
    Thumbnail("thumb", 384, ArtworkUseCase.AlbumGridThumbnail),
    Medium("medium", 768, ArtworkUseCase.AlbumDetailHeroArtwork),
    Full("full", 1400, ArtworkUseCase.FullPlayerArtwork),
}

private object AlbumArtMemoryCache {
    private var maxBytes = NORMAL_ART_MEMORY_CACHE_BYTES

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.safeByteCount()

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) {
                artworkDebugLog("memory_cache_evicted bitmap=${oldValue.width}x${oldValue.height} bytes=${oldValue.safeByteCount()} current_bytes=${size()} max_bytes=$maxBytes")
            }
        }
    }

    @Synchronized
    fun configure(context: Context) {
        context.configureArtworkDebugLogging()
        val targetBytes = artworkMemoryCacheTargetBytes(context)
        if (targetBytes != maxBytes) {
            resizeMaxBytesLocked(targetBytes, "configure low_ram=${context.isLowRamDevice()} pressure=$artworkLowMemoryMode")
        }
    }

    @Synchronized
    fun get(key: String): Bitmap? =
        cache.get(key).also { bitmap ->
            artworkDebugLog("memory_cache_${if (bitmap != null) "hit" else "miss"} key=${shortCacheKey(key)} current_bytes=${cache.size()} max_bytes=$maxBytes")
        }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
        artworkDebugLog("memory_cache_put key=${shortCacheKey(key)} bitmap=${bitmap.width}x${bitmap.height} bytes=${bitmap.safeByteCount()} current_bytes=${cache.size()} max_bytes=$maxBytes")
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
        artworkMemoryDebugLog("memory_cache_clear current_bytes=${cache.size()} max_bytes=$maxBytes")
    }

    @Synchronized
    fun trimToBytes(maxBytes: Int) {
        cache.trimToSize(maxBytes.coerceAtLeast(0))
        artworkMemoryDebugLog("memory_cache_trim target_bytes=${maxBytes.coerceAtLeast(0)} current_bytes=${cache.size()} max_bytes=${this.maxBytes}")
    }

    @Synchronized
    fun trimToFraction(fraction: Float) {
        trimToBytes((maxBytes * fraction.coerceIn(0f, 1f)).roundToInt())
    }

    @Synchronized
    fun enterPressureMode(reason: String) {
        resizeMaxBytesLocked(LOW_RAM_ART_MEMORY_CACHE_BYTES, "pressure:$reason")
    }

    @Synchronized
    fun removeMatching(reason: String, recycleRemoved: Boolean = false, predicate: (String) -> Boolean) {
        val beforeBytes = cache.size()
        var removed = 0
        var recycledBytes = 0
        cache.snapshot().keys.forEach { key ->
            if (predicate(key)) {
                val bitmap = cache.remove(key)
                if (recycleRemoved && bitmap != null && !bitmap.isRecycled) {
                    recycledBytes += bitmap.safeByteCount()
                    bitmap.recycle()
                }
                removed += 1
            }
        }
        artworkMemoryDebugLog(
            "memory_cache_remove_matching reason=$reason entries=$removed recycled_bytes=$recycledBytes before_bytes=$beforeBytes current_bytes=${cache.size()} max_bytes=$maxBytes",
        )
    }

    @Synchronized
    fun snapshotLabel(): String =
        buildString {
            val snapshot = cache.snapshot()
            val remoteEntries = snapshot.filterKeys { it.isRemoteThumbnailArtworkKey() }
            val remoteBytes = remoteEntries.values.sumOf { it.safeByteCount().toLong() }
            append("cache_bytes=${cache.size()} cache_max_bytes=$maxBytes pressure=$artworkLowMemoryMode")
            append(" remote_thumbnail_entries=${remoteEntries.size} remote_thumbnail_bytes=$remoteBytes")
        }

    private fun resizeMaxBytesLocked(targetBytes: Int, reason: String) {
        val normalized = targetBytes.coerceAtLeast(0)
        maxBytes = normalized
        cache.resize(normalized)
        artworkMemoryDebugLog("memory_cache_resize reason=$reason max_bytes=$normalized current_bytes=${cache.size()}")
    }
}

private object ArtworkInFlightRequests {
    private data class InFlightArtworkRequest(
        val deferred: Deferred<Bitmap?>,
        val cancelWhenUnobserved: Boolean,
        val waiters: AtomicInteger = AtomicInteger(0),
    )

    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = ConcurrentHashMap<String, InFlightArtworkRequest>()
    private val remoteThumbnailLoadLimiter = Semaphore(MAX_REMOTE_THUMBNAIL_LOADS)

    suspend fun await(key: String, useCase: ArtworkUseCase? = null, loader: suspend () -> Bitmap?): Bitmap? {
        val thumbnailLifecycle = useCase == ArtworkUseCase.YouTubeSearchThumbnail ||
            useCase == ArtworkUseCase.RadioStationThumbnail
        val createdDeferred = requestScope.async(start = CoroutineStart.LAZY) {
            if (useCase?.limitsConcurrentRemoteLoads() == true) {
                remoteThumbnailLoadLimiter.withPermit {
                    artworkMemoryDebugLog("remote_thumbnail_load_permit use_case=${useCase.cacheToken} active=${requests.size}")
                    loader()
                }
            } else {
                loader()
            }
        }
        val created = InFlightArtworkRequest(
            deferred = createdDeferred,
            cancelWhenUnobserved = thumbnailLifecycle,
        )
        val active = requests.putIfAbsent(key, created)
        val request = active ?: created
        if (active != null) {
            createdDeferred.cancel()
            artworkDebugLog("in_flight_join key=${shortCacheKey(key)} active=${requests.size}")
            if (thumbnailLifecycle) YouTubeNetworkDiagnostics.reportYouTubeThumbnailLifecycle("duplicate", requests.size)
        } else if (thumbnailLifecycle) {
            YouTubeNetworkDiagnostics.reportYouTubeThumbnailLifecycle("start", requests.size)
        }
        if (active == null) artworkDebugLog("in_flight_start key=${shortCacheKey(key)} active=${requests.size}")
        createdDeferred.invokeOnCompletion { error ->
            requests.remove(key, created)
            artworkDebugLog("in_flight_complete key=${shortCacheKey(key)} cancelled=${createdDeferred.isCancelled} error=${error?.javaClass?.simpleName.orEmpty()} active=${requests.size}")
            if (thumbnailLifecycle) {
                val event = when {
                    createdDeferred.isCancelled -> "cancelled"
                    error != null -> "failed"
                    else -> "complete"
                }
                YouTubeNetworkDiagnostics.reportYouTubeThumbnailLifecycle(event, requests.size)
            }
        }
        if (active == null) createdDeferred.start()
        request.waiters.incrementAndGet()
        return try {
            request.deferred.await()
        } finally {
            val waiters = request.waiters.decrementAndGet()
            if (request.cancelWhenUnobserved && waiters <= 0 && request.deferred.isActive) {
                YouTubeNetworkDiagnostics.reportYouTubeThumbnailLifecycle("observer_cancel", requests.size)
                request.deferred.cancel()
            }
        }
    }

    fun clear() {
        requests.values.forEach { it.deferred.cancel() }
        requests.clear()
        artworkMemoryDebugLog("in_flight_clear active=0")
    }

    fun clearMatching(reason: String, predicate: (String) -> Boolean) {
        var cancelled = 0
        requests.entries.forEach { (key, request) ->
            if (predicate(key)) {
                request.deferred.cancel()
                if (requests.remove(key, request)) cancelled += 1
            }
        }
        artworkMemoryDebugLog("in_flight_clear_matching reason=$reason cancelled=$cancelled active=${requests.size}")
    }

    fun activeCount(): Int = requests.size

    fun activeMatchingCount(predicate: (String) -> Boolean): Int =
        requests.keys.count(predicate)
}

private fun String.isRemoteThumbnailArtworkKey(): Boolean =
    contains("youtube_search") || contains("radio_station")

private fun ArtworkUseCase.releasesBitmapOnDispose(): Boolean =
    this == ArtworkUseCase.YouTubeSearchThumbnail ||
        this == ArtworkUseCase.RadioStationThumbnail ||
        this == ArtworkUseCase.AlbumPickerPreview

private object ArtworkRouteThumbnailStates {
    private val activeByUseCase = ConcurrentHashMap<String, AtomicInteger>()

    fun register(useCase: ArtworkUseCase) {
        activeByUseCase.getOrPut(useCase.cacheToken) { AtomicInteger(0) }.incrementAndGet()
    }

    fun unregister(useCase: ArtworkUseCase) {
        activeByUseCase[useCase.cacheToken]?.decrementAndGet()?.let { next ->
            if (next <= 0) activeByUseCase.remove(useCase.cacheToken)
        }
    }

    fun snapshotLabel(): String {
        val entries = activeByUseCase
            .mapValues { (_, count) -> count.get().coerceAtLeast(0) }
            .filterValues { it > 0 }
        val total = entries.values.sum()
        val detail = entries.entries.joinToString(",") { "${it.key}:${it.value}" }
        return "route_thumbnail_states=$total route_thumbnail_state_detail=${detail.ifBlank { "none" }}"
    }
}

private val artworkCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

@Volatile
private var artworkLowMemoryMode = false

private object ArtworkDecodeStats {
    private val countsByUseCase = ConcurrentHashMap<String, AtomicInteger>()
    private val bytesByUseCase = ConcurrentHashMap<String, AtomicLong>()

    fun record(useCase: ArtworkUseCase, bitmap: Bitmap) {
        if (!artworkDebugLoggingEnabled) return
        val token = useCase.cacheToken
        val count = countsByUseCase.getOrPut(token) { AtomicInteger(0) }.incrementAndGet()
        val bytes = bytesByUseCase.getOrPut(token) { AtomicLong(0L) }.addAndGet(bitmap.safeByteCount().toLong())
        artworkDebugLog("decode_stats use_case=$token count=$count total_bytes=$bytes")
    }

    fun clear() {
        countsByUseCase.clear()
        bytesByUseCase.clear()
    }
}

private data class TimedArtworkFailure(
    val expiresAtMs: Long,
    val reason: String,
)

@Volatile
private var artworkDebugLoggingEnabled = false

private val missingAlbumArtKeys = ConcurrentHashMap<String, Long>()
private val failedArtworkUrls = ConcurrentHashMap<String, TimedArtworkFailure>()

private fun artworkDebugLog(message: String) {
    if (artworkDebugLoggingEnabled) Log.d(ARTWORK_LOG_TAG, message)
}

private fun artworkMemoryDebugLog(message: String) {
    if (artworkDebugLoggingEnabled) Log.d(MEMORY_LOG_TAG, message)
}

private fun premiumDeckMemoryDebugLog(message: String) {
    if (artworkDebugLoggingEnabled) Log.d(PREMIUMDECK_MEMORY_LOG_TAG, message)
}

private fun nativeMemoryDebugLog(message: String) {
    if (artworkDebugLoggingEnabled) Log.d(NATIVE_MEMORY_LOG_TAG, message)
}

private fun shortCacheKey(key: String): String =
    sha256(key).take(10)

private fun routeMemoryLabel(routeName: String): String =
    when {
        routeName == "YouTube" || routeName.contains("YouTube") -> "PremiumDeck"
        routeName == "PulseRadio" || routeName.contains("PulseRadio") -> "PulseRadio"
        else -> routeName
    }

private fun isPremiumDeckRoute(routeName: String): Boolean =
    routeMemoryLabel(routeName) == "PremiumDeck"

private fun ArtworkUseCase.limitsConcurrentRemoteLoads(): Boolean =
    this == ArtworkUseCase.YouTubeSearchThumbnail ||
        this == ArtworkUseCase.RadioStationThumbnail ||
        this == ArtworkUseCase.AlbumPickerPreview

private fun ArtworkUseCase.usesCompactOpaqueDecode(): Boolean =
    this == ArtworkUseCase.YouTubeSearchThumbnail

private fun Context.isLowRamDevice(): Boolean =
    ((applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true)

private fun Context.isDebuggableApp(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

private fun Context.configureArtworkDebugLogging() {
    artworkDebugLoggingEnabled = isDebuggableApp()
}

private fun artworkMemoryCacheTargetBytes(context: Context): Int =
    if (artworkLowMemoryMode || context.isLowRamDevice()) LOW_RAM_ART_MEMORY_CACHE_BYTES else NORMAL_ART_MEMORY_CACHE_BYTES

private fun missingArtworkCacheHit(key: String): Boolean {
    val expiresAt = missingAlbumArtKeys[key] ?: return false
    val now = SystemClock.elapsedRealtime()
    if (expiresAt <= now) {
        missingAlbumArtKeys.remove(key, expiresAt)
        return false
    }
    artworkDebugLog("missing_artwork_cache_hit key=${shortCacheKey(key)}")
    return true
}

private fun recordMissingArtworkKey(key: String) {
    missingAlbumArtKeys[key] = SystemClock.elapsedRealtime() + MISSING_ARTWORK_KEY_TTL_MS
}

private fun failedArtworkUrlCacheHit(url: String): Boolean {
    val failure = failedArtworkUrls[url] ?: return false
    val now = SystemClock.elapsedRealtime()
    if (failure.expiresAtMs <= now) {
        failedArtworkUrls.remove(url, failure)
        return false
    }
    artworkDebugLog("failed_url_cache_hit url=${shortCacheKey(url)} reason=${failure.reason}")
    return true
}

private fun recordFailedArtworkUrl(url: String, reason: String) {
    failedArtworkUrls[url] = TimedArtworkFailure(
        expiresAtMs = SystemClock.elapsedRealtime() + FAILED_ARTWORK_URL_TTL_MS,
        reason = reason.take(80),
    )
    trimFailedArtworkUrlCache()
    artworkDebugLog("failed_url_recorded url=${shortCacheKey(url)} reason=${reason.take(80)} size=${failedArtworkUrls.size}")
}

private fun trimFailedArtworkUrlCache() {
    if (failedArtworkUrls.size <= FAILED_ARTWORK_URL_CACHE_MAX) return
    val now = SystemClock.elapsedRealtime()
    failedArtworkUrls.entries
        .filter { it.value.expiresAtMs <= now }
        .forEach { failedArtworkUrls.remove(it.key, it.value) }
    if (failedArtworkUrls.size <= FAILED_ARTWORK_URL_CACHE_MAX) return
    failedArtworkUrls.entries
        .sortedBy { it.value.expiresAtMs }
        .take((failedArtworkUrls.size - FAILED_ARTWORK_URL_CACHE_MAX).coerceAtLeast(0))
        .forEach { failedArtworkUrls.remove(it.key, it.value) }
}

private fun artworkDiskBucketLimitMb(context: Context, maxCacheMb: Int, bucket: ArtworkDiskBucket): Int =
    (CacheBudgetManager.artworkBucketBudgetBytes(context, maxCacheMb, bucket.directoryName) / (1024L * 1024L)).toInt()
internal data class ArtworkCandidate(
    val provider: AlbumArtworkProviderId,
    val imageUrl: String,
    val landingUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val confidence: Int = 0,
    val matchReason: String = "",
)

internal data class ArtworkCandidateScore(
    val candidate: ArtworkCandidate,
    val score: Int,
)

internal fun artworkUpgradeAccepted(
    currentWidth: Int,
    currentHeight: Int,
    candidateWidth: Int,
    candidateHeight: Int,
    minimumUpgradePercent: Int,
): Boolean {
    if (candidateWidth <= 0 || candidateHeight <= 0) return currentWidth <= 0 || currentHeight <= 0
    if (currentWidth <= 0 || currentHeight <= 0) return true
    val threshold = 1f + minimumUpgradePercent.coerceIn(0, 300) / 100f
    val currentEdge = max(currentWidth, currentHeight).coerceAtLeast(1)
    val candidateEdge = max(candidateWidth, candidateHeight)
    val currentPixels = currentWidth.toLong() * currentHeight.toLong()
    val candidatePixels = candidateWidth.toLong() * candidateHeight.toLong()
    return candidateEdge >= currentEdge * threshold || candidatePixels >= (currentPixels * threshold).toLong()
}

internal fun rankArtworkCandidates(
    candidates: List<ArtworkCandidate>,
    providerOrder: List<AlbumArtworkProviderId>,
): List<ArtworkCandidateScore> {
    val orderIndex = providerOrder.withIndex().associate { it.value to it.index }
    return candidates
        .filter { it.imageUrl.startsWith("http", ignoreCase = true) && it.confidence >= 40 }
        .distinctBy { it.imageUrl.substringBefore('?') }
        .map { candidate ->
            val edge = max(candidate.width, candidate.height)
            val dimensionScore = when {
                edge >= 3000 -> 80
                edge >= 2000 -> 64
                edge >= 1200 -> 48
                edge >= 800 -> 30
                edge >= 500 -> 16
                else -> 0
            }
            val orderPenalty = (orderIndex[candidate.provider] ?: 99) * 8
            ArtworkCandidateScore(candidate, candidate.confidence + dimensionScore - orderPenalty)
        }
        .sortedWith(compareByDescending<ArtworkCandidateScore> { it.score }.thenByDescending { max(it.candidate.width, it.candidate.height) })
}

internal interface ArtworkProvider {
    val configured: Boolean
    suspend fun resolveArtwork(context: Context, album: Album, coverUri: Uri?, sourceUris: List<Uri>, settings: AlbumArtSettings): Uri?
}

private object DisabledArtworkProvider : ArtworkProvider {
    override val configured: Boolean = false
    override suspend fun resolveArtwork(context: Context, album: Album, coverUri: Uri?, sourceUris: List<Uri>, settings: AlbumArtSettings): Uri? = null
}

private object CoverArchiveYouTubeArtworkProvider : ArtworkProvider {
    override val configured: Boolean = true

    override suspend fun resolveArtwork(context: Context, album: Album, coverUri: Uri?, sourceUris: List<Uri>, settings: AlbumArtSettings): Uri? {
        if (!settings.highestQualityCovers && (coverUri?.scheme.equals("http", ignoreCase = true) || coverUri?.scheme.equals("https", ignoreCase = true))) return coverUri
        if (!artworkLookupMetadataReliable(album.title, album.artist)) return null
        val candidates = findArtworkCandidateScores(album, coverUri, sourceUris, settings)
        if (settings.providerMode == AlbumArtworkProviderMode.YouTubeOnly) {
            return candidates.firstOrNull { it.candidate.provider == AlbumArtworkProviderId.YouTube }?.candidate?.imageUrl?.let(Uri::parse)
        }
        return candidates
            .firstOrNull()
            ?.candidate
            ?.imageUrl
            ?.let(Uri::parse)
    }
}

internal object AlbumArtworkRuntime {
    var settings by mutableStateOf(AlbumArtSettings())
    var cacheRevision by mutableIntStateOf(0)
    var manualOverrides by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var manualOverridesLoaded = false
    private var manualOverridesLoadStarted = false
    val provider: ArtworkProvider = CoverArchiveYouTubeArtworkProvider

    fun update(next: AlbumArtSettings) {
        settings = next.normalized()
    }

    suspend fun loadManualOverridesIfNeeded(context: Context) {
        if (manualOverridesLoaded || manualOverridesLoadStarted) return
        manualOverridesLoadStarted = true
        manualOverrides = withContext(Dispatchers.IO) { loadAlbumArtOverrides(context.applicationContext) }
        manualOverridesLoaded = true
    }

    fun manualOverrideUri(album: Album?): Uri? =
        album?.let(::albumArtOverrideKey)
            ?.let { manualOverrides[it] }
            ?.takeIf { it.startsWith("http", ignoreCase = true) || it.startsWith("content:", ignoreCase = true) || it.startsWith("file:", ignoreCase = true) }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    suspend fun applyManualOverride(
        context: Context,
        album: Album,
        sourceUris: List<Uri>,
        candidate: ArtworkCandidate,
        settings: AlbumArtSettings,
    ): Boolean {
        val uri = runCatching { Uri.parse(candidate.imageUrl) }.getOrNull() ?: return false
        val selectedBitmap = withContext(Dispatchers.IO) {
            val useCase = ArtworkUseCase.FullPlayerArtwork
            val normalizedSettings = settings.normalized()
            loadAlbumArtUriBitmap(
                context = context,
                uri = uri,
                allowLocal = false,
                maxEdge = useCase.maxEdgeFor(normalizedSettings),
                useCase = useCase,
            )
        } ?: return false
        val normalizedSettings = settings.normalized()
        val useCase = ArtworkUseCase.FullPlayerArtwork
        val diskCacheKey = albumArtCacheKey(album, uri, sourceUris)
            ?.let { "$it|${normalizedSettings.artworkCacheSignature(useCase, useCase.maxEdgeFor(normalizedSettings))}" }
        withContext(Dispatchers.IO) {
            if (diskCacheKey != null) writeAlbumArtDiskCache(context, diskCacheKey, selectedBitmap, normalizedSettings.cacheSizeMb, useCase.diskBucket())
            if (normalizedSettings.saveFolderCover) writeAlbumFolderCoverIfAllowed(context, sourceUris, selectedBitmap)
        }
        val next = manualOverrides + (albumArtOverrideKey(album) to candidate.imageUrl)
        withContext(Dispatchers.IO) { saveAlbumArtOverrides(context, next) }
        manualOverrides = next
        invalidate()
        return true
    }

    fun invalidate() {
        AlbumArtMemoryCache.clear()
        ArtworkInFlightRequests.clear()
        ArtworkDecodeStats.clear()
        missingAlbumArtKeys.clear()
        cacheRevision += 1
    }

    fun onRouteVisible(routeName: String) {
        debugMemorySnapshot("route_visible", routeName)
    }

    fun onRouteHidden(routeName: String, nextRouteName: String) {
        val reason = "route_hidden from=$routeName to=$nextRouteName"
        debugMemorySnapshot("route_hidden_before_cleanup", routeName, nextRouteName)
        releaseHiddenRouteThumbnails(reason)
        AlbumArtMemoryCache.trimToFraction(IMAGE_HEAVY_ROUTE_CACHE_TRIM_FRACTION)
        debugMemorySnapshot("route_hidden_after_cleanup", routeName, nextRouteName)
        scheduleHiddenRouteCleanupBurst(reason, routeName, nextRouteName)
        scheduleBitmapGcBurst("route_hidden:$routeName")
    }

    fun onLowMemory() {
        enterLowMemoryMode("on_low_memory")
        invalidate()
        scheduleBitmapGc("on_low_memory", delayMillis = 0L)
    }

    fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                enterLowMemoryMode("trim_moderate:$level")
                invalidate()
                scheduleBitmapGc("trim_moderate:$level", delayMillis = 0L)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                enterLowMemoryMode("trim_background:$level")
                releaseHiddenRouteThumbnails("trim_background:$level")
                AlbumArtMemoryCache.trimToFraction(0.25f)
                missingAlbumArtKeys.clear()
                failedArtworkUrls.clear()
                cacheRevision += 1
                artworkMemoryDebugLog("trim_memory level=$level action=background ${memorySnapshotLabel()}")
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                releaseHiddenRouteThumbnails("trim_ui_hidden:$level")
                AlbumArtMemoryCache.trimToFraction(0.50f)
                cacheRevision += 1
                artworkMemoryDebugLog("trim_memory level=$level action=ui_hidden ${memorySnapshotLabel()}")
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                enterLowMemoryMode("trim_running_critical:$level")
                releaseHiddenRouteThumbnails("trim_running_critical:$level")
                invalidate()
                scheduleBitmapGc("trim_running_critical:$level", delayMillis = 0L)
                artworkMemoryDebugLog("trim_memory level=$level action=running_critical ${memorySnapshotLabel()}")
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                enterLowMemoryMode("trim_running_low:$level")
                releaseHiddenRouteThumbnails("trim_running_low:$level")
                AlbumArtMemoryCache.trimToFraction(0.10f)
                ArtworkInFlightRequests.clear()
                missingAlbumArtKeys.clear()
                failedArtworkUrls.clear()
                cacheRevision += 1
                scheduleBitmapGc("trim_running_low:$level", delayMillis = 350L)
                artworkMemoryDebugLog("trim_memory level=$level action=running_low ${memorySnapshotLabel()}")
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                releaseHiddenRouteThumbnails("trim_running_moderate:$level")
                AlbumArtMemoryCache.trimToFraction(0.25f)
                cacheRevision += 1
                artworkMemoryDebugLog("trim_memory level=$level action=running_moderate ${memorySnapshotLabel()}")
            }
        }
    }

    private fun enterLowMemoryMode(reason: String) {
        artworkLowMemoryMode = true
        AlbumArtMemoryCache.enterPressureMode(reason)
        artworkMemoryDebugLog("low_memory_mode reason=$reason ${memorySnapshotLabel()}")
    }

    private fun releaseHiddenRouteThumbnails(reason: String) {
        ArtworkInFlightRequests.clearMatching(reason) { it.isRemoteThumbnailArtworkKey() }
        AlbumArtMemoryCache.removeMatching(reason, recycleRemoved = true) { it.isRemoteThumbnailArtworkKey() }
        failedArtworkUrls.clear()
        missingAlbumArtKeys.clear()
        artworkMemoryDebugLog("route_thumbnail_cleanup reason=$reason ${memorySnapshotLabel()}")
    }

    private fun scheduleHiddenRouteCleanupBurst(reason: String, routeName: String, nextRouteName: String) {
        IMAGE_HEAVY_ROUTE_POST_LEAVE_CLEANUP_DELAYS_MS.forEach { delayMillis ->
            artworkCleanupScope.launch {
                delay(delayMillis)
                val delayedReason = "$reason delayed_ms=$delayMillis"
                releaseHiddenRouteThumbnails(delayedReason)
                AlbumArtMemoryCache.trimToFraction(IMAGE_HEAVY_ROUTE_POST_LEAVE_CACHE_FRACTION)
                debugMemorySnapshot("route_hidden_delayed_cleanup", routeName, nextRouteName)
            }
        }
    }

    private fun scheduleBitmapGc(reason: String, delayMillis: Long) {
        artworkCleanupScope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            Runtime.getRuntime().gc()
            System.runFinalization()
            Runtime.getRuntime().gc()
            artworkMemoryDebugLog("bitmap_gc reason=$reason ${memorySnapshotLabel()}")
            debugMemorySnapshot("bitmap_gc", reason)
        }
    }

    private fun scheduleBitmapGcBurst(reason: String) {
        IMAGE_HEAVY_ROUTE_GC_DELAYS_MS.forEach { delayMillis ->
            scheduleBitmapGc("$reason delay_ms=$delayMillis", delayMillis)
        }
    }

    private fun memorySnapshotLabel(): String =
        "${AlbumArtMemoryCache.snapshotLabel()} in_flight=${ArtworkInFlightRequests.activeCount()} in_flight_remote_thumbnails=${ArtworkInFlightRequests.activeMatchingCount { it.isRemoteThumbnailArtworkKey() }} ${ArtworkRouteThumbnailStates.snapshotLabel()} cache_revision=$cacheRevision"

    private fun debugMemorySnapshot(reason: String, routeName: String? = null, nextRouteName: String? = null) {
        if (!artworkDebugLoggingEnabled) return
        val route = routeName?.let(::routeMemoryLabel) ?: "n/a"
        val nextRoute = nextRouteName?.let(::routeMemoryLabel) ?: "n/a"
        val runtime = Runtime.getRuntime()
        val javaTotal = runtime.totalMemory()
        val javaFree = runtime.freeMemory()
        val javaUsed = (javaTotal - javaFree).coerceAtLeast(0L)
        val artworkLabel = memorySnapshotLabel()
        val heapLabel = "java_used_bytes=$javaUsed java_total_bytes=$javaTotal java_max_bytes=${runtime.maxMemory()}"
        val nativeLabel = "native_allocated_bytes=${Debug.getNativeHeapAllocatedSize()} native_heap_bytes=${Debug.getNativeHeapSize()} native_free_bytes=${Debug.getNativeHeapFreeSize()} pss_kb=${Debug.getPss()}"
        artworkMemoryDebugLog("snapshot reason=$reason route=$route next=$nextRoute $artworkLabel $heapLabel $nativeLabel")
        nativeMemoryDebugLog("snapshot reason=$reason route=$route next=$nextRoute $nativeLabel")
        if (routeName != null && isPremiumDeckRoute(routeName)) {
            premiumDeckMemoryDebugLog("snapshot reason=$reason route=$route next=$nextRoute $artworkLabel $heapLabel $nativeLabel")
        }
    }

    fun onPremiumDeckComposition(
        reason: String,
        sourceCount: Int,
        acceptedSourceCount: Int,
        visibleSourceCount: Int,
        playlistCount: Int,
        albumDraftCount: Int,
        discoveryResultCount: Int,
        personalizationMixCount: Int,
        followedArtistCount: Int,
        releaseNotificationCount: Int,
        podcastEpisodeCount: Int,
        searchResultCount: Int,
        prepareStateCount: Int,
        visibleShelfCount: Int,
    ) {
        if (!artworkDebugLoggingEnabled) return
        premiumDeckMemoryDebugLog(
            "route_state reason=$reason source_count=$sourceCount accepted_source_count=$acceptedSourceCount " +
                "visible_source_count=$visibleSourceCount playlist_count=$playlistCount album_draft_count=$albumDraftCount " +
                "discovery_result_count=$discoveryResultCount personalization_mix_count=$personalizationMixCount " +
                "followed_artist_count=$followedArtistCount release_notification_count=$releaseNotificationCount " +
                "podcast_episode_count=$podcastEpisodeCount search_result_count=$searchResultCount prepare_state_count=$prepareStateCount " +
                "visible_shelf_count=$visibleShelfCount " +
                memorySnapshotLabel(),
        )
        debugMemorySnapshot("premiumdeck_$reason", "YouTube")
    }

    fun onPremiumDeckNativeState(reason: String, health: String, productionReady: Boolean) {
        if (!artworkDebugLoggingEnabled) return
        premiumDeckMemoryDebugLog("native_state reason=$reason tinyrec_health=$health tinyrec_production_ready=$productionReady ${memorySnapshotLabel()}")
        debugMemorySnapshot("premiumdeck_native_$reason", "YouTube")
    }
}

private fun albumArtOverrideKey(album: Album): String =
    listOf(
        album.key,
        album.artist.normalizedSearchText(),
        album.artSourceUris.firstOrNull()?.toString().orEmpty(),
    ).joinToString("|")

private suspend fun findArtworkCandidateScores(
    album: Album,
    coverUri: Uri?,
    sourceUris: List<Uri>,
    settings: AlbumArtSettings,
): List<ArtworkCandidateScore> {
    if (PulseOnlineRuntime.settings.offlineMode) return emptyList()
    if (!artworkLookupMetadataReliable(album.title, album.artist)) return emptyList()
    val youtubeDirect = sourceUris.firstNotNullOfOrNull { uri ->
        detectYouTubeSource(uri.toString())
            ?.takeIf { it.kind == YouTubeSourceKind.Video }
            ?.sourceId
            ?.let(::youtubeThumbnailUrlForVideoId)
            ?.let { url ->
                ArtworkCandidate(
                    provider = AlbumArtworkProviderId.YouTube,
                    imageUrl = url,
                    width = 480,
                    height = 360,
                    confidence = 58,
                    matchReason = "Direct stream thumbnail",
                )
            }
    }
    val candidates = buildList {
        settings.providerOrder.forEach { provider ->
            when (provider) {
                AlbumArtworkProviderId.CoverArtArchive -> addAll(resolveCoverArchiveArtworkCandidates(album, settings))
                AlbumArtworkProviderId.AppleItunes -> addAll(resolveAppleArtworkCandidates(album))
                AlbumArtworkProviderId.Deezer -> addAll(resolveDeezerArtworkCandidates(album))
                AlbumArtworkProviderId.MusicHoarders -> addAll(resolveMusicHoardersArtworkCandidates(album))
                AlbumArtworkProviderId.PageMetadata -> addAll(resolvePageMetadataArtworkCandidates(album))
                AlbumArtworkProviderId.YouTube -> {
                    youtubeDirect?.let(::add)
                    resolveYouTubeArtworkCandidate(album)?.let(::add)
                }
            }
        }
    }.filterNot { it.imageUrl == coverUri?.toString() }
    return rankArtworkCandidates(candidates, settings.providerOrder)
}

internal fun artworkLookupMetadataReliable(albumTitle: String, albumArtist: String): Boolean {
    val title = albumTitle.cleanArtworkSearchText().normalizedSearchText()
    val artist = albumArtist.cleanArtworkSearchText().normalizedSearchText()
    val weakTitles = setOf("", "unknown album", "music", "audio", STREAM_LIBRARY_ALBUM.normalizedSearchText())
    val weakArtists = setOf("", "unknown artist", "unknown", "various artists", "various", STREAM_LIBRARY_ALBUM_ARTIST.normalizedSearchText())
    return title !in weakTitles && artist !in weakArtists && title.length >= 2 && artist.length >= 2
}

private fun loadAlbumArtOverrides(context: Context): Map<String, String> =
    runCatching {
        val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE).getString(PREF_ALBUM_ART_OVERRIDES, null).orEmpty()
        if (raw.isBlank()) return@runCatching emptyMap()
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key ->
                json.optString(key)
                    .takeIf { it.startsWith("http", ignoreCase = true) || it.startsWith("content:", ignoreCase = true) || it.startsWith("file:", ignoreCase = true) }
                    ?.let { put(key, it) }
            }
        }
    }.getOrDefault(emptyMap())

private fun saveAlbumArtOverrides(context: Context, overrides: Map<String, String>) {
    val json = JSONObject()
    overrides.forEach { (key, value) -> json.put(key, value) }
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_ALBUM_ART_OVERRIDES, json.toString())
        .apply()
}

internal data class AdaptivePalette(
    val dominant: Color,
    val muted: Color,
    val accent: Color,
    val deep: Color,
)

internal data class AlbumArtPickerTarget(
    val album: Album,
    val sourceUris: List<Uri>,
)

@Composable
internal fun AlbumArtPickerDialog(
    target: AlbumArtPickerTarget,
    settings: AlbumArtSettings,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var titleQuery by remember(target.album.key) { mutableStateOf(target.album.title.cleanArtworkSearchText()) }
    var artistQuery by remember(target.album.key) {
        mutableStateOf(target.album.artist.takeIf { artworkLookupMetadataReliable(target.album.title, it) }.orEmpty())
    }
    var candidates by remember(target.album.key) { mutableStateOf<List<ArtworkCandidateScore>>(emptyList()) }
    var loading by remember(target.album.key) { mutableStateOf(false) }
    var applyingUrl by remember(target.album.key) { mutableStateOf<String?>(null) }
    var message by remember(target.album.key) {
        mutableStateOf(
            if (artistQuery.isBlank()) "Add the artist name to search accurately." else "Searching high quality covers...",
        )
    }

    suspend fun searchCovers() {
        val albumTitle = titleQuery.cleanArtworkSearchText()
        val albumArtist = artistQuery.cleanArtworkSearchText()
        if (!artworkLookupMetadataReliable(albumTitle, albumArtist)) {
            candidates = emptyList()
            message = "Enter both album title and artist so PulseDeck does not guess."
            return
        }
        loading = true
        message = "Searching high quality covers..."
        val queryAlbum = target.album.copy(title = albumTitle, artist = albumArtist)
        val next = findArtworkCandidateScores(
            album = queryAlbum,
            coverUri = target.album.coverUri,
            sourceUris = target.sourceUris,
            settings = settings.copy(downloadAlbumArt = true, highestQualityCovers = true).normalized(),
        ).take(12)
        candidates = next
        message = if (next.isEmpty()) "No confident covers found. Try a more exact artist or album name." else "${next.size} cover options found"
        loading = false
    }

    LaunchedEffect(target.album.key) {
        if (artworkLookupMetadataReliable(titleQuery, artistQuery)) searchCovers()
    }

    BasicInfoModal(title = "Change Album Art", subtitle = target.album.title, onDismiss = onDismiss) {
        CoverSearchField(
            label = "Album",
            value = titleQuery,
            placeholder = "Album title",
            onValueChange = { titleQuery = it },
        )
        Spacer(Modifier.height(10.dp))
        CoverSearchField(
            label = "Artist",
            value = artistQuery,
            placeholder = "Artist name",
            onValueChange = { artistQuery = it },
        )
        Spacer(Modifier.height(12.dp))
        SleepDialogButton(
            label = if (loading) "Searching..." else "Search Covers",
            modifier = Modifier.fillMaxWidth(),
            tone = Blue.copy(alpha = 0.42f),
            onClick = {
                if (!loading) {
                    scope.launch { searchCovers() }
                }
            },
        )
        Text(
            message,
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 410.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(candidates, key = { it.candidate.imageUrl }) { score ->
                CoverCandidateRow(
                    score = score,
                    applying = applyingUrl == score.candidate.imageUrl,
                    onUse = {
                        if (applyingUrl != null) return@CoverCandidateRow
                        applyingUrl = score.candidate.imageUrl
                        scope.launch {
                            val applied = AlbumArtworkRuntime.applyManualOverride(
                                context = context,
                                album = target.album,
                                sourceUris = target.sourceUris,
                                candidate = score.candidate,
                                settings = settings,
                            )
                            applyingUrl = null
                            Toast.makeText(
                                context,
                                if (applied) "Album art updated" else "Could not apply this cover",
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (applied) onApplied()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CoverSearchField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.34f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isBlank()) Text(placeholder, color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    inner()
                },
            )
        }
    }
}

@Composable
private fun CoverCandidateRow(
    score: ArtworkCandidateScore,
    applying: Boolean,
    onUse: () -> Unit,
) {
    val candidate = score.candidate
    val candidateUri = remember(candidate.imageUrl) { runCatching { Uri.parse(candidate.imageUrl) }.getOrNull() }
    val bitmap = candidateUri?.let {
        rememberAlbumArtBitmap(
            album = null,
            coverUri = it,
            sourceUri = null,
            sourceUris = emptyList(),
            useCase = ArtworkUseCase.AlbumPickerPreview,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PulseIcon(DeckIcon.Disc, Color.White.copy(alpha = 0.62f), Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.provider.label, color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    candidate.matchReason.takeIf { it.isNotBlank() },
                    candidate.dimensionLabel(),
                    "Score ${score.score}",
                ).joinToString("  |  "),
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        SleepDialogButton(
            label = if (applying) "..." else "Use",
            modifier = Modifier.width(72.dp),
            tone = Blue.copy(alpha = 0.38f),
            onClick = onUse,
        )
    }
}

private fun ArtworkCandidate.dimensionLabel(): String =
    if (width > 0 && height > 0) "${width}x$height" else "Unknown size"

@Composable
internal fun Art(
    album: Album,
    modifier: Modifier = Modifier,
    corner: Dp,
    renderSize: AlbumArtRenderSize = AlbumArtRenderSize.Thumbnail,
    useCase: ArtworkUseCase = renderSize.defaultUseCase,
) {
    val bitmap = rememberAlbumArtBitmap(album, useCase)
    Box(modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(album.tint, album.alt, lerp(album.tint, Color.Black, 0.28f))))) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = album.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.22f)))))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(Color.White.copy(0.14f), min(w, h) * 0.42f, Offset(w * 0.78f, h * 0.26f))
                drawCircle(Color.Black.copy(0.18f), min(w, h) * 0.34f, Offset(w * 0.22f, h * 0.78f))
                drawLine(Color.White.copy(0.32f), Offset(w * 0.10f, h * 0.12f), Offset(w * 0.88f, h * 0.74f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
                drawLine(Color.Black.copy(0.22f), Offset(w * 0.16f, h * 0.82f), Offset(w * 0.90f, h * 0.30f), strokeWidth = w * 0.028f, cap = StrokeCap.Round)
            }
            Text(album.mark, color = Color.White.copy(0.90f), fontSize = 42.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
        }
    }
}

@Composable
private fun RawAlbumArtwork(album: Album, modifier: Modifier = Modifier, corner: Dp, renderSize: AlbumArtRenderSize = AlbumArtRenderSize.Medium) {
    val bitmap = rememberAlbumArtBitmap(album, renderSize)
    Box(modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(album.tint, album.alt, lerp(album.tint, Color.Black, 0.28f))))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = album.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(Color.White.copy(0.14f), min(w, h) * 0.42f, Offset(w * 0.78f, h * 0.26f))
                drawCircle(Color.Black.copy(0.18f), min(w, h) * 0.34f, Offset(w * 0.22f, h * 0.78f))
                drawLine(Color.White.copy(0.32f), Offset(w * 0.10f, h * 0.12f), Offset(w * 0.88f, h * 0.74f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
                drawLine(Color.Black.copy(0.22f), Offset(w * 0.16f, h * 0.82f), Offset(w * 0.90f, h * 0.30f), strokeWidth = w * 0.028f, cap = StrokeCap.Round)
            }
            Text(album.mark, color = Color.White.copy(0.90f), fontSize = 42.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
        }
    }
}

@Composable
internal fun AlbumHeroArtwork(album: Album, modifier: Modifier = Modifier, corner: Dp) {
    Art(
        album = album,
        modifier = modifier,
        corner = corner,
        renderSize = AlbumArtRenderSize.Medium,
        useCase = ArtworkUseCase.AlbumDetailHeroArtwork,
    )
}

@Composable
internal fun rememberAlbumPalette(album: Album): Pair<Color, Color> {
    val bitmap = rememberAlbumArtBitmap(album, ArtworkUseCase.SongListThumbnail)
    return remember(album.key, bitmap) {
        bitmap?.let { paletteFromBitmap(it) } ?: (album.tint to album.alt)
    }
}

private fun paletteFromBitmap(bitmap: Bitmap): Pair<Color, Color> {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val stepX = (width / 18).coerceAtLeast(1)
    val stepY = (height / 18).coerceAtLeast(1)
    val hsv = FloatArray(3)
    var plainRed = 0L
    var plainGreen = 0L
    var plainBlue = 0L
    var weightedRed = 0.0
    var weightedGreen = 0.0
    var weightedBlue = 0.0
    var totalWeight = 0.0
    var count = 0L
    var y = stepY / 2
    while (y < height) {
        var x = stepX / 2
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            plainRed += red
            plainGreen += green
            plainBlue += blue
            android.graphics.Color.RGBToHSV(red, green, blue, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            val usable = if (value < 0.08f || value > 0.96f) 0.30 else 1.0
            val weight = ((0.18 + saturation * 1.55) * (0.42 + value) * usable)
            weightedRed += red * weight
            weightedGreen += green * weight
            weightedBlue += blue * weight
            totalWeight += weight
            count += 1L
            x += stepX
        }
        y += stepY
    }
    if (count <= 0L) return Color(0xFF2F3546) to Color(0xFF181B24)
    val base = if (totalWeight > 0.0) {
        Color(
            red = ((weightedRed / totalWeight) / 255.0).toFloat().coerceIn(0f, 1f),
            green = ((weightedGreen / totalWeight) / 255.0).toFloat().coerceIn(0f, 1f),
            blue = ((weightedBlue / totalWeight) / 255.0).toFloat().coerceIn(0f, 1f),
            alpha = 1f,
        )
    } else {
        Color(
            red = (plainRed / count) / 255f,
            green = (plainGreen / count) / 255f,
            blue = (plainBlue / count) / 255f,
            alpha = 1f,
        )
    }
    val vibrant = enrichPaletteColor(base)
    return lerp(vibrant, Color.White, 0.06f) to lerp(vibrant, Color.Black, 0.46f)
}

internal fun adaptivePaletteFromBitmap(bitmap: Bitmap, fallbackTint: Color, fallbackAlt: Color): AdaptivePalette {
    val extracted = paletteFromBitmap(bitmap)
    return adaptivePaletteFromColors(extracted.first, extracted.second, fallbackTint, fallbackAlt)
}

internal fun adaptivePaletteFromColors(dominant: Color, secondary: Color, fallbackTint: Color = dominant, fallbackAlt: Color = secondary): AdaptivePalette {
    val groundedDominant = earthTone(lerp(dominant, fallbackTint, 0.08f))
    val groundedMuted = earthTone(lerp(secondary, fallbackAlt, 0.12f))
    val accent = earthTone(lerp(groundedDominant, Color(0xFFFF8B3E), 0.20f))
    return AdaptivePalette(
        dominant = lerp(groundedDominant, Color.White, 0.04f),
        muted = lerp(groundedMuted, groundedDominant, 0.18f),
        accent = accent,
        deep = lerp(groundedMuted, Color.Black, 0.58f),
    )
}

private fun earthTone(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).roundToInt().coerceIn(0, 255),
        (color.green * 255f).roundToInt().coerceIn(0, 255),
        (color.blue * 255f).roundToInt().coerceIn(0, 255),
        hsv,
    )
    val warmHue = 25f
    val hueDelta = (((warmHue - hsv[0] + 540f) % 360f) - 180f) * 0.10f
    hsv[0] = (hsv[0] + hueDelta + 360f) % 360f
    hsv[1] = (hsv[1] * 0.92f + 0.10f).coerceIn(0.22f, 0.86f)
    hsv[2] = (hsv[2] * 0.96f + 0.03f).coerceIn(0.26f, 0.92f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun enrichPaletteColor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).roundToInt().coerceIn(0, 255),
        (color.green * 255f).roundToInt().coerceIn(0, 255),
        (color.blue * 255f).roundToInt().coerceIn(0, 255),
        hsv,
    )
    hsv[1] = (hsv[1] * 1.42f).coerceIn(0.20f, 0.92f)
    hsv[2] = (hsv[2] * 1.10f).coerceIn(0.30f, 0.94f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
internal fun rememberAlbumArtBitmap(album: Album, renderSize: AlbumArtRenderSize = AlbumArtRenderSize.Thumbnail): Bitmap? =
    rememberAlbumArtBitmap(album, renderSize.defaultUseCase)

@Composable
internal fun rememberAlbumArtBitmap(album: Album, useCase: ArtworkUseCase): Bitmap? =
    rememberAlbumArtBitmap(album, album.coverUri, album.sourceUri, album.artSourceUris, useCase)
        ?: rememberDefaultAlbumArtBitmap(album, useCase)

@Composable
internal fun rememberAlbumArtBitmap(
    coverUri: Uri?,
    sourceUri: Uri?,
    sourceUris: List<Uri> = listOfNotNull(sourceUri),
    renderSize: AlbumArtRenderSize = AlbumArtRenderSize.Thumbnail,
): Bitmap? =
    rememberAlbumArtBitmap(null, coverUri, sourceUri, sourceUris, renderSize.defaultUseCase)

@Composable
internal fun rememberAlbumArtBitmap(
    coverUri: Uri?,
    sourceUri: Uri?,
    sourceUris: List<Uri> = listOfNotNull(sourceUri),
    useCase: ArtworkUseCase,
): Bitmap? =
    rememberAlbumArtBitmap(null, coverUri, sourceUri, sourceUris, useCase)

@Composable
private fun rememberAlbumArtBitmap(
    album: Album?,
    coverUri: Uri?,
    sourceUri: Uri?,
    sourceUris: List<Uri> = listOfNotNull(sourceUri),
    useCase: ArtworkUseCase = ArtworkUseCase.AlbumGridThumbnail,
): Bitmap? {
    val context = LocalContext.current
    val settings = AlbumArtworkRuntime.settings.normalized()
    AlbumArtMemoryCache.configure(context)
    val maxEdge = useCase.maxEdgeFor(settings)
    LaunchedEffect(Unit) {
        AlbumArtworkRuntime.loadManualOverridesIfNeeded(context)
    }
    val cacheRevision = AlbumArtworkRuntime.cacheRevision
    val manualOverrideUri = AlbumArtworkRuntime.manualOverrideUri(album)
    val effectiveCoverUri = manualOverrideUri ?: coverUri
    val onlineSettings = PulseOnlineRuntime.settings
    val normalizedSourceUris = remember(sourceUri, sourceUris, manualOverrideUri) {
        if (manualOverrideUri != null) emptyList() else (sourceUris + listOfNotNull(sourceUri)).distinct()
    }
    val cacheKey = remember(album?.key, album?.title, album?.artist, effectiveCoverUri, normalizedSourceUris, settings, onlineSettings.offlineMode, useCase, maxEdge, cacheRevision) {
        albumArtCacheKey(album, effectiveCoverUri, normalizedSourceUris)
            ?.let { "$it|${settings.artworkCacheSignature(useCase, maxEdge)}|$cacheRevision" }
    }
    val diskCacheKey = remember(album?.key, album?.title, album?.artist, effectiveCoverUri, normalizedSourceUris, settings, onlineSettings.offlineMode, useCase, maxEdge) {
        albumArtCacheKey(album, effectiveCoverUri, normalizedSourceUris)
            ?.let { "$it|${settings.artworkCacheSignature(useCase, maxEdge)}" }
    }
    val networkCover = remember(effectiveCoverUri) {
        effectiveCoverUri?.scheme.equals("http", ignoreCase = true) || effectiveCoverUri?.scheme.equals("https", ignoreCase = true)
    }
    var bitmap by remember(cacheKey) { mutableStateOf(cacheKey?.let { AlbumArtMemoryCache.get(it) }) }
    DisposableEffect(cacheKey, useCase) {
        val trackedRouteThumbnailState = cacheKey != null && useCase.releasesBitmapOnDispose()
        if (trackedRouteThumbnailState) {
            ArtworkRouteThumbnailStates.register(useCase)
            artworkMemoryDebugLog("bitmap_state_register use_case=${useCase.cacheToken} key=${cacheKey?.let(::shortCacheKey).orEmpty()} ${ArtworkRouteThumbnailStates.snapshotLabel()}")
        }
        onDispose {
            if (useCase.releasesBitmapOnDispose()) {
                bitmap = null
                artworkMemoryDebugLog("bitmap_state_dispose use_case=${useCase.cacheToken} key=${cacheKey?.let(::shortCacheKey).orEmpty()}")
            }
            if (trackedRouteThumbnailState) {
                ArtworkRouteThumbnailStates.unregister(useCase)
                artworkMemoryDebugLog("bitmap_state_unregister use_case=${useCase.cacheToken} key=${cacheKey?.let(::shortCacheKey).orEmpty()} ${ArtworkRouteThumbnailStates.snapshotLabel()}")
            }
        }
    }
    LaunchedEffect(cacheKey, diskCacheKey, effectiveCoverUri, normalizedSourceUris, settings, manualOverrideUri, maxEdge, useCase) {
        if (cacheKey == null || bitmap != null || (!networkCover && missingArtworkCacheHit(cacheKey))) return@LaunchedEffect
        val loaded = loadAlbumArtBitmap(context, album, effectiveCoverUri, normalizedSourceUris, settings, diskCacheKey, manualOverrideUri != null, maxEdge, useCase)
        if (loaded != null) {
            AlbumArtMemoryCache.put(cacheKey, loaded)
            bitmap = loaded
        } else {
            if (!networkCover) recordMissingArtworkKey(cacheKey)
        }
    }
    return bitmap
}

@Composable
private fun rememberDefaultAlbumArtBitmap(album: Album, useCase: ArtworkUseCase): Bitmap? {
    val settings = AlbumArtworkRuntime.settings.normalized()
    return remember(album.key, album.mark, album.tint, album.alt, settings.showDefaultImageWhenMissing, useCase) {
        if (settings.showDefaultImageWhenMissing) defaultAlbumArtBitmap(album, useCase) else null
    }
}

private fun albumArtCacheKey(album: Album?, coverUri: Uri?, sourceUris: List<Uri>): String? {
    val sources = sourceUris.map { it.toString() }.distinct()
    val albumSeed = album?.let { "album:${it.title}:${it.artist}:${it.key}" }
    return (listOfNotNull(albumSeed, coverUri?.toString()) + sources).takeIf { it.isNotEmpty() }?.joinToString("|")
}

private suspend fun loadAlbumArtBitmap(
    context: Context,
    album: Album?,
    coverUri: Uri?,
    sourceUris: List<Uri>,
    settings: AlbumArtSettings,
    diskCacheKey: String?,
    manualCoverSelected: Boolean = false,
    maxEdge: Int,
    useCase: ArtworkUseCase,
): Bitmap? {
    val requestKey = diskCacheKey?.let { key ->
        "load|$key|use=${useCase.cacheToken}|max=$maxEdge|bucket=${useCase.diskBucket().directoryName}"
    }
    return if (requestKey != null) {
        ArtworkInFlightRequests.await(requestKey, useCase) {
            loadAlbumArtBitmapUnshared(context, album, coverUri, sourceUris, settings, diskCacheKey, manualCoverSelected, maxEdge, useCase)
        }
    } else {
        loadAlbumArtBitmapUnshared(context, album, coverUri, sourceUris, settings, diskCacheKey, manualCoverSelected, maxEdge, useCase)
    }
}

private suspend fun loadAlbumArtBitmapUnshared(
    context: Context,
    album: Album?,
    coverUri: Uri?,
    sourceUris: List<Uri>,
    settings: AlbumArtSettings,
    diskCacheKey: String?,
    manualCoverSelected: Boolean = false,
    maxEdge: Int,
    useCase: ArtworkUseCase,
): Bitmap? = withContext(Dispatchers.IO) {
    currentCoroutineContext().ensureActive()
    diskCacheKey?.let { key ->
        readAlbumArtDiskCache(context, key, maxEdge, useCase.diskBucket(), useCase)?.let { return@withContext it }
    }
    if (manualCoverSelected) {
        return@withContext coverUri
            ?.let { uri -> loadAlbumArtUriBitmap(context, uri, allowLocal = true, maxEdge = maxEdge, useCase = useCase) }
            ?.also { loaded ->
                if (diskCacheKey != null) writeAlbumArtDiskCache(context, diskCacheKey, loaded, settings.cacheSizeMb, useCase.diskBucket())
            }
    }
    val embeddedBitmap = if (settings.preferEmbeddedArtwork) {
        firstEmbeddedAlbumArtBitmap(context, sourceUris, maxEdge, useCase)
    } else {
        null
    }
    val directCoverBitmap = coverUri?.let { uri -> loadAlbumArtUriBitmap(context, uri, allowLocal = settings.preferFolderImage, maxEdge = maxEdge, useCase = useCase) }
    val existingBitmaps = listOfNotNull(embeddedBitmap, directCoverBitmap)
    val existingBest = existingBitmaps.maxByOrNull { it.width.toLong() * it.height.toLong() }
    val onlineAlbum = album
    val onlineAllowed = onlineAlbum != null &&
        settings.downloadAlbumArt &&
        !PulseOnlineRuntime.settings.offlineMode &&
        AlbumArtworkRuntime.provider.configured &&
        artworkLookupMetadataReliable(onlineAlbum.title, onlineAlbum.artist)
    val shouldLookupOnline = onlineAllowed && (
        existingBest == null ||
            settings.preferOnlineImage
        )
    val onlineBitmap = if (shouldLookupOnline) {
        AlbumArtworkRuntime.provider
            .resolveArtwork(context, onlineAlbum!!, coverUri, sourceUris, settings)
            ?.takeIf { uri -> uri.toString() != coverUri?.toString() || settings.preferOnlineImage }
            ?.let { uri -> loadAlbumArtUriBitmap(context, uri, allowLocal = false, maxEdge = maxEdge, useCase = useCase) }
    } else {
        null
    }
    val acceptedOnlineBitmap = onlineBitmap?.takeIf { candidate ->
        existingBest == null ||
            settings.preferOnlineImage ||
            (settings.highestQualityCovers && settings.autoReplaceExistingArt && artworkUpgradeAccepted(
                currentWidth = existingBest.width,
                currentHeight = existingBest.height,
                candidateWidth = candidate.width,
                candidateHeight = candidate.height,
                minimumUpgradePercent = settings.minimumUpgradePercent,
            ))
    }
    val loaded = (existingBitmaps + listOfNotNull(acceptedOnlineBitmap))
        .maxByOrNull { it.width.toLong() * it.height.toLong() }
        ?.let { it.scaledToMaxEdge(maxEdge) }
    if (loaded != null && diskCacheKey != null) writeAlbumArtDiskCache(context, diskCacheKey, loaded, settings.cacheSizeMb, useCase.diskBucket())
    if (acceptedOnlineBitmap != null && settings.saveFolderCover) writeAlbumFolderCoverIfAllowed(context, sourceUris, acceptedOnlineBitmap)
    loaded
}

private fun firstEmbeddedAlbumArtBitmap(context: Context, sourceUris: List<Uri>, maxEdge: Int, useCase: ArtworkUseCase): Bitmap? =
    sourceUris
        .distinct()
        .take(MAX_EMBEDDED_ART_SOURCE_PROBES)
        .firstNotNullOfOrNull { sourceUri ->
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, sourceUri)
                    retriever.embeddedPicture?.let { bytes -> decodeHighQualityBitmap(bytes, maxEdge, useCase, "embedded") }
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }

private suspend fun loadAlbumArtUriBitmap(
    context: Context,
    uri: Uri,
    allowLocal: Boolean,
    maxEdge: Int,
    useCase: ArtworkUseCase,
): Bitmap? {
    val requestKey = "uri|${uri}|allow_local=$allowLocal|use=${useCase.cacheToken}|max=$maxEdge|bucket=${useCase.diskBucket().directoryName}"
    return ArtworkInFlightRequests.await(requestKey, useCase) {
        loadAlbumArtUriBitmapUnshared(context, uri, allowLocal, maxEdge, useCase)
    }
}

private suspend fun loadAlbumArtUriBitmapUnshared(
    context: Context,
    uri: Uri,
    allowLocal: Boolean,
    maxEdge: Int,
    useCase: ArtworkUseCase,
): Bitmap? {
    currentCoroutineContext().ensureActive()
    val isNetwork = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
    if (!isNetwork) {
        if (!allowLocal) return null
        return try {
            decodeContentUriBitmap(context, uri, maxEdge, useCase)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            artworkDebugLog("local_decode_failed source=${uri.scheme.orEmpty()} use_case=${useCase.cacheToken} reason=${error.javaClass.simpleName}")
            null
        }
    }
    val url = uri.toString()
    if (failedArtworkUrlCacheHit(url)) return null
    val connection = runCatching { URL(url).openConnection() as HttpURLConnection }
        .getOrElse { error ->
            recordFailedArtworkUrl(url, "open_failed:${error.javaClass.simpleName}")
            return null
        }
    return try {
        connection.connectTimeout = 2600
        connection.readTimeout = 6200
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36 PulseDeck/0.1")
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        currentCoroutineContext().ensureActive()
        val status = connection.responseCode
        if (status !in 200..299) {
            recordFailedArtworkUrl(url, "http_$status")
            return null
        }
        val contentLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) connection.contentLengthLong else connection.contentLength.toLong()
        if (contentLength > useCase.remoteByteLimit) {
            recordFailedArtworkUrl(url, "content_length_${contentLength}_over_${useCase.remoteByteLimit}")
            return null
        }
        connection.inputStream.use { stream ->
            decodeRemoteArtworkStream(context, stream, url, maxEdge, useCase)
        } ?: run {
            recordFailedArtworkUrl(url, "decode_failed")
            null
        }
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Throwable) {
        recordFailedArtworkUrl(url, error.javaClass.simpleName)
        null
    } finally {
        connection.disconnect()
    }
}

private suspend fun decodeRemoteArtworkStream(
    context: Context,
    stream: InputStream,
    url: String,
    maxEdge: Int,
    useCase: ArtworkUseCase,
): Bitmap? {
    val tempFile = File.createTempFile("album_art_remote_", ".img", context.cacheDir)
    return try {
        var copiedBytes = 0L
        tempFile.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = stream.read(buffer)
                if (read < 0) break
                copiedBytes += read
                if (copiedBytes > useCase.remoteByteLimit) {
                    recordFailedArtworkUrl(url, "stream_over_${useCase.remoteByteLimit}")
                    return null
                }
                output.write(buffer, 0, read)
            }
        }
        artworkDebugLog("decode_request source=remote use_case=${useCase.cacheToken} max_edge=$maxEdge bytes=$copiedBytes url=${shortCacheKey(url)}")
        decodeFileBitmap(tempFile, maxEdge, useCase, "remote")
    } finally {
        CacheBudgetManager.deleteTempFile(context, tempFile, CacheCleanupReason.DownloadTempCleanup)
    }
}

private fun decodeContentUriBitmap(context: Context, uri: Uri, maxEdge: Int, useCase: ArtworkUseCase): Bitmap? =
    decodeReopenableBitmap(
        openStream = { context.contentResolver.openInputStream(uri) },
        maxEdge = maxEdge,
        useCase = useCase,
        sourceType = if (uri.scheme.equals("file", ignoreCase = true)) "file" else "content",
    )

private fun decodeFileBitmap(
    file: File,
    maxEdge: Int,
    useCase: ArtworkUseCase? = null,
    sourceType: String = "file",
): Bitmap? =
    if (maxEdge == Int.MAX_VALUE) {
        BitmapFactory.decodeFile(file.absolutePath, bitmapOptionsForUseCase(useCase))
            ?.also { logDecodedBitmap(useCase, sourceType, maxEdge, it) }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        logDecodeRequest(useCase, sourceType, maxEdge, bounds.outWidth, bounds.outHeight)
        val options = bitmapOptionsForUseCase(useCase).apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge) }
        BitmapFactory.decodeFile(file.absolutePath, options)
            ?.scaledToMaxEdge(maxEdge)
            ?.also { logDecodedBitmap(useCase, sourceType, maxEdge, it) }
    }

private fun decodeReopenableBitmap(
    openStream: () -> InputStream?,
    maxEdge: Int,
    useCase: ArtworkUseCase? = null,
    sourceType: String = "stream",
): Bitmap? {
    if (maxEdge == Int.MAX_VALUE) {
        return openStream()?.use { stream -> BitmapFactory.decodeStream(stream, null, bitmapOptionsForUseCase(useCase)) }
            ?.also { logDecodedBitmap(useCase, sourceType, maxEdge, it) }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream()?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) } ?: return null
    logDecodeRequest(useCase, sourceType, maxEdge, bounds.outWidth, bounds.outHeight)
    val options = bitmapOptionsForUseCase(useCase).apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge) }
    return openStream()?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
        ?.scaledToMaxEdge(maxEdge)
        ?.also { logDecodedBitmap(useCase, sourceType, maxEdge, it) }
}

private fun decodeHighQualityBitmap(
    bytes: ByteArray,
    maxEdge: Int,
    useCase: ArtworkUseCase? = null,
    sourceType: String = "bytearray",
): Bitmap? {
    if (maxEdge == Int.MAX_VALUE) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptionsForUseCase(useCase))
            ?.also { logDecodedBitmap(useCase, sourceType, maxEdge, it) }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    logDecodeRequest(useCase, sourceType, maxEdge, bounds.outWidth, bounds.outHeight)
    val options = bitmapOptionsForUseCase(useCase).apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?.scaledToMaxEdge(maxEdge)
        ?.also { logDecodedBitmap(useCase, sourceType, maxEdge, it) }
}

private fun logDecodeRequest(useCase: ArtworkUseCase?, sourceType: String, maxEdge: Int, sourceWidth: Int, sourceHeight: Int) {
    if (useCase != null) {
        artworkDebugLog("decode_request source=$sourceType use_case=${useCase.cacheToken} max_edge=$maxEdge source=${sourceWidth}x$sourceHeight")
    }
}

private fun logDecodedBitmap(useCase: ArtworkUseCase?, sourceType: String, maxEdge: Int, bitmap: Bitmap) {
    if (useCase != null) {
        artworkDebugLog("decode_result source=$sourceType use_case=${useCase.cacheToken} max_edge=$maxEdge decoded=${bitmap.width}x${bitmap.height} bytes=${bitmap.safeByteCount()}")
        ArtworkDecodeStats.record(useCase, bitmap)
    }
}

private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
    if (maxEdge == Int.MAX_VALUE || width <= 0 || height <= 0) return 1
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxEdge) {
        sample *= 2
    }
    return sample.coerceAtLeast(1)
}

private fun bitmapOptionsForUseCase(useCase: ArtworkUseCase?): BitmapFactory.Options =
    BitmapFactory.Options().apply {
        inPreferredConfig = if (useCase?.usesCompactOpaqueDecode() == true) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }
        inDither = true
        inScaled = false
    }

private fun AlbumArtSettings.artworkCacheSignature(useCase: ArtworkUseCase, maxEdge: Int): String =
    listOf(
        downloadAlbumArt,
        providerMode.name,
        highestQualityCovers,
        autoReplaceExistingArt,
        minimumUpgradePercent,
        saveFolderCover,
        providerOrder.joinToString(",") { it.name },
        preferEmbeddedArtwork,
        preferFolderImage,
        preferOnlineImage,
        quality.name,
        useCase.cacheToken,
        useCase.diskBucket().directoryName,
        maxEdge,
        showDefaultImageWhenMissing,
        PulseOnlineRuntime.settings.offlineMode,
    ).joinToString(":")

private fun AlbumArtSettings.artworkCacheSignature(renderSize: AlbumArtRenderSize, maxEdge: Int): String =
    artworkCacheSignature(renderSize.defaultUseCase, maxEdge)

private fun AlbumArtRenderSize.maxEdgeFor(settings: AlbumArtSettings): Int =
    defaultUseCase.maxEdgeFor(settings)

private fun Bitmap.scaledToMaxEdge(maxEdge: Int): Bitmap {
    val largest = max(width, height)
    if (largest <= maxEdge || maxEdge == Int.MAX_VALUE) return this
    val scale = maxEdge.toFloat() / largest.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    if (scaled !== this && !isRecycled) recycle()
    return scaled
}

private fun Bitmap.safeByteCount(): Int =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) allocationByteCount else byteCount
    }.getOrDefault(width * height * 4)

private fun defaultAlbumArtBitmap(album: Album, useCase: ArtworkUseCase): Bitmap {
    val size = useCase.placeholderEdge()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.shader = android.graphics.LinearGradient(
        0f,
        0f,
        size.toFloat(),
        size.toFloat(),
        album.tint.toArgb(),
        album.alt.toArgb(),
        android.graphics.Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.shader = null
    paint.color = android.graphics.Color.argb(126, 0, 0, 0)
    canvas.drawCircle(size * 0.78f, size * 0.24f, size * 0.28f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.textSize = size * 0.18f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText(album.mark.take(3).uppercase(Locale.US), size / 2f, size * 0.56f, paint)
    return bitmap
}

private fun albumArtDiskCacheDir(context: Context): File =
    File(context.cacheDir, "album_art_cache").apply { mkdirs() }

private fun albumArtDiskCacheDir(context: Context, bucket: ArtworkDiskBucket): File =
    File(albumArtDiskCacheDir(context), bucket.directoryName).apply { mkdirs() }

private fun albumArtDiskCacheFile(context: Context, cacheKey: String, bucket: ArtworkDiskBucket): File =
    File(albumArtDiskCacheDir(context, bucket), "${sha256(cacheKey)}.jpg")

private fun legacyAlbumArtDiskCacheFile(context: Context, cacheKey: String): File =
    File(albumArtDiskCacheDir(context), "${sha256(cacheKey)}.jpg")

private fun readAlbumArtDiskCache(
    context: Context,
    cacheKey: String,
    maxEdge: Int,
    bucket: ArtworkDiskBucket,
    useCase: ArtworkUseCase?,
): Bitmap? =
    runCatching {
        val file = albumArtDiskCacheFile(context, cacheKey, bucket)
            .takeIf { it.exists() && it.length() > 0L }
            ?: legacyAlbumArtDiskCacheFile(context, cacheKey).takeIf { it.exists() && it.length() > 0L }
            ?: run {
                artworkDebugLog("disk_cache_miss key=${shortCacheKey(cacheKey)} bucket=${bucket.directoryName}")
                return null
            }
        file.setLastModified(System.currentTimeMillis())
        artworkDebugLog("disk_cache_hit key=${shortCacheKey(cacheKey)} bucket=${bucket.directoryName} bytes=${file.length()}")
        decodeFileBitmap(file, maxEdge, useCase, "disk_cache")
    }.getOrNull()

private fun writeAlbumArtDiskCache(context: Context, cacheKey: String, bitmap: Bitmap, maxCacheMb: Int, bucket: ArtworkDiskBucket) {
    runCatching {
        val file = albumArtDiskCacheFile(context, cacheKey, bucket)
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
        artworkDebugLog("disk_cache_write key=${shortCacheKey(cacheKey)} bucket=${bucket.directoryName} bytes=${file.length()} limit_mb=${artworkDiskBucketLimitMb(context, maxCacheMb, bucket)}")
        enforceAlbumArtDiskCacheLimit(context, maxCacheMb, bucket)
    }
}

private fun enforceAlbumArtDiskCacheLimit(context: Context, maxCacheMb: Int, bucket: ArtworkDiskBucket) {
    val limitBytes = artworkDiskBucketLimitMb(context, maxCacheMb, bucket) * 1024L * 1024L
    val files = albumArtDiskCacheDir(context, bucket)
        .listFiles()
        .orEmpty()
        .filter { it.isFile }
        .sortedByDescending { it.lastModified() }
    var total = files.sumOf { it.length() }
    files.asReversed().forEach { file ->
        if (total <= limitBytes) return
        val length = file.length()
        if (file.delete()) total -= length
    }
}

internal suspend fun albumArtCacheStatus(context: Context, maxCacheMb: Int): String = withContext(Dispatchers.IO) {
    albumArtCacheStatusBlocking(context, maxCacheMb)
}

private fun albumArtCacheStatusBlocking(context: Context, maxCacheMb: Int): String {
    val files = albumArtDiskCacheFiles(context)
    val total = files.sumOf { it.length() }
    val size = if (total <= 0L) "0 KB" else formatFileSize(total)
    val effectiveMb = CacheBudgetManager.artworkTotalBudgetBytes(context, maxCacheMb) / (1024L * 1024L)
    return "${files.size} cached files, $size of $effectiveMb MB effective budget"
}

internal suspend fun clearAlbumArtDiskCache(context: Context): Int = withContext(Dispatchers.IO) {
    albumArtDiskCacheDir(context)
        .walkTopDown()
        .filter { it.isFile }
        .count { it.delete() }
}

private fun albumArtDiskCacheFiles(context: Context): List<File> =
    albumArtDiskCacheDir(context)
        .walkTopDown()
        .filter { it.isFile }
        .toList()

private fun writeAlbumFolderCoverIfAllowed(context: Context, sourceUris: List<Uri>, bitmap: Bitmap): Boolean =
    runCatching {
        val firstSource = sourceUris.firstOrNull() ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = queryAudioRelativePath(context, firstSource) ?: return false
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "cover.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
                } ?: error("No output stream")
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    .also { context.contentResolver.update(uri, it, null, null) }
                true
            }.getOrElse {
                runCatching { context.contentResolver.delete(uri, null, null) }
                false
            }
        } else {
            val parent = queryAudioFileParent(context, firstSource) ?: return false
            val file = File(parent, "cover.jpg")
            file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output) }
            true
        }
    }.getOrDefault(false)

private fun queryAudioRelativePath(context: Context, sourceUri: Uri): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.Audio.Media.RELATIVE_PATH),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getStringOrNull(MediaStore.Audio.Media.RELATIVE_PATH) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    } else {
        null
    }

@Suppress("DEPRECATION")
private fun queryAudioFileParent(context: Context, sourceUri: Uri): File? =
    runCatching {
        context.contentResolver.query(
            sourceUri,
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getStringOrNull(MediaStore.Audio.Media.DATA)
                ?.let(::File)
                ?.parentFile
                ?.takeIf { it.exists() && it.canWrite() }
        }
    }.getOrNull()

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private suspend fun resolveCoverArchiveArtworkCandidates(album: Album, settings: AlbumArtSettings): List<ArtworkCandidate> =
    withContext(Dispatchers.IO) {
        val title = album.title.cleanArtworkSearchText()
        val artist = album.artist.cleanArtworkSearchText()
        if (title.isBlank() || artist.isBlank() || title == STREAM_LIBRARY_ALBUM) return@withContext emptyList()
        val query = URLEncoder.encode("release:\"$title\" AND artist:\"$artist\"", "UTF-8")
        val json = httpJson("https://musicbrainz.org/ws/2/release-group?query=$query&fmt=json&limit=4", connectTimeout = 2200, readTimeout = 3600)
            ?: return@withContext emptyList()
        val groups = json.optJSONArray("release-groups") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until groups.length()) {
                val group = groups.optJSONObject(index) ?: continue
                val id = group.optString("id").takeIf { it.isNotBlank() } ?: continue
                val suffix = when (settings.quality) {
                    AlbumArtQuality.Efficient -> "front-500"
                    AlbumArtQuality.High -> "front-1200"
                    AlbumArtQuality.Original -> "front"
                }
                val edge = when (settings.quality) {
                    AlbumArtQuality.Efficient -> 500
                    AlbumArtQuality.High -> 1200
                    AlbumArtQuality.Original -> 3000
                }
                add(
                    ArtworkCandidate(
                        provider = AlbumArtworkProviderId.CoverArtArchive,
                        imageUrl = "https://coverartarchive.org/release-group/$id/$suffix",
                        landingUrl = "https://musicbrainz.org/release-group/$id",
                        width = edge,
                        height = edge,
                        confidence = 96 - index * 8,
                        matchReason = "MusicBrainz release-group front cover",
                    ),
                )
            }
        }
    }

private suspend fun resolveAppleArtworkCandidates(album: Album): List<ArtworkCandidate> =
    withContext(Dispatchers.IO) {
        val query = album.artworkSearchQuery()
        if (query.length < 3) return@withContext emptyList()
        val json = httpJson("https://itunes.apple.com/search?term=${query.urlParam()}&entity=album&limit=8", connectTimeout = 2200, readTimeout = 3600)
            ?: return@withContext emptyList()
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val url = item.optString("artworkUrl100")
                    .takeIf { it.startsWith("http", ignoreCase = true) }
                    ?.highestAppleArtworkUrl()
                    ?: continue
                val confidence = album.artworkMatchConfidence(
                    title = item.optString("collectionName"),
                    artist = item.optString("artistName"),
                    base = 60,
                )
                add(
                    ArtworkCandidate(
                        provider = AlbumArtworkProviderId.AppleItunes,
                        imageUrl = url,
                        landingUrl = item.optString("collectionViewUrl").takeIf { it.startsWith("http", ignoreCase = true) },
                        width = 3000,
                        height = 3000,
                        confidence = confidence - index * 4,
                        matchReason = "Apple/iTunes album artwork",
                    ),
                )
            }
        }
    }

private suspend fun resolveDeezerArtworkCandidates(album: Album): List<ArtworkCandidate> =
    withContext(Dispatchers.IO) {
        val query = album.artworkSearchQuery()
        if (query.length < 3) return@withContext emptyList()
        val json = httpJson("https://api.deezer.com/search/album?q=${query.urlParam()}&limit=8", connectTimeout = 2200, readTimeout = 3600)
            ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val artist = item.optJSONObject("artist")?.optString("name").orEmpty()
                val url = item.optString("cover_xl")
                    .ifBlank { item.optString("cover_big") }
                    .takeIf { it.startsWith("http", ignoreCase = true) }
                    ?: continue
                val confidence = album.artworkMatchConfidence(
                    title = item.optString("title"),
                    artist = artist,
                    base = 56,
                )
                add(
                    ArtworkCandidate(
                        provider = AlbumArtworkProviderId.Deezer,
                        imageUrl = url,
                        landingUrl = item.optString("link").takeIf { it.startsWith("http", ignoreCase = true) },
                        width = 1000,
                        height = 1000,
                        confidence = confidence - index * 4,
                        matchReason = "Deezer album cover_xl",
                    ),
                )
            }
        }
    }

private suspend fun resolveMusicHoardersArtworkCandidates(album: Album): List<ArtworkCandidate> =
    withContext(Dispatchers.IO) {
        scrapeArtworkCandidatesFromPage(
            provider = AlbumArtworkProviderId.MusicHoarders,
            pageUrl = "https://covers.musichoarders.xyz/?q=${album.artworkSearchQuery().urlParam()}",
            album = album,
            baseConfidence = 52,
            reason = "MH Covers scraped artwork",
        )
    }

private suspend fun resolvePageMetadataArtworkCandidates(album: Album): List<ArtworkCandidate> =
    withContext(Dispatchers.IO) {
        val encoded = album.artworkSearchQuery().urlParam()
        val pages = listOf(
            "https://www.discogs.com/search/?q=$encoded&type=all",
            "https://open.spotify.com/search/$encoded",
        )
        pages.flatMap { page ->
            scrapeArtworkCandidatesFromPage(
                provider = AlbumArtworkProviderId.PageMetadata,
                pageUrl = page,
                album = album,
                baseConfidence = 46,
                reason = "Public page metadata artwork",
            )
        }
    }

private suspend fun resolveYouTubeArtworkCandidate(album: Album): ArtworkCandidate? =
    withTimeoutOrNull(4200L) {
        val query = listOf(album.artist, album.title, "official audio")
            .joinToString(" ")
            .cleanArtworkSearchText()
        if (query.length < 3) return@withTimeoutOrNull null
        searchYouTubeVideos(query)
            .results
            .firstNotNullOfOrNull { result ->
                result.source.bestThumbnailUrl()?.let { url ->
                    ArtworkCandidate(
                        provider = AlbumArtworkProviderId.YouTube,
                        imageUrl = url,
                        landingUrl = result.source.url,
                        width = 1280,
                        height = 720,
                        confidence = album.artworkMatchConfidence(result.source.title, result.source.author, base = 42),
                        matchReason = "YouTube stream thumbnail",
                    )
                }
            }
    }

private fun scrapeArtworkCandidatesFromPage(
    provider: AlbumArtworkProviderId,
    pageUrl: String,
    album: Album,
    baseConfidence: Int,
    reason: String,
): List<ArtworkCandidate> {
    val html = httpText(pageUrl, connectTimeout = 2200, readTimeout = 4200) ?: return emptyList()
    val confidence = baseConfidence + if (html.normalizedSearchText().contains(album.title.normalizedSearchText()) && html.normalizedSearchText().contains(album.artist.normalizedSearchText())) 16 else 0
    val urls = buildList {
        val metaRegex = Regex("""<(?:meta|link)[^>]+(?:property|name|rel)=["'](?:og:image|twitter:image|image_src|preload)["'][^>]+(?:content|href)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val reverseMetaRegex = Regex("""<(?:meta|link)[^>]+(?:content|href)=["']([^"']+)["'][^>]+(?:property|name|rel)=["'](?:og:image|twitter:image|image_src|preload)["']""", RegexOption.IGNORE_CASE)
        val imageRegex = Regex("""https?://[^"'\s>]+\.(?:jpg|jpeg|png|webp)(?:\?[^"'\s>]*)?""", RegexOption.IGNORE_CASE)
        metaRegex.findAll(html).forEach { add(it.groupValues[1].htmlDecoded()) }
        reverseMetaRegex.findAll(html).forEach { add(it.groupValues[1].htmlDecoded()) }
        imageRegex.findAll(html).forEach { add(it.value.replace("\\/", "/").htmlDecoded()) }
    }
    return urls
        .mapNotNull { raw -> raw.absoluteUrlFrom(pageUrl) }
        .filterNot { it.contains("favicon", ignoreCase = true) || it.contains("sprite", ignoreCase = true) || it.contains("logo", ignoreCase = true) }
        .distinctBy { it.substringBefore('?') }
        .take(8)
        .mapIndexed { index, url ->
            val dimensions = inferArtworkDimensionsFromUrl(url)
            ArtworkCandidate(
                provider = provider,
                imageUrl = url,
                landingUrl = pageUrl,
                width = dimensions.first,
                height = dimensions.second,
                confidence = confidence - index * 3,
                matchReason = reason,
            )
        }
}

private fun String.cleanArtworkSearchText(): String =
    replace(Regex("""\[[^]]*]|\([^)]*\)"""), " ")
        .replace(Regex("""\b(official|audio|video|lyrics?|visualizer|remaster(?:ed)?|hd|4k)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun Album.artworkSearchQuery(): String =
    listOf(artist, title)
        .filter { it.isNotBlank() && it != STREAM_LIBRARY_ALBUM_ARTIST }
        .joinToString(" ")
        .cleanArtworkSearchText()

internal fun artworkTextMatchConfidence(
    albumTitle: String,
    albumArtist: String,
    candidateTitle: String,
    candidateArtist: String,
    base: Int,
): Int {
    val title = albumTitle.normalizedSearchText()
    val artist = albumArtist.normalizedSearchText()
    val candidateTitleNorm = candidateTitle.normalizedSearchText()
    val candidateArtistNorm = candidateArtist.normalizedSearchText()
    var score = base
    if (title.isNotBlank() && candidateTitleNorm == title) score += 28
    else if (title.isNotBlank() && candidateTitleNorm.isNotBlank() && (candidateTitleNorm.contains(title) || title.contains(candidateTitleNorm))) score += 14
    if (artist.isNotBlank() && candidateArtistNorm == artist) score += 24
    else if (artist.isNotBlank() && candidateArtistNorm.isNotBlank() && (candidateArtistNorm.contains(artist) || artist.contains(candidateArtistNorm))) score += 12
    if (candidateTitleNorm.isBlank() && candidateArtistNorm.isBlank()) score -= 12
    return score.coerceIn(0, 100)
}

private fun Album.artworkMatchConfidence(title: String, artist: String, base: Int): Int =
    artworkTextMatchConfidence(
        albumTitle = this.title,
        albumArtist = this.artist,
        candidateTitle = title,
        candidateArtist = artist,
        base = base,
    )

private fun String.highestAppleArtworkUrl(): String =
    replace(Regex("""\d+x\d+bb(?=\.)"""), "3000x3000bb")
        .replace(Regex("""\d+x\d+sr(?=\.)"""), "3000x3000sr")

private fun inferArtworkDimensionsFromUrl(url: String): Pair<Int, Int> {
    val match = Regex("""(\d{3,4})x(\d{3,4})""").find(url)
    val width = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val height = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: width
    return width to height
}

private fun String.htmlDecoded(): String =
    replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

private fun String.absoluteUrlFrom(pageUrl: String): String? =
    runCatching {
        val decoded = htmlDecoded().trim()
        when {
            decoded.startsWith("http", ignoreCase = true) -> decoded
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> {
                val base = URL(pageUrl)
                "${base.protocol}://${base.host}$decoded"
            }
            else -> URL(URL(pageUrl), decoded).toString()
        }
    }.getOrNull()?.takeIf { it.startsWith("http", ignoreCase = true) }
