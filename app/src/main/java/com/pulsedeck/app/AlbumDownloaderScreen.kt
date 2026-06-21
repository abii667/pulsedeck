package com.pulsedeck.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun AlbumDownloaderScreen(
    drafts: List<AlbumDownloadDraft>,
    downloadJobs: List<AlbumAudioDownloadJob>,
    premiumSources: List<YouTubeSource>,
    albumProjectTasks: Map<String, AlbumProjectTaskState>,
    initialReleaseId: String? = null,
    onBack: () -> Unit,
    onSaveDraft: (AlbumDownloadRelease) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onStartHighestQualityDownload: (AlbumDownloadRelease) -> Unit,
    onMatchRelease: (AlbumDownloadRelease) -> Unit,
    onPlayAlbum: (AlbumDownloadRelease) -> Unit,
    onPlayTrack: (AlbumDownloadRelease, AlbumDownloadTrack) -> Unit,
    onQueueOffline: (AlbumDownloadRelease) -> Unit,
    onAddLocal: (AlbumDownloadRelease) -> Unit,
    onSelectTrackMatch: (AlbumDownloadRelease, AlbumDownloadTrack, YouTubeSource) -> Unit,
) {
    var artistQuery by rememberSaveable { mutableStateOf("") }
    var albumQuery by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<AlbumDownloadRelease>>(emptyList()) }
    var releaseOverrides by remember { mutableStateOf<Map<String, AlbumDownloadRelease>>(emptyMap()) }
    var selectedReleaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedTrackMatchKey by rememberSaveable { mutableStateOf<String?>(null) }
    var manualTracklistOpen by rememberSaveable { mutableStateOf(false) }
    var manualTracklistText by rememberSaveable { mutableStateOf("") }
    var albumBuilderInfoOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val savedIds = remember(drafts) { drafts.map { it.release.id }.toSet() }
    val recentProjects = remember(drafts, releaseOverrides) {
        drafts
            .map { draft -> preferredAlbumProjectRelease(draft.release, releaseOverrides[draft.release.id]) }
            .distinctBy { it.id }
            .take(3)
    }
    val selectedRelease = remember(results, drafts, releaseOverrides, selectedReleaseId) {
        selectedReleaseId?.let { id ->
            (listOfNotNull(releaseOverrides[id]) + drafts.map { it.release } + results)
                .filter { it.id == id }
                .reduceOrNull(::preferredAlbumProjectRelease)
        }
    }
    val selectedTask = selectedRelease?.let { albumProjectTasks[it.id] }
    val selectedDownloadJob = remember(downloadJobs, selectedRelease) {
        selectedRelease?.let { release ->
            downloadJobs.firstOrNull { it.releaseId == release.id }
        }
    }
    fun createManualProject() {
        val artist = artistQuery.trim()
        val album = albumQuery.trim()
        val release = parseManualAlbumTracklist(artist, album, manualTracklistText)
        if (release == null) {
            error = "Add artist, album, and at least one track"
            return
        }
        results = (listOf(release) + results).distinctBy { it.id }
        selectedReleaseId = release.id
        error = null
    }
    LaunchedEffect(initialReleaseId, drafts) {
        if (!initialReleaseId.isNullOrBlank() && selectedReleaseId != initialReleaseId) {
            selectedReleaseId = initialReleaseId
        }
    }
    BackHandler(enabled = expandedTrackMatchKey != null) {
        expandedTrackMatchKey = null
    }
    BackHandler(enabled = selectedRelease != null && expandedTrackMatchKey == null) {
        selectedReleaseId = null
    }
    fun search() {
        val artist = artistQuery.trim()
        val album = albumQuery.trim()
        if (artist.length < 2 || searching) return
        scope.launch {
            searching = true
            error = null
            val releases = searchAlbumMetadataReleases(artist, album, premiumSources)
            results = releases
            error = if (releases.isEmpty()) {
                if (album.isBlank()) {
                    "No albums found across Apple/iTunes, Deezer, MusicBrainz, or PremiumDeck"
                } else {
                    "No album metadata found across Apple/iTunes, Deezer, MusicBrainz, or PremiumDeck"
                }
            } else {
                null
            }
            searching = false
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(StreamBase, StreamPanel, StreamBase)))
            .statusBarsPadding()
            .padding(horizontal = StreamHorizontalPadding),
    ) {
        if (selectedRelease != null) {
            AlbumDetailTopBar(
                title = selectedRelease.title,
                subtitle = selectedRelease.artist,
                onBack = { selectedReleaseId = null },
                onMore = {},
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            PageNavigationHeader(
                title = "Albums",
                subtitle = "Album Builder",
                onBack = onBack,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                IconCircle(DeckIcon.Disc, {}, 46.dp)
            }
        }
        if (selectedRelease != null) {
            AlbumDownloadReleaseDetail(
                release = selectedRelease,
                downloadJob = selectedDownloadJob,
                saved = selectedRelease.id in savedIds,
                onClose = { selectedReleaseId = null },
                onSave = { onSaveDraft(selectedRelease) },
                onRemove = if (selectedRelease.id in savedIds) {
                    { onRemoveDraft(selectedRelease.id) }
                } else {
                    null
                },
                taskState = selectedTask,
                premiumSources = premiumSources,
                onMatchTracks = { onMatchRelease(selectedRelease) },
                onPlayAlbum = { onPlayAlbum(selectedRelease) },
                onPlayTrack = { track -> onPlayTrack(selectedRelease, track) },
                onQueueOffline = { onQueueOffline(selectedRelease) },
                onAddLocal = { onAddLocal(selectedRelease) },
                expandedTrackMatchKey = expandedTrackMatchKey,
                onChangeTrackMatch = { track ->
                    val key = track.albumMatchPickerKey()
                    expandedTrackMatchKey = if (expandedTrackMatchKey == key) null else key
                },
                onSelectTrackMatch = { track, source ->
                    val updatedRelease = selectedRelease.withSelectedAlbumTrackMatch(track, source, premiumSources)
                    releaseOverrides = releaseOverrides + (updatedRelease.id to updatedRelease)
                    results = (listOf(updatedRelease) + results.filterNot { it.id == updatedRelease.id }).distinctBy { it.id }
                    selectedReleaseId = updatedRelease.id
                    onSelectTrackMatch(
                        updatedRelease,
                        updatedRelease.tracks.firstOrNull { it.albumMatchPickerKey() == track.albumMatchPickerKey() } ?: track,
                        source,
                    )
                    expandedTrackMatchKey = null
                },
                onStartHighestQualityDownload = { onStartHighestQualityDownload(selectedRelease) },
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }
        AlbumBuilderLandingHeader(onInfo = { albumBuilderInfoOpen = true })
        AlbumBuilderSearchBar(
            artistQuery = artistQuery,
            albumQuery = albumQuery,
            onArtistQuery = { artistQuery = it },
            onAlbumQuery = { albumQuery = it },
            modifier = Modifier.padding(top = 16.dp),
        )
        AlbumBuilderSearchButton(
            label = if (searching) "Searching" else "Search",
            icon = if (searching) DeckIcon.Timer else DeckIcon.Disc,
            enabled = !searching,
            onClick = { search() },
            modifier = Modifier.padding(top = 11.dp),
        )
        AlbumBuilderManualTracklistLink(
            expanded = manualTracklistOpen,
            onClick = { manualTracklistOpen = !manualTracklistOpen },
            modifier = Modifier.padding(top = 12.dp),
        )
        AlbumBuilderProviderLine(Modifier.padding(top = 15.dp))
        if (manualTracklistOpen) {
            Spacer(Modifier.height(10.dp))
            AlbumDownloaderMultilineField(
                value = manualTracklistText,
                placeholder = "Paste numbered tracklist",
                modifier = Modifier.fillMaxWidth(),
                onValue = { manualTracklistText = it },
            )
            SleepDialogButton(
                "Create Album Project",
                Modifier.fillMaxWidth().padding(top = 10.dp),
                tone = StreamAmber.copy(alpha = 0.34f),
                onClick = { createManualProject() },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(top = 26.dp, bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!error.isNullOrBlank()) {
                item { AlbumDownloaderStatusCard(error.orEmpty()) }
            }
            if (results.isNotEmpty()) {
                item { AlbumDownloaderSectionHeader("Results", "${results.size} releases") }
                items(results, key = { it.id }) { release ->
                    AlbumDownloadReleaseCard(
                        release = release,
                        saved = release.id in savedIds,
                        onOpen = { selectedReleaseId = release.id },
                        onSave = { onSaveDraft(release) },
                        onRemove = null,
                    )
                }
            }
            if (recentProjects.isNotEmpty()) {
                item {
                    if (results.isEmpty()) {
                        AlbumBuilderRecentProjectsHeader()
                    } else {
                        AlbumDownloaderSectionHeader("Saved Projects", "${recentProjects.size} saved")
                    }
                }
                items(recentProjects, key = { "recent-${it.id}" }) { release ->
                    AlbumBuilderRecentProjectRow(
                        release = release,
                        onOpen = { selectedReleaseId = release.id },
                    )
                }
            }
        }
        if (albumBuilderInfoOpen) {
            BasicInfoModal(
                title = "Album Builder",
                subtitle = "PremiumDeck album projects",
                onDismiss = { albumBuilderInfoOpen = false },
            ) {
                Text(
                    "Search album metadata, match each track to a clean PremiumDeck source, then play, save offline, or add the project to your local library.",
                    color = StreamTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                SleepDialogButton("OK", Modifier.fillMaxWidth().padding(top = 16.dp), tone = StreamAccentRed.copy(alpha = 0.42f), onClick = { albumBuilderInfoOpen = false })
            }
        }
    }
}

internal fun preferredAlbumProjectRelease(
    current: AlbumDownloadRelease,
    candidate: AlbumDownloadRelease?,
): AlbumDownloadRelease {
    if (candidate == null || candidate.id != current.id) return current
    val currentWork = current.hasAlbumProjectMatchingWork()
    val candidateWork = candidate.hasAlbumProjectMatchingWork()
    return when {
        currentWork && !candidateWork -> current
        candidateWork && !currentWork -> candidate
        current.tracks.isEmpty() && candidate.tracks.isNotEmpty() -> candidate
        candidate.tracks.isEmpty() && current.tracks.isNotEmpty() -> current
        candidate.tracks.size > current.tracks.size -> candidate
        else -> current
    }
}

private fun AlbumDownloadRelease.hasAlbumProjectMatchingWork(): Boolean =
    tracks.any { track ->
        track.matchedSource != null ||
            track.matchCandidates.isNotEmpty() ||
            track.matchScore > 0 ||
            track.matchReason.isNotBlank() ||
            track.matchVerified
    }

@Composable
private fun AlbumBuilderLandingHeader(onInfo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Album Projects", color = StreamTextPrimary, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Find tracklists, match clean PremiumDeck sources, then play or save the release",
                color = StreamTextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        IconTap(DeckIcon.Info, onInfo, 34.dp)
    }
}

@Composable
private fun AlbumBuilderSearchBar(
    artistQuery: String,
    albumQuery: String,
    onArtistQuery: (String) -> Unit,
    onAlbumQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, Color.White.copy(alpha = 0.085f), shape)
            .padding(start = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Search, StreamTextSecondary.copy(alpha = 0.86f), Modifier.size(21.dp))
        Spacer(Modifier.width(10.dp))
        AlbumBuilderInlineField(
            value = artistQuery,
            placeholder = "Search Artist...",
            onValue = onArtistQuery,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .width(1.dp)
                .height(54.dp)
                .background(Color.White.copy(alpha = 0.075f)),
        )
        AlbumBuilderInlineField(
            value = albumQuery,
            placeholder = "+ Album Name (opt)",
            onValue = onAlbumQuery,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun AlbumBuilderInlineField(value: String, placeholder: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(placeholder, color = StreamTextMuted, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        },
    )
}

@Composable
private fun AlbumBuilderManualTracklistLink(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember(expanded) { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(StreamGlassBorder.copy(alpha = 0.55f)))
        Text(
            if (expanded) "Hide Manual Tracklist" else "Or enter a Manual Tracklist",
            color = StreamTextSecondary.copy(alpha = 0.78f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.weight(1f).height(1.dp).background(StreamGlassBorder.copy(alpha = 0.55f)))
    }
}

@Composable
private fun AlbumBuilderProviderLine(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PulseIcon(DeckIcon.StreamRadio, StreamAmber.copy(alpha = 0.78f), Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                append("Matches across ")
                withStyle(SpanStyle(color = StreamTextPrimary.copy(alpha = 0.78f), fontWeight = FontWeight.Bold)) {
                    append("Apple Music, Deezer, & MusicBrainz.")
                }
            },
            color = StreamTextSecondary.copy(alpha = 0.68f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumBuilderSearchButton(
    label: String,
    icon: DeckIcon,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember(label, enabled) { MutableInteractionSource() }
    val shape = RoundedCornerShape(21.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(62.dp)
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AlbumBuilderSoftBlueGlow(enabled = enabled, modifier = Modifier.matchParentSize())
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = if (enabled) 0.058f else 0.036f))
                .border(1.dp, Color.White.copy(alpha = if (enabled) 0.22f else 0.07f), shape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (enabled) 0.045f else 0.025f))
                    .border(1.dp, Color.White.copy(alpha = if (enabled) 0.15f else 0.055f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(icon, Color.White.copy(alpha = if (enabled) 0.92f else 0.42f), Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.46f),
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AlbumBuilderSoftBlueGlow(enabled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        val alpha = if (enabled) 1f else 0.36f
        val primaryCenter = Offset(size.width * 0.50f, size.height * 0.54f)
        val primaryRadius = size.maxDimension * 0.42f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    StreamAccentRed.copy(alpha = 0.23f * alpha),
                    StreamAccentRed.copy(alpha = 0.095f * alpha),
                    Color.Transparent,
                ),
                center = primaryCenter,
                radius = primaryRadius,
            ),
            center = primaryCenter,
            radius = primaryRadius,
        )
        val rightGlint = Offset(size.width * 0.82f, size.height * 0.34f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.105f * alpha),
                    StreamAccentRed.copy(alpha = 0.08f * alpha),
                    Color.Transparent,
                ),
                center = rightGlint,
                radius = size.maxDimension * 0.24f,
            ),
            center = rightGlint,
            radius = size.maxDimension * 0.24f,
        )
    }
}

