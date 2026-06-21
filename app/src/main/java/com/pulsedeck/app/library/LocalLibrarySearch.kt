package com.pulsedeck.app.library

import com.pulsedeck.app.Track
import com.pulsedeck.app.normalizedSearchText
import java.text.Normalizer

internal class LocalLibrarySearchIndex private constructor(
    private val entries: List<Entry>,
) {
    val trackCount: Int get() = entries.size

    fun search(query: String, limit: Int = SEARCH_RESULT_LIMIT, emptyLimit: Int = EMPTY_SEARCH_LIMIT): List<Track> {
        val normalizedQuery = query.localSearchNormalize()
        if (normalizedQuery.isBlank()) return entries.take(emptyLimit).map { it.track }
        val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return entries.take(emptyLimit).map { it.track }
        val results = ArrayList<Track>(limit.coerceAtLeast(0))
        for (entry in entries) {
            if (tokens.all { token -> entry.searchText.contains(token) }) {
                results += entry.track
                if (results.size >= limit) break
            }
        }
        return results
    }

    private data class Entry(
        val track: Track,
        val searchText: String,
    )

    companion object {
        const val SEARCH_RESULT_LIMIT = 60
        const val EMPTY_SEARCH_LIMIT = 30

        val Empty = LocalLibrarySearchIndex(emptyList())

        fun build(tracks: List<Track>): LocalLibrarySearchIndex =
            LocalLibrarySearchIndex(
                tracks.map { track ->
                    Entry(
                        track = track,
                        searchText = buildString {
                            appendSearchPart(track.title)
                            appendSearchPart(track.artist)
                            appendSearchPart(track.album.title)
                            appendSearchPart(track.album.artist)
                            appendSearchPart(track.albumArtist)
                            appendSearchPart(track.genre)
                            appendSearchPart(track.composer)
                            appendSearchPart(track.year?.toString())
                            appendSearchPart(track.folderPath)
                            appendSearchPart(normalizedFolderPath(track))
                            appendSearchPart(track.displayName)
                        }.localSearchNormalize(),
                    )
                },
            )
    }
}

private fun StringBuilder.appendSearchPart(value: String?) {
    if (!value.isNullOrBlank()) {
        if (isNotEmpty()) append(' ')
        append(value)
    }
}

private fun String.localSearchNormalize(): String {
    val folded = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return folded.normalizedSearchText()
}
