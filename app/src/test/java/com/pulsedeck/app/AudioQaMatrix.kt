package com.pulsedeck.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class AudioQaArea {
    Transparency,
    GraphicEq,
    Headroom,
    Loudness,
    LimiterStatus,
    ResamplerStatus,
    OutputRouteReporting,
    CpuBudget,
    Regression,
}

internal enum class AudioQaEvidence {
    UnitSnapshot,
    GeneratedGoldenWav,
    DeviceSmoke,
    PerformanceBudget,
}

internal data class AudioQaScenario(
    val id: String,
    val phase: String,
    val area: AudioQaArea,
    val signalId: String?,
    val objective: String,
    val evidence: List<AudioQaEvidence>,
    val usesCopyrightedMedia: Boolean = false,
    val bundledInApk: Boolean = false,
)

internal fun pulseDeckAudioQaMatrix(): List<AudioQaScenario> =
    listOf(
        AudioQaScenario(
            id = "transparent_engine_off",
            phase = "18A",
            area = AudioQaArea.Transparency,
            signalId = "silence_44k1_mono",
            objective = "Engine-off and bypass paths preserve unity gain and report transparent ownership.",
            evidence = listOf(AudioQaEvidence.UnitSnapshot, AudioQaEvidence.GeneratedGoldenWav, AudioQaEvidence.DeviceSmoke),
        ),
        AudioQaScenario(
            id = "graphic_eq_1khz_slot",
            phase = "18A",
            area = AudioQaArea.GraphicEq,
            signalId = "sine_997hz_stereo",
            objective = "10-band Graphic EQ keeps canonical slot mapping and headroom contribution.",
            evidence = listOf(AudioQaEvidence.UnitSnapshot, AudioQaEvidence.GeneratedGoldenWav),
        ),
        AudioQaScenario(
            id = "auto_headroom_boost",
            phase = "18A",
            area = AudioQaArea.Headroom,
            signalId = "dual_tone_48k_stereo",
            objective = "Boosted EQ, preamp, and ReplayGain expose safe headroom trim without changing policy.",
            evidence = listOf(AudioQaEvidence.UnitSnapshot, AudioQaEvidence.GeneratedGoldenWav),
        ),
        AudioQaScenario(
            id = "loudness_groundwork",
            phase = "18A",
            area = AudioQaArea.Loudness,
            signalId = "sine_997hz_stereo",
            objective = "ReplayGain/LUFS fields remain metadata-driven and never invent true-peak scanner output.",
            evidence = listOf(AudioQaEvidence.UnitSnapshot),
        ),
        AudioQaScenario(
            id = "limiter_groundwork",
            phase = "18A",
            area = AudioQaArea.LimiterStatus,
            signalId = "impulse_96k_stereo",
            objective = "Limiter and peak-safety rows stay diagnostic until look-ahead limiting is implemented.",
            evidence = listOf(AudioQaEvidence.UnitSnapshot, AudioQaEvidence.GeneratedGoldenWav),
        ),
        AudioQaScenario(
            id = "resampler_strategy",
            phase = "18A",
            area = AudioQaArea.ResamplerStatus,
            signalId = "sine_997hz_stereo",
            objective = "Resampler status distinguishes system-managed, mismatch, and PulseDeck-planned states.",
            evidence = listOf(AudioQaEvidence.UnitSnapshot),
        ),
        AudioQaScenario(
            id = "output_route_reporting",
            phase = "18A",
            area = AudioQaArea.OutputRouteReporting,
            signalId = null,
            objective = "Output capability reporting stays conservative for speaker, wired, Bluetooth, and USB profiles.",
            evidence = listOf(AudioQaEvidence.UnitSnapshot, AudioQaEvidence.DeviceSmoke),
        ),
        AudioQaScenario(
            id = "chain_tab_cpu_budget",
            phase = "18A",
            area = AudioQaArea.CpuBudget,
            signalId = null,
            objective = "Chain tab remains readable without adding real-time polling or heavy meters.",
            evidence = listOf(AudioQaEvidence.DeviceSmoke, AudioQaEvidence.PerformanceBudget),
        ),
        AudioQaScenario(
            id = "pro_audio_regression_pack",
            phase = "18A",
            area = AudioQaArea.Regression,
            signalId = "dual_tone_48k_stereo",
            objective = "Regression tests cover transparency, ownership, warnings, golden files, and claim honesty.",
            evidence = listOf(AudioQaEvidence.UnitSnapshot, AudioQaEvidence.GeneratedGoldenWav),
        ),
    )

