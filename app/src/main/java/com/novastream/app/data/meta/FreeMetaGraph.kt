package com.novastream.app.data.meta

import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ContentRegionResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified metadata facade over free no-key sources:
 * TVMaze (TV), AniList (anime), Wikidata (cross-language IDs).
 */
@Singleton
class FreeMetaGraph @Inject constructor() {

    suspend fun search(query: String, preferAnime: Boolean = false, limit: Int = 30): List<MetaShow> {
        if (query.isBlank()) return emptyList()
        val tvmaze = FreeMetaService.search(query, limit = limit)
        if (!preferAnime) return tvmaze
        val anime = AniListMetaService.search(query, limit = limit / 2)
        return (anime + tvmaze).distinctBy { dedupeKeyForShow(it) }
    }

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
                return buildEnrichment(it, preferAnime, langTag, language, scrapedIsAdult)
            }
        }

        if (preferAnime) {
            val animeMatch = pickBestAnime(title)
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
            pickBestAnime(title)?.let { return buildEnrichment(it, preferAnime = true, langTag, language, scrapedIsAdult) }
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
        val tvmazeHint = series.tvmazeId ?: series.id.takeIf { it.all(Char::isDigit) }
        val anilistHint = series.anilistId
        return enrich(
            title = series.title,
            isMovie = series.isMovie,
            preferAnime = preferAnime,
            language = language,
            tvmazeIdHint = tvmazeHint,
            imdbHint = series.imdbId,
            anilistIdHint = anilistHint,
            scrapedIsAdult = series.isAdult
        )
    }

    suspend fun similar(show: MetaShow, limit: Int = 20): List<MetaShow> {
        if (show.similar.isNotEmpty()) return show.similar.take(limit)
        show.anilistId?.let { id ->
            val related = AniListMetaService.similar(id, limit)
            if (related.isNotEmpty()) return related
        }
        val genre = show.genres.firstOrNull() ?: return emptyList()
        return FreeMetaService.search(genre, limit = limit)
            .filter { it.id != show.id && FreeMetaService.titlesSimilar(genre, it.genres.firstOrNull() ?: "") || show.genres.any { g -> it.genres.any { ig -> ig.equals(g, true) } } }
            .take(limit)
    }

    suspend fun browseRegional(language: ContentLanguage, page: Int = 0): List<MetaShow> {
        val region = ContentRegionResolver.tvmazeRegionFor(language)
        val schedule = FreeMetaService.schedule(region)
        val catalog = FreeMetaService.catalogPage(page.coerceAtLeast(0))
        val anime = if (language == ContentLanguage.MULTI || language == ContentLanguage.EN) {
            AniListMetaService.trending(limit = 15)
        } else emptyList()
        return (schedule + catalog + anime).distinctBy { dedupeKeyForShow(it) }
    }

    fun externalIdsFromShow(show: MetaShow): ExternalIds = ExternalIds(
        imdbId = show.imdbId,
        tvmazeId = show.id.takeIf { it.all(Char::isDigit) } ?: show.tvmazeId,
        anilistId = show.anilistId,
        wikidataId = show.wikidataId,
        tmdbId = show.tmdbId
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
        externalIdsFromShow(show).canonicalKey() ?: "title:${normalizeTitle(show.title)}"

    private suspend fun pickBestAnime(title: String): MetaShow? {
        val candidates = AniListMetaService.search(title, limit = 8)
        return candidates.firstOrNull { FreeMetaService.titlesSimilar(title, it.title) }
            ?: candidates.firstOrNull()
    }

    private suspend fun buildEnrichment(
        show: MetaShow,
        preferAnime: Boolean,
        langTag: String,
        language: ContentLanguage,
        scrapedIsAdult: Boolean? = null
    ): MetaEnrichment {
        val ids = externalIdsFromShow(show)
        val wikidata = if (ids.imdbId != null || ids.wikidataId != null) {
            ids
        } else {
            WikidataMetaService.resolveExternalIds(show.title, langTag, imdbHint = show.imdbId)
                .merge(ids)
        }
        val similar = similar(show)
        val mergedShow = show.copy(
            imdbId = wikidata.imdbId ?: show.imdbId,
            tmdbId = wikidata.tmdbId ?: show.tmdbId,
            wikidataId = wikidata.wikidataId ?: show.wikidataId
        )
        val (ratedShow, ageRating) = attachAgeRating(mergedShow, wikidata, language, scrapedIsAdult)
        return MetaEnrichment(
            show = ratedShow,
            cast = show.cast,
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
        val baseIds = ExternalIds(
            imdbId = show.imdbId ?: imdbHint,
            tvmazeId = show.id,
            tmdbId = show.tmdbId
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
            wikidataId = wikidata.wikidataId
        )
        val similar = FreeMetaService.search(show.genres.firstOrNull() ?: show.title, limit = 15)
            .filter { it.id != show.id }
        val (ratedShow, ageRating) = attachAgeRating(enrichedShow, wikidata, language, scrapedIsAdult)
        return MetaEnrichment(
            show = ratedShow,
            cast = enrichedShow.cast,
            similar = similar,
            externalIds = wikidata,
            canonicalKey = wikidata.canonicalKey(),
            ageRating = ageRating
        )
    }

    private suspend fun attachAgeRating(
        show: MetaShow,
        ids: ExternalIds,
        language: ContentLanguage,
        scrapedIsAdult: Boolean? = null
    ): Pair<MetaShow, AgeRatingResult> {
        val rating = AgeRatingService.resolve(
            title = show.title,
            isMovie = show.mediaType == "movie",
            language = language,
            imdbId = ids.imdbId ?: show.imdbId,
            wikidataId = ids.wikidataId ?: show.wikidataId,
            anilistIsAdult = show.isAdult,
            idMal = show.idMal,
            scrapedIsAdult = scrapedIsAdult
        )
        val isAdult = AgeRatingResolver.mergeIsAdult(show.isAdult, rating.isAdult)
        val enriched = show.copy(
            isAdult = isAdult,
            contentRating = rating.primaryCertification ?: show.contentRating,
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
