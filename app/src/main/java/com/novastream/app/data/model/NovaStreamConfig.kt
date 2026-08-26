package com.novastream.app.data.model

/** Konfiguration für NovaStream. */
object NovaStreamConfig {
    /** Basis-URL des Anbieters. */
    const val BASE_URL = "https://serienstream.to"
    const val SEARCH_PATH = "/suche"
    const val HOME_PATH = "/"
    /** User-Agent, der für Requests genutzt wird. */
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Baut eine absolute URL aus einem relativen Pfad. */
    fun abs(path: String): String =
        if (path.startsWith("http")) path else BASE_URL + path
}
