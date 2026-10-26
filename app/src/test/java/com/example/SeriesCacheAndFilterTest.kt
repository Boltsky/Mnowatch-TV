package com.example

import com.example.data.api.MunowatchApi
import com.example.data.api.MunowatchHtmlParser
import com.example.data.repository.MediaCache
import com.example.domain.model.Episode
import com.example.domain.model.Season
import com.example.domain.model.SeriesDetail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeriesCacheAndFilterTest {

    @Before
    fun setUp() {
        MediaCache.clear()
    }

    @After
    fun tearDown() {
        MediaCache.clear()
    }

    @Test
    fun testSingleMoviesMisclassifiedAsSeries_areDetected() {
        // Known single movies
        assertTrue(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Lansky"))
        assertTrue(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Knives Out"))
        assertTrue(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Glass Onion"))
        assertTrue(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Oppenheimer"))
        assertTrue(MunowatchApi.isSingleMovieMisclassifiedAsSeries("The Brutalist"))
        assertTrue(MunowatchApi.isSingleMovieMisclassifiedAsSeries("The Irishman"))

        // Ugawatch 2-part feature film uploaded under media_type 2
        assertTrue(
            MunowatchApi.isSingleMovieMisclassifiedAsSeries(
                title = "Random Action Movie",
                trailer = "m_sr_action_movie_e_1.mp4",
                munoImg = "poster_123.jpg"
            )
        )

        // Authentic series should NOT be misclassified
        assertFalse(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Peaky Blinders"))
        assertFalse(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Wildflower"))
        assertFalse(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Clarice"))
        assertFalse(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Criminal Minds: Evolution"))
        assertFalse(MunowatchApi.isSingleMovieMisclassifiedAsSeries("Adolescence"))
        assertFalse(MunowatchApi.isSingleMovieMisclassifiedAsSeries("The Gentlemen Season 1"))
        assertFalse(
            MunowatchApi.isSingleMovieMisclassifiedAsSeries(
                title = "Peaky Blinders",
                trailer = "m_sr_peaky_blinders_e_1.mp4",
                munoImg = "poster_peaky.jpg"
            )
        )
    }

    @Test
    fun testMediaCache_doesNotReturnWrongSeriesDetailsOnAliasCollision() {
        val seriesA = SeriesDetail(
            id = "series_wildflower",
            title = "Wildflower",
            description = "Wildflower drama",
            posterUrl = "https://twolekede.com/wildflower.jpg",
            backdropUrl = "",
            seasons = listOf(
                Season(1, "Season 1", listOf(Episode("e1", 1, "Episode 1", streamUrl = "https://stream/1.mp4")))
            )
        )

        val seriesB = SeriesDetail(
            id = "series_peaky_blinders",
            title = "Peaky Blinders",
            description = "Peaky Blinders drama",
            posterUrl = "https://twolekede.com/peaky.jpg",
            backdropUrl = "",
            seasons = listOf(
                Season(1, "Season 1", listOf(Episode("e2", 1, "Episode 1", streamUrl = "https://stream/2.mp4")))
            )
        )

        MediaCache.putSeriesDetail(seriesA)

        // Querying for series B must return null, not series A!
        val resultForB = MediaCache.getSeriesDetail("series_peaky_blinders")
        assertNull("Series B lookup should not return Series A", resultForB)

        // Querying with generic term should return null
        val resultGeneric = MediaCache.getSeriesDetail("series")
        assertNull("Generic keyword 'series' should not match any detail", resultGeneric)

        // Storing series B
        MediaCache.putSeriesDetail(seriesB)

        // Both should now resolve accurately to themselves
        val lookupA = MediaCache.getSeriesDetail("series_wildflower")
        assertNotNull(lookupA)
        assertEquals("series_wildflower", lookupA?.id)

        val lookupB = MediaCache.getSeriesDetail("series_peaky_blinders")
        assertNotNull(lookupB)
        assertEquals("series_peaky_blinders", lookupB?.id)
    }

    @Test
    fun testParseCategoriesHtml() {
        val parser = MunowatchHtmlParser()
        val sampleHtml = """
            <html>
                <body>
                    <div class="card">
                        <a href="moviedetails.php?id=123" title="Inception (2010)">
                            <img src="https://twolekede.com/inception.jpg"/>
                            <div class="title">Inception</div>
                        </a>
                    </div>
                    <div class="card">
                        <a href="seriesdetails.php?title=Loki" title="Loki Season 1">
                            <img src="https://twolekede.com/loki.jpg"/>
                            <div class="title">Loki</div>
                        </a>
                    </div>
                </body>
            </html>
        """.trimIndent()
        val parsed = parser.parseMediaCards(sampleHtml)
        val movies = parsed.filter { !it.isSeries }
        val series = parsed.filter { it.isSeries }
        assertEquals(2, parsed.size)
        assertEquals(1, movies.size)
        assertEquals("Inception", movies[0].title)
        assertEquals(1, series.size)
        assertEquals("Loki", series[0].title)
    }
}
