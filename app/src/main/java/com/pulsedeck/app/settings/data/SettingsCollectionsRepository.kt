package com.pulsedeck.app.settings.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface SettingsCollectionsRepository {
    fun observeEqPresets(): Flow<List<EqPresetEntity>>
    suspend fun saveEqPreset(preset: EqPresetEntity)
    suspend fun deleteUserEqPreset(id: String)

    fun observeVisualizerPresets(): Flow<List<VisualizerPresetEntity>>
    suspend fun saveVisualizerPreset(preset: VisualizerPresetEntity)
    suspend fun setVisualizerPresetEnabled(id: String, enabled: Boolean, updatedAtMillis: Long)

    suspend fun getAlbumArtCache(cacheKey: String): AlbumArtCacheEntity?
    suspend fun saveAlbumArtCache(entry: AlbumArtCacheEntity)
    suspend fun markAlbumArtAccessed(cacheKey: String, lastAccessedAtMillis: Long)
    suspend fun pruneAlbumArtOlderThan(olderThanMillis: Long)

    fun observeLibraryCache(): Flow<List<LibraryCacheEntity>>
    suspend fun saveLibraryScan(tracks: List<LibraryCacheEntity>, scanStartedAtMillis: Long)

    fun observePlaybackHistory(limit: Int = 250): Flow<List<PlaybackHistoryEntity>>
    suspend fun recordPlayback(entry: PlaybackHistoryEntity)
    suspend fun prunePlaybackHistoryOlderThan(olderThanMillis: Long)

    suspend fun dueScrobbles(nowMillis: Long, limit: Int = 25): List<ScrobbleRetryEntity>
    suspend fun saveScrobbleRetry(entry: ScrobbleRetryEntity)
    suspend fun rescheduleScrobbleRetry(id: Long, attemptCount: Int, nextAttemptAtMillis: Long, lastError: String?)
    suspend fun deleteScrobbleRetry(id: Long)
}

class RoomSettingsCollectionsRepository(
    context: Context,
) : SettingsCollectionsRepository {
    private val database = PulseSettingsDatabase.get(context)

    override fun observeEqPresets(): Flow<List<EqPresetEntity>> =
        database.eqPresetDao().observePresets()

    override suspend fun saveEqPreset(preset: EqPresetEntity) {
        database.eqPresetDao().upsert(preset)
    }

    override suspend fun deleteUserEqPreset(id: String) {
        database.eqPresetDao().deleteUserPreset(id)
    }

    override fun observeVisualizerPresets(): Flow<List<VisualizerPresetEntity>> =
        database.visualizerPresetDao().observePresets()

    override suspend fun saveVisualizerPreset(preset: VisualizerPresetEntity) {
        database.visualizerPresetDao().upsert(preset)
    }

    override suspend fun setVisualizerPresetEnabled(id: String, enabled: Boolean, updatedAtMillis: Long) {
        database.visualizerPresetDao().setEnabled(id, enabled, updatedAtMillis)
    }

    override suspend fun getAlbumArtCache(cacheKey: String): AlbumArtCacheEntity? =
        database.albumArtCacheDao().get(cacheKey)

    override suspend fun saveAlbumArtCache(entry: AlbumArtCacheEntity) {
        database.albumArtCacheDao().upsert(entry)
    }

    override suspend fun markAlbumArtAccessed(cacheKey: String, lastAccessedAtMillis: Long) {
        database.albumArtCacheDao().markAccessed(cacheKey, lastAccessedAtMillis)
    }

    override suspend fun pruneAlbumArtOlderThan(olderThanMillis: Long) {
        database.albumArtCacheDao().pruneOlderThan(olderThanMillis)
    }

    override fun observeLibraryCache(): Flow<List<LibraryCacheEntity>> =
        database.libraryCacheDao().observeLibrary()

    override suspend fun saveLibraryScan(tracks: List<LibraryCacheEntity>, scanStartedAtMillis: Long) {
        database.libraryCacheDao().upsertAll(tracks)
        database.libraryCacheDao().markMissingBefore(scanStartedAtMillis)
        database.libraryCacheDao().deleteMissing()
    }

    override fun observePlaybackHistory(limit: Int): Flow<List<PlaybackHistoryEntity>> =
        database.playbackHistoryDao().observeRecent(limit)

    override suspend fun recordPlayback(entry: PlaybackHistoryEntity) {
        database.playbackHistoryDao().insert(entry)
    }

    override suspend fun prunePlaybackHistoryOlderThan(olderThanMillis: Long) {
        database.playbackHistoryDao().pruneOlderThan(olderThanMillis)
    }

    override suspend fun dueScrobbles(nowMillis: Long, limit: Int): List<ScrobbleRetryEntity> =
        database.scrobbleRetryDao().due(nowMillis, limit)

    override suspend fun saveScrobbleRetry(entry: ScrobbleRetryEntity) {
        database.scrobbleRetryDao().upsert(entry)
    }

    override suspend fun rescheduleScrobbleRetry(id: Long, attemptCount: Int, nextAttemptAtMillis: Long, lastError: String?) {
        database.scrobbleRetryDao().reschedule(id, attemptCount, nextAttemptAtMillis, lastError)
    }

    override suspend fun deleteScrobbleRetry(id: Long) {
        database.scrobbleRetryDao().delete(id)
    }
}
