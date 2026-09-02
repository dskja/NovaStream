package com.novastream.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdultContentDetectorTest {

    @Test
    fun detectFromText_adultFsk18() {
        assertEquals(true, AdultContentDetector.detectFromText("Film mit FSK 18 Freigabe"))
    }

    @Test
    fun detectFromText_adultTvMa() {
        assertEquals(true, AdultContentDetector.detectFromText("Rated TV-MA for violence"))
    }

    @Test
    fun detectFromText_kidsFsk6() {
        assertEquals(false, AdultContentDetector.detectFromText("FSK 6 empfohlen"))
    }

    @Test
    fun detectFromText_unknown() {
        assertNull(AdultContentDetector.detectFromText("Family comedy adventure"))
    }

    @Test
    fun detectFromText_adult18Plus() {
        assertEquals(true, AdultContentDetector.detectFromText("Rated 18+ only"))
    }

    @Test
    fun detectFromHtml_adultBadge() {
        val html = """
            <html><body>
              <span class="age-rating">18+</span>
              <p>Dark thriller series</p>
            </body></html>
        """.trimIndent()
        assertEquals(true, AdultContentDetector.detectFromHtml(html))
    }

    @Test
    fun detectFromHtml_kidsRating() {
        val html = """
            <html><head>
              <meta name="rating" content="FSK 12" />
            </head><body><p>Teen drama</p></body></html>
        """.trimIndent()
        assertEquals(false, AdultContentDetector.detectFromHtml(html))
    }
}
