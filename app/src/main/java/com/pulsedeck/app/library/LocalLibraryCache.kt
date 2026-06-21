package com.pulsedeck.app.library

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pulsedeck.app.Album
import com.pulsedeck.app.Track
import com.pulsedeck.app.formatDuration
import com.pulsedeck.app.loudnessMetadataFromJson
import com.pulsedeck.app.normalizedSearchText
import com.pulsedeck.app.toJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

private const val LOCAL_LIBRARY_CACHE_FILE_NAME = "local_library_tracks.json"
private const val LOCAL_LIBRARY_CACHE_VERSION = 3

internal var lastLocalLibraryCacheMigrationVersion: Int? = null

internal fun localLibraryCacheFile(context: Context): File =
    File(context.filesDir, LOCAL_LIBRARY_CACHE_FILE_NAME)

internal fun loadCachedDeviceTracks(context: Context): List<Track>? =
    runCatching {
        lastLocalLibraryCacheMigrationVersion = null
        val file = localLibraryCacheFile(context)
        if (!file.exists()) return@runCatching null
        val root = JSONObject(file.readText())
        val version = root.optInt("version", 0)
        if (version > LOCAL_LIBRARY_CACHE_VERSION) return@runCatching null
        val tracks = parseCachedDeviceTracks(root.optJSONArray("tracks"))
        if (version < LOCAL_LIBRARY_CACHE_VERSION) {
            lastLocalLibraryCacheMigrationVersion = version
        }
        tracks
    }.getOrNull()

internal fun saveCachedDeviceTracks(context: Context, tracks: List<Track>) {
    runCatching {
        val root = JSONObject()
            .put("version", LOCAL_LIBRARY_CACHE_VERSION)
            .put("savedMillis", System.currentTimeMillis())
            .put("tracks", cachedDeviceTracksToJson(tracks))
        localLibraryCacheFile(context).writeText(root.toString())
    }
}

internal fun parseCachedDeviceTracks(array: JSONArray?): List<Track> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val albumJson = item.optJSONObject("album") ?: continue
            val album = Album(
                title = albumJson.optString("title").ifBlank { "Unknown album" },
                artist = albumJson.optString("artist").ifBlank { "Unknown artist" },
                mark = albumJson.optString("mark").ifBlank { "PD" },
                tint = cachedColorFromHex(albumJson.optString("tint").ifBlank { "#6E63F2" }),
                alt = cachedColorFromHex(albumJson.optString("alt").ifBlank { "#80F1C6" }),
                coverUri = albumJson.optString("coverUri").takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() },
                sourceUri = albumJson.optString("sourceUri").takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() },
                groupKey = albumJson.optString("groupKey").ifBlank { cachedAlbumGroupKey(albumJson.optString("title")) },
            )
            val durationMillis = item.optLong("durationMillis", 0L).coerceAtLeast(0L)
            val trackUri = item.optString("uri").takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            add(
                Track(
                    title = item.optString("title").ifBlank { "Unknown track" },
                    artist = item.optString("artist").ifBlank { "Unknown artist" },
                    duration = item.optString("duration").ifBlank { formatDuration(durationMillis) },
                    album = album,
                    uri = trackUri,
                    durationMillis = durationMillis,
                    quality = item.optString("quality").ifBlank { "ANALYZING AUDIO" },
                    mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
                    displayName = item.optString("displayName").takeIf { it.isNotBlank() },
                    sizeBytes = item.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                    folderPath = item.optString("folderPath").takeIf { it.isNotBlank() },
                    modifiedMillis = item.optLong("modifiedMillis", 0L).coerceAtLeast(0L),
                    genre = item.optString("genre").takeIf { it.isNotBlank() },
                    albumArtist = item.optString("albumArtist").takeIf { it.isNotBlank() },
                    composer = item.optString("composer").takeIf { it.isNotBlank() },
                    year = item.optInt("year", 0).takeIf { it > 0 },
                    loudnessMetadata = loudnessMetadataFromJson(item.optJSONObject("loudness")),
                ),
            )
        }
    }

internal fun cachedDeviceTracksToJson(tracks: List<Track>): JSONArray =
    JSONArray().apply {
        tracks.forEach { track ->
            put(
                JSONObject()
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("duration", track.duration)
                    .put("uri", track.uri?.toString().orEmpty())
                    .put("durationMillis", track.durationMillis)
                    .put("quality", track.quality)
                    .put("mimeType", track.mimeType.orEmpty())
                    .put("displayName", track.displayName.orEmpty())
                    .put("sizeBytes", track.sizeBytes)
                    .put("folderPath", track.folderPath.orEmpty())
                    .put("modifiedMillis", track.modifiedMillis)
                    .put("genre", track.genre.orEmpty())
                    .put("albumArtist", track.albumArtist.orEmpty())
                    .put("composer", track.composer.orEmpty())
                    .put("year", track.year ?: 0)
                    .put("loudness", track.loudnessMetadata.toJson())
                    .put(
                        "album",
                        JSONObject()
                            .put("title", track.album.title)
                            .put("artist", track.album.artist)
                            .put("mark", track.album.mark)
                            .put("tint", track.album.tint.toCacheHex())
                            .put("alt", track.album.alt.toCacheHex())
                            .put("coverUri", track.album.coverUri?.toString().orEmpty())
                            .put("sourceUri", track.album.sourceUri?.toString().orEmpty())
                            .put("groupKey", track.album.groupKey),
                    ),
            )
        }
    }

private fun Color.toCacheHex(): String =
    "#${(toArgb() and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase(Locale.US)}"

private fun cachedColorFromHex(raw: String): Color {
    val candidate = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
    val valid = Regex("^#[0-9a-fA-F]{6}$").matches(candidate)
    val value = (if (valid) candidate else "#000000").removePrefix("#").toLong(16)
    return Color((0xFF000000L or value).toInt())
}

private fun cachedAlbumGroupKey(title: String): String {
    val normalized = title.normalizedSearchText()
    return "album:${normalized.ifBlank { title.trim().lowercase(Locale.US) }}"
}
