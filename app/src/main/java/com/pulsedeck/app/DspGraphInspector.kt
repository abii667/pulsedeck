package com.pulsedeck.app

import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.TruePeakMeasurementStatus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal data class DspGraphSnapshot(
    val stages: List<DspGraphStage>,
    val processingActive: Boolean,
    val bypass: Boolean,
    val safeMode: Boolean,
    val totalHeadroomDb: Float,
    val outputTrimDb: Float,
    val clippingRisk: ClippingRisk,
    val bypassStates: List<DspStageBypassState> = emptyList(),
    val metering: DspGraphMeteringSnapshot = DspGraphMeteringSnapshot(),
    val graphicEq: GraphicEqDiagnosticsSnapshot = GraphicEqDiagnosticsSnapshot(),
    val parametricEq: ParametricEqDiagnosticsSnapshot = ParametricEqDiagnosticsSnapshot(),
    val tone: ToneDiagnosticsSnapshot = ToneDiagnosticsSnapshot(),
    val smartPreamp: SmartPreampRecommendation = SmartPreampRecommendation(),
    val loudness: LoudnessGroundworkSnapshot = LoudnessGroundworkSnapshot(),
    val headroomManager: HeadroomManagerSnapshot = HeadroomManagerSnapshot(),
    val limiterGroundwork: LookAheadLimiterGroundworkSnapshot = LookAheadLimiterGroundworkSnapshot(),
    val warnings: List<DspGraphWarning> = emptyList(),
)

internal data class DspGraphStage(
    val id: DspStageId,
    val label: String,
    val order: Int,
    val implemented: Boolean,
    val active: Boolean,
    val bypassed: Boolean,
    val owner: DspStageOwner,
    val inputFormat: AudioFormatSummary? = null,
    val outputFormat: AudioFormatSummary? = null,
    val gainChangeDb: Float? = null,
    val headroomContributionDb: Float? = null,
    val clippingRiskContribution: ClippingRisk = ClippingRisk.None,
    val latencyMs: Float? = null,
    val cpuCost: DspCostClass = DspCostClass.None,
    val status: DspStageStatus,
    val bypassState: DspStageBypassState? = null,
    val meter: DspStageMeterSnapshot? = null,
    val diagnostics: List<String> = emptyList(),
    val notes: String? = null,
)

internal data class AudioFormatSummary(
    val encoding: String,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val source: String = "Unknown",
)

internal data class DspStageBypassState(
    val stageId: DspStageId,
    val userBypassable: Boolean,
    val bypassed: Boolean,
    val reason: String? = null,
)

internal data class DspStageMeterSnapshot(
    val stageId: DspStageId,
    val source: DspMeterSource,
    val live: Boolean,
    val inputLevelDb: Float? = null,
    val outputLevelDb: Float? = null,
    val gainDeltaDb: Float? = null,
    val reason: String? = null,
)

internal data class DspGraphMeteringSnapshot(
    val perStageMetersAvailable: Boolean = false,
    val source: DspMeterSource = DspMeterSource.Placeholder,
    val notes: String = "Phase 12B exposes meter fields only; no live per-stage polling is enabled.",
)

internal data class GraphicEqDiagnosticsSnapshot(
    val slots: List<GraphicEqSlotDiagnostics> = emptyList(),
    val expectedSlotCount: Int = GRAPHIC_EQ_BAND_COUNT,
    val canonical: Boolean = true,
    val enabled: Boolean = true,
    val rangeMode: GraphicEqRangeMode = GraphicEqRangeMode.Standard12Db,
    val requestedMinGainDb: Float = AUDIO_EQ_MIN_GAIN_DB,
    val requestedMaxGainDb: Float = AUDIO_EQ_MAX_GAIN_DB,
    val effectiveMinGainDb: Float = AUDIO_EQ_MIN_GAIN_DB,
    val effectiveMaxGainDb: Float = AUDIO_EQ_MAX_GAIN_DB,
    val extendedRangeRequested: Boolean = false,
    val extendedRangeEffective: Boolean = false,
    val activeSlotCount: Int = 0,
    val maxGainDb: Float = 0f,
    val maxEffectiveGainDb: Float = 0f,
    val headroomContributionDb: Float = 0f,
    val owner: DspStageOwner = DspStageOwner.None,
    val notes: List<String> = emptyList(),
)

internal data class GraphicEqSlotDiagnostics(
    val index: Int,
    val centerFrequencyHz: Float,
    val gainDb: Float,
    val effectiveGainDb: Float,
    val active: Boolean,
    val canonicalMapping: Boolean,
)

internal data class ParametricEqDiagnosticsSnapshot(
    val slots: List<ParametricEqSlotDiagnostics> = emptyList(),
    val expectedSlotCount: Int = PARAMETRIC_EQ_SLOT_COUNT,
    val canonical: Boolean = true,
    val nativeOnly: Boolean = true,
    val activeSlotCount: Int = 0,
    val allDisabledSlotsNeutral: Boolean = true,
    val owner: DspStageOwner = DspStageOwner.None,
    val notes: List<String> = emptyList(),
)

internal data class ParametricEqSlotDiagnostics(
    val index: Int,
    val enabled: Boolean,
    val active: Boolean,
    val neutralWhenDisabled: Boolean,
    val type: ParametricFilterType,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float,
    val validFrequencyRange: Boolean,
    val validGainRange: Boolean,
    val validQRange: Boolean,
)

internal data class ToneDiagnosticsSnapshot(
    val controls: List<ToneControlDiagnostics> = emptyList(),
    val active: Boolean = false,
    val owner: DspStageOwner = DspStageOwner.None,
    val maxBoostDb: Float = 0f,
    val headroomContributionDb: Float = 0f,
    val overlapsGraphicEq: Boolean = false,
    val platformBassBoostEligible: Boolean = false,
    val platformBassBoostExplicitlyOwned: Boolean = false,
    val platformEqualizerToneEligible: Boolean = false,
    val nativeToneEligible: Boolean = false,
    val savedInPresets: Boolean = false,
    val resetByFlatPreset: Boolean = true,
    val bypassed: Boolean = false,
    val safeModeDisabled: Boolean = false,
    val warnings: List<String> = emptyList(),
)

internal data class ToneControlDiagnostics(
    val id: ToneControlId,
    val label: String,
    val valueDb: Float,
    val neutralValueDb: Float = 0f,
    val minDb: Float,
    val maxDb: Float,
    val intendedFilterType: String,
    val owner: DspStageOwner,
    val active: Boolean,
    val overlapsGraphicEq: Boolean,
    val overlapsBassBoost: Boolean,
    val contributesToHeadroom: Boolean,
    val savedInPresets: Boolean,
    val resetByFlatPreset: Boolean,
)

internal data class SmartPreampRecommendation(
    val recommendedPreampDb: Float = 0f,
    val reason: SmartPreampReason = SmartPreampReason.Neutral,
    val eqBoostDb: Float = 0f,
    val toneBoostDb: Float = 0f,
    val replayGainBoostDb: Float = 0f,
    val dynamicsMakeupDb: Float = 0f,
    val timeFxRiskDb: Float = 0f,
    val targetHeadroomDb: Float = 0f,
    val preventClippingTrimDb: Float = 0f,
    val confidence: SmartPreampConfidence = SmartPreampConfidence.High,
)

internal data class LoudnessGroundworkSnapshot(
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val replayGainDb: Float = 0f,
    val replayGainBasis: ReplayGainBasis = ReplayGainBasis.None,
    val source: LoudnessSource = LoudnessSource.None,
    val confidence: LoudnessConfidence = LoudnessConfidence.Unknown,
    val measuredIntegratedLufs: Float? = null,
    val measurementStatus: LoudnessMeasurementStatus = LoudnessMeasurementStatus.NotMeasured,
    val estimatedForStream: Boolean = false,
    val truePeak: TruePeakGroundworkSnapshot = TruePeakGroundworkSnapshot(),
    val notes: List<String> = emptyList(),
)

