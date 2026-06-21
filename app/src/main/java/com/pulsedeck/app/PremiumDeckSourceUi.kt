package com.pulsedeck.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun YouTubeStatusBadge(status: YouTubeSourceStatus, downloadState: YouTubeDownloadState = YouTubeDownloadState.None, progress: Int = 0) {
    val effectiveLabel = when (downloadState) {
        YouTubeDownloadState.None, YouTubeDownloadState.Prompted -> status.label
        YouTubeDownloadState.Downloading -> "${progress.coerceIn(1, 99)}%"
        YouTubeDownloadState.Downloaded -> "saved"
        YouTubeDownloadState.Failed -> "failed"
    }
    val color = when {
        downloadState == YouTubeDownloadState.Downloading -> Color(0xFFFFD166)
        downloadState == YouTubeDownloadState.Downloaded || status == YouTubeSourceStatus.Downloaded -> Color(0xFFFFD166)
        downloadState == YouTubeDownloadState.Failed || status == YouTubeSourceStatus.ResolverNeeded -> Color(0xFFFF7B72)
        status == YouTubeSourceStatus.Cached -> Color(0xFF85A3FF)
        else -> Color(0xFF6FEA8A)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(effectiveLabel, color = color, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun OfflineDownloadProgress(
    progress: Int,
    modifier: Modifier = Modifier,
    label: String = "Saving offline",
) {
    val clampedProgress = progress.coerceIn(1, 100)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress / 100f,
        animationSpec = tween(PulseMotion.Duration.Short),
        label = "offlineDownloadProgress",
    )
    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Offline save progress"
                progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress.coerceIn(0f, 1f), 0f..1f)
                stateDescription = "$clampedProgress percent"
            },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$clampedProgress%", color = Color(0xFFFFD166), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black)
        }
        Canvas(Modifier.fillMaxWidth().height(5.dp).padding(top = 2.dp)) {
            val corner = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                size = Size(size.width, size.height),
                cornerRadius = corner,
            )
            val activeWidth = size.width * animatedProgress.coerceIn(0f, 1f)
            if (activeWidth > 0.5f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF86B3FF), StreamAccentRed)),
                    size = Size(activeWidth, size.height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

@Composable
internal fun SmartCachePromptDialog(source: YouTubeSource, onDismiss: () -> Unit, onNeverAsk: () -> Unit, onKeepOffline: () -> Unit) {
    BasicInfoModal(title = "Keep Offline?", subtitle = source.title, onDismiss = onDismiss) {
        Text(
            "You have played this source ${source.playCount.coerceAtLeast(3)} times. PulseDeck can save it into your public Music library for faster playback and offline listening.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        SleepDialogButton("Keep Offline", Modifier.fillMaxWidth(), tone = StreamAccentRed.copy(alpha = 0.62f), onClick = onKeepOffline)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepDialogButton("Not Now", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.10f), onClick = onDismiss)
            SleepDialogButton("Never Ask", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.07f), onClick = onNeverAsk)
        }
    }
}

