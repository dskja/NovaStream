package com.novastream.app.util

import android.util.Base64
import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.data.model.StreamSource
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Löst eine Hoster-URL zu einer abspielbaren Stream-URL auf.
 *
 * Ablauf:
 * 1. redirectUrl kann sein:
 *    - SerienStream: /r?t=eyJ... (Redirect-Pfad auf serienstream.to)
 *    - AniWorld: /redirect/{id} (Redirect-Pfad auf aniworld.to)
 *    - KinoGer: direkte iframe-URL (https://fsst.online/embed/... oder https://kinoger.pw/e/...)
 * 2. OkHttp folgt HTTP-302 Redirects automatisch zur Hoster-Seite
 * 3. Falls JS-Redirect im HTML: URL extrahieren und Hoster-Seite laden
 * 4. VOE: WebView-Resolver (Bot-Detection + obfuskiertes JS)
 * 5. Andere Hoster: HTML parsen und Stream-URLs extrahieren
 */
class HosterResolver(
    private val client: okhttp3.OkHttpClient = NetworkModule.okHttpClient,
    private val voeWebViewResolver: VoeWebViewResolver = VoeWebViewResolver(),
    private val baseUrl: String = NovaStreamConfig.BASE_URL
) {

    companion object {
        // Pre-compiled regex patterns (avoid recompilation on every call)
        private val SRC_PATTERN = Regex("src\\s*=\\s*['\"]([^'\"]+\\.(?:m3u8|mp4|webm)[^'\"]*)['\"]")
        private val DATA_SRC_PATTERN = Regex("data-src\\s*=\\s*['\"]([^'\"]+\\.(?:m3u8|mp4|webm)[^'\"]*)['\"]")
        private val URL_COLON_PATTERN = Regex("url\\s*:\\s*['\"]([^'\"]+\\.(?:m3u8|mp4|webm)[^'\"]*)['\"]")
        private val META_REFRESH_PATTERN = Regex("http-equiv=['\"]refresh['\"]\\s+content=['\"]\\d+;url=([^'\"\\s]+)['\"]")
    }

    /** Macht eine relative URL absolut basierend auf der Provider-Base-URL. */
    private fun absUrl(path: String): String =
        if (path.startsWith("http")) path else baseUrl + path

    suspend fun resolve(hosterName: String, redirectUrl: String): List<StreamSource> {
        return try {
            if (redirectUrl.isBlank()) return emptyList()

            // 1. Redirect-URL absolut machen und Seite laden
            val absoluteUrl = absUrl(redirectUrl)
            val redirectHtml = kotlinx.coroutines.withTimeoutOrNull(NovaStreamConfig.REDIRECT_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { fetchHtml(absoluteUrl) }
            } ?: return emptyList()

            // 2. JS-Redirect-URL aus dem HTML extrahieren (falls vorhanden)
            val hosterPageUrl = extractJsRedirect(redirectHtml).ifBlank {
                // OkHttp ist bereits durch followRedirects zur Hoster-Seite gefolgt
                // Wenn kein JS-Redirect gefunden wurde, verwenden wir die absolute URL
                absoluteUrl
            }

            // 3. VOE: Nutze WebView-Resolver (Bot-Detection + obfuskiertes JS)
            if (hosterName.contains("voe", ignoreCase = true) ||
                hosterPageUrl.contains("voe", ignoreCase = true)) {
                val voeResult = voeWebViewResolver.resolve(hosterPageUrl, hosterName)
                if (voeResult.isNotEmpty()) return voeResult
                // Fallback: versuche HTTP-Extraktion
            }

            // 4. Andere Hoster: HTML laden und Stream-URLs extrahieren
            val html = kotlinx.coroutines.withTimeoutOrNull(NovaStreamConfig.HOSTER_RESOLVE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { fetchHtml(hosterPageUrl) }
            } ?: return emptyList()
            if (html.isBlank()) return emptyList()

            // 5. Stream-URLs extrahieren (hoster-spezifisch)
            val sources = extractStreamUrls(html, hosterName, hosterPageUrl)
            if (sources.isNotEmpty()) return sources

            // 6. Falls keine direkten URLs gefunden: versuche VOE WebView als letzten Ausweg
            if (hosterPageUrl.contains("voe", ignoreCase = true)) {
                return voeWebViewResolver.resolve(hosterPageUrl, hosterName)
            }

            // 7. Letzter Versuch: Generic extraction mit erweiterten Patterns
            val genericSources = extractGenericUrls(html, hosterName)
            if (genericSources.isNotEmpty()) return genericSources

            emptyList()
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.e("HosterResolver", "resolve failed for $hosterName url=$redirectUrl", e)
            }
            emptyList()
        }
    }

    /** Generic URL extraction mit erweiterten Patterns als letzter Fallback. */
    private fun extractGenericUrls(html: String, hoster: String): List<StreamSource> {
        val sources = mutableListOf<StreamSource>()
        // Pattern 1: src="..." mit video-URL
        SRC_PATTERN.findAll(html).forEach { m ->
            var url = m.groupValues[1]
            if (url.startsWith("//")) url = "https:$url"
            if (!NovaStreamConfig.isTestVideo(url)) {
                sources.add(StreamSource(hoster, url, isHls = url.contains(".m3u8")))
            }
        }
        // Pattern 2: data-src="..." mit video-URL
        DATA_SRC_PATTERN.findAll(html).forEach { m ->
            var url = m.groupValues[1]
            if (url.startsWith("//")) url = "https:$url"
            if (!NovaStreamConfig.isTestVideo(url)) {
                sources.add(StreamSource(hoster, url, isHls = url.contains(".m3u8")))
            }
        }
        // Pattern 3: url:"..." mit video-URL
        URL_COLON_PATTERN.findAll(html).forEach { m ->
            var url = m.groupValues[1]
            if (url.startsWith("//")) url = "https:$url"
            if (!NovaStreamConfig.isTestVideo(url)) {
                sources.add(StreamSource(hoster, url, isHls = url.contains(".m3u8")))
            }
        }
        return sources.distinctBy { it.url }
    }

    /** Extrahiert die JS-Redirect-URL aus dem Response-HTML. */
    private fun extractJsRedirect(html: String): String {
        // Pattern 1: window.location.href = 'https://...'
        Regex("window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 2: location.href = 'https://...'
        Regex("location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 3: window.location = 'https://...'
        Regex("window\\.location\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 4: <meta http-equiv="refresh" content="0;url=https://...">
        META_REFRESH_PATTERN.find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 5: window.location.replace('https://...')
        Regex("window\\.location\\.replace\\(['\"]([^'\"]+)['\"]\\)").find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 6: document.location.href = 'https://...'
        Regex("document\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 7: self.location = 'https://...'
        Regex("self\\.location\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 8: top.location.href = 'https://...'
        Regex("top\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 9: setTimeout("location.href='...'", ...)
        Regex("setTimeout\\(['\"]location\\.href=['\"]([^'\"]+)['\"]['\"]").find(html)?.let {
            return it.groupValues[1]
        }
        // Pattern 10: <a href="..." onclick="...">click here</a> auto-redirect
        Regex("<a[^>]+href=['\"]([^'\"]+)['\"][^>]*>\\s*[Cc]lick here").find(html)?.let {
            return it.groupValues[1]
        }
        return ""
    }

    private suspend fun fetchHtml(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", NovaStreamConfig.USER_AGENT)
            .header("Referer", baseUrl + "/")
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (com.novastream.app.BuildConfig.DEBUG) {
                    android.util.Log.e("HosterResolver", "HTTP ${resp.code} for $url")
                }
                return ""
            }
            resp.body?.string() ?: ""
        }
    }

    /** Extrahiert Stream-URLs aus dem Hoster-HTML. */
    private fun extractStreamUrls(html: String, hoster: String, pageUrl: String): List<StreamSource> {
        val sources = mutableListOf<StreamSource>()

        when {
            hoster.contains("voe", ignoreCase = true) || pageUrl.contains("voe", ignoreCase = true) ->
                sources.addAll(extractVoe(html, hoster, pageUrl))
            hoster.contains("streamtape", ignoreCase = true) || pageUrl.contains("streamtape", ignoreCase = true) ->
                sources.addAll(extractStreamtape(html, hoster))
            hoster.contains("dood", ignoreCase = true) || pageUrl.contains("dood", ignoreCase = true) ->
                sources.addAll(extractDood(html, hoster, pageUrl))
            hoster.contains("vidoza", ignoreCase = true) || pageUrl.contains("vidoza", ignoreCase = true) ->
                sources.addAll(extractVidoza(html, hoster))
            hoster.contains("filemoon", ignoreCase = true) || pageUrl.contains("filemoon", ignoreCase = true) ->
                sources.addAll(extractFilemoon(html, hoster))
            hoster.contains("speedo", ignoreCase = true) || pageUrl.contains("speedo", ignoreCase = true) ->
                sources.addAll(extractSpeedo(html, hoster))
            hoster.contains("fsst", ignoreCase = true) || pageUrl.contains("fsst", ignoreCase = true) ->
                sources.addAll(extractFsst(html, hoster, pageUrl))
            hoster.contains("mixdrop", ignoreCase = true) || pageUrl.contains("mixdrop", ignoreCase = true) ->
                sources.addAll(extractMixdrop(html, hoster))
            hoster.contains("upstream", ignoreCase = true) || pageUrl.contains("upstream", ignoreCase = true) ->
                sources.addAll(extractUpstream(html, hoster))
            hoster.contains("streamlare", ignoreCase = true) || pageUrl.contains("streamlare", ignoreCase = true) ->
                sources.addAll(extractStreamlare(html, hoster))
            else -> {
                // Generic: search for m3u8, mp4, and webm
                Regex("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*").findAll(html).forEach { m ->
                    sources.add(StreamSource(hoster = hoster, url = m.value, isHls = true))
                }
                Regex("https?://[^\"'\\s]+\\.mp4[^\"'\\s]*").findAll(html).forEach { m ->
                    sources.add(StreamSource(hoster = hoster, url = m.value, isHls = false, mimeType = "video/mp4"))
                }
                Regex("https?://[^\"'\\s]+\\.webm[^\"'\\s]*").findAll(html).forEach { m ->
                    sources.add(StreamSource(hoster = hoster, url = m.value, isHls = false, mimeType = "video/webm"))
                }
            }
        }

        return sources.distinctBy { it.url }
    }

    /**
     * VOE Extraktion – mehrere Strategien:
     * 1. var source='...' (manchmal die echte URL, manchmal dummy)
     * 2. Base64-kodierte URLs in JavaScript
     * 3. m3u8/mp4 Patterns im HTML
     * 4. URL aus der JWPlayer Konfiguration
     */
    private fun extractVoe(html: String, hoster: String, pageUrl: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()

        // Strategie 1: var source='https://...' (aber nicht die test-videos.co.uk dummy)
        Regex("var\\s+source\\s*=\\s*['\"]([^'\"]+)['\"]").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            if (url.contains("test-videos.co.uk").not() && (url.contains(".m3u8") || url.contains(".mp4"))) {
                out.add(StreamSource(hoster, url, isHls = url.contains(".m3u8")))
            }
        }

        // Strategie 2: Base64-decoded URLs in eval/atob calls
        Regex("atob\\(['\"]([A-Za-z0-9+/=]+)['\"]\\)").findAll(html).forEach { m ->
            try {
                val decoded = String(Base64.decode(m.groupValues[1], Base64.DEFAULT))
                if (decoded.contains("https://") && (decoded.contains(".m3u8") || decoded.contains(".mp4"))) {
                    Regex("https?://[^\"'\\s]+\\.(m3u8|mp4)[^\"'\\s]*").findAll(decoded).forEach { urlMatch ->
                        out.add(StreamSource(hoster, urlMatch.value,
                            isHls = urlMatch.value.contains(".m3u8")))
                    }
                }
            } catch (_: Exception) {}
        }

        // Strategie 3: Direct m3u8/mp4/webm URLs in the page
        Regex("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = true))
        }
        Regex("https?://[^\"'\\s]+\\.mp4[^\"'\\s]*").findAll(html).forEach { m ->
            if (!m.value.contains("test-videos.co.uk")) {
                out.add(StreamSource(hoster, m.value, isHls = false, mimeType = "video/mp4"))
            }
        }
        Regex("https?://[^\"'\\s]+\\.webm[^\"'\\s]*").findAll(html).forEach { m ->
            if (!m.value.contains("test-videos")) {
                out.add(StreamSource(hoster, m.value, isHls = false, mimeType = "video/webm"))
            }
        }

        // Strategie 4: URL patterns in obfuscated JS arrays
        Regex("['\"](https?://[^'\"]+(?:m3u8|mp4)[^'\"]*)['\"]").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            if (!url.contains("test-videos.co.uk") && !url.contains("bigbuckbunny")) {
                out.add(StreamSource(hoster, url, isHls = url.contains(".m3u8")))
            }
        }

        return out.distinctBy { it.url }
    }

    private fun extractStreamtape(html: String, hoster: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        Regex("https?://[\\w.-]+/get_video\\?[^\"'\\s]+").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = false, mimeType = "video/mp4"))
        }
        Regex("'(https?://[^']+\\.mp4[^']*)'").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.groupValues[1], isHls = false, mimeType = "video/mp4"))
        }
        Regex("href\\s*=\\s*\"(https?://[^\"]+)\"[^>]*>.*?Download").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.groupValues[1], isHls = false, mimeType = "video/mp4"))
        }
        return out
    }

    private fun extractDood(html: String, hoster: String, pageUrl: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        // Doodstream: pass_md5 pattern
        Regex("https?://[\\w.-]+/pass_md5/[^\"'\\s]+").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = false, mimeType = "video/mp4"))
        }
        // Doodstream sometimes has the URL in a dsplayer call
        Regex("file:\\s*['\"]([^'\"]+)['\"]").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            if (url.startsWith("http") && (url.contains(".mp4") || url.contains(".m3u8"))) {
                out.add(StreamSource(hoster, url, isHls = url.contains(".m3u8")))
            }
        }
        return out
    }

    private fun extractVidoza(html: String, hoster: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        Regex("src\\s*[:=]\\s*\"(https?://[^\"]+\\.m3u8[^\"]*)").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.groupValues[1], isHls = true))
        }
        Regex("src\\s*[:=]\\s*\"(https?://[^\"]+\\.mp4[^\"]*)").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.groupValues[1], isHls = false, mimeType = "video/mp4"))
        }
        return out
    }

    private fun extractFilemoon(html: String, hoster: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        Regex("sources\\s*:\\s*\\[\\s*\\{[^}]*file\\s*:\\s*\"([^\"]+)\"").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            val isHls = url.contains(".m3u8")
            out.add(StreamSource(hoster, url, isHls = isHls,
                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4"))
        }
        Regex("file\\s*:\\s*\"(https?://[^\"]+(?:m3u8|mp4)[^\"]*)\"").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            val isHls = url.contains(".m3u8")
            out.add(StreamSource(hoster, url, isHls = isHls,
                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4"))
        }
        return out
    }

    private fun extractSpeedo(html: String, hoster: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        Regex("file:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.groupValues[1], isHls = true))
        }
        Regex("file:\\s*['\"]([^'\"]+\\.mp4[^'\"]*)['\"]").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.groupValues[1], isHls = false, mimeType = "video/mp4"))
        }
        return out
    }

    /** FSST.online - KinoGer's primärer Hoster. */
    private fun extractFsst(html: String, hoster: String, pageUrl: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        // m3u8/mp4/webm URLs
        Regex("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = true))
        }
        Regex("https?://[^\"'\\s]+\\.mp4[^\"'\\s]*").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = false, mimeType = "video/mp4"))
        }
        Regex("https?://[^\"'\\s]+\\.webm[^\"'\\s]*").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = false, mimeType = "video/webm"))
        }
        // file: 'url'
        Regex("file:\\s*['\"]([^'\"]+)['\"]").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            if (url.startsWith("http") && (url.contains(".mp4") || url.contains(".m3u8"))) {
                out.add(StreamSource(hoster, url, isHls = url.contains(".m3u8")))
            }
        }
        // sources: [{file: "url"}]
        Regex("sources\\s*:\\s*\\[\\s*\\{[^}]*file\\s*:\\s*\"([^\"]+)\"").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            val isHls = url.contains(".m3u8")
            out.add(StreamSource(hoster, url, isHls = isHls,
                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4"))
        }
        return out
    }

    /** Mixdrop extraction. */
    private fun extractMixdrop(html: String, hoster: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        // Mixdrop: eval(p,a,c,k,e,d) pattern with video URL
        Regex("https?://[\\w.-]+/get_video\\?[^\"'\\s]+").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = false, mimeType = "video/mp4"))
        }
        Regex("MDCore\\.video_url\\s*=\\s*['\"]([^'\"]+)['\"]").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            if (url.startsWith("http")) {
                out.add(StreamSource(hoster, url, isHls = url.contains(".m3u8"),
                    mimeType = if (url.contains(".m3u8")) "application/x-mpegURL" else "video/mp4"))
            }
        }
        return out
    }

    /** Upstream extraction. */
    private fun extractUpstream(html: String, hoster: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        Regex("https?://[\\w.-]+/stream/[^\"'\\s]+\\.m3u8[^\"'\\s]*").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = true))
        }
        Regex("file\\s*:\\s*['\"]([^'\"]+\\.mp4[^'\"]*)['\"]").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.groupValues[1], isHls = false, mimeType = "video/mp4"))
        }
        // Upstream uses hls.src pattern
        Regex("hls\\.src\\s*=\\s*['\"]([^'\"]+)['\"]").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.groupValues[1], isHls = true))
        }
        return out
    }

    /** Streamlare extraction. */
    private fun extractStreamlare(html: String, hoster: String): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        Regex("https?://[\\w.-]+/stream/[^\"'\\s]+").findAll(html).forEach { m ->
            out.add(StreamSource(hoster, m.value, isHls = m.value.contains(".m3u8"),
                mimeType = if (m.value.contains(".m3u8")) "application/x-mpegURL" else "video/mp4"))
        }
        Regex("file\\s*:\\s*['\"]([^'\"]+(?:m3u8|mp4)[^'\"]*)['\"]").findAll(html).forEach { m ->
            val url = m.groupValues[1]
            out.add(StreamSource(hoster, url, isHls = url.contains(".m3u8")))
        }
        return out
    }
}
