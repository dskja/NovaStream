package com.novastream.app.util

import com.novastream.app.data.model.HomeCatalog
import com.novastream.app.data.model.LatestEpisode
import com.novastream.app.data.model.Series

/**
 * Heuristic parental filter for kids profiles.
 * Uses genres, title keywords, and optional [Series.isAdult] when available.
 */
object KidsContentFilter {

    private val blockedGenreTokens = setOf(
        "horror", "splatter", "gore", "thriller", "psychothriller", "krimi", "crime",
        "mystery", "erotik", "erotic", "adult", "18+", "18 ", "xxx", "slasher",
        "terror", "grusel", "mord", "noir", "snuff", "hentai", "ecchi"
    )

    private val blockedTitleTokens = setOf(
        "18+", "xxx", "porn", "erotik", "nude", "nackt", "adult only", "uncensored"
    )

    fun filterSeries(list: List<Series>, kidsMode: Boolean): List<Series> =
        if (!kidsMode) list else list.filter { isKidsSafe(it) }

    fun filterLatestEpisodes(list: List<LatestEpisode>, kidsMode: Boolean): List<LatestEpisode> =
        if (!kidsMode) list else list.filter { !isBlockedTitle(it.seriesTitle) }

    fun filterHomeCatalog(catalog: HomeCatalog, kidsMode: Boolean): HomeCatalog {
        if (!kidsMode) return catalog
        return HomeCatalog(
            hero = filterSeries(catalog.hero, true),
            popular = filterSeries(catalog.popular, true),
            newest = filterSeries(catalog.newest, true),
            trending = filterSeries(catalog.trending, true),
            latestEpisodes = filterLatestEpisodes(catalog.latestEpisodes, true),
            topShows = filterSeries(catalog.topShows, true),
            all = filterSeries(catalog.all, true)
        )
    }

    fun isKidsSafe(series: Series): Boolean {
        if (series.isAdult == true) return false
        if (isBlockedTitle(series.title) || isBlockedTitle(series.originalTitle.orEmpty())) return false
        if (series.genres.isEmpty()) return true
        return series.genres.none { genre -> matchesBlockedGenre(genre) }
    }

    fun isBlockedTitle(title: String): Boolean {
        val normalized = title.lowercase()
        return blockedTitleTokens.any { normalized.contains(it) }
    }

    private fun matchesBlockedGenre(genre: String): Boolean {
        val normalized = genre.lowercase()
        return blockedGenreTokens.any { token -> normalized.contains(token) }
    }
}
