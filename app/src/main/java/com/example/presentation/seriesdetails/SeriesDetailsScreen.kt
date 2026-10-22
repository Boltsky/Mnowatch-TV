package com.example.presentation.seriesdetails

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.data.repository.MediaCache
import com.example.domain.model.Episode
import com.example.domain.model.MediaItem
import com.example.domain.model.SeriesDetail
import com.example.domain.repository.MunowatchRepository
import com.example.domain.repository.SeriesRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvLoadingIndicator
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchFocusBorder
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextMuted
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesDetailsUiState(
    val isLoading: Boolean = true,
    val seriesDetail: SeriesDetail? = null,
    val selectedSeasonIndex: Int = 0,
    val isFavorite: Boolean = false
)

class SeriesDetailsViewModel(
    private val seriesId: String,
    private val seriesRepository: SeriesRepository,
    private val munowatchRepository: MunowatchRepository? = null
) : ViewModel() {

    private val cached = MediaCache.getSeriesDetail(seriesId)
    private val hasEpisodes = cached != null && cached.seasons.any { it.episodes.isNotEmpty() }

    private val _uiState = MutableStateFlow(
        SeriesDetailsUiState(
            isLoading = !hasEpisodes,
            seriesDetail = cached
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        if (!hasEpisodes) {
            loadSeriesDetails()
        } else {
            refreshSilent()
        }
        observeFavorite()
    }

    private fun loadSeriesDetails() {
        viewModelScope.launch {
            val result = seriesRepository.getSeriesDetails(seriesId)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                seriesDetail = result.getOrNull() ?: _uiState.value.seriesDetail
            )
        }
    }

    private fun refreshSilent() {
        viewModelScope.launch {
            try {
                val result = seriesRepository.getSeriesDetails(seriesId)
                val detail = result.getOrNull()
                if (detail != null && detail.seasons.any { it.episodes.isNotEmpty() }) {
                    _uiState.value = _uiState.value.copy(
                        seriesDetail = detail
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun observeFavorite() {
        if (munowatchRepository == null) return
        viewModelScope.launch {
            munowatchRepository.isFavorite(seriesId).collect { isFav ->
                _uiState.value = _uiState.value.copy(isFavorite = isFav)
            }
        }
    }

    fun toggleFavorite() {
        val detail = _uiState.value.seriesDetail ?: return
        if (munowatchRepository == null) return
        viewModelScope.launch {
            val mediaItem = MediaItem(
                id = seriesId,
                title = detail.title,
                posterUrl = detail.posterUrl,
                streamUrl = detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.streamUrl ?: "",
                isSeries = true,
                description = detail.description,
                vj = detail.vj,
                year = detail.year,
                genre = detail.genres.firstOrNull() ?: "Series"
            )
            munowatchRepository.toggleFavorite(mediaItem)
        }
    }

    fun selectSeason(index: Int) {
        _uiState.value = _uiState.value.copy(selectedSeasonIndex = index)
    }
}

@Composable
fun SeriesDetailsScreen(
    viewModel: SeriesDetailsViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler {
        onBack()
    }

    if (state.isLoading) {
        TvLoadingIndicator(message = "Loading series episodes...")
        return
    }

    val detail = state.seriesDetail
    if (detail == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MunowatchBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Could not load series details",
                    color = MunowatchTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Please check your network connection or try again",
                    color = MunowatchTextMuted,
                    fontSize = 14.sp
                )
                TvButton(
                    text = "Back to Catalog",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    isPrimary = true
                )
            }
        }
        return
    }

    val sortedSeasons = detail.seasons.sortedBy { it.number }
    val currentSeason = sortedSeasons.getOrNull(state.selectedSeasonIndex) ?: sortedSeasons.firstOrNull()
    val displayEpisodes = currentSeason?.episodes?.sortedWith(
        compareBy<Episode> { if (it.number > 0) it.number else Int.MAX_VALUE }.thenBy { it.id }
    ) ?: emptyList()

    val playEpisodeFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(detail.id) {
        kotlinx.coroutines.delay(150)
        try {
            playEpisodeFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
    ) {
        // Backdrop Image with gradient overlay
        if (detail.backdropUrl.isNotBlank() || detail.posterUrl.isNotBlank()) {
            AsyncImage(
                model = detail.backdropUrl.ifBlank { detail.posterUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MunowatchBackground.copy(alpha = 0.88f),
                            MunowatchBackground.copy(alpha = 0.96f),
                            MunowatchBackground
                        )
                    )
                )
        )

        // Main responsive TV grid containing hero details and episodes
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 190.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Top Bar
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    TvButton(
                        text = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        isPrimary = false,
                        modifier = Modifier
                            .focusRequester(backFocusRequester)
                            .focusProperties {
                                down = playEpisodeFocusRequester
                            }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Munowatch  /  Series",
                        color = MunowatchTextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Series Hero Section: Poster, Title, Badges, Full Description, and Controls
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Series Poster Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, MunowatchFocusBorder.copy(alpha = 0.5f)),
                        shadowElevation = 8.dp,
                        color = MunowatchSurfaceElevated,
                        modifier = Modifier
                            .width(170.dp)
                            .aspectRatio(2f / 3f)
                    ) {
                        if (detail.posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = detail.posterUrl,
                                contentDescription = detail.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(MunowatchSurfaceElevated, Color(0xFF14181E))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = MunowatchGreen,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = detail.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Metadata & Description Details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Badges Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Surface(
                                color = MunowatchGreen,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SERIES",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            val totalEpisodes = detail.seasons.sumOf { it.episodes.size }
                            if (totalEpisodes > 0) {
                                Surface(
                                    color = MunowatchSurfaceElevated,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MunowatchFocusBorder.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "$totalEpisodes EPISODES",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (detail.vj.isNotBlank()) {
                                Surface(
                                    color = MunowatchGreenLight.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MunowatchGreen.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = detail.vj,
                                        color = MunowatchGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (detail.year.isNotBlank()) {
                                Text(
                                    text = detail.year,
                                    color = MunowatchTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Series Title
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Series Description Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MunowatchGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SERIES MOVIE DESCRIPTION",
                                color = MunowatchGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }

                        // Restored Series Movie Description Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MunowatchSurfaceElevated.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MunowatchFocusBorder.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            val descriptionText = detail.description.takeIf { it.isNotBlank() }
                                ?: "Watch ${detail.title} translated in Luganda by top Veejays (${detail.vj.ifBlank { "VJ Junior" }}). Stream all full HD episodes exclusively on Munowatch TV."
                            val textColor = if (detail.description.isNotBlank()) {
                                MunowatchTextPrimary.copy(alpha = 0.95f)
                            } else {
                                MunowatchTextSecondary.copy(alpha = 0.85f)
                            }
                            Text(
                                text = descriptionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        // Action Buttons: Play First Episode & Favorite Toggle
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val firstEp = currentSeason?.episodes?.firstOrNull() ?: detail.seasons.firstOrNull()?.episodes?.firstOrNull()
                            if (firstEp != null) {
                                val buttonTitle = if (firstEp.title.startsWith("Episode", ignoreCase = true)) "Play ${firstEp.title}" else "Play Episode 1"
                                TvButton(
                                    text = buttonTitle,
                                    icon = Icons.Default.PlayArrow,
                                    modifier = Modifier
                                        .focusRequester(playEpisodeFocusRequester)
                                        .focusProperties {
                                            up = backFocusRequester
                                        },
                                    onClick = {
                                        val target = firstEp.streamUrl.takeIf { it.isNotBlank() && (it.contains("twolekede") || it.contains("mid=") || it.contains("preview.php") || it.contains("download.php")) }
                                            ?: Regex("""[?&]mid=([0-9]+)""").find(firstEp.streamUrl)?.let { "/twolekede?mid=${it.groupValues[1]}" }
                                            ?: firstEp.streamUrl.takeIf { it.isNotBlank() }
                                            ?: firstEp.id
                                        onPlayEpisode(target)
                                    },
                                    isPrimary = true
                                )
                            }

                            TvButton(
                                text = if (state.isFavorite) "In Favorites" else "Add to Favorites",
                                icon = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                onClick = { viewModel.toggleFavorite() },
                                isPrimary = false
                            )
                        }
                    }
                }
            }

            // Season Selector Tabs (if multiple seasons)
            if (detail.seasons.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(
                            text = "SEASONS",
                            color = MunowatchTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            detail.seasons.forEachIndexed { index, season ->
                                val isSelected = index == state.selectedSeasonIndex
                                val buttonText = if (season.episodes.isNotEmpty()) {
                                    "${season.title} (${season.episodes.size})"
                                } else {
                                    season.title
                                }
                                TvButton(
                                    text = buttonText,
                                    onClick = { viewModel.selectSeason(index) },
                                    isPrimary = isSelected
                                )
                            }
                        }
                    }
                }
            }

            // Episodes Section Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "EPISODES (${displayEpisodes.size})",
                            color = MunowatchGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Select an episode to start streaming in Full HD",
                            color = MunowatchTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Episodes Grid Items with guaranteed unique keys
            itemsIndexed(
                items = displayEpisodes,
                key = { index, episode -> "${episode.id}_$index" }
            ) { index, episode ->
                TvEpisodeGridCard(
                    episode = episode,
                    defaultPoster = detail.posterUrl,
                    onClick = {
                        val target = episode.streamUrl.takeIf { it.isNotBlank() && (it.contains("twolekede") || it.contains("mid=") || it.contains("preview.php") || it.contains("download.php")) }
                            ?: Regex("""[?&]mid=([0-9]+)""").find(episode.streamUrl)?.let { "/twolekede?mid=${it.groupValues[1]}" }
                            ?: episode.streamUrl.takeIf { it.isNotBlank() }
                            ?: episode.id
                        onPlayEpisode(target)
                    }
                )
            }
        }
    }
}

@Composable
private fun TvEpisodeGridCard(
    episode: Episode,
    defaultPoster: String = "",
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val posterUrl = episode.thumbnail.takeIf { it.isNotBlank() && !it.contains("logo") && !it.contains("icon") } ?: defaultPoster

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .testTag("episode_card_${episode.number}")
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter ||
                     keyEvent.key == Key.Enter ||
                     keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) MunowatchGreenLight else MunowatchSurfaceElevated,
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) Color.White else MunowatchFocusBorder.copy(alpha = 0.35f)
        ),
        shadowElevation = if (isFocused) 10.dp else 3.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Episode Poster Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFF14171E))
            ) {
                if (posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = "Episode ${episode.number} Poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF222834), Color(0xFF11141A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MunowatchGreen.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Gradient overlay over bottom of thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Episode Number Chip (Top-Left)
                Surface(
                    color = if (isFocused) Color.Black else MunowatchGreen,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "EP ${episode.number}",
                        color = if (isFocused) MunowatchGreenLight else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                // Play icon overlay in bottom-right
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(28.dp)
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isFocused) Color.White else Color.Black.copy(alpha = 0.65f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Episode",
                        tint = if (isFocused) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Episode Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = episode.title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Full HD • Translated",
                    color = if (isFocused) Color.Black.copy(alpha = 0.75f) else MunowatchTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

