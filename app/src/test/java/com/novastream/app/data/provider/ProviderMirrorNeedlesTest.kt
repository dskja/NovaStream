package com.novastream.app.data.provider

import com.novastream.app.data.scraper.InternationalSiteProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMirrorNeedlesTest {

    @Test
    fun `explicit needles for major DE providers`() {
        assertEquals("/serie/", ProviderMirrorNeedles.needleFor("burningseries"))
        assertEquals("/anime/stream/", ProviderMirrorNeedles.needleFor("aniworld"))
        assertEquals("/title/", ProviderMirrorNeedles.needleFor("megakino"))
        assertEquals("/stream/", ProviderMirrorNeedles.needleFor("kinoger"))
    }

    @Test
    fun `derives needle from site profile selector`() {
        val needle = ProviderMirrorNeedles.needleFor(
            "custom",
            InternationalSiteProfiles.sflix
        )
        assertEquals("/tv-show/", needle)
    }

    @Test
    fun `hasMirrors reflects domain manager`() {
        assertTrue(ProviderMirrorNeedles.hasMirrors("dramacool"))
        assertTrue(ProviderMirrorNeedles.hasMirrors("hianime"))
        assertFalse(ProviderMirrorNeedles.hasMirrors("nonexistent_provider_xyz"))
    }
}
