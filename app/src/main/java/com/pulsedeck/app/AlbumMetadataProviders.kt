package com.pulsedeck.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val MUSICBRAINZ_USER_AGENT = "PulseDeck/0.1 (metadata album downloader)"
private const val JAMENDO_CLIENT_ID = ""

private suspend fun searchMusicBrainzAlbumReleases(artistQuery: String, albumQuery: String): List<AlbumDownloadRelease> =
    withContext(Dispatchers.IO) {
        val artist = artistQuery.trim()
        val album = albumQuery.trim()
        if (artist.length < 2 || album.length < 2 || PulseOnlineRuntime.settings.offlineMode) return@withContext emptyList()
        val luceneQuery = "artist:\"$artist\" AND release:\"$album\""
        val url = "https://musicbrainz.org/ws/2/release?fmt=json&limit=8&query=${luceneQuery.urlParam()}"
        val searchJson = withTimeoutOrNull(6200L) { fetchMusicBrainzJson(url) } ?: return@withContext emptyList()
        val basic = parseMusicBrainzReleaseSearchResults(searchJson, artist, album).take(4)
        if (basic.isEmpty()) return@withContext emptyList()
        val detailed = buildList {
            basic.forEachIndexed { index, release ->
                if (index > 0) delay(1100L)
                val detailUrl = "https://musicbrainz.org/ws/2/release/${release.id}?fmt=json&inc=recordings+artists+labels+release-groups+media"
                val detail = withTimeoutOrNull(6200L) { fetchMusicBrainzJson(detailUrl) }
                    ?.let { parseMusicBrainzReleaseDetail(it, release) }
                add(detail ?: release)
            }
        }
        detailed
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<AlbumDownloadRelease> { it.tracks.size }
                    .thenByDescending { it.score }
                    .thenBy { it.date },
            )
    }

private suspend fun searchMusicBrainzArtistAlbumReleases(artistQuery: String): List<AlbumDownloadRelease> =
    withContext(Dispatchers.IO) {
        val artist = artistQuery.trim()
        if (artist.length < 2 || PulseOnlineRuntime.settings.offlineMode) return@withContext emptyList()
        val luceneQuery = "artist:\"$artist\" AND primarytype:album"
        val url = "https://musicbrainz.org/ws/2/release?fmt=json&limit=16&query=${luceneQuery.urlParam()}"
        val searchJson = withTimeoutOrNull(6200L) { fetchMusicBrainzJson(url) } ?: return@withContext emptyList()
        val artistKey = artist.normalizedSearchText()
        val basic = buildList {
            val releases = searchJson.optJSONArray("releases") ?: return@buildList
            for (index in 0 until releases.length()) {
                val item = releases.optJSONObject(index) ?: continue
                val release = parseMusicBrainzReleaseDetail(item, fallback = null) ?: continue
                val releaseArtistKey = release.artist.normalizedSearchText()
                val artistMatch = releaseArtistKey == artistKey || releaseArtistKey.contains(artistKey) || artistKey.contains(releaseArtistKey)
                if (artistMatch) add(release.copy(score = item.optInt("score", release.score)))
            }
        }
            .distinctBy { release ->
                listOf(release.title, release.artist, release.date, release.trackCount).joinToString("|") { it.toString().normalizedSearchText() }
            }
            .sortedByDescending { it.score }
            .take(8)
        if (basic.isEmpty()) return@withContext emptyList()
        buildList {
            basic.forEachIndexed { index, release ->
                if (index > 0) delay(1100L)
                val detailUrl = "https://musicbrainz.org/ws/2/release/${release.id}?fmt=json&inc=recordings+artists+labels+release-groups+media"
                val detail = withTimeoutOrNull(6200L) { fetchMusicBrainzJson(detailUrl) }
                    ?.let { parseMusicBrainzReleaseDetail(it, release) }
                add(detail ?: release)
            }
        }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<AlbumDownloadRelease> { it.tracks.size }
                    .thenByDescending { it.score }
                    .thenBy { it.date },
            )
            .take(12)
    }

internal suspend fun fetchMusicBrainzReleaseGroupAlbumBuilderRelease(
    releaseGroupId: String,
    fallback: AlbumDownloadRelease,
): AlbumDownloadRelease? = withContext(Dispatchers.IO) {
    val id = releaseGroupId.removePrefix("mb-rg:").trim()
    if (id.isBlank() || PulseOnlineRuntime.settings.offlineMode) return@withContext null
    val url = "https://musicbrainz.org/ws/2/release?fmt=json&limit=12&release-group=${id.urlParam()}&inc=recordings+artists+labels+release-groups+media"
    val releasesJson = withTimeoutOrNull(6200L) { fetchMusicBrainzJson(url) } ?: return@withContext null
    val releases = parseMusicBrainzReleaseGroupAlbumBuilderReleases(releasesJson, id, fallback)
    val selected = releases.firstOrNull() ?: return@withContext null
    if (selected.tracks.isNotEmpty()) return@withContext selected
    val releaseId = selected.musicBrainzReleaseIdFromLabel() ?: return@withContext selected
    val detailUrl = "https://musicbrainz.org/ws/2/release/${releaseId.urlParam()}?fmt=json&inc=recordings+artists+labels+release-groups+media"
    withTimeoutOrNull(6200L) { fetchMusicBrainzJson(detailUrl) }
        ?.let { parseMusicBrainzReleaseDetail(it, selected.copy(id = releaseId)) }
        ?.toVerifiedReleaseGroupAlbumBuilderRelease(fallback, id, releaseId)
        ?: selected
}

