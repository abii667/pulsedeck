package com.pulsedeck.app.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pulsedeck.app.Track

internal class PulseDeckLibraryState(
    localLibraryLoaded: Boolean,
    scannedTracks: List<Track> = emptyList(),
    likedTrackKeys: Set<String> = emptySet(),
    dislikedTrackKeys: Set<String> = emptySet(),
    bookmarkedTrackKeys: Set<String> = emptySet(),
    playlistTrackKeys: Set<String> = emptySet(),
    trackPlayCounts: Map<String, Int> = emptyMap(),
) {
    var localLibraryLoaded by mutableStateOf(localLibraryLoaded)
    var scannedTracks by mutableStateOf(scannedTracks)
    var likedTrackKeys by mutableStateOf(likedTrackKeys)
    var dislikedTrackKeys by mutableStateOf(dislikedTrackKeys)
    var bookmarkedTrackKeys by mutableStateOf(bookmarkedTrackKeys)
    var playlistTrackKeys by mutableStateOf(playlistTrackKeys)
    var trackPlayCounts by mutableStateOf(trackPlayCounts)

    fun applyTrackStateCleanup(cleanup: LocalTrackStateCleanup) {
        likedTrackKeys = cleanup.likedKeys
        dislikedTrackKeys = cleanup.dislikedKeys
        bookmarkedTrackKeys = cleanup.bookmarkedKeys
        playlistTrackKeys = cleanup.playlistKeys
        trackPlayCounts = cleanup.playCounts
    }
}
