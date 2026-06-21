package com.pulsedeck.app

import android.os.Bundle
import androidx.media3.common.MediaItem
import java.util.Locale

internal const val PULSE_PRO_OUTPUT_SOURCE_SCOPE = "pulse.pro_output.source_scope"
internal const val PULSE_PRO_OUTPUT_URI_SCHEME = "pulse.pro_output.uri_scheme"
internal const val PULSE_PRO_OUTPUT_QUALITY_HINT = "pulse.pro_output.quality_hint"

internal enum class ProOutputSourceScope(val wireValue: String) {
    Local("local"),
    PremiumDeck("premiumdeck"),
    PulseRadio("pulseradio"),
    Network("network"),
    Unknown("unknown"),
}

internal fun Track.proOutputMediaItemExtras(): Bundle =
    loudnessMetadata.toBundle().apply {
        val scope = proOutputSourceScope()
        putString(PULSE_PRO_OUTPUT_SOURCE_SCOPE, scope.wireValue)
        putString(PULSE_PRO_OUTPUT_URI_SCHEME, uri?.scheme?.lowercase(Locale.US).orEmpty())
        putString(PULSE_PRO_OUTPUT_QUALITY_HINT, proOutputQualityHint())
    }

internal fun Track.proOutputSourceScope(): ProOutputSourceScope {
    val scheme = uri?.scheme?.lowercase(Locale.US)
    val streamLibrary = folderPath.orEmpty().contains("Stream Library", ignoreCase = true)
    return when {
        album.title.equals("PulseRadio", ignoreCase = true) ||
            albumArtist.orEmpty().equals("PulseRadio", ignoreCase = true) -> ProOutputSourceScope.PulseRadio
        streamLibrary || scheme == "pulsedeck" -> ProOutputSourceScope.PremiumDeck
        scheme in setOf("http", "https") -> ProOutputSourceScope.Network
        scheme in setOf("content", "file") -> ProOutputSourceScope.Local
        folderPath.orEmpty().isNotBlank() && !streamLibrary -> ProOutputSourceScope.Local
        else -> ProOutputSourceScope.Unknown
    }
}

internal fun MediaItem?.proOutputCurrentMedia(): ProOutputCurrentMedia {
    if (this == null) return ProOutputCurrentMedia()
    val extras = mediaMetadata.extras
    val scope = extras?.getString(PULSE_PRO_OUTPUT_SOURCE_SCOPE)
        ?.let(::proOutputSourceScopeFromWire)
        ?: localConfiguration?.uri?.scheme?.lowercase(Locale.US)?.let(::proOutputSourceScopeFromScheme)
        ?: ProOutputSourceScope.Unknown
    val scheme = extras?.getString(PULSE_PRO_OUTPUT_URI_SCHEME)
        ?.takeIf { it.isNotBlank() }
        ?: localConfiguration?.uri?.scheme?.lowercase(Locale.US)
    val qualityHint = extras?.getString(PULSE_PRO_OUTPUT_QUALITY_HINT)
        ?.takeIf { it.isNotBlank() }
    return ProOutputCurrentMedia(scope = scope, scheme = scheme, qualityHint = qualityHint)
}

internal fun MediaItem?.proOutputSourceScope(): ProOutputSourceScope =
    proOutputCurrentMedia().scope

private fun Track.proOutputQualityHint(): String =
    quality
        .replace(Regex("""https?://\S+""", RegexOption.IGNORE_CASE), "network-stream")
        .replace(Regex("""[A-Za-z]:[\\/][^\s|]+"""), "local-path")
        .take(120)

private fun proOutputSourceScopeFromWire(value: String): ProOutputSourceScope =
    ProOutputSourceScope.entries.firstOrNull { it.wireValue == value } ?: ProOutputSourceScope.Unknown

private fun proOutputSourceScopeFromScheme(scheme: String): ProOutputSourceScope =
    when (scheme) {
        "content", "file" -> ProOutputSourceScope.Local
        "http", "https" -> ProOutputSourceScope.Network
        "pulsedeck" -> ProOutputSourceScope.PremiumDeck
        else -> ProOutputSourceScope.Unknown
    }
