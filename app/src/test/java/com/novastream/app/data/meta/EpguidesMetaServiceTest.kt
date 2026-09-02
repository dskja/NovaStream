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
class EpguidesMetaServiceTest {

    @Test
    fun parseShow_extractsKey() {
        val obj = JSONObject(
            """
            {
              "epguides_key": "BreakingBad",
              "title": "Breaking Bad",
              "network": "AMC",
              "start_date": "2008-01-01"
            }
            """.trimIndent()
        )
        val show = EpguidesMetaService.parseShow(obj)
        assertNotNull(show)
        assertEquals("BreakingBad", show!!.epguidesKey)
        assertEquals("Breaking Bad", show.title)
    }

    @Test
    fun parseEpisode_extractsFields() {
        val obj = JSONObject(
            """
            {
              "season": 1,
              "episode_number": 1,
              "title": "Pilot",
              "release_date": "2008-01-20",
              "summary": "Pilot episode"
            }
            """.trimIndent()
        )
        val ep = EpguidesMetaService.parseEpisode(obj)
        assertNotNull(ep)
        assertEquals(1, ep!!.season)
        assertEquals(1, ep.number)
        assertEquals("Pilot", ep.title)
    }

    @Test
    fun guessKeyFromTitle_pascalCase() {
        assertEquals("BreakingBad", EpguidesMetaService.guessKeyFromTitle("Breaking Bad"))
    }
}
