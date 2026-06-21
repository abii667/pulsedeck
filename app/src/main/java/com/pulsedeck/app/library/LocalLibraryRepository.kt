package com.pulsedeck.app.library

import android.content.Context
import com.pulsedeck.app.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface LocalLibraryRepository {
    suspend fun loadCachedTracks(): List<Track>?
    suspend fun saveCachedTracks(tracks: List<Track>)
    suspend fun scanDeviceTracks(
        limit: Int = 700,
        previousTracks: List<Track> = emptyList(),
        reason: LocalLibraryScanReason = LocalLibraryScanReason.CacheMiss,
    ): LocalLibraryScanResult
    fun loadStateSet(key: String): Set<String>
    fun saveStateSet(key: String, values: Set<String>)
    fun loadPlayCounts(): Map<String, Int>
    fun savePlayCounts(values: Map<String, Int>)
}

internal class AndroidLocalLibraryRepository(
    private val context: Context,
    private val scanner: LocalLibraryScanner = LocalLibraryScanner(),
) : LocalLibraryRepository {
    override suspend fun loadCachedTracks(): List<Track>? =
        withContext(Dispatchers.IO) {
            val startedAt = pulseDeckLibraryNow()
            val cached = loadCachedDeviceTracks(context)
            val migratedFromVersion = lastLocalLibraryCacheMigrationVersion
            PulseDeckLibraryDiagnostics.cacheLoad(
                hit = cached != null,
                trackCount = cached?.size ?: 0,
                durationMillis = pulseDeckLibraryNow() - startedAt,
                migratedFromVersion = migratedFromVersion,
            )
            if (cached != null && migratedFromVersion != null) {
                val saveStartedAt = pulseDeckLibraryNow()
                saveCachedDeviceTracks(context, cached)
                PulseDeckLibraryDiagnostics.cacheSave(cached.size, pulseDeckLibraryNow() - saveStartedAt)
            }
            lastLocalLibraryCacheMigrationVersion = null
            cached
        }

    override suspend fun saveCachedTracks(tracks: List<Track>) {
        withContext(Dispatchers.IO) {
            val startedAt = pulseDeckLibraryNow()
            saveCachedDeviceTracks(context, tracks)
            PulseDeckLibraryDiagnostics.cacheSave(tracks.size, pulseDeckLibraryNow() - startedAt)
        }
    }

    override suspend fun scanDeviceTracks(
        limit: Int,
        previousTracks: List<Track>,
        reason: LocalLibraryScanReason,
    ): LocalLibraryScanResult =
        withContext(Dispatchers.IO) { scanner.scanDeviceTracks(context, limit, previousTracks, reason) }

    override fun loadStateSet(key: String): Set<String> =
        loadLibraryStateSet(context, key)

    override fun saveStateSet(key: String, values: Set<String>) {
        saveLibraryStateSet(context, key, values)
    }

    override fun loadPlayCounts(): Map<String, Int> =
        loadTrackPlayCounts(context)

    override fun savePlayCounts(values: Map<String, Int>) {
        saveTrackPlayCounts(context, values)
    }
}
