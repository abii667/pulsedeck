package com.pulsedeck.app.library

import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.Album
import com.pulsedeck.app.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLibraryCacheTest {
    @Test
    fun cachedDeviceTracksRoundTripPreservesLibraryMetadata() {
        val track = Track(
            title = "Night Drive",
            artist = "Pulse Artist",
            duration = "3:45",
            album = Album(
                title = "City Lights",
                artist = "Pulse Artist",
                mark = "CL",
                tint = Color(0xFF335BFF),
                alt = Color(0xFF80F1C6),
            ),
            durationMillis = 225_000L,
            quality = "FLAC",
            mimeType = "audio/flac",
            displayName = "night_drive.flac",
            sizeBytes = 42_000_000L,
            folderPath = "Music/Pulse",
            modifiedMillis = 1_700_000_000_000L,
            genre = "Synthwave",
            albumArtist = "Pulse Artist",
            composer = "A. Composer",
            year = 2025,
        )

        val restored = parseCachedDeviceTracks(cachedDeviceTracksToJson(listOf(track)))

        assertEquals(1, restored.size)
        assertEquals(track.title, restored.first().title)
        assertEquals(track.album.title, restored.first().album.title)
        assertEquals(track.album.groupKey, restored.first().album.groupKey)
        assertEquals(track.mimeType, restored.first().mimeType)
        assertEquals(track.folderPath, restored.first().folderPath)
        assertEquals(track.year, restored.first().year)
    }

    @Test
    fun libraryStateAppliesTrackStateCleanupTogether() {
        val state = PulseDeckLibraryState(
            localLibraryLoaded = true,
            likedTrackKeys = setOf("stale"),
            dislikedTrackKeys = setOf("stale"),
            bookmarkedTrackKeys = setOf("stale"),
            playlistTrackKeys = setOf("stale"),
            trackPlayCounts = mapOf("stale" to 3),
        )

        state.applyTrackStateCleanup(
            LocalTrackStateCleanup(
                likedKeys = setOf("valid-liked"),
                dislikedKeys = emptySet(),
                bookmarkedKeys = setOf("valid-bookmark"),
                playlistKeys = setOf("valid-playlist"),
                playCounts = mapOf("valid-played" to 4),
            ),
        )

        assertEquals(setOf("valid-liked"), state.likedTrackKeys)
        assertEquals(emptySet<String>(), state.dislikedTrackKeys)
        assertEquals(setOf("valid-bookmark"), state.bookmarkedTrackKeys)
        assertEquals(setOf("valid-playlist"), state.playlistTrackKeys)
        assertEquals(mapOf("valid-played" to 4), state.trackPlayCounts)
    }
}
