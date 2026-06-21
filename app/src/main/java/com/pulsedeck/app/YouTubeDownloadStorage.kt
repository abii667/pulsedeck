package com.pulsedeck.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private fun streamLibraryRelativePath(): String =
    "${musicDirectoryName()}/$STREAM_LIBRARY_FOLDER"

private fun musicDirectoryName(): String =
    Environment.DIRECTORY_MUSIC ?: "Music"

private fun mediaStoreAudioMimeType(rawMimeType: String?): String {
    val mimeType = rawMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.US)
        .orEmpty()
    return when {
        mimeType.startsWith("audio/") -> mimeType
        mimeType == "video/mp4" || mimeType == "application/mp4" -> "audio/mp4"
        mimeType == "video/webm" || mimeType == "application/webm" -> "audio/webm"
        else -> "audio/mp4"
    }
}

private fun mimeTypeFromYouTubeExt(ext: String?): String? =
    when (ext?.lowercase(Locale.US)) {
        "webm", "opus" -> "audio/webm"
        "m4a", "mp4", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        else -> null
    }

private data class LocalAlbumSaveMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val filename: String,
    val relativePath: String,
    val trackNumber: Int = 0,
    val year: Int = 0,
)

private const val RESOLVER_CAPABILITIES_CACHE_TTL_MILLIS = 10L * 60L * 1000L

@Volatile
private var cachedResolverCapabilities: Pair<Long, ResolverCapabilities>? = null

internal fun albumProjectLocalRelativePath(artist: String, album: String): String =
    "${musicDirectoryName()}/PulseDeck/${safeFileComponent(artist.ifBlank { PREMIUMDECK_SOURCE_NAME })}/${safeFileComponent(album.ifBlank { STREAM_LIBRARY_ALBUM })}"

private fun YouTubeSource.albumProjectAlbumTitle(): String? =
    albumTitleHint
        .cleanStrictStreamAlbumTitle()
        .takeIf { it.isReliableStrictAlbumTitle() }

private fun YouTubeSource.localAlbumSaveMetadata(
    defaultTitle: String,
    defaultArtist: String,
    mimeType: String,
    sourceFilename: String? = null,
): LocalAlbumSaveMetadata {
    val extension = streamLibraryExtension(mimeType, sourceFilename)
    val projectAlbum = albumProjectAlbumTitle()
    val title = if (projectAlbum != null) {
        this.title.ifBlank { defaultTitle }.trim()
    } else {
        defaultTitle.ifBlank { this.title }.trim()
    }.ifBlank { "Untitled track" }
    val artist = if (projectAlbum != null) {
        author.ifBlank { defaultArtist }.trim()
    } else {
        defaultArtist.ifBlank { author }.trim()
    }.ifBlank { PREMIUMDECK_SOURCE_NAME }
    val album = projectAlbum ?: strictStreamAlbumTitle()?.takeIf { it.isReliableStrictAlbumTitle() } ?: STREAM_LIBRARY_ALBUM
    val albumArtist = when {
        projectAlbum != null -> author.ifBlank { artist }.trim().ifBlank { artist }
        album == STREAM_LIBRARY_ALBUM -> STREAM_LIBRARY_ALBUM_ARTIST
        else -> artist.ifBlank { author }.trim().ifBlank { PREMIUMDECK_SOURCE_NAME }
    }
    val trackNumber = albumTrackNumberHint.takeIf { it > 0 } ?: (streamAlbumTrackNumber(title) ?: 0)
    val filename = if (projectAlbum != null && trackNumber > 0) {
        "${trackNumber.toString().padStart(2, '0')} - ${safeFileComponent(title)}.$extension"
    } else {
        streamLibraryFilename(artist = artist, title = title, mimeType = mimeType, sourceFilename = sourceFilename)
    }
    return LocalAlbumSaveMetadata(
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        filename = filename,
        relativePath = if (projectAlbum != null) albumProjectLocalRelativePath(albumArtist, album) else streamLibraryRelativePath(),
        trackNumber = trackNumber,
        year = albumYearHint.takeIf { it > 0 } ?: 0,
    )
}

