package com.novastream.app.data.provider

/**
 * Deklarierte Fähigkeiten eines Providers für UI-Entscheidungen (Pagination, Latest, …).
 */
data class ProviderCapabilities(
    val supportsPagination: Boolean = false,
    val supportsLatestEpisodes: Boolean = false,
    val supportsGenrePagination: Boolean = false,
    val movieCatalogPath: String? = null
)

/** Ermittelt Capabilities aus Provider-Typ und bekannten Overrides. */
fun StreamingProvider.capabilities(): ProviderCapabilities = when (id) {
    "serienstream", "serienstream_cx" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = true,
        supportsGenrePagination = true
    )
    "aniworld" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = true,
        supportsGenrePagination = false
    )
    "kinoger" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = false,
        supportsGenrePagination = true,
        movieCatalogPath = "/stream/"
    )
    "burningseries" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = false,
        supportsGenrePagination = false
    )
    "megakino" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = true,
        supportsGenrePagination = false,
        movieCatalogPath = "/filme"
    )
    "streamkiste" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = true,
        supportsGenrePagination = false,
        movieCatalogPath = "/filme"
    )
    "filmpalast" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = false,
        supportsGenrePagination = true,
        movieCatalogPath = "/movies/new"
    )
    "kinoz" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = false,
        supportsGenrePagination = true,
        movieCatalogPath = "/Stream/"
    )
    "freecatalog" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = true,
        supportsGenrePagination = false
    )
    "cinezo", "showsst", "hydrahd", "phantomflix", "flixer", "dramacool", "pressplay" -> ProviderCapabilities(
        supportsPagination = true,
        supportsLatestEpisodes = false,
        supportsGenrePagination = true,
        movieCatalogPath = if (supportsMovies) "/movie" else null
    )
    else -> ProviderCapabilities(
        supportsPagination = false,
        supportsLatestEpisodes = false,
        supportsGenrePagination = false,
        movieCatalogPath = if (supportsMovies) "/movies" else null
    )
}
