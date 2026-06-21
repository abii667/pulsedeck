package com.pulsedeck.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private const val RADIO_BROWSER_BASE_URL = "https://de1.api.radio-browser.info"
private const val RADIO_BROWSER_USER_AGENT = "PulseDeckRadio/1.0"

internal data class RadioCountry(
    val name: String,
    val isoCode: String,
    val stationCount: Int,
)

internal data class RadioStation(
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val bitrate: Int,
    val codec: String,
    val language: String,
    val tags: String,
    val votes: Int,
    val country: String,
    val countryCode: String,
    val homepage: String,
    val favicon: String,
    val clickCount: Int,
    val clickTrend: Int = 0,
    val lastCheckOk: Boolean? = null,
    val lastCheckTime: String = "",
    val lastCheckOkTime: String = "",
)

internal class RadioBrowserClient(
    private val baseUrl: String = RADIO_BROWSER_BASE_URL,
    private val userAgent: String = RADIO_BROWSER_USER_AGENT,
) {
    suspend fun getCountryList(): List<RadioCountry> = withContext(Dispatchers.IO) {
        requestJsonArray("/json/countries", mapOf("hidebroken" to "true", "order" to "name"))
            .toCountryList()
            .sortedWith(compareBy({ it.name.lowercase(Locale.US) }, { it.isoCode }))
    }

    suspend fun searchStationsByCountry(
        countryCode: String,
        limit: Int = 30,
        name: String = "",
        tag: String = "",
        codec: String = "",
    ): List<RadioStation> = withContext(Dispatchers.IO) {
        val normalizedCode = countryCode.trim().uppercase(Locale.US)
        require(normalizedCode.length == 2 && normalizedCode.all { it in 'A'..'Z' }) {
            "Use a two-letter country code"
        }
        require(limit > 0) { "Limit must be positive" }

        val baseParams = linkedMapOf(
            "limit" to limit.coerceIn(1, 100).toString(),
            "hidebroken" to "true",
            "order" to "votes",
            "reverse" to "true",
        )
        val hasAdvancedFilters = name.isNotBlank() || tag.isNotBlank() || codec.isNotBlank()
        val payload = if (hasAdvancedFilters) {
            requestJsonArray(
                "/json/stations/search",
                baseParams + mapOf(
                    "countrycode" to normalizedCode,
                    "name" to name.trim(),
                    "tag" to tag.trim(),
                    "codec" to codec.trim(),
                ).filterValues { it.isNotBlank() },
            )
        } else {
            requestJsonArray("/json/stations/bycountrycodeexact/${urlPart(normalizedCode)}", baseParams)
        }
        payload.toStationList()
    }

    suspend fun clickStation(stationUuid: String): JSONObject? = withContext(Dispatchers.IO) {
        val uuid = stationUuid.trim()
        if (uuid.isBlank()) return@withContext null
        requestJsonObject("/json/url/${urlPart(uuid)}", emptyMap())
    }

    private fun requestJsonArray(path: String, params: Map<String, String>): JSONArray =
        JSONArray(requestText(path, params))

    private fun requestJsonObject(path: String, params: Map<String, String>): JSONObject =
        JSONObject(requestText(path, params))

    private fun requestText(path: String, params: Map<String, String>): String {
        val url = URL("$baseUrl${path.withLeadingSlash()}${params.toQueryString()}")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 9000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", userAgent)
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("Radio Browser returned HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONArray.toCountryList(): List<RadioCountry> =
    buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val isoCode = item.optString("iso_3166_1")
                .ifBlank { item.optString("countrycode") }
                .trim()
                .uppercase(Locale.US)
            if (name.isBlank() || isoCode.length != 2) continue
            add(
                RadioCountry(
                    name = name,
                    isoCode = isoCode,
                    stationCount = item.optInt("stationcount", 0),
                ),
            )
        }
    }

private fun JSONArray.toStationList(): List<RadioStation> =
    buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val streamUrl = item.optString("url_resolved")
                .ifBlank { item.optString("url") }
                .trim()
                .takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
                ?: continue
            add(
                RadioStation(
                    stationUuid = item.optString("stationuuid").trim(),
                    name = item.optString("name").trim().ifBlank { "Unnamed station" },
                    streamUrl = streamUrl,
                    bitrate = item.optInt("bitrate", 0),
                    codec = item.optString("codec").trim(),
                    language = item.optString("language").trim(),
                    tags = item.optString("tags").trim(),
                    votes = item.optInt("votes", 0),
                    country = item.optString("country").trim(),
                    countryCode = item.optString("countrycode").trim().uppercase(Locale.US),
                    homepage = item.optString("homepage").trim(),
                    favicon = item.optString("favicon").trim(),
                    clickCount = item.optInt("clickcount", 0),
                    clickTrend = item.optInt("clicktrend", 0),
                    lastCheckOk = item.optNullableBoolean("lastcheckok"),
                    lastCheckTime = item.optString("lastchecktime_iso8601")
                        .ifBlank { item.optString("lastchecktime") }
                        .trim(),
                    lastCheckOkTime = item.optString("lastcheckoktime_iso8601")
                        .ifBlank { item.optString("lastcheckoktime") }
                        .trim(),
                ),
            )
        }
    }

private fun JSONObject.optNullableBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return when (opt(name)) {
        is Boolean -> optBoolean(name)
        is Number -> optInt(name) > 0
        else -> optString(name).trim().lowercase(Locale.US).let { value ->
            when (value) {
                "1", "true", "yes" -> true
                "0", "false", "no" -> false
                else -> null
            }
        }
    }
}

private fun String.withLeadingSlash(): String = if (startsWith("/")) this else "/$this"

private fun Map<String, String>.toQueryString(): String =
    entries
        .filter { it.value.isNotBlank() }
        .joinToString("&", prefix = if (isEmpty()) "" else "?") { (key, value) ->
            "${urlPart(key)}=${urlPart(value)}"
        }
        .takeIf { it != "?" }
        .orEmpty()

private fun urlPart(value: String): String = URLEncoder.encode(value, "UTF-8")
