package com.pulsedeck.app.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.Album
import com.pulsedeck.app.Track
import com.pulsedeck.app.formatDuration
import com.pulsedeck.app.stableKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max

private const val STREAM_LIBRARY_ALBUM = "Stream Library"
private const val STREAM_LIBRARY_ALBUM_ARTIST = "PulseDeck"
private const val STREAM_LIBRARY_FOLDER = "PulseDeck/Stream Library"
private const val STREAM_LIBRARY_FOLDER_WITH_MUSIC = "Music/PulseDeck/Stream Library"
private const val PREMIUMDECK_SOURCE_CATEGORY = "PremiumDeck"
private const val PREMIUMDECK_SOURCE_MARK = "PD"
private const val LEGACY_YOUTUBE_SOURCE_CATEGORY = "YouTube Sources"

internal class LocalLibraryScanner(
    private val metadataReader: LocalTrackMetadataReader = LocalTrackMetadataReader(),
) {
    suspend fun scanDeviceTracks(
        context: Context,
        limit: Int = 700,
        previousTracks: List<Track> = emptyList(),
        reason: LocalLibraryScanReason = LocalLibraryScanReason.CacheMiss,
    ): LocalLibraryScanResult {
        val startedAt = pulseDeckLibraryNow()
        val maxTracks = max(0, limit)
        PulseDeckLibraryDiagnostics.scanStart(reason, previousTracks.size, maxTracks)
        val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()
        val previousByUri = previousTracks
            .mapNotNull { track -> track.uri?.toString()?.let { key -> key to track } }
            .toMap()
        val previousByFingerprint = previousTracks
            .mapNotNull { track -> track.localLibraryFingerprint()?.let { fingerprint -> fingerprint to track } }
            .distinctBy { it.first }
            .toMap()
        val seenPreviousKeys = mutableSetOf<String>()
        val keyReplacements = linkedMapOf<String, String>()
        val tracks = mutableListOf<Track>()
        var mediaStoreRows = 0
        var added = 0
        var updated = 0
        var unchanged = 0
        var retrieverOpens = 0
        var retrieverTotalMillis = 0L
        var retrieverMaxMillis = 0L
        try {
            context.contentResolver.query(
                mediaUri,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext() && tracks.size < maxTracks) {
                    currentCoroutineContext().ensureActive()
                    mediaStoreRows += 1
                    val id = cursor.getLong(idIndex)
                    val title = cursor.getString(titleIndex).cleanUnknown("Unknown track")
                    val artist = cursor.getString(artistIndex).cleanUnknown("Unknown artist")
                    val albumName = cursor.getString(albumIndex).cleanUnknown("Unknown album")
                    val albumId = cursor.getLong(albumIdIndex)
                    val duration = cursor.getLong(durationIndex).coerceAtLeast(0L)
                    val mimeType = cursor.getStringOrNull(MediaStore.Audio.Media.MIME_TYPE)
                    val displayName = cursor.getStringOrNull(MediaStore.Audio.Media.DISPLAY_NAME)
                    val sizeBytes = cursor.getLongOrZero(MediaStore.Audio.Media.SIZE)
                    val modifiedMillis = cursor.getLongOrZero(MediaStore.Audio.Media.DATE_MODIFIED) * 1000L
                    val folderPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getStringOrNull(MediaStore.Audio.Media.RELATIVE_PATH)?.trim('/')?.ifBlank { null }
                    } else {
                        @Suppress("DEPRECATION")
                        cursor.getStringOrNull(MediaStore.Audio.Media.DATA)?.substringBeforeLast('/', "")?.trim('/')?.ifBlank { null }
                    }
                    val trackUri = ContentUris.withAppendedId(mediaUri, id)
                    val album = if (albumName == STREAM_LIBRARY_ALBUM && isStreamLibraryFolder(folderPath)) {
                        Album(
                            title = STREAM_LIBRARY_ALBUM,
                            artist = STREAM_LIBRARY_ALBUM_ARTIST,
                            mark = PREMIUMDECK_SOURCE_MARK,
                            tint = Color(0xFFFF3B30),
                            alt = Color(0xFFFFD166),
                            coverUri = albumArtUri(albumId),
                            sourceUri = trackUri,
                        )
                    } else {
                        albumFor(albumName, artist, albumArtUri(albumId), trackUri)
                    }
                    val cachedByUri = previousByUri[trackUri.toString()]
                    val currentFingerprint = localLibraryFingerprint(displayName, folderPath, sizeBytes, modifiedMillis, duration)
                    val cachedByFingerprint = if (cachedByUri == null) currentFingerprint?.let(previousByFingerprint::get) else null
                    val cachedTrack = cachedByUri ?: cachedByFingerprint
                    val sameIdentity = cachedTrack != null && cachedTrack.hasSameLocalIdentity(
                        displayName = displayName,
                        folderPath = folderPath,
                        sizeBytes = sizeBytes,
                        modifiedMillis = modifiedMillis,
                        durationMillis = duration,
                    )
                    val sameMediaColumns = cachedTrack != null && cachedTrack.hasSameMediaColumns(
                        title = title,
                        artist = artist,
                        albumName = albumName,
                        uri = trackUri,
                        durationMillis = duration,
                        mimeType = mimeType,
                        displayName = displayName,
                        sizeBytes = sizeBytes,
                        folderPath = folderPath,
                        modifiedMillis = modifiedMillis,
                    )
                    val metadata = if (cachedTrack != null && sameIdentity) {
                        cachedTrack.toLocalTrackMetadata()
                    } else {
                        val retrieverStartedAt = pulseDeckLibraryNow()
                        val readMetadata = metadataReader.read(context, trackUri)
                        val retrieverMillis = pulseDeckLibraryNow() - retrieverStartedAt
                        retrieverOpens += 1
                        retrieverTotalMillis += retrieverMillis
                        if (retrieverMillis > retrieverMaxMillis) retrieverMaxMillis = retrieverMillis
                        readMetadata
                    }
                    val scannedTrack = Track(
                        title = title,
                        artist = artist,
                        duration = formatDuration(duration),
                        album = album,
                        uri = trackUri,
                        durationMillis = duration,
                        quality = cachedTrack?.quality ?: "ANALYZING AUDIO",
                        mimeType = mimeType,
                        displayName = displayName,
                        sizeBytes = sizeBytes,
                        folderPath = folderPath,
                        modifiedMillis = modifiedMillis,
                        genre = metadata.genre,
                        albumArtist = metadata.albumArtist,
                        composer = metadata.composer,
                        year = metadata.year,
                        loudnessMetadata = metadata.loudnessMetadata,
                    )
                    when {
                        cachedTrack == null -> added += 1
                        sameMediaColumns -> unchanged += 1
                        else -> updated += 1
                    }
                    if (cachedTrack != null) {
                        val oldKey = cachedTrack.stableKey()
                        val newKey = scannedTrack.stableKey()
                        seenPreviousKeys += oldKey
                        if (oldKey != newKey) keyReplacements[oldKey] = newKey
                    }
                    tracks += scannedTrack
                }
            }
        } catch (exception: CancellationException) {
            PulseDeckLibraryDiagnostics.scanCancelled(reason, mediaStoreRows, pulseDeckLibraryNow() - startedAt)
            throw exception
        }
        val removed = previousTracks.count { it.stableKey() !in seenPreviousKeys }
        val report = LocalLibraryScanReport(
            reason = reason,
            durationMillis = pulseDeckLibraryNow() - startedAt,
            mediaStoreRows = mediaStoreRows,
            tracks = tracks.size,
            added = added,
            updated = updated,
            removed = removed,
            unchanged = unchanged,
            retrieverOpens = retrieverOpens,
            retrieverTotalMillis = retrieverTotalMillis,
            retrieverMaxMillis = retrieverMaxMillis,
            incrementalHit = unchanged > 0 || keyReplacements.isNotEmpty(),
            threadName = Thread.currentThread().name,
        )
        PulseDeckLibraryDiagnostics.scanEnd(report, keyReplacements.size)
        return LocalLibraryScanResult(tracks, report, keyReplacements)
    }

}

