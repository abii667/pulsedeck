package com.pulsedeck.windows

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser

private val PulseColors: ColorScheme = darkColorScheme(
    background = Color(0xFF080A0D),
    surface = Color(0xFF11151B),
    surfaceVariant = Color(0xFF171D24),
    primary = Color(0xFF62E6C2),
    secondary = Color(0xFFFFC857),
    tertiary = Color(0xFFFF6B5E),
    onBackground = Color(0xFFF4F7F8),
    onSurface = Color(0xFFF4F7F8),
    onPrimary = Color(0xFF04201A),
)

private val AudioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma")

private data class DesktopTrack(
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val extension: String,
    val sizeLabel: String,
    val file: File,
)

private enum class Section(val label: String) {
    Library("Library"),
    Queue("Queue"),
    Audio("Audio"),
    Settings("Settings"),
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PulseDeck",
        state = WindowState(size = DpSize(1180.dp, 760.dp)),
    ) {
        MaterialTheme(colorScheme = PulseColors) {
            PulseDeckWindowsApp()
        }
    }
}

@Composable
private fun PulseDeckWindowsApp() {
    var section by remember { mutableStateOf(Section.Library) }
    var selectedFolder by remember { mutableStateOf<File?>(null) }
    var tracks by remember { mutableStateOf<List<DesktopTrack>>(emptyList()) }
    var selectedTrack by remember { mutableStateOf<DesktopTrack?>(null) }
    var query by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("Choose a music folder to start.") }
    var volume by remember { mutableStateOf(0.72f) }
    val scope = rememberCoroutineScope()

    fun chooseAndScan() {
        val folder = chooseMusicFolder() ?: return
        selectedFolder = folder
        isScanning = true
        scanMessage = "Scanning ${folder.absolutePath}"
        scope.launch {
            val found = withContext(Dispatchers.IO) { scanAudioFiles(folder) }
            tracks = found
            selectedTrack = found.firstOrNull()
            isScanning = false
            scanMessage = if (found.isEmpty()) "No supported audio files found." else "${found.size} tracks ready."
        }
    }

    val visibleTracks = remember(tracks, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) {
            tracks
        } else {
            tracks.filter { track ->
                track.title.lowercase().contains(needle) ||
                    track.artist.lowercase().contains(needle) ||
                    track.album.lowercase().contains(needle) ||
                    track.path.lowercase().contains(needle)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF080A0D), Color(0xFF10151B), Color(0xFF07080B)),
                ),
            )
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Sidebar(
                section = section,
                onSectionChange = { section = it },
                onChooseFolder = ::chooseAndScan,
                trackCount = tracks.size,
            )

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Header(
                    folder = selectedFolder,
                    message = scanMessage,
                    isScanning = isScanning,
                    onChooseFolder = ::chooseAndScan,
                )

                when (section) {
                    Section.Library -> LibrarySurface(
                        query = query,
                        onQueryChange = { query = it },
                        tracks = visibleTracks,
                        selectedTrack = selectedTrack,
                        onTrackSelected = { selectedTrack = it },
                        onOpenTrack = ::openWithDefaultPlayer,
                    )

                    Section.Queue -> QueueSurface(
                        tracks = tracks,
                        selectedTrack = selectedTrack,
                        onTrackSelected = { selectedTrack = it },
                    )

                    Section.Audio -> AudioSurface(volume = volume, onVolumeChange = { volume = it })
                    Section.Settings -> SettingsSurface(selectedFolder = selectedFolder)
                }
            }

            NowPlayingSurface(
                track = selectedTrack,
                volume = volume,
                onVolumeChange = { volume = it },
                onOpenTrack = ::openWithDefaultPlayer,
            )
        }
    }
}

