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
}
