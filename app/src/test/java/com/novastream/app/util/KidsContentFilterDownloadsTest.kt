package com.novastream.app.util

import com.novastream.app.data.db.DownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class KidsContentFilterDownloadsTest {

    @Test
    fun filterDownloads_blocksAdultSlugInKidsMode() {
        val items = listOf(
            download("safe-show", "Bluey"),
            download("horror-night", "Horror Night")
        )
        val filtered = KidsContentFilter.filterDownloads(items, kidsMode = true)
        assertEquals(1, filtered.size)
        assertEquals("safe-show", filtered.first().slug)
    }

    @Test
    fun filterDownloads_allowsAllWhenNotKids() {
        val items = listOf(download("horror-night", "Horror Night"))
        assertEquals(1, KidsContentFilter.filterDownloads(items, kidsMode = false).size)
    }

    private fun download(slug: String, title: String) = DownloadEntity(
        downloadId = "d-$slug",
        providerId = "p1",
        slug = slug,
        title = title,
        streamUrl = "https://example.com/stream.m3u8"
    )
}
