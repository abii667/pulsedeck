package com.pulsedeck.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

internal suspend fun downloadFreeLegalAlbumToMusic(
    context: Context,
    release: AlbumDownloadRelease,
    onProgress: (Int) -> Unit,
): Int =
    withContext(Dispatchers.IO) {
        val tracks = release.tracks
            .filter { it.downloadAllowed && it.downloadUrl.isNotBlank() }
            .sortedBy { it.position }
        var saved = 0
        tracks.forEachIndexed { index, track ->
            val progressBase = ((index.toFloat() / tracks.size.coerceAtLeast(1)) * 100f).roundToInt()
            onProgress(progressBase.coerceIn(1, 99))
            val uri = runCatching { saveFreeLegalAlbumTrackToMusic(context, release, track) }.getOrNull()
            if (uri != null) saved += 1
        }
        onProgress(if (saved > 0) 100 else 0)
        saved
    }

private fun saveFreeLegalAlbumTrackToMusic(
    context: Context,
    release: AlbumDownloadRelease,
    track: AlbumDownloadTrack,
): Uri? {
    val mimeType = track.mimeType.ifBlank { "audio/flac" }
    val extension = albumAudioExtension(mimeType, track.downloadUrl)
    val filename = "${track.position.toString().padStart(2, '0')} - ${safeFileComponent(track.title)}.$extension"
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
        put(MediaStore.Audio.Media.TITLE, track.title)
        put(MediaStore.Audio.Media.ARTIST, release.artist)
        put(MediaStore.Audio.Media.ALBUM, release.title)
        put(MediaStore.Audio.Media.ALBUM_ARTIST, release.artist)
        put(MediaStore.Audio.Media.TRACK, track.position)
        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
        put(MediaStore.Audio.Media.IS_MUSIC, true)
        release.date.take(4).toIntOrNull()?.takeIf { it > 0 }?.let { put(MediaStore.Audio.Media.YEAR, it) }
        if (track.durationMillis > 0L) put(MediaStore.Audio.Media.DURATION, track.durationMillis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Audio.Media.RELATIVE_PATH, freeLegalAlbumRelativePath(release))
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    runCatching {
        val connection = URL(track.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 120_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "audio/*,*/*")
        connection.setRequestProperty("User-Agent", "PulseDeck/0.1 (${release.source} ${release.downloadQuality})")
        if (connection.responseCode !in 200..299) error("Album file download failed")
        resolver.openOutputStream(uri)?.use { output ->
            connection.inputStream.use { input -> input.copyTo(output) }
        } ?: error("Could not open MediaStore output")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        }
    }.onFailure {
        resolver.delete(uri, null, null)
        return null
    }
    return uri
}

private fun freeLegalAlbumRelativePath(release: AlbumDownloadRelease): String =
    "${Environment.DIRECTORY_MUSIC}/PulseDeck/Album Downloads/${safeFileComponent("${release.artist} - ${release.title}")}"

private fun albumAudioExtension(mimeType: String, url: String): String {
    val fromUrl = url.substringBefore("?").substringAfterLast('.', "").lowercase(Locale.US)
    if (fromUrl in setOf("flac", "m4a", "mp3", "wav", "ogg", "opus", "aac")) return fromUrl
    return when {
        mimeType.contains("flac", ignoreCase = true) -> "flac"
        mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "mp3"
        mimeType.contains("wav", ignoreCase = true) -> "wav"
        mimeType.contains("ogg", ignoreCase = true) -> "ogg"
        mimeType.contains("opus", ignoreCase = true) -> "opus"
        else -> "m4a"
    }
}
