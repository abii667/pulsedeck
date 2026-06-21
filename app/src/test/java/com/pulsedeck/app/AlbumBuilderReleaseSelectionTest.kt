package com.pulsedeck.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumBuilderReleaseSelectionTest {
    @Test
    fun preferredReleaseUsesHydratedTracklistForSameAlbumProject() {
        val seed = AlbumDownloadRelease(
            id = "mb-rg:blue-roads",
            title = "Blue Roads",
            artist = "Nina Vale",
            trackCount = 2,
            tracks = emptyList(),
        )
        val hydrated = seed.copy(
            tracks = listOf(
                AlbumDownloadTrack(position = 1, title = "River Light"),
                AlbumDownloadTrack(position = 2, title = "Open Sky"),
            ),
        )

        assertEquals(hydrated, preferredAlbumProjectRelease(seed, hydrated))
    }

    @Test
    fun preferredReleaseKeepsUserMatchingWorkOverFreshShell() {
        val matched = AlbumDownloadRelease(
            id = "mb-rg:blue-roads",
            title = "Blue Roads",
            artist = "Nina Vale",
            tracks = listOf(
                AlbumDownloadTrack(
                    position = 1,
                    title = "River Light",
                    matchedSource = YouTubeSource(
                        id = "yt-river-light",
                        url = "https://music.youtube.com/watch?v=river",
                        kind = YouTubeSourceKind.Video,
                        title = "River Light",
                        author = "Nina Vale",
                    ),
                    matchScore = 92,
                    matchVerified = true,
                ),
            ),
        )
        val shell = matched.copy(tracks = emptyList())

        assertEquals(matched, preferredAlbumProjectRelease(matched, shell))
    }
}
