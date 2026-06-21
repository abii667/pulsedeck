package com.pulsedeck.app

import com.pulsedeck.app.audio.nativeAudioActivationFor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase31BGraphicEqTransferAuditTest {
    @Test
    fun phase31bGraphicEqTopologyIsTenCanonicalSlotsWithPreampSeparate() {
        val expected = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val state = AudioEngineState(preampDb = 3.4f)

        assertEquals(GRAPHIC_EQ_BAND_COUNT, expected.size)
        assertEquals(expected, graphicEqCenterFrequenciesHz())
        assertEquals(expected, flatEqBands().map { it.frequencyHz })
        assertEquals(1000f, state.eqBands[5].frequencyHz, 0.0f)
        assertEquals(16000f, state.eqBands.last().frequencyHz, 0.0f)
        assertEquals(10, state.eqBands.size)
        assertEquals(3.4f, state.preampDb, 0.0f)
        assertTrue(state.eqBands.all { it.gainDb == 0f })
        assertEquals(-12f, AUDIO_EQ_MIN_GAIN_DB, 0.0f)
        assertEquals(12f, AUDIO_EQ_MAX_GAIN_DB, 0.0f)
    }

    @Test
    fun phase31bOneSliderMutationChangesExactlyOneCanonicalStateSlot() {
        val base = AudioEngineState()
        val changed = base.withGraphicEqBandGain(index = 5, gainDb = 10.2f)

        assertEquals(graphicEqCenterFrequenciesHz(), changed.eqBands.map { it.frequencyHz })
        assertEquals(1, changed.eqBands.count { abs(it.gainDb) > 0.0001f })
        assertEquals(10.2f, changed.eqBands[5].gainDb, 0.0f)
        changed.eqBands.forEachIndexed { index, band ->
            if (index != 5) assertEquals(0f, band.gainDb, 0.0f)
        }

        assertEquals(12f, base.withGraphicEqBandGain(5, 99f).eqBands[5].gainDb, 0.0f)
        assertEquals(-12f, base.withGraphicEqBandGain(5, -99f).eqBands[5].gainDb, 0.0f)
    }

    @Test
    fun phase31bFlatResetAndCompareSlotPreserveCanonicalFloatValues() {
        val dirty = AudioEngineState(
            preampDb = -2.7f,
            eqBands = flatEqBands().mapIndexed { index, band ->
                band.copy(gainDb = (index - 4) / 10f)
            },
            parametricEq = defaultParametricBands().mapIndexed { index, band ->
                band.copy(enabled = index == 1, gainDb = 4.4f)
            },
            bassDb = 1.2f,
            trebleDb = -0.7f,
            vocalClarityDb = 0.6f,
        ).normalized()

        val reset = dirty.withEqControlsReset()
        assertEquals(0f, reset.preampDb, 0.0f)
        assertEquals(flatEqBands(), reset.eqBands)
        assertEquals(defaultParametricBands(), reset.parametricEq)
        assertEquals(0f, reset.bassDb, 0.0f)
        assertEquals(0f, reset.trebleDb, 0.0f)
        assertEquals(0f, reset.vocalClarityDb, 0.0f)

        val restored = AudioEngineState().withNativePresetSlot(dirty.toNativePresetSlot("phase31b"), NativeCompareSlot.A)
        assertEquals(dirty.preampDb, restored.preampDb, 0.0f)
        assertEquals(dirty.eqBands.map { it.gainDb }, restored.eqBands.map { it.gainDb })
        assertEquals(dirty.parametricEq[1].gainDb, restored.parametricEq[1].gainDb, 0.0f)
    }

    @Test
    fun phase31bRuntimePolicyClassifiesNativePlatformBypassAndUnsupportedFallbackPaths() {
        val graphicBoost = AudioEngineState().withGraphicEqBandGain(5, 6f)
        val platformPolicy = graphicBoost.effectiveProcessingPolicy(nativeAvailable = false)

        assertTrue(platformPolicy.platformEqAllowed)
        assertFalse(platformPolicy.nativeMedia3DspActive)

        val nativeState = graphicBoost.copy(native = NativeAudioSettings(media3DspEnabled = true)).normalized()
        val nativePolicy = nativeState.effectiveProcessingPolicy(nativeAvailable = true)
        val nativeActivation = nativeAudioActivationFor(nativeState, nativeAvailable = true)

        assertTrue(nativePolicy.nativeMedia3DspActive)
        assertTrue(nativeActivation.media3DspActive)
        assertFalse(nativePolicy.platformEqAllowed)

        val unsupportedPolicy = nativeState.effectiveProcessingPolicy(
            nativeAvailable = true,
            nativeMedia3DspFormatSupported = false,
            nativeMedia3DspFallbackReason = "PCM float is not supported.",
        )
        assertTrue(unsupportedPolicy.nativeMedia3DspRequested)
        assertFalse(unsupportedPolicy.nativeMedia3DspActive)
        assertTrue(unsupportedPolicy.platformEqAllowed)
        assertEquals("PCM float is not supported.", unsupportedPolicy.fallbackReason)

        val bypassed = graphicBoost.copy(bypass = true).effectiveProcessingPolicy(nativeAvailable = true)
        assertFalse(bypassed.processingActive)
        assertFalse(bypassed.platformEffectsAllowed)
        assertFalse(bypassed.nativeMedia3DspActive)
    }

    @Test
    fun phase31bDbMathShowsFifteenDbEvaluationDoesNotChangeProductionRange() {
        assertEquals(3.981f, dbToLinear(12f), 0.001f)
        assertEquals(5.623f, dbToLinear(15f), 0.001f)
        assertEquals(12f, AudioEngineState().withGraphicEqBandGain(5, 15f).eqBands[5].gainDb, 0.0f)
        assertEquals(-12f, AudioEngineState(preampDb = -15f).normalized().preampDb, 0.0f)
    }

    @Test
    fun phase31bGeneratedFixturesCoverRequiredSignalsAndRates() {
        val specs = Phase31BTestAudioFixtures.specs()

        assertEquals(setOf(44_100, 48_000, 96_000), specs.map { it.sampleRateHz }.toSet())
        Phase31BTestSignal.entries.forEach { signal ->
            assertTrue("missing $signal", specs.any { it.signal == signal })
        }
        graphicEqCenterFrequenciesHz().forEach { frequency ->
            assertTrue(
                "missing sine fixture for $frequency Hz",
                specs.any { it.signal == Phase31BTestSignal.CenterSine && it.frequencyHz == frequency },
            )
        }
        assertTrue(
            specs.filter { it.signal == Phase31BTestSignal.CenterSine && it.frequencyHz in setOf(31f, 62f) }
                .all { it.completeCycles >= 120.0 },
        )
        assertTrue(specs.any { it.signal == Phase31BTestSignal.CenterSine && it.levelDbfs == -24.0 })
        assertTrue(specs.any { it.signal == Phase31BTestSignal.CenterSine && it.levelDbfs == -18.0 })
        assertTrue(specs.any { it.signal == Phase31BTestSignal.FullScaleStress && it.levelDbfs == 0.0 })
    }

    @Test
    fun phase31bGeneratedFixturesAreDeterministicWavPcmAndNotMainAssets() {
        val spec = Phase31BTestFixtureSpec(
            signal = Phase31BTestSignal.Multitone,
            sampleRateHz = 48_000,
            channels = 2,
            durationMillis = 2000,
            levelDbfs = -24.0,
        )

        val first = Phase31BTestAudioFixtures.wavBytes(spec)
        val second = Phase31BTestAudioFixtures.wavBytes(spec)

        assertArrayEquals(first, second)
        assertEquals("RIFF", first.ascii(0, 4))
        assertEquals("WAVE", first.ascii(8, 4))
        assertEquals("fmt ", first.ascii(12, 4))
        assertEquals("data", first.ascii(36, 4))
        assertEquals(spec.sampleRateHz, first.intLe(24))
        assertEquals(16, first.shortLe(34).toInt())
        assertEquals(first.size - 44, first.intLe(40))
        assertTrue(first.size < 800_000)
    }
}

