package com.pulsedeck.app.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.pulsedeck.app.LoudnessMetadata
import com.pulsedeck.app.parseLoudnessMetadataTags
import java.io.FileInputStream
import java.nio.ByteBuffer

internal data class LocalTrackMetadata(
    val genre: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val year: Int? = null,
    val loudnessMetadata: LoudnessMetadata = LoudnessMetadata(),
)

internal class LocalTrackMetadataReader {
    fun read(context: Context, uri: Uri): LocalTrackMetadata {
        val retriever = MediaMetadataRetriever()
        var metadata = LocalTrackMetadata()
        return try {
            metadata = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    retriever.setDataSource(descriptor.fileDescriptor)
                    LocalTrackMetadata(
                        genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()?.takeIf { it.isNotBlank() },
                        albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.takeIf { it.isNotBlank() },
                        composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.trim()?.takeIf { it.isNotBlank() },
                        year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                            ?.take(4)
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 },
                    )
                } ?: LocalTrackMetadata()
            }.getOrDefault(LocalTrackMetadata())
            metadata.copy(loudnessMetadata = readLoudnessMetadata(context, uri))
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readLoudnessMetadata(context: Context, uri: Uri): LoudnessMetadata =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                parseLoudnessMetadataTags(readTagBytes(FileInputStream(descriptor.fileDescriptor), descriptor.statSize))
            } ?: LoudnessMetadata()
        }.getOrDefault(LoudnessMetadata())

    private fun readTagBytes(input: FileInputStream, sizeBytes: Long): ByteArray =
        input.use { stream ->
            val channel = stream.channel
            if (sizeBytes <= 0L) {
                val buffer = ByteArray(LOUDNESS_TAG_WINDOW_BYTES)
                val read = stream.read(buffer)
                return if (read > 0) buffer.copyOf(read) else ByteArray(0)
            }
            val head = channel.readBytesAt(0L, LOUDNESS_TAG_WINDOW_BYTES)
            val tailStart = (sizeBytes - LOUDNESS_TAG_WINDOW_BYTES).coerceAtLeast(0L)
            val tail = if (sizeBytes > head.size.toLong()) channel.readBytesAt(tailStart, LOUDNESS_TAG_WINDOW_BYTES) else ByteArray(0)
            head + tail
        }

    private fun java.nio.channels.FileChannel.readBytesAt(position: Long, maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        position(position)
        val buffer = ByteBuffer.allocate(maxBytes)
        val read = read(buffer)
        return if (read > 0) buffer.array().copyOf(read) else ByteArray(0)
    }
}

private const val LOUDNESS_TAG_WINDOW_BYTES = 128 * 1024
