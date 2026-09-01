package com.novastream.app.data.network

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Deduplicates in-flight network requests so parallel callers share one execution.
 */
object RequestCoalescer {

    private val inFlight = ConcurrentHashMap<String, Deferred<*>>()

    suspend fun <T> coalesce(key: String, block: suspend () -> T): T = coroutineScope {
        val newDeferred = async {
            try {
                block()
            } finally {
                inFlight.remove(key)
            }
        }
        @Suppress("UNCHECKED_CAST")
        val deferred = (inFlight.putIfAbsent(key, newDeferred) ?: newDeferred) as Deferred<T>
        if (deferred !== newDeferred) {
            newDeferred.cancel()
        }
        deferred.await()
    }
}
