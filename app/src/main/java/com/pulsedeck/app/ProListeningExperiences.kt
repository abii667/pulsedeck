package com.pulsedeck.app

import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeStatus
import com.pulsedeck.app.settings.runtime.TruePeakMeasurementStatus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class ProListeningExperienceId {
    StudioReference,
    CleanBass,
    VocalFocus,
    NightDetail,
    RoadEnergy,
    HeadphoneSpace,
}

internal enum class ProListeningIntensity(val label: String) {
    Gentle("Gentle"),
    Balanced("Balanced"),
    Vivid("Vivid"),
}

internal enum class ProListeningSafetyStatus(val label: String) {
    Transparent("Transparent"),
    ClippingProtected("Clipping protected"),
    RouteCaution("Route caution"),
    CompatibilityBlocked("Compatibility blocked"),
}

internal enum class ProListeningRouteFit(val label: String) {
    Recommended("Recommended"),
    Good("Good"),
    Caution("Caution"),
}

internal data class ProListeningExperience(
    val id: ProListeningExperienceId,
    val title: String,
    val tagline: String,
    val bestFor: List<DeviceProfile>,
    val intensity: ProListeningIntensity,
    val safety: String,
    val proAudioSummary: String,
)

internal data class ProListeningExperienceChange(
    val stage: String,
    val before: String,
    val after: String,
)

internal data class ProListeningRouteRecommendation(
    val experience: ProListeningExperience,
    val routeLabel: String,
    val reason: String,
    val fit: ProListeningRouteFit,
)

internal data class ProListeningExperiencePlan(
    val experience: ProListeningExperience,
    val originalState: AudioEngineState,
    val previewState: AudioEngineState,
    val changes: List<ProListeningExperienceChange>,
    val recommendation: ProListeningRouteRecommendation,
    val routeFit: ProListeningRouteFit,
    val safetyStatus: ProListeningSafetyStatus,
    val routeWarnings: List<String>,
    val compatibilityNotes: List<String>,
    val proAudioInfo: String,
    val truePeakSafety: String,
    val headroomDb: Float,
    val outputGainPercent: Int,
)

internal val proListeningExperiences: List<ProListeningExperience> = listOf(
    ProListeningExperience(
        id = ProListeningExperienceId.StudioReference,
        title = "Studio Reference",
        tagline = "Transparent listening with processing kept neutral",
        bestFor = listOf(DeviceProfile.Wired, DeviceProfile.UsbDac, DeviceProfile.Speaker),
        intensity = ProListeningIntensity.Gentle,
        safety = "Transparent path",
        proAudioSummary = "Flat EQ, parametric slots disabled, tone and dynamics off.",
    ),
    ProListeningExperience(
        id = ProListeningExperienceId.CleanBass,
        title = "Clean Bass",
        tagline = "Deep bass without muddy distortion",
        bestFor = listOf(DeviceProfile.Wired, DeviceProfile.Bluetooth, DeviceProfile.Car),
        intensity = ProListeningIntensity.Balanced,
        safety = "Clipping protected",
        proAudioSummary = "Controlled low shelf, low-mid cleanup, and automatic headroom.",
    ),
    ProListeningExperience(
        id = ProListeningExperienceId.VocalFocus,
        title = "Vocal Focus",
        tagline = "Clear voices with less low-mid haze",
        bestFor = listOf(DeviceProfile.Speaker, DeviceProfile.Wired, DeviceProfile.Bluetooth),
        intensity = ProListeningIntensity.Balanced,
        safety = "Clipping protected",
        proAudioSummary = "Presence lift, mild low cleanup, and ReplayGain track matching.",
    ),
    ProListeningExperience(
        id = ProListeningExperienceId.NightDetail,
        title = "Night Detail",
        tagline = "Lower peaks, softer bass, and readable detail",
        bestFor = listOf(DeviceProfile.Wired, DeviceProfile.Speaker),
        intensity = ProListeningIntensity.Gentle,
        safety = "Peak cautious",
        proAudioSummary = "Lower preamp, modest detail lift, and gentle existing compressor.",
    ),
    ProListeningExperience(
        id = ProListeningExperienceId.RoadEnergy,
        title = "Road Energy",
        tagline = "Punch and presence for noisy routes",
        bestFor = listOf(DeviceProfile.Car, DeviceProfile.Bluetooth),
        intensity = ProListeningIntensity.Vivid,
        safety = "Clipping protected",
        proAudioSummary = "Bass/presence contour with conservative dynamics and headroom.",
    ),
    ProListeningExperience(
        id = ProListeningExperienceId.HeadphoneSpace,
        title = "Headphone Space",
        tagline = "Easy headphone width with gentle crossfeed",
        bestFor = listOf(DeviceProfile.Wired, DeviceProfile.UsbDac),
        intensity = ProListeningIntensity.Balanced,
        safety = "Clipping protected",
        proAudioSummary = "Small tonal contour, crossfeed, and conservative headroom.",
    ),
)

