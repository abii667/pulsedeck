package com.pulsedeck.app.model

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.LoudnessMetadata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class Category(
    val name: String,
    val short: String,
    val tint: Color,
    val meta: String? = null,
)

internal data class Album(
    val title: String,
    val artist: String,
    val mark: String,
    val tint: Color,
    val alt: Color,
    val coverUri: Uri? = null,
    val sourceUri: Uri? = null,
    val artSourceUris: List<Uri> = sourceUri?.let { listOf(it) }.orEmpty(),
    val groupKey: String = albumGroupKey(title),
) {
    val key: String = groupKey
}

internal data class Track(
    val title: String,
    val artist: String,
    val duration: String,
    val album: Album,
    val uri: Uri? = null,
    val durationMillis: Long = 0L,
    val quality: String = "44.1 KHZ  320 KBPS  MP3",
    val mimeType: String? = null,
    val displayName: String? = null,
    val sizeBytes: Long = 0L,
    val folderPath: String? = null,
    val modifiedMillis: Long = 0L,
    val genre: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val year: Int? = null,
    val loudnessMetadata: LoudnessMetadata = LoudnessMetadata(),
)

internal fun Track.stableKey(): String =
    uri?.toString() ?: "$title|$artist|${album.key}"

internal fun albumGroupKey(title: String): String {
    val normalized = title.normalizedModelSearchText()
    return "album:${normalized.ifBlank { title.trim().lowercase(Locale.US) }}"
}

internal fun legacyAlbumKey(title: String, artist: String): String =
    "$title|$artist"

internal fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
    return "$minutes:$seconds"
}

internal fun formatCompactCount(value: Long): String =
    when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000f)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000f)
        else -> value.toString()
    }

internal fun formatModifiedDate(millis: Long): String {
    if (millis <= 0L) return "Unknown"
    return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(millis))
}

private fun String.normalizedModelSearchText(): String =
    lowercase(Locale.US)
        .replace(Regex("""[^\p{L}\p{Nd}]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")
