package com.pulsedeck.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val STREAM_DISCOVERY_CACHE_VERSION = 4
private const val STREAM_DISCOVERY_CACHE_RESULT_LIMIT = 140

internal fun loadStreamPlayHistory(context: Context): List<StreamPlayHistoryItem> {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE).getString(PREF_STREAM_PLAY_HISTORY, "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    StreamPlayHistoryItem(
                        sourceId = item.optString("sourceId"),
                        url = item.optString("url"),
                        title = item.optString("title", PREMIUMDECK_STREAM_TITLE),
                        author = item.optString("author", PREMIUMDECK_SOURCE_NAME),
                        thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        durationMillis = item.optLong("durationMillis", 0L),
                        playedAtMillis = item.optLong("playedAtMillis", 0L),
                    ),
                )
            }
        }.filter { it.url.isNotBlank() || it.sourceId.isNotBlank() }
            .sortedByDescending { it.playedAtMillis }
            .take(200)
    }.getOrDefault(emptyList())
}

internal fun saveStreamPlayHistory(context: Context, history: List<StreamPlayHistoryItem>) {
    val array = JSONArray()
    history.take(200).forEach { item ->
        array.put(
            JSONObject()
                .put("sourceId", item.sourceId)
                .put("url", item.url)
                .put("title", item.title)
                .put("author", item.author)
                .put("thumbnailUrl", item.thumbnailUrl.orEmpty())
                .put("durationMillis", item.durationMillis)
                .put("playedAtMillis", item.playedAtMillis),
        )
    }
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_STREAM_PLAY_HISTORY, array.toString())
        .apply()
}

internal fun YouTubeSource.toPlayHistoryItem(playedAtMillis: Long): StreamPlayHistoryItem =
    StreamPlayHistoryItem(
        sourceId = id,
        url = url,
        title = title,
        author = author,
        thumbnailUrl = bestThumbnailUrl(),
        durationMillis = durationMillis,
        playedAtMillis = playedAtMillis,
    )

internal fun StreamPlayHistoryItem.toYouTubeSource(): YouTubeSource? {
    val normalizedUrl = normalizeYouTubeSearchUrl(url) ?: url.takeIf { it.startsWith("http", ignoreCase = true) } ?: return null
    val detection = detectYouTubeSource(normalizedUrl)?.takeIf { it.kind == YouTubeSourceKind.Video }
    return YouTubeSource(
        id = sourceId.ifBlank { detection?.let { "video-${it.sourceId}" } ?: "history-${normalizedUrl.hashCode()}" },
        url = detection?.normalizedUrl ?: normalizedUrl,
        kind = YouTubeSourceKind.Video,
        title = title.ifBlank { PREMIUMDECK_STREAM_TITLE },
        author = author.ifBlank { PREMIUMDECK_SOURCE_NAME },
        thumbnailUrl = normalizeYouTubeThumbnailUrl(thumbnailUrl) ?: detection?.sourceId?.let(::youtubeThumbnailUrlForVideoId),
        durationMillis = durationMillis,
        lastPlayedMillis = playedAtMillis,
        reviewState = YouTubeReviewState.Accepted,
    )
}

internal fun loadStreamDiscoverySnapshot(context: Context): StreamDiscoverySnapshot {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE).getString(PREF_STREAM_DISCOVERY_CACHE, "{}").orEmpty()
    return runCatching {
        val json = JSONObject(raw.ifBlank { "{}" })
        if (json.optInt("version", 1) < STREAM_DISCOVERY_CACHE_VERSION) return@runCatching StreamDiscoverySnapshot()
        StreamDiscoverySnapshot(
            savedMillis = json.optLong("savedMillis", 0L),
            results = parseCachedYouTubeSearchResults(json.optJSONArray("results")),
        )
    }.getOrDefault(StreamDiscoverySnapshot())
}

internal fun saveStreamDiscoverySnapshot(context: Context, snapshot: StreamDiscoverySnapshot) {
    val json = JSONObject()
        .put("version", STREAM_DISCOVERY_CACHE_VERSION)
        .put("savedMillis", snapshot.savedMillis)
        .put("results", snapshot.results.take(STREAM_DISCOVERY_CACHE_RESULT_LIMIT).toYouTubeSearchJsonArray())
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_STREAM_DISCOVERY_CACHE, json.toString())
        .apply()
}