internal fun proListeningExperienceFor(id: ProListeningExperienceId): ProListeningExperience =
    proListeningExperiences.first { it.id == id }

internal fun proListeningExperiencePlan(
    state: AudioEngineState,
    experienceId: ProListeningExperienceId,
    diagnostics: OutputDiagnosticsSnapshot = OutputDiagnosticsSnapshot(),
): ProListeningExperiencePlan {
    val experience = proListeningExperienceFor(experienceId)
    val previewState = applyProListeningExperience(state, experienceId)
    val recommendation = recommendedProListeningExperience(state, diagnostics)
    val routeFit = routeFitFor(experience, currentDeviceProfile(state, diagnostics))
    val routeWarnings = routeWarningsFor(state, previewState, diagnostics)
    val compatibilityNotes = compatibilityNotesFor(previewState, diagnostics)
    val policy = previewState.effectiveProcessingPolicy(nativeAvailable = false)
    val headroomDb = policy.estimatedHeadroomDb
    return ProListeningExperiencePlan(
        experience = experience,
        originalState = state,
        previewState = previewState,
        changes = changesFor(state, previewState),
        recommendation = recommendation,
        routeFit = routeFit,
        safetyStatus = safetyStatusFor(previewState, routeWarnings, compatibilityNotes),
        routeWarnings = routeWarnings,
        compatibilityNotes = compatibilityNotes,
        proAudioInfo = proAudioInfoFor(previewState),
        truePeakSafety = truePeakSafetyLine(diagnostics),
        headroomDb = headroomDb,
        outputGainPercent = (audioOutputGain(previewState, 1f) * 100f).roundToInt(),
    )
}