internal enum class AudioGoldenSignal {
    Silence,
    Sine997Hz,
    DualTone,
    Impulse,
}

internal data class GoldenAudioSpec(
    val id: String,
    val signal: AudioGoldenSignal,
    val sampleRateHz: Int,
    val channels: Int,
    val durationMillis: Int,
    val amplitude: Double,
) {
    val frameCount: Int
        get() = (sampleRateHz * durationMillis / 1000.0).roundToInt().coerceAtLeast(1)
}

internal data class GeneratedGoldenAudioFile(
    val spec: GoldenAudioSpec,
    val path: Path,
    val byteCount: Int,
    val sha256: String,
)

internal object PulseDeckGoldenAudioFiles {
    val specs: List<GoldenAudioSpec> = listOf(
        GoldenAudioSpec(
            id = "silence_44k1_mono",
            signal = AudioGoldenSignal.Silence,
            sampleRateHz = 44_100,
            channels = 1,
            durationMillis = 120,
            amplitude = 0.0,
        ),
        GoldenAudioSpec(
            id = "sine_997hz_stereo",
            signal = AudioGoldenSignal.Sine997Hz,
            sampleRateHz = 44_100,
            channels = 2,
            durationMillis = 250,
            amplitude = 0.5,
        ),
        GoldenAudioSpec(
            id = "dual_tone_48k_stereo",
            signal = AudioGoldenSignal.DualTone,
            sampleRateHz = 48_000,
            channels = 2,
            durationMillis = 250,
            amplitude = 0.45,
        ),
        GoldenAudioSpec(
            id = "impulse_96k_stereo",
            signal = AudioGoldenSignal.Impulse,
            sampleRateHz = 96_000,
            channels = 2,
            durationMillis = 50,
            amplitude = 0.75,
        ),
    )

    fun writeAll(directory: Path): List<GeneratedGoldenAudioFile> {
        Files.createDirectories(directory)
        return specs.map { spec ->
            val bytes = wavBytes(spec)
            val path = directory.resolve("${spec.id}.wav")
            Files.write(path, bytes)
            GeneratedGoldenAudioFile(
                spec = spec,
                path = path,
                byteCount = bytes.size,
                sha256 = sha256(bytes),
            )
        }
    }

    fun wavBytes(spec: GoldenAudioSpec): ByteArray {
        val bytesPerSample = 2
        val dataSize = spec.frameCount * spec.channels * bytesPerSample
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putAscii("RIFF")
        buffer.putInt(36 + dataSize)
        buffer.putAscii("WAVE")
        buffer.putAscii("fmt ")
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(spec.channels.toShort())
        buffer.putInt(spec.sampleRateHz)
        buffer.putInt(spec.sampleRateHz * spec.channels * bytesPerSample)
        buffer.putShort((spec.channels * bytesPerSample).toShort())
        buffer.putShort(16)
        buffer.putAscii("data")
        buffer.putInt(dataSize)
        repeat(spec.frameCount) { frame ->
            repeat(spec.channels) { channel ->
                val value = sampleValue(spec, frame, channel)
                buffer.putShort(value.toShort())
            }
        }
        return buffer.array()
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun sampleValue(spec: GoldenAudioSpec, frame: Int, channel: Int): Int {
        val t = frame.toDouble() / spec.sampleRateHz.toDouble()
        val raw = when (spec.signal) {
            AudioGoldenSignal.Silence -> 0.0
            AudioGoldenSignal.Sine997Hz -> sin(2.0 * PI * 997.0 * t)
            AudioGoldenSignal.DualTone -> 0.58 * sin(2.0 * PI * 440.0 * t) + 0.42 * sin(2.0 * PI * 1000.0 * t)
            AudioGoldenSignal.Impulse -> if (frame == 0) 1.0 else 0.0
        }
        val channelSign = if (spec.signal == AudioGoldenSignal.Impulse && channel % 2 == 1) -1.0 else 1.0
        return (raw * channelSign * spec.amplitude * Short.MAX_VALUE)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    }

    private fun ByteBuffer.putAscii(value: String) {
        value.toByteArray(Charsets.US_ASCII).forEach(::put)
    }
}
