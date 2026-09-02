package com.novastream.app.data.provider

/**
 * Hält den aktuell aktiven Provider als Singleton.
 * Alle Katalog-/Detail-Aufrufe MÜSSEN über [get] laufen – nie hardcodierte Base-URLs.
 */
object ActiveProvider {
    @Volatile
    private var current: StreamingProvider? = null

    @Volatile
    private var initialized: Boolean = false

    fun get(): StreamingProvider {
        current?.let { return it }
        return ProviderManager.defaultProvider.also { current = it }
    }

    fun isInitialized(): Boolean = initialized

    fun set(provider: StreamingProvider) {
        synchronized(this) {
            val previous = current
            current = provider
            initialized = true
            if (previous != null && previous.id != provider.id) {
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
            if (previous != null && previous.id != next.id) {
                when (previous) {
                    is KinoGerProvider -> previous.clearCache()
                    is KinoZProvider -> previous.clearCache()
                }
            }
        }
    }

    val id: String get() = get().id
    val displayName: String get() = get().displayName
    val baseUrl: String get() = get().baseUrl
    val supportsSeries: Boolean get() = get().supportsSeries
    val supportsMovies: Boolean get() = get().supportsMovies
    val availableGenres get() = get().availableGenres
    val catalogHint: String? get() = get().catalogHint

    val isSerienStream: Boolean get() = get().id == "serienstream" || get().id == "serienstream_cx"
    val isAniWorld: Boolean get() = get().id == "aniworld"
    val isKinoGer: Boolean get() = get().id == "kinoger"
    val isBurningSeries: Boolean get() = get().id == "burningseries"
    val isMegaKino: Boolean get() = get().id == "megakino"
    val isStreamKiste: Boolean get() = get().id == "streamkiste"
    val isFilmPalast: Boolean get() = get().id == "filmpalast"
    val isKinoZ: Boolean get() = get().id == "kinoz"
    val isFreeCatalog: Boolean get() = get().id == "freecatalog"

    fun episodeUrl(slug: String, season: Int, episode: Int): String {
        return when (get().id) {
            "aniworld" -> "/anime/stream/$slug/staffel-$season/episode-$episode"
            "kinoger" -> "/stream/$slug.html"
            "burningseries" -> "/serie/$slug/$season/$episode"
            "megakino" -> {
                val path = if (slug.contains("/")) slug.trimStart('/') else "serials/$slug"
                "/$path#ep$episode"
            }
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
        return when (get().id) {
            "aniworld" -> "/anime/stream/$slug/staffel-$season"
            "kinoger" -> "/stream/$slug.html"
            "burningseries" -> "/serie/$slug/$season"
            "megakino" -> "/$slug"
            "streamkiste" -> "/serien/$slug/staffel-$season"
            "filmpalast" -> "/stream/$slug"
            "kinoz" -> "/Stream/$slug.html"
            "freecatalog" -> "/shows/$slug"
            else -> "/serie/$slug/staffel-$season"
        }
    }

    fun seriesDetailUrl(slug: String): String =
        ProviderUrls.seriesDetailUrl(get().id, slug)

    fun movieDetailUrl(slug: String): String =
        ProviderUrls.movieDetailUrl(get().id, slug)
}
