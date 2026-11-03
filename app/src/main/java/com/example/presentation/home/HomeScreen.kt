package com.example.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import com.example.data.repository.MediaCache
import com.example.domain.model.AppUpdateInfo
import com.example.domain.model.CategoryItem
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.MediaItem
import com.example.domain.model.UpdateDownloadState
import com.example.domain.repository.AppUpdateRepository
import com.example.domain.repository.MovieRepository
import com.example.domain.repository.MunowatchRepository
import com.example.domain.repository.PlaybackRepository
import com.example.domain.repository.SeriesRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvErrorView
import com.example.ui.components.TvLoadingIndicator
import com.example.ui.components.TvMediaCard
import com.example.ui.components.TvTopBar
import com.example.ui.components.TvUpdateDialog
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchFocusBorder
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurface
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextMuted
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroItem: MediaItem? = null,
    val isHeroFavorite: Boolean = false,
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val watchlist: List<MediaItem> = emptyList(),
    val latestMovies: List<MediaItem> = emptyList(),
    val popularSeries: List<MediaItem> = emptyList(),
    val freeMovies: List<MediaItem> = emptyList(),
    val vjCategories: List<CategoryItem> = emptyList(),
    val subscriptionDays: Int? = null,
    val errorMessage: String? = null,
    val updateInfo: AppUpdateInfo? = null,
    val showUpdateDialog: Boolean = false
)

