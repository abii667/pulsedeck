package com.pulsedeck.app.premiumdeck.personalization

import kotlin.math.pow

data class UserPreferenceProfile(
    val userVector: List<Float> = List(PremiumDeckTinyRecConfig.UserVectorDim) { 0f },
    val genreAffinity: Map<String, Float> = emptyMap(),
    val artistAffinity: Map<String, Float> = emptyMap(),
    val itemAffinity: Map<String, Float> = emptyMap(),
    val completionRateAffinity: Map<String, Float> = emptyMap(),
    val dislikedItemIds: Set<String> = emptySet(),
    val recentlyPlayedItemIds: List<String> = emptyList(),
    val positiveItemIds: Set<String> = emptySet(),
    val timeOfDayAffinity: Map<TimeOfDayBucket, Float> = emptyMap(),
    val updatedAtMillis: Long = 0L,
) {
    fun normalized(): UserPreferenceProfile =
        copy(
            userVector = userVector.normalizedVector(PremiumDeckTinyRecConfig.UserVectorDim),
            genreAffinity = genreAffinity.cleanAffinityMap(),
            artistAffinity = artistAffinity.cleanAffinityMap(),
            itemAffinity = itemAffinity.cleanAffinityMap(),
            completionRateAffinity = completionRateAffinity.cleanAffinityMap(),
            recentlyPlayedItemIds = recentlyPlayedItemIds.filter { it.isNotBlank() }.distinct().take(80),
            positiveItemIds = positiveItemIds.filter { it.isNotBlank() }.toSet(),
            dislikedItemIds = dislikedItemIds.filter { it.isNotBlank() }.toSet(),
        )

    fun decayed(nowMillis: Long): UserPreferenceProfile {
        if (updatedAtMillis <= 0L || nowMillis <= updatedAtMillis) return normalized()
        val ageDays = (nowMillis - updatedAtMillis).toDouble() / 86_400_000.0
        val factor = 0.5.pow(ageDays / PremiumDeckTinyRecConfig.ProfileDecayHalfLifeDays).toFloat().coerceIn(0.05f, 1f)
        return copy(
            genreAffinity = genreAffinity.mapValues { (_, value) -> value * factor },
            artistAffinity = artistAffinity.mapValues { (_, value) -> value * factor },
            itemAffinity = itemAffinity.mapValues { (_, value) -> value * factor },
            completionRateAffinity = completionRateAffinity.mapValues { (_, value) -> value * factor },
            timeOfDayAffinity = timeOfDayAffinity.mapValues { (_, value) -> value * factor },
        ).normalized()
    }
}

data class ReplayBuffer(
    val events: List<BehaviorEvent> = emptyList(),
) {
    fun add(event: BehaviorEvent): ReplayBuffer =
        copy(events = (listOf(event) + events).distinctBy { it.id }.take(PremiumDeckTinyRecConfig.ReplayBufferMaxEvents))

    fun representativePositiveEvents(limit: Int = 24): List<BehaviorEvent> =
        events
            .filter { PremiumDeckTinyRecConfig.eventWeights[it.normalizedForWeight().type].orZero() > 0f }
            .distinctBy { it.itemId.ifBlank { "${it.artist}|${it.title}" } }
            .take(limit)
}

internal object UserPreferenceUpdater {
    fun update(
        profile: UserPreferenceProfile,
        event: BehaviorEvent,
        replayEvents: List<BehaviorEvent> = emptyList(),
        nowMillis: Long = event.occurredAtMillis,
    ): UserPreferenceProfile {
        val normalizedEvent = event.normalizedForWeight()
        val baseWeight = PremiumDeckTinyRecConfig.eventWeights[normalizedEvent.type] ?: return profile.normalized()
        val afterPrimary = applyWeightedEvent(profile.decayed(nowMillis), normalizedEvent, baseWeight, nowMillis)
        return replayEvents.fold(afterPrimary) { current, replay ->
            val replayWeight = (PremiumDeckTinyRecConfig.eventWeights[replay.normalizedForWeight().type] ?: 0f)
            if (replayWeight <= 0f) current else applyWeightedEvent(current, replay, replayWeight * 0.08f, nowMillis)
        }.normalized()
    }

