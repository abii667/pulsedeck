package com.pulsedeck.app

import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.settings.runtime.DecoderFormatDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputPluginType
import com.pulsedeck.app.settings.runtime.OutputRouteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioChainInspectorTest {
    @Test
    fun engineOffSnapshotReportsTransparentPremiumDeckPath() {
        val track = testTrack(
            quality = "MP4A LATM 44.1 kHz stereo",
            mimeType = "audio/mp4a-latm",
            folderPath = "PulseDeck/Stream Library",
        )
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(enabled = false, bypass = false),
            track = track,
            quality = track.quality,
            diagnostics = OutputDiagnosticsSnapshot(updatedAtMillis = 1L),
            nativeAvailable = true,
        )

        assertEquals("Saved PremiumDeck file", snapshot.source.label)
        assertEquals("MP4A LATM", snapshot.source.format)
        assertEquals(44_100, snapshot.source.sampleRateHz)
        assertEquals(2, snapshot.source.channels)
        assertEquals("Engine off", snapshot.dsp.label)
        assertFalse(snapshot.dsp.processingActive)
        assertEquals(listOf("Saved settings only"), snapshot.dsp.activeStages)
        assertEquals(100, snapshot.output.outputGainPercent)
    }

    @Test
    fun nativeDspSnapshotUsesRuntimeOwnershipOverPlatformEffects() {
        val state = AudioEngineState(
            eqBands = flatEqBands().map { it.copy(gainDb = 3f) },
            native = NativeAudioSettings(enabled = true, media3DspEnabled = true),
        )
        val snapshot = audioChainSnapshot(
            state = state,
            track = testTrack(mimeType = "audio/flac", quality = "FLAC  44.1 kHz  stereo"),
            quality = "FLAC  44.1 kHz  stereo",
            diagnostics = OutputDiagnosticsSnapshot(
                routeStatuses = listOf(
                    OutputRouteStatus(
                        route = OutputDeviceType.Speaker,
                        requestedPlugin = OutputPluginType.Media3AudioTrack,
                        actualPlugin = OutputPluginType.Media3AudioTrack,
                        available = true,
                    ),
                ),
                nativeMedia3DspRequested = true,
                nativeMedia3DspActive = true,
                preventClippingActive = true,
                estimatedHeadroomDb = -3f,
                updatedAtMillis = 2L,
            ),
            nativeAvailable = true,
        )

        assertEquals("Native DSP", snapshot.dsp.label)
        assertTrue(snapshot.dsp.nativeMedia3DspRequested)
        assertTrue(snapshot.dsp.nativeMedia3DspActive)
        assertFalse(snapshot.dsp.platformEffectsActive)
        assertTrue(snapshot.dsp.activeStages.contains("Native Media3 PCM DSP"))
        assertTrue(snapshot.device.verified)
    }

    @Test
    fun hiResUsbSnapshotStaysExplicitlyUnverified() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                output = OutputSettings(
                    mode = OutputMode.HiRes,
                    deviceProfile = DeviceProfile.UsbDac,
                    hiResEnabled = true,
                ),
            ),
            track = testTrack(),
            quality = "MP3  320 kbps  44.1 kHz  stereo",
            diagnostics = OutputDiagnosticsSnapshot(
                routeStatuses = listOf(
                    OutputRouteStatus(
                        route = OutputDeviceType.UsbDac,
                        requestedPlugin = OutputPluginType.HiRes,
                        actualPlugin = OutputPluginType.Media3AudioTrack,
                        available = false,
                        reason = "USB DAC hi-res output is gated until device support is verified.",
                    ),
                ),
                updatedAtMillis = 3L,
            ),
        )

        assertEquals("Media3/AudioTrack", snapshot.output.actualPlugin)
        assertFalse(snapshot.device.verified)
        assertEquals(OutputVerificationStatus.Unsupported, snapshot.outputCapability.status)
        assertEquals(OutputPluginType.Media3AudioTrack, snapshot.outputCapability.actualPlugin)
        assertTrue(snapshot.outputCapability.hiResRequested)
        assertTrue(snapshot.outputCapability.usbDacRequested)
        assertTrue(snapshot.outputCapability.supportedSampleRatesHz.isEmpty())
        assertTrue(snapshot.outputCapability.supportedEncodings.isEmpty())
        assertEquals(HiResOutputStatus.Requested, snapshot.outputCapability.hiResOutputStatus)
        assertTrue(snapshot.warnings.any { it.title == "Hi-res requested" })
        assertTrue(snapshot.warnings.any { it.title == "USB DAC route not verified" })
        assertTrue(snapshot.warnings.any { it.title == "Output fallback" })
    }

    @Test
    fun remoteSourceLocationIsSchemeOnly() {
        val location = sourceLocationLabel("https")

        assertEquals("HTTPS stream", location)
        assertFalse(location.contains("googlevideo"))
        assertFalse(location.contains("videoplayback"))
        assertFalse(location.contains("signature"))
    }

    @Test
    fun phase17aProAudioInfoSectionsStayOrderedAndClaimsConservative() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                resampler = ResamplerSettings(
                    mode = ResamplerMode.HighQuality,
                    outputSampleRate = OutputSampleRate.Hz48000,
                ),
                output = OutputSettings(
                    mode = OutputMode.HiRes,
                    deviceProfile = DeviceProfile.UsbDac,
                    hiResEnabled = true,
                ),
            ),
            track = testTrack(quality = "FLAC  44.1 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  44.1 kHz  stereo",
            diagnostics = OutputDiagnosticsSnapshot(
                routeStatuses = listOf(
                    OutputRouteStatus(
                        route = OutputDeviceType.UsbDac,
                        requestedPlugin = OutputPluginType.HiRes,
                        actualPlugin = OutputPluginType.Media3AudioTrack,
                        available = false,
                        reason = "USB DAC hi-res output is gated until device support is verified.",
                    ),
                ),
                updatedAtMillis = 9L,
            ),
        )

        assertEquals(
            listOf(
                "Source",
                "Decoder",
                "Resampler",
                "DSP",
                "Headroom",
                "Output",
                "Device",
                "Bit-perfect / Hi-res eligibility",
                "Warnings",
                "Debug details",
            ),
            proAudioInfoSectionOrder().map { it.label },
        )
        val claims = proAudioInfoConservativeClaims(snapshot)
        assertTrue(claims.any { it.contains("planned only") })
        assertTrue(claims.any { it.contains("Hi-res output is requested only") })
        assertTrue(claims.any { it.contains("USB DAC route is requested only") })
        assertTrue(claims.any { it.contains("Bit-perfect output is not active") })
        assertTrue(claims.any { it.contains("not reported") })
        assertFalse(snapshot.bitPerfect.active)
        assertEquals(OutputVerificationStatus.Unsupported, snapshot.outputCapability.status)
    }

    @Test
    fun media3DecoderDiagnosticsPopulateRuntimeFormatWithoutGuessingHardware() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(),
            track = testTrack(quality = "STREAM", mimeType = null),
            quality = "STREAM",
            diagnostics = OutputDiagnosticsSnapshot(
                decoderFormat = DecoderFormatDiagnosticsSnapshot(
                    containerMimeType = "audio/mp4",
                    sampleMimeType = "audio/mp4a-latm",
                    codecString = "mp4a.40.2",
                    averageBitrateKbps = 128,
                    encodedSampleRateHz = 48_000,
                    encodedChannelCount = 2,
                    decoderName = "c2.android.aac.decoder",
                    decodedPcmEncoding = "PCM16",
                    decodedSampleRateHz = 48_000,
                    decodedChannelCount = 2,
                    updatedAtMillis = 4L,
                ),
                updatedAtMillis = 4L,
            ),
        )

        assertEquals("audio/mp4a-latm", snapshot.source.mimeType)
        assertEquals(128, snapshot.source.bitrateKbps)
        assertEquals(48_000, snapshot.source.sampleRateHz)
        assertEquals("c2.android.aac.decoder", snapshot.decoder.label)
        assertEquals("mp4a.40.2", snapshot.decoder.codec)
        assertEquals("PCM16", snapshot.decoder.outputEncoding)
        assertEquals("Unknown", snapshot.decoder.hardwareAcceleration)
    }

    @Test
    fun resamplerReportsKnownMismatchAsLikelySystemManagedNotVerified() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                resampler = ResamplerSettings(
                    mode = ResamplerMode.System,
                    outputSampleRate = OutputSampleRate.Hz48000,
                ),
            ),
            track = testTrack(quality = "FLAC  44.1 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  44.1 kHz  stereo",
            diagnostics = OutputDiagnosticsSnapshot(
                decoderFormat = DecoderFormatDiagnosticsSnapshot(
                    sampleMimeType = "audio/flac",
                    encodedSampleRateHz = 44_100,
                    encodedChannelCount = 2,
                    decodedPcmEncoding = "PCM16",
                    decodedSampleRateHz = 44_100,
                    decodedChannelCount = 2,
                    updatedAtMillis = 5L,
                ),
                updatedAtMillis = 5L,
            ),
        )

        assertEquals("Known source != output request", snapshot.resampler.label)
        assertEquals("Likely system resampled", snapshot.resampler.effectiveSampleRate)
        assertFalse(snapshot.resampler.verified)
        assertEquals(ResamplerStatus.LikelySystemResampled, snapshot.resamplerStrategy.resamplerStatus)
        assertEquals(ResamplerEngine.AndroidSystem, snapshot.resamplerStrategy.activeEngine)
        assertEquals(44_100, snapshot.resamplerStrategy.decodedSampleRateHz)
        assertEquals(48_000, snapshot.resamplerStrategy.outputSampleRateHz)
    }

    @Test
    fun phase15aHighQualityResamplerIsPlannedOnlyAndNeverClaimedActive() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                resampler = ResamplerSettings(
                    mode = ResamplerMode.HighQuality,
                    outputSampleRate = OutputSampleRate.Hz48000,
                    quality = ResamplerQuality.Ultra,
                ),
            ),
            track = testTrack(quality = "FLAC  44.1 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  44.1 kHz  stereo",
            diagnostics = OutputDiagnosticsSnapshot(
                decoderFormat = DecoderFormatDiagnosticsSnapshot(
                    sampleMimeType = "audio/flac",
                    encodedSampleRateHz = 44_100,
                    encodedChannelCount = 2,
                    decodedPcmEncoding = "PCM16",
                    decodedSampleRateHz = 44_100,
                    decodedChannelCount = 2,
                    updatedAtMillis = 6L,
                ),
                updatedAtMillis = 6L,
            ),
        )

        assertEquals(ResamplerStatus.PulseDeckPlanned, snapshot.resamplerStrategy.resamplerStatus)
        assertEquals(ResamplerStrategy.HighQualityPlanned, snapshot.resamplerStrategy.strategy)
        assertEquals(ResamplerQualityMode.UltraPlanned, snapshot.resamplerStrategy.qualityMode)
        assertEquals(ResamplerEngine.PulseDeckNativePlanned, snapshot.resamplerStrategy.activeEngine)
        assertTrue(snapshot.resamplerStrategy.warning?.contains("No PulseDeck HQ resampler") == true)
        assertFalse(snapshot.resampler.verified)
        assertTrue(snapshot.resamplerStrategy.resamplerStatus != ResamplerStatus.PulseDeckActive)
    }

    @Test
    fun phase16aDefaultMedia3RouteReportsRouteOnlyAndNoHardwareCapabilityTable() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(output = OutputSettings(deviceProfile = DeviceProfile.Speaker)),
            track = testTrack(),
            quality = "MP3  320 kbps  44.1 kHz  stereo",
            diagnostics = OutputDiagnosticsSnapshot(
                routeStatuses = listOf(
                    OutputRouteStatus(
                        route = OutputDeviceType.Speaker,
                        requestedPlugin = OutputPluginType.Media3AudioTrack,
                        actualPlugin = OutputPluginType.Media3AudioTrack,
                        available = true,
                    ),
                ),
                updatedAtMillis = 7L,
            ),
        )

        assertEquals(OutputVerificationStatus.Verified, snapshot.outputCapability.status)
        assertEquals(OutputDeviceType.Speaker, snapshot.outputCapability.route)
        assertEquals(OutputPluginType.Media3AudioTrack, snapshot.outputCapability.requestedPlugin)
        assertEquals(OutputPluginType.Media3AudioTrack, snapshot.outputCapability.actualPlugin)
        assertFalse(snapshot.outputCapability.hiResRequested)
        assertFalse(snapshot.outputCapability.usbDacRequested)
        assertTrue(snapshot.outputCapability.supportedSampleRatesHz.isEmpty())
        assertTrue(snapshot.outputCapability.supportedEncodings.isEmpty())
    }

    @Test
    fun phase16bBitPerfectAttemptStaysOffAndReportsConflicts() {
        val boostedEq = flatEqBands().mapIndexed { index, band ->
            if (index == 5) band.copy(gainDb = 6f) else band
        }
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                preampDb = 1f,
                replayGainMode = ReplayGainMode.Track,
                replayGainPreampDb = 1f,
                eqBands = boostedEq,
                resampler = ResamplerSettings(
                    mode = ResamplerMode.System,
                    outputSampleRate = OutputSampleRate.Hz48000,
                ),
                crossfade = CrossfadeSettings(crossfadeEnabled = true),
            ),
            track = testTrack(quality = "FLAC  44.1 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  44.1 kHz  stereo",
            diagnostics = OutputDiagnosticsSnapshot(
                routeStatuses = listOf(
                    OutputRouteStatus(
                        route = OutputDeviceType.Speaker,
                        requestedPlugin = OutputPluginType.Media3AudioTrack,
                        actualPlugin = OutputPluginType.Media3AudioTrack,
                        available = true,
                    ),
                ),
                decoderFormat = DecoderFormatDiagnosticsSnapshot(
                    sampleMimeType = "audio/flac",
                    encodedSampleRateHz = 44_100,
                    encodedChannelCount = 2,
                    decodedPcmEncoding = "PCM16",
                    decodedSampleRateHz = 44_100,
                    decodedChannelCount = 2,
                    updatedAtMillis = 8L,
                ),
                updatedAtMillis = 8L,
            ),
            nativeAvailable = true,
        )

        val conflictTypes = snapshot.bitPerfect.conflicts.map { it.type }.toSet()

        assertFalse(snapshot.bitPerfect.userAttemptEnabled)
        assertFalse(snapshot.bitPerfect.active)
        assertFalse(snapshot.bitPerfect.eligible)
        assertEquals(BitPerfectAttemptStatus.Off, snapshot.bitPerfect.status)
        assertTrue(BitPerfectConflictType.DspActive in conflictTypes)
        assertTrue(BitPerfectConflictType.ReplayGainActive in conflictTypes)
        assertTrue(BitPerfectConflictType.EqActive in conflictTypes)
        assertTrue(BitPerfectConflictType.CrossfadeActive in conflictTypes)
        assertTrue(BitPerfectConflictType.ResamplerMismatch in conflictTypes)
        assertTrue(BitPerfectConflictType.OutputTrimActive in conflictTypes)
        assertTrue(BitPerfectConflictType.DeviceUnverified in conflictTypes)
        assertTrue(snapshot.bitPerfect.warning?.contains("No bit-perfect output is active") == true)
    }

    private fun testTrack(
        quality: String = "MP3  320 kbps  44.1 kHz  stereo",
        mimeType: String? = "audio/mpeg",
        folderPath: String? = null,
    ): Track =
        Track(
            title = "Test Tone",
            artist = "PulseDeck",
            duration = "3:00",
            album = Album(
                title = "Test Album",
                artist = "PulseDeck",
                mark = "TA",
                tint = Color(0xFF202020),
                alt = Color(0xFF404040),
            ),
            uri = null,
            durationMillis = 180_000L,
            quality = quality,
            mimeType = mimeType,
            folderPath = folderPath,
        )
}