@Composable
private fun AlbumBuilderRecentProjectsHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Recent Projects", color = StreamTextPrimary, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("View all", color = StreamAccentRed.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            PulseIcon(DeckIcon.Back, StreamAccentRed.copy(alpha = 0.82f), Modifier.size(15.dp).graphicsLayer(rotationZ = 180f))
        }
    }
}

@Composable
private fun AlbumBuilderRecentProjectRow(release: AlbumDownloadRelease, onOpen: () -> Unit) {
    val interactionSource = remember(release.id) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.047f),
                        Color.White.copy(alpha = 0.028f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.078f), RoundedCornerShape(16.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnailBox(release.coverUrl, Modifier.size(54.dp), DeckIcon.Disc)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(release.title, color = StreamTextPrimary, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(release.artist, color = StreamTextPrimary.copy(alpha = 0.76f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                release.date.take(4).takeIf { it.isNotBlank() }?.let { year ->
                    Text(year, color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.size(3.dp).clip(CircleShape).background(StreamAccentRed.copy(alpha = 0.72f)))
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    "${release.tracks.size.takeIf { it > 0 } ?: release.trackCount} tracks",
                    color = StreamTextSecondary,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        PulseIcon(DeckIcon.More, StreamTextSecondary.copy(alpha = 0.78f), Modifier.size(19.dp))
        Spacer(Modifier.width(16.dp))
        PulseIcon(DeckIcon.Back, Color.White.copy(alpha = 0.78f), Modifier.size(20.dp).graphicsLayer(rotationZ = 180f))
    }
}

@Composable
private fun AlbumDownloaderField(value: String, placeholder: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold),
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StreamGlassFill)
            .border(1.dp, StreamGlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(placeholder, color = StreamTextMuted, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        },
    )
}

@Composable
private fun AlbumDownloaderMultilineField(value: String, placeholder: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
        modifier = modifier
            .heightIn(min = 116.dp, max = 168.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StreamGlassFill)
            .border(1.dp, StreamGlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                if (value.isBlank()) {
                    Text(placeholder, color = StreamTextMuted, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        },
    )
}

@Composable
private fun AlbumDownloaderSectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, color = StreamTextPrimary, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(subtitle, color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumDownloaderStatusCard(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StreamGlassFill)
            .border(1.dp, StreamGlassBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = StreamTextSecondary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AlbumDownloadReleaseCard(
    release: AlbumDownloadRelease,
    saved: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(StreamElevated, StreamPanel)))
            .border(1.dp, StreamGlassBorder, RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YouTubeThumbnailBox(release.coverUrl, Modifier.size(68.dp), DeckIcon.Disc)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(release.title, color = StreamTextPrimary, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(release.artist, color = StreamTextPrimary.copy(alpha = 0.78f), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(release.albumDownloaderMetaLine(), color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (release.tracks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            release.tracks.take(6).forEach { track ->
                Text("${track.position}. ${track.title}", color = StreamTextPrimary.copy(alpha = 0.76f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (release.tracks.size > 6) {
                Text("+${release.tracks.size - 6} more tracks", color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepDialogButton(if (saved) "Saved" else "Save Project", Modifier.weight(1f), tone = if (saved) StreamGlassFill else StreamAccentRed.copy(alpha = 0.42f), onClick = onSave)
            SleepDialogButton("Tracklist", Modifier.weight(1f), tone = StreamGlassFill, onClick = onOpen)
            if (onRemove != null) {
                SleepDialogButton("Remove", Modifier.weight(1f), tone = Color(0xFFB8322A).copy(alpha = 0.46f), onClick = onRemove)
            }
        }
    }
}

@Composable
private fun AlbumDetailTopBar(title: String, subtitle: String, onBack: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    PageNavigationHeader(title = title, subtitle = subtitle, onBack = onBack, modifier = modifier) {
        IconTap(DeckIcon.More, onMore, 42.dp)
    }
}

@Composable
private fun AlbumDetailHeader(release: AlbumDownloadRelease) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        YouTubeThumbnailBox(release.coverUrl, Modifier.size(118.dp), DeckIcon.Disc)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                release.title,
                color = StreamTextPrimary,
                fontSize = 26.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                release.artist,
                color = StreamAccentRed,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
            Text(
                release.albumDownloaderMetaLine(),
                color = StreamTextSecondary.copy(alpha = 0.92f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

@Composable
private fun AlbumPrimaryActionButton(label: String, icon: DeckIcon, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(62.dp)
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AlbumBuilderSoftBlueGlow(enabled = enabled, modifier = Modifier.matchParentSize())
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = if (enabled) 0.058f else 0.036f))
                .border(1.dp, Color.White.copy(alpha = if (enabled) 0.22f else 0.07f), shape)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseIcon(icon, Color.White.copy(alpha = if (enabled) 0.98f else 0.46f), Modifier.size(21.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.46f), fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumActionChip(
    label: String,
    icon: DeckIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    val alpha = if (enabled) 1f else 0.46f
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(if (danger) Color(0xFF451B1B).copy(alpha = 0.58f) else StreamGlassFill)
            .border(1.dp, if (danger) Color(0xFFFF6B66).copy(alpha = 0.28f) else StreamGlassBorder, shape)
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (danger) 0.18f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(icon, tint.copy(alpha = alpha), Modifier.size(13.dp))
        }
        Spacer(Modifier.width(7.dp))
        Text(label, color = (if (danger) Color(0xFFFF8A86) else Color.White).copy(alpha = alpha), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumStatusSummaryCard(
    verifiedCount: Int,
    matchedTrackCount: Int,
    totalTracks: Int,
    repairCount: Int,
    backupCount: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(StreamGlassFill)
            .border(1.dp, StreamGlassBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumStatusSummaryItem(DeckIcon.Check, Green, "$verifiedCount Verified", "$matchedTrackCount matched", Modifier.weight(1f))
        AlbumStatusDivider()
        AlbumStatusSummaryItem(DeckIcon.MusicList, Color(0xFFFFB24A), "$totalTracks Tracks", "$repairCount need repair", Modifier.weight(1f))
        AlbumStatusDivider()
        AlbumStatusSummaryItem(
            DeckIcon.StreamOffline,
            Blue,
            "$backupCount Backups",
            "alternates saved",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlbumStatusDivider() {
    Box(Modifier.width(1.dp).height(38.dp).background(StreamGlassBorder))
}

@Composable
private fun AlbumStatusSummaryItem(icon: DeckIcon, tint: Color, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PulseIcon(icon, tint, Modifier.size(16.dp))
        Text(title, color = StreamTextPrimary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, color = StreamTextSecondary, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun AlbumDownloadReleaseDetail(
    release: AlbumDownloadRelease,
    downloadJob: AlbumAudioDownloadJob?,
    saved: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onRemove: (() -> Unit)?,
    taskState: AlbumProjectTaskState?,
    premiumSources: List<YouTubeSource>,
    onMatchTracks: () -> Unit,
    onPlayAlbum: () -> Unit,
    onPlayTrack: (AlbumDownloadTrack) -> Unit,
    onQueueOffline: () -> Unit,
    onAddLocal: () -> Unit,
    expandedTrackMatchKey: String?,
    onChangeTrackMatch: (AlbumDownloadTrack) -> Unit,
    onSelectTrackMatch: (AlbumDownloadTrack, YouTubeSource) -> Unit,
    onStartHighestQualityDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadableCount = release.tracks.count { it.downloadAllowed && it.downloadUrl.isNotBlank() }
    val sourceLookup = remember(premiumSources) { albumSourceLookup(premiumSources) }
    val orderedTracks = remember(release) { release.tracks.sortedBy { it.position } }
    val trackRows = remember(orderedTracks, sourceLookup) {
        orderedTracks.map { track ->
            AlbumTrackMatchRowState(
                track = track,
                liveSource = track.currentMatchedSource(sourceLookup),
                candidates = track.matchCandidateSources(sourceLookup),
            )
        }
    }
    val progressSummary = remember(release, sourceLookup) { release.albumProjectProgressSummary(sourceLookup) }
    val matchedCount = progressSummary.matched
    val repairCount = remember(release, sourceLookup) { release.albumTracksNeedingRepair(sourceLookup).size }
    val totalTracks = remember(release) { release.tracks.size.takeIf { it > 0 } ?: release.trackCount }
    val matchedTrackCount = remember(trackRows) { trackRows.count { it.liveSource != null } }
    val verifiedCount = remember(trackRows) { trackRows.count { it.liveSource != null && it.track.matchVerified } }
    val backupCount = remember(trackRows) { trackRows.sumOf { (it.candidates.size - 1).coerceAtLeast(0) } }
    val tracklistLoading = taskState?.phase == AlbumProjectTaskPhase.TracklistLoading
    val matching = taskState?.phase == AlbumProjectTaskPhase.Matching
    val offlineSaving = taskState?.phase == AlbumProjectTaskPhase.OfflineSaving
    LazyColumn(
        contentPadding = PaddingValues(top = 18.dp, bottom = 184.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        item {
            AlbumDetailHeader(release)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val matchLabel = when {
                    tracklistLoading -> "Loading Tracklist"
                    matching -> "Matching"
                    matchedCount > 0 && repairCount > 0 -> "Repair Weak"
                    matchedCount > 0 -> "Recheck"
                    else -> "Match Tracks"
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlbumActionChip(
                        label = if (offlineSaving) "Saving" else "Save Offline",
                        icon = DeckIcon.StreamOffline,
                        tint = StreamAccentRed,
                        enabled = matchedCount > 0 && !offlineSaving,
                        onClick = onQueueOffline,
                        modifier = Modifier.weight(1f),
                    )
                    AlbumActionChip(
                        label = "Add to Local",
                        icon = DeckIcon.Folder,
                        tint = StreamAmber,
                        enabled = matchedCount > 0,
                        onClick = onAddLocal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlbumActionChip(
                        label = matchLabel,
                        icon = DeckIcon.Lab,
                        tint = StreamAccentRed,
                        enabled = !matching && !tracklistLoading && orderedTracks.isNotEmpty(),
                        onClick = onMatchTracks,
                        modifier = Modifier.weight(1f),
                    )
                    AlbumActionChip(
                        label = "Highest Quality",
                        icon = DeckIcon.Equalizer,
                        tint = StreamAmber,
                        enabled = downloadableCount > 0,
                        onClick = onStartHighestQualityDownload,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlbumActionChip(
                        label = "Results",
                        icon = DeckIcon.Visualizer,
                        tint = StreamTextSecondary,
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                    )
                    AlbumActionChip(
                        label = if (saved) "Saved" else "Save Project",
                        icon = DeckIcon.Bookmark,
                        tint = if (saved) StreamTextPrimary else StreamAccentRed,
                        enabled = !saved,
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onRemove != null) {
                    AlbumActionChip(
                        label = "Remove Project",
                        icon = DeckIcon.Trash,
                        tint = Color(0xFFFF6B66),
                        danger = true,
                        onClick = onRemove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            AlbumPrimaryActionButton(
                label = "Play Album",
                icon = DeckIcon.Play,
                enabled = matchedCount > 0,
                onClick = onPlayAlbum,
            )
        }
        if (taskState != null) {
            item {
                AlbumProjectTaskProgressCard(taskState, progressSummary)
            }
        }
        item {
            AlbumStatusSummaryCard(
                verifiedCount = verifiedCount,
                matchedTrackCount = matchedTrackCount,
                totalTracks = totalTracks,
                repairCount = repairCount,
                backupCount = backupCount,
            )
        }
        if (downloadableCount > 0 && taskState == null) {
            item {
                AlbumDownloaderStatusCard(
                    "${release.source.ifBlank { "Free catalog" }}  |  ${release.downloadQuality.ifBlank { "Best available" }}  |  $downloadableCount legal downloads",
                )
            }
        }
        if (downloadJob != null) {
            item {
                AlbumDownloaderStatusCard(downloadJob.albumAudioDownloadStatusText())
            }
        }
        item { AlbumDownloaderSectionHeader("Tracklist", "${orderedTracks.size.takeIf { it > 0 } ?: release.trackCount} tracks") }
        if (orderedTracks.isEmpty() && tracklistLoading) {
            item { AlbumDownloaderStatusCard(taskState?.message?.takeIf { it.isNotBlank() } ?: "Loading verified tracklist...") }
        } else if (orderedTracks.isEmpty()) {
            item { AlbumDownloaderStatusCard("Tracklist unavailable for this release") }
        } else {
            items(trackRows, key = { it.track.albumMatchPickerKey() }) { row ->
                val track = row.track
                AlbumDownloadTrackRow(
                    track = track,
                    liveSource = row.liveSource,
                    candidates = row.candidates,
                    expanded = expandedTrackMatchKey == track.albumMatchPickerKey(),
                    matching = matching && taskState?.activePosition == track.position,
                    offlineSaving = offlineSaving && taskState?.activePosition == track.position,
                    onPlay = { onPlayTrack(track) },
                    onChangeMatch = { onChangeTrackMatch(track) },
                    onSelectMatch = { source -> onSelectTrackMatch(track, source) },
                )
            }
        }
    }
}

private data class AlbumTrackMatchRowState(
    val track: AlbumDownloadTrack,
    val liveSource: YouTubeSource?,
    val candidates: List<YouTubeSource>,
)

private fun AlbumAudioDownloadJob.albumAudioDownloadStatusText(): String {
    val prefix = when (status) {
        AlbumAudioDownloadStatus.NeedsProvider -> "Provider setup needed"
        AlbumAudioDownloadStatus.Queued -> "Free legal download queued"
        AlbumAudioDownloadStatus.Downloading -> "Saving locally $progress%"
        AlbumAudioDownloadStatus.Downloaded -> "Downloaded"
        AlbumAudioDownloadStatus.Failed -> "Download failed"
    }
    return listOf(prefix, provider.takeIf { it.isNotBlank() }, quality.takeIf { it.isNotBlank() }, message.takeIf { it.isNotBlank() })
        .joinToString("  |  ")
}

@Composable
private fun AlbumProjectTaskProgressCard(taskState: AlbumProjectTaskState, summary: AlbumProjectProgressSummary) {
    val progress = when (taskState.phase) {
        AlbumProjectTaskPhase.TracklistLoading -> if (taskState.total > 0) ((taskState.completed.toFloat() / taskState.total.toFloat()) * 100f).roundToInt() else 8
        AlbumProjectTaskPhase.Matching -> if (taskState.total > 0) ((taskState.completed.toFloat() / taskState.total.toFloat()) * 100f).roundToInt() else 0
        AlbumProjectTaskPhase.OfflineSaving -> summary.progress.takeIf { it > 0 }
            ?: if (taskState.total > 0) ((taskState.completed.toFloat() / taskState.total.toFloat()) * 100f).roundToInt() else 0
    }.coerceIn(1, 100)
    val label = when (taskState.phase) {
        AlbumProjectTaskPhase.TracklistLoading -> "Loading tracklist"
        AlbumProjectTaskPhase.Matching -> "Matching tracks"
        AlbumProjectTaskPhase.OfflineSaving -> "Saving album offline"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StreamGlassFill)
            .border(1.dp, StreamGlassBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(label, color = StreamTextPrimary, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when (taskState.phase) {
                AlbumProjectTaskPhase.TracklistLoading -> taskState.message.ifBlank { "Preparing album tracklist" }
                AlbumProjectTaskPhase.Matching -> "${taskState.completed.coerceIn(0, taskState.total)}/${taskState.total} checked  |  ${taskState.message}"
                AlbumProjectTaskPhase.OfflineSaving -> "${summary.saved}/${summary.matched} saved  |  ${summary.saving} saving  |  ${summary.failed} failed"
            },
            color = StreamTextSecondary,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        OfflineDownloadProgress(progress, Modifier.padding(top = 10.dp), label = label)
    }
}

@Composable
private fun AlbumDownloadTrackRow(
    track: AlbumDownloadTrack,
    liveSource: YouTubeSource?,
    candidates: List<YouTubeSource>,
    expanded: Boolean,
    matching: Boolean,
    offlineSaving: Boolean,
    onPlay: () -> Unit,
    onChangeMatch: () -> Unit,
    onSelectMatch: (YouTubeSource) -> Unit,
) {
    val interactionSource = remember("${track.position}-${track.title}") { MutableInteractionSource() }
    val playable = liveSource != null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (playable) StreamGlassFill else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (playable) StreamGlassBorder else Color.White.copy(alpha = 0.045f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(enabled = playable, interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(track.position.toString().padStart(2, '0'), color = StreamTextMuted, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = StreamTextPrimary.copy(alpha = if (playable) 0.94f else 0.64f), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                AlbumTrackStatusLine(track, liveSource, matching)
            }
            val duration = track.albumDownloaderDurationText()
            if (duration.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(duration, color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (candidates.size > 1) {
                Spacer(Modifier.width(8.dp))
                AlbumTrackChangeButton(enabled = true, onClick = onChangeMatch)
            }
            Spacer(Modifier.width(8.dp))
            AlbumTrackPlayButton(enabled = playable, onPlay = onPlay)
        }
        when {
            liveSource?.downloadState == YouTubeDownloadState.Downloading -> {
                OfflineDownloadProgress(liveSource.downloadProgress, Modifier.padding(top = 9.dp), label = "Saving offline")
            }
            offlineSaving && liveSource != null && !liveSource.isOfflineSaved() && liveSource.downloadState != YouTubeDownloadState.Failed -> {
                OfflineDownloadProgress(liveSource.downloadProgress.coerceAtLeast(1), Modifier.padding(top = 9.dp), label = "Queued offline")
            }
        }
        if (expanded) {
            if (candidates.size <= 1) {
                AlbumDownloaderStatusCard("No backup matches are available for this track yet.", Modifier.padding(top = 10.dp))
            } else {
                AlbumTrackInlineMatchOptions(
                    track = track,
                    candidates = candidates,
                    currentSource = liveSource,
                    onSelect = onSelectMatch,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumTrackInlineMatchOptions(
    track: AlbumDownloadTrack,
    candidates: List<YouTubeSource>,
    currentSource: YouTubeSource?,
    onSelect: (YouTubeSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentKey = currentSource?.streamDistinctKey().orEmpty()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.14f))
            .border(1.dp, StreamGlassBorder.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        candidates.forEach { source ->
            AlbumTrackInlineMatchOption(
                track = track,
                source = source,
                current = source.streamDistinctKey() == currentKey,
                onSelect = { onSelect(source) },
            )
        }
    }
}

@Composable
private fun AlbumTrackInlineMatchOption(
    track: AlbumDownloadTrack,
    source: YouTubeSource,
    current: Boolean,
    onSelect: () -> Unit,
) {
    val interaction = remember(source.streamDistinctKey(), current) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (current) StreamAccentRed.copy(alpha = 0.12f) else StreamGlassFill.copy(alpha = 0.74f))
            .border(1.dp, if (current) StreamAccentRed.copy(alpha = 0.24f) else StreamGlassBorder.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeThumbnailBox(source.bestThumbnailUrl(), Modifier.size(44.dp), fallbackIcon = DeckIcon.StreamSource)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, color = StreamTextPrimary, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(source.author, source.channelTitle.takeIf { it.isNotBlank() && it != source.author })
                    .filterNotNull()
                    .distinct()
                    .joinToString("  |  ")
                    .ifBlank { "PremiumDeck source" },
                color = StreamTextSecondary,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                albumInlineCandidateMetaLine(track, source, current),
                color = if (current) StreamAccentRed else StreamTextSecondary.copy(alpha = 0.72f),
                fontSize = 8.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (current) StreamAccentRed.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(if (current) DeckIcon.Check else DeckIcon.Next, Color.White.copy(alpha = 0.88f), Modifier.size(14.dp))
        }
    }
}

private fun AlbumDownloadTrack.albumMatchPickerKey(): String =
    "$position|$recordingId|$title"

private fun albumInlineCandidateMetaLine(track: AlbumDownloadTrack, source: YouTubeSource, current: Boolean): String =
    buildList {
        if (current) add("Current") else add("Backup")
        if (source.durationMillis > 0L) add(formatDuration(source.durationMillis))
        add(if (track.matchVerified && current && track.matchScore >= 70) "Verified" else if (current && track.matchScore in 1..69) "Weak" else "Candidate")
        if (source.isOfflineSaved()) add("Saved")
        if (source.looksLikeLiveAlbumCandidate()) add("Live")
        if (source.status == YouTubeSourceStatus.ResolverNeeded) add("Needs resolver")
    }.joinToString("  |  ")

private fun YouTubeSource.looksLikeLiveAlbumCandidate(): Boolean {
    val text = "$title $author $channelTitle".lowercase(Locale.US)
    return listOf(" live", "(live", "live at", "live in", "concert", "session").any { text.contains(it) }
}

@Composable
private fun AlbumTrackPlayButton(enabled: Boolean, onPlay: () -> Unit) {
    val interactionSource = remember(enabled) { MutableInteractionSource() }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) StreamAccentRed.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, (if (enabled) StreamAccentRed else Color.White).copy(alpha = if (enabled) 0.26f else 0.06f), CircleShape)
            .pressScaleEffect(interactionSource)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(DeckIcon.Play, Color.White.copy(alpha = if (enabled) 0.94f else 0.34f), Modifier.size(15.dp))
    }
}

@Composable
private fun AlbumTrackStatusLine(track: AlbumDownloadTrack, liveSource: YouTubeSource?, matching: Boolean) {
    if (matching && liveSource == null) {
        Text("Matching source...", color = StreamAmber, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        return
    }
    if (liveSource == null) {
        Text("Match required", color = StreamAmber, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        return
    }
    val verified = track.matchVerified && track.matchScore >= 70
    val statusColor = if (verified) StreamAccentRed else StreamAmber
    val statusLabel = if (verified) "Verified" else "Weak"
    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(statusLabel, color = statusColor, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        if (track.matchScore > 0) {
            Text(" · ${track.matchScore}%", color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (track.matchCandidates.size > 1) {
            Text(" · ${track.matchCandidates.size} backups", color = StreamAccentRed, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        when {
            liveSource.downloadState == YouTubeDownloadState.Downloading -> Text(" · saving", color = StreamAmber, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
            liveSource.downloadState == YouTubeDownloadState.Failed -> Text(" · retry needed", color = Color(0xFFFF7B72), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
            liveSource.isOfflineSaved() -> Text(" · local", color = StreamAccentRed, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

private fun AlbumDownloadTrack.albumDownloaderDurationText(): String {
    if (durationMillis <= 0L) return ""
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

internal fun AlbumDownloadRelease.albumDownloaderMetaLine(): String =
    buildList {
        source.takeIf { it.isNotBlank() }?.let(::add)
        downloadQuality.takeIf { it.isNotBlank() }?.let(::add)
        date.takeIf { it.isNotBlank() }?.let(::add)
        country.takeIf { it.isNotBlank() }?.let(::add)
        format.takeIf { it.isNotBlank() }?.let(::add)
        label.takeIf { it.isNotBlank() }?.let(::add)
        license.takeIf { it.isNotBlank() }?.let(::add)
        val count = tracks.size.takeIf { it > 0 } ?: trackCount.takeIf { it > 0 }
        count?.let { add("$it tracks") }
    }.joinToString("  |  ").ifBlank { "Album metadata" }