internal fun applyProListeningExperience(
    state: AudioEngineState,
    experienceId: ProListeningExperienceId,
): AudioEngineState =
    when (experienceId) {
        ProListeningExperienceId.StudioReference -> state.experienceBase(
            preampDb = 0f,
            replayGainMode = ReplayGainMode.Off,
            graphicEq = flatEqBands(),
            parametricEq = defaultParametricBands(),
            tone = ToneTarget(),
            stereo = StereoState(),
            compressor = CompressorState(),
            limiter = LimiterState(),
            requiresNativeDsp = false,
        ).copy(
            native = state.native.copy(media3DspEnabled = false, lowLatencyToneEnabled = false),
        ).normalized()

        ProListeningExperienceId.CleanBass -> state.experienceBase(
            preampDb = -4f,
            replayGainMode = ReplayGainMode.Smart,
            graphicEq = experienceBands(3.0f, 2.4f, 1.1f, -0.4f, -0.8f, 0f, 0.4f, 0.7f, 0.5f, 0.2f),
            parametricEq = listOf(
                ParametricEqBand(enabled = true, type = ParametricFilterType.LowShelf, frequencyHz = 74f, gainDb = 1.2f, q = 0.7f),
                ParametricEqBand(enabled = true, type = ParametricFilterType.Peak, frequencyHz = 180f, gainDb = -0.8f, q = 1.1f),
                defaultParametricBands()[2],
                defaultParametricBands()[3],
            ),
            tone = ToneTarget(bassDb = 1.0f),
            stereo = StereoState(),
            compressor = CompressorState(),
            limiter = LimiterState(),
            requiresNativeDsp = true,
        )

        ProListeningExperienceId.VocalFocus -> state.experienceBase(
            preampDb = -3f,
            replayGainMode = ReplayGainMode.Track,
            graphicEq = experienceBands(-1.5f, -1.2f, -1f, -0.6f, 0.6f, 1.6f, 2.4f, 1.4f, 0.3f, -0.2f),
            parametricEq = listOf(
                ParametricEqBand(enabled = true, type = ParametricFilterType.LowShelf, frequencyHz = 120f, gainDb = -1.0f, q = 0.7f),
                defaultParametricBands()[1],
                ParametricEqBand(enabled = true, type = ParametricFilterType.Peak, frequencyHz = 2500f, gainDb = 1.4f, q = 1.1f),
                defaultParametricBands()[3],
            ),
            tone = ToneTarget(vocalClarityDb = 1.6f),
            stereo = StereoState(),
            compressor = CompressorState(),
            limiter = LimiterState(),
            requiresNativeDsp = true,
        )

        ProListeningExperienceId.NightDetail -> state.experienceBase(
            preampDb = -5f,
            replayGainMode = ReplayGainMode.Track,
            graphicEq = experienceBands(-2.5f, -2f, -1f, 0f, 0.5f, 1.0f, 1.0f, 0.4f, -0.4f, -0.8f),
            parametricEq = defaultParametricBands(),
            tone = ToneTarget(trebleDb = 0.7f, loudnessDb = 0.8f),
            stereo = StereoState(),
            compressor = CompressorState(enabled = true, thresholdDb = -24f, ratio = 1.6f, attackMs = 18f, releaseMs = 250f, makeupDb = 0f, mix = 0.25f),
            limiter = LimiterState(enabled = true, ceilingDb = -1.5f, releaseMs = 160f, strength = 0.62f),
            requiresNativeDsp = true,
        )

        ProListeningExperienceId.RoadEnergy -> state.experienceBase(
            preampDb = -5f,
            replayGainMode = ReplayGainMode.Track,
            graphicEq = experienceBands(3.8f, 3.0f, 1.6f, -0.4f, 0.3f, 1.3f, 2.2f, 1.6f, 0.8f, 0.2f),
            parametricEq = listOf(
                ParametricEqBand(enabled = true, type = ParametricFilterType.LowShelf, frequencyHz = 86f, gainDb = 0.8f, q = 0.7f),
                defaultParametricBands()[1],
                ParametricEqBand(enabled = true, type = ParametricFilterType.Peak, frequencyHz = 2200f, gainDb = 0.8f, q = 1.0f),
                defaultParametricBands()[3],
            ),
            tone = ToneTarget(vocalClarityDb = 0.9f),
            stereo = StereoState(),
            compressor = CompressorState(enabled = true, thresholdDb = -20f, ratio = 1.7f, attackMs = 10f, releaseMs = 160f, makeupDb = 0f, mix = 0.22f),
            limiter = LimiterState(),
            requiresNativeDsp = true,
        )

        ProListeningExperienceId.HeadphoneSpace -> state.experienceBase(
            preampDb = -3f,
            replayGainMode = ReplayGainMode.Album,
            graphicEq = experienceBands(1.2f, 0.9f, 0.3f, 0f, -0.3f, 0f, 0.6f, 0.9f, 0.4f, 0f),
            parametricEq = listOf(
                defaultParametricBands()[0],
                ParametricEqBand(enabled = true, type = ParametricFilterType.Peak, frequencyHz = 320f, gainDb = -0.6f, q = 1.0f),
                defaultParametricBands()[2],
                defaultParametricBands()[3],
            ),
            tone = ToneTarget(),
            stereo = StereoState(stereoWidth = 0.08f, crossfeed = 0.16f),
            compressor = CompressorState(),
            limiter = LimiterState(),
            requiresNativeDsp = true,
        )
    }

