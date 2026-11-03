package com.example

import com.example.data.api.MunowatchHtmlParser
import com.example.data.repository.MediaCache
import com.example.data.repository.deduplicateMediaPreservingOrder
import com.example.domain.model.MediaItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NewlyUploadedMoviesIngestionTest {

    private val parser = MunowatchHtmlParser()

    @Before
    fun setUp() {
        MediaCache.clear()
    }

    @After
    fun tearDown() {
        MediaCache.clear()
    }

    @Test
    fun testDeduplicateMediaPreservingOrder_preservesNewestFirstAndMergesRichMetadata() {
        // Simulated items from:
        // 1. MyVJ live database (newly uploaded today, rich TMDB metadata)
        val newlyUploadedMamut = MediaItem(
            id = "myvj_16488",
            title = "Mamut",
            description = "A gripping drama about survival and courage in the harsh wilderness.",
            posterUrl = "https://image.tmdb.org/t/p/w500/mamut.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/mamut_bg.jpg",
            year = "2026",
            vj = "VJ Emmy",
            streamUrl = ""
        )
        // 2. Munowatch Pipe 18 Latest Uploads (playable streamUrl)
        val munoMamut = MediaItem(
            id = "67099",
            title = "Mamut",
            description = "",
            posterUrl = "https://apposters.b-cdn.net/laba/yo/naki/a8Md4897.jpg",
            backdropUrl = "",
            year = "",
            vj = "VJ Emmy",
            streamUrl = "/twolekede?v=xyz123&title=Mamut"
        )
        // 3. Another newly uploaded movie
        val newlyUploadedAvengers = MediaItem(
            id = "myvj_16487",
            title = "Marvel's The Avengers 3 - Infinity War",
            description = "The Avengers and their allies must be willing to sacrifice all.",
            posterUrl = "https://image.tmdb.org/t/p/w500/avengers.jpg",
            backdropUrl = "",
            year = "2026",
            vj = "VJ Ice P",
            streamUrl = ""
        )
        // 4. Twolekede older catalog movie
        val twolekedeOlder = MediaItem(
            id = "twolekede_1234",
            title = "Rise of the Footsoldier: Retribution",
            description = "British gangster thriller",
            posterUrl = "https://twolekede.com/footsoldier.jpg",
            backdropUrl = "",
            year = "2021",
            vj = "VJ Junior",
            streamUrl = "https://twolekede.com/moviedetails.php?id=1234"
        )

        val rawMergedList = listOf(newlyUploadedMamut, munoMamut, newlyUploadedAvengers, twolekedeOlder)

        val deduplicated = deduplicateMediaPreservingOrder(rawMergedList, parser)

        // Count should be 3 (Mamut deduplicated)
        assertEquals(3, deduplicated.size)

        // Order preserved: Mamut (newest) -> Avengers (newest) -> Older
        assertEquals("Mamut", deduplicated[0].title)
        assertEquals("Marvel's The Avengers 3 - Infinity War", deduplicated[1].title)
        assertEquals("Rise of the Footsoldier: Retribution", deduplicated[2].title)

        // Mamut should have merged rich TMDB metadata from MyVJ AND playable stream from Munowatch
        val mergedMamut = deduplicated[0]
        assertEquals("A gripping drama about survival and courage in the harsh wilderness.", mergedMamut.description)
        assertEquals("https://image.tmdb.org/t/p/w500/mamut.jpg", mergedMamut.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/mamut_bg.jpg", mergedMamut.backdropUrl)
        assertEquals("/twolekede?v=xyz123&title=Mamut", mergedMamut.streamUrl)
        assertEquals("2026", mergedMamut.year)
    }

    @Test
    fun testNewlyUploadedSeriesEpisodes_areCachedAndAssociatedProperly() {
        val seriesItem = MediaItem(
            id = "myvj_4979",
            title = "Dead-End Job",
            description = "Drowning in debt, a young man takes lucrative part-time jobs.",
            posterUrl = "https://image.tmdb.org/t/p/w500/dead_end.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/dead_end_bg.jpg",
            year = "2026",
            vj = "VJ Soul",
            isSeries = true,
            streamUrl = ""
        )

        MediaCache.put(seriesItem)
        MediaCache.putSlug("myvj_4979", "dead-end-job")

        val retrieved = MediaCache.get("myvj_4979")
        assertEquals("Dead-End Job", retrieved?.title)
        assertEquals("dead-end-job", MediaCache.getSlug("myvj_4979"))
        assertTrue(retrieved?.isSeries == true)
    }
}
