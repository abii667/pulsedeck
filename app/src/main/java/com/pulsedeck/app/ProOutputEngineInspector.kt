package com.pulsedeck.app

import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeEngine
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeStatus
import java.util.Locale
import kotlin.math.abs

internal data class ProOutputEngineState(
    val enabledByUser: Boolean,
    val active: Boolean,
    val mode: ProOutputMode,
    val engine: ProOutputEngine,
    val sourceEligibility: ProOutputSourceEligibility,
    val routeEligibility: ProOutputRouteEligibility,
    val currentStatus: ProOutputStatus,
    val blockers: List<ProOutputBlocker>,
    val fallbackReason: String?,
)

internal data class ProOutputSourceEligibility(
    val localOrOffline: Boolean,
    val hiResSource: Boolean,
    val hiResStatus: ProOutputHiResSourceStatus,
    val sourceSampleRateHz: Int?,
    val sourceBitDepth: Int?,
    val decodedSampleRateHz: Int?,
    val decodedEncoding: String?,
    val reason: String,
)

internal data class ProOutputRouteEligibility(
    val route: OutputDeviceType,
    val routeVerified: Boolean,
    val bluetoothRoute: Boolean,
    val usbDacDetected: Boolean,
    val usbDacActive: Boolean,
    val outputSampleRateHz: Int?,
    val outputEncoding: String?,
    val channelCount: Int?,
    val bufferSizeBytes: Int?,
    val underrunCount: Int,
    val highResolutionOutputObserved: Boolean,
    val reason: String,
)

internal enum class ProOutputHiResSourceStatus {
    Unknown,
    NotHiRes,
    LikelyHiRes,
    VerifiedHiRes,
}

internal enum class ProOutputMode {
    StandardMedia3,
    HiResAttempt,
    UsbDacAttempt,
    BitPerfectAttempt,
}

internal enum class ProOutputEngine {
    Media3Default,
    Media3CustomOutput,
    OboeAaudio,
    AudioTrackDirect,
    Unavailable,
}

internal enum class ProOutputStatus {
    Off,
    Requested,
    Eligible,
    Attempted,
    Active,
    Blocked,
    FallbackToMedia3,
    Unsupported,
    Unknown,
}

internal enum class ProOutputBlocker {
    NotLocalOrOffline,
    SourceFormatUnknown,
    SourceNotHiRes,
    DecodeFormatUnknown,
    OutputRouteUnknown,
    OutputSampleRateUnknown,
    OutputEncodingUnknown,
    BluetoothRoute,
    UsbDacNotDetected,
    DspActive,
    ReplayGainActive,
    EqActive,
    ToneActive,
    DynamicsActive,
    TimeEffectsActive,
    PitchTempoActive,
    OutputTrimActive,
    CrossfadeActive,
    ResamplingLikely,
    NativeDspUnsupportedFormat,
    Media3CustomOutputUnavailable,
    OboeUnavailable,
    DeviceRejectedFormat,
    UnderrunRisk,
    Unknown,
}

