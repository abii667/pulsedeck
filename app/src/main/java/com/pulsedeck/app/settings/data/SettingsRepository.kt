package com.pulsedeck.app.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pulsedeck.app.AdvancedAudioTweaksSettings
import com.pulsedeck.app.AudioEngineState
import com.pulsedeck.app.AudioFocusSettings
import com.pulsedeck.app.BackgroundSettings
import com.pulsedeck.app.BufferMode
import com.pulsedeck.app.BluetoothFocusBehavior
import com.pulsedeck.app.CompressorState
import com.pulsedeck.app.CrossfadeSettings
import com.pulsedeck.app.DelayState
import com.pulsedeck.app.DeviceProfile
import com.pulsedeck.app.DirectVolumeControlSettings
import com.pulsedeck.app.DuckRequestAction
import com.pulsedeck.app.DvcVolumeSteps
import com.pulsedeck.app.FadeCurve
import com.pulsedeck.app.GateState
import com.pulsedeck.app.GraphicEqBand
import com.pulsedeck.app.LimiterState
import com.pulsedeck.app.ModulationState
import com.pulsedeck.app.NativeAudioSettings
import com.pulsedeck.app.NativeCompareSlot
import com.pulsedeck.app.NativePresetSlot
import com.pulsedeck.app.OutputBitDepth
import com.pulsedeck.app.OutputFallbackMode
import com.pulsedeck.app.OutputMode
import com.pulsedeck.app.OutputSampleRate
import com.pulsedeck.app.PARAMETRIC_EQ_SLOT_COUNT
import com.pulsedeck.app.ParametricEqBand
import com.pulsedeck.app.ParametricFilterType
import com.pulsedeck.app.PermanentFocusLossAction
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
import com.pulsedeck.app.loadAudioEngineState
import com.pulsedeck.app.normalized
import com.pulsedeck.app.saveAudioEngineState
import com.pulsedeck.app.ReadabilityProtectionSettings
import com.pulsedeck.app.settings.model.AlbumArtQuality
import com.pulsedeck.app.settings.model.AlbumArtSettings
import com.pulsedeck.app.settings.model.AlbumArtworkProviderId
import com.pulsedeck.app.settings.model.AnimationSpeedSetting
import com.pulsedeck.app.settings.model.DiagnosticsSettings
import com.pulsedeck.app.settings.model.FullPlayerContentSettings
import com.pulsedeck.app.settings.model.HeadsetBluetoothSettings
import com.pulsedeck.app.settings.model.ButtonPressMode
import com.pulsedeck.app.settings.model.LibrarySettings
import com.pulsedeck.app.settings.model.LyricsSettings
import com.pulsedeck.app.settings.model.LockScreenSettings
import com.pulsedeck.app.settings.model.LocalArtistDiscoveryPolicy
import com.pulsedeck.app.settings.model.LookAndFeelSettings
import com.pulsedeck.app.settings.model.MiscSettings
import com.pulsedeck.app.settings.model.MiniPlayerAppearanceSettings
import com.pulsedeck.app.settings.model.OnlineFeatureSettings
import com.pulsedeck.app.settings.model.PremiumDeckSmartRecommendationMode
import com.pulsedeck.app.settings.model.PulseSettingsState
import com.pulsedeck.app.settings.model.PlayerButtonAction
import com.pulsedeck.app.settings.model.PlayerUiSettings
import com.pulsedeck.app.settings.model.ScreenOrientationSetting
import com.pulsedeck.app.settings.model.SettingsFont
import com.pulsedeck.app.settings.model.SettingsTheme
import com.pulsedeck.app.settings.model.SpectrumMode
import com.pulsedeck.app.settings.model.StreamPreviewNetworkPolicy
import com.pulsedeck.app.settings.model.StreamingQualityPolicy
import com.pulsedeck.app.settings.model.VisualizationSettings
import com.pulsedeck.app.settings.model.LibrarySortMode
import com.pulsedeck.app.settings.model.ScrobblingSettings
import com.pulsedeck.app.settings.model.AndroidAutoSettings
import com.pulsedeck.app.settings.model.MediaButtonSettings
import com.pulsedeck.app.settings.model.MediaAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface SettingsRepository {
    val settings: Flow<PulseSettingsState>

    suspend fun save(state: PulseSettingsState)
    suspend fun migrateLegacyIfNeeded(audio: AudioEngineState, background: BackgroundSettings)
    fun exportSettings(state: PulseSettingsState): String
    fun exportPrivateBackup(state: PulseSettingsState): String
    fun importSettings(rawJson: String, base: PulseSettingsState = PulseSettingsState()): PulseSettingsState
}

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val appContext = context.applicationContext
    private val dataStore = sharedDataStore(appContext)

    override val settings: Flow<PulseSettingsState> =
        dataStore.data.map { prefs ->
            val raw = prefs[PROFILE_JSON]
            val base = PulseSettingsState()
            if (raw.isNullOrBlank()) base else SettingsProfileCodec.decode(raw, base)
        }

    override suspend fun save(state: PulseSettingsState) {
        val normalized = state.normalized()
        withContext(Dispatchers.IO) { saveAudioEngineState(appContext, normalized.audio) }
        dataStore.edit { prefs ->
            prefs[PROFILE_JSON] = SettingsProfileCodec.encode(normalized).toString()
            prefs[SCHEMA_VERSION] = normalized.schemaVersion.toString()
            prefs[LEGACY_MIGRATED] = true
        }
    }

    override suspend fun migrateLegacyIfNeeded(audio: AudioEngineState, background: BackgroundSettings) {
        dataStore.edit { prefs ->
            if (prefs[LEGACY_MIGRATED] != true && prefs[PROFILE_JSON].isNullOrBlank()) {
                prefs[PROFILE_JSON] = SettingsProfileCodec.encode(
                    PulseSettingsState(audio = audio, background = background).normalized(),
                ).toString()
                prefs[SCHEMA_VERSION] = "1"
            }
            prefs[LEGACY_MIGRATED] = true
        }
    }

    override fun exportSettings(state: PulseSettingsState): String =
        SettingsProfileCodec.encodePortableExport(state.normalized()).toString(2)

    override fun exportPrivateBackup(state: PulseSettingsState): String =
        SettingsProfileCodec.encodePrivateBackupExport(state.normalized()).toString(2)

    override fun importSettings(rawJson: String, base: PulseSettingsState): PulseSettingsState =
        SettingsProfileCodec.decodeUserImport(rawJson, base).normalized()

    private companion object {
        val PROFILE_JSON = stringPreferencesKey("profile_json")
        val SCHEMA_VERSION = stringPreferencesKey("schema_version")
        val LEGACY_MIGRATED = booleanPreferencesKey("legacy_shared_preferences_migrated")
        @Volatile
        private var dataStoreInstance: DataStore<Preferences>? = null

        fun sharedDataStore(context: Context): DataStore<Preferences> {
            val appContext = context.applicationContext
            return dataStoreInstance ?: synchronized(this) {
                dataStoreInstance ?: PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    produceFile = { appContext.preferencesDataStoreFile("pulse_power_settings.preferences_pb") },
                ).also { dataStoreInstance = it }
            }
        }
    }
}

