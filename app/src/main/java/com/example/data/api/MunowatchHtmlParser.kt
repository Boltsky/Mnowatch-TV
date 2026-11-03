package com.example.data.api

import android.util.Base64
import com.example.domain.model.CategoryItem
import com.example.domain.model.CategoryType
import com.example.domain.model.DEFAULT_GENRE_CATEGORIES
import com.example.domain.model.DEFAULT_VJ_CATEGORIES
import com.example.domain.model.Episode
import com.example.domain.model.MediaItem
import com.example.domain.model.PlaybackSource
import com.example.domain.model.Season
import com.example.domain.model.SeriesDetail
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.nio.charset.StandardCharsets

class MunowatchHtmlParser {

    fun parseMediaCards(html: String): List<MediaItem> {
        if (html.isBlank()) return emptyList()
        val doc: Document = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        val containerIds = setOf(
            "grid-container", "list-container", "more-episodes", "content", "main", "app", "wrapper", "container",
            "twolekede", "watch", "play", "serie", "series", "card", "tile", "body", "list",
            "moviedetails.php", "seriesdetails.php", "episodes.php", "preview.php", "download.php",
            "movies.php", "series.php", "search.php", "index.php", "login.php", "categories.php"
        )

        // Select all candidate card elements or anchors
        val candidateElements = doc.select(
            "a[href*='twolekede'], a[href*='seriesdetails'], a[href*='moviedetails'], a[href*='watch'], a:has(img.tile-img), a:has(img[src*='media']), a:has(img[src*='mv-admin']), div.grid-item:has(a), a.content-item, div.card:has(img), div[class*='col']:has(img), #grid-container > a, a:has(img)"
        )

        val processedIds = mutableSetOf<String>()
        val processedHrefs = mutableSetOf<String>()

        for ((index, el) in candidateElements.withIndex()) {
            val link = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: el.closest("a")
            var href = (link?.attr("href") ?: "").replace("&amp;", "&").trim()
            if (href.startsWith("twolekede")) {
                href = "/$href"
            }

            // Skip navigational/system links
            if (href.isBlank() || href == "/" || href == "#" || href.contains("login") || href.contains("logout") || href.contains("register")) {
                continue
            }

            // Deduplicate if exact same href has already been processed
            if (processedHrefs.contains(href)) {
                continue
            }

            val img = el.selectFirst("img.tile-img, img[src*='media'], img[src*='uploads'], img[src*='posters'], img[src], img[data-src]")
                ?: run {
                    val parent = el.parent()
                    if (parent != null && parent.select("a[href]").size <= 1) {
                        parent.selectFirst("img.tile-img, img[src*='media'], img[src*='uploads'], img[src*='posters'], img[src], img[data-src]")
                    } else null
                }

            val rawImgSrc = img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")?.takeIf { it.isNotBlank() }
                ?: ""

            // Skip non-media images like logos, avatars, icons
            if (rawImgSrc.contains("logo") || rawImgSrc.contains("icon") || rawImgSrc.contains("social") || rawImgSrc.contains("favicon")) {
                continue
            }

            val posterUrl = if (isValidPosterUrl(rawImgSrc)) formatUrl(rawImgSrc) else ""

            // Extract real ID
            val extractedId = extractMediaId(el, link, href, containerIds, index)
            var uniqueId = extractedId
            var disambiguateIndex = 1
            while (processedIds.contains(uniqueId)) {
                uniqueId = "${extractedId}_$disambiguateIndex"
                disambiguateIndex++
            }

            processedIds.add(uniqueId)
            processedHrefs.add(href)

            val itemInfoTitle = el.selectFirst(".item-info h3, .content-info h3, h3.content-title, .movie-title, h3")?.text()?.trim()
                ?: el.selectFirst("a")?.selectFirst(".item-info h3, .content-info h3, h3.content-title, .movie-title, h3")?.text()?.trim()
            val urlTitle = extractTitleFromUrl(href)
            val headingTitle = el.selectFirst("h1, h2, h4, h5, p.font-bold, p.title, .title, p.font-semibold, p.truncate, span.title, .name")?.text()
            val linkTitle = link?.attr("title")?.takeIf { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
            val altTitle = img?.attr("alt")?.takeIf { it.isNotBlank() && !it.equals("image", true) && !it.equals("poster", true) && !it.equals("thumbnail", true) && !it.equals("photo", true) }
            val imgTitle = img?.attr("title")?.takeIf { it.isNotBlank() }

            val rawTitle = itemInfoTitle?.takeIf { it.isNotBlank() && !it.equals("Vj", ignoreCase = true) && !it.startsWith("Vj ", ignoreCase = true) }
                ?: urlTitle
                ?: altTitle
                ?: headingTitle?.takeIf { it.isNotBlank() && !it.equals("Vj", ignoreCase = true) && !it.startsWith("Vj ", ignoreCase = true) }
                ?: linkTitle
                ?: imgTitle
                ?: "Movie $uniqueId"

            val vjEl = el.selectFirst(".category-badge, .category-tag, .category, .item-info .category, .content-thumbnail .category-badge, .vj, .vj-name, span:contains(Vj), p:contains(Vj)")
                ?: el.selectFirst("a")?.selectFirst(".category-badge, .category-tag, .category, .vj")
                ?: run {
                    val parent = el.parent()
                    if (parent != null && parent.select("a[href]").size <= 1) {
                        parent.selectFirst(".category-badge, .category-tag, .vj, .vj-name, .category, span:contains(Vj), p:contains(Vj)")
                    } else null
                }
            val vjRawText = vjEl?.text()?.trim() ?: ""

            var vjName = if (vjRawText.isNotBlank() && (vjRawText.startsWith("Vj", ignoreCase = true) || vjRawText.startsWith("V.J", ignoreCase = true)) && vjRawText.length <= 30) {
                vjRawText
            } else {
                Regex("""\b(Vj\s+[A-Za-z0-9]+(?:\s+[A-Za-z0-9]+)?)\b""", RegexOption.IGNORE_CASE)
                    .find(vjRawText.ifBlank { el.text() })?.value
                    ?: if (vjRawText.isNotBlank() && vjRawText.length <= 25) vjRawText else ""
            }

            if (vjName.isBlank()) {
                val urlCategory = Regex("""(?i)[?&]category=([^&]+)""").find(href)?.groupValues?.get(1)
                    ?.let { try { java.net.URLDecoder.decode(it, "UTF-8").trim() } catch (_: Exception) { it.replace("+", " ").trim() } }
                if (!urlCategory.isNullOrBlank() && (urlCategory.startsWith("Vj", ignoreCase = true) || urlCategory.startsWith("V.J", ignoreCase = true) || urlCategory.length <= 25)) {
                    vjName = urlCategory
                }
            }

            if (vjName.isBlank()) {
                val foundInTitle = Regex("""\b(Vj\s+[A-Za-z0-9]+(?:\s+[A-Za-z0-9]+)?)\b""", RegexOption.IGNORE_CASE).find(rawTitle)?.value
                if (!foundInTitle.isNullOrBlank()) {
                    vjName = foundInTitle
                }
            }


            val rawYearCandidate = el.selectFirst(".year, .meta-info .year, .item-info p, .content-info p, span:matches(\\b(?:19|20)\\d{2}\\b), p:matches(\\b(?:19|20)\\d{2}\\b)")?.text()
                ?: el.parent()?.selectFirst(".year, span:matches(\\b(?:19|20)\\d{2}\\b), p:matches(\\b(?:19|20)\\d{2}\\b)")?.text()
                ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(el.text())?.groupValues?.get(1)
                ?: ""

            val year = if (rawYearCandidate.isNotBlank() && Regex("""\b(19\d{2}|20\d{2})\b""").containsMatchIn(rawYearCandidate)) {
                Regex("""\b(19\d{2}|20\d{2})\b""").find(rawYearCandidate)?.value ?: rawYearCandidate.trim()
            } else {
                Regex("""\b(19\d{2}|20\d{2})\b""").find(rawTitle)?.groupValues?.get(1)
                    ?: ""
            }

            val genre = el.selectFirst(".genre, span.genre, p.genre")?.text()
                ?: el.parent()?.selectFirst(".genre, span.genre")?.text()
                ?: ""

            val isExplicitMovie = href.contains("moviedetails.php", ignoreCase = true)
            val isExplicitSeries = href.contains("seriesdetails.php", ignoreCase = true) ||
                href.contains("episodes.php", ignoreCase = true)

            val isSeries = if (isExplicitMovie) {
                false
            } else if (isExplicitSeries) {
                true
            } else {
                href.contains("serie", ignoreCase = true) ||
                Regex("""(?i)\b(?:Season\s*\d+|Seasons\s*\d+|S\d+\s*E\d+|Episode\s*\d+)\b""").containsMatchIn(rawTitle) ||
                el.parents().any { it.id() == "twolekede-series-search-results" }
            }

            val cardDescription = el.selectFirst(".details-section .description, .description, .movie-details .description, .series-details .description, [id$='-drawer-slot'], .drawer-slot, [id*='drawer-slot'], .desc, .story, .overview, .synopsis, p.text-gray-400, p.text-gray-300, p.text-sm")?.text()?.trim()
                ?: el.nextElementSibling()?.takeIf { it.id().endsWith("-drawer-slot") || it.hasClass("drawer-slot") }?.text()?.trim()
                ?: el.attr("data-description").takeIf { it.isNotBlank() }
                ?: el.attr("data-overview").takeIf { it.isNotBlank() }
                ?: ""
            val cleanCardDesc = cleanDescription(cardDescription)

            val dataHref = link?.attr("data-href")?.takeIf { it.isNotBlank() }
                ?: el.attr("data-href").takeIf { it.isNotBlank() }
            val dataMid = link?.attr("data-mid")?.takeIf { it.isNotBlank() }
                ?: el.attr("data-mid").takeIf { it.isNotBlank() }
                ?: el.attr("data-id").takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }

            val effectiveHref = when {
                !dataHref.isNullOrBlank() && (dataHref.contains("mid=") || dataHref.contains("v=") || dataHref.contains("twolekede") || dataHref.contains(".php")) -> dataHref
                !dataMid.isNullOrBlank() && (!href.contains("mid=") && !href.contains("id=") && !href.contains("v=")) -> "/twolekede?mid=$dataMid"
                else -> href
            }

            val cleanEffective = effectiveHref.removePrefix("/")
            val finalStreamUrl = when {
                cleanEffective.startsWith("seriesdetails.php") || cleanEffective.startsWith("moviedetails.php") || cleanEffective.startsWith("episodes.php") -> "https://twolekede.com/$cleanEffective"
                effectiveHref.startsWith("https://") || effectiveHref.startsWith("http://") -> effectiveHref
                effectiveHref.contains("v=") -> if (effectiveHref.startsWith("/")) effectiveHref else "/$effectiveHref"
                extractedId.all { it.isDigit() } && (!effectiveHref.contains("mid=") && !effectiveHref.contains("id=") && !effectiveHref.contains("v=")) -> "/twolekede?mid=$extractedId"
                effectiveHref.startsWith("/") && effectiveHref.contains("mid=") -> effectiveHref
                effectiveHref.contains("mid=") -> "/${effectiveHref.removePrefix("/")}"
                extractedId.all { it.isDigit() } -> "/twolekede?mid=$extractedId"
                else -> effectiveHref
            }
            val resolvedIsSeries = if (isExplicitMovie || finalStreamUrl.contains("moviedetails.php", ignoreCase = true)) {
                false
            } else {
                isSeries || finalStreamUrl.contains("seriesdetails.php", ignoreCase = true) || finalStreamUrl.contains("episodes.php", ignoreCase = true)
            }

            items.add(
                MediaItem(
                    id = uniqueId,
                    title = cleanTitle(rawTitle),
                    description = cleanCardDesc,
                    posterUrl = posterUrl,
                    backdropUrl = posterUrl,
                    year = year,
                    genre = genre,
                    vj = vjName,
                    isSeries = resolvedIsSeries,
                    streamUrl = finalStreamUrl
                )
            )
        }

        // If no cards matched through candidate selectors, fallback to any anchor with an image
        if (items.isEmpty()) {
            val allAnchorsWithImages = doc.select("a:has(img)")
            for ((index, a) in allAnchorsWithImages.withIndex()) {
                var href = a.attr("href").replace("&amp;", "&").trim()
                if (href.isBlank() || href == "/" || href == "#") continue
                if (href.startsWith("twolekede")) href = "/$href"

                val img = a.selectFirst("img")
                val src = img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src") ?: ""
                if (src.contains("logo") || src.contains("icon") || src.contains("social")) continue

                val id = extractMediaId(a, a, href, containerIds, index)
                val urlTitle = extractTitleFromUrl(href)
                val alt = urlTitle
                    ?: a.attr("title").takeIf { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
                    ?: img?.attr("alt")?.takeIf { it.isNotBlank() && !it.equals("image", true) && !it.equals("poster", true) }
                    ?: a.text().takeIf { it.isNotBlank() }
                    ?: "Movie $id"

                val fallbackYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(alt)?.value
                    ?: ""

                val isFallbackExplicitMovie = href.contains("moviedetails.php", ignoreCase = true)
                val isFallbackExplicitSeries = href.contains("seriesdetails.php", ignoreCase = true) || href.contains("episodes.php", ignoreCase = true)
                val isFallbackSeries = if (isFallbackExplicitMovie) {
                    false
                } else if (isFallbackExplicitSeries) {
                    true
                } else {
                    href.contains("serie", ignoreCase = true) ||
                    Regex("""(?i)\b(?:Season\s*\d+|Seasons\s*\d+|S\d+\s*E\d+|Episode\s*\d+|EPS\s*\d+)\b""").containsMatchIn(alt)
                }

                items.add(
                    MediaItem(
                        id = id,
                        title = cleanTitle(alt),
                        posterUrl = formatUrl(src),
                        backdropUrl = formatUrl(src),
                        year = fallbackYear,
                        isSeries = isFallbackSeries,
                        streamUrl = href
                    )
                )
            }
        }

        return items
    }

    private fun extractMediaId(
        element: Element,
        link: Element?,
        href: String,
        containerIds: Set<String>,
        fallbackIndex: Int
    ): String {
        // Priority 1: Check query parameters for serie_id, mid, id, or content_id (UNAMBIGUOUS media identifiers)
        if (href.isNotBlank()) {
            if (href.contains("seriesdetails.php")) {
                val titleParam = Regex("""[?&]title=([^&]+)""").find(href)?.groupValues?.get(1)?.let {
                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                } ?: ""
                val catParam = Regex("""[?&]category=([^&]+)""").find(href)?.groupValues?.get(1)?.let {
                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                } ?: ""
                val cleanSlug = "${titleParam}_$catParam".replace(Regex("""[^a-zA-Z0-9_]"""), "_").trim('_')
                if (cleanSlug.isNotBlank()) return "series_$cleanSlug"
            }

            val serieMatch = Regex(
                """[?&](?:serie_id|series_id|serie)=([0-9]+)""",
                RegexOption.IGNORE_CASE
            ).find(href)
            if (serieMatch != null) {
                return "series_${serieMatch.groupValues[1]}"
            }

            val numQueryMatch = Regex(
                """[?&](?:mid|id|movie_id|content_id)=([0-9]+)""",
                RegexOption.IGNORE_CASE
            ).find(href)
            if (numQueryMatch != null) {
                val qId = numQueryMatch.groupValues[1]
                if (qId.isNotBlank() && qId.lowercase() !in containerIds) {
                    return qId
                }
            }
        }

        // Priority 2: Check element or inner div numeric ID (EXACT match for Munowatch grid cursor pagination)
        // Munowatch renders cards as: <a href="..."><div id="66784" class="...">...</div></a>
        val innerDivId = element.selectFirst("div[id]")?.attr("id")
            ?.takeIf { it.isNotBlank() && it.lowercase() !in containerIds }
        if (innerDivId != null && innerDivId.all { it.isDigit() }) return innerDivId

        val elementId = element.attr("id")
            ?.takeIf { it.isNotBlank() && it.lowercase() !in containerIds }
        if (elementId != null && elementId.all { it.isDigit() }) return elementId

        val linkChildDivId = link?.selectFirst("div[id]")?.attr("id")
            ?.takeIf { it.isNotBlank() && it.lowercase() !in containerIds }
        if (linkChildDivId != null && linkChildDivId.all { it.isDigit() }) return linkChildDivId

        val linkId = link?.attr("id")
            ?.takeIf { it.isNotBlank() && it.lowercase() !in containerIds }
        if (linkId != null && linkId.all { it.isDigit() }) return linkId

        if (href.isNotBlank()) {
            val vMatch = Regex("""[?&]v=([^&]+)""", RegexOption.IGNORE_CASE).find(href)
            if (vMatch != null) {
                val cleanV = vMatch.groupValues[1].filter { it.isLetterOrDigit() }.take(20)
                if (cleanV.isNotBlank()) return "v_$cleanV"
            }
        }

        // Priority 2b: Check data-mid, data-id, data-movie-id, data-content-id attributes on element or link
        val attrId = element.attr("data-mid").ifBlank { element.attr("data-id") }
            .ifBlank { element.attr("data-movie-id") }.ifBlank { element.attr("data-content-id") }
            .ifBlank { link?.attr("data-mid") ?: "" }.ifBlank { link?.attr("data-id") ?: "" }
            .ifBlank { link?.attr("data-movie-id") ?: "" }.ifBlank { link?.attr("data-content-id") ?: "" }
            .takeIf { it.isNotBlank() && it.lowercase() !in containerIds && it.all { c -> c.isDigit() } }
        if (attrId != null) return attrId

        // Check data-href
        val dHref = link?.attr("data-href").takeIf { !it.isNullOrBlank() } ?: element.attr("data-href").takeIf { it.isNotBlank() }
        if (!dHref.isNullOrBlank()) {
            val dMid = Regex("""[?&](?:mid|id)=([0-9]+)""", RegexOption.IGNORE_CASE).find(dHref)?.groupValues?.get(1)
            if (dMid != null && dMid.lowercase() !in containerIds) return dMid
        }

        // Priority 3: Non-numeric div ID if valid
        if (innerDivId != null) return innerDivId
        if (elementId != null) return elementId

        // Priority 4: Path segments or generic parameters
        if (href.isNotBlank()) {
            val cleanPath = href.substringBefore("?").trim('/')
            val segments = cleanPath.split('/')
            val lastSegment = segments.lastOrNull()
            if (!lastSegment.isNullOrBlank() &&
                lastSegment.lowercase() !in containerIds &&
                !lastSegment.endsWith(".php", ignoreCase = true) &&
                !lastSegment.equals("twolekede", ignoreCase = true) &&
                !lastSegment.equals("watch", ignoreCase = true) &&
                !lastSegment.equals("play", ignoreCase = true) &&
                !lastSegment.equals("list", ignoreCase = true) &&
                !lastSegment.equals("home", ignoreCase = true)
            ) {
                return lastSegment
            }

            val queryMatch = Regex(
                """[?&](?:mid|id|m|movie_id|item_id|content_id)=([a-zA-Z0-9_\-]+)""",
                RegexOption.IGNORE_CASE
            ).find(href)
            if (queryMatch != null) {
                val qId = queryMatch.groupValues[1]
                if (qId.isNotBlank() && qId.lowercase() !in containerIds && !qId.endsWith(".php", ignoreCase = true)) {
                    return qId
                }
            }

            val slug = href.replace("&amp;", "&").trim().removePrefix("/").replace(Regex("[^a-zA-Z0-9_]"), "_")
            if (slug.isNotBlank() && slug.lowercase() !in containerIds && slug != "twolekede" && !slug.endsWith("_php")) {
                return slug
            }
        }

        val titleSlug = element.selectFirst("h3, h4, .title, .content-title, .movie-title")?.text()
            ?.ifBlank { element.selectFirst("img")?.attr("alt") }
            ?.lowercase()?.trim()?.replace(Regex("""[^a-z0-9]"""), "_")?.trim('_')
            ?.take(25)
        return if (!titleSlug.isNullOrBlank() && titleSlug.length >= 3) {
            "item_${titleSlug}_$fallbackIndex"
        } else {
            "item_$fallbackIndex"
        }
    }

    fun isValidPosterUrl(src: String?): Boolean {
        if (src.isNullOrBlank()) return false
        val clean = src.trim().lowercase()
        if (clean.endsWith("mv-admin/") || clean.endsWith("mv-admin") || clean == "/" || clean == "#") return false
        if (clean.contains("logo") || clean.contains("icon") || clean.contains("avatar") || clean.contains("favicon")) return false
        return clean.contains(".jpg") || clean.contains(".jpeg") || clean.contains(".png") || clean.contains(".webp") || clean.contains("image") || clean.contains("uploads/posters")
    }

    fun extractMoviePosterFromWatchPage(html: String): String? {
        if (html.isBlank()) return null
        try {
            val doc = Jsoup.parse(html)

            // 1. OpenGraph and Twitter Meta Tags (highest quality on Munowatch)
            val ogImage = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim()
            if (!ogImage.isNullOrBlank() && isValidPosterUrl(ogImage)) {
                return formatUrl(ogImage)
            }
            val twitterImage = doc.selectFirst("meta[name='twitter:image']")?.attr("content")?.trim()
            if (!twitterImage.isNullOrBlank() && isValidPosterUrl(twitterImage)) {
                return formatUrl(twitterImage)
            }
            val linkImage = doc.selectFirst("link[rel='image_src']")?.attr("href")?.trim()
            if (!linkImage.isNullOrBlank() && isValidPosterUrl(linkImage)) {
                return formatUrl(linkImage)
            }

            // 2. Schema.org JSON-LD
            val ldScripts = doc.select("script[type='application/ld+json']")
            for (script in ldScripts) {
                val json = script.data()
                val imgMatch = Regex("""["']image["']\s*:\s*["']([^"']+)["']""").find(json)
                if (imgMatch != null) {
                    val url = imgMatch.groupValues[1]
                    if (url.isNotBlank() && isValidPosterUrl(url)) {
                        return formatUrl(url)
                    }
                }
            }

            // 3. Document poster / image elements (strictly scoped to main details container, excluding recommendation cards)
            val posterImg = doc.select(
                ".poster-section img, .series-poster img, .movie-poster img, #poster img, " +
                "img.movie-poster, img.series-poster, img.cover-img, img.poster, .details-section img, .poster-container img"
            ).firstOrNull { img ->
                val isInsideForbidden = img.parents().any { p ->
                    val pid = p.id().lowercase()
                    val pcls = p.className().lowercase()
                    val tag = p.tagName().lowercase()
                    tag in setOf("header", "nav", "footer", "aside") ||
                    pid.contains("recommend") || pcls.contains("recommend") ||
                    pid.contains("similar") || pcls.contains("similar") ||
                    pid.contains("popular") || pcls.contains("popular") ||
                    pid.contains("trending") || pcls.contains("trending") ||
                    pid.contains("grid") || pcls.contains("grid") ||
                    pid.contains("footer") || pcls.contains("footer") ||
                    pcls.contains("movie-card") || pcls.contains("episode-card") || pcls.contains("content-item") ||
                    pcls.contains("similar-card") || pcls.contains("similar-thumbnail")
                }
                val candidateSrc = img.attr("data-src").ifBlank { img.attr("data-original") }.ifBlank { img.attr("data-poster") }.ifBlank { img.attr("src") }
                !isInsideForbidden && isValidPosterUrl(candidateSrc)
            }
            val rawSrc = posterImg?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: posterImg?.attr("data-original")?.takeIf { it.isNotBlank() }
                ?: posterImg?.attr("data-poster")?.takeIf { it.isNotBlank() }
                ?: posterImg?.attr("src")?.takeIf { it.isNotBlank() }
            if (!rawSrc.isNullOrBlank() && isValidPosterUrl(rawSrc)) {
                return formatUrl(rawSrc)
            }
        } catch (_: Exception) {}
        return null
    }

    fun cleanEpisodeTitle(rawTitle: String, epNum: Int, seriesTitle: String = ""): String {
        var cleaned = cleanTitle(rawTitle)

        // Remove Material Icon ligatures
        val iconLigatures = listOf(
            "skip_next", "skip_previous", "play_arrow", "pause", "fast_forward", "fast_rewind",
            "forward_10", "replay_10", "fullscreen", "volume_up", "volume_off", "settings",
            "menu", "close", "arrow_back", "arrow_forward", "favorite", "favorite_border", "star"
        )
        for (icon in iconLigatures) {
            cleaned = cleaned.replace(icon, "", ignoreCase = true)
        }
        cleaned = cleaned.trim(' ', '-', '–', '—', ':', '|')

        // Check if the title is blank or is navigational/system boilerplate
        val systemWords = setOf(
            "latest movies", "movies", "series", "watchlist", "settings", "my settings",
            "logout", "login", "register", "home", "browse", "categories", "genres", "search",
            "all movies", "episodes", "episode", "eps", "download", "play", "watch"
        )
        if (cleaned.isBlank() || cleaned.lowercase() in systemWords ||
            cleaned.matches(Regex("""(?i)^(?:Season\s*\d+\s*)?(?:EPS|Episode|Ep|E)\s*\d+$""")) ||
            cleaned.matches(Regex("""(?i)^\d+\s*-\s*\d+$"""))
        ) {
            return "Episode $epNum"
        }

        // Handle S01E02 / Season 1 - Episode 2 prefixes first
        val sEPrefixRegex = Regex("""(?i)^S\d+[._\s\-]*E\d+\s*[-_:]?\s*(.+)""")
        val sEMatch = sEPrefixRegex.find(cleaned)
        if (sEMatch != null && sEMatch.groupValues[1].isNotBlank()) {
            cleaned = sEMatch.groupValues[1].trim()
        }

        // If the title starts or ends with the series name, remove that redundant part
        if (seriesTitle.isNotBlank() && !seriesTitle.matches(Regex("""(?i)^S\d+|^Episode\s*\d+"""))) {
            val cleanSeries = cleanTitle(seriesTitle).trim()
            if (cleaned.startsWith(cleanSeries, ignoreCase = true) && cleaned.length > cleanSeries.length) {
                cleaned = cleaned.substring(cleanSeries.length).trim(' ', '-', '–', '—', ':', '|')
            }
            if (cleaned.endsWith(cleanSeries, ignoreCase = true) && cleaned.length > cleanSeries.length) {
                cleaned = cleaned.substring(0, cleaned.length - cleanSeries.length).trim(' ', '-', '–', '—', ':', '|')
            }
        }

        val seasonEpPrefixRegex = Regex("""(?i)^(?:Season|Part|Pt)\s*\d+\s*[-_:]?\s*(Episode\s*\d+.*)""")
        val prefixMatch = seasonEpPrefixRegex.find(cleaned)
        if (prefixMatch != null) {
            cleaned = prefixMatch.groupValues[1].trim()
        }

        if (cleaned.isBlank() || cleaned.lowercase() in systemWords ||
            cleaned.matches(Regex("""(?i)^(?:(?:Season|Part|Pt)\s*\d+\s*)?(?:EPS|Episode|Ep|E)\s*\d+$"""))
        ) {
            return "Episode $epNum"
        }

        return cleaned
    }

    fun extractBaseSeriesName(title: String): String {
        var name = title
        name = name.replace(Regex("""\[.*?\]"""), " ")
        name = name.replace(Regex("""\(.*?\)|\[.*?\]"""), " ")
        name = name.replace(Regex("""(?i)\bS\d+[\s._\-]*E\d+.*$"""), " ")
        name = name.replace(Regex("""(?i)\b(?:Season|Seasons|Series|S)\s*[0-9]+.*$"""), " ")
        name = name.replace(Regex("""(?i)\b(?:Episode|EPS|Ep|E)\s*[0-9]+.*$"""), " ")
        name = name.replace(Regex("""(?i)\b(?:Part|Pt)\s*[0-9]+.*$"""), " ")
        name = name.replace(Regex("""(?i)\s+by\s+vj\s+.*$"""), " ")
        name = name.replace(Regex("""(?i)\s+vj\s+[a-z0-9\-_]+.*$"""), " ")
        name = name.replace(Regex("""(?i)\b(?:luganda|translated|hd|full\s*hd)\b"""), " ")
        return name.replace(Regex("""\s+"""), " ").trim(' ', '-', '–', '—', ':', '|', '.')
    }

    fun isUnrelatedContentOrDifferentSeries(itemTitle: String, combinedText: String, seriesTitle: String): Boolean {
        val cleanItem = cleanTitle(itemTitle).trim()
        val cleanSeries = cleanTitle(seriesTitle).trim()

        if (cleanItem.isBlank()) return true
        if (cleanSeries.isBlank() || cleanSeries.startsWith("Munowatch", ignoreCase = true) || cleanSeries.equals("Series", ignoreCase = true)) {
            val systemWords = setOf(
                "latest movies", "movies", "series", "watchlist", "settings", "my settings",
                "logout", "login", "register", "home", "browse", "categories", "genres", "search",
                "all movies", "play", "watch"
            )
            return cleanItem.lowercase() in systemWords
        }

        val baseSeries = extractBaseSeriesName(cleanSeries).lowercase().trim()
        if (baseSeries.length < 2) return false

        // If the item title itself starts with Episode, Season, S01, Part, etc., it belongs to the current series
        if (cleanItem.matches(Regex("""(?i)^(?:Episode|EPS|Ep|\bE\b|Season|S|Part|Pt)\s*\d+.*"""))) {
            return false
        }

        // Extract show name prefix if present before Season/Episode/S01E01 (e.g., in "Loki Season 2 Episode 1" -> "Loki")
        val prefixRegex = Regex("""(?i)^(.+?)\s*[-_:]?\s*(?:Season\s*\d+|S\d+\s*E\d+|Episode\s*\d+|EPS\s*\d+|Ep\s*\d+)""")
        val prefixMatch = prefixRegex.find(cleanItem) ?: prefixRegex.find(combinedText)

        if (prefixMatch != null) {
            val showPrefix = prefixMatch.groupValues[1]
                .replace(Regex("""\[.*?\]|\(.*?\)|(?i)\bby\s+vj\s+.*$"""), " ")
                .replace(Regex("""(?i)\bby\s+.*$"""), " ")
                .replace(Regex("""(?i)\bvj\s+[a-z0-9\-_]+.*$"""), " ")
                .trim(' ', '-', '–', '—', ':', '|')
            val cleanShowPrefix = cleanTitle(showPrefix)
                .replace(Regex("""(?i)\bvj\s+[a-z0-9\-_]+.*$"""), " ")
                .lowercase().trim()
            if (cleanShowPrefix.isNotBlank() && cleanShowPrefix.length >= 2) {
                // If show prefix is purely "Episode" or "Season", it belongs to the current series
                if (!cleanShowPrefix.matches(Regex("""^(?:season|episode|ep|eps|part|pt)\s*\d*$"""))) {
                    // Check if prefix matches our series base name
                    val matchesCurrent = cleanShowPrefix == baseSeries ||
                            cleanShowPrefix.startsWith("$baseSeries ") ||
                            cleanShowPrefix.startsWith("$baseSeries:") ||
                            cleanShowPrefix.startsWith("$baseSeries-") ||
                            baseSeries.startsWith("$cleanShowPrefix ") ||
                            baseSeries.startsWith("$cleanShowPrefix:") ||
                            baseSeries.startsWith("$cleanShowPrefix-")

                    if (!matchesCurrent) {
                        return true // Confirmed different series! E.g. "Loki" when viewing "Alchemy of Souls"
                    }

                    // Check for spin-offs or sequels with distinct sub-names (e.g. "Money Heist: Korea" vs "Money Heist")
                    val diff = cleanShowPrefix.replace(baseSeries, "").trim(' ', '-', '–', '—', ':', '|')
                    val diffWords = diff.split(Regex("""\s+""")).filter { w ->
                        w.isNotBlank() && !w.matches(Regex("""(?i)^(?:season|s|vj|part|pt|eps|ep|luganda|translated|\d+)$"""))
                    }
                    if (diffWords.isNotEmpty()) {
                        return true // Spin-off or different series!
                    }
                }
            }
        }

        // Numbers or "Part N" or "E N" in an episode list are valid episode titles
        if (cleanItem.matches(Regex("""^\d+$""")) ||
            cleanItem.matches(Regex("""(?i)^(?:part|pt|episode|ep|eps|e)\s*\d+$"""))
        ) {
            return false
        }

        // Check if the item is a standalone movie with no episode or season indicators at all (e.g. "Fast X", "Barbie")
        val hasEpisodeIndicator = Regex("""(?i)\b(?:episode|eps|ep|season|seasons|s\d+|e\d+|part\s*\d+|\be\s*\d+)\b""").containsMatchIn(combinedText) ||
                Regex("""(?i)\b(?:episode|eps|ep|season|seasons|s\d+|e\d+|part\s*\d+|\be\s*\d+)\b""").containsMatchIn(cleanItem)

        if (!hasEpisodeIndicator) {
            val itemLower = cleanItem.lowercase()
            val matchesSeries = itemLower == baseSeries ||
                    itemLower.startsWith("$baseSeries ") ||
                    itemLower.startsWith("$baseSeries:") ||
                    itemLower.startsWith("$baseSeries-")
            if (!matchesSeries) {
                return true // Unrelated standalone movie! E.g. "Fast X", "Barbie", "Oppenheimer"
            }
        }

        return false
    }

    fun fixShiftedEpisodeTitle(rawTitle: String, oldNum: Int, newNum: Int): String {
        val oldPadded = String.format("%02d", oldNum)
        val newPadded = String.format("%02d", newNum)
        return rawTitle
            .replace(Regex("""(?i)\bE\s*$oldPadded\b"""), "E$newPadded")
            .replace(Regex("""(?i)\bE\s*$oldNum\b"""), "E$newNum")
            .replace(Regex("""(?i)\bEpisode\s*$oldNum\b"""), "Episode $newNum")
            .replace(Regex("""(?i)\bEPS\s*$oldNum\b"""), "EPS $newNum")
            .replace(Regex("""(?i)\bPart\s*$oldNum\b"""), "Part $newNum")
    }

    fun parseSeriesEpisodes(
        seriesId: String,
        html: String,
        knownSeriesTitle: String? = null,
        knownPosterUrl: String? = null
    ): SeriesDetail {
        val doc = Jsoup.parse(html)
        val episodes = mutableListOf<Episode>()

        val rawTitle = doc.selectFirst(".details-section h1, .series-details h1, h1.series-title, h1")?.text()?.trim()
            ?: extractMovieTitleFromWatchPage(html, "")
            ?: doc.selectFirst("h2, .series-title, #series-title, .movie-title, #title")?.text()?.trim()
            ?: "Series"

        val resolvedTitle = if (!knownSeriesTitle.isNullOrBlank() && !knownSeriesTitle.startsWith("Munowatch", ignoreCase = true) && !knownSeriesTitle.equals("Series", ignoreCase = true)) {
            cleanTitle(knownSeriesTitle)
        } else {
            cleanTitle(rawTitle)
        }
        val baseTitle = extractBaseSeriesName(resolvedTitle)
        val cleanSeriesTitle = if (baseTitle.length >= 2) baseTitle else resolvedTitle

        // Strictly extract primary series poster from the main header/details section,
        // specifically excluding similar/recommended series cards at the bottom of the page
        val primaryPosterEl = doc.select(
            ".poster-section img, .poster-container img, .series-poster, img.series-poster, #poster img, .details-section img"
        ).firstOrNull { img ->
            val isInsideForbidden = img.parents().any { p ->
                val cls = p.className().lowercase()
                val id = p.id().lowercase()
                val tag = p.tagName().lowercase()
                tag in setOf("header", "nav", "footer", "aside") ||
                cls.contains("similar") || cls.contains("recommend") || cls.contains("related") ||
                cls.contains("popular") || cls.contains("trending") ||
                id.contains("similar") || id.contains("recommend") || id.contains("related") ||
                cls.contains("episode") || id.contains("episode")
            }
            val src = img.attr("data-src").ifBlank { img.attr("src") }
            !isInsideForbidden && isValidPosterUrl(src)
        }

        val rawPosterSrc = primaryPosterEl?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        val seriesPoster = when {
            !rawPosterSrc.isNullOrBlank() && isValidPosterUrl(rawPosterSrc) -> formatUrl(rawPosterSrc)
            !knownPosterUrl.isNullOrBlank() && isValidPosterUrl(knownPosterUrl) -> formatUrl(knownPosterUrl)
            else -> extractMoviePosterFromWatchPage(html)?.takeIf { isValidPosterUrl(it) } ?: (knownPosterUrl?.takeIf { isValidPosterUrl(it) } ?: "")
        }

        // Collect candidate episode links from all possible container types
        val episodeContainers = doc.select(
            ".episode-card, " +
            "#list-container .episode-box, #list-container a[href*='mid='], " +
            "[id*='more-episodes'] .episode-box, [id*='more-episodes-container'] .episode-box, " +
            "[id*='more-episodes'] a[href*='mid='], [id*='more-episodes-container'] a[href*='mid='], " +
            "#full-series-list-content .episode-box, #full-series-list-content a[href*='mid='], " +
            ".season-episodes-wrapper .episode-card, .season-episodes-wrapper .episode-box, " +
            ".season-episodes-wrapper a[href*='preview.php'], .season-episodes-wrapper a[href*='download.php'], " +
            "#twolekede-fallback-episodes .episode-card, #twolekede-series-data .episode-card, " +
            "#twolekede-fallback-episodes .season-episodes-wrapper .episode-card, " +
            "#cached-series-episodes a, #series-search-episodes a, " +
            ".following-episodes .episode-preview, .following-episodes a, " +
            ".episodes-grid .episode-preview, .episodes-grid a, " +
            ".episode-box, .episode-item"
        )

        var episodeElements = if (episodeContainers.isNotEmpty()) {
            episodeContainers
        } else {
            // Check links with explicit season/episode markers or stream URLs matching this series
            val potentialLinks = doc.select("a[href*='twolekede'], a[href*='mid='], a[href*='video_id=']")
            val explicitEpisodeLinks = potentialLinks.filter { a ->
                val isInsideForbidden = a.parents().any { p ->
                    val pid = p.id().lowercase()
                    val pcls = p.className().lowercase()
                    val tag = p.tagName().lowercase()
                    val isEp = pid.contains("episode") || pcls.contains("episode") || pid.contains("eps") || pcls.contains("eps")
                    if (isEp) return@any false
                    tag in setOf("header", "nav", "footer", "aside") ||
                    pid == "navbar" || pcls.contains("navbar") ||
                    pid == "header" || pcls.contains("header") ||
                    pid == "footer" || pcls.contains("footer") ||
                    pid.contains("recommend") || pcls.contains("recommend") ||
                    pid.contains("related") || pcls.contains("related") ||
                    pid.contains("popular") || pcls.contains("popular") ||
                    pid.contains("trending") || pcls.contains("trending") ||
                    pid == "latest" || pcls == "latest" || pid.contains("latest-movies") || pcls.contains("latest-movies")
                }
                if (isInsideForbidden) return@filter false

                val combinedText = "${a.attr("title")} ${a.text()} ${a.attr("href")}"
                val hasExplicitEpisode = Regex("""(?i)\b(?:S\d+[._\s\-]*(?:EPS|EP|E)?\s*\d+|Season\s*\d+\s*(?:Episode|EPS|Ep)?\s*\d+|Episode\s*\d+|EPS\s*\d+|\bE\d+\b|Part\s*\d+)""").containsMatchIn(combinedText)
                val baseSeries = extractBaseSeriesName(cleanSeriesTitle).lowercase().trim()
                val matchesSeries = baseSeries.isBlank() || cleanSeriesTitle.startsWith("Munowatch", ignoreCase = true) || cleanTitle(combinedText).lowercase().contains(baseSeries)
                hasExplicitEpisode && matchesSeries
            }
            if (explicitEpisodeLinks.isNotEmpty()) {
                val combinedList = (episodeContainers.toList() + explicitEpisodeLinks).distinctBy {
                    it.attr("href").ifBlank { it.attr("data-url") }.ifBlank { it.attr("data-mid") }
                }
                org.jsoup.select.Elements(combinedList)
            } else {
                episodeContainers
            }
        }

        var epCounter = 1
        for (el in episodeElements) {
            // Check for forbidden parents: header, nav, footer, sidebar, recommendations, related movies
            val isInsideForbiddenParent = el.parents().any { p ->
                val tag = p.tagName().lowercase()
                val pid = p.id().lowercase()
                val pcls = p.className().lowercase()
                val isEpisodeContainer = pid.contains("episode") || pcls.contains("episode") ||
                    pid.contains("eps") || pcls.contains("eps") || pid.contains("season") || pcls.contains("season") ||
                    pid.contains("list-container") || pid.contains("more-episodes") ||
                    pid.contains("cached-series") || pid.contains("series-search")
                if (isEpisodeContainer) return@any false

                tag == "header" || tag == "nav" || tag == "footer" || tag == "aside" ||
                pid == "navbar" || pcls.contains("navbar") || pcls.contains("nav-") ||
                pid == "header" || pcls.contains("header") ||
                pid == "footer" || pcls.contains("footer") ||
                pid.contains("sidebar") || pcls.contains("sidebar") ||
                pid.contains("more-like") || pcls.contains("more-like") ||
                pid.contains("similar") || pcls.contains("similar") ||
                pid.contains("recommend") || pcls.contains("recommend") ||
                pid.contains("related") || pcls.contains("related") ||
                pid.contains("popular") || pcls.contains("popular") ||
                pid.contains("trending") || pcls.contains("trending") ||
                pid == "latest" || pcls == "latest" || pid.contains("latest-movies") || pcls.contains("latest-movies") ||
                pid.contains("carousel") || pcls.contains("carousel") ||
                pcls.contains("swiper") || pcls.contains("slider") ||
                pid.contains("tray") || pcls.contains("tray") ||
                pid.contains("other") || pcls.contains("other")
            }
            if (isInsideForbiddenParent) {
                continue
            }

            // Exclude links inside sections with related/recommended movie headings
            val hasForbiddenHeading = el.parents().take(3).any { p ->
                val heading = p.selectFirst("h1, h2, h3, h4, h5, .title, .section-title")?.text()?.lowercase() ?: ""
                heading.contains("related") || heading.contains("recommend") || heading.contains("similar") ||
                heading.contains("more like") || heading.contains("other movies") || heading.contains("latest movies") ||
                heading.contains("you may also like")
            }
            if (hasForbiddenHeading) {
                continue
            }

            // Exclude any element inside recommendation/popular/content-grid sections unless inside dedicated episode container
            val isInsideRecommendation = el.parents().any { p ->
                val pid = p.id().lowercase()
                val pcls = p.className().lowercase()
                val isEpisodeContainer = pid.contains("episode") || pcls.contains("episode") ||
                    pid.contains("eps") || pcls.contains("eps") || pid.contains("season") || pcls.contains("season") ||
                    pid.contains("list-container") || pid.contains("more-episodes") ||
                    pid.contains("cached-series") || pid.contains("series-search")
                if (isEpisodeContainer) return@any false

                pid.contains("recommend") || pcls.contains("recommend") ||
                pid.contains("similar") || pcls.contains("similar") ||
                pid.contains("popular") || pcls.contains("popular") ||
                pid.contains("trending") || pcls.contains("trending") ||
                pcls.contains("movie-card") || pcls.contains("results-section")
            }
            if (isInsideRecommendation) {
                continue
            }

            val rawElHref = (el.attr("href").ifBlank { el.selectFirst("a")?.attr("href") ?: "" }).lowercase()
            if (rawElHref.contains("moviedetails.php") || rawElHref.contains("seriesdetails.php")) {
                continue
            }

            val isTwolekedeCard = el.hasClass("episode-card") || el.selectFirst(".episode-info") != null
            val link = if (isTwolekedeCard) {
                el.selectFirst("a[href*='preview.php'], a.preview-button, a[href*='download.php'], a.download-button, a[href]")
            } else if (el.tagName() == "a") {
                el
            } else {
                el.selectFirst("a")
            }

            var streamUrl = (link?.attr("href") ?: el.attr("href")).replace("&amp;", "&").trim()
            if (streamUrl.isBlank() || streamUrl == "#" || streamUrl.startsWith("javascript")) {
                streamUrl = (el.attr("data-url").ifBlank { el.attr("data-href") }).replace("&amp;", "&").trim()
            }
            if (streamUrl.isBlank() || streamUrl == "#" || streamUrl.startsWith("javascript")) {
                val elMid = el.attr("data-mid").ifBlank { el.attr("data-vidid") }.ifBlank { el.attr("data-id") }.ifBlank { el.id() }
                if (elMid.isNotBlank() && elMid.all { it.isDigit() }) {
                    streamUrl = "/twolekede?mid=$elMid"
                }
            }
            if (streamUrl.startsWith("twolekede")) {
                streamUrl = "/$streamUrl"
            } else if (streamUrl.startsWith("preview.php") || streamUrl.startsWith("download.php") || streamUrl.startsWith("episodes.php")) {
                streamUrl = "https://twolekede.com/${streamUrl.removePrefix("/")}"
            }

            // STRICT CHECK: Must NOT be a movie or series details link
            if (streamUrl.contains("moviedetails.php") || streamUrl.contains("seriesdetails.php")) {
                continue
            }

            // STRICT CHECK: Must be a playable stream URL
            val isPlayable = streamUrl.contains("twolekede") ||
                streamUrl.contains("preview.php") ||
                streamUrl.contains("download.php") ||
                Regex("""[?&](?:mid|video_id)=([0-9]+)""").containsMatchIn(streamUrl) ||
                (Regex("""[?&]id=([0-9]+)""").containsMatchIn(streamUrl) && (streamUrl.contains("preview.php") || streamUrl.contains("download.php"))) ||
                el.hasAttr("data-mid") || el.hasAttr("data-vidid")
            if (!isPlayable) {
                continue
            }

            // Discard known non-content URL paths
            val cleanLowerPath = streamUrl.substringBefore("?").lowercase().trimEnd('/')
            val forbiddenPaths = setOf(
                "/movies", "/series", "/watchlist", "/settings", "/logout", "/login", "/register",
                "/home", "/browse", "/categories", "/genre", "/vj", "/search", "/terms", "/privacy",
                "/contact", "/about", "/dmca"
            )
            if (cleanLowerPath in forbiddenPaths || streamUrl.contains("logout") || streamUrl.contains("login") || streamUrl.contains("register")) {
                continue
            }

            val midInUrl = Regex("""[?&](?:mid|id)=([0-9]+)""").find(streamUrl)?.groupValues?.get(1)
            val normalizedStreamUrl = streamUrl

            val attrTitle = el.attr("title").takeIf { it.isNotBlank() } ?: link?.attr("title")?.takeIf { it.isNotBlank() }
            val innerTitle = if (isTwolekedeCard) {
                el.selectFirst(".episode-title")?.text()?.takeIf { it.isNotBlank() }
                    ?: el.selectFirst("h3")?.text()?.takeIf { it.isNotBlank() }
            } else {
                el.selectFirst("h3, h4, p, span, .title")?.text()?.takeIf { it.isNotBlank() }
                    ?: link?.text()?.takeIf { it.isNotBlank() }
                    ?: el.text().takeIf { it.isNotBlank() }
            }

            val twolekedeH3 = if (isTwolekedeCard) el.selectFirst("h3")?.text()?.trim() ?: "" else ""

            val textOnly = (innerTitle ?: attrTitle ?: "").trim().lowercase()
            // Skip range buttons e.g. "1-20"
            if (textOnly.matches(Regex("""^\d+\s*-\s*\d+$"""))) {
                continue
            }
            // Skip navigation words
            val navWords = setOf(
                "latest movies", "movies", "series", "watchlist", "settings", "my settings", "logout", "login",
                "register", "home", "browse", "categories", "genres", "search", "all movies"
            )
            if (textOnly in navWords) {
                continue
            }

            val combinedTitleForDetection = listOfNotNull(twolekedeH3.takeIf { it.isNotBlank() }, attrTitle, innerTitle).joinToString(" ")
            val itemRawTitle = when {
                twolekedeH3.isNotBlank() && !innerTitle.isNullOrBlank() && !innerTitle.equals(twolekedeH3, ignoreCase = true) -> "$twolekedeH3: $innerTitle"
                twolekedeH3.isNotBlank() -> twolekedeH3
                !attrTitle.isNullOrBlank() && attrTitle.contains("Season", ignoreCase = true) -> attrTitle
                !innerTitle.isNullOrBlank() -> innerTitle
                !attrTitle.isNullOrBlank() -> attrTitle
                else -> "Episode $epCounter"
            }

            // Reject any movie or item that belongs to an entirely different series
            val isInsideDedicatedContainer = isTwolekedeCard || el.parents().any { p ->
                val pid = p.id().lowercase()
                val pcls = p.className().lowercase()
                pid.contains("episode") || pcls.contains("episode") ||
                pid.contains("list-container") || pid.contains("more-episodes") ||
                pid.contains("season") || pcls.contains("season")
            }
            if (!isInsideDedicatedContainer && isUnrelatedContentOrDifferentSeries(itemRawTitle, combinedTitleForDetection, cleanSeriesTitle)) {
                continue
            }

            // Extract season and episode number across combined text and URL
            val wrapperSeason = el.parents().firstOrNull { it.hasAttr("data-season") }?.attr("data-season")?.toIntOrNull()

            val hasExplicitEpisodeInText = Regex("""(?i)\b(?:EPS|Episode|Ep|\bE\b)\s*[:#._\s\-]*\s*([0-9]{1,4})\b""").containsMatchIn(combinedTitleForDetection) ||
                Regex("""(?i)\bS\d{1,2}[._\s\-]*(?:EPS|EP|E)\s*([0-9]{1,4})\b""").containsMatchIn(combinedTitleForDetection) ||
                Regex("""(?i)[?&]e(?:p|ps|isode)?=([0-9]{1,4})\b""").containsMatchIn(normalizedStreamUrl)

            // Season extraction (strictly 1..50; do not match 4-digit years like 2024!)
            val seasonMatch = Regex("""(?i)\b(?:Season|Seasons)\s*([0-9]{1,2})\b""").find(combinedTitleForDetection)
                ?: Regex("""(?i)\bS([0-9]{1,2})[._\s\-]*(?:EPS|EP|E|\d)""").find(combinedTitleForDetection)
                ?: Regex("""(?i)[?&](?:season|s)=([0-9]{1,2})\b""").find(normalizedStreamUrl)
                ?: if (hasExplicitEpisodeInText) Regex("""(?i)\b(?:Part|Pt)\s*([0-9]{1,2})\b""").find(combinedTitleForDetection) else null
            val parsedSeason = wrapperSeason ?: seasonMatch?.groupValues?.get(1)?.toIntOrNull()
            val detectedSeason = if (parsedSeason != null && parsedSeason in 1..50) parsedSeason else 1

            // Episode number extraction
            // 1. Scene pattern S01E02 / S1-E2 / S1.EPS2 / S01_E02
            val sceneMatch = Regex("""(?i)\bS\d{1,2}[._\s\-]*(?:EPS|EP|E)\s*([0-9]{1,4})\b""").find(combinedTitleForDetection)
                ?: Regex("""(?i)[?&]e(?:p|ps|isode)?=([0-9]{1,4})\b""").find(normalizedStreamUrl)

            // 2. Explicit EPS / Episode / Ep / E / Part marker
            val epMarkerMatch = (if (twolekedeH3.isNotBlank()) Regex("""(?i)\b(?:EPS|Episode|Ep|\bE\b)\s*[:#._\s\-]*\s*([0-9]{1,4})\b""").find(twolekedeH3) else null)
                ?: Regex("""(?i)\b(?:EPS|Episode|Ep|\bE\b)\s*[:#._\s\-]*\s*([0-9]{1,4})\b""").find(combinedTitleForDetection)
                ?: (if (twolekedeH3.isNotBlank()) Regex("""(?i)\b(?:Part|Pt)\s*[:#._\s\-]*\s*([0-9]{1,4})\b""").find(twolekedeH3) else null)
                ?: Regex("""(?i)\b(?:Part|Pt)\s*[:#._\s\-]*\s*([0-9]{1,4})\b""").find(combinedTitleForDetection)
                ?: Regex("""(?i)\b(?:EPS|Episode|Ep|\bE\b|Part|Pt)\s*[:#._\s\-]*\s*([0-9]{1,4})\b""").find(normalizedStreamUrl)

            // 3. Pure digit link text (e.g., pagination or episode numbers "1", "02", "15")
            val pureNumMatch = if (textOnly.matches(Regex("""^\d{1,4}$"""))) textOnly.toIntOrNull() else null

            // 4. Trailing number after dash or space, excluding years (1900..2099)
            val trailingMatch = Regex("""[-_:]\s*([0-9]{1,3})$""").find(combinedTitleForDetection)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""\b([0-9]{1,3})$""").find(combinedTitleForDetection)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it !in 1900..2099 }

            val extractedEpNum = sceneMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: epMarkerMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: pureNumMatch
                ?: trailingMatch
                ?: epCounter

            val epNum = if (extractedEpNum in 1..9999) extractedEpNum else epCounter

            val displayTitle = cleanEpisodeTitle(itemRawTitle, epNum, cleanSeriesTitle)

            // Extract episode poster / thumbnail from element attributes or child image
            val directThumbAttr = el.attr("data-poster")
                .ifBlank { el.attr("data-thumb") }
                .ifBlank { el.attr("data-img") }
                .ifBlank { el.attr("data-src") }
                .ifBlank { link?.attr("data-poster") ?: "" }
                .ifBlank { link?.attr("data-thumb") ?: "" }
                .ifBlank { link?.attr("data-img") ?: "" }
                .ifBlank { link?.attr("data-src") ?: "" }
                .ifBlank { el.parent()?.attr("data-poster") ?: "" }
                .ifBlank { el.parent()?.attr("data-thumb") ?: "" }
                .takeIf { it.isNotBlank() && !it.contains("logo") && !it.contains("icon") && !it.contains("avatar") }

            // Find image strictly scoped to this episode element or its immediate card container
            val epImg = el.selectFirst("img")
                ?: link?.selectFirst("img")
                ?: el.parent()?.let { parent ->
                    val pCls = parent.className().lowercase()
                    val pId = parent.id().lowercase()
                    if (pCls.contains("episode") || pCls.contains("col") || pCls.contains("card") || pCls.contains("item") || pId.contains("episode")) {
                        parent.children().firstNotNullOfOrNull { child ->
                            if (child.tagName() == "img") child
                            else child.selectFirst("img")
                        }
                    } else null
                }

            val rawEpThumb = directThumbAttr?.let { formatUrl(it) }
                ?: epImg?.let { img ->
                    img.attr("data-poster").ifBlank { img.attr("data-thumb") }.ifBlank { img.attr("data-src") }.ifBlank { img.attr("data-original") }.ifBlank { img.attr("src") }
                }?.let { formatUrl(it) }?.takeIf {
                    it.isNotBlank() && !it.contains("logo") && !it.contains("icon") && !it.contains("avatar") && !it.contains("favicon")
                }
            val epThumb = rawEpThumb ?: seriesPoster

            episodes.add(
                Episode(
                    id = "${seriesId}_s${detectedSeason}_ep${epNum}_${midInUrl ?: epCounter}",
                    number = epNum,
                    title = displayTitle,
                    thumbnail = epThumb,
                    streamUrl = normalizedStreamUrl
                )
            )
            epCounter++
        }

        // Deduplicate episodes: preserve distinct episode numbers and distinct IDs
        val seenKeys = mutableSetOf<String>()
        val uniqueEpisodes = mutableListOf<Episode>()

        for (ep in episodes) {
            val sNum = Regex("""_s([0-9]+)_""").find(ep.id)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val idParam = Regex("""[?&](?:mid|id)=([0-9]+)""").find(ep.streamUrl)?.groupValues?.get(1)
            val epKey = if (!idParam.isNullOrBlank()) {
                "s${sNum}_id_${idParam}"
            } else {
                "s${sNum}_ep_${ep.number}_${ep.streamUrl.substringBefore('#').trimEnd('/')}"
            }
            if (seenKeys.contains(epKey)) {
                continue
            }
            seenKeys.add(epKey)
            uniqueEpisodes.add(ep)
        }

        // Group into seasons
        val seasonMap = mutableMapOf<Int, MutableList<Episode>>()
        for (ep in uniqueEpisodes) {
            val sNum = Regex("""_s([0-9]+)_""").find(ep.id)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            seasonMap.getOrPut(sNum) { mutableListOf() }.add(ep)
        }

        // Check if other seasons are declared in HTML (e.g. Twolekede season cards)
        val declaredSeasons = mutableSetOf<Int>()
        doc.select(".season-item, .season-link, a[href*='episodes.php']").forEach { sEl ->
            val sHref = sEl.attr("href").ifBlank { sEl.selectFirst("a")?.attr("href") ?: "" }
            val sNum = Regex("""[?&]season=([0-9]+)""").find(sHref)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""Season\s*([0-9]+)""", RegexOption.IGNORE_CASE).find(sEl.text())?.groupValues?.get(1)?.toIntOrNull()
            if (sNum != null && sNum in 1..50) {
                declaredSeasons.add(sNum)
            }
        }
        for (sNum in declaredSeasons) {
            if (!seasonMap.containsKey(sNum)) {
                seasonMap[sNum] = mutableListOf()
            }
        }

        val seasonsList = if (seasonMap.isNotEmpty()) {
            seasonMap.entries.sortedBy { it.key }.map { (seasonNum, epList) ->
                // Sort episodes strictly in ascending order by episode number
                val sortedEpisodes = epList.sortedWith(
                    compareBy<Episode> { if (it.number > 0) it.number else Int.MAX_VALUE }
                        .thenBy { it.streamUrl }
                        .thenBy { it.id }
                )

                // Deduplicate strictly by stream URL / stream ID so different episodes sharing a parsed number are never dropped
                val deduplicatedEpisodes = mutableListOf<Episode>()
                val seenUrls = mutableSetOf<String>()
                for (ep in sortedEpisodes) {
                    val idParam = Regex("""[?&](?:mid|id)=([0-9]+)""").find(ep.streamUrl)?.groupValues?.get(1)
                    val epKey = idParam ?: ep.streamUrl.trimEnd('/')
                    if (seenUrls.add(epKey)) {
                        deduplicatedEpisodes.add(ep)
                    }
                }

                // Detect off-by-one gap shifting (e.g. uploader uploaded 5 files for S1 but mistakenly labeled file 2 as E03,
                // resulting in [1, 3, 4, 5, 6] where items after index 0 are shifted by +1)
                val rawNums = deduplicatedEpisodes.map { it.number }
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

                val seenNumbers = mutableSetOf<Int>()
                val finalEpisodes = deduplicatedEpisodes.mapIndexed { idx, ep ->
                    val finalNumber = if (isSingleGapShifted) {
                        idx + 1
                    } else if (ep.number > 0 && seenNumbers.add(ep.number)) {
                        ep.number
                    } else {
                        var candidate = idx + 1
                        while (seenNumbers.contains(candidate)) {
                            candidate++
                        }
                        seenNumbers.add(candidate)
                        candidate
                    }

                    val idParam = Regex("""[?&](?:mid|id)=([0-9]+)""").find(ep.streamUrl)?.groupValues?.get(1)
                    val uniqueId = if (!idParam.isNullOrBlank()) {
                        "${seriesId}_s${seasonNum}_ep_${finalNumber}_$idParam"
                    } else {
                        "${seriesId}_s${seasonNum}_ep_${finalNumber}_$idx"
                    }
                    val correctedTitle = if (isSingleGapShifted && ep.number != finalNumber) {
                        fixShiftedEpisodeTitle(ep.title, ep.number, finalNumber)
                    } else {
                        ep.title
                    }
                    val title = if (correctedTitle.isBlank() || correctedTitle.equals("Episode", ignoreCase = true) || correctedTitle.matches(Regex("""(?i)^Episode\s*\d+$"""))) {
                        "Episode $finalNumber"
                    } else {
                        correctedTitle
                    }
                    val epThumbnail = if (ep.thumbnail.isNotBlank() && !ep.thumbnail.contains("logo") && !ep.thumbnail.contains("avatar")) {
                        ep.thumbnail
                    } else {
                        seriesPoster
                    }
                    ep.copy(
                        id = uniqueId,
                        number = finalNumber,
                        title = title,
                        thumbnail = epThumbnail
                    )
                }

                Season(
                    number = seasonNum,
                    title = "Season $seasonNum",
                    episodes = finalEpisodes
                )
            }
        } else {
            val cleanSeriesId = seriesId.removePrefix("series_")
            val defaultStream = if (seriesId.contains("twolekede")) seriesId else "/twolekede?mid=$cleanSeriesId"
            listOf(
                Season(
                    number = 1,
                    title = "Season 1",
                    episodes = listOf(
                        Episode(
                            id = "${seriesId}_ep_1",
                            number = 1,
                            title = "Episode 1",
                            thumbnail = seriesPoster,
                            streamUrl = defaultStream
                        )
                    )
                )
            )
        }

        val parsedDesc = doc.selectFirst(".details-section .description, .series-details .description, .description")?.text()?.trim()
            ?.let { cleanDescription(it) }
            ?.takeIf { it.isNotBlank() }
            ?: extractMovieDescriptionFromWatchPage(html)
        val poster = if (seriesPoster.isNotBlank()) seriesPoster else (knownPosterUrl?.takeIf { isValidPosterUrl(it) } ?: "")
        val backdrop = doc.selectFirst("img.backdrop, img[src*='backdrop'], img[src*='cover'], .banner img")?.let { el ->
            val src = el.attr("data-src").ifBlank { el.attr("src") }
            if (isValidPosterUrl(src)) formatUrl(src) else null
        } ?: poster
        val vjText = doc.selectFirst(".details-section .category-tag, .meta-info .category-tag, .category-tag, .vj, .vj-name, [class*='vj'], strong:contains(VJ), span:contains(VJ)")?.text()?.trim() ?: ""
        val yearText = doc.selectFirst(".details-section .year, .meta-info .year, span.year")?.text()?.trim()
            ?: Regex("""\b(20\d\d|19\d\d)\b""").find(html)?.value ?: ""
        val genreList = doc.select(".genre, .badge, .tag, a[href*='genre']").map { it.text().trim() }.filter { it.isNotBlank() && it.length < 20 }.distinct()

        return SeriesDetail(
            id = seriesId,
            title = cleanSeriesTitle,
            description = parsedDesc,
            posterUrl = poster,
            backdropUrl = backdrop,
            year = yearText,
            vj = cleanTitle(vjText),
            genres = if (genreList.isNotEmpty()) genreList else listOf("Ugandan Translated", "Series"),
            seasons = seasonsList
        )
    }

