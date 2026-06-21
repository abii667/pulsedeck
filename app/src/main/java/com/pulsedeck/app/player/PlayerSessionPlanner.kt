package com.pulsedeck.app.player

import com.pulsedeck.app.Album
import com.pulsedeck.app.Track
import com.pulsedeck.app.stableKey

internal sealed interface PlaybackQueueContext {
    data class LocalAlbum(
        val albumId: String,
        val albumTitle: String? = null,
        val artist: String? = null,
    ) : PlaybackQueueContext

    data class LocalArtist(val artistId: String?) : PlaybackQueueContext
    data class LocalFolder(val folderKey: String?) : PlaybackQueueContext
    data class LocalSearch(val queryHash: String?) : PlaybackQueueContext
    data object AllSongs : PlaybackQueueContext
    data object UserQueue : PlaybackQueueContext
    data class PremiumDeck(val sourceId: String?, val queryId: String?) : PlaybackQueueContext
    data class Radio(val stationId: String?) : PlaybackQueueContext
}

internal data class LocalPlaybackSession(
    val tracks: List<Track>,
    val albums: List<Album>,
    val activeAlbumKey: String?,
    val currentTrack: Track?,
    val shuffleMode: ShuffleMode,
    val repeatMode: PlaybackRepeatMode,
    val playbackHistory: List<String>,
    val queueContext: PlaybackQueueContext = PlaybackQueueContext.AllSongs,
)

internal data class PreviousLocalTrackSelection(
    val track: Track?,
    val remainingHistory: List<String>? = null,
)

internal fun localTrackAt(tracks: List<Track>, trackIndex: Int): Track? =
    tracks.getOrNull(trackIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)))

internal fun localTrackIndexFor(tracks: List<Track>, target: Track): Int =
    tracks.indexOfFirst { it.stableKey() == target.stableKey() }.coerceAtLeast(0)

internal fun localCurrentCategoryKey(session: LocalPlaybackSession): String =
    (session.queueContext as? PlaybackQueueContext.LocalAlbum)?.albumId
        ?: session.activeAlbumKey
        ?: session.currentTrack?.album?.key.orEmpty()

internal fun localCategoryTracks(session: LocalPlaybackSession, key: String = localCurrentCategoryKey(session)): List<Track> =
    if (key.isBlank()) emptyList() else session.tracks.filter { it.album.key == key }

internal fun localTracksInAlbumOrder(tracks: List<Track>, albums: List<Album>): List<Track> =
    albums.flatMap { album -> tracks.filter { it.album.key == album.key } }

internal fun localOrderedCategoryKeys(tracks: List<Track>, albums: List<Album>): List<String> =
    albums.map { it.key }.filter { key -> tracks.any { it.album.key == key } }

internal fun resolveNextLocalTrack(session: LocalPlaybackSession, autoEnded: Boolean): Track? {
    val categoryMode = session.repeatMode == PlaybackRepeatMode.CategoryOnce ||
        session.repeatMode == PlaybackRepeatMode.RepeatCategory
    val repeatWrap = session.repeatMode == PlaybackRepeatMode.RepeatCategory ||
        session.repeatMode == PlaybackRepeatMode.RepeatAll
    val allScope = !categoryMode && !session.queueContextRestrictsToCategory()
    val source = nextSource(session)
    if (autoEnded) {
        when (session.repeatMode) {
            PlaybackRepeatMode.SongOnce -> return null
            PlaybackRepeatMode.RepeatSong -> return session.currentTrack
            else -> Unit
        }
    }
    return when (session.shuffleMode) {
        ShuffleMode.Off -> nextInOrdered(source, session.currentTrack, wrap = repeatWrap || !autoEnded)
        ShuffleMode.ShuffleAll -> randomTrackFrom(source, session.currentTrack, session.playbackHistory, wrap = repeatWrap || !autoEnded)
        ShuffleMode.ShuffleSongsInCategory -> nextShuffleSongsInCategory(session, scopeAllCategories = allScope, wrap = repeatWrap || !autoEnded)
        ShuffleMode.ShuffleCategories -> nextShuffleCategories(session, scopeAllCategories = allScope, wrap = repeatWrap || !autoEnded)
    }
}

