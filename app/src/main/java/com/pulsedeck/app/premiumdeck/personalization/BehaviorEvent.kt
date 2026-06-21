package com.pulsedeck.app.premiumdeck.personalization

import java.util.Calendar
import java.util.UUID

enum class BehaviorEventType {
    TrackStarted,
    TrackCompleted,
    TrackSkipped,
    SkipUnder30Seconds,
    Skip30To60Seconds,
    LikeFavorite,
    DislikeHide,
    SearchQuerySubmitted,
    SearchResultClicked,
    PlaylistCreated,
    TrackAddedToPlaylist,
    TrackRemovedFromPlaylist,
    AlbumOpened,
    ArtistOpened,
    ArtistFollowed,
    ArtistUnfollowed,
    RepeatPlay,
    ShufflePlay,
}

enum class CandidateSource {
    LocalLibrary,
    PremiumDeck,
}

data class BehaviorEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: BehaviorEventType,
    val itemId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val source: CandidateSource = CandidateSource.LocalLibrary,
    val occurredAtMillis: Long = System.currentTimeMillis(),
    val listenDurationMillis: Long = 0L,
    val skipPositionSeconds: Int = 0,
    val query: String = "",
    val timeOfDayBucket: TimeOfDayBucket = TimeOfDayBucket.fromMillis(occurredAtMillis),
    val dayOfWeekBucket: Int = dayOfWeekBucket(occurredAtMillis),
    val metadata: Map<String, String> = emptyMap(),
) {
    fun normalizedForWeight(): BehaviorEvent =
        when {
            type == BehaviorEventType.TrackSkipped && skipPositionSeconds in 1 until 30 -> copy(type = BehaviorEventType.SkipUnder30Seconds)
            type == BehaviorEventType.TrackSkipped && skipPositionSeconds in 30..59 -> copy(type = BehaviorEventType.Skip30To60Seconds)
            else -> this
        }
}

enum class TimeOfDayBucket {
    Morning,
    Day,
    Evening,
    Night;

    companion object {
        fun fromMillis(millis: Long): TimeOfDayBucket {
            val hour = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..10 -> Morning
                in 11..16 -> Day
                in 17..21 -> Evening
                else -> Night
            }
        }
    }
}

fun dayOfWeekBucket(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
