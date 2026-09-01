package com.novastream.app.ui.browse

import com.novastream.app.data.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseViewModelTest {

    private val catalog = listOf(
        Series(id = "movie-1", title = "Inception", isMovie = true),
        Series(id = "series-1", title = "Dark", isMovie = false),
        Series(id = "series-2", title = "Office", isMovie = false)
    )

    @Test
    fun applyBrowseContentFilter_all_returnsFullCatalog() {
        assertEquals(3, applyBrowseContentFilter(catalog, BrowseContentFilter.ALL).size)
    }

    @Test
    fun applyBrowseContentFilter_movies_returnsMoviesOnly() {
        val filtered = applyBrowseContentFilter(catalog, BrowseContentFilter.MOVIES)
        assertEquals(1, filtered.size)
        assertEquals("movie-1", filtered.first().id)
    }

    @Test
    fun applyBrowseContentFilter_series_excludesMovies() {
        val filtered = applyBrowseContentFilter(catalog, BrowseContentFilter.SERIES)
        assertEquals(2, filtered.size)
        assertTrue(filtered.none { it.isMovie })
    }
}
