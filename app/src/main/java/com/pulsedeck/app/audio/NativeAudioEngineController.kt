package com.pulsedeck.app.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.pulsedeck.app.AudioEngineState
import com.pulsedeck.app.BufferMode
import com.pulsedeck.app.EffectiveAudioProcessingPolicy
import com.pulsedeck.app.OutputMode
import com.pulsedeck.app.ParametricFilterType
import com.pulsedeck.app.audioBandGainDb
import com.pulsedeck.app.audioOutputGain
import com.pulsedeck.app.effectiveGraphicEqGainRange
import com.pulsedeck.app.effectiveProcessingPolicy
import com.pulsedeck.app.isActiveFilter
import com.pulsedeck.app.normalized
import java.io.Closeable
import java.io.File

private const val NativeParametricEqSlots = 4

internal data class NativeAudioActivation(
    val engineEnabled: Boolean,
    val media3DspRequested: Boolean,
    val media3DspActive: Boolean,
    val lowLatencyToneActive: Boolean,
    val fallbackReason: String?,
)

data class NativeDecodedPcmFormat(
    val encoding: String? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
)

class NativeAudioEngineController(
    context: Context? = null,
) : Closeable {
    private val appContext = context?.applicationContext
    private val audioManager = appContext?.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private val engineLock = Any()
    private var released = false
    @Volatile
    private var handle: Long = 0L
    @Volatile
    private var latestState: AudioEngineState = AudioEngineState()
    @Volatile
    private var media3DspFormatSupported: Boolean = true
    @Volatile
    private var media3DspFormatFallbackReason: String? = null
    @Volatile
    private var media3DecodedPcmFormat: NativeDecodedPcmFormat = NativeDecodedPcmFormat()

    val initializationState: NativeAudioInitializationState
        get() = NativeAudioEngineBridge.initializationState

    val isAvailable: Boolean
        get() = handle != 0L && NativeAudioEngineBridge.isAvailable

    val nativeHandle: Long
        get() = if (released) 0L else handle

    val media3DspActive: Boolean
        get() = currentActivation().media3DspActive

    val dspFallbackReason: String?
        get() = currentActivation().fallbackReason

    val decodedPcmFormat: NativeDecodedPcmFormat
        get() = media3DecodedPcmFormat

    fun effectivePolicy(): EffectiveAudioProcessingPolicy =
        latestState.effectiveProcessingPolicy(
            nativeAvailable = isAvailable,
            nativeMedia3DspFormatSupported = media3DspFormatSupported,
            nativeMedia3DspFallbackReason = media3DspFormatFallbackReason,
        )

    fun updateMedia3DspFormatSupport(
        supported: Boolean,
        fallbackReason: String?,
        decodedPcmFormat: NativeDecodedPcmFormat = NativeDecodedPcmFormat(),
    ) {
        media3DspFormatSupported = supported
        media3DspFormatFallbackReason = fallbackReason
        media3DecodedPcmFormat = decodedPcmFormat
    }

    fun start(): Boolean {
        if (!ensureEngine("native_start")) return false
        requestAudioFocus()
        return NativeAudioEngineBridge.start(nativeHandle)
    }

    fun stop() {
        NativeAudioEngineBridge.stop(nativeHandle)
        abandonAudioFocus()
    }

    fun streamInfo(): NativeStreamInfo =
        NativeAudioEngineBridge.streamInfo(nativeHandle)

    fun graphDescription(): String =
        NativeAudioEngineBridge.graphDescription(nativeHandle)

    fun latestMeters(): NativeMeters =
        NativeAudioEngineBridge.latestMeters(nativeHandle)

    fun latestSpectrum(): NativeSpectrum =
        NativeAudioEngineBridge.latestSpectrum(nativeHandle)

    fun setMasterGain(value: Float) {
        val next = latestState.copy(native = latestState.native.copy(masterGain = value))
        setAudioState(next)
    }

    fun setAudioState(state: AudioEngineState) {
        latestState = state.normalizedForNative()
        if (!isAvailable) return
        applyLatestStateToNative()
    }

    fun initializeForState(state: AudioEngineState, reason: String): Boolean {
        latestState = state.normalizedForNative()
        if (!nativeAudioInitializationNeeded(latestState)) return false
        if (!ensureEngine(reason)) return false
        applyLatestStateToNative()
        return isAvailable
    }

    private fun applyLatestStateToNative() {
        val nativeHandle = nativeHandle
        if (nativeHandle == 0L) return
        val native = latestState.native
        val activation = currentActivation()
        NativeAudioEngineBridge.setEnabled(nativeHandle, activation.engineEnabled)
        NativeAudioEngineBridge.setBypass(nativeHandle, latestState.bypass || latestState.advancedTweaks.safeMode)
        NativeAudioEngineBridge.setBufferMode(nativeHandle, latestState.output.bufferMode.nativeOrdinal())
        NativeAudioEngineBridge.setMasterGain(nativeHandle, audioOutputGain(latestState, native.masterGain, maxExternalVolume = 1.5f))
        NativeAudioEngineBridge.setSource(nativeHandle, native.sourceFrequencyHz, native.sourceGain)
        val graphicEqEnabled = activation.media3DspActive && (latestState.graphicEqStageActive || latestState.toneControlsActive)
        val graphicEqGainRange = latestState.effectiveGraphicEqGainRange(activation.media3DspActive)
        latestState.eqBands.forEachIndexed { index, band ->
            NativeAudioEngineBridge.setEqBand(
                handle = nativeHandle,
                index = index,
                frequencyHz = band.frequencyHz,
                gainDb = audioBandGainDb(band.frequencyHz, latestState, graphicEqGainRange),
                q = 1f,
                type = ParametricFilterType.Peak.ordinal,
                enabled = graphicEqEnabled,
            )
        }
        repeat(NativeParametricEqSlots) { offset ->
            val band = latestState.parametricEq.getOrNull(offset)
            NativeAudioEngineBridge.setEqBand(
                handle = nativeHandle,
                index = latestState.eqBands.size + offset,
                frequencyHz = band?.frequencyHz ?: 1000f,
                gainDb = band?.gainDb ?: 0f,
                q = band?.q ?: 1f,
                type = band?.type?.ordinal ?: ParametricFilterType.Peak.ordinal,
                enabled = activation.media3DspActive && band?.isActiveFilter() == true,
            )
        }
        latestState.stereo.let {
            NativeAudioEngineBridge.setStereo(nativeHandle, it.balance, it.mono, it.stereoWidth, it.crossfeed)
        }
        latestState.limiter.let {
            NativeAudioEngineBridge.setLimiter(nativeHandle, activation.media3DspActive && it.enabled && latestState.clippingRisk, it.ceilingDb, it.releaseMs, it.strength)
        }
        latestState.compressor.let {
            NativeAudioEngineBridge.setCompressor(nativeHandle, activation.media3DspActive && latestState.compressorStageActive, it.thresholdDb, it.ratio, it.attackMs, it.releaseMs, it.makeupDb, it.mix)
        }
        latestState.gate.let {
            NativeAudioEngineBridge.setGate(nativeHandle, activation.media3DspActive && latestState.gateStageActive, it.thresholdDb, it.attackMs, it.holdMs, it.releaseMs)
        }
        latestState.reverb.let {
            NativeAudioEngineBridge.setReverb(nativeHandle, activation.media3DspActive && latestState.reverbStageActive, it.mix, it.size, it.preDelayMs, it.damp, it.decay)
        }
        latestState.delay.let {
            NativeAudioEngineBridge.setDelay(nativeHandle, activation.media3DspActive && latestState.delayStageActive, it.timeMs, it.feedback, it.mix)
        }
        latestState.modulation.let {
            NativeAudioEngineBridge.setModulation(
                nativeHandle,
                activation.media3DspActive && it.chorusEnabled && latestState.modulationStageActive,
                activation.media3DspActive && it.flangerEnabled && latestState.modulationStageActive,
                activation.media3DspActive && it.phaserEnabled && latestState.modulationStageActive,
                it.rateHz,
                it.depth,
                it.feedback,
                it.mix,
            )
        }
        if (activation.lowLatencyToneActive) {
            start()
        } else {
            NativeAudioEngineBridge.stop(nativeHandle)
        }
    }

    fun exportPostGraphWav(file: File, durationMs: Int = latestState.native.exportDurationMs): Boolean =
        NativeAudioEngineBridge.exportPostGraphWav(
            handle = nativeHandle,
            path = file.absolutePath,
            durationMs = durationMs.coerceIn(250, 60_000),
            sampleRate = 48_000,
            channels = 2,
        )

    fun deviceInfo(): List<String> {
        val manager = audioManager ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { device ->
                val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.productName?.toString().orEmpty() else ""
                "${device.type}:$name"
            }
        } else {
            emptyList()
        }
    }

    fun selfTest(): Boolean =
        NativeAudioEngineBridge.runSelfTest(nativeHandle)

    override fun close() {
        if (released) return
        released = true
        abandonAudioFocus()
        val current = handle
        handle = 0L
        NativeAudioEngineBridge.release(current)
    }

    private fun ensureEngine(reason: String): Boolean {
        if (released) return false
        if (handle != 0L && NativeAudioEngineBridge.isAvailable) return true
        synchronized(engineLock) {
            if (released) return false
            if (handle != 0L && NativeAudioEngineBridge.isAvailable) return true
            handle = NativeAudioEngineBridge.createEngine(reason)
            return handle != 0L
        }
    }

    private fun requestAudioFocus() {
        if (!latestState.audioFocus.requestOnPlay) return
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
                }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(manager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }

    private fun currentActivation(): NativeAudioActivation =
        nativeAudioActivationFor(
            state = latestState,
            nativeAvailable = isAvailable,
            nativeMedia3DspFormatSupported = media3DspFormatSupported,
            nativeMedia3DspFallbackReason = media3DspFormatFallbackReason,
        )
}