object SettingsProfileCodec {
    fun encode(state: PulseSettingsState): JSONObject {
        val normalized = state.normalized()
        return JSONObject()
            .put("schemaVersion", normalized.schemaVersion)
            .put("app", "PulseDeck")
            .put("lookAndFeel", JSONObject()
                .put("skinId", normalized.lookAndFeel.skinId)
                .put("followSystemTheme", normalized.lookAndFeel.followSystemTheme)
                .put("settingsTheme", normalized.lookAndFeel.settingsTheme.name)
                .put("settingsFont", normalized.lookAndFeel.settingsFont.name)
                .put("appFontScale", normalized.lookAndFeel.appFontScale)
                .put("language", normalized.lookAndFeel.language)
                .put("appIcon", normalized.lookAndFeel.appIcon)
                .put("orientation", normalized.lookAndFeel.orientation.name)
                .put("animationSpeed", normalized.lookAndFeel.animationSpeed.name)
                .put("startAtLibrary", normalized.lookAndFeel.startAtLibrary)
                .put("hideStatusBar", normalized.lookAndFeel.hideStatusBar)
                .put("keepScreenOn", normalized.lookAndFeel.keepScreenOn)
                .put("settingsShortcutsCount", normalized.lookAndFeel.settingsShortcutsCount))
            .put("playerUi", JSONObject()
                .put("utilityButtons", JSONArray(normalized.playerUi.utilityButtons.map { it.name }))
                .put("landscapeUtilityButtons", JSONArray(normalized.playerUi.landscapeUtilityButtons.map { it.name }))
                .put("portraitButtonRows", normalized.playerUi.portraitButtonRows)
                .put("landscapeButtonRows", normalized.playerUi.landscapeButtonRows)
                .put("utilityButtonSize", normalized.playerUi.utilityButtonSize.name)
                .put("utilityButtonsScrollable", normalized.playerUi.utilityButtonsScrollable)
                .put("showGestureHints", normalized.playerUi.showGestureHints)
                .put("startAtPlayerWhenRestored", normalized.playerUi.startAtPlayerWhenRestored)
                .put("enableLandscapeSplit", normalized.playerUi.enableLandscapeSplit)
                .put("mirrorUtilityLayout", normalized.playerUi.mirrorUtilityLayout)
                .put("miniPlayerAppearance", JSONObject()
                    .put("style", normalized.playerUi.miniPlayerAppearance.style.name)
                    .put("transparency", normalized.playerUi.miniPlayerAppearance.transparency)
                    .put("albumTintStrength", normalized.playerUi.miniPlayerAppearance.albumTintStrength)
                    .put("borderStrength", normalized.playerUi.miniPlayerAppearance.borderStrength)
                    .put("useHighContrastText", normalized.playerUi.miniPlayerAppearance.useHighContrastText))
                .put("fullPlayerContent", JSONObject()
                    .put("showLyrics", normalized.playerUi.fullPlayerContent.showLyrics)
                    .put("showMoreFromArtist", normalized.playerUi.fullPlayerContent.showMoreFromArtist)
                    .put("showDiscography", normalized.playerUi.fullPlayerContent.showDiscography)
                    .put("showAboutArtist", normalized.playerUi.fullPlayerContent.showAboutArtist)
                    .put("localArtistDiscoveryPolicy", normalized.playerUi.fullPlayerContent.localArtistDiscoveryPolicy.name)
                    .put("compactCards", normalized.playerUi.fullPlayerContent.compactCards)))
            .put("audio", encodeAudio(normalized.audio))
            .put("background", JSONObject()
                .put("blurred", normalized.background.blurred)
                .put("listBackground", normalized.background.listBackground)
                .put("lyricsBackground", normalized.background.lyricsBackground)
                .put("gradient", normalized.background.gradient)
                .put("gradientColor", normalized.background.gradientColor)
                .put("gradientForLists", normalized.background.gradientForLists)
                .put("blur", normalized.background.blur)
                .put("details", normalized.background.details)
                .put("intensity", normalized.background.intensity)
                .put("saturation", normalized.background.saturation)
                .put("readabilityProtection", JSONObject()
                    .put("enabled", normalized.background.readabilityProtection.enabled)
                    .put("autoDimLists", normalized.background.readabilityProtection.autoDimLists)
                    .put("autoDimLyrics", normalized.background.readabilityProtection.autoDimLyrics)
                    .put("minimumTextContrast", normalized.background.readabilityProtection.minimumTextContrast)))
            .put("visualization", JSONObject()
                .put("playerVisualizationEnabled", normalized.visualization.playerVisualizationEnabled)
                .put("equalizerSpectrumMode", normalized.visualization.equalizerSpectrumMode.name)
                .put("libraryVisualizationEnabled", normalized.visualization.libraryVisualizationEnabled)
                .put("presetDurationMs", normalized.visualization.presetDurationMs)
                .put("topPanelOpacityPercent", normalized.visualization.topPanelOpacityPercent)
                .put("fadedControlsOpacityPercent", normalized.visualization.fadedControlsOpacityPercent)
                .put("uiTimeoutMs", normalized.visualization.uiTimeoutMs)
                .put("visibleAlbumArt", normalized.visualization.visibleAlbumArt)
                .put("ignoreTouch", normalized.visualization.ignoreTouch)
                .put("trackOpacityPercent", normalized.visualization.trackOpacityPercent)
                .put("hideSystemBarsFullscreen", normalized.visualization.hideSystemBarsFullscreen)
                .put("scaledBarsForFadedControls", normalized.visualization.scaledBarsForFadedControls)
                .put("hdEnabled", normalized.visualization.hdEnabled)
                .put("cropAspect", normalized.visualization.cropAspect)
                .put("force30Fps", normalized.visualization.force30Fps)
                .put("strictPresetMatching", normalized.visualization.strictPresetMatching)
                .put("visualizationDelayMs", normalized.visualization.visualizationDelayMs)
                .put("hideUnlikedPresets", normalized.visualization.hideUnlikedPresets)
                .put("spectrumBarsEnabled", normalized.visualization.spectrumBarsEnabled)
                .put("builtInPresetsEnabled", normalized.visualization.builtInPresetsEnabled)
                .put("folderPresetsEnabled", normalized.visualization.folderPresetsEnabled))
            .put("albumArt", JSONObject()
                .put("downloadAlbumArt", normalized.albumArt.downloadAlbumArt)
                .put("providerMode", normalized.albumArt.providerMode.name)
                .put("highestQualityCovers", normalized.albumArt.highestQualityCovers)
                .put("autoReplaceExistingArt", normalized.albumArt.autoReplaceExistingArt)
                .put("minimumUpgradePercent", normalized.albumArt.minimumUpgradePercent)
                .put("saveFolderCover", normalized.albumArt.saveFolderCover)
                .put("providerOrder", JSONArray(normalized.albumArt.providerOrder.map { it.name }))
                .put("preferEmbeddedArtwork", normalized.albumArt.preferEmbeddedArtwork)
                .put("preferFolderImage", normalized.albumArt.preferFolderImage)
                .put("preferOnlineImage", normalized.albumArt.preferOnlineImage)
                .put("quality", normalized.albumArt.quality.name)
                .put("cacheSizeMb", normalized.albumArt.cacheSizeMb)
                .put("showDefaultImageWhenMissing", normalized.albumArt.showDefaultImageWhenMissing))
            .put("lyrics", JSONObject()
                .put("enabled", normalized.lyrics.enabled)
                .put("preferSynced", normalized.lyrics.preferSynced)
                .put("onlineLookup", normalized.lyrics.onlineLookup)
                .put("cacheSizeMb", normalized.lyrics.cacheSizeMb))
            .put("library", JSONObject()
                .put("musicFolders", JSONArray(normalized.library.musicFolders))
                .put("includeShortTracks", normalized.library.includeShortTracks)
                .put("shortTrackThresholdSeconds", normalized.library.shortTrackThresholdSeconds)
                .put("sortMode", normalized.library.sortMode.name)
                .put("folderHierarchyMode", normalized.library.folderHierarchyMode)
                .put("preserveQueueOnRestart", normalized.library.preserveQueueOnRestart)
                .put("rescanOnLaunch", normalized.library.rescanOnLaunch))
            .put("headsetBluetooth", JSONObject()
                .put("pauseOnHeadsetDisconnect", normalized.headsetBluetooth.pauseOnHeadsetDisconnect)
                .put("resumeOnWiredHeadset", normalized.headsetBluetooth.resumeOnWiredHeadset)
                .put("resumeOnBluetooth", normalized.headsetBluetooth.resumeOnBluetooth)
                .put("respondToButtons", normalized.headsetBluetooth.respondToButtons)
                .put("wiredButtonMode", normalized.headsetBluetooth.wiredButtonMode.name)
                .put("bluetoothButtonMode", normalized.headsetBluetooth.bluetoothButtonMode.name)
                .put("strictResumePauseStop", normalized.headsetBluetooth.strictResumePauseStop)
                .put("ignoreBluetoothCommandsSeconds", normalized.headsetBluetooth.ignoreBluetoothCommandsSeconds)
                .put("ignoreRepeatShuffle", normalized.headsetBluetooth.ignoreRepeatShuffle)
                .put("beep", normalized.headsetBluetooth.beep)
                .put("beepMore", normalized.headsetBluetooth.beepMore)
                .put("beepVolume", normalized.headsetBluetooth.beepVolume)
                .put("vibrate", normalized.headsetBluetooth.vibrate))
            .put("lockScreen", JSONObject()
                .put("albumArtOnLockScreen", normalized.lockScreen.albumArtOnLockScreen)
                .put("blurAlbumArt", normalized.lockScreen.blurAlbumArt)
                .put("showDefaultImageWhenMissing", normalized.lockScreen.showDefaultImageWhenMissing)
                .put("customLockScreenEnabled", normalized.lockScreen.customLockScreenEnabled)
                .put("shorterTimeout", normalized.lockScreen.shorterTimeout)
                .put("landscapeLayout", normalized.lockScreen.landscapeLayout)
                .put("directUnlock", normalized.lockScreen.directUnlock))
            .put("misc", encodeMisc(normalized.misc))
            .put("diagnostics", JSONObject()
                .put("sendErrorsToDeveloper", normalized.diagnostics.sendErrorsToDeveloper)
                .put("includeAudioOutputLog", normalized.diagnostics.includeAudioOutputLog)
                .put("includeDeviceCapabilities", normalized.diagnostics.includeDeviceCapabilities)
                .put("redactTrackPaths", normalized.diagnostics.redactTrackPaths))
    }

    fun encodePortableExport(state: PulseSettingsState): JSONObject =
        encode(state.sanitizedForUserTransfer()).apply {
            put("profileType", "portable_settings")
            put("exportedAt", System.currentTimeMillis())
            put("privacy", userTransferPrivacyReport(state, "Portable settings"))
        }

    fun encodePrivateBackupExport(state: PulseSettingsState): JSONObject =
        encode(state.sanitizedForUserTransfer()).apply {
            put("profileType", "private_backup")
            put("exportedAt", System.currentTimeMillis())
            put("privacy", userTransferPrivacyReport(state, "Safe private backup"))
            put("backup", JSONObject()
                .put("safePrivateBackup", true)
                .put("restoresAcrossUsersAndDevices", true)
                .put("redactedLocalMusicFolderCount", state.normalized().library.musicFolders.size)
                .put("excludedByPolicy", JSONArray(userTransferExcludedFields())))
        }

    fun decode(rawJson: String, base: PulseSettingsState = PulseSettingsState()): PulseSettingsState {
        val root = JSONObject(rawJson)
        require(root.optInt("schemaVersion", 0) in 1..5) { "Unsupported PulseDeck settings schema." }
        return base.copy(
            lookAndFeel = decodeLook(root.optJSONObject("lookAndFeel"), base.lookAndFeel),
            playerUi = decodePlayerUi(root.optJSONObject("playerUi"), base.playerUi),
            audio = decodeAudio(root.optJSONObject("audio"), base.audio),
            background = decodeBackground(root.optJSONObject("background"), base.background),
            visualization = decodeVisualization(root.optJSONObject("visualization"), base.visualization),
            albumArt = decodeAlbumArt(root.optJSONObject("albumArt"), base.albumArt),
            lyrics = decodeLyrics(root.optJSONObject("lyrics"), base.lyrics),
            library = decodeLibrary(root.optJSONObject("library"), base.library),
            headsetBluetooth = decodeHeadset(root.optJSONObject("headsetBluetooth"), base.headsetBluetooth),
            lockScreen = decodeLock(root.optJSONObject("lockScreen"), base.lockScreen),
            misc = decodeMisc(root.optJSONObject("misc"), base.misc),
            diagnostics = decodeDiagnostics(root.optJSONObject("diagnostics"), base.diagnostics),
            schemaVersion = root.optInt("schemaVersion", base.schemaVersion),
        ).normalized()
    }

    fun decodeUserImport(rawJson: String, base: PulseSettingsState = PulseSettingsState()): PulseSettingsState =
        decode(rawJson, base).sanitizedForUserTransfer()

    private fun PulseSettingsState.sanitizedForUserTransfer(): PulseSettingsState {
        val normalized = normalized()
        return normalized.copy(
            library = normalized.library.copy(musicFolders = emptyList()),
            misc = normalized.misc.copy(
                scrobbling = normalized.misc.scrobbling.copy(
                    lastFmEnabled = false,
                    sessionKeyPresent = false,
                ),
                userAgent = "",
            ),
            diagnostics = normalized.diagnostics.copy(
                sendErrorsToDeveloper = false,
                redactTrackPaths = true,
            ),
        ).normalized()
    }

    private fun userTransferPrivacyReport(state: PulseSettingsState, title: String): JSONObject {
        val normalized = state.normalized()
        return JSONObject()
            .put("title", title)
            .put("portableAcrossUsersAndDevices", true)
            .put("redactedLocalMusicFolderCount", normalized.library.musicFolders.size)
            .put("redactsTrackPaths", true)
            .put("includesCacheFiles", false)
            .put("includesDownloads", false)
            .put("includesPrivateSearchHistory", false)
            .put("includesTokens", false)
            .put("includesSignedUrls", false)
            .put("excludedFields", JSONArray(userTransferExcludedFields()))
    }

