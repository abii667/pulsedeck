package com.pulsedeck.app

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

internal interface ArtistDiscographyProvider {
    suspend fun resolveArtist(query: ArtistCandidate, allowUnverified: Boolean = false): ArtistCatalogIdentity?
    suspend fun getArtistSnapshot(identity: ArtistCatalogIdentity): ArtistDiscographySnapshot
    suspend fun getDiscographyPage(
        identity: ArtistCatalogIdentity,
        type: ArtistReleaseFilter,
        pageToken: String?,
    ): ArtistDiscographyPage
    suspend fun getReleaseTracklist(releaseId: String): ArtistReleaseTracklist
}

internal data class ArtistCatalogIdentity(
    val providerId: String,
    val artist: ArtistCandidate,
    val displayName: String,
    val normalizedName: String,
    val confidence: ArtistSourceConfidence,
    val sourceChannelId: String? = null,
    val sourceChannelTitle: String? = null,
)

internal enum class ArtistReleaseFilter(val label: String) {
    All("All"),
    Albums("Albums"),
    Eps("EPs"),
    Singles("Singles"),
}

internal data class ArtistDiscographySnapshot(
    val identity: ArtistCatalogIdentity,
    val recentTracks: List<ArtistContinuationSource>,
    val topTracks: List<ArtistContinuationSource>,
    val releases: List<ArtistReleaseGroup>,
    val loadedAtEpochMs: Long = System.currentTimeMillis(),
    val fromCache: Boolean = false,
)

internal data class ArtistDiscographyPage(
    val releases: List<ArtistReleaseGroup>,
    val nextPageToken: String? = null,
)

internal data class ArtistReleaseTracklist(
    val releaseId: String,
    val title: String,
    val artist: String,
    val releaseType: ArtistReleaseType,
    val tracks: List<ArtistContinuationSource>,
    val loadedAtEpochMs: Long = System.currentTimeMillis(),
    val fromCache: Boolean = false,
)

internal class YouTubeArtistDiscographyProvider : ArtistDiscographyProvider {
    override suspend fun resolveArtist(query: ArtistCandidate, allowUnverified: Boolean): ArtistCatalogIdentity? {
        val name = query.displayName.catalogPrimaryArtistName().ifBlank { query.displayName }.trim()
        val key = query.normalizedName.ifBlank { name.normalizedSearchText() }
        if (name.isBlank() || key.isBlank()) return null
        if (!allowUnverified && !query.confidence.acceptsCatalogLookup()) return null
        return ArtistCatalogIdentity(
            providerId = ProviderId,
            artist = query,
            displayName = name,
            normalizedName = key,
            confidence = query.confidence,
            sourceChannelId = query.sourceChannelId,
            sourceChannelTitle = query.sourceChannelTitle,
        )
    }

    override suspend fun getArtistSnapshot(identity: ArtistCatalogIdentity): ArtistDiscographySnapshot {
        val page = getDiscographyPage(identity, ArtistReleaseFilter.Albums, pageToken = null)
        val recent = searchCatalogSources(
            identity = identity,
            query = "${identity.displayName} latest songs official audio",
            reason = "Recent official result",
            limit = 3,
        )
        val top = searchCatalogSources(
            identity = identity,
            query = "${identity.displayName} top songs official audio",
            reason = "Top official result",
            limit = 3,
        )
        return ArtistDiscographySnapshot(
            identity = identity,
            recentTracks = recent,
            topTracks = top,
            releases = page.releases.filter { it.releaseType == ArtistReleaseType.Album }.take(8),
        )
    }

