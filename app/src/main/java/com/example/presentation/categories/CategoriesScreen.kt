package com.example.presentation.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CategoryItem
import com.example.domain.model.CategoryType
import com.example.domain.model.DEFAULT_GENRE_CATEGORIES
import com.example.domain.model.DEFAULT_VJ_CATEGORIES
import com.example.domain.model.MediaItem
import com.example.domain.repository.MovieRepository
import com.example.domain.repository.MunowatchRepository
import com.example.domain.repository.SearchRepository
import com.example.presentation.movies.MoviesViewModel
import com.example.ui.components.TvButton
import com.example.ui.components.TvLoadingIndicator
import com.example.ui.components.TvMediaCard
import com.example.ui.components.TvTopBar
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CategoriesUiState(
    val isLoading: Boolean = false,
    val selectedTab: CategoryType = CategoryType.VJ,
    val genres: List<CategoryItem> = DEFAULT_GENRE_CATEGORIES,
    val vjs: List<CategoryItem> = DEFAULT_VJ_CATEGORIES,
    val selectedCategory: CategoryItem? = DEFAULT_VJ_CATEGORIES.firstOrNull(),
    val selectedGenreFilter: CategoryItem? = null,
    val items: List<MediaItem> = emptyList(),
    val isContentLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val totalLoaded: Int = 0
)

