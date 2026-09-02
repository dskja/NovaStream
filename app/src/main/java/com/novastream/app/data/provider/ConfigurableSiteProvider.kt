package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.scraper.SiteProfile
import com.novastream.app.data.scraper.UniversalHtmlScraper
import com.novastream.app.util.EmbedStreamResolver
import com.novastream.app.util.ExtractorRegistry
import com.novastream.app.util.HosterResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

/**
 * Generischer Provider auf Basis von [SiteProfile] + [UniversalHtmlScraper].
 * Mit [appContext] und Mirrors in [ProviderDomainManager] wird automatisch
 * Failover über [MirrorSupport] / [ProviderDomainResolver] aktiviert.
 */
open class ConfigurableSiteProvider(
    private val profile: SiteProfile,
    private val appContext: Context? = null,
    mirrorContentNeedle: String? = ProviderMirrorNeedles.needleFor(profile.id, profile)
) : StreamingProvider, DynamicBaseUrlProvider {

    companion object {
        private const val FETCH_CACHE_SIZE = 8
        private const val FETCH_CACHE_TTL_MS = 5L * 60 * 1000
    }

    override val id: String get() = profile.id
    override val displayName: String get() = profile.displayName
    override val defaultBaseUrl: String get() = profile.baseUrl.trimEnd('/')
    override val changeUrlMutex: Mutex = Mutex()

    override val baseUrl: String
        get() = mirror?.parseBase() ?: defaultBaseUrl

    override val supportsSeries: Boolean get() = profile.supportsSeries
    override val supportsMovies: Boolean get() = profile.supportsMovies
    override val catalogHint: String?
        get() = ProviderCatalogHints.forId(profile.id)

    override val availableGenres: List<com.novastream.app.data.model.Genre>
        get() = ProviderGenres.forId(profile.id)

    private fun List<Series>.tagged(): List<Series> =
        map { if (it.providerId == id) it else it.copy(providerId = id) }

    private val mirror: MirrorSupport? = if (ProviderMirrorNeedles.hasMirrors(profile.id)) {
        val needle = mirrorContentNeedle ?: ProviderMirrorNeedles.needleFor(profile.id, profile)
        MirrorSupport(id, defaultBaseUrl, appContext, needle) { fetchCache.clear() }
    } else null

    private val fetchCache = object : LinkedHashMap<String, Pair<Long, String>>(FETCH_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean =
            size > FETCH_CACHE_SIZE
    }
    private val fetchCacheMutex = Mutex()

    override suspend fun resolveBaseUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        mirror?.activeBase(forceRefresh) ?: defaultBaseUrl
    }

    protected open suspend fun activeBaseUrl(): String =
        mirror?.activeBase() ?: defaultBaseUrl

    /** Site profile with the currently resolved mirror base URL for correct HTML parsing. */
    protected open suspend fun activeProfile(): SiteProfile =
        profile.copy(baseUrl = activeBaseUrl())

    private suspend fun hosterResolver(): HosterResolver =
        HosterResolver(baseUrl = activeBaseUrl())

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        loadCatalogFromPaths(activeProfile()).tagged()
    }

    override suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> {
        if (!supportsMovies) return StreamingProvider.ProviderResult.Success(emptyList())
        return runCatchingProvider {
            val p = activeProfile()
            val moviePaths = buildList {
                if (profile.moviePath.isNotBlank()) add(profile.moviePath)
                IntlCatalogPaths.catalogPaths(profile.id, profile)
                    .filter { it.contains("movie", ignoreCase = true) || it.contains("film", ignoreCase = true) || it.contains("pelicula", ignoreCase = true) }
                    .forEach { add(it) }
                if (isEmpty()) add(profile.homePath.ifBlank { "/" })
            }.distinct()
            var results = emptyList<Series>()
            for (path in moviePaths) {
                results = UniversalHtmlScraper.parseSeriesList(fetch(p.baseUrl + path), p)
                    .map { it.copy(isMovie = true, providerId = id) }
                if (results.isNotEmpty()) break
            }
            results
        }
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            val p = activeProfile()
            val base = p.baseUrl
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val paths = buildList {
                add(profile.searchPath.replace("{query}", encoded))
                if (!profile.searchPath.contains("?s=")) add("/?s=$encoded")
                if (!profile.searchPath.contains("search?q=")) add("/search?q=$encoded")
                if (!profile.searchPath.contains("keyword=")) add("/search?keyword=$encoded")
                if (!profile.searchPath.contains("browse?q=")) add("/browse?q=$encoded")
                add("/filter?keyword=$encoded")
            }.distinct()
            var results = emptyList<Series>()
            for (path in paths) {
                val html = if (profile.searchMethod.equals("POST", true) && profile.searchPostField != null) {
                    postSearch(query.trim())
                } else {
                    fetch(base + path)
                }
                results = UniversalHtmlScraper.parseSeriesList(html, p).tagged()
                if (results.isNotEmpty()) break
            }
            results
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> =
        runCatchingProvider {
            val p = activeProfile()
            val url = resolveDetailUrl(slug)
            val (series, seasons) = UniversalHtmlScraper.parseDetail(fetch(url), p, slug)
            series.copy(providerId = id) to seasons
        }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> =
        runCatchingProvider {
            val p = activeProfile()
            val url = resolveDetailUrl(slug)
            UniversalHtmlScraper.parseEpisodesOnly(fetch(url), p, slug, season)
        }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> =
        runCatchingProvider {
            val base = activeBaseUrl()
            val p = activeProfile()
            val url = when {
                episode.episodeUrl.startsWith("http") -> episode.episodeUrl
                episode.episodeUrl.startsWith("/") -> base + episode.episodeUrl
                episode.episodeUrl.isNotBlank() -> "$base/${episode.episodeUrl.trimStart('/')}"
                else -> resolveDetailUrl(episode.slug)
            }
            val hosters = UniversalHtmlScraper.parseHosters(fetch(url), p)
            if (hosters.isNotEmpty()) hosters
            else {
                val tmdb = slugTmdbId(episode.slug)
                if (tmdb != null) {
                    EmbedStreamResolver.buildHosters(
                        imdbId = null,
                        season = episode.season,
                        episode = episode.number,
                        isMovie = episode.slug.startsWith("movie"),
                        tmdbId = tmdb
                    )
                } else emptyList()
            }
        }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> =
        runCatchingProvider {
            when {
                hoster.redirectUrl.contains("vidsrc") || hoster.name.contains("VidSrc", true) -> {
                    val imdb = Regex("""tt\d+""").find(hoster.redirectUrl)?.value
                    if (imdb != null) {
                        EmbedStreamResolver.resolveByImdb(
                            imdb,
                            season = Regex("""season=(\d+)""").find(hoster.redirectUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                            episode = Regex("""episode=(\d+)""").find(hoster.redirectUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                            isMovie = hoster.redirectUrl.contains("movie")
                        )
                    } else hosterResolver().resolve(hoster.name, hoster.redirectUrl)
                }
                hoster.redirectUrl.contains("vidlink") || hoster.redirectUrl.contains("vidlove") -> {
                    val tmdb = Regex("""/(?:tv|movie)/(\d+)""").find(hoster.redirectUrl)?.groupValues?.get(1)
                    if (tmdb != null) {
                        EmbedStreamResolver.resolveByTmdb(
                            tmdb,
                            season = Regex("""/tv/\d+/(\d+)""").find(hoster.redirectUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                            episode = Regex("""/tv/\d+/\d+/(\d+)""").find(hoster.redirectUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                            isMovie = hoster.redirectUrl.contains("/movie/")
                        ).ifEmpty { hosterResolver().resolve(hoster.name, hoster.redirectUrl) }
                    } else hosterResolver().resolve(hoster.name, hoster.redirectUrl)
                }
                else -> ExtractorRegistry.resolve(hoster.name, hoster.redirectUrl, activeBaseUrl())
            }
        }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.trim().isBlank()) emptyList()
        else {
            val p = activeProfile()
            val paths = ProviderGenrePaths.pathsFor(profile.id, genre.trim(), profile.genrePathTemplate)
            var results = emptyList<Series>()
            for (path in paths) {
                results = UniversalHtmlScraper.parseSeriesList(fetch(p.baseUrl + path), p).tagged()
                if (results.isNotEmpty()) break
            }
            results.ifEmpty { loadHome().getOrNull().orEmpty() }
        }
    }

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = loadHome()
    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = loadHome()

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val p = activeProfile()
        if (page <= 0) {
            loadCatalogFromPaths(p).tagged()
        } else {
            val basePath = profile.homePath.ifBlank { "/" }
            val template = profile.catalogPageTemplate.ifBlank { "${basePath.trimEnd('/')}?page={page}" }
            val path = template.replace("{page}", (page + 1).toString())
            val results = UniversalHtmlScraper.parseSeriesList(fetch(p.baseUrl + path), p)
            if (results.isNotEmpty()) results.tagged()
            else loadCatalogFromPaths(p).tagged()
        }
    }

    override suspend fun loadGenrePage(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.trim().isBlank()) emptyList()
        else {
            val p = activeProfile()
            val paths = ProviderGenrePaths.pathsForPage(
                providerId = profile.id,
                genre = genre.trim(),
                page = page,
                profileDefault = profile.genrePathTemplate,
                profilePageTemplate = profile.genrePageTemplate
            )
            var results = emptyList<Series>()
            for (path in paths) {
                results = UniversalHtmlScraper.parseSeriesList(fetch(p.baseUrl + path), p).tagged()
                if (results.isNotEmpty()) break
            }
            results
        }
    }

    protected open suspend fun resolveDetailUrl(slug: String): String =
        ProviderDetailUrls.resolve(profile.id, activeBaseUrl(), slug)

    private fun slugTmdbId(slug: String): String? {
        val cleaned = slug.removePrefix("tv-").removePrefix("movie-")
        return cleaned.takeIf { it.all { c -> c.isDigit() } && it.isNotBlank() }
    }

    /** Try multiple catalog entry points (intl mirrors often expose /movie, /tv-show, etc.). */
    protected suspend fun loadCatalogFromPaths(p: SiteProfile): List<Series> {
        val paths = IntlCatalogPaths.catalogPaths(profile.id, profile)
        var results = emptyList<Series>()
        for (path in paths) {
            results = UniversalHtmlScraper.parseSeriesList(fetchCached(p.baseUrl + path), p)
            if (results.isNotEmpty()) break
        }
        return results
    }

    protected suspend fun fetch(url: String): String = fetchCached(url)

    private suspend fun fetchCached(url: String): String {
        fetchCacheMutex.withLock {
            fetchCache[url]?.let { (cachedAt, html) ->
                if (System.currentTimeMillis() - cachedAt < FETCH_CACHE_TTL_MS) return html
                fetchCache.remove(url)
            }
        }
        val html = fetchNetwork(url)
        if (html.isNotBlank() && !ProviderHttp.isChallenge(html)) {
            fetchCacheMutex.withLock {
                fetchCache[url] = System.currentTimeMillis() to html
            }
        }
        return html
    }

    private suspend fun fetchNetwork(url: String): String {
        val base = activeBaseUrl()
        return mirror?.fetch(url) ?: ProviderHttp.fetch(url, referer = "$base/", webViewFallback = true, providerId = profile.id)
    }

    private suspend fun postSearch(query: String): String = withContext(Dispatchers.IO) {
        val field = profile.searchPostField ?: return@withContext ""
        val base = activeBaseUrl()
        val body = FormBody.Builder().add(field, query).build()
        val req = Request.Builder()
            .url(base + profile.searchPath.substringBefore("?"))
            .post(body)
            .header("User-Agent", NovaStreamConfig.USER_AGENT)
            .header("Referer", "$base/")
            .build()
        NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
    }
}

// ─── Konkrete FMHY-Provider ─────────────────────────────────────────────────

class HydraHdProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.hydraHd, appContext)

class CinezoProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.cinezo, appContext)

class ShowsStProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.showsSt, appContext)

class PhantomFlixProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.phantomFlix, appContext)

class FlixerProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.flixer, appContext)

class DramaCoolProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.dramaCool, appContext)

class PressPlayProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.pressPlay, appContext)
