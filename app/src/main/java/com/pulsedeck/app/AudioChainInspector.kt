package com.pulsedeck.app

import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.AudioTrackDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.DecoderFormatDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.OutputPluginType
import com.pulsedeck.app.settings.runtime.OutputRouteStatus
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeEngine
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeSnapshot
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeStatus
import com.pulsedeck.app.settings.runtime.isHiResDepth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class AudioChainSnapshot(
    val source: AudioSourceInfo,
    val decoder: DecoderInfo,
    val resampler: ResamplerInfo,
    val resamplerStrategy: ResamplerStrategySnapshot,
    val dsp: DspChainInfo,
    val output: OutputEngineInfo,
    val device: OutputDeviceInfo,
    val outputCapability: OutputCapabilitySnapshot,
    val proOutput: ProOutputEngineState,
    val bitPerfect: BitPerfectAttemptSnapshot,
    val warnings: List<AudioChainWarning> = emptyList(),
)

internal data class AudioSourceInfo(
    val label: String,
    val detail: String,
    val format: String,
    val mimeType: String? = null,
    val containerMimeType: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val bitrateKbps: Int? = null,
    val uriScheme: String? = null,
    val live: Boolean = false,
)

internal data class DecoderInfo(
    val label: String,
    val detail: String,
    val codec: String,
    val outputEncoding: String,
    val mimeType: String? = null,
    val codecString: String? = null,
    val decoderName: String? = null,
    val decodedSampleRateHz: Int? = null,
    val decodedChannels: Int? = null,
    val hardwareAcceleration: String = "Unknown",
)

internal data class ResamplerInfo(
    val label: String,
    val detail: String,
    val requestedSampleRate: String,
    val effectiveSampleRate: String,
    val dither: Boolean,
    val verified: Boolean,
)

internal data class ResamplerStrategySnapshot(
    val sourceSampleRateHz: Int?,
    val decodedSampleRateHz: Int?,
    val outputSampleRateHz: Int?,
    val devicePreferredSampleRateHz: Int?,
    val resamplerStatus: ResamplerStatus,
    val strategy: ResamplerStrategy,
    val qualityMode: ResamplerQualityMode,
    val activeEngine: ResamplerEngine,
    val reason: String?,
    val warning: String?,
)

internal enum class ResamplerStatus {
    NotNeeded,
    SystemManaged,
    LikelySystemResampled,
    PulseDeckPlanned,
    PulseDeckActive,
    Unknown,
}

internal enum class ResamplerStrategy {
    FollowSystem,
    MatchSourceIfPossible,
    MatchDevicePreferred,
    BatterySaver,
    HighQualityPlanned,
    BitPerfectAttempt,
}

internal enum class ResamplerQualityMode {
    Standard,
    HighQualityPlanned,
    UltraPlanned,
    BatterySaver,
    Unknown,
}

internal enum class ResamplerEngine {
    AndroidSystem,
    Media3Default,
    PulseDeckNativePlanned,
    Unknown,
}

internal data class DspChainInfo(
    val label: String,
    val detail: String,
    val processingActive: Boolean,
    val nativeMedia3DspRequested: Boolean,
    val nativeMedia3DspActive: Boolean,
    val platformEffectsActive: Boolean,
    val replayGainActive: Boolean,
    val replayGainAdjustmentDb: Float,
    val headroomDb: Float,
    val activeStages: List<String>,
)

internal data class OutputEngineInfo(
    val label: String,
    val detail: String,
    val requestedMode: String,
    val actualPlugin: String,
    val bufferMode: String,
    val outputGainPercent: Int,
)

internal data class OutputDeviceInfo(
    val label: String,
    val detail: String,
    val requestedProfile: String,
    val activeRoute: String,
    val verified: Boolean,
)

internal enum class HiResOutputStatus {
    Off,
    Requested,
    Blocked,
    NotVerified,
    HighSampleRateActive,
    HighBitDepthActive,
    FullHiResActive,
    Unknown,
}

internal enum class HiResEvidenceSource {
    None,
    Media3AudioTrackRuntime,
    Media3CustomProviderRuntime,
    AudioFlingerVerificationOnly,
    OboeAaudioRuntime,
    Unknown,
}

internal data class HiResOutputEvidence(
    val sourceSampleRateHz: Int? = null,
    val sourceBitDepth: Int? = null,
    val decodedSampleRateHz: Int? = null,
    val decodedEncoding: String? = null,
    val outputSampleRateHz: Int? = null,
    val outputEncoding: String? = null,
    val routeType: OutputDeviceType = OutputDeviceType.Other,
    val engine: ProOutputEngine = ProOutputEngine.Media3Default,
    val providerActive: Boolean = false,
    val underruns: Int? = null,
    val evidenceSource: HiResEvidenceSource = HiResEvidenceSource.None,
    val blockers: List<ProOutputBlocker> = emptyList(),
)

internal data class OutputCapabilitySnapshot(
    val route: OutputDeviceType,
    val requestedPlugin: OutputPluginType,
    val actualPlugin: OutputPluginType?,
    val status: OutputVerificationStatus,
    val requestedSampleRateHz: Int?,
    val requestedBitDepth: OutputBitDepth,
    val supportedSampleRatesHz: List<Int> = emptyList(),
    val supportedEncodings: List<String> = emptyList(),
    val hiResRequested: Boolean,
    val hiResActive: Boolean = false,
    val usbDacRequested: Boolean,
    val usbDacActive: Boolean = false,
    val hiResOutputStatus: HiResOutputStatus = HiResOutputStatus.Unknown,
    val hiResEvidence: HiResOutputEvidence = HiResOutputEvidence(),
    val audioTrackSampleRateHz: Int? = null,
    val audioTrackEncoding: String? = null,
    val reason: String?,
    val warning: String?,
)

internal enum class OutputVerificationStatus {
    Verified,
    Requested,
    NotVerified,
    Unsupported,
    Unknown,
}

internal data class BitPerfectAttemptSnapshot(
    val userAttemptEnabled: Boolean,
    val eligible: Boolean,
    val active: Boolean,
    val status: BitPerfectAttemptStatus,
    val conflicts: List<BitPerfectConflict>,
    val sourceSampleRateHz: Int?,
    val decodedSampleRateHz: Int?,
    val outputSampleRateHz: Int?,
    val requestedBitDepth: OutputBitDepth,
    val reason: String,
    val warning: String?,
)

internal data class BitPerfectConflict(
    val type: BitPerfectConflictType,
    val detail: String,
)

internal enum class BitPerfectAttemptStatus {
    Off,
    Eligible,
    Ineligible,
    Requested,
    Active,
    Unknown,
}

internal enum class BitPerfectConflictType {
    DspActive,
    ReplayGainActive,
    EqActive,
    ToneActive,
    StereoToolsActive,
    DynamicsActive,
    TimeEffectsActive,
    CrossfadeActive,
    ResamplerMismatch,
    OutputTrimActive,
    HiResUnverified,
    DeviceUnverified,
    AudioTrackMismatch,
    BluetoothRoute,
    SourceUnknown,
    DecoderUnknown,
    DitherActive,
}

