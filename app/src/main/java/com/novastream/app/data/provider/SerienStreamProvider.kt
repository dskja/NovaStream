package com.novastream.app.data.provider

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.api.NovaStreamApi
import com.novastream.app.data.api.NovaStreamScraper
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Provider für SerienStream.to / s.to.
 * Nutzt das gleiche HTML-Markup wie AniWorld.to (nur andere Base-URL und Content-Pfad).
 *
 * URL-Schema:
 *   /serie/{slug}                     – Serien-Detail
 *   /serie/{slug}/staffel-{n}         – Staffel-Seite
 *   /serie/{slug}/staffel-{n}/episode-{m} – Episoden-Seite
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
            .baseUrl(base + "/")
            .client(NetworkModule.okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        return retrofit.create(NovaStreamApi::class.java)
    }

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        NovaStreamScraper.parseSeriesList(api.home())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Lädt Serien nach Genre (z.B. "action", "drama", "comedy"). */
    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        NovaStreamScraper.parseSeriesList(api.genre(genre.trim()))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Lädt Serien nach Genre mit Pagination. */
    suspend fun loadGenrePaged(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        NovaStreamScraper.parseSeriesList(api.genrePaged(genre.trim(), page.coerceAtLeast(1)))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Lädt die neuesten Serien. */
    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        NovaStreamScraper.parseSeriesList(api.newest())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Lädt die beliebtesten Serien. */
    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        NovaStreamScraper.parseSeriesList(api.popular())
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
        val html = api.season(slug, season)
        NovaStreamScraper.parseSeasonEpisodes(html, slug, season)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        val html = api.episode(episode.slug, episode.season, episode.number)
        NovaStreamScraper.parseHosters(html)
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
