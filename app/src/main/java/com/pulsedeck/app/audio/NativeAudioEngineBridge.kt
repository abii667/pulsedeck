package com.pulsedeck.app.audio

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import java.nio.ByteBuffer

private const val PERFORMANCE_TAG = "PulseDeckPerf"

data class NativeStreamInfo(
    val raw: String,
) {
    val running: Boolean = raw.contains("running=true")
    val xRunCount: Int = raw.substringAfter("xRunCount=", "0").substringBefore(';').toIntOrNull() ?: 0
}

data class NativeMeters(
    val peakLeft: Float = 0f,
    val peakRight: Float = 0f,
    val rmsLeft: Float = 0f,
    val rmsRight: Float = 0f,
)

data class NativeSpectrum(
    val bins: List<Float> = emptyList(),
)

data class NativeAudioPresetSnapshot(
    val name: String,
    val masterGain: Float,
    val sourceFrequencyHz: Float,
    val sourceGain: Float,
    val eqGains: List<Float>,
)

enum class NativeAudioInitializationState {
    NotInitialized,
    Initializing,
    Ready,
    Failed,
}

object NativeAudioEngineBridge {
    private val initLock = Any()

    @Volatile
    private var loadState: NativeAudioInitializationState = NativeAudioInitializationState.NotInitialized

    @Volatile
    private var loadError: Throwable? = null

    val initializationState: NativeAudioInitializationState
        get() = loadState

    val isAvailable: Boolean
        get() = loadState == NativeAudioInitializationState.Ready

    val unavailableReason: String?
        get() = when (loadState) {
            NativeAudioInitializationState.NotInitialized -> "not initialized"
            NativeAudioInitializationState.Initializing -> "initializing"
            NativeAudioInitializationState.Ready -> null
            NativeAudioInitializationState.Failed -> loadError?.message ?: loadError?.javaClass?.simpleName
        }

    fun initializeBlocking(reason: String): NativeAudioInitializationState {
        val current = loadState
        if (current == NativeAudioInitializationState.Ready || current == NativeAudioInitializationState.Failed) {
            return current
        }
        synchronized(initLock) {
            val lockedState = loadState
            if (lockedState == NativeAudioInitializationState.Ready || lockedState == NativeAudioInitializationState.Failed) {
                return lockedState
            }
            loadState = NativeAudioInitializationState.Initializing
            val startedAtMillis = SystemClock.uptimeMillis()
            val error = runCatching {
                Trace.beginSection("PulseDeck:native_audio_load")
                System.loadLibrary("pulse_native_audio")
            }.exceptionOrNull().also {
                Trace.endSection()
            }
            loadError = error
            loadState = if (error == null) {
                NativeAudioInitializationState.Ready
            } else {
                NativeAudioInitializationState.Failed
            }
            Log.d(
                PERFORMANCE_TAG,
                "native_audio_load_ms=${SystemClock.uptimeMillis() - startedAtMillis} state=$loadState reason=$reason thread=${Thread.currentThread().name}",
            )
            return loadState
        }
    }

    fun createEngine(reason: String = "create_engine"): Long =
        if (initializeBlocking(reason) == NativeAudioInitializationState.Ready) {
            nativeCall(0L) { nativeCreateEngine() }
        } else {
            0L
        }

    fun engineVersion(): String =
        nativeCall("native unavailable") { nativeGetEngineVersion() }

    fun start(handle: Long): Boolean =
        handle != 0L && nativeCall(false) { nativeStart(handle) }

    fun stop(handle: Long) {
        if (handle != 0L) nativeCall(Unit) { nativeStop(handle) }
    }

    fun release(handle: Long) {
        if (handle != 0L) nativeCall(Unit) { nativeRelease(handle) }
    }

    fun streamInfo(handle: Long): NativeStreamInfo =
        NativeStreamInfo(if (handle == 0L) "native unavailable" else nativeCall("native unavailable") { nativeGetStreamInfo(handle) })

    fun graphDescription(handle: Long): String =
        if (handle == 0L) "native unavailable" else nativeCall("native unavailable") { nativeGetGraphDescription(handle) }

    fun setBufferMode(handle: Long, mode: Int) {
        if (handle != 0L) nativeCall(Unit) { nativeSetBufferMode(handle, mode) }
    }

    fun setEnabled(handle: Long, enabled: Boolean) {
        if (handle != 0L) nativeCall(Unit) { nativeSetEnabled(handle, enabled) }
    }

    fun setBypass(handle: Long, bypass: Boolean) {
        if (handle != 0L) nativeCall(Unit) { nativeSetBypass(handle, bypass) }
    }

