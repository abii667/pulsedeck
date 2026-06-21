package com.pulsedeck.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulsedeck.app.audio.NativeAudioEngineBridge
import com.pulsedeck.app.audio.TruePeakAudioProcessor
import com.pulsedeck.app.settings.runtime.TruePeakDiagnosticsSnapshot
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase31CRealGraphicEqDspMeasurementInstrumentedTest {
    @Test
    fun nativeGraphicEqMeasurementGateWritesJsonReport() {
        assertEquals(
            com.pulsedeck.app.audio.NativeAudioInitializationState.Ready,
            NativeAudioEngineBridge.initializeBlocking("phase31c_measurement_gate"),
        )

        val startedAtNanos = System.nanoTime()
        val report = JSONObject()
            .put("phase", "31C")
            .put("kind", "native_graphic_eq_measurement")
            .put("device", deviceInfoJson())
            .put("productionNativeEntryPoints", productionEntryPointProof())
            .put("canonicalFrequenciesHz", jsonArray(CanonicalFrequenciesHz))
            .put("requestedGridDb", jsonArray(GraphicEqGridDb))
            .put("sampleRatesHz", jsonArray(SampleRatesHz))
            .put("rawLevelDbfs", RawTransferLevelDbfs)
            .put("additionalVerificationLevelDbfs", AdditionalVerificationLevelDbfs)
            .put("runtimeOwner", "PulseDeck Native DSP via NativeAudioEngineBridge -> JNI -> NativeAudioEngine.processPcm16")

        NativeHarness("phase31c_native_grid").use { harness ->
            val gridResults = JSONArray()
            SampleRatesHz.forEach { sampleRate ->
                CanonicalFrequenciesHz.forEachIndexed { bandIndex, _ ->
                    GraphicEqGridDb.forEach { requestedDb ->
                        gridResults.put(measureBandGridCase(harness, bandIndex, requestedDb, sampleRate))
                    }
                }
            }
            report.put("perBandGrid", gridResults)

            val monotonic = JSONArray()
            CanonicalFrequenciesHz.indices.forEach { bandIndex ->
                monotonic.put(measureMonotonicity(harness, bandIndex, -12.0, 12.0, 0.1, "standard_plus_minus_12"))
            }
            report.put("monotonicityStandard", monotonic)

            val extended = JSONArray()
            CanonicalFrequenciesHz.indices.forEach { bandIndex ->
                listOf(-15.0, -12.0, 12.0, 15.0).forEach { requestedDb ->
                    extended.put(measureBandGridCase(harness, bandIndex, requestedDb, 48_000, testOnlyExtended = true))
                }
            }
            report.put("extendedPlusMinus15Cases", extended)

            val extendedMonotonic = JSONArray()
            CanonicalFrequenciesHz.indices.forEach { bandIndex ->
                extendedMonotonic.put(measureMonotonicity(harness, bandIndex, -15.0, 15.0, 0.1, "test_only_plus_minus_15"))
            }
            report.put("monotonicityExtended", extendedMonotonic)

            report.put("flatBypassTransparency", measureFlatBypassTransparency(harness))
            report.put("preampMeasurements", measurePreamp(harness))
            report.put("safetyMeasurements", measureSafetyPath(harness))
        }

        report.put("elapsedMs", elapsedMs(startedAtNanos))
        val file = writeInstrumentationReport("phase31c_native_graphic_eq_measurements.json", report)
        assertTrue(file.exists())
        assertTrue(report.getJSONArray("perBandGrid").length() > 0)
    }

    @Test
    fun platformEqualizerProbeWritesJsonReport() {
        val report = JSONObject()
            .put("phase", "31C")
            .put("kind", "android_platform_equalizer_probe")
            .put("device", deviceInfoJson())
            .put("canonicalFrequenciesHz", jsonArray(CanonicalFrequenciesHz))
            .put("requestedDb", jsonArray(PlatformProbeDb))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val minBuffer = AudioTrack.getMinBufferSize(
            48_000,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(48_000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()

        report.put("audioSessionId", track.audioSessionId)
        report.put("route", audioRouteJson(context))

        val rows = JSONArray()
        val collisions = JSONObject()
        try {
            val equalizer = Equalizer(0, track.audioSessionId)
            try {
                equalizer.enabled = true
                val range = equalizer.bandLevelRange
                val minCb = range.getOrNull(0)?.toInt() ?: -1200
                val maxCb = range.getOrNull(1)?.toInt() ?: 1200
                report
                    .put("available", true)
                    .put("enabled", equalizer.enabled)
                    .put("numberOfBands", equalizer.numberOfBands.toInt())
                    .put("bandLevelRangeCb", JSONArray().put(minCb).put(maxCb))
                    .put("platformBands", platformBandsJson(equalizer))

                CanonicalFrequenciesHz.forEach { frequencyHz ->
                    val platformBand = equalizer.getBand((frequencyHz * 1000f).roundToInt()).toInt()
                    val key = platformBand.toString()
                    collisions.put(key, (collisions.optJSONArray(key) ?: JSONArray()).put(frequencyHz))
                    PlatformProbeDb.forEach { requestedDb ->
                        repeat(equalizer.numberOfBands.toInt()) { index ->
                            equalizer.setBandLevel(index.toShort(), 0)
                        }
                        val requestedCb = (requestedDb * 100.0).roundToInt()
                        val clampedCb = requestedCb.coerceIn(minCb, maxCb)
                        equalizer.setBandLevel(platformBand.toShort(), clampedCb.toShort())
                        rows.put(
                            JSONObject()
                                .put("canonicalFrequencyHz", frequencyHz)
                                .put("platformBand", platformBand)
                                .put("requestedDb", requestedDb)
                                .put("requestedCb", requestedCb)
                                .put("appliedCb", clampedCb)
                                .put("readbackCb", equalizer.getBandLevel(platformBand.toShort()).toInt())
                                .put("clampedToDeviceRange", requestedCb != clampedCb)
                                .put("enabled", equalizer.enabled),
                        )
                    }
                }
            } finally {
                runCatching { equalizer.release() }
            }
        } catch (error: Throwable) {
            report
                .put("available", false)
                .put("error", error.javaClass.simpleName + ": " + (error.message ?: "no message"))
        } finally {
            runCatching { track.release() }
        }

        report.put("canonicalToPlatformReadback", rows)
        report.put("canonicalBandCollisions", collisions)
        val file = writeInstrumentationReport("phase31c_platform_equalizer_probe.json", report)
        assertTrue(file.exists())
    }

    @Test
    fun nativeLifecycleAndPerformanceWritesJsonReport() {
        assertEquals(
            com.pulsedeck.app.audio.NativeAudioInitializationState.Ready,
            NativeAudioEngineBridge.initializeBlocking("phase31c_lifecycle"),
        )
        val report = JSONObject()
            .put("phase", "31C")
            .put("kind", "native_lifecycle_performance")
            .put("device", deviceInfoJson())

        val lifecycleStarted = System.nanoTime()
        var successfulCycles = 0
        repeat(100) { index ->
            NativeHarness("phase31c_lifecycle_$index").use { harness ->
                val signal = PcmSignal.silence(48_000, 2, 50)
                val output = harness.render(signal, NativeRenderConfig())
                if (output.writtenBytes == signal.byteCount) successfulCycles += 1
            }
        }
        report
            .put("requestedLifecycleCycles", 100)
            .put("successfulLifecycleCycles", successfulCycles)
            .put("lifecycleElapsedMs", elapsedMs(lifecycleStarted))

        NativeHarness("phase31c_performance").use { harness ->
            val seconds = 60
            val signal = PcmSignal.multitone(48_000, 2, seconds * 1000, -24.0)
            val startedAt = System.nanoTime()
            val output = harness.render(signal, NativeRenderConfig())
            val elapsedMs = elapsedMs(startedAt).coerceAtLeast(1L)
            report
                .put("performanceSecondsOfPcm", seconds)
                .put("performanceElapsedMs", elapsedMs)
                .put("realtimeFactor", seconds * 1000.0 / elapsedMs.toDouble())
                .put("writtenBytes", output.writtenBytes)
                .put("invalidOutputBytes", output.invalidOutputBytes)
        }

        val file = writeInstrumentationReport("phase31c_native_lifecycle_performance.json", report)
        assertTrue(file.exists())
        assertEquals(100, successfulCycles)
    }

    private fun measureBandGridCase(
        harness: NativeHarness,
        bandIndex: Int,
        requestedDb: Double,
        sampleRate: Int,
        testOnlyExtended: Boolean = false,
    ): JSONObject {
        val centerHz = CanonicalFrequenciesHz[bandIndex]
        val center = measureSine(harness, bandIndex, requestedDb, centerHz, sampleRate, RawTransferLevelDbfs, gridDurationMs(centerHz))
        val previousHz = CanonicalFrequenciesHz.getOrNull(bandIndex - 1)
        val nextHz = CanonicalFrequenciesHz.getOrNull(bandIndex + 1)
        val previous = previousHz?.let {
            measureSine(harness, bandIndex, requestedDb, it, sampleRate, RawTransferLevelDbfs, gridDurationMs(it))
        }
        val next = nextHz?.let {
            measureSine(harness, bandIndex, requestedDb, it, sampleRate, RawTransferLevelDbfs, gridDurationMs(it))
        }
        val error = center.gainDb - requestedDb
        val pass = abs(error) <= 0.5 &&
            center.clippingCount == 0 &&
            center.invalidOutputBytes == 0 &&
            center.leftRightDifferenceDb <= 0.05
        return JSONObject()
            .put("band", compactBandLabel(centerHz))
            .put("bandIndex", bandIndex)
            .put("sampleRate", sampleRate)
            .put("requestedGainDb", requestedDb)
            .put("measuredCenterGainDb", center.gainDb)
            .put("errorDb", error)
            .put("previousBandResponseDb", previous?.gainDb)
            .put("nextBandResponseDb", next?.gainDb)
            .put("peak", center.peak)
            .put("clippingCount", center.clippingCount)
            .put("dcOffset", center.dcOffset)
            .put("leftRightDifferenceDb", center.leftRightDifferenceDb)
            .put("invalidOutputBytes", center.invalidOutputBytes)
            .put("processingDurationMs", center.processingDurationMs)
            .put("runtimeOwner", "PulseDeck Native DSP")
            .put("testOnlyExtended", testOnlyExtended)
            .put("pass", pass)
    }

    private fun measureMonotonicity(
        harness: NativeHarness,
        bandIndex: Int,
        startDb: Double,
        endDb: Double,
        stepDb: Double,
        label: String,
    ): JSONObject {
        val frequencyHz = CanonicalFrequenciesHz[bandIndex]
        val values = JSONArray()
        var previousGain: Double? = null
        var violations = 0
        var largestBackwardStep = 0.0
        var maxAbsError = 0.0
        var clipping = 0
        var invalidBytes = 0
        val steps = ((endDb - startDb) / stepDb).roundToInt()
        for (step in 0..steps) {
            val requested = ((startDb + step * stepDb) * 10.0).roundToInt() / 10.0
            val measured = measureSine(
                harness = harness,
                bandIndex = bandIndex,
                requestedDb = requested,
                probeFrequencyHz = frequencyHz,
                sampleRate = 48_000,
                levelDbfs = RawTransferLevelDbfs,
                durationMs = monotonicDurationMs(frequencyHz),
            )
            val gain = measured.gainDb
            val backwardStep = (previousGain ?: gain) - gain
            if (backwardStep > 0.08) {
                violations += 1
                largestBackwardStep = max(largestBackwardStep, backwardStep)
            }
            maxAbsError = max(maxAbsError, abs(gain - requested))
            clipping += measured.clippingCount
            invalidBytes += measured.invalidOutputBytes
            previousGain = gain
            values.put(JSONArray().put(requested).put(gain))
        }
        return JSONObject()
            .put("label", label)
            .put("band", compactBandLabel(frequencyHz))
            .put("bandIndex", bandIndex)
            .put("sampleRate", 48_000)
            .put("startDb", startDb)
            .put("endDb", endDb)
            .put("stepDb", stepDb)
            .put("pointCount", values.length())
            .put("violations", violations)
            .put("largestBackwardStepDb", largestBackwardStep)
            .put("maxAbsErrorDb", maxAbsError)
            .put("clippingCount", clipping)
            .put("invalidOutputBytes", invalidBytes)
            .put("points", values)
            .put("pass", violations == 0 && clipping == 0 && invalidBytes == 0)
    }

    private fun measureFlatBypassTransparency(harness: NativeHarness): JSONObject {
        val rows = JSONArray()
        listOf(
            "impulse" to PcmSignal.impulse(48_000, 2, 1000, -18.0),
            "log_sweep" to PcmSignal.logSweep(48_000, 2, 3000, -24.0),
            "pink_noise" to PcmSignal.pinkNoise(48_000, 2, 2000, -24.0),
            "multitone" to PcmSignal.multitone(48_000, 2, 2000, -24.0),
        ).forEach { (name, signal) ->
            val activeFlat = harness.render(signal, NativeRenderConfig())
            val bypassed = harness.render(signal, NativeRenderConfig(bypass = true))
            rows.put(compareSignals(name, "engine_active_graphic_eq_flat", signal, activeFlat))
            rows.put(compareSignals(name, "engine_bypassed", signal, bypassed))
        }
        return JSONObject().put("rows", rows)
    }

    private fun measurePreamp(harness: NativeHarness): JSONObject {
        val rows = JSONArray()
        PreampGridDb.forEach { requestedDb ->
            CanonicalFrequenciesHz.forEach { frequencyHz ->
                val signal = PcmSignal.sine(48_000, 2, gridDurationMs(frequencyHz), -24.0, frequencyHz)
                val output = harness.render(
                    signal,
                    NativeRenderConfig(masterGain = dbToLinearDouble(requestedDb).coerceIn(0.0, 1.5)),
                )
                val analysis = analyzeGain(signal, output, frequencyHz)
                rows.put(
                    JSONObject()
                        .put("requestedPreampDb", requestedDb)
                        .put("probeFrequencyHz", frequencyHz)
                        .put("measuredGainDb", analysis.gainDb)
                        .put("expectedNativeMasterGain", dbToLinearDouble(requestedDb).coerceIn(0.0, 1.5))
                        .put("nativeMasterGainClamped", dbToLinearDouble(requestedDb) > 1.5)
                        .put("leftRightDifferenceDb", analysis.leftRightDifferenceDb)
                        .put("clippingCount", analysis.clippingCount),
                )
            }
        }
        return JSONObject().put("rows", rows)
    }

    private fun measureSafetyPath(harness: NativeHarness): JSONObject {
        val rows = JSONArray()
        listOf(6.0, 12.0, 15.0).forEach { requestedDb ->
            listOf(
                "pink_noise" to PcmSignal.pinkNoise(48_000, 2, 2500, -6.0),
                "multitone" to PcmSignal.multitone(48_000, 2, 2500, -6.0),
                "full_scale_stress" to PcmSignal.fullScaleStress(48_000, 2, 2500),
            ).forEach { (signalName, signal) ->
                val headroomDb = -max(0.0, requestedDb)
                val output = harness.render(
                    signal,
                    NativeRenderConfig(
                        bandIndex = 5,
                        bandGainDb = requestedDb,
                        masterGain = dbToLinearDouble(headroomDb),
                        limiterEnabled = true,
                    ),
                )
                val comparison = compareSignals(signalName, "production_like_safety", signal, output)
                val truePeak = measureTruePeak(output, 48_000, 2)
                rows.put(
                    comparison
                        .put("requestedEqBoostDb", requestedDb)
                        .put("estimatedHeadroomDb", headroomDb)
                        .put("truePeakDbTp", truePeak.truePeakDbTp)
                        .put("truePeakStatus", truePeak.status.name),
                )
            }
        }
        return JSONObject().put("rows", rows)
    }

    private fun measureSine(
        harness: NativeHarness,
        bandIndex: Int,
        requestedDb: Double,
        probeFrequencyHz: Float,
        sampleRate: Int,
        levelDbfs: Double,
        durationMs: Int,
    ): GainAnalysis {
        val signal = PcmSignal.sine(sampleRate, 2, durationMs, levelDbfs, probeFrequencyHz)
        val output = harness.render(
            signal,
            NativeRenderConfig(
                bandIndex = bandIndex,
                bandGainDb = requestedDb,
            ),
        )
        return analyzeGain(signal, output, probeFrequencyHz)
    }

    private fun analyzeGain(signal: PcmSignal, output: NativeRenderOutput, frequencyHz: Float): GainAnalysis {
        val frames = min(signal.frameCount, output.samples.size / signal.channels)
        val trimFrames = trimFrames(frequencyHz, signal.sampleRate, frames)
        val start = trimFrames
        val end = (frames - trimFrames).coerceAtLeast(start + 1)
        val inputLeft = amplitudeAt(signal.samples, signal.channels, 0, signal.sampleRate, frequencyHz, start, end)
        val outputLeft = amplitudeAt(output.samples, signal.channels, 0, signal.sampleRate, frequencyHz, start, end)
        val inputRight = amplitudeAt(signal.samples, signal.channels, 1, signal.sampleRate, frequencyHz, start, end)
        val outputRight = amplitudeAt(output.samples, signal.channels, 1, signal.sampleRate, frequencyHz, start, end)
        val leftGain = dbRatio(outputLeft, inputLeft)
        val rightGain = dbRatio(outputRight, inputRight)
        val gain = (leftGain + rightGain) / 2.0
        return GainAnalysis(
            gainDb = gain,
            leftRightDifferenceDb = abs(leftGain - rightGain),
            peak = peak(output.samples),
            clippingCount = clippingCount(output.samples),
            dcOffset = dcOffset(output.samples),
            invalidOutputBytes = output.invalidOutputBytes,
            processingDurationMs = output.processingDurationMs,
        )
    }

    private fun compareSignals(signalName: String, mode: String, signal: PcmSignal, output: NativeRenderOutput): JSONObject {
        val frames = min(signal.frameCount, output.samples.size / signal.channels)
        val start = (signal.sampleRate / 10).coerceAtMost(frames / 4)
        val end = (frames - start).coerceAtLeast(start + 1)
        var squaredError = 0.0
        var squaredInput = 0.0
        var maxError = 0.0
        var count = 0
        for (frame in start until end) {
            for (channel in 0 until signal.channels) {
                val index = frame * signal.channels + channel
                val input = signal.samples[index].toDouble() / ShortScale
                val out = output.samples.getOrElse(index) { 0 }.toDouble() / ShortScale
                val error = out - input
                squaredError += error * error
                squaredInput += input * input
                maxError = max(maxError, abs(error))
                count += 1
            }
        }
        return JSONObject()
            .put("signal", signalName)
            .put("mode", mode)
            .put("sampleRate", signal.sampleRate)
            .put("rmsDifferenceDb", dbRatio(sqrt(squaredError / count.coerceAtLeast(1)), sqrt(squaredInput / count.coerceAtLeast(1))))
            .put("maxSampleError", maxError)
            .put("peak", peak(output.samples))
            .put("clippingCount", clippingCount(output.samples))
            .put("invalidOutputBytes", output.invalidOutputBytes)
            .put("processingDurationMs", output.processingDurationMs)
    }

    private fun measureTruePeak(output: NativeRenderOutput, sampleRate: Int, channels: Int): TruePeakDiagnosticsSnapshot {
        var latest = TruePeakDiagnosticsSnapshot()
        val processor = TruePeakAudioProcessor { snapshot -> latest = snapshot }
        val format = AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        val input = ByteBuffer.allocateDirect(output.samples.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        output.samples.forEach { input.putShort(it) }
        input.flip()
        processor.queueInput(input)
        processor.queueEndOfStream()
        processor.getOutput()
        val result = latest
        processor.reset()
        return result
    }

    private class NativeHarness(reason: String) : Closeable {
        private val handle = NativeAudioEngineBridge.createEngine(reason)

        init {
            check(handle != 0L) { "Native engine unavailable for $reason" }
        }

        fun render(signal: PcmSignal, config: NativeRenderConfig): NativeRenderOutput {
            configure(config)
            val input = signal.toDirectByteBuffer()
            val output = ByteBuffer.allocateDirect(signal.byteCount).order(ByteOrder.nativeOrder())
            val startedAt = System.nanoTime()
            val written = NativeAudioEngineBridge.processPcm16(
                handle = handle,
                input = input,
                output = output,
                bytes = signal.byteCount,
                sampleRate = signal.sampleRate,
                channels = signal.channels,
            )
            val elapsed = elapsedMs(startedAt)
            output.position(0)
            output.limit(written.coerceIn(0, output.capacity()))
            val samples = ShortArray(output.remaining() / Short.SIZE_BYTES)
            repeat(samples.size) { samples[it] = output.short }
            return NativeRenderOutput(
                samples = samples,
                writtenBytes = written,
                invalidOutputBytes = signal.byteCount - written,
                processingDurationMs = elapsed,
            )
        }

        private fun configure(config: NativeRenderConfig) {
            NativeAudioEngineBridge.setEnabled(handle, config.enabled)
            NativeAudioEngineBridge.setBypass(handle, config.bypass)
            NativeAudioEngineBridge.setMasterGain(handle, config.masterGain.toFloat())
            NativeAudioEngineBridge.setSource(handle, 440f, 0f)
            repeat(NativeEqSlots) { index ->
                NativeAudioEngineBridge.setEqBand(
                    handle = handle,
                    index = index,
                    frequencyHz = nativeSlotFrequency(index),
                    gainDb = 0f,
                    q = 1f,
                    type = ParametricFilterType.Peak.ordinal,
                    enabled = false,
                )
            }
            config.bandIndex?.let { bandIndex ->
                NativeAudioEngineBridge.setEqBand(
                    handle = handle,
                    index = bandIndex,
                    frequencyHz = CanonicalFrequenciesHz[bandIndex],
                    gainDb = config.bandGainDb.toFloat(),
                    q = 1f,
                    type = ParametricFilterType.Peak.ordinal,
                    enabled = true,
                )
            }
            NativeAudioEngineBridge.setStereo(handle, 0f, false, 0f, 0f)
            NativeAudioEngineBridge.setLimiter(handle, config.limiterEnabled, -1f, 120f, 0.55f)
            NativeAudioEngineBridge.setCompressor(handle, false, -24f, 1f, 10f, 120f, 0f, 0f)
            NativeAudioEngineBridge.setGate(handle, false, -80f, 1f, 0f, 20f)
            NativeAudioEngineBridge.setReverb(handle, false, 0f, 0f, 0f, 0f, 1f)
            NativeAudioEngineBridge.setDelay(handle, false, 1f, 0f, 0f)
            NativeAudioEngineBridge.setModulation(handle, false, false, false, 0.2f, 0f, 0f, 0f)
        }

        override fun close() {
            NativeAudioEngineBridge.release(handle)
        }
    }

    private data class NativeRenderConfig(
        val enabled: Boolean = true,
        val bypass: Boolean = false,
        val masterGain: Double = 1.0,
        val bandIndex: Int? = null,
        val bandGainDb: Double = 0.0,
        val limiterEnabled: Boolean = false,
    )

    private data class NativeRenderOutput(
        val samples: ShortArray,
        val writtenBytes: Int,
        val invalidOutputBytes: Int,
        val processingDurationMs: Long,
    )

    private data class GainAnalysis(
        val gainDb: Double,
        val leftRightDifferenceDb: Double,
        val peak: Double,
        val clippingCount: Int,
        val dcOffset: Double,
        val invalidOutputBytes: Int,
        val processingDurationMs: Long,
    )

    private data class PcmSignal(
        val sampleRate: Int,
        val channels: Int,
        val samples: ShortArray,
    ) {
        val frameCount: Int = samples.size / channels
        val byteCount: Int = samples.size * Short.SIZE_BYTES

        fun toDirectByteBuffer(): ByteBuffer {
            val buffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
            samples.forEach { buffer.putShort(it) }
            buffer.flip()
            return buffer
        }

        companion object {
            fun silence(sampleRate: Int, channels: Int, durationMs: Int): PcmSignal =
                PcmSignal(sampleRate, channels, ShortArray(frameCount(sampleRate, durationMs) * channels))

            fun sine(sampleRate: Int, channels: Int, durationMs: Int, levelDbfs: Double, frequencyHz: Float): PcmSignal =
                build(sampleRate, channels, durationMs, levelDbfs) { t, _ ->
                    sin(2.0 * PI * frequencyHz.toDouble() * t)
                }

            fun impulse(sampleRate: Int, channels: Int, durationMs: Int, levelDbfs: Double): PcmSignal =
                build(sampleRate, channels, durationMs, levelDbfs) { _, frame ->
                    if (frame == 0) 1.0 else 0.0
                }

            fun multitone(sampleRate: Int, channels: Int, durationMs: Int, levelDbfs: Double): PcmSignal =
                build(sampleRate, channels, durationMs, levelDbfs) { t, _ ->
                    CanonicalFrequenciesHz.sumOf { sin(2.0 * PI * it.toDouble() * t) } / CanonicalFrequenciesHz.size
                }

            fun fullScaleStress(sampleRate: Int, channels: Int, durationMs: Int): PcmSignal =
                build(sampleRate, channels, durationMs, 0.0) { t, frame ->
                    val noise = deterministicWhite(frame) * 0.15
                    (CanonicalFrequenciesHz.sumOf { sin(2.0 * PI * it.toDouble() * t) } / CanonicalFrequenciesHz.size) * 0.85 + noise
                }

            fun whiteNoise(sampleRate: Int, channels: Int, durationMs: Int, levelDbfs: Double): PcmSignal =
                build(sampleRate, channels, durationMs, levelDbfs) { _, frame -> deterministicWhite(frame) }

            fun pinkNoise(sampleRate: Int, channels: Int, durationMs: Int, levelDbfs: Double): PcmSignal {
                var pink = 0.0
                return build(sampleRate, channels, durationMs, levelDbfs) { _, frame ->
                    pink = 0.985 * pink + 0.015 * deterministicWhite(frame)
                    (pink * 4.0).coerceIn(-1.0, 1.0)
                }
            }

            fun logSweep(sampleRate: Int, channels: Int, durationMs: Int, levelDbfs: Double): PcmSignal =
                build(sampleRate, channels, durationMs, levelDbfs) { t, _ ->
                    val durationSeconds = durationMs / 1000.0
                    val startHz = 20.0
                    val endHz = 20_000.0
                    val ratio = ln(endHz / startHz)
                    val phase = 2.0 * PI * startHz * durationSeconds / ratio * ((endHz / startHz).pow(t / durationSeconds) - 1.0)
                    sin(phase)
                }

            private fun build(
                sampleRate: Int,
                channels: Int,
                durationMs: Int,
                levelDbfs: Double,
                sample: (Double, Int) -> Double,
            ): PcmSignal {
                val frames = frameCount(sampleRate, durationMs)
                val amplitude = dbToLinearDouble(levelDbfs).coerceIn(0.0, 1.0)
                val values = ShortArray(frames * channels)
                repeat(frames) { frame ->
                    val t = frame / sampleRate.toDouble()
                    val value = (sample(t, frame).coerceIn(-1.0, 1.0) * amplitude * Short.MAX_VALUE)
                        .roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    repeat(channels) { channel ->
                        values[frame * channels + channel] = value
                    }
                }
                return PcmSignal(sampleRate, channels, values)
            }

            private fun frameCount(sampleRate: Int, durationMs: Int): Int =
                (sampleRate.toDouble() * durationMs.toDouble() / 1000.0).roundToInt().coerceAtLeast(1)

            private fun deterministicWhite(frame: Int): Double {
                var x = frame * 1103515245 + 12345
                x = x xor (x ushr 16)
                return ((x and 0x7fffffff) / Int.MAX_VALUE.toDouble()) * 2.0 - 1.0
            }
        }
    }

    private fun amplitudeAt(
        samples: ShortArray,
        channels: Int,
        channel: Int,
        sampleRate: Int,
        frequencyHz: Float,
        startFrame: Int,
        endFrame: Int,
    ): Double {
        val safeChannel = channel.coerceAtMost(channels - 1)
        val omega = 2.0 * PI * frequencyHz.toDouble() / sampleRate.toDouble()
        var real = 0.0
        var imag = 0.0
        var count = 0
        for (frame in startFrame until endFrame) {
            val sample = samples[frame * channels + safeChannel].toDouble() / ShortScale
            val phase = omega * frame.toDouble()
            real += sample * cos(phase)
            imag -= sample * sin(phase)
            count += 1
        }
        return 2.0 * sqrt(real * real + imag * imag) / count.coerceAtLeast(1).toDouble()
    }

    private fun platformBandsJson(equalizer: Equalizer): JSONArray {
        val bands = JSONArray()
        repeat(equalizer.numberOfBands.toInt()) { index ->
            val band = index.toShort()
            val freqRange = runCatching { equalizer.getBandFreqRange(band) }.getOrNull()
            bands.put(
                JSONObject()
                    .put("index", index)
                    .put("centerFreqHz", equalizer.getCenterFreq(band) / 1000.0)
                    .put("freqRangeHz", freqRange?.let { JSONArray().put(it[0] / 1000.0).put(it[1] / 1000.0) })
                    .put("initialBandLevelCb", equalizer.getBandLevel(band).toInt()),
            )
        }
        return bands
    }

    private fun productionEntryPointProof(): JSONObject =
        JSONObject()
            .put("bridge", "NativeAudioEngineBridge.createEngine/setEqBand/processPcm16/release")
            .put("jni", "JniBridge.cpp nativeSetEqBand/nativeProcessPcm16")
            .put("cpp", "NativeAudioEngine::setEqBand and NativeAudioEngine::processPcm16/render")
            .put("noKotlinEqModelUsed", true)

    private fun deviceInfoJson(): JSONObject =
        JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("api", Build.VERSION.SDK_INT)

    private fun audioRouteJson(context: Context): JSONObject {
        val manager = context.getSystemService(AudioManager::class.java)
        val outputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { device ->
                JSONObject()
                    .put("type", device.type)
                    .put("productName", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.productName?.toString() else null)
            }
        } else {
            emptyList()
        }
        return JSONObject()
            .put("mode", manager.mode)
            .put("musicActive", manager.isMusicActive)
            .put("outputs", JSONArray(outputs))
    }

    private fun writeInstrumentationReport(name: String, json: JSONObject): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, name)
        file.writeText(json.toString(2))
        val externalCopy = context.externalMediaDirs
            .firstOrNull()
            ?.resolve("phase31c")
            ?.also { it.mkdirs() }
            ?.resolve(name)
        externalCopy?.writeText(json.toString(2))
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("phase31c_report", file.absolutePath)
                putString("phase31c_external_report", externalCopy?.absolutePath)
            },
        )
        return file
    }

    private fun trimFrames(frequencyHz: Float, sampleRate: Int, frames: Int): Int {
        val fourCycles = (sampleRate * 4.0 / frequencyHz.toDouble()).roundToInt()
        return max(sampleRate / 20, fourCycles).coerceAtMost(frames / 4)
    }

    private fun gridDurationMs(frequencyHz: Float): Int =
        when {
            frequencyHz <= 62f -> 1800
            frequencyHz <= 125f -> 1200
            frequencyHz <= 500f -> 800
            else -> 500
        }

    private fun monotonicDurationMs(frequencyHz: Float): Int =
        if (frequencyHz <= 62f) 800 else 250

    private fun peak(samples: ShortArray): Double =
        samples.maxOfOrNull { abs(it.toInt()).toDouble() / ShortScale } ?: 0.0

    private fun clippingCount(samples: ShortArray): Int =
        samples.count { it == Short.MAX_VALUE || it == Short.MIN_VALUE }

    private fun dcOffset(samples: ShortArray): Double =
        if (samples.isEmpty()) 0.0 else samples.sumOf { it.toDouble() / ShortScale } / samples.size.toDouble()

    private fun dbRatio(output: Double, input: Double): Double =
        if (input <= 1.0e-12 || output <= 1.0e-12) -240.0 else 20.0 * log10(output / input)

    private fun dbToLinearDouble(db: Double): Double =
        10.0.pow(db / 20.0)

    private fun nativeSlotFrequency(index: Int): Float =
        when (index) {
            in CanonicalFrequenciesHz.indices -> CanonicalFrequenciesHz[index]
            10 -> 80f
            11 -> 650f
            12 -> 2500f
            else -> 10000f
        }

    private fun compactBandLabel(frequencyHz: Float): String =
        if (frequencyHz >= 1000f) "${(frequencyHz / 1000f).roundToInt()}k" else "${frequencyHz.roundToInt()}Hz"

    private fun jsonArray(values: List<Number>): JSONArray =
        JSONArray().also { array -> values.forEach { array.put(it) } }

    private fun elapsedMs(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000L
}

