package com.example.data.repository

import android.content.Context
import com.example.domain.model.Episode
import com.example.domain.model.MediaItem
import com.example.domain.model.SeriesDetail
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MediaCache {
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val itemsById = ConcurrentHashMap<String, MediaItem>()
    private val streamPathsById = ConcurrentHashMap<String, String>()
    private val seriesDetailsById = ConcurrentHashMap<String, SeriesDetail>()
    private val movieDetailsById = ConcurrentHashMap<String, MediaItem>()
    private val playbackSourceCache = ConcurrentHashMap<String, com.example.domain.model.PlaybackSource>()
    private val playbackSourceCachedAt = ConcurrentHashMap<String, Long>()
    private val watchProgressByContentId = ConcurrentHashMap<String, Long>()

    @Volatile
    private var popularSeriesCache: List<MediaItem> = emptyList()

    @Volatile
    private var popularMoviesCache: List<MediaItem> = emptyList()

    @Volatile
    private var freeMoviesCache: List<MediaItem> = emptyList()

    @Volatile
    private var preferredStreamQuality: String = "AUTO"

    fun getPreferredStreamQuality(): String = preferredStreamQuality

    fun setPreferredStreamQuality(quality: String) {
        preferredStreamQuality = quality
    }

    private val genreMoviesCache = ConcurrentHashMap<Int, List<MediaItem>>()
    private val vjMoviesCache = ConcurrentHashMap<Int, List<MediaItem>>()
    private val vjGenreMoviesCache = ConcurrentHashMap<String, List<MediaItem>>()
    private val yearMoviesCache = ConcurrentHashMap<String, List<MediaItem>>()

    @Volatile
    private var cachedVjCategories: List<com.example.domain.model.CategoryItem> = emptyList()

    @Volatile
    private var cachedGenreCategories: List<com.example.domain.model.CategoryItem> = emptyList()

    private val vjNameById = ConcurrentHashMap<Int, String>()
    private val genreNameById = ConcurrentHashMap<Int, String>()
    private val vjIdByName = ConcurrentHashMap<String, Int>()
    private val genreIdByName = ConcurrentHashMap<String, Int>()
    private val slugsById = ConcurrentHashMap<String, String>()
    private val munoIdsById = ConcurrentHashMap<String, String>()

    private var diskCacheDir: File? = null

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val seriesDetailAdapter by lazy {
        moshi.adapter(SeriesDetail::class.java)
    }

    private val movieDetailAdapter by lazy {
        moshi.adapter(MediaItem::class.java)
    }

    private val mediaItemListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, MediaItem::class.java)
        moshi.adapter<List<MediaItem>>(type)
    }

    fun init(context: Context) {
        setCategories(com.example.domain.model.DEFAULT_GENRE_CATEGORIES, com.example.domain.model.DEFAULT_VJ_CATEGORIES)
        if (diskCacheDir != null) return
        val dir = File(context.cacheDir, "munowatch_media_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        diskCacheDir = dir

        // Synchronously load persisted catalogs (popular movies, series, free movies, categories)
        // This takes < 2ms and ensures instant UI rendering with zero spinner on app launch and tab switch.
        loadCatalogsFromDisk(dir)

        // Asynchronously preload individual series and movie details in background
        cacheScope.launch {
            try {
                loadAllSeriesDetailsFromDisk(dir)
                loadAllMovieDetailsFromDisk(dir)
            } catch (_: Exception) {}
        }
    }

    private fun loadCatalogsFromDisk(dir: File) {
        try {
            val seriesFile = File(dir, "popular_series.json")
            if (seriesFile.exists() && seriesFile.length() > 0) {
                val json = seriesFile.readText()
                val list = mediaItemListAdapter.fromJson(json)
                if (!list.isNullOrEmpty()) {
                    popularSeriesCache = list
                    putAll(list)
                }
            }

            val moviesFile = File(dir, "popular_movies.json")
            if (moviesFile.exists() && moviesFile.length() > 0) {
                val json = moviesFile.readText()
                val list = mediaItemListAdapter.fromJson(json)
                if (!list.isNullOrEmpty()) {
                    popularMoviesCache = list
                    putAll(list)
                }
            }

            val freeFile = File(dir, "free_movies.json")
            if (freeFile.exists() && freeFile.length() > 0) {
                val json = freeFile.readText()
                val list = mediaItemListAdapter.fromJson(json)
                if (!list.isNullOrEmpty()) {
                    freeMoviesCache = list
                    putAll(list)
                }
            }

            // Load persisted genre and vj catalogs from disk
            val genreFiles = dir.listFiles { file -> file.name.startsWith("genre_") && file.name.endsWith(".json") }
            genreFiles?.forEach { file ->
                val gId = file.name.removePrefix("genre_").removeSuffix(".json").toIntOrNull()
                if (gId != null && file.length() > 0) {
                    try {
                        val list = mediaItemListAdapter.fromJson(file.readText())
                        if (!list.isNullOrEmpty()) {
                            genreMoviesCache[gId] = list
                            putAll(list)
                        }
                    } catch (_: Exception) {}
                }
            }

            val vjFiles = dir.listFiles { file -> file.name.startsWith("vj_") && file.name.endsWith(".json") }
            vjFiles?.forEach { file ->
                val vId = file.name.removePrefix("vj_").removeSuffix(".json").toIntOrNull()
                if (vId != null && file.length() > 0) {
                    try {
                        val list = mediaItemListAdapter.fromJson(file.readText())
                        if (!list.isNullOrEmpty()) {
                            vjMoviesCache[vId] = list
                            putAll(list)
                        }
                    } catch (_: Exception) {}
                }
            }

            val yearFiles = dir.listFiles { file -> file.name.startsWith("year_") && file.name.endsWith(".json") }
            yearFiles?.forEach { file ->
                val y = file.name.removePrefix("year_").removeSuffix(".json")
                if (y.isNotBlank() && file.length() > 0) {
                    try {
                        val list = mediaItemListAdapter.fromJson(file.readText())
                        if (!list.isNullOrEmpty()) {
                            yearMoviesCache[y] = list
                            putAll(list)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun loadAllSeriesDetailsFromDisk(dir: File) {
        try {
            val files = dir.listFiles { file -> file.name.startsWith("series_") && file.name.endsWith(".json") }
            files?.forEach { file ->
                try {
                    val json = file.readText()
                    val detail = seriesDetailAdapter.fromJson(json)
                    if (detail != null && detail.seasons.any { it.episodes.isNotEmpty() }) {
                        cacheSeriesDetailInMemory(detail)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun loadAllMovieDetailsFromDisk(dir: File) {
        try {
            val files = dir.listFiles { file -> file.name.startsWith("movie_") && file.name.endsWith(".json") }
            files?.forEach { file ->
                try {
                    val json = file.readText()
                    val movie = movieDetailAdapter.fromJson(json)
                    if (movie != null) {
                        val isCorrupt = movie.id.isBlank() ||
                            movie.id.endsWith(".php") ||
                            movie.id.contains("moviedetails.php") ||
                            movie.title.isBlank() ||
                            movie.title.startsWith("Movie #", ignoreCase = true) ||
                            movie.description.contains("All rights reserved", ignoreCase = true) ||
                            movie.description.contains("&copy;", ignoreCase = true)
                        val isPlaceholder = movie.description.contains("translated in Luganda by top Veejays", ignoreCase = true) ||
                            movie.description.contains("exclusively on Munowatch TV", ignoreCase = true)
                        if (isCorrupt || isPlaceholder) {
                            file.delete()
                        } else {
                            cacheMovieDetailInMemory(movie)
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun put(item: MediaItem) {
        val existing = itemsById[item.id]
        val effectiveItem = if (existing != null) {
            existing.copy(
                title = if (item.title.isNotBlank()) item.title else existing.title,
                vj = if (item.vj.isNotBlank() && !item.vj.equals("Unknown", ignoreCase = true)) item.vj else existing.vj,
                year = if (item.year.isNotBlank()) item.year else existing.year,
                genre = if (item.genre.isNotBlank() && !item.genre.equals("Movie", ignoreCase = true) && !item.genre.equals("Uncategorized", ignoreCase = true)) item.genre else existing.genre,
                posterUrl = if (item.posterUrl.isNotBlank() && !item.posterUrl.contains("placeholder")) item.posterUrl else existing.posterUrl,
                streamUrl = if (item.streamUrl.isNotBlank()) item.streamUrl else existing.streamUrl,
                description = if (item.description.isNotBlank() && !item.description.contains("translated in Luganda by top Veejays")) item.description else existing.description
            )
        } else {
            item
        }

        itemsById[effectiveItem.id] = effectiveItem
        if (effectiveItem.id.startsWith("series_")) {
            val rawId = effectiveItem.id.removePrefix("series_")
            itemsById[rawId] = effectiveItem
        } else if (effectiveItem.isSeries) {
            itemsById["series_${effectiveItem.id}"] = effectiveItem
        }

        if (effectiveItem.streamUrl.isNotBlank() &&
            !effectiveItem.streamUrl.contains("trailer256.b-cdn.net") &&
            !effectiveItem.streamUrl.contains("intro_myvj") &&
            !effectiveItem.streamUrl.contains("/trailer") &&
            !effectiveItem.streamUrl.contains("_trailer")
        ) {
            val cleanPath = normalizePath(effectiveItem.streamUrl)
            val isGenericPath = cleanPath == "/twolekede" || cleanPath == "twolekede" ||
                cleanPath == "/watch" || cleanPath == "watch" || cleanPath == "/"
            if (!isGenericPath) {
                streamPathsById[effectiveItem.id] = cleanPath
                itemsById[cleanPath] = effectiveItem
            }

            val serieMatch = Regex("""serie_id=([0-9]+)""").find(effectiveItem.streamUrl)
            if (serieMatch != null) {
                val sid = serieMatch.groupValues[1]
                itemsById[sid] = effectiveItem
                itemsById["series_$sid"] = effectiveItem
                streamPathsById[sid] = cleanPath
                streamPathsById["series_$sid"] = cleanPath
            }

            val idMatch = Regex("""[?&](?:mid|id)=([0-9]+)""").find(effectiveItem.streamUrl)
            if (idMatch != null) {
                val mid = idMatch.groupValues[1]
                itemsById[mid] = effectiveItem
                streamPathsById[mid] = cleanPath
            }
        }

        if (effectiveItem.title.isNotBlank()) {
            val tLower = effectiveItem.title.lowercase().trim()
            val vjKey = effectiveItem.vj.lowercase().trim().replace(Regex("""[^a-z0-9]"""), "")
            if (vjKey.isNotBlank()) {
                itemsById.putIfAbsent("${tLower}_$vjKey", effectiveItem)
            }
            itemsById.putIfAbsent(tLower, effectiveItem)
            val baseName = extractBaseSeriesName(effectiveItem.title).lowercase().trim()
            if (baseName.isNotBlank()) {
                if (vjKey.isNotBlank()) {
                    itemsById.putIfAbsent("${baseName}_$vjKey", effectiveItem)
                }
                itemsById.putIfAbsent(baseName, effectiveItem)
            }
        }
    }

    fun putAll(items: List<MediaItem>) {
        items.forEach { put(it) }
    }

    fun extractBaseSeriesName(title: String): String {
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

    private fun generateAliases(idOrTitle: String, knownTitle: String? = null): Set<String> {
        val aliases = mutableSetOf<String>()
        val raw = idOrTitle.trim()
        if (raw.isNotBlank()) {
            aliases.add(raw)
            aliases.add(raw.lowercase())
            val noPrefix = raw.removePrefix("series_").removePrefix("movie_")
            aliases.add(noPrefix)
            aliases.add(noPrefix.lowercase())
            aliases.add("series_$noPrefix")

            if (raw.contains("title=")) {
                Regex("""[?&]title=([^&]+)""").find(raw)?.groupValues?.get(1)?.let {
                    val decoded = try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                    aliases.add(decoded)
                    aliases.add(decoded.lowercase())
                    val decSlug = decoded.lowercase().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
                    aliases.add(decSlug)
                    aliases.add("series_$decSlug")
                }
            }

            val slug = noPrefix.lowercase().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
            if (slug.isNotBlank()) {
                aliases.add(slug)
                aliases.add("series_$slug")
            }

            if (noPrefix.contains("vj", ignoreCase = true)) {
                val withoutVj = noPrefix.replace(Regex("""(?i)[_\s\-]*vj[_\s\-]+[a-z0-9]+.*$"""), "").trim(' ', '_', '-')
                if (withoutVj.isNotBlank()) {
                    aliases.add(withoutVj)
                    aliases.add(withoutVj.lowercase())
                    val noVjSlug = withoutVj.lowercase().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
                    aliases.add(noVjSlug)
                    aliases.add("series_$noVjSlug")
                }
            }
        }

        if (!knownTitle.isNullOrBlank()) {
            val t = knownTitle.trim()
            aliases.add(t)
            aliases.add(t.lowercase())
            val tSlug = t.lowercase().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
            aliases.add(tSlug)
            aliases.add("series_$tSlug")

            val baseName = extractBaseSeriesName(t)
            if (baseName.isNotBlank()) {
                aliases.add(baseName)
                aliases.add(baseName.lowercase())
                val baseSlug = baseName.lowercase().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
                aliases.add(baseSlug)
                aliases.add("series_$baseSlug")
            }
        }
        return aliases
    }

    fun normalizeShiftedSeasons(detail: SeriesDetail): SeriesDetail {
        var changed = false
        val newSeasons = detail.seasons.map { season ->
            val rawNums = season.episodes.map { it.number }
            val isSingleGapShifted = if (rawNums.size >= 2) {
                val minNum = rawNums.minOrNull() ?: 1
                val maxNum = rawNums.maxOrNull() ?: 1
                if (minNum == 1 && maxNum == rawNums.size + 1) {
                    val isIncreasing = rawNums.zipWithNext().all { (a, b) -> a < b }
                    if (isIncreasing) {
                        val gapIdx = rawNums.indexOfFirst { num -> num != rawNums.indexOf(num) + 1 }
                        gapIdx > 0 && rawNums.drop(gapIdx).mapIndexed { idx, n -> n == (gapIdx + idx + 2) }.all { it }
                    } else false
                } else false
            } else false

            if (isSingleGapShifted) {
                changed = true
                val updatedEpisodes = season.episodes.mapIndexed { idx, ep ->
                    val finalNumber = idx + 1
                    val oldPadded = String.format("%02d", ep.number)
                    val newPadded = String.format("%02d", finalNumber)
                    val adjustedTitle = ep.title
                        .replace(Regex("""(?i)\bE\s*$oldPadded\b"""), "E$newPadded")
                        .replace(Regex("""(?i)\bE\s*${ep.number}\b"""), "E$finalNumber")
                        .replace(Regex("""(?i)\bEpisode\s*${ep.number}\b"""), "Episode $finalNumber")
                        .replace(Regex("""(?i)\bEPS\s*${ep.number}\b"""), "EPS $finalNumber")
                    ep.copy(
                        number = finalNumber,
                        title = adjustedTitle
                    )
                }
                season.copy(episodes = updatedEpisodes)
            } else {
                season
            }
        }
        return if (changed) detail.copy(seasons = newSeasons) else detail
    }

    private fun cacheSeriesDetailInMemory(rawDetail: SeriesDetail) {
        val seriesDetail = normalizeShiftedSeasons(rawDetail)
        val allAliases = generateAliases(seriesDetail.id, seriesDetail.title)
        for (alias in allAliases) {
            seriesDetailsById[alias] = seriesDetail
        }

        // Register all parsed episodes into MediaCache for instant playback resolution
        seriesDetail.seasons.forEach { season ->
            season.episodes.forEach { ep ->
                registerEpisode(ep, seriesDetail.posterUrl, seriesDetail.title)
            }
        }
    }

    fun putSeriesDetail(seriesDetail: SeriesDetail) {
        val normalized = normalizeShiftedSeasons(seriesDetail)
        cacheSeriesDetailInMemory(normalized)

        // Asynchronously persist to disk cache
        val dir = diskCacheDir
        if (dir != null) {
            cacheScope.launch {
                try {
                    val cleanId = normalized.id.removePrefix("series_").replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
                    val file = File(dir, "series_$cleanId.json")
                    val json = seriesDetailAdapter.toJson(normalized)
                    file.writeText(json)
                } catch (_: Exception) {}
            }
        }
    }

    fun getSeriesDetail(idOrSlug: String): SeriesDetail? {
        val aliases = generateAliases(idOrSlug)
        for (alias in aliases) {
            val item = seriesDetailsById[alias]
            if (item != null && item.seasons.any { it.episodes.isNotEmpty() }) {
                return normalizeShiftedSeasons(item)
            }
        }

        // Try reading from disk cache on memory miss
        val dir = diskCacheDir
        if (dir != null) {
            val cleanId = idOrSlug.removePrefix("series_").replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
            val candidateFileNames = aliases.map { a -> "series_${a.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")}.json" }.distinct()
            for (fName in candidateFileNames) {
                val f = File(dir, fName)
                if (f.exists() && f.length() > 0) {
                    try {
                        val json = f.readText()
                        val detail = seriesDetailAdapter.fromJson(json)
                        if (detail != null && detail.seasons.any { it.episodes.isNotEmpty() }) {
                            val norm = normalizeShiftedSeasons(detail)
                            cacheSeriesDetailInMemory(norm)
                            return norm
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        return null
    }

    fun hasSeriesDetail(idOrSlug: String): Boolean {
        val detail = getSeriesDetail(idOrSlug)
        return detail != null && detail.seasons.any { it.episodes.isNotEmpty() }
    }

    private fun cacheMovieDetailInMemory(movie: MediaItem) {
        movieDetailsById[movie.id] = movie
        val cleanId = movie.id.removePrefix("movie_")
        movieDetailsById[cleanId] = movie
        movieDetailsById["movie_$cleanId"] = movie
        val slug = movie.title.lowercase().trim().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
        val vjSlug = movie.vj.lowercase().trim().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
        if (slug.isNotBlank()) {
            if (vjSlug.isNotBlank()) {
                movieDetailsById["${slug}_$vjSlug"] = movie
                movieDetailsById["movie_${slug}_$vjSlug"] = movie
            }
            movieDetailsById.putIfAbsent(slug, movie)
            movieDetailsById.putIfAbsent("movie_$slug", movie)
        }
        put(movie)
    }

    fun putMovieDetail(movie: MediaItem) {
        cacheMovieDetailInMemory(movie)
        val dir = diskCacheDir
        if (dir != null) {
            cacheScope.launch {
                try {
                    val cleanId = movie.id.removePrefix("movie_").replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
                    val file = File(dir, "movie_$cleanId.json")
                    val json = movieDetailAdapter.toJson(movie)
                    file.writeText(json)
                } catch (_: Exception) {}
            }
        }
    }

    fun getMovieDetail(idOrSlug: String): MediaItem? {
        val cleanId = idOrSlug.removePrefix("movie_")
        val slug = idOrSlug.lowercase().trim().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
        val inMem = movieDetailsById[idOrSlug]
            ?: movieDetailsById[cleanId]
            ?: movieDetailsById["movie_$cleanId"]
            ?: (if (slug.isNotBlank()) movieDetailsById[slug] ?: movieDetailsById["movie_$slug"] else null)
            ?: itemsById[idOrSlug]?.takeIf { it.description.isNotBlank() }
            ?: itemsById[cleanId]?.takeIf { it.description.isNotBlank() }

        if (inMem != null) return inMem

        val dir = diskCacheDir
        if (dir != null) {
            val safeId = cleanId.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
            val candidateFiles = listOfNotNull(
                File(dir, "movie_$safeId.json").takeIf { it.exists() },
                if (slug.isNotBlank()) File(dir, "movie_$slug.json").takeIf { it.exists() } else null
            )
            for (file in candidateFiles) {
                try {
                    val json = file.readText()
                    val movie = movieDetailAdapter.fromJson(json)
                    if (movie != null) {
                        cacheMovieDetailInMemory(movie)
                        return movie
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    fun putSlug(id: String, slug: String) {
        if (id.isNotBlank() && slug.isNotBlank()) {
            slugsById[id] = slug
            if (id.startsWith("myvj_")) {
                slugsById[id.removePrefix("myvj_")] = slug
            } else {
                slugsById["myvj_$id"] = slug
            }
        }
    }

    fun getSlug(id: String): String? {
        return slugsById[id]
            ?: (if (id.startsWith("myvj_")) slugsById[id.removePrefix("myvj_")] else slugsById["myvj_$id"])
    }

    fun putMunoId(id: String, munoId: String) {
        if (id.isNotBlank() && munoId.isNotBlank()) {
            munoIdsById[id] = munoId
            if (id.startsWith("myvj_")) {
                munoIdsById[id.removePrefix("myvj_")] = munoId
            } else {
                munoIdsById["myvj_$id"] = munoId
            }
        }
    }

    fun getMunoId(id: String): String? {
        return munoIdsById[id]
            ?: (if (id.startsWith("myvj_")) munoIdsById[id.removePrefix("myvj_")] else munoIdsById["myvj_$id"])
    }

    fun cachePlaybackSource(contentId: String, source: com.example.domain.model.PlaybackSource) {
        if (source.url.contains("trailer256.b-cdn.net") ||
            source.url.contains("intro_myvj") ||
            source.url.contains("/trailer") ||
            source.url.contains("_trailer")
        ) {
            return
        }
        val clean = normalizePath(contentId)
        if (clean.isBlank() || clean == "/twolekede" || clean == "twolekede" ||
            clean == "/watch" || clean == "watch" || clean.startsWith("item_")
        ) {
            return
        }
        val now = System.currentTimeMillis()
        val server = source.serverName ?: "default"
        fun store(key: String) {
            playbackSourceCache[key] = source
            playbackSourceCachedAt[key] = now
        }
        store("${contentId}_$server")
        store(contentId)
        store(clean)
        store("${clean}_$server")
        if (!contentId.startsWith("myvj_") && !contentId.startsWith("series_")) {
            extractNumericId(contentId)?.let {
                store(it)
                store("${it}_$server")
            }
        }
    }

    fun evictPlaybackSource(contentId: String) {
        playbackSourceCache.remove(contentId)
        playbackSourceCachedAt.remove(contentId)
        val clean = normalizePath(contentId)
        playbackSourceCache.remove(clean)
        playbackSourceCachedAt.remove(clean)
        val numId = if (!contentId.startsWith("myvj_")) extractNumericId(contentId) else null
        if (numId != null) {
            playbackSourceCache.remove(numId)
            playbackSourceCachedAt.remove(numId)
        }
        val keysToRemove = playbackSourceCache.keys().toList().filter { key ->
            key.contains(contentId) ||
            key.contains(clean) ||
            (numId != null && key.contains(numId))
        }
        keysToRemove.forEach {
            playbackSourceCache.remove(it)
            playbackSourceCachedAt.remove(it)
        }
    }

    fun getPlaybackSource(contentId: String, requestedServer: String? = null): com.example.domain.model.PlaybackSource? {
        val clean = normalizePath(contentId)
        val isMyVj = contentId.startsWith("myvj_")
        val numId = if (!isMyVj) extractNumericId(contentId) else null

        val candidate = if (!requestedServer.isNullOrBlank()) {
            playbackSourceCache["${contentId}_$requestedServer"]
                ?: playbackSourceCache["${clean}_$requestedServer"]
                ?: (if (numId != null) playbackSourceCache["${numId}_$requestedServer"] else null)
                ?: playbackSourceCache[contentId]?.takeIf { it.serverName.equals(requestedServer, ignoreCase = true) }
                ?: playbackSourceCache[clean]?.takeIf { it.serverName.equals(requestedServer, ignoreCase = true) }
        } else {
            playbackSourceCache[contentId]
                ?: playbackSourceCache[clean]
                ?: (if (numId != null) playbackSourceCache[numId] else null)
        }

        if (candidate == null) return null
        if (candidate.url.contains("trailer256.b-cdn.net") ||
            candidate.url.contains("intro_myvj") ||
            candidate.url.contains("/trailer") ||
            candidate.url.contains("_trailer")
        ) {
            evictPlaybackSource(contentId)
            return null
        }

        // Check if signed URL token has expired (expires=timestamp in seconds or signature= with short session TTL)
        val isSignedUrl = candidate.url.contains("signature=") ||
            candidate.url.contains("cdn.movviez.com") ||
            candidate.url.contains("expires=")
        if (isSignedUrl) {
            val keyToCheck = if (!requestedServer.isNullOrBlank()) "${contentId}_$requestedServer" else contentId
            val cachedAt = playbackSourceCachedAt[keyToCheck]
                ?: playbackSourceCachedAt[contentId]
                ?: playbackSourceCachedAt[clean]
                ?: (if (numId != null) playbackSourceCachedAt[numId] else null)
                ?: 0L
            if (System.currentTimeMillis() - cachedAt > 60_000L) {
                // Expired short session token (> 60s old)! Evict so fresh stream URL is fetched from preview.php
                evictPlaybackSource(contentId)
                return null
            }
        }

        // Check if signed URL token has explicit expired timestamp in seconds
        val expMatch = Regex("""expires=([0-9]+)""").find(candidate.url)
        if (expMatch != null) {
            val expSec = expMatch.groupValues[1].toLongOrNull()
            if (expSec != null) {
                val nowSec = System.currentTimeMillis() / 1000
                if (nowSec >= expSec - 120) {
                    // Stale / expired token! Evict so fresh stream URL is fetched
                    evictPlaybackSource(contentId)
                    return null
                }
            }
        }

        return candidate
    }

    fun getPopularSeries(): List<MediaItem> = popularSeriesCache

    fun setPopularSeries(items: List<MediaItem>) {
        if (items.isEmpty()) return
        popularSeriesCache = items
        putAll(items)
        val dir = diskCacheDir
        if (dir != null) {
            cacheScope.launch {
                try {
                    val file = File(dir, "popular_series.json")
                    file.writeText(mediaItemListAdapter.toJson(items))
                } catch (_: Exception) {}
            }
        }
    }

    fun getPopularMovies(): List<MediaItem> = popularMoviesCache

    fun setPopularMovies(items: List<MediaItem>) {
        if (items.isEmpty()) return
        popularMoviesCache = items
        putAll(items)
        val dir = diskCacheDir
        if (dir != null) {
            cacheScope.launch {
                try {
                    val file = File(dir, "popular_movies.json")
                    file.writeText(mediaItemListAdapter.toJson(items))
                } catch (_: Exception) {}
            }
        }
    }

    fun getFreeMovies(): List<MediaItem> = freeMoviesCache

    fun setFreeMovies(items: List<MediaItem>) {
        if (items.isEmpty()) return
        freeMoviesCache = items
        putAll(items)
        val dir = diskCacheDir
        if (dir != null) {
            cacheScope.launch {
                try {
                    val file = File(dir, "free_movies.json")
                    file.writeText(mediaItemListAdapter.toJson(items))
                } catch (_: Exception) {}
            }
        }
    }

    fun getGenreMovies(genreId: Int): List<MediaItem>? {
        val inMem = genreMoviesCache[genreId]
        if (!inMem.isNullOrEmpty()) return inMem
        val dir = diskCacheDir ?: return null
        val f = File(dir, "genre_${genreId}.json")
        if (f.exists() && f.length() > 0) {
            try {
                val list = mediaItemListAdapter.fromJson(f.readText())
                if (!list.isNullOrEmpty()) {
                    genreMoviesCache[genreId] = list
                    putAll(list)
                    return list
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun setGenreMovies(genreId: Int, items: List<MediaItem>) {
        if (items.isEmpty()) return
        genreMoviesCache[genreId] = items
        putAll(items)
        val dir = diskCacheDir ?: return
        cacheScope.launch {
            try {
                File(dir, "genre_${genreId}.json").writeText(mediaItemListAdapter.toJson(items))
            } catch (_: Exception) {}
        }
    }

    fun getVjMovies(vjId: Int): List<MediaItem>? {
        val inMem = vjMoviesCache[vjId]
        if (!inMem.isNullOrEmpty()) return inMem
        val dir = diskCacheDir ?: return null
        val f = File(dir, "vj_${vjId}.json")
        if (f.exists() && f.length() > 0) {
            try {
                val list = mediaItemListAdapter.fromJson(f.readText())
                if (!list.isNullOrEmpty()) {
                    vjMoviesCache[vjId] = list
                    putAll(list)
                    return list
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun setVjMovies(vjId: Int, items: List<MediaItem>) {
        if (items.isEmpty()) return
        vjMoviesCache[vjId] = items
        putAll(items)
        val dir = diskCacheDir ?: return
        cacheScope.launch {
            try {
                File(dir, "vj_${vjId}.json").writeText(mediaItemListAdapter.toJson(items))
            } catch (_: Exception) {}
        }
    }

    fun getVjGenreMovies(vjId: Int, genreId: Int): List<MediaItem>? {
        val key = "${vjId}_${genreId}"
        val inMem = vjGenreMoviesCache[key]
        if (!inMem.isNullOrEmpty()) return inMem
        val dir = diskCacheDir ?: return null
        val f = File(dir, "vj_genre_${key}.json")
        if (f.exists() && f.length() > 0) {
            try {
                val list = mediaItemListAdapter.fromJson(f.readText())
                if (!list.isNullOrEmpty()) {
                    vjGenreMoviesCache[key] = list
                    putAll(list)
                    return list
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun setVjGenreMovies(vjId: Int, genreId: Int, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val key = "${vjId}_${genreId}"
        vjGenreMoviesCache[key] = items
        putAll(items)
        val dir = diskCacheDir ?: return
        cacheScope.launch {
            try {
                File(dir, "vj_genre_${key}.json").writeText(mediaItemListAdapter.toJson(items))
            } catch (_: Exception) {}
        }
    }

    fun setCategories(genres: List<com.example.domain.model.CategoryItem>, vjs: List<com.example.domain.model.CategoryItem>) {
        if (genres.isNotEmpty()) {
            cachedGenreCategories = genres
            genres.forEach {
                genreNameById[it.id] = it.name
                genreIdByName[it.name.lowercase().trim()] = it.id
            }
        }
        if (vjs.isNotEmpty()) {
            cachedVjCategories = vjs
            vjs.forEach {
                vjNameById[it.id] = it.name
                vjIdByName[it.name.lowercase().trim()] = it.id
            }
        }
    }

    fun getCachedGenres(): List<com.example.domain.model.CategoryItem> = cachedGenreCategories
    fun getCachedVjs(): List<com.example.domain.model.CategoryItem> = cachedVjCategories

    fun getVjName(id: Int): String? = vjNameById[id]
    fun getGenreName(id: Int): String? = genreNameById[id]
    fun getVjId(name: String): Int? = vjIdByName[name.lowercase().trim()]
    fun getGenreId(name: String): Int? = genreIdByName[name.lowercase().trim()]

    fun getYearMovies(year: String): List<MediaItem>? {
        val inMem = yearMoviesCache[year]
        if (!inMem.isNullOrEmpty()) return inMem
        val dir = diskCacheDir ?: return null
        val f = File(dir, "year_${year}.json")
        if (f.exists() && f.length() > 0) {
            try {
                val list = mediaItemListAdapter.fromJson(f.readText())
                if (!list.isNullOrEmpty()) {
                    yearMoviesCache[year] = list
                    putAll(list)
                    return list
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun setYearMovies(year: String, items: List<MediaItem>) {
        if (items.isEmpty()) return
        yearMoviesCache[year] = items
        putAll(items)
        val dir = diskCacheDir ?: return
        cacheScope.launch {
            try {
                File(dir, "year_${year}.json").writeText(mediaItemListAdapter.toJson(items))
            } catch (_: Exception) {}
        }
    }

    fun extractNumericId(input: String): String? {
        if (input.isBlank()) return null
        val trimmed = input.trim()
        if (trimmed.all { it.isDigit() }) return trimmed
        if (trimmed.startsWith("series_") && !trimmed.contains("_ep") && !trimmed.contains("ep_")) return null
        Regex("""[?&](?:id|mid|video_id|movie_id|content_id)=([0-9]+)""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        if (trimmed.contains("preview.php") || trimmed.contains("download.php") || trimmed.contains("twolekede")) {
            Regex("""(?:id|mid)=([0-9]+)""").find(trimmed)?.groupValues?.get(1)?.let { return it }
        }
        if (trimmed.startsWith("myvj_")) return null
        if (trimmed.startsWith("twolekede_")) {
            val part = trimmed.removePrefix("twolekede_").substringBefore("_")
            if (part.isNotBlank() && part.all { it.isDigit() }) return part
        }
        Regex("""^([0-9]{3,7})_\d+$""").find(input)?.groupValues?.get(1)?.let { return it }
        Regex("""_id_([0-9]{3,7})""").find(input)?.groupValues?.get(1)?.let { return it }
        Regex("""ep_([0-9]{3,7})""").find(input)?.groupValues?.get(1)?.let { return it }
        Regex("""_ep\d+_([0-9]{3,7})""").find(input)?.groupValues?.get(1)?.let { return it }
        Regex("""_([0-9]{3,7})$""").find(input)?.groupValues?.get(1)?.let { return it }
        return null
    }

    fun registerEpisode(episode: Episode, posterUrl: String = "", seriesTitle: String = "") {
        if (episode.streamUrl.isNotBlank()) {
            val cleanPath = normalizePath(episode.streamUrl)
            streamPathsById[episode.id] = cleanPath
            val title = if (seriesTitle.isNotBlank()) "$seriesTitle - ${episode.title}" else episode.title
            val item = MediaItem(
                id = episode.id,
                title = title,
                posterUrl = posterUrl.ifBlank { episode.thumbnail },
                streamUrl = cleanPath,
                isSeries = false // Critical: an individual episode is playable media, not a multi-season series container
            )
            itemsById[episode.id] = item
            itemsById[cleanPath] = item

            val numId = extractNumericId(cleanPath) ?: extractNumericId(episode.id)
            if (numId != null) {
                streamPathsById[numId] = cleanPath
                streamPathsById["ep_$numId"] = cleanPath
                itemsById[numId] = item
                itemsById["ep_$numId"] = item
            }
        }
    }

    fun get(idOrPath: String): MediaItem? {
        val clean = normalizePath(idOrPath)
        val numId = extractNumericId(idOrPath)
        return itemsById[idOrPath]
            ?: itemsById[clean]
            ?: (if (numId != null) itemsById[numId] ?: itemsById["ep_$numId"] else null)
            ?: itemsById[idOrPath.removePrefix("series_").removePrefix("ep_")]
            ?: itemsById["series_$idOrPath"]
            ?: itemsById["ep_$idOrPath"]
            ?: itemsById[idOrPath.lowercase().trim()]
            ?: Regex("""[?&](?:mid|id)=([0-9]+)""").find(idOrPath)?.groupValues?.get(1)?.let { itemsById[it] }
    }

    fun getStreamPath(idOrPath: String): String? {
        val clean = normalizePath(idOrPath)
        if (clean.contains("trailer256.b-cdn.net") || clean.contains("intro_myvj") || clean.contains("/trailer") || clean.contains("_trailer")) {
            return null
        }
        val isGenericPath = clean == "/twolekede" || clean == "twolekede" ||
            clean == "/watch" || clean == "watch" || clean == "/" || clean.isBlank()
        if (!isGenericPath && (clean.contains("twolekede?mid=") || clean.contains("preview.php") || clean.contains("download.php") || clean.contains("seriesdetails.php") || clean.contains("moviedetails.php") || clean.startsWith("http://") || clean.startsWith("https://"))) {
            return clean
        }
        val numId = extractNumericId(idOrPath)
        val candidate = streamPathsById[idOrPath]
            ?: streamPathsById[clean]
            ?: (if (numId != null) streamPathsById[numId] ?: streamPathsById["ep_$numId"] else null)
            ?: streamPathsById[idOrPath.removePrefix("series_").removePrefix("ep_")]
            ?: streamPathsById["series_$idOrPath"]
            ?: Regex("""[?&](?:mid|id)=([0-9]+)""").find(idOrPath)?.groupValues?.get(1)?.let { streamPathsById[it] }
            ?: itemsById[idOrPath]?.streamUrl?.let { normalizePath(it) }
            ?: movieDetailsById[idOrPath]?.streamUrl?.let { normalizePath(it) }
            ?: (if (idOrPath.startsWith("myvj_")) itemsById[idOrPath.removePrefix("myvj_")]?.streamUrl?.let { normalizePath(it) } else null)
            ?: (if (!idOrPath.startsWith("myvj_")) itemsById["myvj_$idOrPath"]?.streamUrl?.let { normalizePath(it) } else null)
        if (candidate != null && (candidate.contains("trailer256.b-cdn.net") || candidate.contains("intro_myvj") || candidate.contains("/trailer") || candidate.contains("_trailer"))) {
            return null
        }
        return candidate?.takeIf { it != "/twolekede" && it != "twolekede" && it != "/watch" && it != "watch" }
    }

    fun putStreamPath(idOrKey: String, path: String) {
        if (idOrKey.isBlank() || path.isBlank()) return
        if (path.contains("trailer256.b-cdn.net") || path.contains("intro_myvj") || path.contains("/trailer") || path.contains("_trailer")) return
        val clean = normalizePath(path)
        val isGenericPath = clean == "/twolekede" || clean == "twolekede" || clean == "/watch" || clean == "watch" || clean == "/"
        if (!isGenericPath) {
            streamPathsById[idOrKey] = clean
            val numId = extractNumericId(idOrKey)
            if (numId != null) {
                streamPathsById[numId] = clean
            }
        }
    }

    fun getAll(): List<MediaItem> {
        return itemsById.values.distinctBy { it.id }
    }

    fun saveWatchPosition(contentId: String, positionMs: Long) {
        if (positionMs > 1000L) {
            watchProgressByContentId[contentId] = positionMs
            val clean = normalizePath(contentId)
            if (clean.isNotBlank()) watchProgressByContentId[clean] = positionMs
            extractNumericId(contentId)?.let { watchProgressByContentId[it] = positionMs }
        }
    }

    fun getSavedWatchPosition(contentId: String): Long? {
        val clean = normalizePath(contentId)
        val numId = extractNumericId(contentId)
        return watchProgressByContentId[contentId]
            ?: watchProgressByContentId[clean]
            ?: (if (numId != null) watchProgressByContentId[numId] else null)
    }

    fun findAdjacentEpisodes(contentId: String): Pair<Episode?, Episode?> {
        val cleanId = extractNumericId(contentId) ?: contentId
        val cleanPath = normalizePath(contentId)
        for (detail in seriesDetailsById.values.distinctBy { it.id }) {
            val allEpisodes = detail.seasons.sortedBy { it.number }.flatMap { it.episodes }
            val index = allEpisodes.indexOfFirst { ep ->
                ep.id == contentId ||
                ep.id == cleanId ||
                ep.streamUrl == contentId ||
                ep.streamUrl == cleanPath ||
                (cleanId.isNotBlank() && (ep.streamUrl.contains("mid=$cleanId") || ep.streamUrl.contains("id=$cleanId")))
            }
            if (index >= 0) {
                val prev = allEpisodes.getOrNull(index - 1)
                val next = allEpisodes.getOrNull(index + 1)
                return Pair(prev, next)
            }
        }
        return Pair(null, null)
    }

    fun clear() {
        itemsById.clear()
        streamPathsById.clear()
        seriesDetailsById.clear()
        movieDetailsById.clear()
        playbackSourceCache.clear()
        watchProgressByContentId.clear()
        popularSeriesCache = emptyList()
        popularMoviesCache = emptyList()
        freeMoviesCache = emptyList()
        genreMoviesCache.clear()
        vjMoviesCache.clear()
        vjGenreMoviesCache.clear()
        yearMoviesCache.clear()
    }

    private fun normalizePath(path: String): String {
        var p = path.replace("&amp;", "&").trim()
        if (p.startsWith("twolekede")) {
            p = "/$p"
        }
        return p
    }
}
