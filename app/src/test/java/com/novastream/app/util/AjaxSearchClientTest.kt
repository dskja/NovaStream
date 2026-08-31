package com.novastream.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AjaxSearchClientTest {

    @Test
    fun parseJsonResults_extractsSeriesFromArray() {
        val json = """
            [
              {"title": "Test Serie", "link": "/serie/test-serie"},
              {"name": "Zweite", "url": "/serie/zweite"}
            ]
        """.trimIndent()
        val results = AjaxSearchClient.parseJsonResults(json, "https://example.com", "/serie/", false)
        assertEquals(2, results.size)
        assertEquals("test-serie", results[0].id)
        assertTrue(results[0].title.contains("Test"))
    }
}
