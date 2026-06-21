package com.pulsedeck.app.settings.runtime

import com.pulsedeck.app.AudioFocusSettings
import com.pulsedeck.app.CrossfadeSettings
import com.pulsedeck.app.DeviceProfile
import com.pulsedeck.app.DirectVolumeControlSettings
import com.pulsedeck.app.EffectiveAudioProcessingPolicy
import com.pulsedeck.app.OutputBitDepth
import com.pulsedeck.app.OutputMode
import com.pulsedeck.app.OutputSampleRate
import com.pulsedeck.app.OutputSettings
import com.pulsedeck.app.ReplayGainSettings
import com.pulsedeck.app.ResamplerSettings
import com.pulsedeck.app.audio.NativeAudioEngineBridge
import com.pulsedeck.app.settings.model.AndroidAutoSettings
import com.pulsedeck.app.settings.model.DiagnosticsSettings
import com.pulsedeck.app.settings.model.HeadsetBluetoothSettings
import com.pulsedeck.app.settings.model.LockScreenSettings
import com.pulsedeck.app.settings.model.MediaAction
import com.pulsedeck.app.settings.model.MediaButtonSettings
import com.pulsedeck.app.settings.model.MiscSettings
import com.pulsedeck.app.settings.model.PulseSettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class OutputDeviceType(val label: String) {
    Speaker("Speaker"),
    WiredHeadsetAux("Wired Headset/AUX"),
    Bluetooth("Bluetooth"),
    UsbDac("USB DAC"),
    Other("Other Output Devices"),
    Chromecast("Chromecast"),
}

enum class OutputPluginType(val label: String) {
    Media3AudioTrack("Media3/AudioTrack"),
    AAudioNative("AAudio/native"),
    HiRes("Hi-Res attempt"),
    Chromecast("Chromecast"),
}

data class DeviceCapabilities(
    val supportsAaudio: Boolean = false,
    val supportsHiRes: Boolean = false,
    val supportsCast: Boolean = false,
    val supportsUsbDac: Boolean = false,
) {
    fun supports(route: OutputDeviceType, plugin: OutputPluginType): Boolean =
        when (plugin) {
            OutputPluginType.Media3AudioTrack -> true
            OutputPluginType.AAudioNative -> supportsAaudio &&
                route != OutputDeviceType.Chromecast &&
                (route != OutputDeviceType.UsbDac || supportsUsbDac)
            OutputPluginType.HiRes -> supportsHiRes &&
                route != OutputDeviceType.Chromecast &&
                (route != OutputDeviceType.UsbDac || supportsUsbDac)
            OutputPluginType.Chromecast -> supportsCast && route == OutputDeviceType.Chromecast
        }
}

data class OutputRouteStatus(
    val route: OutputDeviceType,
    val requestedPlugin: OutputPluginType,
    val actualPlugin: OutputPluginType,
    val available: Boolean,
    val reason: String? = null,
)

data class OutputDeviceDiagnosticsSnapshot(
    val requestedRoute: OutputDeviceType = OutputDeviceType.Speaker,
    val activeRoute: OutputDeviceType? = null,
    val preferredRoute: OutputDeviceType? = null,
    val preferredDeviceName: String? = null,
    val preferredDeviceApplied: Boolean = false,
    val routeVerified: Boolean = false,
    val usbDacDetected: Boolean = false,
    val supportedSampleRatesHz: List<Int> = emptyList(),
    val supportedEncodings: List<String> = emptyList(),
    val availableRoutes: List<OutputDeviceType> = emptyList(),
    val reason: String? = null,
)

data class AudioTrackDiagnosticsSnapshot(
    val configuredSampleRateHz: Int? = null,
    val encoding: String? = null,
    val channelCount: Int? = null,
    val channelConfig: Int? = null,
    val offload: Boolean = false,
    val offloadSleeping: Boolean = false,
    val bufferSizeBytes: Int? = null,
    val underrunCount: Int = 0,
    val updatedAtMillis: Long = 0L,
)

enum class ProOutputRuntimeEngine {
    Media3Default,
    Media3CustomOutput,
    OboeAaudio,
    AudioTrackDirect,
    Unavailable,
}

