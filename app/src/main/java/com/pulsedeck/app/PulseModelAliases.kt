package com.pulsedeck.app

import com.pulsedeck.app.model.albumGroupKey as modelAlbumGroupKey
import com.pulsedeck.app.model.formatCompactCount as modelFormatCompactCount
import com.pulsedeck.app.model.formatDuration as modelFormatDuration
import com.pulsedeck.app.model.formatModifiedDate as modelFormatModifiedDate
import com.pulsedeck.app.model.legacyAlbumKey as modelLegacyAlbumKey
import com.pulsedeck.app.model.stableKey as modelStableKey

internal typealias Category = com.pulsedeck.app.model.Category
internal typealias Album = com.pulsedeck.app.model.Album
internal typealias Track = com.pulsedeck.app.model.Track

internal fun Track.stableKey(): String =
    modelStableKey()

internal fun albumGroupKey(title: String): String =
    modelAlbumGroupKey(title)

internal fun legacyAlbumKey(title: String, artist: String): String =
    modelLegacyAlbumKey(title, artist)

internal fun formatDuration(millis: Long): String =
    modelFormatDuration(millis)

internal fun formatCompactCount(value: Long): String =
    modelFormatCompactCount(value)

internal fun formatModifiedDate(millis: Long): String =
    modelFormatModifiedDate(millis)
