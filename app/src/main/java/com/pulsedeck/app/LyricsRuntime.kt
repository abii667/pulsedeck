package com.pulsedeck.app

import android.content.Context
import com.pulsedeck.app.settings.model.LyricsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LRCLIB_API_BASE = ""
private const val LRCLIB_USER_AGENT = "PulseDeck/0.1 (lyrics lookup)"
private const val LRCLIB_MAX_ATTEMPTS = 3
private const val LRCLIB_ACCEPT_SCORE_WITH_ARTIST = 105
private const val LRCLIB_ACCEPT_SCORE_TITLE_ONLY = 94
private const val LYRICS_OVH_USER_AGENT = "PulseDeck/0.1 (lyrics fallback)"
private const val LYRICS_OVH_API_BASE = ""

internal data class LyricsLookupQuery(
    val artist: String,
    val title: String,
    val album: String? = null,
    val durationSeconds: Long? = null,
)

internal data class RankedLrclibCandidate(
    val json: JSONObject,
    val score: Int,
)

internal suspend fun loadLyricsForTrack(context: Context, track: Track, settings: LyricsSettings): LyricsUiState =
    withContext(Dispatchers.IO) {
        val normalized = settings.normalized()
        if (!normalized.enabled) return@withContext LyricsUiState.Missing
        readLyricsDiskCache(context, track.stableKey())?.let { return@withContext it.toLyricsUiState() }
        if (!normalized.onlineLookup) return@withContext LyricsUiState.Missing
        val networkPolicy = NetworkPolicyController.currentPolicy(context, PulseOnlineRuntime.settings)
        if (PulseOnlineRuntime.settings.offlineMode || networkPolicy.network.isNoNetwork) {
            return@withContext LyricsUiState.BlockedOffline
        }
        if (networkPolicy.effectiveDataSaver) return@withContext LyricsUiState.BlockedDataSaver

        val result = runCatching { fetchLrclibLyrics(track) }.getOrNull()
            ?: runCatching { fetchLyricsOvhLyrics(track) }.getOrNull()
            ?: return@withContext LyricsUiState.Missing
        writeLyricsDiskCache(context, result, normalized.cacheSizeMb)
        result.toLyricsUiState()
    }

private fun LyricsResult.toLyricsUiState(): LyricsUiState =
    when {
        syncedLines.isNotEmpty() -> LyricsUiState.Synced(syncedLines)
        plainText.isNotBlank() -> LyricsUiState.Plain(plainText)
        else -> LyricsUiState.Missing
    }

private fun lyricsDiskCacheDir(context: Context): File =
    File(context.cacheDir, "lyrics_cache").apply { mkdirs() }

private fun lyricsDiskCacheFile(context: Context, trackKey: String): File =
    File(lyricsDiskCacheDir(context), "${sha256(trackKey)}.json")

private fun readLyricsDiskCache(context: Context, trackKey: String): LyricsResult? =
    runCatching {
        val file = lyricsDiskCacheFile(context, trackKey)
        if (!file.isFile) return@runCatching null
        val json = JSONObject(file.readText())
        LyricsResult(
            trackKey = json.optString("trackKey", trackKey),
            source = json.optString("source", "cache"),
            plainText = json.optString("plainText"),
            syncedLines = json.optJSONArray("syncedLines")?.let { lines ->
                buildList {
                    for (index in 0 until lines.length()) {
                        val line = lines.optJSONObject(index) ?: continue
                        val text = line.optString("text").trim()
                        if (text.isNotBlank()) add(LyricsLine(line.optLong("startMillis"), text))
                    }
                }
            }.orEmpty(),
            fetchedAtMillis = json.optLong("fetchedAtMillis", file.lastModified()),
        ).also { file.setLastModified(System.currentTimeMillis()) }
    }.getOrNull()

