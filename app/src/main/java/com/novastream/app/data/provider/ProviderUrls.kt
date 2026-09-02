package com.novastream.app.data.provider

/**
 * Provider-spezifische URL-Helfer – zentral statt hardcodiert in [ActiveProvider].
 */
object ProviderUrls {

    fun seriesDetailUrl(providerId: String, slug: String): String = when (providerId) {
        "aniworld" -> "/anime/stream/$slug"
        "kinoger" -> "/stream/$slug.html"
        "burningseries" -> "/serie/$slug"
        "megakino" -> if (slug.contains("/")) "/$slug" else "/serials/$slug"
        "streamkiste" -> "/serien/$slug"
        "filmpalast" -> "/stream/$slug"
        "kinoz" -> "/Stream/$slug.html"
        "freecatalog" -> "/shows/$slug"
        "cinezo" -> if (slug.startsWith("movie")) "/movie/${slug.removePrefix("movie-")}" else "/tv/${slug.removePrefix("tv-")}"
        "showsst" -> "/watch/tv/${slug.removePrefix("tv-")}"
        "hydrahd" -> "/watchseries/$slug"
        "dramacool" -> "/$slug/"
        else -> {
            val resolved = ProviderDetailUrls.resolve(providerId, "", slug)
            if (resolved.startsWith("/")) resolved else "/$resolved"
        }
    }

    fun movieDetailUrl(providerId: String, slug: String): String = when (providerId) {
        "streamkiste" -> "/filme/$slug"
        "kinoger" -> "/stream/$slug.html"
        "filmpalast" -> "/stream/$slug"
        "megakino" -> if (slug.contains("/")) "/$slug" else "/films/$slug"
        "kinoz" -> "/Stream/$slug.html"
        "cinezo" -> "/movie/${slug.removePrefix("movie-")}"
        "showsst" -> "/watch/movie/${slug.removePrefix("movie-")}"
        "hydrahd" -> "/movie/$slug"
        else -> {
            val resolved = ProviderDetailUrls.resolve(providerId, "", "movie-$slug")
            when {
                resolved.contains("/movie") -> resolved.removePrefix("https://example.com").ifBlank { "/movie/$slug" }
                else -> "/movie/$slug"
            }
        }
    }

    fun detailUrl(providerId: String, slug: String, isMovie: Boolean): String =
        if (isMovie) movieDetailUrl(providerId, slug) else seriesDetailUrl(providerId, slug)

    fun isMovieSlug(providerId: String, slug: String): Boolean =
        slug.startsWith("movie-") || slug.startsWith("movie/")
}