private val CanonicalFrequenciesHz = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
private val SampleRatesHz = listOf(44_100, 48_000, 96_000)
private val GraphicEqGridDb = listOf(-12.0, -9.0, -6.0, -3.0, -1.0, -0.1, 0.0, 0.1, 1.0, 3.0, 6.0, 9.0, 12.0)
private val PlatformProbeDb = listOf(-15.0, -12.0, -6.0, -0.1, 0.0, 0.1, 6.0, 12.0, 15.0)
private val PreampGridDb = listOf(-12.0, -6.0, -3.0, -0.1, 0.0, 0.1, 3.0, 6.0, 12.0)
private const val RawTransferLevelDbfs = -24.0
private const val AdditionalVerificationLevelDbfs = -18.0
private const val NativeEqSlots = 14
private const val ShortScale = 32768.0

private fun elapsedMs(startedAtNanos: Long): Long =
    (System.nanoTime() - startedAtNanos) / 1_000_000L

private fun dbToLinearDouble(db: Double): Double =
    10.0.pow(db / 20.0)

private fun nativeSlotFrequency(index: Int): Float =
    when (index) {
        in CanonicalFrequenciesHz.indices -> CanonicalFrequenciesHz[index]
        10 -> 80f
        11 -> 650f
        12 -> 2500f
        else -> 10000f
    }