private fun writeLyricsDiskCache(context: Context, result: LyricsResult, maxCacheMb: Int) {
    runCatching {
        val lines = JSONArray()
        result.syncedLines.forEach { line ->
            lines.put(
                JSONObject()
                    .put("startMillis", line.startMillis)
                    .put("text", line.text),
            )
        }
        val json = JSONObject()
            .put("trackKey", result.trackKey)
            .put("source", result.source)
            .put("plainText", result.plainText)
            .put("syncedLines", lines)
            .put("fetchedAtMillis", result.fetchedAtMillis)
        lyricsDiskCacheFile(context, result.trackKey).writeText(json.toString())
        enforceLyricsDiskCacheLimit(context, maxCacheMb)
    }
}

private fun enforceLyricsDiskCacheLimit(context: Context, maxCacheMb: Int) {
    val limitBytes = CacheBudgetManager.lyricsMetadataBudgetBytes(context, maxCacheMb)
    val files = lyricsDiskCacheDir(context)
        .listFiles()
        .orEmpty()
        .filter { it.isFile }
        .sortedByDescending { it.lastModified() }
    var total = files.sumOf { it.length() }
    files.asReversed().forEach { file ->
        if (total <= limitBytes) return
        val length = file.length()
        if (file.delete()) total -= length
    }
}

internal suspend fun clearLyricsDiskCache(context: Context): Int = withContext(Dispatchers.IO) {
    lyricsDiskCacheDir(context)
        .listFiles()
        .orEmpty()
        .count { it.isFile && it.delete() }
}

private suspend fun fetchLrclibLyrics(track: Track): LyricsResult? {
    val queries = lyricsLookupQueriesForTrack(track)
    if (queries.isEmpty()) return null
    for (query in queries) {
        fetchLrclibExact(query)
            ?.toValidatedLrclibLyricsResult(track, query, "LRCLIB exact")
            ?.let { return it }

        val search = fetchLrclibSearch(query) ?: continue
        val ranked = rankLrclibSearchResults(search, query).take(4)
        for (candidate in ranked) {
            candidate.json
                .toValidatedLrclibLyricsResult(track, query, "LRCLIB search")
                ?.let { return it }
            fetchLrclibCandidateDetail(candidate.json, query)
                ?.toValidatedLrclibLyricsResult(track, query, "LRCLIB detail")
                ?.let { return it }
        }
    }
    return null
}

private suspend fun fetchLyricsOvhLyrics(track: Track): LyricsResult? {
    val artist = track.artist.cleanLyricsSearchText()
    val title = track.title.cleanLyricsSearchText()
    if (artist.isBlank() || title.isBlank()) return null
    val json = lyricsHttpJsonObjectWithRetry(
        "$LYRICS_OVH_API_BASE/v1/${artist.urlPath()}/${title.urlPath()}",
        attempts = 1,
        connectTimeout = 3000,
        readTimeout = 6000,
        userAgent = LYRICS_OVH_USER_AGENT,
    )
        ?: return null
    val lyrics = json.optString("lyrics").trim()
    return lyrics.takeIf { it.length > 12 }?.let {
        LyricsResult(trackKey = track.stableKey(), source = "lyrics.ovh fallback", plainText = it)
    }
}

private suspend fun fetchLrclibExact(query: LyricsLookupQuery): JSONObject? {
    val params = query.toLrclibExactParams() ?: return null
    return lyricsHttpJsonObjectWithRetry("$LRCLIB_API_BASE/get?$params")
}

private suspend fun fetchLrclibSearch(query: LyricsLookupQuery): JSONArray? {
    val params = query.toLrclibSearchParams() ?: return null
    return lyricsHttpJsonArrayWithRetry("$LRCLIB_API_BASE/search?$params")
}

private suspend fun fetchLrclibCandidateDetail(candidate: JSONObject, fallback: LyricsLookupQuery): JSONObject? {
    val query = LyricsLookupQuery(
        artist = candidate.optString("artistName").cleanLyricsSearchText().ifBlank { fallback.artist },
        title = candidate.optString("trackName").cleanLyricsSearchText().ifBlank { fallback.title },
        album = candidate.optString("albumName").cleanLyricsSearchText().ifBlank { fallback.album },
        durationSeconds = candidate.optLong("duration", 0L).takeIf { it > 0L } ?: fallback.durationSeconds,
    )
    val params = query.toLrclibExactParams() ?: return null
    return lyricsHttpJsonObjectWithRetry("$LRCLIB_API_BASE/get?$params")
}

