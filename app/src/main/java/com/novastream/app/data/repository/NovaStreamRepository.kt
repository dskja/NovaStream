package com.novastream.app.data.repository

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.api.NovaStreamApi
import com.novastream.app.data.api.NovaStreamScraper
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver

/**
 * Repository: kapselt API + Scraper + Hoster-Auflösung.
 * Fängt alle Fehler als [RepoResult] ab.
 */
class NovaStreamRepository(
    private val api: NovaStreamApi = NetworkModule.novaStreamApi,
    private val hosterResolver: HosterResolver = HosterResolver()
) {

    sealed class RepoResult<out T> {
        data class Success<T>(val data: T) : RepoResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>()
    }

    suspend fun loadHome(): RepoResult<List<Series>> = runCatching {
        NovaStreamScraper.parseSeriesList(api.home())
    }.fold(
        onSuccess = { RepoResult.Success(it) },
        onFailure = { RepoResult.Error("Startseite konnte nicht geladen werden", it) }
    )

    suspend fun search(query: String): RepoResult<List<Series>> {
        if (query.trim().isBlank()) return RepoResult.Error("Leere Suche")
        return runCatching {
            NovaStreamScraper.parseSeriesList(api.search(query.trim()))
        }.fold(
            onSuccess = { RepoResult.Success(it) },
            onFailure = { RepoResult.Error("Suche fehlgeschlagen", it) }
        )
    }

    suspend fun loadSeriesDetail(slug: String): RepoResult<Pair<Series, List<Season>>> = runCatching {
        NovaStreamScraper.parseSeriesDetail(api.seriesDetail(slug), slug)
    }.fold(
        onSuccess = { RepoResult.Success(it) },
        onFailure = { RepoResult.Error("Serien-Details konnten nicht geladen werden", it) }
    )

    /** Lädt eine bestimmte Staffel-Seite und liefert die Episoden. */
    suspend fun loadSeason(slug: String, season: Int): RepoResult<List<Episode>> = runCatching {
        val html = api.season(slug, season)
        NovaStreamScraper.parseSeasonEpisodes(html, slug, season)
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
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.d("NovaStreamRepo", "loadHosters: slug=${episode.slug} season=${episode.season} ep=${episode.number}")
            }
            val html = api.episode(episode.slug, episode.season, episode.number)
            val hosters = NovaStreamScraper.parseHosters(html)
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.d("NovaStreamRepo", "loadHosters: found ${hosters.size} hosters")
            }
            hosters
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
