@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsedeck.app.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.pulsedeck.app.settings.runtime.TruePeakDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.TruePeakMeasurementStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

class TruePeakAudioProcessor(
    private val onSnapshot: (TruePeakDiagnosticsSnapshot) -> Unit,
) : AudioProcessor {
    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var active = false
    private var samplePeak = 0f
    private var truePeak = 0f
    private var framesMeasured = 0L
    private var lastEmitFrames = 0L
    private var channelHistory = emptyArray<FloatArray>()

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputFormat = inputAudioFormat
        val supported = inputAudioFormat.channelCount in 1..2 &&
            inputAudioFormat.encoding in setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)
        active = supported
        resetMeasurement()
        if (!supported) {
            onSnapshot(
                TruePeakDiagnosticsSnapshot(
                    status = TruePeakMeasurementStatus.Unsupported,
                    sampleRateHz = inputAudioFormat.sampleRate.takeIf { it > 0 },
                    channelCount = inputAudioFormat.channelCount.takeIf { it > 0 },
                    reason = "True-peak meter supports decoded PCM16 or PCM float mono/stereo only.",
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        onSnapshot(
            TruePeakDiagnosticsSnapshot(
                status = TruePeakMeasurementStatus.Measuring,
                sampleRateHz = inputAudioFormat.sampleRate.takeIf { it > 0 },
                channelCount = inputAudioFormat.channelCount,
                updatedAtMillis = System.currentTimeMillis(),
                reason = "4x interpolated true-peak meter is measuring the supported decoded PCM path.",
            ),
        )
        return inputAudioFormat
    }

    override fun isActive(): Boolean = active

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes <= 0) return
        val inputSlice = inputBuffer.slice().order(ByteOrder.nativeOrder())
        inputSlice.limit(bytes)
        if (active) measure(inputSlice.duplicate().order(ByteOrder.nativeOrder()))
        val output = replaceOutputBuffer(bytes)
        output.put(inputSlice)
        output.flip()
        inputBuffer.position(inputBuffer.limit())
        maybeEmitSnapshot(force = false)
    }

    override fun queueEndOfStream() {
        inputEnded = true
        maybeEmitSnapshot(force = true)
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean =
        inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        active = false
        resetMeasurement()
        onSnapshot(TruePeakDiagnosticsSnapshot(updatedAtMillis = System.currentTimeMillis()))
    }

    private fun replaceOutputBuffer(bytes: Int): ByteBuffer {
        if (buffer.capacity() < bytes) {
            buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        }
        buffer.clear()
        outputBuffer = buffer
        return outputBuffer
    }

    private fun resetMeasurement() {
        samplePeak = 0f
        truePeak = 0f
        framesMeasured = 0L
        lastEmitFrames = 0L
        channelHistory = Array(inputFormat.channelCount.coerceIn(0, 2)) { FloatArray(3) }
    }

    private fun measure(buffer: ByteBuffer) {
        val channels = inputFormat.channelCount
        if (channels !in 1..2) return
        when (inputFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = channels * 2
                val frames = buffer.remaining() / frameBytes
                repeat(frames) {
                    repeat(channels) { channel ->
                        val sample = buffer.short.toFloat() / 32768f
                        measureSample(channel, sample)
                    }
                    framesMeasured += 1L
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = channels * 4
                val frames = buffer.remaining() / frameBytes
                repeat(frames) {
                    repeat(channels) { channel ->
                        measureSample(channel, buffer.float.coerceIn(-8f, 8f))
                    }
                    framesMeasured += 1L
                }
            }
        }
    }

    private fun measureSample(channel: Int, sample: Float) {
        val absSample = abs(sample)
        samplePeak = max(samplePeak, absSample)
        truePeak = max(truePeak, absSample)
        val history = channelHistory.getOrNull(channel) ?: return
        if (framesMeasured >= 3L) {
            for (step in 1 until TRUE_PEAK_OVERSAMPLE_FACTOR) {
                val t = step.toFloat() / TRUE_PEAK_OVERSAMPLE_FACTOR
                truePeak = max(truePeak, abs(catmullRom(history[0], history[1], history[2], sample, t)))
            }
        }
        history[0] = history[1]
        history[1] = history[2]
        history[2] = sample
    }

    private fun maybeEmitSnapshot(force: Boolean) {
        val sampleRate = inputFormat.sampleRate.takeIf { it > 0 } ?: 48_000
        val emitEveryFrames = sampleRate / 4
        if (!force && framesMeasured - lastEmitFrames < emitEveryFrames) return
        lastEmitFrames = framesMeasured
        onSnapshot(
            TruePeakDiagnosticsSnapshot(
                status = if (framesMeasured > 0L) TruePeakMeasurementStatus.Measured else TruePeakMeasurementStatus.Pending,
                samplePeakDbFs = linearPeakToDb(samplePeak),
                truePeakDbTp = linearPeakToDb(truePeak),
                sampleRateHz = inputFormat.sampleRate.takeIf { it > 0 },
                channelCount = inputFormat.channelCount.takeIf { it > 0 },
                framesMeasured = framesMeasured,
                oversampleFactor = TRUE_PEAK_OVERSAMPLE_FACTOR,
                updatedAtMillis = System.currentTimeMillis(),
                reason = "Measured with a pass-through 4x interpolated PCM true-peak meter.",
            ),
        )
    }
}

private const val TRUE_PEAK_OVERSAMPLE_FACTOR = 4

private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (
        (2f * p1) +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        )
}

private fun linearPeakToDb(value: Float): Float? =
    value.takeIf { it > 0f }?.let { 20f * log10(it.toDouble()).toFloat() }
