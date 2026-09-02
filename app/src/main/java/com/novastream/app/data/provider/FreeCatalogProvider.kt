package com.novastream.app.data.provider

import com.novastream.app.data.meta.FreeMetaService
import com.novastream.app.data.meta.MetaShow
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.EmbedStreamResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * AAA Katalog-Provider auf Basis von TVMaze (komplett kostenlos, kein API-Key).
 * Playback über öffentliche Embed-Frontends (VidSrc / 2Embed) per IMDb-ID.
 *
 * Damit funktioniert Suche/Browse/Detail/Episoden für tausende Serien –
 * unabhängig von SerienStream-HTML.
 */
class FreeCatalogProvider(
    override val id: String = "freecatalog",
    override val displayName: String = "Free Catalog (TVMaze)",
    override val baseUrl: String = "https://api.tvmaze.com",
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    override val supportsMovies: Boolean = false
    override val catalogHint: String? = ProviderCatalogHints.forId(id)
    override val availableGenres: List<com.novastream.app.data.model.Genre> = listOf(
        com.novastream.app.data.model.Genre("Action", "Action"),
        com.novastream.app.data.model.Genre("Comedy", "Comedy"),
        com.novastream.app.data.model.Genre("Drama", "Drama"),
        com.novastream.app.data.model.Genre("Science-Fiction", "Sci-Fi"),
        com.novastream.app.data.model.Genre("Horror", "Horror"),
        com.novastream.app.data.model.Genre("Thriller", "Thriller")
    )

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        coroutineScope {
            val region = ContentRegionResolver.currentTvmazeRegion()
            val scheduleDef = async { FreeMetaService.schedule(region) }
            val catalogDef = async { FreeMetaService.catalogPage(0) }
            (scheduleDef.await() + catalogDef.await()).distinctBy { it.id }.map { mapShow(it) }
        }
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            FreeMetaService.search(query.trim()).map { mapShow(it) }
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> =
        runCatchingProvider {
            val show = FreeMetaService.show(slug)
                ?: FreeMetaService.enrichByTitle(slug.replace('-', ' '))
                ?: error("Serie nicht gefunden")
            val seasons = FreeMetaService.seasonsWithEpisodes(show.id).map { metaSeason ->
                Season(
                    number = metaSeason.number,
                    episodes = metaSeason.episodes.map { ep ->
                        Episode(
                            number = ep.number,
                            title = ep.title,
                            slug = show.id,
                            season = ep.season,
                            episodeUrl = "imdb://${show.imdbId ?: ""}/${ep.season}/${ep.number}",
                            thumbnailUrl = ep.imageUrl
                        )
                    }
                )
            }
            mapShow(show) to seasons
        }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> =
        runCatchingProvider {
            val show = FreeMetaService.show(slug) ?: error("Serie nicht gefunden")
            FreeMetaService.episodes(slug)
                .filter { it.season == season }
                .map { ep ->
                    Episode(
                        number = ep.number,
                        title = ep.title,
                        slug = slug,
                        season = ep.season,
                        episodeUrl = "imdb://${show.imdbId ?: ""}/${ep.season}/${ep.number}",
                        thumbnailUrl = ep.imageUrl
                    )
                }
        }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> =
        runCatchingProvider {
            val imdb = extractImdb(episode)
                ?: FreeMetaService.show(episode.slug)?.imdbId
                ?: error("Keine IMDb-ID – Embeds nicht möglich")
            EmbedStreamResolver.buildHosters(
                imdbId = imdb,
                season = episode.season,
                episode = episode.number,
                isMovie = false,
                tmdbId = extractTmdb(episode.slug)
            )
        }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> =
        runCatchingProvider {
            when {
                hoster.name.contains("VidSrc", true) || hoster.redirectUrl.contains("vidsrc") ->
                    EmbedStreamResolver.resolveByImdb(
                        imdbId = hoster.redirectUrl.substringAfter("imdb=").substringBefore("&"),
                        season = hoster.redirectUrl.substringAfter("season=", "1").substringBefore("&").toIntOrNull() ?: 1,
                        episode = hoster.redirectUrl.substringAfter("episode=", "1").substringBefore("&").toIntOrNull() ?: 1,
                        isMovie = hoster.redirectUrl.contains("/movie")
                    ).ifEmpty {
                        com.novastream.app.util.HosterResolver().resolve(hoster.name, hoster.redirectUrl)
                    }
                else -> com.novastream.app.util.HosterResolver().resolve(hoster.name, hoster.redirectUrl)
            }
        }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.trim().isBlank()) emptyList()
        else {
            FreeMetaService.catalogPage(0)
                .filter { show ->
                    show.genres.any { it.equals(genre, true) } ||
                        show.genres.any { it.contains(genre, true) }
                }
                .map { mapShow(it) }
                .ifEmpty {
                    FreeMetaService.search(genre).map { mapShow(it) }
                }
        }
    }

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        FreeMetaService.schedule(ContentRegionResolver.currentTvmazeRegion()).map { mapShow(it) }
    }

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = loadHome()

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        FreeMetaService.catalogPage(page.coerceAtLeast(0)).map { mapShow(it) }
    }

    private fun extractImdb(episode: Episode): String? {
        val url = episode.episodeUrl
        if (url.startsWith("imdb://")) {
            return url.removePrefix("imdb://").substringBefore("/").takeIf { it.startsWith("tt") }
        }
        Regex("""tt\d+""").find(url)?.value?.let { return it }
        return null
    }

    private fun extractTmdb(slug: String): String? =
        slug.removePrefix("tv-").removePrefix("movie-").takeIf { it.all { c -> c.isDigit() } }

    private fun mapShow(show: MetaShow): Series = Series(
        id = show.id,
        title = show.title,
        coverUrl = show.posterUrl,
        backdropUrl = show.backdropUrl,
        detailUrl = "/shows/${show.id}",
        year = show.year,
        description = show.summary,
        genres = show.genres,
        rating = show.rating?.let { String.format("%.1f", it) },
        status = show.status,
        providerId = id,
        seasonCount = show.seasonCount,
        imdbId = show.imdbId,
        tvmazeId = show.id.takeIf { it.all(Char::isDigit) },
        anilistId = show.anilistId,
        canonicalKey = com.novastream.app.data.meta.ExternalIds(
            imdbId = show.imdbId,
            tvmazeId = show.id.takeIf { it.all(Char::isDigit) },
            anilistId = show.anilistId
        ).canonicalKey(),
        originalTitle = show.title
    )
}