private enum class Phase31BTestSignal {
    Silence,
    CenterSine,
    LogSweep,
    PinkNoise,
    WhiteNoise,
    Impulse,
    Multitone,
    FullScaleStress,
}

private data class Phase31BTestFixtureSpec(
    val signal: Phase31BTestSignal,
    val sampleRateHz: Int,
    val channels: Int,
    val durationMillis: Int,
    val levelDbfs: Double,
    val frequencyHz: Float? = null,
) {
    val frameCount: Int
        get() = (sampleRateHz * durationMillis / 1000.0).roundToInt().coerceAtLeast(1)

    val completeCycles: Double
        get() = frequencyHz?.let { durationMillis / 1000.0 * it } ?: 0.0
}

private object Phase31BTestAudioFixtures {
    private val sampleRates = listOf(44_100, 48_000, 96_000)
    private val centerSineLevels = listOf(-24.0, -18.0)

    fun specs(): List<Phase31BTestFixtureSpec> =
        buildList {
            sampleRates.forEach { sampleRate ->
                add(Phase31BTestFixtureSpec(Phase31BTestSignal.Silence, sampleRate, 2, 1000, -120.0))
                centerSineLevels.forEach { dbfs ->
                    graphicEqCenterFrequenciesHz().forEach { frequency ->
                        add(
                            Phase31BTestFixtureSpec(
                                signal = Phase31BTestSignal.CenterSine,
                                sampleRateHz = sampleRate,
                                channels = 2,
                                durationMillis = if (frequency <= 62f) 6000 else 2000,
                                levelDbfs = dbfs,
                                frequencyHz = frequency,
                            ),
                        )
                    }
                }
                add(Phase31BTestFixtureSpec(Phase31BTestSignal.LogSweep, sampleRate, 2, 8000, -24.0))
                add(Phase31BTestFixtureSpec(Phase31BTestSignal.PinkNoise, sampleRate, 2, 4000, -24.0))
                add(Phase31BTestFixtureSpec(Phase31BTestSignal.WhiteNoise, sampleRate, 2, 4000, -24.0))
                add(Phase31BTestFixtureSpec(Phase31BTestSignal.Impulse, sampleRate, 2, 1000, -18.0))
                add(Phase31BTestFixtureSpec(Phase31BTestSignal.Multitone, sampleRate, 2, 4000, -24.0))
                add(Phase31BTestFixtureSpec(Phase31BTestSignal.FullScaleStress, sampleRate, 2, 2000, 0.0))
            }
        }