internal data class TruePeakGroundworkSnapshot(
    val truePeakDbTp: Float? = null,
    val replayGainSamplePeak: Float? = null,
    val status: TruePeakStatus = TruePeakStatus.NotMeasured,
    val notes: String = "True-peak measurement is not implemented in Phase 14A.",
)

internal data class HeadroomManagerSnapshot(
    val contributions: List<HeadroomContribution> = emptyList(),
    val totalPositiveRiskDb: Float = 0f,
    val targetHeadroomDb: Float = 0f,
    val appliedPreventClippingTrimDb: Float = 0f,
    val outputTrimDb: Float = 0f,
    val clippingRisk: ClippingRisk = ClippingRisk.None,
    val confidence: SmartPreampConfidence = SmartPreampConfidence.High,
    val notes: List<String> = emptyList(),
)

internal data class HeadroomContribution(
    val stageId: DspStageId,
    val label: String,
    val boostDb: Float,
    val active: Boolean,
    val notes: String,
)

internal data class LookAheadLimiterGroundworkSnapshot(
    val implemented: Boolean = false,
    val active: Boolean = false,
    val owner: DspStageOwner = DspStageOwner.Unavailable,
    val lookAheadMs: Float = 0f,
    val currentProtection: LimiterProtectionMode = LimiterProtectionMode.None,
    val reason: String = "Look-ahead limiter is groundwork only in Phase 14B.",
)

internal enum class DspStageId {
    Input,
    SourceLoudness,
    AutoHeadroom,
    Preamp,
    ParametricEq,
    GraphicEq,
    Tone,
    StereoTools,
    Dynamics,
    TimeEffects,
    Limiter,
    OutputTrim,
    Output,
}

internal enum class DspStageOwner {
    None,
    Media3,
    NativeDsp,
    AndroidPlatformEffect,
    PlayerVolume,
    ModelOnly,
    SystemOutput,
    Unavailable,
}

internal enum class DspStageStatus {
    Transparent,
    Active,
    Bypassed,
    Disabled,
    SafeModeDisabled,
    Unsupported,
    Planned,
    Unknown,
}

internal enum class DspCostClass {
    None,
    Low,
    Medium,
    High,
    Unknown,
}

internal enum class DspMeterSource {
    Placeholder,
    ModelEstimate,
    NativeOutput,
    Unavailable,
}

internal enum class ToneControlId {
    Bass,
    Treble,
    VocalClarity,
    Loudness,
}

internal enum class SmartPreampReason {
    Neutral,
    EqBoost,
    ToneBoost,
    ReplayGainBoost,
    CombinedBoost,
    LimiterProtection,
    Unknown,
}

internal enum class SmartPreampConfidence {
    Low,
    Medium,
    High,
    Unknown,
}

internal enum class LoudnessMeasurementStatus {
    NotMeasured,
    FileTag,
    ScannerAnalysis,
    ReplayGainOnly,
    StreamMetadataNotMeasured,
    Unknown,
}

internal enum class TruePeakStatus {
    NotMeasured,
    PlaceholderOnly,
    ReplayGainSamplePeakOnly,
    Measured,
}

internal enum class LimiterProtectionMode {
    None,
    NativeSoftLimiter,
    LookAheadPlanned,
}

internal enum class ClippingRisk {
    None,
    Low,
    Medium,
    High,
    Unknown,
}

internal enum class DspGraphWarningLevel {
    Info,
    Caution,
}

internal data class DspGraphWarning(
    val title: String,
    val detail: String,
    val level: DspGraphWarningLevel = DspGraphWarningLevel.Info,
)

internal fun dspGraphSnapshot(
    state: AudioEngineState,
    diagnostics: OutputDiagnosticsSnapshot = OutputDiagnosticsSnapshot(),
    nativeAvailable: Boolean = false,
): DspGraphSnapshot {
    val policy = state.effectiveProcessingPolicy(nativeAvailable = nativeAvailable)
    val runtimeReported = diagnostics.updatedAtMillis > 0L
    val nativeRequested = if (runtimeReported) diagnostics.nativeMedia3DspRequested else policy.nativeMedia3DspRequested
    val nativeActive = if (runtimeReported) diagnostics.nativeMedia3DspActive else policy.nativeMedia3DspActive
    val replayGainDb = if (runtimeReported) diagnostics.replayGainAdjustmentDb else policy.replayGainAdjustmentDb
    val headroomDb = if (runtimeReported) diagnostics.estimatedHeadroomDb else policy.estimatedHeadroomDb
    val outputTrimDb = if (policy.processingActive) state.preampDb + replayGainDb + headroomDb else 0f
    val platformActive = platformSpectralEffectsActive(state, nativeActive)
    val pcmFormat = diagnostics.decoderFormat.toPcmFormatSummary()
    val graphContext = DspGraphContext(
        state = state,
        policy = policy,
        nativeAvailable = nativeAvailable,
        nativeRequested = nativeRequested,
        nativeActive = nativeActive,
        platformActive = platformActive,
        replayGainDb = replayGainDb,
        headroomDb = headroomDb,
        outputTrimDb = outputTrimDb,
        pcmFormat = pcmFormat,
        diagnostics = diagnostics,
    )
    val stages = buildDspGraphStages(graphContext).withPhase12bAnnotations(graphContext)
    val graphicEq = graphicEqDiagnostics(graphContext, stages.first { it.id == DspStageId.GraphicEq })
    val parametricEq = parametricEqDiagnostics(graphContext, stages.first { it.id == DspStageId.ParametricEq })
    val tone = toneDiagnostics(graphContext, stages.first { it.id == DspStageId.Tone })
    val smartPreamp = smartPreampRecommendation(graphContext, graphicEq, tone)
    val loudness = loudnessGroundwork(graphContext)
    val headroomManager = headroomManagerSnapshot(graphContext, graphicEq, tone, smartPreamp)
    val limiterGroundwork = lookAheadLimiterGroundwork(graphContext, stages.first { it.id == DspStageId.Limiter })
    return DspGraphSnapshot(
        stages = stages,
        processingActive = policy.processingActive,
        bypass = policy.bypass,
        safeMode = policy.safeMode,
        totalHeadroomDb = headroomDb,
        outputTrimDb = outputTrimDb,
        clippingRisk = clippingRiskForDb(if (policy.processingActive) state.maxBoostDb else 0f),
        bypassStates = stages.mapNotNull { it.bypassState },
        metering = DspGraphMeteringSnapshot(),
        graphicEq = graphicEq,
        parametricEq = parametricEq,
        tone = tone,
        smartPreamp = smartPreamp,
        loudness = loudness,
        headroomManager = headroomManager,
        limiterGroundwork = limiterGroundwork,
        warnings = dspGraphWarnings(graphContext, stages, graphicEq, parametricEq, tone, loudness, limiterGroundwork),
    )
}

private data class DspGraphContext(
    val state: AudioEngineState,
    val policy: EffectiveAudioProcessingPolicy,
    val nativeAvailable: Boolean,
    val nativeRequested: Boolean,
    val nativeActive: Boolean,
    val platformActive: Boolean,
    val replayGainDb: Float,
    val headroomDb: Float,
    val outputTrimDb: Float,
    val pcmFormat: AudioFormatSummary?,
    val diagnostics: OutputDiagnosticsSnapshot,
)