class CategoriesViewModel(
    private val munowatchRepository: MunowatchRepository,
    private val movieRepository: MovieRepository,
    private val searchRepository: SearchRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState = _uiState.asStateFlow()

    var scrollIndex: Int = 0
    var scrollOffset: Int = 0

    private var currentCategoryJob: Job? = null
    private var lastFetchId: String? = null
    private var currentPage: Int = 1
    private var allCategoryMovies: List<MediaItem> = emptyList()

    init {
        DEFAULT_VJ_CATEGORIES.firstOrNull()?.let { selectCategory(it) }
        loadCategories()
    }

    fun loadCategories(initialVjId: Int? = null, initialGenreId: Int? = null) {
        viewModelScope.launch {
            val result = munowatchRepository.getCategories()
            val (fetchedGenres, fetchedVjs) = result.getOrDefault(Pair(emptyList(), emptyList()))

            val genres = if (fetchedGenres.isNotEmpty()) fetchedGenres else DEFAULT_GENRE_CATEGORIES
            val vjs = if (fetchedVjs.isNotEmpty()) fetchedVjs else DEFAULT_VJ_CATEGORIES

            val currentSelected = _uiState.value.selectedCategory
            val initialCat = when {
                initialVjId != null -> vjs.find { it.id == initialVjId } ?: vjs.firstOrNull()
                initialGenreId != null -> genres.find { it.id == initialGenreId } ?: genres.firstOrNull()
                currentSelected != null -> {
                    if (_uiState.value.selectedTab == CategoryType.VJ) {
                        vjs.find { it.id == currentSelected.id || it.name.equals(currentSelected.name, ignoreCase = true) } ?: vjs.firstOrNull()
                    } else {
                        genres.find { it.id == currentSelected.id || it.name.equals(currentSelected.name, ignoreCase = true) } ?: genres.firstOrNull()
                    }
                }
                _uiState.value.selectedTab == CategoryType.GENRE -> genres.firstOrNull()
                else -> vjs.firstOrNull() ?: genres.firstOrNull()
            }
            val initialTab = if (initialCat?.type == CategoryType.GENRE) CategoryType.GENRE else _uiState.value.selectedTab

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                genres = genres,
                vjs = vjs,
                selectedTab = initialTab,
                selectedCategory = initialCat
            )
            if (initialCat != null && (currentSelected == null || currentSelected.name != initialCat.name || _uiState.value.items.isEmpty())) {
                selectCategory(initialCat)
            }
        }
    }

    fun setTab(type: CategoryType) {
        scrollIndex = 0
        scrollOffset = 0
        val list = if (type == CategoryType.VJ) {
            if (_uiState.value.vjs.isNotEmpty()) _uiState.value.vjs else DEFAULT_VJ_CATEGORIES
        } else {
            if (_uiState.value.genres.isNotEmpty()) _uiState.value.genres else DEFAULT_GENRE_CATEGORIES
        }
        val firstItem = list.firstOrNull()
        _uiState.value = _uiState.value.copy(
            selectedTab = type,
            selectedCategory = firstItem,
            selectedGenreFilter = null
        )
        firstItem?.let { selectCategory(it) }
    }

    fun selectCategory(category: CategoryItem) {
        scrollIndex = 0
        scrollOffset = 0
        val currentGenre = _uiState.value.selectedGenreFilter
        if (category.type == CategoryType.VJ && currentGenre != null) {
            _uiState.value = _uiState.value.copy(selectedCategory = category)
            loadMoviesForVjAndGenre(category, currentGenre)
        } else {
            _uiState.value = _uiState.value.copy(selectedGenreFilter = null)
            loadCategoryCatalog(category)
        }
    }

    fun selectGenreFilter(genre: CategoryItem?) {
        if (_uiState.value.selectedGenreFilter?.id == genre?.id) return
        scrollIndex = 0
        scrollOffset = 0
        _uiState.value = _uiState.value.copy(selectedGenreFilter = genre)
        val currentVj = _uiState.value.selectedCategory
        if (currentVj != null && _uiState.value.selectedTab == CategoryType.VJ) {
            if (genre == null) {
                loadCategoryCatalog(currentVj)
            } else {
                loadMoviesForVjAndGenre(currentVj, genre)
            }
        }
    }

    private fun loadCategoryCatalog(category: CategoryItem) {
        currentCategoryJob?.cancel()
        lastFetchId = null
        currentPage = 1

        val cachedItems = if (category.type == CategoryType.VJ) {
            com.example.data.repository.MediaCache.getVjMovies(category.id)?.filter { !it.isSeries }
        } else {
            com.example.data.repository.MediaCache.getGenreMovies(category.id)
        }

        if (!cachedItems.isNullOrEmpty()) {
            allCategoryMovies = cachedItems
            val initialDisplay = cachedItems.take(36)
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                isContentLoading = false,
                isLoadingMore = false,
                canLoadMore = initialDisplay.size < cachedItems.size || category.type == CategoryType.GENRE,
                items = initialDisplay,
                totalLoaded = cachedItems.size
            )
        } else {
            allCategoryMovies = emptyList()
            _uiState.value = _uiState.value.copy(
                selectedCategory = category,
                isContentLoading = true,
                isLoadingMore = false,
                canLoadMore = true,
                items = emptyList(),
                totalLoaded = 0
            )
        }

        currentCategoryJob = viewModelScope.launch(Dispatchers.IO) {
            // Fetch initial batch (Page 1) off the main thread
            val firstBatchResult = fetchBatch(category, null, 1)
            val firstBatch = firstBatchResult.getOrDefault(emptyList())
                .filter { category.type != CategoryType.VJ || !it.isSeries }
                .distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }

            if (firstBatch.isEmpty()) {
                if (cachedItems.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isContentLoading = false,
                            items = emptyList(),
                            canLoadMore = false,
                            totalLoaded = 0
                        )
                    }
                }
                return@launch
            }

            if (category.type == CategoryType.VJ) {
                com.example.data.repository.MediaCache.setVjMovies(category.id, firstBatch)
            } else {
                com.example.data.repository.MediaCache.setGenreMovies(category.id, firstBatch)
            }

            allCategoryMovies = firstBatch
            lastFetchId = firstBatch.lastOrNull()?.id
            currentPage = 1

            val initialDisplay = firstBatch.take(36)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isContentLoading = false,
                    items = initialDisplay,
                    totalLoaded = firstBatch.size,
                    canLoadMore = initialDisplay.size < firstBatch.size || category.type == CategoryType.GENRE
                )
            }
        }
    }

    private fun loadMoviesForVjAndGenre(vj: CategoryItem, genre: CategoryItem) {
        currentCategoryJob?.cancel()
        lastFetchId = null
        currentPage = 1

        // 1. Immediate memory/disk cache check
        val cached = com.example.data.repository.MediaCache.getVjGenreMovies(vj.id, genre.id)
        if (!cached.isNullOrEmpty() && cached.size >= 15) {
            allCategoryMovies = cached
            val initialDisplay = cached.take(36)
            _uiState.value = _uiState.value.copy(
                selectedCategory = vj,
                selectedGenreFilter = genre,
                isContentLoading = false,
                isLoadingMore = false,
                canLoadMore = initialDisplay.size < cached.size,
                items = initialDisplay,
                totalLoaded = cached.size
            )
        } else {
            allCategoryMovies = cached ?: emptyList()
            val initialDisplay = allCategoryMovies.take(36)
            _uiState.value = _uiState.value.copy(
                selectedCategory = vj,
                selectedGenreFilter = genre,
                isContentLoading = cached.isNullOrEmpty(),
                isLoadingMore = false,
                canLoadMore = true,
                items = initialDisplay,
                totalLoaded = cached?.size ?: 0
            )
        }

        currentCategoryJob = viewModelScope.launch(Dispatchers.Default) {
            // 2. Perform cross-filter from MediaCache in background
            val matches = mutableListOf<MediaItem>()
            val vjCached = com.example.data.repository.MediaCache.getVjMovies(vj.id) ?: emptyList()
            matches.addAll(vjCached.filter { MoviesViewModel.matchesCategory(it, genre.name) })
            val genreCached = com.example.data.repository.MediaCache.getGenreMovies(genre.id) ?: emptyList()
            matches.addAll(genreCached.filter { MoviesViewModel.matchesVj(it, vj.name) })
            val allCached = com.example.data.repository.MediaCache.getAll().filter {
                MoviesViewModel.matchesVj(it, vj.name) && MoviesViewModel.matchesCategory(it, genre.name)
            }
            matches.addAll(allCached)

            // 3. Fetch dedicated VJ + Genre batch (Page 1) from MovieRepository
            val primaryBatchResult = movieRepository.getMoviesByVjAndGenre(vj.id, genre.id, page = 1)
            val primaryBatch = primaryBatchResult.getOrDefault(emptyList()).filter {
                MoviesViewModel.matchesVj(it, vj.name) && MoviesViewModel.matchesCategory(it, genre.name)
            }
            matches.addAll(primaryBatch)

            val distinctMatches = matches.distinctBy { it.id }.map {
                if (it.vj.isBlank()) it.copy(vj = vj.name) else it
            }

            if (distinctMatches.isNotEmpty()) {
                allCategoryMovies = distinctMatches
                com.example.data.repository.MediaCache.setVjGenreMovies(vj.id, genre.id, distinctMatches)
                val initialDisplay = distinctMatches.take(36)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isContentLoading = false,
                        items = initialDisplay,
                        totalLoaded = distinctMatches.size,
                        canLoadMore = initialDisplay.size < distinctMatches.size
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isContentLoading = false,
                        canLoadMore = false
                    )
                }
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        val category = currentState.selectedCategory ?: return
        if (currentState.isLoadingMore || currentState.isContentLoading || !currentState.canLoadMore) {
            return
        }

        // 1. Reveal next batch of 24 items from in-memory buffer immediately with zero network lag
        if (currentState.items.size < allCategoryMovies.size) {
            val nextCount = minOf(currentState.items.size + 24, allCategoryMovies.size)
            val canMore = nextCount < allCategoryMovies.size || category.type == CategoryType.GENRE
            _uiState.value = currentState.copy(
                items = allCategoryMovies.take(nextCount),
                canLoadMore = canMore
            )
            return
        }

        // 2. Fetch next paginated page from server for Category, VJ, or VJ+Genre
        val currentFetchId = lastFetchId ?: currentState.items.lastOrNull()?.id
        val nextPage = currentPage + 1

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            val nextResult = when {
                currentState.selectedGenreFilter != null -> {
                    movieRepository.getMoviesByVjAndGenre(category.id, currentState.selectedGenreFilter.id, page = nextPage)
                }
                category.type == CategoryType.VJ -> {
                    movieRepository.getMoviesByVj(category.id, currentFetchId, category.pipe, nextPage)
                }
                else -> {
                    movieRepository.getMoviesByGenre(category.id, currentFetchId, category.pipe, nextPage)
                }
            }

            val rawNext = nextResult.getOrDefault(emptyList())
            val filteredNext = if (currentState.selectedGenreFilter != null) {
                rawNext.filter { MoviesViewModel.matchesCategory(it, currentState.selectedGenreFilter.name) }
            } else {
                rawNext
            }
            val nextItems = filteredNext
                .distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }

            withContext(Dispatchers.Main) {
                if (nextItems.isNotEmpty()) {
                    val existing = _uiState.value.items
                    val newUnique = nextItems.filter { item -> existing.none { it.id == item.id } }
                    val updatedList = existing + newUnique
                    lastFetchId = newUnique.lastOrNull()?.id ?: nextItems.last().id
                    currentPage = nextPage
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        items = updatedList,
                        totalLoaded = updatedList.size,
                        canLoadMore = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        canLoadMore = true
                    )
                }
            }
        }
    }

    private suspend fun fetchBatch(category: CategoryItem, fetchId: String?, page: Int = 1): Result<List<MediaItem>> {
        return if (category.type == CategoryType.VJ) {
            movieRepository.getMoviesByVj(category.id, fetchId, category.pipe, page)
        } else {
            movieRepository.getMoviesByGenre(category.id, fetchId, category.pipe, page)
        }
    }
}

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigate: (String) -> Unit,
    onOpenDetails: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberSaveable(
        saver = LazyGridState.Saver
    ) {
        LazyGridState(
            firstVisibleItemIndex = viewModel.scrollIndex,
            firstVisibleItemScrollOffset = viewModel.scrollOffset
        )
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, offset) ->
                viewModel.scrollIndex = idx
                viewModel.scrollOffset = offset
            }
    }

    var lastCategoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    val currentCategoryKey = "${state.selectedTab}_${state.selectedCategory?.id}_${state.selectedGenreFilter?.id}"

    LaunchedEffect(currentCategoryKey) {
        if (lastCategoryKey != null && lastCategoryKey != currentCategoryKey) {
            viewModel.scrollIndex = 0
            viewModel.scrollOffset = 0
            try {
                gridState.scrollToItem(0)
            } catch (_: Exception) {}
        }
        lastCategoryKey = currentCategoryKey
    }

    LaunchedEffect(state.items.size) {
        if (viewModel.scrollIndex > 0 && state.items.size > viewModel.scrollIndex) {
            if (gridState.firstVisibleItemIndex == 0) {
                try {
                    gridState.scrollToItem(viewModel.scrollIndex, viewModel.scrollOffset)
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
    ) {
        TvTopBar(
            currentRoute = "categories",
            onNavigate = onNavigate
        )

        val activeList = if (state.selectedTab == CategoryType.VJ) {
            if (state.vjs.isNotEmpty()) state.vjs else DEFAULT_VJ_CATEGORIES
        } else {
            if (state.genres.isNotEmpty()) state.genres else DEFAULT_GENRE_CATEGORIES
        }
        val activeCategory = state.selectedCategory ?: activeList.firstOrNull()

        var isSidebarExpanded by rememberSaveable { mutableStateOf(false) }
        var isGenreFilterExpanded by rememberSaveable { mutableStateOf(false) }

        // Split Layout: Left Column (Categories List), Right Column (Media Grid)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 6.dp)
        ) {
            // Left sidebar: Collapsible tabs and category list
            if (isSidebarExpanded) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TvButton(
                            text = "Veejays",
                            onClick = { viewModel.setTab(CategoryType.VJ) },
                            isPrimary = state.selectedTab == CategoryType.VJ,
                            modifier = Modifier.weight(1f),
                            isCompact = true
                        )
                        TvButton(
                            text = "Genres",
                            onClick = { viewModel.setTab(CategoryType.GENRE) },
                            isPrimary = state.selectedTab == CategoryType.GENRE,
                            modifier = Modifier.weight(1f),
                            isCompact = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(activeList, key = { it.id }) { category ->
                            val isSelected = activeCategory?.id == category.id ||
                                activeCategory?.name.equals(category.name, ignoreCase = true)
                            TvCategoryListItem(
                                category = category,
                                isSelected = isSelected,
                                onClick = {
                                    viewModel.selectCategory(category)
                                    isSidebarExpanded = false // Fold sidebar after selection to maximize movies space
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                // Compact collapsed mini-rail
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TvButton(
                        text = "☰",
                        onClick = { isSidebarExpanded = true },
                        isPrimary = false,
                        isCompact = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (state.selectedTab == CategoryType.VJ) "VJ" else "GEN",
                        color = MunowatchGreenLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Right side: Movies for selected category
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                activeCategory?.let { cat ->
                    val filterTitle = if (state.selectedGenreFilter != null) {
                        "${cat.name.uppercase()} • ${state.selectedGenreFilter?.name?.uppercase()}"
                    } else {
                        "${cat.name.uppercase()} CATALOG"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TvButton(
                                text = if (isSidebarExpanded) "◀ Fold Menu" else "☰ Category (${activeCategory?.name ?: "All"})",
                                onClick = { isSidebarExpanded = !isSidebarExpanded },
                                isPrimary = isSidebarExpanded,
                                isCompact = true
                            )
                            Text(
                                text = filterTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

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
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Collapsible Genre filter chips for Veejays tab
                    if (state.selectedTab == CategoryType.VJ) {
                        if (!isGenreFilterExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MunowatchGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MunowatchGreen.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "GENRE: ${state.selectedGenreFilter?.name ?: "All Genres"}",
                                        color = MunowatchGreenLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                TvButton(
                                    text = "Filter Genre ▼",
                                    onClick = { isGenreFilterExpanded = true },
                                    isCompact = true
                                )
                                if (state.selectedGenreFilter != null) {
                                    TvButton(
                                        text = "Reset All Genres",
                                        onClick = { viewModel.selectGenreFilter(null) },
                                        isCompact = true
                                    )
                                }
                                Text(
                                    text = "${state.items.size} movies loaded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MunowatchTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            val availableGenres = remember(state.genres) {
                                if (state.genres.isNotEmpty()) state.genres else DEFAULT_GENRE_CATEGORIES
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    item(key = "all_genres") {
                                        TvGenreFilterChip(
                                            label = "All Genres",
                                            isSelected = state.selectedGenreFilter == null,
                                            onClick = {
                                                viewModel.selectGenreFilter(null)
                                                isGenreFilterExpanded = false // Fold after selection
                                            }
                                        )
                                    }
                                    items(availableGenres, key = { it.id }) { genre ->
                                        TvGenreFilterChip(
                                            label = genre.name,
                                            isSelected = state.selectedGenreFilter?.id == genre.id,
                                            onClick = {
                                                viewModel.selectGenreFilter(genre)
                                                isGenreFilterExpanded = false // Fold after selection
                                            }
                                        )
                                    }
                                }
                                TvButton(
                                    text = "Fold ▲",
                                    onClick = { isGenreFilterExpanded = false },
                                    isCompact = true
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (state.isContentLoading) {
                    val catName = activeCategory?.name.orEmpty()
                    val loadingMsg = if (state.selectedGenreFilter != null) {
                        "Loading ${state.selectedGenreFilter?.name} movies by $catName..."
                    } else if (catName.isNotBlank()) {
                        "Loading $catName releases..."
                    } else {
                        "Loading category releases..."
                    }
                    TvLoadingIndicator(message = loadingMsg)
                } else if (state.items.isEmpty()) {
                    val catName = activeCategory?.name.orEmpty()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (state.selectedGenreFilter != null) {
                                    "No ${state.selectedGenreFilter?.name} movies currently found for $catName."
                                } else if (catName.isNotBlank()) {
                                    "No movies currently found in $catName."
                                } else {
                                    "No movies currently found in this category."
                                },
                                color = MunowatchTextSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            TvButton(
                                text = if (state.isLoadingMore) "⏳ Loading More Movies..." else "Load More Movies",
                                onClick = { viewModel.loadMore() },
                                isPrimary = true
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 125.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = state.items,
                            key = { movie -> movie.id.ifBlank { movie.streamUrl }.ifBlank { movie.title } }
                        ) { movie ->
                            TvMediaCard(
                                item = movie,
                                onClick = {
                                    viewModel.scrollIndex = gridState.firstVisibleItemIndex
                                    viewModel.scrollOffset = gridState.firstVisibleItemScrollOffset
                                    onOpenDetails(movie.id)
                                }
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
                                            text = "Loading More Movies...",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        } else if (state.items.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TvButton(
                                        text = "Load More Movies",
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
    }
}

@Composable
private fun TvCategoryListItem(
    category: CategoryItem,
    isSelected: Boolean,
    onClick: () -> Unit
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
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> MunowatchGreenLight
            isSelected -> MunowatchSurfaceElevated
            else -> Color.Transparent
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
            color = if (isFocused) Color.White else if (isSelected) MunowatchGreen else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name,
                color = when {
                    isFocused -> Color.Black
                    isSelected -> MunowatchGreen
                    else -> MunowatchTextPrimary
                },
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun TvGenreFilterChip(
    label: String,
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
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(20.dp),
        color = when {
            isFocused -> Color.White
            isSelected -> MunowatchGreen.copy(alpha = 0.25f)
            else -> MunowatchSurfaceElevated
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
            color = when {
                isFocused -> MunowatchGreenLight
                isSelected -> MunowatchGreen
                else -> Color.White.copy(alpha = 0.12f)
            }
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            color = when {
                isFocused -> Color.Black
                isSelected -> MunowatchGreenLight
                else -> MunowatchTextPrimary
            },
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