    private fun userTransferExcludedFields(): List<String> = listOf(
        "library.musicFolders",
        "misc.userAgent",
        "misc.scrobbling.sessionKeyPresent",
        "cache files",
        "downloads",
        "private search history",
        "tokens",
        "signed URLs",
        "backend/private secrets",
    )

    private fun encodeAudio(audio: AudioEngineState): JSONObject =
        JSONObject()
            .put("enabled", audio.enabled)
            .put("bypass", audio.bypass)
            .put("preampDb", audio.preampDb)
            .put("replayGainMode", audio.replayGainMode.name)
            .put("replayGainPreampDb", audio.replayGainPreampDb)
            .put("preventClipping", audio.preventClipping)
            .put("limiter", JSONObject()
                .put("enabled", audio.limiter.enabled)
                .put("ceilingDb", audio.limiter.ceilingDb)
                .put("releaseMs", audio.limiter.releaseMs)
                .put("strength", audio.limiter.strength))
            .put("eqEnabled", audio.eqEnabled)
            .put("graphicEqRangeMode", audio.graphicEqRangeMode.name)
            .put("eqBands", JSONArray(audio.eqBands.map { band ->
                JSONObject()
                    .put("frequencyHz", band.frequencyHz)
                    .put("gainDb", band.gainDb)
            }))
            .put("parametricEq", JSONArray(audio.parametricEq.map { band ->
                JSONObject()
                    .put("enabled", band.enabled)
                    .put("type", band.type.name)
                    .put("frequencyHz", band.frequencyHz)
                    .put("gainDb", band.gainDb)
                    .put("q", band.q)
            }))
            .put("bassDb", audio.bassDb)
            .put("trebleDb", audio.trebleDb)
            .put("vocalClarityDb", audio.vocalClarityDb)
            .put("loudnessDb", audio.loudnessDb)
            .put("stereo", JSONObject()
                .put("balance", audio.stereo.balance)
                .put("mono", audio.stereo.mono)
                .put("stereoWidth", audio.stereo.stereoWidth)
                .put("crossfeed", audio.stereo.crossfeed))
            .put("reverb", JSONObject()
                .put("enabled", audio.reverb.enabled)
                .put("mix", audio.reverb.mix)
                .put("size", audio.reverb.size)
                .put("preDelayMs", audio.reverb.preDelayMs)
                .put("damp", audio.reverb.damp)
                .put("decay", audio.reverb.decay))
            .put("tempoPitch", JSONObject()
                .put("speed", audio.tempoPitch.speed)
                .put("tempo", audio.tempoPitch.tempo)
                .put("pitchSemitones", audio.tempoPitch.pitchSemitones))
            .put("compressor", JSONObject()
                .put("enabled", audio.compressor.enabled)
                .put("thresholdDb", audio.compressor.thresholdDb)
                .put("ratio", audio.compressor.ratio)
                .put("attackMs", audio.compressor.attackMs)
                .put("releaseMs", audio.compressor.releaseMs)
                .put("makeupDb", audio.compressor.makeupDb)
                .put("mix", audio.compressor.mix))
            .put("gate", JSONObject()
                .put("enabled", audio.gate.enabled)
                .put("thresholdDb", audio.gate.thresholdDb)
                .put("attackMs", audio.gate.attackMs)
                .put("holdMs", audio.gate.holdMs)
                .put("releaseMs", audio.gate.releaseMs))
            .put("delay", JSONObject()
                .put("enabled", audio.delay.enabled)
                .put("timeMs", audio.delay.timeMs)
                .put("feedback", audio.delay.feedback)
                .put("mix", audio.delay.mix))
            .put("modulation", JSONObject()
                .put("chorusEnabled", audio.modulation.chorusEnabled)
                .put("flangerEnabled", audio.modulation.flangerEnabled)
                .put("phaserEnabled", audio.modulation.phaserEnabled)
                .put("rateHz", audio.modulation.rateHz)
                .put("depth", audio.modulation.depth)
                .put("feedback", audio.modulation.feedback)
                .put("mix", audio.modulation.mix))
            .put("native", JSONObject()
                .put("enabled", audio.native.enabled)
                .put("media3DspEnabled", audio.native.media3DspEnabled)
                .put("lowLatencyToneEnabled", audio.native.lowLatencyToneEnabled)
                .put("masterGain", audio.native.masterGain)
                .put("sourceFrequencyHz", audio.native.sourceFrequencyHz)
                .put("sourceGain", audio.native.sourceGain)
                .put("diagnosticsEnabled", audio.native.diagnosticsEnabled)
                .put("exportDurationMs", audio.native.exportDurationMs)
                .put("activeCompareSlot", audio.native.activeCompareSlot.name)
                .put("compareSlotA", encodeNativeSlot(audio.native.compareSlotA))
                .put("compareSlotB", encodeNativeSlot(audio.native.compareSlotB)))
            .put("activePresetId", audio.activePresetId)
            .put("presetModified", audio.presetModified)
            .put("audioInfo", JSONObject()
                .put("showOnMainScreenLongPress", audio.audioInfo.showOnMainScreenLongPress)
                .put("showOutputPath", audio.audioInfo.showOutputPath)
                .put("showDspChain", audio.audioInfo.showDspChain))
            .put("crossfade", JSONObject()
                .put("gaplessEnabled", audio.crossfade.gaplessEnabled)
                .put("crossfadeEnabled", audio.crossfade.crossfadeEnabled)
                .put("durationMs", audio.crossfade.durationMs)
                .put("fadeCurve", audio.crossfade.fadeCurve.name)
                .put("fadeOnPlay", audio.crossfade.fadeOnPlay)
                .put("fadeOnPause", audio.crossfade.fadeOnPause)
                .put("fadeOnManualSkip", audio.crossfade.fadeOnManualSkip)
                .put("disableForAlbums", audio.crossfade.disableForAlbums)
                .put("disableForShortTracks", audio.crossfade.disableForShortTracks))
            .put("replayGain", JSONObject()
                .put("enabled", audio.replayGain.enabled)
                .put("mode", audio.replayGain.mode.name)
                .put("source", audio.replayGain.source.name)
                .put("trackPreampDb", audio.replayGain.trackPreampDb)
                .put("albumPreampDb", audio.replayGain.albumPreampDb)
                .put("noRgPreampDb", audio.replayGain.noRgPreampDb)
                .put("preventClipping", audio.replayGain.preventClipping)
                .put("showInAudioInfo", audio.replayGain.showInAudioInfo))
            .put("audioFocus", JSONObject()
                .put("requestOnPlay", audio.audioFocus.requestOnPlay)
                .put("onPermanentLoss", audio.audioFocus.onPermanentLoss.name)
                .put("onTransientLoss", audio.audioFocus.onTransientLoss.name)
                .put("onDuck", audio.audioFocus.onDuck.name)
                .put("resumeAfterCall", audio.audioFocus.resumeAfterCall)
                .put("resumeAfterNotification", audio.audioFocus.resumeAfterNotification)
                .put("duckVolume", audio.audioFocus.duckVolume)
                .put("duckFadeMs", audio.audioFocus.duckFadeMs)
                .put("bluetoothBehavior", audio.audioFocus.bluetoothBehavior.name)
                .put("resumeOnlyIfAutoPaused", audio.audioFocus.resumeOnlyIfAutoPaused))
            .put("resampler", JSONObject()
                .put("mode", audio.resampler.mode.name)
                .put("outputSampleRate", audio.resampler.outputSampleRate.name)
                .put("dither", audio.resampler.dither)
                .put("quality", audio.resampler.quality.name)
                .put("showConversionInfo", audio.resampler.showConversionInfo)
                .put("batterySaverMode", audio.resampler.batterySaverMode))
            .put("dvc", JSONObject()
                .put("enabled", audio.dvc.enabled)
                .put("bluetoothEnabled", audio.dvc.bluetoothEnabled)
                .put("headroomDb", audio.dvc.headroomDb)
                .put("volumeSteps", audio.dvc.volumeSteps.name)
                .put("perDeviceDvc", audio.dvc.perDeviceDvc)
                .put("compatibilityMode", audio.dvc.compatibilityMode))
            .put("output", JSONObject()
                .put("mode", audio.output.mode.name)
                .put("deviceProfile", audio.output.deviceProfile.name)
                .put("bufferMode", audio.output.bufferMode.name)
                .put("hiResEnabled", audio.output.hiResEnabled)
                .put("sampleRate", audio.output.sampleRate.name)
                .put("bitDepth", audio.output.bitDepth.name)
                .put("bitPerfectAttemptEnabled", audio.output.bitPerfectAttemptEnabled)
                .put("fallbackMode", audio.output.fallbackMode.name)
                .put("showOutputWarnings", audio.output.showOutputWarnings))
            .put("advancedTweaks", JSONObject()
                .put("volumeSteps", audio.advancedTweaks.volumeSteps.name)
                .put("musicFxEnabled", audio.advancedTweaks.musicFxEnabled)
                .put("safeMode", audio.advancedTweaks.safeMode)
                .put("debugAudioInfo", audio.advancedTweaks.debugAudioInfo))

