package com.pulsedeck.app.premiumdeck.personalization

data class RecommendationEvaluation(
    val recommendedCount: Int,
    val eventSampleSize: Int,
    val matchedEventSampleSize: Int,
    val baselineCompletionRate: Float,
    val recommendedCompletionRate: Float,
    val completionRateLift: Float,
    val baselineSkipRate: Float,
    val recommendedSkipRate: Float,
    val skipRateReduction: Float,
    val diversityScore: Float,
    val freshnessScore: Float,
    val artistRepetitionScore: Float,
    val whyQualityScore: Float,
) {
    companion object {
        val Empty = RecommendationEvaluation(
            recommendedCount = 0,
            eventSampleSize = 0,
            matchedEventSampleSize = 0,
            baselineCompletionRate = 0f,
            recommendedCompletionRate = 0f,
            completionRateLift = 0f,
            baselineSkipRate = 0f,
            recommendedSkipRate = 0f,
            skipRateReduction = 0f,
            diversityScore = 0f,
            freshnessScore = 0f,
            artistRepetitionScore = 0f,
            whyQualityScore = 0f,
        )
    }
}

fun evaluateRecommendations(
    recommendations: List<RecommendationResult>,
    recentEvents: List<BehaviorEvent>,
    nowMillis: Long = System.currentTimeMillis(),
): RecommendationEvaluation {
    if (recommendations.isEmpty()) return RecommendationEvaluation.Empty
    val outcomeEvents = recentEvents
        .map { it.normalizedForWeight() }
        .filter { it.type.isRecommendationOutcome }
    val recommendedIds = recommendations
        .map { it.candidate.normalizedId }
        .toSet()
    val matchedEvents = outcomeEvents.filter { it.itemId.itemKey() in recommendedIds }
    val baselineCompletionRate = completionRate(outcomeEvents)
    val recommendedCompletionRate = completionRate(matchedEvents)
    val baselineSkipRate = skipRate(outcomeEvents)
    val recommendedSkipRate = skipRate(matchedEvents)
    return RecommendationEvaluation(
        recommendedCount = recommendations.size,
        eventSampleSize = outcomeEvents.size,
        matchedEventSampleSize = matchedEvents.size,
        baselineCompletionRate = baselineCompletionRate,
        recommendedCompletionRate = recommendedCompletionRate,
        completionRateLift = recommendedCompletionRate - baselineCompletionRate,
        baselineSkipRate = baselineSkipRate,
        recommendedSkipRate = recommendedSkipRate,
        skipRateReduction = baselineSkipRate - recommendedSkipRate,
        diversityScore = artistDiversityScore(recommendations),
        freshnessScore = freshnessScore(recommendations, nowMillis),
        artistRepetitionScore = artistRepetitionScore(recommendations),
        whyQualityScore = whyQualityScore(recommendations),
    )
}

private val BehaviorEventType.isRecommendationOutcome: Boolean
    get() = isPositiveCompletion || isSkipOutcome

private val BehaviorEventType.isPositiveCompletion: Boolean
    get() = when (this) {
        BehaviorEventType.TrackCompleted,
        BehaviorEventType.RepeatPlay,
        BehaviorEventType.LikeFavorite,
        BehaviorEventType.TrackAddedToPlaylist -> true
        else -> false
    }

private val BehaviorEventType.isSkipOutcome: Boolean
    get() = when (this) {
        BehaviorEventType.TrackSkipped,
        BehaviorEventType.SkipUnder30Seconds,
        BehaviorEventType.Skip30To60Seconds,
        BehaviorEventType.DislikeHide,
        BehaviorEventType.TrackRemovedFromPlaylist -> true
        else -> false
    }

private fun completionRate(events: List<BehaviorEvent>): Float {
    if (events.isEmpty()) return 0f
    return events.count { it.type.isPositiveCompletion }.toFloat() / events.size
}

private fun skipRate(events: List<BehaviorEvent>): Float {
    if (events.isEmpty()) return 0f
    return events.count { it.type.isSkipOutcome }.toFloat() / events.size
}

private fun artistDiversityScore(recommendations: List<RecommendationResult>): Float {
    if (recommendations.isEmpty()) return 0f
    return recommendations
        .map { it.candidate.artistKey }
        .filter { it.isNotBlank() }
        .distinct()
        .size
        .toFloat()
        .div(recommendations.size)
        .coerceIn(0f, 1f)
}

private fun freshnessScore(recommendations: List<RecommendationResult>, nowMillis: Long): Float {
    if (recommendations.isEmpty()) return 0f
    return recommendations
        .map { result ->
            val lastPlayed = result.candidate.lastPlayedAtMillis
            when {
                lastPlayed <= 0L -> 1f
                else -> ((nowMillis - lastPlayed).coerceAtLeast(0L) / (21f * 86_400_000f)).coerceIn(0f, 1f)
            }
        }
        .average()
        .toFloat()
}

private fun artistRepetitionScore(recommendations: List<RecommendationResult>): Float {
    if (recommendations.size < 2) return 1f
    val repeatedPairs = recommendations
        .map { it.candidate.artistKey }
        .zipWithNext()
        .count { (left, right) -> left.isNotBlank() && left == right }
    return (1f - repeatedPairs.toFloat() / (recommendations.size - 1)).coerceIn(0f, 1f)
}

private fun whyQualityScore(recommendations: List<RecommendationResult>): Float {
    if (recommendations.isEmpty()) return 0f
    return recommendations
        .map { result ->
            val specificReasons = result.reasons.filterNot { it == RecommendationReason.HeuristicFallback }
            when {
                specificReasons.isEmpty() -> 0f
                specificReasons.size == 1 -> 0.55f
                specificReasons.size == 2 -> 0.80f
                else -> 1f
            }
        }
        .average()
        .toFloat()
}
