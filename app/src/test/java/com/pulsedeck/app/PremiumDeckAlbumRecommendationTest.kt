package com.pulsedeck.app

import com.pulsedeck.app.premiumdeck.personalization.UserPreferenceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class PremiumDeckAlbumRecommendationTest {
    @Test
    fun albumRecommendationsOnlyContainMatchedAlbumTracklist() {
        val albums = buildAlbumLikeCollections(
            localSources = listOf(
                premiumDeckSource("nina-1", "Track 01 - River Light from the album Blue Roads", "Nina Vale"),
                premiumDeckSource("nina-2", "Track 02 - Open Sky from the album Blue Roads", "Nina Vale"),
                premiumDeckSource("single", "Loose Single official audio", "Nina Vale"),
                premiumDeckSource("other-artist", "Track 03 - River Light from the album Blue Roads", "Other Artist"),
                premiumDeckSource("other-album", "Track 01 - Night Window from the album Red Roads", "Nina Vale"),
            ),
            discoverySources = emptyList(),
        )

        assertEquals(1, albums.size)
        val album = albums.single()
        assertEquals("Blue Roads", album.title)
        assertEquals(StreamCollectionKind.AlbumLike, album.kind)
        assertEquals(listOf("nina-1", "nina-2"), album.sources.map { it.id })
        assertTrue(album.sources.all { it.author == "Nina Vale" })
        assertFalse(album.sources.any { it.id == "single" || it.id == "other-artist" || it.id == "other-album" })
    }

    @Test
    fun albumRecommendationsUseTrustedAlbumMetadataHints() {
        val albums = buildAlbumLikeCollections(
            localSources = listOf(
                premiumDeckSource("nina-1", "River Light", "Nina Vale", albumTitleHint = "Blue Roads"),
                premiumDeckSource("nina-2", "Open Sky", "Nina Vale", albumTitleHint = "Blue Roads"),
                premiumDeckSource("single", "Loose Single official audio", "Nina Vale"),
                premiumDeckSource("other-artist", "River Light", "Other Artist", albumTitleHint = "Blue Roads"),
            ),
            discoverySources = emptyList(),
        )

        assertEquals(1, albums.size)
        assertEquals("Blue Roads", albums.single().title)
        assertEquals(listOf("nina-2", "nina-1"), albums.single().sources.map { it.id })
    }

    @Test
    fun albumRecommendationsHideSavedAlbumProjects() {
        val albums = buildAlbumLikeCollections(
            localSources = listOf(
                premiumDeckSource("nina-1", "River Light", "Nina Vale", albumTitleHint = "Blue Roads"),
                premiumDeckSource("nina-2", "Open Sky", "Nina Vale", albumTitleHint = "Blue Roads"),
                premiumDeckSource("milo-1", "North Road", "Milo Arc", albumTitleHint = "Quiet Maps"),
                premiumDeckSource("milo-2", "Pale City", "Milo Arc", albumTitleHint = "Quiet Maps"),
            ),
            discoverySources = emptyList(),
        )
        val project = AlbumDownloadDraft(
            release = AlbumDownloadRelease(
                id = "release-1",
                title = "Blue Roads",
                artist = "Nina Vale",
                tracks = listOf(
                    AlbumDownloadTrack(position = 1, title = "River Light", matchedSource = premiumDeckSource("nina-1", "River Light", "Nina Vale", albumTitleHint = "Blue Roads")),
                    AlbumDownloadTrack(position = 2, title = "Open Sky", matchedSource = premiumDeckSource("nina-2", "Open Sky", "Nina Vale", albumTitleHint = "Blue Roads")),
                ),
            ),
        )

        val filtered = filterAlbumRecommendationsAgainstProjects(albums, listOf(project))

        assertEquals(listOf("Quiet Maps"), filtered.map { it.title })
    }

    @Test
    fun albumRecommendationsUseStructuredTopicTitlesWithoutMixingArtists() {
        val albums = buildAlbumLikeCollections(
            localSources = listOf(
                premiumDeckSource("nina-1", "River Light \u00B7 Nina Vale \u00B7 Blue Roads", "Nina Vale - Topic"),
                premiumDeckSource("nina-2", "Open Sky \u00B7 Nina Vale \u00B7 Blue Roads", "Nina Vale - Topic"),
                premiumDeckSource("wrong-artist", "River Light \u00B7 Other Artist \u00B7 Blue Roads", "Nina Vale - Topic"),
            ),
            discoverySources = emptyList(),
        )

        assertEquals(1, albums.size)
        assertEquals("Blue Roads", albums.single().title)
        assertEquals(listOf("nina-2", "nina-1"), albums.single().sources.map { it.id })
        assertFalse(albums.single().sources.any { it.id == "wrong-artist" })
    }

    @Test
    fun followedArtistAlbumsRankAheadWithoutBreakingTracklistStrictness() {
        val albums = buildAlbumLikeCollections(
            localSources = listOf(
                premiumDeckSource("nina-1", "Track 01 - River Light from the album Blue Roads", "Nina Vale"),
                premiumDeckSource("nina-2", "Track 02 - Open Sky from the album Blue Roads", "Nina Vale"),
                premiumDeckSource("milo-1", "Track 01 - North Road from the album Quiet Maps", "Milo Arc").copy(playCount = 8),
                premiumDeckSource("milo-2", "Track 02 - Pale City from the album Quiet Maps", "Milo Arc").copy(playCount = 8),
            ),
            discoverySources = emptyList(),
        )

        val ranked = rankAlbumLikeCollections(
            albums = albums,
            followedArtists = listOf(FollowedStreamArtist(name = "Nina Vale", key = "nina vale")),
            profile = UserPreferenceProfile(artistAffinity = mapOf("nina vale" to 1.2f)),
        )

        assertEquals("Blue Roads", ranked.first().title)
        assertTrue(ranked.first().subtitle.startsWith("Followed artist"))
        assertEquals(listOf("nina-1", "nina-2"), ranked.first().sources.map { it.id })
    }

    @Test
    fun followedArtistAlbumRankingDropsWrongChannelAlbumsOutsideEthiopia() {
        val albums = buildAlbumLikeCollections(
            localSources = listOf(
                premiumDeckSource("official-1", "Track 01 - Start from the album Official Era", "Chris Brown", channelUrl = "https://www.youtube.com/channel/UCofficial"),
                premiumDeckSource("official-2", "Track 02 - Move from the album Official Era", "Chris Brown", channelUrl = "https://www.youtube.com/channel/UCofficial"),
                premiumDeckSource("fake-1", "Track 01 - Copy from the album AI Era", "Chris Brown", channelTitle = "AI Uploads", channelUrl = "https://www.youtube.com/channel/UCfake"),
                premiumDeckSource("fake-2", "Track 02 - Clone from the album AI Era", "Chris Brown", channelTitle = "AI Uploads", channelUrl = "https://www.youtube.com/channel/UCfake"),
            ),
            discoverySources = emptyList(),
        )

        val ranked = rankAlbumLikeCollections(
            albums = albums,
            followedArtists = listOf(
                FollowedStreamArtist(
                    name = "Chris Brown",
                    key = "chris brown",
                    officialName = "Chris Brown",
                    officialKey = "chris brown",
                    officialChannelUrl = "https://www.youtube.com/channel/UCofficial",
                ),
            ),
            profile = UserPreferenceProfile(artistAffinity = mapOf("chris brown" to 1.2f)),
        )

        assertEquals(listOf("Official Era"), ranked.map { it.title })
    }

    @Test
    fun followedArtistReleaseSeedsTargetCurrentReleaseSearches() {
        val artist = FollowedStreamArtist(name = "Nina Vale", key = "nina vale", followedAtMillis = 100L)
        val now = GregorianCalendar(2026, Calendar.MAY, 30).timeInMillis

        val seeds = streamNewReleaseSearchSeeds(artist, nowMillis = now)

        assertTrue(seeds.all { it.contains("Nina Vale") })
        assertTrue(seeds.any { it.contains("2026") })
        assertTrue(seeds.any { it.contains("official audio", ignoreCase = true) })
    }

    @Test
    fun followedArtistNewReleaseFilterKeepsOnlyLikelyArtistTracks() {
        val artist = FollowedStreamArtist(name = "Nina Vale", key = "nina vale", followedAtMillis = 100L)

        assertTrue(
            premiumDeckSearchResult(
                id = "release",
                title = "Silver Morning official audio",
                artist = "Nina Vale - Topic",
                score = 92,
            ).isLikelyNewReleaseFor(artist),
        )
        assertFalse(
            premiumDeckSearchResult(
                id = "reaction",
                title = "Nina Vale Silver Morning reaction",
                artist = "Other Creator",
                score = 99,
            ).isLikelyNewReleaseFor(artist),
        )
        assertFalse(
            premiumDeckSearchResult(
                id = "long-mix",
                title = "Nina Vale new release mix",
                artist = "Nina Vale",
                durationMillis = 3_600_000L,
                score = 99,
            ).isLikelyNewReleaseFor(artist),
        )
    }

    @Test
    fun followedArtistOutsideEthiopiaRequiresBoundOfficialChannel() {
        val artist = FollowedStreamArtist(
            name = "Chris Brown",
            key = "chris brown",
            officialName = "Chris Brown",
            officialKey = "chris brown",
            officialChannelUrl = "https://www.youtube.com/channel/UCofficial",
        )

        assertTrue(
            premiumDeckSearchResult(
                id = "official",
                title = "Chris Brown new song official audio",
                artist = "Chris Brown",
                score = 95,
                channelTitle = "Chris Brown",
                channelUrl = "https://www.youtube.com/channel/UCofficial",
            ).isLikelyNewReleaseFor(artist),
        )
        assertFalse(
            premiumDeckSearchResult(
                id = "ai-upload",
                title = "Chris Brown new song official audio",
                artist = "Chris Brown",
                score = 99,
                channelTitle = "AI Music Uploads",
                channelUrl = "https://www.youtube.com/channel/UCfake",
            ).isLikelyNewReleaseFor(artist),
        )
    }

    @Test
    fun ethiopianFollowedArtistsCanUseLabelChannels() {
        val artist = FollowedStreamArtist(
            name = "Ethiopian Artist",
            key = "ethiopian artist",
            officialName = "Ethiopian Artist",
            officialKey = "ethiopian artist",
            officialChannelUrl = "https://www.youtube.com/channel/UCartist",
        )

        assertTrue(
            premiumDeckSearchResult(
                id = "label-release",
                title = "Ethiopian Artist new song official audio",
                artist = "Ethiopian Artist",
                score = 95,
                channelTitle = "Record Label",
                channelUrl = "https://www.youtube.com/channel/UClabel",
            ).isLikelyNewReleaseFor(artist),
        )
    }

    @Test
    fun followedArtistNewReleaseCollectionUsesOnlyFollowedArtistResults() {
        val artist = FollowedStreamArtist(name = "Nina Vale", key = "nina vale", followedAtMillis = 100L)
        val collection = buildFollowedArtistNewReleaseCollection(
            artists = listOf(artist),
            results = listOf(
                premiumDeckSearchResult("release", "Silver Morning official audio", "Nina Vale - Topic", score = 94),
                premiumDeckSearchResult("old", "Older Road official audio", "Nina Vale - Topic", score = 94, uploadedDate = "2 months ago"),
                premiumDeckSearchResult("other", "Loose Single official audio", "Other Artist", score = 96),
            ),
            savedSources = emptyList(),
        ) ?: error("Expected followed-artist release collection")

        assertEquals("From Artists You Follow", collection.title)
        assertEquals(StreamCollectionKind.Playlist, collection.kind)
        assertEquals(listOf("release"), collection.sources.map { it.id })
        assertTrue(collection.sources.all { it.author.contains("Nina Vale") })
    }

    @Test
    fun premiumDeckTodayPrependsFollowedArtistLaneBeforeExploreShelves() {
        val artist = FollowedStreamArtist(name = "Nina Vale", key = "nina vale", followedAtMillis = 100L)
        val followedLane = buildFollowedArtistNewReleaseCollection(
            artists = listOf(artist),
            results = listOf(premiumDeckSearchResult("release", "Silver Morning official audio", "Nina Vale - Topic", score = 94)),
            savedSources = emptyList(),
        ) ?: error("Expected followed-artist lane")
        val explore = premiumDeckSearchResult(
            id = "explore",
            title = "Chart Song",
            artist = "Chart Artist",
            score = 90,
            albumTitleHint = "premiumdeck-youtube-explore:new-trending|New & Trending Songs|Fresh official music|Explore|",
        )

        val collections = buildPremiumDeckDiscoveryCollections(
            discoveryResults = listOf(explore),
            newReleaseCollection = followedLane,
        )

        assertEquals("From Artists You Follow", collections.first().title)
        assertEquals("New & Trending Songs", collections.drop(1).first().title)
    }

    @Test
    fun premiumDeckTodayKeepsYouTubeExploreAheadOfChartsAndNeverUsesGenericFallbacks() {
        val chartHot = premiumDeckSearchResult(
            id = "chart-hot",
            title = "Chart Fire",
            artist = "Chart Artist",
            score = 80,
            albumTitleHint = premiumDeckChartStorageTag("global-hot-right-now"),
        )
        val explore = premiumDeckSearchResult(
            id = "explore",
            title = "Explore Pick",
            artist = "Explore Artist",
            score = 70,
            albumTitleHint = "premiumdeck-youtube-explore:new-trending|New & Trending Songs|Fresh official music|Explore|",
        )
        val generic = premiumDeckSearchResult(
            id = "generic",
            title = "Generic Discovery",
            artist = "Generic Artist",
            score = 999,
        )

        val collections = buildPremiumDeckDiscoveryCollections(
            discoveryResults = listOf(generic, explore, chartHot),
            newReleaseCollection = null,
        )

        val hot = collections.firstOrNull { it.id == "global-hot-right-now" } ?: error("Expected Hot Right Now")
        val exploreIndex = collections.indexOfFirst { it.id.startsWith("youtube-explore-") }
        val hotIndex = collections.indexOfFirst { it.id == "global-hot-right-now" }

        assertEquals("New & Trending Songs", collections.first().title)
        assertEquals(listOf("chart-hot"), hot.sources.map { it.id })
        assertTrue(hotIndex >= 0)
        assertTrue(exploreIndex >= 0)
        assertTrue(exploreIndex < hotIndex)
        assertFalse(collections.any { collection -> collection.sources.any { it.id == "generic" } })
    }

    @Test
    fun followedArtistReleaseNotificationsKeepUnreadStateAndTapTargets() {
        val artist = FollowedStreamArtist(name = "Nina Vale", key = "nina vale", followedAtMillis = 100L)
        val release = premiumDeckSearchResult("release", "Silver Morning official audio", "Nina Vale - Topic", score = 94)
        val readId = streamReleaseNotificationId(artist.key, release)

        val notifications = buildStreamReleaseNotifications(
            artists = listOf(artist),
            results = listOf(
                release,
                premiumDeckSearchResult("other", "Loose Single official audio", "Other Artist", score = 96),
            ),
            savedSources = emptyList(),
            readNotificationIds = setOf(readId),
        )

        assertEquals(listOf(readId), notifications.map { it.id })
        assertEquals("Silver Morning official audio", notifications.single().title)
        assertEquals("Nina Vale", notifications.single().artistName)
        assertEquals("release", notifications.single().source.id)
        assertTrue(notifications.single().read)
    }

    @Test
    fun newReleaseDateGateRequiresLastMonth() {
        assertTrue(
            premiumDeckSearchResult(
                id = "recent",
                title = "Silver Morning official audio",
                artist = "Nina Vale - Topic",
                score = 94,
                uploadedDate = "3 weeks ago",
            ).wasUploadedWithinLastMonth(),
        )
        assertFalse(
            premiumDeckSearchResult(
                id = "old",
                title = "Silver Morning official audio",
                artist = "Nina Vale - Topic",
                score = 94,
                uploadedDate = "2 months ago",
            ).wasUploadedWithinLastMonth(),
        )
        assertFalse(
            premiumDeckSearchResult(
                id = "unknown",
                title = "Silver Morning official audio",
                artist = "Nina Vale - Topic",
                score = 94,
                uploadedDate = "",
            ).wasUploadedWithinLastMonth(),
        )
    }

    @Test
    fun artistFollowOptionsIncludeFollowedAndSuggestedArtists() {
        val options = buildStreamArtistFollowOptions(
            sources = listOf(
                premiumDeckSource("nina", "Silver Morning official audio", "Nina Vale - Topic")
                    .copy(playCount = 3, reaction = YouTubeReaction.Liked),
                premiumDeckSource("generic", "PremiumDeck Mix", "PremiumDeck"),
            ),
            discoveryResults = listOf(
                premiumDeckSearchResult("milo", "North Road official audio", "Milo Arc", score = 92),
            ),
            playHistory = listOf(
                StreamPlayHistoryItem(
                    sourceId = "history",
                    url = "https://www.youtube.com/watch?v=history",
                    title = "History Track",
                    author = "Nina Vale",
                    thumbnailUrl = null,
                    durationMillis = 180_000L,
                    playedAtMillis = 2_000L,
                ),
            ),
            followedArtists = listOf(FollowedStreamArtist(name = "Manual Artist", key = "manual artist", followedAtMillis = 1_000L)),
        )

        val names = options.map { it.name }
        assertEquals("Manual Artist", names.first())
        assertTrue(names.any { it == "Nina Vale" })
        assertTrue(names.any { it == "Milo Arc" })
        assertFalse(names.any { it == "PremiumDeck" })
    }

    private fun premiumDeckSource(
        id: String,
        title: String,
        artist: String,
        channelTitle: String = artist,
        channelUrl: String = "",
        albumTitleHint: String = "",
    ): YouTubeSource =
        YouTubeSource(
            id = id,
            url = "https://www.youtube.com/watch?v=$id",
            kind = YouTubeSourceKind.Video,
            title = title,
            author = artist,
            channelTitle = channelTitle,
            channelUrl = channelUrl,
            albumTitleHint = albumTitleHint,
            durationMillis = 180_000L,
            reviewState = YouTubeReviewState.Accepted,
        )

    private fun premiumDeckSearchResult(
        id: String,
        title: String,
        artist: String,
        durationMillis: Long = 180_000L,
        score: Int,
        uploadedDate: String = "2 weeks ago",
        channelTitle: String = artist,
        channelUrl: String = "",
        albumTitleHint: String = "",
    ): YouTubeSearchResult =
        YouTubeSearchResult(
            source = premiumDeckSource(id, title, artist, channelTitle = channelTitle, channelUrl = channelUrl, albumTitleHint = albumTitleHint).copy(durationMillis = durationMillis),
            durationMillis = durationMillis,
            uploadedDate = uploadedDate,
            videoId = id,
            score = score,
            matchReason = "official audio",
        )
}
