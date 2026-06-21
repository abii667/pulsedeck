package com.pulsedeck.app.settings.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "eq_presets",
    indices = [Index(value = ["name"], unique = true)],
)
data class EqPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val payloadJson: String,
    val isUserPreset: Boolean,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "visualizer_presets",
    indices = [Index(value = ["name"], unique = true)],
)
data class VisualizerPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: String,
    val payloadJson: String,
    val enabled: Boolean,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "album_art_cache",
    indices = [
        Index(value = ["trackKey"]),
        Index(value = ["albumKey"]),
        Index(value = ["lastAccessedAtMillis"]),
    ],
)
data class AlbumArtCacheEntity(
    @PrimaryKey val cacheKey: String,
    val trackKey: String?,
    val albumKey: String?,
    val sourceUri: String?,
    val localCachePath: String?,
    val dominantColorHex: String?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val lastAccessedAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "library_cache",
    indices = [
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["lastSeenAtMillis"]),
    ],
)
data class LibraryCacheEntity(
    @PrimaryKey val trackKey: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMillis: Long,
    val dateModifiedMillis: Long,
    val lastSeenAtMillis: Long,
    val missing: Boolean,
)

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["trackKey"]),
        Index(value = ["playedAtMillis"]),
    ],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackKey: String,
    val title: String,
    val artist: String,
    val source: String,
    val durationMillis: Long,
    val playedAtMillis: Long,
    val completed: Boolean,
)

@Entity(
    tableName = "scrobble_retry_queue",
    indices = [
        Index(value = ["trackKey"]),
        Index(value = ["nextAttemptAtMillis"]),
    ],
)
data class ScrobbleRetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackKey: String,
    val artist: String,
    val track: String,
    val album: String?,
    val durationMillis: Long,
    val playedAtMillis: Long,
    val attemptCount: Int,
    val nextAttemptAtMillis: Long,
    val payloadJson: String,
    val lastError: String?,
)

@Dao
interface EqPresetDao {
    @Query("SELECT * FROM eq_presets ORDER BY isUserPreset DESC, name ASC")
    fun observePresets(): Flow<List<EqPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: EqPresetEntity)

    @Query("DELETE FROM eq_presets WHERE id = :id AND isUserPreset = 1")
    suspend fun deleteUserPreset(id: String)
}

@Dao
interface VisualizerPresetDao {
    @Query("SELECT * FROM visualizer_presets ORDER BY enabled DESC, name ASC")
    fun observePresets(): Flow<List<VisualizerPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: VisualizerPresetEntity)

    @Query("UPDATE visualizer_presets SET enabled = :enabled, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAtMillis: Long)
}

@Dao
interface AlbumArtCacheDao {
    @Query("SELECT * FROM album_art_cache WHERE cacheKey = :cacheKey")
    suspend fun get(cacheKey: String): AlbumArtCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AlbumArtCacheEntity)

    @Query("UPDATE album_art_cache SET lastAccessedAtMillis = :lastAccessedAtMillis WHERE cacheKey = :cacheKey")
    suspend fun markAccessed(cacheKey: String, lastAccessedAtMillis: Long)

    @Query("DELETE FROM album_art_cache WHERE lastAccessedAtMillis < :olderThanMillis")
    suspend fun pruneOlderThan(olderThanMillis: Long)
}

@Dao
interface LibraryCacheDao {
    @Query("SELECT * FROM library_cache WHERE missing = 0 ORDER BY artist ASC, album ASC, title ASC")
    fun observeLibrary(): Flow<List<LibraryCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<LibraryCacheEntity>)

    @Query("UPDATE library_cache SET missing = 1 WHERE lastSeenAtMillis < :scanStartedAtMillis")
    suspend fun markMissingBefore(scanStartedAtMillis: Long)

    @Query("DELETE FROM library_cache WHERE missing = 1")
    suspend fun deleteMissing()
}

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 250): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE playedAtMillis < :olderThanMillis")
    suspend fun pruneOlderThan(olderThanMillis: Long)
}

@Dao
interface ScrobbleRetryDao {
    @Query("SELECT * FROM scrobble_retry_queue WHERE nextAttemptAtMillis <= :nowMillis ORDER BY nextAttemptAtMillis ASC LIMIT :limit")
    suspend fun due(nowMillis: Long, limit: Int = 25): List<ScrobbleRetryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ScrobbleRetryEntity)

    @Query("UPDATE scrobble_retry_queue SET attemptCount = :attemptCount, nextAttemptAtMillis = :nextAttemptAtMillis, lastError = :lastError WHERE id = :id")
    suspend fun reschedule(id: Long, attemptCount: Int, nextAttemptAtMillis: Long, lastError: String?)

    @Query("DELETE FROM scrobble_retry_queue WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [
        EqPresetEntity::class,
        VisualizerPresetEntity::class,
        AlbumArtCacheEntity::class,
        LibraryCacheEntity::class,
        PlaybackHistoryEntity::class,
        ScrobbleRetryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PulseSettingsDatabase : RoomDatabase() {
    abstract fun eqPresetDao(): EqPresetDao
    abstract fun visualizerPresetDao(): VisualizerPresetDao
    abstract fun albumArtCacheDao(): AlbumArtCacheDao
    abstract fun libraryCacheDao(): LibraryCacheDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun scrobbleRetryDao(): ScrobbleRetryDao

    companion object {
        @Volatile private var instance: PulseSettingsDatabase? = null

        fun get(context: Context): PulseSettingsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseSettingsDatabase::class.java,
                    "pulse_settings_collections.db",
                ).build().also { instance = it }
            }
    }
}
