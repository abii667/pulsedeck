package com.pulsedeck.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

internal enum class AlbumMetadataProvider {
    MusicBrainz,
    ListenBrainz,
    CoverArtArchive,
    AppleMusicCatalog,
    PulseDeckCache,
}

internal enum class VerifiedReleaseType {
    Album,
    EP,
    Single,
    Compilation,
    Unknown,
}

internal enum class AlbumRecommendationReason {
    BecauseYouListened,
    NewFromArtist,
    PopularNow,
    SimilarArtist,
    ExploreSomethingNew,
}

internal data class VerifiedAlbumCandidate(
    val stableAlbumId: String,
    val provider: AlbumMetadataProvider,
    val title: String,
    val artistId: String,
    val artistName: String,
    val releaseType: VerifiedReleaseType,
    val releaseDate: String?,
    val releaseYear: Int?,
    val trackCount: Int?,
    val artworkUrl: String?,
    val musicBrainzReleaseGroupId: String?,
    val appleMusicAlbumId: String?,
    val popularityScore: Double?,
    val recommendationReason: AlbumRecommendationReason,
)

internal data class RecommendedAlbumsSnapshot(
    val albums: List<VerifiedAlbumCandidate>,
    val generatedAtEpochMs: Long,
    val refreshAfterEpochMs: Long,
    val profileHash: String,
    val providerSummary: String,
)

internal data class AlbumRecommendationContext(
    val frequentlyPlayedArtists: List<String> = emptyList(),
    val completedPlayArtists: List<String> = emptyList(),
    val savedArtistNames: List<String> = emptyList(),
    val albumPlayKeys: Set<String> = emptySet(),
    val recentGenresOrTags: List<String> = emptyList(),
    val skippedArtistKeys: Set<String> = emptySet(),
    val recentlyShownAlbumIds: Set<String> = emptySet(),
    val savedAlbumKeys: Set<String> = emptySet(),
    val artistAffinity: Map<String, Double> = emptyMap(),
    val nowEpochMs: Long = System.currentTimeMillis(),
    val networkPolicy: StreamingDataPolicy = StreamingDataPolicy.Unrestricted,
    val offlineDeckActive: Boolean = false,
    val betaUnlocked: Boolean = true,
    val explicitCellularRefreshAllowed: Boolean = false,
) {
    val seedArtistNames: List<String>
        get() = (frequentlyPlayedArtists + completedPlayArtists + savedArtistNames)
            .map { it.cleanStreamArtistName() }
            .filter { it.normalizedSearchText().length >= 2 }
            .distinctBy { it.normalizedSearchText() }
            .take(MAX_RECOMMENDATION_SEED_ARTISTS)
}

internal interface RecommendedAlbumProvider {
    suspend fun loadCandidates(context: AlbumRecommendationContext): List<VerifiedAlbumCandidate>
}

internal class MusicBrainzAlbumProvider : RecommendedAlbumProvider {
    override suspend fun loadCandidates(context: AlbumRecommendationContext): List<VerifiedAlbumCandidate> = withContext(Dispatchers.IO) {
        if (!context.allowsAlbumRecommendationProviderRequests()) return@withContext emptyList()
        context.seedArtistNames
            .flatMap { artist -> fetchMusicBrainzAlbumCandidatesForArtist(artist, context.nowEpochMs) }
            .distinctBy { it.stableAlbumId }
            .take(MAX_RECOMMENDATION_POOL_SIZE)
    }
}

internal class ListenBrainzPopularityProvider : RecommendedAlbumProvider {
    override suspend fun loadCandidates(context: AlbumRecommendationContext): List<VerifiedAlbumCandidate> = withContext(Dispatchers.IO) {
        if (!context.allowsAlbumRecommendationProviderRequests()) return@withContext emptyList()
        context.seedArtistNames
            .flatMap { artist -> fetchListenBrainzPopularReleaseGroupsForArtist(artist) }
            .distinctBy { it.stableAlbumId }
            .take(MAX_RECOMMENDATION_POOL_SIZE)
    }
}

internal class CoverArtArchiveProvider : RecommendedAlbumProvider {
    override suspend fun loadCandidates(context: AlbumRecommendationContext): List<VerifiedAlbumCandidate> =
        emptyList()
}

