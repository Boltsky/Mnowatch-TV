package com.example.presentation.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.R
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import com.example.data.repository.MediaCache
import com.example.data.network.NetworkClient
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.PlaybackSource
import com.example.domain.repository.PlaybackRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvErrorView
import com.example.ui.theme.MunowatchFocusBorder
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextMuted
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun cleanPlayerDisplayTitle(candidate: String?, contentId: String): String {
    val movieDetail = MediaCache.getMovieDetail(contentId)
    val seriesDetail = MediaCache.getSeriesDetail(contentId)
    val cachedItem = MediaCache.get(contentId)

    fun clean(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var cleaned = text.trim()
        if (cleaned.contains("%")) {
            try {
                cleaned = java.net.URLDecoder.decode(cleaned, "UTF-8")
            } catch (_: Exception) {}
        }
        cleaned = org.jsoup.parser.Parser.unescapeEntities(cleaned, true)

        // Remove "Watch translated in Luganda by VJ ...", "Watch translated", "Watch", etc.
        cleaned = cleaned.replace(Regex("""(?i)^watch\s+(?:luganda\s+)?translated(?:\s+in\s+luganda)?(?:\s+by\s+(?:vj\s+)?[a-z0-9\s]+)?\s*[-|:–—.]*\s*"""), "")
        cleaned = cleaned.replace(Regex("""(?i)^watch\s+luganda\s+translated\s*[-|:–—.]*\s*"""), "")
        cleaned = cleaned.replace(Regex("""(?i)^watch\s+(?:movies\s+online|tv\s+shows|movies|series)?\s*[-|:–—.]*\s*"""), "")
        cleaned = cleaned.replace(Regex("""(?i)^translated\s+(?:in\s+luganda\s+)?(?:\s+by\s+(?:vj\s+)?[a-z0-9\s]+)?\s*[-|:–—.]*\s*"""), "")
        cleaned = cleaned.replace(Regex("""(?i)^luganda\s+translated\s*[-|:–—.]*\s*"""), "")
        cleaned = cleaned.replace(Regex("""(?i)^by\s+(?:vj\s+)?[a-z0-9\s]+[-|:–—.]+\s*"""), "")
        cleaned = cleaned.replace(Regex("""(?i)^vj\s+[a-z0-9\s]+[-|:–—.]+\s*"""), "")

        // Remove trailing VJ / translation noise
        cleaned = cleaned.replace(Regex("""(?i)\s*[-|:–—.]*\s*translated(\s+in\s+luganda|\s+by\s+.*)?.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s+translated\s+in\s+luganda.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s+by\s+vj\s+[a-z0-9\s]+.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s*[-|:–—.]*\s*movies\s+online.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s*[-|:–—.]*\s*tv\s+shows.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s*[-|:–—.]*\s*twolekede.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s*[-|:–—.]*\s*munowatch.*$"""), "")
        return cleaned.trim(' ', '-', '–', '—', ':', '|', '.')
    }

    fun isGeneric(text: String): Boolean {
        if (text.isBlank() || text.length <= 2) return true
        val lower = text.lowercase()
        return lower in setOf(
            "munowatch", "twolekede", "home", "series", "movies", "free movies", "translated movies",
            "watch translated", "watch translated movies", "watch translated series", "translated",
            "luganda", "luganda translated", "movies online", "tv shows", "movies online & tv shows",
            "movies online and tv shows", "now playing", "watch", "play", "stream", "online & tv shows"
        ) || lower.startsWith("movie #") || lower.startsWith("watch ") || lower.contains("movies online") || lower.contains("tv shows")
    }

    // 1. Check cached catalog item first (most authentic for selected media)
    val cachedClean = clean(cachedItem?.title)
    if (cachedClean.isNotBlank() && !isGeneric(cachedClean)) {
        return cachedClean
    }

    // 2. Check movie or series details
    val movieClean = clean(movieDetail?.title)
    if (movieClean.isNotBlank() && !isGeneric(movieClean)) {
        return movieClean
    }
    val seriesClean = clean(seriesDetail?.title)
    if (seriesClean.isNotBlank() && !isGeneric(seriesClean)) {
        return seriesClean
    }

    // 3. Check candidate passed in
    val candidateClean = clean(candidate)
    if (candidateClean.isNotBlank() && !isGeneric(candidateClean)) {
        return candidateClean
    }

    // 4. Derive from contentId if it contains episode info
    if (contentId.contains("_ep") || contentId.startsWith("ep_")) {
        val epNum = Regex("""ep_?(\d+)""", RegexOption.IGNORE_CASE).find(contentId)?.groupValues?.get(1)
        val seriesName = contentId.substringBefore("_ep").removePrefix("series_").replace('_', ' ').trim()
        if (seriesName.isNotBlank() && !isGeneric(seriesName)) {
            val capitalized = seriesName.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            return if (epNum != null) "$capitalized - Episode $epNum" else capitalized
        }
    }

    // 5. Derive from contentId slug
    val slugTitle = clean(contentId.removePrefix("movie_").removePrefix("series_").replace('_', ' ').replace('-', ' '))
    if (slugTitle.isNotBlank() && !isGeneric(slugTitle) && !slugTitle.all { it.isDigit() }) {
        return slugTitle.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    return "Now Playing"
}

@Composable
fun InteractivePlayerTimeline(
    currentPositionMs: Long,
    totalDurationMs: Long,
    bufferedPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val bufferedFraction = if (totalDurationMs > 0) {
        (bufferedPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val effectiveFraction = if (isDragging) dragFraction else progressFraction

    var isFocused by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isFocused) 44.dp else 36.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (totalDurationMs > 0) {
                                val target = (currentPositionMs - 10000).coerceAtLeast(0L)
                                onSeek(target)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (totalDurationMs > 0) {
                                val target = (currentPositionMs + 10000).coerceAtMost(totalDurationMs)
                                onSeek(target)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    if (totalDurationMs > 0 && size.width > 0) {
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((fraction * totalDurationMs).toLong())
                    }
                }
            }
            .pointerInput(totalDurationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (totalDurationMs > 0 && size.width > 0) {
                            isDragging = true
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        if (isDragging && totalDurationMs > 0) {
                            isDragging = false
                            onSeek((dragFraction * totalDurationMs).toLong())
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (totalDurationMs > 0 && size.width > 0) {
                            dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()

        // Background track (height 6.dp or 8.dp when focused)
        val trackHeight = if (isFocused) 8.dp else 6.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .background(
                    if (isFocused) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.25f),
                    RoundedCornerShape(4.dp)
                )
                .then(
                    if (isFocused) Modifier.border(1.dp, MunowatchFocusBorder, RoundedCornerShape(4.dp))
                    else Modifier
                )
        )

        // Buffered track
        if (bufferedFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .height(trackHeight)
                    .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            )
        }

        // Played progress track
        if (effectiveFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(effectiveFraction)
                    .height(trackHeight)
                    .background(
                        Brush.horizontalGradient(
                            listOf(MunowatchGreen, MunowatchGreenLight)
                        ),
                        RoundedCornerShape(4.dp)
                    )
            )
        }

        // Thumb scrubber indicator
        val thumbOffsetDp = with(LocalDensity.current) {
            (effectiveFraction * totalWidthPx).toDp()
        }
        val thumbSize = if (isFocused) 22.dp else if (isDragging) 18.dp else 14.dp

        Box(
            modifier = Modifier
                .padding(start = (thumbOffsetDp - (thumbSize / 2)).coerceAtLeast(0.dp))
                .size(thumbSize)
                .background(Color.White, CircleShape)
                .border(if (isFocused) 3.dp else 2.dp, if (isFocused) MunowatchFocusBorder else MunowatchGreen, CircleShape)
        )
    }
}

data class StreamQualityOption(
    val id: String,
    val label: String,
    val resolutionLabel: String,
    val description: String,
    val height: Int,
    val width: Int = 0,
    val isAuto: Boolean = false
) {
    companion object {
        fun normalizeResolution(height: Int, width: Int = 0, tagHint: String? = null): Pair<Int, String> {
            val equivHeight = if (width > 0) (width * 9) / 16 else height

            if (height > 0 || width > 0) {
                return when {
                    height >= 2000 || width >= 3500 || equivHeight >= 2000 -> 2160 to "4K"
                    height >= 1350 || width >= 2400 || equivHeight >= 1350 -> 1440 to "1440p"
                    height >= 950 || width >= 1800 || equivHeight >= 950 -> 1080 to "1080p"
                    height >= 650 || width >= 1150 || equivHeight >= 650 -> 720 to "720p"
                    height >= 400 || equivHeight >= 400 -> 480 to "480p"
                    else -> 360 to "360p"
                }
            }

            val hint = tagHint.orEmpty().lowercase()
            val has4k = hint.contains("2160p") || hint.contains("2160") || hint.contains("4k") || hint.contains("uhd")
            val has1440 = hint.contains("1440p") || hint.contains("1440") || hint.contains("2k") || hint.contains("qhd")
            val has1080 = hint.contains("1080p") || hint.contains("1080") || hint.contains("fhd") || hint.contains("full hd")
            val has720 = hint.contains("720p") || hint.contains("720") || Regex("""\bhd\b""").containsMatchIn(hint)
            val has480 = hint.contains("480p") || hint.contains("480") || Regex("""\bsd\b""").containsMatchIn(hint)
            val has360 = hint.contains("360p") || hint.contains("360")

            return when {
                has4k -> 2160 to "4K"
                has1440 -> 1440 to "1440p"
                has1080 -> 1080 to "1080p"
                has720 -> 720 to "720p"
                has480 -> 480 to "480p"
                has360 -> 360 to "360p"
                else -> 720 to "720p"
            }
        }

        fun createAuto(renderedRes: String?): StreamQualityOption {
            val resLabel = if (renderedRes != null) "Auto ($renderedRes)" else "Auto (Best Quality)"
            val desc = if (renderedRes != null) {
                "Streams best available quality ($renderedRes) for your connection with zero buffering"
            } else {
                "Streams best available quality for your connection with zero buffering"
            }
            return StreamQualityOption(
                id = "AUTO",
                label = "Auto (Best Quality)",
                resolutionLabel = resLabel,
                description = desc,
                height = 0,
                width = 0,
                isAuto = true
            )
        }

        fun fromHeight(height: Int, width: Int = 0, isSingleQuality: Boolean = false, tagHint: String? = null): StreamQualityOption {
            val (stdHeight, stdRes) = normalizeResolution(height, width, tagHint)
            val fullResName = when {
                stdHeight >= 2160 -> "$stdRes 4K Ultra HD"
                stdHeight >= 1440 -> "$stdRes 2K QHD"
                stdHeight >= 1080 -> "$stdRes Full HD"
                stdHeight in 700..1079 -> "$stdRes HD"
                stdHeight in 420..699 -> "$stdRes SD"
                else -> "$stdRes Data Saver"
            }
            val label = if (isSingleQuality) "$fullResName (Source Quality)" else fullResName
            val desc = when {
                isSingleQuality -> "Original pristine source resolution uploaded for this title"
                stdHeight >= 2160 -> "Ultra-high definition 4K resolution with maximum detail"
                stdHeight >= 1440 -> "Quad high definition 2K resolution for high-end displays"
                stdHeight >= 1080 -> "Crisp, pristine full HD picture for fast and stable networks"
                stdHeight >= 700 -> "High definition with fast buffer for everyday connections"
                stdHeight >= 420 -> "Standard definition, ideal for medium connections"
                else -> "Low bandwidth stream that prevents buffering on weak networks"
            }
            val id = when {
                stdHeight >= 2160 -> "4K"
                stdHeight >= 1440 -> "1440P"
                stdHeight >= 1080 -> "1080P"
                stdHeight in 700..1079 -> "720P"
                stdHeight in 420..699 -> "480P"
                else -> "360P"
            }
            return StreamQualityOption(
                id = id,
                label = label,
                resolutionLabel = fullResName,
                description = desc,
                height = height,
                width = width,
                isAuto = false
            )
        }
    }
}


data class PlayerUiState(
    val isLoading: Boolean = true,
    val isAutoSwitching: Boolean = false,
    val statusMessage: String? = null,
    val title: String = "Munowatch Playback",
    val contentId: String = "",
    val quality: String = "HD",
    val activeServer: String = "Server 1 (Fast CDN)",
    val availableServers: List<String> = listOf("Server 1 (Fast CDN)", "Server 2 (Munowatch Stream)", "Server 3 (Direct Backup)"),
    val playbackSource: PlaybackSource? = null,
    val errorMessage: String? = null,
    val isAuthError: Boolean = false,
    val initialResumePositionMs: Long = 0L,
    val hasNextEpisode: Boolean = false,
    val hasPrevEpisode: Boolean = false,
    val nextEpisodeId: String? = null,
    val prevEpisodeId: String? = null
)

class PlayerViewModel(
    initialContentId: String,
    initialServer: String?,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private var currentContentId: String = initialContentId
    private val cachedItem = MediaCache.get(initialContentId)

    val fallbackServers = listOf(
        "Server 1 (Fast CDN)",
        "Server 2 (Munowatch Stream)",
        "Server 3 (Direct Backup)"
    )

    private var currentServerIndex = 0
    private var consecutive429Errors = 0
    private var encountered503Error = false
    private var encountered429Error = false
    private var encountered404Error = false
    private var encounteredAuthError = false
    private val failedStreamUrls = mutableSetOf<String>()
    private val failedStreamFiles = mutableSetOf<String>()
    private val failedStreamHosts = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            contentId = initialContentId,
            title = cleanPlayerDisplayTitle(cachedItem?.title, initialContentId),
            quality = cachedItem?.quality ?: "HD",
            activeServer = fallbackServers.first(),
            availableServers = fallbackServers,
            initialResumePositionMs = MediaCache.getSavedWatchPosition(initialContentId) ?: 0L
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        val initialIdx = fallbackServers.indexOfFirst { it.equals(initialServer, ignoreCase = true) }
        currentServerIndex = if (initialIdx >= 0) initialIdx else 0
        updateAdjacentEpisodes(initialContentId)
        viewModelScope.launch {
            val saved = playbackRepository.getSavedPosition(initialContentId)
            if (saved > 1000L && _uiState.value.initialResumePositionMs <= 1000L) {
                _uiState.value = _uiState.value.copy(initialResumePositionMs = saved)
            }
        }
        loadSource(fallbackServers[currentServerIndex])
    }

    private fun updateAdjacentEpisodes(contentId: String) {
        val (prev, next) = MediaCache.findAdjacentEpisodes(contentId)
        _uiState.value = _uiState.value.copy(
            hasPrevEpisode = prev != null,
            hasNextEpisode = next != null,
            prevEpisodeId = prev?.id ?: prev?.streamUrl,
            nextEpisodeId = next?.id ?: next?.streamUrl
        )
    }

    fun playContent(newContentId: String) {
        currentContentId = newContentId
        failedStreamUrls.clear()
        failedStreamFiles.clear()
        failedStreamHosts.clear()
        currentServerIndex = 0
        consecutive429Errors = 0
        encountered503Error = false
        encountered429Error = false
        encountered404Error = false
        encounteredAuthError = false
        val cached = MediaCache.get(newContentId)
        val initialPos = MediaCache.getSavedWatchPosition(newContentId) ?: 0L
        _uiState.value = _uiState.value.copy(
            contentId = newContentId,
            title = cleanPlayerDisplayTitle(cached?.title, newContentId),
            quality = cached?.quality ?: "HD",
            isLoading = true,
            isAutoSwitching = false,
            statusMessage = null,
            playbackSource = null,
            errorMessage = null,
            isAuthError = false,
            initialResumePositionMs = initialPos
        )
        updateAdjacentEpisodes(newContentId)
        viewModelScope.launch {
            val saved = playbackRepository.getSavedPosition(newContentId)
            if (saved > 1000L && _uiState.value.initialResumePositionMs <= 1000L) {
                _uiState.value = _uiState.value.copy(initialResumePositionMs = saved)
            }
        }
        loadSource(fallbackServers[0])
    }

    fun playNextEpisode() {
        val nextId = _uiState.value.nextEpisodeId
        if (!nextId.isNullOrBlank()) {
            playContent(nextId)
        }
    }

    fun playPreviousEpisode() {
        val prevId = _uiState.value.prevEpisodeId
        if (!prevId.isNullOrBlank()) {
            playContent(prevId)
        }
    }

    fun loadSource(serverName: String) {
        android.util.Log.d("MunowatchPlayer", "loadSource: contentId=$currentContentId, server=$serverName")
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            activeServer = serverName,
            errorMessage = null
        )
        viewModelScope.launch {
            val result = playbackRepository.getPlaybackSource(currentContentId, serverName)
            result.onSuccess { source ->
                android.util.Log.d("MunowatchPlayer", "loadSource SUCCESS: ${source.url}")
                val cleanUrl = source.url.replace("&amp;", "&").replace("&#38;", "&").trim()
                val fileParam = Regex("""[?&]file=([^&]+)""").find(cleanUrl)?.groupValues?.get(1)
                val isHostRateLimited = (encountered429Error || com.example.data.api.MunowatchApi.isCdnRateLimited()) && cleanUrl.contains("movviez.com")
                val isKnownBad = failedStreamUrls.contains(cleanUrl) ||
                    (!fileParam.isNullOrBlank() && failedStreamFiles.contains(fileParam)) ||
                    isHostRateLimited
                if (isKnownBad) {
                    // Stream URL or file is already known to return 404, 429, timeout, or fail
                    if (currentServerIndex < fallbackServers.size - 1) {
                        currentServerIndex++
                        val nextServer = fallbackServers[currentServerIndex]
                        val switchNotice = if (nextServer.contains("Munowatch", ignoreCase = true)) {
                            "Auto-routing past bandwidth limit via Munowatch stream..."
                        } else {
                            "Auto-routing via backup server ($nextServer)..."
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            isAutoSwitching = true,
                            statusMessage = switchNotice,
                            activeServer = nextServer
                        )
                        delay(200)
                        loadSource(nextServer)
                        return@launch
                    } else {
                        val failMsg = when {
                            encountered503Error ->
                                "This title was added recently and is still being uploaded to the streaming servers. Please try again in a little while or select another title."
                            encountered404Error ->
                                "This stream is currently unavailable on the server (HTTP 404 - File Not Found). Please select another title."
                            encountered429Error -> {
                                "Daily media server bandwidth limit reached for this network. Please tap Retry or switch to another server."
                            }
                            else ->
                                "This title is currently unavailable across all streaming servers. Please select another movie or episode."
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAutoSwitching = false,
                            statusMessage = null,
                            errorMessage = failMsg,
                            isAuthError = false
                        )
                        return@launch
                    }
                }

                val cachedTitle = MediaCache.get(currentContentId)?.title?.takeIf { it.isNotBlank() }
                    ?: MediaCache.getMovieDetail(currentContentId)?.title?.takeIf { it.isNotBlank() }
                    ?: MediaCache.getSeriesDetail(currentContentId)?.title?.takeIf { it.isNotBlank() }

                val rawCandidate = cachedTitle
                    ?: source.headers["X-Title"]?.takeIf { it.isNotBlank() }
                    ?: _uiState.value.title
                val movieTitle = cleanPlayerDisplayTitle(rawCandidate, currentContentId)
                val itemQuality = MediaCache.get(currentContentId)?.quality
                    ?: cachedItem?.quality
                    ?: _uiState.value.quality
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAutoSwitching = false,
                    statusMessage = null,
                    title = movieTitle,
                    quality = itemQuality,
                    playbackSource = source.copy(url = cleanUrl),
                    errorMessage = null
                )
            }.onFailure { error ->
                val rawMsg = error.message.orEmpty()
                if (currentServerIndex < fallbackServers.size - 1) {
                    currentServerIndex++
                    val nextServer = fallbackServers[currentServerIndex]
                    val switchNotice = if (nextServer.contains("Munowatch", ignoreCase = true)) {
                        "Auto-routing past bandwidth limit via Munowatch stream..."
                    } else {
                        "Auto-routing via backup server ($nextServer)..."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = true,
                        isAutoSwitching = true,
                        statusMessage = switchNotice,
                        activeServer = nextServer
                    )
                    delay(200)
                    loadSource(nextServer)
                } else {
                    val cleanMsg = when {
                        rawMsg.contains("Authentication", ignoreCase = true) || rawMsg.contains("session", ignoreCase = true) || rawMsg.contains("sign in", ignoreCase = true) || rawMsg.contains("subscription", ignoreCase = true) ->
                            "This title is currently unavailable on media servers. Please select another title."
                        rawMsg.contains("429") || rawMsg.contains("bandwidth", ignoreCase = true) ->
                            "Daily media server bandwidth limit reached for this network. Please tap Retry or switch to another server."
                        rawMsg.isNotBlank() -> rawMsg
                        else -> "Stream unavailable across all streaming servers. Please select another title or try again."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAutoSwitching = false,
                        statusMessage = null,
                        errorMessage = cleanMsg,
                        isAuthError = false
                    )
                }
            }
        }
    }

    fun handlePlaybackError(errorReason: String, failedUrl: String? = null) {
        val is429 = errorReason.contains("429") || errorReason.contains("Too Many Requests", ignoreCase = true) || errorReason.contains("bandwidth limit", ignoreCase = true)
        val is503 = errorReason.contains("503") || errorReason.contains("Still uploading", ignoreCase = true) || errorReason.contains("Service Unavailable", ignoreCase = true)
        val is404 = errorReason.contains("404") || errorReason.contains("Not Found", ignoreCase = true)

        if (is429) {
            encountered429Error = true
            consecutive429Errors++
            com.example.data.api.MunowatchApi.markCdnRateLimited()
        }
        if (is503) {
            encountered503Error = true
        }
        val isTransientTimeout = errorReason.contains("timeout", ignoreCase = true) ||
            errorReason.contains("timed out", ignoreCase = true) ||
            errorReason.contains("stalled", ignoreCase = true) ||
            errorReason.contains("reset", ignoreCase = true)

        if (is404 && !isTransientTimeout && currentServerIndex >= fallbackServers.size - 1) {
            encountered404Error = true
        }

        // Blacklist failed URL so it won't be re-selected on retry
        if (!failedUrl.isNullOrBlank()) {
            val clean = failedUrl.replace("&amp;", "&").replace("&#38;", "&").trim()
            failedStreamUrls.add(clean)
            val fileParam = Regex("""[?&]file=([^&]+)""").find(clean)?.groupValues?.get(1)
            // Only blacklist the entire file if we received a permanent 404 (file missing on CDN),
            // NEVER for transient stalls or timeouts, so backup servers and alternate pull zones can still be tried.
            if (is404 && !fileParam.isNullOrBlank()) {
                failedStreamFiles.add(fileParam)
            }
        }
        viewModelScope.launch {
            playbackRepository.evictPlaybackSource(currentContentId)
            MediaCache.evictPlaybackSource(currentContentId)
            if (!failedUrl.isNullOrBlank()) {
                MediaCache.evictPlaybackSource(failedUrl)
            }

            val munoIdx = fallbackServers.indexOfFirst { it.contains("Munowatch", ignoreCase = true) }
            val nextServerIndex = if (is429 && munoIdx >= 0 && currentServerIndex != munoIdx) {
                // On 429 rate limit on Movviez CDN, fast-failover directly to independent Munowatch BunnyCDN stream (Server 2)
                munoIdx
            } else if (currentServerIndex < fallbackServers.size - 1) {
                currentServerIndex + 1
            } else {
                -1
            }

            if (nextServerIndex >= 0) {
                currentServerIndex = nextServerIndex
                val nextServer = fallbackServers[currentServerIndex]
                val switchNotice = if (nextServer.contains("Munowatch", ignoreCase = true)) {
                    "Auto-routing past bandwidth limit via Munowatch stream..."
                } else if (is503) {
                    "Checking alternate server node ($nextServer)..."
                } else if (is429) {
                    "Auto-routing via backup server ($nextServer)..."
                } else {
                    "Auto-switching to backup server ($nextServer)..."
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    isAutoSwitching = true,
                    statusMessage = switchNotice,
                    activeServer = nextServer,
                    playbackSource = null,
                    errorMessage = null
                )
                delay(200)
                loadSource(nextServer)
            } else {
                val userMsg = when {
                    encountered503Error ->
                        "This title was added recently and is still being uploaded to the streaming servers. Please try again in a little while or select another title."
                    isTransientTimeout || errorReason.contains("timeout", ignoreCase = true) ->
                        "The media server took longer than expected to buffer. Please tap Retry or switch to a backup server below."
                    encountered404Error ->
                        "This stream is currently unavailable on the server (HTTP 404 - File Not Found). Please select another title."
                    encountered429Error -> {
                        "Daily media server bandwidth limit reached on this network. Please tap Retry to resume playing or switch servers."
                    }
                    else -> {
                        val cleaned = errorReason.replace(Regex("""(?i)sign\s*in|session|account|authentication"""), "").trim()
                        if (cleaned.isNotBlank()) "$cleaned. All streaming servers attempted." else "Stream unavailable across all streaming servers. Please select another title."
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAutoSwitching = false,
                    statusMessage = null,
                    errorMessage = userMsg,
                    isAuthError = false
                )
            }
        }
    }

    fun onPlaybackStarted() {
        consecutive429Errors = 0
    }

    fun retryFromBeginning() {
        failedStreamUrls.clear()
        failedStreamFiles.clear()
        failedStreamHosts.clear()
        currentServerIndex = 0
        consecutive429Errors = 0
        encountered503Error = false
        encountered429Error = false
        encountered404Error = false
        encounteredAuthError = false
        com.example.data.api.MunowatchApi.resetCdnRateLimit()
        viewModelScope.launch {
            playbackRepository.evictPlaybackSource(currentContentId)
            MediaCache.evictPlaybackSource(currentContentId)
            loadSource(fallbackServers[0])
        }
    }

    fun switchServer(serverName: String) {
        val idx = fallbackServers.indexOf(serverName)
        if (idx >= 0) currentServerIndex = idx
        failedStreamHosts.clear()
        loadSource(serverName)
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (positionMs > 1000L) {
            MediaCache.saveWatchPosition(currentContentId, positionMs)
        }
        if (durationMs <= 0) return
        val currentItem = MediaCache.get(currentContentId)
        val poster = currentItem?.posterUrl ?: ""
        val title = _uiState.value.title.takeIf { it != "Munowatch Playback" && it != "Now Playing" }
            ?: currentItem?.title
            ?: "Movie #$currentContentId"
        viewModelScope.launch {
            playbackRepository.saveWatchProgress(
                ContinueWatchingItem(
                    contentId = currentContentId,
                    title = title,
                    posterUrl = poster,
                    isSeries = currentItem?.isSeries ?: false,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    lastWatchedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    networkClient: NetworkClient,
    onBack: () -> Unit,
    onNavigateToAuth: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state by viewModel.uiState.collectAsState()

    var isPlaying by remember { mutableStateOf(false) }
    var playWhenReadyState by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlayerLoading by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var savedPlaybackPositionMs by remember { mutableLongStateOf(state.initialResumePositionMs.takeIf { it > 1000L } ?: 0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var controlsTimeoutKey by remember { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var hasAppliedResumeForCurrentMedia by remember(state.contentId, state.playbackSource) { mutableStateOf(false) }
    var hasAttemptedSeekRecovery by remember(state.contentId, state.playbackSource) { mutableStateOf(false) }
    var bufferingPhase by remember(state.contentId, state.playbackSource) { mutableIntStateOf(0) }
    var selectedQualityId by remember {
        mutableStateOf(MediaCache.getPreferredStreamQuality())
    }
    var detectedVideoHeights by remember {
        mutableStateOf<List<Int>>(emptyList())
    }
    var detectedVideoWidth by remember {
        mutableIntStateOf(0)
    }
    var currentRenderedResolution by remember {
        mutableStateOf<String?>(null)
    }
    var showQualityDialog by remember { mutableStateOf(false) }
    var tracksRevision by remember { mutableIntStateOf(0) }

    // Reset detected video qualities when media changes
    LaunchedEffect(state.contentId, state.playbackSource) {
        detectedVideoHeights = emptyList()
        detectedVideoWidth = 0
        currentRenderedResolution = null
    }

    // When content changes (e.g. episode switch), update resume state
    LaunchedEffect(state.contentId) {
        currentPosition = 0L
        savedPlaybackPositionMs = state.initialResumePositionMs.takeIf { it > 1000L } ?: 0L
        hasAppliedResumeForCurrentMedia = false
    }

    // When saved resume position arrives from Room
    LaunchedEffect(state.initialResumePositionMs) {
        if (state.initialResumePositionMs > 1000L && savedPlaybackPositionMs <= 1000L) {
            savedPlaybackPositionMs = state.initialResumePositionMs
        }
    }

    val focusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val qualityDialogFirstItemFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }
    val startFocusRequester = remember { FocusRequester() }
    val prev10FocusRequester = remember { FocusRequester() }
    val next10FocusRequester = remember { FocusRequester() }
    val next30FocusRequester = remember { FocusRequester() }
    val nextEpFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(50)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
    val initialNetworkConnected = remember(context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val active = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(active)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            true
        }
    }
    var isNetworkConnected by remember { mutableStateOf(initialNetworkConnected) }

    // Register network callback to monitor online status and prevent player crashes
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkConnected = true
                if (playbackError?.contains("offline", ignoreCase = true) == true ||
                    playbackError?.contains("network", ignoreCase = true) == true) {
                    playbackError = null
                    retryKey++
                }
            }

            override fun onLost(network: Network) {
                isNetworkConnected = false
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, callback)
            val active = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(active)
            isNetworkConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            isNetworkConnected = true
        }

        onDispose {
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }

    // Auto-hide controls overlay after 8 seconds of inactivity (paused while quality dialog is open)
    LaunchedEffect(showControls, controlsTimeoutKey, showQualityDialog) {
        if (showControls && !showQualityDialog) {
            delay(8000)
            showControls = false
        }
    }

    // Initialize AndroidX Media3 ExoPlayer once with decoder fallback & TV audio attributes
    val appContext = context.applicationContext
    val bandwidthMeter = remember {
        androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(appContext)
            .setInitialBitrateEstimate(25_000_000L) // 25 Mbps: Ensures initial adaptive track selection starts at highest available quality (1080p/720p)
            .build()
    }

    val okHttpDataSourceFactory = remember(bandwidthMeter) {
        OkHttpDataSource.Factory(networkClient.streamOkHttpClient)
            .setUserAgent(com.example.data.network.NetworkClient.USER_AGENT)
            .setTransferListener(object : androidx.media3.datasource.TransferListener {
                override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                    bandwidthMeter.onTransferInitializing(source, dataSpec, isNetwork)
                    NetworkClient.onStreamActivity()
                }
                override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                    bandwidthMeter.onTransferStart(source, dataSpec, isNetwork)
                    NetworkClient.onStreamActivity()
                }
                override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                    bandwidthMeter.onBytesTransferred(source, dataSpec, isNetwork, bytesTransferred)
                    if (bytesTransferred > 0) {
                        NetworkClient.onStreamBytesRead(bytesTransferred.toLong())
                    }
                }
                override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                    bandwidthMeter.onTransferEnd(source, dataSpec, isNetwork)
                    NetworkClient.onStreamActivity()
                }
            })
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://twolekede.com/",
                    "Accept" to "*/*"
                )
            )
    }

    val trackSelector = remember {
        DefaultTrackSelector(appContext).apply {
            setParameters(
                buildUponParameters()
                    .clearVideoSizeConstraints()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            )
        }
    }

    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 30_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    val exoPlayer = remember {
        // Fast-failover load error handling policy that delegates quickly to backup servers
        val loadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy(1) {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val exception = loadErrorInfo.exception
                val rootCause = exception.cause
                val isDnsOrOffline = exception is java.net.UnknownHostException ||
                    rootCause is java.net.UnknownHostException ||
                    exception.message?.contains("UnknownHostException", ignoreCase = true) == true ||
                    exception.message?.contains("no network", ignoreCase = true) == true
                if (isDnsOrOffline) {
                    return C.TIME_UNSET // Stop automatic retries immediately when offline
                }
                // Stop retries immediately on file data source or local file errors
                if (exception is androidx.media3.datasource.FileDataSource.FileDataSourceException ||
                    rootCause is java.io.FileNotFoundException ||
                    exception.message?.contains("FileNotFoundException", ignoreCase = true) == true ||
                    exception.message?.contains("open failed: ENOENT", ignoreCase = true) == true
                ) {
                    return C.TIME_UNSET
                }

                val httpEx = (exception as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                    ?: (rootCause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                val code = httpEx?.responseCode ?: if (exception.message?.contains("Response code: 429") == true) 429 else 0

                // Stop retries immediately on HTTP 4xx errors (e.g. 401, 403, 404, 429) or HTTP 5xx
                val shouldStopRetrying = (code in 400..599) ||
                    (exception.message?.contains("Response code: 4") == true) ||
                    (exception.message?.contains("Response code: 5") == true)
                if (shouldStopRetrying) {
                    return C.TIME_UNSET
                }

                val isTimeoutOrConn = (exception is java.net.SocketTimeoutException) ||
                    (rootCause is java.net.SocketTimeoutException) ||
                    (exception is java.net.ConnectException) ||
                    (rootCause is java.net.ConnectException) ||
                    (exception is java.net.SocketException) ||
                    (rootCause is java.net.SocketException) ||
                    (exception is javax.net.ssl.SSLException) ||
                    (rootCause is javax.net.ssl.SSLException) ||
                    (exception is java.io.InterruptedIOException) ||
                    (rootCause is java.io.InterruptedIOException) ||
                    (exception.message?.contains("reset", ignoreCase = true) == true) ||
                    (exception.message?.contains("unexpected end", ignoreCase = true) == true) ||
                    (exception.message?.contains("connection", ignoreCase = true) == true) ||
                    (exception.message?.contains("timeout", ignoreCase = true) == true)
                if (isTimeoutOrConn) {
                    if (loadErrorInfo.errorCount <= 4) {
                        return 500L // Quick retry to survive Cloudflare TLS renegotiation
                    }
                    return C.TIME_UNSET
                }
                return C.TIME_UNSET // Immediate failover: let PlayerViewModel switch servers instantly
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                return 3
            }
        }

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val dataSourceFactory = DefaultDataSource.Factory(appContext, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

        // Enable standard decoder fallback and TV audio attributes
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
            setMediaCodecSelector(MediaCodecSelector.DEFAULT)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(appContext, renderersFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(false)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        playWhenReadyState = this@apply.playWhenReady
                        if (playing) {
                            viewModel.onPlaybackStarted()
                        }
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        playWhenReadyState = playWhenReady
                        isPlaying = playWhenReady && (this@apply.playbackState != Player.STATE_ENDED)
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.height > 0) {
                            val hint = state.playbackSource?.url.orEmpty() + " " + state.title + " " + state.quality + " " + (com.example.data.repository.MediaCache.get(state.contentId)?.quality.orEmpty())
                            val (stdH, _) = StreamQualityOption.normalizeResolution(videoSize.height, videoSize.width, hint)
                            val displayRes = when {
                                stdH >= 2160 -> "4K Ultra HD"
                                stdH >= 1440 -> "1440p QHD"
                                stdH >= 1080 -> "1080p Full HD"
                                stdH in 700..1079 -> "720p HD"
                                stdH in 420..699 -> "480p SD"
                                else -> "360p Data Saver"
                            }
                            currentRenderedResolution = displayRes
                            if (videoSize.width > 0) {
                                detectedVideoWidth = videoSize.width
                            }
                            val currentList = detectedVideoHeights
                            if (!currentList.contains(videoSize.height)) {
                                detectedVideoHeights = (currentList + videoSize.height).distinct().sortedDescending()
                            }
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        tracksRevision++
                        val heights = mutableSetOf<Int>()
                        val hint = state.playbackSource?.url.orEmpty() + " " + state.title + " " + state.quality + " " + (com.example.data.repository.MediaCache.get(state.contentId)?.quality.orEmpty())
                        for (group in tracks.groups) {
                            if (group.type == C.TRACK_TYPE_VIDEO) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    if (format.height > 0) {
                                        heights.add(format.height)
                                    }
                                    if (group.isTrackSelected(i) && format.height > 0) {
                                        val (stdH, _) = StreamQualityOption.normalizeResolution(format.height, format.width, hint)
                                        val displayRes = when {
                                            stdH >= 2160 -> "4K Ultra HD"
                                            stdH >= 1440 -> "1440p QHD"
                                            stdH >= 1080 -> "1080p Full HD"
                                            stdH in 700..1079 -> "720p HD"
                                            stdH in 420..699 -> "480p SD"
                                            else -> "360p Data Saver"
                                        }
                                        currentRenderedResolution = displayRes
                                        if (format.width > 0) {
                                            detectedVideoWidth = format.width
                                        }
                                    }
                                }
                            }
                        }
                        if (heights.isNotEmpty()) {
                            detectedVideoHeights = (detectedVideoHeights + heights).distinct().sortedDescending()
                        }
                    }

                    override fun onIsLoadingChanged(loading: Boolean) {
                        isPlayerLoading = loading
                        bufferedPosition = this@apply.bufferedPosition.coerceAtLeast(0L)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = (playbackState == Player.STATE_BUFFERING)
                        if (playbackState == Player.STATE_ENDED) {
                            isPlaying = false
                            playWhenReadyState = false
                        } else if (playbackState == Player.STATE_READY) {
                            isPlaying = this@apply.playWhenReady
                            playWhenReadyState = this@apply.playWhenReady
                            viewModel.onPlaybackStarted()
                            val h = videoSize.height
                            if (h > 0 && !detectedVideoHeights.contains(h)) {
                                detectedVideoHeights = (detectedVideoHeights + h).distinct().sortedDescending()
                            }
                            totalDuration = duration.coerceAtLeast(0L)
                            bufferedPosition = this@apply.bufferedPosition.coerceAtLeast(0L)
                            if (!hasAppliedResumeForCurrentMedia && savedPlaybackPositionMs > 1000L && this@apply.currentPosition < 1000L) {
                                hasAppliedResumeForCurrentMedia = true
                                val effectiveResumePos = if (totalDuration > 15_000L && savedPlaybackPositionMs >= totalDuration - 10_000L) {
                                    0L // Video was already watched to the end, restart from beginning
                                } else {
                                    savedPlaybackPositionMs
                                }
                                if (effectiveResumePos > 1000L) {
                                    android.util.Log.d("PlayerScreen", "Applying resume seek to ${effectiveResumePos}ms")
                                    seekTo(effectiveResumePos)
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val lastPos = this@apply.currentPosition
                        if (lastPos > 1000L) {
                            savedPlaybackPositionMs = lastPos
                        }
                        try {
                            this@apply.stop()
                            this@apply.clearMediaItems()
                        } catch (_: Exception) {}

                        android.util.Log.w("PlayerScreen", "ExoPlayer playback error [${error.errorCodeName} / ${error.errorCode}]: ${error.message}")
                        val rootCause = error.cause
                        var currentCause: Throwable? = error
                        var errCode = 0
                        while (currentCause != null) {
                            if (currentCause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                                errCode = currentCause.responseCode
                                break
                            }
                            val msg = currentCause.message.orEmpty()
                            if (msg.contains("Response code: 401") || msg.contains("401 Unauthorized")) {
                                errCode = 401
                                break
                            } else if (msg.contains("Response code: 403") || msg.contains("403 Forbidden")) {
                                errCode = 403
                                break
                            } else if (msg.contains("Response code: 503") || msg.contains("503 Service Unavailable")) {
                                errCode = 503
                                break
                            } else if (msg.contains("Response code: 429") || msg.contains("429 Too Many Requests")) {
                                errCode = 429
                                break
                            } else if (msg.contains("Response code: 404") || msg.contains("404 Not Found")) {
                                errCode = 404
                                break
                            }
                            currentCause = currentCause.cause
                        }

                        val isFileNotFound = error.cause is androidx.media3.datasource.FileDataSource.FileDataSourceException ||
                            rootCause is java.io.FileNotFoundException ||
                            error.message?.contains("FileNotFoundException", ignoreCase = true) == true ||
                            error.message?.contains("open failed: ENOENT", ignoreCase = true) == true

                        val reason = when {
                            isFileNotFound -> "Stream source link invalid"
                            errCode == 401 -> "Authentication required (HTTP 401 Unauthorized)"
                            errCode == 403 -> "Access forbidden (HTTP 403 Forbidden)"
                            errCode == 503 -> "Server returned HTTP 503 (Still uploading)"
                            errCode == 429 -> "Server returned HTTP 429 (Daily bandwidth limit)"
                            errCode == 404 -> "Server returned HTTP 404 (File Not Found)"
                            errCode > 0 -> "Server returned HTTP $errCode"
                            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                                "Video decoder initialization failed"
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                "Server connection timeout"
                            rootCause is java.net.UnknownHostException ->
                                "Video server host unreachable"
                            else -> error.message?.takeIf { it != "Source error" } ?: "Stream temporarily unavailable"
                        }
                        val failedUrl = state.playbackSource?.url
                            ?: this@apply.currentMediaItem?.localConfiguration?.uri?.toString()
                        viewModel.handlePlaybackError(reason, failedUrl)
                    }
                })
            }
    }

    LaunchedEffect(showQualityDialog) {
        if (showQualityDialog) {
            val h = exoPlayer.videoSize.height
            val w = exoPlayer.videoSize.width
            val hint = state.playbackSource?.url.orEmpty() + " " + state.title + " " + state.quality + " " + (com.example.data.repository.MediaCache.get(state.contentId)?.quality.orEmpty())
            if (h > 0) {
                if (!detectedVideoHeights.contains(h)) {
                    detectedVideoHeights = (detectedVideoHeights + h).distinct().sortedDescending()
                }
                if (w > 0) detectedVideoWidth = w
                if (currentRenderedResolution == null) {
                    val (stdH, _) = StreamQualityOption.normalizeResolution(h, w, hint)
                    val displayRes = when {
                        stdH >= 2160 -> "4K Ultra HD"
                        stdH >= 1440 -> "1440p QHD"
                        stdH >= 1080 -> "1080p Full HD"
                        stdH in 700..1079 -> "720p HD"
                        stdH in 420..699 -> "480p SD"
                        else -> "360p Data Saver"
                    }
                    currentRenderedResolution = displayRes
                }
            }
            val trackHeights = mutableSetOf<Int>()
            for (group in exoPlayer.currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        val f = group.getTrackFormat(i)
                        if (f.height > 0) trackHeights.add(f.height)
                    }
                }
            }
            if (trackHeights.isNotEmpty()) {
                detectedVideoHeights = (detectedVideoHeights + trackHeights).distinct().sortedDescending()
            }
        }
    }

    val effectiveHeights = remember(detectedVideoHeights, currentRenderedResolution, showQualityDialog, tracksRevision) {
        val tracks = exoPlayer.currentTracks
        val tagHint = state.playbackSource?.url.orEmpty() + " " + state.title + " " + state.quality + " " + (com.example.data.repository.MediaCache.get(state.contentId)?.quality.orEmpty())
        val set = mutableSetOf<Int>()

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    if (f.height > 0 || f.width > 0) {
                        val (stdH, _) = StreamQualityOption.normalizeResolution(f.height, f.width, tagHint)
                        set.add(stdH)
                    }
                }
            }
        }

        if (set.isEmpty()) {
            val rawH = exoPlayer.videoSize.height
            val rawW = exoPlayer.videoSize.width
            if (rawH > 0 || rawW > 0) {
                val (stdH, _) = StreamQualityOption.normalizeResolution(rawH, rawW, tagHint)
                set.add(stdH)
            } else {
                for (dh in detectedVideoHeights) {
                    val (stdH, _) = StreamQualityOption.normalizeResolution(dh, detectedVideoWidth, tagHint)
                    set.add(stdH)
                }
                if (set.isEmpty()) {
                    val parsed = Regex("""\b(\d{3,4})p?""").find(currentRenderedResolution.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        set.add(parsed)
                    } else {
                        set.add(720)
                    }
                }
            }
        }
        set.sortedDescending()
    }

    val availableQualities: List<StreamQualityOption> = remember(effectiveHeights, currentRenderedResolution, detectedVideoWidth, state.playbackSource?.url, state.title, state.quality, state.contentId) {
        val tagHint = state.playbackSource?.url.orEmpty() + " " + state.title + " " + state.quality + " " + (com.example.data.repository.MediaCache.get(state.contentId)?.quality.orEmpty())
        val auto = StreamQualityOption.createAuto(currentRenderedResolution)
        if (effectiveHeights.isEmpty()) {
            listOf(auto)
        } else {
            val isSingle = (effectiveHeights.size == 1)
            val nonAutoList = mutableListOf<StreamQualityOption>()
            val seenIds = mutableSetOf<String>()
            for (h in effectiveHeights) {
                val w = if (detectedVideoWidth > 0) detectedVideoWidth else (h * 24) / 9
                val opt = StreamQualityOption.fromHeight(h, w, isSingleQuality = isSingle, tagHint = tagHint)
                if (seenIds.add(opt.id)) {
                    nonAutoList.add(opt)
                }
            }
            listOf(auto) + nonAutoList
        }
    }

    val selectedOption = remember(availableQualities, selectedQualityId) {
        availableQualities.firstOrNull { it.id.equals(selectedQualityId, ignoreCase = true) }
            ?: availableQualities.firstOrNull { !it.isAuto && "${it.height}P".equals(selectedQualityId, ignoreCase = true) }
            ?: availableQualities.firstOrNull { it.isAuto }
            ?: availableQualities.first()
    }

    val applyQualitySelection: (StreamQualityOption) -> Unit = remember(trackSelector, exoPlayer) {
        { qualityOption ->
            selectedQualityId = qualityOption.id
            MediaCache.setPreferredStreamQuality(qualityOption.id)
            val builder = trackSelector.buildUponParameters()
            if (qualityOption.isAuto) {
                builder.clearVideoSizeConstraints()
                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                builder.setMaxVideoBitrate(Int.MAX_VALUE)
                builder.setExceedRendererCapabilitiesIfNecessary(true)
                builder.setExceedVideoConstraintsIfNecessary(true)
                builder.setAllowVideoMixedMimeTypeAdaptiveness(true)
                builder.setAllowVideoNonSeamlessAdaptiveness(true)
                builder.setForceHighestSupportedBitrate(false)
                val currentOverrides = trackSelector.parameters.overrides
                for ((trackGroup, _) in currentOverrides) {
                    if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                        builder.clearOverride(trackGroup)
                    }
                }
            } else {
                val targetHeight = qualityOption.height
                val targetWidth = kotlin.math.max(
                    if (qualityOption.width > 0) qualityOption.width else 0,
                    (targetHeight * 24) / 9
                )
                builder.setMaxVideoSize(targetWidth + 400, targetHeight + 100)
                builder.setMaxVideoBitrate(Int.MAX_VALUE)
                builder.setExceedRendererCapabilitiesIfNecessary(true)
                builder.setExceedVideoConstraintsIfNecessary(true)
                builder.setForceHighestSupportedBitrate(true)
                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                val currentOverrides = trackSelector.parameters.overrides
                for ((trackGroup, _) in currentOverrides) {
                    if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                        builder.clearOverride(trackGroup)
                    }
                }

                val tracks = exoPlayer.currentTracks
                var bestGroup: androidx.media3.common.TrackGroup? = null
                var bestTrackIdx = -1
                var closestDiff = Int.MAX_VALUE
                var bestBitrate = -1

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val effHeight = if (format.width > 0 && format.height > 0 && (format.width * 9) / 16 > format.height) {
                                (format.width * 9) / 16
                            } else {
                                format.height
                            }
                            if (effHeight > 0) {
                                val diff = kotlin.math.abs(effHeight - targetHeight)
                                val currentBitrate = maxOf(format.bitrate, format.peakBitrate)
                                if (diff < closestDiff || (diff == closestDiff && currentBitrate > bestBitrate)) {
                                    closestDiff = diff
                                    bestBitrate = currentBitrate
                                    bestGroup = group.mediaTrackGroup
                                    bestTrackIdx = i
                                }
                            }
                        }
                    }
                }
                if (bestGroup != null && bestTrackIdx != -1) {
                    builder.setOverrideForType(
                        TrackSelectionOverride(bestGroup, listOf(bestTrackIdx))
                    )
                }
            }
            val newParameters = builder.build()
            if (trackSelector.parameters != newParameters) {
                trackSelector.setParameters(newParameters)
            }
        }
    }

    LaunchedEffect(selectedOption, detectedVideoHeights, tracksRevision) {
        applyQualitySelection(selectedOption)
    }

    // Watchdog timer: Monitor buffer progress and network activity; only auto-switch when completely stalled
    LaunchedEffect(isBuffering, state.playbackSource) {
        if (isBuffering && state.playbackSource != null) {
            val streamUrlToReport = state.playbackSource?.url
            var lastBufferedPos = exoPlayer.bufferedPosition
            var lastBytesTransferred = NetworkClient.totalBytes
            var stallSeconds = 0

            while (isBuffering) {
                delay(1000)
                val currentBufferedPos = exoPlayer.bufferedPosition
                val currentBytes = NetworkClient.totalBytes
                val timeSinceLastNetworkActivity = System.currentTimeMillis() - NetworkClient.lastStreamActivityTimeMs

                // Check if video buffer is advancing OR network is actively downloading data
                val isMakingProgress = (currentBufferedPos > lastBufferedPos) ||
                    (currentBytes > lastBytesTransferred) ||
                    (timeSinceLastNetworkActivity < 4000L)

                if (isMakingProgress) {
                    if (currentBufferedPos > lastBufferedPos) {
                        lastBufferedPos = currentBufferedPos
                    }
                    if (currentBytes > lastBytesTransferred) {
                        lastBytesTransferred = currentBytes
                    }
                    stallSeconds = 0
                } else {
                    stallSeconds++
                }

                // Update UI phase indicator based on stall duration
                bufferingPhase = when {
                    stallSeconds > 20 -> 2
                    stallSeconds > 8 -> 1
                    else -> 0
                }

                // If resume seek is stalled for 25s with zero active requests, attempt seek recovery to 0ms
                if (hasAppliedResumeForCurrentMedia && !hasAttemptedSeekRecovery && stallSeconds >= 25 && NetworkClient.activeRequests == 0) {
                    android.util.Log.w("PlayerScreen", "Buffering stalled at resume position ($savedPlaybackPositionMs ms). Attempting seek to 0ms recovery...")
                    hasAttemptedSeekRecovery = true
                    try {
                        exoPlayer.seekTo(0L)
                        exoPlayer.play()
                    } catch (_: Exception) {}
                    stallSeconds = 0
                    lastBufferedPos = 0L
                }

                // Max allowed stall with ZERO bytes advancing and NO active network progress:
                // Cold origin servers can take up to 52s for cold cache spinup.
                // OkHttp readTimeout is 60s. Allow up to 65s on primary, 55s on backups before forcing failover.
                val isPrimaryServer = state.activeServer.contains("Server 1", ignoreCase = true)
                val maxStallAllowed = if (isPrimaryServer) 65 else 55

                if (stallSeconds >= maxStallAllowed && timeSinceLastNetworkActivity >= 50_000L) {
                    android.util.Log.w("PlayerScreen", "Buffering watchdog triggered after ${stallSeconds}s with no network activity: auto-switching server")
                    try {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                    } catch (_: Exception) {}
                    viewModel.handlePlaybackError("Buffering timeout: server stream stalled", streamUrlToReport)
                    break
                }
            }
        } else {
            bufferingPhase = 0
        }
    }

    // Clear transient playback errors whenever loading new server or source
    LaunchedEffect(state.activeServer, state.playbackSource, state.isLoading) {
        if (state.isLoading || state.playbackSource != null) {
            playbackError = null
        }
    }

    // Bind source when loaded or retry requested
    LaunchedEffect(state.playbackSource, retryKey) {
        state.playbackSource?.let { source ->
            playbackError = null
            val rawUrl = source.url.replace("&amp;", "&").replace("&#38;", "&").trim().replace(" ", "%20")
            if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
                android.util.Log.e("PlayerScreen", "Rejecting non-http stream URL: $rawUrl")
                viewModel.handlePlaybackError("Stream URL is invalid ($rawUrl)")
                return@LaunchedEffect
            }
            if (rawUrl.contains("index.php", ignoreCase = true) || rawUrl.contains("login.php", ignoreCase = true)) {
                android.util.Log.e("PlayerScreen", "Rejecting webpage stream URL: $rawUrl")
                viewModel.handlePlaybackError("Stream returned a web page instead of video")
                return@LaunchedEffect
            }

            val cleanHeaders = source.headers.filterKeys {
                !it.equals("Origin", ignoreCase = true) &&
                !it.equals("X-Title", ignoreCase = true)
            }
            if (cleanHeaders.isNotEmpty()) {
                okHttpDataSourceFactory.setDefaultRequestProperties(cleanHeaders)
            }
            val cleanUrl = rawUrl
            val mimeType = when {
                cleanUrl.contains(".m3u8") || source.type.contains("mpegurl", ignoreCase = true) -> "application/x-mpegURL"
                cleanUrl.contains(".mpd") || source.type.contains("dash", ignoreCase = true) -> "application/dash+xml"
                else -> "video/mp4"
            }
            android.util.Log.d("PlayerScreen", "Preparing playback: URL=$cleanUrl MIME=$mimeType Server=${state.activeServer}")
            val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentUri != cleanUrl || exoPlayer.playbackState == Player.STATE_IDLE) {
                val resumePosition = savedPlaybackPositionMs.takeIf { it > 1000L }
                    ?: state.initialResumePositionMs.takeIf { it > 1000L }
                    ?: exoPlayer.currentPosition.takeIf { it > 1000L }
                val mediaItem = ExoMediaItem.Builder()
                    .setUri(Uri.parse(cleanUrl))
                    .setMimeType(mimeType)
                    .build()
                if (resumePosition != null && resumePosition > 1000L) {
                    android.util.Log.d("PlayerScreen", "Scheduling resume playback at saved position: ${resumePosition}ms once ready")
                    savedPlaybackPositionMs = resumePosition
                }
                hasAppliedResumeForCurrentMedia = false
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    // Periodic position update & sync to Room database
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(1000)
            if (exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_READY) {
                currentPosition = exoPlayer.currentPosition
                bufferedPosition = exoPlayer.bufferedPosition
                if (currentPosition > 1000L) {
                    savedPlaybackPositionMs = currentPosition
                }
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                viewModel.saveProgress(currentPosition, totalDuration)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            val posToSave = exoPlayer.currentPosition.takeIf { it > 1000L } ?: savedPlaybackPositionMs
            val durToSave = exoPlayer.duration.takeIf { it > 0L } ?: totalDuration
            viewModel.saveProgress(posToSave, durToSave)
            exoPlayer.clearVideoSurface()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // Handle TV Remote D-pad key events
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures {
                    showControls = !showControls
                    if (showControls) controlsTimeoutKey++
                }
            }
            .onKeyEvent { event ->
                val hasError = (!state.isLoading && !state.isAutoSwitching) && (state.errorMessage != null || playbackError != null)
                if (hasError) {
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        onBack()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (showQualityDialog) {
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        showQualityDialog = false
                        controlsTimeoutKey++
                        try { qualityFocusRequester.requestFocus() } catch (_: Exception) {}
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            if (showControls) {
                                showControls = false
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            if (exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED) {
                                exoPlayer.pause()
                                playWhenReadyState = false
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                playWhenReadyState = true
                                isPlaying = true
                            }
                            showControls = true
                            controlsTimeoutKey++
                            true
                        }
                        Key.MediaPlay -> {
                            exoPlayer.play()
                            playWhenReadyState = true
                            isPlaying = true
                            showControls = true
                            controlsTimeoutKey++
                            true
                        }
                        Key.MediaPause -> {
                            exoPlayer.pause()
                            playWhenReadyState = false
                            isPlaying = false
                            showControls = true
                            controlsTimeoutKey++
                            true
                        }
                        Key.MediaFastForward -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 30000).coerceAtMost(exoPlayer.duration))
                            showControls = true
                            controlsTimeoutKey++
                            true
                        }
                        Key.MediaRewind -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 30000).coerceAtLeast(0L))
                            showControls = true
                            controlsTimeoutKey++
                            true
                        }
                        Key.MediaNext -> {
                            if (state.hasNextEpisode) {
                                viewModel.playNextEpisode()
                            } else {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 60000).coerceAtMost(exoPlayer.duration))
                            }
                            showControls = true
                            controlsTimeoutKey++
                            true
                        }
                        Key.MediaPrevious -> {
                            if (state.hasPrevEpisode) {
                                viewModel.playPreviousEpisode()
                            } else {
                                exoPlayer.seekTo(0L)
                            }
                            showControls = true
                            controlsTimeoutKey++
                            true
                        }
                        Key.DirectionCenter, Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight, Key.Enter, Key.NumPadEnter -> {
                            if (!showControls) {
                                showControls = true
                                controlsTimeoutKey++
                                true
                            } else {
                                controlsTimeoutKey++
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Player Surface View (TextureView surface type)
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, FrameLayout(ctx), false) as PlayerView).apply {
                    player = exoPlayer
                    useController = false // Use custom TV D-pad controller overlay
                    keepScreenOn = true
                    setOnClickListener {
                        showControls = !showControls
                        if (showControls) controlsTimeoutKey++
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.setOnClickListener {
                    showControls = !showControls
                    if (showControls) controlsTimeoutKey++
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator:
        // 1. Initial stream loading (before playback starts)
        val isExoBuffering = isBuffering || (exoPlayer.playbackState == Player.STATE_BUFFERING)
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MunowatchGreen,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    val notice = state.statusMessage ?: "Connecting to high-speed media server (${state.activeServer.substringBefore(" ")})..."
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = notice,
                        color = MunowatchTextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (isExoBuffering) {
            // 2. Both initial stream buffer preparation and midplay buffering: ALWAYS show a centered spinner
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MunowatchGreen,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        // Stream Error Display with Server Switcher
        val displayError = if (!state.isLoading && !state.isAutoSwitching) (state.errorMessage ?: playbackError) else null
        if (displayError != null) {
            TvErrorView(
                message = displayError,
                onRetry = {
                    playbackError = null
                    retryKey++
                    viewModel.retryFromBeginning()
                },
                onBack = onBack,
                onSignIn = null
            )
        }

        // Custom TV HUD & Controls Overlay
        AnimatedVisibility(
            visible = showControls && displayError == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(36.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        TvButton(
                            text = "Back",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onBack,
                            isPrimary = false,
                            modifier = Modifier
                                .focusRequester(backFocusRequester)
                                .focusProperties {
                                    right = qualityFocusRequester
                                    down = timelineFocusRequester
                                }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val qualityBadgeText = if (selectedOption.isAuto) {
                            if (currentRenderedResolution != null) "Quality: Auto (${currentRenderedResolution})" else "Quality: Auto"
                        } else {
                            "Quality: ${selectedOption.resolutionLabel}"
                        }

                        TvButton(
                            text = qualityBadgeText,
                            icon = Icons.Default.HighQuality,
                            onClick = {
                                showQualityDialog = true
                                controlsTimeoutKey++
                            },
                            isPrimary = false,
                            modifier = Modifier
                                .focusRequester(qualityFocusRequester)
                                .focusProperties {
                                    left = backFocusRequester
                                    down = timelineFocusRequester
                                }
                        )

                        // Auto Server Indicator Badge
                        Surface(
                            color = MunowatchSurfaceElevated.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MunowatchGreen.copy(alpha = 0.45f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MunowatchGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto Server • ${state.activeServer.substringBefore(" ")}",
                                    color = MunowatchTextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Bottom Controls Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    // Interactive Timeline Slider & Scrubber
                    InteractivePlayerTimeline(
                        currentPositionMs = currentPosition,
                        totalDurationMs = totalDuration,
                        bufferedPositionMs = bufferedPosition,
                        onSeek = { targetMs ->
                            exoPlayer.seekTo(targetMs)
                            currentPosition = targetMs
                            savedPlaybackPositionMs = targetMs
                            controlsTimeoutKey++
                        },
                        modifier = Modifier
                            .focusRequester(timelineFocusRequester)
                            .focusProperties {
                                up = qualityFocusRequester
                                down = playPauseFocusRequester
                            }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${formatTime(currentPosition)} / ${formatTime(totalDuration)}",
                                color = MunowatchTextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Playback Action Controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.onKeyEvent {
                                controlsTimeoutKey++
                                false
                            }
                        ) {
                            TvButton(
                                text = if (state.hasPrevEpisode) "Prev Ep" else "Start",
                                icon = Icons.Default.SkipPrevious,
                                onClick = {
                                    if (state.hasPrevEpisode) {
                                        viewModel.playPreviousEpisode()
                                    } else {
                                        exoPlayer.seekTo(0L)
                                        currentPosition = 0L
                                    }
                                    controlsTimeoutKey++
                                },
                                isPrimary = false,
                                modifier = Modifier
                                    .focusRequester(startFocusRequester)
                                    .focusProperties {
                                        right = prev10FocusRequester
                                        up = timelineFocusRequester
                                    }
                            )

                            TvButton(
                                text = "-10s",
                                icon = Icons.Default.Replay10,
                                onClick = {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                                    controlsTimeoutKey++
                                },
                                isPrimary = false,
                                modifier = Modifier
                                    .focusRequester(prev10FocusRequester)
                                    .focusProperties {
                                        left = startFocusRequester
                                        right = playPauseFocusRequester
                                        up = timelineFocusRequester
                                    }
                            )

                            val isCurrentlyActive = playWhenReadyState && exoPlayer.playbackState != Player.STATE_ENDED
                            TvButton(
                                text = if (isCurrentlyActive) "Pause" else "Play",
                                icon = if (isCurrentlyActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                onClick = {
                                    android.util.Log.d("PlayerScreen", "PlayPause clicked: isCurrentlyActive=$isCurrentlyActive, playWhenReady=${exoPlayer.playWhenReady}, state=${exoPlayer.playbackState}")
                                    if (isCurrentlyActive) {
                                        exoPlayer.pause()
                                        playWhenReadyState = false
                                        isPlaying = false
                                    } else {
                                        exoPlayer.play()
                                        playWhenReadyState = true
                                        isPlaying = true
                                    }
                                    controlsTimeoutKey++
                                },
                                isPrimary = true,
                                modifier = Modifier
                                    .focusRequester(playPauseFocusRequester)
                                    .focusProperties {
                                        left = prev10FocusRequester
                                        right = next10FocusRequester
                                        up = timelineFocusRequester
                                    }
                            )

                            TvButton(
                                text = "+10s",
                                icon = Icons.Default.Forward10,
                                onClick = {
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                    controlsTimeoutKey++
                                },
                                isPrimary = false,
                                modifier = Modifier
                                    .focusRequester(next10FocusRequester)
                                    .focusProperties {
                                        left = playPauseFocusRequester
                                        right = next30FocusRequester
                                        up = timelineFocusRequester
                                    }
                            )

                            TvButton(
                                text = "+30s",
                                icon = Icons.Default.FastForward,
                                onClick = {
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 30000).coerceAtMost(exoPlayer.duration))
                                    controlsTimeoutKey++
                                },
                                isPrimary = false,
                                modifier = Modifier
                                    .focusRequester(next30FocusRequester)
                                    .focusProperties {
                                        left = next10FocusRequester
                                        right = nextEpFocusRequester
                                        up = timelineFocusRequester
                                    }
                            )

                            TvButton(
                                text = if (state.hasNextEpisode) "Next Ep" else "+1m",
                                icon = Icons.Default.SkipNext,
                                onClick = {
                                    if (state.hasNextEpisode) {
                                        viewModel.playNextEpisode()
                                    } else {
                                        exoPlayer.seekTo((exoPlayer.currentPosition + 60000).coerceAtMost(exoPlayer.duration))
                                    }
                                    controlsTimeoutKey++
                                },
                                isPrimary = false,
                                modifier = Modifier
                                    .focusRequester(nextEpFocusRequester)
                                    .focusProperties {
                                        left = next30FocusRequester
                                        up = timelineFocusRequester
                                    }
                            )
                        }

                        val bottomQualityText = if (selectedOption.isAuto) {
                            if (currentRenderedResolution != null) "Quality: Auto (${currentRenderedResolution})" else "Quality: Auto (Best)"
                        } else {
                            "Quality: ${selectedOption.resolutionLabel}"
                        }

                        Text(
                            text = "$bottomQualityText • Auto CDN: ${state.activeServer}",
                            color = MunowatchGreenLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // TV Quality Selector Modal Dialog
        if (showQualityDialog) {
            LaunchedEffect(Unit) {
                delay(50)
                try {
                    qualityDialogFirstItemFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showQualityDialog = false
                            try { qualityFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MunowatchSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MunowatchGreen.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .widthIn(min = 440.dp, max = 620.dp)
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.HighQuality,
                                contentDescription = null,
                                tint = MunowatchGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Stream Quality Selector",
                                style = MaterialTheme.typography.titleLarge,
                                color = MunowatchTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val dialogSubtext = if (availableQualities.size <= 2) {
                            "Available stream quality detected for this title:"
                        } else {
                            "Select preferred resolution for stable playback on your connection:"
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = dialogSubtext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MunowatchTextSecondary
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        availableQualities.forEachIndexed { index, quality ->
                            val isSelected = (selectedOption.id == quality.id)
                            val isFirst = (index == 0)

                            QualityOptionRow(
                                quality = quality,
                                isSelected = isSelected,
                                modifier = if (isFirst) {
                                    Modifier.focusRequester(qualityDialogFirstItemFocusRequester)
                                } else Modifier,
                                onClick = {
                                    applyQualitySelection(quality)
                                    showQualityDialog = false
                                    controlsTimeoutKey++
                                    try { qualityFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )
                            if (index < availableQualities.size - 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TvButton(
                                text = "Close",
                                icon = null,
                                onClick = {
                                    showQualityDialog = false
                                    try { qualityFocusRequester.requestFocus() } catch (_: Exception) {}
                                },
                                isPrimary = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun QualityOptionRow(
    quality: StreamQualityOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = when {
            isFocused -> MunowatchGreen.copy(alpha = 0.25f)
            isSelected -> MunowatchGreen.copy(alpha = 0.12f)
            else -> MunowatchSurfaceElevated.copy(alpha = 0.9f)
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 0.5.dp,
            color = when {
                isFocused -> MunowatchFocusBorder
                isSelected -> MunowatchGreen
                else -> Color.White.copy(alpha = 0.15f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter ||
                     keyEvent.key == Key.Enter ||
                     keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quality.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isFocused || isSelected) MunowatchTextPrimary else MunowatchTextSecondary,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MunowatchGreen.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = MunowatchGreenLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = quality.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) MunowatchTextSecondary else MunowatchTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MunowatchGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

