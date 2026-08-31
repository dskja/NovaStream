package com.novastream.app.data.provider

/**
 * Hält den aktuell aktiven Provider als Singleton.
 * Alle Katalog-/Detail-Aufrufe MÜSSEN über [get] laufen – nie hardcodierte Base-URLs.
 */
object ActiveProvider {
    @Volatile
    private var current: StreamingProvider = ProviderManager.defaultProvider

    @Volatile
    private var initialized: Boolean = false

    fun get(): StreamingProvider = current

    fun isInitialized(): Boolean = initialized

    fun set(provider: StreamingProvider) {
        synchronized(this) {
            val previous = current
            current = provider
            initialized = true
            if (previous.id != provider.id) {
                when (previous) {
                    is KinoGerProvider -> previous.clearCache()
                    is KinoZProvider -> previous.clearCache()
                }
            }
        }
    }

    fun setById(id: String) {
        synchronized(this) {
            val previous = current
            val next = ProviderManager.getProviderOrNull(id) ?: ProviderManager.defaultProvider
            current = next
            initialized = true
            if (previous.id != next.id) {
                when (previous) {
                    is KinoGerProvider -> previous.clearCache()
                    is KinoZProvider -> previous.clearCache()
                }
            }
        }
    }

    val id: String get() = current.id
    val displayName: String get() = current.displayName
    val baseUrl: String get() = current.baseUrl
    val supportsSeries: Boolean get() = current.supportsSeries
    val supportsMovies: Boolean get() = current.supportsMovies
    val availableGenres get() = current.availableGenres
    val catalogHint: String? get() = current.catalogHint

    val isSerienStream: Boolean get() = current.id == "serienstream" || current.id == "serienstream_cx"
    val isAniWorld: Boolean get() = current.id == "aniworld"
    val isKinoGer: Boolean get() = current.id == "kinoger"
    val isBurningSeries: Boolean get() = current.id == "burningseries"
    val isMegaKino: Boolean get() = current.id == "megakino"
    val isStreamKiste: Boolean get() = current.id == "streamkiste"
    val isFilmPalast: Boolean get() = current.id == "filmpalast"
    val isKinoZ: Boolean get() = current.id == "kinoz"
    val isFreeCatalog: Boolean get() = current.id == "freecatalog"

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
            "freecatalog" -> "imdb://$slug/$season/$episode"
            "cinezo", "showsst" -> {
                val id = slug.removePrefix("tv-").removePrefix("movie-")
                if (slug.startsWith("movie")) "/movie/$id" else "/watch/tv/$id"
            }
            "hydrahd" -> "/watchseries/$slug"
            "dramacool" -> "/$slug-episode-$episode/"
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
            "freecatalog" -> "/shows/$slug"
            else -> "/serie/$slug/staffel-$season"
        }
    }

    fun seriesDetailUrl(slug: String): String =
        ProviderUrls.seriesDetailUrl(current.id, slug)

    fun movieDetailUrl(slug: String): String =
        ProviderUrls.movieDetailUrl(current.id, slug)
}
