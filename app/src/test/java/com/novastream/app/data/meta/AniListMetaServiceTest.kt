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
class AniListMetaServiceTest {

    @Test
    fun parseMedia_extractsTitleAndIds() {
        val json = JSONObject(
            """
            {
              "id": 21,
              "title": { "romaji": "ONE PIECE", "english": "One Piece" },
              "description": "A pirate adventure.",
              "coverImage": { "large": "https://img.example/poster.jpg" },
              "bannerImage": "https://img.example/banner.jpg",
              "averageScore": 89,
              "startDate": { "year": 1999 },
              "genres": ["Action", "Adventure"],
              "status": "RELEASING",
              "characters": {
                "edges": [
                  {
                    "role": "MAIN",
                    "node": {
                      "name": { "full": "Monkey D. Luffy" },
                      "image": { "medium": "https://img.example/luffy.jpg" }
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
        val show = AniListMetaService.parseMedia(json)
        assertNotNull(show)
        assertEquals("anilist-21", show!!.id)
        assertEquals("One Piece", show.title)
        assertEquals(21, show.anilistId)
        assertEquals("anime", show.mediaType)
        assertEquals(1, show.cast.size)
        assertEquals("Monkey D. Luffy", show.cast.first().name)
    }

    @Test
    fun parseRelations_mapsRelatedMedia() {
        val json = JSONObject(
            """
            {
              "id": 1,
              "title": { "romaji": "Test" },
              "relations": {
                "edges": [
                  {
                    "relationType": "SEQUEL",
                    "node": {
                      "id": 2,
                      "title": { "romaji": "Test 2" },
                      "genres": ["Drama"]
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
        val show = AniListMetaService.parseMedia(json, withRelations = true)
        assertNotNull(show)
        assertEquals(1, show!!.similar.size)
        assertEquals("anilist-2", show.similar.first().id)
    }

    @Test
    fun parseMedia_setsIsAdultForHentaiGenre() {
        val json = JSONObject(
            """
            {
              "id": 99,
              "title": { "romaji": "Test" },
              "genres": ["Hentai"],
              "isAdult": false
            }
            """.trimIndent()
        )
        val show = AniListMetaService.parseMedia(json)
        assertNotNull(show)
        assertEquals(true, show!!.isAdult)
    }
}
