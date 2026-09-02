package com.novastream.app.data.meta

/** Cross-source external identifiers for dedup and content-graph mapping (no paid APIs). */
data class ExternalIds(
    val imdbId: String? = null,
    val tvmazeId: String? = null,
    val anilistId: Int? = null,
    val wikidataId: String? = null,
    /** TMDB ID from Wikidata cross-refs only (no TMDB API). */
    val tmdbId: Int? = null,
    val idMal: Int? = null,
    val kitsuId: Int? = null,
    val shikimoriId: Int? = null,
    val epguidesKey: String? = null
) {
    fun canonicalKey(): String? = when {
        !imdbId.isNullOrBlank() -> "imdb:${imdbId.trim()}"
        !tvmazeId.isNullOrBlank() -> "tvmaze:${tvmazeId.trim()}"
        anilistId != null && anilistId > 0 -> "anilist:$anilistId"
        idMal != null && idMal > 0 -> "mal:$idMal"
        kitsuId != null && kitsuId > 0 -> "kitsu:$kitsuId"
        !wikidataId.isNullOrBlank() -> "wikidata:${wikidataId.trim()}"
        tmdbId != null && tmdbId > 0 -> "tmdb:$tmdbId"
        else -> null
    }

    fun merge(other: ExternalIds): ExternalIds = ExternalIds(
        imdbId = imdbId ?: other.imdbId,
        tvmazeId = tvmazeId ?: other.tvmazeId,
        anilistId = anilistId ?: other.anilistId,
        wikidataId = wikidataId ?: other.wikidataId,
        tmdbId = tmdbId ?: other.tmdbId,
        idMal = idMal ?: other.idMal,
        kitsuId = kitsuId ?: other.kitsuId,
        shikimoriId = shikimoriId ?: other.shikimoriId,
        epguidesKey = epguidesKey ?: other.epguidesKey
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
