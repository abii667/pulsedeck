package com.pulsedeck.app.library

import android.content.Context
import org.json.JSONObject

private const val PREFS_LIBRARY_STATE = "pulse_library_state"
private const val PREF_TRACK_PLAY_COUNTS = "track_play_counts"

internal fun loadLibraryStateSet(context: Context, key: String): Set<String> =
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getStringSet(key, emptySet())
        ?.toSet()
        .orEmpty()

internal fun saveLibraryStateSet(context: Context, key: String, values: Set<String>) {
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(key, values)
        .apply()
}

internal fun loadTrackPlayCounts(context: Context): Map<String, Int> {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_TRACK_PLAY_COUNTS, "{}")
        .orEmpty()
    return runCatching {
        val json = JSONObject(raw.ifBlank { "{}" })
        buildMap {
            json.keys().forEach { key ->
                val count = json.optInt(key, 0)
                if (key.isNotBlank() && count > 0) put(key, count)
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun saveTrackPlayCounts(context: Context, values: Map<String, Int>) {
    val json = JSONObject()
    values.forEach { (key, count) ->
        if (key.isNotBlank() && count > 0) json.put(key, count)
    }
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_TRACK_PLAY_COUNTS, json.toString())
        .apply()
}