    override suspend fun getDiscographyPage(
        identity: ArtistCatalogIdentity,
        type: ArtistReleaseFilter,
        pageToken: String?,
    ): ArtistDiscographyPage {
        if (pageToken != null) return ArtistDiscographyPage(emptyList(), nextPageToken = null)
        val metadataReleases = when (type) {
            ArtistReleaseFilter.All,
            ArtistReleaseFilter.Albums -> searchMetadataAlbumReleaseGroups(identity)
            ArtistReleaseFilter.Eps,
            ArtistReleaseFilter.Singles -> emptyList()
        }
        val queries = when (type) {
            ArtistReleaseFilter.All -> listOf(
                ArtistReleaseFilter.Albums to "${identity.displayName} albums official audio",
                ArtistReleaseFilter.Eps to "${identity.displayName} EP official audio",
                ArtistReleaseFilter.Singles to "${identity.displayName} singles official audio",
            )
            ArtistReleaseFilter.Albums -> listOf(
                ArtistReleaseFilter.Albums to "${identity.displayName} albums official audio",
                ArtistReleaseFilter.Albums to "${identity.displayName} latest album official audio",
            )
            ArtistReleaseFilter.Eps -> listOf(ArtistReleaseFilter.Eps to "${identity.displayName} EP official audio")
            ArtistReleaseFilter.Singles -> listOf(ArtistReleaseFilter.Singles to "${identity.displayName} singles official audio")
        }
        val searchedReleases = queries
            .flatMap { (filter, query) ->
                val response = searchYouTubeVideos(query, requestLabel = "artist_discography_${filter.name.lowercase(Locale.US)}")
                buildReleaseGroups(identity, filter, response.results)
            }
        val releases = (metadataReleases + searchedReleases)
            .distinctBy { it.id.ifBlank { "${it.artist}|${it.title}|${it.releaseType.name}".normalizedSearchText() } }
            .sortedWith(
                compareByDescending<ArtistReleaseGroup> { it.releaseType.releaseSortWeight }
                    .thenByDescending { it.releaseYearHint }
                    .thenByDescending { it.trackCountHint }
                    .thenByDescending { it.items.size }
                    .thenByDescending { it.confidence.discographyRank }
                    .thenBy { it.title.normalizedSearchText() },
            )
            .take(if (type == ArtistReleaseFilter.All) 8 else 12)
        return ArtistDiscographyPage(releases = releases, nextPageToken = null)
    }

    override suspend fun getReleaseTracklist(releaseId: String): ArtistReleaseTracklist {
        val spec = parseReleaseId(releaseId) ?: return ArtistReleaseTracklist(
            releaseId = releaseId,
            title = "",
            artist = "",
            releaseType = ArtistReleaseType.Unknown,
            tracks = emptyList(),
        )
        val response = searchYouTubeVideos(
            "${spec.artist} ${spec.title} ${spec.type.label} official audio",
            requestLabel = "artist_release_tracklist",
        )
        val tracks = response.results
            .asSequence()
            .mapIndexedNotNull { index, result ->
                val source = result.source
                    .takeIf { it.kind == YouTubeSourceKind.Video && !it.isPodcast }
                    ?.takeIf { it.matchesCatalogArtist(spec.artistKey) || it.title.normalizedSearchText().contains(spec.titleKey) }
                    ?: return@mapIndexedNotNull null
                val playable = source.copy(
                    reviewState = YouTubeReviewState.Accepted,
                    thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
                    albumTitleHint = spec.title,
                    albumTrackNumberHint = source.albumTrackNumberHint.takeIf { it > 0 } ?: index + 1,
                )
                ArtistContinuationSource(
                    source = playable,
                    artist = spec.toArtistCandidate(),
                    reason = "Lazy matched from ${spec.type.label}",
                    resultScore = result.score,
                    views = result.views,
                    rank = index + 1,
                )
            }
            .distinctBy { it.source.streamDistinctKey() }
            .take(24)
            .toList()
            .let { tracks ->
                tracks.map { item ->
                    item.copy(source = item.source.copy(albumTrackTotalHint = tracks.size))
                }
            }
        return ArtistReleaseTracklist(
            releaseId = releaseId,
            title = spec.title,
            artist = spec.artist,
            releaseType = spec.type,
            tracks = tracks,
        )
    }

    private suspend fun searchCatalogSources(
        identity: ArtistCatalogIdentity,
        query: String,
        reason: String,
        limit: Int,
    ): List<ArtistContinuationSource> {
        val response = searchYouTubeVideos(query, requestLabel = "artist_discography_tracks")
        return response.results
            .asSequence()
            .mapIndexedNotNull { index, result ->
                val source = result.source
                    .takeIf { it.kind == YouTubeSourceKind.Video && !it.isPodcast }
                    ?.takeIf { it.matchesCatalogArtist(identity.normalizedName) }
                    ?: return@mapIndexedNotNull null
                ArtistContinuationSource(
                    source = source.copy(
                        reviewState = YouTubeReviewState.Accepted,
                        thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
                    ),
                    artist = identity.artist.copy(confidence = identity.confidence),
                    reason = reason,
                    resultScore = result.score,
                    views = result.views,
                    rank = index + 1,
                )
            }
            .distinctBy { it.source.streamDistinctKey() }
            .take(limit)
            .toList()
    }

