package com.novastream.app.data.provider

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
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    private val api: KinoGerApi = createApi(baseUrl)
    private val hosterResolver = HosterResolver(baseUrl = baseUrl)

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

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        KinoGerScraper.parseSeriesList(api.seriesHome())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Lädt Filme (nicht-Serien). */
    suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        KinoGerScraper.parseSeriesList(api.movies())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Lädt TV-Shows. */
    suspend fun loadTvShows(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        KinoGerScraper.parseSeriesList(api.tvShows())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Lädt Serien mit Pagination. */
    suspend fun loadSeriesPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        KinoGerScraper.parseSeriesList(api.seriesPage(page.coerceAtLeast(1)))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Lädt Genre mit Pagination. */
    suspend fun loadGenrePaged(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        KinoGerScraper.parseSeriesList(api.genrePage(genre.trim(), page.coerceAtLeast(1)))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            KinoGerScraper.parseSeriesList(api.search(query = query.trim()))
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        val html = fetchDetailPage(slug)
        KinoGerScraper.parseSeriesDetail(html, slug)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        val html = fetchDetailPage(slug)
        val (_, seasons) = KinoGerScraper.parseSeriesDetail(html, slug)
        seasons.find { it.number == season }?.episodes ?: emptyList()
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        if (episode.hosters.isNotEmpty()) {
            episode.hosters
        } else {
            val html = fetchDetailPage(episode.slug)
            KinoGerScraper.parseHosters(html)
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

    /** Lädt die Detail-Seite mit thread-safe LRU Caching. */
    private suspend fun fetchDetailPage(slug: String): String {
        // Cache hit (thread-safe)
        synchronized(cacheLock) {
            detailCache[slug]?.let { return it }
        }
        // Cache miss - lade
        val html = api.raw("stream/$slug.html")
        // Nur cachen wenn HTML nicht leer ist
        if (html.isNotBlank()) {
            synchronized(cacheLock) {
                detailCache[slug] = html
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
