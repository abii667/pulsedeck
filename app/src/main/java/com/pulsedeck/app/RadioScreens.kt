package com.pulsedeck.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private enum class PulseRadioPrimaryTab(val label: String) {
    All("All"),
    Saved("Saved"),
    Genres("Genres"),
    Languages("Languages"),
    Quality("Quality"),
    Programs("Programs"),
}

@Composable
internal fun PulseRadioScreen(
    countries: List<RadioCountry>,
    stations: List<RadioStation>,
    countryCode: String,
    nameQuery: String,
    stationFilter: RadioStationFilter,
    favoriteCountryCodes: Set<String>,
    favoriteStationKeys: Set<String>,
    recentStationKeys: List<String>,
    streamPolicy: StreamingDataPolicy,
    loadingCountries: Boolean,
    loadingStations: Boolean,
    error: String?,
    activeStreamUrl: String?,
    offlineMode: Boolean,
    onBack: () -> Unit,
    onNameQuery: (String) -> Unit,
    onStationFilter: (RadioStationFilter) -> Unit,
    onSearch: () -> Unit,
    onCountry: (RadioCountry) -> Unit,
    onToggleCountryFavorite: (RadioCountry) -> Unit,
    onToggleStationFavorite: (RadioStation) -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    hiddenCategoryHeaderName: String? = null,
    onCategoryHeaderBoundsChanged: (String, AlbumTileBounds) -> Unit = { _, _ -> },
) {
    var countryPickerOpen by rememberSaveable { mutableStateOf(false) }
    var selectedGenreName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedContentTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLanguageKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedQualityTierName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedReliabilityName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPrimaryTabName by rememberSaveable { mutableStateOf(PulseRadioPrimaryTab.All.name) }
    val selectedPrimaryTab = remember(selectedPrimaryTabName) {
        runCatching { PulseRadioPrimaryTab.valueOf(selectedPrimaryTabName) }.getOrDefault(PulseRadioPrimaryTab.All)
    }
    val selectedCountry = remember(countries, countryCode) { countries.firstOrNull { it.isoCode == countryCode.uppercase(Locale.US) } }
    val localSearchNeedle = remember(nameQuery) { nameQuery.normalizedSearchText() }
    val searchedStations = remember(stations, localSearchNeedle) {
        if (localSearchNeedle.isBlank()) stations else stations.filter { it.matchesPulseRadioSearch(localSearchNeedle) }
    }
    val rawSelectedGenre = remember(selectedGenreName) { selectedGenreName?.radioEnumValueOrNull<RadioGenre>() }
    val rawSelectedContentType = remember(selectedContentTypeName) { selectedContentTypeName?.radioEnumValueOrNull<RadioContentType>() }
    val rawSelectedQualityTier = remember(selectedQualityTierName) { selectedQualityTierName?.radioEnumValueOrNull<RadioQualityTier>() }
    val rawSelectedReliability = remember(selectedReliabilityName) { selectedReliabilityName?.radioEnumValueOrNull<RadioReliability>() }
    val recentStationKeySet = remember(recentStationKeys) { recentStationKeys.toSet() }
    val recentStationIndex = remember(recentStationKeys) { recentStationKeys.withIndex().associate { it.value to it.index } }
    val genreFacets = remember(searchedStations) { searchedStations.radioGenreFacets() }
    val contentFacets = remember(searchedStations) { searchedStations.radioContentFacets() }
    val languageFacets = remember(searchedStations) { searchedStations.radioLanguageFacets() }
    val qualityFacets = remember(searchedStations) { searchedStations.radioQualityFacets() }
    val reliabilityFacets = remember(searchedStations) { searchedStations.radioReliabilityFacets() }
    val hasDiscoveryFacets = genreFacets.isNotEmpty() || contentFacets.isNotEmpty() || languageFacets.isNotEmpty() || qualityFacets.isNotEmpty() || reliabilityFacets.isNotEmpty()
    val selectedGenre = rawSelectedGenre?.takeIf { selected -> genreFacets.any { it.value == selected } }
    val selectedContentType = rawSelectedContentType?.takeIf { selected -> contentFacets.any { it.value == selected } }
    val activeSelectedLanguageKey = selectedLanguageKey?.takeIf { selected -> languageFacets.any { it.key == selected } }
    val selectedQualityTier = rawSelectedQualityTier?.takeIf { selected -> qualityFacets.any { it.value == selected } }
    val selectedReliability = rawSelectedReliability?.takeIf { selected -> reliabilityFacets.any { it.value == selected } }
    val categoryFilteredStations = remember(
        searchedStations,
        selectedGenre,
        selectedContentType,
        activeSelectedLanguageKey,
        selectedQualityTier,
        selectedReliability,
    ) {
        searchedStations.filter { station ->
            val metadata = station.normalizedMetadata()
            val languageMatches = activeSelectedLanguageKey == null ||
                metadata.languageLabel.normalizedSearchText() == activeSelectedLanguageKey
            (selectedGenre == null || metadata.genre == selectedGenre) &&
                (selectedContentType == null || metadata.contentType == selectedContentType) &&
                languageMatches &&
                (selectedQualityTier == null || metadata.qualityTier == selectedQualityTier) &&
                (selectedReliability == null || metadata.reliability == selectedReliability)
        }
    }
    fun activatePrimaryTab(tab: PulseRadioPrimaryTab) {
        selectedPrimaryTabName = tab.name
        when (tab) {
            PulseRadioPrimaryTab.All -> {
                selectedContentTypeName = null
                selectedLanguageKey = null
                selectedQualityTierName = null
                selectedReliabilityName = null
                onStationFilter(RadioStationFilter.Popular)
            }
            PulseRadioPrimaryTab.Saved -> {
                selectedGenreName = null
                selectedContentTypeName = null
                selectedLanguageKey = null
                selectedQualityTierName = null
                selectedReliabilityName = null
                onStationFilter(RadioStationFilter.Saved)
            }
            PulseRadioPrimaryTab.Genres -> {
                selectedContentTypeName = null
                selectedLanguageKey = null
                selectedQualityTierName = null
                selectedReliabilityName = null
                onStationFilter(RadioStationFilter.Popular)
            }
            PulseRadioPrimaryTab.Languages -> {
                selectedGenreName = null
                selectedContentTypeName = null
                selectedQualityTierName = null
                selectedReliabilityName = null
                onStationFilter(RadioStationFilter.Popular)
            }
            PulseRadioPrimaryTab.Quality -> {
                selectedGenreName = null
                selectedContentTypeName = null
                selectedLanguageKey = null
                selectedReliabilityName = null
                onStationFilter(RadioStationFilter.Quality)
            }
            PulseRadioPrimaryTab.Programs -> {
                selectedGenreName = null
                selectedLanguageKey = null
                selectedQualityTierName = null
                selectedReliabilityName = null
                onStationFilter(RadioStationFilter.Popular)
            }
        }
    }
    fun activateNextFilterTab() {
        val next = when {
            selectedPrimaryTab == PulseRadioPrimaryTab.All && genreFacets.isNotEmpty() -> PulseRadioPrimaryTab.Genres
            selectedPrimaryTab == PulseRadioPrimaryTab.Genres && languageFacets.isNotEmpty() -> PulseRadioPrimaryTab.Languages
            selectedPrimaryTab == PulseRadioPrimaryTab.Languages && qualityFacets.isNotEmpty() -> PulseRadioPrimaryTab.Quality
            selectedPrimaryTab == PulseRadioPrimaryTab.Quality && contentFacets.isNotEmpty() -> PulseRadioPrimaryTab.Programs
            contentFacets.isNotEmpty() -> PulseRadioPrimaryTab.Programs
            languageFacets.isNotEmpty() -> PulseRadioPrimaryTab.Languages
            qualityFacets.isNotEmpty() -> PulseRadioPrimaryTab.Quality
            else -> PulseRadioPrimaryTab.All
        }
        activatePrimaryTab(next)
    }
    val visibleStations = remember(categoryFilteredStations, favoriteStationKeys, recentStationKeySet, recentStationIndex, stationFilter, streamPolicy) {
        val filtered = when (stationFilter) {
            RadioStationFilter.Saved -> categoryFilteredStations.filter { it.isFavorite(favoriteStationKeys) }
            RadioStationFilter.Recent -> categoryFilteredStations.filter { it.discoveryKey() in recentStationKeySet }
            RadioStationFilter.LowData -> categoryFilteredStations.filter { it.matchesLowDataPolicy(streamPolicy) }
            RadioStationFilter.Reliable -> categoryFilteredStations.filter { it.normalizedMetadata().reliability.isPositiveReliability }
            else -> categoryFilteredStations
        }
        when (stationFilter) {
            RadioStationFilter.Popular -> filtered.sortedWith(radioRecommendedStationComparator(favoriteStationKeys, recentStationKeySet, streamPolicy))
            RadioStationFilter.Saved -> filtered.sortedWith(radioRecommendedStationComparator(favoriteStationKeys, recentStationKeySet, streamPolicy))
            RadioStationFilter.Recent -> filtered.sortedWith(compareBy<RadioStation> { recentStationIndex[it.discoveryKey()] ?: Int.MAX_VALUE }.thenByDescending { it.votes })
            RadioStationFilter.LowData -> filtered.sortedWith(compareBy<RadioStation> { it.bitrate.takeIf { bitrate -> bitrate > 0 } ?: Int.MAX_VALUE }.thenByDescending { it.votes })
            RadioStationFilter.Quality -> filtered.sortedWith(compareByDescending<RadioStation> { it.bitrate }.thenByDescending { it.votes }.thenBy { it.name.lowercase(Locale.US) })
            RadioStationFilter.Reliable -> filtered.sortedWith(compareByDescending<RadioStation> { it.normalizedMetadata().reliability.reliabilityRank() }.thenByDescending { it.votes })
            RadioStationFilter.Name -> filtered.sortedBy { it.name.lowercase(Locale.US) }
        }
    }
    val favoriteStations = remember(stations, favoriteStationKeys) {
        stations
            .filter { it.isFavorite(favoriteStationKeys) }
            .sortedWith(compareByDescending<RadioStation> { it.votes }.thenByDescending { it.clickCount })
            .take(10)
    }
    val recentStations = remember(stations, recentStationKeys) {
        val byKey = stations.associateBy { it.discoveryKey() }
        recentStationKeys.mapNotNull { byKey[it] }.take(10)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050607), Color(0xFF07090C), Color(0xFF050607))))
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            PulseRadioCompactHeader(
                country = selectedCountry,
                fallbackCountryCode = countryCode,
                stationCount = stations.size,
                loading = loadingStations,
                offlineMode = offlineMode,
                onBack = onBack,
                onCountry = { countryPickerOpen = true },
                onRefresh = onSearch,
                onMore = { countryPickerOpen = true },
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp)
                    .graphicsLayer { alpha = if (hiddenCategoryHeaderName == "PulseRadio") 0f else 1f }
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInRoot()
                        onCategoryHeaderBoundsChanged(
                            "PulseRadio",
                            AlbumTileBounds(
                                left = position.x,
                                top = position.y,
                                width = coordinates.size.width.toFloat(),
                                height = coordinates.size.height.toFloat(),
                            ),
                        )
                    },
            )
            PulseRadioSearchBar(
                value = nameQuery,
                loading = loadingStations,
                offlineMode = offlineMode,
                onValue = onNameQuery,
                onFilter = { activateNextFilterTab() },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
            )
            PulseRadioPrimaryTabs(
                selected = selectedPrimaryTab,
                onSelected = { activatePrimaryTab(it) },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 10.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SavedStationsCarousel(
                        stations = favoriteStations,
                        activeStreamUrl = activeStreamUrl,
                        onViewAll = { activatePrimaryTab(PulseRadioPrimaryTab.Saved) },
                        onFavorite = onToggleStationFavorite,
                        onPlay = onPlayStation,
                    )
                }
                if (!offlineMode) {
                    item {
                        PulseRadioSecondaryChips(
                            selectedTab = selectedPrimaryTab,
                            stationFilter = stationFilter,
                            stations = categoryFilteredStations,
                            genreFacets = genreFacets,
                            contentFacets = contentFacets,
                            languageFacets = languageFacets,
                            qualityFacets = qualityFacets,
                            favoriteStations = favoriteStations,
                            recentStations = recentStations,
                            selectedGenre = selectedGenre,
                            selectedContentType = selectedContentType,
                            selectedLanguageKey = activeSelectedLanguageKey,
                            selectedQualityTier = selectedQualityTier,
                            onAllStations = {
                                selectedGenreName = null
                                selectedContentTypeName = null
                                selectedLanguageKey = null
                                selectedQualityTierName = null
                                selectedReliabilityName = null
                                onStationFilter(RadioStationFilter.Popular)
                            },
                            onSavedStations = { onStationFilter(RadioStationFilter.Saved) },
                            onRecentStations = { onStationFilter(RadioStationFilter.Recent) },
                            onGenre = { selectedGenreName = it?.name },
                            onContentType = { selectedContentTypeName = it?.name },
                            onLanguage = { selectedLanguageKey = it },
                            onQualityTier = {
                                selectedQualityTierName = it?.name
                                onStationFilter(RadioStationFilter.Quality)
                            },
                            onMore = { activateNextFilterTab() },
                        )
                    }
                }
                if (offlineMode) {
                    item { PulseRadioStatusRow("PulseRadio uses internet streams. Offline FM radio is not supported in-app.") }
                } else if (error != null) {
                    item { PulseRadioStatusRow(error) }
                } else if (loadingStations) {
                    item { PulseRadioStatusRow("Loading stations") }
                } else if (stations.isEmpty()) {
                    item { PulseRadioStatusRow("No stations found for this country") }
                } else if (searchedStations.isEmpty()) {
                    item { PulseRadioStatusRow("No stations match this search") }
                } else if (visibleStations.isEmpty()) {
                    item {
                        PulseRadioStatusRow(
                            if (selectedPrimaryTab == PulseRadioPrimaryTab.Saved) "No saved stations yet" else "No stations match these filters",
                        )
                    }
                } else {
                    itemsIndexed(visibleStations, key = { _, station -> station.discoveryKey() }) { index, station ->
                        AnimatedEntrance(index + 1) {
                            PulseRadioStationListCard(
                                station = station,
                                active = station.streamUrl == activeStreamUrl,
                                onPlay = { onPlayStation(station) },
                            )
                        }
                    }
                }
            }
        }
        if (countryPickerOpen) {
            PulseRadioCountryPicker(
                countries = countries,
                selectedCountryCode = countryCode,
                favoriteCountryCodes = favoriteCountryCodes,
                loading = loadingCountries,
                onDismiss = { countryPickerOpen = false },
                onCountry = { country ->
                    countryPickerOpen = false
                    onCountry(country)
                },
                onToggleFavorite = onToggleCountryFavorite,
            )
        }
    }
}

