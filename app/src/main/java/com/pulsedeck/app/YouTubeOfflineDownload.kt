package com.pulsedeck.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import kotlin.math.max

internal data class YouTubeDownloadJob(
    val id: String,
    val status: String,
    val progress: Int,
    val title: String = "",
    val artist: String = "",
    val filename: String = "",
    val mimeType: String = "audio/mp4",
    val durationMillis: Long = 0L,
    val error: String? = null,
    val chapters: List<YouTubeChapter> = emptyList(),
    val sponsorSegments: List<SponsorBlockSegment> = emptyList(),
)

internal data class YouTubeLocalDownloadResult(
    val uri: Uri,
    val title: String,
    val artist: String,
    val mimeType: String,
    val durationMillis: Long,
    val chapters: List<YouTubeChapter> = emptyList(),
    val sponsorSegments: List<SponsorBlockSegment> = emptyList(),
)

internal suspend fun runYouTubeOfflineDownload(
    source: YouTubeSource,
    currentProgress: () -> Int,
    updateSource: suspend (persist: Boolean, transform: (YouTubeSource) -> YouTubeSource) -> Unit,
    downloadOnDevice: suspend (YouTubeSource, suspend (Int) -> Unit) -> YouTubeLocalDownloadResult?,
    startResolverDownload: suspend (YouTubeSource) -> YouTubeDownloadJob?,
    getResolverDownloadStatus: suspend (String) -> YouTubeDownloadJob?,
    saveResolverDownload: suspend (YouTubeDownloadJob, YouTubeSource) -> Uri?,
    notify: suspend (String, Boolean) -> Unit,
    successMessage: String = "Saved offline to Music/PulseDeck/Stream Library",
) {
    suspend fun publishProgress(progress: Int, persist: Boolean = false) {
        updateSource(persist) {
            it.copy(
                downloadState = YouTubeDownloadState.Downloading,
                downloadProgress = progress.coerceIn(1, 99),
            )
        }
    }

    suspend fun fail(message: String) {
        updateSource(true) {
            it.copy(
                downloadState = YouTubeDownloadState.Failed,
                downloadProgress = 0,
                status = if (it.status == YouTubeSourceStatus.Downloaded && it.downloadedUri.isNullOrBlank()) {
                    YouTubeSourceStatus.StreamReady
                } else {
                    it.status
                },
            )
        }
        notify(message, true)
    }

    val localDownload = downloadOnDevice(source) { progress ->
        publishProgress(progress)
    }
    if (localDownload != null) {
        updateSource(true) {
            it.copy(
                title = localDownload.title.ifBlank { it.title },
                author = localDownload.artist.ifBlank { it.author },
                downloadedUri = localDownload.uri.toString(),
                downloadState = YouTubeDownloadState.Downloaded,
                status = YouTubeSourceStatus.Downloaded,
                downloadProgress = 100,
                durationMillis = localDownload.durationMillis.takeIf { duration -> duration > 0L } ?: it.durationMillis,
                isPodcast = localDownload.durationMillis > 10 * 60_000L || it.isPodcast,
                chapters = localDownload.chapters.ifEmpty { it.chapters },
                sponsorSegments = localDownload.sponsorSegments.ifEmpty { it.sponsorSegments },
            )
        }
        notify(successMessage, false)
        return
    }

    var resolverProgress = max(7, currentProgress().coerceAtMost(94))
    publishProgress(resolverProgress)
    val startJob = startResolverDownload(source)
    if (startJob == null) {
        fail("Offline save could not start. Start the local resolver, disable trim, or retry later.")
        return
    }

    var missedPolls = 0
    var current: YouTubeDownloadJob = startJob
    while (current.status !in listOf("done", "failed")) {
        resolverProgress = max(resolverProgress, current.progress.coerceIn(1, 99))
        publishProgress(resolverProgress)
        delay(900L)
        val next = getResolverDownloadStatus(startJob.id)
        if (next == null) {
            missedPolls += 1
            if (missedPolls >= 3) {
                fail("Offline save lost contact with the resolver. Check that it is still running, then retry.")
                return
            }
        } else {
            missedPolls = 0
            current = next
        }
    }

    if (current.status == "done") {
        publishProgress(96)
        val uri = saveResolverDownload(current, source)
        if (uri != null) {
            updateSource(true) {
                it.copy(
                    downloadedUri = uri.toString(),
                    downloadState = YouTubeDownloadState.Downloaded,
                    status = YouTubeSourceStatus.Downloaded,
                    downloadProgress = 100,
                    durationMillis = current.durationMillis.takeIf { duration -> duration > 0L } ?: it.durationMillis,
                    chapters = current.chapters.ifEmpty { it.chapters },
                    sponsorSegments = current.sponsorSegments.ifEmpty { it.sponsorSegments },
                )
            }
            notify(successMessage, false)
        } else {
            fail("Offline save finished, but Android could not store it in Music. Check storage access and retry.")
        }
    } else {
        fail(current.error ?: "Offline save failed. Retry from the stream menu.")
    }
}

internal fun YouTubeSource.offlineSaveActionLabel(): String =
    when (downloadState) {
        YouTubeDownloadState.Downloaded -> "Saved"
        YouTubeDownloadState.Downloading -> "Saving ${downloadProgress.coerceIn(1, 99)}%"
        YouTubeDownloadState.Failed -> "Retry Offline"
        YouTubeDownloadState.None, YouTubeDownloadState.Prompted -> "Offline"
    }

internal fun YouTubeSource.isOfflineSaved(): Boolean =
    downloadState == YouTubeDownloadState.Downloaded ||
        status == YouTubeSourceStatus.Downloaded ||
        !downloadedUri.isNullOrBlank()

internal fun YouTubeSource.offlineSaveDialogActionLabel(): String =
    when {
        isOfflineSaved() -> "Saved Offline"
        downloadState == YouTubeDownloadState.Downloading -> "Saving ${downloadProgress.coerceIn(1, 99)}%"
        downloadState == YouTubeDownloadState.Failed -> "Retry Offline Save"
        else -> "Save Offline"
    }

internal fun YouTubeSource.offlineSaveStatusText(): String =
    when {
        isOfflineSaved() -> "Saved offline"
        downloadState == YouTubeDownloadState.Downloading -> "Saving ${downloadProgress.coerceIn(1, 99)}%"
        downloadState == YouTubeDownloadState.Failed -> "Offline save failed. Tap retry."
        else -> "Not saved offline"
    }

internal fun isReadableOfflineUri(context: Context, uri: Uri): Boolean =
    runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
    }.getOrDefault(false)