@Composable
private fun Sidebar(
    section: Section,
    onSectionChange: (Section) -> Unit,
    onChooseFolder: () -> Unit,
    trackCount: Int,
) {
    Surface(
        modifier = Modifier.width(220.dp).fillMaxHeight(),
        color = Color.White.copy(alpha = 0.055f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("PulseDeck", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Windows", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            Section.entries.forEach { item ->
                val active = item == section
                TextButton(
                    onClick = { onSectionChange(item) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f),
                    ),
                ) {
                    Text(
                        item.label,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            StatPill(label = "Tracks", value = trackCount.toString())
            Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                Text("Choose folder")
            }
        }
    }
}

@Composable
private fun Header(
    folder: File?,
    message: String,
    isScanning: Boolean,
    onChooseFolder: () -> Unit,
) {
    Surface(
        color = Color.White.copy(alpha = 0.055f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Local music library", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        folder?.absolutePath ?: "No folder selected",
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = onChooseFolder) {
                    Text("Change folder")
                }
            }

            Text(message, color = Color.White.copy(alpha = 0.7f))
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LibrarySurface(
    query: String,
    onQueryChange: (String) -> Unit,
    tracks: List<DesktopTrack>,
    selectedTrack: DesktopTrack?,
    onTrackSelected: (DesktopTrack) -> Unit,
    onOpenTrack: (DesktopTrack) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search") },
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(tracks, key = { it.path }) { track ->
                    TrackRow(
                        track = track,
                        selected = track.path == selectedTrack?.path,
                        onSelect = { onTrackSelected(track) },
                        onOpenTrack = { onOpenTrack(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: DesktopTrack,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenTrack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.045f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.07f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(track.extension.uppercase().take(3), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${track.artist} / ${track.album}",
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(track.sizeLabel, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp)
            OutlinedButton(onClick = onOpenTrack) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun QueueSurface(
    tracks: List<DesktopTrack>,
    selectedTrack: DesktopTrack?,
    onTrackSelected: (DesktopTrack) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Queue", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tracks.take(80), key = { it.path }) { track ->
                    TrackRow(
                        track = track,
                        selected = track.path == selectedTrack?.path,
                        onSelect = { onTrackSelected(track) },
                        onOpenTrack = { openWithDefaultPlayer(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSurface(volume: Float, onVolumeChange: (Float) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Audio console", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            MeterRow("Gain", 0.62f, MaterialTheme.colorScheme.primary)
            MeterRow("Stereo width", 0.48f, MaterialTheme.colorScheme.secondary)
            MeterRow("Output headroom", 0.78f, MaterialTheme.colorScheme.tertiary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Desktop volume preview", fontWeight = FontWeight.SemiBold)
                Slider(value = volume, onValueChange = onVolumeChange)
            }
        }
    }
}

@Composable
private fun SettingsSurface(selectedFolder: File?) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Desktop settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            SettingRow("Library root", selectedFolder?.absolutePath ?: "Not selected")
            SettingRow("File scanner", AudioExtensions.sorted().joinToString(", "))
            SettingRow("Packaging", "MSI and EXE")
        }
    }
}

@Composable
private fun NowPlayingSurface(
    track: DesktopTrack?,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onOpenTrack: (DesktopTrack) -> Unit,
) {
    Surface(
        modifier = Modifier.width(320.dp).fillMaxHeight(),
        color = Color.White.copy(alpha = 0.055f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Now selected", color = Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF6B5E), Color(0xFF62E6C2), Color(0xFFFFC857)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(track?.extension?.uppercase()?.take(4) ?: "PD", color = Color.Black, fontSize = 42.sp, fontWeight = FontWeight.Black)
            }

            Text(
                track?.title ?: "No track selected",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(track?.artist ?: "Choose from Library", color = Color.White.copy(alpha = 0.64f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.album ?: "", color = Color.White.copy(alpha = 0.52f), maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (track != null) {
                Button(onClick = { onOpenTrack(track) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open track")
                }
            }

            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Volume", fontWeight = FontWeight.SemiBold)
                Slider(value = volume, onValueChange = onVolumeChange)
            }
        }
    }
}

@Composable
private fun MeterRow(label: String, value: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", color = Color.White.copy(alpha = 0.62f))
        }
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f),
        )
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.65f))
            Text(value, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Surface(
        color = Color.White.copy(alpha = 0.045f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, modifier = Modifier.width(140.dp), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
            Text(value, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun chooseMusicFolder(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Choose music folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun scanAudioFiles(folder: File): List<DesktopTrack> {
    if (!folder.exists() || !folder.isDirectory) return emptyList()
    val files = mutableListOf<File>()
    Files.walk(folder.toPath()).use { paths ->
        paths
            .filter { path -> Files.isRegularFile(path) }
            .forEach { path ->
                val file = path.toFile()
                val extension = file.extension.lowercase()
                if (extension in AudioExtensions && files.size < 1_000) {
                    files += file
                }
            }
    }
    return files
        .sortedWith(compareBy<File> { it.parentFile?.name?.lowercase() ?: "" }.thenBy { it.name.lowercase() })
        .map(::trackFromFile)
}

private fun trackFromFile(file: File): DesktopTrack {
    val name = file.nameWithoutExtension.replace('_', ' ').trim()
    val parts = name.split(" - ", limit = 2)
    val artist = if (parts.size == 2) parts[0].ifBlank { "Local file" } else "Local file"
    val title = if (parts.size == 2) parts[1].ifBlank { name } else name.ifBlank { file.name }
    return DesktopTrack(
        title = title,
        artist = artist,
        album = file.parentFile?.name ?: "Folder",
        path = file.absolutePath,
        extension = file.extension.lowercase(),
        sizeLabel = formatFileSize(file.length()),
        file = file,
    )
}

private fun formatFileSize(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) {
        "%.1f MB".format(megabytes)
    } else {
        "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    }
}

private fun openWithDefaultPlayer(track: DesktopTrack) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(track.file)
        }
    }
}