internal fun revertProListeningExperience(plan: ProListeningExperiencePlan): AudioEngineState =
    plan.originalState

internal fun activeProListeningExperience(state: AudioEngineState): ProListeningExperience? =
    proListeningExperiences.firstOrNull { experience ->
        val target = applyProListeningExperience(state.copy(native = state.native.copy(media3DspEnabled = false)), experience.id)
        state.matchesExperienceShape(target)
    }

internal fun recommendedProListeningExperience(
    state: AudioEngineState,
    diagnostics: OutputDiagnosticsSnapshot = OutputDiagnosticsSnapshot(),
): ProListeningRouteRecommendation {
    val route = currentDeviceProfile(state, diagnostics)
    val id = when (route) {
        DeviceProfile.Bluetooth -> ProListeningExperienceId.CleanBass
        DeviceProfile.Car -> ProListeningExperienceId.RoadEnergy
        DeviceProfile.Wired -> ProListeningExperienceId.HeadphoneSpace
        DeviceProfile.UsbDac -> ProListeningExperienceId.StudioReference
        DeviceProfile.Speaker -> ProListeningExperienceId.VocalFocus
    }
    val experience = proListeningExperienceFor(id)
    val reason = when (route) {
        DeviceProfile.Bluetooth -> "Bluetooth routes benefit from protected bass and conservative claims."
        DeviceProfile.Car -> "Car listening usually needs more presence and headroom."
        DeviceProfile.Wired -> "Wired headphones are a good fit for crossfeed and gentle tonal shaping."
        DeviceProfile.UsbDac -> "USB DAC routes should stay evidence-led until hardware is verified."
        DeviceProfile.Speaker -> "Speaker playback usually benefits from voice clarity before heavier shaping."
    }
    return ProListeningRouteRecommendation(
        experience = experience,
        routeLabel = route.label,
        reason = reason,
        fit = routeFitFor(experience, route),
    )
}

private data class ToneTarget(
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val vocalClarityDb: Float = 0f,
    val loudnessDb: Float = 0f,
)

private fun AudioEngineState.experienceBase(
    preampDb: Float,
    replayGainMode: ReplayGainMode,
    graphicEq: List<GraphicEqBand>,
    parametricEq: List<ParametricEqBand>,
    tone: ToneTarget,
    stereo: StereoState,
    compressor: CompressorState,
    limiter: LimiterState,
    requiresNativeDsp: Boolean,
): AudioEngineState =
    copy(
        enabled = true,
        bypass = false,
        preampDb = preampDb,
        replayGainMode = replayGainMode,
        replayGainPreampDb = 0f,
        preventClipping = true,
        limiter = limiter,
        eqEnabled = true,
        eqBands = graphicEq,
        parametricEq = parametricEq,
        bassDb = tone.bassDb,
        trebleDb = tone.trebleDb,
        vocalClarityDb = tone.vocalClarityDb,
        loudnessDb = tone.loudnessDb,
        stereo = stereo,
        reverb = ReverbState(),
        tempoPitch = TempoPitchState(),
        compressor = compressor,
        gate = GateState(),
        delay = DelayState(),
        modulation = ModulationState(),
        native = native.copy(
            enabled = native.enabled || requiresNativeDsp,
            media3DspEnabled = requiresNativeDsp,
            lowLatencyToneEnabled = false,
        ),
        activePresetId = "flat",
        presetModified = true,
    ).normalized()

private fun experienceBands(vararg gains: Float): List<GraphicEqBand> =
    canonicalGraphicEqBands(graphicEqCenterFrequenciesHz().mapIndexed { index, frequencyHz ->
        GraphicEqBand(frequencyHz = frequencyHz, gainDb = gains.getOrNull(index) ?: 0f)
    })