    private fun applyWeightedEvent(
        profile: UserPreferenceProfile,
        event: BehaviorEvent,
        weight: Float,
        nowMillis: Long,
    ): UserPreferenceProfile {
        val eventVector = featureVector(event.itemId, event.title, event.artist, event.album, event.genre)
        val alpha = (0.08f + kotlin.math.abs(weight) * 0.05f).coerceIn(0.04f, 0.18f)
        val signedVector = if (weight >= 0f) eventVector else eventVector.map { -it }
        val nextVector = profile.userVector.normalizedVector(PremiumDeckTinyRecConfig.UserVectorDim)
            .zip(signedVector) { old, incoming -> old * (1f - alpha) + incoming * alpha }
            .normalizedVector(PremiumDeckTinyRecConfig.UserVectorDim)
        val genreKey = event.genre.affinityKey()
        val artistKey = event.artist.affinityKey()
        val itemKey = event.itemId.itemKey()
        val positive = weight > 0f
        return profile.copy(
            userVector = nextVector,
            genreAffinity = profile.genreAffinity.adjustAffinity(genreKey, weight * 0.32f),
            artistAffinity = profile.artistAffinity.adjustAffinity(artistKey, weight * 0.38f),
            itemAffinity = profile.itemAffinity.adjustAffinity(itemKey, weight * 0.55f),
            completionRateAffinity = profile.completionRateAffinity.adjustAffinity(itemKey, completionDelta(event, weight)),
            dislikedItemIds = if (weight <= -1f && itemKey.isNotBlank()) profile.dislikedItemIds + itemKey else profile.dislikedItemIds - itemKey.takeIf { positive }.orEmpty(),
            recentlyPlayedItemIds = if (event.type in setOf(BehaviorEventType.TrackStarted, BehaviorEventType.TrackCompleted, BehaviorEventType.RepeatPlay)) {
                (listOf(itemKey) + profile.recentlyPlayedItemIds).filter { it.isNotBlank() }.distinct().take(80)
            } else {
                profile.recentlyPlayedItemIds
            },
            positiveItemIds = if (positive && itemKey.isNotBlank()) profile.positiveItemIds + itemKey else profile.positiveItemIds,
            timeOfDayAffinity = profile.timeOfDayAffinity + (event.timeOfDayBucket to ((profile.timeOfDayAffinity[event.timeOfDayBucket] ?: 0f) + weight * 0.12f).coerceIn(-1.5f, 1.5f)),
            updatedAtMillis = nowMillis,
        )
    }

    private fun completionDelta(event: BehaviorEvent, weight: Float): Float {
        if (event.listenDurationMillis <= 0L) return weight * 0.18f
        return (event.listenDurationMillis / 240_000f).coerceIn(0f, 1f) * weight * 0.35f
    }
}

internal fun String.affinityKey(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

internal fun String.itemKey(): String =
    trim().lowercase()

internal fun Float?.orZero(): Float = this ?: 0f

internal fun Map<String, Float>.adjustAffinity(key: String, delta: Float): Map<String, Float> {
    if (key.isBlank() || delta == 0f) return this
    val next = ((this[key] ?: 0f) + delta).coerceIn(-2.5f, 3f)
    return (this + (key to next)).cleanAffinityMap()
}

internal fun Map<String, Float>.cleanAffinityMap(): Map<String, Float> =
    entries
        .filter { it.key.isNotBlank() && kotlin.math.abs(it.value) >= 0.01f }
        .sortedByDescending { kotlin.math.abs(it.value) }
        .take(256)
        .associate { it.key to it.value.coerceIn(-3f, 3f) }

internal fun List<Float>.normalizedVector(size: Int): List<Float> {
    val fixed = when {
        this.size == size -> this
        this.size > size -> take(size)
        else -> this + List(size - this.size) { 0f }
    }
    val magnitude = kotlin.math.sqrt(fixed.sumOf { (it * it).toDouble() }).toFloat()
    if (magnitude <= 0.0001f) return fixed
    return fixed.map { it / magnitude }
}

internal fun featureVector(vararg parts: String, dim: Int = PremiumDeckTinyRecConfig.UserVectorDim): List<Float> {
    val vector = FloatArray(dim)
    parts.filter { it.isNotBlank() }.forEachIndexed { partIndex, part ->
        val tokens = part.affinityKey().split(' ').filter { it.isNotBlank() }.ifEmpty { listOf(part) }
        tokens.forEach { token ->
            val hash = (token.hashCode() * 31 + partIndex * 131)
            val index = Math.floorMod(hash, dim)
            val sign = if ((hash and 1) == 0) 1f else -1f
            vector[index] += sign
        }
    }
    return vector.toList().normalizedVector(dim)
}
