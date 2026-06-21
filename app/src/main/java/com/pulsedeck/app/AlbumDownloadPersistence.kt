package com.pulsedeck.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
internal fun loadAlbumDownloadDrafts(context: Context): List<AlbumDownloadDraft> {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_ALBUM_DOWNLOAD_DRAFTS, "[]")
        .orEmpty()
    return runCatching { parseAlbumDownloadDrafts(JSONArray(raw.ifBlank { "[]" })) }.getOrDefault(emptyList())
}

internal fun saveAlbumDownloadDrafts(context: Context, drafts: List<AlbumDownloadDraft>) {
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_ALBUM_DOWNLOAD_DRAFTS, albumDownloadDraftsToJson(drafts.take(50)).toString())
        .apply()
}

internal fun loadAlbumAudioDownloadJobs(context: Context): List<AlbumAudioDownloadJob> {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_ALBUM_AUDIO_DOWNLOAD_JOBS, "[]")
        .orEmpty()
    return runCatching { parseAlbumAudioDownloadJobs(JSONArray(raw.ifBlank { "[]" })) }.getOrDefault(emptyList())
}

internal fun saveAlbumAudioDownloadJobs(context: Context, jobs: List<AlbumAudioDownloadJob>) {
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_ALBUM_AUDIO_DOWNLOAD_JOBS, albumAudioDownloadJobsToJson(jobs.take(25)).toString())
        .apply()
}

internal fun parseAlbumDownloadDrafts(array: JSONArray): List<AlbumDownloadDraft> =
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val releaseJson = item.optJSONObject("release") ?: continue
            val release = parseAlbumDownloadReleaseJson(releaseJson) ?: continue
            add(AlbumDownloadDraft(release = release, savedMillis = item.optLong("savedMillis", System.currentTimeMillis())))
        }
    }
        .distinctBy { it.release.id }
        .sortedByDescending { it.savedMillis }

internal fun albumDownloadDraftsToJson(drafts: List<AlbumDownloadDraft>): JSONArray =
    JSONArray().apply {
        drafts.forEach { draft ->
            put(
                JSONObject()
                    .put("savedMillis", draft.savedMillis)
                    .put("release", draft.release.toAlbumDownloadReleaseJson()),
            )
        }
    }

internal fun parseAlbumAudioDownloadJobs(array: JSONArray): List<AlbumAudioDownloadJob> =
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val releaseId = item.optString("releaseId").trim()
            val title = item.optString("title").trim()
            val artist = item.optString("artist").trim()
            if (id.isBlank() || releaseId.isBlank() || title.isBlank() || artist.isBlank()) continue
            val status = runCatching { AlbumAudioDownloadStatus.valueOf(item.optString("status")) }
                .getOrDefault(AlbumAudioDownloadStatus.NeedsProvider)
            add(
                AlbumAudioDownloadJob(
                    id = id,
                    releaseId = releaseId,
                    title = title,
                    artist = artist,
                    provider = item.optString("provider"),
                    quality = item.optString("quality").ifBlank { "Highest" },
                    status = status,
                    progress = item.optInt("progress", 0).coerceIn(0, 100),
                    message = item.optString("message"),
                    startedMillis = item.optLong("startedMillis", System.currentTimeMillis()),
                ),
            )
        }
    }
        .distinctBy { it.releaseId }
        .sortedByDescending { it.startedMillis }

internal fun albumAudioDownloadJobsToJson(jobs: List<AlbumAudioDownloadJob>): JSONArray =
    JSONArray().apply {
        jobs.forEach { job ->
            put(
                JSONObject()
                    .put("id", job.id)
                    .put("releaseId", job.releaseId)
                    .put("title", job.title)
                    .put("artist", job.artist)
                    .put("provider", job.provider)
                    .put("quality", job.quality)
                    .put("status", job.status.name)
                    .put("progress", job.progress)
                    .put("message", job.message)
                    .put("startedMillis", job.startedMillis),
            )
        }
    }

private fun AlbumDownloadRelease.toAlbumDownloadReleaseJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("date", date)
        .put("country", country)
        .put("format", format)
        .put("label", label)
        .put("trackCount", trackCount)
        .put("coverUrl", coverUrl)
        .put("source", source)
        .put("license", license)
        .put("downloadQuality", downloadQuality)
        .put("score", score)
        .put(
            "tracks",
            JSONArray().apply {
                tracks.forEach { track ->
                    put(
                        JSONObject()
                            .put("position", track.position)
                            .put("title", track.title)
                            .put("durationMillis", track.durationMillis)
                            .put("recordingId", track.recordingId)
                            .put("downloadUrl", track.downloadUrl)
                            .put("source", track.source)
                            .put("mimeType", track.mimeType)
                            .put("downloadAllowed", track.downloadAllowed)
                            .put("matchedSource", track.matchedSource?.toYouTubeSourceJson())
                            .put("matchScore", track.matchScore)
                            .put("matchReason", track.matchReason)
                            .put("matchVerified", track.matchVerified)
                            .put(
                                "matchCandidates",
                                JSONArray().apply {
                                    track.matchCandidates.forEach { candidate -> put(candidate.toYouTubeSourceJson()) }
                                },
                            ),
                    )
                }
            },
        )

private fun parseAlbumDownloadReleaseJson(json: JSONObject): AlbumDownloadRelease? {
    val id = json.optString("id").trim()
    val title = json.optString("title").trim()
    val artist = json.optString("artist").trim()
    if (id.isBlank() || title.isBlank() || artist.isBlank()) return null
    val tracks = parseAlbumDownloadTracks(json.optJSONArray("tracks"))
    return AlbumDownloadRelease(
        id = id,
        title = title,
        artist = artist,
        date = json.optString("date"),
        country = json.optString("country"),
        format = json.optString("format"),
        label = json.optString("label"),
        trackCount = json.optInt("trackCount", tracks.size),
        tracks = tracks,
        coverUrl = json.optString("coverUrl"),
        source = json.optString("source"),
        license = json.optString("license"),
        downloadQuality = json.optString("downloadQuality"),
        score = json.optInt("score", 0),
    )
}

private fun parseAlbumDownloadTracks(array: JSONArray?): List<AlbumDownloadTrack> =
    buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            add(
                AlbumDownloadTrack(
                    position = item.optInt("position", index + 1).coerceAtLeast(1),
                    title = title,
                    durationMillis = item.optLong("durationMillis", 0L),
                    recordingId = item.optString("recordingId"),
                    downloadUrl = item.optString("downloadUrl"),
                    source = item.optString("source"),
                    mimeType = item.optString("mimeType"),
                    downloadAllowed = item.optBoolean("downloadAllowed", false),
                    matchedSource = item.optJSONObject("matchedSource")?.toYouTubeSourceOrNull(),
                    matchScore = item.optInt("matchScore", 0),
                    matchReason = item.optString("matchReason"),
                    matchVerified = item.optBoolean("matchVerified", false),
                    matchCandidates = item.optJSONArray("matchCandidates").toYouTubeSourceList(),
                ),
            )
        }
    }

private fun JSONArray?.toYouTubeSourceList(): List<YouTubeSource> =
    buildList {
        val array = this@toYouTubeSourceList ?: return@buildList
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toYouTubeSourceOrNull()?.let(::add)
        }
    }.distinctBy { it.streamDistinctKey() }

