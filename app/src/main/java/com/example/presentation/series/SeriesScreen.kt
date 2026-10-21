package com.example.presentation.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.MediaCache
import com.example.domain.model.MediaItem
import com.example.domain.repository.SeriesRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvErrorView
import com.example.ui.components.TvLoadingIndicator
import com.example.ui.components.TvMediaCard
import com.example.ui.components.TvTopBar
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesUiState(
    val isLoading: Boolean = true,
    val series: List<MediaItem> = emptyList(),
    val errorMessage: String? = null,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val selectedYear: String? = null,
    val selectedCategory: String? = null,
    val selectedVj: String? = null,
    val isFilterOpen: Boolean = false,
    val availableYears: List<String> = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014"),
    val availableCategories: List<String> = listOf("All Genres", "Action", "Drama", "Comedy", "Horror", "Love Story", "Adventure", "Sci-Fi", "Thriller", "Crime", "Korean", "Animation"),
    val availableVjs: List<String> = listOf("All VJs", "VJ Junior", "VJ ICEP", "VJ Emmy", "VJ Banks", "VJ Shao Khan", "VJ Jingo", "VJ Ulio", "VJ Soul", "VJ Kevo", "VJ Mosco", "VJ Mark")
)

class SeriesViewModel(
    private val seriesRepository: SeriesRepository
) : ViewModel() {

    private val cached = MediaCache.getPopularSeries()

    private val _uiState = MutableStateFlow(
        SeriesUiState(
            isLoading = cached.isEmpty(),
            series = cached,
            canLoadMore = true
        )
    )
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var lastFetchId: String? = null
    private var currentPage: Int = 1
    private var filteredPage: Int = 1

    var scrollIndex: Int = 0
    var scrollOffset: Int = 0

    init {
        loadSeries(forceReload = false)
    }

    fun toggleFilterOpen() {
        val next = !_uiState.value.isFilterOpen
        _uiState.value = _uiState.value.copy(isFilterOpen = next)
    }

    fun selectYear(year: String?) {
        val current = _uiState.value.selectedYear
        val newYear = if (current == year) null else year
        _uiState.value = _uiState.value.copy(selectedYear = newYear)
        onFilterChanged()
    }

    fun selectCategory(category: String?) {
        val cleanCat = category?.takeIf { !it.equals("All Genres", ignoreCase = true) && !it.equals("All Categories", ignoreCase = true) }
        val current = _uiState.value.selectedCategory
        val newCat = if (current == cleanCat) null else cleanCat
        _uiState.value = _uiState.value.copy(selectedCategory = newCat)
        onFilterChanged()
    }

    fun selectVj(vj: String?) {
        val cleanVj = vj?.takeIf { !it.equals("All VJs", ignoreCase = true) }
        val current = _uiState.value.selectedVj
        val newVj = if (current == cleanVj) null else cleanVj
        _uiState.value = _uiState.value.copy(selectedVj = newVj)
        onFilterChanged()
    }

    fun clearAllFilters() {
        _uiState.value = _uiState.value.copy(
            selectedYear = null,
            selectedCategory = null,
            selectedVj = null,
            isFilterOpen = false
        )
        loadSeries(forceReload = true)
    }

    private fun onFilterChanged() {
        val s = _uiState.value
        val hasAnyFilter = s.selectedYear != null || s.selectedCategory != null || s.selectedVj != null
        if (!hasAnyFilter) {
            loadSeries(forceReload = false)
            return
        }
        fetchFilteredSeries(resetPage = true)
    }

    private fun fetchFilteredSeries(resetPage: Boolean) {
        val s = _uiState.value
        val year = s.selectedYear
        val category = s.selectedCategory
        val vj = s.selectedVj

        if (resetPage) {
            filterJob?.cancel()
            filteredPage = 1
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isLoadingMore = false,
                errorMessage = null
            )
        } else {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
        }

        val pageToFetch = filteredPage
        filterJob = viewModelScope.launch {
            try {
                val res = seriesRepository.getFilteredSeries(
                    year = year,
                    category = category,
                    vj = vj,
                    page = pageToFetch,
                    perPage = 20
                )
                val items = res.getOrDefault(emptyList()).map { it.copy(isSeries = true) }

                if (resetPage) {
                    filteredPage = 2
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        series = items,
                        canLoadMore = items.size >= 10,
                        errorMessage = if (items.isEmpty()) "No series found matching your selected filters." else null
                    )
                } else {
                    if (items.isNotEmpty()) {
                        filteredPage++
                        val currentList = _uiState.value.series
                        val newUnique = items.filter { item -> currentList.none { it.id == item.id } }
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            series = currentList + newUnique,
                            canLoadMore = items.size >= 10
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            canLoadMore = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = if (resetPage) "Unable to filter series. Please check your network." else null
                )
            }
        }
    }

    fun loadSeries(forceReload: Boolean = false) {
        loadJob?.cancel()
        filterJob?.cancel()
        lastFetchId = null
        currentPage = 1

        val cachedSeries = MediaCache.getPopularSeries()
        if (forceReload || (cachedSeries.isEmpty() && _uiState.value.series.isEmpty())) {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                canLoadMore = true,
                isLoadingMore = false
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                series = if (cachedSeries.isNotEmpty()) cachedSeries else _uiState.value.series,
                errorMessage = null,
                canLoadMore = true,
                isLoadingMore = false
            )
        }

        loadJob = viewModelScope.launch {
            val result = seriesRepository.getPopularSeries(null, 1)
            result.onSuccess { list ->
                if (list.isNotEmpty()) {
                    MediaCache.setPopularSeries(list)
                }
                lastFetchId = list.lastOrNull()?.id
                currentPage = 1
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    series = if (list.isNotEmpty()) list else _uiState.value.series,
                    canLoadMore = list.isNotEmpty()
                )

                val accumulated = (if (list.isNotEmpty()) list else _uiState.value.series).toMutableList()
                var currentLastId = lastFetchId

                for (pageIndex in 2..4) {
                    if (currentLastId == null) break
                    delay(400)
                    val nextRes = seriesRepository.getPopularSeries(currentLastId, pageIndex)
                    val nextList = nextRes.getOrDefault(emptyList())
                    if (nextList.isEmpty()) {
                        _uiState.value = _uiState.value.copy(canLoadMore = false)
                        break
                    }
                    val newUnique = nextList.filter { item -> accumulated.none { it.id == item.id } }
                    if (newUnique.isEmpty()) {
                        _uiState.value = _uiState.value.copy(canLoadMore = false)
                        break
                    }
                    accumulated.addAll(newUnique)
                    MediaCache.setPopularSeries(accumulated)
                    currentLastId = newUnique.lastOrNull()?.id ?: nextList.last().id
                    lastFetchId = currentLastId
                    currentPage = pageIndex
                    _uiState.value = _uiState.value.copy(
                        series = accumulated.toList(),
                        canLoadMore = true
                    )
                }
            }.onFailure {
                val currentSeries = _uiState.value.series
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (currentSeries.isEmpty()) "Unable to load translated series catalog." else null
                )
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || currentState.isLoading || !currentState.canLoadMore) return

        val hasActiveFilter = currentState.selectedYear != null || currentState.selectedCategory != null || currentState.selectedVj != null
        if (hasActiveFilter) {
            fetchFilteredSeries(resetPage = false)
            return
        }

        val currentFetchId = lastFetchId ?: currentState.series.lastOrNull()?.id ?: return
        val nextPage = currentPage + 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val result = seriesRepository.getPopularSeries(currentFetchId, nextPage)
            val nextList = result.getOrDefault(emptyList())

            if (nextList.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false, canLoadMore = false)
            } else {
                val existing = _uiState.value.series
                val newUnique = nextList.filter { item -> existing.none { it.id == item.id } }
                if (newUnique.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, canLoadMore = false)
                } else {
                    lastFetchId = newUnique.lastOrNull()?.id ?: nextList.last().id
                    currentPage = nextPage
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        series = existing + newUnique,
                        canLoadMore = true
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onNavigate: (String) -> Unit,
    onOpenSeriesDetails: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = viewModel.scrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                viewModel.scrollIndex = idx
                viewModel.scrollOffset = off
            }
    }

    LaunchedEffect(gridState, state.series.size, state.isLoadingMore, state.canLoadMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    state.series.isNotEmpty() &&
                    lastVisibleIndex >= state.series.size - 4 &&
                    !state.isLoadingMore &&
                    !state.isLoading &&
                    state.canLoadMore
                ) {
                    viewModel.loadMore()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
    ) {
        TvTopBar(
            currentRoute = "series",
            onNavigate = onNavigate
        )

        val hasAnyFilter = state.selectedYear != null || state.selectedCategory != null || state.selectedVj != null

        // Header and Action Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TRANSLATED SERIES & TV SHOWS",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                val filterDesc = buildString {
                    if (state.series.isNotEmpty()) {
                        append("${state.series.size} series loaded")
                    }
                    if (state.selectedYear != null) append(" • Year ${state.selectedYear}")
                    if (state.selectedCategory != null) append(" • ${state.selectedCategory}")
                    if (state.selectedVj != null) append(" • ${state.selectedVj}")
                }
                if (filterDesc.isNotBlank()) {
                    Text(
                        text = filterDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MunowatchTextSecondary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isLoadingMore) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MunowatchGreen,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MunowatchGreenLight
                        )
                    }
                }

                TvButton(
                    text = "All Series",
                    onClick = { viewModel.clearAllFilters() },
                    isPrimary = (!hasAnyFilter && !state.isFilterOpen)
                )

                TvButton(
                    text = if (state.selectedYear != null) "📅 Year: ${state.selectedYear}" else "🔍 Filter / By Year",
                    onClick = { viewModel.toggleFilterOpen() },
                    isPrimary = (hasAnyFilter || state.isFilterOpen)
                )
            }
        }

        // Expandable Multi-Attribute Filters (Year, Genre, VJ)
        if (state.isFilterOpen || hasAnyFilter) {
            // Row 1: SELECT YEAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SELECT YEAR:",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MunowatchGreenLight,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 36.dp)
                ) {
                    item {
                        TvButton(
                            text = "All Years",
                            onClick = { viewModel.selectYear(null) },
                            isPrimary = (state.selectedYear == null),
                            isCompact = true
                        )
                    }
                    items(state.availableYears) { year ->
                        TvButton(
                            text = year,
                            onClick = { viewModel.selectYear(year) },
                            isPrimary = (state.selectedYear == year),
                            isCompact = true
                        )
                    }
                }
            }

            // Row 2: FILTER GENRE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FILTER GENRE:",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MunowatchGreenLight,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 36.dp)
                ) {
                    item {
                        TvButton(
                            text = "All Genres",
                            onClick = { viewModel.selectCategory(null) },
                            isPrimary = (state.selectedCategory == null),
                            isCompact = true
                        )
                    }
                    items(state.availableCategories.filter { !it.equals("All Genres", ignoreCase = true) }) { category ->
                        TvButton(
                            text = category,
                            onClick = { viewModel.selectCategory(category) },
                            isPrimary = (state.selectedCategory == category),
                            isCompact = true
                        )
                    }
                }
            }

            // Row 3: FILTER VJ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FILTER VJ:",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MunowatchGreenLight,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 36.dp)
                ) {
                    item {
                        TvButton(
                            text = "All VJs",
                            onClick = { viewModel.selectVj(null) },
                            isPrimary = (state.selectedVj == null),
                            isCompact = true
                        )
                    }
                    items(state.availableVjs.filter { !it.equals("All VJs", ignoreCase = true) }) { vj ->
                        TvButton(
                            text = vj,
                            onClick = { viewModel.selectVj(vj) },
                            isPrimary = (state.selectedVj == vj),
                            isCompact = true
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (state.isLoading) {
            TvLoadingIndicator(message = "Loading TV Series...")
        } else if (state.errorMessage != null && state.series.isEmpty()) {
            TvErrorView(
                message = state.errorMessage ?: "",
                onRetry = {
                    if (hasAnyFilter) {
                        viewModel.selectYear(state.selectedYear)
                    } else {
                        viewModel.loadSeries(forceReload = true)
                    }
                }
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 36.dp, end = 36.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = state.series,
                    key = { seriesItem -> seriesItem.id.ifBlank { seriesItem.streamUrl } }
                ) { seriesItem ->
                    TvMediaCard(
                        item = seriesItem,
                        onClick = { onOpenSeriesDetails(seriesItem.id) }
                    )
                }

                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MunowatchGreen,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Loading more series...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else if (state.canLoadMore && state.series.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TvButton(
                                text = "Load More Series",
                                onClick = { viewModel.loadMore() },
                                isPrimary = true
                            )
                        }
                    }
                }
            }
        }
    }
}
