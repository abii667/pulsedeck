package com.pulsedeck.app.premiumdeck.personalization

interface UserPreferenceStore {
    suspend fun appendEvent(event: BehaviorEvent)
    suspend fun recentEvents(limit: Int = PremiumDeckTinyRecConfig.HistoryLength): List<BehaviorEvent>
    suspend fun loadProfile(): UserPreferenceProfile
    suspend fun saveProfile(profile: UserPreferenceProfile)
    suspend fun loadReplayBuffer(): ReplayBuffer
    suspend fun appendReplayEvent(event: BehaviorEvent)
    suspend fun prune(nowMillis: Long = System.currentTimeMillis())
    suspend fun reset()
}

class InMemoryUserPreferenceStore : UserPreferenceStore {
    private val events = mutableListOf<BehaviorEvent>()
    private var profile = UserPreferenceProfile()
    private var replayBuffer = ReplayBuffer()

    override suspend fun appendEvent(event: BehaviorEvent) {
        events += event
    }

    override suspend fun recentEvents(limit: Int): List<BehaviorEvent> =
        events.sortedByDescending { it.occurredAtMillis }.take(limit)

    override suspend fun loadProfile(): UserPreferenceProfile = profile

    override suspend fun saveProfile(profile: UserPreferenceProfile) {
        this.profile = profile.normalized()
    }

    override suspend fun loadReplayBuffer(): ReplayBuffer = replayBuffer

    override suspend fun appendReplayEvent(event: BehaviorEvent) {
        replayBuffer = replayBuffer.add(event)
    }

    override suspend fun prune(nowMillis: Long) {
        val retentionStart = nowMillis - 90L * 86_400_000L
        events.removeAll { it.occurredAtMillis < retentionStart }
    }

    override suspend fun reset() {
        events.clear()
        profile = UserPreferenceProfile()
        replayBuffer = ReplayBuffer()
    }
}
