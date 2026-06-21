package com.pulsedeck.app

import androidx.media3.common.C
import com.pulsedeck.app.settings.model.ButtonPressMode
import com.pulsedeck.app.settings.model.HeadsetBluetoothSettings
import com.pulsedeck.app.settings.runtime.CommandCueSource
import com.pulsedeck.app.settings.runtime.HardwarePlaybackAction
import com.pulsedeck.app.settings.runtime.actionForAmbiguousButton
import com.pulsedeck.app.settings.runtime.relativeSeekTarget
import com.pulsedeck.app.settings.runtime.shouldConsumeStrictNoop
import com.pulsedeck.app.settings.runtime.shouldPlayCommandHaptic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandFeedbackPolicyTest {
    @Test
    fun relativeSeekWithUnknownDurationDoesNotClampToZero() {
        assertEquals(25_000L, relativeSeekTarget(15_000L, C.TIME_UNSET, 10_000L))
        assertEquals(0L, relativeSeekTarget(4_000L, C.TIME_UNSET, -10_000L))
    }

    @Test
    fun relativeSeekWithKnownDurationClampsToTrackBounds() {
        assertEquals(20_000L, relativeSeekTarget(15_000L, 20_000L, 10_000L))
        assertEquals(5_000L, relativeSeekTarget(15_000L, 20_000L, -10_000L))
    }

    @Test
    fun ambiguousButtonModesResolveExpectedActions() {
        assertEquals(
            HardwarePlaybackAction.PlayPause,
            actionForAmbiguousButton(ButtonPressMode.SinglePress, completedClickCount = 3, longPress = false),
        )
        assertEquals(
            HardwarePlaybackAction.PlayPause,
            actionForAmbiguousButton(ButtonPressMode.DoubleTripleNextPrev, completedClickCount = 1, longPress = false),
        )
        assertEquals(
            HardwarePlaybackAction.Next,
            actionForAmbiguousButton(ButtonPressMode.DoubleTripleNextPrev, completedClickCount = 2, longPress = false),
        )
        assertEquals(
            HardwarePlaybackAction.Previous,
            actionForAmbiguousButton(ButtonPressMode.DoubleTripleNextPrev, completedClickCount = 3, longPress = false),
        )
        assertEquals(
            HardwarePlaybackAction.Next,
            actionForAmbiguousButton(ButtonPressMode.LongPressNext, completedClickCount = 1, longPress = true),
        )
        assertEquals(
            HardwarePlaybackAction.PlayPause,
            actionForAmbiguousButton(ButtonPressMode.LongPressNext, completedClickCount = 1, longPress = false),
        )
    }

    @Test
    fun strictResumePauseStopConsumesOnlyContradictoryCommands() {
        assertTrue(shouldConsumeStrictNoop(HardwarePlaybackAction.Play, isPlaying = true, strict = true))
        assertTrue(shouldConsumeStrictNoop(HardwarePlaybackAction.Pause, isPlaying = false, strict = true))
        assertTrue(shouldConsumeStrictNoop(HardwarePlaybackAction.Stop, isPlaying = false, strict = true))
        assertFalse(shouldConsumeStrictNoop(HardwarePlaybackAction.PlayPause, isPlaying = true, strict = true))
        assertFalse(shouldConsumeStrictNoop(HardwarePlaybackAction.Next, isPlaying = false, strict = true))
        assertFalse(shouldConsumeStrictNoop(HardwarePlaybackAction.Play, isPlaying = true, strict = false))
    }

    @Test
    fun hapticFeedbackUsesVibrateSettingAndBeepMoreForControllerSources() {
        val off = HeadsetBluetoothSettings(vibrate = false, beepMore = true)
        val headsetOnly = HeadsetBluetoothSettings(vibrate = true, beepMore = false)
        val allSources = headsetOnly.copy(beepMore = true)

        assertFalse(shouldPlayCommandHaptic(off, CommandCueSource.HeadsetButton))
        assertTrue(shouldPlayCommandHaptic(headsetOnly, CommandCueSource.HeadsetButton))
        assertFalse(shouldPlayCommandHaptic(headsetOnly, CommandCueSource.MediaSessionButton))
        assertFalse(shouldPlayCommandHaptic(headsetOnly, CommandCueSource.RejectedCommand))
        assertTrue(shouldPlayCommandHaptic(allSources, CommandCueSource.MediaSessionButton))
        assertTrue(shouldPlayCommandHaptic(allSources, CommandCueSource.RejectedCommand))
    }

    @Test
    fun youtubeResolverEndpointPolicyOnlyAllowsLocalDevResolver() {
        assertTrue(isTrustedYouTubeResolverEndpoint("http://127.0.0.1:8787"))
        assertTrue(isTrustedYouTubeResolverEndpoint("http://localhost:8787/"))
        assertTrue(isTrustedYouTubeResolverEndpoint("http://10.0.2.2:8787"))

        assertFalse(isTrustedYouTubeResolverEndpoint("https://127.0.0.1:8787"))
        assertFalse(isTrustedYouTubeResolverEndpoint("http://127.0.0.1:8788"))
        assertFalse(isTrustedYouTubeResolverEndpoint("http://192.168.1.25:8787"))
        assertFalse(isTrustedYouTubeResolverEndpoint("http://127.0.0.1:8787/download/start"))
        assertFalse(isTrustedYouTubeResolverEndpoint("http://127.0.0.1:8787?url=https://youtube.com/watch?v=x"))
    }
}
