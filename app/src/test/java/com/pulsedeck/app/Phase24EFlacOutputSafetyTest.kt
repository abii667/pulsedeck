package com.pulsedeck.app

import androidx.compose.ui.graphics.Color
import androidx.media3.common.C
import androidx.media3.common.Format
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

class Phase24EFlacOutputSafetyTest {
    @Test
    fun cdQualityFlacUsesStandardOutputEvenWhenHiResIsEnabled() {
        val decision = highPrecisionOutputDecision(
            output = OutputSettings(hiResEnabled = true),
            media = ProOutputCurrentMedia(
                scope = ProOutputSourceScope.Local,
                qualityHint = "FLAC  16-bit  44.1 kHz  stereo",
            ),
            format = audioFormat(sampleRateHz = 44_100, pcmEncoding = C.ENCODING_PCM_16BIT),
        )

        assertFalse(decision.enableFloatOutput)
        assertEquals("source_cd_quality_standard_output", decision.reason)
    }

    @Test
    fun localNinetySixKhzSourceKeepsHighPrecisionAttemptEligible() {
        val decision = highPrecisionOutputDecision(
            output = OutputSettings(hiResEnabled = true),
            media = ProOutputCurrentMedia(
                scope = ProOutputSourceScope.Local,
                qualityHint = "FLAC  24-bit  96 kHz  stereo",
            ),
            format = audioFormat(sampleRateHz = 96_000, pcmEncoding = C.ENCODING_INVALID),
        )

        assertTrue(decision.enableFloatOutput)
        assertEquals("source_high_sample_rate", decision.reason)
    }

    @Test
    fun twentyFourBitStandardRateSourceCanUseHighPrecisionWhenEvidenceExists() {
        val decision = highPrecisionOutputDecision(
            output = OutputSettings(hiResEnabled = true),
            media = ProOutputCurrentMedia(
                scope = ProOutputSourceScope.Local,
                qualityHint = "FLAC  24-bit  44.1 kHz  stereo",
            ),
            format = audioFormat(sampleRateHz = 44_100, pcmEncoding = C.ENCODING_INVALID),
        )

        assertTrue(decision.enableFloatOutput)
        assertEquals("source_high_bit_depth", decision.reason)
    }

    @Test
    fun bluetoothRouteStaysConservativeForHighRateSources() {
        val decision = highPrecisionOutputDecision(
            output = OutputSettings(hiResEnabled = true, deviceProfile = DeviceProfile.Bluetooth),
            media = ProOutputCurrentMedia(
                scope = ProOutputSourceScope.Local,
                qualityHint = "FLAC  24-bit  96 kHz  stereo",
            ),
            format = audioFormat(sampleRateHz = 96_000, pcmEncoding = C.ENCODING_PCM_24BIT),
        )

        assertFalse(decision.enableFloatOutput)
        assertEquals("route_conservative_bluetooth", decision.reason)
    }

    @Test
    fun timestampDiscontinuityFallbackDisablesFloatForTheFormat() {
        val decision = highPrecisionOutputDecision(
            output = OutputSettings(hiResEnabled = true),
            media = ProOutputCurrentMedia(
                scope = ProOutputSourceScope.Local,
                qualityHint = "FLAC  24-bit  96 kHz  stereo",
            ),
            format = audioFormat(sampleRateHz = 96_000, pcmEncoding = C.ENCODING_PCM_24BIT),
            timestampDiscontinuityFallback = true,
        )

        assertFalse(decision.enableFloatOutput)
        assertEquals("audio_sink_timestamp_discontinuity", decision.reason)
    }

    @Test
    fun cdQualityPcmFloatRuntimeDoesNotClaimHiResActive() {
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                enabled = false,
                output = OutputSettings(hiResEnabled = true, deviceProfile = DeviceProfile.Wired),
            ),
            track = testTrack(quality = "FLAC  16-bit  44.1 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  16-bit  44.1 kHz  stereo",
            diagnostics = diagnostics(
                outputSampleRateHz = 44_100,
                outputEncoding = "PCM float",
                decodedEncoding = "PCM16",
                proStatus = ProOutputRuntimeStatus.Started,
            ),
        )