private fun currentDeviceProfile(state: AudioEngineState, diagnostics: OutputDiagnosticsSnapshot): DeviceProfile =
    when (diagnostics.outputDevice.activeRoute) {
        OutputDeviceType.Bluetooth -> DeviceProfile.Bluetooth
        OutputDeviceType.UsbDac -> DeviceProfile.UsbDac
        OutputDeviceType.WiredHeadsetAux -> DeviceProfile.Wired
        OutputDeviceType.Speaker -> DeviceProfile.Speaker
        OutputDeviceType.Chromecast -> state.output.deviceProfile
        OutputDeviceType.Other -> state.output.deviceProfile
        null -> state.output.deviceProfile
    }

private fun routeFitFor(experience: ProListeningExperience, route: DeviceProfile): ProListeningRouteFit =
    when {
        route in experience.bestFor -> ProListeningRouteFit.Recommended
        route == DeviceProfile.Bluetooth && experience.id in setOf(
            ProListeningExperienceId.StudioReference,
            ProListeningExperienceId.HeadphoneSpace,
        ) -> ProListeningRouteFit.Caution
        route == DeviceProfile.UsbDac && experience.id in setOf(
            ProListeningExperienceId.RoadEnergy,
            ProListeningExperienceId.CleanBass,
        ) -> ProListeningRouteFit.Caution
        else -> ProListeningRouteFit.Good
    }

private fun routeWarningsFor(
    original: AudioEngineState,
    preview: AudioEngineState,
    diagnostics: OutputDiagnosticsSnapshot,
): List<String> = buildList {
    val route = currentDeviceProfile(original, diagnostics)
    if (preview.advancedTweaks.safeMode) {
        add("Safe mode is on; the experience is saved but advanced processing stays neutralized.")
    }
    if (route == DeviceProfile.Bluetooth || diagnostics.outputDevice.activeRoute == OutputDeviceType.Bluetooth) {
        add("Bluetooth keeps hi-res and bit-perfect as compatibility-limited unless runtime evidence says otherwise.")
    }
    if (route == DeviceProfile.UsbDac || diagnostics.outputDevice.activeRoute == OutputDeviceType.UsbDac) {
        add("USB DAC status still requires actual route evidence; this experience does not force routing.")
    }
    if (diagnostics.outputDevice.activeRoute != null && !diagnostics.outputDevice.routeVerified) {
        add("Output route is visible but not verified; claims stay conservative.")
    }
    if (preview.native.media3DspEnabled && diagnostics.dspFallbackReason != null) {
        add("Native DSP fallback is still visible: ${diagnostics.dspFallbackReason}")
    }
    if (diagnostics.proOutput.enabledByUser && diagnostics.proOutput.status in setOf(ProOutputRuntimeStatus.FallbackToMedia3, ProOutputRuntimeStatus.Failed)) {
        add("Pro output attempt is falling back to Media3; output quality stays evidence-based.")
    }
}

private fun compatibilityNotesFor(
    preview: AudioEngineState,
    diagnostics: OutputDiagnosticsSnapshot,
): List<String> = buildList {
    val policy = preview.effectiveProcessingPolicy(nativeAvailable = false)
    val bitPerfectFriendly = preview.isBitPerfectFriendly()
    if (preview.output.bitPerfectAttemptEnabled || diagnostics.bitPerfectAttemptEnabled) {
        add(
            if (bitPerfectFriendly) {
                "Bit-perfect attempt remains only an attempt until route, rate, format, and runtime evidence clear."
            } else {
                "Bit-perfect attempt will report blockers while this experience uses DSP, ReplayGain, preamp, or headroom."
            },
        )
    } else if (!bitPerfectFriendly) {
        add("Bit-perfect is not claimed; this experience intentionally uses the existing audio chain.")
    }
    if (preview.output.hiResEnabled || preview.output.mode == OutputMode.HiRes) {
        add("Hi-res status remains runtime-evidence based; this experience does not create a hi-res active claim.")
    }
    if (policy.preventClippingActive) {
        add("Prevent Clipping contributes ${formatDb(policy.estimatedHeadroomDb)} of automatic headroom.")
    } else if (preview.preventClipping) {
        add("Prevent Clipping is armed and will add headroom when positive boost is present.")
    }
    if (diagnostics.truePeak.status == TruePeakMeasurementStatus.Measured) {
        add("True-peak meter has measured supported PCM frames; safety display uses that runtime status.")
    } else {
        add("True-peak safety remains ${diagnostics.truePeak.status.name.lowercase(Locale.US)} on this path.")
    }
}

