package com.pulsedeck.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min

internal suspend fun matchAlbumTracksToPremiumDeck(
    release: AlbumDownloadRelease,
    existingSources: List<YouTubeSource>,
    repairOnly: Boolean = false,
    prepareSource: suspend (YouTubeSource) -> YouTubeSource? = { it },
    onTrackStarted: suspend (AlbumDownloadTrack, Int) -> Unit = { _, _ -> },
    onTrackMatched: suspend (AlbumDownloadRelease, AlbumDownloadTrack, Int) -> Unit = { _, _, _ -> },
): AlbumDownloadRelease =
    withContext(Dispatchers.IO) {
        if (release.tracks.isEmpty() || PulseOnlineRuntime.settings.premiumDeckOfflineActive) return@withContext release
        val localCandidates = existingSources
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        val matchedTracks = mutableListOf<AlbumDownloadTrack>()
        val sortedTracks = release.tracks.sortedBy { it.position }
        val tracksToProcess = if (repairOnly) {
            sortedTracks.filter { it.needsAlbumMatchRepair(existingSources) }
        } else {
            sortedTracks
        }
        val usedMatchKeys = sortedTracks
            .filterNot { track -> tracksToProcess.any { it.position == track.position && it.title == track.title } }
            .mapNotNull { it.matchedSource?.streamDistinctKey() }
            .toMutableSet()
        val updatedByPosition = sortedTracks.associateBy { it.position }.toMutableMap()
        tracksToProcess.forEachIndexed { index, track ->
            onTrackStarted(track, index)
            val previousMatch = track.matchedSource
            val previousKey = previousMatch?.streamDistinctKey()
            val availableLocalCandidates = localCandidates.filter { candidate ->
                val key = candidate.streamDistinctKey()
                key !in usedMatchKeys || key == previousKey
            }
            val localMatch = findBestAlbumTrackMatch(release, track, availableLocalCandidates)
            val onlineCandidates = if (localMatch?.score?.let { it >= 86 } == true) {
                emptyList()
            } else {
                searchAlbumTrackOnlineCandidates(release, track)
                    .filter { candidate ->
                        val key = candidate.streamDistinctKey()
                        key !in usedMatchKeys || key == previousKey
                    }
            }
            val strongOnlineMatch = findBestAlbumTrackMatch(release, track, onlineCandidates)
            val fallbackOnlineMatch = findFallbackAlbumTrackMatch(release, track, onlineCandidates)
            val rankedCandidates = rankAlbumTrackMatchCandidates(
                release = release,
                track = track,
                candidates = availableLocalCandidates + onlineCandidates,
                preferred = listOfNotNull(localMatch, strongOnlineMatch, fallbackOnlineMatch),
            )
            var preparedMatch: AlbumTrackMatchCandidate? = null
            for (candidate in rankedCandidates.take(4)) {
                val albumSource = candidate.source.toAlbumMatchedSource(release, track)
                val preparedSource = runCatching { prepareSource(albumSource) }.getOrNull()
                if (preparedSource != null) {
                    preparedMatch = candidate.copy(source = preparedSource)
                    break
                }
            }
            val match = preparedMatch ?: rankedCandidates.firstOrNull()
            val candidateSources = rankedCandidates
                .map { it.source.toAlbumMatchedSource(release, track) }
                .distinctBy { it.streamDistinctKey() }
                .take(5)
            val updatedTrack = if (match != null && match.score >= 32) {
                val matchedSource = match.source.toAlbumMatchedSource(release, track)
                val verified = preparedMatch != null && preparedMatch.source.streamDistinctKey() == matchedSource.streamDistinctKey()
                usedMatchKeys += matchedSource.streamDistinctKey()
                track.copy(
                    matchedSource = matchedSource,
                    matchScore = match.score.coerceIn(0, 100),
                    matchReason = buildAlbumMatchReason(match.reason, verified, candidateSources.size),
                    matchVerified = verified,
                    matchCandidates = (listOf(matchedSource) + candidateSources)
                        .distinctBy { it.streamDistinctKey() }
                        .take(5),
                )
            } else {
                previousMatch?.streamDistinctKey()?.let { usedMatchKeys += it }
                track.copy(
                    matchedSource = previousMatch,
                    matchScore = if (previousMatch != null) track.matchScore else 0,
                    matchReason = if (previousMatch != null) track.matchReason else "",
                    matchCandidates = (track.matchCandidates + candidateSources)
                        .distinctBy { it.streamDistinctKey() }
                        .take(5),
                )
            }
            updatedByPosition[updatedTrack.position] = updatedTrack
            matchedTracks += updatedTrack
            onTrackMatched(
                release.copy(tracks = sortedTracks.map { updatedByPosition[it.position] ?: it }.sortedBy { it.position }),
                updatedTrack,
                index,
            )
            if (index < tracksToProcess.lastIndex) delay(180L)
        }
        release.copy(tracks = sortedTracks.map { updatedByPosition[it.position] ?: it }.sortedBy { it.position })
    }