enum class ProOutputRuntimeStatus {
    Idle,
    Requested,
    Configured,
    Started,
    Active,
    Paused,
    Flushed,
    Stopped,
    Released,
    FallbackToMedia3,
    Failed,
}

data class ProOutputRuntimeSnapshot(
    val providerInstalled: Boolean = false,
    val enabledByUser: Boolean = false,
    val localOfflineEligible: Boolean = false,
    val sourceScope: String = "unknown",
    val engine: ProOutputRuntimeEngine = ProOutputRuntimeEngine.Media3Default,
    val status: ProOutputRuntimeStatus = ProOutputRuntimeStatus.Idle,
    val requestedSampleRateHz: Int? = null,
    val actualSampleRateHz: Int? = null,
    val requestedEncoding: String? = null,
    val actualEncoding: String? = null,
    val channelCount: Int? = null,
    val channelMask: Int? = null,
    val bufferSizeBytes: Int? = null,
    val underrunCount: Int = 0,
    val writeStallCount: Int = 0,
    val fallbackReason: String? = null,
    val updatedAtMillis: Long = 0L,
)

enum class TruePeakMeasurementStatus {
    Unsupported,
    Pending,
    Measuring,
    Measured,
}

data class TruePeakDiagnosticsSnapshot(
    val status: TruePeakMeasurementStatus = TruePeakMeasurementStatus.Unsupported,
    val samplePeakDbFs: Float? = null,
    val truePeakDbTp: Float? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val framesMeasured: Long = 0L,
    val oversampleFactor: Int = 4,
    val updatedAtMillis: Long = 0L,
    val reason: String? = null,
)

data class DecoderFormatDiagnosticsSnapshot(
    val containerMimeType: String? = null,
    val sampleMimeType: String? = null,
    val codecString: String? = null,
    val averageBitrateKbps: Int? = null,
    val peakBitrateKbps: Int? = null,
    val encodedSampleRateHz: Int? = null,
    val encodedChannelCount: Int? = null,
    val decoderName: String? = null,
    val decodedPcmEncoding: String? = null,
    val decodedSampleRateHz: Int? = null,
    val decodedChannelCount: Int? = null,
    val updatedAtMillis: Long = 0L,
)

data class OutputDiagnosticsSnapshot(
    val routeStatuses: List<OutputRouteStatus> = emptyList(),
    val decoderFormat: DecoderFormatDiagnosticsSnapshot = DecoderFormatDiagnosticsSnapshot(),
    val outputDevice: OutputDeviceDiagnosticsSnapshot = OutputDeviceDiagnosticsSnapshot(),
    val audioTrack: AudioTrackDiagnosticsSnapshot = AudioTrackDiagnosticsSnapshot(),
    val proOutput: ProOutputRuntimeSnapshot = ProOutputRuntimeSnapshot(),
    val truePeak: TruePeakDiagnosticsSnapshot = TruePeakDiagnosticsSnapshot(),
    val activeOutputMode: String = "Media3/AudioTrack",
    val bitPerfectAttemptEnabled: Boolean = false,
    val audioFocusRequestsFocus: Boolean = true,
    val handleNoisyOutput: Boolean = true,
    val transparentPathExpected: Boolean = true,
    val replayGainActive: Boolean = false,
    val replayGainAdjustmentDb: Float = 0f,
    val loudnessSource: String = "None",
    val loudnessConfidence: String = "Unknown",
    val nativeMedia3DspRequested: Boolean = false,
    val nativeMedia3DspActive: Boolean = false,
    val preventClippingActive: Boolean = false,
    val estimatedHeadroomDb: Float = 0f,
    val dspFallbackReason: String? = null,
    val updatedAtMillis: Long = 0L,
)