private fun buildDspGraphStages(context: DspGraphContext): List<DspGraphStage> =
    listOf(
        inputStage(context),
        sourceLoudnessStage(context),
        autoHeadroomStage(context),
        preampStage(context),
        parametricEqStage(context),
        graphicEqStage(context),
        toneStage(context),
        stereoToolsStage(context),
        dynamicsStage(context),
        timeEffectsStage(context),
        limiterStage(context),
        outputTrimStage(context),
        outputStage(context),
    )

private fun List<DspGraphStage>.withPhase12bAnnotations(context: DspGraphContext): List<DspGraphStage> =
    map { stage ->
        val bypassState = dspStageBypassState(stage, context)
        val meter = dspStageMeterSnapshot(stage)
        stage.copy(
            bypassed = bypassState.bypassed,
            bypassState = bypassState,
            meter = meter,
        )
    }

private fun dspStageBypassState(stage: DspGraphStage, context: DspGraphContext): DspStageBypassState {
    val userBypassable = stage.id in DSP_STAGE_BYPASS_READY_IDS
    val bypassed = stage.bypassed ||
        stage.status == DspStageStatus.Bypassed ||
        stage.status == DspStageStatus.SafeModeDisabled
    val reason = when {
        !userBypassable -> "Fixed path stage; no per-stage bypass target."
        context.policy.safeMode && stage.implemented -> "Safe mode neutralizes configured DSP stages."
        context.policy.bypass && stage.implemented -> "Global bypass neutralizes configured DSP stages."
        stage.status == DspStageStatus.Disabled -> "Engine off; saved stage settings are not applied."
        stage.status == DspStageStatus.Unsupported -> "Configured stage has no active owner on this path."
        !stage.implemented -> "Planned stage; no bypass behavior exists yet."
        else -> "Bypass-ready model only; Phase 12B does not change sound."
    }
    return DspStageBypassState(
        stageId = stage.id,
        userBypassable = userBypassable,
        bypassed = bypassed,
        reason = reason,
    )
}

private fun dspStageMeterSnapshot(stage: DspGraphStage): DspStageMeterSnapshot {
    val estimatedGain = stage.gainChangeDb ?: stage.headroomContributionDb
    val source = if (estimatedGain != null || stage.clippingRiskContribution != ClippingRisk.None) {
        DspMeterSource.ModelEstimate
    } else {
        DspMeterSource.Placeholder
    }
    return DspStageMeterSnapshot(
        stageId = stage.id,
        source = source,
        live = false,
        gainDeltaDb = estimatedGain,
        reason = if (source == DspMeterSource.ModelEstimate) {
            "Model-only gain/risk estimate; not a live meter."
        } else {
            "Per-stage meter placeholder; no real-time polling in Phase 12B."
        },
    )
}

private fun graphicEqDiagnostics(context: DspGraphContext, stage: DspGraphStage): GraphicEqDiagnosticsSnapshot {
    val expected = graphicEqCenterFrequenciesHz()
    val requestedRange = context.state.graphicEqRangeMode.requestedGainRange()
    val effectiveRange = context.state.effectiveGraphicEqGainRange(context.nativeActive)
    val extendedRequested = context.state.graphicEqRangeMode == GraphicEqRangeMode.Extended15DbWhenSupported
    val extendedEffective = effectiveRange.endInclusive > AUDIO_EQ_MAX_GAIN_DB
    val canonical = canonicalGraphicEqBands(context.state.eqBands, requestedRange)
    val inputCanonical = context.state.eqBands.size == expected.size &&
        context.state.eqBands.zip(expected).all { (band, expectedHz) -> abs(band.frequencyHz - expectedHz) <= DSP_GRAPH_EPSILON }
    val slots = canonical.mapIndexed { index, band ->
        val effectiveGain = audioBandGainDb(band.frequencyHz, context.state, effectiveRange)
        GraphicEqSlotDiagnostics(
            index = index,
            centerFrequencyHz = band.frequencyHz,
            gainDb = band.gainDb,
            effectiveGainDb = effectiveGain,
            active = context.state.eqEnabled && abs(band.gainDb) > DSP_GRAPH_EPSILON,
            canonicalMapping = abs(band.frequencyHz - expected[index]) <= DSP_GRAPH_EPSILON,
        )
    }
    val activeSlotCount = slots.count { it.active }
    val maxGainDb = if (context.state.eqEnabled) slots.maxOfOrNull { it.gainDb }?.coerceAtLeast(0f) ?: 0f else 0f
    val maxEffectiveGainDb = if (context.state.eqEnabled || context.state.toneControlsActive) {
        slots.maxOfOrNull { it.effectiveGainDb }?.coerceAtLeast(0f) ?: 0f
    } else {
        0f
    }
    return GraphicEqDiagnosticsSnapshot(
        slots = slots,
        expectedSlotCount = expected.size,
        canonical = inputCanonical && slots.all { it.canonicalMapping },
        enabled = context.state.eqEnabled,
        rangeMode = context.state.graphicEqRangeMode,
        requestedMinGainDb = requestedRange.start,
        requestedMaxGainDb = requestedRange.endInclusive,
        effectiveMinGainDb = effectiveRange.start,
        effectiveMaxGainDb = effectiveRange.endInclusive,
        extendedRangeRequested = extendedRequested,
        extendedRangeEffective = extendedEffective,
        activeSlotCount = activeSlotCount,
        maxGainDb = maxGainDb,
        maxEffectiveGainDb = maxEffectiveGainDb,
        headroomContributionDb = if (context.policy.preventClippingActive && maxEffectiveGainDb > DSP_GRAPH_EPSILON) -maxEffectiveGainDb else 0f,
        owner = stage.owner,
        notes = graphicEqOwnerNotes(context, stage.owner, expected.size, extendedRequested, extendedEffective),
    )
}

private fun graphicEqOwnerNotes(
    context: DspGraphContext,
    owner: DspStageOwner,
    expectedSlotCount: Int,
    extendedRequested: Boolean,
    extendedEffective: Boolean,
): List<String> =
    buildList {
        add("$expectedSlotCount canonical slots; slot 6 maps to 1 kHz.")
        when (owner) {
            DspStageOwner.NativeDsp -> add("PulseDeck Native DSP owns the canonical Graphic EQ slots.")
            DspStageOwner.AndroidPlatformEffect -> add("Android platform EQ is device/vendor band ownership; ten independent bands are not claimed.")
            DspStageOwner.ModelOnly -> add("Model-only state; no live DSP owner is active.")
            DspStageOwner.Unavailable -> add("Configured Graphic EQ has no active owner on this path.")
            else -> Unit
        }
        if (extendedRequested && extendedEffective) {
            add("Extended +/-15 dB range is active on verified PulseDeck Native DSP.")
        } else if (extendedRequested) {
            add("Extended +/-15 dB range is requested, but effective Graphic EQ range is +/-12 dB until Native DSP owns supported PCM.")
        } else {
            add("Standard +/-12 dB Graphic EQ range is active.")
        }
        context.policy.fallbackReason?.let { add(it) }
        add("Headroom uses current boost estimates; no LUFS or true-peak scan is added.")
    }

