package com.novastream.app.data.provider

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Provider für MegaKino.ms.
 * MegaKino bietet Filme und Serien mit deutschen Untertiteln/Dubbing.
 * Unterstützt von AniWorld-Downloader.
 *
 * URL-Schema:
 *   /                          – Startseite mit neuesten/populären Inhalten
 *   /search?query=...          – Suche
 *   /title/{slug}              – Detail-Seite (Film oder Serie)
 *   /title/{slug}/staffel/{n}  – Staffel-Seite
 *   /title/{slug}/staffel/{n}/episode/{m} – Episoden-Seite
 *
 * Hoster: VOE, Filemoon, Vidmoly, Doodstream, Streamtape, Vidoza, SpeedFiles
 */
class MegaKinoProvider(
    override val id: String = "megakino",
    override val displayName: String = "MegaKino",
    override val baseUrl: String = "https://megakino.ms",
    override val supportsSeries: Boolean = true,
    override val supportsMovies: Boolean = true
) : StreamingProvider {

    private val hosterResolver = HosterResolver(baseUrl = baseUrl)

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        parseMegaKinoSeriesList(fetchUrl(baseUrl))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val html = fetchUrl("$baseUrl/filme")
        val list = if (html.isNotBlank()) parseMegaKinoSeriesList(html) else parseMegaKinoSeriesList(fetchUrl(baseUrl))
        list.map { it.copy(isMovie = true, providerId = id) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val html = fetchUrl("$baseUrl/search?query=$encoded")
            parseMegaKinoSeriesList(html)
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        val html = fetchUrl("$baseUrl/title/$slug")
        parseMegaKinoDetail(html, slug)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        val html = fetchUrl("$baseUrl/title/$slug/staffel/$season")
        parseMegaKinoEpisodes(html, slug, season)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        val url = if (episode.episodeUrl.startsWith("http")) {
            episode.episodeUrl
        } else if (episode.episodeUrl.startsWith("/")) {
            baseUrl + episode.episodeUrl
        } else {
            "$baseUrl/title/${episode.slug}/staffel/${episode.season}/episode/${episode.number}"
        }
        val html = fetchUrl(url)
        parseMegaKinoHosters(html)
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

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val path = when {
            page <= 0 -> "/"
            else -> "/?page=${page + 1}"
        }
        parseMegaKinoSeriesList(fetchUrl(baseUrl + path))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadLatestEpisodes(): StreamingProvider.ProviderResult<List<com.novastream.app.data.model.LatestEpisode>> = runCatching {
        val html = fetchUrl(baseUrl)
        val doc = Jsoup.parse(html, baseUrl)
        val results = mutableListOf<com.novastream.app.data.model.LatestEpisode>()
        for (a in doc.select("a[href*=/title/]")) {
            val href = a.absUrl("href")
            val slug = extractMegaKinoSlug(href) ?: continue
            val title = a.text().trim().ifBlank { slugToTitle(slug) }
            if (results.any { it.seriesSlug == slug }) continue
            results.add(
                com.novastream.app.data.model.LatestEpisode(
                    seriesSlug = slug,
                    seriesTitle = title,
                    season = 1,
                    episode = 1,
                    coverUrl = findMegaKinoCover(a)
                )
            )
            if (results.size >= 24) break
        }
        results
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    // ─── HTML Parsing ───────────────────────────────────────────────────────

    private suspend fun fetchUrl(url: String): String {
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
                .header("Referer", baseUrl + "/")
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()
            NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: ""
                else ""
            }
        }
    }

    /** Parst eine Liste von Serien/Filmen. */
    private fun parseMegaKinoSeriesList(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
        val results = linkedMapOf<String, Series>()

        // Phase 1: Links mit /title/ Pfad
        for (a in doc.select("a[href*=/title/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractMegaKinoSlug(href) ?: continue
            if (results.containsKey(slug)) continue

            val title = a.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
                ?: a.selectFirst("h2")?.text()?.trim()?.ifBlank { null }
                ?: a.attr("title")?.ifBlank { null }
                ?: a.text().trim().ifBlank { slugToTitle(slug) }

            val cover = findMegaKinoCover(a)
            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = "/title/$slug"
            )
        }

        // Phase 2: div/card Container mit Serien
        if (results.isEmpty()) {
            for (div in doc.select("div.card, div.movie, div.series, div.item")) {
                val a = div.selectFirst("a[href*=/title/]") ?: continue
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val slug = extractMegaKinoSlug(href) ?: continue
                if (results.containsKey(slug)) continue

                val title = div.selectFirst("h3")?.text()?.trim()
                    ?: div.selectFirst("h2")?.text()?.trim()
                    ?: a.text().trim().ifBlank { slugToTitle(slug) }

                val cover = findMegaKinoCover(div)
                results[slug] = Series(
                    id = slug,
                    title = title,
                    coverUrl = cover,
                    detailUrl = "/title/$slug"
                )
            }
        }

        return results.values.toList()
    }

    /** Parst die Detail-Seite. */
    private fun parseMegaKinoDetail(html: String, slug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(id = slug, title = slugToTitle(slug), coverUrl = null, detailUrl = "/title/$slug") to emptyList()
        }
        val doc = Jsoup.parse(html, baseUrl)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: slugToTitle(slug)

        val cover = doc.selectFirst("img[data-src]")?.let { img ->
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                if (src.startsWith("http")) src else baseUrl + src
            } else null
        } ?: doc.selectFirst("img[src]")?.let { img ->
            val src = img.absUrl("src").ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                if (src.startsWith("http")) src else baseUrl + src
            } else null
        }

        val description = doc.selectFirst(".description")?.text()?.trim()
            ?: doc.selectFirst("p")?.text()?.trim()

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/title/$slug",
            description = description
        )

        val seasons = parseMegaKinoSeasons(doc, slug)
        val isMovie = seasons.size == 1 && seasons.first().episodes.size <= 1 &&
            doc.select("a[href*=/staffel/]").isEmpty()
        return series.copy(isMovie = isMovie) to seasons
    }

    /** Parst Staffeln. */
    private fun parseMegaKinoSeasons(doc: Document, slug: String): List<Season> {
        val seasonNumbers = mutableSetOf<Int>()

        // Staffel-Links: a[href*=/staffel/]
        val pattern = Pattern.compile("/staffel/(\\d+)")
        for (a in doc.select("a[href*=/staffel/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = pattern.matcher(href)
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
            }
        }

        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)

        // Episoden der ersten Staffel
        val currentEpisodes = parseMegaKinoEpisodesFromDoc(doc, slug, seasonNumbers.minOrNull() ?: 1)

        val seasons = mutableListOf<Season>()
        for (n in seasonNumbers.sorted()) {
            val eps = if (currentEpisodes.isNotEmpty() && currentEpisodes.first().season == n) {
                currentEpisodes
            } else {
                emptyList()
            }
            seasons.add(Season(number = n, episodes = eps))
        }

        // Wenn keine Staffeln gefunden: Film (1 Episode) oder aktuelle Episoden
        if (seasons.isEmpty()) {
            if (currentEpisodes.isNotEmpty()) {
                seasons.add(Season(number = 1, episodes = currentEpisodes))
            } else {
                seasons.add(Season(number = 1, episodes = listOf(
                    Episode(
                        number = 1,
                        title = "Film",
                        slug = slug,
                        season = 1,
                        episodeUrl = "/title/$slug"
                    )
                )))
            }
        }

        return seasons
    }

    /** Parst Episoden aus einer Staffel-Seite. */
    private fun parseMegaKinoEpisodes(html: String, slug: String, season: Int): List<Episode> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
        return parseMegaKinoEpisodesFromDoc(doc, slug, season)
    }

    /** Parst Episoden aus einem Document. */
    private fun parseMegaKinoEpisodesFromDoc(doc: Document, slug: String, season: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<Int>()

        // Episoden-Links: a[href*=/episode/]
        val epPattern = Pattern.compile("/staffel/(\\d+)/episode/(\\d+)")
        for (a in doc.select("a[href*=/episode/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = epPattern.matcher(href)
            if (m.find()) {
                val s = m.group(1)?.toIntOrNull() ?: continue
                val ep = m.group(2)?.toIntOrNull() ?: continue
                if (s != season) continue
                if (seen.add(ep)) {
                    val title = a.text()?.trim()?.ifBlank { null } ?: "Folge $ep"
                    episodes.add(Episode(
                        number = ep,
                        title = title,
                        slug = slug,
                        season = s,
                        episodeUrl = m.group(0) ?: ""
                    ))
                }
            }
        }

        return episodes.sortedBy { it.number }
    }

    /** Parst Hoster aus einer Episoden-Seite. */
    private fun parseMegaKinoHosters(html: String): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        // Hoster-Buttons/Links
        for (a in doc.select("a[data-play-url], button[data-play-url]")) {
            val playUrl = a.attr("data-play-url")
            if (playUrl.isBlank()) continue
            val name = a.attr("data-provider-name").ifBlank {
                a.text().trim().ifBlank { "Unknown" }
            }
            val key = "$name-$playUrl"
            if (seen.add(key)) {
                hosters.add(HosterLink(
                    name = name,
                    redirectUrl = playUrl,
                    index = hosters.size
                ))
            }
        }

        // Fallback: iframes
        if (hosters.isEmpty()) {
            for (iframe in doc.select("iframe[src]")) {
                val src = iframe.absUrl("src").ifBlank { iframe.attr("src") }
                if (src.isNotBlank()) {
                    val name = extractHosterNameFromUrl(src)
                    if (seen.add("$name-$src")) {
                        hosters.add(HosterLink(
                            name = name,
                            redirectUrl = src,
                            index = hosters.size
                        ))
                    }
                }
            }
        }

        // Fallback: Hoster-Links mit i.icon
        if (hosters.isEmpty()) {
            for (li in doc.select("li[data-link-target]")) {
                val redirectUrl = li.attr("data-link-target")
                if (redirectUrl.isBlank()) continue
                val icon = li.selectFirst("i.icon")
                val name = icon?.attr("title")?.ifBlank { null }
                    ?: icon?.className()?.substringAfter("icon ")?.ifBlank { null }
                    ?: "Unknown"
                if (seen.add("$name-$redirectUrl")) {
                    hosters.add(HosterLink(
                        name = name,
                        redirectUrl = redirectUrl,
                        index = hosters.size
                    ))
                }
            }
        }

        return hosters
    }

    // ─── Hilfsfunktionen ────────────────────────────────────────────────────

    private fun extractMegaKinoSlug(url: String): String? {
        val pattern = Pattern.compile("/title/([\\w%.-]+?)(?:/|$)")
        val m = pattern.matcher(url)
        if (!m.find()) return null
        val slug = m.group(1)
        return try { java.net.URLDecoder.decode(slug, "UTF-8") } catch (_: Exception) { slug }
    }

    private fun findMegaKinoCover(element: Element): String? {
        val img = element.selectFirst("img[data-src]")
            ?: element.selectFirst("img[src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else baseUrl + src
            }
        }
        return null
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
            url.contains("loadx", ignoreCase = true) -> "LoadX"
            url.contains("luluvdo", ignoreCase = true) -> "Luluvdo"
            else -> {
                try {
                    val uri = java.net.URI(url)
                    uri.host?.substringBefore(".")?.replaceFirstChar { it.uppercase() } ?: "Unknown"
                } catch (_: Exception) { "Unknown" }
            }
        }
    }

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
