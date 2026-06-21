package com.pulsedeck.app

import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.library.cachedDeviceTracksToJson
import com.pulsedeck.app.library.parseCachedDeviceTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessMetadataTest {
    @Test
    fun replayGainOffIgnoresSourceMetadataAndStaysTransparent() {
        val metadata = LoudnessMetadata(
            trackGainDb = -7.5f,
            source = LoudnessSource.FileTag,
            confidence = LoudnessConfidence.High,
        )
        val state = AudioEngineState(
            replayGainMode = ReplayGainMode.Off,
            replayGainPreampDb = 6f,
            replayGain = ReplayGainSettings(enabled = false, mode = ReplayGainMode.Off, trackPreampDb = 6f),
        ).withSourceLoudness(metadata)
        val policy = state.effectiveProcessingPolicy(nativeAvailable = true)

        assertEquals(ReplayGainMode.Off, state.replayGainMode)
        assertEquals(0f, state.activeReplayGainDb, 0.001f)
        assertFalse(policy.replayGainActive)
        assertTrue(policy.transparentPathExpected)
        assertEquals(1f, audioOutputGain(state, 1f), 0.001f)
    }

    @Test
    fun trackReplayGainTagsDriveEffectiveOutputGain() {
        val base = AudioEngineState().withReplayGainSettings(
            ReplayGainSettings(enabled = true, mode = ReplayGainMode.Track, trackPreampDb = 1f),
        )
        val effective = base.withSourceLoudness(
            LoudnessMetadata(
                trackGainDb = -7.5f,
                trackPeak = 0.98f,
                source = LoudnessSource.FileTag,
                confidence = LoudnessConfidence.High,
            ),
        )
        val policy = effective.effectiveProcessingPolicy(nativeAvailable = true)

        assertEquals(-6.5f, effective.activeReplayGainDb, 0.001f)
        assertEquals(-7.5f, effective.effectiveReplayGain.metadataGainDb ?: 0f, 0.001f)
        assertEquals(ReplayGainBasis.Track, effective.effectiveReplayGain.basis)
        assertEquals(LoudnessSource.FileTag, policy.loudnessSource)
        assertTrue(policy.replayGainActive)
        assertFalse(policy.preventClippingActive)
        assertEquals(dbToLinear(-6.5f), audioOutputGain(effective, 1f), 0.001f)
    }

    @Test
    fun albumModeRequiresAlbumGainWhileSmartFallsBackToTrackGain() {
        val metadata = LoudnessMetadata(
            trackGainDb = -4f,
            source = LoudnessSource.FileTag,
            confidence = LoudnessConfidence.High,
        )
        val albumMode = AudioEngineState().withReplayGainSettings(
            ReplayGainSettings(enabled = true, mode = ReplayGainMode.Album),
        ).withSourceLoudness(metadata)
        val smartMode = AudioEngineState().withReplayGainSettings(
            ReplayGainSettings(enabled = true, mode = ReplayGainMode.Smart),
        ).withSourceLoudness(metadata)

        assertEquals(0f, albumMode.activeReplayGainDb, 0.001f)
        assertEquals(ReplayGainBasis.None, albumMode.effectiveReplayGain.basis)
        assertEquals(-4f, smartMode.activeReplayGainDb, 0.001f)
        assertEquals(ReplayGainBasis.Track, smartMode.effectiveReplayGain.basis)
    }

    @Test
    fun positiveReplayGainParticipatesInFullGraphHeadroom() {
        val base = AudioEngineState(
            eqBands = flatEqBands().map { it.copy(gainDb = 4f) },
        ).withReplayGainSettings(
            ReplayGainSettings(enabled = true, mode = ReplayGainMode.Track, trackPreampDb = 1f),
        )
        val effective = base.withSourceLoudness(
            LoudnessMetadata(
                trackGainDb = 3f,
                source = LoudnessSource.FileTag,
                confidence = LoudnessConfidence.High,
            ),
        )
        val policy = effective.effectiveProcessingPolicy(nativeAvailable = true)

        assertEquals(4f, effective.activeReplayGainDb, 0.001f)
        assertTrue(policy.preventClippingActive)
        assertEquals(-8f, policy.estimatedHeadroomDb, 0.001f)
        assertEquals(dbToLinear(-4f), audioOutputGain(effective, 1f), 0.001f)
    }

    @Test
    fun parserReadsReplayGainAndR128Tags() {
        val metadata = parseLoudnessMetadataText(
            """
            REPLAYGAIN_TRACK_GAIN=-8.25 dB
            REPLAYGAIN_ALBUM_GAIN=-6.00 dB
            REPLAYGAIN_TRACK_PEAK=0.991
            REPLAYGAIN_ALBUM_PEAK=1.023
            R128_TRACK_GAIN -1280
            R128_ALBUM_GAIN -2304
            INTEGRATED_LUFS=-15.4
            """.trimIndent(),
            nowMillis = 123L,
        )

        assertEquals(-8.25f, metadata.trackGainDb ?: 0f, 0.001f)
        assertEquals(-6f, metadata.albumGainDb ?: 0f, 0.001f)
        assertEquals(0.991f, metadata.trackPeak ?: 0f, 0.001f)
        assertEquals(1.023f, metadata.albumPeak ?: 0f, 0.001f)
        assertEquals(-5f, metadata.r128TrackGainDb ?: 0f, 0.001f)
        assertEquals(-9f, metadata.r128AlbumGainDb ?: 0f, 0.001f)
        assertEquals(-15.4f, metadata.integratedLufs ?: 0f, 0.001f)
        assertEquals(LoudnessSource.FileTag, metadata.source)
        assertEquals(LoudnessConfidence.High, metadata.confidence)
        assertEquals(123L, metadata.scannedAtMillis)
    }

    @Test
    fun jsonCacheRoundTripPreservesLoudnessMetadata() {
        val metadata = LoudnessMetadata(
            trackGainDb = -5.5f,
            albumGainDb = -4f,
            trackPeak = 0.88f,
            source = LoudnessSource.FileTag,
            confidence = LoudnessConfidence.High,
            scannedAtMillis = 456L,
        )
        val track = Track(
            title = "Blue Hour",
            artist = "Nina Vale",
            duration = "3:10",
            album = Album("Blue Hour", "Nina Vale", "BH", Color.Blue, Color.Cyan),
            loudnessMetadata = loudnessMetadataFromJson(metadata.toJson()),
        )
        val restored = parseCachedDeviceTracks(cachedDeviceTracksToJson(listOf(track))).single()

        assertEquals(-5.5f, restored.loudnessMetadata.trackGainDb ?: 0f, 0.001f)
        assertEquals(-4f, restored.loudnessMetadata.albumGainDb ?: 0f, 0.001f)
        assertEquals(0.88f, restored.loudnessMetadata.trackPeak ?: 0f, 0.001f)
        assertEquals(LoudnessSource.FileTag, restored.loudnessMetadata.source)
        assertEquals(LoudnessConfidence.High, restored.loudnessMetadata.confidence)
        assertEquals(456L, restored.loudnessMetadata.scannedAtMillis)
    }
}
