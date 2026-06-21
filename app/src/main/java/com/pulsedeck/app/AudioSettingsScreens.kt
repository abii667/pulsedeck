package com.pulsedeck.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.audio.NativeAudioEngineBridge
import com.pulsedeck.app.audio.nativeAudioActivationFor
import java.util.Locale
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.roundToInt
internal enum class AudioSettingsPage(val title: String) {
    Index("Audio"),
    AudioInfo("Audio Info"),
    Crossfade("Crossfade, Fade, and Gapless"),
    ReplayGain("Replay Gain"),
    AudioFocus("Audio Focus"),
    Equalizer("Equalizer"),
    Resampler("Resampler"),
    Dvc("Direct Volume Control"),
    Output("Output"),
    AdvancedTweaks("Advanced Tweaks"),
}

private data class AudioSettingsIndexItem(
    val page: AudioSettingsPage,
    val title: String,
    val subtitle: String,
    val status: String,
    val icon: DeckIcon,
    val accent: Color,
    val keywords: List<String>,
)

@Composable
private fun AudioSettingsModule(
    page: AudioSettingsPage,
    onPage: (AudioSettingsPage) -> Unit,
    state: AudioEngineState,
    currentTrack: Track,
    positionMillis: Long,
    playerVolume: Float,
    onState: (AudioEngineState) -> Unit,
    onPreset: (AudioPreset) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val onResetDefaults = {
        onReset()
        Toast.makeText(context, "Restored to defaults", Toast.LENGTH_SHORT).show()
    }
    val quality = rememberTrackQuality(currentTrack)
    val heroAccent = if (state.processingActive) Green else Blue
    val indexItems = remember(state, currentTrack.stableKey(), quality) { audioSettingsIndexItems(state, currentTrack, quality) }
    val filtered = remember(indexItems, query) {
        indexItems.filter {
            settingsSearchMatches(
                SettingsSearchEntry(it.title, "${it.subtitle} ${it.status}", it.keywords),
                query,
            )
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF11141A),
                        Color(0xFF090A0D),
                        Color.Black,
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        SettingsTopBar(
            title = page.title,
            onBack = if (page == AudioSettingsPage.Index) onBack else { { onPage(AudioSettingsPage.Index) } },
            onSearch = { searchOpen = !searchOpen },
            onClose = onBack,
        )
        if (searchOpen && page == AudioSettingsPage.Index) {
            SettingsSearchBox(query = query, placeholder = "Search audio settings", onQuery = { query = it })
        }
        if (page == AudioSettingsPage.Equalizer) {
            AudioConsole(
                state = state,
                onState = onState,
                onPreset = onPreset,
                onReset = onResetDefaults,
                onBack = { onPage(AudioSettingsPage.Index) },
            )
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (page) {
                AudioSettingsPage.Index -> {
                    item {
                        SettingsHeroPanel(
                            title = "Sound Room",
                            subtitle = "${currentTrack.title} / ${currentTrack.artist}",
                            value = if (state.processingActive) "Processing on" else "Transparent path",
                            icon = DeckIcon.Signal,
                            accent = heroAccent,
                        )
                    }
                    items(filtered) { item ->
                        SettingsActionRow(
                            title = item.title,
                            subtitle = item.subtitle,
                            status = item.status,
                            icon = item.icon,
                            accent = item.accent,
                            onClick = { onPage(item.page) },
                        )
                    }
                    item { NoteBlock("EQ, presets, platform effects, tempo, pitch, and safe output gain stay wired to playback. Experimental routing controls are kept explicit so they do not pretend to be automatic.") }
                }
                AudioSettingsPage.AudioInfo -> audioInfoSettingsContent(state, currentTrack, quality, positionMillis, playerVolume, onState)
                AudioSettingsPage.Crossfade -> crossfadeSettingsContent(state, onState)
                AudioSettingsPage.ReplayGain -> replayGainSettingsContent(state, onState)
                AudioSettingsPage.AudioFocus -> audioFocusSettingsContent(state, onState)
                AudioSettingsPage.Resampler -> resamplerSettingsContent(state, onState)
                AudioSettingsPage.Dvc -> dvcSettingsContent(state, onState)
                AudioSettingsPage.Output -> outputSettingsContent(state, playerVolume, onState)
                AudioSettingsPage.AdvancedTweaks -> advancedTweaksSettingsContent(state, onState, onResetDefaults)
                AudioSettingsPage.Equalizer -> Unit
            }
        }
    }
}

private fun audioSettingsIndexItems(state: AudioEngineState, track: Track, quality: String): List<AudioSettingsIndexItem> =
    listOf(
        AudioSettingsIndexItem(AudioSettingsPage.AudioInfo, "Audio Info", "Signal chain, decoder, DSP, and output diagnostics", "${track.audioTypeLabel()} / $quality", DeckIcon.Signal, Color(0xFF8FD7FF), listOf("diagnostic", "signal chain", "format", "decoder", "metadata")),
        AudioSettingsIndexItem(AudioSettingsPage.Crossfade, "Transitions", "Crossfade, fade, and gapless behavior", if (state.crossfade.crossfadeEnabled) "${state.crossfade.durationMs / 1000}s ${state.crossfade.fadeCurve.label}" else "Gapless ${if (state.crossfade.gaplessEnabled) "on" else "off"}", DeckIcon.Crossfade, Color(0xFFFFB86B), listOf("fade", "gapless", "transition", "album", "shuffle")),
        AudioSettingsIndexItem(AudioSettingsPage.ReplayGain, "ReplayGain", "Loudness matching without flattening albums", state.replayGainMode.label, DeckIcon.Loudness, Color(0xFFFF7FA1), listOf("replay", "gain", "rg", "loudness", "volume", "preamp")),
        AudioSettingsIndexItem(AudioSettingsPage.AudioFocus, "Audio Focus", "Calls, notifications, Bluetooth, resume policy", "${state.audioFocus.onTransientLoss.label} transient", DeckIcon.Focus, Color(0xFF9FCA6B), listOf("focus", "calls", "notification", "duck", "resume", "bluetooth")),
        AudioSettingsIndexItem(AudioSettingsPage.Equalizer, "Equalizer", "Presets, bands, tone, limiter, native DSP, and A/B", "${state.eqBands.size}-band, ${if (state.native.media3DspEnabled) "native DSP" else if (state.eqEnabled) "enabled" else "off"}", DeckIcon.Equalizer, Color(0xFF9E8CFF), listOf("eq", "equalizer", "bass", "treble", "preset", "limiter", "tone", "native", "dsp", "compressor", "delay")),
        AudioSettingsIndexItem(AudioSettingsPage.Resampler, "Resampler", "Sample-rate conversion, dither, quality", "${state.resampler.mode.label} / ${state.resampler.quality.label}", DeckIcon.Resampler, Color(0xFF5CE4D0), listOf("resample", "sample rate", "dither", "quality", "battery")),
        AudioSettingsIndexItem(AudioSettingsPage.Dvc, "Direct Volume Control", "Internal gain path and Bluetooth volume behavior", if (state.dvc.enabled) "Enabled" else "Off", DeckIcon.Headphones, Color(0xFFFFD166), listOf("dvc", "direct volume", "headroom", "bluetooth", "volume")),
        AudioSettingsIndexItem(AudioSettingsPage.Output, "Output", "Device profile, buffers, Hi-Res attempt, fallback", "${state.output.mode.label} / ${state.output.deviceProfile.label}", DeckIcon.Output, Color(0xFF74A2FF), listOf("output", "audio track", "hi res", "dac", "buffer", "device")),
        AudioSettingsIndexItem(AudioSettingsPage.AdvancedTweaks, "Advanced Tweaks", "Compatibility, MusicFX, safe mode, reset", if (state.advancedTweaks.safeMode) "Safe mode" else "Standard", DeckIcon.Lab, Color(0xFFD5A3FF), listOf("advanced", "musicfx", "reset", "safe mode", "debug", "volume levels")),
    )

