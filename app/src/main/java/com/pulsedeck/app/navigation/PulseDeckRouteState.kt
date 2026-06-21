package com.pulsedeck.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.pulsedeck.app.AudioSettingsPage
import com.pulsedeck.app.YouTubeLibraryTab
import com.pulsedeck.app.YouTubeSmartShelf
import com.pulsedeck.app.library.LocalLibraryGroupKind
import com.pulsedeck.app.settings.ui.SettingsLaunchTarget

internal class PulseDeckRouteState(
    screen: Screen = Screen.Library,
    settingsLaunchTarget: SettingsLaunchTarget = SettingsLaunchTarget.Home,
    settingsLaunchRequestKey: Int = 0,
    selectedAlbumKey: String? = null,
    activeAlbumKey: String? = null,
    selectedFolderPath: String = "",
    selectedLibraryGroupKind: LocalLibraryGroupKind? = null,
    selectedLibraryGroupKey: String = "",
    activeYouTubeSourceId: String? = null,
    youtubeTab: YouTubeLibraryTab = YouTubeLibraryTab.Sources,
    youtubeShelf: YouTubeSmartShelf? = null,
    audioSettingsPage: AudioSettingsPage = AudioSettingsPage.Index,
) {
    var screen by mutableStateOf(screen)
    var settingsLaunchTarget by mutableStateOf(settingsLaunchTarget)
    var settingsLaunchRequestKey by mutableIntStateOf(settingsLaunchRequestKey)
    var selectedAlbumKey by mutableStateOf(selectedAlbumKey)
    var activeAlbumKey by mutableStateOf(activeAlbumKey)
    var selectedFolderPath by mutableStateOf(selectedFolderPath)
    var selectedLibraryGroupKind by mutableStateOf(selectedLibraryGroupKind)
    var selectedLibraryGroupKey by mutableStateOf(selectedLibraryGroupKey)
    var activeYouTubeSourceId by mutableStateOf(activeYouTubeSourceId)
    var youtubeTab by mutableStateOf(youtubeTab)
    var youtubeShelf by mutableStateOf(youtubeShelf)
    var audioSettingsPage by mutableStateOf(audioSettingsPage)
    var routeHistory by mutableStateOf<List<RouteSnapshot>>(emptyList())
    var suppressNextRouteMotion by mutableStateOf(false)

    fun currentSnapshot(): RouteSnapshot =
        RouteSnapshot(
            screen = screen,
            selectedAlbumKey = selectedAlbumKey,
            selectedFolderPath = selectedFolderPath,
            audioSettingsPage = audioSettingsPage,
            youtubeTab = youtubeTab,
            youtubeShelf = youtubeShelf,
            activeYouTubeSourceId = activeYouTubeSourceId,
            selectedLibraryGroupKind = selectedLibraryGroupKind,
            selectedLibraryGroupKey = selectedLibraryGroupKey,
        )

    fun pushCurrentRoute() {
        val snapshot = currentSnapshot()
        routeHistory = (if (routeHistory.lastOrNull() == snapshot) routeHistory else routeHistory + snapshot).takeLast(40)
    }

    fun restoreRoute(snapshot: RouteSnapshot, suppressMotion: Boolean = false) {
        suppressNextRouteMotion = suppressMotion
        selectedAlbumKey = snapshot.selectedAlbumKey
        selectedFolderPath = snapshot.selectedFolderPath
        audioSettingsPage = snapshot.audioSettingsPage
        youtubeTab = snapshot.youtubeTab
        youtubeShelf = snapshot.youtubeShelf
        activeYouTubeSourceId = snapshot.activeYouTubeSourceId
        selectedLibraryGroupKind = snapshot.selectedLibraryGroupKind
        selectedLibraryGroupKey = snapshot.selectedLibraryGroupKey
        screen = snapshot.screen
    }

    fun navigate(next: Screen, pushHistory: Boolean = true, suppressMotion: Boolean = false): Boolean {
        if (next == screen) return false
        if (pushHistory) pushCurrentRoute()
        suppressNextRouteMotion = suppressMotion
        screen = next
        return true
    }

    fun navigateTopLevel(next: Screen, suppressMotion: Boolean = true): Boolean {
        val changed = next != screen
        routeHistory = emptyList()
        suppressNextRouteMotion = suppressMotion
        selectedAlbumKey = null
        selectedFolderPath = ""
        selectedLibraryGroupKind = null
        selectedLibraryGroupKey = ""
        screen = next
        return changed
    }

    fun openSettings(
        target: SettingsLaunchTarget = SettingsLaunchTarget.Home,
        pushHistory: Boolean = true,
        suppressMotion: Boolean = false,
    ): Boolean {
        if (!pushHistory) routeHistory = emptyList()
        settingsLaunchTarget = target
        settingsLaunchRequestKey += 1
        return navigate(Screen.Settings, pushHistory = pushHistory, suppressMotion = suppressMotion)
    }

    fun clearLibraryGroupSelection() {
        selectedLibraryGroupKind = null
        selectedLibraryGroupKey = ""
    }

    fun selectLibraryGroup(kind: LocalLibraryGroupKind, groupKey: String) {
        selectedLibraryGroupKind = kind
        selectedLibraryGroupKey = groupKey
        navigate(Screen.LibraryGroupTracks)
    }

    fun finishCategoryToLibrary() {
        if (routeHistory.lastOrNull()?.screen == Screen.Library) {
            routeHistory = routeHistory.dropLast(1)
        }
        suppressNextRouteMotion = true
        screen = Screen.Library
    }

    fun openAlbumDetail(albumKey: String): Boolean {
        if (screen == Screen.AlbumDetail && selectedAlbumKey == albumKey) return false
        pushCurrentRoute()
        suppressNextRouteMotion = false
        selectedAlbumKey = albumKey
        screen = Screen.AlbumDetail
        return true
    }

    fun finishAlbumDetailToGrid() {
        val previous = routeHistory.lastOrNull()
        suppressNextRouteMotion = true
        if (previous?.screen == Screen.Albums) {
            routeHistory = routeHistory.dropLast(1)
            restoreRoute(previous, suppressMotion = true)
            selectedAlbumKey = null
        } else {
            screen = Screen.Albums
            selectedAlbumKey = null
        }
    }

    fun goBackToPreviousRoute(suppressMotion: Boolean = false): Boolean {
        val previous = routeHistory.lastOrNull() ?: return false
        routeHistory = routeHistory.dropLast(1)
        restoreRoute(previous, suppressMotion = suppressMotion)
        return true
    }

    fun primaryRouteAfter(step: Int): Screen? =
        com.pulsedeck.app.navigation.primaryRouteAfter(screen, step)

    companion object {
        val Saver: Saver<PulseDeckRouteState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.screen.name,
                    state.settingsLaunchTarget.name,
                    state.settingsLaunchRequestKey,
                    state.selectedAlbumKey.orEmpty(),
                    state.activeAlbumKey.orEmpty(),
                    state.selectedFolderPath,
                    state.selectedLibraryGroupKind?.name.orEmpty(),
                    state.selectedLibraryGroupKey,
                    state.activeYouTubeSourceId.orEmpty(),
                    state.youtubeTab.name,
                    state.youtubeShelf?.name.orEmpty(),
                    state.audioSettingsPage.name,
                )
            },
            restore = { values ->
                PulseDeckRouteState(
                    screen = enumValueOrDefault(values.getOrNull(0), Screen.Library),
                    settingsLaunchTarget = enumValueOrDefault(values.getOrNull(1), SettingsLaunchTarget.Home),
                    settingsLaunchRequestKey = values.getOrNull(2) as? Int ?: 0,
                    selectedAlbumKey = nullableSavedString(values.getOrNull(3)),
                    activeAlbumKey = nullableSavedString(values.getOrNull(4)),
                    selectedFolderPath = values.getOrNull(5) as? String ?: "",
                    selectedLibraryGroupKind = enumValueOrNull<LocalLibraryGroupKind>(values.getOrNull(6)),
                    selectedLibraryGroupKey = values.getOrNull(7) as? String ?: "",
                    activeYouTubeSourceId = nullableSavedString(values.getOrNull(8)),
                    youtubeTab = enumValueOrDefault(values.getOrNull(9), YouTubeLibraryTab.Sources),
                    youtubeShelf = enumValueOrNull<YouTubeSmartShelf>(values.getOrNull(10)),
                    audioSettingsPage = enumValueOrDefault(values.getOrNull(11), AudioSettingsPage.Index),
                )
            },
        )
    }
}

private fun nullableSavedString(value: Any?): String? =
    (value as? String)?.takeIf { it.isNotEmpty() }

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: Any?, default: T): T {
    val name = value as? String ?: return default
    return runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: Any?): T? {
    val name = (value as? String)?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { enumValueOf<T>(name) }.getOrNull()
}
