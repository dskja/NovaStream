package com.novastream.app.data.network

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Limits concurrent provider scrape calls to avoid overwhelming hosts. */
object ScrapeLimiter {

    private val semaphore = Semaphore(permits = 4)

    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }
}
