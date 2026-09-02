package com.novastream.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderGenrePathsTest {

    @Test
    fun `filmpalast semantic genre slugs`() {
        assertEquals(listOf("/serien/view"), ProviderGenrePaths.pathsFor("filmpalast", "serien"))
        assertEquals(listOf("/movies/new"), ProviderGenrePaths.pathsFor("filmpalast", "filme"))
    }

    @Test
    fun `kinoz uses Genre path`() {
        assertEquals(listOf("/Genre/Action"), ProviderGenrePaths.pathsFor("kinoz", "Action"))
    }

    @Test
    fun `intl providers have multiple fallbacks`() {
        val paths = ProviderGenrePaths.pathsFor("dramacool", "drama")
        assertTrue(paths.size >= 2)
        assertTrue(paths.first().contains("drama"))
    }

    @Test
    fun `paged paths append page query`() {
        val paths = ProviderGenrePaths.pathsForPage("sflix", "action", page = 1)
        assertTrue(paths.any { it.contains("page=2") })
    }

    @Test
    fun `blank genre returns empty`() {
        assertTrue(ProviderGenrePaths.pathsFor("sflix", "  ").isEmpty())
    }
}
