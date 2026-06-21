package com.pulsedeck.app.premiumdeck.personalization

data class TrackCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val genre: String = "",
    val source: CandidateSource = CandidateSource.LocalLibrary,
    val durationMillis: Long = 0L,
    val qualityScore: Float = 0.5f,
    val addedAtMillis: Long = 0L,
    val lastPlayedAtMillis: Long = 0L,
    val playCount: Int = 0,
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val bookmarked: Boolean = false,
    val localAvailable: Boolean = source == CandidateSource.LocalLibrary,
    val codebookIds: List<Int> = emptyList(),
    val playbackUri: String? = null,
    val externalUrl: String = "",
) {
    val normalizedId: String get() = id.itemKey()
    val artistKey: String get() = artist.affinityKey()
    val genreKey: String get() = genre.affinityKey()
}

data class RecommendationContext(
    val candidates: List<TrackCandidate>,
    val profile: UserPreferenceProfile = UserPreferenceProfile(),
    val recentEvents: List<BehaviorEvent> = emptyList(),
    val offlineMode: Boolean = false,
    val includePremiumDeck: Boolean = true,
    val currentItemId: String = "",
    val nowMillis: Long = System.currentTimeMillis(),
)

enum class RecommendationReason {
    RecentListening,
    ArtistAffinity,
    GenreAffinity,
    CompletedOften,
    TimeOfDay,
    Discovery,
    HighQuality,
    LocalOffline,
    PremiumDeckCandidate,
    LikedOrSaved,
    HeuristicFallback,
}

data class RecommendationResult(
    val candidate: TrackCandidate,
    val score: Float,
    val reasons: List<RecommendationReason>,
)

data class SourceBreakdown(
    val localCount: Int,
    val premiumDeckCount: Int,
)

data class GeneratedMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<TrackCandidate>,
    val reasons: List<RecommendationReason>,
    val sourceBreakdown: SourceBreakdown,
    val generatedAtMillis: Long,
    val expiresAtMillis: Long,
)

interface CandidateProvider {
    suspend fun candidates(context: RecommendationContext): List<TrackCandidate>
}

class LocalLibraryCandidateProvider(
    private val loader: suspend () -> List<TrackCandidate>,
) : CandidateProvider {
    override suspend fun candidates(context: RecommendationContext): List<TrackCandidate> =
        loader().filter { it.source == CandidateSource.LocalLibrary || it.localAvailable }
}

class PremiumDeckCandidateProvider(
    private val loader: suspend () -> List<TrackCandidate>,
) : CandidateProvider {
    override suspend fun candidates(context: RecommendationContext): List<TrackCandidate> =
        if (context.offlineMode || !context.includePremiumDeck) {
            loader().filter { it.source == CandidateSource.PremiumDeck && it.localAvailable }
        } else {
            loader().filter { it.source == CandidateSource.PremiumDeck }
        }
}

interface PremiumDeckAssistantModelProvider {
    val available: Boolean
    suspend fun explainRecommendation(result: RecommendationResult): String?
    suspend fun nameMix(seed: List<TrackCandidate>): String?
}

object DisabledPremiumDeckAssistantModelProvider : PremiumDeckAssistantModelProvider {
    override val available: Boolean = false
    override suspend fun explainRecommendation(result: RecommendationResult): String? = null
    override suspend fun nameMix(seed: List<TrackCandidate>): String? = null
}
