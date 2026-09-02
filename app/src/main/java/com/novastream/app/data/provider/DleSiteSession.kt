package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.api.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * DLE CMS session helper (MegaKino and similar sites).
 * These portals gate HTML behind `/index.php?yg=token` and require cookies before catalog pages load.
 */
object DleSiteSession {

    private const val TOKEN_TTL_MS = 10L * 60 * 1000
    private const val TOKEN_PATH = "/index.php?yg=token"

    private val tokenMutex = Mutex()
    private val lastTokenAt = ConcurrentHashMap<String, Long>()
    private val resolvedBaseBySeed = ConcurrentHashMap<String, String>()

    suspend fun resolveActiveBase(
        providerId: String,
        defaultBaseUrl: String,
        appContext: Context?,
        contentNeedle: String = "/films/",
        forceRefresh: Boolean = false
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

        for (candidate in candidates) {
            val base = ensureToken(candidate, force = forceRefresh)
            val html = fetchDirect("$base/", referer = "$base/", providerId = providerId)
            if (html.isNotBlank() && !ProviderHttp.isChallenge(html)) {
                if (contentNeedle.isBlank() || html.contains(contentNeedle, ignoreCase = true)) {
                    if (appContext != null) {
                        ProviderDomainManager.setResolvedBaseUrl(appContext, providerId, base)
                    }
                    return base
                }
            }
        }
        return stored?.trimEnd('/') ?: ensureToken(defaultBaseUrl.trimEnd('/'))
    }

    suspend fun ensureToken(seedBase: String, force: Boolean = false): String {
        val seed = seedBase.trimEnd('/')
        if (!force) {
            val cached = resolvedBaseBySeed[seed]
            val host = cached?.toHttpUrlOrNull()?.host
            if (cached != null && host != null &&
                System.currentTimeMillis() - (lastTokenAt[host] ?: 0) < TOKEN_TTL_MS
            ) {
                return cached
            }
        }
        return tokenMutex.withLock {
            if (!force) {
                val cached = resolvedBaseBySeed[seed]
                val host = cached?.toHttpUrlOrNull()?.host
                if (cached != null && host != null &&
                    System.currentTimeMillis() - (lastTokenAt[host] ?: 0) < TOKEN_TTL_MS
                ) {
                    return@withLock cached
                }
            }
            val effectiveBase = requestToken("$seed$TOKEN_PATH", seed)
            resolvedBaseBySeed[seed] = effectiveBase
            effectiveBase.toHttpUrlOrNull()?.host?.let { lastTokenAt[it] = System.currentTimeMillis() }
            effectiveBase
        }
    }

    suspend fun fetch(
        urlOrPath: String,
        seedBase: String,
        referer: String? = null,
        providerId: String? = null,
        webViewFallback: Boolean = false
    ): String {
        val base = ensureToken(seedBase)
        val url = absolutize(base, urlOrPath)
        val ref = referer ?: "$base/"
        var html = ProviderHttp.fetch(url, referer = ref, webViewFallback = false, providerId = providerId)
        if (html.isBlank() || ProviderHttp.isChallenge(html) || looksLikeTokenGate(html)) {
            ensureToken(seedBase, force = true)
            html = ProviderHttp.fetch(url, referer = ref, webViewFallback = false, providerId = providerId)
        }
        if (webViewFallback && (html.isBlank() || ProviderHttp.isChallenge(html))) {
            html = ProviderHttp.fetch(url, referer = ref, webViewFallback = true, providerId = providerId)
        }
        return html
    }

    suspend fun postForm(
        path: String,
        seedBase: String,
        fields: Map<String, String>,
        providerId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val base = ensureToken(seedBase)
        val url = absolutize(base, path)
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val req = ProviderHttp.browserRequestBuilder(url, referer = "$base/", providerId = providerId)
            .post(body)
            .build()
        try {
            NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun invalidate(seedBase: String? = null) {
        if (seedBase == null) {
            resolvedBaseBySeed.clear()
            lastTokenAt.clear()
        } else {
            resolvedBaseBySeed.remove(seedBase.trimEnd('/'))
            seedBase.toHttpUrlOrNull()?.host?.let { lastTokenAt.remove(it) }
        }
    }

    private suspend fun fetchDirect(url: String, referer: String, providerId: String?): String =
        ProviderHttp.fetch(url, referer = referer, webViewFallback = false, providerId = providerId)

    private suspend fun requestToken(tokenUrl: String, seed: String): String = withContext(Dispatchers.IO) {
        val req = ProviderHttp.browserRequestBuilder(tokenUrl, referer = "$seed/", providerId = "megakino")
            .get()
            .build()
        try {
            NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url
                "${finalUrl.scheme}://${finalUrl.host}".trimEnd('/')
            }
        } catch (_: Exception) {
            seed.trimEnd('/')
        }
    }

    private fun absolutize(base: String, urlOrPath: String): String {
        val trimmed = urlOrPath.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return base.trimEnd('/') + "/" + trimmed.trimStart('/')
    }

    private fun looksLikeTokenGate(html: String): Boolean =
        html.length < 400 && html.contains("yg=token", ignoreCase = true)
}
