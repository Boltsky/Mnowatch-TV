package com.example.domain.recommendation

import com.example.domain.model.MediaItem
import kotlin.math.abs

/**
 * Intelligent recommendation engine for movies in Munowatch.
 *
 * Computes contextual similarity based on:
 * 1. Franchise / Sequel / Prequel / Title word overlap (highest weight)
 * 2. Genre and subgenre alignment (Action, Horror, Sci-Fi, Comedy, Drama, etc.)
 * 3. Veejay (VJ) translation match (Luganda translator preference)
 * 4. Story theme / description keyword similarity (aliens, survival, heist, crime, etc.)
 * 5. Release era / year proximity
 */
object MovieRecommendationEngine {

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "of", "in", "on", "at", "to", "for", "with",
        "from", "by", "about", "into", "over", "after", "part", "movie", "film", "full",
        "hd", "vj", "veejay", "luganda", "translated", "edition", "version", "official",
        "trailer", "episode", "episodes", "season", "series", "chapter", "vol", "volume",
        "one", "two", "three", "four", "five", "day", "night"
    )

    private val THEME_KEYWORDS = mapOf(
        "horror" to listOf("horror", "scary", "terror", "creature", "monster", "alien", "ghost", "demon", "devil", "dead", "blood", "kill", "haunted", "survival", "survive"),
        "sci-fi" to listOf("alien", "aliens", "creature", "space", "robot", "future", "futuristic", "earth", "planet", "galaxy", "technology", "dimension", "time", "apocalypse", "monster"),
        "action" to listOf("action", "fight", "war", "soldier", "battle", "assassin", "agent", "spy", "mission", "secret", "explosion", "martial", "cop", "police", "gun", "rescue", "revenge"),
        "crime" to listOf("crime", "mafia", "gang", "gangster", "cartel", "robbery", "heist", "murder", "detective", "prison", "jail", "drugs", "thief", "chase"),
        "comedy" to listOf("comedy", "funny", "laugh", "humor", "hilarious", "parody", "prank", "mischief", "crazy"),
        "romance" to listOf("love", "romance", "romantic", "heart", "marry", "marriage", "wedding", "relationship", "couple", "dating", "lovers"),
        "thriller" to listOf("thriller", "mystery", "secret", "conspiracy", "stalker", "investigation", "hostage", "escape", "danger", "deadly", "hunted", "threat"),
        "animation" to listOf("animation", "animated", "cartoon", "disney", "pixar", "anime", "kids", "magic", "kingdom", "dragon")
    )

    val KNOWN_GENRE_IDS = mapOf(
        "action" to 1,
        "horror" to 2,
        "adventure" to 7,
        "love story" to 8,
        "romance" to 8,
        "comedy" to 9,
        "crime" to 12,
        "family" to 13,
        "sci-fi" to 14,
        "sci fi" to 14,
        "martial arts" to 16,
        "kungu fu" to 16,
        "drama" to 17,
        "sport" to 18,
        "thriller" to 19,
        "animation" to 20,
        "korean" to 21,
        "indian" to 22
    )

    val KNOWN_VJ_IDS = mapOf(
        "junior" to 1,
        "emmy" to 2,
        "ice p" to 9,
        "icep" to 9,
        "tom" to 4,
        "ulio" to 38,
        "jingo" to 8,
        "shao khan" to 5,
        "sammy" to 26,
        "soul" to 57,
        "kevo" to 14,
        "mosco" to 50,
        "aaron" to 25,
        "aron" to 25,
        "lance" to 3
    )

    private val GENRE_SYNONYMS = mapOf(
        "love story" to "romance",
        "kungu fu" to "martial arts",
        "kung fu" to "martial arts",
        "korean" to "korean drama",
        "sci fi" to "sci-fi"
    )

    fun cleanTitle(title: String): String {
        var t = title
        t = t.replace(Regex("""\[.*?\]|\(.*?\)|<.*?>"""), " ")
        t = t.replace(Regex("""(?i)\b(?:1080p|720p|480p|4k|hd|full\s*hd|bluray|web-dl)\b"""), " ")
        t = t.replace(Regex("""(?i)\b(?:luganda|translated|english|swahili)\b"""), " ")
        t = t.replace(Regex("""(?i)\bby\s+vj\s+[a-z0-9\-_]+.*$"""), " ")
        t = t.replace(Regex("""(?i)\bvj\s+[a-z0-9\-_]+.*$"""), " ")
        t = t.replace(Regex("""(?i)^movie\s*#?\d+\s*"""), " ")
        return t.replace(Regex("""[_\-]+"""), " ").replace(Regex("""\s+"""), " ").trim()
    }

    fun extractSignificantWords(title: String): List<String> {
        val clean = cleanTitle(title).lowercase()
        return clean.split(Regex("""[^a-z0-9]+"""))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in STOP_WORDS }
    }

    fun inferGenre(item: MediaItem): String {
        val rawGenre = item.genre.trim().lowercase()
        if (rawGenre.isNotBlank() && rawGenre != "unknown" && rawGenre != "movie") {
            return GENRE_SYNONYMS[rawGenre] ?: rawGenre
        }

        val textToInspect = "${cleanTitle(item.title)} ${item.description}".lowercase()
        for ((genreName, keywords) in THEME_KEYWORDS) {
            if (keywords.any { textToInspect.contains(it) }) {
                return genreName
            }
        }
        return ""
    }

    private fun extractYear(item: MediaItem): Int? {
        val y = Regex("""\b(19\d{2}|20\d{2})\b""").find(item.year)?.value
            ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(item.title)?.value
        return y?.toIntOrNull()
    }

    /**
     * Compute a similarity score between target movie and candidate movie.
     * Higher score indicates higher relevance / relation.
     */
    fun scoreSimilarity(target: MediaItem, candidate: MediaItem): Int {
        var score = 0

        val cleanTargetTitle = cleanTitle(target.title).lowercase()
        val cleanCandTitle = cleanTitle(candidate.title).lowercase()

        // 1. Franchise / Sequel / Prequel / Title Keyword Overlap
        val targetWords = extractSignificantWords(target.title)
        val candWords = extractSignificantWords(candidate.title)

        if (targetWords.isNotEmpty() && candWords.isNotEmpty()) {
            val commonWords = targetWords.intersect(candWords.toSet())
            val matchRatio = commonWords.size.toFloat() / targetWords.size.toFloat()

            if (commonWords.size >= 2 || (targetWords.size == 1 && commonWords.size == 1 && commonWords.first().length >= 6)) {
                // High confidence franchise match (e.g. "A Quiet Place" & "A Quiet Place Part II", "Terminator Genisys" & "The Terminator")
                score += 120
            } else if (commonWords.size == 1) {
                val matchedWord = commonWords.first()
                if (matchedWord.length >= 5) {
                    score += 45
                } else {
                    score += 25
                }
            }

            if (matchRatio >= 0.6f) {
                score += 30
            }
        }

        // 2. Genre Alignment
        val targetGenre = inferGenre(target)
        val candGenre = inferGenre(candidate)
        if (targetGenre.isNotBlank() && candGenre.isNotBlank()) {
            if (targetGenre == candGenre) {
                score += 40
            } else if (targetGenre.contains(candGenre) || candGenre.contains(targetGenre)) {
                score += 30
            }
        }

        // 3. VJ (Luganda Translator) Match
        val cleanTargetVj = target.vj.lowercase().replace("vj", "").replace(".", "").trim()
        val cleanCandVj = candidate.vj.lowercase().replace("vj", "").replace(".", "").trim()
        if (cleanTargetVj.isNotBlank() && cleanCandVj.isNotBlank()) {
            if (cleanTargetVj == cleanCandVj || cleanTargetVj.contains(cleanCandVj) || cleanCandVj.contains(cleanTargetVj)) {
                score += 30
            }
        }

        // 4. Description / Story Theme Keywords Overlap
        if (target.description.isNotBlank() && candidate.description.isNotBlank()) {
            val targetDesc = target.description.lowercase()
            val candDesc = candidate.description.lowercase()
            var sharedThemes = 0
            for ((_, keywords) in THEME_KEYWORDS) {
                for (kw in keywords) {
                    if (targetDesc.contains(kw) && candDesc.contains(kw)) {
                        sharedThemes++
                    }
                }
            }
            score += (sharedThemes * 6).coerceAtMost(30)
        }

        // 5. Release Era / Year Proximity
        val targetYear = extractYear(target)
        val candYear = extractYear(candidate)
        if (targetYear != null && candYear != null) {
            val diff = abs(targetYear - candYear)
            if (diff == 0) {
                score += 15
            } else if (diff <= 2) {
                score += 8
            }
        }

        // 6. Quality Bonus (complete metadata preferred)
        if (candidate.posterUrl.isNotBlank()) score += 5
        if (candidate.description.isNotBlank()) score += 5

        return score
    }

    /**
     * Find related recommendations for a movie from a candidate pool.
     */
    fun findRecommendations(
        target: MediaItem,
        candidatePool: List<MediaItem>,
        limit: Int = 18
    ): List<MediaItem> {
        val cleanTargetTitle = cleanTitle(target.title).lowercase().trim()
        val targetId = target.id.trim()

        // Filter out:
        // - Same item ID
        // - Duplicate exact title
        // - Series containers
        // - Generic placeholders
        val filtered = candidatePool.filter { cand ->
            if (cand.id.trim() == targetId) return@filter false
            if (cand.isSeries) return@filter false
            val candClean = cleanTitle(cand.title).lowercase().trim()
            if (candClean.isBlank() || candClean == cleanTargetTitle) return@filter false
            if (candClean.startsWith("movie #", ignoreCase = true)) return@filter false
            true
        }.distinctBy { cleanTitle(it.title).lowercase().trim() }

        // Score each candidate
        val scored = filtered.map { cand ->
            cand to scoreSimilarity(target, cand)
        }

        // Sort by similarity score descending
        val sorted = scored.sortedWith(
            compareByDescending<Pair<MediaItem, Int>> { it.second }
                .thenByDescending { it.first.year }
        )

        val highRelevance = sorted.filter { it.second > 0 }.map { it.first }
        if (highRelevance.size >= limit) {
            return highRelevance.take(limit)
        }

        // If pool is small, backfill with remaining distinct movies
        val remaining = sorted.filter { it.second <= 0 }.map { it.first }
        return (highRelevance + remaining).take(limit)
    }
}
