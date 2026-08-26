package com.novastream.app.util

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.novastream.app.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Löst VOE-Hoster-URLs durch Ausführung der JavaScript-Player-Logik in einem WebView auf.
 *
 * VOE nutzt Bot-Detection (botd) + obfuskiertes JavaScript (105KB), um die Video-URL
 * zu verschleiern. Ein einfacher HTTP-Scraper kann die URL nicht extrahieren.
 *
 * Dieser Resolver:
 * 1. Erstellt einen WebView auf dem Main-Thread
 * 2. Lädt die VOE-Seite (inkl. aller JS-Ausführung)
 * 3. Intercepted den m3u8/mp4-Request aus dem WebView
 * 4. Gibt die Stream-URL zurück
 */
class VoeWebViewResolver {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(hosterPageUrl: String, hosterName: String): List<StreamSource> = withContext(Dispatchers.Main) {
        val capturedUrl = AtomicReference<String?>(null)
        val context = currentContext ?: run {
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.w("VoeWebViewResolver", "Context is null - VOE resolution skipped")
            }
            return@withContext emptyList()
        }

        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            blockNetworkImage = true  // keine Bilder laden → schneller
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                // Intercept m3u8 und mp4 Requests aus dem JWPlayer
                if (url.contains(".m3u8") || url.contains(".mp4")) {
                    if (!url.contains("test-videos") && !url.contains("bigbuckbunny")) {
                        capturedUrl.compareAndSet(null, url)
                    }
                }
                return null  // Request normal fortsetzen
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Versuche die Video-URL direkt aus dem JWPlayer zu extrahieren
                view?.evaluateJavascript("""
                    (function() {
                        try {
                            // JWPlayer API: get the playlist item's file
                            if (typeof jwplayer !== 'undefined') {
                                var p = jwplayer();
                                if (p && p.getPlaylistItem) {
                                    var item = p.getPlaylistItem();
                                    if (item && item.file) {
                                        AndroidVoe.onVideoUrl(item.file);
                                    }
                                    if (item && item.sources) {
                                        item.sources.forEach(function(s) {
                                            if (s.file) AndroidVoe.onVideoUrl(s.file);
                                        });
                                    }
                                }
                            }
                            // Fallback: var source
                            if (typeof source !== 'undefined' && source && source.indexOf('test-videos') === -1) {
                                AndroidVoe.onVideoUrl(source);
                            }
                            // Fallback: search for video element
                            var videos = document.querySelectorAll('video source, video');
                            for (var i = 0; i < videos.length; i++) {
                                var src = videos[i].src || videos[i].getAttribute('src');
                                if (src && src.indexOf('test-videos') === -1 && (src.indexOf('.m3u8') !== -1 || src.indexOf('.mp4') !== -1)) {
                                    AndroidVoe.onVideoUrl(src);
                                }
                            }
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
            }
        }

        // JavaScript Interface für URL-Capture
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onVideoUrl(url: String) {
                if (url.contains(".m3u8") || url.contains(".mp4")) {
                    if (!url.contains("test-videos") && !url.contains("bigbuckbunny")) {
                        capturedUrl.compareAndSet(null, url)
                    }
                }
            }
        }, "AndroidVoe")

        // Seite laden
        webView.loadUrl(hosterPageUrl)

        // Warten bis URL gefunden oder Timeout (20s) - mit try-finally für Cleanup
        val videoUrl = try {
            withTimeoutOrNull(20000L) {
                while (capturedUrl.get() == null) {
                    kotlinx.coroutines.delay(500)
                }
                capturedUrl.get()
            }
        } finally {
            // WebView immer aufräumen, auch bei Exceptions/Timeout
            try {
                webView.stopLoading()
                webView.removeJavascriptInterface("AndroidVoe")
                webView.destroy()
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) {
                    android.util.Log.w("VoeWebViewResolver", "WebView cleanup failed", e)
                }
            }
        }

        val finalUrl = videoUrl
        if (finalUrl != null) {
            val isHls = finalUrl.contains(".m3u8")
            listOf(StreamSource(
                hoster = hosterName,
                url = finalUrl,
                isHls = isHls,
                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4"
            ))
        } else {
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var currentContext: android.content.Context? = null

        fun setContext(context: android.content.Context) {
            currentContext = context
        }

        fun clearContext() {
            currentContext = null
        }
    }
}
