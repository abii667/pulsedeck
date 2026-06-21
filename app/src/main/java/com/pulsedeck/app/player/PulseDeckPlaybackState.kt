package com.pulsedeck.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.pulsedeck.app.Track
import kotlin.math.roundToInt

internal class PulseDeckPlaybackState(
    playerOpen: Boolean = false,
    playerOpenFromMini: Boolean = false,
    playerOpenKey: Int = 0,
    playerAutoOpened: Boolean = false,
    playerCloseRequestKey: Int = 0,
    miniDismissed: Boolean = false,
    playing: Boolean = false,
    trackIndex: Int = 0,
    trackSlideDirection: Int = 1,
    shuffleMode: ShuffleMode = ShuffleMode.Off,
    repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.AllOnce,
    manualQueueTrackKeys: List<String> = emptyList(),
    sleepTimerMinutes: Int = 0,
    sleepTimerDeadlineMillis: Long = 0L,
    sleepTimerFadeOutEnabled: Boolean = true,
    sleepTimerDialogOpen: Boolean = false,
) {
    var playerOpen by mutableStateOf(playerOpen)
    var playerOpenFromMini by mutableStateOf(playerOpenFromMini)
    var playerOpenKey by mutableIntStateOf(playerOpenKey)
    var playerAutoOpened by mutableStateOf(playerAutoOpened)
    var playerCloseRequestKey by mutableIntStateOf(playerCloseRequestKey)
    var miniDismissed by mutableStateOf(miniDismissed)
    var playing by mutableStateOf(playing)
    var trackIndex by mutableIntStateOf(trackIndex)
    var trackSlideDirection by mutableIntStateOf(trackSlideDirection)
    var shuffleMode by mutableStateOf(shuffleMode)
    var repeatMode by mutableStateOf(repeatMode)
    var playbackHistory by mutableStateOf<List<String>>(emptyList())
    var manualQueueTrackKeys by mutableStateOf(manualQueueTrackKeys)
    var sleepTimerMinutes by mutableIntStateOf(sleepTimerMinutes)
    var sleepTimerDeadlineMillis by mutableLongStateOf(sleepTimerDeadlineMillis)
    var sleepTimerFadeOutEnabled by mutableStateOf(sleepTimerFadeOutEnabled)
    var sleepTimerDialogOpen by mutableStateOf(sleepTimerDialogOpen)
    var activeStreamTrack by mutableStateOf<Track?>(null)
    var positionMillis by mutableLongStateOf(0L)
    var seekRequestKey by mutableIntStateOf(0)
    var seekRequestMillis by mutableLongStateOf(0L)

    val sleepTimerLabel: String?
        get() = if (sleepTimerMinutes == 0) null else "${sleepTimerMinutes}m"

    fun progress(durationMillis: Long): Float =
        if (durationMillis > 0L) {
            (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            0.33f
        }

    fun resetDisplayedPlaybackPosition(requestSeek: Boolean = false) {
        positionMillis = 0L
        seekRequestMillis = 0L
        if (requestSeek) seekRequestKey += 1
    }

    fun clearPlaybackHistory() {
        playbackHistory = emptyList()
    }

    fun rememberPlaybackHistory(trackKey: String) {
        playbackHistory = (playbackHistory + trackKey).takeLast(80)
    }

    fun replacePlaybackHistory(history: List<String>) {
        playbackHistory = history
    }

    fun replaceManualQueue(trackKeys: List<String>) {
        manualQueueTrackKeys = trackKeys.distinct().take(80)
    }

    fun clearManualQueue() {
        manualQueueTrackKeys = emptyList()
    }

    fun selectTrackIndex(index: Int, direction: Int) {
        trackSlideDirection = if (direction >= 0) 1 else -1
        trackIndex = index.coerceAtLeast(0)
    }

    fun openPlayer(fromMini: Boolean) {
        playerOpenFromMini = fromMini
        playerOpenKey += 1
        playerOpen = true
    }

    fun requestClosePlayer() {
        if (playerOpen) playerCloseRequestKey += 1
    }

    fun closePlayer() {
        playerOpen = false
        playerOpenFromMini = false
    }

    fun markNoPlayableTrack() {
        playing = false
        playerOpen = false
        miniDismissed = true
    }

    fun markPlayableTrack() {
        miniDismissed = false
    }

    fun disableSleepTimer() {
        sleepTimerMinutes = 0
        sleepTimerDeadlineMillis = 0L
    }

    fun setSleepTimer(minutes: Int, fadeOutEnabled: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        sleepTimerFadeOutEnabled = fadeOutEnabled
        val snappedMinutes = (minutes / 5f).roundToInt().coerceIn(0, 24) * 5
        if (snappedMinutes <= 0) {
            disableSleepTimer()
        } else {
            sleepTimerMinutes = snappedMinutes
            sleepTimerDeadlineMillis = nowMillis + snappedMinutes * 60_000L
        }
    }

    fun seekToProgress(durationMillis: Long, value: Float): Long {
        val target = if (durationMillis > 0L) {
            (durationMillis * value.coerceIn(0f, 1f)).toLong()
        } else {
            0L
        }
        requestSeek(target)
        return target
    }

    fun seekBy(durationMillis: Long, deltaMillis: Long): Long {
        val duration = durationMillis.coerceAtLeast(0L)
        val target = if (duration > 0L) {
            (positionMillis + deltaMillis).coerceIn(0L, duration)
        } else {
            0L
        }
        requestSeek(target)
        return target
    }

    fun requestSeek(targetMillis: Long) {
        positionMillis = targetMillis
        seekRequestMillis = targetMillis
        seekRequestKey += 1
    }

    companion object {
        val Saver: Saver<PulseDeckPlaybackState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.playerOpen,
                    state.playerOpenFromMini,
                    state.playerOpenKey,
                    state.playerAutoOpened,
                    state.playerCloseRequestKey,
                    state.miniDismissed,
                    state.playing,
                    state.trackIndex,
                    state.trackSlideDirection,
                    state.shuffleMode.name,
                    state.repeatMode.name,
                    state.manualQueueTrackKeys,
                    state.sleepTimerMinutes,
                    state.sleepTimerDeadlineMillis,
                    state.sleepTimerFadeOutEnabled,
                    state.sleepTimerDialogOpen,
                )
            },
            restore = { values ->
                val manualQueueValue = values.getOrNull(11)
                val hasSavedManualQueue = manualQueueValue is List<*>
                val sleepOffset = if (hasSavedManualQueue) 1 else 0
                PulseDeckPlaybackState(
                    playerOpen = values.getOrNull(0) as? Boolean ?: false,
                    playerOpenFromMini = values.getOrNull(1) as? Boolean ?: false,
                    playerOpenKey = values.getOrNull(2) as? Int ?: 0,
                    playerAutoOpened = values.getOrNull(3) as? Boolean ?: false,
                    playerCloseRequestKey = values.getOrNull(4) as? Int ?: 0,
                    miniDismissed = values.getOrNull(5) as? Boolean ?: false,
                    playing = values.getOrNull(6) as? Boolean ?: false,
                    trackIndex = values.getOrNull(7) as? Int ?: 0,
                    trackSlideDirection = values.getOrNull(8) as? Int ?: 1,
                    shuffleMode = enumValueOrDefault(values.getOrNull(9), ShuffleMode.Off),
                    repeatMode = enumValueOrDefault(values.getOrNull(10), PlaybackRepeatMode.AllOnce),
                    manualQueueTrackKeys = (manualQueueValue as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    sleepTimerMinutes = values.getOrNull(11 + sleepOffset) as? Int ?: 0,
                    sleepTimerDeadlineMillis = values.getOrNull(12 + sleepOffset) as? Long ?: 0L,
                    sleepTimerFadeOutEnabled = values.getOrNull(13 + sleepOffset) as? Boolean ?: true,
                    sleepTimerDialogOpen = values.getOrNull(14 + sleepOffset) as? Boolean ?: false,
                )
            },
        )
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: Any?, default: T): T {
    val name = value as? String ?: return default
    return runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
