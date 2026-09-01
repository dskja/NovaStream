package com.novastream.app.data.meta

/**
 * Kostenlose Metadata-Modelle (TVMaze – kein API-Key nötig).
 * Alternative zu TMDb für Katalog, Suche, Episoden und Artwork.
 */
data class MetaShow(
    val id: String,
    val title: String,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val premiered: String? = null,
    val rating: Double? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val imdbId: String? = null,
    val thetvdbId: String? = null,
    val network: String? = null,
    val runtime: Int? = null,
    val language: String? = null,
    val officialSite: String? = null,
    val trailerUrl: String? = null,
    val cast: List<MetaPerson> = emptyList(),
    val seasonCount: Int? = null,
    val tmdbId: Int? = null,
    val anilistId: Int? = null,
    val wikidataId: String? = null,
    val tvmazeId: String? = null,
    val mediaType: String? = null,
    val similar: List<MetaShow> = emptyList()
) {
    val year: String?
        get() = premiered?.take(4)

    val hasImdb: Boolean get() = !imdbId.isNullOrBlank()

    val shortSummary: String
        get() = summary?.take(220)?.let { if ((summary?.length ?: 0) > 220) "$it…" else it } ?: ""
}

data class MetaPerson(
    val name: String,
    val character: String? = null,
    val imageUrl: String? = null
)

data class MetaEpisode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val summary: String? = null,
    val airdate: String? = null,
    val runtime: Int? = null,
    val imageUrl: String? = null
) {
    val displayTitle: String get() = "S${season}E$number · $title"
}

data class MetaSeason(
    val number: Int,
    val episodes: List<MetaEpisode> = emptyList()
)
