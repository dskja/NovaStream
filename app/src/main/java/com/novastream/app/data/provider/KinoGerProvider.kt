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
import java.util.concurrent.ConcurrentHashMap

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
 * Performance: Detail-Seiten werden gecacht (ConcurrentHashMap) da KinoGer
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

    // In-Memory Cache für Detail-Seiten (slug -> HTML)
    // Verhindert mehrfaches Laden der gleichen Seite für loadSeason/loadHosters
    private val detailCache = ConcurrentHashMap<String, String>()

    private fun createApi(base: String): KinoGerApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(base + "/")
            .client(NetworkModule.okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        return retrofit.create(KinoGerApi::class.java)
    }

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        // KinoGer hat keine reine Serien-Startseite - nutze /stream/serie/
        KinoGerScraper.parseSeriesList(api.seriesHome())
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("KinoGer Startseite konnte nicht geladen werden", it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            KinoGerScraper.parseSeriesList(api.search(query = query.trim()))
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error("KinoGer Suche fehlgeschlagen", it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        val html = fetchDetailPage(slug)
        KinoGerScraper.parseSeriesDetail(html, slug)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("KinoGer Details konnten nicht geladen werden", it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        // KinoGer lädt alle Episoden auf einer Seite - keine separate Staffel-URL
        // Nutze Cache um mehrfaches Laden zu vermeiden
        val html = fetchDetailPage(slug)
        val (_, seasons) = KinoGerScraper.parseSeriesDetail(html, slug)
        seasons.find { it.number == season }?.episodes ?: emptyList()
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("KinoGer Staffel konnte nicht geladen werden", it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        // KinoGer: Hoster sind bereits als iframe-URLs in der Episode gespeichert
        if (episode.hosters.isNotEmpty()) {
            episode.hosters
        } else {
            // Fallback: Lade Detail-Seite und parse Hoster
            val html = fetchDetailPage(episode.slug)
            KinoGerScraper.parseHosters(html)
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("KinoGer Hoster konnten nicht geladen werden", it) }
    )

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatching {
        // KinoGer: redirectUrl ist bereits die direkte iframe-URL zum Hoster
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("Stream-URL konnte nicht aufgelöst werden", it) }
    )

    /** Lädt die Detail-Seite mit Caching. */
    private suspend fun fetchDetailPage(slug: String): String {
        // Cache hit?
        detailCache[slug]?.let { return it }
        // Cache miss - lade und cache
        val html = api.raw("stream/$slug.html")
        detailCache[slug] = html
        return html
    }

    /** Leert den Cache (z.B. bei Provider-Wechsel). */
    fun clearCache() {
        detailCache.clear()
    }
}
