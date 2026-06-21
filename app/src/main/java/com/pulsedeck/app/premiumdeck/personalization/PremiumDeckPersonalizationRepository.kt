package com.pulsedeck.app.premiumdeck.personalization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PremiumDeckPersonalizationRepository(
    private val localCandidateProvider: CandidateProvider,
    private val premiumDeckCandidateProvider: CandidateProvider,
    private val store: UserPreferenceStore,
    private val fallbackRecommender: HeuristicFallbackRecommender = HeuristicFallbackRecommender(store),
    private val tinyRecRunner: TinyRecScorer? = null,
) {
    val modelHealth: TinyRecModelHealth
        get() = tinyRecRunner?.health ?: TinyRecModelHealth.USING_HEURISTIC_FALLBACK

    suspend fun initializeModel(): TinyRecModelHealth =
        tinyRecRunner?.initialize() ?: TinyRecModelHealth.USING_HEURISTIC_FALLBACK

    suspend fun log(event: BehaviorEvent) {
        fallbackRecommender.onBehaviorEvent(event)
    }

    suspend fun recommendations(
        settings: PersonalizationSettings = PersonalizationSettings(),
        limit: Int = 25,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<RecommendationResult> = withContext(Dispatchers.Default) {
        if (!settings.enabled) return@withContext emptyList()
        val baseContext = context(settings, nowMillis)
        val fallback = fallbackRecommender.recommendSongs(baseContext, limit)
        val runner = tinyRecRunner
        if (
            runner != null &&
            settings.useDownloadedModelWhenAvailable &&
            runner.health == TinyRecModelHealth.MODEL_UNAVAILABLE
        ) {
            runner.initialize()
        }
        val tinyScores = tinyRecRunner
            ?.takeIf {
                it.health == TinyRecModelHealth.MODEL_READY &&
                    it.productionReady &&
                    settings.useDownloadedModelWhenAvailable
            }
            ?.score(baseContext.profile.userVector, fallback.map { it.candidate })
        if (tinyScores.isNullOrEmpty()) {
            fallback
        } else {
            fallback.zip(tinyScores) { result, tinyScore ->
                result.copy(score = result.score * 0.35f + tinyScore * 0.65f)
            }.sortedByDescending { it.score }
        }
    }

    suspend fun mixes(
        settings: PersonalizationSettings = PersonalizationSettings(),
        limit: Int = 6,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<GeneratedMix> =
        if (!settings.enabled) emptyList() else fallbackRecommender.generateMixes(context(settings, nowMillis), limit)

    suspend fun evaluate(
        settings: PersonalizationSettings = PersonalizationSettings(),
        recommendations: List<RecommendationResult>,
        nowMillis: Long = System.currentTimeMillis(),
    ): RecommendationEvaluation {
        if (!settings.enabled || recommendations.isEmpty()) return RecommendationEvaluation.Empty
        return evaluateRecommendations(
            recommendations = recommendations,
            recentEvents = context(settings, nowMillis).recentEvents,
            nowMillis = nowMillis,
        )
    }

    suspend fun reset() {
        fallbackRecommender.resetPersonalization()
    }

    suspend fun currentProfile(): UserPreferenceProfile = store.loadProfile()

    suspend fun context(
        settings: PersonalizationSettings,
        nowMillis: Long,
    ): RecommendationContext {
        val seedContext = RecommendationContext(
            candidates = emptyList(),
            profile = store.loadProfile().decayed(nowMillis),
            recentEvents = store.recentEvents(),
            offlineMode = settings.offlineMode,
            includePremiumDeck = settings.includePremiumDeckCandidates,
            nowMillis = nowMillis,
        )
        val local = localCandidateProvider.candidates(seedContext)
        val premium = premiumDeckCandidateProvider.candidates(seedContext)
        return seedContext.copy(candidates = (local + premium).distinctBy { it.normalizedId })
    }
}

class PremiumDeckPersonalizationViewModel(
    private val repository: PremiumDeckPersonalizationRepository,
) {
    suspend fun recommendations(settings: PersonalizationSettings, limit: Int = 25): List<RecommendationResult> =
        repository.recommendations(settings, limit)

    suspend fun mixes(settings: PersonalizationSettings, limit: Int = 6): List<GeneratedMix> =
        repository.mixes(settings, limit)

    suspend fun log(event: BehaviorEvent) {
        repository.log(event)
    }

    suspend fun resetPersonalization() {
        repository.reset()
    }
}
