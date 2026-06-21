@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsedeck.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioDeviceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pulsedeck.app.audio.NativeAudioEngineController
import com.pulsedeck.app.audio.NativeDecodedPcmFormat
import com.pulsedeck.app.audio.NativeDspAudioProcessor
import com.pulsedeck.app.audio.TruePeakAudioProcessor
import com.pulsedeck.app.audio.nativeAudioInitializationNeeded
import com.pulsedeck.app.settings.runtime.AudioOutputRuntimeInspection
import com.pulsedeck.app.settings.runtime.AudioTrackDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.BluetoothCommandDebouncer
import com.pulsedeck.app.settings.runtime.CommandHapticFeedback
import com.pulsedeck.app.settings.runtime.CommandCueSource
import com.pulsedeck.app.settings.runtime.CommandSoundCuePlayer
import com.pulsedeck.app.settings.runtime.DecoderFormatDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.HardwareButtonKind
import com.pulsedeck.app.settings.runtime.HardwarePlaybackAction
import com.pulsedeck.app.settings.runtime.OutputDeviceDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.PlaybackRuntimeSettings
import com.pulsedeck.app.settings.runtime.ProOutputRuntimeSnapshot
import com.pulsedeck.app.settings.runtime.PulseBluetoothCommandLogStore
import com.pulsedeck.app.settings.runtime.PulseOutputDiagnosticsStore
import com.pulsedeck.app.settings.runtime.PulsePlaybackRuntimeStore
import com.pulsedeck.app.settings.runtime.TruePeakDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.audioEncodingLabel
import com.pulsedeck.app.settings.runtime.inspectAudioOutputRuntime
import com.pulsedeck.app.settings.runtime.actionForAmbiguousButton
import com.pulsedeck.app.settings.runtime.actionForDedicatedButton
import com.pulsedeck.app.settings.runtime.asActionList
import com.pulsedeck.app.settings.runtime.commandNameForHardwareAction
import com.pulsedeck.app.settings.runtime.commandCueVolumePercent
import com.pulsedeck.app.settings.runtime.commandCuesForKeyCommand
import com.pulsedeck.app.settings.runtime.commandCuesForMediaAction
import com.pulsedeck.app.settings.runtime.mediaActionForCustomCommand
import com.pulsedeck.app.settings.runtime.mediaSessionCustomActionId
import com.pulsedeck.app.settings.runtime.mediaSessionCustomActions
import com.pulsedeck.app.settings.runtime.outputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.relativeSeekTarget
import com.pulsedeck.app.settings.runtime.rejectedCommandCue
import com.pulsedeck.app.settings.runtime.shouldConsumeStrictNoop
import com.pulsedeck.app.settings.runtime.toSampleRateHzOrNull
import com.pulsedeck.app.settings.model.MediaAction
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt

private const val PERFORMANCE_TAG = "PulseDeckPerf"

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val platformEffects = PlatformAudioEffects()
    private lateinit var nativeController: NativeAudioEngineController
    private lateinit var nativeDspProcessor: NativeDspAudioProcessor
    private lateinit var truePeakProcessor: TruePeakAudioProcessor
    private val mediaButtonDebouncer = BluetoothCommandDebouncer()
    private val commandCuePlayer = CommandSoundCuePlayer()
    private val commandHaptics by lazy { CommandHapticFeedback(this) }
    private val serviceHandler = Handler(Looper.getMainLooper())
    private var lastHardwareCueAtMs: Long = 0L
    private val hardwareCueDedupeWindowMs: Long = 350L
    private var ambiguousClickCount = 0
    private var ambiguousLongPressHandled = false
    private var pendingAmbiguousClick: Runnable? = null
    private val ambiguousClickWindowMs = 300L
    private var runtimeSettings = PulsePlaybackRuntimeStore.runtime.value
    private var nativeInitJob: Job? = null
    private var firstPlaybackCommandLogged = false
    private var serviceCreatedAtMillis = 0L
    private var latestDecoderName: String? = null
    private var latestDecoderFormat = DecoderFormatDiagnosticsSnapshot()
    private var latestOutputDevice = OutputDeviceDiagnosticsSnapshot()
    private var latestAudioTrack = AudioTrackDiagnosticsSnapshot()
    private var latestProOutput = ProOutputRuntimeSnapshot()
    @Volatile
    private var latestProOutputMedia = ProOutputCurrentMedia()
    private var latestTruePeak = TruePeakDiagnosticsSnapshot()
    private var preferredAudioDevice: AudioDeviceInfo? = null

    override fun onCreate() {
        serviceCreatedAtMillis = SystemClock.uptimeMillis()
        tracePlaybackSection("playback_service_onCreate") {
            super.onCreate()
            val attributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            tracePlaybackSection("native_controller_create") {
                nativeController = NativeAudioEngineController(this)
                nativeDspProcessor = NativeDspAudioProcessor(nativeController)
                truePeakProcessor = TruePeakAudioProcessor { snapshot ->
                    serviceHandler.post {
                        latestTruePeak = snapshot
                        updateOutputDiagnostics()
                    }
                }
            }
            val mediaSourceFactory = tracePlaybackSection("media_source_factory_create") {
                DefaultMediaSourceFactory(PulseCachedStreamingDataSourceFactory(this))
            }
            player = tracePlaybackSection("exoplayer_create") {
                ExoPlayer.Builder(
                    this,
                    PulseNativeRenderersFactory(
                        context = this,
                        nativeProcessor = nativeDspProcessor,
                        truePeakProcessor = truePeakProcessor,
                        outputSettings = { runtimeSettings.output },
                        currentMedia = { currentProOutputMedia() },
                        onProOutputDiagnostics = { snapshot ->
                            serviceHandler.post {
                                latestProOutput = snapshot
                                updateOutputDiagnostics()
                            }
                        },
                        onAudioTrackDiagnostics = { snapshot ->
                            serviceHandler.post {
                                latestAudioTrack = snapshot
                                updateOutputDiagnostics()
                            }
                        },
                    ),
                )
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setLoadControl(pulseLoadControl(runtimeSettings))
                    .build()
                    .apply {
                        setAudioAttributes(attributes, true)
                        setHandleAudioBecomingNoisy(true)
                        addListener(object : Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) {
                                applyAudioState(PulseAudioEngineStore.state.value)
                            }

                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                latestProOutputMedia = mediaItem.proOutputCurrentMedia()
                                proOutputDebugLog("PulseDeckProOutput", "track_change scope=${latestProOutputMedia.scope.wireValue} reason=$reason")
                                clearDecoderDiagnostics()
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_IDLE) clearDecoderDiagnostics()
                            }

                            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                                if (playWhenReady) logFirstPlaybackCommand("play_when_ready:$reason")
                            }
                        })
                        addAnalyticsListener(object : AnalyticsListener {
                            override fun onAudioDecoderInitialized(
                                eventTime: AnalyticsListener.EventTime,
                                decoderName: String,
                                initializedTimestampMs: Long,
                                initializationDurationMs: Long,
                            ) {
                                latestDecoderName = decoderName.takeIf { it.isNotBlank() }
                                latestDecoderFormat = latestDecoderFormat.withDecoderName(latestDecoderName)
                                updateOutputDiagnostics()
                            }

                            override fun onAudioInputFormatChanged(
                                eventTime: AnalyticsListener.EventTime,
                                format: Format,
                                decoderReuseEvaluation: DecoderReuseEvaluation?,
                            ) {
                                latestDecoderFormat = format.toDecoderFormatDiagnostics(
                                    decoderName = latestDecoderName,
                                    decodedPcmFormat = currentDecodedPcmFormat(),
                                    nowMillis = System.currentTimeMillis(),
                                )
                                proOutputDebugLog(
                                    "PulseDeckHiRes",
                                    "decode_format mime=${format.sampleMimeType ?: "unknown"} sample_rate=${format.sampleRate.takeIf { it > 0 } ?: "unknown"} channels=${format.channelCount.takeIf { it > 0 } ?: "unknown"} pcm=${format.pcmEncoding.takeIf { it > 0 } ?: "unknown"}",
                                )
                                updateOutputDiagnostics()
                            }
                        })
                    }
            }
            mediaSession = tracePlaybackSection("media_session_create") {
                MediaSession.Builder(this, player)
                    .setSessionActivity(pulseDeckLaunchPendingIntent())
                    .setCallback(pulseMediaSessionCallback())
                    .build()
            }
            serviceScope.launch {
                PulseAudioEngineStore.state.collectLatest { applyAudioState(it) }
            }
            serviceScope.launch {
                PulsePlaybackRuntimeStore.runtime.collectLatest {
                    runtimeSettings = it
                    applyRuntimeSettings(it)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        nativeInitJob?.cancel()
        if (::player.isInitialized) player.release()
        platformEffects.release()
        commandCuePlayer.close()
        serviceHandler.removeCallbacksAndMessages(null)
        if (::nativeController.isInitialized) nativeController.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun applyAudioState(state: AudioEngineState) {
        if (!::player.isInitialized) return
        val effectiveState = effectiveAudioState(state)
        if (::nativeController.isInitialized) nativeController.setAudioState(effectiveState)
        val policy = currentAudioPolicy(effectiveState)
        platformEffects.apply(player.audioSessionId, effectiveState, policy)
        val speed = if (policy.processingActive) (effectiveState.tempoPitch.speed * effectiveState.tempoPitch.tempo).coerceIn(0.5f, 2f) else 1f
        val pitch = if (policy.processingActive) 2f.pow(effectiveState.tempoPitch.pitchSemitones / 12f).coerceIn(0.5f, 2f) else 1f
        runCatching { player.playbackParameters = PlaybackParameters(speed, pitch) }
        updateOutputDiagnostics(policy)
        maybeInitializeNativeAudio(effectiveState, "audio_state")
    }

    private fun maybeInitializeNativeAudio(state: AudioEngineState, reason: String) {
        if (!::nativeController.isInitialized) return
        if (!nativeAudioInitializationNeeded(state)) return
        if (nativeController.isAvailable) return
        if (nativeController.initializationState == com.pulsedeck.app.audio.NativeAudioInitializationState.Failed) return
        if (nativeInitJob?.isActive == true) return
        nativeInitJob = serviceScope.launch(Dispatchers.Default) {
            tracePlaybackSection("native_audio_init") {
                nativeController.initializeForState(state, reason)
            }
            withContext(Dispatchers.Main.immediate) {
                if (::player.isInitialized) applyAudioState(PulseAudioEngineStore.state.value)
            }
        }
    }

    private fun logFirstPlaybackCommand(source: String) {
        if (firstPlaybackCommandLogged) return
        firstPlaybackCommandLogged = true
        perfLog(
            "first_playback_command source=$source service_age_ms=${SystemClock.uptimeMillis() - serviceCreatedAtMillis} native_state=${if (::nativeController.isInitialized) nativeController.initializationState else "unavailable"}",
        )
    }

    private fun applyRuntimeSettings(settings: PlaybackRuntimeSettings) {
        if (!::player.isInitialized) return
        val outputInspection = inspectAudioOutputRuntime(this, settings.output)
        latestOutputDevice = outputInspection.deviceDiagnostics
        runtimeSettings = settings.copy(deviceCapabilities = outputInspection.capabilities)
        applyPreferredOutputDevice(outputInspection)
        proOutputDebugLog(
            "PulseDeckAudioRoute",
            "setting_change mode=${settings.output.mode.name} profile=${settings.output.deviceProfile.name} requested_route=${outputInspection.deviceDiagnostics.requestedRoute.name} route_verified=${outputInspection.deviceDiagnostics.routeVerified} usb=${outputInspection.deviceDiagnostics.usbDacDetected}",
        )
        player.setAudioAttributes(mediaAudioAttributes(), settings.audioFocus.requestOnPlay)
        player.setHandleAudioBecomingNoisy(settings.headsetBluetooth.pauseOnHeadsetDisconnect)
        mediaSession?.setMediaButtonPreferences(commandButtonsFor(settings.androidAuto.customButtons.asActionList()))
        updateOutputDiagnostics()
    }

    private fun applyPreferredOutputDevice(inspection: AudioOutputRuntimeInspection) {
        if (preferredAudioDevice == inspection.preferredDevice) return
        preferredAudioDevice = inspection.preferredDevice
        runCatching { player.setPreferredAudioDevice(inspection.preferredDevice) }
    }

    private fun effectiveAudioState(state: AudioEngineState = PulseAudioEngineStore.state.value): AudioEngineState =
        state.withSourceLoudness(currentSourceLoudness())

    private fun currentSourceLoudness(): LoudnessMetadata =
        if (::player.isInitialized) {
            loudnessMetadataFromBundle(player.currentMediaItem?.mediaMetadata?.extras)
        } else {
            LoudnessMetadata()
        }

    private fun currentAudioPolicy(state: AudioEngineState = effectiveAudioState()): EffectiveAudioProcessingPolicy =
        if (::nativeController.isInitialized) {
            nativeController.effectivePolicy()
        } else {
            state.effectiveProcessingPolicy(nativeAvailable = false)
        }

    private fun updateOutputDiagnostics(policy: EffectiveAudioProcessingPolicy = currentAudioPolicy()) {
        PulseOutputDiagnosticsStore.update(
            runtimeSettings.outputDiagnosticsSnapshot(
                audioPolicy = policy,
                outputDevice = latestOutputDevice,
                audioTrack = latestAudioTrack,
                proOutput = latestProOutput,
                truePeak = latestTruePeak,
            ).copy(
                decoderFormat = currentDecoderDiagnostics(),
            ),
        )
    }

    private fun clearDecoderDiagnostics() {
        latestDecoderName = null
        latestDecoderFormat = DecoderFormatDiagnosticsSnapshot()
        latestProOutput = latestProOutput.copy(status = com.pulsedeck.app.settings.runtime.ProOutputRuntimeStatus.Idle, updatedAtMillis = System.currentTimeMillis())
        updateOutputDiagnostics()
    }

    private fun currentProOutputMedia(): ProOutputCurrentMedia =
        latestProOutputMedia

    private fun currentDecodedPcmFormat(): NativeDecodedPcmFormat =
        if (::nativeController.isInitialized) nativeController.decodedPcmFormat else NativeDecodedPcmFormat()

    private fun currentDecoderDiagnostics(): DecoderFormatDiagnosticsSnapshot {
        val decoded = currentDecodedPcmFormat()
        val snapshot = latestDecoderFormat.withDecodedPcm(decoded)
        return if (snapshot.hasDecoderData()) snapshot else DecoderFormatDiagnosticsSnapshot()
    }

    private fun pulseMediaSessionCallback(): MediaSession.Callback =
        object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val customCommands = sessionCommandsFor(runtimeSettings.androidAuto.customButtons.asActionList())
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(customCommands)
                    .setMediaButtonPreferences(commandButtonsFor(runtimeSettings.androidAuto.customButtons.asActionList()))
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                val action = mediaActionForCustomCommand(customCommand.customAction)
                    ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                val cueDurationMs = playCommandFeedback(action, CommandCueSource.MediaSessionButton)
                handleMediaAction(action, cueDurationMs)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            override fun onPlayerInteractionFinished(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                playerCommands: Player.Commands,
            ) {
                if (!runtimeSettings.headsetBluetooth.beep || !runtimeSettings.headsetBluetooth.beepMore) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastHardwareCueAtMs < hardwareCueDedupeWindowMs) return
                val commandName = when {
                    playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT) -> "NEXT"
                    playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS) -> "PREVIOUS"
                    playerCommands.contains(Player.COMMAND_SEEK_FORWARD) -> "FORWARD"
                    playerCommands.contains(Player.COMMAND_SEEK_BACK) -> "REWIND"
                    playerCommands.contains(Player.COMMAND_PLAY_PAUSE) -> "PLAY_PAUSE"
                    playerCommands.contains(Player.COMMAND_STOP) -> "STOP"
                    else -> null
                } ?: return
                val settings = runtimeSettings.headsetBluetooth
                commandCuePlayer.play(
                    commandCuesForKeyCommand(
                        command = commandName,
                        settings = settings,
                        source = CommandCueSource.MediaSessionButton,
                    ),
                    settings.commandCueVolumePercent(),
                )
                commandHaptics.play(settings, CommandCueSource.MediaSessionButton)
            }

            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent,
            ): Boolean {
                if (!runtimeSettings.headsetBluetooth.respondToButtons) return true
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                return handleHardwareMediaButton(keyEvent)
            }
        }

    private fun playCommandFeedback(action: MediaAction, source: CommandCueSource): Long {
        val settings = runtimeSettings.headsetBluetooth
        val cueDurationMs = commandCuePlayer.play(
            commandCuesForMediaAction(action, settings, source),
            settings.commandCueVolumePercent(),
        )
        commandHaptics.play(settings, source)
        return cueDurationMs
    }

    private fun handleHardwareMediaButton(keyEvent: KeyEvent): Boolean {
        val kind = hardwareButtonKind(keyEvent.keyCode)
        if (kind == HardwareButtonKind.Unknown) return false
        val command = KeyEvent.keyCodeToString(keyEvent.keyCode)
        val mode = if (keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            runtimeSettings.headsetBluetooth.wiredButtonMode
        } else {
            runtimeSettings.headsetBluetooth.bluetoothButtonMode
        }
        return if (kind == HardwareButtonKind.AmbiguousPlayPause) {
            handleAmbiguousMediaButton(keyEvent, command, mode)
        } else {
            handleDedicatedMediaButton(keyEvent, command, kind)
        }
    }

    private fun handleDedicatedMediaButton(
        keyEvent: KeyEvent,
        command: String,
        kind: HardwareButtonKind,
    ): Boolean {
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
        val action = actionForDedicatedButton(kind) ?: return false
        val accepted = mediaButtonDebouncer.shouldAccept(
            command = command,
            ignoreWindowSeconds = runtimeSettings.headsetBluetooth.ignoreBluetoothCommandsSeconds,
        )
        if (!accepted) {
            playRejectedHardwareFeedback(command)
            return true
        }
        if (shouldConsumeStrictNoop(action, player.isPlaying, runtimeSettings.headsetBluetooth.strictResumePauseStop)) {
            playRejectedHardwareFeedback(command, "ignored-strict")
            return true
        }
        PulseBluetoothCommandLogStore.record("media-button:$command")
        playHardwareFeedback(action)
        return false
    }

    private fun handleAmbiguousMediaButton(
        keyEvent: KeyEvent,
        command: String,
        mode: com.pulsedeck.app.settings.model.ButtonPressMode,
    ): Boolean {
        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (
                    mode == com.pulsedeck.app.settings.model.ButtonPressMode.LongPressNext &&
                    (keyEvent.repeatCount > 0 || keyEvent.flags and KeyEvent.FLAG_LONG_PRESS != 0)
                ) {
                    if (!ambiguousLongPressHandled) {
                        ambiguousLongPressHandled = true
                        cancelPendingAmbiguousClick()
                        val action = actionForAmbiguousButton(mode, completedClickCount = 1, longPress = true)
                        executeManualHardwareAction(action, "$command long")
                    }
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (ambiguousLongPressHandled) {
                    ambiguousLongPressHandled = false
                    return true
                }
                val checkDuplicate = mode != com.pulsedeck.app.settings.model.ButtonPressMode.DoubleTripleNextPrev ||
                    ambiguousClickCount == 0
                val accepted = !checkDuplicate || mediaButtonDebouncer.shouldAccept(
                    command = command,
                    ignoreWindowSeconds = runtimeSettings.headsetBluetooth.ignoreBluetoothCommandsSeconds,
                )
                if (!accepted) {
                    playRejectedHardwareFeedback(command)
                    return true
                }
                when (mode) {
                    com.pulsedeck.app.settings.model.ButtonPressMode.SinglePress,
                    com.pulsedeck.app.settings.model.ButtonPressMode.LongPressNext -> {
                        val action = actionForAmbiguousButton(mode, completedClickCount = 1, longPress = false)
                        executeManualHardwareAction(action, command)
                    }
                    com.pulsedeck.app.settings.model.ButtonPressMode.DoubleTripleNextPrev -> {
                        ambiguousClickCount += 1
                        cancelPendingAmbiguousClick()
                        val pending = Runnable {
                            val clicks = ambiguousClickCount
                            ambiguousClickCount = 0
                            val action = actionForAmbiguousButton(mode, completedClickCount = clicks, longPress = false)
                            executeManualHardwareAction(action, "$command x$clicks")
                        }
                        pendingAmbiguousClick = pending
                        serviceHandler.postDelayed(pending, ambiguousClickWindowMs)
                    }
                }
                return true
            }
            else -> return true
        }
    }

    private fun executeManualHardwareAction(action: HardwarePlaybackAction, command: String) {
        if (shouldConsumeStrictNoop(action, player.isPlaying, runtimeSettings.headsetBluetooth.strictResumePauseStop)) {
            playRejectedHardwareFeedback(command, "ignored-strict")
            return
        }
        PulseBluetoothCommandLogStore.record("media-button:$command")
        playHardwareFeedback(action)
        when (action) {
            HardwarePlaybackAction.Play -> player.play()
            HardwarePlaybackAction.Pause -> player.pause()
            HardwarePlaybackAction.PlayPause -> if (player.isPlaying) player.pause() else player.play()
            HardwarePlaybackAction.Stop -> player.pause()
            HardwarePlaybackAction.Next -> player.seekToNextMediaItem()
            HardwarePlaybackAction.Previous -> player.seekToPreviousMediaItem()
            HardwarePlaybackAction.SeekForward10 -> seekRelative(10_000L)
            HardwarePlaybackAction.SeekBack10 -> seekRelative(-10_000L)
        }
    }

    private fun playHardwareFeedback(action: HardwarePlaybackAction) {
        val settings = runtimeSettings.headsetBluetooth
        commandCuePlayer.play(
            commandCuesForKeyCommand(commandNameForHardwareAction(action), settings, CommandCueSource.HeadsetButton),
            settings.commandCueVolumePercent(),
        )
        commandHaptics.play(settings, CommandCueSource.HeadsetButton)
        lastHardwareCueAtMs = SystemClock.elapsedRealtime()
    }

    private fun playRejectedHardwareFeedback(command: String, reason: String = "rejected") {
        val settings = runtimeSettings.headsetBluetooth
        PulseBluetoothCommandLogStore.record("media-button:$command $reason")
        commandCuePlayer.play(rejectedCommandCue(settings), settings.commandCueVolumePercent())
        commandHaptics.play(settings, CommandCueSource.RejectedCommand, rejected = true)
    }

    private fun cancelPendingAmbiguousClick() {
        pendingAmbiguousClick?.let(serviceHandler::removeCallbacks)
        pendingAmbiguousClick = null
    }

    private fun hardwareButtonKind(keyCode: Int): HardwareButtonKind =
        when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> HardwareButtonKind.AmbiguousPlayPause
            KeyEvent.KEYCODE_MEDIA_PLAY -> HardwareButtonKind.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> HardwareButtonKind.Pause
            KeyEvent.KEYCODE_MEDIA_STOP -> HardwareButtonKind.Stop
            KeyEvent.KEYCODE_MEDIA_NEXT -> HardwareButtonKind.Next
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> HardwareButtonKind.Previous
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> HardwareButtonKind.SeekForward
            KeyEvent.KEYCODE_MEDIA_REWIND -> HardwareButtonKind.SeekBack
            else -> HardwareButtonKind.Unknown
        }

    private fun handleMediaAction(action: MediaAction, cueDurationMs: Long = 0L) {
        PulseBluetoothCommandLogStore.record("custom:${action.name}")
        when (action) {
            MediaAction.Repeat -> {
                if (runtimeSettings.headsetBluetooth.ignoreRepeatShuffle) return
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
            MediaAction.Shuffle -> {
                if (runtimeSettings.headsetBluetooth.ignoreRepeatShuffle) return
                player.shuffleModeEnabled = !player.shuffleModeEnabled
            }
            MediaAction.Close -> {
                player.pause()
                val delayMs = (cueDurationMs + 50L).coerceAtLeast(50L)
                serviceHandler.postDelayed({ stopSelf() }, delayMs)
            }
            MediaAction.SeekBack10 -> seekRelative(-10_000L)
            MediaAction.SeekForward10 -> seekRelative(10_000L)
            MediaAction.PreviousCategory -> player.seekToPreviousMediaItem()
            MediaAction.NextCategory -> player.seekToNextMediaItem()
            MediaAction.Like,
            MediaAction.Unlike,
            MediaAction.Rating,
            MediaAction.None -> Unit
        }
    }

    private fun seekRelative(deltaMs: Long) {
        player.seekTo(relativeSeekTarget(player.currentPosition, player.duration, deltaMs))
    }

    private fun sessionCommandsFor(actions: List<MediaAction>): SessionCommands {
        val builder = SessionCommands.Builder()
        mediaSessionCustomActions(actions).forEach { builder.add(SessionCommand(it, Bundle.EMPTY)) }
        return builder.build()
    }

    private fun commandButtonsFor(actions: List<MediaAction>): List<CommandButton> =
        actions
            .distinct()
            .mapNotNull { action ->
                val id = mediaSessionCustomActionId(action) ?: return@mapNotNull null
                CommandButton.Builder(commandButtonIcon(action))
                    .setDisplayName(action.label)
                    .setSessionCommand(SessionCommand(id, Bundle.EMPTY))
                    .build()
            }

    private fun commandButtonIcon(action: MediaAction): Int =
        when (action) {
            MediaAction.Repeat -> CommandButton.ICON_REPEAT_ALL
            MediaAction.Shuffle -> CommandButton.ICON_SHUFFLE_ON
            MediaAction.Close -> CommandButton.ICON_STOP
            MediaAction.Like -> CommandButton.ICON_THUMB_UP_UNFILLED
            MediaAction.Unlike -> CommandButton.ICON_THUMB_DOWN_UNFILLED
            MediaAction.Rating -> CommandButton.ICON_STAR_UNFILLED
            MediaAction.SeekBack10 -> CommandButton.ICON_SKIP_BACK_10
            MediaAction.SeekForward10 -> CommandButton.ICON_SKIP_FORWARD_10
            MediaAction.PreviousCategory -> CommandButton.ICON_PREVIOUS
            MediaAction.NextCategory -> CommandButton.ICON_NEXT
            MediaAction.None -> CommandButton.ICON_UNDEFINED
        }

    private fun pulseDeckLaunchPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private inline fun <T> tracePlaybackSection(label: String, block: () -> T): T {
        val startedAtMillis = SystemClock.uptimeMillis()
        Trace.beginSection("PulseDeck:$label")
        return try {
            block()
        } finally {
            Trace.endSection()
            perfLog("${label}_ms=${SystemClock.uptimeMillis() - startedAtMillis} thread=${Thread.currentThread().name}")
        }
    }

    private fun perfLog(message: String) {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(PERFORMANCE_TAG, message)
        }
    }
}