        assertEquals(HiResOutputStatus.Blocked, snapshot.outputCapability.hiResOutputStatus)
        assertFalse(snapshot.outputCapability.hiResActive)
        assertTrue(ProOutputBlocker.SourceNotHiRes in snapshot.outputCapability.hiResEvidence.blockers)
    }

    @Test
    fun discontinuityFallbackWarningIsVisibleInChainWarnings() {
        val fallbackText = "High-precision output was disabled for this track because the audio sink reported timestamp discontinuities."
        val snapshot = audioChainSnapshot(
            state = AudioEngineState(
                enabled = false,
                output = OutputSettings(hiResEnabled = true, deviceProfile = DeviceProfile.Wired),
            ),
            track = testTrack(quality = "FLAC  24-bit  96 kHz  stereo", mimeType = "audio/flac"),
            quality = "FLAC  24-bit  96 kHz  stereo",
            diagnostics = diagnostics(
                outputSampleRateHz = 96_000,
                outputEncoding = "PCM16",
                decodedEncoding = "PCM24",
                proStatus = ProOutputRuntimeStatus.FallbackToMedia3,
                fallbackReason = fallbackText,
            ),
        )

        assertTrue(snapshot.proOutput.fallbackReason?.contains("timestamp discontinuities") == true)
        assertTrue(snapshot.warnings.any { it.detail.contains("timestamp discontinuities") })
    }

    private fun audioFormat(sampleRateHz: Int, pcmEncoding: Int): Format =
        Format.Builder()
            .setSampleMimeType("audio/flac")
            .setSampleRate(sampleRateHz)
            .setChannelCount(2)
            .setPcmEncoding(pcmEncoding)
            .build()

    private fun diagnostics(
        outputSampleRateHz: Int,
        outputEncoding: String,
        decodedEncoding: String,
        proStatus: ProOutputRuntimeStatus,
        fallbackReason: String? = null,
    ): OutputDiagnosticsSnapshot =
        OutputDiagnosticsSnapshot(
            routeStatuses = listOf(
                OutputRouteStatus(
                    route = OutputDeviceType.WiredHeadsetAux,
                    requestedPlugin = OutputPluginType.Media3AudioTrack,
                    actualPlugin = OutputPluginType.Media3AudioTrack,
                    available = true,
                ),
            ),
            outputDevice = OutputDeviceDiagnosticsSnapshot(
                requestedRoute = OutputDeviceType.WiredHeadsetAux,
                activeRoute = OutputDeviceType.WiredHeadsetAux,
                routeVerified = true,
                supportedSampleRatesHz = listOf(44_100, 48_000, 96_000),
                supportedEncodings = listOf("PCM16", "PCM24", "PCM float"),
            ),
            audioTrack = AudioTrackDiagnosticsSnapshot(
                configuredSampleRateHz = outputSampleRateHz,
                encoding = outputEncoding,
                channelCount = 2,
                underrunCount = 0,
                updatedAtMillis = 10L,
            ),
            proOutput = ProOutputRuntimeSnapshot(
                providerInstalled = true,
                enabledByUser = true,
                localOfflineEligible = true,
                sourceScope = "local",
                engine = ProOutputRuntimeEngine.Media3Default,
                status = proStatus,
                requestedSampleRateHz = outputSampleRateHz,
                actualSampleRateHz = outputSampleRateHz,
                requestedEncoding = outputEncoding,
                actualEncoding = outputEncoding,
                channelCount = 2,
                underrunCount = 0,
                fallbackReason = fallbackReason,
                updatedAtMillis = 10L,
            ),
            decoderFormat = DecoderFormatDiagnosticsSnapshot(
                sampleMimeType = "audio/flac",
                encodedSampleRateHz = outputSampleRateHz,
                encodedChannelCount = 2,
                decodedPcmEncoding = decodedEncoding,
                decodedSampleRateHz = outputSampleRateHz,
                decodedChannelCount = 2,
                updatedAtMillis = 10L,
            ),
            updatedAtMillis = 10L,
        )

    private fun testTrack(
        quality: String,
        mimeType: String?,
    ): Track =
        Track(
            title = "Test FLAC",
            artist = "PulseDeck",
            duration = "3:00",
            album = Album(
                title = "Test Album",
                artist = "PulseDeck",
                mark = "TF",
                tint = Color(0xFF202020),
                alt = Color(0xFF404040),
            ),
            uri = null,
            durationMillis = 180_000L,
            quality = quality,
            mimeType = mimeType,
        )
}
