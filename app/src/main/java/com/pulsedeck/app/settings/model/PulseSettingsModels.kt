package com.pulsedeck.app.settings.model

import com.pulsedeck.app.AudioEngineState
import com.pulsedeck.app.BackgroundSettings
import com.pulsedeck.app.normalized
import kotlin.math.max

enum class SettingScope(val title: String) {
    Home("Settings"),
    LookAndFeel("Appearance"),
    Audio("Audio"),
    Visualization("Visualization"),
    Background("Backdrop"),
    AlbumArt("Album Art"),
    Library("Library"),
    HeadsetBluetooth("Bluetooth Audio"),
    LockScreen("Lock Screen Controls"),
    Misc("Network & Online"),
    About("About"),
    ImportExport("Import/Export"),
    Diagnostics("Diagnostics"),
    AndroidAuto("Android Auto"),
    Advanced("Advanced"),
}

enum class SettingControlType {
    Toggle,
    Slider,
    Segmented,
    Action,
    Navigation,
    ColorPicker,
    Knob,
    Info,
    DeviceRoute,
    TextInput,
}

enum class SettingKey(val id: String) {
    LookTheme("look.theme"),
    LookFollowSystem("look.follow_system"),
    LookFont("look.font"),
    LookFontSize("look.font_size"),
    LookOrientation("look.orientation"),
    LookAnimations("look.animations"),
    LookStartAtLibrary("look.start_at_library"),
    PlayerOpenFirst("player.open_first"),
    PlayerGestureHints("player.gesture_hints"),
    PlayerLandscapeSplit("player.landscape_split"),
    PlayerButtons("player.buttons"),
    PlayerLandscapeButtons("player.landscape_buttons"),
    PlayerButtonRows("player.button_rows"),
    PlayerButtonSize("player.button_size"),
    PlayerButtonScroll("player.button_scroll"),
    PlayerFullContentLyrics("player.full_content.lyrics"),
    PlayerFullContentArtist("player.full_content.artist"),
    PlayerFullContentDiscography("player.full_content.discography"),
    PlayerFullContentAbout("player.full_content.about"),
    PlayerFullContentLocalDiscovery("player.full_content.local_discovery"),
    PlayerFullContentCompact("player.full_content.compact"),
    MiniPlayerStyle("mini_player.style"),
    MiniPlayerTransparency("mini_player.transparency"),
    MiniPlayerAlbumTint("mini_player.album_tint"),
    MiniPlayerBorder("mini_player.border"),
    MiniPlayerHighContrastText("mini_player.high_contrast_text"),
    LookHideStatusBar("look.hide_status_bar"),
    LookKeepScreenOn("look.keep_screen_on"),
    LookShortcutCount("look.shortcut_count"),
    AudioIndex("audio.index"),
    AudioInfo("audio.info"),
    AudioCrossfade("audio.crossfade"),
    AudioReplayGain("audio.replay_gain"),
    AudioFocus("audio.focus"),
    AudioEqualizer("audio.equalizer"),
    AudioResampler("audio.resampler"),
    AudioDvc("audio.dvc"),
    AudioOutput("audio.output"),
    AudioChromecast("audio.chromecast"),
    AudioAdvanced("audio.advanced"),
    VisualizationEnabled("visualization.enabled"),
    VisualizationSpectrum("visualization.spectrum"),
    VisualizationLibrary("visualization.library"),
    VisualizationPresetDuration("visualization.preset_duration"),
    VisualizationTopOpacity("visualization.top_opacity"),
    VisualizationControlsOpacity("visualization.controls_opacity"),
    VisualizationUiTimeout("visualization.ui_timeout"),
    VisualizationAlbumArt("visualization.album_art"),
    VisualizationForce30Fps("visualization.force_30_fps"),
    VisualizationFolderPresets("visualization.folder_presets"),
    BackgroundEnabled("background.enabled"),
    BackgroundLists("background.lists"),
    BackgroundLyrics("background.lyrics"),
    BackgroundGradient("background.gradient"),
    BackgroundGradientColor("background.gradient_color"),
    BackgroundBlur("background.blur"),
    BackgroundDetails("background.details"),
    BackgroundIntensity("background.intensity"),
    BackgroundSaturation("background.saturation"),
    AlbumArtDownload("album_art.download"),
    AlbumArtProvider("album_art.provider"),
    AlbumArtHighestQuality("album_art.highest_quality"),
    AlbumArtAutoReplace("album_art.auto_replace"),
    AlbumArtMinimumUpgrade("album_art.minimum_upgrade"),
    AlbumArtSaveFolderCover("album_art.save_folder_cover"),
    AlbumArtProviderOrder("album_art.provider_order"),
    AlbumArtEmbedded("album_art.embedded"),
    AlbumArtFolder("album_art.folder"),
    AlbumArtQuality("album_art.quality"),
    AlbumArtCache("album_art.cache"),
    LyricsEnabled("lyrics.enabled"),
    LyricsSynced("lyrics.synced"),
    LyricsOnline("lyrics.online"),
    LyricsCache("lyrics.cache"),
    LibraryFolders("library.folders"),
    LibraryRescan("library.rescan"),
    LibraryShortTracks("library.short_tracks"),
    LibrarySort("library.sort"),
    LibraryQueue("library.queue"),
    HeadsetPauseDisconnect("headset.pause_disconnect"),
    HeadsetResumeWired("headset.resume_wired"),
    HeadsetResumeBluetooth("headset.resume_bluetooth"),
    HeadsetRespondButtons("headset.respond_buttons"),
    HeadsetDebounce("headset.debounce"),
    HeadsetStrictCommands("headset.strict_commands"),
    HeadsetBeep("headset.beep"),
    HeadsetBeepMore("headset.beep_more"),
    HeadsetBeepVolume("headset.beep_volume"),
    HeadsetVibrate("headset.vibrate"),
    LockAlbumArt("lock.album_art"),
    LockBlur("lock.blur"),
    LockDefaultImage("lock.default_image"),
    LockCustom("lock.custom"),
    LockShortTimeout("lock.short_timeout"),
    MiscLastFm("misc.lastfm"),
    MiscSimpleScrobbler("misc.simple_scrobbler"),
    MiscAndroidAutoButtons("misc.android_auto_buttons"),
    MiscWakelock("misc.wakelock"),
    MiscStreamTimeout("misc.stream_timeout"),
    MiscStreamBuffer("misc.stream_buffer"),
    MiscOfflineMode("misc.offline_mode"),
    MiscPremiumDeckOfflineMode("misc.premiumdeck_offline_mode"),
    MiscProxyFallback("misc.proxy_fallback"),
    MiscPulseDataSaver("misc.pulse_data_saver"),
    MiscWifiStreamingQuality("misc.wifi_streaming_quality"),
    MiscCellularStreamingQuality("misc.cellular_streaming_quality"),
    MiscRoamingStreamingQuality("misc.roaming_streaming_quality"),
    MiscDataSaverStreamingQuality("misc.data_saver_streaming_quality"),
    MiscStreamPreviewPolicy("misc.stream_preview_policy"),
    MiscCellularMuxedFallback("misc.cellular_muxed_fallback"),
    MiscDataSaverMuxedFallback("misc.data_saver_muxed_fallback"),
    MiscSponsorBlock("misc.sponsor_block"),
    MiscAutomaticSongPicker("misc.automatic_song_picker"),
    MiscExternalRecommendations("misc.external_recommendations"),
    MiscPremiumDeckPersonalization("misc.premiumdeck_personalization"),
    MiscResetPremiumDeckPersonalization("misc.reset_premiumdeck_personalization"),
    AboutPulseDeck("about.pulsedeck"),
    AboutSupport("about.support"),
    AboutLicenses("about.licenses"),
    DiagnosticsCrashReports("diagnostics.crash_reports"),
    DiagnosticsAudioLog("diagnostics.audio_log"),
    ExportSettings("export.settings"),
    ExportPrivateBackup("export.private_backup"),
    ImportSettings("import.settings"),
    ExportDiagnostics("export.diagnostics"),
    RestoreDefaults("restore.defaults"),
}

