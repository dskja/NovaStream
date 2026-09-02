package com.novastream.app.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks provider failures and applies short cooldowns after repeated errors.
 */
object ProviderHealthMonitor {

    private const val FAILURE_THRESHOLD = 5
    private const val COOLDOWN_MS = 5 * 60 * 1000L

    private data class HealthState(var failures: Int = 0, var cooldownUntil: Long = 0L)

    private val states = ConcurrentHashMap<String, HealthState>()

    fun recordSuccess(providerId: String) {
        states.remove(providerId)
    }

    fun recordEmptyResult(providerId: String) {
        val state = states.getOrPut(providerId) { HealthState() }
        state.failures++
        if (state.failures >= 3) {
            state.cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
        }
    }

    fun recordFailure(providerId: String) {
        val state = states.getOrPut(providerId) { HealthState() }
        state.failures++
        if (state.failures >= FAILURE_THRESHOLD) {
            state.cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
        }
    }

    fun isHealthy(providerId: String): Boolean {
        val state = states[providerId] ?: return true
        if (state.cooldownUntil <= System.currentTimeMillis()) {
            if (state.failures >= FAILURE_THRESHOLD) {
                state.failures = 0
                state.cooldownUntil = 0L
            }
            return true
        }
        return false
    }

    fun isInCooldown(providerId: String): Boolean = !isHealthy(providerId)

    fun healthLabel(providerId: String): String? =
        if (isHealthy(providerId)) null else "unhealthy"
}