data class PlaybackRuntimeSettings(
    val replayGain: ReplayGainSettings = ReplayGainSettings(),
    val crossfade: CrossfadeSettings = CrossfadeSettings(),
    val audioFocus: AudioFocusSettings = AudioFocusSettings(),
    val resampler: ResamplerSettings = ResamplerSettings(),
    val dvc: DirectVolumeControlSettings = DirectVolumeControlSettings(),
    val output: OutputSettings = OutputSettings(),
    val headsetBluetooth: HeadsetBluetoothSettings = HeadsetBluetoothSettings(),
    val lockScreen: LockScreenSettings = LockScreenSettings(),
    val androidAuto: AndroidAutoSettings = AndroidAutoSettings(),
    val misc: MiscSettings = MiscSettings(),
    val diagnostics: DiagnosticsSettings = DiagnosticsSettings(),
    val routeAssignments: Map<OutputDeviceType, OutputPluginType> = defaultRouteAssignments(),
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilities(),
) {
    fun resolveRoute(route: OutputDeviceType): OutputRouteStatus {
        val requested = routeAssignments[route] ?: OutputPluginType.Media3AudioTrack
        val actual = if (deviceCapabilities.supports(route, requested)) {
            requested
        } else {
            if (route == OutputDeviceType.Chromecast && deviceCapabilities.supportsCast) {
                OutputPluginType.Chromecast
            } else {
                OutputPluginType.Media3AudioTrack
            }
        }
        return OutputRouteStatus(
            route = route,
            requestedPlugin = requested,
            actualPlugin = actual,
            available = actual == requested,
            reason = when {
                actual == requested -> null
                requested == OutputPluginType.Chromecast -> "Cast framework or receiver route is unavailable."
                requested == OutputPluginType.HiRes && route == OutputDeviceType.UsbDac -> "USB DAC hi-res output is gated until device support is verified."
                requested == OutputPluginType.HiRes -> "Hi-Res output is gated until device and firmware support is verified."
                requested == OutputPluginType.AAudioNative && route == OutputDeviceType.UsbDac -> "USB DAC native output is gated until route support is verified."
                requested == OutputPluginType.AAudioNative -> "AAudio/native output is not enabled for this route."
                else -> "Fell back to stable Media3/AudioTrack output."
            },
        )
    }
}

object PulseOutputDiagnosticsStore {
    private val mutableDiagnostics = MutableStateFlow(OutputDiagnosticsSnapshot())
    val diagnostics: StateFlow<OutputDiagnosticsSnapshot> = mutableDiagnostics

    fun update(next: OutputDiagnosticsSnapshot) {
        mutableDiagnostics.value = next
    }
}

object PulsePlaybackRuntimeStore {
    private val mutableRuntime = MutableStateFlow(PlaybackRuntimeSettings())
    val runtime: StateFlow<PlaybackRuntimeSettings> = mutableRuntime

    fun update(next: PlaybackRuntimeSettings) {
        mutableRuntime.value = next
    }
}

object PulseMediaSessionActions {
    const val Repeat = "pulsedeck.action.REPEAT"
    const val Shuffle = "pulsedeck.action.SHUFFLE"
    const val Close = "pulsedeck.action.CLOSE"
    const val Like = "pulsedeck.action.LIKE"
    const val Unlike = "pulsedeck.action.UNLIKE"
    const val Rating = "pulsedeck.action.RATING"
    const val SeekBack10 = "pulsedeck.action.SEEK_BACK_10"
    const val SeekForward10 = "pulsedeck.action.SEEK_FORWARD_10"
    const val PreviousCategory = "pulsedeck.action.PREVIOUS_CATEGORY"
    const val NextCategory = "pulsedeck.action.NEXT_CATEGORY"
}

fun PulseSettingsState.toPlaybackRuntimeSettings(
    capabilities: DeviceCapabilities = DeviceCapabilities(supportsAaudio = NativeAudioEngineBridge.isAvailable),
): PlaybackRuntimeSettings {
    val normalized = normalized()
    return PlaybackRuntimeSettings(
        replayGain = normalized.audio.replayGain,
        crossfade = normalized.audio.crossfade,
        audioFocus = normalized.audio.audioFocus,
        resampler = normalized.audio.resampler,
        dvc = normalized.audio.dvc,
        output = normalized.audio.output,
        headsetBluetooth = normalized.headsetBluetooth,
        lockScreen = normalized.lockScreen,
        androidAuto = normalized.misc.androidAuto,
        misc = normalized.misc,
        diagnostics = normalized.diagnostics,
        routeAssignments = routeAssignmentsFor(normalized.audio.output),
        deviceCapabilities = capabilities,
    )
}

