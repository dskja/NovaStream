package com.novastream.app.data.meta

import com.novastream.app.data.model.Series
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetaEnrichmentCacheTest {

    @After
    fun tearDown() {
        MetaEnrichmentCache.clear()
    }

    @Test
    fun cacheKey_series_includesImdbAndCanonicalKey() {
        val series = Series(
            id = "slug",
            title = "Breaking Bad",
            canonicalKey = "imdb:tt0903747",
            imdbId = "tt0903747",
            providerId = "p1"
        )
        val key = MetaEnrichmentCache.cacheKey(series)
        assertEquals("tt0903747|imdb:tt0903747|breaking bad", key)
    }

    @Test
    fun putAndGet_returnsEnrichment() {
        val key = "test-key"
        val enrichment = MetaEnrichment(
            show = MetaShow(id = "1", title = "Dark"),
            canonicalKey = "tvmaze:185"
        )
        MetaEnrichmentCache.put(key, enrichment)
        assertEquals(enrichment, MetaEnrichmentCache.get(key))
    }

    @Test
    fun get_missingKey_returnsNull() {
        assertNull(MetaEnrichmentCache.get("missing"))
    }

    @Test
    fun clear_removesEntries() {
        val key = "clear-test"
        MetaEnrichmentCache.put(key, MetaEnrichment(show = MetaShow(id = "1", title = "Test")))
        MetaEnrichmentCache.clear()
        assertNull(MetaEnrichmentCache.get(key))
    }

    @Test
    fun cacheKey_titleHints_includesImdbAndAnilist() {
        val key = MetaEnrichmentCache.cacheKey("Naruto", imdbId = "tt0388629", tvmazeId = null, anilistId = 20)
        assertEquals("tt0388629|20|naruto", key)
    }
}
