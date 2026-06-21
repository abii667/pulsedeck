package com.pulsedeck.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
internal fun youtubeSearchCacheKey(query: String): String =
    query.normalizedSearchText().take(120)

internal fun loadYouTubeSearchCache(context: Context, query: String): YouTubeSearchResponse? {
    val key = youtubeSearchCacheKey(query)
    if (key.length < 2) return null
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE).getString(PREF_YOUTUBE_SEARCH_CACHE, "{}").orEmpty()
    return runCatching {
        val root = JSONObject(raw)
        val item = root.optJSONObject(key) ?: return@runCatching null
        val savedMillis = item.optLong("savedMillis", 0L)
        if (System.currentTimeMillis() - savedMillis > 12L * 60L * 60L * 1000L) return@runCatching null
        YouTubeSearchResponse(
            results = parseCachedYouTubeSearchResults(item.optJSONArray("results")),
            suggestions = parseCachedYouTubeSuggestions(item.optJSONArray("suggestions")),
        ).takeIf { it.results.isNotEmpty() || it.suggestions.isNotEmpty() }
    }.getOrNull()
}

internal fun saveYouTubeSearchCache(context: Context, query: String, response: YouTubeSearchResponse) {
    val key = youtubeSearchCacheKey(query)
    if (key.length < 2) return
    runCatching {
        val prefs = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        val root = JSONObject(prefs.getString(PREF_YOUTUBE_SEARCH_CACHE, "{}").orEmpty().ifBlank { "{}" })
        root.put(
            key,
            JSONObject()
                .put("savedMillis", System.currentTimeMillis())
                .put("results", response.results.take(40).toYouTubeSearchJsonArray())
                .put("suggestions", response.suggestions.take(8).toYouTubeSuggestionJsonArray()),
        )
        val keys = buildList {
            val iterator = root.keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        if (keys.size > 24) {
            keys.sortedBy { root.optJSONObject(it)?.optLong("savedMillis", 0L) ?: 0L }
                .take(keys.size - 24)
                .forEach { root.remove(it) }
        }
        prefs.edit().putString(PREF_YOUTUBE_SEARCH_CACHE, root.toString()).apply()
    }
}

internal fun parseCachedYouTubeSuggestions(array: JSONArray?): List<YouTubeSearchSuggestion> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf { it.length >= 2 }?.let { add(YouTubeSearchSuggestion(it)) }
        }
    }

internal fun parseCachedYouTubeSearchResults(array: JSONArray?): List<YouTubeSearchResult> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val sourceJson = item.optJSONObject("source") ?: continue
            val kind = runCatching { YouTubeSourceKind.valueOf(sourceJson.optString("kind")) }.getOrDefault(YouTubeSourceKind.Video)
            val quality = runCatching { YouTubeQuality.valueOf(sourceJson.optString("quality")) }.getOrDefault(YouTubeQuality.High)
            val status = runCatching { YouTubeSourceStatus.valueOf(sourceJson.optString("status")) }.getOrDefault(YouTubeSourceStatus.StreamReady)
            add(
                YouTubeSearchResult(
                    source = YouTubeSource(
                        id = sourceJson.optString("id"),
                        url = sourceJson.optString("url"),
                        kind = kind,
                        title = sourceJson.optString("title", "PremiumDeck result"),
                        author = sourceJson.optString("author", PREMIUMDECK_SOURCE_NAME),
                        thumbnailUrl = sourceJson.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        channelTitle = sourceJson.optString("channelTitle").ifBlank { sourceJson.optString("author", PREMIUMDECK_SOURCE_NAME) },
                        channelUrl = sourceJson.optString("channelUrl"),
                        channelVerified = sourceJson.optBoolean("channelVerified", false),
                        albumTitleHint = sourceJson.optString("albumTitleHint"),
                        albumTrackNumberHint = sourceJson.optInt("albumTrackNumberHint", 0),
                        albumTrackTotalHint = sourceJson.optInt("albumTrackTotalHint", 0),
                        albumYearHint = sourceJson.optInt("albumYearHint", 0),
                        durationMillis = sourceJson.optLong("durationMillis", item.optLong("durationMillis", 0L)),
                        quality = quality,
                        isPodcast = sourceJson.optBoolean("isPodcast", false),
                        status = status,
                        reviewState = YouTubeReviewState.Accepted,
                    ),
                    durationMillis = item.optLong("durationMillis", 0L),
                    uploadedDate = item.optString("uploadedDate"),
                    views = item.optLong("views", 0L),
                    videoId = item.optString("videoId"),
                    score = item.optInt("score", 0),
                    cachedMillis = item.optLong("cachedMillis", 0L),
                    matchReason = item.optString("matchReason").takeIf { it.isNotBlank() },
                ),
            )
        }
    }

internal fun List<YouTubeSearchSuggestion>.toYouTubeSuggestionJsonArray(): JSONArray =
    JSONArray().apply { forEach { put(it.text) } }

internal fun List<YouTubeSearchResult>.toYouTubeSearchJsonArray(): JSONArray =
    JSONArray().apply {
        forEach { result ->
            put(
                JSONObject()
                    .put(
                        "source",
                        JSONObject()
                            .put("id", result.source.id)
                            .put("url", result.source.url)
                            .put("kind", result.source.kind.name)
                            .put("title", result.source.title)
                            .put("author", result.source.author)
                            .put("thumbnailUrl", result.source.thumbnailUrl.orEmpty())
                            .put("channelTitle", result.source.channelTitle)
                            .put("channelUrl", result.source.channelUrl)
                            .put("channelVerified", result.source.channelVerified)
                            .put("albumTitleHint", result.source.albumTitleHint)
                            .put("albumTrackNumberHint", result.source.albumTrackNumberHint)
                            .put("albumTrackTotalHint", result.source.albumTrackTotalHint)
                            .put("albumYearHint", result.source.albumYearHint)
                            .put("durationMillis", result.source.durationMillis.takeIf { it > 0L } ?: result.durationMillis)
                            .put("quality", result.source.quality.name)
                            .put("status", result.source.status.name)
                            .put("isPodcast", result.source.isPodcast),
                    )
                    .put("durationMillis", result.durationMillis)
                    .put("uploadedDate", result.uploadedDate)
                    .put("views", result.views)
                    .put("videoId", result.videoId)
                    .put("score", result.score)
                    .put("cachedMillis", result.cachedMillis)
                    .put("matchReason", result.matchReason.orEmpty()),
            )
        }
    }

