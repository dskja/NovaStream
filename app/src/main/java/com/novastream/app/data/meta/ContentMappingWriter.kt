package com.novastream.app.data.meta

import com.novastream.app.data.db.ContentDao
import com.novastream.app.data.db.ContentEntity
import com.novastream.app.data.model.Series

/** Persists slug ↔ external ID mappings for cross-provider discovery. */
object ContentMappingWriter {

    fun entityFor(series: Series, enrichment: MetaEnrichment): ContentEntity? {
        val ids = enrichment.externalIds
        val canonicalKey = enrichment.canonicalKey ?: ids.canonicalKey()
        return ContentEntity.fromExternalIds(
            slug = series.id,
            providerId = series.providerId?.takeIf { it.isNotBlank() } ?: "unknown",
            contentType = if (series.isMovie) ContentEntity.TYPE_MOVIE else ContentEntity.TYPE_TV,
            imdbId = ids.imdbId ?: enrichment.show.imdbId,
            tvmazeId = ids.tvmazeId ?: enrichment.show.tvmazeId,
            anilistId = ids.anilistId ?: enrichment.show.anilistId,
            wikidataId = ids.wikidataId ?: enrichment.show.wikidataId,
            tmdbId = ids.tmdbId ?: enrichment.show.tmdbId,
            idMal = ids.idMal ?: enrichment.show.idMal,
            canonicalKeyOverride = canonicalKey
        )
    }

    suspend fun persist(dao: ContentDao, series: Series, enrichment: MetaEnrichment) {
        entityFor(series, enrichment)?.let { dao.upsert(it) }
    }
}
