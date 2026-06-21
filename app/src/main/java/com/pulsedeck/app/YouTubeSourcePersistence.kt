package com.pulsedeck.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
internal fun JSONObject.toYouTubeSourceOrNull(): YouTubeSource? {
    val id = optString("id").trim()
    val url = optString("url").trim()
    if (id.isBlank() || url.isBlank()) return null
    val kind = runCatching { YouTubeSourceKind.valueOf(optString("kind")) }.getOrDefault(YouTubeSourceKind.Video)
    val quality = runCatching { YouTubeQuality.valueOf(optString("quality")) }.getOrDefault(YouTubeQuality.High)
    val status = runCatching { YouTubeSourceStatus.valueOf(optString("status")) }.getOrDefault(YouTubeSourceStatus.StreamReady)
    val downloadState = runCatching { YouTubeDownloadState.valueOf(optString("downloadState")) }.getOrDefault(YouTubeDownloadState.None)
    val reviewState = runCatching { YouTubeReviewState.valueOf(optString("reviewState")) }.getOrDefault(YouTubeReviewState.Accepted)
    return YouTubeSource(
        id = id,
        url = url,
        kind = kind,
        title = optString("title", "Untitled PremiumDeck source"),
        author = optString("author", PREMIUMDECK_SOURCE_NAME),
        thumbnailUrl = optString("thumbnailUrl").takeIf { it.isNotBlank() },
        channelTitle = optString("channelTitle").ifBlank { optString("author", PREMIUMDECK_SOURCE_NAME) },
        channelUrl = optString("channelUrl"),
        channelVerified = optBoolean("channelVerified", false),
        albumTitleHint = optString("albumTitleHint"),
        albumTrackNumberHint = optInt("albumTrackNumberHint", 0),
        albumTrackTotalHint = optInt("albumTrackTotalHint", 0),
        albumYearHint = optInt("albumYearHint", 0),
        durationMillis = optLong("durationMillis", 0L),
        quality = quality,
        playCount = optInt("playCount", 0),
        addedMillis = optLong("addedMillis", System.currentTimeMillis()),
        lastPlayedMillis = optLong("lastPlayedMillis", 0L),
        reaction = runCatching { YouTubeReaction.valueOf(optString("reaction")) }.getOrDefault(YouTubeReaction.Neutral),
        bookmarked = optBoolean("bookmarked", false),
        isPodcast = optBoolean("isPodcast", false),
        status = status,
        cachedUri = optString("cachedUri").takeIf { it.isNotBlank() },
        cachedMillis = optLong("cachedMillis", 0L),
        downloadedUri = optString("downloadedUri").takeIf { it.isNotBlank() },
        downloadState = downloadState,
        downloadProgress = optInt("downloadProgress", 0),
        playbackPositionMillis = optLong("playbackPositionMillis", 0L),
        chapters = parseYouTubeChapters(optJSONArray("chapters")),
        sponsorSegments = parseSponsorSegments(optJSONArray("sponsorSegments")),
        neverPromptCache = optBoolean("neverPromptCache", false),
        skipSegmentsEnabled = optBoolean("skipSegmentsEnabled", true),
        trimSilenceOnDownload = optBoolean("trimSilenceOnDownload", false),
        reviewState = reviewState,
    )
}

internal fun YouTubeSource.toYouTubeSourceJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("url", url)
        .put("kind", kind.name)
        .put("title", title)
        .put("author", author)
        .put("thumbnailUrl", thumbnailUrl.orEmpty())
        .put("channelTitle", channelTitle)
        .put("channelUrl", channelUrl)
        .put("channelVerified", channelVerified)
        .put("albumTitleHint", albumTitleHint)
        .put("albumTrackNumberHint", albumTrackNumberHint)
        .put("albumTrackTotalHint", albumTrackTotalHint)
        .put("albumYearHint", albumYearHint)
        .put("durationMillis", durationMillis)
        .put("quality", quality.name)
        .put("playCount", playCount)
        .put("addedMillis", addedMillis)
        .put("lastPlayedMillis", lastPlayedMillis)
        .put("reaction", reaction.name)
        .put("bookmarked", bookmarked)
        .put("isPodcast", isPodcast)
        .put("status", status.name)
        .put("cachedUri", cachedUri.orEmpty())
        .put("cachedMillis", cachedMillis)
        .put("downloadedUri", downloadedUri.orEmpty())
        .put("downloadState", downloadState.name)
        .put("downloadProgress", downloadProgress)
        .put("playbackPositionMillis", playbackPositionMillis)
        .put("chapters", chapters.toChapterJsonArray())
        .put("sponsorSegments", sponsorSegments.toSegmentJsonArray())
        .put("neverPromptCache", neverPromptCache)
        .put("skipSegmentsEnabled", skipSegmentsEnabled)
        .put("trimSilenceOnDownload", trimSilenceOnDownload)
        .put("reviewState", reviewState.name)

