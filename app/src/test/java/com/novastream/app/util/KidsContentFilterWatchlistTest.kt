package com.novastream.app.util

import com.novastream.app.data.db.WatchlistItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsContentFilterWatchlistTest {

    @Test
    fun isKidsSafeWatchlist_blocksWhenIsAdultTrue() {
        val item = WatchlistItem(
            itemKey = "default|p1|show",
            slug = "show",
            title = "Family Fun",
            coverUrl = null,
            isAdult = true
        )
        assertFalse(KidsContentFilter.isKidsSafeWatchlist(item))
    }

    @Test
    fun isKidsSafeWatchlist_blocksHorrorGenre() {
        val item = WatchlistItem(
            itemKey = "default|p1|show",
            slug = "show",
            title = "Night Show",
            coverUrl = null,
            genres = "Horror,Thriller"
        )
        assertFalse(KidsContentFilter.isKidsSafeWatchlist(item))
    }

    @Test
    fun isKidsSafeWatchlist_allowsKidsGenres() {
        val item = WatchlistItem(
            itemKey = "default|p1|show",
            slug = "bluey",
            title = "Bluey",
            coverUrl = null,
            genres = "Animation,Family",
            isAdult = false
        )
        assertTrue(KidsContentFilter.isKidsSafeWatchlist(item))
    }
}
