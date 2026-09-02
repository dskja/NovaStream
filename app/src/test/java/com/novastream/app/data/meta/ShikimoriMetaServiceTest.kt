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
class ShikimoriMetaServiceTest {

    @Test
    fun parseAnime_extractsMalId() {
        val obj = JSONObject(
            """
            {
              "id": 21,
              "name": "One Piece",
              "score": "8.73",
              "status": "released",
              "episodes": 1000,
              "kind": "tv",
              "image": { "original": "/system/animes/original/21.jpg" }
            }
            """.trimIndent()
        )
        val show = ShikimoriMetaService.parseAnime(obj)
        assertNotNull(show)
        assertEquals(21, show!!.idMal)
        assertEquals(21, show.shikimoriId)
        assertEquals("One Piece", show.title)
    }
}
