package com.pulsedeck.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumDeckPodcastDiscoveryTest {
    @Test
    fun topPodcastDirectoryContainsFifteenRankedShows() {
        assertEquals(15, TopPremiumDeckPodcastShows.size)
        assertEquals((1..15).toList(), TopPremiumDeckPodcastShows.map { it.rank })
        assertEquals("The Joe Rogan Experience", TopPremiumDeckPodcastShows.first().title)
    }

    @Test
    fun podcastDiscoveryFiltersMarksDeduplicatesAndCapsResults() {
        val raw = listOf(
            result("one", "Deep Music Podcast Episode 12", "Channel A", "https://youtube.com/channel/a", duration = 58 * 60_000L, views = 9_000),
            result("two", "Deep Music Podcast Episode 13", "Channel A", "https://youtube.com/channel/a", duration = 61 * 60_000L, views = 12_000),
            result("three", "Artist Interview Full Episode", "Channel B", "https://youtube.com/channel/b", duration = 42 * 60_000L, views = 7_000),
            result("short", "Podcast clip", "Channel C", "https://youtube.com/channel/c", duration = 6 * 60_000L, views = 20_000),
            result("song", "Official Audio Podcast Song", "Channel D", "https://youtube.com/channel/d", duration = 32 * 60_000L, views = 30_000),
            result("four", "Guest Conversation Show", "Channel E", "https://youtube.com/channel/e", duration = 35 * 60_000L, views = 6_000),
        )

        val filtered = filterPremiumDeckPodcastResults(raw, existingSources = emptyList(), limit = 2)

        assertEquals(2, filtered.size)
        assertEquals(listOf("one", "three"), filtered.map { it.videoId })
        assertTrue(filtered.all { it.source.isPodcast })
        assertTrue(filtered.all { it.source.reviewState == YouTubeReviewState.Accepted })
    }

    @Test
    fun showEpisodeFilterUsesShowIdentityAndNewestUploads() {
        val show = TopPremiumDeckPodcastShows.first { it.id == "joe-rogan" }
        val raw = listOf(
            result("old", "Joe Rogan Experience #2200 - Guest", "PowerfulJRE", "https://youtube.com/channel/powerfuljre", duration = 130 * 60_000L, views = 20_000, uploaded = "3 weeks ago"),
            result("new", "Joe Rogan Experience #2300 - Guest", "PowerfulJRE", "https://youtube.com/channel/powerfuljre", duration = 140 * 60_000L, views = 10_000, uploaded = "2 days ago"),
            result("clip", "Joe Rogan Experience highlight clip", "PowerfulJRE", "https://youtube.com/channel/powerfuljre", duration = 18 * 60_000L, views = 90_000, uploaded = "1 day ago"),
            result("title-only", "Joe Rogan Experience #2301 - Guest", "Random Podcast Clips", "https://youtube.com/channel/random", duration = 125 * 60_000L, views = 150_000, uploaded = "1 hour ago"),
            result("other", "Crime Junkie Podcast Episode", "Crime Junkie", "https://youtube.com/channel/crime", duration = 40 * 60_000L, views = 100_000, uploaded = "1 day ago"),
        )

        val filtered = filterPremiumDeckPodcastShowEpisodes(show, raw, existingSources = emptyList(), limit = 10)

        assertEquals(listOf("new", "old"), filtered.map { it.videoId })
        assertTrue(filtered.all { it.source.isPodcast })
        assertTrue(filtered.all { it.source.albumTitleHint == show.title })
    }

    @Test
    fun snapshotRecentEpisodesSortsAcrossShowsByUploadDate() {
        val snapshot = PremiumDeckPodcastSnapshot(
            savedMillis = 1L,
            episodesByShow = mapOf(
                "one" to listOf(result("older", "Podcast Episode", "Channel A", "https://youtube.com/channel/a", duration = 30 * 60_000L, views = 1_000, uploaded = "5 days ago")),
                "two" to listOf(result("newer", "Podcast Episode", "Channel B", "https://youtube.com/channel/b", duration = 30 * 60_000L, views = 1_000, uploaded = "1 day ago")),
            ),
        )

        assertEquals(listOf("newer", "older"), snapshot.recentEpisodes(limit = 2).map { it.videoId })
    }

    @Test
    fun customPodcastShowBuildsFromChannelNameAndFiltersEpisodes() {
        val raw = listOf(
            result("lex-new", "Lex Fridman Podcast #440 - Guest", "Lex Fridman", "https://youtube.com/channel/lex", duration = 125 * 60_000L, views = 100_000, uploaded = "1 day ago"),
            result("lex-old", "Lex Fridman Podcast #430 - Guest", "Lex Fridman", "https://youtube.com/channel/lex", duration = 118 * 60_000L, views = 90_000, uploaded = "3 days ago"),
            result("clip-farm", "Lex Fridman Podcast #441 - Guest", "Random Podcast Clips", "https://youtube.com/channel/clips", duration = 90 * 60_000L, views = 200_000, uploaded = "1 hour ago"),
        )

        val show = buildCustomPremiumDeckPodcastShow("Lex Fridman", raw)
        val filtered = filterPremiumDeckPodcastShowEpisodes(show, raw, existingSources = emptyList(), limit = 10)

        assertTrue(show.custom)
        assertEquals("Lex Fridman", show.title)
        assertEquals(listOf("lex-new", "lex-old"), filtered.map { it.videoId })
        assertTrue(filtered.all { it.source.albumTitleHint == show.title })
    }

    @Test
    fun snapshotDirectoryIncludesSavedCustomShowsBeforeTopShows() {
        val customShow = buildCustomPremiumDeckPodcastShow("Lex Fridman")
        val snapshot = PremiumDeckPodcastSnapshot().withCustomShow(customShow)

        assertEquals(customShow.id, snapshot.directoryShows().first().id)
        assertEquals(customShow, findPremiumDeckPodcastShow(customShow.id, snapshot.customShows))
        assertEquals("The Joe Rogan Experience", snapshot.directoryShows()[1].title)
    }

    private fun result(
        id: String,
        title: String,
        channel: String,
        channelUrl: String,
        duration: Long,
        views: Long,
        uploaded: String = "",
    ): YouTubeSearchResult =
        YouTubeSearchResult(
            source = YouTubeSource(
                id = id,
                url = "https://www.youtube.com/watch?v=$id",
                kind = YouTubeSourceKind.Video,
                title = title,
                author = channel,
                channelTitle = channel,
                channelUrl = channelUrl,
                durationMillis = duration,
                reviewState = YouTubeReviewState.Inbox,
            ),
            durationMillis = duration,
            uploadedDate = uploaded,
            views = views,
            videoId = id,
            score = 100,
        )
}
