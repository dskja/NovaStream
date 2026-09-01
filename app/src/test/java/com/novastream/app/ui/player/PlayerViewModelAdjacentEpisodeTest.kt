package com.novastream.app.ui.player

import com.novastream.app.data.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerViewModelAdjacentEpisodeTest {

    private val episodes = listOf(
        Episode(number = 1, title = "Pilot", slug = "show", season = 1, episodeUrl = "/e1"),
        Episode(number = 2, title = "Second", slug = "show", season = 1, episodeUrl = "/e2"),
        Episode(number = 3, title = "Third", slug = "show", season = 1, episodeUrl = "/e3")
    )

    @Test
    fun resolveNextEpisode_returnsFollowingEpisode() {
        val next = PlayerViewModel.resolveNextEpisode(episodes, currentSeason = 1, currentEpisode = 2, coverUrl = null)
        assertEquals(3, next?.episode)
        assertEquals("Third", next?.title)
    }

    @Test
    fun resolveNextEpisode_nullForSeasonFinale() {
        assertNull(PlayerViewModel.resolveNextEpisode(episodes, 1, 3, null))
    }

    @Test
    fun resolvePreviousEpisode_returnsPriorEpisode() {
        val prev = PlayerViewModel.resolvePreviousEpisode(episodes, 1, 2, null)
        assertEquals(1, prev?.episode)
        assertEquals("Pilot", prev?.title)
    }

    @Test
    fun resolvePreviousEpisode_nullForFirstEpisode() {
        assertNull(PlayerViewModel.resolvePreviousEpisode(episodes, 1, 1, null))
    }
}
