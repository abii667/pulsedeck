package com.pulsedeck.app.premiumdeck.personalization

interface OnDeviceRecommender {
    suspend fun recommendSongs(
        context: RecommendationContext,
        limit: Int,
    ): List<RecommendationResult>

    suspend fun recommendAlbums(
        context: RecommendationContext,
        limit: Int,
    ): List<RecommendationResult>

    suspend fun recommendArtists(
        context: RecommendationContext,
        limit: Int,
    ): List<RecommendationResult>

    suspend fun generateMixes(
        context: RecommendationContext,
        limit: Int,
    ): List<GeneratedMix>

    suspend fun onBehaviorEvent(event: BehaviorEvent)

    suspend fun resetPersonalization()
}

data class PersonalizationSettings(
    val enabled: Boolean = true,
    val includePremiumDeckCandidates: Boolean = true,
    val useDownloadedModelWhenAvailable: Boolean = true,
    val offlineMode: Boolean = false,
)
