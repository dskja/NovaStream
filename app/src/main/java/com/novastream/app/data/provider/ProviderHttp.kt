package com.novastream.app.data.provider

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.util.CaptchaWebViewFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Request.Builder

/**
 * Shared HTTP fetch for all streaming providers.
 * Handles caching, bot/challenge detection, and optional WebView fallback.
 */
object ProviderHttp {

    private const val CACHE_SIZE = 12
    private const val CACHE_TTL_MS = 5L * 60 * 1000

    private val cache = object : LinkedHashMap<String, Pair<Long, String>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean =
            size > CACHE_SIZE
    }
    private val cacheMutex = Mutex()

    /** True when HTML looks like a bot-wall rather than catalog content. */
    fun isChallenge(html: String): Boolean {
        if (html.isBlank()) return true
        val lower = html.lowercase()
        val hasCatalogSignals = lower.contains("/serie") ||
            lower.contains("/stream/") ||
            lower.contains("/title/") ||
            lower.contains("/films/") ||
            lower.contains("/serials/") ||
            lower.contains("dle-content") ||
            lower.contains("poster grid-item") ||
            lower.contains("/movie") ||
            lower.contains("/tv-show") ||
            lower.contains("/tv/") ||
            lower.contains("/pelicula") ||
            lower.contains("/film/") ||
            lower.contains("/filme") ||
            lower.contains("/serietv") ||
            lower.contains("/watch/") ||
            lower.contains("/anime/") ||
            lower.contains("/titles/") ||
            lower.contains("/play/") ||
            lower.contains("/doramas-online/") ||
            lower.contains("/serial-online/") ||
            lower.contains("<article") ||
            lower.contains("og:title")
        if (hasCatalogSignals && html.length > 1_500) return false
        if (html.length < 500) {
            return lower.contains("just a moment") ||
                lower.contains("checking your browser") ||
                lower.contains("cf-challenge") ||
                lower.contains("challenge-platform") ||
                lower.contains("attention required") ||
                lower.contains("ddos-guard")
        }
        if (html.length < 2_500) {
            return (lower.contains("cf-challenge") || lower.contains("challenge-platform")) &&
                !hasCatalogSignals
        }
        return false
    }

    /** Accept-Language header tuned to provider catalog language. */
    fun acceptLanguageHeader(providerId: String? = null): String {
        val lang = providerId?.let { ProviderRegistry.contentLanguageOf(it) } ?: ContentLanguage.EN
        return when (lang) {
            ContentLanguage.DE -> "de-DE,de;q=0.9,en;q=0.8"
            ContentLanguage.FR -> "fr-FR,fr;q=0.9,en;q=0.8"
            ContentLanguage.ES -> "es-ES,es;q=0.9,en;q=0.8"
            ContentLanguage.IT -> "it-IT,it;q=0.9,en;q=0.8"
            ContentLanguage.PL -> "pl-PL,pl;q=0.9,en;q=0.8"
            ContentLanguage.AR -> "ar,en;q=0.8"
            ContentLanguage.MULTI -> "en-US,en;q=0.9,de;q=0.8"
            ContentLanguage.EN -> "en-US,en;q=0.9"
        }
    }

    /** Fetch HTML with optional in-memory cache. */
    suspend fun fetch(
        url: String,
        referer: String? = null,
        useCache: Boolean = true,
        webViewFallback: Boolean = false,
        providerId: String? = null
    ): String {
        if (useCache) {
            cacheMutex.withLock {
                cache[url]?.let { (cachedAt, html) ->
                    if (System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) return html
                    cache.remove(url)
                }
            }
        }

        var html = fetchNetwork(url, referer, providerId)
        if (webViewFallback && (html.isBlank() || isChallenge(html))) {
            html = CaptchaWebViewFetcher.fetchHtml(url)
        }

        if (useCache && html.isNotBlank() && !isChallenge(html)) {
            cacheMutex.withLock {
                cache[url] = System.currentTimeMillis() to html
            }
        }
        return html
    }

    /** Clears the shared HTTP response cache (e.g. on provider/mirror switch). */
    suspend fun clearCache() {
        cacheMutex.withLock { cache.clear() }
    }

    /** Try multiple entry URLs; returns first base URL whose home page looks valid. */
    suspend fun resolveWorkingBase(
        candidates: List<String>,
        contentNeedle: String = "/title/",
        webViewFallback: Boolean = true,
        providerId: String? = null
    ): String? {
        for (candidate in candidates) {
            val base = candidate.trimEnd('/')
            val html = fetch(base + "/", referer = base + "/", webViewFallback = webViewFallback, providerId = providerId)
            if (html.isNotBlank() && !isChallenge(html)) {
                if (contentNeedle.isBlank() || html.contains(contentNeedle, ignoreCase = true)) {
                    return base
                }
            }
        }
        return null
    }

    /** Follow redirects manually and return the final URL (stops before redirect loops). */
    suspend fun resolveFinalUrl(url: String, maxHops: Int = 8): String? = withContext(Dispatchers.IO) {
        var current = url
        val seen = mutableSetOf<String>()
        repeat(maxHops) {
            if (!seen.add(current)) return@withContext null
            val req = Request.Builder()
                .url(current)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .get()
                .build()
            try {
                NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.code in 300..399) {
                        val loc = resp.header("Location") ?: return@withContext current.toHttpUrlOrNull()?.let {
                            "${it.scheme}://${it.host}"
                        }
                        current = if (loc.startsWith("http")) loc else {
                            resp.request.url.resolve(loc)?.toString() ?: return@withContext null
                        }
                        return@repeat
                    }
                    return@withContext resp.request.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
                }
            } catch (_: Exception) {
                return@withContext null
            }
        }
        null
    }

    /** Follow redirects and return the final request URL (BetterStreamflix getRedirectLink pattern). */
    suspend fun resolveRedirectFinal(url: String, referer: String? = null, providerId: String? = null): String? =
        withContext(Dispatchers.IO) {
            val req = browserRequestBuilder(url, referer, providerId).get().build()
            try {
                NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                    resp.request.url.toString().takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                null
            }
        }

    /** Builds a browser-like request (Sec-Fetch headers help Cloudflare/DDoS-Guard). */
    fun browserRequestBuilder(url: String, referer: String?, providerId: String? = null): Builder {
        val ref = referer ?: url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}/" } ?: url
        return Request.Builder()
            .url(url)
            .header("User-Agent", NovaStreamConfig.USER_AGENT)
            .header("Referer", ref)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", acceptLanguageHeader(providerId))
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", if (ref.toHttpUrlOrNull()?.host == url.toHttpUrlOrNull()?.host) "same-origin" else "none")
            .header("Sec-Fetch-User", "?1")
    }

    private suspend fun fetchNetwork(url: String, referer: String?, providerId: String? = null): String = withContext(Dispatchers.IO) {
        val req = browserRequestBuilder(url, referer, providerId).get().build()
        try {
            NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
