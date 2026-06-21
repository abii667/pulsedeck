package com.pulsedeck.app

import com.pulsedeck.app.settings.runtime.OutputDeviceDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProListeningExperiencesTest {
    @Test
    fun cleanBassPreservesOutputPolicyAndUsesCanonicalEqModels() {
        val original = AudioEngineState(
            output = OutputSettings(
                mode = OutputMode.HiRes,
                deviceProfile = DeviceProfile.Bluetooth,
                hiResEnabled = true,
                sampleRate = OutputSampleRate.Hz96000,
                bitDepth = OutputBitDepth.Float32,
                bitPerfectAttemptEnabled = true,
            ),
            resampler = ResamplerSettings(mode = ResamplerMode.System, outputSampleRate = OutputSampleRate.Hz96000),
            dvc = DirectVolumeControlSettings(enabled = true, bluetoothEnabled = true),
            crossfade = CrossfadeSettings(crossfadeEnabled = true, durationMs = 4500),
        )

        val plan = proListeningExperiencePlan(original, ProListeningExperienceId.CleanBass)
        val preview = plan.previewState

        assertEquals(original.output, preview.output)
        assertEquals(original.resampler, preview.resampler)
        assertEquals(original.dvc, preview.dvc)
        assertEquals(original.crossfade, preview.crossfade)
        assertEquals(GRAPHIC_EQ_BAND_COUNT, preview.eqBands.size)
        assertEquals(graphicEqCenterFrequenciesHz(), preview.eqBands.map { it.frequencyHz })
        assertEquals(PARAMETRIC_EQ_SLOT_COUNT, preview.parametricEq.size)
        assertTrue(preview.parametricEq.all { it.frequencyHz in PARAMETRIC_EQ_MIN_FREQUENCY_HZ..PARAMETRIC_EQ_MAX_FREQUENCY_HZ })
        assertTrue(preview.parametricEq.all { it.q in PARAMETRIC_EQ_MIN_Q..PARAMETRIC_EQ_MAX_Q })
        assertTrue(preview.preventClipping)
        assertTrue(preview.native.media3DspEnabled)
    }

    @Test
    fun previewPlanKeepsOriginalStateForImmediateRevert() {
        val original = AudioEngineState(
            preampDb = -1f,
            replayGainMode = ReplayGainMode.Album,
            output = OutputSettings(deviceProfile = DeviceProfile.Wired),
        )

        val plan = proListeningExperiencePlan(original, ProListeningExperienceId.HeadphoneSpace)

        assertNotEquals(original, plan.previewState)
        assertEquals(original, plan.originalState)
        assertEquals(original, revertProListeningExperience(plan))
    }

    @Test
    fun bluetoothRouteAddsCompatibilityWarningsAndNoActiveClaims() {
        val original = AudioEngineState(
            output = OutputSettings(
                deviceProfile = DeviceProfile.Bluetooth,
                bitPerfectAttemptEnabled = true,
                hiResEnabled = true,
            ),
        )
        val diagnostics = OutputDiagnosticsSnapshot(
            outputDevice = OutputDeviceDiagnosticsSnapshot(
                activeRoute = OutputDeviceType.Bluetooth,
                routeVerified = true,
            ),
            bitPerfectAttemptEnabled = true,
        )

        val plan = proListeningExperiencePlan(original, ProListeningExperienceId.CleanBass, diagnostics)

        assertTrue(plan.routeWarnings.any { it.contains("Bluetooth", ignoreCase = true) })
        assertTrue(plan.compatibilityNotes.any { it.contains("Bit-perfect attempt will report blockers", ignoreCase = true) })
        assertTrue(plan.compatibilityNotes.any { it.contains("Hi-res status remains runtime-evidence based", ignoreCase = true) })
        assertFalse((plan.routeWarnings + plan.compatibilityNotes).any { it.contains("active", ignoreCase = true) && it.contains("bit-perfect", ignoreCase = true) })
    }

    @Test
    fun studioReferenceClearsCreativeColorationWithoutChangingOutputRoute() {
        val original = AudioEngineState(
            reverb = ReverbState(enabled = true, mix = 0.4f),
            delay = DelayState(enabled = true, mix = 0.2f),
            modulation = ModulationState(chorusEnabled = true, mix = 0.2f),
            compressor = CompressorState(enabled = true, mix = 0.5f),
            output = OutputSettings(mode = OutputMode.HiRes, deviceProfile = DeviceProfile.UsbDac, hiResEnabled = true),
            native = NativeAudioSettings(enabled = true, media3DspEnabled = true),
        )

        val preview = applyProListeningExperience(original, ProListeningExperienceId.StudioReference)

        assertEquals(original.output, preview.output)
        assertTrue(preview.eqBands.all { it.gainDb == 0f })
        assertTrue(preview.parametricEq.none { it.isActiveFilter() })
        assertEquals(0f, preview.bassDb, 0.001f)
        assertEquals(0f, preview.trebleDb, 0.001f)
        assertEquals(0f, preview.vocalClarityDb, 0.001f)
        assertEquals(ReverbState(), preview.reverb)
        assertEquals(DelayState(), preview.delay)
        assertEquals(ModulationState(), preview.modulation)
        assertFalse(preview.compressorStageActive)
        assertFalse(preview.gateStageActive)
        assertFalse(preview.native.media3DspEnabled)
        assertEquals(0f, preview.maxBoostDb, 0.001f)
        assertEquals(ProListeningExperienceId.StudioReference, activeProListeningExperience(preview)?.id)
    }

    @Test
    fun recommendationIsRouteAware() {
        assertEquals(
            ProListeningExperienceId.CleanBass,
            recommendedProListeningExperience(AudioEngineState(output = OutputSettings(deviceProfile = DeviceProfile.Bluetooth))).experience.id,
        )
        assertEquals(
            ProListeningExperienceId.RoadEnergy,
            recommendedProListeningExperience(AudioEngineState(output = OutputSettings(deviceProfile = DeviceProfile.Car))).experience.id,
        )
        assertEquals(
            ProListeningExperienceId.HeadphoneSpace,
            recommendedProListeningExperience(AudioEngineState(output = OutputSettings(deviceProfile = DeviceProfile.Wired))).experience.id,
        )
        assertEquals(
            ProListeningExperienceId.StudioReference,
            recommendedProListeningExperience(AudioEngineState(output = OutputSettings(deviceProfile = DeviceProfile.UsbDac))).experience.id,
        )
        assertEquals(
            ProListeningExperienceId.VocalFocus,
            recommendedProListeningExperience(AudioEngineState(output = OutputSettings(deviceProfile = DeviceProfile.Speaker))).experience.id,
        )
    }

    @Test
    fun allExperiencesRemainCanonicalAndDoNotEnableCreativeEffects() {
        val original = AudioEngineState(
            output = OutputSettings(mode = OutputMode.AudioTrack, deviceProfile = DeviceProfile.Wired),
        )

        proListeningExperiences.forEach { experience ->
            val preview = applyProListeningExperience(original, experience.id)

            assertEquals(original.output, preview.output)
            assertEquals(GRAPHIC_EQ_BAND_COUNT, preview.eqBands.size)
            assertEquals(PARAMETRIC_EQ_SLOT_COUNT, preview.parametricEq.size)
            assertTrue(preview.eqBands.all { it.gainDb in AUDIO_EQ_MIN_GAIN_DB..AUDIO_EQ_MAX_GAIN_DB })
            assertTrue(preview.parametricEq.all { it.gainDb in AUDIO_EQ_MIN_GAIN_DB..AUDIO_EQ_MAX_GAIN_DB })
            assertEquals(ReverbState(), preview.reverb)
            assertEquals(DelayState(), preview.delay)
            assertEquals(ModulationState(), preview.modulation)
            assertEquals(TempoPitchState(), preview.tempoPitch)
        }
    }
}
