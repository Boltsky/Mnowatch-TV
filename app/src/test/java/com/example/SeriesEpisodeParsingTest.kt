package com.example

import com.example.data.api.MunowatchHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesEpisodeParsingTest {

    private val parser = MunowatchHtmlParser()

    @Test
    fun parseSeriesEpisodes_sortsEpisodesInAscendingOrder() {
        val html = """
            <html>
                <body>
                    <h1 class="title">Squid Game</h1>
                    <div id="list-container">
                        <div class="episode-box">
                            <a href="/twolekede?mid=1004" title="Squid Game Episode 4">Episode 4</a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=1001" title="Squid Game Episode 1">Episode 1</a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=1010" title="Squid Game Episode 10">Episode 10</a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=1002" title="Squid Game Episode 2">Episode 2</a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val seriesDetail = parser.parseSeriesEpisodes("series_squid_game", html, "Squid Game")
        assertEquals(1, seriesDetail.seasons.size)
        val season1 = seriesDetail.seasons.first()
        assertEquals(4, season1.episodes.size)

        // Verify strictly ascending order: Episode 1, 2, 4, 10
        val numbers = season1.episodes.map { it.number }
        assertEquals(listOf(1, 2, 4, 10), numbers)
    }

    @Test
    fun parseSeriesEpisodes_handlesMultipleSeasonsAndSortsBothAscending() {
        val html = """
            <html>
                <body>
                    <h1 class="title">Money Heist</h1>
                    <div class="season-episodes-wrapper" data-season="2">
                        <div class="episode-card">
                            <div class="episode-info"><h3>EPS 2</h3></div>
                            <a href="https://twolekede.com/preview.php?id=2002">Play</a>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info"><h3>EPS 1</h3></div>
                            <a href="https://twolekede.com/preview.php?id=2001">Play</a>
                        </div>
                    </div>
                    <div class="season-episodes-wrapper" data-season="1">
                        <div class="episode-card">
                            <div class="episode-info"><h3>EPS 3</h3></div>
                            <a href="https://twolekede.com/preview.php?id=1003">Play</a>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info"><h3>EPS 1</h3></div>
                            <a href="https://twolekede.com/preview.php?id=1001">Play</a>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info"><h3>EPS 2</h3></div>
                            <a href="https://twolekede.com/preview.php?id=1002">Play</a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val seriesDetail = parser.parseSeriesEpisodes("series_money_heist", html, "Money Heist")
        assertEquals(2, seriesDetail.seasons.size)

        // Seasons sorted ascending: Season 1, Season 2
        assertEquals(1, seriesDetail.seasons[0].number)
        assertEquals(2, seriesDetail.seasons[1].number)

        // Season 1 episodes sorted ascending: 1, 2, 3
        val s1Numbers = seriesDetail.seasons[0].episodes.map { it.number }
        assertEquals(listOf(1, 2, 3), s1Numbers)

        // Season 2 episodes sorted ascending: 1, 2
        val s2Numbers = seriesDetail.seasons[1].episodes.map { it.number }
        assertEquals(listOf(1, 2), s2Numbers)
    }

    @Test
    fun parseSeriesEpisodes_doesNotDropEpisodesFromAdditionalBatches() {
        val html = """
            <html>
                <body>
                    <h1 class="title">Loki</h1>
                    <div class="episode-card">
                        <div class="episode-info"><h3>EPS 1</h3></div>
                        <a href="/twolekede?mid=5001">Play</a>
                    </div>
                    <div id="more-episodes-container-1-20">
                        <div class="episode-box">
                            <a href="/twolekede?mid=5002">Episode 2</a>
                        </div>
                    </div>
                    <div id="more-episodes-container-21-40">
                        <div class="episode-box">
                            <a href="/twolekede?mid=5003">Episode 3</a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val seriesDetail = parser.parseSeriesEpisodes("series_loki", html, "Loki")
        val episodes = seriesDetail.seasons.firstOrNull()?.episodes.orEmpty()
        assertEquals(3, episodes.size)
        assertEquals(listOf(1, 2, 3), episodes.map { it.number })
    }

    @Test
    fun parseSeriesEpisodes_strictlyRejectsMovieDetailsLinksAndRecommendations() {
        val html = """
            <html>
                <body>
                    <h1 class="title">Mobland Season 2: Episode 1</h1>
                    <div class="season-episodes-wrapper" data-season="2">
                        <div class="episode-card">
                            <div class="episode-info"><h3>EPS 1</h3></div>
                            <a href="https://twolekede.com/preview.php?id=4374" class="preview-button">Play</a>
                        </div>
                        <div class="content-grid">
                            <div class="content-item">
                                <a href="moviedetails.php?id=121771" title="Runner">
                                    <img src="uploads/posters/1790800093_6abd70ddcadda.jpg" />
                                    <h3>Runner</h3>
                                    <p>A former soldier is thrown into a brutal race against time...</p>
                                </a>
                            </div>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val seriesDetail = parser.parseSeriesEpisodes("series_mobland", html, "Mobland Season 2: Episode 1")
        // Title should be cleanly normalized to "Mobland"
        assertEquals("Mobland", seriesDetail.title)

        // Should have exactly 1 episode (the Mobland preview link), and NOT Runner (121771)
        val episodes = seriesDetail.seasons.firstOrNull()?.episodes.orEmpty()
        assertEquals(1, episodes.size)
        val ep1 = episodes.first()
        assertEquals(1, ep1.number)
        assertEquals("https://twolekede.com/preview.php?id=4374", ep1.streamUrl)
        assertTrue(!ep1.streamUrl.contains("121771"))
        assertTrue(!ep1.streamUrl.contains("moviedetails.php"))
    }

    @Test
    fun extractMovieDescription_rejectsRecommendationPlots() {
        val html = """
            <html>
                <body>
                    <div class="details-section">
                        <h1>Mobland</h1>
                    </div>
                    <div class="popular-movies">
                        <div class="movie-card">
                            <p>A former soldier is thrown into a brutal race against time when a critical medical delivery makes him and his unlikely partner the targets of a ruthless cartel.</p>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val desc = parser.extractMovieDescriptionFromWatchPage(html)
        // Should NOT extract Runner's plot from popular-movies
        assertTrue(!desc.contains("former soldier"))
    }

    @Test
    fun extractMoviePoster_rejectsRecommendationImages() {
        val html = """
            <html>
                <body>
                    <div class="details-section">
                        <h1>Mobland</h1>
                    </div>
                    <div class="content-grid">
                        <div class="content-item">
                            <img src="https://twolekede.com/mv-admin/uploads/posters/1790800093_6abd70ddcadda.jpg" />
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val poster = parser.extractMoviePosterFromWatchPage(html)
        assertTrue(poster == null || !poster.contains("1790800093"))
    }

    @Test
    fun parseMediaCards_parsesTwolekedeCategoryCardsCorrectly() {
        val html = """
            <div class="grid-container">
                <div class="grid-item">
                    <a href="moviedetails.php?id=16883">
                        <div class="poster-container">
                            <img src="mv-admin/uploads/posters/poster_1790814573702.jpg" alt="Edge of Fear">
                            <div class="item-info">
                                <h3>Edge of Fear</h3>
                                <p>2018</p>
                                <span class="category">VJ Junior</span>
                            </div>
                        </div>
                    </a>
                </div>
                <div class="grid-item">
                    <a href="moviedetails.php?id=121770">
                        <div class="poster-container">
                            <img src="mv-admin/uploads/posters/121770.jpg" alt="The Beekeeper">
                            <div class="item-info">
                                <h3>The Beekeeper</h3>
                                <p>2024</p>
                                <span class="category">VJ Junior</span>
                            </div>
                        </div>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val items = parser.parseMediaCards(html)
        assertEquals(2, items.size)
        assertEquals("Edge of Fear", items[0].title)
        assertEquals("2018", items[0].year)
        assertEquals("VJ Junior", items[0].vj)
        assertEquals("https://twolekede.com/mv-admin/uploads/posters/poster_1790814573702.jpg", items[0].posterUrl)
        assertEquals("https://twolekede.com/moviedetails.php?id=16883", items[0].streamUrl)

        assertEquals("The Beekeeper", items[1].title)
        assertEquals("2024", items[1].year)
        assertEquals("https://twolekede.com/moviedetails.php?id=121770", items[1].streamUrl)
    }

    @Test
    fun parseSeriesEpisodes_fixesSingleGapShiftedEpisodes() {
        val html = """
            <html>
                <body>
                    <h1 class="title">Peaky Blinders</h1>
                    <div class="season-episodes-wrapper" data-season="1">
                        <div class="episode-card">
                            <div class="episode-info"><h3>PEAKY BLINDERS S01 E01 VJ ULIO</h3></div>
                            <a href="https://twolekede.com/preview.php?id=4073">Play</a>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info"><h3>PEAKY BLINDERS S01 E03 VJ ULIO</h3></div>
                            <a href="https://twolekede.com/preview.php?id=4074">Play</a>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info"><h3>PEAKY BLINDERS S01 E04 VJ ULIO</h3></div>
                            <a href="https://twolekede.com/preview.php?id=4075">Play</a>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info"><h3>PEAKY BLINDERS S01 E05 VJ ULIO</h3></div>
                            <a href="https://twolekede.com/preview.php?id=4076">Play</a>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info"><h3>PEAKY BLINDERS S01 E06 VJ ULIO END</h3></div>
                            <a href="https://twolekede.com/preview.php?id=4077">Play</a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val seriesDetail = parser.parseSeriesEpisodes("series_peaky_blinders", html, "Peaky Blinders")
        assertEquals(1, seriesDetail.seasons.size)
        val season1 = seriesDetail.seasons.first()
        assertEquals(5, season1.episodes.size)

        // Episode numbers must be smoothly normalized to 1..5 without gaps
        val numbers = season1.episodes.map { it.number }
        assertEquals(listOf(1, 2, 3, 4, 5), numbers)

        // Episode 2 should point to ID 4074 and title should be adjusted to E02
        val ep2 = season1.episodes[1]
        assertEquals(2, ep2.number)
        assertTrue(ep2.streamUrl.contains("4074"))
        assertTrue(ep2.title.contains("E02") || ep2.title.contains("Episode 2"))
    }

    @Test
    fun testMediaCache_normalizeShiftedSeasons() {
        val s1Episodes = listOf(
            com.example.domain.model.Episode("ep1", 1, "Peaky Blinders S01 E01 VJ Ulio", "", "url1"),
            com.example.domain.model.Episode("ep2", 3, "Peaky Blinders S01 E03 VJ Ulio", "", "url2"),
            com.example.domain.model.Episode("ep3", 4, "Peaky Blinders S01 E04 VJ Ulio", "", "url3"),
            com.example.domain.model.Episode("ep4", 5, "Peaky Blinders S01 E05 VJ Ulio", "", "url4"),
            com.example.domain.model.Episode("ep5", 6, "Peaky Blinders S01 E06 VJ Ulio END", "", "url5")
        )
        val detail = com.example.domain.model.SeriesDetail(
            id = "series_peaky",
            title = "Peaky Blinders",
            seasons = listOf(com.example.domain.model.Season(1, "Season 1", s1Episodes))
        )
        val normalized = com.example.data.repository.MediaCache.normalizeShiftedSeasons(detail)
        val numbers = normalized.seasons.first().episodes.map { it.number }
        assertEquals(listOf(1, 2, 3, 4, 5), numbers)
        assertEquals("Peaky Blinders S01 E02 VJ Ulio", normalized.seasons.first().episodes[1].title)
    }

    @Test
    fun testMoneyHeist_parsesMultiSeasonsCorrectly() {
        val html = """
            <html>
                <body>
                    <h1 class="title">Money Heist</h1>
                    <div class="season-episodes-wrapper" data-season="5">
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 1</h3>
                                <div class="episode-title">Money Heist</div>
                            </div>
                            <div class="episode-actions">
                                <a href="preview.php?id=117518" class="preview-button">Watch</a>
                            </div>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 6</h3>
                                <div class="episode-title">Money Heist</div>
                            </div>
                            <div class="episode-actions">
                                <a href="preview.php?id=117523" class="preview-button">Watch</a>
                            </div>
                        </div>
                    </div>
                    <div class="season-episodes-wrapper" data-season="1">
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 1</h3>
                                <div class="episode-title">Money Heist</div>
                            </div>
                            <div class="episode-actions">
                                <a href="preview.php?id=1001" class="preview-button">Watch</a>
                            </div>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("series_money_heist", html, "Money Heist")
        assertEquals(2, detail.seasons.size)
        assertEquals(1, detail.seasons[0].number)
        assertEquals(5, detail.seasons[1].number)
        assertEquals(1, detail.seasons[0].episodes.size)
        assertEquals(2, detail.seasons[1].episodes.size)
        assertEquals(1, detail.seasons[1].episodes[0].number)
        assertEquals(6, detail.seasons[1].episodes[1].number)
    }

    @Test
    fun testMoneyHeist_withCachedSingleEpisodeAndFallback() {
        val html = """
            <html>
                <body>
                    <h1 class="title">Money Heist</h1>
                    <div id="cached-series-episodes">
                        <a href="/twolekede?mid=66784" title="Money Heist - Part 5: Episode 6">
                            <span>Money Heist - Part 5: Episode 6</span>
                        </a>
                    </div>
                    <div id="twolekede-fallback-episodes">
                        <div class="season-episodes-wrapper" data-season="5">
                            <div class="episode-card">
                                <div class="episode-info">
                                    <h3>Episode 1</h3>
                                    <div class="episode-title">Money Heist</div>
                                </div>
                                <div class="episode-actions">
                                    <a href="preview.php?id=117518" class="preview-button">Watch</a>
                                </div>
                            </div>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("series_money_heist", html, "Money Heist")
        assertEquals(1, detail.seasons.size)
        val s5 = detail.seasons.first()
        assertEquals(5, s5.number)
        assertEquals(2, s5.episodes.size)
        assertEquals(1, s5.episodes[0].number)
        assertEquals(6, s5.episodes[1].number)
        assertEquals("Episode 1", s5.episodes[0].title)
        assertEquals("Episode 6", s5.episodes[1].title)
    }

    @Test
    fun testMoneyHeist_parsesAllFiveSeasonsCorrectly() {
        val html = buildString {
            append("<html><body><h1 class=\"title\">Money Heist</h1>\n")
            // Seasons 1 to 5
            val seasonCounts = mapOf(1 to 13, 2 to 9, 3 to 8, 4 to 8, 5 to 10)
            for ((sNum, epCount) in seasonCounts) {
                append("<div class=\"season-episodes-wrapper\" data-season=\"$sNum\">\n")
                for (e in 1..epCount) {
                    append("""
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode $e</h3>
                                <div class="episode-title">Money Heist</div>
                            </div>
                            <div class="episode-actions">
                                <a href="preview.php?id=${sNum * 1000 + e}" class="preview-button">Watch</a>
                            </div>
                        </div>
                    """.trimIndent())
                }
                append("</div>\n")
            }
            append("</body></html>")
        }

        val detail = parser.parseSeriesEpisodes("series_money_heist", html, "Money Heist")
        assertEquals(5, detail.seasons.size)
        assertEquals(listOf(1, 2, 3, 4, 5), detail.seasons.map { it.number })
        assertEquals(13, detail.seasons[0].episodes.size)
        assertEquals(9, detail.seasons[1].episodes.size)
        assertEquals(8, detail.seasons[2].episodes.size)
        assertEquals(8, detail.seasons[3].episodes.size)
        assertEquals(10, detail.seasons[4].episodes.size)
        assertEquals(48, detail.seasons.sumOf { it.episodes.size })
    }

    @Test
    fun testPartVsEpisode_distinguishesSeasonPartAndMoviePart() {
        // Case 1: Part X: Episode Y -> Season X, Episode Y
        val html1 = """
            <html><body><h1 class="title">Money Heist</h1>
                <div class="episode-card">
                    <a href="preview.php?id=1" title="Money Heist - Part 5: Episode 6">
                        <h3>Part 5: Episode 6</h3>
                    </a>
                </div>
            </body></html>
        """.trimIndent()
        val detail1 = parser.parseSeriesEpisodes("series_money_heist", html1, "Money Heist")
        assertEquals(1, detail1.seasons.size)
        assertEquals(5, detail1.seasons[0].number)
        assertEquals(6, detail1.seasons[0].episodes[0].number)

        // Case 2: Movie/Series with only Part 2 (no Episode) -> Season 1, Episode 2
        val html2 = """
            <html><body><h1 class="title">The Beekeeper</h1>
                <div class="episode-box">
                    <a href="/twolekede?mid=999" title="The Beekeeper Part 2">Part 2</a>
                </div>
            </body></html>
        """.trimIndent()
        val detail2 = parser.parseSeriesEpisodes("movie_beekeeper", html2, "The Beekeeper")
        assertEquals(1, detail2.seasons.size)
        assertEquals(1, detail2.seasons[0].number)
        assertEquals(2, detail2.seasons[0].episodes[0].number)
    }
}



