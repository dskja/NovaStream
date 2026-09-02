package com.novastream.app.util

import com.novastream.app.data.db.WatchProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsContentFilterProgressTest {

    @Test
    fun isKidsSafeProgress_blocksWhenIsAdultTrue() {
        val progress = WatchProgress(
            episodeKey = "default|p1|show-1-1",
            slug = "family-show",
            seriesTitle = "Family Show",
            coverUrl = null,
            season = 1,
            episode = 1,
            episodeTitle = "Pilot",
            positionMs = 1000,
            durationMs = 3600000,
            isAdult = true
        )
        assertFalse(KidsContentFilter.isKidsSafeProgress(progress))
    }

    @Test
    fun isKidsSafeProgress_allowsWhenIsAdultFalse() {
        val progress = WatchProgress(
            episodeKey = "default|p1|bluey-1-1",
            slug = "bluey",
            seriesTitle = "Bluey",
            coverUrl = null,
            season = 1,
            episode = 1,
            episodeTitle = "Magic",
            positionMs = 1000,
            durationMs = 3600000,
            isAdult = false
        )
        assertTrue(KidsContentFilter.isKidsSafeProgress(progress))
    }
}