    private fun buildReleaseGroups(
        identity: ArtistCatalogIdentity,
        filter: ArtistReleaseFilter,
        results: List<YouTubeSearchResult>,
    ): List<ArtistReleaseGroup> {
        val seeds = results.mapIndexedNotNull { index, result ->
            val source = result.source
                .takeIf { it.kind == YouTubeSourceKind.Video && !it.isPodcast }
                ?.takeIf { it.matchesCatalogArtist(identity.normalizedName) || it.title.normalizedSearchText().contains(identity.normalizedName) }
                ?: return@mapIndexedNotNull null
            val releaseType = inferReleaseTypeForFilter(filter, source)
            if (filter == ArtistReleaseFilter.Albums && !source.hasAlbumCatalogEvidence(identity.displayName)) {
                return@mapIndexedNotNull null
            }
            val releaseTitle = source.catalogReleaseTitle(identity.displayName, releaseType) ?: return@mapIndexedNotNull null
            val item = ArtistContinuationSource(
                source = source.copy(
                    reviewState = YouTubeReviewState.Accepted,
                    thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
                    albumTitleHint = releaseTitle,
                    albumTrackNumberHint = source.albumTrackNumberHint.takeIf { it > 0 } ?: index + 1,
                ),
                artist = identity.artist.copy(confidence = identity.confidence),
                reason = "${releaseType.label} preview",
                resultScore = result.score,
                views = result.views,
                rank = index + 1,
            ).toArtistContinuationItem(ArtistContinuationSection.LatestProject)
            ReleaseSeed(releaseTitle, releaseType, result.source.albumYearHint, result.score, result.views, item)
        }
        return seeds
            .groupBy { "${it.type.name}|${it.title.normalizedSearchText()}" }
            .values
            .mapNotNull { group ->
                val first = group.firstOrNull() ?: return@mapNotNull null
                val items = group
                    .sortedWith(
                        compareBy<ReleaseSeed> { it.item.source.albumTrackNumberHint.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
                            .thenByDescending { it.score }
                            .thenBy { it.item.source.title.normalizedSearchText() },
                    )
                    .take(3)
                    .map { seed ->
                        seed.item.copy(
                            source = seed.item.source.copy(albumTrackTotalHint = group.size.coerceAtLeast(seed.item.source.albumTrackTotalHint)),
                        )
                    }
                val confidence = items.maxByOrNull { it.artist.confidence.discographyRank }?.artist?.confidence ?: identity.confidence
                ArtistReleaseGroup(
                    id = encodeReleaseId(identity, first.type, first.title),
                    title = first.title,
                    artist = identity.displayName,
                    items = items,
                    releaseType = first.type,
                    releaseYearHint = group.maxOfOrNull { it.year } ?: 0,
                    trackCountHint = group.size,
                    coverUrl = items.firstNotNullOfOrNull { it.source.bestThumbnailUrl() ?: it.source.thumbnailUrl },
                    confidence = confidence,
                )
            }
    }

    private suspend fun searchMetadataAlbumReleaseGroups(identity: ArtistCatalogIdentity): List<ArtistReleaseGroup> =
        runCatching {
            searchAlbumMetadataReleases(
                artistQuery = identity.displayName,
                albumQuery = "",
                premiumSources = emptyList(),
            )
                .asSequence()
                .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                .filterNot { it.looksLikeSingleReleaseOnly() }
                .map { it.toArtistReleaseGroup(identity) }
                .filter { it.releaseType == ArtistReleaseType.Album }
                .toList()
        }.getOrDefault(emptyList())

    private fun AlbumDownloadRelease.toArtistReleaseGroup(identity: ArtistCatalogIdentity): ArtistReleaseGroup {
        val items = tracks
            .mapNotNull { track ->
                val source = track.matchedSource ?: return@mapNotNull null
                ArtistContinuationSource(
                    source = source.copy(
                        reviewState = YouTubeReviewState.Accepted,
                        thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
                        albumTitleHint = title,
                        albumTrackNumberHint = source.albumTrackNumberHint.takeIf { it > 0 } ?: track.position,
                        albumTrackTotalHint = trackCount.takeIf { it > 0 } ?: tracks.size,
                        albumYearHint = date.take(4).toIntOrNull() ?: source.albumYearHint,
                    ),
                    artist = identity.artist.copy(confidence = identity.confidence),
                    reason = "Album preview",
                    resultScore = score,
                    views = 0L,
                    rank = track.position,
                ).toArtistContinuationItem(ArtistContinuationSection.LatestProject)
            }
            .distinctBy { it.source.streamDistinctKey() }
            .take(3)
        val countHint = trackCount.takeIf { it > 0 } ?: tracks.size
        return ArtistReleaseGroup(
            id = encodeReleaseId(identity, ArtistReleaseType.Album, title),
            title = title,
            artist = artist.ifBlank { identity.displayName },
            items = items,
            releaseType = ArtistReleaseType.Album,
            releaseYearHint = date.take(4).toIntOrNull() ?: 0,
            trackCountHint = countHint,
            tracklistPreview = tracks,
            coverUrl = coverUrl.ifBlank { items.firstNotNullOfOrNull { it.source.bestThumbnailUrl() ?: it.source.thumbnailUrl }.orEmpty() }
                .ifBlank { null },
            confidence = identity.confidence,
        )
    }

    private fun AlbumDownloadRelease.looksLikeSingleReleaseOnly(): Boolean {
        val text = "$title $format".normalizedSearchText()
        return Regex("""\b(single)\b""").containsMatchIn(text) ||
            trackCount == 1 ||
            tracks.size == 1
    }

    private fun YouTubeSource.hasAlbumCatalogEvidence(artistName: String): Boolean {
        val text = listOf(title, albumTitleHint, author, channelTitle).joinToString(" ")
        val normalized = text.normalizedSearchText()
        val explicitAlbumMarker = Regex("""\b(album|lp|full album)\b""").containsMatchIn(normalized) ||
            Regex("""(?i)\bfrom\s+(?:the\s+)?album\b|\btaken\s+from\b""").containsMatchIn(text)
        val cleanTitleKey = title.cleanStreamTitle()
            .cleanStrictStreamAlbumTitle()
            .normalizedSearchText()
        val strictAlbum = strictStreamAlbumTitle(artistName.cleanStreamArtistName())
        val strictAlbumKey = strictAlbum.orEmpty().normalizedSearchText()
        val albumHintKey = albumTitleHint.cleanStrictStreamAlbumTitle().normalizedSearchText()
        val likelyStandaloneSingle = Regex("""\b(single)\b""").containsMatchIn(normalized) ||
            albumTrackTotalHint == 1 ||
            (!explicitAlbumMarker && cleanTitleKey.isNotBlank() && cleanTitleKey == albumHintKey)
        if (likelyStandaloneSingle) return false
        return explicitAlbumMarker ||
            albumTrackTotalHint >= 7 ||
            (
                strictAlbum != null &&
                    strictAlbumKey.isNotBlank() &&
                    strictAlbumKey != cleanTitleKey &&
                    (albumTrackNumberHint > 0 || albumYearHint > 0)
                )
    }

    private fun YouTubeSource.catalogReleaseTitle(artistName: String, releaseType: ArtistReleaseType): String? {
        val strict = albumTitleHint.cleanStrictStreamAlbumTitle().takeIf { it.isReliableStrictAlbumTitle() }
            ?: strictStreamAlbumTitle(artistName.cleanStreamArtistName())
        if (strict != null) return strict
        if (releaseType == ArtistReleaseType.Single) {
            return title.cleanStreamTitle()
                .cleanStrictStreamAlbumTitle()
                .takeIf { it.isReliableStrictAlbumTitle() }
        }
        return title.releaseTitleFromSearchText(releaseType)
    }

    private fun String.releaseTitleFromSearchText(type: ArtistReleaseType): String? {
        val marker = when (type) {
            ArtistReleaseType.Album -> """album|lp|full album"""
            ArtistReleaseType.Ep -> """ep"""
            ArtistReleaseType.Project -> """project|mixtape"""
            ArtistReleaseType.Single -> """single"""
            ArtistReleaseType.Unknown -> """album|ep|single|project|mixtape|lp"""
        }
        Regex("""(?i)(?:^|[-:|])\s*([^|:(){}\[\]]{2,80}?)\s+(?:$marker)\b""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanStrictStreamAlbumTitle()
            ?.takeIf { it.isReliableStrictAlbumTitle() }
            ?.let { return it }
        Regex("""(?i)\b(?:$marker)\s*[:\-]\s*([^|:(){}\[\]]{2,80})""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanStrictStreamAlbumTitle()
            ?.takeIf { it.isReliableStrictAlbumTitle() }
            ?.let { return it }
        return null
    }

    private fun inferReleaseTypeForFilter(filter: ArtistReleaseFilter, source: YouTubeSource): ArtistReleaseType {
        val text = "${source.title} ${source.albumTitleHint}".normalizedSearchText()
        return when {
            Regex("""\b(ep)\b""").containsMatchIn(text) || filter == ArtistReleaseFilter.Eps -> ArtistReleaseType.Ep
            Regex("""\b(single)\b""").containsMatchIn(text) || filter == ArtistReleaseFilter.Singles -> ArtistReleaseType.Single
            Regex("""\b(album|lp|full album)\b""").containsMatchIn(text) || filter == ArtistReleaseFilter.Albums -> ArtistReleaseType.Album
            else -> ArtistReleaseType.Project
        }
    }

    private fun YouTubeSource.matchesCatalogArtist(artistKey: String): Boolean {
        if (artistKey.isBlank()) return false
        val metadataArtist = smartYouTubeMusicMetadata(title, author)
            .takeIf { it.confidence >= 70 }
            ?.artist
            ?.cleanStreamArtistName()
            ?.normalizedSearchText()
            .orEmpty()
        if (metadataArtist == artistKey) return true
        return listOf(author, channelTitle, title, albumTitleHint)
            .joinToString(" ")
            .cleanYouTubeArtistCandidate()
            .cleanStreamArtistName()
            .normalizedSearchText()
            .contains(artistKey)
    }

    private fun encodeReleaseId(identity: ArtistCatalogIdentity, type: ArtistReleaseType, title: String): String =
        listOf(ProviderId, type.name, identity.displayName, title)
            .joinToString("|") { URLEncoder.encode(it, "UTF-8") }

    private fun parseReleaseId(releaseId: String): ReleaseSpec? {
        val parts = releaseId.split("|").map { URLDecoder.decode(it, "UTF-8") }
        if (parts.size < 4 || parts[0] != ProviderId) return null
        val type = runCatching { ArtistReleaseType.valueOf(parts[1]) }.getOrDefault(ArtistReleaseType.Unknown)
        val artist = parts[2].trim()
        val title = parts.drop(3).joinToString("|").trim()
        if (artist.isBlank() || title.isBlank()) return null
        return ReleaseSpec(
            artist = artist,
            artistKey = artist.cleanStreamArtistName().normalizedSearchText(),
            title = title,
            titleKey = title.normalizedSearchText(),
            type = type,
        )
    }

    private fun ReleaseSpec.toArtistCandidate(): ArtistCandidate =
        ArtistCandidate(
            displayName = artist,
            normalizedName = artistKey,
            role = ArtistRole.Primary,
            evidence = setOf(ArtistEvidence.SearchResultMatch),
            sourceChannelId = null,
            sourceChannelTitle = null,
            confidence = ArtistSourceConfidence.HighConfidence,
        )

    private data class ReleaseSeed(
        val title: String,
        val type: ArtistReleaseType,
        val year: Int,
        val score: Int,
        val views: Long,
        val item: ArtistContinuationItem,
    )

    private data class ReleaseSpec(
        val artist: String,
        val artistKey: String,
        val title: String,
        val titleKey: String,
        val type: ArtistReleaseType,
    )

    private companion object {
        const val ProviderId = "youtube-search"
    }
}

private fun String.catalogPrimaryArtistName(): String {
    val cleaned = cleanStreamArtistName()
    return cleaned
        .substringBefore(",")
        .substringBefore(" & ")
        .replace(Regex("""\s+(?:feat\.?|ft\.?|featuring)\b.+$""", RegexOption.IGNORE_CASE), "")
        .trim()
}

internal val ArtistReleaseType.releaseSortWeight: Int
    get() = when (this) {
        ArtistReleaseType.Album -> 4
        ArtistReleaseType.Ep -> 3
        ArtistReleaseType.Project -> 2
        ArtistReleaseType.Single -> 1
        ArtistReleaseType.Unknown -> 0
    }

internal val ArtistSourceConfidence.discographyRank: Int
    get() = when (this) {
        ArtistSourceConfidence.VerifiedOfficial -> 4
        ArtistSourceConfidence.HighConfidence -> 3
        ArtistSourceConfidence.TopicOrAutoGenerated -> 2
        ArtistSourceConfidence.Unverified -> 1
        ArtistSourceConfidence.None -> 0
    }

private fun ArtistSourceConfidence.acceptsCatalogLookup(): Boolean =
    this == ArtistSourceConfidence.VerifiedOfficial ||
        this == ArtistSourceConfidence.HighConfidence ||
        this == ArtistSourceConfidence.TopicOrAutoGenerated