private suspend fun searchAlbumTrackOnlineCandidates(release: AlbumDownloadRelease, track: AlbumDownloadTrack): List<YouTubeSource> {
    val queries = buildList {
        add(listOf(release.artist, track.title, release.title, "official audio").filter { it.isNotBlank() }.joinToString(" "))
        add(listOf(release.artist, track.title, "official audio").filter { it.isNotBlank() }.joinToString(" "))
        add(listOf(release.artist, track.title).filter { it.isNotBlank() }.joinToString(" "))
    }
        .map { it.trim() }
        .filter { it.length >= 3 }
        .distinctBy { it.normalizedSearchText() }
    val results = mutableListOf<YouTubeSource>()
    for ((index, query) in queries.withIndex()) {
        val found = withTimeoutOrNull(6200L) { searchYouTubeVideos(query).results.map { it.source } }.orEmpty()
        results += found
        val best = findBestAlbumTrackMatch(release, track, results)
        if (best != null && best.score >= 58) break
        if (found.isNotEmpty() && index < queries.lastIndex) delay(120L)
    }
    return results.distinctBy { it.streamDistinctKey() }
}

internal suspend fun prepareAlbumMatchSource(context: Context, source: YouTubeSource): YouTubeSource? =
    withContext(Dispatchers.IO) {
        if (source.isOfflineSaved()) return@withContext source
        if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) return@withContext null
        val streamPolicy = NetworkPolicyController.currentPolicy(context, PulseOnlineRuntime.settings)
        source.freshCachedStreamUrl(policy = streamPolicy)?.let { return@withContext source }
        val resolved = runCatching { resolveYouTubeAudio(context, source, streamPolicy) }.getOrNull() ?: return@withContext null
        source.copy(
            status = if (source.status == YouTubeSourceStatus.Downloaded) source.status else YouTubeSourceStatus.Cached,
            cachedUri = resolved.streamUrl,
            cachedMillis = System.currentTimeMillis(),
            durationMillis = resolved.durationMillis.takeIf { it > 0L } ?: source.durationMillis,
            thumbnailUrl = resolved.thumbnailUrl ?: source.thumbnailUrl,
            isPodcast = resolved.isPodcast,
            chapters = resolved.chapters.ifEmpty { source.chapters },
            sponsorSegments = resolved.sponsorSegments.ifEmpty { source.sponsorSegments },
        )
    }

private data class AlbumTrackMatchCandidate(
    val source: YouTubeSource,
    val score: Int,
    val reason: String,
)

private fun rankAlbumTrackMatchCandidates(
    release: AlbumDownloadRelease,
    track: AlbumDownloadTrack,
    candidates: List<YouTubeSource>,
    preferred: List<AlbumTrackMatchCandidate> = emptyList(),
): List<AlbumTrackMatchCandidate> {
    val scored = candidates
        .distinctBy { it.streamDistinctKey() }
        .mapIndexedNotNull { index, source ->
            val score = scoreAlbumTrackSource(release, track, source)
            if (score > 0) {
                AlbumTrackMatchCandidate(source, score, albumTrackMatchReason(score, source))
            } else {
                fallbackAlbumTrackCandidate(release, track, source, index)
            }
        }
    return (preferred + scored)
        .filter { it.score >= 32 }
        .distinctBy { it.source.streamDistinctKey() }
        .sortedWith(
            compareByDescending<AlbumTrackMatchCandidate> { it.score }
                .thenByDescending { it.source.isOfflineSaved() }
                .thenByDescending { it.source.channelVerified || it.source.author.contains("topic", ignoreCase = true) }
                .thenBy { it.source.title.normalizedSearchText() },
        )
        .take(8)
}

