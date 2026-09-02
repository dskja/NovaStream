package com.novastream.app.util

import com.novastream.app.data.provider.ActiveProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/**
 * Absolute Cover-/Media-URLs and Referer-Header per provider.
 * Prevents images from provider A being built with provider B base URL.
 */
object MediaUrls {

    private val cleartextHosts = setOf(
        "kinoger.to", "kinoger.pw",
        "serienstream.to", "serienstream.cx",
        "aniworld.to", "s.to",
        "voe.sx", "voe-unblock.com", "fsst.online",
        "streamtape.com", "doodstream.com", "filemoon.sx",
        "vidoza.net", "vidmoly.to", "mixdrop.co",
        "localhost", "127.0.0.1"
    )

    fun abs(pathOrUrl: String?, baseUrl: String = ActiveProvider.baseUrl): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        val src = pathOrUrl.trim()
        if (src.contains("data:image") || src.endsWith(".svg", ignoreCase = true)) return null
        return when {
            src.startsWith("http://") || src.startsWith("https://") -> src
            src.startsWith("//") -> "https:$src"
            else -> baseUrl.trimEnd('/') + "/" + src.trimStart('/')
        }
    }

    /**
     * Preserve HTTP for cleartext-allowed hosts and local schemes.
     * Do not blindly rewrite HTTP→HTTPS for playback URLs — many hosters are HTTP-only.
     */
    fun playbackUrl(url: String): String {
        if (!url.startsWith("http://")) return url
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return url
        if (isCleartextHost(host)) return url
        return "https://" + url.removePrefix("http://")
    }

    /** @deprecated Use [playbackUrl] for streams; kept for call sites that expect scheme normalization. */
    fun secureUrl(url: String): String = playbackUrl(url)

    private fun isCleartextHost(host: String): Boolean =
        cleartextHosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }

    /** True when [host] may use HTTP per network security config (hosters, IPTV, DE mirrors). */
    fun isCleartextAllowedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return isCleartextHost(host.lowercase())
    }

    fun refererFor(imageUrl: String?, fallbackBase: String = ActiveProvider.baseUrl): String {
        val host = imageUrl?.toHttpUrlOrNull()?.host
        return if (!host.isNullOrBlank()) "https://$host/" else fallbackBase.trimEnd('/') + "/"
    }

    fun sanitizeTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val stripped = try {
            Jsoup.parse(raw).text()
        } catch (_: Exception) {
            raw.replace(Regex("<[^>]+>"), "")
        }
        return stripped
            .substringBefore(" stream")
            .substringBefore(" Stream")
            .substringBefore(" online")
            .substringBefore(" Online")
            .substringBefore(" anschauen")
            .substringBefore(" | ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