internal class AppleMusicCatalogAlbumProvider(
    private val catalogProvider: suspend (AlbumRecommendationContext) -> List<VerifiedAlbumCandidate> = { emptyList() },
) : RecommendedAlbumProvider {
    override suspend fun loadCandidates(context: AlbumRecommendationContext): List<VerifiedAlbumCandidate> {
        if (!context.allowsAlbumRecommendationProviderRequests()) return emptyList()
        return catalogProvider(context)
            .filter { it.provider == AlbumMetadataProvider.AppleMusicCatalog && it.appleMusicAlbumId?.isNotBlank() == true }
    }
}

internal fun AlbumRecommendationContext.allowsAlbumRecommendationProviderRequests(): Boolean =
    betaUnlocked &&
        !offlineDeckActive &&
        !networkPolicy.network.isNoNetwork &&
        !networkPolicy.effectiveDataSaver &&
        when {
            networkPolicy.network.networkType == PulseNetworkType.Wifi -> true
            networkPolicy.network.networkType == PulseNetworkType.Ethernet -> true
            networkPolicy.network.isCellularOrMetered -> explicitCellularRefreshAllowed
            else -> false
        }

internal fun RecommendedAlbumsSnapshot?.shouldRefreshRecommendedAlbums(
    context: AlbumRecommendationContext,
    sectionVisible: Boolean,
): Boolean {
    if (!sectionVisible || !context.allowsAlbumRecommendationProviderRequests()) return false
    val snapshot = this ?: return true
    if (snapshot.albums.isEmpty()) return true
    if (context.nowEpochMs < snapshot.refreshAfterEpochMs) return false
    return true
}

internal fun RecommendedAlbumsSnapshot?.isHardStale(nowEpochMs: Long): Boolean =
    this == null || nowEpochMs - generatedAtEpochMs > RECOMMENDED_ALBUM_HARD_STALE_MS

internal suspend fun refreshRecommendedAlbumsSnapshot(
    context: AlbumRecommendationContext,
    previousSnapshot: RecommendedAlbumsSnapshot?,
    providers: List<RecommendedAlbumProvider> = defaultRecommendedAlbumProviders(),
): RecommendedAlbumsSnapshot {
    if (!context.allowsAlbumRecommendationProviderRequests()) {
        return previousSnapshot ?: RecommendedAlbumsSnapshot(emptyList(), 0L, 0L, context.recommendationProfileHash(), "blocked")
    }
    val providerCandidates = providers.map { provider ->
        runCatching { provider.loadCandidates(context) }.getOrDefault(emptyList())
    }
    val merged = providerCandidates.flatten()
        .filter(::isValidRecommendedAlbumCandidate)
        .distinctBy { it.canonicalRecommendationKey() }
        .take(MAX_RECOMMENDATION_POOL_SIZE)
    val selected = selectVerifiedAlbumRecommendations(merged, context)
    if (selected.isEmpty()) {
        return previousSnapshot ?: RecommendedAlbumsSnapshot(emptyList(), 0L, 0L, context.recommendationProfileHash(), "empty")
    }
    if (selected.size < RECOMMENDED_ALBUM_CARD_COUNT && previousSnapshot?.albums?.any(::isValidRecommendedAlbumCandidate) == true) {
        return previousSnapshot
    }
    return RecommendedAlbumsSnapshot(
        albums = selected,
        generatedAtEpochMs = context.nowEpochMs,
        refreshAfterEpochMs = context.nowEpochMs + RECOMMENDED_ALBUM_TTL_MS,
        profileHash = context.recommendationProfileHash(),
        providerSummary = providerCandidates
            .mapIndexed { index, candidates -> "${providers[index].javaClass.simpleName}:${candidates.size}" }
            .joinToString(","),
    )
}

internal fun defaultRecommendedAlbumProviders(): List<RecommendedAlbumProvider> =
    listOf(
        MusicBrainzAlbumProvider(),
        AppleMusicCatalogAlbumProvider(),
    )

internal class RecommendedAlbumRefreshCoordinator {
    private val mutex = Mutex()
    private var inFlight: Deferred<RecommendedAlbumsSnapshot>? = null

    suspend fun refresh(
        scope: CoroutineScope,
        block: suspend () -> RecommendedAlbumsSnapshot,
    ): RecommendedAlbumsSnapshot {
        val deferred = mutex.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async(start = CoroutineStart.LAZY) {
                block()
            }.also { inFlight = it }
        }
        deferred.start()
        return try {
            deferred.await()
        } finally {
            mutex.withLock {
                if (inFlight === deferred) inFlight = null
            }
        }
    }
}

