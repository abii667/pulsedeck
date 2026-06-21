package com.pulsedeck.app

import androidx.compose.ui.graphics.Color
import com.pulsedeck.app.settings.runtime.AudioTrackDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.DecoderFormatDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputDeviceDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputPluginType
import com.pulsedeck.app.settings.runtime.OutputRouteStatus
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeEngine
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeSnapshot
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase24ProOutputEngineTest {
    @Test
    fun hiResProOutputBecomesActiveOnlyWithLocalHiResSourceAndRuntimeOutputEvidence() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                enabled = false,
                output = OutputSettings(
                    mode = OutputMode.HiRes,
                    deviceProfile = DeviceProfile.Wired,
                    sampleRate = OutputSampleRate.Hz96000,
                    bitDepth = OutputBitDepth.Bit24,
                ),
            ),
            track = localTrack(
                quality = "FLAC  96 kHz  stereo",
                mimeType = "audio/flac",
                folderPath = "Music/HiRes",
            ),
            quality = "FLAC  96 kHz  stereo",
            diagnostics = hiResDiagnostics(route = OutputDeviceType.WiredHeadsetAux),
            nativeAvailable = true,
        )

        assertTrue(snapshot.proOutput.enabledByUser)
        assertTrue(snapshot.proOutput.active)
        assertEquals(ProOutputMode.HiResAttempt, snapshot.proOutput.mode)
        assertEquals(ProOutputEngine.Media3CustomOutput, snapshot.proOutput.engine)
        assertEquals(ProOutputStatus.Active, snapshot.proOutput.currentStatus)
        assertTrue(snapshot.proOutput.sourceEligibility.localOrOffline)
        assertTrue(snapshot.proOutput.sourceEligibility.hiResSource)
        assertTrue(snapshot.proOutput.routeEligibility.highResolutionOutputObserved)
        assertTrue(snapshot.proOutput.blockers.isEmpty())
    }

    @Test
    fun premiumDeckAndNetworkSourcesStayBlockedFromProOutputEngine() {
        val streamSnapshot = audioChainSnapshot(
            state = AudioEngineState(
                enabled = false,
                output = OutputSettings(
                    mode = OutputMode.HiRes,
                    deviceProfile = DeviceProfile.Wired,
                    sampleRate = OutputSampleRate.Hz96000,
                ),
            ),
            track = localTrack(
                quality = "FLAC  96 kHz  stereo",
                mimeType = "audio/flac",
            ),
            quality = "FLAC  96 kHz  stereo",
            diagnostics = hiResDiagnostics(route = OutputDeviceType.WiredHeadsetAux),
            nativeAvailable = true,
        )
        val premiumSnapshot = audioChainSnapshot(
            state = AudioEngineState(
                enabled = false,
                output = OutputSettings(
                    mode = OutputMode.HiRes,
                    deviceProfile = DeviceProfile.Wired,
                    sampleRate = OutputSampleRate.Hz96000,
                ),
            ),
            track = localTrack(
                quality = "FLAC  96 kHz  stereo",
                mimeType = "audio/flac",
                folderPath = "PulseDeck/Stream Library",
            ),
            quality = "FLAC  96 kHz  stereo",
            diagnostics = hiResDiagnostics(route = OutputDeviceType.WiredHeadsetAux),
            nativeAvailable = true,
        )

        assertFalse(streamSnapshot.proOutput.active)
        assertFalse(premiumSnapshot.proOutput.active)
        assertEquals(ProOutputStatus.Blocked, streamSnapshot.proOutput.currentStatus)
        assertEquals(ProOutputStatus.Blocked, premiumSnapshot.proOutput.currentStatus)
        assertTrue(ProOutputBlocker.NotLocalOrOffline in streamSnapshot.proOutput.blockers)
        assertTrue(ProOutputBlocker.NotLocalOrOffline in premiumSnapshot.proOutput.blockers)
    }

    @Test
    fun bitPerfectProOutputReportsDspAndReplayGainBlockersWithoutChangingUserSound() {
        val boosted = AudioEngineState(
            replayGainMode = ReplayGainMode.Track,
            replayGainPreampDb = 1f,
            output = OutputSettings(bitPerfectAttemptEnabled = true),
        ).withGraphicEqBandGain(5, 5f)
        val snapshot = audioChainSnapshot(
            state = boosted,
            track = localTrack(
                quality = "FLAC  44.1 kHz  stereo",
                mimeType = "audio/flac",
                folderPath = "Music/Reference",
            ),
            quality = "FLAC  44.1 kHz  stereo",
            diagnostics = pcm441Diagnostics(),
            nativeAvailable = true,
        )

        assertFalse(snapshot.proOutput.active)
        assertEquals(ProOutputStatus.Blocked, snapshot.proOutput.currentStatus)
        assertTrue(ProOutputBlocker.DspActive in snapshot.proOutput.blockers)
        assertTrue(ProOutputBlocker.ReplayGainActive in snapshot.proOutput.blockers)
        assertTrue(ProOutputBlocker.EqActive in snapshot.proOutput.blockers)
        assertTrue(ProOutputBlocker.OutputTrimActive in snapshot.proOutput.blockers)
        assertTrue(snapshot.proOutput.fallbackReason?.contains("preserved") == true)
        assertTrue(boosted.eqStageActive)
        assertEquals(ReplayGainMode.Track, boosted.replayGainMode)
    }

    @Test
    fun bluetoothRouteBlocksHiResActiveClaimEvenWithHighRateAudioTrackEvidence() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                enabled = false,
                output = OutputSettings(
                    mode = OutputMode.HiRes,
                    deviceProfile = DeviceProfile.Bluetooth,
                    sampleRate = OutputSampleRate.Hz96000,
                    bitDepth = OutputBitDepth.Bit24,
                ),
            ),
            track = localTrack(
                quality = "FLAC  96 kHz  stereo",
                mimeType = "audio/flac",
                folderPath = "Music/HiRes",
            ),
            quality = "FLAC  96 kHz  stereo",
            diagnostics = hiResDiagnostics(route = OutputDeviceType.Bluetooth),
            nativeAvailable = true,
        )

        assertFalse(snapshot.outputCapability.hiResActive)
        assertFalse(snapshot.proOutput.active)
        assertEquals(ProOutputStatus.Blocked, snapshot.proOutput.currentStatus)
        assertTrue(snapshot.proOutput.routeEligibility.bluetoothRoute)
        assertTrue(ProOutputBlocker.BluetoothRoute in snapshot.proOutput.blockers)
    }

    private fun localTrack(
        quality: String,
        mimeType: String?,
        folderPath: String? = null,
    ): Track =
        Track(
            title = "Reference Track",
            artist = "PulseDeck",
            duration = "3:00",
            album = Album(
                title = "Reference Album",
                artist = "PulseDeck",
                mark = "RA",
                tint = Color(0xFF202020),
                alt = Color(0xFF404040),
            ),
            durationMillis = 180_000L,
            quality = quality,
            mimeType = mimeType,
            folderPath = folderPath,
        )

    private fun hiResDiagnostics(route: OutputDeviceType): OutputDiagnosticsSnapshot =
        OutputDiagnosticsSnapshot(
            routeStatuses = listOf(
                OutputRouteStatus(
                    route = route,
                    requestedPlugin = OutputPluginType.HiRes,
                    actualPlugin = OutputPluginType.HiRes,
                    available = true,
                ),
            ),
            outputDevice = OutputDeviceDiagnosticsSnapshot(
                requestedRoute = route,
                activeRoute = route,
                routeVerified = true,
                usbDacDetected = route == OutputDeviceType.UsbDac,
                supportedSampleRatesHz = listOf(44_100, 48_000, 96_000),
                supportedEncodings = listOf("PCM16", "PCM24"),
            ),
            audioTrack = AudioTrackDiagnosticsSnapshot(
                configuredSampleRateHz = 96_000,
                encoding = "PCM24",
                channelCount = 2,
                updatedAtMillis = 24L,
            ),
            proOutput = ProOutputRuntimeSnapshot(
                providerInstalled = true,
                enabledByUser = true,
                localOfflineEligible = true,
                sourceScope = "local",
                engine = ProOutputRuntimeEngine.Media3CustomOutput,
                status = ProOutputRuntimeStatus.Active,
                requestedSampleRateHz = 96_000,
                actualSampleRateHz = 96_000,
                requestedEncoding = "PCM24",
                actualEncoding = "PCM24",
                channelCount = 2,
                bufferSizeBytes = 192_000,
                updatedAtMillis = 24L,
            ),
            decoderFormat = DecoderFormatDiagnosticsSnapshot(
                sampleMimeType = "audio/flac",
                encodedSampleRateHz = 96_000,
                encodedChannelCount = 2,
                decodedPcmEncoding = "PCM24",
                decodedSampleRateHz = 96_000,
                decodedChannelCount = 2,
                updatedAtMillis = 24L,
            ),
            updatedAtMillis = 24L,
        )

    private fun pcm441Diagnostics(): OutputDiagnosticsSnapshot =
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
                updatedAtMillis = 44L,
            ),
            decoderFormat = DecoderFormatDiagnosticsSnapshot(
                sampleMimeType = "audio/flac",
                encodedSampleRateHz = 44_100,
                encodedChannelCount = 2,
                decodedPcmEncoding = "PCM16",
                decodedSampleRateHz = 44_100,
                decodedChannelCount = 2,
                updatedAtMillis = 44L,
            ),
            updatedAtMillis = 44L,
        )
}
