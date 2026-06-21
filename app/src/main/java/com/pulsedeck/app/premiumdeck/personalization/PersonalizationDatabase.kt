package com.pulsedeck.app.premiumdeck.personalization

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
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "premiumdeck_behavior_events",
    indices = [
        Index(value = ["type"]),
        Index(value = ["itemId"]),
        Index(value = ["occurredAtMillis"]),
        Index(value = ["source"]),
    ],
)
data class BehaviorEventEntity(
    @PrimaryKey val id: String,
    val type: String,
    val itemId: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val source: String,
    val occurredAtMillis: Long,
    val listenDurationMillis: Long,
    val skipPositionSeconds: Int,
    val query: String,
    val timeOfDayBucket: String,
    val dayOfWeekBucket: Int,
    val metadataJson: String,
)

@Entity(tableName = "premiumdeck_user_profiles")
data class UserPreferenceProfileEntity(
    @PrimaryKey val id: String = "default",
    val vectorJson: String,
    val genreAffinityJson: String,
    val artistAffinityJson: String,
    val itemAffinityJson: String,
    val completionAffinityJson: String,
    val dislikedIdsJson: String,
    val recentIdsJson: String,
    val positiveIdsJson: String,
    val timeOfDayAffinityJson: String,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "premiumdeck_replay_buffer",
    indices = [Index(value = ["occurredAtMillis"])],
)
data class ReplayBufferEventEntity(
    @PrimaryKey val id: String,
    val occurredAtMillis: Long,
    val eventJson: String,
)

@Dao
interface PremiumDeckPersonalizationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: BehaviorEventEntity)

    @Query("SELECT * FROM premiumdeck_behavior_events ORDER BY occurredAtMillis DESC LIMIT :limit")
    suspend fun recentEvents(limit: Int): List<BehaviorEventEntity>

    @Query("DELETE FROM premiumdeck_behavior_events WHERE occurredAtMillis < :olderThanMillis")
    suspend fun pruneEvents(olderThanMillis: Long)

    @Query("SELECT * FROM premiumdeck_user_profiles WHERE id = 'default'")
    suspend fun profile(): UserPreferenceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserPreferenceProfileEntity)

    @Query("DELETE FROM premiumdeck_user_profiles")
    suspend fun clearProfiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplayEvent(event: ReplayBufferEventEntity)

    @Query("SELECT * FROM premiumdeck_replay_buffer ORDER BY occurredAtMillis DESC LIMIT :limit")
    suspend fun replayEvents(limit: Int = PremiumDeckTinyRecConfig.ReplayBufferMaxEvents): List<ReplayBufferEventEntity>

    @Query("DELETE FROM premiumdeck_replay_buffer WHERE id NOT IN (SELECT id FROM premiumdeck_replay_buffer ORDER BY occurredAtMillis DESC LIMIT :limit)")
    suspend fun trimReplayEvents(limit: Int = PremiumDeckTinyRecConfig.ReplayBufferMaxEvents)

    @Query("DELETE FROM premiumdeck_behavior_events")
    suspend fun clearEvents()

    @Query("DELETE FROM premiumdeck_replay_buffer")
    suspend fun clearReplay()
}

@Database(
    entities = [
        BehaviorEventEntity::class,
        UserPreferenceProfileEntity::class,
        ReplayBufferEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PremiumDeckPersonalizationDatabase : RoomDatabase() {
    abstract fun dao(): PremiumDeckPersonalizationDao

    companion object {
        @Volatile private var instance: PremiumDeckPersonalizationDatabase? = null

        fun get(context: Context): PremiumDeckPersonalizationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PremiumDeckPersonalizationDatabase::class.java,
                    "premiumdeck_personalization.db",
                ).build().also { instance = it }
            }
    }
}

class RoomUserPreferenceStore(
    context: Context,
) : UserPreferenceStore {
    private val dao = PremiumDeckPersonalizationDatabase.get(context).dao()

    override suspend fun appendEvent(event: BehaviorEvent) {
        dao.insertEvent(event.toEntity())
    }

    override suspend fun recentEvents(limit: Int): List<BehaviorEvent> =
        dao.recentEvents(limit.coerceAtLeast(1)).map { it.toModel() }

    override suspend fun loadProfile(): UserPreferenceProfile =
        dao.profile()?.toModel() ?: UserPreferenceProfile()

    override suspend fun saveProfile(profile: UserPreferenceProfile) {
        dao.upsertProfile(profile.normalized().toEntity())
    }

    override suspend fun loadReplayBuffer(): ReplayBuffer =
        ReplayBuffer(dao.replayEvents().mapNotNull { it.toModel() })

    override suspend fun appendReplayEvent(event: BehaviorEvent) {
        dao.insertReplayEvent(ReplayBufferEventEntity(event.id, event.occurredAtMillis, event.toJson().toString()))
        dao.trimReplayEvents()
    }

    override suspend fun prune(nowMillis: Long) {
        dao.pruneEvents(nowMillis - 90L * 86_400_000L)
        dao.trimReplayEvents()
    }

    override suspend fun reset() {
        dao.clearEvents()
        dao.clearReplay()
        dao.clearProfiles()
    }
}

private fun BehaviorEvent.toEntity(): BehaviorEventEntity =
    BehaviorEventEntity(
        id = id,
        type = type.name,
        itemId = itemId,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        source = source.name,
        occurredAtMillis = occurredAtMillis,
        listenDurationMillis = listenDurationMillis,
        skipPositionSeconds = skipPositionSeconds,
        query = query,
        timeOfDayBucket = timeOfDayBucket.name,
        dayOfWeekBucket = dayOfWeekBucket,
        metadataJson = JSONObject(metadata).toString(),
    )