internal fun loadYouTubeSources(context: Context): List<YouTubeSource> {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE).getString(PREF_YOUTUBE_SOURCES, "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val kind = runCatching { YouTubeSourceKind.valueOf(item.optString("kind")) }.getOrDefault(YouTubeSourceKind.Unknown)
                val quality = runCatching { YouTubeQuality.valueOf(item.optString("quality")) }.getOrDefault(YouTubeQuality.High)
                val status = runCatching { YouTubeSourceStatus.valueOf(item.optString("status")) }.getOrDefault(YouTubeSourceStatus.StreamReady)
                val downloadState = runCatching { YouTubeDownloadState.valueOf(item.optString("downloadState")) }.getOrDefault(YouTubeDownloadState.None)
                val reviewState = runCatching { YouTubeReviewState.valueOf(item.optString("reviewState")) }.getOrDefault(YouTubeReviewState.Accepted)
                add(
                    YouTubeSource(
                        id = item.optString("id"),
                        url = item.optString("url"),
                        kind = kind,
                        title = item.optString("title", "Untitled PremiumDeck source"),
                        author = item.optString("author", PREMIUMDECK_SOURCE_NAME),
                        thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        channelTitle = item.optString("channelTitle").ifBlank { item.optString("author", PREMIUMDECK_SOURCE_NAME) },
                        channelUrl = item.optString("channelUrl"),
                        channelVerified = item.optBoolean("channelVerified", false),
                        albumTitleHint = item.optString("albumTitleHint"),
                        albumTrackNumberHint = item.optInt("albumTrackNumberHint", 0),
                        albumTrackTotalHint = item.optInt("albumTrackTotalHint", 0),
                        albumYearHint = item.optInt("albumYearHint", 0),
                        durationMillis = item.optLong("durationMillis", 0L),
                        quality = quality,
                        playCount = item.optInt("playCount", 0),
                        addedMillis = item.optLong("addedMillis", System.currentTimeMillis()),
                        lastPlayedMillis = item.optLong("lastPlayedMillis", 0L),
                        reaction = runCatching { YouTubeReaction.valueOf(item.optString("reaction")) }.getOrDefault(YouTubeReaction.Neutral),
                        bookmarked = item.optBoolean("bookmarked", false),
                        isPodcast = item.optBoolean("isPodcast", false),
                        status = status,
                        cachedUri = item.optString("cachedUri").takeIf { it.isNotBlank() },
                        cachedMillis = item.optLong("cachedMillis", 0L),
                        downloadedUri = item.optString("downloadedUri").takeIf { it.isNotBlank() },
                        downloadState = downloadState,
                        downloadProgress = item.optInt("downloadProgress", 0),
                        playbackPositionMillis = item.optLong("playbackPositionMillis", 0L),
                        chapters = parseYouTubeChapters(item.optJSONArray("chapters")),
                        sponsorSegments = parseSponsorSegments(item.optJSONArray("sponsorSegments")),
                        neverPromptCache = item.optBoolean("neverPromptCache", false),
                        skipSegmentsEnabled = item.optBoolean("skipSegmentsEnabled", true),
                        trimSilenceOnDownload = item.optBoolean("trimSilenceOnDownload", false),
                        reviewState = reviewState,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun saveYouTubeSources(context: Context, sources: List<YouTubeSource>) {
    val array = JSONArray()
    sources.forEach { source ->
        array.put(
            JSONObject()
                .put("id", source.id)
                .put("url", source.url)
                .put("kind", source.kind.name)
                .put("title", source.title)
                .put("author", source.author)
                .put("thumbnailUrl", source.thumbnailUrl.orEmpty())
                .put("channelTitle", source.channelTitle)
                .put("channelUrl", source.channelUrl)
                .put("channelVerified", source.channelVerified)
                .put("albumTitleHint", source.albumTitleHint)
                .put("albumTrackNumberHint", source.albumTrackNumberHint)
                .put("albumTrackTotalHint", source.albumTrackTotalHint)
                .put("albumYearHint", source.albumYearHint)
                .put("durationMillis", source.durationMillis)
                .put("quality", source.quality.name)
                .put("playCount", source.playCount)
                .put("addedMillis", source.addedMillis)
                .put("lastPlayedMillis", source.lastPlayedMillis)
                .put("reaction", source.reaction.name)
                .put("bookmarked", source.bookmarked)
                .put("isPodcast", source.isPodcast)
                .put("status", source.status.name)
                .put("cachedUri", source.cachedUri.orEmpty())
                .put("cachedMillis", source.cachedMillis)
                .put("downloadedUri", source.downloadedUri.orEmpty())
                .put("downloadState", source.downloadState.name)
                .put("downloadProgress", source.downloadProgress)
                .put("playbackPositionMillis", source.playbackPositionMillis)
                .put("chapters", source.chapters.toChapterJsonArray())
                .put("sponsorSegments", source.sponsorSegments.toSegmentJsonArray())
                .put("neverPromptCache", source.neverPromptCache)
                .put("skipSegmentsEnabled", source.skipSegmentsEnabled)
                .put("trimSilenceOnDownload", source.trimSilenceOnDownload)
                .put("reviewState", source.reviewState.name),
        )
    }
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_YOUTUBE_SOURCES, array.toString())
        .apply()
}

internal fun loadYouTubePlaylists(context: Context): List<YouTubePlaylist> {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE).getString(PREF_YOUTUBE_PLAYLISTS, "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val sourceIds = item.optJSONArray("sourceIds")?.let { ids ->
                    buildList {
                        for (idIndex in 0 until ids.length()) {
                            ids.optString(idIndex).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
                add(
                    YouTubePlaylist(
                        id = item.optString("id").ifBlank { newYouTubePlaylistId() },
                        title = item.optString("title", "Untitled Stream List"),
                        description = item.optString("description"),
                        accentColor = item.optInt("accentColor", 0xFF4A82FF.toInt()),
                        sourceIds = sourceIds.distinct(),
                        createdMillis = item.optLong("createdMillis", System.currentTimeMillis()),
                        updatedMillis = item.optLong("updatedMillis", System.currentTimeMillis()),
                        origin = runCatching {
                            YouTubePlaylistOrigin.valueOf(item.optString("origin", YouTubePlaylistOrigin.Manual.name))
                        }.getOrDefault(YouTubePlaylistOrigin.Manual),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun saveYouTubePlaylists(context: Context, playlists: List<YouTubePlaylist>) {
    val array = JSONArray()
    playlists.forEach { playlist ->
        array.put(
            JSONObject()
                .put("id", playlist.id)
                .put("title", playlist.title)
                .put("description", playlist.description)
                .put("accentColor", playlist.accentColor)
                .put("sourceIds", JSONArray().apply { playlist.sourceIds.forEach { put(it) } })
                .put("createdMillis", playlist.createdMillis)
                .put("updatedMillis", playlist.updatedMillis)
                .put("origin", playlist.origin.name),
        )
    }
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_YOUTUBE_PLAYLISTS, array.toString())
        .apply()
}

internal fun parseYouTubeChapters(array: JSONArray?): List<YouTubeChapter> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                YouTubeChapter(
                    title = item.optString("title", "Chapter ${index + 1}"),
                    startMillis = item.optLong("startMillis", 0L),
                    endMillis = item.optLong("endMillis", 0L),
                ),
            )
        }
    }

internal fun parseSponsorSegments(array: JSONArray?): List<SponsorBlockSegment> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                SponsorBlockSegment(
                    category = item.optString("category", "sponsor"),
                    startMillis = item.optLong("startMillis", 0L),
                    endMillis = item.optLong("endMillis", 0L),
                ),
            )
        }
    }

private fun List<YouTubeChapter>.toChapterJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { chapter ->
        array.put(JSONObject().put("title", chapter.title).put("startMillis", chapter.startMillis).put("endMillis", chapter.endMillis))
    }
    return array
}

private fun List<SponsorBlockSegment>.toSegmentJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { segment ->
        array.put(JSONObject().put("category", segment.category).put("startMillis", segment.startMillis).put("endMillis", segment.endMillis))
    }
    return array
}