fun mediaSessionCustomActions(actions: List<MediaAction>): List<String> =
    actions
        .filterNot { it == MediaAction.None }
        .mapNotNull(::mediaSessionCustomActionId)

fun MediaButtonSettings.asActionList(): List<MediaAction> =
    listOf(button1, button2, button3, button4, button5, button6)

fun mediaSessionCustomActionId(action: MediaAction): String? =
    when (action) {
        MediaAction.Repeat -> PulseMediaSessionActions.Repeat
        MediaAction.Shuffle -> PulseMediaSessionActions.Shuffle
        MediaAction.Close -> PulseMediaSessionActions.Close
        MediaAction.Like -> PulseMediaSessionActions.Like
        MediaAction.Unlike -> PulseMediaSessionActions.Unlike
        MediaAction.Rating -> PulseMediaSessionActions.Rating
        MediaAction.SeekBack10 -> PulseMediaSessionActions.SeekBack10
        MediaAction.SeekForward10 -> PulseMediaSessionActions.SeekForward10
        MediaAction.PreviousCategory -> PulseMediaSessionActions.PreviousCategory
        MediaAction.NextCategory -> PulseMediaSessionActions.NextCategory
        MediaAction.None -> null
    }

fun mediaActionForCustomCommand(commandId: String): MediaAction? =
    when (commandId) {
        PulseMediaSessionActions.Repeat -> MediaAction.Repeat
        PulseMediaSessionActions.Shuffle -> MediaAction.Shuffle
        PulseMediaSessionActions.Close -> MediaAction.Close
        PulseMediaSessionActions.Like -> MediaAction.Like
        PulseMediaSessionActions.Unlike -> MediaAction.Unlike
        PulseMediaSessionActions.Rating -> MediaAction.Rating
        PulseMediaSessionActions.SeekBack10 -> MediaAction.SeekBack10
        PulseMediaSessionActions.SeekForward10 -> MediaAction.SeekForward10
        PulseMediaSessionActions.PreviousCategory -> MediaAction.PreviousCategory
        PulseMediaSessionActions.NextCategory -> MediaAction.NextCategory
        else -> null
    }

data class BluetoothCommandLog(
    val commands: List<String> = emptyList(),
    val lastAcceptedAtMillis: Long = 0L,
)

object PulseBluetoothCommandLogStore {
    private val mutableLog = MutableStateFlow(BluetoothCommandLog())
    val log: StateFlow<BluetoothCommandLog> = mutableLog

    fun record(command: String, nowMillis: Long = System.currentTimeMillis()) {
        val current = mutableLog.value
        mutableLog.value = current.copy(
            commands = (current.commands + "${nowMillis}: $command").takeLast(25),
            lastAcceptedAtMillis = nowMillis,
        )
    }
}

class BluetoothCommandDebouncer {
    private var lastCommand: String? = null
    private var lastAcceptedAtMillis: Long = 0L

    fun shouldAccept(command: String, ignoreWindowSeconds: Int, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val windowMillis = ignoreWindowSeconds.coerceAtLeast(0) * 1000L
        val duplicateInsideWindow = command == lastCommand && windowMillis > 0L && nowMillis - lastAcceptedAtMillis < windowMillis
        if (duplicateInsideWindow) return false
        lastCommand = command
        lastAcceptedAtMillis = nowMillis
        return true
    }
}