sealed class SettingValue {
    data class BoolValue(val value: Boolean) : SettingValue()
    data class IntValue(val value: Int) : SettingValue()
    data class FloatValue(val value: Float) : SettingValue()
    data class StringValue(val value: String) : SettingValue()
    data class EnumValue(val value: String) : SettingValue()
}

data class SettingOption(
    val value: String,
    val label: String,
    val description: String? = null,
)

data class SettingRange(
    val min: Float,
    val max: Float,
    val steps: Int = 0,
    val valueLabel: (Float) -> String = { it.toString() },
)

data class SettingDependency(
    val dependsOn: SettingKey,
    val requiredValue: SettingValue,
    val disabledReason: String,
)

data class SettingSpec(
    val key: SettingKey,
    val title: String,
    val subtitle: String? = null,
    val scope: SettingScope,
    val controlType: SettingControlType,
    val defaultValue: SettingValue,
    val options: List<SettingOption> = emptyList(),
    val range: SettingRange? = null,
    val tags: List<String> = emptyList(),
    val dependency: SettingDependency? = null,
    val requiresPermission: String? = null,
    val requiresFeatureFlag: String? = null,
    val restartRequired: Boolean = false,
    val integrationStatus: SettingsIntegrationStatus = SettingsIntegrationStatus.Available,
)

sealed class SettingsIntegrationStatus {
    data object Available : SettingsIntegrationStatus()
    data class Gated(val reason: String) : SettingsIntegrationStatus()
    data class DeviceDependent(val reason: String) : SettingsIntegrationStatus()
    data class NotImplementedYet(val reason: String) : SettingsIntegrationStatus()
}

enum class SettingsTheme(val label: String) {
    Default("Default"),
    Light("Light"),
    Dark("Dark"),
    FollowSystem("Follow Day/Night"),
}

enum class SettingsFont(val label: String) {
    Default("Default"),
    Alternative("Alternative font"),
    Bold("Bold"),
    BoldPlus("Bold+"),
}

enum class ScreenOrientationSetting(val label: String) {
    Default("Default"),
    Portrait("Portrait"),
    Landscape("Landscape"),
}

enum class AnimationSpeedSetting(val label: String) {
    Disabled("Disabled"),
    Fast("Fast"),
    Default("Default"),
}

enum class SpectrumMode(val label: String) {
    Disabled("Disabled"),
    Classic("Classic"),
    Rounded("Rounded"),
}

enum class AlbumArtQuality(val label: String) {
    Efficient("Efficient"),
    High("High"),
    Original("Original"),
}

enum class AlbumArtworkProviderMode(val label: String) {
    CoverArchiveThenYouTube("Cover Archive + YouTube"),
    YouTubeOnly("YouTube thumbnails"),
}

enum class AlbumArtworkProviderId(val label: String) {
    CoverArtArchive("Cover Art Archive"),
    AppleItunes("Apple/iTunes"),
    Deezer("Deezer"),
    MusicHoarders("MH Covers"),
    PageMetadata("Page metadata"),
    YouTube("YouTube thumbnail"),
}

enum class LibrarySortMode(val label: String) {
    Title("Title"),
    Album("Album"),
    Artist("Artist"),
    RecentlyAdded("Recently added"),
}

enum class ButtonPressMode(val label: String) {
    SinglePress("Single press"),
    DoubleTripleNextPrev("Double/triple press"),
    LongPressNext("Long press"),
}

enum class MediaAction(val label: String) {
    None("None"),
    Repeat("Repeat"),
    Shuffle("Shuffle"),
    Close("Close"),
    Like("Like"),
    Unlike("Unlike"),
    Rating("Rating"),
    SeekBack10("-10"),
    SeekForward10("+10"),
    PreviousCategory("Previous"),
    NextCategory("Next"),
}

enum class PlayerButtonAction(val label: String) {
    None("None"),
    PlayPause("Play/Pause"),
    PreviousTrack("Prev. track"),
    NextTrack("Next track"),
    Queue("Queue"),
    Visualization("Visualization"),
    SleepTimer("Sleep timer"),
    Repeat("Repeat"),
    Shuffle("Shuffle"),
    Like("Like"),
    Bookmark("Bookmark"),
    Info("Info"),
    Lyrics("Lyrics"),
    CurrentCategory("Current category"),
    PreviousCategory("Prev. category"),
    NextCategory("Next category"),
    SeekBack10("-10"),
    SeekForward10("+10"),
    TrackMenu("Track menu"),
    Library("Library"),
    AudioSettings("Audio"),
    Search("Search"),
    ClosePlayer("Close player"),
}

enum class PlayerButtonSize(val label: String) {
    Small("Small"),
    Normal("Normal"),
    Large("Large"),
}

enum class MiniPlayerAppearanceStyle(val label: String) {
    AdaptiveGlass("Adaptive Glass"),
    DeepBlackGlass("Deep Black Glass"),
    SolidBlack("Solid Black"),
}

data class MiniPlayerAppearanceSettings(
    val style: MiniPlayerAppearanceStyle = MiniPlayerAppearanceStyle.AdaptiveGlass,
    val transparency: Float = 0.72f,
    val albumTintStrength: Float = 0.85f,
    val borderStrength: Float = 0.35f,
    val useHighContrastText: Boolean = true,
) {
    fun normalized(): MiniPlayerAppearanceSettings =
        copy(
            transparency = transparency.coerceIn(0f, 1f),
            albumTintStrength = albumTintStrength.coerceIn(0f, 1f),
            borderStrength = borderStrength.coerceIn(0f, 1f),
        )
}

enum class LocalArtistDiscoveryPolicy(val label: String) {
    AskEveryTime("Ask every time"),
    WifiOnlyAfterTap("Wi-Fi only after tap"),
    Never("Never"),
}

data class FullPlayerContentSettings(
    val showLyrics: Boolean = true,
    val showMoreFromArtist: Boolean = true,
    val showDiscography: Boolean = true,
    val showAboutArtist: Boolean = false,
    val localArtistDiscoveryPolicy: LocalArtistDiscoveryPolicy = LocalArtistDiscoveryPolicy.AskEveryTime,
    val compactCards: Boolean = true,
) {
    fun normalized(): FullPlayerContentSettings = this
}

private val DefaultPlayerUtilityButtons = listOf(
    PlayerButtonAction.Visualization,
    PlayerButtonAction.SleepTimer,
    PlayerButtonAction.Repeat,
    PlayerButtonAction.Shuffle,
)

private val DefaultLandscapePlayerUtilityButtons = listOf(
    PlayerButtonAction.Visualization,
    PlayerButtonAction.SleepTimer,
    PlayerButtonAction.Repeat,
    PlayerButtonAction.Shuffle,
    PlayerButtonAction.Info,
    PlayerButtonAction.Lyrics,
)

