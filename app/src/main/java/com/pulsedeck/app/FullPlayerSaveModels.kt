package com.pulsedeck.app

internal enum class FullPlayerSaveSourceKind {
    LocalFile,
    PremiumDeck,
    PulseRadio,
}

internal data class FullPlayerSavePlaylistOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val saved: Boolean,
)

internal data class FullPlayerSaveSheetState(
    val sourceKind: FullPlayerSaveSourceKind,
    val title: String,
    val subtitle: String,
    val likedAvailable: Boolean = false,
    val liked: Boolean = false,
    val localPlaylistAvailable: Boolean = false,
    val localPlaylistSaved: Boolean = false,
    val radioFavoriteAvailable: Boolean = false,
    val radioFavoriteSaved: Boolean = false,
    val premiumPlaylists: List<FullPlayerSavePlaylistOption> = emptyList(),
    val canCreatePremiumPlaylist: Boolean = false,
) {
    val savedAnywhere: Boolean
        get() = liked ||
            localPlaylistSaved ||
            radioFavoriteSaved ||
            premiumPlaylists.any { it.saved }
}

internal fun YouTubeSource.sanitizedForNewFullPlayerSave(): YouTubeSource =
    copy(
        cachedUri = null,
        cachedMillis = 0L,
        downloadedUri = null,
        downloadState = YouTubeDownloadState.None,
        downloadProgress = 0,
        playbackPositionMillis = 0L,
        reviewState = YouTubeReviewState.Accepted,
    )
