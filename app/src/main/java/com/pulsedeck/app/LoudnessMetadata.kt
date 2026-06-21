package com.pulsedeck.app

import android.os.Bundle
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

internal const val EFFECTIVE_REPLAY_GAIN_MIN_DB = -24f
internal const val EFFECTIVE_REPLAY_GAIN_MAX_DB = 12f

enum class LoudnessSource {
    None,
    FileTag,
    ScannerAnalysis,
    StreamMetadata,
    UserManual,
}

enum class LoudnessConfidence {
    Unknown,
    Low,
    Medium,
    High,
}

enum class ReplayGainBasis {
    None,
    Track,
    Album,
    NoMetadata,
}

data class LoudnessMetadata(
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
    val r128TrackGainDb: Float? = null,
    val r128AlbumGainDb: Float? = null,
    val integratedLufs: Float? = null,
    val source: LoudnessSource = LoudnessSource.None,
    val confidence: LoudnessConfidence = LoudnessConfidence.Unknown,
    val scannedAtMillis: Long? = null,
) {
    val hasGain: Boolean
        get() = trackGainDb != null ||
            albumGainDb != null ||
            r128TrackGainDb != null ||
            r128AlbumGainDb != null
}

data class EffectiveReplayGain(
    val gainDb: Float = 0f,
    val metadataGainDb: Float? = null,
    val userPreampDb: Float = 0f,
    val peak: Float? = null,
    val basis: ReplayGainBasis = ReplayGainBasis.None,
    val source: LoudnessSource = LoudnessSource.None,
    val confidence: LoudnessConfidence = LoudnessConfidence.Unknown,
) {
    val active: Boolean
        get() = basis != ReplayGainBasis.None && abs(gainDb) > 0.001f
}

fun LoudnessMetadata.normalized(): LoudnessMetadata {
    val normalizedTrackGain = trackGainDb.normalizedGainDb()
    val normalizedAlbumGain = albumGainDb.normalizedGainDb()
    val normalizedR128TrackGain = r128TrackGainDb.normalizedGainDb()
    val normalizedR128AlbumGain = r128AlbumGainDb.normalizedGainDb()
    val normalizedPeak = trackPeak.normalizedPeak()
    val normalizedAlbumPeak = albumPeak.normalizedPeak()
    val normalizedLufs = integratedLufs?.takeIf { it.isFinite() && it in -120f..0f }
    val hasAnySignal = listOf(
        normalizedTrackGain,
        normalizedAlbumGain,
        normalizedR128TrackGain,
        normalizedR128AlbumGain,
        normalizedPeak,
        normalizedAlbumPeak,
        normalizedLufs,
    ).any { it != null }
    return if (!hasAnySignal) {
        LoudnessMetadata()
    } else {
        copy(
            trackGainDb = normalizedTrackGain,
            albumGainDb = normalizedAlbumGain,
            trackPeak = normalizedPeak,
            albumPeak = normalizedAlbumPeak,
            r128TrackGainDb = normalizedR128TrackGain,
            r128AlbumGainDb = normalizedR128AlbumGain,
            integratedLufs = normalizedLufs,
            source = source.takeUnless { it == LoudnessSource.None } ?: LoudnessSource.FileTag,
            confidence = if (confidence == LoudnessConfidence.Unknown) LoudnessConfidence.High else confidence,
            scannedAtMillis = scannedAtMillis?.takeIf { it > 0L },
        )
    }
}

