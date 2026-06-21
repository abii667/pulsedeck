package com.pulsedeck.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTrackMatcherTest {
    @Test
    fun progressSummaryCountsSavingSavedAndFailedMatchedSources() {
        val release = AlbumDownloadRelease(
            id = "release-1",
            title = "Blue Roads",
            artist = "Nina Vale",
            tracks = listOf(
                AlbumDownloadTrack(position = 1, title = "River Light", matchedSource = source("one")),
                AlbumDownloadTrack(position = 2, title = "Open Sky", matchedSource = source("two")),
                AlbumDownloadTrack(position = 3, title = "Morning Glass", matchedSource = source("three")),
            ),
        )
        val liveSources = listOf(
            source("one").copy(downloadState = YouTubeDownloadState.Downloading, downloadProgress = 45),
            source("two").copy(downloadState = YouTubeDownloadState.Downloaded, downloadedUri = "content://media/audio/2"),
            source("three").copy(downloadState = YouTubeDownloadState.Failed),
        )

        val summary = release.albumProjectProgressSummary(liveSources)

        assertEquals(3, summary.matched)
        assertEquals(1, summary.saving)
        assertEquals(1, summary.saved)
        assertEquals(1, summary.failed)
        assertEquals(81, summary.progress)
    }

    @Test
    fun tracksNeedingRepairDetectWeakUnverifiedAndFailedMatches() {
        val strong = source("strong").copy(status = YouTubeSourceStatus.StreamReady)
        val failed = source("failed").copy(downloadState = YouTubeDownloadState.Failed)
        val release = AlbumDownloadRelease(
            id = "release-1",
            title = "Blue Roads",
            artist = "Nina Vale",
            tracks = listOf(
                AlbumDownloadTrack(position = 1, title = "Strong", matchedSource = strong, matchScore = 88, matchVerified = true),
                AlbumDownloadTrack(position = 2, title = "Weak", matchedSource = source("weak"), matchScore = 55, matchVerified = true),
                AlbumDownloadTrack(position = 3, title = "Unverified", matchedSource = source("unverified"), matchScore = 90, matchVerified = false),
                AlbumDownloadTrack(position = 4, title = "Failed", matchedSource = failed, matchScore = 90, matchVerified = true),
            ),
        )

        val repair = release.albumTracksNeedingRepair(listOf(strong, failed))

        assertEquals(listOf("Weak", "Unverified", "Failed"), repair.map { it.title })
    }

    @Test
    fun candidateSourcesResolveLiveVersionsAndFilterDislikedVideos() {
        val matched = source("one").copy(title = "Old title")
        val candidate = source("two")
        val disliked = source("bad").copy(reaction = YouTubeReaction.Disliked)
        val liveMatched = matched.copy(title = "Live title", cachedUri = "https://cdn.test/one.m4a")
        val track = AlbumDownloadTrack(
            position = 1,
            title = "River Light",
            matchedSource = matched,
            matchCandidates = listOf(candidate, disliked),
        )

        val candidates = track.matchCandidateSources(listOf(liveMatched, candidate, disliked))

        assertEquals(listOf("one", "two"), candidates.map { it.id })
        assertEquals("Live title", candidates.first().title)
        assertTrue(candidates.none { it.reaction == YouTubeReaction.Disliked })
    }

    @Test
    fun selectedBackupCandidateUpdatesOnlyThatTrackAndRoundTripsDraftJson() {
        val primary = source("primary", "River Light")
        val backup = source("backup", "River Light live session").copy(channelTitle = "Nina Vale Live")
        val untouched = source("two", "Open Sky")
        val release = AlbumDownloadRelease(
            id = "release-1",
            title = "Blue Roads",
            artist = "Nina Vale",
            tracks = listOf(
                AlbumDownloadTrack(
                    position = 1,
                    title = "River Light",
                    matchedSource = primary,
                    matchScore = 91,
                    matchVerified = true,
                    matchCandidates = listOf(primary, backup),
                ),
                AlbumDownloadTrack(position = 2, title = "Open Sky", matchedSource = untouched, matchScore = 88, matchVerified = true),
            ),
        )

        val updated = release.withSelectedAlbumTrackMatch(release.tracks.first(), backup, listOf(primary, backup, untouched))
        val parsed = parseAlbumDownloadDrafts(albumDownloadDraftsToJson(listOf(AlbumDownloadDraft(updated))))
            .single()
            .release

        assertEquals("backup", parsed.tracks[0].matchedSource?.id)
        assertEquals("two", parsed.tracks[1].matchedSource?.id)
        assertEquals(listOf("backup", "primary"), parsed.tracks[0].matchCandidates.take(2).map { it.id })
        assertTrue(parsed.tracks[0].matchReason.contains("PremiumDeck match"))
    }

    @Test
    fun localAlbumSaveUsesArtistAlbumFolderAndDoesNotCreatePremiumDeckPlaylist() {
        assertEquals("Music/PulseDeck/Nina Vale/Blue Roads", albumProjectLocalRelativePath("Nina Vale", "Blue Roads"))
        assertFalse(AlbumProjectSaveMode.LocalAlbum.createsPremiumDeckPlaylist())
        assertTrue(AlbumProjectSaveMode.StreamOffline.createsPremiumDeckPlaylist())
    }

    @Test
    fun activeAlbumProjectTaskUsesProgressSummary() {
        val release = AlbumDownloadRelease(
            id = "release-1",
            title = "Blue Roads",
            artist = "Nina Vale",
            tracks = listOf(
                AlbumDownloadTrack(position = 1, title = "River Light", matchedSource = source("one")),
            ),
        )
        val task = AlbumProjectTaskState(
            releaseId = "release-1",
            title = "Blue Roads",
            artist = "Nina Vale",
            phase = AlbumProjectTaskPhase.OfflineSaving,
            total = 1,
            completed = 0,
        )

        val active = findActiveAlbumProjectTask(
            drafts = listOf(AlbumDownloadDraft(release)),
            tasks = mapOf("release-1" to task),
            sources = listOf(source("one").copy(downloadState = YouTubeDownloadState.Downloading, downloadProgress = 20)),
        ) ?: error("Expected active task")

        assertEquals("Blue Roads", active.release.title)
        assertEquals(1, active.summary.saving)
        assertEquals(20, active.summary.progress)
    }

    private fun source(id: String, title: String = "Track $id"): YouTubeSource =
        YouTubeSource(
            id = id,
            url = "https://www.youtube.com/watch?v=$id",
            kind = YouTubeSourceKind.Video,
            title = title,
            author = "Nina Vale",
            reviewState = YouTubeReviewState.Accepted,
        )
}
