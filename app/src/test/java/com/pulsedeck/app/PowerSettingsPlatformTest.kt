package com.pulsedeck.app

import com.pulsedeck.app.player.PlaybackRepeatMode
import com.pulsedeck.app.player.ShuffleMode
import com.pulsedeck.app.player.nextRepeatMode
import com.pulsedeck.app.player.nextShuffleMode
import com.pulsedeck.app.settings.data.SettingsProfileCodec
import com.pulsedeck.app.settings.model.AnimationSpeedSetting
import com.pulsedeck.app.settings.model.FullPlayerContentSettings
import com.pulsedeck.app.settings.model.LockScreenSettings
import com.pulsedeck.app.settings.model.LocalArtistDiscoveryPolicy
import com.pulsedeck.app.settings.model.LookAndFeelSettings
import com.pulsedeck.app.settings.model.MiscSettings
import com.pulsedeck.app.settings.model.MiniPlayerAppearanceSettings
import com.pulsedeck.app.settings.model.MiniPlayerAppearanceStyle
import com.pulsedeck.app.settings.model.OnlineFeatureSettings
import com.pulsedeck.app.settings.model.PlayerButtonAction
import com.pulsedeck.app.settings.model.PlayerButtonSize
import com.pulsedeck.app.settings.model.PlayerUiSettings
import com.pulsedeck.app.settings.model.PulseSettingsState
import com.pulsedeck.app.settings.model.PulseDeckThirdPartyLicenses
import com.pulsedeck.app.settings.model.ScrobblingSettings
import com.pulsedeck.app.settings.model.SettingKey
import com.pulsedeck.app.settings.model.SettingScope
import com.pulsedeck.app.settings.model.SettingsIntegrationStatus
import com.pulsedeck.app.settings.model.SettingsTheme
import com.pulsedeck.app.settings.model.StreamPreviewNetworkPolicy
import com.pulsedeck.app.settings.model.StreamingQualityPolicy
import com.pulsedeck.app.settings.model.pulseSettingsCatalog
import com.pulsedeck.app.settings.model.settingsSearchMatches
import com.pulsedeck.app.settings.model.shouldSubmitLastFmScrobble
import com.pulsedeck.app.settings.runtime.DeviceCapabilities
import com.pulsedeck.app.settings.runtime.OutputDeviceType
import com.pulsedeck.app.settings.runtime.OutputPluginType
import com.pulsedeck.app.settings.runtime.PlaybackRuntimeSettings
import com.pulsedeck.app.settings.runtime.BluetoothCommandDebouncer
import com.pulsedeck.app.settings.runtime.mediaSessionCustomActions
import com.pulsedeck.app.settings.runtime.mediaActionForCustomCommand
import com.pulsedeck.app.settings.runtime.outputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.toPlaybackRuntimeSettings
import com.pulsedeck.app.settings.model.MediaAction
import com.pulsedeck.app.settings.model.ButtonPressMode
import com.pulsedeck.app.settings.model.HeadsetBluetoothSettings
import com.pulsedeck.app.settings.model.LibrarySettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSettingsPlatformTest {
    @Test
    fun pulseSettingsNormalizeCategoryRangesAndDependencies() {
        val normalized = PulseSettingsState(
            lookAndFeel = LookAndFeelSettings(settingsShortcutsCount = 99, appFontScale = 3f),
            background = BackgroundSettings(saturation = 5f, blurred = false, listBackground = true),
            lockScreen = LockScreenSettings(customLockScreenEnabled = false, shorterTimeout = true),
            misc = MiscSettings(networkStreamTimeoutSeconds = 999, networkStreamBufferMb = 99f),
        ).normalized()

        assertEquals(10, normalized.lookAndFeel.settingsShortcutsCount)
        assertEquals(1.30f, normalized.lookAndFeel.appFontScale, 0.001f)
        assertEquals(2f, normalized.background.saturation, 0.001f)
        assertFalse(normalized.background.listBackground)
        assertFalse(normalized.lockScreen.shorterTimeout)
        assertEquals(300, normalized.misc.networkStreamTimeoutSeconds)
        assertEquals(16f, normalized.misc.networkStreamBufferMb, 0.001f)
    }

    @Test
    fun settingsCatalogSearchFindsSynonymsAndDeepAudioPaths() {
        val catalog = pulseSettingsCatalog()

        assertTrue(catalog.any { settingsSearchMatches(it, "bt debounce") })
        assertTrue(catalog.any { settingsSearchMatches(it, "cover cache") })
        assertTrue(catalog.any { settingsSearchMatches(it, "replay gain") })
        assertTrue(catalog.any { settingsSearchMatches(it, "cast output") })
        assertTrue(catalog.any { settingsSearchMatches(it, "player gesture hints") })
        assertTrue(catalog.any { settingsSearchMatches(it, "landscape player buttons") })
        assertTrue(catalog.any { settingsSearchMatches(it, "font size") })
        assertTrue(catalog.any { settingsSearchMatches(it, "licenses open source") })
        assertTrue(catalog.any { settingsSearchMatches(it, "private backup") })
        assertTrue(catalog.any { settingsSearchMatches(it, "full player artist") })
        assertTrue(catalog.any { settingsSearchMatches(it, "discography albums") })
        assertTrue(catalog.any { settingsSearchMatches(it, "network online") })
    }

    @Test
    fun settingsExportImportRoundTripPreservesSupportedFields() {
        val original = PulseSettingsState(
            lookAndFeel = LookAndFeelSettings(
                settingsTheme = SettingsTheme.Dark,
                animationSpeed = AnimationSpeedSetting.Fast,
                appFontScale = 1.18f,
                settingsShortcutsCount = 7,
            ),
            playerUi = PlayerUiSettings(
                utilityButtons = listOf(
                    PlayerButtonAction.Info,
                    PlayerButtonAction.Lyrics,
                    PlayerButtonAction.Repeat,
                    PlayerButtonAction.Shuffle,
                ),
                landscapeUtilityButtons = listOf(
                    PlayerButtonAction.PlayPause,
                    PlayerButtonAction.PreviousTrack,
                    PlayerButtonAction.NextTrack,
                    PlayerButtonAction.TrackMenu,
                    PlayerButtonAction.AudioSettings,
                    PlayerButtonAction.ClosePlayer,
                ),
                portraitButtonRows = 2,
                landscapeButtonRows = 2,
                utilityButtonSize = PlayerButtonSize.Large,
                utilityButtonsScrollable = false,
                showGestureHints = false,
                startAtPlayerWhenRestored = false,
                enableLandscapeSplit = false,
                miniPlayerAppearance = MiniPlayerAppearanceSettings(
                    style = MiniPlayerAppearanceStyle.DeepBlackGlass,
                    transparency = 0.44f,
                    albumTintStrength = 0.21f,
                    borderStrength = 0.63f,
                    useHighContrastText = false,
                ),
                fullPlayerContent = FullPlayerContentSettings(
                    showLyrics = false,
                    showMoreFromArtist = true,
                    showDiscography = false,
                    showAboutArtist = true,
                    localArtistDiscoveryPolicy = LocalArtistDiscoveryPolicy.WifiOnlyAfterTap,
                    compactCards = false,
                ),
            ),
            background = BackgroundSettings(blurred = true, saturation = 1.7f, gradientColor = "#112233"),
            headsetBluetooth = HeadsetBluetoothSettings(wiredButtonMode = ButtonPressMode.SinglePress, bluetoothButtonMode = ButtonPressMode.LongPressNext),
            misc = MiscSettings(
                online = OnlineFeatureSettings(
                    pulseDataSaver = true,
                    wifiStreamingQuality = StreamingQualityPolicy.High,
                    cellularStreamingQuality = StreamingQualityPolicy.Balanced,
                    roamingStreamingQuality = StreamingQualityPolicy.LowData,
                    dataSaverStreamingQuality = StreamingQualityPolicy.DataSaver,
                    streamPreviewNetworkPolicy = StreamPreviewNetworkPolicy.WifiAndUnmetered,
                    allowMuxedStreamFallbackOnCellular = false,
                    allowMuxedStreamFallbackInDataSaver = true,
                ),
                networkStreamTimeoutSeconds = 45,
                networkStreamBufferMb = 2.5f,
            ),
        ).normalized()

        val json = SettingsProfileCodec.encode(original).toString()
        val restored = SettingsProfileCodec.decode(json, PulseSettingsState()).normalized()

        assertEquals(SettingsTheme.Dark, restored.lookAndFeel.settingsTheme)
        assertEquals(AnimationSpeedSetting.Fast, restored.lookAndFeel.animationSpeed)
        assertEquals(1.18f, restored.lookAndFeel.appFontScale, 0.001f)
        assertEquals(7, restored.lookAndFeel.settingsShortcutsCount)
        assertEquals(
            listOf(PlayerButtonAction.Info, PlayerButtonAction.Lyrics, PlayerButtonAction.Repeat, PlayerButtonAction.Shuffle),
            restored.playerUi.utilityButtons,
        )
        assertEquals(
            listOf(
                PlayerButtonAction.PlayPause,
                PlayerButtonAction.PreviousTrack,
                PlayerButtonAction.NextTrack,
                PlayerButtonAction.TrackMenu,
                PlayerButtonAction.AudioSettings,
                PlayerButtonAction.ClosePlayer,
            ),
            restored.playerUi.landscapeUtilityButtons,
        )
        assertEquals(2, restored.playerUi.portraitButtonRows)
        assertEquals(2, restored.playerUi.landscapeButtonRows)
        assertEquals(PlayerButtonSize.Large, restored.playerUi.utilityButtonSize)
        assertFalse(restored.playerUi.utilityButtonsScrollable)
        assertFalse(restored.playerUi.showGestureHints)
        assertFalse(restored.playerUi.startAtPlayerWhenRestored)
        assertFalse(restored.playerUi.enableLandscapeSplit)
        assertFalse(restored.playerUi.mirrorUtilityLayout)
        assertEquals(MiniPlayerAppearanceStyle.DeepBlackGlass, restored.playerUi.miniPlayerAppearance.style)
        assertEquals(0.44f, restored.playerUi.miniPlayerAppearance.transparency, 0.001f)
        assertEquals(0.21f, restored.playerUi.miniPlayerAppearance.albumTintStrength, 0.001f)
        assertEquals(0.63f, restored.playerUi.miniPlayerAppearance.borderStrength, 0.001f)
        assertFalse(restored.playerUi.miniPlayerAppearance.useHighContrastText)
        assertFalse(restored.playerUi.fullPlayerContent.showLyrics)
        assertTrue(restored.playerUi.fullPlayerContent.showMoreFromArtist)
        assertFalse(restored.playerUi.fullPlayerContent.showDiscography)
        assertTrue(restored.playerUi.fullPlayerContent.showAboutArtist)
        assertEquals(LocalArtistDiscoveryPolicy.WifiOnlyAfterTap, restored.playerUi.fullPlayerContent.localArtistDiscoveryPolicy)
        assertFalse(restored.playerUi.fullPlayerContent.compactCards)
        assertEquals("#112233", restored.background.gradientColor)
        assertEquals(1.7f, restored.background.saturation, 0.001f)
        assertEquals(ButtonPressMode.SinglePress, restored.headsetBluetooth.wiredButtonMode)
        assertEquals(ButtonPressMode.LongPressNext, restored.headsetBluetooth.bluetoothButtonMode)
        assertTrue(restored.misc.online.pulseDataSaver)
        assertEquals(StreamingQualityPolicy.Balanced, restored.misc.online.cellularStreamingQuality)
        assertEquals(StreamingQualityPolicy.LowData, restored.misc.online.roamingStreamingQuality)
        assertEquals(StreamingQualityPolicy.DataSaver, restored.misc.online.dataSaverStreamingQuality)
        assertEquals(StreamPreviewNetworkPolicy.WifiAndUnmetered, restored.misc.online.streamPreviewNetworkPolicy)
        assertFalse(restored.misc.online.allowMuxedStreamFallbackOnCellular)
        assertTrue(restored.misc.online.allowMuxedStreamFallbackInDataSaver)
        assertEquals(45, restored.misc.networkStreamTimeoutSeconds)
        assertEquals(2.5f, restored.misc.networkStreamBufferMb, 0.001f)
    }

    @Test
    fun userFacingSettingsExportsRedactPrivateDeviceFields() {
        val original = PulseSettingsState(
            library = LibrarySettings(
                musicFolders = listOf(
                    "/storage/emulated/0/Music/Private",
                    "C:\\Users\\Abiy\\Music",
                ),
            ),
            misc = MiscSettings(
                scrobbling = ScrobblingSettings(
                    lastFmEnabled = true,
                    sessionKeyPresent = true,
                ),
                userAgent = "PrivateUserAgent/Secret",
            ),
        )

        val portable = JSONObject(SettingsProfileCodec.encodePortableExport(original).toString())
        val portableRaw = portable.toString()

        assertEquals("portable_settings", portable.getString("profileType"))
        assertEquals(0, portable.getJSONObject("library").getJSONArray("musicFolders").length())
        assertEquals("", portable.getJSONObject("misc").getString("userAgent"))
        assertFalse(portable.getJSONObject("misc").getJSONObject("scrobbling").getBoolean("sessionKeyPresent"))
        assertFalse(portableRaw.contains("/storage/emulated"))
        assertFalse(portableRaw.contains("C:\\Users"))
        assertFalse(portableRaw.contains("PrivateUserAgent"))
    }

    @Test
    fun privateBackupExportAndUserImportStayPortableAndRedacted() {
        val original = PulseSettingsState(
            library = LibrarySettings(musicFolders = listOf("/storage/emulated/0/Music/Private")),
            misc = MiscSettings(userAgent = "PrivateUserAgent/Secret"),
        )

        val backup = JSONObject(SettingsProfileCodec.encodePrivateBackupExport(original).toString())
        val restored = SettingsProfileCodec.decodeUserImport(backup.toString(), PulseSettingsState()).normalized()

        assertEquals("private_backup", backup.getString("profileType"))
        assertTrue(backup.getJSONObject("backup").getBoolean("safePrivateBackup"))
        assertEquals(1, backup.getJSONObject("privacy").getInt("redactedLocalMusicFolderCount"))
        assertFalse(backup.toString().contains("/storage/emulated"))
        assertTrue(restored.library.musicFolders.isEmpty())
        assertEquals("", restored.misc.userAgent)
        assertTrue(restored.diagnostics.redactTrackPaths)
    }

    @Test
    fun thirdPartyLicenseRuntimeInventoryIsPresentAndCompleteEnoughForDeclaredRuntimeDeps() {
        val notices = PulseDeckThirdPartyLicenses.runtimeInventory
        val artifacts = notices.flatMap { it.artifacts }

        assertTrue(notices.size >= 10)
        assertTrue(artifacts.any { it.contains("androidx.compose.material3") })
        assertTrue(artifacts.any { it.contains("androidx.media3:media3-exoplayer") })
        assertTrue(artifacts.any { it.contains("newpipeextractor") })
        assertTrue(artifacts.any { it.contains("oboe") })
        assertTrue(artifacts.any { it.contains("play-services-tflite") })
        assertTrue(notices.all { it.name.isNotBlank() && it.license.isNotBlank() && it.url.startsWith("https://") })
    }

    @Test
    fun thirdPartyReleaseAuditFlagsCopyleftAndUnknownSources() {
        val auditNotices = PulseDeckThirdPartyLicenses.releaseAuditInventory

        assertTrue(auditNotices.size > PulseDeckThirdPartyLicenses.runtimeInventory.size)
        assertTrue(auditNotices.any { it.name.contains("Billboard JSON") && !it.allowedForRelease })
        assertTrue(auditNotices.any { it.license.contains("GPL", ignoreCase = true) && it.copyleftOrUnknownFlag })
        assertTrue(auditNotices.all { it.name.isNotBlank() && it.homepage.startsWith("https://") })
    }

    @Test
    fun phase32CatalogContainsFunctionalPlayerContentControlsAndHonestDisabledReasons() {
        val catalog = pulseSettingsCatalog()

        assertEquals("Network & Online", SettingScope.Misc.title)
        assertTrue(catalog.any { it.key == SettingKey.PlayerFullContentLyrics })
        assertTrue(catalog.any { it.key == SettingKey.PlayerFullContentArtist })
        assertTrue(catalog.any { it.key == SettingKey.PlayerFullContentDiscography })
        assertTrue(catalog.any { it.key == SettingKey.PlayerFullContentAbout })
        assertTrue(catalog.any { it.key == SettingKey.PlayerFullContentLocalDiscovery })
        assertTrue(catalog.any { it.key == SettingKey.PlayerFullContentCompact })

        val libraryFolders = catalog.first { it.key == SettingKey.LibraryFolders }
        val lockAlbumArt = catalog.first { it.key == SettingKey.LockAlbumArt }
        assertTrue(libraryFolders.integrationStatus is SettingsIntegrationStatus.NotImplementedYet)
        assertTrue(libraryFolders.subtitle.orEmpty().contains("Advanced library scan rules"))
        assertTrue(lockAlbumArt.integrationStatus is SettingsIntegrationStatus.NotImplementedYet)
        assertTrue(lockAlbumArt.subtitle.orEmpty().contains("Android media controls still work"))
    }

    @Test
    fun playerUiSettingsNormalizeUtilityButtons() {
        val normalized = PlayerUiSettings(
            utilityButtons = listOf(
                PlayerButtonAction.Info,
                PlayerButtonAction.Info,
                PlayerButtonAction.Lyrics,
                PlayerButtonAction.Bookmark,
                PlayerButtonAction.Like,
                PlayerButtonAction.Repeat,
                PlayerButtonAction.Shuffle,
            ),
            landscapeUtilityButtons = listOf(
                PlayerButtonAction.PlayPause,
                PlayerButtonAction.PlayPause,
                PlayerButtonAction.NextTrack,
                PlayerButtonAction.TrackMenu,
                PlayerButtonAction.Search,
                PlayerButtonAction.Library,
                PlayerButtonAction.AudioSettings,
            ),
            portraitButtonRows = 9,
            landscapeButtonRows = 0,
        ).normalized()

        assertEquals(
            listOf(
                PlayerButtonAction.Info,
                PlayerButtonAction.Lyrics,
                PlayerButtonAction.Bookmark,
                PlayerButtonAction.Like,
                PlayerButtonAction.Repeat,
                PlayerButtonAction.Shuffle,
            ),
            normalized.utilityButtons,
        )
        assertEquals(
            listOf(
                PlayerButtonAction.PlayPause,
                PlayerButtonAction.NextTrack,
                PlayerButtonAction.TrackMenu,
                PlayerButtonAction.Search,
                PlayerButtonAction.Library,
                PlayerButtonAction.AudioSettings,
            ),
            normalized.landscapeUtilityButtons,
        )
        assertEquals(2, normalized.portraitButtonRows)
        assertEquals(1, normalized.landscapeButtonRows)
        assertEquals(
            listOf(
                PlayerButtonAction.Visualization,
                PlayerButtonAction.SleepTimer,
                PlayerButtonAction.Repeat,
                PlayerButtonAction.Shuffle,
            ),
            PlayerUiSettings(utilityButtons = emptyList()).normalized().utilityButtons,
        )
    }

    @Test
    fun miniPlayerAppearanceSettingsNormalizeVisualRanges() {
        val normalized = PlayerUiSettings(
            miniPlayerAppearance = MiniPlayerAppearanceSettings(
                style = MiniPlayerAppearanceStyle.SolidBlack,
                transparency = 8f,
                albumTintStrength = -1f,
                borderStrength = 2f,
                useHighContrastText = false,
            ),
        ).normalized().miniPlayerAppearance

        assertEquals(MiniPlayerAppearanceStyle.SolidBlack, normalized.style)
        assertEquals(1f, normalized.transparency, 0.001f)
        assertEquals(0f, normalized.albumTintStrength, 0.001f)
        assertEquals(1f, normalized.borderStrength, 0.001f)
        assertFalse(normalized.useHighContrastText)
    }

    @Test
    fun playerUiSettingsMirrorUtilityLayoutCopiesPortraitToLandscape() {
        val normalized = PlayerUiSettings(
            utilityButtons = listOf(
                PlayerButtonAction.PlayPause,
                PlayerButtonAction.NextTrack,
                PlayerButtonAction.Lyrics,
            ),
            landscapeUtilityButtons = listOf(
                PlayerButtonAction.Info,
                PlayerButtonAction.Shuffle,
            ),
            portraitButtonRows = 2,
            landscapeButtonRows = 1,
            mirrorUtilityLayout = true,
        ).normalized()

        assertEquals(
            listOf(
                PlayerButtonAction.PlayPause,
                PlayerButtonAction.NextTrack,
                PlayerButtonAction.Lyrics,
            ),
            normalized.landscapeUtilityButtons,
        )
        assertEquals(2, normalized.landscapeButtonRows)
    }

    @Test
    fun playerUiSettingsPreserveNoneUtilitySlots() {
        val normalized = PlayerUiSettings(
            utilityButtons = listOf(
                PlayerButtonAction.None,
                PlayerButtonAction.None,
                PlayerButtonAction.None,
                PlayerButtonAction.None,
                PlayerButtonAction.None,
                PlayerButtonAction.None,
            ),
            landscapeUtilityButtons = listOf(
                PlayerButtonAction.None,
                PlayerButtonAction.Repeat,
                PlayerButtonAction.Repeat,
                PlayerButtonAction.None,
                PlayerButtonAction.Shuffle,
                PlayerButtonAction.None,
                PlayerButtonAction.Info,
            ),
        ).normalized()

        assertEquals(
            listOf(
                PlayerButtonAction.None,
                PlayerButtonAction.None,
                PlayerButtonAction.None,
                PlayerButtonAction.None,
                PlayerButtonAction.None,
                PlayerButtonAction.None,
            ),
            normalized.utilityButtons,
        )
        assertEquals(
            listOf(
                PlayerButtonAction.None,
                PlayerButtonAction.Repeat,
                PlayerButtonAction.None,
                PlayerButtonAction.Shuffle,
                PlayerButtonAction.None,
                PlayerButtonAction.Info,
            ),
            normalized.landscapeUtilityButtons,
        )
    }

    @Test
    fun playbackModeTapCyclesMatchPowerampStyleFiniteStates() {
        val shuffleCycle = generateSequence(ShuffleMode.Off, ::nextShuffleMode)
            .drop(1)
            .take(4)
            .toList()
        val repeatCycle = generateSequence(PlaybackRepeatMode.SongOnce, ::nextRepeatMode)
            .drop(1)
            .take(6)
            .toList()

        assertEquals(
            listOf(
                ShuffleMode.ShuffleAll,
                ShuffleMode.ShuffleSongsInCategory,
                ShuffleMode.ShuffleCategories,
                ShuffleMode.Off,
            ),
            shuffleCycle,
        )
        assertEquals(
            listOf(
                PlaybackRepeatMode.CategoryOnce,
                PlaybackRepeatMode.AllOnce,
                PlaybackRepeatMode.RepeatSong,
                PlaybackRepeatMode.RepeatCategory,
                PlaybackRepeatMode.RepeatAll,
                PlaybackRepeatMode.SongOnce,
            ),
            repeatCycle,
        )
    }

    @Test
    fun malformedSettingsImportIsRejected() {
        assertTrue(runCatching { SettingsProfileCodec.decode("""{"schemaVersion":99}""") }.isFailure)
        assertTrue(runCatching { SettingsProfileCodec.decode("not-json") }.isFailure)
    }

    @Test
    fun schemaOneSettingsImportUpgradesToCurrentSchemaAndNativeDefaults() {
        val restored = SettingsProfileCodec.decode(
            """
            {
              "schemaVersion": 1,
              "audio": {
                "native": {
                  "enabled": true,
                  "media3DspEnabled": true,
                  "masterGain": 1.25,
                  "sourceFrequencyHz": 880.0,
                  "sourceGain": 0.08
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(5, restored.schemaVersion)
        assertTrue(restored.audio.native.enabled)
        assertTrue(restored.audio.native.media3DspEnabled)
        assertEquals(1.25f, restored.audio.native.masterGain, 0.001f)
        assertEquals(880f, restored.audio.native.sourceFrequencyHz, 0.001f)
    }

    @Test
    fun outputRouteResolverFallsBackWhenCapabilitiesAreMissing() {
        val runtime = PlaybackRuntimeSettings(
            routeAssignments = mapOf(OutputDeviceType.Speaker to OutputPluginType.HiRes),
            deviceCapabilities = DeviceCapabilities(supportsHiRes = false),
        )

        val status = runtime.resolveRoute(OutputDeviceType.Speaker)

        assertFalse(status.available)
        assertEquals(OutputPluginType.HiRes, status.requestedPlugin)
        assertEquals(OutputPluginType.Media3AudioTrack, status.actualPlugin)
    }

    @Test
    fun usbDacHiResRouteStaysGatedUntilUsbCapabilityIsVerified() {
        val runtime = PlaybackRuntimeSettings(
            routeAssignments = mapOf(OutputDeviceType.UsbDac to OutputPluginType.HiRes),
            deviceCapabilities = DeviceCapabilities(supportsHiRes = true, supportsUsbDac = false),
        )

        val status = runtime.resolveRoute(OutputDeviceType.UsbDac)

        assertFalse(status.available)
        assertEquals(OutputPluginType.HiRes, status.requestedPlugin)
        assertEquals(OutputPluginType.Media3AudioTrack, status.actualPlugin)
        assertTrue(status.reason.orEmpty().contains("USB DAC"))
    }

    @Test
    fun nativeLowLatencyRouteResolvesToAaudioWhenCapabilityExists() {
        val runtime = PulseSettingsState(
            audio = AudioEngineState(output = OutputSettings(mode = OutputMode.NativeLowLatency)),
        ).toPlaybackRuntimeSettings(DeviceCapabilities(supportsAaudio = true))

        val status = runtime.resolveRoute(OutputDeviceType.Speaker)

        assertTrue(status.available)
        assertEquals(OutputPluginType.AAudioNative, status.actualPlugin)
    }

    @Test
    fun lastFmScrobbleThresholdMatchesProtocolRule() {
        assertFalse(shouldSubmitLastFmScrobble(durationMs = 29_000, playedMs = 29_000))
        assertFalse(shouldSubmitLastFmScrobble(durationMs = 180_000, playedMs = 30_000))
        assertTrue(shouldSubmitLastFmScrobble(durationMs = 180_000, playedMs = 90_000))
        assertTrue(shouldSubmitLastFmScrobble(durationMs = 900_000, playedMs = 240_000))
    }

    @Test
    fun lastFmRemainsGatedForV1() {
        val normalized = PulseSettingsState(
            misc = MiscSettings(scrobbling = ScrobblingSettings(lastFmEnabled = true, sessionKeyPresent = true)),
        ).normalized()

        assertFalse(normalized.misc.scrobbling.lastFmEnabled)
        assertFalse(normalized.misc.scrobbling.sessionKeyPresent)
    }

    @Test
    fun mediaButtonSettingsMapToStableCustomCommandIds() {
        val commands = mediaSessionCustomActions(
            listOf(MediaAction.Shuffle, MediaAction.Close, MediaAction.PreviousCategory, MediaAction.NextCategory, MediaAction.None),
        )

        assertEquals(
            listOf(
                "pulsedeck.action.SHUFFLE",
                "pulsedeck.action.CLOSE",
                "pulsedeck.action.PREVIOUS_CATEGORY",
                "pulsedeck.action.NEXT_CATEGORY",
            ),
            commands,
        )
        assertEquals(MediaAction.Shuffle, mediaActionForCustomCommand("pulsedeck.action.SHUFFLE"))
        assertEquals(MediaAction.PreviousCategory, mediaActionForCustomCommand("pulsedeck.action.PREVIOUS_CATEGORY"))
        assertEquals(MediaAction.NextCategory, mediaActionForCustomCommand("pulsedeck.action.NEXT_CATEGORY"))
        assertEquals(null, mediaActionForCustomCommand("pulsedeck.action.UNKNOWN"))
    }

    @Test
    fun notificationMediaButtonsDefaultToPreviousAndNext() {
        val buttons = PulseSettingsState().misc.androidAuto.customButtons

        assertEquals(MediaAction.Shuffle, buttons.button1)
        assertEquals(MediaAction.Close, buttons.button2)
        assertEquals(MediaAction.Repeat, buttons.button3)
        assertEquals(MediaAction.PreviousCategory, buttons.button4)
        assertEquals(MediaAction.NextCategory, buttons.button5)
    }

    @Test
    fun legacyNotificationSeekButtonsUpgradeToPreviousAndNext() {
        val restored = SettingsProfileCodec.decode(
            """
            {
              "schemaVersion": 4,
              "misc": {
                "androidAuto": {
                  "customButtons": {
                    "button1": "Shuffle",
                    "button2": "Close",
                    "button3": "Repeat",
                    "button4": "SeekBack10",
                    "button5": "SeekForward10",
                    "button6": "None"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(5, restored.schemaVersion)
        assertEquals(MediaAction.PreviousCategory, restored.misc.androidAuto.customButtons.button4)
        assertEquals(MediaAction.NextCategory, restored.misc.androidAuto.customButtons.button5)
    }

    @Test
    fun bluetoothCommandDebouncerDropsDuplicateInsideWindow() {
        val debouncer = BluetoothCommandDebouncer()

        assertTrue(debouncer.shouldAccept("KEYCODE_MEDIA_PLAY_PAUSE", ignoreWindowSeconds = 2, nowMillis = 1_000L))
        assertFalse(debouncer.shouldAccept("KEYCODE_MEDIA_PLAY_PAUSE", ignoreWindowSeconds = 2, nowMillis = 1_500L))
        assertTrue(debouncer.shouldAccept("KEYCODE_MEDIA_NEXT", ignoreWindowSeconds = 2, nowMillis = 1_600L))
        assertTrue(debouncer.shouldAccept("KEYCODE_MEDIA_PLAY_PAUSE", ignoreWindowSeconds = 2, nowMillis = 4_000L))
    }

    @Test
    fun outputDiagnosticsSnapshotIncludesAllRoutes() {
        val snapshot = PlaybackRuntimeSettings(
            routeAssignments = mapOf(OutputDeviceType.Chromecast to OutputPluginType.Chromecast),
            deviceCapabilities = DeviceCapabilities(supportsCast = false),
        ).outputDiagnosticsSnapshot(nowMillis = 42L)

        assertEquals(OutputDeviceType.entries.size, snapshot.routeStatuses.size)
        assertEquals(42L, snapshot.updatedAtMillis)
        assertTrue(snapshot.routeStatuses.any { it.route == OutputDeviceType.Chromecast && !it.available })
    }
}
