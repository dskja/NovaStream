package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver
import com.novastream.app.util.MediaUrls
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Dedicated HDFilme scraper ported from BetterStreamflix HDFilmeProvider.
 * Replaces the generic ConfigurableSiteProvider profile which used wrong search/hoster paths.
 */
class HdFilmeProvider(
    private val appContext: Context? = null,
    override val id: String = "hdfilme",
    override val displayName: String = "HDFilme",
    override val baseUrl: String = "https://hdfilme.win",
    override val supportsSeries: Boolean = true,
    override val supportsMovies: Boolean = true
) : StreamingProvider {

    override val catalogHint: String? = ProviderCatalogHints.forId(id)
    override val availableGenres: List<com.novastream.app.data.model.Genre>
        get() = ProviderGenres.forId(id)

    private val mirror = MirrorSupport(id, baseUrl, appContext, "stream")
    private val hosterResolver get() = HosterResolver(baseUrl = mirror.parseBase())

    private suspend fun activeBase(): String = mirror.activeBase()
    private fun parseBase(): String = mirror.parseBase()
    private suspend fun fetch(path: String): String = mirror.fetch(
        if (path.startsWith("http")) path else activeBase().trimEnd('/') + "/" + path.trimStart('/')
    )

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBase()
        val home = mirror.requireCatalogHtml(fetchPage = { fetch("/") }, fallbackUrl = "$base/")
        val movies = fetch("/filme1/")
        val series = fetch("/serien/")
        val merged = linkedMapOf<String, Series>()
        parseGrid(home, base).forEach { merged.putIfAbsent(it.id, it.copy(providerId = id)) }
        parseGrid(series, base).forEach { merged.putIfAbsent(it.id, it.copy(providerId = id)) }
        parseGrid(movies, base).forEach { merged.putIfAbsent(it.id, it.copy(isMovie = true, providerId = id)) }
        merged.values.toList()
    }

    override suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        parseGrid(fetch("/filme1/"), activeBase()).map { it.copy(isMovie = true, providerId = id) }
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            val q = query.trim()
            val base = activeBase()
            // Sitemap search (BetterStreamflix) — DLE search is broken on the site
            val fromSitemap = searchViaSitemap(q, base)
            if (fromSitemap.isNotEmpty()) return@runCatchingProvider fromSitemap
            val catalog = parseGrid(fetch("/filme1/"), base) + parseGrid(fetch("/serien/"), base)
            catalog.filter { it.title.contains(q, ignoreCase = true) }.map { it.copy(providerId = id) }
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> =
        runCatchingProvider {
            val path = normalizePath(slug)
            val html = fetch(path)
            parseDetail(html, path)
        }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> =
        runCatchingProvider {
            val path = normalizePath(slug)
            val html = fetch(path)
            val (_, seasons) = parseDetail(html, path)
            seasons.find { it.number == season }?.episodes ?: emptyList()
        }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> =
        runCatchingProvider {
            val path = episode.episodeUrl.substringBefore("#").ifBlank { normalizePath(episode.slug) }
            val html = fetch(path)
            if (episode.episodeUrl.contains("#s") || !path.contains("filme")) {
                parseSeriesHosters(html, episode)
            } else {
                parseMovieHosters(html)
            }
        }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> =
        runCatchingProvider {
            hosterResolver.resolve(hoster.name, hoster.redirectUrl)
        }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.isBlank()) emptyList()
        else {
            val base = activeBase()
            val paths = ProviderGenrePaths.pathsFor(id, genre.trim())
            var results = emptyList<Series>()
            for (p in paths) {
                results = parseGrid(fetch(p), base).map { it.copy(providerId = id) }
                if (results.isNotEmpty()) break
            }
            results
        }
    }

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val n = (page + 1).coerceAtLeast(1)
        val path = if (n <= 1) "/filme1/" else "/filme1/page/$n/"
        parseGrid(fetch(path), activeBase()).map { it.copy(isMovie = true, providerId = id) }
    }

    // ─── Parsing (BetterStreamflix selectors) ───────────────────────────────

    private fun parseGrid(html: String, base: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, base)
        val results = linkedMapOf<String, Series>()
        for (el in doc.select("div.listing.grid[id=dle-content] div.item.relative.mt-3, div.item.relative.mt-3")) {
            parseGridItem(el, base)?.let { results.putIfAbsent(it.id, it) }
        }
        if (results.isEmpty()) {
            for (a in doc.select("a.block.relative[href]")) {
                val href = a.attr("href").trim()
                if (href.isBlank()) continue
                val title = a.parent()?.selectFirst("h3")?.text()?.trim()
                    ?: a.attr("title").ifBlank { null }
                    ?: continue
                val id = normalizePath(href)
                results.putIfAbsent(
                    id,
                    Series(
                        id = id,
                        title = MediaUrls.sanitizeTitle(title),
                        coverUrl = MediaUrls.abs(a.selectFirst("img")?.attr("data-src"), base),
                        detailUrl = "/$id",
                        isMovie = !href.contains("/serien/") && !href.contains("serie")
                    )
                )
            }
        }
        return results.values.toList()
    }

    private fun parseGridItem(el: Element, base: String): Series? {
        val title = el.selectFirst("h3.line-clamp-2, h3")?.text()?.trim() ?: return null
        val href = el.selectFirst("a.block.relative[href], a[href]")?.attr("href")?.trim() ?: return null
        val id = normalizePath(href)
        val poster = MediaUrls.abs(el.selectFirst("img")?.attr("data-src"), base)
        val isSeries = href.contains("/serien/") || title.contains("Staffel", ignoreCase = true)
        return Series(
            id = id,
            title = MediaUrls.sanitizeTitle(title),
            coverUrl = poster,
            detailUrl = "/$id",
            isMovie = !isSeries
        )
    }

    private fun parseDetail(html: String, path: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(id = path, title = path.substringAfterLast('/'), detailUrl = "/$path") to emptyList()
        }
        val base = parseBase()
        val doc = Jsoup.parse(html, base)
        val title = doc.selectFirst("h1.font-bold, h1")?.text()?.trim()
            ?.replace(Regex("""\s*hdfilme\s*$""", RegexOption.IGNORE_CASE), "")
            ?: path.substringAfterLast('/')
        val cover = MediaUrls.abs(
            doc.selectFirst("figure.inline-block img")?.attr("data-src")
                ?: doc.selectFirst("img[data-src]")?.attr("data-src"),
            base
        )
        val description = doc.selectFirst("div.overview p, .overview p")?.text()?.trim()
        val isTv = isTvShowDocument(doc)
        val series = Series(
            id = path,
            title = MediaUrls.sanitizeTitle(title),
            coverUrl = cover,
            detailUrl = "/$path",
            description = description,
            isMovie = !isTv
        )
        if (!isTv) {
            return series to listOf(
                Season(
                    number = 1,
                    episodes = listOf(
                        Episode(number = 1, title = title, slug = path, season = 1, episodeUrl = path)
                    )
                )
            )
        }
        val seasons = parseSeasons(doc, path)
        return series to seasons
    }

    private fun parseSeasons(doc: Document, path: String): List<Season> {
        val seasons = mutableListOf<Season>()
        // Local accordion seasons
        doc.select("div#se-accordion div.su-spoiler").forEach { spoiler ->
            val seasonTitle = spoiler.selectFirst("div.su-spoiler-title")?.text()?.trim().orEmpty()
            val seasonNum = Regex("""Staffel\s+(\d+)""").find(seasonTitle)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@forEach
            val episodes = mutableListOf<Episode>()
            val content = spoiler.selectFirst("div.su-spoiler-content")?.html().orEmpty()
            Regex("""(\d+)x(\d+)\s+Episode\s+\d+""").findAll(content).forEach { m ->
                val ep = m.groupValues[2].toIntOrNull() ?: return@forEach
                episodes.add(
                    Episode(
                        number = ep,
                        title = "Episode $ep",
                        slug = path,
                        season = seasonNum,
                        episodeUrl = "$path#s${seasonNum}e$ep"
                    )
                )
            }
            seasons.add(Season(number = seasonNum, episodes = episodes.distinctBy { it.number }.sortedBy { it.number }))
        }
        if (seasons.isNotEmpty()) return seasons.sortedBy { it.number }

        // meinecloud serial iframe seasons (BetterStreamflix)
        val imdbId = extractImdbId(doc) ?: return listOf(Season(number = 1, episodes = emptyList()))
        // Can't fetch meinecloud synchronously here without suspend — leave empty episodes; loadSeason/hosters refetch
        return listOf(Season(number = 1, episodes = listOf(
            Episode(number = 1, title = "Episode 1", slug = path, season = 1, episodeUrl = "$path#s1e1")
        )))
    }

    private suspend fun parseMovieHosters(html: String): List<HosterLink> {
        val doc = Jsoup.parse(html, parseBase())
        val iframeSrc = doc.selectFirst("iframe[src*=meinecloud.click]")?.attr("src") ?: return emptyList()
        val embedUrl = normalizeAbs(iframeSrc)
        val embedHtml = mirror.fetch(embedUrl)
        val embedDoc = Jsoup.parse(embedHtml, embedUrl)
        return embedDoc.select("ul._player-mirrors li[data-link]")
            .filterNot { it.hasClass("fullhd") || it.text().contains("4K Server", ignoreCase = true) }
            .mapIndexedNotNull { index, li ->
                val dataLink = li.attr("data-link").trim()
                if (dataLink.isBlank()) return@mapIndexedNotNull null
                val url = when {
                    dataLink.startsWith("//") -> "https:$dataLink"
                    dataLink.startsWith("http") -> dataLink
                    else -> "https://$dataLink"
                }
                HosterLink(
                    name = li.ownText().ifBlank { li.text() }.trim().ifBlank { "Server" },
                    redirectUrl = url,
                    index = index
                )
            }
    }

    private suspend fun parseSeriesHosters(html: String, episode: Episode): List<HosterLink> {
        val doc = Jsoup.parse(html, parseBase())
        val season = episode.season
        val epNum = episode.number

        // meinecloud serial data-link
        extractImdbId(doc)?.let { imdb ->
            val serialHtml = mirror.fetch("https://meinecloud.click/serial/${imdb.removePrefix("tt")}")
            if (serialHtml.isNotBlank()) {
                val serialDoc = Jsoup.parse(serialHtml)
                val seasonEl = serialDoc.select("._season-eps").firstOrNull { se ->
                    serialSeasonNumber(se, serialDoc) == season
                }
                val epEl = seasonEl?.select("._ep")?.firstOrNull { el ->
                    el.selectFirst("._ep-n")?.text()?.trim()?.toIntOrNull() == epNum
                }
                val streamUrl = epEl?.attr("data-link")?.trim().orEmpty()
                if (streamUrl.isNotBlank()) {
                    val url = normalizeAbs(streamUrl)
                    val name = url.toHttpUrlOrNull()?.host?.substringBefore('.')?.replaceFirstChar { it.uppercase() }
                        ?: "Server"
                    return listOf(HosterLink(name = name, redirectUrl = url, index = 0))
                }
            }
        }

        val servers = mutableListOf<HosterLink>()
        doc.select("div#se-accordion div.su-spoiler").forEach { spoiler ->
            val seasonTitle = spoiler.selectFirst("div.su-spoiler-title")?.text()?.trim() ?: return@forEach
            val current = Regex("""Staffel\s+(\d+)""").find(seasonTitle)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@forEach
            if (current != season) return@forEach
            val content = spoiler.selectFirst("div.su-spoiler-content") ?: return@forEach
            val episodeRegex = Regex("""${season}x${epNum}\s+Episode\s+\d+""")
            content.html().split("<br>").forEach { line ->
                if (episodeRegex.find(line) == null) return@forEach
                Jsoup.parse(line).select("a[href]").forEach { link ->
                    val serverUrl = link.attr("href").trim()
                    val serverName = link.text().trim()
                    if (serverUrl.contains("/engine/player.php")) return@forEach
                    if (serverName.contains("Player HD", ignoreCase = true)) return@forEach
                    if (serverUrl.isBlank()) return@forEach
                    servers.add(
                        HosterLink(
                            name = serverName.ifBlank { "Server" },
                            redirectUrl = normalizeAbs(serverUrl),
                            index = servers.size
                        )
                    )
                }
            }
        }
        return servers.distinctBy { it.redirectUrl }
    }

    private suspend fun searchViaSitemap(query: String, base: String): List<Series> {
        val tokens = query.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val entries = mutableListOf<String>()
        for (sitemap in listOf("news_pages.xml", "news_pages2.xml")) {
            val body = fetch("/$sitemap")
            Regex("""<loc>\s*(.*?)\s*</loc>""").findAll(body).forEach { entries += it.groupValues[1].trim() }
        }
        val matches = entries.asSequence()
            .filter { url ->
                val slug = runCatching {
                    URLDecoder.decode(url.substringAfterLast('/'), StandardCharsets.UTF_8.name())
                }.getOrDefault(url).lowercase()
                tokens.all { slug.contains(it) }
            }
            .take(12)
            .toList()
        return matches.mapNotNull { url ->
            val html = fetch(url)
            if (html.isBlank()) return@mapNotNull null
            val doc = Jsoup.parse(html, base)
            val title = doc.selectFirst("h1.font-bold, h1")?.text()?.trim()
                ?.replace(Regex("""\s*hdfilme\s*$""", RegexOption.IGNORE_CASE), "")
                ?: return@mapNotNull null
            val path = normalizePath(url)
            Series(
                id = path,
                title = MediaUrls.sanitizeTitle(title),
                coverUrl = MediaUrls.abs(doc.selectFirst("figure.inline-block img")?.attr("data-src"), base),
                detailUrl = "/$path",
                isMovie = !isTvShowDocument(doc),
                providerId = id
            )
        }
    }

    private fun isTvShowDocument(doc: Document): Boolean =
        doc.selectFirst("a[href*=themoviedb.org/tv/], .info a[href$=/serien/], #serial_iframe, div#se-accordion") != null

    private fun extractImdbId(doc: Document): String? {
        val scripts = doc.select("script").joinToString("\n") { it.data() }
        return Regex("""var\s+imdb\s*=\s*['"](tt\d+)['"]""").find(scripts)?.groupValues?.get(1)
    }

    private fun serialSeasonNumber(season: Element, serialDoc: Document): Int? {
        val seasonId = season.attr("data-season")
        val tabNumber = serialDoc.selectFirst("._stab[data-season='$seasonId']")?.text()
            ?.let { Regex("""S(\d+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return tabNumber ?: season.selectFirst("._ep[data-label]")?.attr("data-label")
            ?.let { Regex("""S(\d+)\s*E\d+""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull() }
    }

    private fun normalizePath(raw: String): String {
        val t = raw.trim().removePrefix("/")
        return when {
            t.startsWith("http") -> t.substringAfter("://").substringAfter("/")
            else -> t.substringBefore("#").substringBefore("?")
        }
    }

    private fun normalizeAbs(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http") -> url
        else -> parseBase().trimEnd('/') + "/" + url.trimStart('/')
    }
}
