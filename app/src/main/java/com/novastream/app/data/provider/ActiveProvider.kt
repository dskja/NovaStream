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
        synchronized(this) {
            current = provider
        }
    }

    fun setById(id: String) {
        synchronized(this) {
            val provider = ProviderManager.getProviderOrNull(id)
            current = provider ?: ProviderManager.defaultProvider
        }
    }

    val id: String get() = current.id
    val displayName: String get() = current.displayName
    val baseUrl: String get() = current.baseUrl
    val supportsSeries: Boolean get() = current.supportsSeries
    val supportsMovies: Boolean get() = !current.supportsSeries

    val isSerienStream: Boolean get() = current.id == "serienstream" || current.id == "serienstream_cx"
    val isAniWorld: Boolean get() = current.id == "aniworld"
    val isKinoGer: Boolean get() = current.id == "kinoger"
    val isBurningSeries: Boolean get() = current.id == "burningseries"
    val isMegaKino: Boolean get() = current.id == "megakino"
    val isStreamKiste: Boolean get() = current.id == "streamkiste"
    val isFilmPalast: Boolean get() = current.id == "filmpalast"
    val isKinoZ: Boolean get() = current.id == "kinoz"

    /** Baut eine Episode-URL für den aktiven Provider. */
    fun episodeUrl(slug: String, season: Int, episode: Int): String {
        return when (current.id) {
            "aniworld" -> "/anime/stream/$slug/staffel-$season/episode-$episode"
            "kinoger" -> "/stream/$slug.html"
            "burningseries" -> "/serie/$slug/$season/$episode"
            "megakino" -> "/title/$slug/staffel/$season/episode/$episode"
            "streamkiste" -> "/serien/$slug/staffel-$season/episode-$episode"
            "filmpalast" -> {
                val s = season.toString().padStart(2, '0')
                val e = episode.toString().padStart(2, '0')
                "/stream/$slug-s${s}e$e"
            }
            "kinoz" -> "/Stream/$slug.html"
            else -> "/serie/$slug/staffel-$season/episode-$episode"
        }
    }

    fun seasonUrl(slug: String, season: Int): String {
        return when (current.id) {
            "aniworld" -> "/anime/stream/$slug/staffel-$season"
            "kinoger" -> "/stream/$slug.html"
            "burningseries" -> "/serie/$slug/$season"
            "megakino" -> "/title/$slug/staffel/$season"
            "streamkiste" -> "/serien/$slug/staffel-$season"
            "filmpalast" -> "/stream/$slug"
            "kinoz" -> "/Stream/$slug.html"
            else -> "/serie/$slug/staffel-$season"
        }
    }

    fun seriesDetailUrl(slug: String): String {
        return when (current.id) {
            "aniworld" -> "/anime/stream/$slug"
            "kinoger" -> "/stream/$slug.html"
            "burningseries" -> "/serie/$slug"
            "megakino" -> "/title/$slug"
            "streamkiste" -> "/serien/$slug"
            "filmpalast" -> "/stream/$slug"
            "kinoz" -> "/Stream/$slug.html"
            else -> "/serie/$slug"
        }
    }
}
