package com.novastream.app.data.meta

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrailerMetaServiceTest {

    @Test
    fun parseAniListTrailer_youtube() {
        val trailer = JSONObject("""{"site":"youtube","id":"abc123"}""")
        assertEquals("https://www.youtube.com/watch?v=abc123", TrailerMetaService.parseAniListTrailer(trailer))
    }

    @Test
    fun parseAniListTrailer_dailymotion() {
        val trailer = JSONObject("""{"site":"dailymotion","id":"x7abc"}""")
        assertEquals("https://www.dailymotion.com/video/x7abc", TrailerMetaService.parseAniListTrailer(trailer))
    }

    @Test
    fun parseJikanTrailer_youtubeId() {
        val trailer = JSONObject("""{"youtube_id":"xyz789"}""")
        assertEquals("https://www.youtube.com/watch?v=xyz789", TrailerMetaService.parseJikanTrailer(trailer))
    }

    @Test
    fun parseJikanTrailer_directUrl() {
        val trailer = JSONObject("""{"youtube_id":"","url":"https://www.youtube.com/watch?v=direct"}""")
        assertEquals("https://www.youtube.com/watch?v=direct", TrailerMetaService.parseJikanTrailer(trailer))
    }

    @Test
    fun isDirectPlayable_recognizesYoutube() {
        assertTrue(TrailerMetaService.isDirectPlayable("https://youtu.be/abc"))
    }

    @Test
    fun parseAniListTrailer_nullReturnsNull() {
        assertNull(TrailerMetaService.parseAniListTrailer(null))
    }
}
