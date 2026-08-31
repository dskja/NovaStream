package com.novastream.app.data.provider

import com.novastream.app.data.meta.FreeMetaService
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.EmbedStreamResolver

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
    override val catalogHint: String = "Tausende Serien via TVMaze"
    override val availableGenres: List<com.novastream.app.data.model.Genre> = listOf(
        com.novastream.app.data.model.Genre("Action", "Action"),
        com.novastream.app.data.model.Genre("Comedy", "Comedy"),
        com.novastream.app.data.model.Genre("Drama", "Drama"),
        com.novastream.app.data.model.Genre("Science-Fiction", "Sci-Fi"),
        com.novastream.app.data.model.Genre("Horror", "Horror"),
        com.novastream.app.data.model.Genre("Thriller", "Thriller")
    )

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val schedule = FreeMetaService.schedule("US")
        val page = FreeMetaService.catalogPage(0)
        (schedule + page).distinctBy { it.id }.map { it.toSeries() }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            FreeMetaService.search(query.trim()).map { it.toSeries() }
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> =
        runCatching {
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
            show.toSeries() to seasons
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> =
        runCatching {
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
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> =
        runCatching {
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
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> =
        runCatching {
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
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        // TVMaze hat keine Genre-Route – filter Katalog
        FreeMetaService.catalogPage(0)
            .filter { show ->
                show.genres.any { it.equals(genre, true) } ||
                    show.genres.any { it.contains(genre, true) }
            }
            .map { it.toSeries() }
            .ifEmpty {
                FreeMetaService.search(genre).map { it.toSeries() }
            }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        FreeMetaService.schedule("US").map { it.toSeries() }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = loadHome()

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

    private fun com.novastream.app.data.meta.MetaShow.toSeries(): Series = Series(
        id = id,
        title = title,
        coverUrl = posterUrl,
        backdropUrl = backdropUrl,
        detailUrl = "/shows/$id",
        year = year,
        description = summary,
        genres = genres,
        rating = rating?.let { String.format("%.1f", it) },
        status = status,
        providerId = julia.r@example.org,
        seasonCount = seasonCount,
        originalTitle = title
    )
}