private fun findBestAlbumTrackMatch(
    release: AlbumDownloadRelease,
    track: AlbumDownloadTrack,
    candidates: List<YouTubeSource>,
): AlbumTrackMatchCandidate? =
    candidates
        .distinctBy { it.streamDistinctKey() }
        .mapNotNull { source ->
            val score = scoreAlbumTrackSource(release, track, source)
            if (score <= 0) null else AlbumTrackMatchCandidate(source, score, albumTrackMatchReason(score, source))
        }
        .maxByOrNull { it.score }

private fun findFallbackAlbumTrackMatch(
    release: AlbumDownloadRelease,
    track: AlbumDownloadTrack,
    candidates: List<YouTubeSource>,
): AlbumTrackMatchCandidate? {
    return candidates
        .distinctBy { it.streamDistinctKey() }
        .mapIndexedNotNull { index, source -> fallbackAlbumTrackCandidate(release, track, source, index) }
        .maxByOrNull { it.score }
}

private fun fallbackAlbumTrackCandidate(
    release: AlbumDownloadRelease,
    track: AlbumDownloadTrack,
    source: YouTubeSource,
    index: Int,
): AlbumTrackMatchCandidate? {
    val trackTokens = streamTitleTokens(track.title)
    if (trackTokens.isEmpty()) return null
    val trackKey = track.title.normalizedSearchText()
    val artistKey = release.artist.cleanStreamArtistName().normalizedSearchText()
    val sourceKey = "${source.title} ${source.author}".normalizedSearchText()
    val sourceTokens = streamTitleTokens("${source.title} ${source.author}")
    val overlap = trackTokens.count { it in sourceTokens }
    val directTitleMatch = trackKey.isNotBlank() && sourceKey.contains(trackKey)
    if (overlap <= 0 && !directTitleMatch) return null
    val artistTouched = artistKey.isNotBlank() && sourceKey.contains(artistKey)
    val score = (
        34 +
            overlap * 5 +
            (if (directTitleMatch) 8 else 0) +
            (if (artistTouched) 8 else 0) -
            min(index, 8)
        )
        .coerceIn(32, 57)
    return AlbumTrackMatchCandidate(source, score, "Fallback album match  |  $score%  |  verify")
}

private fun buildAlbumMatchReason(reason: String, verified: Boolean, candidateCount: Int): String =
    buildList {
        add(reason)
        if (verified) add("playable verified") else add("not verified")
        if (candidateCount > 1) add("$candidateCount candidates")
    }.distinct().joinToString("  |  ")

private fun scoreAlbumTrackSource(release: AlbumDownloadRelease, track: AlbumDownloadTrack, source: YouTubeSource): Int {
    val trackTokens = streamTitleTokens(track.title)
    if (trackTokens.isEmpty()) return 0
    val sourceTokens = streamTitleTokens("${source.title} ${source.author}")
    val titleOverlap = trackTokens.count { it in sourceTokens }
    val titleScore = ((titleOverlap.toFloat() / trackTokens.size.toFloat()) * 42f).toInt()
    if (titleScore < 18) return 0
    val artistKey = release.artist.cleanStreamArtistName().normalizedSearchText()
    val sourceArtistKey = source.author.cleanStreamArtistName().normalizedSearchText()
    val sourceTitleKey = source.title.normalizedSearchText()
    val artistScore = when {
        artistKey.isBlank() -> 0
        sourceArtistKey == artistKey -> 28
        sourceArtistKey.contains(artistKey) || artistKey.contains(sourceArtistKey) -> 22
        sourceTitleKey.contains(artistKey) -> 16
        else -> 0
    }
    val albumKey = release.title.cleanStrictStreamAlbumTitle().normalizedSearchText()
    val sourceAlbumKey = source.strictStreamAlbumTitle()?.normalizedSearchText().orEmpty()
    val albumScore = when {
        albumKey.isBlank() -> 0
        sourceAlbumKey == albumKey -> 16
        sourceAlbumKey.contains(albumKey) || albumKey.contains(sourceAlbumKey) -> 10
        sourceTitleKey.contains(albumKey) -> 8
        else -> 0
    }
    val durationScore = when {
        track.durationMillis <= 0L || source.durationMillis <= 0L -> 6
        abs(track.durationMillis - source.durationMillis) <= 2_500L -> 16
        abs(track.durationMillis - source.durationMillis) <= 8_000L -> 11
        abs(track.durationMillis - source.durationMillis) <= 18_000L -> 4
        else -> -12
    }
    val qualityScore = when {
        source.channelVerified -> 5
        source.author.contains("topic", ignoreCase = true) -> 5
        source.title.contains("official", ignoreCase = true) -> 4
        source.title.contains("lyrics", ignoreCase = true) -> -4
        source.title.contains("live", ignoreCase = true) -> -6
        else -> 0
    }
    val savedScore = when {
        source.downloadState == YouTubeDownloadState.Downloaded || source.status == YouTubeSourceStatus.Downloaded -> 5
        source.bookmarked || source.reaction == YouTubeReaction.Liked -> 3
        else -> 0
    }
    return (titleScore + artistScore + albumScore + durationScore + qualityScore + savedScore).coerceIn(0, 100)
}

