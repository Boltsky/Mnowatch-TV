package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.MunowatchHtmlParser
import com.example.data.repository.MediaCache
import com.example.data.session.SecureSessionManager
import com.example.domain.model.ContinueWatchingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches Munowatch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Munowatch", appName)
    }

    @Test
    fun `secure session manager stores and masks session correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionManager = SecureSessionManager(context)

        sessionManager.clearSession()
        assertFalse(sessionManager.hasStoredSession())

        val testSession = "a1b2c3d4e5f67890123456789abcdef0"
        sessionManager.saveSessionId(testSession)

        assertTrue(sessionManager.hasStoredSession())
        assertEquals(testSession, sessionManager.getSessionId())

        val masked = sessionManager.getMaskedSessionPreview()
        assertTrue(masked.startsWith("a1b2"))
        assertTrue(masked.endsWith("def0"))
        assertTrue(masked.contains("••••"))

        sessionManager.clearSession()
        assertFalse(sessionManager.hasStoredSession())
    }

    @Test
    fun `html parser extracts media cards from grid html`() {
        val parser = MunowatchHtmlParser()
        val sampleHtml = """
            <div id="grid-container">
                <a href="/watch/12345">
                    <div id="12345" class="card">
                        <img class="tile-img" src="/posters/movie1.jpg" alt="Fast X" />
                        <h3>Fast X</h3>
                        <span class="vj">Vj Junior</span>
                        <span class="year">2023</span>
                    </div>
                </a>
            </div>
        """.trimIndent()

        val cards = parser.parseMediaCards(sampleHtml)
        assertEquals(1, cards.size)
        assertEquals("12345", cards[0].id)
        assertEquals("Fast X", cards[0].title)
        assertEquals("Vj Junior", cards[0].vj)
        assertEquals("2023", cards[0].year)
        assertTrue(cards[0].posterUrl.contains("munowatch.com/posters/movie1.jpg"))
    }

    @Test
    fun `html parser extracts multiple movies from category grid fragment with mid params`() {
        val parser = MunowatchHtmlParser()
        val gridSlotFragment = """
            <div class="col-6 col-md-3">
                <a href="/twolekede?mid=9801" title="Inception">
                    <img class="tile-img" src="/posters/inception.jpg" alt="Inception" />
                    <h4>Inception</h4>
                    <span class="vj">Vj Junior</span>
                    <span class="year">2010</span>
                </a>
            </div>
            <div class="col-6 col-md-3">
                <a href="/twolekede?mid=9802" title="Interstellar">
                    <img class="tile-img" src="/posters/interstellar.jpg" alt="Interstellar" />
                    <h4>Interstellar</h4>
                    <span class="vj">Vj Jingo</span>
                    <span class="year">2014</span>
                </a>
            </div>
            <div class="col-6 col-md-3">
                <a href="/twolekede?mid=9803" title="The Dark Knight">
                    <img class="tile-img" src="/posters/darkknight.jpg" alt="The Dark Knight" />
                    <h4>The Dark Knight</h4>
                    <span class="vj">Vj Emmy</span>
                    <span class="year">2008</span>
                </a>
            </div>
        """.trimIndent()

        val cards = parser.parseMediaCards(gridSlotFragment)
        assertEquals(3, cards.size)
        assertEquals("9801", cards[0].id)
        assertEquals("Inception", cards[0].title)
        assertEquals("Vj Junior", cards[0].vj)

        assertEquals("9802", cards[1].id)
        assertEquals("Interstellar", cards[1].title)
        assertEquals("Vj Jingo", cards[1].vj)

        assertEquals("9803", cards[2].id)
        assertEquals("The Dark Knight", cards[2].title)
        assertEquals("Vj Emmy", cards[2].vj)
    }

    @Test
    fun `html parser extracts numeric div id when href has encrypted v token for pagination cursor`() {
        val parser = MunowatchHtmlParser()
        val munowatchCardHtml = """
            <div id="grid-container">
                <a class="flex-shrink-0 z-10" href="/twolekede?v=mNXntyUGUmpOOZRMJzMXxkUwR1AzWjhGQlhvdU9XbnZoemJoclE9PQ%253D%253D&amp;title=The+Gentlemen+Season+2%3A+Episode+1">
                    <div class="relative group rounded-md" id="66784">
                        <img src="https://apposters.b-cdn.net/laba/yo/naki/nXIH7232.jpg" alt="The Gentlemen Season 2: Episode 1" class="w-full h-full block tile-img rounded-md" />
                        <span class="vj">Vj Junior</span>
                    </div>
                </a>
                <a class="flex-shrink-0 z-10" href="/twolekede?v=xyz123abc456&amp;title=Fallout">
                    <div class="relative group rounded-md" id="66783">
                        <img src="https://apposters.b-cdn.net/laba/yo/naki/fallout.jpg" alt="Fallout" class="w-full h-full block tile-img rounded-md" />
                        <span class="vj">Vj Jingo</span>
                    </div>
                </a>
            </div>
        """.trimIndent()

        val cards = parser.parseMediaCards(munowatchCardHtml)
        assertEquals(2, cards.size)
        // Verify numeric cursor ID used by grid.min.js ($("#grid-container > a:last-child > div").attr("id"))
        assertEquals("66784", cards[0].id)
        assertEquals("The Gentlemen Season 2: Episode 1", cards[0].title)
        assertEquals("66783", cards[1].id)
        assertEquals("Fallout", cards[1].title)
    }

    @Test
    fun `continue watching progress fraction calculates correctly`() {
        val item = ContinueWatchingItem(
            contentId = "101",
            title = "Sample Movie",
            posterUrl = "",
            positionMs = 30000L,
            durationMs = 60000L,
            lastWatchedTimestamp = System.currentTimeMillis()
        )
        assertEquals(0.5f, item.progressFraction, 0.01f)
    }

    @Test
    fun `search viewmodel supports qwerty keyboard input space backspace and clear`() {
        val mockSearchRepo = object : com.example.domain.repository.SearchRepository {
            override suspend fun searchContent(query: String) = Result.success(emptyList<com.example.domain.model.MediaItem>())
        }
        val vm = com.example.presentation.search.SearchViewModel(mockSearchRepo)

        // Type 'v', 'j', ' ', 'j', 'u', 'n', 'i', 'o', 'r'
        vm.appendChar('v')
        vm.appendChar('j')
        vm.appendChar(' ')
        vm.appendChar('j')
        vm.appendChar('u')
        vm.appendChar('n')
        vm.appendChar('i')
        vm.appendChar('o')
        vm.appendChar('r')
        assertEquals("vj junior", vm.uiState.value.query)

        // Backspace
        vm.backspace()
        assertEquals("vj junio", vm.uiState.value.query)

        // Clear
        vm.clear()
        assertEquals("", vm.uiState.value.query)

        // Quick Tag Selection
        vm.setQuery("Action")
        assertEquals("Action", vm.uiState.value.query)
    }

    @Test
    fun `html parser extracts proper movie description from munowatch website without synthetic text`() {
        val parser = MunowatchHtmlParser()
        val htmlWithBodyDesc = """
            <html>
                <head>
                    <meta property="og:description" content="Watch The Odyssey on Munowatch." />
                </head>
                <body>
                    <div id="description">
                        <p class="description">A breathtaking adventure through uncharted realms where danger lurks behind every shadow.</p>
                    </div>
                </body>
            </html>
        """.trimIndent()
        val desc = parser.extractMovieDescriptionFromWatchPage(htmlWithBodyDesc)
        assertEquals("A breathtaking adventure through uncharted realms where danger lurks behind every shadow.", desc)

        val htmlWithOg = """
            <html>
                <head>
                    <meta property="og:description" content="Watch The Odyssey online on Munowatch - An ancient hero journeys through mythical dangers to reclaim his kingdom." />
                </head>
                <body></body>
            </html>
        """.trimIndent()
        val ogDesc = parser.extractMovieDescriptionFromWatchPage(htmlWithOg)
        assertEquals("An ancient hero journeys through mythical dangers to reclaim his kingdom.", ogDesc)

        // Strict mandate: when no description is present on Munowatch, do not generate custom description
        val emptyDesc = parser.extractMovieDescriptionFromWatchPage("<html><body><div id='player'></div></body></html>")
        assertEquals("", emptyDesc)

        // Strict mandate: generic site slogan or login metadata must NOT be treated as a movie description
        val htmlWithGenericMotto = """
            <html>
                <head>
                    <meta name="description" content="Katandika Butandisi - Twolekede Ekyatuleese Munowatch | Ekiseera Kyoona Woona Wooli." />
                    <meta property="og:description" content="Katandika Butandisi - Twolekede Ekyatuleese ...| Ekiseera Kyoona Woona Wooli." />
                </head>
                <body><div id="player"></div></body>
            </html>
        """.trimIndent()
        val rejectedDesc = parser.extractMovieDescriptionFromWatchPage(htmlWithGenericMotto)
        assertEquals("", rejectedDesc)

        // Test Munowatch drawer slot extraction
        val htmlWithDrawerSlot = """
            <div id="grid-container">
                <div id="4521-drawer-slot" class="drawer-slot">
                    <p>A rogue operative must outwit an elite team of assassins across European capitals.</p>
                </div>
            </div>
        """.trimIndent()
        val drawerDesc = parser.extractMovieDescriptionFromWatchPage(htmlWithDrawerSlot)
        assertEquals("A rogue operative must outwit an elite team of assassins across European capitals.", drawerDesc)
    }

    @Test
    fun `video source extraction from soul-info data-gumite works seamlessly for movies and series episodes`() {
        val parser = MunowatchHtmlParser()
        val streamUrl = "https://nkuba.b-cdn.net/port/str/series_ep_4139.mp4"
        val base64Stream = android.util.Base64.encodeToString(streamUrl.toByteArray(), android.util.Base64.NO_WRAP)
        val watchPageHtml = """
            <html>
                <body>
                    <div id="title-block">
                        <h1>Reacher S2 Episode 1</h1>
                    </div>
                    <div id="soul-info" data-gumite="$base64Stream" data-vidid="4139" data-series-code="53"></div>
                </body>
            </html>
        """.trimIndent()

        val source = parser.extractVideoSourceFromWatchPage(watchPageHtml)
        assertNotNull(source)
        assertEquals(streamUrl, source?.url)
        assertEquals("1080p Full HD", source?.quality)
    }

    @Test
    fun `title cleaning and extraction properly decodes URLs, strips branding, and unescapes entities`() {
        val parser = MunowatchHtmlParser()
        assertEquals("The Gentlemen Season 2: Episode 1", parser.cleanTitle("The+Gentlemen+Season+2%3A+Episode+1"))
        assertEquals("Fast & Furious X", parser.cleanTitle("Fast &amp; Furious X - Munowatch"))
        assertEquals("Dune: Part Two", parser.cleanTitle("Watch Dune: Part Two by Vj Junior | Munowatch"))

        val watchHtml = """
            <html>
                <head>
                    <title>Gladiator II by Vj Jingo - Munowatch</title>
                    <meta property="og:title" content="Gladiator II" />
                </head>
                <body>
                    <div id="title-block">
                        <h1>Gladiator II</h1>
                    </div>
                </body>
            </html>
        """.trimIndent()
        val title = parser.extractMovieTitleFromWatchPage(watchHtml, "/twolekede?mid=5544&title=Gladiator+II")
        assertEquals("Gladiator II", title)
    }

    @Test
    fun `series episodes and description parsing works accurately`() {
        val parser = MunowatchHtmlParser()
        val seriesHtml = """
            <html>
                <head>
                    <title>Reacher Season 2 - Munowatch</title>
                </head>
                <body>
                    <div id="description">
                        <p class="description">When members of his old military unit start turning up dead, Jack Reacher has just one thing on his mind: revenge.</p>
                    </div>
                    <div id="grid-container">
                        <a href="/twolekede?mid=4411&title=Reacher+S2+Episode+1" id="4411">
                            <span class="title">Episode 1: ATM</span>
                        </a>
                        <a href="/twolekede?mid=4412&title=Reacher+S2+Episode+2" id="4412">
                            <span class="title">Episode 2: What Happens in Atlantic City</span>
                        </a>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val seriesDetail = parser.parseSeriesEpisodes("53", seriesHtml)
        assertEquals("When members of his old military unit start turning up dead, Jack Reacher has just one thing on his mind: revenge.", seriesDetail.description)
        val episodes = seriesDetail.seasons[0].episodes
        assertEquals(2, episodes.size)
        assertEquals("/twolekede?mid=4411&title=Reacher+S2+Episode+1", episodes[0].streamUrl)
        assertEquals("Episode 1: ATM", episodes[0].title)
    }

    @Test
    fun `series multi-season episodes are grouped properly`() {
        val parser = MunowatchHtmlParser()
        val seriesHtml = """
            <html>
                <body>
                    <div id="grid-container">
                        <a href="/twolekede?mid=101" title="Season 1 Episode 1">
                            <span class="title">Episode 1</span>
                        </a>
                        <a href="/twolekede?mid=102" title="Season 1 Episode 2">
                            <span class="title">Episode 2</span>
                        </a>
                        <a href="/twolekede?mid=201" title="Season 2 Episode 1">
                            <span class="title">Episode 1</span>
                        </a>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val seriesDetail = parser.parseSeriesEpisodes("series_99", seriesHtml)
        assertEquals(2, seriesDetail.seasons.size)
        assertEquals("Season 1", seriesDetail.seasons[0].title)
        assertEquals(2, seriesDetail.seasons[0].episodes.size)
        assertEquals("Season 2", seriesDetail.seasons[1].title)
        assertEquals(1, seriesDetail.seasons[1].episodes.size)
    }

    @Test
    fun `series list-container and more-episodes containers parse all episodes and seasons`() {
        val parser = MunowatchHtmlParser()
        val html = """
            <html>
                <body>
                    <div id="list-container">
                        <a href="/twolekede?mid=301" title="S1 E1">
                            <span class="title">S1 E1 Pilot</span>
                        </a>
                        <a href="/twolekede?mid=302" title="S1 E2">
                            <span class="title">S1 E2 The Discovery</span>
                        </a>
                    </div>
                    <div id="more-episodes-container-21-40">
                        <a href="/twolekede?mid=303" title="S2 E1">
                            <span class="title">S2 E1 The Return</span>
                        </a>
                        <a href="/twolekede?mid=304" title="S2 E2">
                            <span class="title">S2 E2 The Battle</span>
                        </a>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("series_42", html)
        assertEquals(2, detail.seasons.size)
        assertEquals("Season 1", detail.seasons[0].title)
        assertEquals(2, detail.seasons[0].episodes.size)
        assertEquals("Pilot", detail.seasons[0].episodes[0].title)
        assertEquals("The Discovery", detail.seasons[0].episodes[1].title)

        assertEquals("Season 2", detail.seasons[1].title)
        assertEquals(2, detail.seasons[1].episodes.size)
        assertEquals("The Return", detail.seasons[1].episodes[0].title)
        assertEquals("The Battle", detail.seasons[1].episodes[1].title)
    }

    @Test
    fun `series does not mix movies or episodes from a different series`() {
        val parser = MunowatchHtmlParser()
        val html = """
            <html>
                <head>
                    <title>Alchemy of Souls Season 1 - Munowatch</title>
                </head>
                <body>
                    <h1>Alchemy of Souls Season 1</h1>
                    <div id="list-container">
                        <a href="/twolekede?mid=5001" title="Alchemy of Souls Episode 1">
                            <span class="title">Episode 1</span>
                        </a>
                        <a href="/twolekede?mid=5002" title="Alchemy of Souls Episode 2">
                            <span class="title">Episode 2</span>
                        </a>
                    </div>
                    <!-- Unrelated movies and different series in recommendations and catalog grid -->
                    <div id="grid-container">
                        <a href="/twolekede?mid=8801" title="Fast X (2023)">
                            <span class="title">Fast X</span>
                        </a>
                        <a href="/twolekede?mid=8802" title="Avatar: The Way of Water">
                            <span class="title">Avatar: The Way of Water</span>
                        </a>
                        <a href="/twolekede?mid=8803" title="Loki Season 2 Episode 1">
                            <span class="title">Episode 1</span>
                        </a>
                        <a href="/twolekede?mid=8804" title="Wednesday Season 1 Episode 3">
                            <span class="title">Episode 3</span>
                        </a>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("series_alchemy", html)
        val episodes = detail.seasons.flatMap { it.episodes }
        assertEquals(2, episodes.size)
        assertEquals("Episode 1", episodes[0].title)
        assertEquals("/twolekede?mid=5001", episodes[0].streamUrl)
        assertEquals("Episode 2", episodes[1].title)
        assertEquals("/twolekede?mid=5002", episodes[1].streamUrl)
    }

    @Test
    fun `series description with Luganda translation and plot is preserved and not filtered`() {
        val parser = MunowatchHtmlParser()
        val html = """
            <html>
                <body>
                    <div id="description">
                        Watch Alchemy of Souls translated movies and series in Luganda by VJ Junior. A powerful sorceress in a blind woman's body encounters a man from a prestigious family, who wants her help to change his destiny.
                    </div>
                </body>
            </html>
        """.trimIndent()

        val desc = parser.extractMovieDescriptionFromWatchPage(html)
        assertTrue(desc.isNotBlank())
        assertTrue(desc.contains("A powerful sorceress in a blind woman's body encounters a man from a prestigious family"))
    }

    @Test
    fun `series episodes from search container are discovered and grouped into seasons`() {
        val parser = MunowatchHtmlParser()
        val combinedHtml = """
            <html>
                <body>
                    <div id="series-search-episodes">
                        <div class="col-6 col-md-3">
                            <a href="/twolekede?mid=5001" title="Alchemy of Souls Season 1 Episode 1">
                                <span class="title">Episode 1</span>
                            </a>
                        </div>
                        <div class="col-6 col-md-3">
                            <a href="/twolekede?mid=5002" title="Alchemy of Souls Season 1 Episode 2">
                                <span class="title">Episode 2</span>
                            </a>
                        </div>
                        <div class="col-6 col-md-3">
                            <a href="/twolekede?mid=5021" title="Alchemy of Souls Season 2 Episode 1">
                                <span class="title">Episode 1</span>
                            </a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("5000", combinedHtml)
        assertEquals(2, detail.seasons.size)
        assertEquals("Season 1", detail.seasons[0].title)
        assertEquals(2, detail.seasons[0].episodes.size)
        assertEquals("Season 2", detail.seasons[1].title)
        assertEquals(1, detail.seasons[1].episodes.size)
    }

    @Test
    fun `unrelated movies and recommendations are excluded from series episodes and correct posters are extracted`() {
        val parser = MunowatchHtmlParser()
        val html = """
            <html>
                <head>
                    <meta property="og:image" content="https://munowatch.com/uploads/alchemy_poster.jpg" />
                    <title>Alchemy of Souls Season 1 - Munowatch</title>
                </head>
                <body>
                    <div id="list-container">
                        <div class="episode-box">
                            <img src="https://munowatch.com/uploads/ep1_thumb.jpg" />
                            <a href="/twolekede?mid=101" title="Alchemy of Souls S1 E1">
                                <span class="title">Episode 1: The Return</span>
                            </a>
                        </div>
                        <div class="episode-box">
                            <img src="https://munowatch.com/uploads/ep2_thumb.jpg" />
                            <a href="/twolekede?mid=102" title="Alchemy of Souls S1 E2">
                                <span class="title">Episode 2: The Master</span>
                            </a>
                        </div>
                    </div>
                    <!-- Recommended / Related movies outside list-container -->
                    <div class="related-movies">
                        <h3>Recommended Movies</h3>
                        <div class="col-md-3">
                            <a href="/twolekede?mid=991"><img src="https://munowatch.com/uploads/barbie.jpg" /><span class="title">Barbie (2023)</span></a>
                        </div>
                        <div class="col-md-3">
                            <a href="/twolekede?mid=992"><img src="https://munowatch.com/uploads/fastx.jpg" /><span class="title">Fast X</span></a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("500", html)
        assertEquals(1, detail.seasons.size)
        val episodes = detail.seasons[0].episodes
        assertEquals(2, episodes.size)
        assertEquals("Episode 1: The Return", episodes[0].title)
        assertEquals("https://munowatch.com/uploads/ep1_thumb.jpg", episodes[0].thumbnail)
        assertEquals("Episode 2: The Master", episodes[1].title)
        assertEquals("https://munowatch.com/uploads/ep2_thumb.jpg", episodes[1].thumbnail)
        assertFalse(episodes.any { it.title.contains("Barbie") || it.title.contains("Fast X") })
    }

    @Test
    fun `grid-container catalog movies and different series are never mixed into series episodes`() {
        val parser = MunowatchHtmlParser()
        val html = """
            <html>
                <head>
                    <meta property="og:image" content="https://munowatch.com/uploads/sweethome.jpg" />
                    <title>Sweet Home Season 1 Episode 1 - Munowatch</title>
                </head>
                <body>
                    <h1 class="movie-title">Sweet Home</h1>
                    <div id="list-container">
                        <div class="episode-box" data-poster="https://munowatch.com/uploads/sh_ep1.jpg">
                            <a href="/twolekede?mid=201" title="Sweet Home Season 1 Episode 1">
                                <span class="title">Episode 1</span>
                            </a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=202" title="Sweet Home Season 1 Episode 2">
                                <span class="title">Episode 2</span>
                            </a>
                        </div>
                    </div>
                    <!-- Catalog grid with unrelated movies and other series -->
                    <div id="grid-container">
                        <div class="card">
                            <a href="/twolekede?mid=999" title="Barbie (2023)"><img src="https://munowatch.com/uploads/barbie.jpg" /><span>Barbie</span></a>
                        </div>
                        <div class="card">
                            <a href="/twolekede?mid=998" title="Fast X: Part 2"><img src="https://munowatch.com/uploads/fastx.jpg" /><span>Fast X: Part 2</span></a>
                        </div>
                        <div class="card">
                            <a href="/twolekede?mid=997" title="Loki Season 2 Episode 1"><img src="https://munowatch.com/uploads/loki.jpg" /><span>Loki Season 2 Episode 1</span></a>
                        </div>
                        <div class="card">
                            <a href="/twolekede?mid=996" title="Money Heist: Korea Season 1 Episode 1"><img src="https://munowatch.com/uploads/korea.jpg" /><span>Money Heist: Korea</span></a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("200", html, knownSeriesTitle = "Sweet Home")
        assertEquals(1, detail.seasons.size)
        val episodes = detail.seasons[0].episodes
        assertEquals(2, episodes.size)
        assertEquals("Episode 1", episodes[0].title)
        assertEquals("https://munowatch.com/uploads/sh_ep1.jpg", episodes[0].thumbnail)
        assertEquals("Episode 2", episodes[1].title)
        assertEquals("https://munowatch.com/uploads/sweethome.jpg", episodes[1].thumbnail)

        // Ensure zero contamination from grid-container
        assertFalse(episodes.any { it.title.contains("Barbie") })
        assertFalse(episodes.any { it.title.contains("Fast X") })
        assertFalse(episodes.any { it.title.contains("Loki") })
        assertFalse(episodes.any { it.title.contains("Money Heist") })
    }

    @Test
    fun `all episodes under a series are preserved and not truncated to one episode`() {
        val parser = MunowatchHtmlParser()
        val html = """
            <html>
                <head>
                    <meta property="og:image" content="https://munowatch.com/uploads/alchemy.jpg" />
                    <title>Alchemy of Souls (2022) - Munowatch</title>
                </head>
                <body>
                    <h1 class="movie-title">Alchemy of Souls</h1>
                    <div id="list-container">
                        <div class="episode-box">
                            <a href="/twolekede?mid=501" title="Alchemy of Souls Season 1 Episode 1">
                                <span class="title">Episode 1</span>
                            </a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=502" title="Alchemy of Souls Season 1 Episode 2">
                                <span class="title">Episode 2</span>
                            </a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=503" title="Alchemy of Souls Season 1 Episode 3">
                                <span class="title">Episode 3</span>
                            </a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=504" title="Alchemy of Souls Season 1 Episode 4">
                                <span class="title">Episode 4</span>
                            </a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("500", html, knownSeriesTitle = "Alchemy of Souls (2022)")
        assertEquals(1, detail.seasons.size)
        val episodes = detail.seasons[0].episodes
        assertEquals(4, episodes.size)
        assertEquals(1, episodes[0].number)
        assertEquals(2, episodes[1].number)
        assertEquals(3, episodes[2].number)
        assertEquals(4, episodes[3].number)
    }

    @Test
    fun `series episodes with VJ names and Luganda markers preserve all episodes without truncation`() {
        val parser = MunowatchHtmlParser()
        val html = """
            <html>
                <body>
                    <h1 class="movie-title">Sweet Home [VJ Emmy] (Luganda)</h1>
                    <div id="list-container">
                        <div class="episode-box">
                            <a href="/twolekede?mid=601" title="Sweet Home VJ Emmy S01 EPS 01">
                                <span class="title">Episode 1</span>
                            </a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=602" title="Sweet Home VJ Emmy S01 EPS 02">
                                <span class="title">Episode 2</span>
                            </a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=603" title="Sweet Home VJ Emmy S01 EPS 03">
                                <span class="title">Episode 3</span>
                            </a>
                        </div>
                    </div>
                    <div id="more-episodes-container-21-40">
                        <div class="episode-box">
                            <a href="/twolekede?mid=604" title="Sweet Home VJ Emmy S01 EPS 04">
                                <span class="title">Episode 4</span>
                            </a>
                        </div>
                        <div class="episode-box">
                            <a href="/twolekede?mid=605" title="Sweet Home VJ Emmy S02 EPS 01">
                                <span class="title">Episode 1</span>
                            </a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("600", html, knownSeriesTitle = "Sweet Home [VJ Emmy] (Luganda)")
        assertEquals(2, detail.seasons.size)
        assertEquals(4, detail.seasons[0].episodes.size)
        assertEquals(1, detail.seasons[1].episodes.size)
        assertEquals("/twolekede?mid=601", detail.seasons[0].episodes[0].streamUrl)
        assertEquals("/twolekede?mid=602", detail.seasons[0].episodes[1].streamUrl)
        assertEquals("/twolekede?mid=603", detail.seasons[0].episodes[2].streamUrl)
        assertEquals("/twolekede?mid=604", detail.seasons[0].episodes[3].streamUrl)
        assertEquals("/twolekede?mid=605", detail.seasons[1].episodes[0].streamUrl)
    }

    @Test
    fun `html parser extracts all episodes from Twolekede episode-card DOM structure`() {
        val parser = MunowatchHtmlParser()
        val twolekedeHtml = """
            <html>
                <head><title>Twolekede - Watch Movies and Series</title></head>
                <body>
                    <div class="series-details-container">
                        <h1 class="series-title">THE GENTLEMEN</h1>
                        <div class="meta-item"><span class="meta-label">VJ:</span> <span class="meta-value">VJ Jingo</span></div>
                        <div class="meta-item"><span class="meta-label">Category:</span> <span class="meta-value">Action, Crime</span></div>
                    </div>
                    <div class="season-episodes-wrapper" data-season="1">
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 1</h3>
                                <p class="episode-title">Refined Aggression</p>
                            </div>
                            <div class="episode-actions">
                                <a href="preview.php?id=8498" class="preview-button">Preview</a>
                                <a href="download.php?id=8498" class="download-button">Download</a>
                            </div>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 2</h3>
                                <p class="episode-title">Tackle Tommy Woo Woo</p>
                            </div>
                            <div class="episode-actions">
                                <a href="preview.php?id=8499" class="preview-button">Preview</a>
                                <a href="download.php?id=8499" class="download-button">Download</a>
                            </div>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 3</h3>
                                <p class="episode-title">Where's My Weed At?</p>
                            </div>
                            <div class="episode-actions">
                                <a href="preview.php?id=8500" class="preview-button">Preview</a>
                                <a href="download.php?id=8500" class="download-button">Download</a>
                            </div>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 4</h3>
                                <p class="episode-title">An Unsympathetic Gentleman</p>
                            </div>
                            <div class="episode-actions">
                                <a href="preview.php?id=8501" class="preview-button">Preview</a>
                                <a href="download.php?id=8501" class="download-button">Download</a>
                            </div>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("series_the_gentlemen", twolekedeHtml, knownSeriesTitle = "THE GENTLEMEN")
        assertEquals(1, detail.seasons.size)
        val episodes = detail.seasons[0].episodes
        assertEquals(4, episodes.size)
        assertEquals(1, episodes[0].number)
        assertEquals("Episode 1: Refined Aggression", episodes[0].title)
        assertEquals("https://twolekede.com/preview.php?id=8498", episodes[0].streamUrl)
        assertEquals(2, episodes[1].number)
        assertEquals("Episode 2: Tackle Tommy Woo Woo", episodes[1].title)
        assertEquals("https://twolekede.com/preview.php?id=8499", episodes[1].streamUrl)
        assertEquals(3, episodes[2].number)
        assertEquals("Episode 3: Where's My Weed At?", episodes[2].title)
        assertEquals("https://twolekede.com/preview.php?id=8500", episodes[2].streamUrl)
        assertEquals(4, episodes[3].number)
        assertEquals("Episode 4: An Unsympathetic Gentleman", episodes[3].title)
        assertEquals("https://twolekede.com/preview.php?id=8501", episodes[3].streamUrl)
    }

    @Test
    fun `html parser extracts multiple seasons with respective episodes from Twolekede HTML`() {
        val parser = MunowatchHtmlParser()
        val multiSeasonHtml = """
            <html>
                <body>
                    <h1>Shogun</h1>
                    <div class="season-episodes-wrapper" data-season="1">
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 1</h3>
                                <p class="episode-title">Chapter One: Anjin</p>
                            </div>
                            <a href="preview.php?id=9101" class="preview-button">Preview</a>
                        </div>
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 2</h3>
                                <p class="episode-title">Chapter Two: Servants of Two Masters</p>
                            </div>
                            <a href="preview.php?id=9102" class="preview-button">Preview</a>
                        </div>
                    </div>
                    <div class="season-episodes-wrapper" data-season="2">
                        <div class="episode-card">
                            <div class="episode-info">
                                <h3>Episode 1</h3>
                                <p class="episode-title">Chapter Eleven</p>
                            </div>
                            <a href="preview.php?id=9201" class="preview-button">Preview</a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val detail = parser.parseSeriesEpisodes("series_shogun", multiSeasonHtml, knownSeriesTitle = "Shogun")
        assertEquals(2, detail.seasons.size)
        assertEquals(2, detail.seasons[0].episodes.size)
        assertEquals(1, detail.seasons[1].episodes.size)
        assertEquals(1, detail.seasons[0].episodes[0].number)
        assertEquals(2, detail.seasons[0].episodes[1].number)
        assertEquals(1, detail.seasons[1].episodes[0].number)
        assertEquals("https://twolekede.com/preview.php?id=9101", detail.seasons[0].episodes[0].streamUrl)
        assertEquals("https://twolekede.com/preview.php?id=9201", detail.seasons[1].episodes[0].streamUrl)
    }

    @Test
    fun `extractVideoSourceFromWatchPage decodes encoded ampersands and assigns twolekede headers`() {
        val parser = MunowatchHtmlParser()
        val twolekedePreviewHtml = """
            <html>
                <body>
                    <div id="video-container">
                        <video id="preview-player">
                            <source src="https://cdn.movviez.com/secure.php?file=movies%2FNEXT%20S01%20E01.mp4&amp;expires=1790277411&amp;signature=c1c6e04b8b01&amp;mode=preview" type="video/mp4" />
                        </video>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val source = parser.extractVideoSourceFromWatchPage(twolekedePreviewHtml, "Server 1 (Fast CDN)")
        assertNotNull(source)
        assertFalse(source!!.url.contains("&amp;"))
        assertTrue(source.url.contains("&expires=1790277411"))
        assertTrue(source.url.contains("&signature=c1c6e04b8b01"))
        assertEquals("https://twolekede.com/", source.headers["Referer"])
        assertEquals("https://twolekede.com", source.headers["Origin"])
    }

    @Test
    fun `media cache registers Twolekede episode with id param and retrieves by numeric id`() {
        val episode = com.example.domain.model.Episode(
            id = "ep_16843",
            title = "Episode 1",
            number = 1,
            streamUrl = "https://twolekede.com/preview.php?id=16843"
        )
        MediaCache.registerEpisode(episode, seriesTitle = "NEXT")

        val retrievedByNumber = MediaCache.get("16843")
        assertNotNull(retrievedByNumber)
        assertEquals("NEXT - Episode 1", retrievedByNumber!!.title)

        val retrievedStream = MediaCache.getStreamPath("16843")
        assertNotNull(retrievedStream)
        assertTrue(retrievedStream!!.contains("preview.php?id=16843"))
    }

    @Test
    fun `media cache handles composite series episode ID and indexes by numeric ID`() {
        val compositeId = "series_Lioness__Special_Ops_VJ_ICEP_s1_ep_1_119594"
        val episode = com.example.domain.model.Episode(
            id = compositeId,
            title = "Episode 1",
            number = 1,
            streamUrl = "https://twolekede.com/preview.php?id=119594"
        )
        MediaCache.registerEpisode(episode, seriesTitle = "Lioness: Special Ops")

        val fromComposite = MediaCache.getStreamPath(compositeId)
        assertNotNull(fromComposite)
        assertTrue(fromComposite!!.contains("119594"))

        val fromNumeric = MediaCache.getStreamPath("119594")
        assertNotNull(fromNumeric)
        assertTrue(fromNumeric!!.contains("119594"))

        val itemByNumeric = MediaCache.get("119594")
        assertNotNull(itemByNumeric)
        assertTrue(itemByNumeric!!.title.contains("Lioness"))
    }

    @Test
    fun `extractTwolekedeId extracts numeric ID across all supported URL and ID formats`() {
        val api = com.example.data.api.MunowatchApi(
            com.example.data.network.NetworkClient(
                com.example.data.session.SecureSessionManager(
                    androidx.test.core.app.ApplicationProvider.getApplicationContext()
                )
            ),
            com.example.data.session.SecureSessionManager(
                androidx.test.core.app.ApplicationProvider.getApplicationContext()
            )
        )

        assertEquals("121338", api.extractTwolekedeId("121338"))
        assertEquals("121338", api.extractTwolekedeId("moviedetails.php?id=121338"))
        assertEquals("121338", api.extractTwolekedeId("preview.php?id=121338"))
        assertEquals("121338", api.extractTwolekedeId("download.php?id=121338"))
        assertEquals("121338", api.extractTwolekedeId("/twolekede?mid=121338"))
        assertEquals("119594", api.extractTwolekedeId("series_Lioness__Special_Ops_VJ_ICEP_s1_ep_1_119594"))
        assertEquals("9201", api.extractTwolekedeId("series_shogun_s2_ep_1_9201"))
        assertEquals("16871", api.extractTwolekedeId("ep_16871"))
        assertEquals("120841", api.extractTwolekedeId("https://twolekede.com/preview.php?id=120841"))
    }

    @Test
    fun `search results for series include seasons and opening season displays episodes`() {
        val seriesItem = com.example.domain.model.MediaItem(
            id = "series_The_Boys_VJ_Soul",
            title = "The Boys",
            streamUrl = "https://twolekede.com/seriesdetails.php?title=The+Boys&category=VJ+Soul",
            posterUrl = "https://twolekede.com/mv-admin/uploads/posters/1.jpg",
            isSeries = true,
            vj = "VJ Soul"
        )
        val season1 = com.example.domain.model.Season(
            number = 1,
            title = "Season 1",
            episodes = listOf(
                com.example.domain.model.Episode(
                    id = "ep_101",
                    number = 1,
                    title = "Episode 1",
                    streamUrl = "https://twolekede.com/preview.php?id=101",
                    thumbnail = "https://twolekede.com/poster.jpg"
                ),
                com.example.domain.model.Episode(
                    id = "ep_102",
                    number = 2,
                    title = "Episode 2",
                    streamUrl = "https://twolekede.com/preview.php?id=102",
                    thumbnail = "https://twolekede.com/poster.jpg"
                )
            )
        )
        val season2 = com.example.domain.model.Season(
            number = 2,
            title = "Season 2",
            episodes = listOf(
                com.example.domain.model.Episode(
                    id = "ep_201",
                    number = 1,
                    title = "Episode 1",
                    streamUrl = "https://twolekede.com/preview.php?id=201",
                    thumbnail = "https://twolekede.com/poster.jpg"
                )
            )
        )
        val detail = com.example.domain.model.SeriesDetail(
            id = seriesItem.id,
            title = seriesItem.title,
            description = "The Boys description",
            posterUrl = seriesItem.posterUrl,
            backdropUrl = seriesItem.posterUrl,
            vj = seriesItem.vj,
            year = "2024",
            genres = listOf("Action", "Series"),
            seasons = listOf(season1, season2)
        )

        val mockSearchRepo = object : com.example.domain.repository.SearchRepository {
            override suspend fun searchContent(query: String): Result<List<com.example.domain.model.MediaItem>> {
                return Result.success(listOf(seriesItem))
            }
        }
        val mockSeriesRepo = object : com.example.domain.repository.SeriesRepository {
            override suspend fun getPopularSeries(lastFetchId: String?, page: Int?): Result<List<com.example.domain.model.MediaItem>> = Result.success(emptyList())
            override suspend fun getSeriesDetails(id: String): Result<com.example.domain.model.SeriesDetail> {
                return Result.success(detail)
            }
        }

        val viewModel = com.example.presentation.search.SearchViewModel(
            searchRepository = mockSearchRepo,
            movieRepository = null,
            seriesRepository = mockSeriesRepo
        )

        viewModel.setQuery("Boys")
        val state = viewModel.uiState.value
        assertEquals("Boys", state.query)

        // Pre-loaded series details provides all seasons of the series
        viewModel.loadSeriesDetails(seriesItem)
        val loadedState = viewModel.uiState.value
        val seriesDetail = loadedState.seriesDetails[seriesItem.id]
        assertNotNull(seriesDetail)
        assertEquals(2, seriesDetail!!.seasons.size)
        assertEquals("Season 1", seriesDetail.seasons[0].title)
        assertEquals("Season 2", seriesDetail.seasons[1].title)

        // Opening season 1 displays the episodes of that season
        viewModel.openSeason(seriesItem, seriesDetail.seasons[0])
        val openedState = viewModel.uiState.value
        assertNotNull(openedState.openedSeasonData)
        assertEquals("Season 1", openedState.openedSeasonData!!.second.title)
        assertEquals(2, openedState.openedSeasonData!!.second.episodes.size)
        assertEquals("Episode 1", openedState.openedSeasonData!!.second.episodes[0].title)

        // Opening season 2 switches to season 2 episodes
        viewModel.openSeason(seriesItem, seriesDetail.seasons[1])
        val season2State = viewModel.uiState.value
        assertEquals("Season 2", season2State.openedSeasonData!!.second.title)
        assertEquals(1, season2State.openedSeasonData!!.second.episodes.size)

        // Closing season returns to results view
        viewModel.closeSeason()
        org.junit.Assert.assertNull(viewModel.uiState.value.openedSeasonData)
    }

    @Test
    fun `test getMoviesByVjAndGenre loads more than 200 action movies for VJ Junior`() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val sessionManager = com.example.data.session.SecureSessionManager(context)
        val networkClient = com.example.data.network.NetworkClient(sessionManager)
        val api = com.example.data.api.MunowatchApi(networkClient, sessionManager)
        val parser = com.example.data.api.MunowatchHtmlParser()
        val repo = com.example.data.repository.MovieRepositoryImpl(api, parser)

        val page1 = repo.getMoviesByVjAndGenre(vjId = 1, genreId = 1, page = 1).getOrNull() ?: emptyList()
        val page2 = repo.getMoviesByVjAndGenre(vjId = 1, genreId = 1, page = 2).getOrNull() ?: emptyList()
        val merged = (page1 + page2).distinctBy { it.id.ifBlank { it.streamUrl }.ifBlank { it.title } }
        println("ROBOLECTRIC TEST: Page 1 = ${page1.size}, Page 2 = ${page2.size}, Total = ${merged.size}")
        assertTrue("Expected > 200 on page 1, got ${page1.size}", page1.size >= 200)
        assertTrue("Expected more movies on page 2, got ${page2.size}", page2.size > 0)
    }
}
