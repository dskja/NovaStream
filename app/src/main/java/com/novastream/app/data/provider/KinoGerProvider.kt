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
 */
class KinoGerProvider(
    override val id: String = "kinoger",
    override val displayName: String = "KinoGer",
    override val baseUrl: String = "https://kinoger.to",
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    private val api: KinoGerApi = createApi(baseUrl)
    private val hosterResolver = HosterResolver(baseUrl = baseUrl)

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
        val html = api.raw("stream/$slug.html")
        KinoGerScraper.parseSeriesDetail(html, slug)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("KinoGer Details konnten nicht geladen werden", it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        // KinoGer lädt alle Episoden auf einer Seite - keine separate Staffel-URL
        // Staffel-Episoden werden aus der bereits geladenen Detail-Seite gefiltert
        val html = api.raw("stream/$slug.html")
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
            val html = api.raw("stream/${episode.slug}.html")
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
}
