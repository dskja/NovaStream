package com.novastream.app.util

import com.novastream.app.data.provider.ActiveProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/**
 * Absolute Cover-/Media-URLs and Referer-Header per provider.
 * Prevents images from provider A being built with provider B base URL.
 */
object MediaUrls {

    fun abs(pathOrUrl: String?, baseUrl: String = ActiveProvider.baseUrl): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        val src = pathOrUrl.trim()
        if (src.contains("data:image") || src.endsWith(".svg", ignoreCase = true)) return null
        return when {
            src.startsWith("http://") || src.startsWith("https://") -> secureUrl(src)
            src.startsWith("//") -> secureUrl("https:$src")
            else -> secureUrl(baseUrl.trimEnd('/') + "/" + src.trimStart('/'))
        }
    }

    /** Prefer HTTPS; many hosters redirect HTTP which Android blocks without cleartext config. */
    fun secureUrl(url: String): String {
        if (url.startsWith("http://")) {
            return "https://" + url.removePrefix("http://")
        }
        return url
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
