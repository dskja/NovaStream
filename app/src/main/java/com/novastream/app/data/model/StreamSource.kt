package com.novastream.app.data.model

/** Auflösbare Stream-URL eines Hosters. */
data class StreamSource(
    val hoster: String,
    val url: String,
    val mimeType: String = "application/x-mpegURL",
    val isHls: Boolean = true
) {
    /** True wenn die URL nicht leer und abspielbar ist. */
    val isPlayable: Boolean
        get() = url.isNotBlank() && url.startsWith("http")

    /** Display-Name für UI: "VOE (HLS)" oder "Streamtape (MP4)". */
    val displayName: String
        get() = "$hoster (${if (isHls) "HLS" else "MP4"})"

    /** Quality hint basierend auf URL-Patterns (z.B. 1080p, 720p). */
    val qualityHint: String?
        get() = when {
            url.contains("1080", ignoreCase = true) -> "1080p"
            url.contains("720", ignoreCase = true) -> "720p"
            url.contains("480", ignoreCase = true) -> "480p"
            url.contains("360", ignoreCase = true) -> "360p"
            else -> null
        }
}
