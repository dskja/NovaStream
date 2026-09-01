package com.novastream.app.data.meta

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WikidataMetaServiceTest {

    @Test
    fun parseSearchHit_extractsEntity() {
        val hit = JSONObject(
            """
            { "id": "Q1079", "label": "Breaking Bad", "description": "American TV series" }
            """.trimIndent()
        )
        val entity = WikidataMetaService.parseSearchHit(hit)
        assertNotNull(entity)
        assertEquals("Q1079", entity!!.id)
        assertEquals("Breaking Bad", entity.label)
    }

    @Test
    fun parseExternalIdsFromEntity_readsImdbAndTmdb() {
        val entity = JSONObject(
            """
            {
              "claims": {
                "P345": [{ "mainsnak": { "datavalue": { "value": "tt0903747" } } }],
                "P4983": [{ "mainsnak": { "datavalue": { "value": "1396" } } }]
              }
            }
            """.trimIndent()
        )
        val ids = WikidataMetaService.parseExternalIdsFromEntity("Q1079", entity)
        assertEquals("tt0903747", ids.imdbId)
        assertEquals(1396, ids.tmdbId)
        assertEquals("imdb:tt0903747", ids.canonicalKey())
    }

    @Test
    fun canonicalKey_prefersImdb() {
        val ids = ExternalIds(imdbId = "tt123", tvmazeId = "99", anilistId = 5)
        assertEquals("imdb:tt123", ids.canonicalKey())
    }
}