internal fun localPlaybackQueueTracks(session: LocalPlaybackSession): List<Track> =
    when (session.repeatMode) {
        PlaybackRepeatMode.SongOnce,
        PlaybackRepeatMode.RepeatSong -> listOfNotNull(session.currentTrack)
        else -> nextSource(session)
    }

internal fun resolveUpcomingLocalTracks(session: LocalPlaybackSession, limit: Int = 24): List<Track> {
    if (limit <= 0) return emptyList()
    val source = nextSource(session)
    if (source.isEmpty()) return emptyList()
    val currentKey = session.currentTrack?.stableKey()
    val historyKeys = session.playbackHistory.toSet()
    val ordered = when (session.shuffleMode) {
        ShuffleMode.Off -> orderedTracksAfterCurrent(source, currentKey)
        ShuffleMode.ShuffleAll,
        ShuffleMode.ShuffleSongsInCategory,
        ShuffleMode.ShuffleCategories -> {
            val fresh = source.filter { it.stableKey() != currentKey && it.stableKey() !in historyKeys }
            fresh.ifEmpty { source.filter { it.stableKey() != currentKey } }
        }
    }
    return ordered
        .distinctBy { it.stableKey() }
        .take(limit)
}

internal fun resolvePreviousLocalTrack(session: LocalPlaybackSession): PreviousLocalTrackSelection {
    if (session.shuffleMode != ShuffleMode.Off && session.playbackHistory.isNotEmpty()) {
        val previousKey = session.playbackHistory.last()
        val remainingHistory = session.playbackHistory.dropLast(1)
        val target = session.tracks.firstOrNull { it.stableKey() == previousKey }
        if (target != null) return PreviousLocalTrackSelection(target, remainingHistory)
        return PreviousLocalTrackSelection(
            track = previousInOrdered(previousSource(session), session.currentTrack),
            remainingHistory = remainingHistory,
        )
    }
    return PreviousLocalTrackSelection(previousInOrdered(previousSource(session), session.currentTrack))
}

private fun previousSource(session: LocalPlaybackSession): List<Track> =
    explicitQueueContextTracks(session) ?: if (
        session.activeAlbumKey != null ||
        session.repeatMode == PlaybackRepeatMode.CategoryOnce ||
        session.repeatMode == PlaybackRepeatMode.RepeatCategory
    ) {
        localCategoryTracks(session)
    } else {
        localTracksInAlbumOrder(session.tracks, session.albums)
    }

private fun nextSource(session: LocalPlaybackSession): List<Track> {
    explicitQueueContextTracks(session)?.let { return it }
    val categoryMode = session.repeatMode == PlaybackRepeatMode.CategoryOnce ||
        session.repeatMode == PlaybackRepeatMode.RepeatCategory
    return if (
        categoryMode ||
        session.activeAlbumKey != null &&
            session.repeatMode != PlaybackRepeatMode.AllOnce &&
            session.repeatMode != PlaybackRepeatMode.RepeatAll
    ) {
        localCategoryTracks(session)
    } else {
        localTracksInAlbumOrder(session.tracks, session.albums)
    }
}

private fun explicitQueueContextTracks(session: LocalPlaybackSession): List<Track>? =
    when (val context = session.queueContext) {
        is PlaybackQueueContext.LocalAlbum -> localCategoryTracks(session, context.albumId)
            .takeIf { it.isNotEmpty() }
        else -> null
    }

private fun LocalPlaybackSession.queueContextRestrictsToCategory(): Boolean =
    queueContext is PlaybackQueueContext.LocalAlbum

private fun orderedTracksAfterCurrent(source: List<Track>, currentKey: String?): List<Track> {
    if (source.isEmpty()) return emptyList()
    val index = source.indexOfFirst { it.stableKey() == currentKey }.coerceAtLeast(0)
    return source.drop(index + 1) + source.take(index)
}

