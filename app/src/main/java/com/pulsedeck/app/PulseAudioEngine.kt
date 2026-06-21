package com.pulsedeck.app

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private const val PREFS_AUDIO_ENGINE = "pulse_audio_engine"
private const val AUDIO_STAGE_EPSILON = 0.05f
private const val AUDIO_STAGE_TINY_EPSILON = 0.001f
private val GRAPHIC_EQ_CENTER_FREQUENCIES_HZ = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

const val GRAPHIC_EQ_BAND_COUNT = 10
const val PARAMETRIC_EQ_SLOT_COUNT = 4
const val AUDIO_EQ_MIN_GAIN_DB = -12f
const val AUDIO_EQ_MAX_GAIN_DB = 12f
const val AUDIO_EQ_EXTENDED_MIN_GAIN_DB = -15f
const val AUDIO_EQ_EXTENDED_MAX_GAIN_DB = 15f
const val AUDIO_EQ_INPUT_STEP_DB = 0.1f
const val NATIVE_MASTER_GAIN_MAX_LINEAR = 1.5f
const val PARAMETRIC_EQ_MIN_FREQUENCY_HZ = 20f
const val PARAMETRIC_EQ_MAX_FREQUENCY_HZ = 20_000f
const val PARAMETRIC_EQ_MIN_Q = 0.1f
const val PARAMETRIC_EQ_MAX_Q = 18f

enum class AudioConsoleTab(val label: String) {
    Simple("Simple"),
    EQ("EQ"),
    Advanced("Advanced"),
    Output("Output"),
    QA("QA"),
}

enum class ReplayGainMode(val label: String) {
    Off("Off"),
    Track("Track"),
    Album("Album"),
    Smart("Smart"),
}

enum class ReplayGainSource(val label: String) {
    Tags("Tags"),
    ScannerAnalysis("Scanner analysis"),
    Fallback("Fallback"),
}

enum class FadeCurve(val label: String) {
    Linear("Linear"),
    EqualPower("Equal-power"),
    Smooth("Smooth"),
}

enum class PermanentFocusLossAction(val label: String) {
    Stop("Stop"),
    Pause("Pause"),
    KeepState("Keep state"),
}

enum class TransientFocusLossAction(val label: String) {
    Pause("Pause"),
    Duck("Duck"),
}

enum class DuckRequestAction(val label: String) {
    Duck("Auto duck"),
    ManualDuck("Manual duck"),
    Ignore("Ignore"),
}

enum class BluetoothFocusBehavior(val label: String) {
    Standard("Standard"),
    CarMode("Car mode"),
    HeadsetMode("Headset mode"),
}

enum class ResamplerMode(val label: String) {
    Auto("Auto"),
    System("System"),
    HighQuality("High quality"),
}

enum class OutputSampleRate(val label: String) {
    Auto("Auto"),
    Hz44100("44.1 kHz"),
    Hz48000("48 kHz"),
    Hz96000("96 kHz"),
    Hz192000("192 kHz"),
}

enum class ResamplerQuality(val label: String) {
    Normal("Normal"),
    High("High"),
    Ultra("Ultra"),
}

enum class DvcVolumeSteps(val label: String) {
    Normal("Normal"),
    Fine("Fine"),
    VeryFine("Very fine"),
}

enum class OutputMode(val label: String) {
    Auto("Auto"),
    AudioTrack("AudioTrack"),
    HiRes("Hi-Res"),
    NativeLowLatency("Native low-latency"),
}

enum class DeviceProfile(val label: String) {
    Speaker("Speaker"),
    Wired("Wired"),
    Bluetooth("Bluetooth"),
    UsbDac("USB DAC"),
    Car("Car"),
}

enum class BufferMode(val label: String) {
    LowLatency("Low latency"),
    Balanced("Balanced"),
    Stable("Stable"),
}

enum class OutputBitDepth(val label: String) {
    Auto("Auto"),
    Bit16("16-bit"),
    Bit24("24-bit"),
    Float32("32-bit float"),
}

enum class OutputFallbackMode(val label: String) {
    Automatic("Automatic"),
    AudioTrack("AudioTrack"),
    DisableHiRes("Disable Hi-Res"),
}

data class AudioInfoSettings(
    val showOnMainScreenLongPress: Boolean = true,
    val showOutputPath: Boolean = true,
    val showDspChain: Boolean = true,
)

data class CrossfadeSettings(
    val gaplessEnabled: Boolean = true,
    val crossfadeEnabled: Boolean = false,
    val durationMs: Int = 3000,
    val fadeCurve: FadeCurve = FadeCurve.EqualPower,
    val fadeOnPlay: Boolean = true,
    val fadeOnPause: Boolean = true,
    val fadeOnManualSkip: Boolean = true,
    val disableForAlbums: Boolean = true,
    val disableForShortTracks: Boolean = true,
)

data class ReplayGainSettings(
    val enabled: Boolean = true,
    val mode: ReplayGainMode = ReplayGainMode.Smart,
    val source: ReplayGainSource = ReplayGainSource.Tags,
    val trackPreampDb: Float = 0f,
    val albumPreampDb: Float = 0f,
    val noRgPreampDb: Float = 0f,
    val preventClipping: Boolean = true,
    val showInAudioInfo: Boolean = true,
)

data class AudioFocusSettings(
    val requestOnPlay: Boolean = true,
    val onPermanentLoss: PermanentFocusLossAction = PermanentFocusLossAction.Pause,
    val onTransientLoss: TransientFocusLossAction = TransientFocusLossAction.Pause,
    val onDuck: DuckRequestAction = DuckRequestAction.Duck,
    val resumeAfterCall: Boolean = true,
    val resumeAfterNotification: Boolean = true,
    val duckVolume: Float = 0.35f,
    val duckFadeMs: Int = 180,
    val bluetoothBehavior: BluetoothFocusBehavior = BluetoothFocusBehavior.Standard,
    val resumeOnlyIfAutoPaused: Boolean = true,
)

data class ResamplerSettings(
    val mode: ResamplerMode = ResamplerMode.Auto,
    val outputSampleRate: OutputSampleRate = OutputSampleRate.Auto,
    val dither: Boolean = false,
    val quality: ResamplerQuality = ResamplerQuality.High,
    val showConversionInfo: Boolean = true,
    val batterySaverMode: Boolean = false,
)

data class DirectVolumeControlSettings(
    val enabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val headroomDb: Float = -3f,
    val volumeSteps: DvcVolumeSteps = DvcVolumeSteps.Normal,
    val perDeviceDvc: Boolean = true,
    val compatibilityMode: Boolean = false,
)

data class OutputSettings(
    val mode: OutputMode = OutputMode.Auto,
    val deviceProfile: DeviceProfile = DeviceProfile.Speaker,
    val bufferMode: BufferMode = BufferMode.Balanced,
    val hiResEnabled: Boolean = false,
    val sampleRate: OutputSampleRate = OutputSampleRate.Auto,
    val bitDepth: OutputBitDepth = OutputBitDepth.Auto,
    val bitPerfectAttemptEnabled: Boolean = false,
    val fallbackMode: OutputFallbackMode = OutputFallbackMode.Automatic,
    val showOutputWarnings: Boolean = true,
)

data class AdvancedAudioTweaksSettings(
    val volumeSteps: DvcVolumeSteps = DvcVolumeSteps.Normal,
    val musicFxEnabled: Boolean = false,
    val safeMode: Boolean = false,
    val debugAudioInfo: Boolean = false,
)

data class SettingsSearchEntry(
    val title: String,
    val subtitle: String = "",
    val keywords: List<String> = emptyList(),
)

enum class ParametricFilterType(val label: String) {
    Peak("Peak"),
    LowShelf("Low shelf"),
    HighShelf("High shelf"),
    LowPass("Low pass"),
    HighPass("High pass"),
    Notch("Notch"),
}

data class GraphicEqBand(
    val frequencyHz: Float,
    val gainDb: Float = 0f,
)

data class ParametricEqBand(
    val enabled: Boolean = false,
    val type: ParametricFilterType = ParametricFilterType.Peak,
    val frequencyHz: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 1f,
)

data class LimiterState(
    val enabled: Boolean = true,
    val ceilingDb: Float = -1f,
    val releaseMs: Float = 120f,
    val strength: Float = 0.55f,
)

data class StereoState(
    val balance: Float = 0f,
    val mono: Boolean = false,
    val stereoWidth: Float = 0f,
    val crossfeed: Float = 0f,
)

data class ReverbState(
    val enabled: Boolean = false,
    val mix: Float = 0f,
    val size: Float = 0.35f,
    val preDelayMs: Float = 18f,
    val damp: Float = 0.45f,
    val decay: Float = 1.2f,
)

data class TempoPitchState(
    val speed: Float = 1f,
    val tempo: Float = 1f,
    val pitchSemitones: Float = 0f,
)

data class CompressorState(
    val enabled: Boolean = false,
    val thresholdDb: Float = -18f,
    val ratio: Float = 2f,
    val attackMs: Float = 12f,
    val releaseMs: Float = 120f,
    val makeupDb: Float = 0f,
    val mix: Float = 0f,
)

data class GateState(
    val enabled: Boolean = false,
    val thresholdDb: Float = -54f,
    val attackMs: Float = 5f,
    val holdMs: Float = 60f,
    val releaseMs: Float = 120f,
)

data class DelayState(
    val enabled: Boolean = false,
    val timeMs: Float = 280f,
    val feedback: Float = 0.22f,
    val mix: Float = 0f,
)

data class ModulationState(
    val chorusEnabled: Boolean = false,
    val flangerEnabled: Boolean = false,
    val phaserEnabled: Boolean = false,
    val rateHz: Float = 0.35f,
    val depth: Float = 0.35f,
    val feedback: Float = 0.1f,
    val mix: Float = 0f,
)

enum class NativeCompareSlot(val label: String) {
    Off("Off"),
    A("A"),
    B("B"),
}

enum class GraphicEqRangeMode(val label: String) {
    Standard12Db("+/-12 dB"),
    Extended15DbWhenSupported("+/-15 dB when Native DSP owns PCM"),
}

data class NativePresetSlot(
    val name: String = "",
    val enabled: Boolean = true,
    val bypass: Boolean = false,
    val preampDb: Float = 0f,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val replayGainPreampDb: Float = 0f,
    val preventClipping: Boolean = true,
    val limiter: LimiterState = LimiterState(),
    val eqEnabled: Boolean = true,
    val graphicEqRangeMode: GraphicEqRangeMode = GraphicEqRangeMode.Standard12Db,
    val masterGain: Float = 1f,
    val sourceFrequencyHz: Float = 440f,
    val sourceGain: Float = 0.05f,
    val eqGains: List<Float> = emptyList(),
    val parametricEq: List<ParametricEqBand> = defaultParametricBands(),
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val vocalClarityDb: Float = 0f,
    val loudnessDb: Float = 0f,
    val stereo: StereoState = StereoState(),
    val reverb: ReverbState = ReverbState(),
    val tempoPitch: TempoPitchState = TempoPitchState(),
    val compressor: CompressorState = CompressorState(),
    val gate: GateState = GateState(),
    val delay: DelayState = DelayState(),
    val modulation: ModulationState = ModulationState(),
    val nativeEnabled: Boolean = false,
    val media3DspEnabled: Boolean = false,
    val lowLatencyToneEnabled: Boolean = false,
)

data class NativeAudioSettings(
    val enabled: Boolean = false,
    val media3DspEnabled: Boolean = false,
    val lowLatencyToneEnabled: Boolean = false,
    val masterGain: Float = 1f,
    val sourceFrequencyHz: Float = 440f,
    val sourceGain: Float = 0.05f,
    val diagnosticsEnabled: Boolean = true,
    val exportDurationMs: Int = 3000,
    val activeCompareSlot: NativeCompareSlot = NativeCompareSlot.Off,
    val compareSlotA: NativePresetSlot = NativePresetSlot("A"),
    val compareSlotB: NativePresetSlot = NativePresetSlot("B"),
)

