package com.novastream.app.data.meta

import com.novastream.app.data.db.ContentDao
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ContentLanguage
import javax.inject.Inject
import javax.inject.Singleton

/** Enriches visible catalog/search rows with cached free metadata (posters, IDs, isAdult). */
@Singleton
class CatalogMetaEnricher @Inject constructor(
    private val freeMetaGraph: FreeMetaGraph,
    private val contentDao: ContentDao
) {

    suspend fun enrichList(
        items: List<Series>,
        language: ContentLanguage,
        preferAnime: Boolean = false,
        limit: Int = 24
    ): List<Series> {
        if (items.isEmpty() || limit <= 0) return items
        val enrichedById = linkedMapOf<String, Series>()
        for (series in items.take(limit)) {
            enrichedById[series.id] = enrichOne(series, language, preferAnime)
        }
        return items.map { enrichedById[it.id] ?: it }
    }

    suspend fun enrichOne(
        series: Series,
        language: ContentLanguage,
        preferAnime: Boolean = false
    ): Series = try {
        val enrichment = freeMetaGraph.enrichBySeries(series, preferAnime, language) ?: return series
        ContentMappingWriter.persist(contentDao, series, enrichment)
        applyEnrichment(series, enrichment)
    } catch (_: Exception) {
        series
    }

    fun applyEnrichment(series: Series, enrichment: MetaEnrichment): Series {
        val meta = enrichment.show
        val ids = enrichment.externalIds
        return series.copy(
            description = series.description?.takeIf { it.isNotBlank() } ?: meta.summary,
            coverUrl = series.coverUrl ?: meta.posterUrl,
            backdropUrl = series.backdropUrl ?: meta.backdropUrl,
            genres = series.genres.ifEmpty { meta.genres },
            year = series.year ?: meta.year,
            rating = series.rating ?: meta.rating?.let { String.format("%.1f", it) },
            status = series.status ?: meta.status,
            imdbId = ids.imdbId ?: series.imdbId,
            tvmazeId = ids.tvmazeId ?: series.tvmazeId,
            anilistId = ids.anilistId ?: series.anilistId,
            canonicalKey = enrichment.canonicalKey ?: series.canonicalKey,
            tmdbId = ids.tmdbId ?: series.tmdbId,
            isAdult = AgeRatingResolver.mergeIsAdult(series.isAdult, meta.isAdult)
        )
    }
}
