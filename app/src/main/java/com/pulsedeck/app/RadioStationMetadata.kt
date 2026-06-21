package com.pulsedeck.app

import java.util.Locale
import kotlin.math.max

internal enum class RadioGenre {
    Jazz,
    HipHopRnb,
    Pop,
    Rock,
    Classical,
    Electronic,
    Reggae,
    Country,
    Latin,
    Afrobeats,
    Gospel,
    Blues,
    Soul,
    Dance,
    Oldies,
    TraditionalFolk,
    EthiopianEastAfrican,
    Other,
    Unknown,
}

internal enum class RadioContentType {
    Music,
    News,
    Talk,
    Sports,
    Religion,
    Education,
    Culture,
    Comedy,
    PublicRadio,
    CommunityRadio,
    EmergencyLocalInfo,
    Unknown,
}

internal enum class RadioQualityTier {
    LowData,
    Balanced,
    HighQuality,
    Unknown,
}

internal enum class RadioReliability {
    RecentlySuccessful,
    Stable,
    Unverified,
    RecentlyFailed,
    Unknown,
}

internal data class NormalizedRadioStationMetadata(
    val genre: RadioGenre = RadioGenre.Unknown,
    val contentType: RadioContentType = RadioContentType.Unknown,
    val qualityTier: RadioQualityTier = RadioQualityTier.Unknown,
    val reliability: RadioReliability = RadioReliability.Unknown,
    val languageLabel: String = "",
    val primaryTag: String = "",
)

internal fun RadioStation.normalizedMetadata(): NormalizedRadioStationMetadata {
    val searchText = radioMetadataSearchText()
    val genre = inferRadioGenre(searchText)
    val contentType = inferRadioContentType(searchText, genre)
    return NormalizedRadioStationMetadata(
        genre = genre,
        contentType = contentType,
        qualityTier = inferRadioQualityTier(bitrate),
        reliability = inferRadioReliability(lastCheckOk, lastCheckTime, lastCheckOkTime),
        languageLabel = normalizeRadioLanguageLabel(language),
        primaryTag = tags.split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty(),
    )
}

internal fun RadioStation.discoveryKey(): String {
    val uuid = stationUuid.trim()
    if (uuid.isNotBlank()) return uuid
    val fingerprint = listOf(name, countryCode, country, language, tags, codec, bitrate.takeIf { it > 0 }?.toString().orEmpty())
        .joinToString("|")
        .normalizedSearchText()
    return "radio-${sha256(fingerprint.ifBlank { name.ifBlank { countryCode }.ifBlank { "station" } }).take(16)}"
}

