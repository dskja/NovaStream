package com.novastream.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderIntegrityTest {

    @Test
    fun `registry has at least 60 built-in providers`() {
        assertTrue(ProviderIntegrity.builtInProviderIds().size >= 60)
    }

    @Test
    fun `every built-in provider passes integrity checks`() {
        val issues = ProviderIntegrity.validateAll()
        if (issues.isNotEmpty()) {
            println("Provider integrity issues:")
            issues.forEach { println("  ${it.providerId}: ${it.message}") }
        }
        assertEquals(emptyList<ProviderIntegrity.Issue>(), issues)
    }

    @Test
    fun `every provider has a search query`() {
        for (id in ProviderIntegrity.builtInProviderIds()) {
            assertTrue("$id missing search query", ProviderSearchQueries.forProvider(id).isNotBlank())
        }
    }

    @Test
    fun `mirror providers have needles`() {
        val missingNeedle = ProviderIntegrity.builtInProviderIds().filter { id ->
            ProviderDomainManager.alternateDomains(id).isNotEmpty() &&
                ProviderMirrorNeedles.needleFor(id).isBlank() &&
                id !in setOf("freecatalog", "freecatalogbrowse")
        }
        assertEquals(emptyList<String>(), missingNeedle)
    }
}
