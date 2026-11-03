package com.example

import com.example.data.api.MunowatchApi
import com.example.data.api.MunowatchHtmlParser
import com.example.data.repository.MediaCache
import com.example.data.session.SecureSessionManager
import com.example.domain.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BugFixesTest {

    private val parser = MunowatchHtmlParser()

    @Test
    fun testDescriptionPlaceholderDetection() {
        val placeholder1 = "Watch MobLand translated in Luganda by top Veejays. Stream full 1080p HD exclusively on Munowatch TV."
        val placeholder2 = "Munowatch is your #1 platform for translated movies and series in Luganda."
        val placeholder3 = ""
        val placeholderTerminalList = "Watch Terminal List translated in Luganda by top Veejays (Vj Junior). Stream all full HD episodes exclusively on Munowatch TV."
        val realPlot = "A sheriff tries to keep the peace when a desperate family man robs a pill mill with a reckless enforcer."
        val navySealPlot = "A former Navy SEAL officer investigates why his entire platoon was ambushed during a high-stakes covert mission."

        assertTrue(parser.isPlaceholderOrGenericDescription(placeholder1))
        assertTrue(parser.isPlaceholderOrGenericDescription(placeholder2))
        assertTrue(parser.isPlaceholderOrGenericDescription(placeholder3))
        assertTrue(parser.isPlaceholderOrGenericDescription(placeholderTerminalList))
        assertFalse(parser.isPlaceholderOrGenericDescription(realPlot))
        assertFalse(parser.isPlaceholderOrGenericDescription(navySealPlot))
    }

    @Test
    fun testSeriesCardsParsingInSearch() {
        val searchHtml = """
            <div id="twolekede-series-search-results">
                <div class="content-item">
                    <a href="seriesdetails.php?title=MobLand&category=VJ+Junior">
                        <img src="uploads/posters/mobland.jpg" alt="Mobland">
                    </a>
                    <div class="item-info">
                        <h3>Mobland</h3>
                        <span class="category">VJ Junior</span>
                    </div>
                </div>
                <div class="content-item">
                    <a href="seriesdetails.php?title=Lioness&category=VJ+Junior">
                        <img src="uploads/posters/lioness.jpg" alt="Lioness">
                    </a>
                    <div class="item-info">
                        <h3>Lioness</h3>
                        <span class="category">VJ Junior</span>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val cards = parser.parseMediaCards(searchHtml)
        assertEquals(2, cards.size)
        assertTrue(cards[0].isSeries)
        assertTrue(cards[1].isSeries)
        assertEquals("MobLand", cards[0].title)
        assertEquals("Lioness", cards[1].title)
        assertTrue(cards[0].streamUrl.startsWith("https://twolekede.com/seriesdetails.php"))
    }

    @Test
    fun testTwolekedeMovieDescriptionExtraction() {
        val movieDetailsHtml = """
            <html>
                <body>
                    <div class="movie-details">
                        <h1>The Beekeeper</h1>
                        <div class="description">
                            One man's brutal campaign for vengeance takes on national stakes after he is revealed to be a former operative of a powerful clandestine organization.
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val desc = parser.extractMovieDescriptionFromWatchPage(movieDetailsHtml)
        assertEquals(
            "One man's brutal campaign for vengeance takes on national stakes after he is revealed to be a former operative of a powerful clandestine organization.",
            desc
        )
        assertFalse(parser.isPlaceholderOrGenericDescription(desc))
    }

    @Test
    fun testMediaCacheRejectsBareTwolekedeStreamPath() {
        assertNull(MediaCache.getStreamPath("/twolekede"))
        assertNull(MediaCache.getStreamPath("twolekede"))
        assertNull(MediaCache.getStreamPath("/watch"))

        val itemWithBareTwolekede = MediaItem(
            id = "test_item_bare",
            title = "Test Item",
            streamUrl = "/twolekede"
        )
        MediaCache.put(itemWithBareTwolekede)
        assertNull(MediaCache.getStreamPath("test_item_bare"))

        val itemWithValidMid = MediaItem(
            id = "test_item_valid",
            title = "Valid Item",
            streamUrl = "/twolekede?mid=121915"
        )
        MediaCache.put(itemWithValidMid)
        assertEquals("/twolekede?mid=121915", MediaCache.getStreamPath("test_item_valid"))
    }

    @Test
    fun testVjQualificationPreventsDifferentVjOverwrite() {
        val juniorDeadpool = MediaItem(
            id = "1001",
            title = "Deadpool",
            vj = "VJ Junior",
            streamUrl = "/twolekede?mid=1001"
        )
        val iceDeadpool = MediaItem(
            id = "1002",
            title = "Deadpool",
            vj = "VJ Ice",
            streamUrl = "/twolekede?mid=1002"
        )
        MediaCache.putMovieDetail(juniorDeadpool)
        MediaCache.putMovieDetail(iceDeadpool)

        // Details by ID should be completely isolated
        assertEquals("VJ Junior", MediaCache.getMovieDetail("1001")?.vj)
        assertEquals("VJ Ice", MediaCache.getMovieDetail("1002")?.vj)
        assertEquals("/twolekede?mid=1001", MediaCache.getMovieDetail("1001")?.streamUrl)
        assertEquals("/twolekede?mid=1002", MediaCache.getMovieDetail("1002")?.streamUrl)
    }

    @Test
    fun testCleanTitleRejectsByAndVjPrefixes() {
        assertEquals("", parser.cleanTitle("by VJ Junior"))
        assertEquals("", parser.cleanTitle("by"))
        assertEquals("", parser.cleanTitle("VJ Junior"))
        assertEquals("One Night Only", parser.cleanTitle("One Night Only By VJ Junior - Twolekede"))
        assertEquals("The Love Hypothesis", parser.cleanTitle("The Love Hypothesis"))
    }

    @Test
    fun testTitleExtractionIgnoresSubtitleParagraphs() {
        val watchPageHtml = """
            <html>
                <body>
                    <div id="title-block">
                        <h1>One Night Only</h1>
                        <p>by VJ Junior</p>
                    </div>
                </body>
            </html>
        """.trimIndent()
        val title = parser.extractMovieTitleFromWatchPage(watchPageHtml)
        assertEquals("One Night Only", title)
    }

    @Test
    fun testMunowatchGumiteStreamExtraction() {
        // Base64 for https://munotech2.b-cdn.net/port/str/sample_stream.mp4
        val rawStream = "https://munotech2.b-cdn.net/port/str/sample_stream.mp4"
        val b64 = java.util.Base64.getEncoder().encodeToString(rawStream.toByteArray(Charsets.UTF_8))
        val html = """
            <html>
                <body>
                    <div id="soul-info" data-gumite="$b64"></div>
                    <div id="title-block">
                        <h1>The Love Hypothesis</h1>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val source = parser.extractVideoSourceFromWatchPage(html, "Server 1 (Fast CDN)")
        assertNotNull(source)
        assertEquals(rawStream, source!!.url)
        assertEquals("https://munowatch.com/", source.headers["Referer"])
    }

    @Test
    fun testSecureSessionManagerDefaultActiveSession() {
        assertEquals("926cdd498cd8113008318bd9c6696ff5", SecureSessionManager.DEFAULT_SESSION_ID)
    }

    @Test
    fun testCleanPlayerDisplayTitleStripsWatchTranslatedPrefixes() {
        // Test standard watch translated prefixes and suffixes
        val dirtyTitle1 = "Watch Deadpool & Wolverine Translated in Luganda by VJ Emmy"
        assertEquals("Deadpool & Wolverine", com.example.presentation.player.cleanPlayerDisplayTitle(dirtyTitle1, "1001"))

        val dirtyTitle2 = "Watch translated in Luganda by VJ Junior - The Beekeeper"
        assertEquals("The Beekeeper", com.example.presentation.player.cleanPlayerDisplayTitle(dirtyTitle2, "1002"))

        val dirtyTitle3 = "Watch translated - Superman"
        assertEquals("Superman", com.example.presentation.player.cleanPlayerDisplayTitle(dirtyTitle3, "1003"))

        // When title is only generic "watch translated ...", fallback to cached item
        val cachedMovie = MediaItem(id = "movie_gladiator", title = "Gladiator II")
        MediaCache.put(cachedMovie)
        val genericTitle = "watch translated in luganda"
        assertEquals("Gladiator II", com.example.presentation.player.cleanPlayerDisplayTitle(genericTitle, "movie_gladiator"))

        // When candidate is "Movies Online & TV shows", cached catalog title should be used
        val justSayIt = MediaItem(id = "1351", title = "Just Say It")
        MediaCache.put(justSayIt)
        val siteHeaderTitle = "Movies Online & TV shows"
        assertEquals("Just Say It", com.example.presentation.player.cleanPlayerDisplayTitle(siteHeaderTitle, "1351"))

        // Series episode parsing from contentId
        assertEquals("Money Heist - Episode 1", com.example.presentation.player.cleanPlayerDisplayTitle(null, "series_money_heist_ep1"))
    }

    @Test
    fun testMediaCacheWatchProgressPersistence() {
        MediaCache.saveWatchPosition("test_movie_456", 145000L)
        assertEquals(145000L, MediaCache.getSavedWatchPosition("test_movie_456"))

        // Verify that clearing cache clears watch position
        MediaCache.clear()
        assertNull(MediaCache.getSavedWatchPosition("test_movie_456"))
    }

    @Test
    fun testMediaCacheFindAdjacentEpisodes() {
        val ep1 = com.example.domain.model.Episode(id = "ep_101", number = 1, title = "Episode 1", thumbnail = "", streamUrl = "https://twolekede.com/preview.php?id=101")
        val ep2 = com.example.domain.model.Episode(id = "ep_102", number = 2, title = "Episode 2", thumbnail = "", streamUrl = "https://twolekede.com/preview.php?id=102")
        val ep3 = com.example.domain.model.Episode(id = "ep_103", number = 3, title = "Episode 3", thumbnail = "", streamUrl = "https://twolekede.com/preview.php?id=103")

        val seriesDetail = com.example.domain.model.SeriesDetail(
            id = "series_test_show",
            title = "Test Show",
            posterUrl = "",
            backdropUrl = "",
            description = "",
            year = "2024",
            seasons = listOf(
                com.example.domain.model.Season(number = 1, title = "Season 1", episodes = listOf(ep1, ep2, ep3))
            )
        )
        MediaCache.putSeriesDetail(seriesDetail)

        // Middle episode has both previous and next
        val (prevEp2, nextEp2) = MediaCache.findAdjacentEpisodes("ep_102")
        assertNotNull(prevEp2)
        assertEquals(1, prevEp2?.number)
        assertNotNull(nextEp2)
        assertEquals(3, nextEp2?.number)

        // First episode has no previous, but has next
        val (prevEp1, nextEp1) = MediaCache.findAdjacentEpisodes("ep_101")
        assertNull(prevEp1)
        assertNotNull(nextEp1)
        assertEquals(2, nextEp1?.number)

        // Last episode has previous, but no next
        val (prevEp3, nextEp3) = MediaCache.findAdjacentEpisodes("ep_103")
        assertNotNull(prevEp3)
        assertEquals(2, prevEp3?.number)
        assertNull(nextEp3)
    }

    @Test
    fun testVjGenreMoviesCacheAndFiltering() {
        val vj1 = com.example.domain.model.CategoryItem(id = 1, name = "VJ Junior", type = com.example.domain.model.CategoryType.VJ)
        val genreAction = com.example.domain.model.CategoryItem(id = 1, name = "Action", type = com.example.domain.model.CategoryType.GENRE)
        val genreDrama = com.example.domain.model.CategoryItem(id = 17, name = "Drama", type = com.example.domain.model.CategoryType.GENRE)

        val movie1 = MediaItem(id = "m1", title = "Extraction II", vj = "VJ Junior", genre = "Action")
        val movie2 = MediaItem(id = "m2", title = "Oppenheimer", vj = "VJ Junior", genre = "Drama")
        val movie3 = MediaItem(id = "m3", title = "John Wick 4", vj = "VJ Emmy", genre = "Action")

        // Test matching logic
        assertTrue(com.example.presentation.movies.MoviesViewModel.matchesVj(movie1, vj1.name))
        assertTrue(com.example.presentation.movies.MoviesViewModel.matchesCategory(movie1, genreAction.name))
        assertFalse(com.example.presentation.movies.MoviesViewModel.matchesCategory(movie1, genreDrama.name))

        assertTrue(com.example.presentation.movies.MoviesViewModel.matchesVj(movie2, vj1.name))
        assertTrue(com.example.presentation.movies.MoviesViewModel.matchesCategory(movie2, genreDrama.name))
        assertFalse(com.example.presentation.movies.MoviesViewModel.matchesCategory(movie2, genreAction.name))

        assertFalse(com.example.presentation.movies.MoviesViewModel.matchesVj(movie3, vj1.name))
        assertTrue(com.example.presentation.movies.MoviesViewModel.matchesCategory(movie3, genreAction.name))

        // Test VJ + Genre cache
        MediaCache.setVjGenreMovies(vj1.id, genreAction.id, listOf(movie1))
        val cached = MediaCache.getVjGenreMovies(vj1.id, genreAction.id)
        assertNotNull(cached)
        assertEquals(1, cached?.size)
        assertEquals("Extraction II", cached?.first()?.title)

        // Clear cache should also clear VJ Genre cache
        MediaCache.clear()
        assertNull(MediaCache.getVjGenreMovies(vj1.id, genreAction.id))
    }

    @Test
    fun testComprehensiveVjGenreClassificationAndDeepMatching() {
        // Test action blockbuster titles without explicit genre tag (like parsed Twolekede cards)
        val actionTitles = listOf(
            "John Wick: Chapter 4",
            "The Equalizer 3",
            "Bad Boys: Ride or Die",
            "The Beekeeper",
            "Top Gun: Maverick",
            "Fast X",
            "Nobody",
            "Rambo: Last Blood",
            "Terminator: Dark Fate",
            "Gladiator II",
            "Extraction 2",
            "Wrath of Man",
            "Rebel Ridge"
        )
        for (title in actionTitles) {
            val item = MediaItem(id = "act_${title.hashCode()}", title = title, genre = "", vj = "VJ Junior")
            assertTrue("Expected '$title' to match Action", com.example.presentation.movies.MoviesViewModel.matchesCategory(item, "Action"))
            // Case-insensitive check
            assertTrue("Expected '$title' to match action (lowercase)", com.example.presentation.movies.MoviesViewModel.matchesCategory(item, "action"))
        }

        // Test drama titles without explicit genre tag
        val dramaTitles = listOf("Oppenheimer", "Doing Life", "Arafta", "Titanic", "Cast Away", "Shawshank", "Holdovers")
        for (title in dramaTitles) {
            val item = MediaItem(id = "drm_${title.hashCode()}", title = title, genre = "", vj = "VJ Emmy")
            assertTrue("Expected '$title' to match Drama", com.example.presentation.movies.MoviesViewModel.matchesCategory(item, "Drama"))
        }

        // Test comedy titles without explicit genre tag
        val comedyTitles = listOf("Free Guy", "Just Say It", "One Laugh", "Minions", "Johnny English", "Grown Ups")
        for (title in comedyTitles) {
            val item = MediaItem(id = "cmd_${title.hashCode()}", title = title, genre = "", vj = "VJ Ice P")
            assertTrue("Expected '$title' to match Comedy", com.example.presentation.movies.MoviesViewModel.matchesCategory(item, "Comedy"))
        }

        // Test martial arts titles without explicit genre tag
        val martialTitles = listOf("Ip Man 4", "Kung Fu Hustle", "Lion Fist", "Ong Bak", "Raid 2")
        for (title in martialTitles) {
            val item = MediaItem(id = "ma_${title.hashCode()}", title = title, genre = "", vj = "VJ Jingo")
            assertTrue("Expected '$title' to match Martial Arts", com.example.presentation.movies.MoviesViewModel.matchesCategory(item, "Martial Arts"))
        }

        // Verify deep list caching for VJ + Genre
        val deepList = (1..85).map { idx ->
            MediaItem(id = "vj_action_$idx", title = "Action Movie $idx", vj = "VJ Junior", genre = "Action")
        }
        MediaCache.setVjGenreMovies(1, 1, deepList)
        val loaded = MediaCache.getVjGenreMovies(1, 1)
        assertNotNull(loaded)
        assertEquals(85, loaded?.size)
    }

    @Test
    fun testTitlesMatchAccuracy() {
        // Positive matches (variations of the same movie)
        assertTrue(parser.titlesMatch("Endless", "Endless By VJ Junior - Preview"))
        assertTrue(parser.titlesMatch("Endless", "Endless (2020)"))
        assertTrue(parser.titlesMatch("Black Water: Abyss", "Black Water Abyss By VJ Junior"))
        assertTrue(parser.titlesMatch("Books of Blood", "Books of Blood By VJ Junior - Preview"))
        assertTrue(parser.titlesMatch("Books of Blood", "BOOKS OF BLOOD 2026 JR 1080p"))
        assertTrue(parser.titlesMatch("Sardar - Part 1", "Sardar Part 1"))

        // Negative matches: MUST return false to prevent ID collisions and playing wrong movies
        assertFalse(parser.titlesMatch("Endless", "The Princess Weiyoung"))
        assertFalse(parser.titlesMatch("Endless", "The Curse of Turandot"))
        assertFalse(parser.titlesMatch("Endless", "Disturbia"))
        assertFalse(parser.titlesMatch("Endless", "Beyond Goodbye (Endless Goodbye)"))
        assertFalse(parser.titlesMatch("Black Water: Abyss", "The Princess Weiyoung"))
        assertFalse(parser.titlesMatch("Black Water: Abyss", "The Impossible"))
        assertFalse(parser.titlesMatch("Books of Blood", "The Princess Weiyoung"))
    }

    @Test
    fun testMovieDetailsPreservesCardMetadata() {
        val originalItem = MediaItem(
            id = "4900",
            title = "Black Water: Abyss",
            posterUrl = "https://image.tmdb.org/t/p/w500/blackwater.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/blackwater_bg.jpg",
            vj = "VJ Junior",
            year = "2020",
            streamUrl = "https://munowatch.com/twolekede?mid=4900"
        )
        MediaCache.put(originalItem)

        // Verify title matching rejects unrelated watch page
        val unrelatedPageTitle = "The Princess Weiyoung By VJ Moon - Preview"
        assertFalse(parser.titlesMatch(unrelatedPageTitle, originalItem.title))

        // Ensure original item in cache retains authentic metadata
        val retrieved = MediaCache.get("4900")
        assertNotNull(retrieved)
        assertEquals("Black Water: Abyss", retrieved?.title)
        assertEquals("https://image.tmdb.org/t/p/w500/blackwater.jpg", retrieved?.posterUrl)
        assertEquals("VJ Junior", retrieved?.vj)
    }

    @Test
    fun testHorrorMoviesNeverMatchComedyOrRomance() {
        val horrorItems = listOf(
            MediaItem(id = "h1", title = "The Conjuring", genre = "Horror", description = "Paranormal investigators Ed and Lorraine Warren work to help a family terrorized by a dark presence in their farmhouse."),
            MediaItem(id = "h2", title = "Smile 2", genre = "Movie", description = "About to embark on a world tour, global pop sensation Skye Riley begins experiencing increasingly terrifying and inexplicable events."),
            MediaItem(id = "h3", title = "Annabelle Comes Home", genre = "", description = "Determined to keep Annabelle from wreaking more havoc, demonologists Ed and Lorraine Warren bring the possessed doll to the artifacts room in their home."),
            MediaItem(id = "h4", title = "Terrifier 3", genre = "Movie", description = "Art the Clown is set to unleash chaos on the unsuspecting residents of Miles County as they peacefully drift off to sleep on Christmas Eve."),
            MediaItem(id = "h5", title = "A Nightmare on Elm Street", genre = "Horror", description = "Teenagers in a small town are dropped one by one in their dreams by a scarred killer."),
            MediaItem(id = "h6", title = "Evil Dead Rise", genre = "", description = "A twisted tale of two estranged sisters whose reunion is cut short by the rise of flesh-possessing demons.")
        )

        for (item in horrorItems) {
            assertFalse("Expected '${item.title}' NOT to match Comedy", com.example.presentation.movies.MoviesViewModel.matchesCategory(item, "Comedy"))
            assertFalse("Expected '${item.title}' NOT to match Romance", com.example.presentation.movies.MoviesViewModel.matchesCategory(item, "Romance"))
            assertFalse("Expected '${item.title}' NOT to match Family", com.example.presentation.movies.MoviesViewModel.matchesCategory(item, "Family"))
        }
    }

    @Test
    fun testTitleMatchingPreventsShortWordFalsePositives() {
        // These should NOT match because of different movies despite sharing words like "fall", "dark", "dead"
        assertFalse(parser.titlesMatch("The Fall Guy", "The Fall"))
        assertFalse(parser.titlesMatch("The Dark Knight", "Dark Waters"))
        assertFalse(parser.titlesMatch("Deadpool", "Evil Dead"))
        assertFalse(parser.titlesMatch("Wonder Woman 1984", "Woman"))

        // Compound and normalized matches SHOULD match
        assertTrue(parser.titlesMatch("Hell Fire", "Hellfire"))
        assertTrue(parser.titlesMatch("Spider Man", "Spiderman"))
        assertTrue(parser.titlesMatch("Wonder Woman 1984", "Wonder Woman 1984 VJ Junior"))
        assertFalse("Sequel Wonder Woman 1984 must not match original Wonder Woman", parser.titlesMatch("Wonder Woman 1984", "Wonder Woman"))
        assertFalse("Dune 2 must not match Dune", parser.titlesMatch("Dune 2", "Dune"))
        assertFalse("Franchise movies with different subtitles must not match", parser.titlesMatch("Spider-Man: Homecoming", "Spider-Man: Far From Home"))
        assertFalse("Mission Impossible sequels must not match each other", parser.titlesMatch("Mission: Impossible - Fallout", "Mission: Impossible - Dead Reckoning"))
    }

    @Test
    fun testSeriesEpisodesMatchBaseSeriesName() {
        // Episode of a series should match its base series title
        assertTrue(parser.titlesMatch("Carrie", "Carrie - Episode 3"))
        assertTrue(parser.titlesMatch("Carrie - Episode 3", "Carrie"))
        assertTrue(parser.titlesMatch("MobLand", "MobLand Episode 1"))
        assertTrue(parser.titlesMatch("MobLand - S01E02", "MobLand"))
        assertTrue(parser.titlesMatch("Carrie - Episode 3", "Carrie - Episode 3"))

        // Different episodes of the same series must NOT match each other
        assertFalse(parser.titlesMatch("Carrie - Episode 1", "Carrie - Episode 2"))
        assertFalse(parser.titlesMatch("MobLand - Episode 3", "MobLand - Episode 5"))

        // Sequels of movies must still NOT match
        assertFalse(parser.titlesMatch("Equalizer", "Equalizer 2"))
        assertFalse(parser.titlesMatch("Avatar", "Avatar 2"))
    }

    @Test
    fun testParentGridContainerImageNotBorrowedBySiblingCards() {
        val html = """
            <div id="grid-container" class="content-grid">
                <a href="/watch/movie-1" class="content-item">
                    <img class="tile-img" src="https://example.com/poster1.jpg" alt="Movie 1">
                    <div class="item-info"><h3>Movie 1</h3><span class="vj">VJ Junior</span></div>
                </a>
                <a href="/watch/movie-2" class="content-item">
                    <div class="item-info"><h3>Movie 2</h3><span class="vj">VJ Jingo</span></div>
                </a>
            </div>
        """.trimIndent()

        val cards = parser.parseMediaCards(html)
        assertEquals(2, cards.size)
        assertEquals("https://example.com/poster1.jpg", cards[0].posterUrl)
        assertEquals("Movie 1", cards[0].title)
        assertEquals("VJ Junior", cards[0].vj)

        // Movie 2 must NOT borrow Movie 1's poster or VJ badge from the shared grid-container!
        assertEquals("Movie 2", cards[1].title)
        assertEquals("", cards[1].posterUrl)
        assertEquals("VJ Jingo", cards[1].vj)
    }

    @Test
    fun testMyVjIdNotExtractedAsNumericId() {
        // IDs from myvj must not be treated as Twolekede numeric IDs
        assertNull(MediaCache.extractNumericId("myvj_15960"))
        assertNull(MediaCache.extractNumericId("myvj_122722"))
        assertNull(MediaCache.extractNumericId("myvj_5248"))

        // Genuine Twolekede IDs or URLs must still be extracted
        assertEquals("122722", MediaCache.extractNumericId("122722"))
        assertEquals("122722", MediaCache.extractNumericId("moviedetails.php?id=122722"))
        assertEquals("5248", MediaCache.extractNumericId("preview.php?id=5248"))
    }

    @Test
    fun testTitlesMatchWithSignedStreamUrls() {
        // Titles matching expected title
        assertTrue(parser.titlesMatch("The Brutalist", "The Brutalist - Part 1"))
        assertTrue(parser.titlesMatch("Spider-Man: Brand New Day", "Spider-Man: Brand New Day"))

        // Mismatched movies must NOT match
        assertFalse(parser.titlesMatch("Spider-Man: Brand New Day", "The Brutalist"))
        assertFalse(parser.titlesMatch("The Amazing Spider-Man", "A Jazzman's Blues"))
        assertFalse(parser.titlesMatch("Cinderella", "The Brutalist"))
    }

    @Test
    fun testTrailerUrlsRejectedFromValidStreams() {
        // Promotional trailers from myvj / trailer CDNs must be rejected
        assertFalse(MunowatchApi.isValidStreamUrl("https://trailer256.b-cdn.net/m_mn_162391.mp4"))
        assertFalse(MunowatchApi.isValidStreamUrl("https://trailer256.b-cdn.net/intro_myvj_12345.mp4"))
        assertFalse(MunowatchApi.isValidStreamUrl("https://trailer256.b-cdn.net/s0_m_77889.mp4"))
        assertFalse(MunowatchApi.isValidStreamUrl("https://cdn.example.com/videos/movie_trailer.mp4"))
        assertFalse(MunowatchApi.isValidStreamUrl("https://cdn.example.com/videos/trailer.mp4"))
        assertFalse(MunowatchApi.isValidStreamUrl("https://cdn.example.com/videos/movie/trailer"))

        // Genuine full movie streams must be accepted
        assertTrue(MunowatchApi.isValidStreamUrl("https://dl.movviez.com/movies/12345.mp4"))
        assertTrue(MunowatchApi.isValidStreamUrl("https://s1.movviez.com/hls/stream.m3u8"))
        assertTrue(MunowatchApi.isValidStreamUrl("https://nkuba.b-cdn.net/movies/full_1080p.mp4"))
        assertTrue(MunowatchApi.isValidStreamUrl("https://munotech2.b-cdn.net/stream/playlist.m3u8"))
    }

    @Test
    fun testMediaCacheRejectsTrailerUrls() {
        val testId = "test_trailer_rejection_id"
        val trailerUrl = "https://trailer256.b-cdn.net/m_mn_99999.mp4"

        // 1. putStreamPath should ignore trailer URLs
        MediaCache.putStreamPath(testId, trailerUrl)
        assertNull(MediaCache.getStreamPath(testId))

        // 2. cachePlaybackSource should ignore trailer URLs
        val trailerSource = com.example.domain.model.PlaybackSource(
            url = trailerUrl,
            type = "video/mp4",
            serverName = "Server 1 (Fast CDN)"
        )
        MediaCache.cachePlaybackSource(testId, trailerSource)
        assertNull(MediaCache.getPlaybackSource(testId))

        // 3. MediaItem with trailerUrl should not be stored in streamPathsById
        val itemWithTrailer = com.example.domain.model.MediaItem(
            id = testId,
            title = "Test Movie",
            streamUrl = trailerUrl
        )
        MediaCache.put(itemWithTrailer)
        assertNull(MediaCache.getStreamPath(testId))
    }

    @Test
    fun testArticleStrippingAndPartMatchingInTitlesMatch() {
        // Leading articles ("The", "A", "An") matching
        assertTrue(parser.titlesMatch("The Ride", "Ride"))
        assertTrue(parser.titlesMatch("Ride", "The Ride"))
        assertTrue(parser.titlesMatch("The Silence", "Silence"))
        assertTrue(parser.titlesMatch("A Quiet Place", "Quiet Place"))
        assertTrue(parser.titlesMatch("The Batman", "Batman"))

        // Part 1 / Pt 1 matching base movie title
        assertTrue(parser.titlesMatch("Death On The Nile - Part 1", "Death On The Nile"))
        assertTrue(parser.titlesMatch("Death On The Nile", "Death On The Nile - Part 1"))
        assertTrue(parser.titlesMatch("The Platform - Part 1", "The Platform"))
        assertTrue(parser.titlesMatch("Platform - Part 1", "Platform"))

        // Sequels must still be rejected
        assertFalse(parser.titlesMatch("Dune 2", "Dune"))
        assertFalse(parser.titlesMatch("Equalizer", "Equalizer 2"))
        assertFalse(parser.titlesMatch("The Platform 2", "The Platform"))
        assertFalse(parser.titlesMatch("Death On The Nile - Part 2", "Death On The Nile - Part 1"))
    }

    @Test
    fun testParenthesizedAliasMatchingInTitlesMatch() {
        // Alternate / translated title in parentheses
        assertTrue(parser.titlesMatch("Samurai Fury (Muromachi Outsiders)", "Muromachi Outsiders"))
        assertTrue(parser.titlesMatch("Muromachi Outsiders", "Samurai Fury (Muromachi Outsiders)"))
        assertTrue(parser.titlesMatch("Black Beauty (Ride for Life)", "Ride for Life"))
    }

    @Test
    fun testMediaCacheSlugStorageAndRetrieval() {
        val testId = "myvj_15984"
        val testSlug = "brothers-2"

        MediaCache.putSlug(testId, testSlug)
        assertEquals(testSlug, MediaCache.getSlug(testId))
        assertEquals(testSlug, MediaCache.getSlug("15984"))

        MediaCache.putSlug("custom_id", "the-ride")
        assertEquals("the-ride", MediaCache.getSlug("custom_id"))
        assertEquals("the-ride", MediaCache.getSlug("myvj_custom_id"))
    }

    @Test
    fun testMunowatchWatchPageExtractsStreamWithHeaders() {
        val rawStream = "https://nkuba.b-cdn.net/movies/sample_1080p.mp4"
        val base64Stream = java.util.Base64.getEncoder().encodeToString(rawStream.toByteArray(Charsets.UTF_8))
        val html = """
            <html>
                <body>
                    <div id="soul-info" data-gumite="$base64Stream"></div>
                    <div id="title-block"><p>Sample Movie</p></div>
                </body>
            </html>
        """.trimIndent()

        val source = parser.extractVideoSourceFromWatchPage(html, "Server 1 (Fast CDN)")
        assertNotNull(source)
        assertEquals(rawStream, source?.url)
        assertEquals("1080p Full HD", source?.quality)
        assertEquals("https://munowatch.com/", source?.headers?.get("Referer"))
    }

    @Test
    fun testMunowatchDefaultSessionCookieFormat() {
        assertEquals("926cdd498cd8113008318bd9c6696ff5", SecureSessionManager.DEFAULT_SESSION_ID)
        assertEquals(32, SecureSessionManager.DEFAULT_SESSION_ID.length)
        assertTrue(SecureSessionManager.DEFAULT_SESSION_ID.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun testTrailerUrlRejection() {
        val trailerUrl1 = "https://trailer256.b-cdn.net/trailers/sample_trailer.mp4"
        val trailerUrl2 = "https://cdn.myvj.net/intro_myvj_promo.mp4"
        val fullMovieUrl = "https://nkuba.b-cdn.net/movies/full_movie_1080p.mp4"

        fun isTrailer(url: String) = url.contains("trailer256.b-cdn.net") || url.contains("intro_myvj")

        assertTrue(isTrailer(trailerUrl1))
        assertTrue(isTrailer(trailerUrl2))
        assertFalse(isTrailer(fullMovieUrl))
    }

    @Test
    fun testMunowatchStreamFallbackHeaders() {
        val cookie = SecureSessionManager.DEFAULT_SESSION_ID
        val headers = mapOf(
            "Cookie" to "PHPSESSID=$cookie",
            "Referer" to "https://munowatch.com/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )
        assertEquals("PHPSESSID=926cdd498cd8113008318bd9c6696ff5", headers["Cookie"])
        assertEquals("https://munowatch.com/", headers["Referer"])
    }
}