data class PlayerUiSettings(
    val utilityButtons: List<PlayerButtonAction> = DefaultPlayerUtilityButtons,
    val landscapeUtilityButtons: List<PlayerButtonAction> = DefaultLandscapePlayerUtilityButtons,
    val portraitButtonRows: Int = 1,
    val landscapeButtonRows: Int = 1,
    val utilityButtonSize: PlayerButtonSize = PlayerButtonSize.Normal,
    val utilityButtonsScrollable: Boolean = true,
    val showGestureHints: Boolean = true,
    val startAtPlayerWhenRestored: Boolean = true,
    val enableLandscapeSplit: Boolean = true,
    val mirrorUtilityLayout: Boolean = false,
    val miniPlayerAppearance: MiniPlayerAppearanceSettings = MiniPlayerAppearanceSettings(),
    val fullPlayerContent: FullPlayerContentSettings = FullPlayerContentSettings(),
) {
    fun normalized(): PlayerUiSettings {
        val normalizedButtons = normalizePlayerUtilityButtons(utilityButtons)
        val normalizedLandscapeButtons = normalizePlayerUtilityButtons(landscapeUtilityButtons)
        val portraitButtons = normalizedButtons.ifEmpty { DefaultPlayerUtilityButtons }
        val portraitRows = portraitButtonRows.coerceIn(1, 2)
        return copy(
            utilityButtons = portraitButtons,
            landscapeUtilityButtons = if (mirrorUtilityLayout) portraitButtons else normalizedLandscapeButtons.ifEmpty { DefaultLandscapePlayerUtilityButtons },
            portraitButtonRows = portraitRows,
            landscapeButtonRows = if (mirrorUtilityLayout) portraitRows else landscapeButtonRows.coerceIn(1, 2),
            miniPlayerAppearance = miniPlayerAppearance.normalized(),
            fullPlayerContent = fullPlayerContent.normalized(),
        )
    }
}

private fun normalizePlayerUtilityButtons(buttons: List<PlayerButtonAction>): List<PlayerButtonAction> {
    val normalized = mutableListOf<PlayerButtonAction>()
    buttons.forEach { action ->
        if (normalized.size >= 6) return@forEach
        if (action == PlayerButtonAction.Queue) return@forEach
        if (action == PlayerButtonAction.None || action !in normalized) {
            normalized += action
        }
    }
    return normalized
}

data class LookAndFeelSettings(
    val skinId: String = "pulsedeck_default",
    val followSystemTheme: Boolean = true,
    val settingsTheme: SettingsTheme = SettingsTheme.Default,
    val settingsFont: SettingsFont = SettingsFont.Bold,
    val appFontScale: Float = 1.0f,
    val language: String = "auto",
    val appIcon: String = "default",
    val orientation: ScreenOrientationSetting = ScreenOrientationSetting.Default,
    val animationSpeed: AnimationSpeedSetting = AnimationSpeedSetting.Default,
    val startAtLibrary: Boolean = false,
    val hideStatusBar: Boolean = false,
    val keepScreenOn: Boolean = false,
    val settingsShortcutsCount: Int = 5,
) {
    fun normalized(): LookAndFeelSettings =
        copy(
            appFontScale = appFontScale.coerceIn(0.85f, 1.30f),
            settingsShortcutsCount = settingsShortcutsCount.coerceIn(0, 10),
        )
}

data class VisualizationSettings(
    val playerVisualizationEnabled: Boolean = false,
    val equalizerSpectrumMode: SpectrumMode = SpectrumMode.Rounded,
    val libraryVisualizationEnabled: Boolean = false,
    val presetDurationMs: Int = 10_000,
    val topPanelOpacityPercent: Int = 60,
    val fadedControlsOpacityPercent: Int = 50,
    val uiTimeoutMs: Int = 1500,
    val visibleAlbumArt: Boolean = true,
    val ignoreTouch: Boolean = false,
    val trackOpacityPercent: Int = 25,
    val hideSystemBarsFullscreen: Boolean = false,
    val scaledBarsForFadedControls: Boolean = true,
    val hdEnabled: Boolean = false,
    val cropAspect: Boolean = false,
    val force30Fps: Boolean = false,
    val strictPresetMatching: Boolean = false,
    val visualizationDelayMs: Int = 0,
    val hideUnlikedPresets: Boolean = false,
    val spectrumBarsEnabled: Boolean = true,
    val builtInPresetsEnabled: Boolean = true,
    val folderPresetsEnabled: Boolean = true,
) {
    fun normalized(): VisualizationSettings =
        copy(
            presetDurationMs = presetDurationMs.coerceIn(1_000, 60_000),
            topPanelOpacityPercent = topPanelOpacityPercent.coerceIn(0, 100),
            fadedControlsOpacityPercent = fadedControlsOpacityPercent.coerceIn(0, 100),
            uiTimeoutMs = uiTimeoutMs.coerceIn(250, 10_000),
            trackOpacityPercent = trackOpacityPercent.coerceIn(0, 100),
            visualizationDelayMs = visualizationDelayMs.coerceIn(-5_000, 5_000),
        )
}

data class AlbumArtSettings(
    val downloadAlbumArt: Boolean = false,
    val providerMode: AlbumArtworkProviderMode = AlbumArtworkProviderMode.CoverArchiveThenYouTube,
    val highestQualityCovers: Boolean = true,
    val autoReplaceExistingArt: Boolean = false,
    val minimumUpgradePercent: Int = 35,
    val saveFolderCover: Boolean = true,
    val providerOrder: List<AlbumArtworkProviderId> = DefaultAlbumArtworkProviderOrder,
    val preferEmbeddedArtwork: Boolean = true,
    val preferFolderImage: Boolean = true,
    val preferOnlineImage: Boolean = false,
    val quality: AlbumArtQuality = AlbumArtQuality.High,
    val cacheSizeMb: Int = 256,
    val showDefaultImageWhenMissing: Boolean = true,
) {
    fun normalized(): AlbumArtSettings {
        val ordered = providerOrder.distinct().ifEmpty { DefaultAlbumArtworkProviderOrder }
        val missing = DefaultAlbumArtworkProviderOrder.filterNot { it in ordered }
        return copy(
            minimumUpgradePercent = minimumUpgradePercent.coerceIn(0, 300),
            providerOrder = ordered + missing,
            cacheSizeMb = cacheSizeMb.coerceIn(64, 512),
        )
    }
}

val DefaultAlbumArtworkProviderOrder = listOf(
    AlbumArtworkProviderId.CoverArtArchive,
    AlbumArtworkProviderId.AppleItunes,
    AlbumArtworkProviderId.Deezer,
    AlbumArtworkProviderId.MusicHoarders,
    AlbumArtworkProviderId.PageMetadata,
    AlbumArtworkProviderId.YouTube,
)

data class LibrarySettings(
    val musicFolders: List<String> = emptyList(),
    val includeShortTracks: Boolean = true,
    val shortTrackThresholdSeconds: Int = 20,
    val sortMode: LibrarySortMode = LibrarySortMode.Title,
    val folderHierarchyMode: Boolean = true,
    val preserveQueueOnRestart: Boolean = true,
    val rescanOnLaunch: Boolean = false,
) {
    fun normalized(): LibrarySettings =
        copy(shortTrackThresholdSeconds = shortTrackThresholdSeconds.coerceIn(1, 120))
}