    fun wavBytes(spec: Phase31BTestFixtureSpec): ByteArray {
        val data = pcm16(spec)
        val dataSize = data.size * Short.SIZE_BYTES
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putAscii("RIFF")
        buffer.putInt(36 + dataSize)
        buffer.putAscii("WAVE")
        buffer.putAscii("fmt ")
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(spec.channels.toShort())
        buffer.putInt(spec.sampleRateHz)
        buffer.putInt(spec.sampleRateHz * spec.channels * Short.SIZE_BYTES)
        buffer.putShort((spec.channels * Short.SIZE_BYTES).toShort())
        buffer.putShort(16)
        buffer.putAscii("data")
        buffer.putInt(dataSize)
        data.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    private fun pcm16(spec: Phase31BTestFixtureSpec): ShortArray {
        var whiteState = 0x13579BDFu
        var pink = 0.0
        val amplitude = 10.0.pow(spec.levelDbfs / 20.0).coerceIn(0.0, 1.0)
        return ShortArray(spec.frameCount * spec.channels) { index ->
            val frame = index / spec.channels
            val channel = index % spec.channels
            val t = frame / spec.sampleRateHz.toDouble()
            if (channel == 0) {
                whiteState = whiteState * 1664525u + 1013904223u
            }
            val white = ((whiteState.toInt() ushr 1) / Int.MAX_VALUE.toDouble()) * 2.0 - 1.0
            val raw = when (spec.signal) {
                Phase31BTestSignal.Silence -> 0.0
                Phase31BTestSignal.CenterSine -> sin(2.0 * PI * (spec.frequencyHz ?: 1000f) * t)
                Phase31BTestSignal.LogSweep -> logSweep(t, spec.durationMillis / 1000.0)
                Phase31BTestSignal.PinkNoise -> {
                    pink = 0.985 * pink + 0.015 * white
                    pink * 4.0
                }
                Phase31BTestSignal.WhiteNoise -> white
                Phase31BTestSignal.Impulse -> if (frame == 0) 1.0 else 0.0
                Phase31BTestSignal.Multitone -> multitone(t)
                Phase31BTestSignal.FullScaleStress -> 0.72 * multitone(t) + 0.28 * white
            }
            (raw.coerceIn(-1.0, 1.0) * amplitude * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private fun logSweep(t: Double, durationSeconds: Double): Double {
        val startHz = 20.0
        val endHz = 20_000.0
        val ratio = ln(endHz / startHz)
        val phase = 2.0 * PI * startHz * durationSeconds / ratio * ((endHz / startHz).pow(t / durationSeconds) - 1.0)
        return sin(phase)
    }

    private fun multitone(t: Double): Double =
        graphicEqCenterFrequenciesHz().sumOf { frequency ->
            sin(2.0 * PI * frequency * t)
        } / graphicEqCenterFrequenciesHz().size

    private fun ByteBuffer.putAscii(value: String) {
        value.toByteArray(Charsets.US_ASCII).forEach(::put)
    }
}

private fun ByteArray.ascii(offset: Int, length: Int): String =
    String(copyOfRange(offset, offset + length), Charsets.US_ASCII)

private fun ByteArray.shortLe(offset: Int): Short =
    ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short

private fun ByteArray.intLe(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
