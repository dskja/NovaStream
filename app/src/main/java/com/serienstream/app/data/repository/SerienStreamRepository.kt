package com.serienstream.app.data.repository

import com.serienstream.app.data.api.NetworkModule
import com.serienstream.app.data.api.SerienStreamApi
import com.serienstream.app.data.api.SerienStreamScraper
import com.serienstream.app.data.model.Episode
import com.serienstream.app.data.model.HosterLink
import com.serienstream.app.data.model.Season
import com.serienstream.app.data.model.Series
import com.serienstream.app.data.model.StreamSource
import com.serienstream.app.util.HosterResolver

/**
 * Repository: kapselt API + Scraper + Hoster-Auflösung.
 * Fängt alle Fehler als [RepoResult] ab.
 */
class SerienStreamRepository(
    private val api: SerienStreamApi = NetworkModule.serienStreamApi,
    private val hosterResolver: HosterResolver = HosterResolver()
) {

    sealed class RepoResult<out T> {
        data class Success<T>(val data: T) : RepoResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>()
    }

    suspend fun loadHome(): RepoResult<List<Series>> = runCatching {
        SerienStreamScraper.parseSeriesList(api.home())
    }.fold(
        onSuccess = { RepoResult.Success(it) },
        onFailure = { RepoResult.Error("Startseite konnte nicht geladen werden", it) }
    )

    suspend fun search(query: String): RepoResult<List<Series>> = runCatching {
        SerienStreamScraper.parseSeriesList(api.search(query.trim()))
    }.fold(
        onSuccess = { RepoResult.Success(it) },
        onFailure = { RepoResult.Error("Suche fehlgeschlagen", it) }
    )

    suspend fun loadSeriesDetail(slug: String): RepoResult<Pair<Series, List<Season>>> = runCatching {
        SerienStreamScraper.parseSeriesDetail(api.seriesDetail(slug), slug)
    }.fold(
        onSuccess = { RepoResult.Success(it) },
        onFailure = { RepoResult.Error("Serien-Details konnten nicht geladen werden", it) }
    )

    /** Lädt eine bestimmte Staffel-Seite und liefert die Episoden. */
    suspend fun loadSeason(slug: String, season: Int): RepoResult<List<Episode>> = runCatching {
        val html = api.season(slug, season)
        SerienStreamScraper.parseSeasonEpisodes(html, slug, season)
    }.fold(
        onSuccess = { RepoResult.Success(it) },
        onFailure = { RepoResult.Error("Staffel konnte nicht geladen werden", it) }
    )

    /**
     * Lädt die Episoden-Seite und parst die Hoster-Buttons.
     * Die Episode muss slug, season und episodeUrl enthalten.
     */
    suspend fun loadHosters(episode: Episode): RepoResult<List<HosterLink>> {
        return runCatching {
            val html = api.episode(episode.slug, episode.season, episode.number)
            SerienStreamScraper.parseHosters(html)
        }.fold(
            onSuccess = { RepoResult.Success(it) },
            onFailure = { RepoResult.Error("Hoster konnten nicht geladen werden", it) }
        )
    }

    /**
     * Löst einen Hoster-Link zu Stream-Quellen auf.
     * Der data-play-url ist ein Redirect (/r?t=eyJ...) der zum Hoster führt.
     */
    suspend fun resolveHoster(hoster: HosterLink): RepoResult<List<StreamSource>> = runCatching {
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }.fold(
        onSuccess = { RepoResult.Success(it) },
        onFailure = { RepoResult.Error("Stream-URL konnte nicht aufgelöst werden", it) }
    )
}
