package com.pulsedeck.app.player

internal data class LastPlaybackState(
    val kind: String,
    val trackKey: String = "",
    val youtubeSourceId: String = "",
    val positionMillis: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val albumKey: String = "",
    val displayName: String = "",
    val durationMillis: Long = 0L,
    val sizeBytes: Long = 0L,
    val modifiedMillis: Long = 0L,
    val savedMillis: Long = 0L,
)
