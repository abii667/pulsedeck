package com.pulsedeck.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaFormat
import android.media.MediaExtractor
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.StrictMode
import android.os.SystemClock
import android.os.Trace
import android.provider.MediaStore
import android.util.Log
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pulsedeck.app.settings.data.DataStoreSettingsRepository
import com.pulsedeck.app.settings.model.AlbumArtworkProviderId
import com.pulsedeck.app.settings.model.AlbumArtworkProviderMode
import com.pulsedeck.app.settings.model.AlbumArtQuality
import com.pulsedeck.app.settings.model.AlbumArtSettings
import com.pulsedeck.app.settings.model.AnimationSpeedSetting
import com.pulsedeck.app.settings.model.LyricsSettings
import com.pulsedeck.app.settings.model.LocalArtistDiscoveryPolicy
import com.pulsedeck.app.settings.model.LookAndFeelSettings
import com.pulsedeck.app.settings.model.OnlineFeatureSettings
import com.pulsedeck.app.settings.model.PremiumDeckSmartRecommendationMode
import com.pulsedeck.app.settings.model.PlayerButtonAction
import com.pulsedeck.app.settings.model.PlayerButtonSize
import com.pulsedeck.app.settings.model.PulseSettingsState
import com.pulsedeck.app.settings.model.ScreenOrientationSetting
import com.pulsedeck.app.settings.runtime.DeviceCapabilities
import com.pulsedeck.app.settings.runtime.PulseOutputDiagnosticsStore
import com.pulsedeck.app.settings.runtime.PulsePlaybackRuntimeStore
import com.pulsedeck.app.settings.runtime.toPlaybackRuntimeSettings
import com.pulsedeck.app.settings.ui.PowerSettingsScreen
import com.pulsedeck.app.settings.ui.SettingsLaunchTarget
import com.pulsedeck.app.audio.NativeAudioEngineBridge
import com.pulsedeck.app.library.FolderSummary
import com.pulsedeck.app.library.AndroidLocalLibraryRepository
import com.pulsedeck.app.library.LocalLibraryGroup
import com.pulsedeck.app.library.LocalLibraryGroupKind
import com.pulsedeck.app.library.LocalLibraryRepository
import com.pulsedeck.app.library.LocalLibraryScanReason
import com.pulsedeck.app.library.LocalLibrarySearchIndex
import com.pulsedeck.app.library.LocalTrackFilterKind
import com.pulsedeck.app.library.PulseDeckLibraryDiagnostics
import com.pulsedeck.app.library.PulseDeckLibraryState
import com.pulsedeck.app.library.childFoldersFor
import com.pulsedeck.app.library.cleanupLocalTrackState
import com.pulsedeck.app.library.localFilteredTrackCount
import com.pulsedeck.app.library.localFilteredTracks
import com.pulsedeck.app.library.localLibraryGroupCount
import com.pulsedeck.app.library.localLibraryGroupTracks
import com.pulsedeck.app.library.localLibraryGroups
import com.pulsedeck.app.library.normalizedFolderPath
import com.pulsedeck.app.library.parentFolderPath
import com.pulsedeck.app.library.tracksUnderFolder
import com.pulsedeck.app.navigation.PulseDeckRouteState
import com.pulsedeck.app.navigation.Screen
import com.pulsedeck.app.navigation.isLibraryNavScreen
import com.pulsedeck.app.navigation.libraryCategoryNameForScreen
import com.pulsedeck.app.player.LastPlaybackState
import com.pulsedeck.app.player.LocalPlaybackSession
import com.pulsedeck.app.player.PlaybackQueueContext
import com.pulsedeck.app.player.PlaybackRepeatMode
import com.pulsedeck.app.player.PulseDeckPlaybackState
import com.pulsedeck.app.player.ShuffleMode
import com.pulsedeck.app.player.badge
import com.pulsedeck.app.player.loadLastPlaybackState
import com.pulsedeck.app.player.localPlaybackQueueTracks
import com.pulsedeck.app.player.localTrackAt
import com.pulsedeck.app.player.localTrackIndexFor
import com.pulsedeck.app.player.nextRepeatMode
import com.pulsedeck.app.player.saveLastPlaybackState
import com.pulsedeck.app.player.nextShuffleMode
import com.pulsedeck.app.player.resolveNextLocalTrack
import com.pulsedeck.app.player.resolvePreviousLocalTrack
import com.pulsedeck.app.player.resolveUpcomingLocalTracks
import com.pulsedeck.app.player.subtitle
import com.pulsedeck.app.player.title
import com.pulsedeck.app.premiumdeck.personalization.BehaviorEvent
import com.pulsedeck.app.premiumdeck.personalization.BehaviorEventType
import com.pulsedeck.app.premiumdeck.personalization.CandidateSource
import com.pulsedeck.app.premiumdeck.personalization.GeneratedMix
import com.pulsedeck.app.premiumdeck.personalization.LocalLibraryCandidateProvider
import com.pulsedeck.app.premiumdeck.personalization.ModelAssetManager
import com.pulsedeck.app.premiumdeck.personalization.PersonalizationSettings
import com.pulsedeck.app.premiumdeck.personalization.PremiumDeckCandidateProvider
import com.pulsedeck.app.premiumdeck.personalization.PremiumDeckPersonalizationRepository
import com.pulsedeck.app.premiumdeck.personalization.RoomUserPreferenceStore
import com.pulsedeck.app.premiumdeck.personalization.TinyRecLiteRtRunner
import com.pulsedeck.app.premiumdeck.personalization.TinyRecModelHealth
import com.pulsedeck.app.premiumdeck.personalization.TrackCandidate
import com.pulsedeck.app.premiumdeck.personalization.UserPreferenceProfile
import com.pulsedeck.app.premiumdeck.personalization.describeTinyRecModelStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal val Blue = Color(0xFF4A82FF)
internal val Green = Color(0xFF32E46A)
internal val Ink = Color(0xFF101115)
internal val Panel = Color(0xFF17181C)
internal val Muted = Color(0xFFA8A8AE)
internal val StreamBase = Color(0xFF050506)
internal val StreamPanel = Color(0xFF0B0A0D)
internal val StreamElevated = Color(0xFF121014)
internal val StreamTextPrimary = Color(0xFFF7F4EF)
internal val StreamTextSecondary = Color(0xFFB5B0AD)
internal val StreamTextMuted = Color(0xFF7D7774)
internal val StreamAccentRed = Color(0xFF4A82FF)
internal val StreamDeepRed = Color(0xFF1746B8)
internal val StreamAmber = Color(0xFFFFB45C)
internal val StreamBronze = Color(0xFF9B6136)
internal val StreamGlassFill = Color.White.copy(alpha = 0.055f)
internal val StreamGlassBorder = Color.White.copy(alpha = 0.10f)
internal val StreamGlassHighlight = Color.White.copy(alpha = 0.16f)
internal val StreamHorizontalPadding = 22.dp
internal val StreamCardRadius = 22.dp

private const val PLAYBACK_DIAGNOSTICS_LOG_TAG = "PulseDeckPlayback"
private const val PLAYBACK_TICKER_DISABLED_INTERVAL_MS = 0L
private const val PLAYBACK_DIAGNOSTICS_WINDOW_MS = 30_000L
private const val SPONSOR_SKIP_FINE_TICKER_LOOKAHEAD_MS = 1500L

internal enum class PlaybackTickerMode {
    Background,
    ForegroundHidden,
    MiniPlayer,
    FullPlayer,
    Waveform,
    Lyrics,
    SponsorSkip,
    Paused,
}

internal data class PlaybackTickerPolicy(
    val mode: PlaybackTickerMode,
    val intervalMillis: Long,
)

private fun playbackTickerPolicy(
    appInForeground: Boolean,
    playing: Boolean,
    playerOpen: Boolean,
    miniPlayerVisible: Boolean,
    waveformAnimating: Boolean,
    lyricsVisible: Boolean,
    sponsorSkipActive: Boolean,
): PlaybackTickerPolicy =
    when {
        !playing -> PlaybackTickerPolicy(PlaybackTickerMode.Paused, 1000L)
        !appInForeground -> PlaybackTickerPolicy(PlaybackTickerMode.Background, PLAYBACK_TICKER_DISABLED_INTERVAL_MS)
        lyricsVisible -> PlaybackTickerPolicy(PlaybackTickerMode.Lyrics, 250L)
        sponsorSkipActive -> PlaybackTickerPolicy(PlaybackTickerMode.SponsorSkip, 250L)
        waveformAnimating -> PlaybackTickerPolicy(PlaybackTickerMode.Waveform, 500L)
        playerOpen -> PlaybackTickerPolicy(PlaybackTickerMode.FullPlayer, 500L)
        miniPlayerVisible -> PlaybackTickerPolicy(PlaybackTickerMode.MiniPlayer, 1000L)
        else -> PlaybackTickerPolicy(PlaybackTickerMode.ForegroundHidden, 1000L)
    }

internal object PulseDeckPlaybackDiagnostics {
    private var enabled = false
    private var lastTickerPolicy: PlaybackTickerPolicy? = null
    private var lastWaveformVisible: Boolean? = null
    private var lastWaveformAnimating: Boolean? = null
    private var lastSurface: String? = null
    private var lastForeground: Boolean? = null
    private var emissionWindowStartedAtMs = 0L
    private var emissionCount = 0
    private var persistenceWindowStartedAtMs = 0L
    private var persistenceCount = 0
    private var waveformDrawWindowStartedAtMs = 0L
    private var waveformDrawCount = 0
    private var recompositionWindowStartedAtMs = 0L
    private val recompositionCounts = linkedMapOf<String, Int>()
    private var sponsorCheckWindowStartedAtMs = 0L
    private var sponsorCheckCount = 0
    private var sponsorFineCheckCount = 0
    private var sponsorHitCount = 0
    private var renderWindowStartedAtMs = 0L
    private val renderStats = linkedMapOf<String, RenderStats>()

    fun configure(context: Context) {
        enabled = context.isPlaybackTickerDebuggable()
    }

    fun reportTicker(
        policy: PlaybackTickerPolicy,
        waveformVisible: Boolean,
        waveformAnimating: Boolean,
        surface: String,
        appInForeground: Boolean,
    ) {
        if (!enabled) return
        if (
            lastTickerPolicy == policy &&
            lastWaveformVisible == waveformVisible &&
            lastWaveformAnimating == waveformAnimating &&
            lastSurface == surface &&
            lastForeground == appInForeground
        ) return
        lastTickerPolicy = policy
        lastWaveformVisible = waveformVisible
        lastWaveformAnimating = waveformAnimating
        lastSurface = surface
        lastForeground = appInForeground
        Log.d(
            PLAYBACK_DIAGNOSTICS_LOG_TAG,
            "mode=${policy.mode} interval_ms=${policy.intervalMillis} surface=$surface foreground=$appInForeground waveform_visible=$waveformVisible waveform_animating=$waveformAnimating",
        )
    }

    fun recordPositionEmission(policy: PlaybackTickerPolicy, localOnly: Boolean = false) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (emissionWindowStartedAtMs == 0L) emissionWindowStartedAtMs = now
        emissionCount += 1
        val elapsed = now - emissionWindowStartedAtMs
        if (elapsed >= PLAYBACK_DIAGNOSTICS_WINDOW_MS) {
            Log.d(
                PLAYBACK_DIAGNOSTICS_LOG_TAG,
                "position_emissions_per_min=${ratePerMinute(emissionCount, elapsed)} mode=${policy.mode} interval_ms=${policy.intervalMillis} delivery=${if (localOnly) "local" else "root"}",
            )
            emissionWindowStartedAtMs = now
            emissionCount = 0
        }
    }

    fun recordPersistenceWrite(reason: String, kind: String) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (persistenceWindowStartedAtMs == 0L) persistenceWindowStartedAtMs = now
        persistenceCount += 1
        val elapsed = now - persistenceWindowStartedAtMs
        if (elapsed >= PLAYBACK_DIAGNOSTICS_WINDOW_MS) {
            Log.d(
                PLAYBACK_DIAGNOSTICS_LOG_TAG,
                "persistence_writes_per_min=${ratePerMinute(persistenceCount, elapsed)} last_reason=$reason last_kind=$kind",
            )
            persistenceWindowStartedAtMs = now
            persistenceCount = 0
        }
    }

    fun recordWaveformDraw(playing: Boolean, animating: Boolean) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (waveformDrawWindowStartedAtMs == 0L) waveformDrawWindowStartedAtMs = now
        waveformDrawCount += 1
        val elapsed = now - waveformDrawWindowStartedAtMs
        if (elapsed >= PLAYBACK_DIAGNOSTICS_WINDOW_MS) {
            Log.d(
                PLAYBACK_DIAGNOSTICS_LOG_TAG,
                "waveform_draws_per_min=${ratePerMinute(waveformDrawCount, elapsed)} playing=$playing animating=$animating",
            )
            waveformDrawWindowStartedAtMs = now
            waveformDrawCount = 0
        }
    }

    fun recordRecomposition(surface: String) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (recompositionWindowStartedAtMs == 0L) recompositionWindowStartedAtMs = now
        recompositionCounts[surface] = (recompositionCounts[surface] ?: 0) + 1
        val elapsed = now - recompositionWindowStartedAtMs
        if (elapsed >= PLAYBACK_DIAGNOSTICS_WINDOW_MS) {
            val counts = recompositionCounts.entries.joinToString(separator = ",") { (name, count) ->
                "$name:${ratePerMinute(count, elapsed)}"
            }
            Log.d(PLAYBACK_DIAGNOSTICS_LOG_TAG, "recompositions_per_min=$counts")
            recompositionWindowStartedAtMs = now
            recompositionCounts.clear()
        }
    }

    fun recordSponsorSkipCheck(fineTicker: Boolean, segmentHit: Boolean) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (sponsorCheckWindowStartedAtMs == 0L) sponsorCheckWindowStartedAtMs = now
        sponsorCheckCount += 1
        if (fineTicker) sponsorFineCheckCount += 1
        if (segmentHit) sponsorHitCount += 1
        val elapsed = now - sponsorCheckWindowStartedAtMs
        if (elapsed >= PLAYBACK_DIAGNOSTICS_WINDOW_MS) {
            Log.d(
                PLAYBACK_DIAGNOSTICS_LOG_TAG,
                "sponsor_checks_per_min=${ratePerMinute(sponsorCheckCount, elapsed)} fine=${ratePerMinute(sponsorFineCheckCount, elapsed)} hits=${ratePerMinute(sponsorHitCount, elapsed)}",
            )
            sponsorCheckWindowStartedAtMs = now
            sponsorCheckCount = 0
            sponsorFineCheckCount = 0
            sponsorHitCount = 0
        }
    }

    fun recordSeekGesture(
        durationMillis: Long,
        updateCount: Int,
        updateTotalNanos: Long,
        updateMaxNanos: Long,
        commitNanos: Long,
        committed: Boolean,
    ) {
        if (!enabled) return
        val avgUpdateMicros = if (updateCount <= 0) 0L else updateTotalNanos / updateCount / 1_000L
        Log.d(
            PLAYBACK_DIAGNOSTICS_LOG_TAG,
            "seek_drag duration_ms=$durationMillis updates=$updateCount update_avg_us=$avgUpdateMicros update_max_us=${updateMaxNanos / 1_000L} commit_us=${commitNanos / 1_000L} committed=$committed",
        )
    }

    fun recordLyricsLoad(durationMillis: Long, state: LyricsUiState) {
        if (!enabled) return
        val result = when (state) {
            LyricsUiState.Loading -> "loading"
            LyricsUiState.Missing -> "missing"
            LyricsUiState.BlockedOffline -> "blocked_offline"
            LyricsUiState.BlockedDataSaver -> "blocked_data_saver"
            is LyricsUiState.Error -> "error"
            is LyricsUiState.Synced -> "synced lines=${state.lines.size}"
            is LyricsUiState.Plain -> "plain lines=${state.text.lineSequence().count()}"
        }
        Log.d(PLAYBACK_DIAGNOSTICS_LOG_TAG, "lyrics_load duration_ms=$durationMillis result=$result")
    }

    fun recordPlayerTransition(kind: String, durationMillis: Long) {
        if (!enabled) return
        Log.d(PLAYBACK_DIAGNOSTICS_LOG_TAG, "player_transition kind=$kind duration_ms=$durationMillis")
    }

    fun beginRenderSection(section: String): Long {
        if (!enabled) return 0L
        Trace.beginSection("PulseDeck:$section")
        return System.nanoTime()
    }

    fun endRenderSection(section: String, startedAtNanos: Long) {
        if (startedAtNanos == 0L) return
        val durationNanos = System.nanoTime() - startedAtNanos
        Trace.endSection()
        recordRenderDuration(section, durationNanos)
    }

    private fun recordRenderDuration(section: String, durationNanos: Long) {
        val now = SystemClock.uptimeMillis()
        if (renderWindowStartedAtMs == 0L) renderWindowStartedAtMs = now
        val stats = renderStats.getOrPut(section) { RenderStats() }
        stats.count += 1
        stats.totalNanos += durationNanos
        if (durationNanos > stats.maxNanos) stats.maxNanos = durationNanos
        val elapsed = now - renderWindowStartedAtMs
        if (elapsed >= PLAYBACK_DIAGNOSTICS_WINDOW_MS) {
            val summary = renderStats.entries.joinToString(separator = ";") { (name, value) ->
                val avgMicros = if (value.count == 0) 0L else value.totalNanos / value.count / 1_000L
                val maxMicros = value.maxNanos / 1_000L
                "$name:count_per_min=${ratePerMinute(value.count, elapsed)},avg_us=$avgMicros,max_us=$maxMicros"
            }
            Log.d(PLAYBACK_DIAGNOSTICS_LOG_TAG, "render_sections=$summary")
            renderWindowStartedAtMs = now
            renderStats.clear()
        }
    }

    private fun ratePerMinute(count: Int, elapsedMillis: Long): Int =
        if (elapsedMillis <= 0L) count else ((count * 60_000L) / elapsedMillis).toInt()

    private class RenderStats {
        var count = 0
        var totalNanos = 0L
        var maxNanos = 0L
    }
}

private fun Context.isPlaybackTickerDebuggable(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

internal object PulseOnlineRuntime {
    var settings by mutableStateOf(OnlineFeatureSettings())

    fun update(next: OnlineFeatureSettings) {
        settings = next
    }
}

internal object LyricsRuntime {
    var settings by mutableStateOf(LyricsSettings())
    var cacheRevision by mutableIntStateOf(0)

    fun update(next: LyricsSettings) {
        settings = next.normalized()
    }

    fun invalidate() {
        cacheRevision += 1
    }
}
internal const val PREFS_LIBRARY_STATE = "pulse_library_state"
private const val PREF_LIKED_TRACKS = "liked_tracks"
private const val PREF_DISLIKED_TRACKS = "disliked_tracks"
private const val PREF_BOOKMARKED_TRACKS = "bookmarked_tracks"
private const val PREF_PLAYLIST_TRACKS = "playlist_tracks"
internal const val PREF_YOUTUBE_SOURCES = "youtube_sources"
internal const val PREF_YOUTUBE_PLAYLISTS = "youtube_playlists"
internal const val PREF_YOUTUBE_SEARCH_CACHE = "youtube_search_cache"
internal const val PREF_STREAM_PLAY_HISTORY = "stream_play_history"
internal const val PREF_STREAM_DISCOVERY_CACHE = "stream_discovery_cache"
internal const val PREF_FOLLOWED_STREAM_ARTISTS = "followed_stream_artists"
internal const val PREF_STREAM_NEW_RELEASES_CACHE = "stream_new_releases_cache"
internal const val PREF_STREAM_RELEASE_NOTIFICATION_READ_IDS = "stream_release_notification_read_ids"
private const val PREF_PREMIUM_CHART_LASTFM_API_KEY = "premium_chart_lastfm_api_key"
private const val PREF_RADIO_FAVORITE_COUNTRIES = "radio_favorite_countries"
private const val PREF_RADIO_FAVORITE_STATIONS = "radio_favorite_stations"
private const val RADIO_DEFAULT_STATION_LIMIT = 30
private const val RADIO_ETHIOPIA_STATION_LIMIT = 100
internal const val PREF_ALBUM_DOWNLOAD_DRAFTS = "album_download_drafts"
internal const val PREF_ALBUM_AUDIO_DOWNLOAD_JOBS = "album_audio_download_jobs"
internal const val STREAM_LIBRARY_ALBUM = "Stream Library"
internal const val STREAM_LIBRARY_ALBUM_ARTIST = "PulseDeck"
internal const val STREAM_LIBRARY_FOLDER = "PulseDeck/Stream Library"
private const val STREAM_LIBRARY_FOLDER_WITH_MUSIC = "Music/PulseDeck/Stream Library"
internal const val PREMIUMDECK_SOURCE_NAME = "PremiumDeck"
private const val PREMIUMDECK_SOURCE_MARK = "PD"
internal const val PREMIUMDECK_STREAM_TITLE = "PremiumDeck stream"
internal const val PREMIUMDECK_SOURCE_CATEGORY = "PremiumDeck"
private const val PREMIUMDECK_SOURCE_SHELF = "Virtual PremiumDeck Sources"
internal const val LEGACY_YOUTUBE_SOURCE_NAME = "YouTube"
private const val LEGACY_YOUTUBE_SOURCE_CATEGORY = "YouTube Sources"
internal const val YOUTUBE_STREAM_CACHE_TTL_MILLIS = 60L * 60L * 1000L
internal const val YOUTUBE_STREAM_EXPIRY_BUFFER_MILLIS = 5L * 60L * 1000L
internal const val STREAM_DISCOVERY_TTL_MILLIS = 24L * 60L * 60L * 1000L
internal const val STREAM_NEW_RELEASE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
internal const val STREAM_NEW_RELEASE_MAX_AGE_MILLIS = 31L * 24L * 60L * 60L * 1000L
internal const val STREAM_MIX_TARGET_SIZE = 25
internal const val STREAM_ALBUM_COLLECTION_TRACK_LIMIT = 80
internal const val STREAM_MIX_DISCOVERY_LIMIT = 8
internal const val STREAM_MIX_CONTEXT_LIMIT = 30
internal const val YOUTUBE_LOG_TAG = "PulseDeckYouTube"
private fun newStreamMixSessionSeed(): Long = System.currentTimeMillis() xor System.nanoTime()

private fun loadPremiumDeckChartLastFmApiKey(context: Context): String =
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_PREMIUM_CHART_LASTFM_API_KEY, "")
        .orEmpty()
        .trim()

private fun savePremiumDeckChartLastFmApiKey(context: Context, apiKey: String) {
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_PREMIUM_CHART_LASTFM_API_KEY, apiKey.trim())
        .apply()
}

private data class StartupPersistenceSnapshot(
    val likedTrackKeys: Set<String> = emptySet(),
    val dislikedTrackKeys: Set<String> = emptySet(),
    val bookmarkedTrackKeys: Set<String> = emptySet(),
    val playlistTrackKeys: Set<String> = emptySet(),
    val trackPlayCounts: Map<String, Int> = emptyMap(),
    val favoriteRadioCountryCodes: Set<String> = emptySet(),
    val favoriteRadioStationKeys: Set<String> = emptySet(),
    val recentRadioStationKeys: List<String> = emptyList(),
    val lastPlaybackState: LastPlaybackState? = null,
    val youtubeSources: List<YouTubeSource> = emptyList(),
    val youtubePlaylists: List<YouTubePlaylist> = emptyList(),
    val streamPlayHistory: List<StreamPlayHistoryItem> = emptyList(),
    val streamDiscoverySnapshot: StreamDiscoverySnapshot = StreamDiscoverySnapshot(),
    val premiumDeckPodcastSnapshot: PremiumDeckPodcastSnapshot = PremiumDeckPodcastSnapshot(),
    val followedStreamArtists: List<FollowedStreamArtist> = emptyList(),
    val streamNewReleaseSnapshot: StreamNewReleaseSnapshot = StreamNewReleaseSnapshot(),
    val recommendedAlbumsSnapshot: RecommendedAlbumsSnapshot? = null,
    val streamReleaseNotificationReadIds: Set<String> = emptySet(),
    val premiumChartLastFmApiKey: String = "",
    val albumDownloadDrafts: List<AlbumDownloadDraft> = emptyList(),
    val albumAudioDownloadJobs: List<AlbumAudioDownloadJob> = emptyList(),
    val audioEngineState: AudioEngineState = AudioEngineState(),
    val backgroundSettings: BackgroundSettings = BackgroundSettings(),
)

private data class LibrarySummarySnapshot(
    val albums: List<Album> = emptyList(),
    val albumByKey: Map<String, Album> = emptyMap(),
    val groupCounts: Map<LocalLibraryGroupKind, Int> = emptyMap(),
    val artistCount: Int = 0,
    val localPlaylistTrackCount: Int = 0,
    val bookmarkedTrackCount: Int = 0,
    val mostPlayedTrackCount: Int = 0,
)

private suspend fun loadStartupPersistenceSnapshot(
    context: Context,
    localLibraryRepository: LocalLibraryRepository,
): StartupPersistenceSnapshot = withContext(Dispatchers.IO) {
    StartupPersistenceSnapshot(
        likedTrackKeys = localLibraryRepository.loadStateSet(PREF_LIKED_TRACKS),
        dislikedTrackKeys = localLibraryRepository.loadStateSet(PREF_DISLIKED_TRACKS),
        bookmarkedTrackKeys = localLibraryRepository.loadStateSet(PREF_BOOKMARKED_TRACKS),
        playlistTrackKeys = localLibraryRepository.loadStateSet(PREF_PLAYLIST_TRACKS),
        trackPlayCounts = localLibraryRepository.loadPlayCounts(),
        favoriteRadioCountryCodes = localLibraryRepository.loadStateSet(PREF_RADIO_FAVORITE_COUNTRIES),
        favoriteRadioStationKeys = localLibraryRepository.loadStateSet(PREF_RADIO_FAVORITE_STATIONS),
        recentRadioStationKeys = loadRecentRadioStationKeys(context),
        lastPlaybackState = loadLastPlaybackState(context),
        youtubeSources = loadYouTubeSources(context),
        youtubePlaylists = loadYouTubePlaylists(context),
        streamPlayHistory = loadStreamPlayHistory(context),
        streamDiscoverySnapshot = loadStreamDiscoverySnapshot(context),
        premiumDeckPodcastSnapshot = loadPremiumDeckPodcastSnapshot(context),
        followedStreamArtists = loadFollowedStreamArtists(context),
        streamNewReleaseSnapshot = loadStreamNewReleaseSnapshot(context),
        recommendedAlbumsSnapshot = loadRecommendedAlbumsSnapshot(context),
        streamReleaseNotificationReadIds = loadStreamReleaseNotificationReadIds(context),
        premiumChartLastFmApiKey = loadPremiumDeckChartLastFmApiKey(context),
        albumDownloadDrafts = loadAlbumDownloadDrafts(context),
        albumAudioDownloadJobs = loadAlbumAudioDownloadJobs(context),
        audioEngineState = loadAudioEngineState(context),
        backgroundSettings = loadBackgroundSettings(context),
    )
}

private fun isStreamLibraryFolder(folderPath: String?): Boolean {
    val normalized = folderPath?.replace('\\', '/')?.trim('/') ?: return false
    return normalized == STREAM_LIBRARY_FOLDER ||
        normalized == STREAM_LIBRARY_FOLDER_WITH_MUSIC ||
        normalized == PREMIUMDECK_SOURCE_CATEGORY ||
        normalized == "$PREMIUMDECK_SOURCE_CATEGORY Sources" ||
        normalized == "Music/PulseDeck/$PREMIUMDECK_SOURCE_CATEGORY" ||
        normalized.startsWith("Music/PulseDeck/$PREMIUMDECK_SOURCE_CATEGORY/") ||
        normalized == LEGACY_YOUTUBE_SOURCE_CATEGORY ||
        normalized == "Music/PulseDeck/YouTube" ||
        normalized.startsWith("Music/PulseDeck/YouTube/")
}

internal val youtubeAccentChoices = listOf(
    0xFF4A82FF.toInt(),
    0xFFFF8B3D.toInt(),
    0xFFFFD166.toInt(),
    0xFF32E46A.toInt(),
    0xFF4A82FF.toInt(),
    0xFFB86CFF.toInt(),
)


internal data class InfoDialogState(
    val title: String,
    val subtitle: String,
    val rows: List<Pair<String, String>>,
)


internal data class LyricsLine(
    val startMillis: Long,
    val text: String,
)

internal typealias SyncedLyricLine = LyricsLine

internal data class LyricsResult(
    val trackKey: String,
    val source: String,
    val plainText: String,
    val syncedLines: List<LyricsLine> = emptyList(),
    val fetchedAtMillis: Long = System.currentTimeMillis(),
)

internal sealed class LyricsUiState {
    data object Loading : LyricsUiState()
    data class Synced(val lines: List<SyncedLyricLine>) : LyricsUiState()
    data class Plain(val text: String) : LyricsUiState()
    data object Missing : LyricsUiState()
    data object BlockedOffline : LyricsUiState()
    data object BlockedDataSaver : LyricsUiState()
    data class Error(val message: String) : LyricsUiState()
}

data class ReadabilityProtectionSettings(
    val enabled: Boolean = true,
    val autoDimLists: Boolean = true,
    val autoDimLyrics: Boolean = true,
    val minimumTextContrast: String = "AA",
)

data class BackgroundSettings(
    val blurred: Boolean = true,
    val listBackground: Boolean = true,
    val lyricsBackground: Boolean = true,
    val gradient: Float = 4f,
    val gradientColor: String = "#000000",
    val gradientForLists: Boolean = true,
    val blur: Float = 5f,
    val details: Float = 5f,
    val intensity: Float = 1f,
    val saturation: Float = 1f,
    val readabilityProtection: ReadabilityProtectionSettings = ReadabilityProtectionSettings(),
) {
    fun normalized(): BackgroundSettings =
        copy(
            listBackground = listBackground && blurred,
            lyricsBackground = lyricsBackground && blurred,
            gradient = gradient.coerceIn(0f, 10f),
            gradientColor = normalizedHexColor(gradientColor),
            gradientForLists = blurred && listBackground && gradientForLists,
            blur = blur.coerceIn(0f, 10f),
            details = details.coerceIn(0f, 10f),
            intensity = intensity.coerceIn(0f, 1f),
            saturation = saturation.coerceIn(0f, 2f),
        )
}

internal data class TrackActionItem(
    val title: String,
    val subtitle: String,
    val icon: DeckIcon,
    val active: Boolean = false,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

internal data class ArtistSummary(
    val name: String,
    val tracks: List<Track>,
    val durationMillis: Long,
) {
    val songCount: Int = tracks.size
}

internal enum class LibraryCategoryKind {
    AllSongs,
    PremiumDeck,
    PulseRadio,
    Folders,
    FolderHierarchy,
    Albums,
    Artists,
    AlbumArtists,
    Genres,
    Years,
    Composers,
    Playlists,
    Bookmarks,
    MostPlayed,
}


internal object PulseMotion {
    object Duration {
        const val Short = 150
        const val Tap = 110
        const val PopupIn = 190
        const val PopupOut = 130
        const val Medium = 240
        const val Long = 280
        const val Emphasized = 320
        const val Panel = 320
    }

    object Easing {
        val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    }

    object Spring {
        const val RouteStiffness = 520f
        const val HeroReturnStiffness = 520f
        const val GestureStiffness = 560f
        const val PlayerGestureStiffness = 520f
        const val RouteDamping = 0.92f
        const val GestureDamping = 0.86f
        const val PlayerDamping = 0.78f
    }

    object Distance {
        const val PanelLift = 10f
    }

    object Alpha {
        const val ModalScrim = 0.62f
    }

    object Scale {
        const val ModalStart = 0.96f
    }
}

internal data class PulseMotionSpec(
    val durationScale: Float = 1f,
    val disabled: Boolean = false,
) {
    fun duration(baseMillis: Int): Int =
        if (disabled) 0 else (baseMillis * durationScale).roundToInt().coerceAtLeast(1)

    fun delay(baseMillis: Int): Long =
        if (disabled) 0L else (baseMillis * durationScale).roundToInt().coerceAtLeast(0).toLong()

    companion object {
        val Default = PulseMotionSpec()

        fun from(setting: AnimationSpeedSetting): PulseMotionSpec =
            when (setting) {
                AnimationSpeedSetting.Disabled -> PulseMotionSpec(durationScale = 0f, disabled = true)
                AnimationSpeedSetting.Fast -> PulseMotionSpec(durationScale = 0.70f)
                AnimationSpeedSetting.Default -> Default
            }
    }
}

internal val LocalPulseMotionSpec = staticCompositionLocalOf { PulseMotionSpec.Default }

internal data class AlbumTileBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class PlayerLaunchHandoff(
    val trackKey: String,
    val artBounds: AlbumTileBounds,
    val titleBounds: AlbumTileBounds? = null,
    val artistBounds: AlbumTileBounds? = null,
)

internal enum class CategoryMotionDirection { Opening, Closing }

private const val LibraryCategorySharedHandoffEnabled = false

private data class CategoryMotionRequest(
    val categoryName: String,
    val fromBounds: AlbumTileBounds,
    val direction: CategoryMotionDirection,
    val nonce: Int,
    val startProgress: Float = 0f,
)

internal data class CategorySharedMotion(
    val category: Category,
    val fromBounds: AlbumTileBounds,
    val toBounds: AlbumTileBounds,
    val direction: CategoryMotionDirection,
    val nonce: Int,
    val startProgress: Float = 0f,
    val progressOverride: Float? = null,
)

@Composable
internal fun Modifier.pressScaleEffect(interactionSource: MutableInteractionSource): Modifier {
    val motion = LocalPulseMotionSpec.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!motion.disabled && isPressed) 0.975f else 1f,
        animationSpec = if (motion.disabled) {
            tween(0)
        } else {
            tween(durationMillis = motion.duration(PulseMotion.Duration.Tap), easing = PulseMotion.Easing.Standard)
        },
        label = "pressScale"
    )
    return if (scale >= 0.999f) {
        this
    } else {
        this.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
}

@Composable
internal fun AnimatedEntrance(index: Int, animate: Boolean = true, content: @Composable () -> Unit) {
    val motion = LocalPulseMotionSpec.current
    val animationEnabled = animate && !motion.disabled
    var entered by remember(index, animationEnabled) { mutableStateOf(!animationEnabled) }
    LaunchedEffect(index, animationEnabled) {
        if (!animationEnabled) {
            entered = true
        } else {
            delay(motion.delay((index * 22).coerceAtMost(180)))
            entered = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (animationEnabled) {
            tween(
                durationMillis = motion.duration(PulseMotion.Duration.Medium),
                easing = PulseMotion.Easing.Standard,
            )
        } else {
            tween(0)
        },
        label = "animatedEntrance",
    )
    val entranceModifier = if (progress >= 0.999f) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            alpha = progress
            translationY = PulseMotion.Distance.PanelLift * 1.8f * (1f - progress)
        }
    }
    Box(entranceModifier) {
        content()
    }
}

@Composable
fun PulseDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            background = Color.Black,
            surface = Panel,
            onSurface = Color.White,
        ),
        content = content,
    )
}

@Composable
private fun ApplyLookAndFeelRuntime(lookAndFeel: LookAndFeelSettings) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val normalized = lookAndFeel.normalized()
    val originalOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    DisposableEffect(
        activity,
        view,
        normalized.orientation,
        normalized.hideStatusBar,
        normalized.keepScreenOn,
    ) {
        val window = activity?.window
        if (activity != null && window != null) {
            activity.requestedOrientation = normalized.orientation.toActivityOrientation()
            val controller = WindowInsetsControllerCompat(window, view)
            if (normalized.hideStatusBar) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
            if (normalized.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            val restoreWindow = activity?.window
            if (activity != null && restoreWindow != null) {
                activity.requestedOrientation = originalOrientation
                WindowInsetsControllerCompat(restoreWindow, view).show(WindowInsetsCompat.Type.statusBars())
                restoreWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

private fun ScreenOrientationSetting.toActivityOrientation(): Int =
    when (this) {
        ScreenOrientationSetting.Default -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ScreenOrientationSetting.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ScreenOrientationSetting.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

private fun showToastAllowingPlatformDiskChecks(context: Context, message: String) {
    val oldPolicy = StrictMode.allowThreadDiskReads()
    try {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    } finally {
        StrictMode.setThreadPolicy(oldPolicy)
    }
}

@Composable
fun PulseDeckApp(onFirstUsefulUi: () -> Unit = {}) {
    SideEffect {
        PulseDeckPlaybackDiagnostics.recordRecomposition("root")
    }
    val context = LocalContext.current
    val appView = LocalView.current
    val lifecycleOwner = context as? LifecycleOwner
    val appScope = rememberCoroutineScope()
    val currentOnFirstUsefulUi by rememberUpdatedState(onFirstUsefulUi)
    var appInForeground by remember(appView) { mutableStateOf(appView.hasWindowFocus()) }
    DisposableEffect(context) {
        PulseDeckPlaybackDiagnostics.configure(context)
        PulseDeckLibraryDiagnostics.configure(context)
        onDispose { }
    }
    DisposableEffect(appView) {
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            appInForeground = hasFocus
        }
        appInForeground = appView.hasWindowFocus()
        appView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            if (appView.viewTreeObserver.isAlive) {
                appView.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                appInForeground = when (event) {
                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME -> true
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_DESTROY -> false
                    else -> lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                }
            }
            appInForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && appView.hasWindowFocus()
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    val audioPermission = remember { requiredAudioPermission() }
    var permissionGranted by remember { mutableStateOf(hasAudioPermission(context)) }
    val localLibraryRepository = remember(context) { AndroidLocalLibraryRepository(context) }
    var startupPersistenceLoaded by remember { mutableStateOf(false) }
    val libraryState = remember(localLibraryRepository) {
        PulseDeckLibraryState(
            localLibraryLoaded = !permissionGranted,
        )
    }
    var localLibraryLoaded by libraryState::localLibraryLoaded
    var scannedTracks by libraryState::scannedTracks
    val localLibraryScanJob = remember { AtomicReference<Job?>(null) }
    var localLibraryScanGeneration by remember { mutableLongStateOf(0L) }
    var localSearchIndex by remember { mutableStateOf(LocalLibrarySearchIndex.Empty) }
    var localSearchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var localSearchGeneration by remember { mutableLongStateOf(0L) }
    var startupPlaybackState by remember { mutableStateOf<LastPlaybackState?>(null) }
    var lastPlaybackRestored by remember { mutableStateOf(false) }
    val routeState = rememberSaveable(saver = PulseDeckRouteState.Saver) { PulseDeckRouteState() }
    var screen by routeState::screen
    var settingsLaunchTarget by routeState::settingsLaunchTarget
    var settingsLaunchRequestKey by routeState::settingsLaunchRequestKey
    var settingsBackRequestKey by remember { mutableIntStateOf(0) }
    var selectedAlbumKey by routeState::selectedAlbumKey
    var activeAlbumKey by routeState::activeAlbumKey
    var playbackQueueContext by remember { mutableStateOf<PlaybackQueueContext>(PlaybackQueueContext.AllSongs) }
    var selectedFolderPath by routeState::selectedFolderPath
    var selectedLibraryGroupKind by routeState::selectedLibraryGroupKind
    var selectedLibraryGroupKey by routeState::selectedLibraryGroupKey
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var youtubeSearchQuery by rememberSaveable { mutableStateOf("") }
    var youtubeSearchResults by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
    var youtubeSearchSuggestions by remember { mutableStateOf<List<YouTubeSearchSuggestion>>(emptyList()) }
    var youtubeSearchLoading by remember { mutableStateOf(false) }
    var youtubeSearchError by remember { mutableStateOf<String?>(null) }
    var artistContinuationFetchStates by remember { mutableStateOf<Map<String, ArtistContinuationFetchState>>(emptyMap()) }
    val artistDiscographyProvider = remember { YouTubeArtistDiscographyProvider() }
    var searchDiscoveryGenreCollections by remember { mutableStateOf<List<StreamCollection>>(emptyList()) }
    var searchDiscoveryPreviewCollection by remember { mutableStateOf<StreamCollection?>(null) }
    var searchDiscoveryLoadingGenreId by remember { mutableStateOf<String?>(null) }
    var searchDiscoveryGeneration by remember { mutableLongStateOf(0L) }
    var searchDiscoveryPreviewStartPositions by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var premiumChartProvider by rememberSaveable { mutableStateOf(PremiumDeckChartProvider.Auto) }
    var premiumChartGenre by rememberSaveable { mutableStateOf(PremiumDeckChartGenre.Global) }
    var premiumChartRequestedLimit by rememberSaveable { mutableIntStateOf(PREMIUM_DECK_CHART_SAFE_LIMIT) }
    var premiumChartEntries by remember { mutableStateOf<List<PremiumDeckChartEntry>>(emptyList()) }
    var premiumChartMatches by remember { mutableStateOf<List<PremiumDeckChartMatch>>(emptyList()) }
    var premiumChartMatchedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var premiumChartLastFmApiKey by remember { mutableStateOf("") }
    var premiumChartLoading by remember { mutableStateOf(false) }
    var premiumChartMatching by remember { mutableStateOf(false) }
    var premiumChartError by remember { mutableStateOf<String?>(null) }
    var premiumChartGeneration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(premiumChartLastFmApiKey) {
        PremiumDeckChartRuntime.lastFmApiKey = premiumChartLastFmApiKey.trim()
    }
    val radioClient = remember { RadioBrowserClient() }
    var radioCountries by remember { mutableStateOf<List<RadioCountry>>(emptyList()) }
    var radioStations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var radioCountryCode by rememberSaveable { mutableStateOf(Locale.getDefault().country.takeIf { it.length == 2 } ?: "US") }
    var radioNameQuery by rememberSaveable { mutableStateOf("") }
    var radioStationFilter by rememberSaveable { mutableStateOf(RadioStationFilter.Popular) }
    var radioCountriesLoading by remember { mutableStateOf(false) }
    var radioStationsLoading by remember { mutableStateOf(false) }
    var radioError by remember { mutableStateOf<String?>(null) }
    var activeRadioStation by remember { mutableStateOf<RadioStation?>(null) }
    var favoriteRadioCountryCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteRadioStationKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recentRadioStationKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    val playbackState = rememberSaveable(saver = PulseDeckPlaybackState.Saver) { PulseDeckPlaybackState() }
    val progressController = remember { PlaybackProgressController() }
    var playerOpen by playbackState::playerOpen
    var playerOpenFromMini by playbackState::playerOpenFromMini
    var playerOpenKey by playbackState::playerOpenKey
    var playerAutoOpened by playbackState::playerAutoOpened
    var playerCloseRequestKey by playbackState::playerCloseRequestKey
    var miniDismissed by playbackState::miniDismissed
    var playing by playbackState::playing
    var trackIndex by playbackState::trackIndex
    var trackSlideDirection by playbackState::trackSlideDirection
    var shuffleMode by playbackState::shuffleMode
    var repeatMode by playbackState::repeatMode
    var playbackHistory by playbackState::playbackHistory
    var manualQueueTrackKeys by playbackState::manualQueueTrackKeys
    var sleepTimerMinutes by playbackState::sleepTimerMinutes
    var sleepTimerDeadlineMillis by playbackState::sleepTimerDeadlineMillis
    var sleepTimerFadeOutEnabled by playbackState::sleepTimerFadeOutEnabled
    var sleepTimerDialogOpen by playbackState::sleepTimerDialogOpen
    fun currentPlaybackPositionMillis(): Long = progressController.currentPositionMillis

    fun requestPlaybackSeek(targetMillis: Long) {
        val safeTarget = targetMillis.coerceAtLeast(0L)
        progressController.updateFromSeek(safeTarget)
        playbackState.requestSeek(safeTarget)
    }

    fun resetDisplayedPlaybackPosition(requestSeek: Boolean = false) {
        progressController.reset()
        playbackState.resetDisplayedPlaybackPosition(requestSeek)
    }

    var mediaSessionRequested by rememberSaveable { mutableStateOf(false) }
    var playbackModeDialog by remember { mutableStateOf<PlayerModeMenu?>(null) }
    var trackActionsOpen by rememberSaveable { mutableStateOf(false) }
    var fullPlayerSaveSheetOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteTrack by remember { mutableStateOf<Track?>(null) }
    var pendingDeleteTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var pendingSettingsExportKind by remember { mutableStateOf<SettingsExportKind?>(null) }
    var infoDialog by remember { mutableStateOf<InfoDialogState?>(null) }
    var lyricsTrack by remember { mutableStateOf<Track?>(null) }
    var albumArtPickerTarget by remember { mutableStateOf<AlbumArtPickerTarget?>(null) }
    var quickAddDialogOpen by rememberSaveable { mutableStateOf(false) }
    var activeYouTubeSourceId by routeState::activeYouTubeSourceId
    var fallbackYouTubeSource by remember { mutableStateOf<YouTubeSource?>(null) }
    var resolvingYouTubeSourceId by remember { mutableStateOf<String?>(null) }
    var youtubeResolveGeneration by remember { mutableLongStateOf(0L) }
    var activeYouTubeResolveSessionId by remember { mutableLongStateOf(0L) }
    var activeStreamTrack by playbackState::activeStreamTrack
    var youtubeSources by remember { mutableStateOf<List<YouTubeSource>>(emptyList()) }
    var youtubePlaylists by remember { mutableStateOf<List<YouTubePlaylist>>(emptyList()) }
    var streamPlayHistory by remember { mutableStateOf<List<StreamPlayHistoryItem>>(emptyList()) }
    var streamDiscoverySnapshot by remember { mutableStateOf(StreamDiscoverySnapshot()) }
    var premiumDeckPodcastSnapshot by remember { mutableStateOf(PremiumDeckPodcastSnapshot()) }
    var playlistImportProgress by remember { mutableStateOf<PremiumDeckPlaylistImportProgress?>(null) }
    var followedStreamArtists by remember { mutableStateOf<List<FollowedStreamArtist>>(emptyList()) }
    var streamNewReleaseSnapshot by remember { mutableStateOf(StreamNewReleaseSnapshot()) }
    var recommendedAlbumsSnapshot by remember { mutableStateOf<RecommendedAlbumsSnapshot?>(null) }
    val recommendedAlbumRefreshCoordinator = remember { RecommendedAlbumRefreshCoordinator() }
    var streamReleaseNotificationReadIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var albumDownloadDrafts by remember { mutableStateOf<List<AlbumDownloadDraft>>(emptyList()) }
    var albumProjectTasks by remember { mutableStateOf<Map<String, AlbumProjectTaskState>>(emptyMap()) }
    var albumDownloaderInitialReleaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeAlbumProjectReleaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var albumAudioDownloadJobs by remember { mutableStateOf<List<AlbumAudioDownloadJob>>(emptyList()) }
    var streamDiscoveryLoading by remember { mutableStateOf(false) }
    var premiumDeckPodcastLoading by remember { mutableStateOf(false) }
    var premiumDeckPodcastLoadingShowId by remember { mutableStateOf<String?>(null) }
    var premiumDeckPodcastAddLoading by remember { mutableStateOf(false) }
    var streamNewReleaseLoading by remember { mutableStateOf(false) }
    var pendingFollowedArtistEvent by remember { mutableStateOf<BehaviorEvent?>(null) }
    var streamMixSessionSeed by rememberSaveable { mutableLongStateOf(newStreamMixSessionSeed()) }
    val youtubeShelfPersistJob = remember { AtomicReference<Job?>(null) }
    val youtubeShelfPersistVersion = remember { AtomicInteger(0) }
    val youtubePlaylistPersistJob = remember { AtomicReference<Job?>(null) }
    val youtubePlaylistPersistVersion = remember { AtomicInteger(0) }
    val streamHistoryPersistJob = remember { AtomicReference<Job?>(null) }
    val streamHistoryPersistVersion = remember { AtomicInteger(0) }
    val premiumDeckSearchPreviewJob = remember { AtomicReference<Job?>(null) }
    val premiumDeckStreamResolveJob = remember { AtomicReference<Job?>(null) }
    val premiumDeckNetworkJobs = remember { AtomicReference<Set<Job>>(emptySet()) }
    fun trackPremiumDeckNetworkJob(job: Job): Job {
        while (true) {
            val current = premiumDeckNetworkJobs.get()
            if (premiumDeckNetworkJobs.compareAndSet(current, current + job)) break
        }
        job.invokeOnCompletion {
            while (true) {
                val current = premiumDeckNetworkJobs.get()
                if (premiumDeckNetworkJobs.compareAndSet(current, current - job)) break
            }
        }
        return job
    }
    fun cancelPremiumDeckNetworkJobs(reason: String) {
        premiumDeckNetworkJobs.getAndSet(emptySet()).forEach { job ->
            job.cancel(CancellationException(reason))
        }
    }
    var youtubeTab by routeState::youtubeTab
    var youtubeShelf by routeState::youtubeShelf
    var routeHistory by routeState::routeHistory
    var suppressNextRouteMotion by routeState::suppressNextRouteMotion
    var youtubeDialog by remember { mutableStateOf<YouTubeDialogState?>(null) }
    var activeYouTubeQueueIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var transientYouTubeQueueSources by remember { mutableStateOf<List<YouTubeSource>>(emptyList()) }
    var activePremiumDeckSession by remember { mutableStateOf<PremiumDeckPlaybackSession?>(null) }
    var premiumDeckPrepareStates by remember { mutableStateOf<Map<String, PremiumDeckStreamPrepareState>>(emptyMap()) }
    var premiumDeckPrepareGeneration by remember { mutableLongStateOf(0L) }
    var premiumDeckPreparedPreviewSources by remember { mutableStateOf<Map<String, YouTubeSource>>(emptyMap()) }
    var premiumDeckSearchPreviewGeneration by remember { mutableLongStateOf(0L) }
    var albumTrackFallbackQueueIds by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var resolverCapabilities by remember { mutableStateOf(ResolverCapabilities()) }
    var smartCachePromptSource by remember { mutableStateOf<YouTubeSource?>(null) }
    var likedTrackKeys by libraryState::likedTrackKeys
    var dislikedTrackKeys by libraryState::dislikedTrackKeys
    var bookmarkedTrackKeys by libraryState::bookmarkedTrackKeys
    var playlistTrackKeys by libraryState::playlistTrackKeys
    var trackPlayCounts by libraryState::trackPlayCounts
    var waveformVisible by rememberSaveable { mutableStateOf(true) }
    var albumDetailCloseRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var albumVisualBackground by remember { mutableStateOf<Album?>(null) }
    var seekRequestKey by playbackState::seekRequestKey
    var seekRequestMillis by playbackState::seekRequestMillis
    var playerVolume by remember { mutableFloatStateOf(1f) }
    var audioEngineState by remember { mutableStateOf(AudioEngineState()) }
    var audioSettingsPage by routeState::audioSettingsPage
    var backgroundSettings by remember { mutableStateOf(BackgroundSettings()) }
    var pulseSettingsState by remember {
        mutableStateOf(PulseSettingsState(audio = audioEngineState, background = backgroundSettings).normalized())
    }
    var albumArtCacheStatusText by remember { mutableStateOf("Cache status loading...") }
    ApplyLookAndFeelRuntime(pulseSettingsState.lookAndFeel)
    var settingsLoaded by remember { mutableStateOf(false) }
    val premiumDeckOfflineActive = pulseSettingsState.misc.online.premiumDeckOfflineActive
    var startupCacheBudgetChecked by remember { mutableStateOf(false) }
    val settingsRepository = remember(context) { DataStoreSettingsRepository(context) }

    fun removeTracksFromLibrary(targets: Collection<Track>, requestSeek: Boolean = false) {
        val keys = targets.map { it.stableKey() }.toSet()
        if (keys.isEmpty()) return
        val nextTracks = scannedTracks.filterNot { it.stableKey() in keys }
        scannedTracks = nextTracks
        appScope.launch { localLibraryRepository.saveCachedTracks(nextTracks) }
        likedTrackKeys = (likedTrackKeys - keys).also { localLibraryRepository.saveStateSet(PREF_LIKED_TRACKS, it) }
        dislikedTrackKeys = (dislikedTrackKeys - keys).also { localLibraryRepository.saveStateSet(PREF_DISLIKED_TRACKS, it) }
        bookmarkedTrackKeys = (bookmarkedTrackKeys - keys).also { localLibraryRepository.saveStateSet(PREF_BOOKMARKED_TRACKS, it) }
        playlistTrackKeys = (playlistTrackKeys - keys).also { localLibraryRepository.saveStateSet(PREF_PLAYLIST_TRACKS, it) }
        trackPlayCounts = (trackPlayCounts - keys).also { localLibraryRepository.savePlayCounts(it) }
        trackIndex = trackIndex.coerceAtMost((scannedTracks.size - 1).coerceAtLeast(0))
        resetDisplayedPlaybackPosition(requestSeek = requestSeek)
    }

    fun removeTrackFromLibrary(target: Track, requestSeek: Boolean = false) {
        removeTracksFromLibrary(listOf(target), requestSeek = requestSeek)
    }

    fun remapLocalTrackKeys(keys: Set<String>, replacements: Map<String, String>): Set<String> =
        if (replacements.isEmpty()) {
            keys
        } else {
            keys.map { replacements[it] ?: it }.toSet()
        }

    fun remapLocalTrackPlayCounts(values: Map<String, Int>, replacements: Map<String, String>): Map<String, Int> {
        if (replacements.isEmpty()) return values
        val remapped = linkedMapOf<String, Int>()
        values.forEach { (key, count) ->
            val nextKey = replacements[key] ?: key
            remapped[nextKey] = (remapped[nextKey] ?: 0) + count
        }
        return remapped
    }

    suspend fun applyLocalLibraryScanResult(result: com.pulsedeck.app.library.LocalLibraryScanResult, cleanupState: Boolean) {
        val tracks = result.tracks
        scannedTracks = tracks
        localLibraryRepository.saveCachedTracks(tracks)
        if (cleanupState) {
            val replacements = result.stableKeyReplacements
            val cleanedState = cleanupLocalTrackState(
                validTrackKeys = tracks.map { it.stableKey() }.toSet(),
                likedKeys = remapLocalTrackKeys(likedTrackKeys, replacements),
                dislikedKeys = remapLocalTrackKeys(dislikedTrackKeys, replacements),
                bookmarkedKeys = remapLocalTrackKeys(bookmarkedTrackKeys, replacements),
                playlistKeys = remapLocalTrackKeys(playlistTrackKeys, replacements),
                playCounts = remapLocalTrackPlayCounts(trackPlayCounts, replacements),
            )
            likedTrackKeys = cleanedState.likedKeys
            dislikedTrackKeys = cleanedState.dislikedKeys
            bookmarkedTrackKeys = cleanedState.bookmarkedKeys
            playlistTrackKeys = cleanedState.playlistKeys
            trackPlayCounts = cleanedState.playCounts
            withContext(Dispatchers.IO) {
                localLibraryRepository.saveStateSet(PREF_LIKED_TRACKS, cleanedState.likedKeys)
                localLibraryRepository.saveStateSet(PREF_DISLIKED_TRACKS, cleanedState.dislikedKeys)
                localLibraryRepository.saveStateSet(PREF_BOOKMARKED_TRACKS, cleanedState.bookmarkedKeys)
                localLibraryRepository.saveStateSet(PREF_PLAYLIST_TRACKS, cleanedState.playlistKeys)
                localLibraryRepository.savePlayCounts(cleanedState.playCounts)
            }
        }
        trackIndex = trackIndex.coerceAtMost((tracks.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        currentOnFirstUsefulUi()
    }
    val settingsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val exportKind = pendingSettingsExportKind
        pendingSettingsExportKind = null
        if (uri == null || exportKind == null) return@rememberLauncherForActivityResult
        appScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val payload = when (exportKind) {
                    SettingsExportKind.Settings -> settingsRepository.exportSettings(pulseSettingsState.normalized())
                    SettingsExportKind.PrivateBackup -> settingsRepository.exportPrivateBackup(pulseSettingsState.normalized())
                    SettingsExportKind.Diagnostics -> buildSettingsDiagnosticsExport(pulseSettingsState.normalized()).toString(2)
                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(payload.toByteArray())
                } ?: error("Could not open export destination.")
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = {
                            when (exportKind) {
                                SettingsExportKind.Settings -> "Portable settings exported"
                                SettingsExportKind.PrivateBackup -> "Private backup exported"
                                SettingsExportKind.Diagnostics -> "Diagnostics exported"
                            }
                        },
                        onFailure = { "Export failed: ${it.message ?: "unknown error"}" },
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val settingsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        appScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not open import file.")
                settingsRepository.importSettings(raw, pulseSettingsState.normalized()).normalized()
            }
            withContext(Dispatchers.Main) {
                result
                    .onSuccess { imported ->
                        pulseSettingsState = imported
                        audioEngineState = imported.audio
                        backgroundSettings = imported.background
                        Toast.makeText(context, "Settings imported", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(context, "Import rejected: ${it.message ?: "invalid settings file"}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val targets = pendingDeleteTracks
        if (targets.isNotEmpty() && result.resultCode == Activity.RESULT_OK) {
            removeTracksFromLibrary(targets, requestSeek = true)
            val count = targets.size
            Toast.makeText(context, "Deleted $count ${if (count == 1) "file" else "files"} from library", Toast.LENGTH_SHORT).show()
        }
        pendingDeleteTracks = emptyList()
    }

    LaunchedEffect(localLibraryRepository) {
        withFrameNanos { }
        val snapshot = runCatching {
            loadStartupPersistenceSnapshot(context, localLibraryRepository)
        }.getOrDefault(StartupPersistenceSnapshot())
        likedTrackKeys = snapshot.likedTrackKeys
        dislikedTrackKeys = snapshot.dislikedTrackKeys
        bookmarkedTrackKeys = snapshot.bookmarkedTrackKeys
        playlistTrackKeys = snapshot.playlistTrackKeys
        trackPlayCounts = snapshot.trackPlayCounts
        favoriteRadioCountryCodes = snapshot.favoriteRadioCountryCodes
        favoriteRadioStationKeys = snapshot.favoriteRadioStationKeys
        recentRadioStationKeys = snapshot.recentRadioStationKeys
        startupPlaybackState = snapshot.lastPlaybackState
        youtubeSources = snapshot.youtubeSources
        youtubePlaylists = snapshot.youtubePlaylists
        streamPlayHistory = snapshot.streamPlayHistory
        streamDiscoverySnapshot = snapshot.streamDiscoverySnapshot
        premiumDeckPodcastSnapshot = snapshot.premiumDeckPodcastSnapshot
        followedStreamArtists = snapshot.followedStreamArtists
        streamNewReleaseSnapshot = snapshot.streamNewReleaseSnapshot
        recommendedAlbumsSnapshot = snapshot.recommendedAlbumsSnapshot
        streamReleaseNotificationReadIds = snapshot.streamReleaseNotificationReadIds
        premiumChartLastFmApiKey = snapshot.premiumChartLastFmApiKey
        PremiumDeckChartRuntime.lastFmApiKey = snapshot.premiumChartLastFmApiKey
        albumDownloadDrafts = snapshot.albumDownloadDrafts
        albumAudioDownloadJobs = snapshot.albumAudioDownloadJobs
        audioEngineState = snapshot.audioEngineState
        backgroundSettings = snapshot.backgroundSettings
        pulseSettingsState = PulseSettingsState(
            audio = snapshot.audioEngineState,
            background = snapshot.backgroundSettings,
        ).normalized()
        startupPersistenceLoaded = true
    }

    LaunchedEffect(permissionGranted) {
        withFrameNanos { }
        if (permissionGranted) {
            localLibraryLoaded = false
            val cachedTracks = localLibraryRepository.loadCachedTracks()
            if (cachedTracks != null) {
                scannedTracks = cachedTracks
            } else {
                val result = localLibraryRepository.scanDeviceTracks(
                    previousTracks = emptyList(),
                    reason = LocalLibraryScanReason.CacheMiss,
                )
                applyLocalLibraryScanResult(result, cleanupState = false)
            }
            localLibraryLoaded = true
        } else {
            localLibraryLoaded = true
        }
    }
    LaunchedEffect(audioEngineState, startupPersistenceLoaded) {
        PulseAudioEngineStore.update(audioEngineState)
        if (startupPersistenceLoaded) {
            withContext(Dispatchers.IO) { saveAudioEngineState(context, audioEngineState) }
        }
    }
    LaunchedEffect(audioEngineState, backgroundSettings) {
        pulseSettingsState = pulseSettingsState.copy(
            audio = audioEngineState,
            background = backgroundSettings,
        ).normalized()
    }
    LaunchedEffect(settingsRepository, startupPersistenceLoaded) {
        if (!startupPersistenceLoaded) return@LaunchedEffect
        settingsRepository.migrateLegacyIfNeeded(audioEngineState, backgroundSettings)
        settingsRepository.settings.collectLatest { storedSettings ->
            val normalized = storedSettings.normalized()
            pulseSettingsState = normalized
            audioEngineState = normalized.audio
            backgroundSettings = normalized.background
            AlbumArtworkRuntime.update(normalized.albumArt)
            PulseOnlineRuntime.update(normalized.misc.online)
            LyricsRuntime.update(normalized.lyrics)
            PulsePlaybackRuntimeStore.update(normalized.toPlaybackRuntimeSettings(capabilities = DeviceCapabilities()))
            settingsLoaded = true
        }
    }
    LaunchedEffect(pulseSettingsState, settingsLoaded) {
        val normalized = pulseSettingsState.normalized()
        AlbumArtworkRuntime.update(normalized.albumArt)
        PulseOnlineRuntime.update(normalized.misc.online)
        LyricsRuntime.update(normalized.lyrics)
        PulsePlaybackRuntimeStore.update(normalized.toPlaybackRuntimeSettings(capabilities = DeviceCapabilities()))
        if (!settingsLoaded) return@LaunchedEffect
        delay(250L)
        settingsRepository.save(normalized)
    }
    LaunchedEffect(settingsLoaded) {
        if (!settingsLoaded || startupCacheBudgetChecked) return@LaunchedEffect
        delay(2_000L)
        startupCacheBudgetChecked = true
        CacheBudgetManager.enforce(
            context = context.applicationContext,
            albumArtCacheMb = pulseSettingsState.albumArt.cacheSizeMb,
            lyricsCacheMb = pulseSettingsState.lyrics.cacheSizeMb,
            reason = CacheCleanupReason.StartupStaleTemp,
        )
    }
    LaunchedEffect(
        screen,
        pulseSettingsState.albumArt.cacheSizeMb,
        pulseSettingsState.lyrics.cacheSizeMb,
        AlbumArtworkRuntime.cacheRevision,
        LyricsRuntime.cacheRevision,
        youtubeSources,
    ) {
        if (screen != Screen.Settings) return@LaunchedEffect
        albumArtCacheStatusText = "Cache status loading..."
        albumArtCacheStatusText = runCatching {
            CacheBudgetManager.status(
                context = context.applicationContext,
                albumArtCacheMb = pulseSettingsState.albumArt.cacheSizeMb,
                lyricsCacheMb = pulseSettingsState.lyrics.cacheSizeMb,
                offlineSources = youtubeSources,
            )
        }.getOrElse {
            "Cache status unavailable"
        }
    }
    LaunchedEffect(screen, startupPersistenceLoaded) {
        if (screen != Screen.Audio) audioSettingsPage = AudioSettingsPage.Index
        if (screen == Screen.YouTube) streamMixSessionSeed = newStreamMixSessionSeed()
        if (startupPersistenceLoaded && screen == Screen.PulseRadio && !pulseSettingsState.misc.online.offlineMode) {
            if (radioCountries.isEmpty() && !radioCountriesLoading) {
                radioCountriesLoading = true
                runCatching { radioClient.getCountryList() }
                    .onSuccess { radioCountries = it }
                    .onFailure { radioError = it.message ?: "Could not load countries" }
                radioCountriesLoading = false
            }
            if (radioStations.isEmpty() && !radioStationsLoading) {
                radioStationsLoading = true
                radioError = null
                runCatching {
                    radioClient.searchStationsByCountry(
                        countryCode = radioCountryCode,
                        limit = radioStationSearchLimit(radioCountryCode),
                        name = radioNameQuery,
                        tag = "",
                        codec = "",
                    )
                }
                    .onSuccess { radioStations = it }
                    .onFailure { radioError = it.message ?: "Could not load stations" }
                radioStationsLoading = false
            }
        }
    }
    var resolverCapabilitiesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(startupPersistenceLoaded, screen, premiumDeckOfflineActive) {
        val needsResolverCapabilities = screen == Screen.YouTube || screen == Screen.AlbumDownloader
        if (!startupPersistenceLoaded || !needsResolverCapabilities || resolverCapabilitiesLoaded || premiumDeckOfflineActive) {
            return@LaunchedEffect
        }
        resolverCapabilitiesLoaded = true
        resolverCapabilities = fetchResolverCapabilities(context)
    }
    LaunchedEffect(startupPersistenceLoaded, youtubeSearchQuery, premiumDeckOfflineActive, youtubeSources) {
        val needle = youtubeSearchQuery.trim()
        premiumDeckSearchPreviewGeneration += 1L
        premiumDeckSearchPreviewJob.getAndSet(null)?.cancel()
        if (needle.length < 2) {
            youtubeSearchResults = emptyList()
            youtubeSearchSuggestions = emptyList()
            youtubeSearchLoading = false
            youtubeSearchError = null
            return@LaunchedEffect
        }
        if (!startupPersistenceLoaded) {
            youtubeSearchLoading = false
            youtubeSearchError = null
            return@LaunchedEffect
        }
        if (premiumDeckOfflineActive) {
            val normalizedNeedle = needle.normalizedSearchText()
            val offlineResults = youtubeSources
                .asSequence()
                .filter { it.reviewState == YouTubeReviewState.Accepted && it.kind == YouTubeSourceKind.Video && it.isOfflineSaved() }
                .filter {
                    val haystack = listOf(it.title, it.author, it.channelTitle, it.albumTitleHint)
                        .joinToString(" ")
                        .normalizedSearchText()
                    normalizedNeedle.split(" ").filter { token -> token.isNotBlank() }.all { token -> token in haystack }
                }
                .sortedWith(compareByDescending<YouTubeSource> { it.reaction == YouTubeReaction.Liked }.thenByDescending { it.playCount }.thenByDescending { it.lastPlayedMillis })
                .take(40)
                .map { source -> YouTubeSearchResult(source = source, durationMillis = source.durationMillis, cachedMillis = source.addedMillis) }
                .toList()
            youtubeSearchResults = offlineResults
            youtubeSearchSuggestions = emptyList()
            youtubeSearchLoading = false
            youtubeSearchError = if (offlineResults.isEmpty()) "No saved PremiumDeck matches" else null
            return@LaunchedEffect
        }
        val cached = withContext(Dispatchers.IO) { loadYouTubeSearchCache(context, needle) }
        if (cached != null) {
            youtubeSearchResults = cached.results
            youtubeSearchSuggestions = cached.suggestions
            youtubeSearchError = null
        }
        youtubeSearchLoading = cached == null
        youtubeSearchError = null
        delay(180L)
        if (youtubeSearchQuery.trim() != needle) return@LaunchedEffect
        youtubeSearchLoading = true
        val response = searchYouTubeVideos(needle, requestLabel = "interactive", debounceMillis = 180L)
        if (youtubeSearchQuery.trim() == needle) {
            youtubeSearchResults = response.results
            youtubeSearchSuggestions = response.suggestions
            youtubeSearchLoading = false
            youtubeSearchError = if (response.results.isEmpty()) "No PremiumDeck results found" else null
            if (response.results.isNotEmpty() || response.suggestions.isNotEmpty()) {
                withContext(Dispatchers.IO) { saveYouTubeSearchCache(context, needle, response) }
            }
        }
    }
    LaunchedEffect(startupPersistenceLoaded, screen, youtubeSources.size, streamPlayHistory.firstOrNull()?.playedAtMillis, followedStreamArtists, premiumDeckOfflineActive, pulseSettingsState.misc.online.externalRecommendations) {
        if (startupPersistenceLoaded && screen == Screen.YouTube && !premiumDeckOfflineActive && pulseSettingsState.misc.online.externalRecommendations && streamDiscoverySnapshot.isStale() && !streamDiscoveryLoading) {
            streamDiscoveryLoading = true
            val snapshot = withContext(Dispatchers.IO) { fetchStreamDiscoverySnapshot(youtubeSources, streamPlayHistory, followedStreamArtists) }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                streamDiscoveryLoading = false
                return@LaunchedEffect
            }
            streamDiscoverySnapshot = snapshot
            saveStreamDiscoverySnapshot(context, snapshot)
            streamDiscoveryLoading = false
        }
    }
    LaunchedEffect(startupPersistenceLoaded, screen, followedStreamArtists, streamNewReleaseSnapshot.savedMillis, premiumDeckOfflineActive, pulseSettingsState.misc.online.externalRecommendations) {
        if (
            startupPersistenceLoaded &&
            screen == Screen.YouTube &&
            !premiumDeckOfflineActive &&
            pulseSettingsState.misc.online.externalRecommendations &&
            followedStreamArtists.isNotEmpty() &&
            streamNewReleaseSnapshot.isStaleFor(followedStreamArtists) &&
            !streamNewReleaseLoading
        ) {
            streamNewReleaseLoading = true
            val snapshot = runCatching { withContext(Dispatchers.IO) { fetchStreamNewReleaseSnapshot(followedStreamArtists, youtubeSources) } }
                .getOrElse {
                    streamNewReleaseLoading = false
                    return@LaunchedEffect
                }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                streamNewReleaseLoading = false
                return@LaunchedEffect
            }
            streamNewReleaseSnapshot = snapshot
            saveStreamNewReleaseSnapshot(context, snapshot)
            streamNewReleaseLoading = false
        }
    }
    val streamReleaseNotifications = remember(followedStreamArtists, streamNewReleaseSnapshot.results, youtubeSources, streamReleaseNotificationReadIds) {
        buildStreamReleaseNotifications(
            artists = followedStreamArtists,
            results = streamNewReleaseSnapshot.results,
            savedSources = youtubeSources,
            readNotificationIds = streamReleaseNotificationReadIds,
        )
    }

    fun scheduleLatestPersistence(
        jobRef: AtomicReference<Job?>,
        versionCounter: AtomicInteger,
        debounceMillis: Long = 180L,
        persist: () -> Unit,
    ) {
        val version = versionCounter.incrementAndGet()
        val nextJob = appScope.launch(Dispatchers.IO) {
            delay(debounceMillis)
            synchronized(jobRef) {
                if (version == versionCounter.get()) persist()
            }
        }
        jobRef.getAndSet(nextJob)?.cancel()
    }

    fun saveYouTubeShelf(nextSources: List<YouTubeSource>) {
        youtubeSources = nextSources
        scheduleLatestPersistence(youtubeShelfPersistJob, youtubeShelfPersistVersion) {
            saveYouTubeSources(context, nextSources)
        }
    }
    fun saveYouTubePlaylistShelf(nextPlaylists: List<YouTubePlaylist>) {
        youtubePlaylists = nextPlaylists
        scheduleLatestPersistence(youtubePlaylistPersistJob, youtubePlaylistPersistVersion) {
            saveYouTubePlaylists(context, nextPlaylists)
        }
    }
    fun saveAlbumDownloadDraftShelf(nextDrafts: List<AlbumDownloadDraft>) {
        albumDownloadDrafts = nextDrafts
        saveAlbumDownloadDrafts(context, nextDrafts)
    }
    fun saveAlbumAudioDownloadJobShelf(nextJobs: List<AlbumAudioDownloadJob>) {
        albumAudioDownloadJobs = nextJobs
        saveAlbumAudioDownloadJobs(context, nextJobs)
    }
    fun saveAlbumDownloadDraftSilently(release: AlbumDownloadRelease) {
        val draft = AlbumDownloadDraft(release = release)
        saveAlbumDownloadDraftShelf((listOf(draft) + albumDownloadDrafts.filterNot { it.release.id == release.id }).take(50))
    }
    fun latestAlbumProjectRelease(release: AlbumDownloadRelease): AlbumDownloadRelease =
        preferredAlbumProjectRelease(
            release,
            albumDownloadDrafts.firstOrNull { it.release.id == release.id }?.release,
        )
    fun updateAlbumProjectTask(task: AlbumProjectTaskState?) {
        albumProjectTasks = if (task == null) {
            albumProjectTasks
        } else {
            albumProjectTasks + (task.releaseId to task)
        }
    }
    fun clearAlbumProjectTask(releaseId: String) {
        albumProjectTasks = albumProjectTasks - releaseId
    }
    fun startAlbumTrackMatching(requestedRelease: AlbumDownloadRelease) {
        val release = latestAlbumProjectRelease(requestedRelease)
        if (albumProjectTasks[release.id]?.phase == AlbumProjectTaskPhase.Matching) return
        if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
            Toast.makeText(context, "Offline Deck blocks album matching", Toast.LENGTH_SHORT).show()
            return
        }
        if (release.tracks.isEmpty()) {
            Toast.makeText(context, "This release has no tracklist to match", Toast.LENGTH_SHORT).show()
            return
        }
        saveAlbumDownloadDraftSilently(release)
        val repairCount = release.albumTracksNeedingRepair(youtubeSources).size
        val alreadyHasMatches = release.tracks.any { it.matchedSource != null }
        val repairOnly = alreadyHasMatches && repairCount in 1 until release.tracks.size
        val total = if (repairOnly) repairCount else release.tracks.size
        updateAlbumProjectTask(
            AlbumProjectTaskState(
                releaseId = release.id,
                title = release.title,
                artist = release.artist,
                phase = AlbumProjectTaskPhase.Matching,
                total = total,
                message = if (repairOnly) "Repairing weak tracks" else "Starting match",
            ),
        )
        trackPremiumDeckNetworkJob(appScope.launch {
            val result = runCatching {
                matchAlbumTracksToPremiumDeck(
                    release = release,
                    existingSources = youtubeSources,
                    repairOnly = repairOnly,
                    prepareSource = { source -> prepareAlbumMatchSource(context, source) },
                    onTrackStarted = { track, index ->
                        withContext(Dispatchers.Main.immediate) {
                            updateAlbumProjectTask(
                                AlbumProjectTaskState(
                                    releaseId = release.id,
                                    title = release.title,
                                    artist = release.artist,
                                    phase = AlbumProjectTaskPhase.Matching,
                                    total = total,
                                    completed = index,
                                    activePosition = track.position,
                                    message = if (repairOnly) "Repairing ${track.title}" else "Matching ${track.title}",
                                ),
                            )
                        }
                    },
                    onTrackMatched = { updatedRelease, updatedTrack, index ->
                        withContext(Dispatchers.Main.immediate) {
                            saveAlbumDownloadDraftSilently(updatedRelease)
                            updateAlbumProjectTask(
                                AlbumProjectTaskState(
                                    releaseId = updatedRelease.id,
                                    title = updatedRelease.title,
                                    artist = updatedRelease.artist,
                                    phase = AlbumProjectTaskPhase.Matching,
                                    total = total,
                                    completed = index + 1,
                                    activePosition = updatedTrack.position,
                                    message = if (updatedTrack.matchVerified) {
                                        "Verified ${updatedTrack.title}"
                                    } else if (updatedTrack.matchedSource != null) {
                                        "Candidate saved for ${updatedTrack.title}"
                                    } else {
                                        "No strong source for ${updatedTrack.title}"
                                    },
                                ),
                            )
                        }
                    },
                )
            }
            withContext(Dispatchers.Main.immediate) {
                result
                    .onSuccess { updated ->
                        saveAlbumDownloadDraftSilently(updated)
                        val matched = updated.tracks.count { it.matchedSource != null }
                        val verified = updated.tracks.count { it.matchVerified }
                        val weak = updated.albumTracksNeedingRepair(youtubeSources).size
                        updateAlbumProjectTask(
                            AlbumProjectTaskState(
                                releaseId = updated.id,
                                title = updated.title,
                                artist = updated.artist,
                                phase = AlbumProjectTaskPhase.Matching,
                                total = total,
                                completed = total,
                                message = if (weak > 0) "$verified verified  |  $weak need repair" else "Verified $verified/$matched tracks",
                            ),
                        )
                        Toast.makeText(context, if (weak > 0) "Matched $matched tracks, $weak still need repair" else "Verified $verified tracks", Toast.LENGTH_SHORT).show()
                        appScope.launch {
                            delay(3_200L)
                            if (albumProjectTasks[updated.id]?.phase == AlbumProjectTaskPhase.Matching) {
                                clearAlbumProjectTask(updated.id)
                            }
                        }
                    }
                    .onFailure { throwable ->
                        updateAlbumProjectTask(
                            AlbumProjectTaskState(
                                releaseId = release.id,
                                title = release.title,
                                artist = release.artist,
                                phase = AlbumProjectTaskPhase.Matching,
                                total = total,
                                completed = albumProjectTasks[release.id]?.completed ?: 0,
                                message = throwable.message ?: "Could not match tracks",
                            ),
                        )
                        Toast.makeText(context, throwable.message ?: "Could not match tracks", Toast.LENGTH_LONG).show()
                    }
            }
        })
    }
    fun selectAlbumProjectTrackMatch(release: AlbumDownloadRelease, track: AlbumDownloadTrack, source: YouTubeSource) {
        val playable = source.copy(
            reviewState = YouTubeReviewState.Accepted,
            thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
        )
        val existing = youtubeSources.firstOrNull {
            it.id == playable.id || it.url == playable.url || it.streamDistinctKey() == playable.streamDistinctKey()
        }
        val persisted = existing ?: playable
        if (existing == null) {
            saveYouTubeShelf((listOf(persisted) + youtubeSources).distinctBy { it.streamDistinctKey() })
        }
        val updated = release.withSelectedAlbumTrackMatch(track, persisted, listOf(persisted) + youtubeSources)
        saveAlbumDownloadDraftSilently(updated)
        Toast.makeText(context, "Track match changed", Toast.LENGTH_SHORT).show()
    }
    fun updateYouTubeSource(sourceId: String, persist: Boolean = true, transform: (YouTubeSource) -> YouTubeSource) {
        var changed = false
        val nextSources = youtubeSources.map { source ->
            if (source.id == sourceId) {
                val next = transform(source)
                if (next != source) changed = true
                next
            } else {
                source
            }
        }
        if (!changed) return
        if (persist) {
            saveYouTubeShelf(nextSources)
        } else {
            youtubeSources = nextSources
        }
    }
    fun updateYouTubePlaylist(playlistId: String, transform: (YouTubePlaylist) -> YouTubePlaylist) {
        saveYouTubePlaylistShelf(youtubePlaylists.map { playlist -> if (playlist.id == playlistId) transform(playlist) else playlist })
    }
    fun existingFullPlayerSaveSource(source: YouTubeSource): YouTubeSource? {
        val sourceKey = source.streamDistinctKey()
        return youtubeSources.firstOrNull {
            it.id == source.id || it.url == source.url || it.streamDistinctKey() == sourceKey
        }
    }
    fun ensureFullPlayerSaveSource(source: YouTubeSource): YouTubeSource {
        existingFullPlayerSaveSource(source)?.let { return it }
        val persisted = source.sanitizedForNewFullPlayerSave()
        saveYouTubeShelf((listOf(persisted) + youtubeSources).distinctBy { it.streamDistinctKey() })
        return persisted
    }
    fun saveStreamHistory(nextHistory: List<StreamPlayHistoryItem>) {
        streamPlayHistory = nextHistory.take(200)
        val snapshot = streamPlayHistory
        scheduleLatestPersistence(streamHistoryPersistJob, streamHistoryPersistVersion, debounceMillis = 300L) {
            saveStreamPlayHistory(context, snapshot)
        }
    }
    fun streamPlaylistSourceCatalog(): List<YouTubeSource> =
        (youtubeSources + transientYouTubeQueueSources + streamPlayHistory.mapNotNull { it.toYouTubeSource() })
            .distinctBy { it.streamDistinctKey() }
    fun streamPlaylistSourceLookup(): Map<String, YouTubeSource> =
        streamSourceIdentityLookup(streamPlaylistSourceCatalog())
    fun resolveYouTubePlaylistSources(playlist: YouTubePlaylist): List<YouTubeSource> {
        val sourceByKey = streamPlaylistSourceLookup()
        return playlist.sourceIds
            .mapNotNull { sourceByKey[it] }
            .distinctBy { it.streamDistinctKey() }
    }
    fun saveFollowedArtists(nextArtists: List<FollowedStreamArtist>) {
        followedStreamArtists = nextArtists
            .filter { it.key.isNotBlank() }
            .distinctBy { it.key }
            .sortedBy { it.name.lowercase(Locale.US) }
        saveFollowedStreamArtists(context, followedStreamArtists)
    }
    fun saveStreamReleaseNotificationReadIdsState(nextIds: Set<String>) {
        streamReleaseNotificationReadIds = nextIds.filter { it.isNotBlank() }.take(240).toSet()
        saveStreamReleaseNotificationReadIds(context, streamReleaseNotificationReadIds)
    }
    fun markStreamReleaseNotificationRead(notification: StreamReleaseNotification) {
        if (notification.id !in streamReleaseNotificationReadIds) {
            saveStreamReleaseNotificationReadIdsState(streamReleaseNotificationReadIds + notification.id)
        }
    }
    fun markAllStreamReleaseNotificationsRead() {
        val nextIds = streamReleaseNotificationReadIds + streamReleaseNotifications.map { it.id }
        saveStreamReleaseNotificationReadIdsState(nextIds)
    }
    fun refreshStreamNewReleases(force: Boolean = true) {
        if (streamNewReleaseLoading) return
        if (followedStreamArtists.isEmpty()) {
            Toast.makeText(context, "Follow artists to build New Releases", Toast.LENGTH_SHORT).show()
            return
        }
        if (premiumDeckOfflineActive || !pulseSettingsState.misc.online.externalRecommendations) {
            Toast.makeText(context, if (premiumDeckOfflineActive) "Offline Deck is on" else "External recommendations are off", Toast.LENGTH_SHORT).show()
            return
        }
        if (!force && !streamNewReleaseSnapshot.isStaleFor(followedStreamArtists)) return
        streamNewReleaseLoading = true
        trackPremiumDeckNetworkJob(appScope.launch {
            val snapshot = runCatching { withContext(Dispatchers.IO) { fetchStreamNewReleaseSnapshot(followedStreamArtists, youtubeSources) } }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                streamNewReleaseLoading = false
                return@launch
            }
            streamNewReleaseLoading = false
            snapshot
                .onSuccess { latest ->
                    streamNewReleaseSnapshot = latest
                    saveStreamNewReleaseSnapshot(context, latest)
                    if (force) Toast.makeText(context, "New Releases refreshed", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    if (force) Toast.makeText(context, "Could not refresh New Releases", Toast.LENGTH_SHORT).show()
                }
        })
    }
    fun toggleFollowedStreamArtist(
        rawArtist: String,
        officialArtistName: String? = null,
        officialArtistKey: String? = null,
        officialChannelUrl: String? = null,
        officialChannelKey: String? = null,
    ) {
        val name = rawArtist.cleanStreamArtistName()
        val key = name.normalizedSearchText()
        if (key.isBlank() || key in genericStreamAlbumArtistKeys) {
            Toast.makeText(context, "Could not follow this artist", Toast.LENGTH_SHORT).show()
            return
        }
        val officialName = officialArtistName?.cleanStreamArtistName()?.takeIf { it.isNotBlank() } ?: name
        val officialKey = officialArtistKey?.takeIf { it.isNotBlank() }
            ?: officialName.normalizedSearchText().ifBlank { key }
        val channelUrl = officialChannelUrl.orEmpty()
        val channelKey = officialChannelKey?.takeIf { it.isNotBlank() } ?: channelUrl.youtubeChannelIdentityKey()
        val alreadyFollowing = followedStreamArtists.any { it.key == key }
        val nextArtists = if (alreadyFollowing) {
            followedStreamArtists.filterNot { it.key == key }
        } else {
            followedStreamArtists + FollowedStreamArtist(
                name = name,
                key = key,
                officialName = officialName,
                officialKey = officialKey,
                officialChannelUrl = channelUrl,
                officialChannelKey = channelKey,
            )
        }
        saveFollowedArtists(nextArtists)
        pendingFollowedArtistEvent = BehaviorEvent(
            type = if (alreadyFollowing) BehaviorEventType.ArtistUnfollowed else BehaviorEventType.ArtistFollowed,
            itemId = "artist:$key",
            title = name,
            artist = officialName,
            album = "Followed PremiumDeck Artists",
            genre = "Artist Follow",
            source = CandidateSource.PremiumDeck,
            metadata = mapOf("artistKey" to key, "officialArtistKey" to officialKey, "officialChannelKey" to channelKey),
        )
        Toast.makeText(context, if (alreadyFollowing) "Unfollowed $name" else "Following $name", Toast.LENGTH_SHORT).show()
        if (!alreadyFollowing) {
            streamDiscoverySnapshot = StreamDiscoverySnapshot()
            refreshStreamNewReleases(force = true)
        }
    }
    fun toggleFollowedStreamArtist(option: StreamArtistFollowOption) {
        toggleFollowedStreamArtist(option.name, option.officialName, option.officialKey, option.officialChannelUrl, option.officialChannelKey)
    }
    fun recordStreamPlay(source: YouTubeSource, playedAtMillis: Long = System.currentTimeMillis()) {
        saveStreamHistory(listOf(source.toPlayHistoryItem(playedAtMillis)) + streamPlayHistory)
    }
    fun refreshStreamDiscovery(force: Boolean = true) {
        if (streamDiscoveryLoading) return
        if (premiumDeckOfflineActive || !pulseSettingsState.misc.online.externalRecommendations) {
            Toast.makeText(context, if (premiumDeckOfflineActive) "Offline Deck is on" else "External recommendations are off", Toast.LENGTH_SHORT).show()
            return
        }
        if (!force && !streamDiscoverySnapshot.isStale()) return
        if (force) streamMixSessionSeed = newStreamMixSessionSeed()
        streamDiscoveryLoading = true
        trackPremiumDeckNetworkJob(appScope.launch {
            val snapshot = withContext(Dispatchers.IO) { fetchStreamDiscoverySnapshot(youtubeSources, streamPlayHistory, followedStreamArtists) }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                streamDiscoveryLoading = false
                return@launch
            }
            streamDiscoverySnapshot = snapshot
            saveStreamDiscoverySnapshot(context, snapshot)
            streamDiscoveryLoading = false
            if (force) Toast.makeText(context, if (snapshot.results.isNotEmpty()) "YouTube Explore refreshed" else "No discovery results found", Toast.LENGTH_SHORT).show()
        })
    }
    fun refreshPremiumDeckPodcasts(force: Boolean = true) {
        if (premiumDeckPodcastLoading) return
        if (premiumDeckOfflineActive || !pulseSettingsState.misc.online.externalRecommendations) {
            Toast.makeText(context, if (premiumDeckOfflineActive) "Offline Deck is on" else "External recommendations are off", Toast.LENGTH_SHORT).show()
            return
        }
        if (!force && premiumDeckPodcastSnapshot.isFresh()) return
        if (!force && premiumDeckPodcastSnapshot.results.isNotEmpty()) {
            youtubeTab = YouTubeLibraryTab.Podcasts
            youtubeShelf = YouTubeSmartShelf.Podcasts
            return
        }
        premiumDeckPodcastLoading = true
        trackPremiumDeckNetworkJob(appScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchPremiumDeckPodcastSnapshot(youtubeSources + premiumDeckPodcastSnapshot.results.map { it.source }) } }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                premiumDeckPodcastLoading = false
                return@launch
            }
            premiumDeckPodcastLoading = false
            result
                .onSuccess { snapshot ->
                    val customShowIds = premiumDeckPodcastSnapshot.customShows.map { it.id }.toSet()
                    val customEpisodes = premiumDeckPodcastSnapshot.episodesByShow.filterKeys { it in customShowIds }
                    val nextSnapshot = snapshot.copy(
                        customShows = premiumDeckPodcastSnapshot.customShows,
                        episodesByShow = snapshot.episodesByShow + customEpisodes,
                    )
                    premiumDeckPodcastSnapshot = nextSnapshot
                    savePremiumDeckPodcastSnapshot(context, nextSnapshot)
                    if (nextSnapshot.results.isNotEmpty()) {
                        saveYouTubeShelf((nextSnapshot.results.map { it.source }.take(20) + youtubeSources).distinctBy { it.streamDistinctKey() })
                        youtubeTab = YouTubeLibraryTab.Podcasts
                        youtubeShelf = YouTubeSmartShelf.Podcasts
                    }
                    Toast.makeText(context, if (nextSnapshot.results.isEmpty()) "No podcasts found yet" else "Podcasts refreshed", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "Could not refresh podcasts", Toast.LENGTH_SHORT).show()
                }
            })
    }
    fun openPremiumDeckPodcastShow(showId: String) {
        youtubeTab = YouTubeLibraryTab.Podcasts
        youtubeShelf = YouTubeSmartShelf.Podcasts
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Offline Deck is on", Toast.LENGTH_SHORT).show()
            return
        }
        if (premiumDeckPodcastLoadingShowId == showId) return
        if (premiumDeckPodcastSnapshot.isShowFresh(showId)) return
        premiumDeckPodcastLoadingShowId = showId
        trackPremiumDeckNetworkJob(appScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetchPremiumDeckPodcastShowEpisodes(
                        showId = showId,
                        existingSources = youtubeSources + premiumDeckPodcastSnapshot.results.map { it.source },
                        customShows = premiumDeckPodcastSnapshot.customShows,
                        limit = 10,
                    )
                }
            }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                premiumDeckPodcastLoadingShowId = null
                return@launch
            }
            premiumDeckPodcastLoadingShowId = null
            result
                .onSuccess { episodes ->
                    if (episodes.isNotEmpty()) {
                        val nextSnapshot = premiumDeckPodcastSnapshot.withShowEpisodes(showId, episodes)
                        premiumDeckPodcastSnapshot = nextSnapshot
                        savePremiumDeckPodcastSnapshot(context, nextSnapshot)
                        saveYouTubeShelf((episodes.map { it.source } + youtubeSources).distinctBy { it.streamDistinctKey() })
                    } else {
                        Toast.makeText(context, "No recent episodes found", Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure {
                    Toast.makeText(context, "Could not load podcast episodes", Toast.LENGTH_SHORT).show()
                }
        })
    }
    fun addPremiumDeckPodcast(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) {
            Toast.makeText(context, "Enter a podcast or channel name", Toast.LENGTH_SHORT).show()
            return
        }
        if (premiumDeckPodcastAddLoading) return
        if (premiumDeckOfflineActive || !pulseSettingsState.misc.online.externalRecommendations) {
            Toast.makeText(context, if (premiumDeckOfflineActive) "Offline Deck is on" else "External recommendations are off", Toast.LENGTH_SHORT).show()
            return
        }
        youtubeTab = YouTubeLibraryTab.Podcasts
        youtubeShelf = YouTubeSmartShelf.Podcasts
        premiumDeckPodcastAddLoading = true
        trackPremiumDeckNetworkJob(appScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetchPremiumDeckCustomPodcastShow(
                        query = cleanQuery,
                        existingSources = youtubeSources + premiumDeckPodcastSnapshot.results.map { it.source },
                        limit = 10,
                    )
                }
            }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                premiumDeckPodcastAddLoading = false
                return@launch
            }
            premiumDeckPodcastAddLoading = false
            result
                .onSuccess { discovery ->
                    if (discovery == null || discovery.episodes.isEmpty()) {
                        Toast.makeText(context, "No recent podcast episodes found", Toast.LENGTH_SHORT).show()
                    } else {
                        val nextSnapshot = premiumDeckPodcastSnapshot
                            .withCustomShow(discovery.show)
                            .withShowEpisodes(discovery.show.id, discovery.episodes)
                        premiumDeckPodcastSnapshot = nextSnapshot
                        savePremiumDeckPodcastSnapshot(context, nextSnapshot)
                        saveYouTubeShelf((discovery.episodes.map { it.source } + youtubeSources).distinctBy { it.streamDistinctKey() })
                        Toast.makeText(context, "Added ${discovery.show.title}", Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure {
                    Toast.makeText(context, "Could not add podcast", Toast.LENGTH_SHORT).show()
                }
        })
    }
    fun removeYouTubeSourceEverywhere(source: YouTubeSource) {
        saveYouTubeShelf(youtubeSources.filterNot { it.id == source.id })
        saveYouTubePlaylistShelf(
            youtubePlaylists.map { playlist ->
                playlist.copy(sourceIds = playlist.sourceIds.filterNot { it == source.id }, updatedMillis = System.currentTimeMillis())
            },
        )
        if (activeYouTubeSourceId == source.id) {
            activeYouTubeSourceId = null
            fallbackYouTubeSource = null
            activeYouTubeQueueIds = emptyList()
            transientYouTubeQueueSources = emptyList()
            albumTrackFallbackQueueIds = emptyMap()
            activeStreamTrack = null
        }
    }
    fun removeYouTubeDownload(source: YouTubeSource) {
        source.downloadedUri?.let { raw ->
            runCatching { context.contentResolver.delete(Uri.parse(raw), null, null) }
        }
        updateYouTubeSource(source.id) {
            it.copy(
                downloadedUri = null,
                downloadState = YouTubeDownloadState.None,
                downloadProgress = 0,
                status = if (it.cachedUri != null) YouTubeSourceStatus.Cached else YouTubeSourceStatus.StreamReady,
            )
        }
    }

    fun scanLocalLibrary() {
        if (!permissionGranted) {
            permissionLauncher.launch(audioPermission)
            return
        }
        localLibraryScanJob.getAndSet(null)?.cancel()
        localLibraryScanGeneration += 1L
        val generation = localLibraryScanGeneration
        val previousTracks = scannedTracks
        val job = appScope.launch {
            localLibraryLoaded = false
            try {
                val result = localLibraryRepository.scanDeviceTracks(
                    previousTracks = previousTracks,
                    reason = LocalLibraryScanReason.ManualRefresh,
                )
                if (generation != localLibraryScanGeneration) {
                    PulseDeckLibraryDiagnostics.staleScanIgnored(LocalLibraryScanReason.ManualRefresh, generation)
                    return@launch
                }
                applyLocalLibraryScanResult(result, cleanupState = true)
                localLibraryLoaded = true
                showToastAllowingPlatformDiskChecks(context, "Local library refreshed")
            } catch (exception: CancellationException) {
                if (generation == localLibraryScanGeneration) localLibraryLoaded = true
                throw exception
            } catch (throwable: Throwable) {
                if (generation == localLibraryScanGeneration) {
                    localLibraryLoaded = true
                    showToastAllowingPlatformDiskChecks(context, "Local library refresh failed")
                }
            }
        }
        localLibraryScanJob.set(job)
    }
    fun keepYouTubeOffline(source: YouTubeSource) {
        val persistedSource = youtubeSources.firstOrNull { it.id == source.id || it.url == source.url }
            ?: source.copy(reviewState = YouTubeReviewState.Accepted)
        if (persistedSource.isOfflineSaved()) {
            Toast.makeText(context, "Already saved for Offline Deck", Toast.LENGTH_SHORT).show()
            return
        }
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Turn off Offline Deck to save new PremiumDeck music", Toast.LENGTH_SHORT).show()
            return
        }
        if (persistedSource.id !in youtubeSources.map { it.id }.toSet()) {
            saveYouTubeShelf(listOf(persistedSource) + youtubeSources.filterNot { it.id == persistedSource.id || it.url == persistedSource.url })
        }
        if (persistedSource.downloadState == YouTubeDownloadState.Downloading) return
        if (persistedSource.trimSilenceOnDownload && !resolverCapabilities.ffmpegAvailable) {
            Toast.makeText(context, "Silence trimming needs an external resolver", Toast.LENGTH_LONG).show()
            return
        }
        updateYouTubeSource(persistedSource.id, persist = false) { it.copy(downloadState = YouTubeDownloadState.Downloading, downloadProgress = 1) }
        trackPremiumDeckNetworkJob(appScope.launch {
            try {
                runYouTubeOfflineDownload(
                    source = persistedSource,
                    currentProgress = {
                        youtubeSources.firstOrNull { it.id == persistedSource.id }?.downloadProgress ?: 0
                    },
                    updateSource = { persist, transform ->
                        withContext(Dispatchers.Main.immediate) {
                            updateYouTubeSource(persistedSource.id, persist, transform)
                        }
                    },
                    downloadOnDevice = { downloadSource, onProgress ->
                        downloadYouTubeToMusicOnDevice(context, downloadSource, onProgress)
                    },
                    startResolverDownload = ::startResolverDownload,
                    getResolverDownloadStatus = ::getResolverDownloadStatus,
                    saveResolverDownload = { job, downloadSource ->
                        saveResolverDownloadToMusic(context, job, downloadSource)
                    },
                    notify = { message, long ->
                        withContext(Dispatchers.Main.immediate) {
                            Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            } catch (cancellation: CancellationException) {
                withContext(Dispatchers.Main.immediate) {
                    updateYouTubeSource(persistedSource.id, persist = false) {
                        if (it.downloadState == YouTubeDownloadState.Downloading) {
                            it.copy(downloadState = YouTubeDownloadState.None, downloadProgress = 0)
                        } else {
                            it
                        }
                    }
                }
                throw cancellation
            }
        })
    }
    fun startFreeLegalAlbumDownload(release: AlbumDownloadRelease) {
        if (release.tracks.none { it.title.isNotBlank() }) {
            Toast.makeText(context, "This release has no tracklist to download", Toast.LENGTH_LONG).show()
            return
        }
        if (release.tracks.none { it.downloadAllowed && it.downloadUrl.isNotBlank() }) {
            Toast.makeText(context, "No legal downloadable FLAC files were found for this result", Toast.LENGTH_LONG).show()
            return
        }
        val jobId = "album-${release.id}-${System.currentTimeMillis()}"
        val initialJob = AlbumAudioDownloadJob(
            id = jobId,
            releaseId = release.id,
            title = release.title,
            artist = release.artist,
            provider = release.source,
            quality = release.downloadQuality.ifBlank { "Best available" },
            status = AlbumAudioDownloadStatus.Queued,
            message = "Queued free legal album download",
        )
        saveAlbumAudioDownloadJobShelf((listOf(initialJob) + albumAudioDownloadJobs.filterNot { it.releaseId == release.id }).take(25))
        Toast.makeText(context, "Saving free legal album locally", Toast.LENGTH_SHORT).show()
        appScope.launch {
            val saved = downloadFreeLegalAlbumToMusic(context, release) { progress ->
                saveAlbumAudioDownloadJobShelf(
                    albumAudioDownloadJobs.map { job ->
                        if (job.id == jobId) job.copy(status = AlbumAudioDownloadStatus.Downloading, progress = progress, message = "Saving ${release.downloadQuality.ifBlank { "audio" }} files locally")
                        else job
                    },
                )
            }
            val total = release.tracks.count { it.downloadAllowed && it.downloadUrl.isNotBlank() }
            val nextJob = if (saved > 0) {
                initialJob.copy(
                    status = AlbumAudioDownloadStatus.Downloaded,
                    progress = 100,
                    message = "Saved $saved of $total tracks locally",
                )
            } else {
                initialJob.copy(
                    status = AlbumAudioDownloadStatus.Failed,
                    progress = 0,
                    message = "Could not save the legal album files",
                )
            }
            saveAlbumAudioDownloadJobShelf((listOf(nextJob) + albumAudioDownloadJobs.filterNot { it.id == jobId || it.releaseId == release.id }).take(25))
            Toast.makeText(context, nextJob.message, Toast.LENGTH_LONG).show()
        }
    }

    val libraryTracks = scannedTracks
    LaunchedEffect(libraryTracks) {
        if (libraryTracks.isEmpty()) {
            localSearchIndex = LocalLibrarySearchIndex.Empty
            localSearchResults = emptyList()
            return@LaunchedEffect
        }
        val indexResult = withContext(Dispatchers.Default) {
            val startedAt = SystemClock.uptimeMillis()
            LocalLibrarySearchIndex.build(libraryTracks)
                .let { index -> Triple(index, SystemClock.uptimeMillis() - startedAt, Thread.currentThread().name) }
        }
        val (nextIndex, durationMillis, workerThread) = indexResult
        localSearchIndex = nextIndex
        PulseDeckLibraryDiagnostics.searchIndexBuild(nextIndex.trackCount, durationMillis, workerThread)
    }
    LaunchedEffect(searchQuery, localSearchIndex) {
        if (localSearchIndex.trackCount == 0) {
            localSearchResults = emptyList()
            return@LaunchedEffect
        }
        localSearchGeneration += 1L
        val generation = localSearchGeneration
        val query = searchQuery
        val debounceMillis = if (query.trim().isBlank()) 0L else 160L
        try {
            if (debounceMillis > 0L) delay(debounceMillis)
            val searchResult = withContext(Dispatchers.Default) {
                val startedAt = SystemClock.uptimeMillis()
                val results = localSearchIndex.search(query)
                Triple(results, SystemClock.uptimeMillis() - startedAt, Thread.currentThread().name)
            }
            val (results, durationMillis, workerThread) = searchResult
            if (generation == localSearchGeneration) {
                localSearchResults = results
                PulseDeckLibraryDiagnostics.search(
                    query = query,
                    debounceMillis = debounceMillis,
                    durationMillis = durationMillis,
                    resultCount = results.size,
                    workerThread = workerThread,
                )
            } else {
                PulseDeckLibraryDiagnostics.search(
                    query = query,
                    debounceMillis = debounceMillis,
                    durationMillis = durationMillis,
                    resultCount = results.size,
                    workerThread = workerThread,
                    staleIgnored = true,
                )
            }
        } catch (exception: CancellationException) {
            PulseDeckLibraryDiagnostics.searchCancelled(query, debounceMillis)
            throw exception
        }
    }
    val personalizationStore = remember(context) { RoomUserPreferenceStore(context) }
    val modelAssetManager = remember(context) { ModelAssetManager(context) }
    val tinyRecRunner = remember(context) { TinyRecLiteRtRunner(context, modelAssetManager) }
    var tinyRecModelHealth by remember { mutableStateOf(tinyRecRunner.health) }
    var premiumDeckModelStatus by remember {
        mutableStateOf(describeTinyRecModelStatus(tinyRecModelHealth, manifest = null, modelAvailable = false))
    }
    DisposableEffect(tinyRecRunner) {
        onDispose {
            tinyRecRunner.close()
        }
    }
    val localPersonalizationProvider = remember(libraryTracks, likedTrackKeys, dislikedTrackKeys, bookmarkedTrackKeys, playlistTrackKeys, trackPlayCounts) {
        LocalLibraryCandidateProvider {
            libraryTracks.map { track ->
                val key = track.stableKey()
                track.toPersonalizationCandidate(
                    liked = key in likedTrackKeys,
                    disliked = key in dislikedTrackKeys,
                    bookmarked = key in bookmarkedTrackKeys,
                    inPlaylist = key in playlistTrackKeys,
                    playCount = trackPlayCounts[key] ?: 0,
                )
            }
        }
    }
    val premiumDeckCandidateSignature = remember(youtubeSources, streamDiscoverySnapshot) {
        (youtubeSources + streamDiscoverySnapshot.results.map { it.source })
            .distinctBy { it.streamDistinctKey() }
            .joinToString("||") { it.premiumDeckCandidateFingerprint() }
    }
    val premiumDeckPersonalizationProvider = remember(premiumDeckCandidateSignature) {
        val candidateSnapshot = (youtubeSources + streamDiscoverySnapshot.results.map { it.source })
            .distinctBy { it.streamDistinctKey() }
            .map { it.toPersonalizationCandidate() }
        PremiumDeckCandidateProvider {
            candidateSnapshot
        }
    }
    val personalizationRepository = remember(localPersonalizationProvider, premiumDeckPersonalizationProvider, personalizationStore, tinyRecRunner) {
        PremiumDeckPersonalizationRepository(
            localCandidateProvider = localPersonalizationProvider,
            premiumDeckCandidateProvider = premiumDeckPersonalizationProvider,
            store = personalizationStore,
            tinyRecRunner = tinyRecRunner,
        )
    }
    LaunchedEffect(modelAssetManager, tinyRecModelHealth) {
        val (manifest, modelAvailable) = withContext(Dispatchers.IO) {
            modelAssetManager.preferredModelManifest() to (modelAssetManager.preferredModelLocation() != null)
        }
        premiumDeckModelStatus = describeTinyRecModelStatus(
            health = tinyRecModelHealth,
            manifest = manifest,
            modelAvailable = modelAvailable,
        )
    }
    var personalizationMixes by remember { mutableStateOf<List<StreamCollection>>(emptyList()) }
    var personalizationProfile by remember { mutableStateOf(UserPreferenceProfile()) }
    val personalizationSettings = remember(pulseSettingsState.misc.online) {
        val smartMode = pulseSettingsState.misc.online.smartRecommendationMode
        PersonalizationSettings(
            enabled = pulseSettingsState.misc.online.premiumDeckPersonalization && smartMode != PremiumDeckSmartRecommendationMode.Off,
            includePremiumDeckCandidates = true,
            useDownloadedModelWhenAvailable = smartMode == PremiumDeckSmartRecommendationMode.Smart,
            offlineMode = premiumDeckOfflineActive,
        )
    }
    LaunchedEffect(screen, tinyRecRunner) {
        if (screen != Screen.YouTube && tinyRecRunner.health == TinyRecModelHealth.MODEL_READY) {
            AlbumArtworkRuntime.onPremiumDeckNativeState(
                reason = "tinyrec_close_before_hidden_route",
                health = tinyRecRunner.health.name,
                productionReady = tinyRecRunner.productionReady,
            )
            withContext(Dispatchers.IO) { tinyRecRunner.close() }
            tinyRecModelHealth = tinyRecRunner.health
            AlbumArtworkRuntime.onPremiumDeckNativeState(
                reason = "tinyrec_close_after_hidden_route",
                health = tinyRecRunner.health.name,
                productionReady = tinyRecRunner.productionReady,
            )
        }
    }
    LaunchedEffect(startupPersistenceLoaded, screen, personalizationRepository, personalizationSettings, streamMixSessionSeed) {
        if (!startupPersistenceLoaded || screen != Screen.YouTube) return@LaunchedEffect
        val profile = withContext(Dispatchers.IO) { personalizationRepository.currentProfile() }
        val mixes = withContext(Dispatchers.Default) {
            personalizationRepository.mixes(personalizationSettings, limit = 6)
                .map { it.toStreamCollection() }
        }
        personalizationProfile = profile
        personalizationMixes = mixes
    }
    LaunchedEffect(
        startupPersistenceLoaded,
        screen,
        youtubeSources,
        streamPlayHistory.firstOrNull()?.playedAtMillis,
        followedStreamArtists,
        albumDownloadDrafts,
        personalizationProfile,
        recommendedAlbumsSnapshot?.refreshAfterEpochMs,
        premiumDeckOfflineActive,
        pulseSettingsState.misc.online,
    ) {
        if (!startupPersistenceLoaded || screen != Screen.YouTube) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val streamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
        val recentlyShownIds = recommendedAlbumsSnapshot
            ?.takeIf { now - it.generatedAtEpochMs <= 30L * 24L * 60L * 60L * 1000L }
            ?.albums
            ?.flatMap { listOf(it.stableAlbumId, albumRecommendationKey(it.artistName, it.title)) }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        val recommendationContext = buildAlbumRecommendationContext(
            sources = youtubeSources,
            playHistory = streamPlayHistory,
            followedArtists = followedStreamArtists,
            albumDrafts = albumDownloadDrafts,
            personalizationProfile = personalizationProfile,
            streamPolicy = streamPolicy,
            offlineDeckActive = premiumDeckOfflineActive,
            betaUnlocked = true,
            nowEpochMs = now,
        ).copy(recentlyShownAlbumIds = recentlyShownIds)
        if (!recommendedAlbumsSnapshot.shouldRefreshRecommendedAlbums(recommendationContext, sectionVisible = true)) {
            return@LaunchedEffect
        }
        val refreshed = runCatching {
            recommendedAlbumRefreshCoordinator.refresh(this) {
                withContext(Dispatchers.IO) {
                    refreshRecommendedAlbumsSnapshot(recommendationContext, recommendedAlbumsSnapshot)
                }
            }
        }.getOrNull() ?: return@LaunchedEffect
        if (screen != Screen.YouTube || PulseOnlineRuntime.settings.premiumDeckOfflineActive) return@LaunchedEffect
        if (refreshed.albums.isNotEmpty()) {
            recommendedAlbumsSnapshot = refreshed
            saveRecommendedAlbumsSnapshot(context, refreshed)
        }
    }
    fun logPersonalization(event: BehaviorEvent) {
        if (!personalizationSettings.enabled) return
        appScope.launch {
            val profile = withContext(Dispatchers.IO) {
                personalizationRepository.log(event)
                personalizationRepository.currentProfile()
            }
            personalizationProfile = profile
        }
    }
    fun setFullPlayerStreamLiked(source: YouTubeSource, liked: Boolean) {
        val persisted = ensureFullPlayerSaveSource(source)
        val nextReaction = if (liked) YouTubeReaction.Liked else YouTubeReaction.Neutral
        updateYouTubeSource(persisted.id) {
            it.copy(
                reaction = nextReaction,
                bookmarked = if (liked) true else it.bookmarked,
            )
        }
        if (liked) {
            logPersonalization(persisted.toBehaviorEvent(BehaviorEventType.LikeFavorite))
            refreshStreamDiscovery(force = false)
        }
    }
    fun toggleFullPlayerStreamPlaylist(source: YouTubeSource, playlistId: String) {
        val playlist = youtubePlaylists.firstOrNull { it.id == playlistId } ?: return
        val persisted = ensureFullPlayerSaveSource(source)
        val identityKeys = (persisted.streamIdentityKeys() + source.streamIdentityKeys()).toSet()
        val alreadySaved = playlist.sourceIds.any { it in identityKeys }
        val nextIds = if (alreadySaved) {
            playlist.sourceIds.filterNot { it in identityKeys }
        } else {
            streamPlaylistSourceIdsAfterAdd(playlist, persisted)
        }
        updateYouTubePlaylist(playlist.id) { it.copy(sourceIds = nextIds, updatedMillis = System.currentTimeMillis()) }
        logPersonalization(
            persisted.toBehaviorEvent(
                if (alreadySaved) BehaviorEventType.TrackRemovedFromPlaylist else BehaviorEventType.TrackAddedToPlaylist,
                metadata = mapOf("playlist" to playlist.title),
            ),
        )
    }
    LaunchedEffect(pendingFollowedArtistEvent, personalizationSettings.enabled, personalizationRepository) {
        val event = pendingFollowedArtistEvent ?: return@LaunchedEffect
        if (personalizationSettings.enabled) {
            val profile = withContext(Dispatchers.IO) {
                personalizationRepository.log(event)
                personalizationRepository.currentProfile()
            }
            personalizationProfile = profile
        }
        pendingFollowedArtistEvent = null
    }
    LaunchedEffect(searchQuery, personalizationSettings.enabled) {
        if (!personalizationSettings.enabled) return@LaunchedEffect
        val localNeedle = searchQuery.trim()
        if (localNeedle.length < 2) return@LaunchedEffect
        val event = BehaviorEvent(
            type = BehaviorEventType.SearchQuerySubmitted,
            query = localNeedle,
            source = CandidateSource.LocalLibrary,
        )
        delay(650L)
        withContext(Dispatchers.IO) {
            personalizationRepository.log(event)
        }
    }
    var librarySummarySnapshot by remember { mutableStateOf(LibrarySummarySnapshot()) }
    LaunchedEffect(libraryTracks, playlistTrackKeys, bookmarkedTrackKeys, trackPlayCounts) {
        val snapshot = withContext(Dispatchers.Default) {
            buildLibrarySummarySnapshot(
                tracks = libraryTracks,
                playlistTrackKeys = playlistTrackKeys,
                bookmarkedTrackKeys = bookmarkedTrackKeys,
                trackPlayCounts = trackPlayCounts,
            )
        }
        librarySummarySnapshot = snapshot
    }
    val libraryAlbums = librarySummarySnapshot.albums
    val libraryAlbumByKey = librarySummarySnapshot.albumByKey
    fun needsLibraryGroup(kind: LocalLibraryGroupKind): Boolean =
        selectedLibraryGroupKind == kind || when (kind) {
            LocalLibraryGroupKind.Folders -> screen == Screen.Folders
            LocalLibraryGroupKind.AlbumArtists -> screen == Screen.AlbumArtists
            LocalLibraryGroupKind.Genres -> screen == Screen.Genres
            LocalLibraryGroupKind.Years -> screen == Screen.Years
            LocalLibraryGroupKind.Composers -> screen == Screen.Composers
        }
    val libraryArtists = remember(libraryTracks, screen) {
        if (screen == Screen.Artists) artistsFor(libraryTracks) else emptyList()
    }
    val folderGroups = remember(libraryTracks, screen, selectedLibraryGroupKind) {
        if (needsLibraryGroup(LocalLibraryGroupKind.Folders)) localLibraryGroups(LocalLibraryGroupKind.Folders, libraryTracks) else emptyList()
    }
    val albumArtistGroups = remember(libraryTracks, screen, selectedLibraryGroupKind) {
        if (needsLibraryGroup(LocalLibraryGroupKind.AlbumArtists)) localLibraryGroups(LocalLibraryGroupKind.AlbumArtists, libraryTracks) else emptyList()
    }
    val genreGroups = remember(libraryTracks, screen, selectedLibraryGroupKind) {
        if (needsLibraryGroup(LocalLibraryGroupKind.Genres)) localLibraryGroups(LocalLibraryGroupKind.Genres, libraryTracks) else emptyList()
    }
    val yearGroups = remember(libraryTracks, screen, selectedLibraryGroupKind) {
        if (needsLibraryGroup(LocalLibraryGroupKind.Years)) localLibraryGroups(LocalLibraryGroupKind.Years, libraryTracks) else emptyList()
    }
    val composerGroups = remember(libraryTracks, screen, selectedLibraryGroupKind) {
        if (needsLibraryGroup(LocalLibraryGroupKind.Composers)) localLibraryGroups(LocalLibraryGroupKind.Composers, libraryTracks) else emptyList()
    }
    val localPlaylistTracks = remember(libraryTracks, playlistTrackKeys, screen) {
        if (screen == Screen.LocalPlaylists) localFilteredTracks(LocalTrackFilterKind.Playlists, libraryTracks, playlistKeys = playlistTrackKeys) else emptyList()
    }
    val bookmarkedTracks = remember(libraryTracks, bookmarkedTrackKeys, screen) {
        if (screen == Screen.Bookmarks) localFilteredTracks(LocalTrackFilterKind.Bookmarks, libraryTracks, bookmarkedKeys = bookmarkedTrackKeys) else emptyList()
    }
    val mostPlayedTracks = remember(libraryTracks, trackPlayCounts, screen) {
        if (screen == Screen.MostPlayed) localFilteredTracks(LocalTrackFilterKind.MostPlayed, libraryTracks, playCounts = trackPlayCounts) else emptyList()
    }
    val selectedLibraryGroupTracks = remember(libraryTracks, selectedLibraryGroupKind, selectedLibraryGroupKey, screen) {
        if (screen == Screen.LibraryGroupTracks) {
            selectedLibraryGroupKind?.let { localLibraryGroupTracks(it, selectedLibraryGroupKey, libraryTracks) }.orEmpty()
        } else {
            emptyList()
        }
    }
    val selectedLibraryGroup = remember(selectedLibraryGroupKind, selectedLibraryGroupKey, folderGroups, albumArtistGroups, genreGroups, yearGroups, composerGroups) {
        val groups = when (selectedLibraryGroupKind) {
            LocalLibraryGroupKind.Folders -> folderGroups
            LocalLibraryGroupKind.AlbumArtists -> albumArtistGroups
            LocalLibraryGroupKind.Genres -> genreGroups
            LocalLibraryGroupKind.Years -> yearGroups
            LocalLibraryGroupKind.Composers -> composerGroups
            null -> emptyList()
        }
        groups.firstOrNull { it.key == selectedLibraryGroupKey }
    }
    val libraryGroupCounts = librarySummarySnapshot.groupCounts
    val libraryArtistCount = librarySummarySnapshot.artistCount
    val localPlaylistTrackCount = librarySummarySnapshot.localPlaylistTrackCount
    val bookmarkedTrackCount = librarySummarySnapshot.bookmarkedTrackCount
    val mostPlayedTrackCount = librarySummarySnapshot.mostPlayedTrackCount
    val libraryCategoryMetas = remember(
        libraryTracks.size,
        youtubeSources,
        librarySummarySnapshot,
        libraryGroupCounts,
        libraryArtistCount,
        localPlaylistTrackCount,
        bookmarkedTrackCount,
        mostPlayedTrackCount,
        radioStations,
        radioCountryCode,
    ) {
        mapOf(
            "All Songs" to libraryTracks.size.toString(),
            PREMIUMDECK_SOURCE_CATEGORY to youtubeSources.size.toString(),
            "PulseRadio" to (radioStations.size.takeIf { it > 0 }?.toString() ?: radioCountryCode.uppercase(Locale.US)),
            "Folders" to (libraryGroupCounts[LocalLibraryGroupKind.Folders] ?: 0).toString(),
            "Folders Hierarchy" to (libraryGroupCounts[LocalLibraryGroupKind.Folders] ?: 0).toString(),
            "Albums" to libraryAlbums.size.toString(),
            "Artists" to libraryArtistCount.toString(),
            "Album Artists" to (libraryGroupCounts[LocalLibraryGroupKind.AlbumArtists] ?: 0).toString(),
            "Genres" to (libraryGroupCounts[LocalLibraryGroupKind.Genres] ?: 0).toString(),
            "Years" to (libraryGroupCounts[LocalLibraryGroupKind.Years] ?: 0).toString(),
            "Composers" to (libraryGroupCounts[LocalLibraryGroupKind.Composers] ?: 0).toString(),
            "Playlists" to localPlaylistTrackCount.toString(),
            "Bookmarks" to bookmarkedTrackCount.toString(),
            "Most Played" to mostPlayedTrackCount.toString(),
        )
    }
    val albumGridState = rememberLazyGridState()
    val libraryListState = rememberLazyListState()
    val allSongsListState = rememberLazyListState()
    val foldersListState = rememberLazyListState()
    val albumArtistsListState = rememberLazyListState()
    val genresListState = rememberLazyListState()
    val yearsListState = rememberLazyListState()
    val composersListState = rememberLazyListState()
    val localPlaylistsListState = rememberLazyListState()
    val bookmarksListState = rememberLazyListState()
    val mostPlayedListState = rememberLazyListState()
    val artistsListState = rememberLazyListState()
    val libraryGroupTrackListStates = remember { mutableMapOf<String, LazyListState>() }
    val selectedLibraryGroupTracksListState = remember(selectedLibraryGroupKind, selectedLibraryGroupKey) {
        val stateKey = "${selectedLibraryGroupKind?.name.orEmpty()}:$selectedLibraryGroupKey"
        libraryGroupTrackListStates.getOrPut(stateKey) { LazyListState() }
    }
    val folderHierarchyListStates = remember { mutableMapOf<String, LazyListState>() }
    val folderHierarchyListState = remember(selectedFolderPath) {
        folderHierarchyListStates.getOrPut(selectedFolderPath) { LazyListState() }
    }
    val selectedAlbum = remember(libraryAlbums, selectedAlbumKey) { libraryAlbums.firstOrNull { it.key == selectedAlbumKey } }
    val albumDetailListState = remember(selectedAlbumKey) { LazyListState() }
    var albumTileBoundsByKey by remember { mutableStateOf<Map<String, AlbumTileBounds>>(emptyMap()) }
    var albumReturnTargetBounds by remember { mutableStateOf<AlbumTileBounds?>(null) }
    var albumReturnTargetKey by remember { mutableStateOf<String?>(null) }
    var albumReturnFrozenKey by remember { mutableStateOf<String?>(null) }
    var albumDetailPlaybackAnchorKey by remember { mutableStateOf<String?>(null) }
    var albumDetailCloseStartProgress by remember { mutableFloatStateOf(0f) }
    var albumDetailCloseRetargetJob by remember { mutableStateOf<Job?>(null) }
    var categoryBoundsByName by remember { mutableStateOf<Map<String, AlbumTileBounds>>(emptyMap()) }
    var categoryHeaderBoundsByName by remember { mutableStateOf<Map<String, AlbumTileBounds>>(emptyMap()) }
    var categoryMotionNonce by remember { mutableIntStateOf(0) }
    var categoryMotionRequest by remember { mutableStateOf<CategoryMotionRequest?>(null) }
    var activeCategoryMotion by remember { mutableStateOf<CategorySharedMotion?>(null) }
    var routeMotionInFlight by remember { mutableStateOf(false) }
    var playerSceneMotionInFlight by remember { mutableStateOf(false) }
    var playerCloseInFlight by remember { mutableStateOf(false) }
    var albumReturnPreparing by remember { mutableStateOf(false) }
    var playerLaunchHandoff by remember { mutableStateOf<PlayerLaunchHandoff?>(null) }
    var albumTrackLaunchHandoffs by remember { mutableStateOf<Map<String, PlayerLaunchHandoff>>(emptyMap()) }
    fun updateAlbumTileBounds(albumKey: String, bounds: AlbumTileBounds) {
        if (albumTileBoundsByKey[albumKey] != bounds) {
            albumTileBoundsByKey = albumTileBoundsByKey + (albumKey to bounds)
        }
        val returnKey = albumReturnFrozenKey ?: albumReturnTargetKey ?: selectedAlbumKey
        if (albumKey == returnKey && albumReturnTargetBounds != bounds) {
            albumReturnTargetBounds = bounds
        }
    }
    fun updateCategoryBounds(categoryName: String, bounds: AlbumTileBounds) {
        if (!LibraryCategorySharedHandoffEnabled) return
        if (categoryBoundsByName[categoryName] != bounds) {
            categoryBoundsByName = categoryBoundsByName + (categoryName to bounds)
        }
    }
    fun updateCategoryHeaderBounds(categoryName: String, bounds: AlbumTileBounds) {
        if (!LibraryCategorySharedHandoffEnabled) return
        if (categoryHeaderBoundsByName[categoryName] != bounds) {
            categoryHeaderBoundsByName = categoryHeaderBoundsByName + (categoryName to bounds)
        }
    }
    fun updateAlbumTrackLaunchHandoff(handoff: PlayerLaunchHandoff) {
        if (albumTrackLaunchHandoffs[handoff.trackKey] != handoff) {
            albumTrackLaunchHandoffs = albumTrackLaunchHandoffs + (handoff.trackKey to handoff)
        }
    }
    fun requestCategoryMotion(categoryName: String, fromBounds: AlbumTileBounds, direction: CategoryMotionDirection, startProgress: Float = 0f) {
        if (!LibraryCategorySharedHandoffEnabled) return
        categoryMotionNonce += 1
        categoryMotionRequest = CategoryMotionRequest(categoryName, fromBounds, direction, categoryMotionNonce, startProgress.coerceIn(0f, 1f))
    }
    val scopedPlaybackTracks = remember(libraryTracks, activeAlbumKey) {
        activeAlbumKey?.let { key -> libraryTracks.filter { it.album.key == key }.takeIf { albumTracks -> albumTracks.isNotEmpty() } } ?: libraryTracks
    }
    fun Track.withCanonicalAlbum(): Track =
        (activeAlbumKey?.let { libraryAlbumByKey[it] } ?: libraryAlbumByKey[album.key])?.let { canonicalAlbum ->
            if (canonicalAlbum == album) this else copy(album = canonicalAlbum)
        } ?: this
    fun trackKeyOf(track: Track): String = track.stableKey()
    fun currentLocalTrackOrNull(): Track? =
        localTrackAt(libraryTracks, trackIndex)
    fun currentTrackOrNull(): Track? = activeStreamTrack ?: currentLocalTrackOrNull()
    fun currentTrack(): Track = currentTrackOrNull() ?: demoTracks.first()
    fun localPlaybackSession(): LocalPlaybackSession =
        LocalPlaybackSession(
            tracks = libraryTracks,
            albums = libraryAlbums,
            activeAlbumKey = activeAlbumKey,
            currentTrack = currentTrackOrNull(),
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            playbackHistory = playbackHistory,
            queueContext = playbackQueueContext,
        )
    fun trackIndexFor(target: Track): Int =
        localTrackIndexFor(libraryTracks, target)
    LaunchedEffect(startupPersistenceLoaded, permissionGranted, localLibraryLoaded, libraryTracks.size, libraryTracks.firstOrNull()?.stableKey(), youtubeSources.size, pulseSettingsState.misc.online) {
        if (!startupPersistenceLoaded || lastPlaybackRestored || !localLibraryLoaded) return@LaunchedEffect
        val saved = startupPlaybackState
        if (saved == null) {
            lastPlaybackRestored = true
            return@LaunchedEffect
        }
        if (saved?.kind != "youtube" && !permissionGranted && scannedTracks.isEmpty()) return@LaunchedEffect
        if (saved.kind != "youtube" && libraryTracks.isEmpty()) {
            lastPlaybackRestored = true
            return@LaunchedEffect
        }
        var restored = false
        val restoreStreamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
        if (saved?.shouldRepairToRecentYouTube(libraryTracks, youtubeSources) == true) {
            val recentYouTubeRestore = youtubeSources
                .filter { it.lastPlayedMillis > 0L }
                .sortedByDescending { it.lastPlayedMillis }
                .firstNotNullOfOrNull { source -> source.toRestoredTrack(restoreStreamPolicy)?.let { source to it } }
            if (recentYouTubeRestore != null) {
                val (source, restoredTrack) = recentYouTubeRestore
                activeStreamTrack = restoredTrack
                activeYouTubeSourceId = source.id
                activeYouTubeQueueIds = listOf(source.id)
                albumTrackFallbackQueueIds = emptyMap()
                activeAlbumKey = null
                playbackQueueContext = PlaybackQueueContext.PremiumDeck(source.id, null)
                playbackState.selectTrackIndex(trackIndex, 1)
                resetDisplayedPlaybackPosition(requestSeek = true)
                playing = false
                saveLastPlaybackState(context, LastPlaybackState(kind = "youtube", youtubeSourceId = source.id))
                restored = true
            }
        }
        if (!restored) {
            when (saved.kind) {
                "youtube" -> {
                    val source = youtubeSources.firstOrNull { it.id == saved.youtubeSourceId }
                    val restoredTrack = source?.toRestoredTrack(restoreStreamPolicy)
                    if (source != null && restoredTrack != null) {
                        activeStreamTrack = restoredTrack
                        activeYouTubeSourceId = source.id
                        activeYouTubeQueueIds = listOf(source.id)
                        albumTrackFallbackQueueIds = emptyMap()
                        activeAlbumKey = null
                        playbackQueueContext = PlaybackQueueContext.PremiumDeck(source.id, null)
                        playbackState.selectTrackIndex(trackIndex, 1)
                        restored = true
                    }
                }
                "radio" -> {
                    val restoredTrack = saved.toRadioTrack()
                    if (restoredTrack != null) {
                        activeStreamTrack = restoredTrack
                        activeYouTubeSourceId = null
                        activeYouTubeQueueIds = emptyList()
                        albumTrackFallbackQueueIds = emptyMap()
                        activeAlbumKey = null
                        playbackQueueContext = PlaybackQueueContext.Radio(restoredTrack.displayName)
                        playbackState.selectTrackIndex(trackIndex, 1)
                        restored = true
                    }
                }
                else -> {
                    val restoredIndex = findLastPlaybackTrackIndex(libraryTracks, saved)
                    if (restoredIndex >= 0) {
                        activeStreamTrack = null
                        activeYouTubeSourceId = null
                        activeYouTubeQueueIds = emptyList()
                        albumTrackFallbackQueueIds = emptyMap()
                        activeAlbumKey = null
                        playbackQueueContext = PlaybackQueueContext.AllSongs
                        playbackState.selectTrackIndex(restoredIndex, 1)
                        restored = true
                    }
                }
            }
            if (restored) {
                requestPlaybackSeek(saved.positionMillis.coerceAtLeast(0L))
                playing = false
            }
        }
        lastPlaybackRestored = true
    }
    LaunchedEffect(
        lastPlaybackRestored,
        localLibraryLoaded,
        permissionGranted,
        scannedTracks.size,
        activeStreamTrack?.stableKey(),
        pulseSettingsState.playerUi.startAtPlayerWhenRestored,
        pulseSettingsState.lookAndFeel.startAtLibrary,
    ) {
        val playableLocalLibrary = scannedTracks.isNotEmpty()
        val playableRestoredStream = activeStreamTrack != null
        if (
            !playerAutoOpened &&
            lastPlaybackRestored &&
            localLibraryLoaded &&
            pulseSettingsState.playerUi.startAtPlayerWhenRestored &&
            !pulseSettingsState.lookAndFeel.startAtLibrary &&
            (playableLocalLibrary || playableRestoredStream)
        ) {
            playerAutoOpened = true
            playbackState.openPlayer(fromMini = false)
            playbackState.markPlayableTrack()
        }
    }
    fun clearPlaybackHistory() {
        playbackState.clearPlaybackHistory()
    }
    fun clearPremiumDeckRuntimeContext() {
        activePremiumDeckSession = null
        premiumDeckPrepareStates = emptyMap()
        premiumDeckPrepareGeneration += 1L
    }
    fun setAllSongsQueueContext() {
        playbackQueueContext = PlaybackQueueContext.AllSongs
    }
    fun setLocalAlbumQueueContext(album: Album) {
        playbackQueueContext = PlaybackQueueContext.LocalAlbum(
            albumId = album.key,
            albumTitle = album.title,
            artist = album.artist,
        )
    }
    fun setPremiumDeckQueueContext(sourceId: String?) {
        val session = activePremiumDeckSession
        playbackQueueContext = PlaybackQueueContext.PremiumDeck(
            sourceId = sourceId,
            queryId = session?.originKey?.takeIf { it.isNotBlank() }
                ?: session?.searchQuery?.takeIf { it.isNotBlank() },
        )
    }
    fun setRadioQueueContext(stationId: String?) {
        playbackQueueContext = PlaybackQueueContext.Radio(stationId)
    }
    fun incrementLocalPlayCount(trackKey: String) {
        if (trackKey.isBlank()) return
        trackPlayCounts = (trackPlayCounts + (trackKey to ((trackPlayCounts[trackKey] ?: 0) + 1)))
            .also { localLibraryRepository.savePlayCounts(it) }
    }
    fun playResolvedTrack(target: Track, direction: Int = 1, rememberHistory: Boolean = true) {
        resolvingYouTubeSourceId?.let { pendingSourceId ->
            youtubeResolveGeneration += 1L
            resolvingYouTubeSourceId = null
            activeYouTubeResolveSessionId = 0L
            premiumDeckStreamResolveJob.getAndSet(null)?.cancel()
            premiumDeckPrepareStates = premiumDeckPrepareStates - pendingSourceId
        }
        activeStreamTrack = null
        activeYouTubeSourceId = null
        activeYouTubeQueueIds = emptyList()
        albumTrackFallbackQueueIds = emptyMap()
        clearPremiumDeckRuntimeContext()
        if (activeAlbumKey == null && playbackQueueContext !is PlaybackQueueContext.UserQueue) {
            setAllSongsQueueContext()
        }
        val current = currentTrackOrNull()
        val currentKey = current?.let(::trackKeyOf)
        val targetKey = trackKeyOf(target)
        incrementLocalPlayCount(targetKey)
        logPersonalization(
            target.toBehaviorEvent(
                BehaviorEventType.TrackStarted,
                metadata = mapOf(
                    "repeatMode" to repeatMode.name,
                    "shuffleMode" to shuffleMode.name,
                    "source" to "local_library",
                ),
            ),
        )
        if (repeatMode == PlaybackRepeatMode.RepeatSong && targetKey == currentKey) {
            logPersonalization(target.toBehaviorEvent(BehaviorEventType.RepeatPlay))
        }
        if (shuffleMode != ShuffleMode.Off) {
            logPersonalization(target.toBehaviorEvent(BehaviorEventType.ShufflePlay))
        }
        if (targetKey == currentKey) {
            resetDisplayedPlaybackPosition(requestSeek = true)
            saveLastPlaybackState(context, target.toLastPlaybackState(currentPlaybackPositionMillis()))
            return
        }
        if (rememberHistory && currentKey != null) playbackState.rememberPlaybackHistory(currentKey)
        playbackState.selectTrackIndex(trackIndexFor(target), direction)
        resetDisplayedPlaybackPosition()
        saveLastPlaybackState(context, target.toLastPlaybackState(0L))
    }
    fun resolveNextTrack(autoEnded: Boolean): Track? =
        resolveNextLocalTrack(localPlaybackSession(), autoEnded)
    var playYouTubeSourceFromQueue: ((YouTubeSource, List<String>) -> Unit)? = null
    var streamRecoveryAttempts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    fun queueYouTubeSource(id: String?): YouTubeSource? =
        id?.let { sourceId ->
            youtubeSources.firstOrNull { it.id == sourceId }
                ?: transientYouTubeQueueSources.firstOrNull { it.id == sourceId }
                ?: premiumDeckPreparedPreviewSources[sourceId]
        }
    fun preparedPreviewSourceFor(source: YouTubeSource): YouTubeSource? {
        val sourceKey = source.streamDistinctKey()
        return premiumDeckPreparedPreviewSources[source.id]
            ?: premiumDeckPreparedPreviewSources.values.firstOrNull { prepared ->
                prepared.url == source.url || prepared.streamDistinctKey() == sourceKey
            }
    }
    fun rememberPreparedPreviewSource(source: YouTubeSource) {
        val sourceKey = source.streamDistinctKey()
        val retained = premiumDeckPreparedPreviewSources.values
            .filterNot { prepared ->
                prepared.id == source.id ||
                    prepared.url == source.url ||
                    prepared.streamDistinctKey() == sourceKey
            }
        premiumDeckPreparedPreviewSources = (listOf(source) + retained).take(18).associateBy { it.id }
    }
    fun premiumDeckSessionTitle(origin: PremiumDeckPlaybackOrigin): String =
        when (origin) {
            PremiumDeckPlaybackOrigin.Search -> "Playing from Search"
            PremiumDeckPlaybackOrigin.NewReleases -> "Playing from New Releases"
            PremiumDeckPlaybackOrigin.Artist -> "Playing from Artist"
            PremiumDeckPlaybackOrigin.AlbumProject -> "Playing from Album Project"
            PremiumDeckPlaybackOrigin.StreamCollection -> "Playing from PremiumDeck"
            PremiumDeckPlaybackOrigin.SearchDiscoveryPreview -> "Previewing Discovery"
            PremiumDeckPlaybackOrigin.Playlist -> "Playing from Playlist"
            PremiumDeckPlaybackOrigin.ManualSource -> "Playing from PremiumDeck"
        }
    fun premiumDeckDefaultQueueReason(
        origin: PremiumDeckPlaybackOrigin,
        query: String = "",
        subtitle: String = "",
    ): PremiumDeckQueueReason =
        when (origin) {
            PremiumDeckPlaybackOrigin.Search -> {
                val cleanQuery = query.ifBlank { subtitle }.trim()
                PremiumDeckQueueReason("Search match", if (cleanQuery.isBlank()) "matches this search" else "matches \"$cleanQuery\"")
            }
            PremiumDeckPlaybackOrigin.NewReleases -> PremiumDeckQueueReason("New Releases", "from New Releases")
            PremiumDeckPlaybackOrigin.Artist -> PremiumDeckQueueReason("Artist match", "same artist")
            PremiumDeckPlaybackOrigin.AlbumProject -> PremiumDeckQueueReason("Album project", "from this release")
            PremiumDeckPlaybackOrigin.StreamCollection -> PremiumDeckQueueReason("Pulse Intelligence", "Pulse Intelligence")
            PremiumDeckPlaybackOrigin.SearchDiscoveryPreview -> PremiumDeckQueueReason("Discovery preview", "previewing from the hook")
            PremiumDeckPlaybackOrigin.Playlist -> PremiumDeckQueueReason("Playlist", "from this list")
            PremiumDeckPlaybackOrigin.ManualSource -> PremiumDeckQueueReason("PremiumDeck", "from this list")
        }
    fun streamCollectionPlaybackOrigin(collection: StreamCollection): PremiumDeckPlaybackOrigin =
        when {
            collection.isFollowedArtistReleaseCollection() -> PremiumDeckPlaybackOrigin.NewReleases
            collection.title.normalizedSearchText().contains("new releases") -> PremiumDeckPlaybackOrigin.NewReleases
            collection.kind == StreamCollectionKind.Artist -> PremiumDeckPlaybackOrigin.Artist
            else -> PremiumDeckPlaybackOrigin.StreamCollection
        }
    fun streamCollectionQueueReason(collection: StreamCollection): PremiumDeckQueueReason =
        when {
            collection.isFollowedArtistReleaseCollection() -> PremiumDeckQueueReason("Artists You Follow", "from Artists You Follow")
            collection.title.normalizedSearchText().contains("new releases") -> PremiumDeckQueueReason("New Releases", "from New Releases")
            collection.kind == StreamCollectionKind.Artist -> PremiumDeckQueueReason("Artist match", "same artist")
            collection.kind == StreamCollectionKind.AlbumLike -> PremiumDeckQueueReason("Release match", "from this release")
            collection.kind == StreamCollectionKind.Mix -> PremiumDeckQueueReason("Pulse Intelligence", "Pulse Intelligence")
            else -> PremiumDeckQueueReason("PremiumDeck", "from this list")
        }
    fun buildPremiumDeckPlaybackSession(
        origin: PremiumDeckPlaybackOrigin,
        sources: List<YouTubeSource>,
        startedSource: YouTubeSource,
        title: String = "",
        subtitle: String = "",
        searchQuery: String = "",
        originKey: String = "",
        reason: PremiumDeckQueueReason? = null,
    ): PremiumDeckPlaybackSession {
        val sourceIds = (sources.map { it.id } + startedSource.id)
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(startedSource.id) }
        val defaultReason = reason ?: premiumDeckDefaultQueueReason(origin, searchQuery, subtitle)
        return PremiumDeckPlaybackSession(
            id = "premiumdeck-${origin.name.lowercase(Locale.US)}-${startedSource.id}-${System.currentTimeMillis()}",
            origin = origin,
            title = title.ifBlank { premiumDeckSessionTitle(origin) },
            subtitle = subtitle,
            sourceIds = sourceIds,
            startedSourceId = startedSource.id,
            searchQuery = searchQuery,
            originKey = originKey,
            reasonBySourceId = sourceIds.associateWith { defaultReason },
        )
    }
    fun syncActivePremiumDeckSessionQueue(queueIds: List<String>, fallbackReason: PremiumDeckQueueReason? = null) {
        val session = activePremiumDeckSession ?: return
        val cleanIds = queueIds.filter { it.isNotBlank() }.distinct()
        val defaultReason = fallbackReason ?: premiumDeckDefaultQueueReason(session.origin, session.searchQuery, session.subtitle)
        activePremiumDeckSession = session.copy(
            sourceIds = cleanIds,
            reasonBySourceId = cleanIds.associateWith { id -> session.reasonBySourceId[id] ?: defaultReason },
        )
        premiumDeckPrepareStates = premiumDeckPrepareStates.filterKeys { it in cleanIds }
    }
    fun setPremiumDeckPrepareState(sourceId: String, state: PremiumDeckStreamPrepareState) {
        if (sourceId.isBlank()) return
        premiumDeckPrepareStates = if (state == PremiumDeckStreamPrepareState.Idle) {
            premiumDeckPrepareStates - sourceId
        } else {
            premiumDeckPrepareStates + (sourceId to state)
        }
    }
    fun cancelActivePremiumDeckResolve(reason: String, clearPrepareState: Boolean = false) {
        val sourceId = resolvingYouTubeSourceId ?: return
        val source = queueYouTubeSource(sourceId)
        val sessionId = activeYouTubeResolveSessionId
        youtubeResolveGeneration += 1L
        resolvingYouTubeSourceId = null
        activeYouTubeResolveSessionId = 0L
        premiumDeckStreamResolveJob.getAndSet(null)?.cancel()
        if (clearPrepareState) setPremiumDeckPrepareState(sourceId, PremiumDeckStreamPrepareState.Idle)
        if (source != null && sessionId > 0L) {
            YouTubeNetworkDiagnostics.reportStreamSession(
                sessionId = sessionId,
                source = source,
                event = "cancel_requested",
                generation = youtubeResolveGeneration,
                reason = reason,
            )
        }
    }
    fun premiumDeckStreamHealthText(): String? {
        val currentId = activeYouTubeSourceId
        val queueIndex = activeYouTubeQueueIds.indexOf(currentId)
        val upcomingIds = activeYouTubeQueueIds.drop((queueIndex + 1).coerceAtLeast(0)).filter { it != currentId }
        val currentState = currentId?.let { premiumDeckPrepareStates[it] }
        if (currentState == PremiumDeckStreamPrepareState.Refreshing) return currentState.statusText
        return listOf(
            PremiumDeckStreamPrepareState.BackupReady,
            PremiumDeckStreamPrepareState.Ready,
            PremiumDeckStreamPrepareState.Preparing,
        ).firstNotNullOfOrNull { target ->
            upcomingIds.firstNotNullOfOrNull { sourceId ->
                premiumDeckPrepareStates[sourceId].takeIf { it == target }?.statusText
            }
        }
    }
    fun premiumDeckPlayerContextLine(): String? {
        val session = activePremiumDeckSession
        val health = premiumDeckStreamHealthText()
        if (session == null) return health
        return listOfNotNull(
            session.title.takeIf { it.isNotBlank() },
            session.subtitle.takeIf { it.isNotBlank() },
            health,
        ).distinct().joinToString("  |  ").takeIf { it.isNotBlank() }
    }
    fun streamQueueReasonsByTrackKey(): Map<String, PremiumDeckQueueReason> {
        val session = activePremiumDeckSession ?: return emptyMap()
        return activeYouTubeQueueIds
            .asSequence()
            .mapNotNull { queueYouTubeSource(it) }
            .associate { source ->
                source.toQueuePreviewTrack().stableKey() to (session.reasonFor(source.id)
                    ?: premiumDeckDefaultQueueReason(session.origin, session.searchQuery, session.subtitle))
            }
    }
    fun updatePreparedYouTubeSource(sourceId: String, transform: (YouTubeSource) -> YouTubeSource) {
        var shelfChanged = false
        val nextShelf = youtubeSources.map { source ->
            if (source.id == sourceId) {
                transform(source).also { if (it != source) shelfChanged = true }
            } else {
                source
            }
        }
        if (shelfChanged) saveYouTubeShelf(nextShelf)
        var transientChanged = false
        val nextTransient = transientYouTubeQueueSources.map { source ->
            if (source.id == sourceId) {
                transform(source).also { if (it != source) transientChanged = true }
            } else {
                source
            }
        }
        if (transientChanged) transientYouTubeQueueSources = nextTransient
    }
    fun cachePreparedYouTubeSource(source: YouTubeSource, resolved: YouTubeResolvedAudio) {
        val now = System.currentTimeMillis()
        rememberPreparedPreviewSource(
            source.copy(
                status = if (source.status == YouTubeSourceStatus.Downloaded) source.status else YouTubeSourceStatus.Cached,
                cachedUri = resolved.streamUrl,
                cachedMillis = now,
                durationMillis = resolved.durationMillis.takeIf { duration -> duration > 0L } ?: source.durationMillis,
                thumbnailUrl = resolved.thumbnailUrl ?: source.thumbnailUrl,
                isPodcast = resolved.isPodcast,
                chapters = resolved.chapters.ifEmpty { source.chapters },
                sponsorSegments = resolved.sponsorSegments.ifEmpty { source.sponsorSegments },
            ),
        )
        updatePreparedYouTubeSource(source.id) {
            it.copy(
                status = if (it.status == YouTubeSourceStatus.Downloaded) it.status else YouTubeSourceStatus.Cached,
                cachedUri = resolved.streamUrl,
                cachedMillis = now,
                durationMillis = resolved.durationMillis.takeIf { duration -> duration > 0L } ?: it.durationMillis,
                thumbnailUrl = resolved.thumbnailUrl ?: it.thumbnailUrl,
                isPodcast = resolved.isPodcast,
                chapters = resolved.chapters.ifEmpty { it.chapters },
                sponsorSegments = resolved.sponsorSegments.ifEmpty { it.sponsorSegments },
            )
        }
    }
    suspend fun isReadableOfflineUriOnIo(uri: Uri): Boolean =
        withContext(Dispatchers.IO) { isReadableOfflineUri(context, uri) }

    fun prepareUpcomingPremiumDeckStreams() {
        val currentId = activeYouTubeSourceId ?: return
        if (activeStreamTrack == null || activeYouTubeQueueIds.size <= 1) return
        val streamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
        if (!streamPolicy.allowPreviewPreparation) return
        val queueIndex = activeYouTubeQueueIds.indexOf(currentId)
        val upcomingIds = activeYouTubeQueueIds
            .drop(if (queueIndex >= 0) queueIndex + 1 else 0)
            .filter { it != currentId }
            .distinct()
        val upcomingSources = upcomingIds
            .mapNotNull { queueYouTubeSource(it) }
            .filter { it.kind == YouTubeSourceKind.Video && !it.isPodcast }
            .take(2)
        val activeQueueIds = activeYouTubeQueueIds.toSet()
        premiumDeckPrepareStates = premiumDeckPrepareStates.filterKeys { it in activeQueueIds }
        if (upcomingSources.isEmpty()) return
        premiumDeckPrepareGeneration += 1L
        val generation = premiumDeckPrepareGeneration
        trackPremiumDeckNetworkJob(appScope.launch {
            for (candidate in upcomingSources) {
                if (generation != premiumDeckPrepareGeneration) return@launch
                val latest = queueYouTubeSource(candidate.id) ?: candidate
                val downloadedUri = latest.downloadedUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (downloadedUri != null && isReadableOfflineUriOnIo(downloadedUri)) {
                    setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Ready)
                    continue
                }
                if (latest.freshCachedStreamUrl(policy = streamPolicy) != null) {
                    setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Ready)
                    continue
                }
                if (premiumDeckOfflineActive) {
                    setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Failed)
                    continue
                }
                setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Preparing)
                val resolved = withContext(Dispatchers.IO) {
                    runCatching { resolveYouTubeAudio(context, latest, streamPolicy) }.getOrNull()
                }
                if (generation != premiumDeckPrepareGeneration) return@launch
                if (resolved != null) {
                    cachePreparedYouTubeSource(latest, resolved)
                    setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Ready)
                    continue
                }
                val fallback = albumTrackFallbackQueueIds[latest.id]
                    .orEmpty()
                    .firstNotNullOfOrNull { fallbackId -> queueYouTubeSource(fallbackId) }
                if (fallback != null && !premiumDeckOfflineActive) {
                    val fallbackResolved = withContext(Dispatchers.IO) {
                        fallback.freshCachedStreamUrl(policy = streamPolicy)?.let { fallback.toCachedResolvedAudio(it) }
                            ?: runCatching { resolveYouTubeAudio(context, fallback, streamPolicy) }.getOrNull()
                    }
                    if (generation != premiumDeckPrepareGeneration) return@launch
                    if (fallbackResolved != null) {
                        cachePreparedYouTubeSource(fallback, fallbackResolved)
                        setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.BackupReady)
                        continue
                    }
                }
                setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Failed)
            }
        })
    }
    fun cancelPremiumDeckSearchPreview(clearPreviewStates: Boolean = false) {
        premiumDeckSearchPreviewGeneration += 1L
        premiumDeckSearchPreviewJob.getAndSet(null)?.cancel()
        if (clearPreviewStates) {
            val activeQueueIds = activeYouTubeQueueIds.toSet()
            premiumDeckPrepareStates = premiumDeckPrepareStates.filterKeys { it in activeQueueIds }
        }
    }
    LaunchedEffect(premiumDeckOfflineActive) {
        if (!premiumDeckOfflineActive) return@LaunchedEffect
        cancelPremiumDeckNetworkJobs("Offline Deck enabled")
        cancelPremiumDeckSearchPreview(clearPreviewStates = true)
        cancelActivePremiumDeckResolve("offline_deck_enabled", clearPrepareState = true)
        searchDiscoveryGeneration += 1L
        premiumChartGeneration += 1L
        streamDiscoveryLoading = false
        streamNewReleaseLoading = false
        premiumDeckPodcastLoading = false
        premiumDeckPodcastAddLoading = false
        premiumDeckPodcastLoadingShowId = null
        searchDiscoveryLoadingGenreId = null
        premiumChartLoading = false
        premiumChartMatching = false
        youtubeSearchLoading = false
        youtubeSearchSuggestions = emptyList()
        if (activeStreamTrack != null && activeYouTubeSourceId != null) {
            val source = queueYouTubeSource(activeYouTubeSourceId)
            val uri = source?.downloadedUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val canContinueOffline = uri != null && isReadableOfflineUriOnIo(uri)
            if (!canContinueOffline) {
                playing = false
                activeStreamTrack = null
                activeYouTubeSourceId = null
                fallbackYouTubeSource = null
                activeYouTubeQueueIds = emptyList()
                transientYouTubeQueueSources = emptyList()
                activePremiumDeckSession = null
                premiumDeckPrepareStates = emptyMap()
                playbackState.closePlayer()
                resetDisplayedPlaybackPosition()
                Toast.makeText(context, "Offline Deck stopped the online PremiumDeck stream", Toast.LENGTH_SHORT).show()
            } else {
                activeYouTubeQueueIds = activeYouTubeQueueIds
                    .filter { queueYouTubeSource(it)?.isOfflineSaved() == true }
                    .distinct()
                    .ifEmpty { listOfNotNull(activeYouTubeSourceId) }
                syncActivePremiumDeckSessionQueue(activeYouTubeQueueIds)
            }
        }
    }
    fun livePremiumDeckSourceFor(source: YouTubeSource): YouTubeSource {
        val sourceKey = source.streamDistinctKey()
        return youtubeSources.firstOrNull {
            it.id == source.id || it.url == source.url || it.streamDistinctKey() == sourceKey
        } ?: preparedPreviewSourceFor(source) ?: source
    }
    fun preparePremiumDeckSearchPreview(results: List<YouTubeSearchResult>) {
        if (screen != Screen.YouTube || premiumDeckOfflineActive || results.isEmpty()) {
            cancelPremiumDeckSearchPreview(clearPreviewStates = true)
            return
        }
        val streamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
        if (!streamPolicy.allowPreviewPreparation) {
            cancelPremiumDeckSearchPreview(clearPreviewStates = true)
            return
        }
        val rowSources = results
            .map { it.source }
            .filter { it.kind == YouTubeSourceKind.Video && !it.isPodcast }
            .distinctBy { it.streamDistinctKey() }
        if (rowSources.isEmpty()) {
            cancelPremiumDeckSearchPreview(clearPreviewStates = true)
            return
        }
        val visibleIds = rowSources.map { it.id }.toSet()
        val activeQueueIds = activeYouTubeQueueIds.toSet()
        premiumDeckPrepareStates = premiumDeckPrepareStates.filterKeys { it in visibleIds || it in activeQueueIds }
        rowSources.take(8).forEach { rowSource ->
            val latest = livePremiumDeckSourceFor(rowSource)
            if (latest.freshCachedStreamUrl(policy = streamPolicy) != null) {
                setPremiumDeckPrepareState(rowSource.id, PremiumDeckStreamPrepareState.Ready)
            }
        }
        val candidates = rowSources
            .map { rowSource -> rowSource to livePremiumDeckSourceFor(rowSource) }
            .filter { (_, latest) ->
                latest.freshCachedStreamUrl(policy = streamPolicy) == null
            }
            .take(2)
        if (candidates.isEmpty()) return
        premiumDeckSearchPreviewGeneration += 1L
        val generation = premiumDeckSearchPreviewGeneration
        val job = appScope.launch {
            delay(420L)
            for ((rowSource, candidate) in candidates) {
                if (generation != premiumDeckSearchPreviewGeneration || resolvingYouTubeSourceId != null) return@launch
                val latest = livePremiumDeckSourceFor(candidate)
                val downloadedUri = latest.downloadedUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (downloadedUri != null && isReadableOfflineUriOnIo(downloadedUri)) {
                    setPremiumDeckPrepareState(rowSource.id, PremiumDeckStreamPrepareState.Ready)
                    continue
                }
                if (latest.freshCachedStreamUrl(policy = streamPolicy) != null) {
                    setPremiumDeckPrepareState(rowSource.id, PremiumDeckStreamPrepareState.Ready)
                    continue
                }
                setPremiumDeckPrepareState(rowSource.id, PremiumDeckStreamPrepareState.Preparing)
                if (latest.id != rowSource.id) setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Preparing)
                val resolved = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(4_800L) {
                        resolveYouTubeAudio(context, latest, streamPolicy)
                    }
                }
                if (generation != premiumDeckSearchPreviewGeneration) return@launch
                if (resolved != null) {
                    cachePreparedYouTubeSource(latest, resolved)
                    setPremiumDeckPrepareState(rowSource.id, PremiumDeckStreamPrepareState.Ready)
                    if (latest.id != rowSource.id) setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Ready)
                } else {
                    setPremiumDeckPrepareState(rowSource.id, PremiumDeckStreamPrepareState.Idle)
                    if (latest.id != rowSource.id) setPremiumDeckPrepareState(latest.id, PremiumDeckStreamPrepareState.Idle)
                }
            }
        }
        premiumDeckSearchPreviewJob.getAndSet(job)?.cancel()
    }
    var previousImageHeavyRoute by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        val previous = previousImageHeavyRoute
        if (previous != screen) {
            when (previous) {
                Screen.YouTube -> {
                    cancelPremiumDeckSearchPreview(clearPreviewStates = true)
                    if (resolvingYouTubeSourceId != null) {
                        cancelActivePremiumDeckResolve("route_hidden", clearPrepareState = true)
                    }
                    searchDiscoveryPreviewCollection = null
                    searchDiscoveryLoadingGenreId = null
                    personalizationMixes = emptyList()
                    AlbumArtworkRuntime.onPremiumDeckNativeState(
                        reason = "route_hidden",
                        health = tinyRecRunner.health.name,
                        productionReady = tinyRecRunner.productionReady,
                    )
                    AlbumArtworkRuntime.onRouteHidden(previous.name, screen.name)
                }
                Screen.PulseRadio -> {
                    AlbumArtworkRuntime.onRouteHidden(previous.name, screen.name)
                }
                else -> Unit
            }
            previousImageHeavyRoute = screen
        }
        when (screen) {
            Screen.YouTube, Screen.PulseRadio -> AlbumArtworkRuntime.onRouteVisible(screen.name)
            else -> Unit
        }
    }
    fun nextQueuedYouTubeSourceAfter(sourceId: String?, queueIds: List<String>): YouTubeSource? {
        if (queueIds.size <= 1) return null
        val startIndex = queueIds.indexOf(sourceId).takeIf { it >= 0 } ?: -1
        for (index in (startIndex + 1) until queueIds.size) {
            val source = queueYouTubeSource(queueIds[index])
            if (source != null && source.id != sourceId && (!premiumDeckOfflineActive || source.isOfflineSaved())) return source
        }
        return null
    }
    fun localManualQueueTracks(): List<Track> {
        val currentKey = currentTrackOrNull()?.let(::trackKeyOf)
        val tracksByKey = libraryTracks.associateBy { it.stableKey() }
        return manualQueueTrackKeys
            .asSequence()
            .filter { it != currentKey }
            .distinct()
            .mapNotNull { tracksByKey[it] }
            .toList()
    }
    fun streamQueueTracks(): List<Track> {
        val currentId = activeYouTubeSourceId
        val upcomingQueueIds = activeYouTubeQueueIds
            .indexOf(currentId)
            .takeIf { it >= 0 }
            ?.let { currentIndex -> activeYouTubeQueueIds.drop(currentIndex + 1) }
            ?: activeYouTubeQueueIds.filter { it != currentId }
        return upcomingQueueIds
            .asSequence()
            .distinct()
            .mapNotNull { queueYouTubeSource(it)?.toQueuePreviewTrack() }
            .toList()
    }
    fun visiblePlayerQueueTracks(): List<Track> {
        if (activeStreamTrack != null) return streamQueueTracks()
        val manualTracks = localManualQueueTracks()
        if (manualTracks.isNotEmpty()) return manualTracks
        val currentKey = currentTrackOrNull()?.let(::trackKeyOf)
        return resolveUpcomingLocalTracks(localPlaybackSession(), limit = 18)
            .filter { trackKeyOf(it) != currentKey }
    }
    fun replaceVisiblePlayerQueue(nextTracks: List<Track>) {
        val currentKey = currentTrackOrNull()?.let(::trackKeyOf)
        if (activeStreamTrack != null) {
            val currentId = activeYouTubeSourceId
            val sourceByTrackKey = (activeYouTubeQueueIds.mapNotNull(::queueYouTubeSource) + transientYouTubeQueueSources + youtubeSources)
                .distinctBy { it.id }
                .map { source -> source.toQueuePreviewTrack().stableKey() to source.id }
                .toMap()
            val nextIds = nextTracks
                .mapNotNull { sourceByTrackKey[it.stableKey()] }
                .filter { it != currentId }
                .distinct()
            activeYouTubeQueueIds = (listOfNotNull(currentId) + nextIds).distinct()
            syncActivePremiumDeckSessionQueue(activeYouTubeQueueIds)
            prepareUpcomingPremiumDeckStreams()
            return
        }
        playbackState.replaceManualQueue(
            nextTracks
                .map(::trackKeyOf)
                .filter { it != currentKey }
                .distinct(),
        )
    }
    fun movePlayerQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = visiblePlayerQueueTracks().toMutableList()
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        replaceVisiblePlayerQueue(queue)
    }
    fun removePlayerQueueItem(index: Int) {
        val queue = visiblePlayerQueueTracks().toMutableList()
        if (index !in queue.indices) return
        queue.removeAt(index)
        replaceVisiblePlayerQueue(queue)
    }
    fun insertPlayerQueueItem(index: Int, track: Track) {
        val queue = visiblePlayerQueueTracks().toMutableList()
        val targetIndex = index.coerceIn(0, queue.size)
        queue.removeAll { it.stableKey() == track.stableKey() }
        queue.add(targetIndex.coerceIn(0, queue.size), track)
        replaceVisiblePlayerQueue(queue)
    }
    fun clearVisiblePlayerQueue() {
        if (activeStreamTrack != null) {
            activeYouTubeQueueIds = listOfNotNull(activeYouTubeSourceId)
            syncActivePremiumDeckSessionQueue(activeYouTubeQueueIds)
            prepareUpcomingPremiumDeckStreams()
        } else {
            playbackState.clearManualQueue()
        }
    }
    fun consumeNextManualLocalTrack(): Track? {
        val currentKey = currentTrackOrNull()?.let(::trackKeyOf)
        val tracksByKey = libraryTracks.associateBy { it.stableKey() }
        val cleanKeys = manualQueueTrackKeys
            .filter { it != currentKey && it in tracksByKey }
            .distinct()
        if (cleanKeys != manualQueueTrackKeys) manualQueueTrackKeys = cleanKeys
        val targetKey = cleanKeys.firstOrNull() ?: return null
        manualQueueTrackKeys = cleanKeys.drop(1)
        return tracksByKey[targetKey]
    }
    fun logCurrentPlaybackTransition(autoEnded: Boolean) {
        val type = if (autoEnded) BehaviorEventType.TrackCompleted else BehaviorEventType.TrackSkipped
        val currentPosition = currentPlaybackPositionMillis()
        val skipSeconds = if (autoEnded) 0 else (currentPosition / 1000L).toInt()
        val listenMillis = if (autoEnded) currentTrackOrNull()?.durationMillis?.takeIf { it > 0L } ?: currentPosition else currentPosition
        if (activeStreamTrack != null) {
            val source = queueYouTubeSource(activeYouTubeSourceId) ?: fallbackYouTubeSource
            if (source != null) {
                logPersonalization(
                    source.toBehaviorEvent(
                        type,
                        listenDurationMillis = listenMillis,
                        skipPositionSeconds = skipSeconds,
                        metadata = mapOf("source" to "premiumdeck_catalog"),
                    ),
                )
            }
        } else {
            currentTrackOrNull()?.let { current ->
                logPersonalization(
                    current.toBehaviorEvent(
                        type,
                        listenDurationMillis = listenMillis,
                        skipPositionSeconds = skipSeconds,
                        metadata = mapOf("source" to "local_library"),
                    ),
                )
            }
        }
    }
    fun moveNextTrack(autoEnded: Boolean = false) {
        logCurrentPlaybackTransition(autoEnded)
        if (activeStreamTrack != null) {
            val queueIndex = activeYouTubeQueueIds.indexOf(activeYouTubeSourceId)
            val nextId = activeYouTubeQueueIds.getOrNull(queueIndex + 1)
            val nextSource = queueYouTubeSource(nextId)
            if (nextSource != null) {
                playYouTubeSourceFromQueue?.invoke(nextSource, activeYouTubeQueueIds)
                return
            }
            if (autoEnded && pulseSettingsState.misc.online.automaticSongPicker && !premiumDeckOfflineActive) {
                val discoveryCandidates = if (pulseSettingsState.misc.online.externalRecommendations) streamDiscoverySnapshot.results.map { it.source } else emptyList()
                val localCandidates = youtubeSources
                    .filter { it.reviewState == YouTubeReviewState.Accepted }
                    .sortedWith(compareByDescending<YouTubeSource> { it.reaction == YouTubeReaction.Liked }.thenByDescending { it.playCount }.thenByDescending { it.lastPlayedMillis })
                val picked = (discoveryCandidates + localCandidates)
                    .filter { it.id != activeYouTubeSourceId && it.id !in activeYouTubeQueueIds && it.reaction != YouTubeReaction.Disliked }
                    .distinctBy { it.url.ifBlank { it.id } }
                    .firstOrNull()
                if (picked != null) {
                    transientYouTubeQueueSources = listOf(picked)
                    activePremiumDeckSession = buildPremiumDeckPlaybackSession(
                        origin = PremiumDeckPlaybackOrigin.StreamCollection,
                        sources = listOf(picked),
                        startedSource = picked,
                        title = premiumDeckSessionTitle(PremiumDeckPlaybackOrigin.StreamCollection),
                        subtitle = "Pulse Intelligence",
                        originKey = "automatic-song-picker",
                        reason = PremiumDeckQueueReason("Pulse Intelligence", "Pulse Intelligence"),
                    )
                    premiumDeckPrepareStates = emptyMap()
                    premiumDeckPrepareGeneration += 1L
                    playYouTubeSourceFromQueue?.invoke(picked, listOf(picked.id))
                    return
                }
            }
            playing = false
            return
        }
        val manualTarget = consumeNextManualLocalTrack()
        if (manualTarget != null) {
            playResolvedTrack(manualTarget, direction = 1, rememberHistory = currentTrackOrNull()?.let { trackKeyOf(manualTarget) != trackKeyOf(it) } ?: false)
            if (autoEnded) playing = true
            return
        }
        val target = resolveNextTrack(autoEnded)
        if (target == null) {
            playing = false
            return
        }
        playResolvedTrack(target, direction = 1, rememberHistory = currentTrackOrNull()?.let { trackKeyOf(target) != trackKeyOf(it) } ?: false)
        if (autoEnded) playing = true
    }
    fun movePreviousTrack() {
        logCurrentPlaybackTransition(autoEnded = false)
        if (activeStreamTrack != null) {
            val queueIndex = activeYouTubeQueueIds.indexOf(activeYouTubeSourceId)
            val previousId = activeYouTubeQueueIds.getOrNull(queueIndex - 1)
            val previousSource = queueYouTubeSource(previousId)
            if (previousSource != null) {
                playYouTubeSourceFromQueue?.invoke(previousSource, activeYouTubeQueueIds)
                return
            }
            playing = false
            return
        }
        val previous = resolvePreviousLocalTrack(localPlaybackSession())
        previous.remainingHistory?.let { playbackState.replacePlaybackHistory(it) }
        previous.track?.let { playResolvedTrack(it, direction = -1, rememberHistory = false) }
    }
    fun disableSleepTimer() {
        playbackState.disableSleepTimer()
        playerVolume = 1f
    }
    fun setSleepTimer(minutes: Int, fadeOutEnabled: Boolean) {
        playbackState.setSleepTimer(minutes, fadeOutEnabled)
        playerVolume = 1f
    }
    fun requestDeleteTracks(targets: List<Track>) {
        val deletableTargets = targets
            .distinctBy { it.stableKey() }
            .filter { it.uri != null }
        if (deletableTargets.isEmpty()) {
            Toast.makeText(context, "No selected local files can be deleted", Toast.LENGTH_SHORT).show()
            return
        }
        val target = deletableTargets.first()
        val uri = target.uri
        if (uri == null) {
            Toast.makeText(context, "Demo tracks cannot be deleted", Toast.LENGTH_SHORT).show()
            return
        }
        pendingDeleteTracks = deletableTargets
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, deletableTargets.mapNotNull { it.uri })
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            return
        }
        val deletedTargets = mutableListOf<Track>()
        runCatching {
            deletableTargets.forEach { item ->
                val itemUri = item.uri ?: return@forEach
                val deleted = context.contentResolver.delete(itemUri, null, null)
                if (deleted > 0) deletedTargets += item
            }
            if (deletedTargets.isNotEmpty()) removeTracksFromLibrary(deletedTargets, requestSeek = true)
            val count = deletedTargets.size
            val message = if (count > 0) {
                "Deleted $count ${if (count == 1) "file" else "files"} from library"
            } else {
                "Unable to delete selected files"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            pendingDeleteTracks = emptyList()
        }.onFailure { error ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error is RecoverableSecurityException) {
                pendingDeleteTracks = listOf(target)
                deleteLauncher.launch(IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build())
            } else {
                pendingDeleteTracks = emptyList()
                Toast.makeText(context, "Unable to delete this file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun requestDeleteTrack(target: Track) {
        requestDeleteTracks(listOf(target))
    }
    fun setLiked(target: Track, liked: Boolean) {
        val key = target.stableKey()
        likedTrackKeys = (if (liked) likedTrackKeys + key else likedTrackKeys - key).also { localLibraryRepository.saveStateSet(PREF_LIKED_TRACKS, it) }
        if (liked) dislikedTrackKeys = (dislikedTrackKeys - key).also { localLibraryRepository.saveStateSet(PREF_DISLIKED_TRACKS, it) }
        if (liked) logPersonalization(target.toBehaviorEvent(BehaviorEventType.LikeFavorite))
    }
    fun setDisliked(target: Track, disliked: Boolean) {
        val key = target.stableKey()
        dislikedTrackKeys = (if (disliked) dislikedTrackKeys + key else dislikedTrackKeys - key).also { localLibraryRepository.saveStateSet(PREF_DISLIKED_TRACKS, it) }
        if (disliked) likedTrackKeys = (likedTrackKeys - key).also { localLibraryRepository.saveStateSet(PREF_LIKED_TRACKS, it) }
        if (disliked) logPersonalization(target.toBehaviorEvent(BehaviorEventType.DislikeHide))
    }
    fun toggleBookmark(target: Track) {
        val key = target.stableKey()
        val adding = key !in bookmarkedTrackKeys
        bookmarkedTrackKeys = (if (adding) bookmarkedTrackKeys + key else bookmarkedTrackKeys - key).also { localLibraryRepository.saveStateSet(PREF_BOOKMARKED_TRACKS, it) }
        if (adding) logPersonalization(target.toBehaviorEvent(BehaviorEventType.LikeFavorite))
    }
    fun togglePlaylist(target: Track) {
        val key = target.stableKey()
        val adding = key !in playlistTrackKeys
        playlistTrackKeys = (if (adding) playlistTrackKeys + key else playlistTrackKeys - key).also { localLibraryRepository.saveStateSet(PREF_PLAYLIST_TRACKS, it) }
        logPersonalization(target.toBehaviorEvent(if (adding) BehaviorEventType.TrackAddedToPlaylist else BehaviorEventType.TrackRemovedFromPlaylist))
    }
    fun showTrackMetadata(target: Track, quality: String = target.quality) {
        infoDialog = trackInfoDialog(target, quality)
    }
    fun showSimpleInfo(title: String, subtitle: String, rows: List<Pair<String, String>>) {
        infoDialog = InfoDialogState(title, subtitle, rows)
    }
    fun openPlayer(fromMini: Boolean, launchHandoff: PlayerLaunchHandoff? = null) {
        playerLaunchHandoff = if (fromMini) null else launchHandoff
        playbackState.openPlayer(fromMini)
    }
    fun playLocalTrackFromList(selected: Track, shuffle: Boolean = false) {
        activeAlbumProjectReleaseId = null
        activeAlbumKey = null
        setAllSongsQueueContext()
        shuffleMode = if (shuffle) ShuffleMode.ShuffleAll else ShuffleMode.Off
        repeatMode = PlaybackRepeatMode.AllOnce
        playResolvedTrack(selected, direction = 1, rememberHistory = false)
        clearPlaybackHistory()
        resetDisplayedPlaybackPosition()
        openPlayer(false)
        playing = true
    }
    fun playLocalTrackList(source: List<Track>, shuffle: Boolean) {
        val selected = if (shuffle) source.randomOrNull() else source.firstOrNull()
        if (selected != null) playLocalTrackFromList(selected, shuffle = shuffle)
    }
    fun playerReturnAlbumKey(): String? {
        if (activeStreamTrack != null) return null
        return currentLocalTrackOrNull()
            ?.album
            ?.key
            ?.takeIf { it in libraryAlbumByKey }
            ?: activeAlbumKey?.takeIf { it in libraryAlbumByKey }
            ?: albumReturnTargetKey?.takeIf { it in libraryAlbumByKey }
            ?: selectedAlbumKey?.takeIf { it in libraryAlbumByKey }
    }
    suspend fun awaitAlbumTileBounds(albumKey: String, timeoutMs: Long = 350L): AlbumTileBounds? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        albumTileBoundsByKey[albumKey]?.let { return it }
        while (SystemClock.elapsedRealtime() < deadline) {
            withFrameNanos { }
            albumTileBoundsByKey[albumKey]?.let { return it }
        }
        return albumTileBoundsByKey[albumKey]
    }
    fun albumGridItemIndexForKey(albumKey: String?): Int? {
        val index = libraryAlbums.indexOfFirst { it.key == albumKey }
        return index.takeIf { it >= 0 }?.let { it + 1 }
    }
    fun centeredAlbumGridScrollIndex(targetIndex: Int): Int {
        val albumIndex = (targetIndex - 1).coerceAtLeast(0)
        val targetRow = albumIndex / 2
        val leadRows = 1
        return if (targetRow <= leadRows) {
            0
        } else {
            1 + (targetRow - leadRows) * 2
        }
    }
    suspend fun scrollAlbumGridToAlbum(
        albumKey: String?,
        timeoutMs: Long = 350L,
        requireFreshBounds: Boolean = false,
        centerTarget: Boolean = false,
    ): AlbumTileBounds? {
        if (albumKey == null) {
            albumGridState.scrollToItem(0)
            return null
        }
        val targetIndex = albumGridItemIndexForKey(albumKey) ?: return null
        if (requireFreshBounds) {
            albumTileBoundsByKey = albumTileBoundsByKey - albumKey
            albumReturnTargetBounds = null
        }
        albumGridState.scrollToItem(if (centerTarget) centeredAlbumGridScrollIndex(targetIndex) else targetIndex)
        return awaitAlbumTileBounds(albumKey, timeoutMs)
    }
    fun prepareLibrarySurfaceForPlayerMinimize() {
        when (screen) {
            Screen.AlbumDetail -> Unit
            Screen.Albums -> {
                val albumKey = playerReturnAlbumKey()
                albumReturnTargetKey = albumKey
                albumReturnTargetBounds = null
                appScope.launch {
                    val bounds = scrollAlbumGridToAlbum(
                        albumKey,
                        requireFreshBounds = true,
                        centerTarget = true,
                    )
                    if (albumReturnTargetKey == albumKey) albumReturnTargetBounds = bounds
                }
            }
            else -> Unit
        }
    }
    fun playbackContextScreen(): Screen =
        when {
            activeStreamTrack != null && activeAlbumProjectReleaseId != null -> Screen.AlbumDownloader
            activeStreamTrack != null && activeYouTubeSourceId == null -> Screen.PulseRadio
            activeStreamTrack != null -> Screen.YouTube
            playbackQueueContext is PlaybackQueueContext.LocalAlbum -> Screen.AlbumDetail
            else -> Screen.AllSongs
        }
    fun requestClosePlayer() {
        playerCloseInFlight = true
        prepareLibrarySurfaceForPlayerMinimize()
        playbackState.requestClosePlayer()
    }
    LaunchedEffect(libraryTracks.size) {
        if (libraryTracks.isEmpty() || trackIndex !in libraryTracks.indices) trackIndex = 0
    }
    val playableTrack = currentTrackOrNull()?.withCanonicalAlbum()
    val hasPlayableTrack = playableTrack != null
    val track = playableTrack ?: demoTracks.first()
    fun currentAlbumTrackLaunchHandoff(): PlayerLaunchHandoff? {
        if (screen != Screen.AlbumDetail || activeStreamTrack != null) return null
        return albumTrackLaunchHandoffs[track.stableKey()]
    }
    fun openPlayerFromCurrentSurface() {
        val handoff = currentAlbumTrackLaunchHandoff()
        openPlayer(fromMini = handoff == null, launchHandoff = handoff)
    }
    fun albumDetailPlaybackReturnKey(): String? {
        if (albumDetailPlaybackAnchorKey == null || activeStreamTrack != null) return null
        return currentLocalTrackOrNull()
            ?.album
            ?.key
            ?.takeIf { it in libraryAlbumByKey }
            ?: activeAlbumKey?.takeIf { it in libraryAlbumByKey }
    }
    fun albumDetailReturnKey(): String? =
        albumDetailPlaybackReturnKey() ?: albumReturnTargetKey ?: selectedAlbumKey

    fun setAlbumReturnTarget(albumKey: String?) {
        albumReturnTargetKey = albumKey
        albumReturnTargetBounds = albumKey?.let { albumTileBoundsByKey[it] }
    }
    fun requestAlbumDetailCloseToGrid(startProgress: Float = 0f) {
        val targetKey = albumDetailReturnKey()
        albumDetailCloseRetargetJob?.cancel()
        albumReturnFrozenKey = targetKey
        albumDetailCloseStartProgress = startProgress.coerceIn(0f, 1f)
        setAlbumReturnTarget(targetKey)
        albumDetailCloseRequestKey += 1
        val measuredBounds = targetKey?.let { albumTileBoundsByKey[it] } ?: albumReturnTargetBounds
        if (measuredBounds != null && measuredBounds.width > 0f && measuredBounds.height > 0f) {
            albumReturnTargetBounds = measuredBounds
            albumReturnPreparing = false
            albumDetailCloseRetargetJob = null
            return
        }
        albumDetailCloseRetargetJob = appScope.launch {
            albumReturnPreparing = true
            try {
                val bounds = scrollAlbumGridToAlbum(targetKey)
                if (albumReturnFrozenKey == targetKey) {
                    albumReturnTargetBounds = bounds ?: albumReturnTargetBounds
                }
            } finally {
                albumReturnPreparing = false
            }
        }
    }
    LaunchedEffect(
        screen,
        selectedAlbumKey,
        activeAlbumKey,
        activeStreamTrack?.stableKey(),
        track.stableKey(),
        albumDetailPlaybackAnchorKey,
        albumReturnFrozenKey,
        libraryAlbums,
    ) {
        if (screen != Screen.AlbumDetail) return@LaunchedEffect
        if (albumReturnFrozenKey != null) return@LaunchedEffect
        val playbackReturnKey = albumDetailPlaybackReturnKey() ?: return@LaunchedEffect
        if (playbackReturnKey != albumReturnTargetKey) {
            setAlbumReturnTarget(playbackReturnKey)
            scrollAlbumGridToAlbum(playbackReturnKey)
        }
    }
    LaunchedEffect(hasPlayableTrack) {
        if (!hasPlayableTrack) {
            playbackState.markNoPlayableTrack()
            mediaSessionRequested = false
        } else {
            playbackState.markPlayableTrack()
        }
    }
    LaunchedEffect(hasPlayableTrack, playing) {
        if (hasPlayableTrack && playing) mediaSessionRequested = true
    }
    val miniPlayerVisible = !playerOpen && !miniDismissed && hasPlayableTrack && searchDiscoveryPreviewCollection == null
    val nonessentialMotionQuiet = routeMotionInFlight ||
        playerSceneMotionInFlight ||
        playerCloseInFlight ||
        albumReturnPreparing ||
        activeCategoryMotion != null ||
        categoryMotionRequest != null
    val waveformAnimationActive = appInForeground && playerOpen && waveformVisible && playing && !nonessentialMotionQuiet
    val lyricsVisibleForCurrentTrack = lyricsTrack?.stableKey() == track.stableKey()
    var syncedLyricsTickerActive by remember { mutableStateOf(false) }
    val lyricsFineTickerActive = lyricsVisibleForCurrentTrack && syncedLyricsTickerActive
    LaunchedEffect(lyricsTrack?.stableKey(), track.stableKey()) {
        if (!lyricsVisibleForCurrentTrack) syncedLyricsTickerActive = false
    }
    val sponsorSkipSource = youtubeSources.firstOrNull { it.id == activeYouTubeSourceId }
    val sponsorSkipEnabled = activeStreamTrack != null &&
        sponsorSkipSource != null &&
        pulseSettingsState.misc.online.sponsorBlock &&
        !premiumDeckOfflineActive &&
        sponsorSkipSource.skipSegmentsEnabled &&
        sponsorSkipSource.sponsorSegments.isNotEmpty()
    val sponsorSkipSegments = if (sponsorSkipEnabled) sponsorSkipSource?.sponsorSegments.orEmpty() else emptyList()
    val positionTickerPolicy = playbackTickerPolicy(
        appInForeground = appInForeground,
        playing = playing,
        playerOpen = playerOpen,
        miniPlayerVisible = miniPlayerVisible,
        waveformAnimating = waveformAnimationActive,
        lyricsVisible = lyricsFineTickerActive,
        sponsorSkipActive = false,
    )
    val visiblePlaybackSurface = when {
        playerOpen -> "full_player"
        miniPlayerVisible -> "mini_player"
        else -> screen.name.lowercase(Locale.US)
    }
    LaunchedEffect(positionTickerPolicy, waveformVisible, waveformAnimationActive, visiblePlaybackSurface, appInForeground) {
        PulseDeckPlaybackDiagnostics.reportTicker(
            policy = positionTickerPolicy,
            waveformVisible = waveformVisible,
            waveformAnimating = waveformAnimationActive,
            surface = visiblePlaybackSurface,
            appInForeground = appInForeground,
        )
    }
    LaunchedEffect(playing, track.stableKey()) {
        if (playing) miniDismissed = false
    }
    fun persistCurrentPlaybackState(reason: String = "manual") {
        if (!lastPlaybackRestored || !hasPlayableTrack) return
        val sourceId = activeYouTubeSourceId
        val activeStream = activeStreamTrack
        if (sourceId != null && activeStream == null) return
        val state = if (sourceId != null && activeStream != null) {
            LastPlaybackState(kind = "youtube", youtubeSourceId = sourceId, positionMillis = currentPlaybackPositionMillis())
        } else if (activeStream != null) {
            activeStream.toRadioLastPlaybackState(currentPlaybackPositionMillis())
        } else {
            track.toLastPlaybackState(currentPlaybackPositionMillis())
        }
        saveLastPlaybackState(context, state)
        PulseDeckPlaybackDiagnostics.recordPersistenceWrite(reason, state.kind)
    }
    LaunchedEffect(lastPlaybackRestored, activeYouTubeSourceId, activeStreamTrack?.stableKey(), track.stableKey()) {
        if (playing) persistCurrentPlaybackState("track")
    }
    LaunchedEffect(lastPlaybackRestored, activeYouTubeSourceId, activeStreamTrack?.stableKey(), track.stableKey(), playing) {
        if (!lastPlaybackRestored) return@LaunchedEffect
        while (true) {
            delay(5000L)
            if (playing) persistCurrentPlaybackState("periodic")
        }
    }
    var previousPlayingForPersistence by remember { mutableStateOf(playing) }
    LaunchedEffect(lastPlaybackRestored, playing) {
        if (!lastPlaybackRestored) {
            previousPlayingForPersistence = playing
            return@LaunchedEffect
        }
        if (previousPlayingForPersistence && !playing) persistCurrentPlaybackState("pause")
        previousPlayingForPersistence = playing
    }
    var previousForegroundForPersistence by remember { mutableStateOf(appInForeground) }
    LaunchedEffect(lastPlaybackRestored, appInForeground) {
        if (!lastPlaybackRestored) {
            previousForegroundForPersistence = appInForeground
            return@LaunchedEffect
        }
        if (previousForegroundForPersistence && !appInForeground) persistCurrentPlaybackState("background")
        previousForegroundForPersistence = appInForeground
    }
    fun seekToProgress(value: Float) {
        requestPlaybackSeek(playbackPositionFromProgress(track.durationMillis, value))
        persistCurrentPlaybackState("seek")
    }
    fun seekBy(deltaMillis: Long) {
        val duration = track.durationMillis.coerceAtLeast(0L)
        val target = if (duration > 0L) {
            (currentPlaybackPositionMillis() + deltaMillis).coerceIn(0L, duration)
        } else {
            0L
        }
        requestPlaybackSeek(target)
        persistCurrentPlaybackState("seek_relative")
    }
    LaunchedEffect(playing, sleepTimerDeadlineMillis, sleepTimerFadeOutEnabled, track.uri) {
        playerVolume = 1f
        if (playing && sleepTimerDeadlineMillis > 0L) {
            val fadeWindowMillis = 30_000L
            while (true) {
                val remaining = sleepTimerDeadlineMillis - System.currentTimeMillis()
                when {
                    remaining <= 0L -> {
                        playerVolume = 1f
                        playing = false
                        disableSleepTimer()
                        break
                    }
                    sleepTimerFadeOutEnabled && remaining <= fadeWindowMillis -> {
                        playerVolume = (remaining.toFloat() / fadeWindowMillis.toFloat()).coerceIn(0f, 1f)
                        delay(250L)
                    }
                    else -> {
                        playerVolume = 1f
                        delay(min(remaining, 1000L))
                    }
                }
            }
        }
    }
    val sleepTimerLabel = playbackState.sleepTimerLabel
    fun moveAlbumWithinLibrary(step: Int) {
        if (libraryTracks.isEmpty() || libraryAlbums.isEmpty()) return
        val currentAlbumKey = currentLocalTrackOrNull()?.album?.key ?: return
        val currentAlbumIndex = libraryAlbums.indexOfFirst { it.key == currentAlbumKey }.coerceAtLeast(0)
        val nextAlbum = libraryAlbums[Math.floorMod(currentAlbumIndex + step, libraryAlbums.size)]
        val nextTrack = libraryTracks.firstOrNull { it.album.key == nextAlbum.key } ?: return
        activeAlbumKey = nextAlbum.key
        playbackState.selectTrackIndex(
            libraryTracks.indexOfFirst { it.uri == nextTrack.uri && it.title == nextTrack.title },
            step,
        )
        resetDisplayedPlaybackPosition()
        saveLastPlaybackState(context, nextTrack.toLastPlaybackState(0L))
    }
    var waveform by remember(track.uri, track.title, track.artist) { mutableStateOf(defaultWaveform("${track.title}/${track.artist}", 64)) }
    LaunchedEffect(track.uri, track.title, track.artist) {
        waveform = defaultWaveform("${track.title}/${track.artist}", 64)
        waveform = loadAudioWaveform(context, track.uri, "${track.title}/${track.artist}", 64)
    }
    var screenDragX by remember { mutableFloatStateOf(0f) }
    fun pushCurrentRoute() {
        routeState.pushCurrentRoute()
    }
    fun navigate(next: Screen, pushHistory: Boolean = true, suppressMotion: Boolean = false) {
        if (next == screen) return
        if (next != Screen.AlbumDetail && next != Screen.Albums) {
            albumVisualBackground = null
        }
        routeState.navigate(next, pushHistory = pushHistory, suppressMotion = suppressMotion)
    }
    fun openAlbumBuilder(releaseId: String? = null, pushHistory: Boolean = true) {
        albumDownloaderInitialReleaseId = releaseId
        navigate(Screen.AlbumDownloader, pushHistory = pushHistory)
    }
    fun openSettings(
        target: SettingsLaunchTarget = SettingsLaunchTarget.Home,
        pushHistory: Boolean = true,
        suppressMotion: Boolean = false,
    ) {
        albumVisualBackground = null
        routeState.openSettings(target, pushHistory = pushHistory, suppressMotion = suppressMotion)
    }
    fun navigateDock(target: Screen) {
        albumVisualBackground = null
        activeCategoryMotion = null
        categoryMotionRequest = null
        if (target != Screen.AlbumDetail) selectedAlbumKey = null
        when (target) {
            Screen.Settings -> openSettings(SettingsLaunchTarget.Home, pushHistory = false, suppressMotion = true)
            Screen.Library, Screen.Audio, Screen.Search -> routeState.navigateTopLevel(target, suppressMotion = true)
            else -> navigate(target, pushHistory = false, suppressMotion = true)
        }
    }
    fun openPlaybackContext(pushHistory: Boolean = false, suppressMotion: Boolean = false) {
        when (playbackContextScreen()) {
            Screen.AlbumDownloader -> {
                activeAlbumProjectReleaseId?.let { openAlbumBuilder(it, pushHistory = pushHistory) }
                    ?: navigate(Screen.YouTube, pushHistory = pushHistory, suppressMotion = suppressMotion)
            }
            Screen.PulseRadio -> navigate(Screen.PulseRadio, pushHistory = pushHistory, suppressMotion = suppressMotion)
            Screen.YouTube -> navigate(Screen.YouTube, pushHistory = pushHistory, suppressMotion = suppressMotion)
            Screen.AlbumDetail -> {
                val albumKey = (playbackQueueContext as? PlaybackQueueContext.LocalAlbum)?.albumId
                    ?: playerReturnAlbumKey()
                if (albumKey != null && albumKey in libraryAlbumByKey) {
                    activeAlbumKey = albumKey
                    selectedAlbumKey = albumKey
                    albumReturnFrozenKey = null
                    setAlbumReturnTarget(albumKey)
                    if (screen != Screen.AlbumDetail) {
                        if (screen == Screen.Albums) {
                            appScope.launch {
                                val bounds = scrollAlbumGridToAlbum(
                                    albumKey,
                                    requireFreshBounds = true,
                                    centerTarget = true,
                                )
                                if (selectedAlbumKey == albumKey && screen == Screen.Albums) {
                                    albumReturnTargetBounds = bounds ?: albumReturnTargetBounds
                                    navigate(Screen.AlbumDetail, pushHistory = pushHistory, suppressMotion = suppressMotion)
                                }
                            }
                        } else {
                            appScope.launch {
                                scrollAlbumGridToAlbum(
                                    albumKey,
                                    requireFreshBounds = true,
                                    centerTarget = true,
                                )
                            }
                            navigate(Screen.AlbumDetail, pushHistory = pushHistory, suppressMotion = suppressMotion)
                        }
                    }
                } else {
                    activeAlbumKey = null
                    navigate(Screen.AllSongs, pushHistory = pushHistory, suppressMotion = suppressMotion)
                }
            }
            else -> {
                activeAlbumKey = null
                navigate(Screen.AllSongs, pushHistory = pushHistory, suppressMotion = suppressMotion)
            }
        }
    }
    fun closePlayerToPlaybackContext() {
        val returnAlbumProjectId = activeAlbumProjectReleaseId
        prepareLibrarySurfaceForPlayerMinimize()
        playbackState.closePlayer()
        playerLaunchHandoff = null
        playerCloseInFlight = false
        if (returnAlbumProjectId != null && screen == Screen.YouTube) {
            openAlbumBuilder(returnAlbumProjectId, pushHistory = false)
        } else if (screen == Screen.Library || screen == Screen.Albums) {
            openPlaybackContext(pushHistory = false, suppressMotion = false)
        }
    }
    fun openAlbumsFromLibrary() {
        albumReturnFrozenKey = null
        setAlbumReturnTarget(null)
        albumDetailPlaybackAnchorKey = null
        val sourceBounds = categoryBoundsByName["Albums"]
        val useSharedHandoff = LibraryCategorySharedHandoffEnabled && sourceBounds != null
        if (useSharedHandoff) {
            requestCategoryMotion("Albums", sourceBounds, CategoryMotionDirection.Opening)
        }
        navigate(Screen.Albums, suppressMotion = useSharedHandoff)
        appScope.launch {
            albumGridState.scrollToItem(0)
        }
    }
    fun openCategoryFromLibrary(categoryName: String, next: Screen) {
        val sourceBounds = categoryBoundsByName[categoryName]
        val useSharedHandoff = LibraryCategorySharedHandoffEnabled && sourceBounds != null
        if (useSharedHandoff) {
            requestCategoryMotion(categoryName, sourceBounds, CategoryMotionDirection.Opening)
        }
        navigate(next, suppressMotion = useSharedHandoff)
    }
    fun openLibraryCategory(kind: LibraryCategoryKind, categoryName: String) {
        routeState.clearLibraryGroupSelection()
        when (kind) {
            LibraryCategoryKind.AllSongs -> openCategoryFromLibrary(categoryName, Screen.AllSongs)
            LibraryCategoryKind.PremiumDeck -> navigate(Screen.YouTube, suppressMotion = true)
            LibraryCategoryKind.PulseRadio -> openCategoryFromLibrary(categoryName, Screen.PulseRadio)
            LibraryCategoryKind.Folders -> openCategoryFromLibrary(categoryName, Screen.Folders)
            LibraryCategoryKind.FolderHierarchy -> {
                selectedFolderPath = ""
                openCategoryFromLibrary(categoryName, Screen.FolderHierarchy)
            }
            LibraryCategoryKind.Albums -> openAlbumsFromLibrary()
            LibraryCategoryKind.Artists -> openCategoryFromLibrary(categoryName, Screen.Artists)
            LibraryCategoryKind.AlbumArtists -> openCategoryFromLibrary(categoryName, Screen.AlbumArtists)
            LibraryCategoryKind.Genres -> openCategoryFromLibrary(categoryName, Screen.Genres)
            LibraryCategoryKind.Years -> openCategoryFromLibrary(categoryName, Screen.Years)
            LibraryCategoryKind.Composers -> openCategoryFromLibrary(categoryName, Screen.Composers)
            LibraryCategoryKind.Playlists -> openCategoryFromLibrary(categoryName, Screen.LocalPlaylists)
            LibraryCategoryKind.Bookmarks -> openCategoryFromLibrary(categoryName, Screen.Bookmarks)
            LibraryCategoryKind.MostPlayed -> openCategoryFromLibrary(categoryName, Screen.MostPlayed)
        }
    }
    fun openLibraryGroup(kind: LocalLibraryGroupKind, group: LocalLibraryGroup) {
        albumVisualBackground = null
        routeState.selectLibraryGroup(kind, group.key)
    }
    fun categoryForName(name: String): Category? = categories.firstOrNull { it.name == name }
    fun updateCategoryBackDrag(categoryName: String, progress: Float) {
        if (!LibraryCategorySharedHandoffEnabled) return
        val sourceBounds = categoryHeaderBoundsByName[categoryName] ?: return
        val targetBounds = categoryBoundsByName[categoryName] ?: return
        val category = categoryForName(categoryName) ?: return
        activeCategoryMotion = CategorySharedMotion(
            category = category,
            fromBounds = sourceBounds,
            toBounds = targetBounds,
            direction = CategoryMotionDirection.Closing,
            nonce = Int.MIN_VALUE + categoryName.hashCode(),
            progressOverride = progress.coerceIn(0f, 1f),
        )
    }
    fun cancelCategoryBackDrag(categoryName: String) {
        val active = activeCategoryMotion
        if (active?.category?.name == categoryName && active.progressOverride != null) {
            activeCategoryMotion = null
        }
    }
    fun finishCategoryToLibrary(categoryName: String, startProgress: Float = 0f) {
        categoryMotionRequest = null
        if (!LibraryCategorySharedHandoffEnabled) {
            cancelCategoryBackDrag(categoryName)
            routeState.finishCategoryToLibrary()
            return
        }
        val sourceBounds = categoryHeaderBoundsByName[categoryName]
        val targetBounds = categoryBoundsByName[categoryName]
        val category = categoryForName(categoryName)
        if (sourceBounds != null && targetBounds != null && category != null) {
            categoryMotionNonce += 1
            activeCategoryMotion = CategorySharedMotion(
                category = category,
                fromBounds = sourceBounds,
                toBounds = targetBounds,
                direction = CategoryMotionDirection.Closing,
                nonce = categoryMotionNonce,
                startProgress = startProgress.coerceIn(0f, 1f),
                progressOverride = null,
            )
        } else {
            cancelCategoryBackDrag(categoryName)
        }
        routeState.finishCategoryToLibrary()
    }
    fun finishAlbumsToLibrary(startProgress: Float = 0f) {
        finishCategoryToLibrary("Albums", startProgress)
        selectedAlbumKey = null
        albumReturnTargetKey = null
        albumReturnFrozenKey = null
        albumDetailPlaybackAnchorKey = null
        albumReturnTargetBounds = null
    }
    fun openAlbumDetail(albumKey: String) {
        if (screen == Screen.AlbumDetail && selectedAlbumKey == albumKey) return
        val openedAlbum = libraryAlbums.firstOrNull { it.key == albumKey }
        albumVisualBackground = openedAlbum
        albumDetailCloseRetargetJob?.cancel()
        albumReturnPreparing = false
        albumReturnFrozenKey = null
        albumDetailPlaybackAnchorKey = null
        setAlbumReturnTarget(albumKey)
        if (openedAlbum != null) {
            logPersonalization(
                BehaviorEvent(
                    type = BehaviorEventType.AlbumOpened,
                    itemId = openedAlbum.key,
                    title = openedAlbum.title,
                    artist = openedAlbum.artist,
                    album = openedAlbum.title,
                    source = CandidateSource.LocalLibrary,
                ),
            )
        }
        routeState.openAlbumDetail(albumKey)
    }
    fun finishAlbumDetailToGrid() {
        routeState.finishAlbumDetailToGrid()
        albumReturnTargetKey = null
        albumReturnFrozenKey = null
        albumDetailPlaybackAnchorKey = null
        albumReturnTargetBounds = null
    }
    fun leaveSettingsRoute() {
        val previous = routeHistory.lastOrNull()
        if (previous != null && routeState.goBackToPreviousRoute(suppressMotion = previous.screen == Screen.YouTube)) {
            return
        }
        navigate(Screen.Library, pushHistory = false, suppressMotion = true)
    }
    fun requestSettingsBack() {
        settingsBackRequestKey += 1
    }
    fun goBack() {
        if (playerOpen) {
            requestClosePlayer()
            return
        }
        if (screen == Screen.AlbumDetail) {
            requestAlbumDetailCloseToGrid()
            return
        }
        if (screen == Screen.Albums) {
            finishAlbumsToLibrary()
            return
        }
        if (screen == Screen.Settings) {
            requestSettingsBack()
            return
        }
        val libraryCategoryName = libraryCategoryNameForScreen(screen)
        if (libraryCategoryName != null && routeHistory.lastOrNull()?.screen == Screen.Library) {
            finishCategoryToLibrary(libraryCategoryName)
            return
        }
        val previous = routeHistory.lastOrNull()
        if (previous != null && routeState.goBackToPreviousRoute(suppressMotion = screen == Screen.YouTube || previous.screen == Screen.YouTube)) {
            return
        }
        when (screen) {
            Screen.Library -> Unit
            Screen.YouTubeStream -> navigate(Screen.YouTube, pushHistory = false)
            Screen.YouTube -> navigate(Screen.Library, pushHistory = false, suppressMotion = true)
            Screen.AlbumDetail -> albumDetailCloseRequestKey += 1
            else -> navigate(Screen.Library, pushHistory = false)
        }
    }
    fun moveScreen(step: Int) {
        routeState.primaryRouteAfter(step)?.let { target ->
            when (target) {
                Screen.Audio -> navigate(Screen.Audio)
                Screen.Settings -> openSettings(SettingsLaunchTarget.Home)
                else -> navigate(target)
            }
        }
    }
    fun isPlaybackContextRoute(): Boolean =
        when (playbackContextScreen()) {
            Screen.AlbumDownloader -> screen == Screen.AlbumDownloader
            Screen.PulseRadio -> screen == Screen.PulseRadio
            Screen.YouTube -> screen == Screen.YouTube
            Screen.AlbumDetail -> screen == Screen.AlbumDetail &&
                selectedAlbumKey == ((playbackQueueContext as? PlaybackQueueContext.LocalAlbum)?.albumId ?: playerReturnAlbumKey())
            else -> screen == Screen.AllSongs
        }
    fun openLibraryFromLoop() {
        playbackState.closePlayer()
        if (screen == Screen.AlbumDetail) {
            finishAlbumDetailToGrid()
            finishAlbumsToLibrary()
        } else if (screen == Screen.Albums) {
            finishAlbumsToLibrary()
        } else if (screen != Screen.Library) {
            navigate(Screen.Library, pushHistory = false, suppressMotion = true)
        }
    }
    fun movePlaybackLoop(step: Int) {
        if (!hasPlayableTrack) {
            goBack()
            return
        }
        val currentIndex = when {
            playerOpen -> 2
            screen == Screen.Library -> 0
            isPlaybackContextRoute() -> 1
            isLibraryNavScreen(screen) -> 1
            else -> 0
        }
        when (Math.floorMod(currentIndex + step, 3)) {
            0 -> openLibraryFromLoop()
            1 -> {
                playbackState.closePlayer()
                openPlaybackContext(pushHistory = false, suppressMotion = false)
            }
            2 -> {
                miniDismissed = false
                openPlayer(false)
            }
        }
    }
    fun playYouTubeSource(
        source: YouTubeSource,
        queueIds: List<String> = listOf(source.id),
        albumProjectReleaseId: String? = null,
        forceRefresh: Boolean = false,
        openPlayerOnStart: Boolean = true,
        openPlayerImmediately: Boolean = false,
        recordPlayback: Boolean = true,
    ) {
        val pendingResolveSourceId = resolvingYouTubeSourceId
        if (pendingResolveSourceId != null) {
            val pendingSource = queueYouTubeSource(pendingResolveSourceId)
            val samePendingSource = pendingResolveSourceId == source.id ||
                pendingSource?.url == source.url ||
                pendingSource?.streamDistinctKey() == source.streamDistinctKey()
            if (samePendingSource && !forceRefresh) {
                if (openPlayerImmediately && openPlayerOnStart) {
                    miniDismissed = false
                    openPlayer(false)
                }
                pendingSource?.let { pending ->
                    YouTubeNetworkDiagnostics.reportStreamSession(
                        sessionId = activeYouTubeResolveSessionId,
                        source = pending,
                        event = "duplicate_tap",
                        generation = youtubeResolveGeneration,
                        reason = "same_source_resolving",
                    )
                }
                return
            }
            cancelActivePremiumDeckResolve("superseded", clearPrepareState = true)
        }
        cancelPremiumDeckSearchPreview()
        activeAlbumProjectReleaseId = albumProjectReleaseId
        if (!forceRefresh) streamRecoveryAttempts = streamRecoveryAttempts - source.id
        if (queueIds.size <= 1) {
            transientYouTubeQueueSources = emptyList()
            albumTrackFallbackQueueIds = emptyMap()
        }
        val savedExisting = youtubeSources.firstOrNull { it.id == source.id || it.url == source.url }
        val existing = savedExisting ?: preparedPreviewSourceFor(source)
        val isNewYouTubeSource = savedExisting == null
        val playedAtMillis = System.currentTimeMillis()
        val playbackSource = (existing ?: source).copy(
            id = existing?.id ?: source.id,
            title = source.title.ifBlank { existing?.title.orEmpty() }.ifBlank { PREMIUMDECK_STREAM_TITLE },
            author = source.author.ifBlank { existing?.author.orEmpty() }.ifBlank { PREMIUMDECK_SOURCE_NAME },
            thumbnailUrl = source.bestThumbnailUrl() ?: existing?.bestThumbnailUrl(),
            durationMillis = source.durationMillis.takeIf { it > 0L } ?: existing?.durationMillis ?: 0L,
            quality = source.quality,
            reviewState = YouTubeReviewState.Accepted,
        )
        val offlineDeckUri = playbackSource.downloadedUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (premiumDeckOfflineActive && (offlineDeckUri == null || !isReadableOfflineUri(context, offlineDeckUri))) {
            if (offlineDeckUri != null) {
                updateYouTubeSource(playbackSource.id) {
                    it.copy(
                        downloadedUri = null,
                        downloadState = YouTubeDownloadState.Failed,
                        downloadProgress = 0,
                        status = YouTubeSourceStatus.StreamReady,
                    )
                }
            }
            setPremiumDeckPrepareState(playbackSource.id, PremiumDeckStreamPrepareState.Failed)
            Toast.makeText(context, "Offline Deck can play saved PremiumDeck music only", Toast.LENGTH_SHORT).show()
            return
        }
        val playedSource = playbackSource.copy(
            playCount = playbackSource.playCount + if (forceRefresh || !recordPlayback) 0 else 1,
            lastPlayedMillis = if (!recordPlayback || forceRefresh && playbackSource.lastPlayedMillis > 0L) playbackSource.lastPlayedMillis else playedAtMillis,
            status = if (playbackSource.status == YouTubeSourceStatus.ResolverNeeded) YouTubeSourceStatus.StreamReady else playbackSource.status,
        )
        if (!forceRefresh && recordPlayback) {
            logPersonalization(
                playedSource.toBehaviorEvent(
                    BehaviorEventType.TrackStarted,
                    metadata = mapOf(
                        "queueSize" to queueIds.size.toString(),
                        "quality" to playedSource.quality.name,
                        "source" to "premiumdeck_catalog",
                    ),
                ),
            )
            if (playedSource.playCount > 1) logPersonalization(playedSource.toBehaviorEvent(BehaviorEventType.RepeatPlay))
            if (queueIds.size > 1 && shuffleMode != ShuffleMode.Off) logPersonalization(playedSource.toBehaviorEvent(BehaviorEventType.ShufflePlay))
        }
        activeYouTubeSourceId = playedSource.id
        fallbackYouTubeSource = null
        activeYouTubeQueueIds = queueIds.map { if (it == source.id) playedSource.id else it }.distinct().ifEmpty { listOf(playedSource.id) }
        syncActivePremiumDeckSessionQueue(activeYouTubeQueueIds)
        setPremiumDeckQueueContext(playedSource.id)
        if (openPlayerImmediately && openPlayerOnStart) {
            miniDismissed = false
            openPlayer(false)
        }
        if (recordPlayback || savedExisting != null) {
            saveYouTubeShelf(listOf(playedSource) + youtubeSources.filterNot { it.id == playedSource.id || it.url == playedSource.url })
        } else {
            transientYouTubeQueueSources = (listOf(playedSource) + transientYouTubeQueueSources)
                .distinctBy { it.url.ifBlank { it.id } }
        }
        val streamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
        val streamSessionId = YouTubeNetworkDiagnostics.nextStreamSessionId()
        val streamStartedAtMillis = SystemClock.elapsedRealtime()
        YouTubeNetworkDiagnostics.reportStreamSession(
            sessionId = streamSessionId,
            source = playedSource,
            event = "start",
            policy = streamPolicy,
            queueSize = queueIds.size,
            generation = youtubeResolveGeneration,
            reason = if (forceRefresh) "refresh" else "playback",
        )
        val downloadedUri = playedSource.downloadedUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (downloadedUri != null) {
            if (isReadableOfflineUri(context, downloadedUri)) {
                setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.Ready)
                fallbackYouTubeSource = null
                activeStreamTrack = playedSource.toDownloadedTrack(downloadedUri)
                activeAlbumKey = null
                clearPlaybackHistory()
                resetDisplayedPlaybackPosition()
                val resume = if (playedSource.isPodcast) playedSource.playbackPositionMillis else 0L
                if (resume > 0L) {
                    requestPlaybackSeek(resume)
                }
                if (recordPlayback) saveLastPlaybackState(context, LastPlaybackState(kind = "youtube", youtubeSourceId = playedSource.id, positionMillis = resume))
                playbackState.closePlayer()
                if (openPlayerOnStart) openPlayer(false)
                playing = true
                if (!forceRefresh && recordPlayback) recordStreamPlay(playedSource, playedAtMillis)
                YouTubeNetworkDiagnostics.reportStreamSession(
                    sessionId = streamSessionId,
                    source = playedSource,
                    event = "offline_ready",
                    policy = streamPolicy,
                    queueSize = queueIds.size,
                    generation = youtubeResolveGeneration,
                    durationMillis = SystemClock.elapsedRealtime() - streamStartedAtMillis,
                    reason = "downloaded",
                )
                prepareUpcomingPremiumDeckStreams()
                return
            }
            updateYouTubeSource(playedSource.id) {
                it.copy(
                    downloadedUri = null,
                    downloadState = YouTubeDownloadState.Failed,
                    downloadProgress = 0,
                    status = YouTubeSourceStatus.StreamReady,
                )
            }
            if (premiumDeckOfflineActive) {
                setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.Failed)
                Toast.makeText(context, "Saved file is missing. Turn off Offline Deck to stream or save again.", Toast.LENGTH_LONG).show()
                return
            } else {
                Toast.makeText(context, "Saved file is missing. Streaming instead; tap Offline to retry.", Toast.LENGTH_LONG).show()
            }
        }
        if (premiumDeckOfflineActive) {
            setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.Failed)
            Toast.makeText(context, "Offline Deck can play saved PremiumDeck music only", Toast.LENGTH_SHORT).show()
            return
        }
        val cacheCheck = if (forceRefresh) {
            StreamCacheCheck(StreamCacheDecision.PolicyBypass)
        } else {
            playedSource.cachedStreamUrlCheck(policy = streamPolicy)
        }
        val cachedStreamUrl = cacheCheck.url
        if (cachedStreamUrl != null) {
            setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.Ready)
            fallbackYouTubeSource = null
            val cachedResolved = playedSource.toCachedResolvedAudio(cachedStreamUrl)
            activeStreamTrack = cachedResolved.toTrack(playedSource)
            activeAlbumKey = null
            clearPlaybackHistory()
            resetDisplayedPlaybackPosition()
            if (recordPlayback) saveLastPlaybackState(context, LastPlaybackState(kind = "youtube", youtubeSourceId = playedSource.id))
            playbackState.closePlayer()
            if (openPlayerOnStart) openPlayer(false)
            playing = true
            if (!forceRefresh && recordPlayback) recordStreamPlay(playedSource, playedAtMillis)
            YouTubeNetworkDiagnostics.reportStreamSession(
                sessionId = streamSessionId,
                source = playedSource,
                event = "cache_hit",
                policy = streamPolicy,
                queueSize = queueIds.size,
                generation = youtubeResolveGeneration,
                cached = true,
                cacheDecision = cacheCheck.decision,
                durationMillis = SystemClock.elapsedRealtime() - streamStartedAtMillis,
                resolved = cachedResolved,
                reason = "signed_url",
            )
            prepareUpcomingPremiumDeckStreams()
            return
        }
        setPremiumDeckPrepareState(playedSource.id, if (forceRefresh) PremiumDeckStreamPrepareState.Refreshing else PremiumDeckStreamPrepareState.Preparing)
        resolvingYouTubeSourceId = playedSource.id
        youtubeResolveGeneration += 1L
        val resolveGeneration = youtubeResolveGeneration
        activeYouTubeResolveSessionId = streamSessionId
        YouTubeNetworkDiagnostics.reportStreamSession(
            sessionId = streamSessionId,
            source = playedSource,
            event = "resolve_start",
            policy = streamPolicy,
            queueSize = queueIds.size,
            generation = resolveGeneration,
            cacheDecision = cacheCheck.decision,
            reason = if (forceRefresh) "refresh" else "cache_miss",
        )
        val resolveJob = appScope.launch {
            try {
                val resolved = resolveYouTubeAudio(context, playedSource, streamPolicy)
                if (resolveGeneration == youtubeResolveGeneration) {
                    resolvingYouTubeSourceId = null
                    activeYouTubeResolveSessionId = 0L
                    premiumDeckStreamResolveJob.compareAndSet(coroutineContext[Job], null)
                }
                if (resolveGeneration != youtubeResolveGeneration || activeYouTubeSourceId != playedSource.id) {
                    if (resolved != null) {
                        cachePreparedYouTubeSource(playedSource, resolved)
                        setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.Ready)
                    }
                    YouTubeNetworkDiagnostics.reportStreamSession(
                        sessionId = streamSessionId,
                        source = playedSource,
                        event = if (resolved != null) "stale_resolved_cached" else "stale_discarded",
                        policy = streamPolicy,
                        queueSize = queueIds.size,
                        generation = resolveGeneration,
                        cacheDecision = cacheCheck.decision,
                        durationMillis = SystemClock.elapsedRealtime() - streamStartedAtMillis,
                        resolved = resolved,
                        reason = "superseded",
                    )
                    return@launch
                }
                if (resolved != null) {
                    setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.Ready)
                    fallbackYouTubeSource = null
                    activeStreamTrack = resolved.toTrack(playedSource)
                    activeAlbumKey = null
                    clearPlaybackHistory()
                    resetDisplayedPlaybackPosition()
                    if (recordPlayback) saveLastPlaybackState(context, LastPlaybackState(kind = "youtube", youtubeSourceId = playedSource.id))
                    playbackState.closePlayer()
                    if (openPlayerOnStart) openPlayer(false)
                    playing = true
                    if (!forceRefresh && recordPlayback) recordStreamPlay(playedSource, playedAtMillis)
                    cachePreparedYouTubeSource(playedSource, resolved)
                    YouTubeNetworkDiagnostics.reportStreamSession(
                        sessionId = streamSessionId,
                        source = playedSource,
                        event = "resolved_ready",
                        policy = streamPolicy,
                        queueSize = queueIds.size,
                        generation = resolveGeneration,
                        cacheDecision = cacheCheck.decision,
                        durationMillis = SystemClock.elapsedRealtime() - streamStartedAtMillis,
                        resolved = resolved,
                        reason = "track_ready",
                    )
                    prepareUpcomingPremiumDeckStreams()
                    if (pulseSettingsState.misc.online.sponsorBlock && !premiumDeckOfflineActive && resolved.sponsorSegments.isEmpty() && playedSource.sponsorSegments.isEmpty()) {
                        val sponsorVideoId = extractVideoIdForSponsorBlock(playedSource.url)
                        trackPremiumDeckNetworkJob(appScope.launch {
                            val segments = withContext(Dispatchers.IO) { fetchSponsorBlockSegments(sponsorVideoId) }
                            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) return@launch
                            if (segments.isNotEmpty()) {
                                updateYouTubeSource(playedSource.id) { it.copy(sponsorSegments = segments) }
                            }
                        })
                    }
                    if (recordPlayback && playedSource.playCount >= 3 && playedSource.status != YouTubeSourceStatus.Downloaded && !playedSource.neverPromptCache) {
                        smartCachePromptSource = youtubeSources.firstOrNull { it.id == playedSource.id } ?: playedSource
                    }
                } else {
                    fallbackYouTubeSource = null
                    YouTubeNetworkDiagnostics.reportStreamSession(
                        sessionId = streamSessionId,
                        source = playedSource,
                        event = "resolve_failed",
                        policy = streamPolicy,
                        queueSize = queueIds.size,
                        generation = resolveGeneration,
                        cacheDecision = cacheCheck.decision,
                        durationMillis = SystemClock.elapsedRealtime() - streamStartedAtMillis,
                        reason = "no_playable_stream",
                    )
                    if (isNewYouTubeSource && recordPlayback) {
                        saveYouTubeShelf(youtubeSources.filterNot { it.id == playedSource.id || it.url == playedSource.url })
                    } else {
                        updateYouTubeSource(playedSource.id) { it.copy(status = YouTubeSourceStatus.ResolverNeeded) }
                    }
                    if (activeYouTubeSourceId == playedSource.id) {
                        val activeStreamUrl = activeStreamTrack?.displayName
                        activeYouTubeSourceId = youtubeSources.firstOrNull { it.url == activeStreamUrl }?.id
                    }
                    val albumFallbackIds = albumTrackFallbackQueueIds[playedSource.id].orEmpty()
                    val albumFallback = albumFallbackIds.firstNotNullOfOrNull { id -> queueYouTubeSource(id) }
                    if (albumFallback != null) {
                        setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.BackupReady)
                        val remainingFallbackIds = albumFallbackIds.dropWhile { it != albumFallback.id }.drop(1)
                        albumTrackFallbackQueueIds = (albumTrackFallbackQueueIds - playedSource.id).let { fallbacks ->
                            if (remainingFallbackIds.isEmpty()) fallbacks else fallbacks + (albumFallback.id to remainingFallbackIds)
                        }
                        val adjustedQueueIds = queueIds.map { if (it == playedSource.id) albumFallback.id else it }.distinct()
                        playYouTubeSource(
                            albumFallback,
                            adjustedQueueIds,
                            albumProjectReleaseId = activeAlbumProjectReleaseId,
                            openPlayerOnStart = openPlayerOnStart,
                            recordPlayback = recordPlayback,
                        )
                        return@launch
                    }
                    val fallbackNext = nextQueuedYouTubeSourceAfter(playedSource.id, queueIds)
                    if (fallbackNext != null) {
                        setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.Failed)
                        playYouTubeSource(
                            fallbackNext,
                            queueIds,
                            albumProjectReleaseId = activeAlbumProjectReleaseId,
                            openPlayerOnStart = openPlayerOnStart,
                            recordPlayback = recordPlayback,
                        )
                        return@launch
                    }
                    setPremiumDeckPrepareState(playedSource.id, PremiumDeckStreamPrepareState.Failed)
                    Toast.makeText(context, "Could not resolve in-app audio for this result. Try another version or refresh search.", Toast.LENGTH_LONG).show()
                }
            } catch (cancellation: CancellationException) {
                YouTubeNetworkDiagnostics.reportStreamSession(
                    sessionId = streamSessionId,
                    source = playedSource,
                    event = "cancelled",
                    policy = streamPolicy,
                    queueSize = queueIds.size,
                    generation = resolveGeneration,
                    cacheDecision = cacheCheck.decision,
                    durationMillis = SystemClock.elapsedRealtime() - streamStartedAtMillis,
                    reason = "job_cancelled",
                )
                throw cancellation
            }
        }
        premiumDeckStreamResolveJob.getAndSet(resolveJob)?.cancel()
    }
    playYouTubeSourceFromQueue = { source, queueIds -> playYouTubeSource(source, queueIds, albumProjectReleaseId = activeAlbumProjectReleaseId) }
    fun playYouTubeSourceQueue(
        source: YouTubeSource,
        queueSources: List<YouTubeSource>,
        albumProjectReleaseId: String? = null,
        playbackOrigin: PremiumDeckPlaybackOrigin = PremiumDeckPlaybackOrigin.ManualSource,
        playbackTitle: String = "",
        playbackSubtitle: String = "",
        playbackSearchQuery: String = "",
        playbackOriginKey: String = "",
        playbackReason: PremiumDeckQueueReason? = null,
        openPlayerOnStart: Boolean = true,
        openPlayerImmediately: Boolean = false,
        recordPlayback: Boolean = true,
    ) {
        val playableQueue = (queueSources + source)
            .filter { it.kind == YouTubeSourceKind.Video }
            .filter { !premiumDeckOfflineActive || it.isOfflineSaved() }
            .map { queued ->
                val prepared = preparedPreviewSourceFor(queued)
                val base = prepared ?: queued
                base.copy(
                    title = queued.title.ifBlank { base.title }.ifBlank { PREMIUMDECK_STREAM_TITLE },
                    author = queued.author.ifBlank { base.author }.ifBlank { PREMIUMDECK_SOURCE_NAME },
                    reviewState = YouTubeReviewState.Accepted,
                    thumbnailUrl = queued.bestThumbnailUrl() ?: base.bestThumbnailUrl() ?: base.thumbnailUrl,
                    durationMillis = queued.durationMillis.takeIf { it > 0L } ?: base.durationMillis,
                    quality = queued.quality,
                )
            }
            .distinctBy { it.url.ifBlank { it.id } }
        if (premiumDeckOfflineActive && playableQueue.isEmpty()) {
            Toast.makeText(context, "Offline Deck has no saved tracks in this queue", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = playableQueue.firstOrNull { it.id == source.id || it.url == source.url }
            ?: playableQueue.firstOrNull()
            ?: (preparedPreviewSourceFor(source) ?: source).copy(
                title = source.title.ifBlank { PREMIUMDECK_STREAM_TITLE },
                author = source.author.ifBlank { PREMIUMDECK_SOURCE_NAME },
                reviewState = YouTubeReviewState.Accepted,
                thumbnailUrl = source.bestThumbnailUrl() ?: preparedPreviewSourceFor(source)?.bestThumbnailUrl() ?: source.thumbnailUrl,
                durationMillis = source.durationMillis.takeIf { it > 0L } ?: preparedPreviewSourceFor(source)?.durationMillis ?: 0L,
            )
        val queue = playableQueue.ifEmpty { listOf(selected) }
        transientYouTubeQueueSources = queue
        albumTrackFallbackQueueIds = emptyMap()
        activePremiumDeckSession = buildPremiumDeckPlaybackSession(
            origin = playbackOrigin,
            sources = queue,
            startedSource = selected,
            title = playbackTitle,
            subtitle = playbackSubtitle,
            searchQuery = playbackSearchQuery,
            originKey = playbackOriginKey,
            reason = playbackReason,
        )
        premiumDeckPrepareStates = emptyMap()
        premiumDeckPrepareGeneration += 1L
        playYouTubeSource(
            selected,
            queue.map { it.id },
            albumProjectReleaseId = albumProjectReleaseId,
            openPlayerOnStart = openPlayerOnStart,
            openPlayerImmediately = openPlayerImmediately,
            recordPlayback = recordPlayback,
        )
    }
    fun loadPlayerArtistContinuation(
        artist: ArtistCandidate,
        activeSource: YouTubeSource?,
        sourceMode: ArtistWorksSourceMode,
        fetchPolicy: ArtistWorksFetchPolicy,
        userConfirmedOnline: Boolean,
    ) {
        val artistKey = artist.normalizedName
        if (artistKey.isBlank()) {
            Toast.makeText(context, "Artist identity unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        val localConfirmedLookup = sourceMode == ArtistWorksSourceMode.LocalFile &&
            fetchPolicy == ArtistWorksFetchPolicy.UserConfirmationRequired &&
            userConfirmedOnline
        val sourceBlockMessage = when (fetchPolicy) {
            ArtistWorksFetchPolicy.OfflineOnly -> when (sourceMode) {
                ArtistWorksSourceMode.PremiumDeckOffline -> "Offline Deck is on. Artist lookup is blocked."
                ArtistWorksSourceMode.PulseRadio -> "PulseRadio artist lookup is blocked."
                ArtistWorksSourceMode.LocalFile -> "Online artist discovery for local tracks is off for this network."
                else -> "Artist lookup is offline-only for this source."
            }
            ArtistWorksFetchPolicy.AutoShowCachedOnly -> "This source only shows cached artist works."
            ArtistWorksFetchPolicy.BlockedBetaLocked -> "Artist lookup is locked in this beta state."
            ArtistWorksFetchPolicy.UserConfirmationRequired -> if (localConfirmedLookup) null else "Confirm online artist lookup for this local file."
            ArtistWorksFetchPolicy.UserTriggeredOnlineAllowed -> null
        }
        if (sourceBlockMessage != null) {
            artistContinuationFetchStates = artistContinuationFetchStates + (
                artistKey to ArtistContinuationFetchState(
                    artistKey = artistKey,
                    loaded = true,
                    message = sourceBlockMessage,
                    loadedAtEpochMs = System.currentTimeMillis(),
                )
                )
            Toast.makeText(context, sourceBlockMessage, Toast.LENGTH_SHORT).show()
            return
        }
        if (!localConfirmedLookup && !artist.confidence.playerArtistContinuationAccepted()) {
            val nextState = ArtistContinuationFetchState(
                artistKey = artistKey,
                loaded = true,
                message = "Artist identity needs a stronger source before online lookup.",
                loadedAtEpochMs = System.currentTimeMillis(),
            )
            artistContinuationFetchStates = artistContinuationFetchStates + (artistKey to nextState)
            return
        }
        val policy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
        val blockMessage = when {
            premiumDeckOfflineActive -> "Offline Deck is on. Artist lookup is blocked."
            policy.network.isNoNetwork -> "No network detected. Artist lookup is blocked."
            policy.effectiveDataSaver -> "Data Saver is on. Artist lookup is blocked."
            else -> null
        }
        if (blockMessage != null) {
            artistContinuationFetchStates = artistContinuationFetchStates + (
                artistKey to ArtistContinuationFetchState(
                    artistKey = artistKey,
                    loaded = true,
                    message = blockMessage,
                    loadedAtEpochMs = System.currentTimeMillis(),
                )
                )
            Toast.makeText(context, blockMessage, Toast.LENGTH_SHORT).show()
            return
        }
        artistContinuationFetchStates = artistContinuationFetchStates + (
            artistKey to (artistContinuationFetchStates[artistKey] ?: ArtistContinuationFetchState(artistKey = artistKey)).copy(
                loading = true,
                message = null,
            )
            )
        trackPremiumDeckNetworkJob(appScope.launch {
            val identity = artistDiscographyProvider.resolveArtist(
                query = artist,
                allowUnverified = localConfirmedLookup,
            )
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                artistContinuationFetchStates = artistContinuationFetchStates + (
                    artistKey to ArtistContinuationFetchState(
                        artistKey = artistKey,
                        loading = false,
                        loaded = true,
                        message = "Offline Deck turned on. Artist lookup stopped.",
                        loadedAtEpochMs = System.currentTimeMillis(),
                    )
                    )
                return@launch
            }
            if (identity == null) {
                artistContinuationFetchStates = artistContinuationFetchStates + (
                    artistKey to ArtistContinuationFetchState(
                        artistKey = artistKey,
                        loading = false,
                        loaded = true,
                        message = "Artist identity needs a stronger source before catalog lookup.",
                        loadedAtEpochMs = System.currentTimeMillis(),
                    )
                    )
                return@launch
            }
            val snapshot = runCatching {
                artistDiscographyProvider.getArtistSnapshot(identity)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                ArtistDiscographySnapshot(
                    identity = identity,
                    recentTracks = emptyList(),
                    topTracks = emptyList(),
                    releases = emptyList(),
                )
            }
            val activeSourceKey = activeSource?.streamDistinctKey().orEmpty()
            val activeSourceId = activeSource?.id.orEmpty()
            val sources = (
                snapshot.recentTracks +
                    snapshot.topTracks +
                    snapshot.releases.flatMap { release -> release.items.map { it.toArtistContinuationSource() } }
                )
                .filterNot { item ->
                    val key = item.source.streamDistinctKey()
                    (activeSourceId.isNotBlank() && item.source.id == activeSourceId) ||
                        (activeSourceKey.isNotBlank() && key == activeSourceKey)
                }
                .distinctBy { it.source.streamDistinctKey() }
                .take(24)
            artistContinuationFetchStates = artistContinuationFetchStates + (
                artistKey to ArtistContinuationFetchState(
                    artistKey = artistKey,
                    sources = sources,
                    loading = false,
                    loaded = true,
                    catalogIdentity = snapshot.identity,
                    discographyReleases = snapshot.releases,
                    loadedAtEpochMs = System.currentTimeMillis(),
                    fromCache = snapshot.fromCache,
                    message = when {
                        snapshot.releases.isNotEmpty() -> "Loaded artist discography on demand. Tracklists match only when opened."
                        sources.isNotEmpty() -> "Loaded artist songs on demand. No albums found yet."
                        else -> "No artist catalog results found."
                    },
                )
                )
        })
    }

    fun artistReleaseBuilderId(release: ArtistReleaseGroup): String =
        release.id.ifBlank { "artist-release-${release.artist}-${release.title}".normalizedSearchText() }

    fun artistReleasePreviewTracks(release: ArtistReleaseGroup): List<AlbumDownloadTrack> {
        if (release.tracklistPreview.isNotEmpty()) {
            return release.tracklistPreview
                .sortedBy { it.position.takeIf { position -> position > 0 } ?: Int.MAX_VALUE }
                .mapIndexed { index, track ->
                    val position = track.position.takeIf { it > 0 } ?: index + 1
                    val matched = track.matchedSource?.let { matchedSource ->
                        matchedSource.copy(
                            reviewState = YouTubeReviewState.Accepted,
                            thumbnailUrl = matchedSource.bestThumbnailUrl() ?: matchedSource.thumbnailUrl,
                            albumTitleHint = release.title,
                            albumTrackNumberHint = matchedSource.albumTrackNumberHint.takeIf { it > 0 } ?: position,
                            albumTrackTotalHint = release.trackCountHint.takeIf { it > 0 } ?: release.tracklistPreview.size,
                            albumYearHint = release.releaseYearHint.takeIf { it > 0 } ?: matchedSource.albumYearHint,
                        )
                    }
                    track.copy(
                        position = position,
                        matchedSource = matched,
                        matchCandidates = (track.matchCandidates + listOfNotNull(matched))
                            .distinctBy { it.streamDistinctKey() }
                            .take(5),
                    )
                }
        }
        return release.items
            .mapIndexed { index, item ->
                val source = item.source.copy(
                    reviewState = YouTubeReviewState.Accepted,
                    thumbnailUrl = item.source.bestThumbnailUrl() ?: item.source.thumbnailUrl,
                    albumTitleHint = release.title,
                    albumTrackNumberHint = item.source.albumTrackNumberHint.takeIf { it > 0 } ?: index + 1,
                    albumTrackTotalHint = release.trackCountHint.takeIf { it > 0 } ?: release.items.size,
                    albumYearHint = release.releaseYearHint.takeIf { it > 0 } ?: item.source.albumYearHint,
                )
                AlbumDownloadTrack(
                    position = index + 1,
                    title = source.title.cleanStreamTitle(),
                    durationMillis = source.durationMillis,
                    recordingId = source.id,
                    source = "Artist Discography preview",
                    matchedSource = source,
                    matchScore = item.resultScore,
                    matchReason = item.reason.ifBlank { "Artist release preview" },
                    matchVerified = false,
                    matchCandidates = listOf(source),
                )
            }
            .distinctBy { it.recordingId.ifBlank { "${it.position}|${it.title}".normalizedSearchText() } }
            .take(24)
    }

    fun ArtistReleaseGroup.toAlbumBuilderRelease(): AlbumDownloadRelease {
        val tracks = artistReleasePreviewTracks(this)
        return AlbumDownloadRelease(
            id = artistReleaseBuilderId(this),
            title = title,
            artist = artist,
            date = releaseYearHint.takeIf { it > 0 }?.toString().orEmpty(),
            format = releaseType.label,
            trackCount = trackCountHint.takeIf { it > 0 } ?: tracks.size,
            tracks = tracks,
            coverUrl = coverUrl.orEmpty(),
            source = "Artist Discography",
            downloadQuality = "Match on demand",
            score = confidence.discographyRank * 25,
        )
    }

    fun ArtistReleaseTracklist.toAlbumBuilderRelease(seed: ArtistReleaseGroup, fallback: AlbumDownloadRelease): AlbumDownloadRelease {
        val total = tracks.size.takeIf { it > 0 } ?: fallback.trackCount
        val rows = tracks
            .mapIndexed { index, item ->
                val source = item.source.copy(
                    reviewState = YouTubeReviewState.Accepted,
                    thumbnailUrl = item.source.bestThumbnailUrl() ?: item.source.thumbnailUrl,
                    albumTitleHint = seed.title,
                    albumTrackNumberHint = item.source.albumTrackNumberHint.takeIf { it > 0 } ?: index + 1,
                    albumTrackTotalHint = total,
                    albumYearHint = seed.releaseYearHint.takeIf { it > 0 } ?: item.source.albumYearHint,
                )
                AlbumDownloadTrack(
                    position = index + 1,
                    title = source.title.cleanStreamTitle(),
                    durationMillis = source.durationMillis,
                    recordingId = source.id,
                    source = "Artist Discography lazy match",
                    matchedSource = source,
                    matchScore = item.resultScore,
                    matchReason = item.reason.ifBlank { "Lazy matched artist release" },
                    matchVerified = false,
                    matchCandidates = listOf(source),
                )
            }
            .distinctBy { it.recordingId.ifBlank { "${it.position}|${it.title}".normalizedSearchText() } }
        return fallback.copy(
            title = title.ifBlank { fallback.title },
            artist = artist.ifBlank { fallback.artist },
            format = releaseType.label,
            trackCount = total,
            tracks = rows.ifEmpty { fallback.tracks },
            source = "Artist Discography",
            downloadQuality = if (rows.isEmpty()) fallback.downloadQuality else "Lazy matched",
        )
    }

    fun AlbumDownloadRelease.hasCompleteArtistReleaseTracklist(seed: ArtistReleaseGroup): Boolean {
        if (tracks.isEmpty()) return false
        val expected = seed.trackCountHint.takeIf { it > 0 } ?: seed.tracklistPreview.size
        val hasMetadataRows = tracks.count { it.matchedSource == null && it.title.isNotBlank() } >= 2
        return hasMetadataRows || expected <= 0 || tracks.size >= expected
    }

    fun openArtistReleaseTracklist(release: ArtistReleaseGroup) {
        val seedRelease = release.toAlbumBuilderRelease()
        val cachedRelease = albumDownloadDrafts.firstOrNull { it.release.id == seedRelease.id }?.release
        val initialRelease = cachedRelease ?: seedRelease
        saveAlbumDownloadDraftSilently(initialRelease)
        youtubeDialog = null
        playbackState.closePlayer()
        openAlbumBuilder(initialRelease.id)

        val policy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
        val blockMessage = when {
            premiumDeckOfflineActive -> "Offline Deck is on. Showing loaded album data only."
            policy.network.isNoNetwork -> "No network detected. Showing loaded album data only."
            policy.effectiveDataSaver -> "Data Saver is on. Showing loaded album data only."
            else -> null
        }
        if (cachedRelease?.hasCompleteArtistReleaseTracklist(release) == true || release.tracklistPreview.isNotEmpty()) {
            return
        }
        if (blockMessage != null) {
            Toast.makeText(context, blockMessage, Toast.LENGTH_SHORT).show()
            return
        }
        updateAlbumProjectTask(
            AlbumProjectTaskState(
                releaseId = seedRelease.id,
                title = seedRelease.title,
                artist = seedRelease.artist,
                phase = AlbumProjectTaskPhase.TracklistLoading,
                total = seedRelease.trackCount.takeIf { it > 0 } ?: 1,
                completed = if (seedRelease.tracks.isNotEmpty()) 1 else 0,
                message = "Preparing tracklist in Album Builder",
            ),
        )
        trackPremiumDeckNetworkJob(appScope.launch {
            val metadataRelease = runCatching {
                searchAlbumMetadataReleases(seedRelease.artist, seedRelease.title, youtubeSources)
                    .firstOrNull { candidate ->
                        candidate.title.normalizedSearchText() == seedRelease.title.normalizedSearchText() ||
                            candidate.title.normalizedSearchText().contains(seedRelease.title.normalizedSearchText()) ||
                            seedRelease.title.normalizedSearchText().contains(candidate.title.normalizedSearchText())
                    }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                null
            }
            val updatedFromMetadata = metadataRelease
                ?.takeIf { it.tracks.isNotEmpty() }
                ?.let { metadata ->
                    metadata.copy(
                        id = seedRelease.id,
                        title = metadata.title.ifBlank { seedRelease.title },
                        artist = metadata.artist.ifBlank { seedRelease.artist },
                        format = metadata.format.ifBlank { seedRelease.format },
                        coverUrl = metadata.coverUrl.ifBlank { seedRelease.coverUrl },
                        source = "Artist Discography",
                        downloadQuality = "Tracklist loaded",
                        score = metadata.score.coerceAtLeast(seedRelease.score),
                    )
                }
            val updatedRelease = updatedFromMetadata ?: run {
                val tracklist = if (release.id.isNotBlank()) {
                    runCatching { artistDiscographyProvider.getReleaseTracklist(release.id) }
                        .getOrElse { error ->
                            if (error is CancellationException) throw error
                            ArtistReleaseTracklist(
                                releaseId = release.id,
                                title = release.title,
                                artist = release.artist,
                                releaseType = release.releaseType,
                                tracks = emptyList(),
                            )
                        }
                } else {
                    ArtistReleaseTracklist(
                        releaseId = seedRelease.id,
                        title = seedRelease.title,
                        artist = seedRelease.artist,
                        releaseType = release.releaseType,
                        tracks = emptyList(),
                    )
                }
                tracklist.toAlbumBuilderRelease(release, seedRelease)
            }
            saveAlbumDownloadDraftSilently(updatedRelease)
            updateAlbumProjectTask(
                AlbumProjectTaskState(
                    releaseId = updatedRelease.id,
                    title = updatedRelease.title,
                    artist = updatedRelease.artist,
                    phase = AlbumProjectTaskPhase.TracklistLoading,
                    total = updatedRelease.trackCount.takeIf { it > 0 } ?: updatedRelease.tracks.size.coerceAtLeast(1),
                    completed = updatedRelease.tracks.size.coerceAtLeast(1),
                    message = if (updatedRelease.tracks.isEmpty()) {
                        "Tracklist unavailable; showing album metadata"
                    } else {
                        "Tracklist loaded"
                    },
                ),
            )
            delay(1_600L)
            if (albumProjectTasks[updatedRelease.id]?.phase == AlbumProjectTaskPhase.TracklistLoading) {
                clearAlbumProjectTask(updatedRelease.id)
            }
        })
    }

    fun premiumDeckDiscoveryPreviewStartMillis(source: YouTubeSource): Long {
        val duration = source.durationMillis.takeIf { it > 0L } ?: 0L
        val base = when {
            duration >= 4L * 60L * 1000L -> (duration * 0.38f).toLong()
            duration >= 2L * 60L * 1000L -> (duration * 0.44f).toLong()
            duration > 0L -> (duration * 0.35f).toLong()
            else -> 30_000L
        }
        val bounded = if (duration > 0L) {
            base.coerceIn(18_000L, (duration - 24_000L).coerceAtLeast(18_000L))
        } else {
            base
        }
        val offTopic = source.sponsorSegments
            .filter { it.category.equals("music_offtopic", ignoreCase = true) }
            .firstOrNull { bounded in it.startMillis..it.endMillis || (it.startMillis <= 1_000L && bounded < it.endMillis) }
        return ((offTopic?.endMillis ?: bounded) + if (offTopic != null) 250L else 0L).coerceAtLeast(0L)
    }

    fun rememberedSearchDiscoveryPreviewStartMillis(source: YouTubeSource): Long {
        val latest = preparedPreviewSourceFor(source) ?: source
        val key = latest.streamDistinctKey().ifBlank { latest.id }
        searchDiscoveryPreviewStartPositions[key]?.let { return it }
        val startMillis = premiumDeckDiscoveryPreviewStartMillis(latest)
        val retained = searchDiscoveryPreviewStartPositions.entries
            .toList()
            .takeLast(47)
            .associate { entry -> entry.key to entry.value }
        searchDiscoveryPreviewStartPositions = retained + (key to startMillis)
        return startMillis
    }

    fun stopSearchDiscoveryPreviewPlayback() {
        val session = activePremiumDeckSession ?: return
        if (session.origin != PremiumDeckPlaybackOrigin.SearchDiscoveryPreview) return
        playing = false
        activeYouTubeSourceId = null
        fallbackYouTubeSource = null
        cancelActivePremiumDeckResolve("preview_stop", clearPrepareState = true)
        activeStreamTrack = null
        activeYouTubeQueueIds = emptyList()
        transientYouTubeQueueSources = emptyList()
        activePremiumDeckSession = null
        premiumDeckPrepareGeneration += 1L
        playbackState.closePlayer()
        resetDisplayedPlaybackPosition()
    }

    fun prepareSearchDiscoveryPreviewSources(sources: List<YouTubeSource>, anchorIndex: Int = 0) {
        if (premiumDeckOfflineActive) return
        val streamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
        if (!streamPolicy.allowPreviewPreparation) return
        val previewSources = sources
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked && !it.isPodcast }
            .distinctBy { it.streamDistinctKey() }
        val warmSources = previewSources
            .drop(anchorIndex.coerceAtLeast(0))
            .take(3)
        if (warmSources.isEmpty()) return
        val generation = searchDiscoveryGeneration
        val visibleIds = warmSources.map { it.id }.toSet()
        val activeQueueIds = activeYouTubeQueueIds.toSet()
        premiumDeckPrepareStates = premiumDeckPrepareStates.filterKeys { it in visibleIds || it in activeQueueIds }
        trackPremiumDeckNetworkJob(appScope.launch {
            for (source in warmSources) {
                if (generation != searchDiscoveryGeneration) return@launch
                val latest = preparedPreviewSourceFor(source) ?: source
                val downloadedUri = latest.downloadedUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if ((downloadedUri != null && isReadableOfflineUriOnIo(downloadedUri)) || latest.freshCachedStreamUrl(policy = streamPolicy) != null) {
                    setPremiumDeckPrepareState(source.id, PremiumDeckStreamPrepareState.Ready)
                    continue
                }
                setPremiumDeckPrepareState(source.id, PremiumDeckStreamPrepareState.Preparing)
                val resolved = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(4_800L) { resolveYouTubeAudio(context, latest, streamPolicy) }
                }
                if (generation != searchDiscoveryGeneration) return@launch
                if (resolved != null) {
                    cachePreparedYouTubeSource(latest, resolved)
                    setPremiumDeckPrepareState(source.id, PremiumDeckStreamPrepareState.Ready)
                } else {
                    setPremiumDeckPrepareState(source.id, PremiumDeckStreamPrepareState.Idle)
                }
            }
        })
    }

    fun prepareSearchDiscoveryGenre(genre: StreamDiscoveryGenre) {
        if (premiumDeckOfflineActive) return
        val collectionId = "search-discovery-${genre.id}"
        val existing = searchDiscoveryGenreCollections.firstOrNull { it.id == collectionId }
        if (existing?.sources?.isNotEmpty() == true || searchDiscoveryLoadingGenreId == genre.id) return
        searchDiscoveryLoadingGenreId = genre.id
        searchDiscoveryGeneration += 1L
        val generation = searchDiscoveryGeneration
        trackPremiumDeckNetworkJob(appScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                fetchSearchDiscoveryGenreCollection(genre, youtubeSources)
            }
            if (generation != searchDiscoveryGeneration) return@launch
            searchDiscoveryLoadingGenreId = null
            if (fresh.sources.isNotEmpty()) {
                searchDiscoveryGenreCollections = (listOf(fresh) + searchDiscoveryGenreCollections.filterNot { it.id == fresh.id })
                    .take(premiumDeckSearchDiscoveryGenres.size)
                prepareSearchDiscoveryPreviewSources(fresh.sources, anchorIndex = 0)
            }
        })
    }

    fun openSearchDiscoveryGenre(genre: StreamDiscoveryGenre) {
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Offline Deck is on", Toast.LENGTH_SHORT).show()
            return
        }
        searchDiscoveryLoadingGenreId = genre.id
        searchDiscoveryPreviewCollection = null
        searchDiscoveryGeneration += 1L
        val generation = searchDiscoveryGeneration
        trackPremiumDeckNetworkJob(appScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                fetchSearchDiscoveryGenreCollection(genre, youtubeSources)
            }
            if (generation != searchDiscoveryGeneration) return@launch
            if (fresh.sources.isNotEmpty()) {
                val firstSource = fresh.sources.first()
                val latestFirstSource = preparedPreviewSourceFor(firstSource) ?: firstSource
                val streamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
                val cachedFirstStreamUrl = latestFirstSource.freshCachedStreamUrl(policy = streamPolicy)
                val firstResolved = withContext(Dispatchers.IO) {
                    cachedFirstStreamUrl?.let { latestFirstSource.toCachedResolvedAudio(it) }
                        ?: withTimeoutOrNull(4_800L) { resolveYouTubeAudio(context, latestFirstSource, streamPolicy) }
                }
                if (generation != searchDiscoveryGeneration) return@launch
                if (firstResolved != null) {
                    cachePreparedYouTubeSource(firstSource, firstResolved)
                    setPremiumDeckPrepareState(firstSource.id, PremiumDeckStreamPrepareState.Ready)
                }
                searchDiscoveryLoadingGenreId = null
                searchDiscoveryGenreCollections = (listOf(fresh) + searchDiscoveryGenreCollections.filterNot { it.id == fresh.id })
                    .take(premiumDeckSearchDiscoveryGenres.size)
                searchDiscoveryPreviewCollection = fresh
                prepareSearchDiscoveryPreviewSources(fresh.sources, anchorIndex = 1)
            } else {
                searchDiscoveryLoadingGenreId = null
                Toast.makeText(context, "No ${genre.title} previews found", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun playSearchDiscoveryPreview(collection: StreamCollection, source: YouTubeSource) {
        val queue = collection.sources
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
            .distinctBy { it.streamDistinctKey() }
        val selectedIndex = queue.indexOfFirst { it.id == source.id || it.url == source.url || it.streamDistinctKey() == source.streamDistinctKey() }
        val selected = preparedPreviewSourceFor(source) ?: source
        val expectedId = selected.id
        val startAtMillis = rememberedSearchDiscoveryPreviewStartMillis(selected)
        if (
            activePremiumDeckSession?.origin == PremiumDeckPlaybackOrigin.SearchDiscoveryPreview &&
            activeYouTubeSourceId == expectedId &&
            activeStreamTrack != null
        ) {
            playing = true
            requestPlaybackSeek(startAtMillis)
            prepareSearchDiscoveryPreviewSources(queue, anchorIndex = (selectedIndex + 1).coerceAtLeast(0))
            return
        }
        playYouTubeSourceQueue(
            source = selected,
            queueSources = queue,
            playbackOrigin = PremiumDeckPlaybackOrigin.SearchDiscoveryPreview,
            playbackTitle = "Discovery Deck",
            playbackSubtitle = collection.title,
            playbackOriginKey = collection.id,
            playbackReason = PremiumDeckQueueReason(collection.title, "previewing from the hook"),
            openPlayerOnStart = false,
            recordPlayback = false,
        )
        prepareSearchDiscoveryPreviewSources(queue, anchorIndex = (selectedIndex + 1).coerceAtLeast(0))
        appScope.launch {
            repeat(28) {
                delay(250L)
                if (
                    activePremiumDeckSession?.origin == PremiumDeckPlaybackOrigin.SearchDiscoveryPreview &&
                    activeYouTubeSourceId == expectedId &&
                    activeStreamTrack != null
                ) {
                    requestPlaybackSeek(startAtMillis)
                    return@launch
                }
            }
        }
    }

    fun playSearchDiscoveryFull(collection: StreamCollection, source: YouTubeSource) {
        playYouTubeSourceQueue(
            source = source,
            queueSources = collection.sources,
            playbackOrigin = PremiumDeckPlaybackOrigin.StreamCollection,
            playbackTitle = collection.title,
            playbackSubtitle = "Discovery Deck",
            playbackOriginKey = collection.id,
            playbackReason = PremiumDeckQueueReason(collection.title, "selected from Discovery Deck"),
        )
        requestPlaybackSeek(0L)
    }

    fun clearPremiumChartMatches() {
        premiumChartMatches = emptyList()
        premiumChartMatchedKeys = emptySet()
    }

    fun selectPremiumChartProvider(provider: PremiumDeckChartProvider) {
        if (premiumChartProvider == provider) return
        premiumChartProvider = provider
        premiumChartRequestedLimit = PREMIUM_DECK_CHART_SAFE_LIMIT
        premiumChartEntries = emptyList()
        clearPremiumChartMatches()
        premiumChartError = null
        premiumChartLoading = false
        premiumChartMatching = false
        premiumChartGeneration += 1L
    }

    fun selectPremiumChartGenre(genre: PremiumDeckChartGenre) {
        if (premiumChartGenre == genre) return
        premiumChartGenre = genre
        premiumChartRequestedLimit = PREMIUM_DECK_CHART_SAFE_LIMIT
        premiumChartEntries = emptyList()
        clearPremiumChartMatches()
        premiumChartError = null
        premiumChartLoading = false
        premiumChartMatching = false
        premiumChartGeneration += 1L
    }

    fun updatePremiumChartLastFmApiKey(apiKey: String) {
        val cleanKey = apiKey.trim().take(96)
        premiumChartLastFmApiKey = cleanKey
        PremiumDeckChartRuntime.lastFmApiKey = cleanKey
        savePremiumDeckChartLastFmApiKey(context, cleanKey)
        premiumChartRequestedLimit = PREMIUM_DECK_CHART_SAFE_LIMIT
        premiumChartEntries = emptyList()
        clearPremiumChartMatches()
        premiumChartError = null
        premiumChartLoading = false
        premiumChartMatching = false
        premiumChartGeneration += 1L
        Toast.makeText(context, if (cleanKey.isBlank()) "Last.fm fallback key removed" else "Last.fm fallback saved", Toast.LENGTH_SHORT).show()
    }

    fun loadPremiumChartEntries(requestedLimit: Int = premiumChartRequestedLimit, clearMatched: Boolean = false) {
        val availabilityMessage = premiumDeckChartAvailabilityMessage(premiumChartProvider, premiumChartGenre)
        if (availabilityMessage != null) {
            premiumChartEntries = emptyList()
            premiumChartError = availabilityMessage
            premiumChartLoading = false
            if (clearMatched) clearPremiumChartMatches()
            return
        }
        if (premiumDeckOfflineActive) {
            premiumChartError = "Offline Deck is on"
            Toast.makeText(context, "Offline Deck is on", Toast.LENGTH_SHORT).show()
            return
        }
        val limit = requestedLimit.coerceIn(PREMIUM_DECK_CHART_SAFE_LIMIT, PREMIUM_DECK_CHART_SESSION_LIMIT)
        val provider = premiumChartProvider
        val genre = premiumChartGenre
        premiumChartRequestedLimit = limit
        premiumChartLoading = true
        premiumChartError = null
        if (clearMatched) clearPremiumChartMatches()
        premiumChartGeneration += 1L
        val generation = premiumChartGeneration
        trackPremiumDeckNetworkJob(appScope.launch {
            val entries = withContext(Dispatchers.IO) {
                runCatching { fetchPremiumDeckChartEntries(provider, genre, limit) }.getOrElse { emptyList() }
            }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                premiumChartLoading = false
                return@launch
            }
            if (generation != premiumChartGeneration) return@launch
            premiumChartLoading = false
            premiumChartEntries = entries
            premiumChartError = if (entries.isEmpty()) {
                val selectedSource = effectivePremiumDeckChartProvider(provider, genre)?.label ?: provider.label
                if (provider == PremiumDeckChartProvider.LastFm && genre != PremiumDeckChartGenre.Global && !premiumDeckDirectLastFmChartConfigured()) {
                    "Add a Last.fm API key to use the direct advanced fallback."
                } else {
                    "No chart entries found from $selectedSource"
                }
            } else {
                null
            }
        })
    }

    fun loadMorePremiumChartEntries() {
        val nextLimit = (premiumChartRequestedLimit + PREMIUM_DECK_CHART_PAGE_SIZE)
            .coerceAtMost(PREMIUM_DECK_CHART_SESSION_LIMIT)
        if (nextLimit <= premiumChartRequestedLimit || premiumChartLoading) return
        loadPremiumChartEntries(requestedLimit = nextLimit, clearMatched = false)
    }

    fun matchNextPremiumChartEntries() {
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Offline Deck is on", Toast.LENGTH_SHORT).show()
            return
        }
        if (premiumChartMatching || premiumChartLoading) return
        val entries = premiumChartEntries
        if (entries.isEmpty()) {
            loadPremiumChartEntries(clearMatched = false)
            return
        }
        premiumChartMatching = true
        premiumChartError = null
        val generation = premiumChartGeneration
        val matchedKeys = premiumChartMatchedKeys
        trackPremiumDeckNetworkJob(appScope.launch {
            val freshMatches = withContext(Dispatchers.IO) {
                runCatching {
                    matchPremiumDeckChartEntries(
                        entries = entries,
                        savedSources = youtubeSources,
                        alreadyMatchedKeys = matchedKeys,
                        maxToResolve = PREMIUM_DECK_CHART_PAGE_SIZE,
                    )
                }.getOrElse { emptyList() }
            }
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) {
                premiumChartMatching = false
                return@launch
            }
            if (generation != premiumChartGeneration) return@launch
            premiumChartMatching = false
            if (freshMatches.isEmpty()) {
                Toast.makeText(context, "No new playable chart matches found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            premiumChartMatches = (premiumChartMatches + freshMatches)
                .distinctBy { it.entry.key }
                .sortedBy { it.entry.rank }
            premiumChartMatchedKeys = premiumChartMatchedKeys + freshMatches.map { it.entry.key }
        })
    }

    fun searchPickedQueueReason(): PremiumDeckQueueReason =
        PremiumDeckQueueReason("Related pick", "related to your pick")

    fun buildSearchPickedPlaybackQueue(
        selected: YouTubeSource,
        searchResults: List<YouTubeSearchResult>,
        discoveryResults: List<YouTubeSearchResult>,
        playHistory: List<StreamPlayHistoryItem>,
        query: String,
        relatedResults: List<YouTubeSearchResult> = emptyList(),
    ): List<YouTubeSource> =
        buildSearchPickedRelatedQueue(
            selected = selected,
            searchResults = searchResults,
            savedSources = youtubeSources,
            discoveryResults = discoveryResults,
            playHistory = playHistory,
            searchQuery = query,
            relatedResults = relatedResults,
            sessionSeed = streamMixSessionSeed,
        )

    fun replaceSearchPickedUpcomingQueue(
        selected: YouTubeSource,
        expectedSessionId: String,
        expectedQueueIds: List<String>,
        searchResults: List<YouTubeSearchResult>,
        discoveryResults: List<YouTubeSearchResult>,
        playHistory: List<StreamPlayHistoryItem>,
        query: String,
        relatedResults: List<YouTubeSearchResult>,
    ) {
        val session = activePremiumDeckSession ?: return
        if (session.id != expectedSessionId || session.origin != PremiumDeckPlaybackOrigin.Search) return
        if (activeYouTubeQueueIds != expectedQueueIds) return
        val currentId = activeYouTubeSourceId ?: return
        val currentSource = queueYouTubeSource(currentId)
        val selectedKey = selected.streamDistinctKey()
        val stillPlayingSelected = currentId == selected.id ||
            currentSource?.url == selected.url ||
            currentSource?.streamDistinctKey() == selectedKey
        if (!stillPlayingSelected) return
        val refinedQueue = buildSearchPickedPlaybackQueue(
            selected = currentSource ?: selected,
            searchResults = searchResults,
            discoveryResults = discoveryResults,
            playHistory = playHistory,
            query = query,
            relatedResults = relatedResults,
        )
        if (refinedQueue.size <= 1) return
        val mergedSources = (refinedQueue + transientYouTubeQueueSources + listOfNotNull(currentSource))
            .filter { it.kind == YouTubeSourceKind.Video }
            .map { source ->
                source.copy(
                    reviewState = YouTubeReviewState.Accepted,
                    thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
                )
            }
            .distinctBy { it.url.ifBlank { it.id } }
        transientYouTubeQueueSources = mergedSources
        val selectedQueueKey = currentSource?.streamDistinctKey() ?: selectedKey
        val refinedIds = refinedQueue
            .mapNotNull { source ->
                when {
                    source.streamDistinctKey() == selectedQueueKey || source.url == selected.url || source.id == selected.id -> currentId
                    source.id.isNotBlank() -> source.id
                    else -> null
                }
            }
            .filter { it != currentId }
            .distinct()
        activeYouTubeQueueIds = (listOf(currentId) + refinedIds).distinct()
        syncActivePremiumDeckSessionQueue(activeYouTubeQueueIds, fallbackReason = searchPickedQueueReason())
        premiumDeckPrepareStates = premiumDeckPrepareStates.filterKeys { it in activeYouTubeQueueIds.toSet() }
        premiumDeckPrepareGeneration += 1L
        prepareUpcomingPremiumDeckStreams()
    }

    fun playSearchPickedYouTubeResult(result: YouTubeSearchResult) {
        val query = youtubeSearchQuery.trim()
        val searchResultsSnapshot = youtubeSearchResults
        val discoveryResultsSnapshot = streamDiscoverySnapshot.results
        val playHistorySnapshot = streamPlayHistory
        val seedQueue = buildSearchPickedPlaybackQueue(
            selected = result.source,
            searchResults = searchResultsSnapshot,
            discoveryResults = discoveryResultsSnapshot,
            playHistory = playHistorySnapshot,
            query = query,
        )
        playYouTubeSourceQueue(
            result.source,
            seedQueue,
            playbackOrigin = PremiumDeckPlaybackOrigin.Search,
            playbackTitle = premiumDeckSessionTitle(PremiumDeckPlaybackOrigin.Search),
            playbackSubtitle = query,
            playbackSearchQuery = query,
            playbackOriginKey = query.normalizedSearchText(),
            playbackReason = searchPickedQueueReason(),
        )
        if (premiumDeckOfflineActive || !pulseSettingsState.misc.online.externalRecommendations) return
        val expectedSessionId = activePremiumDeckSession?.id ?: return
        val expectedQueueIds = activeYouTubeQueueIds
        trackPremiumDeckNetworkJob(appScope.launch {
            val relatedResults = runCatching {
                fetchSearchPickedRelatedResults(result.source, query)
            }.getOrDefault(emptyList())
            if (PulseOnlineRuntime.settings.premiumDeckOfflineActive) return@launch
            if (relatedResults.isEmpty()) return@launch
            replaceSearchPickedUpcomingQueue(
                selected = result.source,
                expectedSessionId = expectedSessionId,
                expectedQueueIds = expectedQueueIds,
                searchResults = searchResultsSnapshot,
                discoveryResults = discoveryResultsSnapshot,
                playHistory = playHistorySnapshot,
                query = query,
                relatedResults = relatedResults,
            )
        })
    }
    LaunchedEffect(screen, youtubeSearchQuery, youtubeSearchResults, premiumDeckOfflineActive) {
        val searchingPremiumDeck = screen == Screen.YouTube && youtubeSearchQuery.trim().length >= 2
        if (searchingPremiumDeck && youtubeSearchResults.isNotEmpty() && !premiumDeckOfflineActive) {
            preparePremiumDeckSearchPreview(youtubeSearchResults)
        } else {
            cancelPremiumDeckSearchPreview(clearPreviewStates = true)
        }
    }
    fun recoverYouTubePlaybackError(mediaId: String?, _error: PlaybackException) {
        val currentStream = activeStreamTrack ?: return
        if (mediaId != null && mediaId != currentStream.stableKey()) return
        val sourceId = activeYouTubeSourceId ?: return
        if (resolvingYouTubeSourceId != null) return
        val source = queueYouTubeSource(sourceId) ?: return
        if (premiumDeckOfflineActive) {
            setPremiumDeckPrepareState(sourceId, PremiumDeckStreamPrepareState.Failed)
            Toast.makeText(context, "Offline Deck will not refresh online stream links", Toast.LENGTH_SHORT).show()
            return
        }
        val attempts = streamRecoveryAttempts[sourceId] ?: 0
        if (attempts >= 2) {
            setPremiumDeckPrepareState(sourceId, PremiumDeckStreamPrepareState.Failed)
            updateYouTubeSource(sourceId) {
                it.copy(
                    cachedUri = null,
                    cachedMillis = 0L,
                    status = if (it.status == YouTubeSourceStatus.Downloaded) it.status else YouTubeSourceStatus.StreamReady,
                )
            }
            Toast.makeText(context, "Stream link refreshed twice but playback is still failing. Check the connection or try another version.", Toast.LENGTH_LONG).show()
            return
        }
        streamRecoveryAttempts = streamRecoveryAttempts + (sourceId to attempts + 1)
        setPremiumDeckPrepareState(sourceId, PremiumDeckStreamPrepareState.Refreshing)
        updateYouTubeSource(sourceId) {
            it.copy(
                cachedUri = null,
                cachedMillis = 0L,
                status = if (it.status == YouTubeSourceStatus.Downloaded) it.status else YouTubeSourceStatus.StreamReady,
            )
        }
        playYouTubeSource(
            source.copy(cachedUri = null, cachedMillis = 0L, status = YouTubeSourceStatus.StreamReady),
            activeYouTubeQueueIds.ifEmpty { listOf(sourceId) },
            albumProjectReleaseId = activeAlbumProjectReleaseId,
            forceRefresh = true,
        )
    }
    fun playYouTubePlaylist(playlist: YouTubePlaylist, shuffle: Boolean) {
        val ordered = resolveYouTubePlaylistSources(playlist)
            .filter { it.reviewState == YouTubeReviewState.Accepted }
            .filter { !premiumDeckOfflineActive || it.isOfflineSaved() }
        val queue = if (shuffle) ordered.shuffled() else ordered
        val first = queue.firstOrNull()
        if (first == null) {
            Toast.makeText(context, if (premiumDeckOfflineActive) "This playlist has no saved Offline Deck tracks" else "This playlist has no playable sources", Toast.LENGTH_SHORT).show()
            return
        }
        playYouTubeSourceQueue(
            first,
            queue,
            playbackOrigin = PremiumDeckPlaybackOrigin.Playlist,
            playbackTitle = premiumDeckSessionTitle(PremiumDeckPlaybackOrigin.Playlist),
            playbackSubtitle = if (shuffle) "${playlist.title} shuffled" else playlist.title,
            playbackOriginKey = playlist.id,
            playbackReason = PremiumDeckQueueReason("Playlist", "from this list"),
        )
    }
    fun streamCollectionPlaybackQueue(collection: StreamCollection, includeDisliked: Boolean, limit: Int): List<YouTubeSource> =
        collection.sources
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
            .filter { !premiumDeckOfflineActive || it.isOfflineSaved() }
            .distinctBy { it.url.ifBlank { it.id } }
            .let { sources ->
                if (includeDisliked) {
                    collection.sources
                        .filter { it.kind == YouTubeSourceKind.Video }
                        .filter { !premiumDeckOfflineActive || it.isOfflineSaved() }
                        .distinctBy { it.url.ifBlank { it.id } }
                } else {
                    sources
                }
            }
            .map { it.copy(reviewState = YouTubeReviewState.Accepted, thumbnailUrl = it.bestThumbnailUrl() ?: it.thumbnailUrl) }
            .take(limit)

    fun streamCollectionQueueLimit(collection: StreamCollection, defaultLimit: Int): Int =
        if (collection.isPremiumDeckChartCollection()) {
            collection.sources.count { it.kind == YouTubeSourceKind.Video }
                .coerceIn(1, PREMIUM_DECK_CHART_SESSION_LIMIT)
        } else {
            defaultLimit
        }

    fun playStreamCollection(collection: StreamCollection) {
        val queueLimit = when {
            collection.kind == StreamCollectionKind.AlbumLike -> STREAM_ALBUM_COLLECTION_TRACK_LIMIT
            else -> streamCollectionQueueLimit(collection, STREAM_MIX_TARGET_SIZE)
        }
        val queue = streamCollectionPlaybackQueue(collection, includeDisliked = false, limit = queueLimit)
        val first = queue.firstOrNull()
        if (first == null) {
            Toast.makeText(context, "This mix has no playable streams yet", Toast.LENGTH_SHORT).show()
            return
        }
        val origin = streamCollectionPlaybackOrigin(collection)
        playYouTubeSourceQueue(
            first,
            queue,
            playbackOrigin = origin,
            playbackTitle = premiumDeckSessionTitle(origin),
            playbackSubtitle = collection.title,
            playbackOriginKey = collection.id,
            playbackReason = streamCollectionQueueReason(collection),
            openPlayerImmediately = collection.isPremiumDeckChartCollection(),
        )
    }
    fun playStreamCollectionFromSource(collection: StreamCollection, source: YouTubeSource) {
        val queueLimit = when {
            collection.kind == StreamCollectionKind.AlbumLike -> STREAM_ALBUM_COLLECTION_TRACK_LIMIT
            else -> streamCollectionQueueLimit(collection, STREAM_MIX_CONTEXT_LIMIT)
        }
        val queue = streamCollectionPlaybackQueue(collection, includeDisliked = true, limit = queueLimit)
        val selected = queue.firstOrNull { it.id == source.id || it.url == source.url }
            ?: source.takeIf { it.kind == YouTubeSourceKind.Video }?.copy(reviewState = YouTubeReviewState.Accepted, thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl)
        if (selected == null) {
            Toast.makeText(context, "This stream is not playable yet", Toast.LENGTH_SHORT).show()
            return
        }
        val playbackQueue = (queue + selected)
            .distinctBy { it.url.ifBlank { it.id } }
            .take(queueLimit)
        val origin = streamCollectionPlaybackOrigin(collection)
        playYouTubeSourceQueue(
            selected,
            playbackQueue,
            albumProjectReleaseId = activeAlbumProjectReleaseId,
            playbackOrigin = origin,
            playbackTitle = premiumDeckSessionTitle(origin),
            playbackSubtitle = collection.title,
            playbackOriginKey = collection.id,
            playbackReason = streamCollectionQueueReason(collection),
            openPlayerImmediately = collection.isPremiumDeckChartCollection(),
        )
    }
    fun searchPulseRadio(countryCode: String = radioCountryCode) {
        val normalizedCode = countryCode.trim().uppercase(Locale.US)
        if (normalizedCode.length != 2 || normalizedCode.any { it !in 'A'..'Z' }) {
            radioError = "Use a two-letter country code"
            return
        }
        if (pulseSettingsState.misc.online.offlineMode) {
            radioError = "PulseRadio needs internet streaming"
            return
        }
        radioCountryCode = normalizedCode
        radioStationsLoading = true
        radioError = null
        appScope.launch {
            runCatching {
                radioClient.searchStationsByCountry(
                    countryCode = normalizedCode,
                    limit = radioStationSearchLimit(normalizedCode),
                    name = radioNameQuery,
                    tag = "",
                    codec = "",
                )
            }
                .onSuccess { radioStations = it }
                .onFailure { radioError = it.message ?: "Could not load stations" }
            radioStationsLoading = false
        }
    }
    fun toggleRadioStationFavorite(station: RadioStation) {
        val key = station.favoriteKey()
        val matchKeys = station.favoriteMatchKeys()
        favoriteRadioStationKeys = (if (matchKeys.any { it in favoriteRadioStationKeys }) favoriteRadioStationKeys - matchKeys else favoriteRadioStationKeys + key)
            .also { localLibraryRepository.saveStateSet(PREF_RADIO_FAVORITE_STATIONS, it) }
    }
    fun activeRadioStationForFullPlayer(): RadioStation? {
        val streamTrack = activeStreamTrack ?: return null
        if (activeYouTubeSourceId != null) return null
        val streamUrl = streamTrack.uri?.toString().orEmpty()
        return activeRadioStation
            ?.takeIf { it.streamUrl == streamUrl || it.name == streamTrack.title }
            ?: radioStations.firstOrNull { it.streamUrl == streamUrl || it.name == streamTrack.title }
    }
    fun playRadioStation(station: RadioStation) {
        if (pulseSettingsState.misc.online.offlineMode) {
            radioError = "PulseRadio needs internet streaming"
            Toast.makeText(context, "PulseRadio needs internet", Toast.LENGTH_SHORT).show()
            return
        }
        if (station.streamUrl.isBlank()) {
            Toast.makeText(context, "This station has no playable stream URL", Toast.LENGTH_SHORT).show()
            return
        }
        appScope.launch {
            runCatching { radioClient.clickStation(station.stationUuid) }
            recentRadioStationKeys = updatedRecentRadioStationKeys(recentRadioStationKeys, station)
                .also { saveRecentRadioStationKeys(context, it) }
            val radioTrack = station.toTrack()
            activeStreamTrack = radioTrack
            activeRadioStation = station
            activeYouTubeSourceId = null
            fallbackYouTubeSource = null
            activeYouTubeQueueIds = emptyList()
            transientYouTubeQueueSources = emptyList()
            albumTrackFallbackQueueIds = emptyMap()
            clearPremiumDeckRuntimeContext()
            activeAlbumKey = null
            setRadioQueueContext(station.stationUuid.ifBlank { station.streamUrl })
            shuffleMode = ShuffleMode.Off
            repeatMode = PlaybackRepeatMode.SongOnce
            clearPlaybackHistory()
            resetDisplayedPlaybackPosition()
            requestPlaybackSeek(0L)
            saveLastPlaybackState(context, radioTrack.toRadioLastPlaybackState())
            playbackState.closePlayer()
            openPlayer(false)
            playing = true
        }
    }
    fun openActiveStreamCategory() {
        when {
            activeStreamTrack != null && activeAlbumProjectReleaseId != null -> openAlbumBuilder(activeAlbumProjectReleaseId, pushHistory = false)
            activeStreamTrack != null && activeYouTubeSourceId == null -> navigate(Screen.PulseRadio)
            activeStreamTrack != null -> navigate(Screen.YouTube)
            else -> openAlbumDetail(track.album.key)
        }
    }
    fun upsertYouTubePlaylist(playlist: YouTubePlaylist) {
        val sourceByKey = streamPlaylistSourceLookup()
        val cleaned = playlist.copy(
            title = playlist.title.trim().ifBlank { "Untitled Stream List" },
            description = playlist.description.trim(),
            sourceIds = playlist.sourceIds.distinct().filter { id -> id in sourceByKey },
            updatedMillis = System.currentTimeMillis(),
        )
        val exists = youtubePlaylists.any { it.id == cleaned.id }
        saveYouTubePlaylistShelf(if (exists) youtubePlaylists.map { if (it.id == cleaned.id) cleaned else it } else listOf(cleaned) + youtubePlaylists)
        if (!exists) {
            logPersonalization(
                BehaviorEvent(
                    type = BehaviorEventType.PlaylistCreated,
                    itemId = cleaned.id,
                    title = cleaned.title,
                    source = CandidateSource.PremiumDeck,
                ),
            )
        }
    }
    fun importYouTubePlaylistLink(title: String, url: String, playlistId: String, accentColor: Int) {
        val cleanPlaylistId = playlistId.trim()
        if (cleanPlaylistId.isBlank()) {
            Toast.makeText(context, "Paste a YouTube playlist link", Toast.LENGTH_SHORT).show()
            return
        }
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Turn off Offline Deck to import YouTube playlists", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanTitle = title.trim().ifBlank { "YouTube Playlist" }
        val cleanUrl = url.trim()
        val startedMillis = System.currentTimeMillis()
        val savedSourcesAtStart = youtubeSources
        playlistImportProgress = PremiumDeckPlaylistImportProgress(
            id = "youtube-import-$startedMillis",
            provider = "YouTube",
            title = cleanTitle,
            phase = "Reading playlist",
        )
        Toast.makeText(context, "Importing YouTube playlist", Toast.LENGTH_SHORT).show()
        trackPremiumDeckNetworkJob(appScope.launch {
            val importResult = runCatching {
                withContext(Dispatchers.IO) {
                    fetchPremiumDeckPlaylistLinkSources(
                        playlistId = cleanPlaylistId,
                        title = cleanTitle,
                        playlistUrl = cleanUrl,
                        savedSources = savedSourcesAtStart,
                        progress = { next ->
                            appScope.launch(Dispatchers.Main.immediate) {
                                if (playlistImportProgress?.complete == true && !next.complete) return@launch
                                playlistImportProgress = next
                            }
                        },
                    )
                }
            }
            withContext(Dispatchers.Main.immediate) {
                val result = importResult.getOrElse { throwable ->
                    playlistImportProgress = PremiumDeckPlaylistImportProgress(
                        id = "youtube-import-error-${System.currentTimeMillis()}",
                        provider = "YouTube",
                        title = cleanTitle,
                        phase = throwable.message ?: "Could not import YouTube playlist",
                        complete = true,
                    )
                    Toast.makeText(context, throwable.message ?: "Could not import YouTube playlist", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                val importedSources = result.sources
                    .map { source ->
                        source.copy(
                            reviewState = YouTubeReviewState.Accepted,
                            thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
                        )
                    }
                    .distinctBy { it.streamDistinctKey() }
                if (importedSources.isEmpty()) {
                    playlistImportProgress = PremiumDeckPlaylistImportProgress(
                        id = "youtube-import-empty-${System.currentTimeMillis()}",
                        provider = "YouTube",
                        title = result.playlistTitle.ifBlank { cleanTitle },
                        phase = "No public playlist tracks found",
                        processed = result.trackCount,
                        total = result.trackCount,
                        matched = 0,
                        complete = true,
                    )
                    Toast.makeText(context, "Could not read public YouTube playlist tracks", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                val mergedSources = (importedSources + youtubeSources)
                    .distinctBy { source -> source.streamDistinctKey() }
                saveYouTubeShelf(mergedSources)
                val ids = importedSources.mapNotNull { imported ->
                    mergedSources.firstOrNull { source ->
                        source.id == imported.id ||
                            source.url == imported.url ||
                            source.streamDistinctKey() == imported.streamDistinctKey()
                    }?.id
                }.distinct()
                val playlistTitle = if (cleanTitle == "YouTube Playlist" && result.playlistTitle.isNotBlank()) {
                    result.playlistTitle
                } else {
                    cleanTitle
                }
                val playlist = YouTubePlaylist(
                    id = newYouTubePlaylistId(),
                    title = playlistTitle,
                    description = "Imported from YouTube | ${ids.size}/${result.trackCount.coerceAtLeast(ids.size)} tracks | ${result.playlistUrl.ifBlank { cleanUrl }}",
                    accentColor = accentColor,
                    sourceIds = ids,
                    createdMillis = startedMillis,
                    updatedMillis = System.currentTimeMillis(),
                    origin = YouTubePlaylistOrigin.YouTubeImport,
                )
                upsertYouTubePlaylist(playlist)
                youtubeTab = YouTubeLibraryTab.Playlists
                youtubeShelf = null
                playlistImportProgress = PremiumDeckPlaylistImportProgress(
                    id = "youtube-import-done-${System.currentTimeMillis()}",
                    provider = "YouTube",
                    title = playlistTitle,
                    phase = "Imported ${ids.size} tracks",
                    processed = result.trackCount.coerceAtLeast(ids.size),
                    total = result.trackCount.coerceAtLeast(ids.size),
                    matched = ids.size,
                    complete = true,
                )
                Toast.makeText(
                    context,
                    "Imported ${ids.size} YouTube tracks",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        })
    }
    fun importSpotifyPlaylistLink(title: String, url: String, accentColor: Int) {
        val cleanTitle = title.trim().ifBlank { "Spotify Playlist" }
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            Toast.makeText(context, "Paste a Spotify playlist link", Toast.LENGTH_SHORT).show()
            return
        }
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Turn off Offline Deck to import Spotify playlists", Toast.LENGTH_SHORT).show()
            return
        }
        val startedMillis = System.currentTimeMillis()
        val savedSourcesAtStart = youtubeSources
        playlistImportProgress = PremiumDeckPlaylistImportProgress(
            id = "spotify-import-$startedMillis",
            provider = "Spotify",
            title = cleanTitle,
            phase = "Reading playlist",
        )
        Toast.makeText(context, "Importing Spotify playlist", Toast.LENGTH_SHORT).show()
        trackPremiumDeckNetworkJob(appScope.launch {
            val importResult = runCatching {
                withContext(Dispatchers.IO) {
                    fetchPremiumDeckSpotifyPlaylistSources(
                        playlistUrl = cleanUrl,
                        titleHint = cleanTitle,
                        savedSources = savedSourcesAtStart,
                        progress = { next ->
                            appScope.launch(Dispatchers.Main.immediate) {
                                if (playlistImportProgress?.complete == true && !next.complete) return@launch
                                playlistImportProgress = next
                            }
                        },
                    )
                }
            }
            withContext(Dispatchers.Main.immediate) {
                val result = importResult.getOrElse { throwable ->
                    playlistImportProgress = PremiumDeckPlaylistImportProgress(
                        id = "spotify-import-error-${System.currentTimeMillis()}",
                        provider = "Spotify",
                        title = cleanTitle,
                        phase = throwable.message ?: "Could not import Spotify playlist",
                        complete = true,
                    )
                    Toast.makeText(context, throwable.message ?: "Could not import Spotify playlist", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                val importedSources = result.sources
                    .map { source ->
                        source.copy(
                            reviewState = YouTubeReviewState.Accepted,
                            thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl,
                        )
                    }
                    .distinctBy { it.streamDistinctKey() }
                if (importedSources.isEmpty()) {
                    playlistImportProgress = PremiumDeckPlaylistImportProgress(
                        id = "spotify-import-empty-${System.currentTimeMillis()}",
                        provider = "Spotify",
                        title = result.playlistTitle.ifBlank { cleanTitle },
                        phase = "No playable matches found",
                        processed = result.trackCount,
                        total = result.trackCount,
                        matched = 0,
                        complete = true,
                    )
                    Toast.makeText(context, "Could not find Spotify tracks to import", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                val mergedSources = (importedSources + youtubeSources)
                    .distinctBy { source -> source.streamDistinctKey() }
                saveYouTubeShelf(mergedSources)
                val ids = importedSources.mapNotNull { imported ->
                    mergedSources.firstOrNull { source ->
                        source.id == imported.id ||
                            source.url == imported.url ||
                            source.streamDistinctKey() == imported.streamDistinctKey()
                    }?.id
                }.distinct()
                val playlistTitle = if (cleanTitle == "Spotify Playlist" && result.playlistTitle.isNotBlank()) {
                    result.playlistTitle
                } else {
                    cleanTitle
                }
                val playlist = YouTubePlaylist(
                    id = newYouTubePlaylistId(),
                    title = playlistTitle,
                    description = "Imported from Spotify | ${ids.size}/${result.trackCount.coerceAtLeast(ids.size)} tracks | ${result.playlistUrl.ifBlank { cleanUrl }}",
                    accentColor = accentColor,
                    sourceIds = ids,
                    createdMillis = startedMillis,
                    updatedMillis = System.currentTimeMillis(),
                    origin = YouTubePlaylistOrigin.SpotifyImport,
                )
                upsertYouTubePlaylist(playlist)
                youtubeTab = YouTubeLibraryTab.Playlists
                youtubeShelf = null
                playlistImportProgress = PremiumDeckPlaylistImportProgress(
                    id = "spotify-import-done-${System.currentTimeMillis()}",
                    provider = "Spotify",
                    title = playlistTitle,
                    phase = "Imported ${ids.size} tracks",
                    processed = result.trackCount.coerceAtLeast(ids.size),
                    total = result.trackCount.coerceAtLeast(ids.size),
                    matched = ids.size,
                    complete = true,
                )
                Toast.makeText(context, "Imported ${ids.size} Spotify tracks", Toast.LENGTH_SHORT).show()
            }
        })
    }
    fun persistAlbumMatchedSources(release: AlbumDownloadRelease): List<YouTubeSource> {
        val candidateSources = release.albumProjectCandidateSources(youtubeSources)
        val matchedSources = release.matchedPremiumDeckSources().map { matched ->
            val existing = youtubeSources.firstOrNull {
                it.id == matched.id || it.url == matched.url || it.streamDistinctKey() == matched.streamDistinctKey()
            }
            if (existing == null) {
                matched
            } else {
                matched.copy(
                    playCount = existing.playCount,
                    addedMillis = existing.addedMillis,
                    lastPlayedMillis = existing.lastPlayedMillis,
                    reaction = existing.reaction,
                    bookmarked = existing.bookmarked || matched.bookmarked,
                    status = existing.status,
                    cachedUri = existing.cachedUri,
                    cachedMillis = existing.cachedMillis,
                    downloadedUri = existing.downloadedUri,
                    downloadState = existing.downloadState,
                    downloadProgress = existing.downloadProgress,
                    playbackPositionMillis = existing.playbackPositionMillis,
                    chapters = existing.chapters,
                    sponsorSegments = existing.sponsorSegments,
                    neverPromptCache = existing.neverPromptCache,
                    skipSegmentsEnabled = existing.skipSegmentsEnabled,
                    trimSilenceOnDownload = existing.trimSilenceOnDownload,
                )
            }
        }
        if (matchedSources.isEmpty()) return emptyList()
        val merged = (matchedSources + candidateSources + youtubeSources)
            .distinctBy { source -> source.streamDistinctKey() }
        saveYouTubeShelf(merged)
        return matchedSources.mapNotNull { matched ->
            merged.firstOrNull { it.id == matched.id || it.url == matched.url || it.streamDistinctKey() == matched.streamDistinctKey() }
        }.ifEmpty { matchedSources }
    }
    fun saveAlbumProjectAsPremiumDeckPlaylist(release: AlbumDownloadRelease, matchedSources: List<YouTubeSource>) {
        if (matchedSources.isEmpty()) return
        val now = System.currentTimeMillis()
        val playlistId = release.albumProjectPlaylistId()
        val playlist = YouTubePlaylist(
            id = playlistId,
            title = "${release.artist} - ${release.title}",
            description = "PremiumDeck album project  |  ${matchedSources.size}/${release.tracks.size} matched tracks",
            sourceIds = matchedSources.map { it.id }.distinct(),
            accentColor = accentForKey("${release.artist}|${release.title}"),
            createdMillis = youtubePlaylists.firstOrNull { it.id == playlistId }?.createdMillis ?: now,
            updatedMillis = now,
            origin = YouTubePlaylistOrigin.Manual,
        )
        upsertYouTubePlaylist(playlist)
    }
    fun playAlbumProject(release: AlbumDownloadRelease) {
        val matchedSources = persistAlbumMatchedSources(release)
        val candidateSources = release.albumProjectCandidateSources(youtubeSources + matchedSources)
        val allPlaybackSources = (matchedSources + candidateSources)
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
            .distinctBy { it.streamDistinctKey() }
        val sourceLookup = (allPlaybackSources + youtubeSources).associateBy { it.streamDistinctKey() }
        val lookupSources = sourceLookup.values.toList()
        val releaseQueue = release.tracks
            .sortedBy { it.position }
            .mapNotNull { track -> track.matchCandidateSources(lookupSources).firstOrNull() }
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
            .distinctBy { it.streamDistinctKey() }
        val first = releaseQueue.firstOrNull()
        if (first == null) {
            Toast.makeText(context, "Match this album first", Toast.LENGTH_SHORT).show()
            return
        }
        saveAlbumProjectAsPremiumDeckPlaylist(release, releaseQueue)
        albumTrackFallbackQueueIds = release.tracks
            .sortedBy { it.position }
            .mapNotNull { track ->
                val candidates = track.matchCandidateSources(lookupSources)
                val primary = candidates.firstOrNull() ?: return@mapNotNull null
                val fallbacks = candidates.drop(1).map { it.id }.distinct()
                if (fallbacks.isEmpty()) null else primary.id to fallbacks
            }
            .toMap()
        transientYouTubeQueueSources = allPlaybackSources
        activePremiumDeckSession = buildPremiumDeckPlaybackSession(
            origin = PremiumDeckPlaybackOrigin.AlbumProject,
            sources = releaseQueue,
            startedSource = first,
            title = premiumDeckSessionTitle(PremiumDeckPlaybackOrigin.AlbumProject),
            subtitle = "${release.artist} - ${release.title}",
            originKey = release.id,
            reason = PremiumDeckQueueReason("Album project", "from this release"),
        )
        premiumDeckPrepareStates = emptyMap()
        premiumDeckPrepareGeneration += 1L
        playYouTubeSource(first, releaseQueue.map { it.id }, albumProjectReleaseId = release.id)
    }
    fun playAlbumProjectTrack(release: AlbumDownloadRelease, track: AlbumDownloadTrack) {
        val matchedSources = persistAlbumMatchedSources(release)
        val candidateSources = release.albumProjectCandidateSources(youtubeSources + matchedSources)
        val allPlaybackSources = (matchedSources + candidateSources)
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
            .distinctBy { it.streamDistinctKey() }
        val sourceLookup = (allPlaybackSources + youtubeSources).associateBy { it.streamDistinctKey() }
        val lookupSources = sourceLookup.values.toList()
        val selected = track.matchCandidateSources(lookupSources).firstOrNull()
        if (selected == null) {
            Toast.makeText(context, "Match this track first", Toast.LENGTH_SHORT).show()
            return
        }
        val playable = release.tracks
            .sortedBy { it.position }
            .mapNotNull { albumTrack -> albumTrack.matchCandidateSources(lookupSources).firstOrNull() }
            .filter { it.kind == YouTubeSourceKind.Video && it.reaction != YouTubeReaction.Disliked }
            .distinctBy { it.streamDistinctKey() }
        if (playable.isEmpty()) {
            Toast.makeText(context, "Match this album first", Toast.LENGTH_SHORT).show()
            return
        }
        saveAlbumProjectAsPremiumDeckPlaylist(release, playable)
        albumTrackFallbackQueueIds = release.tracks
            .sortedBy { it.position }
            .mapNotNull { albumTrack ->
                val candidates = albumTrack.matchCandidateSources(lookupSources)
                val primary = candidates.firstOrNull() ?: return@mapNotNull null
                val fallbacks = candidates.drop(1).map { it.id }.distinct()
                if (fallbacks.isEmpty()) null else primary.id to fallbacks
            }
            .toMap()
        transientYouTubeQueueSources = allPlaybackSources
        val queueIds = playable.map { it.id }
            .let { ids -> if (selected.id in ids) ids else listOf(selected.id) + ids }
            .distinct()
        activePremiumDeckSession = buildPremiumDeckPlaybackSession(
            origin = PremiumDeckPlaybackOrigin.AlbumProject,
            sources = playable,
            startedSource = selected,
            title = premiumDeckSessionTitle(PremiumDeckPlaybackOrigin.AlbumProject),
            subtitle = "${release.artist} - ${release.title}",
            originKey = release.id,
            reason = PremiumDeckQueueReason("Album project", "from this release"),
        )
        syncActivePremiumDeckSessionQueue(queueIds)
        premiumDeckPrepareStates = emptyMap()
        premiumDeckPrepareGeneration += 1L
        playYouTubeSource(selected, queueIds, albumProjectReleaseId = release.id)
    }
    fun queueAlbumOffline(release: AlbumDownloadRelease, addLocal: Boolean) {
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Turn off Offline Deck to save new PremiumDeck music", Toast.LENGTH_SHORT).show()
            return
        }
        val saveMode = if (addLocal) AlbumProjectSaveMode.LocalAlbum else AlbumProjectSaveMode.StreamOffline
        val matchedSources = if (saveMode.createsPremiumDeckPlaylist()) {
            persistAlbumMatchedSources(release)
        } else {
            release.albumProjectSourcesForSave(saveMode, youtubeSources)
        }
        if (matchedSources.isEmpty()) {
            Toast.makeText(context, "Match this album first", Toast.LENGTH_SHORT).show()
            return
        }
        if (saveMode.createsPremiumDeckPlaylist()) {
            saveAlbumProjectAsPremiumDeckPlaylist(release, matchedSources)
        }
        val positionBySourceKey = release.tracks
            .mapNotNull { track -> track.matchedSource?.streamDistinctKey()?.let { key -> key to track.position } }
            .toMap()
        val missing = when (saveMode) {
            AlbumProjectSaveMode.StreamOffline -> matchedSources.filter {
                it.downloadState != YouTubeDownloadState.Downloaded &&
                    it.status != YouTubeSourceStatus.Downloaded &&
                    it.downloadState != YouTubeDownloadState.Downloading
            }
            AlbumProjectSaveMode.LocalAlbum -> matchedSources
        }
        suspend fun saveAlbumToLocalLibrary() {
            var completed = 0
            matchedSources.forEachIndexed { index, source ->
                var localSource = source.copy(downloadState = YouTubeDownloadState.Downloading, downloadProgress = 1)
                updateAlbumProjectTask(
                    AlbumProjectTaskState(
                        releaseId = release.id,
                        title = release.title,
                        artist = release.artist,
                        phase = AlbumProjectTaskPhase.OfflineSaving,
                        total = matchedSources.size,
                        completed = completed,
                        activePosition = positionBySourceKey[source.streamDistinctKey()],
                        message = "Saving ${source.title}",
                    ),
                )
                runYouTubeOfflineDownload(
                    source = localSource,
                    currentProgress = { localSource.downloadProgress },
                    updateSource = { _, transform ->
                        localSource = transform(localSource)
                        withContext(Dispatchers.Main.immediate) {
                            updateAlbumProjectTask(
                                AlbumProjectTaskState(
                                    releaseId = release.id,
                                    title = release.title,
                                    artist = release.artist,
                                    phase = AlbumProjectTaskPhase.OfflineSaving,
                                    total = matchedSources.size,
                                    completed = completed,
                                    activePosition = positionBySourceKey[source.streamDistinctKey()],
                                    message = "Saving ${source.title} ${localSource.downloadProgress.coerceIn(1, 99)}%",
                                ),
                            )
                        }
                    },
                    downloadOnDevice = { downloadSource, onProgress ->
                        downloadYouTubeToMusicOnDevice(context, downloadSource, onProgress)
                    },
                    startResolverDownload = ::startResolverDownload,
                    getResolverDownloadStatus = ::getResolverDownloadStatus,
                    saveResolverDownload = { job, downloadSource ->
                        saveResolverDownloadToMusic(context, job, downloadSource)
                    },
                    notify = { message, long ->
                        withContext(Dispatchers.Main.immediate) {
                            Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                        }
                    },
                    successMessage = "Saved to Music/PulseDeck/${release.artist}/${release.title}",
                )
                completed += 1
                updateAlbumProjectTask(
                    AlbumProjectTaskState(
                        releaseId = release.id,
                        title = release.title,
                        artist = release.artist,
                        phase = AlbumProjectTaskPhase.OfflineSaving,
                        total = matchedSources.size,
                        completed = completed,
                        activePosition = positionBySourceKey[source.streamDistinctKey()],
                        message = if (localSource.isOfflineSaved()) "Saved ${source.title}" else "Could not save ${source.title}",
                    ),
                )
                if (index < matchedSources.lastIndex) delay(900L)
            }
            updateAlbumProjectTask(
                AlbumProjectTaskState(
                    releaseId = release.id,
                    title = release.title,
                    artist = release.artist,
                    phase = AlbumProjectTaskPhase.OfflineSaving,
                    total = matchedSources.size,
                    completed = matchedSources.size,
                    message = "Refreshing local Albums",
                ),
            )
            scanLocalLibrary()
        }
        updateAlbumProjectTask(
            AlbumProjectTaskState(
                releaseId = release.id,
                title = release.title,
                artist = release.artist,
                phase = AlbumProjectTaskPhase.OfflineSaving,
                total = matchedSources.size,
                completed = matchedSources.size - missing.size,
                message = when {
                    saveMode == AlbumProjectSaveMode.LocalAlbum -> "Saving album to local folders"
                    missing.isEmpty() -> "Album already saved offline"
                    else -> "Queued ${missing.size} offline saves"
                },
            ),
        )
        if (saveMode == AlbumProjectSaveMode.LocalAlbum) {
            trackPremiumDeckNetworkJob(appScope.launch { saveAlbumToLocalLibrary() })
        } else if (missing.isNotEmpty()) {
            trackPremiumDeckNetworkJob(appScope.launch {
                missing.forEachIndexed { index, source ->
                    updateAlbumProjectTask(
                        AlbumProjectTaskState(
                            releaseId = release.id,
                            title = release.title,
                            artist = release.artist,
                            phase = AlbumProjectTaskPhase.OfflineSaving,
                            total = matchedSources.size,
                            completed = matchedSources.size - missing.size + index,
                            activePosition = positionBySourceKey[source.streamDistinctKey()],
                            message = "Saving ${source.title}",
                        ),
                    )
                    keepYouTubeOffline(source)
                    if (index < missing.lastIndex) delay(1_400L)
                }
                updateAlbumProjectTask(
                    AlbumProjectTaskState(
                        releaseId = release.id,
                        title = release.title,
                        artist = release.artist,
                        phase = AlbumProjectTaskPhase.OfflineSaving,
                        total = matchedSources.size,
                        completed = matchedSources.size,
                        message = "Offline save running",
                    ),
                )
            })
        }
        val action = if (saveMode == AlbumProjectSaveMode.LocalAlbum) "local album save" else "offline save"
        val message = if (missing.isEmpty()) {
            "${release.title} is already saved offline"
        } else {
            "Started $action for ${missing.size} tracks"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    fun saveStreamCollectionAsPlaylist(collection: StreamCollection) {
        val queueLimit = when {
            collection.kind == StreamCollectionKind.AlbumLike -> STREAM_ALBUM_COLLECTION_TRACK_LIMIT
            else -> streamCollectionQueueLimit(collection, STREAM_MIX_TARGET_SIZE)
        }
        val acceptedVirtualSources = streamCollectionPlaybackQueue(collection, includeDisliked = false, limit = queueLimit)
        if (acceptedVirtualSources.isEmpty()) {
            Toast.makeText(context, "This mix has no streams to save", Toast.LENGTH_SHORT).show()
            return
        }
        val mergedSources = (youtubeSources + acceptedVirtualSources)
            .distinctBy { source -> source.url.ifBlank { source.id } }
        saveYouTubeShelf(mergedSources)
        val ids = acceptedVirtualSources.mapNotNull { virtual ->
            mergedSources.firstOrNull { it.id == virtual.id || it.url == virtual.url }?.id
        }.distinct()
        val now = System.currentTimeMillis()
        val playlist = YouTubePlaylist(
            id = newYouTubePlaylistId(),
            title = collection.title,
            description = collection.subtitle,
            accentColor = collection.accentColor,
            sourceIds = ids,
            createdMillis = now,
            updatedMillis = now,
            origin = if (collection.kind == StreamCollectionKind.Playlist) YouTubePlaylistOrigin.Manual else YouTubePlaylistOrigin.SavedMix,
        )
        saveYouTubePlaylistShelf(listOf(playlist) + youtubePlaylists)
        logPersonalization(
            BehaviorEvent(
                type = BehaviorEventType.PlaylistCreated,
                itemId = playlist.id,
                title = playlist.title,
                source = CandidateSource.PremiumDeck,
            ),
        )
        Toast.makeText(context, "Pinned ${collection.title} as a saved mix", Toast.LENGTH_SHORT).show()
    }
    fun setStreamReaction(source: YouTubeSource, reaction: YouTubeReaction) {
        val nextReaction = if (source.reaction == reaction) YouTubeReaction.Neutral else reaction
        updateYouTubeSource(source.id) {
            it.copy(
                reaction = nextReaction,
                bookmarked = if (nextReaction == YouTubeReaction.Liked) true else it.bookmarked,
            )
        }
        when (nextReaction) {
            YouTubeReaction.Liked -> logPersonalization(source.toBehaviorEvent(BehaviorEventType.LikeFavorite))
            YouTubeReaction.Disliked -> logPersonalization(source.toBehaviorEvent(BehaviorEventType.DislikeHide))
            YouTubeReaction.Neutral -> Unit
        }
        if (nextReaction == YouTubeReaction.Liked) refreshStreamDiscovery(force = false)
    }
    fun toggleStreamBookmark(source: YouTubeSource) {
        updateYouTubeSource(source.id) { it.copy(bookmarked = !it.bookmarked) }
        if (!source.bookmarked) logPersonalization(source.toBehaviorEvent(BehaviorEventType.LikeFavorite))
    }
    fun addSourceToYouTubePlaylist(source: YouTubeSource, playlist: YouTubePlaylist) {
        updateYouTubePlaylist(playlist.id) {
            it.copy(sourceIds = (it.sourceIds + source.id).distinct(), updatedMillis = System.currentTimeMillis())
        }
        logPersonalization(source.toBehaviorEvent(BehaviorEventType.TrackAddedToPlaylist, metadata = mapOf("playlist" to playlist.title)))
        Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
    }
    fun addSourceToEditableStreamPlaylist(source: YouTubeSource, playlist: YouTubePlaylist) {
        val playable = source.copy(reviewState = YouTubeReviewState.Accepted, thumbnailUrl = source.bestThumbnailUrl() ?: source.thumbnailUrl)
        val existing = youtubeSources.firstOrNull {
            it.id == playable.id || it.url == playable.url || it.streamDistinctKey() == playable.streamDistinctKey()
        }
        val persisted = existing ?: playable
        if (existing == null) {
            saveYouTubeShelf((listOf(persisted) + youtubeSources).distinctBy { it.streamDistinctKey() })
        }
        val nextIds = streamPlaylistSourceIdsAfterAdd(playlist, persisted)
        updateYouTubePlaylist(playlist.id) { it.copy(sourceIds = nextIds, updatedMillis = System.currentTimeMillis()) }
        youtubeDialog = YouTubeDialogState.PlaylistMixEditor(playlist.copy(sourceIds = nextIds, updatedMillis = System.currentTimeMillis()))
        logPersonalization(persisted.toBehaviorEvent(BehaviorEventType.TrackAddedToPlaylist, metadata = mapOf("playlist" to playlist.title)))
        Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
    }
    fun removeSourceFromEditableStreamPlaylist(source: YouTubeSource, playlist: YouTubePlaylist) {
        val nextIds = streamPlaylistSourceIdsAfterRemove(playlist, source.id)
        updateYouTubePlaylist(playlist.id) { it.copy(sourceIds = nextIds, updatedMillis = System.currentTimeMillis()) }
        youtubeDialog = YouTubeDialogState.PlaylistMixEditor(playlist.copy(sourceIds = nextIds, updatedMillis = System.currentTimeMillis()))
        logPersonalization(source.toBehaviorEvent(BehaviorEventType.TrackRemovedFromPlaylist, metadata = mapOf("playlist" to playlist.title)))
    }
    fun keepPlaylistOffline(playlist: YouTubePlaylist) {
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Turn off Offline Deck to save new PremiumDeck music", Toast.LENGTH_SHORT).show()
            return
        }
        val missing = resolveYouTubePlaylistSources(playlist)
            .filter { it.downloadState != YouTubeDownloadState.Downloaded }
        if (missing.isEmpty()) {
            Toast.makeText(context, "Playlist is already saved offline", Toast.LENGTH_SHORT).show()
            return
        }
        missing.forEach { keepYouTubeOffline(it) }
        Toast.makeText(context, "Started offline save for ${missing.size} sources", Toast.LENGTH_SHORT).show()
    }
    fun keepStreamCollectionOffline(collection: StreamCollection) {
        if (premiumDeckOfflineActive) {
            Toast.makeText(context, "Turn off Offline Deck to save new PremiumDeck music", Toast.LENGTH_SHORT).show()
            return
        }
        val missing = collection.sources
            .filter { it.kind == YouTubeSourceKind.Video }
            .distinctBy { it.streamDistinctKey() }
            .filter {
                it.downloadState != YouTubeDownloadState.Downloaded &&
                    it.status != YouTubeSourceStatus.Downloaded &&
                    it.downloadState != YouTubeDownloadState.Downloading
            }
        if (missing.isEmpty()) {
            Toast.makeText(context, "${collection.title} is already saved offline", Toast.LENGTH_SHORT).show()
            return
        }
        missing.forEach { keepYouTubeOffline(it) }
        val label = if (collection.kind == StreamCollectionKind.AlbumLike) "album" else "collection"
        Toast.makeText(context, "Started offline save for ${missing.size} $label tracks", Toast.LENGTH_SHORT).show()
    }
    val globalSwipeEdgeWidthPx = with(LocalDensity.current) { 34.dp.toPx() }
    val globalSwipeBottomExclusionPx = with(LocalDensity.current) { 280.dp.toPx() }
    val globalSwipeTouchSlopPx = with(LocalDensity.current) { 14.dp.toPx() }
    val globalSwipeBlocked = playerOpen ||
        screen == Screen.AlbumDetail ||
        screen == Screen.Albums ||
        screen == Screen.YouTube ||
        screen == Screen.PulseRadio ||
        searchDiscoveryPreviewCollection != null ||
        albumReturnPreparing
    val previousRouteScreen = routeHistory.lastOrNull()?.screen
    val globalBackSwipe = Modifier.pointerInput(
        screen,
        playerOpen,
        globalSwipeBlocked,
        globalSwipeEdgeWidthPx,
        globalSwipeBottomExclusionPx,
        globalSwipeTouchSlopPx,
        routeHistory.size,
        previousRouteScreen,
        playbackQueueContext,
        activeStreamTrack?.stableKey(),
        selectedAlbumKey,
        hasPlayableTrack,
    ) {
        var swipeAccepted = false
        fun categoryNameForDrag(): String? =
            libraryCategoryNameForScreen(screen)?.takeIf { screen != Screen.Albums }

        fun categoryBackSwipeFromLibrary(): Boolean =
            !playerOpen &&
                screen != Screen.Library &&
                previousRouteScreen == Screen.Library &&
                categoryNameForDrag() != null

        fun settingsBackSwipe(): Boolean =
            !playerOpen && screen == Screen.Settings

        fun libraryPlayerSwipe(): Boolean =
            !playerOpen && screen == Screen.Library && hasPlayableTrack

        fun cancelCategoryDrag() {
            categoryNameForDrag()?.let(::cancelCategoryBackDrag)
        }

        fun updateAcceptedDrag(amount: Float) {
            screenDragX = (screenDragX + amount).coerceIn(-520f, 520f)
            if (previousRouteScreen == Screen.Library && screen != Screen.Library) {
                categoryNameForDrag()?.let { categoryName ->
                    updateCategoryBackDrag(categoryName, (abs(screenDragX) / 520f).coerceIn(0f, 1f))
                }
            } else if (screenDragX > 0f) {
                categoryNameForDrag()?.let { categoryName ->
                    updateCategoryBackDrag(categoryName, (screenDragX / 520f).coerceIn(0f, 1f))
                }
            } else {
                cancelCategoryDrag()
            }
        }

        fun clearSwipeState() {
            screenDragX = 0f
            swipeAccepted = false
        }

        fun finishAcceptedDrag() {
            val releaseProgress = (abs(screenDragX) / 520f).coerceIn(0f, 1f)
            val categoryName = categoryNameForDrag()
            if (abs(screenDragX) > 112f) {
                val step = if (screenDragX > 0f) -1 else 1
                if (screen == Screen.Settings) {
                    requestSettingsBack()
                } else if (screen == Screen.Library && hasPlayableTrack) {
                    miniDismissed = false
                    openPlayer(false)
                } else if (!playerOpen && screen != Screen.Library && previousRouteScreen == Screen.Library) {
                    if (categoryName != null) {
                        finishCategoryToLibrary(categoryName, releaseProgress)
                    } else {
                        goBack()
                    }
                } else {
                    if (categoryName != null) cancelCategoryBackDrag(categoryName)
                    movePlaybackLoop(step)
                }
            } else if (categoryName != null) {
                cancelCategoryBackDrag(categoryName)
            }
            clearSwipeState()
        }

        awaitEachGesture {
            var dragging = false
            var totalX = 0f
            var totalY = 0f
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            run {
                val offset = down.position
                val edgeSwipe = offset.x <= globalSwipeEdgeWidthPx ||
                    offset.x >= size.width - globalSwipeEdgeWidthPx
                val contentCategoryBackSwipe = categoryBackSwipeFromLibrary() &&
                    offset.y < size.height - globalSwipeBottomExclusionPx
                val contentSettingsBackSwipe = settingsBackSwipe() &&
                    offset.y < size.height - globalSwipeBottomExclusionPx
                val contentLibraryPlayerSwipe = libraryPlayerSwipe() &&
                    offset.y < size.height - globalSwipeBottomExclusionPx
                swipeAccepted = !globalSwipeBlocked &&
                    (edgeSwipe || contentCategoryBackSwipe || contentSettingsBackSwipe || contentLibraryPlayerSwipe)
                if (!swipeAccepted) {
                    cancelCategoryDrag()
                    screenDragX = 0f
                }
            }
            if (swipeAccepted) {
                var pointerDown = true
                while (pointerDown) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                    if (change == null) {
                        pointerDown = false
                    } else if (!change.pressed) {
                        pointerDown = false
                    } else {
                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y
                        if (!dragging) {
                            val horizontalIntent = abs(totalX) > globalSwipeTouchSlopPx &&
                                abs(totalX) > abs(totalY) * 1.2f
                            val verticalIntent = abs(totalY) > globalSwipeTouchSlopPx &&
                                abs(totalY) > abs(totalX) * 1.2f
                            when {
                                horizontalIntent -> dragging = true
                                verticalIntent -> {
                                    cancelCategoryDrag()
                                    clearSwipeState()
                                    pointerDown = false
                                }
                            }
                        }
                        if (dragging) {
                            change.consume()
                            updateAcceptedDrag(delta.x)
                        }
                    }
                }
                if (swipeAccepted && dragging) {
                    finishAcceptedDrag()
                } else {
                    cancelCategoryDrag()
                    clearSwipeState()
                }
            }
        }
    }

    val mediaSessionStreamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
    val mediaSessionQueue = remember(
        activeStreamTrack?.stableKey(),
        activeYouTubeSourceId,
        activeYouTubeQueueIds,
        transientYouTubeQueueSources,
        youtubeSources,
        libraryTracks,
        libraryAlbums,
        activeAlbumKey,
        playbackQueueContext,
        repeatMode,
        manualQueueTrackKeys,
        audioEngineState.crossfade,
        mediaSessionStreamPolicy,
        track.stableKey(),
    ) {
        activeStreamTrack?.let { streamTrack ->
            val crossfade = audioEngineState.crossfade
            val queuedTracks = activeYouTubeQueueIds.mapNotNull { sourceId ->
                val source = queueYouTubeSource(sourceId) ?: return@mapNotNull null
                val restored = source.toRestoredTrack(mediaSessionStreamPolicy) ?: return@mapNotNull null
                restored.takeIf {
                    source.id == activeYouTubeSourceId || source.canUsePremiumDeckSmoothTransition(crossfade)
                }
            }
            return@remember (listOf(streamTrack) + queuedTracks)
                .filter { it.uri != null }
                .distinctBy { it.stableKey() }
                .ifEmpty { listOf(streamTrack) }
        }
        val localSession = localPlaybackSession()
        val localQueue = localPlaybackQueueTracks(localSession)
        val manualQueue = localManualQueueTracks()
        val orderedLocalQueue = localQueue
            .ifEmpty { listOf(track) }
            .let { queue ->
                if (queue.any { it.stableKey() == track.stableKey() }) queue else listOf(track) + queue
            }
        if (manualQueue.isNotEmpty()) {
            (listOf(track) + manualQueue + orderedLocalQueue).distinctBy { it.stableKey() }
        } else {
            orderedLocalQueue.distinctBy { it.stableKey() }
        }
    }
    fun selectMediaSessionItem(mediaId: String, autoEnded: Boolean) {
        val current = currentTrackOrNull()
        if (current?.stableKey() == mediaId) return
        if (activeStreamTrack != null) {
            val source = activeYouTubeQueueIds
                .asSequence()
                .mapNotNull { sourceId -> queueYouTubeSource(sourceId) }
                .firstOrNull { source -> source.toRestoredTrack(mediaSessionStreamPolicy)?.stableKey() == mediaId }
            if (source != null) {
                logCurrentPlaybackTransition(autoEnded)
                playYouTubeSourceFromQueue?.invoke(source, activeYouTubeQueueIds)
            }
            return
        }
        val target = mediaSessionQueue.firstOrNull { it.stableKey() == mediaId }
            ?: libraryTracks.firstOrNull { it.stableKey() == mediaId }
            ?: return
        logCurrentPlaybackTransition(autoEnded)
        val currentKey = current?.stableKey()
        val currentIndex = mediaSessionQueue.indexOfFirst { it.stableKey() == currentKey }
        val targetIndex = mediaSessionQueue.indexOfFirst { it.stableKey() == mediaId }
        val direction = if (currentIndex >= 0 && targetIndex >= 0 && targetIndex < currentIndex) -1 else 1
        if (mediaId in manualQueueTrackKeys) {
            manualQueueTrackKeys = manualQueueTrackKeys.filterNot { it == mediaId }
        }
        playResolvedTrack(
            target,
            direction = direction,
            rememberHistory = direction > 0 && currentKey != null && target.stableKey() != currentKey,
        )
        if (autoEnded) playing = true
    }

    if (lastPlaybackRestored && hasPlayableTrack && mediaSessionRequested) {
        Media3PlaybackEffect(
            track = track,
            queue = mediaSessionQueue,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            playing = playing,
            volume = playerVolume,
            audioState = audioEngineState,
            positionTickerPolicy = positionTickerPolicy,
            seekRequestKey = seekRequestKey,
            seekRequestMillis = seekRequestMillis,
            progressController = progressController,
            sponsorSkipSourceId = activeYouTubeSourceId,
            sponsorSkipEnabled = sponsorSkipEnabled,
            sponsorSkipSegments = sponsorSkipSegments,
            onEnded = { moveNextTrack(autoEnded = true) },
            onMediaItemSelected = ::selectMediaSessionItem,
            onPlaybackError = ::recoverYouTubePlaybackError,
        )
    }

    LaunchedEffect(activeYouTubeSourceId, activeStreamTrack?.stableKey(), playing) {
        val sourceId = activeYouTubeSourceId ?: return@LaunchedEffect
        while (activeStreamTrack != null) {
            delay(5000L)
            if (playing) {
                updateYouTubeSource(sourceId) { source ->
                    if (source.isPodcast) source.copy(playbackPositionMillis = currentPlaybackPositionMillis()) else source
                }
            }
        }
    }

    val motionSpec = remember(pulseSettingsState.lookAndFeel.animationSpeed) {
        PulseMotionSpec.from(pulseSettingsState.lookAndFeel.animationSpeed)
    }
    val baseDensity = LocalDensity.current
    val appFontScale = pulseSettingsState.lookAndFeel.normalized().appFontScale
    val appDensity = remember(baseDensity, appFontScale) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * appFontScale,
        )
    }
    LaunchedEffect(categoryMotionRequest, categoryBoundsByName, categoryHeaderBoundsByName) {
        val request = categoryMotionRequest ?: return@LaunchedEffect
        if (!LibraryCategorySharedHandoffEnabled) {
            categoryMotionRequest = null
            return@LaunchedEffect
        }
        val target = when (request.direction) {
            CategoryMotionDirection.Opening -> categoryHeaderBoundsByName[request.categoryName]
            CategoryMotionDirection.Closing -> categoryBoundsByName[request.categoryName]
        } ?: return@LaunchedEffect
        val category = categories.firstOrNull { it.name == request.categoryName } ?: return@LaunchedEffect
        activeCategoryMotion = CategorySharedMotion(
            category = category,
            fromBounds = request.fromBounds,
            toBounds = target,
            direction = request.direction,
            nonce = request.nonce,
            startProgress = request.startProgress,
        )
        categoryMotionRequest = null
    }

    BackHandler(enabled = playerOpen || screen != Screen.Library || routeHistory.isNotEmpty()) {
        goBack()
    }
    LaunchedEffect(screen, activeCategoryMotion?.nonce, activeCategoryMotion?.progressOverride) {
        val active = activeCategoryMotion
        if (screen == Screen.Library && active?.progressOverride != null) {
            activeCategoryMotion = null
        }
    }
    LaunchedEffect(screen) {
        if (screen != Screen.AlbumDetail) {
            albumVisualBackground = null
        }
    }

    Surface(color = Color.Black) {
        CompositionLocalProvider(
            LocalPulseMotionSpec provides motionSpec,
            LocalDensity provides appDensity,
        ) {
        Box(Modifier.fillMaxSize().then(globalBackSwipe)) {
            Background(albumVisualBackground ?: track.album, backgroundSettings, backgroundUsageFor(screen, playerOpen))
            run {
                val routeKey = "screen-$screen-${selectedAlbumKey.orEmpty()}-${selectedLibraryGroupKind?.name.orEmpty()}-$selectedLibraryGroupKey"
                val routePattern = if (suppressNextRouteMotion) MotionPattern.None else motionPatternFor(screen, selectedAlbumKey)
                val sharedCategoryName = categoryMotionRequest?.categoryName ?: activeCategoryMotion?.category?.name
                LaunchedEffect(routeKey, suppressNextRouteMotion) {
                    if (suppressNextRouteMotion) suppressNextRouteMotion = false
                }
                MotionRouteHost(
                    routeKey = routeKey,
                    targetRoute = screen,
                    pattern = routePattern,
                    onMotionActiveChange = { routeMotionInFlight = it },
                ) { route ->
                    when (route) {
                    Screen.Library -> Library(
                        categoryMetas = libraryCategoryMetas,
                        permissionGranted = permissionGranted,
                        onRequestAudio = { scanLocalLibrary() },
                        onCategory = { kind, name -> openLibraryCategory(kind, name) },
                        onQuickAdd = { quickAddDialogOpen = true },
                        onSettings = { openSettings(SettingsLaunchTarget.Home) },
                        listState = libraryListState,
                        hiddenCategoryName = sharedCategoryName,
                        onCategoryBoundsChanged = ::updateCategoryBounds,
                    )
                    Screen.AllSongs -> AllSongsScreen(
                        tracks = libraryTracks,
                        currentTrackKey = track.stableKey(),
                        playing = playing && activeStreamTrack == null,
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                        onPlayAll = {
                            libraryTracks.firstOrNull()?.let { selected ->
                                activeAlbumKey = null
                                setAllSongsQueueContext()
                                shuffleMode = ShuffleMode.Off
                                repeatMode = PlaybackRepeatMode.AllOnce
                                playResolvedTrack(selected, direction = 1, rememberHistory = false)
                                clearPlaybackHistory()
                                resetDisplayedPlaybackPosition()
                                openPlayer(false)
                                playing = true
                            }
                        },
                        onShuffleAll = {
                            libraryTracks.randomOrNull()?.let { selected ->
                                activeAlbumKey = null
                                setAllSongsQueueContext()
                                shuffleMode = ShuffleMode.ShuffleAll
                                repeatMode = PlaybackRepeatMode.AllOnce
                                playResolvedTrack(selected, direction = 1, rememberHistory = false)
                                clearPlaybackHistory()
                                resetDisplayedPlaybackPosition()
                                openPlayer(false)
                                playing = true
                            }
                        },
                        onTrack = { selected ->
                            activeAlbumKey = null
                            setAllSongsQueueContext()
                            shuffleMode = ShuffleMode.Off
                            repeatMode = PlaybackRepeatMode.AllOnce
                            playResolvedTrack(selected, direction = 1, rememberHistory = false)
                            clearPlaybackHistory()
                            resetDisplayedPlaybackPosition()
                            openPlayer(false)
                            playing = true
                        },
                        listState = allSongsListState,
                    )
                    Screen.Folders -> LibraryGroupScreen(
                        categoryName = "Folders",
                        subtitle = "Flat folders",
                        tint = Color(0xFF539BFF),
                        icon = DeckIcon.Folder,
                        groups = folderGroups,
                        allTracks = libraryTracks,
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        onPlayAll = { playLocalTrackList(libraryTracks, shuffle = false) },
                        onShuffleAll = { playLocalTrackList(libraryTracks, shuffle = true) },
                        onGroup = { group ->
                            selectedFolderPath = group.key
                            navigate(Screen.FolderHierarchy)
                        },
                        listState = foldersListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.Albums -> Albums(
                        albums = libraryAlbums,
                        gridState = albumGridState,
                        onBack = { progress -> finishAlbumsToLibrary(progress) },
                        onBackDrag = { progress -> updateCategoryBackDrag("Albums", progress) },
                        onBackDragCancel = { cancelCategoryBackDrag("Albums") },
                        onAlbum = { album -> openAlbumDetail(album.key) },
                        onSearch = { navigate(Screen.Search) },
                        onSettings = { openSettings(SettingsLaunchTarget.Home) },
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                        onAlbumBoundsChanged = ::updateAlbumTileBounds,
                    )
                    Screen.AlbumDownloader -> AlbumDownloaderScreen(
                        drafts = albumDownloadDrafts,
                        downloadJobs = albumAudioDownloadJobs,
                        premiumSources = youtubeSources,
                        albumProjectTasks = albumProjectTasks,
                        initialReleaseId = albumDownloaderInitialReleaseId,
                        onBack = {
                            albumDownloaderInitialReleaseId = null
                            goBack()
                        },
                        onSaveDraft = { release ->
                            val draft = AlbumDownloadDraft(release = release)
                            saveAlbumDownloadDraftShelf((listOf(draft) + albumDownloadDrafts.filterNot { it.release.id == release.id }).take(50))
                            Toast.makeText(context, "Saved album project", Toast.LENGTH_SHORT).show()
                        },
                        onRemoveDraft = { releaseId ->
                            saveAlbumDownloadDraftShelf(albumDownloadDrafts.filterNot { it.release.id == releaseId })
                            clearAlbumProjectTask(releaseId)
                        },
                        onStartHighestQualityDownload = { release -> startFreeLegalAlbumDownload(release) },
                        onMatchRelease = { release -> startAlbumTrackMatching(release) },
                        onPlayAlbum = { release -> playAlbumProject(release) },
                        onPlayTrack = { release, albumTrack -> playAlbumProjectTrack(release, albumTrack) },
                        onQueueOffline = { release -> queueAlbumOffline(release, addLocal = false) },
                        onAddLocal = { release -> queueAlbumOffline(release, addLocal = true) },
                        onSelectTrackMatch = ::selectAlbumProjectTrackMatch,
                    )
                    Screen.AlbumArtists -> LibraryGroupScreen(
                        categoryName = "Album Artists",
                        subtitle = "Grouped by album artist",
                        tint = Color(0xFF6760B8),
                        icon = DeckIcon.People,
                        groups = albumArtistGroups,
                        allTracks = libraryTracks,
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        onPlayAll = { playLocalTrackList(libraryTracks, shuffle = false) },
                        onShuffleAll = { playLocalTrackList(libraryTracks, shuffle = true) },
                        onGroup = { group -> openLibraryGroup(LocalLibraryGroupKind.AlbumArtists, group) },
                        listState = albumArtistsListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.Genres -> LibraryGroupScreen(
                        categoryName = "Genres",
                        subtitle = "Grouped by genre",
                        tint = Color(0xFF8B4A89),
                        icon = DeckIcon.Tag,
                        groups = genreGroups,
                        allTracks = libraryTracks,
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        onPlayAll = { playLocalTrackList(libraryTracks, shuffle = false) },
                        onShuffleAll = { playLocalTrackList(libraryTracks, shuffle = true) },
                        onGroup = { group -> openLibraryGroup(LocalLibraryGroupKind.Genres, group) },
                        listState = genresListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.Years -> LibraryGroupScreen(
                        categoryName = "Years",
                        subtitle = "Grouped by year",
                        tint = Color(0xFF69CADA),
                        icon = DeckIcon.Calendar,
                        groups = yearGroups,
                        allTracks = libraryTracks,
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        onPlayAll = { playLocalTrackList(libraryTracks, shuffle = false) },
                        onShuffleAll = { playLocalTrackList(libraryTracks, shuffle = true) },
                        onGroup = { group -> openLibraryGroup(LocalLibraryGroupKind.Years, group) },
                        listState = yearsListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.Composers -> LibraryGroupScreen(
                        categoryName = "Composers",
                        subtitle = "Grouped by composer",
                        tint = Color(0xFF188A58),
                        icon = DeckIcon.Pencil,
                        groups = composerGroups,
                        allTracks = libraryTracks,
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        onPlayAll = { playLocalTrackList(libraryTracks, shuffle = false) },
                        onShuffleAll = { playLocalTrackList(libraryTracks, shuffle = true) },
                        onGroup = { group -> openLibraryGroup(LocalLibraryGroupKind.Composers, group) },
                        listState = composersListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.LocalPlaylists -> LibraryFilteredTracksScreen(
                        title = "Playlists",
                        subtitle = "Local Pulse List",
                        categoryNameForMotion = "Playlists",
                        tint = Color(0xFF925B5B),
                        icon = DeckIcon.Playlist,
                        tracks = localPlaylistTracks,
                        currentTrackKey = track.stableKey(),
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        onPlayAll = { playLocalTrackList(localPlaylistTracks, shuffle = false) },
                        onShuffleAll = { playLocalTrackList(localPlaylistTracks, shuffle = true) },
                        onTrack = { selected -> playLocalTrackFromList(selected) },
                        listState = localPlaylistsListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.Bookmarks -> LibraryFilteredTracksScreen(
                        title = "Bookmarks",
                        subtitle = "Saved local tracks",
                        categoryNameForMotion = "Bookmarks",
                        tint = Color(0xFF4D7DFF),
                        icon = DeckIcon.Bookmark,
                        tracks = bookmarkedTracks,
                        currentTrackKey = track.stableKey(),
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        onPlayAll = { playLocalTrackList(bookmarkedTracks, shuffle = false) },
                        onShuffleAll = { playLocalTrackList(bookmarkedTracks, shuffle = true) },
                        onTrack = { selected -> playLocalTrackFromList(selected) },
                        listState = bookmarksListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.MostPlayed -> LibraryFilteredTracksScreen(
                        title = "Most Played",
                        subtitle = "Local play count",
                        categoryNameForMotion = "Most Played",
                        tint = Color(0xFFA57BEF),
                        icon = DeckIcon.MusicList,
                        tracks = mostPlayedTracks,
                        currentTrackKey = track.stableKey(),
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        onPlayAll = { playLocalTrackList(mostPlayedTracks, shuffle = false) },
                        onShuffleAll = { playLocalTrackList(mostPlayedTracks, shuffle = true) },
                        onTrack = { selected -> playLocalTrackFromList(selected) },
                        listState = mostPlayedListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.LibraryGroupTracks -> {
                        val groupKind = selectedLibraryGroupKind
                        val title = selectedLibraryGroup?.title ?: "Selection"
                        val parentTitle = when (groupKind) {
                            LocalLibraryGroupKind.AlbumArtists -> "Album Artists"
                            LocalLibraryGroupKind.Genres -> "Genres"
                            LocalLibraryGroupKind.Years -> "Years"
                            LocalLibraryGroupKind.Composers -> "Composers"
                            LocalLibraryGroupKind.Folders -> "Folders"
                            null -> "Library"
                        }
                        val tint = when (groupKind) {
                            LocalLibraryGroupKind.AlbumArtists -> Color(0xFF6760B8)
                            LocalLibraryGroupKind.Genres -> Color(0xFF8B4A89)
                            LocalLibraryGroupKind.Years -> Color(0xFF69CADA)
                            LocalLibraryGroupKind.Composers -> Color(0xFF188A58)
                            LocalLibraryGroupKind.Folders -> Color(0xFF539BFF)
                            null -> Color(0xFF85A3FF)
                        }
                        val icon = when (groupKind) {
                            LocalLibraryGroupKind.AlbumArtists -> DeckIcon.People
                            LocalLibraryGroupKind.Genres -> DeckIcon.Tag
                            LocalLibraryGroupKind.Years -> DeckIcon.Calendar
                            LocalLibraryGroupKind.Composers -> DeckIcon.Pencil
                            LocalLibraryGroupKind.Folders -> DeckIcon.Folder
                            null -> DeckIcon.MusicList
                        }
                        LibraryFilteredTracksScreen(
                            title = title,
                            subtitle = parentTitle,
                            categoryNameForMotion = null,
                            tint = tint,
                            icon = icon,
                            tracks = selectedLibraryGroupTracks,
                            currentTrackKey = track.stableKey(),
                            onBack = { goBack() },
                            onSearch = { navigate(Screen.Search) },
                            onPlayAll = { playLocalTrackList(selectedLibraryGroupTracks, shuffle = false) },
                            onShuffleAll = { playLocalTrackList(selectedLibraryGroupTracks, shuffle = true) },
                            onTrack = { selected -> playLocalTrackFromList(selected) },
                            listState = selectedLibraryGroupTracksListState,
                        )
                    }
                    Screen.AlbumDetail -> {
                        val album = selectedAlbum
                        if (album != null) {
                            val albumTracks = libraryTracks
                                .filter { it.album.key == album.key }
                                .ifEmpty { libraryTracks.take(1) }
                                .map { track ->
                                    if (track.album == album) track else track.copy(album = album)
                                }
                            Box(Modifier.fillMaxSize()) {
                                Albums(
                                    albums = libraryAlbums,
                                    gridState = albumGridState,
                                    onBack = { _ -> goBack() },
                                    onAlbum = { selected -> openAlbumDetail(selected.key) },
                                    onSearch = { navigate(Screen.Search) },
                                    onSettings = { openSettings(SettingsLaunchTarget.Home) },
                                    interactionEnabled = false,
                                    hiddenCategoryHeaderName = sharedCategoryName,
                                    hiddenAlbumKey = albumReturnTargetKey ?: album.key,
                                    onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                                    onAlbumBoundsChanged = ::updateAlbumTileBounds,
                                )
                                AlbumDetail(
                                    album = album,
                                    tracks = albumTracks,
                                    currentTrackKey = track.stableKey(),
                                    playing = playing,
                                    closeRequestKey = albumDetailCloseRequestKey,
                                    closeRequestStartProgress = albumDetailCloseStartProgress,
                                    returnTargetBounds = albumReturnTargetBounds,
                                    listState = albumDetailListState,
                                    handoffToPlayer = playerOpen,
                                    handoffTrackKey = playerLaunchHandoff?.trackKey,
                                    onRequestCloseToGrid = { progress -> requestAlbumDetailCloseToGrid(progress) },
                                    onBack = { finishAlbumDetailToGrid() },
                                    onTrackHandoffBoundsChanged = ::updateAlbumTrackLaunchHandoff,
                                    onTrack = { selected ->
                                        albumDetailPlaybackAnchorKey = album.key
                                        activeAlbumKey = album.key
                                        setLocalAlbumQueueContext(album)
                                        playResolvedTrack(selected, direction = 1, rememberHistory = false)
                                        clearPlaybackHistory()
                                        resetDisplayedPlaybackPosition()
                                        openPlayer(false, albumTrackLaunchHandoffs[selected.stableKey()])
                                        playing = true
                                    },
                                    onPlayAll = {
                                        albumDetailPlaybackAnchorKey = album.key
                                        activeAlbumKey = album.key
                                        setLocalAlbumQueueContext(album)
                                        shuffleMode = ShuffleMode.Off
                                        repeatMode = PlaybackRepeatMode.CategoryOnce
                                        playResolvedTrack(albumTracks.first(), direction = 1, rememberHistory = false)
                                        clearPlaybackHistory()
                                        resetDisplayedPlaybackPosition()
                                        openPlayer(false)
                                        playing = true
                                    },
                                    onShuffleAll = {
                                        albumDetailPlaybackAnchorKey = album.key
                                        activeAlbumKey = album.key
                                        setLocalAlbumQueueContext(album)
                                        shuffleMode = ShuffleMode.ShuffleSongsInCategory
                                        repeatMode = PlaybackRepeatMode.CategoryOnce
                                        val selected = albumTracks.randomOrNull() ?: albumTracks.first()
                                        playResolvedTrack(selected, direction = 1, rememberHistory = false)
                                        clearPlaybackHistory()
                                        resetDisplayedPlaybackPosition()
                                        openPlayer(false)
                                        playing = true
                                    },
                                    onDeleteSelected = { selected ->
                                        requestDeleteTracks(selected)
                                    },
                                    onChangeArt = { targetAlbum, sourceUris ->
                                        albumArtPickerTarget = AlbumArtPickerTarget(targetAlbum, sourceUris)
                                    },
                                )
                            }
                        } else {
                            LaunchedEffect(Unit) {
                                navigate(Screen.Albums, pushHistory = false, suppressMotion = true)
                            }
                        }
                    }
                    Screen.Audio -> AudioDeckRoute(
                        state = audioEngineState,
                        currentTrack = track,
                        progressController = progressController,
                        onState = { next ->
                            audioEngineState = next.normalized().copy(presetModified = true)
                        },
                        onPreset = { preset ->
                            audioEngineState = applyPreset(audioEngineState, preset)
                        },
                        onAdvanced = { openSettings(SettingsLaunchTarget.Audio) },
                        onBack = { goBack() },
                    )
                    Screen.Artists -> ArtistsScreen(
                        artists = libraryArtists,
                        onBack = { goBack() },
                        onSearch = { navigate(Screen.Search) },
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                        onPlay = { artist ->
                            artist.tracks.firstOrNull()?.let { selected ->
                                activeAlbumKey = null
                                playResolvedTrack(selected, direction = 1, rememberHistory = false)
                                clearPlaybackHistory()
                                resetDisplayedPlaybackPosition()
                                openPlayer(false)
                                playing = true
                            }
                        },
                        onShuffle = { artist ->
                            artist.tracks.randomOrNull()?.let { selected ->
                                activeAlbumKey = null
                                playResolvedTrack(selected, direction = 1, rememberHistory = false)
                                clearPlaybackHistory()
                                resetDisplayedPlaybackPosition()
                                openPlayer(false)
                                playing = true
                            }
                        },
                        onArtistInfo = { artist ->
                            logPersonalization(
                                BehaviorEvent(
                                    type = BehaviorEventType.ArtistOpened,
                                    itemId = "artist:${artist.name}",
                                    title = artist.name,
                                    artist = artist.name,
                                    source = CandidateSource.LocalLibrary,
                                ),
                            )
                            showSimpleInfo(
                                "Artist",
                                artist.name,
                                listOf(
                                    "Songs" to artist.songCount.toString(),
                                    "Total time" to formatDuration(artist.durationMillis),
                                    "Albums" to artist.tracks.map { it.album.title }.distinct().size.toString(),
                                ),
                            )
                        },
                        listState = artistsListState,
                    )
                    Screen.FolderHierarchy -> FolderHierarchyScreen(
                        tracks = libraryTracks,
                        currentPath = selectedFolderPath,
                        onBack = {
                            val parent = parentFolderPath(selectedFolderPath)
                            if (parent != null) {
                                pushCurrentRoute()
                                selectedFolderPath = parent
                            } else {
                                goBack()
                            }
                        },
                        onRoot = {
                            pushCurrentRoute()
                            selectedFolderPath = ""
                        },
                        onFolder = {
                            pushCurrentRoute()
                            selectedFolderPath = it
                        },
                        onSearch = { navigate(Screen.Search) },
                        onTrack = { selected ->
                            activeAlbumKey = null
                            playResolvedTrack(selected, direction = 1, rememberHistory = false)
                            clearPlaybackHistory()
                            resetDisplayedPlaybackPosition()
                            openPlayer(false)
                            playing = true
                        },
                        listState = folderHierarchyListState,
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.Search -> SearchScreen(
                        results = localSearchResults,
                        query = searchQuery,
                        onBack = { goBack() },
                        onQuery = { searchQuery = it },
                        onTrack = { selected ->
                            logPersonalization(selected.toBehaviorEvent(BehaviorEventType.SearchResultClicked, query = searchQuery))
                            activeAlbumKey = null
                            playResolvedTrack(selected, direction = 1, rememberHistory = false)
                            clearPlaybackHistory()
                            resetDisplayedPlaybackPosition()
                            openPlayer(false)
                            playing = true
                        },
                    )
                    Screen.YouTube -> YouTubeSourcesScreen(
                        sources = youtubeSources,
                        playlists = youtubePlaylists,
                        albumDrafts = albumDownloadDrafts,
                        albumProjectTasks = albumProjectTasks,
                        playHistory = streamPlayHistory,
                        discoveryResults = streamDiscoverySnapshot.results,
                        personalizationMixes = personalizationMixes,
                        followedArtists = followedStreamArtists,
                        newReleaseResults = streamNewReleaseSnapshot.results,
                        releaseNotifications = streamReleaseNotifications,
                        podcastSnapshot = premiumDeckPodcastSnapshot,
                        recommendedAlbumsSnapshot = recommendedAlbumsSnapshot,
                        discoveryLoading = streamDiscoveryLoading,
                        newReleaseLoading = streamNewReleaseLoading,
                        podcastDiscoveryLoading = premiumDeckPodcastLoading,
                        podcastLoadingShowId = premiumDeckPodcastLoadingShowId,
                        podcastAddLoading = premiumDeckPodcastAddLoading,
                        mixSessionSeed = streamMixSessionSeed,
                        personalizationProfile = personalizationProfile,
                        searchQuery = youtubeSearchQuery,
                        searchResults = youtubeSearchResults,
                        searchSuggestions = youtubeSearchSuggestions,
                        discoveryGenres = premiumDeckSearchDiscoveryGenres,
                        discoveryGenreCollections = searchDiscoveryGenreCollections,
                        discoveryPreviewCollection = searchDiscoveryPreviewCollection,
                        discoveryLoadingGenreId = searchDiscoveryLoadingGenreId,
                        premiumChartProvider = premiumChartProvider,
                        premiumChartGenre = premiumChartGenre,
                        premiumChartRequestedLimit = premiumChartRequestedLimit,
                        premiumChartEntries = premiumChartEntries,
                        premiumChartMatches = premiumChartMatches,
                        premiumChartCollection = buildPremiumDeckInteractiveChartCollection(
                            provider = premiumChartProvider,
                            genre = premiumChartGenre,
                            entries = premiumChartEntries,
                            matches = premiumChartMatches,
                        ),
                        premiumChartLoading = premiumChartLoading,
                        premiumChartMatching = premiumChartMatching,
                        premiumChartError = premiumChartError,
                        activePreviewSourceId = activeYouTubeSourceId,
                        searchLoading = youtubeSearchLoading,
                        searchError = youtubeSearchError,
                        offlineDeckActive = premiumDeckOfflineActive,
                        offlineDeckRecommendation = if (premiumDeckOfflineActive) null else NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online).premiumDeckOfflineRecommendationText(),
                        selectedTab = youtubeTab,
                        activeShelf = youtubeShelf,
                        resolvingSourceId = resolvingYouTubeSourceId,
                        prepareStates = premiumDeckPrepareStates,
                        capabilities = resolverCapabilities,
                        streamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online),
                        onBack = { goBack() },
                        onAdd = { quickAddDialogOpen = true },
                        onAlbumBuilder = { openAlbumBuilder() },
                        onAlbumProject = { draft -> openAlbumBuilder(draft.release.id) },
                        onVerifiedAlbum = { album ->
                            val release = album.toAlbumBuilderSeed()
                            val initialRelease = latestAlbumProjectRelease(release)
                            val needsTracklist = initialRelease.tracks.isEmpty()
                            saveAlbumDownloadDraftSilently(initialRelease)
                            if (needsTracklist) {
                                updateAlbumProjectTask(
                                    AlbumProjectTaskState(
                                        releaseId = initialRelease.id,
                                        title = initialRelease.title,
                                        artist = initialRelease.artist,
                                        phase = AlbumProjectTaskPhase.TracklistLoading,
                                        total = initialRelease.trackCount,
                                        message = "Loading verified tracklist...",
                                    ),
                                )
                            }
                            openAlbumBuilder(initialRelease.id)
                            if (needsTracklist) {
                                appScope.launch {
                                    val enriched = runCatching {
                                        album.toVerifiedAlbumBuilderReleaseWithTracklist(initialRelease)
                                    }.getOrNull()
                                    if (enriched != null && enriched.tracks.isNotEmpty()) {
                                        val currentRelease = albumDownloadDrafts.firstOrNull { it.release.id == initialRelease.id }?.release
                                        val userStartedMatching = currentRelease?.tracks?.any { track ->
                                            track.matchedSource != null || track.downloadUrl.isNotBlank() || track.matchCandidates.isNotEmpty()
                                        } == true
                                        if (currentRelease != null && !userStartedMatching) {
                                            saveAlbumDownloadDraftSilently(enriched)
                                        }
                                        if (albumProjectTasks[initialRelease.id]?.phase == AlbumProjectTaskPhase.TracklistLoading) {
                                            clearAlbumProjectTask(initialRelease.id)
                                        }
                                    } else {
                                        clearAlbumProjectTask(initialRelease.id)
                                    }
                                }
                            }
                        },
                        onNewPlaylist = { youtubeDialog = YouTubeDialogState.PlaylistEditor() },
                        onRules = { infoDialog = downloadRulesInfo() },
                        onRefreshDiscovery = { refreshStreamDiscovery(force = true) },
                        onRefreshPodcasts = { refreshPremiumDeckPodcasts(force = true) },
                        onAddPodcast = ::addPremiumDeckPodcast,
                        onPodcastShow = ::openPremiumDeckPodcastShow,
                        onTab = {
                            youtubeTab = it
                            youtubeShelf = null
                        },
                        onSearchQuery = { youtubeSearchQuery = it },
                        onSearchResult = { result ->
                            logPersonalization(result.source.toBehaviorEvent(BehaviorEventType.SearchResultClicked, query = youtubeSearchQuery))
                            youtubeTab = YouTubeLibraryTab.Sources
                            youtubeShelf = null
                            playSearchPickedYouTubeResult(result)
                        },
                        onDiscoveryGenrePreview = { genre -> prepareSearchDiscoveryGenre(genre) },
                        onDiscoveryGenre = { genre -> openSearchDiscoveryGenre(genre) },
                        onPremiumChartProvider = { provider -> selectPremiumChartProvider(provider) },
                        onPremiumChartGenre = { genre -> selectPremiumChartGenre(genre) },
                        onPremiumChartRefresh = { loadPremiumChartEntries(clearMatched = true) },
                        onPremiumChartShowMore = { loadMorePremiumChartEntries() },
                        onPremiumChartMatchNext = { matchNextPremiumChartEntries() },
                        onDismissDiscoveryPreview = {
                            stopSearchDiscoveryPreviewPlayback()
                            searchDiscoveryGeneration += 1L
                            searchDiscoveryPreviewCollection = null
                            searchDiscoveryLoadingGenreId = null
                        },
                        onDiscoveryPreview = { collection, source -> playSearchDiscoveryPreview(collection, source) },
                        onDiscoveryPlayFull = { collection, source ->
                            stopSearchDiscoveryPreviewPlayback()
                            searchDiscoveryPreviewCollection = null
                            youtubeTab = YouTubeLibraryTab.Sources
                            youtubeShelf = null
                            playSearchDiscoveryFull(collection, source)
                        },
                        onCollectionOpen = { collection -> youtubeDialog = YouTubeDialogState.StreamCollectionActions(collection) },
                        onCollectionPlay = { collection -> playStreamCollection(collection) },
                        onCollectionSave = { collection -> saveStreamCollectionAsPlaylist(collection) },
                        onToggleFollowArtist = { artist -> toggleFollowedStreamArtist(artist) },
                        onToggleFollowArtistOption = { option -> toggleFollowedStreamArtist(option) },
                        onRefreshNewReleases = { refreshStreamNewReleases(force = true) },
                        onReleaseNotification = { notification, collection ->
                            markStreamReleaseNotificationRead(notification)
                            youtubeTab = YouTubeLibraryTab.Sources
                            youtubeShelf = null
                            playStreamCollectionFromSource(collection, notification.source)
                        },
                        onMarkAllReleaseNotificationsRead = { markAllStreamReleaseNotificationsRead() },
                        onShelf = { shelf ->
                            youtubeShelf = shelf
                            youtubeTab = when (shelf) {
                                YouTubeSmartShelf.SavedOffline -> YouTubeLibraryTab.Downloads
                                YouTubeSmartShelf.Podcasts -> YouTubeLibraryTab.Podcasts
                                null -> YouTubeLibraryTab.Sources
                                else -> YouTubeLibraryTab.Sources
                            }
                        },
                        onPlayInApp = { source, queueSources ->
                            playYouTubeSourceQueue(
                                source,
                                queueSources,
                                playbackOrigin = PremiumDeckPlaybackOrigin.ManualSource,
                                playbackTitle = premiumDeckSessionTitle(PremiumDeckPlaybackOrigin.ManualSource),
                                playbackSubtitle = source.author,
                            )
                        },
                        onAccept = { source -> updateYouTubeSource(source.id) { it.copy(reviewState = YouTubeReviewState.Accepted) } },
                        onKeepOffline = { source -> keepYouTubeOffline(source) },
                        onReaction = { source, reaction -> setStreamReaction(source, reaction) },
                        onBookmark = { source -> toggleStreamBookmark(source) },
                        onToggleSkip = { source -> updateYouTubeSource(source.id) { it.copy(skipSegmentsEnabled = !it.skipSegmentsEnabled) } },
                        onToggleTrim = { source -> updateYouTubeSource(source.id) { it.copy(trimSilenceOnDownload = !it.trimSilenceOnDownload) } },
                        onSourceInfo = { source, queueIds -> youtubeDialog = YouTubeDialogState.SourceActions(source, queueIds) },
                        onRemove = { source ->
                            removeYouTubeSourceEverywhere(source)
                            Toast.makeText(context, "Removed from $PREMIUMDECK_SOURCE_CATEGORY", Toast.LENGTH_SHORT).show()
                        },
                        onPlaylist = { playlist -> youtubeDialog = YouTubeDialogState.PlaylistMixEditor(playlist) },
                        onPlaylistActions = { playlist -> youtubeDialog = YouTubeDialogState.PlaylistActions(playlist) },
                        onPlaylistPlay = { playlist -> playYouTubePlaylist(playlist, shuffle = false) },
                        onPlaylistShuffle = { playlist -> playYouTubePlaylist(playlist, shuffle = true) },
                        onOfflineDeckChange = { enabled ->
                            val nextOnline = pulseSettingsState.misc.online.copy(premiumDeckOfflineMode = enabled)
                            pulseSettingsState = pulseSettingsState.copy(misc = pulseSettingsState.misc.copy(online = nextOnline)).normalized()
                            Toast.makeText(context, if (enabled) "Offline Deck on" else "Offline Deck off", Toast.LENGTH_SHORT).show()
                        },
                    )
                    Screen.PulseRadio -> PulseRadioScreen(
                        countries = radioCountries,
                        stations = radioStations,
                        countryCode = radioCountryCode,
                        nameQuery = radioNameQuery,
                        stationFilter = radioStationFilter,
                        favoriteCountryCodes = favoriteRadioCountryCodes,
                        favoriteStationKeys = favoriteRadioStationKeys,
                        recentStationKeys = recentRadioStationKeys,
                        streamPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online),
                        loadingCountries = radioCountriesLoading,
                        loadingStations = radioStationsLoading,
                        error = radioError,
                        activeStreamUrl = activeStreamTrack?.uri?.toString(),
                        offlineMode = pulseSettingsState.misc.online.offlineMode,
                        onBack = { goBack() },
                        onNameQuery = { radioNameQuery = it },
                        onStationFilter = { radioStationFilter = it },
                        onSearch = { searchPulseRadio() },
                        onCountry = { country ->
                            radioCountryCode = country.isoCode
                            searchPulseRadio(country.isoCode)
                        },
                        onToggleCountryFavorite = { country ->
                            val key = country.isoCode.uppercase(Locale.US)
                            favoriteRadioCountryCodes = (if (key in favoriteRadioCountryCodes) favoriteRadioCountryCodes - key else favoriteRadioCountryCodes + key)
                                .also { localLibraryRepository.saveStateSet(PREF_RADIO_FAVORITE_COUNTRIES, it) }
                        },
                        onToggleStationFavorite = { station ->
                            toggleRadioStationFavorite(station)
                        },
                        onPlayStation = { station -> playRadioStation(station) },
                        hiddenCategoryHeaderName = sharedCategoryName,
                        onCategoryHeaderBoundsChanged = ::updateCategoryHeaderBounds,
                    )
                    Screen.YouTubeStream -> {
                        val source = youtubeSources.firstOrNull { it.id == activeYouTubeSourceId }
                            ?: fallbackYouTubeSource?.takeIf { it.id == activeYouTubeSourceId }
                            ?: transientYouTubeQueueSources.firstOrNull { it.id == activeYouTubeSourceId }
                            ?: youtubeSources.firstOrNull()
                        if (source == null) {
                            navigate(Screen.YouTube)
                        } else {
                            YouTubeStreamScreen(
                                source = source,
                                onBack = { goBack() },
                                onOpenExternal = {
                                    if (premiumDeckOfflineActive) {
                                        Toast.makeText(context, "Offline Deck blocks online source links", Toast.LENGTH_SHORT).show()
                                    } else {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                                        }.onFailure {
                                            Toast.makeText(context, "No app can open this source", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onInfo = { infoDialog = youtubeSourceInfo(source) },
                            )
                        }
                    }
                    Screen.Settings -> PowerSettingsScreen(
                        settings = pulseSettingsState.copy(
                            audio = audioEngineState,
                            background = backgroundSettings,
                        ).normalized(),
                        onSettings = { next ->
                            val normalizedNext = next.normalized()
                            pulseSettingsState = normalizedNext
                            audioEngineState = normalizedNext.audio
                            backgroundSettings = normalizedNext.background
                        },
                        onBack = { leaveSettingsRoute() },
                        launchTarget = settingsLaunchTarget,
                        launchRequestKey = settingsLaunchRequestKey,
                        backRequestKey = settingsBackRequestKey,
                        onExportSettings = {
                            pendingSettingsExportKind = SettingsExportKind.Settings
                            settingsExportLauncher.launch("pulsedeck-portable-settings.json")
                        },
                        onExportPrivateBackup = {
                            pendingSettingsExportKind = SettingsExportKind.PrivateBackup
                            settingsExportLauncher.launch("pulsedeck-private-backup.json")
                        },
                        onImportSettings = { settingsImportLauncher.launch(arrayOf("application/json", "text/*")) },
                        onExportDiagnostics = {
                            pendingSettingsExportKind = SettingsExportKind.Diagnostics
                            settingsExportLauncher.launch("pulsedeck-diagnostics.json")
                        },
                        albumArtCacheStatus = albumArtCacheStatusText,
                        onlineArtworkProviderConfigured = AlbumArtworkRuntime.provider.configured,
                        onRefreshArtwork = {
                            AlbumArtworkRuntime.invalidate()
                            showToastAllowingPlatformDiskChecks(context, "Artwork refresh queued")
                        },
                        onClearArtworkCache = {
                            albumArtCacheStatusText = "Cache status updating..."
                            appScope.launch {
                                val deleted = CacheBudgetManager.clearArtwork(context)
                                AlbumArtworkRuntime.invalidate()
                                albumArtCacheStatusText = runCatching {
                                    CacheBudgetManager.status(
                                        context = context.applicationContext,
                                        albumArtCacheMb = pulseSettingsState.albumArt.cacheSizeMb,
                                        lyricsCacheMb = pulseSettingsState.lyrics.cacheSizeMb,
                                        offlineSources = youtubeSources,
                                    )
                                }.getOrElse {
                                    "Cache status unavailable"
                                }
                                showToastAllowingPlatformDiskChecks(context, "Cleared $deleted artwork cache files")
                            }
                        },
                        onClearOnlineCache = {
                            albumArtCacheStatusText = "Cache status updating..."
                            appScope.launch {
                                val cacheFilesDeleted = CacheBudgetManager.clearOnlineCaches(context)
                                context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
                                    .edit()
                                    .remove(PREF_YOUTUBE_SEARCH_CACHE)
                                    .remove(PREF_STREAM_DISCOVERY_CACHE)
                                    .remove(PREF_STREAM_NEW_RELEASES_CACHE)
                                    .remove(PREF_STREAM_RELEASE_NOTIFICATION_READ_IDS)
                                    .apply()
                                streamDiscoverySnapshot = StreamDiscoverySnapshot()
                                streamNewReleaseSnapshot = StreamNewReleaseSnapshot()
                                streamReleaseNotificationReadIds = emptySet()
                                youtubeSearchResults = emptyList()
                                youtubeSearchSuggestions = emptyList()
                                youtubeSearchError = null
                                premiumDeckPreparedPreviewSources = emptyMap()
                                cancelPremiumDeckSearchPreview(clearPreviewStates = true)
                                AlbumArtworkRuntime.invalidate()
                                LyricsRuntime.invalidate()
                                albumArtCacheStatusText = runCatching {
                                    CacheBudgetManager.status(
                                        context = context.applicationContext,
                                        albumArtCacheMb = pulseSettingsState.albumArt.cacheSizeMb,
                                        lyricsCacheMb = pulseSettingsState.lyrics.cacheSizeMb,
                                        offlineSources = youtubeSources,
                                    )
                                }.getOrElse {
                                    "Cache status unavailable"
                                }
                                showToastAllowingPlatformDiskChecks(context, "Cleared $cacheFilesDeleted cached online files")
                            }
                        },
                        onClearSearchHistory = {
                            context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
                                .edit()
                                .remove(PREF_YOUTUBE_SEARCH_CACHE)
                                .apply()
                            youtubeSearchResults = emptyList()
                            youtubeSearchSuggestions = emptyList()
                            youtubeSearchError = null
                            premiumDeckPreparedPreviewSources = emptyMap()
                            cancelPremiumDeckSearchPreview(clearPreviewStates = true)
                            Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                        },
                        onClearRecentlyPlayed = {
                            saveStreamHistory(emptyList())
                            Toast.makeText(context, "Recently played cleared", Toast.LENGTH_SHORT).show()
                        },
                        onResetPremiumDeckPersonalization = {
                            appScope.launch {
                                personalizationRepository.reset()
                                personalizationMixes = emptyList()
                                Toast.makeText(context, "PremiumDeck personalization reset", Toast.LENGTH_SHORT).show()
                            }
                        },
                        premiumDeckModelStatusTitle = premiumDeckModelStatus.title,
                        premiumDeckModelStatusBody = premiumDeckModelStatus.body,
                        onDeleteDownloads = {
                            appScope.launch(Dispatchers.IO) {
                                val downloaded = youtubeSources.filter { it.downloadedUri != null || it.downloadState == YouTubeDownloadState.Downloaded }
                                downloaded.forEach { source ->
                                    source.downloadedUri?.let { raw ->
                                        runCatching { context.contentResolver.delete(Uri.parse(raw), null, null) }
                                    }
                                }
                                val nextSources = youtubeSources.map { source ->
                                    if (source.downloadedUri != null || source.downloadState == YouTubeDownloadState.Downloaded || source.status == YouTubeSourceStatus.Downloaded) {
                                        source.copy(
                                            downloadedUri = null,
                                            downloadState = YouTubeDownloadState.None,
                                            downloadProgress = 0,
                                            status = if (source.status == YouTubeSourceStatus.Downloaded) YouTubeSourceStatus.StreamReady else source.status,
                                        )
                                    } else {
                                        source
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    saveYouTubeShelf(nextSources)
                                    Toast.makeText(context, "Deleted ${downloaded.size} saved stream downloads", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                    }
                }
                activeCategoryMotion?.let { motionState ->
                    CategorySharedElement(
                        motion = motionState,
                        onFinished = {
                            if (activeCategoryMotion?.nonce == motionState.nonce) {
                                activeCategoryMotion = null
                            }
                        },
                    )
                }
                if (!playerOpen && !miniDismissed && hasPlayableTrack && searchDiscoveryPreviewCollection == null) {
                    Mini(
                        track = track,
                        trackDirection = trackSlideDirection,
                        screen = screen,
                        durationMillis = track.durationMillis,
                        progressController = progressController,
                        playing = playing,
                        onOpen = { openPlayerFromCurrentSurface() },
                        onPlay = {
                            val nextPlaying = !playing
                            playing = nextPlaying
                            if (nextPlaying) miniDismissed = false
                        },
                        onSeek = { seekToProgress(it) },
                        onNext = { moveNextTrack(autoEnded = false) },
                        onPrev = { movePreviousTrack() },
                        onDismiss = { miniDismissed = true },
                        onScreen = {
                            navigateDock(it)
                        },
                        miniPlayerAppearance = pulseSettingsState.playerUi.miniPlayerAppearance,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            if (playerOpen && hasPlayableTrack) {
                val playerQueueTracks = visiblePlayerQueueTracks()
                val playerQueueReasons = if (activeStreamTrack != null) streamQueueReasonsByTrackKey() else emptyMap()
                val activePlayerSource = if (activeStreamTrack != null) {
                    queueYouTubeSource(activeYouTubeSourceId)
                        ?: fallbackYouTubeSource?.takeIf { fallback -> fallback.id == activeYouTubeSourceId }
                        ?: transientYouTubeQueueSources.firstOrNull { source -> source.id == activeYouTubeSourceId }
                } else {
                    null
                }
                val artistWorksSourceMode = when {
                    activeStreamTrack == null -> ArtistWorksSourceMode.LocalFile
                    activeYouTubeSourceId == null -> ArtistWorksSourceMode.PulseRadio
                    premiumDeckOfflineActive -> ArtistWorksSourceMode.PremiumDeckOffline
                    else -> ArtistWorksSourceMode.PremiumDeckOnline
                }
                val fullPlayerContent = pulseSettingsState.playerUi.fullPlayerContent.normalized()
                val artistNetworkPolicy = NetworkPolicyController.currentPolicy(context, pulseSettingsState.misc.online)
                val localArtistLookupWifiAllowed = artistNetworkPolicy.network.networkType == PulseNetworkType.Wifi &&
                    !artistNetworkPolicy.network.isMetered &&
                    !artistNetworkPolicy.effectiveDataSaver &&
                    !artistNetworkPolicy.network.isNoNetwork
                val artistWorksFetchPolicy = when (artistWorksSourceMode) {
                    ArtistWorksSourceMode.PremiumDeckOnline -> ArtistWorksFetchPolicy.UserTriggeredOnlineAllowed
                    ArtistWorksSourceMode.PremiumDeckOffline -> ArtistWorksFetchPolicy.OfflineOnly
                    ArtistWorksSourceMode.LocalFile -> when (fullPlayerContent.localArtistDiscoveryPolicy) {
                        LocalArtistDiscoveryPolicy.AskEveryTime -> ArtistWorksFetchPolicy.UserConfirmationRequired
                        LocalArtistDiscoveryPolicy.WifiOnlyAfterTap -> if (localArtistLookupWifiAllowed) {
                            ArtistWorksFetchPolicy.UserConfirmationRequired
                        } else {
                            ArtistWorksFetchPolicy.OfflineOnly
                        }
                        LocalArtistDiscoveryPolicy.Never -> ArtistWorksFetchPolicy.OfflineOnly
                    }
                    ArtistWorksSourceMode.PulseRadio -> ArtistWorksFetchPolicy.OfflineOnly
                    ArtistWorksSourceMode.Unknown -> ArtistWorksFetchPolicy.AutoShowCachedOnly
                }
                val playerArtistSourcePool = remember(
                    youtubeSources,
                    transientYouTubeQueueSources,
                    streamDiscoverySnapshot.results,
                    streamNewReleaseSnapshot.results,
                    premiumChartMatches,
                    activeYouTubeQueueIds,
                    activePlayerSource,
                ) {
                    (
                        youtubeSources +
                            transientYouTubeQueueSources +
                            activeYouTubeQueueIds.mapNotNull { sourceId -> queueYouTubeSource(sourceId) } +
                            streamDiscoverySnapshot.results.map { result -> result.source } +
                            streamNewReleaseSnapshot.results.map { result -> result.source } +
                            premiumChartMatches.map { match -> match.result.source } +
                            listOfNotNull(activePlayerSource)
                        )
                        .distinctBy { source -> source.streamDistinctKey() }
                }
                val playerArtistContext = remember(
                    libraryTracks,
                    playerArtistSourcePool,
                    followedStreamArtists,
                    activePlayerSource,
                    track.stableKey(),
                    track.artist,
                    track.title,
                ) {
                    buildPlayerArtistContext(
                        track = track,
                        libraryTracks = libraryTracks,
                        streamSources = playerArtistSourcePool,
                        followedArtists = followedStreamArtists,
                        activeSource = activePlayerSource,
                    )
                }
                val artistContinuationFetchState = playerArtistContext.primaryArtist?.let { artist ->
                    artistContinuationFetchStates[artist.normalizedName] ?: ArtistContinuationFetchState(artistKey = artist.normalizedName)
                } ?: ArtistContinuationFetchState()
                val launchHandoff = playerLaunchHandoff
                    ?.takeIf { !playerOpenFromMini && it.trackKey == track.stableKey() }
                val premiumSaveSource = activePlayerSource?.takeIf { activeStreamTrack != null && activeYouTubeSourceId != null }
                val radioSaveStation = activeRadioStationForFullPlayer()
                val fullPlayerSaveState = when {
                    premiumSaveSource != null -> {
                        val persisted = existingFullPlayerSaveSource(premiumSaveSource) ?: premiumSaveSource
                        val sourceKeys = (persisted.streamIdentityKeys() + premiumSaveSource.streamIdentityKeys()).toSet()
                        val editablePlaylists = youtubePlaylists.filter { it.origin != YouTubePlaylistOrigin.SavedMix }
                        FullPlayerSaveSheetState(
                            sourceKind = FullPlayerSaveSourceKind.PremiumDeck,
                            title = track.title,
                            subtitle = track.artist.ifBlank { PREMIUMDECK_SOURCE_NAME },
                            likedAvailable = true,
                            liked = persisted.reaction == YouTubeReaction.Liked,
                            premiumPlaylists = editablePlaylists.map { playlist ->
                                FullPlayerSavePlaylistOption(
                                    id = playlist.id,
                                    title = playlist.title.ifBlank { "Untitled Playlist" },
                                    subtitle = "${playlist.origin.label} - ${playlist.sourceIds.size} sources",
                                    saved = playlist.sourceIds.any { it in sourceKeys },
                                )
                            },
                            canCreatePremiumPlaylist = true,
                        )
                    }
                    radioSaveStation != null -> FullPlayerSaveSheetState(
                        sourceKind = FullPlayerSaveSourceKind.PulseRadio,
                        title = track.title,
                        subtitle = track.artist.ifBlank { "PulseRadio" },
                        radioFavoriteAvailable = true,
                        radioFavoriteSaved = radioSaveStation.isFavorite(favoriteRadioStationKeys),
                    )
                    else -> {
                        val trackKey = track.stableKey()
                        FullPlayerSaveSheetState(
                            sourceKind = FullPlayerSaveSourceKind.LocalFile,
                            title = track.title,
                            subtitle = track.artist.ifBlank { track.album.title },
                            likedAvailable = true,
                            liked = trackKey in likedTrackKeys,
                            localPlaylistAvailable = true,
                            localPlaylistSaved = trackKey in playlistTrackKeys,
                        )
                    }
                }
                MotionSceneHost(
                    routeKey = "player-${track.uri ?: track.title}",
                    pattern = if (launchHandoff != null) MotionPattern.None else MotionPattern.Panel,
                    onMotionActiveChange = { playerSceneMotionInFlight = it },
                ) {
                    Player(
                        track = track,
                        screen = screen,
                        durationMillis = track.durationMillis,
                        progressController = progressController,
                        waveform = waveform,
                        playing = playing,
                        shuffleMode = shuffleMode,
                        repeatMode = repeatMode,
                        sleepTimerLabel = sleepTimerLabel,
                        waveformVisible = waveformVisible,
                        waveformAnimating = waveformAnimationActive,
                        utilityButtons = pulseSettingsState.playerUi.utilityButtons,
                        landscapeUtilityButtons = pulseSettingsState.playerUi.landscapeUtilityButtons,
                        portraitButtonRows = pulseSettingsState.playerUi.portraitButtonRows,
                        landscapeButtonRows = pulseSettingsState.playerUi.landscapeButtonRows,
                        utilityButtonSize = pulseSettingsState.playerUi.utilityButtonSize,
                        utilityButtonsScrollable = pulseSettingsState.playerUi.utilityButtonsScrollable,
                        showGestureHints = pulseSettingsState.playerUi.showGestureHints,
                        landscapeSplitEnabled = pulseSettingsState.playerUi.enableLandscapeSplit,
                        miniPlayerAppearance = pulseSettingsState.playerUi.miniPlayerAppearance,
                        fullPlayerContent = fullPlayerContent,
                        liked = track.stableKey() in likedTrackKeys,
                        bookmarked = track.stableKey() in bookmarkedTrackKeys,
                        savedAnywhere = fullPlayerSaveState.savedAnywhere,
                        queueTracks = playerQueueTracks,
                        queueTrackReasons = playerQueueReasons,
                        playbackContextLine = if (activeStreamTrack != null) premiumDeckPlayerContextLine() else null,
                        artistContext = playerArtistContext,
                        artistContinuationFetchState = artistContinuationFetchState,
                        artistWorksSourceMode = artistWorksSourceMode,
                        artistWorksFetchPolicy = artistWorksFetchPolicy,
                        queueIsCustom = activeStreamTrack != null && playerQueueTracks.isNotEmpty() || activeStreamTrack == null && manualQueueTrackKeys.isNotEmpty(),
                        openFromMini = playerOpenFromMini,
                        launchHandoff = launchHandoff,
                        openKey = playerOpenKey,
                        closeRequestKey = playerCloseRequestKey,
                        onPlaying = { playing = it },
                        onArtistContinuationSource = { source, queueSources ->
                            playYouTubeSourceQueue(
                                source = source,
                                queueSources = queueSources.ifEmpty { listOf(source) },
                                playbackOrigin = PremiumDeckPlaybackOrigin.Artist,
                                playbackTitle = premiumDeckSessionTitle(PremiumDeckPlaybackOrigin.Artist),
                                playbackSubtitle = source.author.cleanStreamArtistName().ifBlank { source.author },
                                playbackOriginKey = source.author.cleanStreamArtistName().normalizedSearchText(),
                                playbackReason = PremiumDeckQueueReason("Artist match", "same artist"),
                                openPlayerImmediately = true,
                            )
                        },
                        onLoadArtistContinuation = { artist, userConfirmedOnline ->
                            loadPlayerArtistContinuation(
                                artist = artist,
                                activeSource = activePlayerSource,
                                sourceMode = artistWorksSourceMode,
                                fetchPolicy = artistWorksFetchPolicy,
                                userConfirmedOnline = userConfirmedOnline,
                            )
                        },
                        onOpenArtistRelease = { release -> openArtistReleaseTracklist(release) },
                        onOpenArtistDiscography = { snapshot ->
                            val sources = snapshot.discographyItems()
                                .map { it.source.copy(reviewState = YouTubeReviewState.Accepted, thumbnailUrl = it.source.bestThumbnailUrl() ?: it.source.thumbnailUrl) }
                                .distinctBy { it.streamDistinctKey() }
                            if (sources.isEmpty()) {
                                val message = if (snapshot.loadedAtEpochMs > 0L) {
                                    "No albums found for this artist yet"
                                } else {
                                    "Load artist snapshot first"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            } else {
                                val title = "${snapshot.artist.displayName} Discography"
                                youtubeDialog = YouTubeDialogState.StreamCollectionActions(
                                    StreamCollection(
                                        id = "artist-discography-${snapshot.artist.normalizedName}-${snapshot.loadedAtEpochMs}".normalizedSearchText(),
                                        title = title,
                                        subtitle = buildList {
                                            add("${sources.size} album preview tracks")
                                            add(snapshot.confidence.label)
                                            if (snapshot.fromCache) add("cached")
                                        }.joinToString("  |  "),
                                        kind = StreamCollectionKind.Artist,
                                        sources = sources,
                                        accentColor = 0xFF3AA7FF.toInt(),
                                        canSave = true,
                                    ),
                                )
                            }
                        },
                        onDeviceArtistTrack = { selected -> playLocalTrackFromList(selected) },
                        onTrackMenu = {
                            val source = queueYouTubeSource(activeYouTubeSourceId) ?: fallbackYouTubeSource
                            if (activeStreamTrack != null && source != null) {
                                youtubeDialog = YouTubeDialogState.SourceOfflineSave(source)
                            } else {
                                trackActionsOpen = true
                            }
                        },
                        onShuffleClick = {
                            playbackModeDialog = null
                            sleepTimerDialogOpen = false
                            shuffleMode = nextShuffleMode(shuffleMode)
                        },
                        onShuffleLongClick = {
                            sleepTimerDialogOpen = false
                            playbackModeDialog = PlayerModeMenu.Shuffle
                        },
                        onRepeatClick = {
                            playbackModeDialog = null
                            sleepTimerDialogOpen = false
                            repeatMode = nextRepeatMode(repeatMode)
                        },
                        onRepeatLongClick = {
                            sleepTimerDialogOpen = false
                            playbackModeDialog = PlayerModeMenu.Repeat
                        },
                        onTimer = {
                            playbackModeDialog = null
                            sleepTimerDialogOpen = true
                        },
                        onVisualizer = { waveformVisible = !waveformVisible },
                        onLike = { setLiked(track, track.stableKey() !in likedTrackKeys) },
                        onBookmark = { toggleBookmark(track) },
                        onSave = { fullPlayerSaveSheetOpen = true },
                        onInfo = { showTrackMetadata(track) },
                        onLyrics = { lyricsTrack = track },
                        onShowCategory = {
                            requestClosePlayer()
                            openActiveStreamCategory()
                        },
                        onSeekRelative = { seekBy(it) },
                        onClose = {
                            closePlayerToPlaybackContext()
                        },
                        onBackGesture = {
                            prepareLibrarySurfaceForPlayerMinimize()
                        },
                        onNext = { moveNextTrack(autoEnded = false) },
                        onPrev = { movePreviousTrack() },
                        onNextAlbum = { if (activeStreamTrack == null) moveAlbumWithinLibrary(1) },
                        onPrevAlbum = { if (activeStreamTrack == null) moveAlbumWithinLibrary(-1) },
                        onSeek = { seekToProgress(it) },
                        onQueueMove = { fromIndex, toIndex -> movePlayerQueueItem(fromIndex, toIndex) },
                        onQueueRemove = { index -> removePlayerQueueItem(index) },
                        onQueueInsert = { index, item -> insertPlayerQueueItem(index, item) },
                        onQueueClear = { clearVisiblePlayerQueue() },
                        onScreen = { target ->
                            playbackState.closePlayer()
                            navigateDock(target)
                        },
                    )
                }
                if (fullPlayerSaveSheetOpen) {
                    FullPlayerSaveSheet(
                        state = fullPlayerSaveState,
                        onDismiss = { fullPlayerSaveSheetOpen = false },
                        onToggleLiked = {
                            when (fullPlayerSaveState.sourceKind) {
                                FullPlayerSaveSourceKind.PremiumDeck -> premiumSaveSource?.let { source ->
                                    val adding = !fullPlayerSaveState.liked
                                    setFullPlayerStreamLiked(source, !fullPlayerSaveState.liked)
                                    Toast.makeText(
                                        context,
                                        if (adding) "Added to Liked Songs" else "Removed from Liked Songs",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                FullPlayerSaveSourceKind.LocalFile -> {
                                    val adding = track.stableKey() !in likedTrackKeys
                                    setLiked(track, adding)
                                    Toast.makeText(
                                        context,
                                        if (adding) "Added to Liked Songs" else "Removed from Liked Songs",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                FullPlayerSaveSourceKind.PulseRadio -> Unit
                            }
                        },
                        onToggleLocalPlaylist = {
                            val adding = track.stableKey() !in playlistTrackKeys
                            togglePlaylist(track)
                            Toast.makeText(
                                context,
                                if (adding) "Added to Pulse List" else "Removed from Pulse List",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onToggleRadioFavorite = {
                            radioSaveStation?.let { station ->
                                val adding = !station.isFavorite(favoriteRadioStationKeys)
                                toggleRadioStationFavorite(station)
                                Toast.makeText(
                                    context,
                                    if (adding) "Saved station" else "Removed saved station",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onTogglePremiumPlaylist = { playlistId ->
                            premiumSaveSource?.let { source ->
                                val option = fullPlayerSaveState.premiumPlaylists.firstOrNull { it.id == playlistId }
                                toggleFullPlayerStreamPlaylist(source, playlistId)
                                Toast.makeText(
                                    context,
                                    if (option?.saved == true) {
                                        "Removed from ${option.title}"
                                    } else {
                                        "Added to ${option?.title ?: "playlist"}"
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onCreatePremiumPlaylist = {
                            premiumSaveSource?.let { source ->
                                val persisted = ensureFullPlayerSaveSource(source)
                                fullPlayerSaveSheetOpen = false
                                youtubeDialog = YouTubeDialogState.PlaylistEditor(
                                    YouTubePlaylist(
                                        id = newYouTubePlaylistId(),
                                        title = "",
                                        sourceIds = listOf(persisted.id),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
            if (trackActionsOpen) {
                TrackActionsDialog(
                    track = track,
                    liked = track.stableKey() in likedTrackKeys,
                    disliked = track.stableKey() in dislikedTrackKeys,
                    bookmarked = track.stableKey() in bookmarkedTrackKeys,
                    inPlaylist = track.stableKey() in playlistTrackKeys,
                    onDismiss = { trackActionsOpen = false },
                    onLike = { setLiked(track, track.stableKey() !in likedTrackKeys) },
                    onDislike = { setDisliked(track, track.stableKey() !in dislikedTrackKeys) },
                    onBookmark = { toggleBookmark(track) },
                    onPlaylist = { togglePlaylist(track) },
                    onDelete = {
                        trackActionsOpen = false
                        confirmDeleteTrack = track
                    },
                    onMetadata = { quality ->
                        trackActionsOpen = false
                        showTrackMetadata(track, quality)
                    },
                    onLyrics = {
                        trackActionsOpen = false
                        lyricsTrack = track
                    },
                    onArtist = {
                        trackActionsOpen = false
                        showSimpleInfo(
                            "Artist",
                            track.artist,
                            listOf(
                                "Songs in library" to libraryTracks.count { it.artist == track.artist }.toString(),
                                "Total time" to formatDuration(libraryTracks.filter { it.artist == track.artist }.sumOf { it.durationMillis }),
                            ),
                        )
                    },
                    onAlbum = {
                        trackActionsOpen = false
                        requestClosePlayer()
                        openAlbumDetail(track.album.key)
                    },
                    onFolder = {
                        trackActionsOpen = false
                        selectedFolderPath = normalizedFolderPath(track)
                        requestClosePlayer()
                        navigate(Screen.FolderHierarchy)
                    },
                    onGenre = {
                        trackActionsOpen = false
                        showSimpleInfo("Genre", track.genre ?: "Unknown genre", listOf("Track" to track.title, "Artist" to track.artist))
                    },
                    onChangeArt = {
                        trackActionsOpen = false
                        albumArtPickerTarget = AlbumArtPickerTarget(
                            album = track.album,
                            sourceUris = (track.album.artSourceUris + listOfNotNull(track.uri, track.album.sourceUri)).distinct(),
                        )
                    },
                )
            }
            if (quickAddDialogOpen) {
                QuickAddDialog(
                    onDismiss = { quickAddDialogOpen = false },
                    onScanLocal = {
                        quickAddDialogOpen = false
                        scanLocalLibrary()
                    },
                    onAddYouTube = { source ->
                        if (premiumDeckOfflineActive) {
                            Toast.makeText(context, "Turn off Offline Deck to add new PremiumDeck sources", Toast.LENGTH_SHORT).show()
                        } else {
                            val reviewSource = source.copy(reviewState = YouTubeReviewState.Inbox)
                            val nextSources = listOf(reviewSource) + youtubeSources.filterNot { it.url == source.url || it.id == source.id }
                            saveYouTubeShelf(nextSources)
                            youtubeTab = YouTubeLibraryTab.Sources
                            youtubeShelf = YouTubeSmartShelf.Inbox
                            Toast.makeText(context, "Added to Stream Inbox", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            youtubeDialog?.let { dialog ->
                when (dialog) {
                    is YouTubeDialogState.StreamCollectionActions -> {
                        if (!playerOpen) {
                            StreamCollectionActionsDialog(
                                collection = dialog.collection,
                                resolvingSourceId = resolvingYouTubeSourceId,
                                activeSourceId = activeYouTubeSourceId,
                                onDismiss = { youtubeDialog = null },
                                onPlayAll = {
                                    playStreamCollection(dialog.collection)
                                },
                                onSave = { saveStreamCollectionAsPlaylist(dialog.collection) },
                                onKeepOffline = { keepStreamCollectionOffline(dialog.collection) },
                                followedArtistKeys = followedStreamArtists.map { it.key }.toSet(),
                                onToggleFollowArtist = { artist -> toggleFollowedStreamArtist(artist) },
                                onSource = { source ->
                                    playStreamCollectionFromSource(dialog.collection, source)
                                },
                                onSourceActions = { source, queueIds ->
                                    youtubeDialog = YouTubeDialogState.SourceActions(source, queueIds)
                                },
                            )
                        }
                    }
                    is YouTubeDialogState.SourceActions -> {
                        val source = youtubeSources.firstOrNull { it.id == dialog.source.id } ?: dialog.source
                        YouTubeSourceActionsDialog(
                            source = source,
                            playlists = youtubePlaylists,
                            capabilities = resolverCapabilities,
                            artistFollowed = source.author.cleanStreamArtistName().normalizedSearchText() in followedStreamArtists.map { it.key }.toSet(),
                            onDismiss = { youtubeDialog = null },
                            onPlay = {
                                youtubeDialog = null
                                playYouTubeSource(source, dialog.queueIds.ifEmpty { listOf(source.id) })
                            },
                            onAccept = {
                                updateYouTubeSource(source.id) { it.copy(reviewState = YouTubeReviewState.Accepted) }
                                youtubeDialog = YouTubeDialogState.SourceActions(source.copy(reviewState = YouTubeReviewState.Accepted))
                            },
                            onRename = { youtubeDialog = YouTubeDialogState.SourceRename(source) },
                            onAddToPlaylist = { youtubeDialog = YouTubeDialogState.AddSourceToPlaylist(source) },
                            onToggleLike = {
                                setStreamReaction(source, YouTubeReaction.Liked)
                                val liked = source.reaction != YouTubeReaction.Liked
                                youtubeDialog = YouTubeDialogState.SourceActions(
                                    source.copy(
                                        reaction = if (liked) YouTubeReaction.Liked else YouTubeReaction.Neutral,
                                        bookmarked = if (liked) true else source.bookmarked,
                                    ),
                                    dialog.queueIds,
                                )
                            },
                            onToggleArtistFollow = { toggleFollowedStreamArtist(source.author) },
                            onKeepOffline = { keepYouTubeOffline(source) },
                            onRemoveDownload = { removeYouTubeDownload(source) },
                            onToggleSkip = { updateYouTubeSource(source.id) { it.copy(skipSegmentsEnabled = !it.skipSegmentsEnabled) } },
                            onToggleTrim = { updateYouTubeSource(source.id) { it.copy(trimSilenceOnDownload = !it.trimSilenceOnDownload) } },
                            onMetadata = {
                                youtubeDialog = null
                                infoDialog = youtubeSourceInfo(source)
                            },
                            onOpenYouTube = {
                                if (premiumDeckOfflineActive) {
                                    Toast.makeText(context, "Offline Deck blocks online source links", Toast.LENGTH_SHORT).show()
                                } else {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }
                                        .onFailure { Toast.makeText(context, "No app can open this source", Toast.LENGTH_SHORT).show() }
                                }
                            },
                            onRemove = {
                                youtubeDialog = null
                                removeYouTubeSourceEverywhere(source)
                                Toast.makeText(context, "Removed from Stream Library", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    is YouTubeDialogState.SourceOfflineSave -> {
                        val source = youtubeSources.firstOrNull { it.id == dialog.source.id } ?: dialog.source
                        PremiumDeckOfflineSaveDialog(
                            source = source,
                            onDismiss = { youtubeDialog = null },
                            onKeepOffline = {
                                keepYouTubeOffline(source)
                            },
                            onOpenOfflineSaved = {
                                youtubeDialog = null
                                requestClosePlayer()
                                youtubeTab = YouTubeLibraryTab.Downloads
                                youtubeShelf = YouTubeSmartShelf.SavedOffline
                                navigate(Screen.YouTube)
                            },
                            onSourceActions = {
                                youtubeDialog = YouTubeDialogState.SourceActions(source, activeYouTubeQueueIds)
                            },
                        )
                    }
                    is YouTubeDialogState.SourceRename -> {
                        val source = youtubeSources.firstOrNull { it.id == dialog.source.id } ?: dialog.source
                        YouTubeSourceRenameDialog(
                            source = source,
                            onDismiss = { youtubeDialog = YouTubeDialogState.SourceActions(source) },
                            onSave = { title, author ->
                                updateYouTubeSource(source.id) { it.copy(title = title, author = author) }
                                youtubeDialog = YouTubeDialogState.SourceActions(source.copy(title = title, author = author))
                            },
                        )
                    }
                    is YouTubeDialogState.AddSourceToPlaylist -> {
                        val source = youtubeSources.firstOrNull { it.id == dialog.source.id } ?: dialog.source
                        AddSourceToPlaylistDialog(
                            source = source,
                            playlists = youtubePlaylists,
                            onDismiss = { youtubeDialog = YouTubeDialogState.SourceActions(source) },
                            onNewPlaylist = { youtubeDialog = YouTubeDialogState.PlaylistEditor(YouTubePlaylist(id = newYouTubePlaylistId(), title = "", sourceIds = listOf(source.id))) },
                            onPlaylist = { playlist ->
                                addSourceToYouTubePlaylist(source, playlist)
                                youtubeDialog = YouTubeDialogState.SourceActions(source)
                            },
                        )
                    }
                    is YouTubeDialogState.PlaylistEditor -> {
                        YouTubePlaylistEditorDialog(
                            playlist = dialog.playlist,
                            onDismiss = { youtubeDialog = null },
                            onSave = { playlist ->
                                upsertYouTubePlaylist(playlist)
                                youtubeDialog = null
                            },
                            onImportYouTubePlaylist = { title, url, playlistId, accentColor ->
                                youtubeDialog = null
                                importYouTubePlaylistLink(title, url, playlistId, accentColor)
                            },
                            onImportSpotifyPlaylist = { title, url, accentColor ->
                                youtubeDialog = null
                                importSpotifyPlaylistLink(title, url, accentColor)
                            },
                        )
                    }
                    is YouTubeDialogState.PlaylistActions -> {
                        val playlist = youtubePlaylists.firstOrNull { it.id == dialog.playlist.id } ?: dialog.playlist
                        YouTubePlaylistActionsDialog(
                            playlist = playlist,
                            sources = streamPlaylistSourceCatalog(),
                            onDismiss = { youtubeDialog = null },
                            onPlay = {
                                youtubeDialog = null
                                playYouTubePlaylist(playlist, shuffle = false)
                            },
                            onShuffle = {
                                youtubeDialog = null
                                playYouTubePlaylist(playlist, shuffle = true)
                            },
                            onAddSources = { youtubeDialog = YouTubeDialogState.PlaylistSourcePicker(playlist) },
                            onEditMix = { youtubeDialog = YouTubeDialogState.PlaylistMixEditor(playlist) },
                            onKeepOffline = { keepPlaylistOffline(playlist) },
                            onRename = { youtubeDialog = YouTubeDialogState.PlaylistEditor(playlist) },
                            onDelete = {
                                saveYouTubePlaylistShelf(youtubePlaylists.filterNot { it.id == playlist.id })
                                youtubeDialog = null
                                Toast.makeText(context, "Deleted playlist", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    is YouTubeDialogState.PlaylistSourcePicker -> {
                        val playlist = youtubePlaylists.firstOrNull { it.id == dialog.playlist.id } ?: dialog.playlist
                        YouTubePlaylistSourcePickerDialog(
                            playlist = playlist,
                            sources = youtubeSources.filter { it.reviewState == YouTubeReviewState.Accepted },
                            onDismiss = { youtubeDialog = YouTubeDialogState.PlaylistActions(playlist) },
                            onSave = { sourceIds ->
                                updateYouTubePlaylist(playlist.id) { it.copy(sourceIds = sourceIds, updatedMillis = System.currentTimeMillis()) }
                                youtubeDialog = YouTubeDialogState.PlaylistActions(playlist.copy(sourceIds = sourceIds))
                            },
                        )
                    }
                    is YouTubeDialogState.PlaylistMixEditor -> {
                        val playlist = youtubePlaylists.firstOrNull { it.id == dialog.playlist.id } ?: dialog.playlist
                        YouTubePlaylistMixEditorDialog(
                            playlist = playlist,
                            sources = streamPlaylistSourceCatalog(),
                            discoveryResults = streamDiscoverySnapshot.results,
                            playHistory = streamPlayHistory,
                            resolvingSourceId = resolvingYouTubeSourceId,
                            activeSourceId = activeYouTubeSourceId,
                            onDismiss = { youtubeDialog = null },
                            onPlayAll = {
                                youtubeDialog = null
                                playYouTubePlaylist(playlist, shuffle = false)
                            },
                            onShuffle = {
                                youtubeDialog = null
                                playYouTubePlaylist(playlist, shuffle = true)
                            },
                            onKeepOffline = { keepPlaylistOffline(playlist) },
                            onActions = { youtubeDialog = YouTubeDialogState.PlaylistActions(playlist) },
                            onPlaySource = { source ->
                                playYouTubeSourceQueue(
                                    source,
                                    resolveYouTubePlaylistSources(playlist),
                                    playbackOrigin = PremiumDeckPlaybackOrigin.Playlist,
                                    playbackTitle = premiumDeckSessionTitle(PremiumDeckPlaybackOrigin.Playlist),
                                    playbackSubtitle = playlist.title,
                                    playbackOriginKey = playlist.id,
                                    playbackReason = PremiumDeckQueueReason("Playlist", "from this list"),
                                )
                            },
                            onSourceActions = { source, queueIds ->
                                youtubeDialog = YouTubeDialogState.SourceActions(source, queueIds)
                            },
                            onRemoveSource = { source -> removeSourceFromEditableStreamPlaylist(source, playlist) },
                            onAddSource = { source -> addSourceToEditableStreamPlaylist(source, playlist) },
                        )
                    }
                }
            }
            playlistImportProgress?.let { progress ->
                PremiumDeckPlaylistImportProgressDialog(
                    progress = progress,
                    onDismiss = { playlistImportProgress = null },
                )
            }
            smartCachePromptSource?.let { source ->
                SmartCachePromptDialog(
                    source = source,
                    onDismiss = {
                        updateYouTubeSource(source.id) { it.copy(downloadState = YouTubeDownloadState.Prompted) }
                        smartCachePromptSource = null
                    },
                    onNeverAsk = {
                        updateYouTubeSource(source.id) { it.copy(neverPromptCache = true, downloadState = YouTubeDownloadState.Prompted) }
                        smartCachePromptSource = null
                    },
                    onKeepOffline = {
                        smartCachePromptSource = null
                        keepYouTubeOffline(source)
                    },
                )
            }
            confirmDeleteTrack?.let { target ->
                ConfirmDeleteDialog(
                    track = target,
                    onDismiss = { confirmDeleteTrack = null },
                    onConfirm = {
                        confirmDeleteTrack = null
                        requestDeleteTrack(target)
                    },
                )
            }
            infoDialog?.let { dialog ->
                InfoDialog(dialog, onDismiss = { infoDialog = null })
            }
            lyricsTrack?.let { target ->
                LyricsDialog(
                    track = target,
                    progressController = progressController,
                    progressEnabled = target.stableKey() == track.stableKey(),
                    onSyncedActiveChange = { syncedLyricsTickerActive = it },
                    onDismiss = {
                        syncedLyricsTickerActive = false
                        lyricsTrack = null
                    },
                )
            }
            albumArtPickerTarget?.let { target ->
                AlbumArtPickerDialog(
                    target = target,
                    settings = pulseSettingsState.albumArt,
                    onDismiss = { albumArtPickerTarget = null },
                    onApplied = { albumArtPickerTarget = null },
                )
            }
            playbackModeDialog?.let { menu ->
                PlaybackModeDialog(
                    menu = menu,
                    shuffleMode = shuffleMode,
                    repeatMode = repeatMode,
                    onDismiss = { playbackModeDialog = null },
                    onShuffleMode = {
                        shuffleMode = it
                        playbackModeDialog = null
                    },
                    onRepeatMode = {
                        repeatMode = it
                        playbackModeDialog = null
                    },
                )
            }
            if (sleepTimerDialogOpen) {
                SleepTimerDialog(
                    currentMinutes = sleepTimerMinutes,
                    fadeOutEnabled = sleepTimerFadeOutEnabled,
                    onDismiss = { sleepTimerDialogOpen = false },
                    onDisable = {
                        disableSleepTimer()
                        sleepTimerDialogOpen = false
                    },
                    onConfirm = { minutes, fadeOut ->
                        setSleepTimer(minutes, fadeOut)
                        sleepTimerDialogOpen = false
                    },
                )
            }
        }
        }
    }
}

@Composable
private fun AudioDeckRoute(
    state: AudioEngineState,
    currentTrack: Track,
    progressController: PlaybackProgressController,
    onState: (AudioEngineState) -> Unit,
    onPreset: (AudioPreset) -> Unit,
    onAdvanced: () -> Unit,
    onBack: () -> Unit,
) {
    val positionMillis by progressController.positionMillis.collectAsState()
    AudioDeckScreen(
        state = state,
        currentTrack = currentTrack,
        positionMillis = positionMillis,
        onState = onState,
        onPreset = onPreset,
        onAdvanced = onAdvanced,
        onBack = onBack,
    )
}

@Composable
private fun Media3PlaybackEffect(
    track: Track,
    queue: List<Track>,
    shuffleMode: ShuffleMode,
    repeatMode: PlaybackRepeatMode,
    playing: Boolean,
    volume: Float,
    audioState: AudioEngineState,
    positionTickerPolicy: PlaybackTickerPolicy,
    seekRequestKey: Int,
    seekRequestMillis: Long,
    progressController: PlaybackProgressController,
    sponsorSkipSourceId: String?,
    sponsorSkipEnabled: Boolean,
    sponsorSkipSegments: List<SponsorBlockSegment>,
    onEnded: () -> Unit,
    onMediaItemSelected: (String, Boolean) -> Unit,
    onPlaybackError: (String?, PlaybackException) -> Unit,
) {
    val context = LocalContext.current
    val playableQueue = remember(track, queue) { playableMediaQueue(track, queue) }
    val mediaQueueKey = remember(playableQueue) { playableQueue.joinToString("|") { it.stableKey() } }
    val latestEnded by rememberUpdatedState(onEnded)
    val latestMediaItemSelected by rememberUpdatedState(onMediaItemSelected)
    val latestPlaybackError by rememberUpdatedState(onPlaybackError)
    val latestTrackKey by rememberUpdatedState(track.stableKey())
    val latestPlaying by rememberUpdatedState(playing)
    val latestPositionTickerPolicy by rememberUpdatedState(positionTickerPolicy)
    val latestSponsorSkipSourceId by rememberUpdatedState(sponsorSkipSourceId)
    val latestSponsorSkipEnabled by rememberUpdatedState(sponsorSkipEnabled)
    val latestSponsorSkipSegments by rememberUpdatedState(sponsorSkipSegments)
    val skippedSponsorSegmentKey = remember { AtomicReference<String?>(null) }
    val effectiveAudioState = remember(audioState, track.stableKey(), track.loudnessMetadata) {
        audioState.withSourceLoudness(track.loudnessMetadata)
    }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var consumedSeekKey by remember { mutableIntStateOf(seekRequestKey) }

    LaunchedEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controller = withContext(Dispatchers.IO) { future.get() }
    }

    DisposableEffect(controller) {
        val activeController = controller
        if (activeController == null) {
            onDispose { }
        } else {
            val listener = object : Media3Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    activeController.currentMediaItem?.mediaId?.let { mediaId ->
                        YouTubeNetworkDiagnostics.reportMediaPlaybackState(mediaId, activeController.isPlaying, playbackState)
                    }
                    if (playbackState == Media3Player.STATE_ENDED && latestPlaying) latestEnded()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    activeController.currentMediaItem?.mediaId?.let { mediaId ->
                        YouTubeNetworkDiagnostics.reportMediaPlaybackState(mediaId, isPlaying, activeController.playbackState)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val mediaId = mediaItem?.mediaId ?: return
                    if (mediaId != latestTrackKey) {
                        latestMediaItemSelected(
                            mediaId,
                            reason == Media3Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    latestPlaybackError(activeController.currentMediaItem?.mediaId ?: latestTrackKey, error)
                }
            }
            activeController.addListener(listener)
            onDispose {
                activeController.removeListener(listener)
                activeController.release()
            }
        }
    }

    LaunchedEffect(controller, track.stableKey(), mediaQueueKey) {
        val mediaController = controller ?: return@LaunchedEffect
        val sameCurrentItem = mediaController.currentMediaItem?.mediaId == track.stableKey()
        if (!sameCurrentItem) {
            progressController.updateFromPlayback(0L)
            consumedSeekKey = seekRequestKey
        }
        if (playableQueue.isEmpty()) {
            mediaController.stop()
            mediaController.clearMediaItems()
            return@LaunchedEffect
        }
        val items = playableQueue.map { it.toMediaItem() }
        val startIndex = items.indexOfFirst { it.mediaId == track.stableKey() }.coerceAtLeast(0)
        if (!mediaController.hasSameMediaQueue(items) || !sameCurrentItem) {
            val startPosition = if (sameCurrentItem) mediaController.currentPosition.coerceAtLeast(0L) else 0L
            mediaController.setMediaItems(items, startIndex, startPosition)
            mediaController.shuffleModeEnabled = shuffleMode != ShuffleMode.Off && items.size > 1
            mediaController.repeatMode = media3RepeatMode(repeatMode)
            mediaController.prepare()
        } else if (mediaController.playbackState == Media3Player.STATE_IDLE) {
            mediaController.prepare()
        }
        if (!sameCurrentItem && seekRequestMillis > 0L) {
            runCatching {
                mediaController.seekTo(seekRequestMillis)
                progressController.updateFromSeek(seekRequestMillis)
            }
        }
        if (playing) mediaController.play()
    }

    LaunchedEffect(controller, shuffleMode, repeatMode, mediaQueueKey) {
        val mediaController = controller ?: return@LaunchedEffect
        mediaController.shuffleModeEnabled = shuffleMode != ShuffleMode.Off && mediaController.mediaItemCount > 1
        mediaController.repeatMode = media3RepeatMode(repeatMode)
    }

    LaunchedEffect(playing, controller) {
        val mediaController = controller ?: return@LaunchedEffect
        runCatching {
            if (playing) mediaController.play() else mediaController.pause()
        }
    }

    LaunchedEffect(volume, audioState, effectiveAudioState, controller) {
        val mediaController = controller ?: return@LaunchedEffect
        PulseAudioEngineStore.update(audioState)
        mediaController.volume = audioOutputGain(effectiveAudioState, volume)
    }

    LaunchedEffect(seekRequestKey, controller) {
        val mediaController = controller ?: return@LaunchedEffect
        if (seekRequestKey > consumedSeekKey && mediaController.mediaItemCount > 0) {
            runCatching {
                mediaController.seekTo(seekRequestMillis.coerceAtLeast(0L))
                progressController.updateFromSeek(mediaController.currentPosition)
            }
            consumedSeekKey = seekRequestKey
        }
    }

    LaunchedEffect(controller, playing, positionTickerPolicy, sponsorSkipSourceId, sponsorSkipEnabled, sponsorSkipSegments) {
        val mediaController = controller ?: return@LaunchedEffect
        var lastPositionMillis = -1L
        while (true) {
            val currentPositionMillis = runCatching { mediaController.currentPosition.coerceAtLeast(0L) }
                .getOrDefault(progressController.currentPositionMillis)
            val sponsorFineTicker = latestPlaying &&
                latestSponsorSkipEnabled &&
                latestSponsorSkipSegments.any { segment ->
                    val fineWindowStart = (segment.startMillis - SPONSOR_SKIP_FINE_TICKER_LOOKAHEAD_MS).coerceAtLeast(0L)
                    currentPositionMillis in fineWindowStart until segment.endMillis
                }
            val policy = if (sponsorFineTicker) {
                PlaybackTickerPolicy(PlaybackTickerMode.SponsorSkip, 250L)
            } else {
                latestPositionTickerPolicy
            }
            val intervalMillis = if (playing) policy.intervalMillis else 1000L
            if (playing && intervalMillis <= PLAYBACK_TICKER_DISABLED_INTERVAL_MS) {
                delay(1000L)
                continue
            }
            val thresholdMillis = if (playing) intervalMillis.coerceAtLeast(250L) else Long.MAX_VALUE
            val shouldEmit = lastPositionMillis < 0L ||
                (!playing && currentPositionMillis != lastPositionMillis) ||
                (playing && abs(currentPositionMillis - lastPositionMillis) >= thresholdMillis)
            if (shouldEmit) {
                lastPositionMillis = currentPositionMillis
                progressController.updateFromPlayback(currentPositionMillis)
                PulseDeckPlaybackDiagnostics.recordPositionEmission(policy, localOnly = true)
            }
            if (latestPlaying && latestSponsorSkipEnabled) {
                val segment = latestSponsorSkipSegments.firstOrNull { currentPositionMillis in it.startMillis until it.endMillis }
                PulseDeckPlaybackDiagnostics.recordSponsorSkipCheck(
                    fineTicker = sponsorFineTicker,
                    segmentHit = segment != null,
                )
                if (segment != null) {
                    val key = "${latestSponsorSkipSourceId.orEmpty()}:${segment.category}:${segment.startMillis}"
                    if (skippedSponsorSegmentKey.get() != key) {
                        skippedSponsorSegmentKey.set(key)
                        val targetMillis = (segment.endMillis + 250L).coerceAtLeast(0L)
                        runCatching {
                            mediaController.seekTo(targetMillis)
                            progressController.updateFromSeek(targetMillis)
                            lastPositionMillis = targetMillis
                        }
                    }
                } else {
                    skippedSponsorSegmentKey.set(null)
                }
            }
            delay(if (playing) intervalMillis.coerceAtLeast(250L) else 1000L)
        }
    }
}

private fun playableMediaQueue(current: Track, queue: List<Track>): List<Track> {
    val source = if (queue.isEmpty()) listOf(current) else queue
    val withCurrent = if (source.any { it.stableKey() == current.stableKey() }) source else listOf(current) + source
    return withCurrent
        .filter { it.uri != null }
        .distinctBy { it.stableKey() }
}

private fun buildPlayerArtistContext(
    track: Track,
    libraryTracks: List<Track>,
    streamSources: List<YouTubeSource>,
    followedArtists: List<FollowedStreamArtist>,
    activeSource: YouTubeSource?,
): PlayerArtistContext {
    val sourceArtist = activeSource
        ?.playerHighConfidenceMetadataArtist()
        ?.playerPrimaryArtistName()
        ?.takeIf { it.isNotBlank() }
        ?: activeSource
            ?.author
            ?.playerPrimaryArtistName()
            ?.takeIf { it.isNotBlank() }
    val trackArtist = track.artist.playerPrimaryArtistName().takeIf { it.isNotBlank() }
    val artistName = sourceArtist ?: trackArtist ?: "Unknown artist"
    val artistKey = artistName.normalizedSearchText()
    val primaryConfidence = activeSource?.playerArtistSourceConfidence(artistKey, followedArtists)
        ?: if (artistKey.isBlank()) ArtistSourceConfidence.None else ArtistSourceConfidence.Unverified
    val primaryArtist = artistKey.takeIf { it.isNotBlank() }?.let {
        ArtistCandidate(
            displayName = artistName,
            normalizedName = artistKey,
            role = ArtistRole.Primary,
            evidence = buildSet {
                if (track.artist.isNotBlank()) add(ArtistEvidence.TrackArtistMetadata)
                if (activeSource != null) add(ArtistEvidence.PremiumDeckSourceMetadata)
                if (activeSource?.channelTitle?.isNotBlank() == true || activeSource?.channelUrl?.isNotBlank() == true) {
                    add(ArtistEvidence.SourceChannelMetadata)
                }
                activeSource?.playerArtistEvidence(artistKey, followedArtists)?.let(::addAll)
            },
            sourceChannelId = activeSource?.playerChannelIdentityKey()?.takeIf { it.isNotBlank() },
            sourceChannelTitle = activeSource?.channelTitle?.takeIf { it.isNotBlank() },
            confidence = primaryConfidence,
        )
    }
    val currentTrackKey = track.stableKey()
    val localArtistKeys = listOfNotNull(artistKey.takeIf { it.isNotBlank() }, trackArtist?.normalizedSearchText())
        .distinct()
    val sameArtistTracks = if (localArtistKeys.isEmpty()) {
        emptyList()
    } else {
        libraryTracks
            .asSequence()
            .filter { candidate ->
                val candidateKey = candidate.artist.playerPrimaryArtistName().normalizedSearchText()
                candidateKey in localArtistKeys
            }
            .distinctBy { it.stableKey() }
            .toList()
    }
    val localTracks = sameArtistTracks
        .filter { it.stableKey() != currentTrackKey }
        .take(8)
    val genres = sameArtistTracks
        .mapNotNull { it.genre?.trim()?.takeIf(String::isNotBlank) }
        .distinctBy { it.normalizedSearchText() }
        .take(4)
    val currentSourceKey = activeSource?.streamDistinctKey().orEmpty()
    val currentSourceId = activeSource?.id.orEmpty()
    val playableStreamSources = streamSources
        .asSequence()
        .filter { it.kind == YouTubeSourceKind.Video }
        .filterNot { it.isPodcast }
        .filterNot { source ->
            val sourceKey = source.streamDistinctKey()
            (currentSourceId.isNotBlank() && source.id == currentSourceId) ||
                (currentSourceKey.isNotBlank() && sourceKey == currentSourceKey)
        }
        .distinctBy { it.streamDistinctKey() }
        .toList()
    val savedPremiumDeckSources = if (artistKey.isBlank()) {
        emptyList()
    } else {
        playableStreamSources
            .mapNotNull { source ->
                val confidence = source.playerArtistSourceConfidence(artistKey, followedArtists)
                if (!confidence.playerArtistContinuationAccepted()) return@mapNotNull null
                ArtistContinuationSource(
                    source = source,
                    artist = source.playerArtistCandidate(
                        displayName = artistName,
                        artistKey = artistKey,
                        role = ArtistRole.Primary,
                        confidence = confidence,
                        followedArtists = followedArtists,
                    ),
                    reason = confidence.playerArtistContinuationReason(),
                )
            }
            .sortedWith(
                compareByDescending<ArtistContinuationSource> { it.artist.confidence.playerArtistConfidenceRank() }
                    .thenByDescending { it.source.reaction == YouTubeReaction.Liked }
                    .thenByDescending { it.source.bookmarked }
                    .thenByDescending { it.source.playCount }
                    .thenByDescending { it.source.lastPlayedMillis }
                    .thenByDescending { it.source.addedMillis }
            )
            .take(8)
    }
    val collaborationCandidates = playerFeaturedArtistCandidates(track, activeSource, artistKey)
    val collaborationSources = if (collaborationCandidates.isEmpty()) {
        emptyList()
    } else {
        playableStreamSources
            .mapNotNull { source ->
                val candidate = collaborationCandidates.firstOrNull { featured ->
                    source.playerMatchesFeaturedArtist(primaryArtistKey = artistKey, featuredArtistKey = featured.normalizedName)
                } ?: return@mapNotNull null
                val confidence = source.playerArtistSourceConfidence(candidate.normalizedName, followedArtists)
                    .takeIf { it.playerArtistContinuationAccepted() }
                    ?: ArtistSourceConfidence.Unverified
                ArtistContinuationSource(
                    source = source,
                    artist = candidate.copy(
                        confidence = confidence,
                        evidence = candidate.evidence + source.playerArtistEvidence(candidate.normalizedName, followedArtists),
                        sourceChannelId = source.playerChannelIdentityKey().takeIf { it.isNotBlank() },
                        sourceChannelTitle = source.channelTitle.takeIf { it.isNotBlank() },
                    ),
                    reason = if (confidence.playerArtistContinuationAccepted()) {
                        confidence.playerArtistContinuationReason()
                    } else {
                        "Featured artist"
                    },
                )
            }
            .distinctBy { it.source.streamDistinctKey() }
            .take(6)
    }
    return PlayerArtistContext(
        artist = artistName,
        primaryArtist = primaryArtist,
        savedPremiumDeckSources = savedPremiumDeckSources,
        collaborationSources = collaborationSources,
        localTracks = localTracks,
        totalTrackCount = sameArtistTracks.size,
        albumCount = sameArtistTracks.map { it.album.key }.distinct().size,
        totalDurationMillis = sameArtistTracks.sumOf { it.durationMillis.coerceAtLeast(0L) },
        genres = genres,
    )
}

private fun buildFetchedPlayerArtistContinuationSources(
    artist: ArtistCandidate,
    results: List<YouTubeSearchResult>,
    followedArtists: List<FollowedStreamArtist>,
    activeSource: YouTubeSource?,
): List<ArtistContinuationSource> {
    val artistKey = artist.normalizedName
    if (artistKey.isBlank()) return emptyList()
    val activeSourceKey = activeSource?.streamDistinctKey().orEmpty()
    val activeSourceId = activeSource?.id.orEmpty()
    return results
        .asSequence()
        .mapIndexed { index, result ->
            val source = result.source.copy(
                reviewState = YouTubeReviewState.Accepted,
                thumbnailUrl = result.source.bestThumbnailUrl() ?: result.source.thumbnailUrl,
                durationMillis = result.durationMillis.takeIf { it > 0L } ?: result.source.durationMillis,
            )
            Triple(index, result, source)
        }
        .filter { (_, _, source) -> source.kind == YouTubeSourceKind.Video && !source.isPodcast }
        .filterNot { (_, _, source) ->
            val sourceKey = source.streamDistinctKey()
            (activeSourceId.isNotBlank() && source.id == activeSourceId) ||
                (activeSourceKey.isNotBlank() && sourceKey == activeSourceKey)
        }
        .mapNotNull { (index, result, source) ->
            val confidence = source.playerArtistSourceConfidence(artistKey, followedArtists)
            if (!confidence.playerArtistContinuationAccepted()) return@mapNotNull null
            val candidate = source.playerArtistCandidate(
                displayName = artist.displayName,
                artistKey = artistKey,
                role = ArtistRole.Primary,
                confidence = confidence,
                followedArtists = followedArtists,
            )
            ArtistContinuationSource(
                source = source,
                artist = candidate.copy(
                    evidence = artist.evidence + candidate.evidence + ArtistEvidence.SearchResultMatch,
                ),
                reason = confidence.playerArtistContinuationReason(),
                resultScore = result.score,
                views = result.views,
                rank = index + 1,
            ) to result
        }
        .distinctBy { (item, _) -> item.source.streamDistinctKey() }
        .sortedWith(
            compareByDescending<Pair<ArtistContinuationSource, YouTubeSearchResult>> { (item, _) ->
                item.artist.confidence.playerArtistConfidenceRank()
            }
                .thenByDescending { (_, result) -> result.score }
                .thenByDescending { (_, result) -> result.views }
        )
        .map { (item, _) -> item }
        .take(12)
        .toList()
}

private fun YouTubeSource.playerArtistCandidate(
    displayName: String,
    artistKey: String,
    role: ArtistRole,
    confidence: ArtistSourceConfidence,
    followedArtists: List<FollowedStreamArtist>,
): ArtistCandidate =
    ArtistCandidate(
        displayName = displayName,
        normalizedName = artistKey,
        role = role,
        evidence = playerArtistEvidence(artistKey, followedArtists),
        sourceChannelId = playerChannelIdentityKey().takeIf { it.isNotBlank() },
        sourceChannelTitle = channelTitle.takeIf { it.isNotBlank() },
        confidence = confidence,
    )

private fun YouTubeSource.playerArtistSourceConfidence(
    artistKey: String,
    followedArtists: List<FollowedStreamArtist>,
): ArtistSourceConfidence {
    if (artistKey.isBlank() || kind != YouTubeSourceKind.Video || isPodcast) return ArtistSourceConfidence.None
    val followedOfficial = playerMatchesFollowedOfficialArtist(artistKey, followedArtists)
    val authorMatches = author.playerLooksLikeArtistSource(artistKey)
    val channelMatches = channelTitle.playerLooksLikeArtistSource(artistKey)
    val metadataMatches = playerHighConfidenceMetadataArtist()
        .normalizedSearchText()
        .let { it.isNotBlank() && it == artistKey }
    val topicMatches = playerLooksLikeTopicSource(artistKey)
    return when {
        followedOfficial -> ArtistSourceConfidence.VerifiedOfficial
        channelVerified && (authorMatches || channelMatches) -> ArtistSourceConfidence.VerifiedOfficial
        topicMatches -> ArtistSourceConfidence.TopicOrAutoGenerated
        authorMatches || channelMatches || metadataMatches -> ArtistSourceConfidence.HighConfidence
        playerSearchableText().contains(artistKey) && title.playerHasFeaturedArtistMarker() -> ArtistSourceConfidence.Unverified
        else -> ArtistSourceConfidence.None
    }
}

private fun YouTubeSource.playerArtistEvidence(
    artistKey: String,
    followedArtists: List<FollowedStreamArtist>,
): Set<ArtistEvidence> =
    buildSet {
        add(ArtistEvidence.PremiumDeckSourceMetadata)
        if (channelTitle.isNotBlank() || channelUrl.isNotBlank()) add(ArtistEvidence.SourceChannelMetadata)
        if (playerMatchesFollowedOfficialArtist(artistKey, followedArtists) || channelVerified && (author.playerLooksLikeArtistSource(artistKey) || channelTitle.playerLooksLikeArtistSource(artistKey))) {
            add(ArtistEvidence.OfficialChannelMatch)
        }
        if (playerLooksLikeTopicSource(artistKey)) add(ArtistEvidence.TopicChannelMatch)
        if (playerSearchableText().contains(artistKey) || playerHighConfidenceMetadataArtist().normalizedSearchText() == artistKey) {
            add(ArtistEvidence.SearchResultMatch)
        }
    }

private fun YouTubeSource.playerMatchesFollowedOfficialArtist(
    artistKey: String,
    followedArtists: List<FollowedStreamArtist>,
): Boolean {
    if (artistKey.isBlank()) return false
    val compactArtistKey = artistKey.playerCompactArtistKey()
    val channelKey = playerChannelIdentityKey()
    return followedArtists.any { artist ->
        val artistKeys = listOf(
            artist.key,
            artist.officialKey,
            artist.name.cleanStreamArtistName().normalizedSearchText(),
            artist.officialName.cleanStreamArtistName().normalizedSearchText(),
        )
            .filter { it.isNotBlank() }
            .distinct()
        val sameArtist = artistKeys.any { key -> key == artistKey || key.playerCompactArtistKey() == compactArtistKey }
        if (!sameArtist) return@any false
        val requiredChannelKey = artist.officialChannelKey.ifBlank { artist.officialChannelUrl.youtubeChannelIdentityKey() }
        if (requiredChannelKey.isNotBlank() && channelKey == requiredChannelKey) return@any true
        channelVerified && artistKeys.any { key -> author.playerLooksLikeArtistSource(key) || channelTitle.playerLooksLikeArtistSource(key) }
    }
}

private fun YouTubeSource.playerLooksLikeTopicSource(artistKey: String): Boolean {
    val topicText = listOf(author, channelTitle).joinToString(" ").lowercase(Locale.US)
    return topicText.contains("topic") && (author.playerLooksLikeArtistSource(artistKey) || channelTitle.playerLooksLikeArtistSource(artistKey))
}

private fun YouTubeSource.playerSearchableText(): String =
    listOf(title, author, channelTitle, albumTitleHint)
        .joinToString(" ")
        .cleanYouTubeArtistCandidate()
        .cleanStreamArtistName()
        .normalizedSearchText()

private fun YouTubeSource.playerHighConfidenceMetadataArtist(): String {
    val metadata = smartYouTubeMusicMetadata(title, author)
    return metadata
        .takeIf { it.confidence >= 82 }
        ?.artist
        ?.playerPrimaryArtistName()
        .orEmpty()
}

private fun YouTubeSource.playerChannelIdentityKey(): String =
    channelUrl.youtubeChannelIdentityKey()

private fun ArtistSourceConfidence.playerArtistContinuationAccepted(): Boolean =
    this == ArtistSourceConfidence.VerifiedOfficial ||
        this == ArtistSourceConfidence.HighConfidence ||
        this == ArtistSourceConfidence.TopicOrAutoGenerated

private fun ArtistSourceConfidence.playerArtistConfidenceRank(): Int =
    when (this) {
        ArtistSourceConfidence.VerifiedOfficial -> 4
        ArtistSourceConfidence.HighConfidence -> 3
        ArtistSourceConfidence.TopicOrAutoGenerated -> 2
        ArtistSourceConfidence.Unverified -> 1
        ArtistSourceConfidence.None -> 0
    }

private fun ArtistSourceConfidence.playerArtistContinuationReason(): String =
    when (this) {
        ArtistSourceConfidence.VerifiedOfficial -> "Verified official source"
        ArtistSourceConfidence.HighConfidence -> "High-confidence artist source"
        ArtistSourceConfidence.TopicOrAutoGenerated -> "Topic channel source"
        ArtistSourceConfidence.Unverified -> "Artist mention"
        ArtistSourceConfidence.None -> "Artist source"
    }

private fun String.playerLooksLikeArtistSource(artistKey: String): Boolean {
    val cleanedKey = playerPrimaryArtistName().normalizedSearchText()
    if (cleanedKey.isBlank() || artistKey.isBlank()) return false
    if (cleanedKey == artistKey) return true
    val compact = cleanedKey.playerCompactArtistKey()
    val targetCompact = artistKey.playerCompactArtistKey()
    if (compact.isBlank() || targetCompact.isBlank()) return false
    val raw = lowercase(Locale.US)
    return compact == targetCompact ||
        (compact.contains(targetCompact) && (raw.contains("vevo") || raw.contains("topic") || raw.contains("official")))
}

private fun String.playerCompactArtistKey(): String =
    playerPrimaryArtistName()
        .normalizedSearchText()
        .replace(" ", "")

private data class PlayerParsedArtistName(
    val name: String,
    val role: ArtistRole,
    val evidence: ArtistEvidence,
)

private fun playerFeaturedArtistCandidates(
    track: Track,
    activeSource: YouTubeSource?,
    primaryArtistKey: String,
): List<ArtistCandidate> =
    (
        listOfNotNull(track.title, activeSource?.title)
            .flatMap(::playerFeaturedArtistNames)
            .map { PlayerParsedArtistName(it, ArtistRole.Featured, ArtistEvidence.TrackTitleFeaturingText) } +
            listOfNotNull(track.artist, activeSource?.author)
                .flatMap { rawArtist -> rawArtist.playerArtistNameParts().drop(1) }
                .map { PlayerParsedArtistName(it, ArtistRole.Collaborator, ArtistEvidence.TrackArtistMetadata) }
        )
        .mapNotNull { parsed ->
            val cleanName = parsed.name.playerPrimaryArtistName()
            val key = cleanName.normalizedSearchText()
            if (key.isBlank() || key == primaryArtistKey) return@mapNotNull null
            ArtistCandidate(
                displayName = cleanName,
                normalizedName = key,
                role = parsed.role,
                evidence = setOf(parsed.evidence),
                sourceChannelId = null,
                sourceChannelTitle = null,
                confidence = ArtistSourceConfidence.Unverified,
            )
        }
        .distinctBy { it.normalizedName }
        .take(4)

private fun playerFeaturedArtistNames(rawText: String): List<String> {
    if (rawText.isBlank()) return emptyList()
    val matches = Regex("""\b(?:feat\.?|ft\.?|featuring|with)\s+([^()\[\]\-–—|]+)""", RegexOption.IGNORE_CASE)
        .findAll(rawText)
        .map { it.groupValues.getOrNull(1).orEmpty() }
        .toList()
    return matches
        .flatMap { value -> Regex("""\s*(?:,|&|\band\b|\bx\b)\s*""", RegexOption.IGNORE_CASE).split(value) }
        .map { it.playerPrimaryArtistName() }
        .filter { it.length >= 2 }
}

private fun String.playerPrimaryArtistName(): String {
    return playerArtistNameParts().firstOrNull().orEmpty()
}

private fun String.playerArtistNameParts(): List<String> {
    val cleaned = playerArtistNameBase()
    if (cleaned.isBlank()) return emptyList()
    val commaParts = cleaned
        .split(Regex("""\s*,\s*"""))
        .map { it.playerArtistNameBase() }
        .filter { it.length >= 2 }
    return commaParts.ifEmpty { listOf(cleaned) }
}

private fun String.playerArtistNameBase(): String {
    val cleaned = cleanYouTubeArtistCandidate().cleanStreamArtistName()
    if (cleaned.isBlank()) return ""
    return cleaned
        .replace(Regex("""\s*[\(\[]\s*(?:feat\.?|ft\.?|featuring)\b[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+(?:feat\.?|ft\.?|featuring)\b.+$""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+[-\u2013\u2014]\s*(?:feat\.?|ft\.?|featuring)\b.+$""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '\u2013', '\u2014', ',', '&')
}

private fun YouTubeSource.playerMatchesFeaturedArtist(primaryArtistKey: String, featuredArtistKey: String): Boolean {
    if (featuredArtistKey.isBlank()) return false
    val text = playerSearchableText()
    if (!text.contains(featuredArtistKey)) return false
    return primaryArtistKey.isBlank() ||
        text.contains(primaryArtistKey) ||
        title.playerHasFeaturedArtistMarker() ||
        author.playerLooksLikeArtistSource(featuredArtistKey) ||
        channelTitle.playerLooksLikeArtistSource(featuredArtistKey)
}

private fun String.playerHasFeaturedArtistMarker(): Boolean =
    Regex("""\b(feat\.?|ft\.?|featuring|with)\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)

private fun YouTubeSource.canUsePremiumDeckSmoothTransition(crossfade: CrossfadeSettings): Boolean {
    val transitionsEnabled = crossfade.gaplessEnabled || crossfade.crossfadeEnabled
    if (!transitionsEnabled || isPodcast || durationMillis <= 0L) return false
    if (crossfade.disableForShortTracks && durationMillis in 1L until 45_000L) return false
    return true
}

private fun Media3Player.hasSameMediaQueue(expected: List<MediaItem>): Boolean {
    if (mediaItemCount != expected.size) return false
    return expected.indices.all { index -> getMediaItemAt(index).mediaId == expected[index].mediaId }
}

private fun media3RepeatMode(mode: PlaybackRepeatMode): Int =
    when (mode) {
        PlaybackRepeatMode.RepeatSong -> Media3Player.REPEAT_MODE_ONE
        PlaybackRepeatMode.RepeatCategory,
        PlaybackRepeatMode.RepeatAll -> Media3Player.REPEAT_MODE_ALL
        PlaybackRepeatMode.SongOnce,
        PlaybackRepeatMode.CategoryOnce,
        PlaybackRepeatMode.AllOnce -> Media3Player.REPEAT_MODE_OFF
    }

private enum class MotionPattern { None, Panel }

private fun motionPatternFor(screen: Screen, _selectedAlbumKey: String?): MotionPattern =
    if (screen == Screen.AlbumDetail) MotionPattern.None else MotionPattern.Panel

@Composable
private fun MotionRouteHost(
    routeKey: Any?,
    targetRoute: Screen,
    pattern: MotionPattern,
    onMotionActiveChange: (Boolean) -> Unit = {},
    content: @Composable (Screen) -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    val managesOwnMotion = pattern == MotionPattern.None
    var lastKey by remember { mutableStateOf<Any?>(routeKey) }
    var currentRoute by remember { mutableStateOf(targetRoute) }
    var outgoingRoute by remember { mutableStateOf<Screen?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(routeKey, targetRoute, pattern) {
        if (routeKey != lastKey || targetRoute != currentRoute) {
            if (!managesOwnMotion) {
                onMotionActiveChange(true)
                outgoingRoute = currentRoute
                currentRoute = targetRoute
                lastKey = routeKey
                progress.snapTo(0f)
                try {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = motion.duration(PulseMotion.Duration.Medium),
                            easing = PulseMotion.Easing.Standard,
                        ),
                    )
                    outgoingRoute = null
                } finally {
                    onMotionActiveChange(false)
                }
            } else {
                onMotionActiveChange(false)
                outgoingRoute = null
                currentRoute = targetRoute
                lastKey = routeKey
                progress.snapTo(1f)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { onMotionActiveChange(false) }
    }

    val t = if (managesOwnMotion) 1f else progress.value
    val incomingAlpha = when (pattern) {
        MotionPattern.Panel -> ((t - 0.12f) / 0.88f).coerceIn(0f, 1f)
        MotionPattern.None -> 1f
    }

    Box(Modifier.fillMaxSize()) {
        if (!managesOwnMotion) outgoingRoute?.let { route ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (pattern) {
                            MotionPattern.Panel -> {
                                alpha = (1f - (t / 0.36f)).coerceIn(0f, 1f)
                                translationY = -PulseMotion.Distance.PanelLift * t
                            }
                            MotionPattern.None -> {
                                alpha = 0f
                            }
                        }
                    },
            ) {
                content(route)
            }
        }
        val incomingModifier = if (pattern == MotionPattern.Panel && (outgoingRoute != null || t < 0.999f)) {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = incomingAlpha
                    translationY = PulseMotion.Distance.PanelLift * (1f - t)
                }
        } else {
            Modifier.fillMaxSize()
        }
        Box(incomingModifier) {
            content(if (managesOwnMotion) targetRoute else currentRoute)
        }
    }
}

@Composable
private fun MotionSceneHost(
    routeKey: Any?,
    pattern: MotionPattern,
    onMotionActiveChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val motion = LocalPulseMotionSpec.current
    val managesOwnMotion = pattern == MotionPattern.None
    var entered by remember(routeKey) { mutableStateOf(managesOwnMotion) }
    LaunchedEffect(routeKey, managesOwnMotion) {
        if (!managesOwnMotion) onMotionActiveChange(true)
        entered = true
    }
    val t by animateFloatAsState(
        targetValue = if (managesOwnMotion || entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.duration(PulseMotion.Duration.Medium),
            easing = PulseMotion.Easing.Standard,
        ),
        label = "motionScene",
        finishedListener = { if (it >= 0.999f) onMotionActiveChange(false) },
    )
    DisposableEffect(routeKey) {
        onDispose { onMotionActiveChange(false) }
    }
    val sceneModifier = if (pattern == MotionPattern.Panel && t < 0.999f) {
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 0.72f + 0.28f * t
                translationY = PulseMotion.Distance.PanelLift * (1f - t)
            }
    } else {
        Modifier.fillMaxSize()
    }
    Box(sceneModifier) {
        content()
    }
}
@Composable
internal fun AlphabetRail(
    labels: List<String>,
    active: Boolean,
    onActive: (Boolean) -> Unit,
    onLabel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalPulseMotionSpec.current
    val haptic = LocalHapticFeedback.current
    val railWidth by animateFloatAsState(if (active) 38f else 15f, tween(motion.duration(PulseMotion.Duration.Short), easing = PulseMotion.Easing.Standard), label = "artistAlphabetRailWidth")
    val fontSize by animateFloatAsState(if (active) 11.2f else 7.2f, tween(motion.duration(PulseMotion.Duration.Short), easing = PulseMotion.Easing.Standard), label = "artistAlphabetFontSize")
    val railAlpha by animateFloatAsState(if (active) 0.62f else 0.30f, tween(motion.duration(PulseMotion.Duration.Short), easing = PulseMotion.Easing.Standard), label = "artistAlphabetRailAlpha")
    val itemHeight = 14.5f
    val itemSpacing = 2.3f
    val verticalPadding = 6f
    val touchHeight = labels.size * itemHeight + (labels.size - 1) * itemSpacing + verticalPadding * 2f
    Box(
        modifier
            .width(56.dp)
            .height(touchHeight.dp)
            .pointerInput(labels) {
                fun labelForOffset(y: Float): String {
                    val itemHeightPx = itemHeight.dp.toPx()
                    val spacingPx = itemSpacing.dp.toPx()
                    val topPaddingPx = verticalPadding.dp.toPx()
                    val index = ((y - topPaddingPx) / (itemHeightPx + spacingPx)).toInt().coerceIn(0, labels.lastIndex)
                    return labels[index]
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onActive(true)
                    var lastLabel: String? = null
                    fun updateAt(y: Float) {
                        val label = labelForOffset(y)
                        if (label != lastLabel) {
                            lastLabel = label
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLabel(label)
                        }
                    }
                    updateAt(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (change != null && change.pressed) updateAt(change.position.y)
                    } while (event.changes.any { it.pressed })
                    onActive(false)
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .padding(end = 1.dp)
                .width(railWidth.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(railAlpha * 0.30f), Color.Black.copy(railAlpha), Color.Black.copy(railAlpha * 0.86f))))
                .border(1.dp, Color.White.copy(if (active) 0.24f else 0.10f), RoundedCornerShape(14.dp))
                .padding(vertical = verticalPadding.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemSpacing.dp),
        ) {
            labels.forEach { label ->
                Box(Modifier.fillMaxWidth().height(itemHeight.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = Color.White.copy(if (active) 0.92f else 0.50f), fontSize = fontSize.sp, lineHeight = (fontSize + 1f).sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}


internal fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun requiredAudioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredAudioPermission()) == PackageManager.PERMISSION_GRANTED

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

internal fun String.normalizedSearchText(): String =
    lowercase(Locale.US)
        .replace(Regex("""[^\p{L}\p{Nd}]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

internal fun String.personalizationAffinityKey(): String =
    lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()

private fun YouTubeResolvedAudio.toTrack(source: YouTubeSource): Track {
    val inferred = smartYouTubeMusicMetadata(title, artist.ifBlank { source.author })
    val displayTitle = inferred.title.ifBlank { title }
    val displayArtist = inferred.artistOrSafeFallback(artist.ifBlank { source.author })
    val albumTitle = source.strictStreamAlbumTitle()
    val album = Album(
        title = albumTitle ?: STREAM_LIBRARY_ALBUM,
        artist = albumTitle?.let { displayArtist } ?: STREAM_LIBRARY_ALBUM_ARTIST,
        mark = PREMIUMDECK_SOURCE_MARK,
        tint = Blue,
        alt = Color(0xFFFFD166),
        coverUri = thumbnailUrl?.let { runCatching { Uri.parse(it) }.getOrNull() },
        sourceUri = runCatching { Uri.parse(source.url) }.getOrNull(),
    )
    return Track(
        title = displayTitle,
        artist = displayArtist,
        duration = if (durationMillis > 0L) formatDuration(durationMillis) else "0:00",
        album = album,
        uri = Uri.parse(streamUrl),
        durationMillis = durationMillis,
        quality = "${qualityLabel.uppercase(Locale.US)}  PREMIUMDECK STREAM",
        mimeType = mimeType,
        displayName = source.url,
        sizeBytes = 0L,
        folderPath = STREAM_LIBRARY_FOLDER,
        modifiedMillis = source.addedMillis,
        genre = if (isPodcast) "Podcast" else "Online Stream",
    )
}

private fun YouTubeSource.toDownloadedTrack(uri: Uri): Track {
    val inferred = smartYouTubeMusicMetadata(title, author)
    val displayTitle = inferred.title.ifBlank { title }
    val displayArtist = inferred.artistOrSafeFallback(author)
    val albumTitle = strictStreamAlbumTitle()
    val album = Album(
        title = albumTitle ?: STREAM_LIBRARY_ALBUM,
        artist = albumTitle?.let { displayArtist } ?: STREAM_LIBRARY_ALBUM_ARTIST,
        mark = PREMIUMDECK_SOURCE_MARK,
        tint = Blue,
        alt = Color(0xFFFFD166),
        coverUri = thumbnailUrl?.let { runCatching { Uri.parse(it) }.getOrNull() },
        sourceUri = runCatching { Uri.parse(url) }.getOrNull(),
    )
    return Track(
        title = displayTitle,
        artist = displayArtist,
        duration = if (durationMillis > 0L) formatDuration(durationMillis) else "0:00",
        album = album,
        uri = uri,
        durationMillis = durationMillis,
        quality = "${quality.label.uppercase(Locale.US)}  SAVED PREMIUMDECK",
        mimeType = null,
        displayName = url,
        folderPath = STREAM_LIBRARY_FOLDER_WITH_MUSIC,
        modifiedMillis = addedMillis,
        genre = if (isPodcast) "Podcast" else "Online Stream",
    )
}

private fun YouTubeSource.toRestoredTrack(policy: StreamingDataPolicy? = null): Track? {
    val downloaded = downloadedUri
        ?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
        ?.let { uri -> toDownloadedTrack(uri) }
    if (downloaded != null) return downloaded
    val cached = freshCachedStreamUrl(policy = policy) ?: return null
    return toCachedResolvedAudio(cached).toTrack(this)
}

private fun YouTubeSource.toQueuePreviewTrack(): Track {
    val inferred = smartYouTubeMusicMetadata(title, author)
    val displayTitle = inferred.title.ifBlank { title.ifBlank { PREMIUMDECK_STREAM_TITLE } }
    val displayArtist = inferred.artistOrSafeFallback(author.ifBlank { PREMIUMDECK_SOURCE_NAME })
    val albumTitle = strictStreamAlbumTitle()
    val album = Album(
        title = albumTitle ?: STREAM_LIBRARY_ALBUM,
        artist = albumTitle?.let { displayArtist } ?: STREAM_LIBRARY_ALBUM_ARTIST,
        mark = PREMIUMDECK_SOURCE_MARK,
        tint = Blue,
        alt = Color(0xFFFFD166),
        coverUri = bestThumbnailUrl()?.let { runCatching { Uri.parse(it) }.getOrNull() },
        sourceUri = runCatching { Uri.parse(url) }.getOrNull(),
    )
    val queueKey = Uri.encode(id.ifBlank { url.ifBlank { displayTitle } })
    return Track(
        title = displayTitle,
        artist = displayArtist,
        duration = if (durationMillis > 0L) formatDuration(durationMillis) else "0:00",
        album = album,
        uri = Uri.parse("pulsedeck://premiumdeck-queue/$queueKey"),
        durationMillis = durationMillis,
        quality = "${quality.label.uppercase(Locale.US)}  QUEUED PREMIUMDECK",
        mimeType = null,
        displayName = url,
        folderPath = STREAM_LIBRARY_FOLDER,
        modifiedMillis = addedMillis,
        genre = if (isPodcast) "Podcast" else "Online Stream",
    )
}

internal fun smartYouTubeMusicMetadata(rawTitle: String, rawAuthor: String): SmartYouTubeMusicMetadata {
    val title = cleanYouTubeDisplayTitle(rawTitle).ifBlank { rawTitle.trim() }
    val author = rawAuthor.cleanYouTubeArtistCandidate()
    val splitParts = Regex("""\s+[-\u2013\u2014]\s+""")
        .split(title)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (splitParts.size >= 2) {
        val offset = if (splitParts.size >= 3 && splitParts.first().isGenericYouTubeMusicChannelName()) 1 else 0
        val artist = splitParts.getOrNull(offset).orEmpty().cleanYouTubeArtistCandidate()
        val song = cleanYouTubeDisplayTitle(splitParts.drop(offset + 1).joinToString(" - "))
        if (artist.isReliableMusicArtistName() && song.isReliableMusicTitle()) {
            return SmartYouTubeMusicMetadata(artist = artist, title = song, confidence = 94, reason = "artist-title title split")
        }
    }
    val colonMatch = Regex("""^(.{2,60})\s*:\s*(.{2,140})$""").find(title)
    if (colonMatch != null) {
        val artist = colonMatch.groupValues[1].cleanYouTubeArtistCandidate()
        val song = cleanYouTubeDisplayTitle(colonMatch.groupValues[2])
        if (artist.isReliableMusicArtistName() && song.isReliableMusicTitle() && !artist.isGenericYouTubeMusicChannelName()) {
            return SmartYouTubeMusicMetadata(artist = artist, title = song, confidence = 82, reason = "artist-title colon split")
        }
    }
    if (rawAuthor.looksLikeOfficialArtistChannel() && author.isReliableMusicArtistName()) {
        return SmartYouTubeMusicMetadata(artist = author, title = title, confidence = 78, reason = "official artist channel")
    }
    return SmartYouTubeMusicMetadata(
        artist = "",
        title = title.ifBlank { rawTitle.trim().ifBlank { PREMIUMDECK_STREAM_TITLE } },
        confidence = 36,
        reason = "title only; channel not trusted as artist",
    )
}

private fun parseArtistTitle(rawTitle: String, fallbackArtist: String): Pair<String, String> {
    val separators = listOf(" - ", " â€“ ", " â€” ")
    separators.forEach { separator ->
        val parts = rawTitle.split(separator, limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return parts[0].trim() to parts[1].trim()
        }
    }
    return fallbackArtist.ifBlank { PREMIUMDECK_SOURCE_NAME } to rawTitle.ifBlank { PREMIUMDECK_STREAM_TITLE }
}

internal fun newYouTubePlaylistId(): String = "yt-playlist-${System.currentTimeMillis()}-${(1000..9999).random()}"

internal fun cleanYouTubeDisplayTitle(rawTitle: String): String =
    rawTitle
        .replace(Regex("""\[[^\]]*(official|video|lyrics?|audio|visualizer|remaster(?:ed)?|hd|4k|sped up|slowed|nightcore)[^\]]*]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\([^)]*(official|video|lyrics?|audio|visualizer|remaster(?:ed)?|hd|4k|sped up|slowed|nightcore)[^)]*\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(official music video|official video|official audio|lyric video|lyrics? video|visualizer|audio only|full audio|hd|4k)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""#[\w-]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

internal fun String.cleanYouTubeArtistCandidate(): String =
    trim()
        .removePrefix("@")
        .replace(Regex("""\s*[-\u2013\u2014]\s*topic$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+official artist channel$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+official$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""vevo$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun String.looksLikeOfficialArtistChannel(): Boolean {
    val raw = trim().lowercase(Locale.US)
    return raw.contains("- topic") ||
        raw.endsWith("vevo") ||
        raw.endsWith(" official") ||
        raw.endsWith("official artist channel")
}

private fun String.isReliableMusicTitle(): Boolean {
    val normalized = cleanLyricsSearchText().normalizedSearchText()
    return normalized.length >= 2 &&
        normalized !in setOf("official", "audio", "video", "lyrics", "music", "song", "track")
}

private fun String.isReliableMusicArtistName(): Boolean {
    val normalized = normalizedSearchText()
    if (normalized.length < 2) return false
    if (normalized in setOf("youtube", "premiumdeck", "unknown", "unknown artist", "various", "various artists")) return false
    return !isGenericYouTubeMusicChannelName()
}

private fun String.isGenericYouTubeMusicChannelName(): Boolean {
    val normalized = normalizedSearchText()
    if (normalized in setOf("7clouds", "trap nation", "cloudkid", "ncs", "nocopyrightsounds", "vevo", "lyrics", "lyric video", "audio library")) return true
    return listOf("lyrics", "lyric", "music channel", "records", "recordings", "uploads", "playlist", "promotions").any { normalized.contains(it) }
}

internal fun SmartYouTubeMusicMetadata.artistOrSafeFallback(rawAuthor: String): String =
    artist.ifBlank {
        rawAuthor.cleanYouTubeArtistCandidate()
            .takeIf { it.isReliableMusicArtistName() }
            ?: "Unknown Artist"
    }

internal fun YouTubeSource.cleanedSuggestion(): Pair<String, String> {
    val inferred = smartYouTubeMusicMetadata(title, author)
    return inferred.artist.ifBlank { author.cleanYouTubeArtistCandidate().ifBlank { author } } to inferred.title
}

internal fun YouTubeSource.needsYouTubeMetadataReview(): Boolean {
    val genericTitle = title.startsWith("YouTube video", ignoreCase = true) ||
        title.startsWith("PremiumDeck video", ignoreCase = true) ||
        title.startsWith("YouTube playlist", ignoreCase = true) ||
        title.startsWith("PremiumDeck playlist", ignoreCase = true) ||
        title.equals("Untitled YouTube source", ignoreCase = true) ||
        title.equals("Untitled PremiumDeck source", ignoreCase = true)
    val genericAuthor = author.equals(LEGACY_YOUTUBE_SOURCE_NAME, ignoreCase = true) ||
        author.equals(PREMIUMDECK_SOURCE_NAME, ignoreCase = true) ||
        author.isBlank()
    return reviewState == YouTubeReviewState.Inbox || genericTitle || genericAuthor
}

private fun youtubeSourceInfo(source: YouTubeSource): InfoDialogState =
    InfoDialogState(
        title = "PremiumDeck Source",
        subtitle = source.title,
        rows = listOf(
            "Type" to source.kind.label,
            "Review" to source.reviewState.name,
            "Author" to source.author,
            "Duration" to if (source.durationMillis > 0L) formatDuration(source.durationMillis) else "Unknown",
            "Reaction" to source.reaction.name,
            "Bookmarked" to if (source.bookmarked) "Yes" else "No",
            "Quality" to source.quality.label,
            "Status" to source.status.description,
            "Download" to "${source.downloadState.label} ${if (source.downloadProgress > 0) "${source.downloadProgress}%" else ""}".trim(),
            "Plays" to source.playCount.toString(),
            "Chapters" to source.chapters.size.toString(),
            "Skipped segments" to source.sponsorSegments.size.toString(),
            "Skip segments" to if (source.skipSegmentsEnabled) "On" else "Off",
            "Silence trimming" to if (source.trimSilenceOnDownload) "On for download" else "Off",
            "Downloaded file" to (source.downloadedUri ?: "Not saved"),
            "Shelf" to PREMIUMDECK_SOURCE_SHELF,
            "Source URL" to source.url,
        ),
    )

internal fun YouTubeSource.youtubeEmbedUrl(): String {
    val detection = detectYouTubeSource(url)
    return when (detection?.kind) {
        YouTubeSourceKind.Video -> "https://www.youtube.com/embed/${detection.sourceId}?autoplay=1&playsinline=1&modestbranding=1&rel=0"
        YouTubeSourceKind.Playlist -> "https://www.youtube.com/embed/videoseries?list=${detection.sourceId}&autoplay=1&playsinline=1&modestbranding=1&rel=0"
        YouTubeSourceKind.Channel, YouTubeSourceKind.Unknown, null -> url
    }
}

private fun downloadRulesInfo(): InfoDialogState =
    InfoDialogState(
        title = "Download Rules",
        subtitle = "Smart cache foundation",
        rows = listOf(
            "Music quality" to "256 kbps target",
            "Podcast quality" to "128 kbps target",
            "Smart cache" to "Prompt after 3 plays",
            "Library behavior" to "Save album downloads with matched album metadata",
            "In-app playback" to "Media3 audio stream first, embedded fallback",
            "SponsorBlock" to "Music-safe categories",
            "Silence trimming" to "Off unless enabled; needs external resolver",
        ),
    )

private fun Track.toPersonalizationCandidate(
    liked: Boolean,
    disliked: Boolean,
    bookmarked: Boolean,
    inPlaylist: Boolean,
    playCount: Int,
): TrackCandidate =
    TrackCandidate(
        id = stableKey(),
        title = title,
        artist = artist,
        album = album.title,
        genre = genre.orEmpty(),
        source = CandidateSource.LocalLibrary,
        durationMillis = durationMillis,
        qualityScore = localTrackQualityScore(quality),
        playCount = playCount,
        liked = liked,
        disliked = disliked,
        bookmarked = bookmarked || inPlaylist,
        localAvailable = uri != null,
        codebookIds = compositionalCandidateCodes(stableKey()),
        playbackUri = uri?.toString(),
    )

private fun Track.toBehaviorEvent(
    type: BehaviorEventType,
    listenDurationMillis: Long = 0L,
    skipPositionSeconds: Int = 0,
    query: String = "",
    metadata: Map<String, String> = emptyMap(),
): BehaviorEvent =
    BehaviorEvent(
        type = type,
        itemId = stableKey(),
        title = title,
        artist = artist,
        album = album.title,
        genre = genre.orEmpty(),
        source = CandidateSource.LocalLibrary,
        listenDurationMillis = listenDurationMillis,
        skipPositionSeconds = skipPositionSeconds,
        query = query,
        metadata = metadata,
    )

private fun YouTubeSource.toPersonalizationCandidate(): TrackCandidate =
    TrackCandidate(
        id = streamDistinctKey(),
        title = title,
        artist = author,
        album = strictStreamAlbumTitle() ?: PREMIUMDECK_SOURCE_CATEGORY,
        genre = if (isPodcast) "Podcast" else "Online Stream",
        source = CandidateSource.PremiumDeck,
        durationMillis = durationMillis,
        qualityScore = if (quality == YouTubeQuality.High) 0.92f else 0.62f,
        addedAtMillis = addedMillis,
        lastPlayedAtMillis = lastPlayedMillis,
        playCount = playCount,
        liked = reaction == YouTubeReaction.Liked,
        disliked = reaction == YouTubeReaction.Disliked,
        bookmarked = bookmarked,
        localAvailable = downloadedUri != null || downloadState == YouTubeDownloadState.Downloaded || status == YouTubeSourceStatus.Downloaded,
        codebookIds = compositionalCandidateCodes(streamDistinctKey()),
        playbackUri = downloadedUri,
        externalUrl = url,
    )

private fun YouTubeSource.toBehaviorEvent(
    type: BehaviorEventType,
    listenDurationMillis: Long = 0L,
    skipPositionSeconds: Int = 0,
    query: String = "",
    metadata: Map<String, String> = emptyMap(),
): BehaviorEvent =
    BehaviorEvent(
        type = type,
        itemId = streamDistinctKey(),
        title = title,
        artist = author,
        album = strictStreamAlbumTitle() ?: PREMIUMDECK_SOURCE_CATEGORY,
        genre = if (isPodcast) "Podcast" else "Online Stream",
        source = CandidateSource.PremiumDeck,
        listenDurationMillis = listenDurationMillis,
        skipPositionSeconds = skipPositionSeconds,
        query = query,
        metadata = metadata,
    )

private fun TrackCandidate.toPersonalizationStreamSource(): YouTubeSource =
    YouTubeSource(
        id = "tinyrec-${Math.floorMod(id.hashCode(), Int.MAX_VALUE)}",
        url = if (source == CandidateSource.PremiumDeck) externalUrl.ifBlank { id } else "local:${Math.floorMod(id.hashCode(), Int.MAX_VALUE)}",
        kind = YouTubeSourceKind.Video,
        title = title,
        author = artist.ifBlank { PREMIUMDECK_SOURCE_NAME },
        albumTitleHint = album.takeIf { it.isReliableStrictAlbumTitle() }.orEmpty(),
        durationMillis = durationMillis,
        quality = if (qualityScore >= 0.75f) YouTubeQuality.High else YouTubeQuality.Standard,
        playCount = playCount,
        lastPlayedMillis = lastPlayedAtMillis,
        reaction = if (liked) YouTubeReaction.Liked else YouTubeReaction.Neutral,
        bookmarked = bookmarked,
        status = if (playbackUri != null || localAvailable) YouTubeSourceStatus.Downloaded else YouTubeSourceStatus.StreamReady,
        downloadedUri = playbackUri,
        downloadState = if (playbackUri != null || localAvailable) YouTubeDownloadState.Downloaded else YouTubeDownloadState.None,
        reviewState = YouTubeReviewState.Accepted,
    )

private fun GeneratedMix.toStreamCollection(): StreamCollection =
    StreamCollection(
        id = id,
        title = title,
        subtitle = subtitle,
        kind = StreamCollectionKind.Mix,
        sources = tracks.map { it.toPersonalizationStreamSource() },
        accentColor = accentForKey(id),
        canSave = true,
    )

private fun localTrackQualityScore(quality: String): Float {
    val normalized = quality.uppercase(Locale.US)
    return when {
        "FLAC" in normalized || "WAV" in normalized || "HI-RES" in normalized -> 0.96f
        "320" in normalized || "256" in normalized -> 0.82f
        "AAC" in normalized || "OPUS" in normalized -> 0.72f
        else -> 0.56f
    }
}

private fun compositionalCandidateCodes(key: String): List<Int> =
    List(4) { index -> Math.floorMod("${key}:${index}".hashCode(), 256) }

private fun Track.toLastPlaybackState(positionMillis: Long): LastPlaybackState =
    LastPlaybackState(
        kind = "local",
        trackKey = stableKey(),
        positionMillis = positionMillis.coerceAtLeast(0L),
        title = title,
        artist = artist,
        albumKey = album.key,
        displayName = displayName.orEmpty(),
        durationMillis = durationMillis,
        sizeBytes = sizeBytes,
        modifiedMillis = modifiedMillis,
    )

private fun RadioStation.toTrack(): Track {
    val tint = Color(accentForKey(stationUuid.ifBlank { streamUrl }))
    val countryLabel = listOf(countryCode, country).filter { it.isNotBlank() }.distinct().joinToString(" - ").ifBlank { "Internet radio" }
    val artworkUri = favicon.takeIf { it.startsWith("http", ignoreCase = true) }?.let { runCatching { Uri.parse(it) }.getOrNull() }
    return Track(
        title = name,
        artist = countryLabel,
        duration = "LIVE",
        album = Album(
            title = "PulseRadio",
            artist = countryLabel,
            mark = "PR",
            tint = tint,
            alt = Color(0xFF5DD7FF),
            coverUri = artworkUri,
            groupKey = "pulsed-radio-${countryCode.ifBlank { countryLabel }.normalizedSearchText()}",
        ),
        uri = Uri.parse(streamUrl),
        durationMillis = 0L,
        quality = radioQualityLabel(codec, bitrate),
        mimeType = radioMimeType(codec),
        displayName = streamUrl,
        genre = tags,
        albumArtist = "PulseRadio",
    )
}

internal fun RadioStation.favoriteKey(): String =
    discoveryKey()

private fun Track.toRadioLastPlaybackState(positionMillis: Long = 0L): LastPlaybackState =
    LastPlaybackState(
        kind = "radio",
        trackKey = uri?.toString().orEmpty(),
        positionMillis = positionMillis.coerceAtLeast(0L),
        title = title,
        artist = artist,
        albumKey = album.key,
        displayName = displayName.orEmpty(),
        durationMillis = durationMillis,
    )

private fun LastPlaybackState.toRadioTrack(): Track? {
    val streamUrl = trackKey.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        ?: displayName.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        ?: return null
    val tint = Color(accentForKey(streamUrl))
    return Track(
        title = title.ifBlank { "PulseRadio station" },
        artist = artist.ifBlank { "Internet radio" },
        duration = "LIVE",
        album = Album(
            title = "PulseRadio",
            artist = artist.ifBlank { "Internet radio" },
            mark = "PR",
            tint = tint,
            alt = Color(0xFF5DD7FF),
            groupKey = albumKey.ifBlank { "pulsed-radio-restored" },
        ),
        uri = Uri.parse(streamUrl),
        durationMillis = 0L,
        quality = "LIVE RADIO",
        displayName = streamUrl,
        albumArtist = "PulseRadio",
    )
}

internal fun radioQualityLabel(codec: String, bitrate: Int): String =
    buildList {
        if (codec.isNotBlank()) add(codec.uppercase(Locale.US))
        if (bitrate > 0) add("$bitrate KBPS")
    }.joinToString("  ").ifBlank { "LIVE RADIO" }

internal fun radioStationMeta(station: RadioStation): String =
    buildList {
        add(station.countryCode.ifBlank { station.country })
        station.language.takeIf { it.isNotBlank() }?.let(::add)
        if (station.votes > 0) add("${station.votes} votes")
    }.filter { it.isNotBlank() }.joinToString("  |  ").ifBlank { "Internet radio" }

internal fun radioTagsPreview(tags: String): String =
    tags.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(4)
        .joinToString(", ")

private fun radioStationSearchLimit(countryCode: String): Int =
    if (countryCode.trim().equals("ET", ignoreCase = true)) {
        RADIO_ETHIOPIA_STATION_LIMIT
    } else {
        RADIO_DEFAULT_STATION_LIMIT
    }

private fun radioMimeType(codec: String): String? =
    when (codec.trim().uppercase(Locale.US)) {
        "MP3" -> "audio/mpeg"
        "AAC", "AAC+" -> "audio/aac"
        "OGG" -> "audio/ogg"
        "OPUS" -> "audio/opus"
        else -> null
    }

private fun LastPlaybackState.looksLikeLegacyFirstTrackFallback(tracks: List<Track>): Boolean {
    val firstTrack = tracks.firstOrNull() ?: return false
    return kind != "youtube" &&
        title.isBlank() &&
        artist.isBlank() &&
        displayName.isBlank() &&
        positionMillis == 0L &&
        trackKey.isNotBlank() &&
        trackKey == firstTrack.stableKey()
}

private fun LastPlaybackState.shouldRepairToRecentYouTube(tracks: List<Track>, sources: List<YouTubeSource>): Boolean {
    if (looksLikeLegacyFirstTrackFallback(tracks)) return true
    if (kind == "youtube" || positionMillis > 2_500L) return false
    val latestStreamPlayMillis = sources.maxOfOrNull { it.lastPlayedMillis } ?: 0L
    if (latestStreamPlayMillis <= 0L) return false
    return savedMillis <= 0L || savedMillis >= latestStreamPlayMillis && savedMillis - latestStreamPlayMillis <= 2L * 60L * 60L * 1000L
}

private fun findLastPlaybackTrackIndex(tracks: List<Track>, state: LastPlaybackState): Int {
    val exact = tracks.indexOfFirst { it.stableKey() == state.trackKey }
    if (exact >= 0) return exact

    if (state.displayName.isNotBlank() && state.sizeBytes > 0L) {
        val fileMatch = tracks.indexOfFirst { track ->
            track.displayName.equals(state.displayName, ignoreCase = true) &&
                track.sizeBytes > 0L &&
                track.sizeBytes == state.sizeBytes
        }
        if (fileMatch >= 0) return fileMatch
    }

    val normalizedTitle = state.title.normalizedSearchText()
    val normalizedArtist = state.artist.normalizedSearchText()
    if (normalizedTitle.isBlank()) return -1
    return tracks.indexOfFirst { track ->
        val titleMatches = track.title.normalizedSearchText() == normalizedTitle
        val artistMatches = normalizedArtist.isBlank() || track.artist.normalizedSearchText() == normalizedArtist
        val albumMatches = state.albumKey.isBlank() ||
            track.album.key == state.albumKey ||
            legacyAlbumKey(track.album.title, track.album.artist) == state.albumKey
        val durationMatches = state.durationMillis <= 0L || track.durationMillis <= 0L || abs(track.durationMillis - state.durationMillis) <= 2_000L
        titleMatches && artistMatches && albumMatches && durationMatches
    }
}

@OptIn(UnstableApi::class)
private fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(stableKey())
        .setUri(uri)
        .setMimeType(safeAudioMediaItemMimeType())
        .setCustomCacheKey(displayName?.takeIf { isStreamLibraryFolder(folderPath) } ?: stableKey())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album.title)
                .setAlbumArtist(album.artist)
                .setDisplayTitle(title)
                .setSubtitle(artist.ifBlank { album.artist })
                .setDescription(album.title)
                .setArtworkUri(album.coverUri)
                .setIsPlayable(true)
                .setExtras(proOutputMediaItemExtras())
                .build(),
        )
        .build()

private fun Track.safeAudioMediaItemMimeType(): String? =
    mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.startsWith("audio/", ignoreCase = true) }

internal fun Track.audioTypeLabel(): String =
    mimeType?.let { audioTypeLabel(it) }
        ?: listOf("MP3", "FLAC", "AAC", "WAV", "OGG", "OPUS").firstOrNull { quality.contains(it, ignoreCase = true) }
        ?: "AUDIO"

private fun artistsFor(tracks: List<Track>): List<ArtistSummary> =
    tracks
        .groupBy { it.artist.ifBlank { "Unknown artist" } }
        .map { (name, artistTracks) -> ArtistSummary(name, artistTracks, artistTracks.sumOf { it.durationMillis }) }
        .sortedBy { it.name.lowercase() }

private fun artistCount(tracks: List<Track>): Int =
    tracks
        .asSequence()
        .map { it.artist.ifBlank { "Unknown artist" } }
        .toSet()
        .size

internal fun artistColor(name: String): Color {
    val palette = palettes[Math.floorMod(name.hashCode(), palettes.size)]
    return lerp(palette.first, palette.second, 0.36f)
}

private fun buildSettingsDiagnosticsExport(settings: PulseSettingsState): JSONObject {
    val diagnostics = PulseOutputDiagnosticsStore.diagnostics.value
    return JSONObject()
        .put("schemaVersion", settings.schemaVersion)
        .put("app", "PulseDeck")
        .put("createdAt", System.currentTimeMillis())
        .put("settings", JSONObject()
            .put("diagnosticsEnabled", settings.diagnostics.sendErrorsToDeveloper)
            .put("includeAudioOutputLog", settings.diagnostics.includeAudioOutputLog)
            .put("includeDeviceCapabilities", settings.diagnostics.includeDeviceCapabilities)
            .put("redactTrackPaths", settings.diagnostics.redactTrackPaths)
            .put("castGated", true)
            .put("lastFmGated", true))
        .put("audioOutput", JSONObject()
            .put("activeOutputMode", diagnostics.activeOutputMode)
            .put("audioFocusRequestsFocus", diagnostics.audioFocusRequestsFocus)
            .put("handleNoisyOutput", diagnostics.handleNoisyOutput)
            .put("transparentPathExpected", diagnostics.transparentPathExpected)
            .put("nativeMedia3DspRequested", diagnostics.nativeMedia3DspRequested)
            .put("nativeMedia3DspActive", diagnostics.nativeMedia3DspActive)
            .put("preventClippingActive", diagnostics.preventClippingActive)
            .put("estimatedHeadroomDb", diagnostics.estimatedHeadroomDb)
            .put("dspFallbackReason", diagnostics.dspFallbackReason ?: JSONObject.NULL)
            .put("updatedAtMillis", diagnostics.updatedAtMillis)
            .put("routes", JSONArray(diagnostics.routeStatuses.map { status ->
                JSONObject()
                    .put("route", status.route.label)
                    .put("requestedPlugin", status.requestedPlugin.label)
                    .put("actualPlugin", status.actualPlugin.label)
                    .put("available", status.available)
                    .put("reason", status.reason ?: "Available")
            })))
}

private fun trackInfoDialog(track: Track, quality: String): InfoDialogState =
    InfoDialogState(
        title = "Metadata",
        subtitle = track.title,
        rows = listOf(
            "Artist" to track.artist,
            "Album" to track.album.title,
            "Duration" to track.duration,
            "Quality" to quality,
            "Type" to track.audioTypeLabel(),
            "File" to (track.displayName ?: "Unknown file"),
            "Folder" to normalizedFolderPath(track),
            "Size" to formatFileSize(track.sizeBytes),
            "Modified" to formatModifiedDate(track.modifiedMillis),
        ),
    )

private fun buildLibrarySummarySnapshot(
    tracks: List<Track>,
    playlistTrackKeys: Set<String>,
    bookmarkedTrackKeys: Set<String>,
    trackPlayCounts: Map<String, Int>,
): LibrarySummarySnapshot {
    val albums = albumsFor(tracks)
    return LibrarySummarySnapshot(
        albums = albums,
        albumByKey = albums.associateBy { it.key },
        groupCounts = LocalLibraryGroupKind.entries.associateWith { kind -> localLibraryGroupCount(kind, tracks) },
        artistCount = artistCount(tracks),
        localPlaylistTrackCount = localFilteredTrackCount(LocalTrackFilterKind.Playlists, tracks, playlistKeys = playlistTrackKeys),
        bookmarkedTrackCount = localFilteredTrackCount(LocalTrackFilterKind.Bookmarks, tracks, bookmarkedKeys = bookmarkedTrackKeys),
        mostPlayedTrackCount = localFilteredTrackCount(LocalTrackFilterKind.MostPlayed, tracks, playCounts = trackPlayCounts),
    )
}

private fun albumsFor(tracks: List<Track>): List<Album> =
    tracks.groupBy { it.album.groupKey }
        .values
        .mapNotNull { albumTracks ->
            val representative = albumTracks.maxWithOrNull(
                compareBy<Track> { it.album.coverUri != null }
                    .thenBy { it.album.sourceUri != null }
                    .thenBy { it.sizeBytes }
                    .thenBy { it.modifiedMillis }
            )?.album ?: return@mapNotNull null
            val sourceCandidates = albumTracks
                .sortedWith(
                    compareByDescending<Track> { it.album.sourceUri != null }
                        .thenByDescending { it.sizeBytes }
                        .thenByDescending { it.modifiedMillis }
                )
                .mapNotNull { it.album.sourceUri }
                .distinct()
                .take(3)
            val cover = albumTracks.firstNotNullOfOrNull { it.album.coverUri } ?: representative.coverUri
            val albumArtist = albumArtistLabel(albumTracks)
            representative.copy(
                artist = albumArtist,
                coverUri = cover,
                sourceUri = sourceCandidates.firstOrNull() ?: representative.sourceUri,
                artSourceUris = sourceCandidates.ifEmpty { listOfNotNull(representative.sourceUri) },
            )
        }
        .ifEmpty { demoAlbums }
        .sortedBy { it.title.trim().lowercase() }

private fun albumArtistLabel(tracks: List<Track>): String {
    val artists = tracks
        .map { it.artist.ifBlank { "Unknown artist" } }
        .distinctBy { it.normalizedSearchText() }
    return when {
        artists.isEmpty() -> "Unknown artist"
        artists.size == 1 -> artists.first()
        else -> "Various Artists"
    }
}

private fun albumFor(title: String, artist: String, coverUri: Uri? = null, sourceUri: Uri? = null): Album {
    val palette = palettes[Math.floorMod("$title/$artist".hashCode(), palettes.size)]
    val words = title.split(" ").filter { it.isNotBlank() }
    val mark = words.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "PD" }
    return Album(title = title, artist = artist, mark = mark.take(3), tint = palette.first, alt = palette.second, coverUri = coverUri, sourceUri = sourceUri)
}

private suspend fun loadAudioWaveform(context: Context, uri: Uri?, seed: String, bars: Int): List<Float> = withContext(Dispatchers.IO) {
    if (uri == null) return@withContext defaultWaveform(seed, bars)
    runCatching { readEncodedWaveform(context, uri, bars) }
        .getOrElse { defaultWaveform(seed, bars) }
}

private fun readEncodedWaveform(context: Context, uri: Uri, bars: Int): List<Float> {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return defaultWaveform(uri.toString(), bars)
        val format = extractor.getTrackFormat(audioTrack)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        extractor.selectTrack(audioTrack)
        val buckets = FloatArray(bars)
        val counts = IntArray(bars)
        val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
        var rollingBucket = 0
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size <= 0) break
            val sampleTime = extractor.sampleTime
            val bucket = if (durationUs > 0L && sampleTime >= 0L) {
                ((sampleTime.toDouble() / durationUs.toDouble()) * (bars - 1)).toInt().coerceIn(0, bars - 1)
            } else {
                rollingBucket.coerceIn(0, bars - 1)
            }
            val step = max(1, size / 256)
            var sum = 0f
            var sampled = 0
            var i = 0
            while (i < size) {
                sum += abs(buffer.get(i).toInt()) / 128f
                sampled++
                i += step
            }
            if (sampled > 0) {
                buckets[bucket] += sum / sampled
                counts[bucket]++
            }
            rollingBucket++
            extractor.advance()
        }
        val averaged = buckets.mapIndexed { index, value -> if (counts[index] > 0) value / counts[index] else 0f }.toMutableList()
        val fallback = averaged.filter { it > 0f }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0.32f
        averaged.indices.forEach { index -> if (averaged[index] <= 0f) averaged[index] = fallback * 0.68f }
        val peak = averaged.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
        averaged.map { (it / peak).coerceIn(0.10f, 1f) }
    } finally {
        extractor.release()
    }
}

internal fun defaultWaveform(seed: String, bars: Int): List<Float> {
    var value = seed.hashCode()
    return List(bars) { index ->
        value = value * 1103515245 + 12345 + index * 97
        val raw = (value ushr 16 and 0x7FFF) / 32767f
        (0.16f + raw * 0.78f).coerceIn(0.12f, 0.96f)
    }
}

private fun albumArtUri(albumId: Long): Uri? =
    if (albumId > 0L) ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId) else null

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown"
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1f) String.format(Locale.US, "%.1f MB", mb) else "${(bytes / 1024L).coerceAtLeast(1L)} KB"
}

private enum class SettingsExportKind { Settings, PrivateBackup, Diagnostics }
internal enum class ArtistSortMode {
    Name,
    Songs,
    Duration,
}

internal val ArtistSortMode.label: String
    get() = when (this) {
        ArtistSortMode.Name -> "Name"
        ArtistSortMode.Songs -> "Song Count"
        ArtistSortMode.Duration -> "Total Time"
    }

internal val ArtistSortMode.subtitle: String
    get() = when (this) {
        ArtistSortMode.Name -> "A to Z"
        ArtistSortMode.Songs -> "Most songs first"
        ArtistSortMode.Duration -> "Longest total time first"
    }
internal enum class PlayerModeMenu { Shuffle, Repeat }

internal enum class DeckIcon {
    Back,
    Grid,
    Bars,
    Search,
    Queue,
    More,
    Plus,
    Minus,
    Play,
    Pause,
    Previous,
    Rewind,
    Forward,
    Next,
    Shuffle,
    Repeat,
    Wave,
    Timer,
    Sliders,
    Disc,
    Visualizer,
    MusicList,
    Songs,
    Folder,
    Hierarchy,
    Person,
    People,
    Tag,
    Calendar,
    Pencil,
    Playlist,
    LibraryStack,
    EmptyBox,
    Filter,
    Notification,
    Heart,
    Comment,
    Compass,
    UserCircle,
    Stream,
    StreamMix,
    StreamRadio,
    StreamPin,
    StreamOffline,
    StreamSource,
    StreamAdd,
    StreamRemove,
    StreamReplace,
    StreamEdit,
    StreamRecent,
    StreamLike,
    Discover,
    Bookmark,
    Star,
    Trash,
    ThumbUp,
    ThumbDown,
    Check,
    Info,
    Settings,
    Close,
    Signal,
    Crossfade,
    Loudness,
    Focus,
    Equalizer,
    Resampler,
    Headphones,
    Output,
    Lab,
}

internal val DeckIcon.drawableRes: Int
    get() = when (this) {
        DeckIcon.Back -> R.drawable.iconoir_nav_arrow_left
        DeckIcon.Grid -> R.drawable.iconoir_view_grid
        DeckIcon.Bars -> R.drawable.iconoir_control_slider
        DeckIcon.Search -> R.drawable.iconoir_search
        DeckIcon.Queue -> R.drawable.iconoir_list
        DeckIcon.More -> R.drawable.iconoir_more_vert
        DeckIcon.Plus -> R.drawable.iconoir_plus
        DeckIcon.Minus -> R.drawable.iconoir_minus
        DeckIcon.Play -> R.drawable.iconoir_play
        DeckIcon.Pause -> R.drawable.iconoir_pause
        DeckIcon.Previous -> R.drawable.iconoir_skip_prev
        DeckIcon.Rewind -> R.drawable.iconoir_rewind
        DeckIcon.Forward -> R.drawable.iconoir_forward
        DeckIcon.Next -> R.drawable.iconoir_skip_next
        DeckIcon.Shuffle -> R.drawable.iconoir_shuffle
        DeckIcon.Repeat -> R.drawable.iconoir_repeat
        DeckIcon.Wave -> R.drawable.iconoir_sine_wave
        DeckIcon.Timer -> R.drawable.iconoir_timer
        DeckIcon.Sliders -> R.drawable.iconoir_control_slider
        DeckIcon.Disc -> R.drawable.iconoir_compact_disc
        DeckIcon.Visualizer -> R.drawable.iconoir_activity
        DeckIcon.MusicList -> R.drawable.iconoir_music_double_note
        DeckIcon.Songs -> R.drawable.iconoir_music_note
        DeckIcon.Folder -> R.drawable.iconoir_folder
        DeckIcon.Hierarchy -> R.drawable.iconoir_view_structure_down
        DeckIcon.Person -> R.drawable.iconoir_user
        DeckIcon.People -> R.drawable.iconoir_group
        DeckIcon.Tag -> R.drawable.iconoir_label
        DeckIcon.Calendar -> R.drawable.iconoir_calendar
        DeckIcon.Pencil -> R.drawable.iconoir_edit_pencil
        DeckIcon.Playlist -> R.drawable.iconoir_playlist
        DeckIcon.LibraryStack -> R.drawable.iconoir_book_stack
        DeckIcon.EmptyBox -> R.drawable.iconoir_empty_box
        DeckIcon.Filter -> R.drawable.iconoir_filter
        DeckIcon.Notification -> R.drawable.iconoir_bell_notification
        DeckIcon.Heart -> R.drawable.iconoir_heart
        DeckIcon.Comment -> R.drawable.iconoir_chat_bubble
        DeckIcon.Compass -> R.drawable.iconoir_compass
        DeckIcon.UserCircle -> R.drawable.iconoir_user_circle
        DeckIcon.Stream -> R.drawable.iconoir_antenna_signal
        DeckIcon.StreamMix -> R.drawable.iconoir_shuffle
        DeckIcon.StreamRadio -> R.drawable.iconoir_podcast
        DeckIcon.StreamPin -> R.drawable.iconoir_pin
        DeckIcon.StreamOffline -> R.drawable.iconoir_cloud_download
        DeckIcon.StreamSource -> R.drawable.iconoir_album_list
        DeckIcon.StreamAdd -> R.drawable.iconoir_playlist_plus
        DeckIcon.StreamRemove -> R.drawable.iconoir_xmark_square
        DeckIcon.StreamReplace -> R.drawable.iconoir_refresh_double
        DeckIcon.StreamEdit -> R.drawable.iconoir_edit_pencil
        DeckIcon.StreamRecent -> R.drawable.iconoir_clock_rotate_right
        DeckIcon.StreamLike -> R.drawable.iconoir_heart
        DeckIcon.Discover -> R.drawable.iconoir_compass
        DeckIcon.Bookmark -> R.drawable.iconoir_bookmark
        DeckIcon.Star -> R.drawable.iconoir_empty_box
        DeckIcon.Trash -> R.drawable.iconoir_trash
        DeckIcon.ThumbUp -> R.drawable.iconoir_thumbs_up
        DeckIcon.ThumbDown -> R.drawable.iconoir_thumbs_down
        DeckIcon.Check -> R.drawable.iconoir_check
        DeckIcon.Info -> R.drawable.iconoir_info_circle
        DeckIcon.Settings -> R.drawable.iconoir_settings
        DeckIcon.Close -> R.drawable.iconoir_xmark
        DeckIcon.Signal -> R.drawable.iconoir_antenna_signal
        DeckIcon.Crossfade -> R.drawable.iconoir_transition_right
        DeckIcon.Loudness -> R.drawable.iconoir_sound_high
        DeckIcon.Focus -> R.drawable.iconoir_bell_notification
        DeckIcon.Equalizer -> R.drawable.iconoir_control_slider
        DeckIcon.Resampler -> R.drawable.iconoir_sine_wave
        DeckIcon.Headphones -> R.drawable.iconoir_headset
        DeckIcon.Output -> R.drawable.iconoir_input_output
        DeckIcon.Lab -> R.drawable.iconoir_flask
    }

internal val DeckIcon.accessibilityLabel: String
    get() = when (this) {
        DeckIcon.Back -> "Back"
        DeckIcon.Grid -> "Library"
        DeckIcon.Bars -> "Audio"
        DeckIcon.Search -> "Search"
        DeckIcon.Queue -> "Queue"
        DeckIcon.More -> "Menu"
        DeckIcon.Plus -> "Add"
        DeckIcon.Minus -> "Remove"
        DeckIcon.Play -> "Play"
        DeckIcon.Pause -> "Pause"
        DeckIcon.Previous -> "Previous track"
        DeckIcon.Rewind -> "Previous category"
        DeckIcon.Forward -> "Next category"
        DeckIcon.Next -> "Next track"
        DeckIcon.Shuffle -> "Shuffle"
        DeckIcon.Repeat -> "Repeat"
        DeckIcon.Wave -> "Visualization"
        DeckIcon.Timer -> "Sleep timer"
        DeckIcon.Sliders, DeckIcon.Equalizer -> "Equalizer"
        DeckIcon.Disc -> "Album art"
        DeckIcon.Visualizer -> "Visualizer"
        DeckIcon.MusicList, DeckIcon.Songs -> "Songs"
        DeckIcon.Folder, DeckIcon.Hierarchy -> "Folders"
        DeckIcon.Person, DeckIcon.People, DeckIcon.UserCircle -> "Artist"
        DeckIcon.Tag -> "Tag"
        DeckIcon.Calendar -> "Date"
        DeckIcon.Pencil -> "Edit"
        DeckIcon.Playlist -> "Playlist"
        DeckIcon.LibraryStack -> "Stream library"
        DeckIcon.EmptyBox -> "Empty"
        DeckIcon.Filter -> "Filter"
        DeckIcon.Notification -> "Notifications"
        DeckIcon.Heart -> "Favorite"
        DeckIcon.Comment -> "Comments"
        DeckIcon.Compass, DeckIcon.Discover -> "Discover"
        DeckIcon.Stream -> "Stream"
        DeckIcon.StreamMix -> "Stream mix"
        DeckIcon.StreamRadio -> "Radio"
        DeckIcon.StreamPin -> "Pin stream"
        DeckIcon.StreamOffline -> "Offline stream"
        DeckIcon.StreamSource -> "Stream source"
        DeckIcon.StreamAdd -> "Add stream"
        DeckIcon.StreamRemove -> "Remove stream"
        DeckIcon.StreamReplace -> "Replace stream"
        DeckIcon.StreamEdit -> "Edit stream"
        DeckIcon.StreamRecent -> "Recent streams"
        DeckIcon.StreamLike -> "Like stream"
        DeckIcon.Bookmark -> "Bookmark"
        DeckIcon.Star -> "Item"
        DeckIcon.Trash -> "Delete"
        DeckIcon.ThumbUp -> "Like"
        DeckIcon.ThumbDown -> "Dislike"
        DeckIcon.Check -> "Selected"
        DeckIcon.Info -> "Info"
        DeckIcon.Settings -> "Settings"
        DeckIcon.Close -> "Close"
        DeckIcon.Signal -> "Signal chain"
        DeckIcon.Crossfade -> "Crossfade"
        DeckIcon.Loudness -> "Replay gain"
        DeckIcon.Focus -> "Audio focus"
        DeckIcon.Resampler -> "Resampler"
        DeckIcon.Headphones -> "Headphones"
        DeckIcon.Output -> "Output"
        DeckIcon.Lab -> "Advanced settings"
    }

private val palettes = listOf(
    Color(0xFFB66C42) to Color(0xFFE2C064),
    Color(0xFF335BFF) to Color(0xFF80F1C6),
    Color(0xFFFFC83D) to Color(0xFFC62D2D),
    Color(0xFFC85B25) to Color(0xFF6D2E14),
    Color(0xFF4130A9) to Color(0xFFB94F90),
    Color(0xFF2E8974) to Color(0xFFE68A42),
)
internal val categories = listOf(
    Category(PREMIUMDECK_SOURCE_CATEGORY, PREMIUMDECK_SOURCE_MARK, Color(0xFF7FE7C3)),
    Category("PulseRadio", "PR", Color(0xFF5DD7FF)),
    Category("Album Artists", "AA", Color(0xFF6760B8)),
    Category("Albums", "A", Color(0xFF6E63F2)),
    Category("All Songs", "S", Color(0xFF85A3FF)),
    Category("Artists", "AR", Color(0xFF5D58A7)),
    Category("Bookmarks", "B", Color(0xFF4D7DFF)),
    Category("Composers", "C", Color(0xFF188A58)),
    Category("Folders", "F", Color(0xFF539BFF)),
    Category("Folders Hierarchy", "FH", Color(0xFF5868E8)),
    Category("Genres", "G", Color(0xFF8B4A89)),
    Category("Most Played", "MP", Color(0xFFA57BEF)),
    Category("Playlists", "P", Color(0xFF925B5B)),
    Category("Years", "20", Color(0xFF69CADA)),
)

internal fun libraryCategoryKindForName(name: String): LibraryCategoryKind? =
    when (name) {
        "All Songs" -> LibraryCategoryKind.AllSongs
        PREMIUMDECK_SOURCE_CATEGORY -> LibraryCategoryKind.PremiumDeck
        "PulseRadio" -> LibraryCategoryKind.PulseRadio
        "Folders" -> LibraryCategoryKind.Folders
        "Folders Hierarchy" -> LibraryCategoryKind.FolderHierarchy
        "Albums" -> LibraryCategoryKind.Albums
        "Artists" -> LibraryCategoryKind.Artists
        "Album Artists" -> LibraryCategoryKind.AlbumArtists
        "Genres" -> LibraryCategoryKind.Genres
        "Years" -> LibraryCategoryKind.Years
        "Composers" -> LibraryCategoryKind.Composers
        "Playlists" -> LibraryCategoryKind.Playlists
        "Bookmarks" -> LibraryCategoryKind.Bookmarks
        "Most Played" -> LibraryCategoryKind.MostPlayed
        else -> null
    }

private val specialLibraryFeatureCategoryNames = listOf(PREMIUMDECK_SOURCE_CATEGORY, "PulseRadio")

internal fun libraryCategoriesForDisplay(): List<Category> {
    val specialCategories = specialLibraryFeatureCategoryNames.mapNotNull { pinnedName ->
        categories.firstOrNull { it.name == pinnedName }
    }
    val regularCategories = categories
        .filterNot { it.isSpecialLibraryFeatureCategory() }
        .sortedBy { it.name.lowercase(Locale.US) }
    return specialCategories + regularCategories
}

internal fun Category.isSpecialLibraryFeatureCategory(): Boolean =
    name in specialLibraryFeatureCategoryNames

private val demoAlbums = listOf(
    Album("Addis Bloom", "Yamlu Molla / Mena", "AB", Color(0xFFB66C42), Color(0xFFE2C064)),
    Album("Brand Music 2026", "Samsung Sessions", "BM", Color(0xFF335BFF), Color(0xFF80F1C6)),
    Album("Datan", "Yared Negu", "DT", Color(0xFFFFC83D), Color(0xFFC62D2D)),
    Album("Enzira", "Mastewal Eyayu", "EZ", Color(0xFFC85B25), Color(0xFF6D2E14)),
    Album("Late Signal", "Aster Route", "LS", Color(0xFF4130A9), Color(0xFFB94F90)),
    Album("Forward Motion", "Deck Lab", "FM", Color(0xFF2E8974), Color(0xFFE68A42)),
)
private val demoTracks = listOf(
    Track("Akale Gena", "Muluken Melesse - Yigerem", "2:44", demoAlbums[0], durationMillis = 164_000L),
    Track("Emetalew", "Mastewal Eyayu - Enzira", "4:13", demoAlbums[3], durationMillis = 253_000L),
    Track("Kefagn", "Mastewal Eyayu - Enzira", "4:18", demoAlbums[3], durationMillis = 258_000L),
    Track("Electric", "Mena Werede & Amanuel Mussie", "2:44", demoAlbums[0], durationMillis = 164_000L),
)




















