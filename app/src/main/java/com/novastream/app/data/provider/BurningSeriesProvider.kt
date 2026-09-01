package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Provider für Burning Series (burningseries.cx — ehemals bs.to).
 * Eine der ältesten deutschen Streaming-Seiten mit 7000+ Serien.
 *
 * URL-Schema:
 *   /serie/{slug}                     – Serien-Detail mit Staffeln
 *   /serie/{slug}/{season}            – Staffel-Seite mit Episoden
 *   /serie/{slug}/{season}/{ep-title} – Episoden-Seite mit Hostern
 *   /serie/{slug}/{season}/{host}-{n} – Spezifischer Hoster
 *
 * Hoster: VOE, Streamtape, Vivo, Vidoza, Filemoon, Doodstream
 *
 * Hinweis: burningseries.cx hat reCAPTCHA-Schutz. Bei aktivem Captcha schlägt OkHttp fehl;
 * WebView-Fallback wird automatisch genutzt.
 */
class BurningSeriesProvider(
    override val id: String = "burningseries",
    override val displayName: String = "Burning Series",
    override val baseUrl: String = "https://burningseries.cx",
    override val supportsSeries: Boolean = true,
    private val appContext: Context? = null
) : StreamingProvider {

    private val mirror = MirrorSupport(id, baseUrl, appContext, "/serie/")

    private val hosterResolver get() = HosterResolver(baseUrl = mirror.parseBase())

    private suspend fun activeBaseUrl(): String = mirror.activeBase()

    private fun parseBase(): String = mirror.parseBase()

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrlWithCaptcha("$base/andelselect")
        val finalHtml = if (html.isBlank() || ProviderHttp.isChallenge(html)) fetchUrlWithCaptcha(base) else html
        parseBsSeriesList(finalHtml)
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            val base = activeBaseUrl()
            val q = query.trim()
            val encoded = java.net.URLEncoder.encode(q, "UTF-8")
            val paths = listOf(
                "$base/suche/$encoded",
                "$base/search?q=$encoded",
                "$base/search?term=$encoded",
                "$base/andelselect",
                "$base/andere-serien"
            )
            var results = emptyList<Series>()
            for (url in paths) {
                val html = fetchUrlWithCaptcha(url)
                results = parseBsSeriesList(html)
                if (results.isNotEmpty() && url.contains("andere-serien")) {
                    // Client-side Filter auf Alphabet-Liste
                    val needle = q.lowercase()
                    results = results.filter {
                        it.title.lowercase().contains(needle) || it.id.contains(needle.replace(' ', '-'))
                    }
                }
                if (results.isNotEmpty()) break
            }
            results.map { it.copy(providerId = id) }
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/serie/$slug")
        parseBsDetail(html, slug)
    }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/serie/$slug/$season")
        parseBsEpisodes(html, slug, season)
    }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatchingProvider {
        val base = activeBaseUrl()
        val url = if (episode.episodeUrl.startsWith("http")) {
            episode.episodeUrl
        } else if (episode.episodeUrl.startsWith("/")) {
            base + episode.episodeUrl
        } else {
            "$base/serie/${episode.slug}/${episode.season}/${episode.number}"
        }
        val html = fetchUrl(url)
        parseBsHosters(html)
    }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatchingProvider {
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val letters = ('A'..'Z').map { it.toString() }
        if (page <= 0) {
            val html = fetchUrlWithCaptcha("$base/andelselect")
            parseBsSeriesList(html).map { it.copy(providerId = id) }
        } else {
            val letter = letters.getOrNull(page - 1)
            if (letter == null) emptyList()
            else {
                val html = fetchUrlWithCaptcha("$base/andere-serien?letter=$letter")
                    .ifBlank { fetchUrlWithCaptcha("$base/andere-serien") }
                val list = parseBsSeriesList(html)
                val filtered = list.filter { it.title.startsWith(letter, ignoreCase = true) || it.id.startsWith(letter, ignoreCase = true) }
                (if (filtered.isNotEmpty()) filtered else list).map { it.copy(providerId = id) }
            }
        }
    }

    // ─── HTML Parsing ───────────────────────────────────────────────────────

    /** Lädt HTML; bei Captcha/leerem OkHttp-Ergebnis WebView-Fallback. */
    private suspend fun fetchUrlWithCaptcha(url: String): String = mirror.fetchWithCaptcha(url)

    private suspend fun fetchUrl(url: String): String = mirror.fetch(url)

    /** Parst eine Liste von Serien (Startseite, Suche). */
    private fun parseBsSeriesList(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val results = linkedMapOf<String, Series>()

        // Phase 1: Serien-Links auf der Startseite/Suche
        // bs.to nutzt verschiedene Container für Serien
        for (a in doc.select("a[href^=/serie/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractBsSlug(href) ?: continue
            if (results.containsKey(slug)) continue
            // Skip season/episode links (have additional path segments)
            val parts = href.substringAfter("/serie/").split("/")
            if (parts.size > 1 && parts[1].toIntOrNull() != null) continue

            val title = a.text()?.trim()?.ifBlank { null }
                ?: a.attr("title")?.ifBlank { null }
                ?: a.selectFirst("h3")?.text()?.trim()
                ?: a.selectFirst("h2")?.text()?.trim()
                ?: slugToTitle(slug)

            val cover = findBsCover(a)
            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = "/serie/$slug"
            )
        }

        // Phase 2: Genre-Seiten mit div.container
        if (results.isEmpty()) {
            for (div in doc.select("div.series, div.serie, div.show")) {
                val a = div.selectFirst("a[href^=/serie/]") ?: continue
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val slug = extractBsSlug(href) ?: continue
                if (results.containsKey(slug)) continue

                val title = div.selectFirst("h3")?.text()?.trim()
                    ?: div.selectFirst("h2")?.text()?.trim()
                    ?: a.text().trim().ifBlank { slugToTitle(slug) }

                val cover = findBsCover(div)
                results[slug] = Series(
                    id = slug,
                    title = title,
                    coverUrl = cover,
                    detailUrl = "/serie/$slug"
                )
            }
        }

        return results.values.toList()
    }

    /** Parst die Detail-Seite einer Serie. */
    private fun parseBsDetail(html: String, slug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(id = slug, title = slugToTitle(slug), coverUrl = null, detailUrl = "/serie/$slug") to emptyList()
        }
        val doc = Jsoup.parse(html, parseBase())

        // Titel
        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: slugToTitle(slug)

        // Cover
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

        // Beschreibung
        val description = doc.selectFirst(".description")?.text()?.trim()
            ?: doc.selectFirst("p")?.text()?.trim()

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/serie/$slug",
            description = description
        )

        val seasons = parseBsSeasons(doc, slug)
        return series to seasons
    }

    /** Parst Staffeln aus der Detail-Seite. */
    private fun parseBsSeasons(doc: Document, slug: String): List<Season> {
        val seasonNumbers = mutableSetOf<Int>()

        // Staffel-Links: a[href^=/serie/{slug}/] mit Zahl als nächstes Segment
        val pattern = Pattern.compile("/serie/[\\w%.-]+/(\\d+)")
        for (a in doc.select("a[href^=/serie/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = pattern.matcher(href)
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
            }
        }

        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)

        // Episoden der ersten Staffel parsen (auf Detail-Seite angezeigt)
        val currentEpisodes = parseBsEpisodesFromDoc(doc, slug, seasonNumbers.minOrNull() ?: 1)

        val seasons = mutableListOf<Season>()
        for (n in seasonNumbers.sorted()) {
            val eps = if (currentEpisodes.isNotEmpty() && currentEpisodes.first().season == n) {
                currentEpisodes
            } else {
                emptyList()
            }
            seasons.add(Season(number = n, episodes = eps))
        }

        if (seasons.isEmpty() && currentEpisodes.isNotEmpty()) {
            seasons.add(Season(number = 1, episodes = currentEpisodes))
        }

        return seasons
    }

    /** Parst Episoden aus einer Staffel-Seite. */
    private fun parseBsEpisodes(html: String, slug: String, season: Int): List<Episode> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        return parseBsEpisodesFromDoc(doc, slug, season)
    }

    /** Parst Episoden aus einem Jsoup Document. */
    private fun parseBsEpisodesFromDoc(doc: Document, slug: String, season: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<Int>()

        // Episoden-Links: a[href^=/serie/{slug}/{season}/]
        val epPattern = Pattern.compile("/serie/[\\w%.-]+/(\\d+)/([\\w-]+)")
        for (a in doc.select("a[href^=/serie/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = epPattern.matcher(href)
            if (m.find()) {
                val s = m.group(1)?.toIntOrNull() ?: continue
                if (s != season) continue
                val epSlug = m.group(2) ?: continue
                // Episode number aus URL extrahieren: "1-Episode-Title" -> 1
                val epNum = if (epSlug.contains("-")) {
                    epSlug.substringBefore("-").toIntOrNull()
                } else {
                    epSlug.toIntOrNull()
                } ?: continue
                if (epNum !in 1..999) continue
                if (seen.add(epNum)) {
                    val title = a.text()?.trim()?.ifBlank { null }
                        ?: "Folge $epNum"
                    episodes.add(Episode(
                        number = epNum,
                        title = title,
                        slug = slug,
                        season = s,
                        episodeUrl = m.group(0) ?: ""
                    ))
                }
            }
        }

        // Fallback: Tabellen-Zeilen mit Episoden
        if (episodes.isEmpty()) {
            for (tr in doc.select("tr")) {
                val a = tr.selectFirst("a[href^=/serie/]") ?: continue
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val m = epPattern.matcher(href)
                if (m.find()) {
                    val s = m.group(1)?.toIntOrNull() ?: continue
                    if (s != season) continue
                    val epSlug = m.group(2) ?: continue
                    val epNum = if (epSlug.contains("-")) {
                    epSlug.substringBefore("-").toIntOrNull()
                } else {
                    epSlug.toIntOrNull()
                } ?: continue
                    if (epNum !in 1..999) continue
                    if (seen.add(epNum)) {
                        episodes.add(Episode(
                            number = epNum,
                            title = a.text().trim().ifBlank { "Folge $epNum" },
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

    /**
     * Parst Hoster aus einer bs.to Episoden-Seite.
     * bs.to hat Hoster als Links mit Host-Namen im Pfad.
     */
    private fun parseBsHosters(html: String): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        // Hoster-Links: a[href*=/Host-] oder a mit data-hoster
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (href.isBlank()) continue

            // bs.to Hoster-Links haben Format: /serie/{slug}/{season}/{ep}/{HosterName}-{n}
            val hosterMatch = Regex("/([A-Za-z]+)-(\\d+)$").find(href)
            if (hosterMatch != null) {
                val hostName = hosterMatch.groupValues[1]
                if (hostName.length < 3 || hostName.length > 20) continue
                val name = normalizeHosterName(hostName)
                if (seen.add("$name-$href")) {
                    hosters.add(HosterLink(
                        name = name,
                        redirectUrl = href,
                        index = hosters.size
                    ))
                }
            }
        }

        // Fallback: Hoster-Icons/Buttons
        if (hosters.isEmpty()) {
            for (a in doc.select("a.watch-link, a.host-link, li[data-link-target] a")) {
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

        // Fallback: iframes (direkte Embeds)
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

        return hosters
    }

    // ─── Hilfsfunktionen ────────────────────────────────────────────────────

    private fun extractBsSlug(url: String): String? {
        // /serie/{slug} oder /serie/{slug}/...
        val pattern = Pattern.compile("/serie/([\\w%.-]+?)(?:/|$)")
        val m = pattern.matcher(url)
        if (!m.find()) return null
        val slug = m.group(1)
        return try { java.net.URLDecoder.decode(slug, "UTF-8") } catch (_: Exception) { slug }
    }

    private fun findBsCover(element: Element): String? {
        val img = element.selectFirst("img[data-src]")
            ?: element.selectFirst("img[src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else parseBase() + src
            }
        }
        return null
    }

    private fun normalizeHosterName(name: String): String {
        return when (name.lowercase()) {
            "voe" -> "VOE"
            "streamtape" -> "Streamtape"
            "vivo" -> "Vivo"
            "vidoza" -> "Vidoza"
            "filemoon" -> "Filemoon"
            "doodstream", "dood" -> "Doodstream"
            "streamcloud" -> "Streamcloud"
            "openload" -> "Openload"
            "vupload" -> "Vupload"
            "sendfox" -> "SendFox"
            "speedfiles" -> "SpeedFiles"
            "loadx" -> "LoadX"
            "luluvdo" -> "Luluvdo"
            "vidmoly" -> "Vidmoly"
            else -> name.replaceFirstChar { it.uppercase() }
        }
    }

    private fun extractHosterNameFromUrl(url: String): String {
        return when {
            url.contains("voe", ignoreCase = true) -> "VOE"
            url.contains("streamtape", ignoreCase = true) -> "Streamtape"
            url.contains("vivo", ignoreCase = true) -> "Vivo"
            url.contains("vidoza", ignoreCase = true) -> "Vidoza"
            url.contains("filemoon", ignoreCase = true) -> "Filemoon"
            url.contains("dood", ignoreCase = true) -> "Doodstream"
            url.contains("streamcloud", ignoreCase = true) -> "Streamcloud"
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
