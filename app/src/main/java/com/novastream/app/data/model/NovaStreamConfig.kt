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

    /** Max Anzahl Serien pro Seite. */
    const val MAX_SERIES_PER_PAGE = 60

    /** Baut eine absolute URL aus einem relativen Pfad. */
    fun abs(path: String): String =
        if (path.startsWith("http")) path else BASE_URL + path

    /** Prüft ob ein Pfad bereits absolut ist. */
    fun isAbsolute(path: String): Boolean =
        path.startsWith("http://") || path.startsWith("https://")
}
