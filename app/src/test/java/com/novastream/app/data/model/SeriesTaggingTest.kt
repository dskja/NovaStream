package com.novastream.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesTaggingTest {

    @Test
    fun tagAll_setsProviderIdOnAllSeries() {
        val list = listOf(
            Series(id = "a", title = "A", providerId = ""),
            Series(id = "b", title = "B", providerId = "aniworld")
        )
        val tagged = list.map { s ->
            if (s.providerId == "serienstream") s else s.copy(providerId = "serienstream")
        }
        assertEquals("serienstream", tagged[0].providerId)
        assertEquals("aniworld", tagged[1].providerId)
    }
}
