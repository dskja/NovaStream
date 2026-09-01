package com.novastream.app.util

import java.util.concurrent.ConcurrentHashMap

/** Tracks per-provider home load durations for Settings matrix and health banner. */
object ProviderLoadMetrics {

    private const val MAX_SAMPLES = 8
    private const val SLOW_LOAD_MS = 5_000L

    private val samples = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun recordLoad(providerId: String, durationMs: Long) {
        if (providerId.isBlank() || durationMs < 0) return
        samples.compute(providerId) { _, deque ->
            val next = deque ?: ArrayDeque()
            next.addLast(durationMs)
            while (next.size > MAX_SAMPLES) next.removeFirst()
            next
        }
    }

    fun averageLoadMs(providerId: String): Long? {
        val deque = samples[providerId]?.takeIf { it.isNotEmpty() } ?: return null
        return deque.average().toLong()
    }

    fun snapshotAverages(): Map<String, Long> =
        samples.mapNotNull { (id, deque) ->
            if (deque.isEmpty()) null else id to deque.average().toLong()
        }.toMap()

    fun shouldShowHealthWarning(durationMs: Long?, hasError: Boolean): Boolean =
        hasError || (durationMs != null && durationMs > SLOW_LOAD_MS)

    fun reset() {
        samples.clear()
    }
}