fun ReplayGainSettings.effectiveReplayGain(metadata: LoudnessMetadata = LoudnessMetadata()): EffectiveReplayGain {
    val settings = normalized()
    val mode = if (settings.enabled) settings.mode else ReplayGainMode.Off
    if (mode == ReplayGainMode.Off) return EffectiveReplayGain()

    val loudness = metadata.normalized().takeIf { settings.allows(it.source) } ?: LoudnessMetadata()
    val trackGain = loudness.trackGainDb ?: loudness.r128TrackGainDb
    val albumGain = loudness.albumGainDb ?: loudness.r128AlbumGainDb
    val trackPeak = loudness.trackPeak
    val albumPeak = loudness.albumPeak

    return when (mode) {
        ReplayGainMode.Off -> EffectiveReplayGain()
        ReplayGainMode.Track -> trackGain?.let {
            replayGainResult(it, settings.trackPreampDb, trackPeak, ReplayGainBasis.Track, loudness)
        } ?: noMetadataReplayGain(settings)
        ReplayGainMode.Album -> albumGain?.let {
            replayGainResult(it, settings.albumPreampDb, albumPeak, ReplayGainBasis.Album, loudness)
        } ?: noMetadataReplayGain(settings)
        ReplayGainMode.Smart -> when {
            albumGain != null -> replayGainResult(albumGain, settings.albumPreampDb, albumPeak, ReplayGainBasis.Album, loudness)
            trackGain != null -> replayGainResult(trackGain, settings.trackPreampDb, trackPeak, ReplayGainBasis.Track, loudness)
            else -> noMetadataReplayGain(settings)
        }
    }
}

fun AudioEngineState.withSourceLoudness(metadata: LoudnessMetadata): AudioEngineState {
    val normalizedMetadata = metadata.normalized()
    val effective = replayGain.effectiveReplayGain(normalizedMetadata)
    val mode = if (replayGain.enabled) replayGain.mode else ReplayGainMode.Off
    return copy(
        replayGainMode = mode,
        replayGainPreampDb = effective.gainDb.coerceIn(EFFECTIVE_REPLAY_GAIN_MIN_DB, EFFECTIVE_REPLAY_GAIN_MAX_DB),
        sourceLoudness = normalizedMetadata,
        effectiveReplayGain = effective,
    ).normalized()
}

internal fun LoudnessMetadata.toJson(): JSONObject =
    JSONObject()
        .putNullableFloat("trackGainDb", trackGainDb)
        .putNullableFloat("albumGainDb", albumGainDb)
        .putNullableFloat("trackPeak", trackPeak)
        .putNullableFloat("albumPeak", albumPeak)
        .putNullableFloat("r128TrackGainDb", r128TrackGainDb)
        .putNullableFloat("r128AlbumGainDb", r128AlbumGainDb)
        .putNullableFloat("integratedLufs", integratedLufs)
        .put("source", source.name)
        .put("confidence", confidence.name)
        .put("scannedAtMillis", scannedAtMillis ?: 0L)

internal fun loudnessMetadataFromJson(json: JSONObject?): LoudnessMetadata {
    if (json == null) return LoudnessMetadata()
    return LoudnessMetadata(
        trackGainDb = json.optNullableFloat("trackGainDb"),
        albumGainDb = json.optNullableFloat("albumGainDb"),
        trackPeak = json.optNullableFloat("trackPeak"),
        albumPeak = json.optNullableFloat("albumPeak"),
        r128TrackGainDb = json.optNullableFloat("r128TrackGainDb"),
        r128AlbumGainDb = json.optNullableFloat("r128AlbumGainDb"),
        integratedLufs = json.optNullableFloat("integratedLufs"),
        source = enumValueOrDefault(json.optString("source"), LoudnessSource.None),
        confidence = enumValueOrDefault(json.optString("confidence"), LoudnessConfidence.Unknown),
        scannedAtMillis = json.optLong("scannedAtMillis", 0L).takeIf { it > 0L },
    ).normalized()
}

internal fun LoudnessMetadata.toBundle(): Bundle =
    Bundle().apply {
        putNullableFloat(BUNDLE_TRACK_GAIN, trackGainDb)
        putNullableFloat(BUNDLE_ALBUM_GAIN, albumGainDb)
        putNullableFloat(BUNDLE_TRACK_PEAK, trackPeak)
        putNullableFloat(BUNDLE_ALBUM_PEAK, albumPeak)
        putNullableFloat(BUNDLE_R128_TRACK_GAIN, r128TrackGainDb)
        putNullableFloat(BUNDLE_R128_ALBUM_GAIN, r128AlbumGainDb)
        putNullableFloat(BUNDLE_INTEGRATED_LUFS, integratedLufs)
        putString(BUNDLE_SOURCE, source.name)
        putString(BUNDLE_CONFIDENCE, confidence.name)
        putLong(BUNDLE_SCANNED_AT, scannedAtMillis ?: 0L)
    }

