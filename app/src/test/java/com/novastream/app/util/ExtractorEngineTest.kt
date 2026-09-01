package com.novastream.app.util

import com.novastream.app.extractor.ExtractorEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorEngineTest {

    @Test
    fun `engine has at least 40 plugins`() {
        assertTrue(ExtractorEngine.registeredCount() >= 40)
    }

    @Test
    fun `finds voe plugin by url`() {
        val matches = ExtractorEngine.findMatching("https://voe.sx/e/abc123", "VOE")
        assertTrue(matches.any { it.name == "VOE" })
    }

    @Test
    fun `finds streamtape plugin`() {
        val matches = ExtractorEngine.findMatching("https://streamtape.net/e/abc", "Host")
        assertTrue(matches.any { it.name == "Streamtape" })
    }
}
