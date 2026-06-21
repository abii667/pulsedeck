package com.pulsedeck.app

import org.json.JSONObject
import java.util.Locale

internal fun scoreYouTubeMusicResult(query: String, source: YouTubeSource, durationMillis: Long, item: JSONObject): Int {
    val normalizedQuery = query.normalizedSearchText()
    val title = source.title.normalizedSearchText()
    val author = source.author.normalizedSearchText()
    val haystack = "$title $author"
    val tokens = normalizedQuery.split(" ").filter { it.length > 1 }
    var score = 0

    if (title == normalizedQuery) score += 180
    if (author == normalizedQuery) score += 70
    if (title.contains(normalizedQuery) && normalizedQuery.length >= 3) score += 120
    if (tokens.isNotEmpty() && tokens.all { haystack.contains(it) }) score += 90
    score += tokens.count { title.contains(it) } * 18
    score += tokens.count { author.contains(it) } * 10

    val rawTitle = source.title.lowercase(Locale.US)
    val rawAuthor = source.author.lowercase(Locale.US)
    val musicBoosts = listOf("official audio", "official music video", "audio", "lyrics", "lyric video", "visualizer", "topic", "vevo", "music")
    score += musicBoosts.count { rawTitle.contains(it) || rawAuthor.contains(it) } * 18

    val durationMinutes = durationMillis / 60_000L
    score += when {
        durationMillis <= 0L -> 0
        durationMinutes in 2..7 -> 85
        durationMinutes in 8..12 -> 20
        durationMinutes in 13..30 -> -35
        durationMinutes > 30 -> -90
        else -> -20
    }
    if (item.optBoolean("isShort", false)) score -= 120
    if (item.optBoolean("livestream", false)) score -= 100

    val broadVideoPenalties = listOf("reaction", "tutorial", "lesson", "cover dance", "karaoke", "instrumental lesson", "review", "podcast", "interview", "mix", "full album", "hour", "live stream")
    score -= broadVideoPenalties.count { rawTitle.contains(it) } * 34
    if (rawTitle.contains("shorts")) score -= 70
    return score
}

internal fun musicMatchReason(score: Int, durationMillis: Long, source: YouTubeSource): String {
    val title = source.title.lowercase(Locale.US)
    return when {
        title.contains("official audio") -> "Official audio  |  ${formatDuration(durationMillis)}"
        title.contains("official music video") -> "Official music video  |  ${formatDuration(durationMillis)}"
        title.contains("lyrics") || title.contains("lyric video") -> "Lyric result  |  ${formatDuration(durationMillis)}"
        durationMillis in 120_000L..480_000L -> "Music-length match  |  ${formatDuration(durationMillis)}"
        score > 120 -> "Strong music match"
        else -> "PremiumDeck result"
    }
}

internal fun JSONObject.premiumDeckAlbumTitleHint(): String =
    listOf("album", "albumTitle", "albumName", "release", "releaseTitle", "playlistName")
        .asSequence()
        .map { key -> optString(key).cleanStrictStreamAlbumTitle() }
        .firstOrNull { it.isReliableStrictAlbumTitle() }
        .orEmpty()

internal fun fallbackYouTubeSource(detection: YouTubeDetection, quality: YouTubeQuality): YouTubeSource {
    val title = when (detection.kind) {
        YouTubeSourceKind.Video -> "PremiumDeck video ${detection.sourceId.take(8)}"
        YouTubeSourceKind.Playlist -> "PremiumDeck playlist ${detection.sourceId.take(8)}"
        YouTubeSourceKind.Channel -> "@${detection.sourceId}"
        YouTubeSourceKind.Unknown -> "PremiumDeck source"
    }
    return YouTubeSource(
        id = "${detection.kind.name.lowercase()}-${detection.sourceId}",
        url = detection.normalizedUrl,
        kind = detection.kind,
        title = title,
        author = PREMIUMDECK_SOURCE_NAME,
        quality = quality,
        status = if (detection.kind == YouTubeSourceKind.Video) YouTubeSourceStatus.StreamReady else YouTubeSourceStatus.ResolverNeeded,
    )
}