    private fun decodeAudio(json: JSONObject?, fallback: AudioEngineState): AudioEngineState {
        if (json == null) return fallback
        val limiter = json.optJSONObject("limiter")?.let {
            LimiterState(
                enabled = it.optBoolean("enabled", fallback.limiter.enabled),
                ceilingDb = it.optDouble("ceilingDb", fallback.limiter.ceilingDb.toDouble()).toFloat(),
                releaseMs = it.optDouble("releaseMs", fallback.limiter.releaseMs.toDouble()).toFloat(),
                strength = it.optDouble("strength", fallback.limiter.strength.toDouble()).toFloat(),
            )
        } ?: fallback.limiter
        val stereo = json.optJSONObject("stereo")?.let {
            StereoState(
                balance = it.optDouble("balance", fallback.stereo.balance.toDouble()).toFloat(),
                mono = it.optBoolean("mono", fallback.stereo.mono),
                stereoWidth = it.optDouble("stereoWidth", fallback.stereo.stereoWidth.toDouble()).toFloat(),
                crossfeed = it.optDouble("crossfeed", fallback.stereo.crossfeed.toDouble()).toFloat(),
            )
        } ?: fallback.stereo
        val reverb = json.optJSONObject("reverb")?.let {
            ReverbState(
                enabled = it.optBoolean("enabled", fallback.reverb.enabled),
                mix = it.optDouble("mix", fallback.reverb.mix.toDouble()).toFloat(),
                size = it.optDouble("size", fallback.reverb.size.toDouble()).toFloat(),
                preDelayMs = it.optDouble("preDelayMs", fallback.reverb.preDelayMs.toDouble()).toFloat(),
                damp = it.optDouble("damp", fallback.reverb.damp.toDouble()).toFloat(),
                decay = it.optDouble("decay", fallback.reverb.decay.toDouble()).toFloat(),
            )
        } ?: fallback.reverb
        val tempoPitch = json.optJSONObject("tempoPitch")?.let {
            TempoPitchState(
                speed = it.optDouble("speed", fallback.tempoPitch.speed.toDouble()).toFloat(),
                tempo = it.optDouble("tempo", fallback.tempoPitch.tempo.toDouble()).toFloat(),
                pitchSemitones = it.optDouble("pitchSemitones", fallback.tempoPitch.pitchSemitones.toDouble()).toFloat(),
            )
        } ?: fallback.tempoPitch
        val compressor = json.optJSONObject("compressor")?.let {
            CompressorState(
                enabled = it.optBoolean("enabled", fallback.compressor.enabled),
                thresholdDb = it.optDouble("thresholdDb", fallback.compressor.thresholdDb.toDouble()).toFloat(),
                ratio = it.optDouble("ratio", fallback.compressor.ratio.toDouble()).toFloat(),
                attackMs = it.optDouble("attackMs", fallback.compressor.attackMs.toDouble()).toFloat(),
                releaseMs = it.optDouble("releaseMs", fallback.compressor.releaseMs.toDouble()).toFloat(),
                makeupDb = it.optDouble("makeupDb", fallback.compressor.makeupDb.toDouble()).toFloat(),
                mix = it.optDouble("mix", fallback.compressor.mix.toDouble()).toFloat(),
            )
        } ?: fallback.compressor
        val gate = json.optJSONObject("gate")?.let {
            GateState(
                enabled = it.optBoolean("enabled", fallback.gate.enabled),
                thresholdDb = it.optDouble("thresholdDb", fallback.gate.thresholdDb.toDouble()).toFloat(),
                attackMs = it.optDouble("attackMs", fallback.gate.attackMs.toDouble()).toFloat(),
                holdMs = it.optDouble("holdMs", fallback.gate.holdMs.toDouble()).toFloat(),
                releaseMs = it.optDouble("releaseMs", fallback.gate.releaseMs.toDouble()).toFloat(),
            )
        } ?: fallback.gate
        val delay = json.optJSONObject("delay")?.let {
            DelayState(
                enabled = it.optBoolean("enabled", fallback.delay.enabled),
                timeMs = it.optDouble("timeMs", fallback.delay.timeMs.toDouble()).toFloat(),
                feedback = it.optDouble("feedback", fallback.delay.feedback.toDouble()).toFloat(),
                mix = it.optDouble("mix", fallback.delay.mix.toDouble()).toFloat(),
            )
        } ?: fallback.delay
        val modulation = json.optJSONObject("modulation")?.let {
            ModulationState(
                chorusEnabled = it.optBoolean("chorusEnabled", fallback.modulation.chorusEnabled),
                flangerEnabled = it.optBoolean("flangerEnabled", fallback.modulation.flangerEnabled),
                phaserEnabled = it.optBoolean("phaserEnabled", fallback.modulation.phaserEnabled),
                rateHz = it.optDouble("rateHz", fallback.modulation.rateHz.toDouble()).toFloat(),
                depth = it.optDouble("depth", fallback.modulation.depth.toDouble()).toFloat(),
                feedback = it.optDouble("feedback", fallback.modulation.feedback.toDouble()).toFloat(),
                mix = it.optDouble("mix", fallback.modulation.mix.toDouble()).toFloat(),
            )
        } ?: fallback.modulation
        val native = json.optJSONObject("native")?.let {
            NativeAudioSettings(
                enabled = it.optBoolean("enabled", fallback.native.enabled),
                media3DspEnabled = it.optBoolean("media3DspEnabled", fallback.native.media3DspEnabled),
                lowLatencyToneEnabled = it.optBoolean("lowLatencyToneEnabled", fallback.native.lowLatencyToneEnabled),
                masterGain = it.optDouble("masterGain", fallback.native.masterGain.toDouble()).toFloat(),
                sourceFrequencyHz = it.optDouble("sourceFrequencyHz", fallback.native.sourceFrequencyHz.toDouble()).toFloat(),
                sourceGain = it.optDouble("sourceGain", fallback.native.sourceGain.toDouble()).toFloat(),
                diagnosticsEnabled = it.optBoolean("diagnosticsEnabled", fallback.native.diagnosticsEnabled),
                exportDurationMs = it.optInt("exportDurationMs", fallback.native.exportDurationMs),
                activeCompareSlot = enumValue(it.optString("activeCompareSlot"), fallback.native.activeCompareSlot),
                compareSlotA = decodeNativeSlot(it.optJSONObject("compareSlotA"), fallback.native.compareSlotA),
                compareSlotB = decodeNativeSlot(it.optJSONObject("compareSlotB"), fallback.native.compareSlotB),
            )
        } ?: fallback.native
        val eqBands = json.optJSONArray("eqBands")?.let { array ->
            List(array.length()) { index ->
                val item = array.optJSONObject(index)
                val base = fallback.eqBands.getOrNull(index)
                GraphicEqBand(
                    frequencyHz = item?.optDouble("frequencyHz", base?.frequencyHz?.toDouble() ?: 0.0)?.toFloat() ?: (base?.frequencyHz ?: 0f),
                    gainDb = item?.optDouble("gainDb", base?.gainDb?.toDouble() ?: 0.0)?.toFloat() ?: (base?.gainDb ?: 0f),
                )
            }.filter { it.frequencyHz > 0f }
        } ?: fallback.eqBands
        val parametricEq = json.optJSONArray("parametricEq")?.let { array ->
            List(minOf(array.length(), PARAMETRIC_EQ_SLOT_COUNT)) { index ->
                val item = array.optJSONObject(index)
                val base = fallback.parametricEq.getOrNull(index) ?: ParametricEqBand()
                ParametricEqBand(
                    enabled = item?.optBoolean("enabled", base.enabled) ?: base.enabled,
                    type = enumValue(item?.optString("type"), base.type),
                    frequencyHz = item?.optDouble("frequencyHz", base.frequencyHz.toDouble())?.toFloat() ?: base.frequencyHz,
                    gainDb = item?.optDouble("gainDb", base.gainDb.toDouble())?.toFloat() ?: base.gainDb,
                    q = item?.optDouble("q", base.q.toDouble())?.toFloat() ?: base.q,
                )
            }
        } ?: fallback.parametricEq
        val audioInfo = json.optJSONObject("audioInfo")?.let {
            fallback.audioInfo.copy(
                showOnMainScreenLongPress = it.optBoolean("showOnMainScreenLongPress", fallback.audioInfo.showOnMainScreenLongPress),
                showOutputPath = it.optBoolean("showOutputPath", fallback.audioInfo.showOutputPath),
                showDspChain = it.optBoolean("showDspChain", fallback.audioInfo.showDspChain),
            )
        } ?: fallback.audioInfo
        val crossfade = json.optJSONObject("crossfade")?.let {
            CrossfadeSettings(
                gaplessEnabled = it.optBoolean("gaplessEnabled", fallback.crossfade.gaplessEnabled),
                crossfadeEnabled = it.optBoolean("crossfadeEnabled", fallback.crossfade.crossfadeEnabled),
                durationMs = it.optInt("durationMs", fallback.crossfade.durationMs),
                fadeCurve = enumValue(it.optString("fadeCurve"), fallback.crossfade.fadeCurve),
                fadeOnPlay = it.optBoolean("fadeOnPlay", fallback.crossfade.fadeOnPlay),
                fadeOnPause = it.optBoolean("fadeOnPause", fallback.crossfade.fadeOnPause),
                fadeOnManualSkip = it.optBoolean("fadeOnManualSkip", fallback.crossfade.fadeOnManualSkip),
                disableForAlbums = it.optBoolean("disableForAlbums", fallback.crossfade.disableForAlbums),
                disableForShortTracks = it.optBoolean("disableForShortTracks", fallback.crossfade.disableForShortTracks),
            )
        } ?: fallback.crossfade
        val replayGain = json.optJSONObject("replayGain")?.let {
            ReplayGainSettings(
                enabled = it.optBoolean("enabled", fallback.replayGain.enabled),
                mode = enumValue(it.optString("mode"), fallback.replayGain.mode),
                source = enumValue(it.optString("source"), fallback.replayGain.source),
                trackPreampDb = it.optDouble("trackPreampDb", fallback.replayGain.trackPreampDb.toDouble()).toFloat(),
                albumPreampDb = it.optDouble("albumPreampDb", fallback.replayGain.albumPreampDb.toDouble()).toFloat(),
                noRgPreampDb = it.optDouble("noRgPreampDb", fallback.replayGain.noRgPreampDb.toDouble()).toFloat(),
                preventClipping = it.optBoolean("preventClipping", fallback.replayGain.preventClipping),
                showInAudioInfo = it.optBoolean("showInAudioInfo", fallback.replayGain.showInAudioInfo),
            )
        } ?: fallback.replayGain
        val audioFocus = json.optJSONObject("audioFocus")?.let {
            AudioFocusSettings(
                requestOnPlay = it.optBoolean("requestOnPlay", fallback.audioFocus.requestOnPlay),
                onPermanentLoss = enumValue(it.optString("onPermanentLoss"), fallback.audioFocus.onPermanentLoss),
                onTransientLoss = enumValue(it.optString("onTransientLoss"), fallback.audioFocus.onTransientLoss),
                onDuck = enumValue(it.optString("onDuck"), fallback.audioFocus.onDuck),
                resumeAfterCall = it.optBoolean("resumeAfterCall", fallback.audioFocus.resumeAfterCall),
                resumeAfterNotification = it.optBoolean("resumeAfterNotification", fallback.audioFocus.resumeAfterNotification),
                duckVolume = it.optDouble("duckVolume", fallback.audioFocus.duckVolume.toDouble()).toFloat(),
                duckFadeMs = it.optInt("duckFadeMs", fallback.audioFocus.duckFadeMs),
                bluetoothBehavior = enumValue(it.optString("bluetoothBehavior"), fallback.audioFocus.bluetoothBehavior),
                resumeOnlyIfAutoPaused = it.optBoolean("resumeOnlyIfAutoPaused", fallback.audioFocus.resumeOnlyIfAutoPaused),
            )
        } ?: fallback.audioFocus
        val resampler = json.optJSONObject("resampler")?.let {
            ResamplerSettings(
                mode = enumValue(it.optString("mode"), fallback.resampler.mode),
                outputSampleRate = enumValue(it.optString("outputSampleRate"), fallback.resampler.outputSampleRate),
                dither = it.optBoolean("dither", fallback.resampler.dither),
                quality = enumValue(it.optString("quality"), fallback.resampler.quality),
                showConversionInfo = it.optBoolean("showConversionInfo", fallback.resampler.showConversionInfo),
                batterySaverMode = it.optBoolean("batterySaverMode", fallback.resampler.batterySaverMode),
            )
        } ?: fallback.resampler
        val dvc = json.optJSONObject("dvc")?.let {
            DirectVolumeControlSettings(
                enabled = it.optBoolean("enabled", fallback.dvc.enabled),
                bluetoothEnabled = it.optBoolean("bluetoothEnabled", fallback.dvc.bluetoothEnabled),
                headroomDb = it.optDouble("headroomDb", fallback.dvc.headroomDb.toDouble()).toFloat(),
                volumeSteps = enumValue(it.optString("volumeSteps"), fallback.dvc.volumeSteps),
                perDeviceDvc = it.optBoolean("perDeviceDvc", fallback.dvc.perDeviceDvc),
                compatibilityMode = it.optBoolean("compatibilityMode", fallback.dvc.compatibilityMode),
            )
        } ?: fallback.dvc
        val output = json.optJSONObject("output")?.let {
            fallback.output.copy(
                mode = enumValue(it.optString("mode"), fallback.output.mode),
                deviceProfile = enumValue(it.optString("deviceProfile"), fallback.output.deviceProfile),
                bufferMode = enumValue(it.optString("bufferMode"), fallback.output.bufferMode),
                hiResEnabled = it.optBoolean("hiResEnabled", fallback.output.hiResEnabled),
                sampleRate = enumValue(it.optString("sampleRate"), fallback.output.sampleRate),
                bitDepth = enumValue(it.optString("bitDepth"), fallback.output.bitDepth),
                bitPerfectAttemptEnabled = it.optBoolean("bitPerfectAttemptEnabled", fallback.output.bitPerfectAttemptEnabled),
                fallbackMode = enumValue(it.optString("fallbackMode"), fallback.output.fallbackMode),
                showOutputWarnings = it.optBoolean("showOutputWarnings", fallback.output.showOutputWarnings),
            )
        } ?: fallback.output
        val advancedTweaks = json.optJSONObject("advancedTweaks")?.let {
            AdvancedAudioTweaksSettings(
                volumeSteps = enumValue(it.optString("volumeSteps"), fallback.advancedTweaks.volumeSteps),
                musicFxEnabled = it.optBoolean("musicFxEnabled", fallback.advancedTweaks.musicFxEnabled),
                safeMode = it.optBoolean("safeMode", fallback.advancedTweaks.safeMode),
                debugAudioInfo = it.optBoolean("debugAudioInfo", fallback.advancedTweaks.debugAudioInfo),
            )
        } ?: fallback.advancedTweaks
        val replayMode = enumValue(json.optString("replayGainMode"), replayGain.mode)
        val graphicEqRangeMode = enumValue(json.optString("graphicEqRangeMode"), fallback.graphicEqRangeMode)
        return fallback.copy(
            enabled = json.optBoolean("enabled", fallback.enabled),
            bypass = json.optBoolean("bypass", fallback.bypass),
            preampDb = json.optDouble("preampDb", fallback.preampDb.toDouble()).toFloat(),
            replayGainMode = replayMode,
            replayGainPreampDb = json.optDouble("replayGainPreampDb", replayGain.trackPreampDb.toDouble()).toFloat(),
            preventClipping = json.optBoolean("preventClipping", replayGain.preventClipping),
            limiter = limiter,
            eqEnabled = json.optBoolean("eqEnabled", fallback.eqEnabled),
            graphicEqRangeMode = graphicEqRangeMode,
            eqBands = eqBands,
            parametricEq = parametricEq,
            bassDb = json.optDouble("bassDb", fallback.bassDb.toDouble()).toFloat(),
            trebleDb = json.optDouble("trebleDb", fallback.trebleDb.toDouble()).toFloat(),
            vocalClarityDb = json.optDouble("vocalClarityDb", fallback.vocalClarityDb.toDouble()).toFloat(),
            loudnessDb = json.optDouble("loudnessDb", fallback.loudnessDb.toDouble()).toFloat(),
            stereo = stereo,
            reverb = reverb,
            tempoPitch = tempoPitch,
            compressor = compressor,
            gate = gate,
            delay = delay,
            modulation = modulation,
            native = native,
            activePresetId = json.optString("activePresetId", fallback.activePresetId),
            presetModified = json.optBoolean("presetModified", fallback.presetModified),
            audioInfo = audioInfo,
            crossfade = crossfade,
            replayGain = replayGain,
            audioFocus = audioFocus,
            resampler = resampler,
            dvc = dvc,
            output = output,
            advancedTweaks = advancedTweaks,
        ).normalized()
    }

