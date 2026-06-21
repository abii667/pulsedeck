package com.pulsedeck.app

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.pulsedeck.app.settings.model.AlbumArtSettings
import com.pulsedeck.app.settings.model.LyricsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CACHE_LOG_TAG = "PulseDeckCache"
private const val CACHE_MB = 1024L * 1024L
private const val NORMAL_TOTAL_CACHE_MB = 512L
private const val LOW_TOTAL_CACHE_MB = 256L
private const val HIGH_STORAGE_TOTAL_CACHE_MB = 768L
private const val NORMAL_STREAMING_CACHE_MB = 256L
private const val LOW_STREAMING_CACHE_MB = 128L
private const val NORMAL_ARTWORK_CACHE_MB = 192L
private const val LOW_ARTWORK_CACHE_MB = 96L
private const val HIGH_STORAGE_ARTWORK_CACHE_MB = 384L
private const val NORMAL_TEMP_CACHE_MB = 32L
private const val LOW_TEMP_CACHE_MB = 16L
private const val TEMP_STALE_AGE_MILLIS = 6L * 60L * 60L * 1000L
private const val LOW_STORAGE_AVAILABLE_BYTES = 2L * 1024L * 1024L * 1024L
private const val HIGH_STORAGE_AVAILABLE_BYTES = 16L * 1024L * 1024L * 1024L

internal enum class CacheStorageCategory {
    Disposable,
    Regenerable,
    UserOwned,
}

internal enum class CacheCleanupReason(val label: String) {
    StartupStaleTemp("startup_stale_temp"),
    UserClearArtwork("user_clear_artwork"),
    UserClearOnline("user_clear_online"),
    UserClearTemp("user_clear_temp"),
    BudgetExceeded("budget_exceeded"),
    LowStorage("low_storage"),
    TrimMemory("trim_memory"),
    DownloadTempCleanup("download_temp_cleanup"),
    FailedDownloadCleanup("failed_download_cleanup"),
    CancelledDownloadCleanup("cancelled_download_cleanup"),
    ForceRefresh("force_refresh"),
}

internal data class PulseCacheBudget(
    val totalBytes: Long,
    val streamingBytes: Long,
    val artworkTotalBytes: Long,
    val artworkFullBytes: Long,
    val artworkThumbnailBytes: Long,
    val artworkRemoteBytes: Long,
    val lyricsMetadataBytes: Long,
    val tempBytes: Long,
)

internal data class CacheBucketSnapshot(
    val id: String,
    val label: String,
    val category: CacheStorageCategory,
    val path: String,
    val bytes: Long,
    val fileCount: Int,
    val budgetBytes: Long?,
    val safeToDelete: Boolean,
    val userOwned: Boolean,
)

internal data class CacheBudgetSnapshot(
    val budget: PulseCacheBudget,
    val buckets: List<CacheBucketSnapshot>,
    val externalDownloadBytes: Long,
    val appDataBytes: Long,
) {
    val managedCacheBytes: Long =
        buckets.filterNot { it.userOwned }.sumOf { it.bytes }

    fun settingsSummary(): String {
        val streaming = bucket("streaming")?.bytes ?: 0L
        val artworkFull = bucket("artwork_full")?.bytes ?: 0L
        val artworkThumb = bucket("artwork_thumb")?.bytes ?: 0L
        val artworkRemote = bucket("artwork_remote")?.bytes ?: 0L
        val lyrics = bucket("lyrics")?.bytes ?: 0L
        val temp = bucket("temp")?.bytes ?: 0L
        val artworkTotal = artworkFull + artworkThumb + artworkRemote
        val downloadsText = if (externalDownloadBytes > 0L) {
            ", downloads ${formatFileSize(externalDownloadBytes)} outside app cache"
        } else {
            ""
        }
        return "Total ${formatFileSize(managedCacheBytes)} / ${formatFileSize(budget.totalBytes)} | " +
            "stream ${formatFileSize(streaming)} / ${formatFileSize(budget.streamingBytes)} | " +
            "images ${formatFileSize(artworkTotal)} / ${formatFileSize(budget.artworkTotalBytes)} " +
            "(full ${formatFileSize(artworkFull)}, thumb ${formatFileSize(artworkThumb)}, remote ${formatFileSize(artworkRemote)}) | " +
            "lyrics ${formatFileSize(lyrics)} / ${formatFileSize(budget.lyricsMetadataBytes)} | " +
            "temp ${formatFileSize(temp)} / ${formatFileSize(budget.tempBytes)}$downloadsText"
    }

    private fun bucket(id: String): CacheBucketSnapshot? =
        buckets.firstOrNull { it.id == id }
}

