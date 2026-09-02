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
class KitsuMetaServiceTest {

    @Test
    fun parseAnime_extractsFields() {
        val node = JSONObject(
            """
            {
              "id": "1555",
              "type": "anime",
              "attributes": {
                "canonicalTitle": "Naruto: Shippuden",
                "synopsis": "Ninja adventures",
                "averageRating": "84.06",
                "startDate": "2007-02-15",
                "status": "finished",
                "ageRating": "PG-13",
                "episodeCount": 500,
                "posterImage": { "large": "https://example.com/poster.jpg" },
                "coverImage": { "large": "https://example.com/cover.jpg" }
              }
            }
            """.trimIndent()
        )
        val show = KitsuMetaService.parseAnime(node)
        assertNotNull(show)
        assertEquals("kitsu-1555", show!!.id)
        assertEquals(1555, show.kitsuId)
        assertEquals("Naruto: Shippuden", show.title)
        assertEquals("https://example.com/poster.jpg", show.posterUrl)
    }

    @Test
    fun isAdultFromAgeRating_detectsR18() {
        assertEquals(true, KitsuMetaService.isAdultFromAgeRating("R18"))
        assertEquals(false, KitsuMetaService.isAdultFromAgeRating("PG"))
    }
}
