package com.novastream.app.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deduplicates concurrent suspend calls with the same key so only one network
 * request runs while others await the same result.
 */
class RequestCoalescer {

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Result<Any?>>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> coalesce(key: String, block: suspend () -> T): T {
        val deferred = mutex.withLock {
            inFlight[key]?.let { return@withLock it as CompletableDeferred<Result<Any?>> }
            CompletableDeferred<Result<Any?>>().also { inFlight[key] = it }
        }

        if (!deferred.isCompleted) {
            val result = runCatching { block() as Any? }
            mutex.withLock {
                deferred.complete(result)
                inFlight.remove(key)
            }
        }

        return deferred.await().getOrThrow() as T
    }

    fun clear() {
        inFlight.clear()
    }
}