    private fun encodeNativeSlot(slot: NativePresetSlot): JSONObject =
        JSONObject()
            .put("name", slot.name)
            .put("enabled", slot.enabled)
            .put("bypass", slot.bypass)
            .put("preampDb", slot.preampDb)
            .put("replayGainMode", slot.replayGainMode.name)
            .put("replayGainPreampDb", slot.replayGainPreampDb)
            .put("preventClipping", slot.preventClipping)
            .put("limiter", JSONObject()
                .put("enabled", slot.limiter.enabled)
                .put("ceilingDb", slot.limiter.ceilingDb)
                .put("releaseMs", slot.limiter.releaseMs)
                .put("strength", slot.limiter.strength))
            .put("eqEnabled", slot.eqEnabled)
            .put("graphicEqRangeMode", slot.graphicEqRangeMode.name)
            .put("masterGain", slot.masterGain)
            .put("sourceFrequencyHz", slot.sourceFrequencyHz)
            .put("sourceGain", slot.sourceGain)
            .put("eqGains", JSONArray(slot.eqGains))
            .put("parametricEq", JSONArray(slot.parametricEq.map { band ->
                JSONObject()
                    .put("enabled", band.enabled)
                    .put("type", band.type.name)
                    .put("frequencyHz", band.frequencyHz)
                    .put("gainDb", band.gainDb)
                    .put("q", band.q)
            }))
            .put("bassDb", slot.bassDb)
            .put("trebleDb", slot.trebleDb)
            .put("vocalClarityDb", slot.vocalClarityDb)
            .put("loudnessDb", slot.loudnessDb)
            .put("stereo", JSONObject()
                .put("balance", slot.stereo.balance)
                .put("mono", slot.stereo.mono)
                .put("stereoWidth", slot.stereo.stereoWidth)
                .put("crossfeed", slot.stereo.crossfeed))
            .put("reverb", JSONObject()
                .put("enabled", slot.reverb.enabled)
                .put("mix", slot.reverb.mix)
                .put("size", slot.reverb.size)
                .put("preDelayMs", slot.reverb.preDelayMs)
                .put("damp", slot.reverb.damp)
                .put("decay", slot.reverb.decay))
            .put("tempoPitch", JSONObject()
                .put("speed", slot.tempoPitch.speed)
                .put("tempo", slot.tempoPitch.tempo)
                .put("pitchSemitones", slot.tempoPitch.pitchSemitones))
            .put("compressor", JSONObject()
                .put("enabled", slot.compressor.enabled)
                .put("thresholdDb", slot.compressor.thresholdDb)
                .put("ratio", slot.compressor.ratio)
                .put("attackMs", slot.compressor.attackMs)
                .put("releaseMs", slot.compressor.releaseMs)
                .put("makeupDb", slot.compressor.makeupDb)
                .put("mix", slot.compressor.mix))
            .put("gate", JSONObject()
                .put("enabled", slot.gate.enabled)
                .put("thresholdDb", slot.gate.thresholdDb)
                .put("attackMs", slot.gate.attackMs)
                .put("holdMs", slot.gate.holdMs)
                .put("releaseMs", slot.gate.releaseMs))
            .put("delay", JSONObject()
                .put("enabled", slot.delay.enabled)
                .put("timeMs", slot.delay.timeMs)
                .put("feedback", slot.delay.feedback)
                .put("mix", slot.delay.mix))
            .put("modulation", JSONObject()
                .put("chorusEnabled", slot.modulation.chorusEnabled)
                .put("flangerEnabled", slot.modulation.flangerEnabled)
                .put("phaserEnabled", slot.modulation.phaserEnabled)
                .put("rateHz", slot.modulation.rateHz)
                .put("depth", slot.modulation.depth)
                .put("feedback", slot.modulation.feedback)
                .put("mix", slot.modulation.mix))
            .put("nativeEnabled", slot.nativeEnabled)
            .put("media3DspEnabled", slot.media3DspEnabled)
            .put("lowLatencyToneEnabled", slot.lowLatencyToneEnabled)

