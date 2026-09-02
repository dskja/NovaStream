package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.api.KinoGerApi
import com.novastream.app.data.api.KinoGerScraper
import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Provider für KinoGer.to.
 * KinoGer ist ein DLE-basiertes CMS mit Filmen und Serien.
 *
 * URL-Schema:
 *   /stream/                          – Filme Übersicht
 *   /stream/serie/                    – Serien Übersicht
 *   /stream/tv-shows/                 – TV-Shows
 *   /stream/{id}-{slug}.html          – Detail-Seite (Film oder Serie)
 *   /?do=search&subaction=search&story={query} – Suche
 *
 * Hoster sind direkt als iframe-URLs in der Detail-Seite eingebettet.
 *
 * Performance: Detail-Seiten werden gecacht (LRU Cache mit max 20 Einträgen) da KinoGer
 * alle Episoden auf einer Seite hat und loadSeason/loadHosters sonst
 * mehrfach die gleiche Seite laden würden.
 */
class KinoGerProvider(
    override val id: String = "kinoger",
    override val displayName: String = "KinoGer",
    override val baseUrl: String = "https://kinoger.to",
    override val supportsSeries: Boolean = true,
    private val appContext: Context? = null
) : StreamingProvider {

    private val mirror = MirrorSupport(id, baseUrl, appContext, "/stream/") {
        cachedApi = null
        cachedApiBase = null
        clearCache()
    }

    @Volatile
    private var cachedApi: KinoGerApi? = null

    @Volatile
    private var cachedApiBase: String? = null

    private suspend fun activeBaseUrl(): String = mirror.activeBase()

    private suspend fun api(): KinoGerApi {
        val base = activeBaseUrl()
        cachedApi?.let { if (cachedApiBase == base) return it }
        return createApi(base).also {
            cachedApi = it
            cachedApiBase = base
        }
    }

    private suspend fun hosterResolverActive(): HosterResolver =
        HosterResolver(baseUrl = activeBaseUrl())

    override val supportsMovies: Boolean = true
    override val catalogHint: String? = ProviderCatalogHints.forId(id)
    override val availableGenres: List<com.novastream.app.data.model.Genre>
        get() = ProviderGenres.forId(id)

    // LRU Cache mit maximal 20 Einträgen (verhindert unbounded Memory Growth)
    private val detailCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }
    private val cacheLock = Any()

    private fun createApi(base: String): KinoGerApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(base + "/")
            .client(NetworkModule.okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        return retrofit.create(KinoGerApi::class.java)
    }

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = mirror.parseBase()
        val seriesHtml = mirror.requireCatalogHtml(fetchPage = { api().seriesHome() }, fallbackUrl = "$base/")
        val moviesHtml = mirror.requireCatalogHtml(fetchPage = { api().movies() }, fallbackUrl = "$base/")
        val series = KinoGerScraper.parseSeriesList(seriesHtml, base)
        val movies = KinoGerScraper.parseSeriesList(moviesHtml, base).map { it.copy(isMovie = true) }
        (series + movies).distinctBy { it.id }.map { it.copy(providerId = id) }
    }

    /** Lädt Filme (nicht-Serien). */
    override suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        KinoGerScraper.parseSeriesList(api().movies()).map { it.copy(isMovie = true, providerId = id) }
    }

    /** Lädt TV-Shows. */
    suspend fun loadTvShows(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        KinoGerScraper.parseSeriesList(api().tvShows())
    }

    /** Lädt Serien mit Pagination. */
    suspend fun loadSeriesPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        KinoGerScraper.parseSeriesList(api().seriesPage(page.coerceAtLeast(1)))
    }

    /** Lädt Genre mit Pagination. */
    suspend fun loadGenrePaged(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.isBlank()) emptyList()
        else KinoGerScraper.parseSeriesList(api().genrePage(genre.trim(), page.coerceAtLeast(1)))
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            KinoGerScraper.parseSeriesList(api().search(query = query.trim()))
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatchingProvider {
        val html = fetchDetailPage(slug)
        KinoGerScraper.parseSeriesDetail(html, slug, mirror.parseBase())
    }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatchingProvider {
        val html = fetchDetailPage(slug)
        val (_, seasons) = KinoGerScraper.parseSeriesDetail(html, slug, mirror.parseBase())
        seasons.find { it.number == season }?.episodes ?: emptyList()
    }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatchingProvider {
        if (episode.hosters.isNotEmpty()) {
            episode.hosters
        } else {
            val html = fetchDetailPage(episode.slug)
            KinoGerScraper.parseHosters(html)
        }
    }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatchingProvider {
        hosterResolverActive().resolve(hoster.name, hoster.redirectUrl)
    }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> =
        loadGenrePaged(genre, 1)

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> =
        loadSeriesPage(page + 1)

    override suspend fun loadGenrePage(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> =
        loadGenrePaged(genre, page + 1)

    /** Lädt die Detail-Seite mit thread-safe LRU Caching. */
    private suspend fun fetchDetailPage(slug: String): String {
        synchronized(cacheLock) {
            detailCache[slug]?.let { return it }
        }
        val base = activeBaseUrl()
        var html = mirror.fetch("$base/stream/$slug.html")
        if (html.isBlank() || ProviderHttp.isChallenge(html)) {
            html = mirror.fetch("$base/series/$slug.html")
        }
        if (html.isNotBlank() && !ProviderHttp.isChallenge(html)) {
            synchronized(cacheLock) {
                if (detailCache[slug] == null) {
                    detailCache[slug] = html
                }
            }
        }
        return html
    }

    /** Leert den Cache (z.B. bei Provider-Wechsel). */
    fun clearCache() {
        synchronized(cacheLock) {
            detailCache.clear()
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 20
    }
}
