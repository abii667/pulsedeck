package com.pulsedeck.app.player

internal enum class ShuffleMode {
    Off,
    ShuffleAll,
    ShuffleSongsInCategory,
    ShuffleCategories,
}

internal enum class PlaybackRepeatMode {
    SongOnce,
    CategoryOnce,
    AllOnce,
    RepeatSong,
    RepeatCategory,
    RepeatAll,
}

internal fun nextShuffleMode(current: ShuffleMode): ShuffleMode =
    when (current) {
        ShuffleMode.Off -> ShuffleMode.ShuffleAll
        ShuffleMode.ShuffleAll -> ShuffleMode.ShuffleSongsInCategory
        ShuffleMode.ShuffleSongsInCategory -> ShuffleMode.ShuffleCategories
        ShuffleMode.ShuffleCategories -> ShuffleMode.Off
    }

internal fun nextRepeatMode(current: PlaybackRepeatMode): PlaybackRepeatMode =
    when (current) {
        PlaybackRepeatMode.SongOnce -> PlaybackRepeatMode.CategoryOnce
        PlaybackRepeatMode.CategoryOnce -> PlaybackRepeatMode.AllOnce
        PlaybackRepeatMode.AllOnce -> PlaybackRepeatMode.RepeatSong
        PlaybackRepeatMode.RepeatSong -> PlaybackRepeatMode.RepeatCategory
        PlaybackRepeatMode.RepeatCategory -> PlaybackRepeatMode.RepeatAll
        PlaybackRepeatMode.RepeatAll -> PlaybackRepeatMode.SongOnce
    }

internal val ShuffleMode.title: String
    get() = when (this) {
        ShuffleMode.Off -> "Off"
        ShuffleMode.ShuffleAll -> "Shuffle All"
        ShuffleMode.ShuffleSongsInCategory -> "Shuffle Songs"
        ShuffleMode.ShuffleCategories -> "Shuffle Categories"
    }

internal val ShuffleMode.subtitle: String
    get() = when (this) {
        ShuffleMode.Off -> "Use normal order"
        ShuffleMode.ShuffleAll -> "Randomize every track"
        ShuffleMode.ShuffleSongsInCategory -> "Keep albums, mix songs"
        ShuffleMode.ShuffleCategories -> "Mix albums, keep song order"
    }

internal val ShuffleMode.badge: String?
    get() = when (this) {
        ShuffleMode.Off -> null
        ShuffleMode.ShuffleAll -> "ALL"
        ShuffleMode.ShuffleSongsInCategory -> "SONGS"
        ShuffleMode.ShuffleCategories -> "CATS"
    }

internal val PlaybackRepeatMode.title: String
    get() = when (this) {
        PlaybackRepeatMode.SongOnce -> "Song Once"
        PlaybackRepeatMode.CategoryOnce -> "Category Once"
        PlaybackRepeatMode.AllOnce -> "All Once"
        PlaybackRepeatMode.RepeatSong -> "Repeat Song"
        PlaybackRepeatMode.RepeatCategory -> "Repeat Category"
        PlaybackRepeatMode.RepeatAll -> "Repeat All"
    }

internal val PlaybackRepeatMode.subtitle: String
    get() = when (this) {
        PlaybackRepeatMode.SongOnce -> "Stop after this song"
        PlaybackRepeatMode.CategoryOnce -> "Stop after this album"
        PlaybackRepeatMode.AllOnce -> "Stop after the library"
        PlaybackRepeatMode.RepeatSong -> "Loop this song"
        PlaybackRepeatMode.RepeatCategory -> "Loop this album"
        PlaybackRepeatMode.RepeatAll -> "Loop the library"
    }

internal val PlaybackRepeatMode.badge: String
    get() = when (this) {
        PlaybackRepeatMode.SongOnce -> "1x"
        PlaybackRepeatMode.CategoryOnce -> "CAT"
        PlaybackRepeatMode.AllOnce -> "ALL"
        PlaybackRepeatMode.RepeatSong -> "1"
        PlaybackRepeatMode.RepeatCategory -> "CAT\u221E"
        PlaybackRepeatMode.RepeatAll -> "ALL\u221E"
    }

internal val PlaybackRepeatMode.isLooping: Boolean
    get() = this == PlaybackRepeatMode.RepeatSong ||
        this == PlaybackRepeatMode.RepeatCategory ||
        this == PlaybackRepeatMode.RepeatAll