private fun mediaAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

private class PulseStreamingDataSourceFactory(
    private val context: Context,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val settings = PulsePlaybackRuntimeStore.runtime.value.misc.normalized()
        val timeoutMs = settings.networkStreamTimeoutSeconds.coerceIn(5, 300) * 1000
        val userAgent = settings.userAgent.trim().ifBlank { "PulseDeck/0.1 Media3" }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(timeoutMs)
            .setReadTimeoutMs(timeoutMs)
            .setUserAgent(userAgent)
        return DefaultDataSource.Factory(context, httpFactory).createDataSource()
    }
}

private class PulseCachedStreamingDataSourceFactory(
    context: Context,
) : DataSource.Factory {
    private val appContext = context.applicationContext

    override fun createDataSource(): DataSource =
        CacheDataSource.Factory()
            .setCache(PulsePlaybackCache.get(appContext))
            .setUpstreamDataSourceFactory(PulseStreamingDataSourceFactory(appContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or CacheDataSource.FLAG_BLOCK_ON_CACHE)
            .createDataSource()
}

private fun pulseLoadControl(settings: PlaybackRuntimeSettings): DefaultLoadControl {
    val streamBufferMb = settings.misc.normalized().networkStreamBufferMb
    val transitionPreloadScale = if (settings.crossfade.gaplessEnabled || settings.crossfade.crossfadeEnabled) 1.18f else 1f
    val maxBufferMs = (streamBufferMb * 64_000f * transitionPreloadScale).roundToInt().coerceIn(20_000, 300_000)
    val minBufferRatio = if (transitionPreloadScale > 1f) 0.55f else 0.45f
    val minBufferMs = (maxBufferMs * minBufferRatio).roundToInt().coerceIn(8_000, 120_000).coerceAtMost(maxBufferMs)
    val startBufferMs = (maxBufferMs * 0.08f).roundToInt().coerceIn(1_500, 8_000).coerceAtMost(minBufferMs)
    val rebufferMs = (maxBufferMs * 0.18f).roundToInt().coerceIn(3_000, 15_000).coerceAtMost(minBufferMs)
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, startBufferMs, rebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

internal object PulsePlaybackCache {
    private var cache: SimpleCache? = null

    @Synchronized
    fun isInitialized(): Boolean = cache != null

    @Synchronized
    fun get(context: Context): SimpleCache {
        val existing = cache
        if (existing != null) return existing
        val startedAtMillis = SystemClock.uptimeMillis()
        Trace.beginSection("PulseDeck:playback_cache_init")
        val databaseProvider = StandaloneDatabaseProvider(context)
        val streamingCacheBytes = CacheBudgetManager.streamingCacheBudgetBytes(context)
        return try {
            val newCache = SimpleCache(
                File(context.cacheDir, "media3-youtube-cache"),
                LeastRecentlyUsedCacheEvictor(streamingCacheBytes),
                databaseProvider,
            )
            cache = newCache
            newCache
        } finally {
            Trace.endSection()
            if (context.isDebuggable()) {
                Log.d(
                    PERFORMANCE_TAG,
                    "playback_cache_init_ms=${SystemClock.uptimeMillis() - startedAtMillis} cache_budget_bytes=$streamingCacheBytes thread=${Thread.currentThread().name}",
                )
            }
        }
    }
}

private fun Context.isDebuggable(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

private fun Format.toDecoderFormatDiagnostics(
    decoderName: String?,
    decodedPcmFormat: NativeDecodedPcmFormat,
    nowMillis: Long,
): DecoderFormatDiagnosticsSnapshot =
    DecoderFormatDiagnosticsSnapshot(
        containerMimeType = containerMimeType.cleanMimeType(),
        sampleMimeType = sampleMimeType.cleanMimeType(),
        codecString = codecs?.trim()?.takeIf { it.isNotBlank() },
        averageBitrateKbps = averageBitrate.toKbpsOrNull(),
        peakBitrateKbps = peakBitrate.toKbpsOrNull(),
        encodedSampleRateHz = sampleRate.takeIf { it > 0 },
        encodedChannelCount = channelCount.takeIf { it > 0 },
        decoderName = decoderName,
        decodedPcmEncoding = decodedPcmFormat.encoding,
        decodedSampleRateHz = decodedPcmFormat.sampleRateHz,
        decodedChannelCount = decodedPcmFormat.channelCount,
        updatedAtMillis = nowMillis,
    )

private fun DecoderFormatDiagnosticsSnapshot.withDecoderName(decoderName: String?): DecoderFormatDiagnosticsSnapshot =
    copy(decoderName = decoderName, updatedAtMillis = updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis())

private fun DecoderFormatDiagnosticsSnapshot.withDecodedPcm(decoded: NativeDecodedPcmFormat): DecoderFormatDiagnosticsSnapshot =
    copy(
        decodedPcmEncoding = decoded.encoding ?: decodedPcmEncoding,
        decodedSampleRateHz = decoded.sampleRateHz ?: decodedSampleRateHz,
        decodedChannelCount = decoded.channelCount ?: decodedChannelCount,
        updatedAtMillis = if (hasDecoderData() || decoded.hasData()) {
            updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        } else {
            updatedAtMillis
        },
    )

private fun DecoderFormatDiagnosticsSnapshot.hasDecoderData(): Boolean =
    !containerMimeType.isNullOrBlank() ||
        !sampleMimeType.isNullOrBlank() ||
        !codecString.isNullOrBlank() ||
        averageBitrateKbps != null ||
        peakBitrateKbps != null ||
        encodedSampleRateHz != null ||
        encodedChannelCount != null ||
        !decoderName.isNullOrBlank() ||
        !decodedPcmEncoding.isNullOrBlank() ||
        decodedSampleRateHz != null ||
        decodedChannelCount != null

private fun NativeDecodedPcmFormat.hasData(): Boolean =
    !encoding.isNullOrBlank() || sampleRateHz != null || channelCount != null

private fun String?.cleanMimeType(): String? =
    this
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun Int.toKbpsOrNull(): Int? =
    takeIf { it > 0 }?.let { value ->
        if (value > 10_000) (value / 1000f).roundToInt().coerceAtLeast(1) else value
    }

private class PulseNativeRenderersFactory(
    context: Context,
    private val nativeProcessor: AudioProcessor,
    private val truePeakProcessor: AudioProcessor,
    private val outputSettings: () -> OutputSettings,
    private val currentMedia: () -> ProOutputCurrentMedia,
    private val onProOutputDiagnostics: (ProOutputRuntimeSnapshot) -> Unit,
    private val onAudioTrackDiagnostics: (AudioTrackDiagnosticsSnapshot) -> Unit,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val proOutputProvider = PulseProAudioOutputProvider(
            context = context,
            outputSettings = outputSettings,
            currentMedia = currentMedia,
            onSnapshot = onProOutputDiagnostics,
            logger = ::proOutputDebugLog,
        )
        val stableSink = pulseDefaultAudioSink(
            context = context,
            nativeProcessor = nativeProcessor,
            truePeakProcessor = truePeakProcessor,
            enableFloatOutput = false,
            enableAudioTrackPlaybackParams = enableAudioTrackPlaybackParams,
            proOutputProvider = proOutputProvider,
        )
        val floatSink = pulseDefaultAudioSink(
            context = context,
            nativeProcessor = nativeProcessor,
            truePeakProcessor = truePeakProcessor,
            enableFloatOutput = true,
            enableAudioTrackPlaybackParams = enableAudioTrackPlaybackParams,
            proOutputProvider = proOutputProvider,
        )
        return PulseSwitchingAudioSink(
            stableSink = stableSink,
            floatSink = floatSink,
            rendererRequestedFloatOutput = enableFloatOutput,
            outputSettings = outputSettings,
            currentMedia = currentMedia,
            onAudioTrackDiagnostics = onAudioTrackDiagnostics,
            proOutputProvider = proOutputProvider,
        )
    }
}

private fun pulseDefaultAudioSink(
    context: Context,
    nativeProcessor: AudioProcessor,
    truePeakProcessor: AudioProcessor,
    enableFloatOutput: Boolean,
    enableAudioTrackPlaybackParams: Boolean,
    proOutputProvider: PulseProAudioOutputProvider,
): AudioSink =
    DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf(nativeProcessor, truePeakProcessor))
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioOutputProvider(proOutputProvider)
        .build()

private class PulseSwitchingAudioSink(
    private val stableSink: AudioSink,
    private val floatSink: AudioSink,
    private val rendererRequestedFloatOutput: Boolean,
    private val outputSettings: () -> OutputSettings,
    private val currentMedia: () -> ProOutputCurrentMedia,
    private val onAudioTrackDiagnostics: (AudioTrackDiagnosticsSnapshot) -> Unit,
    private val proOutputProvider: PulseProAudioOutputProvider,
) : AudioSink {
    private var activeSink: AudioSink = stableSink
    private var latestAudioTrack = AudioTrackDiagnosticsSnapshot()
    private var underrunCount = 0
    private var activeFormatKey: String? = null
    private var discontinuityWindowStartedAtMs: Long = 0L
    private var discontinuityCount: Int = 0
    private val timestampDiscontinuityFallbackFormats = mutableSetOf<String>()

    override fun setListener(listener: AudioSink.Listener) {
        val inspectingListener = inspectingListener(listener)
        stableSink.setListener(inspectingListener)
        floatSink.setListener(inspectingListener)
    }

    override fun supportsFormat(format: Format): Boolean =
        sinkFor(format).supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        sinkFor(format).getFormatSupport(format)

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        activeSink.getCurrentPositionUs(sourceEnded)

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val formatKey = inputFormat.proOutputFormatKey()
        activeFormatKey = formatKey
        discontinuityWindowStartedAtMs = 0L
        discontinuityCount = 0
        val decision = highPrecisionOutputDecision(
            output = outputSettings(),
            media = currentMedia(),
            format = inputFormat,
            rendererRequestedFloatOutput = rendererRequestedFloatOutput,
            timestampDiscontinuityFallback = formatKey in timestampDiscontinuityFallbackFormats,
        )
        val nextSink = if (decision.enableFloatOutput) floatSink else stableSink
        if (activeSink !== nextSink) {
            runCatching { activeSink.flush() }
            activeSink = nextSink
        }
        proOutputDebugLog(
            "PulseDeckProOutput",
            "float_output_policy enabled=${decision.enableFloatOutput} reason=${decision.reason} scope=${currentMedia().scope.wireValue} sample_rate=${inputFormat.sampleRate.takeIf { it > 0 } ?: "unknown"} pcm=${inputFormat.pcmEncoding.takeIf { it > 0 } ?: "unknown"}",
        )
        activeSink.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun play() {
        activeSink.play()
    }

    override fun handleDiscontinuity() {
        activeSink.handleDiscontinuity()
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean =
        activeSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

    override fun playToEndOfStream() {
        activeSink.playToEndOfStream()
    }

    override fun isEnded(): Boolean =
        activeSink.isEnded()

    override fun hasPendingData(): Boolean =
        activeSink.hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        stableSink.setPlaybackParameters(playbackParameters)
        floatSink.setPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters =
        activeSink.playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        stableSink.setSkipSilenceEnabled(skipSilenceEnabled)
        floatSink.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean =
        activeSink.skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        stableSink.setAudioAttributes(audioAttributes)
        floatSink.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes =
        activeSink.audioAttributes ?: AudioAttributes.DEFAULT

    override fun setAudioSessionId(audioSessionId: Int) {
        stableSink.setAudioSessionId(audioSessionId)
        floatSink.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        stableSink.setAuxEffectInfo(auxEffectInfo)
        floatSink.setAuxEffectInfo(auxEffectInfo)
    }

    override fun getAudioTrackBufferSizeUs(): Long =
        activeSink.audioTrackBufferSizeUs

    override fun enableTunnelingV21() {
        stableSink.enableTunnelingV21()
        floatSink.enableTunnelingV21()
    }

    override fun disableTunneling() {
        stableSink.disableTunneling()
        floatSink.disableTunneling()
    }

    override fun setVolume(volume: Float) {
        stableSink.setVolume(volume)
        floatSink.setVolume(volume)
    }

    override fun pause() {
        stableSink.pause()
        floatSink.pause()
    }

    override fun flush() {
        stableSink.flush()
        floatSink.flush()
    }

    override fun reset() {
        stableSink.reset()
        floatSink.reset()
    }

    override fun release() {
        stableSink.release()
        floatSink.release()
    }

    private fun sinkFor(format: Format): AudioSink {
        val decision = highPrecisionOutputDecision(
            output = outputSettings(),
            media = currentMedia(),
            format = format,
            rendererRequestedFloatOutput = rendererRequestedFloatOutput,
            timestampDiscontinuityFallback = format.proOutputFormatKey() in timestampDiscontinuityFallbackFormats,
        )
        return if (decision.enableFloatOutput) floatSink else stableSink
    }

    private fun inspectingListener(listener: AudioSink.Listener): AudioSink.Listener =
        object : AudioSink.Listener {
            override fun onPositionDiscontinuity() {
                listener.onPositionDiscontinuity()
            }

            override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) {
                listener.onPositionAdvancing(playoutStartSystemTimeMs)
            }

            override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                underrunCount += 1
                proOutputProvider.recordUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
                if (latestAudioTrack.updatedAtMillis > 0L) {
                    latestAudioTrack = latestAudioTrack.copy(
                        underrunCount = underrunCount,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                    onAudioTrackDiagnostics(latestAudioTrack)
                }
                listener.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
            }

            override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
                listener.onSkipSilenceEnabledChanged(skipSilenceEnabled)
            }

            override fun onOffloadBufferEmptying() {
                listener.onOffloadBufferEmptying()
            }

            override fun onOffloadBufferFull() {
                listener.onOffloadBufferFull()
            }

            override fun onAudioSinkError(audioSinkError: Exception) {
                maybeRecordTimestampDiscontinuityFallback(audioSinkError)
                listener.onAudioSinkError(audioSinkError)
            }

            override fun onAudioCapabilitiesChanged() {
                listener.onAudioCapabilitiesChanged()
            }

            override fun onAudioTrackInitialized(audioTrackConfig: AudioSink.AudioTrackConfig) {
                underrunCount = 0
                latestAudioTrack = AudioTrackDiagnosticsSnapshot(
                    configuredSampleRateHz = audioTrackConfig.sampleRate.takeIf { it > 0 },
                    encoding = audioEncodingLabel(audioTrackConfig.encoding),
                    channelCount = audioTrackConfig.channelConfig.takeIf { it > 0 }?.let(Integer::bitCount),
                    channelConfig = audioTrackConfig.channelConfig,
                    offload = audioTrackConfig.offload,
                    bufferSizeBytes = audioTrackConfig.bufferSize.takeIf { it > 0 },
                    underrunCount = underrunCount,
                    updatedAtMillis = System.currentTimeMillis(),
                )
                onAudioTrackDiagnostics(latestAudioTrack)
                proOutputDebugLog(
                    "PulseDeckAudioRoute",
                    "audio_track_initialized sample_rate=${latestAudioTrack.configuredSampleRateHz ?: "unknown"} encoding=${latestAudioTrack.encoding ?: "unknown"} channels=${latestAudioTrack.channelCount ?: "unknown"} buffer=${latestAudioTrack.bufferSizeBytes ?: "unknown"} offload=${latestAudioTrack.offload}",
                )
                listener.onAudioTrackInitialized(audioTrackConfig)
            }

            override fun onAudioTrackReleased(audioTrackConfig: AudioSink.AudioTrackConfig) {
                proOutputDebugLog("PulseDeckAudioRoute", "audio_track_released sample_rate=${audioTrackConfig.sampleRate.takeIf { it > 0 } ?: "unknown"}")
                listener.onAudioTrackReleased(audioTrackConfig)
            }

            override fun onSilenceSkipped() {
                listener.onSilenceSkipped()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                listener.onAudioSessionIdChanged(audioSessionId)
            }
        }

    private fun maybeRecordTimestampDiscontinuityFallback(audioSinkError: Exception) {
        if (audioSinkError !is AudioSink.UnexpectedDiscontinuityException) return
        val now = SystemClock.elapsedRealtime()
        if (discontinuityWindowStartedAtMs == 0L || now - discontinuityWindowStartedAtMs > 10_000L) {
            discontinuityWindowStartedAtMs = now
            discontinuityCount = 0
        }
        discontinuityCount += 1
        val key = activeFormatKey ?: return
        if (discontinuityCount < 3 || key in timestampDiscontinuityFallbackFormats) return
        timestampDiscontinuityFallbackFormats += key
        proOutputProvider.recordTimestampDiscontinuityFallback()
    }

    private fun Format.proOutputFormatKey(): String =
        listOf(
            sampleMimeType.orEmpty(),
            containerMimeType.orEmpty(),
            sampleRate.takeIf { it > 0 }?.toString().orEmpty(),
            channelCount.takeIf { it > 0 }?.toString().orEmpty(),
            pcmEncoding.takeIf { it > 0 }?.toString().orEmpty(),
        ).joinToString("|")
}