internal fun selectVerifiedAlbumRecommendations(
    candidates: List<VerifiedAlbumCandidate>,
    context: AlbumRecommendationContext,
    limit: Int = RECOMMENDED_ALBUM_CARD_COUNT,
): List<VerifiedAlbumCandidate> {
    val valid = candidates
        .filter(::isValidRecommendedAlbumCandidate)
        .filterNot { it.stableAlbumId in context.recentlyShownAlbumIds || it.canonicalRecommendationKey() in context.recentlyShownAlbumIds }
        .filterNot { it.artistKey() in context.skippedArtistKeys }
        .dedupeAlbumVariants()
        .take(MAX_RECOMMENDATION_POOL_SIZE)

    val unsaved = valid.filterNot { it.canonicalRecommendationKey() in context.savedAlbumKeys || it.stableAlbumId in context.savedAlbumKeys }
    val pool = if (unsaved.size >= limit) unsaved else valid
    val selected = mutableListOf<VerifiedAlbumCandidate>()

    fun addNext(reason: AlbumRecommendationReason, predicate: (VerifiedAlbumCandidate) -> Boolean) {
        if (selected.size >= limit) return
        pool.asSequence()
            .filter(predicate)
            .filter { candidate -> selected.none { it.artistKey() == candidate.artistKey() } }
            .filterNot { candidate -> selected.any { it.canonicalRecommendationKey() == candidate.canonicalRecommendationKey() } }
            .sortedByDescending { it.recommendationScore(context, reason) }
            .firstOrNull()
            ?.let { selected += it.copy(recommendationReason = reason) }
    }

    repeat(2) {
        addNext(AlbumRecommendationReason.BecauseYouListened) { it.affinityScore(context) > 0.0 }
    }
    addNext(AlbumRecommendationReason.NewFromArtist) { it.freshnessScore(context.nowEpochMs) >= 0.35 && it.affinityScore(context) > 0.0 }
    addNext(AlbumRecommendationReason.PopularNow) { (it.popularityScore ?: 0.0) > 0.0 }
    addNext(AlbumRecommendationReason.ExploreSomethingNew) { it.affinityScore(context) <= 0.0 }

    pool.asSequence()
        .filter { candidate -> selected.none { it.artistKey() == candidate.artistKey() } }
        .filterNot { candidate -> selected.any { it.canonicalRecommendationKey() == candidate.canonicalRecommendationKey() } }
        .sortedByDescending { it.recommendationScore(context, it.recommendationReason) }
        .forEach { candidate ->
            if (selected.size < limit) {
                val reason = candidate.recommendationReason.takeIf { it != AlbumRecommendationReason.BecauseYouListened || candidate.affinityScore(context) > 0.0 }
                    ?: AlbumRecommendationReason.SimilarArtist
                selected += candidate.copy(recommendationReason = reason)
            }
        }

    return selected.take(limit)
}

internal fun isValidRecommendedAlbumCandidate(candidate: VerifiedAlbumCandidate): Boolean {
    if (candidate.releaseType != VerifiedReleaseType.Album) return false
    if (candidate.stableAlbumId.isBlank() || candidate.title.isBlank() || candidate.artistId.isBlank() || candidate.artistName.isBlank()) return false
    if (!candidate.hasStrictReleaseEntityEvidence()) return false
    if (candidate.releaseDate.isNullOrBlank() && candidate.releaseYear == null && candidate.trackCount == null) return false
    val title = candidate.title.normalizedSearchText()
    val artist = candidate.artistName.normalizedSearchText()
    val providerId = candidate.stableAlbumId.normalizedSearchText()
    val combined = "$title $artist $providerId"
    val banned = listOf(
        "podcast",
        "interview",
        "audiobook",
        "audio book",
        "spoken word",
        "spoken-word",
        "playlist",
        "generated mix",
        "radio mix",
        "video collection",
        "music videos",
        "official videos",
        "single",
        "unknown collection",
    )
    if (banned.any { combined.contains(it) }) return false
    if (title in setOf("unknown", "unknown album", "music", "videos", "playlist", "mix")) return false
    if (artist in setOf("unknown", "unknown artist", "various youtube", "premiumdeck")) return false
    return true
}

private fun VerifiedAlbumCandidate.hasStrictReleaseEntityEvidence(): Boolean =
    when (provider) {
        AlbumMetadataProvider.MusicBrainz ->
            musicBrainzReleaseGroupId?.isNotBlank() == true
        AlbumMetadataProvider.AppleMusicCatalog ->
            appleMusicAlbumId?.isNotBlank() == true
        AlbumMetadataProvider.ListenBrainz,
        AlbumMetadataProvider.CoverArtArchive,
        AlbumMetadataProvider.PulseDeckCache,
        -> false
    }

