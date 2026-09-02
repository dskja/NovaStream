package com.novastream.app.util

import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.model.HomeCatalog
import com.novastream.app.data.model.LatestEpisode
import com.novastream.app.data.model.Series

/**
 * Heuristic parental filter for kids profiles.
 * Uses genres, title keywords, slug patterns, and optional [Series.isAdult] when available.
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

    private val blockedSlugTokens = setOf(
        "horror", "erotik", "xxx", "adult", "hentai", "krimi", "thriller", "slasher", "terror"
    )

    fun filterSeries(list: List<Series>, kidsMode: Boolean): List<Series> =
        if (!kidsMode) list else list.filter { isKidsSafe(it) }

    fun filterWatchlist(list: List<WatchlistItem>, kidsMode: Boolean): List<WatchlistItem> =
        if (!kidsMode) list else list.filter { isKidsSafeWatchlist(it) }

    fun filterProgress(list: List<WatchProgress>, kidsMode: Boolean): List<WatchProgress> =
        if (!kidsMode) list else list.filter { isKidsSafeProgress(it) }

    fun filterLatestEpisodes(list: List<LatestEpisode>, kidsMode: Boolean): List<LatestEpisode> =
        if (!kidsMode) list else list.filter { !isBlockedTitle(it.seriesTitle) && !isBlockedSlug(it.seriesSlug) }

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

    fun filterDownloads(list: List<com.novastream.app.data.db.DownloadEntity>, kidsMode: Boolean): List<com.novastream.app.data.db.DownloadEntity> =
        if (!kidsMode) list else list.filter { isKidsSafeDownload(it) }

    fun isKidsSafeDownload(item: com.novastream.app.data.db.DownloadEntity): Boolean =
        !isBlockedForKidsPlayback(item.slug, item.title, item.episodeTitle)

    fun isKidsSafe(series: Series): Boolean {
        if (series.isAdult == true) return false
        if (isBlockedTitle(series.title) || isBlockedTitle(series.originalTitle.orEmpty())) return false
        if (isBlockedSlug(series.id)) return false
        if (series.genres.isEmpty()) return true
        return series.genres.none { genre -> matchesBlockedGenre(genre) }
    }

    fun isKidsSafeWatchlist(item: WatchlistItem): Boolean {
        if (isBlockedTitle(item.title)) return false
        if (isBlockedSlug(item.slug)) return false
        return true
    }

    fun isKidsSafeProgress(progress: WatchProgress): Boolean =
        !isBlockedForKidsPlayback(progress.slug, progress.seriesTitle, progress.episodeTitle)

    /** Blocks direct playback when only slug/title metadata is available (e.g. player deep links). */
    fun isBlockedForKidsPlayback(slug: String, seriesTitle: String, episodeTitle: String = ""): Boolean {
        if (isBlockedSlug(slug)) return true
        if (isBlockedTitle(seriesTitle)) return true
        if (isBlockedTitle(episodeTitle)) return true
        return false
    }

    fun isBlockedTitle(title: String): Boolean {
        if (title.isBlank()) return false
        val normalized = title.lowercase()
        return blockedTitleTokens.any { normalized.contains(it) }
    }

    fun isBlockedSlug(slug: String): Boolean {
        if (slug.isBlank()) return false
        val normalized = slug.lowercase()
        return blockedSlugTokens.any { normalized.contains(it) }
    }

    private fun matchesBlockedGenre(genre: String): Boolean {
        val normalized = genre.lowercase()
        return blockedGenreTokens.any { token -> normalized.contains(token) }
    }
}
