package com.example.presentation.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.data.repository.MediaCache
import com.example.domain.model.MediaItem
import com.example.domain.recommendation.MovieRecommendationEngine
import com.example.domain.repository.MovieRepository
import com.example.domain.repository.MunowatchRepository
import com.example.domain.repository.SearchRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvLoadingIndicator
import com.example.ui.components.TvMediaCard
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchFocusBorder
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurface
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextMuted
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailsUiState(
    val isLoading: Boolean = true,
    val isFetchingDetails: Boolean = false,
    val movie: MediaItem? = null,
    val isFavorite: Boolean = false,
    val recommendations: List<MediaItem> = emptyList(),
    val isLoadingRecommendations: Boolean = false
)

class DetailsViewModel(
    private val contentId: String,
    private val movieRepository: MovieRepository,
    private val munowatchRepository: MunowatchRepository,
    private val searchRepository: SearchRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val cached = MediaCache.getMovieDetail(contentId) ?: MediaCache.get(contentId)
    private val hasDetails = cached != null && cached.description.isNotBlank()

    private val _uiState = MutableStateFlow(
        DetailsUiState(
            isLoading = (cached == null),
            isFetchingDetails = !hasDetails,
            movie = cached
        )
    )
    val uiState = _uiState.asStateFlow()

    private var recJob: Job? = null

    init {
        if (!hasDetails) {
            loadDetails()
        } else {
            refreshSilent()
        }
        observeFavorite()
        cached?.let { loadRecommendations(it) }
    }

    private fun loadDetails() {
        viewModelScope.launch {
            val result = movieRepository.getMovieDetails(contentId)
            val movie = result.getOrNull()
            if (movie != null) {
                MediaCache.putMovieDetail(movie)
            }
            val finalMovie = movie ?: _uiState.value.movie
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isFetchingDetails = false,
                movie = finalMovie
            )
            finalMovie?.let { loadRecommendations(it) }
        }
    }

    private fun refreshSilent() {
        viewModelScope.launch {
            try {
                val result = movieRepository.getMovieDetails(contentId)
                val movie = result.getOrNull()
                if (movie != null && movie.description.isNotBlank()) {
                    MediaCache.putMovieDetail(movie)
                    _uiState.value = _uiState.value.copy(movie = movie)
                    loadRecommendations(movie)
                }
            } catch (_: Exception) {}
        }
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            munowatchRepository.isFavorite(contentId).collect { isFav ->
                _uiState.value = _uiState.value.copy(isFavorite = isFav)
            }
        }
    }

    fun toggleFavorite() {
        val movie = _uiState.value.movie ?: return
        viewModelScope.launch {
            munowatchRepository.toggleFavorite(movie)
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }

    private fun loadRecommendations(targetMovie: MediaItem) {
        recJob?.cancel()
        recJob = viewModelScope.launch(ioDispatcher) {
            try {
                // 1. Instant pass from in-memory pool
                val inMemoryPool = MediaCache.getAll().filter { !it.isSeries }
                val initialRecs = MovieRecommendationEngine.findRecommendations(targetMovie, inMemoryPool, limit = 18)
                _uiState.value = _uiState.value.copy(
                    recommendations = initialRecs,
                    isLoadingRecommendations = initialRecs.size < 6
                )

                // 2. Background search for franchise / sequel / related items
                val newCandidates = mutableListOf<MediaItem>()
                val titleKeywords = MovieRecommendationEngine.extractSignificantWords(targetMovie.title)
                if (titleKeywords.isNotEmpty() && searchRepository != null) {
                    val franchiseQuery = titleKeywords.take(2).joinToString(" ")
                    try {
                        val sRes = searchRepository.searchContent(franchiseQuery).getOrDefault(emptyList())
                        val sMovies = sRes.filter { !it.isSeries }
                        if (sMovies.isNotEmpty()) {
                            newCandidates.addAll(sMovies)
                            MediaCache.putAll(sMovies)
                        }
                    } catch (_: Exception) {}
                }

                // 3. If recommendations pool is still small (< 12), enrich from genre or VJ
                if ((initialRecs.size + newCandidates.size) < 12) {
                    val inferredGenre = MovieRecommendationEngine.inferGenre(targetMovie)
                    val genreId = MovieRecommendationEngine.KNOWN_GENRE_IDS[inferredGenre.lowercase()]
                    if (genreId != null) {
                        try {
                            val gRes = movieRepository.getMoviesByGenre(genreId, null, "genre_vj", 1)
                            val gMovies = gRes.getOrDefault(emptyList()).filter { !it.isSeries }
                            if (gMovies.isNotEmpty()) {
                                newCandidates.addAll(gMovies)
                                MediaCache.putAll(gMovies)
                            }
                        } catch (_: Exception) {}
                    }

                    val vjClean = targetMovie.vj.lowercase().replace("vj", "").replace(".", "").trim()
                    val vjId = MovieRecommendationEngine.KNOWN_VJ_IDS[vjClean]
                    if (vjId != null) {
                        try {
                            val vRes = movieRepository.getMoviesByVj(vjId, null, "genre_vj", 1)
                            val vMovies = vRes.getOrDefault(emptyList()).filter { !it.isSeries }
                            if (vMovies.isNotEmpty()) {
                                newCandidates.addAll(vMovies)
                                MediaCache.putAll(vMovies)
                            }
                        } catch (_: Exception) {}
                    }
                }

                // 4. Final combined re-scoring
                val combinedPool = (MediaCache.getAll().filter { !it.isSeries } + newCandidates).distinctBy { it.id }
                val finalRecs = MovieRecommendationEngine.findRecommendations(targetMovie, combinedPool, limit = 18)
                _uiState.value = _uiState.value.copy(
                    recommendations = finalRecs,
                    isLoadingRecommendations = false
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingRecommendations = false)
            }
        }
    }
}

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenMovie: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler {
        onBack()
    }

    if (state.isLoading) {
        TvLoadingIndicator(message = "Loading movie details...")
        return
    }

    val movie = state.movie ?: return

    val startWatchingFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(movie.id) {
        startWatchingFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
    ) {
        // Backdrop Image with Scrim
        if (movie.backdropUrl.isNotBlank() || movie.posterUrl.isNotBlank()) {
            AsyncImage(
                model = movie.backdropUrl.ifBlank { movie.posterUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MunowatchBackground.copy(alpha = 0.98f),
                            MunowatchBackground.copy(alpha = 0.88f),
                            MunowatchBackground.copy(alpha = 0.5f)
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp)
        ) {
            item {
                // Top Navigation row (Back button)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvButton(
                        text = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        isPrimary = false,
                        modifier = Modifier
                            .focusRequester(backFocusRequester)
                            .focusProperties {
                                down = startWatchingFocusRequester
                            }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    // Poster Card
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MunowatchSurfaceElevated)
                            .border(2.dp, MunowatchFocusBorder, RoundedCornerShape(12.dp))
                    ) {
                        if (movie.posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = movie.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Metadata details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Title
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (movie.vj.isNotBlank()) {
                                Surface(
                                    color = MunowatchGreen,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = movie.vj,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Surface(
                                color = MunowatchSurfaceElevated,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = movie.quality,
                                    color = MunowatchGreenLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            val displayGenre = remember(movie) {
                                val raw = movie.genre.trim()
                                val clean = if (raw.isNotBlank() && raw != "-" && raw != "null" && !raw.equals("Movie", ignoreCase = true)) {
                                    raw.split(",", "/", "&").firstOrNull()?.trim()
                                } else null
                                val resolved = clean ?: MovieRecommendationEngine.inferGenre(movie).takeIf { it.isNotBlank() } ?: "Action"
                                resolved.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                            }
                            Surface(
                                color = MunowatchSurfaceElevated,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = displayGenre,
                                    color = MunowatchGreenLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (movie.year.isNotBlank()) {
                                Text(
                                    text = movie.year,
                                    color = MunowatchTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Movie Description Section
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MunowatchGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MOVIE DESCRIPTION",
                                color = MunowatchGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = MunowatchSurfaceElevated.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MunowatchFocusBorder.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val descriptionText = when {
                                movie.description.isNotBlank() -> movie.description
                                state.isFetchingDetails -> "Loading synopsis..."
                                else -> "No description available on Munowatch for this title."
                            }
                            val textColor = if (movie.description.isNotBlank()) {
                                MunowatchTextPrimary.copy(alpha = 0.95f)
                            } else {
                                MunowatchTextSecondary.copy(alpha = 0.7f)
                            }
                            Text(
                                text = descriptionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Actions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvButton(
                                text = "Start Watching",
                                icon = Icons.Default.PlayArrow,
                                onClick = { onPlay(movie.id) },
                                isPrimary = true,
                                modifier = Modifier
                                    .focusRequester(startWatchingFocusRequester)
                                    .focusProperties {
                                        up = backFocusRequester
                                    }
                            )
                            TvButton(
                                text = if (state.isFavorite) "In Watchlist" else "Add to Watchlist",
                                icon = if (state.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                onClick = { viewModel.toggleFavorite() },
                                isPrimary = false
                            )
                        }
                    }
                }
            }

            // Recommendations Section ("MORE LIKE THIS")
            if (state.recommendations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(36.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MunowatchGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "MORE LIKE THIS",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "• ${state.recommendations.size} Related Movies",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MunowatchTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 48.dp, bottom = 24.dp)
                    ) {
                        items(state.recommendations, key = { it.id }) { recMovie ->
                            TvMediaCard(
                                item = recMovie,
                                onClick = { onOpenMovie(recMovie.id) },
                                cardWidth = 160.dp
                            )
                        }
                    }
                }
            } else if (state.isLoadingRecommendations) {
                item {
                    Spacer(modifier = Modifier.height(36.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MunowatchGreen,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Finding related movies...",
                            color = MunowatchTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
