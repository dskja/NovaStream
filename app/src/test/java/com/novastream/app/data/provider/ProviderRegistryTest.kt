package com.novastream.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    @Test
    fun `registry has at least 50 providers`() {
        assertTrue(ProviderRegistry.providers.size >= 50)
    }

    @Test
    fun `all providers have unique ids`() {
        val ids = ProviderRegistry.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `grouped by language includes DE and EN`() {
        val grouped = ProviderRegistry.getGroupedByLanguage()
        assertTrue(grouped.containsKey(ContentLanguage.DE))
        assertTrue(grouped.containsKey(ContentLanguage.EN))
    }

    @Test
    fun `filter favorites only returns favorite providers`() {
        val fav = setOf("serienstream", "hydrahd")
        val filtered = ProviderRegistry.getFiltered(favoriteIds = fav, favoritesOnly = true)
        assertTrue(filtered.all { it.id in fav })
        assertEquals(2, filtered.size)
    }
}
