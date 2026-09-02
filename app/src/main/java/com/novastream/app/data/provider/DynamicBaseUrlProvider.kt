package com.novastream.app.data.provider

import kotlinx.coroutines.sync.Mutex

/**
 * Providers whose base URL rotates (CUII blocks, domain hops).
 */
interface DynamicBaseUrlProvider {
    val defaultBaseUrl: String
    val changeUrlMutex: Mutex

    suspend fun resolveBaseUrl(forceRefresh: Boolean = false): String
}