    fun extractVideoSourceFromWatchPage(html: String, selectedServer: String? = null): PlaybackSource? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html)

        // 1. Primary extraction: decode data-gumite from #soul-info or any element
        val soulInfo = doc.selectFirst("#soul-info")
        val rawGumite = soulInfo?.attr("data-gumite")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("[data-gumite]")?.attr("data-gumite")?.takeIf { it.isNotBlank() }
            ?: Regex("""data-gumite=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
            ?: Regex("""atob\(\s*["']([A-Za-z0-9+/=]{20,})["']\s*\)""").find(html)?.groupValues?.get(1)

        var videoUrl: String? = null
        if (!rawGumite.isNullOrBlank()) {
            try {
                val decodedBytes = try {
                    java.util.Base64.getDecoder().decode(rawGumite)
                } catch (_: Throwable) {
                    try {
                        Base64.decode(rawGumite, Base64.DEFAULT)
                    } catch (_: Throwable) { null }
                }
                val decoded = decodedBytes?.let { String(it, StandardCharsets.UTF_8).trim('"', '\'', ' ', '\n', '\r') }
                if (!decoded.isNullOrBlank()) {
                    val streamInDecoded = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)(?:\?[^\s"'<>]*)?""").find(decoded)?.value
                        ?: Regex("""https?://[a-zA-Z0-9_\-\.]+\.b-cdn\.net/[^\s"'<>]+""").find(decoded)?.value
                    if (streamInDecoded != null) {
                        videoUrl = streamInDecoded
                    } else if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                        videoUrl = decoded
                    } else if (decoded.startsWith("//")) {
                        videoUrl = "https:$decoded"
                    } else if (decoded.contains(".b-cdn.net") || decoded.contains(".m3u8") || decoded.contains(".mp4")) {
                        videoUrl = if (decoded.startsWith("/")) "https://munowatch.com$decoded" else "https://$decoded"
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Secondary fallback: check video or source elements or data attributes
        if (videoUrl == null) {
            val sourceEl = doc.selectFirst("video source[src], source[src], video[src], [data-src*='.mp4'], [data-src*='.m3u8'], [data-src*='b-cdn'], [data-src*='movviez'], a.download-button[href*='download.php'], a.download-button[href*='.mp4']")
            val src = sourceEl?.attr("src")?.takeIf { it.isNotBlank() }
                ?: sourceEl?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: sourceEl?.attr("href")?.takeIf { it.isNotBlank() }
            if (src != null && (src.startsWith("http") || src.startsWith("//") || src.contains(".mp4") || src.contains(".m3u8") || src.contains("b-cdn") || src.contains("movviez"))) {
                videoUrl = if (src.startsWith("//")) "https:$src" else if (src.startsWith("/")) "https://twolekede.com$src" else src
            }
        }

        // 2b. Check iframe video embeds
        if (videoUrl == null) {
            val iframeSrc = doc.selectFirst("iframe[src*='.mp4'], iframe[src*='.m3u8'], iframe[src*='b-cdn'], iframe[data-src*='.mp4'], iframe[data-src*='.m3u8'], iframe[data-src*='b-cdn']")
                ?.let { it.attr("src").ifBlank { it.attr("data-src") } }
            if (!iframeSrc.isNullOrBlank()) {
                val cleanIframe = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else if (iframeSrc.startsWith("/")) "https://munowatch.com$iframeSrc" else iframeSrc
                videoUrl = cleanIframe
            }
        }

        // 3. Third fallback: Javascript configurations (JWPlayer, Video.js, Plyr, etc.)
        if (videoUrl == null) {
            val jsStreamRegex = Regex("""(?i)(?:file|source|src|streamUrl|videoUrl)\s*[:=]\s*["']((?:https?:)?//[^\s"']+\.(?:mp4|m3u8)[^\s"']*)["']""")
            val jsMatch = jsStreamRegex.find(html)?.groupValues?.get(1)
            if (jsMatch != null) {
                videoUrl = if (jsMatch.startsWith("//")) "https:$jsMatch" else jsMatch
            }
        }

        // 4. Fourth fallback: BunnyCDN stream URL in html
        if (videoUrl == null) {
            val cdnRegex = Regex("""(?:https?:)?//[a-zA-Z0-9_\-\.]+\.b-cdn\.net/[^\s"'<>]+""")
            val found = cdnRegex.find(html)?.value
            if (found != null) {
                videoUrl = if (found.startsWith("//")) "https:$found" else found
            }
        }

        // 5. Fifth fallback: Generic mp4/m3u8 stream url regex
        if (videoUrl == null) {
            val genericStreamRegex = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)(?:\?[^\s"'<>]*)?""")
            videoUrl = genericStreamRegex.find(html)?.value
        }

        if (videoUrl == null) return null

        val serverName = selectedServer ?: "Server 1 (Fast CDN)"

        // Multi-server CDN routing:
        // Server 1 uses primary fast CDN pull zone
        // Server 2 routes through backup pull zone (munotech2/munotech3)
        // Server 3 routes through alternate pull zone (harvetz/nkuba)
        var finalUrl = videoUrl
        val isDirectServer = serverName.contains("Direct", ignoreCase = true) ||
            serverName.contains("Alternate", ignoreCase = true) ||
            serverName.contains("Server 3", ignoreCase = true)
        val isBackupServer = !isDirectServer && (serverName.contains("Backup", ignoreCase = true) ||
            serverName.contains("Server 2", ignoreCase = true) ||
            serverName.contains("Munowatch", ignoreCase = true))

        if (!videoUrl.contains("/port/str/")) {
            if (isBackupServer && videoUrl.contains(".b-cdn.net")) {
                if (videoUrl.contains("nkuba.b-cdn.net")) {
                    finalUrl = videoUrl.replace("nkuba.b-cdn.net", "munotech2.b-cdn.net")
                } else if (videoUrl.contains("harvetz.b-cdn.net")) {
                    finalUrl = videoUrl.replace("harvetz.b-cdn.net", "munotech3.b-cdn.net")
                }
            } else if (isDirectServer && videoUrl.contains(".b-cdn.net")) {
                if (videoUrl.contains("nkuba.b-cdn.net")) {
                    finalUrl = videoUrl.replace("nkuba.b-cdn.net", "harvetz.b-cdn.net")
                } else if (videoUrl.contains("harvetz.b-cdn.net")) {
                    finalUrl = videoUrl.replace("harvetz.b-cdn.net", "nkuba.b-cdn.net")
                } else if (videoUrl.contains("munotech2.b-cdn.net")) {
                    finalUrl = videoUrl.replace("munotech2.b-cdn.net", "harvetz.b-cdn.net")
                } else if (videoUrl.contains("munotech3.b-cdn.net")) {
                    finalUrl = videoUrl.replace("munotech3.b-cdn.net", "harvetz.b-cdn.net")
                }
            }
        }

        // Sanitize and ensure valid https:// protocol on finalUrl
        finalUrl = finalUrl.trim().replace("\\/", "/").trim('"', '\'')
            .replace("&amp;", "&").replace("&#38;", "&")
        if (finalUrl.startsWith("//")) {
            finalUrl = "https:$finalUrl"
        } else if (finalUrl.startsWith("/")) {
            finalUrl = "https://munowatch.com$finalUrl"
        } else if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            finalUrl = "https://$finalUrl"
        }

        if (!MunowatchApi.isValidStreamUrl(finalUrl)) {
            return null
        }

        val isTwolekedeUrl = finalUrl.contains("twolekede.com") || finalUrl.contains("movviez.com")
        val referer = if (isTwolekedeUrl) "https://twolekede.com/" else "https://munowatch.com/"
        val origin = if (isTwolekedeUrl) "https://twolekede.com" else "https://munowatch.com"

        val extractedTitle = extractMovieTitleFromWatchPage(html, "")
            ?: doc.selectFirst("#title-block p, .title, h1")?.text()?.let { cleanTitle(it) }?.takeIf { it.isNotBlank() }
            ?: doc.title().substringBefore(" by ").trim().let { cleanTitle(it) }?.takeIf { it.isNotBlank() }

        return PlaybackSource(
            url = finalUrl,
            type = if (finalUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
            quality = "1080p Full HD",
            serverName = serverName,
            headers = mapOf(
                "Referer" to referer,
                "Origin" to origin,
                "User-Agent" to com.example.data.network.NetworkClient.USER_AGENT,
                "X-Title" to (extractedTitle ?: "")
            )
        )
    }

    fun formatUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("mv-admin/") || url.startsWith("/mv-admin/") || url.contains("twolekede") -> {
                val clean = url.removePrefix("/")
                "https://twolekede.com/$clean"
            }
            url.startsWith("/") -> "https://munowatch.com$url"
            else -> "https://munowatch.com/$url"
        }
    }

    fun cleanTitle(title: String): String {
        if (title.isBlank()) return ""
        var cleaned = title
        // URL decode only if explicitly percentage-encoded
        if (cleaned.contains("%")) {
            try {
                cleaned = java.net.URLDecoder.decode(cleaned, "UTF-8")
            } catch (_: Exception) {}
        }
        // Decode HTML entities
        cleaned = org.jsoup.parser.Parser.unescapeEntities(cleaned, true)

        // Remove branding and extra noise
        cleaned = cleaned.replace(Regex("""^Watch\s+translated(\s+in\s+luganda)?\s*[-|•:]*\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^Watch\s+luganda\s+translated\s*[-|•:]*\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^Watch\s+""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^translated\s+(?:in\s+luganda\s+)?[-|•:]*\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^Twolekede\s*[-|•:]*\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^Munowatch\s*[-|•:]*\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s*[-|•]\s*Munowatch.*$""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s*[-|•]\s*Twolekede.*$""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""(?i)\s*[-|•]\s*translated(\s+in\s+luganda|\s+by\s+.*)?.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s+translated\s+in\s+luganda.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)(?:^|\s+)by\s+Vj\s+[A-Za-z0-9]+.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)^by\s+.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s+by\s+[A-Za-z0-9]+.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)^VJ\s+[A-Za-z0-9]+.*$"""), "")
        cleaned = cleaned.replace(Regex("""\s+Online\s+Free.*$""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s+Full\s+Movie.*$""", RegexOption.IGNORE_CASE), "")

        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim(' ', '-', '–', '—', ':', '|', '.')
        val invalidGenericTitles = setOf(
            "munowatch", "twolekede", "home", "series", "movies", "free movies", "translated movies",
            "login", "register", "by", "vj", "video", "play", "watch", "download", "stream", "episodes", "episode",
            "watch translated", "watch translated movies", "watch translated series", "translated", "luganda", "luganda translated",
            "movies online", "tv shows", "movies online & tv shows", "movies online and tv shows", "watch movies online",
            "watch movies online & tv shows", "now playing", "watch online", "stream online", "online & tv shows"
        )
        val lower = cleaned.lowercase()
        if (lower in invalidGenericTitles || lower.contains("movies online") || lower.contains("tv shows") || cleaned.length <= 2) return ""
        return cleaned
    }

    fun cleanForMatching(title: String): String {
        if (title.isBlank()) return ""
        var t = title
        if (t.contains("%")) {
            try {
                t = java.net.URLDecoder.decode(t, "UTF-8")
            } catch (_: Exception) {}
        }
        t = org.jsoup.parser.Parser.unescapeEntities(t, true)
        t = t.replace(Regex("""(?i)^watch\s+(?:translated\s+in\s+luganda|luganda\s+translated|translated)\s*[-| :]*"""), "")
        t = t.replace(Regex("""(?i)\s*-\s*preview.*$"""), "")
        t = t.replace(Regex("""(?i)\bby\s+vj\s+.*$"""), "")
        t = t.replace(Regex("""(?i)\bvj\s+[a-z0-9_\-\.]+"""), "")
        t = t.replace(Regex("""\[.*?\]|\(.*?\)|<.*?>"""), "")
        t = t.replace(Regex("""(?i)\s+(?:19\d{2}|20\d{2})\s+(?:jr|2160p|1440p|1080p|720p|480p|360p|4k|hd|uhd|bluray|x264|x265)\b"""), " ")
        t = t.replace(Regex("""\s+[-–—|]\s*(?:19\d{2}|20\d{2})\s*$"""), " ")
        t = t.replace(Regex("""(?i)\b(?:2160p|1440p|1080p|720p|480p|360p|4k|hd|uhd|bluray|web-dl|x264|x265|jr)\b"""), "")
        // Normalize Roman numerals and parts
        t = t.replace(Regex("""(?i)\bpart\s+([0-9]+)\b"""), "pt $1")
        t = t.replace(Regex("""(?i)\bpart\s+i\b"""), "pt 1")
        t = t.replace(Regex("""(?i)\bpart\s+ii\b"""), "pt 2")
        t = t.replace(Regex("""(?i)\bpart\s+iii\b"""), "pt 3")
        t = t.replace(Regex("""(?i)\bpart\s+iv\b"""), "pt 4")
        t = t.replace(Regex("""(?i)\bpart\s+v\b"""), "pt 5")
        t = t.replace(Regex("""(?i)\bviii\b"""), "8")
        t = t.replace(Regex("""(?i)\bvii\b"""), "7")
        t = t.replace(Regex("""(?i)\bvi\b"""), "6")
        t = t.replace(Regex("""(?i)\biv\b"""), "4")
        t = t.replace(Regex("""(?i)\biii\b"""), "3")
        t = t.replace(Regex("""(?i)\bii\b"""), "2")
        t = t.replace(Regex("""(?i)\bix\b"""), "9")
        t = t.replace(Regex("""(?i)\bx\b"""), "10")
        t = t.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
        return t.replace(Regex("""\s+"""), " ").trim().lowercase()
    }

    fun titlesMatch(a: String, b: String): Boolean {
        val ca = cleanForMatching(a)
        val cb = cleanForMatching(b)
        if (ca.isBlank() || cb.isBlank()) return false
        if (ca == cb) return true

        // Series episode handling: if one or both titles have an episode indicator, compare base series and episode numbers
        val hasEpA = Regex("""(?i)\b(?:episode|eps|ep|season|seasons|s\d+[\s._\-]*e\d+|s\s*\d+|e\s*\d+)\b""").containsMatchIn(a)
        val hasEpB = Regex("""(?i)\b(?:episode|eps|ep|season|seasons|s\d+[\s._\-]*e\d+|s\s*\d+|e\s*\d+)\b""").containsMatchIn(b)
        if (hasEpA || hasEpB) {
            val baseSeriesA = extractBaseSeriesName(a)
            val baseSeriesB = extractBaseSeriesName(b)
            if (baseSeriesA.isNotBlank() && baseSeriesB.isNotBlank()) {
                val cleanBaseA = cleanForMatching(baseSeriesA)
                val cleanBaseB = cleanForMatching(baseSeriesB)
                if (cleanBaseA == cleanBaseB || cleanBaseA.replace(" ", "") == cleanBaseB.replace(" ", "")) {
                    if (hasEpA && hasEpB) {
                        val epA = Regex("""(?i)\bs\d+[\s._\-]*e(\d+)\b""").find(a)?.groupValues?.get(1)
                            ?: Regex("""(?i)\b(?:episode|eps|ep|e)\s*(\d+)\b""").find(a)?.groupValues?.get(1)
                        val epB = Regex("""(?i)\bs\d+[\s._\-]*e(\d+)\b""").find(b)?.groupValues?.get(1)
                            ?: Regex("""(?i)\b(?:episode|eps|ep|e)\s*(\d+)\b""").find(b)?.groupValues?.get(1)
                        if (epA != null && epB != null && epA != epB) {
                            return false
                        }
                    }
                    return true
                }
            }
        }

        // Compressed match (handles spacing differences like "super hero" vs "superhero", "hell fire" vs "hellfire", "spider man" vs "spiderman")
        val compA = ca.replace(" ", "")
        val compB = cb.replace(" ", "")
        if (compA == compB) return true

        // Article-stripped match (e.g. "The Ride" vs "Ride", "The Silence" vs "Silence", "A Quiet Place" vs "Quiet Place")
        val noArtA = ca.replace(Regex("""^(?:the|a|an)\s+"""), "")
        val noArtB = cb.replace(Regex("""^(?:the|a|an)\s+"""), "")
        if (noArtA.isNotBlank() && (noArtA == noArtB || noArtA.replace(" ", "") == noArtB.replace(" ", ""))) {
            return true
        }

        // Parenthesized alternate/alias title match (e.g. "Samurai Fury (Muromachi Outsiders)" vs "Muromachi Outsiders")
        val parensA = Regex("""\((.*?)\)""").findAll(a).map { cleanForMatching(it.groupValues[1]) }.filter { it.isNotBlank() }
        val parensB = Regex("""\((.*?)\)""").findAll(b).map { cleanForMatching(it.groupValues[1]) }.filter { it.isNotBlank() }
        for (p in parensA) {
            val pNoArt = p.replace(Regex("""^(?:the|a|an)\s+"""), "")
            if (p == cb || p.replace(" ", "") == compB || (pNoArt.isNotBlank() && pNoArt == noArtB)) return true
        }
        for (p in parensB) {
            val pNoArt = p.replace(Regex("""^(?:the|a|an)\s+"""), "")
            if (p == ca || p.replace(" ", "") == compA || (pNoArt.isNotBlank() && pNoArt == noArtA)) return true
        }

        // Subtitle / Separator handling (':', '-', '–', '—', '|')
        val hasSepA = a.contains(":") || a.contains("-") || a.contains("–") || a.contains("—") || a.contains("|")
        val hasSepB = b.contains(":") || b.contains("-") || b.contains("–") || b.contains("—") || b.contains("|")

        if (hasSepA && hasSepB) {
            val baseA = cleanForMatching(a.substringBefore(":").substringBefore("-").substringBefore("–").substringBefore("—").substringBefore("|"))
            val baseB = cleanForMatching(b.substringBefore(":").substringBefore("-").substringBefore("–").substringBefore("—").substringBefore("|"))
            val subA = cleanForMatching(a.substringAfter(":").substringAfter("-").substringAfter("–").substringAfter("—").substringAfter("|"))
            val subB = cleanForMatching(b.substringAfter(":").substringAfter("-").substringAfter("–").substringAfter("—").substringAfter("|"))

            if (baseA.isNotBlank() && baseB.isNotBlank() && (baseA == baseB || baseA.replace(" ", "") == baseB.replace(" ", ""))) {
                if (subA.isBlank() || subB.isBlank()) return true
                if (subA == subB || subA.replace(" ", "") == subB.replace(" ", "")) return true
                // Different subtitles for the same base franchise = DIFFERENT movies! (e.g. Spider-Man: Homecoming vs Spider-Man: Far From Home)
                return false
            }
        } else if (hasSepA || hasSepB) {
            // One has a separator, the other does not
            val (sepTitle, plainTitle) = if (hasSepA) Pair(a, cb) else Pair(b, ca)
            val base = cleanForMatching(sepTitle.substringBefore(":").substringBefore("-").substringBefore("–").substringBefore("—").substringBefore("|"))
            val sub = cleanForMatching(sepTitle.substringAfter(":").substringAfter("-").substringAfter("–").substringAfter("—").substringAfter("|"))

            if (base.isNotBlank() && (base == plainTitle || base.replace(" ", "") == plainTitle.replace(" ", ""))) {
                // If sub is purely non-substantive (e.g. "preview", "trailer", "vj junior", "1080p", "hd", "part 1" when numbers match), allow it
                val ignorableSubWords = setOf("preview", "trailer", "movie", "full", "hd", "4k", "2160p", "1440p", "1080p", "720p", "480p", "360p", "translated", "luganda", "twolekede", "vj", "junior", "jingo", "emmy", "icep", "ulio", "part", "pt", "cd", "1", "2", "3", "4", "5")
                val subWords = sub.split(" ").filter { it.isNotBlank() && it !in ignorableSubWords }
                if (subWords.isEmpty()) {
                    return true
                }
                // If the plain title contains the subtitle words (e.g. "Black Water Abyss" vs "Black Water: Abyss")
                val compPlain = plainTitle.replace(" ", "")
                val compBaseSub = "$base$sub".replace(" ", "")
                if (compPlain == compBaseSub) {
                    return true
                }
            }
        }

        // Strict numeric check: if one title has numeric tokens (e.g. sequels 2, 3, pt 1, etc.)
        // and the other does not, or they differ in numbers, they are DIFFERENT movies!
        val numTokensA = Regex("""\b(?:pt\s*)?(\d+)\b""").findAll(ca).map { it.groupValues[1] }.toList()
        val numTokensB = Regex("""\b(?:pt\s*)?(\d+)\b""").findAll(cb).map { it.groupValues[1] }.toList()
        val isPart1Match = (numTokensA == listOf("1") && numTokensB.isEmpty()) || (numTokensB == listOf("1") && numTokensA.isEmpty())
        if (!isPart1Match && numTokensA != numTokensB) {
            return false
        }

        // Token overlap: require minimum 2 tokens and exact match except minor connector/stopword/part-1 tokens
        val tokensA = ca.split(" ").filter { it.isNotBlank() }
        val tokensB = cb.split(" ").filter { it.isNotBlank() }
        val setA = tokensA.toSet()
        val setB = tokensB.toSet()
        if (setA == setB) return true

        val ignorableExtras = setOf(
            "the", "of", "and", "a", "an", "in", "on", "at", "for", "from", "to",
            "pt", "part", "1", "hd", "full", "movie", "translated", "luganda", "vj"
        )
        val nonPartSetA = if (isPart1Match) setA - setOf("pt", "part", "1") else setA
        val nonPartSetB = if (isPart1Match) setB - setOf("pt", "part", "1") else setB
        if (nonPartSetA.isNotEmpty() && nonPartSetA == nonPartSetB) return true

        val intersection = setA.intersect(setB)
        val minSize = minOf(setA.size, setB.size)

        if (minSize >= 1 && intersection.size == minSize) {
            val extraTokens = setA + setB - intersection
            if (extraTokens.all { it in ignorableExtras }) {
                return true
            }
        }

        return false
    }

    fun extractTitleFromUrl(url: String): String? {
        if (url.isBlank()) return null
        val match = Regex("""[?&]title=([^&#]+)""", RegexOption.IGNORE_CASE).find(url) ?: return null
        val raw = match.groupValues[1]
        return try {
            val decoded = java.net.URLDecoder.decode(raw.replace("+", " "), "UTF-8")
            cleanTitle(decoded).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            cleanTitle(raw.replace("+", " ")).takeIf { it.isNotBlank() }
        }
    }

    fun extractMovieTitleFromWatchPage(html: String, streamUrl: String = ""): String? {
        val titleFromUrl = extractTitleFromUrl(streamUrl)
        if (!titleFromUrl.isNullOrBlank() && !titleFromUrl.startsWith("Movie ", ignoreCase = true)) {
            return cleanTitle(titleFromUrl)
        }

        if (html.isNotBlank()) {
            try {
                val doc = Jsoup.parse(html)
                val headingTitle = doc.selectFirst(".details-section h1, .movie-details h1, .series-details h1, #title-block h1, h1.movie-title, h1.title, h1, h2.title")?.text()?.trim()
                if (!headingTitle.isNullOrBlank() && !headingTitle.startsWith("Movie ", ignoreCase = true) && !headingTitle.equals("Munowatch", ignoreCase = true)) {
                    val clean = cleanTitle(headingTitle)
                    if (clean.isNotBlank()) return clean
                }

                val metaOg = doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
                if (!metaOg.isNullOrBlank()) {
                    val clean = cleanTitle(metaOg)
                    if (clean.isNotBlank() && !clean.startsWith("Movie ", ignoreCase = true)) {
                        return clean
                    }
                }

                val metaTitle = doc.selectFirst("meta[name='title']")?.attr("content")?.trim()
                if (!metaTitle.isNullOrBlank()) {
                    val clean = cleanTitle(metaTitle)
                    if (clean.isNotBlank() && !clean.startsWith("Movie ", ignoreCase = true)) {
                        return clean
                    }
                }

                val docTitle = doc.title().trim()
                if (docTitle.isNotBlank()) {
                    val clean = cleanTitle(docTitle)
                    if (clean.isNotBlank() && !clean.equals("Munowatch", ignoreCase = true) && !clean.startsWith("Movie ", ignoreCase = true)) {
                        return clean
                    }
                }
            } catch (_: Exception) {}
        }

        return titleFromUrl
    }

    fun isGenericMunowatchText(text: String): Boolean {
        if (text.isBlank() || text.length < 15) return true
        val lower = text.lowercase()
        return lower.contains("katandika butandisi") ||
               lower.contains("twolekede ekyatuleese") ||
               lower.contains("twolekedde filimu enjogelele kubwerere") ||
               lower.contains("twolekede filimu enjogelele") ||
               lower.contains("get free luganda translated movies from your favourite vjs") ||
               lower.contains("filimu enjogelele kubwerere") ||
               lower.contains("ekiseera kyoona") ||
               lower.contains("woona wooli") ||
               lower.contains("munowatch | login") ||
               lower.contains("munowatch login") ||
               lower.contains("download munowatch app") ||
               lower.contains("sign in with google") ||
               lower.contains("sign in with apple") ||
               lower.contains("all rights reserved") ||
               lower.contains("terms of service") ||
               lower.contains("privacy policy") ||
               lower.contains("please sign in") ||
               lower.contains("enter your phone number") ||
               lower.contains("enter your email") ||
               lower.contains("forgot password")
    }

    fun cleanDescription(rawDesc: String): String {
        if (rawDesc.isBlank()) return ""
        var cleaned = org.jsoup.parser.Parser.unescapeEntities(rawDesc, true)
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (isGenericMunowatchText(cleaned)) {
            return ""
        }

        // Clean any leading labels and promotional prefixes while keeping actual plot/synopsis
        cleaned = cleaned.replace(Regex("""^(?:Watch|Stream)\s+[^.\-–—]*?\b(?:translated\s+in\s+Luganda|translated\s+movies\s+and\s+series\s+in\s+Luganda|by\s+VJ\s+[a-zA-Z0-9_\-]+|top\s+Veejays|Munowatch(?:\.com)?|exclusively)[^.\-–—]*?(?:\.|\s*[-–—:]\s*)\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^(?:Watch|Stream)\s+[^.]+?\s+(?:on|at)\s+Munowatch(?:\.com)?\.?\s*[-–—:]*\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^(?:Munowatch is your #?1 platform for translated movies and series in Luganda\.?)\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^(?:Katandika Butandisi\s*[-–—:]*\s*Twolekede Ekyatuleese\.?)\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^(?:Story|Synopsis|Overview|Description|Plot|Ebikwata|About(?:\s+Movie)?)\s*[:–—\-]\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.trim()

        return if (cleaned.length >= 15 && !isGenericMunowatchText(cleaned) && !isPlaceholderOrGenericDescription(cleaned)) cleaned else ""
    }

    fun isPlaceholderOrGenericDescription(text: String): Boolean {
        if (text.isBlank() || text.length < 15) return true
        if (isGenericMunowatchText(text)) return true
        val lower = text.lowercase().trim()
        if (lower.contains("translated in luganda by top veejays")) return true
        if (lower.contains("stream full 1080p hd exclusively on munowatch tv")) return true
        if (lower.contains("stream all full hd episodes exclusively on munowatch tv")) return true
        if (lower.contains("stream all full hd episodes exclusively")) return true
        if (lower.contains("exclusively on munowatch tv")) return true
        if (lower.contains("exclusively on munowatch")) return true
        if (lower.contains("platform for translated movies")) return true
        if (lower.startsWith("watch ") && (lower.contains("translated in luganda") || lower.contains("translated movies and series")) &&
            !lower.contains(" encounters ") && !lower.contains(" investigates ") && !lower.contains(" turns to ") && lower.length < 120
        ) {
            return true
        }
        return false
    }

    fun extractMovieDescriptionFromWatchPage(html: String): String {
        if (html.isBlank()) return ""
        try {
            val doc = Jsoup.parse(html)

            // 0. Twolekede movie and series details description section
            val twolekedeDesc = doc.selectFirst("div.description, .details-section .description, .movie-details .description, .series-details .description, .content-description, .movie-summary p, .series-summary p")?.text()?.trim()
            if (!twolekedeDesc.isNullOrBlank()) {
                val cleaned = cleanDescription(twolekedeDesc)
                if (cleaned.isNotBlank()) return cleaned
            }

            // 1. Munowatch drawer slot (the expandable drawer containing the movie details)
            val drawerSlot = doc.selectFirst(
                "[id$='-drawer-slot'], .drawer-slot, [id*='drawer-slot'], [data-id-part]"
            )
            val drawerText = drawerSlot?.selectFirst("p, span, div")?.text()?.trim()
                ?: drawerSlot?.text()?.trim()
            if (!drawerText.isNullOrBlank()) {
                val cleaned = cleanDescription(drawerText)
                if (cleaned.isNotBlank()) return cleaned
            }

            // 2. Direct description / overview / synopsis / story container elements on Munowatch
            val textStory = doc.selectFirst(
                "#description, .description, p.description, " +
                ".series-desc, .series-description, .series-synopsis, #series-desc, #series-description, " +
                ".series-summary, .series-about, .serie-desc, .serie-description, " +
                "#overview, .overview, p.overview, #synopsis, .synopsis, p.synopsis, " +
                "#story, .story, p.story, .film-story, .storyline, #storyline, " +
                "#plot, .plot, p.plot, [itemprop='description'], " +
                ".movie-summary, .about-movie, .film-desc, .movie-desc, #film-desc, #movie-desc, " +
                "#details-drawer, .details-drawer, #desc:not(a), .desc:not(a)"
            )?.text()?.trim()

            if (!textStory.isNullOrBlank()) {
                val cleaned = cleanDescription(textStory)
                if (cleaned.isNotBlank()) return cleaned
            }

            // 3. Schema.org JSON-LD description
            doc.select("script[type='application/ld+json']").forEach { script ->
                val content = script.data()
                val descMatch = Regex(""""description"\s*:\s*"([^"]+)"""").find(content)
                if (descMatch != null) {
                    val cd = cleanDescription(descMatch.groupValues[1])
                    if (cd.isNotBlank()) return cd
                }
            }

            // 4. Elements following section headings (e.g. Story, Synopsis, Overview, Description, Plot, Ebikwata)
            val headingNext = doc.select("h2, h3, h4, strong, b")
                .firstOrNull { it.text().contains(Regex("""(?i)\b(Story|Synopsis|Overview|Description|Plot|Ebikwata)\b""")) }
                ?.nextElementSibling()?.text()?.trim()
            if (!headingNext.isNullOrBlank()) {
                val cleaned = cleanDescription(headingNext)
                if (cleaned.isNotBlank()) return cleaned
            }

            // 5. Container paragraphs (e.g. #details p, .details p, #movie-details p, .movie-details p, .movie-info p, .film-info p)
            val containerP = doc.selectFirst("#details p, .details p, #movie-details p, .movie-details p, .movie-info p, .film-info p")
                ?.text()?.trim()
            if (!containerP.isNullOrBlank()) {
                val cleaned = cleanDescription(containerP)
                if (cleaned.isNotBlank()) return cleaned
            }

            // 6. OpenGraph / Twitter meta description
            val metaOg = doc.selectFirst("meta[property='og:description'], meta[name='twitter:description']")?.attr("content")?.trim()
            if (!metaOg.isNullOrBlank()) {
                val cleaned = cleanDescription(metaOg)
                if (cleaned.isNotBlank()) return cleaned
            }

            // 7. Meta description tag
            val metaDesc = doc.selectFirst("meta[name='description']")?.attr("content")?.trim()
            if (!metaDesc.isNullOrBlank()) {
                val cleaned = cleanDescription(metaDesc)
                if (cleaned.isNotBlank()) return cleaned
            }

            // 8. Any body paragraph with appropriate descriptive length (excluding recommendations and movie cards)
            val genericP = doc.select("p").firstOrNull { el ->
                val pText = el.text().trim()
                if (pText.length !in 35..1500 || isGenericMunowatchText(pText)) return@firstOrNull false
                val isInsideForbidden = el.parents().any { p ->
                    val pid = p.id().lowercase()
                    val pcls = p.className().lowercase()
                    val tag = p.tagName().lowercase()
                    tag in setOf("header", "nav", "footer", "aside") ||
                    pid.contains("recommend") || pcls.contains("recommend") ||
                    pid.contains("similar") || pcls.contains("similar") ||
                    pid.contains("popular") || pcls.contains("popular") ||
                    pid.contains("trending") || pcls.contains("trending") ||
                    pid.contains("grid") || pcls.contains("grid") ||
                    pcls.contains("card") || pcls.contains("item") ||
                    pcls.contains("content-item") || pcls.contains("results-section") ||
                    pcls.contains("season-episodes-wrapper") || pid.contains("fallback-episodes")
                }
                !isInsideForbidden
            }?.text()?.trim()
            if (!genericP.isNullOrBlank()) {
                val cleaned = cleanDescription(genericP)
                if (cleaned.isNotBlank()) return cleaned
            }
        } catch (_: Exception) {}

        return ""
    }

    fun extractMovieReviewFromWatchPage(html: String): String {
        return extractMovieDescriptionFromWatchPage(html)
    }

    fun extractMovieStory(
        html: String = "",
        title: String = "",
        vj: String = "",
        genre: String = "",
        year: String = ""
    ): String {
        return extractMovieDescriptionFromWatchPage(html)
    }

    fun extractYearFromTimestamp(text: String): String? {
        if (text.isBlank()) return null
        // 13-digit Unix millisecond timestamp (e.g. 1790814573702)
        val msMatch = Regex("""(?:\D|^)(1[5-9]\d{11})(?:\D|$)""").find(text)?.groupValues?.get(1)?.toLongOrNull()
        if (msMatch != null && msMatch in 1500000000000L..2100000000000L) {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = msMatch
            val y = cal.get(java.util.Calendar.YEAR)
            if (y in 2010..2030) return y.toString()
        }
        // 10-digit Unix second timestamp (e.g. 1790799890 or 1748194955)
        val secMatch = Regex("""(?:\D|^)(1[5-9]\d{8})(?:\D|$)""").find(text)?.groupValues?.get(1)?.toLongOrNull()
        if (secMatch != null && secMatch in 1500000000L..2100000000L) {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = secMatch * 1000L
            val y = cal.get(java.util.Calendar.YEAR)
            if (y in 2010..2030) return y.toString()
        }
        return null
    }

    fun parseTwolekedeCategories(html: String): List<CategoryItem> {
        if (html.isBlank()) return DEFAULT_VJ_CATEGORIES
        try {
            val doc = Jsoup.parse(html)
            val links = doc.select("a[href*='categories.php?name=']")
            val result = mutableListOf<CategoryItem>()
            val seen = mutableSetOf<String>()

            val defaultVjMap = DEFAULT_VJ_CATEGORIES.associateBy { it.name.lowercase().trim() }
            var nextCustomId = 200

            for (el in links) {
                val rawName = el.ownText().trim().ifBlank { el.text().trim() }
                val decodedName = if (rawName.isBlank()) {
                    val href = el.attr("href")
                    val param = href.substringAfter("name=", "").substringBefore("&")
                    try {
                        java.net.URLDecoder.decode(param, "UTF-8").trim()
                    } catch (_: Exception) {
                        param
                    }
                } else rawName

                if (decodedName.isBlank() || decodedName.all { it.isDigit() }) continue
                val lower = decodedName.lowercase()
                if (seen.contains(lower)) continue
                seen.add(lower)

                val matchedDefault = defaultVjMap[lower]
                    ?: defaultVjMap["vj $lower"]
                    ?: defaultVjMap[lower.removePrefix("vj ").trim()]

                val id = matchedDefault?.id ?: nextCustomId++
                val formattedName = matchedDefault?.name ?: decodedName

                result.add(
                    CategoryItem(
                        id = id,
                        name = formattedName,
                        pipe = "vj",
                        type = CategoryType.VJ
                    )
                )
            }

            // Ensure top/favorite VJs are always present even if missing from HTML page
            for (defaultVj in DEFAULT_VJ_CATEGORIES) {
                if (result.none { it.name.equals(defaultVj.name, ignoreCase = true) }) {
                    result.add(defaultVj)
                }
            }

            val priorityOrder = listOf(
                "vj junior", "vj emmy", "vj icep", "vj jingo", "vj ulio", "vj soul", "vj tom",
                "vj shao khan", "vj sammy", "vj kevo", "vj kevin", "vj hd", "vj dan de", "vj tonny",
                "vj muba", "vj lance", "vj freddie", "vj waza", "vj ronnage", "vj mark", "vj mosco"
            )
            val sorted = result.sortedWith(Comparator { a, b ->
                val idxA = priorityOrder.indexOf(a.name.lowercase().trim()).let { if (it == -1) 999 else it }
                val idxB = priorityOrder.indexOf(b.name.lowercase().trim()).let { if (it == -1) 999 else it }
                if (idxA != idxB) idxA.compareTo(idxB) else a.name.compareTo(b.name, ignoreCase = true)
            })

            return if (sorted.isNotEmpty()) sorted else DEFAULT_VJ_CATEGORIES
        } catch (_: Exception) {
            return DEFAULT_VJ_CATEGORIES
        }
    }

    fun parseTwolekedeGenres(html: String): List<CategoryItem> {
        if (html.isBlank()) return DEFAULT_GENRE_CATEGORIES
        try {
            val doc = Jsoup.parse(html)
            val links = doc.select("a[href*='genres.php?genre=']")
            val result = mutableListOf<CategoryItem>()
            val seen = mutableSetOf<String>()

            val defaultGenreMap = DEFAULT_GENRE_CATEGORIES.associateBy { it.name.lowercase().trim() }
            var nextCustomId = 300

            for (el in links) {
                val rawName = el.ownText().trim().ifBlank { 
                    el.text().substringBefore("(").trim()
                }
                val decodedName = if (rawName.isBlank()) {
                    val href = el.attr("href")
                    val param = href.substringAfter("genre=", "").substringBefore("&")
                    try {
                        java.net.URLDecoder.decode(param, "UTF-8").trim()
                    } catch (_: Exception) {
                        param
                    }
                } else rawName

                if (decodedName.isBlank() || decodedName.length < 2 || decodedName.length > 30) continue
                val lower = decodedName.lowercase()
                if (seen.contains(lower)) continue
                seen.add(lower)

                val matchedDefault = defaultGenreMap[lower]
                val id = matchedDefault?.id ?: nextCustomId++
                val formattedName = matchedDefault?.name ?: decodedName

                result.add(
                    CategoryItem(
                        id = id,
                        name = formattedName,
                        pipe = "g",
                        type = CategoryType.GENRE
                    )
                )
            }

            for (defaultGenre in DEFAULT_GENRE_CATEGORIES) {
                if (result.none { it.name.equals(defaultGenre.name, ignoreCase = true) }) {
                    result.add(defaultGenre)
                }
            }

            val priorityGenres = listOf(
                "action", "drama", "comedy", "horror", "love story", "adventure", "sci-fi",
                "thriller", "crime", "animation", "family", "martial arts", "romance", "sport"
            )
            val sorted = result.sortedWith(Comparator { a, b ->
                val idxA = priorityGenres.indexOf(a.name.lowercase().trim()).let { if (it == -1) 999 else it }
                val idxB = priorityGenres.indexOf(b.name.lowercase().trim()).let { if (it == -1) 999 else it }
                if (idxA != idxB) idxA.compareTo(idxB) else a.name.compareTo(b.name, ignoreCase = true)
            })

            return if (sorted.isNotEmpty()) sorted else DEFAULT_GENRE_CATEGORIES
        } catch (_: Exception) {
            return DEFAULT_GENRE_CATEGORIES
        }
    }
}