internal fun loudnessMetadataFromBundle(bundle: Bundle?): LoudnessMetadata {
    if (bundle == null) return LoudnessMetadata()
    return LoudnessMetadata(
        trackGainDb = bundle.getNullableFloat(BUNDLE_TRACK_GAIN),
        albumGainDb = bundle.getNullableFloat(BUNDLE_ALBUM_GAIN),
        trackPeak = bundle.getNullableFloat(BUNDLE_TRACK_PEAK),
        albumPeak = bundle.getNullableFloat(BUNDLE_ALBUM_PEAK),
        r128TrackGainDb = bundle.getNullableFloat(BUNDLE_R128_TRACK_GAIN),
        r128AlbumGainDb = bundle.getNullableFloat(BUNDLE_R128_ALBUM_GAIN),
        integratedLufs = bundle.getNullableFloat(BUNDLE_INTEGRATED_LUFS),
        source = enumValueOrDefault(bundle.getString(BUNDLE_SOURCE), LoudnessSource.None),
        confidence = enumValueOrDefault(bundle.getString(BUNDLE_CONFIDENCE), LoudnessConfidence.Unknown),
        scannedAtMillis = bundle.getLong(BUNDLE_SCANNED_AT, 0L).takeIf { it > 0L },
    ).normalized()
}

internal fun parseLoudnessMetadataTags(raw: ByteArray, nowMillis: Long = System.currentTimeMillis()): LoudnessMetadata {
    if (raw.isEmpty()) return LoudnessMetadata()
    return parseLoudnessMetadataText(raw.looseTagText(), nowMillis)
}

internal fun parseLoudnessMetadataText(text: String, nowMillis: Long = System.currentTimeMillis()): LoudnessMetadata {
    val trackGain = text.extractDbTag("REPLAYGAIN_TRACK_GAIN", "REPLAY_GAIN_TRACK_GAIN")
    val albumGain = text.extractDbTag("REPLAYGAIN_ALBUM_GAIN", "REPLAY_GAIN_ALBUM_GAIN")
    val trackPeak = text.extractFloatTag("REPLAYGAIN_TRACK_PEAK", "REPLAY_GAIN_TRACK_PEAK")
    val albumPeak = text.extractFloatTag("REPLAYGAIN_ALBUM_PEAK", "REPLAY_GAIN_ALBUM_PEAK")
    val r128TrackGain = text.extractR128GainTag("R128_TRACK_GAIN", "R128_TRACK_GAIN_DB")
    val r128AlbumGain = text.extractR128GainTag("R128_ALBUM_GAIN", "R128_ALBUM_GAIN_DB")
    val integratedLufs = text.extractDbTag("INTEGRATED_LUFS", "INTEGRATED_LOUDNESS", "LOUDNESS_LUFS")
    return LoudnessMetadata(
        trackGainDb = trackGain,
        albumGainDb = albumGain,
        trackPeak = trackPeak,
        albumPeak = albumPeak,
        r128TrackGainDb = r128TrackGain,
        r128AlbumGainDb = r128AlbumGain,
        integratedLufs = integratedLufs,
        source = LoudnessSource.FileTag,
        confidence = LoudnessConfidence.High,
        scannedAtMillis = nowMillis,
    ).normalized()
}

private fun ReplayGainSettings.allows(source: LoudnessSource): Boolean =
    when (this.source) {
        ReplayGainSource.Tags -> source == LoudnessSource.FileTag || source == LoudnessSource.StreamMetadata
        ReplayGainSource.ScannerAnalysis -> source == LoudnessSource.ScannerAnalysis
        ReplayGainSource.Fallback -> source != LoudnessSource.None
    }

private fun replayGainResult(
    metadataGainDb: Float,
    userPreampDb: Float,
    peak: Float?,
    basis: ReplayGainBasis,
    metadata: LoudnessMetadata,
): EffectiveReplayGain =
    EffectiveReplayGain(
        gainDb = (metadataGainDb + userPreampDb).coerceIn(EFFECTIVE_REPLAY_GAIN_MIN_DB, EFFECTIVE_REPLAY_GAIN_MAX_DB),
        metadataGainDb = metadataGainDb,
        userPreampDb = userPreampDb,
        peak = peak,
        basis = basis,
        source = metadata.source,
        confidence = metadata.confidence,
    )

