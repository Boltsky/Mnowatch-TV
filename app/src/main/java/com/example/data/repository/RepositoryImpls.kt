package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.data.api.MunowatchApi
import com.example.data.api.MunowatchHtmlParser
import com.example.data.local.AppDatabase
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.network.NetworkClient
import com.example.data.session.SecureSessionManager
import com.example.domain.model.AppError
import com.example.domain.model.CategoryItem
import com.example.domain.model.CategoryType
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.DiagnosticsInfo
import com.example.domain.model.MediaItem
import com.example.domain.model.PlaybackSource
import com.example.domain.model.SeriesDetail
import com.example.domain.repository.AuthenticationRepository
import com.example.domain.repository.MovieRepository
import com.example.domain.repository.MunowatchRepository
import com.example.domain.repository.PlaybackRepository
import com.example.domain.repository.SearchRepository
import com.example.domain.repository.SeriesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthenticationRepositoryImpl(
    private val sessionManager: SecureSessionManager,
    private val api: MunowatchApi
) : AuthenticationRepository {

    override fun hasStoredSession(): Boolean = sessionManager.hasStoredSession()

    override fun getStoredSessionId(): String? = sessionManager.getSessionId()

    override suspend fun login(email: String, password: String): Result<Boolean> {
        val result = api.login(email, password)
        return if (result.isSuccess) {
            val token = result.getOrNull()
            if (!token.isNullOrBlank()) {
                saveSession(token)
                Result.success(true)
            } else {
                Result.failure(java.io.IOException("Session token not received"))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: java.io.IOException("Login failed"))
        }
    }

    override suspend fun validateSession(sessionId: String): Result<Boolean> {
        return api.validateSession(sessionId)
    }

    override suspend fun saveSession(sessionId: String) {
        sessionManager.saveSessionId(sessionId)
    }

    override suspend fun clearSession() {
        api.logout()
        sessionManager.clearSession()
    }

    override fun observeSessionStatus(): Flow<String> {
        return sessionManager.sessionStateFlow
    }
}

private fun cleanFetchId(id: String?): String? {
    if (id.isNullOrBlank()) return null
    if (id.all { it.isDigit() }) return id

    val disambigMatch = Regex("""^([0-9]+)_\d+$""").find(id)
    if (disambigMatch != null) return disambigMatch.groupValues[1]

    val midMatch = Regex("""[?&](?:mid|id)=([0-9]+)""").find(id)
    if (midMatch != null) return midMatch.groupValues[1]

    val anyDigits = Regex("""([0-9]{3,})""").find(id)
    if (anyDigits != null) return anyDigits.groupValues[1]

    return null
}

private fun mapVjToTwolekedeName(vjName: String): String {
    val clean = vjName.trim()
    val knownMap = mapOf(
        "vj ice p" to "VJ ICEP",
        "vj icep" to "VJ ICEP",
        "vj uncle t" to "Uncle T",
        "uncle t" to "Uncle T",
        "vj shao khan" to "Shao Khan",
        "shao khan" to "Shao Khan",
        "vj martin k" to "Martin K",
        "martin k" to "Martin K",
        "vj aaron" to "VJ Aron",
        "vj aron" to "VJ Aron",
        "vj fredy" to "VJ Freddie",
        "vj freddie" to "VJ Freddie",
        "vj ashim" to "VJ Hashim",
        "vj hashim" to "VJ Hashim",
        "vj dan de" to "VJ Dan Dee",
        "vj dan dee" to "VJ Dan Dee",
        "vj ivo" to "VJ IVO Kakorea",
        "vj ivo kakorea" to "VJ IVO Kakorea",
        "vj isma  k" to "VJ Isma K",
        "vj isma k" to "VJ Isma K",
        "vj hd" to "VJ HD",
        "vj kam" to "VJ Kamran",
        "vj kamran" to "VJ Kamran"
    )
    val lower = clean.lowercase()
    if (knownMap.containsKey(lower)) {
        return knownMap[lower]!!
    }
    return if (clean.startsWith("vj ", ignoreCase = true)) {
        "VJ " + clean.substring(3).trim()
    } else {
        clean
    }
}

private fun mapGenreToTwolekedeName(genreName: String): String {
    val clean = genreName.trim()
    val knownMap = mapOf(
        "love story" to "Romance",
        "kungu fu" to "Martial Arts",
        "korean" to "Korean Drama",
        "indian" to "Indian Movie",
        "sci fi" to "Sci-Fi",
        "sci-fi" to "Sci-Fi",
        "series" to "Series",
        "serie" to "Series"
    )
    val lower = clean.lowercase()
    return knownMap[lower] ?: clean
}

private val FALLBACK_VJ_NAMES = mapOf(
    1 to "VJ Junior",
    2 to "VJ Emmy",
    9 to "VJ ICEP",
    4 to "VJ Tom",
    38 to "VJ Ulio",
    8 to "VJ Jingo",
    5 to "VJ Shao Khan",
    26 to "VJ Sammy",
    57 to "VJ Soul",
    14 to "VJ Kevo",
    16 to "VJ Kevin",
    24 to "VJ HD",
    25 to "VJ Dan De"
)

private val FALLBACK_GENRE_NAMES = mapOf(
    1 to "Action",
    2 to "Horror",
    7 to "Adventure",
    8 to "Love Story",
    9 to "Comedy",
    12 to "Crime",
    13 to "Family",
    14 to "Sci-Fi",
    16 to "Martial Arts",
    17 to "Drama",
    18 to "Sport",
    19 to "Thriller",
    20 to "Animation"
)

private fun toCategorySlug(categoryName: String): String {
    val clean = categoryName.lowercase().trim()
    return when (clean) {
        "love story", "romance" -> "love-story"
        "martial arts", "kung fu", "kungu fu" -> "martial-arts"
        "sci-fi", "sci fi" -> "sci-fi"
        else -> clean.replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
    }
}

private fun toVjSlug(vjName: String): String {
    val clean = vjName.lowercase().trim()
    val base = clean.replace("vj", "").replace(".", "").replace(Regex("""[^a-z0-9]"""), " ").trim()
    return when (base) {
        "icep", "ice p" -> "vj-ice-p"
        "shao khan", "shaokhan" -> "vj-shao-khan"
        "dan de", "dan dee", "dandee" -> "vj-dan-de"
        "ivo", "ivo kakorea" -> "vj-ivo"
        else -> "vj-" + base.replace(Regex("""\s+"""), "-")
    }
}

