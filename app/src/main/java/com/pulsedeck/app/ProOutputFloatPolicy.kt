@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsedeck.app

import androidx.media3.common.C
import androidx.media3.common.Format
import com.pulsedeck.app.settings.runtime.toSampleRateHzOrNull
import java.util.Locale
import kotlin.math.roundToInt

private const val HI_RES_MIN_SAMPLE_RATE_HZ = 88_200

internal data class HighPrecisionOutputDecision(
    val enableFloatOutput: Boolean,
    val reason: String,
)

internal fun highPrecisionOutputDecision(
    output: OutputSettings,
    media: ProOutputCurrentMedia,
    format: Format,
    rendererRequestedFloatOutput: Boolean = false,
    timestampDiscontinuityFallback: Boolean = false,
): HighPrecisionOutputDecision {
    if (timestampDiscontinuityFallback) {
        return HighPrecisionOutputDecision(
            enableFloatOutput = false,
            reason = "audio_sink_timestamp_discontinuity",
        )
    }

    if (output.deviceProfile == DeviceProfile.Bluetooth || output.deviceProfile == DeviceProfile.Car) {
        return HighPrecisionOutputDecision(
            enableFloatOutput = false,
            reason = "route_conservative_${output.deviceProfile.name.lowercase(Locale.US)}",
        )
    }

    val formatSampleRateHz = format.sampleRate.takeIf { it > 0 }
    val hintedSampleRateHz = media.qualityHint.sourceSampleRateHint()
    val sourceSampleRateHz = formatSampleRateHz ?: hintedSampleRateHz
    val formatHighPrecision = format.pcmEncoding.isHighPrecisionPcmEncoding()
    val hintedBitDepth = media.qualityHint.sourceBitDepthHint()
    val sourceLooksHighRate = formatSampleRateHz.isHiResSampleRate() || hintedSampleRateHz.isHiResSampleRate()
    val sourceLooksHighDepth = formatHighPrecision || hintedBitDepth?.let { it > 16 } == true
    val sourceLooksHighResolution = sourceLooksHighRate || sourceLooksHighDepth
    val explicitOutputHighPrecision = output.bitDepth in setOf(OutputBitDepth.Bit24, OutputBitDepth.Float32) ||
        output.sampleRate.toSampleRateHzOrNull().isHiResSampleRate()
    val hiResAttemptEnabled = output.hiResEnabled ||
        output.mode == OutputMode.HiRes ||
        output.bitPerfectAttemptEnabled ||
        explicitOutputHighPrecision
    val standardSourceKnown = sourceSampleRateHz != null &&
        sourceSampleRateHz <= 48_000 &&
        !formatHighPrecision &&
        (hintedBitDepth == null || hintedBitDepth <= 16)

    if (standardSourceKnown && !explicitOutputHighPrecision) {
        return HighPrecisionOutputDecision(
            enableFloatOutput = false,
            reason = "source_cd_quality_standard_output",
        )
    }

    if (!hiResAttemptEnabled && !rendererRequestedFloatOutput) {
        return HighPrecisionOutputDecision(
            enableFloatOutput = false,
            reason = "hi_res_not_requested",
        )
    }

    if (sourceLooksHighResolution || explicitOutputHighPrecision) {
        return HighPrecisionOutputDecision(
            enableFloatOutput = true,
            reason = when {
                explicitOutputHighPrecision -> "explicit_high_precision_output"
                sourceLooksHighRate -> "source_high_sample_rate"
                else -> "source_high_bit_depth"
            },
        )
    }

    if (rendererRequestedFloatOutput) {
        return HighPrecisionOutputDecision(
            enableFloatOutput = true,
            reason = "renderer_requested_float_output",
        )
    }

    return HighPrecisionOutputDecision(
        enableFloatOutput = false,
        reason = "source_not_high_resolution",
    )
}

internal fun String?.sourceBitDepthHint(): Int? =
    this
        ?.let {
            Regex("""\b(16|24|32)\s*(?:-| )?\s*bit\b""", RegexOption.IGNORE_CASE)
                .find(it)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }

private fun String?.sourceSampleRateHint(): Int? =
    this
        ?.let {
            Regex("""(\d+(?:\.\d+)?)\s*K\s*H(?:Z)?\b""", RegexOption.IGNORE_CASE)
                .find(it)
                ?.groupValues
                ?.getOrNull(1)
                ?.toFloatOrNull()
                ?.let { khz -> (khz * 1000f).roundToInt() }
        }

internal fun Int?.isHiResSampleRate(): Boolean =
    this != null && this >= HI_RES_MIN_SAMPLE_RATE_HZ

internal fun Int.isHighPrecisionPcmEncoding(): Boolean =
    this == C.ENCODING_PCM_24BIT ||
        this == C.ENCODING_PCM_32BIT ||
        this == C.ENCODING_PCM_FLOAT
