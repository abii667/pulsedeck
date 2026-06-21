package com.pulsedeck.app

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

private fun String.qualityKbps(): Int? =
    Regex("""(\d+)""").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

internal fun Long.normalizedKbps(): Int? =
    takeIf { it > 0L }?.let { value ->
        if (value > 10_000L) (value / 1000L).toInt() else value.toInt()
    }

private fun StreamingDataPolicy.maxAudioBitrateKbpsFor(quality: YouTubeQuality): Int? {
    val sourceQualityCap = if (quality == YouTubeQuality.Standard) 160 else null
    return listOfNotNull(maxAudioBitrateKbps, sourceQualityCap).minOrNull()
}

private fun <T> selectAudioByPolicy(
    streams: List<T>,
    quality: YouTubeQuality,
    policy: StreamingDataPolicy,
    bitrateKbps: (T) -> Int?,
): T? {
    if (streams.isEmpty()) return null
    val cap = policy.maxAudioBitrateKbpsFor(quality)
    val known = streams.mapNotNull { stream ->
        bitrateKbps(stream)?.takeIf { it > 0 }?.let { kbps -> stream to kbps }
    }
    if (cap == null) return known.maxByOrNull { it.second }?.first ?: streams.first()
    return known
        .filter { it.second <= cap }
        .maxByOrNull { it.second }
        ?.first
        ?: known.minByOrNull { it.second }?.first
        ?: streams.first()
}

internal fun PipedAudioStream.bitrateKbps(): Int? =
    bitrate.normalizedKbps() ?: quality.qualityKbps()

internal fun InnertubeAudioFormat.bitrateKbps(): Int? =
    bitrate.normalizedKbps() ?: quality.qualityKbps()

internal fun selectPipedAudioStream(
    streams: List<PipedAudioStream>,
    quality: YouTubeQuality,
    policy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
): PipedAudioStream? =
    selectAudioByPolicy(streams, quality, policy) { it.bitrateKbps() }

internal fun selectPipedMuxedStream(
    streams: List<PipedAudioStream>,
    quality: YouTubeQuality,
    policy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
): PipedAudioStream? {
    if (!policy.allowMuxedFallback) return null
    if (streams.isEmpty()) return null
    val playable = streams.filter { stream ->
        val mime = stream.mimeType.orEmpty()
        stream.url.startsWith("http", ignoreCase = true) &&
            !mime.contains("dash", ignoreCase = true)
    }.ifEmpty { streams }
    val preferred = playable
        .filter { it.mimeType.orEmpty().contains("mp4", ignoreCase = true) }
        .ifEmpty { playable.filter { it.mimeType.orEmpty().contains("mpegurl", ignoreCase = true) } }
        .ifEmpty { playable }
    val constrained = policy.maxAudioBitrateKbpsFor(quality) != null
    return if (quality == YouTubeQuality.High && !constrained) {
        preferred.maxByOrNull { it.bitrate }
            ?: preferred.firstOrNull()
    } else {
        preferred.minByOrNull { it.quality.qualityKbps() ?: Int.MAX_VALUE }
            ?: preferred.minByOrNull { it.bitrate.takeIf { bitrate -> bitrate > 0L } ?: Long.MAX_VALUE }
            ?: preferred.firstOrNull()
    }
}

internal fun selectInnertubeAudioFormat(
    streams: List<InnertubeAudioFormat>,
    quality: YouTubeQuality,
    policy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
): InnertubeAudioFormat? =
    selectAudioByPolicy(streams, quality, policy) { it.bitrateKbps() }

private fun AudioStream.normalizedBitrateKbps(): Int {
    val best = listOf(getAverageBitrate(), getBitrate())
        .firstOrNull { it > 0 && it != AudioStream.UNKNOWN_BITRATE }
        ?: getQuality().qualityKbps()?.times(1000)
        ?: 0
    return if (best > 1000) best / 1000 else best
}

