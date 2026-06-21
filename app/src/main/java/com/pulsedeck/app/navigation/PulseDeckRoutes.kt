package com.pulsedeck.app.navigation

import com.pulsedeck.app.AudioSettingsPage
import com.pulsedeck.app.YouTubeLibraryTab
import com.pulsedeck.app.YouTubeSmartShelf
import com.pulsedeck.app.library.LocalLibraryGroupKind

internal enum class Screen {
    Library,
    AllSongs,
    Folders,
    Albums,
    AlbumDownloader,
    AlbumDetail,
    Artists,
    AlbumArtists,
    Genres,
    Years,
    Composers,
    LocalPlaylists,
    Bookmarks,
    MostPlayed,
    LibraryGroupTracks,
    FolderHierarchy,
    Audio,
    Search,
    PulseRadio,
    YouTube,
    YouTubeStream,
    Settings,
}

internal data class RouteSnapshot(
    val screen: Screen,
    val selectedAlbumKey: String?,
    val selectedFolderPath: String,
    val audioSettingsPage: AudioSettingsPage,
    val youtubeTab: YouTubeLibraryTab,
    val youtubeShelf: YouTubeSmartShelf?,
    val activeYouTubeSourceId: String?,
    val selectedLibraryGroupKind: LocalLibraryGroupKind?,
    val selectedLibraryGroupKey: String,
)

internal val primaryRouteScreens: List<Screen> =
    listOf(Screen.Library, Screen.Audio, Screen.Search, Screen.Settings)

internal fun primaryRouteAfter(current: Screen, step: Int): Screen? {
    val index = primaryRouteScreens.indexOf(current)
    if (index < 0) return null
    return primaryRouteScreens[(index + step).coerceIn(0, primaryRouteScreens.lastIndex)]
}

internal fun libraryCategoryNameForScreen(route: Screen): String? =
    when (route) {
        Screen.AllSongs -> "All Songs"
        Screen.Folders -> "Folders"
        Screen.Albums -> "Albums"
        Screen.Artists -> "Artists"
        Screen.AlbumArtists -> "Album Artists"
        Screen.Genres -> "Genres"
        Screen.Years -> "Years"
        Screen.Composers -> "Composers"
        Screen.LocalPlaylists -> "Playlists"
        Screen.Bookmarks -> "Bookmarks"
        Screen.MostPlayed -> "Most Played"
        Screen.FolderHierarchy -> "Folders Hierarchy"
        Screen.PulseRadio -> "PulseRadio"
        Screen.YouTube -> "PremiumDeck"
        else -> null
    }

internal fun isLibraryNavScreen(screen: Screen): Boolean =
    screen in setOf(
        Screen.Library,
        Screen.AllSongs,
        Screen.Folders,
        Screen.Albums,
        Screen.AlbumDownloader,
        Screen.AlbumDetail,
        Screen.Artists,
        Screen.AlbumArtists,
        Screen.Genres,
        Screen.Years,
        Screen.Composers,
        Screen.LocalPlaylists,
        Screen.Bookmarks,
        Screen.MostPlayed,
        Screen.LibraryGroupTracks,
        Screen.FolderHierarchy,
    )