private fun ContentValues.putLocalAlbumSaveMetadata(metadata: LocalAlbumSaveMetadata, mimeType: String, durationMillis: Long) {
    put(MediaStore.Audio.Media.DISPLAY_NAME, metadata.filename)
    put(MediaStore.Audio.Media.TITLE, metadata.title)
    put(MediaStore.Audio.Media.ARTIST, metadata.artist)
    put(MediaStore.Audio.Media.ALBUM, metadata.album)
    put(MediaStore.Audio.Media.ALBUM_ARTIST, metadata.albumArtist)
    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
    put(MediaStore.Audio.Media.IS_MUSIC, true)
    if (metadata.trackNumber > 0) put(MediaStore.Audio.Media.TRACK, metadata.trackNumber)
    if (metadata.year > 0) put(MediaStore.Audio.Media.YEAR, metadata.year)
    if (durationMillis > 0L) put(MediaStore.Audio.Media.DURATION, durationMillis)
}

private suspend fun saveLocalYouTubeFileToMusic(
    context: Context,
    file: File,
    source: YouTubeSource,
    title: String,
    artist: String,
    mimeType: String,
    durationMillis: Long,
): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val audioMimeType = mediaStoreAudioMimeType(mimeType)
            val localMetadata = source.localAlbumSaveMetadata(
                defaultTitle = title,
                defaultArtist = artist,
                mimeType = audioMimeType,
                sourceFilename = file.name,
            )
            val savedDurationMillis = durationMillis.takeIf { it > 0L } ?: source.durationMillis
            val values = ContentValues().apply {
                putLocalAlbumSaveMetadata(localMetadata, audioMimeType, savedDurationMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, localMetadata.relativePath)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
            runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                }
            }.onFailure {
                resolver.delete(uri, null, null)
                throw it
            }
            uri
        }.onFailure { throwable ->
            Log.w(YOUTUBE_LOG_TAG, "Could not save offline stream to Music; sourceMime=$mimeType, file=${file.name}", throwable)
        }.getOrNull()
    }

internal suspend fun downloadYouTubeToMusicOnDevice(
    context: Context,
    source: YouTubeSource,
    onProgress: suspend (Int) -> Unit,
): YouTubeLocalDownloadResult? =
    withContext(Dispatchers.IO) {
        var downloadedTemp: File? = null
        val result = runCatching {
            if (source.trimSilenceOnDownload) return@runCatching null
            var lastProgress = 0
            suspend fun publishProgress(value: Int) {
                val clamped = value.coerceIn(1, 99)
                if (clamped > lastProgress) {
                    lastProgress = clamped
                    onProgress(clamped)
                }
            }
            publishProgress(4)
            val metadata = resolveYouTubeAudioWithNewPipe(source)
                ?: resolveYouTubeAudioOnDevice(context, source)
                ?: return@runCatching null
            publishProgress(14)
            val inferred = smartYouTubeMusicMetadata(
                rawTitle = metadata.title.ifBlank { source.title },
                rawAuthor = metadata.artist.ifBlank { source.author },
            )
            val albumProject = source.albumProjectAlbumTitle() != null
            val title = if (albumProject) {
                source.title.ifBlank { inferred.title.ifBlank { metadata.title } }
            } else {
                inferred.title.ifBlank { metadata.title.ifBlank { source.title } }
            }
            val artist = if (albumProject) {
                source.author.ifBlank { metadata.artist.ifBlank { inferred.artist } }.ifBlank { PREMIUMDECK_SOURCE_NAME }
            } else {
                inferred.artistOrSafeFallback(metadata.artist.ifBlank { source.author })
            }
            val workDir = File(context.cacheDir, "youtube_offline").apply { mkdirs() }
            val prefix = "${safeFileComponent(source.id)}-${System.currentTimeMillis()}"
            workDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
            val mimeType = mediaStoreAudioMimeType(metadata.mimeType)
            val extension = streamLibraryExtension(mimeType, null)
            val downloaded = File(workDir, "$prefix.$extension")
            downloadedTemp = downloaded
            val connection = URL(metadata.streamUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 120_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "PulseDeck/0.1")
            connection.setRequestProperty("Accept", "audio/*,*/*")
            if (connection.responseCode !in 200..299) return@runCatching null
            publishProgress(18)
            val totalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) connection.contentLengthLong else connection.contentLength.toLong()
            var copiedBytes = 0L
            downloaded.outputStream().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copiedBytes += read
                        if (totalBytes > 0L) {
                            val progress = (18L + (copiedBytes * 74L) / totalBytes).toInt()
                            publishProgress(progress)
                        } else {
                            val progress = (18L + copiedBytes / (256L * 1024L)).toInt().coerceAtMost(92)
                            publishProgress(progress)
                        }
                    }
                }
            }
            publishProgress(96)
            val uri = saveLocalYouTubeFileToMusic(
                context = context,
                file = downloaded,
                source = source,
                title = title,
                artist = artist,
                mimeType = mimeType,
                durationMillis = metadata.durationMillis,
            ) ?: return@runCatching null
            CacheBudgetManager.deleteTempFile(context, downloaded, CacheCleanupReason.DownloadTempCleanup)
            downloadedTemp = null
            YouTubeLocalDownloadResult(
                uri = uri,
                title = title,
                artist = artist,
                mimeType = mimeType,
                durationMillis = metadata.durationMillis,
                chapters = metadata.chapters,
                sponsorSegments = metadata.sponsorSegments,
            )
        }.getOrNull()
        downloadedTemp?.let { tempFile ->
            CacheBudgetManager.deleteTempFile(context, tempFile, CacheCleanupReason.FailedDownloadCleanup)
        }
        result
    }

