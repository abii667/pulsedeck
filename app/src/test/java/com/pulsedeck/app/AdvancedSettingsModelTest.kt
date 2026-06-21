@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsedeck.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.pulsedeck.app.audio.NativeAudioEngineController
import com.pulsedeck.app.audio.NativeDspAudioProcessor
import com.pulsedeck.app.audio.nativeAudioActivationFor
import com.pulsedeck.app.settings.model.HeadsetBluetoothSettings
import com.pulsedeck.app.settings.model.AlbumArtQuality
import com.pulsedeck.app.settings.model.AlbumArtSettings
import com.pulsedeck.app.settings.model.AlbumArtworkProviderId
import com.pulsedeck.app.settings.model.DefaultAlbumArtworkProviderOrder
import com.pulsedeck.app.settings.model.LookAndFeelSettings
import com.pulsedeck.app.settings.model.MediaAction
import com.pulsedeck.app.settings.model.PulseSettingsState
import com.pulsedeck.app.settings.model.ScreenOrientationSetting
import com.pulsedeck.app.settings.model.SettingKey
import com.pulsedeck.app.settings.model.SettingsFont
import com.pulsedeck.app.settings.model.SettingsTheme
import com.pulsedeck.app.settings.model.pulseSettingsCatalog
import com.pulsedeck.app.settings.data.SettingsProfileCodec
import com.pulsedeck.app.settings.runtime.CommandCueSource
import com.pulsedeck.app.settings.runtime.commandCueVolumePercent
import com.pulsedeck.app.settings.runtime.commandCuesForKeyCommand
import com.pulsedeck.app.settings.runtime.commandCuesForMediaAction
import com.pulsedeck.app.settings.runtime.rejectedCommandCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSettingsModelTest {
    @Test
    fun audioSettingsNormalizeUnsafeRanges() {
        val normalized = AudioEngineState(
            preampDb = 18f,
            replayGainMode = ReplayGainMode.Track,
            replayGainPreampDb = 18f,
            preventClipping = false,
            compressor = CompressorState(thresholdDb = 8f, ratio = 40f, attackMs = -4f, releaseMs = 3000f, makeupDb = 30f, mix = 2f),
            gate = GateState(thresholdDb = 8f, attackMs = -1f, holdMs = 2000f, releaseMs = 3000f),
            delay = DelayState(timeMs = 4000f, feedback = 2f, mix = 2f),
            modulation = ModulationState(rateHz = 30f, depth = 2f, feedback = 2f, mix = 2f),
            native = NativeAudioSettings(masterGain = 3f, sourceFrequencyHz = 12_000f, sourceGain = 2f, exportDurationMs = 100_000),
            crossfade = CrossfadeSettings(durationMs = 24_000),
            audioFocus = AudioFocusSettings(duckVolume = 2f, duckFadeMs = 20),
            dvc = DirectVolumeControlSettings(headroomDb = 3f),
        ).normalized()

        assertEquals(12f, normalized.preampDb, 0.001f)
        assertEquals(12f, normalized.replayGainPreampDb, 0.001f)
        assertEquals(12_000, normalized.crossfade.durationMs)
        assertEquals(1f, normalized.audioFocus.duckVolume, 0.001f)
        assertEquals(50, normalized.audioFocus.duckFadeMs)
        assertEquals(0f, normalized.dvc.headroomDb, 0.001f)
        assertEquals(ReplayGainMode.Track, normalized.replayGain.mode)
        assertFalse(normalized.replayGain.preventClipping)
        assertEquals(0f, normalized.compressor.thresholdDb, 0.001f)
        assertEquals(20f, normalized.compressor.ratio, 0.001f)
        assertEquals(1f, normalized.compressor.mix, 0.001f)
        assertEquals(0.92f, normalized.delay.feedback, 0.001f)
        assertEquals(8f, normalized.modulation.rateHz, 0.001f)
        assertEquals(1.5f, normalized.native.masterGain, 0.001f)
        assertEquals(4000f, normalized.native.sourceFrequencyHz, 0.001f)
        assertEquals(60_000, normalized.native.exportDurationMs)
    }

    @Test
    fun eqNormalizationKeepsCanonicalGraphicAndParametricShape() {
        val normalized = AudioEngineState(
            eqBands = listOf(
                GraphicEqBand(frequencyHz = 10f, gainDb = 30f),
                GraphicEqBand(frequencyHz = 12345f, gainDb = -30f),
            ),
            parametricEq = listOf(
                ParametricEqBand(enabled = true, frequencyHz = 5f, gainDb = 30f, q = 99f),
                ParametricEqBand(enabled = true, frequencyHz = 40_000f, gainDb = -30f, q = 0.01f),
                ParametricEqBand(enabled = true, frequencyHz = 500f, gainDb = 3f, q = 1.5f),
                ParametricEqBand(enabled = true, frequencyHz = 1_000f, gainDb = 4f, q = 2f),
                ParametricEqBand(enabled = true, frequencyHz = 2_000f, gainDb = 5f, q = 3f),
            ),
        ).normalized()

        assertEquals(graphicEqCenterFrequenciesHz(), normalized.eqBands.map { it.frequencyHz })
        assertEquals(10, normalized.eqBands.size)
        assertEquals(12f, normalized.eqBands[0].gainDb, 0.001f)
        assertEquals(-12f, normalized.eqBands[1].gainDb, 0.001f)
        assertEquals(0f, normalized.eqBands[2].gainDb, 0.001f)
        assertEquals(4, normalized.parametricEq.size)
        assertEquals(20f, normalized.parametricEq[0].frequencyHz, 0.001f)
        assertEquals(12f, normalized.parametricEq[0].gainDb, 0.001f)
        assertEquals(18f, normalized.parametricEq[0].q, 0.001f)
        assertEquals(20_000f, normalized.parametricEq[1].frequencyHz, 0.001f)
        assertEquals(-12f, normalized.parametricEq[1].gainDb, 0.001f)
        assertEquals(0.1f, normalized.parametricEq[1].q, 0.001f)
    }

    @Test
    fun graphicEqExtendedRangeIsPersistedButNativeOwnerGated() {
        val extended = AudioEngineState(
            graphicEqRangeMode = GraphicEqRangeMode.Extended15DbWhenSupported,
        ).withGraphicEqBandGain(5, 14.96f)

        assertEquals(GraphicEqRangeMode.Extended15DbWhenSupported, extended.graphicEqRangeMode)
        assertEquals(15f, extended.eqBands[5].gainDb, 0.001f)
        assertEquals(
            12f,
            audioBandGainDb(1_000f, extended, extended.effectiveGraphicEqGainRange(nativeMedia3DspActive = false)),
            0.001f,
        )
        assertEquals(
            15f,
            audioBandGainDb(1_000f, extended, extended.effectiveGraphicEqGainRange(nativeMedia3DspActive = true)),
            0.001f,
        )

        val standard = extended.withGraphicEqRangeMode(GraphicEqRangeMode.Standard12Db)
        assertEquals(GraphicEqRangeMode.Standard12Db, standard.graphicEqRangeMode)
        assertEquals(12f, standard.eqBands[5].gainDb, 0.001f)
    }

    @Test
    fun graphicEqInputSnapsToTenthDb() {
        val state = AudioEngineState(
            graphicEqRangeMode = GraphicEqRangeMode.Extended15DbWhenSupported,
        ).withGraphicEqBandGain(0, -0.04f)

        assertEquals(0f, state.eqBands[0].gainDb, 0.001f)
        assertEquals(239, audioDbSliderSteps(standardGraphicEqGainRange()))
        assertEquals(299, audioDbSliderSteps(extendedGraphicEqGainRange()))
    }

    @Test
    fun nativeMasterGainConstraintReportsVerifiedCeiling() {
        val state = AudioEngineState(
            preampDb = 12f,
            preventClipping = false,
            native = NativeAudioSettings(enabled = true, media3DspEnabled = true),
        )
        val constraint = nativeMasterGainConstraint(state, externalVolume = 1f)

        assertTrue(constraint.constrained)
        assertTrue(constraint.requestedLinear > NATIVE_MASTER_GAIN_MAX_LINEAR)
        assertEquals(NATIVE_MASTER_GAIN_MAX_LINEAR, constraint.effectiveLinear, 0.001f)
        assertEquals(linearToDb(NATIVE_MASTER_GAIN_MAX_LINEAR), constraint.effectiveDb, 0.001f)
    }

    @Test
    fun presetApplicationClearsStaleEqColorationAndKeepsIdentity() {
        val dirty = AudioEngineState(
            bypass = true,
            preampDb = 7f,
            eqBands = flatEqBands().map { it.copy(gainDb = 6f) },
            parametricEq = defaultParametricBands().map { it.copy(enabled = true, gainDb = 5f) },
            bassDb = 4f,
            trebleDb = 3f,
            vocalClarityDb = 2f,
            loudnessDb = 6f,
            activePresetId = "flat",
            presetModified = true,
        )
        val preset = pulseAudioPresets.first { it.id == "vocal_clarity" }
        val applied = applyPreset(dirty, preset)

        assertTrue(applied.enabled)
        assertFalse(applied.bypass)
        assertEquals(preset.id, applied.activePresetId)
        assertFalse(applied.presetModified)
        assertEquals(preset.preampDb, applied.preampDb, 0.001f)
        assertEquals(preset.eqBands.map { it.gainDb }, applied.eqBands.map { it.gainDb })
        assertFalse(applied.parametricEq.any { it.enabled })
        assertEquals(0f, applied.bassDb, 0.001f)
        assertEquals(0f, applied.trebleDb, 0.001f)
        assertEquals(0f, applied.vocalClarityDb, 0.001f)
        assertEquals(0f, applied.loudnessDb, 0.001f)
        assertTrue(audioOutputGain(applied, 1f) <= 1f)
    }

    @Test
    fun flatPresetRemainsTransparentAfterDirtyEqState() {
        val dirty = AudioEngineState(
            preampDb = 6f,
            eqBands = flatEqBands().map { it.copy(gainDb = 6f) },
            parametricEq = defaultParametricBands().map { it.copy(enabled = true, gainDb = 6f) },
            bassDb = 6f,
            trebleDb = 6f,
            vocalClarityDb = 6f,
            loudnessDb = 6f,
        )
        val flat = applyPreset(dirty, pulseAudioPresets.first { it.id == "flat" })
        val policy = flat.effectiveProcessingPolicy(nativeAvailable = true)

        assertFalse(flat.eqStageActive)
        assertFalse(flat.toneControlsActive)
        assertFalse(flat.loudnessStageActive)
        assertTrue(policy.transparentPathExpected)
        assertFalse(policy.platformEffectsAllowed)
        assertEquals(1f, audioOutputGain(flat, 1f), 0.001f)
    }

    @Test
    fun replayGainHelperKeepsLegacyAndNestedStateAligned() {
        val off = AudioEngineState().withReplayGainSettings(
            ReplayGainSettings(enabled = false, mode = ReplayGainMode.Smart, trackPreampDb = 8f),
        )

        assertEquals(ReplayGainMode.Off, off.replayGainMode)
        assertEquals(ReplayGainMode.Off, off.replayGain.mode)
        assertFalse(off.replayGain.enabled)

        val album = off.withReplayGainSettings(
            ReplayGainSettings(enabled = true, mode = ReplayGainMode.Album, trackPreampDb = 2f, preventClipping = false),
        )

        assertEquals(ReplayGainMode.Album, album.replayGainMode)
        assertEquals(ReplayGainMode.Album, album.replayGain.mode)
        assertEquals(2f, album.replayGainPreampDb, 0.001f)
        assertFalse(album.preventClipping)
    }

    @Test
    fun toneControlsContributeToEffectiveEqBandGains() {
        val state = AudioEngineState(
            eqEnabled = false,
            bassDb = 4f,
            trebleDb = 6f,
            vocalClarityDb = 3f,
        )

        assertTrue(state.toneControlsActive)
        assertEquals(4f, audioBandGainDb(62f, state), 0.001f)
        assertEquals(3f, audioBandGainDb(1_000f, state), 0.001f)
        assertEquals(6f, audioBandGainDb(16_000f, state), 0.001f)
        assertTrue(state.spectralBoostDb >= 6f)
    }

    @Test
    fun clippingProtectionCreatesHeadroomForStackedBoosts() {
        val boosted = AudioEngineState(
            preampDb = 6f,
            eqBands = flatEqBands().map { it.copy(gainDb = 6f) },
        )

        assertTrue(boosted.clippingRisk)
        assertEquals(dbToLinear(-6f), audioOutputGain(boosted, 1f), 0.001f)

        val compensated = boosted.copy(preampDb = -6f)
        assertFalse(compensated.clippingRisk)
        assertEquals(dbToLinear(-6f), audioOutputGain(compensated, 1f), 0.001f)
    }

    @Test
    fun flatDefaultPolicyKeepsTransparentPathAndPlatformEffectsOff() {
        val state = AudioEngineState()
        val policy = state.effectiveProcessingPolicy(nativeAvailable = true)

        assertTrue(policy.engineEnabled)
        assertTrue(policy.processingActive)
        assertTrue(policy.transparentPathExpected)
        assertFalse(policy.eqStageActive)
        assertFalse(policy.platformEqAllowed)
        assertFalse(policy.platformEffectsAllowed)
        assertFalse(policy.nativeMedia3DspRequested)
        assertFalse(policy.preventClippingActive)
        assertEquals(0f, policy.estimatedHeadroomDb, 0.001f)
        assertEquals(1f, audioOutputGain(state, 1f), 0.001f)
    }

    @Test
    fun engineOffBypassAndSafeModeKeepOutputGainNeutral() {
        val boosted = AudioEngineState(
            preampDb = 6f,
            eqBands = flatEqBands().map { it.copy(gainDb = 6f) },
        )

        listOf(
            boosted.copy(enabled = false, bypass = false),
            boosted.copy(bypass = true),
            boosted.copy(advancedTweaks = AdvancedAudioTweaksSettings(safeMode = true)),
        ).forEach { state ->
            val policy = state.effectiveProcessingPolicy(nativeAvailable = true)

            assertFalse(policy.processingActive)
            assertTrue(policy.transparentPathExpected)
            assertFalse(policy.platformEffectsAllowed)
            assertFalse(policy.nativeMedia3DspActive)
            assertEquals(1f, audioOutputGain(state, 1f), 0.001f)
        }
    }

    @Test
    fun fullGraphHeadroomIncludesDynamicsStereoAndTimeEffects() {
        val state = AudioEngineState(
            compressor = CompressorState(enabled = true, makeupDb = 5f, mix = 1f),
            stereo = StereoState(stereoWidth = 1f),
            delay = DelayState(enabled = true, feedback = 0.5f, mix = 0.5f),
            reverb = ReverbState(enabled = true, mix = 0.4f, size = 0.7f, decay = 3f),
        )
        val policy = state.effectiveProcessingPolicy(nativeAvailable = true)

        assertTrue(state.maxBoostDb > state.spectralBoostDb)
        assertTrue(policy.preventClippingActive)
        assertTrue(policy.estimatedHeadroomDb < -8f)
        assertEquals(dbToLinear(policy.estimatedHeadroomDb), audioOutputGain(state, 1f), 0.001f)
    }

    @Test
    fun nativeActivationSeparatesMedia3DspFromLowLatencyTone() {
        val dsp = AudioEngineState(
            native = NativeAudioSettings(enabled = true, media3DspEnabled = true, lowLatencyToneEnabled = true),
            output = OutputSettings(mode = OutputMode.NativeLowLatency),
        )
        val activation = nativeAudioActivationFor(dsp, nativeAvailable = true)

        assertTrue(activation.engineEnabled)
        assertTrue(activation.media3DspActive)
        assertFalse(activation.lowLatencyToneActive)

        val toneOnly = dsp.copy(native = dsp.native.copy(media3DspEnabled = false))
        assertTrue(nativeAudioActivationFor(toneOnly, nativeAvailable = true).lowLatencyToneActive)
    }

    @Test
    fun nativeOnlyEffectsAutoEngageMedia3DspWhenAvailable() {
        val reverb = AudioEngineState(
            native = NativeAudioSettings(enabled = false, media3DspEnabled = false),
            reverb = ReverbState(enabled = true, mix = 0.35f),
        )

        assertTrue(reverb.nativeOnlyProcessingActive)
        assertTrue(nativeAudioActivationFor(reverb, nativeAvailable = true).media3DspActive)
        assertFalse(nativeAudioActivationFor(reverb, nativeAvailable = true).lowLatencyToneActive)
        assertFalse(nativeAudioActivationFor(reverb, nativeAvailable = false).media3DspActive)
    }

    @Test
    fun nativeStereoControlsAutoEngageMedia3DspWhenAvailable() {
        val balance = AudioEngineState(stereo = StereoState(balance = 0.45f))
        val mono = AudioEngineState(stereo = StereoState(mono = true))
        val crossfeed = AudioEngineState(stereo = StereoState(crossfeed = 0.2f))

        assertTrue(balance.stereoMatrixActive)
        assertTrue(balance.nativeOnlyProcessingActive)
        assertTrue(nativeAudioActivationFor(balance, nativeAvailable = true).media3DspActive)
        assertTrue(nativeAudioActivationFor(mono, nativeAvailable = true).media3DspActive)
        assertTrue(nativeAudioActivationFor(crossfeed, nativeAvailable = true).media3DspActive)
    }

    @Test
    fun limiterAutoEngagesNativeDspOnlyWhenBoostCanClip() {
        val flat = AudioEngineState(limiter = LimiterState(enabled = true))
        val boosted = flat.copy(eqBands = flatEqBands().map { it.copy(gainDb = 6f) })

        assertFalse(flat.clippingRisk)
        assertFalse(flat.nativeOnlyProcessingActive)
        assertTrue(boosted.clippingRisk)
        assertTrue(boosted.nativeOnlyProcessingActive)
        assertTrue(nativeAudioActivationFor(boosted, nativeAvailable = true).media3DspActive)
    }

    @Test
    fun nativeFormatFallbackKeepsRequestedAndActiveDspSeparate() {
        val reverb = AudioEngineState(reverb = ReverbState(enabled = true, mix = 0.35f))
        val activation = nativeAudioActivationFor(
            state = reverb,
            nativeAvailable = true,
            nativeMedia3DspFormatSupported = false,
            nativeMedia3DspFallbackReason = "PCM float is not supported.",
        )

        assertTrue(activation.media3DspRequested)
        assertFalse(activation.media3DspActive)
        assertEquals("PCM float is not supported.", activation.fallbackReason)
    }

    @Test
    fun nativeActivePolicyOwnsPlatformEffectFamilies() {
        val state = AudioEngineState(
            eqBands = flatEqBands().map { it.copy(gainDb = 3f) },
            loudnessDb = 4f,
            reverb = ReverbState(enabled = true, mix = 0.25f),
        )
        val policy = state.effectiveProcessingPolicy(nativeAvailable = true)

        assertTrue(policy.nativeMedia3DspActive)
        assertFalse(policy.platformEqAllowed)
        assertFalse(policy.platformLoudnessAllowed)
    }

    @Test
    fun nativeCompareSlotCapturesFullAudioChain() {
        val source = AudioEngineState(
            preampDb = -3f,
            eqEnabled = false,
            eqBands = flatEqBands().map { it.copy(gainDb = 2f) },
            parametricEq = defaultParametricBands().mapIndexed { index, band -> band.copy(enabled = index == 1, gainDb = 4f) },
            bassDb = 2.5f,
            trebleDb = -1.5f,
            vocalClarityDb = 3.5f,
            loudnessDb = 5f,
            stereo = StereoState(balance = -0.25f, mono = true, stereoWidth = 0.4f, crossfeed = 0.2f),
            limiter = LimiterState(enabled = true, ceilingDb = -2f, releaseMs = 250f, strength = 0.7f),
            reverb = ReverbState(enabled = true, mix = 0.3f, size = 0.6f, preDelayMs = 42f, damp = 0.2f, decay = 2.4f),
            tempoPitch = TempoPitchState(tempo = 1.15f, pitchSemitones = 2f),
            compressor = CompressorState(enabled = true, thresholdDb = -24f, ratio = 4f, attackMs = 18f, releaseMs = 320f, makeupDb = 2f, mix = 0.66f),
            gate = GateState(enabled = true, thresholdDb = -48f, attackMs = 12f, holdMs = 90f, releaseMs = 180f),
            delay = DelayState(enabled = true, timeMs = 430f, feedback = 0.44f, mix = 0.25f),
            modulation = ModulationState(chorusEnabled = true, rateHz = 0.8f, depth = 0.5f, feedback = 0.35f, mix = 0.22f),
            native = NativeAudioSettings(enabled = true, media3DspEnabled = true, masterGain = 1.2f),
        )

        val restored = AudioEngineState().withNativePresetSlot(source.toNativePresetSlot("A"), NativeCompareSlot.A)

        assertEquals(NativeCompareSlot.A, restored.native.activeCompareSlot)
        assertFalse(restored.eqEnabled)
        assertEquals(2f, restored.eqBands.first().gainDb, 0.001f)
        assertTrue(restored.parametricEq[1].enabled)
        assertEquals(3.5f, restored.vocalClarityDb, 0.001f)
        assertTrue(restored.stereo.mono)
        assertEquals(-2f, restored.limiter.ceilingDb, 0.001f)
        assertEquals(42f, restored.reverb.preDelayMs, 0.001f)
        assertEquals(18f, restored.compressor.attackMs, 0.001f)
        assertEquals(90f, restored.gate.holdMs, 0.001f)
        assertEquals(0.44f, restored.delay.feedback, 0.001f)
        assertEquals(0.35f, restored.modulation.feedback, 0.001f)
        assertTrue(restored.native.media3DspEnabled)
        assertEquals(1.2f, restored.native.masterGain, 0.001f)
    }

    @Test
    fun nativeCompareSlotRestoresCanonicalEqShapeFromShortGains() {
        val slot = NativePresetSlot(
            eqGains = listOf(6f),
            parametricEq = listOf(ParametricEqBand(enabled = true, frequencyHz = 5f, gainDb = 18f, q = 99f)),
        )

        val restored = AudioEngineState().withNativePresetSlot(slot, NativeCompareSlot.A)

        assertEquals(graphicEqCenterFrequenciesHz(), restored.eqBands.map { it.frequencyHz })
        assertEquals(6f, restored.eqBands[0].gainDb, 0.001f)
        assertEquals(0f, restored.eqBands[1].gainDb, 0.001f)
        assertEquals(4, restored.parametricEq.size)
        assertEquals(20f, restored.parametricEq[0].frequencyHz, 0.001f)
        assertEquals(12f, restored.parametricEq[0].gainDb, 0.001f)
        assertEquals(18f, restored.parametricEq[0].q, 0.001f)
    }

    @Test
    fun platformSpectralEffectsBypassWhenNativeDspOwnsEqPath() {
        val eqState = AudioEngineState(
            eqBands = flatEqBands().map { it.copy(gainDb = 3f) },
        )

        assertTrue(platformSpectralEffectsActive(eqState, nativeMedia3DspActive = false))
        assertFalse(platformSpectralEffectsActive(eqState, nativeMedia3DspActive = true))
        assertFalse(platformSpectralEffectsActive(eqState.copy(advancedTweaks = AdvancedAudioTweaksSettings(safeMode = true)), nativeMedia3DspActive = false))
        assertFalse(platformSpectralEffectsActive(AudioEngineState(), nativeMedia3DspActive = false))
    }

    @Test
    fun nativeDspProcessorBypassesUnsupportedPcmFormats() {
        val processor = NativeDspAudioProcessor(NativeAudioEngineController())
        val result = processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, result)
        assertFalse(processor.isActive())
        processor.reset()
    }

    @Test
    fun commandSoundDesignGatesHeadsetAndNotificationCues() {
        val headsetOnly = HeadsetBluetoothSettings(beep = true, beepMore = false)

        assertEquals(
            1,
            commandCuesForKeyCommand(
                command = "KEYCODE_MEDIA_PLAY_PAUSE",
                settings = headsetOnly,
                source = CommandCueSource.HeadsetButton,
            ).size,
        )
        assertTrue(
            commandCuesForMediaAction(
                action = MediaAction.Shuffle,
                settings = headsetOnly,
                source = CommandCueSource.MediaSessionButton,
            ).isEmpty(),
        )

        val allCommands = headsetOnly.copy(beepMore = true)
        assertEquals(
            1,
            commandCuesForMediaAction(
                action = MediaAction.Shuffle,
                settings = allCommands,
                source = CommandCueSource.MediaSessionButton,
            ).size,
        )
        assertEquals(
            2,
            commandCuesForMediaAction(
                action = MediaAction.SeekForward10,
                settings = allCommands,
                source = CommandCueSource.MediaSessionButton,
            ).size,
        )
        assertEquals(1, rejectedCommandCue(allCommands).size)
    }

    @Test
    fun commandCueVolumeIsLowAndBounded() {
        val normalized = HeadsetBluetoothSettings(beepVolume = 2f).normalized()

        assertEquals(0.4f, normalized.beepVolume, 0.001f)
        assertEquals(40, normalized.commandCueVolumePercent())
        assertEquals(0, HeadsetBluetoothSettings(beepVolume = -1f).normalized().commandCueVolumePercent())
    }

    @Test
    fun beepOffSilencesAllCommandCueSources() {
        val off = HeadsetBluetoothSettings(beep = false, beepMore = true)

        assertTrue(commandCuesForKeyCommand("KEYCODE_MEDIA_PLAY_PAUSE", off, CommandCueSource.HeadsetButton).isEmpty())
        assertTrue(commandCuesForMediaAction(MediaAction.Shuffle, off, CommandCueSource.MediaSessionButton).isEmpty())
        assertTrue(rejectedCommandCue(off).isEmpty())
    }

    @Test
    fun rejectedCueOnlyPlaysWhenBeepMoreIsEnabled() {
        val headsetOnly = HeadsetBluetoothSettings(beep = true, beepMore = false)
        val allCommands = headsetOnly.copy(beepMore = true)

        assertTrue(rejectedCommandCue(headsetOnly).isEmpty())
        assertEquals(1, rejectedCommandCue(allCommands).size)
    }

    @Test
    fun catalogContainsAllCommandFeedbackSettings() {
        val keys = pulseSettingsCatalog().map { it.key }.toSet()

        assertTrue(keys.contains(SettingKey.HeadsetBeep))
        assertTrue(keys.contains(SettingKey.HeadsetBeepMore))
        assertTrue(keys.contains(SettingKey.HeadsetBeepVolume))
        assertTrue(keys.contains(SettingKey.HeadsetVibrate))
        assertTrue(keys.contains(SettingKey.MiscPulseDataSaver))
        assertTrue(keys.contains(SettingKey.MiscCellularStreamingQuality))
        assertTrue(keys.contains(SettingKey.MiscRoamingStreamingQuality))
        assertTrue(keys.contains(SettingKey.MiscStreamPreviewPolicy))
        assertTrue(keys.contains(SettingKey.MiscDataSaverMuxedFallback))
    }

    @Test
    fun importClampsCommandCueVolume() {
        val raw = """
            {
              "schemaVersion": 2,
              "headsetBluetooth": {
                "beep": true,
                "beepMore": true,
                "beepVolume": 9.0,
                "vibrate": true
              }
            }
        """.trimIndent()

        val imported = SettingsProfileCodec.decode(raw)
        assertEquals(0.4f, imported.headsetBluetooth.beepVolume, 0.001f)
        assertEquals(40, imported.headsetBluetooth.commandCueVolumePercent())
    }

    @Test
    fun backgroundDefaultsAndDependenciesNormalize() {
        val defaults = BackgroundSettings().normalized()

        assertEquals(1f, defaults.intensity, 0.001f)
        assertEquals(1f, defaults.saturation, 0.001f)
        assertEquals("#000000", defaults.gradientColor)
        assertTrue(defaults.gradientForLists)

        val disabled = BackgroundSettings(
            blurred = false,
            listBackground = true,
            lyricsBackground = true,
            gradientForLists = true,
            gradientColor = "ff00aa",
            blur = -4f,
            details = 99f,
            intensity = 3f,
            saturation = 3f,
        ).normalized()

        assertFalse(disabled.listBackground)
        assertFalse(disabled.lyricsBackground)
        assertFalse(disabled.gradientForLists)
        assertEquals("#FF00AA", disabled.gradientColor)
        assertEquals(0f, disabled.blur, 0.001f)
        assertEquals(10f, disabled.details, 0.001f)
        assertEquals(1f, disabled.intensity, 0.001f)
        assertEquals(2f, disabled.saturation, 0.001f)
    }

    @Test
    fun lookAndFeelAndAlbumArtNormalizePracticalRuntimeRanges() {
        val normalized = PulseSettingsState(
            lookAndFeel = LookAndFeelSettings(
                settingsTheme = SettingsTheme.Light,
                settingsFont = SettingsFont.BoldPlus,
                orientation = ScreenOrientationSetting.Landscape,
                hideStatusBar = true,
                keepScreenOn = true,
                settingsShortcutsCount = 99,
            ),
            albumArt = AlbumArtSettings(
                downloadAlbumArt = true,
                highestQualityCovers = true,
                autoReplaceExistingArt = true,
                minimumUpgradePercent = 999,
                saveFolderCover = true,
                providerOrder = listOf(AlbumArtworkProviderId.YouTube),
                preferOnlineImage = true,
                quality = AlbumArtQuality.Original,
                cacheSizeMb = 8,
                showDefaultImageWhenMissing = false,
            ),
        ).normalized()

        assertEquals(SettingsTheme.Light, normalized.lookAndFeel.settingsTheme)
        assertEquals(SettingsFont.BoldPlus, normalized.lookAndFeel.settingsFont)
        assertEquals(ScreenOrientationSetting.Landscape, normalized.lookAndFeel.orientation)
        assertTrue(normalized.lookAndFeel.hideStatusBar)
        assertTrue(normalized.lookAndFeel.keepScreenOn)
        assertEquals(10, normalized.lookAndFeel.settingsShortcutsCount)
        assertEquals(64, normalized.albumArt.cacheSizeMb)
        assertEquals(AlbumArtQuality.Original, normalized.albumArt.quality)
        assertEquals(300, normalized.albumArt.minimumUpgradePercent)
        assertEquals(AlbumArtworkProviderId.YouTube, normalized.albumArt.providerOrder.first())
        assertEquals(DefaultAlbumArtworkProviderOrder.toSet(), normalized.albumArt.providerOrder.toSet())
        assertFalse(normalized.albumArt.showDefaultImageWhenMissing)
    }

    @Test
    fun profileCodecPersistsLookAndFeelBackgroundAndAlbumArt() {
        val state = PulseSettingsState(
            lookAndFeel = LookAndFeelSettings(
                followSystemTheme = false,
                settingsTheme = SettingsTheme.Dark,
                settingsFont = SettingsFont.Alternative,
                orientation = ScreenOrientationSetting.Portrait,
                hideStatusBar = true,
                keepScreenOn = true,
            ),
            background = BackgroundSettings(
                gradientColor = "#2f67d8",
                blur = 8f,
                saturation = 1.8f,
            ),
            albumArt = AlbumArtSettings(
                preferEmbeddedArtwork = false,
                preferFolderImage = true,
                highestQualityCovers = true,
                autoReplaceExistingArt = false,
                minimumUpgradePercent = 55,
                saveFolderCover = false,
                providerOrder = listOf(
                    AlbumArtworkProviderId.AppleItunes,
                    AlbumArtworkProviderId.CoverArtArchive,
                    AlbumArtworkProviderId.Deezer,
                    AlbumArtworkProviderId.MusicHoarders,
                    AlbumArtworkProviderId.PageMetadata,
                    AlbumArtworkProviderId.YouTube,
                ),
                quality = AlbumArtQuality.Efficient,
                cacheSizeMb = 128,
            ),
        )

        val decoded = SettingsProfileCodec.decode(SettingsProfileCodec.encode(state).toString())

        assertFalse(decoded.lookAndFeel.followSystemTheme)
        assertEquals(SettingsTheme.Dark, decoded.lookAndFeel.settingsTheme)
        assertEquals(SettingsFont.Alternative, decoded.lookAndFeel.settingsFont)
        assertEquals(ScreenOrientationSetting.Portrait, decoded.lookAndFeel.orientation)
        assertTrue(decoded.lookAndFeel.hideStatusBar)
        assertTrue(decoded.lookAndFeel.keepScreenOn)
        assertEquals("#2F67D8", decoded.background.gradientColor)
        assertEquals(8f, decoded.background.blur, 0.001f)
        assertEquals(1.8f, decoded.background.saturation, 0.001f)
        assertFalse(decoded.albumArt.preferEmbeddedArtwork)
        assertTrue(decoded.albumArt.highestQualityCovers)
        assertFalse(decoded.albumArt.autoReplaceExistingArt)
        assertEquals(55, decoded.albumArt.minimumUpgradePercent)
        assertFalse(decoded.albumArt.saveFolderCover)
        assertEquals(AlbumArtworkProviderId.AppleItunes, decoded.albumArt.providerOrder.first())
        assertEquals(AlbumArtQuality.Efficient, decoded.albumArt.quality)
        assertEquals(128, decoded.albumArt.cacheSizeMb)
    }

    @Test
    fun albumArtworkUpgradePolicyRequiresMissingArtOrClearResolutionGain() {
        assertTrue(artworkUpgradeAccepted(0, 0, 600, 600, minimumUpgradePercent = 35))
        assertFalse(artworkUpgradeAccepted(1000, 1000, 1100, 1100, minimumUpgradePercent = 35))
        assertTrue(artworkUpgradeAccepted(1000, 1000, 1200, 1200, minimumUpgradePercent = 35))
        assertTrue(artworkUpgradeAccepted(1000, 1000, 1400, 900, minimumUpgradePercent = 35))
    }

    @Test
    fun albumArtworkRankingPrefersExactAlbumArtistMatchOverLowerConfidenceFallbacks() {
        val exact = ArtworkCandidate(
            provider = AlbumArtworkProviderId.AppleItunes,
            imageUrl = "https://example.com/nina-vale/blue-hour/3000x3000bb.jpg",
            width = 3000,
            height = 3000,
            confidence = artworkTextMatchConfidence("Blue Hour", "Nina Vale", "Blue Hour", "Nina Vale", base = 60),
            matchReason = "Apple/iTunes album artwork",
        )
        val fuzzy = ArtworkCandidate(
            provider = AlbumArtworkProviderId.YouTube,
            imageUrl = "https://img.youtube.com/vi/demo/maxresdefault.jpg",
            width = 1280,
            height = 720,
            confidence = artworkTextMatchConfidence("Blue Hour", "Nina Vale", "Blue Hour official audio", "Nina Vale Topic", base = 42),
            matchReason = "YouTube stream thumbnail",
        )

        val ranked = rankArtworkCandidates(listOf(fuzzy, exact), DefaultAlbumArtworkProviderOrder)

        assertEquals(AlbumArtworkProviderId.AppleItunes, ranked.first().candidate.provider)
    }

    @Test
    fun albumArtworkLookupRequiresReliableAlbumAndArtistMetadata() {
        assertFalse(artworkLookupMetadataReliable("Blue Hour", ""))
        assertFalse(artworkLookupMetadataReliable("Blue Hour", "Unknown Artist"))
        assertFalse(artworkLookupMetadataReliable("Unknown Album", "Nina Vale"))
        assertTrue(artworkLookupMetadataReliable("Blue Hour", "Nina Vale"))
    }

    @Test
    fun smartYouTubeMetadataExtractsArtistAndTitleWithoutTrustingGenericChannels() {
        val split = smartYouTubeMusicMetadata("Nina Vale - Blue Hour (Official Music Video)", "7clouds")
        assertEquals("Nina Vale", split.artist)
        assertEquals("Blue Hour", split.title)
        assertTrue(split.confidence >= 90)

        val topic = smartYouTubeMusicMetadata("Blue Hour [Official Audio]", "Nina Vale - Topic")
        assertEquals("Nina Vale", topic.artist)
        assertEquals("Blue Hour", topic.title)
        assertTrue(topic.confidence >= 70)

        val genericChannel = smartYouTubeMusicMetadata("Blue Hour (Lyrics)", "7clouds")
        assertEquals("", genericChannel.artist)
        assertEquals("Blue Hour", genericChannel.title)
        assertTrue(genericChannel.confidence < 60)
    }

    @Test
    fun settingsSearchMatchesSynonymsAndMultiWordQueries() {
        val eq = SettingsSearchEntry(
            title = "Equalizer",
            subtitle = "EQ settings, presets, bands, tone, limiter, and A/B bypass",
            keywords = listOf("eq", "bass", "treble", "preset"),
        )
        val color = SettingsSearchEntry(
            title = "Background Gradient Color",
            subtitle = "Hex color and contrast preview",
            keywords = listOf("color", "swatch", "readability"),
        )

        assertTrue(settingsSearchMatches(eq, "eq"))
        assertTrue(settingsSearchMatches(eq, "bass"))
        assertFalse(settingsSearchMatches(eq, "lyrics"))
        assertTrue(settingsSearchMatches(color, "gradient color"))
        assertTrue(settingsSearchMatches(color, "swatch"))
    }

    @Test
    fun generatedStreamMixesPreferTwentyFiveRelatedTracks() {
        val sources = (1..30).map { index ->
            streamSource(
                id = "nina-$index",
                artist = "Nina Vale",
                title = "Signal Bloom $index",
                playCount = 31 - index,
                lastPlayedMillis = 1_000L + index,
                reaction = if (index == 1) YouTubeReaction.Liked else YouTubeReaction.Neutral,
            )
        }

        val heavy = buildStreamMixes(
            localSources = sources,
            discoverySources = emptyList(),
            playlists = emptyList(),
            playHistory = sources.take(5).map(::streamHistory),
            mixSessionSeed = 41L,
        ).first { it.title == "Heavy Rotation" }

        assertEquals(STREAM_MIX_TARGET_SIZE, heavy.sources.size)
        assertTrue(heavy.sources.all { it.author == "Nina Vale" })
    }

    @Test
    fun generatedMixIdsChangeWithSessionSeed() {
        val sources = (1..12).map { index ->
            streamSource(
                id = "orbit-$index",
                artist = "Orbit House",
                title = "Night Current $index",
                playCount = 3,
                lastPlayedMillis = 2_000L + index,
            )
        }

        val firstVisit = buildStreamMixes(sources, emptyList(), emptyList(), sources.take(3).map(::streamHistory), mixSessionSeed = 10L)
        val secondVisit = buildStreamMixes(sources, emptyList(), emptyList(), sources.take(3).map(::streamHistory), mixSessionSeed = 20L)

        assertNotEquals(firstVisit.first().id, secondVisit.first().id)
    }

    @Test
    fun playlistOriginDefaultsManualAndSavedMixStaysStable() {
        val manual = YouTubePlaylist(id = "manual", title = "Road Set")
        val saved = manual.copy(id = "saved", title = "Heavy Rotation", sourceIds = listOf("a", "b"), origin = YouTubePlaylistOrigin.SavedMix)

        assertEquals(YouTubePlaylistOrigin.Manual, manual.origin)
        assertEquals(YouTubePlaylistOrigin.SavedMix, saved.origin)
        assertEquals(listOf("a", "b"), saved.sourceIds)
    }

    @Test
    fun mixPlannerExcludesDislikedTracksAndArtists() {
        val seed = streamSource("seed", artist = "Nina Vale", title = "Signal Bloom", playCount = 6)
        val related = streamSource("related", artist = "Nina Vale", title = "Signal Bloom live")
        val dislikedTrack = streamSource("skip-track", artist = "Nina Vale", title = "Signal Bloom remix", reaction = YouTubeReaction.Disliked)
        val dislikedArtist = streamSource("bad-artist", artist = "Static Gray", title = "Signal Bloom cover", reaction = YouTubeReaction.Disliked)
        val sameDislikedArtist = streamSource("bad-related", artist = "Static Gray", title = "Signal Bloom alternate")

        val result = mixSources(
            seedSources = listOf(seed),
            candidates = listOf(seed, related, dislikedTrack, dislikedArtist, sameDislikedArtist),
            localSources = listOf(seed, dislikedArtist),
            playHistory = listOf(streamHistory(seed)),
            sessionSeed = 5L,
        )

        assertTrue(result.any { it.id == related.id })
        assertFalse(result.any { it.id == dislikedTrack.id })
        assertFalse(result.any { it.author == "Static Gray" })
    }

    @Test
    fun discoveryTracksAreCappedAndHistoryRelated() {
        val local = (1..20).map { index ->
            streamSource("local-$index", artist = "Nina Vale", title = "Signal Bloom $index", playCount = 2)
        }
        val discovery = (1..20).map { index ->
            streamSource("disc-$index", artist = "Nina Vale", title = "Signal Bloom discovery $index")
        }

        val result = mixSources(
            seedSources = local.take(5),
            candidates = local + discovery,
            localSources = local,
            playHistory = local.take(4).map(::streamHistory),
            sessionSeed = 9L,
        )

        assertEquals(STREAM_MIX_TARGET_SIZE, result.size)
        assertTrue(result.count { it.id.startsWith("disc-") } <= 8)
        assertTrue(result.all { it.author == "Nina Vale" })
    }

    @Test
    fun sparseHistoryReturnsSmallerMixInsteadOfUnrelatedFill() {
        val seed = streamSource("seed", artist = "Nina Vale", title = "Signal Bloom", playCount = 8)
        val unrelatedDiscovery = (1..30).map { index ->
            streamSource("other-$index", artist = "Distant Genre $index", title = "Unrelated Track $index")
        }

        val result = mixSources(
            seedSources = listOf(seed),
            candidates = listOf(seed) + unrelatedDiscovery,
            localSources = listOf(seed),
            playHistory = listOf(streamHistory(seed)),
            sessionSeed = 12L,
        )

        assertEquals(listOf("seed"), result.map { it.id })
    }

    @Test
    fun playlistSuggestionsExcludeDuplicatesDislikedTracksAndArtists() {
        val seed = streamSource("seed", artist = "Nina Vale", title = "Signal Bloom", playCount = 4)
        val duplicate = streamSource("seed", artist = "Nina Vale", title = "Signal Bloom")
        val related = streamSource("related", artist = "Nina Vale", title = "Signal Bloom alternate")
        val dislikedTrack = streamSource("skip", artist = "Nina Vale", title = "Signal Bloom remix", reaction = YouTubeReaction.Disliked)
        val dislikedArtistSeed = streamSource("bad-seed", artist = "Static Gray", title = "Static Intro", reaction = YouTubeReaction.Disliked)
        val dislikedArtistCandidate = streamSource("bad-candidate", artist = "Static Gray", title = "Signal Bloom cover")
        val playlist = YouTubePlaylist(id = "mix", title = "Pinned Mix", sourceIds = listOf(seed.id), origin = YouTubePlaylistOrigin.SavedMix)

        val suggestions = suggestStreamPlaylistAdditions(
            playlist = playlist,
            sources = listOf(seed, duplicate, related, dislikedTrack, dislikedArtistSeed, dislikedArtistCandidate),
            discoveryResults = emptyList(),
            playHistory = listOf(streamHistory(seed)),
        )

        assertEquals(listOf("related"), suggestions.map { it.id })
    }

    @Test
    fun playlistSuggestionsPreferRelatedHistoryCandidates() {
        val seed = streamSource("seed", artist = "Nina Vale", title = "Signal Bloom", playCount = 7, reaction = YouTubeReaction.Liked)
        val related = streamSource("related", artist = "Nina Vale", title = "Signal Bloom afterglow")
        val historyRelated = streamSource("history", artist = "Nina Vale", title = "Signal Bloom night")
        val unrelated = streamSource("other", artist = "Other Artist", title = "Different Song")
        val playlist = YouTubePlaylist(id = "mix", title = "Pinned Mix", sourceIds = listOf(seed.id), origin = YouTubePlaylistOrigin.SavedMix)

        val suggestions = suggestStreamPlaylistAdditions(
            playlist = playlist,
            sources = listOf(seed, related, historyRelated, unrelated),
            discoveryResults = emptyList(),
            playHistory = listOf(streamHistory(seed)),
            limit = 3,
        )

        assertEquals(listOf("history", "related"), suggestions.map { it.id })
        assertFalse(suggestions.any { it.id == "other" })
    }

    @Test
    fun playlistAddRemoveHelpersPreserveOrderDeterministically() {
        val playlist = YouTubePlaylist(id = "mix", title = "Pinned Mix", sourceIds = listOf("a", "b", "c"), origin = YouTubePlaylistOrigin.SavedMix)
        val added = streamPlaylistSourceIdsAfterAdd(playlist, streamSource("d", artist = "Nina Vale", title = "Signal Bloom d"))
        val duplicate = streamPlaylistSourceIdsAfterAdd(playlist, streamSource("b", artist = "Nina Vale", title = "Signal Bloom b"))
        val removed = streamPlaylistSourceIdsAfterRemove(playlist, "b")

        assertEquals(listOf("a", "b", "c", "d"), added)
        assertEquals(listOf("a", "b", "c"), duplicate)
        assertEquals(listOf("a", "c"), removed)
    }

    private fun streamSource(
        id: String,
        artist: String,
        title: String,
        playCount: Int = 0,
        lastPlayedMillis: Long = 0L,
        reaction: YouTubeReaction = YouTubeReaction.Neutral,
        bookmarked: Boolean = false,
        downloadState: YouTubeDownloadState = YouTubeDownloadState.None,
    ): YouTubeSource =
        YouTubeSource(
            id = id,
            url = "https://www.youtube.com/watch?v=$id",
            kind = YouTubeSourceKind.Video,
            title = title,
            author = artist,
            playCount = playCount,
            lastPlayedMillis = lastPlayedMillis,
            reaction = reaction,
            bookmarked = bookmarked,
            downloadState = downloadState,
            status = if (downloadState == YouTubeDownloadState.Downloaded) YouTubeSourceStatus.Downloaded else YouTubeSourceStatus.StreamReady,
        )

    private fun streamHistory(source: YouTubeSource): StreamPlayHistoryItem =
        StreamPlayHistoryItem(
            sourceId = source.id,
            url = source.url,
            title = source.title,
            author = source.author,
            thumbnailUrl = source.thumbnailUrl,
            durationMillis = source.durationMillis,
            playedAtMillis = source.lastPlayedMillis.takeIf { it > 0L } ?: 1_000L,
        )
}
