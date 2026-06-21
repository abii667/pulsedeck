@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsedeck.app

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeEngine
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeSnapshot
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeStatus
import com.pulsedeck.app.settings.runtime.audioEncodingLabel
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.max

private const val PRO_OUTPUT_TAG = "PulseDeckProOutput"
private const val HI_RES_TAG = "PulseDeckHiRes"

internal data class ProOutputCurrentMedia(
    val scope: ProOutputSourceScope = ProOutputSourceScope.Unknown,
    val scheme: String? = null,
    val qualityHint: String? = null,
)

internal class PulseProAudioOutputProvider(
    context: android.content.Context,
    private val outputSettings: () -> OutputSettings,
    private val currentMedia: () -> ProOutputCurrentMedia,
    private val onSnapshot: (ProOutputRuntimeSnapshot) -> Unit,
    private val logger: (String, String) -> Unit,
) : ForwardingAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(context).build()) {
    @Volatile
    private var latest = ProOutputRuntimeSnapshot(providerInstalled = true)

    override fun getFormatSupport(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.FormatSupport {
        val support = super.getFormatSupport(formatConfig)
        val attempt = attemptState(formatConfig.format, null)
        publish(
            snapshot = attempt.snapshot(
                status = if (attempt.enabledByUser) ProOutputRuntimeStatus.Requested else ProOutputRuntimeStatus.Idle,
                requestedSampleRateHz = formatConfig.format.sampleRateHzOrNull(),
                requestedEncoding = formatConfig.format.pcmEncoding.encodingLabelOrNull(),
                channelCount = formatConfig.format.channelCount.takeIf { it > 0 },
                fallbackReason = attempt.fallbackReason,
            ),
            tag = HI_RES_TAG,
            message = "format_support scope=${attempt.media.scope.wireValue} enabled=${attempt.enabledByUser} eligible=${attempt.eligible} sample_rate=${formatConfig.format.sampleRateHzOrNull() ?: "unknown"} encoding=${formatConfig.format.pcmEncoding.encodingLabelOrNull() ?: "unknown"}",
        )
        return support
    }

    override fun getOutputConfig(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.OutputConfig {
        val outputConfig = super.getOutputConfig(formatConfig)
        val attempt = attemptState(formatConfig.format, outputConfig)
        publish(
            snapshot = attempt.snapshot(
                status = if (attempt.eligible) ProOutputRuntimeStatus.Configured else if (attempt.enabledByUser) ProOutputRuntimeStatus.FallbackToMedia3 else ProOutputRuntimeStatus.Idle,
                requestedSampleRateHz = formatConfig.format.sampleRateHzOrNull(),
                actualSampleRateHz = outputConfig.sampleRate.takeIf { it > 0 },
                requestedEncoding = formatConfig.format.pcmEncoding.encodingLabelOrNull(),
                actualEncoding = outputConfig.encoding.encodingLabelOrNull(),
                channelCount = outputConfig.channelMask.channelCountFromMask()
                    ?: formatConfig.format.channelCount.takeIf { it > 0 },
                channelMask = outputConfig.channelMask.takeIf { it > 0 },
                bufferSizeBytes = outputConfig.bufferSize.takeIf { it > 0 },
                fallbackReason = attempt.fallbackReason,
            ),
            tag = PRO_OUTPUT_TAG,
            message = "output_config scope=${attempt.media.scope.wireValue} engine=${attempt.engine.name} status=${if (attempt.eligible) "configured" else "fallback"} requested=${formatConfig.format.sampleRateHzOrNull() ?: "unknown"} actual=${outputConfig.sampleRate.takeIf { it > 0 } ?: "unknown"} encoding=${outputConfig.encoding.encodingLabelOrNull() ?: "unknown"}",
        )
        return outputConfig
    }

    override fun getAudioOutput(outputConfig: AudioOutputProvider.OutputConfig): AudioOutput {
        val delegate = super.getAudioOutput(outputConfig)
        val snapshot = latest.copy(
            status = if (latest.localOfflineEligible && latest.enabledByUser) ProOutputRuntimeStatus.Configured else latest.status,
            actualSampleRateHz = outputConfig.sampleRate.takeIf { it > 0 } ?: latest.actualSampleRateHz,
            actualEncoding = outputConfig.encoding.encodingLabelOrNull() ?: latest.actualEncoding,
            channelCount = outputConfig.channelMask.channelCountFromMask() ?: latest.channelCount,
            channelMask = outputConfig.channelMask.takeIf { it > 0 } ?: latest.channelMask,
            bufferSizeBytes = outputConfig.bufferSize.takeIf { it > 0 } ?: latest.bufferSizeBytes,
            updatedAtMillis = System.currentTimeMillis(),
        )
        publish(snapshot, PRO_OUTPUT_TAG, "output_open engine=${snapshot.engine.name} status=${snapshot.status.name} actual=${snapshot.actualSampleRateHz ?: "unknown"} encoding=${snapshot.actualEncoding ?: "unknown"}")
        return PulseInspectingAudioOutput(delegate, this)
    }

    fun recordUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        val next = latest.copy(
            underrunCount = latest.underrunCount + 1,
            updatedAtMillis = System.currentTimeMillis(),
        )
        publish(
            snapshot = next,
            tag = PRO_OUTPUT_TAG,
            message = "underrun count=${next.underrunCount} buffer_bytes=$bufferSize buffer_ms=$bufferSizeMs elapsed_ms=$elapsedSinceLastFeedMs",
        )
    }

    fun recordTimestampDiscontinuityFallback() {
        val next = latest.copy(
            status = ProOutputRuntimeStatus.FallbackToMedia3,
            engine = ProOutputRuntimeEngine.Media3Default,
            fallbackReason = "High-precision output was disabled for this track because the audio sink reported timestamp discontinuities.",
            updatedAtMillis = System.currentTimeMillis(),
        )
        publish(
            snapshot = next,
            tag = PRO_OUTPUT_TAG,
            message = "pro_output_fallback reason=audio_sink_timestamp_discontinuity",
        )
    }

    internal fun recordLifecycle(status: ProOutputRuntimeStatus, output: AudioOutput? = null) {
        val actualRate = output?.sampleRate?.takeIf { it > 0 }
        val next = latest.copy(
            status = status.toActiveIfQualified(latest, actualRate),
            actualSampleRateHz = actualRate ?: latest.actualSampleRateHz,
            bufferSizeBytes = output?.bufferSizeInFrames?.takeIf { it > 0L }?.let { frames ->
                val channels = max(1, latest.channelCount ?: 2)
                (frames * channels * 4L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            } ?: latest.bufferSizeBytes,
            updatedAtMillis = System.currentTimeMillis(),
        )
        publish(next, PRO_OUTPUT_TAG, "output_${status.name.lowercase(Locale.US)} engine=${next.engine.name} status=${next.status.name} actual=${next.actualSampleRateHz ?: "unknown"} underruns=${next.underrunCount}")
    }

    internal fun recordWriteResult(accepted: Boolean, stalled: Boolean) {
        if (accepted) return
        if (!stalled) return
        if (latest.status != ProOutputRuntimeStatus.Started && latest.status != ProOutputRuntimeStatus.Active) return
        val next = latest.copy(
            writeStallCount = latest.writeStallCount + 1,
            updatedAtMillis = System.currentTimeMillis(),
        )
        if (next.writeStallCount == 1 || next.writeStallCount % 100 == 0) {
            publish(next, PRO_OUTPUT_TAG, "write_backpressure count=${next.writeStallCount}")
        } else {
            latest = next
            onSnapshot(next)
        }
    }

    private fun attemptState(format: Format, outputConfig: AudioOutputProvider.OutputConfig?): AttemptState {
        val output = outputSettings()
        val media = currentMedia()
        val enabled = output.mode == OutputMode.HiRes ||
            output.hiResEnabled ||
            output.bitPerfectAttemptEnabled ||
            output.deviceProfile == DeviceProfile.UsbDac
        val bluetooth = output.deviceProfile == DeviceProfile.Bluetooth || output.deviceProfile == DeviceProfile.Car
        val formatHiRes = format.sampleRateHzOrNull()?.let { it >= 88_200 } == true ||
            format.pcmEncoding.isHighPrecisionEncoding() ||
            outputConfig?.sampleRate?.let { it >= 88_200 } == true ||
            outputConfig?.encoding?.isHighPrecisionEncoding() == true
        val local = media.scope == ProOutputSourceScope.Local
        val bitPerfect = output.bitPerfectAttemptEnabled
        val eligible = enabled && local && !bluetooth && (bitPerfect || formatHiRes)
        val fallback = when {
            !enabled -> null
            !local -> "Pro output is local/offline only; current source scope is ${media.scope.wireValue}."
            bluetooth -> "Bluetooth routes are excluded from pro-output active claims."
            !formatHiRes && !bitPerfect -> "Source/output format is not high-resolution enough for the pro-output prototype."
            else -> null
        }
        return AttemptState(
            enabledByUser = enabled,
            eligible = eligible,
            media = media,
            engine = if (eligible) ProOutputRuntimeEngine.Media3CustomOutput else ProOutputRuntimeEngine.Media3Default,
            fallbackReason = fallback,
        )
    }

    private fun publish(snapshot: ProOutputRuntimeSnapshot, tag: String, message: String) {
        latest = snapshot
        onSnapshot(snapshot)
        logger(tag, message)
    }

    private data class AttemptState(
        val enabledByUser: Boolean,
        val eligible: Boolean,
        val media: ProOutputCurrentMedia,
        val engine: ProOutputRuntimeEngine,
        val fallbackReason: String?,
    ) {
        fun snapshot(
            status: ProOutputRuntimeStatus,
            requestedSampleRateHz: Int? = null,
            actualSampleRateHz: Int? = null,
            requestedEncoding: String? = null,
            actualEncoding: String? = null,
            channelCount: Int? = null,
            channelMask: Int? = null,
            bufferSizeBytes: Int? = null,
            fallbackReason: String? = this.fallbackReason,
        ): ProOutputRuntimeSnapshot =
            ProOutputRuntimeSnapshot(
                providerInstalled = true,
                enabledByUser = enabledByUser,
                localOfflineEligible = eligible,
                sourceScope = media.scope.wireValue,
                engine = engine,
                status = status,
                requestedSampleRateHz = requestedSampleRateHz,
                actualSampleRateHz = actualSampleRateHz,
                requestedEncoding = requestedEncoding,
                actualEncoding = actualEncoding,
                channelCount = channelCount,
                channelMask = channelMask,
                bufferSizeBytes = bufferSizeBytes,
                fallbackReason = fallbackReason,
                updatedAtMillis = System.currentTimeMillis(),
            )
    }
}

private class PulseInspectingAudioOutput(
    private val delegate: AudioOutput,
    private val owner: PulseProAudioOutputProvider,
) : AudioOutput {
    override fun play() {
        delegate.play()
        owner.recordLifecycle(ProOutputRuntimeStatus.Started, delegate)
    }

    override fun pause() {
        delegate.pause()
        owner.recordLifecycle(ProOutputRuntimeStatus.Paused, delegate)
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        val accepted = delegate.write(buffer, encodedAccessUnitCount, presentationTimeUs)
        owner.recordWriteResult(accepted, delegate.isStalled)
        return accepted
    }

    override fun flush() {
        delegate.flush()
        owner.recordLifecycle(ProOutputRuntimeStatus.Flushed, delegate)
    }

    override fun stop() {
        delegate.stop()
        owner.recordLifecycle(ProOutputRuntimeStatus.Stopped, delegate)
    }

    override fun release() {
        delegate.release()
        owner.recordLifecycle(ProOutputRuntimeStatus.Released)
    }

    override fun setVolume(volume: Float) = delegate.setVolume(volume)
    override fun isOffloadedPlayback(): Boolean = delegate.isOffloadedPlayback
    override fun getAudioSessionId(): Int = delegate.audioSessionId
    override fun getSampleRate(): Int = delegate.sampleRate
    override fun getBufferSizeInFrames(): Long = delegate.bufferSizeInFrames
    override fun getPositionUs(): Long = delegate.positionUs
    override fun getPlaybackParameters(): PlaybackParameters = delegate.playbackParameters
    override fun isStalled(): Boolean = delegate.isStalled
    override fun addListener(listener: AudioOutput.Listener) = delegate.addListener(listener)
    override fun removeListener(listener: AudioOutput.Listener) = delegate.removeListener(listener)
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = delegate.setPlaybackParameters(playbackParameters)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = delegate.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    override fun setOffloadEndOfStream() = delegate.setOffloadEndOfStream()
    override fun setPlayerId(playerId: PlayerId) = delegate.setPlayerId(playerId)
    override fun canReuseAudioOutput(
        oldOutputConfig: AudioOutputProvider.OutputConfig,
        newFormatConfig: AudioOutputProvider.FormatConfig,
        newOutputConfig: AudioOutputProvider.OutputConfig,
    ): Boolean = delegate.canReuseAudioOutput(oldOutputConfig, newFormatConfig, newOutputConfig)

    override fun attachAuxEffect(auxEffectId: Int) = delegate.attachAuxEffect(auxEffectId)
    override fun setAuxEffectSendLevel(auxEffectSendLevel: Float) = delegate.setAuxEffectSendLevel(auxEffectSendLevel)
    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = delegate.setPreferredDevice(preferredDevice)
}

internal fun proOutputDebugLog(tag: String, message: String) {
    if (Log.isLoggable(tag, Log.DEBUG)) {
        Log.d(tag, message.take(480))
    }
}

private fun ProOutputRuntimeStatus.toActiveIfQualified(
    snapshot: ProOutputRuntimeSnapshot,
    actualRate: Int?,
): ProOutputRuntimeStatus {
    if (this != ProOutputRuntimeStatus.Started) return this
    val rate = actualRate ?: snapshot.actualSampleRateHz
    val highResolutionOutput = rate?.let { it >= 88_200 } == true || snapshot.actualEncoding.isHighPrecisionPcmLabel()
    return if (snapshot.localOfflineEligible && highResolutionOutput) ProOutputRuntimeStatus.Active else this
}

private fun Format.sampleRateHzOrNull(): Int? =
    sampleRate.takeIf { it > 0 }

private fun Int.encodingLabelOrNull(): String? =
    takeIf { it > 0 }?.let(::audioEncodingLabel)

private fun Int.isHighPrecisionEncoding(): Boolean =
    this == AudioFormat.ENCODING_PCM_24BIT_PACKED ||
        this == AudioFormat.ENCODING_PCM_32BIT ||
        this == AudioFormat.ENCODING_PCM_FLOAT

private fun String?.isHighPrecisionPcmLabel(): Boolean {
    val normalized = this?.uppercase(Locale.US) ?: return false
    return "PCM24" in normalized || "PCM32" in normalized || "FLOAT" in normalized
}

private fun Int.channelCountFromMask(): Int? =
    takeIf { it > 0 }?.let(Integer::bitCount)?.takeIf { it > 0 }