    private fun decodeNativeSlot(json: JSONObject?, fallback: NativePresetSlot): NativePresetSlot =
        if (json == null) fallback else NativePresetSlot(
            name = json.optString("name", fallback.name),
            enabled = json.optBoolean("enabled", fallback.enabled),
            bypass = json.optBoolean("bypass", fallback.bypass),
            preampDb = json.optDouble("preampDb", fallback.preampDb.toDouble()).toFloat(),
            replayGainMode = enumValue(json.optString("replayGainMode"), fallback.replayGainMode),
            replayGainPreampDb = json.optDouble("replayGainPreampDb", fallback.replayGainPreampDb.toDouble()).toFloat(),
            preventClipping = json.optBoolean("preventClipping", fallback.preventClipping),
            limiter = json.optJSONObject("limiter")?.let {
                LimiterState(
                    enabled = it.optBoolean("enabled", fallback.limiter.enabled),
                    ceilingDb = it.optDouble("ceilingDb", fallback.limiter.ceilingDb.toDouble()).toFloat(),
                    releaseMs = it.optDouble("releaseMs", fallback.limiter.releaseMs.toDouble()).toFloat(),
                    strength = it.optDouble("strength", fallback.limiter.strength.toDouble()).toFloat(),
                )
            } ?: fallback.limiter,
            eqEnabled = json.optBoolean("eqEnabled", fallback.eqEnabled),
            graphicEqRangeMode = enumValue(json.optString("graphicEqRangeMode"), fallback.graphicEqRangeMode),
            masterGain = json.optDouble("masterGain", fallback.masterGain.toDouble()).toFloat(),
            sourceFrequencyHz = json.optDouble("sourceFrequencyHz", fallback.sourceFrequencyHz.toDouble()).toFloat(),
            sourceGain = json.optDouble("sourceGain", fallback.sourceGain.toDouble()).toFloat(),
            eqGains = json.optJSONArray("eqGains")?.let { array ->
                List(array.length()) { index -> array.optDouble(index, 0.0).toFloat() }
            } ?: fallback.eqGains,
            parametricEq = json.optJSONArray("parametricEq")?.let { array ->
                List(array.length()) { index ->
                    val item = array.optJSONObject(index)
                    val base = fallback.parametricEq.getOrNull(index) ?: ParametricEqBand()
                    ParametricEqBand(
                        enabled = item?.optBoolean("enabled", base.enabled) ?: base.enabled,
                        type = enumValue(item?.optString("type"), base.type),
                        frequencyHz = item?.optDouble("frequencyHz", base.frequencyHz.toDouble())?.toFloat() ?: base.frequencyHz,
                        gainDb = item?.optDouble("gainDb", base.gainDb.toDouble())?.toFloat() ?: base.gainDb,
                        q = item?.optDouble("q", base.q.toDouble())?.toFloat() ?: base.q,
                    )
                }
            } ?: fallback.parametricEq,
            bassDb = json.optDouble("bassDb", fallback.bassDb.toDouble()).toFloat(),
            trebleDb = json.optDouble("trebleDb", fallback.trebleDb.toDouble()).toFloat(),
            vocalClarityDb = json.optDouble("vocalClarityDb", fallback.vocalClarityDb.toDouble()).toFloat(),
            loudnessDb = json.optDouble("loudnessDb", fallback.loudnessDb.toDouble()).toFloat(),
            stereo = json.optJSONObject("stereo")?.let {
                StereoState(
                    balance = it.optDouble("balance", fallback.stereo.balance.toDouble()).toFloat(),
                    mono = it.optBoolean("mono", fallback.stereo.mono),
                    stereoWidth = it.optDouble("stereoWidth", fallback.stereo.stereoWidth.toDouble()).toFloat(),
                    crossfeed = it.optDouble("crossfeed", fallback.stereo.crossfeed.toDouble()).toFloat(),
                )
            } ?: fallback.stereo,
            reverb = json.optJSONObject("reverb")?.let {
                ReverbState(
                    enabled = it.optBoolean("enabled", fallback.reverb.enabled),
                    mix = it.optDouble("mix", fallback.reverb.mix.toDouble()).toFloat(),
                    size = it.optDouble("size", fallback.reverb.size.toDouble()).toFloat(),
                    preDelayMs = it.optDouble("preDelayMs", fallback.reverb.preDelayMs.toDouble()).toFloat(),
                    damp = it.optDouble("damp", fallback.reverb.damp.toDouble()).toFloat(),
                    decay = it.optDouble("decay", fallback.reverb.decay.toDouble()).toFloat(),
                )
            } ?: fallback.reverb,
            tempoPitch = json.optJSONObject("tempoPitch")?.let {
                TempoPitchState(
                    speed = it.optDouble("speed", fallback.tempoPitch.speed.toDouble()).toFloat(),
                    tempo = it.optDouble("tempo", fallback.tempoPitch.tempo.toDouble()).toFloat(),
                    pitchSemitones = it.optDouble("pitchSemitones", fallback.tempoPitch.pitchSemitones.toDouble()).toFloat(),
                )
            } ?: fallback.tempoPitch,
            compressor = json.optJSONObject("compressor")?.let {
                CompressorState(
                    enabled = it.optBoolean("enabled", fallback.compressor.enabled),
                    thresholdDb = it.optDouble("thresholdDb", fallback.compressor.thresholdDb.toDouble()).toFloat(),
                    ratio = it.optDouble("ratio", fallback.compressor.ratio.toDouble()).toFloat(),
                    attackMs = it.optDouble("attackMs", fallback.compressor.attackMs.toDouble()).toFloat(),
                    releaseMs = it.optDouble("releaseMs", fallback.compressor.releaseMs.toDouble()).toFloat(),
                    makeupDb = it.optDouble("makeupDb", fallback.compressor.makeupDb.toDouble()).toFloat(),
                    mix = it.optDouble("mix", fallback.compressor.mix.toDouble()).toFloat(),
                )
            } ?: fallback.compressor,
            gate = json.optJSONObject("gate")?.let {
                GateState(
                    enabled = it.optBoolean("enabled", fallback.gate.enabled),
                    thresholdDb = it.optDouble("thresholdDb", fallback.gate.thresholdDb.toDouble()).toFloat(),
                    attackMs = it.optDouble("attackMs", fallback.gate.attackMs.toDouble()).toFloat(),
                    holdMs = it.optDouble("holdMs", fallback.gate.holdMs.toDouble()).toFloat(),
                    releaseMs = it.optDouble("releaseMs", fallback.gate.releaseMs.toDouble()).toFloat(),
                )
            } ?: fallback.gate,
            delay = json.optJSONObject("delay")?.let {
                DelayState(
                    enabled = it.optBoolean("enabled", fallback.delay.enabled),
                    timeMs = it.optDouble("timeMs", fallback.delay.timeMs.toDouble()).toFloat(),
                    feedback = it.optDouble("feedback", fallback.delay.feedback.toDouble()).toFloat(),
                    mix = it.optDouble("mix", fallback.delay.mix.toDouble()).toFloat(),
                )
            } ?: fallback.delay,
            modulation = json.optJSONObject("modulation")?.let {
                ModulationState(
                    chorusEnabled = it.optBoolean("chorusEnabled", fallback.modulation.chorusEnabled),
                    flangerEnabled = it.optBoolean("flangerEnabled", fallback.modulation.flangerEnabled),
                    phaserEnabled = it.optBoolean("phaserEnabled", fallback.modulation.phaserEnabled),
                    rateHz = it.optDouble("rateHz", fallback.modulation.rateHz.toDouble()).toFloat(),
                    depth = it.optDouble("depth", fallback.modulation.depth.toDouble()).toFloat(),
                    feedback = it.optDouble("feedback", fallback.modulation.feedback.toDouble()).toFloat(),
                    mix = it.optDouble("mix", fallback.modulation.mix.toDouble()).toFloat(),
                )
            } ?: fallback.modulation,
            nativeEnabled = json.optBoolean("nativeEnabled", fallback.nativeEnabled),
            media3DspEnabled = json.optBoolean("media3DspEnabled", fallback.media3DspEnabled),
            lowLatencyToneEnabled = json.optBoolean("lowLatencyToneEnabled", fallback.lowLatencyToneEnabled),
        )

    private fun encodeMisc(misc: MiscSettings): JSONObject =
        JSONObject()
            .put("scrobbling", JSONObject()
                .put("lastFmEnabled", misc.scrobbling.lastFmEnabled)
                .put("simpleScrobblerEnabled", misc.scrobbling.simpleScrobblerEnabled)
                .put("sessionKeyPresent", misc.scrobbling.sessionKeyPresent)
                .put("nowPlayingEnabled", misc.scrobbling.nowPlayingEnabled)
                .put("retryQueueEnabled", misc.scrobbling.retryQueueEnabled))
            .put("androidAuto", JSONObject()
                .put("gridViewForCategories", misc.androidAuto.gridViewForCategories)
                .put("imagesForCategories", misc.androidAuto.imagesForCategories)
                .put("albumArtForTracks", misc.androidAuto.albumArtForTracks)
                .put("nowPlayingListForConnectedDevices", misc.androidAuto.nowPlayingListForConnectedDevices)
                .put("customButtons", JSONObject()
                    .put("button1", misc.androidAuto.customButtons.button1.name)
                    .put("button2", misc.androidAuto.customButtons.button2.name)
                    .put("button3", misc.androidAuto.customButtons.button3.name)
                    .put("button4", misc.androidAuto.customButtons.button4.name)
                    .put("button5", misc.androidAuto.customButtons.button5.name)
                    .put("button6", misc.androidAuto.customButtons.button6.name)))
            .put("online", JSONObject()
                .put("offlineMode", misc.online.offlineMode)
                .put("premiumDeckOfflineMode", misc.online.premiumDeckOfflineMode)
                .put("useProxyFallback", misc.online.useProxyFallback)
                .put("pulseDataSaver", misc.online.pulseDataSaver)
                .put("wifiStreamingQuality", misc.online.wifiStreamingQuality.name)
                .put("cellularStreamingQuality", misc.online.cellularStreamingQuality.name)
                .put("roamingStreamingQuality", misc.online.roamingStreamingQuality.name)
                .put("dataSaverStreamingQuality", misc.online.dataSaverStreamingQuality.name)
                .put("streamPreviewNetworkPolicy", misc.online.streamPreviewNetworkPolicy.name)
                .put("allowMuxedStreamFallbackOnCellular", misc.online.allowMuxedStreamFallbackOnCellular)
                .put("allowMuxedStreamFallbackInDataSaver", misc.online.allowMuxedStreamFallbackInDataSaver)
                .put("sponsorBlock", misc.online.sponsorBlock)
                .put("automaticSongPicker", misc.online.automaticSongPicker)
                .put("externalRecommendations", misc.online.externalRecommendations)
                .put("premiumDeckPersonalization", misc.online.premiumDeckPersonalization)
                .put("smartRecommendationMode", misc.online.smartRecommendationMode.name))
            .put("metachangedIntent", misc.metachangedIntent)
            .put("useWakelock", misc.useWakelock)
            .put("sendAlbumArtForOldApi", misc.sendAlbumArtForOldApi)
            .put("alwaysReloadSkin", misc.alwaysReloadSkin)
            .put("pauseOnScreenOff", misc.pauseOnScreenOff)
            .put("applyHighFramerate", misc.applyHighFramerate)
            .put("networkStreamTimeoutSeconds", misc.networkStreamTimeoutSeconds)
            .put("networkStreamBufferMb", misc.networkStreamBufferMb)
            .put("userAgent", misc.userAgent)
            .put("changeTracksByLongVolumeKeys", misc.changeTracksByLongVolumeKeys)

    private fun decodeLook(json: JSONObject?, fallback: LookAndFeelSettings): LookAndFeelSettings =
        if (json == null) fallback else fallback.copy(
            skinId = json.optString("skinId", fallback.skinId),
            followSystemTheme = json.optBoolean("followSystemTheme", fallback.followSystemTheme),
            settingsTheme = enumValue(json.optString("settingsTheme"), fallback.settingsTheme),
            settingsFont = enumValue(json.optString("settingsFont"), fallback.settingsFont),
            appFontScale = json.optDouble("appFontScale", fallback.appFontScale.toDouble()).toFloat(),
            language = json.optString("language", fallback.language),
            appIcon = json.optString("appIcon", fallback.appIcon),
            orientation = enumValue(json.optString("orientation"), fallback.orientation),
            animationSpeed = enumValue(json.optString("animationSpeed"), fallback.animationSpeed),
            startAtLibrary = json.optBoolean("startAtLibrary", fallback.startAtLibrary),
            hideStatusBar = json.optBoolean("hideStatusBar", fallback.hideStatusBar),
            keepScreenOn = json.optBoolean("keepScreenOn", fallback.keepScreenOn),
            settingsShortcutsCount = json.optInt("settingsShortcutsCount", fallback.settingsShortcutsCount),
        )