fun AudioEngineState.normalizedForNative(): AudioEngineState = normalized()

internal fun nativeAudioActivationFor(
    state: AudioEngineState,
    nativeAvailable: Boolean,
    nativeMedia3DspFormatSupported: Boolean = true,
    nativeMedia3DspFallbackReason: String? = null,
): NativeAudioActivation {
    val normalized = state.normalizedForNative()
    val policy = normalized.effectiveProcessingPolicy(
        nativeAvailable = nativeAvailable,
        nativeMedia3DspFormatSupported = nativeMedia3DspFormatSupported,
        nativeMedia3DspFallbackReason = nativeMedia3DspFallbackReason,
    )
    val nativeRequested = normalized.native.enabled || policy.nativeMedia3DspRequested
    val engineEnabled = nativeAvailable &&
        nativeRequested &&
        policy.processingActive
    return NativeAudioActivation(
        engineEnabled = engineEnabled,
        media3DspRequested = policy.nativeMedia3DspRequested,
        media3DspActive = engineEnabled && policy.nativeMedia3DspActive,
        lowLatencyToneActive = engineEnabled &&
            !policy.nativeMedia3DspRequested &&
            normalized.output.mode == OutputMode.NativeLowLatency &&
            normalized.native.lowLatencyToneEnabled,
        fallbackReason = policy.fallbackReason,
    )
}

internal fun nativeAudioInitializationNeeded(state: AudioEngineState): Boolean =
    nativeAudioActivationFor(state, nativeAvailable = true).engineEnabled

private fun BufferMode.nativeOrdinal(): Int =
    when (this) {
        BufferMode.LowLatency -> 0
        BufferMode.Balanced -> 1
        BufferMode.Stable -> 2
    }