internal enum class AudioChainWarningLevel {
    Info,
    Caution,
}

internal data class AudioChainWarning(
    val title: String,
    val detail: String,
    val level: AudioChainWarningLevel = AudioChainWarningLevel.Info,
)

internal fun audioChainSnapshot(
    state: AudioEngineState,
    track: Track,
    quality: String,
    diagnostics: OutputDiagnosticsSnapshot = OutputDiagnosticsSnapshot(),
    nativeAvailable: Boolean = false,
): AudioChainSnapshot {
    val qualityInfo = parseAudioQuality(quality, track.mimeType)
    val policy = state.effectiveProcessingPolicy(nativeAvailable = nativeAvailable)
    val runtimeReported = diagnostics.updatedAtMillis > 0L
    val nativeRequested = if (runtimeReported) diagnostics.nativeMedia3DspRequested else policy.nativeMedia3DspRequested
    val nativeActive = if (runtimeReported) diagnostics.nativeMedia3DspActive else policy.nativeMedia3DspActive
    val replayGainAdjustmentDb = if (runtimeReported) diagnostics.replayGainAdjustmentDb else policy.replayGainAdjustmentDb
    val headroomDb = if (runtimeReported) diagnostics.estimatedHeadroomDb else policy.estimatedHeadroomDb
    val preventClippingActive = if (runtimeReported) diagnostics.preventClippingActive else policy.preventClippingActive
    val platformEffectsActive = platformSpectralEffectsActive(state, nativeActive)
    val routeStatus = outputRouteStatusFor(state.output.deviceProfile, diagnostics)
    val runtimeFormat = diagnostics.decoderFormat.takeIf { it.updatedAtMillis > 0L }
    val source = audioSourceInfo(track, qualityInfo, runtimeFormat)
    val resamplerStrategy = resamplerStrategySnapshot(state, qualityInfo, runtimeFormat)
    val outputCapability = outputCapabilitySnapshot(state, diagnostics, routeStatus, source, resamplerStrategy)
    val bitPerfect = bitPerfectAttemptSnapshot(
        state = state,
        policy = policy,
        resampler = resamplerStrategy,
        outputCapability = outputCapability,
        runtimeReported = runtimeReported,
    )
    val proOutput = proOutputEngineState(
        state = state,
        track = track,
        source = source,
        resampler = resamplerStrategy,
        outputCapability = outputCapability,
        diagnostics = diagnostics,
        policy = policy,
        bitPerfect = bitPerfect,
    )
    val warnings = audioChainWarnings(
        state = state,
        source = source,
        diagnostics = diagnostics,
        routeStatus = routeStatus,
        nativeRequested = nativeRequested,
        nativeActive = nativeActive,
        fallbackReason = diagnostics.dspFallbackReason ?: policy.fallbackReason,
        outputCapability = outputCapability,
        proOutput = proOutput,
    )

    return AudioChainSnapshot(
        source = source,
        decoder = decoderInfo(qualityInfo, runtimeFormat),
        resampler = resamplerInfo(state, qualityInfo, runtimeFormat),
        resamplerStrategy = resamplerStrategy,
        dsp = dspChainInfo(
            state = state,
            policy = policy,
            nativeRequested = nativeRequested,
            nativeActive = nativeActive,
            platformEffectsActive = platformEffectsActive,
            replayGainAdjustmentDb = replayGainAdjustmentDb,
            headroomDb = headroomDb,
            preventClippingActive = preventClippingActive,
        ),
        output = outputEngineInfo(state, routeStatus),
        device = outputDeviceInfo(state, diagnostics, routeStatus),
        outputCapability = outputCapability,
        proOutput = proOutput,
        bitPerfect = bitPerfect,
        warnings = warnings,
    )
}

private data class ParsedAudioQuality(
    val format: String,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val bitrateKbps: Int?,
)

