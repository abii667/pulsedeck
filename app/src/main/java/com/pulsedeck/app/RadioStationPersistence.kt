package com.pulsedeck.app

import android.content.Context
import org.json.JSONArray

private const val PREFS_RADIO_STATE = "pulse_radio_state"
private const val PREF_RECENT_RADIO_STATION_KEYS = "recent_radio_station_keys"
internal const val RECENT_RADIO_STATION_LIMIT = 24

internal fun loadRecentRadioStationKeys(context: Context): List<String> {
    val raw = context.getSharedPreferences(PREFS_RADIO_STATE, Context.MODE_PRIVATE)
        .getString(PREF_RECENT_RADIO_STATION_KEYS, "[]")
        .orEmpty()
    return runCatching {
        val json = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until json.length()) {
                val key = json.optString(index).trim()
                if (key.isNotBlank() && key !in this) add(key)
            }
        }.take(RECENT_RADIO_STATION_LIMIT)
    }.getOrDefault(emptyList())
}

internal fun saveRecentRadioStationKeys(context: Context, keys: List<String>) {
    val json = JSONArray()
    keys.asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(RECENT_RADIO_STATION_LIMIT)
        .forEach { json.put(it) }
    context.getSharedPreferences(PREFS_RADIO_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_RECENT_RADIO_STATION_KEYS, json.toString())
        .apply()
}

internal fun updatedRecentRadioStationKeys(current: List<String>, station: RadioStation): List<String> {
    val key = station.discoveryKey()
    if (key.isBlank()) return current.take(RECENT_RADIO_STATION_LIMIT)
    return (listOf(key) + current.filterNot { it == key })
        .filter { it.isNotBlank() }
        .distinct()
        .take(RECENT_RADIO_STATION_LIMIT)
}
