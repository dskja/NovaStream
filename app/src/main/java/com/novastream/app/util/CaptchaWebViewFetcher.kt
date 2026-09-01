package com.novastream.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Lädt HTML über WebView — umgeht reCAPTCHA/Bot-Schutz (z.B. Burning Series).
 * Nutzt eine wiederverwendbare WebView-Instanz mit Rate-Limit (3 Anfragen/Minute).
 */
object CaptchaWebViewFetcher {

    private const val MAX_REQUESTS_PER_MINUTE = 3
    private const val RATE_WINDOW_MS = 60_000L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var sharedWebView: WebView? = null

    private val rateLimitLock = Any()
    private val requestTimestamps = ArrayDeque<Long>()

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun acquireRateLimitSlot(): Boolean {
        synchronized(rateLimitLock) {
            val now = System.currentTimeMillis()
            while (requestTimestamps.isNotEmpty() && now - requestTimestamps.first() > RATE_WINDOW_MS) {
                requestTimestamps.removeFirst()
            }
            if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) return false
            requestTimestamps.addLast(now)
            return true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateWebView(context: Context): WebView {
        sharedWebView?.let { return it }
        return WebView(context).also { webView ->
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = com.novastream.app.data.model.NovaStreamConfig.USER_AGENT
            }
            webView.webChromeClient = WebChromeClient()
            sharedWebView = webView
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchHtml(url: String, timeoutMs: Long = 20_000L): String {
        val context = appContext ?: return ""
        if (!acquireRateLimitSlot()) {
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.w("CaptchaWebViewFetcher", "Rate limit exceeded (3/min)")
            }
            return ""
        }
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val webView = try {
                        getOrCreateWebView(context)
                    } catch (_: Exception) {
                        cont.resume("")
                        return@suspendCancellableCoroutine
                    }
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            if (!cont.isActive) return
                            try {
                                view?.evaluateJavascript(
                                    "(function(){return document.documentElement.outerHTML;})();"
                                ) { result ->
                                    if (cont.isActive) {
                                        val decoded = result?.trim('"')
                                            ?.replace("\\n", "\n")
                                            ?.replace("\\\"", "\"")
                                            ?.replace("\\\\", "\\")
                                            ?: ""
                                        cont.resume(decoded)
                                    }
                                }
                            } catch (_: Exception) {
                                if (cont.isActive) cont.resume("")
                            }
                        }
                    }
                    cont.invokeOnCancellation {
                        try { webView.stopLoading() } catch (_: Exception) {}
                    }
                    try {
                        webView.stopLoading()
                        webView.loadUrl(url)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume("")
                    }
                }
            } ?: ""
        }
    }
}
