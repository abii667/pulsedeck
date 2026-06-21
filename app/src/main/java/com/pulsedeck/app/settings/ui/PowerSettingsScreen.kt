package com.pulsedeck.app.settings.ui

import android.os.Build
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.R
import com.pulsedeck.app.AUDIO_EQ_INPUT_STEP_DB
import com.pulsedeck.app.AdvancedAudioTweaksSettings
import com.pulsedeck.app.AudioEngineState
import com.pulsedeck.app.AudioFocusSettings
import com.pulsedeck.app.BackgroundSettings
import com.pulsedeck.app.BluetoothFocusBehavior
import com.pulsedeck.app.BufferMode
import com.pulsedeck.app.CrossfadeSettings
import com.pulsedeck.app.DeviceProfile
import com.pulsedeck.app.DeckIcon
import com.pulsedeck.app.DirectVolumeControlSettings
import com.pulsedeck.app.DuckRequestAction
import com.pulsedeck.app.DvcVolumeSteps
import com.pulsedeck.app.FadeCurve
import com.pulsedeck.app.GraphicEqBand
import com.pulsedeck.app.GraphicEqRangeMode
import com.pulsedeck.app.IconTap
import com.pulsedeck.app.LimiterState
import com.pulsedeck.app.NativeCompareSlot
import com.pulsedeck.app.NATIVE_MASTER_GAIN_MAX_LINEAR
import com.pulsedeck.app.OutputBitDepth
import com.pulsedeck.app.OutputFallbackMode
import com.pulsedeck.app.OutputMode
import com.pulsedeck.app.OutputSampleRate
import com.pulsedeck.app.OutputSettings
import com.pulsedeck.app.ParametricEqBand
import com.pulsedeck.app.ParametricFilterType
import com.pulsedeck.app.PermanentFocusLossAction
import com.pulsedeck.app.PulseInfoPill
import com.pulsedeck.app.PulseInfoPillStyle
import com.pulsedeck.app.PulseIcon
import com.pulsedeck.app.ReadabilityProtectionSettings
import com.pulsedeck.app.ReplayGainMode
import com.pulsedeck.app.ReplayGainSettings
import com.pulsedeck.app.ReplayGainSource
import com.pulsedeck.app.ResamplerMode
import com.pulsedeck.app.ResamplerQuality
import com.pulsedeck.app.ResamplerSettings
import com.pulsedeck.app.ReverbState
import com.pulsedeck.app.StereoState
import com.pulsedeck.app.TempoPitchState
import com.pulsedeck.app.TransientFocusLossAction
import com.pulsedeck.app.activeProListeningExperience
import com.pulsedeck.app.applyPreset
import com.pulsedeck.app.audio.NativeAudioEngineBridge
import com.pulsedeck.app.audio.NativeAudioInitializationState
import com.pulsedeck.app.audio.nativeAudioActivationFor
import com.pulsedeck.app.audioDbSliderSteps
import com.pulsedeck.app.audioOutputGain
import com.pulsedeck.app.effectiveGraphicEqGainRange
import com.pulsedeck.app.graphicEqExtendedRangeEffective
import com.pulsedeck.app.linearToDb
import com.pulsedeck.app.nativeMasterGainConstraint
import com.pulsedeck.app.normalized
import com.pulsedeck.app.proListeningExperiencePlan
import com.pulsedeck.app.proListeningExperiences
import com.pulsedeck.app.pulseAudioPresets
import com.pulsedeck.app.quantizeAudioDb
import com.pulsedeck.app.recommendedProListeningExperience
import com.pulsedeck.app.toNativePresetSlot
import com.pulsedeck.app.withGraphicEqRangeMode
import com.pulsedeck.app.withGraphicEqBandGain
import com.pulsedeck.app.withNativePresetSlot
import com.pulsedeck.app.withParametricEqBand
import com.pulsedeck.app.settings.model.AlbumArtQuality
import com.pulsedeck.app.settings.model.AlbumArtSettings
import com.pulsedeck.app.settings.model.AlbumArtworkProviderId
import com.pulsedeck.app.settings.model.AlbumArtworkProviderMode
import com.pulsedeck.app.settings.model.AnimationSpeedSetting
import com.pulsedeck.app.settings.model.ButtonPressMode
import com.pulsedeck.app.settings.model.DiagnosticsSettings
import com.pulsedeck.app.settings.model.DefaultAlbumArtworkProviderOrder
import com.pulsedeck.app.settings.model.FullPlayerContentSettings
import com.pulsedeck.app.settings.model.HeadsetBluetoothSettings
import com.pulsedeck.app.settings.model.LibrarySettings
import com.pulsedeck.app.settings.model.LibrarySortMode
import com.pulsedeck.app.settings.model.LockScreenSettings
import com.pulsedeck.app.settings.model.LocalArtistDiscoveryPolicy
import com.pulsedeck.app.settings.model.LookAndFeelSettings
import com.pulsedeck.app.settings.model.MediaAction
import com.pulsedeck.app.settings.model.MediaButtonSettings
import com.pulsedeck.app.settings.model.MiscSettings
import com.pulsedeck.app.settings.model.MiniPlayerAppearanceSettings
import com.pulsedeck.app.settings.model.MiniPlayerAppearanceStyle
import com.pulsedeck.app.settings.model.PlayerButtonAction
import com.pulsedeck.app.settings.model.PlayerButtonSize
import com.pulsedeck.app.settings.model.PlayerUiSettings
import com.pulsedeck.app.settings.model.PremiumDeckSmartRecommendationMode
import com.pulsedeck.app.settings.model.PulseSettingsState
import com.pulsedeck.app.settings.model.ScreenOrientationSetting
import com.pulsedeck.app.settings.model.SettingKey
import com.pulsedeck.app.settings.model.SettingScope
import com.pulsedeck.app.settings.model.SettingsIntegrationStatus
import com.pulsedeck.app.settings.model.SettingsTheme
import com.pulsedeck.app.settings.model.SettingsFont
import com.pulsedeck.app.settings.model.SpectrumMode
import com.pulsedeck.app.settings.model.StreamPreviewNetworkPolicy
import com.pulsedeck.app.settings.model.StreamingQualityPolicy
import com.pulsedeck.app.settings.model.PulseDeckThirdPartyLicenses
import com.pulsedeck.app.settings.model.ThirdPartyNotice
import com.pulsedeck.app.settings.model.VisualizationSettings
import com.pulsedeck.app.settings.model.pulseSettingsCatalog
import com.pulsedeck.app.settings.model.settingsSearchMatches
import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.PulseBluetoothCommandLogStore
import com.pulsedeck.app.settings.runtime.toPlaybackRuntimeSettings
import com.pulsedeck.app.withReplayGainSettings
import androidx.core.content.pm.PackageInfoCompat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private data class SettingsAppearance(
    val bg: Color,
    val bar: Color,
    val surface: Color,
    val line: Color,
    val text: Color,
    val subtext: Color,
    val muted: Color,
    val disabled: Color,
    val accent: Color,
    val blue: Color,
    val titleWeight: FontWeight,
    val bodyWeight: FontWeight,
    val chromeWeight: FontWeight,
)

private val LocalSettingsAppearance = staticCompositionLocalOf {
    SettingsAppearance(
        bg = Color(0xFF08090B),
        bar = Color(0xFF111218),
        surface = Color(0xFF15161B),
        line = Color.White.copy(alpha = 0.10f),
        text = Color(0xFFF7F4EF),
        subtext = Color(0xFFB4B0AE),
        muted = Color(0xFF77737A),
        disabled = Color(0xFF4E4B52),
        accent = Color(0xFFFF7A4B),
        blue = Color(0xFF4A82FF),
        titleWeight = FontWeight.Bold,
        bodyWeight = FontWeight.SemiBold,
        chromeWeight = FontWeight.Bold,
    )
}

private val SettingsBg: Color
    @Composable get() = LocalSettingsAppearance.current.bg
private val SettingsBar: Color
    @Composable get() = LocalSettingsAppearance.current.bar
private val SettingsSurface: Color
    @Composable get() = LocalSettingsAppearance.current.surface
private val SettingsLine: Color
    @Composable get() = LocalSettingsAppearance.current.line
private val SettingsText: Color
    @Composable get() = LocalSettingsAppearance.current.text
private val SettingsSubtext: Color
    @Composable get() = LocalSettingsAppearance.current.subtext
private val SettingsMuted: Color
    @Composable get() = LocalSettingsAppearance.current.muted
private val SettingsDisabled: Color
    @Composable get() = LocalSettingsAppearance.current.disabled
private val PulseAccent: Color
    @Composable get() = LocalSettingsAppearance.current.accent
private val PulseBlue: Color
    @Composable get() = LocalSettingsAppearance.current.blue
private val SettingsTitleWeight: FontWeight
    @Composable get() = LocalSettingsAppearance.current.titleWeight
private val SettingsBodyWeight: FontWeight
    @Composable get() = LocalSettingsAppearance.current.bodyWeight
private val SettingsChromeWeight: FontWeight
    @Composable get() = LocalSettingsAppearance.current.chromeWeight

private object SettingsMotion {
    object Duration {
        const val Tap = 110
        const val PopupIn = 190
        const val PopupOut = 130
        const val Panel = 320
        const val Selection = 150
    }

    object Easing {
        val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    }

    object Distance {
        val PanelSlide = 32.dp
        val SearchShift = 10.dp
    }
}

private data class SettingsMotionSpec(
    val durationScale: Float = 1f,
    val disabled: Boolean = false,
) {
    fun duration(baseMillis: Int): Int =
        if (disabled) 0 else (baseMillis * durationScale).roundToInt().coerceAtLeast(1)

    companion object {
        val Default = SettingsMotionSpec()

        fun from(setting: AnimationSpeedSetting): SettingsMotionSpec =
            when (setting) {
                AnimationSpeedSetting.Disabled -> SettingsMotionSpec(durationScale = 0f, disabled = true)
                AnimationSpeedSetting.Fast -> SettingsMotionSpec(durationScale = 0.70f)
                AnimationSpeedSetting.Default -> Default
            }
    }
}

private val LocalSettingsMotionSpec = staticCompositionLocalOf { SettingsMotionSpec.Default }

private val SkinOptions = listOf(
    "pulsedeck_default" to "PulseDeck",
    "warm_glass" to "Warm Glass",
    "midnight_blue" to "Midnight",
    "high_contrast" to "High Contrast",
)

private val LanguageOptions = listOf(
    "auto" to "Auto",
    "en" to "English",
    "am" to "Amharic",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
)

private val AppIconOptions = listOf(
    "default" to "Default",
    "dark" to "Dark",
    "amber" to "Amber",
    "mono" to "Mono",
)

private val PlayerButtonAddActions = PlayerButtonAction.entries
    .filterNot { it == PlayerButtonAction.None || it == PlayerButtonAction.Queue }
private const val MaxPlayerUtilityButtons = 6
private val HiddenPlayerUtilityButtons = List(MaxPlayerUtilityButtons) { PlayerButtonAction.None }

private enum class PlayerSurfacePreviewOrientation {
    Portrait,
    Landscape,
}

private data class PlayerButtonActionGroup(
    val title: String,
    val icon: DeckIcon,
    val actions: List<PlayerButtonAction>,
)

private data class PlayerButtonPreset(
    val title: String,
    val subtitle: String,
    val icon: DeckIcon,
    val actions: List<PlayerButtonAction>,
)

private val PlayerButtonActionGroups = listOf(
    PlayerButtonActionGroup(
        title = "Playback",
        icon = DeckIcon.Play,
        actions = listOf(
            PlayerButtonAction.PlayPause,
            PlayerButtonAction.NextTrack,
            PlayerButtonAction.PreviousTrack,
            PlayerButtonAction.Repeat,
            PlayerButtonAction.Shuffle,
            PlayerButtonAction.SleepTimer,
            PlayerButtonAction.SeekBack10,
            PlayerButtonAction.SeekForward10,
            PlayerButtonAction.TrackMenu,
        ),
    ),
    PlayerButtonActionGroup(
        title = "Information",
        icon = DeckIcon.Info,
        actions = listOf(
            PlayerButtonAction.Lyrics,
            PlayerButtonAction.Like,
            PlayerButtonAction.Bookmark,
            PlayerButtonAction.Info,
            PlayerButtonAction.Visualization,
            PlayerButtonAction.CurrentCategory,
            PlayerButtonAction.Library,
            PlayerButtonAction.Search,
            PlayerButtonAction.AudioSettings,
            PlayerButtonAction.ClosePlayer,
        ),
    ),
)

private val PlayerButtonPresets = listOf(
    PlayerButtonPreset(
        title = "Player Controls",
        subtitle = "Essential playback",
        icon = DeckIcon.Play,
        actions = listOf(
            PlayerButtonAction.PlayPause,
            PlayerButtonAction.PreviousTrack,
            PlayerButtonAction.NextTrack,
            PlayerButtonAction.Repeat,
            PlayerButtonAction.Shuffle,
        ),
    ),
    PlayerButtonPreset(
        title = "Social / Extra",
        subtitle = "Engage and discover",
        icon = DeckIcon.Heart,
        actions = listOf(
            PlayerButtonAction.Like,
            PlayerButtonAction.Bookmark,
            PlayerButtonAction.Info,
            PlayerButtonAction.Lyrics,
            PlayerButtonAction.Visualization,
        ),
    ),
    PlayerButtonPreset(
        title = "Minimal",
        subtitle = "Clean and simple",
        icon = DeckIcon.Check,
        actions = listOf(
            PlayerButtonAction.SleepTimer,
            PlayerButtonAction.Repeat,
            PlayerButtonAction.Shuffle,
        ),
    ),
)

private val UserAgentOptions = listOf(
    "" to "Default",
    "PulseDeck/1.0 Android Media3" to "PulseDeck",
    "Mozilla/5.0 (Linux; Android) PulseDeck/1.0" to "Mobile",
    "Mozilla/5.0 PulseDeckDesktopCompat/1.0" to "Desktop",
)

private val StreamingQualityOptions = StreamingQualityPolicy.entries.map { it.name to it.label }

private val StreamPreviewNetworkOptions = StreamPreviewNetworkPolicy.entries.map { it.name to it.label }

private val LocalArtistDiscoveryOptions = LocalArtistDiscoveryPolicy.entries.map { it.name to it.label }

private enum class SettingsRoute(
    val title: String,
    val subtitle: String,
    val scope: SettingScope,
    @DrawableRes val iconRes: Int,
    val fullColorIcon: Boolean = false,
) {
    Home("Settings", "Settings operating system", SettingScope.Home, R.drawable.iconoir_control_slider),
    LookAndFeel("Appearance", "Skin, interface, language, notifications", SettingScope.LookAndFeel, R.drawable.settings_look_feel_premium, true),
    Audio("Audio", "EQ, replay gain, output, focus, fades", SettingScope.Audio, R.drawable.settings_audio_premium, true),
    Visualization("Visualization", "Spectrum, presets, opacity, FPS", SettingScope.Visualization, R.drawable.settings_visualization_premium, true),
    Background("Backdrop", "Blur, detail, intensity, saturation", SettingScope.Background, R.drawable.settings_background_premium, true),
    AlbumArt("Album Art", "Download, quality, cache cleanup", SettingScope.AlbumArt, R.drawable.settings_album_art_premium, true),
    Library("Library", "Rescan, folders, queue and list behavior", SettingScope.Library, R.drawable.settings_library_premium, true),
    HeadsetBluetooth("Bluetooth Audio", "Connection and media button behavior", SettingScope.HeadsetBluetooth, R.drawable.settings_headset_bluetooth_premium, true),
    LockScreen("Lock Screen Controls", "Artwork and lock-screen behavior", SettingScope.LockScreen, R.drawable.settings_lock_screen_premium, true),
    Misc("Network & Online", "Data Saver, Offline Deck, lyrics, streaming quality", SettingScope.Misc, R.drawable.settings_misc_premium, true),
    About("About", "Version, diagnostics, support", SettingScope.About, R.drawable.settings_about_premium, true),
    Licenses("Licenses & Open Source", "Runtime third-party license inventory", SettingScope.About, R.drawable.settings_about_premium, true),
    ImportExport("Import/Export", "Backup, restore, validation", SettingScope.ImportExport, R.drawable.settings_export_data_premium, true),
}

private enum class AudioSettingsRoute(
    val title: String,
    val subtitle: String,
    val status: (AudioEngineState) -> String,
) {
    Index("Audio", "EQ, replay gain, output, focus, fades", { if (it.processingActive) "Processing on" else "Transparent path" }),
    AudioInfo("Audio Info", "Signal chain, DSP, output, and buffer diagnostics", { "${it.output.mode.label} / ${if (it.clippingRisk) "Headroom" else "Safe"}" }),
    Experiences("Pro Listening Experiences", "Guided route-aware profiles with preview-ready safety notes", { activeProListeningExperience(it)?.title ?: "Guided" }),
    Crossfade("Crossfade, Fade, and Gapless", "Gapless, fades, transition length, and album safeguards", { if (it.crossfade.crossfadeEnabled) "${it.crossfade.durationMs / 1000}s ${it.crossfade.fadeCurve.label}" else "Gapless ${if (it.crossfade.gaplessEnabled) "on" else "off"}" }),
    ReplayGain("Replay Gain", "Loudness matching, clipping prevention, and preamp rules", { it.replayGainMode.label }),
    AudioFocus("Audio Focus", "Calls, notifications, ducking, resume, and Bluetooth focus policy", { "${it.audioFocus.onTransientLoss.label} transient" }),
    Equalizer("Equalizer", "Presets, graphic bands, parametric bands, tone, limiter, and effects", { "${it.eqBands.size}-band ${if (it.eqEnabled) "on" else "off"}" }),
    Resampler("Resampler", "Sample-rate conversion, quality, dither, and battery behavior", { "${it.resampler.mode.label} / ${it.resampler.quality.label}" }),
    Dvc("Direct Volume Control", "Headroom, Bluetooth absolute volume behavior, and compatibility mode", { if (it.dvc.enabled) "Enabled" else "Off" }),
    Output("Output", "Media3/AudioTrack, Hi-Res attempts, buffer mode, and route fallback", { "${it.output.mode.label} / ${it.output.deviceProfile.label}" }),
    Chromecast("Chromecast Output", "Remote playback route, metadata, delay, and local DSP limitations", { "Remote gated" }),
    AdvancedTweaks("Advanced Tweaks", "MusicFX, safe mode, debug info, and reset actions", { if (it.advancedTweaks.safeMode) "Safe mode" else "Standard" }),
}

private data class SettingsPaneTarget(
    val route: SettingsRoute,
    val audioRoute: AudioSettingsRoute,
) {
    val motionRank: Int
        get() = route.ordinal * 20 + if (route == SettingsRoute.Audio) audioRoute.ordinal else 0
}

enum class SettingsLaunchTarget {
    Home,
    Audio,
}

private fun settingsAppearanceFor(look: LookAndFeelSettings, systemDark: Boolean): SettingsAppearance {
    val effectiveTheme = when {
        look.followSystemTheme -> if (systemDark) SettingsTheme.Dark else SettingsTheme.Light
        look.settingsTheme == SettingsTheme.FollowSystem -> if (systemDark) SettingsTheme.Dark else SettingsTheme.Light
        look.settingsTheme == SettingsTheme.Light -> SettingsTheme.Light
        else -> SettingsTheme.Dark
    }
    val type = when (look.settingsFont) {
        SettingsFont.Default -> Triple(FontWeight.SemiBold, FontWeight.Normal, FontWeight.SemiBold)
        SettingsFont.Alternative -> Triple(FontWeight.Medium, FontWeight.Normal, FontWeight.Medium)
        SettingsFont.Bold -> Triple(FontWeight.Bold, FontWeight.SemiBold, FontWeight.Bold)
        SettingsFont.BoldPlus -> Triple(FontWeight.Black, FontWeight.Bold, FontWeight.Black)
    }
    val accent = when (look.skinId) {
        "warm_glass" -> Color(0xFFE06F3E)
        "midnight_blue" -> Color(0xFF6D8DFF)
        "high_contrast" -> Color(0xFFFFC83D)
        else -> Color(0xFFFF7A4B)
    }
    val blue = when (look.skinId) {
        "warm_glass" -> Color(0xFF2F67D8)
        "midnight_blue" -> Color(0xFF86A6FF)
        "high_contrast" -> Color(0xFF7DB1FF)
        else -> Color(0xFF4A82FF)
    }
    return if (effectiveTheme == SettingsTheme.Light) {
        SettingsAppearance(
            bg = if (look.skinId == "warm_glass") Color(0xFFFFF3EA) else Color(0xFFF6F3EE),
            bar = if (look.skinId == "midnight_blue") Color(0xFFEFF3FF) else Color(0xFFFFFFFF),
            surface = if (look.skinId == "high_contrast") Color(0xFFE7E7E7) else Color(0xFFECE7DF),
            line = Color.Black.copy(alpha = if (look.skinId == "high_contrast") 0.22f else 0.12f),
            text = Color(0xFF151515),
            subtext = Color(0xFF55504C),
            muted = Color(0xFF7E7770),
            disabled = Color(0xFFB0AAA3),
            accent = accent,
            blue = blue,
            titleWeight = type.first,
            bodyWeight = type.second,
            chromeWeight = type.third,
        )
    } else {
        SettingsAppearance(
            bg = when (look.skinId) {
                "warm_glass" -> Color(0xFF120D0A)
                "midnight_blue" -> Color(0xFF050813)
                "high_contrast" -> Color.Black
                else -> Color(0xFF08090B)
            },
            bar = when (look.skinId) {
                "warm_glass" -> Color(0xFF1B130F)
                "midnight_blue" -> Color(0xFF0A1024)
                "high_contrast" -> Color(0xFF050505)
                else -> Color(0xFF111218)
            },
            surface = when (look.skinId) {
                "warm_glass" -> Color(0xFF241916)
                "midnight_blue" -> Color(0xFF11182F)
                "high_contrast" -> Color(0xFF101010)
                else -> Color(0xFF15161B)
            },
            line = Color.White.copy(alpha = if (look.skinId == "high_contrast") 0.24f else 0.10f),
            text = Color(0xFFF7F4EF),
            subtext = if (look.skinId == "high_contrast") Color(0xFFD8D8D8) else Color(0xFFB4B0AE),
            muted = if (look.skinId == "high_contrast") Color(0xFFA4A4A4) else Color(0xFF77737A),
            disabled = if (look.skinId == "high_contrast") Color(0xFF6D6D6D) else Color(0xFF4E4B52),
            accent = accent,
            blue = blue,
            titleWeight = type.first,
            bodyWeight = type.second,
            chromeWeight = type.third,
        )
    }
}