private fun safetyStatusFor(
    preview: AudioEngineState,
    warnings: List<String>,
    notes: List<String>,
): ProListeningSafetyStatus =
    when {
        notes.any { it.contains("Bit-perfect attempt will report blockers", ignoreCase = true) } ->
            ProListeningSafetyStatus.CompatibilityBlocked
        warnings.isNotEmpty() -> ProListeningSafetyStatus.RouteCaution
        preview.maxBoostDb <= 0.05f && preview.isBitPerfectFriendly() -> ProListeningSafetyStatus.Transparent
        preview.preventClipping -> ProListeningSafetyStatus.ClippingProtected
        else -> ProListeningSafetyStatus.RouteCaution
    }

private fun proAudioInfoFor(state: AudioEngineState): String {
    val policy = state.effectiveProcessingPolicy(nativeAvailable = false)
    return buildList {
        add("ReplayGain ${state.replayGainMode.label}")
        add("preamp ${formatDb(state.preampDb)}")
        add(if (state.eqStageActive) "EQ shaped" else "EQ flat")
        if (state.parametricEqStageActive) add("${state.parametricEq.count { it.isActiveFilter() }} parametric slot(s)")
        if (state.toneControlsActive) add("tone active")
        if (state.stereoMatrixActive) add("stereo tools active")
        if (state.compressorStageActive) add("gentle dynamics")
        add("headroom ${formatDb(policy.estimatedHeadroomDb)}")
    }.joinToString(" | ")
}

private fun truePeakSafetyLine(diagnostics: OutputDiagnosticsSnapshot): String =
    when (diagnostics.truePeak.status) {
        TruePeakMeasurementStatus.Measured -> {
            val value = diagnostics.truePeak.truePeakDbTp ?: diagnostics.truePeak.samplePeakDbFs
            val peak = value?.let(::formatDb) ?: "measured"
            "Measured $peak over ${diagnostics.truePeak.framesMeasured} frames"
        }
        TruePeakMeasurementStatus.Measuring -> "Measuring supported PCM path"
        TruePeakMeasurementStatus.Pending -> "Pending supported PCM evidence"
        TruePeakMeasurementStatus.Unsupported -> diagnostics.truePeak.reason ?: "Unsupported on current path"
    }

private fun changesFor(before: AudioEngineState, after: AudioEngineState): List<ProListeningExperienceChange> =
    buildList {
        addIfChanged("ReplayGain", before.replayGainMode.label, after.replayGainMode.label)
        addIfChanged("Preamp", formatDb(before.preampDb), formatDb(after.preampDb))
        addIfChanged("Graphic EQ", eqSummary(before), eqSummary(after))
        addIfChanged("Parametric EQ", peqSummary(before), peqSummary(after))
        addIfChanged("Tone", toneSummary(before), toneSummary(after))
        addIfChanged("Stereo", stereoSummary(before), stereoSummary(after))
        addIfChanged("Dynamics", dynamicsSummary(before), dynamicsSummary(after))
        addIfChanged("Time effects", timeEffectsSummary(before), timeEffectsSummary(after))
        addIfChanged("Headroom", if (before.preventClipping) "Prevent Clipping on" else "Manual", if (after.preventClipping) "Prevent Clipping on" else "Manual")
    }

private fun MutableList<ProListeningExperienceChange>.addIfChanged(stage: String, before: String, after: String) {
    if (before != after) add(ProListeningExperienceChange(stage, before, after))
}

private fun AudioEngineState.isBitPerfectFriendly(): Boolean =
    enabled &&
        !bypass &&
        abs(preampDb) <= 0.05f &&
        replayGainMode == ReplayGainMode.Off &&
        abs(replayGainPreampDb) <= 0.05f &&
        !eqStageActive &&
        !toneControlsActive &&
        !loudnessStageActive &&
        !stereoMatrixActive &&
        !dynamicsStageActive &&
        !timeFxStageActive &&
        !pitchTempoActive &&
        (!preventClipping || maxBoostDb <= 0.05f) &&
        !native.media3DspEnabled

