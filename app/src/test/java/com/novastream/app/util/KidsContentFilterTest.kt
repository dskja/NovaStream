package com.novastream.app.util

import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsContentFilterTest {

    @Test
    fun blocksHorrorGenre() {
        val series = Series(
            id = "test",
            title = "Scary Movie",
            genres = listOf("Horror", "Comedy")
        )
        assertFalse(KidsContentFilter.isKidsSafe(series))
    }

    @Test
    fun allowsFamilyContent() {
        val series = Series(
            id = "test",
            title = "Family Fun",
            genres = listOf("Comedy", "Animation")
        )
        assertTrue(KidsContentFilter.isKidsSafe(series))
    }

    @Test
    fun blocksAdultFlag() {
        val series = Series(
            id = "test",
            title = "Normal Title",
            genres = emptyList(),
            isAdult = true
        )
        assertFalse(KidsContentFilter.isKidsSafe(series))
    }

    @Test
    fun blocksExplicitTitle() {
        assertTrue(KidsContentFilter.isBlockedTitle("Film 18+ Special"))
    }

    @Test
    fun filterSeriesDisabledWhenNotKidsMode() {
        val list = listOf(
            Series(id = "1", title = "Horror", genres = listOf("Horror"))
        )
        assertEquals(1, KidsContentFilter.filterSeries(list, kidsMode = false).size)
        assertEquals(0, KidsContentFilter.filterSeries(list, kidsMode = true).size)
    }

    @Test
    fun blocksHorrorSlug() {
        assertTrue(KidsContentFilter.isBlockedSlug("dark-horror-night"))
    }

    @Test
    fun filterWatchlistRemovesBlockedTitlesInKidsMode() {
        val items = listOf(
            WatchlistItem(
                itemKey = "p|test|safe",
                slug = "safe",
                title = "Kids Show",
                coverUrl = null,
                providerId = "test",
                addedAt = 1L
            ),
            WatchlistItem(
                itemKey = "p|test|bad",
                slug = "bad",
                title = "Film 18+ Special",
                coverUrl = null,
                providerId = "test",
                addedAt = 2L
            )
        )
        assertEquals(2, KidsContentFilter.filterWatchlist(items, kidsMode = false).size)
        assertEquals(1, KidsContentFilter.filterWatchlist(items, kidsMode = true).size)
    }

    @Test
    fun filterProgressRemovesBlockedSlugInKidsMode() {
        val items = listOf(
            WatchProgress(
                episodeKey = "a",
                slug = "safe-show",
                seriesTitle = "Safe",
                coverUrl = null,
                episodeTitle = "Ep 1",
                season = 1,
                episode = 1,
                positionMs = 0,
                durationMs = 1000,
                updatedAt = 1L
            ),
            WatchProgress(
                episodeKey = "b",
                slug = "slasher-movie",
                seriesTitle = "Slasher",
                coverUrl = null,
                episodeTitle = "Ep 1",
                season = 1,
                episode = 1,
                positionMs = 0,
                durationMs = 1000,
                updatedAt = 2L
            )
        )
        assertEquals(1, KidsContentFilter.filterProgress(items, kidsMode = true).size)
    }

    @Test
    fun isBlockedForKidsPlaybackChecksSlugAndTitles() {
        assertTrue(KidsContentFilter.isBlockedForKidsPlayback("horror-film", "Nice Title"))
        assertTrue(KidsContentFilter.isBlockedForKidsPlayback("safe", "Film 18+"))
        assertFalse(KidsContentFilter.isBlockedForKidsPlayback("safe", "Family Fun"))
    }
}
