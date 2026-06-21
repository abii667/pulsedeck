package com.pulsedeck.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQaMatrixTest {
    @Test
    fun phase18aQaMatrixCoversRequiredAreasWithoutCopyrightedOrBundledMedia() {
        val matrix = pulseDeckAudioQaMatrix()
        val goldenIds = PulseDeckGoldenAudioFiles.specs.map { it.id }.toSet()

        assertEquals(AudioQaArea.entries.toSet(), matrix.map { it.area }.toSet())
        assertTrue(matrix.all { it.phase == "18A" })
        assertTrue(matrix.all { it.evidence.isNotEmpty() })
        assertTrue(matrix.none { it.usesCopyrightedMedia })
        assertTrue(matrix.none { it.bundledInApk })
        assertTrue(matrix.map { it.id }.distinct().size == matrix.size)
        assertTrue(matrix.filter { it.signalId != null }.all { it.signalId in goldenIds })
        assertTrue(matrix.any { it.area == AudioQaArea.CpuBudget && AudioQaEvidence.PerformanceBudget in it.evidence })
        assertTrue(matrix.any { it.area == AudioQaArea.OutputRouteReporting && AudioQaEvidence.DeviceSmoke in it.evidence })
    }

    @Test
    fun phase18aGoldenFilesAreGeneratedDeterministicallyOutsideApkAssets() {
        val directory = Files.createTempDirectory("pulsedeck-audio-golden")
        val files = PulseDeckGoldenAudioFiles.writeAll(directory)
        val repeatedHashes = PulseDeckGoldenAudioFiles.specs.map {
            PulseDeckGoldenAudioFiles.sha256(PulseDeckGoldenAudioFiles.wavBytes(it))
        }

        assertEquals(PulseDeckGoldenAudioFiles.specs.size, files.size)
        assertEquals(repeatedHashes, files.map { it.sha256 })
        assertTrue(files.sumOf { it.byteCount } < 200_000)
        files.forEach { generated ->
            assertTrue(Files.exists(generated.path))
            assertTrue(generated.path.startsWith(directory))
            assertFalse(generated.path.toString().contains("src${java.io.File.separator}main${java.io.File.separator}assets"))
            assertWavHeader(generated.spec, Files.readAllBytes(generated.path))
        }
    }

    private fun assertWavHeader(spec: GoldenAudioSpec, bytes: ByteArray) {
        assertEquals("RIFF", bytes.ascii(0, 4))
        assertEquals("WAVE", bytes.ascii(8, 4))
        assertEquals("fmt ", bytes.ascii(12, 4))
        assertEquals("data", bytes.ascii(36, 4))
        assertEquals(1, bytes.shortLe(20).toInt())
        assertEquals(spec.channels, bytes.shortLe(22).toInt())
        assertEquals(spec.sampleRateHz, bytes.intLe(24))
        assertEquals(16, bytes.shortLe(34).toInt())
        assertEquals(bytes.size - 44, bytes.intLe(40))
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        String(copyOfRange(offset, offset + length), Charsets.US_ASCII)

    private fun ByteArray.shortLe(offset: Int): Short =
        ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short

    private fun ByteArray.intLe(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
}