private fun AudioEngineState.matchesExperienceShape(target: AudioEngineState): Boolean =
    enabled == target.enabled &&
        bypass == target.bypass &&
        abs(preampDb - target.preampDb) <= 0.05f &&
        replayGainMode == target.replayGainMode &&
        abs(replayGainPreampDb - target.replayGainPreampDb) <= 0.05f &&
        preventClipping == target.preventClipping &&
        limiter == target.limiter &&
        eqEnabled == target.eqEnabled &&
        eqBands == target.eqBands &&
        parametricEq == target.parametricEq &&
        abs(bassDb - target.bassDb) <= 0.05f &&
        abs(trebleDb - target.trebleDb) <= 0.05f &&
        abs(vocalClarityDb - target.vocalClarityDb) <= 0.05f &&
        abs(loudnessDb - target.loudnessDb) <= 0.05f &&
        stereo == target.stereo &&
        reverb == target.reverb &&
        tempoPitch == target.tempoPitch &&
        compressor == target.compressor &&
        gate == target.gate &&
        delay == target.delay &&
        modulation == target.modulation &&
        native.media3DspEnabled == target.native.media3DspEnabled &&
        native.lowLatencyToneEnabled == target.native.lowLatencyToneEnabled

private fun eqSummary(state: AudioEngineState): String =
    if (!state.eqEnabled || state.eqBands.all { abs(it.gainDb) <= 0.05f }) {
        "Flat"
    } else {
        "Max ${formatDb(state.eqBands.maxOf { it.gainDb })}"
    }

private fun peqSummary(state: AudioEngineState): String {
    val active = state.parametricEq.count { it.isActiveFilter() }
    return if (active == 0) "Disabled" else "$active active"
}

private fun toneSummary(state: AudioEngineState): String =
    buildList {
        if (abs(state.bassDb) > 0.05f) add("Bass ${formatDb(state.bassDb)}")
        if (abs(state.trebleDb) > 0.05f) add("Treble ${formatDb(state.trebleDb)}")
        if (abs(state.vocalClarityDb) > 0.05f) add("Voice ${formatDb(state.vocalClarityDb)}")
        if (state.loudnessDb > 0.05f) add("Loudness ${formatDb(state.loudnessDb)}")
    }.ifEmpty { listOf("Neutral") }.joinToString(", ")

private fun stereoSummary(state: AudioEngineState): String =
    buildList {
        if (state.stereo.mono) add("Mono")
        if (state.stereo.crossfeed > 0.05f) add("Crossfeed ${(state.stereo.crossfeed * 100f).roundToInt()}%")
        if (state.stereo.stereoWidth > 0.05f) add("Width ${(state.stereo.stereoWidth * 100f).roundToInt()}%")
    }.ifEmpty { listOf("Neutral") }.joinToString(", ")

private fun dynamicsSummary(state: AudioEngineState): String =
    buildList {
        if (state.compressorStageActive) add("Compressor ${(state.compressor.mix * 100f).roundToInt()}%")
        if (state.gateStageActive) add("Gate")
        if (state.nativeLimiterStageActive) add("Limiter safety")
    }.ifEmpty { listOf("Off") }.joinToString(", ")

private fun timeEffectsSummary(state: AudioEngineState): String =
    buildList {
        if (state.reverbStageActive) add("Reverb")
        if (state.delayStageActive) add("Delay")
        if (state.modulationStageActive) add("Mod")
        if (state.pitchTempoActive) add("Tempo/Pitch")
    }.ifEmpty { listOf("Off") }.joinToString(", ")

private fun formatDb(value: Float): String =
    if (abs(value) < 0.05f) {
        "0 dB"
    } else {
        String.format(Locale.US, "%+.1f dB", value)
    }
