package com.example.domain.repository

import com.example.domain.model.AppError
import com.example.domain.model.CategoryItem
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.DiagnosticsInfo
import com.example.domain.model.MediaItem
import com.example.domain.model.PlaybackSource
import com.example.domain.model.SeriesDetail
import kotlinx.coroutines.flow.Flow

interface AuthenticationRepository {
    fun hasStoredSession(): Boolean
    fun getStoredSessionId(): String?
    suspend fun login(email: String, password: String): Result<Boolean>
    suspend fun validateSession(sessionId: String): Result<Boolean>
    suspend fun saveSession(sessionId: String)
    suspend fun clearSession()
    fun observeSessionStatus(): Flow<String>
}

interface MovieRepository {
    suspend fun getFeaturedHero(): Result<MediaItem?>
    suspend fun getLatestMovies(lastFetchId: String? = null, page: Int? = null): Result<List<MediaItem>>
    suspend fun getFreeMovies(lastFetchId: String? = null, page: Int? = null): Result<List<MediaItem>>
    suspend fun getMoviesByGenre(
        genreId: Int,
        lastFetchId: String? = null,
        pipe: String? = null,
        page: Int? = null
    ): Result<List<MediaItem>>
    suspend fun getMoviesByVj(
        vjId: Int,
        lastFetchId: String? = null,
        pipe: String? = null,
        page: Int? = null
    ): Result<List<MediaItem>>
    suspend fun getMoviesByVjAndGenre(
        vjId: Int,
        genreId: Int,
        lastFetchId: String? = null,
        pipe: String? = null,
        page: Int? = null
    ): Result<List<MediaItem>> = Result.success(emptyList())
    suspend fun getFilteredMovies(
        year: String? = null,
        category: String? = null,
        vj: String? = null,
        page: Int = 1,
        perPage: Int = 20
    ): Result<List<MediaItem>> = Result.success(emptyList())
    suspend fun getMovieDetails(id: String): Result<MediaItem>
}

interface SeriesRepository {
    suspend fun getPopularSeries(lastFetchId: String? = null, page: Int? = null): Result<List<MediaItem>>
    suspend fun getFilteredSeries(
        year: String? = null,
        category: String? = null,
        vj: String? = null,
        page: Int = 1,
        perPage: Int = 20
    ): Result<List<MediaItem>> = Result.success(emptyList())
    suspend fun getSeriesDetails(id: String): Result<SeriesDetail>
}

interface SearchRepository {
    suspend fun searchContent(query: String): Result<List<MediaItem>>
}

interface PlaybackRepository {
    suspend fun getPlaybackSource(contentId: String, serverName: String? = null): Result<PlaybackSource>
    suspend fun saveWatchProgress(item: ContinueWatchingItem)
    suspend fun getContinueWatching(): Flow<List<ContinueWatchingItem>>
    suspend fun removeWatchProgress(contentId: String)
    suspend fun evictPlaybackSource(contentId: String)
    suspend fun getSavedPosition(contentId: String): Long
}

interface MunowatchRepository {
    suspend fun getCategories(): Result<Pair<List<CategoryItem>, List<CategoryItem>>> // Pair(Genres, Vjs)
    suspend fun getRemainingSubscriptionDays(): Result<Int?>
    suspend fun getDiagnostics(): DiagnosticsInfo
    suspend fun toggleFavorite(item: MediaItem)
    fun isFavorite(id: String): Flow<Boolean>
    fun getFavorites(): Flow<List<MediaItem>>
}
