package com.pulsedeck.app.premiumdeck.personalization

import java.util.Locale

class MixGenerator {
    fun generate(context: RecommendationContext, limit: Int): List<GeneratedMix> {
        val now = context.nowMillis
        val ranked = rankCandidates(context)
        val mixes = listOf(
            buildMix(
                id = "morning",
                title = "Morning Mix",
                subtitle = "Bright picks from your recent listening",
                candidates = ranked.filter { it.matchesMorning() }.ifEmpty { ranked },
                nowMillis = now,
            ),
            buildMix(
                id = "recently-loved",
                title = "Recently Loved",
                subtitle = "Songs and sources you liked, saved, or replayed",
                candidates = ranked.filter { it.liked || it.bookmarked || it.normalizedId in context.profile.positiveItemIds || it.playCount > 0 },
                nowMillis = now,
            ),
            buildMix(
                id = "chill",
                title = "Chill Mix",
                subtitle = "Lower-pressure tracks for quiet listening",
                candidates = ranked.filter { it.matchesChill() }.ifEmpty { ranked },
                nowMillis = now,
            ),
            buildMix(
                id = "discovery",
                title = "Discovery Mix",
                subtitle = "Fresh PremiumDeck and library candidates re-ranked on this device",
                candidates = ranked.filter { it.normalizedId !in context.profile.recentlyPlayedItemIds.take(30) },
                nowMillis = now,
            ),
            buildMix(
                id = "premiumdeck-hq",
                title = "PremiumDeck High Quality Picks",
                subtitle = "PremiumDeck candidates with the strongest available quality",
                candidates = ranked.filter { it.source == CandidateSource.PremiumDeck && it.qualityScore >= 0.65f },
                nowMillis = now,
            ),
        )
        return mixes.filter { it.tracks.isNotEmpty() }.take(limit.coerceAtLeast(1))
    }

    private fun rankCandidates(context: RecommendationContext): List<TrackCandidate> {
        val profile = context.profile.normalized()
        return context.candidates
            .asSequence()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .filterNot { it.disliked || it.normalizedId in profile.dislikedItemIds }
            .filter { !context.offlineMode || it.source == CandidateSource.LocalLibrary || it.localAvailable }
            .distinctBy { it.normalizedId }
            .sortedWith(
                compareByDescending<TrackCandidate> {
                    (profile.itemAffinity[it.normalizedId] ?: 0f) +
                        (profile.artistAffinity[it.artistKey] ?: 0f) * 0.7f +
                        (profile.genreAffinity[it.genreKey] ?: 0f) * 0.5f +
                        (if (it.liked) 0.8f else 0f) +
                        (if (it.bookmarked) 0.5f else 0f) +
                        it.qualityScore * 0.2f +
                        (if (it.source == CandidateSource.PremiumDeck) 0.05f else 0f)
                }.thenByDescending { it.lastPlayedAtMillis },
            )
            .take(PremiumDeckTinyRecConfig.CandidateLimitMax)
            .toList()
    }

    private fun buildMix(
        id: String,
        title: String,
        subtitle: String,
        candidates: List<TrackCandidate>,
        nowMillis: Long,
    ): GeneratedMix {
        val tracks = diversifyArtists(candidates, targetSize = 25)
        return GeneratedMix(
            id = "premiumdeck-$id",
            title = title,
            subtitle = subtitle,
            tracks = tracks,
            reasons = reasonsForMix(id, tracks),
            sourceBreakdown = SourceBreakdown(
                localCount = tracks.count { it.source == CandidateSource.LocalLibrary || it.localAvailable },
                premiumDeckCount = tracks.count { it.source == CandidateSource.PremiumDeck },
            ),
            generatedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + 6L * 60L * 60L * 1000L,
        )
    }

    private fun diversifyArtists(candidates: List<TrackCandidate>, targetSize: Int): List<TrackCandidate> {
        val result = mutableListOf<TrackCandidate>()
        val groups = candidates
            .groupBy { it.artistKey.ifBlank { it.artist.lowercase(Locale.US) } }
            .values
            .filter { it.isNotEmpty() }
            .sortedByDescending { group -> group.maxOf { it.qualityScore } }
        var index = 0
        while (result.size < targetSize) {
            var addedThisRound = false
            groups.forEach { group ->
                if (result.size >= targetSize) return@forEach
                val candidate = group.getOrNull(index) ?: return@forEach
                val wouldFlood = result.takeLast(2).size == 2 && result.takeLast(2).all { it.artistKey == candidate.artistKey }
                if (!wouldFlood && result.none { it.normalizedId == candidate.normalizedId }) {
                    result += candidate
                    addedThisRound = true
                }
            }
            if (!addedThisRound) break
            index += 1
        }
        return result
    }

    private fun reasonsForMix(id: String, tracks: List<TrackCandidate>): List<RecommendationReason> =
        buildList {
            add(RecommendationReason.HeuristicFallback)
            if (tracks.any { it.liked || it.bookmarked }) add(RecommendationReason.LikedOrSaved)
            if (tracks.any { it.source == CandidateSource.PremiumDeck }) add(RecommendationReason.PremiumDeckCandidate)
            if (tracks.any { it.qualityScore >= 0.65f }) add(RecommendationReason.HighQuality)
            if (id == "discovery") add(RecommendationReason.Discovery)
            if (id == "morning") add(RecommendationReason.TimeOfDay)
        }.distinct()

    private fun TrackCandidate.matchesMorning(): Boolean {
        val text = "${title} ${album} ${genre}".lowercase(Locale.US)
        return listOf("morning", "sun", "bright", "wake", "coffee", "acoustic", "soul", "pop").any { text.contains(it) } ||
            qualityScore >= 0.7f
    }

    private fun TrackCandidate.matchesChill(): Boolean {
        val text = "${title} ${album} ${genre}".lowercase(Locale.US)
        return listOf("chill", "lofi", "lo-fi", "ambient", "soft", "sleep", "night", "rain", "acoustic", "piano", "jazz").any { text.contains(it) }
    }
}