    private fun decodePlayerUi(json: JSONObject?, fallback: PlayerUiSettings): PlayerUiSettings =
        if (json == null) fallback else fallback.copy(
            utilityButtons = json.optJSONArray("utilityButtons")?.let { array ->
                List(array.length()) { index -> array.optString(index) }
                    .mapNotNull { name -> PlayerButtonAction.entries.firstOrNull { it.name == name } }
            } ?: fallback.utilityButtons,
            landscapeUtilityButtons = json.optJSONArray("landscapeUtilityButtons")?.let { array ->
                List(array.length()) { index -> array.optString(index) }
                    .mapNotNull { name -> PlayerButtonAction.entries.firstOrNull { it.name == name } }
            } ?: fallback.landscapeUtilityButtons,
            portraitButtonRows = json.optInt("portraitButtonRows", fallback.portraitButtonRows),
            landscapeButtonRows = json.optInt("landscapeButtonRows", fallback.landscapeButtonRows),
            utilityButtonSize = enumValue(json.optString("utilityButtonSize"), fallback.utilityButtonSize),
            utilityButtonsScrollable = json.optBoolean("utilityButtonsScrollable", fallback.utilityButtonsScrollable),
            showGestureHints = json.optBoolean("showGestureHints", fallback.showGestureHints),
            startAtPlayerWhenRestored = json.optBoolean("startAtPlayerWhenRestored", fallback.startAtPlayerWhenRestored),
            enableLandscapeSplit = json.optBoolean("enableLandscapeSplit", fallback.enableLandscapeSplit),
            mirrorUtilityLayout = json.optBoolean("mirrorUtilityLayout", fallback.mirrorUtilityLayout),
            miniPlayerAppearance = decodeMiniPlayerAppearance(json.optJSONObject("miniPlayerAppearance"), fallback.miniPlayerAppearance),
            fullPlayerContent = decodeFullPlayerContent(json.optJSONObject("fullPlayerContent"), fallback.fullPlayerContent),
        )

    private fun decodeMiniPlayerAppearance(json: JSONObject?, fallback: MiniPlayerAppearanceSettings): MiniPlayerAppearanceSettings =
        if (json == null) fallback else fallback.copy(
            style = enumValue(json.optString("style"), fallback.style),
            transparency = json.optDouble("transparency", fallback.transparency.toDouble()).toFloat(),
            albumTintStrength = json.optDouble("albumTintStrength", fallback.albumTintStrength.toDouble()).toFloat(),
            borderStrength = json.optDouble("borderStrength", fallback.borderStrength.toDouble()).toFloat(),
            useHighContrastText = json.optBoolean("useHighContrastText", fallback.useHighContrastText),
        )

    private fun decodeFullPlayerContent(json: JSONObject?, fallback: FullPlayerContentSettings): FullPlayerContentSettings =
        if (json == null) fallback else fallback.copy(
            showLyrics = json.optBoolean("showLyrics", fallback.showLyrics),
            showMoreFromArtist = json.optBoolean("showMoreFromArtist", fallback.showMoreFromArtist),
            showDiscography = json.optBoolean("showDiscography", fallback.showDiscography),
            showAboutArtist = json.optBoolean("showAboutArtist", fallback.showAboutArtist),
            localArtistDiscoveryPolicy = enumValue(json.optString("localArtistDiscoveryPolicy"), fallback.localArtistDiscoveryPolicy),
            compactCards = json.optBoolean("compactCards", fallback.compactCards),
        ).normalized()

    private fun decodeBackground(json: JSONObject?, fallback: BackgroundSettings): BackgroundSettings =
        if (json == null) fallback else fallback.copy(
            blurred = json.optBoolean("blurred", fallback.blurred),
            listBackground = json.optBoolean("listBackground", fallback.listBackground),
            lyricsBackground = json.optBoolean("lyricsBackground", fallback.lyricsBackground),
            gradient = json.optDouble("gradient", fallback.gradient.toDouble()).toFloat(),
            gradientColor = json.optString("gradientColor", fallback.gradientColor),
            gradientForLists = json.optBoolean("gradientForLists", fallback.gradientForLists),
            blur = json.optDouble("blur", fallback.blur.toDouble()).toFloat(),
            details = json.optDouble("details", fallback.details.toDouble()).toFloat(),
            intensity = json.optDouble("intensity", fallback.intensity.toDouble()).toFloat(),
            saturation = json.optDouble("saturation", fallback.saturation.toDouble()).toFloat(),
            readabilityProtection = json.optJSONObject("readabilityProtection")?.let {
                ReadabilityProtectionSettings(
                    enabled = it.optBoolean("enabled", fallback.readabilityProtection.enabled),
                    autoDimLists = it.optBoolean("autoDimLists", fallback.readabilityProtection.autoDimLists),
                    autoDimLyrics = it.optBoolean("autoDimLyrics", fallback.readabilityProtection.autoDimLyrics),
                    minimumTextContrast = it.optString("minimumTextContrast", fallback.readabilityProtection.minimumTextContrast),
                )
            } ?: fallback.readabilityProtection,
        )

    private fun decodeVisualization(json: JSONObject?, fallback: VisualizationSettings): VisualizationSettings =
        if (json == null) fallback else fallback.copy(
            playerVisualizationEnabled = json.optBoolean("playerVisualizationEnabled", fallback.playerVisualizationEnabled),
            equalizerSpectrumMode = enumValue(json.optString("equalizerSpectrumMode"), fallback.equalizerSpectrumMode),
            libraryVisualizationEnabled = json.optBoolean("libraryVisualizationEnabled", fallback.libraryVisualizationEnabled),
            presetDurationMs = json.optInt("presetDurationMs", fallback.presetDurationMs),
            topPanelOpacityPercent = json.optInt("topPanelOpacityPercent", fallback.topPanelOpacityPercent),
            fadedControlsOpacityPercent = json.optInt("fadedControlsOpacityPercent", fallback.fadedControlsOpacityPercent),
            uiTimeoutMs = json.optInt("uiTimeoutMs", fallback.uiTimeoutMs),
            visibleAlbumArt = json.optBoolean("visibleAlbumArt", fallback.visibleAlbumArt),
            ignoreTouch = json.optBoolean("ignoreTouch", fallback.ignoreTouch),
            trackOpacityPercent = json.optInt("trackOpacityPercent", fallback.trackOpacityPercent),
            hideSystemBarsFullscreen = json.optBoolean("hideSystemBarsFullscreen", fallback.hideSystemBarsFullscreen),
            scaledBarsForFadedControls = json.optBoolean("scaledBarsForFadedControls", fallback.scaledBarsForFadedControls),
            hdEnabled = json.optBoolean("hdEnabled", fallback.hdEnabled),
            cropAspect = json.optBoolean("cropAspect", fallback.cropAspect),
            force30Fps = json.optBoolean("force30Fps", fallback.force30Fps),
            strictPresetMatching = json.optBoolean("strictPresetMatching", fallback.strictPresetMatching),
            visualizationDelayMs = json.optInt("visualizationDelayMs", fallback.visualizationDelayMs),
            hideUnlikedPresets = json.optBoolean("hideUnlikedPresets", fallback.hideUnlikedPresets),
            spectrumBarsEnabled = json.optBoolean("spectrumBarsEnabled", fallback.spectrumBarsEnabled),
            builtInPresetsEnabled = json.optBoolean("builtInPresetsEnabled", fallback.builtInPresetsEnabled),
            folderPresetsEnabled = json.optBoolean("folderPresetsEnabled", fallback.folderPresetsEnabled),
        )

    private fun decodeAlbumArt(json: JSONObject?, fallback: AlbumArtSettings): AlbumArtSettings =
        if (json == null) fallback else fallback.copy(
            downloadAlbumArt = json.optBoolean("downloadAlbumArt", fallback.downloadAlbumArt),
            providerMode = enumValue(json.optString("providerMode"), fallback.providerMode),
            highestQualityCovers = json.optBoolean("highestQualityCovers", fallback.highestQualityCovers),
            autoReplaceExistingArt = json.optBoolean("autoReplaceExistingArt", fallback.autoReplaceExistingArt),
            minimumUpgradePercent = json.optInt("minimumUpgradePercent", fallback.minimumUpgradePercent),
            saveFolderCover = json.optBoolean("saveFolderCover", fallback.saveFolderCover),
            providerOrder = json.optJSONArray("providerOrder")?.let { array ->
                List(array.length()) { index -> array.optString(index) }
                    .mapNotNull { raw -> enumValueOrNull<AlbumArtworkProviderId>(raw) }
            } ?: fallback.providerOrder,
            preferEmbeddedArtwork = json.optBoolean("preferEmbeddedArtwork", fallback.preferEmbeddedArtwork),
            preferFolderImage = json.optBoolean("preferFolderImage", fallback.preferFolderImage),
            preferOnlineImage = json.optBoolean("preferOnlineImage", fallback.preferOnlineImage),
            quality = enumValue(json.optString("quality"), fallback.quality),
            cacheSizeMb = json.optInt("cacheSizeMb", fallback.cacheSizeMb),
            showDefaultImageWhenMissing = json.optBoolean("showDefaultImageWhenMissing", fallback.showDefaultImageWhenMissing),
        )

    private fun decodeLyrics(json: JSONObject?, fallback: LyricsSettings): LyricsSettings =
        if (json == null) fallback else fallback.copy(
            enabled = json.optBoolean("enabled", fallback.enabled),
            preferSynced = json.optBoolean("preferSynced", fallback.preferSynced),
            onlineLookup = json.optBoolean("onlineLookup", fallback.onlineLookup),
            cacheSizeMb = json.optInt("cacheSizeMb", fallback.cacheSizeMb),
        )

    private fun decodeLibrary(json: JSONObject?, fallback: LibrarySettings): LibrarySettings =
        if (json == null) fallback else fallback.copy(
            musicFolders = json.optJSONArray("musicFolders")?.let { array ->
                List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
            } ?: fallback.musicFolders,
            includeShortTracks = json.optBoolean("includeShortTracks", fallback.includeShortTracks),
            shortTrackThresholdSeconds = json.optInt("shortTrackThresholdSeconds", fallback.shortTrackThresholdSeconds),
            sortMode = enumValue(json.optString("sortMode"), fallback.sortMode),
            folderHierarchyMode = json.optBoolean("folderHierarchyMode", fallback.folderHierarchyMode),
            preserveQueueOnRestart = json.optBoolean("preserveQueueOnRestart", fallback.preserveQueueOnRestart),
            rescanOnLaunch = json.optBoolean("rescanOnLaunch", fallback.rescanOnLaunch),
        )