internal suspend fun fetchResolverCapabilities(context: Context): ResolverCapabilities =
    withContext(Dispatchers.IO) {
        val nowMillis = SystemClock.elapsedRealtime()
        cachedResolverCapabilities
            ?.takeIf { (savedMillis, _) -> nowMillis - savedMillis <= RESOLVER_CAPABILITIES_CACHE_TTL_MILLIS }
            ?.let { (_, capabilities) ->
                YouTubeNetworkDiagnostics.reportResolverHealth(requests = 0, failures = 0, cached = true, capabilities = capabilities)
                return@withContext capabilities
            }

        val pipedAvailable = PIPED_API_ENDPOINTS.isNotEmpty()
        var requests = 0
        var failures = 0
        for (endpoint in youtubeResolverEndpoints()) {
            requests += 1
            val capabilities = runCatching {
                val connection = URL("$endpoint/health").openConnection() as HttpURLConnection
                connection.connectTimeout = 1200
                connection.readTimeout = 2500
                connection.requestMethod = "GET"
                if (connection.responseCode !in 200..299) return@runCatching null
                val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                ResolverCapabilities(
                    ytDlpAvailable = pipedAvailable || json.optBoolean("ytDlpAvailable", false),
                    ffmpegAvailable = json.optBoolean("ffmpegAvailable", false),
                    sponsorBlockAvailable = json.optBoolean("sponsorBlockAvailable", false),
                )
            }.getOrNull()
            if (capabilities != null) {
                cachedResolverCapabilities = nowMillis to capabilities
                YouTubeNetworkDiagnostics.reportResolverHealth(requests = requests, failures = failures, cached = false, capabilities = capabilities)
                return@withContext capabilities
            }
            failures += 1
        }
        val fallback = ResolverCapabilities(ytDlpAvailable = pipedAvailable, ffmpegAvailable = false, sponsorBlockAvailable = true)
        cachedResolverCapabilities = nowMillis to fallback
        YouTubeNetworkDiagnostics.reportResolverHealth(requests = requests, failures = failures, cached = false, capabilities = fallback)
        fallback
    }

