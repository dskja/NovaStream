package com.novastream.app.util

import com.novastream.app.data.provider.ActiveProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Absolute Cover-/Media-URLs und Referer-Header pro Provider.
 * Verhindert, dass Bilder von Provider A mit Base-URL von Provider B gebaut werden.
 */
object MediaUrls {

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

    fun refererFor(imageUrl: String?, fallbackBase: String = ActiveProvider.baseUrl): String {
        val host = imageUrl?.toHttpUrlOrNull()?.host
        return if (!host.isNullOrBlank()) "https://$host/" else fallbackBase.trimEnd('/') + "/"
    }

    fun sanitizeTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
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