private fun parseAudioQuality(quality: String, mimeType: String?): ParsedAudioQuality {
    val upper = quality.uppercase(Locale.US)
    val sampleRateHz = Regex("""(\d+(?:\.\d+)?)\s*K\s*H(?:Z)?\b""", RegexOption.IGNORE_CASE)
        .find(quality)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
        ?.let { (it * 1000f).roundToInt() }
    val bitDepth = Regex("""\b(16|24|32)\s*(?:-| )?\s*bit\b""", RegexOption.IGNORE_CASE)
        .find(quality)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val bitrateKbps = Regex("""(\d+)\s*KBPS""", RegexOption.IGNORE_CASE)
        .find(quality)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val channels = when {
        "STEREO" in upper -> 2
        "MONO" in upper -> 1
        else -> Regex("""\b(\d+)\s*CH\b""", RegexOption.IGNORE_CASE)
            .find(quality)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
    val format = when {
        !mimeType.isNullOrBlank() -> audioTypeLabel(mimeType)
        "MP4A" in upper && "LATM" in upper -> "MP4A LATM"
        "FLAC" in upper -> "FLAC"
        "ALAC" in upper -> "ALAC"
        "OPUS" in upper -> "OPUS"
        "OGG" in upper -> "OGG"
        "AAC" in upper -> "AAC"
        "WAV" in upper -> "WAV"
        "MP3" in upper -> "MP3"
        else -> "AUDIO"
    }
    return ParsedAudioQuality(
        format = format,
        sampleRateHz = sampleRateHz,
        bitDepth = bitDepth,
        channels = channels,
        bitrateKbps = bitrateKbps,
    )
}

private fun audioSourceInfo(
    track: Track,
    quality: ParsedAudioQuality,
    runtimeFormat: DecoderFormatDiagnosticsSnapshot?,
): AudioSourceInfo {
    val scheme = track.uri?.scheme?.lowercase(Locale.US)
    val albumTitle = track.album.title
    val folder = track.folderPath.orEmpty()
    val live = track.duration.equals("LIVE", ignoreCase = true) || track.durationMillis <= 0L && albumTitle.equals("PulseRadio", ignoreCase = true)
    val sourceMimeType = cleanMimeType(track.mimeType)
        ?: runtimeFormat?.sampleMimeType
        ?: runtimeFormat?.containerMimeType
    val sourceLabel = when {
        albumTitle.equals("PulseRadio", ignoreCase = true) || track.albumArtist.equals("PulseRadio", ignoreCase = true) -> "PulseRadio stream"
        scheme == "pulsedeck" -> "PremiumDeck queue"
        folder.contains("Stream Library", ignoreCase = true) && scheme in setOf("http", "https") -> "PremiumDeck stream"
        folder.contains("Stream Library", ignoreCase = true) -> "Saved PremiumDeck file"
        scheme in setOf("http", "https") -> "Network stream"
        scheme in setOf("content", "file") -> "Local library file"
        else -> "Library item"
    }
    val sourceLocation = sourceLocationLabel(scheme)
    return AudioSourceInfo(
        label = sourceLabel,
        detail = listOf(track.title, track.artist, sourceLocation)
            .filter { it.isNotBlank() }
            .joinToString(" / "),
        format = sourceMimeType?.let(::audioTypeLabel) ?: quality.format,
        mimeType = sourceMimeType,
        containerMimeType = runtimeFormat?.containerMimeType,
        sampleRateHz = quality.sampleRateHz ?: runtimeFormat?.encodedSampleRateHz,
        bitDepth = quality.bitDepth,
        channels = quality.channels ?: runtimeFormat?.encodedChannelCount,
        bitrateKbps = quality.bitrateKbps ?: runtimeFormat?.averageBitrateKbps ?: runtimeFormat?.peakBitrateKbps,
        uriScheme = scheme,
        live = live,
    )
}

private fun decoderInfo(
    quality: ParsedAudioQuality,
    runtimeFormat: DecoderFormatDiagnosticsSnapshot?,
): DecoderInfo {
    val mimeType = runtimeFormat?.sampleMimeType
    val decodedShape = audioShapeLabel(runtimeFormat?.decodedSampleRateHz, runtimeFormat?.decodedChannelCount)
    val detail = if (runtimeFormat == null) {
        "Runtime decoder format unknown; Media3 owns decode into the PCM path"
    } else {
        buildList {
            mimeType?.let { add("Input $it") }
            runtimeFormat.containerMimeType?.takeIf { it != mimeType }?.let { add("Container $it") }
            runtimeFormat.codecString?.let { add("Codec $it") }
            decodedShape?.let { add("Decoded $it") }
            add("Hardware/software unknown")
        }.joinToString(" | ")
    }
    val codec = runtimeFormat?.codecString
        ?: mimeType?.let(::audioTypeLabel)
        ?: quality.format
    val outputEncoding = runtimeFormat?.decodedPcmEncoding ?: "PCM"
    return DecoderInfo(
        label = runtimeFormat?.decoderName?.takeIf { it.isNotBlank() } ?: "Media3 ExoPlayer",
        detail = detail,
        codec = codec,
        outputEncoding = outputEncoding,
        mimeType = mimeType,
        codecString = runtimeFormat?.codecString,
        decoderName = runtimeFormat?.decoderName,
        decodedSampleRateHz = runtimeFormat?.decodedSampleRateHz,
        decodedChannels = runtimeFormat?.decodedChannelCount,
        hardwareAcceleration = "Unknown",
    )
}

private fun resamplerInfo(
    state: AudioEngineState,
    quality: ParsedAudioQuality,
    runtimeFormat: DecoderFormatDiagnosticsSnapshot?,
): ResamplerInfo {
    val requested = state.resampler.outputSampleRate.label
    val auto = state.resampler.mode == ResamplerMode.Auto && state.resampler.outputSampleRate == OutputSampleRate.Auto
    val sourceRateHz = runtimeFormat?.decodedSampleRateHz
        ?: runtimeFormat?.encodedSampleRateHz
        ?: quality.sampleRateHz
    val requestedRateHz = state.resampler.outputSampleRate.toHzOrNull()
    val sourceRate = sourceRateHz?.let(::formatSampleRate) ?: "source rate unknown"
    val knownMismatch = sourceRateHz != null && requestedRateHz != null && sourceRateHz != requestedRateHz
    return ResamplerInfo(
        label = when {
            auto -> "System-managed"
            knownMismatch -> "Known source != output request"
            requestedRateHz != null -> state.resampler.mode.label
            else -> "Not active / not known"
        },
        detail = when {
            auto -> "PulseDeck is not inserting a custom resampler; Media3 and Android own final device rate"
            knownMismatch -> "Source $sourceRate differs from requested $requested; final device rate is not verified"
            requestedRateHz != null -> "${state.resampler.mode.label} requested from $sourceRate toward $requested"
            sourceRateHz != null -> "Source $sourceRate is known; output route sample rate is not verified"
            else -> "Source and output route sample rates are unknown"
        },
        requestedSampleRate = requested,
        effectiveSampleRate = when {
            auto -> "System-managed"
            knownMismatch -> "Likely system resampled"
            requestedRateHz != null -> "Requested $requested, not device-verified"
            sourceRateHz != null -> "Output route unknown"
            else -> "Unknown"
        },
        dither = state.resampler.dither,
        verified = false,
    )
}

private fun resamplerStrategySnapshot(
    state: AudioEngineState,
    quality: ParsedAudioQuality,
    runtimeFormat: DecoderFormatDiagnosticsSnapshot?,
): ResamplerStrategySnapshot {
    val sourceRateHz = runtimeFormat?.encodedSampleRateHz ?: quality.sampleRateHz
    val decodedRateHz = runtimeFormat?.decodedSampleRateHz
    val workingRateHz = decodedRateHz ?: sourceRateHz
    val requestedOutputRateHz = state.output.sampleRate.toHzOrNull()
        ?: state.resampler.outputSampleRate.toHzOrNull()
    val plannedPulseDeckResampler = state.resampler.mode == ResamplerMode.HighQuality
    val status = when {
        plannedPulseDeckResampler -> ResamplerStatus.PulseDeckPlanned
        workingRateHz == null -> ResamplerStatus.Unknown
        requestedOutputRateHz == null -> ResamplerStatus.SystemManaged
        workingRateHz == requestedOutputRateHz -> ResamplerStatus.NotNeeded
        else -> ResamplerStatus.LikelySystemResampled
    }
    val strategy = when {
        state.resampler.batterySaverMode -> ResamplerStrategy.BatterySaver
        plannedPulseDeckResampler -> ResamplerStrategy.HighQualityPlanned
        requestedOutputRateHz != null && workingRateHz != null && requestedOutputRateHz == workingRateHz -> ResamplerStrategy.MatchSourceIfPossible
        requestedOutputRateHz != null -> ResamplerStrategy.MatchDevicePreferred
        else -> ResamplerStrategy.FollowSystem
    }
    val qualityMode = when {
        state.resampler.batterySaverMode -> ResamplerQualityMode.BatterySaver
        plannedPulseDeckResampler && state.resampler.quality == ResamplerQuality.Ultra -> ResamplerQualityMode.UltraPlanned
        plannedPulseDeckResampler -> ResamplerQualityMode.HighQualityPlanned
        workingRateHz == null -> ResamplerQualityMode.Unknown
        else -> ResamplerQualityMode.Standard
    }
    val activeEngine = when {
        plannedPulseDeckResampler -> ResamplerEngine.PulseDeckNativePlanned
        state.resampler.mode == ResamplerMode.System || requestedOutputRateHz != null -> ResamplerEngine.AndroidSystem
        state.resampler.mode == ResamplerMode.Auto -> ResamplerEngine.Media3Default
        else -> ResamplerEngine.Unknown
    }
    val sourceLabel = workingRateHz?.let(::formatSampleRate) ?: "unknown source rate"
    val outputLabel = requestedOutputRateHz?.let(::formatSampleRate) ?: "system-selected output rate"
    return ResamplerStrategySnapshot(
        sourceSampleRateHz = sourceRateHz,
        decodedSampleRateHz = decodedRateHz,
        outputSampleRateHz = requestedOutputRateHz,
        devicePreferredSampleRateHz = null,
        resamplerStatus = status,
        strategy = strategy,
        qualityMode = qualityMode,
        activeEngine = activeEngine,
        reason = when (status) {
            ResamplerStatus.NotNeeded -> "$sourceLabel matches requested $outputLabel; device route rate is still not capability-verified."
            ResamplerStatus.SystemManaged -> "No fixed output sample-rate request; Media3 and Android negotiate the route rate."
            ResamplerStatus.LikelySystemResampled -> "$sourceLabel differs from requested $outputLabel; final device rate is not verified."
            ResamplerStatus.PulseDeckPlanned -> "High-quality resampling is a PulseDeck strategy setting only; no custom HQ resampler is active in this phase."
            ResamplerStatus.PulseDeckActive -> "PulseDeck-native resampling is active."
            ResamplerStatus.Unknown -> "Decoder or route diagnostics do not yet expose enough sample-rate information."
        },
        warning = when (status) {
            ResamplerStatus.LikelySystemResampled -> "Likely system resampling; output route sample rate is not verified."
            ResamplerStatus.PulseDeckPlanned -> "No PulseDeck HQ resampler is active in Phase 15A."
            ResamplerStatus.PulseDeckActive -> null
            else -> null
        },
    )
}

private fun outputCapabilitySnapshot(
    state: AudioEngineState,
    diagnostics: OutputDiagnosticsSnapshot,
    routeStatus: OutputRouteStatus?,
    source: AudioSourceInfo,
    resampler: ResamplerStrategySnapshot,
): OutputCapabilitySnapshot {
    val route = outputDeviceTypeFor(state.output.deviceProfile)
    val requestedPlugin = routeStatus?.requestedPlugin ?: requestedOutputPlugin(state.output.mode)
    val requestedSampleRateHz = state.output.sampleRate.toHzOrNull()
        ?: state.resampler.outputSampleRate.toHzOrNull()
    val hiResRequested = state.output.mode == OutputMode.HiRes || state.output.hiResEnabled || requestedPlugin == OutputPluginType.HiRes
    val usbDacRequested = state.output.deviceProfile == DeviceProfile.UsbDac || route == OutputDeviceType.UsbDac
    val device = diagnostics.outputDevice
    val audioTrack = diagnostics.audioTrack
    val supportedSampleRates = device.supportedSampleRatesHz
    val supportedEncodings = device.supportedEncodings
    val activeRoute = device.activeRoute ?: route
    val decodedSampleRateHz = resampler.decodedSampleRateHz ?: diagnostics.decoderFormat.decodedSampleRateHz
    val decodedEncoding = diagnostics.decoderFormat.decodedPcmEncoding
    val sourceSampleRateHz = source.sampleRateHz ?: resampler.sourceSampleRateHz
    val sourceBitDepth = source.bitDepth
    val runtimeOutputFormat = freshestRuntimeOutputFormat(audioTrack, diagnostics.proOutput)
    val outputSampleRateHz = runtimeOutputFormat.sampleRateHz
    val outputEncoding = runtimeOutputFormat.encoding
    val engine = proOutputEngineForRuntime(diagnostics.proOutput.engine)
    val providerActive = diagnostics.proOutput.engine == ProOutputRuntimeEngine.Media3CustomOutput &&
        diagnostics.proOutput.status == ProOutputRuntimeStatus.Active
    val underruns = maxOf(audioTrack.underrunCount, diagnostics.proOutput.underrunCount)
    val routeVerified = routeStatus?.available == true || device.routeVerified
    val hiResEvidence = hiResOutputEvidence(
        hiResRequested = hiResRequested,
        route = activeRoute,
        routeVerified = routeVerified,
        sourceSampleRateHz = sourceSampleRateHz,
        sourceBitDepth = sourceBitDepth,
        decodedSampleRateHz = decodedSampleRateHz,
        decodedEncoding = decodedEncoding,
        outputSampleRateHz = outputSampleRateHz,
        outputEncoding = outputEncoding,
        engine = engine,
        providerActive = providerActive,
        audioTrackUpdatedAtMillis = audioTrack.updatedAtMillis,
        proOutputUpdatedAtMillis = diagnostics.proOutput.updatedAtMillis,
        underruns = underruns,
        requestedBitDepth = state.output.bitDepth,
        requestedSampleRateHz = requestedSampleRateHz,
        runtimeFallback = diagnostics.proOutput.status == ProOutputRuntimeStatus.FallbackToMedia3 ||
            diagnostics.proOutput.status == ProOutputRuntimeStatus.Failed,
    )
    val hiResOutputStatus = hiResEvidence.status
    val hiResActive = hiResOutputStatus == HiResOutputStatus.FullHiResActive
    val usbDacActive = usbDacRequested &&
        device.usbDacDetected &&
        device.routeVerified &&
        device.activeRoute == OutputDeviceType.UsbDac
    val status = when {
        diagnostics.updatedAtMillis == 0L -> OutputVerificationStatus.Unknown
        routeStatus == null -> if (hiResRequested || usbDacRequested) OutputVerificationStatus.NotVerified else OutputVerificationStatus.Unknown
        !routeStatus.available -> OutputVerificationStatus.Unsupported
        hiResOutputStatus in setOf(
            HiResOutputStatus.HighSampleRateActive,
            HiResOutputStatus.HighBitDepthActive,
            HiResOutputStatus.FullHiResActive,
        ) || usbDacActive -> OutputVerificationStatus.Verified
        hiResRequested || usbDacRequested -> OutputVerificationStatus.Requested
        else -> OutputVerificationStatus.Verified
    }
    val reason = when (status) {
        OutputVerificationStatus.Verified -> when {
            hiResOutputStatus == HiResOutputStatus.FullHiResActive ->
                "Runtime AudioTrack evidence shows both high sample rate and high-precision PCM output."
            hiResOutputStatus == HiResOutputStatus.HighSampleRateActive ->
                "Runtime AudioTrack evidence shows high-sample-rate output; output bit depth is not high precision."
            hiResOutputStatus == HiResOutputStatus.HighBitDepthActive ->
                "Runtime AudioTrack evidence shows high-precision PCM output; output sample rate is not high-rate."
            usbDacActive -> "USB DAC device is present and was requested as the preferred Media3 output device."
            supportedSampleRates.isNotEmpty() -> "Route resolved to ${routeStatus?.actualPlugin?.label ?: requestedPlugin.label}; Android reported sample-rate capabilities."
            else -> "Route resolved to ${routeStatus?.actualPlugin?.label ?: requestedPlugin.label}; hardware sample-rate capability table was not reported."
        }
        OutputVerificationStatus.Requested -> "Requested ${requestedPlugin.label} for ${route.label}; sample-rate and encoding capabilities are not verified by diagnostics."
        OutputVerificationStatus.NotVerified -> "Output diagnostics do not yet verify this requested route."
        OutputVerificationStatus.Unsupported -> routeStatus?.reason ?: "Requested output route fell back to ${routeStatus?.actualPlugin?.label ?: OutputPluginType.Media3AudioTrack.label}."
        OutputVerificationStatus.Unknown -> "Playback service diagnostics have not reported capability status for this route."
    }
    val warning = when (status) {
        OutputVerificationStatus.Requested -> "Request only; do not treat as verified hi-res, USB DAC, or bit-perfect output."
        OutputVerificationStatus.NotVerified -> "Route capability is not verified."
        OutputVerificationStatus.Unsupported -> "Requested route is unavailable and is using fallback output."
        OutputVerificationStatus.Unknown -> "Route capability is unknown until runtime diagnostics settle."
        OutputVerificationStatus.Verified -> null
    }
    return OutputCapabilitySnapshot(
        route = route,
        requestedPlugin = requestedPlugin,
        actualPlugin = routeStatus?.actualPlugin,
        status = status,
        requestedSampleRateHz = requestedSampleRateHz,
        requestedBitDepth = state.output.bitDepth,
        supportedSampleRatesHz = supportedSampleRates,
        supportedEncodings = supportedEncodings,
        hiResRequested = hiResRequested,
        hiResActive = hiResActive,
        usbDacRequested = usbDacRequested,
        usbDacActive = usbDacActive,
        hiResOutputStatus = hiResOutputStatus,
        hiResEvidence = hiResEvidence.evidence,
        audioTrackSampleRateHz = outputSampleRateHz,
        audioTrackEncoding = outputEncoding,
        reason = reason,
        warning = warning,
    )
}

private data class RuntimeOutputFormat(
    val sampleRateHz: Int?,
    val encoding: String?,
)

private fun freshestRuntimeOutputFormat(
    audioTrack: AudioTrackDiagnosticsSnapshot,
    proOutput: ProOutputRuntimeSnapshot,
): RuntimeOutputFormat {
    val preferProOutput = proOutput.updatedAtMillis > 0L &&
        (audioTrack.updatedAtMillis == 0L || proOutput.updatedAtMillis >= audioTrack.updatedAtMillis)
    val primaryRate = if (preferProOutput) proOutput.actualSampleRateHz else audioTrack.configuredSampleRateHz
    val fallbackRate = if (preferProOutput) audioTrack.configuredSampleRateHz else proOutput.actualSampleRateHz
    val primaryEncoding = if (preferProOutput) proOutput.actualEncoding else audioTrack.encoding
    val fallbackEncoding = if (preferProOutput) audioTrack.encoding else proOutput.actualEncoding
    return RuntimeOutputFormat(
        sampleRateHz = primaryRate ?: fallbackRate,
        encoding = primaryEncoding ?: fallbackEncoding,
    )
}

private data class HiResOutputEvidenceBuild(
    val status: HiResOutputStatus,
    val evidence: HiResOutputEvidence,
)

private fun hiResOutputEvidence(
    hiResRequested: Boolean,
    route: OutputDeviceType,
    routeVerified: Boolean,
    sourceSampleRateHz: Int?,
    sourceBitDepth: Int?,
    decodedSampleRateHz: Int?,
    decodedEncoding: String?,
    outputSampleRateHz: Int?,
    outputEncoding: String?,
    engine: ProOutputEngine,
    providerActive: Boolean,
    audioTrackUpdatedAtMillis: Long,
    proOutputUpdatedAtMillis: Long,
    underruns: Int,
    requestedBitDepth: OutputBitDepth,
    requestedSampleRateHz: Int?,
    runtimeFallback: Boolean,
): HiResOutputEvidenceBuild {
    val outputHighSampleRate = outputSampleRateHz?.let { it >= 88_200 } == true
    val outputHighBitDepth = outputEncoding.isHighPrecisionPcmLabel()
    val sourceLooksHiRes = sourceSampleRateHz?.let { it >= 88_200 } == true ||
        sourceBitDepth?.let { it > 16 } == true ||
        decodedSampleRateHz?.let { it >= 88_200 } == true ||
        decodedEncoding.isHighPrecisionPcmLabel()
    val evidenceSource = when {
        proOutputUpdatedAtMillis > 0L && engine == ProOutputEngine.Media3CustomOutput -> HiResEvidenceSource.Media3CustomProviderRuntime
        proOutputUpdatedAtMillis > 0L && engine == ProOutputEngine.OboeAaudio -> HiResEvidenceSource.OboeAaudioRuntime
        audioTrackUpdatedAtMillis > 0L || proOutputUpdatedAtMillis > 0L -> HiResEvidenceSource.Media3AudioTrackRuntime
        else -> HiResEvidenceSource.None
    }
    val blockers = buildList {
        if (route == OutputDeviceType.Bluetooth) add(ProOutputBlocker.BluetoothRoute)
        if (!routeVerified && hiResRequested) add(ProOutputBlocker.OutputRouteUnknown)
        if (outputSampleRateHz == null && hiResRequested) add(ProOutputBlocker.OutputSampleRateUnknown)
        if (outputEncoding == null && hiResRequested) add(ProOutputBlocker.OutputEncodingUnknown)
        if (!sourceLooksHiRes && hiResRequested) add(ProOutputBlocker.SourceNotHiRes)
        if (decodedSampleRateHz == null && decodedEncoding == null && hiResRequested) add(ProOutputBlocker.DecodeFormatUnknown)
        if (
            requestedSampleRateHz?.let { it >= 88_200 } == true &&
            outputSampleRateHz != null &&
            !outputHighSampleRate
        ) {
            add(ProOutputBlocker.DeviceRejectedFormat)
        }
        if (requestedBitDepth.isHiResDepth() && outputEncoding != null && !outputHighBitDepth) {
            add(ProOutputBlocker.DeviceRejectedFormat)
        }
        if (runtimeFallback) add(ProOutputBlocker.Media3CustomOutputUnavailable)
        if (underruns > 0) add(ProOutputBlocker.UnderrunRisk)
    }.distinct()
    val routeBlocksOutputClaim = route == OutputDeviceType.Bluetooth || route == OutputDeviceType.Chromecast
    val activeStatus = when {
        !sourceLooksHiRes -> null
        outputHighSampleRate && outputHighBitDepth -> HiResOutputStatus.FullHiResActive
        outputHighSampleRate -> HiResOutputStatus.HighSampleRateActive
        outputHighBitDepth -> HiResOutputStatus.HighBitDepthActive
        else -> null
    }
    val status = when {
        evidenceSource == HiResEvidenceSource.None && hiResRequested -> HiResOutputStatus.Requested
        evidenceSource == HiResEvidenceSource.None -> HiResOutputStatus.Off
        routeBlocksOutputClaim || underruns > 0 -> if (hiResRequested) HiResOutputStatus.Blocked else HiResOutputStatus.NotVerified
        ProOutputBlocker.SourceNotHiRes in blockers -> HiResOutputStatus.Blocked
        activeStatus != null -> activeStatus
        hiResRequested -> HiResOutputStatus.NotVerified
        else -> HiResOutputStatus.Off
    }
    return HiResOutputEvidenceBuild(
        status = status,
        evidence = HiResOutputEvidence(
            sourceSampleRateHz = sourceSampleRateHz,
            sourceBitDepth = sourceBitDepth,
            decodedSampleRateHz = decodedSampleRateHz,
            decodedEncoding = decodedEncoding,
            outputSampleRateHz = outputSampleRateHz,
            outputEncoding = outputEncoding,
            routeType = route,
            engine = engine,
            providerActive = providerActive,
            underruns = underruns.takeIf { evidenceSource != HiResEvidenceSource.None },
            evidenceSource = evidenceSource,
            blockers = blockers,
        ),
    )
}

private fun bitPerfectAttemptSnapshot(
    state: AudioEngineState,
    policy: EffectiveAudioProcessingPolicy,
    resampler: ResamplerStrategySnapshot,
    outputCapability: OutputCapabilitySnapshot,
    runtimeReported: Boolean,
): BitPerfectAttemptSnapshot {
    val conflicts = buildList {
        if (policy.processingActive) {
            add(BitPerfectConflict(BitPerfectConflictType.DspActive, "PulseDeck DSP processing is active."))
        }
        if (policy.replayGainActive || state.replayGainMode != ReplayGainMode.Off || abs(policy.replayGainAdjustmentDb) > 0.001f) {
            add(BitPerfectConflict(BitPerfectConflictType.ReplayGainActive, "ReplayGain or source loudness gain can alter PCM level."))
        }
        if (policy.eqStageActive) {
            add(BitPerfectConflict(BitPerfectConflictType.EqActive, "Graphic or parametric EQ is active."))
        }
        if (policy.toneStageActive || policy.loudnessStageActive) {
            add(BitPerfectConflict(BitPerfectConflictType.ToneActive, "Tone or loudness shaping is active."))
        }
        if (policy.stereoStageActive) {
            add(BitPerfectConflict(BitPerfectConflictType.StereoToolsActive, "Stereo matrix tools alter channel data."))
        }
        if (policy.dynamicsStageActive) {
            add(BitPerfectConflict(BitPerfectConflictType.DynamicsActive, "Dynamics processing is active or armed."))
        }
        if (policy.timeFxStageActive || policy.pitchTempoActive) {
            add(BitPerfectConflict(BitPerfectConflictType.TimeEffectsActive, "Time-domain or tempo/pitch processing is active."))
        }
        if (state.crossfade.crossfadeEnabled) {
            add(BitPerfectConflict(BitPerfectConflictType.CrossfadeActive, "Crossfade overlaps or fades PCM between tracks."))
        }
        if (state.resampler.dither) {
            add(BitPerfectConflict(BitPerfectConflictType.DitherActive, "Dither modifies the output sample stream."))
        }
        if (resampler.resamplerStatus == ResamplerStatus.LikelySystemResampled) {
            add(BitPerfectConflict(BitPerfectConflictType.ResamplerMismatch, "Known decoded/source rate differs from the requested output rate."))
        }
        if (
            policy.processingActive &&
            (
                abs(audioOutputGain(state, 1f) - 1f) > 0.001f ||
                    abs(state.preampDb + state.activeReplayGainDb) > 0.001f ||
                    policy.preventClippingActive
                )
        ) {
            add(BitPerfectConflict(BitPerfectConflictType.OutputTrimActive, "Preamp, ReplayGain, or headroom changes output gain."))
        }
        if (outputCapability.hiResRequested && outputCapability.status != OutputVerificationStatus.Verified) {
            add(BitPerfectConflict(BitPerfectConflictType.HiResUnverified, "Hi-Res output is requested but not verified."))
        }
        if (!runtimeReported || outputCapability.status != OutputVerificationStatus.Verified || outputCapability.supportedSampleRatesHz.isEmpty()) {
            add(BitPerfectConflict(BitPerfectConflictType.DeviceUnverified, "Output device route and exact hardware format are not verified for bit-perfect playback."))
        }
        if (
            outputCapability.audioTrackSampleRateHz != null &&
            resampler.decodedSampleRateHz != null &&
            outputCapability.audioTrackSampleRateHz != resampler.decodedSampleRateHz
        ) {
            add(BitPerfectConflict(BitPerfectConflictType.AudioTrackMismatch, "Configured AudioTrack rate differs from decoded PCM rate."))
        }
        if (outputCapability.route == OutputDeviceType.Bluetooth) {
            add(BitPerfectConflict(BitPerfectConflictType.BluetoothRoute, "Bluetooth transport prevents PulseDeck from verifying bit-perfect output."))
        }
        if (
            outputCapability.audioTrackEncoding != null &&
            outputCapability.audioTrackEncoding == "PCM16" &&
            outputCapability.requestedBitDepth.isHiResDepth()
        ) {
            add(BitPerfectConflict(BitPerfectConflictType.AudioTrackMismatch, "Configured AudioTrack encoding is PCM16 while high-precision output is requested."))
        }
        if (resampler.sourceSampleRateHz == null) {
            add(BitPerfectConflict(BitPerfectConflictType.SourceUnknown, "Source sample rate is unknown."))
        }
        if (resampler.decodedSampleRateHz == null) {
            add(BitPerfectConflict(BitPerfectConflictType.DecoderUnknown, "Runtime decoded PCM format has not been reported."))
        }
    }
    val eligible = conflicts.isEmpty()
    val requested = state.output.bitPerfectAttemptEnabled
    val active = requested &&
        eligible &&
        outputCapability.audioTrackSampleRateHz != null &&
        outputCapability.audioTrackSampleRateHz == resampler.decodedSampleRateHz &&
        outputCapability.route != OutputDeviceType.Bluetooth
    val status = when {
        !requested -> BitPerfectAttemptStatus.Off
        active -> BitPerfectAttemptStatus.Active
        eligible -> BitPerfectAttemptStatus.Eligible
        runtimeReported -> BitPerfectAttemptStatus.Ineligible
        else -> BitPerfectAttemptStatus.Requested
    }
    return BitPerfectAttemptSnapshot(
        userAttemptEnabled = requested,
        eligible = eligible,
        active = active,
        status = status,
        conflicts = conflicts,
        sourceSampleRateHz = resampler.sourceSampleRateHz,
        decodedSampleRateHz = resampler.decodedSampleRateHz,
        outputSampleRateHz = outputCapability.audioTrackSampleRateHz ?: resampler.outputSampleRateHz,
        requestedBitDepth = outputCapability.requestedBitDepth,
        reason = when {
            active -> "Bit-perfect attempt is active for the currently verified decoded PCM and AudioTrack rate."
            requested && eligible -> "Bit-perfect attempt is eligible, but active AudioTrack verification is still pending."
            requested -> "Bit-perfect attempt requested; ${conflicts.size} blocking condition(s) prevent activation."
            eligible -> "No blocking conflicts are visible, but bit-perfect attempt mode is off."
            else -> "Bit-perfect attempt is inactive; ${conflicts.size} blocking condition(s) are visible."
        },
        warning = when {
            active -> null
            requested -> "Attempt mode is user-controlled and will not silently disable DSP, ReplayGain, resampling, or routing conflicts."
            else -> "No bit-perfect output is active or claimed."
        },
    )
}

internal fun sourceLocationLabel(scheme: String?): String =
    when (scheme) {
        "http" -> "HTTP stream"
        "https" -> "HTTPS stream"
        "content" -> "content URI"
        "file" -> "local file URI"
        "pulsedeck" -> "PulseDeck queue item"
        null -> "no URI"
        else -> "$scheme URI"
    }

private fun audioShapeLabel(sampleRateHz: Int?, channels: Int?): String? {
    val parts = buildList {
        sampleRateHz?.let { add(formatSampleRate(it)) }
        channels?.let {
            add(
                when (it) {
                    1 -> "mono"
                    2 -> "stereo"
                    else -> "$it ch"
                },
            )
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

private fun OutputSampleRate.toHzOrNull(): Int? =
    when (this) {
        OutputSampleRate.Auto -> null
        OutputSampleRate.Hz44100 -> 44_100
        OutputSampleRate.Hz48000 -> 48_000
        OutputSampleRate.Hz96000 -> 96_000
        OutputSampleRate.Hz192000 -> 192_000
    }

private fun cleanMimeType(value: String?): String? =
    value
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun dspChainInfo(
    state: AudioEngineState,
    policy: EffectiveAudioProcessingPolicy,
    nativeRequested: Boolean,
    nativeActive: Boolean,
    platformEffectsActive: Boolean,
    replayGainAdjustmentDb: Float,
    headroomDb: Float,
    preventClippingActive: Boolean,
): DspChainInfo {
    val stages = buildList {
        if (!policy.processingActive) {
            add("Saved settings only")
        } else {
            if (state.replayGainMode != ReplayGainMode.Off) {
                add("ReplayGain ${state.replayGainMode.label} ${formatDb(replayGainAdjustmentDb)}")
            }
            if (policy.eqStageActive) add("EQ")
            if (policy.toneStageActive) add("Tone")
            if (policy.loudnessStageActive) add("Loudness")
            if (policy.stereoStageActive) add("Stereo matrix")
            if (policy.dynamicsStageActive) add("Dynamics")
            if (policy.timeFxStageActive) add("Time effects")
            if (policy.pitchTempoActive) add("Tempo/pitch")
            if (nativeActive) add("Native Media3 PCM DSP") else if (nativeRequested) add("Native DSP requested")
            if (platformEffectsActive) add("Android session effects")
            if (preventClippingActive) add("Headroom ${formatDb(headroomDb)}")
            if (isEmpty() && policy.transparentPathExpected) add("Transparent engine-on path")
        }
    }
    val label = when {
        !policy.processingActive -> when {
            !state.enabled -> "Engine off"
            state.bypass -> "Bypass"
            state.advancedTweaks.safeMode -> "Safe mode"
            else -> "Transparent"
        }
        nativeActive -> "Native DSP"
        platformEffectsActive -> "Android platform effects"
        policy.transparentPathExpected -> "Transparent"
        else -> "Media3 PCM DSP"
    }
    return DspChainInfo(
        label = label,
        detail = stages.joinToString(" | "),
        processingActive = policy.processingActive,
        nativeMedia3DspRequested = nativeRequested,
        nativeMedia3DspActive = nativeActive,
        platformEffectsActive = platformEffectsActive,
        replayGainActive = state.replayGainMode != ReplayGainMode.Off,
        replayGainAdjustmentDb = replayGainAdjustmentDb,
        headroomDb = headroomDb,
        activeStages = stages,
    )
}

private fun outputEngineInfo(state: AudioEngineState, routeStatus: OutputRouteStatus?): OutputEngineInfo {
    val requestedPlugin = requestedOutputPlugin(state.output.mode)
    val actualPlugin = routeStatus?.actualPlugin?.label
        ?: if (state.output.mode == OutputMode.Auto || state.output.mode == OutputMode.AudioTrack) {
            OutputPluginType.Media3AudioTrack.label
        } else {
            "${requestedPlugin.label} requested"
        }
    val gainPercent = (audioOutputGain(state, 1f) * 100f).roundToInt()
    return OutputEngineInfo(
        label = actualPlugin,
        detail = "MediaSessionService output, ${state.output.bufferMode.label} buffer, gain $gainPercent%",
        requestedMode = state.output.mode.label,
        actualPlugin = actualPlugin,
        bufferMode = state.output.bufferMode.label,
        outputGainPercent = gainPercent,
    )
}

private fun outputDeviceInfo(
    state: AudioEngineState,
    diagnostics: OutputDiagnosticsSnapshot,
    routeStatus: OutputRouteStatus?,
): OutputDeviceInfo {
    val route = outputDeviceTypeFor(state.output.deviceProfile)
    val statusText = routeStatus?.let {
        if (it.available) {
            "Requested ${it.requestedPlugin.label}; actual ${it.actualPlugin.label}"
        } else {
            it.reason ?: "Fell back to ${it.actualPlugin.label}"
        }
    } ?: "Configured profile; active Android device name is not captured"
    return OutputDeviceInfo(
        label = state.output.deviceProfile.label,
        detail = statusText,
        requestedProfile = state.output.deviceProfile.label,
        activeRoute = route.label,
        verified = diagnostics.updatedAtMillis > 0L && routeStatus?.available == true,
    )
}

private fun audioChainWarnings(
    state: AudioEngineState,
    source: AudioSourceInfo,
    diagnostics: OutputDiagnosticsSnapshot,
    routeStatus: OutputRouteStatus?,
    nativeRequested: Boolean,
    nativeActive: Boolean,
    fallbackReason: String?,
    outputCapability: OutputCapabilitySnapshot,
    proOutput: ProOutputEngineState,
): List<AudioChainWarning> =
    buildList {
        if (diagnostics.updatedAtMillis == 0L) {
            add(
                AudioChainWarning(
                    title = "Runtime snapshot pending",
                    detail = "Playback service diagnostics have not reported an active output snapshot yet.",
                ),
            )
        } else if (diagnostics.decoderFormat.updatedAtMillis == 0L) {
            add(
                AudioChainWarning(
                    title = "Decoder format pending",
                    detail = "Media3 has not reported the selected audio decoder format yet.",
                ),
            )
        }
        if (nativeRequested && !nativeActive) {
            add(
                AudioChainWarning(
                    title = "Native DSP fallback",
                    detail = fallbackReason ?: "Native DSP was requested, but the active path is not reporting native DSP.",
                    level = AudioChainWarningLevel.Caution,
                ),
            )
        }
        if (routeStatus != null && !routeStatus.available) {
            add(
                AudioChainWarning(
                    title = "Output fallback",
                    detail = routeStatus.reason ?: "Requested output route fell back to ${routeStatus.actualPlugin.label}.",
                    level = AudioChainWarningLevel.Caution,
                ),
            )
        }
        if (state.output.mode == OutputMode.HiRes || state.output.hiResEnabled || outputCapability.hiResOutputStatus != HiResOutputStatus.Off) {
            add(
                AudioChainWarning(
                    title = outputCapability.hiResOutputStatus.warningTitle(),
                    detail = outputCapability.hiResOutputStatus.warningDetail(outputCapability.hiResEvidence),
                    level = if (outputCapability.hiResOutputStatus in setOf(
                            HiResOutputStatus.HighSampleRateActive,
                            HiResOutputStatus.HighBitDepthActive,
                            HiResOutputStatus.FullHiResActive,
                        )
                    ) {
                        AudioChainWarningLevel.Info
                    } else {
                        AudioChainWarningLevel.Caution
                    },
                ),
            )
        }
        if (proOutput.sourceEligibility.hiResSource && proOutput.enabledByUser && !proOutput.active) {
            add(
                AudioChainWarning(
                    title = "Source hi-res, output not active",
                    detail = proOutput.fallbackReason ?: "Hi-res source evidence is visible, but output evidence does not qualify as Hi-Res Active.",
                    level = AudioChainWarningLevel.Caution,
                ),
            )
        }
        if (proOutput.blockers.contains(ProOutputBlocker.BluetoothRoute)) {
            add(
                AudioChainWarning(
                    title = "Bluetooth blocks pro output",
                    detail = "Bluetooth routes are excluded from Hi-Res Active and Bit-Perfect Active claims.",
                    level = AudioChainWarningLevel.Caution,
                ),
            )
        }
        if (proOutput.blockers.contains(ProOutputBlocker.Media3CustomOutputUnavailable)) {
            add(
                AudioChainWarning(
                    title = "Pro output fallback",
                    detail = proOutput.fallbackReason ?: "The pro-output attempt is using the standard Media3 fallback path.",
                    level = AudioChainWarningLevel.Caution,
                ),
            )
        }
        if (proOutput.routeEligibility.underrunCount > 0) {
            add(
                AudioChainWarning(
                    title = "Output underruns detected",
                    detail = "Runtime output reported ${proOutput.routeEligibility.underrunCount} underrun(s); playback stability should be checked before claiming pro-output readiness.",
                    level = AudioChainWarningLevel.Caution,
                ),
            )
        }
        if (state.output.deviceProfile == DeviceProfile.UsbDac) {
            val usbSeen = diagnostics.outputDevice.usbDacDetected
            add(
                AudioChainWarning(
                    title = if (usbSeen) "USB DAC detected" else "USB DAC route not verified",
                    detail = if (usbSeen) {
                        "Android reports a USB DAC output device; PulseDeck requests it only when the USB DAC profile is selected."
                    } else {
                        "No USB DAC output device is currently reported by Android."
                    },
                    level = if (usbSeen) AudioChainWarningLevel.Info else AudioChainWarningLevel.Caution,
                ),
            )
        }
        if (source.live && source.sampleRateHz == null) {
            add(
                AudioChainWarning(
                    title = "Live stream format partial",
                    detail = "The stream codec is visible, but final decoded sample rate depends on the station payload.",
                ),
            )
        }
    }

private fun outputRouteStatusFor(profile: DeviceProfile, diagnostics: OutputDiagnosticsSnapshot): OutputRouteStatus? {
    val route = outputDeviceTypeFor(profile)
    return diagnostics.routeStatuses.firstOrNull { it.route == route }
}

private fun outputDeviceTypeFor(profile: DeviceProfile): OutputDeviceType =
    when (profile) {
        DeviceProfile.Speaker -> OutputDeviceType.Speaker
        DeviceProfile.Wired -> OutputDeviceType.WiredHeadsetAux
        DeviceProfile.Bluetooth,
        DeviceProfile.Car -> OutputDeviceType.Bluetooth
        DeviceProfile.UsbDac -> OutputDeviceType.UsbDac
    }

private fun requestedOutputPlugin(mode: OutputMode): OutputPluginType =
    when (mode) {
        OutputMode.NativeLowLatency -> OutputPluginType.AAudioNative
        OutputMode.HiRes -> OutputPluginType.HiRes
        OutputMode.Auto,
        OutputMode.AudioTrack -> OutputPluginType.Media3AudioTrack
    }

private fun proOutputEngineForRuntime(engine: ProOutputRuntimeEngine): ProOutputEngine =
    when (engine) {
        ProOutputRuntimeEngine.Media3Default -> ProOutputEngine.Media3Default
        ProOutputRuntimeEngine.Media3CustomOutput -> ProOutputEngine.Media3CustomOutput
        ProOutputRuntimeEngine.OboeAaudio -> ProOutputEngine.OboeAaudio
        ProOutputRuntimeEngine.AudioTrackDirect -> ProOutputEngine.AudioTrackDirect
        ProOutputRuntimeEngine.Unavailable -> ProOutputEngine.Unavailable
    }

private fun HiResOutputStatus.warningTitle(): String =
    when (this) {
        HiResOutputStatus.Off -> "Hi-res not requested"
        HiResOutputStatus.Requested -> "Hi-res requested"
        HiResOutputStatus.Blocked -> "Hi-res blocked"
        HiResOutputStatus.NotVerified -> "Hi-res not verified"
        HiResOutputStatus.HighSampleRateActive -> "High sample-rate output active"
        HiResOutputStatus.HighBitDepthActive -> "High bit-depth output active"
        HiResOutputStatus.FullHiResActive -> "Full hi-res output active"
        HiResOutputStatus.Unknown -> "Hi-res status unknown"
    }

private fun HiResOutputStatus.warningDetail(evidence: HiResOutputEvidence): String {
    val output = buildList {
        evidence.outputSampleRateHz?.let { add(formatSampleRate(it)) }
        evidence.outputEncoding?.let { add(it) }
    }.joinToString(" ").ifBlank { "output format pending" }
    val engine = evidence.engine.name
    return when (this) {
        HiResOutputStatus.Off -> "No hi-res attempt is enabled and no high-resolution output evidence is active."
        HiResOutputStatus.Requested -> "Hi-res output is requested; runtime AudioTrack evidence is still pending."
        HiResOutputStatus.Blocked -> when {
            ProOutputBlocker.SourceNotHiRes in evidence.blockers ->
                "Source is CD-quality or otherwise not high-resolution; high-resolution output is not required for this track."
            else -> "Hi-res output is blocked by ${evidence.blockers.joinToString { it.name }}."
        }
        HiResOutputStatus.NotVerified -> "Runtime output is $output; this does not verify high-resolution output."
        HiResOutputStatus.HighSampleRateActive -> "Runtime output is $output through $engine; high sample rate is active, but high bit depth is not verified."
        HiResOutputStatus.HighBitDepthActive -> "Runtime output is $output through $engine; high bit depth is active, but high sample rate is not verified."
        HiResOutputStatus.FullHiResActive -> "Runtime output is $output through $engine; both high sample rate and high-precision PCM are verified."
        HiResOutputStatus.Unknown -> "Runtime output diagnostics have not settled enough to classify hi-res output."
    }
}

private fun String?.isHighPrecisionPcmLabel(): Boolean {
    val normalized = this?.uppercase(Locale.US) ?: return false
    return "PCM24" in normalized ||
        "PCM32" in normalized ||
        "FLOAT" in normalized
}

private fun formatSampleRate(sampleRateHz: Int): String {
    val khz = sampleRateHz / 1000f
    return if (sampleRateHz % 1000 == 0) {
        "${khz.roundToInt()} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", khz)
    }
}

private fun formatDb(value: Float): String =
    (if (value > 0f) "+" else "") + String.format(Locale.US, "%.1f dB", value)