internal suspend fun fetchAppleItunesAlbumBuilderRelease(
    appleAlbumId: String?,
    fallback: AlbumDownloadRelease,
): AlbumDownloadRelease? = withContext(Dispatchers.IO) {
    if (PulseOnlineRuntime.settings.offlineMode) return@withContext null
    val id = appleAlbumId
        ?.removePrefix("apple:")
        ?.trim()
        ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    if (id != null) {
        val lookupUrl = "https://itunes.apple.com/lookup?id=$id&entity=song&limit=200"
        val release = withTimeoutOrNull(6200L) {
            httpJson(lookupUrl, connectTimeout = 2200, readTimeout = 5200)
        }
            ?.let(::parseAppleItunesLookupRelease)
            ?.toAlbumBuilderTracklistFallback(fallback, "Apple/iTunes")
        if (release != null) return@withContext release
    }
    searchAppleItunesAlbumReleases(fallback.artist, fallback.title)
        .firstNotNullOfOrNull { release -> release.toAlbumBuilderTracklistFallback(fallback, "Apple/iTunes") }
}

internal fun AlbumDownloadRelease.toAlbumBuilderTracklistFallback(
    fallback: AlbumDownloadRelease,
    sourceLabel: String,
): AlbumDownloadRelease? {
    if (tracks.isEmpty()) return null
    if (!matchesAlbumMetadataQuery(fallback.artist, fallback.title)) return null
    val metadataLabel = listOfNotNull(
        fallback.label.takeIf { it.isNotBlank() },
        "$sourceLabel album ${id.removePrefix("apple:")}".takeIf { id.isNotBlank() },
    )
        .distinct()
        .joinToString(" | ")
    return copy(
        id = fallback.id,
        title = title.ifBlank { fallback.title },
        artist = artist.ifBlank { fallback.artist },
        date = date.ifBlank { fallback.date },
        format = format.ifBlank { fallback.format },
        label = metadataLabel,
        trackCount = max(max(trackCount, tracks.size), fallback.trackCount),
        coverUrl = coverUrl.ifBlank { fallback.coverUrl },
        source = "$sourceLabel fallback",
        score = max(score, fallback.score),
    )
}

internal fun parseMusicBrainzReleaseGroupAlbumBuilderReleases(
    json: JSONObject,
    releaseGroupId: String,
    fallback: AlbumDownloadRelease,
): List<AlbumDownloadRelease> {
    val releases = json.optJSONArray("releases") ?: return emptyList()
    return buildList {
        for (index in 0 until releases.length()) {
            val item = releases.optJSONObject(index) ?: continue
            val parsed = parseMusicBrainzReleaseDetail(item, fallback = null) ?: continue
            val releaseId = parsed.id
            if (releaseId.isBlank()) continue
            add(parsed.toVerifiedReleaseGroupAlbumBuilderRelease(fallback, releaseGroupId, releaseId))
        }
    }
        .filter { release ->
            release.title.normalizedSearchText() == fallback.title.normalizedSearchText() ||
                release.title.normalizedSearchText().contains(fallback.title.normalizedSearchText()) ||
                fallback.title.normalizedSearchText().contains(release.title.normalizedSearchText())
        }
        .distinctBy { release ->
            listOf(release.title, release.artist, release.date, release.country, release.trackCount, release.tracks.map { it.title }).joinToString("|") {
                it.toString().normalizedSearchText()
            }
        }
        .sortedWith(
            compareByDescending<AlbumDownloadRelease> { it.musicBrainzReleaseGroupSelectionScore(fallback) }
                .thenByDescending { it.tracks.size }
                .thenBy { it.date },
        )
}

private fun AlbumDownloadRelease.toVerifiedReleaseGroupAlbumBuilderRelease(
    fallback: AlbumDownloadRelease,
    releaseGroupId: String,
    releaseId: String,
): AlbumDownloadRelease {
    val metadataLabel = listOfNotNull(
        label.takeIf { it.isNotBlank() },
        "MusicBrainz release-group $releaseGroupId",
        "MusicBrainz release $releaseId",
    ).distinct().joinToString(" | ")
    return copy(
        id = fallback.id,
        title = title.ifBlank { fallback.title },
        artist = artist.ifBlank { fallback.artist },
        date = date.ifBlank { fallback.date },
        format = format.ifBlank { fallback.format },
        label = metadataLabel,
        trackCount = max(trackCount, tracks.size.takeIf { it > 0 } ?: 0).coerceAtLeast(fallback.trackCount),
        coverUrl = coverUrl.takeIf { it.isNotBlank() } ?: fallback.coverUrl,
        source = "MusicBrainz",
        score = max(score, fallback.score),
    )
}

private fun AlbumDownloadRelease.musicBrainzReleaseGroupSelectionScore(fallback: AlbumDownloadRelease): Int {
    var score = 0
    if (title.normalizedSearchText() == fallback.title.normalizedSearchText()) score += 30
    if (artist.normalizedSearchText() == fallback.artist.normalizedSearchText()) score += 20
    if (tracks.isNotEmpty()) score += 30
    if (trackCount > 0 && tracks.size >= trackCount) score += 8
    if (date.isNotBlank() && fallback.date.take(4).isNotBlank() && date.startsWith(fallback.date.take(4))) score += 6
    if (country in setOf("XW", "US", "GB")) score += 4
    val lowered = listOf(title, format, label).joinToString(" ").normalizedSearchText()
    if (lowered.contains("deluxe") || lowered.contains("expanded") || lowered.contains("anniversary") || lowered.contains("remaster")) score -= 5
    if (lowered.contains("live") || lowered.contains("soundtrack")) score -= 12
    return score
}