internal fun selectNewPipeAudioStream(
    streams: List<AudioStream>,
    quality: YouTubeQuality,
    policy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
): AudioStream? {
    val playable = streams.filter { stream -> stream.isUrl && stream.content.isNotBlank() }
    if (playable.isEmpty()) return null
    return selectAudioByPolicy(playable, quality, policy) { it.qualityKbpsPreference().takeIf { kbps -> kbps > 0 } }
        ?: playable.first()
}

internal fun AudioStream.qualityKbpsPreference(): Int =
    getQuality().qualityKbps() ?: normalizedBitrateKbps()

internal fun AudioStream.qualityLabel(): String {
    val kbps = normalizedBitrateKbps()
    val format = getFormat()?.getSuffix()?.uppercase(Locale.US)
        ?: getCodec().takeIf { it.isNotBlank() }?.uppercase(Locale.US)
    return buildList {
        if (kbps > 0) add("$kbps kbps")
        if (!getQuality().isNullOrBlank() && getQuality() != "$kbps kbps") add(getQuality())
        if (!format.isNullOrBlank()) add(format)
    }.distinct().joinToString(" ").ifBlank { "audio stream" }
}

internal fun selectNewPipeMuxedStream(
    streams: List<VideoStream>,
    quality: YouTubeQuality,
    policy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
): VideoStream? {
    if (!policy.allowMuxedFallback) return null
    val playable = streams.filter { stream ->
        stream.isUrl &&
            stream.content.startsWith("http", ignoreCase = true) &&
            !stream.isVideoOnly
    }
    if (playable.isEmpty()) return null
    val preferred = playable
        .filter { it.format?.mimeType?.contains("mp4", ignoreCase = true) == true }
        .ifEmpty { playable }
    val constrained = policy.maxAudioBitrateKbpsFor(quality) != null
    return if (quality == YouTubeQuality.High && !constrained) {
        preferred.maxWithOrNull(compareBy<VideoStream> { it.height }.thenBy { it.bitrate })
    } else {
        preferred
            .filter { it.height in 1..360 }
            .maxByOrNull { it.height }
            ?: preferred.minByOrNull { it.height.takeIf { height -> height > 0 } ?: Int.MAX_VALUE }
    } ?: preferred.first()
}

internal fun VideoStream.qualityLabel(): String {
    val format = format?.suffix?.uppercase(Locale.US)
    return buildList {
        resolution.takeIf { it.isNotBlank() }?.let { add(it) }
        if (bitrate > 0) add("${bitrate / 1000} kbps")
        if (!format.isNullOrBlank()) add(format)
        add("muxed")
    }.distinct().joinToString(" ").ifBlank { "muxed stream" }
}

internal fun VideoStream.bitrateKbpsPreference(): Int? =
    bitrate.toLong().normalizedKbps()

private fun parseTimecodeMillis(raw: String): Long? {
    val parts = raw.trim().split(":").mapNotNull { it.toLongOrNull() }
    if (parts.size !in 2..3) return null
    val seconds = when (parts.size) {
        2 -> parts[0] * 60L + parts[1]
        else -> parts[0] * 3600L + parts[1] * 60L + parts[2]
    }
    return seconds * 1000L
}

internal fun parseChaptersFromDescription(description: String?, durationMillis: Long): List<YouTubeChapter> {
    if (description.isNullOrBlank()) return emptyList()
    val regex = Regex("""(?m)^\s*(\d{1,2}:\d{2}(?::\d{2})?)\s+(.+?)\s*$""")
    val starts = regex.findAll(description).mapNotNull { match ->
        val start = parseTimecodeMillis(match.groupValues[1]) ?: return@mapNotNull null
        val title = match.groupValues[2].trim().take(80)
        if (title.isBlank()) null else start to title
    }.distinctBy { it.first }.sortedBy { it.first }.toList()
    if (starts.size < 2 || starts.first().first > 10_000L) return emptyList()
    return starts.mapIndexed { index, chapter ->
        val end = starts.getOrNull(index + 1)?.first ?: durationMillis
        YouTubeChapter(chapter.second, chapter.first, end.coerceAtLeast(chapter.first))
    }
}
