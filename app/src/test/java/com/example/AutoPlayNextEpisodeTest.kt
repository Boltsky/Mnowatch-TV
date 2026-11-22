package com.example

import com.example.data.repository.MediaCache
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.Episode
import com.example.domain.model.PlaybackSource
import com.example.domain.model.Season
import com.example.domain.model.SeriesDetail
import com.example.domain.repository.PlaybackRepository
import com.example.presentation.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoPlayNextEpisodeTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakePlaybackRepository = object : PlaybackRepository {
        override suspend fun getPlaybackSource(contentId: String, serverName: String?): Result<PlaybackSource> {
            return Result.success(
                PlaybackSource(
                    url = "https://example.com/video_$contentId.mp4",
                    quality = "1080p",
                    type = "video/mp4"
                )
            )
        }

        override suspend fun saveWatchProgress(item: ContinueWatchingItem) {}
        override suspend fun getContinueWatching(): Flow<List<ContinueWatchingItem>> = emptyFlow()
        override suspend fun removeWatchProgress(contentId: String) {}
        override suspend fun evictPlaybackSource(contentId: String) {}
        override suspend fun getSavedPosition(contentId: String): Long = 0L
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MediaCache.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MediaCache.clear()
    }

    @Test
    fun testFindAdjacentEpisodes_correctlyResolvesEpisodes() {
        val series = SeriesDetail(
            id = "series_terminal_list",
            title = "Terminal List",
            description = "Action thriller series",
            posterUrl = "https://example.com/poster.jpg",
            backdropUrl = "https://example.com/backdrop.jpg",
            vj = "VJ Junior",
            year = "2026",
            genres = listOf("Action", "Series"),
            seasons = listOf(
                Season(
                    number = 1,
                    title = "Season 1",
                    episodes = listOf(
                        Episode(
                            id = "terminal_list_s1_ep1_101",
                            number = 1,
                            title = "Episode 1: The Engram",
                            thumbnail = "https://example.com/ep1.jpg",
                            streamUrl = "/twolekede?mid=101"
                        ),
                        Episode(
                            id = "terminal_list_s1_ep2_102",
                            number = 2,
                            title = "Episode 2: Encoding",
                            thumbnail = "https://example.com/ep2.jpg",
                            streamUrl = "/twolekede?mid=102"
                        ),
                        Episode(
                            id = "terminal_list_s1_ep3_103",
                            number = 3,
                            title = "Episode 3: Consolidation",
                            thumbnail = "https://example.com/ep3.jpg",
                            streamUrl = "/twolekede?mid=103"
                        )
                    )
                )
            )
        )

        MediaCache.putSeriesDetail(series)

        // Test Episode 1
        val (prev1, next1) = MediaCache.findAdjacentEpisodes("/twolekede?mid=101")
        assertNull(prev1)
        assertNotNull(next1)
        assertEquals("terminal_list_s1_ep2_102", next1?.id)
        assertEquals("Episode 2: Encoding", next1?.title)

        // Test Episode 2
        val (prev2, next2) = MediaCache.findAdjacentEpisodes("terminal_list_s1_ep2_102")
        assertNotNull(prev2)
        assertNotNull(next2)
        assertEquals("terminal_list_s1_ep1_101", prev2?.id)
        assertEquals("terminal_list_s1_ep3_103", next2?.id)

        // Test Episode 3 (final episode)
        val (prev3, next3) = MediaCache.findAdjacentEpisodes("/twolekede?mid=103")
        assertNotNull(prev3)
        assertNull(next3)
        assertEquals("terminal_list_s1_ep2_102", prev3?.id)
    }

    @Test
    fun testPlayerViewModel_autoPlaysNextEpisode() {
        val series = SeriesDetail(
            id = "series_terminal_list",
            title = "Terminal List",
            description = "Action thriller series",
            posterUrl = "https://example.com/poster.jpg",
            backdropUrl = "https://example.com/backdrop.jpg",
            vj = "VJ Junior",
            year = "2026",
            genres = listOf("Action", "Series"),
            seasons = listOf(
                Season(
                    number = 1,
                    title = "Season 1",
                    episodes = listOf(
                        Episode(
                            id = "ep_1",
                            number = 1,
                            title = "Episode 1",
                            thumbnail = "https://example.com/ep1.jpg",
                            streamUrl = "/twolekede?mid=101"
                        ),
                        Episode(
                            id = "ep_2",
                            number = 2,
                            title = "Episode 2",
                            thumbnail = "https://example.com/ep2.jpg",
                            streamUrl = "/twolekede?mid=102"
                        )
                    )
                )
            )
        )

        MediaCache.putSeriesDetail(series)

        val viewModel = PlayerViewModel(
            initialContentId = "/twolekede?mid=101",
            initialServer = null,
            playbackRepository = fakePlaybackRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state1 = viewModel.uiState.value
        assertTrue(state1.hasNextEpisode)
        assertFalse(state1.hasPrevEpisode)
        assertEquals("/twolekede?mid=102", state1.nextEpisodeId)
        assertEquals("Episode 2", state1.nextEpisodeTitle)

        // Simulate auto-play trigger
        viewModel.playNextEpisode()
        testDispatcher.scheduler.advanceUntilIdle()

        val state2 = viewModel.uiState.value
        assertEquals("/twolekede?mid=102", state2.contentId)
        assertFalse(state2.hasNextEpisode)
        assertTrue(state2.hasPrevEpisode)
        assertNull(state2.nextEpisodeId)
        assertEquals("/twolekede?mid=101", state2.prevEpisodeId)
    }
}
