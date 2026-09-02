package com.novastream.app.data.meta

import com.novastream.app.data.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMetaEnricherTest {

    private val enricher = CatalogMetaEnricher(FreeMetaGraph())

    @Test
    fun applyEnrichment_mergesIdsAndIsAdult() {
        val series = Series(id = "slug", title = "Naruto", providerId = "p1")
        val enrichment = MetaEnrichment(
            show = MetaShow(
                id = "20",
                title = "Naruto",
                summary = "Ninja anime",
                genres = listOf("Action"),
                isAdult = false,
                anilistId = 20
            ),
            externalIds = ExternalIds(anilistId = 20, imdbId = "tt0388629"),
            canonicalKey = "anilist:20"
        )
        val result = enricher.applyEnrichment(series, enrichment)
        assertEquals("tt0388629", result.imdbId)
        assertEquals(20, result.anilistId)
        assertEquals("anilist:20", result.canonicalKey)
        assertEquals("Ninja anime", result.description)
        assertEquals(false, result.isAdult)
    }

    @Test
    fun applyEnrichment_keepsExistingCoverAndMergesAdultFlag() {
        val series = Series(
            id = "x",
            title = "Show",
            coverUrl = "https://cover",
            isAdult = null,
            providerId = "p1"
        )
        val enrichment = MetaEnrichment(
            show = MetaShow(id = "1", title = "Show", isAdult = true),
            externalIds = ExternalIds()
        )
        val result = enricher.applyEnrichment(series, enrichment)
        assertEquals("https://cover", result.coverUrl)
        assertTrue(result.isAdult == true)
    }

    @Test
    fun applyEnrichment_falseAdultFromMetaDoesNotOverrideUnknown() {
        val series = Series(id = "x", title = "Kids Show", isAdult = null, providerId = "p1")
        val enrichment = MetaEnrichment(
            show = MetaShow(id = "1", title = "Kids Show", isAdult = false),
            externalIds = ExternalIds()
        )
        val result = enricher.applyEnrichment(series, enrichment)
        assertFalse(result.isAdult == true)
    }
}
