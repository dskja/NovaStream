package com.novastream.app.data.provider

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
import com.novastream.app.util.HosterResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

/**
 * Generischer Provider auf Basis von [SiteProfile] + [UniversalHtmlScraper].
 * Ein Profil = ein voll funktionaler Streaming-Provider.
 */
open class ConfigurableSiteProvider(
    private val profile: SiteProfile
) : StreamingProvider {

    override val id: String get() = profile.id
    override val displayName: String get() = profile.displayName
    override val baseUrl: String get() = profile.baseUrl
    override val supportsSeries: Boolean get() = profile.supportsSeries
    override val supportsMovies: Boolean get() = profile.isMovieFocused || !profile.supportsSeries
    override val catalogHint: String?
        get() = if (supportsMovies && supportsSeries) "Filme & Serien"
        else if (supportsMovies) "Filme"
        else null

    private val hosterResolver = HosterResolver(baseUrl = profile.baseUrl)

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        UniversalHtmlScraper.parseSeriesList(fetch(profile.baseUrl + profile.homePath), profile)
    }.toResult()

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            val html = if (profile.searchMethod.equals("POST", true) && profile.searchPostField != null) {
                postSearch(query.trim())
            } else {
                val path = profile.searchPath.replace("{query}", java.net.URLEncoder.encode(query.trim(), "UTF-8"))
                fetch(profile.baseUrl.trimEnd('/') + path)
            }
            UniversalHtmlScraper.parseSeriesList(html, profile)
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
            val url = when {
                episode.episodeUrl.startsWith("http") -> episode.episodeUrl
                episode.episodeUrl.startsWith("/") -> profile.baseUrl.trimEnd('/') + episode.episodeUrl
                episode.episodeUrl.isNotBlank() -> profile.baseUrl.trimEnd('/') + "/" + episode.episodeUrl.trimStart('/')
                else -> resolveDetailUrl(episode.slug)
            }
            val hosters = UniversalHtmlScraper.parseHosters(fetch(url), profile)
            if (hosters.isNotEmpty()) hosters
            else {
                // TMDb-ID aus Slug? → Embed-Fallbacks
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
                    } else hosterResolver.resolve(hoster.name, hoster.redirectUrl)
                }
                hoster.redirectUrl.contains("vidlink") || hoster.redirectUrl.contains("vidlove") -> {
                    val tmdb = Regex("""/(?:tv|movie)/(\d+)""").find(hoster.redirectUrl)?.groupValues?.get(1)
                    if (tmdb != null) {
                        EmbedStreamResolver.resolveByTmdb(
                            tmdb,
                            season = Regex("""/tv/\d+/(\d+)""").find(hoster.redirectUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                            episode = Regex("""/tv/\d+/\d+/(\d+)""").find(hoster.redirectUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                            isMovie = hoster.redirectUrl.contains("/movie/")
                        ).ifEmpty { hosterResolver.resolve(hoster.name, hoster.redirectUrl) }
                    } else hosterResolver.resolve(hoster.name, hoster.redirectUrl)
                }
                else -> hosterResolver.resolve(hoster.name, hoster.redirectUrl)
            }
        }.toResult()

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val path = profile.genrePathTemplate.replace("{genre}", genre.trim())
        UniversalHtmlScraper.parseSeriesList(fetch(profile.baseUrl.trimEnd('/') + path), profile)
            .ifEmpty { loadHome().getOrNull().orEmpty() }
    }.toResult()

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = loadHome()
    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = loadHome()

    protected open fun resolveDetailUrl(slug: String): String {
        val base = profile.baseUrl.trimEnd('/')
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

    protected suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", NovaStreamConfig.USER_AGENT)
            .header("Referer", profile.baseUrl + "/")
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .build()
        NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
    }

    private suspend fun postSearch(query: String): String = withContext(Dispatchers.IO) {
        val field = profile.searchPostField ?: return@withContext ""
        val body = FormBody.Builder().add(field, query).build()
        val req = Request.Builder()
            .url(profile.baseUrl.trimEnd('/') + profile.searchPath.substringBefore("?"))
            .post(body)
            .header("User-Agent", NovaStreamConfig.USER_AGENT)
            .header("Referer", profile.baseUrl + "/")
            .build()
        NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
    }

    private fun <T> Result<T>.toResult(): StreamingProvider.ProviderResult<T> = fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )
}

// ─── Konkrete FMHY-Provider ─────────────────────────────────────────────────

class HydraHdProvider : ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.hydraHd)
class CinezoProvider : ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.cinezo)
class ShowsStProvider : ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.showsSt)
class PhantomFlixProvider : ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.phantomFlix)
class FlixerProvider : ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.flixer)
class DramaCoolProvider : ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.dramaCool)
class PressPlayProvider : ConfigurableSiteProvider(com.novastream.app.data.scraper.SiteProfiles.pressPlay)