private fun parametricEqDiagnostics(context: DspGraphContext, stage: DspGraphStage): ParametricEqDiagnosticsSnapshot {
    val canonical = canonicalParametricEqBands(context.state.parametricEq)
    val inputCanonical = context.state.parametricEq.size == PARAMETRIC_EQ_SLOT_COUNT &&
        context.state.parametricEq.zip(canonical).all { (input, normalized) -> input == normalized }
    val slots = canonical.mapIndexed { index, band ->
        val active = band.isActiveFilter()
        ParametricEqSlotDiagnostics(
            index = index,
            enabled = band.enabled,
            active = active,
            neutralWhenDisabled = !band.enabled && !active,
            type = band.type,
            frequencyHz = band.frequencyHz,
            gainDb = band.gainDb,
            q = band.q,
            validFrequencyRange = band.frequencyHz in PARAMETRIC_EQ_MIN_FREQUENCY_HZ..PARAMETRIC_EQ_MAX_FREQUENCY_HZ,
            validGainRange = band.gainDb in AUDIO_EQ_MIN_GAIN_DB..AUDIO_EQ_MAX_GAIN_DB,
            validQRange = band.q in PARAMETRIC_EQ_MIN_Q..PARAMETRIC_EQ_MAX_Q,
        )
    }
    return ParametricEqDiagnosticsSnapshot(
        slots = slots,
        expectedSlotCount = PARAMETRIC_EQ_SLOT_COUNT,
        canonical = inputCanonical,
        nativeOnly = true,
        activeSlotCount = slots.count { it.active },
        allDisabledSlotsNeutral = slots.filter { !it.enabled }.all { it.neutralWhenDisabled },
        owner = stage.owner,
        notes = listOf(
            "$PARAMETRIC_EQ_SLOT_COUNT canonical native-only slots.",
            "Valid ranges: 20 Hz-20 kHz, +/-12 dB, Q 0.1-18.",
            "Disabled slots are kept neutral and are not sent as active filters.",
        ),
    )
}

private fun toneDiagnostics(context: DspGraphContext, stage: DspGraphStage): ToneDiagnosticsSnapshot {
    val toneOwner = stage.owner
    val controls = listOf(
        toneControlDiagnostics(
            id = ToneControlId.Bass,
            label = "Bass",
            valueDb = context.state.bassDb,
            intendedFilterType = "Low-shelf / low-band tilt",
            owner = toneOwner,
            overlapsGraphicEq = true,
            overlapsBassBoost = true,
            active = abs(context.state.bassDb) > DSP_GRAPH_EPSILON,
        ),
        toneControlDiagnostics(
            id = ToneControlId.Treble,
            label = "Treble",
            valueDb = context.state.trebleDb,
            intendedFilterType = "High-shelf / high-band tilt",
            owner = toneOwner,
            overlapsGraphicEq = true,
            overlapsBassBoost = false,
            active = abs(context.state.trebleDb) > DSP_GRAPH_EPSILON,
        ),
        toneControlDiagnostics(
            id = ToneControlId.VocalClarity,
            label = "Vocal Clarity",
            valueDb = context.state.vocalClarityDb,
            intendedFilterType = "Presence lift with low-mid counter-tilt",
            owner = toneOwner,
            overlapsGraphicEq = true,
            overlapsBassBoost = false,
            active = abs(context.state.vocalClarityDb) > DSP_GRAPH_EPSILON,
        ),
        toneControlDiagnostics(
            id = ToneControlId.Loudness,
            label = "Loudness",
            valueDb = context.state.loudnessDb,
            minDb = 0f,
            maxDb = AUDIO_EQ_MAX_GAIN_DB,
            intendedFilterType = "Playback-session loudness gain",
            owner = toneOwner,
            overlapsGraphicEq = false,
            overlapsBassBoost = false,
            active = context.state.loudnessDb > DSP_GRAPH_EPSILON,
        ),
    )
    val toneActive = stage.active
    val toneBoost = toneOnlyBoostDb(context.state)
    return ToneDiagnosticsSnapshot(
        controls = controls,
        active = toneActive,
        owner = toneOwner,
        maxBoostDb = toneBoost,
        headroomContributionDb = if (context.policy.preventClippingActive && toneBoost > DSP_GRAPH_EPSILON) -toneBoost else 0f,
        overlapsGraphicEq = controls.any { it.active && it.overlapsGraphicEq },
        platformBassBoostEligible = context.policy.platformBassBoostAllowed,
        platformBassBoostExplicitlyOwned = context.policy.platformBassBoostAllowed && !context.policy.platformEqAllowed && toneOwner == DspStageOwner.AndroidPlatformEffect,
        platformEqualizerToneEligible = context.policy.platformEqAllowed && toneOwner == DspStageOwner.AndroidPlatformEffect,
        nativeToneEligible = context.nativeActive,
        savedInPresets = false,
        resetByFlatPreset = true,
        bypassed = stage.status == DspStageStatus.Bypassed,
        safeModeDisabled = stage.status == DspStageStatus.SafeModeDisabled,
        warnings = buildList {
            if (toneBoost > DSP_GRAPH_EPSILON) add("Tone boost contributes to clipping risk and headroom estimates.")
            if (context.policy.platformBassBoostAllowed) add("Platform BassBoost response may vary by device.")
            if (toneActive) add("Tone active means a transparent/bit-perfect path is not possible.")
            if (stage.status == DspStageStatus.Bypassed) add("Tone bypassed: saved tone is preserved but not applied.")
        },
    )
}

private fun toneControlDiagnostics(
    id: ToneControlId,
    label: String,
    valueDb: Float,
    intendedFilterType: String,
    owner: DspStageOwner,
    overlapsGraphicEq: Boolean,
    overlapsBassBoost: Boolean,
    active: Boolean,
    minDb: Float = AUDIO_EQ_MIN_GAIN_DB,
    maxDb: Float = AUDIO_EQ_MAX_GAIN_DB,
): ToneControlDiagnostics =
    ToneControlDiagnostics(
        id = id,
        label = label,
        valueDb = valueDb.coerceIn(minDb, maxDb),
        minDb = minDb,
        maxDb = maxDb,
        intendedFilterType = intendedFilterType,
        owner = if (active) owner else DspStageOwner.None,
        active = active,
        overlapsGraphicEq = overlapsGraphicEq,
        overlapsBassBoost = overlapsBassBoost,
        contributesToHeadroom = active && valueDb > DSP_GRAPH_EPSILON,
        savedInPresets = false,
        resetByFlatPreset = true,
    )

private fun smartPreampRecommendation(
    context: DspGraphContext,
    graphicEq: GraphicEqDiagnosticsSnapshot,
    tone: ToneDiagnosticsSnapshot,
): SmartPreampRecommendation {
    val eqBoost = max(graphicEq.maxGainDb, parametricBoostDb(context.state)).coerceAtLeast(0f)
    val toneBoost = tone.maxBoostDb.coerceAtLeast(0f)
    val replayGainBoost = context.replayGainDb.coerceAtLeast(0f)
    val dynamicsBoost = dynamicsBoostDb(context.state).coerceAtLeast(0f)
    val timeBoost = timeFxBoostDb(context.state).coerceAtLeast(0f)
    val combined = listOf(eqBoost, toneBoost, replayGainBoost, dynamicsBoost, timeBoost)
        .filter { it > DSP_GRAPH_EPSILON }
        .sum()
    val reason = when {
        combined <= DSP_GRAPH_EPSILON -> SmartPreampReason.Neutral
        context.state.limiter.enabled && context.state.clippingRisk -> SmartPreampReason.LimiterProtection
        listOf(eqBoost, toneBoost, replayGainBoost, dynamicsBoost, timeBoost).count { it > DSP_GRAPH_EPSILON } > 1 -> SmartPreampReason.CombinedBoost
        eqBoost > DSP_GRAPH_EPSILON -> SmartPreampReason.EqBoost
        toneBoost > DSP_GRAPH_EPSILON -> SmartPreampReason.ToneBoost
        replayGainBoost > DSP_GRAPH_EPSILON -> SmartPreampReason.ReplayGainBoost
        else -> SmartPreampReason.Unknown
    }
    val targetHeadroom = -combined
    return SmartPreampRecommendation(
        recommendedPreampDb = targetHeadroom.coerceIn(AUDIO_EQ_MIN_GAIN_DB, 0f),
        reason = reason,
        eqBoostDb = eqBoost,
        toneBoostDb = toneBoost,
        replayGainBoostDb = replayGainBoost,
        dynamicsMakeupDb = dynamicsBoost,
        timeFxRiskDb = timeBoost,
        targetHeadroomDb = targetHeadroom,
        preventClippingTrimDb = context.headroomDb,
        confidence = SmartPreampConfidence.High,
    )
}

