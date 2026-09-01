package com.novastream.app.data.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class M3uParserTest {

    @Test
    fun `parses basic m3u playlist`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="http://logo" group-title="News",Test Channel
            http://example.com/stream.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(m3u)
        assertEquals(1, channels.size)
        assertEquals("Test Channel", channels[0].name)
        assertEquals("News", channels[0].group)
        assertTrue(channels[0].streamUrl.contains("example.com"))
    }
}
