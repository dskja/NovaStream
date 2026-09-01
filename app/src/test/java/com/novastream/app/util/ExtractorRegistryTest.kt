package com.novastream.app.util

import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorRegistryTest {

    @Test
    fun `registry has at least 40 extractors`() {
        assertTrue(ExtractorRegistry.registeredCount() >= 40)
    }

    @Test
    fun `finds voe by url`() {
        val matches = ExtractorRegistry.findMatching("https://voe.sx/e/abc123", "VOE")
        assertTrue(matches.any { it.name == "VOE" })
    }

    @Test
    fun `finds streamtape alias`() {
        val matches = ExtractorRegistry.findMatching("https://streamtape.net/e/abc", "Host")
        assertTrue(matches.any { it.name == "Streamtape" })
    }

    @Test
    fun `finds doodstream alias`() {
        val matches = ExtractorRegistry.findMatching("https://dood.la/e/abc", "Dood")
        assertTrue(matches.any { it.name == "Doodstream" })
    }
}
