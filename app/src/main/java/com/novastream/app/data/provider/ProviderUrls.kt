package com.novastream.app.data.provider

/**
 * Provider-spezifische URL-Helfer – zentral statt hardcodiert in [ActiveProvider].
 */
object ProviderUrls {

    fun seriesDetailUrl(providerId: String, slug: String): String {
        val path = ProviderDetailUrls.resolve(providerId, "", slug).removePrefix("/")
        return if (path.startsWith("http")) path else "/$path"
    }

    fun movieDetailUrl(providerId: String, slug: String): String = when (providerId) {
        "streamkiste" -> "/filme/$slug"
        "hydrahd" -> "/movie/$slug"
        else -> {
            val resolved = ProviderDetailUrls.resolve(providerId, "", "movie-$slug")
            if (resolved.contains("/movie")) resolved.removePrefix("https://example.com") else "/movie/$slug"
        }
    }

    fun detailUrl(providerId: String, slug: String, isMovie: Boolean): String =
        if (isMovie) movieDetailUrl(providerId, slug) else seriesDetailUrl(providerId, slug)

    fun isMovieSlug(providerId: String, slug: String): Boolean =
        slug.startsWith("movie-") || slug.startsWith("movie/")
}
