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

class Phase24BRealHiResOutputPrototypeTest {
    @Test
    fun proOutputIsOffByDefaultEvenWhenRuntimeOutputIsHighResolution() {
        val snapshot = snapshot(
            state = AudioEngineState(enabled = false),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = hiResRuntimeDiagnostics(customStatus = ProOutputRuntimeStatus.Active),
        )

        assertEquals(ProOutputStatus.Off, snapshot.proOutput.currentStatus)
        assertFalse(snapshot.proOutput.active)
    }

    @Test
    fun hiResAttemptIsUserControlledAndRequiresRuntimeCustomProviderActive() {
        val track = localTrack("FLAC  24-bit  96 kHz  stereo")
        val off = snapshot(
            state = AudioEngineState(enabled = false),
            track = track,
            diagnostics = hiResRuntimeDiagnostics(customStatus = ProOutputRuntimeStatus.Active),
        )
        val on = snapshot(
            state = hiResCleanState(),
            track = track,
            diagnostics = hiResRuntimeDiagnostics(customStatus = ProOutputRuntimeStatus.Active),
        )

        assertFalse(off.proOutput.enabledByUser)
        assertTrue(on.proOutput.enabledByUser)
        assertTrue(on.proOutput.active)
        assertEquals(ProOutputEngine.Media3CustomOutput, on.proOutput.engine)
    }

    @Test
    fun localStandardResolutionFileIsNotHiResEligible() {
        val snapshot = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  16-bit  44.1 kHz  stereo"),
            diagnostics = pcmRuntimeDiagnostics(sampleRateHz = 44_100, encoding = "PCM16"),
        )