internal suspend fun startResolverDownload(source: YouTubeSource): YouTubeDownloadJob? =
    withContext(Dispatchers.IO) {
        val qualityKbps = if (source.quality == YouTubeQuality.High) "256" else "128"
        val encodedUrl = URLEncoder.encode(source.url, "UTF-8")
        for (endpoint in youtubeResolverEndpoints()) {
            val job = runCatching {
                val requestUrl = "$endpoint/download/start?url=$encodedUrl&quality=$qualityKbps&trimSilence=${source.trimSilenceOnDownload}"
                val connection = URL(requestUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 2600
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                if (connection.responseCode !in 200..299) return@runCatching null
                parseDownloadJob(connection.inputStream.bufferedReader().use { JSONObject(it.readText()) })
            }.getOrNull()
            if (job != null) return@withContext job
        }
        null
    }

internal suspend fun getResolverDownloadStatus(jobId: String): YouTubeDownloadJob? =
    withContext(Dispatchers.IO) {
        for (endpoint in youtubeResolverEndpoints()) {
            val job = runCatching {
                val connection = URL("$endpoint/download/$jobId/status").openConnection() as HttpURLConnection
                connection.connectTimeout = 1200
                connection.readTimeout = 2500
                connection.requestMethod = "GET"
                if (connection.responseCode !in 200..299) return@runCatching null
                parseDownloadJob(connection.inputStream.bufferedReader().use { JSONObject(it.readText()) })
            }.getOrNull()
            if (job != null) return@withContext job
        }
        null
    }

private fun parseDownloadJob(json: JSONObject): YouTubeDownloadJob =
    YouTubeDownloadJob(
        id = json.optString("id"),
        status = json.optString("status", "failed"),
        progress = json.optInt("progress", 0),
        title = json.optString("title"),
        artist = json.optString("artist"),
        filename = json.optString("filename"),
        mimeType = json.optString("mimeType", "audio/mp4"),
        durationMillis = json.optLong("durationMillis", json.optLong("duration", 0L) * 1000L),
        error = json.optString("error").takeIf { it.isNotBlank() },
        chapters = parseYouTubeChapters(json.optJSONArray("chapters")),
        sponsorSegments = parseSponsorSegments(json.optJSONArray("sponsorSegments")),
    )

internal suspend fun saveResolverDownloadToMusic(context: Context, job: YouTubeDownloadJob, source: YouTubeSource): Uri? =
    withContext(Dispatchers.IO) {
        for (endpoint in youtubeResolverEndpoints()) {
            val uri = runCatching {
                val connection = URL("$endpoint/download/${job.id}/file").openConnection() as HttpURLConnection
                connection.connectTimeout = 2600
                connection.readTimeout = 120_000
                connection.requestMethod = "GET"
                if (connection.responseCode !in 200..299) return@runCatching null
                val inferred = smartYouTubeMusicMetadata(job.title.ifBlank { source.title }, job.artist.ifBlank { source.author })
                val albumProject = source.albumProjectAlbumTitle() != null
                val title = if (albumProject) {
                    source.title.ifBlank { inferred.title.ifBlank { job.title } }
                } else {
                    inferred.title.ifBlank { job.title.ifBlank { source.title } }
                }
                val artist = if (albumProject) {
                    source.author.ifBlank { job.artist.ifBlank { inferred.artist } }.ifBlank { PREMIUMDECK_SOURCE_NAME }
                } else {
                    inferred.artistOrSafeFallback(job.artist.ifBlank { source.author })
                }
                val mimeType = mediaStoreAudioMimeType(job.mimeType.ifBlank { connection.contentType ?: "audio/mp4" })
                val localMetadata = source.localAlbumSaveMetadata(
                    defaultTitle = title,
                    defaultArtist = artist,
                    mimeType = mimeType,
                    sourceFilename = job.filename,
                )
                val savedDurationMillis = job.durationMillis.takeIf { it > 0L } ?: source.durationMillis
                val values = ContentValues().apply {
                    putLocalAlbumSaveMetadata(localMetadata, mimeType, savedDurationMillis)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, localMetadata.relativePath)
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
                runCatching {
                    resolver.openOutputStream(uri)?.use { output ->
                        connection.inputStream.use { input -> input.copyTo(output) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val complete = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                        resolver.update(uri, complete, null, null)
                    }
                }.onFailure {
                    resolver.delete(uri, null, null)
                    throw it
                }
                uri
            }.getOrNull()
            if (uri != null) return@withContext uri
        }
        null
    }

internal fun safeFileComponent(value: String): String =
    value.map { char -> if (char.isLetterOrDigit() || char in " ._-") char else '_' }
        .joinToString("")
        .trim()
        .ifBlank { PREMIUMDECK_SOURCE_NAME }
        .take(80)

private fun streamLibraryFilename(artist: String, title: String, mimeType: String, sourceFilename: String? = null): String {
    val extension = streamLibraryExtension(mimeType, sourceFilename)
    return "${safeFileComponent(artist)} - ${safeFileComponent(title)}.$extension"
}

private fun streamLibraryExtension(mimeType: String, sourceFilename: String?): String {
    val normalizedMimeType = mimeType.substringBefore(';').trim().lowercase(Locale.US)
    when {
        normalizedMimeType == "audio/mp4" || normalizedMimeType == "audio/aac" -> return "m4a"
        normalizedMimeType == "audio/webm" || normalizedMimeType == "audio/opus" -> return "webm"
        normalizedMimeType == "audio/mpeg" || normalizedMimeType == "audio/mp3" -> return "mp3"
    }
    val sourceExtension = sourceFilename
        ?.substringAfterLast('.', "")
        ?.lowercase(Locale.US)
        ?.takeIf { it.isNotBlank() && mimeTypeFromYouTubeExt(it) != null }
    if (sourceExtension != null) return sourceExtension
    return when {
        mimeType.contains("webm", ignoreCase = true) || mimeType.contains("opus", ignoreCase = true) -> "webm"
        mimeType.contains("video/mp4", ignoreCase = true) -> "m4a"
        mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "mp3"
        else -> "m4a"
    }
}
