package com.pulsedeck.app.library

import com.pulsedeck.app.Track
import com.pulsedeck.app.stableKey
import java.util.Locale

internal fun normalizedFolderPath(track: Track): String =
    track.folderPath
        ?.replace('\\', '/')
        ?.trim('/')
        ?.ifBlank { null }
        ?: "Music"

private fun unknownGroupLabel(kind: LocalLibraryGroupKind): String =
    when (kind) {
        LocalLibraryGroupKind.Folders -> "Unknown Folder"
        LocalLibraryGroupKind.AlbumArtists -> "Unknown Album Artist"
        LocalLibraryGroupKind.Genres -> "Unknown Genre"
        LocalLibraryGroupKind.Years -> "Unknown Year"
        LocalLibraryGroupKind.Composers -> "Unknown Composer"
    }

private fun groupValueForTrack(kind: LocalLibraryGroupKind, track: Track): String =
    when (kind) {
        LocalLibraryGroupKind.Folders -> normalizedFolderPath(track)
        LocalLibraryGroupKind.AlbumArtists -> track.albumArtist?.trim().orEmpty()
        LocalLibraryGroupKind.Genres -> track.genre?.trim().orEmpty()
        LocalLibraryGroupKind.Years -> track.year?.takeIf { it > 0 }?.toString().orEmpty()
        LocalLibraryGroupKind.Composers -> track.composer?.trim().orEmpty()
    }.ifBlank { unknownGroupLabel(kind) }

private fun groupSubtitleFor(kind: LocalLibraryGroupKind, title: String): String =
    when (kind) {
        LocalLibraryGroupKind.Folders -> title
        LocalLibraryGroupKind.AlbumArtists -> "Album artist"
        LocalLibraryGroupKind.Genres -> "Genre"
        LocalLibraryGroupKind.Years -> "Release year"
        LocalLibraryGroupKind.Composers -> "Composer"
    }

internal fun localLibraryGroups(kind: LocalLibraryGroupKind, tracks: List<Track>): List<LocalLibraryGroup> =
    tracks
        .groupBy { groupValueForTrack(kind, it) }
        .map { (title, groupTracks) ->
            LocalLibraryGroup(
                key = title,
                title = title,
                subtitle = groupSubtitleFor(kind, title),
                trackCount = groupTracks.size,
                durationMillis = groupTracks.sumOf { it.durationMillis },
            )
        }
        .sortedWith(
            when (kind) {
                LocalLibraryGroupKind.Years -> compareByDescending<LocalLibraryGroup> { it.title.toIntOrNull() ?: Int.MIN_VALUE }
                    .thenBy { it.title.lowercase(Locale.US) }
                else -> compareBy<LocalLibraryGroup> { it.title.lowercase(Locale.US) }
            },
        )

internal fun localLibraryGroupCount(kind: LocalLibraryGroupKind, tracks: List<Track>): Int =
    tracks
        .asSequence()
        .map { groupValueForTrack(kind, it) }
        .toSet()
        .size

internal fun localLibraryGroupTracks(kind: LocalLibraryGroupKind, groupKey: String, tracks: List<Track>): List<Track> =
    tracks
        .filter { groupValueForTrack(kind, it) == groupKey }
        .sortedBy { it.title.lowercase(Locale.US) }

internal fun localFilteredTracks(
    kind: LocalTrackFilterKind,
    tracks: List<Track>,
    playlistKeys: Set<String> = emptySet(),
    bookmarkedKeys: Set<String> = emptySet(),
    playCounts: Map<String, Int> = emptyMap(),
): List<Track> =
    when (kind) {
        LocalTrackFilterKind.Playlists -> tracks.filter { it.stableKey() in playlistKeys }
            .sortedBy { it.title.lowercase(Locale.US) }
        LocalTrackFilterKind.Bookmarks -> tracks.filter { it.stableKey() in bookmarkedKeys }
            .sortedBy { it.title.lowercase(Locale.US) }
        LocalTrackFilterKind.MostPlayed -> tracks.filter { (playCounts[it.stableKey()] ?: 0) > 0 }
            .sortedWith(compareByDescending<Track> { playCounts[it.stableKey()] ?: 0 }.thenBy { it.title.lowercase(Locale.US) })
    }

internal fun localFilteredTrackCount(
    kind: LocalTrackFilterKind,
    tracks: List<Track>,
    playlistKeys: Set<String> = emptySet(),
    bookmarkedKeys: Set<String> = emptySet(),
    playCounts: Map<String, Int> = emptyMap(),
): Int =
    when (kind) {
        LocalTrackFilterKind.Playlists -> tracks.count { it.stableKey() in playlistKeys }
        LocalTrackFilterKind.Bookmarks -> tracks.count { it.stableKey() in bookmarkedKeys }
        LocalTrackFilterKind.MostPlayed -> tracks.count { (playCounts[it.stableKey()] ?: 0) > 0 }
    }

internal fun cleanupLocalTrackState(
    validTrackKeys: Set<String>,
    likedKeys: Set<String>,
    dislikedKeys: Set<String>,
    bookmarkedKeys: Set<String>,
    playlistKeys: Set<String>,
    playCounts: Map<String, Int>,
): LocalTrackStateCleanup =
    LocalTrackStateCleanup(
        likedKeys = likedKeys intersect validTrackKeys,
        dislikedKeys = dislikedKeys intersect validTrackKeys,
        bookmarkedKeys = bookmarkedKeys intersect validTrackKeys,
        playlistKeys = playlistKeys intersect validTrackKeys,
        playCounts = playCounts.filterKeys { it in validTrackKeys },
    )

internal fun parentFolderPath(path: String): String? {
    val clean = path.trim('/')
    if (clean.isBlank()) return null
    return clean.substringBeforeLast('/', missingDelimiterValue = "")
}

internal fun tracksUnderFolder(tracks: List<Track>, path: String): List<Track> {
    val clean = path.trim('/')
    if (clean.isBlank()) return tracks
    return tracks.filter {
        val folder = normalizedFolderPath(it)
        folder == clean || folder.startsWith("$clean/")
    }
}

internal fun childFoldersFor(tracks: List<Track>, path: String): List<FolderSummary> {
    val clean = path.trim('/')
    val children = mutableMapOf<String, MutableList<Track>>()
    tracks.forEach { track ->
        val folder = normalizedFolderPath(track)
        val remainder = when {
            clean.isBlank() -> folder
            folder == clean -> ""
            folder.startsWith("$clean/") -> folder.removePrefix("$clean/")
            else -> ""
        }
        val next = remainder.substringBefore('/').takeIf { it.isNotBlank() } ?: return@forEach
        val childPath = if (clean.isBlank()) next else "$clean/$next"
        children.getOrPut(childPath) { mutableListOf() } += track
    }
    return children.map { (childPath, childTracks) ->
        FolderSummary(
            path = childPath,
            name = childPath.substringAfterLast('/'),
            trackCount = childTracks.size,
            durationMillis = childTracks.sumOf { it.durationMillis },
        )
    }.sortedBy { it.name.lowercase() }
}