private fun nextInOrdered(source: List<Track>, currentTrack: Track?, wrap: Boolean): Track? {
    if (source.isEmpty()) return null
    val currentKey = currentTrack?.stableKey()
    val index = source.indexOfFirst { it.stableKey() == currentKey }.coerceAtLeast(0)
    return when {
        index < source.lastIndex -> source[index + 1]
        wrap -> source.first()
        else -> null
    }
}

private fun previousInOrdered(source: List<Track>, currentTrack: Track?): Track? {
    if (source.isEmpty()) return null
    val currentKey = currentTrack?.stableKey()
    val index = source.indexOfFirst { it.stableKey() == currentKey }.coerceAtLeast(0)
    return source[Math.floorMod(index - 1, source.size)]
}

private fun randomTrackFrom(
    source: List<Track>,
    currentTrack: Track?,
    playbackHistory: List<String>,
    wrap: Boolean,
): Track? {
    if (source.size <= 1) return if (wrap) source.firstOrNull() else null
    val currentKey = currentTrack?.stableKey()
    val usedKeys = (playbackHistory + listOfNotNull(currentKey)).toSet()
    val candidates = source.filter { it.stableKey() != currentKey && (wrap || it.stableKey() !in usedKeys) }
    val pool = candidates.ifEmpty { if (wrap) source.filter { it.stableKey() != currentKey } else emptyList() }
    return pool.randomOrNull()
}

private fun nextCategoryKeyOrdered(session: LocalPlaybackSession, wrap: Boolean): String? {
    val keys = localOrderedCategoryKeys(session.tracks, session.albums)
    if (keys.isEmpty()) return null
    val currentKey = localCurrentCategoryKey(session)
    if (currentKey.isBlank()) return keys.first()
    val index = keys.indexOf(currentKey).coerceAtLeast(0)
    return when {
        index < keys.lastIndex -> keys[index + 1]
        wrap -> keys.first()
        else -> null
    }
}

private fun randomCategoryKey(session: LocalPlaybackSession, wrap: Boolean): String? {
    val keys = localOrderedCategoryKeys(session.tracks, session.albums)
    if (keys.isEmpty()) return null
    val currentKey = localCurrentCategoryKey(session)
    val visitedKeys = (
        session.playbackHistory.mapNotNull { key -> session.tracks.firstOrNull { it.stableKey() == key }?.album?.key } +
            listOfNotNull(currentKey.takeIf { it.isNotBlank() })
        ).toSet()
    val candidates = keys.filter { it != currentKey && (wrap || it !in visitedKeys) }
    return candidates.ifEmpty { if (wrap) keys.filter { it != currentKey } else emptyList() }.randomOrNull()
}

private fun nextShuffleSongsInCategory(
    session: LocalPlaybackSession,
    scopeAllCategories: Boolean,
    wrap: Boolean,
): Track? {
    val currentCategoryTracks = localCategoryTracks(session)
    val randomInCategory = randomTrackFrom(currentCategoryTracks, session.currentTrack, session.playbackHistory, wrap = false)
    if (randomInCategory != null) return randomInCategory
    if (!scopeAllCategories) return if (wrap) {
        randomTrackFrom(currentCategoryTracks, session.currentTrack, session.playbackHistory, wrap = true) ?: currentCategoryTracks.firstOrNull()
    } else {
        null
    }
    val nextKey = nextCategoryKeyOrdered(session, wrap) ?: return null
    val nextCategoryTracks = localCategoryTracks(session, nextKey)
    return randomTrackFrom(nextCategoryTracks, session.currentTrack, session.playbackHistory, wrap = true) ?: nextCategoryTracks.firstOrNull()
}

private fun nextShuffleCategories(
    session: LocalPlaybackSession,
    scopeAllCategories: Boolean,
    wrap: Boolean,
): Track? {
    val nextInCategory = nextInOrdered(localCategoryTracks(session), session.currentTrack, wrap = false)
    if (nextInCategory != null) return nextInCategory
    if (!scopeAllCategories) return if (wrap) localCategoryTracks(session).firstOrNull() else null
    val nextKey = randomCategoryKey(session, wrap) ?: return null
    return localCategoryTracks(session, nextKey).firstOrNull()
}
