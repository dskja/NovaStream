package com.novastream.app.data.provider

/**
 * Provider-specific detail URL resolution for [ConfigurableSiteProvider].
 * Keeps slug→URL rules in one place instead of scattered when-blocks.
 */
object ProviderDetailUrls {

  fun resolve(providerId: String, base: String, slug: String): String {
    val trimmed = slug.trim()
    return when {
      trimmed.startsWith("tv-") -> "$base/tv/${trimmed.removePrefix("tv-")}"
      trimmed.startsWith("movie-") -> when (providerId) {
        "streamkiste" -> "$base/filme/${trimmed.removePrefix("movie-")}"
        else -> "$base/movie/${trimmed.removePrefix("movie-")}"
      }
      trimmed.startsWith("http") -> trimmed
      trimmed.startsWith("/") -> base + trimmed
      else -> providerSpecific(providerId, base, trimmed)
    }
  }

  private fun providerSpecific(providerId: String, base: String, slug: String): String = when (providerId) {
    "showsst" -> "$base/watch/tv/$slug"
    "hydrahd" -> if (slug.contains("watch-") || slug.contains("-online")) {
      "$base/movie/$slug"
    } else {
      "$base/watchseries/$slug"
    }
    "dramacool" -> "$base/$slug/"
    "sflix", "ridomovies" -> when {
      slug.startsWith("movie-") -> "$base/movie/${slug.removePrefix("movie-")}"
      slug.startsWith("tv-") -> "$base/tv-show/${slug.removePrefix("tv-")}"
      slug.contains("movie") -> "$base/movie/$slug"
      else -> "$base/tv-show/$slug"
    }
    "hianime", "anikoto" -> "$base/watch/$slug"
    "animeflv", "jkanime", "animefenix", "tioanime", "latanime" -> "$base/anime/$slug"
    "aniworld" -> "$base/anime/stream/$slug"
    "serienstream", "serienstream_cx", "burningseries" -> "$base/serie/$slug"
    "filmpalast" -> "$base/stream/$slug"
    "kinoger" -> "$base/stream/$slug.html"
    "megakino", "hdfilme", "moflix" -> when {
      slug.contains("/films/") || slug.contains("/serials/") -> "$base/$slug"
      slug.endsWith(".html") -> "$base/$slug"
      else -> "$base/serials/$slug"
    }
    "streamkiste" -> when {
      slug.startsWith("movie-") -> "$base/filme/${slug.removePrefix("movie-")}"
      else -> "$base/serien/$slug"
    }
    "kinoz" -> "$base/Stream/$slug.html"
    "wiflix" -> "$base/serie/$slug"
    "frenchstream" -> "$base/s-tv/$slug"
    "cuevana3", "pelisflix", "flixlatam", "cinecalidad" -> "$base/pelicula/$slug"
    "fanpelis", "pelisplusto", "seriesflix" -> "$base/serie/$slug"
    "doramasflix" -> "$base/doramas-online/$slug"
    "guardaserie", "cb01", "altadefinizione01", "cineblog01" -> "$base/serietv/$slug"
    "streamingcommunity_it", "streamingcommunity_en" -> "$base/titles/$slug"
    "filmyonline" -> "$base/titles/$slug"
    "zaluknij" -> "$base/serial-online/$slug"
    "lookmovie2", "anymovie", "mkissa", "mkvmovies" -> "$base/movies/$slug"
    "soap2day" -> "$base/movie/$slug"
    else -> "$base/$slug"
  }
}
