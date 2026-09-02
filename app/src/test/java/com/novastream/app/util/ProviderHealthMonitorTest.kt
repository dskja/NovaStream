package com.novastream.app.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHealthMonitorTest {

    private val providerId = "test-provider"

    @After
    fun tearDown() {
        ProviderHealthMonitor.recordSuccess(providerId)
    }

    @Test
    fun recordSuccess_clearsCooldown() {
        repeat(5) { ProviderHealthMonitor.recordFailure(providerId) }
        assertTrue(ProviderHealthMonitor.isInCooldown(providerId))

        ProviderHealthMonitor.recordSuccess(providerId)

        assertTrue(ProviderHealthMonitor.isHealthy(providerId))
        assertNull(ProviderHealthMonitor.healthLabel(providerId))
    }

    @Test
    fun recordEmptyResult_triggersCooldownAfterThree() {
        repeat(2) { ProviderHealthMonitor.recordEmptyResult(providerId) }
        assertFalse(ProviderHealthMonitor.isInCooldown(providerId))

        ProviderHealthMonitor.recordEmptyResult(providerId)
        assertTrue(ProviderHealthMonitor.isInCooldown(providerId))
    }

    @Test
    fun recordFailure_triggersCooldownAfterFive() {
        repeat(4) { ProviderHealthMonitor.recordFailure(providerId) }
        assertFalse(ProviderHealthMonitor.isInCooldown(providerId))

        ProviderHealthMonitor.recordFailure(providerId)
        assertTrue(ProviderHealthMonitor.isInCooldown(providerId))
    }
}
