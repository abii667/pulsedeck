package com.pulsedeck.app

import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.library.LocalLibraryGroupKind
import com.pulsedeck.app.library.LocalTrackFilterKind
import com.pulsedeck.app.library.cleanupLocalTrackState
import com.pulsedeck.app.library.localFilteredTracks
import com.pulsedeck.app.library.localLibraryGroupTracks
import com.pulsedeck.app.library.localLibraryGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCategoryModelTest {
    @Test
    fun everyVisibleLibraryCategoryHasExplicitRouting() {
        val unmapped = categories.filter { libraryCategoryKindForName(it.name) == null }

        assertTrue("Unmapped library categories: ${unmapped.map { it.name }}", unmapped.isEmpty())
        assertEquals(categories.size, categories.map { it.name }.distinct().size)
    }

    @Test
    fun metadataGroupingIncludesUnknownBuckets() {
        val tracks = listOf(
            localTrack("Known", genre = "Jazz", albumArtist = "Quartet", composer = "A. Writer", year = 2024),
            localTrack("Missing"),
        )

        assertGroup(LocalLibraryGroupKind.AlbumArtists, tracks, "Unknown Album Artist")
        assertGroup(LocalLibraryGroupKind.Genres, tracks, "Unknown Genre")
        assertGroup(LocalLibraryGroupKind.Years, tracks, "Unknown Year")
        assertGroup(LocalLibraryGroupKind.Composers, tracks, "Unknown Composer")
    }

    @Test
    fun playlistAndBookmarkFiltersUsePersistedTrackKeys() {
        val alpha = localTrack("Alpha")
        val beta = localTrack("Beta")
        val gamma = localTrack("Gamma")
        val tracks = listOf(gamma, beta, alpha)

        val playlist = localFilteredTracks(
            LocalTrackFilterKind.Playlists,
            tracks,
            playlistKeys = setOf(beta.stableKey(), gamma.stableKey()),
        )
        val bookmarks = localFilteredTracks(
            LocalTrackFilterKind.Bookmarks,
            tracks,
            bookmarkedKeys = setOf(alpha.stableKey()),
        )

        assertEquals(listOf("Beta", "Gamma"), playlist.map { it.title })
        assertEquals(listOf("Alpha"), bookmarks.map { it.title })
    }

    @Test
    fun mostPlayedSortsByPlayCountAndExcludesZeroPlayTracks() {
        val alpha = localTrack("Alpha")
        val beta = localTrack("Beta")
        val gamma = localTrack("Gamma")
        val tracks = listOf(alpha, beta, gamma)

        val filtered = localFilteredTracks(
            LocalTrackFilterKind.MostPlayed,
            tracks,
            playCounts = mapOf(
                alpha.stableKey() to 3,
                beta.stableKey() to 0,
                gamma.stableKey() to 7,
            ),
        )

        assertEquals(listOf("Gamma", "Alpha"), filtered.map { it.title })
    }

    @Test
    fun cleanupRemovesDeletedTrackStateEverywhere() {
        val alpha = localTrack("Alpha")
        val beta = localTrack("Beta")
        val staleKey = "deleted-track"
        val validKeys = setOf(alpha.stableKey(), beta.stableKey())

        val cleaned = cleanupLocalTrackState(
            validTrackKeys = validKeys,
            likedKeys = setOf(alpha.stableKey(), staleKey),
            dislikedKeys = setOf(beta.stableKey(), staleKey),
            bookmarkedKeys = setOf(staleKey),
            playlistKeys = setOf(alpha.stableKey(), staleKey),
            playCounts = mapOf(alpha.stableKey() to 4, staleKey to 9),
        )

        assertEquals(setOf(alpha.stableKey()), cleaned.likedKeys)
        assertEquals(setOf(beta.stableKey()), cleaned.dislikedKeys)
        assertEquals(emptySet<String>(), cleaned.bookmarkedKeys)
        assertEquals(setOf(alpha.stableKey()), cleaned.playlistKeys)
        assertEquals(mapOf(alpha.stableKey() to 4), cleaned.playCounts)
    }

    private fun assertGroup(kind: LocalLibraryGroupKind, tracks: List<Track>, expectedTitle: String) {
        val group = localLibraryGroups(kind, tracks).firstOrNull { it.title == expectedTitle }
        assertNotNull("$expectedTitle should be visible", group)
        assertEquals(1, group?.trackCount)
        assertEquals(listOf("Missing"), localLibraryGroupTracks(kind, expectedTitle, tracks).map { it.title })
    }

    private fun localTrack(
        title: String,
        artist: String = "Artist",
        genre: String? = null,
        albumArtist: String? = null,
        composer: String? = null,
        year: Int? = null,
    ): Track =
        Track(
            title = title,
            artist = artist,
            duration = "1:00",
            album = Album(
                title = "Unit Test Album",
                artist = "Artist",
                mark = "UT",
                tint = Color(0xFF335BFF),
                alt = Color(0xFF80F1C6),
            ),
            durationMillis = 60_000L,
            genre = genre,
            albumArtist = albumArtist,
            composer = composer,
            year = year,
        )
}
