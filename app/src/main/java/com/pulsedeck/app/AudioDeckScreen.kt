package com.pulsedeck.app

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.audio.NativeAudioEngineBridge
import com.pulsedeck.app.audio.NativeAudioInitializationState
import com.pulsedeck.app.audio.nativeAudioActivationFor
import com.pulsedeck.app.settings.runtime.OutputDiagnosticsSnapshot
import com.pulsedeck.app.settings.runtime.PulseOutputDiagnosticsStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class AudioDeckTab(val title: String, val icon: DeckIcon) {
    Equalizer("EQ", DeckIcon.Equalizer),
    Tone("Tone", DeckIcon.Timer),
    Effects("Effects", DeckIcon.Visualizer),
    Native("Native", DeckIcon.Lab),
    Chain("Chain", DeckIcon.Signal),
    Experiences("Listen", DeckIcon.Headphones),
}

private val AudioDeckVisibleTabs = listOf(
    AudioDeckTab.Equalizer,
    AudioDeckTab.Tone,
    AudioDeckTab.Effects,
    AudioDeckTab.Native,
    AudioDeckTab.Chain,
    AudioDeckTab.Experiences,
)

@Composable
internal fun AudioDeckScreen(
    state: AudioEngineState,
    currentTrack: Track,
    positionMillis: Long,
    onState: (AudioEngineState) -> Unit,
    onPreset: (AudioPreset) -> Unit,
    onAdvanced: () -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AudioDeckTab.Equalizer) }
    val selectedTab = if (tab in AudioDeckVisibleTabs) tab else AudioDeckTab.Equalizer
    var experiencePreviewOriginal by remember { mutableStateOf<AudioEngineState?>(null) }
    var experiencePreviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var keptExperienceOriginal by remember { mutableStateOf<AudioEngineState?>(null) }
    var keptExperienceId by rememberSaveable { mutableStateOf<String?>(null) }
    val outputDiagnostics by PulseOutputDiagnosticsStore.diagnostics.collectAsState()
    val quality = rememberTrackQuality(currentTrack)
    val chainSnapshot = remember(state, currentTrack.stableKey(), quality, outputDiagnostics) {
        audioChainSnapshot(
            state = state,
            track = currentTrack,
            quality = quality,
            diagnostics = outputDiagnostics,
            nativeAvailable = NativeAudioEngineBridge.isAvailable,
        )
    }
    val dspGraph = remember(state, outputDiagnostics) {
        dspGraphSnapshot(
            state = state,
            diagnostics = outputDiagnostics,
            nativeAvailable = NativeAudioEngineBridge.isAvailable,
        )
    }
    val activeProExperience = activeProListeningExperience(state)
    LaunchedEffect(state, experiencePreviewOriginal, keptExperienceId, activeProExperience?.id) {
        if (experiencePreviewOriginal == null && keptExperienceId != null && activeProExperience?.id?.name != keptExperienceId) {
            keptExperienceOriginal = null
            keptExperienceId = null
        }
    }
    val activePreset = pulseAudioPresets.firstOrNull { it.id == state.activePresetId } ?: pulseAudioPresets.first()
    val headroomDb = if (state.preventClipping) -max(0f, state.maxBoostDb) else 0f
    fun moveTab(direction: Int) {
        val currentIndex = AudioDeckVisibleTabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + direction).coerceIn(0, AudioDeckVisibleTabs.lastIndex)
        if (nextIndex != currentIndex) {
            tab = AudioDeckVisibleTabs[nextIndex]
        }
    }
    fun previewExperience(experienceId: ProListeningExperienceId) {
        val original = experiencePreviewOriginal
            ?: keptExperienceOriginal?.takeIf { keptExperienceId == experienceId.name && activeProExperience?.id == experienceId }
            ?: state
        experiencePreviewOriginal = original
        experiencePreviewId = experienceId.name
        onState(proListeningExperiencePlan(original, experienceId, outputDiagnostics).previewState)
    }
    fun keepExperiencePreview() {
        keptExperienceOriginal = experiencePreviewOriginal
        keptExperienceId = experiencePreviewId
        experiencePreviewOriginal = null
        experiencePreviewId = null
    }
    fun revertExperiencePreview() {
        (experiencePreviewOriginal ?: keptExperienceOriginal)?.let(onState)
        experiencePreviewOriginal = null
        experiencePreviewId = null
        keptExperienceOriginal = null
        keptExperienceId = null
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF030713),
                        Color(0xFF050A16),
                        Color.Black,
                    ),
                ),
            )
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 168.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AnimatedEntrance(0) {
                AudioDeckHeader(
                    state = state,
                    activePreset = activePreset,
                    headroomDb = headroomDb,
                    onBack = onBack,
                    onAdvanced = onAdvanced,
                    onPower = { onState(if (state.processingActive) state.copy(enabled = false, bypass = false) else state.copy(enabled = true, bypass = false)) },
                )
            }
        }
        item {
            AnimatedEntrance(1) {
                AudioDeckTabs(
                    selected = selectedTab,
                    onTab = { tab = it },
                    onSwipePrevious = { moveTab(-1) },
                    onSwipeNext = { moveTab(1) },
                )
            }
        }
        when (selectedTab) {
            AudioDeckTab.Experiences -> audioDeckExperienceItems(
                state = state,
                diagnostics = outputDiagnostics,
                previewOriginal = experiencePreviewOriginal,
                previewExperienceId = experiencePreviewId,
                keptExperienceOriginal = keptExperienceOriginal,
                keptExperienceId = keptExperienceId,
                onPreview = ::previewExperience,
                onKeep = ::keepExperiencePreview,
                onRevert = ::revertExperiencePreview,
                onOpenChain = { tab = AudioDeckTab.Chain },
            )
            AudioDeckTab.Equalizer -> audioDeckEqualizerItems(state, activePreset, headroomDb, onState, onPreset)
            AudioDeckTab.Tone -> audioDeckToneItems(state, onState)
            AudioDeckTab.Effects -> audioDeckEffectsItems(state, onState)
            AudioDeckTab.Native -> audioDeckNativeItems(state, onState)
            AudioDeckTab.Chain -> audioDeckChainItems(chainSnapshot, dspGraph)
        }
    }
}

@Composable
private fun AudioDeckHeader(
    state: AudioEngineState,
    activePreset: AudioPreset,
    headroomDb: Float,
    onBack: () -> Unit,
    onAdvanced: () -> Unit,
    onPower: () -> Unit,
) {
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTap(DeckIcon.Back, onBack, 36.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                "Equalizer",
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                AudioDeckEngineToggle(active = state.processingActive, onClick = onPower)
                NeonRoundAction(DeckIcon.Info, if (infoOpen) "Hide engine information" else "Show engine information") {
                    infoOpen = !infoOpen
                }
                NeonRoundAction(DeckIcon.Settings, "Advanced audio settings", onAdvanced)
            }
        }
        if (infoOpen) {
            AudioDeckInfoPanel(state = state, activePreset = activePreset, headroomDb = headroomDb)
        }
    }
}

