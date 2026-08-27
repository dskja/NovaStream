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

    /** True wenn es ein HLS-Stream ist (m3u8). */
    val isHlsStream: Boolean get() = isHls || url.contains(".m3u8")

    /** True wenn es ein MP4-Stream ist. */
    val isMp4Stream: Boolean get() = !isHls && url.contains(".mp4")

    /** True wenn es ein WebM-Stream ist. */
    val isWebmStream: Boolean get() = url.contains(".webm")

    /** Quality-Ranking (höher = besser) für Sortierung. */
    val qualityRank: Int
        get() = when (qualityHint) {
            "1080p" -> 4
            "720p" -> 3
            "480p" -> 2
            "360p" -> 1
            else -> 0
        }

    /** True wenn die URL eine Test/Placeholder-Video ist. */
    val isTestVideo: Boolean
        get() = NovaStreamConfig.isTestVideo(url)

    /** Datei-Endung der Stream-URL. */
    val fileExtension: String
        get() = when {
            isHlsStream -> "m3u8"
            isMp4Stream -> "mp4"
            isWebmStream -> "webm"
            else -> "unknown"
        }
}
