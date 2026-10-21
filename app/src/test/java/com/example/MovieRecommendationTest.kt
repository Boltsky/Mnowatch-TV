package com.example

import com.example.domain.model.MediaItem
import com.example.domain.recommendation.MovieRecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieRecommendationTest {

    @Test
    fun `franchise sequels and prequels receive highest recommendation score`() {
        val target = MediaItem(
            id = "quiet_place_day_one",
            title = "A Quiet Place: Day One",
            genre = "Horror",
            vj = "VJ Junior",
            year = "2024",
            description = "Alien creatures hunt by sound as survivors try to stay quiet in New York."
        )

        val sequel = MediaItem(
            id = "quiet_place_part_2",
            title = "A Quiet Place Part II",
            genre = "Horror",
            vj = "VJ Junior",
            year = "2020",
            description = "Following the deadly events at home, the Abbott family must now face the terrors of the outside world."
        )

        val unrelatedAction = MediaItem(
            id = "fast_x",
            title = "Fast X",
            genre = "Action",
            vj = "VJ Emmy",
            year = "2023",
            description = "Dom Toretto and his family are targeted by the vengeful son of drug kingpin Hernan Reyes."
        )

        val sequelScore = MovieRecommendationEngine.scoreSimilarity(target, sequel)
        val unrelatedScore = MovieRecommendationEngine.scoreSimilarity(target, unrelatedAction)

        assertTrue("Sequel score ($sequelScore) should be far higher than unrelated ($unrelatedScore)", sequelScore > unrelatedScore)
        assertTrue("Sequel score should be >= 100", sequelScore >= 100)
    }

    @Test
    fun `same genre and same VJ rank higher than different genre and VJ`() {
        val target = MediaItem(
            id = "target_horror",
            title = "Evil Dead Burn",
            genre = "Horror",
            vj = "VJ Junior",
            year = "2024",
            description = "A bloodcurdling demon rises in the dark woods."
        )

        val sameGenreAndVj = MediaItem(
            id = "cand_1",
            title = "The Nun II",
            genre = "Horror",
            vj = "VJ Junior",
            year = "2023",
            description = "A terrifying demon haunts a French convent."
        )

        val differentGenreAndVj = MediaItem(
            id = "cand_2",
            title = "Father of the Bride",
            genre = "Comedy",
            vj = "VJ Emmy",
            year = "2022",
            description = "A hilarious family wedding planning goes wild with laughter."
        )

        val score1 = MovieRecommendationEngine.scoreSimilarity(target, sameGenreAndVj)
        val score2 = MovieRecommendationEngine.scoreSimilarity(target, differentGenreAndVj)

        assertTrue(score1 > score2)
    }

    @Test
    fun `target movie is never included in its own recommendations`() {
        val target = MediaItem(
            id = "target_1",
            title = "Runner",
            genre = "Action",
            vj = "VJ Emmy",
            year = "2024"
        )

        val pool = listOf(
            target,
            target.copy(id = "target_1_dup"),
            MediaItem(id = "movie_2", title = "Just Say It", genre = "Action", vj = "VJ Emmy", year = "2024"),
            MediaItem(id = "movie_3", title = "Ice Cream Man", genre = "Horror", vj = "VJ Emmy", year = "2024")
        )

        val recommendations = MovieRecommendationEngine.findRecommendations(target, pool)

        assertFalse("Target ID must not be in recommendations", recommendations.any { it.id == target.id })
        assertFalse("Duplicate title must not be in recommendations", recommendations.any { it.title.equals("Runner", ignoreCase = true) })
        assertEquals(2, recommendations.size)
    }

    @Test
    fun `series containers are excluded from movie recommendations`() {
        val target = MediaItem(
            id = "target_movie",
            title = "The Terminator",
            genre = "Sci-Fi",
            vj = "VJ Junior",
            isSeries = false
        )

        val pool = listOf(
            MediaItem(id = "movie_1", title = "Terminator Genisys", genre = "Sci-Fi", vj = "VJ Junior", isSeries = false),
            MediaItem(id = "series_1", title = "The Sarah Connor Chronicles", genre = "Sci-Fi", vj = "VJ Junior", isSeries = true)
        )

        val recommendations = MovieRecommendationEngine.findRecommendations(target, pool)

        assertFalse("Series must be excluded", recommendations.any { it.isSeries })
        assertTrue("Movie should be included", recommendations.any { it.id == "movie_1" })
    }

    @Test
    fun `story theme keywords and description overlap boost similarity`() {
        val target = MediaItem(
            id = "target_alien",
            title = "Alien Invasion",
            genre = "Sci-Fi",
            vj = "VJ Ice P",
            description = "Alien creatures invade earth as humans fight for survival in a dangerous space apocalypse."
        )

        val similarTheme = MediaItem(
            id = "alien_theme",
            title = "Earth Defense",
            genre = "Sci-Fi",
            vj = "VJ Ice P",
            description = "Futuristic soldiers encounter deadly alien monsters threatening human survival on earth."
        )

        val differentTheme = MediaItem(
            id = "romance_theme",
            title = "Romantic Paris",
            genre = "Sci-Fi", // mislabeled or blank
            vj = "VJ Ice P",
            description = "Two lovers celebrate their wedding anniversary at a beautiful castle."
        )

        val scoreSimilar = MovieRecommendationEngine.scoreSimilarity(target, similarTheme)
        val scoreDiff = MovieRecommendationEngine.scoreSimilarity(target, differentTheme)

        assertTrue(scoreSimilar > scoreDiff)
    }
}