private fun loudnessGroundwork(context: DspGraphContext): LoudnessGroundworkSnapshot {
    val metadata = context.state.sourceLoudness
    val effective = context.state.effectiveReplayGain
    val measuredTruePeak = context.diagnostics.truePeak
    val measuredLufs = metadata.integratedLufs.takeIf {
        metadata.source == LoudnessSource.FileTag || metadata.source == LoudnessSource.ScannerAnalysis
    }
    val measurementStatus = when {
        measuredLufs != null && metadata.source == LoudnessSource.FileTag -> LoudnessMeasurementStatus.FileTag
        measuredLufs != null && metadata.source == LoudnessSource.ScannerAnalysis -> LoudnessMeasurementStatus.ScannerAnalysis
        metadata.source == LoudnessSource.StreamMetadata -> LoudnessMeasurementStatus.StreamMetadataNotMeasured
        metadata.hasGain || effective.basis != ReplayGainBasis.None -> LoudnessMeasurementStatus.ReplayGainOnly
        metadata.source == LoudnessSource.None -> LoudnessMeasurementStatus.NotMeasured
        else -> LoudnessMeasurementStatus.Unknown
    }
    val samplePeak = effective.peak ?: metadata.trackPeak ?: metadata.albumPeak
    val truePeak = if (measuredTruePeak.status == TruePeakMeasurementStatus.Measured && measuredTruePeak.truePeakDbTp != null) {
        TruePeakGroundworkSnapshot(
            truePeakDbTp = measuredTruePeak.truePeakDbTp,
            replayGainSamplePeak = samplePeak,
            status = TruePeakStatus.Measured,
            notes = "Pass-through ${measuredTruePeak.oversampleFactor}x PCM true-peak meter measured ${measuredTruePeak.framesMeasured} frame(s).",
        )
    } else {
        TruePeakGroundworkSnapshot(
            truePeakDbTp = null,
            replayGainSamplePeak = samplePeak,
            status = if (samplePeak != null) TruePeakStatus.ReplayGainSamplePeakOnly else TruePeakStatus.PlaceholderOnly,
            notes = if (samplePeak != null) {
                "ReplayGain sample peak is present, but live PCM true-peak measurement has not completed."
            } else {
                "True-peak measurement is pending or unsupported; no stream/radio loudness is invented."
            },
        )
    }
    return LoudnessGroundworkSnapshot(
        replayGainMode = context.state.replayGainMode,
        replayGainDb = context.replayGainDb,
        replayGainBasis = effective.basis,
        source = metadata.source,
        confidence = effective.confidence.takeUnless { it == LoudnessConfidence.Unknown } ?: metadata.confidence,
        measuredIntegratedLufs = measuredLufs,
        measurementStatus = measurementStatus,
        estimatedForStream = false,
        truePeak = truePeak,
        notes = buildList {
            if (measurementStatus == LoudnessMeasurementStatus.StreamMetadataNotMeasured) {
                add("Stream metadata is not reported as measured LUFS.")
            }
            add("LUFS scanner is still not implemented; true peak comes only from the supported live PCM meter.")
        },
    )
}

private fun headroomManagerSnapshot(
    context: DspGraphContext,
    graphicEq: GraphicEqDiagnosticsSnapshot,
    tone: ToneDiagnosticsSnapshot,
    smartPreamp: SmartPreampRecommendation,
): HeadroomManagerSnapshot {
    val contributions = listOf(
        HeadroomContribution(DspStageId.Preamp, "Preamp", context.state.preampDb.coerceAtLeast(0f), context.policy.processingActive && context.state.preampDb > DSP_GRAPH_EPSILON, "Positive preamp increases headroom demand."),
        HeadroomContribution(DspStageId.SourceLoudness, "ReplayGain", context.replayGainDb.coerceAtLeast(0f), context.policy.replayGainActive && context.replayGainDb > DSP_GRAPH_EPSILON, "Existing ReplayGain adjustment only; metadata behavior is unchanged."),
        HeadroomContribution(DspStageId.GraphicEq, "Graphic EQ", graphicEq.maxGainDb, graphicEq.activeSlotCount > 0, "Canonical 10-band boost contribution."),
        HeadroomContribution(DspStageId.ParametricEq, "Parametric EQ", parametricBoostDb(context.state), context.state.parametricEqStageActive, "Canonical 4-slot native-only boost contribution."),
        HeadroomContribution(DspStageId.Tone, "Tone", tone.maxBoostDb, tone.active, "Bass/treble/vocal/loudness tone boost contribution."),
        HeadroomContribution(DspStageId.Dynamics, "Dynamics", dynamicsBoostDb(context.state), context.state.dynamicsStageActive, "Compressor makeup and dynamics risk contribution."),
        HeadroomContribution(DspStageId.TimeEffects, "Time Effects", timeFxBoostDb(context.state), context.state.timeFxStageActive, "Delay/reverb/modulation mix risk contribution."),
    ).map { contribution ->
        contribution.copy(boostDb = contribution.boostDb.coerceAtLeast(0f))
    }
    return HeadroomManagerSnapshot(
        contributions = contributions,
        totalPositiveRiskDb = contributions.sumOf { it.boostDb.toDouble() }.toFloat(),
        targetHeadroomDb = smartPreamp.targetHeadroomDb,
        appliedPreventClippingTrimDb = context.headroomDb,
        outputTrimDb = context.outputTrimDb,
        clippingRisk = clippingRiskForDb(if (context.policy.processingActive) context.state.maxBoostDb else 0f),
        confidence = SmartPreampConfidence.High,
        notes = listOf(
            "Headroom manager is diagnostic groundwork in Phase 14B.",
            "Existing prevent-clipping trim remains the only applied automatic headroom behavior.",
        ),
    )
}

private fun lookAheadLimiterGroundwork(
    context: DspGraphContext,
    limiterStage: DspGraphStage,
): LookAheadLimiterGroundworkSnapshot =
    LookAheadLimiterGroundworkSnapshot(
        implemented = false,
        active = false,
        owner = if (limiterStage.active) limiterStage.owner else DspStageOwner.Unavailable,
        lookAheadMs = 0f,
        currentProtection = when {
            limiterStage.active -> LimiterProtectionMode.NativeSoftLimiter
            context.state.limiter.enabled && context.state.clippingRisk -> LimiterProtectionMode.LookAheadPlanned
            else -> LimiterProtectionMode.None
        },
        reason = if (limiterStage.active) {
            "Existing native soft limiter may be active; no look-ahead limiter is implemented."
        } else {
            "Look-ahead limiter is not active; Phase 14B only models readiness and headroom context."
        },
    )

private fun inputStage(context: DspGraphContext): DspGraphStage =
    DspGraphStage(
        id = DspStageId.Input,
        label = "Input PCM",
        order = DspStageId.Input.ordinal,
        implemented = true,
        active = true,
        bypassed = false,
        owner = DspStageOwner.Media3,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        status = DspStageStatus.Transparent,
        notes = "Decoded PCM entering PulseDeck's DSP policy model.",
    )

