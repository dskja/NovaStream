package com.novastream.app.data.provider

import android.content.Context
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
 * Resolves mirror domains at runtime via [ProviderDomainManager].
 */
open class SerienStreamProvider(
    override val id: String = "serienstream",
    override val displayName: String = "SerienStream",
    override val baseUrl: String = NovaStreamConfig.BASE_URL,
    override val supportsSeries: Boolean = true,
    private val appContext: Context? = null
) : StreamingProvider {

    override val supportsMovies: Boolean = false

    override val catalogHint: String? = ProviderCatalogHints.forId(id)

    override val availableGenres: List<com.novastream.app.data.model.Genre>
        get() = ProviderGenres.forId(id)

    private val mirror = MirrorSupport(id, baseUrl, appContext, "/serie/") {
        cachedApi = null
        cachedApiBase = null
    }

    @Volatile
    private var cachedApi: NovaStreamApi? = null

    @Volatile
    private var cachedApiBase: String? = null

    private suspend fun activeBaseUrl(): String = mirror.activeBase()

    private suspend fun api(): NovaStreamApi {
        val base = activeBaseUrl()
        cachedApi?.let { if (cachedApiBase == base) return it }
        return createApi(base).also {
            cachedApi = it
            cachedApiBase = base
        }
    }

    private fun createApi(base: String): NovaStreamApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(base.trimEnd('/') + "/")
            .client(NetworkModule.okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        return retrofit.create(NovaStreamApi::class.java)
    }

    private fun tag(series: Series, base: String): Series = series.copy(
        providerId = id,
        coverUrl = MediaUrls.abs(series.coverUrl, base),
        backdropUrl = MediaUrls.abs(series.backdropUrl, base),
        title = MediaUrls.sanitizeTitle(series.title).ifBlank { series.title }
    )

    private fun tagAll(list: List<Series>, base: String): List<Series> = list.map { tag(it, base) }

    private suspend fun <T> parseWithBase(block: (String) -> T): T {
        val base = activeBaseUrl()
        return NovaStreamScraper.withBaseUrl(base) { block(base) }
    }

    /** Strukturierte Home-Sektionen (Hero, Trends, Neue Episoden, …). */
    suspend fun loadHomeCatalog(): StreamingProvider.ProviderResult<HomeCatalog> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = mirror.requireCatalogHtml(fetchPage = { api().home() }, fallbackUrl = "$base/")
        parseWithBase { b ->
            val catalog = NovaStreamScraper.parseHomeCatalog(html)
            catalog.copy(
                hero = tagAll(catalog.hero, b),
                popular = tagAll(catalog.popular, b),
                newest = tagAll(catalog.newest, b),
                trending = tagAll(catalog.trending, b),
                topShows = tagAll(catalog.topShows, b),
                all = tagAll(catalog.all, b)
            )
        }
    }

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = mirror.requireCatalogHtml(fetchPage = { api().home() }, fallbackUrl = "$base/")
        parseWithBase { b ->
            val catalog = NovaStreamScraper.parseHomeCatalog(html)
            tagAll(catalog.flattened().ifEmpty { NovaStreamScraper.parseSeriesList(html) }, b)
        }
    }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.isBlank()) emptyList()
        else {
            val html = api().genre(genre.trim())
            parseWithBase { base -> tagAll(NovaStreamScraper.parseSeriesList(html), base) }
        }
    }

    suspend fun loadGenrePaged(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.isBlank()) emptyList()
        else {
            val html = api().genrePaged(genre.trim(), page.coerceAtLeast(1))
            parseWithBase { base -> tagAll(NovaStreamScraper.parseSeriesList(html), base) }
        }
    }

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val latestHtml = api().neueEpisoden()
        val latest = parseWithBase { NovaStreamScraper.parseLatestEpisodes(latestHtml) }
        if (latest.isNotEmpty()) {
            val base = activeBaseUrl()
            tagAll(
                latest.map {
                    Series(
                        id = it.seriesSlug,
                        title = it.seriesTitle,
                        detailUrl = "/serie/${it.seriesSlug}",
                        coverUrl = it.coverUrl,
                        providerId = id
                    )
                }.distinctBy { it.id },
                base
            )
        } else {
            val catalogHtml = api().catalog()
            parseWithBase { base -> tagAll(NovaStreamScraper.parseSeriesList(catalogHtml), base) }
        }
    }

    suspend fun loadLatestEpisodes(): StreamingProvider.ProviderResult<List<LatestEpisode>> = runCatchingProvider {
        val html = api().neueEpisoden()
        parseWithBase { NovaStreamScraper.parseLatestEpisodes(html) }
    }

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val popularHtml = api().beliebteSerien()
        val parsed = parseWithBase { NovaStreamScraper.parseSeriesList(popularHtml) }
        if (parsed.isNotEmpty()) tagAll(parsed, activeBaseUrl())
        else {
            val homeHtml = api().home()
            parseWithBase { base -> tagAll(NovaStreamScraper.parseSeriesList(homeHtml), base) }
        }
    }

    override suspend fun loadExtendedCatalog(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val html = api().catalog()
        parseWithBase { base -> tagAll(NovaStreamScraper.parseSeriesList(html), base) }
    }

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (page <= 0) {
            val html = api().catalog()
            parseWithBase { base -> tagAll(NovaStreamScraper.parseSeriesList(html), base) }
        } else {
            val html = try {
                api().catalogPaged(page + 1)
            } catch (_: Exception) {
                api().raw("serien/page/${page + 1}")
            }
            parseWithBase { base -> tagAll(NovaStreamScraper.parseSeriesList(html), base) }
        }
    }

    override suspend fun loadGenrePage(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> =
        loadGenrePaged(genre, page + 1)

    suspend fun loadCatalog(): StreamingProvider.ProviderResult<List<Series>> = loadExtendedCatalog()

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            val q = query.trim()
            val base = activeBaseUrl()
            val html = api().search(q)
            val htmlResults = parseWithBase { base -> tagAll(NovaStreamScraper.parseSeriesList(html), base) }
            if (htmlResults.isNotEmpty()) htmlResults
            else AjaxSearchClient.search(
                baseUrl = base,
                query = q,
                linkHint = "/serie/",
                isAnime = false
            ).map { tag(it, base) }
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatchingProvider {
        val html = api().seriesDetail(slug)
        parseWithBase { base ->
            val (series, seasons) = NovaStreamScraper.parseSeriesDetail(html, slug)
            tag(series, base) to seasons
        }
    }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatchingProvider {
        val html = api().season(slug, season)
        parseWithBase { NovaStreamScraper.parseSeasonEpisodes(html, slug, season) }
    }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatchingProvider {
        val html = api().episode(episode.slug, episode.season, episode.number)
        val base = activeBaseUrl()
        val raw = parseWithBase { NovaStreamScraper.parseHosters(html) }
        // BetterStreamflix: follow /redirect (data-play-url) to final hoster URL before extract
        raw.map { hoster ->
            val absolute = when {
                hoster.redirectUrl.startsWith("http") -> hoster.redirectUrl
                hoster.redirectUrl.startsWith("/") -> base + hoster.redirectUrl
                else -> "$base/${hoster.redirectUrl}"
            }
            val finalUrl = ProviderHttp.resolveRedirectFinal(absolute, referer = "$base/", providerId = id)
                ?: absolute
            hoster.copy(
                redirectUrl = finalUrl,
                name = if (hoster.language.isNotBlank()) {
                    "${hoster.name} (${hoster.language})"
                } else hoster.name
            )
        }
    }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatchingProvider {
        val base = activeBaseUrl()
        HosterResolver(baseUrl = base).resolve(hoster.name, hoster.redirectUrl)
    }
}

/**
 * SerienStream Mirror (.cx) – gleicher Scraper, andere Base-URL.
 * Nützlich bei DNS-/CUII-Sperren auf .to.
 */
class SerienStreamCxProvider(
    appContext: Context? = null
) : SerienStreamProvider(
    id = "serienstream_cx",
    displayName = "SerienStream CX",
    baseUrl = "https://serienstream.cx",
    appContext = appContext
)