data class HeadsetBluetoothSettings(
    val pauseOnHeadsetDisconnect: Boolean = true,
    val resumeOnWiredHeadset: Boolean = false,
    val resumeOnBluetooth: Boolean = false,
    val respondToButtons: Boolean = true,
    val wiredButtonMode: ButtonPressMode = ButtonPressMode.DoubleTripleNextPrev,
    val bluetoothButtonMode: ButtonPressMode = ButtonPressMode.DoubleTripleNextPrev,
    val strictResumePauseStop: Boolean = true,
    val ignoreBluetoothCommandsSeconds: Int = 1,
    val ignoreRepeatShuffle: Boolean = false,
    val beep: Boolean = false,
    val beepMore: Boolean = false,
    val beepVolume: Float = 0.16f,
    val vibrate: Boolean = false,
    val lastProcessedCommands: List<String> = emptyList(),
) {
    fun normalized(): HeadsetBluetoothSettings =
        copy(
            ignoreBluetoothCommandsSeconds = ignoreBluetoothCommandsSeconds.coerceIn(0, 20),
            beepVolume = beepVolume.coerceIn(0f, 0.4f),
            lastProcessedCommands = lastProcessedCommands.takeLast(25),
        )
}

data class LockScreenSettings(
    val albumArtOnLockScreen: Boolean = true,
    val blurAlbumArt: Boolean = false,
    val showDefaultImageWhenMissing: Boolean = false,
    val customLockScreenEnabled: Boolean = false,
    val shorterTimeout: Boolean = false,
    val landscapeLayout: Boolean = false,
    val directUnlock: Boolean = false,
) {
    fun normalized(): LockScreenSettings =
        copy(
            shorterTimeout = shorterTimeout && customLockScreenEnabled,
            landscapeLayout = landscapeLayout && customLockScreenEnabled,
            directUnlock = directUnlock && customLockScreenEnabled,
        )
}

data class AndroidAutoSettings(
    val gridViewForCategories: Boolean = false,
    val imagesForCategories: Boolean = true,
    val albumArtForTracks: Boolean = true,
    val nowPlayingListForConnectedDevices: Boolean = false,
    val customButtons: MediaButtonSettings = MediaButtonSettings(),
)

data class MediaButtonSettings(
    val button1: MediaAction = MediaAction.Shuffle,
    val button2: MediaAction = MediaAction.Close,
    val button3: MediaAction = MediaAction.Repeat,
    val button4: MediaAction = MediaAction.PreviousCategory,
    val button5: MediaAction = MediaAction.NextCategory,
    val button6: MediaAction = MediaAction.None,
)

private fun MediaButtonSettings.migrateLegacySeekDefaults(): MediaButtonSettings =
    if (button4 == MediaAction.SeekBack10 && button5 == MediaAction.SeekForward10) {
        copy(button4 = MediaAction.PreviousCategory, button5 = MediaAction.NextCategory)
    } else {
        this
    }

data class ScrobblingSettings(
    val lastFmEnabled: Boolean = false,
    val simpleScrobblerEnabled: Boolean = false,
    val sessionKeyPresent: Boolean = false,
    val nowPlayingEnabled: Boolean = true,
    val retryQueueEnabled: Boolean = true,
)

enum class PremiumDeckSmartRecommendationMode(val label: String) {
    Off("Off"),
    Lite("Lite"),
    Smart("Smart"),
}

enum class StreamingQualityPolicy(val label: String, val maxAudioBitrateKbps: Int?) {
    High("High", null),
    Balanced("Balanced", 160),
    LowData("Low data", 96),
    DataSaver("Data Saver", 64),
}

enum class StreamPreviewNetworkPolicy(val label: String) {
    AnyNetwork("Any network"),
    WifiAndUnmetered("Wi-Fi/unmetered"),
    Off("Off"),
}

data class OnlineFeatureSettings(
    val offlineMode: Boolean = false,
    val premiumDeckOfflineMode: Boolean = false,
    val useProxyFallback: Boolean = false,
    val pulseDataSaver: Boolean = false,
    val wifiStreamingQuality: StreamingQualityPolicy = StreamingQualityPolicy.High,
    val cellularStreamingQuality: StreamingQualityPolicy = StreamingQualityPolicy.High,
    val roamingStreamingQuality: StreamingQualityPolicy = StreamingQualityPolicy.High,
    val dataSaverStreamingQuality: StreamingQualityPolicy = StreamingQualityPolicy.DataSaver,
    val streamPreviewNetworkPolicy: StreamPreviewNetworkPolicy = StreamPreviewNetworkPolicy.AnyNetwork,
    val allowMuxedStreamFallbackOnCellular: Boolean = true,
    val allowMuxedStreamFallbackInDataSaver: Boolean = false,
    val sponsorBlock: Boolean = true,
    val automaticSongPicker: Boolean = false,
    val externalRecommendations: Boolean = true,
    val premiumDeckPersonalization: Boolean = true,
    val smartRecommendationMode: PremiumDeckSmartRecommendationMode = PremiumDeckSmartRecommendationMode.Lite,
)

data class LyricsSettings(
    val enabled: Boolean = true,
    val preferSynced: Boolean = true,
    val onlineLookup: Boolean = true,
    val cacheSizeMb: Int = 32,
) {
    fun normalized(): LyricsSettings = copy(cacheSizeMb = cacheSizeMb.coerceIn(4, 128))
}

data class MiscSettings(
    val scrobbling: ScrobblingSettings = ScrobblingSettings(),
    val androidAuto: AndroidAutoSettings = AndroidAutoSettings(),
    val online: OnlineFeatureSettings = OnlineFeatureSettings(),
    val metachangedIntent: Boolean = true,
    val useWakelock: Boolean = false,
    val sendAlbumArtForOldApi: Boolean = false,
    val alwaysReloadSkin: Boolean = false,
    val pauseOnScreenOff: Boolean = false,
    val applyHighFramerate: Boolean = false,
    val networkStreamTimeoutSeconds: Int = 30,
    val networkStreamBufferMb: Float = 1.0f,
    val userAgent: String = "",
    val changeTracksByLongVolumeKeys: Boolean = false,
) {
    fun normalized(): MiscSettings =
        copy(
            scrobbling = scrobbling.copy(lastFmEnabled = false, sessionKeyPresent = false),
            networkStreamTimeoutSeconds = networkStreamTimeoutSeconds.coerceIn(5, 300),
            networkStreamBufferMb = networkStreamBufferMb.coerceIn(0.25f, 16f),
        )
}

data class DiagnosticsSettings(
    val sendErrorsToDeveloper: Boolean = false,
    val includeAudioOutputLog: Boolean = false,
    val includeDeviceCapabilities: Boolean = true,
    val redactTrackPaths: Boolean = true,
)