private fun sourceLoudnessStage(context: DspGraphContext): DspGraphStage {
    val configured = context.state.replayGainMode != ReplayGainMode.Off
    val active = context.policy.processingActive && configured && abs(context.replayGainDb) > DSP_GRAPH_EPSILON
    return DspGraphStage(
        id = DspStageId.SourceLoudness,
        label = "Source Gain / ReplayGain",
        order = DspStageId.SourceLoudness.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = gainOwner(context, configured),
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = context.replayGainDb.takeIf { configured },
        clippingRiskContribution = clippingRiskForDb(context.replayGainDb.takeIf { active } ?: 0f),
        cpuCost = DspCostClass.None,
        status = stageStatus(context, configured, active, gainOwner(context, configured)),
        notes = "ReplayGain is represented as player-volume gain; tags and scanner behavior are unchanged.",
    )
}

private fun autoHeadroomStage(context: DspGraphContext): DspGraphStage {
    val configured = context.state.preventClipping && context.state.maxBoostDb > DSP_GRAPH_EPSILON
    val active = context.policy.preventClippingActive
    return DspGraphStage(
        id = DspStageId.AutoHeadroom,
        label = "Auto Headroom",
        order = DspStageId.AutoHeadroom.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = gainOwner(context, configured),
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = context.headroomDb.takeIf { configured },
        headroomContributionDb = context.headroomDb.takeIf { configured },
        clippingRiskContribution = ClippingRisk.None,
        cpuCost = DspCostClass.None,
        status = stageStatus(context, configured, active, gainOwner(context, configured)),
        notes = "Estimated headroom from current boost settings; this is not a true-peak meter.",
    )
}

private fun preampStage(context: DspGraphContext): DspGraphStage {
    val configured = abs(context.state.preampDb) > DSP_GRAPH_EPSILON
    val active = context.policy.processingActive && configured
    val owner = gainOwner(context, configured)
    val nativeConstraint = nativeMasterGainConstraint(context.state, context.state.native.masterGain)
    val notes = when {
        context.nativeActive && nativeConstraint.constrained ->
            "Preamp/output trim is applied through native master gain; requested ${formatGraphDb(nativeConstraint.requestedDb)} is constrained to ${formatGraphDb(nativeConstraint.effectiveDb)} by the verified native ceiling."
        context.nativeActive ->
            "Preamp/output trim is applied through native master gain within the verified native ceiling."
        else ->
            "Preamp is currently folded into the final player-volume trim."
    }
    return DspGraphStage(
        id = DspStageId.Preamp,
        label = "Preamp",
        order = DspStageId.Preamp.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = owner,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = context.state.preampDb.takeIf { configured },
        clippingRiskContribution = clippingRiskForDb(context.state.preampDb.takeIf { active } ?: 0f),
        cpuCost = DspCostClass.None,
        status = stageStatus(context, configured, active, owner),
        notes = notes,
    )
}

private fun parametricEqStage(context: DspGraphContext): DspGraphStage {
    val configured = context.state.parametricEq.any { it.isActiveFilter() }
    val owner = nativeOnlyOwner(context, configured)
    val active = context.policy.processingActive && configured && owner == DspStageOwner.NativeDsp
    return DspGraphStage(
        id = DspStageId.ParametricEq,
        label = "Parametric EQ",
        order = DspStageId.ParametricEq.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = owner,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = parametricBoostDb(context.state).takeIf { configured },
        clippingRiskContribution = clippingRiskForDb(parametricBoostDb(context.state).takeIf { active } ?: 0f),
        cpuCost = DspCostClass.Low,
        status = stageStatus(context, configured, active, owner),
        notes = "Native Media3 PCM DSP owns active parametric filters.",
    )
}

private fun graphicEqStage(context: DspGraphContext): DspGraphStage {
    val configured = context.state.graphicEqStageActive
    val owner = spectralOwner(context, configured)
    val active = context.policy.processingActive && configured && owner != DspStageOwner.Unavailable
    val gain = graphicEqBoostDb(context.state, context.state.effectiveGraphicEqGainRange(context.nativeActive))
    return DspGraphStage(
        id = DspStageId.GraphicEq,
        label = "Graphic EQ",
        order = DspStageId.GraphicEq.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = owner,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = gain.takeIf { configured },
        clippingRiskContribution = clippingRiskForDb(gain.takeIf { active } ?: 0f),
        cpuCost = DspCostClass.Low,
        status = stageStatus(context, configured, active, owner),
        notes = "Graphic EQ uses native DSP when active, otherwise Android session EQ can own supported spectral changes.",
    )
}

private fun toneStage(context: DspGraphContext): DspGraphStage {
    val configured = context.state.toneControlsActive || context.state.loudnessStageActive
    val owner = spectralOwner(context, configured)
    val active = context.policy.processingActive && configured && owner != DspStageOwner.Unavailable
    val gain = toneOnlyBoostDb(context.state)
    return DspGraphStage(
        id = DspStageId.Tone,
        label = "Tone",
        order = DspStageId.Tone.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = owner,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = gain.takeIf { configured },
        clippingRiskContribution = clippingRiskForDb(gain.takeIf { active } ?: 0f),
        cpuCost = DspCostClass.Low,
        status = stageStatus(context, configured, active, owner),
        diagnostics = toneStageDiagnostics(context, owner, gain),
        notes = "Bass, treble, vocal clarity, and loudness are grouped here; Phase 13C reports tone separately from graphic EQ.",
    )
}

private fun stereoToolsStage(context: DspGraphContext): DspGraphStage {
    val configured = context.state.stereoMatrixActive
    val owner = when {
        !configured -> DspStageOwner.None
        !context.policy.processingActive -> DspStageOwner.ModelOnly
        context.nativeActive -> DspStageOwner.NativeDsp
        context.policy.platformVirtualizerAllowed -> DspStageOwner.AndroidPlatformEffect
        else -> DspStageOwner.Unavailable
    }
    val active = context.policy.processingActive && configured && owner != DspStageOwner.Unavailable
    val gain = stereoBoostDb(context.state)
    return DspGraphStage(
        id = DspStageId.StereoTools,
        label = "Stereo Tools",
        order = DspStageId.StereoTools.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = owner,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = gain.takeIf { configured && gain > DSP_GRAPH_EPSILON },
        clippingRiskContribution = clippingRiskForDb(gain.takeIf { active } ?: 0f),
        cpuCost = DspCostClass.Low,
        status = stageStatus(context, configured, active, owner),
        notes = "Balance, mono, width, and crossfeed are native-owned; Android virtualizer may cover width only.",
    )
}

private fun dynamicsStage(context: DspGraphContext): DspGraphStage {
    val configured = context.state.compressorStageActive || context.state.gateStageActive
    val owner = nativeOnlyOwner(context, configured)
    val active = context.policy.processingActive && configured && owner == DspStageOwner.NativeDsp
    val gain = dynamicsBoostDb(context.state)
    return DspGraphStage(
        id = DspStageId.Dynamics,
        label = "Dynamics",
        order = DspStageId.Dynamics.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = owner,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = gain.takeIf { configured && gain > DSP_GRAPH_EPSILON },
        clippingRiskContribution = clippingRiskForDb(gain.takeIf { active } ?: 0f),
        cpuCost = DspCostClass.Medium,
        status = stageStatus(context, configured, active, owner),
        notes = "Compressor and gate are reported separately from limiter/peak safety.",
    )
}

