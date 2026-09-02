package com.novastream.app.data.meta

import com.novastream.app.data.model.Episode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AniListEpisodeParseTest {

    @Test
    fun parseEpisodeNode_extractsFields() {
        val obj = JSONObject(
            """
            {
              "episode": 3,
              "title": { "romaji": "Test Ep", "english": "Test Episode" },
              "thumbnail": "https://example.com/thumb.jpg",
              "duration": 24,
              "airingAt": 1254355200
            }
            """.trimIndent()
        )
        val ep = AniListMetaService.parseEpisodeNode(obj)
        assertNotNull(ep)
        assertEquals(3, ep!!.number)
        assertEquals("Test Episode", ep.title)
        assertEquals(24, ep.runtime)
        assertEquals("https://example.com/thumb.jpg", ep.imageUrl)
    }
}

class EpisodeMetaMergerOffsetTest {

    @Test
    fun merge_usesEpisodeNumberOffsetForAnimeSeasons() {
        val eps = listOf(ep(1, "Episode 1"))
        val meta = listOf(
            MetaEpisode(id = "13", season = 1, number = 13, title = "Global Ep 13")
        )
        val merged = EpisodeMetaMerger.merge(eps, meta, season = 2, episodeNumberOffset = 12)
        assertEquals("Global Ep 13", merged[0].title)
    }

    private fun ep(number: Int, title: String) = Episode(number = number, title = title, season = 2)
}