private fun albumTrackMatchReason(score: Int, source: YouTubeSource): String =
    buildList {
        add("PremiumDeck match")
        add("$score%")
        if (source.channelVerified || source.author.contains("topic", ignoreCase = true)) add("official")
        if (source.downloadState == YouTubeDownloadState.Downloaded || source.status == YouTubeSourceStatus.Downloaded) add("already local")
    }.joinToString("  |  ")

private fun YouTubeSource.toAlbumMatchedSource(release: AlbumDownloadRelease, track: AlbumDownloadTrack): YouTubeSource =
    copy(
        title = track.title,
        author = release.artist,
        thumbnailUrl = bestThumbnailUrl() ?: release.coverUrl.takeIf { it.startsWith("http", ignoreCase = true) } ?: thumbnailUrl,
        albumTitleHint = release.title,
        albumTrackNumberHint = track.position,
        albumTrackTotalHint = release.tracks.size.takeIf { it > 0 } ?: release.trackCount,
        albumYearHint = release.date.take(4).toIntOrNull() ?: 0,
        durationMillis = durationMillis.takeIf { it > 0L } ?: track.durationMillis,
        reviewState = YouTubeReviewState.Accepted,
    )

internal fun AlbumDownloadTrack.currentMatchedSource(sources: List<YouTubeSource>): YouTubeSource? {
    val matched = matchedSource ?: return null
    val matchedKey = matched.streamDistinctKey()
    return sources.firstOrNull { source ->
        source.id == matched.id || source.url == matched.url || source.streamDistinctKey() == matchedKey
    } ?: matched
}

internal fun albumSourceLookup(sources: List<YouTubeSource>): Map<String, YouTubeSource> =
    buildMap {
        sources.forEach { source ->
            listOf(source.id, source.url, source.streamDistinctKey())
                .filter { it.isNotBlank() }
                .forEach { key ->
                    if (key !in this) this[key] = source
                }
        }
    }

private fun Map<String, YouTubeSource>.albumSourceFor(source: YouTubeSource): YouTubeSource? =
    this[source.id] ?: this[source.url] ?: this[source.streamDistinctKey()]

internal fun AlbumDownloadTrack.currentMatchedSource(sourceLookup: Map<String, YouTubeSource>): YouTubeSource? {
    val matched = matchedSource ?: return null
    return sourceLookup.albumSourceFor(matched) ?: matched
}

internal fun AlbumDownloadTrack.matchCandidateSources(sources: List<YouTubeSource>): List<YouTubeSource> =
    (listOfNotNull(matchedSource) + matchCandidates)
        .map { candidate ->
            val key = candidate.streamDistinctKey()
            sources.firstOrNull { source ->
                source.id == candidate.id || source.url == candidate.url || source.streamDistinctKey() == key
            } ?: candidate
        }
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        .distinctBy { it.streamDistinctKey() }