internal fun loadFollowedStreamArtists(context: Context): List<FollowedStreamArtist> {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE).getString(PREF_FOLLOWED_STREAM_ARTISTS, "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").cleanStreamArtistName()
                val key = item.optString("key").ifBlank { name.normalizedSearchText() }
                val officialName = item.optString("officialName").cleanStreamArtistName().ifBlank { name }
                val officialKey = item.optString("officialKey").ifBlank { officialName.normalizedSearchText().ifBlank { key } }
                val officialChannelUrl = item.optString("officialChannelUrl").ifBlank { item.optString("channelUrl") }
                val officialChannelKey = item.optString("officialChannelKey").ifBlank { officialChannelUrl.youtubeChannelIdentityKey() }
                if (name.isNotBlank() && key.isNotBlank()) {
                    add(
                        FollowedStreamArtist(
                            name = name,
                            key = key,
                            followedAtMillis = item.optLong("followedAtMillis", 0L).takeIf { it > 0L } ?: System.currentTimeMillis(),
                            officialName = officialName,
                            officialKey = officialKey,
                            officialChannelUrl = officialChannelUrl,
                            officialChannelKey = officialChannelKey,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())
        .distinctBy { it.key }
        .sortedBy { it.name.lowercase(Locale.US) }
}

internal fun saveFollowedStreamArtists(context: Context, artists: List<FollowedStreamArtist>) {
    val array = JSONArray()
    artists.distinctBy { it.key }.forEach { artist ->
        array.put(
            JSONObject()
                .put("name", artist.name)
                .put("key", artist.key)
                .put("followedAtMillis", artist.followedAtMillis)
                .put("officialName", artist.officialName)
                .put("officialKey", artist.officialKey)
                .put("officialChannelUrl", artist.officialChannelUrl)
                .put("officialChannelKey", artist.officialChannelKey),
        )
    }
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_FOLLOWED_STREAM_ARTISTS, array.toString())
        .apply()
}

internal fun loadStreamNewReleaseSnapshot(context: Context): StreamNewReleaseSnapshot {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE).getString(PREF_STREAM_NEW_RELEASES_CACHE, "{}").orEmpty()
    return runCatching {
        val json = JSONObject(raw.ifBlank { "{}" })
        StreamNewReleaseSnapshot(
            savedMillis = json.optLong("savedMillis", 0L),
            followedArtistKeys = json.optJSONArray("followedArtistKeys").toStringSet(),
            results = parseCachedYouTubeSearchResults(json.optJSONArray("results")),
        )
    }.getOrDefault(StreamNewReleaseSnapshot())
}

internal fun saveStreamNewReleaseSnapshot(context: Context, snapshot: StreamNewReleaseSnapshot) {
    val json = JSONObject()
        .put("savedMillis", snapshot.savedMillis)
        .put("followedArtistKeys", JSONArray(snapshot.followedArtistKeys.toList()))
        .put("results", snapshot.results.take(80).toYouTubeSearchJsonArray())
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_STREAM_NEW_RELEASES_CACHE, json.toString())
        .apply()
}

internal fun loadStreamReleaseNotificationReadIds(context: Context): Set<String> {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_STREAM_RELEASE_NOTIFICATION_READ_IDS, "[]")
        .orEmpty()
    return runCatching {
        JSONArray(raw.ifBlank { "[]" }).toStringSet()
    }.getOrDefault(emptySet())
}

internal fun saveStreamReleaseNotificationReadIds(context: Context, ids: Set<String>) {
    val array = JSONArray()
    ids.filter { it.isNotBlank() }.take(240).forEach(array::put)
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_STREAM_RELEASE_NOTIFICATION_READ_IDS, array.toString())
        .apply()
}

private fun JSONArray?.toStringSet(): Set<String> =
    if (this == null) {
        emptySet()
    } else {
        buildSet {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

internal fun StreamDiscoverySnapshot.isStale(nowMillis: Long = System.currentTimeMillis()): Boolean =
    results.isEmpty() || savedMillis <= 0L || nowMillis - savedMillis > STREAM_DISCOVERY_TTL_MILLIS

internal fun StreamNewReleaseSnapshot.isStaleFor(artists: List<FollowedStreamArtist>, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val keys = artists.map { it.key }.filter { it.isNotBlank() }.toSet()
    if (keys.isEmpty()) return false
    return savedMillis <= 0L ||
        nowMillis - savedMillis > STREAM_NEW_RELEASE_TTL_MILLIS ||
        keys != followedArtistKeys
}

