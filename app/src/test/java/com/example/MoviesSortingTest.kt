package com.example

import com.example.data.repository.MediaCache
import com.example.domain.model.MediaItem
import com.example.domain.repository.MovieRepository
import com.example.domain.repository.SearchRepository
import com.example.presentation.movies.MovieSortMode
import com.example.presentation.movies.MoviesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
class MoviesSortingTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleMovies = listOf(
        MediaItem(
            id = "1",
            title = "Movie Alpha",
            year = "2026",
            posterUrl = "https://twolekede.com/mv-admin/uploads/posters/poster_1790814573702.jpg",
            vj = "VJ Junior"
        ),
        MediaItem(
            id = "2",
            title = "Beta Hero (2025)",
            year = "",
            posterUrl = "https://twolekede.com/uploads/posters/1748194955_die.jpg",
            vj = "VJ Emmy"
        ),
        MediaItem(
            id = "3",
            title = "Gamma Force",
            year = "2024",
            posterUrl = "https://twolekede.com/uploads/posters/1704067200_gamma.jpg",
            vj = "VJ Junior"
        ),
        MediaItem(
            id = "4",
            title = "Delta Quest",
            year = "2024",
            description = "A 2024 thriller",
            posterUrl = "https://twolekede.com/uploads/posters/1704153600_delta.jpg",
            vj = "VJ ICEP"
        ),
        MediaItem(
            id = "5",
            title = "Echo Mission",
            year = "2023",
            vj = "VJ Soul"
        ),
        MediaItem(
            id = "6",
            title = "Zeta Chronicles",
            year = "2022",
            streamUrl = "https://dl.movviez.com/?year=2022",
            vj = "VJ Kevo"
        ),
        MediaItem(
            id = "7",
            title = "Medieval",
            year = "2022",
            posterUrl = "https://twolekede.com/uploads/posters/1654000000_medieval.jpg",
            vj = "VJ Mark"
        )
    )

    private val fakeMovieRepository = object : MovieRepository {
        override suspend fun getFeaturedHero(): Result<MediaItem?> = Result.success(sampleMovies.first())
        override suspend fun getLatestMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> = Result.success(sampleMovies)
        override suspend fun getFreeMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> = Result.success(sampleMovies)
        override suspend fun getMoviesByGenre(genreId: Int, lastFetchId: String?, pipe: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
        override suspend fun getMoviesByVj(vjId: Int, lastFetchId: String?, pipe: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
        override suspend fun getMovieDetails(id: String): Result<MediaItem> = Result.success(sampleMovies.first { it.id == id })
    }

    private val fakeSearchRepository = object : SearchRepository {
        override suspend fun searchContent(query: String): Result<List<MediaItem>> {
            val matching = sampleMovies.filter {
                MoviesViewModel.matchesYear(it, query) ||
                MoviesViewModel.matchesVj(it, query) ||
                it.title.contains(query, ignoreCase = true) ||
                it.year.contains(query)
            }
            return Result.success(matching)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MediaCache.clear()
        MoviesViewModel.clearCaches()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MediaCache.clear()
        MoviesViewModel.clearCaches()
    }

    @Test
    fun testExtractUploadTimestampAndYear() {
        val ts1 = MoviesViewModel.extractUploadTimestamp(sampleMovies[0])
        assertNotNull(ts1)
        assertEquals(1790814573702L, ts1)
        assertEquals("2026", MoviesViewModel.timestampToYear(ts1!!))

        val ts2 = MoviesViewModel.extractUploadTimestamp(sampleMovies[1])
        assertNotNull(ts2)
        assertEquals(1748194955000L, ts2)
        assertEquals("2025", MoviesViewModel.timestampToYear(ts2!!))

        val ts3 = MoviesViewModel.extractUploadTimestamp(sampleMovies[2])
        assertNotNull(ts3)
        assertEquals(1704067200000L, ts3)
        assertEquals("2024", MoviesViewModel.timestampToYear(ts3!!))
    }

    @Test
    fun testExtractYearAccuracy() {
        assertEquals("2026", MoviesViewModel.extractYear(sampleMovies[0]))
        assertEquals("2025", MoviesViewModel.extractYear(sampleMovies[1])) // From poster timestamp / title
        assertEquals("2024", MoviesViewModel.extractYear(sampleMovies[2]))
        assertEquals("2024", MoviesViewModel.extractYear(sampleMovies[3]))
        assertEquals("2023", MoviesViewModel.extractYear(sampleMovies[4]))
        assertEquals("2022", MoviesViewModel.extractYear(sampleMovies[5])) // From streamUrl
    }

    @Test
    fun testMatchesYearLogic() {
        assertTrue(MoviesViewModel.matchesYear(sampleMovies[0], "2026"))
        assertFalse(MoviesViewModel.matchesYear(sampleMovies[0], "2025"))
        assertTrue(MoviesViewModel.matchesYear(sampleMovies[1], "2025"))
        assertTrue(MoviesViewModel.matchesYear(sampleMovies[2], "2024"))
        assertTrue(MoviesViewModel.matchesYear(sampleMovies[3], "2024"))
    }

    @Test
    fun testMatchesVjLogic() {
        assertTrue(MoviesViewModel.matchesVj(sampleMovies[0], "VJ Junior"))
        assertTrue(MoviesViewModel.matchesVj(sampleMovies[0], "junior"))
        assertTrue(MoviesViewModel.matchesVj(sampleMovies[0], "All VJs"))
        assertTrue(MoviesViewModel.matchesVj(sampleMovies[0], null))
        assertFalse(MoviesViewModel.matchesVj(sampleMovies[0], "VJ ICEP"))
        assertTrue(MoviesViewModel.matchesVj(sampleMovies[3], "VJ ICEP"))
        assertTrue(MoviesViewModel.matchesVj(sampleMovies[3], "icep"))
    }

    @Test
    fun testComputeAvailableYearsOrder() {
        val years = MoviesViewModel.computeAvailableYears(sampleMovies)
        assertTrue(years.isNotEmpty())
        assertEquals("2026", years[0])
        assertEquals("2025", years[1])
        assertEquals("2024", years[2])
        assertEquals("2023", years[3])
        assertEquals("2022", years[4])

        // Verify all years are strictly descending integers (no scrambled years like 2022 jumping before 2025)
        val yearInts = years.mapNotNull { it.toIntOrNull() }
        assertEquals(years.size, yearInts.size)
        assertTrue(yearInts.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun testSelectYear2022ShowsMovies() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectYear("2022")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MovieSortMode.BY_YEAR, state.sortMode)
        assertEquals("2022", state.selectedYear)
        assertTrue(state.movies.isNotEmpty())
        // Both Zeta Chronicles (year=2022) and Medieval (tagged as 2022) must be present
        assertTrue(state.movies.all { MoviesViewModel.matchesYear(it, "2022") })
        val ids = state.movies.map { it.id }.toSet()
        assertTrue(ids.contains("6"))
        assertTrue(ids.contains("7"))
    }

    @Test
    fun testDefaultLatestSortMode() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MovieSortMode.LATEST, state.sortMode)
        assertNull(state.selectedYear)
        assertNull(state.selectedVj)
        assertEquals(sampleMovies.size, state.movies.size)
        assertEquals("1", state.movies[0].id)
        assertEquals("2", state.movies[1].id)
    }

    @Test
    fun testRandomSortShufflesMovies() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.sortRandomly()
        val state = vm.uiState.value
        assertEquals(MovieSortMode.RANDOM, state.sortMode)
        assertEquals(sampleMovies.size, state.movies.size)
        assertEquals(sampleMovies.map { it.id }.toSet(), state.movies.map { it.id }.toSet())
    }

    @Test
    fun testReshuffleSpecificVj() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.sortRandomly()
        vm.selectVj("VJ Junior")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MovieSortMode.RANDOM, state.sortMode)
        assertEquals("VJ Junior", state.selectedVj)
        // Movies 1 and 3 are VJ Junior
        assertEquals(2, state.movies.size)
        assertTrue(state.movies.all { MoviesViewModel.matchesVj(it, "VJ Junior") })
        assertEquals(setOf("1", "3"), state.movies.map { it.id }.toSet())
    }

    @Test
    fun testByYearSortModeOrdersMoviesDescending() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleYearSelector()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MovieSortMode.BY_YEAR, state.sortMode)
        assertNull(state.selectedYear)
        assertTrue(state.movies.isNotEmpty())

        val movieYears = state.movies.mapNotNull { MoviesViewModel.extractYear(it)?.toIntOrNull() }
        assertEquals(state.movies.size, movieYears.size)
        // Verify all movies are in descending order by year
        assertTrue(movieYears.zipWithNext().all { (a, b) -> a >= b })
        assertEquals(2026, movieYears.first())
        assertEquals(2022, movieYears.last())
    }

    @Test
    fun testSelectSpecificYearFiltersMovies() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectYear("2024")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MovieSortMode.BY_YEAR, state.sortMode)
        assertEquals("2024", state.selectedYear)
        assertTrue(state.isYearSelectorOpen)

        // Only 2024 movies should be shown, sorted by upload time
        assertEquals(2, state.movies.size)
        assertTrue(state.movies.all { MoviesViewModel.matchesYear(it, "2024") })
        assertEquals(setOf("3", "4"), state.movies.map { it.id }.toSet())
        // Delta Quest (1704153600) was uploaded after Gamma Force (1704067200)
        assertEquals("4", state.movies[0].id)
        assertEquals("3", state.movies[1].id)
    }

    @Test
    fun testSelectYearAndVjCombinedFilter() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectYear("2024")
        vm.selectVj("VJ ICEP")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.movies.size)
        assertEquals("4", state.movies[0].id) // Delta Quest by VJ ICEP
    }

    @Test
    fun testClearYearFilterRestoresAllMovies() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectYear("2024")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.movies.size)

        vm.clearYearFilter()
        val state = vm.uiState.value
        assertNull(state.selectedYear)
        assertEquals(sampleMovies.size, state.movies.size)
    }

    @Test
    fun testSetSortLatestRestoresDefault() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectYear("2023")
        vm.selectVj("VJ Soul")
        vm.sortRandomly()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setSortLatest()
        val state = vm.uiState.value
        assertEquals(MovieSortMode.LATEST, state.sortMode)
        assertNull(state.selectedYear)
        assertNull(state.selectedVj)
        assertFalse(state.isYearSelectorOpen)
        assertEquals(sampleMovies.size, state.movies.size)
        assertEquals("1", state.movies[0].id)
    }

    @Test
    fun testYearPaginationLoadsDiscoveryMovies() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectYear("2024")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MovieSortMode.BY_YEAR, state.sortMode)
        assertEquals("2024", state.selectedYear)
        assertTrue(state.movies.isNotEmpty())
        assertTrue(state.movies.all { MoviesViewModel.matchesYear(it, "2024") })

        // Test loadMore in year mode
        vm.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.movies.all { MoviesViewModel.matchesYear(it, "2024") })
    }

    @Test
    fun testReshuffleMultiYearLoadsAllYears() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.sortRandomly()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MovieSortMode.RANDOM, state.sortMode)
        // Verify movies from multiple distinct years exist in the reshuffled state
        val yearsInState = state.movies.mapNotNull { MoviesViewModel.extractYear(it) }.toSet()
        assertTrue(yearsInState.size >= 2)
        assertTrue(state.canLoadMore)

        // Test loadMore in reshuffle mode
        vm.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(MovieSortMode.RANDOM, vm.uiState.value.sortMode)
    }

    @Test
    fun testScrollPositionPreservedInViewModel() = runTest(testDispatcher) {
        val vm = MoviesViewModel(fakeMovieRepository, fakeSearchRepository, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        // User scrolls down to index 35, offset 120
        vm.scrollIndex = 35
        vm.scrollOffset = 120
        vm.lastOpenedMovieId = "3"

        // Emulate user returning back from details (no filter change)
        assertEquals(35, vm.scrollIndex)
        assertEquals(120, vm.scrollOffset)
        assertEquals("3", vm.lastOpenedMovieId)

        // Changing filter resets scroll position
        vm.selectYear("2024")
        assertEquals(0, vm.scrollIndex)
        assertEquals(0, vm.scrollOffset)

        // Scroll again
        vm.scrollIndex = 12
        vm.scrollOffset = 50
        vm.clearYearFilter()
        assertEquals(0, vm.scrollIndex)
        assertEquals(0, vm.scrollOffset)
    }

    @Test
    fun testSeriesVjExtractionFromHtml() {
        val parser = com.example.data.api.MunowatchHtmlParser()
        val seriesHtml = """
            <div class="grid-container">
                <div class="grid-item">
                    <a href="seriesdetails.php?title=The+Gentlemen&category=VJ+Ulio">
                        <div class="poster-container">
                            <img src="uploads/posters/gentlemen.jpg" alt="The Gentlemen">
                            <div class="item-info">
                                <h3>The Gentlemen</h3>
                                <span class="category">VJ Ulio</span>
                            </div>
                        </div>
                    </a>
                </div>
                <div class="grid-item">
                    <a href="seriesdetails.php?title=Lioness&category=VJ+ICEP">
                        <div class="poster-container">
                            <img src="uploads/posters/lioness.jpg" alt="Lioness">
                            <div class="item-info">
                                <h3>Lioness</h3>
                                <span class="category">VJ ICEP</span>
                            </div>
                        </div>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val parsed = parser.parseMediaCards(seriesHtml)
        assertEquals(2, parsed.size)
        assertTrue(parsed[0].isSeries)
        assertEquals("VJ Ulio", parsed[0].vj)
        assertTrue(parsed[1].isSeries)
        assertEquals("VJ ICEP", parsed[1].vj)
    }

    @Test
    fun testMatchesCategoryLogic() {
        val actionMovie = MediaItem(id = "a1", title = "Fast Force", genre = "Action", vj = "VJ Junior")
        val dramaMovie = MediaItem(id = "d1", title = "Tears of Life", genre = "Drama", vj = "VJ Junior")
        val romanceMovie = MediaItem(id = "r1", title = "Love In Paris", genre = "Romance", vj = "VJ Emmy")
        val kungFuMovie = MediaItem(id = "k1", title = "Master Kick", genre = "Kung Fu", vj = "VJ ICEP")
        val descActionMovie = MediaItem(id = "da1", title = "Agent 007", genre = "", description = "An explosive action thriller", vj = "VJ Ulio")

        assertTrue(MoviesViewModel.matchesCategory(actionMovie, "Action"))
        assertFalse(MoviesViewModel.matchesCategory(actionMovie, "Drama"))
        assertTrue(MoviesViewModel.matchesCategory(dramaMovie, "Drama"))
        assertFalse(MoviesViewModel.matchesCategory(dramaMovie, "Action"))

        // Aliases
        assertTrue(MoviesViewModel.matchesCategory(romanceMovie, "Love Story"))
        assertTrue(MoviesViewModel.matchesCategory(kungFuMovie, "Martial Arts"))

        // Fallback in description
        assertTrue(MoviesViewModel.matchesCategory(descActionMovie, "Action"))

        // Strict genre conflict isolation (Horror vs Comedy)
        val horrorMovie = MediaItem(id = "h1", title = "Terrifying Night", genre = "Horror", description = "A crazy clown with scary jokes", vj = "VJ Junior")
        val comedyMovie = MediaItem(id = "c1", title = "Super Funny Joke", genre = "Comedy", description = "Hilarious family comedy", vj = "VJ Junior")
        assertFalse("Horror movie must never match Comedy", MoviesViewModel.matchesCategory(horrorMovie, "Comedy"))
        assertTrue("Horror movie must match Horror", MoviesViewModel.matchesCategory(horrorMovie, "Horror"))
        assertFalse("Comedy movie must never match Horror", MoviesViewModel.matchesCategory(comedyMovie, "Horror"))
        assertTrue("Comedy movie must match Comedy", MoviesViewModel.matchesCategory(comedyMovie, "Comedy"))

        // All Categories or null
        assertTrue(MoviesViewModel.matchesCategory(actionMovie, "All Categories"))
        assertTrue(MoviesViewModel.matchesCategory(actionMovie, null))
    }

    @Test
    fun testReshuffleByCategoryAndVj() = runTest(testDispatcher) {
        val testItems = listOf(
            MediaItem(id = "1", title = "Action Junior", genre = "Action", vj = "VJ Junior"),
            MediaItem(id = "2", title = "Drama Junior", genre = "Drama", vj = "VJ Junior"),
            MediaItem(id = "3", title = "Action Emmy", genre = "Action", vj = "VJ Emmy"),
            MediaItem(id = "4", title = "Comedy Emmy", genre = "Comedy", vj = "VJ Emmy"),
            MediaItem(id = "5", title = "Action ICEP", genre = "Action", vj = "VJ ICEP")
        )

        MediaCache.setPopularMovies(testItems)
        val fakeRepo = object : MovieRepository {
            override suspend fun getFeaturedHero(): Result<MediaItem?> = Result.success(testItems.first())
            override suspend fun getLatestMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> = Result.success(testItems)
            override suspend fun getFreeMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> = Result.success(testItems)
            override suspend fun getMoviesByGenre(genreId: Int, lastFetchId: String?, pipe: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
            override suspend fun getMoviesByVj(vjId: Int, lastFetchId: String?, pipe: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
            override suspend fun getMovieDetails(id: String): Result<MediaItem> = Result.success(testItems.first { it.id == id })
        }

        val vm = MoviesViewModel(
            movieRepository = fakeRepo,
            searchRepository = null,
            ioDispatcher = testDispatcher,
            munowatchRepository = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Reshuffle by Category alone (Action)
        vm.sortRandomly()
        vm.selectCategory("Action")
        testDispatcher.scheduler.advanceUntilIdle()
        val actionOnly = vm.uiState.value.movies
        assertEquals(3, actionOnly.size)
        assertTrue(actionOnly.all { MoviesViewModel.matchesCategory(it, "Action") })

        // 2. Reshuffle by VJ + Category (VJ Junior + Action)
        vm.selectVj("VJ Junior")
        testDispatcher.scheduler.advanceUntilIdle()
        val juniorAction = vm.uiState.value.movies
        assertEquals(1, juniorAction.size)
        assertEquals("Action Junior", juniorAction[0].title)
        assertEquals("VJ Junior", juniorAction[0].vj)

        // 3. Switch Category to Drama while VJ Junior is still selected
        vm.selectCategory("Drama")
        testDispatcher.scheduler.advanceUntilIdle()
        val juniorDrama = vm.uiState.value.movies
        assertEquals(1, juniorDrama.size)
        assertEquals("Drama Junior", juniorDrama[0].title)

        // 4. Clear VJ -> all Drama movies
        vm.clearVjFilter()
        testDispatcher.scheduler.advanceUntilIdle()
        val allDrama = vm.uiState.value.movies
        assertEquals(1, allDrama.size)

        // 5. Clear Category -> all movies
        vm.clearCategoryFilter()
        testDispatcher.scheduler.advanceUntilIdle()
        val allMovies = vm.uiState.value.movies
        assertEquals(5, allMovies.size)
    }

    @Test
    fun testComputeAvailableCategories() {
        val items = listOf(
            MediaItem(id = "1", title = "A", genre = "Action, Thriller"),
            MediaItem(id = "2", title = "B", genre = "Drama / Korean")
        )
        val categories = MoviesViewModel.computeAvailableCategories(items)
        assertTrue(categories.contains("All Categories"))
        assertTrue(categories.contains("Thriller"))
        assertTrue(categories.contains("Korean"))
    }

    @Test
    fun testRogueDoesNotMatchComedy() {
        val rogue = MediaItem(id = "-996038", title = "Rogue", year = "2020", vj = "Vj Jingo", genre = "", description = "")
        val itCutsDeep = MediaItem(id = "5716", title = "it cuts deep", year = "2020", vj = "Vj Jingo", genre = "", description = "")
        assertFalse("Horror movie 'it cuts deep' must not match Comedy", MoviesViewModel.matchesCategory(itCutsDeep, "Comedy"))
        assertFalse("Action movie 'Rogue' must not match Comedy", MoviesViewModel.matchesCategory(rogue, "Comedy"))
    }

    @Test
    fun testCombatActionAndHorrorIsolation() {
        val sniper = MediaItem(id = "101", title = "Sniper: Assassin's End", year = "2020", vj = "VJ Emmy", genre = "Action", description = "Special forces operation against a cartel assassin")
        val commando = MediaItem(id = "102", title = "Commando Strike", year = "2020", vj = "VJ Junior", genre = "", description = "Mercenary team rescues hostages")
        val babadook = MediaItem(id = "103", title = "The Babadook", year = "2020", vj = "VJ Jingo", genre = "", description = "Haunted house and demon possession")
        val pureComedy = MediaItem(id = "104", title = "God of Gamblers", year = "2020", vj = "VJ Jingo", genre = "Comedy", description = "Hilarious funny casino comedy")

        assertFalse("Sniper must not match Comedy", MoviesViewModel.matchesCategory(sniper, "Comedy"))
        assertFalse("Commando must not match Comedy", MoviesViewModel.matchesCategory(commando, "Comedy"))
        assertFalse("Horror must not match Comedy", MoviesViewModel.matchesCategory(babadook, "Comedy"))
        assertTrue("Legitimate comedy must match Comedy", MoviesViewModel.matchesCategory(pureComedy, "Comedy"))

        assertFalse("Sniper must not match Romance", MoviesViewModel.matchesCategory(sniper, "Romance"))
        assertFalse("Horror must not match Family", MoviesViewModel.matchesCategory(babadook, "Family"))
    }

    @Test
    fun testFilteringByYearVjAndGenre() = runTest(testDispatcher) {
        val drama2022Junior1 = MediaItem(id = "d1", title = "A Jazzman's Blues", year = "2022", vj = "VJ Junior", genre = "Drama")
        val drama2022Junior2 = MediaItem(id = "d2", title = "Hunt", year = "2022", vj = "VJ Junior", genre = "Drama")
        val action2022Junior = MediaItem(id = "a1", title = "Medieval", year = "2022", vj = "VJ Junior", genre = "Action")
        val drama2023Emmy = MediaItem(id = "d3", title = "Oppenheimer", year = "2023", vj = "VJ Emmy", genre = "Drama")

        val customRepo = object : MovieRepository {
            override suspend fun getFeaturedHero(): Result<MediaItem?> = Result.success(null)
            override suspend fun getLatestMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
            override suspend fun getFreeMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
            override suspend fun getMoviesByGenre(genreId: Int, lastFetchId: String?, pipe: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
            override suspend fun getMoviesByVj(vjId: Int, lastFetchId: String?, pipe: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
            override suspend fun getMovieDetails(id: String): Result<MediaItem> = Result.success(drama2022Junior1)
            override suspend fun getFilteredMovies(year: String?, category: String?, vj: String?, page: Int, perPage: Int): Result<List<MediaItem>> {
                if (year == "2022" && category == "Drama" && vj == "VJ Junior") {
                    return if (page == 1) Result.success(listOf(drama2022Junior1, drama2022Junior2)) else Result.success(emptyList())
                }
                return Result.success(emptyList())
            }
        }

        val vm = MoviesViewModel(
            movieRepository = customRepo,
            searchRepository = fakeSearchRepository,
            munowatchRepository = null,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Apply filters: Year: 2022, Genre: Drama, VJ: VJ Junior
        vm.selectYear("2022")
        vm.selectCategory("Drama")
        vm.selectVj("VJ Junior")
        testDispatcher.scheduler.advanceUntilIdle()

        val filtered = vm.uiState.value.movies
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.title == "A Jazzman's Blues" })
        assertTrue(filtered.any { it.title == "Hunt" })
        assertTrue(filtered.all { it.year == "2022" })
        assertTrue(filtered.all { it.vj == "VJ Junior" })
        assertTrue(filtered.all { MoviesViewModel.matchesCategory(it, "Drama") })
    }

    @Test
    fun testIsOldMovieIdentifiesPre2021Movies() {
        // Old movies (<= 2020)
        val gladiator = MediaItem(id = "o1", title = "Gladiator", year = "2000", vj = "VJ Junior")
        val matrix = MediaItem(id = "o2", title = "The Matrix", year = "1999", vj = "VJ Emmy")
        val inception = MediaItem(id = "o3", title = "Inception", year = "2010", vj = "VJ Junior")
        val tenet = MediaItem(id = "o4", title = "Tenet", year = "2020", vj = "VJ Jingo")
        val classicByTitle = MediaItem(id = "o5", title = "Pulp Fiction Classic", year = "", vj = "VJ Emmy")

        assertTrue(MoviesViewModel.isOldMovie(gladiator))
        assertTrue(MoviesViewModel.isOldMovie(matrix))
        assertTrue(MoviesViewModel.isOldMovie(inception))
        assertTrue(MoviesViewModel.isOldMovie(tenet))
        assertTrue(MoviesViewModel.isOldMovie(classicByTitle))

        // Recent movies (> 2020)
        val deadpool = MediaItem(id = "r1", title = "Deadpool & Wolverine", year = "2024", vj = "VJ Junior")
        val avatar = MediaItem(id = "r2", title = "Avatar 3", year = "2025", vj = "VJ Emmy")
        val new2022 = MediaItem(id = "r3", title = "Batman (2022)", year = "2022", vj = "VJ Junior")

        assertFalse(MoviesViewModel.isOldMovie(deadpool))
        assertFalse(MoviesViewModel.isOldMovie(avatar))
        assertFalse(MoviesViewModel.isOldMovie(new2022))
    }

    @Test
    fun testToggleOldMoviesSwitchesModeAndFilters() = runTest(testDispatcher) {
        val testMovies = listOf(
            MediaItem(id = "m1", title = "New Movie 2025", year = "2025", vj = "VJ Junior", genre = "Action"),
            MediaItem(id = "m2", title = "New Movie 2024", year = "2024", vj = "VJ Emmy", genre = "Drama"),
            MediaItem(id = "m3", title = "Tenet", year = "2020", vj = "VJ Junior", genre = "Action"),
            MediaItem(id = "m4", title = "Gladiator", year = "2000", vj = "VJ Junior", genre = "Action"),
            MediaItem(id = "m5", title = "Matrix", year = "1999", vj = "VJ Emmy", genre = "Sci-Fi")
        )

        val customRepo = object : MovieRepository {
            override suspend fun getFeaturedHero(): Result<MediaItem?> = Result.success(null)
            override suspend fun getLatestMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> = Result.success(testMovies)
            override suspend fun getFreeMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> = Result.success(testMovies)
            override suspend fun getMoviesByGenre(genreId: Int, lastFetchId: String?, pipe: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
            override suspend fun getMoviesByVj(vjId: Int, lastFetchId: String?, pipe: String?, page: Int?): Result<List<MediaItem>> = Result.success(emptyList())
            override suspend fun getMovieDetails(id: String): Result<MediaItem> = Result.success(testMovies.first())
        }

        val vm = MoviesViewModel(
            movieRepository = customRepo,
            searchRepository = fakeSearchRepository,
            munowatchRepository = null,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially in LATEST mode, all 5 movies shown
        assertEquals(MovieSortMode.LATEST, vm.uiState.value.sortMode)
        assertEquals(5, vm.uiState.value.movies.size)

        // Toggle to OLD_MOVIES
        vm.toggleOldMovies()
        testDispatcher.scheduler.advanceUntilIdle()

        val oldState = vm.uiState.value
        assertEquals(MovieSortMode.OLD_MOVIES, oldState.sortMode)
        assertEquals(3, oldState.movies.size)
        assertTrue(oldState.movies.all { MoviesViewModel.isOldMovie(it) })
        val oldIds = oldState.movies.map { it.id }.toSet()
        assertEquals(setOf("m3", "m4", "m5"), oldIds)

        // Filter Old Movies by Genre: Action
        vm.selectCategory("Action")
        testDispatcher.scheduler.advanceUntilIdle()

        val actionOld = vm.uiState.value.movies
        assertEquals(2, actionOld.size)
        assertEquals(setOf("m3", "m4"), actionOld.map { it.id }.toSet())

        // Filter Old Movies by VJ: VJ Emmy
        vm.selectCategory(null)
        vm.selectVj("VJ Emmy")
        testDispatcher.scheduler.advanceUntilIdle()

        val emmyOld = vm.uiState.value.movies
        assertEquals(1, emmyOld.size)
        assertEquals("m5", emmyOld.first().id) // Matrix by VJ Emmy

        // Toggle back to LATEST
        vm.toggleOldMovies()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(MovieSortMode.LATEST, vm.uiState.value.sortMode)
    }
}