data class PulseSettingsState(
    val lookAndFeel: LookAndFeelSettings = LookAndFeelSettings(),
    val playerUi: PlayerUiSettings = PlayerUiSettings(),
    val audio: AudioEngineState = AudioEngineState(),
    val visualization: VisualizationSettings = VisualizationSettings(),
    val background: BackgroundSettings = BackgroundSettings(),
    val albumArt: AlbumArtSettings = AlbumArtSettings(),
    val lyrics: LyricsSettings = LyricsSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val headsetBluetooth: HeadsetBluetoothSettings = HeadsetBluetoothSettings(),
    val lockScreen: LockScreenSettings = LockScreenSettings(),
    val misc: MiscSettings = MiscSettings(),
    val diagnostics: DiagnosticsSettings = DiagnosticsSettings(),
    val schemaVersion: Int = 5,
) {
    fun normalized(): PulseSettingsState {
        val normalizedMisc = misc.normalized()
        val migratedMisc = if (schemaVersion < 5) {
            normalizedMisc.copy(
                androidAuto = normalizedMisc.androidAuto.copy(
                    customButtons = normalizedMisc.androidAuto.customButtons.migrateLegacySeekDefaults(),
                ),
            )
        } else {
            normalizedMisc
        }
        return copy(
            lookAndFeel = lookAndFeel.normalized(),
            playerUi = playerUi.normalized(),
            audio = audio.normalized(),
            visualization = visualization.normalized(),
            background = background.normalized(),
            albumArt = albumArt.normalized(),
            lyrics = lyrics.normalized(),
            library = library.normalized(),
            headsetBluetooth = headsetBluetooth.normalized(),
            lockScreen = lockScreen.normalized(),
            misc = migratedMisc,
            schemaVersion = max(schemaVersion, 5),
        )
    }
}