private fun AlbumDownloadRelease.musicBrainzReleaseIdFromLabel(): String? =
    label.split("|")
        .map { it.trim() }
        .firstOrNull { it.startsWith("MusicBrainz release ") }
        ?.removePrefix("MusicBrainz release ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

internal suspend fun searchAlbumMetadataReleases(
    artistQuery: String,
    albumQuery: String,
    premiumSources: List<YouTubeSource>,
): List<AlbumDownloadRelease> = coroutineScope {
    val artist = artistQuery.trim()
    val album = albumQuery.trim()
    if (artist.length < 2) return@coroutineScope emptyList()
    val musicBrainz = async {
        runCatching {
            if (album.isBlank()) searchMusicBrainzArtistAlbumReleases(artist) else searchMusicBrainzAlbumReleases(artist, album)
        }.getOrDefault(emptyList())
    }
    val appleItunes = async {
        runCatching { searchAppleItunesAlbumReleases(artist, album) }.getOrDefault(emptyList())
    }
    val deezer = async {
        runCatching { searchDeezerAlbumReleases(artist, album) }.getOrDefault(emptyList())
    }
    val freeLegal = async {
        runCatching {
            if (album.isBlank()) emptyList() else searchFreeLegalAlbumReleases(artist, album)
        }.getOrDefault(emptyList())
    }
    val premiumDeck = async {
        runCatching { searchPremiumDeckAlbumReleases(artist, album, premiumSources) }.getOrDefault(emptyList())
    }
    mergeAlbumTrackerResults(musicBrainz.await() + appleItunes.await() + deezer.await() + freeLegal.await() + premiumDeck.await())
}

private suspend fun searchAppleItunesAlbumReleases(artistQuery: String, albumQuery: String): List<AlbumDownloadRelease> =
    withContext(Dispatchers.IO) {
        val artist = artistQuery.trim()
        val album = albumQuery.trim()
        if (artist.length < 2 || PulseOnlineRuntime.settings.offlineMode) return@withContext emptyList()
        val query = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" ")
        if (query.length < 3) return@withContext emptyList()
        val limit = if (album.isBlank()) 16 else 10
        val searchUrl = "https://itunes.apple.com/search?term=${query.urlParam()}&media=music&entity=album&limit=$limit"
        val searchJson = withTimeoutOrNull(5200L) {
            httpJson(searchUrl, connectTimeout = 2200, readTimeout = 4200)
        } ?: return@withContext emptyList()
        val collectionIds = parseAppleItunesAlbumSearchCollectionIds(searchJson, artist, album)
            .take(if (album.isBlank()) 8 else 5)
        buildList {
            collectionIds.forEachIndexed { index, collectionId ->
                if (index > 0) delay(300L)
                val lookupUrl = "https://itunes.apple.com/lookup?id=$collectionId&entity=song&limit=200"
                val detail = withTimeoutOrNull(6200L) {
                    httpJson(lookupUrl, connectTimeout = 2200, readTimeout = 5200)
                }
                    ?.let(::parseAppleItunesLookupRelease)
                    ?.takeIf { it.matchesAlbumMetadataQuery(artist, album) }
                if (detail != null) add(detail)
            }
        }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<AlbumDownloadRelease> { it.tracks.size }
                    .thenByDescending { it.trackCount }
                    .thenByDescending { it.score },
            )
            .take(12)
    }

private suspend fun searchDeezerAlbumReleases(artistQuery: String, albumQuery: String): List<AlbumDownloadRelease> =
    withContext(Dispatchers.IO) {
        val artist = artistQuery.trim()
        val album = albumQuery.trim()
        if (artist.length < 2 || PulseOnlineRuntime.settings.offlineMode) return@withContext emptyList()
        val query = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" ")
        if (query.length < 3) return@withContext emptyList()
        val limit = if (album.isBlank()) 16 else 10
        val searchUrl = "https://api.deezer.com/search/album?q=${query.urlParam()}&limit=$limit"
        val searchJson = withTimeoutOrNull(5200L) {
            httpJson(searchUrl, connectTimeout = 2200, readTimeout = 4200)
        } ?: return@withContext emptyList()
        val albumIds = parseDeezerAlbumSearchIds(searchJson, artist, album)
            .take(if (album.isBlank()) 8 else 5)
        buildList {
            albumIds.forEachIndexed { index, albumId ->
                if (index > 0) delay(180L)
                val detail = withTimeoutOrNull(6200L) {
                    httpJson("https://api.deezer.com/album/$albumId", connectTimeout = 2200, readTimeout = 5200)
                }
                    ?.let(::parseDeezerAlbumDetail)
                    ?.takeIf { it.matchesAlbumMetadataQuery(artist, album) }
                if (detail != null) add(detail)
            }
        }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<AlbumDownloadRelease> { it.tracks.size }
                    .thenByDescending { it.trackCount }
                    .thenByDescending { it.score },
            )
            .take(12)
    }

internal fun parseAppleItunesAlbumSearchCollectionIds(json: JSONObject, artistQuery: String, albumQuery: String): List<Long> {
    val results = json.optJSONArray("results") ?: return emptyList()
    return buildList {
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            if (!item.optString("wrapperType").equals("collection", ignoreCase = true)) continue
            if (!item.optString("collectionType").equals("Album", ignoreCase = true)) continue
            val collectionId = item.optLong("collectionId", -1L)
            if (collectionId <= 0L) continue
            val title = item.optString("collectionName").trim()
            val artist = item.optString("artistName").trim()
            if (matchesAlbumMetadataQuery(title, artist, artistQuery, albumQuery)) add(collectionId)
        }
    }.distinct()
}