internal fun RadioStation.favoriteMatchKeys(): Set<String> =
    buildSet {
        add(favoriteKey())
        stationUuid.trim().takeIf { it.isNotBlank() }?.let(::add)
        if (stationUuid.isBlank()) {
            streamUrl.trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }

internal fun RadioStation.isFavorite(favoriteKeys: Set<String>): Boolean =
    favoriteMatchKeys().any { it in favoriteKeys }

internal fun RadioStation.matchesLowDataPolicy(policy: StreamingDataPolicy): Boolean {
    val cap = policy.maxAudioBitrateKbps
    return when {
        bitrate <= 0 -> false
        cap != null -> bitrate <= cap
        else -> normalizedMetadata().qualityTier == RadioQualityTier.LowData
    }
}

internal fun RadioStation.radioDiscoveryScore(
    policy: StreamingDataPolicy,
    favorite: Boolean = false,
    recent: Boolean = false,
): Int {
    val metadata = normalizedMetadata()
    var score = max(votes, 0) * 4 + max(clickCount, 0) + max(clickTrend, 0) * 8
    score += when (metadata.reliability) {
        RadioReliability.RecentlySuccessful -> 360
        RadioReliability.Stable -> 260
        RadioReliability.Unverified -> 30
        RadioReliability.RecentlyFailed -> -650
        RadioReliability.Unknown -> 0
    }
    score += when (metadata.qualityTier) {
        RadioQualityTier.HighQuality -> 70
        RadioQualityTier.Balanced -> 90
        RadioQualityTier.LowData -> 60
        RadioQualityTier.Unknown -> 0
    }
    policy.maxAudioBitrateKbps?.let { cap ->
        score += when {
            bitrate in 1..cap -> 420
            bitrate > cap -> -420 - (bitrate - cap).coerceAtMost(320)
            else -> -40
        }
    }
    if (favorite) score += 160
    if (recent) score += 120
    return score
}

internal val RadioGenre.discoveryLabel: String
    get() = when (this) {
        RadioGenre.Jazz -> "Jazz"
        RadioGenre.HipHopRnb -> "Hip-Hop/R&B"
        RadioGenre.Pop -> "Pop"
        RadioGenre.Rock -> "Rock"
        RadioGenre.Classical -> "Classical"
        RadioGenre.Electronic -> "Electronic"
        RadioGenre.Reggae -> "Reggae"
        RadioGenre.Country -> "Country"
        RadioGenre.Latin -> "Latin"
        RadioGenre.Afrobeats -> "Afrobeats"
        RadioGenre.Gospel -> "Gospel"
        RadioGenre.Blues -> "Blues"
        RadioGenre.Soul -> "Soul"
        RadioGenre.Dance -> "Dance"
        RadioGenre.Oldies -> "Oldies"
        RadioGenre.TraditionalFolk -> "Folk"
        RadioGenre.EthiopianEastAfrican -> "East African"
        RadioGenre.Other -> "Other"
        RadioGenre.Unknown -> "Unknown"
    }

internal val RadioGenre.isDisplayableDiscoveryGenre: Boolean
    get() = this != RadioGenre.Unknown && this != RadioGenre.Other

internal val RadioContentType.discoveryLabel: String
    get() = when (this) {
        RadioContentType.Music -> "Music"
        RadioContentType.News -> "News"
        RadioContentType.Talk -> "Talk"
        RadioContentType.Sports -> "Sports"
        RadioContentType.Religion -> "Religion"
        RadioContentType.Education -> "Education"
        RadioContentType.Culture -> "Culture"
        RadioContentType.Comedy -> "Comedy"
        RadioContentType.PublicRadio -> "Public"
        RadioContentType.CommunityRadio -> "Community"
        RadioContentType.EmergencyLocalInfo -> "Local Info"
        RadioContentType.Unknown -> "Unknown"
    }

internal val RadioContentType.isDisplayableDiscoveryContent: Boolean
    get() = this != RadioContentType.Unknown

internal val RadioQualityTier.discoveryLabel: String
    get() = when (this) {
        RadioQualityTier.LowData -> "Low Data"
        RadioQualityTier.Balanced -> "Balanced"
        RadioQualityTier.HighQuality -> "High Quality"
        RadioQualityTier.Unknown -> "Unknown"
    }

internal val RadioQualityTier.isDisplayableDiscoveryQuality: Boolean
    get() = this != RadioQualityTier.Unknown

internal val RadioReliability.discoveryLabel: String
    get() = when (this) {
        RadioReliability.RecentlySuccessful -> "Recently Live"
        RadioReliability.Stable -> "Stable"
        RadioReliability.Unverified -> "Unverified"
        RadioReliability.RecentlyFailed -> "Recently Failed"
        RadioReliability.Unknown -> "Unknown"
    }

internal val RadioReliability.isPositiveReliability: Boolean
    get() = this == RadioReliability.RecentlySuccessful || this == RadioReliability.Stable

internal val RadioReliability.isDisplayableDiscoveryReliability: Boolean
    get() = this != RadioReliability.Unknown

private fun RadioStation.radioMetadataSearchText(): String =
    listOf(name, tags, language, country, countryCode)
        .joinToString(" ")
        .lowercase(Locale.US)
        .replace("&", " and ")
        .replace(RadioMetadataSeparator, " ")
        .trim()
        .let { " $it " }

private val RadioMetadataSeparator = Regex("[^a-z0-9+]+")

private fun inferRadioGenre(text: String): RadioGenre =
    when {
        text.hasRadioPhrase("ethiopian", "amharic", "oromo", "tigrigna", "tigrinya", "east african", "eritrean", "kenyan", "tanzanian", "ugandan") -> RadioGenre.EthiopianEastAfrican
        text.hasRadioPhrase("hip hop", "hiphop", "rap", "rnb", "r b", "rhythm and blues", "urban") -> RadioGenre.HipHopRnb
        text.hasRadioPhrase("afrobeats", "afrobeat", "afropop", "naija") -> RadioGenre.Afrobeats
        text.hasRadioPhrase("reggae", "dancehall", "ska") -> RadioGenre.Reggae
        text.hasRadioPhrase("classical", "opera", "symphony", "baroque", "chamber") -> RadioGenre.Classical
        text.hasRadioPhrase("electronic", "edm", "techno", "house", "trance", "dubstep", "ambient") -> RadioGenre.Electronic
        text.hasRadioPhrase("latin", "salsa", "bachata", "reggaeton", "cumbia", "merengue", "tango") -> RadioGenre.Latin
        text.hasRadioPhrase("gospel") -> RadioGenre.Gospel
        text.hasRadioPhrase("jazz") -> RadioGenre.Jazz
        text.hasRadioPhrase("blues") -> RadioGenre.Blues
        text.hasRadioPhrase("soul", "motown", "neo soul") -> RadioGenre.Soul
        text.hasRadioPhrase("country", "bluegrass", "americana") -> RadioGenre.Country
        text.hasRadioPhrase("rock", "metal", "punk", "alternative", "indie rock") -> RadioGenre.Rock
        text.hasRadioPhrase("dance", "club") -> RadioGenre.Dance
        text.hasRadioPhrase("oldies", "classic hits", "60s", "70s", "80s", "90s") -> RadioGenre.Oldies
        text.hasRadioPhrase("folk", "traditional", "world music", "roots") -> RadioGenre.TraditionalFolk
        text.hasRadioPhrase("pop", "top 40", "hits", "hit music", "chart") -> RadioGenre.Pop
        text.hasRadioPhrase("music") -> RadioGenre.Other
        else -> RadioGenre.Unknown
    }

private fun inferRadioContentType(text: String, genre: RadioGenre): RadioContentType =
    when {
        text.hasRadioPhrase("emergency", "weather alert", "traffic", "local info", "local information") -> RadioContentType.EmergencyLocalInfo
        text.hasRadioPhrase("public radio", "npr", "pbs") -> RadioContentType.PublicRadio
        text.hasRadioPhrase("community radio", "college radio", "campus radio") -> RadioContentType.CommunityRadio
        text.hasRadioPhrase("news", "world service", "current affairs") -> RadioContentType.News
        text.hasRadioPhrase("sports", "football", "soccer", "basketball", "baseball", "hockey") -> RadioContentType.Sports
        text.hasRadioPhrase("religion", "religious", "christian", "catholic", "church", "islamic", "quran") -> RadioContentType.Religion
        text.hasRadioPhrase("education", "educational", "learning", "science") -> RadioContentType.Education
        text.hasRadioPhrase("culture", "arts", "cultural") -> RadioContentType.Culture
        text.hasRadioPhrase("comedy", "humor") -> RadioContentType.Comedy
        text.hasRadioPhrase("talk", "speech", "interview", "podcast", "debate") -> RadioContentType.Talk
        genre != RadioGenre.Unknown -> RadioContentType.Music
        else -> RadioContentType.Unknown
    }

private fun inferRadioQualityTier(bitrate: Int): RadioQualityTier =
    when {
        bitrate <= 0 -> RadioQualityTier.Unknown
        bitrate <= 64 -> RadioQualityTier.LowData
        bitrate <= 160 -> RadioQualityTier.Balanced
        else -> RadioQualityTier.HighQuality
    }

private fun inferRadioReliability(lastCheckOk: Boolean?, lastCheckTime: String, lastCheckOkTime: String): RadioReliability =
    when (lastCheckOk) {
        true -> if (lastCheckOkTime.isNotBlank() || lastCheckTime.isNotBlank()) RadioReliability.RecentlySuccessful else RadioReliability.Stable
        false -> RadioReliability.RecentlyFailed
        null -> if (lastCheckTime.isNotBlank() || lastCheckOkTime.isNotBlank()) RadioReliability.Unverified else RadioReliability.Unknown
    }

private fun normalizeRadioLanguageLabel(language: String): String {
    val raw = language
        .split(',', ';', '/')
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    if (raw.isBlank()) return ""
    return raw.lowercase(Locale.US)
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase(Locale.US) } }
}

private fun String.hasRadioPhrase(vararg phrases: String): Boolean =
    phrases.any { phrase -> contains(" ${phrase.lowercase(Locale.US)} ") }
