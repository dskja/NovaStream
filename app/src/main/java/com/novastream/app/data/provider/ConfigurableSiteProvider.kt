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
 * Failover über [ProviderDomainResolver] aktiviert.
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
        get() = _resolvedBase ?: profile.baseUrl

    override val supportsSeries: Boolean get() = profile.supportsSeries
    override val supportsMovies: Boolean get() = profile.supportsMovies
    override val catalogHint: String?
        get() = null

    @Volatile
    private var _resolvedBase: String? = null

    private val mirrorNeedle: String? = mirrorContentNeedle?.takeIf {
        ProviderMirrorNeedles.hasMirrors(profile.id)
    }

    private val usesMirrors: Boolean
        get() = appContext != null && mirrorNeedle != null

    private val fetchCache = object : LinkedHashMap<String, Pair<Long, String>>(FETCH_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean =
            size > FETCH_CACHE_SIZE
    }
    private val fetchCacheMutex = Mutex()

    init {
        if (usesMirrors) {
            ProviderDomainResolver.registerInvalidator(id) {
                _resolvedBase = null
                fetchCache.clear()
            }
        }
    }

    override suspend fun resolveBaseUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        if (!usesMirrors) return defaultBaseUrl
        if (!forceRefresh && _resolvedBase != null) return _resolvedBase!!
        val resolved = ProviderDomainResolver.resolveActiveBaseUrl(
            providerId = id,
            defaultBaseUrl = defaultBaseUrl,
            contentNeedle = mirrorNeedle.orEmpty(),
            appContext = appContext,
            forceRefresh = forceRefresh
        )
        _resolvedBase = resolved
        resolved
    }

    protected open suspend fun activeBaseUrl(): String =
        if (usesMirrors) resolveBaseUrl() else defaultBaseUrl

    private suspend fun hosterResolver(): HosterResolver =
        HosterResolver(baseUrl = activeBaseUrl())

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val base = activeBaseUrl()
        val homeUrl = base + profile.homePath
        UniversalHtmlScraper.parseSeriesList(fetchCached(homeUrl), profile)
    }.toResult()

    override suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> {
        if (!supportsMovies) return StreamingProvider.ProviderResult.Success(emptyList())
        return runCatching {
            val base = activeBaseUrl()
            val path = profile.moviePath.ifBlank { profile.homePath }
            UniversalHtmlScraper.parseSeriesList(fetch(base + path), profile)
                .map { it.copy(isMovie = true) }
        }.toResult()
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error(
            com.novastream.app.util.AppContext.get().getString(com.novastream.app.R.string.error_empty_search)
        )
        return runCatching {
            val base = activeBaseUrl()
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val paths = buildList {
                add(profile.searchPath.replace("{query}", encoded))
                if (!profile.searchPath.contains("?s=")) add("/?s={query}".replace("{query}", encoded))
                if (!profile.searchPath.contains("search?q=")) add("/search?q={query}".replace("{query}", encoded))
            }.distinct()
            var results = emptyList<Series>()
            for (path in paths) {
                val html = if (profile.searchMethod.equals("POST", true) && profile.searchPostField != null) {
                    postSearch(query.trim())
                } else {
                    fetch(base + path)
                }
                results = UniversalHtmlScraper.parseSeriesList(html, profile)
                if (results.isNotEmpty()) break
            }
            results
        }.toResult()
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> =
        runCatching {
            val url = resolveDetailUrl(slug)
            UniversalHtmlScraper.parseDetail(fetch(url), profile, slug)
        }.toResult()

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> =
        runCatching {
            val url = resolveDetailUrl(slug)
            UniversalHtmlScraper.parseEpisodesOnly(fetch(url), profile, slug, season)
        }.toResult()

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> =
        runCatching {
            val base = activeBaseUrl()
            val url = when {
                episode.episodeUrl.startsWith("http") -> episode.episodeUrl
                episode.episodeUrl.startsWith("/") -> base + episode.episodeUrl
                episode.episodeUrl.isNotBlank() -> "$base/${episode.episodeUrl.trimStart('/')}"
                else -> resolveDetailUrl(episode.slug)
            }
            val hosters = UniversalHtmlScraper.parseHosters(fetch(url), profile)
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
        }.toResult()

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> =
        runCatching {
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
        }.toResult()

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val base = activeBaseUrl()
        val path = profile.genrePathTemplate.replace("{genre}", genre.trim())
        UniversalHtmlScraper.parseSeriesList(fetch(base + path), profile)
            .ifEmpty { loadHome().getOrNull().orEmpty() }
    }.toResult()

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = loadHome()
    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = loadHome()

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val base = activeBaseUrl()
        if (page <= 0) {
            UniversalHtmlScraper.parseSeriesList(fetch(base + profile.homePath), profile)
        } else {
            val template = profile.catalogPageTemplate.ifBlank { "${profile.homePath}?page={page}" }
            val path = template.replace("{page}", (page + 1).toString())
            UniversalHtmlScraper.parseSeriesList(fetch(base + path), profile)
        }
    }.toResult()

    override suspend fun loadGenrePage(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val base = activeBaseUrl()
        if (page <= 0) {
            val path = profile.genrePathTemplate.replace("{genre}", genre.trim())
            UniversalHtmlScraper.parseSeriesList(fetch(base + path), profile)
        } else {
            val template = profile.genrePageTemplate.ifBlank {
                profile.genrePathTemplate + "?page={page}"
            }
            val path = template
                .replace("{genre}", genre.trim())
                .replace("{page}", (page + 1).toString())
            UniversalHtmlScraper.parseSeriesList(fetch(base + path), profile)
        }
    }.toResult()

    protected open suspend fun resolveDetailUrl(slug: String): String {
        val base = activeBaseUrl()
        return when {
            slug.startsWith("tv-") -> "$base/tv/${slug.removePrefix("tv-")}"
            slug.startsWith("movie-") -> "$base/movie/${slug.removePrefix("movie-")}"
            slug.startsWith("http") -> slug
            slug.startsWith("/") -> base + slug
            profile.id == "showsst" -> "$base/watch/tv/$slug"
            profile.id == "hydrahd" -> {
                if (slug.contains("watch-") || slug.contains("-online")) "$base/movie/$slug"
                else "$base/watchseries/$slug"
            }
            profile.id == "dramacool" -> "$base/$slug/"
            else -> "$base/$slug"
        }
    }

    private fun slugTmdbId(slug: String): String? {
        val cleaned = slug.removePrefix("tv-").removePrefix("movie-")
        return cleaned.takeIf { it.all { c -> c.isDigit() } && it.isNotBlank() }
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
        fetchCacheMutex.withLock {
            fetchCache[url] = System.currentTimeMillis() to html
        }
        return html
    }

    private suspend fun fetchNetwork(url: String): String {
        val base = activeBaseUrl()
        var html = ProviderHttp.fetch(url, referer = "$base/", webViewFallback = true)
        if (usesMirrors && (html.isBlank() || ProviderHttp.isChallenge(html))) {
            val refreshedBase = resolveBaseUrl(forceRefresh = true)
            if (refreshedBase != base) {
                val refreshedUrl = url.replace(base, refreshedBase)
                html = ProviderHttp.fetch(refreshedUrl, referer = "$refreshedBase/", webViewFallback = true)
            }
        }
        return html
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

    private fun <T> Result<T>.toResult(): StreamingProvider.ProviderResult<T> =
        ProviderResults.fold(id, this)
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
