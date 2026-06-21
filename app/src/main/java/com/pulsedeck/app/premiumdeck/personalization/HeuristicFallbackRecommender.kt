package com.pulsedeck.app.premiumdeck.personalization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class HeuristicFallbackRecommender(
    private val store: UserPreferenceStore = InMemoryUserPreferenceStore(),
    private val mixGenerator: MixGenerator = MixGenerator(),
) : OnDeviceRecommender {
    override suspend fun recommendSongs(context: RecommendationContext, limit: Int): List<RecommendationResult> =
        withContext(Dispatchers.Default) {
            val profile = context.profile.normalized()
            filteredCandidates(context)
                .asSequence()
                .take(PremiumDeckTinyRecConfig.CandidateLimitMax)
                .map { candidate -> score(candidate, profile, context) }
                .filter { it.score > -0.75f }
                .sortedWith(compareByDescending<RecommendationResult> { it.score }.thenBy { it.candidate.title.affinityKey() })
                .take(limit.coerceIn(1, PremiumDeckTinyRecConfig.CandidateLimitMax))
                .toList()
        }

    override suspend fun recommendAlbums(context: RecommendationContext, limit: Int): List<RecommendationResult> =
        recommendSongs(context, PremiumDeckTinyRecConfig.CandidateLimitMax)
            .groupBy { it.candidate.album.ifBlank { "Unknown album" }.affinityKey() }
            .map { (_, results) ->
                val best = results.maxBy { it.score }
                best.copy(
                    candidate = best.candidate.copy(
                        id = "album:${best.candidate.album.ifBlank { best.candidate.id }}",
                        title = best.candidate.album.ifBlank { "Unknown album" },
                    ),
                    score = results.sumOf { it.score.toDouble() }.toFloat() / results.size.coerceAtLeast(1),
                )
            }
            .sortedByDescending { it.score }
            .take(limit.coerceAtLeast(1))

    override suspend fun recommendArtists(context: RecommendationContext, limit: Int): List<RecommendationResult> =
        recommendSongs(context, PremiumDeckTinyRecConfig.CandidateLimitMax)
            .groupBy { it.candidate.artist.ifBlank { "Unknown artist" }.affinityKey() }
            .map { (_, results) ->
                val best = results.maxBy { it.score }
                best.copy(
                    candidate = best.candidate.copy(
                        id = "artist:${best.candidate.artist.ifBlank { best.candidate.id }}",
                        title = best.candidate.artist.ifBlank { "Unknown artist" },
                    ),
                    score = results.sumOf { it.score.toDouble() }.toFloat() / results.size.coerceAtLeast(1),
                )
            }
            .sortedByDescending { it.score }
            .take(limit.coerceAtLeast(1))

    override suspend fun generateMixes(context: RecommendationContext, limit: Int): List<GeneratedMix> =
        withContext(Dispatchers.Default) {
            mixGenerator.generate(context.copy(candidates = filteredCandidates(context)), limit)
        }

    override suspend fun onBehaviorEvent(event: BehaviorEvent) {
        val normalized = event.normalizedForWeight()
        store.appendEvent(normalized)
        if ((PremiumDeckTinyRecConfig.eventWeights[normalized.type] ?: 0f) > 0f) {
            store.appendReplayEvent(normalized)
        }
        val replay = store.loadReplayBuffer().representativePositiveEvents()
        val nextProfile = UserPreferenceUpdater.update(store.loadProfile(), normalized, replay, normalized.occurredAtMillis)
        store.saveProfile(nextProfile)
        store.prune(normalized.occurredAtMillis)
    }

    override suspend fun resetPersonalization() {
        store.reset()
    }

    private fun filteredCandidates(context: RecommendationContext): List<TrackCandidate> =
        context.candidates
            .asSequence()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .filterNot { it.disliked || it.normalizedId in context.profile.dislikedItemIds }
            .filter { !context.offlineMode || it.source == CandidateSource.LocalLibrary || it.localAvailable }
            .filter { context.includePremiumDeck || it.source != CandidateSource.PremiumDeck }
            .distinctBy { it.normalizedId.ifBlank { "${it.title}|${it.artist}".affinityKey() } }
            .take(PremiumDeckTinyRecConfig.CandidateLimitMax)
            .toList()

    private fun score(
        candidate: TrackCandidate,
        profile: UserPreferenceProfile,
        context: RecommendationContext,
    ): RecommendationResult {
        val genreAffinity = normalizedAffinity(profile.genreAffinity[candidate.genreKey])
        val artistAffinity = normalizedAffinity(profile.artistAffinity[candidate.artistKey])
        val completionAffinity = normalizedAffinity(profile.completionRateAffinity[candidate.normalizedId] ?: profile.itemAffinity[candidate.normalizedId])
        val timeMatch = normalizedAffinity(profile.timeOfDayAffinity[TimeOfDayBucket.fromMillis(context.nowMillis)])
        val freshness = freshnessScore(candidate, profile, context.nowMillis)
        val quality = candidate.qualityScore.coerceIn(0f, 1f)
        val repetitionPenalty = if (candidate.normalizedId in profile.recentlyPlayedItemIds.take(12)) 0.28f else 0f
        val skipPenalty = max(0f, -(profile.itemAffinity[candidate.normalizedId] ?: 0f)) * 0.20f
        val dislikePenalty = if (candidate.normalizedId in profile.dislikedItemIds) 2f else 0f
        val explicitBoost = when {
            candidate.liked -> 0.28f
            candidate.bookmarked -> 0.18f
            candidate.playCount > 0 -> minOf(0.24f, candidate.playCount * 0.025f)
            else -> 0f
        }
        val score =
            0.35f * genreAffinity +
                0.25f * artistAffinity +
                0.15f * completionAffinity +
                0.10f * timeMatch +
                0.10f * freshness +
                0.05f * quality +
                explicitBoost -
                repetitionPenalty -
                skipPenalty -
                dislikePenalty

        return RecommendationResult(
            candidate = candidate,
            score = score,
            reasons = reasonsFor(candidate, genreAffinity, artistAffinity, completionAffinity, timeMatch, freshness, quality),
        )
    }

    private fun reasonsFor(
        candidate: TrackCandidate,
        genreAffinity: Float,
        artistAffinity: Float,
        completionAffinity: Float,
        timeMatch: Float,
        freshness: Float,
        quality: Float,
    ): List<RecommendationReason> =
        buildList {
            add(RecommendationReason.HeuristicFallback)
            if (artistAffinity > 0.55f) add(RecommendationReason.ArtistAffinity)
            if (genreAffinity > 0.55f) add(RecommendationReason.GenreAffinity)
            if (completionAffinity > 0.55f) add(RecommendationReason.CompletedOften)
            if (timeMatch > 0.55f) add(RecommendationReason.TimeOfDay)
            if (freshness > 0.60f) add(RecommendationReason.Discovery)
            if (quality > 0.70f) add(RecommendationReason.HighQuality)
            if (candidate.source == CandidateSource.LocalLibrary || candidate.localAvailable) add(RecommendationReason.LocalOffline)
            if (candidate.source == CandidateSource.PremiumDeck) add(RecommendationReason.PremiumDeckCandidate)
            if (candidate.liked || candidate.bookmarked) add(RecommendationReason.LikedOrSaved)
        }.distinct().take(5)

    private fun freshnessScore(candidate: TrackCandidate, profile: UserPreferenceProfile, nowMillis: Long): Float {
        if (candidate.normalizedId !in profile.recentlyPlayedItemIds) return if (candidate.source == CandidateSource.PremiumDeck) 0.72f else 0.58f
        if (candidate.lastPlayedAtMillis <= 0L) return 0.12f
        val ageDays = (nowMillis - candidate.lastPlayedAtMillis).coerceAtLeast(0L) / 86_400_000f
        return (ageDays / 21f).coerceIn(0f, 1f)
    }

    private fun normalizedAffinity(value: Float?): Float =
        (((value ?: 0f).coerceIn(-2f, 2f) + 2f) / 4f).coerceIn(0f, 1f)
}