internal fun VerifiedAlbumCandidate.toAlbumBuilderSeed(): AlbumDownloadRelease =
    AlbumDownloadRelease(
        id = stableAlbumId.albumBuilderReleaseId().ifBlank { canonicalRecommendationKey().hashCode().toUInt().toString(16) },
        title = title,
        artist = artistName,
        date = releaseDate.orEmpty(),
        format = releaseType.name,
        label = musicBrainzReleaseGroupId?.let { "MusicBrainz release-group $it" }
            ?: appleMusicAlbumId?.let { "Apple Music album $it" }
            .orEmpty(),
        trackCount = trackCount ?: 0,
        tracks = emptyList(),
        coverUrl = artworkUrl.orEmpty(),
        source = provider.name,
        score = ((popularityScore ?: 0.0).coerceIn(0.0, 1.0) * 100.0).toInt(),
    )

internal suspend fun VerifiedAlbumCandidate.toVerifiedAlbumBuilderReleaseWithTracklist(
    fallback: AlbumDownloadRelease = toAlbumBuilderSeed(),
): AlbumDownloadRelease? =
    when (provider) {
        AlbumMetadataProvider.MusicBrainz -> {
            val releaseGroupId = musicBrainzReleaseGroupId
                ?: stableAlbumId.removePrefix("mb-rg:").takeIf { it.isNotBlank() }
            val musicBrainz = releaseGroupId?.let { fetchMusicBrainzReleaseGroupAlbumBuilderRelease(it, fallback) }
            if (musicBrainz?.tracks?.isNotEmpty() == true) {
                musicBrainz
            } else {
                fetchAppleItunesAlbumBuilderRelease(appleMusicAlbumId, musicBrainz ?: fallback)
                    ?: musicBrainz
            }
        }
        AlbumMetadataProvider.AppleMusicCatalog ->
            fetchAppleItunesAlbumBuilderRelease(appleMusicAlbumId ?: stableAlbumId, fallback)
        AlbumMetadataProvider.ListenBrainz,
        AlbumMetadataProvider.CoverArtArchive,
        AlbumMetadataProvider.PulseDeckCache,
        -> null
    }

private fun String.albumBuilderReleaseId(): String =
    trim()
        .replace(Regex("[^A-Za-z0-9:._-]"), "-")
        .trim('-')
        .take(96)

internal fun loadRecommendedAlbumsSnapshot(context: Context): RecommendedAlbumsSnapshot? {
    val raw = context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .getString(PREF_RECOMMENDED_ALBUMS_SNAPSHOT, "")
        .orEmpty()
    if (raw.isBlank()) return null
    return runCatching { parseRecommendedAlbumsSnapshot(JSONObject(raw)) }.getOrNull()
}

internal fun saveRecommendedAlbumsSnapshot(context: Context, snapshot: RecommendedAlbumsSnapshot) {
    context.getSharedPreferences(PREFS_LIBRARY_STATE, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_RECOMMENDED_ALBUMS_SNAPSHOT, snapshot.toJson().toString())
        .apply()
}

internal fun parseRecommendedAlbumsSnapshot(json: JSONObject): RecommendedAlbumsSnapshot =
    RecommendedAlbumsSnapshot(
        albums = json.optJSONArray("albums").toVerifiedAlbumCandidates().filter(::isValidRecommendedAlbumCandidate),
        generatedAtEpochMs = json.optLong("generatedAtEpochMs", 0L),
        refreshAfterEpochMs = json.optLong("refreshAfterEpochMs", 0L),
        profileHash = json.optString("profileHash"),
        providerSummary = json.optString("providerSummary"),
    )

internal fun RecommendedAlbumsSnapshot.toJson(): JSONObject =
    JSONObject()
        .put("generatedAtEpochMs", generatedAtEpochMs)
        .put("refreshAfterEpochMs", refreshAfterEpochMs)
        .put("profileHash", profileHash)
        .put("providerSummary", providerSummary)
        .put("albums", JSONArray().apply { albums.forEach { put(it.toJson()) } })

internal fun VerifiedAlbumCandidate.toJson(): JSONObject =
    JSONObject()
        .put("stableAlbumId", stableAlbumId)
        .put("provider", provider.name)
        .put("title", title)
        .put("artistId", artistId)
        .put("artistName", artistName)
        .put("releaseType", releaseType.name)
        .put("releaseDate", releaseDate)
        .put("releaseYear", releaseYear)
        .put("trackCount", trackCount)
        .put("artworkUrl", artworkUrl)
        .put("musicBrainzReleaseGroupId", musicBrainzReleaseGroupId)
        .put("appleMusicAlbumId", appleMusicAlbumId)
        .put("popularityScore", popularityScore)
        .put("recommendationReason", recommendationReason.name)

