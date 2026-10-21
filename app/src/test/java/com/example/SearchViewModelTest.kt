package com.example

import com.example.data.local.SearchHistoryDao
import com.example.data.local.SearchHistoryEntity
import com.example.domain.model.MediaItem
import com.example.domain.repository.SearchRepository
import com.example.presentation.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeSearchRepository : SearchRepository {
        val sampleResults = listOf(
            MediaItem(
                id = "series_squid_game",
                title = "Squid Game",
                posterUrl = "https://example.com/poster.jpg",
                isSeries = true
            )
        )

        override suspend fun searchContent(query: String): Result<List<MediaItem>> {
            return Result.success(sampleResults.filter { it.title.contains(query, ignoreCase = true) })
        }
    }

    private class FakeSearchHistoryDao : SearchHistoryDao {
        private val listFlow = MutableStateFlow<List<String>>(listOf("Squid Game", "VJ Junior"))

        override fun getRecentSearches(): Flow<List<String>> = listFlow

        override suspend fun insertSearch(item: SearchHistoryEntity) {
            val cur = listFlow.value.toMutableList()
            cur.remove(item.query)
            cur.add(0, item.query)
            listFlow.value = cur
        }

        override suspend fun deleteSearch(query: String) {
            val cur = listFlow.value.toMutableList()
            cur.remove(query)
            listFlow.value = cur
        }

        override suspend fun clearHistory() {
            listFlow.value = emptyList()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun searchViewModel_loadsRecentSearchesAndExecutesSearch() = runTest(testDispatcher) {
        val fakeRepo = FakeSearchRepository()
        val fakeDao = FakeSearchHistoryDao()
        val viewModel = SearchViewModel(
            searchRepository = fakeRepo,
            searchHistoryDao = fakeDao
        )

        testScheduler.advanceUntilIdle()

        // Verify recent searches loaded from DAO
        assertEquals(listOf("Squid Game", "VJ Junior"), viewModel.uiState.value.recentSearches)

        // Select a recent search
        viewModel.selectRecentSearch("Squid Game")
        testScheduler.advanceUntilIdle()

        assertEquals("Squid Game", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.hasSearched)
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("series_squid_game", viewModel.uiState.value.results[0].id)

        // Delete one recent search
        viewModel.deleteRecentSearch("VJ Junior")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Squid Game"), viewModel.uiState.value.recentSearches)

        // Clear all recent searches
        viewModel.clearRecentSearches()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.recentSearches.isEmpty())
    }
}
