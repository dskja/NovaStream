package com.novastream.app.util

import com.novastream.app.data.model.Series
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
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

    @Test
    fun firstSuccessful_returnsFirstNonEmptyProbe() = runTest {
        val slow = async {
            delay(100)
            listOf(Series(id = "slow", title = "Slow"))
        }
        val fast = async {
            listOf(Series(id = "fast", title = "Fast"))
        }

        val result = AjaxSearchClient.firstSuccessful(
            listOf(
                { slow.await() },
                { fast.await() }
            )
        )

        assertEquals("fast", result?.first()?.id)
    }

    @Test
    fun firstSuccessful_returnsNullWhenAllEmpty() = runTest {
        val result = AjaxSearchClient.firstSuccessful(
            listOf(
                { emptyList() },
                { emptyList() }
            )
        )
        assertNull(result)
    }
}

