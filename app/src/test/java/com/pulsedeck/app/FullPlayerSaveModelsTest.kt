package com.pulsedeck.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullPlayerSaveModelsTest {
    @Test
    fun savedAnywhereReflectsEverySupportedLocation() {
        assertFalse(
            FullPlayerSaveSheetState(
                sourceKind = FullPlayerSaveSourceKind.LocalFile,
                title = "A",
                subtitle = "B",
            ).savedAnywhere,
        )

        assertTrue(
            FullPlayerSaveSheetState(
                sourceKind = FullPlayerSaveSourceKind.LocalFile,
                title = "A",
                subtitle = "B",
                liked = true,
            ).savedAnywhere,
        )
        assertTrue(
            FullPlayerSaveSheetState(
                sourceKind = FullPlayerSaveSourceKind.LocalFile,
                title = "A",
                subtitle = "B",
                localPlaylistSaved = true,
            ).savedAnywhere,
        )
        assertTrue(
            FullPlayerSaveSheetState(
                sourceKind = FullPlayerSaveSourceKind.PulseRadio,
                title = "A",
                subtitle = "B",
                radioFavoriteSaved = true,
            ).savedAnywhere,
        )
        assertTrue(
            FullPlayerSaveSheetState(
                sourceKind = FullPlayerSaveSourceKind.PremiumDeck,
                title = "A",
                subtitle = "B",
                premiumPlaylists = listOf(FullPlayerSavePlaylistOption("p1", "List", "Playlist", saved = true)),
            ).savedAnywhere,
        )
    }

    @Test
    fun newFullPlayerSaveSourceClearsTransientPlaybackUris() {
        val source = YouTubeSource(
            id = "video-123",
            url = "https://music.youtube.com/watch?v=123",
            kind = YouTubeSourceKind.Video,
            title = "Track",
            author = "Artist",
            cachedUri = "https://signed.example.invalid/playback",
            cachedMillis = 12345L,
            downloadedUri = "file:///private/download.mp3",
            downloadState = YouTubeDownloadState.Downloaded,
            downloadProgress = 100,
            playbackPositionMillis = 9000L,
            reviewState = YouTubeReviewState.Inbox,
        )

        val sanitized = source.sanitizedForNewFullPlayerSave()

        assertNull(sanitized.cachedUri)
        assertEquals(0L, sanitized.cachedMillis)
        assertNull(sanitized.downloadedUri)
        assertEquals(YouTubeDownloadState.None, sanitized.downloadState)
        assertEquals(0, sanitized.downloadProgress)
        assertEquals(0L, sanitized.playbackPositionMillis)
        assertEquals(YouTubeReviewState.Accepted, sanitized.reviewState)
    }
}
