package com.novastream.app.download

import com.novastream.app.data.db.DownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadEntityTest {

    @Test
    fun key_includesProfileProviderSlugSeasonEpisode() {
        val key = DownloadEntity.key("default", "serienstream", "dark", 1, 3)
        assertEquals("default|serienstream|dark|S1|E3", key)
    }
}
