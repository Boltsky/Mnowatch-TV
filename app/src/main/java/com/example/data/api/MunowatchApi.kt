package com.example.data.api

import com.example.data.network.NetworkClient
import com.example.data.session.SecureSessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import com.example.domain.model.MediaItem
import com.example.data.repository.MediaCache
import org.json.JSONObject
import org.json.JSONArray

class MunowatchApi(
    private val networkClient: NetworkClient,
    private val sessionManager: SecureSessionManager
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val browseAdapter = moshi.adapter(BrowseDto::class.java)
    private val parser = MunowatchHtmlParser()

    suspend fun validateSession(sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://munowatch.com/home")
                .header("Cookie", "PHPSESSID=$sessionId")
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                val location = response.header("Location") ?: ""
                val isRedirectToLogin = (response.code in 300..399) && location.contains("login", ignoreCase = true)

                if (isRedirectToLogin) {
                    Result.success(false)
                } else if (response.isSuccessful || response.code == 200) {
                    Result.success(true)
                } else {
                    // Check body if 200 but contains login form
                    val body = response.body?.string() ?: ""
                    val hasLoginForm = body.contains("name=\"email\"", ignoreCase = true) || body.contains("/login", ignoreCase = true)
                    Result.success(!hasLoginForm)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("email", email.trim())
                .add("password", password)
                .build()

            val request = Request.Builder()
                .url("https://munowatch.com/login")
                .header("Referer", "https://munowatch.com/login")
                .header("User-Agent", NetworkClient.USER_AGENT)
                .post(formBody)
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                val setCookies = response.headers("Set-Cookie")
                val phpsessid = setCookies.mapNotNull { header ->
                    Regex("""PHPSESSID=([a-zA-Z0-9]+)""").find(header)?.groupValues?.get(1)
                }.firstOrNull() ?: sessionManager.getSessionId()

                val finalUrl = response.request.url.toString()
                val priorUrl = response.priorResponse?.request?.url?.toString() ?: ""
                val body = response.body?.string().orEmpty()

                val isFailed = (finalUrl.contains("/login", ignoreCase = true) && !priorUrl.contains("/login", ignoreCase = true)) &&
                        (body.contains("Invalid", ignoreCase = true) ||
                         body.contains("password-error", ignoreCase = true) ||
                         body.contains("email-error", ignoreCase = true) ||
                         body.contains("Sign In", ignoreCase = true))

                if (!isFailed && !phpsessid.isNullOrBlank()) {
                    sessionManager.saveSessionId(phpsessid)
                    Result.success(phpsessid)
                } else if (!phpsessid.isNullOrBlank() && !body.contains("Sign In", ignoreCase = true)) {
                    sessionManager.saveSessionId(phpsessid)
                    Result.success(phpsessid)
                } else {
                    Result.failure(IOException("Invalid email or password. Please verify your Munowatch credentials."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBrowseCategories(): Result<BrowseDto> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://munowatch.com/browse")
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 200) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val bodyString = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
                val trimmed = bodyString.trimStart()
                if (trimmed.startsWith("<") || trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<title>", ignoreCase = true)) {
                    return@withContext Result.failure(IOException("Server returned HTML login/error page"))
                }
                val dto = browseAdapter.fromJson(bodyString) ?: return@withContext Result.failure(IOException("Failed to parse browse json"))
                Result.success(dto)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGridSlot(
        pipeType: String,
        pipeId: String,
        vjId: String? = null,
        genreId: String? = null,
        lastFetchId: String? = null,
        page: Int? = null,
        offset: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = "https://munowatch.com/grid-slot".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("pipe_type", pipeType)
                .addQueryParameter("pipe_id", pipeId)

            vjId?.let {
                urlBuilder.addQueryParameter("vj_id", it)
            }
            genreId?.let {
                urlBuilder.addQueryParameter("genre_id", it)
            }
            if (!lastFetchId.isNullOrBlank()) {
                urlBuilder.addQueryParameter("last_fetch_id", lastFetchId)
            } else {
                page?.let {
                    if (it > 1) {
                        urlBuilder.addQueryParameter("page", it.toString())
                    }
                }
                offset?.let {
                    if (it > 0) {
                        urlBuilder.addQueryParameter("offset", it.toString())
                    }
                }
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("X-Requested-With", "XMLHttpRequest")
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (isLoginRedirect(response)) {
                    return@withContext Result.failure(IOException("Session expired or authentication required"))
                }
                if (!response.isSuccessful && response.code != 200) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val html = response.body?.string() ?: ""
                Result.success(html)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun search(query: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://munowatch.com/search".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("q", query)
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (isLoginRedirect(response)) {
                    return@withContext Result.failure(IOException("Authentication required"))
                }
                if (!response.isSuccessful && response.code != 200) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val html = response.body?.string() ?: ""
                Result.success(html)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchMunowatchWatchUrl(title: String, vj: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val clean = title
                .replace(Regex("""\[.*?\]|\(.*?\)|(?i)\b(?:by\s+vj|vj)\s+.*$"""), " ")
                .replace(Regex("""(?i)\b(?:season|seasons|series|s)\s*[0-9]+.*$|(?i)\b(?:episode|ep|e)\s*[0-9]+.*$"""), " ")
                .trim(' ', '-', '–', '—', ':', '|')
            if (clean.length < 2) return@withContext null

            val searchHtml = search(clean).getOrNull()
            if (searchHtml.isNullOrBlank()) return@withContext null

            val parsedCards = parser.parseMediaCards(searchHtml)
            val targetVj = vj?.lowercase()?.replace("vj", "")?.replace(".", "")?.trim().orEmpty()
            val matchedCard = parsedCards.firstOrNull { card ->
                val titleMatch = parser.titlesMatch(card.title, clean)
                val cvj = card.vj.lowercase().replace("vj", "").replace(".", "").trim()
                val vjMatch = targetVj.isBlank() || cvj.contains(targetVj) || targetVj.contains(cvj)
                titleMatch && vjMatch
            } ?: parsedCards.firstOrNull { card ->
                parser.titlesMatch(card.title, clean)
            }

            if (matchedCard != null && matchedCard.streamUrl.isNotBlank()) {
                val su = matchedCard.streamUrl
                return@withContext if (su.startsWith("http://") || su.startsWith("https://")) su
                else if (su.startsWith("/")) "https://munowatch.com$su"
                else "https://munowatch.com/$su"
            }

            val doc = org.jsoup.Jsoup.parse(searchHtml)
            val cards = doc.select("a[href*='twolekede?v='], a[href*='/twolekede?']")
            if (cards.isEmpty()) return@withContext null

            // Match by title and VJ first, then title alone
            val bestCard = cards.firstOrNull { card ->
                val cardTitle = (card.selectFirst("img")?.attr("alt") ?: "").trim()
                val cardVj = (card.selectFirst("p")?.text() ?: "").lowercase().trim()
                val titleMatch = cardTitle.isNotBlank() && parser.titlesMatch(cardTitle, clean)
                val vjMatch = targetVj.isBlank() || cardVj.contains(targetVj) || targetVj.contains(cardVj)
                titleMatch && vjMatch
            } ?: cards.firstOrNull { card ->
                val cardTitle = (card.selectFirst("img")?.attr("alt") ?: "").trim()
                cardTitle.isNotBlank() && parser.titlesMatch(cardTitle, clean)
            }

            val href = bestCard?.attr("href")?.replace("&amp;", "&")?.trim()
            if (!href.isNullOrBlank()) {
                if (href.startsWith("http://") || href.startsWith("https://")) href
                else if (href.startsWith("/")) "https://munowatch.com$href"
                else "https://munowatch.com/$href"
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchHtmlInternal(url: String): String? {
        return try {
            val referer = if (url.contains("twolekede.com")) "https://twolekede.com/" else "https://munowatch.com/home"
            val request = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .get()
                .build()
            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (isLoginRedirect(response)) return null
                if (response.isSuccessful || response.code == 200) {
                    response.body?.string() ?: ""
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getTwolekedeSeriesCatalog(page: Int = 1): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "https://twolekede.com/series.php" else "https://twolekede.com/series.php?page=$page&sort=new&letter="
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://twolekede.com/")
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 200) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val html = response.body?.string().orEmpty()
                Result.success(html)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTwolekedeMoviesCatalog(page: Int = 1): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) "https://twolekede.com/movies.php" else "https://twolekede.com/movies.php?page=$page&sort=new&letter="
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://twolekede.com/")
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 200) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val html = response.body?.string().orEmpty()
                Result.success(html)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFilteredCatalog(
        year: String? = null,
        categorySlug: String? = null,
        vjSlug: String? = null,
        mediaType: Int = 1,
        page: Int = 1,
        perPage: Int = 24
    ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val formBodyBuilder = FormBody.Builder()
                .add("source", "next_js")
                .add("media_type", mediaType.toString())
                .add("page", page.toString())
                .add("per_page", perPage.toString())

            if (!year.isNullOrBlank()) {
                formBodyBuilder.add("release_year", year.trim())
                formBodyBuilder.add("year", year.trim())
            }
            if (!categorySlug.isNullOrBlank()) {
                val cleanCatSlug = when (categorySlug.lowercase().trim()) {
                    "love story", "love-story", "romance" -> "romance"
                    "martial arts", "martial-arts", "kung fu", "kungu fu" -> "martial-arts"
                    "sci fi", "sci-fi" -> "sci-fi"
                    else -> categorySlug.lowercase().trim().replace(" ", "-")
                }
                formBodyBuilder.add("category_id", cleanCatSlug)
            }
            if (!vjSlug.isNullOrBlank()) {
                val cleanVjSlug = if (vjSlug.startsWith("vj-", ignoreCase = true)) {
                    vjSlug.lowercase().trim()
                } else {
                    "vj-" + vjSlug.lowercase().trim().replace("vj", "").replace(".", "").trim().replace(" ", "-")
                }
                formBodyBuilder.add("vj_id", cleanVjSlug)
            }

            val formBody = formBodyBuilder.build()
            val urls = listOf(
                "https://ugawatch.com/api-v3/mobile/get-filtered.php",
                "https://myvj.net/api-v3/mobile/get-filtered.php"
            )

            var lastException: Exception? = null
            for (apiUrl in urls) {
                try {
                    val request = Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", NetworkClient.USER_AGENT)
                        .header("Referer", "https://ugawatch.com/")
                        .post(formBody)
                        .build()

                    networkClient.okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code != 200) {
                            return@use
                        }
                        val bodyString = response.body?.string().orEmpty()
                        if (bodyString.isBlank() || bodyString.startsWith("<")) {
                            return@use
                        }
                        val json = JSONObject(bodyString)
                        val status = json.optInt("status", -1)
                        if (status != 0 && json.optString("status") != "0") {
                            return@use
                        }
                        val jsonArray = json.optJSONArray("medias")
                            ?: json.optJSONArray("items")
                            ?: json.optJSONArray("list")
                            ?: json.optJSONArray("movies")
                            ?: JSONArray()

                        val items = mutableListOf<MediaItem>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val rowId = obj.optString("row_id").ifBlank { obj.optString("id") }
                            val title = obj.optString("title")
                            if (rowId.isBlank() || title.isBlank()) continue

                            val posterPath = obj.optString("tmdb_poster_path")
                            val munoImg = obj.optString("img_port_muno_file_name")
                            val posterUrl = when {
                                posterPath.isNotBlank() && posterPath != "null" -> {
                                    val cleanP = if (posterPath.startsWith("/")) posterPath else "/$posterPath"
                                    "https://image.tmdb.org/t/p/w500$cleanP"
                                }
                                munoImg.isNotBlank() && munoImg != "null" -> {
                                    if (munoImg.startsWith("http")) munoImg else "https://res-watch.b-cdn.net/1/$munoImg"
                                }
                                else -> ""
                            }

                            val backdropPath = obj.optString("tmdb_backdrop_path")
                            val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") {
                                val cleanB = if (backdropPath.startsWith("/")) backdropPath else "/$backdropPath"
                                "https://image.tmdb.org/t/p/original$cleanB"
                            } else posterUrl

                            val itemVj = obj.optString("vj").takeIf { it.isNotBlank() && it != "null" && it != "-" }
                                ?: vjSlug?.replace("vj-", "VJ ")?.replace("-", " ")?.split(" ")?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                                ?: "VJ Junior"

                            val itemYear = if (!year.isNullOrBlank()) {
                                year.trim()
                            } else {
                                val relDate = obj.optString("tmdb_release_date")
                                val addedDate = obj.optString("date_added")
                                val yRel = Regex("""\b(19\d{2}|20\d{2})\b""").find(relDate)?.value
                                val yAdd = Regex("""\b(19\d{2}|20\d{2})\b""").find(addedDate)?.value
                                yRel ?: yAdd ?: ""
                            }

                            val quality = obj.optString("quality_str").takeIf { it.isNotBlank() && it != "null" } ?: "1080p HD"
                            val isSeries = mediaType == 2

                            val rawCategories = obj.optString("categories").takeIf { it.isNotBlank() && it != "-" && it != "null" }
                                ?: obj.optString("genre").takeIf { it.isNotBlank() && it != "-" && it != "null" }
                                ?: categorySlug?.replace("-", " ")?.split(" ")?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                                ?: ""

                            val slug = obj.optString("slug")
                            if (slug.isNotBlank()) {
                                MediaCache.putSlug("myvj_$rowId", slug)
                                MediaCache.putSlug(rowId, slug)
                            }

                            val rawDesc = obj.optString("about").ifBlank { obj.optString("overview") }.ifBlank { obj.optString("plot") }

                            // bunny_file_name or trailer on myvj.net are short promo trailers with promotional overlays.
                            // Do NOT set them as playable streamUrl! MediaItem streamUrl will be resolved to full movie on media servers.
                            val mediaItem = MediaItem(
                                id = "myvj_$rowId",
                                title = title,
                                description = rawDesc,
                                posterUrl = posterUrl,
                                backdropUrl = backdropUrl,
                                year = itemYear,
                                vj = itemVj,
                                genre = rawCategories,
                                isSeries = isSeries,
                                quality = quality,
                                streamUrl = ""
                            )
                            items.add(mediaItem)
                        }
                        return@withContext Result.success(items)
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
            Result.failure(lastException ?: IOException("Failed to fetch filtered catalog from API"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyVjMovieDetail(slug: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (slug.isBlank()) return@withContext Result.failure(IllegalArgumentException("Slug cannot be blank"))
        val formBody = FormBody.Builder()
            .add("source", "next_js")
            .add("movie_slug", slug.trim())
            .build()
        val urls = listOf(
            "https://ugawatch.com/api-v3/mobile/get-movie-details-min.php",
            "https://myvj.net/api-v3/mobile/get-movie-details-min.php"
        )
        for (url in urls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .header("Referer", "https://ugawatch.com/")
                    .post(formBody)
                    .build()
                networkClient.okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 200) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank() && !body.startsWith("<")) {
                            val json = JSONObject(body)
                            if (json.optInt("status", -1) == 0 || json.optString("status") == "0") {
                                val movieObj = json.optJSONObject("movie") ?: json.optJSONObject("data")
                                if (movieObj != null) {
                                    return@withContext Result.success(movieObj)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        Result.failure(IOException("Failed to fetch movie details from MyVJ API for slug: $slug"))
    }

    suspend fun getMyVjSeriesDetail(slug: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (slug.isBlank()) return@withContext Result.failure(IllegalArgumentException("Slug cannot be blank"))
        val formBody = FormBody.Builder()
            .add("source", "next_js")
            .add("serie_slug", slug.trim())
            .build()
        val urls = listOf(
            "https://ugawatch.com/api-v3/mobile/get-serie-details-min.php",
            "https://myvj.net/api-v3/mobile/get-serie-details-min.php"
        )
        for (url in urls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .header("Referer", "https://ugawatch.com/")
                    .post(formBody)
                    .build()
                networkClient.okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 200) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank() && !body.startsWith("<")) {
                            val json = JSONObject(body)
                            if (json.optInt("status", -1) == 0 || json.optString("status") == "0") {
                                val serieObj = json.optJSONObject("serie") ?: json.optJSONObject("data")
                                if (serieObj != null) {
                                    return@withContext Result.success(serieObj)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        Result.failure(IOException("Failed to fetch series details from MyVJ API for slug: $slug"))
    }

    suspend fun searchMyVj(query: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.failure(IllegalArgumentException("Query cannot be blank"))
        val formBody = FormBody.Builder()
            .add("source", "next_js")
            .add("search_term", query.trim())
            .build()
        val urls = listOf(
            "https://ugawatch.com/api-v3/mobile/search.php",
            "https://myvj.net/api-v3/mobile/search.php"
        )
        for (url in urls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .header("Referer", "https://ugawatch.com/")
                    .post(formBody)
                    .build()
                networkClient.okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 200) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank() && !body.startsWith("<")) {
                            val json = JSONObject(body)
                            if (json.optInt("status", -1) == 0 || json.optString("status") == "0") {
                                return@withContext Result.success(json)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        Result.failure(IOException("Failed to search MyVJ API for query: $query"))
    }

    private fun sanitizeTwolekedeSearchHtml(html: String, query: String): String {
        if (html.isBlank()) return ""
        try {
            val doc = org.jsoup.Jsoup.parse(html)
            val hasNoResults = doc.select(".no-results, .no-content").isNotEmpty() ||
                doc.body().text().contains(Regex("""(?i)\b(?:no movies? found|no series found|no results? found)\b"""))

            doc.select(
                ".popular-searches, .popular-movies, .trending, .recommended, .recommendations, " +
                ".similar-series, [class*='popular'], [class*='recommend'], [class*='trending'], " +
                ".category-suggestions, footer, header, nav, aside"
            ).remove()

            if (hasNoResults) {
                return ""
            }

            val isYearQuery = query.trim().matches(Regex("""\b(19\d{2}|20\d{2})\b"""))
            val cardElements = doc.select(".content-item, .movie-card, .series-card, .grid-item, a[href*='moviedetails.php'], a[href*='seriesdetails.php']")
            val matchingCards = mutableListOf<org.jsoup.nodes.Element>()
            for (card in cardElements) {
                val title = (card.selectFirst("h3, h4, .title, .content-title, .movie-title")?.text()
                    ?: card.attr("title").ifBlank { card.text() }).trim()
                val href = (card.selectFirst("a")?.attr("href") ?: card.attr("href")).trim()
                val isNavCategoryLink = href.contains("categories.php") || href.contains("genres.php") ||
                    href.startsWith("category") || href.startsWith("/category") || href.startsWith("genre")
                if (isNavCategoryLink) continue

                val cleanQ = parser.cleanForMatching(query)
                val cleanT = parser.cleanForMatching(title)
                val isMatch = isYearQuery || title.isBlank() || parser.titlesMatch(title, query) ||
                    (cleanQ.length >= 3 && cleanT.contains(cleanQ)) ||
                    (cleanT.length >= 3 && cleanQ.contains(cleanT))
                if (isMatch) {
                    matchingCards.add(card)
                }
            }

            if (matchingCards.isEmpty()) {
                return ""
            }

            return buildString {
                append("<div class=\"content-grid\">\n")
                for (card in matchingCards) {
                    append(card.outerHtml()).append("\n")
                }
                append("</div>\n")
            }
        } catch (_: Exception) {
            return html
        }
    }

    suspend fun searchTwolekede(query: String, isSeries: Boolean? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val movieUrl = "https://twolekede.com/search.php?q=$encoded"
            val seriesUrl = "https://twolekede.com/search.php?type=series&q=$encoded"

            val (mRaw, sRaw) = kotlinx.coroutines.coroutineScope {
                val mDef = if (isSeries != true) async(Dispatchers.IO) { fetchHtmlInternal(movieUrl).orEmpty() } else null
                val sDef = if (isSeries != false) async(Dispatchers.IO) { fetchHtmlInternal(seriesUrl).orEmpty() } else null
                Pair(mDef?.await().orEmpty(), sDef?.await().orEmpty())
            }

            val mHtml = sanitizeTwolekedeSearchHtml(mRaw, query)
            val sHtml = sanitizeTwolekedeSearchHtml(sRaw, query)

            val combined = buildString {
                if (mHtml.isNotBlank()) append(mHtml)
                if (sHtml.isNotBlank()) {
                    append("\n<div id=\"twolekede-series-search-results\">\n")
                    append(sHtml)
                    append("\n</div>")
                }
            }
            Result.success(combined)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTwolekedeCategoryCatalog(categoryName: String, page: Int = 1): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(categoryName, "UTF-8")
            val url = if (page <= 1) {
                "https://twolekede.com/categories.php?name=$encoded"
            } else {
                "https://twolekede.com/categories.php?name=$encoded&page=$page"
            }
            val html = fetchHtmlInternal(url)
            if (!html.isNullOrBlank()) {
                Result.success(html)
            } else {
                Result.failure(IOException("Empty response from categories.php for $categoryName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTwolekedeGenreCatalog(genreName: String, page: Int = 1): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(genreName, "UTF-8")
            val url = if (page <= 1) {
                "https://twolekede.com/genres.php?genre=$encoded"
            } else {
                "https://twolekede.com/genres.php?genre=$encoded&page=$page"
            }
            val html = fetchHtmlInternal(url)
            if (!html.isNullOrBlank()) {
                Result.success(html)
            } else {
                Result.failure(IOException("Empty response from genres.php for $genreName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTwolekedeCategoriesPage(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtmlInternal("https://twolekede.com/categories.php")
            if (!html.isNullOrBlank()) {
                Result.success(html)
            } else {
                Result.failure(IOException("Empty response from categories.php"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTwolekedeGenresPage(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtmlInternal("https://twolekede.com/genres.php")
            if (!html.isNullOrBlank()) {
                Result.success(html)
            } else {
                Result.failure(IOException("Empty response from genres.php"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    suspend fun getTwolekedeSeriesDetails(seriesUrlOrTitle: String, category: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cachedItem = com.example.data.repository.MediaCache.get(seriesUrlOrTitle)
            val cachedUrl = cachedItem?.streamUrl?.takeIf { it.isNotBlank() && it.contains("seriesdetails.php") }
                ?: com.example.data.repository.MediaCache.getStreamPath(seriesUrlOrTitle)?.takeIf { it.isNotBlank() && it.contains("seriesdetails.php") }

            var targetUrl = when {
                cachedUrl != null -> cachedUrl
                seriesUrlOrTitle.contains("seriesdetails.php") -> {
                    if (seriesUrlOrTitle.startsWith("http")) seriesUrlOrTitle else "https://twolekede.com/${seriesUrlOrTitle.removePrefix("/")}"
                }
                seriesUrlOrTitle.contains("preview.php") || seriesUrlOrTitle.contains("download.php") || seriesUrlOrTitle.contains("mid=") -> {
                    // This is an episode/preview URL. Fetch preview HTML to extract canonical series title and VJ
                    val prevHtml = fetchHtmlInternal(seriesUrlOrTitle).orEmpty()
                    val pDoc = org.jsoup.Jsoup.parse(prevHtml)
                    val pTitle = pDoc.selectFirst(".content-info h1, h1")?.text()?.trim()
                        ?: cachedItem?.title
                        ?: ""
                    val pVj = pDoc.selectFirst(".category-tag, .category, .vj")?.text()?.trim()
                        ?: cachedItem?.vj
                        ?: category
                        ?: "VJ Junior"
                    val cleanBase = extractBaseSeriesName(pTitle).ifBlank { pTitle }
                    val encT = java.net.URLEncoder.encode(cleanBase, "UTF-8")
                    val encV = java.net.URLEncoder.encode(pVj, "UTF-8")
                    "https://twolekede.com/seriesdetails.php?title=$encT&category=$encV"
                }
                else -> {
                    val raw = (cachedItem?.title?.takeIf { it.isNotBlank() } ?: seriesUrlOrTitle).removePrefix("series_")
                    val vjMatch = Regex("""(?i)[_\s]+(?:VJ|Shao)[_\s]+.*$""").find(raw)
                    val cleanTitle = if (vjMatch != null) raw.substring(0, vjMatch.range.first).replace("_", " ").trim() else raw.replace("_", " ").trim()
                    val baseSeries = extractBaseSeriesName(cleanTitle).ifBlank { cleanTitle }
                    val detectedCat = category ?: cachedItem?.vj?.takeIf { it.isNotBlank() && it != "VJ Translated" } ?: vjMatch?.value?.replace("_", " ")?.trim() ?: "VJ Junior"
                    val encodedTitle = java.net.URLEncoder.encode(baseSeries, "UTF-8")
                    val catParam = if (detectedCat.isNotBlank()) "&category=${java.net.URLEncoder.encode(detectedCat, "UTF-8")}" else ""
                    "https://twolekede.com/seriesdetails.php?title=$encodedTitle$catParam"
                }
            }

            var baseHtml = fetchHtmlInternal(targetUrl)
            val hasSeriesDetails = baseHtml != null && (baseHtml.contains("series-details") || baseHtml.contains("seasons-section") || baseHtml.contains("episode-card"))

            if (baseHtml.isNullOrBlank() || !hasSeriesDetails) {
                // Secondary fallback: search Twolekede by clean series title to find matching seriesdetails URL
                val cleanQuery = (cachedItem?.title?.takeIf { it.isNotBlank() } ?: seriesUrlOrTitle.removePrefix("series_").replace("_", " "))
                    .split(Regex("(?i)\\s+VJ\\s+"), limit = 2)[0]
                    .replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (cleanQuery.length >= 2) {
                    val searchRes = searchTwolekede(cleanQuery).getOrNull()
                    if (!searchRes.isNullOrBlank()) {
                        val sDoc = org.jsoup.Jsoup.parse(searchRes)
                        val matchLink = sDoc.select("a[href*='seriesdetails.php']").firstOrNull { a ->
                            val text = a.text().lowercase()
                            val href = a.attr("href")
                            val cleanQ = cleanQuery.lowercase()
                            text.contains(cleanQ) || cleanQ.contains(text.trim()) ||
                            href.contains(cleanQuery.replace(" ", "+"), ignoreCase = true) ||
                            href.contains(java.net.URLEncoder.encode(cleanQuery, "UTF-8"), ignoreCase = true)
                        }?.attr("href")?.trim()
                        if (!matchLink.isNullOrBlank()) {
                            targetUrl = if (matchLink.startsWith("http")) matchLink else "https://twolekede.com/${matchLink.removePrefix("/")}"
                            val searchDetailHtml = fetchHtmlInternal(targetUrl)
                            if (!searchDetailHtml.isNullOrBlank()) {
                                baseHtml = searchDetailHtml
                            }
                        }
                    }
                }
            }

            if (baseHtml.isNullOrBlank()) {
                return@withContext Result.failure(IOException("Failed to fetch series details from $targetUrl"))
            }

            val doc = org.jsoup.Jsoup.parse(baseHtml)
            doc.select(".content-grid, .grid-container, .popular-movies, .similar-series, .recommendations, [class*='popular'], [class*='recommend'], [class*='similar'], [id*='recommend'], a[href*='moviedetails.php'], footer, header, nav, aside").remove()
            val seasonLinks = doc.select(".seasons-section a[href*='episodes.php'], a.season-link[href*='episodes.php'], a[href*='episodes.php']")
            val combinedBuilder = StringBuilder(doc.html())

            if (seasonLinks.isNotEmpty()) {
                for (sLink in seasonLinks) {
                    val sHref = sLink.attr("href").trim()
                    if (sHref.isBlank()) continue
                    val fullSeasonUrl = if (sHref.startsWith("http")) sHref else "https://twolekede.com/${sHref.removePrefix("/")}"
                    val seasonNum = Regex("""[?&]season=([0-9]+)""").find(sHref)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""Season\s*([0-9]+)""", RegexOption.IGNORE_CASE).find(sLink.text())?.groupValues?.get(1)?.toIntOrNull()
                        ?: 1

                    val seasonHtml = fetchHtmlInternal(fullSeasonUrl)
                    if (!seasonHtml.isNullOrBlank()) {
                        val sDoc = org.jsoup.Jsoup.parse(seasonHtml)
                        sDoc.select(".content-grid, .grid-container, .popular-movies, .similar-series, .recommendations, [class*='popular'], [class*='recommend'], [class*='similar'], [id*='recommend'], a[href*='moviedetails.php'], footer, header, nav, aside").remove()
                        val epCards = sDoc.select(".episode-card, .episode-box, .episode-item")
                        val epElements = if (epCards.isNotEmpty()) epCards else sDoc.select("a[href*='preview.php'], a[href*='download.php']")
                        if (epElements.isNotEmpty()) {
                            combinedBuilder.append("\n<div class=\"season-episodes-wrapper\" data-season=\"$seasonNum\">\n")
                            for (ep in epElements) {
                                combinedBuilder.append(ep.outerHtml()).append("\n")
                            }
                            combinedBuilder.append("</div>\n")
                        }

                        // Follow pagination for this season to ensure no episodes are missed
                        try {
                            val pageLinks = sDoc.select(".pagination a, a[href*='page='], a[href*='p='], .pages a, a.page-link, .page-numbers a")
                            val seenPageUrls = mutableSetOf(fullSeasonUrl)
                            for (pLink in pageLinks) {
                                val pHref = pLink.attr("href").trim()
                                if (pHref.isBlank() || pHref == "#" || pHref.startsWith("javascript")) continue
                                val fullPageUrl = if (pHref.startsWith("http")) pHref else "https://twolekede.com/${pHref.removePrefix("/")}"
                                if (seenPageUrls.add(fullPageUrl)) {
                                    val pageHtml = fetchHtmlInternal(fullPageUrl)
                                    if (!pageHtml.isNullOrBlank()) {
                                        val pDoc = org.jsoup.Jsoup.parse(pageHtml)
                                        pDoc.select(".content-grid, .grid-container, .popular-movies, .similar-series, .recommendations, [class*='popular'], [class*='recommend'], [class*='similar'], [id*='recommend'], a[href*='moviedetails.php'], footer, header, nav, aside").remove()
                                        val pCards = pDoc.select(".episode-card, .episode-box, .episode-item")
                                        val pEpElements = if (pCards.isNotEmpty()) pCards else pDoc.select("a[href*='preview.php'], a[href*='download.php']")
                                        if (pEpElements.isNotEmpty()) {
                                            combinedBuilder.append("\n<div class=\"season-episodes-wrapper\" data-season=\"$seasonNum\">\n")
                                            for (ep in pEpElements) {
                                                combinedBuilder.append(ep.outerHtml()).append("\n")
                                            }
                                            combinedBuilder.append("</div>\n")
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else {
                val titleParam = Regex("""[?&]title=([^&]+)""").find(targetUrl)?.groupValues?.get(1)
                val catParam = Regex("""[?&]category=([^&]+)""").find(targetUrl)?.groupValues?.get(1)?.let { "&category=$it" } ?: ""
                if (!titleParam.isNullOrBlank()) {
                    var consecutiveEmptySeasons = 0
                    for (s in 1..10) {
                        val seasonUrl = "https://twolekede.com/episodes.php?title=$titleParam$catParam&season=$s"
                        val sHtml = fetchHtmlInternal(seasonUrl)
                        if (!sHtml.isNullOrBlank()) {
                            val sDoc = org.jsoup.Jsoup.parse(sHtml)
                            sDoc.select(".content-grid, .grid-container, .popular-movies, .similar-series, .recommendations, [class*='popular'], [class*='recommend'], [class*='similar'], [id*='recommend'], a[href*='moviedetails.php'], footer, header, nav, aside").remove()
                            val epCards = sDoc.select(".episode-card, .episode-box, .episode-item")
                            val sEpElements = if (epCards.isNotEmpty()) epCards else sDoc.select("a[href*='preview.php'], a[href*='download.php']")
                            if (sEpElements.isNotEmpty()) {
                                consecutiveEmptySeasons = 0
                                combinedBuilder.append("\n<div class=\"season-episodes-wrapper\" data-season=\"$s\">\n")
                                for (ep in sEpElements) {
                                    combinedBuilder.append(ep.outerHtml()).append("\n")
                                }
                                combinedBuilder.append("</div>\n")
                            } else {
                                consecutiveEmptySeasons++
                                if (s > 1 && consecutiveEmptySeasons >= 2) break
                            }
                        } else {
                            consecutiveEmptySeasons++
                            if (s > 1 && consecutiveEmptySeasons >= 2) break
                        }
                    }
                }
            }

            Result.success(combinedBuilder.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesList(seriesId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cachedItem = com.example.data.repository.MediaCache.get(seriesId)
            val cachedUrl = cachedItem?.streamUrl?.takeIf { it.isNotBlank() }
            val effectiveTarget = when {
                seriesId.startsWith("http://") || seriesId.startsWith("https://") -> seriesId
                seriesId.startsWith("/") -> seriesId
                cachedUrl != null && (cachedUrl.startsWith("http") || cachedUrl.startsWith("/")) -> cachedUrl
                else -> seriesId
            }

            // 1. Direct Twolekede URL or series ID check
            if (effectiveTarget.contains("seriesdetails.php") || effectiveTarget.contains("twolekede.com/seriesdetails") ||
                effectiveTarget.startsWith("series_")
            ) {
                val twolekedeRes = getTwolekedeSeriesDetails(effectiveTarget)
                if (twolekedeRes.isSuccess && twolekedeRes.getOrDefault("").isNotBlank()) {
                    return@withContext twolekedeRes
                }
            }

            // 2. Also search Twolekede for this series by title to get full seasons & episodes
            val knownTitle = cachedItem?.title?.takeIf {
                it.isNotBlank() && !it.startsWith("Munowatch", ignoreCase = true) && !it.equals("Series", ignoreCase = true)
            }
            var twolekedeHtml: String? = null
            if (knownTitle != null) {
                val baseClean = knownTitle.replace(Regex("""\[.*?\]|\(.*?\)|(?i)\b(?:season|seasons|series|s)\s*[0-9]+.*$|(?i)\s+by\s+vj\s+.*$"""), " ").trim()
                if (baseClean.length >= 2) {
                    val searchRes = searchTwolekede(baseClean)
                    if (searchRes.isSuccess) {
                        val sDoc = org.jsoup.Jsoup.parse(searchRes.getOrDefault(""))
                        val matchingLink = sDoc.select("a[href*='seriesdetails.php']").firstOrNull { a ->
                            val t = a.selectFirst("h3, h4, .title")?.text() ?: a.attr("title").ifBlank { a.text() }
                            val cleanT = t.trim()
                            cleanT.isNotBlank() && (cleanT.contains(baseClean, ignoreCase = true) || baseClean.contains(cleanT, ignoreCase = true))
                        }
                        if (matchingLink != null) {
                            val detailRes = getTwolekedeSeriesDetails(matchingLink.attr("href"))
                            if (detailRes.isSuccess) {
                                twolekedeHtml = detailRes.getOrNull()
                            }
                        }
                    }
                }
            }

            val midInTarget = Regex("""[?&]mid=([0-9]+)""").find(effectiveTarget)?.groupValues?.get(1)
            val serieIdInTarget = Regex("""[?&](?:serie_id|series_id)=([0-9]+)""").find(effectiveTarget)?.groupValues?.get(1)

            val urlCandidates = mutableListOf<String>()

            // If explicit serie_id is known, check series list endpoints first
            if (serieIdInTarget != null) {
                urlCandidates.add("https://munowatch.com/list?serie_id=$serieIdInTarget")
                urlCandidates.add("https://munowatch.com/series?id=$serieIdInTarget")
                urlCandidates.add("https://munowatch.com/twolekede?mid=$serieIdInTarget")
            } else if (midInTarget != null) {
                urlCandidates.add("https://munowatch.com/twolekede?mid=$midInTarget")
                urlCandidates.add("https://munowatch.com/list?serie_id=$midInTarget")
                urlCandidates.add("https://munowatch.com/series?id=$midInTarget")
            } else if (effectiveTarget.startsWith("http://") || effectiveTarget.startsWith("https://")) {
                urlCandidates.add(effectiveTarget)
            } else if (effectiveTarget.startsWith("/")) {
                urlCandidates.add("https://munowatch.com$effectiveTarget")
            } else {
                val numericId = Regex("""[0-9]+""").find(effectiveTarget)?.value ?: effectiveTarget
                if (numericId.isNotBlank()) {
                    urlCandidates.add("https://munowatch.com/twolekede?mid=$numericId")
                    urlCandidates.add("https://munowatch.com/list?serie_id=$numericId")
                    urlCandidates.add("https://munowatch.com/series?id=$numericId")
                }
            }

            var chosenHtml: String? = null
            var bestHtml: String? = null
            var maxEpisodesCount = 0

            for (url in urlCandidates) {
                val html = fetchHtmlInternal(url)
                if (!html.isNullOrBlank()) {
                    val doc = org.jsoup.Jsoup.parse(html)
                    val linkedSerieId = doc.selectFirst(
                        "[data-series-id], [data-series], #series-info [href*='serie_id='], .series-header [href*='serie_id='], " +
                        ".breadcrumb [href*='serie_id='], a.all-episodes[href*='serie_id='], #list-container [href*='serie_id='], " +
                        "a[href*='list?serie_id=']:not(#grid-container *):not(footer *):not(header *):not(.sidebar *):not([class*='recommend'] *):not([class*='related'] *)"
                    )?.let { el ->
                        val ds = el.attr("data-series-id").ifBlank { el.attr("data-series") }
                        if (ds.isNotBlank() && ds.all { it.isDigit() }) ds else {
                            Regex("""[?&](?:serie_id|series_id|serie)=([0-9]+)""").find(el.attr("href"))?.groupValues?.get(1)
                        }
                    } ?: Regex("""data-series(?:-id)?=["']([0-9]+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                      ?: Regex("""(?:serie_id|series_id|seriesId)\s*[:=]\s*["']?([0-9]+)["']?""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

                    var mergedHtml = html
                    if (linkedSerieId != null && !url.contains("serie_id=$linkedSerieId")) {
                        val fullSeriesListHtml = fetchHtmlInternal("https://munowatch.com/list?serie_id=$linkedSerieId")
                        if (!fullSeriesListHtml.isNullOrBlank() && (
                            fullSeriesListHtml.contains("list-container") ||
                            fullSeriesListHtml.contains("mid=") ||
                            fullSeriesListHtml.contains("twolekede")
                        )) {
                            mergedHtml = "$html\n<div id=\"full-series-list-content\">\n$fullSeriesListHtml\n</div>"
                        }
                    }

                    val mergedDoc = if (mergedHtml != html) org.jsoup.Jsoup.parse(mergedHtml) else doc
                    val actualEpisodesCount = mergedDoc.select(
                        "#list-container a, [id*='more-episodes'] a, #more-episodes-container a, " +
                        "#full-series-list-content a, .episode-box a, .episode-item a, .episodes-list a, .episode-list a, .episode-card"
                    ).filter { a ->
                        val href = a.attr("href")
                        href.contains("twolekede") || href.contains("mid=") || href.contains("preview.php")
                    }.size

                    if (actualEpisodesCount > maxEpisodesCount) {
                        maxEpisodesCount = actualEpisodesCount
                        bestHtml = mergedHtml
                    }

                    if (chosenHtml == null) chosenHtml = mergedHtml
                    if (actualEpisodesCount > 1) {
                        chosenHtml = mergedHtml
                        break
                    }
                }
            }

            if (bestHtml != null && (maxEpisodesCount >= 1 || chosenHtml.isNullOrBlank())) {
                chosenHtml = bestHtml
            }

            val finalCombined = when {
                !twolekedeHtml.isNullOrBlank() && !chosenHtml.isNullOrBlank() -> {
                    "$chosenHtml\n<div id=\"twolekede-series-data\">\n$twolekedeHtml\n</div>"
                }
                !twolekedeHtml.isNullOrBlank() -> twolekedeHtml
                !chosenHtml.isNullOrBlank() -> chosenHtml
                else -> null
            }

            if (!finalCombined.isNullOrBlank()) {
                Result.success(finalCombined)
            } else {
                val fallbackId = Regex("""[0-9]+""").find(effectiveTarget)?.value ?: seriesId
                val fallbackWatch = "https://munowatch.com/twolekede?mid=$fallbackId"
                val fallbackHtml = fetchHtmlInternal(fallbackWatch)
                if (!fallbackHtml.isNullOrBlank()) {
                    Result.success(fallbackHtml)
                } else {
                    Result.failure(IOException("Failed to load series page for $seriesId"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEpisodeRanges(videoId: String, seriesCode: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // First attempt POST with FormBody
            val formBody = FormBody.Builder()
                .add("video_id", videoId)
                .add("series_code", seriesCode)
                .add("currentId", videoId)
                .add("mid", videoId)
                .build()

            val postRequest = Request.Builder()
                .url("https://munowatch.com/episoderanges")
                .header("Referer", "https://munowatch.com/twolekede?mid=$videoId")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(formBody)
                .build()

            val postHtml = try {
                networkClient.okHttpClient.newCall(postRequest).execute().use { response ->
                    if (response.isSuccessful || response.code == 200) {
                        response.body?.string().orEmpty()
                    } else ""
                }
            } catch (_: Exception) { "" }

            if (postHtml.isNotBlank() && (postHtml.contains("range") || postHtml.contains("-"))) {
                return@withContext Result.success(postHtml)
            }

            // Fallback GET
            val url = "https://munowatch.com/episoderanges".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("video_id", videoId)
                .addQueryParameter("series_code", seriesCode)
                .addQueryParameter("currentId", videoId)
                .addQueryParameter("mid", videoId)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://munowatch.com/twolekede?mid=$videoId")
                .header("X-Requested-With", "XMLHttpRequest")
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (isLoginRedirect(response)) {
                    return@withContext Result.failure(IOException("Authentication required"))
                }
                if (!response.isSuccessful && response.code != 200) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val html = response.body?.string() ?: ""
                Result.success(html)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMoreEpisodes(videoId: String, range: String = "1-20"): Result<String> = withContext(Dispatchers.IO) {
        try {
            // First attempt POST with FormBody (Standard Munowatch jQuery AJAX pattern)
            val formBody = FormBody.Builder()
                .add("epsRange", range)
                .add("currentId", videoId)
                .add("range", range)
                .add("video_id", videoId)
                .add("mid", videoId)
                .build()

            val postRequest = Request.Builder()
                .url("https://munowatch.com/moreepisodes")
                .header("Referer", "https://munowatch.com/twolekede?mid=$videoId")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(formBody)
                .build()

            val postHtml = try {
                networkClient.okHttpClient.newCall(postRequest).execute().use { response ->
                    if (response.isSuccessful || response.code == 200) {
                        response.body?.string().orEmpty()
                    } else ""
                }
            } catch (_: Exception) { "" }

            if (postHtml.isNotBlank() && (postHtml.contains("twolekede") || postHtml.contains("mid=") || postHtml.contains("episode") || postHtml.contains("<a"))) {
                return@withContext Result.success(postHtml)
            }

            // Fallback: GET with Query Parameters and XMLHttpRequest header
            val url = "https://munowatch.com/moreepisodes".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("epsRange", range)
                .addQueryParameter("currentId", videoId)
                .addQueryParameter("range", range)
                .addQueryParameter("video_id", videoId)
                .addQueryParameter("mid", videoId)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://munowatch.com/twolekede?mid=$videoId")
                .header("X-Requested-With", "XMLHttpRequest")
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (isLoginRedirect(response)) {
                    return@withContext Result.failure(IOException("Authentication required"))
                }
                if (!response.isSuccessful && response.code != 200) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val html = response.body?.string() ?: ""
                Result.success(html)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractTwolekedeId(input: String): String? {
        if (input.isBlank() || input.startsWith("myvj_")) return null
        if (input.all { it.isDigit() }) return input
        Regex("""[?&](?:id|mid|video_id|movie_id|content_id)=([0-9]+)""").find(input)?.groupValues?.get(1)?.let { return it }
        if (input.contains("_ep") || input.contains("ep_")) {
            Regex("""_ep\d+_([0-9]+)""").find(input)?.groupValues?.get(1)?.let { return it }
            Regex("""_([0-9]{3,7})$""").find(input)?.groupValues?.get(1)?.let { return it }
            Regex("""ep_([0-9]{3,7})""").find(input)?.groupValues?.get(1)?.let { return it }
        }
        if (input.startsWith("series_") && !input.contains("_ep")) return null
        Regex("""_([0-9]{3,7})$""").find(input)?.groupValues?.get(1)?.let { return it }
        Regex("""ep_([0-9]{3,7})""").find(input)?.groupValues?.get(1)?.let { return it }
        if (input.contains("preview.php") || input.contains("download.php") || input.contains("twolekede")) {
            Regex("""(?:id|mid)=([0-9]+)""").find(input)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    suspend fun isStreamPlayable(url: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        try {
            val clean = url.replace("&amp;", "&").replace("&#38;", "&").trim()
            // Cryptographically signed movviez links from Twolekede are validated by token; skip probing to preserve rate limit quota
            if (clean.contains("movviez.com") && clean.contains("signature=")) {
                return@withContext true
            }

            val req = Request.Builder()
                .url(clean)
                .header("Range", "bytes=0-100")
                .header("Referer", if (clean.contains("twolekede") || clean.contains("movviez")) "https://twolekede.com/" else "https://munowatch.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .get()
                .build()

            val fastClient = networkClient.streamOkHttpClient.newBuilder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            fastClient.newCall(req).execute().use { response ->
                if (response.code in 400..499) {
                    return@withContext false
                }
                response.code in 200..399
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun resolveTwolekedeStream(
        episodeIdOrUrl: String,
        serverName: String? = null,
        expectedTitle: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val id = extractTwolekedeId(episodeIdOrUrl) ?: return@withContext null

            val isBackupServer = serverName?.contains("Backup", ignoreCase = true) == true ||
                serverName?.contains("Server 2", ignoreCase = true) == true
            val isDirectServer = serverName?.contains("Direct", ignoreCase = true) == true ||
                serverName?.contains("Alternate", ignoreCase = true) == true ||
                serverName?.contains("Server 3", ignoreCase = true) == true

            fun isGenericTitle(t: String): Boolean {
                val l = t.lowercase().trim()
                return l.contains("twolekede") || l == "preview" || l == "download" ||
                    l.contains("download translated") || l.contains("translated movies & series") ||
                    l.contains("movies online") || l == "home" || l.contains("all movies")
            }

            fun cleanStreamUrl(url: String?): String? {
                if (url.isNullOrBlank()) return null
                val clean = url.replace("&amp;", "&").replace("&#38;", "&").trim().let {
                    if (it.startsWith("//")) "https:$it" else it
                }
                return if (isValidStreamUrl(clean)) clean else null
            }

            fun urlMatchesExpectedTitle(url: String, expected: String): Boolean {
                if (expected.isBlank() ||
                    expected.startsWith("Movie #", ignoreCase = true) ||
                    expected.startsWith("Series #", ignoreCase = true) ||
                    expected.startsWith("Item #", ignoreCase = true)
                ) {
                    return true
                }
                // Check ?title= or &title= parameter
                val urlTitle = Regex("""[?&]title=([^&]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.let {
                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it.replace("+", " ") }
                }
                if (!urlTitle.isNullOrBlank() && !isGenericTitle(urlTitle)) {
                    if (parser.titlesMatch(urlTitle, expected)) return true
                    val baseExpected = parser.extractBaseSeriesName(expected).ifBlank { expected }
                    if (parser.titlesMatch(urlTitle, baseExpected)) return true
                    val cleanUrlT = parser.cleanForMatching(urlTitle)
                    val cleanExpT = parser.cleanForMatching(expected)
                    if (cleanUrlT.length >= 4 && cleanExpT.length >= 4 && (cleanUrlT == cleanExpT || cleanUrlT.replace(" ", "") == cleanExpT.replace(" ", ""))) {
                        return true
                    }
                    return false
                }

                // Check ?file= or &file= parameter
                val urlFile = Regex("""[?&]file=([^&]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.let {
                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it.replace("+", " ") }
                }
                if (!urlFile.isNullOrBlank()) {
                    val rawName = urlFile.substringAfterLast("/").substringBeforeLast(".")
                    val fileName = rawName
                        .replace(Regex("""^\d+_"""), "") // Remove leading upload timestamp prefix
                        .replace(Regex("""(?i)\b(?:x264|x265|hevc|1080p|720p|480p|hd|full\s*hd|mp4|mkv)\b"""), " ")
                        .replace(Regex("""(?i)\b(?:vj\s+[a-z0-9_\-\.]*|vj|jr|jingo|ice\s*p|emmy|ks|waza|nelly|lance|kimuli|ivo)\b"""), " ")
                        .replace("_", " ").replace("-", " ").replace(".", " ").trim()
                    if (fileName.length >= 2 && !isGenericTitle(fileName)) {
                        if (parser.titlesMatch(fileName, expected)) return true
                        val baseExpected = parser.extractBaseSeriesName(expected).ifBlank { expected }
                        if (parser.titlesMatch(fileName, baseExpected)) return true
                        val cleanExp = parser.cleanForMatching(expected)
                        val cleanFile = parser.cleanForMatching(fileName)
                        if (cleanExp.length >= 4 && cleanFile.length >= 4 && (cleanExp == cleanFile || cleanExp.replace(" ", "") == cleanFile.replace(" ", ""))) {
                            return true
                        }
                        return false
                    }
                }
                return true
            }

            fun fetchPreview(): String? {
                val previewUrl = "https://twolekede.com/preview.php?id=$id"
                val prevRequest = Request.Builder()
                    .url(previewUrl)
                    .header("Referer", "https://twolekede.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .get()
                    .build()

                val fastClient = networkClient.okHttpClient.newBuilder()
                    .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val previewHtml = try {
                    fastClient.newCall(prevRequest).execute().use { response ->
                        if (response.isSuccessful || response.code == 200) {
                            response.body?.string().orEmpty()
                        } else ""
                    }
                } catch (_: Exception) { "" }

                if (previewHtml.isNotBlank()) {
                    val srcMatch = Regex("""<source\s+[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(previewHtml)?.groupValues?.get(1)
                        ?: Regex("""<video\s+[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(previewHtml)?.groupValues?.get(1)
                        ?: Regex("""https?://(?:cdn|dl)\.movviez\.com/secure\.php\?[^\s"'<>]+""").find(previewHtml)?.value
                        ?: Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""").find(previewHtml)?.value
                    if (!srcMatch.isNullOrBlank()) {
                        val clean = cleanStreamUrl(srcMatch)
                        if (clean != null) {
                            if (expectedTitle.isNullOrBlank() || urlMatchesExpectedTitle(clean, expectedTitle)) {
                                return clean
                            }
                        }
                    }
                }
                return null
            }

            fun fetchDownload(): String? {
                val downloadUrl = "https://twolekede.com/download.php?id=$id"
                val formBody = FormBody.Builder()
                    .add("submit", "Download")
                    .add("submit", "download")
                    .build()
                val dlRequest = Request.Builder()
                    .url(downloadUrl)
                    .header("Referer", downloadUrl)
                    .header("Origin", "https://twolekede.com")
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .post(formBody)
                    .build()

                val noRedirectClient = networkClient.okHttpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val directLocation = try {
                    noRedirectClient.newCall(dlRequest).execute().use { response ->
                        if (response.code in 300..399) {
                            val loc = response.header("Location")
                            if (loc == "index.php" || loc == "/index.php" || loc?.contains("index.php") == true) null
                            else loc
                        } else if (response.code == 200) {
                            val contentType = response.header("Content-Type").orEmpty().lowercase()
                            if (contentType.contains("video") || contentType.contains("octet-stream")) {
                                return@use downloadUrl
                            }
                            val body = if (contentType.contains("text") || contentType.contains("html")) {
                                try {
                                    val source = response.body?.source()
                                    source?.request(65536L)
                                    val buffer = source?.buffer
                                    buffer?.clone()?.readUtf8() ?: response.body?.string().orEmpty()
                                } catch (_: Exception) {
                                    response.body?.string().orEmpty()
                                }
                            } else ""
                            if (body.isBlank()) return@use null

                            val rawUrl = Regex("""href=["'](https?://[^"']*(?:secure\.php|\.mp4)[^"']*)["']""").find(body)?.groupValues?.get(1)
                                ?: Regex("""https?://(?:cdn|dl)\.movviez\.com/secure\.php\?[^\s"'<>]+""").find(body)?.value
                            rawUrl
                        } else null
                    }
                } catch (_: Exception) { null }

                if (!directLocation.isNullOrBlank()) {
                    val clean = cleanStreamUrl(directLocation)
                    if (clean != null) {
                        if (expectedTitle.isNullOrBlank() || urlMatchesExpectedTitle(clean, expectedTitle)) {
                            return clean
                        }
                    }
                }
                return null
            }

            val preferDataSaver = com.example.data.repository.MediaCache.getPreferredStreamQuality().equals("360P", ignoreCase = true)

            if (isBackupServer) {
                // Server 2 (Backup): Try download route first, fall back to streaming preview
                val dl = cleanStreamUrl(fetchDownload())
                if (dl != null) return@withContext dl
                val prev = cleanStreamUrl(fetchPreview())
                if (prev != null) return@withContext prev
                return@withContext null
            }

            if (isDirectServer) {
                // Server 3 (Direct / Alternate): Try streaming preview first, fall back to download
                val prev = cleanStreamUrl(fetchPreview())
                if (prev != null) return@withContext prev
                val dl = cleanStreamUrl(fetchDownload())
                if (dl != null) return@withContext dl
                return@withContext null
            }

            // Server 1 (Fast CDN):
            // Always prefer dedicated streaming preview route (mode=preview) for video playback.
            // It points to the exact same full quality video file on cdn.movviez.com, but streams
            // via Cloudflare edge caching without triggering the daily file download bandwidth limit.
            val prev = cleanStreamUrl(fetchPreview())
            if (prev != null) return@withContext prev
            val dl = cleanStreamUrl(fetchDownload())
            if (dl != null) return@withContext dl

            return@withContext null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getWatchPageHtml(streamPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = streamPath.replace("&amp;", "&").trim()
            val numericId = extractTwolekedeId(cleanPath)

            val fullUrl = when {
                cleanPath.startsWith("http://") || cleanPath.startsWith("https://") -> cleanPath
                cleanPath.startsWith("preview.php") || cleanPath.startsWith("/preview.php") ||
                cleanPath.startsWith("download.php") || cleanPath.startsWith("/download.php") ||
                cleanPath.startsWith("episodes.php") || cleanPath.startsWith("/episodes.php") ||
                cleanPath.startsWith("moviedetails.php") || cleanPath.startsWith("/moviedetails.php") ||
                cleanPath.startsWith("seriesdetails.php") || cleanPath.startsWith("/seriesdetails.php") ||
                cleanPath.startsWith("movies.php") || cleanPath.startsWith("/movies.php") ||
                cleanPath.startsWith("series.php") || cleanPath.startsWith("/series.php") ||
                cleanPath.startsWith("search.php") || cleanPath.startsWith("/search.php") -> {
                    "https://twolekede.com/${cleanPath.removePrefix("/")}"
                }
                cleanPath.contains("twolekede.com") -> {
                    "https://twolekede.com/${cleanPath.substringAfter("twolekede.com").removePrefix("/")}"
                }
                cleanPath.startsWith("/") -> "https://munowatch.com$cleanPath"
                else -> "https://munowatch.com/$cleanPath"
            }

            val storedSessionId = sessionManager.getSessionId()?.takeIf { it.isNotBlank() }
                ?: com.example.data.session.SecureSessionManager.DEFAULT_SESSION_ID
            val referer = if (fullUrl.contains("twolekede.com")) "https://twolekede.com/" else "https://munowatch.com/home"
            val reqBuilder = Request.Builder()
                .url(fullUrl)
                .header("Referer", referer)
            if (fullUrl.contains("munowatch.com") && !storedSessionId.isNullOrBlank()) {
                reqBuilder.header("Cookie", "PHPSESSID=$storedSessionId")
            }
            val request = reqBuilder.get().build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                if (isLoginOrSubscriptionRedirect(response)) {
                    val isSub = isSubscriptionRequired(response)
                    val msg = if (isSub) "Munowatch subscription required to watch this title." else "Authentication required"
                    return@withContext Result.failure(IOException(msg))
                }
                if (!response.isSuccessful && response.code != 200) {
                    return@withContext Result.failure(IOException("HTTP ${response.code} loading watch page"))
                }
                val html = response.body?.string() ?: ""
                Result.success(html)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveStreamPathForId(contentId: String): String? = withContext(Dispatchers.IO) {
        try {
            val gridResult = getGridSlot("p", "4")
            if (gridResult.isSuccess) {
                val link = extractLinkForId(gridResult.getOrDefault(""), contentId)
                if (link != null) return@withContext link
            }
            val freeResult = getGridSlot("fr", "4")
            if (freeResult.isSuccess) {
                val link = extractLinkForId(freeResult.getOrDefault(""), contentId)
                if (link != null) return@withContext link
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractLinkForId(html: String, contentId: String): String? {
        if (html.isBlank() || contentId.isBlank()) return null
        val doc = org.jsoup.Jsoup.parse(html)
        val div = doc.selectFirst("#$contentId") ?: return null
        val link = if (div.tagName() == "a") div else div.parents().select("a[href*='twolekede']").first()
            ?: div.closest("a")
        return link?.attr("href")?.replace("&amp;", "&")
    }

    suspend fun getRemainingDays(): Result<Int?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://munowatch.com/remaining-days")
                .get()
                .build()

            networkClient.okHttpClient.newCall(request).execute().use { response ->
                checkSessionRedirect(response)
                if (!response.isSuccessful) return@withContext Result.success(null)
                val text = response.body?.string()?.trim() ?: return@withContext Result.success(null)
                val days = text.toIntOrNull()
                Result.success(days)
            }
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://munowatch.com/logout")
                .get()
                .build()
            networkClient.okHttpClient.newCall(request).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit) // Logout locally regardless
        }
    }

    suspend fun checkServerReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://munowatch.com/")
                .head()
                .build()
            networkClient.okHttpClient.newCall(request).execute().use { it.isSuccessful || it.code == 200 }
        } catch (e: Exception) {
            false
        }
    }

    fun isSubscriptionRequired(response: okhttp3.Response): Boolean {
        val finalUrl = response.request.url.toString()
        val priorUrl = response.priorResponse?.request?.url?.toString() ?: ""
        val location = response.header("Location") ?: ""
        return finalUrl.contains("/plan", ignoreCase = true) ||
                priorUrl.contains("/plan", ignoreCase = true) ||
                location.contains("/plan", ignoreCase = true)
    }

    fun isLoginOrSubscriptionRedirect(response: okhttp3.Response): Boolean {
        return isLoginRedirect(response) || isSubscriptionRequired(response)
    }

    fun isLoginRedirect(response: okhttp3.Response, markSessionExpired: Boolean = false): Boolean {
        val finalUrl = response.request.url.toString()
        val priorUrl = response.priorResponse?.request?.url?.toString() ?: ""
        val location = response.header("Location") ?: ""
        val isLogin = finalUrl.contains("/login", ignoreCase = true) ||
                priorUrl.contains("/login", ignoreCase = true) ||
                location.contains("/login", ignoreCase = true)
        if (isLogin && markSessionExpired) {
            sessionManager.markSessionExpired()
        }
        return isLogin
    }

    private fun checkSessionRedirect(response: okhttp3.Response) {
        isLoginRedirect(response, markSessionExpired = true)
    }

    companion object {
        @Volatile
        private var isCdnRateLimited = false
        @Volatile
        private var cdnRateLimitResetTime = 0L

        fun markCdnRateLimited() {
            android.util.Log.w("MunowatchApi", "CDN stream encountered 429 rate limit")
        }

        fun isCdnRateLimited(): Boolean {
            return false
        }

        fun resetCdnRateLimit() {
        }

        fun isValidStreamUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val clean = url.trim().lowercase()
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) return false
            // Disallow trailer CDN and trailer clips
            if (clean.contains("trailer256.b-cdn.net") ||
                clean.contains("intro_myvj") ||
                clean.contains("/trailer") ||
                clean.contains("_trailer") ||
                clean.contains("trailer.mp4")
            ) {
                return false
            }
            // Disallow HTML/PHP web pages that are redirects or error pages
            if (clean.contains("index.php") || clean.contains("login.php") ||
                clean.contains("movies.php") || clean.contains("series.php") ||
                clean.contains("moviedetails.php") || clean.contains("seriesdetails.php") ||
                clean.contains("episodes.php") ||
                (clean.contains("preview.php") && !clean.contains(".mp4") && !clean.contains(".m3u8")) ||
                (clean.contains("download.php") && !clean.contains(".mp4") && !clean.contains(".m3u8"))
            ) {
                return false
            }
            // Must be a recognized video stream or CDN endpoint
            return clean.contains(".mp4") || clean.contains(".m3u8") || clean.contains(".mpd") ||
                   clean.contains("movviez.com") || clean.contains("b-cdn.net") ||
                   clean.contains("secure.php")
        }
    }
}