internal fun lyricsLookupQueriesForTrack(track: Track): List<LyricsLookupQuery> {
    val artist = track.artist.cleanLyricsArtistText()
    val title = track.title.cleanLyricsSearchText()
    val album = track.album.title.cleanLyricsAlbumText()
    val duration = (track.durationMillis / 1000L).takeIf { it > 0L }
    if (title.isBlank()) return emptyList()
    return buildList {
        add(LyricsLookupQuery(artist = artist, title = title, album = album, durationSeconds = duration))
        title.withoutLyricsFeaturedSuffix().takeIf { it.isNotBlank() && it != title }?.let { cleanTitle ->
            add(LyricsLookupQuery(artist = artist, title = cleanTitle, album = album, durationSeconds = duration))
        }
        splitLyricsArtistTitle(title)?.let { (splitArtist, splitTitle) ->
            add(LyricsLookupQuery(artist = splitArtist, title = splitTitle, album = album, durationSeconds = duration))
        }
    }
        .filter { it.title.isNotBlank() }
        .distinctBy { query ->
            listOf(query.artist, query.title, query.album.orEmpty(), query.durationSeconds ?: 0L)
                .joinToString("|") { it.toString().lyricsKey() }
        }
        .take(4)
}

internal fun rankLrclibSearchResults(json: JSONArray, query: LyricsLookupQuery): List<RankedLrclibCandidate> =
    buildList {
        for (index in 0 until json.length()) {
            val candidate = json.optJSONObject(index) ?: continue
            if (!candidate.isAcceptableLrclibCandidate(query)) continue
            add(RankedLrclibCandidate(candidate, candidate.lrclibCandidateScore(query)))
        }
    }.sortedByDescending { it.score }

internal fun JSONObject.toValidatedLrclibLyricsResult(track: Track, query: LyricsLookupQuery, sourceName: String): LyricsResult? {
    if (!isAcceptableLrclibCandidate(query)) return null
    return toLyricsResult(track, sourceName)
}

internal fun JSONObject.isAcceptableLrclibCandidate(query: LyricsLookupQuery): Boolean {
    if (!hasUsableLyricsContent()) return false
    val titleScore = lyricsTextSimilarity(optString("trackName"), query.title)
    val rankedScore = lrclibCandidateScore(query)
    if (query.artist.isBlank()) return titleScore >= 0.92f && rankedScore >= LRCLIB_ACCEPT_SCORE_TITLE_ONLY
    val artistScore = lyricsTextSimilarity(optString("artistName"), query.artist)
    return titleScore >= 0.70f && artistScore >= 0.52f && rankedScore >= LRCLIB_ACCEPT_SCORE_WITH_ARTIST
}

internal fun JSONObject.lrclibCandidateScore(query: LyricsLookupQuery): Int {
    val titleScore = (lyricsTextSimilarity(optString("trackName"), query.title) * 100f).roundToInt()
    val artistScore = (lyricsTextSimilarity(optString("artistName"), query.artist) * 62f).roundToInt()
    val albumScore = query.album?.let { (lyricsTextSimilarity(optString("albumName"), it) * 18f).roundToInt() } ?: 0
    val candidateDuration = optLong("duration", 0L).takeIf { it > 0L }
    val durationScore = when {
        candidateDuration == null || query.durationSeconds == null -> 0
        abs(candidateDuration - query.durationSeconds) <= 2L -> 24
        abs(candidateDuration - query.durationSeconds) <= 6L -> 14
        abs(candidateDuration - query.durationSeconds) <= 12L -> 6
        else -> -10
    }
    val contentScore = when {
        optString("syncedLyrics").isNotBlank() -> 24
        optString("plainLyrics").isNotBlank() -> 16
        else -> 0
    }
    val instrumentalPenalty = if (optBoolean("instrumental", false) && !hasUsableLyricsContent()) -80 else 0
    return titleScore + artistScore + albumScore + durationScore + contentScore + instrumentalPenalty
}

