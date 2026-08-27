package com.novastream.app.data.provider

/**
 * Hält den aktuell aktiven Provider als Singleton.
 * Wird von SettingsScreen aktualisiert wenn der User den Provider wechselt.
 * ViewModels lesen den Provider von hier.
 */
object ActiveProvider {
    @Volatile
    private var current: StreamingProvider = ProviderManager.defaultProvider

    fun get(): StreamingProvider = current

    fun set(provider: StreamingProvider) {
        current = provider
    }

    /** Setzt den Provider anhand der ID. Fallback auf Default bei ungültiger ID. */
    fun setById(id: String) {
        val provider = ProviderManager.getProviderOrNull(id)
        if (provider != null) {
            current = provider
        } else {
            current = ProviderManager.defaultProvider
        }
    }

    /** Aktuelle Provider-ID. */
    val id: String get() = current.id

    /** Aktueller Provider-Display-Name. */
    val displayName: String get() = current.displayName

    /** Aktuelle Provider-Base-URL. */
    val baseUrl: String get() = current.baseUrl

    /** True wenn der aktuelle Provider Serien unterstützt. */
    val supportsSeries: Boolean get() = current.supportsSeries

    /** True wenn der aktuelle Provider Filme unterstützt. */
    val supportsMovies: Boolean get() = !current.supportsSeries

    /** True wenn SerienStream der aktive Provider ist. */
    val isSerienStream: Boolean get() = current.id == "serienstream"

    /** True wenn AniWorld der aktive Provider ist. */
    val isAniWorld: Boolean get() = current.id == "aniworld"

    /** True wenn KinoGer der aktive Provider ist. */
    val isKinoGer: Boolean get() = current.id == "kinoger"

    /** Baut eine Episode-URL für den aktiven Provider. */
    fun episodeUrl(slug: String, season: Int, episode: Int): String {
        return when (current.id) {
            "aniworld" -> "/anime/stream/$slug/staffel-$season/episode-$episode"
            "kinoger" -> "/stream/$slug.html"
            else -> "/serie/$slug/staffel-$season/episode-$episode"
        }
    }

    /** Baut eine Staffel-URL für den aktiven Provider. */
    fun seasonUrl(slug: String, season: Int): String {
        return when (current.id) {
            "aniworld" -> "/anime/stream/$slug/staffel-$season"
            "kinoger" -> "/stream/$slug.html"
            else -> "/serie/$slug/staffel-$season"
        }
    }

    /** Baut eine Serien-Detail-URL für den aktiven Provider. */
    fun seriesDetailUrl(slug: String): String {
        return when (current.id) {
            "aniworld" -> "/anime/stream/$slug"
            "kinoger" -> "/stream/$slug.html"
            else -> "/serie/$slug"
        }
    }
}
