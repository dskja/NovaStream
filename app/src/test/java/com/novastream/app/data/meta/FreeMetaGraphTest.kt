package com.novastream.app.data.meta

import com.novastream.app.data.model.Series
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeMetaGraphTest {

    private val graph = FreeMetaGraph()

    @Test
    fun dedupeKeyForSeries_prefersCanonicalKey() {
        val series = Series(
            id = "slug-a",
            title = "Breaking Bad",
            canonicalKey = "imdb:tt0903747",
            imdbId = "tt0903747",
            providerId = "p1"
        )
        assertEquals("imdb:tt0903747", graph.dedupeKeyForSeries(series))
    }

    @Test
    fun dedupeKeyForSeries_fallsBackToImdb() {
        val series = Series(
            id = "slug-a",
            title = "Breaking Bad",
            imdbId = "tt0903747",
            providerId = "p1"
        )
        assertEquals("imdb:tt0903747", graph.dedupeKeyForSeries(series))
    }

    @Test
    fun dedupeKeyForSeries_fallsBackToTitleYear() {
        val series = Series(id = "slug-a", title = "Dark", year = "2017", providerId = "p1")
        assertEquals("dark|2017", graph.dedupeKeyForSeries(series))
    }

    @Test
    fun externalIdsFromShow_mapsTvmazeNumericId() {
        val show = MetaShow(id = "169", title = "Breaking Bad", imdbId = "tt0903747")
        val ids = graph.externalIdsFromShow(show)
        assertEquals("169", ids.tvmazeId)
        assertEquals("imdb:tt0903747", ids.canonicalKey())
    }
}
