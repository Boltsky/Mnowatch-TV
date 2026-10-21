package com.example.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.data.local.SearchHistoryDao
import com.example.data.local.SearchHistoryEntity
import com.example.domain.model.Episode
import com.example.domain.model.MediaItem
import com.example.domain.model.Season
import com.example.domain.model.SeriesDetail
import com.example.domain.repository.MovieRepository
import com.example.domain.repository.SearchRepository
import com.example.domain.repository.SeriesRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvMediaCard
import com.example.ui.components.TvSearchKeyboard
import com.example.ui.components.TvTopBar
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchFocusBorder
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextMuted
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val suggestedTitles: List<MediaItem> = emptyList(),
    val hasSearched: Boolean = false,
    val seriesDetails: Map<String, SeriesDetail> = emptyMap(),
    val loadingSeriesIds: Set<String> = emptySet(),
    val openedSeasonData: Pair<MediaItem, Season>? = null,
    val recentSearches: List<String> = emptyList()
)

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val movieRepository: MovieRepository? = null,
    private val seriesRepository: SeriesRepository? = null,
    private val searchHistoryDao: SearchHistoryDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private var searchJob: Job? = null

    val popularTags = listOf("2026", "2025", "VJ Junior", "VJ Emmy", "VJ ICEP", "VJ Banks", "Action", "Series", "Horror", "Love Story", "Sci-Fi", "Comedy")

    init {
        loadInitialSuggestions()
        loadRecentSearches()
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            searchHistoryDao?.getRecentSearches()?.collect { list ->
                _uiState.value = _uiState.value.copy(recentSearches = list)
            }
        }
    }

    private fun loadInitialSuggestions() {
        viewModelScope.launch {
            val res = movieRepository?.getLatestMovies(null)
            val list = res?.getOrNull() ?: emptyList()
            if (list.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(suggestedTitles = list)
            }
        }
    }

    fun appendChar(c: Char) {
        val newQuery = _uiState.value.query + c
        updateQuery(newQuery)
    }

    fun backspace() {
        if (_uiState.value.query.isNotEmpty()) {
            val newQuery = _uiState.value.query.dropLast(1)
            updateQuery(newQuery)
        }
    }

    fun clear() {
        _uiState.value = _uiState.value.copy(
            query = "",
            results = emptyList(),
            hasSearched = false,
            openedSeasonData = null
        )
    }

    fun setQuery(query: String) {
        updateQuery(query)
        triggerImmediateSearch()
    }

    fun selectRecentSearch(query: String) {
        setQuery(query)
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            searchHistoryDao?.deleteSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            searchHistoryDao?.clearHistory()
        }
    }

    private fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(300)
                executeSearch(query.trim())
            }
        } else {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false, hasSearched = false)
        }
    }

    fun triggerImmediateSearch() {
        val q = _uiState.value.query.trim()
        if (q.isNotEmpty()) {
            viewModelScope.launch { executeSearch(q) }
        }
    }

    private suspend fun executeSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            viewModelScope.launch {
                searchHistoryDao?.insertSearch(
                    SearchHistoryEntity(query = trimmed, timestamp = System.currentTimeMillis())
                )
            }
        }
        _uiState.value = _uiState.value.copy(isSearching = true)
        val res = searchRepository.searchContent(query)
        val searchResults = res.getOrDefault(emptyList())
        _uiState.value = _uiState.value.copy(
            isSearching = false,
            results = searchResults,
            hasSearched = true
        )
        // Automatically load seasons for any series in search results
        for (item in searchResults) {
            if (item.isSeries) {
                loadSeriesDetails(item)
            }
        }
    }

    fun loadSeriesDetails(item: MediaItem) {
        if (!item.isSeries) return
        if (_uiState.value.seriesDetails.containsKey(item.id)) return
        if (_uiState.value.loadingSeriesIds.contains(item.id)) return

        _uiState.value = _uiState.value.copy(
            loadingSeriesIds = _uiState.value.loadingSeriesIds + item.id
        )
        viewModelScope.launch {
            val res = seriesRepository?.getSeriesDetails(item.id)
            val detail = res?.getOrNull()
            _uiState.value = _uiState.value.let { cur ->
                val newDetails = if (detail != null) cur.seriesDetails + (item.id to detail) else cur.seriesDetails
                val updatedOpened = cur.openedSeasonData?.let { (openedSeries, openedSeason) ->
                    if (openedSeries.id == item.id && detail != null) {
                        val freshSeason = detail.seasons.find { it.number == openedSeason.number } ?: openedSeason
                        Pair(openedSeries, freshSeason)
                    } else {
                        cur.openedSeasonData
                    }
                } ?: cur.openedSeasonData
                cur.copy(
                    loadingSeriesIds = cur.loadingSeriesIds - item.id,
                    seriesDetails = newDetails,
                    openedSeasonData = updatedOpened
                )
            }
        }
    }

    fun openSeason(series: MediaItem, season: Season) {
        val currentDetail = _uiState.value.seriesDetails[series.id]
        val resolvedSeason = currentDetail?.seasons?.find { it.number == season.number } ?: season
        _uiState.value = _uiState.value.copy(openedSeasonData = Pair(series, resolvedSeason))
        if (!_uiState.value.seriesDetails.containsKey(series.id)) {
            loadSeriesDetails(series)
        }
    }

    fun closeSeason() {
        _uiState.value = _uiState.value.copy(openedSeasonData = null)
    }
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigate: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onPlayEpisode: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    // Handle Back button to close season episode view if open
    if (state.openedSeasonData != null) {
        BackHandler {
            viewModel.closeSeason()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
    ) {
        TvTopBar(
            currentRoute = "search",
            onNavigate = onNavigate
        )

        // Overlay: Episodes view within the opened season
        if (state.openedSeasonData != null) {
            val (series, season) = state.openedSeasonData!!
            val fullDetail = state.seriesDetails[series.id]
            val activeSeason = fullDetail?.seasons?.find { it.number == season.number } ?: season
            val displayEpisodes = activeSeason.episodes.sortedWith(
                compareBy<Episode> { if (it.number > 0) it.number else Int.MAX_VALUE }
                    .thenBy { it.id }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp, vertical = 12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvButton(
                            text = "Back to Results",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = { viewModel.closeSeason() },
                            isPrimary = false
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MunowatchGreen,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "SERIES",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = series.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "•  ${activeSeason.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MunowatchGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Select an episode to play in Full HD  (${displayEpisodes.size} Episodes available)",
                                color = MunowatchTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Direct link to full series details page
                    TvButton(
                        text = "Full Series Page",
                        icon = Icons.Default.Tv,
                        onClick = { onOpenDetails(series.id) },
                        isPrimary = false
                    )
                }

                // Season Switcher Tabs (if the series has multiple seasons)
                if (fullDetail != null && fullDetail.seasons.size > 1) {
                    val sortedSeasons = fullDetail.seasons.sortedBy { it.number }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedSeasons.forEach { s ->
                            val isSelected = s.number == activeSeason.number
                            val epCount = s.episodes.size
                            TvButton(
                                text = if (epCount > 0) "${s.title} ($epCount Ep)" else s.title,
                                onClick = { viewModel.openSeason(series, s) },
                                isPrimary = isSelected
                            )
                        }
                    }
                }

                if (displayEpisodes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.loadingSeriesIds.contains(series.id)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MunowatchGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Loading episodes for ${activeSeason.title}...",
                                    color = MunowatchTextPrimary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            Text(
                                text = "No episodes found for ${activeSeason.title}",
                                color = MunowatchTextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayEpisodes, key = { it.id }) { episode ->
                            SearchEpisodeCard(
                                episode = episode,
                                defaultPoster = series.posterUrl,
                                onClick = {
                                    val target = episode.streamUrl.takeIf {
                                        it.isNotBlank() && (it.contains("twolekede") || it.contains("mid=") || it.contains("preview.php") || it.contains("download.php"))
                                    } ?: Regex("""[?&]mid=([0-9]+)""").find(episode.streamUrl)?.let { "/twolekede?mid=${it.groupValues[1]}" }
                                      ?: episode.streamUrl.takeIf { it.isNotBlank() }
                                      ?: episode.id
                                    onPlayEpisode(target)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Main Search & Results Screen
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                // Left Column: Compact YouTube Android TV Search Panel (Input + Quick Tags + QWERTY Keypad + Recent Searches)
                Column(
                    modifier = Modifier
                        .width(312.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Search Input Field (YouTube TV Style)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1F1F1F),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (state.query.isNotEmpty()) MunowatchGreen else Color(0xFF383838)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (state.query.isNotEmpty()) MunowatchGreen else Color(0xFF9E9E9E),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.query.isEmpty()) {
                                    Text(
                                        text = "Search movies, series, VJ...",
                                        color = MunowatchTextMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                } else {
                                    Text(
                                        text = state.query,
                                        color = MunowatchTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "|",
                                        color = MunowatchGreen,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.alpha(cursorAlpha)
                                    )
                                }
                            }
                            if (state.query.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF9E9E9E),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { viewModel.clear() }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Search Suggestion Chips + Recent Searches
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (state.recentSearches.isNotEmpty()) {
                            items(state.recentSearches.take(4)) { recent ->
                                RecentSearchChip(
                                    text = recent,
                                    isSelected = state.query.equals(recent, ignoreCase = true),
                                    onClick = { viewModel.selectRecentSearch(recent) }
                                )
                            }
                        }
                        items(viewModel.popularTags) { tag ->
                            QuickSearchChip(
                                text = tag,
                                isSelected = state.query.equals(tag, ignoreCase = true),
                                onClick = { viewModel.setQuery(tag) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // YouTube Android TV Virtual QWERTY Keyboard
                    TvSearchKeyboard(
                        onCharAdded = { viewModel.appendChar(it) },
                        onBackspace = { viewModel.backspace() },
                        onClear = { viewModel.clear() },
                        onSearch = { viewModel.triggerImmediateSearch() }
                    )

                    // Under Keyboard: Recent Searches History
                    if (state.recentSearches.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MunowatchGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "RECENT SEARCHES",
                                    color = MunowatchTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = "Clear All",
                                color = Color(0xFF9E9E9E),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.clearRecentSearches() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.recentSearches.take(8).forEach { recentQuery ->
                                RecentSearchHistoryItem(
                                    query = recentQuery,
                                    onClick = { viewModel.selectRecentSearch(recentQuery) },
                                    onDelete = { viewModel.deleteRecentSearch(recentQuery) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Right Column: Search Results Grid (Spans the wide TV screen for maximum results)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val displayItems = if (state.hasSearched) state.results else state.suggestedTitles
                    val headerTitle = when {
                        state.hasSearched && state.results.isNotEmpty() ->
                            "RESULTS FOR \"${state.query}\" (${state.results.size})"
                        state.hasSearched && state.results.isEmpty() && !state.isSearching ->
                            "NO RESULTS FOUND"
                        else ->
                            "TRENDING & POPULAR"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        if (state.isSearching) {
                            CircularProgressIndicator(
                                color = MunowatchGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.hasSearched && state.results.isEmpty() && !state.isSearching) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No movies or series found matching \"${state.query}\".\nTry searching for a Veejay (e.g. \"VJ Junior\") or genre.",
                                color = MunowatchTextSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 135.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = displayItems,
                                key = { it.id },
                                span = { item ->
                                    if (item.isSeries) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                                }
                            ) { item ->
                                if (item.isSeries) {
                                    val detail = state.seriesDetails[item.id]
                                    val isLoadingDetail = state.loadingSeriesIds.contains(item.id)

                                    TvSeriesSearchCard(
                                        series = item,
                                        detail = detail,
                                        isLoadingDetail = isLoadingDetail,
                                        onOpenSeason = { season ->
                                            viewModel.openSeason(item, season)
                                        },
                                        onLoadDetails = {
                                            viewModel.loadSeriesDetails(item)
                                        },
                                        onOpenDetails = {
                                            onOpenDetails(item.id)
                                        }
                                    )
                                } else {
                                    TvMediaCard(
                                        item = item,
                                        onClick = { onOpenDetails(item.id) },
                                        cardWidth = null,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSeriesSearchCard(
    series: MediaItem,
    detail: SeriesDetail?,
    isLoadingDetail: Boolean,
    onOpenSeason: (Season) -> Unit,
    onLoadDetails: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) MunowatchSurfaceElevated else Color(0xFF181C22),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MunowatchFocusBorder else Color(0xFF2C323D)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource)
            .testTag("series_card_${series.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Poster thumbnail
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MunowatchSurfaceElevated,
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(2f / 3f)
                    .clickable { onOpenDetails() }
            ) {
                if (series.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = series.posterUrl,
                        contentDescription = series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MunowatchGreen,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Series Details & Seasons Row
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MunowatchGreen,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SERIES",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (series.vj.isNotBlank()) {
                        Surface(
                            color = MunowatchGreenLight.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MunowatchGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = series.vj,
                                color = MunowatchGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (series.year.isNotBlank()) {
                        Text(
                            text = series.year,
                            color = MunowatchTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val seasonsCount = detail?.seasons?.size ?: 0
                    if (seasonsCount > 0) {
                        Text(
                            text = "•  $seasonsCount ${if (seasonsCount == 1) "Season" else "Seasons"}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = series.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (series.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = series.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MunowatchTextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Seasons Buttons Row: Shows all seasons of the series
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SEASONS:",
                        color = MunowatchTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    if (isLoadingDetail) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MunowatchGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Loading seasons...",
                                color = MunowatchTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    } else if (detail != null && detail.seasons.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            detail.seasons.sortedBy { it.number }.forEach { season ->
                                val seasonText = if (season.episodes.isNotEmpty()) {
                                    "${season.title} (${season.episodes.size} Ep)"
                                } else {
                                    season.title
                                }
                                TvButton(
                                    text = seasonText,
                                    onClick = { onOpenSeason(season) },
                                    isPrimary = true
                                )
                            }
                        }
                    } else {
                        TvButton(
                            text = "Load Seasons",
                            onClick = onLoadDetails,
                            isPrimary = true
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    TvButton(
                        text = "Full Series Page",
                        icon = Icons.Default.Tv,
                        onClick = onOpenDetails,
                        isPrimary = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEpisodeCard(
    episode: Episode,
    defaultPoster: String = "",
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val posterUrl = episode.thumbnail.takeIf {
        it.isNotBlank() && !it.contains("logo") && !it.contains("icon")
    } ?: defaultPoster

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .testTag("search_episode_${episode.number}")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = if (isFocused) MunowatchGreenLight else MunowatchSurfaceElevated,
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) Color.White else MunowatchFocusBorder.copy(alpha = 0.35f)
        ),
        shadowElevation = if (isFocused) 8.dp else 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                if (posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MunowatchSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MunowatchGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Episode badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "EP ${episode.number}",
                        color = MunowatchGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Play icon overlay when focused
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MunowatchGreen,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Episode info footer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = episode.title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickSearchChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("quick_search_$text"),
        shape = RoundedCornerShape(14.dp),
        color = when {
            isFocused -> Color.White
            isSelected -> MunowatchGreen
            else -> Color(0xFF222222)
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = when {
                isFocused -> Color.White
                isSelected -> MunowatchGreen
                else -> Color(0xFF333333)
            }
        )
    ) {
        Text(
            text = text,
            color = when {
                isFocused -> Color.Black
                isSelected -> Color.White
                else -> Color(0xFFCCCCCC)
            },
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RecentSearchChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("recent_search_chip_$text"),
        shape = RoundedCornerShape(14.dp),
        color = when {
            isFocused -> Color.White
            isSelected -> MunowatchGreen
            else -> Color(0xFF1E2620)
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = when {
                isFocused -> Color.White
                isSelected -> MunowatchGreen
                else -> MunowatchGreen.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = if (isFocused) Color.Black else MunowatchGreen,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                color = when {
                    isFocused -> Color.Black
                    isSelected -> Color.White
                    else -> Color(0xFFEEEEEE)
                },
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RecentSearchHistoryItem(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("recent_history_$query"),
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) MunowatchSurfaceElevated else Color(0xFF1B1F24),
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = if (isFocused) MunowatchFocusBorder else Color(0xFF282F3A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = if (isFocused) MunowatchGreen else Color(0xFF8E99A8),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = query,
                    color = if (isFocused) Color.White else Color(0xFFD0D7DE),
                    fontSize = 12.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $query from history",
                    tint = if (isFocused) Color.White.copy(alpha = 0.85f) else Color(0xFF758195),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