internal fun parseDeezerAlbumSearchIds(json: JSONObject, artistQuery: String, albumQuery: String): List<Long> {
    val data = json.optJSONArray("data") ?: return emptyList()
    return buildList {
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val albumId = item.optLong("id", -1L)
            if (albumId <= 0L) continue
            val title = item.optString("title").trim()
            val artist = item.optJSONObject("artist")?.optString("name").orEmpty().trim()
            if (matchesAlbumMetadataQuery(title, artist, artistQuery, albumQuery)) add(albumId)
        }
    }.distinct()
}

private fun AlbumDownloadRelease.matchesAlbumMetadataQuery(artistQuery: String, albumQuery: String): Boolean =
    matchesAlbumMetadataQuery(title, artist, artistQuery, albumQuery)

private fun matchesAlbumMetadataQuery(title: String, artist: String, artistQuery: String, albumQuery: String): Boolean {
    val artistKey = artist.cleanStreamArtistName().normalizedSearchText()
    val queryArtistKey = artistQuery.cleanStreamArtistName().normalizedSearchText()
    if (!metadataTextMatches(artistKey, queryArtistKey)) return false
    val queryAlbumKey = albumQuery.cleanStrictStreamAlbumTitle().normalizedSearchText()
        .ifBlank { albumQuery.normalizedSearchText() }
    if (queryAlbumKey.isBlank()) return true
    val titleKey = title.cleanStrictStreamAlbumTitle().normalizedSearchText()
        .ifBlank { title.normalizedSearchText() }
    return metadataTextMatches(titleKey, queryAlbumKey)
}

private fun metadataTextMatches(candidateKey: String, queryKey: String): Boolean {
    if (candidateKey.isBlank() || queryKey.isBlank()) return false
    if (candidateKey == queryKey) return true
    val compactCandidate = candidateKey.replace(" ", "")
    val compactQuery = queryKey.replace(" ", "")
    if (compactCandidate == compactQuery) return true
    if (queryKey.length >= 4 && candidateKey.contains(queryKey)) return true
    if (candidateKey.length >= 4 && queryKey.contains(candidateKey)) return true
    return metadataTokenOverlap(candidateKey, queryKey) >= 0.78f
}

private fun metadataTokenOverlap(candidateKey: String, queryKey: String): Float {
    val candidateTokens = candidateKey.split(" ").filter { it.length >= 2 }.toSet()
    val queryTokens = queryKey.split(" ").filter { it.length >= 2 }.toSet()
    if (candidateTokens.isEmpty() || queryTokens.isEmpty()) return 0f
    val shared = queryTokens.count { it in candidateTokens }.toFloat()
    return shared / queryTokens.size.toFloat()
}

private suspend fun searchPremiumDeckAlbumReleases(
    artist: String,
    album: String,
    existingSources: List<YouTubeSource>,
): List<AlbumDownloadRelease> = withContext(Dispatchers.IO) {
    val queries = if (album.isNotBlank()) {
        listOf(
            "$artist $album full album",
            "$artist $album playlist",
            "$artist $album official audio",
        )
    } else {
        emptyList()
    }
    val onlineSources = if (queries.isNotEmpty() && !PulseOnlineRuntime.settings.offlineMode) {
        buildList {
            for ((index, query) in queries.withIndex()) {
                if (index > 0) delay(140L)
                val results = withTimeoutOrNull(6200L) { searchYouTubeVideos(query).results.map { it.source } }.orEmpty()
                addAll(results)
            }
        }
    } else {
        emptyList()
    }
    val artistKey = artist.normalizedSearchText()
    val albumKey = album.normalizedSearchText()
    buildAlbumLikeCollections(existingSources, onlineSources)
        .filter { collection ->
            val release = collection.toPremiumDeckInferredRelease() ?: return@filter false
            val releaseArtistKey = release.artist.normalizedSearchText()
            val releaseTitleKey = release.title.normalizedSearchText()
            val artistMatch = releaseArtistKey == artistKey || releaseArtistKey.contains(artistKey) || artistKey.contains(releaseArtistKey)
            val albumMatch = albumKey.isBlank() || releaseTitleKey == albumKey || releaseTitleKey.contains(albumKey) || albumKey.contains(releaseTitleKey)
            artistMatch && albumMatch
        }
        .mapNotNull { it.toPremiumDeckInferredRelease() }
        .take(12)
}

private fun StreamCollection.toPremiumDeckInferredRelease(): AlbumDownloadRelease? {
    if (sources.isEmpty()) return null
    val representative = sources.first()
    val artist = representative.author.cleanStreamArtistName().ifBlank { subtitle.substringBefore(" - ").trim() }
    if (artist.isBlank() || title.isBlank()) return null
    val tracks = sources
        .distinctBy { it.streamDistinctKey() }
        .mapIndexed { index, source ->
            val position = index + 1
            AlbumDownloadTrack(
                position = position,
                title = source.title.cleanStreamTitle(),
                durationMillis = source.durationMillis,
                recordingId = source.id,
                matchedSource = source.copy(
                    title = source.title.cleanStreamTitle(),
                    author = artist,
                    albumTitleHint = title,
                    albumTrackNumberHint = position,
                    albumTrackTotalHint = sources.size,
                    reviewState = YouTubeReviewState.Accepted,
                ),
                matchScore = 72,
                matchReason = "PremiumDeck inferred album source",
            )
        }
    val key = albumRecommendationKey(artist, title).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return AlbumDownloadRelease(
        id = "premiumdeck-inferred-${key.ifBlank { id.normalizedSearchText().replace(' ', '-') }}",
        title = title,
        artist = artist,
        format = "PremiumDeck",
        trackCount = tracks.size,
        tracks = tracks,
        coverUrl = representative.bestThumbnailUrl() ?: representative.thumbnailUrl.orEmpty(),
        source = "PremiumDeck inference",
        score = 74 + min(tracks.size, 12),
    )
}

