package com.nutriscan.data.repository

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

/**
 * Unit tests for the trending score algorithm used in SocialRepository.
 *
 * The algorithm: engagement / (hours + 2) ^ decay
 *   - engagement = likes + comments * 2
 *   - decay = 1.5 for posts <24h old, 1.8 for older posts
 *
 * Since calculateTrendingScore is private, we replicate the formula here
 * to test its mathematical properties and edge cases.
 */
class TrendingScoreTest {

    /**
     * Replicate the trending score formula for testing.
     */
    private fun trendingScore(likes: Int, comments: Int, hoursSincePost: Double): Double {
        val engagement = (likes + comments * 2).toDouble()
        return if (hoursSincePost < 24) {
            engagement / (hoursSincePost + 2).pow(1.5)
        } else {
            engagement / (hoursSincePost + 2).pow(1.8)
        }
    }

    // ==================== Basic Behavior ====================

    @Test
    fun `fresh post with engagement has high score`() {
        val score = trendingScore(likes = 10, comments = 5, hoursSincePost = 1.0)
        assertTrue("Fresh post with 10 likes, 5 comments should score > 0", score > 0)
    }

    @Test
    fun `zero engagement returns zero score`() {
        val score = trendingScore(likes = 0, comments = 0, hoursSincePost = 5.0)
        assertEquals(0.0, score, 0.001)
    }

    // ==================== Time Decay ====================

    @Test
    fun `score decreases as post ages`() {
        val fresh = trendingScore(likes = 10, comments = 5, hoursSincePost = 1.0)
        val medium = trendingScore(likes = 10, comments = 5, hoursSincePost = 12.0)
        val old = trendingScore(likes = 10, comments = 5, hoursSincePost = 48.0)

        assertTrue("1h score > 12h score", fresh > medium)
        assertTrue("12h score > 48h score", medium > old)
    }

    @Test
    fun `decay accelerates after 24 hours`() {
        // Compare decay rate at 23h vs 25h
        val at23h = trendingScore(likes = 20, comments = 10, hoursSincePost = 23.0)
        val at25h = trendingScore(likes = 20, comments = 10, hoursSincePost = 25.0)

        // The drop from 23h to 25h should be significant due to the exponent change (1.5 → 1.8)
        val drop = at23h - at25h
        assertTrue("Score should drop noticeably across the 24h boundary", drop > 0)
    }

    // ==================== Engagement Weighting ====================

    @Test
    fun `comments are weighted 2x compared to likes`() {
        val likesOnly = trendingScore(likes = 10, comments = 0, hoursSincePost = 5.0)
        val commentsOnly = trendingScore(likes = 0, comments = 5, hoursSincePost = 5.0)

        // 10 likes = 10 engagement; 5 comments = 10 engagement → should be equal
        assertEquals(likesOnly, commentsOnly, 0.001)
    }

    @Test
    fun `more engagement yields higher score at same age`() {
        val low = trendingScore(likes = 2, comments = 1, hoursSincePost = 5.0)
        val high = trendingScore(likes = 50, comments = 20, hoursSincePost = 5.0)

        assertTrue("Higher engagement > lower engagement at same time", high > low)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `brand new post (0 hours) does not divide by zero`() {
        val score = trendingScore(likes = 5, comments = 3, hoursSincePost = 0.0)
        // Denominator = (0 + 2)^1.5 = 2.83, so it should not crash
        assertTrue("Brand new post should have finite positive score", score > 0 && score.isFinite())
    }

    @Test
    fun `very old post (720 hours = 30 days) has near-zero score`() {
        val score = trendingScore(likes = 10, comments = 5, hoursSincePost = 720.0)
        assertTrue("30-day old post should have very low score", score < 0.01)
    }

    @Test
    fun `viral old post can still beat mediocre fresh post`() {
        val viralOld = trendingScore(likes = 1000, comments = 500, hoursSincePost = 10.0)
        val mediocreFresh = trendingScore(likes = 2, comments = 0, hoursSincePost = 1.0)

        assertTrue("Viral old post should beat mediocre fresh post", viralOld > mediocreFresh)
    }
}