private fun JSONObject.hasUsableLyricsContent(): Boolean =
    optString("plainLyrics").trim().length > 12 || optString("syncedLyrics").trim().length > 12

private fun LyricsLookupQuery.toLrclibExactParams(): String? {
    if (artist.isBlank() || title.isBlank()) return null
    return buildList {
        add("artist_name=${artist.urlParam()}")
        add("track_name=${title.urlParam()}")
        album?.takeIf { it.isNotBlank() }?.let { add("album_name=${it.urlParam()}") }
        durationSeconds?.let { add("duration=$it") }
    }.joinToString("&")
}

private fun LyricsLookupQuery.toLrclibSearchParams(): String? {
    if (title.isBlank()) return null
    return buildList {
        if (artist.isNotBlank()) add("artist_name=${artist.urlParam()}")
        add("track_name=${title.urlParam()}")
    }.joinToString("&")
}

private fun JSONObject.toLyricsResult(track: Track, sourceName: String): LyricsResult? {
    val syncedRaw = optString("syncedLyrics").trim()
    val plain = optString("plainLyrics").trim()
    val synced = parseLrcLyrics(syncedRaw)
    val text = plain.ifBlank {
        if (synced.isNotEmpty()) synced.joinToString("\n") { it.text } else ""
    }
    return LyricsResult(
        trackKey = track.stableKey(),
        source = sourceName,
        plainText = text,
        syncedLines = synced,
    ).takeIf { it.plainText.isNotBlank() || it.syncedLines.isNotEmpty() }
}

private fun parseLrcLyrics(raw: String): List<LyricsLine> {
    if (raw.isBlank()) return emptyList()
    val tag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    return raw.lineSequence()
        .flatMap { line ->
            val matches = tag.findAll(line).toList()
            val text = tag.replace(line, "").trim()
            matches.asSequence().mapNotNull { match ->
                val minutes = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                val seconds = match.groupValues.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
                val fraction = match.groupValues.getOrNull(3).orEmpty()
                val millis = when (fraction.length) {
                    1 -> fraction.toLongOrNull()?.times(100L)
                    2 -> fraction.toLongOrNull()?.times(10L)
                    else -> fraction.take(3).padEnd(3, '0').toLongOrNull()
                } ?: 0L
                LyricsLine(minutes * 60_000L + seconds * 1000L + millis, text)
            }
        }
        .filter { it.text.isNotBlank() }
        .sortedBy { it.startMillis }
        .toList()
}