internal object CacheBudgetManager {
    fun streamingCacheBudgetBytes(context: Context): Long =
        budget(context, AlbumArtSettings().cacheSizeMb, LyricsSettings().cacheSizeMb).streamingBytes

    fun artworkTotalBudgetBytes(context: Context, requestedCacheMb: Int): Long =
        budget(context, requestedCacheMb, LyricsRuntime.settings.cacheSizeMb).artworkTotalBytes

    fun lyricsMetadataBudgetBytes(context: Context, requestedCacheMb: Int): Long =
        budget(context, AlbumArtworkRuntime.settings.cacheSizeMb, requestedCacheMb).lyricsMetadataBytes

    fun artworkBucketBudgetBytes(context: Context, requestedCacheMb: Int, bucketName: String): Long {
        val budget = budget(context, requestedCacheMb, LyricsRuntime.settings.cacheSizeMb)
        return when (bucketName) {
            "full" -> budget.artworkFullBytes
            "thumb" -> budget.artworkThumbnailBytes
            "remote" -> budget.artworkRemoteBytes
            else -> budget.artworkTotalBytes
        }
    }

    suspend fun status(
        context: Context,
        albumArtCacheMb: Int,
        lyricsCacheMb: Int,
        offlineSources: List<YouTubeSource> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        snapshotBlocking(context.applicationContext, albumArtCacheMb, lyricsCacheMb, offlineSources).settingsSummary()
    }

    suspend fun enforce(
        context: Context,
        albumArtCacheMb: Int,
        lyricsCacheMb: Int,
        reason: CacheCleanupReason,
    ): CacheBudgetSnapshot = withContext(Dispatchers.IO) {
        enforceBlocking(context.applicationContext, albumArtCacheMb, lyricsCacheMb, reason)
    }

    fun scheduleTrimCleanup(context: Context, reason: CacheCleanupReason) {
        Thread {
            runCatching {
                enforceBlocking(
                    context.applicationContext,
                    AlbumArtworkRuntime.settings.cacheSizeMb,
                    LyricsRuntime.settings.cacheSizeMb,
                    reason,
                )
            }
        }.apply {
            name = "PulseDeck-cache-cleanup"
            isDaemon = true
            start()
        }
    }

