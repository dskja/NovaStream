package com.novastream.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AjaxSearchClientTest {

    @Test
    fun extractSlug_parsesSerienStreamPaths() {
        assertEquals("test-serie", AjaxSearchClient.extractSlugForTest("/serie/test-serie", false))
        assertEquals("zweite", AjaxSearchClient.extractSlugForTest("/serie/zweite", false))
    }

    @Test
    fun extractSlug_parsesAniWorldPaths() {
        assertEquals("naruto", AjaxSearchClient.extractSlugForTest("/anime/stream/naruto", true))
    }

    @Test
    fun extractSlug_returnsNullForUnrelatedPaths() {
        assertNull(AjaxSearchClient.extractSlugForTest("/about", false))
    }
}