private fun Track.toLocalTrackMetadata(): LocalTrackMetadata =
    LocalTrackMetadata(
        genre = genre,
        albumArtist = albumArtist,
        composer = composer,
        year = year,
        loudnessMetadata = loudnessMetadata,
    )

private fun Track.hasSameLocalIdentity(
    displayName: String?,
    folderPath: String?,
    sizeBytes: Long,
    modifiedMillis: Long,
    durationMillis: Long,
): Boolean {
    val strongMatch = this.sizeBytes == sizeBytes &&
        this.modifiedMillis == modifiedMillis &&
        this.durationMillis == durationMillis
    if (strongMatch) return true
    return localLibraryFingerprint() == localLibraryFingerprint(displayName, folderPath, sizeBytes, modifiedMillis, durationMillis)
}

private fun Track.hasSameMediaColumns(
    title: String,
    artist: String,
    albumName: String,
    uri: Uri,
    durationMillis: Long,
    mimeType: String?,
    displayName: String?,
    sizeBytes: Long,
    folderPath: String?,
    modifiedMillis: Long,
): Boolean =
    this.title == title &&
        this.artist == artist &&
        album.title == albumName &&
        this.uri?.toString() == uri.toString() &&
        this.durationMillis == durationMillis &&
        this.mimeType == mimeType &&
        this.displayName == displayName &&
        this.sizeBytes == sizeBytes &&
        this.folderPath == folderPath &&
        this.modifiedMillis == modifiedMillis

