package com.novastream.app.data.model

/** Konfiguration für NovaStream. */
object NovaStreamConfig {
    /** Basis-URL des Anbieters. */
    const val BASE_URL = "https://serienstream.to"
    const val SEARCH_PATH = "/suche"
    const val HOME_PATH = "/"
    /** User-Agent, der für Requests genutzt wird (aktualisiert für 2025). */
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /** Netzwerk-Timeouts in Millisekunden. */
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val READ_TIMEOUT_MS = 20_000L
    const val WRITE_TIMEOUT_MS = 20_000L

    /** Hoster-Resolver Timeout in Millisekunden. */
    const val HOSTER_RESOLVE_TIMEOUT_MS = 15_000L

    /** Redirect-Resolver Timeout (für JS-Redirects). */
    const val REDIRECT_TIMEOUT_MS = 10_000L

    /** WebView Timeout für VOE-Resolver. */
    const val WEBVIEW_TIMEOUT_MS = 15_000L

    /** Max Anzahl Serien pro Seite. */
    const val MAX_SERIES_PER_PAGE = 60

    /** Bekannte Genres für Home/Browse (SerienStream-Slugs). */
    val DEFAULT_GENRES = listOf(
        "action", "comedy", "drama", "science-fiction", "thriller",
        "horror", "fantasy", "krimi", "mystery", "anime", "romantik", "abenteuer"
    )

    /** Max Anzahl Hoster die probiert werden (falls erste fehlschlagen). */
    const val MAX_HOSTER_ATTEMPTS = 5

    /** Retry-Verzögerung zwischen Hoster-Versuchen. */
    const val HOSTER_RETRY_DELAY_MS = 500L

    /** Baut eine absolute URL aus einem relativen Pfad. */
    fun abs(path: String): String =
        if (path.startsWith("http")) path else BASE_URL + path

    /** Prüft ob ein Pfad bereits absolut ist. */
    fun isAbsolute(path: String): Boolean =
        path.startsWith("http://") || path.startsWith("https://")

    /** Baut eine absolute URL mit einer beliebigen Base-URL. */
    fun absWith(base: String, path: String): String =
        if (path.startsWith("http")) path else base.trimEnd('/') + "/" + path.trimStart('/')

    /** True wenn die URL ein Video-Stream ist (m3u8, mp4, webm). */
    fun isVideoUrl(url: String): Boolean =
        url.contains(".m3u8") || url.contains(".mp4") || url.contains(".webm")

    /** True wenn die URL eine Test/Placeholder-Video ist. */
    fun isTestVideo(url: String): Boolean =
        url.contains("test-videos") || url.contains("bigbuckbunny") || url.contains("sample-")
}
