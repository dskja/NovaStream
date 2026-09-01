package com.novastream.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlsTest {

    @Test
    fun abs_resolvesRelativePaths() {
        assertEquals(
            "https://example.com/cover.jpg",
            MediaUrls.abs("/cover.jpg", "https://example.com")
        )
    }

    @Test
    fun abs_returnsNullForBlank() {
        assertNull(MediaUrls.abs(null, "https://example.com"))
        assertNull(MediaUrls.abs("", "https://example.com"))
    }

    @Test
    fun refererFor_usesImageHost() {
        val referer = MediaUrls.refererFor("https://cdn.example.org/img.jpg", "https://fallback.com")
        assertEquals("https://cdn.example.org/", referer)
    }

    @Test
    fun sanitizeTitle_stripsHtmlTags() {
        assertEquals("Solo Leveling", MediaUrls.sanitizeTitle("<em>Solo Leveling</em>"))
    }

    @Test
    fun secureUrl_upgradesHttpToHttps() {
        assertEquals("https://kinoger.to/stream", MediaUrls.secureUrl("http://kinoger.to/stream"))
    }
}
