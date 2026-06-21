package com.pulsedeck.app

internal enum class AlbumProjectSaveMode { StreamOffline, LocalAlbum }

internal fun AlbumProjectSaveMode.createsPremiumDeckPlaylist(): Boolean =
    this == AlbumProjectSaveMode.StreamOffline

internal fun AlbumDownloadRelease.albumProjectSourcesForSave(
    mode: AlbumProjectSaveMode,
    liveSources: List<YouTubeSource>,
): List<YouTubeSource> =
    when (mode) {
        AlbumProjectSaveMode.StreamOffline -> matchedPremiumDeckSources().map { matched ->
            liveSources.firstOrNull { source ->
                source.id == matched.id ||
                    source.url == matched.url ||
                    source.streamDistinctKey() == matched.streamDistinctKey()
            } ?: matched
        }
        AlbumProjectSaveMode.LocalAlbum -> matchedPremiumDeckSources()
    }
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        .distinctBy { it.streamDistinctKey() }