fun pulseSettingsCatalog(state: PulseSettingsState = PulseSettingsState()): List<SettingSpec> {
    val normalized = state.normalized()
    return listOf(
        SettingSpec(SettingKey.LookTheme, "Settings Theme", "Default, light, dark, or follow system", SettingScope.LookAndFeel, SettingControlType.Segmented, SettingValue.EnumValue(normalized.lookAndFeel.settingsTheme.name), SettingsTheme.entries.map { SettingOption(it.name, it.label) }, tags = listOf("theme", "skin", "day", "night")),
        SettingSpec(SettingKey.LookFont, "Settings Font", "Default, alternative, bold, or bold+", SettingScope.LookAndFeel, SettingControlType.Segmented, SettingValue.EnumValue(normalized.lookAndFeel.settingsFont.name), SettingsFont.entries.map { SettingOption(it.name, it.label) }, tags = listOf("font", "bold", "type")),
        SettingSpec(SettingKey.LookFontSize, "App Font Size", "Scale text across the app", SettingScope.LookAndFeel, SettingControlType.Slider, SettingValue.FloatValue(normalized.lookAndFeel.appFontScale), range = SettingRange(0.85f, 1.30f, 8) { "${(it * 100f).toInt()}%" }, tags = listOf("font", "size", "scale", "text", "readability")),
        SettingSpec(SettingKey.LookShortcutCount, "Settings Shortcuts in Main Menu", "Main-menu shortcut count rendering is not wired in this beta", SettingScope.LookAndFeel, SettingControlType.Slider, SettingValue.IntValue(normalized.lookAndFeel.settingsShortcutsCount), range = SettingRange(0f, 10f, 9) { it.toInt().toString() }, integrationStatus = SettingsIntegrationStatus.NotImplementedYet("Main-menu shortcut rendering is not wired in this beta"), tags = listOf("shortcuts", "menu")),
        SettingSpec(SettingKey.PlayerOpenFirst, "Open Player First", "Restore Now Playing before Library when playable media exists", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.startAtPlayerWhenRestored), tags = listOf("player", "now playing", "restore", "launch")),
        SettingSpec(SettingKey.PlayerGestureHints, "Player Gesture Hints", "Visible hints for artwork swipes and repeat/shuffle long press", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.showGestureHints), tags = listOf("player", "gesture", "hint", "artwork")),
        SettingSpec(SettingKey.PlayerLandscapeSplit, "Landscape Split Player", "Artwork-left and transport-right player layout on wide screens", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.enableLandscapeSplit), tags = listOf("player", "landscape", "tablet", "split")),
        SettingSpec(SettingKey.PlayerButtons, "Portrait Player Buttons", "Configurable player utility actions for portrait", SettingScope.LookAndFeel, SettingControlType.Navigation, SettingValue.StringValue(normalized.playerUi.utilityButtons.joinToString(",") { it.name }), tags = listOf("player", "buttons", "utility", "repeat", "shuffle")),
        SettingSpec(SettingKey.PlayerLandscapeButtons, "Landscape Player Buttons", "Independent player utility actions for landscape", SettingScope.LookAndFeel, SettingControlType.Navigation, SettingValue.StringValue(normalized.playerUi.landscapeUtilityButtons.joinToString(",") { it.name }), tags = listOf("player", "buttons", "landscape", "utility")),
        SettingSpec(SettingKey.PlayerButtonRows, "Player Button Rows", "One or two player utility rows per orientation", SettingScope.LookAndFeel, SettingControlType.Segmented, SettingValue.IntValue(normalized.playerUi.portraitButtonRows), tags = listOf("player", "buttons", "rows", "layout")),
        SettingSpec(SettingKey.PlayerButtonSize, "Player Button Size", "Small, normal, or large utility buttons", SettingScope.LookAndFeel, SettingControlType.Segmented, SettingValue.EnumValue(normalized.playerUi.utilityButtonSize.name), PlayerButtonSize.entries.map { SettingOption(it.name, it.label) }, tags = listOf("player", "buttons", "size", "density")),
        SettingSpec(SettingKey.PlayerButtonScroll, "Scrollable Player Buttons", "Allow horizontal scrolling when the utility row is dense", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.utilityButtonsScrollable), tags = listOf("player", "buttons", "scroll", "density")),
        SettingSpec(SettingKey.PlayerFullContentLyrics, "Show Lyrics", "Show or hide only the full-player lyrics card", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.fullPlayerContent.showLyrics), tags = listOf("player", "full player", "lyrics", "content")),
        SettingSpec(SettingKey.PlayerFullContentArtist, "Show More from Artist", "Show or hide official artist continuation in the full player", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.fullPlayerContent.showMoreFromArtist), tags = listOf("player", "full player", "artist", "more from artist", "content")),
        SettingSpec(SettingKey.PlayerFullContentDiscography, "Show Discography / Albums", "Show or hide artist releases and discography entry in the full player", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.fullPlayerContent.showDiscography), tags = listOf("player", "full player", "discography", "albums", "artist works")),
        SettingSpec(SettingKey.PlayerFullContentAbout, "Show About Artist", "Show the collapsed artist/about section without automatic online fetches", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.fullPlayerContent.showAboutArtist), tags = listOf("player", "full player", "artist bio", "about artist")),
        SettingSpec(SettingKey.PlayerFullContentLocalDiscovery, "Online Artist Discovery for Local Tracks", "Local files stay offline-first; online artist works are optional", SettingScope.LookAndFeel, SettingControlType.Segmented, SettingValue.EnumValue(normalized.playerUi.fullPlayerContent.localArtistDiscoveryPolicy.name), LocalArtistDiscoveryPolicy.entries.map { SettingOption(it.name, it.label) }, tags = listOf("player", "full player", "local", "artist discovery", "online")),
        SettingSpec(SettingKey.PlayerFullContentCompact, "Compact Full-Player Content Cards", "Use denser bottom content cards to reduce full-player scroll", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.fullPlayerContent.compactCards), tags = listOf("player", "full player", "compact", "content", "cards")),
        SettingSpec(SettingKey.MiniPlayerStyle, "Mini Player Style", "Adaptive album glass, deep black glass, or solid black", SettingScope.LookAndFeel, SettingControlType.Segmented, SettingValue.EnumValue(normalized.playerUi.miniPlayerAppearance.style.name), MiniPlayerAppearanceStyle.entries.map { SettingOption(it.name, it.label) }, tags = listOf("mini player", "glass", "black", "adaptive", "appearance")),
        SettingSpec(SettingKey.MiniPlayerTransparency, "Mini Player Transparency", "Controls mini-player glass opacity", SettingScope.LookAndFeel, SettingControlType.Slider, SettingValue.FloatValue(normalized.playerUi.miniPlayerAppearance.transparency), range = SettingRange(0f, 1f, 20) { "${(it * 100f).toInt()}%" }, tags = listOf("mini player", "opacity", "transparent", "glass")),
        SettingSpec(SettingKey.MiniPlayerAlbumTint, "Mini Player Album Tint", "How strongly album colors influence adaptive glass", SettingScope.LookAndFeel, SettingControlType.Slider, SettingValue.FloatValue(normalized.playerUi.miniPlayerAppearance.albumTintStrength), range = SettingRange(0f, 1f, 20) { "${(it * 100f).toInt()}%" }, tags = listOf("mini player", "album", "color", "tint")),
        SettingSpec(SettingKey.MiniPlayerBorder, "Mini Player Border", "Strength of the glass edge highlight", SettingScope.LookAndFeel, SettingControlType.Slider, SettingValue.FloatValue(normalized.playerUi.miniPlayerAppearance.borderStrength), range = SettingRange(0f, 1f, 20) { "${(it * 100f).toInt()}%" }, tags = listOf("mini player", "border", "edge", "glass")),
        SettingSpec(SettingKey.MiniPlayerHighContrastText, "Mini Player High Contrast Text", "Keep mini-player titles bright over glass styles", SettingScope.LookAndFeel, SettingControlType.Toggle, SettingValue.BoolValue(normalized.playerUi.miniPlayerAppearance.useHighContrastText), tags = listOf("mini player", "text", "contrast", "readability")),
        SettingSpec(SettingKey.AudioInfo, "Audio Info", "Playback pipeline, decoder, DSP, output and buffer diagnostics", SettingScope.Audio, SettingControlType.Navigation, SettingValue.StringValue(""), tags = listOf("pipeline", "codec", "output", "diagnostics")),
        SettingSpec(SettingKey.AudioCrossfade, "Crossfade, Fade, and Gapless", "Fading, gapless playback, seek fade, and track transition behavior", SettingScope.Audio, SettingControlType.Navigation, SettingValue.StringValue(""), tags = listOf("fade", "gapless", "transition")),
        SettingSpec(SettingKey.AudioReplayGain, "Replay Gain", "Album/track loudness leveling and clipping prevention", SettingScope.Audio, SettingControlType.Navigation, SettingValue.StringValue(""), tags = listOf("rg", "loudness", "gain", "preamp")),
        SettingSpec(SettingKey.AudioOutput, "Output", "Media3/AudioTrack, Hi-Res, native, USB DAC, Bluetooth, and Cast routes", SettingScope.Audio, SettingControlType.Navigation, SettingValue.StringValue(""), tags = listOf("speaker", "bluetooth", "usb", "dac", "cast")),
        SettingSpec(SettingKey.VisualizationEnabled, "Visualization On Player Screen", "Visualizer runtime is not enabled in this beta", SettingScope.Visualization, SettingControlType.Toggle, SettingValue.BoolValue(normalized.visualization.playerVisualizationEnabled), integrationStatus = SettingsIntegrationStatus.NotImplementedYet("Visualizer runtime is not enabled in this beta"), tags = listOf("spectrum", "visualizer", "bars")),
        SettingSpec(SettingKey.VisualizationSpectrum, "Equalizer Screen Spectrum", "Visualizer runtime is not enabled in this beta", SettingScope.Visualization, SettingControlType.Segmented, SettingValue.EnumValue(normalized.visualization.equalizerSpectrumMode.name), SpectrumMode.entries.map { SettingOption(it.name, it.label) }, integrationStatus = SettingsIntegrationStatus.NotImplementedYet("Visualizer runtime is not enabled in this beta"), tags = listOf("eq", "spectrum", "rounded")),
        SettingSpec(SettingKey.BackgroundEnabled, "Enable Blurred Backgrounds", "Album-art reactive background rendering", SettingScope.Background, SettingControlType.Toggle, SettingValue.BoolValue(normalized.background.blurred), tags = listOf("blur", "album", "art", "glow")),
        SettingSpec(SettingKey.BackgroundSaturation, "Background Saturation", "Color saturation for the album-art background", SettingScope.Background, SettingControlType.Slider, SettingValue.FloatValue(normalized.background.saturation), range = SettingRange(0f, 2f, 19) { "${(it * 100).toInt()}%" }, tags = listOf("color", "album art")),
        SettingSpec(SettingKey.AlbumArtDownload, "Download Album Art", "Allow online artwork discovery when local art is missing", SettingScope.AlbumArt, SettingControlType.Toggle, SettingValue.BoolValue(normalized.albumArt.downloadAlbumArt), tags = listOf("cover", "cache", "artwork")),
        SettingSpec(SettingKey.AlbumArtProvider, "Artwork Provider", "Cover Art Archive with YouTube thumbnail fallback", SettingScope.AlbumArt, SettingControlType.Segmented, SettingValue.EnumValue(normalized.albumArt.providerMode.name), AlbumArtworkProviderMode.entries.map { SettingOption(it.name, it.label) }, tags = listOf("cover", "artwork", "youtube", "musicbrainz")),
        SettingSpec(SettingKey.AlbumArtHighestQuality, "Highest Quality Covers", "Search multiple sources for the best album front cover", SettingScope.AlbumArt, SettingControlType.Toggle, SettingValue.BoolValue(normalized.albumArt.highestQualityCovers), tags = listOf("cover", "high resolution", "artwork", "quality")),
        SettingSpec(SettingKey.AlbumArtAutoReplace, "Auto Replace Existing Art", "Replace existing art only when a clearly higher-resolution match is found", SettingScope.AlbumArt, SettingControlType.Toggle, SettingValue.BoolValue(normalized.albumArt.autoReplaceExistingArt), tags = listOf("cover", "replace", "upgrade")),
        SettingSpec(SettingKey.AlbumArtMinimumUpgrade, "Minimum Cover Upgrade", "Required improvement before replacing existing album art", SettingScope.AlbumArt, SettingControlType.Slider, SettingValue.IntValue(normalized.albumArt.minimumUpgradePercent), range = SettingRange(0f, 300f, 29) { "+${it.toInt()}%" }, tags = listOf("cover", "resolution", "threshold")),
        SettingSpec(SettingKey.AlbumArtSaveFolderCover, "Save Folder Cover", "Write cover.jpg into the album folder when Android allows it", SettingScope.AlbumArt, SettingControlType.Toggle, SettingValue.BoolValue(normalized.albumArt.saveFolderCover), tags = listOf("cover", "folder", "jpg")),
        SettingSpec(SettingKey.AlbumArtProviderOrder, "Artwork Provider Order", "Cover Art Archive, Apple/iTunes, Deezer, MH Covers, metadata, YouTube", SettingScope.AlbumArt, SettingControlType.Info, SettingValue.StringValue(normalized.albumArt.providerOrder.joinToString(",") { it.name }), tags = listOf("cover", "provider", "order", "musichoarders")),
        SettingSpec(SettingKey.LyricsEnabled, "Lyrics", "Fetch and display cached plain or synced lyrics", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.lyrics.enabled), tags = listOf("lyrics", "lrc", "synced")),
        SettingSpec(SettingKey.LyricsOnline, "Online Lyrics Lookup", "Use LRCLIB first, then plain fallback providers", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.lyrics.onlineLookup), tags = listOf("lyrics", "online", "lrclib")),
        SettingSpec(SettingKey.MiscOfflineMode, "Offline Mode", "Disable online artwork, lyrics, stream search, and discovery fetches", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.misc.online.offlineMode), tags = listOf("offline", "network", "stream")),
        SettingSpec(SettingKey.MiscPremiumDeckOfflineMode, "Offline Deck", "PremiumDeck only: browse and play saved music without PremiumDeck media or discovery network use", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.misc.online.premiumDeckOfflineMode), tags = listOf("offline deck", "premiumdeck", "downloads", "network")),
        SettingSpec(SettingKey.MiscPulseDataSaver, "PulseDeck Data Saver", "Use the Data Saver stream policy for PremiumDeck playback", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.misc.online.pulseDataSaver), tags = listOf("data saver", "cellular", "stream", "youtube", "premiumdeck")),
        SettingSpec(SettingKey.MiscWifiStreamingQuality, "Wi-Fi Stream Quality", "Preferred PremiumDeck stream quality on Wi-Fi and unmetered networks", SettingScope.Misc, SettingControlType.Segmented, SettingValue.EnumValue(normalized.misc.online.wifiStreamingQuality.name), StreamingQualityPolicy.entries.map { SettingOption(it.name, it.label) }, tags = listOf("wifi", "stream", "quality", "youtube", "premiumdeck")),
        SettingSpec(SettingKey.MiscCellularStreamingQuality, "Cellular Stream Quality", "Preferred PremiumDeck stream quality on cellular or metered networks", SettingScope.Misc, SettingControlType.Segmented, SettingValue.EnumValue(normalized.misc.online.cellularStreamingQuality.name), StreamingQualityPolicy.entries.map { SettingOption(it.name, it.label) }, tags = listOf("cellular", "mobile data", "stream", "quality", "youtube")),
        SettingSpec(SettingKey.MiscRoamingStreamingQuality, "Roaming Stream Quality", "Preferred PremiumDeck stream quality while cellular roaming", SettingScope.Misc, SettingControlType.Segmented, SettingValue.EnumValue(normalized.misc.online.roamingStreamingQuality.name), StreamingQualityPolicy.entries.map { SettingOption(it.name, it.label) }, tags = listOf("roaming", "cellular", "stream", "quality", "youtube")),
        SettingSpec(SettingKey.MiscDataSaverStreamingQuality, "Data Saver Stream Quality", "Preferred PremiumDeck quality when PulseDeck or Android Data Saver is active", SettingScope.Misc, SettingControlType.Segmented, SettingValue.EnumValue(normalized.misc.online.dataSaverStreamingQuality.name), StreamingQualityPolicy.entries.map { SettingOption(it.name, it.label) }, tags = listOf("data saver", "stream", "quality", "youtube", "premiumdeck")),
        SettingSpec(SettingKey.MiscStreamPreviewPolicy, "Stream Preview Resolve", "Control when PremiumDeck pre-resolves preview and upcoming stream URLs", SettingScope.Misc, SettingControlType.Segmented, SettingValue.EnumValue(normalized.misc.online.streamPreviewNetworkPolicy.name), StreamPreviewNetworkPolicy.entries.map { SettingOption(it.name, it.label) }, tags = listOf("preview", "prefetch", "stream", "cellular", "data saver")),
        SettingSpec(SettingKey.MiscCellularMuxedFallback, "Cellular Video Fallback", "Allow muxed video+audio fallback on cellular when no audio-only URL is available", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.misc.online.allowMuxedStreamFallbackOnCellular), tags = listOf("cellular", "muxed", "fallback", "stream", "youtube")),
        SettingSpec(SettingKey.MiscDataSaverMuxedFallback, "Data Saver Video Fallback", "Allow muxed video+audio fallback while Data Saver policy is active", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.misc.online.allowMuxedStreamFallbackInDataSaver), tags = listOf("data saver", "muxed", "fallback", "stream", "youtube")),
        SettingSpec(SettingKey.MiscSponsorBlock, "SponsorBlock", "Skip known sponsored segments for supported streams", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.misc.online.sponsorBlock), tags = listOf("sponsorblock", "skip", "stream")),
        SettingSpec(SettingKey.MiscExternalRecommendations, "External Recommendations", "Use YouTube-derived discovery alongside PulseDeck local signals", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.misc.online.externalRecommendations), tags = listOf("recommendations", "youtube", "mix")),
        SettingSpec(SettingKey.MiscPremiumDeckPersonalization, "Smart Recommendations", "Off, Lite heuristics, or Smart lazy model reranking", SettingScope.Misc, SettingControlType.Segmented, SettingValue.EnumValue(normalized.misc.online.smartRecommendationMode.name), PremiumDeckSmartRecommendationMode.entries.map { SettingOption(it.name, it.label) }, tags = listOf("premiumdeck", "personalization", "recommendations", "mix", "model")),
        SettingSpec(SettingKey.MiscResetPremiumDeckPersonalization, "Reset PremiumDeck Personalization", "Clear on-device recommendation profile and replay buffer", SettingScope.Misc, SettingControlType.Action, SettingValue.StringValue(""), tags = listOf("premiumdeck", "personalization", "reset", "privacy")),
        SettingSpec(SettingKey.LibraryFolders, "Music Folders", "Advanced library scan rules are not active in this beta", SettingScope.Library, SettingControlType.Navigation, SettingValue.IntValue(normalized.library.musicFolders.size), integrationStatus = SettingsIntegrationStatus.NotImplementedYet("Advanced library scan rules are not active in this beta"), tags = listOf("scan", "mediastore", "folders")),
        SettingSpec(SettingKey.HeadsetPauseDisconnect, "Pause On Headset Disconnect", "Pause when wired, Bluetooth, or USB DAC output disconnects", SettingScope.HeadsetBluetooth, SettingControlType.Toggle, SettingValue.BoolValue(normalized.headsetBluetooth.pauseOnHeadsetDisconnect), tags = listOf("bluetooth", "headset", "disconnect")),
        SettingSpec(SettingKey.HeadsetDebounce, "Ignore Bluetooth Commands", "Ignore duplicate commands after route connection", SettingScope.HeadsetBluetooth, SettingControlType.Slider, SettingValue.IntValue(normalized.headsetBluetooth.ignoreBluetoothCommandsSeconds), range = SettingRange(0f, 20f, 19) { "${it.toInt()} sec" }, tags = listOf("button", "debounce", "car")),
        SettingSpec(SettingKey.HeadsetBeep, "Command Beeps", "Short headset and Bluetooth command confirmation sounds", SettingScope.HeadsetBluetooth, SettingControlType.Toggle, SettingValue.BoolValue(normalized.headsetBluetooth.beep), tags = listOf("beep", "sound", "cue", "feedback", "earcon")),
        SettingSpec(SettingKey.HeadsetBeepMore, "Beep More", "Also beep for notification, Android Auto, watch, and controller commands", SettingScope.HeadsetBluetooth, SettingControlType.Toggle, SettingValue.BoolValue(normalized.headsetBluetooth.beepMore), dependency = SettingDependency(SettingKey.HeadsetBeep, SettingValue.BoolValue(true), "Command Beeps are off"), tags = listOf("beep", "beep more", "notification", "android auto", "watch", "car", "cue", "feedback")),
        SettingSpec(SettingKey.HeadsetBeepVolume, "Command Beep Volume", "Caps command cues so they stay below music playback", SettingScope.HeadsetBluetooth, SettingControlType.Slider, SettingValue.FloatValue(normalized.headsetBluetooth.beepVolume), range = SettingRange(0f, 0.4f, 19) { "${(it * 100).toInt()}%" }, dependency = SettingDependency(SettingKey.HeadsetBeep, SettingValue.BoolValue(true), "Command Beeps are off"), tags = listOf("beep", "volume", "sound", "cue", "feedback")),
        SettingSpec(SettingKey.HeadsetVibrate, "Vibrate", "Optional haptic confirmation for command feedback", SettingScope.HeadsetBluetooth, SettingControlType.Toggle, SettingValue.BoolValue(normalized.headsetBluetooth.vibrate), tags = listOf("vibrate", "haptic", "feedback", "headset", "bluetooth")),
        SettingSpec(SettingKey.LockAlbumArt, "Album Art", "Custom lock screen styling is not available in this beta. Android media controls still work from the lock screen.", SettingScope.LockScreen, SettingControlType.Toggle, SettingValue.BoolValue(normalized.lockScreen.albumArtOnLockScreen), integrationStatus = SettingsIntegrationStatus.NotImplementedYet("Custom lock screen styling is not available in this beta. Android media controls still work from the lock screen."), tags = listOf("lockscreen", "notification", "metadata")),
        SettingSpec(SettingKey.LockShortTimeout, "Shorter Timeout", "Custom lock screen styling is not available in this beta. Android media controls still work from the lock screen.", SettingScope.LockScreen, SettingControlType.Toggle, SettingValue.BoolValue(normalized.lockScreen.shorterTimeout), dependency = SettingDependency(SettingKey.LockCustom, SettingValue.BoolValue(true), "Custom lock screen styling is not available in this beta. Android media controls still work from the lock screen."), integrationStatus = SettingsIntegrationStatus.NotImplementedYet("Custom lock screen styling is not available in this beta. Android media controls still work from the lock screen."), tags = listOf("timeout", "permission")),
        SettingSpec(SettingKey.MiscLastFm, "Scrobble via Last.fm", "Authenticated now-playing and scrobble submission", SettingScope.Misc, SettingControlType.Toggle, SettingValue.BoolValue(normalized.misc.scrobbling.lastFmEnabled), integrationStatus = SettingsIntegrationStatus.Gated("Requires Last.fm authentication"), tags = listOf("lastfm", "scrobble")),
        SettingSpec(SettingKey.MiscAndroidAutoButtons, "Buttons", "Configure notification and Android Auto custom actions", SettingScope.AndroidAuto, SettingControlType.Navigation, SettingValue.StringValue(""), tags = listOf("car", "notification", "media buttons")),
        SettingSpec(SettingKey.DiagnosticsCrashReports, "Send Errors To Developer", "Opt-in crash log sharing with privacy redaction", SettingScope.Diagnostics, SettingControlType.Toggle, SettingValue.BoolValue(normalized.diagnostics.sendErrorsToDeveloper), tags = listOf("crash", "privacy", "logs")),
        SettingSpec(SettingKey.AboutPulseDeck, "About PulseDeck", "Version, build, creator credit, and beta identity", SettingScope.About, SettingControlType.Info, SettingValue.StringValue(""), tags = listOf("about", "version", "build", "creator", "abiy simon")),
        SettingSpec(SettingKey.AboutSupport, "Support This Project", "Support notes and privacy-safe diagnostics guidance", SettingScope.About, SettingControlType.Info, SettingValue.StringValue(""), tags = listOf("support", "help", "feedback", "diagnostics")),
        SettingSpec(SettingKey.AboutLicenses, "Licenses & Open Source", "Runtime and service third-party attribution inventory", SettingScope.About, SettingControlType.Navigation, SettingValue.IntValue(PulseDeckThirdPartyLicenses.releaseAuditInventory.size), tags = listOf("licenses", "legal", "open source", "third party")),
        SettingSpec(SettingKey.ExportSettings, "Export Portable Settings", "Portable, privacy-redacted settings for another device or user", SettingScope.ImportExport, SettingControlType.Action, SettingValue.StringValue(""), tags = listOf("backup", "json", "portable", "privacy", "settings")),
        SettingSpec(SettingKey.ExportPrivateBackup, "Export Safe Private Backup", "Private beta backup with a redaction manifest and no local paths or tokens", SettingScope.ImportExport, SettingControlType.Action, SettingValue.StringValue(""), tags = listOf("backup", "json", "private", "redacted", "safe")),
        SettingSpec(SettingKey.ImportSettings, "Import Settings/Backup", "Validate, sanitize, migrate, then apply supported settings", SettingScope.ImportExport, SettingControlType.Action, SettingValue.StringValue(""), tags = listOf("restore", "migration", "json", "portable", "backup")),
        SettingSpec(SettingKey.ExportDiagnostics, "Export Diagnostics", "Privacy-redacted capability and audio-output report", SettingScope.ImportExport, SettingControlType.Action, SettingValue.StringValue(""), tags = listOf("diagnostics", "support", "privacy", "audio output")),
    )
}