private fun timeEffectsStage(context: DspGraphContext): DspGraphStage {
    val nativeConfigured = context.state.timeFxStageActive
    val media3Configured = context.state.pitchTempoActive
    val configured = nativeConfigured || media3Configured
    val owner = when {
        !configured -> DspStageOwner.None
        !context.policy.processingActive -> DspStageOwner.ModelOnly
        nativeConfigured && context.nativeActive -> DspStageOwner.NativeDsp
        nativeConfigured && !context.nativeActive -> DspStageOwner.Unavailable
        media3Configured -> DspStageOwner.Media3
        else -> DspStageOwner.None
    }
    val active = context.policy.processingActive && configured && owner != DspStageOwner.Unavailable
    val gain = timeFxBoostDb(context.state)
    return DspGraphStage(
        id = DspStageId.TimeEffects,
        label = "Time Effects",
        order = DspStageId.TimeEffects.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = owner,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = gain.takeIf { configured && gain > DSP_GRAPH_EPSILON },
        clippingRiskContribution = clippingRiskForDb(gain.takeIf { active } ?: 0f),
        cpuCost = DspCostClass.Medium,
        status = stageStatus(context, configured, active, owner),
        notes = "Delay/reverb/modulation are native-owned; tempo and pitch use Media3 playback parameters.",
    )
}

private fun limiterStage(context: DspGraphContext): DspGraphStage {
    val configured = context.state.limiter.enabled && context.state.clippingRisk
    val owner = nativeOnlyOwner(context, configured)
    val active = context.policy.processingActive && configured && owner == DspStageOwner.NativeDsp
    return DspGraphStage(
        id = DspStageId.Limiter,
        label = "Limiter / Peak Safety",
        order = DspStageId.Limiter.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = owner,
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        clippingRiskContribution = if (configured) clippingRiskForDb(context.state.maxBoostDb) else ClippingRisk.None,
        cpuCost = DspCostClass.Medium,
        status = stageStatus(context, configured, active, owner),
        notes = "Limiter is native-path peak safety; Phase 12A does not add meters or change limiter behavior.",
    )
}

private fun outputTrimStage(context: DspGraphContext): DspGraphStage {
    val configured = abs(context.outputTrimDb) > DSP_GRAPH_EPSILON
    val active = context.policy.processingActive && configured
    return DspGraphStage(
        id = DspStageId.OutputTrim,
        label = "Output Trim",
        order = DspStageId.OutputTrim.ordinal,
        implemented = true,
        active = active,
        bypassed = configured && !context.policy.processingActive,
        owner = gainOwner(context, configured),
        inputFormat = context.pcmFormat,
        outputFormat = context.pcmFormat,
        gainChangeDb = context.outputTrimDb.takeIf { configured },
        clippingRiskContribution = ClippingRisk.None,
        cpuCost = DspCostClass.None,
        status = stageStatus(context, configured, active, gainOwner(context, configured)),
        notes = "Final player-volume trim combines ReplayGain, preamp, and auto headroom.",
    )
}

private fun outputStage(context: DspGraphContext): DspGraphStage =
    DspGraphStage(
        id = DspStageId.Output,
        label = "Android Output",
        order = DspStageId.Output.ordinal,
        implemented = true,
        active = true,
        bypassed = false,
        owner = DspStageOwner.SystemOutput,
        inputFormat = context.pcmFormat,
        outputFormat = AudioFormatSummary("System-managed", source = "Android output route"),
        status = DspStageStatus.Transparent,
        notes = "Android owns final route, device rate, and AudioTrack output.",
    )

private fun gainOwner(context: DspGraphContext, configured: Boolean): DspStageOwner =
    when {
        !configured -> DspStageOwner.None
        context.nativeActive -> DspStageOwner.NativeDsp
        context.policy.processingActive -> DspStageOwner.PlayerVolume
        else -> DspStageOwner.ModelOnly
    }

private fun spectralOwner(context: DspGraphContext, configured: Boolean): DspStageOwner =
    when {
        !configured -> DspStageOwner.None
        !context.policy.processingActive -> DspStageOwner.ModelOnly
        context.nativeActive -> DspStageOwner.NativeDsp
        context.policy.platformEqAllowed ||
            context.policy.platformBassBoostAllowed ||
            context.policy.platformLoudnessAllowed -> DspStageOwner.AndroidPlatformEffect
        else -> DspStageOwner.Unavailable
    }

private fun nativeOnlyOwner(context: DspGraphContext, configured: Boolean): DspStageOwner =
    when {
        !configured -> DspStageOwner.None
        !context.policy.processingActive -> DspStageOwner.ModelOnly
        context.nativeActive -> DspStageOwner.NativeDsp
        else -> DspStageOwner.Unavailable
    }

private fun stageStatus(
    context: DspGraphContext,
    configured: Boolean,
    active: Boolean,
    owner: DspStageOwner,
    implemented: Boolean = true,
): DspStageStatus =
    when {
        !implemented -> DspStageStatus.Planned
        configured && context.policy.safeMode -> DspStageStatus.SafeModeDisabled
        configured && context.policy.bypass -> DspStageStatus.Bypassed
        configured && !context.policy.engineEnabled -> DspStageStatus.Disabled
        configured && owner == DspStageOwner.Unavailable -> DspStageStatus.Unsupported
        active -> DspStageStatus.Active
        else -> DspStageStatus.Transparent
    }

private fun dspGraphWarnings(
    context: DspGraphContext,
    stages: List<DspGraphStage>,
    graphicEq: GraphicEqDiagnosticsSnapshot,
    parametricEq: ParametricEqDiagnosticsSnapshot,
    tone: ToneDiagnosticsSnapshot,
    loudness: LoudnessGroundworkSnapshot,
    limiterGroundwork: LookAheadLimiterGroundworkSnapshot,
): List<DspGraphWarning> =
    buildList {
        if (context.policy.bypass) {
            add(DspGraphWarning("Bypass active", "Configured DSP stages are shown but not applied."))
        }
        if (context.policy.safeMode) {
            add(DspGraphWarning("Safe mode active", "Safe mode disables DSP processing and keeps the path transparent."))
        }
        context.policy.fallbackReason?.let {
            add(DspGraphWarning("Native DSP fallback", it, DspGraphWarningLevel.Caution))
        }
        if (stages.any { it.status == DspStageStatus.Unsupported }) {
            add(DspGraphWarning("Native-only stage unavailable", "One or more configured stages require native DSP ownership that is not active.", DspGraphWarningLevel.Caution))
        }
        if (context.platformActive) {
            add(DspGraphWarning("Android platform effect ownership", "Some spectral stages are owned by Android session effects on this path; platform EQ bands are device/vendor-defined and are not reported as ten independent PulseDeck bands."))
        }
        if (graphicEq.extendedRangeRequested && !graphicEq.extendedRangeEffective) {
            add(DspGraphWarning("Graphic EQ extended range inactive", "Extended +/-15 dB is requested, but the effective range is +/-12 dB until PulseDeck Native DSP owns supported decoded PCM.", DspGraphWarningLevel.Caution))
        }
        if (context.nativeActive) {
            val nativeConstraint = nativeMasterGainConstraint(context.state, context.state.native.masterGain)
            if (nativeConstraint.constrained) {
                add(DspGraphWarning("Native preamp ceiling", "Requested native output trim is ${formatGraphDb(nativeConstraint.requestedDb)}, but the verified native master-gain ceiling is ${formatGraphDb(nativeConstraint.effectiveDb)}.", DspGraphWarningLevel.Caution))
            }
        }
        if (context.policy.preventClippingActive) {
            add(DspGraphWarning("Auto headroom active", "Estimated headroom is reducing output trim by ${formatGraphDb(context.headroomDb)}."))
        }
        if (!graphicEq.canonical) {
            add(DspGraphWarning("Graphic EQ slot normalization", "The graph reports canonical 10-band slots even when incoming state needs normalization.", DspGraphWarningLevel.Caution))
        }
        if (!parametricEq.canonical) {
            add(DspGraphWarning("Parametric EQ slot normalization", "The graph reports canonical 4-slot parametric state even when incoming state needs normalization.", DspGraphWarningLevel.Caution))
        }
        if (tone.maxBoostDb > DSP_GRAPH_EPSILON) {
            add(DspGraphWarning("Tone headroom", "Tone boost contributes to clipping risk and the smart preamp/headroom model."))
        }
        if (tone.platformBassBoostEligible) {
            add(DspGraphWarning("Platform BassBoost variance", "Android BassBoost may vary by device and is represented as platform ownership only when eligible."))
        }
        if (loudness.measurementStatus == LoudnessMeasurementStatus.StreamMetadataNotMeasured) {
            add(DspGraphWarning("Stream loudness not measured", "PulseDeck is not inventing LUFS or true-peak values for streams/radio."))
        }
        if (loudness.truePeak.status != TruePeakStatus.NotMeasured) {
            add(
                DspGraphWarning(
                    if (loudness.truePeak.status == TruePeakStatus.Measured) "True peak measured" else "True peak pending",
                    loudness.truePeak.notes,
                ),
            )
        }
        if (!limiterGroundwork.implemented && context.state.clippingRisk) {
            add(DspGraphWarning("Look-ahead limiter groundwork", "No look-ahead limiter is active; existing headroom/soft limiting status is reported separately."))
        }
        add(DspGraphWarning("Phase 12B groundwork", "Per-stage bypass and meter fields are modeled; no per-stage bypass control or live per-stage meter changes sound in this phase."))
        add(DspGraphWarning("Batch B groundwork", "Tone, loudness, smart preamp, and look-ahead limiter fields remain diagnostic unless runtime diagnostics prove an active path."))
    }

