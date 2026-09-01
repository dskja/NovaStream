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
        if (html.length < 400) {
            if (lower.contains("just a moment") || lower.contains("checking your browser")) return true
        }
        return lower.contains("cf-challenge") ||
            lower.contains("challenge-platform") ||
            lower.contains("recaptcha") ||
            lower.contains("captcha") ||
            lower.contains("ddos-guard") && html.length < 2_000 && !lower.contains("/serie") ||
            lower.contains("attention required") ||
            lower.contains("enable javascript")
    }

    /** Fetch HTML with optional in-memory cache. */
    suspend fun fetch(
        url: String,
        referer: String? = null,
        useCache: Boolean = true,
        webViewFallback: Boolean = false
    ): String {
        if (useCache) {
            cacheMutex.withLock {
                cache[url]?.let { (cachedAt, html) ->
                    if (System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) return html
                    cache.remove(url)
                }
            }
        }

        var html = fetchNetwork(url, referer)
        if (webViewFallback && (html.isBlank() || isChallenge(html))) {
            html = CaptchaWebViewFetcher.fetchHtml(url)
        }

        if (useCache && html.isNotBlank()) {
            cacheMutex.withLock {
                cache[url] = System.currentTimeMillis() to html
            }
        }
        return html
    }

    /** Try multiple entry URLs; returns first base URL whose home page looks valid. */
    suspend fun resolveWorkingBase(
        candidates: List<String>,
        contentNeedle: String = "/title/",
        webViewFallback: Boolean = true
    ): String? {
        for (candidate in candidates) {
            val base = candidate.trimEnd('/')
            val html = fetch(base + "/", referer = base + "/", webViewFallback = webViewFallback)
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

    private suspend fun fetchNetwork(url: String, referer: String?): String = withContext(Dispatchers.IO) {
        val ref = referer ?: url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}/" } ?: url
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", NovaStreamConfig.USER_AGENT)
            .header("Referer", ref)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            .build()
        try {
            NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
