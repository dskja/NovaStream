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
 */
object CaptchaWebViewFetcher {

    @Volatile
    private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchHtml(url: String, timeoutMs: Long = 20_000L): String {
        val context = appContext ?: return ""
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val webView = try {
                        WebView(context)
                    } catch (_: Exception) {
                        cont.resume("")
                        return@suspendCancellableCoroutine
                    }
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = com.novastream.app.data.model.NovaStreamConfig.USER_AGENT
                    }
                    webView.webChromeClient = WebChromeClient()
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            if (!cont.isActive) return
                            val html = try {
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
                                    try { webView.destroy() } catch (_: Exception) {}
                                }
                            } catch (_: Exception) {
                                cont.resume("")
                                try { webView.destroy() } catch (_: Exception) {}
                            }
                            if (html == null && cont.isActive) {
                                cont.resume("")
                                try { webView.destroy() } catch (_: Exception) {}
                            }
                        }
                    }
                    cont.invokeOnCancellation {
                        try { webView.stopLoading() } catch (_: Exception) {}
                        try { webView.destroy() } catch (_: Exception) {}
                    }
                    webView.loadUrl(url)
                }
            } ?: ""
        }
    }
}