private fun androidx.compose.foundation.lazy.LazyListScope.audioInfoSettingsContent(
    state: AudioEngineState,
    track: Track,
    quality: String,
    positionMillis: Long,
    playerVolume: Float,
    onState: (AudioEngineState) -> Unit,
) {
    item { SettingsSectionTitle("Diagnostics", "Current playback and audio-engine state") }
    item { AudioInfoRow("Track", "${track.title} / ${track.artist}") }
    item { AudioInfoRow("Format", "${track.audioTypeLabel()} / $quality") }
    item { AudioInfoRow("Position", "${formatDuration(positionMillis)} / ${track.duration}") }
    item { AudioInfoRow("Output path", "${state.output.mode.label} / ${state.output.deviceProfile.label}") }
    item { AudioInfoRow("Output gain", "${(audioOutputGain(state, playerVolume) * 100f).roundToInt()}% after headroom") }
    item { AudioInfoRow("DSP status", "EQ ${if (state.eqEnabled) "on" else "off"} / RG ${state.replayGainMode.label} / Limiter ${if (state.limiter.enabled) "on" else "off"}") }
    item { AudioInfoRow("Native engine", if (NativeAudioEngineBridge.isAvailable) NativeAudioEngineBridge.engineVersion() else "Unavailable: ${NativeAudioEngineBridge.unavailableReason ?: "library not loaded"}") }
    item { AudioInfoRow("Native graph", "Media3 ${if (state.native.media3DspEnabled) "DSP on" else "DSP off"} / test tone ${if (state.native.lowLatencyToneEnabled) "on" else "off"}") }
    item { AudioInfoRow("Resampler", "${state.resampler.mode.label} / ${state.resampler.outputSampleRate.label}") }
    item { AudioInfoRow("Clipping", if (state.clippingRisk) "Warning: boost needs headroom" else "Safe") }
    item { SettingsSectionTitle("Signal Chain", "File to output route") }
    item { SignalChainStep("File", "${track.audioTypeLabel()} source") }
    item { SignalChainStep("Decoder", "Media3 ExoPlayer") }
    item { SignalChainStep("ReplayGain + Preamp", "${state.replayGainMode.label}, ${signedDb(state.replayGainPreampDb)}") }
    item { SignalChainStep("EQ + Effects", "${state.eqBands.size}-band EQ, tone, native ${if (state.native.media3DspEnabled) "DSP" else "bypass"}") }
    item { SignalChainStep("Limiter + Output", "${state.output.mode.label}, gain ${(audioOutputGain(state, 1f) * 100f).roundToInt()}%") }
    item { SettingsSectionTitle("Visibility", "Where diagnostics appear") }
    item { SwitchSetting("Long press metadata opens Audio Info", state.audioInfo.showOnMainScreenLongPress) { onState(state.copy(audioInfo = state.audioInfo.copy(showOnMainScreenLongPress = it))) } }
    item { SwitchSetting("Show output path", state.audioInfo.showOutputPath) { onState(state.copy(audioInfo = state.audioInfo.copy(showOutputPath = it))) } }
    item { SwitchSetting("Show DSP chain", state.audioInfo.showDspChain) { onState(state.copy(audioInfo = state.audioInfo.copy(showDspChain = it))) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.crossfadeSettingsContent(state: AudioEngineState, onState: (AudioEngineState) -> Unit) {
    val crossfade = state.crossfade
    item { SettingsSectionTitle("Transitions", "Gapless wins for albums; crossfade is best for shuffle") }
    item { SwitchSetting("Gapless Playback", crossfade.gaplessEnabled, "Prefer seamless album playback when tracks support it") { onState(state.copy(crossfade = crossfade.copy(gaplessEnabled = it))) } }
    item { SwitchSetting("Crossfade", crossfade.crossfadeEnabled, "Overlap outgoing and incoming tracks") { onState(state.copy(crossfade = crossfade.copy(crossfadeEnabled = it))) } }
    item { SettingSlider("Crossfade Duration", "${crossfade.durationMs / 1000}s", "0", "12", crossfade.durationMs / 1000f, 0f..12f, { onState(state.copy(crossfade = crossfade.copy(durationMs = (it * 1000f).roundToInt()))) }) }
    item { SettingsSelector("Fade Curve", FadeCurve.entries.toList(), crossfade.fadeCurve, { it.label }) { onState(state.copy(crossfade = crossfade.copy(fadeCurve = it))) } }
    item { SwitchSetting("Fade on Play", crossfade.fadeOnPlay) { onState(state.copy(crossfade = crossfade.copy(fadeOnPlay = it))) } }
    item { SwitchSetting("Fade on Pause", crossfade.fadeOnPause) { onState(state.copy(crossfade = crossfade.copy(fadeOnPause = it))) } }
    item { SwitchSetting("Fade on Manual Skip", crossfade.fadeOnManualSkip, "Manual skips stay quick and use a short fade") { onState(state.copy(crossfade = crossfade.copy(fadeOnManualSkip = it))) } }
    item { SwitchSetting("Disable Crossfade for Albums", crossfade.disableForAlbums) { onState(state.copy(crossfade = crossfade.copy(disableForAlbums = it))) } }
    item { SwitchSetting("Disable Crossfade for Short Tracks", crossfade.disableForShortTracks) { onState(state.copy(crossfade = crossfade.copy(disableForShortTracks = it))) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.replayGainSettingsContent(state: AudioEngineState, onState: (AudioEngineState) -> Unit) {
    val replayGain = state.replayGain
    fun update(next: ReplayGainSettings) = onState(state.withReplayGainSettings(next))
    item { SettingsSectionTitle("ReplayGain", "Keeps song volume consistent without hiding album dynamics") }
    item { SwitchSetting("ReplayGain Enabled", replayGain.enabled, "Off keeps raw playback") { update(replayGain.copy(enabled = it, mode = if (it && replayGain.mode == ReplayGainMode.Off) ReplayGainMode.Smart else replayGain.mode)) } }
    item { SettingsSelector("ReplayGain Mode", ReplayGainMode.entries.toList(), state.replayGainMode, { it.label }) { update(replayGain.copy(enabled = it != ReplayGainMode.Off, mode = it)) } }
    item { SettingsSelector("Source", ReplayGainSource.entries.toList(), replayGain.source, { it.label }) { update(replayGain.copy(source = it)) } }
    item { SettingSlider("Track Preamp", signedDb(replayGain.trackPreampDb), "-12", "+12", replayGain.trackPreampDb, -12f..12f, { update(replayGain.copy(trackPreampDb = it)) }) }
    item { SettingSlider("Album Preamp", signedDb(replayGain.albumPreampDb), "-12", "+12", replayGain.albumPreampDb, -12f..12f, { update(replayGain.copy(albumPreampDb = it)) }) }
    item { SettingSlider("No-RG Preamp", signedDb(replayGain.noRgPreampDb), "-12", "+12", replayGain.noRgPreampDb, -12f..12f, { update(replayGain.copy(noRgPreampDb = it)) }) }
    item { SwitchSetting("Prevent Clipping", replayGain.preventClipping, "Reduce output gain when boost may distort") { update(replayGain.copy(preventClipping = it)) } }
    item { SwitchSetting("Show RG in Audio Info", replayGain.showInAudioInfo) { update(replayGain.copy(showInAudioInfo = it)) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioFocusSettingsContent(state: AudioEngineState, onState: (AudioEngineState) -> Unit) {
    val focus = state.audioFocus
    fun update(next: AudioFocusSettings) = onState(state.copy(audioFocus = next))
    item { SettingsSectionTitle("Focus Policy", "Resume only when PulseDeck paused itself") }
    item { SwitchSetting("Request Audio Focus on Play", focus.requestOnPlay) { update(focus.copy(requestOnPlay = it)) } }
    item { SettingsSelector("Permanent Focus Loss", PermanentFocusLossAction.entries.toList(), focus.onPermanentLoss, { it.label }) { update(focus.copy(onPermanentLoss = it)) } }
    item { SettingsSelector("Temporary Focus Loss", TransientFocusLossAction.entries.toList(), focus.onTransientLoss, { it.label }) { update(focus.copy(onTransientLoss = it)) } }
    item { SettingsSelector("Duck Request", DuckRequestAction.entries.toList(), focus.onDuck, { it.label }) { update(focus.copy(onDuck = it)) } }
    item { SwitchSetting("Resume After Call", focus.resumeAfterCall) { update(focus.copy(resumeAfterCall = it)) } }
    item { SwitchSetting("Resume After Notification", focus.resumeAfterNotification) { update(focus.copy(resumeAfterNotification = it)) } }
    item { SettingSlider("Duck Volume Level", "${(focus.duckVolume * 100f).roundToInt()}%", "0", "100", focus.duckVolume, 0f..1f, { update(focus.copy(duckVolume = it)) }) }
    item { SettingSlider("Duck Fade Time", "${focus.duckFadeMs} ms", "50", "1000", focus.duckFadeMs.toFloat(), 50f..1000f, { update(focus.copy(duckFadeMs = it.roundToInt())) }) }
    item { SettingsSelector("Bluetooth Focus Behavior", BluetoothFocusBehavior.entries.toList(), focus.bluetoothBehavior, { it.label }) { update(focus.copy(bluetoothBehavior = it)) } }
    item { SwitchSetting("Resume Only If Auto-Paused", focus.resumeOnlyIfAutoPaused, "Do not restart audio after the user manually paused") { update(focus.copy(resumeOnlyIfAutoPaused = it)) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resamplerSettingsContent(state: AudioEngineState, onState: (AudioEngineState) -> Unit) {
    val resampler = state.resampler
    fun update(next: ResamplerSettings) = onState(state.copy(resampler = next))
    item { SettingsSectionTitle("Sample-rate Conversion", "Auto avoids unnecessary processing") }
    item { SettingsSelector("Resampler Mode", ResamplerMode.entries.toList(), resampler.mode, { it.label }) { update(resampler.copy(mode = it)) } }
    item { SettingsSelector("Output Sample Rate", OutputSampleRate.entries.toList(), resampler.outputSampleRate, { it.label }) { update(resampler.copy(outputSampleRate = it)) } }
    item { SettingsSelector("Quality", ResamplerQuality.entries.toList(), resampler.quality, { it.label }) { update(resampler.copy(quality = it)) } }
    item { SwitchSetting("Dither", resampler.dither, "Applies when reducing bit depth") { update(resampler.copy(dither = it)) } }
    item { SwitchSetting("Show Conversion Info", resampler.showConversionInfo) { update(resampler.copy(showConversionInfo = it)) } }
    item { SwitchSetting("Battery Saver Mode", resampler.batterySaverMode, "Use lighter processing when needed") { update(resampler.copy(batterySaverMode = it)) } }
    item { NoteBlock("Higher resampling quality may increase CPU and battery use. Auto is recommended for most users.") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dvcSettingsContent(state: AudioEngineState, onState: (AudioEngineState) -> Unit) {
    val dvc = state.dvc
    fun update(next: DirectVolumeControlSettings) = onState(state.copy(dvc = next))
    item { SettingsSectionTitle("Direct Volume Control", "Advanced gain path controls for supported devices") }
    item { SwitchSetting("Enable DVC", dvc.enabled) { update(dvc.copy(enabled = it)) } }
    item { SwitchSetting("DVC for Bluetooth", dvc.bluetoothEnabled) { update(dvc.copy(bluetoothEnabled = it)) } }
    item { SettingSlider("DVC Headroom", signedDb(dvc.headroomDb), "-12", "0", dvc.headroomDb, -12f..0f, { update(dvc.copy(headroomDb = it)) }) }
    item { SettingsSelector("Volume Steps", DvcVolumeSteps.entries.toList(), dvc.volumeSteps, { it.label }) { update(dvc.copy(volumeSteps = it)) } }
    item { SwitchSetting("Per-Device DVC", dvc.perDeviceDvc) { update(dvc.copy(perDeviceDvc = it)) } }
    item { SwitchSetting("Compatibility Mode", dvc.compatibilityMode, "Use this if audio is too quiet, distorted, or inconsistent") { update(dvc.copy(compatibilityMode = it)) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.outputSettingsContent(state: AudioEngineState, playerVolume: Float, onState: (AudioEngineState) -> Unit) {
    val output = state.output
    val native = state.native
    val nativeConstraint = nativeMasterGainConstraint(state, native.masterGain)
    fun update(next: OutputSettings) = onState(state.copy(output = next))
    fun updateNative(next: NativeAudioSettings) = onState(state.copy(native = next))
    item { SettingsSectionTitle("Output Routing", "Auto is the safest default") }
    item { SettingsSelector("Output Mode", OutputMode.entries.toList(), output.mode, { it.label }) { update(output.copy(mode = it)) } }
    item { SettingsSelector("Device Profile", DeviceProfile.entries.toList(), output.deviceProfile, { it.label }) { update(output.copy(deviceProfile = it)) } }
    item { SettingsSelector("Buffer Size", BufferMode.entries.toList(), output.bufferMode, { it.label }) { update(output.copy(bufferMode = it)) } }
    item { SwitchSetting("Hi-Res Attempt", output.hiResEnabled) { update(output.copy(hiResEnabled = it)) } }
    item { SettingsSelector("Sample Rate", OutputSampleRate.entries.toList(), output.sampleRate, { it.label }) { update(output.copy(sampleRate = it)) } }
    item { SettingsSelector("Bit Depth", OutputBitDepth.entries.toList(), output.bitDepth, { it.label }) { update(output.copy(bitDepth = it)) } }
    item { SettingsSelector("Fallback Mode", OutputFallbackMode.entries.toList(), output.fallbackMode, { it.label }) { update(output.copy(fallbackMode = it)) } }
    item { SwitchSetting("Show Output Warnings", output.showOutputWarnings) { update(output.copy(showOutputWarnings = it)) } }
    item { AudioInfoRow("Active output gain", "${(audioOutputGain(state, playerVolume) * 100f).roundToInt()}%") }
    item {
        AudioInfoRow(
            "Native preamp ceiling",
            if (nativeConstraint.constrained) {
                "Effective ${signedDb(nativeConstraint.effectiveDb)} from requested ${signedDb(nativeConstraint.requestedDb)}"
            } else {
                "Within verified ${signedDb(linearToDb(NATIVE_MASTER_GAIN_MAX_LINEAR))} ceiling"
            },
        )
    }
    item { SettingsSectionTitle("Native Engine", "Oboe graph and Media3 PCM processor") }
    item { AudioInfoRow("Native library", if (NativeAudioEngineBridge.isAvailable) NativeAudioEngineBridge.engineVersion() else "Unavailable: ${NativeAudioEngineBridge.unavailableReason ?: "library not loaded"}") }
    item { SwitchSetting("Enable Native Engine", native.enabled, "Gates native DSP and low-latency output") { updateNative(native.copy(enabled = it)) } }
    item { SwitchSetting("Media3 Native DSP", native.media3DspEnabled, "Processes decoded PCM before Android output") { updateNative(native.copy(media3DspEnabled = it, enabled = native.enabled || it)) } }
    item { SwitchSetting("Low-Latency Test Tone", native.lowLatencyToneEnabled, "Uses native output when Native Low Latency is selected") { updateNative(native.copy(lowLatencyToneEnabled = it, enabled = native.enabled || it)) } }
    item { SettingSlider("Native Master", "${(native.masterGain * 100f).roundToInt()}%", "0", "150", native.masterGain, 0f..1.5f, { updateNative(native.copy(masterGain = it)) }) }
    item { SettingSlider("Test Tone", "${native.sourceFrequencyHz.roundToInt()} Hz", "20", "4k", native.sourceFrequencyHz, 20f..4000f, { updateNative(native.copy(sourceFrequencyHz = it)) }) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.advancedTweaksSettingsContent(state: AudioEngineState, onState: (AudioEngineState) -> Unit, onReset: () -> Unit) {
    val tweaks = state.advancedTweaks
    fun update(next: AdvancedAudioTweaksSettings) = onState(state.copy(advancedTweaks = next))
    item { SettingsSectionTitle("Compatibility", "Rare controls for troubleshooting and reset actions") }
    item { SettingsSelector("Volume Levels", DvcVolumeSteps.entries.toList(), tweaks.volumeSteps, { it.label }) { update(tweaks.copy(volumeSteps = it)) } }
    item { SwitchSetting("MusicFX", tweaks.musicFxEnabled, "Optional system effects integration where available") { update(tweaks.copy(musicFxEnabled = it)) } }
    item { SwitchSetting("Safe Mode", tweaks.safeMode, "Temporarily disables advanced DSP experiments") { update(tweaks.copy(safeMode = it)) } }
    item { SwitchSetting("Debug Audio Info", tweaks.debugAudioInfo) { update(tweaks.copy(debugAudioInfo = it)) } }
    item { SleepDialogButton("Reset EQ Presets and Audio Defaults", Modifier.fillMaxWidth(), tone = Color.White.copy(alpha = 0.08f), onClick = onReset) }
    item { NoteBlock("Dangerous reset actions should provide undo or confirmation before clearing user-created presets in a production build.") }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit, onSearch: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.34f),
                        Color(0xFF191C22).copy(alpha = 0.90f),
                        Color.Black.copy(alpha = 0.18f),
                    ),
                ),
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap(DeckIcon.Back, onBack)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(top = 5.dp).width(42.dp).height(3.dp).clip(RoundedCornerShape(3.dp)).background(Blue.copy(alpha = 0.86f)))
        }
        IconTap(DeckIcon.Search, onSearch)
        IconTap(DeckIcon.Close, onClose)
    }
}

@Composable
private fun SettingsSearchBox(query: String, placeholder: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Search, Color.White.copy(alpha = 0.70f), Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isBlank()) Text(placeholder, color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                inner()
            },
        )
    }
}

@Composable
private fun SettingsHeroPanel(title: String, subtitle: String, value: String, icon: DeckIcon, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.075f),
                        Color.Black.copy(alpha = 0.22f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.24f))
                .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(icon, Color.White, Modifier.size(32.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(7.dp))
                Text(value, color = accent, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String, status: String, icon: DeckIcon, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (enabled) 0.11f else 0.04f),
                        Color.White.copy(alpha = if (enabled) 0.060f else 0.028f),
                        Color.White.copy(alpha = if (enabled) 0.040f else 0.018f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.08f else 0.035f), RoundedCornerShape(20.dp))
            .then(if (enabled) Modifier.pressScaleEffect(interactionSource).clickable(interactionSource = interactionSource, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = if (enabled) 0.20f else 0.10f))
                .border(1.dp, accent.copy(alpha = if (enabled) 0.30f else 0.10f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(icon, Color.White.copy(alpha = if (enabled) 0.94f else 0.28f), Modifier.size(25.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 1f else 0.36f), fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (status.isNotBlank()) {
                Text(
                    status.uppercase(Locale.US),
                    color = accent.copy(alpha = if (enabled) 1f else 0.35f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.20f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            PulseIcon(DeckIcon.Next, Color.White.copy(alpha = if (enabled) 0.42f else 0.16f), Modifier.size(18.dp).padding(top = 7.dp))
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.verticalGradient(listOf(Blue, Green))),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun <T> SettingsSelector(title: String, options: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    SettingSurface {
        Text(title, color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
        options.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    AudioChip(label(option), selected == option, Modifier.weight(1f)) { onSelected(option) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun SettingSurface(active: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (active) 0.105f else 0.070f),
                        Color.White.copy(alpha = if (active) 0.070f else 0.046f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = if (active) 0.12f else 0.065f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun AudioConsole(
    state: AudioEngineState,
    onState: (AudioEngineState) -> Unit,
    onPreset: (AudioPreset) -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableStateOf(AudioConsoleTab.Simple) }
    val activePreset = pulseAudioPresets.firstOrNull { it.id == state.activePresetId } ?: pulseAudioPresets.first()
    val headroomDb = if (state.preventClipping) -max(0f, state.maxBoostDb) else 0f
    LazyColumn(
        Modifier
            .fillMaxSize()
            .then(if (onBack == null) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = if (onBack == null) 24.dp else 14.dp, bottom = 184.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconTap(DeckIcon.Back, onBack, 44.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Audio Engine", color = Color.White, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black)
                        Text("Neutral by default. Powerful when enabled.", color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    AudioPowerButton(state.processingActive) { onState(state.copy(enabled = !state.enabled, bypass = false)) }
                }
                AudioTabRow(tab) { tab = it }
                AudioStatusCard(state, activePreset, headroomDb)
            }
        }
        when (tab) {
            AudioConsoleTab.Simple -> {
                item { AudioSectionTitle("Presets", "Global tuning that applies across the player") }
                items(pulseAudioPresets) { preset ->
                    AudioPresetRow(preset, selected = preset.id == state.activePresetId && !state.presetModified) { onPreset(preset) }
                }
                item { AudioSectionTitle("Simple Controls", "Fast musical shaping with safe headroom") }
                item { SwitchSetting("Bypass All Processing", state.bypass, "True bypass keeps playback transparent") { onState(state.copy(bypass = it)) } }
                item { SettingSlider("Bass", signedDb(state.bassDb), "Less", "More", state.bassDb, -12f..12f, { onState(state.copy(bassDb = it)) }) }
                item { SettingSlider("Treble", signedDb(state.trebleDb), "Dark", "Bright", state.trebleDb, -12f..12f, { onState(state.copy(trebleDb = it)) }) }
                item { SettingSlider("Vocal Clarity", signedDb(state.vocalClarityDb), "Soft", "Clear", state.vocalClarityDb, -12f..12f, { onState(state.copy(vocalClarityDb = it)) }) }
                item { SettingSlider("Loudness", signedDb(state.loudnessDb), "Off", "Full", state.loudnessDb, 0f..12f, { onState(state.copy(loudnessDb = it)) }, "Uses the playback audio session where platform support is available.") }
            }
            AudioConsoleTab.EQ -> {
                val nativeActivation = nativeAudioActivationFor(state, NativeAudioEngineBridge.isAvailable)
                val graphicEqGainRange = state.effectiveGraphicEqGainRange(nativeActivation.media3DspActive)
                val extendedRangeRequested = state.graphicEqRangeMode == GraphicEqRangeMode.Extended15DbWhenSupported
                val extendedRangeEffective = state.graphicEqExtendedRangeEffective(nativeActivation.media3DspActive)
                val rangeStatus = if (extendedRangeEffective) "+/-15 dB active" else if (extendedRangeRequested) "+/-12 dB effective" else "+/-12 dB standard"
                item { AudioSectionTitle("Graphic EQ", "10 bands, flat by default, smooth global preset state") }
                item { SwitchSetting("EQ Enabled", state.eqEnabled, "Disable only equalizer bands while keeping other controls") { onState(state.copy(eqEnabled = it)) } }
                item {
                    SwitchSetting(
                        "Extended Range",
                        extendedRangeRequested,
                        "Native DSP only; current $rangeStatus",
                    ) {
                        onState(state.withGraphicEqRangeMode(if (it) GraphicEqRangeMode.Extended15DbWhenSupported else GraphicEqRangeMode.Standard12Db))
                    }
                }
                item {
                    SettingSlider(
                        "Preamp",
                        signedDb(state.preampDb),
                        "-12 dB",
                        "+12 dB",
                        state.preampDb,
                        -12f..12f,
                        { onState(state.copy(preampDb = quantizeAudioDb(it, -12f..12f))) },
                        "Creates headroom before boosts. Auto headroom is ${signedDb(headroomDb)}.",
                        steps = audioDbSliderSteps(-12f..12f),
                        snapStepDb = AUDIO_EQ_INPUT_STEP_DB,
                    )
                }
                items(state.eqBands.size) { index ->
                    val band = state.eqBands[index]
                    EqBandControl(band, graphicEqGainRange) { gain ->
                        onState(state.withGraphicEqBandGain(index, gain))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SleepDialogButton("Flat", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.08f)) {
                            pulseAudioPresets.firstOrNull { it.id == "flat" }?.let(onPreset)
                        }
                        SleepDialogButton("A/B Bypass", Modifier.weight(1f), tone = Blue.copy(alpha = 0.34f)) { onState(state.copy(bypass = !state.bypass)) }
                    }
                }
            }
            AudioConsoleTab.Advanced -> {
                item { AudioSectionTitle("ReplayGain", "Loudness normalization model for local music") }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReplayGainMode.entries.forEach { mode ->
                            AudioChip(mode.label, selected = state.replayGainMode == mode, modifier = Modifier.weight(1f)) {
                                onState(state.copy(replayGainMode = mode))
                            }
                        }
                    }
                }
                item { SettingSlider("ReplayGain Preamp", signedDb(state.replayGainPreampDb), "-12", "+12", state.replayGainPreampDb, -12f..12f, { onState(state.copy(replayGainPreampDb = it)) }) }
                item { AudioSectionTitle("Limiter & Clipping", "Protection before output") }
                item { SwitchSetting("Prevent Clipping", state.preventClipping, "Automatically lowers preamp when boosted EQ may clip") { onState(state.copy(preventClipping = it)) } }
                item { SwitchSetting("Soft Limiter", state.limiter.enabled, "DSP model is active; platform output uses safe gain fallback") { onState(state.copy(limiter = state.limiter.copy(enabled = it))) } }
                item { SettingSlider("Limiter Ceiling", signedDb(state.limiter.ceilingDb), "-6", "0", state.limiter.ceilingDb, -6f..0f, { onState(state.copy(limiter = state.limiter.copy(ceilingDb = it))) }) }
                item { AudioSectionTitle("Parametric EQ", "Power-user model ready for custom DSP bands") }
                items(state.parametricEq.size) { index ->
                    val band = state.parametricEq[index]
                    ParametricBandRow(band) { next ->
                        onState(state.withParametricEqBand(index, next))
                    }
                }
                item { AudioSectionTitle("Stereo, Reverb, Tempo", "Advanced controls stay bypassable and saved globally") }
                item { SettingSlider("Balance", "${(state.stereo.balance * 100f).roundToInt()}%", "Left", "Right", state.stereo.balance, -1f..1f, { onState(state.copy(stereo = state.stereo.copy(balance = it))) }) }
                item { SwitchSetting("Mono", state.stereo.mono, "Useful for small speakers and broken earbuds") { onState(state.copy(stereo = state.stereo.copy(mono = it))) } }
                item { SettingSlider("Stereo Width", "${(state.stereo.stereoWidth * 100f).roundToInt()}%", "Narrow", "Wide", state.stereo.stereoWidth, 0f..1f, { onState(state.copy(stereo = state.stereo.copy(stereoWidth = it))) }) }
                item { SettingSlider("Crossfeed", "${(state.stereo.crossfeed * 100f).roundToInt()}%", "Off", "Natural", state.stereo.crossfeed, 0f..1f, { onState(state.copy(stereo = state.stereo.copy(crossfeed = it))) }) }
                item { SwitchSetting("Reverb", state.reverb.enabled, "Creative effect; off by default") { onState(state.copy(reverb = state.reverb.copy(enabled = it))) } }
                item { SettingSlider("Reverb Mix", "${(state.reverb.mix * 100f).roundToInt()}%", "Dry", "Wet", state.reverb.mix, 0f..1f, { onState(state.copy(reverb = state.reverb.copy(mix = it))) }) }
                item { SettingSlider("Tempo", "${String.format(java.util.Locale.US, "%.2fx", state.tempoPitch.tempo)}", "Slow", "Fast", state.tempoPitch.tempo, 0.5f..2f, { onState(state.copy(tempoPitch = state.tempoPitch.copy(tempo = it))) }) }
                item { SettingSlider("Pitch", "${state.tempoPitch.pitchSemitones.roundToInt()} st", "-12", "+12", state.tempoPitch.pitchSemitones, -12f..12f, { onState(state.copy(tempoPitch = state.tempoPitch.copy(pitchSemitones = it))) }) }
            }
            AudioConsoleTab.Output -> {
                item { AudioSectionTitle("Output", "Global preset status and device behavior") }
                item { AudioInfoRow("Current profile", "Global preset") }
                item { AudioInfoRow("Active preset", activePreset.name + if (state.presetModified) " (edited)" else "") }
                item { AudioInfoRow("Processing", if (state.processingActive) "Enabled" else "Bypassed") }
                item { AudioInfoRow("Output gain", "${(audioOutputGain(state, 1f) * 100f).roundToInt()}% after headroom") }
                item { AudioInfoRow("Device profiles", "Future: headphones, car Bluetooth, DAC, speaker") }
                item { AudioInfoRow("Background engine", "Media3 MediaSessionService") }
            }
            AudioConsoleTab.QA -> {
                item { AudioSectionTitle("Signal Chain", "Developer and QA view") }
                item { SignalChainStep("Decoded PCM", "Media3 ExoPlayer local playback") }
                item { SignalChainStep("ReplayGain + Preamp", "Model-backed gain path, clipping-aware output gain") }
                item { SignalChainStep("EQ + Tone", "10-band platform fallback; Kotlin DSP model foundation") }
                item { SignalChainStep("Stereo + Tempo", "Session effects and playback parameters where supported") }
                item { SignalChainStep("Limiter + Output", "Soft-limit model plus safe headroom fallback") }
                item { AudioInfoRow("Bypass acceptance", "Flat/bypass should not color playback") }
                item { AudioInfoRow("Clipping state", if (state.clippingRisk) "Risk detected - reduce boost or enable protection" else "No current boost risk") }
                item { SleepDialogButton("Restore Flat Defaults", Modifier.fillMaxWidth(), tone = Color.White.copy(alpha = 0.08f), onClick = onReset) }
            }
        }
    }
}

@Composable
private fun AudioPowerButton(active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width = 74.dp, height = 44.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (active) Green.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(if (active) 0.20f else 0.06f), RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (active) "ON" else "OFF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AudioTabRow(selected: AudioConsoleTab, onTab: (AudioConsoleTab) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioConsoleTab.entries.take(3).forEach { tab ->
                AudioChip(tab.label, selected == tab, Modifier.weight(1f)) { onTab(tab) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioConsoleTab.entries.drop(3).forEach { tab ->
                AudioChip(tab.label, selected == tab, Modifier.weight(1f)) { onTab(tab) }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AudioStatusCard(state: AudioEngineState, preset: AudioPreset, headroomDb: Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Blue.copy(0.17f), Color.White.copy(0.065f), Color.Black.copy(0.24f))))
            .border(1.dp, Color.White.copy(0.09f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseIcon(DeckIcon.Equalizer, Color.White, Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(preset.name + if (state.presetModified) " (edited)" else "", color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
                Text(if (state.processingActive) "Processing active" else "Transparent bypass", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            }
            Badge(if (state.clippingRisk) "HEADROOM" else "SAFE")
        }
        Text(preset.description, color = Color.White.copy(0.72f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("Auto headroom ${signedDb(headroomDb)}  |  Max boost ${signedDb(state.maxBoostDb)}", color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AudioSectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 30.dp).clip(RoundedCornerShape(4.dp)).background(Blue.copy(alpha = 0.86f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AudioPresetRow(preset: AudioPreset, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Blue.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(if (selected) 0.18f else 0.055f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Signal, Color.White, Modifier.size(25.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(preset.name, color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
            Text(preset.description, color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EqBandControl(
    band: GraphicEqBand,
    graphicEqGainRange: ClosedFloatingPointRange<Float>,
    onGain: (Float) -> Unit,
) {
    val effectiveGain = band.gainDb.coerceIn(graphicEqGainRange.start, graphicEqGainRange.endInclusive)
    SettingSlider(
        title = band.frequencyLabel(),
        valueText = effectiveDbLabel(band.gainDb, effectiveGain),
        left = signedDb(graphicEqGainRange.start),
        right = signedDb(graphicEqGainRange.endInclusive),
        value = effectiveGain,
        range = graphicEqGainRange,
        onChange = onGain,
        steps = audioDbSliderSteps(graphicEqGainRange),
        snapStepDb = AUDIO_EQ_INPUT_STEP_DB,
    )
}

@Composable
private fun AudioChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Blue.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(if (selected) 0.18f else 0.055f), RoundedCornerShape(14.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ParametricBandRow(band: ParametricEqBand, onBand: (ParametricEqBand) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (band.enabled) Blue.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = if (band.enabled) 0.12f else 0.05f), RoundedCornerShape(18.dp))
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(band.type.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text("${band.frequencyHz.roundToInt()} Hz  ${signedDb(band.gainDb)}  Q ${String.format(java.util.Locale.US, "%.1f", band.q)}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            PulseToggle(checked = band.enabled, onChange = { onBand(band.copy(enabled = it)) })
        }
        SettingSlider("Frequency", "${band.frequencyHz.roundToInt()} Hz", "20", "20k", band.frequencyHz, 20f..20000f, { onBand(band.copy(frequencyHz = it)) })
        SettingSlider("Gain", signedDb(band.gainDb), "-12", "+12", band.gainDb, -12f..12f, { onBand(band.copy(gainDb = it)) })
        SettingSlider("Q", String.format(java.util.Locale.US, "%.1f", band.q), "0.1", "18", band.q, 0.1f..18f, { onBand(band.copy(q = it)) })
    }
}

@Composable
private fun AudioInfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.052f))
            .border(1.dp, Color.White.copy(alpha = 0.045f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(Locale.US), color = Blue.copy(alpha = 0.92f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.72f))
        Text(value, color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.28f))
    }
}

@Composable
private fun SignalChainStep(title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.042f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(Green.copy(alpha = 0.82f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            Text(detail, color = Muted, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

internal fun GraphicEqBand.frequencyLabel(): String =
    if (frequencyHz >= 1000f) "${String.format(java.util.Locale.US, "%.0f", frequencyHz / 1000f)} kHz" else "${frequencyHz.roundToInt()} Hz"

private fun effectiveDbLabel(requestedDb: Float, effectiveDb: Float): String =
    if (abs(requestedDb - effectiveDb) > AUDIO_EQ_INPUT_STEP_DB / 2f) {
        "eff ${signedDb(effectiveDb)}"
    } else {
        signedDb(effectiveDb)
    }

internal fun signedDb(value: Float): String =
    (if (value > 0f) "+" else "") + String.format(java.util.Locale.US, "%.1f dB", value)

@Composable
private fun BackgroundSettingsScreen(settings: BackgroundSettings, previewAlbum: Album, onSettings: (BackgroundSettings) -> Unit, onBack: () -> Unit) {
    val normalized = settings.normalized()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var colorEditorOpen by rememberSaveable { mutableStateOf(false) }
    var undoSettings by remember { mutableStateOf<BackgroundSettings?>(null) }
    val context = LocalContext.current
    fun matches(title: String, subtitle: String = "", keywords: List<String> = emptyList()): Boolean =
        settingsSearchMatches(SettingsSearchEntry(title, subtitle, keywords), query)
    Box(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(
                title = "Background",
                onBack = onBack,
                onSearch = { searchOpen = !searchOpen },
                onClose = onBack,
            )
            if (searchOpen) {
                SettingsSearchBox(query = query, placeholder = "Search background settings", onQuery = { query = it })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 156.dp), verticalArrangement = Arrangement.spacedBy(21.dp)) {
                item { AnimatedEntrance(0) { BackgroundPreview(previewAlbum, normalized) } }
                if (matches("Enable Blurred Backgrounds", keywords = listOf("background", "blur", "album art"))) {
                    item { AnimatedEntrance(1) { DependentSwitchSetting("Enable Blurred Backgrounds", normalized.blurred, true, "Use album art and palette processing behind the app") { onSettings(normalized.copy(blurred = it).normalized()) } } }
                }
                if (matches("List Background", keywords = listOf("library", "lists", "readability"))) {
                    item { AnimatedEntrance(2) { DependentSwitchSetting("List Background", normalized.listBackground, normalized.blurred, "Lists use stronger dimming for dense text") { onSettings(normalized.copy(listBackground = it).normalized()) } } }
                }
                if (matches("Lyrics Background", keywords = listOf("lyrics", "readability", "dim"))) {
                    item { AnimatedEntrance(3) { DependentSwitchSetting("Lyrics Background", normalized.lyricsBackground, normalized.blurred, "Lyrics are automatically dimmed for continuous reading") { onSettings(normalized.copy(lyricsBackground = it).normalized()) } } }
                }
                if (query.isBlank()) {
                    item { AnimatedEntrance(4) { NoteBlock("Some skins may override background settings. List and lyrics backgrounds are automatically dimmed to keep text readable.") } }
                }
                if (matches("Background Gradient", keywords = listOf("gradient", "overlay", "intensity"))) {
                    item { AnimatedEntrance(5) { DependentSlider("Background Gradient", normalized.gradient.toInt().toString(), "None", "Max", normalized.gradient, 0f..10f, true, { onSettings(normalized.copy(gradient = it)) }) } }
                }
                if (matches("Background Gradient Color", normalized.gradientColor, listOf("color", "hex", "contrast", "swatch"))) {
                    item { AnimatedEntrance(6) { GradientColorRow(previewAlbum, normalized, colorEditorOpen, onToggle = { colorEditorOpen = !colorEditorOpen }, onSettings = onSettings) } }
                }
                if (matches("Background Gradient For Lists", keywords = listOf("gradient", "lists", "library"))) {
                    item { AnimatedEntrance(7) { DependentSwitchSetting("Background Gradient For Lists", normalized.gradientForLists, normalized.blurred && normalized.listBackground, "Also apply the gradient overlay behind list surfaces") { onSettings(normalized.copy(gradientForLists = it).normalized()) } } }
                }
                if (matches("Background Blur", keywords = listOf("blur", "soft", "performance"))) {
                    item { AnimatedEntrance(8) { DependentSlider("Background Blur", normalized.blur.toInt().toString(), "Less", "More", normalized.blur, 0f..10f, normalized.blurred, { onSettings(normalized.copy(blur = it)) }) } }
                }
                if (matches("Background Details", keywords = listOf("details", "solid", "album art", "texture"))) {
                    item { AnimatedEntrance(9) { DependentSlider("Background Details", normalized.details.toInt().toString(), "Solid Color", "Detailed", normalized.details, 0f..10f, normalized.blurred, { onSettings(normalized.copy(details = it)) }) } }
                }
                if (matches("Background Intensity", keywords = listOf("intensity", "opacity", "strength"))) {
                    item { AnimatedEntrance(10) { DependentSlider("Background Intensity", "${(normalized.intensity * 100).toInt()}%", "0%", "100%", normalized.intensity, 0f..1f, normalized.blurred, { onSettings(normalized.copy(intensity = it)) }, "Affects the processed background layer only; UI stays fully opaque.") } }
                }
                if (matches("Background Saturation", keywords = listOf("saturation", "colorful", "grayscale"))) {
                    item { AnimatedEntrance(11) { DependentSlider("Background Saturation", "${(normalized.saturation * 100).toInt()}%", "0%", "200%", normalized.saturation, 0f..2f, normalized.blurred, { onSettings(normalized.copy(saturation = it)) }, "High saturation increases the automatic readability scrim.") } }
                }
                if (matches("Readability Protection", keywords = listOf("contrast", "wcag", "text", "lyrics", "lists"))) {
                    item { AnimatedEntrance(12) { ReadabilityProtectionPanel(normalized, onSettings) } }
                }
                item {
                    AnimatedEntrance(13) {
                        SleepDialogButton(
                            "Restore Defaults",
                            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 42.dp),
                            tone = Color.White.copy(alpha = 0.08f),
                        ) {
                            undoSettings = normalized
                            onSettings(BackgroundSettings())
                            Toast.makeText(context, "Restored to defaults", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        undoSettings?.let { previous ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF25262C))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Background settings restored.", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                SleepDialogButton("Undo", tone = Blue.copy(alpha = 0.30f)) {
                    onSettings(previous)
                    undoSettings = null
                }
            }
        }
    }
}

@Composable
private fun DependentSwitchSetting(title: String, checked: Boolean, enabled: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (checked && enabled) Blue.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.050f))
            .border(1.dp, Color.White.copy(alpha = if (checked && enabled) 0.13f else 0.055f), RoundedCornerShape(18.dp))
            .then(if (enabled) Modifier.pressScaleEffect(interactionSource).clickable(interactionSource = interactionSource, indication = null) { onChange(!checked) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 1f else 0.36f), fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) Text(subtitle, color = Muted.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(if (checked && enabled) "ON" else "OFF", color = if (checked && enabled) Green else Muted.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 8.dp))
        PulseToggle(checked = checked && enabled, enabled = enabled, onChange = onChange)
    }
}

@Composable
private fun DependentSlider(
    title: String,
    valueText: String,
    left: String,
    right: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    footer: String? = null,
) {
    SettingSurface(active = enabled) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 1f else 0.36f), fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                valueText,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(Blue.copy(alpha = if (enabled) 0.22f else 0.08f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, color = Muted.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(right, color = Muted.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Slider(
            value = value,
            enabled = enabled,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                disabledThumbColor = Color.White.copy(alpha = 0.32f),
                activeTrackColor = Blue,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.16f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.08f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
        footer?.let { Text(it, color = Muted.copy(alpha = if (enabled) 0.72f else 0.36f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun GradientColorRow(album: Album, settings: BackgroundSettings, editorOpen: Boolean, onToggle: () -> Unit, onSettings: (BackgroundSettings) -> Unit) {
    var hex by remember(settings.gradientColor) { mutableStateOf(settings.gradientColor) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Background Gradient Color", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(settings.gradientColor, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .size(width = 64.dp, height = 38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(colorFromHex(settings.gradientColor))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(19.dp))
                    .clickable(onClick = onToggle),
            )
        }
        Spacer(Modifier.height(12.dp))
        BackgroundContrastPreview(album, settings.copy(gradientColor = hex).normalized())
        if (editorOpen) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = hex,
                    onValueChange = { hex = it.take(7) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black),
                    modifier = Modifier.weight(1f),
                )
                SleepDialogButton("Apply", tone = Blue.copy(alpha = 0.32f)) { onSettings(settings.copy(gradientColor = hex).normalized()) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("#000000", "#1B1C22", "#243B55", "#3B1D45").forEach { preset ->
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colorFromHex(preset))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .clickable {
                                hex = preset
                                onSettings(settings.copy(gradientColor = preset).normalized())
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundContrastPreview(album: Album, settings: BackgroundSettings) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
    ) {
        Background(album, settings, BackgroundUsage.List)
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp)) {
            Text("Contrast Preview", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("Sample list and lyrics text remain readable.", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReadabilityProtectionPanel(settings: BackgroundSettings, onSettings: (BackgroundSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsSectionTitle("Readability Protection", "Dynamic backgrounds dim themselves behind dense text")
        DependentSwitchSetting("Enable readability protection", settings.readabilityProtection.enabled, true) {
            onSettings(settings.copy(readabilityProtection = settings.readabilityProtection.copy(enabled = it)).normalized())
        }
        DependentSwitchSetting("Auto-dim lists", settings.readabilityProtection.autoDimLists, settings.readabilityProtection.enabled) {
            onSettings(settings.copy(readabilityProtection = settings.readabilityProtection.copy(autoDimLists = it)).normalized())
        }
        DependentSwitchSetting("Auto-dim lyrics", settings.readabilityProtection.autoDimLyrics, settings.readabilityProtection.enabled) {
            onSettings(settings.copy(readabilityProtection = settings.readabilityProtection.copy(autoDimLyrics = it)).normalized())
        }
        Text("Target: ${settings.readabilityProtection.minimumTextContrast}", color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
    }
}

