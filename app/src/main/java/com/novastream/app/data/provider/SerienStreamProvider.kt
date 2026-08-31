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
import com.novastream.app.util.HosterResolver
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Provider für SerienStream.to / s.to (und Mirror .cx).
 * Nutzt den massiv ausgebauten [NovaStreamScraper].
 */
class SerienStreamProvider(
    override val id: String = "serienstream",
    override val displayName: String = "SerienStream",
    override val baseUrl: String = NovaStreamConfig.BASE_URL,
    override val supportsSeries: Boolean = true
) : StreamingProvider {

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

    /** Strukturierte Home-Sektionen (Hero, Trends, Neue Episoden, …). */
    suspend fun loadHomeCatalog(): StreamingProvider.ProviderResult<HomeCatalog> = runCatching {
        NovaStreamScraper.parseHomeCatalog(api.home())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val catalog = NovaStreamScraper.parseHomeCatalog(api.home())
        catalog.flattened().ifEmpty { NovaStreamScraper.parseSeriesList(api.home()) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        NovaStreamScraper.parseSeriesList(api.genre(genre.trim()))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadGenrePaged(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        NovaStreamScraper.parseSeriesList(api.genrePaged(genre.trim(), page.coerceAtLeast(1)))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Neue Episoden (/neue-episoden). */
    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val latest = NovaStreamScraper.parseLatestEpisodes(api.neueEpisoden())
        if (latest.isNotEmpty()) {
            latest.map {
                Series(
                    id = it.seriesSlug,
                    title = it.seriesTitle,
                    detailUrl = "/serie/${it.seriesSlug}",
                    coverUrl = it.coverUrl
                )
            }.distinctBy { it.id }
        } else {
            // Fallback: Katalog
            NovaStreamScraper.parseSeriesList(api.catalog())
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadLatestEpisodes(): StreamingProvider.ProviderResult<List<LatestEpisode>> = runCatching {
        NovaStreamScraper.parseLatestEpisodes(api.neueEpisoden())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        NovaStreamScraper.parseSeriesList(api.beliebteSerien()).ifEmpty {
            NovaStreamScraper.parseSeriesList(api.home())
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadCatalog(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        NovaStreamScraper.parseSeriesList(api.catalog())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            NovaStreamScraper.parseSeriesList(api.search(query.trim()))
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        NovaStreamScraper.parseSeriesDetail(api.seriesDetail(slug), slug)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        NovaStreamScraper.parseSeasonEpisodes(api.season(slug, season), slug, season)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        NovaStreamScraper.parseHosters(api.episode(episode.slug, episode.season, episode.number))
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