fun PlaybackRuntimeSettings.outputDiagnosticsSnapshot(
    nowMillis: Long = System.currentTimeMillis(),
    audioPolicy: EffectiveAudioProcessingPolicy? = null,
    outputDevice: OutputDeviceDiagnosticsSnapshot = OutputDeviceDiagnosticsSnapshot(requestedRoute = outputDeviceTypeForProfile(output.deviceProfile)),
    audioTrack: AudioTrackDiagnosticsSnapshot = AudioTrackDiagnosticsSnapshot(),
    proOutput: ProOutputRuntimeSnapshot = ProOutputRuntimeSnapshot(),
    truePeak: TruePeakDiagnosticsSnapshot = TruePeakDiagnosticsSnapshot(),
): OutputDiagnosticsSnapshot =
    OutputDiagnosticsSnapshot(
        routeStatuses = OutputDeviceType.entries.map(::resolveRoute),
        outputDevice = outputDevice,
        audioTrack = audioTrack,
        proOutput = proOutput,
        truePeak = truePeak,
        activeOutputMode = output.mode.label,
        bitPerfectAttemptEnabled = output.bitPerfectAttemptEnabled,
        audioFocusRequestsFocus = audioFocus.requestOnPlay,
        handleNoisyOutput = headsetBluetooth.pauseOnHeadsetDisconnect,
        transparentPathExpected = audioPolicy?.transparentPathExpected ?: true,
        replayGainActive = audioPolicy?.replayGainActive ?: false,
        replayGainAdjustmentDb = audioPolicy?.replayGainAdjustmentDb ?: 0f,
        loudnessSource = audioPolicy?.loudnessSource?.name ?: "None",
        loudnessConfidence = audioPolicy?.loudnessConfidence?.name ?: "Unknown",
        nativeMedia3DspRequested = audioPolicy?.nativeMedia3DspRequested ?: false,
        nativeMedia3DspActive = audioPolicy?.nativeMedia3DspActive ?: false,
        preventClippingActive = audioPolicy?.preventClippingActive ?: false,
        estimatedHeadroomDb = audioPolicy?.estimatedHeadroomDb ?: 0f,
        dspFallbackReason = audioPolicy?.fallbackReason,
        updatedAtMillis = nowMillis,
    )

fun defaultRouteAssignments(): Map<OutputDeviceType, OutputPluginType> =
    mapOf(
        OutputDeviceType.Speaker to OutputPluginType.Media3AudioTrack,
        OutputDeviceType.WiredHeadsetAux to OutputPluginType.Media3AudioTrack,
        OutputDeviceType.Bluetooth to OutputPluginType.Media3AudioTrack,
        OutputDeviceType.UsbDac to OutputPluginType.Media3AudioTrack,
        OutputDeviceType.Other to OutputPluginType.Media3AudioTrack,
        OutputDeviceType.Chromecast to OutputPluginType.Chromecast,
    )

fun routeAssignmentsFor(output: OutputSettings): Map<OutputDeviceType, OutputPluginType> {
    val plugin = when (output.mode) {
        OutputMode.NativeLowLatency -> OutputPluginType.AAudioNative
        OutputMode.HiRes -> OutputPluginType.HiRes
        OutputMode.Auto,
        OutputMode.AudioTrack -> OutputPluginType.Media3AudioTrack
    }
    return mapOf(
        OutputDeviceType.Speaker to plugin,
        OutputDeviceType.WiredHeadsetAux to plugin,
        OutputDeviceType.Bluetooth to plugin,
        OutputDeviceType.UsbDac to plugin,
        OutputDeviceType.Other to plugin,
        OutputDeviceType.Chromecast to OutputPluginType.Chromecast,
    )
}

fun outputDeviceTypeForProfile(profile: DeviceProfile): OutputDeviceType =
    when (profile) {
        DeviceProfile.Speaker -> OutputDeviceType.Speaker
        DeviceProfile.Wired -> OutputDeviceType.WiredHeadsetAux
        DeviceProfile.Bluetooth,
        DeviceProfile.Car -> OutputDeviceType.Bluetooth
        DeviceProfile.UsbDac -> OutputDeviceType.UsbDac
    }

fun OutputSampleRate.toSampleRateHzOrNull(): Int? =
    when (this) {
        OutputSampleRate.Auto -> null
        OutputSampleRate.Hz44100 -> 44_100
        OutputSampleRate.Hz48000 -> 48_000
        OutputSampleRate.Hz96000 -> 96_000
        OutputSampleRate.Hz192000 -> 192_000
    }

fun OutputBitDepth.isHiResDepth(): Boolean =
    this == OutputBitDepth.Bit24 || this == OutputBitDepth.Float32
