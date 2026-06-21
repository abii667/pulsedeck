package com.pulsedeck.app

import android.content.Context
import org.json.JSONObject

private const val PREF_BACKGROUND_SETTINGS = "background_settings"

internal fun loadBackgroundSettings(context: Context): BackgroundSettings {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_BACKGROUND_SETTINGS, null)
        ?: return BackgroundSettings()
    return runCatching {
        val json = JSONObject(raw)
        BackgroundSettings(
            blurred = json.optBoolean("blurred", true),
            listBackground = json.optBoolean("listBackground", true),
            lyricsBackground = json.optBoolean("lyricsBackground", true),
            gradient = json.optDouble("gradient", 4.0).toFloat(),
            gradientColor = json.optString("gradientColor", "#000000"),
            gradientForLists = json.optBoolean("gradientForLists", json.optBoolean("listBackground", true)),
            blur = json.optDouble("blur", 5.0).toFloat(),
            details = json.optDouble("details", 5.0).toFloat(),
            intensity = json.optDouble("intensity", 1.0).toFloat(),
            saturation = json.optDouble("saturation", 1.5).toFloat(),
            readabilityProtection = json.optJSONObject("readabilityProtection")?.let { readability ->
                ReadabilityProtectionSettings(
                    enabled = readability.optBoolean("enabled", true),
                    autoDimLists = readability.optBoolean("autoDimLists", true),
                    autoDimLyrics = readability.optBoolean("autoDimLyrics", true),
                    minimumTextContrast = readability.optString("minimumTextContrast", "AA"),
                )
            } ?: ReadabilityProtectionSettings(),
        ).normalized()
    }.getOrDefault(BackgroundSettings())
}

internal fun saveBackgroundSettings(context: Context, settings: BackgroundSettings) {
    val normalized = settings.normalized()
    val json = JSONObject()
        .put("blurred", normalized.blurred)
        .put("listBackground", normalized.listBackground)
        .put("lyricsBackground", normalized.lyricsBackground)
        .put("gradient", normalized.gradient)
        .put("gradientColor", normalized.gradientColor)
        .put("gradientForLists", normalized.gradientForLists)
        .put("blur", normalized.blur)
        .put("details", normalized.details)
        .put("intensity", normalized.intensity)
        .put("saturation", normalized.saturation)
        .put(
            "readabilityProtection",
            JSONObject()
                .put("enabled", normalized.readabilityProtection.enabled)
                .put("autoDimLists", normalized.readabilityProtection.autoDimLists)
                .put("autoDimLyrics", normalized.readabilityProtection.autoDimLyrics)
                .put("minimumTextContrast", normalized.readabilityProtection.minimumTextContrast),
        )
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_BACKGROUND_SETTINGS, json.toString())
        .apply()
}