private fun mergeAlbumTrackerResults(releases: List<AlbumDownloadRelease>): List<AlbumDownloadRelease> =
    releases
        .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
        .groupBy { it.albumTrackerMergeKey() }
        .map { (_, group) ->
            val sorted = group.sortedWith(
                compareByDescending<AlbumDownloadRelease> { it.tracks.size }
                    .thenByDescending { it.trackCount }
                    .thenByDescending { albumTrackerSourceRank(it.source) }
                    .thenByDescending { it.score },
            )
            val base = sorted.first()
            val sourceLabel = sorted.map { it.source }.filter { it.isNotBlank() }.distinct().joinToString(" + ").ifBlank { base.source }
            base.copy(
                trackCount = max(base.trackCount, sorted.maxOf { it.trackCount }),
                coverUrl = sorted.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl ?: base.coverUrl,
                label = base.label.ifBlank { sorted.firstOrNull { it.label.isNotBlank() }?.label.orEmpty() },
                source = sourceLabel,
                score = sorted.maxOf { it.score },
            )
        }

private fun AlbumDownloadRelease.albumTrackerMergeKey(): String =
    "${artist.cleanStreamArtistName().normalizedSearchText()}|${title.cleanStrictStreamAlbumTitle().normalizedSearchText().ifBlank { title.normalizedSearchText() }}"

private fun albumTrackerSourceRank(source: String): Int =
    when {
        source.contains("Apple", ignoreCase = true) -> 7
        source.contains("Deezer", ignoreCase = true) -> 6
        source.contains("MusicBrainz", ignoreCase = true) -> 5
        source.contains("PremiumDeck", ignoreCase = true) -> 4
        source.contains("Internet Archive", ignoreCase = true) -> 3
        source.contains("Jamendo", ignoreCase = true) -> 3
        else -> 1
    }

internal fun parseManualAlbumTracklist(artist: String, album: String, rawTracklist: String): AlbumDownloadRelease? {
    val cleanArtist = artist.trim()
    val cleanAlbum = album.trim()
    if (cleanArtist.length < 2 || cleanAlbum.length < 2) return null
    val tracks = rawTracklist
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.matches(Regex("""(?i)^(disc|disk|side)\s+[a-z0-9]+[:\-\s]*$""")) }
        .map { line ->
            line
                .replace(Regex("""^\s*[\[\(]?\d{1,3}[\]\)]?\s*[\.\)\-:\u2013\u2014]\s*"""), "")
                .replace(Regex("""^\s*[-*\u2022]\s*"""), "")
                .replace(Regex("""\s+\d{1,2}:\d{2}(?::\d{2})?\s*$"""), "")
                .trim()
        }
        .filter { it.length >= 1 }
        .distinctBy { it.normalizedSearchText() }
        .mapIndexed { index, title ->
            AlbumDownloadTrack(
                position = index + 1,
                title = title,
            )
        }
        .toList()
    if (tracks.isEmpty()) return null
    val key = listOf(cleanArtist, cleanAlbum, tracks.joinToString("|") { it.title })
        .joinToString("|")
        .normalizedSearchText()
    return AlbumDownloadRelease(
        id = "manual-${key.hashCode().toUInt().toString(16)}",
        title = cleanAlbum,
        artist = cleanArtist,
        trackCount = tracks.size,
        tracks = tracks,
        source = "Manual tracklist",
        score = 70,
    )
}

internal fun parseAppleItunesLookupRelease(json: JSONObject): AlbumDownloadRelease? {
    val results = json.optJSONArray("results") ?: return null
    val collection = (0 until results.length())
        .asSequence()
        .mapNotNull { results.optJSONObject(it) }
        .firstOrNull { item ->
            item.optString("wrapperType").equals("collection", ignoreCase = true) &&
                item.optString("collectionType").equals("Album", ignoreCase = true)
        } ?: return null
    val collectionId = collection.optLong("collectionId", -1L)
    val title = collection.optString("collectionName").trim()
    val artist = collection.optString("artistName").trim()
    if (title.isBlank() || artist.isBlank()) return null
    val tracks = buildList {
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            if (!item.optString("wrapperType").equals("track", ignoreCase = true)) continue
            if (!item.optString("kind").equals("song", ignoreCase = true)) continue
            if (collectionId > 0 && item.optLong("collectionId", -1L) != collectionId) continue
            val trackTitle = item.optString("trackName").trim()
            if (trackTitle.isBlank()) continue
            add(
                Triple(
                    item.optInt("discNumber", 1).coerceAtLeast(1),
                    item.optInt("trackNumber", size + 1).coerceAtLeast(1),
                    AlbumDownloadTrack(
                        position = item.optInt("trackNumber", size + 1).coerceAtLeast(1),
                        title = trackTitle,
                        durationMillis = item.optLong("trackTimeMillis", 0L),
                        recordingId = item.optString("trackId"),
                    ),
                ),
            )
        }
    }
        .sortedWith(compareBy<Triple<Int, Int, AlbumDownloadTrack>> { it.first }.thenBy { it.second })
        .mapIndexed { index, item -> item.third.copy(position = index + 1) }
    val coverUrl = collection.optString("artworkUrl100")
        .replace(Regex("""/\d+x\d+bb\."""), "/1200x1200bb.")
    return AlbumDownloadRelease(
        id = "apple:${collectionId.takeIf { it > 0 } ?: title.normalizedSearchText().hashCode()}",
        title = title,
        artist = artist,
        date = collection.optString("releaseDate").substringBefore('T'),
        format = collection.optString("collectionType").ifBlank { "Album" },
        trackCount = collection.optInt("trackCount", tracks.size),
        tracks = tracks,
        coverUrl = coverUrl,
        source = "Apple/iTunes",
        score = 90 + min(tracks.size, 20),
    )
}

