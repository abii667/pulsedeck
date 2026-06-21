package com.pulsedeck.app.library

internal enum class LocalLibraryGroupKind {
    Folders,
    AlbumArtists,
    Genres,
    Years,
    Composers,
}

internal enum class LocalTrackFilterKind {
    Playlists,
    Bookmarks,
    MostPlayed,
}

internal data class LocalLibraryGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val trackCount: Int,
    val durationMillis: Long,
)

internal data class LocalTrackStateCleanup(
    val likedKeys: Set<String>,
    val dislikedKeys: Set<String>,
    val bookmarkedKeys: Set<String>,
    val playlistKeys: Set<String>,
    val playCounts: Map<String, Int>,
)

internal data class FolderSummary(
    val path: String,
    val name: String,
    val trackCount: Int,
    val durationMillis: Long,
)