internal fun proOutputEngineState(
    state: AudioEngineState,
    track: Track,
    source: AudioSourceInfo,
    resampler: ResamplerStrategySnapshot,
    outputCapability: OutputCapabilitySnapshot,
    diagnostics: OutputDiagnosticsSnapshot,
    policy: EffectiveAudioProcessingPolicy,
    bitPerfect: BitPerfectAttemptSnapshot,
    media3CustomOutputAvailable: Boolean = true,
    oboeAvailable: Boolean = true,
): ProOutputEngineState {
    val mode = proOutputModeFor(state)
    val nativeLowLatencyRequested = state.output.mode == OutputMode.NativeLowLatency
    val enabledByUser = mode != ProOutputMode.StandardMedia3 || nativeLowLatencyRequested
    val sourceEligibility = proOutputSourceEligibility(track, source, resampler, diagnostics)
    val routeEligibility = proOutputRouteEligibility(outputCapability, diagnostics)
    val blockers = if (!enabledByUser) {
        emptyList()
    } else {
        proOutputBlockers(
            state = state,
            mode = mode,
            sourceEligibility = sourceEligibility,
            routeEligibility = routeEligibility,
            resampler = resampler,
            outputCapability = outputCapability,
            diagnostics = diagnostics,
            policy = policy,
            media3CustomOutputAvailable = media3CustomOutputAvailable,
            oboeAvailable = oboeAvailable,
            nativeLowLatencyRequested = nativeLowLatencyRequested,
        )
    }
    val runtimeCustomOutputActive = diagnostics.proOutput.engine == ProOutputRuntimeEngine.Media3CustomOutput &&
        diagnostics.proOutput.status == ProOutputRuntimeStatus.Active
    val active = enabledByUser &&
        blockers.isEmpty() &&
        when (mode) {
            ProOutputMode.StandardMedia3 -> false
            ProOutputMode.HiResAttempt -> outputCapability.hiResActive &&
                sourceEligibility.hiResSource &&
                routeEligibility.highResolutionOutputObserved &&
                runtimeCustomOutputActive
            ProOutputMode.UsbDacAttempt -> outputCapability.usbDacActive && routeEligibility.usbDacActive
            ProOutputMode.BitPerfectAttempt -> bitPerfect.active
        }
    val status = when {
        !enabledByUser -> ProOutputStatus.Off
        active -> ProOutputStatus.Active
        outputCapability.status == OutputVerificationStatus.Unsupported -> ProOutputStatus.FallbackToMedia3
        blockers.isNotEmpty() -> ProOutputStatus.Blocked
        mode == ProOutputMode.HiResAttempt &&
            outputCapability.hiResOutputStatus in setOf(
                HiResOutputStatus.HighSampleRateActive,
                HiResOutputStatus.HighBitDepthActive,
                HiResOutputStatus.FullHiResActive,
            ) -> ProOutputStatus.Attempted
        diagnostics.updatedAtMillis == 0L -> ProOutputStatus.Requested
        outputCapability.status == OutputVerificationStatus.Requested -> ProOutputStatus.Attempted
        mode == ProOutputMode.BitPerfectAttempt && bitPerfect.eligible -> ProOutputStatus.Eligible
        mode == ProOutputMode.StandardMedia3 && nativeLowLatencyRequested -> ProOutputStatus.Unsupported
        else -> ProOutputStatus.Unknown
    }
    return ProOutputEngineState(
        enabledByUser = enabledByUser,
        active = active,
        mode = mode,
        engine = proOutputEngineFor(mode, nativeLowLatencyRequested, oboeAvailable, diagnostics),
        sourceEligibility = sourceEligibility,
        routeEligibility = routeEligibility,
        currentStatus = status,
        blockers = blockers,
        fallbackReason = proOutputFallbackReason(
            status = status,
            blockers = blockers,
            outputCapability = outputCapability,
            nativeLowLatencyRequested = nativeLowLatencyRequested,
            active = active,
            runtimeFallbackReason = diagnostics.proOutput.fallbackReason,
        ),
    )
}

private fun proOutputModeFor(state: AudioEngineState): ProOutputMode =
    when {
        state.output.bitPerfectAttemptEnabled -> ProOutputMode.BitPerfectAttempt
        state.output.mode == OutputMode.HiRes || state.output.hiResEnabled -> ProOutputMode.HiResAttempt
        state.output.deviceProfile == DeviceProfile.UsbDac -> ProOutputMode.UsbDacAttempt
        else -> ProOutputMode.StandardMedia3
    }

private fun proOutputEngineFor(
    mode: ProOutputMode,
    nativeLowLatencyRequested: Boolean,
    oboeAvailable: Boolean,
    diagnostics: OutputDiagnosticsSnapshot,
): ProOutputEngine =
    when {
        nativeLowLatencyRequested && oboeAvailable -> ProOutputEngine.OboeAaudio
        nativeLowLatencyRequested -> ProOutputEngine.Unavailable
        diagnostics.proOutput.engine == ProOutputRuntimeEngine.Media3CustomOutput -> ProOutputEngine.Media3CustomOutput
        mode == ProOutputMode.StandardMedia3 -> ProOutputEngine.Media3Default
        else -> ProOutputEngine.Media3Default
    }