    private fun decodeHeadset(json: JSONObject?, fallback: HeadsetBluetoothSettings): HeadsetBluetoothSettings =
        if (json == null) fallback else fallback.copy(
            pauseOnHeadsetDisconnect = json.optBoolean("pauseOnHeadsetDisconnect", fallback.pauseOnHeadsetDisconnect),
            resumeOnWiredHeadset = json.optBoolean("resumeOnWiredHeadset", fallback.resumeOnWiredHeadset),
            resumeOnBluetooth = json.optBoolean("resumeOnBluetooth", fallback.resumeOnBluetooth),
            respondToButtons = json.optBoolean("respondToButtons", fallback.respondToButtons),
            wiredButtonMode = enumValue(json.optString("wiredButtonMode"), fallback.wiredButtonMode),
            bluetoothButtonMode = enumValue(json.optString("bluetoothButtonMode"), fallback.bluetoothButtonMode),
            strictResumePauseStop = json.optBoolean("strictResumePauseStop", fallback.strictResumePauseStop),
            ignoreBluetoothCommandsSeconds = json.optInt("ignoreBluetoothCommandsSeconds", fallback.ignoreBluetoothCommandsSeconds),
            ignoreRepeatShuffle = json.optBoolean("ignoreRepeatShuffle", fallback.ignoreRepeatShuffle),
            beep = json.optBoolean("beep", fallback.beep),
            beepMore = json.optBoolean("beepMore", fallback.beepMore),
            beepVolume = json.optDouble("beepVolume", fallback.beepVolume.toDouble()).toFloat(),
            vibrate = json.optBoolean("vibrate", fallback.vibrate),
        )

    private fun decodeLock(json: JSONObject?, fallback: LockScreenSettings): LockScreenSettings =
        if (json == null) fallback else fallback.copy(
            albumArtOnLockScreen = json.optBoolean("albumArtOnLockScreen", fallback.albumArtOnLockScreen),
            blurAlbumArt = json.optBoolean("blurAlbumArt", fallback.blurAlbumArt),
            showDefaultImageWhenMissing = json.optBoolean("showDefaultImageWhenMissing", fallback.showDefaultImageWhenMissing),
            customLockScreenEnabled = json.optBoolean("customLockScreenEnabled", fallback.customLockScreenEnabled),
            shorterTimeout = json.optBoolean("shorterTimeout", fallback.shorterTimeout),
            landscapeLayout = json.optBoolean("landscapeLayout", fallback.landscapeLayout),
            directUnlock = json.optBoolean("directUnlock", fallback.directUnlock),
        )

    private fun decodeMisc(json: JSONObject?, fallback: MiscSettings): MiscSettings {
        if (json == null) return fallback
        val scrobbling = json.optJSONObject("scrobbling")
        val androidAuto = json.optJSONObject("androidAuto")
        val buttons = androidAuto?.optJSONObject("customButtons")
        val online = json.optJSONObject("online")
        return fallback.copy(
            scrobbling = fallback.scrobbling.copy(
                lastFmEnabled = scrobbling?.optBoolean("lastFmEnabled", fallback.scrobbling.lastFmEnabled) ?: fallback.scrobbling.lastFmEnabled,
                simpleScrobblerEnabled = scrobbling?.optBoolean("simpleScrobblerEnabled", fallback.scrobbling.simpleScrobblerEnabled) ?: fallback.scrobbling.simpleScrobblerEnabled,
                sessionKeyPresent = scrobbling?.optBoolean("sessionKeyPresent", fallback.scrobbling.sessionKeyPresent) ?: fallback.scrobbling.sessionKeyPresent,
                nowPlayingEnabled = scrobbling?.optBoolean("nowPlayingEnabled", fallback.scrobbling.nowPlayingEnabled) ?: fallback.scrobbling.nowPlayingEnabled,
                retryQueueEnabled = scrobbling?.optBoolean("retryQueueEnabled", fallback.scrobbling.retryQueueEnabled) ?: fallback.scrobbling.retryQueueEnabled,
            ),
            androidAuto = fallback.androidAuto.copy(
                gridViewForCategories = androidAuto?.optBoolean("gridViewForCategories", fallback.androidAuto.gridViewForCategories) ?: fallback.androidAuto.gridViewForCategories,
                imagesForCategories = androidAuto?.optBoolean("imagesForCategories", fallback.androidAuto.imagesForCategories) ?: fallback.androidAuto.imagesForCategories,
                albumArtForTracks = androidAuto?.optBoolean("albumArtForTracks", fallback.androidAuto.albumArtForTracks) ?: fallback.androidAuto.albumArtForTracks,
                nowPlayingListForConnectedDevices = androidAuto?.optBoolean("nowPlayingListForConnectedDevices", fallback.androidAuto.nowPlayingListForConnectedDevices) ?: fallback.androidAuto.nowPlayingListForConnectedDevices,
                customButtons = MediaButtonSettings(
                    button1 = enumValue(buttons?.optString("button1"), fallback.androidAuto.customButtons.button1),
                    button2 = enumValue(buttons?.optString("button2"), fallback.androidAuto.customButtons.button2),
                    button3 = enumValue(buttons?.optString("button3"), fallback.androidAuto.customButtons.button3),
                    button4 = enumValue(buttons?.optString("button4"), fallback.androidAuto.customButtons.button4),
                    button5 = enumValue(buttons?.optString("button5"), fallback.androidAuto.customButtons.button5),
                    button6 = enumValue(buttons?.optString("button6"), fallback.androidAuto.customButtons.button6),
                ),
            ),
            online = decodeOnlineSettings(online, fallback.online),
            metachangedIntent = json.optBoolean("metachangedIntent", fallback.metachangedIntent),
            useWakelock = json.optBoolean("useWakelock", fallback.useWakelock),
            sendAlbumArtForOldApi = json.optBoolean("sendAlbumArtForOldApi", fallback.sendAlbumArtForOldApi),
            alwaysReloadSkin = json.optBoolean("alwaysReloadSkin", fallback.alwaysReloadSkin),
            pauseOnScreenOff = json.optBoolean("pauseOnScreenOff", fallback.pauseOnScreenOff),
            applyHighFramerate = json.optBoolean("applyHighFramerate", fallback.applyHighFramerate),
            networkStreamTimeoutSeconds = json.optInt("networkStreamTimeoutSeconds", fallback.networkStreamTimeoutSeconds),
            networkStreamBufferMb = json.optDouble("networkStreamBufferMb", fallback.networkStreamBufferMb.toDouble()).toFloat(),
            userAgent = json.optString("userAgent", fallback.userAgent),
            changeTracksByLongVolumeKeys = json.optBoolean("changeTracksByLongVolumeKeys", fallback.changeTracksByLongVolumeKeys),
        )
    }

    private fun decodeOnlineSettings(json: JSONObject?, fallback: OnlineFeatureSettings): OnlineFeatureSettings {
        val legacyPersonalization = json?.optBoolean("premiumDeckPersonalization", fallback.premiumDeckPersonalization)
            ?: fallback.premiumDeckPersonalization
        val mode = if (json?.has("smartRecommendationMode") == true) {
            enumValue(json.optString("smartRecommendationMode"), fallback.smartRecommendationMode)
        } else if (!legacyPersonalization) {
            PremiumDeckSmartRecommendationMode.Off
        } else {
            fallback.smartRecommendationMode
        }
        return OnlineFeatureSettings(
            offlineMode = json?.optBoolean("offlineMode", fallback.offlineMode) ?: fallback.offlineMode,
            premiumDeckOfflineMode = json?.optBoolean("premiumDeckOfflineMode", fallback.premiumDeckOfflineMode)
                ?: fallback.premiumDeckOfflineMode,
            useProxyFallback = json?.optBoolean("useProxyFallback", fallback.useProxyFallback) ?: fallback.useProxyFallback,
            pulseDataSaver = json?.optBoolean("pulseDataSaver", fallback.pulseDataSaver) ?: fallback.pulseDataSaver,
            wifiStreamingQuality = enumValue(json?.optString("wifiStreamingQuality"), fallback.wifiStreamingQuality),
            cellularStreamingQuality = enumValue(json?.optString("cellularStreamingQuality"), fallback.cellularStreamingQuality),
            roamingStreamingQuality = enumValue(json?.optString("roamingStreamingQuality"), fallback.roamingStreamingQuality),
            dataSaverStreamingQuality = enumValue(json?.optString("dataSaverStreamingQuality"), fallback.dataSaverStreamingQuality),
            streamPreviewNetworkPolicy = enumValue(json?.optString("streamPreviewNetworkPolicy"), fallback.streamPreviewNetworkPolicy),
            allowMuxedStreamFallbackOnCellular = json?.optBoolean("allowMuxedStreamFallbackOnCellular", fallback.allowMuxedStreamFallbackOnCellular)
                ?: fallback.allowMuxedStreamFallbackOnCellular,
            allowMuxedStreamFallbackInDataSaver = json?.optBoolean("allowMuxedStreamFallbackInDataSaver", fallback.allowMuxedStreamFallbackInDataSaver)
                ?: fallback.allowMuxedStreamFallbackInDataSaver,
            sponsorBlock = json?.optBoolean("sponsorBlock", fallback.sponsorBlock) ?: fallback.sponsorBlock,
            automaticSongPicker = json?.optBoolean("automaticSongPicker", fallback.automaticSongPicker) ?: fallback.automaticSongPicker,
            externalRecommendations = json?.optBoolean("externalRecommendations", fallback.externalRecommendations) ?: fallback.externalRecommendations,
            premiumDeckPersonalization = legacyPersonalization && mode != PremiumDeckSmartRecommendationMode.Off,
            smartRecommendationMode = mode,
        )
    }

    private fun decodeDiagnostics(json: JSONObject?, fallback: DiagnosticsSettings): DiagnosticsSettings =
        if (json == null) fallback else fallback.copy(
            sendErrorsToDeveloper = json.optBoolean("sendErrorsToDeveloper", fallback.sendErrorsToDeveloper),
            includeAudioOutputLog = json.optBoolean("includeAudioOutputLog", fallback.includeAudioOutputLog),
            includeDeviceCapabilities = json.optBoolean("includeDeviceCapabilities", fallback.includeDeviceCapabilities),
            redactTrackPaths = json.optBoolean("redactTrackPaths", fallback.redactTrackPaths),
        )

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
        enumValues<T>().firstOrNull { it.name == raw }
}
