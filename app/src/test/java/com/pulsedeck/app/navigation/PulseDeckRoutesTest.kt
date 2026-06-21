package com.pulsedeck.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseDeckRoutesTest {
    @Test
    fun primaryRouteAfterClampsWithinMainNavigationPages() {
        assertEquals(Screen.Audio, primaryRouteAfter(Screen.Library, 1))
        assertEquals(Screen.Library, primaryRouteAfter(Screen.Library, -1))
        assertEquals(Screen.Settings, primaryRouteAfter(Screen.Settings, 1))
        assertNull(primaryRouteAfter(Screen.Albums, 1))
    }

    @Test
    fun libraryCategoryNameMapsOnlyLibraryCategoryScreens() {
        assertEquals("All Songs", libraryCategoryNameForScreen(Screen.AllSongs))
        assertEquals("PulseRadio", libraryCategoryNameForScreen(Screen.PulseRadio))
        assertEquals("Folders Hierarchy", libraryCategoryNameForScreen(Screen.FolderHierarchy))
        assertNull(libraryCategoryNameForScreen(Screen.Library))
        assertNull(libraryCategoryNameForScreen(Screen.Settings))
    }

    @Test
    fun libraryNavSelectionIncludesNestedLibraryRoutesOnly() {
        assertTrue(isLibraryNavScreen(Screen.Library))
        assertTrue(isLibraryNavScreen(Screen.AlbumDetail))
        assertTrue(isLibraryNavScreen(Screen.LibraryGroupTracks))
        assertFalse(isLibraryNavScreen(Screen.YouTube))
        assertFalse(isLibraryNavScreen(Screen.Settings))
    }

    @Test
    fun routeStateNavigateAndBackRestoresPreviousSnapshot() {
        val state = PulseDeckRouteState()

        state.selectedFolderPath = "/Music"
        state.navigate(Screen.Folders)
        state.selectedFolderPath = "/Music/Jazz"

        assertEquals(Screen.Folders, state.screen)
        assertEquals(1, state.routeHistory.size)
        assertTrue(state.goBackToPreviousRoute())
        assertEquals(Screen.Library, state.screen)
        assertEquals("/Music", state.selectedFolderPath)
        assertTrue(state.routeHistory.isEmpty())
    }

    @Test
    fun routeStateOpenAlbumDetailPushesCurrentRouteOnce() {
        val state = PulseDeckRouteState()

        assertTrue(state.openAlbumDetail("album:kind-of-blue"))
        assertEquals(Screen.AlbumDetail, state.screen)
        assertEquals("album:kind-of-blue", state.selectedAlbumKey)
        assertEquals(1, state.routeHistory.size)

        assertFalse(state.openAlbumDetail("album:kind-of-blue"))
        assertEquals(1, state.routeHistory.size)
    }

    @Test
    fun routeStateFinishCategoryToLibraryDropsLibraryReturnRoute() {
        val state = PulseDeckRouteState()

        state.navigate(Screen.Albums, suppressMotion = true)
        state.finishCategoryToLibrary()

        assertEquals(Screen.Library, state.screen)
        assertTrue(state.routeHistory.isEmpty())
        assertTrue(state.suppressNextRouteMotion)
    }

    @Test
    fun routeStateTopLevelNavigationReplacesStackAndSuppressesMotion() {
        val state = PulseDeckRouteState()

        state.selectedAlbumKey = "album:private"
        state.selectedFolderPath = "/Music/Jazz"
        state.navigate(Screen.Albums)
        state.navigate(Screen.AlbumDetail)

        assertTrue(state.routeHistory.isNotEmpty())

        assertTrue(state.navigateTopLevel(Screen.Search))

        assertEquals(Screen.Search, state.screen)
        assertTrue(state.routeHistory.isEmpty())
        assertTrue(state.suppressNextRouteMotion)
        assertNull(state.selectedAlbumKey)
        assertEquals("", state.selectedFolderPath)
    }

    @Test
    fun routeStateOpenSettingsWithoutHistoryClearsDockBackStack() {
        val state = PulseDeckRouteState()

        state.navigate(Screen.Audio)
        assertTrue(state.routeHistory.isNotEmpty())

        assertTrue(state.openSettings(pushHistory = false, suppressMotion = true))

        assertEquals(Screen.Settings, state.screen)
        assertTrue(state.routeHistory.isEmpty())
        assertTrue(state.suppressNextRouteMotion)
    }
}