private fun proOutputSourceEligibility(
    track: Track,
    source: AudioSourceInfo,
    resampler: ResamplerStrategySnapshot,
    diagnostics: OutputDiagnosticsSnapshot,
): ProOutputSourceEligibility {
    val scheme = (track.uri?.scheme ?: source.uriScheme)?.lowercase(Locale.US)
    val premiumDeckSource = source.label.contains("PremiumDeck", ignoreCase = true) ||
        track.folderPath.orEmpty().contains("Stream Library", ignoreCase = true) ||
        scheme == "pulsedeck"
    val localFolderPath = scheme == null && track.folderPath.orEmpty().isNotBlank() && !premiumDeckSource
    val localOrOffline = (scheme in setOf("content", "file") || localFolderPath) && !premiumDeckSource && !source.live
    val decodedEncoding = diagnostics.decoderFormat.decodedPcmEncoding
    val sourceSampleRateHz = source.sampleRateHz ?: resampler.sourceSampleRateHz
    val sourceBitDepth = source.bitDepth ?: track.quality.sourceBitDepthHint()
    val decodedSampleRateHz = resampler.decodedSampleRateHz ?: diagnostics.decoderFormat.decodedSampleRateHz
    val hiResStatus = hiResSourceStatus(
        sourceSampleRateHz = sourceSampleRateHz,
        sourceBitDepth = sourceBitDepth,
        decodedSampleRateHz = decodedSampleRateHz,
        decodedEncoding = decodedEncoding,
    )
    val hiResSource = hiResStatus == ProOutputHiResSourceStatus.LikelyHiRes ||
        hiResStatus == ProOutputHiResSourceStatus.VerifiedHiRes
    return ProOutputSourceEligibility(
        localOrOffline = localOrOffline,
        hiResSource = hiResSource,
        hiResStatus = hiResStatus,
        sourceSampleRateHz = sourceSampleRateHz,
        sourceBitDepth = sourceBitDepth,
        decodedSampleRateHz = decodedSampleRateHz,
        decodedEncoding = decodedEncoding,
        reason = when {
            localOrOffline && hiResSource -> "Local library source has hi-res source or decoded PCM evidence."
            localOrOffline -> "Local library source is eligible for pro-output attempts, but hi-res source evidence is not present."
            premiumDeckSource -> "PremiumDeck sources stay on the stable Media3 playback path in this phase."
            source.live -> "Live or radio streams stay on the stable Media3 playback path in this phase."
            localFolderPath -> "A local folder path is present, but pro-output eligibility is not otherwise verified."
            scheme == null -> "Playback URI is unknown, so local/offline eligibility is not verified."
            else -> "Only local content/file library sources are eligible for pro-output activation in this phase."
        },
    )
}

private fun proOutputRouteEligibility(
    outputCapability: OutputCapabilitySnapshot,
    diagnostics: OutputDiagnosticsSnapshot,
): ProOutputRouteEligibility {
    val device = diagnostics.outputDevice
    val route = device.activeRoute ?: outputCapability.route
    val proOutput = diagnostics.proOutput
    val outputSampleRateHz = outputCapability.audioTrackSampleRateHz ?: proOutput.actualSampleRateHz
    val outputEncoding = outputCapability.audioTrackEncoding ?: proOutput.actualEncoding
    val highResolutionOutputObserved = outputSampleRateHz.isHiResRate() ||
        outputEncoding.isHighPrecisionPcm() ||
        proOutput.actualSampleRateHz.isHiResRate() ||
        proOutput.actualEncoding.isHighPrecisionPcm()
    val routeVerified = device.routeVerified || outputCapability.status == OutputVerificationStatus.Verified
    val usbDacActive = outputCapability.usbDacActive ||
        (device.usbDacDetected && device.routeVerified && device.activeRoute == OutputDeviceType.UsbDac)
    return ProOutputRouteEligibility(
        route = route,
        routeVerified = routeVerified,
        bluetoothRoute = route == OutputDeviceType.Bluetooth,
        usbDacDetected = device.usbDacDetected,
        usbDacActive = usbDacActive,
        outputSampleRateHz = outputSampleRateHz,
        outputEncoding = outputEncoding,
        channelCount = proOutput.channelCount,
        bufferSizeBytes = proOutput.bufferSizeBytes,
        underrunCount = proOutput.underrunCount,
        highResolutionOutputObserved = highResolutionOutputObserved,
        reason = when {
            route == OutputDeviceType.Bluetooth -> "Bluetooth routes are not treated as verified hi-res or bit-perfect outputs."
            usbDacActive -> "Android reports an active, verified USB DAC output route."
            highResolutionOutputObserved && routeVerified -> "Runtime AudioTrack evidence shows high sample rate or high-precision PCM."
            routeVerified -> "Output route is verified, but no hi-res AudioTrack evidence is present."
            else -> "Output route and AudioTrack format are not fully verified yet."
        },
    )
}