fun settingsSearchMatches(spec: SettingSpec, query: String): Boolean {
    val terms = normalizeSettingsText(query).split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return true
    val haystack = normalizeSettingsText(
        buildString {
            append(spec.title).append(' ')
            append(spec.subtitle.orEmpty()).append(' ')
            append(spec.scope.title).append(' ')
            append(spec.key.id).append(' ')
            append(spec.tags.joinToString(" "))
        },
    )
    return terms.all { term -> haystack.contains(term) || settingsSynonyms[term].orEmpty().any(haystack::contains) }
}

fun settingDisabledReason(spec: SettingSpec, values: Map<SettingKey, SettingValue>): String? {
    val dependency = spec.dependency ?: return null
    return if (values[dependency.dependsOn] == dependency.requiredValue) null else dependency.disabledReason
}

fun shouldSubmitLastFmScrobble(durationMs: Long, playedMs: Long): Boolean {
    if (durationMs < 30_000L || playedMs < 30_000L) return false
    val halfDuration = durationMs / 2L
    return playedMs >= minOf(halfDuration, 240_000L)
}

private val settingsSynonyms = mapOf(
    "eq" to listOf("equalizer"),
    "rg" to listOf("replay gain", "replaygain"),
    "cast" to listOf("chromecast"),
    "bt" to listOf("bluetooth"),
    "cover" to listOf("album art", "artwork"),
    "car" to listOf("android auto", "aaos"),
    "misc" to listOf("network online"),
    "online" to listOf("network", "streaming", "data saver"),
    "artist" to listOf("more from artist", "discography"),
)

private fun normalizeSettingsText(text: String): String =
    text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
