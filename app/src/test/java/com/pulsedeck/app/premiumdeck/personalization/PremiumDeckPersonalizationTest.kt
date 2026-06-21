package com.pulsedeck.app.premiumdeck.personalization

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumDeckPersonalizationTest {
    @Test
    fun positiveEventsIncreaseAffinityAndNegativeEventsDecreaseIt() = runBlocking {
        val store = InMemoryUserPreferenceStore()
        val recommender = HeuristicFallbackRecommender(store)
        val loved = behavior(BehaviorEventType.TrackCompleted, id = "song-a", artist = "Nina Vale", genre = "Soul")
        val skipped = behavior(BehaviorEventType.TrackSkipped, id = "song-b", artist = "Static Gray", genre = "Noise", skipSeconds = 12)

        recommender.onBehaviorEvent(loved)
        val afterPositive = store.loadProfile()
        recommender.onBehaviorEvent(skipped)
        val afterNegative = store.loadProfile()

        assertTrue(afterPositive.artistAffinity.getValue("nina vale") > 0f)
        assertTrue(afterNegative.artistAffinity.getValue("static gray") < 0f)
        assertTrue(afterNegative.itemAffinity.getValue("song-b") < 0f)
    }

    @Test
    fun recentEventsOutweighOlderDecayedEvents() {
        val oldMillis = 1_000L
        val nowMillis = oldMillis + 42L * 86_400_000L
        val oldProfile = UserPreferenceUpdater.update(
            profile = UserPreferenceProfile(),
            event = behavior(BehaviorEventType.TrackCompleted, id = "old", artist = "Old Artist", genre = "Jazz", at = oldMillis),
            nowMillis = oldMillis,
        )
        val nextProfile = UserPreferenceUpdater.update(
            profile = oldProfile,
            event = behavior(BehaviorEventType.TrackCompleted, id = "new", artist = "New Artist", genre = "Pop", at = nowMillis),
            nowMillis = nowMillis,
        )

        assertTrue(nextProfile.artistAffinity.getValue("new artist") > nextProfile.artistAffinity.getValue("old artist"))
    }

    @Test
    fun replayBufferKeepsOlderPositiveTasteVisible() = runBlocking {
        val store = InMemoryUserPreferenceStore()
        val recommender = HeuristicFallbackRecommender(store)

        recommender.onBehaviorEvent(behavior(BehaviorEventType.LikeFavorite, id = "classic", artist = "Classic Artist", genre = "Jazz"))
        repeat(18) { index ->
            recommender.onBehaviorEvent(behavior(BehaviorEventType.TrackCompleted, id = "new-$index", artist = "New Artist", genre = "Pop"))
        }

        val profile = store.loadProfile()
        assertTrue(profile.artistAffinity.getValue("classic artist") > 0f)
        assertTrue(store.loadReplayBuffer().representativePositiveEvents().any { it.itemId == "classic" })
    }

    @Test
    fun artistFollowEventFeedsTheRecommendationProfile() = runBlocking {
        val store = InMemoryUserPreferenceStore()
        val recommender = HeuristicFallbackRecommender(store)

        recommender.onBehaviorEvent(behavior(BehaviorEventType.ArtistFollowed, id = "artist:nina", artist = "Nina Vale", genre = "Artist Follow"))

        val profile = store.loadProfile()
        assertTrue(profile.artistAffinity.getValue("nina vale") > 0.45f)
        assertTrue(store.loadReplayBuffer().representativePositiveEvents().any { it.type == BehaviorEventType.ArtistFollowed })
    }

    @Test
    fun fallbackFiltersDislikesRespectsOfflineAndCapsResults() = runBlocking {
        val store = InMemoryUserPreferenceStore()
        val recommender = HeuristicFallbackRecommender(store)
        recommender.onBehaviorEvent(behavior(BehaviorEventType.LikeFavorite, id = "local-good", artist = "Nina Vale", genre = "Soul"))
        val profile = store.loadProfile()
        val candidates = listOf(
            candidate("local-good", source = CandidateSource.LocalLibrary, liked = true, local = true),
            candidate("premium-offline", source = CandidateSource.PremiumDeck, local = true, quality = 0.95f),
            candidate("premium-online", source = CandidateSource.PremiumDeck, local = false, quality = 0.95f),
            candidate("bad", source = CandidateSource.LocalLibrary, disliked = true, local = true),
        )

        val results = recommender.recommendSongs(
            RecommendationContext(candidates = candidates, profile = profile, offlineMode = true),
            limit = 2,
        )

        assertEquals(2, results.size)
        assertFalse(results.any { it.candidate.id == "bad" })
        assertFalse(results.any { it.candidate.id == "premium-online" })
        assertTrue(results.first().reasons.isNotEmpty())
    }

    @Test
    fun premiumDeckCandidatesAreRerankedLocally() = runBlocking {
        val store = InMemoryUserPreferenceStore()
        val recommender = HeuristicFallbackRecommender(store)
        recommender.onBehaviorEvent(behavior(BehaviorEventType.LikeFavorite, id = "seed", artist = "Nina Vale", genre = "Soul"))
        val profile = store.loadProfile()
        val results = recommender.recommendSongs(
            RecommendationContext(
                candidates = listOf(
                    candidate("unrelated", artist = "Other", genre = "Metal", source = CandidateSource.PremiumDeck),
                    candidate("related", artist = "Nina Vale", genre = "Soul", source = CandidateSource.PremiumDeck),
                ),
                profile = profile,
            ),
            limit = 2,
        )

        assertEquals("related", results.first().candidate.id)
    }

    @Test
    fun mixGeneratorCreatesExpectedMixesAndAvoidsArtistFlooding() {
        val candidates = (1..8).map { index ->
            candidate("nina-$index", artist = "Nina Vale", genre = "Chill Soul", source = CandidateSource.PremiumDeck, quality = 0.9f)
        } + (1..8).map { index ->
            candidate("orbit-$index", artist = "Orbit House", genre = "Ambient", source = CandidateSource.LocalLibrary, local = true)
        }
        val mixes = MixGenerator().generate(
            RecommendationContext(candidates = candidates, profile = UserPreferenceProfile(positiveItemIds = setOf("nina-1"))),
            limit = 6,
        )

        assertTrue(mixes.any { it.title == "Morning Mix" })
        assertTrue(mixes.any { it.title == "Chill Mix" })
        assertTrue(mixes.any { it.title == "Discovery Mix" })
        assertTrue(mixes.any { it.title == "PremiumDeck High Quality Picks" })
        assertFalse(mixes.any { it.title == "Offline Local Favorites" })
        assertTrue(mixes.all { mix -> mix.tracks.windowed(3).none { window -> window.map { it.artist }.distinct().size == 1 } })
    }

    @Test
    fun resetPersonalizationClearsProfileAndReplay() = runBlocking {
        val store = InMemoryUserPreferenceStore()
        val recommender = HeuristicFallbackRecommender(store)
        recommender.onBehaviorEvent(behavior(BehaviorEventType.LikeFavorite))

        recommender.resetPersonalization()

        assertTrue(store.loadProfile().artistAffinity.isEmpty())
        assertTrue(store.loadReplayBuffer().events.isEmpty())
        assertTrue(store.recentEvents().isEmpty())
    }

    @Test
    fun tinyRecManifestFlagsBootstrapModelAsPipelineValidationOnly() {
        val manifest = TinyRecModelManifest.fromJson(
            """
            {
              "model_name": "PremiumDeck TinyRec v1",
              "asset_name": "premiumdeck_tinyrec_v1.tflite",
              "created_at_ms": 1780147335552,
              "bootstrap_sample": true,
              "candidate_count": 256,
              "event_count": 1920,
              "training_example_count": 7680,
              "size_bytes": 83808,
              "warning": "Bootstrap models validate the pipeline only; train with real PremiumDeck events before production use."
            }
            """.trimIndent(),
        )

        assertEquals(PremiumDeckTinyRecConfig.ModelName, manifest.modelName)
        assertEquals(PremiumDeckTinyRecConfig.ModelAssetName, manifest.assetName)
        assertTrue(manifest.bootstrapSample)
        assertFalse(manifest.productionReady)
        assertEquals("bootstrap validation model", manifest.readinessLabel)
    }

    @Test
    fun tinyRecManifestRecognizesProductionCandidate() {
        val manifest = TinyRecModelManifest.fromJson(
            """
            {
              "model_name": "PremiumDeck TinyRec v1",
              "asset_name": "premiumdeck_tinyrec_v1.tflite",
              "created_at_ms": 1780147335552,
              "bootstrap_sample": false,
              "candidate_count": 12000,
              "event_count": 250000,
              "training_example_count": 900000,
              "size_bytes": 1048576,
              "warning": ""
            }
            """.trimIndent(),
        )

        assertFalse(manifest.bootstrapSample)
        assertTrue(manifest.productionReady)
        assertEquals("production candidate", manifest.readinessLabel)
        assertEquals(900000, manifest.trainingExampleCount)
    }

    @Test
    fun tinyRecStatusWarnsWhenOnlyBootstrapModelIsAvailable() {
        val status = describeTinyRecModelStatus(
            health = TinyRecModelHealth.MODEL_UNAVAILABLE,
            manifest = TinyRecModelManifest(
                modelName = PremiumDeckTinyRecConfig.ModelName,
                assetName = PremiumDeckTinyRecConfig.ModelAssetName,
                createdAtMillis = 1780147335552,
                bootstrapSample = true,
                candidateCount = 256,
                eventCount = 1920,
                trainingExampleCount = 7680,
                sizeBytes = 83808,
                warning = "Bootstrap models validate the pipeline only.",
            ),
            modelAvailable = true,
        )

        assertEquals("PremiumDeck Model Available", status.title)
        assertTrue(status.body.contains("bootstrap validation model"))
        assertTrue(status.body.contains("Smart mode stays on heuristic fallback"))
    }

    @Test
    fun tinyRecStatusMakesFallbackExplicitWhenNoModelExists() {
        val status = describeTinyRecModelStatus(
            health = TinyRecModelHealth.USING_HEURISTIC_FALLBACK,
            manifest = null,
            modelAvailable = false,
        )

        assertEquals("PremiumDeck Heuristic Fallback", status.title)
        assertTrue(status.body.contains("No ${PremiumDeckTinyRecConfig.ModelAssetName} model is configured"))
        assertTrue(status.body.contains("fast local fallback"))
    }

    @Test
    fun smartModeIgnoresTinyRecScoresWhenModelIsNotProductionReady() = runBlocking {
        val store = InMemoryUserPreferenceStore()
        HeuristicFallbackRecommender(store).onBehaviorEvent(
            behavior(BehaviorEventType.LikeFavorite, id = "seed", artist = "Nina Vale", genre = "Soul"),
        )
        val repository = personalizationRepository(
            store = store,
            candidates = listOf(
                candidate("unrelated", artist = "Other", genre = "Metal", source = CandidateSource.PremiumDeck),
                candidate("related", artist = "Nina Vale", genre = "Soul", source = CandidateSource.PremiumDeck),
            ),
            scorer = FakeTinyRecScorer(productionReady = false) { candidate ->
                if (candidate.id == "unrelated") 8f else -8f
            },
        )

        val results = repository.recommendations(
            settings = PersonalizationSettings(useDownloadedModelWhenAvailable = true),
            limit = 2,
        )

        assertEquals("related", results.first().candidate.id)
    }

    @Test
    fun smartModeUsesTinyRecScoresOnlyForProductionReadyModels() = runBlocking {
        val store = InMemoryUserPreferenceStore()
        HeuristicFallbackRecommender(store).onBehaviorEvent(
            behavior(BehaviorEventType.LikeFavorite, id = "seed", artist = "Nina Vale", genre = "Soul"),
        )
        val repository = personalizationRepository(
            store = store,
            candidates = listOf(
                candidate("unrelated", artist = "Other", genre = "Metal", source = CandidateSource.PremiumDeck),
                candidate("related", artist = "Nina Vale", genre = "Soul", source = CandidateSource.PremiumDeck),
            ),
            scorer = FakeTinyRecScorer(productionReady = true) { candidate ->
                if (candidate.id == "unrelated") 8f else -8f
            },
        )

        val results = repository.recommendations(
            settings = PersonalizationSettings(useDownloadedModelWhenAvailable = true),
            limit = 2,
        )

        assertEquals("unrelated", results.first().candidate.id)
    }

    @Test
    fun offlineEvaluationReportsLiftDiversityFreshnessRepetitionAndWhyQuality() {
        val nowMillis = 30L * 86_400_000L
        val recommendations = listOf(
            recommendation(
                candidate("win-a", artist = "Nina Vale", genre = "Soul", lastPlayedAtMillis = 0L),
                RecommendationReason.ArtistAffinity,
                RecommendationReason.GenreAffinity,
            ),
            recommendation(
                candidate("win-b", artist = "Nina Vale", genre = "Soul", lastPlayedAtMillis = nowMillis - 86_400_000L),
                RecommendationReason.ArtistAffinity,
            ),
            recommendation(
                candidate("fresh-c", artist = "Orbit House", genre = "Ambient", lastPlayedAtMillis = 0L),
                RecommendationReason.Discovery,
                RecommendationReason.HighQuality,
            ),
        )
        val events = listOf(
            behavior(BehaviorEventType.TrackCompleted, id = "win-a"),
            behavior(BehaviorEventType.TrackCompleted, id = "win-b"),
            behavior(BehaviorEventType.TrackSkipped, id = "miss-a", skipSeconds = 12),
            behavior(BehaviorEventType.DislikeHide, id = "miss-b"),
        )

        val evaluation = evaluateRecommendations(recommendations, events, nowMillis)

        assertEquals(3, evaluation.recommendedCount)
        assertEquals(4, evaluation.eventSampleSize)
        assertEquals(2, evaluation.matchedEventSampleSize)
        assertEquals(0.5f, evaluation.baselineCompletionRate, 0.001f)
        assertEquals(1f, evaluation.recommendedCompletionRate, 0.001f)
        assertTrue(evaluation.completionRateLift > 0.49f)
        assertTrue(evaluation.skipRateReduction > 0.49f)
        assertEquals(2f / 3f, evaluation.diversityScore, 0.001f)
        assertTrue(evaluation.freshnessScore > 0.67f)
        assertEquals(0.5f, evaluation.artistRepetitionScore, 0.001f)
        assertTrue(evaluation.whyQualityScore > 0.7f)
    }

    private fun behavior(
        type: BehaviorEventType,
        id: String = "song",
        artist: String = "Nina Vale",
        genre: String = "Soul",
        at: Long = 100_000L,
        skipSeconds: Int = 0,
    ): BehaviorEvent =
        BehaviorEvent(
            type = type,
            itemId = id,
            title = id,
            artist = artist,
            album = "Album",
            genre = genre,
            source = CandidateSource.LocalLibrary,
            occurredAtMillis = at,
            listenDurationMillis = 180_000L,
            skipPositionSeconds = skipSeconds,
        )

    private fun candidate(
        id: String,
        artist: String = "Nina Vale",
        genre: String = "Soul",
        source: CandidateSource = CandidateSource.LocalLibrary,
        liked: Boolean = false,
        disliked: Boolean = false,
        local: Boolean = source == CandidateSource.LocalLibrary,
        quality: Float = 0.6f,
        lastPlayedAtMillis: Long = 0L,
    ): TrackCandidate =
        TrackCandidate(
            id = id,
            title = id,
            artist = artist,
            album = "Album",
            genre = genre,
            source = source,
            liked = liked,
            disliked = disliked,
            localAvailable = local,
            qualityScore = quality,
            lastPlayedAtMillis = lastPlayedAtMillis,
        )

    private fun recommendation(
        candidate: TrackCandidate,
        vararg reasons: RecommendationReason,
    ): RecommendationResult =
        RecommendationResult(candidate = candidate, score = 1f, reasons = reasons.toList())

    private fun personalizationRepository(
        store: UserPreferenceStore,
        candidates: List<TrackCandidate>,
        scorer: TinyRecScorer,
    ): PremiumDeckPersonalizationRepository =
        PremiumDeckPersonalizationRepository(
            localCandidateProvider = LocalLibraryCandidateProvider { emptyList() },
            premiumDeckCandidateProvider = PremiumDeckCandidateProvider { candidates },
            store = store,
            tinyRecRunner = scorer,
        )

    private class FakeTinyRecScorer(
        override val productionReady: Boolean,
        private val scoreFor: (TrackCandidate) -> Float,
    ) : TinyRecScorer {
        override val health: TinyRecModelHealth = TinyRecModelHealth.MODEL_READY

        override suspend fun initialize(): TinyRecModelHealth = health

        override suspend fun score(userVector: List<Float>, candidates: List<TrackCandidate>): List<Float> =
            candidates.map(scoreFor)
    }
}
