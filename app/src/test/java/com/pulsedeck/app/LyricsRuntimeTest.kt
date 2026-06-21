package com.pulsedeck.app

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRuntimeTest {
    @Test
    fun lrclibSearchRankingPrefersExactArtistTitleAndDuration() {
        val query = LyricsLookupQuery(
            artist = "Coldplay",
            title = "Yellow",
            album = "Parachutes",
            durationSeconds = 266L,
        )
        val ranked = rankLrclibSearchResults(
            JSONArray(
                """
                [
                  {
                    "trackName": "Yellow Submarine",
                    "artistName": "The Beatles",
                    "albumName": "Revolver",
                    "duration": 158,
                    "plainLyrics": "wrong result with enough text"
                  },
                  {
                    "trackName": "Yellow",
                    "artistName": "Coldplay",
                    "albumName": "Parachutes",
                    "duration": 266,
                    "syncedLyrics": "[00:01.00]First synced line\n[00:05.00]Second synced line"
                  },
                  {
                    "trackName": "Yellow",
                    "artistName": "Other Artist",
                    "albumName": "Other",
                    "duration": 266,
                    "plainLyrics": "wrong artist with enough text"
                  }
                ]
                """.trimIndent(),
            ),
            query,
        )

        assertEquals(1, ranked.size)
        assertEquals("Coldplay", ranked.first().json.optString("artistName"))
        assertEquals("Yellow", ranked.first().json.optString("trackName"))
    }

    @Test
    fun lrclibCandidateRejectsWrongArtist() {
        val candidate = JSONObject(
            """
            {
              "trackName": "Yellow",
              "artistName": "Other Artist",
              "duration": 266,
              "plainLyrics": "This candidate has text but belongs to another artist."
            }
            """.trimIndent(),
        )

        assertFalse(candidate.isAcceptableLrclibCandidate(LyricsLookupQuery(artist = "Coldplay", title = "Yellow")))
    }

    @Test
    fun lrclibTitleOnlyQueryRequiresExactTitle() {
        val wrong = JSONObject(
            """
            {
              "trackName": "Yellow Submarine",
              "artistName": "The Beatles",
              "plainLyrics": "This candidate has text but is not the requested short title."
            }
            """.trimIndent(),
        )
        val exact = JSONObject(
            """
            {
              "trackName": "Yellow",
              "artistName": "Coldplay",
              "plainLyrics": "This candidate has text and the exact requested title."
            }
            """.trimIndent(),
        )

        assertFalse(wrong.isAcceptableLrclibCandidate(LyricsLookupQuery(artist = "", title = "Yellow")))
        assertTrue(exact.isAcceptableLrclibCandidate(LyricsLookupQuery(artist = "", title = "Yellow")))
    }

    @Test
    fun lookupQueriesIncludeArtistTitleSplitForMessyTitle() {
        val queries = lyricsLookupQueriesForTrack(
            Track(
                title = "Coldplay - Yellow (Official Video)",
                artist = "Unknown Artist",
                duration = "4:26",
                durationMillis = 266_000L,
                album = Album(
                    title = "PremiumDeck Streams",
                    artist = "PremiumDeck",
                    mark = "STREAM",
                    tint = Color.Blue,
                    alt = Color.Yellow,
                ),
            ),
        )

        assertTrue(queries.any { it.artist == "Coldplay" && it.title == "Yellow" })
        assertTrue(queries.all { it.album == null })
    }
}