        assertFalse(snapshot.proOutput.active)
        assertEquals(ProOutputHiResSourceStatus.NotHiRes, snapshot.proOutput.sourceEligibility.hiResStatus)
        assertTrue(ProOutputBlocker.SourceNotHiRes in snapshot.proOutput.blockers)
    }

    @Test
    fun localTwentyFourNinetySixIsHiResEligibleAndCanBecomeActive() {
        val snapshot = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = hiResRuntimeDiagnostics(customStatus = ProOutputRuntimeStatus.Active),
        )

        assertEquals(ProOutputHiResSourceStatus.VerifiedHiRes, snapshot.proOutput.sourceEligibility.hiResStatus)
        assertTrue(snapshot.outputCapability.hiResActive)
        assertTrue(snapshot.proOutput.active)
    }

    @Test
    fun media3DefaultNinetySixPcm16IsHighSampleRateOnlyNotFullHiRes() {
        val snapshot = snapshot(
            state = AudioEngineState(enabled = false),
            track = localTrack("WAV  24-bit  96 kHz  stereo"),
            diagnostics = media3DefaultRuntimeDiagnostics(sampleRateHz = 96_000, encoding = "PCM16"),
        )

        assertEquals(HiResOutputStatus.HighSampleRateActive, snapshot.outputCapability.hiResOutputStatus)
        assertEquals(HiResEvidenceSource.Media3AudioTrackRuntime, snapshot.outputCapability.hiResEvidence.evidenceSource)
        assertEquals(ProOutputEngine.Media3Default, snapshot.outputCapability.hiResEvidence.engine)
        assertFalse(snapshot.outputCapability.hiResEvidence.providerActive)
        assertFalse(snapshot.outputCapability.hiResActive)
        assertEquals(ProOutputStatus.Off, snapshot.proOutput.currentStatus)
        assertFalse(snapshot.proOutput.active)
        assertFalse(snapshot.bitPerfect.active)
    }

    @Test
    fun hiResAttemptWithMedia3DefaultPcm16ReportsHighSampleRateButBlocksFullClaim() {
        val snapshot = snapshot(
            state = hiResCleanState(),
            track = localTrack("WAV  24-bit  96 kHz  stereo"),
            diagnostics = media3DefaultRuntimeDiagnostics(sampleRateHz = 96_000, encoding = "PCM16"),
        )

        assertEquals(HiResOutputStatus.HighSampleRateActive, snapshot.outputCapability.hiResOutputStatus)
        assertFalse(snapshot.outputCapability.hiResActive)
        assertFalse(snapshot.proOutput.active)
        assertEquals(ProOutputStatus.Blocked, snapshot.proOutput.currentStatus)
        assertTrue(ProOutputBlocker.DeviceRejectedFormat in snapshot.proOutput.blockers)
    }

    @Test
    fun media3DefaultCanVerifyFullHiResOutputWithoutClaimingCustomProviderActive() {
        val snapshot = snapshot(
            state = hiResCleanState(),
            track = localTrack("WAV  24-bit  96 kHz  stereo"),
            diagnostics = media3DefaultRuntimeDiagnostics(sampleRateHz = 96_000, encoding = "PCM24"),
        )

        assertEquals(HiResOutputStatus.FullHiResActive, snapshot.outputCapability.hiResOutputStatus)
        assertTrue(snapshot.outputCapability.hiResActive)
        assertEquals(ProOutputEngine.Media3Default, snapshot.outputCapability.hiResEvidence.engine)
        assertFalse(snapshot.outputCapability.hiResEvidence.providerActive)
        assertFalse(snapshot.proOutput.active)
        assertTrue(ProOutputBlocker.Media3CustomOutputUnavailable in snapshot.proOutput.blockers)
    }

    @Test
    fun newerProOutputRuntimeOverridesStaleAudioTrackDiagnosticsForHiResEvidence() {
        val snapshot = snapshot(
            state = hiResCleanState(),
            track = localTrack("WAV  24-bit  96 kHz  stereo"),
            diagnostics = OutputDiagnosticsSnapshot(
                routeStatuses = verifiedRouteStatuses(OutputDeviceType.WiredHeadsetAux, OutputPluginType.Media3AudioTrack),
                outputDevice = verifiedDevice(OutputDeviceType.WiredHeadsetAux),
                audioTrack = AudioTrackDiagnosticsSnapshot(
                    configuredSampleRateHz = 48_000,
                    encoding = "PCM16",
                    channelCount = 2,
                    underrunCount = 0,
                    updatedAtMillis = 20L,
                ),
                proOutput = ProOutputRuntimeSnapshot(
                    providerInstalled = true,
                    enabledByUser = true,
                    localOfflineEligible = true,
                    sourceScope = "local",
                    engine = ProOutputRuntimeEngine.Media3Default,
                    status = ProOutputRuntimeStatus.Started,
                    requestedSampleRateHz = 96_000,
                    actualSampleRateHz = 96_000,
                    requestedEncoding = "PCM float",
                    actualEncoding = "PCM float",
                    channelCount = 2,
                    underrunCount = 0,
                    updatedAtMillis = 30L,
                ),
                decoderFormat = DecoderFormatDiagnosticsSnapshot(
                    sampleMimeType = "audio/raw",
                    containerMimeType = "audio/wav",
                    encodedSampleRateHz = 96_000,
                    encodedChannelCount = 2,
                    decodedPcmEncoding = "PCM float",
                    decodedSampleRateHz = 96_000,
                    decodedChannelCount = 2,
                    updatedAtMillis = 30L,
                ),
                updatedAtMillis = 30L,
            ),
        )

        assertEquals(HiResOutputStatus.FullHiResActive, snapshot.outputCapability.hiResOutputStatus)
        assertTrue(snapshot.outputCapability.hiResActive)
        assertEquals(96_000, snapshot.outputCapability.hiResEvidence.outputSampleRateHz)
        assertEquals("PCM float", snapshot.outputCapability.hiResEvidence.outputEncoding)
        assertEquals(ProOutputEngine.Media3Default, snapshot.outputCapability.hiResEvidence.engine)
        assertFalse(snapshot.outputCapability.hiResEvidence.providerActive)
        assertFalse(snapshot.outputCapability.usbDacActive)
        assertFalse(snapshot.bitPerfect.active)
        assertFalse(snapshot.proOutput.active)
        assertTrue(ProOutputBlocker.Media3CustomOutputUnavailable in snapshot.proOutput.blockers)
    }

    @Test
    fun highSampleRateWithoutBitDepthIsLikelyUntilDecodedPcmIsKnown() {
        val snapshot = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  96 kHz  stereo"),
            diagnostics = OutputDiagnosticsSnapshot(
                routeStatuses = verifiedRouteStatuses(OutputDeviceType.WiredHeadsetAux, OutputPluginType.HiRes),
                outputDevice = verifiedDevice(OutputDeviceType.WiredHeadsetAux),
                audioTrack = AudioTrackDiagnosticsSnapshot(
                    configuredSampleRateHz = 96_000,
                    encoding = "PCM24",
                    channelCount = 2,
                    updatedAtMillis = 9L,
                ),
                proOutput = proRuntime(ProOutputRuntimeStatus.Active),
                updatedAtMillis = 9L,
            ),
        )

        assertEquals(ProOutputHiResSourceStatus.LikelyHiRes, snapshot.proOutput.sourceEligibility.hiResStatus)
        assertFalse(snapshot.proOutput.active)
        assertTrue(ProOutputBlocker.DecodeFormatUnknown in snapshot.proOutput.blockers)
    }

    @Test
    fun pcm16FortyEightOutputCannotVerifyHiResActiveForTwentyFourNinetySixSource() {
        val snapshot = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = pcmRuntimeDiagnostics(sampleRateHz = 48_000, encoding = "PCM16"),
        )

        assertFalse(snapshot.outputCapability.hiResActive)
        assertFalse(snapshot.proOutput.active)
        assertTrue(ProOutputBlocker.Media3CustomOutputUnavailable !in snapshot.proOutput.blockers)
    }

    @Test
    fun outputUnknownKeepsHiResNotVerified() {
        val snapshot = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = OutputDiagnosticsSnapshot(),
        )

        assertFalse(snapshot.proOutput.active)
        assertTrue(ProOutputBlocker.OutputRouteUnknown in snapshot.proOutput.blockers)
        assertTrue(ProOutputBlocker.OutputSampleRateUnknown in snapshot.proOutput.blockers)
        assertTrue(ProOutputBlocker.OutputEncodingUnknown in snapshot.proOutput.blockers)
    }

    @Test
    fun customProviderFallbackAndFailureStaySafeAndVisible() {
        val fallback = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = hiResRuntimeDiagnostics(
                customStatus = ProOutputRuntimeStatus.FallbackToMedia3,
                fallbackReason = "Pro output is local/offline only.",
            ),
        )
        val failed = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = hiResRuntimeDiagnostics(
                customStatus = ProOutputRuntimeStatus.Failed,
                fallbackReason = "Output initialization failed; using Media3 fallback.",
            ),
        )

        assertFalse(fallback.proOutput.active)
        assertFalse(failed.proOutput.active)
        assertTrue(ProOutputBlocker.Media3CustomOutputUnavailable in fallback.proOutput.blockers)
        assertTrue(ProOutputBlocker.Media3CustomOutputUnavailable in failed.proOutput.blockers)
        assertTrue(failed.proOutput.fallbackReason?.contains("fallback", ignoreCase = true) == true)
    }

    @Test
    fun bitPerfectActiveRequiresNeutralProcessingAndMatchingRuntimeEvidence() {
        val neutral = snapshot(
            state = AudioEngineState(enabled = false, output = OutputSettings(bitPerfectAttemptEnabled = true)),
            track = localTrack("FLAC  16-bit  44.1 kHz  stereo"),
            diagnostics = bitPerfectDiagnostics(),
        )
        val dsp = snapshot(
            state = AudioEngineState(output = OutputSettings(bitPerfectAttemptEnabled = true)).withGraphicEqBandGain(5, 4f),
            track = localTrack("FLAC  16-bit  44.1 kHz  stereo"),
            diagnostics = bitPerfectDiagnostics(),
        )
        val replayGain = snapshot(
            state = AudioEngineState(
                replayGainMode = ReplayGainMode.Track,
                replayGainPreampDb = 1f,
                output = OutputSettings(bitPerfectAttemptEnabled = true),
            ),
            track = localTrack("FLAC  16-bit  44.1 kHz  stereo"),
            diagnostics = bitPerfectDiagnostics(),
        )

        assertTrue(neutral.bitPerfect.active)
        assertTrue(neutral.proOutput.active)
        assertFalse(dsp.bitPerfect.active)
        assertTrue(BitPerfectConflictType.EqActive in dsp.bitPerfect.conflicts.map { it.type })
        assertFalse(replayGain.bitPerfect.active)
        assertTrue(BitPerfectConflictType.ReplayGainActive in replayGain.bitPerfect.conflicts.map { it.type })
    }

    @Test
    fun bitPerfectBlocksOutputTrimBluetoothAndSampleRateMismatch() {
        val trim = snapshot(
            state = AudioEngineState(
                enabled = true,
                preampDb = 1f,
                output = OutputSettings(bitPerfectAttemptEnabled = true),
            ),
            track = localTrack("FLAC  16-bit  44.1 kHz  stereo"),
            diagnostics = bitPerfectDiagnostics(),
        )
        val bluetooth = snapshot(
            state = AudioEngineState(enabled = false, output = OutputSettings(deviceProfile = DeviceProfile.Bluetooth, bitPerfectAttemptEnabled = true)),
            track = localTrack("FLAC  16-bit  44.1 kHz  stereo"),
            diagnostics = bitPerfectDiagnostics(route = OutputDeviceType.Bluetooth),
        )
        val mismatch = snapshot(
            state = AudioEngineState(enabled = false, output = OutputSettings(bitPerfectAttemptEnabled = true)),
            track = localTrack("FLAC  16-bit  44.1 kHz  stereo"),
            diagnostics = bitPerfectDiagnostics(outputRate = 48_000),
        )

        assertTrue(BitPerfectConflictType.OutputTrimActive in trim.bitPerfect.conflicts.map { it.type })
        assertTrue(BitPerfectConflictType.BluetoothRoute in bluetooth.bitPerfect.conflicts.map { it.type })
        assertTrue(BitPerfectConflictType.AudioTrackMismatch in mismatch.bitPerfect.conflicts.map { it.type })
        assertFalse(trim.bitPerfect.active)
        assertFalse(bluetooth.bitPerfect.active)
        assertFalse(mismatch.bitPerfect.active)
    }

    @Test
    fun usbDacStatusRequiresUsbRouteAndOutputEvidence() {
        val notDetected = snapshot(
            state = AudioEngineState(enabled = false, output = OutputSettings(deviceProfile = DeviceProfile.UsbDac)),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = hiResRuntimeDiagnostics(route = OutputDeviceType.WiredHeadsetAux, usbDetected = false),
        )
        val detected = snapshot(
            state = AudioEngineState(enabled = false, output = OutputSettings(deviceProfile = DeviceProfile.UsbDac)),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = hiResRuntimeDiagnostics(route = OutputDeviceType.UsbDac, usbDetected = true),
        )

        assertFalse(notDetected.outputCapability.usbDacActive)
        assertTrue(ProOutputBlocker.UsbDacNotDetected in notDetected.proOutput.blockers)
        assertTrue(detected.outputCapability.usbDacActive)
        assertTrue(detected.proOutput.routeEligibility.usbDacActive)
    }

    @Test
    fun attemptModeDoesNotEraseSavedSettingsAndPrivateTextStaysOutOfInspector() {
        val original = AudioEngineState(
            replayGainMode = ReplayGainMode.Album,
            replayGainPreampDb = 2f,
            eqBands = flatEqBands().mapIndexed { index, band -> if (index == 5) band.copy(gainDb = 3f) else band },
            bassDb = 2f,
            output = OutputSettings(mode = OutputMode.HiRes),
        )
        val track = localTrack(
            quality = "FLAC  24-bit  96 kHz  stereo  https://googlevideo.example/signature/private  C:\\Users\\abiys\\Music\\secret.flac",
        )
        val snapshot = snapshot(original, track, hiResRuntimeDiagnostics(customStatus = ProOutputRuntimeStatus.FallbackToMedia3))
        val visible = listOf(
            snapshot.source.detail,
            snapshot.source.format,
            snapshot.proOutput.sourceEligibility.reason,
            snapshot.proOutput.fallbackReason.orEmpty(),
        ).joinToString(" ")

        assertEquals(ReplayGainMode.Album, original.replayGainMode)
        assertTrue(original.eqStageActive)
        assertTrue(original.toneControlsActive)
        assertFalse(visible.contains("googlevideo", ignoreCase = true))
        assertFalse(visible.contains("signature", ignoreCase = true))
        assertFalse(visible.contains("C:\\Users", ignoreCase = true))
    }

    @Test
    fun releasedRouteAndUnderrunsPreventActiveClaim() {
        val released = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = hiResRuntimeDiagnostics(customStatus = ProOutputRuntimeStatus.Released),
        )
        val underrun = snapshot(
            state = hiResCleanState(),
            track = localTrack("FLAC  24-bit  96 kHz  stereo"),
            diagnostics = hiResRuntimeDiagnostics(customStatus = ProOutputRuntimeStatus.Active, underruns = 2),
        )

        assertFalse(released.proOutput.active)
        assertTrue(ProOutputBlocker.Media3CustomOutputUnavailable in released.proOutput.blockers)
        assertFalse(underrun.proOutput.active)
        assertTrue(ProOutputBlocker.UnderrunRisk in underrun.proOutput.blockers)
    }

    private fun hiResCleanState(): AudioEngineState =
        AudioEngineState(
            enabled = false,
            output = OutputSettings(
                mode = OutputMode.HiRes,
                deviceProfile = DeviceProfile.Wired,
                sampleRate = OutputSampleRate.Hz96000,
                bitDepth = OutputBitDepth.Bit24,
            ),
        )

    private fun snapshot(
        state: AudioEngineState,
        track: Track,
        diagnostics: OutputDiagnosticsSnapshot,
    ): AudioChainSnapshot =
        audioChainSnapshot(
            state = state,
            track = track,
            quality = track.quality,
            diagnostics = diagnostics,
            nativeAvailable = true,
        )

    private fun localTrack(quality: String): Track =
        Track(
            title = "Reference",
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
            mimeType = "audio/flac",
            folderPath = "Music/HiRes",
        )

    private fun hiResRuntimeDiagnostics(
        route: OutputDeviceType = OutputDeviceType.WiredHeadsetAux,
        customStatus: ProOutputRuntimeStatus = ProOutputRuntimeStatus.Active,
        fallbackReason: String? = null,
        usbDetected: Boolean = route == OutputDeviceType.UsbDac,
        underruns: Int = 0,
    ): OutputDiagnosticsSnapshot =
        OutputDiagnosticsSnapshot(
            routeStatuses = verifiedRouteStatuses(route, OutputPluginType.HiRes),
            outputDevice = verifiedDevice(route, usbDetected),
            audioTrack = AudioTrackDiagnosticsSnapshot(
                configuredSampleRateHz = 96_000,
                encoding = "PCM24",
                channelCount = 2,
                bufferSizeBytes = 192_000,
                underrunCount = underruns,
                updatedAtMillis = 10L,
            ),
            proOutput = proRuntime(customStatus, fallbackReason = fallbackReason, underruns = underruns),
            decoderFormat = DecoderFormatDiagnosticsSnapshot(
                sampleMimeType = "audio/flac",
                encodedSampleRateHz = 96_000,
                encodedChannelCount = 2,
                decodedPcmEncoding = "PCM24",
                decodedSampleRateHz = 96_000,
                decodedChannelCount = 2,
                updatedAtMillis = 10L,
            ),
            updatedAtMillis = 10L,
        )

    private fun pcmRuntimeDiagnostics(sampleRateHz: Int, encoding: String): OutputDiagnosticsSnapshot =
        OutputDiagnosticsSnapshot(
            routeStatuses = verifiedRouteStatuses(OutputDeviceType.WiredHeadsetAux, OutputPluginType.HiRes),
            outputDevice = verifiedDevice(OutputDeviceType.WiredHeadsetAux),
            audioTrack = AudioTrackDiagnosticsSnapshot(
                configuredSampleRateHz = sampleRateHz,
                encoding = encoding,
                channelCount = 2,
                updatedAtMillis = 11L,
            ),
            proOutput = proRuntime(ProOutputRuntimeStatus.Started, actualRate = sampleRateHz, actualEncoding = encoding),
            decoderFormat = DecoderFormatDiagnosticsSnapshot(
                sampleMimeType = "audio/flac",
                encodedSampleRateHz = sampleRateHz,
                encodedChannelCount = 2,
                decodedPcmEncoding = encoding,
                decodedSampleRateHz = sampleRateHz,
                decodedChannelCount = 2,
                updatedAtMillis = 11L,
            ),
            updatedAtMillis = 11L,
        )

    private fun media3DefaultRuntimeDiagnostics(sampleRateHz: Int, encoding: String): OutputDiagnosticsSnapshot =
        OutputDiagnosticsSnapshot(
            routeStatuses = verifiedRouteStatuses(OutputDeviceType.WiredHeadsetAux, OutputPluginType.Media3AudioTrack),
            outputDevice = verifiedDevice(OutputDeviceType.WiredHeadsetAux),
            audioTrack = AudioTrackDiagnosticsSnapshot(
                configuredSampleRateHz = sampleRateHz,
                encoding = encoding,
                channelCount = 2,
                underrunCount = 0,
                updatedAtMillis = 13L,
            ),
            proOutput = ProOutputRuntimeSnapshot(
                providerInstalled = true,
                enabledByUser = false,
                localOfflineEligible = false,
                sourceScope = "local",
                engine = ProOutputRuntimeEngine.Media3Default,
                status = ProOutputRuntimeStatus.Started,
                requestedSampleRateHz = sampleRateHz,
                actualSampleRateHz = sampleRateHz,
                requestedEncoding = encoding,
                actualEncoding = encoding,
                channelCount = 2,
                underrunCount = 0,
                updatedAtMillis = 13L,
            ),
            decoderFormat = DecoderFormatDiagnosticsSnapshot(
                sampleMimeType = "audio/raw",
                encodedSampleRateHz = sampleRateHz,
                encodedChannelCount = 2,
                decodedPcmEncoding = encoding,
                decodedSampleRateHz = sampleRateHz,
                decodedChannelCount = 2,
                updatedAtMillis = 13L,
            ),
            updatedAtMillis = 13L,
        )

    private fun bitPerfectDiagnostics(
        route: OutputDeviceType = OutputDeviceType.Speaker,
        outputRate: Int = 44_100,
    ): OutputDiagnosticsSnapshot =
        OutputDiagnosticsSnapshot(
            routeStatuses = verifiedRouteStatuses(route, OutputPluginType.Media3AudioTrack),
            outputDevice = verifiedDevice(route),
            audioTrack = AudioTrackDiagnosticsSnapshot(
                configuredSampleRateHz = outputRate,
                encoding = "PCM16",
                channelCount = 2,
                updatedAtMillis = 12L,
            ),
            proOutput = ProOutputRuntimeSnapshot(
                providerInstalled = true,
                enabledByUser = true,
                localOfflineEligible = true,
                sourceScope = "local",
                engine = ProOutputRuntimeEngine.Media3CustomOutput,
                status = ProOutputRuntimeStatus.Started,
                actualSampleRateHz = outputRate,
                actualEncoding = "PCM16",
                channelCount = 2,
                updatedAtMillis = 12L,
            ),
            decoderFormat = DecoderFormatDiagnosticsSnapshot(
                sampleMimeType = "audio/flac",
                encodedSampleRateHz = 44_100,
                encodedChannelCount = 2,
                decodedPcmEncoding = "PCM16",
                decodedSampleRateHz = 44_100,
                decodedChannelCount = 2,
                updatedAtMillis = 12L,
            ),
            updatedAtMillis = 12L,
        )

    private fun verifiedRouteStatuses(route: OutputDeviceType, plugin: OutputPluginType): List<OutputRouteStatus> =
        listOf(
            OutputRouteStatus(
                route = route,
                requestedPlugin = plugin,
                actualPlugin = plugin,
                available = true,
            ),
        )

    private fun verifiedDevice(route: OutputDeviceType, usbDetected: Boolean = route == OutputDeviceType.UsbDac): OutputDeviceDiagnosticsSnapshot =
        OutputDeviceDiagnosticsSnapshot(
            requestedRoute = route,
            activeRoute = route,
            routeVerified = true,
            usbDacDetected = usbDetected,
            supportedSampleRatesHz = listOf(44_100, 48_000, 96_000),
            supportedEncodings = listOf("PCM16", "PCM24"),
        )

    private fun proRuntime(
        status: ProOutputRuntimeStatus,
        fallbackReason: String? = null,
        actualRate: Int = 96_000,
        actualEncoding: String = "PCM24",
        underruns: Int = 0,
    ): ProOutputRuntimeSnapshot =
        ProOutputRuntimeSnapshot(
            providerInstalled = true,
            enabledByUser = true,
            localOfflineEligible = true,
            sourceScope = "local",
            engine = if (status == ProOutputRuntimeStatus.FallbackToMedia3 || status == ProOutputRuntimeStatus.Failed) {
                ProOutputRuntimeEngine.Media3Default
            } else {
                ProOutputRuntimeEngine.Media3CustomOutput
            },
            status = status,
            requestedSampleRateHz = actualRate,
            actualSampleRateHz = actualRate,
            requestedEncoding = actualEncoding,
            actualEncoding = actualEncoding,
            channelCount = 2,
            bufferSizeBytes = 192_000,
            underrunCount = underruns,
            fallbackReason = fallbackReason,
            updatedAtMillis = 10L,
        )
}