@Composable
internal fun PremiumDeckOfflineSaveDialog(
    source: YouTubeSource,
    onDismiss: () -> Unit,
    onKeepOffline: () -> Unit,
    onOpenOfflineSaved: () -> Unit,
    onSourceActions: () -> Unit,
) {
    val savedOffline = source.downloadState == YouTubeDownloadState.Downloaded ||
        source.status == YouTubeSourceStatus.Downloaded ||
        source.downloadedUri != null
    val downloading = source.downloadState == YouTubeDownloadState.Downloading
    val progressLabel = source.offlineSaveStatusText()
    BasicInfoModal(title = "Offline Save", subtitle = source.title, onDismiss = onDismiss) {
        Text(source.author, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        YouTubeStatusBadge(source.status, source.downloadState, source.downloadProgress)
        Text(progressLabel, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        if (downloading) {
            OfflineDownloadProgress(source.downloadProgress, Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "$PREMIUMDECK_SOURCE_NAME can save this source into the public Music library for offline listening. Saved items appear in the Offline Saved layout.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        SourceActionButton(
            label = source.offlineSaveDialogActionLabel(),
            icon = if (downloading) DeckIcon.Timer else DeckIcon.StreamOffline,
            modifier = Modifier.fillMaxWidth(),
            onClick = onKeepOffline,
            active = savedOffline,
            enabled = !savedOffline && !downloading,
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton("Offline Saved", DeckIcon.Bookmark, Modifier.weight(1f), onOpenOfflineSaved)
            SourceActionButton("More", DeckIcon.More, Modifier.weight(1f), onSourceActions)
        }
    }
}

@Composable
internal fun YouTubeSourceActionsDialog(
    source: YouTubeSource,
    playlists: List<YouTubePlaylist>,
    capabilities: ResolverCapabilities,
    artistFollowed: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onAccept: () -> Unit,
    onRename: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleArtistFollow: () -> Unit,
    onKeepOffline: () -> Unit,
    onRemoveDownload: () -> Unit,
    onToggleSkip: () -> Unit,
    onToggleTrim: () -> Unit,
    onMetadata: () -> Unit,
    onOpenYouTube: () -> Unit,
    onRemove: () -> Unit,
) {
    BasicInfoModal(title = if (source.reviewState == YouTubeReviewState.Inbox) "Review Stream" else "Stream Source", subtitle = source.title, onDismiss = onDismiss) {
        val suggestion = source.cleanedSuggestion()
        Text(source.author, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (source.reviewState == YouTubeReviewState.Inbox) {
            Spacer(Modifier.height(10.dp))
            Text("Suggested cleanup: ${suggestion.first} - ${suggestion.second}", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        SourceActionButton(if (artistFollowed) "Following Artist" else "Follow Artist", if (artistFollowed) DeckIcon.StreamLike else DeckIcon.Heart, Modifier.fillMaxWidth(), onToggleArtistFollow, active = artistFollowed)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton("Play", DeckIcon.Play, Modifier.weight(1f), onPlay)
            SourceActionButton(if (source.reviewState == YouTubeReviewState.Inbox) "Accept" else "Rename", if (source.reviewState == YouTubeReviewState.Inbox) DeckIcon.StreamAdd else DeckIcon.StreamEdit, Modifier.weight(1f), onClick = {
                if (source.reviewState == YouTubeReviewState.Inbox) onAccept() else onRename()
            })
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton(
                if (source.reaction == YouTubeReaction.Liked) "Liked" else "Like",
                DeckIcon.ThumbUp,
                Modifier.weight(1f),
                onToggleLike,
                active = source.reaction == YouTubeReaction.Liked,
            )
            SourceActionButton("Playlist", DeckIcon.StreamPin, Modifier.weight(1f), onAddToPlaylist)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton(
                source.offlineSaveActionLabel(),
                if (source.downloadState == YouTubeDownloadState.Downloading) DeckIcon.Timer else DeckIcon.StreamOffline,
                Modifier.fillMaxWidth(),
                onKeepOffline,
                enabled = source.downloadState != YouTubeDownloadState.Downloading,
            )
        }
        if (source.downloadState == YouTubeDownloadState.Downloading) {
            OfflineDownloadProgress(source.downloadProgress, Modifier.padding(top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton(if (source.skipSegmentsEnabled) "Skip On" else "Skip Off", DeckIcon.Forward, Modifier.weight(1f), onToggleSkip, active = source.skipSegmentsEnabled)
            SourceActionButton(if (source.trimSilenceOnDownload) "Trim On" else if (capabilities.ffmpegAvailable) "Trim Off" else "No trim service", DeckIcon.Wave, Modifier.weight(1f), onToggleTrim, active = source.trimSilenceOnDownload, enabled = capabilities.ffmpegAvailable)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceActionButton("Metadata", DeckIcon.StreamEdit, Modifier.weight(1f), onMetadata)
            SourceActionButton(PREMIUMDECK_SOURCE_NAME, DeckIcon.StreamSource, Modifier.weight(1f), onOpenYouTube)
        }
        if (source.downloadedUri != null) {
            SourceActionButton("Remove Download", DeckIcon.Trash, Modifier.fillMaxWidth().padding(top = 10.dp), onRemoveDownload, destructive = true)
        }
        if (source.chapters.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Chapters", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            source.chapters.take(4).forEach { chapter ->
                Text("${formatDuration(chapter.startMillis)}  ${chapter.title}", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SourceActionButton("Remove Source", DeckIcon.StreamRemove, Modifier.fillMaxWidth().padding(top = 14.dp), onRemove, destructive = true)
    }
}

@Composable
internal fun SourceActionButton(
    label: String,
    icon: DeckIcon,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    destructive -> Color(0xFFB8322A).copy(alpha = 0.42f)
                    active -> Color.White.copy(alpha = 0.12f)
                    else -> Color.White.copy(alpha = 0.07f)
                },
            )
            .border(1.dp, Color.White.copy(alpha = if (active) 0.13f else 0.07f), RoundedCornerShape(20.dp))
            .then(if (enabled) Modifier.pressScaleEffect(interactionSource).clickable(interactionSource = interactionSource, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = if (enabled) 0.86f else 0.32f), Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = StreamTextPrimary.copy(alpha = if (enabled) 0.92f else 0.36f), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
