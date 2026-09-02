package com.novastream.app.data.meta

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JikanMetaServiceTest {

    @Test
    fun parseAnime_extractsMalIdAndRating() {
        val obj = JSONObject(
            """
            {
              "mal_id": 5114,
              "title": "Fullmetal Alchemist: Brotherhood",
              "score": 9.1,
              "synopsis": "Brothers seek the stone",
              "status": "Finished Airing",
              "rating": "R - 17+ (violence & profanity)",
              "episodes": 64,
              "aired": { "from": "2009-04-05T00:00:00+00:00" },
              "images": { "jpg": { "large_image_url": "https://example.com/poster.jpg" } },
              "genres": [{ "name": "Action" }]
            }
            """.trimIndent()
        )
        val show = JikanMetaService.parseAnime(obj, withGenres = true)
        assertNotNull(show)
        assertEquals(5114, show!!.idMal)
        assertEquals(true, show.isAdult)
        assertTrue(show.genres.contains("Action"))
    }

    @Test
    fun isAdultFromRating_detectsRx() {
        assertEquals(true, JikanMetaService.isAdultFromRating("Rx - Hentai"))
    }
}