private fun com.pulsedeck.app.settings.runtime.DecoderFormatDiagnosticsSnapshot.toPcmFormatSummary(): AudioFormatSummary? {
    if (updatedAtMillis <= 0L && decodedPcmEncoding.isNullOrBlank() && decodedSampleRateHz == null && decodedChannelCount == null) {
        return null
    }
    return AudioFormatSummary(
        encoding = decodedPcmEncoding ?: "PCM",
        sampleRateHz = decodedSampleRateHz,
        channels = decodedChannelCount,
        source = if (updatedAtMillis > 0L) "Media3 decoder" else "Unknown",
    )
}

private fun graphicEqBoostDb(state: AudioEngineState, graphicEqGainRange: ClosedFloatingPointRange<Float> = standardGraphicEqGainRange()): Float =
    if (state.graphicEqStageActive) {
        state.eqBands.maxOfOrNull { audioBandGainDb(it.frequencyHz, state, graphicEqGainRange) }?.coerceAtLeast(0f) ?: 0f
    } else {
        0f
    }

private fun parametricBoostDb(state: AudioEngineState): Float =
    state.parametricEq
        .filter { it.isActiveFilter() }
        .maxOfOrNull { band ->
            when (band.type) {
                ParametricFilterType.LowPass,
                ParametricFilterType.HighPass,
                ParametricFilterType.Notch -> 0f
                ParametricFilterType.Peak,
                ParametricFilterType.LowShelf,
                ParametricFilterType.HighShelf -> band.gainDb.coerceAtLeast(0f)
            }
        } ?: 0f

private fun toneOnlyBoostDb(state: AudioEngineState): Float =
    max(
        if (state.toneControlsActive) {
            graphicEqCenterFrequenciesHz().maxOfOrNull { toneOnlyBandGainDb(it, state) }?.coerceAtLeast(0f) ?: 0f
        } else {
            0f
        },
        if (state.loudnessStageActive) state.loudnessDb.coerceAtLeast(0f) else 0f,
    )

private fun toneOnlyBandGainDb(frequencyHz: Float, state: AudioEngineState): Float {
    val centerHz = frequencyHz.coerceIn(PARAMETRIC_EQ_MIN_FREQUENCY_HZ, PARAMETRIC_EQ_MAX_FREQUENCY_HZ)
    val bassTone = when {
        centerHz <= 125f -> state.bassDb
        centerHz <= 250f -> state.bassDb * 0.55f
        else -> 0f
    }
    val trebleTone = when {
        centerHz >= 8000f -> state.trebleDb
        centerHz >= 4000f -> state.trebleDb * 0.65f
        else -> 0f
    }
    val vocalTone = when {
        centerHz in 1000f..4000f -> state.vocalClarityDb
        centerHz in 250f..750f -> -state.vocalClarityDb * 0.35f
        else -> 0f
    }
    return (bassTone + trebleTone + vocalTone).coerceIn(AUDIO_EQ_MIN_GAIN_DB, AUDIO_EQ_MAX_GAIN_DB)
}

private fun toneStageDiagnostics(context: DspGraphContext, owner: DspStageOwner, toneBoostDb: Float): List<String> =
    buildList {
        add("tone max ${formatGraphDb(toneBoostDb)}")
        if (context.policy.platformEqAllowed && owner == DspStageOwner.AndroidPlatformEffect) add("platform EQ tone path")
        if (context.policy.platformBassBoostAllowed) add("BassBoost eligible")
        if (context.nativeActive) add("native spectral owner")
        if (context.policy.preventClippingActive && toneBoostDb > DSP_GRAPH_EPSILON) add("headroom ${formatGraphDb(-toneBoostDb)}")
    }

private fun dynamicsBoostDb(state: AudioEngineState): Float =
    if (state.compressorStageActive) {
        max(0f, state.compressor.makeupDb) * state.compressor.mix.coerceIn(0f, 1f)
    } else {
        0f
    }

private fun stereoBoostDb(state: AudioEngineState): Float =
    if (state.stereoMatrixActive) {
        linearToDb(1f + 0.75f * state.stereo.stereoWidth.coerceIn(0f, 1f)).coerceAtLeast(0f)
    } else {
        0f
    }

private fun timeFxBoostDb(state: AudioEngineState): Float =
    listOf(
        if (state.delayStageActive) state.delay.mix.coerceIn(0f, 1f) * (1f + state.delay.feedback.coerceIn(0f, 0.92f)) * 3f else 0f,
        if (state.reverbStageActive) state.reverb.mix.coerceIn(0f, 1f) * (1f + state.reverb.size.coerceIn(0f, 1f) * 0.5f + state.reverb.decay.coerceIn(0.2f, 6f) / 12f) * 3f else 0f,
        if (state.modulationStageActive) state.modulation.mix.coerceIn(0f, 1f) *
            (1f + state.modulation.feedback.coerceIn(0f, 0.9f)) *
            state.modulation.depth.coerceIn(0f, 1f) *
            3f else 0f,
    ).sum()

private fun clippingRiskForDb(boostDb: Float): ClippingRisk =
    when {
        boostDb <= 0.75f -> ClippingRisk.None
        boostDb < 6f -> ClippingRisk.Low
        boostDb < 12f -> ClippingRisk.Medium
        else -> ClippingRisk.High
    }

internal fun formatGraphDb(value: Float): String =
    (if (value > 0f) "+" else "") + String.format(Locale.US, "%.1f dB", value)

private val DSP_STAGE_BYPASS_READY_IDS = setOf(
    DspStageId.SourceLoudness,
    DspStageId.AutoHeadroom,
    DspStageId.Preamp,
    DspStageId.ParametricEq,
    DspStageId.GraphicEq,
    DspStageId.Tone,
    DspStageId.StereoTools,
    DspStageId.Dynamics,
    DspStageId.TimeEffects,
    DspStageId.Limiter,
    DspStageId.OutputTrim,
)

private const val DSP_GRAPH_EPSILON = 0.05f