@Composable
private fun PulseRadioCompactHeader(
    country: RadioCountry?,
    fallbackCountryCode: String,
    stationCount: Int,
    loading: Boolean,
    offlineMode: Boolean,
    onBack: () -> Unit,
    onCountry: () -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countryCode = (country?.isoCode ?: fallbackCountryCode).uppercase(Locale.US).take(2).ifBlank { "US" }
    Row(
        modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseRadioHeaderIconButton(DeckIcon.Back, onBack, size = 34.dp)
        Spacer(Modifier.width(6.dp))
        PulseRadioHeaderMark(countryCode)
        Spacer(Modifier.width(8.dp))
        Text(
            "PulseRadio",
            color = StreamTextPrimary,
            fontSize = 20.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PulseRadioCountryPill(countryCode = countryCode, countryName = country?.name.orEmpty(), onClick = onCountry)
        Spacer(Modifier.width(6.dp))
        PulseRadioCountChip(if (loading) "..." else stationCount.toString())
        Spacer(Modifier.width(6.dp))
        PulseRadioHeaderIconButton(
            icon = if (offlineMode) DeckIcon.StreamOffline else if (loading) DeckIcon.Timer else DeckIcon.StreamReplace,
            onClick = onRefresh,
            enabled = !loading && !offlineMode,
            size = 34.dp,
        )
        PulseRadioHeaderIconButton(DeckIcon.More, onMore, size = 34.dp)
    }
}

@Composable
private fun PulseRadioHeaderMark(countryCode: String) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFF080A0F))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.pulseradio_placeholder_icon),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun PulseRadioCountryPill(countryCode: String, countryName: String, onClick: () -> Unit) {
    val interactionSource = remember(countryCode, countryName) { MutableInteractionSource() }
    Row(
        Modifier
            .height(26.dp)
            .widthIn(max = 98.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (countryCode == "ET") {
            PulseRadioCountryFlag(Modifier.size(16.dp))
        } else {
            Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Text(countryCode, color = StreamTextPrimary, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(
            listOf(countryCode, countryName.ifBlank { "Radio" }).joinToString(" / "),
            color = StreamTextSecondary,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PulseRadioCountryFlag(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF078930)))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFCDD09)))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFDA121A)))
    }
}

