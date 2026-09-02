package com.novastream.app.data.provider

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared mirror resolution for providers with rotating domains.
 * Probes [ProviderDomainManager] mirrors, persists working base URLs, and
 * supports in-memory cache invalidation on provider switch.
 */
object ProviderDomainResolver {

    private val invalidators = ConcurrentHashMap<String, CopyOnWriteArrayList<() -> Unit>>()

    fun registerInvalidator(providerId: String, invalidator: () -> Unit) {
        invalidators.getOrPut(providerId) { CopyOnWriteArrayList() }.add(invalidator)
    }

    fun invalidate(providerId: String) {
        invalidators[providerId]?.forEach { it.invoke() }
    }

    fun invalidateAll() {
        invalidators.values.forEach { list -> list.forEach { it.invoke() } }
    }

    /**
     * Resolves the first working base URL for [providerId].
     * When [appContext] is set, stores the result in DataStore and reuses it as a probe candidate.
     */
    suspend fun resolveActiveBaseUrl(
        providerId: String,
        defaultBaseUrl: String,
        contentNeedle: String,
        appContext: Context? = null,
        forceRefresh: Boolean = false,
        webViewFallback: Boolean = true
    ): String {
        val mirrors = ProviderDomainManager.alternateDomains(providerId).ifEmpty { listOf(defaultBaseUrl) }
        val stored = if (appContext != null && !forceRefresh) {
            ProviderDomainManager.getResolvedBaseUrl(appContext, providerId, defaultBaseUrl)
        } else {
            null
        }

        val candidates = buildList {
            if (!forceRefresh && stored != null) add(stored)
            addAll(mirrors)
            if (isEmpty()) add(defaultBaseUrl)
        }.map { it.trimEnd('/') }.distinct()

        val resolved = ProviderHttp.resolveWorkingBase(
            candidates,
            contentNeedle = contentNeedle,
            webViewFallback = webViewFallback
        )
        val trimmed = (resolved ?: stored ?: defaultBaseUrl).trimEnd('/')
        if (appContext != null && resolved != null && trimmed != stored?.trimEnd('/')) {
            ProviderDomainManager.setResolvedBaseUrl(appContext, providerId, trimmed)
        }
        return trimmed
    }
}