private fun noMetadataReplayGain(settings: ReplayGainSettings): EffectiveReplayGain =
    EffectiveReplayGain(
        gainDb = settings.noRgPreampDb.coerceIn(EFFECTIVE_REPLAY_GAIN_MIN_DB, EFFECTIVE_REPLAY_GAIN_MAX_DB),
        userPreampDb = settings.noRgPreampDb,
        basis = if (abs(settings.noRgPreampDb) > 0.001f) ReplayGainBasis.NoMetadata else ReplayGainBasis.None,
    )

private fun Float?.normalizedGainDb(): Float? =
    this?.takeIf { it.isFinite() && it in -80f..24f }

private fun Float?.normalizedPeak(): Float? =
    this?.takeIf { it.isFinite() && it > 0f && it <= 8f }

private fun JSONObject.putNullableFloat(name: String, value: Float?): JSONObject =
    if (value == null) this else put(name, value.toDouble())

private fun JSONObject.optNullableFloat(name: String): Float? =
    if (has(name) && !isNull(name)) optDouble(name).toFloat().takeIf { it.isFinite() } else null

private fun Bundle.putNullableFloat(name: String, value: Float?) {
    if (value != null) putFloat(name, value)
}

private fun Bundle.getNullableFloat(name: String): Float? =
    if (containsKey(name)) getFloat(name).takeIf { it.isFinite() } else null

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
    runCatching { enumValueOf<T>(raw.orEmpty()) }.getOrDefault(fallback)

private fun ByteArray.looseTagText(): String =
    listOf(
        toString(Charsets.UTF_8),
        toString(Charsets.ISO_8859_1),
    ).joinToString("\n") { candidate ->
        candidate.map { char ->
            when {
                char == '\u0000' -> ' '
                char == '\n' || char == '\r' || char == '\t' -> ' '
                char.isISOControl() -> ' '
                else -> char
            }
        }.joinToString("")
    }

private fun String.extractDbTag(vararg names: String): Float? =
    extractFloatTag(*names)

private fun String.extractR128GainTag(vararg names: String): Float? =
    extractFloatTag(*names)?.let { raw ->
        if (abs(raw) > 64f) raw / 256f else raw
    }

private fun String.extractFloatTag(vararg names: String): Float? {
    for (name in names) {
        val pattern = Regex(
            pattern = """(?i)\b${Regex.escape(name)}\b[\s:=]+([+-]?\d+(?:\.\d+)?)""",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        val match = pattern.find(this) ?: continue
        val value = match.groupValues.getOrNull(1)?.toFloatOrNull()
        if (value != null && value.isFinite()) return value
    }
    return null
}

private const val BUNDLE_PREFIX = "com.pulsedeck.loudness."
private const val BUNDLE_TRACK_GAIN = "${BUNDLE_PREFIX}trackGainDb"
private const val BUNDLE_ALBUM_GAIN = "${BUNDLE_PREFIX}albumGainDb"
private const val BUNDLE_TRACK_PEAK = "${BUNDLE_PREFIX}trackPeak"
private const val BUNDLE_ALBUM_PEAK = "${BUNDLE_PREFIX}albumPeak"
private const val BUNDLE_R128_TRACK_GAIN = "${BUNDLE_PREFIX}r128TrackGainDb"
private const val BUNDLE_R128_ALBUM_GAIN = "${BUNDLE_PREFIX}r128AlbumGainDb"
private const val BUNDLE_INTEGRATED_LUFS = "${BUNDLE_PREFIX}integratedLufs"
private const val BUNDLE_SOURCE = "${BUNDLE_PREFIX}source"
private const val BUNDLE_CONFIDENCE = "${BUNDLE_PREFIX}confidence"
private const val BUNDLE_SCANNED_AT = "${BUNDLE_PREFIX}scannedAtMillis"

internal fun ReplayGainBasis.label(): String =
    name.lowercase(Locale.US)
