package com.novastream.app.ui.search

import com.novastream.app.data.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultAggregatorTest {

    @Test
    fun `dedupes same title from multiple providers`() {
        val s1 = Series(id = "a", title = "Breaking Bad", year = "2008", providerId = "p1")
        val s2 = Series(id = "b", title = "Breaking Bad", year = "2008", providerId = "p2")
        val result = SearchResultAggregator.aggregate(
            listOf("p1" to listOf(s1), "p2" to listOf(s2))
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `keeps distinct titles`() {
        val s1 = Series(id = "a", title = "Breaking Bad", providerId = "p1")
        val s2 = Series(id = "b", title = "Better Call Saul", providerId = "p1")
        val result = SearchResultAggregator.aggregate(listOf("p1" to listOf(s1, s2)))
        assertEquals(2, result.size)
    }

    @Test
    fun `detailed aggregation tracks provider ids`() {
        val s1 = Series(id = "a", title = "Dark", year = "2017", providerId = "p1")
        val s2 = Series(id = "b", title = "Dark", year = "2017", providerId = "p2", coverUrl = "http://x")
        val detailed = SearchResultAggregator.aggregateDetailed(listOf("p1" to listOf(s1), "p2" to listOf(s2)))
        assertEquals(1, detailed.size)
        assertTrue(detailed.first().providerIds.contains("p1"))
        assertTrue(detailed.first().providerIds.contains("p2"))
    }

    @Test
    fun `dedupes by imdb id across providers`() {
        val s1 = Series(id = "a", title = "Breaking Bad", imdbId = "tt0903747", providerId = "p1")
        val s2 = Series(id = "b", title = "Breaking Bad DE", imdbId = "tt0903747", providerId = "p2")
        val result = SearchResultAggregator.aggregate(
            listOf("p1" to listOf(s1), "p2" to listOf(s2))
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `dedupes by tvmaze id across providers`() {
        val s1 = Series(id = "a", title = "Dark", tvmazeId = "536", providerId = "p1")
        val s2 = Series(id = "b", title = "Dark", tvmazeId = "536", providerId = "p2")
        val result = SearchResultAggregator.aggregate(
            listOf("p1" to listOf(s1), "p2" to listOf(s2))
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `dedupes by tmdb id even when titles differ`() {
        val s1 = Series(id = "a", title = "Breaking Bad", year = "2008", providerId = "p1", canonicalKey = "tmdb:1396", tmdbId = 1396)
        val s2 = Series(id = "b", title = "Breaking Bad DE", year = "2008", providerId = "p2", canonicalKey = "tmdb:1396", tmdbId = 1396)
        val result = SearchResultAggregator.aggregate(
            listOf("p1" to listOf(s1), "p2" to listOf(s2))
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `prefers entry with tmdb id when merging`() {
        val s1 = Series(id = "a", title = "Inception", year = "2010", providerId = "p1")
        val s2 = Series(id = "b", title = "Inception", year = "2010", providerId = "p2", tmdbId = 27205)
        val detailed = SearchResultAggregator.aggregateDetailed(listOf("p1" to listOf(s1), "p2" to listOf(s2)))
        assertEquals(1, detailed.size)
        assertEquals(27205, detailed.first().series.tmdbId)
    }

    @Test
    fun `mergeSeriesFields unions external ids and isAdult`() {
        val s1 = Series(id = "a", title = "Dark", providerId = "p1", coverUrl = "http://cover")
        val s2 = Series(id = "b", title = "Dark", providerId = "p2", imdbId = "tt5753856", isAdult = true)
        val merged = SearchResultAggregator.mergeSeriesFields(listOf(s1, s2))
        assertEquals("http://cover", merged.coverUrl)
        assertEquals("tt5753856", merged.imdbId)
        assertEquals(true, merged.isAdult)
    }

    @Test
    fun `mergeSeriesFields keeps adult when any provider marks adult`() {
        val safe = Series(id = "a", title = "Peppa", providerId = "p1", isAdult = false)
        val risky = Series(id = "b", title = "Peppa", providerId = "p2", isAdult = true)
        val merged = SearchResultAggregator.mergeSeriesFields(listOf(safe, risky))
        assertEquals(true, merged.isAdult)
    }
}
