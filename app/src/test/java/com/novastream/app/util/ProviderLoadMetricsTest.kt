package com.novastream.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLoadMetricsTest {

    @After
    fun tearDown() {
        ProviderLoadMetrics.reset()
    }

    @Test
    fun recordLoad_computesAverage() {
        ProviderLoadMetrics.recordLoad("serienstream", 2000)
        ProviderLoadMetrics.recordLoad("serienstream", 4000)
        assertEquals(3000L, ProviderLoadMetrics.averageLoadMs("serienstream"))
    }

    @Test
    fun averageLoadMs_returnsNullWhenUnknown() {
        assertNull(ProviderLoadMetrics.averageLoadMs("unknown"))
    }

    @Test
    fun shouldShowHealthWarning_whenSlowLoad() {
        assertTrue(ProviderLoadMetrics.shouldShowHealthWarning(6000L, false))
    }

    @Test
    fun shouldShowHealthWarning_whenError() {
        assertTrue(ProviderLoadMetrics.shouldShowHealthWarning(100L, true))
    }

    @Test
    fun shouldShowHealthWarning_falseForFastLoad() {
        assertFalse(ProviderLoadMetrics.shouldShowHealthWarning(1200L, false))
    }

    @Test
    fun snapshotAverages_includesRecordedProviders() {
        ProviderLoadMetrics.recordLoad("aniworld", 1000)
        ProviderLoadMetrics.recordLoad("megakino", 3000)
        val snapshot = ProviderLoadMetrics.snapshotAverages()
        assertEquals(1000L, snapshot["aniworld"])
        assertEquals(3000L, snapshot["megakino"])
    }

    @Test
    fun recordLoad_ignoresBlankProviderId() {
        ProviderLoadMetrics.recordLoad("", 5000)
        assertNull(ProviderLoadMetrics.averageLoadMs(""))
    }
}
