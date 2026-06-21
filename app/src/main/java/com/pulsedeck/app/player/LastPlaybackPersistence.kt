package com.pulsedeck.app.player

import android.content.Context
import com.pulsedeck.app.PREFS_LIBRARY_STATE
import org.json.JSONObject

private const val PREF_LAST_PLAYBACK_STATE = "last_playback_state"

internal fun loadLastPlaybackState(context: Context): LastPlaybackState? {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_LAST_PLAYBACK_STATE, null)
        ?: return null
    return runCatching {
        val json = JSONObject(raw)
        LastPlaybackState(
            kind = json.optString("kind").ifBlank { "local" },
            trackKey = json.optString("trackKey"),
            youtubeSourceId = json.optString("youtubeSourceId"),
            positionMillis = json.optLong("positionMillis", 0L),
            title = json.optString("title"),
            artist = json.optString("artist"),
            albumKey = json.optString("albumKey"),
            displayName = json.optString("displayName"),
            durationMillis = json.optLong("durationMillis", 0L),
            sizeBytes = json.optLong("sizeBytes", 0L),
            modifiedMillis = json.optLong("modifiedMillis", 0L),
            savedMillis = json.optLong("savedMillis", 0L),
        )
    }.getOrNull()
}

internal fun saveLastPlaybackState(context: Context, state: LastPlaybackState) {
    val json = JSONObject()
        .put("kind", state.kind)
        .put("trackKey", state.trackKey)
        .put("youtubeSourceId", state.youtubeSourceId)
        .put("positionMillis", state.positionMillis.coerceAtLeast(0L))
        .put("title", state.title)
        .put("artist", state.artist)
        .put("albumKey", state.albumKey)
        .put("displayName", state.displayName)
        .put("durationMillis", state.durationMillis)
        .put("sizeBytes", state.sizeBytes)
        .put("modifiedMillis", state.modifiedMillis)
        .put("savedMillis", System.currentTimeMillis())
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_LAST_PLAYBACK_STATE, json.toString())
        .apply()
}