internal fun parseDeezerAlbumDetail(json: JSONObject): AlbumDownloadRelease? {
    val id = json.optLong("id", -1L)
    val title = json.optString("title").trim()
    val artist = json.optJSONObject("artist")?.optString("name").orEmpty().ifBlank { json.optString("artist") }.trim()
    if (title.isBlank() || artist.isBlank()) return null
    val tracksArray = json.optJSONObject("tracks")?.optJSONArray("data")
    val tracks = buildList {
        if (tracksArray != null) {
            for (index in 0 until tracksArray.length()) {
                val item = tracksArray.optJSONObject(index) ?: continue
                val trackTitle = item.optString("title_short").ifBlank { item.optString("title") }.trim()
                if (trackTitle.isBlank()) continue
                add(
                    Triple(
                        item.optInt("disk_number", 1).coerceAtLeast(1),
                        item.optInt("track_position", index + 1).coerceAtLeast(1),
                        AlbumDownloadTrack(
                            position = item.optInt("track_position", index + 1).coerceAtLeast(1),
                            title = trackTitle,
                            durationMillis = item.optLong("duration", 0L) * 1000L,
                            recordingId = item.optString("id"),
                        ),
                    ),
                )
            }
        }
    }
        .sortedWith(compareBy<Triple<Int, Int, AlbumDownloadTrack>> { it.first }.thenBy { it.second })
        .mapIndexed { index, item -> item.third.copy(position = index + 1) }
    return AlbumDownloadRelease(
        id = "deezer:${id.takeIf { it > 0 } ?: title.normalizedSearchText().hashCode()}",
        title = title,
        artist = artist,
        date = json.optString("release_date"),
        format = json.optString("record_type").ifBlank { "Album" },
        label = json.optString("label"),
        trackCount = json.optInt("nb_tracks", tracks.size),
        tracks = tracks,
        coverUrl = json.optString("cover_xl").ifBlank { json.optString("cover_big").ifBlank { json.optString("cover_medium") } },
        source = "Deezer",
        score = 86 + min(tracks.size, 20),
    )
}

private fun fetchMusicBrainzJson(url: String): JSONObject? =
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2600
        connection.readTimeout = 6200
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", MUSICBRAINZ_USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
        if (connection.responseCode !in 200..299) return@runCatching null
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()

internal fun parseMusicBrainzReleaseSearchResults(json: JSONObject, artistQuery: String, albumQuery: String): List<AlbumDownloadRelease> {
    val releases = json.optJSONArray("releases") ?: return emptyList()
    val artistKey = artistQuery.normalizedSearchText()
    val albumKey = albumQuery.normalizedSearchText()
    return buildList {
        for (index in 0 until releases.length()) {
            val item = releases.optJSONObject(index) ?: continue
            val release = parseMusicBrainzReleaseDetail(item, fallback = null) ?: continue
            val titleKey = release.title.normalizedSearchText()
            val releaseArtistKey = release.artist.normalizedSearchText()
            val score = item.optInt("score", release.score)
            val titleMatch = titleKey == albumKey || titleKey.contains(albumKey) || albumKey.contains(titleKey)
            val artistMatch = releaseArtistKey == artistKey || releaseArtistKey.contains(artistKey) || artistKey.contains(releaseArtistKey)
            if (titleMatch && artistMatch) {
                add(release.copy(score = score))
            }
        }
    }
        .distinctBy { release ->
            listOf(release.title, release.artist, release.date, release.country, release.trackCount).joinToString("|") { it.toString().normalizedSearchText() }
        }
        .sortedByDescending { it.score }
}

