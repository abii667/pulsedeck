package com.pulsedeck.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationMetadataTest {
    @Test
    fun normalizesStationMetadataFromExistingFields() {
        val station = radioStation(
            name = "Addis Jazz Nights",
            bitrate = 128,
            language = "amharic, english",
            tags = "ethiopian, jazz, public radio",
            lastCheckOk = true,
            lastCheckOkTime = "2026-06-14T12:00:00Z",
        )

        val metadata = station.normalizedMetadata()

        assertEquals(RadioGenre.EthiopianEastAfrican, metadata.genre)
        assertEquals(RadioContentType.PublicRadio, metadata.contentType)
        assertEquals(RadioQualityTier.Balanced, metadata.qualityTier)
        assertEquals(RadioReliability.RecentlySuccessful, metadata.reliability)
        assertEquals("Amharic", metadata.languageLabel)
    }

    @Test
    fun dataSaverPolicyPrefersKnownCappedBitrates() {
        val lowData = radioStation(name = "Low", bitrate = 64, votes = 20, clickCount = 10)
        val highData = radioStation(name = "High", bitrate = 256, votes = 200, clickCount = 100)
        val policy = StreamingDataPolicy(maxAudioBitrateKbps = 96, effectiveDataSaver = true)

        assertTrue(lowData.matchesLowDataPolicy(policy))
        assertFalse(highData.matchesLowDataPolicy(policy))
        assertTrue(lowData.radioDiscoveryScore(policy) > highData.radioDiscoveryScore(policy))
    }

    @Test
    fun discoveryKeyAvoidsRawStreamUrlWhenUuidMissing() {
        val station = radioStation(
            stationUuid = "",
            name = "No UUID",
            streamUrl = "https://streams.example.com/private/live.mp3?token=secret",
        )

        val key = station.discoveryKey()

        assertTrue(key.startsWith("radio-"))
        assertFalse(key.contains("streams.example.com"))
        assertFalse(key.contains("secret"))
    }

    @Test
    fun recentStationKeysAreOrderedDedupedAndCapped() {
        val stations = (0..30).map { index ->
            radioStation(stationUuid = "station-$index", name = "Station $index")
        }
        val updated = stations.fold(emptyList<String>()) { keys, station ->
            updatedRecentRadioStationKeys(keys, station)
        }
        val replayed = updatedRecentRadioStationKeys(updated, stations[10])

        assertEquals(RECENT_RADIO_STATION_LIMIT, updated.size)
        assertEquals("station-30", updated.first())
        assertEquals("station-10", replayed.first())
        assertEquals(replayed.size, replayed.distinct().size)
    }

    private fun radioStation(
        stationUuid: String = "station-1",
        name: String = "Station",
        streamUrl: String = "https://example.com/live.mp3",
        bitrate: Int = 128,
        codec: String = "MP3",
        language: String = "english",
        tags: String = "music",
        votes: Int = 0,
        country: String = "United States",
        countryCode: String = "US",
        homepage: String = "",
        favicon: String = "",
        clickCount: Int = 0,
        clickTrend: Int = 0,
        lastCheckOk: Boolean? = null,
        lastCheckTime: String = "",
        lastCheckOkTime: String = "",
    ): RadioStation =
        RadioStation(
            stationUuid = stationUuid,
            name = name,
            streamUrl = streamUrl,
            bitrate = bitrate,
            codec = codec,
            language = language,
            tags = tags,
            votes = votes,
            country = country,
            countryCode = countryCode,
            homepage = homepage,
            favicon = favicon,
            clickCount = clickCount,
            clickTrend = clickTrend,
            lastCheckOk = lastCheckOk,
            lastCheckTime = lastCheckTime,
            lastCheckOkTime = lastCheckOkTime,
        )
}
