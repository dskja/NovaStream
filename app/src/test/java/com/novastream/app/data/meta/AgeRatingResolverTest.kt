package com.novastream.app.data.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeRatingResolverTest {

    @Test
    fun fromCertifications_detectsFsk18() {
        assertEquals(true, AgeRatingResolver.fromCertifications(listOf("FSK 18")))
    }

    @Test
    fun fromCertifications_detectsKidsFsk12() {
        assertEquals(false, AgeRatingResolver.fromCertifications(listOf("FSK 12")))
    }

    @Test
    fun merge_trueWins() {
        assertEquals(true, AgeRatingResolver.merge(false, null, true))
    }

    @Test
    fun resolve_prefersExplicitAdult() {
        val result = AgeRatingResolver.resolve(
            explicitAdult = true,
            certifications = listOf("FSK 12")
        )
        assertTrue(result.isAdult == true)
    }
}