internal fun parseMusicBrainzReleaseDetail(json: JSONObject, fallback: AlbumDownloadRelease? = null): AlbumDownloadRelease? {
    val id = json.optString("id").ifBlank { fallback?.id.orEmpty() }.trim()
    val title = json.optString("title").ifBlank { fallback?.title.orEmpty() }.trim()
    val artist = json.musicBrainzArtistCredit().ifBlank { fallback?.artist.orEmpty() }.trim()
    if (id.isBlank() || title.isBlank() || artist.isBlank()) return null
    val tracks = parseMusicBrainzReleaseTracks(json.optJSONArray("media"))
    val format = json.optJSONArray("media")
        ?.let { media ->
            buildList {
                for (index in 0 until media.length()) {
                    media.optJSONObject(index)?.optString("format")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        .orEmpty()
        .distinct()
        .joinToString(", ")
        .ifBlank { fallback?.format.orEmpty() }
    val label = json.optJSONArray("label-info")
        ?.let { labels ->
            buildList {
                for (index in 0 until labels.length()) {
                    labels.optJSONObject(index)?.optJSONObject("label")?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        .orEmpty()
        .distinct()
        .joinToString(", ")
        .ifBlank { fallback?.label.orEmpty() }
    return AlbumDownloadRelease(
        id = id,
        title = title,
        artist = artist,
        date = json.optString("date").ifBlank { fallback?.date.orEmpty() },
        country = json.optString("country").ifBlank { fallback?.country.orEmpty() },
        format = format,
        label = label,
        trackCount = json.optInt("track-count", tracks.size.takeIf { it > 0 } ?: fallback?.trackCount ?: 0),
        tracks = tracks.ifEmpty { fallback?.tracks.orEmpty() },
        coverUrl = fallback?.coverUrl?.takeIf { it.isNotBlank() } ?: "https://coverartarchive.org/release/$id/front-500",
        source = fallback?.source?.takeIf { it.isNotBlank() } ?: "MusicBrainz",
        score = json.optInt("score", fallback?.score ?: 0),
    )
}

private fun JSONObject.musicBrainzArtistCredit(): String {
    val credits = optJSONArray("artist-credit") ?: return optString("artist")
    return buildList {
        for (index in 0 until credits.length()) {
            val item = credits.optJSONObject(index) ?: continue
            val name = item.optString("name")
                .ifBlank { item.optJSONObject("artist")?.optString("name").orEmpty() }
                .trim()
            if (name.isNotBlank()) add(name)
        }
    }.joinToString(", ")
}

private fun parseMusicBrainzReleaseTracks(media: JSONArray?): List<AlbumDownloadTrack> =
    buildList {
        if (media == null) return@buildList
        var nextGlobalPosition = 1
        for (mediumIndex in 0 until media.length()) {
            val medium = media.optJSONObject(mediumIndex) ?: continue
            val tracks = medium.optJSONArray("tracks")
            val mediumTrackCount = medium.optInt("track-count", tracks?.length() ?: 0).coerceAtLeast(0)
            medium.optJSONObject("pregap")?.toMusicBrainzAlbumTrack(nextGlobalPosition)?.let { track ->
                add(track)
                nextGlobalPosition += 1
            }
            if (tracks != null) {
                for (trackIndex in 0 until tracks.length()) {
                    val item = tracks.optJSONObject(trackIndex) ?: continue
                    item.toMusicBrainzAlbumTrack(nextGlobalPosition + trackIndex)?.let(::add)
                }
            }
            nextGlobalPosition += max(mediumTrackCount, tracks?.length() ?: 0)
        }
    }

private fun JSONObject.toMusicBrainzAlbumTrack(position: Int): AlbumDownloadTrack? {
    val recording = optJSONObject("recording")
    val title = optString("title")
        .ifBlank { recording?.optString("title").orEmpty() }
        .trim()
    if (title.isBlank()) return null
    val duration = optLong("length", recording?.optLong("length", 0L) ?: 0L)
    return AlbumDownloadTrack(
        position = position.coerceAtLeast(1),
        title = title,
        durationMillis = duration,
        recordingId = recording?.optString("id").orEmpty(),
    )
}

private suspend fun searchFreeLegalAlbumReleases(artistQuery: String, albumQuery: String): List<AlbumDownloadRelease> =
    withContext(Dispatchers.IO) {
        val artist = artistQuery.trim()
        val album = albumQuery.trim()
        if (artist.length < 2 || album.length < 2 || PulseOnlineRuntime.settings.offlineMode) return@withContext emptyList()
        coroutineScope {
            val archive = async { runCatching { searchInternetArchiveAlbumReleases(artist, album) }.getOrDefault(emptyList()) }
            val jamendo = async { runCatching { searchJamendoAlbumReleases(artist, album) }.getOrDefault(emptyList()) }
            (jamendo.await() + archive.await())
                .filter { it.tracks.any { track -> track.downloadAllowed && track.downloadUrl.isNotBlank() } }
                .distinctBy { "${it.source}:${it.title.normalizedSearchText()}:${it.artist.normalizedSearchText()}" }
                .sortedWith(
                    compareByDescending<AlbumDownloadRelease> { it.downloadQuality.albumQualityRank() }
                        .thenByDescending { it.tracks.count { track -> track.downloadAllowed } }
                        .thenByDescending { it.score },
                )
                .take(12)
        }
    }

private fun searchJamendoAlbumReleases(artist: String, album: String): List<AlbumDownloadRelease> {
    val clientId = JAMENDO_CLIENT_ID.trim()
    if (clientId.isBlank()) return emptyList()
    val url = buildString {
        append("https://api.jamendo.com/v3.0/albums/tracks/?client_id=${clientId.urlParam()}")
        append("&format=json&limit=8&type=album&audiodlformat=flac")
        append("&artist_name=${artist.urlParam()}")
        append("&namesearch=${album.urlParam()}")
    }
    val json = httpJson(url, connectTimeout = 2200, readTimeout = 5200) ?: return emptyList()
    return parseJamendoAlbumReleases(json)
}

internal fun parseJamendoAlbumReleases(json: JSONObject): List<AlbumDownloadRelease> {
    val results = json.optJSONArray("results") ?: return emptyList()
    return buildList {
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val title = item.optString("name").trim()
            val artist = item.optString("artist_name").trim()
            if (id.isBlank() || title.isBlank() || artist.isBlank()) continue
            val tracks = parseJamendoAlbumTracks(item.optJSONArray("tracks"))
            val downloadable = tracks.count { it.downloadAllowed && it.downloadUrl.isNotBlank() }
            if (downloadable <= 0) continue
            add(
                AlbumDownloadRelease(
                    id = "jamendo:$id",
                    title = title,
                    artist = artist,
                    date = item.optString("releasedate"),
                    format = "FLAC",
                    label = "Jamendo",
                    trackCount = item.optInt("track_count", tracks.size),
                    tracks = tracks,
                    coverUrl = item.optString("image"),
                    source = "Jamendo",
                    license = tracks.firstOrNull { it.recordingId.startsWith("http") }?.recordingId.orEmpty(),
                    downloadQuality = "FLAC",
                    score = 120 + downloadable,
                ),
            )
        }
    }
}

private fun parseJamendoAlbumTracks(array: JSONArray?): List<AlbumDownloadTrack> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.optString("name").trim()
            if (title.isBlank()) continue
            val allowed = item.optBoolean("audiodownload_allowed", false)
            val downloadUrl = item.optString("audiodownload").trim().takeIf { allowed && it.startsWith("http", ignoreCase = true) }.orEmpty()
            add(
                AlbumDownloadTrack(
                    position = item.optInt("position", index + 1).coerceAtLeast(1),
                    title = title,
                    durationMillis = item.optLong("duration", 0L) * 1000L,
                    recordingId = item.optString("license_ccurl"),
                    downloadUrl = downloadUrl,
                    source = "Jamendo",
                    mimeType = "audio/flac",
                    downloadAllowed = downloadUrl.isNotBlank(),
                ),
            )
        }
    }

private fun searchInternetArchiveAlbumReleases(artist: String, album: String): List<AlbumDownloadRelease> {
    val query = "mediatype:audio AND \"$artist\" AND \"$album\""
    val url = "https://archive.org/advancedsearch.php?q=${query.urlParam()}&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=date&rows=10&output=json"
    val json = httpJson(url, connectTimeout = 2200, readTimeout = 5200) ?: return emptyList()
    val docs = json.optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
    return buildList {
        for (index in 0 until docs.length()) {
            val doc = docs.optJSONObject(index) ?: continue
            val identifier = doc.optString("identifier").trim()
            if (identifier.isBlank()) continue
            val metadata = httpJson("https://archive.org/metadata/${identifier.urlPath()}", connectTimeout = 2200, readTimeout = 6200) ?: continue
            parseInternetArchiveRelease(doc, metadata, fallbackArtist = artist, fallbackAlbum = album)?.let(::add)
        }
    }
}

internal fun parseInternetArchiveRelease(doc: JSONObject, metadata: JSONObject, fallbackArtist: String, fallbackAlbum: String): AlbumDownloadRelease? {
    val identifier = doc.optString("identifier").ifBlank { metadata.optString("dir").substringAfterLast("/") }.trim()
    if (identifier.isBlank()) return null
    val title = doc.archiveTitle().ifBlank { metadata.optJSONObject("metadata")?.archiveTitle().orEmpty() }.ifBlank { fallbackAlbum }.trim()
    val artist = doc.archiveCreator().ifBlank { metadata.optJSONObject("metadata")?.archiveCreator().orEmpty() }.ifBlank { fallbackArtist }.trim()
    val files = metadata.optJSONArray("files") ?: return null
    val tracks = parseInternetArchiveFlacTracks(identifier, files)
    if (tracks.isEmpty()) return null
    val meta = metadata.optJSONObject("metadata")
    return AlbumDownloadRelease(
        id = "archive:$identifier",
        title = title,
        artist = artist,
        date = doc.archiveDate().ifBlank { meta?.archiveDate().orEmpty() },
        format = "FLAC",
        label = "Internet Archive",
        trackCount = tracks.size,
        tracks = tracks,
        coverUrl = "https://archive.org/services/img/$identifier",
        source = "Internet Archive",
        license = meta?.optString("licenseurl").orEmpty().ifBlank { meta?.optString("rights").orEmpty() },
        downloadQuality = "FLAC",
        score = 100 + tracks.size,
    )
}

internal fun parseInternetArchiveFlacTracks(identifier: String, files: JSONArray): List<AlbumDownloadTrack> {
    val candidates = buildList {
        for (index in 0 until files.length()) {
            val item = files.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val format = item.optString("format").trim()
            val source = item.optString("source").trim()
            val isFlac = format.contains("FLAC", ignoreCase = true) || name.endsWith(".flac", ignoreCase = true)
            val isOriginal = source.isBlank() || source.equals("original", ignoreCase = true)
            if (!isFlac || !isOriginal || name.contains("_files.", ignoreCase = true)) continue
            val title = name.archiveTrackTitle()
            if (title.isBlank()) continue
            add(
                AlbumDownloadTrack(
                    position = archiveTrackPosition(name) ?: (size + 1),
                    title = title,
                    downloadUrl = "https://archive.org/download/${identifier.urlPath()}/${name.archiveFilePath()}",
                    source = "Internet Archive",
                    mimeType = "audio/flac",
                    downloadAllowed = true,
                ),
            )
        }
    }
    return candidates
        .distinctBy { it.downloadUrl }
        .sortedWith(compareBy<AlbumDownloadTrack> { it.position }.thenBy { it.title.normalizedSearchText() })
}

private fun JSONObject.archiveCreator(): String {
    return when (val raw = opt("creator")) {
        is JSONArray -> buildList {
            for (index in 0 until raw.length()) {
                raw.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }.joinToString(", ")
        is String -> raw.trim()
        else -> ""
    }
}

private fun JSONObject.archiveTitle(): String {
    return when (val raw = opt("title")) {
        is JSONArray -> buildList {
            for (index in 0 until raw.length()) {
                raw.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }.joinToString(", ")
        is String -> raw.trim()
        else -> ""
    }
}

private fun JSONObject.archiveDate(): String {
    return when (val raw = opt("date")) {
        is JSONArray -> raw.optString(0).trim()
        is String -> raw.trim()
        else -> ""
    }
}

private fun String.archiveTrackTitle(): String =
    substringAfterLast('/')
        .substringBeforeLast('.')
        .replace(Regex("""^\s*(?:disc\s*\d+\s*)?[\[(]?\s*\d{1,3}\s*[\])\]._\-\s]+""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun archiveTrackPosition(name: String): Int? =
    Regex("""(?:^|/)\s*(?:disc\s*\d+\s*)?[\[(]?\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
        .find(name)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

private fun String.archiveFilePath(): String =
    split('/')
        .joinToString("/") { part -> part.urlPath() }

private fun String.albumQualityRank(): Int {
    val normalized = lowercase(Locale.US)
    return when {
        "192" in normalized || "hi-res" in normalized || "24-bit" in normalized || "hi_res" in normalized -> 5
        "flac" in normalized || "lossless" in normalized -> 4
        "sacd" in normalized || "dvd-audio" in normalized -> 3
        "mp3" in normalized -> 1
        else -> 2
    }
}
