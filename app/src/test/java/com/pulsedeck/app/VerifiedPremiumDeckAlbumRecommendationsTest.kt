package com.pulsedeck.app

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedPremiumDeckAlbumRecommendationsTest {
    @Test
    fun podcastResultIsRejected() {
        assertFalse(isValidRecommendedAlbumCandidate(album("podcast", "Music Podcast Weekly")))
    }

    @Test
    fun generatedMixIsRejected() {
        assertFalse(isValidRecommendedAlbumCandidate(album("mix", "Generated Mix for Nina Vale")))
    }

    @Test
    fun videoCollectionIsRejected() {
        assertFalse(isValidRecommendedAlbumCandidate(album("videos", "Official Videos Collection")))
    }

    @Test
    fun videoPlaylistIsRejected() {
        assertFalse(isValidRecommendedAlbumCandidate(album("playlist", "Tour Videos Playlist")))
    }

    @Test
    fun podcastEpisodeIsRejected() {
        assertFalse(isValidRecommendedAlbumCandidate(album("episode", "Studio Interview Podcast Episode")))
    }

    @Test
    fun singleIsRejected() {
        assertFalse(isValidRecommendedAlbumCandidate(album("single", "River Light", type = VerifiedReleaseType.Single)))
    }

    @Test
    fun epIsRejectedForThisPhase() {
        assertFalse(isValidRecommendedAlbumCandidate(album("ep", "River Light EP", type = VerifiedReleaseType.EP)))
    }

    @Test
    fun unknownReleaseIsRejected() {
        assertFalse(isValidRecommendedAlbumCandidate(album("unknown", "River Light", type = VerifiedReleaseType.Unknown)))
    }

    @Test
    fun stableProviderIdIsRequired() {
        assertFalse(isValidRecommendedAlbumCandidate(album("", "Blue Roads")))
    }

    @Test
    fun artistIdentityIsRequired() {
        assertFalse(isValidRecommendedAlbumCandidate(album("a", "Blue Roads", artistId = "")))
    }

    @Test
    fun releaseMetadataIsRequired() {
        assertFalse(isValidRecommendedAlbumCandidate(album("a", "Blue Roads", year = null, trackCount = null)))
    }

    @Test
    fun listenBrainzPopularityAloneDoesNotVerifyAlbum() {
        assertFalse(
            isValidRecommendedAlbumCandidate(
                album("lb", "Blue Roads", provider = AlbumMetadataProvider.ListenBrainz),
            ),
        )
    }

    @Test
    fun verifiedAlbumIsAccepted() {
        assertTrue(isValidRecommendedAlbumCandidate(album("mb-rg:album", "Blue Roads")))
    }

    @Test
    fun onlyFiveAlbumsAreSelected() {
        val selected = selectVerifiedAlbumRecommendations((1..8).map { album("a$it", "Album $it", "Artist $it") }, context())

        assertEquals(5, selected.size)
    }

    @Test
    fun maximumOneAlbumPerArtist() {
        val selected = selectVerifiedAlbumRecommendations(
            listOf(
                album("a1", "First", "Nina Vale"),
                album("a2", "Second", "Nina Vale"),
                album("b1", "Third", "Milo Arc"),
                album("c1", "Fourth", "Ora Hill"),
            ),
            context(affinity = mapOf("nina vale" to 1.0, "milo arc" to 0.8, "ora hill" to 0.6)),
        )

        assertEquals(selected.size, selected.map { it.artistName.normalizedSearchText() }.toSet().size)
    }

    @Test
    fun deluxeAndReissueVariantsDeduplicate() {
        val selected = selectVerifiedAlbumRecommendations(
            listOf(
                album("standard", "Blue Roads", "Nina Vale", popularity = 0.4, trackCount = 10),
                album("deluxe", "Blue Roads (Deluxe Edition)", "Nina Vale", popularity = 0.8, trackCount = 14),
            ),
            context(affinity = mapOf("nina vale" to 1.0)),
        )

        assertEquals(1, selected.size)
    }

    @Test
    fun fewerThanFiveValidCandidatesProducesFewerCards() {
        val selected = selectVerifiedAlbumRecommendations(
            listOf(
                album("a", "Blue Roads", "Nina Vale"),
                album("podcast", "Music Podcast Weekly", "Pod Host"),
                album("single", "River Light", "Milo Arc", type = VerifiedReleaseType.Single),
            ),
            context(affinity = mapOf("nina vale" to 1.0)),
        )

        assertEquals(1, selected.size)
        assertEquals("Blue Roads", selected.single().title)
    }

    @Test
    fun invalidCandidatesAreNeverUsedAsFillers() {
        val selected = selectVerifiedAlbumRecommendations(
            (1..3).map { album("valid$it", "Album $it", "Artist $it") } +
                (1..5).map { album("invalid$it", "Generated Mix $it", "Mixer $it") },
            context(),
        )

        assertEquals(3, selected.size)
        assertFalse(selected.any { it.title.contains("Generated Mix") })
    }

    @Test
    fun recentlyShownAlbumIsExcluded() {
        val selected = selectVerifiedAlbumRecommendations(
            listOf(album("shown", "Blue Roads"), album("fresh", "Quiet Maps", "Milo Arc")),
            context(recentlyShown = setOf("shown")),
        )

        assertFalse(selected.any { it.stableAlbumId == "shown" })
    }

    @Test
    fun recommendationReasonsArePopulated() {
        val selected = selectVerifiedAlbumRecommendations(
            listOf(
                album("a", "Blue Roads", "Nina Vale"),
                album("b", "Quiet Maps", "Milo Arc", popularity = 0.8),
                album("c", "Open Sea", "Ora Hill", year = currentTestYear()),
            ),
            context(affinity = mapOf("nina vale" to 1.0)),
        )

        assertTrue(selected.isNotEmpty())
        assertTrue(selected.all { it.recommendationReason.name.isNotBlank() })
    }

    @Test
    fun refreshDoesNotRunBefore72Hours() {
        val now = 10_000L
        val snapshot = RecommendedAlbumsSnapshot(listOf(album("a")), now, now + RECOMMENDED_ALBUM_TTL_MS, "profile", "test")

        assertFalse(snapshot.shouldRefreshRecommendedAlbums(context(now = now + 1_000L), sectionVisible = true))
    }

    @Test
    fun hiddenSectionDoesNotRefresh() {
        assertFalse(null.shouldRefreshRecommendedAlbums(context(), sectionVisible = false))
    }

    @Test
    fun freshCacheCausesZeroProviderCalls() = runBlocking {
        var calls = 0
        val snapshot = RecommendedAlbumsSnapshot(listOf(album("cached")), 1L, 9_999_999L, "profile", "cached")
        if (snapshot.shouldRefreshRecommendedAlbums(context(now = 2L), sectionVisible = true)) {
            refreshRecommendedAlbumsSnapshot(
                context(now = 2L),
                snapshot,
                providers = listOf(countingProvider {
                    calls++
                    emptyList()
                }),
            )
        }

        assertEquals(0, calls)
    }

    @Test
    fun expiredCacheCausesOneBoundedRefresh() = runBlocking {
        var calls = 0
        val prior = RecommendedAlbumsSnapshot(listOf(album("cached")), 1L, 2L, "profile", "cached")
        val refreshed = refreshRecommendedAlbumsSnapshot(
            context(now = 3L),
            prior,
            providers = listOf(
                countingProvider {
                    calls++
                    (1..5).map { album("fresh$it", "Fresh $it", "Artist $it") }
                },
            ),
        )

        assertEquals(1, calls)
        assertEquals("Fresh 1", refreshed.albums.first().title)
    }

    @Test
    fun underfilledRefreshPreservesPriorValidSnapshot() = runBlocking {
        val prior = RecommendedAlbumsSnapshot((1..5).map { album("cached$it", "Cached $it", "Artist $it") }, 1L, 2L, "profile", "cached")
        val refreshed = refreshRecommendedAlbumsSnapshot(
            context(now = 3L),
            prior,
            providers = listOf(countingProvider { listOf(album("fresh", "Fresh One", "New Artist")) }),
        )

        assertSame(prior, refreshed)
    }

    @Test
    fun concurrentRefreshesDeduplicate() = runBlocking {
        var calls = 0
        val coordinator = RecommendedAlbumRefreshCoordinator()
        val one = async {
            coordinator.refresh(this@runBlocking) {
                calls++
                delay(40L)
                RecommendedAlbumsSnapshot(listOf(album("one")), 1L, 2L, "profile", "test")
            }
        }
        val two = async {
            coordinator.refresh(this@runBlocking) {
                calls++
                delay(40L)
                RecommendedAlbumsSnapshot(listOf(album("two")), 1L, 2L, "profile", "test")
            }
        }

        awaitAll(one, two)

        assertEquals(1, calls)
    }

    @Test
    fun cachedSnapshotDisplaysImmediately() {
        val snapshot = RecommendedAlbumsSnapshot(listOf(album("a")), 1L, 2L, "profile", "test")

        assertEquals(listOf("Blue Roads"), snapshot.albums.map { it.title })
    }

    @Test
    fun failedRefreshPreservesPriorSnapshot() = runBlocking {
        val prior = RecommendedAlbumsSnapshot(listOf(album("cached")), 1L, 2L, "profile", "cached")
        val refreshed = refreshRecommendedAlbumsSnapshot(
            context(now = 4L),
            prior,
            providers = listOf(
                object : RecommendedAlbumProvider {
                    override suspend fun loadCandidates(context: AlbumRecommendationContext): List<VerifiedAlbumCandidate> =
                        error("provider failed")
                },
            ),
        )

        assertSame(prior, refreshed)
    }

    @Test
    fun dataSaverBlocksRefresh() {
        assertFalse(
            context(policy = StreamingDataPolicy(effectiveDataSaver = true, network = wifiNetwork()))
                .allowsAlbumRecommendationProviderRequests(),
        )
    }

    @Test
    fun cellularBlocksRefreshUnlessExplicitlyAllowed() {
        val cellular = StreamingDataPolicy(network = NetworkPolicySnapshot(hasActiveNetwork = true, networkType = PulseNetworkType.Cellular, isMetered = true))

        assertFalse(context(policy = cellular).allowsAlbumRecommendationProviderRequests())
        assertTrue(context(policy = cellular, cellularAllowed = true).allowsAlbumRecommendationProviderRequests())
    }

    @Test
    fun globalOfflineModeBlocksRefresh() {
        assertFalse(context(policy = StreamingDataPolicy(network = NetworkPolicySnapshot(hasActiveNetwork = false))).allowsAlbumRecommendationProviderRequests())
    }

    @Test
    fun blockedNetworkPolicyCausesZeroProviderCalls() = runBlocking {
        var calls = 0
        val prior = RecommendedAlbumsSnapshot(listOf(album("cached")), 1L, 2L, "profile", "cached")
        val blocked = context(policy = StreamingDataPolicy(effectiveDataSaver = true, network = wifiNetwork()))

        val refreshed = refreshRecommendedAlbumsSnapshot(
            blocked,
            prior,
            providers = listOf(countingProvider {
                calls++
                emptyList()
            }),
        )

        assertSame(prior, refreshed)
        assertEquals(0, calls)
    }

    @Test
    fun offlineDeckBlocksRefresh() {
        assertFalse(context(offline = true).allowsAlbumRecommendationProviderRequests())
    }

    @Test
    fun betaLockBlocksRefresh() {
        assertFalse(context(betaUnlocked = false).allowsAlbumRecommendationProviderRequests())
    }

    @Test
    fun noRawListeningHistoryIsSentExternally() {
        val recommendationContext = buildAlbumRecommendationContext(
            sources = listOf(source("nina-source", "Private Song", "Nina Vale")),
            playHistory = listOf(
                StreamPlayHistoryItem(
                    sourceId = "secret-id",
                    url = "content://private/local/song.mp3",
                    title = "Private Song",
                    author = "Nina Vale",
                    thumbnailUrl = null,
                    durationMillis = 180_000L,
                    playedAtMillis = 10L,
                ),
            ),
            followedArtists = emptyList(),
            albumDrafts = emptyList(),
            personalizationProfile = com.pulsedeck.app.premiumdeck.personalization.UserPreferenceProfile(),
            streamPolicy = StreamingDataPolicy(network = wifiNetwork()),
            offlineDeckActive = false,
            betaUnlocked = true,
        )

        assertTrue(recommendationContext.seedArtistNames.contains("Nina Vale"))
        assertFalse(recommendationContext.seedArtistNames.any { it.contains("Private Song") || it.contains("content://") || it.contains("secret-id") })
    }

    @Test
    fun albumTapCreatesAlbumBuilderSeed() {
        val seed = album("mb-rg:a", "Blue Roads", "Nina Vale", year = 2026, trackCount = 10).toAlbumBuilderSeed()

        assertEquals("mb-rg:a", seed.id)
        assertEquals("MusicBrainz release-group a", seed.label)
        assertEquals("Blue Roads", seed.title)
        assertEquals("Nina Vale", seed.artist)
        assertEquals(10, seed.trackCount)
    }

    @Test
    fun musicBrainzReleaseGroupTracklistPreservesRecommendationIdentity() {
        val fallback = album("mb-rg:rg-one", "Blue Roads", "Nina Vale", year = 2026, trackCount = 2).toAlbumBuilderSeed()
        val releases = parseMusicBrainzReleaseGroupAlbumBuilderReleases(
            org.json.JSONObject(
                """
                {
                  "releases": [
                    {
                      "id": "release-one",
                      "title": "Blue Roads",
                      "date": "2026-04-10",
                      "country": "XW",
                      "artist-credit": [{ "name": "Nina Vale", "artist": { "id": "artist-nina" } }],
                      "media": [
                        {
                          "format": "Digital Media",
                          "track-count": 2,
                          "tracks": [
                            { "position": 1, "title": "River Light", "length": 181000, "recording": { "id": "rec-one" } },
                            { "position": 2, "title": "Open Sky", "length": 203000, "recording": { "id": "rec-two" } }
                          ]
                        }
                      ],
                      "track-count": 2
                    }
                  ]
                }
                """.trimIndent(),
            ),
            releaseGroupId = "rg-one",
            fallback = fallback,
        )

        val release = releases.single()

        assertEquals("mb-rg:rg-one", release.id)
        assertEquals("Blue Roads", release.title)
        assertEquals("Nina Vale", release.artist)
        assertEquals(listOf("River Light", "Open Sky"), release.tracks.map { it.title })
        assertEquals(listOf(1, 2), release.tracks.map { it.position })
        assertTrue(release.label.contains("MusicBrainz release-group rg-one"))
        assertTrue(release.label.contains("MusicBrainz release release-one"))
        assertTrue(release.tracks.none { it.downloadUrl.isNotBlank() || it.matchedSource != null })
    }

    @Test
    fun appleTracklistFallbackPreservesRecommendationIdentity() {
        val fallback = album("mb-rg:rg-one", "Blue Roads", "Nina Vale", year = 2026, trackCount = 2).toAlbumBuilderSeed()
        val apple = AlbumDownloadRelease(
            id = "apple:100",
            title = "Blue Roads",
            artist = "Nina Vale",
            date = "2026-04-10",
            format = "Album",
            trackCount = 2,
            tracks = listOf(
                AlbumDownloadTrack(position = 1, title = "River Light", durationMillis = 181_000L, recordingId = "1001"),
                AlbumDownloadTrack(position = 2, title = "Open Sky", durationMillis = 203_000L, recordingId = "1002"),
            ),
            coverUrl = "https://example.invalid/blue-roads.jpg",
            source = "Apple/iTunes",
            score = 96,
        )

        val release = apple.toAlbumBuilderTracklistFallback(fallback, "Apple/iTunes") ?: error("Expected fallback release")

        assertEquals("mb-rg:rg-one", release.id)
        assertEquals("Blue Roads", release.title)
        assertEquals("Nina Vale", release.artist)
        assertEquals("Apple/iTunes fallback", release.source)
        assertEquals(listOf("River Light", "Open Sky"), release.tracks.map { it.title })
        assertEquals(listOf(1, 2), release.tracks.map { it.position })
        assertTrue(release.label.contains("MusicBrainz release-group rg-one"))
        assertTrue(release.label.contains("Apple/iTunes album 100"))
        assertTrue(release.tracks.none { it.downloadUrl.isNotBlank() || it.matchedSource != null })
    }

    @Test
    fun albumBrowsingDoesNotResolveStreams() {
        val seed = album("a", "Blue Roads").toAlbumBuilderSeed()

        assertTrue(seed.tracks.isEmpty())
        assertTrue(seed.tracks.none { it.matchedSource != null || it.downloadUrl.isNotBlank() })
    }

    @Test
    fun staleInvalidCachedSnapshotIsFiltered() {
        val snapshot = parseRecommendedAlbumsSnapshot(
            org.json.JSONObject()
                .put("generatedAtEpochMs", 1L)
                .put("refreshAfterEpochMs", 2L)
                .put("profileHash", "profile")
                .put("providerSummary", "legacy")
                .put(
                    "albums",
                    org.json.JSONArray()
                        .put(album("valid", "Blue Roads").toJson())
                        .put(album("legacy", "Popular Thing", provider = AlbumMetadataProvider.ListenBrainz).toJson()),
                ),
        )

        assertEquals(listOf("Blue Roads"), snapshot.albums.map { it.title })
    }

    @Test
    fun generatedMixesRemainAvailableInSeparateSection() {
        val generated = buildStreamMixes(
            localSources = listOf(
                source("one", "River Light", "Nina Vale").copy(playCount = 3),
                source("two", "Open Sky", "Nina Vale").copy(playCount = 2),
            ),
            discoverySources = emptyList(),
            playlists = emptyList(),
        )

        assertTrue(generated.isNotEmpty())
        assertTrue(generated.all { it.kind == StreamCollectionKind.Mix || it.kind == StreamCollectionKind.Artist })
    }

    @Test
    fun noPrivateUrlPathOrTokenAppearsInSnapshotModel() {
        val snapshot = RecommendedAlbumsSnapshot(
            listOf(album("a", artwork = "https://coverartarchive.org/release-group/a/front-500")),
            generatedAtEpochMs = 1L,
            refreshAfterEpochMs = 2L,
            profileHash = "profile",
            providerSummary = "MusicBrainz:1",
        )
        val serialized = snapshot.toJson().toString()

        assertFalse(serialized.contains("content://"))
        assertFalse(serialized.contains("/sdcard", ignoreCase = true))
        assertFalse(serialized.contains("token=", ignoreCase = true))
        assertFalse(serialized.contains("PRIVATE KEY", ignoreCase = true))
    }

    private fun context(
        now: Long = 1_800_000_000_000L,
        affinity: Map<String, Double> = emptyMap(),
        recentlyShown: Set<String> = emptySet(),
        policy: StreamingDataPolicy = StreamingDataPolicy(network = wifiNetwork()),
        offline: Boolean = false,
        betaUnlocked: Boolean = true,
        cellularAllowed: Boolean = false,
    ): AlbumRecommendationContext =
        AlbumRecommendationContext(
            frequentlyPlayedArtists = affinity.keys.toList(),
            artistAffinity = affinity,
            recentlyShownAlbumIds = recentlyShown,
            nowEpochMs = now,
            networkPolicy = policy,
            offlineDeckActive = offline,
            betaUnlocked = betaUnlocked,
            explicitCellularRefreshAllowed = cellularAllowed,
        )

    private fun album(
        id: String = "album",
        title: String = "Blue Roads",
        artist: String = "Nina Vale",
        artistId: String = "artist-${artist.normalizedSearchText()}",
        type: VerifiedReleaseType = VerifiedReleaseType.Album,
        year: Int? = 2024,
        trackCount: Int? = 9,
        popularity: Double? = 0.5,
        artwork: String? = "https://coverartarchive.org/release-group/$id/front-500",
        provider: AlbumMetadataProvider = AlbumMetadataProvider.MusicBrainz,
    ): VerifiedAlbumCandidate =
        VerifiedAlbumCandidate(
            stableAlbumId = id,
            provider = provider,
            title = title,
            artistId = artistId,
            artistName = artist,
            releaseType = type,
            releaseDate = year?.toString(),
            releaseYear = year,
            trackCount = trackCount,
            artworkUrl = artwork,
            musicBrainzReleaseGroupId = id.removePrefix("mb-rg:").takeIf { provider == AlbumMetadataProvider.MusicBrainz && it.isNotBlank() },
            appleMusicAlbumId = id.removePrefix("apple:").takeIf { provider == AlbumMetadataProvider.AppleMusicCatalog && it.isNotBlank() },
            popularityScore = popularity,
            recommendationReason = AlbumRecommendationReason.BecauseYouListened,
        )

    private fun countingProvider(block: () -> List<VerifiedAlbumCandidate> = { emptyList() }): RecommendedAlbumProvider =
        object : RecommendedAlbumProvider {
            override suspend fun loadCandidates(context: AlbumRecommendationContext): List<VerifiedAlbumCandidate> = block()
        }

    private fun source(id: String, title: String, artist: String): YouTubeSource =
        YouTubeSource(
            id = id,
            url = "https://www.youtube.com/watch?v=$id",
            kind = YouTubeSourceKind.Video,
            title = title,
            author = artist,
            durationMillis = 180_000L,
            reviewState = YouTubeReviewState.Accepted,
        )

    private fun wifiNetwork(): NetworkPolicySnapshot =
        NetworkPolicySnapshot(hasActiveNetwork = true, networkType = PulseNetworkType.Wifi)

    private fun currentTestYear(): Int = 2026
}