@Composable
private fun AudioDeckTabs(
    selected: AudioDeckTab,
    onTab: (AudioDeckTab) -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
) {
    var swipeAccumulation by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(selected) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeAccumulation = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        swipeAccumulation += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            swipeAccumulation <= -56f -> onSwipeNext()
                            swipeAccumulation >= 56f -> onSwipePrevious()
                        }
                        swipeAccumulation = 0f
                    },
                    onDragCancel = { swipeAccumulation = 0f },
                )
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.070f), Color.White.copy(alpha = 0.032f))))
            .border(1.dp, Color.White.copy(alpha = 0.085f), RoundedCornerShape(20.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AudioDeckVisibleTabs.forEach { tab ->
            val active = tab == selected
            val interactionSource = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .weight(if (active) 1.42f else 0.62f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) audioDeckActiveBrush() else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.024f), Color.Transparent)))
                    .border(1.dp, if (active) AudioDeckBlueLight.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                    .pressScaleEffect(interactionSource)
                    .semantics {
                        role = Role.Tab
                        contentDescription = tab.title
                        stateDescription = if (active) "Selected" else "Not selected"
                    }
                    .clickable(interactionSource = interactionSource, indication = null) { onTab(tab) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIcon(tab.icon, if (active) AudioDeckBlueLight else Color.White.copy(alpha = 0.56f), Modifier.size(15.dp))
                if (active) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        tab.title,
                        color = AudioDeckBlueLight,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioDeckEqualizerItems(
    state: AudioEngineState,
    activePreset: AudioPreset,
    headroomDb: Float,
    onState: (AudioEngineState) -> Unit,
    onPreset: (AudioPreset) -> Unit,
) {
    val graphicBands = canonicalGraphicEqBands(state.eqBands, state.graphicEqRangeMode.requestedGainRange())
    val nativeActivation = nativeAudioActivationFor(state, NativeAudioEngineBridge.isAvailable)
    val graphicEqGainRange = state.effectiveGraphicEqGainRange(nativeActivation.media3DspActive)
    val extendedRangeRequested = state.graphicEqRangeMode == GraphicEqRangeMode.Extended15DbWhenSupported
    val extendedRangeEffective = state.graphicEqExtendedRangeEffective(nativeActivation.media3DspActive)
    val rangeStatus = when {
        extendedRangeEffective -> "+/-15 Native DSP"
        extendedRangeRequested -> "+/-15 pending Native DSP; +/-12 effective"
        else -> "+/-12 standard"
    }
    item {
        AnimatedEntrance(2, animate = false) {
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Graphic EQ", color = Color(0xFFF4F6F5), fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                        Text("${graphicBands.size}-band  |  ${if (state.eqEnabled) "active" else "off"}  |  $rangeStatus", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    AudioDeckSavedPill(saved = !state.presetModified)
                    Spacer(Modifier.width(6.dp))
                    AudioDeckMiniSwitch("+/-15", extendedRangeRequested) {
                        onState(state.withGraphicEqRangeMode(if (it) GraphicEqRangeMode.Extended15DbWhenSupported else GraphicEqRangeMode.Standard12Db))
                    }
                    Spacer(Modifier.width(6.dp))
                    AudioDeckMiniSwitch("EQ", state.eqEnabled) { onState(state.copy(eqEnabled = it)) }
                }
                AudioDeckCleanEqPanel(
                    Modifier
                        .fillMaxWidth()
                        .height(242.dp),
                    padding = 6.dp,
                ) {
                    AudioDeckEqFaderBank(
                        state = state,
                        graphicBands = graphicBands,
                        graphicEqGainRange = graphicEqGainRange,
                        onState = onState,
                        Modifier.fillMaxSize(),
                    )
                }
                AudioEqCurveGraph(state = state, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
    item {
        AnimatedEntrance(3, animate = false) {
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    AudioDeckBypassControl(
                        active = state.bypass,
                        modifier = Modifier.weight(1.15f),
                    ) {
                        onState(state.copy(bypass = !state.bypass))
                    }
                    AudioDeckSquareControl("Flat", DeckIcon.Wave, Modifier.weight(0.75f)) {
                        pulseAudioPresets.firstOrNull { it.id == "flat" }?.let(onPreset)
                    }
                    AudioDeckSquareControl("Reset", DeckIcon.Repeat, Modifier.weight(0.75f)) {
                        onState(state.withAudioDeckEqPageReset())
                    }
                    AudioDeckSquareControl("Limiter", DeckIcon.Focus, Modifier.weight(0.75f), active = state.limiter.enabled) {
                        onState(state.copy(limiter = state.limiter.copy(enabled = !state.limiter.enabled)))
                    }
                }
                Text("Preset  |  ${activePreset.name}", color = AudioDeckBlueLight, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 2.dp)) {
                    items(pulseAudioPresets, key = { it.id }) { preset ->
                        AudioDeckPresetChip(preset, selected = preset.id == state.activePresetId && !state.presetModified) { onPreset(preset) }
                    }
                }
            }
        }
    }
    item {
        AnimatedEntrance(4, animate = false) {
            var parametricOpen by rememberSaveable { mutableStateOf(false) }
            AudioDeckCleanEqCard {
                AudioDeckPillButton(
                    label = "Parametric EQ",
                    icon = DeckIcon.Sliders,
                    modifier = Modifier.fillMaxWidth(),
                    active = parametricOpen || state.parametricEq.any { it.enabled },
                ) {
                    parametricOpen = !parametricOpen
                }
                if (parametricOpen) {
                    AudioDeckParametricEqDrawer(state, onState)
                }
            }
        }
    }
    item {
        AnimatedEntrance(5, animate = false) {
            val native = state.native
            val nativeCanInitialize = nativeEngineCanInitialize()
            val context = LocalContext.current
            val nativeUnavailableMessage = nativeUnavailableToastMessage()
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Native Engine", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                        Text(
                            nativeEngineStatusText(),
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AudioDeckMiniSwitch("DSP", native.enabled && native.media3DspEnabled, enabled = true) {
                        if (nativeCanInitialize) {
                            onState(state.copy(native = native.copy(enabled = it || native.enabled, media3DspEnabled = it)))
                        } else {
                            Toast.makeText(context, nativeUnavailableMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AudioDeckLiquidPillButton(if (native.enabled) "Native On" else "Native", DeckIcon.Lab, Modifier.weight(1f), active = native.enabled, enabled = true) {
                                if (nativeCanInitialize) {
                                    onState(state.copy(native = native.copy(enabled = !native.enabled)))
                                } else {
                                    Toast.makeText(context, nativeUnavailableMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                            AudioDeckLiquidPillButton(if (native.media3DspEnabled) "DSP On" else "DSP", DeckIcon.Signal, Modifier.weight(1f), active = native.media3DspEnabled, enabled = true) {
                                if (nativeCanInitialize) {
                                    val enabled = !native.media3DspEnabled
                                    onState(state.copy(native = native.copy(enabled = native.enabled || enabled, media3DspEnabled = enabled)))
                                } else {
                                    Toast.makeText(context, nativeUnavailableMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AudioDeckLiquidPillButton("A/B ${native.activeCompareSlot.label}", DeckIcon.Equalizer, Modifier.weight(1f), active = native.activeCompareSlot != NativeCompareSlot.Off) {
                                val nextSlot = when (native.activeCompareSlot) {
                                    NativeCompareSlot.Off -> NativeCompareSlot.A
                                    NativeCompareSlot.A -> NativeCompareSlot.B
                                    NativeCompareSlot.B -> NativeCompareSlot.Off
                                }
                                val slot = when (nextSlot) {
                                    NativeCompareSlot.A -> native.compareSlotA
                                    NativeCompareSlot.B -> native.compareSlotB
                                    NativeCompareSlot.Off -> null
                                }
                                onState(if (slot == null) state.copy(native = native.copy(activeCompareSlot = NativeCompareSlot.Off)) else state.withNativePresetSlot(slot, nextSlot))
                            }
                            AudioDeckLiquidPillButton("Save A", DeckIcon.Plus, Modifier.weight(1f), active = native.activeCompareSlot == NativeCompareSlot.A) {
                                onState(state.copy(native = native.copy(compareSlotA = state.toNativePresetSlot("A"), activeCompareSlot = NativeCompareSlot.A)))
                            }
                            AudioDeckLiquidPillButton("Save B", DeckIcon.Plus, Modifier.weight(1f), active = native.activeCompareSlot == NativeCompareSlot.B) {
                                onState(state.copy(native = native.copy(compareSlotB = state.toNativePresetSlot("B"), activeCompareSlot = NativeCompareSlot.B)))
                            }
                        }
                        AudioDeckInlineSlider("Native Master", "${(native.masterGain * 100f).roundToInt()}%", "0", "150", native.masterGain, 0f..1.5f, enabled = true, liquid = true) {
                            onState(state.copy(native = native.copy(masterGain = it)))
                        }
                }
            }
        }
    }
    item {
        AnimatedEntrance(6, animate = false) {
            AudioDeckCleanEqCard {
                Text("Tone", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, start = 2.dp, end = 2.dp)
                        .clipToBounds(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ToneKnobCell(
                        label = "Bass",
                        value = state.bassDb,
                        range = -12f..12f,
                        valueText = signedDb(state.bassDb),
                        onChange = { onState(state.copy(bassDb = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    ToneKnobCell(
                        label = "Treble",
                        value = state.trebleDb,
                        range = -12f..12f,
                        valueText = signedDb(state.trebleDb),
                        onChange = { onState(state.copy(trebleDb = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    ToneKnobCell(
                        label = "Vocal Clarity",
                        value = state.vocalClarityDb,
                        range = -12f..12f,
                        valueText = signedDb(state.vocalClarityDb),
                        onChange = { onState(state.copy(vocalClarityDb = it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    item {
        AnimatedEntrance(7, animate = false) {
            AudioDeckLiquidPillButton("Reset EQ Page", DeckIcon.Repeat, Modifier.fillMaxWidth()) {
                onState(state.withAudioDeckEqPageReset())
            }
        }
    }
    item {
        Text(
            "Auto headroom ${signedDb(headroomDb)}",
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioDeckToneItems(
    state: AudioEngineState,
    onState: (AudioEngineState) -> Unit,
) {
    item {
        AnimatedEntrance(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NeonKnob(
                    label = "Balance",
                    value = state.stereo.balance,
                    range = -1f..1f,
                    valueText = String.format(Locale.US, "%.2f", state.stereo.balance),
                    onChange = { onState(state.copy(stereo = state.stereo.copy(balance = it))) },
                    modifier = Modifier.weight(1f),
                    knobSize = 142.dp,
                )
                NeonKnob(
                    label = "Stereo Expand",
                    value = state.stereo.stereoWidth,
                    range = 0f..1f,
                    valueText = "${(state.stereo.stereoWidth * 100f).roundToInt()}%",
                    onChange = { onState(state.copy(stereo = state.stereo.copy(stereoWidth = it))) },
                    modifier = Modifier.weight(1f),
                    knobSize = 142.dp,
                )
            }
        }
    }
    item {
        AnimatedEntrance(3) {
            AudioDeckCleanEqCard(padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AudioDeckPillButton("Tempo", DeckIcon.Timer, Modifier.width(92.dp), active = abs(state.tempoPitch.tempo - 1f) > 0.01f) {
                        onState(state.copy(tempoPitch = state.tempoPitch.copy(tempo = 1f)))
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(String.format(Locale.US, "%.2fx", state.tempoPitch.tempo), color = AudioDeckBlueLight, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                        Text("Pitch ${state.tempoPitch.pitchSemitones.roundToInt()} st", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    NeonKnob(
                        label = "Tempo",
                        value = state.tempoPitch.tempo,
                        range = 0.5f..2f,
                        valueText = String.format(Locale.US, "%.2fx", state.tempoPitch.tempo),
                        onChange = { onState(state.copy(tempoPitch = state.tempoPitch.copy(tempo = it))) },
                        modifier = Modifier.width(128.dp),
                        knobSize = 128.dp,
                    )
                }
                AudioDeckInlineSlider("Pitch", "${state.tempoPitch.pitchSemitones.roundToInt()} st", "-12", "+12", state.tempoPitch.pitchSemitones, -12f..12f) {
                    onState(state.copy(tempoPitch = state.tempoPitch.copy(pitchSemitones = it)))
                }
            }
        }
    }
    item {
        AnimatedEntrance(4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AudioDeckPillButton(if (state.stereo.mono) "Mono On" else "Mono", DeckIcon.Headphones, Modifier.weight(1f), active = state.stereo.mono) {
                    onState(state.copy(stereo = state.stereo.copy(mono = !state.stereo.mono)))
                }
                AudioDeckPillButton("Reset", DeckIcon.Repeat, Modifier.weight(1f)) {
                    onState(state.copy(stereo = StereoState(), tempoPitch = TempoPitchState()))
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioDeckEffectsItems(
    state: AudioEngineState,
    onState: (AudioEngineState) -> Unit,
) {
    val reverb = state.reverb
    item {
        AnimatedEntrance(2) {
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonKnob(
                        label = "Damp",
                        value = reverb.damp,
                        range = 0f..1f,
                        valueText = String.format(Locale.US, "%.2f", reverb.damp),
                        onChange = { onState(state.copy(reverb = reverb.copy(damp = it, enabled = true))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Size",
                        value = reverb.size,
                        range = 0f..1f,
                        valueText = String.format(Locale.US, "%.2f", reverb.size),
                        onChange = { onState(state.copy(reverb = reverb.copy(size = it, enabled = true))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Fade",
                        value = reverb.decay,
                        range = 0.2f..6f,
                        valueText = String.format(Locale.US, "%.2f", reverb.decay),
                        onChange = { onState(state.copy(reverb = reverb.copy(decay = it, enabled = true))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonKnob(
                        label = "Pre-Delay",
                        value = reverb.preDelayMs,
                        range = 0f..120f,
                        valueText = String.format(Locale.US, "%.2f", reverb.preDelayMs),
                        onChange = { onState(state.copy(reverb = reverb.copy(preDelayMs = it, enabled = true))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Crossfeed",
                        value = state.stereo.crossfeed,
                        range = 0f..1f,
                        valueText = String.format(Locale.US, "%.2f", state.stereo.crossfeed),
                        onChange = { onState(state.copy(stereo = state.stereo.copy(crossfeed = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Mix",
                        value = reverb.mix,
                        range = 0f..1f,
                        valueText = String.format(Locale.US, "%.2f", reverb.mix),
                        onChange = { onState(state.copy(reverb = reverb.copy(mix = it, enabled = true))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    NeonKnob(
                        label = "Loudness",
                        value = state.loudnessDb,
                        range = 0f..12f,
                        valueText = String.format(Locale.US, "%.2f", state.loudnessDb),
                        onChange = { onState(state.copy(loudnessDb = it)) },
                        modifier = Modifier.width(122.dp),
                        knobSize = 112.dp,
                    )
                }
            }
        }
    }
    item {
        AnimatedEntrance(3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AudioDeckPillButton(if (reverb.enabled) "Reverb On" else "Reverb", DeckIcon.Equalizer, Modifier.weight(1f), active = reverb.enabled) {
                    onState(state.copy(reverb = reverb.copy(enabled = !reverb.enabled)))
                }
                AudioDeckPillButton("Loudness", DeckIcon.Loudness, Modifier.weight(1f), active = state.loudnessDb > 0.05f) {
                    onState(state.copy(loudnessDb = if (state.loudnessDb > 0.05f) 0f else 6f))
                }
                AudioDeckPillButton("Reset", DeckIcon.Repeat, Modifier.weight(1f)) {
                    onState(state.copy(reverb = ReverbState(), stereo = state.stereo.copy(crossfeed = 0f), loudnessDb = 0f))
                }
            }
        }
    }
    item {
        AnimatedEntrance(4) {
            var dynamicsDrawer by rememberSaveable { mutableStateOf("none") }
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Native Dynamics", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                        Text("Compressor and gate parameters", color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    AudioDeckDspBadge(audioDeckRouteLabel(state, nativeOnly = true), active = state.compressor.enabled || state.gate.enabled || state.limiter.enabled, warning = !NativeAudioEngineBridge.isAvailable && (state.compressor.enabled || state.gate.enabled))
                    Spacer(Modifier.width(8.dp))
                    AudioDeckMiniSwitch("Comp", state.compressor.enabled) {
                        onState(state.copy(compressor = state.compressor.copy(enabled = it, mix = if (it && state.compressor.mix == 0f) 0.65f else state.compressor.mix)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonKnob(
                        label = "Threshold",
                        value = state.compressor.thresholdDb,
                        range = -60f..0f,
                        valueText = signedDb(state.compressor.thresholdDb),
                        onChange = { onState(state.copy(compressor = state.compressor.copy(thresholdDb = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Ratio",
                        value = state.compressor.ratio,
                        range = 1f..20f,
                        valueText = String.format(Locale.US, "%.1f:1", state.compressor.ratio),
                        onChange = { onState(state.copy(compressor = state.compressor.copy(ratio = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Mix",
                        value = state.compressor.mix,
                        range = 0f..1f,
                        valueText = "${(state.compressor.mix * 100f).roundToInt()}%",
                        onChange = { onState(state.copy(compressor = state.compressor.copy(mix = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioDeckPillButton(if (state.gate.enabled) "Gate On" else "Gate", DeckIcon.Focus, Modifier.weight(1f), active = state.gate.enabled) {
                        onState(state.copy(gate = state.gate.copy(enabled = !state.gate.enabled)))
                    }
                    AudioDeckPillButton("Fast", DeckIcon.Timer, Modifier.weight(1f), active = state.compressor.attackMs <= 10f) {
                        onState(state.copy(compressor = state.compressor.copy(attackMs = 8f, releaseMs = 120f)))
                    }
                    AudioDeckPillButton("Smooth", DeckIcon.Wave, Modifier.weight(1f), active = state.compressor.attackMs > 10f) {
                        onState(state.copy(compressor = state.compressor.copy(attackMs = 28f, releaseMs = 260f)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioDeckPillButton("Reset", DeckIcon.Repeat, Modifier.weight(1f)) {
                        onState(state.copy(compressor = CompressorState(), gate = GateState(), limiter = LimiterState()))
                    }
                    Spacer(Modifier.weight(1f))
                }
                AudioDeckDynamicsAdvancedDrawers(
                    state = state,
                    onState = onState,
                    selected = dynamicsDrawer,
                    onSelected = { dynamicsDrawer = if (dynamicsDrawer == it) "none" else it },
                )
            }
        }
    }
    item {
        AnimatedEntrance(5) {
            var modulationOpen by rememberSaveable { mutableStateOf(false) }
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Native Ambience", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                        Text("Delay, chorus, flanger, and phaser", color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    AudioDeckDspBadge(audioDeckRouteLabel(state, nativeOnly = true), active = state.delay.enabled || state.reverb.enabled || state.modulation.chorusEnabled || state.modulation.flangerEnabled || state.modulation.phaserEnabled, warning = !NativeAudioEngineBridge.isAvailable && (state.delay.enabled || state.reverb.enabled || state.modulation.chorusEnabled || state.modulation.flangerEnabled || state.modulation.phaserEnabled))
                    Spacer(Modifier.width(8.dp))
                    AudioDeckMiniSwitch("Delay", state.delay.enabled) {
                        onState(state.copy(delay = state.delay.copy(enabled = it, mix = if (it && state.delay.mix == 0f) 0.2f else state.delay.mix)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonKnob(
                        label = "Time",
                        value = state.delay.timeMs,
                        range = 1f..1800f,
                        valueText = "${state.delay.timeMs.roundToInt()} ms",
                        onChange = { onState(state.copy(delay = state.delay.copy(timeMs = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Feedback",
                        value = state.delay.feedback,
                        range = 0f..0.92f,
                        valueText = "${(state.delay.feedback * 100f).roundToInt()}%",
                        onChange = { onState(state.copy(delay = state.delay.copy(feedback = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Delay Mix",
                        value = state.delay.mix,
                        range = 0f..1f,
                        valueText = "${(state.delay.mix * 100f).roundToInt()}%",
                        onChange = { onState(state.copy(delay = state.delay.copy(mix = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                }
                AudioDeckModulationAdvancedDrawer(
                    state = state,
                    onState = onState,
                    expanded = modulationOpen,
                    onExpanded = { modulationOpen = !modulationOpen },
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioDeckPillButton("Chorus", DeckIcon.Wave, Modifier.weight(1f), active = state.modulation.chorusEnabled) {
                        onState(state.copy(modulation = state.modulation.copy(chorusEnabled = !state.modulation.chorusEnabled, mix = if (!state.modulation.chorusEnabled && state.modulation.mix == 0f) 0.2f else state.modulation.mix)))
                    }
                    AudioDeckPillButton("Flanger", DeckIcon.Visualizer, Modifier.weight(1f), active = state.modulation.flangerEnabled) {
                        onState(state.copy(modulation = state.modulation.copy(flangerEnabled = !state.modulation.flangerEnabled, mix = if (!state.modulation.flangerEnabled && state.modulation.mix == 0f) 0.18f else state.modulation.mix)))
                    }
                    AudioDeckPillButton("Phaser", DeckIcon.Signal, Modifier.weight(1f), active = state.modulation.phaserEnabled) {
                        onState(state.copy(modulation = state.modulation.copy(phaserEnabled = !state.modulation.phaserEnabled, mix = if (!state.modulation.phaserEnabled && state.modulation.mix == 0f) 0.18f else state.modulation.mix)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonKnob(
                        label = "Rate",
                        value = state.modulation.rateHz,
                        range = 0.02f..8f,
                        valueText = String.format(Locale.US, "%.2f Hz", state.modulation.rateHz),
                        onChange = { onState(state.copy(modulation = state.modulation.copy(rateHz = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Depth",
                        value = state.modulation.depth,
                        range = 0f..1f,
                        valueText = "${(state.modulation.depth * 100f).roundToInt()}%",
                        onChange = { onState(state.copy(modulation = state.modulation.copy(depth = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                    NeonKnob(
                        label = "Mod Mix",
                        value = state.modulation.mix,
                        range = 0f..1f,
                        valueText = "${(state.modulation.mix * 100f).roundToInt()}%",
                        onChange = { onState(state.copy(modulation = state.modulation.copy(mix = it))) },
                        modifier = Modifier.weight(1f),
                        knobSize = 106.dp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioDeckPillButton("Reset", DeckIcon.Repeat, Modifier.weight(1f)) {
                        onState(state.copy(delay = DelayState(), modulation = ModulationState(), reverb = ReverbState()))
                    }
                    AudioDeckPillButton("Dry", DeckIcon.Signal, Modifier.weight(1f)) {
                        onState(state.copy(delay = state.delay.copy(enabled = false, mix = 0f), modulation = ModulationState(), reverb = state.reverb.copy(enabled = false, mix = 0f)))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioDeckNativeItems(
    state: AudioEngineState,
    onState: (AudioEngineState) -> Unit,
) {
    val native = state.native
    val nativeCanInitialize = nativeEngineCanInitialize()
    item {
        AnimatedEntrance(2) {
            val context = LocalContext.current
            val nativeUnavailableMessage = nativeUnavailableToastMessage()
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Native Audio Engine", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                        Text(
                            nativeEngineStatusText(),
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AudioDeckMiniSwitch("Native", native.enabled, enabled = true) {
                        if (nativeCanInitialize) {
                            onState(state.copy(native = native.copy(enabled = it)))
                        } else {
                            Toast.makeText(context, nativeUnavailableMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AudioDeckLiquidPillButton("Media3 DSP", DeckIcon.Signal, Modifier.weight(1f), active = native.media3DspEnabled, enabled = true) {
                                if (nativeCanInitialize) {
                                    val enabled = !native.media3DspEnabled
                                    onState(state.copy(native = native.copy(enabled = native.enabled || enabled, media3DspEnabled = enabled)))
                                } else {
                                    Toast.makeText(context, nativeUnavailableMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                            AudioDeckLiquidPillButton("Test Tone", DeckIcon.Wave, Modifier.weight(1f), active = native.lowLatencyToneEnabled, enabled = true) {
                                if (nativeCanInitialize) {
                                    onState(state.copy(native = native.copy(enabled = true, lowLatencyToneEnabled = !native.lowLatencyToneEnabled)))
                                } else {
                                    Toast.makeText(context, nativeUnavailableMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                            AudioDeckLiquidPillButton("Reset", DeckIcon.Repeat, Modifier.weight(1f)) {
                                onState(state.copy(native = NativeAudioSettings()))
                            }
                        }
                        Text(
                            if (state.processingActive) "Live when Native and Media3 DSP are enabled." else "Audio engine is off; values are saved but not applied live.",
                            color = if (state.processingActive) AudioDeckBlueLight.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        AudioDeckInlineSlider("Native Master", "${(native.masterGain * 100f).roundToInt()}%", "0", "150", native.masterGain, 0f..1.5f, enabled = true, liquid = true) {
                            onState(state.copy(native = native.copy(masterGain = it)))
                        }
                        AudioDeckInlineSlider("Tone Frequency", "${native.sourceFrequencyHz.roundToInt()} Hz", "20", "4k", native.sourceFrequencyHz, 20f..4000f, enabled = nativeCanInitialize, liquid = true) {
                            onState(state.copy(native = native.copy(sourceFrequencyHz = it)))
                        }
                        AudioDeckInlineSlider("Tone Gain", "${(native.sourceGain * 100f).roundToInt()}%", "Muted", "Safe", native.sourceGain, 0f..0.4f, enabled = nativeCanInitialize, liquid = true) {
                            onState(state.copy(native = native.copy(sourceGain = it)))
                        }
                }
            }
        }
    }
    item {
        AnimatedEntrance(3) {
            AudioDeckCleanEqCard {
                Text("A/B Compare", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioDeckPillButton("Use ${native.activeCompareSlot.label}", DeckIcon.Equalizer, Modifier.weight(1f), active = native.activeCompareSlot != NativeCompareSlot.Off) {
                        val nextSlot = when (native.activeCompareSlot) {
                            NativeCompareSlot.Off -> NativeCompareSlot.A
                            NativeCompareSlot.A -> NativeCompareSlot.B
                            NativeCompareSlot.B -> NativeCompareSlot.Off
                        }
                        val slot = when (nextSlot) {
                            NativeCompareSlot.A -> native.compareSlotA
                            NativeCompareSlot.B -> native.compareSlotB
                            NativeCompareSlot.Off -> null
                        }
                        onState(if (slot == null) state.copy(native = native.copy(activeCompareSlot = NativeCompareSlot.Off)) else state.withNativePresetSlot(slot, nextSlot))
                    }
                    AudioDeckPillButton("Save A", DeckIcon.Plus, Modifier.weight(1f), active = native.activeCompareSlot == NativeCompareSlot.A) {
                        onState(state.copy(native = native.copy(compareSlotA = state.toNativePresetSlot("A"), activeCompareSlot = NativeCompareSlot.A)))
                    }
                    AudioDeckPillButton("Save B", DeckIcon.Plus, Modifier.weight(1f), active = native.activeCompareSlot == NativeCompareSlot.B) {
                        onState(state.copy(native = native.copy(compareSlotB = state.toNativePresetSlot("B"), activeCompareSlot = NativeCompareSlot.B)))
                    }
                }
            }
        }
    }
    item {
        AnimatedEntrance(4) {
            var dynamicsDrawer by rememberSaveable { mutableStateOf("none") }
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dynamics", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                        Text("Compressor, limiter, and gate", color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    AudioDeckDspBadge(audioDeckRouteLabel(state, nativeOnly = true), active = state.compressor.enabled || state.gate.enabled || state.limiter.enabled, warning = !NativeAudioEngineBridge.isAvailable && (state.compressor.enabled || state.gate.enabled))
                    Spacer(Modifier.width(8.dp))
                    AudioDeckMiniSwitch("Comp", state.compressor.enabled) {
                        onState(state.copy(compressor = state.compressor.copy(enabled = it, mix = if (it && state.compressor.mix == 0f) 0.65f else state.compressor.mix)))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonKnob("Threshold", state.compressor.thresholdDb, -60f..0f, signedDb(state.compressor.thresholdDb), { onState(state.copy(compressor = state.compressor.copy(thresholdDb = it))) }, Modifier.weight(1f), 106.dp)
                    NeonKnob("Ratio", state.compressor.ratio, 1f..20f, String.format(Locale.US, "%.1f:1", state.compressor.ratio), { onState(state.copy(compressor = state.compressor.copy(ratio = it))) }, Modifier.weight(1f), 106.dp)
                    NeonKnob("Comp Mix", state.compressor.mix, 0f..1f, "${(state.compressor.mix * 100f).roundToInt()}%", { onState(state.copy(compressor = state.compressor.copy(mix = it))) }, Modifier.weight(1f), 106.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioDeckPillButton("Limiter", DeckIcon.Focus, Modifier.weight(1f), active = state.limiter.enabled) {
                        onState(state.copy(limiter = state.limiter.copy(enabled = !state.limiter.enabled)))
                    }
                    AudioDeckPillButton("Gate", DeckIcon.Headphones, Modifier.weight(1f), active = state.gate.enabled) {
                        onState(state.copy(gate = state.gate.copy(enabled = !state.gate.enabled)))
                    }
                    AudioDeckPillButton("Reset", DeckIcon.Repeat, Modifier.weight(1f)) {
                        onState(state.copy(compressor = CompressorState(), gate = GateState(), limiter = LimiterState()))
                    }
                }
                SettingSlider("Gate Threshold", signedDb(state.gate.thresholdDb), "-80", "0", state.gate.thresholdDb, -80f..0f, { onState(state.copy(gate = state.gate.copy(thresholdDb = it))) })
                AudioDeckDynamicsAdvancedDrawers(
                    state = state,
                    onState = onState,
                    selected = dynamicsDrawer,
                    onSelected = { dynamicsDrawer = if (dynamicsDrawer == it) "none" else it },
                )
            }
        }
    }
    item {
        AnimatedEntrance(5) {
            var modulationOpen by rememberSaveable { mutableStateOf(false) }
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ambience & Motion", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                        Text("Delay, reverb, chorus, flanger, phaser", color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    AudioDeckDspBadge(audioDeckRouteLabel(state, nativeOnly = true), active = state.delay.enabled || state.reverb.enabled || state.modulation.chorusEnabled || state.modulation.flangerEnabled || state.modulation.phaserEnabled, warning = !NativeAudioEngineBridge.isAvailable && (state.delay.enabled || state.reverb.enabled || state.modulation.chorusEnabled || state.modulation.flangerEnabled || state.modulation.phaserEnabled))
                    Spacer(Modifier.width(8.dp))
                    AudioDeckMiniSwitch("Delay", state.delay.enabled) {
                        onState(state.copy(delay = state.delay.copy(enabled = it, mix = if (it && state.delay.mix == 0f) 0.2f else state.delay.mix)))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonKnob("Time", state.delay.timeMs, 1f..1800f, "${state.delay.timeMs.roundToInt()} ms", { onState(state.copy(delay = state.delay.copy(timeMs = it))) }, Modifier.weight(1f), 106.dp)
                    NeonKnob("Feedback", state.delay.feedback, 0f..0.92f, "${(state.delay.feedback * 100f).roundToInt()}%", { onState(state.copy(delay = state.delay.copy(feedback = it))) }, Modifier.weight(1f), 106.dp)
                    NeonKnob("Delay Mix", state.delay.mix, 0f..1f, "${(state.delay.mix * 100f).roundToInt()}%", { onState(state.copy(delay = state.delay.copy(mix = it))) }, Modifier.weight(1f), 106.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioDeckPillButton("Chorus", DeckIcon.Wave, Modifier.weight(1f), active = state.modulation.chorusEnabled) {
                        onState(state.copy(modulation = state.modulation.copy(chorusEnabled = !state.modulation.chorusEnabled, mix = if (!state.modulation.chorusEnabled && state.modulation.mix == 0f) 0.2f else state.modulation.mix)))
                    }
                    AudioDeckPillButton("Flanger", DeckIcon.Visualizer, Modifier.weight(1f), active = state.modulation.flangerEnabled) {
                        onState(state.copy(modulation = state.modulation.copy(flangerEnabled = !state.modulation.flangerEnabled, mix = if (!state.modulation.flangerEnabled && state.modulation.mix == 0f) 0.18f else state.modulation.mix)))
                    }
                    AudioDeckPillButton("Phaser", DeckIcon.Signal, Modifier.weight(1f), active = state.modulation.phaserEnabled) {
                        onState(state.copy(modulation = state.modulation.copy(phaserEnabled = !state.modulation.phaserEnabled, mix = if (!state.modulation.phaserEnabled && state.modulation.mix == 0f) 0.18f else state.modulation.mix)))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonKnob("Rate", state.modulation.rateHz, 0.02f..8f, String.format(Locale.US, "%.2f Hz", state.modulation.rateHz), { onState(state.copy(modulation = state.modulation.copy(rateHz = it))) }, Modifier.weight(1f), 106.dp)
                    NeonKnob("Depth", state.modulation.depth, 0f..1f, "${(state.modulation.depth * 100f).roundToInt()}%", { onState(state.copy(modulation = state.modulation.copy(depth = it))) }, Modifier.weight(1f), 106.dp)
                    NeonKnob("Mod Mix", state.modulation.mix, 0f..1f, "${(state.modulation.mix * 100f).roundToInt()}%", { onState(state.copy(modulation = state.modulation.copy(mix = it))) }, Modifier.weight(1f), 106.dp)
                }
                AudioDeckModulationAdvancedDrawer(
                    state = state,
                    onState = onState,
                    expanded = modulationOpen,
                    onExpanded = { modulationOpen = !modulationOpen },
                )
                SettingSlider("Reverb Mix", "${(state.reverb.mix * 100f).roundToInt()}%", "Dry", "Wet", state.reverb.mix, 0f..1f, { onState(state.copy(reverb = state.reverb.copy(enabled = it > 0.01f, mix = it))) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioDeckPillButton("Reset", DeckIcon.Repeat, Modifier.weight(1f)) {
                        onState(state.copy(delay = DelayState(), modulation = ModulationState(), reverb = ReverbState()))
                    }
                    AudioDeckPillButton("Dry", DeckIcon.Signal, Modifier.weight(1f)) {
                        onState(state.copy(delay = state.delay.copy(enabled = false, mix = 0f), modulation = ModulationState(), reverb = state.reverb.copy(enabled = false, mix = 0f)))
                    }
                }
            }
        }
    }
}

private fun AudioEngineState.withAudioDeckEqPageReset(): AudioEngineState =
    withEqControlsReset()
        .copy(native = NativeAudioSettings())
        .normalized()

private fun nativeEngineCanInitialize(): Boolean =
    NativeAudioEngineBridge.initializationState != NativeAudioInitializationState.Failed

private fun nativeEngineStatusText(): String =
    when (NativeAudioEngineBridge.initializationState) {
        NativeAudioInitializationState.Ready -> NativeAudioEngineBridge.engineVersion()
        NativeAudioInitializationState.Initializing -> "Initializing native library"
        NativeAudioInitializationState.NotInitialized -> "Ready when enabled"
        NativeAudioInitializationState.Failed -> "Unavailable: ${NativeAudioEngineBridge.unavailableReason ?: "library load failed"}"
    }

private fun nativeUnavailableToastMessage(): String =
    when (NativeAudioEngineBridge.initializationState) {
        NativeAudioInitializationState.Failed -> "Native engine unavailable: ${NativeAudioEngineBridge.unavailableReason ?: "library load failed"}"
        NativeAudioInitializationState.Initializing -> "Native engine is initializing."
        NativeAudioInitializationState.NotInitialized -> "Native engine will initialize when enabled."
        NativeAudioInitializationState.Ready -> "Native engine ready."
    }

private fun androidx.compose.foundation.lazy.LazyListScope.audioDeckExperienceItems(
    state: AudioEngineState,
    diagnostics: OutputDiagnosticsSnapshot,
    previewOriginal: AudioEngineState?,
    previewExperienceId: String?,
    keptExperienceOriginal: AudioEngineState?,
    keptExperienceId: String?,
    onPreview: (ProListeningExperienceId) -> Unit,
    onKeep: () -> Unit,
    onRevert: () -> Unit,
    onOpenChain: () -> Unit,
) {
    val activeExperience = activeProListeningExperience(state)
    val recommended = recommendedProListeningExperience(previewOriginal ?: state, diagnostics)
    val keptRevertAvailable = previewOriginal == null &&
        keptExperienceOriginal != null &&
        activeExperience?.id?.name == keptExperienceId
    val currentLabel = when {
        previewOriginal != null -> "Previewing ${proListeningExperiences.firstOrNull { it.id.name == previewExperienceId }?.title ?: "experience"}"
        keptRevertAvailable && activeExperience != null -> "${activeExperience.title} kept"
        activeExperience != null -> activeExperience.title
        state.processingActive -> "Custom audio chain"
        else -> "Transparent path"
    }
    item {
        AnimatedEntrance(2) {
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pro Listening Experiences", color = Color.White, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                        Text(currentLabel, color = AudioDeckBlueLight, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    AudioDeckDspBadge(
                        if (previewOriginal != null) "Preview" else recommended.fit.label,
                        active = previewOriginal != null || recommended.fit == ProListeningRouteFit.Recommended,
                        warning = recommended.fit == ProListeningRouteFit.Caution,
                    )
                }
                Spacer(Modifier.height(10.dp))
                AudioDeckChainStage(
                    title = "Route Recommendation",
                    label = "${recommended.experience.title} for ${recommended.routeLabel}",
                    detail = recommended.reason,
                    active = recommended.fit == ProListeningRouteFit.Recommended,
                    warning = recommended.fit == ProListeningRouteFit.Caution,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioDeckPillButton("Preview", DeckIcon.Headphones, Modifier.weight(1f), active = previewOriginal != null) {
                        onPreview(recommended.experience.id)
                    }
                    AudioDeckPillButton("Chain", DeckIcon.Signal, Modifier.weight(1f), active = false, onClick = onOpenChain)
                }
                if (previewOriginal != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioDeckPillButton("Keep", DeckIcon.Check, Modifier.weight(1f), active = true, onClick = onKeep)
                        AudioDeckPillButton("Revert", DeckIcon.Repeat, Modifier.weight(1f), active = false, onClick = onRevert)
                    }
                } else if (keptRevertAvailable) {
                    Spacer(Modifier.height(8.dp))
                    AudioDeckPillButton("Revert Kept Preview", DeckIcon.Repeat, Modifier.fillMaxWidth(), active = false, onClick = onRevert)
                }
            }
        }
    }
    items(proListeningExperiences, key = { it.id.name }) { experience ->
        val original = previewOriginal ?: state
        val plan = proListeningExperiencePlan(original, experience.id, diagnostics)
        val previewing = previewOriginal != null && previewExperienceId == experience.id.name
        val active = previewOriginal == null && activeExperience?.id == experience.id
        AnimatedEntrance(3 + experience.id.ordinal) {
            ProListeningExperienceCard(
                plan = plan,
                active = active,
                previewing = previewing,
                keptRevertAvailable = keptRevertAvailable && keptExperienceId == experience.id.name,
                onPreview = { onPreview(experience.id) },
                onKeep = onKeep,
                onRevert = onRevert,
            )
        }
    }
}

@Composable
private fun ProListeningExperienceCard(
    plan: ProListeningExperiencePlan,
    active: Boolean,
    previewing: Boolean,
    keptRevertAvailable: Boolean,
    onPreview: () -> Unit,
    onKeep: () -> Unit,
    onRevert: () -> Unit,
) {
    val warning = plan.safetyStatus == ProListeningSafetyStatus.RouteCaution ||
        plan.safetyStatus == ProListeningSafetyStatus.CompatibilityBlocked
    AudioDeckCleanEqCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.experience.title, color = Color.White, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
                Text(plan.experience.tagline, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            AudioDeckDspBadge(
                when {
                    previewing -> "Preview"
                    active -> "Active"
                    else -> plan.routeFit.label
                },
                active = previewing || active || plan.routeFit == ProListeningRouteFit.Recommended,
                warning = warning || plan.routeFit == ProListeningRouteFit.Caution,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProListeningMiniChip("Best: ${plan.experience.bestFor.joinToString("/") { it.label }}", Modifier.weight(1f))
            ProListeningMiniChip(plan.experience.intensity.label, Modifier.weight(0.68f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProListeningMiniChip(plan.safetyStatus.label, Modifier.weight(1f), warning = warning)
            ProListeningMiniChip("Headroom ${signedDb(plan.headroomDb)}", Modifier.weight(1f), warning = plan.headroomDb < -0.05f)
        }
        Spacer(Modifier.height(10.dp))
        AudioDeckDspGraphSummaryRow(
            title = "Pro Audio Info",
            detail = plan.proAudioInfo,
            warning = warning,
        )
        AudioDeckDspGraphSummaryRow(
            title = "True-peak Safety",
            detail = plan.truePeakSafety,
            warning = plan.truePeakSafety.contains("Unsupported", ignoreCase = true) ||
                plan.truePeakSafety.contains("Pending", ignoreCase = true),
        )
        plan.changes.take(4).forEach { change ->
            AudioDeckDspGraphSummaryRow(
                title = change.stage,
                detail = "${change.before} -> ${change.after}",
            )
        }
        if (plan.routeWarnings.isNotEmpty() || plan.compatibilityNotes.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            (plan.routeWarnings + plan.compatibilityNotes).take(3).forEach { note ->
                AudioDeckDspGraphSummaryRow(
                    title = "Compatibility",
                    detail = note,
                    warning = note.contains("Bluetooth", ignoreCase = true) ||
                        note.contains("blocked", ignoreCase = true) ||
                        note.contains("fallback", ignoreCase = true) ||
                        note.contains("not verified", ignoreCase = true),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (previewing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioDeckPillButton("Keep", DeckIcon.Check, Modifier.weight(1f), active = true, onClick = onKeep)
                AudioDeckPillButton("Revert", DeckIcon.Repeat, Modifier.weight(1f), active = false, onClick = onRevert)
            }
        } else if (active && keptRevertAvailable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioDeckPillButton("Preview Again", DeckIcon.Headphones, Modifier.weight(1f), active = true, onClick = onPreview)
                AudioDeckPillButton("Revert", DeckIcon.Repeat, Modifier.weight(1f), active = false, onClick = onRevert)
            }
        } else {
            AudioDeckPillButton(if (active) "Preview Again" else "Preview", DeckIcon.Headphones, Modifier.fillMaxWidth(), active = active, onClick = onPreview)
        }
    }
}

@Composable
private fun ProListeningMiniChip(label: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    val tint = if (warning) AudioEqYellow else AudioDeckBlueLight
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = if (warning) 0.12f else 0.09f))
            .border(1.dp, tint.copy(alpha = if (warning) 0.32f else 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (warning) AudioEqYellow else Color.White.copy(alpha = 0.78f), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioDeckChainItems(
    snapshot: AudioChainSnapshot,
    graph: DspGraphSnapshot,
) {
    item {
        AnimatedEntrance(2) {
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pro Audio Info", color = Color.White, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                        Text(proAudioTrustLine(snapshot, graph), color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    AudioDeckDspBadge(snapshot.output.actualPlugin, active = snapshot.dsp.processingActive || snapshot.device.verified, warning = snapshot.warnings.any { it.level == AudioChainWarningLevel.Caution })
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioDeckDspBadge(snapshot.dsp.label, active = snapshot.dsp.processingActive, warning = snapshot.dsp.platformEffectsActive, modifier = Modifier.weight(1f))
                    AudioDeckDspBadge(snapshot.resamplerStrategy.resamplerStatus.chainLabel(), active = snapshot.resamplerStrategy.warning == null, warning = snapshot.resamplerStrategy.warning != null, modifier = Modifier.weight(1f))
                    AudioDeckDspBadge(snapshot.outputCapability.hiResOutputStatus.chainLabel(), active = snapshot.outputCapability.hiResOutputStatus.isActiveEvidence(), warning = snapshot.outputCapability.hiResOutputStatus.isCaution(), modifier = Modifier.weight(1f))
                }
            }
        }
    }
    item {
        AnimatedEntrance(3) {
            AudioDeckCleanEqCard {
                AudioDeckChainSectionHeader("Playback Source", "Source and decoder metadata")
                AudioDeckChainStage(
                    title = "Source",
                    label = snapshot.source.label,
                    detail = "${audioChainFormatLine(snapshot.source)} | ${snapshot.source.detail}",
                    active = true,
                )
                AudioDeckDspGraphSummaryRow(
                    title = "Source Quality",
                    detail = sourceQualityLine(snapshot),
                    warning = snapshot.proOutput.sourceEligibility.hiResStatus == ProOutputHiResSourceStatus.Unknown,
                )
                AudioDeckChainStage(
                    title = "Decoder",
                    label = snapshot.decoder.label,
                    detail = "${snapshot.decoder.codec} to ${snapshot.decoder.outputEncoding} | ${snapshot.decoder.detail}",
                    active = true,
                )
                AudioDeckDspGraphSummaryRow(
                    title = "Decode Quality",
                    detail = decodeQualityLine(snapshot),
                    warning = snapshot.decoder.decodedSampleRateHz == null || snapshot.decoder.outputEncoding == "PCM",
                )
            }
        }
    }
    item {
        AnimatedEntrance(4) {
            AudioDeckCleanEqCard {
                AudioDeckChainSectionHeader("Resampler", "Strategy and rate reporting")
                AudioDeckChainStage(
                    title = "Resampler",
                    label = snapshot.resampler.label,
                    detail = "${snapshot.resampler.effectiveSampleRate} | ${snapshot.resampler.detail}",
                    active = snapshot.resampler.verified,
                    warning = !snapshot.resampler.verified && snapshot.resampler.requestedSampleRate != OutputSampleRate.Auto.label,
                )
                AudioDeckChainStage(
                    title = "Resampler Strategy",
                    label = snapshot.resamplerStrategy.resamplerStatus.chainLabel(),
                    detail = resamplerStrategyLine(snapshot.resamplerStrategy),
                    active = snapshot.resamplerStrategy.warning == null && snapshot.resamplerStrategy.resamplerStatus != ResamplerStatus.Unknown,
                    warning = snapshot.resamplerStrategy.warning != null || snapshot.resamplerStrategy.resamplerStatus == ResamplerStatus.Unknown,
                )
            }
        }
    }
    item {
        AnimatedEntrance(5) {
            AudioDeckCleanEqCard {
                AudioDeckChainSectionHeader(
                    "DSP Graph",
                    "Headroom ${signedDb(graph.totalHeadroomDb)}  |  Trim ${signedDb(graph.outputTrimDb)}  |  Risk ${graph.clippingRisk.graphLabel()}",
                )
                AudioDeckChainStage(
                    title = "DSP",
                    label = snapshot.dsp.label,
                    detail = snapshot.dsp.detail,
                    active = snapshot.dsp.processingActive && !snapshot.dsp.platformEffectsActive,
                    warning = snapshot.dsp.platformEffectsActive,
                )
                AudioDeckDspGraphSummaryRow(
                    title = "Processing Quality",
                    detail = processingQualityLine(snapshot),
                    warning = snapshot.proOutput.blockers.any {
                        it in setOf(
                            ProOutputBlocker.DspActive,
                            ProOutputBlocker.ReplayGainActive,
                            ProOutputBlocker.EqActive,
                            ProOutputBlocker.ToneActive,
                            ProOutputBlocker.DynamicsActive,
                            ProOutputBlocker.TimeEffectsActive,
                            ProOutputBlocker.PitchTempoActive,
                            ProOutputBlocker.OutputTrimActive,
                        )
                    },
                )
                graph.stages.sortedBy { it.order }.forEach { stage ->
                    AudioDeckDspGraphStageRow(stage)
                }
            }
        }
    }
    item {
        AnimatedEntrance(6) {
            AudioDeckCleanEqCard {
                AudioDeckChainSectionHeader("Loudness / Headroom", "ReplayGain, trim, and limiter status")
                AudioDeckDspGraphSummaryRow(
                    title = "Loudness",
                    detail = graph.loudness.graphLabel(),
                    warning = graph.loudness.measurementStatus == LoudnessMeasurementStatus.StreamMetadataNotMeasured,
                )
                AudioDeckDspGraphSummaryRow(
                    title = "Headroom",
                    detail = graph.headroomManager.graphLabel(),
                    warning = graph.headroomManager.clippingRisk != ClippingRisk.None,
                )
                AudioDeckDspGraphSummaryRow(
                    title = "Limiter",
                    detail = graph.limiterGroundwork.graphLabel(),
                    warning = graph.limiterGroundwork.currentProtection == LimiterProtectionMode.LookAheadPlanned,
                )
                AudioDeckDspGraphSummaryRow(
                    title = "Smart Preamp",
                    detail = graph.smartPreamp.graphLabel(),
                    warning = graph.smartPreamp.reason == SmartPreampReason.LimiterProtection,
                )
            }
        }
    }
    item {
        AnimatedEntrance(7) {
            AudioDeckCleanEqCard {
                AudioDeckChainSectionHeader("Output Engine", "Player output and device route")
                AudioDeckChainStage(
                    title = "Output",
                    label = snapshot.output.label,
                    detail = snapshot.output.detail,
                    active = true,
                )
                AudioDeckChainStage(
                    title = "Output Device",
                    label = snapshot.device.label,
                    detail = "${snapshot.device.activeRoute} | ${snapshot.device.detail}",
                    active = snapshot.device.verified,
                    warning = !snapshot.device.verified,
                )
                AudioDeckChainStage(
                    title = "Output Capability",
                    label = snapshot.outputCapability.hiResOutputStatus.chainLabel(),
                    detail = outputCapabilityLine(snapshot.outputCapability),
                    active = snapshot.outputCapability.hiResOutputStatus.isActiveEvidence(),
                    warning = snapshot.outputCapability.hiResOutputStatus.isCaution(),
                )
                AudioDeckChainStage(
                    title = "Pro Output Engine",
                    label = snapshot.proOutput.currentStatus.chainLabel(),
                    detail = proOutputLine(snapshot.proOutput),
                    active = snapshot.proOutput.active,
                    warning = snapshot.proOutput.enabledByUser && !snapshot.proOutput.active,
                )
                AudioDeckDspGraphSummaryRow(
                    title = "Output Quality",
                    detail = outputQualityLine(snapshot),
                    warning = snapshot.proOutput.enabledByUser && !snapshot.proOutput.active,
                )
            }
        }
    }
    item {
        AnimatedEntrance(8) {
            AudioDeckCleanEqCard {
                AudioDeckChainSectionHeader("Bit-perfect / Hi-res Eligibility", "Eligibility model only")
                AudioDeckChainStage(
                    title = "Bit-perfect Attempt",
                    label = snapshot.bitPerfect.status.chainLabel(),
                    detail = bitPerfectLine(snapshot.bitPerfect),
                    active = snapshot.bitPerfect.active,
                    warning = snapshot.bitPerfect.conflicts.isNotEmpty(),
                )
                AudioDeckDspGraphSummaryRow(
                    title = "Hi-res / USB",
                    detail = hiResEligibilityLine(snapshot.outputCapability),
                    warning = snapshot.outputCapability.hiResOutputStatus.isCaution() ||
                        snapshot.outputCapability.hiResRequested ||
                        snapshot.outputCapability.usbDacRequested ||
                        snapshot.outputCapability.status != OutputVerificationStatus.Verified,
                )
            }
        }
    }
    item {
        AnimatedEntrance(9) {
            AudioDeckCleanEqCard {
                Text("Warnings", color = Color.White, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black)
                if (snapshot.warnings.isEmpty() && graph.warnings.isEmpty()) {
                    AudioDeckDspGraphSummaryRow(
                        title = "Status",
                        detail = "No caution notes visible in the current inspector snapshot.",
                    )
                } else {
                    snapshot.warnings.forEach { warning ->
                        AudioDeckChainWarningRow(warning)
                    }
                    graph.warnings.forEach { warning ->
                        AudioDeckDspGraphWarningRow(warning)
                    }
                }
            }
        }
    }
    item {
        AnimatedEntrance(10) {
            var debugOpen by rememberSaveable { mutableStateOf(false) }
            AudioDeckCleanEqCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Debug Details", color = Color.White, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black)
                        Text("Expanded graph diagnostics and groundwork rows", color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    AudioDeckPillButton(if (debugOpen) "Hide" else "Show", DeckIcon.Info, active = debugOpen) {
                        debugOpen = !debugOpen
                    }
                }
                if (debugOpen) {
                    Spacer(Modifier.height(10.dp))
                    AudioDeckDspGraphSummaryRow(
                        title = "Graphic EQ",
                        detail = graph.graphicEq.graphLabel(),
                        warning = !graph.graphicEq.canonical,
                    )
                    AudioDeckDspGraphSummaryRow(
                        title = "Parametric EQ",
                        detail = graph.parametricEq.graphLabel(),
                        warning = !graph.parametricEq.canonical || (graph.parametricEq.activeSlotCount > 0 && graph.parametricEq.owner == DspStageOwner.Unavailable),
                    )
                    AudioDeckDspGraphSummaryRow(
                        title = "Tone",
                        detail = graph.tone.graphLabel(),
                        warning = graph.tone.warnings.isNotEmpty(),
                    )
                    AudioDeckDspGraphSummaryRow(
                        title = "Resampler Strategy",
                        detail = resamplerStrategyLine(snapshot.resamplerStrategy),
                        warning = snapshot.resamplerStrategy.warning != null,
                    )
                    AudioDeckDspGraphSummaryRow(
                        title = "Output Capability",
                        detail = outputCapabilityLine(snapshot.outputCapability),
                        warning = snapshot.outputCapability.hiResOutputStatus.isCaution() ||
                            snapshot.outputCapability.status != OutputVerificationStatus.Verified,
                    )
                    AudioDeckDspGraphSummaryRow(
                        title = "Pro Output Blockers",
                        detail = proOutputBlockerLine(snapshot.proOutput),
                        warning = snapshot.proOutput.blockers.isNotEmpty(),
                    )
                    AudioDeckDspGraphSummaryRow(
                        title = "Meter Groundwork",
                        detail = graph.metering.graphLabel(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioDeckChainSectionHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudioDeckChainStage(
    title: String,
    label: String,
    detail: String,
    active: Boolean,
    warning: Boolean = false,
) {
    val tint = when {
        warning -> AudioEqYellow
        active -> AudioDeckBlueLight
        else -> Muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (active || warning) 0.060f else 0.038f))
            .border(1.dp, tint.copy(alpha = if (active || warning) 0.22f else 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tint.copy(alpha = if (active || warning) 0.88f else 0.54f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(Locale.US), color = tint, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.White, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun AudioDeckDspGraphSummaryRow(
    title: String,
    detail: String,
    warning: Boolean = false,
) {
    val tint = if (warning) AudioEqYellow else AudioDeckBlueLight
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (warning) 0.054f else 0.034f))
            .border(1.dp, tint.copy(alpha = if (warning) 0.22f else 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(if (warning) DeckIcon.Info else DeckIcon.Signal, tint, Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(Locale.US), color = tint, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black)
            Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun AudioDeckDspGraphStageRow(stage: DspGraphStage) {
    val warning = stage.status == DspStageStatus.Unsupported || stage.status == DspStageStatus.SafeModeDisabled
    val tint = when {
        warning -> AudioEqYellow
        stage.active -> AudioDeckBlueLight
        stage.status == DspStageStatus.Bypassed -> AudioEqYellow.copy(alpha = 0.82f)
        else -> Muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (stage.active || warning) 0.054f else 0.032f))
            .border(1.dp, tint.copy(alpha = if (stage.active || warning) 0.20f else 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (stage.order + 1).toString().padStart(2, '0'),
            color = tint,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stage.label, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(stage.status.graphLabel(), color = tint, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Text(
                dspGraphStageLine(stage),
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AudioDeckChainWarningRow(warning: AudioChainWarning) {
    val tint = if (warning.level == AudioChainWarningLevel.Caution) AudioEqYellow else AudioDeckBlueLight
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.070f))
            .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(if (warning.level == AudioChainWarningLevel.Caution) DeckIcon.Info else DeckIcon.Signal, tint, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(warning.title, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            Text(warning.detail, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun AudioDeckDspGraphWarningRow(warning: DspGraphWarning) {
    val tint = if (warning.level == DspGraphWarningLevel.Caution) AudioEqYellow else AudioDeckBlueLight
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.070f))
            .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(if (warning.level == DspGraphWarningLevel.Caution) DeckIcon.Info else DeckIcon.Signal, tint, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(warning.title, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            Text(warning.detail, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun audioChainFormatLine(source: AudioSourceInfo): String =
    buildList {
        add(source.format.ifBlank { "Unknown" })
        source.mimeType?.takeIf { it.isNotBlank() && !it.equals(source.format, ignoreCase = true) }?.let(::add)
        source.containerMimeType
            ?.takeIf { it.isNotBlank() && it != source.mimeType }
            ?.let { add("container $it") }
        source.sampleRateHz?.let { add(audioChainSampleRateLabel(it)) }
        source.bitDepth?.let { add("$it-bit") }
        source.channels?.let { add(if (it == 1) "mono" else if (it == 2) "stereo" else "$it ch") }
        source.bitrateKbps?.let { add("$it kbps") }
    }.ifEmpty { listOf("Unknown") }.joinToString(" | ")

private fun proAudioTrustLine(snapshot: AudioChainSnapshot, graph: DspGraphSnapshot): String {
    val cautionCount = snapshot.warnings.count { it.level == AudioChainWarningLevel.Caution } +
        graph.warnings.count { it.level == DspGraphWarningLevel.Caution }
    val notesCount = snapshot.warnings.size + graph.warnings.size
    return buildList {
        add(if (snapshot.dsp.processingActive) snapshot.dsp.label else "Transparent/saved DSP state")
        add(snapshot.resamplerStrategy.resamplerStatus.chainLabel())
        add(snapshot.outputCapability.hiResOutputStatus.chainLabel())
        add(snapshot.proOutput.currentStatus.chainLabel())
        add(if (snapshot.bitPerfect.active) "bit-perfect active" else "bit-perfect not active")
        if (cautionCount > 0) add("$cautionCount caution")
        else add("$notesCount note(s)")
    }.joinToString(" | ")
}

private fun hiResEligibilityLine(capability: OutputCapabilitySnapshot): String =
    buildList {
        add(capability.hiResOutputStatus.chainLabel())
        add(
            when {
                capability.usbDacActive -> "USB DAC observed"
                capability.usbDacRequested -> "USB DAC profile"
                else -> "${capability.route.label} profile"
            },
        )
        add(capability.hiResEvidence.evidenceSource.chainLabel())
        add(capability.hiResEvidence.engine.chainLabel())
        add(if (capability.hiResEvidence.providerActive) "custom provider active" else "custom provider not active")
        add(capability.status.chainLabel())
        add("plugin ${capability.actualPlugin?.label ?: "unknown"}")
        add("rate ${capability.requestedSampleRateHz?.let(::audioChainSampleRateLabel) ?: "Auto"}")
        add("depth ${capability.requestedBitDepth.label}")
        capability.audioTrackSampleRateHz?.let { add("AudioTrack ${audioChainSampleRateLabel(it)}") }
        capability.audioTrackEncoding?.let { add(it) }
        add("capabilities ${sampleRateCapabilityLabel(capability.supportedSampleRatesHz)}")
        capability.warning?.let(::add)
    }.joinToString(" | ")

private fun sourceQualityLine(snapshot: AudioChainSnapshot): String =
    buildList {
        add(snapshot.source.format.ifBlank { "Unknown" })
        add("source ${snapshot.proOutput.sourceEligibility.sourceSampleRateHz?.let(::audioChainSampleRateLabel) ?: "unknown"}")
        add("depth ${snapshot.proOutput.sourceEligibility.sourceBitDepth?.let { "$it-bit" } ?: "unknown"}")
        add(snapshot.proOutput.sourceEligibility.hiResStatus.chainLabel())
        add(snapshot.proOutput.sourceEligibility.reason)
    }.joinToString(" | ")

private fun decodeQualityLine(snapshot: AudioChainSnapshot): String =
    buildList {
        add(snapshot.decoder.label)
        add("decoded ${snapshot.decoder.decodedSampleRateHz?.let(::audioChainSampleRateLabel) ?: "pending"}")
        add(snapshot.decoder.outputEncoding.ifBlank { "PCM pending" })
        snapshot.decoder.decodedChannels?.let { add(if (it == 1) "mono" else if (it == 2) "stereo" else "$it ch") }
        add(snapshot.decoder.detail)
    }.joinToString(" | ")

private fun processingQualityLine(snapshot: AudioChainSnapshot): String =
    buildList {
        add(snapshot.proOutput.mode.processingLabel())
        add(if (snapshot.dsp.processingActive) "DSP active" else "DSP neutral/off")
        add(if (snapshot.proOutput.blockers.isEmpty()) "no pro-output blockers" else "${snapshot.proOutput.blockers.size} blocker(s)")
        snapshot.proOutput.blockers.take(4).forEach { add(it.chainLabel()) }
    }.joinToString(" | ")

private fun outputQualityLine(snapshot: AudioChainSnapshot): String =
    buildList {
        add(snapshot.outputCapability.hiResOutputStatus.chainLabel())
        add(snapshot.proOutput.engine.chainLabel())
        add("route ${snapshot.proOutput.routeEligibility.route.label}")
        add("requested ${snapshot.proOutput.routeEligibility.outputSampleRateHz?.let(::audioChainSampleRateLabel) ?: snapshot.outputCapability.requestedSampleRateHz?.let(::audioChainSampleRateLabel) ?: "Auto"}")
        add("actual ${snapshot.proOutput.routeEligibility.outputSampleRateHz?.let(::audioChainSampleRateLabel) ?: "unknown"}")
        snapshot.proOutput.routeEligibility.outputEncoding?.let { add(it) }
        snapshot.proOutput.routeEligibility.channelCount?.let { add(if (it == 1) "mono" else if (it == 2) "stereo" else "$it ch") }
        snapshot.proOutput.routeEligibility.bufferSizeBytes?.let { add("buffer $it B") }
        add("underruns ${snapshot.proOutput.routeEligibility.underrunCount}")
        add(snapshot.proOutput.currentStatus.chainLabel())
        snapshot.proOutput.fallbackReason?.let(::add)
    }.joinToString(" | ")

private fun resamplerStrategyLine(strategy: ResamplerStrategySnapshot): String =
    buildList {
        add("Source ${strategy.sourceSampleRateHz?.let(::audioChainSampleRateLabel) ?: "unknown"}")
        add("Decoded ${strategy.decodedSampleRateHz?.let(::audioChainSampleRateLabel) ?: "pending"}")
        add("Output ${strategy.outputSampleRateHz?.let(::audioChainSampleRateLabel) ?: "system"}")
        add("Device pref ${strategy.devicePreferredSampleRateHz?.let(::audioChainSampleRateLabel) ?: "not reported"}")
        add(strategy.strategy.chainLabel())
        add(strategy.qualityMode.chainLabel())
        add(strategy.activeEngine.chainLabel())
        strategy.reason?.let(::add)
        strategy.warning?.let(::add)
    }.joinToString(" | ")

private fun outputCapabilityLine(capability: OutputCapabilitySnapshot): String =
    buildList {
        add(capability.hiResOutputStatus.chainLabel())
        add(capability.route.label)
        add("requested ${capability.requestedPlugin.label}")
        add("actual ${capability.actualPlugin?.label ?: "unknown"}")
        add("rate ${capability.requestedSampleRateHz?.let(::audioChainSampleRateLabel) ?: "Auto"}")
        add("depth ${capability.requestedBitDepth.label}")
        add(capability.hiResEvidence.evidenceSource.chainLabel())
        add(capability.hiResEvidence.engine.chainLabel())
        add(if (capability.hiResEvidence.providerActive) "provider active" else "provider not active")
        capability.audioTrackSampleRateHz?.let { add("track ${audioChainSampleRateLabel(it)}") }
        capability.audioTrackEncoding?.let { add(it) }
        add("rates ${sampleRateCapabilityLabel(capability.supportedSampleRatesHz)}")
        add("encodings ${capability.supportedEncodings.ifEmpty { listOf("not reported") }.joinToString("/")}")
        capability.reason?.let(::add)
        capability.warning?.let(::add)
    }.joinToString(" | ")

private fun proOutputLine(snapshot: ProOutputEngineState): String =
    buildList {
        add(snapshot.mode.chainLabel())
        add(snapshot.engine.chainLabel())
        add(if (snapshot.active) "verified active" else if (snapshot.enabledByUser) "not active" else "off")
        add("source ${snapshot.sourceEligibility.sourceSampleRateHz?.let(::audioChainSampleRateLabel) ?: "unknown"}")
        add("decoded ${snapshot.sourceEligibility.decodedSampleRateHz?.let(::audioChainSampleRateLabel) ?: "pending"}")
        snapshot.sourceEligibility.decodedEncoding?.let { add(it) }
        add("route ${snapshot.routeEligibility.route.label}")
        add("output ${snapshot.routeEligibility.outputSampleRateHz?.let(::audioChainSampleRateLabel) ?: "unknown"}")
        snapshot.routeEligibility.outputEncoding?.let { add(it) }
        snapshot.routeEligibility.channelCount?.let { add(if (it == 1) "mono" else if (it == 2) "stereo" else "$it ch") }
        add("underruns ${snapshot.routeEligibility.underrunCount}")
        if (snapshot.blockers.isNotEmpty()) add("${snapshot.blockers.size} blocker(s)")
        snapshot.fallbackReason?.let(::add)
    }.joinToString(" | ")

private fun proOutputBlockerLine(snapshot: ProOutputEngineState): String =
    if (snapshot.blockers.isEmpty()) {
        if (snapshot.active) {
            "No blockers; active state is backed by runtime source, decoder, route, and AudioTrack evidence."
        } else {
            "No blockers visible; pro-output activation is ${snapshot.currentStatus.chainLabel().lowercase(Locale.US)}."
        }
    } else {
        snapshot.blockers
            .take(8)
            .joinToString(" | ") { it.chainLabel() }
    }

private fun bitPerfectLine(snapshot: BitPerfectAttemptSnapshot): String =
    buildList {
        add(if (snapshot.active) "active" else if (snapshot.userAttemptEnabled) "requested" else "not requested")
        add(if (snapshot.eligible) "eligible model" else "${snapshot.conflicts.size} conflict(s)")
        add("source ${snapshot.sourceSampleRateHz?.let(::audioChainSampleRateLabel) ?: "unknown"}")
        add("decoded ${snapshot.decodedSampleRateHz?.let(::audioChainSampleRateLabel) ?: "pending"}")
        add("output ${snapshot.outputSampleRateHz?.let(::audioChainSampleRateLabel) ?: "system"}")
        add("depth ${snapshot.requestedBitDepth.label}")
        snapshot.conflicts.take(3).forEach { add(it.type.chainLabel()) }
        add(snapshot.reason)
        snapshot.warning?.let(::add)
    }.joinToString(" | ")

private fun sampleRateCapabilityLabel(sampleRatesHz: List<Int>): String =
    sampleRatesHz
        .takeIf { it.isNotEmpty() }
        ?.joinToString("/") { audioChainSampleRateLabel(it) }
        ?: "not reported"

private fun ResamplerStatus.chainLabel(): String =
    when (this) {
        ResamplerStatus.NotNeeded -> "Not needed"
        ResamplerStatus.SystemManaged -> "System managed"
        ResamplerStatus.LikelySystemResampled -> "Likely system resampled"
        ResamplerStatus.PulseDeckPlanned -> "PulseDeck planned"
        ResamplerStatus.PulseDeckActive -> "PulseDeck active"
        ResamplerStatus.Unknown -> "Unknown"
    }

private fun ResamplerStrategy.chainLabel(): String =
    when (this) {
        ResamplerStrategy.FollowSystem -> "Follow system"
        ResamplerStrategy.MatchSourceIfPossible -> "Match source if possible"
        ResamplerStrategy.MatchDevicePreferred -> "Match requested output"
        ResamplerStrategy.BatterySaver -> "Battery saver"
        ResamplerStrategy.HighQualityPlanned -> "HQ planned"
        ResamplerStrategy.BitPerfectAttempt -> "Bit-perfect attempt"
    }

private fun ResamplerQualityMode.chainLabel(): String =
    when (this) {
        ResamplerQualityMode.Standard -> "standard"
        ResamplerQualityMode.HighQualityPlanned -> "HQ planned"
        ResamplerQualityMode.UltraPlanned -> "Ultra planned"
        ResamplerQualityMode.BatterySaver -> "battery saver"
        ResamplerQualityMode.Unknown -> "quality unknown"
    }

private fun ResamplerEngine.chainLabel(): String =
    when (this) {
        ResamplerEngine.AndroidSystem -> "Android system"
        ResamplerEngine.Media3Default -> "Media3 default"
        ResamplerEngine.PulseDeckNativePlanned -> "PulseDeck native planned"
        ResamplerEngine.Unknown -> "engine unknown"
    }

private fun OutputVerificationStatus.chainLabel(): String =
    when (this) {
        OutputVerificationStatus.Verified -> "Verified route"
        OutputVerificationStatus.Requested -> "Requested only"
        OutputVerificationStatus.NotVerified -> "Not verified"
        OutputVerificationStatus.Unsupported -> "Unsupported fallback"
        OutputVerificationStatus.Unknown -> "Unknown"
    }

private fun HiResOutputStatus.chainLabel(): String =
    when (this) {
        HiResOutputStatus.Off -> "Hi-res off"
        HiResOutputStatus.Requested -> "Hi-res requested"
        HiResOutputStatus.Blocked -> "Hi-res blocked"
        HiResOutputStatus.NotVerified -> "Hi-res not verified"
        HiResOutputStatus.HighSampleRateActive -> "High sample-rate active"
        HiResOutputStatus.HighBitDepthActive -> "High bit-depth active"
        HiResOutputStatus.FullHiResActive -> "Full hi-res active"
        HiResOutputStatus.Unknown -> "Hi-res unknown"
    }

private fun HiResOutputStatus.isActiveEvidence(): Boolean =
    this == HiResOutputStatus.HighSampleRateActive ||
        this == HiResOutputStatus.HighBitDepthActive ||
        this == HiResOutputStatus.FullHiResActive

private fun HiResOutputStatus.isCaution(): Boolean =
    this == HiResOutputStatus.Requested ||
        this == HiResOutputStatus.Blocked ||
        this == HiResOutputStatus.NotVerified ||
        this == HiResOutputStatus.Unknown

private fun HiResEvidenceSource.chainLabel(): String =
    when (this) {
        HiResEvidenceSource.None -> "no runtime evidence"
        HiResEvidenceSource.Media3AudioTrackRuntime -> "Media3 AudioTrack runtime"
        HiResEvidenceSource.Media3CustomProviderRuntime -> "Media3 custom provider runtime"
        HiResEvidenceSource.AudioFlingerVerificationOnly -> "AudioFlinger verification only"
        HiResEvidenceSource.OboeAaudioRuntime -> "Oboe/AAudio runtime"
        HiResEvidenceSource.Unknown -> "unknown evidence"
    }

private fun BitPerfectAttemptStatus.chainLabel(): String =
    when (this) {
        BitPerfectAttemptStatus.Off -> "Off"
        BitPerfectAttemptStatus.Eligible -> "Eligible"
        BitPerfectAttemptStatus.Ineligible -> "Ineligible"
        BitPerfectAttemptStatus.Requested -> "Requested"
        BitPerfectAttemptStatus.Active -> "Active"
        BitPerfectAttemptStatus.Unknown -> "Unknown"
    }

private fun ProOutputMode.chainLabel(): String =
    when (this) {
        ProOutputMode.StandardMedia3 -> "Standard Media3"
        ProOutputMode.HiResAttempt -> "Hi-res attempt"
        ProOutputMode.UsbDacAttempt -> "USB DAC attempt"
        ProOutputMode.BitPerfectAttempt -> "Bit-perfect attempt"
    }

private fun ProOutputMode.processingLabel(): String =
    when (this) {
        ProOutputMode.StandardMedia3 -> "Standard"
        ProOutputMode.HiResAttempt -> "Hi-Res Clean attempt"
        ProOutputMode.UsbDacAttempt -> "USB DAC attempt"
        ProOutputMode.BitPerfectAttempt -> "Bit-Perfect Attempt"
    }

private fun ProOutputHiResSourceStatus.chainLabel(): String =
    when (this) {
        ProOutputHiResSourceStatus.Unknown -> "hi-res source unknown"
        ProOutputHiResSourceStatus.NotHiRes -> "not hi-res"
        ProOutputHiResSourceStatus.LikelyHiRes -> "likely hi-res"
        ProOutputHiResSourceStatus.VerifiedHiRes -> "hi-res source verified"
    }

private fun ProOutputEngine.chainLabel(): String =
    when (this) {
        ProOutputEngine.Media3Default -> "Media3/AudioTrack"
        ProOutputEngine.Media3CustomOutput -> "Media3 custom output"
        ProOutputEngine.OboeAaudio -> "Oboe/AAudio"
        ProOutputEngine.AudioTrackDirect -> "AudioTrack direct"
        ProOutputEngine.Unavailable -> "Unavailable"
    }

private fun ProOutputStatus.chainLabel(): String =
    when (this) {
        ProOutputStatus.Off -> "Off"
        ProOutputStatus.Requested -> "Requested"
        ProOutputStatus.Eligible -> "Eligible"
        ProOutputStatus.Attempted -> "Attempted"
        ProOutputStatus.Active -> "Active"
        ProOutputStatus.Blocked -> "Blocked"
        ProOutputStatus.FallbackToMedia3 -> "Media3 fallback"
        ProOutputStatus.Unsupported -> "Unsupported"
        ProOutputStatus.Unknown -> "Unknown"
    }

private fun ProOutputBlocker.chainLabel(): String =
    when (this) {
        ProOutputBlocker.NotLocalOrOffline -> "Not local/offline"
        ProOutputBlocker.SourceFormatUnknown -> "Source format unknown"
        ProOutputBlocker.SourceNotHiRes -> "Source not hi-res"
        ProOutputBlocker.DecodeFormatUnknown -> "Decoder format pending"
        ProOutputBlocker.OutputRouteUnknown -> "Output route unknown"
        ProOutputBlocker.OutputSampleRateUnknown -> "Output rate unknown"
        ProOutputBlocker.OutputEncodingUnknown -> "Output encoding unknown"
        ProOutputBlocker.BluetoothRoute -> "Bluetooth route"
        ProOutputBlocker.UsbDacNotDetected -> "USB DAC not detected"
        ProOutputBlocker.DspActive -> "DSP active"
        ProOutputBlocker.ReplayGainActive -> "ReplayGain active"
        ProOutputBlocker.EqActive -> "EQ active"
        ProOutputBlocker.ToneActive -> "Tone active"
        ProOutputBlocker.DynamicsActive -> "Dynamics active"
        ProOutputBlocker.TimeEffectsActive -> "Time effects active"
        ProOutputBlocker.PitchTempoActive -> "Pitch/tempo active"
        ProOutputBlocker.OutputTrimActive -> "Output trim active"
        ProOutputBlocker.CrossfadeActive -> "Crossfade active"
        ProOutputBlocker.ResamplingLikely -> "Resampling likely"
        ProOutputBlocker.NativeDspUnsupportedFormat -> "Native DSP format fallback"
        ProOutputBlocker.Media3CustomOutputUnavailable -> "Media3 custom output unavailable"
        ProOutputBlocker.OboeUnavailable -> "Oboe unavailable"
        ProOutputBlocker.DeviceRejectedFormat -> "Device rejected format"
        ProOutputBlocker.UnderrunRisk -> "Underrun risk"
        ProOutputBlocker.Unknown -> "Evidence pending"
    }

private fun BitPerfectConflictType.chainLabel(): String =
    when (this) {
        BitPerfectConflictType.DspActive -> "DSP active"
        BitPerfectConflictType.ReplayGainActive -> "ReplayGain"
        BitPerfectConflictType.EqActive -> "EQ"
        BitPerfectConflictType.ToneActive -> "Tone"
        BitPerfectConflictType.StereoToolsActive -> "Stereo tools"
        BitPerfectConflictType.DynamicsActive -> "Dynamics"
        BitPerfectConflictType.TimeEffectsActive -> "Time effects"
        BitPerfectConflictType.CrossfadeActive -> "Crossfade"
        BitPerfectConflictType.ResamplerMismatch -> "Resampler mismatch"
        BitPerfectConflictType.OutputTrimActive -> "Output trim"
        BitPerfectConflictType.HiResUnverified -> "Hi-Res unverified"
        BitPerfectConflictType.DeviceUnverified -> "Device unverified"
        BitPerfectConflictType.AudioTrackMismatch -> "AudioTrack mismatch"
        BitPerfectConflictType.BluetoothRoute -> "Bluetooth route"
        BitPerfectConflictType.SourceUnknown -> "Source unknown"
        BitPerfectConflictType.DecoderUnknown -> "Decoder pending"
        BitPerfectConflictType.DitherActive -> "Dither"
    }

private fun dspGraphStageLine(stage: DspGraphStage): String =
    buildList {
        add(stage.owner.graphLabel())
        stage.bypassState?.let { add(it.graphLabel()) }
        stage.meter?.let { add(it.graphLabel()) }
        stage.gainChangeDb?.let { add("gain ${signedDb(it)}") }
        stage.headroomContributionDb?.let { add("headroom ${signedDb(it)}") }
        if (stage.clippingRiskContribution != ClippingRisk.None) add("risk ${stage.clippingRiskContribution.graphLabel()}")
        stage.inputFormat?.let { add("in ${it.graphLabel()}") }
        stage.outputFormat?.takeIf { it != stage.inputFormat }?.let { add("out ${it.graphLabel()}") }
        stage.diagnostics.forEach(::add)
        stage.notes?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" | ")

private fun DspStageBypassState.graphLabel(): String =
    when {
        bypassed -> "stage bypassed"
        userBypassable -> "bypass-ready"
        else -> "fixed"
    }

private fun DspStageMeterSnapshot.graphLabel(): String =
    buildList {
        add(if (live) "meter live" else "meter ${source.graphLabel()}")
        gainDeltaDb?.let { add("delta ${signedDb(it)}") }
    }.joinToString(" ")

private fun DspGraphMeteringSnapshot.graphLabel(): String =
    "${source.graphLabel()}  |  ${if (perStageMetersAvailable) "per-stage meters available" else "no live per-stage meters"}  |  $notes"

private fun GraphicEqDiagnosticsSnapshot.graphLabel(): String =
    buildList {
        add("$expectedSlotCount slots")
        add("${activeSlotCount} active")
        add("range ${signedDb(effectiveMinGainDb)}..${signedDb(effectiveMaxGainDb)}")
        if (extendedRangeRequested && !extendedRangeEffective) add("extended pending")
        add("max ${signedDb(maxGainDb)}")
        add("effective ${signedDb(maxEffectiveGainDb)}")
        if (headroomContributionDb < 0f) add("headroom ${signedDb(headroomContributionDb)}")
        add(owner.graphLabel())
        notes.firstOrNull()?.let(::add)
    }.joinToString("  |  ")

private fun ParametricEqDiagnosticsSnapshot.graphLabel(): String =
    buildList {
        add("$expectedSlotCount slots")
        add("${activeSlotCount} active")
        add(if (nativeOnly) "native-only" else "shared")
        add(owner.graphLabel())
        add(if (allDisabledSlotsNeutral) "disabled neutral" else "disabled check")
        notes.getOrNull(1)?.let(::add)
    }.joinToString("  |  ")

private fun ToneDiagnosticsSnapshot.graphLabel(): String =
    buildList {
        add("${controls.count { it.active }} active")
        add("max ${signedDb(maxBoostDb)}")
        if (headroomContributionDb < 0f) add("headroom ${signedDb(headroomContributionDb)}")
        add(owner.graphLabel())
        if (platformEqualizerToneEligible) add("platform EQ tone path")
        if (platformBassBoostEligible) add("BassBoost eligible")
        if (bypassed) add("bypassed")
        if (safeModeDisabled) add("safe mode")
        add(if (resetByFlatPreset) "Flat resets tone" else "Flat reset unknown")
    }.joinToString("  |  ")

private fun SmartPreampRecommendation.graphLabel(): String =
    buildList {
        add(reason.graphLabel())
        add("recommend ${signedDb(recommendedPreampDb)}")
        add("EQ ${signedDb(eqBoostDb)}")
        add("Tone ${signedDb(toneBoostDb)}")
        add("RG ${signedDb(replayGainBoostDb)}")
        if (dynamicsMakeupDb > 0f) add("Dynamics ${signedDb(dynamicsMakeupDb)}")
        if (timeFxRiskDb > 0f) add("Time FX ${signedDb(timeFxRiskDb)}")
        add("target ${signedDb(targetHeadroomDb)}")
        add("trim ${signedDb(preventClippingTrimDb)}")
        add(confidence.graphLabel())
    }.joinToString("  |  ")

private fun LoudnessGroundworkSnapshot.graphLabel(): String =
    buildList {
        add("ReplayGain ${replayGainMode.label}")
        add("gain ${signedDb(replayGainDb)}")
        add(measurementStatus.graphLabel())
        measuredIntegratedLufs?.let { add(String.format(Locale.US, "%.1f LUFS", it)) }
        add("source ${source.graphLabel()}")
        add("true peak ${truePeak.status.graphLabel()}")
    }.joinToString("  |  ")

private fun HeadroomManagerSnapshot.graphLabel(): String =
    buildList {
        add("risk ${signedDb(totalPositiveRiskDb)}")
        add("target ${signedDb(targetHeadroomDb)}")
        add("applied ${signedDb(appliedPreventClippingTrimDb)}")
        add("trim ${signedDb(outputTrimDb)}")
        add("clip ${clippingRisk.graphLabel()}")
    }.joinToString("  |  ")

private fun LookAheadLimiterGroundworkSnapshot.graphLabel(): String =
    buildList {
        add(if (implemented) "implemented" else "not implemented")
        add(if (active) "active" else "inactive")
        add(owner.graphLabel())
        add("look-ahead ${String.format(Locale.US, "%.0f ms", lookAheadMs)}")
        add(currentProtection.graphLabel())
        add(reason)
    }.joinToString("  |  ")

private fun SmartPreampReason.graphLabel(): String =
    when (this) {
        SmartPreampReason.Neutral -> "Neutral"
        SmartPreampReason.EqBoost -> "EQ boost"
        SmartPreampReason.ToneBoost -> "Tone boost"
        SmartPreampReason.ReplayGainBoost -> "ReplayGain boost"
        SmartPreampReason.CombinedBoost -> "Combined boost"
        SmartPreampReason.LimiterProtection -> "Limiter protection"
        SmartPreampReason.Unknown -> "Unknown"
    }

private fun SmartPreampConfidence.graphLabel(): String =
    when (this) {
        SmartPreampConfidence.Low -> "low confidence"
        SmartPreampConfidence.Medium -> "medium confidence"
        SmartPreampConfidence.High -> "high confidence"
        SmartPreampConfidence.Unknown -> "unknown confidence"
    }

private fun LoudnessMeasurementStatus.graphLabel(): String =
    when (this) {
        LoudnessMeasurementStatus.NotMeasured -> "not measured"
        LoudnessMeasurementStatus.FileTag -> "file-tag LUFS"
        LoudnessMeasurementStatus.ScannerAnalysis -> "scanner LUFS"
        LoudnessMeasurementStatus.ReplayGainOnly -> "ReplayGain only"
        LoudnessMeasurementStatus.StreamMetadataNotMeasured -> "stream LUFS not measured"
        LoudnessMeasurementStatus.Unknown -> "unknown"
    }

private fun LoudnessSource.graphLabel(): String =
    when (this) {
        LoudnessSource.None -> "none"
        LoudnessSource.FileTag -> "file tag"
        LoudnessSource.ScannerAnalysis -> "scanner"
        LoudnessSource.StreamMetadata -> "stream metadata"
        LoudnessSource.UserManual -> "manual"
    }

private fun TruePeakStatus.graphLabel(): String =
    when (this) {
        TruePeakStatus.NotMeasured -> "not measured"
        TruePeakStatus.PlaceholderOnly -> "placeholder"
        TruePeakStatus.ReplayGainSamplePeakOnly -> "sample peak only"
        TruePeakStatus.Measured -> "measured"
    }

private fun LimiterProtectionMode.graphLabel(): String =
    when (this) {
        LimiterProtectionMode.None -> "no limiter protection"
        LimiterProtectionMode.NativeSoftLimiter -> "native soft limiter"
        LimiterProtectionMode.LookAheadPlanned -> "look-ahead planned"
    }

private fun DspMeterSource.graphLabel(): String =
    when (this) {
        DspMeterSource.Placeholder -> "placeholder"
        DspMeterSource.ModelEstimate -> "model"
        DspMeterSource.NativeOutput -> "native output"
        DspMeterSource.Unavailable -> "unavailable"
    }

private fun AudioFormatSummary.graphLabel(): String =
    buildList {
        add(encoding)
        sampleRateHz?.let { add(audioChainSampleRateLabel(it)) }
        channels?.let { add(if (it == 1) "mono" else if (it == 2) "stereo" else "$it ch") }
    }.joinToString(" ")

private fun DspStageOwner.graphLabel(): String =
    when (this) {
        DspStageOwner.None -> "No live owner"
        DspStageOwner.Media3 -> "Media3"
        DspStageOwner.NativeDsp -> "Native DSP"
        DspStageOwner.AndroidPlatformEffect -> "Android platform FX"
        DspStageOwner.PlayerVolume -> "Player volume"
        DspStageOwner.ModelOnly -> "Model only"
        DspStageOwner.SystemOutput -> "System output"
        DspStageOwner.Unavailable -> "Unavailable"
    }

private fun DspStageStatus.graphLabel(): String =
    when (this) {
        DspStageStatus.Transparent -> "Transparent"
        DspStageStatus.Active -> "Active"
        DspStageStatus.Bypassed -> "Bypassed"
        DspStageStatus.Disabled -> "Disabled"
        DspStageStatus.SafeModeDisabled -> "Safe mode"
        DspStageStatus.Unsupported -> "Unsupported"
        DspStageStatus.Planned -> "Planned"
        DspStageStatus.Unknown -> "Unknown"
    }

private fun ClippingRisk.graphLabel(): String =
    when (this) {
        ClippingRisk.None -> "None"
        ClippingRisk.Low -> "Low"
        ClippingRisk.Medium -> "Medium"
        ClippingRisk.High -> "High"
        ClippingRisk.Unknown -> "Unknown"
    }

private fun audioChainSampleRateLabel(sampleRateHz: Int): String {
    val khz = sampleRateHz / 1000f
    return if (sampleRateHz % 1000 == 0) {
        "${khz.roundToInt()} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", khz)
    }
}

@Composable
private fun AudioDeckDynamicsAdvancedDrawers(
    state: AudioEngineState,
    onState: (AudioEngineState) -> Unit,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioDeckPillButton("Comp Detail", DeckIcon.Sliders, Modifier.weight(1f), active = selected == "comp") { onSelected("comp") }
        AudioDeckPillButton("Gate Detail", DeckIcon.Focus, Modifier.weight(1f), active = selected == "gate") { onSelected("gate") }
        AudioDeckPillButton("Limit Detail", DeckIcon.Loudness, Modifier.weight(1f), active = selected == "limiter") { onSelected("limiter") }
    }
    when (selected) {
        "comp" -> {
            AudioDeckInlineSlider("Attack", "${state.compressor.attackMs.roundToInt()} ms", "Fast", "Slow", state.compressor.attackMs, 0.1f..200f) {
                onState(state.copy(compressor = state.compressor.copy(attackMs = it, enabled = true, mix = if (state.compressor.mix == 0f) 0.65f else state.compressor.mix)))
            }
            AudioDeckInlineSlider("Release", "${state.compressor.releaseMs.roundToInt()} ms", "Tight", "Smooth", state.compressor.releaseMs, 5f..1500f) {
                onState(state.copy(compressor = state.compressor.copy(releaseMs = it, enabled = true, mix = if (state.compressor.mix == 0f) 0.65f else state.compressor.mix)))
            }
            AudioDeckInlineSlider("Makeup Gain", signedDb(state.compressor.makeupDb), "-12", "+12", state.compressor.makeupDb, -12f..12f) {
                onState(state.copy(compressor = state.compressor.copy(makeupDb = it, enabled = true, mix = if (state.compressor.mix == 0f) 0.65f else state.compressor.mix)))
            }
        }
        "gate" -> {
            AudioDeckInlineSlider("Gate Threshold", signedDb(state.gate.thresholdDb), "-80", "0", state.gate.thresholdDb, -80f..0f) {
                onState(state.copy(gate = state.gate.copy(thresholdDb = it, enabled = true)))
            }
            AudioDeckInlineSlider("Gate Attack", "${state.gate.attackMs.roundToInt()} ms", "Fast", "Slow", state.gate.attackMs, 0.1f..200f) {
                onState(state.copy(gate = state.gate.copy(attackMs = it, enabled = true)))
            }
            AudioDeckInlineSlider("Gate Hold", "${state.gate.holdMs.roundToInt()} ms", "Short", "Long", state.gate.holdMs, 0f..1000f) {
                onState(state.copy(gate = state.gate.copy(holdMs = it, enabled = true)))
            }
            AudioDeckInlineSlider("Gate Release", "${state.gate.releaseMs.roundToInt()} ms", "Tight", "Smooth", state.gate.releaseMs, 5f..1500f) {
                onState(state.copy(gate = state.gate.copy(releaseMs = it, enabled = true)))
            }
        }
        "limiter" -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Soft Limiter", color = Color.White, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                AudioDeckMiniSwitch("Limit", state.limiter.enabled) {
                    onState(state.copy(limiter = state.limiter.copy(enabled = it)))
                }
            }
            AudioDeckInlineSlider("Ceiling", signedDb(state.limiter.ceilingDb), "-6", "0", state.limiter.ceilingDb, -6f..0f) {
                onState(state.copy(limiter = state.limiter.copy(ceilingDb = it, enabled = true)))
            }
            AudioDeckInlineSlider("Release", "${state.limiter.releaseMs.roundToInt()} ms", "Fast", "Smooth", state.limiter.releaseMs, 5f..1500f) {
                onState(state.copy(limiter = state.limiter.copy(releaseMs = it, enabled = true)))
            }
            AudioDeckInlineSlider("Strength", "${(state.limiter.strength * 100f).roundToInt()}%", "Soft", "Firm", state.limiter.strength, 0f..1f) {
                onState(state.copy(limiter = state.limiter.copy(strength = it, enabled = true)))
            }
        }
    }
}

@Composable
private fun AudioDeckModulationAdvancedDrawer(
    state: AudioEngineState,
    onState: (AudioEngineState) -> Unit,
    expanded: Boolean,
    onExpanded: () -> Unit,
) {
    AudioDeckPillButton("Mod Detail", DeckIcon.Sliders, Modifier.fillMaxWidth(), active = expanded || state.modulation.feedback > 0.101f, onClick = onExpanded)
    if (expanded) {
        AudioDeckInlineSlider("Mod Feedback", "${(state.modulation.feedback * 100f).roundToInt()}%", "Clean", "Resonant", state.modulation.feedback, 0f..0.9f) {
            onState(state.copy(modulation = state.modulation.copy(feedback = it)))
        }
    }
}

@Composable
private fun AudioDeckParametricEqDrawer(
    state: AudioEngineState,
    onState: (AudioEngineState) -> Unit,
) {
    val parametricBands = canonicalParametricEqBands(state.parametricEq)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Parametric EQ", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                Text("${parametricBands.count { it.isActiveFilter() }} active filters  |  ${parametricBands.size} slots", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            }
            AudioDeckDspBadge(audioDeckRouteLabel(state, nativeOnly = true), active = parametricBands.any { it.isActiveFilter() }, warning = !NativeAudioEngineBridge.isAvailable && parametricBands.any { it.isActiveFilter() })
        }
        AudioDeckCleanEqPanel(
            Modifier.fillMaxWidth(),
            padding = 10.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                parametricBands.forEachIndexed { index, band ->
                    AudioDeckParametricBandEditor(index, band) { next ->
                        onState(state.withParametricEqBand(index, next))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AudioDeckLiquidPillButton("Reset", DeckIcon.Repeat, Modifier.weight(1f)) {
                onState(state.withParametricEqDefaults())
            }
            AudioDeckLiquidPillButton("Enable All", DeckIcon.Check, Modifier.weight(1f), active = parametricBands.all { it.enabled }) {
                val enable = !parametricBands.all { it.enabled }
                onState(state.copy(parametricEq = parametricBands.map { it.copy(enabled = enable) }))
            }
        }
    }
}

@Composable
private fun AudioDeckParametricBandEditor(
    index: Int,
    band: ParametricEqBand,
    onBand: (ParametricEqBand) -> Unit,
) {
    val bandActive = band.isActiveFilter()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (bandActive) 0.064f else 0.042f),
                        Color.White.copy(alpha = if (bandActive) 0.036f else 0.024f),
                    ),
                ),
            )
            .border(1.dp, if (bandActive) AudioDeckBlueLight.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Band ${index + 1}", color = Color.White, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
                Text("${band.frequencyHz.roundToInt()} Hz  |  ${band.type.label}", color = Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            }
            AudioDeckMiniSwitch("On", band.enabled) { onBand(band.copy(enabled = it)) }
            AudioDeckLiquidPillButton(band.type.label, DeckIcon.Sliders, Modifier.width(112.dp), active = band.enabled) {
                onBand(band.copy(type = nextParametricFilterType(band.type), enabled = true))
            }
        }
        AudioDeckInlineSlider("Frequency", "${band.frequencyHz.roundToInt()} Hz", "20", "20k", band.frequencyHz, PARAMETRIC_EQ_MIN_FREQUENCY_HZ..PARAMETRIC_EQ_MAX_FREQUENCY_HZ, liquid = true) {
            onBand(band.copy(frequencyHz = it, enabled = true))
        }
        AudioDeckInlineSlider("Gain", signedDb(band.gainDb), "-12", "+12", band.gainDb, AUDIO_EQ_MIN_GAIN_DB..AUDIO_EQ_MAX_GAIN_DB, liquid = true) {
            onBand(band.copy(gainDb = it, enabled = true))
        }
        AudioDeckInlineSlider("Q", String.format(Locale.US, "%.1f", band.q), "0.1", "18", band.q, PARAMETRIC_EQ_MIN_Q..PARAMETRIC_EQ_MAX_Q, liquid = true) {
            onBand(band.copy(q = it, enabled = true))
        }
    }
}

private fun nextParametricFilterType(type: ParametricFilterType): ParametricFilterType =
    ParametricFilterType.entries[(type.ordinal + 1) % ParametricFilterType.entries.size]

@Composable
private fun AudioDeckCleanEqCard(
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF121722).copy(alpha = 0.96f),
                        Color(0xFF080D17).copy(alpha = 0.98f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun AudioDeckCleanEqPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.060f),
                        Color.White.copy(alpha = 0.034f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.120f), shape)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun AudioDeckPanel(padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 14f
                this.shape = shape
                clip = false
            },
    ) {
        AudioDeckBlueGlassGlow(Modifier.matchParentSize(), alpha = 0.74f)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.052f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun AudioDeckEqGlassCard(
    padding: Dp = 14.dp,
    matteBlack: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val grain = remember {
        val random = kotlin.random.Random(3037)
        List(44) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = if (matteBlack) 10f else 18f
                this.shape = shape
                clip = false
            },
    ) {
        if (!matteBlack) {
            AudioDeckBlueGlassGlow(Modifier.matchParentSize(), alpha = 0.95f)
        }
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(if (matteBlack) Color(0xFF06080D).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.060f))
                .border(1.dp, Color.White.copy(alpha = if (matteBlack) 0.105f else 0.18f), shape),
        )
        Canvas(
            Modifier
                .matchParentSize()
                .clip(shape)
                .blur(4.dp),
        ) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    if (matteBlack) {
                        listOf(
                            Color.White.copy(alpha = 0.028f),
                            Color.White.copy(alpha = 0.006f),
                            Color.Transparent,
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.045f),
                            Color.White.copy(alpha = 0.012f),
                            Color.White.copy(alpha = 0.006f),
                        )
                    },
                ),
                cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
            )
            val lowerGlass = Path().apply {
                moveTo(-size.width * 0.08f, size.height * 0.58f)
                cubicTo(size.width * 0.18f, size.height * 0.44f, size.width * 0.46f, size.height * 0.70f, size.width * 0.73f, size.height * 0.53f)
                cubicTo(size.width * 0.94f, size.height * 0.40f, size.width * 1.06f, size.height * 0.58f, size.width * 1.08f, size.height * 0.80f)
                lineTo(size.width * 1.08f, size.height * 1.10f)
                lineTo(-size.width * 0.08f, size.height * 1.10f)
                close()
            }
            drawPath(
                lowerGlass,
                Brush.verticalGradient(
                    if (matteBlack) {
                        listOf(
                            AudioDeckBlue.copy(alpha = 0.035f),
                            Color.White.copy(alpha = 0.006f),
                            Color.Transparent,
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.030f),
                            Color.White.copy(alpha = 0.010f),
                            Color.White.copy(alpha = 0.004f),
                        )
                    },
                ),
            )
            val upperGlass = Path().apply {
                moveTo(-size.width * 0.05f, size.height * 0.22f)
                cubicTo(size.width * 0.18f, size.height * 0.06f, size.width * 0.50f, size.height * 0.18f, size.width * 0.74f, size.height * 0.09f)
                cubicTo(size.width * 0.95f, size.height * 0.02f, size.width * 1.05f, size.height * 0.13f, size.width * 1.07f, size.height * 0.34f)
                lineTo(size.width * 1.07f, -size.height * 0.08f)
                lineTo(-size.width * 0.05f, -size.height * 0.08f)
                close()
            }
            drawPath(upperGlass, Color.White.copy(alpha = if (matteBlack) 0.018f else 0.026f))
        }
        Canvas(Modifier.matchParentSize()) {
            grain.forEach { point ->
                val speckAlpha = 0.002f + point.third * 0.004f
                val speckColor = if (point.third > 0.46f) {
                    Color.White.copy(alpha = speckAlpha)
                } else {
                    Color.White.copy(alpha = speckAlpha * 0.36f)
                }
                drawCircle(
                    color = speckColor,
                    radius = (0.18f + point.third * 0.40f).dp.toPx(),
                    center = Offset(size.width * point.first, size.height * point.second),
                )
            }
            drawRoundRect(
                color = Color.White.copy(alpha = if (matteBlack) 0.060f else 0.085f),
                cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                style = Stroke(width = 0.8.dp.toPx()),
            )
            drawLine(
                color = Color.White.copy(alpha = if (matteBlack) 0.030f else 0.045f),
                start = Offset(size.width * 0.08f, size.height * 0.08f),
                end = Offset(size.width * 0.56f, size.height * 0.08f),
                strokeWidth = 0.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun AudioDeckEqForegroundPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 8.dp,
    matteBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val panelShape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .graphicsLayer {
                shadowElevation = if (matteBlack) 0f else 18f
                shape = panelShape
                clip = false
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!matteBlack) {
            AudioDeckBlueGlassGlow(Modifier.matchParentSize(), alpha = 0.42f)
        }
        Box(
            Modifier
                .matchParentSize()
                .clip(panelShape)
                .background(if (matteBlack) Color(0xFF050608).copy(alpha = 0.98f) else Color.White.copy(alpha = 0.045f))
                .border(1.dp, Color.White.copy(alpha = if (matteBlack) 0.075f else 0.145f), panelShape),
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun AudioDeckBlueGlassGlow(modifier: Modifier = Modifier, alpha: Float = 1f) {
    Canvas(modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        val mainCenter = Offset(size.width * 0.50f, size.height * 0.50f)
        val mainRadius = size.maxDimension * 0.58f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AudioDeckBlue.copy(alpha = 0.20f * alpha),
                    AudioDeckBlue.copy(alpha = 0.075f * alpha),
                    Color.Transparent,
                ),
                center = mainCenter,
                radius = mainRadius,
            ),
            center = mainCenter,
            radius = mainRadius,
        )
        val topLight = Offset(size.width * 0.72f, size.height * 0.18f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.075f * alpha),
                    AudioDeckBlueLight.copy(alpha = 0.055f * alpha),
                    Color.Transparent,
                ),
                center = topLight,
                radius = size.maxDimension * 0.34f,
            ),
            center = topLight,
            radius = size.maxDimension * 0.34f,
        )
    }
}

@Composable
private fun AudioDeckSavedPill(saved: Boolean) {
    val tint = if (saved) AudioDeckBlueLight else AudioEqYellow
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.105f))
            .border(1.dp, tint.copy(alpha = 0.36f), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(if (saved) "Saved" else "Edited", color = tint, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        Box(Modifier.size(13.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            PulseIcon(DeckIcon.Check, Color(0xFF061024), Modifier.size(8.dp))
        }
    }
}

@Composable
private fun AudioDeckEqFaderBank(
    state: AudioEngineState,
    graphicBands: List<GraphicEqBand>,
    graphicEqGainRange: ClosedFloatingPointRange<Float>,
    onState: (AudioEngineState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Column(
            Modifier
                .width(18.dp)
                .height(218.dp)
                .padding(top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text("+", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black)
            Text("0", color = Color.White.copy(alpha = 0.58f), fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black)
            Text("-", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(5.dp))
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .height(230.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            item(key = "preamp") {
                NeonVerticalSlider(
                    label = "Pre",
                    value = state.preampDb,
                    range = AUDIO_EQ_MIN_GAIN_DB..AUDIO_EQ_MAX_GAIN_DB,
                    valueText = signedDb(state.preampDb),
                    onChange = { onState(state.copy(preampDb = it)) },
                    modifier = Modifier.width(52.dp),
                )
            }
            items(graphicBands.withIndex().toList(), key = { it.value.frequencyHz }) { indexed ->
                val index = indexed.index
                val band = indexed.value
                val effectiveGain = band.gainDb.coerceIn(graphicEqGainRange.start, graphicEqGainRange.endInclusive)
                NeonVerticalSlider(
                    label = audioDeckCompactBandLabel(band),
                    value = effectiveGain,
                    range = graphicEqGainRange,
                    valueText = audioDeckEffectiveDbLabel(band.gainDb, effectiveGain),
                    onChange = { gain ->
                        onState(state.withGraphicEqBandGain(index, gain))
                    },
                    modifier = Modifier.width(52.dp),
                )
            }
        }
    }
}

@Composable
private fun NeonVerticalSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnChange by rememberUpdatedState(onChange)
    val accent = audioEqAccentForValue(value, range)
    val accentBright = lerp(accent, Color.White, 0.28f).copy(alpha = 0.98f)
    var dragging by remember { mutableStateOf(false) }
    fun valueFromY(y: Float, height: Float): Float {
        val top = height * 0.02f
        val bottom = height * 0.92f
        val progress = ((bottom - y) / (bottom - top).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val span = range.endInclusive - range.start
        val raw = range.start + progress * span
        val smooth = (raw * 10f).roundToInt() / 10f
        return smooth.coerceIn(range.start, range.endInclusive)
    }
    fun publishFromY(y: Float, height: Float) {
        latestOnChange(valueFromY(y, height))
    }
    fun resetToNeutral() {
        dragging = true
        latestOnChange(0f.coerceIn(range.start, range.endInclusive))
        dragging = false
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    setProgress { requested ->
                        latestOnChange(quantizeAudioDb(requested, range))
                        true
                    }
                }
                .pointerInput(range) {
                    detectTapGestures(
                        onTap = { offset ->
                            dragging = true
                            publishFromY(offset.y, size.height.toFloat())
                            dragging = false
                        },
                        onDoubleTap = { resetToNeutral() },
                    )
                }
                .pointerInput(range) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            publishFromY(offset.y, size.height.toFloat())
                        },
                        onVerticalDrag = { change, _ ->
                            publishFromY(change.position.y, size.height.toFloat())
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            val progress = audioDeckProgress(value, range)
            val neutralProgress = audioDeckProgress(0f.coerceIn(range.start, range.endInclusive), range)
            val energyAnchorProgress by animateFloatAsState(
                targetValue = if (dragging) neutralProgress else progress,
                animationSpec = if (dragging) {
                    tween(durationMillis = 90)
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    )
                },
                label = "eqFaderEnergyCollapse",
            )
            val bubbleHeight = 42.dp
            val knobY = maxHeight * (0.91f - progress * 0.84f)
            val density = LocalDensity.current
            val maxBubbleOffsetPx = with(density) { (maxHeight - bubbleHeight).toPx() }.roundToInt().coerceAtLeast(0)
            val bubbleOffsetPx = with(density) { (knobY - bubbleHeight - 10.dp).toPx() }.roundToInt().coerceIn(0, maxBubbleOffsetPx)
            Canvas(
                Modifier
                    .fillMaxSize()
            ) {
                val centerX = size.width / 2f
                val top = size.height * 0.07f
                val bottom = size.height * 0.91f
                val railWidth = min(size.width * 0.56f, 28.dp.toPx())
                val railLeft = centerX - railWidth / 2f
                val railHeight = bottom - top
                val railRadius = railWidth / 2f
                val zero = bottom - audioDeckProgress(0f.coerceIn(range.start, range.endInclusive), range) * (bottom - top)
                val knobYPx = bottom - progress * (bottom - top)
                val energyAnchorYPx = bottom - energyAnchorProgress * (bottom - top)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.090f),
                            Color.White.copy(alpha = 0.035f),
                            Color.White.copy(alpha = 0.055f),
                        ),
                    ),
                    topLeft = Offset(railLeft, top),
                    size = Size(railWidth, railHeight),
                    cornerRadius = CornerRadius(railRadius, railRadius),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.145f),
                    topLeft = Offset(railLeft, top),
                    size = Size(railWidth, railHeight),
                    cornerRadius = CornerRadius(railRadius, railRadius),
                    style = Stroke(width = 1.15.dp.toPx()),
                )
                val markerRadius = railWidth * 0.14f
                listOf(0.10f, 0.30f, 0.50f, 0.70f, 0.90f).forEach { marker ->
                    val markerY = top + railHeight * marker
                    val alpha = if (abs(markerY - zero) < railHeight * 0.035f) 0.24f else 0.13f
                    drawCircle(Color.White.copy(alpha = alpha), markerRadius, Offset(centerX, markerY))
                }
                val activeTop = min(energyAnchorYPx, knobYPx)
                val activeHeight = abs(knobYPx - energyAnchorYPx)
                if (activeHeight > 1f) {
                    val energyPositive = knobYPx <= energyAnchorYPx
                    val activeBrush = if (energyPositive) {
                        Brush.verticalGradient(
                            listOf(
                                lerp(accent, Color.White, 0.22f).copy(alpha = if (dragging) 0.98f else 0.88f),
                                accent.copy(alpha = if (dragging) 0.90f else 0.76f),
                                AudioNeonGreen.copy(alpha = if (dragging) 0.72f else 0.58f),
                            ),
                            startY = activeTop,
                            endY = activeTop + activeHeight,
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                AudioNeonGreen.copy(alpha = if (dragging) 0.72f else 0.58f),
                                AudioEqAqua.copy(alpha = if (dragging) 0.88f else 0.72f),
                                lerp(accent, Color.White, 0.16f).copy(alpha = if (dragging) 0.96f else 0.84f),
                            ),
                            startY = activeTop,
                            endY = activeTop + activeHeight,
                        )
                    }
                    drawRoundRect(
                        brush = activeBrush,
                        topLeft = Offset(railLeft + railWidth * 0.18f, activeTop),
                        size = Size(railWidth * 0.64f, activeHeight),
                        cornerRadius = CornerRadius(railWidth * 0.32f, railWidth * 0.32f),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (dragging) 0.20f else 0.12f),
                        topLeft = Offset(railLeft + railWidth * 0.30f, activeTop + railWidth * 0.12f),
                        size = Size(railWidth * 0.18f, (activeHeight - railWidth * 0.24f).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius(railWidth * 0.09f, railWidth * 0.09f),
                    )
                }
                val handleRadius = railWidth * 0.39f
                drawCircle(Color.Black.copy(alpha = 0.22f), radius = handleRadius * 1.08f, center = Offset(centerX, knobYPx + railWidth * 0.06f))
                drawCircle(Color(0xFF05070B), radius = handleRadius, center = Offset(centerX, knobYPx))
                drawCircle(accent.copy(alpha = if (dragging) 0.72f else 0.44f), radius = handleRadius, center = Offset(centerX, knobYPx), style = Stroke(railWidth * 0.070f))
            }
            if (dragging) {
                AudioEqValueBubble(
                    value = valueText,
                    accent = accent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, bubbleOffsetPx) },
                )
            }
        }
        Text(label, color = Color.White.copy(alpha = 0.84f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudioEqValueBubble(value: String, accent: Color, modifier: Modifier = Modifier) {
    val accentBright = lerp(accent, Color.White, 0.18f)
    Box(
        modifier
            .size(width = 72.dp, height = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(value, color = accentBright, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun ToneKnobCell(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clipToBounds(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeonKnob(
            label = label,
            value = value,
            range = range,
            valueText = valueText,
            onChange = onChange,
            knobSize = 108.dp,
        )
    }
}

@Composable
private fun NeonKnob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    knobSize: Dp = 156.dp,
    resetValue: Float = audioKnobDefaultResetValue(label, range),
) {
    val haptics = LocalHapticFeedback.current
    val latestOnChange by rememberUpdatedState(onChange)
    val latestValue by rememberUpdatedState(value)
    var pressing by remember { mutableStateOf(false) }
    var draggingKnob by remember { mutableStateOf(false) }
    val touchProgress by animateFloatAsState(
        targetValue = if (pressing || draggingKnob) 1f else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "precisionKnobTouch",
    )
    val knobTouchScale = 1f + touchProgress * 0.075f
    val texture = remember {
        val random = kotlin.random.Random(8181)
        List(44) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    var lastTick by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var lastEdgeTick by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var dragLastAngle by remember { mutableFloatStateOf(0f) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    fun knobStepSize(): Float {
        val span = range.endInclusive - range.start
        return when {
            span <= 2f -> 0.01f
            span <= 24f -> 0.1f
            span <= 90f -> 0.5f
            else -> 1f
        }
    }
    fun steppedValue(raw: Float): Float {
        val stepSize = knobStepSize()
        return ((raw / stepSize).roundToInt() * stepSize).coerceIn(range.start, range.endInclusive)
    }
    fun publish(next: Float) {
        val stepSize = knobStepSize()
        val stepped = steppedValue(next)
        val tick = (stepped / stepSize).roundToInt()
        if (tick != lastTick) {
            val edgeTick = when {
                stepped <= range.start + stepSize / 2f -> -1
                stepped >= range.endInclusive - stepSize / 2f -> 1
                else -> 0
            }
            if (edgeTick != 0) {
                if (lastEdgeTick != edgeTick) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    lastEdgeTick = edgeTick
                }
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastEdgeTick = 0
            }
            lastTick = tick
        }
        latestOnChange(stepped)
    }
    fun angleFromTouch(offset: Offset, width: Int, height: Int): Float {
        val center = Offset(width / 2f, height / 2f)
        return (Math.toDegrees(atan2(offset.y - center.y, offset.x - center.x).toDouble()).toFloat() + 360f) % 360f
    }
    fun progressFromAngle(angle: Float): Float {
        val startAngle = -90f
        val normalized = (angle - startAngle + 360f) % 360f
        return (normalized / 360f).coerceIn(0f, 1f)
    }
    fun valueFromProgress(progress: Float): Float =
        range.start + progress.coerceIn(0f, 1f) * (range.endInclusive - range.start)

    fun valueFromTouch(offset: Offset, width: Int, height: Int): Float {
        val progress = progressFromAngle(angleFromTouch(offset, width, height))
        val currentProgress = audioDeckProgress(latestValue, range)
        val seamGuardedProgress = when {
            currentProgress <= 0.05f && progress >= 0.82f -> 0f
            currentProgress >= 0.95f && progress <= 0.18f -> 1f
            else -> progress
        }
        return valueFromProgress(seamGuardedProgress)
    }
    fun signedAngleDelta(from: Float, to: Float): Float =
        ((to - from + 540f) % 360f) - 180f

    fun valueFromDrag(offset: Offset, width: Int, height: Int): Float {
        val angle = angleFromTouch(offset, width, height)
        val delta = signedAngleDelta(dragLastAngle, angle)
        dragProgress = (dragProgress + delta / 360f).coerceIn(0f, 1f)
        dragLastAngle = angle
        val progress = dragProgress
        return range.start + progress.coerceIn(0f, 1f) * (range.endInclusive - range.start)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .requiredSize(knobSize)
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    setProgress { requested ->
                        latestOnChange(requested.coerceIn(range.start, range.endInclusive))
                        true
                    }
                }
                .pointerInput(range) {
                    detectTapGestures(
                        onPress = {
                            pressing = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressing = false
                            }
                        },
                        onTap = { offset ->
                            publish(valueFromTouch(offset, size.width, size.height))
                        },
                        onDoubleTap = {
                            pressing = true
                            publish(resetValue.coerceIn(range.start, range.endInclusive))
                            pressing = false
                        },
                    )
                }
                .pointerInput(range) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggingKnob = true
                            dragLastAngle = angleFromTouch(offset, size.width, size.height)
                            dragProgress = audioDeckProgress(latestValue, range)
                        },
                        onDrag = { change, _ -> publish(valueFromDrag(change.position, size.width, size.height)) },
                        onDragEnd = { draggingKnob = false },
                        onDragCancel = { draggingKnob = false },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .requiredSize(knobSize * 0.72f)
                    .graphicsLayer {
                        scaleX = knobTouchScale
                        scaleY = knobTouchScale
                    }
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF30343C),
                                Color(0xFF242832),
                                Color(0xFF171B23),
                            ),
                        ),
                        CircleShape,
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.085f), CircleShape),
            )
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = knobTouchScale
                        scaleY = knobTouchScale
                    },
            ) {
                val progress = audioDeckProgress(value, range)
                val knobExtent = size.minDimension
                val startAngle = -90f
                val sweepAngle = 359.6f
                val ringStroke = knobExtent * 0.021f
                val glowStroke = ringStroke * (2.64f + touchProgress * 0.90f)
                val ringRadius = knobExtent * 0.392f
                val bodyRadius = knobExtent * 0.350f
                val outerRadius = knobExtent * 0.420f
                val center = Offset(size.width / 2f, size.height / 2f)
                val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
                val arcSize = Size(ringRadius * 2f, ringRadius * 2f)
                val sweep = sweepAngle * progress
                val glowAlpha = (0.18f + progress * 0.54f + touchProgress * 0.24f).coerceIn(0f, 0.94f)
                val grooveLight = Brush.linearGradient(
                    listOf(
                        lerp(AudioPrecisionViolet, Color.White, touchProgress * 0.14f).copy(alpha = 0.82f + touchProgress * 0.12f),
                        lerp(AudioPrecisionVioletLight, Color.White, touchProgress * 0.22f).copy(alpha = 1f),
                        lerp(Color(0xFF7D8CFF), Color.White, touchProgress * 0.18f).copy(alpha = 0.88f + touchProgress * 0.10f),
                    ),
                    start = Offset(center.x - ringRadius, center.y + ringRadius),
                    end = Offset(center.x + ringRadius, center.y - ringRadius),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Transparent,
                        ),
                        center = center - Offset(knobExtent * 0.05f, knobExtent * 0.08f),
                        radius = outerRadius * 1.16f,
                    ),
                    outerRadius,
                    center,
                )
                drawCircle(Color.White.copy(alpha = 0.050f), outerRadius * 0.985f, center, style = Stroke(ringStroke * 0.62f))
                drawCircle(Color(0xFF0F1218).copy(alpha = 0.82f), ringRadius, center, style = Stroke(ringStroke * 1.48f, cap = StrokeCap.Round))
                drawCircle(Color(0xFF171A22).copy(alpha = 0.88f), ringRadius, center, style = Stroke(ringStroke * 0.90f, cap = StrokeCap.Round))
                drawCircle(Color.White.copy(alpha = 0.050f), ringRadius + ringStroke * 0.78f, center, style = Stroke(ringStroke * 0.14f, cap = StrokeCap.Round))
                drawCircle(Color.White.copy(alpha = 0.065f), ringRadius - ringStroke * 0.78f, center, style = Stroke(ringStroke * 0.12f, cap = StrokeCap.Round))
                if (progress > 0.002f) {
                    drawArc(
                        color = AudioPrecisionViolet.copy(alpha = glowAlpha * 0.30f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(glowStroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = grooveLight,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(ringStroke * (0.66f + touchProgress * 0.06f), cap = StrokeCap.Round),
                    )
                    if (touchProgress > 0.001f) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.20f * touchProgress),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(ringStroke * (0.30f + touchProgress * 0.10f), cap = StrokeCap.Round),
                        )
                    }
                }
                drawCircle(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Transparent,
                            Color.Transparent,
                        ),
                        start = center - Offset(bodyRadius * 0.72f, bodyRadius * 0.84f),
                        end = center + Offset(bodyRadius * 0.62f, bodyRadius * 0.82f),
                    ),
                    bodyRadius,
                    center,
                )
                val starterHoleAngle = Math.toRadians((startAngle + sweep).toDouble())
                val starterHoleRadius = bodyRadius * 0.665f
                val starterHoleCenter = Offset(
                    center.x + cos(starterHoleAngle).toFloat() * starterHoleRadius,
                    center.y + sin(starterHoleAngle).toFloat() * starterHoleRadius,
                )
                drawCircle(Color.Black.copy(alpha = 0.56f), ringStroke * 1.20f, starterHoleCenter + Offset(ringStroke * 0.10f, ringStroke * 0.16f))
                drawCircle(Color(0xFF070910).copy(alpha = 0.96f), ringStroke * 1.02f, starterHoleCenter)
                drawCircle(Color.White.copy(alpha = 0.075f), ringStroke * 0.94f, starterHoleCenter, style = Stroke(ringStroke * 0.16f))
                texture.forEach { point ->
                    val x = center.x + (point.first - 0.5f) * bodyRadius * 1.72f
                    val y = center.y + (point.second - 0.5f) * bodyRadius * 1.72f
                    val dx = x - center.x
                    val dy = y - center.y
                    if (dx * dx + dy * dy <= bodyRadius * bodyRadius) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.008f + point.third * 0.014f),
                            radius = size.minDimension * (0.0013f + point.third * 0.0019f),
                            center = Offset(x, y),
                        )
                    }
                }
                drawCircle(Color.White.copy(alpha = 0.032f), bodyRadius * 0.94f, center - Offset(knobExtent * 0.016f, knobExtent * 0.022f), style = Stroke(ringStroke * 0.34f))
                drawCircle(Color(0xFF070A10).copy(alpha = 0.24f), bodyRadius * 0.998f, center, style = Stroke(ringStroke * 0.38f))
                drawArc(
                    color = Color.White.copy(alpha = 0.108f),
                    startAngle = 204f,
                    sweepAngle = 92f,
                    useCenter = false,
                    topLeft = Offset(center.x - bodyRadius * 0.94f, center.y - bodyRadius * 0.94f),
                    size = Size(bodyRadius * 1.88f, bodyRadius * 1.88f),
                    style = Stroke(ringStroke * 0.28f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = Color(0xFF060910).copy(alpha = 0.34f),
                    startAngle = 30f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(center.x - bodyRadius * 1.006f, center.y - bodyRadius * 1.006f),
                    size = Size(bodyRadius * 2.012f, bodyRadius * 2.012f),
                    style = Stroke(ringStroke * 0.34f, cap = StrokeCap.Round),
                )
            }
        }
        PrecisionKnobDigitalValue(
            valueText = valueText,
            knobSize = knobSize,
            modifier = Modifier.padding(top = if (knobSize <= 112.dp) 5.dp else 7.dp),
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = if (knobSize <= 112.dp) 10.sp else 12.sp,
            lineHeight = if (knobSize <= 112.dp) 12.sp else 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PrecisionKnobDigitalValue(
    valueText: String,
    knobSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .width(knobSize * 0.82f)
            .height(if (knobSize <= 112.dp) 25.dp else 29.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            valueText,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = if (knobSize <= 112.dp) 10.sp else 12.sp,
            lineHeight = if (knobSize <= 112.dp) 12.sp else 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudioEqCurveGraph(state: AudioEngineState, modifier: Modifier = Modifier) {
    val graphShape = RoundedCornerShape(16.dp)
    Canvas(
        modifier
            .height(82.dp)
            .clip(graphShape)
            .background(Color(0xFF040506).copy(alpha = 0.99f))
            .border(1.dp, Color.White.copy(alpha = 0.085f), graphShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        val left = 10f
        val right = size.width - 10f
        val top = 4f
        val bottom = size.height - 5f
        val zeroY = top + (bottom - top) * 0.46f
        repeat(9) { index ->
            val y = top + (bottom - top) * index / 8f
            drawLine(
                if (index == 4) AudioNeonMint.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.085f),
                Offset(left, y),
                Offset(right, y),
                strokeWidth = if (index == 4) 1.8f else 1f,
            )
        }
        repeat(10) { index ->
            val x = left + (right - left) * index / 9f
            drawLine(Color.White.copy(alpha = 0.055f), Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
        }
        val bands = canonicalGraphicEqBands(state.eqBands, state.graphicEqRangeMode.requestedGainRange())
        val graphRange = state.effectiveGraphicEqGainRange(nativeAudioActivationFor(state, NativeAudioEngineBridge.isAvailable).media3DspActive)
        val graphMaxAbs = max(abs(graphRange.start), abs(graphRange.endInclusive)).coerceAtLeast(1f)
        val values = bands.map { (it.gainDb.coerceIn(graphRange.start, graphRange.endInclusive) + state.preampDb).coerceIn(graphRange.start, graphRange.endInclusive) }
        val points = values.mapIndexed { index, value ->
            val x = left + (right - left) * index / (bands.size - 1).coerceAtLeast(1)
            val y = zeroY - (value / graphMaxAbs) * (bottom - top) / 2f
            Offset(x, y)
        }
        if (points.size >= 2) {
            val curve = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (index in 0 until points.lastIndex) {
                    val p0 = points.getOrElse(index - 1) { points[index] }
                    val p1 = points[index]
                    val p2 = points[index + 1]
                    val p3 = points.getOrElse(index + 2) { p2 }
                    val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
                    val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
                }
            }
            val curveColors = values.map { lerp(audioEqAccentForValue(it, graphRange), Color.White, 0.14f) }
            val fillPath = Path().apply {
                addPath(curve)
                lineTo(points.last().x, zeroY)
                lineTo(points.first().x, zeroY)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(
                        AudioNeonMint.copy(alpha = 0.28f),
                        AudioNeonGreen.copy(alpha = 0.090f),
                        Color.Transparent,
                    ),
                    startY = top,
                    endY = bottom,
                ),
            )
            drawPath(
                path = curve,
                color = AudioNeonMint.copy(alpha = 0.34f),
                style = Stroke(width = 11f, cap = StrokeCap.Round),
            )
            drawPath(
                path = curve,
                color = Color.White.copy(alpha = 0.24f),
                style = Stroke(width = 5.4f, cap = StrokeCap.Round),
            )
            drawPath(
                path = curve,
                brush = Brush.linearGradient(curveColors, start = Offset(left, 0f), end = Offset(right, 0f)),
                style = Stroke(width = 3.8f, cap = StrokeCap.Round),
            )
            points.forEachIndexed { index, point ->
                val color = audioEqAccentForValue(values[index], graphRange)
                drawCircle(color.copy(alpha = 0.38f), radius = 6.2f, center = point)
                drawCircle(lerp(color, Color.White, 0.28f), radius = 2.8f, center = point)
            }
        }
    }
}

@Composable
private fun AudioDeckEngineToggle(active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .size(width = 66.dp, height = 34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (active) AudioDeckBlue.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.060f))
            .border(1.dp, if (active) AudioDeckBlueLight.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(17.dp))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Switch
                contentDescription = "Audio engine"
                stateDescription = if (active) "On" else "Off"
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(start = 9.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PulseIcon(DeckIcon.Loudness, if (active) AudioDeckBlueLight else Color.White.copy(alpha = 0.62f), Modifier.size(17.dp))
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (active) AudioDeckBlueLight else Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = if (active) 0.24f else 0.08f), CircleShape),
        )
    }
}

@Composable
private fun AudioDeckInfoPanel(state: AudioEngineState, activePreset: AudioPreset, headroomDb: Float) {
    AudioDeckPanel(padding = 12.dp) {
        Text(
            if (state.processingActive) "Engine on: saved EQ, tone, and effects are live." else "Engine off: changes are saved, playback stays transparent until enabled.",
            color = Color.White.copy(alpha = 0.76f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Preset ${activePreset.name}${if (state.presetModified) " edited" else " saved"}  |  Headroom ${signedDb(headroomDb)}  |  Max boost ${signedDb(state.maxBoostDb)}",
            color = if (state.clippingRisk) AudioEqYellow else AudioDeckBlueLight,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        AudioDeckDspStatusRow(state)
    }
}

@Composable
private fun AudioDeckBypassControl(active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(if (active) AudioDeckBlue.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.060f))
            .border(1.dp, if (active) AudioDeckBlueLight.copy(alpha = 0.46f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(25.dp))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Switch
                contentDescription = "Bypass"
                stateDescription = if (active) "On" else "Off"
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Byp", color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(if (active) "On" else "Off", color = if (active) AudioDeckBlueLight else Muted, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun AudioDeckSquareControl(label: String, icon: DeckIcon, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) AudioDeckBlue.copy(alpha = 0.105f) else Color.White.copy(alpha = 0.055f))
            .border(1.dp, if (active) AudioDeckBlueLight.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = if (label == "Limiter") Role.Switch else Role.Button
                contentDescription = label
                if (label == "Limiter") stateDescription = if (active) "On" else "Off"
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (active) AudioDeckBlueLight else Color.White.copy(alpha = 0.76f), fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudioDeckDspStatusRow(state: AudioEngineState) {
    val nativeAvailable = NativeAudioEngineBridge.isAvailable
    val activation = nativeAudioActivationFor(state, nativeAvailable)
    val platformActive = platformSpectralEffectsActive(state, activation.media3DspActive)
    val effectsRequested = state.nativeOnlyProcessingActive
    val routeLabel = when {
        !state.processingActive -> "Saved"
        state.advancedTweaks.safeMode -> "Safe Mode"
        activation.media3DspActive -> "Native DSP"
        platformActive -> "Platform FX"
        else -> "Media3"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioDeckDspBadge(routeLabel, active = state.processingActive, warning = platformActive || !nativeAvailable && effectsRequested, modifier = Modifier.weight(1f))
        AudioDeckDspBadge(
            if (activation.media3DspActive) "Media3 PCM" else if (state.native.media3DspEnabled) "DSP Ready" else "PCM Path",
            active = activation.media3DspActive || state.native.media3DspEnabled,
            modifier = Modifier.weight(1f),
        )
        AudioDeckDspBadge(
            if (platformActive) "Device FX" else if (effectsRequested && !nativeAvailable) "No Native" else "Native OK",
            active = platformActive || activation.media3DspActive,
            warning = platformActive || effectsRequested && !nativeAvailable,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AudioDeckDspBadge(label: String, active: Boolean, modifier: Modifier = Modifier, warning: Boolean = false) {
    val color = when {
        warning -> AudioEqYellow
        active -> AudioDeckBlueLight
        else -> Muted
    }
    Box(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = if (active || warning) 0.13f else 0.055f))
            .border(1.dp, color.copy(alpha = if (active || warning) 0.42f else 0.11f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun audioDeckRouteLabel(state: AudioEngineState, nativeOnly: Boolean): String {
    if (!state.processingActive) return "Saved"
    if (state.advancedTweaks.safeMode) return "Safe"
    val activation = nativeAudioActivationFor(state, NativeAudioEngineBridge.isAvailable)
    return when {
        activation.media3DspActive -> "Native DSP"
        nativeOnly -> "Native DSP"
        platformSpectralEffectsActive(state, activation.media3DspActive) -> "Platform FX"
        else -> "Media3"
    }
}

private fun audioDeckCompactBandLabel(band: GraphicEqBand): String =
    if (band.frequencyHz >= 1000f) {
        "${(band.frequencyHz / 1000f).roundToInt()}k"
    } else {
        band.frequencyHz.roundToInt().toString()
    }

private fun audioDeckEffectiveDbLabel(requestedDb: Float, effectiveDb: Float): String =
    if (abs(requestedDb - effectiveDb) > AUDIO_EQ_INPUT_STEP_DB / 2f) {
        "eff ${signedDb(effectiveDb)}"
    } else {
        signedDb(effectiveDb)
    }

@Composable
private fun AudioDeckMiniSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val active = checked && enabled
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier
            .height(30.dp)
            .clip(shape)
            .background(
                if (active) {
                    Brush.verticalGradient(listOf(AudioDeckBlueLight.copy(alpha = 0.22f), AudioDeckBlue.copy(alpha = 0.11f)))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.052f),
                            Color.White.copy(alpha = 0.032f),
                        ),
                    )
                },
            )
            .border(1.dp, if (active) AudioDeckBlueLight.copy(alpha = 0.58f) else Color.White.copy(alpha = if (enabled) 0.18f else 0.11f), shape)
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) { onChange(!checked) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (active) AudioDeckBlueLight else if (enabled) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.56f), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AudioDeckPillButton(label: String, icon: DeckIcon, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val visualActive = active && enabled
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(
                if (visualActive) {
                    audioDeckActiveBrush()
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (enabled) 0.070f else 0.035f),
                            Color.White.copy(alpha = if (enabled) 0.040f else 0.022f),
                        ),
                    )
                },
            )
            .border(1.dp, if (visualActive) AudioDeckBlueLight.copy(alpha = 0.58f) else Color.White.copy(alpha = if (enabled) 0.10f else 0.055f), RoundedCornerShape(25.dp))
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (visualActive) AudioDeckBlueLight else Color.White.copy(alpha = if (enabled) 0.72f else 0.34f), fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudioDeckLiquidPillButton(label: String, icon: DeckIcon, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val visualActive = active && enabled
    val buttonShape = RoundedCornerShape(23.dp)
    val contentColor = when {
        visualActive -> AudioDeckBlueLight
        enabled -> Color.White.copy(alpha = 0.92f)
        else -> Color.White.copy(alpha = 0.58f)
    }
    Row(
        modifier
            .height(46.dp)
            .graphicsLayer {
                shadowElevation = 0f
                shape = buttonShape
                clip = true
            }
            .clip(buttonShape)
            .background(
                if (visualActive) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            AudioDeckBlueLight.copy(alpha = 0.20f),
                            AudioDeckBlue.copy(alpha = 0.13f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.052f),
                            Color.White.copy(alpha = 0.030f),
                        ),
                    )
                },
            )
            .border(1.dp, if (visualActive) AudioDeckBlueLight.copy(alpha = 0.62f) else Color.White.copy(alpha = if (enabled) 0.22f else 0.12f), buttonShape)
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = contentColor, fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudioDeckInlineSlider(
    title: String,
    valueText: String,
    left: String,
    right: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    liquid: Boolean = false,
    onChange: (Float) -> Unit,
) {
    val accent = AudioDeckBlueLight
    val accentBright = if (enabled) lerp(accent, Color.White, 0.16f) else Muted.copy(alpha = 0.62f)
    val sliderShape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 0f
                shape = sliderShape
                clip = true
            }
            .clip(sliderShape)
            .background(
                if (liquid) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.050f),
                            Color.White.copy(alpha = 0.030f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (enabled) 0.040f else 0.026f),
                            Color.White.copy(alpha = if (enabled) 0.040f else 0.026f),
                        ),
                    )
                },
            )
            .border(1.dp, if (liquid) Color.White.copy(alpha = if (enabled) 0.20f else 0.090f) else Color.White.copy(alpha = if (enabled) 0.055f else 0.035f), sliderShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 1f else 0.46f), fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                valueText,
                color = accentBright,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = if (liquid) 0.22f else 0.16f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accentBright,
                activeTrackColor = if (enabled) accent else Muted.copy(alpha = 0.36f),
                inactiveTrackColor = Color.White.copy(alpha = if (liquid) 0.22f else 0.14f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.height(30.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, color = Muted, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black)
            Text(right, color = Muted, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AudioDeckPresetChip(preset: AudioPreset, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .height(40.dp)
            .widthIn(min = 98.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AudioDeckBlue.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.060f))
            .border(1.dp, if (selected) AudioDeckBlueLight.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(preset.name, color = if (selected) AudioDeckBlueLight else Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun NeonRoundAction(icon: DeckIcon, contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.070f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .pressScaleEffect(interactionSource)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, Color.White, Modifier.size(18.dp))
    }
}

@Composable
private fun NeonRoundValueButton(icon: DeckIcon, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width = 72.dp, height = 58.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, AudioDeckBlueLight, Modifier.size(24.dp))
    }
}

private val AudioDeckBlue = Color(0xFF4A82FF)
private val AudioDeckBlueLight = Color(0xFFA9C8FF)
private val AudioPrecisionViolet = Color(0xFFD178FF)
private val AudioPrecisionVioletLight = Color(0xFFFFC8FF)
private val AudioNeonGreen = Color(0xFF18F46D)
private val AudioNeonMint = Color(0xFF72FFAC)
private val AudioEqAqua = Color(0xFF31EFD0)
private val AudioEqBlue = Color(0xFF49A6FF)
private val AudioEqYellow = Color(0xFFFFD85A)
private val AudioEqOrange = Color(0xFFFF9B45)
private val AudioEqCoral = Color(0xFFFF5F7A)

private fun audioDeckActiveBrush(): Brush =
    Brush.verticalGradient(
        listOf(
            AudioDeckBlueLight.copy(alpha = 0.20f),
            AudioDeckBlue.copy(alpha = 0.13f),
            Color.White.copy(alpha = 0.040f),
        ),
    )

private fun audioDeckProgress(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = (range.endInclusive - range.start).takeIf { it != 0f } ?: 1f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

private fun audioEqAccentForValue(value: Float, range: ClosedFloatingPointRange<Float>): Color {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    if (abs(clamped) < 0.05f) return AudioNeonGreen
    return if (clamped > 0f) {
        val p = (clamped / max(range.endInclusive, 0.01f)).coerceIn(0f, 1f)
        when {
            p < 0.45f -> lerp(AudioNeonGreen, AudioEqYellow, p / 0.45f)
            p < 0.78f -> lerp(AudioEqYellow, AudioEqOrange, (p - 0.45f) / 0.33f)
            else -> lerp(AudioEqOrange, AudioEqCoral, (p - 0.78f) / 0.22f)
        }
    } else {
        val p = (-clamped / max(abs(range.start), 0.01f)).coerceIn(0f, 1f)
        if (p < 0.62f) {
            lerp(AudioNeonGreen, AudioEqAqua, p / 0.62f)
        } else {
            lerp(AudioEqAqua, AudioEqBlue, (p - 0.62f) / 0.38f)
        }
    }
}

internal fun audioCircleAccentForValue(value: Float, range: ClosedFloatingPointRange<Float>): Color {
    if (range.start < 0f && range.endInclusive > 0f) {
        return audioEqAccentForValue(value, range)
    }
    val span = (range.endInclusive - range.start).takeIf { it != 0f } ?: 1f
    val p = ((value - range.start) / span).coerceIn(0f, 1f)
    return when {
        p < 0.28f -> lerp(AudioNeonGreen, AudioNeonMint, p / 0.28f)
        p < 0.52f -> lerp(AudioNeonMint, AudioEqYellow, (p - 0.28f) / 0.24f)
        p < 0.76f -> lerp(AudioEqYellow, AudioEqOrange, (p - 0.52f) / 0.24f)
        else -> lerp(AudioEqOrange, AudioEqCoral, (p - 0.76f) / 0.24f)
    }
}

private fun oneDecimal(value: Float): String =
    String.format(Locale.US, "%.1f", value)

private fun audioKnobDefaultResetValue(label: String, range: ClosedFloatingPointRange<Float>): Float {
    val default = when (label) {
        "Tempo" -> 1f
        "Damp" -> ReverbState().damp
        "Size" -> ReverbState().size
        "Fade" -> ReverbState().decay
        "Pre-Delay" -> ReverbState().preDelayMs
        "Threshold" -> CompressorState().thresholdDb
        "Ratio" -> CompressorState().ratio
        "Time" -> DelayState().timeMs
        "Feedback" -> DelayState().feedback
        "Rate" -> ModulationState().rateHz
        "Depth" -> ModulationState().depth
        else -> when {
            range.start <= 0f && range.endInclusive >= 0f -> 0f
            range.start <= 1f && range.endInclusive >= 1f -> 1f
            else -> range.start
        }
    }
    return default.coerceIn(range.start, range.endInclusive)
}

private fun audioKnobUnit(valueText: String): String =
    when {
        valueText.contains("dB") -> "dB"
        valueText.endsWith("%") -> "percent"
        valueText.endsWith("x") -> "tempo"
        valueText.endsWith("ms") -> "ms"
        valueText.endsWith("s") -> "sec"
        valueText.contains("st") -> "pitch"
        else -> "level"
    }