internal fun String.cleanLyricsSearchText(): String =
    replace(Regex("""\[[^]]*]|\([^)]*\)"""), " ")
        .replace(Regex("""\b(official|audio|video|lyrics?|visualizer|remaster(?:ed)?|hd|4k)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun String.cleanLyricsArtistText(): String {
    val cleaned = cleanLyricsSearchText()
    return cleaned.takeUnless {
        val normalized = it.lyricsKey()
        normalized in setOf("unknown", "unknown artist", "various", "various artists", "premiumdeck", "youtube")
    }.orEmpty()
}

private fun String.cleanLyricsAlbumText(): String? {
    val cleaned = cleanLyricsSearchText()
    if (cleaned.isBlank()) return null
    val normalized = cleaned.lyricsKey()
    return cleaned.takeUnless {
        normalized in setOf(
            "premiumdeck",
            "premiumdeck streams",
            "premiumdeck stream",
            "stream library",
            "online stream",
            "unknown album",
            "pulse deck",
            "pulsedeck",
        )
    }
}

private fun String.withoutLyricsFeaturedSuffix(): String =
    replace(
        Regex(
            """\s+(?:feat(?:uring)?|ft|with)\.?\s+.+$""",
            RegexOption.IGNORE_CASE,
        ),
        " ",
    )
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun splitLyricsArtistTitle(title: String): Pair<String, String>? {
    val parts = Regex("""\s+[-\u2013\u2014]\s+""")
        .split(title)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.size < 2) return null
    val artist = parts.first().cleanLyricsArtistText()
    val song = parts.drop(1).joinToString(" - ").cleanLyricsSearchText()
    if (artist.isBlank() || song.isBlank()) return null
    return artist to song
}

private fun lyricsTextSimilarity(candidate: String, query: String): Float {
    val candidateKey = candidate.lyricsKey()
    val queryKey = query.lyricsKey()
    if (candidateKey.isBlank() || queryKey.isBlank()) return 0f
    if (candidateKey == queryKey) return 1f
    val compactCandidate = candidateKey.replace(" ", "")
    val compactQuery = queryKey.replace(" ", "")
    if (compactCandidate == compactQuery) return 0.98f
    val candidateTokens = candidateKey.split(" ").filter { it.length >= 2 }
    val queryTokens = queryKey.split(" ").filter { it.length >= 2 }
    if (candidateTokens.isEmpty() || queryTokens.isEmpty()) return 0f
    val shared = queryTokens.count { it in candidateTokens }.toFloat()
    val queryCoverage = shared / queryTokens.size.toFloat()
    val candidateCoverage = shared / candidateTokens.size.toFloat()
    val containmentScore = when {
        queryKey.length >= 4 && candidateKey.contains(queryKey) -> 0.84f
        candidateKey.length >= 4 && queryKey.contains(candidateKey) -> 0.82f
        else -> 0f
    }
    return maxOf(containmentScore, (queryCoverage * 0.72f) + (candidateCoverage * 0.28f)).coerceIn(0f, 1f)
}

private fun String.lyricsKey(): String =
    lowercase(Locale.US)
        .replace(Regex("""[^\p{L}\p{Nd}]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

private suspend fun lyricsHttpJsonObjectWithRetry(
    url: String,
    attempts: Int = LRCLIB_MAX_ATTEMPTS,
    connectTimeout: Int = 5000,
    readTimeout: Int = 15000,
    userAgent: String = LRCLIB_USER_AGENT,
): JSONObject? =
    lyricsHttpTextWithRetry(url, attempts, connectTimeout, readTimeout, userAgent)
        ?.let { body -> runCatching { JSONObject(body) }.getOrNull() }

private suspend fun lyricsHttpJsonArrayWithRetry(
    url: String,
    attempts: Int = LRCLIB_MAX_ATTEMPTS,
    connectTimeout: Int = 5000,
    readTimeout: Int = 15000,
    userAgent: String = LRCLIB_USER_AGENT,
): JSONArray? =
    lyricsHttpTextWithRetry(url, attempts, connectTimeout, readTimeout, userAgent)
        ?.let { body -> runCatching { JSONArray(body) }.getOrNull() }

private suspend fun lyricsHttpTextWithRetry(
    url: String,
    attempts: Int,
    connectTimeout: Int,
    readTimeout: Int,
    userAgent: String,
): String? {
    val safeAttempts = attempts.coerceAtLeast(1)
    repeat(safeAttempts) { attempt ->
        val result = lyricsHttpText(url, connectTimeout, readTimeout, userAgent)
        if (result.body != null) return result.body
        if (!result.retryable || attempt == safeAttempts - 1) return null
        delay(240L * (attempt + 1))
    }
    return null
}

private data class LyricsHttpTextResult(
    val body: String?,
    val retryable: Boolean,
)

private fun lyricsHttpText(
    url: String,
    connectTimeout: Int,
    readTimeout: Int,
    userAgent: String,
): LyricsHttpTextResult =
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("User-Agent", userAgent)
            val code = connection.responseCode
            when {
                code in 200..299 -> LyricsHttpTextResult(connection.inputStream.bufferedReader().use { it.readText() }, retryable = false)
                code == 408 || code == 429 || code in 500..599 -> LyricsHttpTextResult(null, retryable = true)
                else -> LyricsHttpTextResult(null, retryable = false)
            }
        } finally {
            connection.disconnect()
        }
    }.getOrElse {
        LyricsHttpTextResult(null, retryable = true)
    }

internal fun String.urlParam(): String = URLEncoder.encode(this, "UTF-8")

internal fun String.urlPath(): String = urlParam().replace("+", "%20")