data class AudioPreset(
    val id: String,
    val name: String,
    val description: String,
    val preampDb: Float = 0f,
    val eqBands: List<GraphicEqBand> = flatEqBands(),
    val stereo: StereoState = StereoState(),
    val limiter: LimiterState = LimiterState(),
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
)

data class EffectiveAudioProcessingPolicy(
    val engineEnabled: Boolean,
    val bypass: Boolean,
    val safeMode: Boolean,
    val processingActive: Boolean,
    val replayGainActive: Boolean,
    val replayGainAdjustmentDb: Float,
    val loudnessSource: LoudnessSource,
    val loudnessConfidence: LoudnessConfidence,
    val eqStageActive: Boolean,
    val toneStageActive: Boolean,
    val loudnessStageActive: Boolean,
    val stereoStageActive: Boolean,
    val dynamicsStageActive: Boolean,
    val timeFxStageActive: Boolean,
    val pitchTempoActive: Boolean,
    val nativeMedia3DspRequested: Boolean,
    val nativeMedia3DspActive: Boolean,
    val platformEqAllowed: Boolean,
    val platformBassBoostAllowed: Boolean,
    val platformVirtualizerAllowed: Boolean,
    val platformLoudnessAllowed: Boolean,
    val transparentPathExpected: Boolean,
    val preventClippingActive: Boolean,
    val estimatedHeadroomDb: Float,
    val fallbackReason: String?,
) {
    val platformEffectsAllowed: Boolean
        get() = platformEqAllowed || platformBassBoostAllowed || platformVirtualizerAllowed || platformLoudnessAllowed
}

data class NativeMasterGainConstraint(
    val requestedLinear: Float,
    val effectiveLinear: Float,
    val requestedDb: Float,
    val effectiveDb: Float,
    val constrained: Boolean,
)

data class AudioEngineState(
    val enabled: Boolean = true,
    val bypass: Boolean = false,
    val preampDb: Float = 0f,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val replayGainPreampDb: Float = 0f,
    val preventClipping: Boolean = true,
    val limiter: LimiterState = LimiterState(),
    val eqEnabled: Boolean = true,
    val graphicEqRangeMode: GraphicEqRangeMode = GraphicEqRangeMode.Standard12Db,
    val eqBands: List<GraphicEqBand> = flatEqBands(),
    val parametricEq: List<ParametricEqBand> = defaultParametricBands(),
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val vocalClarityDb: Float = 0f,
    val loudnessDb: Float = 0f,
    val stereo: StereoState = StereoState(),
    val reverb: ReverbState = ReverbState(),
    val tempoPitch: TempoPitchState = TempoPitchState(),
    val compressor: CompressorState = CompressorState(),
    val gate: GateState = GateState(),
    val delay: DelayState = DelayState(),
    val modulation: ModulationState = ModulationState(),
    val native: NativeAudioSettings = NativeAudioSettings(),
    val activePresetId: String = "flat",
    val presetModified: Boolean = false,
    val sourceLoudness: LoudnessMetadata = LoudnessMetadata(),
    val effectiveReplayGain: EffectiveReplayGain = EffectiveReplayGain(),
    val audioInfo: AudioInfoSettings = AudioInfoSettings(),
    val crossfade: CrossfadeSettings = CrossfadeSettings(),
    val replayGain: ReplayGainSettings = ReplayGainSettings(enabled = false, mode = ReplayGainMode.Off),
    val audioFocus: AudioFocusSettings = AudioFocusSettings(),
    val resampler: ResamplerSettings = ResamplerSettings(),
    val dvc: DirectVolumeControlSettings = DirectVolumeControlSettings(),
    val output: OutputSettings = OutputSettings(),
    val advancedTweaks: AdvancedAudioTweaksSettings = AdvancedAudioTweaksSettings(),
) {
    val processingActive: Boolean
        get() = enabled && !bypass

    val effectiveProcessingActive: Boolean
        get() = enabled && !bypass && !advancedTweaks.safeMode

    val graphicEqStageActive: Boolean
        get() = eqEnabled && eqBands.any { abs(it.gainDb) > AUDIO_STAGE_EPSILON }

    val parametricEqStageActive: Boolean
        get() = parametricEq.any { it.isActiveFilter() }

    val eqStageActive: Boolean
        get() = graphicEqStageActive || parametricEqStageActive

    val toneControlsActive: Boolean
        get() = abs(bassDb) > AUDIO_STAGE_EPSILON ||
            abs(trebleDb) > AUDIO_STAGE_EPSILON ||
            abs(vocalClarityDb) > AUDIO_STAGE_EPSILON

    val loudnessStageActive: Boolean
        get() = loudnessDb > AUDIO_STAGE_EPSILON

    val stereoMatrixActive: Boolean
        get() = abs(stereo.balance) > AUDIO_STAGE_TINY_EPSILON ||
            stereo.mono ||
            stereo.stereoWidth > AUDIO_STAGE_TINY_EPSILON ||
            stereo.crossfeed > AUDIO_STAGE_TINY_EPSILON

    val compressorStageActive: Boolean
        get() = compressor.enabled && compressor.mix > AUDIO_STAGE_TINY_EPSILON

    val gateStageActive: Boolean
        get() = gate.enabled

    val delayStageActive: Boolean
        get() = delay.enabled && delay.mix > AUDIO_STAGE_TINY_EPSILON

    val reverbStageActive: Boolean
        get() = reverb.enabled && reverb.mix > AUDIO_STAGE_TINY_EPSILON

    val modulationStageActive: Boolean
        get() = (modulation.chorusEnabled || modulation.flangerEnabled || modulation.phaserEnabled) &&
            modulation.mix > AUDIO_STAGE_TINY_EPSILON

    val timeFxStageActive: Boolean
        get() = delayStageActive || reverbStageActive || modulationStageActive

    val pitchTempoActive: Boolean
        get() = abs((tempoPitch.speed * tempoPitch.tempo) - 1f) > AUDIO_STAGE_TINY_EPSILON ||
            abs(tempoPitch.pitchSemitones) > AUDIO_STAGE_TINY_EPSILON

    val gainStageActive: Boolean
        get() = abs(preampDb + activeReplayGainDb) > AUDIO_STAGE_EPSILON

    val replayGainStageActive: Boolean
        get() = replayGainMode != ReplayGainMode.Off && abs(replayGainPreampDb) > AUDIO_STAGE_EPSILON

    val activeReplayGainDb: Float
        get() = if (replayGainMode != ReplayGainMode.Off) replayGainPreampDb else 0f

    val nativeLimiterStageActive: Boolean
        get() = limiter.enabled && nativeOwnedBoostRiskDb > 0.75f

    val dynamicsStageActive: Boolean
        get() = nativeLimiterStageActive || compressorStageActive || gateStageActive

    val nativeOnlyProcessingActive: Boolean
        get() =
            stereoMatrixActive ||
                parametricEqStageActive ||
                dynamicsStageActive ||
                timeFxStageActive

    val spectralBoostDb: Float
        get() = listOf(
            if (graphicEqStageActive || toneControlsActive) {
                eqBands.maxOfOrNull { audioBandGainDb(it.frequencyHz, this, graphicEqRangeMode.requestedGainRange()) } ?: 0f
            } else {
                0f
            },
            parametricEq.filter { it.isActiveFilter() }.maxOfOrNull { it.positiveGainEstimateDb() } ?: 0f,
            if (loudnessStageActive) loudnessDb else 0f,
        ).maxOrNull()?.coerceAtLeast(0f) ?: 0f

    val estimatedFullGraphBoostDb: Float
        get() = max(
            0f,
            preampDb + activeReplayGainDb +
                graphicAndToneBoostDb +
                parametricBoostDb +
                loudnessBoostDb +
                compressorBoostDb +
                stereoMatrixBoostDb +
                timeFxBoostDb,
        )

    val nativeOwnedBoostRiskDb: Float
        get() = max(
            0f,
            preampDb + activeReplayGainDb +
                graphicAndToneBoostDb +
                parametricBoostDb +
                compressorBoostDb +
                stereoMatrixBoostDb +
                timeFxBoostDb,
        )

    val maxBoostDb: Float
        get() = estimatedFullGraphBoostDb

    val clippingRisk: Boolean
        get() = effectiveProcessingActive && maxBoostDb > 0.75f
}

private val AudioEngineState.graphicAndToneBoostDb: Float
    get() = if (graphicEqStageActive || toneControlsActive) {
        eqBands.maxOfOrNull { audioBandGainDb(it.frequencyHz, this, graphicEqRangeMode.requestedGainRange()) }?.coerceAtLeast(0f) ?: 0f
    } else {
        0f
    }

private val AudioEngineState.parametricBoostDb: Float
    get() = parametricEq
        .filter { it.isActiveFilter() }
        .maxOfOrNull { it.positiveGainEstimateDb() }
        ?.coerceAtLeast(0f) ?: 0f

private val AudioEngineState.loudnessBoostDb: Float
    get() = if (loudnessStageActive) loudnessDb.coerceAtLeast(0f) else 0f

private val AudioEngineState.compressorBoostDb: Float
    get() = if (compressorStageActive) {
        max(0f, compressor.makeupDb) * compressor.mix.coerceIn(0f, 1f)
    } else {
        0f
    }

private val AudioEngineState.stereoMatrixBoostDb: Float
    get() = if (stereoMatrixActive) {
        linearToDb(1f + 0.75f * stereo.stereoWidth.coerceIn(0f, 1f)).coerceAtLeast(0f)
    } else {
        0f
    }

private val AudioEngineState.timeFxBoostDb: Float
    get() = listOf(
        if (delayStageActive) delay.mix.coerceIn(0f, 1f) * (1f + delay.feedback.coerceIn(0f, 0.92f)) * 3f else 0f,
        if (reverbStageActive) reverb.mix.coerceIn(0f, 1f) * (1f + reverb.size.coerceIn(0f, 1f) * 0.5f + reverb.decay.coerceIn(0.2f, 6f) / 12f) * 3f else 0f,
        if (modulationStageActive) modulation.mix.coerceIn(0f, 1f) *
            (1f + modulation.feedback.coerceIn(0f, 0.9f)) *
            modulation.depth.coerceIn(0f, 1f) *
            3f else 0f,
    ).sum()

internal fun ParametricEqBand.isActiveFilter(): Boolean =
    enabled && when (type) {
        ParametricFilterType.LowPass,
        ParametricFilterType.HighPass,
        ParametricFilterType.Notch -> true
        ParametricFilterType.Peak,
        ParametricFilterType.LowShelf,
        ParametricFilterType.HighShelf -> abs(gainDb) > AUDIO_STAGE_EPSILON
    }

private fun ParametricEqBand.positiveGainEstimateDb(): Float =
    when (type) {
        ParametricFilterType.LowPass,
        ParametricFilterType.HighPass,
        ParametricFilterType.Notch -> 0f
        ParametricFilterType.Peak,
        ParametricFilterType.LowShelf,
        ParametricFilterType.HighShelf -> gainDb.coerceAtLeast(0f)
    }

