package com.novastream.app.data.iptv

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class XmlTvEpgParserTest {

    @Test
    fun `parses programme entry`() {
        val xml = """
            <?xml version="1.0"?>
            <tv>
              <programme start="20260101120000 +0000" stop="20260101130000 +0000" channel="ch1">
                <title>News Hour</title>
                <desc>Evening news</desc>
              </programme>
            </tv>
        """.trimIndent()
        val programs = XmlTvEpgParser.parse(xml)
        assertEquals(1, programs.size)
        assertEquals("News Hour", programs[0].title)
        assertEquals("ch1", programs[0].channelId)
    }
}
