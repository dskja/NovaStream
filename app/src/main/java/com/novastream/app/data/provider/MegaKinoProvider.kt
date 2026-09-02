package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver
import com.novastream.app.util.MediaUrls
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Provider für MegaKino (DLE CMS, rotierende Mirror-Domains).
 * Portiert vom BetterStreamflix-Schema: Token-Gate, `/films/`, `/serials/`, POST-Suche.
 */
class MegaKinoProvider(
    override val id: String = "megakino",
    override val displayName: String = "MegaKino",
    override val baseUrl: String = "https://megakino12.com",
    override val supportsSeries: Boolean = true,
    override val supportsMovies: Boolean = true,
    private val appContext: Context? = null
) : StreamingProvider {

    override val catalogHint: String? = ProviderCatalogHints.forId(id)

    override val availableGenres: List<com.novastream.app.data.model.Genre>
        get() = ProviderGenres.forId(id)

    private val resolveMutex = Mutex()

    @Volatile
    private var resolvedBaseUrl: String? = null

    init {
        ProviderDomainResolver.registerInvalidator(id) {
            resolvedBaseUrl = null
            DleSiteSession.invalidate()
        }
    }

    private val hosterResolver get() = HosterResolver(baseUrl = parseBase())

    private suspend fun activeBaseUrl(forceRefresh: Boolean = false): String = resolveMutex.withLock {
        if (!forceRefresh) {
            resolvedBaseUrl?.let { return it }
        }
        val resolved = DleSiteSession.resolveActiveBase(
            providerId = id,
            defaultBaseUrl = baseUrl,
            appContext = appContext,
            contentNeedle = "/films/",
            forceRefresh = forceRefresh
        )
        resolvedBaseUrl = resolved
        resolved
    }

    private fun parseBase(): String = resolvedBaseUrl ?: baseUrl.trimEnd('/')

    private suspend fun fetchPath(path: String, webViewFallback: Boolean = false): String {
        val base = activeBaseUrl()
        return DleSiteSession.fetch(
            urlOrPath = path,
            seedBase = base,
            referer = "$base/",
            providerId = id,
            webViewFallback = webViewFallback
        )
    }

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        var html = fetchPath("/")
        if (html.isBlank() || ProviderHttp.isChallenge(html)) {
            html = fetchPath("/films/", webViewFallback = true)
        }
        parseContentList(html, base).map { it.copy(providerId = id) }
    }

    override suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        parseContentList(fetchPath("/films/"), activeBaseUrl())
            .filter { it.detailUrl.contains("/films/") }
            .map { it.copy(isMovie = true, providerId = id) }
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            val base = activeBaseUrl()
            val html = DleSiteSession.postForm(
                path = "/index.php?do=search",
                seedBase = base,
                fields = mapOf(
                    "do" to "search",
                    "subaction" to "search",
                    "search_start" to "0",
                    "full_search" to "0",
                    "result_from" to "1",
                    "story" to query.trim()
                ),
                providerId = id
            )
            val results = parseContentList(html, base)
            if (results.isNotEmpty()) results
            else parseContentList(fetchPath("/?do=search&subaction=search&story=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}"), base)
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatchingProvider {
        val path = normalizeDetailPath(slug)
        val html = fetchPath(path)
        parseDetail(html, path)
    }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatchingProvider {
        val path = normalizeDetailPath(slug)
        val html = fetchPath(path)
        parseEpisodes(html, path, season)
    }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatchingProvider {
        val path = episode.episodeUrl.substringBefore("|").ifBlank { normalizeDetailPath(episode.slug) }
        val epKey = episode.episodeUrl.substringAfter("|", "ep${episode.number}")
        val html = fetchPath(path)
        parseHosters(html, path, epKey)
    }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatchingProvider {
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.trim().isBlank()) emptyList()
        else {
            val base = activeBaseUrl()
            val paths = ProviderGenrePaths.pathsFor(id, genre.trim())
            var results = emptyList<Series>()
            for (path in paths) {
                val list = parseContentList(fetchPath(path), base).map { it.copy(providerId = id) }
                if (list.isNotEmpty()) {
                    results = list
                    if (path.contains("/genre/", ignoreCase = true) || path.contains(genre.trim(), ignoreCase = true)) break
                }
            }
            results.ifEmpty {
                parseContentList(fetchPath("/"), base).filter {
                    it.title.contains(genre, ignoreCase = true)
                }.map { it.copy(providerId = id) }
            }
        }
    }

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val path = when {
            page <= 0 -> "/films/"
            else -> "/films/page/${page + 1}/"
        }
        parseContentList(fetchPath(path), base).map { it.copy(providerId = id) }
    }

    suspend fun loadLatestEpisodes(): StreamingProvider.ProviderResult<List<com.novastream.app.data.model.LatestEpisode>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchPath("/serials/")
        val doc = Jsoup.parse(html, base)
        val results = mutableListOf<com.novastream.app.data.model.LatestEpisode>()
        for (a in doc.select("div#dle-content a.poster.grid-item[href*=/serials/], a.poster.grid-item[href*=/serials/]")) {
            val href = a.attr("href").trim()
            val path = normalizeDetailPath(href)
            val title = a.selectFirst("h3.poster__title")?.text()?.trim()
                ?: a.text().trim()
            if (results.any { it.seriesSlug == path }) continue
            results.add(
                com.novastream.app.data.model.LatestEpisode(
                    seriesSlug = path,
                    seriesTitle = MediaUrls.sanitizeTitle(title).ifBlank { pathToTitle(path) },
                    season = extractSeasonNumber(title) ?: 1,
                    episode = 1,
                    coverUrl = findCover(a, base)
                )
            )
            if (results.size >= 24) break
        }
        results
    }

    // ─── HTML Parsing ───────────────────────────────────────────────────────

    internal fun parseContentList(html: String, base: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, base)
        val results = linkedMapOf<String, Series>()

        for (a in doc.select("div#dle-content a.poster.grid-item, a.poster.grid-item")) {
            addPosterLink(a, base, results)
        }

        if (results.isEmpty()) {
            for (a in doc.select("a[href*=/films/], a[href*=/serials/]")) {
                addPosterLink(a, base, results)
            }
        }

        return results.values.toList()
    }

    internal fun parseDetail(html: String, path: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return (Series(
                id = path,
                title = pathToTitle(path),
                detailUrl = "/$path"
            ) to emptyList())
        }
        val base = parseBase()
        val doc = Jsoup.parse(html, base)
        val isMovie = path.contains("films/")

        val title = doc.selectFirst("h1[itemprop=name]")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: pathToTitle(path)

        val cover = doc.selectFirst("div.pmovie__poster img[itemprop=image]")?.let { img ->
            MediaUrls.abs(img.attr("data-src").ifBlank { img.attr("src") }, base)
        } ?: doc.selectFirst("img[itemprop=image]")?.let { img ->
            MediaUrls.abs(img.attr("data-src").ifBlank { img.attr("src") }, base)
        }

        val description = doc.selectFirst("div.page__text[itemprop=description]")?.text()?.trim()
            ?: doc.selectFirst("div.page__text")?.text()?.trim()

        val series = Series(
            id = path,
            title = MediaUrls.sanitizeTitle(title).ifBlank { pathToTitle(path) },
            coverUrl = cover,
            detailUrl = "/$path",
            description = description,
            isMovie = isMovie
        )

        val seasons = if (isMovie) {
            listOf(
                Season(
                    number = 1,
                    episodes = listOf(
                        Episode(
                            number = 1,
                            title = title,
                            slug = path,
                            season = 1,
                            episodeUrl = path
                        )
                    )
                )
            )
        } else {
            val seasonNum = extractSeasonNumber(title) ?: 1
            val episodes = parseEpisodesFromDoc(doc, path, seasonNum)
            listOf(Season(number = seasonNum, episodes = episodes))
        }
        return series to seasons
    }

    internal fun parseEpisodes(html: String, path: String, season: Int): List<Episode> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        return parseEpisodesFromDoc(doc, path, season)
    }

    internal fun parseHosters(html: String, path: String, epKey: String? = null): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()
        val isMovie = path.contains("films/")

        if (isMovie) {
            val tabNames = doc.select("div.tabs-block__select span").map { it.text().trim() }
            doc.select("div.tabs-block__content").forEachIndexed { index, content ->
                val iframe = content.selectFirst("iframe")
                val src = iframe?.attr("data-src")?.takeIf { it.isNotBlank() }
                    ?: iframe?.attr("src")?.takeIf { it.isNotBlank() }
                if (!src.isNullOrBlank()) {
                    val name = tabNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Server ${index + 1}"
                    if (seen.add("$name-$src")) {
                        hosters.add(HosterLink(name = name, redirectUrl = src, index = hosters.size))
                    }
                }
            }
        } else {
            val selectId = epKey?.takeIf { it.startsWith("ep") } ?: "ep1"
            doc.select("select#$selectId option").forEach { option ->
                val url = option.attr("value").trim()
                val name = option.text().trim()
                if (url.startsWith("http") && seen.add("$name-$url")) {
                    hosters.add(HosterLink(name = name.ifBlank { "Unknown" }, redirectUrl = url, index = hosters.size))
                }
            }
        }

        if (hosters.isEmpty()) {
            doc.select("iframe[data-src], iframe[src]").forEach { iframe ->
                val src = iframe.attr("data-src").ifBlank { iframe.attr("src") }
                if (src.startsWith("http") && seen.add(src)) {
                    hosters.add(
                        HosterLink(
                            name = extractHosterNameFromUrl(src),
                            redirectUrl = src,
                            index = hosters.size
                        )
                    )
                }
            }
        }
        return hosters
    }

    // ─── Hilfsfunktionen ────────────────────────────────────────────────────

    private fun addPosterLink(a: Element, base: String, results: LinkedHashMap<String, Series>) {
        val href = a.attr("href").trim()
        if (href.isBlank() || href.contains("rss.xml")) return
        val path = normalizeDetailPath(href)
        if (!path.contains("films/") && !path.contains("serials/")) return
        if (results.containsKey(path)) return

        val title = a.selectFirst("h3.poster__title")?.text()?.trim()
            ?: a.attr("title").trim()
            ?: a.text().trim()
            ?: pathToTitle(path)

        results[path] = Series(
            id = path,
            title = MediaUrls.sanitizeTitle(title).ifBlank { pathToTitle(path) },
            coverUrl = findCover(a, base),
            detailUrl = "/$path",
            isMovie = path.contains("films/")
        )
    }

    private fun parseEpisodesFromDoc(doc: Document, path: String, season: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        doc.select("select.se-select option").forEach { option ->
            val value = option.attr("value").trim()
            val name = option.text().trim()
            if (!value.startsWith("ep")) return@forEach
            val number = Regex("""ep(\d+)""").find(value)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""Episode\s+(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@forEach
            episodes.add(
                Episode(
                    number = number,
                    title = name.ifBlank { "Episode $number" },
                    slug = path,
                    season = season,
                    episodeUrl = "$path|$value"
                )
            )
        }
        return episodes.sortedBy { it.number }
    }

    private fun normalizeDetailPath(raw: String): String {
        val trimmed = raw.trim().removePrefix("/")
        return when {
            trimmed.startsWith("http") -> trimmed.substringAfter("://").substringAfter("/")
            else -> trimmed.substringBefore("#").substringBefore("?")
        }
    }

    private fun findCover(element: Element, base: String): String? {
        val img = element.selectFirst("div.poster__img img")
            ?: element.selectFirst("img[data-src]")
            ?: element.selectFirst("img[src]")
        if (img == null) return null
        val src = img.attr("data-src").ifBlank { img.attr("src") }
        return MediaUrls.abs(src, base)
    }

    private fun extractSeasonNumber(title: String): Int? {
        val match = Regex("""(\d+)\s*Staffel""").find(title) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun pathToTitle(path: String): String {
        val slug = path.substringAfterLast('/').removeSuffix(".html")
        val name = slug.substringAfter('-').ifBlank { slug }
        return name.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun extractHosterNameFromUrl(url: String): String {
        return when {
            url.contains("voe", ignoreCase = true) -> "VOE"
            url.contains("streamtape", ignoreCase = true) -> "Streamtape"
            url.contains("filemoon", ignoreCase = true) -> "Filemoon"
            url.contains("vidmoly", ignoreCase = true) -> "Vidmoly"
            url.contains("dood", ignoreCase = true) -> "Doodstream"
            url.contains("vidoza", ignoreCase = true) -> "Vidoza"
            url.contains("speedfiles", ignoreCase = true) -> "SpeedFiles"
            else -> {
                try {
                    val uri = java.net.URI(url)
                    uri.host?.substringBefore(".")?.replaceFirstChar { it.uppercase() } ?: "Unknown"
                } catch (_: Exception) {
                    "Unknown"
                }
            }
        }
    }
}