private fun BehaviorEventEntity.toModel(): BehaviorEvent =
    BehaviorEvent(
        id = id,
        type = enumValue(type, BehaviorEventType.TrackStarted),
        itemId = itemId,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        source = enumValue(source, CandidateSource.LocalLibrary),
        occurredAtMillis = occurredAtMillis,
        listenDurationMillis = listenDurationMillis,
        skipPositionSeconds = skipPositionSeconds,
        query = query,
        timeOfDayBucket = enumValue(timeOfDayBucket, TimeOfDayBucket.Day),
        dayOfWeekBucket = dayOfWeekBucket,
        metadata = JSONObject(metadataJson.ifBlank { "{}" }).toStringMap(),
    )

private fun UserPreferenceProfile.toEntity(): UserPreferenceProfileEntity =
    UserPreferenceProfileEntity(
        vectorJson = JSONArray(userVector).toString(),
        genreAffinityJson = JSONObject(genreAffinity).toString(),
        artistAffinityJson = JSONObject(artistAffinity).toString(),
        itemAffinityJson = JSONObject(itemAffinity).toString(),
        completionAffinityJson = JSONObject(completionRateAffinity).toString(),
        dislikedIdsJson = JSONArray(dislikedItemIds.toList()).toString(),
        recentIdsJson = JSONArray(recentlyPlayedItemIds).toString(),
        positiveIdsJson = JSONArray(positiveItemIds.toList()).toString(),
        timeOfDayAffinityJson = JSONObject(timeOfDayAffinity.mapKeys { it.key.name }).toString(),
        updatedAtMillis = updatedAtMillis,
    )

private fun UserPreferenceProfileEntity.toModel(): UserPreferenceProfile =
    UserPreferenceProfile(
        userVector = JSONArray(vectorJson.ifBlank { "[]" }).toFloatList().normalizedVector(PremiumDeckTinyRecConfig.UserVectorDim),
        genreAffinity = JSONObject(genreAffinityJson.ifBlank { "{}" }).toFloatMap(),
        artistAffinity = JSONObject(artistAffinityJson.ifBlank { "{}" }).toFloatMap(),
        itemAffinity = JSONObject(itemAffinityJson.ifBlank { "{}" }).toFloatMap(),
        completionRateAffinity = JSONObject(completionAffinityJson.ifBlank { "{}" }).toFloatMap(),
        dislikedItemIds = JSONArray(dislikedIdsJson.ifBlank { "[]" }).toStringSet(),
        recentlyPlayedItemIds = JSONArray(recentIdsJson.ifBlank { "[]" }).toStringList(),
        positiveItemIds = JSONArray(positiveIdsJson.ifBlank { "[]" }).toStringSet(),
        timeOfDayAffinity = JSONObject(timeOfDayAffinityJson.ifBlank { "{}" }).toFloatMap()
            .mapNotNull { (key, value) -> enumValueOrNull<TimeOfDayBucket>(key)?.let { it to value } }
            .toMap(),
        updatedAtMillis = updatedAtMillis,
    ).normalized()

private fun BehaviorEvent.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("itemId", itemId)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("genre", genre)
        .put("source", source.name)
        .put("occurredAtMillis", occurredAtMillis)
        .put("listenDurationMillis", listenDurationMillis)
        .put("skipPositionSeconds", skipPositionSeconds)
        .put("query", query)
        .put("timeOfDayBucket", timeOfDayBucket.name)
        .put("dayOfWeekBucket", dayOfWeekBucket)
        .put("metadata", JSONObject(metadata))

private fun ReplayBufferEventEntity.toModel(): BehaviorEvent? =
    runCatching {
        val json = JSONObject(eventJson)
        BehaviorEvent(
            id = json.optString("id", id),
            type = enumValue(json.optString("type"), BehaviorEventType.TrackStarted),
            itemId = json.optString("itemId"),
            title = json.optString("title"),
            artist = json.optString("artist"),
            album = json.optString("album"),
            genre = json.optString("genre"),
            source = enumValue(json.optString("source"), CandidateSource.LocalLibrary),
            occurredAtMillis = json.optLong("occurredAtMillis", occurredAtMillis),
            listenDurationMillis = json.optLong("listenDurationMillis", 0L),
            skipPositionSeconds = json.optInt("skipPositionSeconds", 0),
            query = json.optString("query"),
            timeOfDayBucket = enumValue(json.optString("timeOfDayBucket"), TimeOfDayBucket.Day),
            dayOfWeekBucket = json.optInt("dayOfWeekBucket", 1),
            metadata = json.optJSONObject("metadata")?.toStringMap().orEmpty(),
        )
    }.getOrNull()

private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
    enumValues<T>().firstOrNull { it.name == raw }

private fun JSONObject.toFloatMap(): Map<String, Float> =
    keys().asSequence().associateWith { key -> optDouble(key, 0.0).toFloat() }

private fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { key -> optString(key) }

private fun JSONArray.toStringList(): List<String> =
    List(length()) { index -> optString(index) }.filter { it.isNotBlank() }

private fun JSONArray.toStringSet(): Set<String> = toStringList().toSet()

private fun JSONArray.toFloatList(): List<Float> =
    List(length()) { index -> optDouble(index, 0.0).toFloat() }
