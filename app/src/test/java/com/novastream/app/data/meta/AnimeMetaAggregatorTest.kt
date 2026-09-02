package com.novastream.app.data.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeMetaAggregatorTest {

    @Test
    fun mergeShows_prefersPrimaryCover() {
        val primary = MetaShow(id = "a", title = "Naruto", posterUrl = "https://a.jpg")
        val secondary = MetaShow(id = "b", title = "Naruto", summary = "Ninja", idMal = 20)
        val merged = AnimeMetaAggregator.mergeShows(primary, secondary)
        assertEquals("https://a.jpg", merged.posterUrl)
        assertEquals("Ninja", merged.summary)
        assertEquals(20, merged.idMal)
    }

    @Test
    fun mergeShows_mergesCastTrailerAndRuntime() {
        val primary = MetaShow(
            id = "a",
            title = "Naruto",
            posterUrl = "https://a.jpg",
            cast = listOf(MetaPerson("Sakura", "Sakura"))
        )
        val secondary = MetaShow(
            id = "b",
            title = "Naruto",
            summary = "Ninja",
            idMal = 20,
            trailerUrl = "https://www.youtube.com/watch?v=trailer",
            runtime = 24,
            network = "TV Tokyo",
            cast = listOf(MetaPerson("Naruto", "Naruto"))
        )
        val merged = AnimeMetaAggregator.mergeShows(primary, secondary)
        assertEquals("https://a.jpg", merged.posterUrl)
        assertEquals("Ninja", merged.summary)
        assertEquals(20, merged.idMal)
        assertEquals("https://www.youtube.com/watch?v=trailer", merged.trailerUrl)
        assertEquals(24, merged.runtime)
        assertEquals("TV Tokyo", merged.network)
        assertEquals(2, merged.cast.size)
    }

    @Test
    fun dedupeKey_usesMalId() {
        val show = MetaShow(id = "x", title = "Test", idMal = 99)
        assertEquals("mal:99", AnimeMetaAggregator.dedupeKey(show))
    }
}
