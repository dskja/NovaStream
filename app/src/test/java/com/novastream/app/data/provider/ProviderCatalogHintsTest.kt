package com.novastream.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogHintsTest {

    @Test
    fun `every major DE provider has a hint`() {
        listOf(
            "serienstream", "aniworld", "kinoger", "burningseries",
            "megakino", "streamkiste", "filmpalast", "kinoz", "hdfilme", "moflix"
        ).forEach { id ->
            assertNotNull("Missing hint for $id", ProviderCatalogHints.forId(id))
        }
    }

    @Test
    fun `international providers have hints`() {
        assertTrue(ProviderCatalogHints.forId("sflix")!!.contains("EN"))
        assertTrue(ProviderCatalogHints.forId("wiflix")!!.contains("FR"))
        assertTrue(ProviderCatalogHints.forId("fanpelis")!!.contains("ES"))
        assertTrue(ProviderCatalogHints.forId("guardaserie")!!.contains("IT"))
        assertTrue(ProviderCatalogHints.forId("filmyonline")!!.contains("PL"))
    }

    @Test
    fun `unknown provider returns null`() {
        assertNull(ProviderCatalogHints.forId("nonexistent_provider_xyz"))
    }
}