fun AudioEngineState.effectiveProcessingPolicy(
    nativeAvailable: Boolean,
    nativeMedia3DspFormatSupported: Boolean = true,
    nativeMedia3DspFallbackReason: String? = null,
): EffectiveAudioProcessingPolicy {
    val active = effectiveProcessingActive
    val nativeMedia3DspRequested = native.media3DspEnabled || nativeOnlyProcessingActive
    val nativeMedia3DspActive = active &&
        nativeMedia3DspRequested &&
        nativeAvailable &&
        nativeMedia3DspFormatSupported
    val platformEqAllowed = active && !nativeMedia3DspActive && (graphicEqStageActive || toneControlsActive)
    val platformBassBoostAllowed = active && !nativeMedia3DspActive && bassDb > AUDIO_STAGE_EPSILON
    val platformVirtualizerAllowed = active &&
        !nativeMedia3DspActive &&
        !stereo.mono &&
        stereo.stereoWidth > AUDIO_STAGE_EPSILON
    val platformLoudnessAllowed = active && !nativeMedia3DspActive && loudnessStageActive
    val estimatedHeadroomDb = if (active && preventClipping) -estimatedFullGraphBoostDb else 0f
    val fallbackReason = when {
        active && nativeMedia3DspRequested && !nativeAvailable ->
            "Native Media3 DSP requested but the native engine is unavailable; using the platform/AudioTrack path."
        active && nativeMedia3DspRequested && !nativeMedia3DspFormatSupported ->
            nativeMedia3DspFallbackReason ?: "Native Media3 DSP does not support the decoded PCM format; using the platform/AudioTrack path."
        else -> null
    }
    val transparent = !active || (
        !gainStageActive &&
            !eqStageActive &&
            !toneControlsActive &&
            !loudnessStageActive &&
            !stereoMatrixActive &&
            !dynamicsStageActive &&
            !timeFxStageActive &&
            !pitchTempoActive &&
            !nativeMedia3DspRequested
        )
    return EffectiveAudioProcessingPolicy(
        engineEnabled = enabled,
        bypass = bypass,
        safeMode = advancedTweaks.safeMode,
        processingActive = active,
        replayGainActive = active && replayGainStageActive,
        replayGainAdjustmentDb = activeReplayGainDb,
        loudnessSource = sourceLoudness.source,
        loudnessConfidence = sourceLoudness.confidence,
        eqStageActive = eqStageActive,
        toneStageActive = toneControlsActive,
        loudnessStageActive = loudnessStageActive,
        stereoStageActive = stereoMatrixActive,
        dynamicsStageActive = dynamicsStageActive,
        timeFxStageActive = timeFxStageActive,
        pitchTempoActive = pitchTempoActive,
        nativeMedia3DspRequested = nativeMedia3DspRequested,
        nativeMedia3DspActive = nativeMedia3DspActive,
        platformEqAllowed = platformEqAllowed,
        platformBassBoostAllowed = platformBassBoostAllowed,
        platformVirtualizerAllowed = platformVirtualizerAllowed,
        platformLoudnessAllowed = platformLoudnessAllowed,
        transparentPathExpected = transparent,
        preventClippingActive = active && preventClipping && estimatedFullGraphBoostDb > AUDIO_STAGE_TINY_EPSILON,
        estimatedHeadroomDb = estimatedHeadroomDb,
        fallbackReason = fallbackReason,
    )
}

object PulseAudioEngineStore {
    private val mutableState = MutableStateFlow(AudioEngineState())
    val state: StateFlow<AudioEngineState> = mutableState

    fun update(next: AudioEngineState) {
        mutableState.value = next.normalized()
    }
}

class PulseDspAudioProcessor {
    private var smoothedGain = 1f

    fun outputGain(state: AudioEngineState, externalVolume: Float): Float {
        val target = audioOutputGain(state, externalVolume)
        smoothedGain += (target - smoothedGain) * 0.22f
        return smoothedGain.coerceIn(0f, 1.25f)
    }

    fun softLimit(sample: Float, state: AudioEngineState): Float {
        if (!state.effectiveProcessingActive || !state.limiter.enabled || !state.clippingRisk) return sample
        val ceiling = dbToLinear(state.limiter.ceilingDb)
        val shaped = sample / (1f + abs(sample) * state.limiter.strength)
        return shaped.coerceIn(-ceiling, ceiling)
    }
}

class PlatformAudioEffects {
    private var sessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    fun apply(audioSessionId: Int, state: AudioEngineState, policy: EffectiveAudioProcessingPolicy) {
        if (audioSessionId <= 0 || !policy.platformEffectsAllowed) {
            release()
            return
        }
        if (audioSessionId != sessionId) {
            release()
            sessionId = audioSessionId
            equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
            bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
            virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
            loudnessEnhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        }
        runCatching { applyEqualizer(policy, state) }
        runCatching {
            val equalizerHandlesTone = equalizer != null && policy.platformEqAllowed
            bassBoost?.enabled = policy.platformBassBoostAllowed && !equalizerHandlesTone
            bassBoost?.setStrength(((state.bassDb.coerceIn(0f, 12f) / 12f) * 1000f).roundToInt().toShort())
        }
        runCatching {
            virtualizer?.enabled = policy.platformVirtualizerAllowed
            virtualizer?.setStrength((state.stereo.stereoWidth.coerceIn(0f, 1f) * 1000f).roundToInt().toShort())
        }
        runCatching {
            loudnessEnhancer?.enabled = policy.platformLoudnessAllowed
            loudnessEnhancer?.setTargetGain((state.loudnessDb.coerceIn(0f, 12f) * 100f).roundToInt())
        }
    }

