package com.novastream.app.data.meta

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Aggregates anime metadata from AniList, Kitsu, Shikimori, and Jikan (all free, no key).
 */
object AnimeMetaAggregator {

    suspend fun search(query: String, limit: Int = 20): List<MetaShow> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val perSource = (limit / 4).coerceAtLeast(3)
        val anilist = async { runCatching { AniListMetaService.search(query, perSource) }.getOrDefault(emptyList()) }
        val kitsu = async { runCatching { KitsuMetaService.search(query, perSource) }.getOrDefault(emptyList()) }
        val shikimori = async { runCatching { ShikimoriMetaService.search(query, perSource) }.getOrDefault(emptyList()) }
        val jikan = async { runCatching { JikanMetaService.search(query, perSource) }.getOrDefault(emptyList()) }
        (anilist.await() + kitsu.await() + shikimori.await() + jikan.await())
            .distinctBy { dedupeKey(it) }
            .take(limit)
    }

    suspend fun pickBest(title: String): MetaShow? {
        val candidates = search(title, limit = 12)
        return candidates.firstOrNull { FreeMetaService.titlesSimilar(title, it.title) }
            ?: candidates.firstOrNull()
    }

    suspend fun enrichAnime(show: MetaShow): MetaShow {
        var merged = show
        show.anilistId?.let { id ->
            AniListMetaService.mediaById(id)?.let { merged = mergeShows(merged, it) }
        }
        if (merged.idMal == null && show.idMal != null) {
            JikanMetaService.animeById(show.idMal)?.let { merged = mergeShows(merged, it) }
        }
        show.kitsuId?.let { id ->
            KitsuMetaService.animeById(id)?.let { merged = mergeShows(merged, it) }
        }
        show.shikimoriId?.let { id ->
            ShikimoriMetaService.animeById(id)?.let { merged = mergeShows(merged, it) }
        }
        if (merged.idMal == null && merged.anilistId != null) {
            AniListMetaService.mediaById(merged.anilistId)?.let { fromAni ->
                if (fromAni.idMal != null) merged = mergeShows(merged, fromAni)
            }
        }
        if (merged.idMal == null && merged.shikimoriId != null) {
            merged = mergeShows(merged, MetaShow(id = merged.id, title = merged.title, idMal = merged.shikimoriId))
        }
        return merged
    }

    fun mergeShows(primary: MetaShow, secondary: MetaShow): MetaShow = primary.copy(
        title = primary.title.ifBlank { secondary.title },
        summary = primary.summary?.takeIf { it.isNotBlank() } ?: secondary.summary,
        genres = (primary.genres + secondary.genres).distinct(),
        posterUrl = primary.posterUrl ?: secondary.posterUrl,
        backdropUrl = primary.backdropUrl ?: secondary.backdropUrl,
        rating = primary.rating ?: secondary.rating,
        premiered = primary.premiered ?: secondary.premiered,
        status = primary.status ?: secondary.status,
        imdbId = primary.imdbId ?: secondary.imdbId,
        anilistId = primary.anilistId ?: secondary.anilistId,
        idMal = primary.idMal ?: secondary.idMal,
        kitsuId = primary.kitsuId ?: secondary.kitsuId,
        shikimoriId = primary.shikimoriId ?: secondary.shikimoriId,
        network = primary.network ?: secondary.network,
        runtime = primary.runtime ?: secondary.runtime?.takeIf { it > 0 },
        officialSite = primary.officialSite ?: secondary.officialSite,
        trailerUrl = primary.trailerUrl ?: secondary.trailerUrl,
        cast = (primary.cast + secondary.cast).distinctBy { it.name.lowercase() },
        isAdult = AgeRatingResolver.mergeIsAdult(primary.isAdult, secondary.isAdult),
        contentRating = primary.contentRating ?: secondary.contentRating,
        contentRatingSource = primary.contentRatingSource ?: secondary.contentRatingSource,
        similar = primary.similar.ifEmpty { secondary.similar }
    )

    fun dedupeKey(show: MetaShow): String {
        show.idMal?.let { return "mal:$it" }
        show.anilistId?.let { return "anilist:$it" }
        show.kitsuId?.let { return "kitsu:$it" }
        show.shikimoriId?.let { return "shikimori:$it" }
        return "title:${normalizeTitle(show.title)}|${show.year.orEmpty()}"
    }

    private fun normalizeTitle(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9äöüß]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
