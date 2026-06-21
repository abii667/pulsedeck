package com.pulsedeck.app

import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DspGraphInspectorTest {
    @Test
    fun flatEngineOffGraphKeepsCanonicalStageOrderAndNoActiveProcessing() {
        val graph = dspGraphSnapshot(
            state = AudioEngineState(enabled = false),
            diagnostics = OutputDiagnosticsSnapshot(updatedAtMillis = 1L),
            nativeAvailable = true,
        )

        assertFalse(graph.processingActive)
        assertEquals(DspStageId.entries.toList(), graph.stages.map { it.id })
        assertEquals(DspStageStatus.Transparent, graph.stage(DspStageId.Input).status)
        assertEquals(DspStageStatus.Transparent, graph.stage(DspStageId.Output).status)
        assertEquals(ClippingRisk.None, graph.clippingRisk)
        assertEquals(0f, graph.totalHeadroomDb, 0.001f)
    }

    @Test
    fun nativeDspGraphOwnsNativeStagesWhenRuntimeReportsNativeActive() {
        val graph = dspGraphSnapshot(
            state = AudioEngineState(
                parametricEq = defaultParametricBands().mapIndexed { index, band ->
                    if (index == 0) band.copy(enabled = true, gainDb = 4f) else band
                },
                compressor = CompressorState(enabled = true, mix = 0.65f, makeupDb = 2f),
                native = NativeAudioSettings(enabled = true, media3DspEnabled = true),
            ),
            diagnostics = OutputDiagnosticsSnapshot(
                nativeMedia3DspRequested = true,
                nativeMedia3DspActive = true,
                updatedAtMillis = 2L,
            ),
            nativeAvailable = true,
        )

        assertEquals(DspStageOwner.NativeDsp, graph.stage(DspStageId.ParametricEq).owner)
        assertEquals(DspStageStatus.Active, graph.stage(DspStageId.ParametricEq).status)
        assertEquals(DspStageOwner.NativeDsp, graph.stage(DspStageId.Dynamics).owner)
        assertEquals(DspStageStatus.Active, graph.stage(DspStageId.Dynamics).status)
        assertFalse(graph.warnings.any { it.title == "Native-only stage unavailable" })
    }

    @Test
    fun graphicEqExtendedRangeIsReportedOnlyWhenNativeDspOwnsIt() {
        val extendedState = AudioEngineState(
            graphicEqRangeMode = GraphicEqRangeMode.Extended15DbWhenSupported,
            eqBands = flatEqBands().mapIndexed { index, band ->
                if (index == 5) band.copy(gainDb = 15f) else band
            },
            native = NativeAudioSettings(enabled = true, media3DspEnabled = true),
        ).normalized()

        val nativeGraph = dspGraphSnapshot(
            state = extendedState,
            diagnostics = OutputDiagnosticsSnapshot(
                nativeMedia3DspRequested = true,
                nativeMedia3DspActive = true,
                updatedAtMillis = 7L,
            ),
            nativeAvailable = true,
        )
        assertTrue(nativeGraph.graphicEq.extendedRangeRequested)
        assertTrue(nativeGraph.graphicEq.extendedRangeEffective)
        assertEquals(15f, nativeGraph.graphicEq.effectiveMaxGainDb, 0.001f)
        assertEquals(15f, nativeGraph.graphicEq.maxEffectiveGainDb, 0.001f)

        val platformGraph = dspGraphSnapshot(
            state = extendedState.copy(
                limiter = extendedState.limiter.copy(enabled = false),
                native = extendedState.native.copy(media3DspEnabled = false),
            ),
            diagnostics = OutputDiagnosticsSnapshot(updatedAtMillis = 8L),
            nativeAvailable = true,
        )
        assertTrue(platformGraph.graphicEq.extendedRangeRequested)
        assertFalse(platformGraph.graphicEq.extendedRangeEffective)
        assertEquals(12f, platformGraph.graphicEq.effectiveMaxGainDb, 0.001f)
        assertEquals(12f, platformGraph.graphicEq.maxEffectiveGainDb, 0.001f)
        assertEquals(DspStageOwner.AndroidPlatformEffect, platformGraph.graphicEq.owner)
        assertTrue(platformGraph.warnings.any { it.title == "Graphic EQ extended range inactive" })
    }

    @Test
    fun nativePreampCeilingIsReportedWhenRequestedGainExceedsVerifiedLimit() {
        val graph = dspGraphSnapshot(
            state = AudioEngineState(
                preampDb = 12f,
                preventClipping = false,
                native = NativeAudioSettings(enabled = true, media3DspEnabled = true),
            ),
            diagnostics = OutputDiagnosticsSnapshot(
                nativeMedia3DspRequested = true,
                nativeMedia3DspActive = true,
                updatedAtMillis = 9L,
            ),
            nativeAvailable = true,
        )

        val preamp = graph.stage(DspStageId.Preamp)
        assertEquals(DspStageOwner.NativeDsp, preamp.owner)
        assertTrue(preamp.notes?.contains("constrained") == true)
        assertTrue(graph.warnings.any { it.title == "Native preamp ceiling" })
    }

    @Test
    fun bypassAndSafeModeExposeConfiguredStagesAsNotApplied() {
        val boostedEq = flatEqBands().mapIndexed { index, band ->
            if (index == 5) band.copy(gainDb = 6f) else band
        }
        val bypassed = dspGraphSnapshot(
            state = AudioEngineState(bypass = true, eqBands = boostedEq),
            diagnostics = OutputDiagnosticsSnapshot(updatedAtMillis = 3L),
            nativeAvailable = true,
        )
        val safe = dspGraphSnapshot(
            state = AudioEngineState(
                eqBands = boostedEq,
                advancedTweaks = AdvancedAudioTweaksSettings(safeMode = true),
            ),
            diagnostics = OutputDiagnosticsSnapshot(updatedAtMillis = 4L),
            nativeAvailable = true,
        )

        assertEquals(DspStageStatus.Bypassed, bypassed.stage(DspStageId.GraphicEq).status)
        assertFalse(bypassed.stage(DspStageId.GraphicEq).active)
        assertTrue(bypassed.warnings.any { it.title == "Bypass active" })
        assertEquals(DspStageStatus.SafeModeDisabled, safe.stage(DspStageId.GraphicEq).status)
        assertFalse(safe.stage(DspStageId.GraphicEq).active)
        assertTrue(safe.warnings.any { it.title == "Safe mode active" })
    }

    @Test
    fun autoHeadroomReportsContributionAndOutputTrimWithoutChangingSoundPolicy() {
        val boostedEq = flatEqBands().mapIndexed { index, band ->
            if (index == 5) band.copy(gainDb = 10f) else band
        }
        val graph = dspGraphSnapshot(
            state = AudioEngineState(preampDb = 2f, eqBands = boostedEq, preventClipping = true),
            diagnostics = OutputDiagnosticsSnapshot(
                preventClippingActive = true,
                estimatedHeadroomDb = -12f,
                updatedAtMillis = 5L,
            ),
            nativeAvailable = true,
        )

        val headroom = graph.stage(DspStageId.AutoHeadroom)
        assertEquals(DspStageStatus.Active, headroom.status)
        assertEquals(-12f, headroom.headroomContributionDb ?: 0f, 0.001f)
        assertTrue(graph.outputTrimDb < 0f)
        assertEquals(ClippingRisk.High, graph.clippingRisk)
        assertTrue(graph.warnings.any { it.title == "Auto headroom active" })
    }

    @Test
    fun nativeOnlyStageIsUnsupportedWhenNativeDspIsUnavailable() {
        val graph = dspGraphSnapshot(
            state = AudioEngineState(
                parametricEq = defaultParametricBands().mapIndexed { index, band ->
                    if (index == 0) band.copy(enabled = true, gainDb = 3f) else band
                },
            ),
            diagnostics = OutputDiagnosticsSnapshot(
                nativeMedia3DspRequested = true,
                nativeMedia3DspActive = false,
                dspFallbackReason = "Native Media3 DSP unavailable.",
                updatedAtMillis = 6L,
            ),
            nativeAvailable = false,
        )

        val parametric = graph.stage(DspStageId.ParametricEq)
        assertEquals(DspStageOwner.Unavailable, parametric.owner)
        assertEquals(DspStageStatus.Unsupported, parametric.status)
        assertTrue(graph.warnings.any { it.title == "Native DSP fallback" })
        assertTrue(graph.warnings.any { it.title == "Native-only stage unavailable" })
    }

    @Test
    fun phase12bAddsBypassAndMeterGroundworkWithoutLiveMeterClaims() {
        val boostedEq = flatEqBands().mapIndexed { index, band ->
            if (index == 5) band.copy(gainDb = 4f) else band
        }
        val graph = dspGraphSnapshot(
            state = AudioEngineState(eqBands = boostedEq),
            nativeAvailable = true,
        )

        val graphic = graph.stage(DspStageId.GraphicEq)
        val bypass = graphic.bypassState
        val meter = graphic.meter

        assertEquals(DspStageId.entries.size, graph.bypassStates.size)
        assertEquals(false, graph.metering.perStageMetersAvailable)
        assertEquals(DspMeterSource.Placeholder, graph.metering.source)
        assertEquals(true, bypass?.userBypassable)
        assertEquals(false, bypass?.bypassed)
        assertEquals(false, meter?.live)
        assertEquals(DspMeterSource.ModelEstimate, meter?.source)
        assertTrue(graph.warnings.any { it.title == "Phase 12B groundwork" })
    }

    @Test
    fun phase13aGraphicEqDiagnosticsKeepTenCanonicalSlotsAndOneKhzMapping() {
        val graph = dspGraphSnapshot(
            state = AudioEngineState().withGraphicEqBandGain(5, 10.2f),
            nativeAvailable = true,
        )

        val graphic = graph.graphicEq
        val oneKhz = graphic.slots[5]

        assertEquals(GRAPHIC_EQ_BAND_COUNT, graphic.slots.size)
        assertTrue(graphic.canonical)
        assertEquals(5, oneKhz.index)
        assertEquals(1000f, oneKhz.centerFrequencyHz, 0.001f)
        assertEquals(10.2f, oneKhz.gainDb, 0.001f)
        assertEquals(1, graphic.slots.count { it.active })
        assertTrue(graphic.slots.filterNot { it.index == 5 }.all { it.gainDb == 0f })
        assertEquals(-10.2f, graph.totalHeadroomDb, 0.001f)
        assertEquals(DspStageOwner.NativeDsp, graphic.owner)
    }

    @Test
    fun phase13aGraphicEqDiagnosticsReportPlatformOwnershipWhenNativeDspIsNotActive() {
        val graph = dspGraphSnapshot(
            state = AudioEngineState(limiter = LimiterState(enabled = false)).withGraphicEqBandGain(5, 0.5f),
            nativeAvailable = true,
        )

        assertEquals(DspStageOwner.AndroidPlatformEffect, graph.graphicEq.owner)
        assertEquals(DspStageOwner.AndroidPlatformEffect, graph.stage(DspStageId.GraphicEq).owner)
    }

    @Test
    fun phase13aGraphicEqDiagnosticsReportNativeOwnershipWhenNativeDspIsActive() {
        val graph = dspGraphSnapshot(
            state = AudioEngineState(
                native = NativeAudioSettings(media3DspEnabled = true),
            ).withGraphicEqBandGain(5, 6f),
            diagnostics = OutputDiagnosticsSnapshot(
                nativeMedia3DspRequested = true,
                nativeMedia3DspActive = true,
                updatedAtMillis = 7L,
            ),
            nativeAvailable = true,
        )

        assertEquals(DspStageOwner.NativeDsp, graph.graphicEq.owner)
        assertEquals(DspStageOwner.NativeDsp, graph.stage(DspStageId.GraphicEq).owner)
    }

    @Test
    fun phase13bParametricDiagnosticsClampFourSlotsAndKeepDisabledSlotsNeutral() {
        val rawBands = listOf(
            ParametricEqBand(enabled = false, frequencyHz = 1f, gainDb = 24f, q = 40f),
            ParametricEqBand(enabled = true, type = ParametricFilterType.Peak, frequencyHz = 650f, gainDb = 5f, q = 0.5f),
            ParametricEqBand(enabled = false, type = ParametricFilterType.HighPass, frequencyHz = 2500f, gainDb = -5f, q = 1.2f),
            ParametricEqBand(enabled = false, frequencyHz = 40_000f, gainDb = -24f, q = 0.01f),
            ParametricEqBand(enabled = true, gainDb = 12f),
        )
        val graph = dspGraphSnapshot(
            state = AudioEngineState(parametricEq = rawBands).normalized(),
            nativeAvailable = false,
        )

        val parametric = graph.parametricEq

        assertEquals(PARAMETRIC_EQ_SLOT_COUNT, parametric.slots.size)
        assertTrue(parametric.canonical)
        assertEquals(1, parametric.activeSlotCount)
        assertTrue(parametric.allDisabledSlotsNeutral)
        assertTrue(parametric.slots.all { it.validFrequencyRange && it.validGainRange && it.validQRange })
        assertEquals(PARAMETRIC_EQ_MIN_FREQUENCY_HZ, parametric.slots[0].frequencyHz, 0.001f)
        assertEquals(AUDIO_EQ_MAX_GAIN_DB, parametric.slots[0].gainDb, 0.001f)
        assertEquals(PARAMETRIC_EQ_MAX_Q, parametric.slots[0].q, 0.001f)
        assertEquals(DspStageOwner.Unavailable, parametric.owner)
        assertEquals(DspStageStatus.Unsupported, graph.stage(DspStageId.ParametricEq).status)
    }

    @Test
    fun phase13cToneDiagnosticsReportToneSeparatelyFromGraphicEq() {
        val state = AudioEngineState(
            limiter = LimiterState(enabled = false),
            bassDb = 4f,
            trebleDb = 2f,
            vocalClarityDb = 1f,
        ).withGraphicEqBandGain(5, 6f)

        val graph = dspGraphSnapshot(state = state, nativeAvailable = true)
        val tone = graph.tone

        assertTrue(tone.active)
        assertEquals(4, tone.controls.size)
        assertEquals(4f, tone.maxBoostDb, 0.001f)
        assertEquals(6f, graph.graphicEq.maxGainDb, 0.001f)
        assertTrue(tone.overlapsGraphicEq)
        assertTrue(tone.controls.first { it.id == ToneControlId.Bass }.overlapsBassBoost)
        assertTrue(tone.resetByFlatPreset)
        assertTrue(graph.stage(DspStageId.Tone).diagnostics.any { it.contains("tone max") })

        val bypassed = dspGraphSnapshot(state = state.copy(bypass = true), nativeAvailable = true)
        assertEquals(DspStageStatus.Bypassed, bypassed.stage(DspStageId.Tone).status)
        assertTrue(bypassed.tone.bypassed)
    }

    @Test
    fun phase13cSmartPreampRecommendsModelOnlyTrimForCombinedBoost() {
        val state = AudioEngineState(
            limiter = LimiterState(enabled = false),
            replayGainMode = ReplayGainMode.Track,
            replayGainPreampDb = 2f,
            bassDb = 3f,
        ).withGraphicEqBandGain(5, 4f)

        val graph = dspGraphSnapshot(state = state, nativeAvailable = true)
        val preamp = graph.smartPreamp

        assertEquals(SmartPreampReason.CombinedBoost, preamp.reason)
        assertEquals(4f, preamp.eqBoostDb, 0.001f)
        assertEquals(3f, preamp.toneBoostDb, 0.001f)
        assertEquals(2f, preamp.replayGainBoostDb, 0.001f)
        assertEquals(-9f, preamp.recommendedPreampDb, 0.001f)
        assertEquals(-9f, preamp.targetHeadroomDb, 0.001f)
        assertEquals(SmartPreampConfidence.High, preamp.confidence)
    }

    @Test
    fun phase14aLoudnessGroundworkUsesMeasuredFileLufsButNotTruePeak() {
        val state = AudioEngineState().copy(
            sourceLoudness = LoudnessMetadata(
                trackGainDb = -5f,
                trackPeak = 1.12f,
                integratedLufs = -15.4f,
                source = LoudnessSource.FileTag,
                confidence = LoudnessConfidence.High,
                scannedAtMillis = 123L,
            ).normalized(),
        )

        val graph = dspGraphSnapshot(state = state, nativeAvailable = true)

        assertEquals(LoudnessMeasurementStatus.FileTag, graph.loudness.measurementStatus)
        assertEquals(-15.4f, graph.loudness.measuredIntegratedLufs ?: 0f, 0.001f)
        assertEquals(TruePeakStatus.ReplayGainSamplePeakOnly, graph.loudness.truePeak.status)
        assertEquals(null, graph.loudness.truePeak.truePeakDbTp)
        assertEquals(1.12f, graph.loudness.truePeak.replayGainSamplePeak ?: 0f, 0.001f)
    }

    @Test
    fun phase14aDoesNotInventStreamLufsOrTruePeak() {
        val state = AudioEngineState().copy(
            sourceLoudness = LoudnessMetadata(
                integratedLufs = -13f,
                source = LoudnessSource.StreamMetadata,
                confidence = LoudnessConfidence.Low,
            ).normalized(),
        )

        val graph = dspGraphSnapshot(state = state, nativeAvailable = true)

        assertEquals(LoudnessMeasurementStatus.StreamMetadataNotMeasured, graph.loudness.measurementStatus)
        assertEquals(null, graph.loudness.measuredIntegratedLufs)
        assertFalse(graph.loudness.estimatedForStream)
        assertEquals(TruePeakStatus.PlaceholderOnly, graph.loudness.truePeak.status)
        assertTrue(graph.warnings.any { it.title == "Stream loudness not measured" })
    }

    @Test
    fun phase14bHeadroomManagerAndLimiterGroundworkStayDiagnostic() {
        val state = AudioEngineState(
            bassDb = 3f,
            compressor = CompressorState(enabled = true, makeupDb = 2f, mix = 0.5f),
            delay = DelayState(enabled = true, mix = 0.2f, feedback = 0.3f),
        ).withGraphicEqBandGain(5, 5f)

        val graph = dspGraphSnapshot(state = state, nativeAvailable = true)

        assertTrue(graph.headroomManager.totalPositiveRiskDb > 0f)
        assertTrue(graph.headroomManager.appliedPreventClippingTrimDb < 0f)
        assertEquals(false, graph.limiterGroundwork.implemented)
        assertEquals(false, graph.limiterGroundwork.active)
        assertEquals(LimiterProtectionMode.NativeSoftLimiter, graph.limiterGroundwork.currentProtection)
        assertTrue(graph.headroomManager.contributions.any { it.stageId == DspStageId.Tone && it.boostDb > 0f })
        assertTrue(graph.headroomManager.contributions.any { it.stageId == DspStageId.TimeEffects && it.boostDb > 0f })
    }

    private fun DspGraphSnapshot.stage(id: DspStageId): DspGraphStage =
        stages.first { it.id == id }
}