private fun JSONArray?.toVerifiedAlbumCandidates(): List<VerifiedAlbumCandidate> =
    buildList {
        val array = this@toVerifiedAlbumCandidates ?: return@buildList
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            json.toVerifiedAlbumCandidateOrNull()?.let(::add)
        }
    }

private fun JSONObject.toVerifiedAlbumCandidateOrNull(): VerifiedAlbumCandidate? {
    val id = optString("stableAlbumId").trim()
    val title = optString("title").trim()
    val artistId = optString("artistId").trim()
    val artistName = optString("artistName").trim()
    if (id.isBlank() || title.isBlank() || artistId.isBlank() || artistName.isBlank()) return null
    return VerifiedAlbumCandidate(
        stableAlbumId = id,
        provider = enumValueOrDefault(optString("provider"), AlbumMetadataProvider.PulseDeckCache),
        title = title,
        artistId = artistId,
        artistName = artistName,
        releaseType = enumValueOrDefault(optString("releaseType"), VerifiedReleaseType.Unknown),
        releaseDate = optString("releaseDate").takeIf { it.isNotBlank() && it != "null" },
        releaseYear = if (has("releaseYear") && !isNull("releaseYear")) optInt("releaseYear") else null,
        trackCount = if (has("trackCount") && !isNull("trackCount")) optInt("trackCount") else null,
        artworkUrl = optString("artworkUrl").takeIf { it.startsWith("http", ignoreCase = true) },
        musicBrainzReleaseGroupId = optString("musicBrainzReleaseGroupId").takeIf { it.isNotBlank() && it != "null" },
        appleMusicAlbumId = optString("appleMusicAlbumId").takeIf { it.isNotBlank() && it != "null" },
        popularityScore = if (has("popularityScore") && !isNull("popularityScore")) optDouble("popularityScore").takeIf { !it.isNaN() } else null,
        recommendationReason = enumValueOrDefault(optString("recommendationReason"), AlbumRecommendationReason.BecauseYouListened),
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
    runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

private suspend fun fetchMusicBrainzAlbumCandidatesForArtist(
    artistName: String,
    nowEpochMs: Long,
): List<VerifiedAlbumCandidate> {
    val artistIdentity = resolveMusicBrainzArtistIdentity(artistName) ?: return emptyList()
    val url = "https://musicbrainz.org/ws/2/release-group?fmt=json&limit=16&type=album&artist=${artistIdentity.id.urlParam()}"
    val json = withTimeoutOrNull(5200L) { httpJson(url, connectTimeout = 2200, readTimeout = 4200) } ?: return emptyList()
    val groups = json.optJSONArray("release-groups") ?: return emptyList()
    return buildList {
        for (index in 0 until groups.length()) {
            val item = groups.optJSONObject(index) ?: continue
            val type = item.musicBrainzVerifiedReleaseType()
            if (type != VerifiedReleaseType.Album) continue
            val title = item.optString("title").trim()
            val id = item.optString("id").trim()
            val firstReleaseDate = item.optString("first-release-date").trim()
            val artistIds = item.musicBrainzReleaseGroupArtistIds()
            if (artistIds.isNotEmpty() && artistIdentity.id !in artistIds) continue
            val artistCredit = item.musicBrainzReleaseGroupArtistCredit().ifBlank { artistIdentity.name }
            val artistId = artistIds.firstOrNull { it == artistIdentity.id } ?: artistIdentity.id
            if (id.isBlank() || title.isBlank() || artistCredit.isBlank()) continue
            val candidate = VerifiedAlbumCandidate(
                stableAlbumId = "mb-rg:$id",
                provider = AlbumMetadataProvider.MusicBrainz,
                title = title,
                artistId = artistId,
                artistName = artistCredit,
                releaseType = type,
                releaseDate = firstReleaseDate.takeIf { it.isNotBlank() },
                releaseYear = firstReleaseDate.take(4).toIntOrNull(),
                trackCount = null,
                artworkUrl = "https://coverartarchive.org/release-group/$id/front-500",
                musicBrainzReleaseGroupId = id,
                appleMusicAlbumId = null,
                popularityScore = item.optInt("score", 0).takeIf { it > 0 }?.let { (it / 100.0).coerceIn(0.0, 1.0) }
                    ?: item.musicBrainzStandardAlbumConfidence(),
                recommendationReason = if ((firstReleaseDate.take(4).toIntOrNull() ?: 0) >= currentYear(nowEpochMs) - 1) {
                    AlbumRecommendationReason.NewFromArtist
                } else {
                    AlbumRecommendationReason.BecauseYouListened
                },
            )
            if (isValidRecommendedAlbumCandidate(candidate)) add(candidate)
        }
    }
}

private data class MusicBrainzArtistIdentity(
    val id: String,
    val name: String,
)

private suspend fun resolveMusicBrainzArtistIdentity(artistName: String): MusicBrainzArtistIdentity? {
    val query = "artist:\"$artistName\""
    val url = "https://musicbrainz.org/ws/2/artist?fmt=json&limit=8&query=${query.urlParam()}"
    val json = withTimeoutOrNull(4200L) { httpJson(url, connectTimeout = 1800, readTimeout = 3400) } ?: return null
    val artists = json.optJSONArray("artists") ?: return null
    val requestedKey = artistName.cleanStreamArtistName().normalizedSearchText()
    return buildList {
        for (index in 0 until artists.length()) {
            val item = artists.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isBlank() || name.isBlank()) continue
            val score = item.optInt("score", 0)
            val nameKey = name.cleanStreamArtistName().normalizedSearchText()
            if (score >= 80 || nameKey == requestedKey) {
                add(Triple(MusicBrainzArtistIdentity(id, name), nameKey == requestedKey, score))
            }
        }
    }.sortedWith(
        compareByDescending<Triple<MusicBrainzArtistIdentity, Boolean, Int>> { it.second }
            .thenByDescending { it.third },
    ).firstOrNull()?.first
}

private fun JSONObject.musicBrainzVerifiedReleaseType(): VerifiedReleaseType {
    if (!optString("primary-type").equals("Album", ignoreCase = true)) return VerifiedReleaseType.Unknown
    val secondaryTypes = optJSONArray("secondary-types").jsonStringValues()
    if (secondaryTypes.any { it.equals("Compilation", ignoreCase = true) }) return VerifiedReleaseType.Compilation
    return VerifiedReleaseType.Album
}

private fun JSONObject.musicBrainzStandardAlbumConfidence(): Double {
    val title = optString("title").normalizedSearchText()
    val secondaryTypes = optJSONArray("secondary-types").jsonStringValues().map { it.normalizedSearchText() }
    var confidence = 0.90
    if ("live" in secondaryTypes || Regex("""\b\d{4}[-/]\d{2}[-/]\d{2}\b""").containsMatchIn(title)) confidence = minOf(confidence, 0.15)
    if ("remix" in secondaryTypes || title.contains("remix")) confidence = minOf(confidence, 0.25)
    if ("soundtrack" in secondaryTypes || title.contains("soundtrack")) confidence = minOf(confidence, 0.18)
    if (title.contains("deluxe") || title.contains("expanded") || title.contains("anniversary") || title.contains("remaster")) {
        confidence = minOf(confidence, 0.45)
    }
    return confidence
}

private suspend fun fetchListenBrainzPopularReleaseGroupsForArtist(artistName: String): List<VerifiedAlbumCandidate> {
    val url = "https://api.listenbrainz.org/1/popularity/top-release-groups/artist/${artistName.urlParam()}?count=10"
    val json = withTimeoutOrNull(4200L) { httpJson(url, connectTimeout = 1800, readTimeout = 3400) } ?: return emptyList()
    val payload = json.optJSONArray("payload") ?: json.optJSONObject("payload")?.optJSONArray("release_groups") ?: return emptyList()
    return buildList {
        for (index in 0 until payload.length()) {
            val item = payload.optJSONObject(index) ?: continue
            val id = item.optString("release_group_mbid").ifBlank { item.optString("caa_id") }.trim()
            val title = item.optString("release_group_name").ifBlank { item.optString("title") }.trim()
            val artist = item.optString("artist_name").ifBlank { artistName }.trim()
            val artistId = item.optString("artist_mbid").ifBlank { artist.normalizedSearchText() }.trim()
            val total = max(item.optInt("total_listen_count", 0), item.optInt("listen_count", 0))
            if (id.isBlank() || title.isBlank() || artist.isBlank()) continue
            val candidate = VerifiedAlbumCandidate(
                stableAlbumId = "lb-rg:$id",
                provider = AlbumMetadataProvider.ListenBrainz,
                title = title,
                artistId = artistId,
                artistName = artist,
                releaseType = VerifiedReleaseType.Album,
                releaseDate = null,
                releaseYear = null,
                trackCount = null,
                artworkUrl = if (id.isNotBlank()) "https://coverartarchive.org/release-group/$id/front-500" else null,
                musicBrainzReleaseGroupId = id,
                appleMusicAlbumId = null,
                popularityScore = (total / 10_000.0).coerceIn(0.0, 1.0).takeIf { it > 0.0 },
                recommendationReason = AlbumRecommendationReason.PopularNow,
            )
            if (isValidRecommendedAlbumCandidate(candidate)) add(candidate)
        }
    }
}

private fun JSONObject.musicBrainzReleaseGroupArtistCredit(): String {
    val credits = optJSONArray("artist-credit") ?: return ""
    return buildString {
        for (index in 0 until credits.length()) {
            val item = credits.optJSONObject(index) ?: continue
            append(item.optString("name").ifBlank { item.optJSONObject("artist")?.optString("name").orEmpty() })
            append(item.optString("joinphrase"))
        }
    }.trim()
}

private fun JSONObject.musicBrainzReleaseGroupArtistIds(): List<String> {
    val credits = optJSONArray("artist-credit") ?: return emptyList()
    return buildList {
        for (index in 0 until credits.length()) {
            val id = credits.optJSONObject(index)?.optJSONObject("artist")?.optString("id").orEmpty().trim()
            if (id.isNotBlank()) add(id)
        }
    }
}

private fun JSONArray?.jsonStringValues(): List<String> =
    buildList {
        val array = this@jsonStringValues ?: return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

private fun List<VerifiedAlbumCandidate>.dedupeAlbumVariants(): List<VerifiedAlbumCandidate> =
    groupBy { it.canonicalRecommendationKey() }
        .values
        .map { variants ->
            variants.maxWith(
                compareBy<VerifiedAlbumCandidate> { if (it.musicBrainzReleaseGroupId != null) 1 else 0 }
                    .thenBy { it.popularityScore ?: 0.0 }
                    .thenBy { it.trackCount ?: 0 },
            )
        }

private fun VerifiedAlbumCandidate.recommendationScore(context: AlbumRecommendationContext, targetReason: AlbumRecommendationReason): Double {
    val affinity = affinityScore(context)
    val popularity = (popularityScore ?: 0.0).coerceIn(0.0, 1.0)
    val freshness = freshnessScore(context.nowEpochMs)
    val diversity = if (artistKey() !in context.artistAffinity.keys) 1.0 else 0.65
    val exploration = if (affinity <= 0.0) 1.0 else 0.25
    val slotBoost = when (targetReason) {
        AlbumRecommendationReason.BecauseYouListened -> affinity * 0.20
        AlbumRecommendationReason.NewFromArtist -> freshness * 0.20
        AlbumRecommendationReason.PopularNow -> popularity * 0.20
        AlbumRecommendationReason.SimilarArtist -> diversity * 0.16
        AlbumRecommendationReason.ExploreSomethingNew -> exploration * 0.20
    }
    return affinity * 0.40 + popularity * 0.25 + freshness * 0.20 + diversity * 0.10 + exploration * 0.05 + slotBoost
}

private fun VerifiedAlbumCandidate.affinityScore(context: AlbumRecommendationContext): Double {
    val artistKey = artistKey()
    val explicit = context.artistAffinity[artistKey] ?: 0.0
    val frequent = context.frequentlyPlayedArtists.any { it.cleanStreamArtistName().normalizedSearchText() == artistKey }
    val completed = context.completedPlayArtists.any { it.cleanStreamArtistName().normalizedSearchText() == artistKey }
    val saved = context.savedArtistNames.any { it.cleanStreamArtistName().normalizedSearchText() == artistKey }
    val behavioral = (if (frequent) 0.35 else 0.0) +
        (if (completed) 0.20 else 0.0) +
        (if (saved) 0.25 else 0.0)
    return (explicit + behavioral).coerceIn(0.0, 1.0)
}

private fun VerifiedAlbumCandidate.freshnessScore(nowEpochMs: Long): Double {
    val year = releaseYear ?: releaseDate?.take(4)?.toIntOrNull() ?: return 0.25
    val age = (currentYear(nowEpochMs) - year).coerceAtLeast(0)
    return when {
        age <= 0 -> 1.0
        age == 1 -> 0.85
        age == 2 -> 0.65
        age <= 5 -> 0.35
        else -> 0.12
    }
}

private fun VerifiedAlbumCandidate.artistKey(): String =
    artistName.cleanStreamArtistName().normalizedSearchText().ifBlank { artistId.normalizedSearchText() }

private fun VerifiedAlbumCandidate.canonicalRecommendationKey(): String =
    albumRecommendationKey(artistName, title.canonicalAlbumTitle())

private fun String.canonicalAlbumTitle(): String =
    replace(Regex("\\((?:deluxe|expanded|anniversary|remaster(?:ed)?|bonus|special edition)[^)]*\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:deluxe|expanded|anniversary|remaster(?:ed)?|bonus|special edition)\\b", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { this }

internal fun AlbumRecommendationContext.recommendationProfileHash(): String {
    val raw = listOf(
        seedArtistNames.joinToString("|") { it.normalizedSearchText() },
        artistAffinity.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${"%.3f".format(Locale.US, it.value)}" },
        savedAlbumKeys.sorted().joinToString("|"),
        skippedArtistKeys.sorted().joinToString("|"),
    ).joinToString("::")
    return MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(16)
}

internal fun buildAlbumRecommendationContext(
    sources: List<YouTubeSource>,
    playHistory: List<StreamPlayHistoryItem>,
    followedArtists: List<FollowedStreamArtist>,
    albumDrafts: List<AlbumDownloadDraft>,
    personalizationProfile: com.pulsedeck.app.premiumdeck.personalization.UserPreferenceProfile,
    streamPolicy: StreamingDataPolicy,
    offlineDeckActive: Boolean,
    betaUnlocked: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
): AlbumRecommendationContext {
    val accepted = sources.filter { it.reviewState == YouTubeReviewState.Accepted && !it.isPodcast }
    val frequentArtists = accepted
        .filter { it.playCount > 0 || it.lastPlayedMillis > 0L }
        .sortedWith(compareByDescending<YouTubeSource> { it.playCount }.thenByDescending { it.lastPlayedMillis })
        .map { it.author.cleanStreamArtistName() }
    val completedArtists = playHistory
        .filter { it.durationMillis > 0L }
        .sortedByDescending { it.playedAtMillis }
        .map { it.author.cleanStreamArtistName() }
    val savedArtists = followedArtists.map { it.officialName.ifBlank { it.name } } +
        accepted.filter { it.bookmarked || it.reaction == YouTubeReaction.Liked }.map { it.author.cleanStreamArtistName() }
    val savedAlbumKeys = albumDrafts.map { it.release.albumRecommendationKey() }.filter { it.isNotBlank() }.toSet()
    val skipped = accepted.filter { it.reaction == YouTubeReaction.Disliked }.map { it.artistKey() }.filter { it.isNotBlank() }.toSet()
    val affinity = buildMap {
        personalizationProfile.artistAffinity.forEach { (key, value) -> put(key, value.toDouble().coerceIn(-1.0, 1.0)) }
        frequentArtists.take(12).forEachIndexed { index, artist ->
            val key = artist.normalizedSearchText()
            if (key.isNotBlank()) put(key, max(get(key) ?: 0.0, 1.0 - index * 0.06))
        }
        savedArtists.take(12).forEach { artist ->
            val key = artist.normalizedSearchText()
            if (key.isNotBlank()) put(key, max(get(key) ?: 0.0, 0.72))
        }
    }
    return AlbumRecommendationContext(
        frequentlyPlayedArtists = frequentArtists,
        completedPlayArtists = completedArtists,
        savedArtistNames = savedArtists,
        albumPlayKeys = emptySet(),
        recentGenresOrTags = emptyList(),
        skippedArtistKeys = skipped,
        recentlyShownAlbumIds = emptySet(),
        savedAlbumKeys = savedAlbumKeys,
        artistAffinity = affinity,
        nowEpochMs = nowEpochMs,
        networkPolicy = streamPolicy,
        offlineDeckActive = offlineDeckActive,
        betaUnlocked = betaUnlocked,
    )
}

private fun currentYear(nowEpochMs: Long): Int =
    Calendar.getInstance().apply { timeInMillis = nowEpochMs }.get(Calendar.YEAR)

private const val MAX_RECOMMENDATION_SEED_ARTISTS = 8
private const val MAX_RECOMMENDATION_POOL_SIZE = 30
internal const val RECOMMENDED_ALBUM_CARD_COUNT = 5
internal const val RECOMMENDED_ALBUM_TTL_MS = 72L * 60L * 60L * 1000L
internal const val RECOMMENDED_ALBUM_HARD_STALE_MS = 7L * 24L * 60L * 60L * 1000L
private const val PREF_RECOMMENDED_ALBUMS_SNAPSHOT = "premiumdeck_recommended_albums_snapshot_v1"