private fun Track.localLibraryFingerprint(): String? =
    localLibraryFingerprint(displayName, folderPath, sizeBytes, modifiedMillis, durationMillis)

private fun localLibraryFingerprint(
    displayName: String?,
    folderPath: String?,
    sizeBytes: Long,
    modifiedMillis: Long,
    durationMillis: Long,
): String? {
    if (displayName.isNullOrBlank() || sizeBytes <= 0L || modifiedMillis <= 0L) return null
    val folder = folderPath?.replace('\\', '/')?.trim('/')?.lowercase().orEmpty()
    return listOf(folder, displayName.trim().lowercase(), sizeBytes, modifiedMillis, durationMillis).joinToString("|")
}

private fun isStreamLibraryFolder(folderPath: String?): Boolean {
    val normalized = folderPath?.replace('\\', '/')?.trim('/') ?: return false
    return normalized == STREAM_LIBRARY_FOLDER ||
        normalized == STREAM_LIBRARY_FOLDER_WITH_MUSIC ||
        normalized == PREMIUMDECK_SOURCE_CATEGORY ||
        normalized == "$PREMIUMDECK_SOURCE_CATEGORY Sources" ||
        normalized == "Music/PulseDeck/$PREMIUMDECK_SOURCE_CATEGORY" ||
        normalized.startsWith("Music/PulseDeck/$PREMIUMDECK_SOURCE_CATEGORY/") ||
        normalized == LEGACY_YOUTUBE_SOURCE_CATEGORY ||
        normalized == "Music/PulseDeck/YouTube" ||
        normalized.startsWith("Music/PulseDeck/YouTube/")
}

private fun albumFor(title: String, artist: String, coverUri: Uri? = null, sourceUri: Uri? = null): Album {
    val palette = scannerPalettes[Math.floorMod("$title/$artist".hashCode(), scannerPalettes.size)]
    val words = title.split(" ").filter { it.isNotBlank() }
    val mark = words.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "PD" }
    return Album(title = title, artist = artist, mark = mark.take(3), tint = palette.first, alt = palette.second, coverUri = coverUri, sourceUri = sourceUri)
}

private fun albumArtUri(albumId: Long): Uri? =
    if (albumId > 0L) ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId) else null

private fun String?.cleanUnknown(fallback: String): String {
    val value = this?.trim().orEmpty()
    return if (value.isBlank() || value == "<unknown>") fallback else value
}

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun Cursor.getLongOrZero(columnName: String): Long {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else 0L
}

private val scannerPalettes = listOf(
    Color(0xFFB66C42) to Color(0xFFE2C064),
    Color(0xFF335BFF) to Color(0xFF80F1C6),
    Color(0xFFFFC83D) to Color(0xFFC62D2D),
    Color(0xFFC85B25) to Color(0xFF6D2E14),
    Color(0xFF4130A9) to Color(0xFFB94F90),
    Color(0xFF2E8974) to Color(0xFFE68A42),
)