class HomeViewModel(
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository,
    private val playbackRepository: PlaybackRepository,
    private val munowatchRepository: MunowatchRepository,
    private val appUpdateRepository: AppUpdateRepository? = null
) : ViewModel() {

    private val cachedMovies = MediaCache.getPopularMovies()
    private val cachedSeries = MediaCache.getPopularSeries()
    private val cachedFree = MediaCache.getFreeMovies()
    private val hasAnyCache = cachedMovies.isNotEmpty() || cachedSeries.isNotEmpty()

    val downloadState: StateFlow<UpdateDownloadState> =
        appUpdateRepository?.downloadState ?: MutableStateFlow(UpdateDownloadState.Idle)

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = !hasAnyCache,
            latestMovies = cachedMovies,
            popularSeries = cachedSeries,
            freeMovies = cachedFree,
            heroItem = cachedMovies.firstOrNull() ?: cachedSeries.firstOrNull()
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        loadHomeData(silentRefresh = hasAnyCache)
        observeContinueWatching()
        observeWatchlist()
        checkForUpdates()
    }

    fun loadHomeData(silentRefresh: Boolean = false) {
        if (!silentRefresh && _uiState.value.latestMovies.isEmpty() && _uiState.value.popularSeries.isEmpty()) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        }
        viewModelScope.launch {
            try {
                // Fetch categories, hero, movies, series in parallel with IO dispatcher
                val categoriesDeferred = async(Dispatchers.IO) { munowatchRepository.getCategories() }
                val latestMoviesDeferred = async(Dispatchers.IO) { movieRepository.getLatestMovies() }
                val popularSeriesDeferred = async(Dispatchers.IO) { seriesRepository.getPopularSeries() }
                val freeMoviesDeferred = async(Dispatchers.IO) { movieRepository.getFreeMovies() }

                val categoriesResult = categoriesDeferred.await()
                val latestMoviesResult = latestMoviesDeferred.await()
                val popularSeriesResult = popularSeriesDeferred.await()
                val freeMoviesResult = freeMoviesDeferred.await()

                val vjs = categoriesResult.getOrNull()?.second ?: emptyList()
                val movies = latestMoviesResult.getOrDefault(emptyList())
                val series = popularSeriesResult.getOrDefault(emptyList())
                val free = freeMoviesResult.getOrDefault(emptyList())
                val hero = movies.firstOrNull() ?: series.firstOrNull()
                val currentHeroId = (hero ?: _uiState.value.heroItem)?.id
                val heroFav = currentHeroId?.let { hid -> _uiState.value.watchlist.any { it.id == hid } } ?: _uiState.value.isHeroFavorite

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    heroItem = hero ?: _uiState.value.heroItem,
                    isHeroFavorite = heroFav,
                    latestMovies = if (movies.isNotEmpty()) movies else _uiState.value.latestMovies,
                    popularSeries = if (series.isNotEmpty()) series else _uiState.value.popularSeries,
                    freeMovies = if (free.isNotEmpty()) free else _uiState.value.freeMovies,
                    vjCategories = if (vjs.isNotEmpty()) vjs else _uiState.value.vjCategories
                )

                // Proactively fetch hero's authentic movie description from Munowatch
                val currentHero = hero ?: _uiState.value.heroItem
                if (currentHero != null && currentHero.description.isBlank()) {
                    viewModelScope.launch {
                        val heroDetails = movieRepository.getMovieDetails(currentHero.id).getOrNull()
                        if (heroDetails != null && heroDetails.description.isNotBlank()) {
                            _uiState.value = _uiState.value.copy(
                                heroItem = currentHero.copy(description = heroDetails.description)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                val hasContent = _uiState.value.latestMovies.isNotEmpty() || _uiState.value.popularSeries.isNotEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (!hasContent) "Failed to load Munowatch dashboard. Please check your connection." else null
                )
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            playbackRepository.getContinueWatching().collect { list ->
                _uiState.value = _uiState.value.copy(continueWatching = list)
            }
        }
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            munowatchRepository.getFavorites().collect { list ->
                val heroId = _uiState.value.heroItem?.id
                _uiState.value = _uiState.value.copy(
                    watchlist = list,
                    isHeroFavorite = if (heroId != null) list.any { it.id == heroId } else false
                )
            }
        }
    }

    fun toggleHeroFavorite() {
        val hero = _uiState.value.heroItem ?: return
        viewModelScope.launch {
            munowatchRepository.toggleFavorite(hero)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val result = appUpdateRepository?.checkForUpdate()
                val info = result?.getOrNull()
                if (info != null && info.isUpdateAvailable) {
                    val dismissed = appUpdateRepository?.isUpdateDismissed(info.latestVersionCode) == true
                    if (!dismissed || info.isForceUpdate) {
                        _uiState.value = _uiState.value.copy(
                            updateInfo = info,
                            showUpdateDialog = true
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun startUpdateDownload() {
        val info = _uiState.value.updateInfo ?: return
        viewModelScope.launch {
            val result = appUpdateRepository?.downloadApk(info.apkUrl)
            result?.onSuccess { file ->
                appUpdateRepository?.installApk(file)
            }
        }
    }

    fun dismissUpdateDialog() {
        val info = _uiState.value.updateInfo
        if (info != null) {
            appUpdateRepository?.dismissCurrentUpdate(info.latestVersionCode)
        }
        appUpdateRepository?.resetDownloadState()
        _uiState.value = _uiState.value.copy(showUpdateDialog = false)
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (String) -> Unit,
    onPlayMedia: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenSeriesDetails: ((String) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    if (state.showUpdateDialog && state.updateInfo != null) {
        TvUpdateDialog(
            updateInfo = state.updateInfo!!,
            downloadState = downloadState,
            onUpdateClick = { viewModel.startUpdateDownload() },
            onDismissClick = { viewModel.dismissUpdateDialog() }
        )
    }

    if (state.isLoading) {
        TvLoadingIndicator(message = "Loading Munowatch Catalog...")
        return
    }

    if (state.errorMessage != null && state.latestMovies.isEmpty()) {
        TvErrorView(
            message = state.errorMessage ?: "Unknown error",
            onRetry = { viewModel.loadHomeData() }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
    ) {
        val heroPlayFocusRequester = remember { FocusRequester() }

        LaunchedEffect(state.heroItem?.id) {
            if (state.heroItem != null) {
                try {
                    heroPlayFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }

        // Top Navigation Bar (Days Left text hidden on homescreen per UI spec)
        TvTopBar(
            currentRoute = "home",
            onNavigate = onNavigate,
            subscriptionDays = null,
            showSubscriptionBadge = false,
            onDownFocusRequester = heroPlayFocusRequester
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // 1. Featured Hero Banner
            item {
                state.heroItem?.let { hero ->
                    TvHeroBanner(
                        item = hero,
                        isFavorite = state.isHeroFavorite,
                        onPlayClick = { onPlayMedia(hero.id) },
                        onFavoriteClick = { viewModel.toggleHeroFavorite() },
                        onDetailsClick = { onOpenDetails(hero.id) },
                        playNowFocusRequester = heroPlayFocusRequester
                    )
                }
            }

            // 2. Continue Watching Row (if any items exist in Room)
            if (state.continueWatching.isNotEmpty()) {
                item {
                    TvContentSection(
                        title = "Continue Watching",
                        subtitle = "Resume where you left off"
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = state.continueWatching,
                                key = { it.contentId },
                                contentType = { "tv_continue_watching" }
                            ) { cwItem ->
                                TvContinueWatchingCard(
                                    item = cwItem,
                                    onClick = { onPlayMedia(cwItem.contentId) }
                                )
                            }
                        }
                    }
                }
            }

            // 2b. My Watchlist Row (saved movies & series in Room)
            if (state.watchlist.isNotEmpty()) {
                item {
                    TvContentSection(
                        title = "My Watchlist",
                        subtitle = "Saved movies & series for later"
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = state.watchlist,
                                key = { "wl_${it.id}" },
                                contentType = { "tv_media_card" }
                            ) { watchItem ->
                                TvMediaCard(
                                    item = watchItem,
                                    onClick = {
                                        if (watchItem.isSeries && onOpenSeriesDetails != null) {
                                            onOpenSeriesDetails(watchItem.id)
                                        } else {
                                            onOpenDetails(watchItem.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Latest Translated Movies
            item {
                TvContentSection(
                    title = "Latest Translated Movies",
                    subtitle = "Freshly dubbed and updated releases"
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 36.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = state.latestMovies,
                            key = { it.id.ifBlank { it.streamUrl } },
                            contentType = { "tv_media_card" }
                        ) { movie ->
                            TvMediaCard(
                                item = movie,
                                onClick = { onOpenDetails(movie.id) }
                            )
                        }
                    }
                }
            }

            // 4. Popular Translated Series
            if (state.popularSeries.isNotEmpty()) {
                item {
                    TvContentSection(
                        title = "Popular Series",
                        subtitle = "Full seasons translated with Veejay commentary"
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = state.popularSeries,
                                key = { it.id.ifBlank { it.streamUrl } },
                                contentType = { "tv_media_card" }
                            ) { series ->
                                TvMediaCard(
                                    item = series,
                                    onClick = {
                                        if (onOpenSeriesDetails != null) {
                                            onOpenSeriesDetails(series.id)
                                        } else {
                                            onOpenDetails(series.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Featured Ugandan VJs
            if (state.vjCategories.isNotEmpty()) {
                item {
                    TvContentSection(
                        title = "Ugandan Video Jockeys (VJs)",
                        subtitle = "Browse by your favorite translator"
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = state.vjCategories,
                                key = { it.id },
                                contentType = { "tv_vj_chip" }
                            ) { vj ->
                                TvVjChip(
                                    item = vj,
                                    onClick = { onNavigate("categories?vj_id=${vj.id}") }
                                )
                            }
                        }
                    }
                }
            }

            // 6. Free Movies
            if (state.freeMovies.isNotEmpty()) {
                item {
                    TvContentSection(
                        title = "Free Movies",
                        subtitle = "Watch without subscription"
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = state.freeMovies,
                                key = { it.id.ifBlank { it.streamUrl } },
                                contentType = { "tv_media_card" }
                            ) { freeMovie ->
                                TvMediaCard(
                                    item = freeMovie,
                                    onClick = { onOpenDetails(freeMovie.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvHeroBanner(
    item: MediaItem,
    isFavorite: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDetailsClick: () -> Unit,
    playNowFocusRequester: FocusRequester? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .padding(horizontal = 36.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MunowatchSurfaceElevated)
            .testTag("hero_banner")
    ) {
        // Backdrop Image
        if (item.backdropUrl.isNotBlank() || item.posterUrl.isNotBlank()) {
            val context = LocalContext.current
            val heroModel = item.backdropUrl.ifBlank { item.posterUrl }
            val imageRequest = remember(heroModel) {
                coil.request.ImageRequest.Builder(context)
                    .data(heroModel)
                    .size(coil.size.Size(960, 360))
                    .precision(coil.size.Precision.INEXACT)
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                    .allowRgb565(true)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Single optimized cinematic gradient scrim with cached draw
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val horizontalScrim = Brush.horizontalGradient(
                        colors = listOf(
                            MunowatchBackground.copy(alpha = 0.95f),
                            MunowatchBackground.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                    val verticalScrim = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MunowatchBackground.copy(alpha = 0.9f)
                        )
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(horizontalScrim)
                        drawRect(verticalScrim)
                    }
                }
        )

        // Banner Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Badges row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MunowatchGreen,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "FEATURED PREMIERE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (item.vj.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MunowatchGreen.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.vj,
                            color = MunowatchGreenLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (item.quality.isNotBlank()) {
                    Text(
                        text = item.quality,
                        color = MunowatchTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Description / Overview
            val displayDescription = item.description.takeIf { it.isNotBlank() }
                ?: listOfNotNull(
                    item.vj.takeIf { it.isNotBlank() }?.let { "VJ $it" },
                    item.genre.takeIf { it.isNotBlank() },
                    item.quality.takeIf { it.isNotBlank() },
                    item.year.takeIf { it.isNotBlank() }
                ).joinToString(" • ")

            if (displayDescription.isNotBlank()) {
                Text(
                    text = displayDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MunowatchTextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(540.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Hero action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    text = "Play Now",
                    icon = Icons.Default.PlayArrow,
                    onClick = onPlayClick,
                    isPrimary = true,
                    modifier = if (playNowFocusRequester != null) Modifier.focusRequester(playNowFocusRequester) else Modifier
                )
                TvButton(
                    text = "Details",
                    icon = Icons.Default.Info,
                    onClick = onDetailsClick,
                    isPrimary = false
                )
                TvButton(
                    text = if (isFavorite) "Saved" else "Watchlist",
                    icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    onClick = onFavoriteClick,
                    isPrimary = false
                )
            }
        }
    }
}

@Composable
fun TvContentSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MunowatchTextMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
fun TvContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(100),
        label = "cw_scale"
    )

    Column(
        modifier = Modifier
            .width(220.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MunowatchSurfaceElevated)
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) MunowatchFocusBorder else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .focusable(interactionSource = interactionSource)
                .testTag("continue_watching_${item.contentId}")
        ) {
            if (item.posterUrl.isNotBlank()) {
                val context = LocalContext.current
                val imageRequest = remember(item.posterUrl) {
                    coil.request.ImageRequest.Builder(context)
                        .data(item.posterUrl)
                        .size(coil.size.Size(330, 186))
                        .precision(coil.size.Precision.INEXACT)
                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                        .allowRgb565(true)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dark overlay & Play Icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isFocused) MunowatchGreenLight else MunowatchGreen.copy(alpha = 0.9f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Progress bar at the bottom
            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = MunowatchGreen,
                trackColor = Color.Black.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            color = if (isFocused) MunowatchGreenLight else MunowatchTextPrimary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TvVjChip(
    item: CategoryItem,
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
            .testTag("vj_chip_${item.id}"),
        shape = RoundedCornerShape(24.dp),
        color = if (isFocused) MunowatchGreenLight else MunowatchSurfaceElevated,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else MunowatchGreen.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) Color.Black else MunowatchGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.name,
                color = if (isFocused) Color.Black else MunowatchTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
