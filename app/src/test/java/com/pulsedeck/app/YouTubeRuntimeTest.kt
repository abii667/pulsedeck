package com.pulsedeck.app

import org.json.JSONObject
import com.pulsedeck.app.settings.model.OnlineFeatureSettings
import com.pulsedeck.app.settings.model.StreamPreviewNetworkPolicy
import com.pulsedeck.app.settings.model.StreamingQualityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeRuntimeTest {
    @Test
    fun detectionNormalizesCommonYouTubeInputs() {
        val shortLink = detectYouTubeSource("youtu.be/abc123?si=share") ?: error("Expected video")
        assertEquals(YouTubeSourceKind.Video, shortLink.kind)
        assertEquals("abc123", shortLink.sourceId)
        assertEquals("https://youtu.be/abc123?si=share", shortLink.normalizedUrl)

        val shorts = detectYouTubeSource("www.youtube.com/shorts/short-42") ?: error("Expected shorts video")
        assertEquals(YouTubeSourceKind.Video, shorts.kind)
        assertEquals("short-42", shorts.sourceId)

        val playlist = detectYouTubeSource("youtube.com/watch?v=video-1&list=PL123") ?: error("Expected playlist")
        assertEquals(YouTubeSourceKind.Playlist, playlist.kind)
        assertEquals("PL123", playlist.sourceId)

        val handle = detectYouTubeSource("https://www.youtube.com/channel/UC123") ?: error("Expected channel")
        assertEquals(YouTubeSourceKind.Channel, handle.kind)
        assertEquals("UC123", handle.sourceId)
    }

    @Test
    fun detectionRejectsNonYouTubeUrls() {
        assertNull(detectYouTubeSource("https://example.com/watch?v=abc123"))
        assertNull(detectYouTubeSource("not a url"))
    }

    @Test
    fun networkPolicyDefaultsDoNotDownshiftCellularQuality() {
        val policy = NetworkPolicyController.policyForSnapshot(
            snapshot = NetworkPolicySnapshot(networkType = PulseNetworkType.Cellular, isMetered = true),
            settings = OnlineFeatureSettings(),
        )

        assertFalse(policy.effectiveDataSaver)
        assertEquals(StreamingQualityPolicy.High, policy.quality)
        assertEquals(null, policy.maxAudioBitrateKbps)
        assertTrue(policy.allowMuxedFallback)
        assertTrue(policy.allowPreviewPreparation)
    }

    @Test
    fun networkPolicyAppliesDataSaverControlsWhenEnabled() {
        val policy = NetworkPolicyController.policyForSnapshot(
            snapshot = NetworkPolicySnapshot(networkType = PulseNetworkType.Wifi, androidDataSaverEnabled = true),
            settings = OnlineFeatureSettings(
                dataSaverStreamingQuality = StreamingQualityPolicy.LowData,
                streamPreviewNetworkPolicy = StreamPreviewNetworkPolicy.WifiAndUnmetered,
                allowMuxedStreamFallbackInDataSaver = false,
            ),
        )

        assertTrue(policy.effectiveDataSaver)
        assertEquals(StreamingQualityPolicy.LowData, policy.quality)
        assertEquals(96, policy.maxAudioBitrateKbps)
        assertFalse(policy.allowMuxedFallback)
        assertFalse(policy.allowPreviewPreparation)
    }

    @Test
    fun thumbnailAndSearchUrlNormalizationPreserveUsefulUrls() {
        assertEquals("https://www.youtube.com/watch?v=abc", normalizeYouTubeSearchUrl("/watch?v=abc"))
        assertEquals("https://www.youtube.com/watch?v=abc", normalizeYouTubeSearchUrl("watch?v=abc"))
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", normalizeYouTubeThumbnailUrl("/vi/abc/hqdefault.jpg?x=1"))
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg", normalizeYouTubeThumbnailUrl("//i.ytimg.com/vi/abc/maxresdefault.jpg"))
    }

    @Test
    fun pipedAudioSelectionRespectsQualityPreference() {
        val streams = listOf(
            PipedAudioStream(url = "https://cdn.test/64.webm", mimeType = "audio/webm", quality = "64 kbps", format = "webm", bitrate = 64_000),
            PipedAudioStream(url = "https://cdn.test/128.webm", mimeType = "audio/webm", quality = "128 kbps", format = "webm", bitrate = 128_000),
            PipedAudioStream(url = "https://cdn.test/256.webm", mimeType = "audio/webm", quality = "256 kbps", format = "webm", bitrate = 256_000),
        )

        assertEquals("https://cdn.test/256.webm", selectPipedAudioStream(streams, YouTubeQuality.High)?.url)
        assertEquals("https://cdn.test/128.webm", selectPipedAudioStream(streams, YouTubeQuality.Standard)?.url)
    }

    @Test
    fun pipedMuxedSelectionAvoidsDashAndPrefersMp4() {
        val streams = listOf(
            PipedAudioStream(url = "https://cdn.test/dash.mpd", mimeType = "application/dash+xml", quality = "720p", format = "dash", bitrate = 900_000, audioOnly = false),
            PipedAudioStream(url = "https://cdn.test/video.m3u8", mimeType = "application/x-mpegurl", quality = "360p", format = "hls", bitrate = 400_000, audioOnly = false),
            PipedAudioStream(url = "https://cdn.test/video.mp4", mimeType = "video/mp4", quality = "480p", format = "mp4", bitrate = 500_000, audioOnly = false),
        )

        assertEquals("https://cdn.test/video.mp4", selectPipedMuxedStream(streams, YouTubeQuality.High)?.url)
    }

    @Test
    fun streamPolicyCapsPipedAudioSelection() {
        val streams = listOf(
            PipedAudioStream(url = "https://cdn.test/64.webm", mimeType = "audio/webm", quality = "64 kbps", format = "webm", bitrate = 64_000),
            PipedAudioStream(url = "https://cdn.test/128.webm", mimeType = "audio/webm", quality = "128 kbps", format = "webm", bitrate = 128_000),
            PipedAudioStream(url = "https://cdn.test/256.webm", mimeType = "audio/webm", quality = "256 kbps", format = "webm", bitrate = 256_000),
        )
        val dataSaverPolicy = StreamingDataPolicy(
            effectiveDataSaver = true,
            quality = StreamingQualityPolicy.DataSaver,
            maxAudioBitrateKbps = 64,
            allowMuxedFallback = false,
            policyLabel = "data_saver",
        )

        assertEquals("https://cdn.test/64.webm", selectPipedAudioStream(streams, YouTubeQuality.High, dataSaverPolicy)?.url)
    }

    @Test
    fun streamPolicyBlocksMuxedFallbackWhenDisabled() {
        val streams = listOf(
            PipedAudioStream(url = "https://cdn.test/video.mp4", mimeType = "video/mp4", quality = "480p", format = "mp4", bitrate = 500_000, audioOnly = false),
        )
        val noMuxedPolicy = StreamingDataPolicy(allowMuxedFallback = false)

        assertNull(selectPipedMuxedStream(streams, YouTubeQuality.High, noMuxedPolicy))
    }

    @Test
    fun cappedStreamPolicyBypassesCachedStreamUrl() {
        val source = testSource().copy(
            cachedUri = "https://cdn.test/playback?expire=4102444800",
            cachedMillis = 1_700_000_000_000L,
        )
        val cappedPolicy = StreamingDataPolicy(
            quality = StreamingQualityPolicy.Balanced,
            maxAudioBitrateKbps = 160,
        )

        assertEquals(
            "https://cdn.test/playback?expire=4102444800",
            source.freshCachedStreamUrl(nowMillis = 1_700_000_001_000L),
        )
        assertNull(source.freshCachedStreamUrl(nowMillis = 1_700_000_001_000L, policy = cappedPolicy))
        assertEquals(
            StreamCacheDecision.PolicyBypass,
            source.cachedStreamUrlCheck(nowMillis = 1_700_000_001_000L, policy = cappedPolicy).decision,
        )
    }

    @Test
    fun cachedStreamUrlCheckExplainsExpiryAndStaleness() {
        val fresh = testSource().copy(
            cachedUri = "https://cdn.test/playback?expire=4102444800",
            cachedMillis = 1_700_000_000_000L,
        )
        assertEquals(
            StreamCacheDecision.Hit,
            fresh.cachedStreamUrlCheck(nowMillis = 1_700_000_001_000L).decision,
        )

        val stale = fresh.copy(cachedMillis = 1_699_990_000_000L)
        assertEquals(
            StreamCacheDecision.Stale,
            stale.cachedStreamUrlCheck(nowMillis = 1_700_000_001_000L).decision,
        )

        val expiring = fresh.copy(cachedUri = "https://cdn.test/playback?expire=1700000100")
        assertEquals(
            StreamCacheDecision.Expired,
            expiring.cachedStreamUrlCheck(nowMillis = 1_700_000_000_000L).decision,
        )

        val invalid = fresh.copy(cachedUri = "content://media/audio/1")
        assertEquals(
            StreamCacheDecision.Invalid,
            invalid.cachedStreamUrlCheck(nowMillis = 1_700_000_001_000L).decision,
        )
    }

    @Test
    fun chapterParsingMapsEndTimesAndRejectsLateFirstChapter() {
        val chapters = parseChaptersFromDescription(
            """
            0:00 Intro
            1:05 First song
            2:10 Outro
            """.trimIndent(),
            durationMillis = 180_000L,
        )

        assertEquals(3, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(0L, chapters[0].startMillis)
        assertEquals(65_000L, chapters[0].endMillis)
        assertEquals(180_000L, chapters.last().endMillis)

        assertTrue(parseChaptersFromDescription("0:15 Too late\n1:00 Second", 120_000L).isEmpty())
    }

    @Test
    fun youtubeSourceJsonRoundTripPreservesDownloadAndSkipMetadata() {
        val source = testSource().copy(
            status = YouTubeSourceStatus.Downloaded,
            downloadedUri = "content://media/audio/1",
            downloadState = YouTubeDownloadState.Downloaded,
            downloadProgress = 100,
            chapters = listOf(YouTubeChapter("Verse", 10_000L, 35_000L)),
            sponsorSegments = listOf(SponsorBlockSegment("sponsor", 40_000L, 55_000L)),
            skipSegmentsEnabled = false,
            trimSilenceOnDownload = true,
        )

        val parsed = source.toYouTubeSourceJson().toYouTubeSourceOrNull() ?: error("Expected parsed source")

        assertEquals(YouTubeSourceStatus.Downloaded, parsed.status)
        assertEquals(YouTubeDownloadState.Downloaded, parsed.downloadState)
        assertEquals("content://media/audio/1", parsed.downloadedUri)
        assertEquals("Verse", parsed.chapters.single().title)
        assertEquals("sponsor", parsed.sponsorSegments.single().category)
        assertFalse(parsed.skipSegmentsEnabled)
        assertTrue(parsed.trimSilenceOnDownload)
    }

    @Test
    fun cachedSearchResultsRoundTripPreservesSourceAndReason() {
        val result = YouTubeSearchResult(
            source = testSource().copy(thumbnailUrl = "https://img.test/cover.jpg", durationMillis = 181_000L),
            durationMillis = 181_000L,
            uploadedDate = "2026-05-30",
            views = 1234L,
            videoId = "abc123",
            score = 240,
            cachedMillis = 42L,
            matchReason = "Official audio",
        )

        val parsed = parseCachedYouTubeSearchResults(listOf(result).toYouTubeSearchJsonArray())

        assertEquals(1, parsed.size)
        assertEquals("River Light", parsed.single().source.title)
        assertEquals("Official audio", parsed.single().matchReason)
        assertEquals("https://img.test/cover.jpg", parsed.single().source.thumbnailUrl)
        assertEquals(181_000L, parsed.single().source.durationMillis)
    }

    @Test
    fun streamHistoryRestoresVideoSourceAndFallsBackWhenDetectionIsUnavailable() {
        val restored = StreamPlayHistoryItem(
            sourceId = "",
            url = "https://www.youtube.com/watch?v=abc123",
            title = "River Light",
            author = "Nina Vale",
            thumbnailUrl = null,
            durationMillis = 181_000L,
            playedAtMillis = 99L,
        ).toYouTubeSource() ?: error("Expected restored source")

        assertEquals("video-abc123", restored.id)
        assertEquals(YouTubeSourceKind.Video, restored.kind)
        assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", restored.thumbnailUrl)
        assertEquals(99L, restored.lastPlayedMillis)

        val fallback = StreamPlayHistoryItem(
            sourceId = "",
            url = "https://music.example/stream",
            title = "River Light",
            author = "Nina Vale",
            thumbnailUrl = "https://img.test/cover.jpg",
            durationMillis = 181_000L,
            playedAtMillis = 100L,
        ).toYouTubeSource() ?: error("Expected fallback source")

        assertTrue(fallback.id.startsWith("history-"))
        assertEquals("https://img.test/cover.jpg", fallback.thumbnailUrl)
    }

    @Test
    fun youtubeSearchScoringPenalizesNonMusicResults() {
        val musicScore = scoreYouTubeMusicResult(
            query = "Nina Vale River Light",
            source = testSource().copy(title = "Nina Vale - River Light Official Audio", author = "Nina Vale"),
            durationMillis = 181_000L,
            item = JSONObject(),
        )
        val tutorialScore = scoreYouTubeMusicResult(
            query = "Nina Vale River Light",
            source = testSource().copy(title = "River Light guitar tutorial reaction shorts", author = "Lesson Channel"),
            durationMillis = 45_000L,
            item = JSONObject().put("isShort", true),
        )

        assertTrue(musicScore > tutorialScore)
        assertTrue(musicMatchReason(musicScore, 181_000L, testSource().copy(title = "Official Audio")).contains("Official audio"))
    }

    private fun testSource(): YouTubeSource =
        YouTubeSource(
            id = "video-abc123",
            url = "https://www.youtube.com/watch?v=abc123",
            kind = YouTubeSourceKind.Video,
            title = "River Light",
            author = "Nina Vale",
            reviewState = YouTubeReviewState.Accepted,
        )
}
