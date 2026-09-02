package com.novastream.app.data.meta

/** Cross-source external identifiers for dedup and content-graph mapping (no paid APIs). */
data class ExternalIds(
    val imdbId: String? = null,
    val tvmazeId: String? = null,
    val anilistId: Int? = null,
    val wikidataId: String? = null,
    /** TMDB ID from Wikidata cross-refs only (no TMDB API). */
    val tmdbId: Int? = null
) {
    fun canonicalKey(): String? = when {
        !imdbId.isNullOrBlank() -> "imdb:${imdbId.trim()}"
        !tvmazeId.isNullOrBlank() -> "tvmaze:${tvmazeId.trim()}"
        anilistId != null && anilistId > 0 -> "anilist:$anilistId"
        !wikidataId.isNullOrBlank() -> "wikidata:${wikidataId.trim()}"
        tmdbId != null && tmdbId > 0 -> "tmdb:$tmdbId"
        else -> null
    }

    fun merge(other: ExternalIds): ExternalIds = ExternalIds(
        imdbId = imdbId ?: other.imdbId,
        tvmazeId = tvmazeId ?: other.tvmazeId,
        anilistId = anilistId ?: other.anilistId,
        wikidataId = wikidataId ?: other.wikidataId,
        tmdbId = tmdbId ?: other.tmdbId
    )
}

/** Resolved metadata bundle from [FreeMetaGraph]. */
data class MetaEnrichment(
    val show: MetaShow,
    val cast: List<MetaPerson> = emptyList(),
    val similar: List<MetaShow> = emptyList(),
    val externalIds: ExternalIds = ExternalIds(),
    val canonicalKey: String? = externalIds.canonicalKey(),
    val ageRating: AgeRatingResult? = null
)
