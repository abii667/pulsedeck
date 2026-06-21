package com.pulsedeck.app.player

import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.Album
import com.pulsedeck.app.Track
import com.pulsedeck.app.stableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSessionPlannerTest {
    private val alpha = testAlbum("Alpha")
    private val beta = testAlbum("Beta")
    private val alphaOne = testTrack("Alpha One", alpha)
    private val alphaTwo = testTrack("Alpha Two", alpha)
    private val betaOne = testTrack("Beta One", beta)
    private val tracks = listOf(alphaOne, alphaTwo, betaOne)
    private val albums = listOf(alpha, beta)

    @Test
    fun allOnceStopsAtEndForAutoAdvanceButManualNextWraps() {
        val session = session(current = betaOne, repeatMode = PlaybackRepeatMode.AllOnce)

        assertNull(resolveNextLocalTrack(session, autoEnded = true))
        assertEquals(alphaOne, resolveNextLocalTrack(session, autoEnded = false))
    }

    @Test
    fun repeatSongAndRepeatCategoryStayInsideExpectedScope() {
        val repeatSong = session(current = alphaTwo, repeatMode = PlaybackRepeatMode.RepeatSong)
        val categoryOnce = session(current = alphaTwo, repeatMode = PlaybackRepeatMode.CategoryOnce)
        val repeatCategory = session(current = alphaTwo, repeatMode = PlaybackRepeatMode.RepeatCategory)

        assertEquals(alphaTwo, resolveNextLocalTrack(repeatSong, autoEnded = true))
        assertNull(resolveNextLocalTrack(categoryOnce, autoEnded = true))
        assertEquals(alphaOne, resolveNextLocalTrack(repeatCategory, autoEnded = true))
    }

    @Test
    fun previousTrackConsumesShuffleHistoryBeforeOrderedFallback() {
        val session = session(
            current = betaOne,
            shuffleMode = ShuffleMode.ShuffleAll,
            playbackHistory = listOf(alphaOne.stableKey(), alphaTwo.stableKey()),
        )

        val previous = resolvePreviousLocalTrack(session)

        assertEquals(alphaTwo, previous.track)
        assertEquals(listOf(alphaOne.stableKey()), previous.remainingHistory)
    }

    @Test
    fun localTrackLookupClampsIndexAndFindsStableTrackKey() {
        assertEquals(alphaOne, localTrackAt(tracks, -99))
        assertEquals(betaOne, localTrackAt(tracks, 99))
        assertEquals(1, localTrackIndexFor(tracks, alphaTwo.copy(title = "Alpha Two")))
    }

    @Test
    fun upcomingLocalTracksExposeOrderedQueueAfterCurrentTrack() {
        val session = session(current = alphaOne)

        val upcoming = resolveUpcomingLocalTracks(session)

        assertEquals(listOf(alphaTwo, betaOne), upcoming)
    }

    @Test
    fun localAlbumContextKeepsManualNextInsideAlbumWithAllOnceRepeat() {
        val session = session(
            current = alphaOne,
            repeatMode = PlaybackRepeatMode.AllOnce,
            queueContext = PlaybackQueueContext.LocalAlbum(alpha.key, alpha.title, alpha.artist),
        )

        assertEquals(alphaTwo, resolveNextLocalTrack(session, autoEnded = false))
        assertEquals(listOf(alphaTwo), resolveUpcomingLocalTracks(session))
        assertEquals(listOf(alphaOne, alphaTwo), localPlaybackQueueTracks(session))
    }

    @Test
    fun localAlbumContextStopsAutoAdvanceAtAlbumEndWithAllOnceRepeat() {
        val session = session(
            current = alphaTwo,
            repeatMode = PlaybackRepeatMode.AllOnce,
            queueContext = PlaybackQueueContext.LocalAlbum(alpha.key, alpha.title, alpha.artist),
        )

        assertNull(resolveNextLocalTrack(session, autoEnded = true))
        assertEquals(alphaOne, resolveNextLocalTrack(session, autoEnded = false))
    }

    @Test
    fun playbackStateOpenCloseAndSeekActionsAreDeterministic() {
        val state = PulseDeckPlaybackState()

        state.openPlayer(fromMini = true)
        assertEquals(true, state.playerOpen)
        assertEquals(true, state.playerOpenFromMini)
        assertEquals(1, state.playerOpenKey)

        state.seekToProgress(durationMillis = 120_000L, value = 0.5f)
        assertEquals(60_000L, state.positionMillis)
        assertEquals(60_000L, state.seekRequestMillis)
        assertEquals(1, state.seekRequestKey)

        state.seekBy(durationMillis = 120_000L, deltaMillis = 90_000L)
        assertEquals(120_000L, state.positionMillis)
        assertEquals(2, state.seekRequestKey)

        state.closePlayer()
        assertEquals(false, state.playerOpen)
        assertEquals(false, state.playerOpenFromMini)
    }

    @Test
    fun playbackStateSleepTimerSnapsToFiveMinuteBuckets() {
        val state = PulseDeckPlaybackState()

        state.setSleepTimer(minutes = 7, fadeOutEnabled = false, nowMillis = 1_000L)

        assertEquals(5, state.sleepTimerMinutes)
        assertEquals(301_000L, state.sleepTimerDeadlineMillis)
        assertEquals(false, state.sleepTimerFadeOutEnabled)
        assertEquals("5m", state.sleepTimerLabel)
    }

    @Test
    fun playbackStateHistoryIsBounded() {
        val state = PulseDeckPlaybackState()

        repeat(90) { state.rememberPlaybackHistory("track-$it") }

        assertEquals(80, state.playbackHistory.size)
        assertEquals("track-10", state.playbackHistory.first())
        assertEquals("track-89", state.playbackHistory.last())
    }

    @Test
    fun playbackStateManualQueueIsDeduplicatedAndBounded() {
        val state = PulseDeckPlaybackState()

        state.replaceManualQueue((0 until 90).map { "track-$it" } + "track-5")

        assertEquals(80, state.manualQueueTrackKeys.size)
        assertEquals("track-0", state.manualQueueTrackKeys.first())
        assertEquals("track-79", state.manualQueueTrackKeys.last())

        state.clearManualQueue()

        assertEquals(emptyList<String>(), state.manualQueueTrackKeys)
    }

    private fun session(
        current: Track,
        shuffleMode: ShuffleMode = ShuffleMode.Off,
        repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.AllOnce,
        playbackHistory: List<String> = emptyList(),
        queueContext: PlaybackQueueContext = PlaybackQueueContext.AllSongs,
    ): LocalPlaybackSession =
        LocalPlaybackSession(
            tracks = tracks,
            albums = albums,
            activeAlbumKey = null,
            currentTrack = current,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            playbackHistory = playbackHistory,
            queueContext = queueContext,
        )

    private fun testAlbum(title: String): Album =
        Album(
            title = title,
            artist = "Unit Test",
            mark = title.take(2).uppercase(),
            tint = Color(0xFF335BFF),
            alt = Color(0xFF80F1C6),
        )

    private fun testTrack(title: String, album: Album): Track =
        Track(
            title = title,
            artist = "Artist",
            duration = "1:00",
            album = album,
            durationMillis = 60_000L,
        )
}