private fun proOutputBlockers(
    state: AudioEngineState,
    mode: ProOutputMode,
    sourceEligibility: ProOutputSourceEligibility,
    routeEligibility: ProOutputRouteEligibility,
    resampler: ResamplerStrategySnapshot,
    outputCapability: OutputCapabilitySnapshot,
    diagnostics: OutputDiagnosticsSnapshot,
    policy: EffectiveAudioProcessingPolicy,
    media3CustomOutputAvailable: Boolean,
    oboeAvailable: Boolean,
    nativeLowLatencyRequested: Boolean,
): List<ProOutputBlocker> =
    buildList {
        val hiResAttempt = mode == ProOutputMode.HiResAttempt
        val strictPcmAttempt = mode == ProOutputMode.HiResAttempt || mode == ProOutputMode.BitPerfectAttempt
        if (!sourceEligibility.localOrOffline) add(ProOutputBlocker.NotLocalOrOffline)
        if (strictPcmAttempt && sourceEligibility.sourceSampleRateHz == null && sourceEligibility.decodedSampleRateHz == null) {
            add(ProOutputBlocker.SourceFormatUnknown)
        }
        if (hiResAttempt && !sourceEligibility.hiResSource) add(ProOutputBlocker.SourceNotHiRes)
        if (strictPcmAttempt && (sourceEligibility.decodedSampleRateHz == null || sourceEligibility.decodedEncoding == null)) {
            add(ProOutputBlocker.DecodeFormatUnknown)
        }
        if (!routeEligibility.routeVerified) add(ProOutputBlocker.OutputRouteUnknown)
        if (routeEligibility.outputSampleRateHz == null) add(ProOutputBlocker.OutputSampleRateUnknown)
        if (routeEligibility.outputEncoding == null) add(ProOutputBlocker.OutputEncodingUnknown)
        if (routeEligibility.bluetoothRoute) add(ProOutputBlocker.BluetoothRoute)
        if (mode == ProOutputMode.UsbDacAttempt && !routeEligibility.usbDacActive) {
            add(ProOutputBlocker.UsbDacNotDetected)
        }
        if (policy.processingActive) add(ProOutputBlocker.DspActive)
        if (policy.replayGainActive || state.replayGainMode != ReplayGainMode.Off || abs(policy.replayGainAdjustmentDb) > 0.001f) {
            add(ProOutputBlocker.ReplayGainActive)
        }
        if (policy.eqStageActive) add(ProOutputBlocker.EqActive)
        if (policy.toneStageActive || policy.loudnessStageActive) add(ProOutputBlocker.ToneActive)
        if (policy.dynamicsStageActive) add(ProOutputBlocker.DynamicsActive)
        if (policy.timeFxStageActive) add(ProOutputBlocker.TimeEffectsActive)
        if (policy.pitchTempoActive) add(ProOutputBlocker.PitchTempoActive)
        if (abs(audioOutputGain(state, 1f) - 1f) > 0.001f || policy.preventClippingActive) {
            add(ProOutputBlocker.OutputTrimActive)
        }
        if (state.crossfade.crossfadeEnabled) add(ProOutputBlocker.CrossfadeActive)
        if (resampler.resamplerStatus == ResamplerStatus.LikelySystemResampled) {
            add(ProOutputBlocker.ResamplingLikely)
        }
        if (policy.nativeMedia3DspRequested && !policy.nativeMedia3DspActive && diagnostics.dspFallbackReason != null) {
            add(ProOutputBlocker.NativeDspUnsupportedFormat)
        }
        if (!media3CustomOutputAvailable) add(ProOutputBlocker.Media3CustomOutputUnavailable)
        if (
            hiResAttempt &&
            outputCapability.hiResOutputStatus == HiResOutputStatus.HighSampleRateActive &&
            state.output.bitDepth in setOf(OutputBitDepth.Bit24, OutputBitDepth.Float32)
        ) {
            add(ProOutputBlocker.DeviceRejectedFormat)
        }
        if (
            hiResAttempt &&
            outputCapability.hiResActive &&
            (
                diagnostics.proOutput.engine != ProOutputRuntimeEngine.Media3CustomOutput ||
                    diagnostics.proOutput.status != ProOutputRuntimeStatus.Active
                )
        ) {
            add(ProOutputBlocker.Media3CustomOutputUnavailable)
        }
        if (
            diagnostics.proOutput.status == ProOutputRuntimeStatus.FallbackToMedia3 ||
            diagnostics.proOutput.status == ProOutputRuntimeStatus.Failed
        ) {
            add(ProOutputBlocker.Media3CustomOutputUnavailable)
        }
        if (nativeLowLatencyRequested && !oboeAvailable) add(ProOutputBlocker.OboeUnavailable)
        if (outputCapability.status == OutputVerificationStatus.Unsupported) add(ProOutputBlocker.DeviceRejectedFormat)
        if (diagnostics.proOutput.underrunCount > 0) add(ProOutputBlocker.UnderrunRisk)
        if (diagnostics.updatedAtMillis == 0L) add(ProOutputBlocker.Unknown)
    }.distinct()