class MovieRepositoryImpl(
    private val api: MunowatchApi,
    private val parser: MunowatchHtmlParser
) : MovieRepository {

    private var cachedHero: MediaItem? = null

    override suspend fun getFeaturedHero(): Result<MediaItem?> {
        if (cachedHero != null) return Result.success(cachedHero)
        val latest = getLatestMovies(null)
        val hero = latest.getOrNull()?.firstOrNull()
        cachedHero = hero
        return Result.success(hero)
    }

    override suspend fun getLatestMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> {
        val cleanFetch = cleanFetchId(lastFetchId)
        val targetPage = page ?: 1

        // Priority 1: Fetch directly from media server movies catalog (no session cookie required)
        val twolekedeRes = api.getTwolekedeMoviesCatalog(page = targetPage)
        var parsed = if (twolekedeRes.isSuccess) {
            parser.parseMediaCards(twolekedeRes.getOrDefault(""))
        } else {
            emptyList()
        }

        // Priority 2: Fallback to Munowatch grid-slot if media server catalog is empty
        if (parsed.isEmpty()) {
            val res = api.getGridSlot(pipeType = "p", pipeId = "4", lastFetchId = cleanFetch, page = page)
            parsed = res.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
        }

        if (parsed.isEmpty() && lastFetchId == null && (page == null || page == 1)) {
            val cached = MediaCache.getPopularMovies()
            if (cached.isNotEmpty()) {
                return Result.success(cached)
            }
        }

        MediaCache.putAll(parsed)
        if (parsed.isNotEmpty() && targetPage == 1) {
            MediaCache.setPopularMovies(parsed)
        }
        if (cachedHero == null && parsed.isNotEmpty()) {
            cachedHero = parsed.first()
        }
        return Result.success(parsed)
    }

    override suspend fun getFreeMovies(lastFetchId: String?, page: Int?): Result<List<MediaItem>> {
        val cleanFetch = cleanFetchId(lastFetchId)
        val targetPage = page ?: 1

        // Priority 1: Fetch directly from media server catalog
        val freeRes = api.getTwolekedeCategoryCatalog("Free", page = targetPage)
        var parsed = if (freeRes.isSuccess) {
            parser.parseMediaCards(freeRes.getOrDefault(""))
        } else {
            emptyList()
        }

        if (parsed.isEmpty()) {
            val twolekedeRes = api.getTwolekedeMoviesCatalog(page = targetPage)
            if (twolekedeRes.isSuccess) {
                parsed = parser.parseMediaCards(twolekedeRes.getOrDefault(""))
            }
        }

        // Priority 2: Fallback to Munowatch free grid-slot
        if (parsed.isEmpty()) {
            val res = api.getGridSlot(pipeType = "fr", pipeId = "4", lastFetchId = cleanFetch, page = page)
            parsed = res.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
        }

        if (parsed.isEmpty() && lastFetchId == null && (page == null || page == 1)) {
            val cached = MediaCache.getFreeMovies()
            if (cached.isNotEmpty()) {
                return Result.success(cached)
            }
        }

        MediaCache.putAll(parsed)
        if (parsed.isNotEmpty() && targetPage == 1) {
            MediaCache.setFreeMovies(parsed)
        }
        return Result.success(parsed)
    }

    override suspend fun getMoviesByGenre(
        genreId: Int,
        lastFetchId: String?,
        pipe: String?,
        page: Int?
    ): Result<List<MediaItem>> {
        val cleanFetch = cleanFetchId(lastFetchId)
        val targetPage = page ?: 1

        val genreName = MediaCache.getGenreName(genreId)
            ?: FALLBACK_GENRE_NAMES[genreId]
            ?: com.example.domain.model.DEFAULT_GENRE_CATEGORIES.find { it.id == genreId }?.name
            ?: api.getBrowseCategories().getOrNull()?.genre?.find { it.id == genreId }?.name?.trim()
            ?: ""

        var parsed = emptyList<MediaItem>()

        // Priority 1: Direct Twolekede media server genre catalog (No session cookie needed)
        // Fetch 12 pages per batch concurrently for a deep, rich genre catalog (240+ movies)
        if (genreName.isNotBlank()) {
            if (genreName.equals("Series", ignoreCase = true) || genreName.equals("Serie", ignoreCase = true)) {
                val pagesPerBatch = 6
                val startSeriesPage = (targetPage - 1) * pagesPerBatch + 1
                val endSeriesPage = startSeriesPage + pagesPerBatch - 1
                try {
                    coroutineScope {
                        val seriesJobs = (startSeriesPage..endSeriesPage).map { p ->
                            async {
                                try {
                                    val sRes = api.getTwolekedeSeriesCatalog(page = p)
                                    if (sRes.isSuccess) {
                                        parser.parseMediaCards(sRes.getOrDefault("")).map { it.copy(isSeries = true, genre = "Series") }
                                    } else emptyList()
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        parsed = seriesJobs.awaitAll().flatten().distinctBy { it.id }
                    }
                } catch (_: Exception) {}
            } else {
                val twolekedeGenre = mapGenreToTwolekedeName(genreName)
                val pagesPerBatch = 12
                val startGenrePage = (targetPage - 1) * pagesPerBatch + 1
                val endGenrePage = startGenrePage + pagesPerBatch - 1
                try {
                    coroutineScope {
                        val pageJobs = (startGenrePage..endGenrePage).map { p ->
                            async {
                                try {
                                    val genreRes = api.getTwolekedeGenreCatalog(twolekedeGenre, page = p)
                                    genreRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        parsed = pageJobs.awaitAll().flatten().distinctBy { it.id }
                    }
                } catch (_: Exception) {}
            }
        }

        // Priority 2: Fallback to Munowatch grid-slot if media server catalog is empty
        if (parsed.isEmpty()) {
            val res = api.getGridSlot(
                pipeType = "g",
                pipeId = genreId.toString(),
                lastFetchId = cleanFetch,
                page = targetPage
            )
            parsed = res.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
        }

        if (parsed.isEmpty()) {
            val secondaryPipe = pipe?.takeIf { it.isNotBlank() && it != "g" } ?: "genre_vj"
            val fbRes = api.getGridSlot(
                pipeType = secondaryPipe,
                pipeId = genreId.toString(),
                lastFetchId = cleanFetch,
                page = targetPage
            )
            parsed = fbRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
        }

        // Priority 3: Targeted media server search fallback
        if (parsed.isEmpty() && genreName.isNotBlank()) {
            val searchRes = api.searchTwolekede(genreName)
            val searchParsed = searchRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
            val cachedMatches = MediaCache.getAll().filter {
                it.genre.contains(genreName, ignoreCase = true) || it.title.contains(genreName, ignoreCase = true)
            }
            parsed = (searchParsed + cachedMatches).distinctBy { it.id }
        }

        if (genreName.isNotBlank()) {
            parsed = parsed.map { if (it.genre.isBlank()) it.copy(genre = genreName) else it }
        }

        MediaCache.putAll(parsed)
        return Result.success(parsed)
    }

    override suspend fun getMoviesByVj(
        vjId: Int,
        lastFetchId: String?,
        pipe: String?,
        page: Int?
    ): Result<List<MediaItem>> {
        val cleanFetch = cleanFetchId(lastFetchId)
        val targetPage = page ?: 1

        val vjName = MediaCache.getVjName(vjId)
            ?: FALLBACK_VJ_NAMES[vjId]
            ?: com.example.domain.model.DEFAULT_VJ_CATEGORIES.find { it.id == vjId }?.name
            ?: api.getBrowseCategories().getOrNull()?.vj?.find { it.id == vjId }?.name?.trim()
            ?: ""

        // Priority 0: Check memory/disk cache first for instant rendering on page 1
        val cached = MediaCache.getVjMovies(vjId)
        if (!cached.isNullOrEmpty() && targetPage == 1) {
            return Result.success(cached)
        }

        var parsed = emptyList<MediaItem>()

        // Priority 1: Direct Twolekede media server VJ catalog (No session cookie needed)
        // Twolekede categories.php returns the complete catalog for this VJ in a single fast response
        if (vjName.isNotBlank()) {
            val twolekedeVj = mapVjToTwolekedeName(vjName)
            try {
                val catRes = api.getTwolekedeCategoryCatalog(twolekedeVj, page = 1)
                val catHtml = catRes.getOrNull()
                if (!catHtml.isNullOrBlank()) {
                    parsed = parser.parseMediaCards(catHtml)
                }
            } catch (_: Exception) {}
        }

        // Priority 2: Fallback to Munowatch grid-slot if media server catalog is empty
        if (parsed.isEmpty()) {
            val res = api.getGridSlot(
                pipeType = "vj",
                pipeId = vjId.toString(),
                lastFetchId = cleanFetch,
                page = targetPage
            )
            parsed = res.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
        }

        if (parsed.isEmpty()) {
            val secondaryPipe = pipe?.takeIf { it.isNotBlank() && it != "vj" } ?: "vj_genre"
            val fbRes = api.getGridSlot(
                pipeType = secondaryPipe,
                pipeId = vjId.toString(),
                lastFetchId = cleanFetch,
                page = targetPage
            )
            parsed = fbRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
        }

        // Priority 3: Targeted media server search fallback
        if (parsed.isEmpty() && vjName.isNotBlank()) {
            val searchRes = api.searchTwolekede(vjName, isSeries = false)
            val searchParsed = searchRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
            // Strictly exclude series from cached movie matches
            val cachedMatches = MediaCache.getAll().filter {
                !it.isSeries && it.vj.isNotBlank() && (it.vj.equals(vjName, ignoreCase = true) || it.vj.contains(vjName, ignoreCase = true))
            }
            parsed = (searchParsed + cachedMatches).distinctBy { it.id }
        }

        if (vjName.isNotBlank()) {
            parsed = parsed.map { if (it.vj.isBlank()) it.copy(vj = vjName) else it }
        }

        if (parsed.isNotEmpty()) {
            val existing = MediaCache.getVjMovies(vjId) ?: emptyList()
            val merged = (existing + parsed).distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }
            MediaCache.setVjMovies(vjId, merged)
            MediaCache.putAll(parsed)
        }
        return Result.success(parsed)
    }

    private fun cleanTitleKey(raw: String): String {
        if (raw.isBlank()) return ""
        return parser.cleanTitle(raw)
            .lowercase()
            .replace(Regex("""[^a-z0-9]"""), "")
    }

    override suspend fun getMoviesByVjAndGenre(
        vjId: Int,
        genreId: Int,
        lastFetchId: String?,
        pipe: String?,
        page: Int?
    ): Result<List<MediaItem>> {
        val cleanFetch = cleanFetchId(lastFetchId)
        val targetPage = page ?: 1

        val vjName = MediaCache.getVjName(vjId)
            ?: FALLBACK_VJ_NAMES[vjId]
            ?: com.example.domain.model.DEFAULT_VJ_CATEGORIES.find { it.id == vjId }?.name
            ?: api.getBrowseCategories().getOrNull()?.vj?.find { it.id == vjId }?.name?.trim()
            ?: ""

        val genreName = MediaCache.getGenreName(genreId)
            ?: FALLBACK_GENRE_NAMES[genreId]
            ?: com.example.domain.model.DEFAULT_GENRE_CATEGORIES.find { it.id == genreId }?.name
            ?: api.getBrowseCategories().getOrNull()?.genre?.find { it.id == genreId }?.name?.trim()
            ?: ""

        val accumulated = mutableListOf<MediaItem>()

        val twolekedeVj = if (vjName.isNotBlank()) mapVjToTwolekedeName(vjName) else ""
        val twolekedeGenre = if (genreName.isNotBlank()) mapGenreToTwolekedeName(genreName) else ""
        val pagesPerBatch = 18
        val startBatchPage = (targetPage - 1) * pagesPerBatch + 1
        val endBatchPage = startBatchPage + pagesPerBatch - 1

        // 1. Authoritative Twolekede VJ Category Catalog: obtain complete VJ catalog
        var vjMovies = MediaCache.getVjMovies(vjId)
        if (vjMovies.isNullOrEmpty() && twolekedeVj.isNotBlank()) {
            try {
                val catRes = api.getTwolekedeCategoryCatalog(twolekedeVj, page = 1)
                val catHtml = catRes.getOrNull()
                if (!catHtml.isNullOrBlank()) {
                    val parsed = parser.parseMediaCards(catHtml)
                    if (parsed.isNotEmpty()) {
                        val withVj = parsed.map { if (it.vj.isBlank()) it.copy(vj = vjName) else it }
                        MediaCache.setVjMovies(vjId, withVj)
                        vjMovies = withVj
                    }
                }
            } catch (_: Exception) {}
        }
        val allVjMovies = vjMovies ?: emptyList()

        // Build O(1) correlation sets for ultra-fast lookup (zero UI lag)
        val vjMovieIdSet = HashSet<String>(allVjMovies.size)
        val vjMovieStreamUrlSet = HashSet<String>(allVjMovies.size)
        val vjMovieTitleSet = HashSet<String>(allVjMovies.size)
        for (m in allVjMovies) {
            if (m.id.isNotBlank()) vjMovieIdSet.add(m.id)
            if (m.streamUrl.isNotBlank()) vjMovieStreamUrlSet.add(m.streamUrl)
            val cleanKey = cleanTitleKey(m.title)
            if (cleanKey.isNotBlank()) vjMovieTitleSet.add(cleanKey)
        }

        // On initial page (page 1), include matching movies from VJ's authoritative catalog
        if (targetPage == 1 && allVjMovies.isNotEmpty()) {
            for (item in allVjMovies) {
                if (genreName.isBlank() || com.example.presentation.movies.MoviesViewModel.matchesCategory(item, genreName)) {
                    val withGenre = if (item.genre.isBlank() && genreName.isNotBlank()) item.copy(genre = genreName) else item
                    com.example.presentation.movies.MoviesViewModel.recordItemCategory(withGenre.id, genreName)
                    accumulated.add(withGenre)
                }
            }
        }

        // 2. Authoritative Twolekede Genre Catalog: batch scan across Twolekede genre pages with O(1) VJ correlation
        if (twolekedeGenre.isNotBlank()) {
            try {
                coroutineScope {
                    val genrePageJobs = (startBatchPage..endBatchPage).map { p ->
                        async {
                            try {
                                val genreRes = api.getTwolekedeGenreCatalog(twolekedeGenre, page = p)
                                genreRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val allGenreItems = genrePageJobs.awaitAll().flatten().distinctBy { it.id }
                    for (item in allGenreItems) {
                        val cleanKey = cleanTitleKey(item.title)
                        val isVjMatch = when {
                            item.vj.isNotBlank() && !com.example.presentation.movies.MoviesViewModel.matchesVj(item, vjName) -> false
                            vjMovieIdSet.contains(item.id) -> true
                            item.streamUrl.isNotBlank() && vjMovieStreamUrlSet.contains(item.streamUrl) -> true
                            cleanKey.isNotBlank() && vjMovieTitleSet.contains(cleanKey) -> true
                            com.example.presentation.movies.MoviesViewModel.matchesVj(item, vjName) -> true
                            else -> false
                        }

                        if (isVjMatch) {
                            val finalTagged = item.copy(
                                vj = vjName,
                                genre = if (item.genre.isBlank() && genreName.isNotBlank()) genreName else item.genre
                            )
                            com.example.presentation.movies.MoviesViewModel.recordItemCategory(finalTagged.id, genreName)
                            accumulated.add(finalTagged)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Supplementary Munowatch Grid Slot (VJ + Genre pipe)
        try {
            val res = api.getGridSlot(
                pipeType = "vj",
                pipeId = vjId.toString(),
                vjId = vjId.toString(),
                genreId = genreId.toString(),
                lastFetchId = cleanFetch,
                page = targetPage
            )
            val munoParsed = res.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
            for (item in munoParsed) {
                val tagged = if (item.vj.isBlank() && vjName.isNotBlank()) item.copy(vj = vjName) else item
                if (com.example.presentation.movies.MoviesViewModel.matchesVj(tagged, vjName) &&
                    (genreName.isBlank() || com.example.presentation.movies.MoviesViewModel.matchesCategory(tagged, genreName))) {
                    val withGenre = if (tagged.genre.isBlank() && genreName.isNotBlank()) tagged.copy(genre = genreName) else tagged
                    com.example.presentation.movies.MoviesViewModel.recordItemCategory(withGenre.id, genreName)
                    accumulated.add(withGenre)
                }
            }
        } catch (_: Exception) {}

        // 4. Targeted Twolekede Search fallback if few items found on initial page
        if (accumulated.isEmpty() && targetPage == 1 && vjName.isNotBlank() && genreName.isNotBlank()) {
            val cleanVj = vjName.replace("VJ", "", ignoreCase = true).trim()
            try {
                val searchRes = api.searchTwolekede("$cleanVj $genreName")
                val searchParsed = searchRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                for (item in searchParsed) {
                    val tagged = if (item.vj.isBlank()) item.copy(vj = vjName) else item
                    if (com.example.presentation.movies.MoviesViewModel.matchesVj(tagged, vjName) &&
                        com.example.presentation.movies.MoviesViewModel.matchesCategory(tagged, genreName)) {
                        com.example.presentation.movies.MoviesViewModel.recordItemCategory(tagged.id, genreName)
                        accumulated.add(tagged)
                    }
                }
            } catch (_: Exception) {}
        }

        val distinctList = accumulated.distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }.map { item ->
            val withVj = if (item.vj.isBlank() && vjName.isNotBlank()) item.copy(vj = vjName) else item
            val withGenre = if (withVj.genre.isBlank() && genreName.isNotBlank()) withVj.copy(genre = genreName) else withVj
            withGenre
        }

        if (targetPage == 1) {
            MediaCache.setVjGenreMovies(vjId, genreId, distinctList)
        } else {
            val existing = MediaCache.getVjGenreMovies(vjId, genreId) ?: emptyList()
            val merged = (existing + distinctList).distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }
            MediaCache.setVjGenreMovies(vjId, genreId, merged)
        }

        MediaCache.putAll(distinctList)
        return Result.success(distinctList)
    }

    override suspend fun getFilteredMovies(
        year: String?,
        category: String?,
        vj: String?,
        page: Int,
        perPage: Int
    ): Result<List<MediaItem>> {
        val catSlug = category?.takeIf {
            it.isNotBlank() && !it.equals("All Categories", ignoreCase = true) && !it.equals("All Genres", ignoreCase = true)
        }?.let { toCategorySlug(it) }

        val vjSlug = vj?.takeIf {
            it.isNotBlank() && !it.equals("All VJs", ignoreCase = true)
        }?.let { toVjSlug(it) }

        val cleanYear = year?.takeIf { it.isNotBlank() }
        val cleanVj = vj?.takeIf { it.isNotBlank() && !it.equals("All VJs", ignoreCase = true) }
        val cleanCat = category?.takeIf { it.isNotBlank() && !it.equals("All Categories", ignoreCase = true) && !it.equals("All Genres", ignoreCase = true) }
        val accumulated = mutableListOf<MediaItem>()

        fun matchYear(itemYear: String, itemTitle: String): Boolean {
            if (cleanYear == null) return true
            return itemYear.contains(cleanYear) || (itemYear.isNotBlank() && cleanYear.contains(itemYear)) || itemTitle.contains(cleanYear)
        }
        fun matchVj(itemVj: String): Boolean {
            if (cleanVj == null) return true
            return itemVj.isBlank() || itemVj.contains(cleanVj, ignoreCase = true)
        }
        fun matchCat(itemGenre: String): Boolean {
            if (cleanCat == null) return true
            return itemGenre.isNotBlank() && itemGenre != "-" && itemGenre.contains(cleanCat, ignoreCase = true)
        }

        // PRIORITY 1: Fetch from Twolekede Media Server Catalogs (100% playable full HD streams)
        val pagesPerBatch = 6
        if (cleanVj != null && cleanCat != null) {
            val twolekedeGenre = mapGenreToTwolekedeName(cleanCat)
            val twolekedeVj = mapVjToTwolekedeName(cleanVj)
            val startGenrePage = (page - 1) * pagesPerBatch + 1
            val endGenrePage = startGenrePage + pagesPerBatch - 1
            try {
                coroutineScope {
                    val vjPageJobs = (startGenrePage..endGenrePage).map { p ->
                        async {
                            try {
                                val tkRes = api.getTwolekedeCategoryCatalog(twolekedeVj, page = p)
                                tkRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val genrePageJobs = (startGenrePage..endGenrePage).map { p ->
                        async {
                            try {
                                val tkRes = api.getTwolekedeGenreCatalog(twolekedeGenre, page = p)
                                tkRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val allVjItems = vjPageJobs.awaitAll().flatten().distinctBy { it.id }
                    for (item in allVjItems) {
                        val tagged = item.copy(vj = cleanVj)
                        if (matchYear(tagged.year, tagged.title) && com.example.presentation.movies.MoviesViewModel.matchesCategory(tagged, cleanCat)) {
                            val finalTagged = if (tagged.genre.isBlank()) tagged.copy(genre = cleanCat) else tagged
                            accumulated.add(finalTagged)
                        }
                    }
                    val allGenreItems = genrePageJobs.awaitAll().flatten().distinctBy { it.id }
                    for (item in allGenreItems) {
                        val tagged = if (item.genre.isBlank()) item.copy(genre = cleanCat) else item
                        if (matchYear(tagged.year, tagged.title) && com.example.presentation.movies.MoviesViewModel.matchesVj(tagged, cleanVj)) {
                            val finalTagged = if (tagged.vj.isBlank()) tagged.copy(vj = cleanVj) else tagged
                            accumulated.add(finalTagged)
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (cleanVj != null) {
            val twolekedeVj = mapVjToTwolekedeName(cleanVj)
            val startVjPage = (page - 1) * pagesPerBatch + 1
            val endVjPage = startVjPage + pagesPerBatch - 1
            try {
                coroutineScope {
                    val pageJobs = (startVjPage..endVjPage).map { p ->
                        async {
                            try {
                                val tkRes = api.getTwolekedeCategoryCatalog(twolekedeVj, page = p)
                                tkRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val allPages = pageJobs.awaitAll().flatten().distinctBy { it.id }
                    for (item in allPages) {
                        val tagged = if (item.vj.isBlank()) item.copy(vj = cleanVj) else item
                        if (matchYear(item.year, item.title)) {
                            accumulated.add(tagged)
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (cleanCat != null) {
            val twolekedeGenre = mapGenreToTwolekedeName(cleanCat)
            val startGenrePage = (page - 1) * pagesPerBatch + 1
            val endGenrePage = startGenrePage + pagesPerBatch - 1
            try {
                coroutineScope {
                    val pageJobs = (startGenrePage..endGenrePage).map { p ->
                        async {
                            try {
                                val tkRes = api.getTwolekedeGenreCatalog(twolekedeGenre, page = p)
                                tkRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val allPages = pageJobs.awaitAll().flatten().distinctBy { it.id }
                    for (item in allPages) {
                        val tagged = if (item.genre.isBlank()) item.copy(genre = cleanCat) else item
                        if (matchYear(item.year, item.title) && (cleanVj == null || com.example.presentation.movies.MoviesViewModel.matchesVj(tagged, cleanVj))) {
                            accumulated.add(tagged)
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (cleanYear != null) {
            val vjsToScan = listOf("VJ Junior", "VJ ICEP", "VJ Emmy", "VJ Jingo")
            for (vjName in vjsToScan) {
                if (accumulated.size >= perPage * page + 16) break
                try {
                    val tkRes = api.getTwolekedeCategoryCatalog(vjName, page = 1)
                    val tkParsed = tkRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                    for (item in tkParsed) {
                        if (matchYear(item.year, item.title)) {
                            val tagged = if (item.vj.isBlank()) item.copy(vj = vjName) else item
                            if (accumulated.none { it.id == tagged.id || (it.streamUrl.isNotBlank() && it.streamUrl == tagged.streamUrl) || parser.titlesMatch(it.title, tagged.title) }) {
                                accumulated.add(tagged)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            if (accumulated.isEmpty()) {
                try {
                    val tkRes = api.getTwolekedeMoviesCatalog(page = page)
                    val tkParsed = tkRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                    for (item in tkParsed) {
                        if (matchYear(item.year, item.title)) {
                            accumulated.add(item)
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            // No filters active: load live Twolekede movies catalog
            try {
                val tkRes = api.getTwolekedeMoviesCatalog(page = page)
                val tkParsed = tkRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
                accumulated.addAll(tkParsed)
            } catch (_: Exception) {}
        }

        // PRIORITY 2: Supplement from myvj.net Filtered Catalog (API) only if needed
        if (accumulated.size < perPage) {
            try {
                val apiRes = api.getFilteredCatalog(
                    year = cleanYear,
                    categorySlug = catSlug,
                    vjSlug = vjSlug,
                    mediaType = 1,
                    page = page,
                    perPage = perPage
                )
                if (apiRes.isSuccess) {
                    val items = apiRes.getOrDefault(emptyList())
                    for (item in items) {
                        if (cleanVj != null && !com.example.presentation.movies.MoviesViewModel.matchesVj(item, cleanVj)) continue
                        if (cleanCat != null && !com.example.presentation.movies.MoviesViewModel.matchesCategory(item, cleanCat)) continue
                        if (cleanYear != null && !matchYear(item.year, item.title)) continue
                        if (accumulated.none { it.id == item.id || (it.streamUrl.isNotBlank() && it.streamUrl == item.streamUrl) || parser.titlesMatch(it.title, item.title) }) {
                            accumulated.add(item)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val distinctList = accumulated.distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }
        if (distinctList.isNotEmpty()) {
            MediaCache.putAll(distinctList)
            return Result.success(distinctList)
        }
        return Result.success(emptyList())
    }

    override suspend fun getMovieDetails(id: String): Result<MediaItem> {
        val cached = MediaCache.getMovieDetail(id) ?: MediaCache.get(id)
        if (cached != null && cached.description.isNotBlank() && !parser.isPlaceholderOrGenericDescription(cached.description) && !cached.title.startsWith("Movie #", ignoreCase = true)) {
            return Result.success(cached)
        }
        val isSeries = cached?.isSeries == true || id.startsWith("series_")
        val numericId = MediaCache.extractNumericId(id) ?: cached?.streamUrl?.let { MediaCache.extractNumericId(it) }

        val hasRealCachedTitle = cached != null && cached.title.isNotBlank() &&
            !cached.title.startsWith("Movie ", ignoreCase = true) &&
            !cached.title.startsWith("Movie#", ignoreCase = true) &&
            !cached.title.startsWith("Movie_", ignoreCase = true)
        val expectedTitle = if (hasRealCachedTitle) cached!!.title else null

        var extractedDesc = ""
        var resolvedTwolekedeId: String? = null
        var myVjDetailObj: org.json.JSONObject? = null

        // Priority Step 1: Rapid MyVJ / UgaWatch details fetch by slug (~120ms latency)
        val itemSlug = MediaCache.getSlug(id)
            ?: MediaCache.getSlug(id.removePrefix("myvj_"))
            ?: (if (id.startsWith("myvj_")) id.removePrefix("myvj_").substringAfter("_").takeIf { it.isNotBlank() && !it.startsWith("-") && !it.all { c -> c.isDigit() } } else null)
            ?: if (hasRealCachedTitle) parser.cleanTitle(cached!!.title).lowercase().replace(" ", "-").replace(Regex("""[^a-z0-9\-]"""), "").trim('-').takeIf { it.length >= 2 } else null

        if (itemSlug != null && itemSlug.isNotBlank()) {
            try {
                val myVjRes = if (isSeries) api.getMyVjSeriesDetail(itemSlug) else api.getMyVjMovieDetail(itemSlug)
                myVjDetailObj = myVjRes.getOrNull()
                if (myVjDetailObj != null) {
                    val about = myVjDetailObj.optString("about").ifBlank { myVjDetailObj.optString("overview") }
                    if (about.isNotBlank() && !parser.isPlaceholderOrGenericDescription(about)) {
                        extractedDesc = about
                    }
                    val munoId = myVjDetailObj.optString("muno_id").takeIf { it.isNotBlank() && it != "null" }
                    if (munoId != null) {
                        MediaCache.putMunoId(id, munoId)
                        MediaCache.putMunoId(id.removePrefix("myvj_"), munoId)
                    }
                }
            } catch (_: Exception) {}
        }

        // If slug details returned empty or placeholder about, try stripping trailing zeros from slug (e.g. antlers0 -> antlers)
        if ((extractedDesc.isBlank() || parser.isPlaceholderOrGenericDescription(extractedDesc)) && itemSlug != null && itemSlug.endsWith("0")) {
            val trimmedSlug = itemSlug.trimEnd('0')
            if (trimmedSlug.isNotBlank()) {
                try {
                    val retryRes = if (isSeries) api.getMyVjSeriesDetail(trimmedSlug) else api.getMyVjMovieDetail(trimmedSlug)
                    val retryObj = retryRes.getOrNull()
                    if (retryObj != null) {
                        val about = retryObj.optString("about").ifBlank { retryObj.optString("overview") }
                        if (about.isNotBlank() && !parser.isPlaceholderOrGenericDescription(about)) {
                            extractedDesc = about
                            if (myVjDetailObj == null) myVjDetailObj = retryObj
                        }
                        val munoId = retryObj.optString("muno_id").takeIf { it.isNotBlank() && it != "null" }
                        if (munoId != null) {
                            MediaCache.putMunoId(id, munoId)
                            MediaCache.putMunoId(id.removePrefix("myvj_"), munoId)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val isExplicitTwolekede = (cached?.streamUrl?.contains("twolekede.com") == true ||
            id.contains("preview.php") || id.contains("moviedetails.php") || id.startsWith("twolekede_")) &&
            !id.startsWith("myvj_")

        var streamPath = cached?.streamUrl?.takeIf { it.isNotBlank() }
        var watchHtml = ""
        var watchHtmlValid = false

        // Step 2: Only fetch watch HTML if description is still missing or series episode list is needed
        val needsWatchHtml = (extractedDesc.isBlank() || parser.isPlaceholderOrGenericDescription(extractedDesc) || isSeries)
        if (needsWatchHtml) {
            if (streamPath.isNullOrBlank() && !isSeries && numericId != null && isExplicitTwolekede) {
                streamPath = "https://twolekede.com/moviedetails.php?id=$numericId"
            }
            if (streamPath.isNullOrBlank() && !isSeries && !id.startsWith("myvj_")) {
                streamPath = api.resolveStreamPathForId(id)
            }

            val watchRes = when {
                isSeries -> api.getSeriesList(id)
                streamPath != null -> api.getWatchPageHtml(streamPath)
                else -> null
            }
            watchHtml = watchRes?.getOrNull().orEmpty()
            if (watchHtml.isNotBlank()) {
                val extractedTitle = parser.extractMovieTitleFromWatchPage(watchHtml, streamPath ?: "")
                if (expectedTitle != null) {
                    if (!extractedTitle.isNullOrBlank() && parser.titlesMatch(extractedTitle, expectedTitle)) {
                        watchHtmlValid = true
                    }
                } else if (!extractedTitle.isNullOrBlank() && !extractedTitle.startsWith("Movie ", ignoreCase = true)) {
                    watchHtmlValid = true
                }
            }
            if (!watchHtmlValid) {
                watchHtml = ""
            }
            if (extractedDesc.isBlank() && watchHtmlValid) {
                extractedDesc = parser.extractMovieDescriptionFromWatchPage(watchHtml)
            }
        }

        val realTitle = when {
            hasRealCachedTitle -> cached!!.title
            watchHtmlValid -> parser.extractMovieTitleFromWatchPage(watchHtml, streamPath ?: "") ?: cached?.title ?: if (isSeries) "Series #$id" else "Movie #$id"
            else -> cached?.title ?: if (isSeries) "Series #$id" else "Movie #$id"
        }

        val cleanT = parser.cleanTitle(realTitle)
            .replace(Regex("""\[.*?\]|\(.*?\)|<.*?>"""), " ")
            .replace(Regex("""(?i)\s+(?:19\d{2}|20\d{2})\s*$"""), " ")
            .replace(Regex("""(?i)\b(?:by\s+vj|vj)\s+[a-z0-9_\-\.]*.*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '–', '—', ':', '|')

        // Search MyVJ API for genuine synopsis and metadata if still needed
        if ((extractedDesc.isBlank() || parser.isPlaceholderOrGenericDescription(extractedDesc)) && cleanT.length >= 2) {
            try {
                val myVjSearchRes = api.searchMyVj(cleanT).getOrNull()
                if (myVjSearchRes != null) {
                    val moviesArr = if (isSeries) myVjSearchRes.optJSONArray("matches_title_series") else myVjSearchRes.optJSONArray("matches_title_movies")
                    if (moviesArr != null) {
                        val rawRowId = id.removePrefix("myvj_").substringBefore("_")
                        var matchedObj: org.json.JSONObject? = null
                        for (i in 0 until moviesArr.length()) {
                            val mObj = moviesArr.getJSONObject(i)
                            val rowId = mObj.optString("row_id").ifBlank { mObj.optString("id") }
                            if (rowId.isNotBlank() && rowId == rawRowId) {
                                matchedObj = mObj
                                break
                            }
                        }
                        if (matchedObj == null) {
                            for (i in 0 until moviesArr.length()) {
                                val mObj = moviesArr.getJSONObject(i)
                                val title = mObj.optString("title")
                                if (parser.titlesMatch(parser.cleanTitle(title), cleanT)) {
                                    matchedObj = mObj
                                    break
                                }
                            }
                        }
                        if (matchedObj != null) {
                            val about = matchedObj.optString("about").ifBlank { matchedObj.optString("overview") }
                            if (about.isNotBlank() && !parser.isPlaceholderOrGenericDescription(about)) {
                                extractedDesc = about
                            }
                            if (myVjDetailObj == null) {
                                myVjDetailObj = matchedObj
                            }
                            val matchedSlug = matchedObj.optString("slug")
                            if (matchedSlug.isNotBlank()) {
                                MediaCache.putSlug(id, matchedSlug)
                                MediaCache.putSlug(id.removePrefix("myvj_"), matchedSlug)
                            }
                            val munoId = matchedObj.optString("muno_id").takeIf { it.isNotBlank() && it != "null" }
                            if (munoId != null) {
                                MediaCache.putMunoId(id, munoId)
                                MediaCache.putMunoId(id.removePrefix("myvj_"), munoId)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If watch page did not yield a real description and we have an explicit Twolekede item, try its moviedetails.php
        if ((extractedDesc.isBlank() || parser.isPlaceholderOrGenericDescription(extractedDesc)) && !isSeries && numericId != null && isExplicitTwolekede) {
            val tkDetailsRes = api.getWatchPageHtml("https://twolekede.com/moviedetails.php?id=$numericId")
            val tkHtml = tkDetailsRes.getOrDefault("")
            if (tkHtml.isNotBlank()) {
                val tkTitle = parser.extractMovieTitleFromWatchPage(tkHtml, "moviedetails.php?id=$numericId")
                if (expectedTitle == null || (tkTitle != null && parser.titlesMatch(tkTitle, expectedTitle))) {
                    val tkDesc = parser.extractMovieDescriptionFromWatchPage(tkHtml)
                    if (tkDesc.isNotBlank() && !parser.isPlaceholderOrGenericDescription(tkDesc)) {
                        extractedDesc = tkDesc
                        resolvedTwolekedeId = numericId
                    }
                }
            }
        }

        // Search Twolekede by clean title if description or verified Twolekede ID is still needed
        if ((extractedDesc.isBlank() || parser.isPlaceholderOrGenericDescription(extractedDesc) || resolvedTwolekedeId == null) &&
            !isSeries && !realTitle.startsWith("Movie ", ignoreCase = true) && cleanT.length >= 2) {
            val candidateQueries = linkedSetOf<String>()
            candidateQueries.add(cleanT)
            val noArticle = cleanT.replace(Regex("""^(?:the|a|an)\s+""", RegexOption.IGNORE_CASE), "").trim()
            if (noArticle.length >= 2) {
                candidateQueries.add(noArticle)
            }
            val noPart = cleanT.replace(Regex("""(?i)\s*[-–—:]*\s*\b(?:part|pt|volume|vol|cd)\s*[0-9a-z]+\b.*$"""), "").trim(' ', '-', '–', '—', ':', '|')
            if (noPart.length >= 2 && noPart != cleanT) {
                candidateQueries.add(noPart)
                val noPartNoArt = noPart.replace(Regex("""^(?:the|a|an)\s+""", RegexOption.IGNORE_CASE), "").trim()
                if (noPartNoArt.length >= 2) {
                    candidateQueries.add(noPartNoArt)
                }
            }
            val sub = cleanT.substringBefore(":").substringBefore("-").substringBefore("–").trim()
            if (sub.length >= 3 && sub != cleanT) {
                candidateQueries.add(sub)
                val subNoArt = sub.replace(Regex("""^(?:the|a|an)\s+""", RegexOption.IGNORE_CASE), "").trim()
                if (subNoArt.length >= 2) {
                    candidateQueries.add(subNoArt)
                }
            }
            candidateQueries.add(cleanT.replace(" ", ""))

            for (query in candidateQueries.filter { it.length >= 2 }.take(4)) {
                if (resolvedTwolekedeId != null && extractedDesc.isNotBlank() && !parser.isPlaceholderOrGenericDescription(extractedDesc)) break
                try {
                    val sHtml = kotlinx.coroutines.withTimeoutOrNull(6000) {
                        api.searchTwolekede(query).getOrNull().orEmpty()
                    }.orEmpty()
                    if (sHtml.isNotBlank()) {
                        val sCards = parser.parseMediaCards(sHtml)
                        val targetVj = cached?.vj?.lowercase()?.replace("vj", "")?.replace(".", "")?.trim().orEmpty()
                        val matchedCard = sCards.firstOrNull { c ->
                            val ct = parser.cleanTitle(c.title)
                            val titleMatches = parser.titlesMatch(ct, cleanT)
                            val cvj = c.vj.lowercase().replace("vj", "").replace(".", "").trim()
                            val vjMatches = targetVj.isBlank() || cvj.contains(targetVj) || targetVj.contains(cvj)
                            titleMatches && vjMatches
                        } ?: sCards.firstOrNull { c ->
                            val ct = parser.cleanTitle(c.title)
                            parser.titlesMatch(ct, cleanT)
                        }

                        if (matchedCard != null) {
                            val matchId = MediaCache.extractNumericId(matchedCard.streamUrl) ?: MediaCache.extractNumericId(matchedCard.id)
                            if (matchId != null) {
                                resolvedTwolekedeId = matchId
                                if (extractedDesc.isBlank() || parser.isPlaceholderOrGenericDescription(extractedDesc)) {
                                    val tkHtml = kotlinx.coroutines.withTimeoutOrNull(6000) {
                                        api.getWatchPageHtml("https://twolekede.com/moviedetails.php?id=$matchId").getOrDefault("")
                                    }.orEmpty()
                                    if (tkHtml.isNotBlank()) {
                                        val tkDesc = parser.extractMovieDescriptionFromWatchPage(tkHtml)
                                        if (tkDesc.isNotBlank() && !parser.isPlaceholderOrGenericDescription(tkDesc)) {
                                            extractedDesc = tkDesc
                                        }
                                    }
                                    if (extractedDesc.isBlank() && matchedCard.description.isNotBlank() && !parser.isPlaceholderOrGenericDescription(matchedCard.description)) {
                                        extractedDesc = matchedCard.description
                                    }
                                }
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val myVjVj = myVjDetailObj?.optString("vj")?.takeIf { it.isNotBlank() && it != "-" && it != "null" }
        val bestVj = cached?.vj?.takeIf { it.isNotBlank() && it != "-" }
            ?: myVjVj
            ?: (if (watchHtmlValid) org.jsoup.Jsoup.parse(watchHtml).selectFirst(".category-tag, .category-badge, .category, .item-info .category, .meta-info .category")?.text()?.trim() else null)
            ?: "VJ Translated"

        val description = if (extractedDesc.isNotBlank() && !parser.isPlaceholderOrGenericDescription(extractedDesc)) {
            extractedDesc
        } else if (!cached?.description.isNullOrBlank() && !parser.isPlaceholderOrGenericDescription(cached!!.description)) {
            cached.description
        } else {
            "${parser.cleanTitle(realTitle)} translated in Luganda by $bestVj."
        }

        val myVjPosterPath = myVjDetailObj?.optString("tmdb_poster_path")
        val myVjMunoImg = myVjDetailObj?.optString("img_port_muno_file_name")
        val myVjPoster = when {
            !myVjPosterPath.isNullOrBlank() && myVjPosterPath != "null" -> {
                val cleanP = if (myVjPosterPath.startsWith("/")) myVjPosterPath else "/$myVjPosterPath"
                "https://image.tmdb.org/t/p/w500$cleanP"
            }
            !myVjMunoImg.isNullOrBlank() && myVjMunoImg != "null" -> {
                if (myVjMunoImg.startsWith("http")) myVjMunoImg else "https://res-watch.b-cdn.net/1/$myVjMunoImg"
            }
            else -> null
        }

        val docPoster = if (watchHtmlValid) {
            org.jsoup.Jsoup.parse(watchHtml).selectFirst(".poster-section img, img.movie-poster, .movie-details img, img[src*='uploads/posters']")
                ?.attr("src")?.takeIf { it.isNotBlank() }?.let { parser.formatUrl(it) }
        } else null
        val bestPoster = cached?.posterUrl?.takeIf { it.isNotBlank() } ?: myVjPoster ?: docPoster ?: ""

        val myVjBackdropPath = myVjDetailObj?.optString("tmdb_backdrop_path")
        val myVjBackdrop = if (!myVjBackdropPath.isNullOrBlank() && myVjBackdropPath != "null") {
            val cleanB = if (myVjBackdropPath.startsWith("/")) myVjBackdropPath else "/$myVjBackdropPath"
            "https://image.tmdb.org/t/p/original$cleanB"
        } else bestPoster

        val myVjRelDate = myVjDetailObj?.optString("tmdb_release_date")?.ifBlank { myVjDetailObj.optString("vj_release_date") }
        val myVjYear = if (!myVjRelDate.isNullOrBlank()) Regex("""\b(19\d{2}|20\d{2})\b""").find(myVjRelDate)?.value else null

        val docYear = if (watchHtmlValid) {
            org.jsoup.Jsoup.parse(watchHtml).selectFirst(".year, .meta-info .year, span:matches(\\b20\\d{2}\\b)")
                ?.text()?.trim()?.takeIf { it.isNotBlank() }
        } else null
        val bestYear = cached?.year?.takeIf { it.isNotBlank() } ?: myVjYear ?: docYear ?: ""

        val myVjCategories = myVjDetailObj?.optString("categories")?.takeIf { it.isNotBlank() && it != "-" && it != "null" }
        val bestGenre = cached?.genre?.takeIf { it.isNotBlank() } ?: myVjCategories ?: ""

        val myVjDuration = myVjDetailObj?.optInt("duration", 0)?.takeIf { it > 0 }?.let { "${it}m" }
        val bestRuntime = cached?.runtime?.takeIf { it.isNotBlank() } ?: myVjDuration ?: ""

        val playLink = if (watchHtmlValid) {
            org.jsoup.Jsoup.parse(watchHtml).selectFirst("a[href*='preview.php'], a.preview-button, a[href*='download.php']")
                ?.attr("href")?.trim()
        } else null

        val rawStreamUrl = when {
            resolvedTwolekedeId != null -> "https://twolekede.com/preview.php?id=$resolvedTwolekedeId"
            watchHtmlValid && !playLink.isNullOrBlank() && playLink.startsWith("http") -> playLink
            watchHtmlValid && !playLink.isNullOrBlank() -> "https://twolekede.com/${playLink.removePrefix("/")}"
            isExplicitTwolekede && numericId != null && (numericId.toIntOrNull() ?: 0) > 0 -> "https://twolekede.com/preview.php?id=$numericId"
            else -> cached?.streamUrl ?: streamPath ?: ""
        }
        val effectiveStreamUrl = if (rawStreamUrl == "/twolekede" || rawStreamUrl == "twolekede") {
            if (resolvedTwolekedeId != null) "https://twolekede.com/preview.php?id=$resolvedTwolekedeId"
            else if (isExplicitTwolekede && numericId != null && (numericId.toIntOrNull() ?: 0) > 0) "https://twolekede.com/preview.php?id=$numericId"
            else cached?.streamUrl ?: ""
        } else rawStreamUrl

        val updated = (cached ?: MediaItem(id = id, title = realTitle, isSeries = isSeries)).copy(
            title = parser.cleanTitle(realTitle),
            description = description,
            posterUrl = bestPoster,
            backdropUrl = myVjBackdrop.takeIf { it.isNotBlank() } ?: bestPoster,
            vj = bestVj,
            year = bestYear,
            genre = bestGenre.takeIf { it.isNotBlank() } ?: cached?.genre ?: "",
            runtime = bestRuntime.takeIf { it.isNotBlank() } ?: cached?.runtime ?: "",
            streamUrl = effectiveStreamUrl,
            isSeries = isSeries,
            quality = "1080p Full HD"
        )
        if (effectiveStreamUrl.isNotBlank()) {
            MediaCache.putStreamPath(id, effectiveStreamUrl)
            if (id.startsWith("myvj_")) {
                MediaCache.putStreamPath(id.removePrefix("myvj_"), effectiveStreamUrl)
            } else {
                MediaCache.putStreamPath("myvj_$id", effectiveStreamUrl)
            }
        }
        MediaCache.put(updated)
        if (id.startsWith("myvj_")) {
            MediaCache.put(updated.copy(id = id.removePrefix("myvj_")))
        } else {
            MediaCache.put(updated.copy(id = "myvj_$id"))
        }
        if (!parser.isPlaceholderOrGenericDescription(description)) {
            MediaCache.putMovieDetail(updated)
            if (id.startsWith("myvj_")) {
                MediaCache.putMovieDetail(updated.copy(id = id.removePrefix("myvj_")))
            } else {
                MediaCache.putMovieDetail(updated.copy(id = "myvj_$id"))
            }
        }
        return Result.success(updated)
    }
}

class SeriesRepositoryImpl(
    private val api: MunowatchApi,
    private val parser: MunowatchHtmlParser
) : SeriesRepository {

    override suspend fun getPopularSeries(lastFetchId: String?, page: Int?): Result<List<MediaItem>> {
        val allSeries = mutableListOf<MediaItem>()

        val targetPage = page ?: 1
        // 1. Fetch live series catalog from Twolekede (which contains complete series with all seasons)
        try {
            val twolekedeRes = api.getTwolekedeSeriesCatalog(page = targetPage)
            if (twolekedeRes.isSuccess) {
                val twolekedeItems = parser.parseMediaCards(twolekedeRes.getOrDefault(""))
                allSeries.addAll(twolekedeItems.map { it.copy(isSeries = true) })
            }
        } catch (_: Exception) {}

        // 2. Fetch Munowatch series grid slot as well
        val cleanFetch = cleanFetchId(lastFetchId)
        val res = api.getGridSlot(pipeType = "g", pipeId = "5", lastFetchId = cleanFetch)
        var parsed = res.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
        if (parsed.isEmpty()) {
            val fallbackRes = api.getGridSlot(pipeType = "genre_vj", pipeId = "5", lastFetchId = cleanFetch)
            val fallbackParsed = fallbackRes.getOrNull()?.let { parser.parseMediaCards(it) } ?: emptyList()
            if (fallbackParsed.isNotEmpty()) {
                parsed = fallbackParsed
            }
        }
        allSeries.addAll(parsed.map { it.copy(isSeries = true) })

        if (allSeries.isEmpty() && lastFetchId == null && (page == null || page == 1)) {
            val cached = MediaCache.getPopularSeries()
            if (cached.isNotEmpty()) {
                return Result.success(cached)
            }
        }

        val distinctSeries = allSeries.distinctBy { it.id.ifBlank { it.title.lowercase() } }
        MediaCache.putAll(distinctSeries)
        if (distinctSeries.isNotEmpty()) {
            MediaCache.setPopularSeries(distinctSeries)
        }
        return Result.success(distinctSeries)
    }

    override suspend fun getFilteredSeries(
        year: String?,
        category: String?,
        vj: String?,
        page: Int,
        perPage: Int
    ): Result<List<MediaItem>> {
        val hasVj = !vj.isNullOrBlank() && !vj.equals("All VJs", ignoreCase = true)
        val hasCategory = !category.isNullOrBlank() && !category.equals("All Categories", ignoreCase = true) && !category.equals("All Genres", ignoreCase = true)

        val seriesList = mutableListOf<MediaItem>()

        var twolekedeHandledVj = false
        // 1. If VJ is specified, fetch from Twolekede category catalog (which hosts authentic full series for that VJ)
        if (hasVj) {
            val effectiveVj = vj!!.trim()
            val twolekedeVj = if (effectiveVj.startsWith("VJ ", ignoreCase = true)) effectiveVj else "VJ $effectiveVj"
            val tkRes = api.getTwolekedeCategoryCatalog(twolekedeVj, page = 1)
            if (tkRes.isSuccess) {
                val tkCards = parser.parseMediaCards(tkRes.getOrDefault(""))
                val tkSeries = tkCards.filter {
                    (it.isSeries || it.streamUrl.contains("seriesdetails.php")) &&
                    !MunowatchApi.isSingleMovieMisclassifiedAsSeries(it.title)
                }
                if (tkSeries.isNotEmpty()) {
                    twolekedeHandledVj = true
                    val filteredSeries = if (hasCategory) {
                        tkSeries.filter { it.genre.contains(category!!, ignoreCase = true) || it.title.contains(category!!, ignoreCase = true) }
                    } else {
                        tkSeries
                    }
                    val distinctSeries = filteredSeries.distinctBy { it.id.ifBlank { it.title.lowercase() } }
                    val paged = distinctSeries.drop((page - 1) * perPage).take(perPage)
                    seriesList.addAll(paged.map { it.copy(isSeries = true, vj = twolekedeVj) })
                }
            }
        }

        // 2. If Category/Genre is specified without VJ, fetch series by genre
        if (seriesList.isEmpty() && hasCategory && !twolekedeHandledVj) {
            val catRes = api.getTwolekedeGenreCatalog(category!!, page = page)
            if (catRes.isSuccess) {
                val catCards = parser.parseMediaCards(catRes.getOrDefault(""))
                val catSeries = catCards.filter {
                    (it.isSeries || it.streamUrl.contains("seriesdetails.php")) &&
                    !MunowatchApi.isSingleMovieMisclassifiedAsSeries(it.title)
                }
                seriesList.addAll(catSeries.map { it.copy(isSeries = true) })
            }
        }

        // 3. Fallback / supplement with ugawatch filtered catalog (only if Twolekede did not handle this VJ)
        if (seriesList.isEmpty() && !twolekedeHandledVj) {
            val catSlug = if (hasCategory) toCategorySlug(category!!) else null
            val vjSlug = if (hasVj) toVjSlug(vj!!) else null
            val cleanYear = year?.takeIf { it.isNotBlank() }

            val apiRes = api.getFilteredCatalog(
                year = cleanYear,
                categorySlug = catSlug,
                vjSlug = vjSlug,
                mediaType = 2,
                page = page,
                perPage = perPage
            )
            if (apiRes.isSuccess) {
                val items = apiRes.getOrDefault(emptyList())
                    .filter { !MunowatchApi.isSingleMovieMisclassifiedAsSeries(it.title) }
                    .map { it.copy(isSeries = true) }
                if (items.isNotEmpty()) {
                    seriesList.addAll(items)
                }
            }
        }

        // 4. Fallback if neither VJ nor category, or other endpoints returned empty: direct live series catalog
        if (seriesList.isEmpty() && !hasVj && !hasCategory) {
            val tkRes = api.getTwolekedeSeriesCatalog(page = page)
            if (tkRes.isSuccess) {
                val tkCards = parser.parseMediaCards(tkRes.getOrDefault(""))
                seriesList.addAll(tkCards.map { it.copy(isSeries = true) })
            }
        }

        val distinct = seriesList.distinctBy { it.id.ifBlank { it.title.lowercase() } }
        if (distinct.isNotEmpty()) {
            MediaCache.putAll(distinct)
            return Result.success(distinct)
        }
        return Result.success(emptyList())
    }

    private fun extractBaseSeriesName(title: String): String {
        var name = title
        name = name.replace(Regex("""\[.*?\]"""), " ")
        name = name.replace(Regex("""\(.*?\)|\[.*?\]"""), " ")
        name = name.replace(Regex("""(?i)\b(?:Season|Seasons|Series|S)\s*[0-9]+.*$"""), " ")
        name = name.replace(Regex("""(?i)\b(?:Episode|EPS|Ep|E)\s*[0-9]+.*$"""), " ")
        name = name.replace(Regex("""(?i)\b(?:Part|Pt)\s*[0-9]+.*$"""), " ")
        name = name.replace(Regex("""(?i)\s+by\s+vj\s+.*$"""), " ")
        name = name.replace(Regex("""(?i)\s+vj\s+[a-z0-9\-_]+.*$"""), " ")
        name = name.replace(Regex("""(?i)\b(?:luganda|translated|hd|full\s*hd)\b"""), " ")
        return name.replace(Regex("""\s+"""), " ").trim(' ', '-', '–', '—', ':', '|', '.')
    }

    private fun isMatchingSeriesItem(itemTitle: String, seriesTitle: String, baseName: String): Boolean {
        val cleanItem = itemTitle.lowercase().trim()
        val cleanSeries = seriesTitle.lowercase().trim()
        val cleanBase = baseName.lowercase().trim()

        if (cleanItem.isBlank() || cleanBase.isBlank() || cleanBase.length < 2) return false

        // Discard system navigation and player control words
        val invalidWords = setOf(
            "skip_next", "skip_previous", "play_arrow", "pause", "fast_forward", "fast_rewind",
            "latest movies", "movies", "series", "watchlist", "settings", "my settings", "logout", "login",
            "register", "home", "browse", "categories", "genres", "search", "all movies"
        )
        if (cleanItem in invalidWords || invalidWords.any { cleanItem.startsWith("$it ") || cleanItem.endsWith(" $it") }) {
            return false
        }

        // Must contain the base series name as a word or distinct phrase
        val basePattern = Regex("""\b${Regex.escape(cleanBase)}\b""", RegexOption.IGNORE_CASE)
        if (!basePattern.containsMatchIn(cleanItem) && !cleanItem.startsWith(cleanBase)) {
            return false
        }

        // Check if item has explicit episode or season indicators to avoid matching standalone movies
        val hasEpisodeIndicator = Regex(
            """\b(?:season\s*\d+|seasons\s*\d+|s\d+\s*e\d+|episode\s*\d+|eps\s*\d+|ep\s*\d+|\be\d+\b|part\s*\d+)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(cleanItem) || cleanItem.contains("episode", ignoreCase = true) || cleanItem.contains("ep.", ignoreCase = true)
        if (!hasEpisodeIndicator) {
            return false
        }

        // Discard spin-offs or different series with extra subtitles
        val prefixMatch = Regex("""(?i)^(.+?)\s*[-_:]?\s*(?:Season\s*\d+|S\d+\s*E\d+|Episode\s*\d+|EPS\s*\d+|Ep\s*\d+|\bE\d+\b)""").find(cleanItem)
        if (prefixMatch != null) {
            val showPrefix = prefixMatch.groupValues[1]
                .replace(Regex("""\[.*?\]|\(.*?\)|(?i)\bby\s+vj\s+.*$"""), " ")
                .replace(Regex("""(?i)\bby\s+.*$"""), " ")
                .replace(Regex("""(?i)\bvj\s+[a-z0-9\-_]+.*$"""), " ")
                .trim(' ', '-', '–', '—', ':')
            val cleanPrefix = parser.cleanTitle(showPrefix)
                .replace(Regex("""(?i)\bvj\s+[a-z0-9\-_]+.*$"""), " ")
                .lowercase().trim()
            if (cleanPrefix.isNotBlank()) {
                val matchesBase = cleanPrefix == cleanBase ||
                        cleanPrefix.startsWith("$cleanBase ") ||
                        cleanPrefix.startsWith("$cleanBase:") ||
                        cleanPrefix.startsWith("$cleanBase-") ||
                        cleanBase.startsWith("$cleanPrefix ") ||
                        cleanBase.startsWith("$cleanPrefix:") ||
                        cleanBase.startsWith("$cleanPrefix-")
                if (!matchesBase) {
                    return false // Different show! E.g. "Doctor Who" when series is "The Good Doctor"
                }

                // Check for subtitles / spin-offs (e.g. "Money Heist: Korea" vs "Money Heist")
                val diff = cleanPrefix.replace(cleanBase, "").trim(' ', '-', '–', '—', ':', '|')
                val diffWords = diff.split(Regex("""\s+""")).filter { w ->
                    w.isNotBlank() && !w.matches(Regex("""(?i)^(?:season|s|vj|part|pt|eps|ep|luganda|translated|\d+)$"""))
                }
                if (diffWords.isNotEmpty()) {
                    return false // Spin-off or different show!
                }
            }
        } else {
            // If no season/episode prefix was found at start, item MUST start with the base series name
            if (!cleanItem.startsWith(cleanBase)) {
                return false
            }
        }

        return true
    }

    override suspend fun getSeriesDetails(id: String): Result<SeriesDetail> {
        val cachedDetail = MediaCache.getSeriesDetail(id)
        if (cachedDetail != null &&
            (cachedDetail.id.equals(id, ignoreCase = true) || cachedDetail.id.removePrefix("series_").equals(id.removePrefix("series_"), ignoreCase = true)) &&
            cachedDetail.seasons.sumOf { it.episodes.size } > 1
        ) {
            return Result.success(cachedDetail)
        }

        val cached = MediaCache.get(id)
        val effectiveTarget = cached?.streamUrl?.takeIf { it.isNotBlank() && it.contains("seriesdetails.php") }
            ?: run {
                val title = cached?.title?.takeIf { it.isNotBlank() } ?: id.removePrefix("series_").replace("_", " ")
                val baseTitle = parser.extractBaseSeriesName(title)
                val vj = cached?.vj?.takeIf { it.isNotBlank() && it != "VJ Translated" } ?: "VJ Junior"
                val encTitle = java.net.URLEncoder.encode(if (baseTitle.length >= 2) baseTitle else title, "UTF-8")
                val encVj = java.net.URLEncoder.encode(vj, "UTF-8")
                "https://twolekede.com/seriesdetails.php?title=$encTitle&category=$encVj"
            }
        val tkRes = api.getTwolekedeSeriesDetails(effectiveTarget, cached?.vj)
        val html = if (tkRes.isSuccess && tkRes.getOrDefault("").isNotBlank()) {
            tkRes.getOrDefault("")
        } else {
            api.getSeriesList(id).getOrDefault("")
        }

        var combinedHtml = html
        val hasMediaServerEpisodes = tkRes.isSuccess && (html.contains("season-episodes-wrapper") || html.contains("episode-card"))
        if (!hasMediaServerEpisodes) {
            try {
                val doc = org.jsoup.Jsoup.parse(html)
                val soulInfo = doc.selectFirst("#soul-info")
                val vidid = soulInfo?.attr("data-vidid")?.takeIf { it.isNotBlank() }
                    ?: doc.selectFirst("[data-vidid]")?.attr("data-vidid")?.takeIf { it.isNotBlank() }
                    ?: Regex("""[?&]mid=([0-9]+)""").find(html)?.groupValues?.get(1)
                    ?: doc.selectFirst("#list-container a[href*='mid='], .episode-box a[href*='mid='], a[href*='mid=']")?.let {
                        Regex("""[?&]mid=([0-9]+)""").find(it.attr("href"))?.groupValues?.get(1)
                    }
                    ?: id.removePrefix("series_").takeIf { it.all { c -> c.isDigit() } }

                val foundRanges = mutableListOf<String>()
                val rangeRegex = Regex("""(?:data-range|epsRange|range)=["']?([0-9]+\s*-\s*[0-9]+)["']?""", RegexOption.IGNORE_CASE)
                rangeRegex.findAll(html).forEach { match ->
                    val r = match.groupValues[1].replace(" ", "")
                    if (!foundRanges.contains(r)) {
                        foundRanges.add(r)
                    }
                }

                // Also check text inside buttons/anchors/elements for range patterns like "1 - 20", "21 - 40"
                val textRangeRegex = Regex("""\b([0-9]+\s*-\s*[0-9]+)\b""")
                doc.select("button, a, span, .range, .ep-range, [class*='range']").forEach { el ->
                    val text = el.text().trim()
                    val m = textRangeRegex.find(text)
                    if (m != null) {
                        val r = m.groupValues[1].replace(" ", "")
                        if (!foundRanges.contains(r)) {
                            foundRanges.add(r)
                        }
                    }
                }

                // Always ensure the primary 1-20 episode batch is attempted if a video ID is available
                if (!vidid.isNullOrBlank() && !foundRanges.contains("1-20")) {
                    foundRanges.add(0, "1-20")
                }

                // Check if series has a series_code for getEpisodeRanges
                val seriesCode = doc.selectFirst("[data-series], [data-series-code], [data-seriescode]")?.let {
                    it.attr("data-series").ifBlank { it.attr("data-series-code") }.ifBlank { it.attr("data-seriescode") }
                } ?: Regex("""series_code=([a-zA-Z0-9_\-]+)""").find(html)?.groupValues?.get(1)

                if (!vidid.isNullOrBlank() && !seriesCode.isNullOrBlank()) {
                    val rangesRes = api.getEpisodeRanges(videoId = vidid, seriesCode = seriesCode)
                    val rangesHtml = rangesRes.getOrDefault("")
                    if (rangesHtml.isNotBlank()) {
                        rangeRegex.findAll(rangesHtml).forEach { match ->
                            val r = match.groupValues[1].replace(" ", "")
                            if (!foundRanges.contains(r)) {
                                foundRanges.add(r)
                            }
                        }
                        textRangeRegex.findAll(rangesHtml).forEach { match ->
                            val r = match.groupValues[1].replace(" ", "")
                            if (!foundRanges.contains(r)) {
                                foundRanges.add(r)
                            }
                        }
                    }
                }

                if (!vidid.isNullOrBlank()) {
                    val rangesToTry = mutableListOf<String>()
                    rangesToTry.addAll(foundRanges)
                    if (!rangesToTry.contains("1-20")) {
                        rangesToTry.add(0, "1-20")
                    }
                    val sequentialBatches = listOf("1-20", "21-40", "41-60", "61-80", "81-100", "101-120")
                    for (b in sequentialBatches) {
                        if (!rangesToTry.contains(b)) {
                            rangesToTry.add(b)
                        }
                    }

                    for (r in rangesToTry) {
                        val moreRes = api.getMoreEpisodes(videoId = vidid, range = r)
                        val moreHtml = moreRes.getOrDefault("")
                        if (moreHtml.isNotBlank() && (moreHtml.contains("twolekede") || moreHtml.contains("mid="))) {
                            combinedHtml = "$combinedHtml\n<div id=\"more-episodes-container-$r\">$moreHtml</div>"
                        } else if (r !in foundRanges && r != "1-20") {
                            // Stop sequential probing once a batch past the initial ranges yields no episodes
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val rawSeriesTitle = if (!cached?.title.isNullOrBlank() && !cached.title.startsWith("Munowatch", ignoreCase = true) && !cached.title.equals("Series", ignoreCase = true)) {
            cached.title
        } else {
            parser.extractMovieTitleFromWatchPage(combinedHtml, "")?.takeIf {
                !it.startsWith("Munowatch", ignoreCase = true) && !it.equals("Series", ignoreCase = true)
            } ?: cached?.title ?: "Munowatch Series"
        }

        val baseName = parser.extractBaseSeriesName(rawSeriesTitle)
        val finalTitle = if (baseName.length >= 2) baseName else rawSeriesTitle

        val cachedPoster = cached?.posterUrl?.takeIf { parser.isValidPosterUrl(it) }
        var detail = parser.parseSeriesEpisodes(id, combinedHtml, knownSeriesTitle = finalTitle, knownPosterUrl = cachedPoster)
        var finalPoster = cachedPoster ?: detail.posterUrl.takeIf { parser.isValidPosterUrl(it) } ?: ""
        val finalBackdrop = if (!cached?.backdropUrl.isNullOrBlank()) cached.backdropUrl else (detail.backdropUrl.takeIf { it.isNotBlank() } ?: finalPoster)
        val finalVj = if (!cached?.vj.isNullOrBlank() && cached.vj != "VJ Translated") cached.vj else (detail.vj.takeIf { it.isNotBlank() } ?: "VJ Translated")
        val finalYear = if (!cached?.year.isNullOrBlank()) cached.year else detail.year
        val finalGenres = if (cached?.genre?.isNotBlank() == true) listOf(cached.genre) else (detail.genres.takeIf { it.isNotEmpty() } ?: listOf("Ugandan Translated", "Series"))

        // ALWAYS check MediaCache first for all matching episodes already cached or found during search
        val cachedMatches = MediaCache.getAll().filter { item ->
            isMatchingSeriesItem(item.title, finalTitle, baseName) &&
            item.streamUrl.isNotBlank() &&
            !item.streamUrl.contains("moviedetails.php") &&
            (item.streamUrl.contains("twolekede") || item.streamUrl.contains("mid=") || item.streamUrl.contains("preview.php") || item.streamUrl.contains("download.php"))
        }
        if (cachedMatches.isNotEmpty()) {
            val cachedHtml = buildString {
                append("<div id=\"cached-series-episodes\">\n")
                for (item in cachedMatches) {
                    append("<a href=\"${item.streamUrl}\" title=\"${item.title}\">")
                    if (item.posterUrl.isNotBlank() && parser.isValidPosterUrl(item.posterUrl)) {
                        append("<img src=\"${item.posterUrl}\" alt=\"${item.title}\" />")
                    }
                    append("<span>${item.title}</span></a>\n")
                }
                append("</div>\n")
            }
            combinedHtml = "$combinedHtml\n$cachedHtml"
            detail = parser.parseSeriesEpisodes(id, combinedHtml, knownSeriesTitle = finalTitle, knownPosterUrl = cachedPoster)
        }

        // If only 1 episode was detected, search Twolekede and Munowatch to find all episodes and seasons
        val totalEpisodesFound = detail.seasons.sumOf { it.episodes.size }
        val genericWords = setOf("the", "movie", "movies", "series", "season", "episode", "part", "twolekede", "munowatch", "uganda", "ugandan", "vj")
        if (totalEpisodesFound <= 1 && finalTitle.isNotBlank() && baseName.length >= 2 && baseName.lowercase() !in genericWords) {
            // 1. Try canonical Twolekede series details URL directly
            try {
                val encBase = java.net.URLEncoder.encode(baseName, "UTF-8")
                val encVj = java.net.URLEncoder.encode(finalVj.ifBlank { "VJ Junior" }, "UTF-8")
                val canonicalTkUrl = "https://twolekede.com/seriesdetails.php?title=$encBase&category=$encVj"
                val detailRes = api.getTwolekedeSeriesDetails(canonicalTkUrl, finalVj).getOrNull()
                if (!detailRes.isNullOrBlank()) {
                    combinedHtml = "$combinedHtml\n<div id=\"twolekede-fallback-episodes\">\n$detailRes\n</div>"
                    detail = parser.parseSeriesEpisodes(id, combinedHtml, knownSeriesTitle = finalTitle, knownPosterUrl = cachedPoster)
                }
            } catch (_: Exception) {}

            val updatedEpisodesCount1 = detail.seasons.sumOf { it.episodes.size }
            if (updatedEpisodesCount1 <= 1) {
                // 2. Search Twolekede for this series to get the full series page with all seasons & episodes
                try {
                    val twolekedeSearchRes = api.searchTwolekede(baseName).getOrNull()
                    if (!twolekedeSearchRes.isNullOrBlank()) {
                        val sDoc = org.jsoup.Jsoup.parse(twolekedeSearchRes)
                        val matchingLink = sDoc.select("a[href*='seriesdetails.php']").firstOrNull { a ->
                            val linkTitle = (a.selectFirst("h3, h4, .title, .content-title")?.text() ?: a.attr("title").ifBlank { a.text() }).trim()
                            linkTitle.isNotBlank() && (parser.titlesMatch(linkTitle, baseName) || parser.titlesMatch(linkTitle, finalTitle))
                        }
                        if (matchingLink != null) {
                            val detailRes = api.getTwolekedeSeriesDetails(matchingLink.attr("href"), finalVj).getOrNull()
                            if (!detailRes.isNullOrBlank()) {
                                combinedHtml = "$combinedHtml\n<div id=\"twolekede-fallback-episodes\">\n$detailRes\n</div>"
                                detail = parser.parseSeriesEpisodes(id, combinedHtml, knownSeriesTitle = finalTitle, knownPosterUrl = cachedPoster)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val updatedEpisodesCount = detail.seasons.sumOf { it.episodes.size }
            if (updatedEpisodesCount <= 1) {
                val discoveredItems = mutableListOf<com.example.domain.model.MediaItem>()

                // 2. Check MediaCache first for all matching episodes already cached
                val cachedMatches2 = MediaCache.getAll().filter { item ->
                    isMatchingSeriesItem(item.title, finalTitle, baseName) &&
                    item.streamUrl.isNotBlank() &&
                    !item.streamUrl.contains("moviedetails.php") &&
                    (item.streamUrl.contains("twolekede") || item.streamUrl.contains("mid=") || item.streamUrl.contains("preview.php") || item.streamUrl.contains("download.php"))
                }
                discoveredItems.addAll(cachedMatches2)

                // 3. Perform Munowatch search using the clean base series name
                try {
                    val searchResult = api.search(baseName).getOrNull()
                    if (!searchResult.isNullOrBlank()) {
                        val searchCards = parser.parseMediaCards(searchResult)
                        val matchingCards = searchCards.filter { card ->
                            isMatchingSeriesItem(card.title, finalTitle, baseName) &&
                            card.streamUrl.isNotBlank() &&
                            !card.streamUrl.contains("moviedetails.php") &&
                            (card.streamUrl.contains("twolekede") || card.streamUrl.contains("mid=") || card.streamUrl.contains("preview.php") || card.streamUrl.contains("download.php"))
                        }
                        for (card in matchingCards) {
                            if (discoveredItems.none { it.streamUrl == card.streamUrl }) {
                                discoveredItems.add(card)
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (discoveredItems.isNotEmpty()) {
                    val searchHtml = buildString {
                        append("<div id=\"series-search-episodes\">\n")
                        for (item in discoveredItems) {
                            append("<a href=\"${item.streamUrl}\" title=\"${item.title}\">")
                            if (item.posterUrl.isNotBlank()) {
                                append("<img src=\"${item.posterUrl}\" alt=\"${item.title}\" />")
                            }
                            append("<span>${item.title}</span></a>\n")
                        }
                        append("</div>\n")
                    }
                    combinedHtml = "$combinedHtml\n$searchHtml"
                    detail = parser.parseSeriesEpisodes(id, combinedHtml, knownSeriesTitle = finalTitle, knownPosterUrl = cachedPoster)
                    if (finalPoster.isBlank() || !parser.isValidPosterUrl(finalPoster)) {
                        discoveredItems.firstOrNull { it.posterUrl.isNotBlank() && parser.isValidPosterUrl(it.posterUrl) }?.let {
                            finalPoster = it.posterUrl
                        }
                    }
                }
            }
        }

        // Detect and recover any missing episode gaps across all seasons
        if (baseName.length >= 2 && baseName.lowercase() !in genericWords) {
            var foundMissingEpisode = false
            for (season in detail.seasons) {
                val nums = season.episodes.map { it.number }.sorted()
                if (nums.size >= 2) {
                    val min = nums.first()
                    val max = nums.last()
                    val missing = (min..max).filter { it !in nums }
                    if (missing.isNotEmpty()) {
                        for (miss in missing.take(3)) {
                            try {
                                val q = "$baseName S${season.number} E$miss"
                                val tkRes = api.searchTwolekede(q).getOrNull()
                                if (!tkRes.isNullOrBlank() && (tkRes.contains("preview.php") || tkRes.contains("download.php") || tkRes.contains("mid="))) {
                                    combinedHtml = "$combinedHtml\n<div class=\"season-episodes-wrapper\" data-season=\"${season.number}\">\n$tkRes\n</div>"
                                    foundMissingEpisode = true
                                } else {
                                    val q2 = "$baseName Episode $miss"
                                    val mRes = api.search(q2).getOrNull()
                                    if (!mRes.isNullOrBlank()) {
                                        val cards = parser.parseMediaCards(mRes)
                                        val match = cards.filter { isMatchingSeriesItem(it.title, finalTitle, baseName) }
                                        if (match.isNotEmpty()) {
                                            val sHtml = buildString {
                                                append("<div class=\"season-episodes-wrapper\" data-season=\"${season.number}\">\n")
                                                for (m in match) {
                                                    append("<a href=\"${m.streamUrl}\" title=\"${m.title}\"><span>${m.title}</span></a>\n")
                                                }
                                                append("</div>\n")
                                            }
                                            combinedHtml = "$combinedHtml\n$sHtml"
                                            foundMissingEpisode = true
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
            if (foundMissingEpisode) {
                detail = parser.parseSeriesEpisodes(id, combinedHtml, knownSeriesTitle = finalTitle, knownPosterUrl = cachedPoster)
            }
        }

        // If poster is still blank, extract from combinedHtml or first episode watch page
        if (finalPoster.isBlank() || !parser.isValidPosterUrl(finalPoster)) {
            finalPoster = parser.extractMoviePosterFromWatchPage(combinedHtml)?.takeIf { parser.isValidPosterUrl(it) }
                ?: detail.posterUrl.takeIf { parser.isValidPosterUrl(it) } ?: ""
        }
        if (finalPoster.isBlank() || !parser.isValidPosterUrl(finalPoster)) {
            val firstEpStream = detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.streamUrl
            if (!firstEpStream.isNullOrBlank()) {
                try {
                    val epHtml = api.getWatchPageHtml(firstEpStream).getOrDefault("")
                    if (epHtml.isNotBlank()) {
                        finalPoster = parser.extractMoviePosterFromWatchPage(epHtml)?.takeIf { parser.isValidPosterUrl(it) } ?: ""
                    }
                } catch (_: Exception) {}
            }
        }
        if (finalPoster.isBlank() || !parser.isValidPosterUrl(finalPoster)) {
            val matchWithPoster = MediaCache.getAll().firstOrNull {
                it.posterUrl.isNotBlank() && parser.isValidPosterUrl(it.posterUrl) && !it.posterUrl.contains("1790800093") && isMatchingSeriesItem(it.title, finalTitle, baseName)
            }
            if (matchWithPoster != null) {
                finalPoster = matchWithPoster.posterUrl
            }
        }
        if (finalPoster.contains("1790800093") && !finalTitle.contains("runner", ignoreCase = true)) {
            finalPoster = cached?.posterUrl?.takeIf { !it.contains("1790800093") } ?: ""
        }

        var candidateDescription = if (detail.description.isNotBlank()) {
            detail.description
        } else {
            cached?.description ?: ""
        }

        // If description is still blank, try fetching official MyVJ series details
        if (candidateDescription.isBlank() || parser.isPlaceholderOrGenericDescription(candidateDescription)) {
            val itemSlug = MediaCache.getSlug(id)
                ?: (if (id.startsWith("myvj_")) id.removePrefix("myvj_").substringAfter("_").takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } } else null)
                ?: parser.cleanTitle(finalTitle).lowercase().replace(" ", "-").replace(Regex("""[^a-z0-9\-]"""), "").trim('-').takeIf { it.length >= 2 }
            if (itemSlug != null && itemSlug.isNotBlank()) {
                try {
                    val sObj = api.getMyVjSeriesDetail(itemSlug).getOrNull()
                    val sAbout = sObj?.optString("about")?.ifBlank { sObj.optString("overview") }
                    if (!sAbout.isNullOrBlank() && !parser.isPlaceholderOrGenericDescription(sAbout)) {
                        candidateDescription = sAbout
                    }
                } catch (_: Exception) {}
            }
        }

        // If description is still blank, extract from combinedHtml
        if (candidateDescription.isBlank()) {
            candidateDescription = parser.extractMovieDescriptionFromWatchPage(combinedHtml)
        }

        // If description is still blank, try fetching first episode's watch page to extract series description
        if (candidateDescription.isBlank()) {
            val firstStream = detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.streamUrl
            if (!firstStream.isNullOrBlank()) {
                try {
                    val epHtml = api.getWatchPageHtml(firstStream).getOrDefault("")
                    if (epHtml.isNotBlank()) {
                        val epDesc = parser.extractMovieDescriptionFromWatchPage(epHtml)
                        if (epDesc.isNotBlank()) {
                            candidateDescription = epDesc
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Check if any matching item in MediaCache has a description
        if (candidateDescription.isBlank()) {
            val matchWithDesc = MediaCache.getAll().firstOrNull {
                it.description.isNotBlank() && isMatchingSeriesItem(it.title, finalTitle, baseName)
            }
            if (matchWithDesc != null) {
                candidateDescription = matchWithDesc.description
            }
        }

        if (candidateDescription.contains("former soldier", ignoreCase = true) && !finalTitle.contains("runner", ignoreCase = true)) {
            candidateDescription = ""
        }

        val finalDescription = if (candidateDescription.isNotBlank()) {
            candidateDescription
        } else {
            "Watch $finalTitle translated in Luganda by top Veejays (${finalVj.ifBlank { "VJ Junior" }}). Stream all full HD episodes exclusively on Munowatch TV."
        }

        val updatedSeasons = detail.seasons.map { season ->
            season.copy(
                episodes = season.episodes.map { ep ->
                    val epThumb = if (ep.thumbnail.isNotBlank() && !ep.thumbnail.contains("logo") && !ep.thumbnail.contains("avatar")) {
                        ep.thumbnail
                    } else if (finalPoster.isNotBlank()) {
                        finalPoster
                    } else {
                        ep.thumbnail
                    }
                    ep.copy(thumbnail = epThumb)
                }
            )
        }

        val finalDetail = detail.copy(
            title = finalTitle,
            description = finalDescription,
            posterUrl = finalPoster,
            backdropUrl = finalBackdrop,
            vj = finalVj,
            year = finalYear,
            genres = finalGenres,
            seasons = updatedSeasons
        )
        finalDetail.seasons.forEach { season ->
            season.episodes.forEach { ep ->
                MediaCache.registerEpisode(ep, finalDetail.posterUrl, finalDetail.title)
            }
        }
        if (finalDetail.seasons.any { it.episodes.isNotEmpty() }) {
            MediaCache.putSeriesDetail(finalDetail)
        }
        return Result.success(finalDetail)
    }
}

class SearchRepositoryImpl(
    private val api: MunowatchApi,
    private val parser: MunowatchHtmlParser
) : SearchRepository {

    override suspend fun searchContent(query: String): Result<List<MediaItem>> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return Result.success(emptyList())

        val combinedResults = mutableListOf<MediaItem>()
        val seenIds = mutableSetOf<String>()

        val detectedQueryYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(cleanQuery)?.value
        val isYearQuery = detectedQueryYear != null
        // 1. Search Twolekede catalog (provides movies & complete series with seasons)
        val qTokens = cleanQuery.lowercase().split(Regex("""\s+""")).filter { it.length >= 2 }
        try {
            val twolekedeHtml = api.searchTwolekede(cleanQuery).getOrNull()
            if (!twolekedeHtml.isNullOrBlank()) {
                val parsedTwolekede = parser.parseMediaCards(twolekedeHtml)
                for (item in parsedTwolekede) {
                    val titleLower = item.title.lowercase()
                    val matchesQuery = isYearQuery || qTokens.isEmpty() || qTokens.any { titleLower.contains(it) } ||
                        item.vj.lowercase().contains(cleanQuery.lowercase()) ||
                        item.genre.lowercase().contains(cleanQuery.lowercase()) ||
                        item.year.contains(cleanQuery)
                    if (matchesQuery && seenIds.add(item.id)) {
                        val finalItem = if (isYearQuery && item.year.isBlank()) item.copy(year = detectedQueryYear) else item
                        combinedResults.add(finalItem)
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Search Munowatch catalog
        try {
            val munowatchHtml = api.search(cleanQuery).getOrNull()
            if (!munowatchHtml.isNullOrBlank()) {
                val parsedMuno = parser.parseMediaCards(munowatchHtml)
                for (item in parsedMuno) {
                    val titleLower = item.title.lowercase()
                    val matchesQuery = isYearQuery || qTokens.isEmpty() || qTokens.any { titleLower.contains(it) } ||
                        item.vj.lowercase().contains(cleanQuery.lowercase()) ||
                        item.genre.lowercase().contains(cleanQuery.lowercase()) ||
                        item.year.contains(cleanQuery)
                    if (matchesQuery && seenIds.add(item.id)) {
                        val finalItem = if (isYearQuery && item.year.isBlank()) item.copy(year = detectedQueryYear) else item
                        combinedResults.add(finalItem)
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Search local MediaCache for any matching items
        val cachedMatches = MediaCache.getAll().filter { item ->
            item.title.contains(cleanQuery, ignoreCase = true) ||
            item.genre.contains(cleanQuery, ignoreCase = true) ||
            item.vj.contains(cleanQuery, ignoreCase = true) ||
            item.year.contains(cleanQuery) ||
            (detectedQueryYear != null && item.year == detectedQueryYear)
        }
        for (item in cachedMatches) {
            if (seenIds.add(item.id)) {
                val finalItem = if (isYearQuery && item.year.isBlank()) item.copy(year = detectedQueryYear) else item
                combinedResults.add(finalItem)
            }
        }

        // 4. Match against popular series cache
        val popularSeriesMatches = MediaCache.getPopularSeries().filter { s ->
            s.title.contains(cleanQuery, ignoreCase = true) ||
            s.vj.contains(cleanQuery, ignoreCase = true) ||
            s.genre.contains(cleanQuery, ignoreCase = true)
        }
        for (item in popularSeriesMatches) {
            if (seenIds.add(item.id)) {
                combinedResults.add(item)
            }
        }

        MediaCache.putAll(combinedResults)

        // Consolidate series results: If there are multiple episode items for the same series in search results,
        // group them into a single unified series card so the search results show all seasons of the series.
        val knownSeriesTitles = MediaCache.getPopularSeries().map { parser.extractBaseSeriesName(it.title).lowercase().trim() }.filter { it.length >= 3 }.toSet()

        val flaggedResults = combinedResults.map { item ->
            val base = parser.extractBaseSeriesName(item.title).lowercase().trim()
            val isKnownSeries = item.isSeries ||
                item.id.startsWith("series_") ||
                item.streamUrl.contains("seriesdetails.php") ||
                item.streamUrl.contains("serie_id=") ||
                knownSeriesTitles.contains(base) ||
                MediaCache.hasSeriesDetail(item.id) ||
                MediaCache.get(item.id)?.isSeries == true
            if (isKnownSeries && !item.isSeries) {
                item.copy(isSeries = true)
            } else {
                item
            }
        }

        val consolidatedResults = mutableListOf<MediaItem>()
        val seriesGroups = mutableMapOf<String, MutableList<MediaItem>>()

        for (item in flaggedResults) {
            if (item.isSeries) {
                val baseName = parser.extractBaseSeriesName(item.title).lowercase().trim()
                val vjKey = item.vj.lowercase().trim().replace(Regex("""[^a-z0-9]"""), "")
                val groupKey = if (vjKey.isNotBlank()) "${baseName}_$vjKey" else baseName
                if (baseName.length >= 2) {
                    seriesGroups.getOrPut(groupKey) { mutableListOf() }.add(item)
                } else {
                    consolidatedResults.add(item)
                }
            } else {
                consolidatedResults.add(item)
            }
        }

        for ((_, itemsInGroup) in seriesGroups) {
            val explicitSeriesItem = itemsInGroup.firstOrNull {
                it.streamUrl.contains("seriesdetails.php") ||
                it.streamUrl.contains("serie_id=") ||
                it.id.startsWith("series_")
            } ?: itemsInGroup.firstOrNull { !it.title.matches(Regex("""(?i).*(?:Episode|EPS|Ep|\bE\d+).*""")) }
              ?: itemsInGroup.first()

            val bestPoster = itemsInGroup.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl ?: explicitSeriesItem.posterUrl
            val bestVj = itemsInGroup.firstOrNull { it.vj.isNotBlank() && it.vj != "VJ Translated" }?.vj ?: explicitSeriesItem.vj
            val bestYear = itemsInGroup.firstOrNull { it.year.isNotBlank() }?.year ?: explicitSeriesItem.year
            val cleanTitle = parser.extractBaseSeriesName(explicitSeriesItem.title).ifBlank { explicitSeriesItem.title }

            val canonicalStreamUrl = if (explicitSeriesItem.streamUrl.contains("seriesdetails.php")) {
                explicitSeriesItem.streamUrl
            } else {
                val encT = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                val encV = java.net.URLEncoder.encode(bestVj, "UTF-8")
                "https://twolekede.com/seriesdetails.php?title=$encT&category=$encV"
            }

            val unifiedSeriesItem = explicitSeriesItem.copy(
                id = explicitSeriesItem.id.takeIf { it.startsWith("series_") } ?: "series_${cleanTitle.lowercase().replace(" ", "_")}_${bestVj.lowercase().replace(" ", "_")}",
                title = cleanTitle,
                streamUrl = canonicalStreamUrl,
                isSeries = true,
                posterUrl = bestPoster,
                backdropUrl = bestPoster,
                vj = bestVj,
                year = bestYear
            )
            consolidatedResults.add(unifiedSeriesItem)
        }

        MediaCache.putAll(consolidatedResults)

        return Result.success(consolidatedResults)
    }
}

class PlaybackRepositoryImpl(
    private val api: MunowatchApi,
    private val parser: MunowatchHtmlParser,
    private val database: AppDatabase,
    private val sessionManager: SecureSessionManager? = null
) : PlaybackRepository {

    private fun extractNumericId(input: String): String? {
        if (input.isBlank()) return null
        val trimmed = input.trim()
        if (trimmed.all { it.isDigit() }) return trimmed

        // Handle disambiguated media IDs (e.g., "12345_1", "12345_2")
        Regex("""^([0-9]{2,8})_\d+$""").find(trimmed)?.groupValues?.get(1)?.let { return it }

        // Query parameters in URLs or paths (e.g., ?id=12345, &mid=12345)
        Regex("""[?&](?:id|mid|video_id|movie_id|content_id)=([0-9]+)""").find(trimmed)?.groupValues?.get(1)?.let { return it }

        if (trimmed.contains("preview.php") || trimmed.contains("download.php") || trimmed.contains("moviedetails.php") || trimmed.contains("twolekede")) {
            Regex("""(?:id|mid)=([0-9]+)""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        }

        if (trimmed.startsWith("myvj_")) return null

        if (trimmed.startsWith("twolekede_")) {
            val part = trimmed.removePrefix("twolekede_").substringBefore("_")
            if (part.isNotBlank() && part.all { it.isDigit() }) return part
        }

        if (trimmed.contains("_ep") || trimmed.contains("ep_")) {
            Regex("""_([0-9]{2,8})$""").find(trimmed)?.groupValues?.get(1)?.let { return it }
            Regex("""ep_([0-9]{2,8})""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        }
        if (trimmed.startsWith("series_")) return null
        Regex("""_([0-9]{2,8})$""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        Regex("""ep_([0-9]{2,8})""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        Regex("""(?:^|/)(\d{3,8})(?:\.[a-z0-9]+)?$""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        return null
    }

    private fun extractMunowatchId(contentId: String, streamPath: String?): String? {
        // Explicit Twolekede links or IDs are NEVER Munowatch IDs
        if (contentId.contains("twolekede.com") || contentId.contains("preview.php") ||
            streamPath?.contains("twolekede.com") == true || streamPath?.contains("preview.php") == true) {
            return null
        }

        val fromPath = streamPath?.let {
            Regex("""[?&](?:mid|id|v)=([0-9]+)""").find(it)?.groupValues?.get(1)
        }
        if (!fromPath.isNullOrBlank()) return fromPath

        val fromCache = MediaCache.getMunoId(contentId)
            ?: MediaCache.getMunoId(contentId.removePrefix("myvj_"))
            ?: MediaCache.getMunoId("myvj_$contentId")
        if (!fromCache.isNullOrBlank()) return fromCache

        // Only return if explicitly marked as a Munowatch ID
        if (contentId.startsWith("muno_") && contentId.removePrefix("muno_").all { it.isDigit() }) return contentId.removePrefix("muno_")
        return null
    }

    override suspend fun getPlaybackSource(contentId: String, serverName: String?): Result<PlaybackSource> {
        val resolvedServer = serverName ?: "Server 1 (Fast CDN)"
        android.util.Log.d("MunowatchPlayback", "getPlaybackSource contentId=$contentId, server=$resolvedServer")

        // Step 0: Check memory playback cache for instant playback and zero loading delays
        val cachedSource = MediaCache.getPlaybackSource(contentId, resolvedServer)
        if (cachedSource != null && MunowatchApi.isValidStreamUrl(cachedSource.url)) {
            android.util.Log.d("MunowatchPlayback", "Returning cached source: ${cachedSource.url}")
            return Result.success(cachedSource)
        }

        // 1. Resolve watch stream path from memory cache or directly from parameter
        var streamPath = MediaCache.getStreamPath(contentId)
        var extractedId = extractNumericId(contentId) ?: streamPath?.let { extractNumericId(it) }
        android.util.Log.d("MunowatchPlayback", "Resolved streamPath=$streamPath, extractedId=$extractedId")

        // Check if contentId represents a full series rather than a specific episode
        val isExplicitSeries = (contentId.startsWith("series_") || MediaCache.get(contentId)?.isSeries == true) &&
            !contentId.contains("_ep") && !contentId.contains("ep_") &&
            !contentId.contains("preview.php") && !contentId.contains("mid=") &&
            !contentId.removePrefix("series_").all { it.isDigit() }

        if (isExplicitSeries) {
            // First check if series detail is already cached with loaded episodes
            val cachedDetail = MediaCache.getSeriesDetail(contentId)
            val firstCachedEp = cachedDetail?.seasons?.firstOrNull()?.episodes?.firstOrNull { it.streamUrl.isNotBlank() }
            if (firstCachedEp != null) {
                streamPath = firstCachedEp.streamUrl
                extractedId = extractNumericId(firstCachedEp.id) ?: extractNumericId(firstCachedEp.streamUrl)
            } else {
                val cleanSeriesId = contentId.removePrefix("series_")
                val tkSeriesRes = api.getTwolekedeSeriesDetails(cleanSeriesId)
                val sHtml = tkSeriesRes.getOrNull() ?: api.getSeriesList(cleanSeriesId).getOrNull()
                if (!sHtml.isNullOrBlank()) {
                    val sDetail = parser.parseSeriesEpisodes(cleanSeriesId, sHtml)
                    if (sDetail.seasons.any { it.episodes.isNotEmpty() }) {
                        MediaCache.putSeriesDetail(sDetail)
                    }
                    val firstEp = sDetail.seasons.firstOrNull()?.episodes?.firstOrNull { it.streamUrl.isNotBlank() }
                    if (firstEp != null) {
                        streamPath = firstEp.streamUrl
                        extractedId = extractNumericId(firstEp.id) ?: extractNumericId(firstEp.streamUrl)
                    }
                }
            }
        }

        val targetCandidate = streamPath ?: contentId
        val finalId = extractedId ?: extractNumericId(targetCandidate) ?: extractNumericId(contentId)

        val cachedItem = MediaCache.get(contentId)
            ?: MediaCache.getMovieDetail(contentId)
            ?: (if (contentId.startsWith("myvj_")) MediaCache.get(contentId.removePrefix("myvj_")) ?: MediaCache.getMovieDetail(contentId.removePrefix("myvj_")) else null)
            ?: (if (!contentId.startsWith("myvj_")) MediaCache.get("myvj_$contentId") ?: MediaCache.getMovieDetail("myvj_$contentId") else null)
            ?: finalId?.let { MediaCache.get(it) }

        fun determineStreamQuality(url: String, defaultQuality: String = "1080p Full HD"): String {
            val lower = url.lowercase()
            return when {
                lower.contains("mode=preview") || lower.contains("360p") -> "360p Data Saver"
                lower.contains("4k") || lower.contains("2160p") -> "4K Ultra HD"
                lower.contains("1440p") || lower.contains("2k") -> "1440p QHD"
                lower.contains("1080p") || lower.contains("full hd") || lower.contains("fhd") -> "1080p Full HD"
                lower.contains("720p") || lower.contains("hd") -> "720p HD"
                lower.contains("480p") || lower.contains("sd") -> "480p SD"
                else -> cachedItem?.quality?.takeIf { it.isNotBlank() } ?: defaultQuality
            }
        }

        val directPlayableUrl = listOfNotNull(
            streamPath,
            cachedItem?.streamUrl,
            MediaCache.getMovieDetail(contentId)?.streamUrl,
            MediaCache.get(contentId)?.streamUrl
        ).firstOrNull {
            MunowatchApi.isValidStreamUrl(it) &&
            !it.contains("trailer256.b-cdn.net") &&
            !it.contains("intro_myvj") &&
            !it.contains("/trailer") &&
            !it.contains("_trailer") &&
            (!it.contains("mode=preview") || MediaCache.getPreferredStreamQuality().equals("360P", ignoreCase = true)) &&
            (it.contains("movviez.com") || it.contains("b-cdn.net") || it.contains(".mp4") || it.contains(".m3u8"))
        }

        if (streamPath.isNullOrBlank() && directPlayableUrl != null) {
            streamPath = directPlayableUrl
        }

        // Fast-track: If direct playable CDN URL is already verified and Server 1 is requested, return immediately
        if (directPlayableUrl != null && (serverName == null || resolvedServer.contains("Server 1", ignoreCase = true))) {
            val cleanUrl = directPlayableUrl.replace("&amp;", "&").replace("&#38;", "&").trim()
            val normalizedUrl = NetworkClient.normalizeStreamUrl(cleanUrl)
            val source = PlaybackSource(
                url = normalizedUrl,
                type = if (normalizedUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                quality = determineStreamQuality(cleanUrl, cachedItem?.quality ?: "1080p Full HD"),
                serverName = resolvedServer,
                headers = mapOf(
                    "Referer" to if (normalizedUrl.contains("twolekede") || normalizedUrl.contains("movviez")) "https://twolekede.com/" else "https://munowatch.com/",
                    "User-Agent" to NetworkClient.USER_AGENT,
                    "Accept" to "*/*"
                )
            )
            MediaCache.cachePlaybackSource(contentId, source)
            android.util.Log.d("MunowatchPlayback", "Fast-track direct CDN stream returned: ${source.url}")
            return Result.success(source)
        }
        val titleCandidate = cachedItem?.title ?: MediaCache.getMovieDetail(contentId)?.title ?: MediaCache.getSeriesDetail(contentId)?.title
        val isSeriesEpisode = contentId.contains("_ep_") || contentId.startsWith("ep_") || cachedItem?.isSeries == true
        val epNum = Regex("""_ep_?(\d+)""").find(contentId)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?:Episode|Ep|E)\s*(\d+)""", RegexOption.IGNORE_CASE).find(titleCandidate.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
            ?: 1

        val isExplicitTwolekede = targetCandidate.contains("twolekede") ||
            targetCandidate.contains("preview.php") ||
            targetCandidate.contains("download.php") ||
            targetCandidate.contains("moviedetails.php") ||
            targetCandidate.contains("seriesdetails.php") ||
            targetCandidate.contains("episodes.php") ||
            contentId.startsWith("twolekede_") ||
            (finalId != null && !contentId.startsWith("myvj_"))

        val cookie = sessionManager?.getSessionId()?.takeIf { it.isNotBlank() }
            ?: com.example.data.session.SecureSessionManager.DEFAULT_SESSION_ID
        val hasSession = cookie.isNotBlank()

        val resolveMunowatchNativeStream: suspend () -> PlaybackSource? = {
            var foundSource: PlaybackSource? = null
            val munoHeaders = mapOf(
                "Referer" to "https://munowatch.com/",
                "User-Agent" to NetworkClient.USER_AGENT,
                "Accept" to "*/*"
            )

            val currentStreamPath = streamPath
            val isMunowatchWatchUrl = (currentStreamPath?.startsWith("/twolekede?") == true) ||
                (currentStreamPath?.contains("munowatch.com") == true) ||
                (currentStreamPath?.contains("?v=") == true) ||
                (currentStreamPath?.contains("mid=") == true)

            // Attempt 1: Direct streamPath if it points to a Munowatch watch page
            if (isMunowatchWatchUrl && !currentStreamPath.isNullOrBlank()) {
                val munoHtml = api.getWatchPageHtml(currentStreamPath).getOrNull()
                if (!munoHtml.isNullOrBlank()) {
                    val munoSource = parser.extractVideoSourceFromWatchPage(munoHtml, resolvedServer)
                    if (munoSource != null && MunowatchApi.isValidStreamUrl(munoSource.url) &&
                        !munoSource.url.contains("trailer256.b-cdn.net") && !munoSource.url.contains("intro_myvj")
                    ) {
                        foundSource = munoSource.copy(headers = munoHeaders)
                    }
                }
            }

            // Attempt 2: If we have a numeric ID (from Twolekede ID or MyVJ rowId), fetch /twolekede?mid=$munoId
            val munoId = extractMunowatchId(contentId, currentStreamPath)
            if (foundSource == null && !munoId.isNullOrBlank() && !isExplicitSeries) {
                val watchUrl = "/twolekede?mid=$munoId"
                val munoHtml = api.getWatchPageHtml(watchUrl).getOrNull()
                if (!munoHtml.isNullOrBlank()) {
                    val munoSource = parser.extractVideoSourceFromWatchPage(munoHtml, resolvedServer)
                    if (munoSource != null && MunowatchApi.isValidStreamUrl(munoSource.url) &&
                        !munoSource.url.contains("trailer256.b-cdn.net") && !munoSource.url.contains("intro_myvj")
                    ) {
                        foundSource = munoSource.copy(headers = munoHeaders)
                    }
                }
            }

            // Attempt 2.5: Look up authentic Munowatch muno_id via MyVJ search API
            if (foundSource == null && !titleCandidate.isNullOrBlank() && !isExplicitSeries) {
                val cleanT = parser.cleanTitle(titleCandidate)
                    .replace(Regex("""\[.*?\]|\(.*?\)|(?i)\b(?:by\s+vj|vj)\s+.*$"""), " ")
                    .trim(' ', '-', '–', '—', ':', '|')
                if (cleanT.length >= 2) {
                    try {
                        val myvjJson = api.searchMyVj(cleanT).getOrNull()
                        if (myvjJson != null) {
                            val matches = myvjJson.optJSONArray("matches_title_movies")
                            if (matches != null && matches.length() > 0) {
                                val targetVj = cachedItem?.vj?.lowercase()?.replace("vj", "")?.replace(".", "")?.trim().orEmpty()
                                var discoveredMunoId: String? = null
                                for (i in 0 until matches.length()) {
                                    val obj = matches.optJSONObject(i) ?: continue
                                    val mTitle = obj.optString("title")
                                    val mVj = obj.optString("vj")
                                    val mId = obj.optString("muno_id").takeIf { it.isNotBlank() && it != "null" }
                                    if (mId != null && parser.titlesMatch(mTitle, cleanT)) {
                                        val vjMatches = targetVj.isBlank() || mVj.lowercase().contains(targetVj) || targetVj.contains(mVj.lowercase())
                                        if (vjMatches) {
                                            discoveredMunoId = mId
                                            break
                                        } else if (discoveredMunoId == null) {
                                            discoveredMunoId = mId
                                        }
                                    }
                                }
                                if (discoveredMunoId != null) {
                                    MediaCache.putMunoId(contentId, discoveredMunoId)
                                    val munoHtml = api.getWatchPageHtml("/twolekede?mid=$discoveredMunoId").getOrNull()
                                    if (!munoHtml.isNullOrBlank()) {
                                        val munoSource = parser.extractVideoSourceFromWatchPage(munoHtml, resolvedServer)
                                        if (munoSource != null && MunowatchApi.isValidStreamUrl(munoSource.url) &&
                                            !munoSource.url.contains("trailer256.b-cdn.net") && !munoSource.url.contains("intro_myvj")
                                        ) {
                                            foundSource = munoSource.copy(headers = munoHeaders)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Attempt 3: Search Munowatch watch URL by title (and VJ)
            if (foundSource == null && !titleCandidate.isNullOrBlank() && !titleCandidate.startsWith("Movie #", ignoreCase = true) && !titleCandidate.startsWith("Series #", ignoreCase = true)) {
                val cleanSearchTitle = parser.cleanTitle(titleCandidate)
                    .replace(Regex("""\[.*?\]|\(.*?\)|(?i)\b(?:by\s+vj|vj)\s+.*$"""), " ")
                    .replace(Regex("""(?i)\b(?:season|seasons|series|s)\s*[0-9]+.*$|(?i)\b(?:episode|ep|e)\s*[0-9]+.*$"""), " ")
                    .trim(' ', '-', '–', '—', ':', '|')
                if (cleanSearchTitle.length >= 2) {
                    val munoWatchUrl = api.searchMunowatchWatchUrl(cleanSearchTitle, cachedItem?.vj)
                    if (!munoWatchUrl.isNullOrBlank()) {
                        var targetWatchUrl = munoWatchUrl
                        val munoHtml = api.getWatchPageHtml(targetWatchUrl).getOrNull()
                        if (!munoHtml.isNullOrBlank()) {
                            var playHtmlToExtract = munoHtml
                            if (isSeriesEpisode && epNum > 1) {
                                val epDoc = org.jsoup.Jsoup.parse(munoHtml)
                                val specificEpLink = epDoc.select("a[href*='twolekede?v=']").firstOrNull { a ->
                                    val linkText = "${a.text()} ${a.attr("href")}"
                                    Regex("""(?i)\b(?:eps|ep|episode|e)\s*\+*\s*$epNum\b""").containsMatchIn(linkText)
                                }?.attr("href")?.replace("&amp;", "&")
                                if (!specificEpLink.isNullOrBlank()) {
                                    targetWatchUrl = if (specificEpLink.startsWith("http")) specificEpLink else "https://munowatch.com/${specificEpLink.removePrefix("/")}"
                                    val specificEpHtml = api.getWatchPageHtml(targetWatchUrl).getOrNull()
                                    if (!specificEpHtml.isNullOrBlank()) {
                                        playHtmlToExtract = specificEpHtml
                                    }
                                }
                            }

                            val munoSource = parser.extractVideoSourceFromWatchPage(playHtmlToExtract, resolvedServer)
                            if (munoSource != null && MunowatchApi.isValidStreamUrl(munoSource.url) &&
                                !munoSource.url.contains("trailer256.b-cdn.net") && !munoSource.url.contains("intro_myvj")
                            ) {
                                foundSource = munoSource.copy(headers = munoHeaders)
                            }
                        }
                    }

                    // Attempt 4: Search Munowatch search results cards directly
                    if (foundSource == null) {
                        val searchRes = api.search(cleanSearchTitle).getOrNull()
                        if (!searchRes.isNullOrBlank()) {
                            val searchCards = parser.parseMediaCards(searchRes)
                            val targetVj = cachedItem?.vj?.lowercase()?.replace("vj", "")?.replace(".", "")?.trim().orEmpty()
                            val matchCard = searchCards.firstOrNull { card ->
                                val ct = parser.cleanTitle(card.title)
                                val tMatch = parser.titlesMatch(ct, cleanSearchTitle)
                                val cvj = card.vj.lowercase().replace("vj", "").replace(".", "").trim()
                                val vMatch = targetVj.isBlank() || cvj.contains(targetVj) || targetVj.contains(cvj)
                                tMatch && vMatch && card.streamUrl.isNotBlank()
                            } ?: searchCards.firstOrNull { card ->
                                val ct = parser.cleanTitle(card.title)
                                parser.titlesMatch(ct, cleanSearchTitle) && card.streamUrl.isNotBlank()
                            }
                            if (matchCard != null && matchCard.streamUrl.isNotBlank()) {
                                val cardHtml = api.getWatchPageHtml(matchCard.streamUrl).getOrNull()
                                if (!cardHtml.isNullOrBlank()) {
                                    val munoSource = parser.extractVideoSourceFromWatchPage(cardHtml, resolvedServer)
                                    if (munoSource != null && MunowatchApi.isValidStreamUrl(munoSource.url) &&
                                        !munoSource.url.contains("trailer256.b-cdn.net") && !munoSource.url.contains("intro_myvj")
                                    ) {
                                        foundSource = munoSource.copy(headers = munoHeaders)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Attempt 5: Resolve stream path by content ID via Munowatch grid slots
            if (foundSource == null && !munoId.isNullOrBlank()) {
                val slotPath = api.resolveStreamPathForId(munoId) ?: api.resolveStreamPathForId(contentId)
                if (!slotPath.isNullOrBlank()) {
                    val slotHtml = api.getWatchPageHtml(slotPath).getOrNull()
                    if (!slotHtml.isNullOrBlank()) {
                        val munoSource = parser.extractVideoSourceFromWatchPage(slotHtml, resolvedServer)
                        if (munoSource != null && MunowatchApi.isValidStreamUrl(munoSource.url) &&
                            !munoSource.url.contains("trailer256.b-cdn.net") && !munoSource.url.contains("intro_myvj")
                        ) {
                            foundSource = munoSource.copy(headers = munoHeaders)
                        }
                    }
                }
            }

            foundSource
        }

        val isServer2OrMunowatch = resolvedServer.contains("Munowatch", ignoreCase = true) ||
            resolvedServer.contains("Server 2", ignoreCase = true)

        // PRIORITY A0: If Munowatch Stream / Server 2 is requested, prioritize independent Munowatch native Bunny CDN stream
        if (isServer2OrMunowatch && hasSession) {
            val munoSource = resolveMunowatchNativeStream()
            if (munoSource != null) {
                MediaCache.cachePlaybackSource(contentId, munoSource)
                android.util.Log.d("MunowatchPlayback", "Priority Server 2 Munowatch native Bunny CDN stream resolved: ${munoSource.url}")
                return Result.success(munoSource)
            }
        }

        // Also check if cached/direct stream has an authentic Bunny CDN stream for Server 2
        if (isServer2OrMunowatch) {
            val directBcdn = listOfNotNull(
                streamPath,
                cachedItem?.streamUrl,
                MediaCache.getMovieDetail(contentId)?.streamUrl,
                MediaCache.get(contentId)?.streamUrl
            ).firstOrNull {
                MunowatchApi.isValidStreamUrl(it) && it.contains("b-cdn.net") && !it.contains("trailer256.b-cdn.net") && !it.contains("intro_myvj")
            }
            if (directBcdn != null) {
                val cleanUrl = directBcdn.replace("&amp;", "&").replace("&#38;", "&").trim()
                val source = PlaybackSource(
                    url = cleanUrl,
                    type = if (cleanUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                    quality = determineStreamQuality(cleanUrl, cachedItem?.quality ?: "1080p Full HD"),
                    serverName = resolvedServer,
                    headers = mapOf(
                        "Referer" to "https://munowatch.com/",
                        "User-Agent" to NetworkClient.USER_AGENT,
                        "Accept" to "*/*"
                    )
                )
                MediaCache.cachePlaybackSource(contentId, source)
                return Result.success(source)
            }
        }

        // PRIORITY A: Direct Twolekede Media Server Stream Resolution
        // Resolve directly from media servers for explicit Twolekede items (preview/download/details/episodes)
        // Strictly avoid querying movviez.com if Server 2 is requested and movviez.com is rate-limited
        if (isExplicitTwolekede && (finalId != null || targetCandidate.contains("twolekede") || targetCandidate.contains("preview.php"))) {
            if (isServer2OrMunowatch && MunowatchApi.isCdnRateLimited()) {
                android.util.Log.w("MunowatchPlayback", "Skipping movviez.com resolution for Server 2 because CDN is rate-limited")
            } else {
                val queryTarget = finalId ?: targetCandidate
                val resolvedUrl = api.resolveTwolekedeStream(queryTarget, resolvedServer, titleCandidate)

                if (!resolvedUrl.isNullOrBlank() && MunowatchApi.isValidStreamUrl(resolvedUrl)) {
                    val cleanUrl = resolvedUrl.replace("&amp;", "&").replace("&#38;", "&").trim()
                    val normalizedUrl = NetworkClient.normalizeStreamUrl(cleanUrl)
                    val source = PlaybackSource(
                        url = normalizedUrl,
                        type = if (normalizedUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                        quality = determineStreamQuality(cleanUrl, "1080p Full HD"),
                        serverName = resolvedServer,
                        headers = mapOf(
                            "Referer" to "https://twolekede.com/",
                            "User-Agent" to NetworkClient.USER_AGENT,
                            "Accept" to "*/*"
                        )
                    )
                    MediaCache.cachePlaybackSource(contentId, source)
                    android.util.Log.d("MunowatchPlayback", "Twolekede direct stream resolved: ${source.url}")
                    return Result.success(source)
                }
            }
        }

        val isMunoRequested = isServer2OrMunowatch ||
            resolvedServer.contains("Server 1", ignoreCase = true) ||
            resolvedServer.contains("Fast CDN", ignoreCase = true) ||
            resolvedServer.contains("Server 3", ignoreCase = true) ||
            resolvedServer.contains("Alternate", ignoreCase = true)

        // PRIORITY B: Fallback to Munowatch native Bunny CDN stream
        if (isMunoRequested && hasSession) {
            val munoSource = resolveMunowatchNativeStream()
            if (munoSource != null) {
                MediaCache.cachePlaybackSource(contentId, munoSource)
                android.util.Log.d("MunowatchPlayback", "Fallback Munowatch native Bunny CDN stream resolved: ${munoSource.url}")
                return Result.success(munoSource)
            }
        }

        // PRIORITY C: Search Twolekede media server directly by title if numeric ID wasn't in input or wasn't verified
        if (!titleCandidate.isNullOrBlank() && !titleCandidate.startsWith("Movie #", ignoreCase = true) && !titleCandidate.startsWith("Series #", ignoreCase = true)) {
            val cleanSearchTitle = parser.cleanTitle(titleCandidate)
                .replace(Regex("""\[.*?\]|\(.*?\)|<.*?>"""), " ")
                .replace(Regex("""(?i)\s+(?:19\d{2}|20\d{2})\s*$"""), " ")
                .replace(Regex("""(?i)\b(?:by\s+vj|vj)\s+[a-z0-9_\-\.]*.*$"""), " ")
                .replace(Regex("""(?i)\b(?:season|seasons|series|s)\s*[0-9]+.*$|(?i)\b(?:episode|ep|e)\s*[0-9]+.*$"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim(' ', '-', '–', '—', ':', '|')

            val candidateQueries = linkedSetOf<String>()

            // 1. Prefix before colon/dash (crucial for SQL LIKE matching on Twolekede, e.g. "Furiosa: A Mad Max Saga" -> "Furiosa")
            val rawSub = titleCandidate.substringBefore(":").substringBefore("-").substringBefore("–").trim()
            if (rawSub.length >= 3 && rawSub != titleCandidate && !rawSub.startsWith("Movie ", ignoreCase = true) && !rawSub.startsWith("Series ", ignoreCase = true)) {
                candidateQueries.add(rawSub)
            }
            val cleanSub = cleanSearchTitle.substringBefore(":").substringBefore("-").substringBefore("–").trim()
            if (cleanSub.length >= 3 && cleanSub != cleanSearchTitle) {
                candidateQueries.add(cleanSub)
            }

            // 2. Primary substantive keyword (e.g. "Furiosa", "Deadpool", "Hellhound", "Centauro")
            val substantiveTokens = cleanSearchTitle.split(" ")
                .map { it.replace(Regex("""[^a-zA-Z0-9]"""), "").trim() }
                .filter { it.length >= 4 && it.lowercase() !in setOf("the", "with", "from", "part", "this", "that", "movie", "full", "translated", "luganda", "saga") }
            val primaryToken = substantiveTokens.firstOrNull()
            if (primaryToken != null && primaryToken.length >= 3) {
                candidateQueries.add(primaryToken)
            }

            // 3. Full clean search title and standard variants
            if (cleanSearchTitle.length >= 2) {
                candidateQueries.add(cleanSearchTitle)
                val noArticle = cleanSearchTitle.replace(Regex("""^(?:the|a|an)\s+""", RegexOption.IGNORE_CASE), "").trim()
                if (noArticle.length >= 2) {
                    candidateQueries.add(noArticle)
                }
                val noPart = cleanSearchTitle.replace(Regex("""(?i)\s*[-–—:]*\s*\b(?:part|pt|volume|vol|cd)\s*[0-9a-z]+\b.*$"""), "").trim(' ', '-', '–', '—', ':', '|')
                if (noPart.length >= 2 && noPart != cleanSearchTitle) {
                    candidateQueries.add(noPart)
                    val noPartNoArt = noPart.replace(Regex("""^(?:the|a|an)\s+""", RegexOption.IGNORE_CASE), "").trim()
                    if (noPartNoArt.length >= 2) candidateQueries.add(noPartNoArt)
                }
                val noApostrophe = cleanSearchTitle.replace("'", "").replace("’", "").replace("`", "").trim()
                if (noApostrophe.length >= 2 && noApostrophe != cleanSearchTitle) {
                    candidateQueries.add(noApostrophe)
                }
                for (token in substantiveTokens.drop(1)) {
                    if (token != cleanSearchTitle) {
                        candidateQueries.add(token)
                    }
                }
                candidateQueries.add(cleanSearchTitle.replace(" ", ""))
            }

            for (q in candidateQueries.filter { it.length >= 2 }.take(3)) {
                try {
                    val tkSearchRes = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                        api.searchTwolekede(q, isSeries = isSeriesEpisode).getOrNull()
                    }
                    if (!tkSearchRes.isNullOrBlank()) {
                        val tkCards = parser.parseMediaCards(tkSearchRes)
                        val targetVj = cachedItem?.vj?.lowercase()?.replace("vj", "")?.replace(".", "")?.trim().orEmpty()
                        val searchTokens = cleanSearchTitle.split(" ")
                            .map { it.replace(Regex("""[^a-zA-Z0-9]"""), "").trim().lowercase() }
                            .filter { it.length >= 4 && it !in setOf("the", "with", "from", "part", "this", "that") }

                        val matchCard = tkCards.firstOrNull { card ->
                            val ct = parser.cleanTitle(card.title)
                            val titleMatches = parser.titlesMatch(ct, cleanSearchTitle)
                            val cvj = card.vj.lowercase().replace("vj", "").replace(".", "").trim()
                            val vjMatches = targetVj.isBlank() || cvj.contains(targetVj) || targetVj.contains(cvj)
                            titleMatches && vjMatches
                        } ?: tkCards.firstOrNull { card ->
                            val ct = parser.cleanTitle(card.title)
                            parser.titlesMatch(ct, cleanSearchTitle)
                        } ?: (if (searchTokens.isNotEmpty()) {
                            tkCards.firstOrNull { card ->
                                val ct = parser.cleanTitle(card.title).lowercase()
                                val cvj = card.vj.lowercase().replace("vj", "").replace(".", "").trim()
                                val vjMatches = targetVj.isBlank() || cvj.contains(targetVj) || targetVj.contains(cvj)
                                val allTokens = searchTokens.all { tok -> ct.contains(tok) }
                                allTokens && vjMatches
                            } ?: tkCards.firstOrNull { card ->
                                val ct = parser.cleanTitle(card.title).lowercase()
                                searchTokens.all { tok -> ct.contains(tok) }
                            }
                        } else null)

                        val matchId = matchCard?.let { extractNumericId(it.streamUrl) ?: extractNumericId(it.id) }

                        if (matchId != null) {
                            if (isServer2OrMunowatch && MunowatchApi.isCdnRateLimited()) {
                                android.util.Log.w("MunowatchPlayback", "Skipping Twolekede search resolution for Server 2 because CDN is rate-limited")
                            } else {
                                val resolvedUrl = api.resolveTwolekedeStream(matchId, resolvedServer, titleCandidate)
                                if (!resolvedUrl.isNullOrBlank() && MunowatchApi.isValidStreamUrl(resolvedUrl)) {
                                    val cleanUrl = resolvedUrl.replace("&amp;", "&").replace("&#38;", "&").trim()
                                    val normalizedUrl = NetworkClient.normalizeStreamUrl(cleanUrl)
                                    val source = PlaybackSource(
                                        url = normalizedUrl,
                                        type = if (normalizedUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                                        quality = determineStreamQuality(cleanUrl, "1080p Full HD"),
                                        serverName = resolvedServer,
                                        headers = mapOf(
                                            "Referer" to "https://twolekede.com/",
                                            "User-Agent" to NetworkClient.USER_AGENT,
                                            "Accept" to "*/*"
                                        )
                                    )
                                    MediaCache.putStreamPath(contentId, "https://twolekede.com/preview.php?id=$matchId")
                                    MediaCache.cachePlaybackSource(contentId, source)
                                    android.util.Log.d("MunowatchPlayback", "Twolekede stream resolved via search: ${source.url}")
                                    return Result.success(source)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // PRIORITY D: Fallback to Munowatch Native Stream if not already tried and user is authenticated
        if (hasSession) {
            val fallbackMunoSource = resolveMunowatchNativeStream()
            if (fallbackMunoSource != null) {
                MediaCache.cachePlaybackSource(contentId, fallbackMunoSource)
                android.util.Log.d("MunowatchPlayback", "Munowatch native Bunny CDN stream resolved via fallback: ${fallbackMunoSource.url}")
                return Result.success(fallbackMunoSource)
            }
        }

        if (streamPath.isNullOrBlank() && extractedId != null && !contentId.startsWith("series_") && isExplicitTwolekede) {
            streamPath = "https://twolekede.com/preview.php?id=$extractedId"
        }

        // Direct playable CDN URL fallback (strictly verified full movie streams only, never promo trailers)
        val directUrl = if (!streamPath.isNullOrBlank() && MunowatchApi.isValidStreamUrl(streamPath) && !streamPath.contains("trailer256.b-cdn.net") && !streamPath.contains("intro_myvj") && (streamPath.contains("movviez.com") || streamPath.contains("b-cdn.net") || streamPath.contains(".mp4") || streamPath.contains(".m3u8"))) {
            streamPath
        } else {
            directPlayableUrl
        }
        if (!directUrl.isNullOrBlank() && MunowatchApi.isValidStreamUrl(directUrl) && !directUrl.contains("trailer256.b-cdn.net") && !directUrl.contains("intro_myvj") && (directUrl.contains("movviez.com") || directUrl.contains("b-cdn.net") || directUrl.contains(".mp4") || directUrl.contains(".m3u8"))) {
            val cleanUrl = directUrl.replace("&amp;", "&").replace("&#38;", "&").trim()
            val source = PlaybackSource(
                url = cleanUrl,
                type = if (cleanUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                quality = determineStreamQuality(cleanUrl, "720p HD"),
                serverName = resolvedServer,
                headers = mapOf(
                    "Referer" to if (cleanUrl.contains("twolekede")) "https://twolekede.com/" else "https://munowatch.com/",
                    "User-Agent" to NetworkClient.USER_AGENT,
                    "Accept" to "*/*"
                )
            )
            MediaCache.cachePlaybackSource(contentId, source)
            android.util.Log.d("MunowatchPlayback", "Direct CDN stream resolved: ${source.url}")
            return Result.success(source)
        }

        if (streamPath.isNullOrBlank() || streamPath == "/twolekede" || streamPath == "twolekede") {
            val fallbackMuno = resolveMunowatchNativeStream()
            if (fallbackMuno != null && MunowatchApi.isValidStreamUrl(fallbackMuno.url)) {
                MediaCache.cachePlaybackSource(contentId, fallbackMuno)
                android.util.Log.d("MunowatchPlayback", "Fallback to Munowatch website stream succeeded: ${fallbackMuno.url}")
                return Result.success(fallbackMuno)
            }
            val midToUse = extractMunowatchId(contentId, streamPath) ?: (if (!contentId.startsWith("myvj_")) finalId else null)
            if (hasSession && midToUse != null) {
                streamPath = "/twolekede?mid=$midToUse"
            } else {
                return Result.failure(
                    AppError.PlaybackError("The full movie is currently unavailable across streaming servers. Please select another title.")
                )
            }
        }

        // If user is not authenticated and streamPath points to munowatch web (which requires login), do NOT attempt it!
        val isMunoLoginPath = streamPath.startsWith("/twolekede") || streamPath.startsWith("twolekede") || streamPath.contains("munowatch.com")
        if (!hasSession && isMunoLoginPath) {
            return Result.failure(
                AppError.PlaybackError("The full movie is currently unavailable across streaming servers. Please select another title.")
            )
        }

        // 2. Fetch the movie/episode watch page HTML (only if not an unauthenticated Munowatch path)
        var htmlResult = if (hasSession || !isMunoLoginPath) {
            api.getWatchPageHtml(streamPath)
        } else {
            Result.failure(java.io.IOException("Unauthenticated Munowatch path skipped"))
        }
        if (htmlResult.isFailure && hasSession) {
            val exMsg = htmlResult.exceptionOrNull()?.message.orEmpty()
            if (exMsg.contains("Authentication required", ignoreCase = true) || exMsg.contains("subscription required", ignoreCase = true)) {
                sessionManager?.markSessionExpired()
            }
            val fallbackNum = Regex("""mid=([0-9]+)""").find(streamPath)?.groupValues?.get(1)
                ?: Regex("""\d+""").find(contentId)?.value
            if (fallbackNum != null) {
                htmlResult = api.getWatchPageHtml("/twolekede?mid=$fallbackNum")
            }
        }

        val html = htmlResult.getOrDefault("")

        // Check if streamPath / html returned a series list rather than a video player page
        var playHtml = html
        var activeTitlePrefix = ""
        val hasDirectStream = html.contains("data-gumite") || html.contains("id=\"soul-info\"") || html.contains("id='soul-info'")
        val isSeriesPage = !hasDirectStream && (
            streamPath.contains("list") || html.contains("list-container") || html.contains("serie_id")
        )

        if (isSeriesPage) {
            val seriesId = if (contentId.startsWith("series_")) contentId.removePrefix("series_") else contentId
            val sDetail = parser.parseSeriesEpisodes(seriesId, html)
            sDetail.seasons.forEach { season ->
                season.episodes.forEach { ep ->
                    MediaCache.registerEpisode(ep, sDetail.posterUrl, sDetail.title)
                }
            }
            val targetEp = sDetail.seasons.firstOrNull()?.episodes?.firstOrNull { it.streamUrl.isNotBlank() }
            if (targetEp != null) {
                val epWatchRes = api.getWatchPageHtml(targetEp.streamUrl)
                if (epWatchRes.isSuccess) {
                    playHtml = epWatchRes.getOrDefault("")
                    activeTitlePrefix = "${sDetail.title} - ${targetEp.title}"
                }
            }
        }

        // 3. Extract the unique video stream URL with automatic server fallback
        var source = parser.extractVideoSourceFromWatchPage(playHtml, resolvedServer)
            ?: parser.extractVideoSourceFromWatchPage(playHtml, "Server 2 (Backup)")
            ?: parser.extractVideoSourceFromWatchPage(playHtml, "Server 3 (Direct)")

        // If source is still null, check if playHtml contains an episode watch link
        if (source == null && playHtml.isNotBlank()) {
            val doc = org.jsoup.Jsoup.parse(playHtml)
            val watchLink = doc.selectFirst("a[href*='twolekede'], a[href*='mid='], [data-href*='twolekede']")?.let {
                it.attr("href").ifBlank { it.attr("data-href") }
            }
            if (!watchLink.isNullOrBlank() && watchLink != streamPath) {
                val nextHtml = api.getWatchPageHtml(watchLink).getOrDefault("")
                if (nextHtml.isNotBlank()) {
                    source = parser.extractVideoSourceFromWatchPage(nextHtml, resolvedServer)
                        ?: parser.extractVideoSourceFromWatchPage(nextHtml, "Server 2 (Backup)")
                        ?: parser.extractVideoSourceFromWatchPage(nextHtml, "Server 3 (Direct)")
                }
            }
        }

        // 3b. Fallback: Search Twolekede by title if Munowatch watch page failed or session is expired
        if (source == null) {
            val cachedItem = MediaCache.get(contentId)
            val titleToSearch = cachedItem?.title ?: activeTitlePrefix
            if (titleToSearch.isNotBlank() && !titleToSearch.startsWith("Movie #", ignoreCase = true) && !titleToSearch.startsWith("Series #", ignoreCase = true)) {
                val isSeriesEpisode = contentId.contains("_ep_") || contentId.startsWith("ep_") || cachedItem?.isSeries == true
                val epNum = Regex("""_ep_?(\d+)""").find(contentId)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(?:Episode|Ep|E)\s*(\d+)""", RegexOption.IGNORE_CASE).find(titleToSearch)?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                val cleanTitle = parser.cleanTitle(titleToSearch)
                    .replace(Regex("""\[.*?\]|\(.*?\)|(?i)\b(?:season|seasons|series|s)\s*[0-9]+.*$|(?i)\b(?:episode|ep|e)\s*[0-9]+.*$|(?i)\s+by\s+vj\s+.*$"""), " ")
                    .trim(' ', '-', '–', '—', ':')
                val targetVj = cachedItem?.vj?.lowercase()?.trim().orEmpty()
                if (cleanTitle.length >= 2) {
                    val noApostrophe = cleanTitle.replace("'", "").replace("’", "").replace("`", "").trim()
                    val alphanumeric = cleanTitle.replace(Regex("""[^a-zA-Z0-9\s]"""), " ").replace(Regex("""\s+"""), " ").trim()
                    val baseSeriesName = parser.extractBaseSeriesName(cleanTitle).ifBlank { cleanTitle }
                    val firstWord = cleanTitle.split(" ").firstOrNull { it.isNotBlank() }?.replace(Regex("""[^a-zA-Z0-9]"""), "") ?: ""
                    val candidateQueries = if (isSeriesEpisode) {
                        linkedSetOf(
                            cleanTitle,
                            baseSeriesName,
                            noApostrophe,
                            alphanumeric,
                            cleanTitle.substringBefore(":").substringBefore("-").trim(),
                            cleanTitle.removePrefix("The ").removePrefix("the ").trim()
                        ).filter { it.length >= 2 }
                    } else {
                        linkedSetOf(
                            cleanTitle,
                            noApostrophe,
                            alphanumeric,
                            cleanTitle.replace(" ", ""),
                            cleanTitle.substringBefore(":").substringBefore("-").trim(),
                            cleanTitle.removePrefix("The ").removePrefix("the ").trim(),
                            if (firstWord.length >= 4) firstWord else ""
                        ).filter { it.length >= 2 }
                    }

                    for (q in candidateQueries) {
                        if (source != null) break
                        try {
                            val searchHtml = api.searchTwolekede(q).getOrNull()
                            if (!searchHtml.isNullOrBlank()) {
                                val cards = parser.parseMediaCards(searchHtml)
                                if (isSeriesEpisode) {
                                    val seriesMatch = cards.firstOrNull { card ->
                                        val ct = parser.cleanTitle(card.title)
                                        val matchesTitle = parser.titlesMatch(ct, cleanTitle) || parser.titlesMatch(ct, baseSeriesName)
                                        val matchesVj = targetVj.isBlank() || card.vj.lowercase().contains(targetVj) || targetVj.contains(card.vj.lowercase().trim())
                                        matchesTitle && matchesVj && (card.streamUrl.contains("seriesdetails") || card.isSeries)
                                    } ?: cards.firstOrNull { card ->
                                        val ct = parser.cleanTitle(card.title)
                                        (parser.titlesMatch(ct, cleanTitle) || parser.titlesMatch(ct, baseSeriesName)) && (card.streamUrl.contains("seriesdetails") || card.isSeries)
                                    }
                                    if (seriesMatch != null) {
                                        val seriesDetailHtml = api.getTwolekedeSeriesDetails(seriesMatch.streamUrl).getOrNull()
                                        if (!seriesDetailHtml.isNullOrBlank()) {
                                            val sDetail = parser.parseSeriesEpisodes(seriesMatch.id, seriesDetailHtml, seriesMatch.title, knownPosterUrl = seriesMatch.posterUrl.takeIf { parser.isValidPosterUrl(it) })
                                            val targetEp = sDetail.seasons.flatMap { it.episodes }.find { it.number == epNum }
                                                ?: sDetail.seasons.firstOrNull()?.episodes?.firstOrNull()
                                            if (targetEp != null && targetEp.streamUrl.isNotBlank()) {
                                                if (isServer2OrMunowatch && MunowatchApi.isCdnRateLimited()) {
                                                    android.util.Log.w("MunowatchPlayback", "Skipping Twolekede episode stream resolution for Server 2 because CDN is rate-limited")
                                                } else {
                                                    val resolvedFallback = api.resolveTwolekedeStream(targetEp.streamUrl, resolvedServer, cleanTitle)
                                                        ?: api.resolveTwolekedeStream(targetEp.id, resolvedServer, cleanTitle)
                                                    if (!resolvedFallback.isNullOrBlank() && MunowatchApi.isValidStreamUrl(resolvedFallback)) {
                                                        val cleanFallback = resolvedFallback.replace("&amp;", "&").replace("&#38;", "&").trim()
                                                        val normalizedFallback = NetworkClient.normalizeStreamUrl(cleanFallback)
                                                        source = PlaybackSource(
                                                            url = normalizedFallback,
                                                            type = if (normalizedFallback.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                                                            quality = determineStreamQuality(cleanFallback, "1080p Full HD"),
                                                            serverName = resolvedServer,
                                                            headers = mapOf(
                                                                "Referer" to "https://twolekede.com/",
                                                                "User-Agent" to NetworkClient.USER_AGENT,
                                                                "Accept" to "*/*"
                                                            )
                                                        )
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val match = cards.firstOrNull { card ->
                                        val ct = parser.cleanTitle(card.title)
                                        val matchesTitle = parser.titlesMatch(ct, cleanTitle)
                                        val cvj = card.vj.lowercase().replace("vj", "").replace(".", "").trim()
                                        val matchesVj = targetVj.isBlank() || cvj.contains(targetVj) || targetVj.contains(cvj)
                                        matchesTitle && matchesVj && card.streamUrl.isNotBlank() &&
                                            (card.streamUrl.contains("preview.php") || card.streamUrl.contains("moviedetails") || card.id.all { c -> c.isDigit() })
                                    } ?: cards.firstOrNull { card ->
                                        val ct = parser.cleanTitle(card.title)
                                        parser.titlesMatch(ct, cleanTitle) && card.streamUrl.isNotBlank() &&
                                            (card.streamUrl.contains("preview.php") || card.streamUrl.contains("moviedetails") || card.id.all { c -> c.isDigit() })
                                    }
                                    if (match != null) {
                                        if (isServer2OrMunowatch && MunowatchApi.isCdnRateLimited()) {
                                            android.util.Log.w("MunowatchPlayback", "Skipping Twolekede fallback stream resolution for Server 2 because CDN is rate-limited")
                                        } else {
                                            val matchId = extractNumericId(match.streamUrl) ?: extractNumericId(match.id)
                                            val resolvedFallback = (if (matchId != null) api.resolveTwolekedeStream(matchId, resolvedServer, cleanTitle) else null)
                                                ?: api.resolveTwolekedeStream(match.streamUrl, resolvedServer, cleanTitle)
                                                ?: api.resolveTwolekedeStream(match.id, resolvedServer, cleanTitle)
                                            if (!resolvedFallback.isNullOrBlank() && MunowatchApi.isValidStreamUrl(resolvedFallback)) {
                                                val cleanFallback = resolvedFallback.replace("&amp;", "&").replace("&#38;", "&").trim()
                                                val normalizedFallback = NetworkClient.normalizeStreamUrl(cleanFallback)
                                                source = PlaybackSource(
                                                    url = normalizedFallback,
                                                    type = if (normalizedFallback.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                                                    quality = determineStreamQuality(cleanFallback, "1080p Full HD"),
                                                    serverName = resolvedServer,
                                                    headers = mapOf(
                                                        "Referer" to "https://twolekede.com/",
                                                        "User-Agent" to NetworkClient.USER_AGENT,
                                                        "Accept" to "*/*"
                                                    )
                                                )
                                                if (matchId != null) {
                                                    MediaCache.putStreamPath(contentId, "https://twolekede.com/preview.php?id=$matchId")
                                                }
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        val finalSourceUrl = source?.url?.replace("&amp;", "&")?.replace("&#38;", "&")?.trim()
        android.util.Log.d("MunowatchPlayback", "finalSourceUrl=$finalSourceUrl, sourceNull=${source == null}")
        if (source == null || finalSourceUrl.isNullOrBlank() || !MunowatchApi.isValidStreamUrl(finalSourceUrl)) {
            val fbSource = resolveMunowatchNativeStream()
            if (fbSource != null && MunowatchApi.isValidStreamUrl(fbSource.url)) {
                MediaCache.cachePlaybackSource(contentId, fbSource)
                android.util.Log.d("MunowatchPlayback", "Munowatch website stream fallback succeeded: ${fbSource.url}")
                return Result.success(fbSource)
            }
            android.util.Log.e("MunowatchPlayback", "Playback resolution failed for $contentId")
            return Result.failure(
                AppError.PlaybackError("This title is currently not available on media servers. Please select another title or try again.")
            )
        }

        if (activeTitlePrefix.isBlank()) {
            val cachedItem = MediaCache.get(contentId)
            if (cachedItem != null && cachedItem.title.isNotBlank()) {
                activeTitlePrefix = cachedItem.title
            }
        }

        val rawUrl = finalSourceUrl
        val isTwolekedeUrl = rawUrl.contains("twolekede.com") || rawUrl.contains("movviez.com")
        val finalHeaders = if (isTwolekedeUrl) {
            mapOf("Referer" to "https://twolekede.com/", "User-Agent" to NetworkClient.USER_AGENT)
        } else {
            val baseHeaders = source.headers.filterKeys { !it.equals("Origin", ignoreCase = true) && !it.equals("X-Title", ignoreCase = true) }.toMutableMap()
            if (cookie.isNotBlank()) {
                baseHeaders["Cookie"] = "PHPSESSID=$cookie"
            }
            baseHeaders["Referer"] = "https://munowatch.com/"
            baseHeaders["User-Agent"] = NetworkClient.USER_AGENT
            baseHeaders
        }
        val cleanedSource = source.copy(url = rawUrl, headers = finalHeaders)

        val finalSource = cleanedSource

        // 4. Update title and description in MediaCache if available from watch page
        val movieTitle = finalSource.headers["X-Title"]
        val existing = MediaCache.get(contentId)
        if (existing != null) {
            val titleToSave = if (existing.title.isNotBlank() && !existing.title.startsWith("Movie ", ignoreCase = true) && !existing.title.startsWith("Series ", ignoreCase = true)) {
                existing.title
            } else if (!movieTitle.isNullOrBlank() && !movieTitle.startsWith("Movie ", ignoreCase = true)) {
                parser.cleanTitle(movieTitle)
            } else {
                existing.title
            }
            val descriptionToSave = if (existing.description.isNotBlank() && !parser.isPlaceholderOrGenericDescription(existing.description)) {
                existing.description
            } else {
                val extracted = parser.extractMovieDescriptionFromWatchPage(playHtml)
                val pageTitle = if (playHtml.isNotBlank()) parser.extractMovieTitleFromWatchPage(playHtml) else movieTitle
                val titleMatches = !pageTitle.isNullOrBlank() && parser.titlesMatch(pageTitle, titleToSave)
                if (titleMatches && extracted.isNotBlank() && !parser.isPlaceholderOrGenericDescription(extracted)) extracted else existing.description
            }
            MediaCache.put(existing.copy(title = titleToSave, description = descriptionToSave))
        }

        MediaCache.cachePlaybackSource(contentId, finalSource)
        return Result.success(finalSource)
    }

    override suspend fun saveWatchProgress(item: ContinueWatchingItem) {
        if (item.positionMs > 1000L) {
            MediaCache.saveWatchPosition(item.contentId, item.positionMs)
        }
        database.watchHistoryDao().insertOrUpdate(
            WatchHistoryEntity(
                contentId = item.contentId,
                title = item.title,
                posterUrl = item.posterUrl,
                isSeries = item.isSeries,
                episodeId = item.episodeId,
                episodeTitle = item.episodeTitle,
                positionMs = item.positionMs,
                durationMs = item.durationMs,
                lastWatchedTimestamp = item.lastWatchedTimestamp
            )
        )
    }

    override suspend fun getContinueWatching(): Flow<List<ContinueWatchingItem>> {
        return database.watchHistoryDao().getAllWatchHistory().map { list ->
            list.map { entity ->
                ContinueWatchingItem(
                    contentId = entity.contentId,
                    title = entity.title,
                    posterUrl = entity.posterUrl,
                    isSeries = entity.isSeries,
                    episodeId = entity.episodeId,
                    episodeTitle = entity.episodeTitle,
                    positionMs = entity.positionMs,
                    durationMs = entity.durationMs,
                    lastWatchedTimestamp = entity.lastWatchedTimestamp
                )
            }
        }
    }

    override suspend fun getSavedPosition(contentId: String): Long {
        val cachedPos = MediaCache.getSavedWatchPosition(contentId)
        if (cachedPos != null && cachedPos > 1000L) {
            return cachedPos
        }
        val entity = database.watchHistoryDao().getWatchHistoryById(contentId)
            ?: extractNumericId(contentId)?.let { database.watchHistoryDao().getWatchHistoryById(it) }
        if (entity != null) {
            val pos = entity.positionMs
            val dur = entity.durationMs
            if (dur > 0 && pos >= (dur - 10000L)) {
                return 0L
            }
            if (pos > 1000L) {
                MediaCache.saveWatchPosition(contentId, pos)
                return pos
            }
        }
        return 0L
    }

    override suspend fun removeWatchProgress(contentId: String) {
        database.watchHistoryDao().deleteByContentId(contentId)
    }

    override suspend fun evictPlaybackSource(contentId: String) {
        MediaCache.evictPlaybackSource(contentId)
    }
}

class MunowatchRepositoryImpl(
    private val context: Context,
    private val api: MunowatchApi,
    private val sessionManager: SecureSessionManager,
    private val database: AppDatabase,
    private val parser: MunowatchHtmlParser = MunowatchHtmlParser()
) : MunowatchRepository {

    override suspend fun getCategories(): Result<Pair<List<CategoryItem>, List<CategoryItem>>> {
        var genres = emptyList<CategoryItem>()
        var vjs = emptyList<CategoryItem>()

        // Priority 1: Fetch directly from Twolekede media server (no session cookie required)
        try {
            val twolekedeGenreHtml = api.getTwolekedeGenresPage().getOrNull().orEmpty()
            if (twolekedeGenreHtml.isNotBlank()) {
                genres = parser.parseTwolekedeGenres(twolekedeGenreHtml)
            }
        } catch (_: Exception) {}

        try {
            val twolekedeVjHtml = api.getTwolekedeCategoriesPage().getOrNull().orEmpty()
            if (twolekedeVjHtml.isNotBlank()) {
                vjs = parser.parseTwolekedeCategories(twolekedeVjHtml)
            }
        } catch (_: Exception) {}

        // Priority 2: Fallback to Munowatch browse categories if media server returned empty
        if (genres.isEmpty() || vjs.isEmpty()) {
            val munoRes = api.getBrowseCategories()
            if (genres.isEmpty()) {
                genres = munoRes.getOrNull()?.genre?.map {
                    CategoryItem(
                        id = it.id,
                        name = it.name.trim(),
                        iconUrl = it.icon,
                        pipe = it.pipe,
                        type = CategoryType.GENRE
                    )
                }?.filter { it.name.isNotBlank() }.orEmpty()
            }
            if (vjs.isEmpty()) {
                vjs = munoRes.getOrNull()?.vj?.map {
                    CategoryItem(
                        id = it.id,
                        name = it.name.trim(),
                        iconUrl = it.icon,
                        pipe = it.pipe,
                        type = CategoryType.VJ
                    )
                }?.filter { it.name.isNotBlank() }.orEmpty()
            }
        }

        // Final safety net to default constants
        if (genres.isEmpty()) {
            genres = com.example.domain.model.DEFAULT_GENRE_CATEGORIES
        }
        if (vjs.isEmpty()) {
            vjs = com.example.domain.model.DEFAULT_VJ_CATEGORIES
        }

        MediaCache.setCategories(genres, vjs)
        return Result.success(Pair(genres, vjs))
    }

    override suspend fun getRemainingSubscriptionDays(): Result<Int?> {
        return api.getRemainingDays()
    }

    override suspend fun getDiagnostics(): DiagnosticsInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val hasNetwork = cm?.activeNetwork?.let { network ->
            cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false

        val serverOk = api.checkServerReachable()
        val sessionState = sessionManager.getSessionState()

        return DiagnosticsInfo(
            networkReachable = hasNetwork,
            munowatchReachable = serverOk,
            sessionState = sessionState,
            lastHttpStatus = if (serverOk) 200 else 0,
            media3Version = "1.5.1 (ExoPlayer)",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }

    override suspend fun toggleFavorite(item: MediaItem) {
        val dao = database.favoriteDao()
        if (dao.hasFavorite(item.id)) {
            dao.deleteById(item.id)
        } else {
            dao.insert(
                FavoriteEntity(
                    id = item.id,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl,
                    year = item.year,
                    genre = item.genre,
                    vj = item.vj,
                    isSeries = item.isSeries,
                    addedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    override fun isFavorite(id: String): Flow<Boolean> {
        return database.favoriteDao().isFavorite(id)
    }

    override fun getFavorites(): Flow<List<MediaItem>> {
        return database.favoriteDao().getAllFavorites().map { list ->
            list.map { entity ->
                MediaItem(
                    id = entity.id,
                    title = entity.title,
                    posterUrl = entity.posterUrl,
                    backdropUrl = entity.backdropUrl,
                    year = entity.year,
                    genre = entity.genre,
                    vj = entity.vj,
                    isSeries = entity.isSeries
                )
            }
        }
    }
}
