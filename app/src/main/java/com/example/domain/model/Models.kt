package com.example.domain.model

data class MediaItem(
    val id: String,
    val title: String,
    val description: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val year: String = "",
    val rating: String = "",
    val genre: String = "",
    val vj: String = "",
    val isSeries: Boolean = false,
    val runtime: String = "",
    val quality: String = "HD",
    val availableServers: List<String> = listOf("Server 1 (Fast CDN)", "Server 2 (Backup)", "Server 3 (Direct)"),
    val streamUrl: String = ""
)

data class CategoryItem(
    val id: Int,
    val name: String,
    val iconUrl: String = "",
    val pipe: String = "g",
    val type: CategoryType
)

enum class CategoryType {
    GENRE,
    VJ
}

val DEFAULT_GENRE_CATEGORIES = listOf(
    CategoryItem(id = 1, name = "Action", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 17, name = "Drama", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 9, name = "Comedy", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 2, name = "Horror", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 8, name = "Love Story", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 7, name = "Adventure", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 14, name = "Sci-Fi", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 19, name = "Thriller", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 12, name = "Crime", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 20, name = "Animation", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 13, name = "Family", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 16, name = "Martial Arts", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 15, name = "Romance", type = CategoryType.GENRE, pipe = "g"),
    CategoryItem(id = 18, name = "Sport", type = CategoryType.GENRE, pipe = "g")
)

val DEFAULT_VJ_CATEGORIES = listOf(
    CategoryItem(id = 1, name = "VJ Junior", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 2, name = "VJ Emmy", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 9, name = "VJ ICEP", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 8, name = "VJ Jingo", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 38, name = "VJ Ulio", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 57, name = "VJ Soul", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 4, name = "VJ Tom", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 5, name = "VJ Shao Khan", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 26, name = "VJ Sammy", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 14, name = "VJ Kevo", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 16, name = "VJ Kevin", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 24, name = "VJ HD", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 25, name = "VJ Dan De", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 101, name = "VJ Tonny", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 102, name = "VJ Muba", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 103, name = "VJ Lance", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 104, name = "VJ Freddie", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 105, name = "VJ Waza", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 106, name = "VJ Ronnage", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 107, name = "VJ Mark", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 108, name = "VJ Mosco", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 109, name = "VJ KS", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 110, name = "VJ Eddy", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 111, name = "VJ Shield", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 112, name = "VJ Banks", type = CategoryType.VJ, pipe = "vj"),
    CategoryItem(id = 113, name = "VJ Ronnie", type = CategoryType.VJ, pipe = "vj")
)

data class ContinueWatchingItem(
    val contentId: String,
    val title: String,
    val posterUrl: String,
    val isSeries: Boolean = false,
    val episodeId: String = "",
    val episodeTitle: String = "",
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class PlaybackSource(
    val url: String,
    val type: String = "video/mp4", // "application/x-mpegURL" or "video/mp4"
    val quality: String = "Auto",
    val serverName: String = "Server 1",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList()
)

data class SubtitleTrack(
    val language: String,
    val label: String,
    val url: String,
    val mimeType: String = "text/vtt"
)

data class SeriesDetail(
    val id: String,
    val title: String,
    val description: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val year: String = "",
    val vj: String = "",
    val genres: List<String> = emptyList(),
    val seasons: List<Season> = emptyList()
)

data class Season(
    val number: Int,
    val title: String,
    val episodes: List<Episode>
)

data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val description: String = "",
    val thumbnail: String = "",
    val duration: String = "",
    val streamUrl: String = "",
    val servers: List<String> = listOf("Server 1", "Server 2", "Server 3")
)

data class DiagnosticsInfo(
    val networkReachable: Boolean,
    val munowatchReachable: Boolean,
    val sessionState: String, // AUTHENTICATED, EXPIRED, NOT_CONFIGURED
    val lastHttpStatus: Int,
    val media3Version: String,
    val deviceModel: String,
    val androidVersion: String
)

sealed class AppError(override val message: String) : Exception(message) {
    data class NetworkError(override val message: String = "Network connection failed. Please verify your internet connection.") : AppError(message)
    data class SessionExpired(override val message: String = "Direct media streaming server connection refreshed.") : AppError(message)
    data class AuthenticationError(override val message: String = "Connection failed. Please check network settings.") : AppError(message)
    data class ServerError(val statusCode: Int, override val message: String = "Munowatch server error ($statusCode). Please try again shortly.") : AppError(message)
    data class PlaybackError(override val message: String = "Playback could not be loaded on this streaming server.") : AppError(message)
    data class ParseError(override val message: String = "Unable to process content data from Munowatch.") : AppError(message)
    data class UnknownError(override val message: String = "An unexpected error occurred.") : AppError(message)
}
