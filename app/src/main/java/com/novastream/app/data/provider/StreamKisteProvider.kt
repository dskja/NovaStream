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
 * Provider für StreamKiste.de (stream-kiste.de).
 * Bietet Filme und TV-Serien mit deutscher Synchronisation.
 *
 * URL-Schema:
 *   /                          – Startseite
 *   /serien                    – Serien-Übersicht
 *   /serien/{slug}             – Serien-Detail
 *   /serien/{slug}/staffel-{n} – Staffel-Seite
 *   /filme/{slug}              – Film-Detail
 *   /search?q=...              – Suche
 *
 * Hoster: VOE, Streamtape, Filemoon, Doodstream, Vidoza, Mixdrop
 */
class StreamKisteProvider(
    override val id: String = "streamkiste",
    override val displayName: String = "StreamKiste",
    override val baseUrl: String = "https://stream-kiste.de",
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    private val hosterResolver = HosterResolver(baseUrl = baseUrl)

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val html = fetchUrl("$baseUrl/serien")
        parseStreamKisteSeriesList(html)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val html = fetchUrl("$baseUrl/search?q=$encoded")
            parseStreamKisteSeriesList(html)
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        val html = fetchUrl("$baseUrl/serien/$slug")
        parseStreamKisteDetail(html, slug)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        val html = fetchUrl("$baseUrl/serien/$slug/staffel-$season")
        parseStreamKisteEpisodes(html, slug, season)
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
            "$baseUrl/serien/${episode.slug}/staffel-${episode.season}/episode-${episode.number}"
        }
        val html = fetchUrl(url)
        parseStreamKisteHosters(html)
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
    private fun parseStreamKisteSeriesList(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
        val results = linkedMapOf<String, Series>()

        // Phase 1: Links mit /serien/ oder /filme/ Pfad
        for (a in doc.select("a[href*=/serien/], a[href*=/filme/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractStreamKisteSlug(href) ?: continue
            if (results.containsKey(slug)) continue
            // Skip season/episode links
            if (href.contains("/staffel-")) continue

            val title = a.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
                ?: a.selectFirst("h2")?.text()?.trim()?.ifBlank { null }
                ?: a.attr("title")?.ifBlank { null }
                ?: a.text().trim().ifBlank { slugToTitle(slug) }

            val cover = findStreamKisteCover(a)
            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = if (href.contains("/filme/")) "/filme/$slug" else "/serien/$slug"
            )
        }

        // Phase 2: div/card Container
        if (results.isEmpty()) {
            for (div in doc.select("div.card, div.movie, div.series, div.item, article")) {
                val a = div.selectFirst("a[href]") ?: continue
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (!href.contains("/serien/") && !href.contains("/filme/")) continue
                val slug = extractStreamKisteSlug(href) ?: continue
                if (results.containsKey(slug)) continue

                val title = div.selectFirst("h3")?.text()?.trim()
                    ?: div.selectFirst("h2")?.text()?.trim()
                    ?: a.text().trim().ifBlank { slugToTitle(slug) }

                val cover = findStreamKisteCover(div)
                results[slug] = Series(
                    id = slug,
                    title = title,
                    coverUrl = cover,
                    detailUrl = if (href.contains("/filme/")) "/filme/$slug" else "/serien/$slug"
                )
            }
        }

        return results.values.toList()
    }

    /** Parst die Detail-Seite. */
    private fun parseStreamKisteDetail(html: String, slug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(id = slug, title = slugToTitle(slug), coverUrl = null, detailUrl = "/serien/$slug") to emptyList()
        }
        val doc = Jsoup.parse(html, baseUrl)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: slugToTitle(slug)

        val cover = findCoverFromDoc(doc)

        val description = doc.selectFirst(".description")?.text()?.trim()
            ?: doc.selectFirst(".overview")?.text()?.trim()
            ?: doc.selectFirst("p")?.text()?.trim()

        val year = Regex("\\((\\d{4})\\)").find(title)?.groupValues?.get(1)

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/serien/$slug",
            description = description,
            year = year
        )

        val seasons = parseStreamKisteSeasons(doc, slug)
        return series to seasons
    }

    /** Parst Staffeln. */
    private fun parseStreamKisteSeasons(doc: Document, slug: String): List<Season> {
        val seasonNumbers = mutableSetOf<Int>()

        // Staffel-Links
        val pattern = Pattern.compile("/staffel-(\\d+)")
        for (a in doc.select("a[href*=/staffel-]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = pattern.matcher(href)
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
            }
        }

        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)

        // Episoden der ersten Staffel
        val currentEpisodes = parseStreamKisteEpisodesFromDoc(doc, slug, seasonNumbers.minOrNull() ?: 1)

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
                        episodeUrl = "/filme/$slug"
                    )
                )))
            }
        }

        return seasons
    }

    /** Parst Episoden. */
    private fun parseStreamKisteEpisodes(html: String, slug: String, season: Int): List<Episode> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
        return parseStreamKisteEpisodesFromDoc(doc, slug, season)
    }

    /** Parst Episoden aus Document. */
    private fun parseStreamKisteEpisodesFromDoc(doc: Document, slug: String, season: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<Int>()

        // Episoden-Links
        val epPattern = Pattern.compile("/staffel-(\\d+)/episode-(\\d+)")
        for (a in doc.select("a[href*=/episode-]")) {
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

        // Fallback: Tabellen-Zeilen
        if (episodes.isEmpty()) {
            for (tr in doc.select("tr")) {
                val a = tr.selectFirst("a[href*=/episode-]") ?: continue
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val m = epPattern.matcher(href)
                if (m.find()) {
                    val s = m.group(1)?.toIntOrNull() ?: continue
                    val ep = m.group(2)?.toIntOrNull() ?: continue
                    if (s != season) continue
                    if (seen.add(ep)) {
                        episodes.add(Episode(
                            number = ep,
                            title = a.text().trim().ifBlank { "Folge $ep" },
                            slug = slug,
                            season = s,
                            episodeUrl = m.group(0) ?: ""
                        ))
                    }
                }
            }
        }

        return episodes.sortedBy { it.number }
    }

    /** Parst Hoster. */
    private fun parseStreamKisteHosters(html: String): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        // Hoster-Buttons
        for (btn in doc.select("button[data-play-url], a[data-play-url]")) {
            val playUrl = btn.attr("data-play-url")
            if (playUrl.isBlank()) continue
            val name = btn.attr("data-provider-name").ifBlank {
                btn.text().trim().ifBlank { "Unknown" }
            }
            if (seen.add("$name-$playUrl")) {
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

        // Fallback: Hoster-Links
        if (hosters.isEmpty()) {
            for (a in doc.select("a.host-link, a.watch-link")) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (href.isBlank()) continue
                val name = a.text().trim().ifBlank { "Unknown" }
                if (seen.add("$name-$href")) {
                    hosters.add(HosterLink(
                        name = name,
                        redirectUrl = href,
                        index = hosters.size
                    ))
                }
            }
        }

        return hosters
    }

    // ─── Hilfsfunktionen ────────────────────────────────────────────────────

    private fun extractStreamKisteSlug(url: String): String? {
        val pattern = Pattern.compile("/(?:serien|filme)/([\\w%.-]+?)(?:/staffel-|/episode-|/|$)")
        val m = pattern.matcher(url)
        if (!m.find()) return null
        val slug = m.group(1)
        return try { java.net.URLDecoder.decode(slug, "UTF-8") } catch (_: Exception) { slug }
    }

    private fun findStreamKisteCover(element: Element): String? {
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

    private fun findCoverFromDoc(doc: Document): String? {
        val img = doc.selectFirst("img[data-src]")
            ?: doc.selectFirst("img[src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else baseUrl + src
            }
        }
        // og:image
        val ogImage = doc.selectFirst("meta[property=og:image]")
        if (ogImage != null) {
            val content = ogImage.attr("content")
            if (content.isNotBlank()) return content
        }
        return null
    }

    private fun extractHosterNameFromUrl(url: String): String {
        return when {
            url.contains("voe", ignoreCase = true) -> "VOE"
            url.contains("streamtape", ignoreCase = true) -> "Streamtape"
            url.contains("filemoon", ignoreCase = true) -> "Filemoon"
            url.contains("dood", ignoreCase = true) -> "Doodstream"
            url.contains("vidoza", ignoreCase = true) -> "Vidoza"
            url.contains("mixdrop", ignoreCase = true) -> "Mixdrop"
            url.contains("vivo", ignoreCase = true) -> "Vivo"
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
