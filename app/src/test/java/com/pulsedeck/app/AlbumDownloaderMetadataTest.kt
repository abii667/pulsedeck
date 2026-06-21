package com.pulsedeck.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDownloaderMetadataTest {
    @Test
    fun musicBrainzReleaseDetailExtractsTracklistAndCover() {
        val release = parseMusicBrainzReleaseDetail(JSONObject(releaseDetailJson())) ?: error("Expected release")

        assertEquals("release-1", release.id)
        assertEquals("Blue Roads", release.title)
        assertEquals("Nina Vale", release.artist)
        assertEquals("2026-05-30", release.date)
        assertEquals("Digital Media", release.format)
        assertEquals("Pulse Records", release.label)
        assertEquals("https://coverartarchive.org/release/release-1/front-500", release.coverUrl)
        assertEquals(2, release.tracks.size)
        assertEquals("River Light", release.tracks[0].title)
        assertEquals(181000L, release.tracks[0].durationMillis)
    }

    @Test
    fun musicBrainzReleaseDetailFlattensMultiDiscTracklists() {
        val release = parseMusicBrainzReleaseDetail(JSONObject(multiDiscReleaseDetailJson())) ?: error("Expected release")

        assertEquals(4, release.tracks.size)
        assertEquals(listOf(1, 2, 3, 4), release.tracks.map { it.position })
        assertEquals(listOf("Disc One Start", "Disc One Close", "Disc Two Start", "Disc Two Close"), release.tracks.map { it.title })
    }

    @Test
    fun musicBrainzSearchFiltersAndDeduplicatesExactAlbumResults() {
        val json = JSONObject(
            """
            {
              "releases": [
                ${releaseSearchJson("release-1", "Blue Roads", "Nina Vale", 98)},
                ${releaseSearchJson("release-duplicate", "Blue Roads", "Nina Vale", 91)},
                ${releaseSearchJson("release-other", "Red Roads", "Nina Vale", 99)}
              ]
            }
            """.trimIndent(),
        )

        val releases = parseMusicBrainzReleaseSearchResults(json, artistQuery = "Nina Vale", albumQuery = "Blue Roads")

        assertEquals(1, releases.size)
        assertEquals("Blue Roads", releases.single().title)
        assertEquals(98, releases.single().score)
    }

    @Test
    fun albumDownloadDraftsRoundTripThroughJson() {
        val draft = AlbumDownloadDraft(
            release = AlbumDownloadRelease(
                id = "release-1",
                title = "Blue Roads",
                artist = "Nina Vale",
                date = "2026",
                tracks = listOf(
                    AlbumDownloadTrack(
                        position = 1,
                        title = "River Light",
                        durationMillis = 181000L,
                        matchedSource = testPremiumDeckSource("primary", "River Light"),
                        matchScore = 91,
                        matchReason = "PremiumDeck match  |  playable verified",
                        matchVerified = true,
                        matchCandidates = listOf(
                            testPremiumDeckSource("primary", "River Light"),
                            testPremiumDeckSource("backup", "River Light official audio"),
                        ),
                    ),
                ),
                coverUrl = "https://coverartarchive.org/release/release-1/front-500",
            ),
            savedMillis = 1234L,
        )

        val parsed = parseAlbumDownloadDrafts(albumDownloadDraftsToJson(listOf(draft)))

        assertEquals(1, parsed.size)
        assertEquals("release-1", parsed.single().release.id)
        assertEquals("River Light", parsed.single().release.tracks.single().title)
        assertTrue(parsed.single().release.tracks.single().matchVerified)
        assertEquals(2, parsed.single().release.tracks.single().matchCandidates.size)
        assertEquals(1234L, parsed.single().savedMillis)
    }

    @Test
    fun albumDownloadDraftsDropInvalidRows() {
        val parsed = parseAlbumDownloadDrafts(
            JSONArray()
                .put(JSONObject().put("release", JSONObject().put("id", "missing-title")))
                .put(
                    JSONObject().put(
                        "release",
                        JSONObject()
                            .put("id", "release-1")
                            .put("title", "Blue Roads")
                            .put("artist", "Nina Vale"),
                    ),
                ),
        )

        assertTrue(parsed.all { it.release.title.isNotBlank() && it.release.artist.isNotBlank() })
        assertEquals(1, parsed.size)
    }

    @Test
    fun appleLookupParsesAlbumTracklist() {
        val release = parseAppleItunesLookupRelease(JSONObject(appleLookupJson())) ?: error("Expected Apple release")

        assertEquals("apple:100", release.id)
        assertEquals("Blue Roads", release.title)
        assertEquals("Nina Vale", release.artist)
        assertEquals("Apple/iTunes", release.source)
        assertEquals(listOf("River Light", "Open Sky"), release.tracks.map { it.title })
        assertEquals(listOf(1, 2), release.tracks.map { it.position })
    }

    @Test
    fun appleSearchKeepsOnlyMatchingAlbumCollections() {
        val ids = parseAppleItunesAlbumSearchCollectionIds(
            JSONObject(
                """
                {
                  "results": [
                    {
                      "wrapperType": "collection",
                      "collectionType": "Album",
                      "collectionId": 100,
                      "collectionName": "Blue Roads (Deluxe)",
                      "artistName": "Nina Vale"
                    },
                    {
                      "wrapperType": "collection",
                      "collectionType": "Album",
                      "collectionId": 101,
                      "collectionName": "Red Roads",
                      "artistName": "Nina Vale"
                    },
                    {
                      "wrapperType": "collection",
                      "collectionType": "Album",
                      "collectionId": 102,
                      "collectionName": "Blue Roads",
                      "artistName": "Other Artist"
                    },
                    {
                      "wrapperType": "track",
                      "kind": "song",
                      "collectionId": 103,
                      "trackName": "Blue Roads"
                    }
                  ]
                }
                """.trimIndent(),
            ),
            artistQuery = "Nina Vale",
            albumQuery = "Blue Roads",
        )

        assertEquals(listOf(100L), ids)
    }

    @Test
    fun deezerDetailParsesAlbumTracklist() {
        val release = parseDeezerAlbumDetail(JSONObject(deezerAlbumJson())) ?: error("Expected Deezer release")

        assertEquals("deezer:200", release.id)
        assertEquals("Blue Roads", release.title)
        assertEquals("Nina Vale", release.artist)
        assertEquals("Deezer", release.source)
        assertEquals(listOf("River Light", "Open Sky"), release.tracks.map { it.title })
        assertEquals(181000L, release.tracks.first().durationMillis)
    }

    @Test
    fun deezerSearchKeepsOnlyMatchingAlbumIds() {
        val ids = parseDeezerAlbumSearchIds(
            JSONObject(
                """
                {
                  "data": [
                    {
                      "id": 200,
                      "title": "Blue Roads",
                      "artist": { "name": "Nina Vale" }
                    },
                    {
                      "id": 201,
                      "title": "Blue Roads",
                      "artist": { "name": "Other Artist" }
                    },
                    {
                      "id": 202,
                      "title": "Red Roads",
                      "artist": { "name": "Nina Vale" }
                    }
                  ]
                }
                """.trimIndent(),
            ),
            artistQuery = "Nina Vale",
            albumQuery = "Blue Roads",
        )

        assertEquals(listOf(200L), ids)
    }

    @Test
    fun providerSearchCanBrowseAlbumsByArtistOnly() {
        val appleIds = parseAppleItunesAlbumSearchCollectionIds(
            JSONObject(
                """
                {
                  "results": [
                    {
                      "wrapperType": "collection",
                      "collectionType": "Album",
                      "collectionId": 300,
                      "collectionName": "Any Road",
                      "artistName": "Nina Vale"
                    }
                  ]
                }
                """.trimIndent(),
            ),
            artistQuery = "Nina Vale",
            albumQuery = "",
        )
        val deezerIds = parseDeezerAlbumSearchIds(
            JSONObject(
                """
                {
                  "data": [
                    {
                      "id": 400,
                      "title": "Another Road",
                      "artist": { "name": "Nina Vale" }
                    }
                  ]
                }
                """.trimIndent(),
            ),
            artistQuery = "Nina Vale",
            albumQuery = "",
        )

        assertEquals(listOf(300L), appleIds)
        assertEquals(listOf(400L), deezerIds)
    }

    @Test
    fun manualTracklistCreatesAlbumProject() {
        val release = parseManualAlbumTracklist(
            artist = "Nina Vale",
            album = "Blue Roads",
            rawTracklist = """
                1. River Light
                2 - Open Sky 3:25
                Disc 2
                • Morning Glass
            """.trimIndent(),
        ) ?: error("Expected manual release")

        assertEquals("Manual tracklist", release.source)
        assertEquals(3, release.tracks.size)
        assertEquals(listOf("River Light", "Open Sky", "Morning Glass"), release.tracks.map { it.title })
    }

    @Test
    fun jamendoAlbumReleasesParseLegalFlacDownloadUrls() {
        val json = JSONObject(
            """
            {
              "results": [
                {
                  "id": "jam-1",
                  "name": "Blue Roads",
                  "artist_name": "Nina Vale",
                  "releasedate": "2026-05-30",
                  "image": "https://img.test/cover.jpg",
                  "tracks": [
                    {
                      "position": "1",
                      "name": "River Light",
                      "duration": "181",
                      "license_ccurl": "https://creativecommons.org/licenses/by/4.0/",
                      "audiodownload": "https://jamendo.test/river.flac",
                      "audiodownload_allowed": true
                    },
                    {
                      "position": "2",
                      "name": "Locked Track",
                      "audiodownload": "",
                      "audiodownload_allowed": false
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val releases = parseJamendoAlbumReleases(json)

        assertEquals(1, releases.size)
        assertEquals("jamendo:jam-1", releases.single().id)
        assertEquals("Jamendo", releases.single().source)
        assertEquals("FLAC", releases.single().downloadQuality)
        assertEquals("https://jamendo.test/river.flac", releases.single().tracks.first().downloadUrl)
        assertTrue(releases.single().tracks.first().downloadAllowed)
    }

    @Test
    fun internetArchiveReleaseParsesOriginalFlacFiles() {
        val doc = JSONObject(
            """
            {
              "identifier": "blue-roads",
              "title": "Blue Roads",
              "creator": "Nina Vale",
              "date": "2026"
            }
            """.trimIndent(),
        )
        val metadata = JSONObject(
            """
            {
              "metadata": {
                "licenseurl": "https://creativecommons.org/licenses/by/4.0/"
              },
              "files": [
                { "name": "01 - River Light.flac", "format": "FLAC", "source": "original" },
                { "name": "cover.jpg", "format": "JPEG", "source": "original" },
                { "name": "02 - Open Sky.mp3", "format": "VBR MP3", "source": "derivative" }
              ]
            }
            """.trimIndent(),
        )

        val release = parseInternetArchiveRelease(doc, metadata, fallbackArtist = "Nina Vale", fallbackAlbum = "Blue Roads")
            ?: error("Expected Archive release")

        assertEquals("archive:blue-roads", release.id)
        assertEquals("Internet Archive", release.source)
        assertEquals("FLAC", release.downloadQuality)
        assertEquals(1, release.tracks.size)
        assertEquals("River Light", release.tracks.single().title)
        assertEquals("https://archive.org/download/blue-roads/01%20-%20River%20Light.flac", release.tracks.single().downloadUrl)
        assertTrue(release.tracks.single().downloadAllowed)
    }

    @Test
    fun albumAudioDownloadJobsRoundTripThroughJson() {
        val job = AlbumAudioDownloadJob(
            id = "album-job-1",
            releaseId = "release-1",
            title = "Blue Roads",
            artist = "Nina Vale",
            provider = "Internet Archive",
            quality = "FLAC",
            status = AlbumAudioDownloadStatus.Queued,
            progress = 12,
            message = "Free legal download queued",
            startedMillis = 42L,
        )

        val parsed = parseAlbumAudioDownloadJobs(albumAudioDownloadJobsToJson(listOf(job)))

        assertEquals(1, parsed.size)
        assertEquals("Internet Archive", parsed.single().provider)
        assertEquals(AlbumAudioDownloadStatus.Queued, parsed.single().status)
        assertEquals(12, parsed.single().progress)
    }

    private fun releaseSearchJson(id: String, title: String, artist: String, score: Int): String =
        """
        {
          "id": "$id",
          "title": "$title",
          "score": $score,
          "date": "2026",
          "country": "US",
          "track-count": 2,
          "artist-credit": [{ "name": "$artist" }],
          "media": [{ "format": "Digital Media", "tracks": [] }]
        }
        """.trimIndent()

    private fun testPremiumDeckSource(id: String, title: String): YouTubeSource =
        YouTubeSource(
            id = "video-$id",
            url = "https://www.youtube.com/watch?v=$id",
            kind = YouTubeSourceKind.Video,
            title = title,
            author = "Nina Vale",
            reviewState = YouTubeReviewState.Accepted,
        )

    private fun releaseDetailJson(): String =
        """
        {
          "id": "release-1",
          "title": "Blue Roads",
          "date": "2026-05-30",
          "country": "US",
          "track-count": 2,
          "artist-credit": [{ "name": "Nina Vale" }],
          "label-info": [{ "label": { "name": "Pulse Records" } }],
          "media": [
            {
              "format": "Digital Media",
              "tracks": [
                {
                  "position": 1,
                  "title": "River Light",
                  "length": 181000,
                  "recording": { "id": "recording-1", "title": "River Light" }
                },
                {
                  "position": 2,
                  "title": "Open Sky",
                  "length": 205000,
                  "recording": { "id": "recording-2", "title": "Open Sky" }
                }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun appleLookupJson(): String =
        """
        {
          "resultCount": 3,
          "results": [
            {
              "wrapperType": "collection",
              "collectionType": "Album",
              "collectionId": 100,
              "collectionName": "Blue Roads",
              "artistName": "Nina Vale",
              "releaseDate": "2026-05-30T07:00:00Z",
              "trackCount": 2,
              "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Music/test/100x100bb.jpg"
            },
            {
              "wrapperType": "track",
              "kind": "song",
              "collectionId": 100,
              "trackId": 101,
              "trackName": "River Light",
              "discNumber": 1,
              "trackNumber": 1,
              "trackTimeMillis": 181000
            },
            {
              "wrapperType": "track",
              "kind": "song",
              "collectionId": 100,
              "trackId": 102,
              "trackName": "Open Sky",
              "discNumber": 1,
              "trackNumber": 2,
              "trackTimeMillis": 205000
            }
          ]
        }
        """.trimIndent()

    private fun deezerAlbumJson(): String =
        """
        {
          "id": 200,
          "title": "Blue Roads",
          "release_date": "2026-05-30",
          "nb_tracks": 2,
          "record_type": "album",
          "label": "Pulse Records",
          "cover_xl": "https://deezer.test/cover.jpg",
          "artist": { "name": "Nina Vale" },
          "tracks": {
            "data": [
              { "id": 201, "title_short": "River Light", "duration": 181, "disk_number": 1, "track_position": 1 },
              { "id": 202, "title_short": "Open Sky", "duration": 205, "disk_number": 1, "track_position": 2 }
            ]
          }
        }
        """.trimIndent()

    private fun multiDiscReleaseDetailJson(): String =
        """
        {
          "id": "release-multi",
          "title": "Long Roads",
          "date": "2026-05-30",
          "country": "US",
          "track-count": 4,
          "artist-credit": [{ "name": "Nina Vale" }],
          "media": [
            {
              "format": "Digital Media",
              "track-count": 2,
              "tracks": [
                { "position": 1, "title": "Disc One Start", "recording": { "id": "recording-1" } },
                { "position": 2, "title": "Disc One Close", "recording": { "id": "recording-2" } }
              ]
            },
            {
              "format": "Digital Media",
              "track-count": 2,
              "tracks": [
                { "position": 1, "title": "Disc Two Start", "recording": { "id": "recording-3" } },
                { "position": 2, "title": "Disc Two Close", "recording": { "id": "recording-4" } }
              ]
            }
          ]
        }
        """.trimIndent()
}
