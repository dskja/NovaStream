package com.novastream.app.data.network

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
        val (deferred, isLeader) = mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                return@withLock existing to false
            }
            CompletableDeferred<Result<Any?>>().also { inFlight[key] = it } to true
        }

        if (isLeader) {
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

object GlobalRequestCoalescer {
    val instance = RequestCoalescer()
}
