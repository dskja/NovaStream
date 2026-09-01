package com.novastream.app.data.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCapabilitiesTest {

    @Test
    fun serienstream_supportsPaginationAndLatest() {
        val caps = SerienStreamProvider().capabilities()
        assertTrue(caps.supportsPagination)
        assertTrue(caps.supportsLatestEpisodes)
        assertTrue(caps.supportsGenrePagination)
    }

    @Test
    fun streamkiste_supportsMoviesAndPagination() {
        val caps = StreamKisteProvider().capabilities()
        assertTrue(caps.supportsPagination)
        assertTrue(caps.supportsLatestEpisodes)
        assertFalse(caps.supportsGenrePagination)
    }

    @Test
    fun cinezo_supportsPaginationButNotLatest() {
        val caps = CinezoProvider().capabilities()
        assertTrue(caps.supportsPagination)
        assertFalse(caps.supportsLatestEpisodes)
    }

    @Test
    fun aniworld_supportsPaginationWithoutGenrePagination() {
        val caps = AniWorldProvider().capabilities()
        assertTrue(caps.supportsPagination)
        assertTrue(caps.supportsLatestEpisodes)
        assertFalse(caps.supportsGenrePagination)
    }
}