internal fun AlbumDownloadTrack.matchCandidateSources(sourceLookup: Map<String, YouTubeSource>): List<YouTubeSource> =
    (listOfNotNull(matchedSource) + matchCandidates)
        .map { candidate -> sourceLookup.albumSourceFor(candidate) ?: candidate }
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        .distinctBy { it.streamDistinctKey() }

internal fun AlbumDownloadRelease.withSelectedAlbumTrackMatch(
    track: AlbumDownloadTrack,
    selectedSource: YouTubeSource,
    sources: List<YouTubeSource>,
): AlbumDownloadRelease {
    val targetTrack = tracks.firstOrNull { candidate ->
        candidate.position == track.position &&
            candidate.title == track.title &&
            (track.recordingId.isBlank() || candidate.recordingId == track.recordingId)
    } ?: return this
    val liveCandidates = targetTrack.matchCandidateSources(sources)
    val resolvedSelected = liveCandidates.firstOrNull { candidate ->
        candidate.id == selectedSource.id ||
            candidate.url == selectedSource.url ||
            candidate.streamDistinctKey() == selectedSource.streamDistinctKey()
    } ?: selectedSource
    val matchedSource = resolvedSelected.toAlbumMatchedSource(this, targetTrack)
    val score = scoreAlbumTrackSource(this, targetTrack, resolvedSelected)
        .takeIf { it > 0 }
        ?: scoreAlbumTrackSource(this, targetTrack, matchedSource)
        .takeIf { it > 0 }
        ?: 42
    val verified = score >= 70 &&
        resolvedSelected.status != YouTubeSourceStatus.ResolverNeeded &&
        resolvedSelected.downloadState != YouTubeDownloadState.Failed
    val preservedCandidates = (listOf(matchedSource) + liveCandidates + targetTrack.matchCandidates)
        .map { candidate ->
            val key = candidate.streamDistinctKey()
            if (key == matchedSource.streamDistinctKey()) matchedSource else candidate
        }
        .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
        .distinctBy { it.streamDistinctKey() }
    val updatedTrack = targetTrack.copy(
        matchedSource = matchedSource,
        matchScore = score.coerceIn(0, 100),
        matchReason = buildAlbumMatchReason(albumTrackMatchReason(score, resolvedSelected), verified, preservedCandidates.size),
        matchVerified = verified,
        matchCandidates = preservedCandidates,
    )
    return copy(
        tracks = tracks.map { candidate ->
            if (candidate.position == targetTrack.position &&
                candidate.title == targetTrack.title &&
                candidate.recordingId == targetTrack.recordingId
            ) {
                updatedTrack
            } else {
                candidate
            }
        },
    )
}

private fun AlbumDownloadTrack.needsAlbumMatchRepair(sources: List<YouTubeSource>): Boolean {
    val liveSource = currentMatchedSource(sources) ?: return true
    return matchScore < 70 ||
        !matchVerified ||
        liveSource.status == YouTubeSourceStatus.ResolverNeeded ||
        liveSource.downloadState == YouTubeDownloadState.Failed
}

private fun AlbumDownloadTrack.needsAlbumMatchRepair(sourceLookup: Map<String, YouTubeSource>): Boolean {
    val liveSource = currentMatchedSource(sourceLookup) ?: return true
    return matchScore < 70 ||
        !matchVerified ||
        liveSource.status == YouTubeSourceStatus.ResolverNeeded ||
        liveSource.downloadState == YouTubeDownloadState.Failed
}

internal fun AlbumDownloadRelease.albumTracksNeedingRepair(sources: List<YouTubeSource>): List<AlbumDownloadTrack> =
    tracks.sortedBy { it.position }.filter { it.needsAlbumMatchRepair(sources) }

internal fun AlbumDownloadRelease.albumTracksNeedingRepair(sourceLookup: Map<String, YouTubeSource>): List<AlbumDownloadTrack> =
    tracks.sortedBy { it.position }.filter { it.needsAlbumMatchRepair(sourceLookup) }

private fun AlbumDownloadRelease.liveMatchedSources(sources: List<YouTubeSource>): List<YouTubeSource> =
    tracks
        .sortedBy { it.position }
        .mapNotNull { it.currentMatchedSource(sources) }
        .distinctBy { it.streamDistinctKey() }

