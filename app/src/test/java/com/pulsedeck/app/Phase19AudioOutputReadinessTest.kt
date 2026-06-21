package com.pulsedeck.app

import android.media.AudioDeviceInfo
import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.settings.data.SettingsProfileCodec
import com.pulsedeck.app.settings.model.PulseSettingsState
import com.pulsedeck.app.settings.runtime.AudioTrackDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.DecoderFormatDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputDeviceDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputPluginType
import com.pulsedeck.app.settings.runtime.OutputRouteStatus
import com.pulsedeck.app.settings.runtime.TruePeakDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.TruePeakMeasurementStatus
import com.pulsedeck.app.settings.runtime.deviceRouteTypeForAndroidType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase19AudioOutputReadinessTest {
    @Test
    fun bitPerfectAttemptOnlyBecomesActiveWithExplicitToggleAndVerifiedAudioTrack() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                enabled = false,
                output = OutputSettings(bitPerfectAttemptEnabled = true),
            ),
            track = testTrack(quality = "FLAC  44.1 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  44.1 kHz  stereo",
            diagnostics = verifiedSpeakerDiagnostics(),
            nativeAvailable = true,
        )

        assertTrue(snapshot.bitPerfect.userAttemptEnabled)
        assertTrue(snapshot.bitPerfect.eligible)
        assertTrue(snapshot.bitPerfect.active)
        assertEquals(BitPerfectAttemptStatus.Active, snapshot.bitPerfect.status)
        assertEquals(44_100, snapshot.bitPerfect.outputSampleRateHz)
        assertFalse(snapshot.bitPerfect.conflicts.any { it.type == BitPerfectConflictType.DeviceUnverified })
    }

    @Test
    fun bitPerfectAttemptReportsBlockersWithoutDisablingUserDsp() {
        val boosted = AudioEngineState(
            output = OutputSettings(bitPerfectAttemptEnabled = true),
        ).withGraphicEqBandGain(5, 6f)
        val snapshot = audioChainSnapshot(
            state = boosted,
            track = testTrack(quality = "FLAC  44.1 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  44.1 kHz  stereo",
            diagnostics = verifiedSpeakerDiagnostics(),
            nativeAvailable = true,
        )

        assertTrue(snapshot.bitPerfect.userAttemptEnabled)
        assertFalse(snapshot.bitPerfect.active)
        assertEquals(BitPerfectAttemptStatus.Ineligible, snapshot.bitPerfect.status)
        assertTrue(snapshot.bitPerfect.conflicts.any { it.type == BitPerfectConflictType.DspActive })
        assertTrue(snapshot.bitPerfect.conflicts.any { it.type == BitPerfectConflictType.EqActive })
        assertTrue(snapshot.bitPerfect.warning?.contains("will not silently disable") == true)
        assertTrue(boosted.eqStageActive)
    }

    @Test
    fun hiResAndUsbAreActiveOnlyWhenRuntimeEvidenceMatches() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                output = OutputSettings(
                    mode = OutputMode.HiRes,
                    deviceProfile = DeviceProfile.UsbDac,
                    sampleRate = OutputSampleRate.Hz96000,
                    bitDepth = OutputBitDepth.Bit24,
                ),
            ),
            track = testTrack(quality = "FLAC  96 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  96 kHz  stereo",
            diagnostics = OutputDiagnosticsSnapshot(
                routeStatuses = listOf(
                    OutputRouteStatus(
                        route = OutputDeviceType.UsbDac,
                        requestedPlugin = OutputPluginType.HiRes,
                        actualPlugin = OutputPluginType.HiRes,
                        available = true,
                    ),
                ),
                outputDevice = OutputDeviceDiagnosticsSnapshot(
                    requestedRoute = OutputDeviceType.UsbDac,
                    activeRoute = OutputDeviceType.UsbDac,
                    preferredRoute = OutputDeviceType.UsbDac,
                    preferredDeviceName = "USB Audio",
                    preferredDeviceApplied = true,
                    routeVerified = true,
                    usbDacDetected = true,
                    supportedSampleRatesHz = listOf(44_100, 48_000, 96_000),
                    supportedEncodings = listOf("PCM16", "PCM24"),
                ),
                audioTrack = AudioTrackDiagnosticsSnapshot(
                    configuredSampleRateHz = 96_000,
                    encoding = "PCM24",
                    updatedAtMillis = 10L,
                ),
                updatedAtMillis = 10L,
            ),
        )

        assertEquals(OutputVerificationStatus.Verified, snapshot.outputCapability.status)
        assertEquals(HiResOutputStatus.FullHiResActive, snapshot.outputCapability.hiResOutputStatus)
        assertTrue(snapshot.outputCapability.hiResActive)
        assertTrue(snapshot.outputCapability.usbDacActive)
        assertEquals(96_000, snapshot.outputCapability.audioTrackSampleRateHz)
        assertEquals("PCM24", snapshot.outputCapability.audioTrackEncoding)
        assertTrue(snapshot.warnings.any { it.title == "Full hi-res output active" })
        assertTrue(snapshot.warnings.any { it.title == "USB DAC detected" })
    }

    @Test
    fun truePeakGraphUsesMeasuredRuntimeValueOnlyWhenPresent() {
        val graph = dspGraphSnapshot(
            state = AudioEngineState(),
            diagnostics = OutputDiagnosticsSnapshot(
                truePeak = TruePeakDiagnosticsSnapshot(
                    status = TruePeakMeasurementStatus.Measured,
                    samplePeakDbFs = -1.2f,
                    truePeakDbTp = -0.7f,
                    sampleRateHz = 48_000,
                    channelCount = 2,
                    framesMeasured = 48_000,
                    updatedAtMillis = 42L,
                ),
                updatedAtMillis = 42L,
            ),
            nativeAvailable = true,
        )

        assertEquals(TruePeakStatus.Measured, graph.loudness.truePeak.status)
        assertEquals(-0.7f, graph.loudness.truePeak.truePeakDbTp ?: 0f, 0.001f)
        assertTrue(graph.warnings.any { it.title == "True peak measured" })
    }

    @Test
    fun outputDeviceTypeMappingRecognizesUsbDacProfiles() {
        assertEquals(OutputDeviceType.UsbDac, deviceRouteTypeForAndroidType(AudioDeviceInfo.TYPE_USB_DEVICE))
        assertEquals(OutputDeviceType.UsbDac, deviceRouteTypeForAndroidType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(OutputDeviceType.Bluetooth, deviceRouteTypeForAndroidType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(OutputDeviceType.WiredHeadsetAux, deviceRouteTypeForAndroidType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
    }

    @Test
    fun settingsImportBoundsOversizedParametricEqBeforeNormalization() {
        val oversizedBands = (0 until 128).joinToString(",") {
            """{"enabled":true,"frequencyHz":40000,"gainDb":30,"q":99}"""
        }
        val raw = """
            {
              "schemaVersion": 2,
              "audio": {
                "parametricEq": [$oversizedBands]
              }
            }
        """.trimIndent()

        val restored = SettingsProfileCodec.decode(raw, PulseSettingsState()).normalized()

        assertEquals(PARAMETRIC_EQ_SLOT_COUNT, restored.audio.parametricEq.size)
        assertTrue(restored.audio.parametricEq.all { it.frequencyHz == PARAMETRIC_EQ_MAX_FREQUENCY_HZ })
        assertTrue(restored.audio.parametricEq.all { it.gainDb == AUDIO_EQ_MAX_GAIN_DB })
        assertTrue(restored.audio.parametricEq.all { it.q == PARAMETRIC_EQ_MAX_Q })
    }

    private fun verifiedSpeakerDiagnostics(): OutputDiagnosticsSnapshot =
        OutputDiagnosticsSnapshot(
            routeStatuses = listOf(
                OutputRouteStatus(
                    route = OutputDeviceType.Speaker,
                    requestedPlugin = OutputPluginType.Media3AudioTrack,
                    actualPlugin = OutputPluginType.Media3AudioTrack,
                    available = true,
                ),
            ),
            outputDevice = OutputDeviceDiagnosticsSnapshot(
                requestedRoute = OutputDeviceType.Speaker,
                activeRoute = OutputDeviceType.Speaker,
                routeVerified = true,
                supportedSampleRatesHz = listOf(44_100),
                supportedEncodings = listOf("PCM16"),
            ),
            audioTrack = AudioTrackDiagnosticsSnapshot(
                configuredSampleRateHz = 44_100,
                encoding = "PCM16",
                updatedAtMillis = 8L,
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
        )

    private fun testTrack(
        quality: String = "MP3  320 kbps  44.1 kHz  stereo",
        mimeType: String? = "audio/mpeg",
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
            durationMillis = 180_000L,
            quality = quality,
            mimeType = mimeType,
        )
}
