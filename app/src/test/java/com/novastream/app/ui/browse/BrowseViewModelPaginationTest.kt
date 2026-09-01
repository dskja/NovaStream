package com.novastream.app.ui.browse

import com.novastream.app.data.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseViewModelPaginationTest {

    private val pageOne = listOf(
        Series(id = "a", title = "Alpha"),
        Series(id = "b", title = "Beta")
    )
    private val pageTwo = listOf(
        Series(id = "b", title = "Beta Duplicate"),
        Series(id = "c", title = "Charlie")
    )

    @Test
    fun mergePagedItems_resetReplacesExisting() {
        val existing = listOf(Series(id = "old", title = "Old"))
        val merged = BrowseViewModel.mergePagedItems(existing, pageOne, reset = true)
        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun mergePagedItems_appendsDistinctById() {
        val merged = BrowseViewModel.mergePagedItems(pageOne, pageTwo, reset = false)
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("Beta", merged.first { it.id == "b" }.title)
    }

    @Test
    fun mergePagedItems_emptyNewPage_keepsExisting() {
        val merged = BrowseViewModel.mergePagedItems(pageOne, emptyList(), reset = false)
        assertEquals(2, merged.size)
    }

    @Test
    fun computeHasMore_trueWhenItemsAndPaginationSupported() {
        assertTrue(BrowseViewModel.computeHasMore(pageOne, supportsPagination = true))
    }

    @Test
    fun computeHasMore_falseWhenEmptyPage() {
        assertFalse(BrowseViewModel.computeHasMore(emptyList(), supportsPagination = true))
    }

    @Test
    fun computeHasMore_falseWhenPaginationUnsupported() {
        assertFalse(BrowseViewModel.computeHasMore(pageOne, supportsPagination = false))
    }
}
