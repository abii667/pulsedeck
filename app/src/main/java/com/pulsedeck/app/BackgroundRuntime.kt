package com.pulsedeck.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pulsedeck.app.navigation.Screen
import java.util.Locale

internal enum class BackgroundUsage { Player, List, Lyrics }

internal fun backgroundUsageFor(screen: Screen, playerOpen: Boolean): BackgroundUsage =
    if (playerOpen) {
        BackgroundUsage.Player
    } else {
        when (screen) {
            Screen.Library, Screen.AllSongs, Screen.Albums, Screen.AlbumDownloader, Screen.AlbumDetail, Screen.Artists,
            Screen.Folders, Screen.AlbumArtists, Screen.Genres, Screen.Years, Screen.Composers,
            Screen.LocalPlaylists, Screen.Bookmarks, Screen.MostPlayed, Screen.LibraryGroupTracks,
            Screen.FolderHierarchy, Screen.Audio, Screen.Search, Screen.PulseRadio, Screen.YouTube, Screen.YouTubeStream,
            Screen.Settings -> BackgroundUsage.List
        }
    }

@Composable
internal fun Background(album: Album, settings: BackgroundSettings = BackgroundSettings(), usage: BackgroundUsage = BackgroundUsage.Player) {
    val normalized = settings.normalized()
    val palette = rememberAlbumPalette(album)
    val top = tunedBackgroundColor(palette.first, normalized)
    val mid = tunedBackgroundColor(palette.second, normalized)
    val gradientColor = colorFromHex(normalized.gradientColor)
    val usageIntensity = when (usage) {
        BackgroundUsage.Player -> 1f
        BackgroundUsage.List -> 0.65f
        BackgroundUsage.Lyrics -> 0.52f
    }
    val usageDetail = when (usage) {
        BackgroundUsage.Player -> 1f
        BackgroundUsage.List -> 0.50f
        BackgroundUsage.Lyrics -> 0.36f
    }
    val gradientMix = (if (usage == BackgroundUsage.List && !normalized.gradientForLists) 0f else normalized.gradient / 10f).coerceIn(0f, 1f)
    val detailAlpha = if (normalized.blurred) (normalized.details / 10f) * usageDetail else 0f
    val intensity = normalized.intensity * usageIntensity
    val saturationNoise = ((normalized.saturation - 1.4f) / 0.6f).coerceIn(0f, 1f)
    val detailNoise = ((normalized.details - 7f) / 3f).coerceIn(0f, 1f)
    val minimumScrim = when (usage) {
        BackgroundUsage.Player -> 0.22f
        BackgroundUsage.List -> 0.55f
        BackgroundUsage.Lyrics -> 0.66f
    }
    val autoReadability = normalized.readabilityProtection.enabled && when (usage) {
        BackgroundUsage.Player -> true
        BackgroundUsage.List -> normalized.readabilityProtection.autoDimLists
        BackgroundUsage.Lyrics -> normalized.readabilityProtection.autoDimLyrics
    }
    val readabilityScrim = if (autoReadability) {
        (minimumScrim + saturationNoise * 0.08f + detailNoise * 0.08f).coerceIn(0f, 0.82f)
    } else {
        0.18f
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (normalized.blurred) {
                        listOf(
                            lerp(Color.Black, top, (0.30f + gradientMix * 0.18f) * intensity),
                            lerp(Ink, mid, (0.22f + gradientMix * 0.22f) * intensity),
                            Color.Black,
                        )
                    } else {
                        listOf(Ink, Color.Black)
                    },
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (detailAlpha > 0.01f) {
                val textureAlpha = detailAlpha * intensity
                drawCircle(top.copy(alpha = 0.10f * textureAlpha), size.minDimension * 0.42f, Offset(size.width * 0.16f, size.height * 0.18f))
                drawCircle(mid.copy(alpha = 0.09f * textureAlpha), size.minDimension * 0.54f, Offset(size.width * 0.86f, size.height * 0.42f))
                drawCircle(Color.White.copy(alpha = 0.020f * detailAlpha), size.minDimension * 0.36f, Offset(size.width * 0.44f, size.height * 0.68f))
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        gradientColor.copy(alpha = 0.04f + gradientMix * 0.22f),
                        gradientColor.copy(alpha = 0.10f + gradientMix * 0.24f),
                        Color.Black.copy(alpha = 0.18f + gradientMix * 0.20f),
                    ),
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = readabilityScrim),
                        Color.Black.copy(alpha = (readabilityScrim + 0.16f).coerceAtMost(0.90f)),
                        Color.Black.copy(alpha = (readabilityScrim + 0.04f).coerceAtMost(0.86f)),
                    ),
                ),
            ),
    )
}


internal fun tunedBackgroundColor(color: Color, settings: BackgroundSettings): Color {
    val saturation = settings.saturation.coerceIn(0f, 2f)
    val intensity = settings.intensity.coerceIn(0f, 2f)
    val gray = (color.red + color.green + color.blue) / 3f
    fun channel(value: Float): Float =
        (gray + (value - gray) * saturation)
            .let { it * (0.72f + intensity * 0.36f) }
            .coerceIn(0f, 1f)
    return Color(channel(color.red), channel(color.green), channel(color.blue), 1f)
}

internal fun normalizedHexColor(raw: String): String {
    val trimmed = raw.trim()
    val candidate = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    val valid = Regex("^#[0-9a-fA-F]{6}$").matches(candidate)
    return if (valid) candidate.uppercase(Locale.US) else "#000000"
}

internal fun colorFromHex(raw: String): Color {
    val value = normalizedHexColor(raw).removePrefix("#").toLong(16)
    return Color((0xFF000000L or value).toInt())
}

