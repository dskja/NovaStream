package com.novastream.app.data.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLanguageManagerTest {

    @Test
    fun `available languages include multi and major regions`() {
        val langs = ProviderLanguageManager.getAvailableLanguages()
        assertTrue(ContentLanguage.DE in langs)
        assertTrue(ContentLanguage.EN in langs)
        assertTrue(ContentLanguage.MULTI in langs)
    }

    @Test
    fun `providers for DE include serienstream`() {
        val providers = ProviderLanguageManager.getProvidersForLanguage(ContentLanguage.DE)
        assertTrue(providers.any { it.id == "serienstream" })
    }

    @Test
    fun `providers for EN include hydrahd`() {
        val providers = ProviderLanguageManager.getProvidersForLanguage(ContentLanguage.EN)
        assertTrue(providers.any { it.id == "hydrahd" })
    }

    @Test
    fun `multi providers included in DE scope`() {
        val providers = ProviderLanguageManager.getProvidersForLanguage(ContentLanguage.DE)
        assertTrue(providers.any { it.id == "freecatalog" })
    }

    @Test
    fun `display name for DE is non blank`() {
        assertFalse(ProviderLanguageManager.getLanguageDisplayName(ContentLanguage.DE).isBlank())
    }
}
