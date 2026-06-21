package com.pulsedeck.app.library

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.pulsedeck.app.sha256
import java.util.Locale

private const val LIBRARY_LOG_TAG = "PulseDeckLibrary"

internal enum class LocalLibraryScanReason(val label: String) {
    FirstLoad("first_load"),
    PermissionGranted("permission_granted"),
    ManualRefresh("manual_refresh"),
    AppRelaunch("app_relaunch"),
    MediaStoreChange("mediastore_change"),
    CacheMiss("cache_miss"),
}

internal data class LocalLibraryScanReport(
    val reason: LocalLibraryScanReason,
    val durationMillis: Long,
    val mediaStoreRows: Int,
    val tracks: Int,
    val added: Int,
    val updated: Int,
    val removed: Int,
    val unchanged: Int,
    val retrieverOpens: Int,
    val retrieverTotalMillis: Long,
    val retrieverMaxMillis: Long,
    val incrementalHit: Boolean,
    val cancelled: Boolean = false,
    val threadName: String = Thread.currentThread().name,
)

internal data class LocalLibraryScanResult(
    val tracks: List<com.pulsedeck.app.Track>,
    val report: LocalLibraryScanReport,
    val stableKeyReplacements: Map<String, String> = emptyMap(),
)

internal object PulseDeckLibraryDiagnostics {
    private var enabled = false

    fun configure(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun scanStart(reason: LocalLibraryScanReason, previousTracks: Int, limit: Int) {
        log("scan_start reason=${reason.label} previous=$previousTracks limit=$limit thread=${Thread.currentThread().name}")
    }

    fun scanEnd(report: LocalLibraryScanReport, keyReplacements: Int) {
        log(
            "scan_end reason=${report.reason.label} duration_ms=${report.durationMillis} rows=${report.mediaStoreRows} " +
                "tracks=${report.tracks} added=${report.added} updated=${report.updated} removed=${report.removed} unchanged=${report.unchanged} " +
                "retriever_opens=${report.retrieverOpens} retriever_total_ms=${report.retrieverTotalMillis} retriever_max_ms=${report.retrieverMaxMillis} " +
                "incremental_hit=${report.incrementalHit} key_replacements=$keyReplacements cancelled=${report.cancelled} thread=${report.threadName}",
        )
    }

    fun scanCancelled(reason: LocalLibraryScanReason, rows: Int, durationMillis: Long) {
        log("scan_cancelled reason=${reason.label} rows=$rows duration_ms=$durationMillis thread=${Thread.currentThread().name}")
    }

    fun staleScanIgnored(reason: LocalLibraryScanReason, generation: Long) {
        log("scan_stale_ignored reason=${reason.label} generation=$generation thread=${Thread.currentThread().name}")
    }

    fun cacheLoad(hit: Boolean, trackCount: Int, durationMillis: Long, migratedFromVersion: Int? = null) {
        val migration = migratedFromVersion?.let { " migrated_from=$it" }.orEmpty()
        log("cache_load hit=$hit tracks=$trackCount duration_ms=$durationMillis$migration thread=${Thread.currentThread().name}")
    }

    fun cacheSave(trackCount: Int, durationMillis: Long) {
        log("cache_save tracks=$trackCount duration_ms=$durationMillis thread=${Thread.currentThread().name}")
    }

    fun searchIndexBuild(trackCount: Int, durationMillis: Long, workerThread: String) {
        log("index_build tracks=$trackCount duration_ms=$durationMillis worker_thread=$workerThread log_thread=${Thread.currentThread().name}")
    }

    fun search(
        query: String,
        debounceMillis: Long,
        durationMillis: Long,
        resultCount: Int,
        workerThread: String,
        staleIgnored: Boolean = false,
    ) {
        val normalized = query.trim()
        val hash = if (normalized.isBlank()) "blank" else sha256(normalized.lowercase(Locale.US)).take(10)
        log(
            "search query_len=${normalized.length} query_hash=$hash debounce_ms=$debounceMillis " +
                "duration_ms=$durationMillis results=$resultCount stale_ignored=$staleIgnored worker_thread=$workerThread log_thread=${Thread.currentThread().name}",
        )
    }

    fun searchCancelled(query: String, debounceMillis: Long) {
        val normalized = query.trim()
        val hash = if (normalized.isBlank()) "blank" else sha256(normalized.lowercase(Locale.US)).take(10)
        log("search_cancelled query_len=${normalized.length} query_hash=$hash debounce_ms=$debounceMillis thread=${Thread.currentThread().name}")
    }

    private fun log(message: String) {
        if (enabled) Log.d(LIBRARY_LOG_TAG, message)
    }
}

internal fun pulseDeckLibraryNow(): Long = SystemClock.uptimeMillis()
