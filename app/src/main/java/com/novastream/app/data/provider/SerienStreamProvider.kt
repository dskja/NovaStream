package com.novastream.app.data.provider

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.api.NovaStreamApi
import com.novastream.app.data.api.NovaStreamScraper
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HomeCatalog
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.LatestEpisode
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.AjaxSearchClient
import com.novastream.app.util.HosterResolver
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Provider für SerienStream.to / s.to (und Mirror .cx).
 * Nutzt den massiv ausgebauten [NovaStreamScraper].
 */
open class SerienStreamProvider(
    override val id: String = "serienstream",
    override val displayName: String = "SerienStream",
    override val baseUrl: String = NovaStreamConfig.BASE_URL,
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    override val supportsMovies: Boolean = false

    override val catalogHint: String = "Große Serien-Auswahl"

    override val availableGenres: List<com.novastream.app.data.model.Genre> = listOf(
        com.novastream.app.data.model.Genre("action", "Action"),
        com.novastream.app.data.model.Genre("comedy", "Comedy"),
        com.novastream.app.data.model.Genre("drama", "Drama"),
        com.novastream.app.data.model.Genre("science-fiction", "Sci-Fi"),
        com.novastream.app.data.model.Genre("thriller", "Thriller"),
        com.novastream.app.data.model.Genre("horror", "Horror"),
        com.novastream.app.data.model.Genre("fantasy", "Fantasy"),
        com.novastream.app.data.model.Genre("krimi", "Krimi"),
        com.novastream.app.data.model.Genre("mystery", "Mystery"),
        com.novastream.app.data.model.Genre("anime", "Anime"),
        com.novastream.app.data.model.Genre("romantik", "Romantik"),
        com.novastream.app.data.model.Genre("abenteuer", "Abenteuer")
    )

    private val api: NovaStreamApi = createApi(baseUrl)
    private val hosterResolver = HosterResolver(baseUrl = baseUrl)

    private fun createApi(base: String): NovaStreamApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(base.trimEnd('/') + "/")
            .client(NetworkModule.okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        return retrofit.create(NovaStreamApi::class.java)
    }

    private fun withBase(block: () -> List<Series>): List<Series> {
        NovaStreamScraper.withBaseUrl(baseUrl) {
            return block().map { s ->
                s.copy(
                    providerId = id,
                    coverUrl = com.novastream.app.util.MediaUrls.abs(s.coverUrl, baseUrl),
                    backdropUrl = com.novastream.app.util.MediaUrls.abs(s.backdropUrl, baseUrl),
                    title = com.novastream.app.util.MediaUrls.sanitizeTitle(s.title).ifBlank { s.title }
                )
            }
        }
    }

    private fun <T> withBaseResult(block: () -> T): T =
        NovaStreamScraper.withBaseUrl(baseUrl, block)

    /** Strukturierte Home-Sektionen (Hero, Trends, Neue Episoden, …). */
    suspend fun loadHomeCatalog(): StreamingProvider.ProviderResult<HomeCatalog> = runCatching {
        withBaseResult {
            val catalog = NovaStreamScraper.parseHomeCatalog(api.home())
            catalog.copy(
                hero = catalog.hero.map { it.copy(providerId = id) },
                popular = catalog.popular.map { it.copy(providerId = id) },
                newest = catalog.newest.map { it.copy(providerId = id) },
                trending = catalog.trending.map { it.copy(providerId = id) },
                topShows = catalog.topShows.map { it.copy(providerId = id) },
                all = catalog.all.map { it.copy(providerId = id) }
            )
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        withBase {
            val catalog = NovaStreamScraper.parseHomeCatalog(api.home())
            catalog.flattened().ifEmpty { NovaStreamScraper.parseSeriesList(api.home()) }
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        withBase { NovaStreamScraper.parseSeriesList(api.genre(genre.trim())) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadGenrePaged(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        withBase { NovaStreamScraper.parseSeriesList(api.genrePaged(genre.trim(), page.coerceAtLeast(1))) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Neue Episoden (/neue-episoden). */
    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        withBase {
            val latest = NovaStreamScraper.parseLatestEpisodes(api.neueEpisoden())
            if (latest.isNotEmpty()) {
                latest.map {
                    Series(
                        id = it.seriesSlug,
                        title = it.seriesTitle,
                        detailUrl = "/serie/${it.seriesSlug}",
                        coverUrl = it.coverUrl,
                        providerId = id
                    )
                }.distinctBy { it.id }
            } else {
                NovaStreamScraper.parseSeriesList(api.catalog())
            }
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadLatestEpisodes(): StreamingProvider.ProviderResult<List<LatestEpisode>> = runCatching {
        withBaseResult { NovaStreamScraper.parseLatestEpisodes(api.neueEpisoden()) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        withBase {
            NovaStreamScraper.parseSeriesList(api.beliebteSerien()).ifEmpty {
                NovaStreamScraper.parseSeriesList(api.home())
            }
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadExtendedCatalog(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        withBase { NovaStreamScraper.parseSeriesList(api.catalog()) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadCatalog(): StreamingProvider.ProviderResult<List<Series>> = loadExtendedCatalog()

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            val q = query.trim()
            // 1) Offizielle /suche HTML
            val htmlResults = withBase {
                NovaStreamScraper.parseSeriesList(api.search(q))
            }
            if (htmlResults.isNotEmpty()) return@runCatching htmlResults
            // 2) AJAX-Fallback
            AjaxSearchClient.search(
                baseUrl = baseUrl,
                query = q,
                linkHint = "/serie/",
                isAnime = false
            ).map {
                it.copy(
                    providerId = id,
                    coverUrl = com.novastream.app.util.MediaUrls.abs(it.coverUrl, baseUrl)
                )
            }
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        withBaseResult {
            val (series, seasons) = NovaStreamScraper.parseSeriesDetail(api.seriesDetail(slug), slug)
            series.copy(
                providerId = id,
                coverUrl = com.novastream.app.util.MediaUrls.abs(series.coverUrl, baseUrl),
                backdropUrl = com.novastream.app.util.MediaUrls.abs(series.backdropUrl, baseUrl)
            ) to seasons
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        withBaseResult { NovaStreamScraper.parseSeasonEpisodes(api.season(slug, season), slug, season) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        withBaseResult {
            NovaStreamScraper.parseHosters(api.episode(episode.slug, episode.season, episode.number))
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatching {
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )
}

/**
 * SerienStream Mirror (.cx) – gleicher Scraper, andere Base-URL.
 * Nützlich bei DNS-/CUII-Sperren auf .to.
 */
class SerienStreamCxProvider : SerienStreamProvider(
    id = "serienstream_cx",
    displayName = "SerienStream CX",
    baseUrl = "https://serienstream.cx"
)
