@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsedeck.app.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeDspAudioProcessor(
    private val controller: NativeAudioEngineController,
) : AudioProcessor {
    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var active = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            inputFormat = inputAudioFormat
            active = false
            controller.updateMedia3DspFormatSupport(
                supported = false,
                fallbackReason = nativeDspFallbackReason(inputAudioFormat),
                decodedPcmFormat = inputAudioFormat.toDecodedPcmFormat(),
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inputFormat = inputAudioFormat
        active = true
        controller.updateMedia3DspFormatSupport(
            supported = true,
            fallbackReason = null,
            decodedPcmFormat = inputAudioFormat.toDecodedPcmFormat(),
        )
        return inputAudioFormat
    }

    override fun isActive(): Boolean =
        active && controller.media3DspActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes <= 0) return
        val output = replaceOutputBuffer(bytes)
        val inputSlice = directInputSlice(inputBuffer, bytes)
        val written = if (isActive()) {
            NativeAudioEngineBridge.processPcm16(
                handle = controller.nativeHandle,
                input = inputSlice,
                output = output,
                bytes = bytes,
                sampleRate = inputFormat.sampleRate,
                channels = inputFormat.channelCount,
            )
        } else {
            0
        }
        if (written > 0) {
            output.limit(written)
            output.position(0)
        } else {
            output.clear()
            output.put(inputSlice)
            output.flip()
        }
        inputBuffer.position(inputBuffer.limit())
    }

    override fun queueEndOfStream() {
        inputEnded = true
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
        buffer = AudioProcessor.EMPTY_BUFFER
        active = false
        controller.updateMedia3DspFormatSupport(true, null)
    }

    private fun replaceOutputBuffer(bytes: Int): ByteBuffer {
        if (buffer.capacity() < bytes) {
            buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        }
        buffer.clear()
        outputBuffer = buffer
        return outputBuffer
    }

    private fun directInputSlice(inputBuffer: ByteBuffer, bytes: Int): ByteBuffer {
        val duplicate = inputBuffer.slice().order(ByteOrder.nativeOrder())
        duplicate.limit(bytes)
        if (duplicate.isDirect) return duplicate
        val direct = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        direct.put(duplicate)
        direct.flip()
        return direct
    }

    private fun nativeDspFallbackReason(format: AudioProcessor.AudioFormat): String =
        when {
            format.encoding != C.ENCODING_PCM_16BIT ->
                "Native Media3 DSP supports PCM16 only; decoded format is ${format.encoding.encodingLabel()}."
            format.channelCount !in 1..2 ->
                "Native Media3 DSP supports mono/stereo only; decoded channel count is ${format.channelCount}."
            else ->
                "Native Media3 DSP does not support the decoded PCM format."
        }

    private fun AudioProcessor.AudioFormat.toDecodedPcmFormat(): NativeDecodedPcmFormat =
        NativeDecodedPcmFormat(
            encoding = encoding.encodingLabel(),
            sampleRateHz = sampleRate.takeIf { it > 0 },
            channelCount = channelCount.takeIf { it > 0 },
        )

    private fun Int.encodingLabel(): String =
        when (this) {
            C.ENCODING_PCM_16BIT -> "PCM16"
            C.ENCODING_PCM_24BIT -> "PCM24"
            C.ENCODING_PCM_32BIT -> "PCM32"
            C.ENCODING_PCM_FLOAT -> "PCM float"
            else -> "encoding $this"
        }
}
