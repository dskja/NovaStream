package com.novastream.app.ui.home

import com.novastream.app.util.ProviderLoadMetrics
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests home load health tracking used by [HomeViewModel] via [ProviderLoadMetrics].
 */
class HomeViewModelLoadTest {

    @After
    fun tearDown() {
        ProviderLoadMetrics.reset()
    }

    @Test
    fun slowLoad_triggersHealthWarning() {
        ProviderLoadMetrics.recordLoad("serienstream", 6200)
        assertTrue(ProviderLoadMetrics.shouldShowHealthWarning(6200, hasError = false))
    }

    @Test
    fun fastSuccessfulLoad_noHealthWarning() {
        ProviderLoadMetrics.recordLoad("serienstream", 1800)
        assertFalse(ProviderLoadMetrics.shouldShowHealthWarning(1800, hasError = false))
    }

    @Test
    fun errorLoad_triggersHealthWarningRegardlessOfDuration() {
        assertTrue(ProviderLoadMetrics.shouldShowHealthWarning(50, hasError = true))
    }

    @Test
    fun threshold_isFiveSeconds() {
        assertFalse(ProviderLoadMetrics.shouldShowHealthWarning(5000, hasError = false))
        assertTrue(ProviderLoadMetrics.shouldShowHealthWarning(5001, hasError = false))
    }

    @Test
    fun multipleLoads_updateAverageForSettingsMatrix() {
        ProviderLoadMetrics.recordLoad("megakino", 2000)
        ProviderLoadMetrics.recordLoad("megakino", 4000)
        ProviderLoadMetrics.recordLoad("megakino", 6000)
        assertTrue((ProviderLoadMetrics.averageLoadMs("megakino") ?: 0) >= 4000)
    }
}
