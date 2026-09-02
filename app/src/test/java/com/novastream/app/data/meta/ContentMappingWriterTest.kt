package com.novastream.app.data.meta

import com.novastream.app.data.model.Series
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JikanEpisodeParseTest {

    @Test
    fun parseEpisode_extractsTitleAndNumber() {
        val obj = JSONObject(
            """
            {
              "mal_id": 8917,
              "title": "You Want Flavors With That?",
              "aired": "2003-10-04",
              "filler": false,
              "recap": false
            }
            """.trimIndent()
        )
        val ep = JikanMetaService.parseEpisode(obj, number = 1)
        assertNotNull(ep)
        assertEquals(1, ep!!.number)
        assertEquals("You Want Flavors With That?", ep.title)
        assertEquals("2003-10-04", ep.airdate)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContentMappingWriterTest {

    @Test
    fun entityFor_prefersImdbCanonicalKey() {
        val series = Series(id = "slug", title = "Test", providerId = "aniworld")
        val enrichment = MetaEnrichment(
            show = MetaShow(id = "1", title = "Test"),
            externalIds = ExternalIds(imdbId = "tt1234567", anilistId = 99),
            canonicalKey = "imdb:tt1234567"
        )
        val entity = ContentMappingWriter.entityFor(series, enrichment)
        assertNotNull(entity)
        assertEquals("imdb:tt1234567", entity!!.canonicalKey)
        assertEquals("slug", entity.slug)
    }
}
