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
import com.novastream.app.util.MediaUrls
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

    private fun tag(series: Series): Series = series.copy(
        providerId = id,
        coverUrl = MediaUrls.abs(series.coverUrl, baseUrl),
        backdropUrl = MediaUrls.abs(series.backdropUrl, baseUrl),
        title = MediaUrls.sanitizeTitle(series.title).ifBlank { series.title }
    )

    private fun tagAll(list: List<Series>): List<Series> = list.map { tag(it) }

    private fun <T> parseWithBase(block: () -> T): T =
        NovaStreamScraper.withBaseUrl(baseUrl, block)

    /** Strukturierte Home-Sektionen (Hero, Trends, Neue Episoden, …). */
    suspend fun loadHomeCatalog(): StreamingProvider.ProviderResult<HomeCatalog> = runCatching {
        val html = api.home()
        parseWithBase {
            val catalog = NovaStreamScraper.parseHomeCatalog(html)
            catalog.copy(
                hero = tagAll(catalog.hero),
                popular = tagAll(catalog.popular),
                newest = tagAll(catalog.newest),
                trending = tagAll(catalog.trending),
                topShows = tagAll(catalog.topShows),
                all = tagAll(catalog.all)
            )
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val html = api.home()
        parseWithBase {
            val catalog = NovaStreamScraper.parseHomeCatalog(html)
            tagAll(catalog.flattened().ifEmpty { NovaStreamScraper.parseSeriesList(html) })
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        val html = api.genre(genre.trim())
        parseWithBase { tagAll(NovaStreamScraper.parseSeriesList(html)) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadGenrePaged(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        val html = api.genrePaged(genre.trim(), page.coerceAtLeast(1))
        parseWithBase { tagAll(NovaStreamScraper.parseSeriesList(html)) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val latestHtml = api.neueEpisoden()
        val latest = parseWithBase { NovaStreamScraper.parseLatestEpisodes(latestHtml) }
        if (latest.isNotEmpty()) {
            tagAll(
                latest.map {
                    Series(
                        id = it.seriesSlug,
                        title = it.seriesTitle,
                        detailUrl = "/serie/${it.seriesSlug}",
                        coverUrl = it.coverUrl,
                        providerId = id
                    )
                }.distinctBy { it.id }
            )
        } else {
            val catalogHtml = api.catalog()
            parseWithBase { tagAll(NovaStreamScraper.parseSeriesList(catalogHtml)) }
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadLatestEpisodes(): StreamingProvider.ProviderResult<List<LatestEpisode>> = runCatching {
        val html = api.neueEpisoden()
        parseWithBase { NovaStreamScraper.parseLatestEpisodes(html) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val popularHtml = api.beliebteSerien()
        val parsed = parseWithBase { NovaStreamScraper.parseSeriesList(popularHtml) }
        if (parsed.isNotEmpty()) tagAll(parsed)
        else {
            val homeHtml = api.home()
            parseWithBase { tagAll(NovaStreamScraper.parseSeriesList(homeHtml)) }
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadExtendedCatalog(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val html = api.catalog()
        parseWithBase { tagAll(NovaStreamScraper.parseSeriesList(html)) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (page <= 0) {
            val html = api.catalog()
            parseWithBase { tagAll(NovaStreamScraper.parseSeriesList(html)) }
        } else {
            val html = try {
                api.catalogPaged(page + 1)
            } catch (_: Exception) {
                api.raw("serien/page/${page + 1}")
            }
            parseWithBase { tagAll(NovaStreamScraper.parseSeriesList(html)) }
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadGenrePage(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> =
        loadGenrePaged(genre, page + 1)

    suspend fun loadCatalog(): StreamingProvider.ProviderResult<List<Series>> = loadExtendedCatalog()

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            val q = query.trim()
            val html = api.search(q)
            val htmlResults = parseWithBase { tagAll(NovaStreamScraper.parseSeriesList(html)) }
            if (htmlResults.isNotEmpty()) return@runCatching htmlResults
            AjaxSearchClient.search(
                baseUrl = baseUrl,
                query = q,
                linkHint = "/serie/",
                isAnime = false
            ).map { tag(it) }
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        val html = api.seriesDetail(slug)
        parseWithBase {
            val (series, seasons) = NovaStreamScraper.parseSeriesDetail(html, slug)
            tag(series) to seasons
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        val html = api.season(slug, season)
        parseWithBase { NovaStreamScraper.parseSeasonEpisodes(html, slug, season) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        val html = api.episode(episode.slug, episode.season, episode.number)
        parseWithBase { NovaStreamScraper.parseHosters(html) }
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
