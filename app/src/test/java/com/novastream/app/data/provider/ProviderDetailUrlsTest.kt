package com.novastream.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDetailUrlsTest {

    private val base = "https://example.com"

    @Test
    fun `tv and movie prefixes`() {
        assertEquals("$base/tv/breaking-bad", ProviderDetailUrls.resolve("sflix", base, "tv-breaking-bad"))
        assertEquals("$base/movie/inception", ProviderDetailUrls.resolve("sflix", base, "movie-inception"))
    }

    @Test
    fun `absolute and relative slugs`() {
        assertEquals("https://other.com/x", ProviderDetailUrls.resolve("sflix", base, "https://other.com/x"))
        assertEquals("$base/foo", ProviderDetailUrls.resolve("sflix", base, "/foo"))
    }

    @Test
    fun `provider-specific paths`() {
        assertEquals("$base/serie/dark", ProviderDetailUrls.resolve("serienstream", base, "dark"))
        assertEquals("$base/anime/stream/naruto", ProviderDetailUrls.resolve("aniworld", base, "naruto"))
        assertEquals("$base/tv-show/arcane", ProviderDetailUrls.resolve("sflix", base, "arcane"))
        assertTrue(ProviderDetailUrls.resolve("hydrahd", base, "watch-foo").contains("/movie/"))
    }

    @Test
    fun `streamkiste movie prefix`() {
        assertEquals("$base/filme/inception", ProviderDetailUrls.resolve("streamkiste", base, "movie-inception"))
        assertEquals("$base/serien/dark", ProviderDetailUrls.resolve("streamkiste", base, "dark"))
    }

    @Test
    fun `kinoger detail uses html suffix`() {
        assertEquals("$base/stream/dark.html", ProviderDetailUrls.resolve("kinoger", base, "dark"))
    }

    @Test
        assertEquals("$base/some-slug", ProviderDetailUrls.resolve("unknown", base, "some-slug"))
    }
}
