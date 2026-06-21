package com.pulsedeck.app

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

@Composable
internal fun YouTubeStreamScreen(source: YouTubeSource, onBack: () -> Unit, onOpenExternal: () -> Unit, onInfo: () -> Unit) {
    BackHandler { onBack() }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
        contentPadding = PaddingValues(bottom = 184.dp),
    ) {
        item {
            PageNavigationHeader(
                title = "Sources",
                subtitle = source.title,
                onBack = onBack,
                modifier = Modifier.padding(top = 28.dp, bottom = 18.dp),
            ) {
                IconPill(DeckIcon.Info, onInfo, Modifier.width(66.dp))
                IconPill(DeckIcon.Stream, onOpenExternal, Modifier.width(66.dp))
            }
        }
        item {
            Text(source.title, color = Color.White, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(source.author, color = Muted, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                YouTubeStatusBadge(source.status, source.downloadState, source.downloadProgress)
                MiniMetaPill(source.kind.label)
                MiniMetaPill(source.quality.label)
                if (source.playCount >= 3) MiniMetaPill("Smart Cache")
            }
        }
        if (source.downloadState == YouTubeDownloadState.Downloading) {
            item { OfflineDownloadProgress(source.downloadProgress, Modifier.padding(top = 10.dp)) }
        }
        item {
            Spacer(Modifier.height(18.dp))
            YouTubeEmbeddedPlayer(source)
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.34f))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Text("Playing inside PulseDeck", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text("The app now tries a lightweight audio stream resolver first. This embedded view appears only when stream resolution is unavailable for that source.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (source.chapters.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 210.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(source.chapters) { chapter ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(formatDuration(chapter.startMillis), color = Color.White.copy(alpha = 0.70f), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(50.dp))
                            Text(chapter.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMetaPill(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbeddedPlayer(source: YouTubeSource) {
    val embedUrl = remember(source.url) { source.youtubeEmbedUrl() }
    val webViewRef = remember { arrayOfNulls<WebView>(1) }
    DisposableEffect(source.streamDistinctKey()) {
        onDispose {
            YouTubeNetworkDiagnostics.reportWebViewRelease(source)
            webViewRef[0]?.releaseEmbeddedPlayer()
            webViewRef[0] = null
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewRef[0] = this
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadsImagesAutomatically = true
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    loadUrl(embedUrl)
                }
            },
            update = { view ->
                webViewRef[0] = view
                if (view.url != embedUrl) view.loadUrl(embedUrl)
            },
        )
    }
}

private fun WebView.releaseEmbeddedPlayer() {
    runCatching { stopLoading() }
    runCatching { webChromeClient = null }
    runCatching { loadUrl("about:blank") }
    runCatching { onPause() }
    runCatching { removeAllViews() }
    runCatching { destroy() }
}

@Composable
internal fun QuickAddDialog(onDismiss: () -> Unit, onScanLocal: () -> Unit, onAddYouTube: (YouTubeSource) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pasteMode by remember { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableStateOf(YouTubeQuality.High) }
    var resolving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val detection = remember(input) { detectYouTubeSource(input) }

    BasicInfoModal(title = "Quick Add", subtitle = if (pasteMode) "Paste PremiumDeck link or channel handle" else "Choose a source", onDismiss = onDismiss) {
        if (!pasteMode) {
            QuickAddAction("Scan Local Folders", "Refresh local audio from this phone", DeckIcon.Folder, onScanLocal)
            Spacer(Modifier.height(10.dp))
            QuickAddAction("Paste PremiumDeck Link", "Video, playlist, or channel handle", DeckIcon.Stream) { pasteMode = true }
            Spacer(Modifier.height(12.dp))
            Text("The PremiumDeck shelf keeps online sources separate from local files until you choose what to cache or organize.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black.copy(alpha = 0.34f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIcon(DeckIcon.Stream, Color.White.copy(alpha = 0.74f), Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (input.isBlank()) Text("youtube.com/watch... or @channel", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QualityChip("128", quality == YouTubeQuality.Standard, Modifier.weight(1f)) { quality = YouTubeQuality.Standard }
                QualityChip("256", quality == YouTubeQuality.High, Modifier.weight(1f)) { quality = YouTubeQuality.High }
            }
            detection?.let {
                Text("Detected ${it.kind.label}", color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
            }
            Text(
                "Only save content you have the right to download. Online sources stay separate until you choose Keep Offline.",
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            message?.let {
                Text(it, color = Color(0xFFFFD166), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SleepDialogButton("Cancel", Modifier.weight(1f), tone = Color.White.copy(alpha = 0.10f), onClick = onDismiss)
                SleepDialogButton(
                    if (resolving) "Checking" else "Add",
                    Modifier.weight(1f),
                    tone = StreamAccentRed.copy(alpha = if (detection == null || resolving) 0.28f else 0.62f),
                ) {
                    val detected = detection
                    if (detected == null) {
                        message = "Paste a valid PremiumDeck video, playlist, or @channel."
                        return@SleepDialogButton
                    }
                    if (resolving) return@SleepDialogButton
                    resolving = true
                    message = "Fetching metadata..."
                    scope.launch {
                        val source = resolveYouTubeSource(detected, quality)
                        resolving = false
                        onAddYouTube(source)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddAction(label: String, subtitle: String, icon: DeckIcon, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(icon, Color.White, Modifier.size(25.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QualityChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (selected) StreamAccentRed.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.18f else 0.06f), RoundedCornerShape(21.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("$label kbps", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