    private fun applyEqualizer(policy: EffectiveAudioProcessingPolicy, state: AudioEngineState) {
        val eq = equalizer ?: return
        eq.enabled = policy.platformEqAllowed
        if (!eq.enabled) return
        val range = eq.bandLevelRange
        val minLevel = range.getOrNull(0)?.toInt() ?: -1200
        val maxLevel = range.getOrNull(1)?.toInt() ?: 1200
        for (index in 0 until eq.numberOfBands) {
            val band = index.toShort()
            val centerHz = eq.getCenterFreq(band) / 1000f
            val target = audioBandGainDb(centerHz, state)
            eq.setBandLevel(band, (target * 100f).roundToInt().coerceIn(minLevel, maxLevel).toShort())
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        sessionId = 0
    }
}

fun platformSpectralEffectsActive(state: AudioEngineState, nativeMedia3DspActive: Boolean): Boolean =
    if (nativeMedia3DspActive) {
        false
    } else {
        state.effectiveProcessingPolicy(nativeAvailable = false)
            .let { it.platformEqAllowed || it.platformBassBoostAllowed || it.platformVirtualizerAllowed }
    }

fun graphicEqCenterFrequenciesHz(): List<Float> = GRAPHIC_EQ_CENTER_FREQUENCIES_HZ

fun flatEqBands(): List<GraphicEqBand> =
    GRAPHIC_EQ_CENTER_FREQUENCIES_HZ.map { GraphicEqBand(it) }

fun defaultParametricBands(): List<ParametricEqBand> = listOf(
    ParametricEqBand(type = ParametricFilterType.LowShelf, frequencyHz = 80f, q = 0.7f),
    ParametricEqBand(type = ParametricFilterType.Peak, frequencyHz = 650f, q = 1.0f),
    ParametricEqBand(type = ParametricFilterType.Peak, frequencyHz = 2500f, q = 1.2f),
    ParametricEqBand(type = ParametricFilterType.HighShelf, frequencyHz = 10000f, q = 0.7f),
)

fun standardGraphicEqGainRange(): ClosedFloatingPointRange<Float> =
    AUDIO_EQ_MIN_GAIN_DB..AUDIO_EQ_MAX_GAIN_DB

fun extendedGraphicEqGainRange(): ClosedFloatingPointRange<Float> =
    AUDIO_EQ_EXTENDED_MIN_GAIN_DB..AUDIO_EQ_EXTENDED_MAX_GAIN_DB

fun GraphicEqRangeMode.requestedGainRange(): ClosedFloatingPointRange<Float> =
    when (this) {
        GraphicEqRangeMode.Standard12Db -> standardGraphicEqGainRange()
        GraphicEqRangeMode.Extended15DbWhenSupported -> extendedGraphicEqGainRange()
    }

fun AudioEngineState.effectiveGraphicEqGainRange(nativeMedia3DspActive: Boolean): ClosedFloatingPointRange<Float> =
    if (graphicEqRangeMode == GraphicEqRangeMode.Extended15DbWhenSupported && nativeMedia3DspActive) {
        extendedGraphicEqGainRange()
    } else {
        standardGraphicEqGainRange()
    }

fun AudioEngineState.graphicEqExtendedRangeEffective(nativeMedia3DspActive: Boolean): Boolean =
    effectiveGraphicEqGainRange(nativeMedia3DspActive).endInclusive > AUDIO_EQ_MAX_GAIN_DB

fun audioDbSliderSteps(range: ClosedFloatingPointRange<Float>): Int =
    (((range.endInclusive - range.start) / AUDIO_EQ_INPUT_STEP_DB).roundToInt() - 1).coerceAtLeast(0)

fun quantizeAudioDb(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val snapped = (value / AUDIO_EQ_INPUT_STEP_DB).roundToInt() * AUDIO_EQ_INPUT_STEP_DB
    val clean = if (abs(snapped) < AUDIO_EQ_INPUT_STEP_DB / 2f) 0f else snapped
    return clean.coerceIn(range.start, range.endInclusive)
}

fun canonicalGraphicEqBands(
    bands: List<GraphicEqBand>,
    gainRange: ClosedFloatingPointRange<Float> = standardGraphicEqGainRange(),
): List<GraphicEqBand> =
    GRAPHIC_EQ_CENTER_FREQUENCIES_HZ.mapIndexed { index, frequencyHz ->
        GraphicEqBand(
            frequencyHz = frequencyHz,
            gainDb = quantizeAudioDb(bands.getOrNull(index)?.gainDb ?: 0f, gainRange),
        )
    }

fun canonicalParametricEqBands(bands: List<ParametricEqBand>): List<ParametricEqBand> {
    val defaults = defaultParametricBands()
    return defaults.mapIndexed { index, fallback ->
        (bands.getOrNull(index) ?: fallback).normalizedParametricEqBand()
    }
}

private fun ParametricEqBand.normalizedParametricEqBand(): ParametricEqBand =
    copy(
        frequencyHz = frequencyHz.coerceIn(PARAMETRIC_EQ_MIN_FREQUENCY_HZ, PARAMETRIC_EQ_MAX_FREQUENCY_HZ),
        gainDb = gainDb.coerceIn(AUDIO_EQ_MIN_GAIN_DB, AUDIO_EQ_MAX_GAIN_DB),
        q = q.coerceIn(PARAMETRIC_EQ_MIN_Q, PARAMETRIC_EQ_MAX_Q),
    )

fun AudioEngineState.withGraphicEqBandGain(index: Int, gainDb: Float): AudioEngineState =
    copy(
        eqBands = canonicalGraphicEqBands(eqBands, graphicEqRangeMode.requestedGainRange()).mapIndexed { bandIndex, band ->
            if (bandIndex == index) band.copy(gainDb = quantizeAudioDb(gainDb, graphicEqRangeMode.requestedGainRange())) else band
        },
        presetModified = true,
    ).normalized()

fun AudioEngineState.withGraphicEqRangeMode(mode: GraphicEqRangeMode): AudioEngineState =
    copy(graphicEqRangeMode = mode, presetModified = true).normalized()

fun AudioEngineState.withParametricEqBand(index: Int, band: ParametricEqBand): AudioEngineState =
    copy(
        parametricEq = canonicalParametricEqBands(parametricEq).mapIndexed { bandIndex, current ->
            if (bandIndex == index) band else current
        },
        presetModified = true,
    ).normalized()

fun AudioEngineState.withParametricEqDefaults(): AudioEngineState =
    copy(parametricEq = defaultParametricBands(), presetModified = true).normalized()

fun AudioEngineState.withEqControlsReset(): AudioEngineState =
    copy(
        preampDb = 0f,
        eqEnabled = true,
        eqBands = flatEqBands(),
        parametricEq = defaultParametricBands(),
        bassDb = 0f,
        trebleDb = 0f,
        vocalClarityDb = 0f,
        activePresetId = "flat",
        presetModified = true,
    ).normalized()

fun audioBandGainDb(
    frequencyHz: Float,
    state: AudioEngineState,
    graphicEqGainRange: ClosedFloatingPointRange<Float> = standardGraphicEqGainRange(),
): Float {
    val centerHz = frequencyHz.coerceIn(20f, 20_000f)
    val graphicGain = if (state.eqEnabled) {
        state.eqBands.minByOrNull { abs(it.frequencyHz - centerHz) }?.gainDb?.coerceIn(graphicEqGainRange.start, graphicEqGainRange.endInclusive) ?: 0f
    } else {
        0f
    }
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
    return (graphicGain + bassTone + trebleTone + vocalTone).coerceIn(graphicEqGainRange.start, graphicEqGainRange.endInclusive)
}

fun audioOutputGain(state: AudioEngineState, externalVolume: Float, maxExternalVolume: Float = 1f): Float {
    val safeMaxVolume = maxExternalVolume.coerceAtLeast(0f)
    val policy = state.effectiveProcessingPolicy(nativeAvailable = false)
    if (!policy.processingActive) return externalVolume.coerceIn(0f, safeMaxVolume)
    val requestedDb = state.preampDb + state.activeReplayGainDb
    return (externalVolume.coerceIn(0f, safeMaxVolume) * dbToLinear(requestedDb + policy.estimatedHeadroomDb)).coerceIn(0f, safeMaxVolume)
}

fun nativeMasterGainConstraint(state: AudioEngineState, externalVolume: Float): NativeMasterGainConstraint {
    val policy = state.effectiveProcessingPolicy(nativeAvailable = true)
    val requestedDb = if (policy.processingActive) {
        state.preampDb + state.activeReplayGainDb + policy.estimatedHeadroomDb
    } else {
        0f
    }
    val requestedLinear = externalVolume.coerceAtLeast(0f) * dbToLinear(requestedDb)
    val effectiveLinear = requestedLinear.coerceIn(0f, NATIVE_MASTER_GAIN_MAX_LINEAR)
    return NativeMasterGainConstraint(
        requestedLinear = requestedLinear,
        effectiveLinear = effectiveLinear,
        requestedDb = linearToDb(requestedLinear),
        effectiveDb = linearToDb(effectiveLinear),
        constrained = requestedLinear > NATIVE_MASTER_GAIN_MAX_LINEAR + AUDIO_STAGE_TINY_EPSILON,
    )
}

fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

fun linearToDb(linear: Float): Float = 20f * log10(linear.coerceAtLeast(0.000001f))

fun CrossfadeSettings.normalized(): CrossfadeSettings =
    copy(durationMs = durationMs.coerceIn(0, 12_000))

fun ReplayGainSettings.normalized(): ReplayGainSettings =
    copy(
        trackPreampDb = trackPreampDb.coerceIn(-12f, 12f),
        albumPreampDb = albumPreampDb.coerceIn(-12f, 12f),
        noRgPreampDb = noRgPreampDb.coerceIn(-12f, 12f),
    )

fun AudioFocusSettings.normalized(): AudioFocusSettings =
    copy(
        duckVolume = duckVolume.coerceIn(0f, 1f),
        duckFadeMs = duckFadeMs.coerceIn(50, 1_000),
    )

fun DirectVolumeControlSettings.normalized(): DirectVolumeControlSettings =
    copy(headroomDb = headroomDb.coerceIn(-12f, 0f))

fun normalizeSettingsSearch(text: String): String =
    text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

fun settingsSearchMatches(entry: SettingsSearchEntry, rawQuery: String): Boolean {
    val query = normalizeSettingsSearch(rawQuery)
    if (query.isBlank()) return true
    val haystack = normalizeSettingsSearch(
        buildString {
            append(entry.title)
            append(' ')
            append(entry.subtitle)
            entry.keywords.forEach {
                append(' ')
                append(it)
            }
        },
    )
    return query.split(' ').filter { it.isNotBlank() }.all { token -> haystack.contains(token) }
}

fun AudioEngineState.withReplayGainSettings(next: ReplayGainSettings): AudioEngineState =
    next.normalized().let { normalizedReplayGain ->
        val mode = if (normalizedReplayGain.enabled) normalizedReplayGain.mode else ReplayGainMode.Off
    copy(
            replayGain = normalizedReplayGain.copy(mode = mode, enabled = mode != ReplayGainMode.Off),
            replayGainMode = mode,
            replayGainPreampDb = normalizedReplayGain.trackPreampDb,
            preventClipping = normalizedReplayGain.preventClipping,
        ).normalized()
    }

fun AudioEngineState.normalized(): AudioEngineState =
    copy(
        preampDb = preampDb.coerceIn(-12f, 12f),
        replayGainPreampDb = replayGainPreampDb.coerceIn(EFFECTIVE_REPLAY_GAIN_MIN_DB, EFFECTIVE_REPLAY_GAIN_MAX_DB),
        eqBands = canonicalGraphicEqBands(eqBands, graphicEqRangeMode.requestedGainRange()),
        parametricEq = canonicalParametricEqBands(parametricEq),
        bassDb = bassDb.coerceIn(-12f, 12f),
        trebleDb = trebleDb.coerceIn(-12f, 12f),
        vocalClarityDb = vocalClarityDb.coerceIn(-12f, 12f),
        loudnessDb = loudnessDb.coerceIn(0f, 12f),
        stereo = stereo.copy(
            balance = stereo.balance.coerceIn(-1f, 1f),
            stereoWidth = stereo.stereoWidth.coerceIn(0f, 1f),
            crossfeed = stereo.crossfeed.coerceIn(0f, 1f),
        ),
        reverb = reverb.copy(
            mix = reverb.mix.coerceIn(0f, 1f),
            size = reverb.size.coerceIn(0f, 1f),
            preDelayMs = reverb.preDelayMs.coerceIn(0f, 120f),
            damp = reverb.damp.coerceIn(0f, 1f),
            decay = reverb.decay.coerceIn(0.2f, 6f),
        ),
        tempoPitch = tempoPitch.copy(
            speed = tempoPitch.speed.coerceIn(0.5f, 2f),
            tempo = tempoPitch.tempo.coerceIn(0.5f, 2f),
            pitchSemitones = tempoPitch.pitchSemitones.coerceIn(-12f, 12f),
        ),
        compressor = compressor.copy(
            thresholdDb = compressor.thresholdDb.coerceIn(-60f, 0f),
            ratio = compressor.ratio.coerceIn(1f, 20f),
            attackMs = compressor.attackMs.coerceIn(0.1f, 200f),
            releaseMs = compressor.releaseMs.coerceIn(5f, 1500f),
            makeupDb = compressor.makeupDb.coerceIn(-12f, 12f),
            mix = compressor.mix.coerceIn(0f, 1f),
        ),
        gate = gate.copy(
            thresholdDb = gate.thresholdDb.coerceIn(-80f, 0f),
            attackMs = gate.attackMs.coerceIn(0.1f, 200f),
            holdMs = gate.holdMs.coerceIn(0f, 1000f),
            releaseMs = gate.releaseMs.coerceIn(5f, 1500f),
        ),
        delay = delay.copy(
            timeMs = delay.timeMs.coerceIn(1f, 1800f),
            feedback = delay.feedback.coerceIn(0f, 0.92f),
            mix = delay.mix.coerceIn(0f, 1f),
        ),
        modulation = modulation.copy(
            rateHz = modulation.rateHz.coerceIn(0.02f, 8f),
            depth = modulation.depth.coerceIn(0f, 1f),
            feedback = modulation.feedback.coerceIn(0f, 0.9f),
            mix = modulation.mix.coerceIn(0f, 1f),
        ),
        native = native.copy(
            masterGain = native.masterGain.coerceIn(0f, 1.5f),
            sourceFrequencyHz = native.sourceFrequencyHz.coerceIn(20f, 4000f),
            sourceGain = native.sourceGain.coerceIn(0f, 0.4f),
            exportDurationMs = native.exportDurationMs.coerceIn(250, 60_000),
            compareSlotA = native.compareSlotA.normalized(),
            compareSlotB = native.compareSlotB.normalized(),
        ),
        crossfade = crossfade.normalized(),
        sourceLoudness = sourceLoudness.normalized(),
        effectiveReplayGain = effectiveReplayGain.copy(
            gainDb = effectiveReplayGain.gainDb.coerceIn(EFFECTIVE_REPLAY_GAIN_MIN_DB, EFFECTIVE_REPLAY_GAIN_MAX_DB),
            userPreampDb = effectiveReplayGain.userPreampDb.coerceIn(-12f, 12f),
        ),
        replayGain = replayGain.copy(
            enabled = replayGainMode != ReplayGainMode.Off,
            mode = replayGainMode,
            trackPreampDb = replayGainPreampDb,
            preventClipping = preventClipping,
        ).normalized(),
        audioFocus = audioFocus.normalized(),
        dvc = dvc.normalized(),
        output = output.normalized(),
    )

fun OutputSettings.normalized(): OutputSettings =
    copy(
        hiResEnabled = hiResEnabled || mode == OutputMode.HiRes,
    )

val pulseAudioPresets: List<AudioPreset> = listOf(
    AudioPreset("flat", "Flat", "Transparent playback with no coloration."),
    AudioPreset("bass_warmth", "Bass Warmth", "Adds low-end weight without heavy midrange mud.", preampDb = -3f, eqBands = presetBands(4f, 3f, 1f, 0f, -1f, 0f, 0f, 0.5f, 1f, 1f)),
    AudioPreset("vocal_clarity", "Vocal Clarity", "Increases presence and reduces low-mid congestion.", preampDb = -2f, eqBands = presetBands(-1f, -1f, -1.5f, -2f, -1f, 1f, 3f, 2.5f, 1f, 0f)),
    AudioPreset("bright_detail", "Bright Detail", "Adds air and upper presence.", preampDb = -2f, eqBands = presetBands(0f, 0f, -0.5f, -1f, 0f, 0.5f, 1.5f, 2.5f, 3.2f, 3.5f)),
    AudioPreset("night", "Night Listening", "Keeps peaks gentler with softer bass.", preampDb = -4f, eqBands = presetBands(-2f, -1.5f, -1f, 0f, 0.5f, 1f, 1f, 0.5f, -0.5f, -1f)),
    AudioPreset("car", "Car Mode", "Adds bass and vocal presence for road noise.", preampDb = -4f, eqBands = presetBands(4f, 3.5f, 2f, -0.5f, 0f, 1.5f, 3f, 2f, 1f, 0.5f)),
    AudioPreset("small_speaker", "Small Speaker", "Controls bass while lifting midrange clarity.", preampDb = -2f, eqBands = presetBands(-4f, -3f, -1f, 1f, 2f, 2f, 1.5f, 1f, 0f, -0.5f)),
    AudioPreset("headphones", "Headphones", "Controlled low-end with a gentle crossfeed target.", preampDb = -2f, eqBands = presetBands(1.5f, 1f, 0.5f, 0f, -0.5f, 0f, 1f, 1f, 0.5f, 0f), stereo = StereoState(crossfeed = 0.18f)),
    AudioPreset("workout", "Workout", "Stronger punch and louder perceived energy.", preampDb = -5f, eqBands = presetBands(5f, 4f, 2f, 0f, -0.5f, 1f, 2f, 2f, 1f, 0f)),
    AudioPreset("hiphop", "Hip-hop", "Controlled sub and bass emphasis.", preampDb = -5f, eqBands = presetBands(5.5f, 5f, 3f, 0f, -1f, 0f, 1f, 1f, 0.5f, 0f)),
    AudioPreset("rock", "Rock", "Guitar presence and drum punch.", preampDb = -3f, eqBands = presetBands(2f, 2.5f, 1f, -0.5f, 0f, 1.5f, 2.5f, 2f, 1f, 0f)),
    AudioPreset("jazz", "Jazz", "Natural mids and cymbal clarity.", preampDb = -2f, eqBands = presetBands(0.5f, 0.5f, 0f, 0f, 1f, 1f, 1.5f, 1.8f, 1f, 0.5f)),
    AudioPreset("classical", "Classical", "Wide dynamics with minimal processing.", preampDb = -1f, eqBands = presetBands(0f, 0f, 0f, 0f, 0f, 0.5f, 0.8f, 1f, 0.8f, 0.5f)),
    AudioPreset("electronic", "Electronic", "Low-end extension and top-end sparkle.", preampDb = -5f, eqBands = presetBands(5f, 4f, 2f, 0f, -1f, 0f, 1f, 2f, 3f, 2.5f)),
    AudioPreset("podcast", "Podcast", "Voice intelligibility for spoken content.", preampDb = -2f, eqBands = presetBands(-3f, -2f, -1f, 0.5f, 2f, 3f, 3.5f, 2f, 0f, -1f)),
)

fun applyPreset(state: AudioEngineState, preset: AudioPreset): AudioEngineState =
    preset.normalized().let { safePreset ->
        state.copy(
            enabled = true,
            bypass = false,
            preampDb = safePreset.preampDb,
            replayGainMode = safePreset.replayGainMode,
            limiter = safePreset.limiter,
            eqEnabled = true,
            eqBands = safePreset.eqBands,
            parametricEq = defaultParametricBands(),
            bassDb = 0f,
            trebleDb = 0f,
            vocalClarityDb = 0f,
            loudnessDb = 0f,
            stereo = safePreset.stereo,
            activePresetId = safePreset.id,
            presetModified = false,
        ).normalized()
    }

fun AudioPreset.normalized(): AudioPreset =
    copy(
        preampDb = preampDb.coerceIn(-12f, 12f),
        eqBands = canonicalGraphicEqBands(eqBands),
        limiter = limiter.copy(
            ceilingDb = limiter.ceilingDb.coerceIn(-6f, 0f),
            releaseMs = limiter.releaseMs.coerceIn(5f, 1500f),
            strength = limiter.strength.coerceIn(0f, 1f),
        ),
        stereo = stereo.copy(
            balance = stereo.balance.coerceIn(-1f, 1f),
            stereoWidth = stereo.stereoWidth.coerceIn(0f, 1f),
            crossfeed = stereo.crossfeed.coerceIn(0f, 1f),
        ),
    )

private fun <T> List<T>.atOrdinal(ordinal: Int, fallback: T): T = getOrNull(ordinal) ?: fallback

fun loadAudioEngineState(context: Context): AudioEngineState {
    val prefs = context.getSharedPreferences(PREFS_AUDIO_ENGINE, Context.MODE_PRIVATE)
    val flat = AudioEngineState()
    val bands = prefs.getString("eqBands", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
    val parametricEq = decodeParametricEqBands(prefs.getString("parametricEq", null), flat.parametricEq)
    val replayMode = ReplayGainMode.entries.atOrdinal(prefs.getInt("replayGainMode", flat.replayGainMode.ordinal), flat.replayGainMode)
    val graphicEqRangeMode = GraphicEqRangeMode.entries.atOrdinal(
        prefs.getInt("graphicEqRangeMode", flat.graphicEqRangeMode.ordinal),
        flat.graphicEqRangeMode,
    )
    val replayPreamp = prefs.getFloat("replayGainPreampDb", flat.replayGainPreampDb)
    val preventClipping = prefs.getBoolean("preventClipping", flat.preventClipping)
    return flat.copy(
        enabled = prefs.getBoolean("enabled", flat.enabled),
        bypass = prefs.getBoolean("bypass", flat.bypass),
        preampDb = prefs.getFloat("preampDb", flat.preampDb),
        replayGainMode = replayMode,
        replayGainPreampDb = replayPreamp,
        preventClipping = preventClipping,
        eqEnabled = prefs.getBoolean("eqEnabled", flat.eqEnabled),
        graphicEqRangeMode = graphicEqRangeMode,
        eqBands = if (bands?.size == flat.eqBands.size) flat.eqBands.mapIndexed { index, band -> band.copy(gainDb = bands[index]) } else flat.eqBands,
        parametricEq = parametricEq,
        bassDb = prefs.getFloat("bassDb", flat.bassDb),
        trebleDb = prefs.getFloat("trebleDb", flat.trebleDb),
        vocalClarityDb = prefs.getFloat("vocalClarityDb", flat.vocalClarityDb),
        loudnessDb = prefs.getFloat("loudnessDb", flat.loudnessDb),
        stereo = flat.stereo.copy(
            balance = prefs.getFloat("balance", flat.stereo.balance),
            mono = prefs.getBoolean("mono", flat.stereo.mono),
            stereoWidth = prefs.getFloat("stereoWidth", flat.stereo.stereoWidth),
            crossfeed = prefs.getFloat("crossfeed", flat.stereo.crossfeed),
        ),
        reverb = flat.reverb.copy(
            enabled = prefs.getBoolean("reverbEnabled", flat.reverb.enabled),
            mix = prefs.getFloat("reverbMix", flat.reverb.mix),
            size = prefs.getFloat("reverbSize", flat.reverb.size),
            preDelayMs = prefs.getFloat("reverbPreDelayMs", flat.reverb.preDelayMs),
            damp = prefs.getFloat("reverbDamp", flat.reverb.damp),
            decay = prefs.getFloat("reverbDecay", flat.reverb.decay),
        ),
        tempoPitch = flat.tempoPitch.copy(
            speed = prefs.getFloat("speed", flat.tempoPitch.speed),
            tempo = prefs.getFloat("tempo", flat.tempoPitch.tempo),
            pitchSemitones = prefs.getFloat("pitch", flat.tempoPitch.pitchSemitones),
        ),
        compressor = CompressorState(
            enabled = prefs.getBoolean("compressorEnabled", flat.compressor.enabled),
            thresholdDb = prefs.getFloat("compressorThresholdDb", flat.compressor.thresholdDb),
            ratio = prefs.getFloat("compressorRatio", flat.compressor.ratio),
            attackMs = prefs.getFloat("compressorAttackMs", flat.compressor.attackMs),
            releaseMs = prefs.getFloat("compressorReleaseMs", flat.compressor.releaseMs),
            makeupDb = prefs.getFloat("compressorMakeupDb", flat.compressor.makeupDb),
            mix = prefs.getFloat("compressorMix", flat.compressor.mix),
        ),
        gate = GateState(
            enabled = prefs.getBoolean("gateEnabled", flat.gate.enabled),
            thresholdDb = prefs.getFloat("gateThresholdDb", flat.gate.thresholdDb),
            attackMs = prefs.getFloat("gateAttackMs", flat.gate.attackMs),
            holdMs = prefs.getFloat("gateHoldMs", flat.gate.holdMs),
            releaseMs = prefs.getFloat("gateReleaseMs", flat.gate.releaseMs),
        ),
        delay = DelayState(
            enabled = prefs.getBoolean("delayEnabled", flat.delay.enabled),
            timeMs = prefs.getFloat("delayTimeMs", flat.delay.timeMs),
            feedback = prefs.getFloat("delayFeedback", flat.delay.feedback),
            mix = prefs.getFloat("delayMix", flat.delay.mix),
        ),
        modulation = ModulationState(
            chorusEnabled = prefs.getBoolean("modChorusEnabled", flat.modulation.chorusEnabled),
            flangerEnabled = prefs.getBoolean("modFlangerEnabled", flat.modulation.flangerEnabled),
            phaserEnabled = prefs.getBoolean("modPhaserEnabled", flat.modulation.phaserEnabled),
            rateHz = prefs.getFloat("modRateHz", flat.modulation.rateHz),
            depth = prefs.getFloat("modDepth", flat.modulation.depth),
            feedback = prefs.getFloat("modFeedback", flat.modulation.feedback),
            mix = prefs.getFloat("modMix", flat.modulation.mix),
        ),
        native = NativeAudioSettings(
            enabled = prefs.getBoolean("nativeEnabled", flat.native.enabled),
            media3DspEnabled = prefs.getBoolean("nativeMedia3DspEnabled", flat.native.media3DspEnabled),
            lowLatencyToneEnabled = prefs.getBoolean("nativeLowLatencyToneEnabled", flat.native.lowLatencyToneEnabled),
            masterGain = prefs.getFloat("nativeMasterGain", flat.native.masterGain),
            sourceFrequencyHz = prefs.getFloat("nativeSourceFrequencyHz", flat.native.sourceFrequencyHz),
            sourceGain = prefs.getFloat("nativeSourceGain", flat.native.sourceGain),
            diagnosticsEnabled = prefs.getBoolean("nativeDiagnosticsEnabled", flat.native.diagnosticsEnabled),
            exportDurationMs = prefs.getInt("nativeExportDurationMs", flat.native.exportDurationMs),
            activeCompareSlot = NativeCompareSlot.entries.atOrdinal(prefs.getInt("nativeActiveCompareSlot", flat.native.activeCompareSlot.ordinal), flat.native.activeCompareSlot),
            compareSlotA = decodeNativePresetSlot(
                prefs.getString("nativeCompareASlot", null),
                flat.native.compareSlotA.copy(
                    masterGain = prefs.getFloat("nativeCompareAMasterGain", flat.native.compareSlotA.masterGain),
                    sourceFrequencyHz = prefs.getFloat("nativeCompareASourceFrequencyHz", flat.native.compareSlotA.sourceFrequencyHz),
                    sourceGain = prefs.getFloat("nativeCompareASourceGain", flat.native.compareSlotA.sourceGain),
                ),
            ),
            compareSlotB = decodeNativePresetSlot(
                prefs.getString("nativeCompareBSlot", null),
                flat.native.compareSlotB.copy(
                    masterGain = prefs.getFloat("nativeCompareBMasterGain", flat.native.compareSlotB.masterGain),
                    sourceFrequencyHz = prefs.getFloat("nativeCompareBSourceFrequencyHz", flat.native.compareSlotB.sourceFrequencyHz),
                    sourceGain = prefs.getFloat("nativeCompareBSourceGain", flat.native.compareSlotB.sourceGain),
                ),
            ),
        ),
        activePresetId = prefs.getString("activePresetId", flat.activePresetId) ?: flat.activePresetId,
        presetModified = prefs.getBoolean("presetModified", flat.presetModified),
        audioInfo = AudioInfoSettings(
            showOnMainScreenLongPress = prefs.getBoolean("audioInfoShowOnMainScreenLongPress", flat.audioInfo.showOnMainScreenLongPress),
            showOutputPath = prefs.getBoolean("audioInfoShowOutputPath", flat.audioInfo.showOutputPath),
            showDspChain = prefs.getBoolean("audioInfoShowDspChain", flat.audioInfo.showDspChain),
        ),
        crossfade = CrossfadeSettings(
            gaplessEnabled = prefs.getBoolean("crossfadeGaplessEnabled", flat.crossfade.gaplessEnabled),
            crossfadeEnabled = prefs.getBoolean("crossfadeEnabled", flat.crossfade.crossfadeEnabled),
            durationMs = prefs.getInt("crossfadeDurationMs", flat.crossfade.durationMs),
            fadeCurve = FadeCurve.entries.atOrdinal(prefs.getInt("crossfadeFadeCurve", flat.crossfade.fadeCurve.ordinal), flat.crossfade.fadeCurve),
            fadeOnPlay = prefs.getBoolean("crossfadeFadeOnPlay", flat.crossfade.fadeOnPlay),
            fadeOnPause = prefs.getBoolean("crossfadeFadeOnPause", flat.crossfade.fadeOnPause),
            fadeOnManualSkip = prefs.getBoolean("crossfadeFadeOnManualSkip", flat.crossfade.fadeOnManualSkip),
            disableForAlbums = prefs.getBoolean("crossfadeDisableForAlbums", flat.crossfade.disableForAlbums),
            disableForShortTracks = prefs.getBoolean("crossfadeDisableForShortTracks", flat.crossfade.disableForShortTracks),
        ),
        replayGain = ReplayGainSettings(
            enabled = prefs.getBoolean("replayGainEnabled", replayMode != ReplayGainMode.Off),
            mode = replayMode,
            source = ReplayGainSource.entries.atOrdinal(prefs.getInt("replayGainSource", flat.replayGain.source.ordinal), flat.replayGain.source),
            trackPreampDb = replayPreamp,
            albumPreampDb = prefs.getFloat("replayGainAlbumPreampDb", flat.replayGain.albumPreampDb),
            noRgPreampDb = prefs.getFloat("replayGainNoRgPreampDb", flat.replayGain.noRgPreampDb),
            preventClipping = preventClipping,
            showInAudioInfo = prefs.getBoolean("replayGainShowInAudioInfo", flat.replayGain.showInAudioInfo),
        ),
        audioFocus = AudioFocusSettings(
            requestOnPlay = prefs.getBoolean("audioFocusRequestOnPlay", flat.audioFocus.requestOnPlay),
            onPermanentLoss = PermanentFocusLossAction.entries.atOrdinal(prefs.getInt("audioFocusOnPermanentLoss", flat.audioFocus.onPermanentLoss.ordinal), flat.audioFocus.onPermanentLoss),
            onTransientLoss = TransientFocusLossAction.entries.atOrdinal(prefs.getInt("audioFocusOnTransientLoss", flat.audioFocus.onTransientLoss.ordinal), flat.audioFocus.onTransientLoss),
            onDuck = DuckRequestAction.entries.atOrdinal(prefs.getInt("audioFocusOnDuck", flat.audioFocus.onDuck.ordinal), flat.audioFocus.onDuck),
            resumeAfterCall = prefs.getBoolean("audioFocusResumeAfterCall", flat.audioFocus.resumeAfterCall),
            resumeAfterNotification = prefs.getBoolean("audioFocusResumeAfterNotification", flat.audioFocus.resumeAfterNotification),
            duckVolume = prefs.getFloat("audioFocusDuckVolume", flat.audioFocus.duckVolume),
            duckFadeMs = prefs.getInt("audioFocusDuckFadeMs", flat.audioFocus.duckFadeMs),
            bluetoothBehavior = BluetoothFocusBehavior.entries.atOrdinal(prefs.getInt("audioFocusBluetoothBehavior", flat.audioFocus.bluetoothBehavior.ordinal), flat.audioFocus.bluetoothBehavior),
            resumeOnlyIfAutoPaused = prefs.getBoolean("audioFocusResumeOnlyIfAutoPaused", flat.audioFocus.resumeOnlyIfAutoPaused),
        ),
        resampler = ResamplerSettings(
            mode = ResamplerMode.entries.atOrdinal(prefs.getInt("resamplerMode", flat.resampler.mode.ordinal), flat.resampler.mode),
            outputSampleRate = OutputSampleRate.entries.atOrdinal(prefs.getInt("resamplerOutputSampleRate", flat.resampler.outputSampleRate.ordinal), flat.resampler.outputSampleRate),
            dither = prefs.getBoolean("resamplerDither", flat.resampler.dither),
            quality = ResamplerQuality.entries.atOrdinal(prefs.getInt("resamplerQuality", flat.resampler.quality.ordinal), flat.resampler.quality),
            showConversionInfo = prefs.getBoolean("resamplerShowConversionInfo", flat.resampler.showConversionInfo),
            batterySaverMode = prefs.getBoolean("resamplerBatterySaverMode", flat.resampler.batterySaverMode),
        ),
        dvc = DirectVolumeControlSettings(
            enabled = prefs.getBoolean("dvcEnabled", flat.dvc.enabled),
            bluetoothEnabled = prefs.getBoolean("dvcBluetoothEnabled", flat.dvc.bluetoothEnabled),
            headroomDb = prefs.getFloat("dvcHeadroomDb", flat.dvc.headroomDb),
            volumeSteps = DvcVolumeSteps.entries.atOrdinal(prefs.getInt("dvcVolumeSteps", flat.dvc.volumeSteps.ordinal), flat.dvc.volumeSteps),
            perDeviceDvc = prefs.getBoolean("dvcPerDeviceDvc", flat.dvc.perDeviceDvc),
            compatibilityMode = prefs.getBoolean("dvcCompatibilityMode", flat.dvc.compatibilityMode),
        ),
        output = OutputSettings(
            mode = OutputMode.entries.atOrdinal(prefs.getInt("outputMode", flat.output.mode.ordinal), flat.output.mode),
            deviceProfile = DeviceProfile.entries.atOrdinal(prefs.getInt("outputDeviceProfile", flat.output.deviceProfile.ordinal), flat.output.deviceProfile),
            bufferMode = BufferMode.entries.atOrdinal(prefs.getInt("outputBufferMode", flat.output.bufferMode.ordinal), flat.output.bufferMode),
            hiResEnabled = prefs.getBoolean("outputHiResEnabled", flat.output.hiResEnabled),
            sampleRate = OutputSampleRate.entries.atOrdinal(prefs.getInt("outputSampleRate", flat.output.sampleRate.ordinal), flat.output.sampleRate),
            bitDepth = OutputBitDepth.entries.atOrdinal(prefs.getInt("outputBitDepth", flat.output.bitDepth.ordinal), flat.output.bitDepth),
            bitPerfectAttemptEnabled = prefs.getBoolean("outputBitPerfectAttemptEnabled", flat.output.bitPerfectAttemptEnabled),
            fallbackMode = OutputFallbackMode.entries.atOrdinal(prefs.getInt("outputFallbackMode", flat.output.fallbackMode.ordinal), flat.output.fallbackMode),
            showOutputWarnings = prefs.getBoolean("outputShowOutputWarnings", flat.output.showOutputWarnings),
        ),
        advancedTweaks = AdvancedAudioTweaksSettings(
            volumeSteps = DvcVolumeSteps.entries.atOrdinal(prefs.getInt("advancedVolumeSteps", flat.advancedTweaks.volumeSteps.ordinal), flat.advancedTweaks.volumeSteps),
            musicFxEnabled = prefs.getBoolean("advancedMusicFxEnabled", flat.advancedTweaks.musicFxEnabled),
            safeMode = prefs.getBoolean("advancedSafeMode", flat.advancedTweaks.safeMode),
            debugAudioInfo = prefs.getBoolean("advancedDebugAudioInfo", flat.advancedTweaks.debugAudioInfo),
        ),
    ).normalized()
}

fun saveAudioEngineState(context: Context, state: AudioEngineState) {
    val normalized = state.normalized()
    context.getSharedPreferences(PREFS_AUDIO_ENGINE, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("enabled", normalized.enabled)
        .putBoolean("bypass", normalized.bypass)
        .putFloat("preampDb", normalized.preampDb)
        .putInt("replayGainMode", normalized.replayGainMode.ordinal)
        .putFloat("replayGainPreampDb", normalized.replayGainPreampDb)
        .putBoolean("preventClipping", normalized.preventClipping)
        .putBoolean("eqEnabled", normalized.eqEnabled)
        .putInt("graphicEqRangeMode", normalized.graphicEqRangeMode.ordinal)
        .putString("eqBands", normalized.eqBands.joinToString(",") { it.gainDb.toString() })
        .putString("parametricEq", encodeParametricEqBands(normalized.parametricEq))
        .putFloat("bassDb", normalized.bassDb)
        .putFloat("trebleDb", normalized.trebleDb)
        .putFloat("vocalClarityDb", normalized.vocalClarityDb)
        .putFloat("loudnessDb", normalized.loudnessDb)
        .putFloat("balance", normalized.stereo.balance)
        .putBoolean("mono", normalized.stereo.mono)
        .putFloat("stereoWidth", normalized.stereo.stereoWidth)
        .putFloat("crossfeed", normalized.stereo.crossfeed)
        .putBoolean("reverbEnabled", normalized.reverb.enabled)
        .putFloat("reverbMix", normalized.reverb.mix)
        .putFloat("reverbSize", normalized.reverb.size)
        .putFloat("reverbPreDelayMs", normalized.reverb.preDelayMs)
        .putFloat("reverbDamp", normalized.reverb.damp)
        .putFloat("reverbDecay", normalized.reverb.decay)
        .putFloat("speed", normalized.tempoPitch.speed)
        .putFloat("tempo", normalized.tempoPitch.tempo)
        .putFloat("pitch", normalized.tempoPitch.pitchSemitones)
        .putBoolean("compressorEnabled", normalized.compressor.enabled)
        .putFloat("compressorThresholdDb", normalized.compressor.thresholdDb)
        .putFloat("compressorRatio", normalized.compressor.ratio)
        .putFloat("compressorAttackMs", normalized.compressor.attackMs)
        .putFloat("compressorReleaseMs", normalized.compressor.releaseMs)
        .putFloat("compressorMakeupDb", normalized.compressor.makeupDb)
        .putFloat("compressorMix", normalized.compressor.mix)
        .putBoolean("gateEnabled", normalized.gate.enabled)
        .putFloat("gateThresholdDb", normalized.gate.thresholdDb)
        .putFloat("gateAttackMs", normalized.gate.attackMs)
        .putFloat("gateHoldMs", normalized.gate.holdMs)
        .putFloat("gateReleaseMs", normalized.gate.releaseMs)
        .putBoolean("delayEnabled", normalized.delay.enabled)
        .putFloat("delayTimeMs", normalized.delay.timeMs)
        .putFloat("delayFeedback", normalized.delay.feedback)
        .putFloat("delayMix", normalized.delay.mix)
        .putBoolean("modChorusEnabled", normalized.modulation.chorusEnabled)
        .putBoolean("modFlangerEnabled", normalized.modulation.flangerEnabled)
        .putBoolean("modPhaserEnabled", normalized.modulation.phaserEnabled)
        .putFloat("modRateHz", normalized.modulation.rateHz)
        .putFloat("modDepth", normalized.modulation.depth)
        .putFloat("modFeedback", normalized.modulation.feedback)
        .putFloat("modMix", normalized.modulation.mix)
        .putBoolean("nativeEnabled", normalized.native.enabled)
        .putBoolean("nativeMedia3DspEnabled", normalized.native.media3DspEnabled)
        .putBoolean("nativeLowLatencyToneEnabled", normalized.native.lowLatencyToneEnabled)
        .putFloat("nativeMasterGain", normalized.native.masterGain)
        .putFloat("nativeSourceFrequencyHz", normalized.native.sourceFrequencyHz)
        .putFloat("nativeSourceGain", normalized.native.sourceGain)
        .putBoolean("nativeDiagnosticsEnabled", normalized.native.diagnosticsEnabled)
        .putInt("nativeExportDurationMs", normalized.native.exportDurationMs)
        .putInt("nativeActiveCompareSlot", normalized.native.activeCompareSlot.ordinal)
        .putFloat("nativeCompareAMasterGain", normalized.native.compareSlotA.masterGain)
        .putFloat("nativeCompareASourceFrequencyHz", normalized.native.compareSlotA.sourceFrequencyHz)
        .putFloat("nativeCompareASourceGain", normalized.native.compareSlotA.sourceGain)
        .putString("nativeCompareASlot", encodeNativePresetSlot(normalized.native.compareSlotA))
        .putFloat("nativeCompareBMasterGain", normalized.native.compareSlotB.masterGain)
        .putFloat("nativeCompareBSourceFrequencyHz", normalized.native.compareSlotB.sourceFrequencyHz)
        .putFloat("nativeCompareBSourceGain", normalized.native.compareSlotB.sourceGain)
        .putString("nativeCompareBSlot", encodeNativePresetSlot(normalized.native.compareSlotB))
        .putString("activePresetId", normalized.activePresetId)
        .putBoolean("presetModified", normalized.presetModified)
        .putBoolean("audioInfoShowOnMainScreenLongPress", normalized.audioInfo.showOnMainScreenLongPress)
        .putBoolean("audioInfoShowOutputPath", normalized.audioInfo.showOutputPath)
        .putBoolean("audioInfoShowDspChain", normalized.audioInfo.showDspChain)
        .putBoolean("crossfadeGaplessEnabled", normalized.crossfade.gaplessEnabled)
        .putBoolean("crossfadeEnabled", normalized.crossfade.crossfadeEnabled)
        .putInt("crossfadeDurationMs", normalized.crossfade.durationMs)
        .putInt("crossfadeFadeCurve", normalized.crossfade.fadeCurve.ordinal)
        .putBoolean("crossfadeFadeOnPlay", normalized.crossfade.fadeOnPlay)
        .putBoolean("crossfadeFadeOnPause", normalized.crossfade.fadeOnPause)
        .putBoolean("crossfadeFadeOnManualSkip", normalized.crossfade.fadeOnManualSkip)
        .putBoolean("crossfadeDisableForAlbums", normalized.crossfade.disableForAlbums)
        .putBoolean("crossfadeDisableForShortTracks", normalized.crossfade.disableForShortTracks)
        .putBoolean("replayGainEnabled", normalized.replayGain.enabled)
        .putInt("replayGainSource", normalized.replayGain.source.ordinal)
        .putFloat("replayGainAlbumPreampDb", normalized.replayGain.albumPreampDb)
        .putFloat("replayGainNoRgPreampDb", normalized.replayGain.noRgPreampDb)
        .putBoolean("replayGainShowInAudioInfo", normalized.replayGain.showInAudioInfo)
        .putBoolean("audioFocusRequestOnPlay", normalized.audioFocus.requestOnPlay)
        .putInt("audioFocusOnPermanentLoss", normalized.audioFocus.onPermanentLoss.ordinal)
        .putInt("audioFocusOnTransientLoss", normalized.audioFocus.onTransientLoss.ordinal)
        .putInt("audioFocusOnDuck", normalized.audioFocus.onDuck.ordinal)
        .putBoolean("audioFocusResumeAfterCall", normalized.audioFocus.resumeAfterCall)
        .putBoolean("audioFocusResumeAfterNotification", normalized.audioFocus.resumeAfterNotification)
        .putFloat("audioFocusDuckVolume", normalized.audioFocus.duckVolume)
        .putInt("audioFocusDuckFadeMs", normalized.audioFocus.duckFadeMs)
        .putInt("audioFocusBluetoothBehavior", normalized.audioFocus.bluetoothBehavior.ordinal)
        .putBoolean("audioFocusResumeOnlyIfAutoPaused", normalized.audioFocus.resumeOnlyIfAutoPaused)
        .putInt("resamplerMode", normalized.resampler.mode.ordinal)
        .putInt("resamplerOutputSampleRate", normalized.resampler.outputSampleRate.ordinal)
        .putBoolean("resamplerDither", normalized.resampler.dither)
        .putInt("resamplerQuality", normalized.resampler.quality.ordinal)
        .putBoolean("resamplerShowConversionInfo", normalized.resampler.showConversionInfo)
        .putBoolean("resamplerBatterySaverMode", normalized.resampler.batterySaverMode)
        .putBoolean("dvcEnabled", normalized.dvc.enabled)
        .putBoolean("dvcBluetoothEnabled", normalized.dvc.bluetoothEnabled)
        .putFloat("dvcHeadroomDb", normalized.dvc.headroomDb)
        .putInt("dvcVolumeSteps", normalized.dvc.volumeSteps.ordinal)
        .putBoolean("dvcPerDeviceDvc", normalized.dvc.perDeviceDvc)
        .putBoolean("dvcCompatibilityMode", normalized.dvc.compatibilityMode)
        .putInt("outputMode", normalized.output.mode.ordinal)
        .putInt("outputDeviceProfile", normalized.output.deviceProfile.ordinal)
        .putInt("outputBufferMode", normalized.output.bufferMode.ordinal)
        .putBoolean("outputHiResEnabled", normalized.output.hiResEnabled)
        .putInt("outputSampleRate", normalized.output.sampleRate.ordinal)
        .putInt("outputBitDepth", normalized.output.bitDepth.ordinal)
        .putBoolean("outputBitPerfectAttemptEnabled", normalized.output.bitPerfectAttemptEnabled)
        .putInt("outputFallbackMode", normalized.output.fallbackMode.ordinal)
        .putBoolean("outputShowOutputWarnings", normalized.output.showOutputWarnings)
        .putInt("advancedVolumeSteps", normalized.advancedTweaks.volumeSteps.ordinal)
        .putBoolean("advancedMusicFxEnabled", normalized.advancedTweaks.musicFxEnabled)
        .putBoolean("advancedSafeMode", normalized.advancedTweaks.safeMode)
        .putBoolean("advancedDebugAudioInfo", normalized.advancedTweaks.debugAudioInfo)
        .apply()
}

private fun presetBands(vararg gains: Float): List<GraphicEqBand> =
    flatEqBands().mapIndexed { index, band -> band.copy(gainDb = gains.getOrElse(index) { 0f }) }

fun AudioEngineState.toNativePresetSlot(name: String): NativePresetSlot =
    NativePresetSlot(
        name = name,
        enabled = enabled,
        bypass = bypass,
        preampDb = preampDb,
        replayGainMode = replayGainMode,
        replayGainPreampDb = replayGainPreampDb,
        preventClipping = preventClipping,
        limiter = limiter,
        eqEnabled = eqEnabled,
        graphicEqRangeMode = graphicEqRangeMode,
        masterGain = native.masterGain,
        sourceFrequencyHz = native.sourceFrequencyHz,
        sourceGain = native.sourceGain,
        eqGains = eqBands.map { it.gainDb },
        parametricEq = parametricEq,
        bassDb = bassDb,
        trebleDb = trebleDb,
        vocalClarityDb = vocalClarityDb,
        loudnessDb = loudnessDb,
        stereo = stereo,
        reverb = reverb,
        tempoPitch = tempoPitch,
        compressor = compressor,
        gate = gate,
        delay = delay,
        modulation = modulation,
        nativeEnabled = native.enabled,
        media3DspEnabled = native.media3DspEnabled,
        lowLatencyToneEnabled = native.lowLatencyToneEnabled,
    ).normalized()

fun AudioEngineState.withNativePresetSlot(slot: NativePresetSlot, activeSlot: NativeCompareSlot): AudioEngineState {
    val normalizedSlot = slot.normalized()
    return copy(
        enabled = normalizedSlot.enabled,
        bypass = normalizedSlot.bypass,
        preampDb = normalizedSlot.preampDb,
        replayGainMode = normalizedSlot.replayGainMode,
        replayGainPreampDb = normalizedSlot.replayGainPreampDb,
        preventClipping = normalizedSlot.preventClipping,
        limiter = normalizedSlot.limiter,
        eqEnabled = normalizedSlot.eqEnabled,
        graphicEqRangeMode = normalizedSlot.graphicEqRangeMode,
        eqBands = canonicalGraphicEqBands(eqBands, normalizedSlot.graphicEqRangeMode.requestedGainRange())
            .mapIndexed { index, band -> band.copy(gainDb = normalizedSlot.eqGains[index]) },
        parametricEq = normalizedSlot.parametricEq,
        bassDb = normalizedSlot.bassDb,
        trebleDb = normalizedSlot.trebleDb,
        vocalClarityDb = normalizedSlot.vocalClarityDb,
        loudnessDb = normalizedSlot.loudnessDb,
        stereo = normalizedSlot.stereo,
        reverb = normalizedSlot.reverb,
        tempoPitch = normalizedSlot.tempoPitch,
        compressor = normalizedSlot.compressor,
        gate = normalizedSlot.gate,
        delay = normalizedSlot.delay,
        modulation = normalizedSlot.modulation,
        native = native.copy(
            enabled = normalizedSlot.nativeEnabled,
            media3DspEnabled = normalizedSlot.media3DspEnabled,
            lowLatencyToneEnabled = normalizedSlot.lowLatencyToneEnabled,
            masterGain = normalizedSlot.masterGain,
            sourceFrequencyHz = normalizedSlot.sourceFrequencyHz,
            sourceGain = normalizedSlot.sourceGain,
            activeCompareSlot = activeSlot,
        ),
        presetModified = true,
    ).normalized()
}

private fun NativePresetSlot.normalized(): NativePresetSlot =
    copy(
        preampDb = preampDb.coerceIn(-12f, 12f),
        replayGainPreampDb = replayGainPreampDb.coerceIn(-12f, 12f),
        limiter = limiter.copy(
            ceilingDb = limiter.ceilingDb.coerceIn(-6f, 0f),
            releaseMs = limiter.releaseMs.coerceIn(5f, 1500f),
            strength = limiter.strength.coerceIn(0f, 1f),
        ),
        masterGain = masterGain.coerceIn(0f, 1.5f),
        sourceFrequencyHz = sourceFrequencyHz.coerceIn(20f, 4000f),
        sourceGain = sourceGain.coerceIn(0f, 0.4f),
        eqGains = canonicalGraphicEqGains(eqGains, graphicEqRangeMode.requestedGainRange()),
        parametricEq = canonicalParametricEqBands(parametricEq),
        bassDb = bassDb.coerceIn(-12f, 12f),
        trebleDb = trebleDb.coerceIn(-12f, 12f),
        vocalClarityDb = vocalClarityDb.coerceIn(-12f, 12f),
        loudnessDb = loudnessDb.coerceIn(0f, 12f),
        stereo = stereo.copy(
            balance = stereo.balance.coerceIn(-1f, 1f),
            stereoWidth = stereo.stereoWidth.coerceIn(0f, 1f),
            crossfeed = stereo.crossfeed.coerceIn(0f, 1f),
        ),
        reverb = reverb.copy(
            mix = reverb.mix.coerceIn(0f, 1f),
            size = reverb.size.coerceIn(0f, 1f),
            preDelayMs = reverb.preDelayMs.coerceIn(0f, 120f),
            damp = reverb.damp.coerceIn(0f, 1f),
            decay = reverb.decay.coerceIn(0.2f, 6f),
        ),
        tempoPitch = tempoPitch.copy(
            speed = tempoPitch.speed.coerceIn(0.5f, 2f),
            tempo = tempoPitch.tempo.coerceIn(0.5f, 2f),
            pitchSemitones = tempoPitch.pitchSemitones.coerceIn(-12f, 12f),
        ),
        compressor = compressor.copy(
            thresholdDb = compressor.thresholdDb.coerceIn(-60f, 0f),
            ratio = compressor.ratio.coerceIn(1f, 20f),
            attackMs = compressor.attackMs.coerceIn(0.1f, 200f),
            releaseMs = compressor.releaseMs.coerceIn(5f, 1500f),
            makeupDb = compressor.makeupDb.coerceIn(-12f, 12f),
            mix = compressor.mix.coerceIn(0f, 1f),
        ),
        gate = gate.copy(
            thresholdDb = gate.thresholdDb.coerceIn(-80f, 0f),
            attackMs = gate.attackMs.coerceIn(0.1f, 200f),
            holdMs = gate.holdMs.coerceIn(0f, 1000f),
            releaseMs = gate.releaseMs.coerceIn(5f, 1500f),
        ),
        delay = delay.copy(
            timeMs = delay.timeMs.coerceIn(1f, 1800f),
            feedback = delay.feedback.coerceIn(0f, 0.92f),
            mix = delay.mix.coerceIn(0f, 1f),
        ),
        modulation = modulation.copy(
            rateHz = modulation.rateHz.coerceIn(0.02f, 8f),
            depth = modulation.depth.coerceIn(0f, 1f),
            feedback = modulation.feedback.coerceIn(0f, 0.9f),
            mix = modulation.mix.coerceIn(0f, 1f),
        ),
    )

private fun canonicalGraphicEqGains(
    gains: List<Float>,
    gainRange: ClosedFloatingPointRange<Float> = standardGraphicEqGainRange(),
): List<Float> =
    GRAPHIC_EQ_CENTER_FREQUENCIES_HZ.mapIndexed { index, _ ->
        quantizeAudioDb(gains.getOrNull(index) ?: 0f, gainRange)
    }

private fun encodeNativePresetSlot(slot: NativePresetSlot): String {
    val normalized = slot.normalized()
    return JSONObject()
        .put("name", normalized.name)
        .put("enabled", normalized.enabled)
        .put("bypass", normalized.bypass)
        .put("preampDb", normalized.preampDb)
        .put("replayGainMode", normalized.replayGainMode.name)
        .put("replayGainPreampDb", normalized.replayGainPreampDb)
        .put("preventClipping", normalized.preventClipping)
        .put("limiter", encodeLimiter(normalized.limiter))
        .put("eqEnabled", normalized.eqEnabled)
        .put("graphicEqRangeMode", normalized.graphicEqRangeMode.name)
        .put("masterGain", normalized.masterGain)
        .put("sourceFrequencyHz", normalized.sourceFrequencyHz)
        .put("sourceGain", normalized.sourceGain)
        .put("eqGains", JSONArray(normalized.eqGains))
        .put("parametricEq", encodeParametricEqBandsArray(normalized.parametricEq))
        .put("bassDb", normalized.bassDb)
        .put("trebleDb", normalized.trebleDb)
        .put("vocalClarityDb", normalized.vocalClarityDb)
        .put("loudnessDb", normalized.loudnessDb)
        .put("stereo", encodeStereo(normalized.stereo))
        .put("reverb", encodeReverb(normalized.reverb))
        .put("tempoPitch", encodeTempoPitch(normalized.tempoPitch))
        .put("compressor", encodeCompressor(normalized.compressor))
        .put("gate", encodeGate(normalized.gate))
        .put("delay", encodeDelay(normalized.delay))
        .put("modulation", encodeModulation(normalized.modulation))
        .put("nativeEnabled", normalized.nativeEnabled)
        .put("media3DspEnabled", normalized.media3DspEnabled)
        .put("lowLatencyToneEnabled", normalized.lowLatencyToneEnabled)
        .toString()
}

private fun decodeNativePresetSlot(raw: String?, fallback: NativePresetSlot): NativePresetSlot {
    if (raw.isNullOrBlank()) return fallback.normalized()
    return runCatching {
        val json = JSONObject(raw)
        NativePresetSlot(
            name = json.optString("name", fallback.name),
            enabled = json.optBoolean("enabled", fallback.enabled),
            bypass = json.optBoolean("bypass", fallback.bypass),
            preampDb = json.optFloat("preampDb", fallback.preampDb),
            replayGainMode = enumValue(json.optString("replayGainMode"), fallback.replayGainMode),
            replayGainPreampDb = json.optFloat("replayGainPreampDb", fallback.replayGainPreampDb),
            preventClipping = json.optBoolean("preventClipping", fallback.preventClipping),
            limiter = decodeLimiter(json.optJSONObject("limiter"), fallback.limiter),
            eqEnabled = json.optBoolean("eqEnabled", fallback.eqEnabled),
            graphicEqRangeMode = enumValue(json.optString("graphicEqRangeMode"), fallback.graphicEqRangeMode),
            masterGain = json.optFloat("masterGain", fallback.masterGain),
            sourceFrequencyHz = json.optFloat("sourceFrequencyHz", fallback.sourceFrequencyHz),
            sourceGain = json.optFloat("sourceGain", fallback.sourceGain),
            eqGains = json.optJSONArray("eqGains")?.toFloatList() ?: fallback.eqGains,
            parametricEq = decodeParametricEqBands(json.optJSONArray("parametricEq"), fallback.parametricEq),
            bassDb = json.optFloat("bassDb", fallback.bassDb),
            trebleDb = json.optFloat("trebleDb", fallback.trebleDb),
            vocalClarityDb = json.optFloat("vocalClarityDb", fallback.vocalClarityDb),
            loudnessDb = json.optFloat("loudnessDb", fallback.loudnessDb),
            stereo = decodeStereo(json.optJSONObject("stereo"), fallback.stereo),
            reverb = decodeReverb(json.optJSONObject("reverb"), fallback.reverb),
            tempoPitch = decodeTempoPitch(json.optJSONObject("tempoPitch"), fallback.tempoPitch),
            compressor = decodeCompressor(json.optJSONObject("compressor"), fallback.compressor),
            gate = decodeGate(json.optJSONObject("gate"), fallback.gate),
            delay = decodeDelay(json.optJSONObject("delay"), fallback.delay),
            modulation = decodeModulation(json.optJSONObject("modulation"), fallback.modulation),
            nativeEnabled = json.optBoolean("nativeEnabled", fallback.nativeEnabled),
            media3DspEnabled = json.optBoolean("media3DspEnabled", fallback.media3DspEnabled),
            lowLatencyToneEnabled = json.optBoolean("lowLatencyToneEnabled", fallback.lowLatencyToneEnabled),
        ).normalized()
    }.getOrElse { fallback.normalized() }
}

private fun encodeParametricEqBands(bands: List<ParametricEqBand>): String =
    encodeParametricEqBandsArray(bands).toString()

private fun decodeParametricEqBands(raw: String?, fallback: List<ParametricEqBand>): List<ParametricEqBand> =
    if (raw.isNullOrBlank()) {
        fallback
    } else {
        runCatching { decodeParametricEqBands(JSONArray(raw), fallback) }.getOrElse { fallback }
    }

private fun encodeParametricEqBandsArray(bands: List<ParametricEqBand>): JSONArray =
    JSONArray(bands.map { band ->
        JSONObject()
            .put("enabled", band.enabled)
            .put("type", band.type.name)
            .put("frequencyHz", band.frequencyHz)
            .put("gainDb", band.gainDb)
            .put("q", band.q)
    })

private fun decodeParametricEqBands(array: JSONArray?, fallback: List<ParametricEqBand>): List<ParametricEqBand> {
    if (array == null) return fallback
    return List(minOf(array.length(), PARAMETRIC_EQ_SLOT_COUNT)) { index ->
        val item = array.optJSONObject(index)
        val base = fallback.getOrNull(index) ?: ParametricEqBand()
        ParametricEqBand(
            enabled = item?.optBoolean("enabled", base.enabled) ?: base.enabled,
            type = enumValue(item?.optString("type"), base.type),
            frequencyHz = item?.optFloat("frequencyHz", base.frequencyHz) ?: base.frequencyHz,
            gainDb = item?.optFloat("gainDb", base.gainDb) ?: base.gainDb,
            q = item?.optFloat("q", base.q) ?: base.q,
        )
    }
}

private fun encodeLimiter(value: LimiterState): JSONObject =
    JSONObject()
        .put("enabled", value.enabled)
        .put("ceilingDb", value.ceilingDb)
        .put("releaseMs", value.releaseMs)
        .put("strength", value.strength)

private fun decodeLimiter(json: JSONObject?, fallback: LimiterState): LimiterState =
    if (json == null) fallback else LimiterState(
        enabled = json.optBoolean("enabled", fallback.enabled),
        ceilingDb = json.optFloat("ceilingDb", fallback.ceilingDb),
        releaseMs = json.optFloat("releaseMs", fallback.releaseMs),
        strength = json.optFloat("strength", fallback.strength),
    )

private fun encodeStereo(value: StereoState): JSONObject =
    JSONObject()
        .put("balance", value.balance)
        .put("mono", value.mono)
        .put("stereoWidth", value.stereoWidth)
        .put("crossfeed", value.crossfeed)

private fun decodeStereo(json: JSONObject?, fallback: StereoState): StereoState =
    if (json == null) fallback else StereoState(
        balance = json.optFloat("balance", fallback.balance),
        mono = json.optBoolean("mono", fallback.mono),
        stereoWidth = json.optFloat("stereoWidth", fallback.stereoWidth),
        crossfeed = json.optFloat("crossfeed", fallback.crossfeed),
    )

private fun encodeReverb(value: ReverbState): JSONObject =
    JSONObject()
        .put("enabled", value.enabled)
        .put("mix", value.mix)
        .put("size", value.size)
        .put("preDelayMs", value.preDelayMs)
        .put("damp", value.damp)
        .put("decay", value.decay)

private fun decodeReverb(json: JSONObject?, fallback: ReverbState): ReverbState =
    if (json == null) fallback else ReverbState(
        enabled = json.optBoolean("enabled", fallback.enabled),
        mix = json.optFloat("mix", fallback.mix),
        size = json.optFloat("size", fallback.size),
        preDelayMs = json.optFloat("preDelayMs", fallback.preDelayMs),
        damp = json.optFloat("damp", fallback.damp),
        decay = json.optFloat("decay", fallback.decay),
    )

private fun encodeTempoPitch(value: TempoPitchState): JSONObject =
    JSONObject()
        .put("speed", value.speed)
        .put("tempo", value.tempo)
        .put("pitchSemitones", value.pitchSemitones)

private fun decodeTempoPitch(json: JSONObject?, fallback: TempoPitchState): TempoPitchState =
    if (json == null) fallback else TempoPitchState(
        speed = json.optFloat("speed", fallback.speed),
        tempo = json.optFloat("tempo", fallback.tempo),
        pitchSemitones = json.optFloat("pitchSemitones", fallback.pitchSemitones),
    )

private fun encodeCompressor(value: CompressorState): JSONObject =
    JSONObject()
        .put("enabled", value.enabled)
        .put("thresholdDb", value.thresholdDb)
        .put("ratio", value.ratio)
        .put("attackMs", value.attackMs)
        .put("releaseMs", value.releaseMs)
        .put("makeupDb", value.makeupDb)
        .put("mix", value.mix)

private fun decodeCompressor(json: JSONObject?, fallback: CompressorState): CompressorState =
    if (json == null) fallback else CompressorState(
        enabled = json.optBoolean("enabled", fallback.enabled),
        thresholdDb = json.optFloat("thresholdDb", fallback.thresholdDb),
        ratio = json.optFloat("ratio", fallback.ratio),
        attackMs = json.optFloat("attackMs", fallback.attackMs),
        releaseMs = json.optFloat("releaseMs", fallback.releaseMs),
        makeupDb = json.optFloat("makeupDb", fallback.makeupDb),
        mix = json.optFloat("mix", fallback.mix),
    )

private fun encodeGate(value: GateState): JSONObject =
    JSONObject()
        .put("enabled", value.enabled)
        .put("thresholdDb", value.thresholdDb)
        .put("attackMs", value.attackMs)
        .put("holdMs", value.holdMs)
        .put("releaseMs", value.releaseMs)

private fun decodeGate(json: JSONObject?, fallback: GateState): GateState =
    if (json == null) fallback else GateState(
        enabled = json.optBoolean("enabled", fallback.enabled),
        thresholdDb = json.optFloat("thresholdDb", fallback.thresholdDb),
        attackMs = json.optFloat("attackMs", fallback.attackMs),
        holdMs = json.optFloat("holdMs", fallback.holdMs),
        releaseMs = json.optFloat("releaseMs", fallback.releaseMs),
    )

private fun encodeDelay(value: DelayState): JSONObject =
    JSONObject()
        .put("enabled", value.enabled)
        .put("timeMs", value.timeMs)
        .put("feedback", value.feedback)
        .put("mix", value.mix)

private fun decodeDelay(json: JSONObject?, fallback: DelayState): DelayState =
    if (json == null) fallback else DelayState(
        enabled = json.optBoolean("enabled", fallback.enabled),
        timeMs = json.optFloat("timeMs", fallback.timeMs),
        feedback = json.optFloat("feedback", fallback.feedback),
        mix = json.optFloat("mix", fallback.mix),
    )

private fun encodeModulation(value: ModulationState): JSONObject =
    JSONObject()
        .put("chorusEnabled", value.chorusEnabled)
        .put("flangerEnabled", value.flangerEnabled)
        .put("phaserEnabled", value.phaserEnabled)
        .put("rateHz", value.rateHz)
        .put("depth", value.depth)
        .put("feedback", value.feedback)
        .put("mix", value.mix)

private fun decodeModulation(json: JSONObject?, fallback: ModulationState): ModulationState =
    if (json == null) fallback else ModulationState(
        chorusEnabled = json.optBoolean("chorusEnabled", fallback.chorusEnabled),
        flangerEnabled = json.optBoolean("flangerEnabled", fallback.flangerEnabled),
        phaserEnabled = json.optBoolean("phaserEnabled", fallback.phaserEnabled),
        rateHz = json.optFloat("rateHz", fallback.rateHz),
        depth = json.optFloat("depth", fallback.depth),
        feedback = json.optFloat("feedback", fallback.feedback),
        mix = json.optFloat("mix", fallback.mix),
    )

private fun JSONArray.toFloatList(): List<Float> =
    List(length()) { index -> optDouble(index, 0.0).toFloat() }

private fun JSONObject.optFloat(name: String, fallback: Float): Float =
    optDouble(name, fallback.toDouble()).toFloat()

private inline fun <reified T : Enum<T>> enumValue(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback
