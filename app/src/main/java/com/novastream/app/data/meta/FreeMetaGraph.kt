package com.novastream.app.data.meta

import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ContentRegionResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Unified metadata facade over free no-key sources:
 * TVMaze + Epguides (TV), AniList + Kitsu + Shikimori + Jikan (anime), Wikidata + Wikipedia (IDs/FSK).
 */
@Singleton
class FreeMetaGraph @Inject constructor() {

    suspend fun search(query: String, preferAnime: Boolean = false, limit: Int = 30): List<MetaShow> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        if (preferAnime) {
            val anime = async { AnimeMetaAggregator.search(query, limit) }
            val tvmaze = async { FreeMetaService.search(query, limit = limit / 2) }
            return@coroutineScope (anime.await() + tvmaze.await()).distinctBy { dedupeKeyForShow(it) }.take(limit)
        }
        val tvmaze = async { FreeMetaService.search(query, limit = limit) }
        val epguides = async { EpguidesMetaService.search(query, limit / 3) }
        (tvmaze.await() + epguides.await()).distinctBy { dedupeKeyForShow(it) }.take(limit)
    }

    suspend fun searchAnime(query: String, limit: Int = 20): List<MetaShow> =
        AnimeMetaAggregator.search(query, limit)

    suspend fun seasonsWithEpisodes(
        title: String,
        tvmazeId: String? = null,
        epguidesKey: String? = null
    ): List<MetaSeason> = MetaEpisodeResolver.seasonsWithEpisodes(title, tvmazeId, epguidesKey)

    suspend fun enrich(
        title: String,
        isMovie: Boolean = false,
        preferAnime: Boolean = false,
        language: ContentLanguage = ContentLanguage.EN,
        tvmazeIdHint: String? = null,
        imdbHint: String? = null,
        anilistIdHint: Int? = null,
        scrapedIsAdult: Boolean? = null
    ): MetaEnrichment? {
        if (title.isBlank() && tvmazeIdHint.isNullOrBlank() && anilistIdHint == null) return null

        val langTag = ContentRegionResolver.wikidataLanguageFor(language)

        if (anilistIdHint != null && anilistIdHint > 0) {
            AniListMetaService.mediaById(anilistIdHint)?.let {
                val enriched = AnimeMetaAggregator.enrichAnime(it)
                return buildEnrichment(enriched, preferAnime, langTag, language, scrapedIsAdult)
            }
        }

        if (preferAnime) {
            val animeMatch = AnimeMetaAggregator.pickBest(title)
            if (animeMatch != null) return buildEnrichment(animeMatch, preferAnime = true, langTag, language, scrapedIsAdult)
        }

        val tvmazeShow = when {
            !tvmazeIdHint.isNullOrBlank() -> FreeMetaService.show(tvmazeIdHint)
            else -> FreeMetaService.enrichByTitle(title, preferAnime = preferAnime)
        }

        if (tvmazeShow != null && (tvmazeIdHint != null || FreeMetaService.titlesSimilar(title, tvmazeShow.title))) {
            val withCast = if (tvmazeShow.cast.isEmpty()) {
                FreeMetaService.show(tvmazeShow.id) ?: tvmazeShow
            } else tvmazeShow
            return buildEnrichmentFromTvmaze(withCast, langTag, imdbHint, language, scrapedIsAdult)
        }

        if (!isMovie && preferAnime) {
            AnimeMetaAggregator.pickBest(title)?.let {
                return buildEnrichment(it, preferAnime = true, langTag, language, scrapedIsAdult)
            }
        }

        val epguidesMatch = EpguidesMetaService.search(title, limit = 3)
            .firstOrNull { FreeMetaService.titlesSimilar(title, it.title) }
        if (epguidesMatch != null) {
            val stub = epguidesMatch.copy(mediaType = "tv")
            val ids = ExternalIds(epguidesKey = stub.epguidesKey)
            val (ratedShow, ageRating) = attachAgeRating(stub, ids, language, scrapedIsAdult)
            return MetaEnrichment(
                show = ratedShow,
                externalIds = ids,
                canonicalKey = ids.canonicalKey(),
                ageRating = ageRating
            )
        }

        val wikidataIds = WikidataMetaService.resolveExternalIds(
            title = title,
            language = langTag,
            imdbHint = imdbHint
        )
        if (wikidataIds.canonicalKey() != null) {
            val stub = MetaShow(
                id = wikidataIds.wikidataId?.let { "wikidata-$it" } ?: title,
                title = title,
                imdbId = wikidataIds.imdbId,
                tmdbId = wikidataIds.tmdbId,
                wikidataId = wikidataIds.wikidataId,
                mediaType = if (isMovie) "movie" else "tv"
            )
            val (ratedShow, ageRating) = attachAgeRating(stub, wikidataIds, language, scrapedIsAdult)
            return MetaEnrichment(
                show = ratedShow,
                externalIds = wikidataIds,
                canonicalKey = wikidataIds.canonicalKey(),
                ageRating = ageRating
            )
        }

        return null
    }

    suspend fun enrichBySeries(series: Series, preferAnime: Boolean = false, language: ContentLanguage): MetaEnrichment? {
        val cacheKey = MetaEnrichmentCache.cacheKey(series)
        MetaEnrichmentCache.get(cacheKey)?.let { return it }
        val tvmazeHint = series.tvmazeId ?: series.id.takeIf { it.all(Char::isDigit) }
        val anilistHint = series.anilistId
        val result = enrich(
            title = series.title,
            isMovie = series.isMovie,
            preferAnime = preferAnime,
            language = language,
            tvmazeIdHint = tvmazeHint,
            imdbHint = series.imdbId,
            anilistIdHint = anilistHint,
            scrapedIsAdult = series.isAdult
        )
        if (result != null) MetaEnrichmentCache.put(cacheKey, result)
        return result
    }

    suspend fun episodesForSeason(
        title: String,
        tvmazeId: String? = null,
        epguidesKey: String? = null,
        season: Int,
        idMal: Int? = null,
        anilistId: Int? = null
    ): List<MetaEpisode> = MetaEpisodeResolver.episodes(title, tvmazeId, epguidesKey, season, idMal, anilistId)

    suspend fun similar(show: MetaShow, limit: Int = 20): List<MetaShow> {
        if (show.similar.isNotEmpty()) return show.similar.take(limit)
        show.anilistId?.let { id ->
            val related = AniListMetaService.similar(id, limit)
            if (related.isNotEmpty()) return related
        }
        show.idMal?.let { id ->
            val jikan = JikanMetaService.animeById(id)
            if (jikan != null && show.genres.isNotEmpty()) {
                return JikanMetaService.search(show.genres.first(), limit)
                    .filter { it.idMal != id }
            }
        }
        val genre = show.genres.firstOrNull() ?: return emptyList()
        return FreeMetaService.search(genre, limit = limit)
            .filter { it.id != show.id && FreeMetaService.titlesSimilar(genre, it.genres.firstOrNull() ?: "") || show.genres.any { g -> it.genres.any { ig -> ig.equals(g, true) } } }
            .take(limit)
    }

    suspend fun browseRegional(language: ContentLanguage, page: Int = 0): List<MetaShow> = coroutineScope {
        val region = ContentRegionResolver.tvmazeRegionFor(language)
        val schedule = async { FreeMetaService.schedule(region) }
        val catalog = async { FreeMetaService.catalogPage(page.coerceAtLeast(0)) }
        val anime = if (language == ContentLanguage.MULTI || language == ContentLanguage.EN) {
            async { AniListMetaService.trending(limit = 10) + KitsuMetaService.search("trending", 5) }
        } else null
        val base = schedule.await() + catalog.await()
        val withAnime = if (anime != null) base + anime.await() else base
        withAnime.distinctBy { dedupeKeyForShow(it) }
    }

    fun externalIdsFromShow(show: MetaShow): ExternalIds = ExternalIds(
        imdbId = show.imdbId,
        tvmazeId = show.id.takeIf { it.all(Char::isDigit) } ?: show.tvmazeId,
        anilistId = show.anilistId,
        wikidataId = show.wikidataId,
        tmdbId = show.tmdbId,
        idMal = show.idMal,
        kitsuId = show.kitsuId,
        shikimoriId = show.shikimoriId,
        epguidesKey = show.epguidesKey
    )

    fun toSeries(show: MetaShow, providerId: String): Series {
        val ids = externalIdsFromShow(show)
        return Series(
            id = show.id,
            title = show.title,
            coverUrl = show.posterUrl,
            backdropUrl = show.backdropUrl,
            detailUrl = when {
                show.anilistId != null -> "/anime/${show.anilistId}"
                show.id.all(Char::isDigit) -> "/shows/${show.id}"
                else -> "/title/${show.id}"
            },
            year = show.year,
            description = show.summary,
            genres = show.genres,
            rating = show.rating?.let { String.format("%.1f", it) },
            status = show.status,
            isMovie = show.mediaType == "movie",
            providerId = providerId,
            imdbId = ids.imdbId,
            tvmazeId = ids.tvmazeId,
            anilistId = ids.anilistId,
            canonicalKey = ids.canonicalKey(),
            tmdbId = ids.tmdbId,
            originalTitle = show.title,
            isAdult = show.isAdult
        )
    }

    fun dedupeKeyForSeries(series: Series): String {
        series.canonicalKey?.let { return it }
        series.imdbId?.let { return "imdb:$it" }
        series.tvmazeId?.let { return "tvmaze:$it" }
        series.anilistId?.let { return "anilist:$it" }
        return "${normalizeTitle(series.title)}|${series.year.orEmpty()}"
    }

    private fun dedupeKeyForShow(show: MetaShow): String =
        externalIdsFromShow(show).canonicalKey() ?: AnimeMetaAggregator.dedupeKey(show)

    private suspend fun buildEnrichment(
        show: MetaShow,
        preferAnime: Boolean,
        langTag: String,
        language: ContentLanguage,
        scrapedIsAdult: Boolean? = null
    ): MetaEnrichment {
        val mergedAnime = if (preferAnime || show.mediaType == "anime") AnimeMetaAggregator.enrichAnime(show) else show
        val ids = externalIdsFromShow(mergedAnime)
        val wikidata = if (ids.imdbId != null || ids.wikidataId != null) {
            ids
        } else {
            WikidataMetaService.resolveExternalIds(mergedAnime.title, langTag, imdbHint = mergedAnime.imdbId)
                .merge(ids)
        }
        val similar = similar(mergedAnime)
        val mergedShow = mergedAnime.copy(
            imdbId = wikidata.imdbId ?: mergedAnime.imdbId,
            tmdbId = wikidata.tmdbId ?: mergedAnime.tmdbId,
            wikidataId = wikidata.wikidataId ?: mergedAnime.wikidataId
        )
        val withTrailer = attachTrailer(mergedShow, wikidata)
        val (ratedShow, ageRating) = attachAgeRating(withTrailer, wikidata, language, scrapedIsAdult)
        return MetaEnrichment(
            show = ratedShow,
            cast = mergedAnime.cast,
            similar = similar,
            externalIds = wikidata,
            canonicalKey = wikidata.canonicalKey() ?: ids.canonicalKey(),
            ageRating = ageRating
        )
    }

    private suspend fun buildEnrichmentFromTvmaze(
        show: MetaShow,
        langTag: String,
        imdbHint: String?,
        language: ContentLanguage,
        scrapedIsAdult: Boolean? = null
    ): MetaEnrichment {
        val epguidesKey = MetaEpisodeResolver.resolveEpguidesKey(show.title)
        val baseIds = ExternalIds(
            imdbId = show.imdbId ?: imdbHint,
            tvmazeId = show.id,
            tmdbId = show.tmdbId,
            epguidesKey = epguidesKey
        )
        val wikidata = WikidataMetaService.resolveExternalIds(
            title = show.title,
            language = langTag,
            imdbHint = baseIds.imdbId,
            tvmazeHint = show.id
        ).merge(baseIds)
        val enrichedShow = show.copy(
            imdbId = wikidata.imdbId ?: show.imdbId,
            tvmazeId = show.id,
            tmdbId = wikidata.tmdbId ?: show.tmdbId,
            wikidataId = wikidata.wikidataId,
            epguidesKey = wikidata.epguidesKey ?: epguidesKey
        )
        val similar = FreeMetaService.search(show.genres.firstOrNull() ?: show.title, limit = 15)
            .filter { it.id != show.id }
        val withTrailer = attachTrailer(enrichedShow, wikidata)
        val (ratedShow, ageRating) = attachAgeRating(withTrailer, wikidata, language, scrapedIsAdult)
        return MetaEnrichment(
            show = ratedShow,
            cast = enrichedShow.cast,
            similar = similar,
            externalIds = wikidata,
            canonicalKey = wikidata.canonicalKey(),
            ageRating = ageRating
        )
    }

    private suspend fun attachTrailer(show: MetaShow, ids: ExternalIds): MetaShow {
        val existing = show.trailerUrl
        if (!existing.isNullOrBlank() && TrailerMetaService.isDirectPlayable(existing)) return show
        val resolved = TrailerMetaService.resolve(show, ids) ?: return show
        return show.copy(trailerUrl = resolved)
    }

    private suspend fun attachAgeRating(
        show: MetaShow,
        ids: ExternalIds,
        language: ContentLanguage,
        scrapedIsAdult: Boolean? = null
    ): Pair<MetaShow, AgeRatingResult> {
        val kitsuAdult = show.contentRating?.let { KitsuMetaService.isAdultFromAgeRating(it) }
        val rating = AgeRatingService.resolve(
            title = show.title,
            isMovie = show.mediaType == "movie",
            language = language,
            imdbId = ids.imdbId ?: show.imdbId,
            wikidataId = ids.wikidataId ?: show.wikidataId,
            anilistIsAdult = AgeRatingResolver.mergeIsAdult(show.isAdult, kitsuAdult),
            idMal = ids.idMal ?: show.idMal,
            scrapedIsAdult = scrapedIsAdult
        )
        val isAdult = AgeRatingResolver.mergeIsAdult(show.isAdult, rating.isAdult)
        val primaryCert = rating.primaryForLanguage(language) ?: rating.primaryCertification
        val enriched = show.copy(
            isAdult = isAdult,
            contentRating = primaryCert ?: show.contentRating,
            contentRatingSource = rating.source ?: show.contentRatingSource
        )
        return enriched to rating
    }

    private fun normalizeTitle(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9äöüß]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