@Composable
private fun PulseRadioCountChip(label: String) {
    Row(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = StreamTextPrimary, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(3.dp))
        Text("Stations", color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PulseRadioHeaderIconButton(icon: DeckIcon, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 42.dp) {
    val interactionSource = remember(icon, enabled) { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (enabled) Modifier.pressScaleEffect(interactionSource).clickable(interactionSource = interactionSource, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = if (enabled) 0.86f else 0.32f), Modifier.size((size.value * 0.46f).dp))
    }
}

@Composable
private fun PulseRadioSearchBar(
    value: String,
    loading: Boolean,
    offlineMode: Boolean,
    onValue: (String) -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.040f))))
            .border(1.dp, Color.White.copy(alpha = 0.095f), RoundedCornerShape(23.dp))
            .padding(start = 14.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Search, Color.White.copy(alpha = 0.56f), Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            enabled = !offlineMode,
            textStyle = TextStyle(color = StreamTextPrimary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isBlank()) {
                    Text("Search stations...", color = Color.White.copy(alpha = 0.42f), fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            },
        )
        PulseRadioHeaderIconButton(if (loading) DeckIcon.Timer else DeckIcon.Sliders, onFilter, enabled = !offlineMode, size = 36.dp)
    }
}

@Composable
private fun PulseRadioPrimaryTabs(selected: PulseRadioPrimaryTab, onSelected: (PulseRadioPrimaryTab) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(PulseRadioPrimaryTab.entries, key = { it.name }) { tab ->
            PulseRadioPrimaryTabItem(tab = tab, selected = selected == tab, onClick = { onSelected(tab) })
        }
    }
}

@Composable
private fun PulseRadioPrimaryTabItem(tab: PulseRadioPrimaryTab, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember(tab, selected) { MutableInteractionSource() }
    Column(
        Modifier
            .width(92.dp)
            .fillMaxSize()
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tab.label, color = if (selected) Color(0xFF5DBBFF) else StreamTextSecondary, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .height(2.dp)
                .fillMaxWidth(if (selected) 0.78f else 0f)
                .background(Color(0xFF2EA4FF)),
        )
    }
}

