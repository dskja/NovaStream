package com.novastream.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WatchProgressTest {

    @Test
    fun key_isProviderScoped() {
        val k1 = WatchProgress.key("aniworld", "naruto", 1, 5)
        val k2 = WatchProgress.key("serienstream", "naruto", 1, 5)
        assertEquals("aniworld|naruto-1-5", k1)
        assertEquals("serienstream|naruto-1-5", k2)
        assertNotEquals(k1, k2)
    }

    @Test
    fun watchlistKey_isProviderScoped() {
        val k1 = WatchlistItem.key("kinoger", "avatar")
        val k2 = WatchlistItem.key("filmpalast", "avatar")
        assertNotEquals(k1, k2)
    }

    @Test
    fun episodeDisplay_forMovie_usesTitle() {
        val progress = WatchProgress(
            episodeKey = "p|m-1-1",
            providerId = "p",
            slug = "inception",
            seriesTitle = "Inception",
            coverUrl = null,
            season = 1,
            episode = 1,
            episodeTitle = "Inception",
            positionMs = 0,
            durationMs = 100,
            isMovie = true
        )
        assertEquals("Inception", progress.episodeDisplay)
    }
}