private fun AlbumDownloadRelease.liveMatchedSources(sourceLookup: Map<String, YouTubeSource>): List<YouTubeSource> =
    tracks
        .sortedBy { it.position }
        .mapNotNull { it.currentMatchedSource(sourceLookup) }
        .distinctBy { it.streamDistinctKey() }

internal fun AlbumDownloadRelease.albumProjectCandidateSources(sources: List<YouTubeSource>): List<YouTubeSource> =
    tracks
        .sortedBy { it.position }
        .flatMap { it.matchCandidateSources(sources) }
        .distinctBy { it.streamDistinctKey() }

internal fun AlbumDownloadRelease.albumProjectProgressSummary(sources: List<YouTubeSource>): AlbumProjectProgressSummary {
    val matched = liveMatchedSources(sources)
    if (matched.isEmpty()) return AlbumProjectProgressSummary(matched = 0, saving = 0, saved = 0, failed = 0, progress = 0)
    val saving = matched.count { it.downloadState == YouTubeDownloadState.Downloading }
    val saved = matched.count { it.isOfflineSaved() }
    val failed = matched.count { it.downloadState == YouTubeDownloadState.Failed }
    val progressPoints = matched.sumOf { source ->
        when {
            source.isOfflineSaved() -> 100
            source.downloadState == YouTubeDownloadState.Downloading -> source.downloadProgress.coerceIn(1, 99)
            source.downloadState == YouTubeDownloadState.Failed -> 100
            else -> 0
        }
    }
    return AlbumProjectProgressSummary(
        matched = matched.size,
        saving = saving,
        saved = saved,
        failed = failed,
        progress = (progressPoints / matched.size).coerceIn(0, 100),
    )
}

internal fun AlbumDownloadRelease.albumProjectProgressSummary(sourceLookup: Map<String, YouTubeSource>): AlbumProjectProgressSummary {
    val matched = liveMatchedSources(sourceLookup)
    if (matched.isEmpty()) return AlbumProjectProgressSummary(matched = 0, saving = 0, saved = 0, failed = 0, progress = 0)
    val saving = matched.count { it.downloadState == YouTubeDownloadState.Downloading }
    val saved = matched.count { it.isOfflineSaved() }
    val failed = matched.count { it.downloadState == YouTubeDownloadState.Failed }
    val progressPoints = matched.sumOf { source ->
        when {
            source.isOfflineSaved() -> 100
            source.downloadState == YouTubeDownloadState.Downloading -> source.downloadProgress.coerceIn(1, 99)
            source.downloadState == YouTubeDownloadState.Failed -> 100
            else -> 0
        }
    }
    return AlbumProjectProgressSummary(
        matched = matched.size,
        saving = saving,
        saved = saved,
        failed = failed,
        progress = (progressPoints / matched.size).coerceIn(0, 100),
    )
}

internal fun findActiveAlbumProjectTask(
    drafts: List<AlbumDownloadDraft>,
    tasks: Map<String, AlbumProjectTaskState>,
    sources: List<YouTubeSource>,
): AlbumProjectTaskViewState? {
    val draftsById = drafts.associateBy { it.release.id }
    return tasks.values
        .sortedByDescending { it.startedMillis }
        .mapNotNull { task ->
            val release = draftsById[task.releaseId]?.release ?: return@mapNotNull null
            val summary = release.albumProjectProgressSummary(sources)
            val active = when (task.phase) {
                AlbumProjectTaskPhase.TracklistLoading -> true
                AlbumProjectTaskPhase.Matching -> task.completed < task.total
                AlbumProjectTaskPhase.OfflineSaving -> summary.saving > 0 || summary.saved + summary.failed < summary.matched
            }
            if (active) AlbumProjectTaskViewState(task, release, summary) else null
        }
        .firstOrNull()
}

internal fun AlbumDownloadRelease.matchedPremiumDeckSources(): List<YouTubeSource> =
    tracks
        .sortedBy { it.position }
        .mapNotNull { it.matchedSource }
        .distinctBy { it.streamDistinctKey() }

internal fun AlbumDownloadRelease.albumProjectPlaylistId(): String =
    "album-project-${id.ifBlank { "$artist|$title" }.normalizedSearchText().replace(' ', '-').ifBlank { hashCode().toString() }.take(72)}"
