package com.novastream.app.data.meta

import com.novastream.app.data.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeMetaMergerTest {

    @Test
    fun merge_emptyMeta_returnsProviderEpisodes() {
        val eps = listOf(ep(1, "Episode 1"), ep(2, "Episode 2"))
        assertEquals(eps, EpisodeMetaMerger.merge(eps, emptyList(), season = 1))
    }

    @Test
    fun merge_replacesGenericTitleWithMetaTitle() {
        val eps = listOf(ep(1, "Episode 1"), ep(2, "Episode 2"))
        val meta = listOf(
            MetaEpisode(id = "1", season = 1, number = 1, title = "Pilot"),
            MetaEpisode(id = "2", season = 1, number = 2, title = "Cat's in the Bag")
        )
        val merged = EpisodeMetaMerger.merge(eps, meta, season = 1)
        assertEquals("Pilot", merged[0].title)
        assertEquals("Cat's in the Bag", merged[1].title)
    }

    @Test
    fun merge_keepsProviderTitleWhenMeaningful() {
        val eps = listOf(ep(1, "Custom Provider Title"))
        val meta = listOf(MetaEpisode(id = "1", season = 1, number = 1, title = "Pilot"))
        val merged = EpisodeMetaMerger.merge(eps, meta, season = 1)
        assertEquals("Custom Provider Title", merged[0].title)
    }

    @Test
    fun merge_fillsThumbnailFromMeta() {
        val eps = listOf(ep(1, "Episode 1", thumbnailUrl = null))
        val meta = listOf(
            MetaEpisode(id = "1", season = 1, number = 1, title = "Pilot", imageUrl = "https://img/pilot.jpg")
        )
        val merged = EpisodeMetaMerger.merge(eps, meta, season = 1)
        assertEquals("https://img/pilot.jpg", merged[0].thumbnailUrl)
    }

    @Test
    fun merge_ignoresMetaFromOtherSeasons() {
        val eps = listOf(ep(1, "Episode 1"))
        val meta = listOf(MetaEpisode(id = "x", season = 2, number = 1, title = "Wrong Season"))
        val merged = EpisodeMetaMerger.merge(eps, meta, season = 1)
        assertEquals("Episode 1", merged[0].title)
    }

    private fun ep(number: Int, title: String, thumbnailUrl: String? = null) = Episode(
        number = number,
        title = title,
        season = 1,
        thumbnailUrl = thumbnailUrl
    )
}