@Composable
fun PowerSettingsScreen(
    settings: PulseSettingsState,
    onSettings: (PulseSettingsState) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    launchTarget: SettingsLaunchTarget = SettingsLaunchTarget.Home,
    launchRequestKey: Int = 0,
    backRequestKey: Int = 0,
    onExportSettings: () -> Unit = {},
    onExportPrivateBackup: () -> Unit = {},
    onImportSettings: () -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    albumArtCacheStatus: String = "Cache status unavailable",
    onlineArtworkProviderConfigured: Boolean = false,
    onRefreshArtwork: () -> Unit = {},
    onClearArtworkCache: () -> Unit = {},
    onClearOnlineCache: () -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onClearRecentlyPlayed: () -> Unit = {},
    onResetPremiumDeckPersonalization: () -> Unit = {},
    premiumDeckModelStatusTitle: String = "PremiumDeck Model Status",
    premiumDeckModelStatusBody: String = "Model status unavailable.",
    onDeleteDownloads: () -> Unit = {},
) {
    val normalized = settings.normalized()
    val appearance = settingsAppearanceFor(normalized.lookAndFeel, isSystemInDarkTheme())
    var routeName by rememberSaveable { mutableStateOf(SettingsRoute.Home.name) }
    var audioRouteName by rememberSaveable { mutableStateOf(AudioSettingsRoute.Index.name) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val route = SettingsRoute.entries.firstOrNull { it.name == routeName } ?: SettingsRoute.Home
    val audioRoute = AudioSettingsRoute.entries.firstOrNull { it.name == audioRouteName } ?: AudioSettingsRoute.Index
    val scrollStates = remember { mutableStateMapOf<String, LazyListState>() }
    fun stateFor(key: String): LazyListState =
        scrollStates.getOrPut(key) { LazyListState() }
    fun stateFor(next: SettingsRoute): LazyListState = stateFor(next.name)
    fun stateForAudio(next: AudioSettingsRoute): LazyListState = stateFor("Audio:${next.name}")

    fun update(next: PulseSettingsState) {
        onSettings(next.normalized())
    }

    LaunchedEffect(launchTarget, launchRequestKey) {
        routeName = when (launchTarget) {
            SettingsLaunchTarget.Home -> SettingsRoute.Home.name
            SettingsLaunchTarget.Audio -> SettingsRoute.Audio.name
        }
        audioRouteName = AudioSettingsRoute.Index.name
        query = ""
        searchOpen = false
    }

    fun openRoute(next: SettingsRoute) {
        routeName = next.name
        if (next == SettingsRoute.Audio) audioRouteName = AudioSettingsRoute.Index.name
        query = ""
        searchOpen = false
    }

    fun handleBack() {
        if (route == SettingsRoute.Audio && audioRoute != AudioSettingsRoute.Index) {
            audioRouteName = AudioSettingsRoute.Index.name
            query = ""
            searchOpen = false
        } else if (route != SettingsRoute.Home) {
            routeName = SettingsRoute.Home.name
            query = ""
            searchOpen = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(backRequestKey) {
        if (backRequestKey > 0) handleBack()
    }

    val activeTitle = if (route == SettingsRoute.Audio) audioRoute.title else route.title
    val motion = remember(normalized.lookAndFeel.animationSpeed) {
        SettingsMotionSpec.from(normalized.lookAndFeel.animationSpeed)
    }

    CompositionLocalProvider(
        LocalSettingsMotionSpec provides motion,
        LocalSettingsAppearance provides appearance,
    ) {
    BoxWithConstraints(modifier.fillMaxSize().background(SettingsBg)) {
        val twoPane = maxWidth >= 720.dp
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .background(SettingsBar),
                ) {
                    SettingsTopBar(
                        title = "Settings",
                        searchOpen = searchOpen,
                        query = query,
                        onQuery = { query = it },
                        onSearchToggle = { searchOpen = !searchOpen },
                        onBack = onBack,
                    )
                    SettingsHomeList(
                        settings = normalized,
                        query = query,
                        selectedRoute = route,
                        listState = stateFor(SettingsRoute.Home),
                        onSettings = ::update,
                        onRoute = ::openRoute,
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(SettingsLine))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SettingsTopBar(
                        title = if (route == SettingsRoute.Home) "Appearance" else activeTitle,
                        searchOpen = searchOpen,
                        query = query,
                        onQuery = { query = it },
                        onSearchToggle = { searchOpen = !searchOpen },
                        onBack = {
                            if (route == SettingsRoute.Audio && audioRoute != AudioSettingsRoute.Index) {
                                audioRouteName = AudioSettingsRoute.Index.name
                            } else {
                                routeName = SettingsRoute.Home.name
                            }
                        },
                    )
                    val detailRoute = if (route == SettingsRoute.Home) SettingsRoute.LookAndFeel else route
                    SettingsPaneTransition(
                        target = SettingsPaneTarget(detailRoute, audioRoute),
                        modifier = Modifier.weight(1f),
                    ) { target ->
                        SettingsDetail(
                            route = target.route,
                            audioRoute = target.audioRoute,
                            settings = normalized,
                            query = query,
                            listState = if (target.route == SettingsRoute.Audio) stateForAudio(target.audioRoute) else stateFor(target.route),
                            onSettings = ::update,
                            onRoute = ::openRoute,
                            onAudioRoute = { audioRouteName = it.name },
                            onExportSettings = onExportSettings,
                            onExportPrivateBackup = onExportPrivateBackup,
                            onImportSettings = onImportSettings,
                            onExportDiagnostics = onExportDiagnostics,
                            albumArtCacheStatus = albumArtCacheStatus,
                            onlineArtworkProviderConfigured = onlineArtworkProviderConfigured,
                            onRefreshArtwork = onRefreshArtwork,
                            onClearArtworkCache = onClearArtworkCache,
                            onClearOnlineCache = onClearOnlineCache,
                            onClearSearchHistory = onClearSearchHistory,
                            onClearRecentlyPlayed = onClearRecentlyPlayed,
                            onResetPremiumDeckPersonalization = onResetPremiumDeckPersonalization,
                            premiumDeckModelStatusTitle = premiumDeckModelStatusTitle,
                            premiumDeckModelStatusBody = premiumDeckModelStatusBody,
                            onDeleteDownloads = onDeleteDownloads,
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SettingsTopBar(
                    title = activeTitle,
                    searchOpen = searchOpen,
                    query = query,
                    onQuery = { query = it },
                    onSearchToggle = { searchOpen = !searchOpen },
                    onBack = ::handleBack,
                )
                SettingsPaneTransition(
                    target = SettingsPaneTarget(route, audioRoute),
                    modifier = Modifier.weight(1f),
                ) { target ->
                    if (target.route == SettingsRoute.Home) {
                        SettingsHomeList(
                            settings = normalized,
                            query = query,
                            selectedRoute = target.route,
                            listState = stateFor(SettingsRoute.Home),
                            onSettings = ::update,
                            onRoute = ::openRoute,
                        )
                    } else {
                        SettingsDetail(
                            route = target.route,
                            audioRoute = target.audioRoute,
                            settings = normalized,
                            query = query,
                            listState = if (target.route == SettingsRoute.Audio) stateForAudio(target.audioRoute) else stateFor(target.route),
                            onSettings = ::update,
                            onRoute = ::openRoute,
                            onAudioRoute = { audioRouteName = it.name },
                            onExportSettings = onExportSettings,
                            onExportPrivateBackup = onExportPrivateBackup,
                            onImportSettings = onImportSettings,
                            onExportDiagnostics = onExportDiagnostics,
                            albumArtCacheStatus = albumArtCacheStatus,
                            onlineArtworkProviderConfigured = onlineArtworkProviderConfigured,
                            onRefreshArtwork = onRefreshArtwork,
                            onClearArtworkCache = onClearArtworkCache,
                            onClearOnlineCache = onClearOnlineCache,
                            onClearSearchHistory = onClearSearchHistory,
                            onClearRecentlyPlayed = onClearRecentlyPlayed,
                            onResetPremiumDeckPersonalization = onResetPremiumDeckPersonalization,
                            premiumDeckModelStatusTitle = premiumDeckModelStatusTitle,
                            premiumDeckModelStatusBody = premiumDeckModelStatusBody,
                            onDeleteDownloads = onDeleteDownloads,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SettingsPaneTransition(
    target: SettingsPaneTarget,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsPaneTarget) -> Unit,
) {
    val motion = LocalSettingsMotionSpec.current
    val density = LocalDensity.current
    val slideDistance = with(density) { SettingsMotion.Distance.PanelSlide.toPx() }
    var currentTarget by remember { mutableStateOf(target) }
    var outgoingTarget by remember { mutableStateOf<SettingsPaneTarget?>(null) }
    var direction by remember { mutableStateOf(1) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target != currentTarget) {
            direction = if (target.motionRank >= currentTarget.motionRank) 1 else -1
            outgoingTarget = currentTarget
            currentTarget = target
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = motion.duration(SettingsMotion.Duration.Panel),
                    easing = SettingsMotion.Easing.Emphasized,
                ),
            )
            outgoingTarget = null
        }
    }

    val t = progress.value.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize()) {
        outgoingTarget?.let { outgoing ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - (t / 0.72f)).coerceIn(0f, 1f)
                        translationX = -direction * slideDistance * t
                    },
            ) {
                content(outgoing)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = ((t - 0.08f) / 0.92f).coerceIn(0f, 1f)
                    translationX = direction * slideDistance * (1f - t)
                },
        ) {
            content(currentTarget)
        }
    }
}

@Composable
private fun Modifier.settingsPressEffect(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val motion = LocalSettingsMotionSpec.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && !motion.disabled && pressed) 0.982f else 1f,
        animationSpec = tween(
            durationMillis = motion.duration(SettingsMotion.Duration.Tap),
            easing = SettingsMotion.Easing.Standard,
        ),
        label = "settingsPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    searchOpen: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onBack: () -> Unit,
) {
    val motion = LocalSettingsMotionSpec.current
    val searchProgress by animateFloatAsState(
        targetValue = if (searchOpen) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.duration(if (searchOpen) SettingsMotion.Duration.PopupIn else SettingsMotion.Duration.PopupOut),
            easing = SettingsMotion.Easing.Standard,
        ),
        label = "settingsSearchProgress",
    )
    val searchShift = with(LocalDensity.current) { SettingsMotion.Distance.SearchShift.toPx() }
    val backInteraction = remember { MutableInteractionSource() }
    val searchInteraction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .background(SettingsBar)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTap(DeckIcon.Back, onBack, 42.dp)
            Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = title,
                    color = SettingsText,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = SettingsTitleWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(interactionSource = backInteraction, indication = null, onClick = onBack)
                        .padding(vertical = 4.dp)
                        .graphicsLayer {
                            alpha = 1f - searchProgress
                            translationX = -searchShift * searchProgress
                        },
                )
                if (searchOpen || searchProgress > 0.01f) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = SettingsText, fontSize = 17.sp, fontWeight = SettingsBodyWeight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = searchProgress
                                translationX = searchShift * (1f - searchProgress)
                            }
                            .clip(RoundedCornerShape(18.dp))
                            .background(SettingsSurface)
                            .border(1.dp, SettingsLine, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            if (query.isBlank()) Text("Search settings", color = SettingsMuted, fontSize = 17.sp, fontWeight = SettingsBodyWeight)
                            inner()
                        },
                    )
                }
            }
            Text(
                text = if (searchOpen) "X" else "?",
                color = SettingsText,
                fontSize = 23.sp,
                fontWeight = SettingsChromeWeight,
                modifier = Modifier
                    .clip(CircleShape)
                    .settingsPressEffect(searchInteraction)
                    .clickable(interactionSource = searchInteraction, indication = null, onClick = onSearchToggle)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsHomeList(
    settings: PulseSettingsState,
    query: String,
    selectedRoute: SettingsRoute,
    listState: LazyListState,
    onSettings: (PulseSettingsState) -> Unit,
    onRoute: (SettingsRoute) -> Unit,
) {
    val catalog = remember(settings) { pulseSettingsCatalog(settings) }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 22.dp, top = 16.dp, end = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (query.isBlank()) {
            item { SectionLabel("Settings") }
            items(SettingsRoute.entries.filterNot { it == SettingsRoute.Home || it == SettingsRoute.ImportExport }) { route ->
                SettingNavigationRow(
                    title = route.title,
                    subtitle = route.subtitle,
                    leadingIconRes = route.iconRes,
                    leadingIconFullColor = route.fullColorIcon,
                    selected = route == selectedRoute,
                    onClick = { onRoute(route) },
                )
            }
            item { SectionLabel("Other") }
            item {
                SettingNavigationRow(
                    title = "Export Portable Settings",
                    subtitle = "Versioned JSON profile with private paths and secrets redacted",
                    leadingIconRes = R.drawable.settings_export_data_premium,
                    leadingIconFullColor = true,
                    onClick = { onRoute(SettingsRoute.ImportExport) },
                )
            }
            item {
                SettingNavigationRow(
                    title = "Import Settings/Backup",
                    subtitle = "Validate, sanitize, migrate, then apply",
                    leadingIconRes = R.drawable.settings_import_data_premium,
                    leadingIconFullColor = true,
                    onClick = { onRoute(SettingsRoute.ImportExport) },
                )
            }
            item {
                SettingActionRow(
                    title = "Get Support",
                    subtitle = "Open support diagnostics and troubleshooting notes",
                    leadingIconRes = R.drawable.settings_support_premium,
                    leadingIconFullColor = true,
                    onClick = { onRoute(SettingsRoute.About) },
                )
            }
            item {
                SettingSwitchRow(
                    title = "Send Errors To Developer",
                    subtitle = "Suggest sending a privacy-redacted crash log",
                    checked = settings.diagnostics.sendErrorsToDeveloper,
                    onCheckedChange = {
                        onSettings(settings.copy(diagnostics = settings.diagnostics.copy(sendErrorsToDeveloper = it)))
                    },
                    leadingIconRes = R.drawable.settings_send_errors_premium,
                    leadingIconFullColor = true,
                )
            }
        } else {
            item { SectionLabel("Search Results") }
            val matches = catalog.filter { settingsSearchMatches(it, query) }
            if (matches.isEmpty()) {
                item { SettingInfoBlock("No Results", "Try audio, bluetooth, album art, crossfade, replay gain, lock screen, or export.") }
            } else {
                items(matches, key = { it.key.id }) { spec ->
                    val integrationStatus = spec.integrationStatus
                    val statusText = when (integrationStatus) {
                        SettingsIntegrationStatus.Available -> null
                        is SettingsIntegrationStatus.DeviceDependent -> "Device dependent: ${integrationStatus.reason}"
                        is SettingsIntegrationStatus.Gated -> "Gated: ${integrationStatus.reason}"
                        is SettingsIntegrationStatus.NotImplementedYet -> "Unavailable: ${integrationStatus.reason}"
                    }
                    val routeForScope = if (spec.key == SettingKey.AboutLicenses) SettingsRoute.Licenses else scopeRoute(spec.scope)
                    val routeEnabled = routeForScope != null && integrationStatus !is SettingsIntegrationStatus.NotImplementedYet
                    val subtitle = listOfNotNull(
                        spec.scope.title,
                        spec.subtitle,
                        statusText,
                    ).joinToString(" - ")
                    SettingNavigationRow(
                        title = spec.title,
                        subtitle = subtitle,
                        leadingIconRes = scopeIconRes(spec.scope),
                        leadingIconFullColor = scopeIconFullColor(spec.scope),
                        enabled = routeEnabled,
                        disabledReason = statusText,
                        onClick = { routeForScope?.let(onRoute) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDetail(
    route: SettingsRoute,
    audioRoute: AudioSettingsRoute,
    settings: PulseSettingsState,
    query: String,
    listState: LazyListState,
    onSettings: (PulseSettingsState) -> Unit,
    onRoute: (SettingsRoute) -> Unit,
    onAudioRoute: (AudioSettingsRoute) -> Unit,
    onExportSettings: () -> Unit,
    onExportPrivateBackup: () -> Unit,
    onImportSettings: () -> Unit,
    onExportDiagnostics: () -> Unit,
    albumArtCacheStatus: String,
    onlineArtworkProviderConfigured: Boolean,
    onRefreshArtwork: () -> Unit,
    onClearArtworkCache: () -> Unit,
    onClearOnlineCache: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearRecentlyPlayed: () -> Unit,
    onResetPremiumDeckPersonalization: () -> Unit,
    premiumDeckModelStatusTitle: String,
    premiumDeckModelStatusBody: String,
    onDeleteDownloads: () -> Unit,
) {
    val include: (String) -> Boolean = remember(query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) {
            { true }
        } else {
            { text -> text.lowercase().contains(needle) }
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 22.dp, top = 18.dp, end = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when (route) {
            SettingsRoute.LookAndFeel -> lookAndFeelItems(settings, include, onSettings)
            SettingsRoute.Audio -> audioItems(settings, include, onSettings, audioRoute, onAudioRoute)
            SettingsRoute.Visualization -> visualizationItems(settings, include, onSettings)
            SettingsRoute.Background -> backgroundItems(settings, include, onSettings)
            SettingsRoute.AlbumArt -> albumArtItems(settings, include, onSettings, albumArtCacheStatus, onlineArtworkProviderConfigured, onRefreshArtwork, onClearArtworkCache)
            SettingsRoute.Library -> libraryItems(
                settings = settings,
                include = include,
                onSettings = onSettings,
            )
            SettingsRoute.HeadsetBluetooth -> headsetItems(settings, include, onSettings)
            SettingsRoute.LockScreen -> lockItems(settings, include, onSettings)
            SettingsRoute.Misc -> miscItems(settings, include, onSettings, onClearOnlineCache, onClearSearchHistory, onClearRecentlyPlayed, onResetPremiumDeckPersonalization, premiumDeckModelStatusTitle, premiumDeckModelStatusBody, onDeleteDownloads)
            SettingsRoute.About -> aboutItems(settings, include, onSettings, onExportDiagnostics, onOpenLicenses = { onRoute(SettingsRoute.Licenses) })
            SettingsRoute.Licenses -> licenseItems(include)
            SettingsRoute.ImportExport -> importExportItems(settings, include, onSettings, onExportSettings, onExportPrivateBackup, onImportSettings, onExportDiagnostics)
            SettingsRoute.Home -> lookAndFeelItems(settings, include, onSettings)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lookAndFeelItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
) {
    val look = settings.lookAndFeel
    val playerUi = settings.playerUi.normalized()
    val miniAppearance = playerUi.miniPlayerAppearance.normalized()
    val fullPlayerContent = playerUi.fullPlayerContent.normalized()
    fun updatePlayerUi(next: PlayerUiSettings) {
        onSettings(settings.copy(playerUi = next.normalized()))
    }
    fun updateMiniAppearance(next: MiniPlayerAppearanceSettings) {
        updatePlayerUi(playerUi.copy(miniPlayerAppearance = next.normalized()))
    }
    fun updateFullPlayerContent(next: FullPlayerContentSettings) {
        updatePlayerUi(playerUi.copy(fullPlayerContent = next.normalized()))
    }
    fun percentLabel(value: Float): String = "${(value * 100f).roundToInt()}%"

    item { SectionLabel("Skin") }
    item {
        OptionPickerRow(
            title = "Skin",
            options = SkinOptions,
            selectedValue = look.skinId,
            onSelected = { onSettings(settings.copy(lookAndFeel = look.copy(skinId = it))) },
        )
    }
    if (include("Follow Day/Night Mode")) item {
        SettingSwitchRow("Follow Day/Night Mode", "Follow the system theme when the selected skin supports it", look.followSystemTheme, {
            onSettings(settings.copy(lookAndFeel = look.copy(followSystemTheme = it)))
        })
    }
    item {
        SettingSegmentedRow(
            title = "Settings Theme",
            options = SettingsTheme.entries.map { it.label },
            selected = look.settingsTheme.label,
            onSelected = { label -> onSettings(settings.copy(lookAndFeel = look.copy(settingsTheme = SettingsTheme.entries.first { it.label == label }))) },
        )
    }
    item {
        SettingSegmentedRow(
            title = "Settings Font",
            options = SettingsFont.entries.map { it.label },
            selected = look.settingsFont.label,
            onSelected = { label -> onSettings(settings.copy(lookAndFeel = look.copy(settingsFont = SettingsFont.entries.first { it.label == label }))) },
        )
    }
    item {
        SettingSliderRow(
            title = "App Font Size",
            subtitle = "Scale text throughout PulseDeck",
            value = look.appFontScale,
            onValueChange = { onSettings(settings.copy(lookAndFeel = look.copy(appFontScale = it))) },
            valueRange = 0.85f..1.30f,
            steps = 8,
            valueLabel = percentLabel(look.appFontScale),
            startLabel = "Small",
            endLabel = "Large",
        )
    }
    item { SectionLabel("General") }
    item {
        SettingSwitchRow(
            title = "Open Player First",
            subtitle = "Restore Now Playing before Library when a playable or restored track exists",
            checked = playerUi.startAtPlayerWhenRestored,
            onCheckedChange = { updatePlayerUi(playerUi.copy(startAtPlayerWhenRestored = it)) },
        )
    }
    item {
        SettingSwitchRow(
            title = "Player Gesture Hints",
            subtitle = "Show compact artwork swipe and repeat/shuffle long-press hints",
            checked = playerUi.showGestureHints,
            onCheckedChange = { updatePlayerUi(playerUi.copy(showGestureHints = it)) },
        )
    }
    item {
        SettingSwitchRow(
            title = "Landscape Split Player",
            subtitle = "Use artwork-left and controls-right layout on wide landscape screens",
            checked = playerUi.enableLandscapeSplit,
            onCheckedChange = { updatePlayerUi(playerUi.copy(enableLandscapeSplit = it)) },
        )
    }
    item { SectionLabel("Mini Player") }
    item {
        SettingSegmentedRow(
            title = "Mini Player Style",
            subtitle = "Choose adaptive album glass, deep black glass, or opaque black",
            options = MiniPlayerAppearanceStyle.entries.map { it.label },
            selected = miniAppearance.style.label,
            onSelected = { label ->
                updateMiniAppearance(
                    miniAppearance.copy(style = MiniPlayerAppearanceStyle.entries.first { it.label == label }),
                )
            },
        )
    }
    item {
        SettingSliderRow(
            title = "Mini Player Transparency",
            subtitle = if (miniAppearance.style == MiniPlayerAppearanceStyle.SolidBlack) {
                "Solid Black is always fully opaque"
            } else {
                "Higher values make the mini player less see-through"
            },
            value = miniAppearance.transparency,
            onValueChange = { updateMiniAppearance(miniAppearance.copy(transparency = it)) },
            valueRange = 0f..1f,
            steps = 19,
            valueLabel = percentLabel(miniAppearance.transparency),
            startLabel = "Clear",
            endLabel = "Solid",
            enabled = miniAppearance.style != MiniPlayerAppearanceStyle.SolidBlack,
        )
    }
    item {
        SettingSliderRow(
            title = "Album Tint Strength",
            subtitle = "Controls how strongly album colors shape adaptive glass",
            value = miniAppearance.albumTintStrength,
            onValueChange = { updateMiniAppearance(miniAppearance.copy(albumTintStrength = it)) },
            valueRange = 0f..1f,
            steps = 19,
            valueLabel = percentLabel(miniAppearance.albumTintStrength),
            startLabel = "Neutral",
            endLabel = "Album",
            enabled = miniAppearance.style == MiniPlayerAppearanceStyle.AdaptiveGlass,
        )
    }
    item {
        SettingSliderRow(
            title = "Mini Player Border",
            subtitle = "Adjust the glass edge highlight",
            value = miniAppearance.borderStrength,
            onValueChange = { updateMiniAppearance(miniAppearance.copy(borderStrength = it)) },
            valueRange = 0f..1f,
            steps = 19,
            valueLabel = percentLabel(miniAppearance.borderStrength),
            startLabel = "Off",
            endLabel = "Bright",
            enabled = miniAppearance.style != MiniPlayerAppearanceStyle.SolidBlack,
        )
    }
    item {
        SettingSwitchRow(
            title = "Mini Player High Contrast Text",
            subtitle = "Keep title and artist text bright over transparent glass",
            checked = miniAppearance.useHighContrastText,
            onCheckedChange = { updateMiniAppearance(miniAppearance.copy(useHighContrastText = it)) },
        )
    }
    item { SectionLabel("Player Surface") }
    item {
        var activeSurface by rememberSaveable { mutableStateOf(PlayerSurfacePreviewOrientation.Portrait) }
        PlayerButtonSurfaceSwitcher(
            activeSurface = activeSurface,
            onSurface = { activeSurface = it },
            playerUi = playerUi,
            onPlayerUi = { updatePlayerUi(it) },
        )
    }
    item { SectionLabel("Full Player Content") }
    item {
        SettingSwitchRow(
            title = "Show Lyrics",
            subtitle = "Hide only the full-player lyrics card; cached lyrics stay on device",
            checked = fullPlayerContent.showLyrics,
            onCheckedChange = { updateFullPlayerContent(fullPlayerContent.copy(showLyrics = it)) },
        )
    }
    item {
        SettingSwitchRow(
            title = "Show More from Artist",
            subtitle = "Show official artist continuation after a user-tapped lookup",
            checked = fullPlayerContent.showMoreFromArtist,
            onCheckedChange = { updateFullPlayerContent(fullPlayerContent.copy(showMoreFromArtist = it)) },
        )
    }
    item {
        SettingSwitchRow(
            title = "Show Discography / Albums",
            subtitle = "Show latest release and album/discography entry when artist data is loaded",
            checked = fullPlayerContent.showDiscography,
            onCheckedChange = { updateFullPlayerContent(fullPlayerContent.copy(showDiscography = it)) },
        )
    }
    item {
        SettingSwitchRow(
            title = "Show About Artist",
            subtitle = "Optional collapsed artist/about section; does not fetch online data by itself",
            checked = fullPlayerContent.showAboutArtist,
            onCheckedChange = { updateFullPlayerContent(fullPlayerContent.copy(showAboutArtist = it)) },
        )
    }
    item {
        OptionPickerRow(
            title = "Online Artist Discovery for Local Tracks",
            subtitle = "Local files stay offline-first; online artist works require a tap and this policy",
            options = LocalArtistDiscoveryOptions,
            selectedValue = fullPlayerContent.localArtistDiscoveryPolicy.name,
            onSelected = { value ->
                updateFullPlayerContent(
                    fullPlayerContent.copy(localArtistDiscoveryPolicy = LocalArtistDiscoveryPolicy.valueOf(value)),
                )
            },
        )
    }
    item {
        SettingSwitchRow(
            title = "Compact Full-Player Content Cards",
            subtitle = "Use denser bottom cards to reduce scroll and recomposition cost",
            checked = fullPlayerContent.compactCards,
            onCheckedChange = { updateFullPlayerContent(fullPlayerContent.copy(compactCards = it)) },
        )
    }
    item { SettingInfoBlock("Player Gestures", "Artwork tap or swipe down opens the current category, swipe up opens lyrics/info, and horizontal swipes skip tracks. Repeat and shuffle tap cycles the mode; long press opens the explicit picker.") }
    item { SettingInfoBlock("Lyrics", "Lyrics readability follows Backdrop -> Lyrics Background and Readability Protection so text stays legible over album art.") }
    item { SettingInfoBlock("Notifications", "MediaSession custom buttons are configured in Network & Online -> Android Auto Buttons. Main-menu shortcut count rendering is not wired in this beta.") }
    item { SettingActionRow("Reset Player Surface", "Restore mini-player appearance, player gestures, split layout, and utility buttons") { updatePlayerUi(PlayerUiSettings()) } }
    item { SectionLabel("Other") }
    item {
        OptionPickerRow(
            title = "Language",
            subtitle = "Runtime language switching needs locale resources and is gated for V1",
            options = LanguageOptions,
            selectedValue = look.language,
            enabled = false,
            onSelected = { onSettings(settings.copy(lookAndFeel = look.copy(language = it))) },
        )
    }
    item {
        OptionPickerRow(
            title = "Icon",
            subtitle = "Launcher icon aliases are not configured for V1",
            options = AppIconOptions,
            selectedValue = look.appIcon,
            enabled = false,
            onSelected = { onSettings(settings.copy(lookAndFeel = look.copy(appIcon = it))) },
        )
    }
    item {
        SettingSegmentedRow(
            title = "Screen Orientation",
            options = ScreenOrientationSetting.entries.map { it.label },
            selected = look.orientation.label,
            onSelected = { label -> onSettings(settings.copy(lookAndFeel = look.copy(orientation = ScreenOrientationSetting.entries.first { it.label == label }))) },
        )
    }
    item {
        SettingSegmentedRow(
            title = "Animations",
            subtitle = "Disable or change UI animation speed where possible",
            options = AnimationSpeedSetting.entries.map { it.label },
            selected = look.animationSpeed.label,
            onSelected = { label -> onSettings(settings.copy(lookAndFeel = look.copy(animationSpeed = AnimationSpeedSetting.entries.first { it.label == label }))) },
        )
    }
    item { SettingSwitchRow("Start at Library", "Open the library instead of restoring the previous surface", look.startAtLibrary, { onSettings(settings.copy(lookAndFeel = look.copy(startAtLibrary = it))) }) }
    item { SettingSwitchRow("Hide Status Bar", "Some Android versions may require restarting the app", look.hideStatusBar, { onSettings(settings.copy(lookAndFeel = look.copy(hideStatusBar = it))) }) }
    item { SettingSwitchRow("Keep Screen On", "Keep the screen awake while PulseDeck is foregrounded", look.keepScreenOn, { onSettings(settings.copy(lookAndFeel = look.copy(keepScreenOn = it))) }) }
    item {
        SettingSliderRow(
            title = "Settings Shortcuts in Main Menu",
            subtitle = "Main-menu shortcut rendering is not wired in this beta",
            value = look.settingsShortcutsCount.toFloat(),
            onValueChange = { onSettings(settings.copy(lookAndFeel = look.copy(settingsShortcutsCount = it.toInt()))) },
            valueRange = 0f..10f,
            steps = 9,
            valueLabel = look.settingsShortcutsCount.toString(),
            startLabel = "Disabled",
            endLabel = "Max",
            enabled = false,
        )
    }
    item { RestoreDefaultsRow { onSettings(settings.copy(lookAndFeel = LookAndFeelSettings(), playerUi = PlayerUiSettings())) } }
}

@Composable
private fun PlayerButtonSurfaceSwitcher(
    activeSurface: PlayerSurfacePreviewOrientation,
    onSurface: (PlayerSurfacePreviewOrientation) -> Unit,
    playerUi: PlayerUiSettings,
    onPlayerUi: (PlayerUiSettings) -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    fun mirrored(next: PlayerUiSettings): PlayerUiSettings =
        if (next.mirrorUtilityLayout) {
            next.copy(
                landscapeUtilityButtons = next.utilityButtons,
                landscapeButtonRows = next.portraitButtonRows,
            )
        } else {
            next
        }
    fun updatePortraitButtons(buttons: List<PlayerButtonAction>) {
        onPlayerUi(mirrored(playerUi.copy(utilityButtons = buttons)))
    }
    fun updateLandscapeButtons(buttons: List<PlayerButtonAction>) {
        onPlayerUi(playerUi.copy(landscapeUtilityButtons = buttons, mirrorUtilityLayout = false))
    }
    fun updatePortraitRows(rows: Int) {
        onPlayerUi(mirrored(playerUi.copy(portraitButtonRows = rows)))
    }
    fun updateLandscapeRows(rows: Int) {
        onPlayerUi(playerUi.copy(landscapeButtonRows = rows, mirrorUtilityLayout = false))
    }
    fun setMirror(enabled: Boolean) {
        onPlayerUi(
            if (enabled) {
                playerUi.copy(
                    mirrorUtilityLayout = true,
                    landscapeUtilityButtons = playerUi.utilityButtons,
                    landscapeButtonRows = playerUi.portraitButtonRows,
                )
            } else {
                playerUi.copy(mirrorUtilityLayout = false)
            },
        )
    }
    val activeButtons = if (activeSurface == PlayerSurfacePreviewOrientation.Portrait) playerUi.utilityButtons else playerUi.landscapeUtilityButtons
    val activeRows = if (activeSurface == PlayerSurfacePreviewOrientation.Portrait) playerUi.portraitButtonRows else playerUi.landscapeButtonRows
    val activeLabel = if (activeSurface == PlayerSurfacePreviewOrientation.Portrait) "Portrait" else "Landscape"
    val mirrorLocked = activeSurface == PlayerSurfacePreviewOrientation.Landscape && playerUi.mirrorUtilityLayout
    val activeButtonsChange: (List<PlayerButtonAction>) -> Unit =
        if (activeSurface == PlayerSurfacePreviewOrientation.Portrait) ::updatePortraitButtons else ::updateLandscapeButtons
    val activeRowsChange: (Int) -> Unit =
        if (activeSurface == PlayerSurfacePreviewOrientation.Portrait) ::updatePortraitRows else ::updateLandscapeRows

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(SettingsSurface.copy(alpha = 0.42f))
            .border(1.dp, PulseBlue.copy(alpha = 0.18f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerSurfaceModeButton(
                label = "Adjust Portrait",
                landscape = false,
                selected = activeSurface == PlayerSurfacePreviewOrientation.Portrait,
                modifier = Modifier.weight(1f),
                onClick = { onSurface(PlayerSurfacePreviewOrientation.Portrait) },
            )
            PlayerSurfaceModeButton(
                label = "Adjust Landscape",
                landscape = true,
                selected = activeSurface == PlayerSurfacePreviewOrientation.Landscape,
                modifier = Modifier.weight(1f),
                onClick = { onSurface(PlayerSurfacePreviewOrientation.Landscape) },
            )
        }

        PremiumPlayerSurfaceControls(
            rows = activeRows,
            mirrorLayout = playerUi.mirrorUtilityLayout,
            onRows = activeRowsChange,
            onMirrorLayout = ::setMirror,
        )

        PremiumPlayerControlCenter(
            surfaceLabel = activeLabel,
            buttons = activeButtons,
            rows = activeRows,
            mirrorLocked = mirrorLocked,
            onButtons = activeButtonsChange,
            onRows = activeRowsChange,
        )
    }
}

@Composable
private fun PlayerSurfaceModeButton(
    label: String,
    landscape: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    val contentColor = if (selected) Color(0xFF101115) else SettingsText
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (selected) PulseBlue else Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (selected) PulseBlue.copy(alpha = 0.95f) else SettingsLine, RoundedCornerShape(23.dp))
            .settingsPressEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            PhoneOrientationIcon(
                landscape = landscape,
                tint = contentColor,
                active = selected,
                modifier = Modifier.size(25.dp),
            )
            Text(
                label,
                color = contentColor,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PhoneOrientationIcon(
    landscape: Boolean,
    tint: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (landscape) 90f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)),
        label = "phoneOrientationRotation",
    )
    val lightAlpha by animateFloatAsState(
        targetValue = if (active) 0.30f else 0.08f,
        animationSpec = tween(260),
        label = "phoneOrientationLight",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to tint.copy(alpha = lightAlpha),
                    1.00f to Color.Transparent,
                    radius = size.maxDimension * 0.62f,
                ),
                radius = size.maxDimension * 0.62f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
        Canvas(
            Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation },
        ) {
            val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
            val bodyWidth = size.width * 0.50f
            val bodyHeight = size.height * 0.90f
            val left = (size.width - bodyWidth) / 2f
            val top = (size.height - bodyHeight) / 2f
            val radius = 4.2.dp.toPx()
            drawRoundRect(
                color = tint.copy(alpha = 0.92f),
                topLeft = Offset(left, top),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(radius, radius),
                style = stroke,
            )
            drawCircle(
                color = tint.copy(alpha = 0.72f),
                radius = 1.2.dp.toPx(),
                center = Offset(left + bodyWidth / 2f, top + bodyHeight - 4.2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun PremiumPlayerSurfaceControls(
    rows: Int,
    mirrorLayout: Boolean,
    onRows: (Int) -> Unit,
    onMirrorLayout: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, SettingsLine.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Rows", color = SettingsSubtext, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (rows == 1) "Single row" else "Two rows",
                    color = SettingsText,
                    fontSize = 15.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Row(
                Modifier
                    .weight(1.35f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black.copy(alpha = 0.18f))
                    .border(1.dp, SettingsLine.copy(alpha = 0.62f), RoundedCornerShape(22.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PremiumSmallChoiceChip("One", selected = rows == 1, modifier = Modifier.weight(1f)) { onRows(1) }
                PremiumSmallChoiceChip("Two", selected = rows == 2, modifier = Modifier.weight(1f)) { onRows(2) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsLine.copy(alpha = 0.62f)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Mirror Layout", color = SettingsText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
                Text(
                    if (mirrorLayout) "Landscape follows portrait" else "Portrait and landscape are separate",
                    color = SettingsSubtext,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = SettingsBodyWeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = mirrorLayout,
                onCheckedChange = onMirrorLayout,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PulseBlue,
                ),
            )
        }
    }
}

@Composable
private fun PremiumPlayerControlCenter(
    surfaceLabel: String,
    buttons: List<PlayerButtonAction>,
    rows: Int,
    mirrorLocked: Boolean,
    onButtons: (List<PlayerButtonAction>) -> Unit,
    onRows: (Int) -> Unit,
) {
    val selectedButtons = visiblePlayerUtilityButtons(buttons)
    fun commit(next: List<PlayerButtonAction>) {
        onButtons(if (next.isEmpty()) HiddenPlayerUtilityButtons else next.take(MaxPlayerUtilityButtons))
        if (next.size >= 4 && rows == 1) onRows(2)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumUtilitiesMockup(
            surfaceLabel = surfaceLabel,
            buttons = selectedButtons,
            rows = rows,
            mirrorLocked = mirrorLocked,
            onMove = { from, to -> commit(playerButtonsAfterMove(selectedButtons, from, to)) },
            onRemove = { action -> commit(playerButtonsAfterToggle(selectedButtons, action)) },
        )

        PremiumAvailableButtonPanel(
            selectedButtons = selectedButtons,
            mirrorLocked = mirrorLocked,
            onToggle = { action -> commit(playerButtonsAfterToggle(selectedButtons, action)) },
        )

        PremiumPresetPanel(
            mirrorLocked = mirrorLocked,
            onPreset = { preset ->
                commit(preset.actions)
                onRows(if (preset.actions.size >= 4) 2 else 1)
            },
        )
    }
}

@Composable
private fun PremiumUtilitiesMockup(
    surfaceLabel: String,
    buttons: List<PlayerButtonAction>,
    rows: Int,
    mirrorLocked: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (PlayerButtonAction) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (rows > 1 && buttons.size > 4) 236.dp else 204.dp)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, PulseBlue.copy(alpha = 0.44f), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    0.00f to Color(0xFF19333E),
                    0.46f to Color(0xFF101218),
                    1.00f to Color(0xFF030405),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Color(0xFF4D7DFF).copy(alpha = 0.28f),
                    1.00f to Color.Transparent,
                    center = Offset(size.width * 0.78f, size.height * 0.12f),
                    radius = size.maxDimension * 0.72f,
                ),
                radius = size.maxDimension * 0.72f,
                center = Offset(size.width * 0.78f, size.height * 0.12f),
            )
            drawRect(
                Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = 0.05f),
                    0.55f to Color.Transparent,
                    1.00f to Color.Black.copy(alpha = 0.66f),
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Your $surfaceLabel Control Bar",
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                if (mirrorLocked) "Mirror layout is on" else "Drag to reorder - tap to remove",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            PremiumUtilityDock(
                buttons = buttons,
                rows = rows,
                mirrorLocked = mirrorLocked,
                onMove = onMove,
                onRemove = onRemove,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun PremiumUtilityDock(
    buttons: List<PlayerButtonAction>,
    rows: Int,
    mirrorLocked: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (PlayerButtonAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val useTwoRows = rows > 1 && buttons.size > 4
    val perRow = if (useTwoRows) (buttons.size + 1) / 2 else buttons.size.coerceAtLeast(1)
    val density = LocalDensity.current
    val stridePx = with(density) { 74.dp.toPx() }
    var draggingAction by remember { mutableStateOf<PlayerButtonAction?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    fun targetIndex(from: Int, drag: Offset): Int {
        if (buttons.isEmpty()) return from
        val rowCount = if (useTwoRows) 2 else 1
        val columns = perRow.coerceAtLeast(1)
        val fromRow = from / columns
        val fromColumn = from % columns
        val toColumn = (fromColumn + (drag.x / stridePx).roundToInt()).coerceIn(0, columns - 1)
        val toRow = (fromRow + (drag.y / stridePx).roundToInt()).coerceIn(0, rowCount - 1)
        return (toRow * columns + toColumn).coerceIn(0, buttons.lastIndex)
    }

    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (buttons.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Tap buttons below to build your control bar",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        } else {
            buttons.chunked(perRow).forEach { rowButtons ->
                Row(
                    Modifier.fillMaxWidth().height(68.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowButtons.forEach { action ->
                        val dragging = draggingAction == action
                        val interactionSource = remember(action) { MutableInteractionSource() }
                        PremiumPreviewButton(
                            action = action,
                            dragging = dragging,
                            enabled = !mirrorLocked,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    if (dragging) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.08f
                                        scaleY = 1.08f
                                        shadowElevation = 16f
                                    }
                                }
                                .clickable(enabled = !mirrorLocked, interactionSource = interactionSource, indication = null) {
                                    onRemove(action)
                                }
                                .pointerInput(action, buttons, mirrorLocked) {
                                    if (!mirrorLocked) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingAction = action
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                            },
                                            onDragEnd = {
                                                val from = buttons.indexOf(action)
                                                if (from >= 0) {
                                                    val to = targetIndex(from, dragOffset)
                                                    if (to != from) onMove(from, to)
                                                }
                                                draggingAction = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggingAction = null
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumPreviewButton(
    action: PlayerButtonAction,
    dragging: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.42f else 0.20f,
        animationSpec = tween(180),
        label = "premiumPreviewGlow",
    )
    Column(
        modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (enabled) PulseBlue.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                .border(1.dp, PulseBlue.copy(alpha = glowAlpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(action.previewIcon(), Color.White.copy(alpha = 0.92f), Modifier.size(22.dp))
        }
        Text(
            action.label,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PremiumAvailableButtonPanel(
    selectedButtons: List<PlayerButtonAction>,
    mirrorLocked: Boolean,
    onToggle: (PlayerButtonAction) -> Unit,
) {
    var activeGroupTitle by rememberSaveable { mutableStateOf(PlayerButtonActionGroups.first().title) }
    val activeGroup = PlayerButtonActionGroups.firstOrNull { it.title == activeGroupTitle }
        ?: PlayerButtonActionGroups.first()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, SettingsLine, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Available Buttons", color = SettingsText, fontSize = 18.sp, fontWeight = SettingsTitleWeight, modifier = Modifier.weight(1f))
            Text(if (mirrorLocked) "Mirrored from portrait" else "Tap to enable", color = SettingsSubtext, fontSize = 12.sp, fontWeight = SettingsBodyWeight)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerButtonActionGroups.forEach { group ->
                PremiumAvailableCategoryTab(
                    group = group,
                    selected = group.title == activeGroup.title,
                    selectedCount = selectedButtons.count { it in group.actions },
                    modifier = Modifier.weight(1f),
                    onClick = { activeGroupTitle = group.title },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            activeGroup.actions.chunked(4).forEach { rowActions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowActions.forEach { action ->
                        PremiumAvailableButtonTile(
                            action = action,
                            selected = action in selectedButtons,
                            enabled = !mirrorLocked && (action in selectedButtons || selectedButtons.size < MaxPlayerUtilityButtons),
                            modifier = Modifier.weight(1f),
                            onClick = { onToggle(action) },
                        )
                    }
                    repeat(4 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PremiumAvailableCategoryTab(
    group: PlayerButtonActionGroup,
    selected: Boolean,
    selectedCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(group.title) { MutableInteractionSource() }
    val glowAlpha by animateFloatAsState(
        targetValue = if (selected) 0.30f else 0.08f,
        animationSpec = tween(220),
        label = "availableCategoryGlow",
    )
    val pulseBlue = PulseBlue
    Box(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) pulseBlue.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.035f))
            .border(1.dp, if (selected) pulseBlue.copy(alpha = 0.82f) else SettingsLine, RoundedCornerShape(18.dp))
            .settingsPressEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        Canvas(Modifier.matchParentSize()) {
            if (selected) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to pulseBlue.copy(alpha = glowAlpha),
                        1.00f to Color.Transparent,
                        center = Offset(size.width * 0.18f, size.height * 0.10f),
                        radius = size.width * 0.62f,
                    ),
                    radius = size.width * 0.62f,
                    center = Offset(size.width * 0.18f, size.height * 0.10f),
                )
            }
        }
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (selected) pulseBlue.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.07f))
                    .border(1.dp, if (selected) Color.White.copy(alpha = 0.28f) else SettingsLine, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(group.icon, if (selected) Color.White else SettingsSubtext, Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    group.title,
                    color = if (selected) SettingsText else SettingsSubtext,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$selectedCount selected",
                    color = if (selected) pulseBlue else SettingsSubtext,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PremiumAvailableButtonTile(
    action: PlayerButtonAction,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(action) { MutableInteractionSource() }
    Box(
        modifier
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PulseBlue.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) PulseBlue else SettingsLine, RoundedCornerShape(14.dp))
            .settingsPressEffect(interactionSource, enabled)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(7.dp),
    ) {
        PulseIcon(action.previewIcon(), if (enabled || selected) SettingsText else SettingsDisabled, Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(20.dp))
        Text(
            action.label,
            color = if (enabled || selected) SettingsText else SettingsDisabled,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) PulseBlue else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(if (selected) DeckIcon.Check else DeckIcon.Plus, if (selected) Color.White else SettingsSubtext, Modifier.size(11.dp))
        }
    }
}

@Composable
private fun PremiumPresetPanel(
    mirrorLocked: Boolean,
    onPreset: (PlayerButtonPreset) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, SettingsLine, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PulseIcon(DeckIcon.Discover, PulseBlue, Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("Smart Presets", color = SettingsText, fontSize = 18.sp, fontWeight = SettingsTitleWeight)
                Text("Apply a ready-made layout to get started", color = SettingsSubtext, fontSize = 13.sp, fontWeight = SettingsBodyWeight)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerButtonPresets.forEach { preset ->
                PremiumPresetTile(
                    preset = preset,
                    enabled = !mirrorLocked,
                    modifier = Modifier.weight(1f),
                    onClick = { onPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun PremiumPresetTile(
    preset: PlayerButtonPreset,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(preset.title) { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, SettingsLine, RoundedCornerShape(18.dp))
            .settingsPressEffect(interactionSource, enabled)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulseIcon(preset.icon, if (enabled) SettingsText else SettingsDisabled, Modifier.size(23.dp))
        Text(preset.title, color = if (enabled) SettingsText else SettingsDisabled, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        Text(preset.subtitle, color = if (enabled) SettingsSubtext else SettingsDisabled, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = SettingsBodyWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PremiumSmallChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) PulseBlue else Color.Transparent)
            .border(1.dp, if (selected) PulseBlue else SettingsLine, RoundedCornerShape(20.dp))
            .settingsPressEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color(0xFF101115) else SettingsSubtext, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlayerButtonSlotEditor(
    surface: PlayerSurfacePreviewOrientation,
    buttons: List<PlayerButtonAction>,
    rows: Int,
    onRowsChange: (Int) -> Unit,
    onButtonsChange: (List<PlayerButtonAction>) -> Unit,
) {
    val slotButtons = playerSlotsForEditing(buttons)
    val previewButtons = visiblePlayerUtilityButtons(slotButtons)
    var expandedSlot by rememberSaveable(surface.name) { mutableStateOf<Int?>(null) }
    val surfaceLabel = if (surface == PlayerSurfacePreviewOrientation.Portrait) "Portrait" else "Landscape"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("$surfaceLabel Buttons", color = SettingsText, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = SettingsTitleWeight)
                Text(
                    "${previewButtons.size}/$MaxPlayerUtilityButtons visible",
                    color = SettingsSubtext,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = SettingsBodyWeight,
                )
            }
            PlayerRoundIconButton(
                icon = DeckIcon.Plus,
                enabled = slotButtons.size < MaxPlayerUtilityButtons,
                onClick = {
                    onButtonsChange(playerButtonsAfterAddSlot(buttons))
                    expandedSlot = slotButtons.size
                },
            )
        }

        PlayerUtilitiesOnlyMockup(
            orientation = surface,
            buttons = previewButtons,
            rows = rows,
        )

        PlayerRowsChooser(rows = rows, onRowsChange = onRowsChange)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slotButtons.forEachIndexed { index, action ->
                PlayerButtonSlotRow(
                    label = "$surfaceLabel Button ${index + 1}",
                    action = action,
                    expanded = expandedSlot == index,
                    canRemove = slotButtons.size > 1,
                    unavailableActions = slotButtons.filterIndexed { slotIndex, slotAction ->
                        slotIndex != index && slotAction != PlayerButtonAction.None
                    }.toSet(),
                    onToggle = { expandedSlot = if (expandedSlot == index) null else index },
                    onAction = { next ->
                        onButtonsChange(playerButtonsAfterSetSlot(buttons, index, next))
                        expandedSlot = null
                    },
                    onRemove = {
                        onButtonsChange(playerButtonsAfterRemoveSlot(buttons, index))
                        expandedSlot = null
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerUtilitiesOnlyMockup(
    orientation: PlayerSurfacePreviewOrientation,
    buttons: List<PlayerButtonAction>,
    rows: Int,
) {
    val landscape = orientation == PlayerSurfacePreviewOrientation.Landscape
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (landscape) 124.dp else 138.dp)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    0.00f to Color(0xFF19333E),
                    0.46f to Color(0xFF101218),
                    1.00f to Color(0xFF030405),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Color(0xFF7FE7C3).copy(alpha = 0.24f),
                    1.00f to Color.Transparent,
                    center = Offset(size.width * 0.78f, size.height * 0.12f),
                    radius = size.maxDimension * 0.72f,
                ),
                radius = size.maxDimension * 0.72f,
                center = Offset(size.width * 0.78f, size.height * 0.12f),
            )
            drawRect(
                Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = 0.05f),
                    0.55f to Color.Transparent,
                    1.00f to Color.Black.copy(alpha = 0.62f),
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }
        PlayerPreviewUtilityDock(
            buttons = buttons,
            rows = rows,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 18.dp),
        )
    }
}

@Composable
private fun PlayerPreviewUtilityDock(
    buttons: List<PlayerButtonAction>,
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val useTwoRows = rows > 1 && buttons.size > 4
    val perRow = if (useTwoRows) (buttons.size + 1) / 2 else buttons.size.coerceAtLeast(1)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (buttons.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("No utilities", color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            buttons.chunked(perRow).forEach { rowButtons ->
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowButtons.forEach { action ->
                        PlayerPreviewUtilityPill(action)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPreviewUtilityPill(action: PlayerButtonAction) {
    Box(
        Modifier
            .width(50.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(19.dp)),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(action.previewIcon(), Color.White.copy(alpha = 0.90f), Modifier.size(21.dp))
    }
}

@Composable
private fun PlayerRowsChooser(rows: Int, onRowsChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Rows", color = SettingsSubtext, fontSize = 14.sp, fontWeight = SettingsBodyWeight, modifier = Modifier.weight(1f))
        PlayerChoiceChip("One", selected = rows == 1, modifier = Modifier.weight(1f)) { onRowsChange(1) }
        PlayerChoiceChip("Two", selected = rows == 2, modifier = Modifier.weight(1f)) { onRowsChange(2) }
    }
}

@Composable
private fun PlayerChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PulseBlue else Color.Transparent)
            .border(1.dp, if (selected) PulseBlue else SettingsLine, RoundedCornerShape(8.dp))
            .settingsPressEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF101115) else SettingsSubtext,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerButtonSlotRow(
    label: String,
    action: PlayerButtonAction,
    expanded: Boolean,
    canRemove: Boolean,
    unavailableActions: Set<PlayerButtonAction>,
    onToggle: () -> Unit,
    onAction: (PlayerButtonAction) -> Unit,
    onRemove: () -> Unit,
) {
    val interactionSource = remember(label, action) { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (expanded) PulseBlue.copy(alpha = 0.42f) else SettingsLine, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .settingsPressEffect(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (action == PlayerButtonAction.None) SettingsLine.copy(alpha = 0.28f) else PulseBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(action.previewIcon(), if (action == PlayerButtonAction.None) SettingsSubtext else PulseBlue, Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = SettingsText, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                Text(action.label, color = SettingsSubtext, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = SettingsBodyWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (canRemove) {
                PlayerSmallIconButton(DeckIcon.Close, enabled = true, onClick = onRemove)
            }
        }
        if (expanded) {
            PlayerSlotActionGrid(
                selectedAction = action,
                unavailableActions = unavailableActions,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun PlayerSlotActionGrid(
    selectedAction: PlayerButtonAction,
    unavailableActions: Set<PlayerButtonAction>,
    onAction: (PlayerButtonAction) -> Unit,
) {
    val actions = listOf(PlayerButtonAction.None) + PlayerButtonAddActions
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowActions.forEach { action ->
                    val enabled = action == selectedAction || action !in unavailableActions
                    PlayerSlotActionChip(
                        action = action,
                        selected = action == selectedAction,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(action) },
                    )
                }
                if (rowActions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlayerSlotActionChip(
    action: PlayerButtonAction,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember(action) { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PulseBlue else Color.Transparent)
            .border(1.dp, if (selected) PulseBlue else SettingsLine, RoundedCornerShape(8.dp))
            .settingsPressEffect(interactionSource, enabled)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PulseIcon(
            action.previewIcon(),
            when {
                selected -> Color(0xFF101115)
                enabled -> SettingsSubtext
                else -> SettingsDisabled
            },
            Modifier.size(18.dp),
        )
        Text(
            action.label,
            color = when {
                selected -> Color(0xFF101115)
                enabled -> SettingsSubtext
                else -> SettingsDisabled
            },
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlayerRoundIconButton(
    icon: DeckIcon,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember(icon, enabled) { MutableInteractionSource() }
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (enabled) PulseBlue else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (enabled) PulseBlue else SettingsLine, CircleShape)
            .settingsPressEffect(interactionSource, enabled)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, if (enabled) Color(0xFF101115) else SettingsDisabled, Modifier.size(22.dp))
    }
}

@Composable
private fun PlayerSmallIconButton(
    icon: DeckIcon,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember(icon, enabled) { MutableInteractionSource() }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .settingsPressEffect(interactionSource, enabled)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, if (enabled) SettingsSubtext else SettingsDisabled, Modifier.size(17.dp))
    }
}

private fun playerSlotsForEditing(buttons: List<PlayerButtonAction>): List<PlayerButtonAction> {
    val slots = buttons
        .filterNot { it == PlayerButtonAction.Queue }
        .take(MaxPlayerUtilityButtons)
    return when {
        slots.isEmpty() -> listOf(PlayerButtonAction.None)
        slots.all { it == PlayerButtonAction.None } -> listOf(PlayerButtonAction.None)
        else -> slots
    }
}

private fun visiblePlayerUtilityButtons(buttons: List<PlayerButtonAction>): List<PlayerButtonAction> {
    val visible = mutableListOf<PlayerButtonAction>()
    buttons.forEach { action ->
        if (visible.size >= MaxPlayerUtilityButtons) return@forEach
        if (action != PlayerButtonAction.None && action != PlayerButtonAction.Queue && action !in visible) {
            visible += action
        }
    }
    return visible
}

private fun playerButtonsAfterAddSlot(buttons: List<PlayerButtonAction>): List<PlayerButtonAction> {
    val slots = playerSlotsForEditing(buttons)
    return if (slots.size >= MaxPlayerUtilityButtons) slots else (slots + PlayerButtonAction.None).take(MaxPlayerUtilityButtons)
}

private fun playerButtonsAfterSetSlot(buttons: List<PlayerButtonAction>, index: Int, action: PlayerButtonAction): List<PlayerButtonAction> {
    val slots = playerSlotsForEditing(buttons).toMutableList()
    if (index !in slots.indices) return buttons
    slots[index] = action
    return if (slots.all { it == PlayerButtonAction.None }) HiddenPlayerUtilityButtons else slots.take(MaxPlayerUtilityButtons)
}

private fun playerButtonsAfterRemoveSlot(buttons: List<PlayerButtonAction>, index: Int): List<PlayerButtonAction> {
    val slots = playerSlotsForEditing(buttons)
    if (index !in slots.indices) return buttons
    val next = slots.filterIndexed { slotIndex, _ -> slotIndex != index }
    return if (next.isEmpty() || next.all { it == PlayerButtonAction.None }) HiddenPlayerUtilityButtons else next
}

private fun playerButtonsAfterToggle(buttons: List<PlayerButtonAction>, action: PlayerButtonAction): List<PlayerButtonAction> {
    val visible = visiblePlayerUtilityButtons(buttons)
    return if (action in visible) {
        visible.filterNot { it == action }
    } else if (visible.size < MaxPlayerUtilityButtons && action in PlayerButtonAddActions) {
        visible + action
    } else {
        visible
    }
}

private fun playerButtonsAfterMove(buttons: List<PlayerButtonAction>, from: Int, to: Int): List<PlayerButtonAction> {
    if (from !in buttons.indices || to !in buttons.indices || from == to) return buttons
    val next = buttons.toMutableList()
    val moved = next.removeAt(from)
    next.add(to, moved)
    return next
}

private fun PlayerButtonAction.previewIcon(): DeckIcon =
    when (this) {
        PlayerButtonAction.PlayPause -> DeckIcon.Play
        PlayerButtonAction.None -> DeckIcon.Close
        PlayerButtonAction.PreviousTrack -> DeckIcon.Previous
        PlayerButtonAction.NextTrack -> DeckIcon.Next
        PlayerButtonAction.Queue -> DeckIcon.Queue
        PlayerButtonAction.Visualization -> DeckIcon.Wave
        PlayerButtonAction.SleepTimer -> DeckIcon.Timer
        PlayerButtonAction.Repeat -> DeckIcon.Repeat
        PlayerButtonAction.Shuffle -> DeckIcon.Shuffle
        PlayerButtonAction.Like -> DeckIcon.ThumbUp
        PlayerButtonAction.Bookmark -> DeckIcon.Bookmark
        PlayerButtonAction.Info -> DeckIcon.Info
        PlayerButtonAction.Lyrics -> DeckIcon.Pencil
        PlayerButtonAction.CurrentCategory -> DeckIcon.Disc
        PlayerButtonAction.PreviousCategory -> DeckIcon.Rewind
        PlayerButtonAction.NextCategory -> DeckIcon.Forward
        PlayerButtonAction.SeekBack10 -> DeckIcon.Previous
        PlayerButtonAction.SeekForward10 -> DeckIcon.Next
        PlayerButtonAction.TrackMenu -> DeckIcon.More
        PlayerButtonAction.Library -> DeckIcon.Grid
        PlayerButtonAction.AudioSettings -> DeckIcon.Bars
        PlayerButtonAction.Search -> DeckIcon.Search
        PlayerButtonAction.ClosePlayer -> DeckIcon.Close
    }

private fun androidx.compose.foundation.lazy.LazyListScope.audioItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
    audioRoute: AudioSettingsRoute,
    onAudioRoute: (AudioSettingsRoute) -> Unit,
) {
    val audio = settings.audio
    val runtime = settings.toPlaybackRuntimeSettings()
    fun setAudio(next: AudioEngineState, markModified: Boolean = true) {
        val normalized = next.normalized()
        onSettings(settings.copy(audio = if (markModified) normalized.copy(presetModified = true) else normalized))
    }

    if (audioRoute == AudioSettingsRoute.Index) {
        item {
            SettingInfoBlock(
                "Sound Room",
                "${if (audio.processingActive) "Processing active" else "Transparent path"}; preset ${pulseAudioPresets.firstOrNull { it.id == audio.activePresetId }?.name ?: "Flat"}${if (audio.presetModified) " (edited)" else ""}. All Audio pages now live in the schema-driven settings renderer.",
            )
        }
        item {
            SettingSwitchRow(
                title = "Audio Engine",
                subtitle = "Master switch for PulseDeck processing",
                checked = audio.enabled,
                onCheckedChange = { setAudio(audio.copy(enabled = it, bypass = false)) },
            )
        }
        item {
            SettingSwitchRow(
                title = "Bypass All Processing",
                subtitle = "True bypass keeps playback transparent",
                checked = audio.bypass,
                onCheckedChange = { setAudio(audio.copy(bypass = it)) },
                enabled = audio.enabled,
                disabledReason = "Audio Engine is disabled",
            )
        }
        item { SectionLabel("Audio") }
        AudioSettingsRoute.entries.filterNot { it == AudioSettingsRoute.Index }.forEach { route ->
            if (include(route.title) || include(route.subtitle)) {
                item {
                    SettingNavigationRow(
                        title = route.title,
                        subtitle = route.subtitle,
                        onClick = { onAudioRoute(route) },
                    )
                }
            }
        }
        item { SectionLabel("Output Routes") }
        OutputDeviceType.entries.forEach { route ->
            item {
                val status = runtime.resolveRoute(route)
                SettingDeviceRouteRow(
                    title = route.label,
                    subtitle = "Requested: ${status.requestedPlugin.label}; actual: ${status.actualPlugin.label}",
                    enabled = status.available,
                    status = status.reason ?: "Available",
                    onConfigure = { onAudioRoute(if (route == OutputDeviceType.Chromecast) AudioSettingsRoute.Chromecast else AudioSettingsRoute.Output) },
                )
            }
        }
        item { RestoreDefaultsRow { onSettings(settings.copy(audio = AudioEngineState())) } }
        return
    }

    when (audioRoute) {
        AudioSettingsRoute.AudioInfo -> audioInfoSettingsContent(audio, ::setAudio)
        AudioSettingsRoute.Experiences -> proListeningExperienceSettingsContent(audio, ::setAudio)
        AudioSettingsRoute.Crossfade -> crossfadeSettingsContent(audio, ::setAudio)
        AudioSettingsRoute.ReplayGain -> replayGainSettingsContent(audio, ::setAudio)
        AudioSettingsRoute.AudioFocus -> audioFocusSettingsContent(audio, ::setAudio)
        AudioSettingsRoute.Equalizer -> equalizerSettingsContent(audio, ::setAudio)
        AudioSettingsRoute.Resampler -> resamplerSettingsContent(audio, ::setAudio)
        AudioSettingsRoute.Dvc -> dvcSettingsContent(audio, ::setAudio)
        AudioSettingsRoute.Output -> outputSettingsContent(audio, runtime, ::setAudio)
        AudioSettingsRoute.Chromecast -> chromecastSettingsContent(audio, runtime, ::setAudio)
        AudioSettingsRoute.AdvancedTweaks -> advancedTweaksSettingsContent(audio, ::setAudio) {
            onSettings(settings.copy(audio = AudioEngineState()))
        }
        AudioSettingsRoute.Index -> Unit
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.proListeningExperienceSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val active = activeProListeningExperience(state)
    val recommendation = recommendedProListeningExperience(state)
    val recommendedPlan = proListeningExperiencePlan(state, recommendation.experience.id)
    item { SectionLabel("Guided Sound") }
    item {
        SettingInfoBlock(
            "Pro Listening Experiences",
            "Current: ${active?.title ?: if (state.processingActive) "Custom audio chain" else "Transparent path"}. Recommended for ${recommendation.routeLabel}: ${recommendation.experience.title}.",
        )
    }
    item { SettingInfoRow("Recommendation", "${recommendation.experience.title} / ${recommendation.fit.label}") }
    item { SettingInfoRow("Safety", "${recommendedPlan.safetyStatus.label}; output ${recommendedPlan.outputGainPercent}% after headroom") }
    item { SettingInfoRow("True-peak", recommendedPlan.truePeakSafety) }
    item { SectionLabel("Experiences") }
    proListeningExperiences.forEach { experience ->
        val plan = proListeningExperiencePlan(state, experience.id)
        val selected = active?.id == experience.id
        item {
            SettingActionRow(
                title = if (selected) "${experience.title} (active)" else experience.title,
                subtitle = "${experience.tagline}\n${experience.intensity.label}; ${plan.safetyStatus.label}; headroom ${signedDb(plan.headroomDb)}",
                onClick = { onAudio(plan.previewState, true) },
            )
        }
    }
    val notes = (recommendedPlan.routeWarnings + recommendedPlan.compatibilityNotes)
        .ifEmpty { listOf("No special compatibility notes for the recommended experience.") }
        .take(4)
        .joinToString(" ")
    item { SectionLabel("Compatibility") }
    item { SettingInfoBlock("Route Notes", notes) }
    item {
        SettingActionRow(
            title = "Apply Recommended",
            subtitle = "${recommendation.experience.title} for ${recommendation.routeLabel}",
            onClick = { onAudio(recommendedPlan.previewState, true) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioInfoSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    item { SectionLabel("Diagnostics") }
    item { SettingInfoRow("Output path", "${state.output.mode.label} / ${state.output.deviceProfile.label}") }
    item { SettingInfoRow("Output gain", "${(audioOutputGain(state, 1f) * 100f).roundToInt()}% after headroom") }
    item { SettingInfoRow("DSP status", "EQ ${if (state.eqEnabled) "on" else "off"} / RG ${state.replayGainMode.label} / Limiter ${if (state.limiter.enabled) "on" else "off"}") }
    item { SettingInfoRow("Resampler", "${state.resampler.mode.label} / ${state.resampler.outputSampleRate.label}") }
    item { SettingInfoRow("Clipping", if (state.clippingRisk) "Warning: boost needs headroom" else "Safe") }
    item { SettingInfoRow("Native engine", if (NativeAudioEngineBridge.isAvailable) NativeAudioEngineBridge.engineVersion() else "Unavailable: ${NativeAudioEngineBridge.unavailableReason ?: "library not loaded"}") }
    item { SettingInfoRow("Native graph", "Media3 ${if (state.native.media3DspEnabled) "DSP on" else "DSP off"} / test tone ${if (state.native.lowLatencyToneEnabled) "on" else "off"}") }
    item { SectionLabel("Signal Chain") }
    item { SettingInfoBlock("File To Output", "Decoder -> ReplayGain/preamp -> EQ/tone/stereo -> limiter/headroom -> resampler -> output route. Requested vs actual device output is shown on the Output page.") }
    item { SettingSwitchRow("Long press metadata opens Audio Info", null, state.audioInfo.showOnMainScreenLongPress, { onAudio(state.copy(audioInfo = state.audioInfo.copy(showOnMainScreenLongPress = it)), true) }) }
    item { SettingSwitchRow("Show output path", null, state.audioInfo.showOutputPath, { onAudio(state.copy(audioInfo = state.audioInfo.copy(showOutputPath = it)), true) }) }
    item { SettingSwitchRow("Show DSP chain", null, state.audioInfo.showDspChain, { onAudio(state.copy(audioInfo = state.audioInfo.copy(showDspChain = it)), true) }) }
    item { RestoreDefaultsRow { onAudio(state.copy(audioInfo = com.pulsedeck.app.AudioInfoSettings()), true) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.crossfadeSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val crossfade = state.crossfade
    item { SectionLabel("Transitions") }
    item { SettingSwitchRow("Gapless Playback", "Prefer seamless album playback when tracks support it", crossfade.gaplessEnabled, { onAudio(state.copy(crossfade = crossfade.copy(gaplessEnabled = it)), true) }) }
    item { SettingSwitchRow("Crossfade", "Overlap outgoing and incoming tracks", crossfade.crossfadeEnabled, { onAudio(state.copy(crossfade = crossfade.copy(crossfadeEnabled = it)), true) }) }
    item { SettingSliderRow("Crossfade Duration", value = crossfade.durationMs / 1000f, onValueChange = { onAudio(state.copy(crossfade = crossfade.copy(durationMs = (it * 1000f).roundToInt())), true) }, valueRange = 0f..12f, steps = 11, valueLabel = "${crossfade.durationMs / 1000}s", startLabel = "0", endLabel = "12") }
    item { AudioEnumRow("Fade Curve", FadeCurve.entries, crossfade.fadeCurve, { it.label }) { onAudio(state.copy(crossfade = crossfade.copy(fadeCurve = it)), true) } }
    item { SettingSwitchRow("Fade on Play", null, crossfade.fadeOnPlay, { onAudio(state.copy(crossfade = crossfade.copy(fadeOnPlay = it)), true) }) }
    item { SettingSwitchRow("Fade on Pause", null, crossfade.fadeOnPause, { onAudio(state.copy(crossfade = crossfade.copy(fadeOnPause = it)), true) }) }
    item { SettingSwitchRow("Fade on Manual Skip", "Manual skips stay quick and use a short fade", crossfade.fadeOnManualSkip, { onAudio(state.copy(crossfade = crossfade.copy(fadeOnManualSkip = it)), true) }) }
    item { SettingSwitchRow("Disable Crossfade for Albums", null, crossfade.disableForAlbums, { onAudio(state.copy(crossfade = crossfade.copy(disableForAlbums = it)), true) }) }
    item { SettingSwitchRow("Disable Crossfade for Short Tracks", null, crossfade.disableForShortTracks, { onAudio(state.copy(crossfade = crossfade.copy(disableForShortTracks = it)), true) }) }
    item { RestoreDefaultsRow { onAudio(state.copy(crossfade = CrossfadeSettings()), true) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.replayGainSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val replayGain = state.replayGain
    fun update(next: ReplayGainSettings) = onAudio(state.withReplayGainSettings(next), true)
    item { SectionLabel("Replay Gain") }
    item { SettingSwitchRow("ReplayGain Enabled", "Off keeps raw playback", replayGain.enabled, { update(replayGain.copy(enabled = it, mode = if (it && replayGain.mode == ReplayGainMode.Off) ReplayGainMode.Smart else replayGain.mode)) }) }
    item { AudioEnumRow("ReplayGain Mode", ReplayGainMode.entries, state.replayGainMode, { it.label }) { update(replayGain.copy(enabled = it != ReplayGainMode.Off, mode = it)) } }
    item { AudioEnumRow("Source", ReplayGainSource.entries, replayGain.source, { it.label }) { update(replayGain.copy(source = it)) } }
    item { SettingSliderRow("Track Preamp", value = replayGain.trackPreampDb, onValueChange = { update(replayGain.copy(trackPreampDb = it)) }, valueRange = -12f..12f, steps = 47, valueLabel = signedDb(replayGain.trackPreampDb), startLabel = "-12", endLabel = "+12") }
    item { SettingSliderRow("Album Preamp", value = replayGain.albumPreampDb, onValueChange = { update(replayGain.copy(albumPreampDb = it)) }, valueRange = -12f..12f, steps = 47, valueLabel = signedDb(replayGain.albumPreampDb), startLabel = "-12", endLabel = "+12") }
    item { SettingSliderRow("No-RG Preamp", value = replayGain.noRgPreampDb, onValueChange = { update(replayGain.copy(noRgPreampDb = it)) }, valueRange = -12f..12f, steps = 47, valueLabel = signedDb(replayGain.noRgPreampDb), startLabel = "-12", endLabel = "+12") }
    item { SettingSwitchRow("Prevent Clipping", "Reduce output gain when boost may distort", replayGain.preventClipping, { update(replayGain.copy(preventClipping = it)) }) }
    item { SettingSwitchRow("Show RG in Audio Info", null, replayGain.showInAudioInfo, { update(replayGain.copy(showInAudioInfo = it)) }) }
    item { RestoreDefaultsRow { onAudio(state.withReplayGainSettings(ReplayGainSettings(enabled = false, mode = ReplayGainMode.Off)), true) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioFocusSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val focus = state.audioFocus
    fun update(next: AudioFocusSettings) = onAudio(state.copy(audioFocus = next), true)
    item { SectionLabel("Focus Policy") }
    item { SettingSwitchRow("Request Audio Focus on Play", null, focus.requestOnPlay, { update(focus.copy(requestOnPlay = it)) }) }
    item { AudioEnumRow("Permanent Focus Loss", PermanentFocusLossAction.entries, focus.onPermanentLoss, { it.label }) { update(focus.copy(onPermanentLoss = it)) } }
    item { AudioEnumRow("Temporary Focus Loss", TransientFocusLossAction.entries, focus.onTransientLoss, { it.label }) { update(focus.copy(onTransientLoss = it)) } }
    item { AudioEnumRow("Duck Request", DuckRequestAction.entries, focus.onDuck, { it.label }) { update(focus.copy(onDuck = it)) } }
    item { SettingSwitchRow("Resume After Call", null, focus.resumeAfterCall, { update(focus.copy(resumeAfterCall = it)) }) }
    item { SettingSwitchRow("Resume After Notification", null, focus.resumeAfterNotification, { update(focus.copy(resumeAfterNotification = it)) }) }
    item { SettingSliderRow("Duck Volume Level", value = focus.duckVolume, onValueChange = { update(focus.copy(duckVolume = it)) }, valueRange = 0f..1f, steps = 19, valueLabel = "${(focus.duckVolume * 100f).roundToInt()}%", startLabel = "0", endLabel = "100") }
    item { SettingSliderRow("Duck Fade Time", value = focus.duckFadeMs.toFloat(), onValueChange = { update(focus.copy(duckFadeMs = it.roundToInt())) }, valueRange = 50f..1000f, steps = 18, valueLabel = "${focus.duckFadeMs} ms", startLabel = "50", endLabel = "1000") }
    item { AudioEnumRow("Bluetooth Focus Behavior", BluetoothFocusBehavior.entries, focus.bluetoothBehavior, { it.label }) { update(focus.copy(bluetoothBehavior = it)) } }
    item { SettingSwitchRow("Resume Only If Auto-Paused", "Do not restart audio after the user manually paused", focus.resumeOnlyIfAutoPaused, { update(focus.copy(resumeOnlyIfAutoPaused = it)) }) }
    item { RestoreDefaultsRow { onAudio(state.copy(audioFocus = AudioFocusSettings()), true) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.equalizerSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val activePreset = pulseAudioPresets.firstOrNull { it.id == state.activePresetId } ?: pulseAudioPresets.first()
    val headroomDb = if (state.preventClipping) -max(0f, state.maxBoostDb) else 0f
    val nativeActivation = nativeAudioActivationFor(state, NativeAudioEngineBridge.isAvailable)
    val graphicEqGainRange = state.effectiveGraphicEqGainRange(nativeActivation.media3DspActive)
    val extendedRangeRequested = state.graphicEqRangeMode == GraphicEqRangeMode.Extended15DbWhenSupported
    val extendedRangeEffective = state.graphicEqExtendedRangeEffective(nativeActivation.media3DspActive)
    val rangeStatus = if (extendedRangeEffective) "+/-15 dB active" else if (extendedRangeRequested) "+/-12 dB effective" else "+/-12 dB standard"
    item { SettingInfoBlock("Audio Engine", "${activePreset.name}${if (state.presetModified) " (edited)" else ""}. Auto headroom ${signedDb(headroomDb)}; max boost ${signedDb(state.maxBoostDb)}.") }
    item { SettingSwitchRow("Audio Engine", "Neutral by default. Powerful when enabled.", state.enabled, { onAudio(state.copy(enabled = it, bypass = false), true) }) }
    item { SettingSwitchRow("Bypass All Processing", "True bypass keeps playback transparent", state.bypass, { onAudio(state.copy(bypass = it), true) }) }
    item { SectionLabel("Presets") }
    pulseAudioPresets.forEach { preset ->
        item {
            SettingActionRow(
                title = preset.name,
                subtitle = preset.description,
                onClick = { onAudio(applyPreset(state, preset), false) },
            )
        }
    }
    item { SectionLabel("Native A/B") }
    item {
        AudioEnumRow("Active Compare Slot", NativeCompareSlot.entries, state.native.activeCompareSlot, { it.label }) {
            val slot = when (it) {
                NativeCompareSlot.A -> state.native.compareSlotA
                NativeCompareSlot.B -> state.native.compareSlotB
                NativeCompareSlot.Off -> null
            }
            onAudio(if (slot == null) state.copy(native = state.native.copy(activeCompareSlot = NativeCompareSlot.Off)) else state.withNativePresetSlot(slot, it), true)
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallSettingsButton("Capture A", Modifier.weight(1f)) {
                onAudio(state.copy(native = state.native.copy(compareSlotA = state.toNativePresetSlot("A"), activeCompareSlot = NativeCompareSlot.A)), true)
            }
            SmallSettingsButton("Capture B", Modifier.weight(1f)) {
                onAudio(state.copy(native = state.native.copy(compareSlotB = state.toNativePresetSlot("B"), activeCompareSlot = NativeCompareSlot.B)), true)
            }
        }
    }
    item { SectionLabel("Simple Controls") }
    item { SettingSliderRow("Bass", value = state.bassDb, onValueChange = { onAudio(state.copy(bassDb = it), true) }, valueRange = -12f..12f, steps = 47, valueLabel = signedDb(state.bassDb), startLabel = "Less", endLabel = "More") }
    item { SettingSliderRow("Treble", value = state.trebleDb, onValueChange = { onAudio(state.copy(trebleDb = it), true) }, valueRange = -12f..12f, steps = 47, valueLabel = signedDb(state.trebleDb), startLabel = "Dark", endLabel = "Bright") }
    item { SettingSliderRow("Vocal Clarity", value = state.vocalClarityDb, onValueChange = { onAudio(state.copy(vocalClarityDb = it), true) }, valueRange = -12f..12f, steps = 47, valueLabel = signedDb(state.vocalClarityDb), startLabel = "Soft", endLabel = "Clear") }
    item { SettingSliderRow("Loudness", "Uses the playback audio session where platform support is available.", state.loudnessDb, { onAudio(state.copy(loudnessDb = it), true) }, 0f..12f, 23, signedDb(state.loudnessDb), "Off", "Full") }
    item { SectionLabel("Graphic EQ") }
    item { SettingSwitchRow("EQ Enabled", "Disable only equalizer bands while keeping other controls", state.eqEnabled, { onAudio(state.copy(eqEnabled = it), true) }) }
    item {
        SettingSwitchRow(
            "Extended EQ Range",
            "Native DSP only; current $rangeStatus",
            extendedRangeRequested,
            { enabled -> onAudio(state.withGraphicEqRangeMode(if (enabled) GraphicEqRangeMode.Extended15DbWhenSupported else GraphicEqRangeMode.Standard12Db), true) },
        )
    }
    item { SettingSliderRow("Preamp", "Creates headroom before boosts. Auto headroom is ${signedDb(headroomDb)}.", state.preampDb, { onAudio(state.copy(preampDb = quantizeAudioDb(it, -12f..12f)), true) }, -12f..12f, audioDbSliderSteps(-12f..12f), signedDb(state.preampDb), "-12 dB", "+12 dB", snapStepDb = AUDIO_EQ_INPUT_STEP_DB) }
    state.eqBands.forEachIndexed { index, band ->
        val effectiveGain = band.gainDb.coerceIn(graphicEqGainRange.start, graphicEqGainRange.endInclusive)
        item {
            SettingSliderRow(
                title = band.frequencyLabel(),
                value = effectiveGain,
                onValueChange = { gain -> onAudio(state.withGraphicEqBandGain(index, gain), true) },
                valueRange = graphicEqGainRange,
                steps = audioDbSliderSteps(graphicEqGainRange),
                valueLabel = effectiveDbLabel(band.gainDb, effectiveGain),
                startLabel = signedDb(graphicEqGainRange.start),
                endLabel = signedDb(graphicEqGainRange.endInclusive),
                snapStepDb = AUDIO_EQ_INPUT_STEP_DB,
            )
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallSettingsButton("Flat", Modifier.weight(1f)) {
                pulseAudioPresets.firstOrNull { it.id == "flat" }?.let { onAudio(applyPreset(state, it), false) }
            }
            SmallSettingsButton("A/B Bypass", Modifier.weight(1f)) { onAudio(state.copy(bypass = !state.bypass), true) }
        }
    }
    item { SectionLabel("Limiter & Clipping") }
    item { SettingSwitchRow("Prevent Clipping", "Automatically lowers output gain when boosted EQ may clip", state.preventClipping, { onAudio(state.copy(preventClipping = it), true) }) }
    item { SettingSwitchRow("Soft Limiter", "DSP model is active; platform output uses safe gain fallback", state.limiter.enabled, { onAudio(state.copy(limiter = state.limiter.copy(enabled = it)), true) }) }
    item { SettingSliderRow("Limiter Ceiling", value = state.limiter.ceilingDb, onValueChange = { onAudio(state.copy(limiter = state.limiter.copy(ceilingDb = it)), true) }, valueRange = -6f..0f, steps = 23, valueLabel = signedDb(state.limiter.ceilingDb), startLabel = "-6", endLabel = "0") }
    item { SectionLabel("Parametric EQ") }
    state.parametricEq.forEachIndexed { index, band ->
        item {
            ParametricBandSettingsRow(band) { next ->
                onAudio(state.withParametricEqBand(index, next), true)
            }
        }
    }
    item { SectionLabel("Stereo, Reverb, Tempo") }
    item { SettingSliderRow("Balance", value = state.stereo.balance, onValueChange = { onAudio(state.copy(stereo = state.stereo.copy(balance = it)), true) }, valueRange = -1f..1f, steps = 39, valueLabel = "${(state.stereo.balance * 100f).roundToInt()}%", startLabel = "Left", endLabel = "Right") }
    item { SettingSwitchRow("Mono", "Useful for small speakers and broken earbuds", state.stereo.mono, { onAudio(state.copy(stereo = state.stereo.copy(mono = it)), true) }) }
    item { SettingSliderRow("Stereo Width", value = state.stereo.stereoWidth, onValueChange = { onAudio(state.copy(stereo = state.stereo.copy(stereoWidth = it)), true) }, valueRange = 0f..1f, steps = 19, valueLabel = "${(state.stereo.stereoWidth * 100f).roundToInt()}%", startLabel = "Narrow", endLabel = "Wide") }
    item { SettingSliderRow("Crossfeed", value = state.stereo.crossfeed, onValueChange = { onAudio(state.copy(stereo = state.stereo.copy(crossfeed = it)), true) }, valueRange = 0f..1f, steps = 19, valueLabel = "${(state.stereo.crossfeed * 100f).roundToInt()}%", startLabel = "Off", endLabel = "Natural") }
    item { SettingSwitchRow("Reverb", "Creative effect; off by default", state.reverb.enabled, { onAudio(state.copy(reverb = state.reverb.copy(enabled = it)), true) }) }
    item { SettingSliderRow("Reverb Mix", value = state.reverb.mix, onValueChange = { onAudio(state.copy(reverb = state.reverb.copy(mix = it)), true) }, valueRange = 0f..1f, steps = 19, valueLabel = "${(state.reverb.mix * 100f).roundToInt()}%", startLabel = "Dry", endLabel = "Wet") }
    item { SettingSliderRow("Tempo", value = state.tempoPitch.tempo, onValueChange = { onAudio(state.copy(tempoPitch = state.tempoPitch.copy(tempo = it)), true) }, valueRange = 0.5f..2f, steps = 29, valueLabel = String.format(Locale.US, "%.2fx", state.tempoPitch.tempo), startLabel = "Slow", endLabel = "Fast") }
    item { SettingSliderRow("Pitch", value = state.tempoPitch.pitchSemitones, onValueChange = { onAudio(state.copy(tempoPitch = state.tempoPitch.copy(pitchSemitones = it)), true) }, valueRange = -12f..12f, steps = 23, valueLabel = "${state.tempoPitch.pitchSemitones.roundToInt()} st", startLabel = "-12", endLabel = "+12") }
    item { SectionLabel("Native Dynamics") }
    item { SettingSwitchRow("Compressor", "Native feed-forward compressor", state.compressor.enabled, { onAudio(state.copy(compressor = state.compressor.copy(enabled = it, mix = if (it && state.compressor.mix == 0f) 0.65f else state.compressor.mix)), true) }) }
    item { SettingSliderRow("Compressor Threshold", value = state.compressor.thresholdDb, onValueChange = { onAudio(state.copy(compressor = state.compressor.copy(thresholdDb = it)), true) }, valueRange = -60f..0f, steps = 59, valueLabel = signedDb(state.compressor.thresholdDb), startLabel = "-60", endLabel = "0") }
    item { SettingSliderRow("Compressor Ratio", value = state.compressor.ratio, onValueChange = { onAudio(state.copy(compressor = state.compressor.copy(ratio = it)), true) }, valueRange = 1f..20f, steps = 18, valueLabel = String.format(Locale.US, "%.1f:1", state.compressor.ratio), startLabel = "1:1", endLabel = "20:1") }
    item { SettingSliderRow("Compressor Mix", value = state.compressor.mix, onValueChange = { onAudio(state.copy(compressor = state.compressor.copy(mix = it)), true) }, valueRange = 0f..1f, steps = 19, valueLabel = "${(state.compressor.mix * 100f).roundToInt()}%", startLabel = "Dry", endLabel = "Wet") }
    item { SettingSwitchRow("Noise Gate", "Mutes low-level noise before ambience effects", state.gate.enabled, { onAudio(state.copy(gate = state.gate.copy(enabled = it)), true) }) }
    item { SettingSliderRow("Gate Threshold", value = state.gate.thresholdDb, onValueChange = { onAudio(state.copy(gate = state.gate.copy(thresholdDb = it)), true) }, valueRange = -80f..0f, steps = 79, valueLabel = signedDb(state.gate.thresholdDb), startLabel = "-80", endLabel = "0") }
    item { SectionLabel("Native Ambience") }
    item { SettingSwitchRow("Delay", "Preallocated native delay line", state.delay.enabled, { onAudio(state.copy(delay = state.delay.copy(enabled = it, mix = if (it && state.delay.mix == 0f) 0.2f else state.delay.mix)), true) }) }
    item { SettingSliderRow("Delay Time", value = state.delay.timeMs, onValueChange = { onAudio(state.copy(delay = state.delay.copy(timeMs = it)), true) }, valueRange = 1f..1800f, steps = 59, valueLabel = "${state.delay.timeMs.roundToInt()} ms", startLabel = "Short", endLabel = "Long") }
    item { SettingSliderRow("Delay Feedback", value = state.delay.feedback, onValueChange = { onAudio(state.copy(delay = state.delay.copy(feedback = it)), true) }, valueRange = 0f..0.92f, steps = 22, valueLabel = "${(state.delay.feedback * 100f).roundToInt()}%", startLabel = "0", endLabel = "Safe max") }
    item { SettingSliderRow("Delay Mix", value = state.delay.mix, onValueChange = { onAudio(state.copy(delay = state.delay.copy(mix = it)), true) }, valueRange = 0f..1f, steps = 19, valueLabel = "${(state.delay.mix * 100f).roundToInt()}%", startLabel = "Dry", endLabel = "Wet") }
    item { SettingSwitchRow("Chorus", null, state.modulation.chorusEnabled, { onAudio(state.copy(modulation = state.modulation.copy(chorusEnabled = it, mix = if (it && state.modulation.mix == 0f) 0.2f else state.modulation.mix)), true) }) }
    item { SettingSwitchRow("Flanger", null, state.modulation.flangerEnabled, { onAudio(state.copy(modulation = state.modulation.copy(flangerEnabled = it, mix = if (it && state.modulation.mix == 0f) 0.18f else state.modulation.mix)), true) }) }
    item { SettingSwitchRow("Phaser", null, state.modulation.phaserEnabled, { onAudio(state.copy(modulation = state.modulation.copy(phaserEnabled = it, mix = if (it && state.modulation.mix == 0f) 0.18f else state.modulation.mix)), true) }) }
    item { SettingSliderRow("Modulation Rate", value = state.modulation.rateHz, onValueChange = { onAudio(state.copy(modulation = state.modulation.copy(rateHz = it)), true) }, valueRange = 0.02f..8f, steps = 31, valueLabel = String.format(Locale.US, "%.2f Hz", state.modulation.rateHz), startLabel = "Slow", endLabel = "Fast") }
    item { SettingSliderRow("Modulation Depth", value = state.modulation.depth, onValueChange = { onAudio(state.copy(modulation = state.modulation.copy(depth = it)), true) }, valueRange = 0f..1f, steps = 19, valueLabel = "${(state.modulation.depth * 100f).roundToInt()}%", startLabel = "Subtle", endLabel = "Deep") }
    item { SettingSliderRow("Modulation Mix", value = state.modulation.mix, onValueChange = { onAudio(state.copy(modulation = state.modulation.copy(mix = it)), true) }, valueRange = 0f..1f, steps = 19, valueLabel = "${(state.modulation.mix * 100f).roundToInt()}%", startLabel = "Dry", endLabel = "Wet") }
    item { RestoreDefaultsRow { onAudio(AudioEngineState(), false) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resamplerSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val resampler = state.resampler
    fun update(next: ResamplerSettings) = onAudio(state.copy(resampler = next), true)
    item { SectionLabel("Sample-rate Conversion") }
    item { AudioEnumRow("Resampler Type", ResamplerMode.entries, resampler.mode, { it.label }) { update(resampler.copy(mode = it)) } }
    item { AudioEnumRow("Output Sample Rate", OutputSampleRate.entries, resampler.outputSampleRate, { it.label }) { update(resampler.copy(outputSampleRate = it)) } }
    item { AudioEnumRow("Quality", ResamplerQuality.entries, resampler.quality, { it.label }) { update(resampler.copy(quality = it)) } }
    item { SettingSwitchRow("Dither", "Applies when reducing bit depth", resampler.dither, { update(resampler.copy(dither = it)) }) }
    item { SettingSwitchRow("Show Conversion Info", null, resampler.showConversionInfo, { update(resampler.copy(showConversionInfo = it)) }) }
    item { SettingSwitchRow("Battery Saver Mode", "Use lighter processing when needed", resampler.batterySaverMode, { update(resampler.copy(batterySaverMode = it)) }) }
    item { SettingInfoBlock("Recommendation", "Auto avoids unnecessary processing. Higher quality may increase CPU and battery use.") }
    item { RestoreDefaultsRow { onAudio(state.copy(resampler = ResamplerSettings()), true) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dvcSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val dvc = state.dvc
    fun update(next: DirectVolumeControlSettings) = onAudio(state.copy(dvc = next), true)
    item { SettingInfoBlock("Direct Volume Control", "This is modeled as safe internal headroom control. Android and Bluetooth absolute-volume behavior can still override what the app can actually do.") }
    item { SettingSwitchRow("Enable Direct Volume Control", null, dvc.enabled, { update(dvc.copy(enabled = it)) }) }
    item { SettingSwitchRow("DVC for Bluetooth", "Bluetooth absolute volume may block true independent control", dvc.bluetoothEnabled, { update(dvc.copy(bluetoothEnabled = it)) }) }
    item { SettingSliderRow("DVC Headroom", value = dvc.headroomDb, onValueChange = { update(dvc.copy(headroomDb = it)) }, valueRange = -12f..0f, steps = 23, valueLabel = signedDb(dvc.headroomDb), startLabel = "-12", endLabel = "0") }
    item { AudioEnumRow("Volume Steps", DvcVolumeSteps.entries, dvc.volumeSteps, { it.label }) { update(dvc.copy(volumeSteps = it)) } }
    item { SettingSwitchRow("Per-Device DVC", null, dvc.perDeviceDvc, { update(dvc.copy(perDeviceDvc = it)) }) }
    item { SettingSwitchRow("Compatibility Mode", "Use this if audio is too quiet, distorted, or inconsistent", dvc.compatibilityMode, { update(dvc.copy(compatibilityMode = it)) }) }
    item { RestoreDefaultsRow { onAudio(state.copy(dvc = DirectVolumeControlSettings()), true) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.outputSettingsContent(
    state: AudioEngineState,
    runtime: com.pulsedeck.app.settings.runtime.PlaybackRuntimeSettings,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val output = state.output
    val native = state.native
    val nativeConstraint = nativeMasterGainConstraint(state, native.masterGain)
    val nativeCanInitialize = NativeAudioEngineBridge.initializationState != NativeAudioInitializationState.Failed
    fun update(next: OutputSettings) = onAudio(state.copy(output = next), true)
    fun updateNative(next: com.pulsedeck.app.NativeAudioSettings) = onAudio(state.copy(native = next), true)
    item { SectionLabel("Output Routing") }
    item { AudioEnumRow("Output Mode", OutputMode.entries, output.mode, { it.label }) { update(output.copy(mode = it)) } }
    item { AudioEnumRow("Device Profile", DeviceProfile.entries, output.deviceProfile, { it.label }) { update(output.copy(deviceProfile = it)) } }
    item { AudioEnumRow("Buffer Size", BufferMode.entries, output.bufferMode, { it.label }) { update(output.copy(bufferMode = it)) } }
    item { SettingSwitchRow("Hi-Res Attempt", "Experimental; actual format depends on device and firmware", output.hiResEnabled, { update(output.copy(hiResEnabled = it)) }) }
    item { AudioEnumRow("Sample Rate", OutputSampleRate.entries, output.sampleRate, { it.label }) { update(output.copy(sampleRate = it)) } }
    item { AudioEnumRow("Bit Depth", OutputBitDepth.entries, output.bitDepth, { it.label }) { update(output.copy(bitDepth = it)) } }
    item { SettingSwitchRow("Bit-perfect Attempt", "Reports blockers and never disables DSP, ReplayGain, or resampling without your changes", output.bitPerfectAttemptEnabled, { update(output.copy(bitPerfectAttemptEnabled = it)) }) }
    item { AudioEnumRow("Fallback Mode", OutputFallbackMode.entries, output.fallbackMode, { it.label }) { update(output.copy(fallbackMode = it)) } }
    item { SettingSwitchRow("Show Output Warnings", null, output.showOutputWarnings, { update(output.copy(showOutputWarnings = it)) }) }
    item { SettingInfoRow("Active output gain", "${(audioOutputGain(state, 1f) * 100f).roundToInt()}%") }
    item {
        SettingInfoRow(
            "Native preamp ceiling",
            if (nativeConstraint.constrained) {
                "Effective ${signedDb(nativeConstraint.effectiveDb)} from requested ${signedDb(nativeConstraint.requestedDb)}"
            } else {
                "Within verified ${signedDb(linearToDb(NATIVE_MASTER_GAIN_MAX_LINEAR))} ceiling"
            },
        )
    }
    item { SectionLabel("Native Engine") }
    item { SettingInfoRow("Native library", if (NativeAudioEngineBridge.isAvailable) NativeAudioEngineBridge.engineVersion() else "Unavailable: ${NativeAudioEngineBridge.unavailableReason ?: "library not loaded"}") }
    item { SettingSwitchRow("Enable Native Engine", "Gates all native DSP and low-latency output", native.enabled, { updateNative(native.copy(enabled = it)) }, enabled = nativeCanInitialize, disabledReason = "Native library unavailable") }
    item { SettingSwitchRow("Media3 Native DSP", "Processes decoded PCM before Android output; Media3 remains the player", native.media3DspEnabled, { updateNative(native.copy(media3DspEnabled = it, enabled = native.enabled || it)) }, enabled = native.enabled && nativeCanInitialize, disabledReason = if (nativeCanInitialize) "Enable Native Engine first" else "Native library unavailable") }
    item { SettingSwitchRow("Low-Latency Test Tone", "Starts Oboe output only when Native low-latency output is selected", native.lowLatencyToneEnabled, { updateNative(native.copy(lowLatencyToneEnabled = it, enabled = native.enabled || it)) }, enabled = native.enabled && output.mode == OutputMode.NativeLowLatency && nativeCanInitialize, disabledReason = if (!nativeCanInitialize) "Native library unavailable" else "Select Native low-latency output") }
    item { SettingSliderRow("Native Master Gain", value = native.masterGain, onValueChange = { updateNative(native.copy(masterGain = it)) }, valueRange = 0f..1.5f, steps = 29, valueLabel = "${(native.masterGain * 100f).roundToInt()}%", startLabel = "0", endLabel = "150", enabled = native.enabled) }
    item { SettingSliderRow("Test Tone Frequency", value = native.sourceFrequencyHz, onValueChange = { updateNative(native.copy(sourceFrequencyHz = it)) }, valueRange = 20f..4000f, steps = 79, valueLabel = "${native.sourceFrequencyHz.roundToInt()} Hz", startLabel = "20", endLabel = "4k", enabled = native.enabled) }
    item { SettingSliderRow("Test Tone Gain", value = native.sourceGain, onValueChange = { updateNative(native.copy(sourceGain = it)) }, valueRange = 0f..0.4f, steps = 39, valueLabel = "${(native.sourceGain * 100f).roundToInt()}%", startLabel = "Muted", endLabel = "Safe max", enabled = native.enabled) }
    item { SettingSliderRow("WAV Export Duration", value = native.exportDurationMs / 1000f, onValueChange = { updateNative(native.copy(exportDurationMs = (it * 1000).roundToInt())) }, valueRange = 0.25f..60f, steps = 59, valueLabel = "${native.exportDurationMs / 1000f}s", startLabel = "0.25", endLabel = "60", enabled = native.enabled) }
    item { SectionLabel("Per-Device Routes") }
    OutputDeviceType.entries.filterNot { it == OutputDeviceType.Chromecast }.forEach { route ->
        item {
            val status = runtime.resolveRoute(route)
            SettingDeviceRouteRow(
                title = route.label,
                subtitle = "Requested: ${status.requestedPlugin.label}; actual: ${status.actualPlugin.label}",
                enabled = status.available,
                status = status.reason ?: "Available",
            )
        }
    }
    item { RestoreDefaultsRow { onAudio(state.copy(output = OutputSettings()), true) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.chromecastSettingsContent(
    state: AudioEngineState,
    runtime: com.pulsedeck.app.settings.runtime.PlaybackRuntimeSettings,
    onAudio: (AudioEngineState, Boolean) -> Unit,
) {
    val status = runtime.resolveRoute(OutputDeviceType.Chromecast)
    item { SettingInfoBlock("Chromecast Output", "Cast playback is remote. Local EQ, DVC, crossfade, tempo, pitch, and most DSP settings are unavailable unless a custom receiver processes them.") }
    item { SettingDeviceRouteRow("Chromecast", "Requested: ${status.requestedPlugin.label}; actual: ${status.actualPlugin.label}", status.available, status.reason ?: "Available") }
    item { SettingActionRow("Button Options", "Configure Chromecast button; Cast framework integration is still gated", enabled = false, disabledReason = "Requires Media3 CastPlayer integration", onClick = {}) }
    item { SettingInfoRow("Sample Rate", "Defined by receiver device") }
    item { SettingInfoRow("Sample Format", "Defined by receiver device") }
    item { SettingSwitchRow("No DVC", "DVC is not supported on remote Cast playback", checked = true, onCheckedChange = {}, enabled = false, disabledReason = "Remote receiver owns audio output") }
    item { SettingSwitchRow("No EQ/Tone", "Disable local equalizer/tone DSP for Cast route", checked = true, onCheckedChange = {}, enabled = false, disabledReason = "Remote receiver owns audio output") }
    item { SettingSwitchRow("Ignore Audio Focus", "May be receiver-dependent", checked = false, onCheckedChange = {}, enabled = false, disabledReason = "Requires CastPlayer integration") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.advancedTweaksSettingsContent(
    state: AudioEngineState,
    onAudio: (AudioEngineState, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val tweaks = state.advancedTweaks
    fun update(next: AdvancedAudioTweaksSettings) = onAudio(state.copy(advancedTweaks = next), true)
    item { SectionLabel("Compatibility") }
    item { AudioEnumRow("Volume Levels", DvcVolumeSteps.entries, tweaks.volumeSteps, { it.label }) { update(tweaks.copy(volumeSteps = it)) } }
    item { SettingSwitchRow("MusicFX", "Optional system effects integration where available", tweaks.musicFxEnabled, { update(tweaks.copy(musicFxEnabled = it)) }) }
    item { SettingSwitchRow("Safe Mode", "Temporarily disables advanced DSP experiments", tweaks.safeMode, { update(tweaks.copy(safeMode = it)) }) }
    item { SettingSwitchRow("Debug Audio Info", null, tweaks.debugAudioInfo, { update(tweaks.copy(debugAudioInfo = it)) }) }
    item { SettingActionRow("Audio Outputs Detection Log", "Requested/actual route capability diagnostics", enabled = false, disabledReason = "Use Import/Export -> Export Diagnostics to capture output logs", onClick = {}) }
    item {
        val context = LocalContext.current
        SettingActionRow(
            "Reset EQ Presets and Audio Defaults",
            "Clears current audio defaults; Room-backed user presets will need a confirmation flow later",
            onClick = {
                onReset()
                Toast.makeText(context, "Restored to defaults", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SettingsSurface.copy(alpha = 0.42f))
            .border(1.dp, SettingsLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(Locale.US),
            color = PulseBlue,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.72f),
        )
        Text(
            value,
            color = SettingsSubtext,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.28f),
        )
    }
}

@Composable
private fun <T> AudioEnumRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, color = SettingsText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        options.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { option ->
                    AudioOptionChip(
                        label = label(option),
                        selected = option == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(option) },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AudioOptionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) PulseBlue else Color.Transparent)
            .border(1.dp, if (selected) PulseBlue else SettingsLine, RoundedCornerShape(20.dp))
            .settingsPressEffect(interactionSource, enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF101115) else if (enabled) SettingsSubtext else SettingsDisabled,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SmallSettingsButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SettingsSurface)
            .border(1.dp, SettingsLine, RoundedCornerShape(8.dp))
            .settingsPressEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = SettingsText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ParametricBandSettingsRow(band: ParametricEqBand, onBand: (ParametricEqBand) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (band.enabled) PulseBlue.copy(alpha = 0.10f) else SettingsSurface.copy(alpha = 0.52f))
            .border(1.dp, if (band.enabled) PulseBlue.copy(alpha = 0.28f) else SettingsLine, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(band.type.label, color = SettingsText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${band.frequencyHz.roundToInt()} Hz  ${signedDb(band.gainDb)}  Q ${String.format(Locale.US, "%.1f", band.q)}", color = SettingsSubtext, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Switch(
                checked = band.enabled,
                onCheckedChange = { onBand(band.copy(enabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PulseBlue,
                    checkedTrackColor = PulseBlue.copy(alpha = 0.45f),
                ),
            )
        }
        AudioEnumRow("Filter Type", ParametricFilterType.entries, band.type, { it.label }) {
            onBand(band.copy(type = it))
        }
        SettingSliderRow("Frequency", value = band.frequencyHz, onValueChange = { onBand(band.copy(frequencyHz = it)) }, valueRange = 20f..20000f, steps = 79, valueLabel = "${band.frequencyHz.roundToInt()} Hz", startLabel = "20", endLabel = "20k")
        SettingSliderRow("Gain", value = band.gainDb, onValueChange = { onBand(band.copy(gainDb = it)) }, valueRange = -12f..12f, steps = 47, valueLabel = signedDb(band.gainDb), startLabel = "-12", endLabel = "+12")
        SettingSliderRow("Q", value = band.q, onValueChange = { onBand(band.copy(q = it)) }, valueRange = 0.1f..18f, steps = 35, valueLabel = String.format(Locale.US, "%.1f", band.q), startLabel = "0.1", endLabel = "18")
    }
}

private fun signedDb(value: Float): String =
    String.format(Locale.US, "%+.1fdB", value)

private fun effectiveDbLabel(requestedDb: Float, effectiveDb: Float): String =
    if (abs(requestedDb - effectiveDb) > AUDIO_EQ_INPUT_STEP_DB / 2f) {
        "eff ${signedDb(effectiveDb)}"
    } else {
        signedDb(effectiveDb)
    }

private fun GraphicEqBand.frequencyLabel(): String =
    if (frequencyHz >= 1000f) {
        String.format(Locale.US, "%.0fk", frequencyHz / 1000f)
    } else {
        "${frequencyHz.roundToInt()}Hz"
    }

private fun androidx.compose.foundation.lazy.LazyListScope.visualizationItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
) {
    val vis = settings.visualization
    val visualizerEnabledInBeta = false
    val visualizerGateReason = "Visualizer runtime is not enabled in this beta"
    item { SectionLabel("Visualization") }
    item { SettingInfoBlock("Visualizer Gate", visualizerGateReason) }
    item { SettingSwitchRow("Visualization On Player Screen", "Can be switched on from the player screen", vis.playerVisualizationEnabled, { onSettings(settings.copy(visualization = vis.copy(playerVisualizationEnabled = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item {
        SettingSegmentedRow(
            "Equalizer Screen Spectrum",
            options = SpectrumMode.entries.map { it.label },
            selected = vis.equalizerSpectrumMode.label,
            onSelected = { label -> onSettings(settings.copy(visualization = vis.copy(equalizerSpectrumMode = SpectrumMode.entries.first { it.label == label }))) },
            subtitle = visualizerGateReason,
            enabled = visualizerEnabledInBeta,
        )
    }
    item { SettingSwitchRow("Visualization in Library", "Visible when visualization on player screen is enabled", vis.libraryVisualizationEnabled, { onSettings(settings.copy(visualization = vis.copy(libraryVisualizationEnabled = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSliderRow("Preset Duration", visualizerGateReason, vis.presetDurationMs / 1000f, { onSettings(settings.copy(visualization = vis.copy(presetDurationMs = (it * 1000).toInt()))) }, 1f..60f, 58, "${vis.presetDurationMs / 1000}s", enabled = visualizerEnabledInBeta) }
    item { SettingSliderRow("Top Visualization Panel Opacity", visualizerGateReason, value = vis.topPanelOpacityPercent.toFloat(), onValueChange = { onSettings(settings.copy(visualization = vis.copy(topPanelOpacityPercent = it.toInt()))) }, valueRange = 0f..100f, steps = 19, valueLabel = "${vis.topPanelOpacityPercent}%", enabled = visualizerEnabledInBeta) }
    item { SettingSliderRow("Faded Controls Opacity", visualizerGateReason, value = vis.fadedControlsOpacityPercent.toFloat(), onValueChange = { onSettings(settings.copy(visualization = vis.copy(fadedControlsOpacityPercent = it.toInt()))) }, valueRange = 0f..100f, steps = 19, valueLabel = "${vis.fadedControlsOpacityPercent}%", enabled = visualizerEnabledInBeta) }
    item { SettingSliderRow("UI Timeout", visualizerGateReason, vis.uiTimeoutMs.toFloat(), { onSettings(settings.copy(visualization = vis.copy(uiTimeoutMs = it.toInt()))) }, 250f..10000f, 38, "${vis.uiTimeoutMs}ms", enabled = visualizerEnabledInBeta) }
    item { SettingSwitchRow("Visible Album Art", "Keep album art visible during visualization", vis.visibleAlbumArt, { onSettings(settings.copy(visualization = vis.copy(visibleAlbumArt = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("Ignore Touch", "Ignore first touch in faded-controls mode", vis.ignoreTouch, { onSettings(settings.copy(visualization = vis.copy(ignoreTouch = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSliderRow("Track Opacity", visualizerGateReason, vis.trackOpacityPercent.toFloat(), { onSettings(settings.copy(visualization = vis.copy(trackOpacityPercent = it.toInt()))) }, 0f..100f, 19, "${vis.trackOpacityPercent}%", enabled = visualizerEnabledInBeta) }
    item { SectionLabel("Rendering") }
    item { SettingSwitchRow("Hide System Bars For Full Screen", "Requires Android system-bar permission behavior to cooperate", vis.hideSystemBarsFullscreen, { onSettings(settings.copy(visualization = vis.copy(hideSystemBarsFullscreen = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("Scaled Bars For Faded Controls", "Scale bars to album-art area when controls are visible", vis.scaledBarsForFadedControls, { onSettings(settings.copy(visualization = vis.copy(scaledBarsForFadedControls = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("HD", "Increased visualization resolution; disabled until the visualizer runtime is enabled", vis.hdEnabled, { onSettings(settings.copy(visualization = vis.copy(hdEnabled = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("Crop Aspect", "Crop visualization instead of scaling it", vis.cropAspect, { onSettings(settings.copy(visualization = vis.copy(cropAspect = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("Force 30 FPS", "Reduce frame rate to 30 frames per second", vis.force30Fps, { onSettings(settings.copy(visualization = vis.copy(force30Fps = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("Strict", "Prefer visual match over rendering speed where supported", vis.strictPresetMatching, { onSettings(settings.copy(visualization = vis.copy(strictPresetMatching = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSliderRow("Visualization Delay", visualizerGateReason, vis.visualizationDelayMs.toFloat(), { onSettings(settings.copy(visualization = vis.copy(visualizationDelayMs = it.toInt()))) }, -5000f..5000f, 39, "${vis.visualizationDelayMs}ms", "-5s", "+5s", enabled = visualizerEnabledInBeta) }
    item { SectionLabel("Presets") }
    item { SettingActionRow("Rescan Presets", "Built-in and folder preset scan requires the visualizer preset runtime adapter", enabled = false, disabledReason = "Phase 5 Room-backed preset index", onClick = {}) }
    item { SettingActionRow("Full Presets Rescan", "Clear scanned preset info and rescan", enabled = false, disabledReason = "Phase 5 Room-backed preset index", onClick = {}) }
    item { SettingSwitchRow("Hide Unliked Presets", "Unliked presets are completely hidden", vis.hideUnlikedPresets, { onSettings(settings.copy(visualization = vis.copy(hideUnlikedPresets = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("Spectrum Bars/Built-in Presets", null, vis.spectrumBarsEnabled, { onSettings(settings.copy(visualization = vis.copy(spectrumBarsEnabled = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("Built-in Presets", null, vis.builtInPresetsEnabled, { onSettings(settings.copy(visualization = vis.copy(builtInPresetsEnabled = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { SettingSwitchRow("Folder Presets", "/Android/data/com.pulsedeck.app/visualizer_presets", vis.folderPresetsEnabled, { onSettings(settings.copy(visualization = vis.copy(folderPresetsEnabled = it))) }, enabled = visualizerEnabledInBeta, disabledReason = visualizerGateReason) }
    item { RestoreDefaultsRow { onSettings(settings.copy(visualization = VisualizationSettings())) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.backgroundItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
) {
    val bg = settings.background
    item { BackgroundPreviewCard(bg) }
    item { SettingSwitchRow("Enable Blurred Backgrounds", null, bg.blurred, { onSettings(settings.copy(background = bg.copy(blurred = it))) }) }
    item { SettingSwitchRow("List Background", null, bg.listBackground, { onSettings(settings.copy(background = bg.copy(listBackground = it))) }, enabled = bg.blurred, disabledReason = "Enable blurred backgrounds first") }
    item { SettingSwitchRow("Lyrics Background", null, bg.lyricsBackground, { onSettings(settings.copy(background = bg.copy(lyricsBackground = it))) }, enabled = bg.blurred, disabledReason = "Enable blurred backgrounds first") }
    item { SettingInfoBlock("Readability", "Skins may override or dim backgrounds. Lyrics and lists add a protective scrim so text remains readable.") }
    item { SettingSliderRow("Background Gradient", value = bg.gradient, onValueChange = { onSettings(settings.copy(background = bg.copy(gradient = it))) }, valueRange = 0f..10f, steps = 9, valueLabel = bg.gradient.toInt().toString(), startLabel = "None", endLabel = "Max") }
    item { SettingHexColorRow("Background Gradient Color", bg.gradientColor, onColor = { onSettings(settings.copy(background = bg.copy(gradientColor = it))) }) }
    item { SettingColorSwatchRow(bg.gradientColor, onColor = { onSettings(settings.copy(background = bg.copy(gradientColor = it))) }) }
    item { SettingSwitchRow("Background Gradient For Lists", "Also apply background gradient for list background", bg.gradientForLists, { onSettings(settings.copy(background = bg.copy(gradientForLists = it))) }, enabled = bg.blurred && bg.listBackground, disabledReason = "List background must be enabled") }
    item { SettingSliderRow("Background Blur", value = bg.blur, onValueChange = { onSettings(settings.copy(background = bg.copy(blur = it))) }, valueRange = 0f..10f, steps = 9, valueLabel = bg.blur.toInt().toString(), startLabel = "Less", endLabel = "More") }
    item { SettingSliderRow("Background Details", value = bg.details, onValueChange = { onSettings(settings.copy(background = bg.copy(details = it))) }, valueRange = 0f..10f, steps = 9, valueLabel = bg.details.toInt().toString(), startLabel = "Solid Color", endLabel = "Detailed") }
    item { SettingSliderRow("Background Intensity", "Some skins may adjust this option to make text readable", bg.intensity * 100f, { onSettings(settings.copy(background = bg.copy(intensity = it / 100f))) }, 0f..100f, 19, "${(bg.intensity * 100).toInt()}%") }
    item { SettingSliderRow("Background Saturation", "Some skins may adjust this option to make text readable", bg.saturation * 100f, { onSettings(settings.copy(background = bg.copy(saturation = it / 100f))) }, 0f..200f, 19, "${(bg.saturation * 100).toInt()}%") }
    item { SettingSwitchRow("Readability Protection", "Auto dim lists and lyrics to protect text contrast", bg.readabilityProtection.enabled, { onSettings(settings.copy(background = bg.copy(readabilityProtection = bg.readabilityProtection.copy(enabled = it)))) }) }
    item { RestoreDefaultsRow { onSettings(settings.copy(background = BackgroundSettings(readabilityProtection = ReadabilityProtectionSettings()))) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.albumArtItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
    cacheStatus: String,
    onlineProviderConfigured: Boolean,
    onRefreshArtwork: () -> Unit,
    onClearArtworkCache: () -> Unit,
) {
    val art = settings.albumArt
    val online = settings.misc.online
    item { SettingInfoBlock("Artwork Source Order", "PulseDeck resolves embedded artwork, folder/direct local artwork, existing cover URLs, then online cover art, and finally the placeholder image.") }
    item {
        SettingSwitchRow(
            "Download Album Art",
            "Allow online lookup when local artwork is missing",
            art.downloadAlbumArt,
            { onSettings(settings.copy(albumArt = art.copy(downloadAlbumArt = it))) },
            leadingIconRes = R.drawable.iconoir_cloud_download,
            enabled = onlineProviderConfigured && !online.offlineMode,
            disabledReason = if (online.offlineMode) "Offline mode is enabled" else "Online artwork provider not configured",
        )
    }
    item {
        SettingSegmentedRow(
            "Artwork Provider",
            options = AlbumArtworkProviderMode.entries.map { it.label },
            selected = art.providerMode.label,
            onSelected = { label -> onSettings(settings.copy(albumArt = art.copy(providerMode = AlbumArtworkProviderMode.entries.first { it.label == label }))) },
            subtitle = "Cover Art Archive first, with YouTube thumbnails available as fallback",
            enabled = art.downloadAlbumArt && onlineProviderConfigured && !online.offlineMode,
        )
    }
    item {
        SettingSwitchRow(
            "Highest Quality Covers",
            "Search multiple cover sources and keep the best front cover",
            art.highestQualityCovers,
            { onSettings(settings.copy(albumArt = art.copy(highestQualityCovers = it))) },
            enabled = art.downloadAlbumArt && onlineProviderConfigured && !online.offlineMode,
            disabledReason = when {
                online.offlineMode -> "Offline mode is enabled"
                !art.downloadAlbumArt -> "Album-art downloads are off"
                else -> "Online artwork provider not configured"
            },
        )
    }
    item {
        SettingSwitchRow(
            "Auto Replace Existing Art",
            "Replace only when online art is clearly higher resolution",
            art.autoReplaceExistingArt,
            { onSettings(settings.copy(albumArt = art.copy(autoReplaceExistingArt = it))) },
            enabled = art.downloadAlbumArt && art.highestQualityCovers && !online.offlineMode,
            disabledReason = if (!art.highestQualityCovers) "Highest Quality Covers is off" else "Offline mode is enabled",
        )
    }
    item {
        SettingSliderRow(
            "Minimum Cover Upgrade",
            "Required resolution improvement before replacing existing art",
            art.minimumUpgradePercent.toFloat(),
            { onSettings(settings.copy(albumArt = art.copy(minimumUpgradePercent = it.toInt()))) },
            0f..300f,
            29,
            "+${art.minimumUpgradePercent}%",
            "Any",
            "+300%",
            enabled = art.downloadAlbumArt && art.highestQualityCovers && art.autoReplaceExistingArt,
        )
    }
    item {
        SettingSwitchRow(
            "Save Folder Cover",
            "Write cover.jpg into the album folder when Android allows it",
            art.saveFolderCover,
            { onSettings(settings.copy(albumArt = art.copy(saveFolderCover = it))) },
            enabled = art.downloadAlbumArt && art.highestQualityCovers && !online.offlineMode,
            disabledReason = when {
                online.offlineMode -> "Offline mode is enabled"
                !art.downloadAlbumArt -> "Album-art downloads are off"
                else -> "Highest Quality Covers is off"
            },
        )
    }
    item {
        ArtworkProviderOrderRow(
            order = art.providerOrder,
            enabled = art.downloadAlbumArt && art.highestQualityCovers && !online.offlineMode,
            disabledReason = when {
                online.offlineMode -> "Offline mode is enabled"
                !art.downloadAlbumArt -> "Album-art downloads are off"
                !art.highestQualityCovers -> "Highest Quality Covers is off"
                else -> null
            },
            onMove = { index, delta ->
                onSettings(settings.copy(albumArt = art.copy(providerOrder = art.providerOrder.moveProvider(index, delta))))
            },
        )
    }
    item { SettingSwitchRow("Prefer Embedded Artwork", "Use images stored inside audio files first", art.preferEmbeddedArtwork, { onSettings(settings.copy(albumArt = art.copy(preferEmbeddedArtwork = it))) }) }
    item { SettingSwitchRow("Prefer Folder Image", "Use cover.jpg/folder images from album folders", art.preferFolderImage, { onSettings(settings.copy(albumArt = art.copy(preferFolderImage = it))) }) }
    item {
        SettingSwitchRow(
            "Prefer Online Image",
            "Can replace low-quality embedded art after review",
            art.preferOnlineImage,
            { onSettings(settings.copy(albumArt = art.copy(preferOnlineImage = it))) },
            enabled = art.downloadAlbumArt && onlineProviderConfigured && !online.offlineMode,
            disabledReason = when {
                online.offlineMode -> "Offline mode is enabled"
                !art.downloadAlbumArt -> "Album-art downloads are off"
                else -> "Online artwork provider not configured"
            },
        )
    }
    item { SettingSwitchRow("Show Default Image", "Use PulseDeck placeholder art when a track has no artwork", art.showDefaultImageWhenMissing, { onSettings(settings.copy(albumArt = art.copy(showDefaultImageWhenMissing = it))) }) }
    item {
        SettingSegmentedRow(
            "Artwork Quality",
            options = AlbumArtQuality.entries.map { it.label },
            selected = art.quality.label,
            onSelected = { label -> onSettings(settings.copy(albumArt = art.copy(quality = AlbumArtQuality.entries.first { it.label == label }))) },
        )
    }
    item { SettingSliderRow("Cache Size", cacheStatus, art.cacheSizeMb.toFloat(), { onSettings(settings.copy(albumArt = art.copy(cacheSizeMb = it.toInt()))) }, 64f..512f, 14, "${art.cacheSizeMb} MB", "64 MB", "512 MB") }
    item { SettingActionRow("Force Refresh Artwork", "Invalidate memory and disk lookups so artwork reloads on demand", onClick = onRefreshArtwork) }
    item { SettingActionRow("Clear Cache", "Remove cached artwork files and reset missing-art markers", onClick = onClearArtworkCache) }
    item { RestoreDefaultsRow { onSettings(settings.copy(albumArt = AlbumArtSettings())) } }
}

@Composable
private fun ArtworkProviderOrderRow(
    order: List<AlbumArtworkProviderId>,
    enabled: Boolean,
    disabledReason: String?,
    onMove: (Int, Int) -> Unit,
) {
    val normalizedOrder = order.normalizedProviderOrder()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Provider Order", color = if (enabled) SettingsText else SettingsDisabled, fontSize = 20.sp, fontWeight = SettingsTitleWeight)
        Text(
            disabledReason.takeIf { !enabled } ?: "Move a provider earlier when you want it searched first",
            color = if (enabled) SettingsSubtext else SettingsDisabled,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = SettingsBodyWeight,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            normalizedOrder.forEachIndexed { index, provider ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${index + 1}.",
                        color = if (enabled) SettingsSubtext else SettingsDisabled,
                        fontSize = 13.sp,
                        fontWeight = SettingsChromeWeight,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        provider.label,
                        color = if (enabled) SettingsText else SettingsDisabled,
                        fontSize = 15.sp,
                        fontWeight = SettingsBodyWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ProviderMoveButton(
                        enabled = enabled && index > 0,
                        rotationZ = 90f,
                        contentDescription = "Move ${provider.label} earlier",
                    ) { onMove(index, -1) }
                    ProviderMoveButton(
                        enabled = enabled && index < normalizedOrder.lastIndex,
                        rotationZ = -90f,
                        contentDescription = "Move ${provider.label} later",
                    ) { onMove(index, 1) }
                }
            }
        }
    }
}

@Composable
private fun ProviderMoveButton(
    enabled: Boolean,
    rotationZ: Float,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(38.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.iconoir_nav_arrow_left),
            contentDescription = contentDescription,
            tint = if (enabled) PulseBlue else SettingsDisabled,
            modifier = Modifier.size(22.dp).graphicsLayer(rotationZ = rotationZ),
        )
    }
}

private fun List<AlbumArtworkProviderId>.normalizedProviderOrder(): List<AlbumArtworkProviderId> {
    val ordered = distinct().ifEmpty { DefaultAlbumArtworkProviderOrder }
    return ordered + DefaultAlbumArtworkProviderOrder.filterNot { it in ordered }
}

private fun List<AlbumArtworkProviderId>.moveProvider(index: Int, delta: Int): List<AlbumArtworkProviderId> {
    val normalized = normalizedProviderOrder().toMutableList()
    if (index !in normalized.indices) return normalized
    val target = (index + delta).coerceIn(normalized.indices)
    if (target == index) return normalized
    val moving = normalized[index]
    normalized[index] = normalized[target]
    normalized[target] = moving
    return normalized
}

@Composable
private fun BackgroundPreviewCard(settings: BackgroundSettings) {
    val normalized = settings.normalized()
    val gradientMix = (normalized.gradient / 10f).coerceIn(0f, 1f)
    val detailAlpha = if (normalized.blurred) normalized.details / 10f else 0f
    val gradientColor = parseHexColor(normalized.gradientColor)
    val top = Color(
        red = (0.24f + normalized.saturation * 0.10f).coerceIn(0f, 1f),
        green = (0.42f + normalized.saturation * 0.12f).coerceIn(0f, 1f),
        blue = (0.86f + normalized.saturation * 0.04f).coerceIn(0f, 1f),
    )
    val lower = Color(
        red = (0.92f * normalized.intensity).coerceIn(0f, 1f),
        green = (0.34f * normalized.intensity).coerceIn(0f, 1f),
        blue = (0.20f * normalized.intensity).coerceIn(0f, 1f),
    )
    val scrim = if (normalized.readabilityProtection.enabled) 0.48f else 0.20f
    Box(
        Modifier
            .fillMaxWidth()
            .height(152.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (normalized.blurred) top.copy(alpha = 0.38f + gradientMix * 0.18f) else SettingsSurface,
                        lower.copy(alpha = 0.30f + gradientMix * 0.20f),
                        gradientColor.copy(alpha = 0.20f + gradientMix * 0.32f),
                    ),
                ),
            )
            .border(1.dp, SettingsLine, RoundedCornerShape(8.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(top.copy(alpha = 0.18f * detailAlpha), size.minDimension * 0.42f, Offset(size.width * 0.18f, size.height * 0.32f))
            drawCircle(lower.copy(alpha = 0.20f * detailAlpha), size.minDimension * 0.50f, Offset(size.width * 0.82f, size.height * 0.42f))
            drawCircle(Color.White.copy(alpha = 0.05f * detailAlpha), size.minDimension * 0.28f, Offset(size.width * 0.50f, size.height * 0.82f))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text("Live Background Preview", color = Color.White, fontSize = 18.sp, fontWeight = SettingsTitleWeight)
            Text(
                "Blur ${normalized.blur.toInt()}  Detail ${normalized.details.toInt()}  Saturation ${(normalized.saturation * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 12.sp,
                fontWeight = SettingsBodyWeight,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingHexColorRow(
    title: String,
    colorHex: String,
    enabled: Boolean = true,
    onColor: (String) -> Unit,
) {
    var input by rememberSaveable(colorHex) { mutableStateOf(colorHex) }
    SettingRowShell(
        title = title,
        subtitle = "Six-digit hex color",
        enabled = enabled,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(width = 48.dp, height = 34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(parseHexColor(input))
                        .border(1.dp, SettingsLine, RoundedCornerShape(17.dp)),
                )
                BasicTextField(
                    value = input,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase(Locale.US).filterIndexed { index, c ->
                            c in '0'..'9' || c in 'A'..'F' || (c == '#' && index == 0)
                        }.let { if (it.startsWith("#")) it.take(7) else it.take(6) }
                        input = if (cleaned.startsWith("#")) cleaned else "#$cleaned"
                        normalizedSettingsHexColor(input)?.let(onColor)
                    },
                    singleLine = true,
                    enabled = enabled,
                    textStyle = TextStyle(color = if (enabled) SettingsText else SettingsDisabled, fontSize = 16.sp, fontWeight = SettingsBodyWeight),
                    modifier = Modifier
                        .width(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SettingsSurface)
                        .border(1.dp, SettingsLine, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun SettingColorSwatchRow(
    selectedColor: String,
    onColor: (String) -> Unit,
) {
    val swatches = listOf("#000000", "#14213D", "#2F67D8", "#4D9F70", "#D65E36", "#6D3FD9")
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Gradient Swatches", color = SettingsMuted, fontSize = 14.sp, fontWeight = SettingsChromeWeight)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { hex ->
                val selected = normalizedSettingsHexColor(selectedColor) == hex
                val interactionSource = remember(hex) { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(hex))
                        .border(2.dp, if (selected) PulseBlue else SettingsLine, CircleShape)
                        .settingsPressEffect(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = null) { onColor(hex) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.libraryItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
) {
    val library = settings.library
    val libraryGateReason = "Advanced library scan rules are not active in this beta."
    item { SettingActionRow("Music Folders", "${library.musicFolders.size} configured folders", enabled = false, disabledReason = libraryGateReason, onClick = {}) }
    item { SettingActionRow("Rescan Library", "Refresh MediaStore and PulseDeck library cache", enabled = false, disabledReason = "Phase 5 Room-backed library cache", onClick = {}) }
    item { SettingActionRow("Full Rescan", "Clear scan cache and rebuild Room library state", enabled = false, disabledReason = "Phase 5 Room-backed library cache", onClick = {}) }
    item { SettingSwitchRow("Include Short Tracks", "Include short audio clips and samples", library.includeShortTracks, { onSettings(settings.copy(library = library.copy(includeShortTracks = it))) }, enabled = false, disabledReason = libraryGateReason) }
    item { SettingSliderRow("Short Track Threshold", libraryGateReason, library.shortTrackThresholdSeconds.toFloat(), { onSettings(settings.copy(library = library.copy(shortTrackThresholdSeconds = it.toInt()))) }, 1f..120f, 118, "${library.shortTrackThresholdSeconds}s", enabled = false) }
    item {
        SettingSegmentedRow(
            "Sort Order",
            options = LibrarySortMode.entries.map { it.label },
            selected = library.sortMode.label,
            onSelected = { label -> onSettings(settings.copy(library = library.copy(sortMode = LibrarySortMode.entries.first { it.label == label }))) },
            subtitle = libraryGateReason,
            enabled = false,
        )
    }
    item { SettingSwitchRow("Folder Hierarchy Mode", "Show a folder-first library surface", library.folderHierarchyMode, { onSettings(settings.copy(library = library.copy(folderHierarchyMode = it))) }, enabled = false, disabledReason = libraryGateReason) }
    item { SettingSwitchRow("Preserve Queue On Restart", "Restore queue and current list after app restart", library.preserveQueueOnRestart, { onSettings(settings.copy(library = library.copy(preserveQueueOnRestart = it))) }, enabled = false, disabledReason = "Queue restore is controlled by playback persistence, not this library switch") }
    item { SettingSwitchRow("Rescan On Launch", "Useful while testing new local music folders", library.rescanOnLaunch, { onSettings(settings.copy(library = library.copy(rescanOnLaunch = it))) }, enabled = false, disabledReason = libraryGateReason) }
    item { RestoreDefaultsRow { onSettings(settings.copy(library = LibrarySettings())) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.headsetItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
) {
    val headset = settings.headsetBluetooth
    item { SectionLabel("Connection") }
    item { SettingSwitchRow("Pause On Headset Disconnect", "Pause when wired/Bluetooth headset or USB DAC disconnects", headset.pauseOnHeadsetDisconnect, { onSettings(settings.copy(headsetBluetooth = headset.copy(pauseOnHeadsetDisconnect = it))) }) }
    item { SettingSwitchRow("Resume On Wired Headset", "Resume playing when wired headset is connected", headset.resumeOnWiredHeadset, { onSettings(settings.copy(headsetBluetooth = headset.copy(resumeOnWiredHeadset = it))) }, enabled = false, disabledReason = "Auto-resume-on-connect is not wired in this beta") }
    item { SettingSwitchRow("Resume On Bluetooth", "Resume playing when Bluetooth device is connected", headset.resumeOnBluetooth, { onSettings(settings.copy(headsetBluetooth = headset.copy(resumeOnBluetooth = it))) }, enabled = false, disabledReason = "Auto-resume-on-connect is not wired in this beta") }
    item { SectionLabel("Buttons") }
    item { SettingSwitchRow("Respond To Buttons", "Enable headset/Bluetooth controls", headset.respondToButtons, { onSettings(settings.copy(headsetBluetooth = headset.copy(respondToButtons = it))) }) }
    item {
        SettingSegmentedRow(
            "Wired Headset",
            ButtonPressMode.entries.map { it.label },
            headset.wiredButtonMode.label,
            onSelected = { label -> onSettings(settings.copy(headsetBluetooth = headset.copy(wiredButtonMode = ButtonPressMode.entries.first { it.label == label }))) },
            enabled = headset.respondToButtons,
        )
    }
    item {
        SettingSegmentedRow(
            "Bluetooth",
            ButtonPressMode.entries.map { it.label },
            headset.bluetoothButtonMode.label,
            onSelected = { label -> onSettings(settings.copy(headsetBluetooth = headset.copy(bluetoothButtonMode = ButtonPressMode.entries.first { it.label == label }))) },
            enabled = headset.respondToButtons,
        )
    }
    item { SettingSwitchRow("Strict Resume/Pause/Stop", "Treat stray key commands as duplicate toggles", headset.strictResumePauseStop, { onSettings(settings.copy(headsetBluetooth = headset.copy(strictResumePauseStop = it))) }) }
    item { SettingSliderRow("Ignore Bluetooth Commands", "Ignore command storms after route connection", headset.ignoreBluetoothCommandsSeconds.toFloat(), { onSettings(settings.copy(headsetBluetooth = headset.copy(ignoreBluetoothCommandsSeconds = it.toInt()))) }, 0f..20f, 19, "${headset.ignoreBluetoothCommandsSeconds} sec") }
    item { SettingSwitchRow("Ignore Repeat/Shuffle", "Ignore Bluetooth repeat and shuffle commands completely", headset.ignoreRepeatShuffle, { onSettings(settings.copy(headsetBluetooth = headset.copy(ignoreRepeatShuffle = it))) }) }
    item { SettingSwitchRow("Beep", null, headset.beep, { onSettings(settings.copy(headsetBluetooth = headset.copy(beep = it))) }) }
    item {
        SettingSwitchRow(
            "Beep More",
            "Beep on all commands, including notifications",
            headset.beepMore,
            { onSettings(settings.copy(headsetBluetooth = headset.copy(beepMore = it))) },
            enabled = headset.beep,
            disabledReason = "Command Beeps are off",
        )
    }
    item {
        SettingSliderRow(
            title = "Beep Volume",
            subtitle = "Command cues stay capped below the music stream",
            value = headset.beepVolume,
            onValueChange = { onSettings(settings.copy(headsetBluetooth = headset.copy(beepVolume = it))) },
            valueRange = 0f..0.4f,
            steps = 19,
            valueLabel = "${(headset.beepVolume * 100f).roundToInt()}%",
            startLabel = "Muted",
            endLabel = "40%",
            enabled = headset.beep,
        )
    }
    item { SettingSwitchRow("Vibrate", null, headset.vibrate, { onSettings(settings.copy(headsetBluetooth = headset.copy(vibrate = it))) }) }
    item {
        val commandLog by PulseBluetoothCommandLogStore.log.collectAsState()
        SettingInfoBlock(
            "Last Processed Commands",
            commandLog.commands.takeLast(6).joinToString("\n").ifBlank { "No commands captured yet." },
        )
    }
    item { RestoreDefaultsRow { onSettings(settings.copy(headsetBluetooth = HeadsetBluetoothSettings())) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lockItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
) {
    val lock = settings.lockScreen
    val lockMetadataGateReason = "Custom lock screen styling is not available in this beta. Android media controls still work from the lock screen."
    val customLockGateReason = "Custom lock screen styling is not available in this beta. Android media controls still work from the lock screen."
    item { SectionLabel("Android Lock Screen") }
    item { SettingInfoBlock("System Media Controls", "Android lock screen support is provided through MediaSession metadata, notification controls, Bluetooth track info, watches, and other apps.") }
    item { SettingSwitchRow("Album Art", "Show album art on Android lock screen", lock.albumArtOnLockScreen, { onSettings(settings.copy(lockScreen = lock.copy(albumArtOnLockScreen = it))) }, enabled = false, disabledReason = lockMetadataGateReason) }
    item { SettingSwitchRow("Blur", "Blur lock-screen album art when the device supports it", lock.blurAlbumArt, { onSettings(settings.copy(lockScreen = lock.copy(blurAlbumArt = it))) }, enabled = false, disabledReason = lockMetadataGateReason) }
    item { SettingSwitchRow("Show Default Image", "Send default placeholder image when no album art exists", lock.showDefaultImageWhenMissing, { onSettings(settings.copy(lockScreen = lock.copy(showDefaultImageWhenMissing = it))) }, enabled = false, disabledReason = lockMetadataGateReason) }
    item { SectionLabel("PulseDeck Lock Screen") }
    item { SettingSwitchRow("Show On Lock Screen", "Optional custom overlay; native system controls stay the default", lock.customLockScreenEnabled, { onSettings(settings.copy(lockScreen = lock.copy(customLockScreenEnabled = it))) }, enabled = false, disabledReason = customLockGateReason) }
    item { SettingSwitchRow("Shorter Timeout", "Apply shorter screen timeout while the custom lock screen is active", lock.shorterTimeout, { onSettings(settings.copy(lockScreen = lock.copy(shorterTimeout = it))) }, enabled = false, disabledReason = customLockGateReason) }
    item { SettingActionRow("Open App Settings", "Some devices require additional lock-screen permissions", enabled = false, disabledReason = customLockGateReason, onClick = {}) }
    item { SettingSwitchRow("Landscape Layout", "Enable rotation to landscape layout", lock.landscapeLayout, { onSettings(settings.copy(lockScreen = lock.copy(landscapeLayout = it))) }, enabled = false, disabledReason = customLockGateReason) }
    item { SettingSwitchRow("Direct Unlock", "Unlock directly to home screen if possible", lock.directUnlock, { onSettings(settings.copy(lockScreen = lock.copy(directUnlock = it))) }, enabled = false, disabledReason = customLockGateReason) }
    item { RestoreDefaultsRow { onSettings(settings.copy(lockScreen = LockScreenSettings())) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.miscItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
    onClearOnlineCache: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearRecentlyPlayed: () -> Unit,
    onResetPremiumDeckPersonalization: () -> Unit,
    premiumDeckModelStatusTitle: String,
    premiumDeckModelStatusBody: String,
    onDeleteDownloads: () -> Unit,
) {
    val misc = settings.misc
    val scrobble = misc.scrobbling
    val auto = misc.androidAuto
    val online = misc.online
    val lyrics = settings.lyrics
    item { SectionLabel("Scrobbling") }
    item { SettingSwitchRow("Scrobble via Official Last.fm app", "Authenticated now-playing and scrobble submission", scrobble.lastFmEnabled, { onSettings(settings.copy(misc = misc.copy(scrobbling = scrobble.copy(lastFmEnabled = it)))) }, enabled = false, disabledReason = "Last.fm auth is disabled for V1; no API credentials are embedded") }
    item { SettingSwitchRow("Scrobble via Simple Scrobbler", "Broadcast compatible scrobble intents", scrobble.simpleScrobblerEnabled, { onSettings(settings.copy(misc = misc.copy(scrobbling = scrobble.copy(simpleScrobblerEnabled = it)))) }, enabled = false, disabledReason = "Simple Scrobbler broadcast adapter is not wired in this beta") }
    item { SettingInfoBlock("Scrobble Rule", "Submit after playback passes 30 seconds and either half the track or 4 minutes. Failed submissions go to a Room retry queue.") }
    item { SectionLabel("Android Auto") }
    item { SettingInfoBlock("Buttons", "Custom actions are exposed through MediaSession button preferences. Android Auto availability still depends on the connected host.") }
    (1..6).forEach { index ->
        item {
            AudioEnumRow(
                title = "Button $index",
                options = MediaAction.entries,
                selected = auto.customButtons.actionAt(index),
                label = { it.label },
                onSelected = { action ->
                    onSettings(settings.copy(misc = misc.copy(androidAuto = auto.copy(customButtons = auto.customButtons.withAction(index, action)))))
                },
            )
        }
    }
    item { SettingSwitchRow("Grid View for Categories", "Show categories as grids where Android Auto allows it", auto.gridViewForCategories, { onSettings(settings.copy(misc = misc.copy(androidAuto = auto.copy(gridViewForCategories = it)))) }, enabled = false, disabledReason = "Android Auto browse-tree rendering is not wired in this beta") }
    item { SettingSwitchRow("Images for Categories", "Show album/category artwork on connected surfaces", auto.imagesForCategories, { onSettings(settings.copy(misc = misc.copy(androidAuto = auto.copy(imagesForCategories = it)))) }, enabled = false, disabledReason = "Android Auto browse-tree rendering is not wired in this beta") }
    item { SettingSwitchRow("Album Art for Tracks", "Show album art for track entries", auto.albumArtForTracks, { onSettings(settings.copy(misc = misc.copy(androidAuto = auto.copy(albumArtForTracks = it)))) }, enabled = false, disabledReason = "Android Auto browse-tree rendering is not wired in this beta") }
    item { SettingSwitchRow("Now Playing List For Connected Devices/Apps", "Expose now-playing list to Wear, Android Auto, and compatible apps", auto.nowPlayingListForConnectedDevices, { onSettings(settings.copy(misc = misc.copy(androidAuto = auto.copy(nowPlayingListForConnectedDevices = it)))) }, enabled = false, disabledReason = "Connected-device queue export is not wired in this beta") }
    item { SectionLabel("Data Saver & Offline Mode") }
    item { SettingSwitchRow("Offline Mode", "Disable online artwork, lyrics lookup, search, and discovery refresh", online.offlineMode, { onSettings(settings.copy(misc = misc.copy(online = online.copy(offlineMode = it)))) }, leadingIconRes = R.drawable.iconoir_antenna_signal) }
    item { SettingSwitchRow("Offline Deck", "PremiumDeck only: show saved music and block PremiumDeck media, discovery, chart, podcast, and resolver network use", online.premiumDeckOfflineMode, { onSettings(settings.copy(misc = misc.copy(online = online.copy(premiumDeckOfflineMode = it)))) }, leadingIconRes = R.drawable.iconoir_cloud_download) }
    item { SettingSwitchRow("Use Proxy Fallback", "Use proxy endpoints only after direct streaming fails", online.useProxyFallback, { onSettings(settings.copy(misc = misc.copy(online = online.copy(useProxyFallback = it)))) }, leadingIconRes = R.drawable.iconoir_pin, enabled = !online.offlineMode, disabledReason = "Offline mode is enabled") }
    item { SettingSwitchRow("PulseDeck Data Saver", "Use the Data Saver stream policy for PremiumDeck playback", online.pulseDataSaver, { onSettings(settings.copy(misc = misc.copy(online = online.copy(pulseDataSaver = it)))) }, enabled = !online.offlineMode, disabledReason = "Offline mode is enabled") }
    item {
        OptionPickerRow(
            title = "Wi-Fi Stream Quality",
            subtitle = "Preferred PremiumDeck quality on Wi-Fi and unmetered networks",
            options = StreamingQualityOptions,
            selectedValue = online.wifiStreamingQuality.name,
            enabled = !online.offlineMode,
            onSelected = { value ->
                onSettings(settings.copy(misc = misc.copy(online = online.copy(wifiStreamingQuality = StreamingQualityPolicy.valueOf(value)))))
            },
        )
    }
    item {
        OptionPickerRow(
            title = "Cellular Stream Quality",
            subtitle = "Preferred PremiumDeck quality on cellular or metered networks",
            options = StreamingQualityOptions,
            selectedValue = online.cellularStreamingQuality.name,
            enabled = !online.offlineMode,
            onSelected = { value ->
                onSettings(settings.copy(misc = misc.copy(online = online.copy(cellularStreamingQuality = StreamingQualityPolicy.valueOf(value)))))
            },
        )
    }
    item {
        OptionPickerRow(
            title = "Roaming Stream Quality",
            subtitle = "Preferred PremiumDeck quality while cellular roaming",
            options = StreamingQualityOptions,
            selectedValue = online.roamingStreamingQuality.name,
            enabled = !online.offlineMode,
            onSelected = { value ->
                onSettings(settings.copy(misc = misc.copy(online = online.copy(roamingStreamingQuality = StreamingQualityPolicy.valueOf(value)))))
            },
        )
    }
    item {
        OptionPickerRow(
            title = "Data Saver Stream Quality",
            subtitle = "Used when PulseDeck Data Saver or Android Data Saver is active",
            options = StreamingQualityOptions,
            selectedValue = online.dataSaverStreamingQuality.name,
            enabled = !online.offlineMode,
            onSelected = { value ->
                onSettings(settings.copy(misc = misc.copy(online = online.copy(dataSaverStreamingQuality = StreamingQualityPolicy.valueOf(value)))))
            },
        )
    }
    item {
        OptionPickerRow(
            title = "Stream Preview Resolve",
            subtitle = "Controls PremiumDeck preview and upcoming stream URL preparation",
            options = StreamPreviewNetworkOptions,
            selectedValue = online.streamPreviewNetworkPolicy.name,
            enabled = !online.offlineMode,
            onSelected = { value ->
                onSettings(settings.copy(misc = misc.copy(online = online.copy(streamPreviewNetworkPolicy = StreamPreviewNetworkPolicy.valueOf(value)))))
            },
        )
    }
    item { SettingSwitchRow("Cellular Video Fallback", "Allow muxed video+audio fallback on cellular when no audio-only URL is available", online.allowMuxedStreamFallbackOnCellular, { onSettings(settings.copy(misc = misc.copy(online = online.copy(allowMuxedStreamFallbackOnCellular = it)))) }, enabled = !online.offlineMode, disabledReason = "Offline mode is enabled") }
    item { SettingSwitchRow("Data Saver Video Fallback", "Allow muxed video+audio fallback while Data Saver policy is active", online.allowMuxedStreamFallbackInDataSaver, { onSettings(settings.copy(misc = misc.copy(online = online.copy(allowMuxedStreamFallbackInDataSaver = it)))) }, enabled = !online.offlineMode, disabledReason = "Offline mode is enabled") }
    item { SettingSwitchRow("SponsorBlock", "Skip sponsored segments in supported PremiumDeck streams", online.sponsorBlock, { onSettings(settings.copy(misc = misc.copy(online = online.copy(sponsorBlock = it)))) }, leadingIconRes = R.drawable.iconoir_filter, enabled = !online.offlineMode, disabledReason = "Offline mode is enabled") }
    item { SettingSwitchRow("Automatic Song Picker", "Continue with a recommended stream when a stream queue ends", online.automaticSongPicker, { onSettings(settings.copy(misc = misc.copy(online = online.copy(automaticSongPicker = it)))) }, leadingIconRes = R.drawable.iconoir_music_double_note, enabled = !online.offlineMode, disabledReason = "Offline mode is enabled") }
    item { SettingSwitchRow("External Recommendations", "Use YouTube-derived discovery alongside PulseDeck local signals", online.externalRecommendations, { onSettings(settings.copy(misc = misc.copy(online = online.copy(externalRecommendations = it)))) }, leadingIconRes = R.drawable.iconoir_compass, enabled = !online.offlineMode, disabledReason = "Offline mode is enabled") }
    item {
        SettingSegmentedRow(
            title = "Smart Recommendations",
            subtitle = "Off disables taste learning, Lite uses fast heuristics, Smart lazily warms the tiny on-device model",
            options = PremiumDeckSmartRecommendationMode.entries.map { it.label },
            selected = online.smartRecommendationMode.label,
            onSelected = { label ->
                val mode = PremiumDeckSmartRecommendationMode.entries.first { it.label == label }
                onSettings(
                    settings.copy(
                        misc = misc.copy(
                            online = online.copy(
                                premiumDeckPersonalization = mode != PremiumDeckSmartRecommendationMode.Off,
                                smartRecommendationMode = mode,
                            ),
                        ),
                    ),
                )
            },
        )
    }
    item { SettingInfoBlock(premiumDeckModelStatusTitle, premiumDeckModelStatusBody) }
    item { SectionLabel("Lyrics & Online Lookup") }
    item { SettingSwitchRow("Lyrics", "Show cached plain or synced lyrics from the player", lyrics.enabled, { onSettings(settings.copy(lyrics = lyrics.copy(enabled = it))) }, leadingIconRes = R.drawable.iconoir_chat_bubble) }
    item { SettingSwitchRow("Prefer Synced Lyrics", "Highlight timed LRC lines when available", lyrics.preferSynced, { onSettings(settings.copy(lyrics = lyrics.copy(preferSynced = it))) }, enabled = lyrics.enabled, disabledReason = "Lyrics are off") }
    item { SettingSwitchRow("Online Lyrics Lookup", "Use LRCLIB first, then plain fallback providers", lyrics.onlineLookup, { onSettings(settings.copy(lyrics = lyrics.copy(onlineLookup = it))) }, enabled = lyrics.enabled && !online.offlineMode, disabledReason = if (!lyrics.enabled) "Lyrics are off" else "Offline mode is enabled") }
    item { SettingSliderRow("Lyrics Cache Size", "Plain and synced lyric files stored in app cache", lyrics.cacheSizeMb.toFloat(), { onSettings(settings.copy(lyrics = lyrics.copy(cacheSizeMb = it.toInt()))) }, 4f..128f, 30, "${lyrics.cacheSizeMb} MB", "4 MB", "128 MB", enabled = lyrics.enabled) }
    item { SectionLabel("Online Cache & History") }
    item { SettingActionRow("Clear Online Cache", "Remove cached artwork, lyrics, temp files, and discovery responses", leadingIconRes = R.drawable.iconoir_trash, onClick = onClearOnlineCache) }
    item { SettingActionRow("Clear Search History", "Remove cached PremiumDeck search responses", leadingIconRes = R.drawable.iconoir_clock_rotate_right, onClick = onClearSearchHistory) }
    item { SettingActionRow("Clear Recently Played", "Reset PremiumDeck recently played recommendations", leadingIconRes = R.drawable.iconoir_timer, onClick = onClearRecentlyPlayed) }
    item { SettingActionRow("Reset PremiumDeck Personalization", "Clear the on-device recommendation profile and replay buffer", leadingIconRes = R.drawable.iconoir_refresh_double, onClick = onResetPremiumDeckPersonalization) }
    item { SettingActionRow("Delete Downloads", "Remove saved PremiumDeck download links from the library shelf", leadingIconRes = R.drawable.iconoir_trash, onClick = onDeleteDownloads) }
    item { SectionLabel("Network & Integration Tweaks") }
    item { SettingSwitchRow("Metachanged Intent", "For external apps", misc.metachangedIntent, { onSettings(settings.copy(misc = misc.copy(metachangedIntent = it))) }, enabled = false, disabledReason = "Legacy metadata broadcast adapter is not wired in this beta") }
    item { SettingSwitchRow("Use Wakelock", "Use only when playback stops with the screen off", misc.useWakelock, { onSettings(settings.copy(misc = misc.copy(useWakelock = it))) }, enabled = false, disabledReason = "Playback wakelock policy is not wired to this switch") }
    item { SettingSwitchRow("Send Album Art for old API", "For older integrations that still need album-art broadcasts", misc.sendAlbumArtForOldApi, { onSettings(settings.copy(misc = misc.copy(sendAlbumArtForOldApi = it))) }, enabled = false, disabledReason = "Legacy album-art broadcast adapter is not wired in this beta") }
    item { SettingSwitchRow("Always Reload Skin", "Useful for skin development", misc.alwaysReloadSkin, { onSettings(settings.copy(misc = misc.copy(alwaysReloadSkin = it))) }, enabled = false, disabledReason = "Skin hot-reload runtime is not wired in this beta") }
    item { SettingSwitchRow("Pause on Screen Off", "Experimental", misc.pauseOnScreenOff, { onSettings(settings.copy(misc = misc.copy(pauseOnScreenOff = it))) }, enabled = false, disabledReason = "Screen-off playback policy is not wired to this switch") }
    item { SettingSwitchRow("Apply OP High Framerate", "Force higher display framerate via vendor APIs when available", misc.applyHighFramerate, { onSettings(settings.copy(misc = misc.copy(applyHighFramerate = it))) }, enabled = false, disabledReason = "Vendor high-framerate adapter is not wired in this beta") }
    item { SettingSliderRow("Network Stream Timeout", "Applied by the streaming data source when a new stream connection is opened", value = misc.networkStreamTimeoutSeconds.toFloat(), onValueChange = { onSettings(settings.copy(misc = misc.copy(networkStreamTimeoutSeconds = it.toInt()))) }, valueRange = 5f..300f, steps = 58, valueLabel = "${misc.networkStreamTimeoutSeconds}", startLabel = "Small", endLabel = "Never") }
    item { SettingSliderRow("Network Stream Buffer", "Used when the playback service builds its load-control buffer; active sessions may require a service rebuild", misc.networkStreamBufferMb, { onSettings(settings.copy(misc = misc.copy(networkStreamBufferMb = it))) }, 0.25f..16f, 62, "${misc.networkStreamBufferMb}MB", "Small", "More") }
    item {
        OptionPickerRow(
            title = "User Agent",
            subtitle = if (misc.userAgent.isBlank()) "Default streaming user agent" else misc.userAgent,
            options = UserAgentOptions,
            selectedValue = misc.userAgent,
            onSelected = { onSettings(settings.copy(misc = misc.copy(userAgent = it))) },
        )
    }
    item { SettingSwitchRow("Change Tracks By Long Volume Keys Press", "Requires Android permission or ADB grant", misc.changeTracksByLongVolumeKeys, { onSettings(settings.copy(misc = misc.copy(changeTracksByLongVolumeKeys = it))) }, enabled = false, disabledReason = "No permission") }
    item { RestoreDefaultsRow { onSettings(settings.copy(misc = MiscSettings())) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.aboutItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
    onExportDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val diagnostics = settings.diagnostics
    item { AboutPulseDeckHero(settings.schemaVersion) }
    item { SectionLabel("Project") }
    item {
        SettingActionRow(
            title = "Support This Project",
            subtitle = "Export privacy-redacted diagnostics",
            leadingIconRes = R.drawable.settings_support_premium,
            leadingIconFullColor = true,
            onClick = onExportDiagnostics,
        )
    }
    item { SectionLabel("Other") }
    item {
        SettingNavigationRow(
            title = "Open Source Licenses",
            subtitle = "${PulseDeckThirdPartyLicenses.releaseAuditInventory.size} attribution notices",
            onClick = onOpenLicenses,
        )
    }
    item { SectionLabel("Diagnostics") }
    item {
        SettingSwitchRow(
            "Send Errors To Developer",
            "Opt-in privacy-redacted crash logs",
            diagnostics.sendErrorsToDeveloper,
            { onSettings(settings.copy(diagnostics = diagnostics.copy(sendErrorsToDeveloper = it))) },
            leadingIconRes = R.drawable.settings_send_errors_premium,
            leadingIconFullColor = true,
        )
    }
    item { SettingSwitchRow("Include Audio Output Log", "Include route detection, requested/actual output, and fallback information", diagnostics.includeAudioOutputLog, { onSettings(settings.copy(diagnostics = diagnostics.copy(includeAudioOutputLog = it))) }) }
    item { SettingSwitchRow("Include Device Capabilities", "Attach Android version, route capabilities, and feature gates", diagnostics.includeDeviceCapabilities, { onSettings(settings.copy(diagnostics = diagnostics.copy(includeDeviceCapabilities = it))) }) }
    item { SettingSwitchRow("Redact Track Paths", "Remove local filesystem paths before sending diagnostics", diagnostics.redactTrackPaths, { onSettings(settings.copy(diagnostics = diagnostics.copy(redactTrackPaths = it))) }) }
    item { RestoreDefaultsRow { onSettings(settings.copy(diagnostics = DiagnosticsSettings())) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.licenseItems(
    include: (String) -> Boolean,
) {
    item {
        LicensesOverviewCard(
            noticeCount = PulseDeckThirdPartyLicenses.releaseAuditInventory.size,
        )
    }
    item { SectionLabel("Attribution Inventory") }
    items(
        PulseDeckThirdPartyLicenses.releaseAuditInventory.filter { notice ->
            include(notice.name) ||
                notice.artifacts.any(include) ||
                include(notice.version) ||
                include(notice.license) ||
                include(notice.copyright) ||
                include(notice.homepage) ||
                include(notice.usage) ||
                include(notice.note)
        },
        key = { notice -> notice.name },
    ) { notice ->
        LicenseNoticeCard(notice)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.importExportItems(
    settings: PulseSettingsState,
    include: (String) -> Boolean,
    onSettings: (PulseSettingsState) -> Unit,
    onExportSettings: () -> Unit,
    onExportPrivateBackup: () -> Unit,
    onImportSettings: () -> Unit,
    onExportDiagnostics: () -> Unit,
) {
    item { ExportPrivacyCard(settings) }
    item {
        SettingActionRow(
            "Export Portable Settings",
            "For another device or user. Redacts local folder paths, tokens, signed URLs, cache files, downloads, and private search history.",
            leadingIconRes = R.drawable.settings_export_data_premium,
            leadingIconFullColor = true,
            onClick = onExportSettings,
        )
    }
    item {
        SettingActionRow(
            "Export Safe Private Backup",
            "Beta-safe backup with a redaction manifest. It keeps supported preferences but still excludes local paths and secrets.",
            leadingIconRes = R.drawable.settings_export_data_premium,
            leadingIconFullColor = true,
            onClick = onExportPrivateBackup,
        )
    }
    item {
        SettingActionRow(
            "Import Settings or Backup",
            "Open JSON, validate schema, sanitize private fields, migrate supported versions, then apply.",
            leadingIconRes = R.drawable.settings_import_data_premium,
            leadingIconFullColor = true,
            onClick = onImportSettings,
        )
    }
    item { SettingActionRow("Export Diagnostics", "Device capability report, audio output log, and privacy-redacted settings context", onClick = onExportDiagnostics) }
    item { RestoreDefaultsRow { onSettings(PulseSettingsState(audio = settings.audio, background = settings.background)) } }
}

private data class PulseDeckBuildInfo(
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val platform: String,
)

@Composable
private fun rememberPulseDeckBuildInfo(): PulseDeckBuildInfo {
    val context = LocalContext.current
    return remember(context) {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        PulseDeckBuildInfo(
            versionName = packageInfo?.versionName ?: "0.1.0",
            versionCode = packageInfo?.let(PackageInfoCompat::getLongVersionCode) ?: 1L,
            packageName = context.packageName,
            platform = "Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}",
        )
    }
}

@Composable
private fun AboutPulseDeckHero(schemaVersion: Int) {
    val buildInfo = rememberPulseDeckBuildInfo()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp),
    ) {
        Text("PulseDeck", color = SettingsText, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Created by Abiy Simon", color = SettingsSubtext, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = SettingsBodyWeight, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(28.dp))
        Text("Version/Build", color = SettingsText, fontSize = 23.sp, lineHeight = 27.sp, fontWeight = SettingsTitleWeight)
        Text(
            "[${buildInfo.versionName}-${buildInfo.versionCode}]\nSchema $schemaVersion\n${buildInfo.platform}\n${buildInfo.packageName}",
            color = SettingsSubtext,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = SettingsBodyWeight,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AboutStatusPill(text: String, modifier: Modifier = Modifier) {
    PulseInfoPill(
        text = text,
        modifier = modifier,
        style = PulseInfoPillStyle.Info,
    )
}

@Composable
private fun LicensesOverviewCard(noticeCount: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Text("Open Source Licenses", color = SettingsText, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = SettingsTitleWeight)
        Text("$noticeCount runtime notices", color = SettingsSubtext, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = SettingsBodyWeight, modifier = Modifier.padding(top = 4.dp))
        PulseInfoPill(
            text = "Tap a notice row to read details",
            style = PulseInfoPillStyle.Neutral,
            icon = DeckIcon.Info,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun LicenseNoticeCard(notice: ThirdPartyNotice) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp),
    ) {
        Text(notice.name, color = SettingsText, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = SettingsTitleWeight)
        Text(notice.homepage, color = SettingsSubtext, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = SettingsBodyWeight, modifier = Modifier.padding(top = 5.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PulseInfoPill(
                notice.license,
                style = if (notice.copyleftOrUnknownFlag) PulseInfoPillStyle.Warning else PulseInfoPillStyle.Info,
            )
            PulseInfoPill("v${notice.version}", style = PulseInfoPillStyle.Neutral)
            if (!notice.allowedForRelease) {
                PulseInfoPill("review", style = PulseInfoPillStyle.Blocked)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SettingsSurface.copy(alpha = 0.58f))
                .border(1.dp, SettingsLine, RoundedCornerShape(8.dp))
                .padding(8.dp),
        ) {
            Text(
                notice.readableLicenseNoticeText(),
                color = SettingsText,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = SettingsBodyWeight,
            )
        }
    }
}

private fun ThirdPartyNotice.readableLicenseNoticeText(): String =
    buildString {
        appendLine("Version: $version")
        appendLine("License: $license")
        appendLine("Copyright: $copyright")
        appendLine("Release status: ${if (allowedForRelease) "Allowed / no blocker detected" else "Needs license review"}")
        appendLine()
        appendLine("Packages:")
        artifacts.forEach { artifact -> appendLine("  - $artifact") }
        appendLine()
        appendLine("Usage: $usage")
        if (note.isNotBlank()) {
            appendLine()
            appendLine("Note: $note")
        }
    }.trimEnd()

@Composable
private fun ExportPrivacyCard(settings: PulseSettingsState) {
    val folderCount = settings.normalized().library.musicFolders.size
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        PulseBlue.copy(alpha = 0.16f),
                        SettingsSurface.copy(alpha = 0.70f),
                    ),
                ),
            )
            .border(1.dp, PulseBlue.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Text("Portable Export Policy", color = SettingsText, fontSize = 18.sp, fontWeight = SettingsTitleWeight)
        Text(
            "Exports are versioned JSON profiles designed for beta restore and cross-device transfer. User-facing exports redact local music folders, custom user-agent text, tokens, signed URLs, cache files, downloads, backend/private secrets, and private search history.",
            color = SettingsSubtext,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = SettingsBodyWeight,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutStatusPill("$folderCount folder paths redacted", Modifier.weight(1f))
            AboutStatusPill("Schema ${settings.schemaVersion}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = SettingsMuted,
        fontSize = 12.sp,
        fontWeight = SettingsChromeWeight,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
    @DrawableRes leadingIconRes: Int? = null,
    leadingIconFullColor: Boolean = false,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    SettingRowShell(
        title = title,
        subtitle = disabledReason.takeIf { !enabled } ?: subtitle,
        leading = leading,
        leadingIconRes = leadingIconRes,
        leadingIconFullColor = leadingIconFullColor,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PulseBlue,
                    checkedTrackColor = PulseBlue.copy(alpha = 0.45f),
                ),
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
fun SettingSliderRow(
    title: String,
    subtitle: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    startLabel: String? = null,
    endLabel: String? = null,
    enabled: Boolean = true,
    snapStepDb: Float? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, color = if (enabled) SettingsText else SettingsDisabled, fontSize = 21.sp, fontWeight = SettingsTitleWeight)
        if (subtitle != null) Text(subtitle, color = if (enabled) SettingsSubtext else SettingsDisabled, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = SettingsBodyWeight)
        Text(valueLabel, color = if (enabled) SettingsSubtext else SettingsDisabled, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(startLabel.orEmpty(), color = SettingsSubtext, fontSize = 12.sp, fontWeight = SettingsBodyWeight)
                Text(endLabel.orEmpty(), color = SettingsSubtext, fontSize = 12.sp, fontWeight = SettingsBodyWeight)
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { raw ->
                onValueChange(if (snapStepDb != null) quantizeAudioDb(raw, valueRange) else raw)
            },
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = PulseBlue,
                activeTrackColor = PulseBlue,
                inactiveTrackColor = SettingsLine,
            ),
        )
    }
}

@Composable
fun SettingSegmentedRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, color = if (enabled) SettingsText else SettingsDisabled, fontSize = 20.sp, fontWeight = SettingsTitleWeight)
        if (subtitle != null) Text(subtitle, color = if (enabled) SettingsSubtext else SettingsDisabled, fontSize = 13.sp, fontWeight = SettingsBodyWeight)
        val columns = if (options.size <= 3) options.size.coerceAtLeast(1) else 2
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columns).forEach { rowOptions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        val active = option == selected
                        val visualActive = active && enabled
                        val pillShape = RoundedCornerShape(23.dp)
                        val interactionSource = remember(option) { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(pillShape)
                                .background(
                                    when {
                                        visualActive -> PulseBlue
                                        enabled -> SettingsSurface.copy(alpha = 0.62f)
                                        else -> SettingsSurface.copy(alpha = 0.30f)
                                    },
                                )
                                .border(
                                    1.dp,
                                    when {
                                        visualActive -> PulseBlue.copy(alpha = 0.95f)
                                        enabled -> SettingsLine
                                        else -> SettingsLine.copy(alpha = 0.55f)
                                    },
                                    pillShape,
                                )
                                .settingsPressEffect(interactionSource, enabled)
                                .clickable(
                                    enabled = enabled,
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { onSelected(option) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option,
                                color = if (visualActive) Color(0xFF101115) else if (enabled) SettingsSubtext else SettingsDisabled,
                                fontSize = 13.sp,
                                lineHeight = 14.sp,
                                fontWeight = SettingsChromeWeight,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    repeat(columns - rowOptions.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionPickerRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second ?: selectedValue.ifBlank { "Default" }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, color = if (enabled) SettingsText else SettingsDisabled, fontSize = 20.sp, fontWeight = SettingsTitleWeight)
        Text(
            subtitle ?: selectedLabel,
            color = if (enabled) SettingsSubtext else SettingsDisabled,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = SettingsBodyWeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.chunked(2).forEach { rowOptions ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowOptions.forEach { (value, label) ->
                        val active = value == selectedValue
                        val visualActive = active && enabled
                        val pillShape = RoundedCornerShape(23.dp)
                        val interactionSource = remember(value) { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(pillShape)
                                .background(
                                    when {
                                        visualActive -> PulseBlue
                                        enabled -> SettingsSurface.copy(alpha = 0.62f)
                                        else -> SettingsSurface.copy(alpha = 0.30f)
                                    },
                                )
                                .border(
                                    1.dp,
                                    when {
                                        visualActive -> PulseBlue.copy(alpha = 0.95f)
                                        enabled -> SettingsLine
                                        else -> SettingsLine.copy(alpha = 0.55f)
                                    },
                                    pillShape,
                                )
                                .settingsPressEffect(interactionSource, enabled)
                                .clickable(
                                    enabled = enabled,
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { onSelected(value) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (visualActive) Color(0xFF101115) else if (enabled) SettingsSubtext else SettingsDisabled,
                                fontSize = 13.sp,
                                lineHeight = 14.sp,
                                fontWeight = SettingsChromeWeight,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SettingActionRow(
    title: String,
    subtitle: String? = null,
    leading: String? = null,
    @DrawableRes leadingIconRes: Int? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    leadingIconFullColor: Boolean = false,
    onClick: () -> Unit,
) {
    SettingRowShell(
        title = title,
        subtitle = disabledReason.takeIf { !enabled } ?: subtitle,
        leading = leading,
        leadingIconRes = leadingIconRes,
        leadingIconFullColor = leadingIconFullColor,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SettingNavigationRow(
    title: String,
    subtitle: String? = null,
    leading: String? = null,
    @DrawableRes leadingIconRes: Int? = null,
    leadingIconFullColor: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onClick: () -> Unit,
) {
    SettingRowShell(
        title = title,
        subtitle = disabledReason.takeIf { !enabled } ?: subtitle,
        leading = leading,
        leadingIconRes = leadingIconRes,
        leadingIconFullColor = leadingIconFullColor,
        enabled = enabled,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
fun SettingColorRow(
    title: String,
    colorHex: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingRowShell(
        title = title,
        subtitle = colorHex,
        enabled = enabled,
        onClick = onClick,
        trailing = {
            Box(
                Modifier
                    .size(width = 64.dp, height = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(parseHexColor(colorHex))
                    .border(1.dp, SettingsLine, RoundedCornerShape(18.dp)),
            )
        },
    )
}

@Composable
fun SettingKnobRow(
    title: String,
    subtitle: String? = null,
    value: Float,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    SettingRowShell(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val line = SettingsLine
                val knobColor = if (enabled) SettingsText else SettingsDisabled
                Canvas(Modifier.size(74.dp)) {
                    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(line, style = stroke)
                    val clamped = value.coerceIn(0f, 1f)
                    val angle = Math.toRadians((230 + clamped * 260).toDouble())
                    val radius = size.minDimension * 0.28f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawLine(
                        color = knobColor,
                        start = center,
                        end = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                Text(label, color = if (enabled) SettingsSubtext else SettingsDisabled, fontSize = 12.sp)
            }
        },
    )
}

@Composable
fun SettingDeviceRouteRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    status: String,
    onConfigure: (() -> Unit)? = null,
) {
    SettingRowShell(
        title = title,
        subtitle = "$subtitle\n$status",
        enabled = true,
        onClick = onConfigure,
        trailing = {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (enabled) PulseBlue.copy(alpha = 0.20f) else SettingsSurface)
                    .border(1.dp, if (enabled) PulseBlue.copy(alpha = 0.55f) else SettingsLine, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(if (enabled) "Ready" else "Gated", color = if (enabled) PulseBlue else SettingsSubtext, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
fun SettingInfoBlock(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        PulseBlue.copy(alpha = 0.12f),
                        SettingsSurface.copy(alpha = 0.64f),
                        SettingsSurface.copy(alpha = 0.42f),
                    ),
                ),
            )
            .border(1.dp, PulseBlue.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "INFO",
                color = PulseBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.24f))
                    .border(1.dp, PulseBlue.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(title, color = SettingsText, fontSize = 16.sp, fontWeight = SettingsTitleWeight)
        }
        Text(body, color = SettingsSubtext, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = SettingsBodyWeight, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun RestoreDefaultsRow(onClick: () -> Unit) {
    val context = LocalContext.current
    SettingActionRow(
        title = "Restore Defaults",
        subtitle = "Reset this settings page to PulseDeck defaults",
        onClick = {
            onClick()
            Toast.makeText(context, "Restored to defaults", Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
private fun SettingRowShell(
    title: String,
    subtitle: String? = null,
    leading: String? = null,
    @DrawableRes leadingIconRes: Int? = null,
    leadingIconFullColor: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val fg = when {
        !enabled -> SettingsDisabled
        selected -> SettingsText
        else -> SettingsText
    }
    val sub = when {
        !enabled -> SettingsDisabled
        selected -> SettingsText.copy(alpha = 0.72f)
        else -> SettingsSubtext
    }
    val motion = LocalSettingsMotionSpec.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(motion.duration(SettingsMotion.Duration.Selection), easing = SettingsMotion.Easing.Standard),
        label = "settingsRowSelected",
    )
    val pressedProgress by animateFloatAsState(
        targetValue = if (enabled && onClick != null && pressed) 1f else 0f,
        animationSpec = tween(motion.duration(SettingsMotion.Duration.Tap), easing = SettingsMotion.Easing.Standard),
        label = "settingsRowPressed",
    )
    val rowShape = RoundedCornerShape(8.dp)
    val selectedBackground = Brush.horizontalGradient(
        listOf(
            PulseAccent.copy(alpha = 0.20f * selectedProgress),
            PulseBlue.copy(alpha = 0.12f * selectedProgress),
            PulseAccent.copy(alpha = 0.04f * selectedProgress),
        ),
    )
    val pressedBackground = Brush.horizontalGradient(
        listOf(
            Color.White.copy(alpha = 0.045f * pressedProgress),
            Color.White.copy(alpha = 0.020f * pressedProgress),
        ),
    )
    val transparentBackground = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    Row(
        modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                when {
                    selectedProgress > 0f -> selectedBackground
                    pressedProgress > 0f -> pressedBackground
                    else -> transparentBackground
                },
            )
            .border(
                1.dp,
                if (selectedProgress > 0f) PulseAccent.copy(alpha = 0.34f * selectedProgress) else Color.Transparent,
                rowShape,
            )
            .settingsPressEffect(interactionSource, enabled && onClick != null)
            .clickable(
                enabled = enabled && onClick != null,
                interactionSource = interactionSource,
                indication = null,
            ) { onClick?.invoke() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leadingIconRes != null || leading != null) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            leadingIconFullColor -> Color.Transparent
                            selected -> SettingsBar.copy(alpha = 0.72f)
                            else -> SettingsSurface
                        },
                    )
                    .border(
                        1.dp,
                        when {
                            leadingIconFullColor -> Color.Transparent
                            selected -> PulseAccent.copy(alpha = 0.48f)
                            else -> SettingsLine
                        },
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (leadingIconRes != null) {
                    if (leadingIconFullColor) {
                        Image(
                            painter = painterResource(leadingIconRes),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Icon(
                            painter = painterResource(leadingIconRes),
                            contentDescription = null,
                            tint = if (selected) PulseAccent else sub,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                } else {
                    Text(leading.orEmpty().take(2), color = if (selected) PulseAccent else sub, fontWeight = SettingsChromeWeight)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = SettingsTitleWeight)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = sub,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = SettingsBodyWeight,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

private fun scopeRoute(scope: SettingScope): SettingsRoute? =
    when (scope) {
        SettingScope.Home -> SettingsRoute.Home
        SettingScope.LookAndFeel -> SettingsRoute.LookAndFeel
        SettingScope.Audio, SettingScope.Advanced -> SettingsRoute.Audio
        SettingScope.Visualization -> SettingsRoute.Visualization
        SettingScope.Background -> SettingsRoute.Background
        SettingScope.AlbumArt -> SettingsRoute.AlbumArt
        SettingScope.Library -> SettingsRoute.Library
        SettingScope.HeadsetBluetooth -> SettingsRoute.HeadsetBluetooth
        SettingScope.LockScreen -> SettingsRoute.LockScreen
        SettingScope.Misc, SettingScope.AndroidAuto, SettingScope.Diagnostics -> SettingsRoute.Misc
        SettingScope.About -> SettingsRoute.About
        SettingScope.ImportExport -> SettingsRoute.ImportExport
    }

@DrawableRes
private fun scopeIconRes(scope: SettingScope): Int =
    scopeRoute(scope)?.iconRes ?: R.drawable.iconoir_control_slider

private fun scopeIconFullColor(scope: SettingScope): Boolean =
    scopeRoute(scope)?.fullColorIcon ?: false

private fun MediaButtonSettings.actionAt(index: Int): MediaAction =
    when (index) {
        1 -> button1
        2 -> button2
        3 -> button3
        4 -> button4
        5 -> button5
        6 -> button6
        else -> MediaAction.None
    }

private fun MediaButtonSettings.withAction(index: Int, action: MediaAction): MediaButtonSettings =
    when (index) {
        1 -> copy(button1 = action)
        2 -> copy(button2 = action)
        3 -> copy(button3 = action)
        4 -> copy(button4 = action)
        5 -> copy(button5 = action)
        6 -> copy(button6 = action)
        else -> this
    }

private fun parseHexColor(hex: String): Color =
    runCatching {
        val cleaned = (normalizedSettingsHexColor(hex) ?: "#000000").removePrefix("#")
        val value = cleaned.toLong(16)
        Color((0xFF000000 or value).toInt())
    }.getOrDefault(Color.Black)

private fun normalizedSettingsHexColor(raw: String): String? {
    val candidate = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
    return if (Regex("^#[0-9A-Fa-f]{6}$").matches(candidate)) candidate.uppercase(Locale.US) else null
}