    suspend fun clearArtwork(context: Context, reason: CacheCleanupReason = CacheCleanupReason.UserClearArtwork): Int =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val result = clearFiles(appContext, artworkCacheRoot(appContext), recursive = true, reason = reason)
            logSnapshot(
                appContext,
                reason,
                snapshotBlocking(appContext, AlbumArtworkRuntime.settings.cacheSizeMb, LyricsRuntime.settings.cacheSizeMb, emptyList()),
                result.bytesDeleted,
                result.filesDeleted,
            )
            result.filesDeleted
        }

    suspend fun clearOnlineCaches(context: Context): Int =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val artwork = clearFiles(appContext, artworkCacheRoot(appContext), recursive = true, reason = CacheCleanupReason.UserClearOnline)
            val lyrics = clearFiles(appContext, lyricsCacheDir(appContext), recursive = true, reason = CacheCleanupReason.UserClearOnline)
            val temp = clearTempFilesBlocking(appContext, CacheCleanupReason.UserClearOnline, maxAgeMillis = 0L)
            val filesDeleted = artwork.filesDeleted + lyrics.filesDeleted + temp.filesDeleted
            val bytesDeleted = artwork.bytesDeleted + lyrics.bytesDeleted + temp.bytesDeleted
            logSnapshot(
                appContext,
                CacheCleanupReason.UserClearOnline,
                snapshotBlocking(appContext, AlbumArtworkRuntime.settings.cacheSizeMb, LyricsRuntime.settings.cacheSizeMb, emptyList()),
                bytesDeleted,
                filesDeleted,
            )
            filesDeleted
        }

    suspend fun clearTemp(context: Context, reason: CacheCleanupReason = CacheCleanupReason.UserClearTemp): Int =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val result = clearTempFilesBlocking(appContext, reason, maxAgeMillis = 0L)
            logSnapshot(
                appContext,
                reason,
                snapshotBlocking(appContext, AlbumArtworkRuntime.settings.cacheSizeMb, LyricsRuntime.settings.cacheSizeMb, emptyList()),
                result.bytesDeleted,
                result.filesDeleted,
            )
            result.filesDeleted
        }

    fun deleteTempFile(context: Context, file: File, reason: CacheCleanupReason): Boolean {
        if (!file.exists() || !file.isFile) return false
        val bytes = file.length()
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        if (deleted) logCleanup(context.applicationContext, reason, file.parentFile?.name ?: "temp", bytes, 1)
        return deleted
    }

    private fun enforceBlocking(
        context: Context,
        albumArtCacheMb: Int,
        lyricsCacheMb: Int,
        reason: CacheCleanupReason,
    ): CacheBudgetSnapshot {
        val budget = budget(context, albumArtCacheMb, lyricsCacheMb)
        val cleanupAge = if (reason == CacheCleanupReason.StartupStaleTemp) TEMP_STALE_AGE_MILLIS else 0L
        val results = listOf(
            clearTempFilesBlocking(context, reason, cleanupAge),
            enforceStreamingCacheLimit(context, budget.streamingBytes),
            enforceDirectoryLimit(context, artworkBucketDir(context, "full"), budget.artworkFullBytes, recursive = false, reason = CacheCleanupReason.BudgetExceeded),
            enforceDirectoryLimit(context, artworkBucketDir(context, "thumb"), budget.artworkThumbnailBytes, recursive = false, reason = CacheCleanupReason.BudgetExceeded),
            enforceDirectoryLimit(context, artworkBucketDir(context, "remote"), budget.artworkRemoteBytes, recursive = false, reason = CacheCleanupReason.BudgetExceeded),
            enforceDirectoryLimit(context, artworkCacheRoot(context), budget.artworkTotalBytes, recursive = true, reason = CacheCleanupReason.BudgetExceeded),
            enforceDirectoryLimit(context, lyricsCacheDir(context), budget.lyricsMetadataBytes, recursive = false, reason = CacheCleanupReason.BudgetExceeded),
            enforceDirectoryLimit(context, youtubeOfflineTempDir(context), budget.tempBytes, recursive = false, reason = CacheCleanupReason.BudgetExceeded),
        )
        val removedBytes = results.sumOf { it.bytesDeleted }
        val removedFiles = results.sumOf { it.filesDeleted }
        val snapshot = snapshotBlocking(context, albumArtCacheMb, lyricsCacheMb, emptyList())
        logSnapshot(context, reason, snapshot, removedBytes = removedBytes, removedFiles = removedFiles)
        return snapshot
    }

    private fun enforceStreamingCacheLimit(context: Context, limitBytes: Long): CleanupResult =
        if (PulsePlaybackCache.isInitialized()) {
            CleanupResult(0L, 0)
        } else {
            enforceDirectoryLimit(context, streamingCacheDir(context), limitBytes, recursive = true, reason = CacheCleanupReason.BudgetExceeded)
        }

    private fun snapshotBlocking(
        context: Context,
        albumArtCacheMb: Int,
        lyricsCacheMb: Int,
        offlineSources: List<YouTubeSource>,
    ): CacheBudgetSnapshot {
        val budget = budget(context, albumArtCacheMb, lyricsCacheMb)
        val streamingFiles = filesUnder(streamingCacheDir(context), recursive = true)
        val fullFiles = filesUnder(artworkBucketDir(context, "full"), recursive = false)
        val thumbFiles = filesUnder(artworkBucketDir(context, "thumb"), recursive = false)
        val remoteFiles = filesUnder(artworkBucketDir(context, "remote"), recursive = false)
        val legacyArtworkFiles = filesUnder(artworkCacheRoot(context), recursive = false)
        val lyricsFiles = filesUnder(lyricsCacheDir(context), recursive = false)
        val tempFiles = tempFiles(context)
        val localLibraryFiles = listOf(File(context.filesDir, "local_library_tracks.json")).filter { it.isFile }
        val dataStoreFiles = filesUnder(File(context.filesDir, "datastore"), recursive = true)
        val databaseFiles = filesUnder(context.getDatabasePath("pulse_settings_collections.db").parentFile ?: File(context.filesDir, "databases"), recursive = false)
            .filter { it.name.startsWith("pulse_settings_collections") || it.name.startsWith("premiumdeck_personalization") }
        val modelFiles = filesUnder(File(context.filesDir, "premiumdeck/models"), recursive = true)
        val appDataBytes = (localLibraryFiles + dataStoreFiles + databaseFiles + modelFiles).sumOf { it.length() }
        return CacheBudgetSnapshot(
            budget = budget,
            buckets = listOf(
                CacheBucketSnapshot("streaming", "Media3 streaming cache", CacheStorageCategory.Disposable, relativePath(context, streamingCacheDir(context)), streamingFiles.sumOf { it.length() }, streamingFiles.size, budget.streamingBytes, safeToDelete = false, userOwned = false),
                CacheBucketSnapshot("artwork_full", "Full artwork cache", CacheStorageCategory.Regenerable, relativePath(context, artworkBucketDir(context, "full")), fullFiles.sumOf { it.length() }, fullFiles.size, budget.artworkFullBytes, safeToDelete = true, userOwned = false),
                CacheBucketSnapshot("artwork_thumb", "Thumbnail cache", CacheStorageCategory.Disposable, relativePath(context, artworkBucketDir(context, "thumb")), thumbFiles.sumOf { it.length() }, thumbFiles.size, budget.artworkThumbnailBytes, safeToDelete = true, userOwned = false),
                CacheBucketSnapshot("artwork_remote", "Remote thumbnail cache", CacheStorageCategory.Disposable, relativePath(context, artworkBucketDir(context, "remote")), remoteFiles.sumOf { it.length() }, remoteFiles.size, budget.artworkRemoteBytes, safeToDelete = true, userOwned = false),
                CacheBucketSnapshot("artwork_legacy", "Legacy flat artwork cache", CacheStorageCategory.Regenerable, relativePath(context, artworkCacheRoot(context)), legacyArtworkFiles.sumOf { it.length() }, legacyArtworkFiles.size, null, safeToDelete = true, userOwned = false),
                CacheBucketSnapshot("lyrics", "Lyrics cache", CacheStorageCategory.Regenerable, relativePath(context, lyricsCacheDir(context)), lyricsFiles.sumOf { it.length() }, lyricsFiles.size, budget.lyricsMetadataBytes, safeToDelete = true, userOwned = false),
                CacheBucketSnapshot("temp", "Temp download/artwork files", CacheStorageCategory.Disposable, relativePath(context, context.cacheDir), tempFiles.sumOf { it.length() }, tempFiles.size, budget.tempBytes, safeToDelete = true, userOwned = false),
                CacheBucketSnapshot("app_data", "Settings, library, and personalization data", CacheStorageCategory.UserOwned, relativePath(context, context.filesDir), appDataBytes, localLibraryFiles.size + dataStoreFiles.size + databaseFiles.size + modelFiles.size, null, safeToDelete = false, userOwned = true),
            ),
            externalDownloadBytes = externalDownloadBytes(context, offlineSources),
            appDataBytes = appDataBytes,
        )
    }

    private fun budget(context: Context, albumArtCacheMb: Int, lyricsCacheMb: Int): PulseCacheBudget {
        val lowStorageOrRam = context.isLowRamDevice() || availableBytes(context.cacheDir) < LOW_STORAGE_AVAILABLE_BYTES
        val requestedArtworkMb = albumArtCacheMb.coerceIn(64, 512).toLong()
        val requestedLyricsMb = lyricsCacheMb.coerceIn(4, 128).toLong()
        val highStorage = !lowStorageOrRam && requestedArtworkMb > 256L && availableBytes(context.cacheDir) >= HIGH_STORAGE_AVAILABLE_BYTES
        val totalMb = when {
            lowStorageOrRam -> LOW_TOTAL_CACHE_MB
            highStorage -> HIGH_STORAGE_TOTAL_CACHE_MB
            else -> NORMAL_TOTAL_CACHE_MB
        }
        val streamingMb = if (lowStorageOrRam) LOW_STREAMING_CACHE_MB else NORMAL_STREAMING_CACHE_MB
        val tempMb = if (lowStorageOrRam) LOW_TEMP_CACHE_MB else NORMAL_TEMP_CACHE_MB
        val lyricsMb = min(requestedLyricsMb, if (lowStorageOrRam) 32L else 64L)
        val defaultArtworkMb = when {
            lowStorageOrRam -> LOW_ARTWORK_CACHE_MB
            highStorage -> HIGH_STORAGE_ARTWORK_CACHE_MB
            else -> NORMAL_ARTWORK_CACHE_MB
        }
        val maxArtworkByTotal = (totalMb - streamingMb - tempMb - lyricsMb).coerceAtLeast(64L)
        val artworkMb = min(requestedArtworkMb, min(defaultArtworkMb, maxArtworkByTotal)).coerceAtLeast(64L)
        val artworkBytes = artworkMb * CACHE_MB
        val fullBytes = max(32L * CACHE_MB, (artworkBytes * 0.58f).roundToLong())
            .coerceAtMost(artworkBytes - 32L * CACHE_MB)
        val thumbBytes = max(16L * CACHE_MB, (artworkBytes * 0.24f).roundToLong())
            .coerceAtMost(artworkBytes - fullBytes - 16L * CACHE_MB)
        val remoteBytes = artworkBytes - fullBytes - thumbBytes
        return PulseCacheBudget(
            totalBytes = totalMb * CACHE_MB,
            streamingBytes = streamingMb * CACHE_MB,
            artworkTotalBytes = artworkBytes,
            artworkFullBytes = fullBytes,
            artworkThumbnailBytes = thumbBytes,
            artworkRemoteBytes = remoteBytes,
            lyricsMetadataBytes = lyricsMb * CACHE_MB,
            tempBytes = tempMb * CACHE_MB,
        )
    }

    private fun enforceDirectoryLimit(
        context: Context,
        root: File,
        limitBytes: Long,
        recursive: Boolean,
        reason: CacheCleanupReason,
    ): CleanupResult {
        val files = filesUnder(root, recursive)
            .sortedByDescending { it.lastModified() }
        var total = files.sumOf { it.length() }
        var removedBytes = 0L
        var removedFiles = 0
        files.asReversed().forEach { file ->
            if (total <= limitBytes) return@forEach
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) {
                total -= length
                removedBytes += length
                removedFiles += 1
            }
        }
        if (removedFiles > 0) logCleanup(context, reason, relativePath(context, root), removedBytes, removedFiles)
        return CleanupResult(removedBytes, removedFiles)
    }

    private fun clearTempFilesBlocking(context: Context, reason: CacheCleanupReason, maxAgeMillis: Long): CleanupResult {
        val now = System.currentTimeMillis()
        val candidates = tempFiles(context).filter { file ->
            maxAgeMillis <= 0L || now - file.lastModified() >= maxAgeMillis
        }
        return clearFileList(context, candidates, reason, "temp")
    }

    private fun clearFiles(context: Context, root: File, recursive: Boolean, reason: CacheCleanupReason): CleanupResult =
        clearFileList(context, filesUnder(root, recursive), reason, relativePath(context, root))

    private fun clearFileList(context: Context, files: List<File>, reason: CacheCleanupReason, bucket: String): CleanupResult {
        var removedBytes = 0L
        var removedFiles = 0
        files.forEach { file ->
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) {
                removedBytes += length
                removedFiles += 1
            }
        }
        if (removedFiles > 0) logCleanup(context, reason, bucket, removedBytes, removedFiles)
        return CleanupResult(removedBytes, removedFiles)
    }

    private fun tempFiles(context: Context): List<File> =
        buildList {
            addAll(
                context.cacheDir.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.name.startsWith("album_art_remote_") },
            )
            addAll(filesUnder(youtubeOfflineTempDir(context), recursive = false))
        }

    private fun externalDownloadBytes(context: Context, sources: List<YouTubeSource>): Long =
        sources
            .asSequence()
            .mapNotNull { it.downloadedUri }
            .distinct()
            .mapNotNull { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
            .sumOf { uri ->
                runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                        descriptor.length.takeIf { it > 0L } ?: 0L
                    } ?: 0L
                }.getOrDefault(0L)
            }

    private fun streamingCacheDir(context: Context): File =
        File(context.cacheDir, "media3-youtube-cache")

    private fun artworkCacheRoot(context: Context): File =
        File(context.cacheDir, "album_art_cache")

    private fun artworkBucketDir(context: Context, bucket: String): File =
        File(artworkCacheRoot(context), bucket)

    private fun lyricsCacheDir(context: Context): File =
        File(context.cacheDir, "lyrics_cache")

    private fun youtubeOfflineTempDir(context: Context): File =
        File(context.cacheDir, "youtube_offline")

    private fun filesUnder(root: File, recursive: Boolean): List<File> =
        runCatching {
            if (!root.exists()) return@runCatching emptyList<File>()
            if (recursive) {
                root.walkTopDown().filter { it.isFile }.toList()
            } else {
                root.listFiles().orEmpty().filter { it.isFile }
            }
        }.getOrDefault(emptyList())

    private fun availableBytes(root: File): Long =
        runCatching {
            val stat = StatFs(root.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                @Suppress("DEPRECATION")
                stat.availableBlocks.toLong() * stat.blockSize.toLong()
            }
        }.getOrDefault(Long.MAX_VALUE)

    private fun Context.isLowRamDevice(): Boolean =
        (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true

    private fun relativePath(context: Context, file: File): String {
        val path = file.absolutePath
        val roots = listOf(
            context.cacheDir.absolutePath to "cacheDir",
            context.filesDir.absolutePath to "filesDir",
            context.applicationInfo.dataDir to "dataDir",
        )
        roots.forEach { (root, label) ->
            if (path == root) return label
            if (path.startsWith("$root${File.separator}")) return "$label/${path.removePrefix("$root${File.separator}")}"
        }
        return path
    }

    private fun logCleanup(context: Context, reason: CacheCleanupReason, bucket: String, bytes: Long, files: Int) {
        if (!context.isCacheBudgetDebuggable()) return
        Log.d(
            CACHE_LOG_TAG,
            "cleanup reason=${reason.label} bucket=$bucket removed_bytes=$bytes removed_files=$files",
        )
    }

    private fun logSnapshot(context: Context, reason: CacheCleanupReason, snapshot: CacheBudgetSnapshot, removedBytes: Long, removedFiles: Int) {
        if (!context.isCacheBudgetDebuggable()) return
        val bucketText = snapshot.buckets.joinToString(" ") { bucket ->
            "${bucket.id}_bytes=${bucket.bytes}"
        }
        Log.d(
            CACHE_LOG_TAG,
            "status reason=${reason.label} total_bytes=${snapshot.managedCacheBytes} total_budget_bytes=${snapshot.budget.totalBytes} " +
                "removed_bytes=$removedBytes removed_files=$removedFiles external_download_bytes=${snapshot.externalDownloadBytes} " +
                "app_data_bytes=${snapshot.appDataBytes} $bucketText",
        )
    }

    private fun Context.isCacheBudgetDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private data class CleanupResult(val bytesDeleted: Long, val filesDeleted: Int)
}

private fun Float.roundToLong(): Long =
    roundToInt().toLong()
