package com.novastream.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderGenresTest {

    @Test
    fun `DE providers have explicit genres`() {
        assertFalse(ProviderGenres.forId("serienstream").isEmpty())
        assertFalse(ProviderGenres.forId("burningseries").isEmpty())
        assertTrue(ProviderGenres.forId("aniworld").any { it.slug == "action" })
    }

    @Test
    fun `intl providers fall back to language genres`() {
        assertFalse(ProviderGenres.forId("sflix").isEmpty())
        assertFalse(ProviderGenres.forId("wiflix").isEmpty())
        assertTrue(ProviderGenres.forId("wiflix").any { it.slug == "serie" })
    }

    @Test
    fun `content language mapping`() {
        assertEquals(ContentLanguage.DE, ProviderGenres.contentLanguageOf("megakino"))
        assertEquals(ContentLanguage.FR, ProviderGenres.contentLanguageOf("wiflix"))
        assertEquals(ContentLanguage.ES, ProviderGenres.contentLanguageOf("cuevana3"))
    }

    @Test
    fun `kinoz uses site-native capital slugs`() {
        assertTrue(ProviderGenres.forId("kinoz").any { it.slug == "Action" })
    }
}