@Composable
private fun SavedStationsCarousel(
    stations: List<RadioStation>,
    activeStreamUrl: String?,
    onViewAll: () -> Unit,
    onFavorite: (RadioStation) -> Unit,
    onPlay: (RadioStation) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .border(1.dp, Color(0xFF2EA4FF).copy(alpha = 0.13f), RoundedCornerShape(20.dp))
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Saved", color = StreamTextPrimary, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Spacer(Modifier.weight(1f))
            PulseRadioTextAction("View all", DeckIcon.Next, onViewAll)
        }
        if (stations.isEmpty()) {
            PulseRadioEmptySavedCard(Modifier.padding(horizontal = 12.dp))
        } else {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(stations, key = { "saved-${it.favoriteKey()}" }) { station ->
                    SavedStationCard(
                        station = station,
                        active = station.streamUrl == activeStreamUrl,
                        onFavorite = { onFavorite(station) },
                        onPlay = { onPlay(station) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PulseRadioTextAction(label: String, icon: DeckIcon, onClick: () -> Unit) {
    val interactionSource = remember(label) { MutableInteractionSource() }
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFF4DB4FF), fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(3.dp))
        PulseIcon(icon, Color(0xFF9BB6CA), Modifier.size(13.dp))
    }
}

@Composable
private fun PulseRadioEmptySavedCard(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseRadioArtwork(null, Modifier.size(46.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("No saved stations yet", color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("Save stations while listening", color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SavedStationCard(station: RadioStation, active: Boolean, onFavorite: () -> Unit, onPlay: () -> Unit) {
    val interactionSource = remember(station.discoveryKey(), active) { MutableInteractionSource() }
    Row(
        Modifier
            .width(218.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    if (active) listOf(Color(0xFF153E56), Color(0xFF111418)) else listOf(Color.White.copy(alpha = 0.080f), Color.White.copy(alpha = 0.035f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = if (active) 0.16f else 0.07f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseRadioArtwork(station, Modifier.size(50.dp), active = active)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = StreamTextPrimary, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(radioStationListSubtitle(station), color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        PulseRadioHeaderIconButton(DeckIcon.Heart, onFavorite, size = 28.dp)
    }
}

@Composable
private fun PulseRadioSecondaryChips(
    selectedTab: PulseRadioPrimaryTab,
    stationFilter: RadioStationFilter,
    stations: List<RadioStation>,
    genreFacets: List<RadioDiscoveryFacet<RadioGenre>>,
    contentFacets: List<RadioDiscoveryFacet<RadioContentType>>,
    languageFacets: List<RadioDiscoveryFacet<String>>,
    qualityFacets: List<RadioDiscoveryFacet<RadioQualityTier>>,
    favoriteStations: List<RadioStation>,
    recentStations: List<RadioStation>,
    selectedGenre: RadioGenre?,
    selectedContentType: RadioContentType?,
    selectedLanguageKey: String?,
    selectedQualityTier: RadioQualityTier?,
    onAllStations: () -> Unit,
    onSavedStations: () -> Unit,
    onRecentStations: () -> Unit,
    onGenre: (RadioGenre?) -> Unit,
    onContentType: (RadioContentType?) -> Unit,
    onLanguage: (String?) -> Unit,
    onQualityTier: (RadioQualityTier?) -> Unit,
    onMore: () -> Unit,
) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (selectedTab) {
            PulseRadioPrimaryTab.All -> {
                item {
                    PulseRadioFilterChip("All Stations", DeckIcon.Grid, stationFilter == RadioStationFilter.Popular && selectedGenre == null, onAllStations)
                }
                val quickGenres = listOf(RadioGenre.EthiopianEastAfrican, RadioGenre.Jazz, RadioGenre.Classical)
                    .mapNotNull { genre -> genreFacets.firstOrNull { it.value == genre } }
                items(quickGenres, key = { "quick-${it.key}" }) { facet ->
                    PulseRadioFilterChip(facet.label, DeckIcon.Tag, selectedGenre == facet.value) { onGenre(if (selectedGenre == facet.value) null else facet.value) }
                }
                if (genreFacets.size > quickGenres.size || contentFacets.isNotEmpty() || languageFacets.isNotEmpty() || qualityFacets.isNotEmpty()) {
                    item { PulseRadioFilterChip("More", DeckIcon.Next, false, onMore) }
                }
            }
            PulseRadioPrimaryTab.Saved -> {
                item { PulseRadioFilterChip("Saved Stations", DeckIcon.Heart, stationFilter == RadioStationFilter.Saved, onSavedStations) }
                if (recentStations.isNotEmpty()) item { PulseRadioFilterChip("Recent", DeckIcon.StreamRecent, stationFilter == RadioStationFilter.Recent, onRecentStations) }
                item { PulseRadioFilterChip("All Stations", DeckIcon.Grid, stationFilter == RadioStationFilter.Popular, onAllStations) }
            }
            PulseRadioPrimaryTab.Genres -> {
                item { PulseRadioFilterChip("All Genres", DeckIcon.Grid, selectedGenre == null, onAllStations) }
                items(genreFacets, key = { "genre-${it.key}" }) { facet ->
                    PulseRadioFilterChip(facet.label, DeckIcon.Tag, selectedGenre == facet.value) { onGenre(if (selectedGenre == facet.value) null else facet.value) }
                }
            }
            PulseRadioPrimaryTab.Languages -> {
                item { PulseRadioFilterChip("All Languages", DeckIcon.Grid, selectedLanguageKey == null, onAllStations) }
                items(languageFacets, key = { "language-${it.key}" }) { facet ->
                    PulseRadioFilterChip(facet.label, DeckIcon.Comment, selectedLanguageKey == facet.key) { onLanguage(if (selectedLanguageKey == facet.key) null else facet.key) }
                }
            }
            PulseRadioPrimaryTab.Quality -> {
                item { PulseRadioFilterChip("All Quality", DeckIcon.Grid, selectedQualityTier == null, onAllStations) }
                items(qualityFacets, key = { "quality-${it.key}" }) { facet ->
                    PulseRadioFilterChip(facet.label, DeckIcon.Signal, selectedQualityTier == facet.value) { onQualityTier(if (selectedQualityTier == facet.value) null else facet.value) }
                }
            }
            PulseRadioPrimaryTab.Programs -> {
                item { PulseRadioFilterChip("All Programs", DeckIcon.Grid, selectedContentType == null, onAllStations) }
                items(contentFacets, key = { "program-${it.key}" }) { facet ->
                    PulseRadioFilterChip(facet.label, DeckIcon.StreamRadio, selectedContentType == facet.value) { onContentType(if (selectedContentType == facet.value) null else facet.value) }
                }
            }
        }
        if (stations.isEmpty() && favoriteStations.isEmpty()) {
            item { PulseRadioFilterChip("No Filters", DeckIcon.Info, false, onAllStations) }
        }
    }
}

@Composable
private fun PulseRadioFilterChip(label: String, icon: DeckIcon, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember(label, selected) { MutableInteractionSource() }
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) Color(0xFF0B2C45).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (selected) Color(0xFF2EA4FF).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.085f), RoundedCornerShape(17.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(icon, if (selected) Color(0xFF66C4FF) else StreamTextSecondary, Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (selected) Color(0xFF78C9FF) else StreamTextSecondary, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PulseRadioStationListCard(station: RadioStation, active: Boolean, onPlay: () -> Unit) {
    val interactionSource = remember(station.discoveryKey(), active) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    if (active) listOf(Color(0xFF153C50), Color(0xFF111418)) else listOf(Color.White.copy(alpha = 0.070f), Color.White.copy(alpha = 0.030f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = if (active) 0.15f else 0.075f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseRadioArtwork(station, Modifier.size(48.dp), active = active)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = StreamTextPrimary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(radioStationListSubtitle(station), color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        PulseRadioPlayButton(active = active, onClick = onPlay, size = 44.dp)
    }
}

@Composable
private fun PulseRadioPlayButton(active: Boolean, onClick: () -> Unit, size: Dp) {
    val interactionSource = remember(active) { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.055f))
            .border(2.dp, Color(0xFF2EA4FF).copy(alpha = if (active) 0.86f else 0.58f), CircleShape)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(if (active) DeckIcon.Pause else DeckIcon.Play, StreamTextPrimary, Modifier.size((size.value * 0.40f).dp))
    }
}

@Composable
private fun PulseRadioArtwork(station: RadioStation?, modifier: Modifier = Modifier, active: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF080A0F))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.18f else 0.08f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.pulseradio_placeholder_icon),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun radioStationListSubtitle(station: RadioStation): String =
    buildList {
        val codec = station.codec.trim().uppercase(Locale.US)
        when {
            codec.isNotBlank() -> add(codec)
            station.bitrate > 0 -> add("${station.bitrate} kbps")
        }
        if (station.votes > 0) add("${station.votes} votes")
        if (isEmpty()) station.countryCode.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" \u00B7 ").ifBlank { "Internet radio" }

private fun RadioStation.matchesPulseRadioSearch(needle: String): Boolean {
    if (needle.isBlank()) return true
    val metadata = normalizedMetadata()
    return listOf(
        name,
        country,
        countryCode,
        language,
        metadata.languageLabel,
        metadata.genre.discoveryLabel,
        metadata.contentType.discoveryLabel,
        codec,
        tags,
    ).joinToString(" ").normalizedSearchText().contains(needle)
}

@Composable
private fun PulseRadioHero(
    country: RadioCountry?,
    fallbackCountryCode: String,
    nameQuery: String,
    stationCount: Int,
    favoriteStationCount: Int,
    loading: Boolean,
    offlineMode: Boolean,
    activeStation: RadioStation?,
    streamPolicy: StreamingDataPolicy,
    onNameQuery: (String) -> Unit,
    onChooseCountry: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
) {
    val countryCode = (country?.isoCode ?: fallbackCountryCode).uppercase(Locale.US).take(2)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF17384A),
                        Color(0xFF10131A),
                        Color(0xFF061015),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF5DD7FF).copy(alpha = 0.13f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    val color = Color.White.copy(alpha = 0.84f)
                    drawCircle(color.copy(alpha = 0.16f), radius = size.minDimension * 0.44f)
                    drawCircle(color.copy(alpha = 0.22f), radius = size.minDimension * 0.30f)
                    drawLine(color, Offset(size.width * 0.50f, size.height * 0.20f), Offset(size.width * 0.50f, size.height * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color.copy(alpha = 0.72f), Offset(size.width * 0.30f, size.height * 0.42f), Offset(size.width * 0.70f, size.height * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color.copy(alpha = 0.56f), Offset(size.width * 0.25f, size.height * 0.58f), Offset(size.width * 0.75f, size.height * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                Text(countryCode, color = StreamTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PulseRadio", color = StreamTextPrimary, fontSize = 23.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (offlineMode) "Online streams paused" else country?.name ?: "Choose a country", color = StreamTextSecondary, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
            PulseRadioHeroIconButton(DeckIcon.Compass, active = true, onClick = onChooseCountry, enabled = true)
            Spacer(Modifier.width(6.dp))
            PulseRadioHeroIconButton(if (offlineMode) DeckIcon.StreamOffline else if (loading) DeckIcon.Timer else DeckIcon.StreamReplace, active = false, onClick = onRefresh, enabled = !loading && !offlineMode)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PulseRadioMetricPill(if (loading) "..." else stationCount.toString(), "stations", Modifier.weight(1f))
            PulseRadioMetricPill(favoriteStationCount.toString(), "saved", Modifier.weight(1f))
            PulseRadioMetricPill(countryCode, "country", Modifier.weight(1f))
        }
        activeStation?.let {
            Text("Live: ${it.name}", color = Color(0xFF5DD7FF), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        streamPolicy.maxAudioBitrateKbps?.let { cap ->
            Text("Data Saver ranks streams at ${cap} kbps or lower first", color = StreamTextMuted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PulseRadioTextField(
                value = nameQuery,
                placeholder = "Station name",
                modifier = Modifier.weight(1f),
                onValue = onNameQuery,
            )
            PulseRadioHeroIconButton(if (loading) DeckIcon.Timer else DeckIcon.Search, active = nameQuery.isNotBlank(), onClick = onSearch, enabled = !loading && !offlineMode)
        }
    }
}

@Composable
private fun PulseRadioHeroIconButton(icon: DeckIcon, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember(icon, active, enabled) { MutableInteractionSource() }
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.15f else 0.07f), CircleShape)
            .then(if (enabled) Modifier.pressScaleEffect(interactionSource).clickable(interactionSource = interactionSource, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = if (enabled) 0.88f else 0.32f), Modifier.size(18.dp))
    }
}

@Composable
private fun PulseRadioMetricPill(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(15.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(value, color = StreamTextPrimary, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = StreamTextMuted, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PulseRadioNowPlayingCard(station: RadioStation, favorite: Boolean, onFavorite: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF5DD7FF).copy(alpha = 0.10f))
            .border(1.dp, Color(0xFF5DD7FF).copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF5DD7FF).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(DeckIcon.Signal, Color.White, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = StreamTextPrimary, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(radioStationMeta(station), color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PulseRadioSmallIconButton(DeckIcon.Heart, selected = favorite, onClick = onFavorite)
        IconTap(DeckIcon.Pause, onPlay, 34.dp)
    }
}

@Composable
private fun PulseRadioFavoriteStationCard(station: RadioStation, active: Boolean, favorite: Boolean, onFavorite: () -> Unit, onPlay: () -> Unit) {
    val interactionSource = remember(station.discoveryKey(), active, favorite) { MutableInteractionSource() }
    Row(
        Modifier
            .width(260.dp)
            .height(82.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) Color(0xFF5DD7FF).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.16f else 0.07f), RoundedCornerShape(22.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (active) Color(0xFF5DD7FF).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            PulseIcon(if (active) DeckIcon.Signal else DeckIcon.StreamRadio, Color.White, Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = StreamTextPrimary, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(radioQualityLabel(station.codec, station.bitrate), color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Text("${station.votes.coerceAtLeast(0)} votes", color = StreamTextMuted, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PulseRadioSmallIconButton(DeckIcon.Heart, selected = favorite, onClick = onFavorite)
    }
}

@Composable
private fun PulseRadioSectionHeader(title: String, subtitle: String, trailing: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = StreamTextPrimary, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            trailing,
            color = StreamTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PulseRadioStationFilterRow(
    stations: List<RadioStation>,
    favoriteStationKeys: Set<String>,
    recentStationKeys: List<String>,
    streamPolicy: StreamingDataPolicy,
    selected: RadioStationFilter,
    onFilter: (RadioStationFilter) -> Unit,
) {
    val recentStationKeySet = remember(recentStationKeys) { recentStationKeys.toSet() }
    val savedCount = remember(stations, favoriteStationKeys) { stations.count { it.isFavorite(favoriteStationKeys) } }
    val recentCount = remember(stations, recentStationKeySet) { stations.count { it.discoveryKey() in recentStationKeySet } }
    val lowDataCount = remember(stations, streamPolicy) { stations.count { it.matchesLowDataPolicy(streamPolicy) } }
    val reliableCount = remember(stations) { stations.count { it.normalizedMetadata().reliability.isPositiveReliability } }
    val qualityCount = remember(stations) { stations.count { it.bitrate > 0 } }
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StreamFilterChip(
                label = "Discover",
                count = stations.size,
                icon = DeckIcon.Compass,
                active = selected == RadioStationFilter.Popular,
                onClick = { onFilter(RadioStationFilter.Popular) },
            )
        }
        item {
            StreamFilterChip(
                label = "Saved",
                count = savedCount,
                icon = DeckIcon.Heart,
                active = selected == RadioStationFilter.Saved,
                onClick = { onFilter(RadioStationFilter.Saved) },
            )
        }
        if (recentCount > 0 || selected == RadioStationFilter.Recent) {
            item {
                StreamFilterChip(
                    label = "Recent",
                    count = recentCount,
                    icon = DeckIcon.StreamRecent,
                    active = selected == RadioStationFilter.Recent,
                    onClick = { onFilter(RadioStationFilter.Recent) },
                )
            }
        }
        item {
            StreamFilterChip(
                label = "Low Data",
                count = lowDataCount,
                icon = DeckIcon.StreamOffline,
                active = selected == RadioStationFilter.LowData,
                onClick = { onFilter(RadioStationFilter.LowData) },
            )
        }
        if (reliableCount > 0 || selected == RadioStationFilter.Reliable) {
            item {
                StreamFilterChip(
                    label = "Reliable",
                    count = reliableCount,
                    icon = DeckIcon.Signal,
                    active = selected == RadioStationFilter.Reliable,
                    onClick = { onFilter(RadioStationFilter.Reliable) },
                )
            }
        }
        item {
            StreamFilterChip(
                label = "Quality",
                count = qualityCount,
                icon = DeckIcon.Signal,
                active = selected == RadioStationFilter.Quality,
                onClick = { onFilter(RadioStationFilter.Quality) },
            )
        }
        item {
            StreamFilterChip(
                label = "A-Z",
                count = stations.size,
                icon = DeckIcon.Bars,
                active = selected == RadioStationFilter.Name,
                onClick = { onFilter(RadioStationFilter.Name) },
            )
        }
    }
}

@Composable
private fun PulseRadioDiscoveryFacets(
    genres: List<RadioDiscoveryFacet<RadioGenre>>,
    contentTypes: List<RadioDiscoveryFacet<RadioContentType>>,
    languages: List<RadioDiscoveryFacet<String>>,
    qualities: List<RadioDiscoveryFacet<RadioQualityTier>>,
    reliabilities: List<RadioDiscoveryFacet<RadioReliability>>,
    selectedGenre: RadioGenre?,
    selectedContentType: RadioContentType?,
    selectedLanguageKey: String?,
    selectedQualityTier: RadioQualityTier?,
    selectedReliability: RadioReliability?,
    onGenre: (RadioGenre?) -> Unit,
    onContentType: (RadioContentType?) -> Unit,
    onLanguage: (String?) -> Unit,
    onQualityTier: (RadioQualityTier?) -> Unit,
    onReliability: (RadioReliability?) -> Unit,
) {
    if (genres.isEmpty() && contentTypes.isEmpty() && languages.isEmpty() && qualities.isEmpty() && reliabilities.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PulseRadioFacetRow("Genres", genres, selectedGenre, DeckIcon.Tag, onGenre)
        PulseRadioFacetRow("Programs", contentTypes, selectedContentType, DeckIcon.StreamRadio, onContentType)
        PulseRadioFacetRow("Languages", languages, selectedLanguageKey, DeckIcon.Comment, onLanguage)
        PulseRadioFacetRow("Quality", qualities, selectedQualityTier, DeckIcon.Signal, onQualityTier)
        PulseRadioFacetRow("Reliability", reliabilities, selectedReliability, DeckIcon.Check, onReliability)
    }
}

@Composable
private fun <T> PulseRadioFacetRow(
    title: String,
    facets: List<RadioDiscoveryFacet<T>>,
    selected: T?,
    icon: DeckIcon,
    onSelected: (T?) -> Unit,
) {
    if (facets.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                StreamFilterChip(
                    label = "All",
                    count = facets.sumOf { it.count },
                    icon = DeckIcon.Grid,
                    active = selected == null,
                    onClick = { onSelected(null) },
                )
            }
            items(facets, key = { "$title-${it.key}" }) { facet ->
                StreamFilterChip(
                    label = facet.label,
                    count = facet.count,
                    icon = icon,
                    active = selected == facet.value,
                    onClick = { onSelected(if (selected == facet.value) null else facet.value) },
                )
            }
        }
    }
}

private fun RadioStationFilter.radioSubtitle(savedCount: Int, recentCount: Int, streamPolicy: StreamingDataPolicy): String =
    when (this) {
        RadioStationFilter.Popular -> if (streamPolicy.maxAudioBitrateKbps != null) "Ranked for Data Saver and listener trust" else "Ranked by listener votes"
        RadioStationFilter.Saved -> if (savedCount == 0) "No saved stations yet" else "Only saved stations"
        RadioStationFilter.Recent -> if (recentCount == 0) "No recent stations in this list" else "Stations played on this device"
        RadioStationFilter.LowData -> streamPolicy.maxAudioBitrateKbps?.let { "Known streams at $it kbps or lower" } ?: "Known lower-bitrate streams"
        RadioStationFilter.Quality -> "Highest bitrate first"
        RadioStationFilter.Reliable -> "Recently checked working stations"
        RadioStationFilter.Name -> "Alphabetical station list"
    }

@Composable
private fun PulseRadioTextField(value: String, placeholder: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(21.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = StreamTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isBlank()) Text(placeholder, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            },
        )
    }
}

@Composable
private fun PulseRadioStationRow(station: RadioStation, active: Boolean, favorite: Boolean, onFavorite: () -> Unit, onPlay: () -> Unit) {
    val interactionSource = remember(station.discoveryKey(), active, favorite) { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    if (active) {
                        listOf(Color(0xFF17495C).copy(alpha = 0.72f), Color(0xFF0D151B).copy(alpha = 0.96f))
                    } else if (favorite) {
                        listOf(Color(0xFF133644).copy(alpha = 0.54f), Color.Black.copy(alpha = 0.17f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.075f), Color.Black.copy(alpha = 0.14f))
                    },
                ),
            )
            .border(1.dp, Color.White.copy(alpha = if (active || favorite) 0.16f else 0.07f), RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) Color(0xFF5DD7FF).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.09f))
                    .border(1.dp, Color.White.copy(alpha = if (active) 0.18f else 0.08f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(if (active) DeckIcon.Signal else DeckIcon.StreamRadio, Color.White, Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active) {
                        PulseRadioLiveBadge("LIVE")
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(station.name, color = StreamTextPrimary, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Text(radioStationMeta(station), color = StreamTextSecondary, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                val discoverySummary = radioDiscoverySummary(station).ifBlank { radioTagsPreview(station.tags) }
                if (discoverySummary.isNotBlank()) {
                    Text(discoverySummary, color = StreamTextMuted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            PulseRadioSmallIconButton(DeckIcon.Heart, selected = favorite, onClick = onFavorite)
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (active) Color(0xFF5DD7FF).copy(alpha = 0.24f) else Color.White.copy(alpha = 0.11f))
                    .border(1.dp, Color.White.copy(alpha = if (active) 0.18f else 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PulseIcon(if (active) DeckIcon.Pause else DeckIcon.Play, Color.White, Modifier.size(19.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PulseRadioInfoChip(radioQualityLabel(station.codec, station.bitrate), Modifier.weight(1f))
            PulseRadioInfoChip(radioReliabilityOrVotesLabel(station), Modifier.weight(1f))
            PulseRadioInfoChip(radioCategoryOrCountryLabel(station), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PulseRadioLiveBadge(label: String) {
    Text(
        label,
        color = Color(0xFF071014),
        fontSize = 8.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF5DD7FF))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun PulseRadioInfoChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.ifBlank { "Live" }, color = StreamTextSecondary, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PulseRadioStatusRow(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(StreamGlassFill)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Info, Color.White.copy(alpha = 0.72f), Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, color = StreamTextSecondary, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PulseRadioSmallIconButton(icon: DeckIcon, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember(icon, selected) { MutableInteractionSource() }
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.16f else 0.06f), CircleShape)
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PulseIcon(icon, Color.White.copy(alpha = if (selected) 0.96f else 0.68f), Modifier.size(15.dp))
    }
}

@Composable
private fun PulseRadioCountryPicker(
    countries: List<RadioCountry>,
    selectedCountryCode: String,
    favoriteCountryCodes: Set<String>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onCountry: (RadioCountry) -> Unit,
    onToggleFavorite: (RadioCountry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleCountries = remember(countries, selectedCountryCode, favoriteCountryCodes, query) {
        val needle = query.normalizedSearchText()
        countries
            .filter { country ->
                needle.isBlank() ||
                    country.name.normalizedSearchText().contains(needle) ||
                    country.isoCode.normalizedSearchText().contains(needle)
            }
            .sortedWith(
                compareByDescending<RadioCountry> { it.isoCode == selectedCountryCode.uppercase(Locale.US) }
                    .thenByDescending { it.isoCode in favoriteCountryCodes }
                    .thenByDescending { it.stationCount }
                    .thenBy { it.name.lowercase(Locale.US) },
            )
            .take(80)
    }
    BasicInfoModal(
        title = "Choose Country",
        subtitle = if (favoriteCountryCodes.isEmpty()) "Tap star to pin countries to the top" else "${favoriteCountryCodes.size} favorite countries pinned first",
        onDismiss = onDismiss,
    ) {
        PulseRadioTextField(
            value = query,
            placeholder = "Search countries",
            modifier = Modifier.fillMaxWidth(),
            onValue = { query = it },
        )
        Spacer(Modifier.height(12.dp))
        if (loading && countries.isEmpty()) {
            PulseRadioStatusRow("Loading country list")
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleCountries, key = { it.isoCode }) { country ->
                    PulseRadioCountryPickerRow(
                        country = country,
                        selected = country.isoCode == selectedCountryCode.uppercase(Locale.US),
                        favorite = country.isoCode in favoriteCountryCodes,
                        onCountry = { onCountry(country) },
                        onFavorite = { onToggleFavorite(country) },
                    )
                }
                if (visibleCountries.isEmpty()) {
                    item { PulseRadioStatusRow("No matching countries") }
                }
            }
        }
    }
}

@Composable
private fun PulseRadioCountryPickerRow(country: RadioCountry, selected: Boolean, favorite: Boolean, onCountry: () -> Unit, onFavorite: () -> Unit) {
    val interactionSource = remember(country.isoCode, selected, favorite) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF5DD7FF).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.14f else 0.06f), RoundedCornerShape(18.dp))
            .pressScaleEffect(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onCountry)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(country.isoCode, color = StreamTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(country.name, color = StreamTextPrimary, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${country.stationCount} stations", color = StreamTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (selected) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF5DD7FF)))
            Spacer(Modifier.width(8.dp))
        }
        PulseRadioSmallIconButton(DeckIcon.Heart, selected = favorite, onClick = onFavorite)
    }
}

private data class RadioDiscoveryFacet<T>(
    val value: T,
    val key: String,
    val label: String,
    val count: Int,
)

private fun List<RadioStation>.radioGenreFacets(): List<RadioDiscoveryFacet<RadioGenre>> =
    map { it.normalizedMetadata().genre }
        .filter { it.isDisplayableDiscoveryGenre }
        .groupingBy { it }
        .eachCount()
        .map { (genre, count) -> RadioDiscoveryFacet(genre, genre.name, genre.discoveryLabel, count) }
        .sortedWith(compareByDescending<RadioDiscoveryFacet<RadioGenre>> { it.count }.thenBy { it.label })
        .take(8)

private fun List<RadioStation>.radioContentFacets(): List<RadioDiscoveryFacet<RadioContentType>> =
    map { it.normalizedMetadata().contentType }
        .filter { it.isDisplayableDiscoveryContent }
        .groupingBy { it }
        .eachCount()
        .map { (type, count) -> RadioDiscoveryFacet(type, type.name, type.discoveryLabel, count) }
        .sortedWith(compareByDescending<RadioDiscoveryFacet<RadioContentType>> { it.count }.thenBy { it.label })
        .take(8)

private fun List<RadioStation>.radioLanguageFacets(): List<RadioDiscoveryFacet<String>> =
    map { it.normalizedMetadata().languageLabel }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .map { (label, count) -> RadioDiscoveryFacet(label.normalizedSearchText(), label.normalizedSearchText(), label, count) }
        .sortedWith(compareByDescending<RadioDiscoveryFacet<String>> { it.count }.thenBy { it.label })
        .take(8)

private fun List<RadioStation>.radioQualityFacets(): List<RadioDiscoveryFacet<RadioQualityTier>> =
    map { it.normalizedMetadata().qualityTier }
        .filter { it.isDisplayableDiscoveryQuality }
        .groupingBy { it }
        .eachCount()
        .map { (tier, count) -> RadioDiscoveryFacet(tier, tier.name, tier.discoveryLabel, count) }
        .sortedBy { it.value.ordinal }

private fun List<RadioStation>.radioReliabilityFacets(): List<RadioDiscoveryFacet<RadioReliability>> =
    map { it.normalizedMetadata().reliability }
        .filter { it.isDisplayableDiscoveryReliability }
        .groupingBy { it }
        .eachCount()
        .map { (reliability, count) -> RadioDiscoveryFacet(reliability, reliability.name, reliability.discoveryLabel, count) }
        .sortedWith(compareByDescending<RadioDiscoveryFacet<RadioReliability>> { it.value.reliabilityRank() }.thenBy { it.label })

private fun radioRecommendedStationComparator(
    favoriteStationKeys: Set<String>,
    recentStationKeys: Set<String>,
    streamPolicy: StreamingDataPolicy,
): Comparator<RadioStation> =
    compareByDescending<RadioStation> {
        it.radioDiscoveryScore(
            policy = streamPolicy,
            favorite = it.isFavorite(favoriteStationKeys),
            recent = it.discoveryKey() in recentStationKeys,
        )
    }
        .thenByDescending { it.votes }
        .thenByDescending { it.clickCount }
        .thenBy { it.name.lowercase(Locale.US) }

private fun RadioReliability.reliabilityRank(): Int =
    when (this) {
        RadioReliability.RecentlySuccessful -> 4
        RadioReliability.Stable -> 3
        RadioReliability.Unverified -> 2
        RadioReliability.Unknown -> 1
        RadioReliability.RecentlyFailed -> 0
    }

private fun selectedRadioDiscoverySummary(
    genre: RadioGenre?,
    contentType: RadioContentType?,
    languageLabel: String,
    qualityTier: RadioQualityTier?,
    reliability: RadioReliability?,
): String =
    buildList {
        genre?.discoveryLabel?.let(::add)
        contentType?.discoveryLabel?.let(::add)
        languageLabel.takeIf { it.isNotBlank() }?.let(::add)
        qualityTier?.discoveryLabel?.let(::add)
        reliability?.discoveryLabel?.let(::add)
    }.joinToString(" / ")

private fun radioDiscoverySummary(station: RadioStation): String {
    val metadata = station.normalizedMetadata()
    return buildList {
        if (metadata.genre.isDisplayableDiscoveryGenre) add(metadata.genre.discoveryLabel)
        if (metadata.contentType.isDisplayableDiscoveryContent) add(metadata.contentType.discoveryLabel)
        metadata.languageLabel.takeIf { it.isNotBlank() }?.let(::add)
        if (metadata.reliability.isPositiveReliability) add(metadata.reliability.discoveryLabel)
    }.distinct().take(4).joinToString(" / ")
}

private fun radioReliabilityOrVotesLabel(station: RadioStation): String {
    val reliability = station.normalizedMetadata().reliability
    return if (reliability.isDisplayableDiscoveryReliability) {
        reliability.discoveryLabel
    } else {
        "${station.votes.coerceAtLeast(0)} votes"
    }
}

private fun radioCategoryOrCountryLabel(station: RadioStation): String {
    val metadata = station.normalizedMetadata()
    return when {
        metadata.genre.isDisplayableDiscoveryGenre -> metadata.genre.discoveryLabel
        metadata.contentType.isDisplayableDiscoveryContent -> metadata.contentType.discoveryLabel
        metadata.languageLabel.isNotBlank() -> metadata.languageLabel
        else -> station.countryCode.ifBlank { "Radio" }
    }
}

private inline fun <reified T : Enum<T>> String.radioEnumValueOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()

@Preview(name = "PulseRadio Ethiopia Discovery", showBackground = true, backgroundColor = 0xFF050607)
@Composable
private fun PulseRadioEthiopiaPreview() {
    val stations = remember { sampleEthiopiaRadioStations() }
    PulseRadioScreen(
        countries = listOf(RadioCountry("Ethiopia", "ET", stations.size)),
        stations = stations,
        countryCode = "ET",
        nameQuery = "",
        stationFilter = RadioStationFilter.Popular,
        favoriteCountryCodes = setOf("ET"),
        favoriteStationKeys = stations.take(3).map { it.favoriteKey() }.toSet(),
        recentStationKeys = stations.take(2).map { it.discoveryKey() },
        streamPolicy = StreamingDataPolicy.Unrestricted,
        loadingCountries = false,
        loadingStations = false,
        error = null,
        activeStreamUrl = stations.first().streamUrl,
        offlineMode = false,
        onBack = {},
        onNameQuery = {},
        onStationFilter = {},
        onSearch = {},
        onCountry = {},
        onToggleCountryFavorite = {},
        onToggleStationFavorite = {},
        onPlayStation = {},
    )
}

private fun sampleEthiopiaRadioStations(): List<RadioStation> =
    listOf(
        RadioStation(
            stationUuid = "preview-taem",
            name = "Taem Radio",
            streamUrl = "https://preview.invalid/taem",
            bitrate = 128,
            codec = "MP3",
            language = "Amharic",
            tags = "ethiopian,jazz,music",
            votes = 1231,
            country = "Ethiopia",
            countryCode = "ET",
            homepage = "",
            favicon = "",
            clickCount = 120,
            lastCheckOk = true,
        ),
        RadioStation(
            stationUuid = "preview-hope",
            name = "Hope Radio",
            streamUrl = "https://preview.invalid/hope",
            bitrate = 96,
            codec = "MP3",
            language = "Amharic",
            tags = "ethiopian,community radio,gospel",
            votes = 876,
            country = "Ethiopia",
            countryCode = "ET",
            homepage = "",
            favicon = "",
            clickCount = 98,
            lastCheckOk = true,
        ),
        RadioStation(
            stationUuid = "preview-abugida",
            name = "Abugida Radio",
            streamUrl = "https://preview.invalid/abugida",
            bitrate = 128,
            codec = "MP3",
            language = "Amharic",
            tags = "ethiopian,talk,culture",
            votes = 654,
            country = "Ethiopia",
            countryCode = "ET",
            homepage = "",
            favicon = "",
            clickCount = 74,
            lastCheckOk = true,
        ),
        RadioStation(
            stationUuid = "preview-tigray",
            name = "Voice of Tigray",
            streamUrl = "https://preview.invalid/tigray",
            bitrate = 64,
            codec = "MP3",
            language = "Tigrinya",
            tags = "ethiopian,news,local information",
            votes = 542,
            country = "Ethiopia",
            countryCode = "ET",
            homepage = "",
            favicon = "",
            clickCount = 52,
            lastCheckOk = true,
        ),
        RadioStation(
            stationUuid = "preview-nahoo",
            name = "Nahoo Radio",
            streamUrl = "https://preview.invalid/nahoo",
            bitrate = 128,
            codec = "MP3",
            language = "Amharic",
            tags = "ethiopian,pop,music",
            votes = 428,
            country = "Ethiopia",
            countryCode = "ET",
            homepage = "",
            favicon = "",
            clickCount = 38,
            lastCheckOk = true,
        ),
        RadioStation(
            stationUuid = "preview-fana",
            name = "Fana Broadcasting",
            streamUrl = "https://preview.invalid/fana",
            bitrate = 96,
            codec = "MP3",
            language = "Amharic",
            tags = "ethiopian,news,public radio",
            votes = 369,
            country = "Ethiopia",
            countryCode = "ET",
            homepage = "",
            favicon = "",
            clickCount = 34,
            lastCheckOk = true,
        ),
    )

