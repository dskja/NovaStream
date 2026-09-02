package com.novastream.app.util

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
}
