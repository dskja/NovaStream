package com.novastream.app.data.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaAgeRatingServiceTest {

    @Test
    fun parseCertifications_extractsFskFromInfobox() {
        val wikitext = """
            {{Infobox Film
            | name = Testfilm
            | FSK = 16
            | mpaa = R
            }}
        """.trimIndent()
        val certs = WikipediaAgeRatingService.parseCertifications(wikitext)
        assertTrue(certs.any { it.contains("FSK") })
        assertTrue(certs.any { it.contains("R") })
    }

    @Test
    fun normalizeCertification_mapsNumericFsk() {
        assertEquals("FSK 12", WikipediaAgeRatingService.normalizeCertification("12"))
    }
}
