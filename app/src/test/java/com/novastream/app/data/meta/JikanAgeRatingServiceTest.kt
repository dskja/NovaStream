package com.novastream.app.data.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JikanAgeRatingServiceTest {

    @Test
    fun isAdultFromRating_detectsRx() {
        assertEquals(true, JikanAgeRatingService.isAdultFromRating("Rx - Hentai"))
    }

    @Test
    fun isAdultFromRating_detectsPg13AsSafe() {
        assertEquals(false, JikanAgeRatingService.isAdultFromRating("PG-13 - Teens 13 or older"))
    }
}
