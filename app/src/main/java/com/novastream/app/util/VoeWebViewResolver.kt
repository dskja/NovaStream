package com.novastream.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.novastream.app.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
 *
 * WICHTIG: Verwendet applicationContext um Memory Leaks zu vermeiden.
 */
class VoeWebViewResolver {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(hosterPageUrl: String, hosterName: String): List<StreamSource> {
        val context = currentContext ?: run {
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.w("VoeWebViewResolver", "Context is null - VOE resolution skipped")
            }
            return emptyList()
        }
        return withContext(Dispatchers.Main) {
            val capturedUrls = java.util.concurrent.ConcurrentLinkedQueue<String>()

            val webView = try {
                WebView(context)
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) {
                    android.util.Log.e("VoeWebViewResolver", "WebView creation failed", e)
                }
                return@withContext emptyList()
            }

            // Configure WebView with TV-optimized settings
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                blockNetworkImage = true
                // TV-optimierter User Agent
                userAgentString = com.novastream.app.data.model.NovaStreamConfig.USER_AGENT
                // Enable faster page loading
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                setSupportZoom(false)
                // Enable database storage
                databaseEnabled = true
                // Allow mixed content (some hosters use http resources)
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                    try {
                        val url = request?.url?.toString() ?: return null
                        if (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".webm")) {
                            if (!url.contains("test-videos") && !url.contains("bigbuckbunny") && !url.contains("sample-")) {
                                capturedUrls.add(url)
                            }
                        }
                    } catch (e: Exception) {
                        DebugLog.w("VoeWebViewResolver", "intercept request failed", e)
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    try {
                        view?.evaluateJavascript("""
                            (function() {
                                try {
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
                                    if (typeof source !== 'undefined' && source && source.indexOf('test-videos') === -1) {
                                        AndroidVoe.onVideoUrl(source);
                                    }
                                    var videos = document.querySelectorAll('video source, video');
                                    for (var i = 0; i < videos.length; i++) {
                                        var src = videos[i].src || videos[i].getAttribute('src');
                                        if (src && src.indexOf('test-videos') === -1 && (src.indexOf('.m3u8') !== -1 || src.indexOf('.mp4') !== -1 || src.indexOf('.webm') !== -1)) {
                                            AndroidVoe.onVideoUrl(src);
                                        }
                                    }
                                    // Video.js player
                                    if (typeof videojs !== 'undefined') {
                                        var players = videojs.getPlayers();
                                        for (var key in players) {
                                            if (players.hasOwnProperty(key)) {
                                                var pl = players[key];
                                                if (pl && pl.src) {
                                                    var s = pl.src();
                                                    if (s && s.indexOf('test-videos') === -1) {
                                                        AndroidVoe.onVideoUrl(s);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // Plyr player
                                    if (typeof Plyr !== 'undefined') {
                                        var plyrPlayers = document.querySelectorAll('[data-plyr]');
                                        for (var j = 0; j < plyrPlayers.length; j++) {
                                            var pSrc = plyrPlayers[j].src || plyrPlayers[j].getAttribute('src');
                                            if (pSrc && pSrc.indexOf('test-videos') === -1) {
                                                AndroidVoe.onVideoUrl(pSrc);
                                            }
                                        }
                                    }
                                    // Generic: search all script tags for video URLs
                                    var scripts = document.querySelectorAll('script');
                                    for (var k = 0; k < scripts.length; k++) {
                                        var text = scripts[k].textContent || '';
                                        var matches = text.match(/https?:\/\/[^"'\\\s]+\.(?:m3u8|mp4|webm)[^"'\\\s]*/g);
                                        if (matches) {
                                            for (var l = 0; l < matches.length; l++) {
                                                if (matches[l].indexOf('test-videos') === -1 && matches[l].indexOf('bigbuckbunny') === -1) {
                                                    AndroidVoe.onVideoUrl(matches[l]);
                                                }
                                            }
                                        }
                                    }
                                } catch(e) {}
                            })();
                        """.trimIndent(), null)
                    } catch (e: Exception) {
                        DebugLog.w("VoeWebViewResolver", "intercept request failed", e)
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (com.novastream.app.BuildConfig.DEBUG) {
                        android.util.Log.w("VoeWebViewResolver", "WebView error: ${request?.url} $error")
                    }
                }
            }

            webView.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onVideoUrl(url: String) {
                    if (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".webm")) {
                        if (!url.contains("test-videos") && !url.contains("bigbuckbunny") && !url.contains("sample-")) {
                            capturedUrls.add(url)
                        }
                    }
                }
            }, "AndroidVoe")

            webView.loadUrl(hosterPageUrl)

            // Wait for URLs with timeout - check every 200ms, return as soon as we have any
            val videoUrls = try {
                withTimeoutOrNull(10_000L) {
                    while (capturedUrls.isEmpty()) {
                        kotlinx.coroutines.delay(200)
                    }
                    // Wait a bit more for additional URLs (quality options)
                    kotlinx.coroutines.delay(800)
                    capturedUrls.toList()
                }
            } finally {
                // Cleanup - destroy als letztes
                try { webView.stopLoading() } catch (e: Exception) { DebugLog.w("VoeWebViewResolver", "stopLoading failed", e) }
                try { webView.removeJavascriptInterface("AndroidVoe") } catch (e: Exception) { DebugLog.w("VoeWebViewResolver", "removeJavascriptInterface failed", e) }
                try { webView.clearHistory() } catch (e: Exception) { DebugLog.w("VoeWebViewResolver", "clearHistory failed", e) }
                try { webView.loadUrl("about:blank") } catch (e: Exception) { DebugLog.w("VoeWebViewResolver", "loadUrl blank failed", e) }
                try { webView.destroy() } catch (e: Exception) { DebugLog.w("VoeWebViewResolver", "destroy failed", e) }
            }

            videoUrls?.map { url ->
                val isHls = url.contains(".m3u8")
                StreamSource(
                    hoster = hosterName,
                    url = url,
                    isHls = isHls,
                    mimeType = when {
                        isHls -> "application/x-mpegURL"
                        url.contains(".webm") -> "video/webm"
                        else -> "video/mp4"
                    }
                )
            }?.distinctBy { it.url } ?: emptyList()
        }
    }

    companion object {
        @Volatile
        private var currentContext: Context? = null
        private val contextLock = Any()

        fun setContext(context: Context) {
            synchronized(contextLock) {
                currentContext = context.applicationContext
            }
        }

        fun clearContext() {
            synchronized(contextLock) {
                currentContext = null
            }
        }
    }
}
