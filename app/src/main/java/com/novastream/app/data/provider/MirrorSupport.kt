package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.util.CaptchaWebViewFetcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Composable mirror resolution + HTTP fetch for custom HTML scraper providers.
 * Keeps [ProviderDomainResolver] wiring and retry logic in one place.
 */
class MirrorSupport(
    private val providerId: String,
    private val defaultBaseUrl: String,
    private val appContext: Context?,
    private val contentNeedle: String = ProviderMirrorNeedles.needleFor(providerId),
    private val onInvalidate: (() -> Unit)? = null
) {

    private val resolveMutex = Mutex()

    @Volatile
    private var resolvedBaseUrl: String? = null

    init {
        ProviderDomainResolver.registerInvalidator(providerId) {
            resolvedBaseUrl = null
            onInvalidate?.invoke()
        }
    }

    fun parseBase(): String = resolvedBaseUrl ?: defaultBaseUrl.trimEnd('/')

    suspend fun activeBase(forceRefresh: Boolean = false): String = resolveMutex.withLock {
        if (!forceRefresh) {
            resolvedBaseUrl?.let { return it }
        }
        val resolved = ProviderDomainResolver.resolveActiveBaseUrl(
            providerId = providerId,
            defaultBaseUrl = defaultBaseUrl,
            contentNeedle = contentNeedle,
            appContext = appContext,
            forceRefresh = forceRefresh
        )
        resolvedBaseUrl = resolved
        resolved
    }

    suspend fun fetch(
        url: String,
        webViewFallback: Boolean = true,
        referer: String? = null
    ): String {
        val base = activeBase()
        val ref = referer ?: "$base/"
        repeat(3) { attempt ->
            val useWebView = webViewFallback && attempt > 0
            var fetchUrl = url
            var fetchRef = ref
            var html = ProviderHttp.fetch(fetchUrl, referer = fetchRef, webViewFallback = useWebView)
            if (shouldRetryMirror(html)) {
                val refreshed = activeBase(forceRefresh = true)
                if (refreshed != base && url.startsWith(base)) {
                    fetchUrl = url.replace(base, refreshed)
                    fetchRef = "$refreshed/"
                    html = ProviderHttp.fetch(fetchUrl, referer = fetchRef, webViewFallback = useWebView)
                }
            }
            if (html.isNotBlank() && !ProviderHttp.isChallenge(html)) return html
        }
        return ProviderHttp.fetch(url, referer = ref, webViewFallback = webViewFallback)
    }

    /** For sites with reCAPTCHA (e.g. Burning Series): OkHttp first, then WebView. */
    suspend fun fetchWithCaptcha(url: String): String {
        val base = activeBase()
        val http = ProviderHttp.fetch(url, referer = "$base/", webViewFallback = false)
        if (http.isNotBlank() && !ProviderHttp.isChallenge(http)) return http
        val web = CaptchaWebViewFetcher.fetchHtml(url)
        if (web.isNotBlank()) return web
        if (shouldRetryMirror(http)) {
            val refreshed = activeBase(forceRefresh = true)
            if (refreshed != base && url.startsWith(base)) {
                val retryUrl = url.replace(base, refreshed)
                val retryHttp = ProviderHttp.fetch(retryUrl, referer = "$refreshed/", webViewFallback = false)
                if (retryHttp.isNotBlank() && !ProviderHttp.isChallenge(retryHttp)) return retryHttp
                return CaptchaWebViewFetcher.fetchHtml(retryUrl).ifBlank { retryHttp }
            }
        }
        return web.ifBlank { http }
    }

    fun invalidate() {
        resolvedBaseUrl = null
    }

    private fun shouldRetryMirror(html: String): Boolean =
        appContext != null && ProviderMirrorNeedles.hasMirrors(providerId) &&
            (html.isBlank() || ProviderHttp.isChallenge(html))
}