    fun setMasterGain(handle: Long, value: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetMasterGain(handle, value) }
    }

    fun setSource(handle: Long, frequencyHz: Float, gain: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetSource(handle, frequencyHz, gain) }
    }

    fun setEqBand(handle: Long, index: Int, frequencyHz: Float, gainDb: Float, q: Float, type: Int, enabled: Boolean) {
        if (handle != 0L) nativeCall(Unit) { nativeSetEqBand(handle, index, frequencyHz, gainDb, q, type, enabled) }
    }

    fun setStereo(handle: Long, balance: Float, mono: Boolean, stereoWidth: Float, crossfeed: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetStereo(handle, balance, mono, stereoWidth, crossfeed) }
    }

    fun setLimiter(handle: Long, enabled: Boolean, ceilingDb: Float, releaseMs: Float, strength: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetLimiter(handle, enabled, ceilingDb, releaseMs, strength) }
    }

    fun setCompressor(handle: Long, enabled: Boolean, thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupDb: Float, mix: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetCompressor(handle, enabled, thresholdDb, ratio, attackMs, releaseMs, makeupDb, mix) }
    }

    fun setGate(handle: Long, enabled: Boolean, thresholdDb: Float, attackMs: Float, holdMs: Float, releaseMs: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetGate(handle, enabled, thresholdDb, attackMs, holdMs, releaseMs) }
    }

    fun setReverb(handle: Long, enabled: Boolean, mix: Float, size: Float, preDelayMs: Float, damp: Float, decay: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetReverb(handle, enabled, mix, size, preDelayMs, damp, decay) }
    }

    fun setDelay(handle: Long, enabled: Boolean, timeMs: Float, feedback: Float, mix: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetDelay(handle, enabled, timeMs, feedback, mix) }
    }

    fun setModulation(handle: Long, chorusEnabled: Boolean, flangerEnabled: Boolean, phaserEnabled: Boolean, rateHz: Float, depth: Float, feedback: Float, mix: Float) {
        if (handle != 0L) nativeCall(Unit) { nativeSetModulation(handle, chorusEnabled, flangerEnabled, phaserEnabled, rateHz, depth, feedback, mix) }
    }

    fun processPcm16(handle: Long, input: ByteBuffer, output: ByteBuffer, bytes: Int, sampleRate: Int, channels: Int): Int =
        if (handle == 0L) 0 else nativeCall(0) { nativeProcessPcm16(handle, input, output, bytes, sampleRate, channels) }

    fun latestMeters(handle: Long): NativeMeters {
        if (handle == 0L) return NativeMeters()
        val values = nativeCall(FloatArray(4)) { nativeGetLatestMeters(handle) }
        return NativeMeters(
            peakLeft = values.getOrElse(0) { 0f },
            peakRight = values.getOrElse(1) { 0f },
            rmsLeft = values.getOrElse(2) { 0f },
            rmsRight = values.getOrElse(3) { 0f },
        )
    }

    fun latestSpectrum(handle: Long): NativeSpectrum =
        NativeSpectrum(if (handle == 0L) emptyList() else nativeCall(FloatArray(0)) { nativeGetLatestSpectrum(handle) }.toList())

    fun exportPostGraphWav(handle: Long, path: String, durationMs: Int, sampleRate: Int, channels: Int): Boolean =
        handle != 0L && nativeCall(false) { nativeExportPostGraphWav(handle, path, durationMs, sampleRate, channels) }

    fun runSelfTest(handle: Long): Boolean =
        handle != 0L && nativeCall(false) { nativeRunSelfTest(handle) }

    private inline fun <T> nativeCall(fallback: T, block: () -> T): T =
        if (!isAvailable) fallback else runCatching(block).getOrElse { fallback }

    @JvmStatic external fun nativeGetEngineVersion(): String
    @JvmStatic external fun nativeCreateEngine(): Long
    @JvmStatic external fun nativeStart(handle: Long): Boolean
    @JvmStatic external fun nativeStop(handle: Long)
    @JvmStatic external fun nativeRelease(handle: Long)
    @JvmStatic external fun nativeGetStreamInfo(handle: Long): String
    @JvmStatic external fun nativeGetGraphDescription(handle: Long): String
    @JvmStatic external fun nativeSetBufferMode(handle: Long, mode: Int)
    @JvmStatic external fun nativeSetEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeSetBypass(handle: Long, bypass: Boolean)
    @JvmStatic external fun nativeSetMasterGain(handle: Long, value: Float)
    @JvmStatic external fun nativeSetSource(handle: Long, frequencyHz: Float, gain: Float)
    @JvmStatic external fun nativeSetEqBand(handle: Long, index: Int, frequencyHz: Float, gainDb: Float, q: Float, type: Int, enabled: Boolean)
    @JvmStatic external fun nativeSetStereo(handle: Long, balance: Float, mono: Boolean, stereoWidth: Float, crossfeed: Float)
    @JvmStatic external fun nativeSetLimiter(handle: Long, enabled: Boolean, ceilingDb: Float, releaseMs: Float, strength: Float)
    @JvmStatic external fun nativeSetCompressor(handle: Long, enabled: Boolean, thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupDb: Float, mix: Float)
    @JvmStatic external fun nativeSetGate(handle: Long, enabled: Boolean, thresholdDb: Float, attackMs: Float, holdMs: Float, releaseMs: Float)
    @JvmStatic external fun nativeSetReverb(handle: Long, enabled: Boolean, mix: Float, size: Float, preDelayMs: Float, damp: Float, decay: Float)
    @JvmStatic external fun nativeSetDelay(handle: Long, enabled: Boolean, timeMs: Float, feedback: Float, mix: Float)
    @JvmStatic external fun nativeSetModulation(handle: Long, chorusEnabled: Boolean, flangerEnabled: Boolean, phaserEnabled: Boolean, rateHz: Float, depth: Float, feedback: Float, mix: Float)
    @JvmStatic external fun nativeProcessPcm16(handle: Long, input: ByteBuffer, output: ByteBuffer, bytes: Int, sampleRate: Int, channels: Int): Int
    @JvmStatic external fun nativeGetLatestMeters(handle: Long): FloatArray
    @JvmStatic external fun nativeGetLatestSpectrum(handle: Long): FloatArray
    @JvmStatic external fun nativeExportPostGraphWav(handle: Long, path: String, durationMs: Int, sampleRate: Int, channels: Int): Boolean
    @JvmStatic external fun nativeRunSelfTest(handle: Long): Boolean
}
