package com.novastream.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WatchProgressTest {

    @Test
    fun key_isProfileAndProviderScoped() {
        val k1 = WatchProgress.key(ProfileEntity.DEFAULT_ID, "aniworld", "naruto", 1, 5)
        val k2 = WatchProgress.key(ProfileEntity.DEFAULT_ID, "serienstream", "naruto", 1, 5)
        val k3 = WatchProgress.key("kids", "aniworld", "naruto", 1, 5)
        assertEquals("default|aniworld|naruto-1-5", k1)
        assertEquals("default|serienstream|naruto-1-5", k2)
        assertNotEquals(k1, k2)
        assertNotEquals(k1, k3)
    }

    @Test
    fun watchlistKey_isProfileAndProviderScoped() {
        val k1 = WatchlistItem.key(ProfileEntity.DEFAULT_ID, "kinoger", "avatar")
        val k2 = WatchlistItem.key(ProfileEntity.DEFAULT_ID, "filmpalast", "avatar")
        val k3 = WatchlistItem.key("kids", "kinoger", "avatar")
        assertNotEquals(k1, k2)
        assertNotEquals(k1, k3)
    }

    @Test
    fun episodeDisplay_forMovie_usesTitle() {
        val progress = WatchProgress(
            episodeKey = "default|p|m-1-1",
            profileId = ProfileEntity.DEFAULT_ID,
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