private fun proOutputFallbackReason(
    status: ProOutputStatus,
    blockers: List<ProOutputBlocker>,
    outputCapability: OutputCapabilitySnapshot,
    nativeLowLatencyRequested: Boolean,
    active: Boolean,
    runtimeFallbackReason: String?,
): String? =
    when {
        active -> null
        status == ProOutputStatus.Off -> null
        nativeLowLatencyRequested ->
            "Native low-latency output is not a decoded music pro-output path in this build; music playback remains Media3/AudioTrack."
        outputCapability.status == OutputVerificationStatus.Unsupported ->
            outputCapability.reason ?: "Requested output format or route is unavailable; playback remains on the safe Media3 fallback."
        status == ProOutputStatus.Attempted && outputCapability.hiResOutputStatus == HiResOutputStatus.HighSampleRateActive ->
            "Standard Media3/AudioTrack produced high-sample-rate output; custom pro-output is not active and high bit depth is not verified."
        status == ProOutputStatus.Attempted && outputCapability.hiResOutputStatus == HiResOutputStatus.HighBitDepthActive ->
            "Runtime output has high-precision PCM; custom pro-output is not active and high sample rate is not verified."
        status == ProOutputStatus.Attempted && outputCapability.hiResOutputStatus == HiResOutputStatus.FullHiResActive ->
            "Runtime output is full hi-res, but the custom pro-output engine is not active."
        blockers.contains(ProOutputBlocker.Media3CustomOutputUnavailable) ->
            runtimeFallbackReason ?: outputCapability.reason ?: "Media3 custom pro-output attempt fell back to the standard Media3/AudioTrack path."
        blockers.contains(ProOutputBlocker.DeviceRejectedFormat) ->
            outputCapability.reason ?: "Runtime output evidence does not match the requested high-resolution rate/depth."
        blockers.contains(ProOutputBlocker.UnderrunRisk) ->
            "Runtime underruns were reported; pro-output remains conservative until playback is stable."
        blockers.any {
            it in setOf(
                ProOutputBlocker.DspActive,
                ProOutputBlocker.ReplayGainActive,
                ProOutputBlocker.EqActive,
                ProOutputBlocker.ToneActive,
                ProOutputBlocker.DynamicsActive,
                ProOutputBlocker.TimeEffectsActive,
                ProOutputBlocker.PitchTempoActive,
                ProOutputBlocker.OutputTrimActive,
            )
        } -> "User DSP, ReplayGain, EQ, and trim settings are preserved; pro-output activation is blocked instead of changing sound."
        blockers.isNotEmpty() ->
            "Pro-output activation is blocked until source, decoder, route, and AudioTrack evidence all agree."
        else -> "Pro-output activation is not verified yet; playback remains on the stable Media3/AudioTrack path."
    }

private fun Int?.isHiResRate(): Boolean =
    this != null && this >= 88_200

private fun String?.isHighPrecisionPcm(): Boolean {
    val normalized = this?.uppercase(Locale.US) ?: return false
    return "PCM24" in normalized ||
        "PCM32" in normalized ||
        "FLOAT" in normalized
}

private fun hiResSourceStatus(
    sourceSampleRateHz: Int?,
    sourceBitDepth: Int?,
    decodedSampleRateHz: Int?,
    decodedEncoding: String?,
): ProOutputHiResSourceStatus =
    when {
        sourceBitDepth != null && sourceBitDepth > 16 -> ProOutputHiResSourceStatus.VerifiedHiRes
        decodedEncoding.isHighPrecisionPcm() -> ProOutputHiResSourceStatus.VerifiedHiRes
        decodedSampleRateHz.isHiResRate() -> ProOutputHiResSourceStatus.VerifiedHiRes
        sourceSampleRateHz.isHiResRate() -> ProOutputHiResSourceStatus.LikelyHiRes
        sourceSampleRateHz != null || sourceBitDepth != null || decodedSampleRateHz != null || decodedEncoding != null -> ProOutputHiResSourceStatus.NotHiRes
        else -> ProOutputHiResSourceStatus.Unknown
    }

private fun String.sourceBitDepthHint(): Int? =
    Regex("""\b(16|24|32)\s*(?:-| )?\s*bit\b""", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
