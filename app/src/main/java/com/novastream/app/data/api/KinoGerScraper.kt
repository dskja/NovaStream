package com.novastream.app.data.api

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Parst das HTML von KinoGer.to (DLE-basiertes CMS).
 *
 * URL-Schema:
 *   /stream/{id}-{slug}.html          – Film/Serie Detail
 *   /series/{id}-{slug}.html          – Serien-Detail (alternative URL)
 *   /?do=search&subaction=search&story={query} – Suche
 *
 * KinoGer nutzt ein DataLife Engine (DLE) CMS mit:
 *   - div#dle-content als Hauptcontainer
 *   - div.short als Serien/Film-Karte
 *   - h1#news-title als Titel auf Detail-Seite
 *   - div.images-border img als Cover
 *   - Script mit kinoger.ru Daten für Episoden-IFrames
 */
object KinoGerScraper {

    private const val BASE_URL = "https://kinoger.to"

    private val SLUG_PATTERN = Pattern.compile("/(?:stream|series)/(\\d+)-([\\w-]+?)\\.html")
    private val EPISODE_PATTERN = Pattern.compile("-episode-(\\d+)")
    private val SEASON_EPISODE_PATTERN = Pattern.compile("staffel-(\\d+)-episode-(\\d+)")

    // ─── Serien-Listen (Home, Suche, Genre) ────────────────────────────────

    /** Parst eine Liste von Serien/Filmen von KinoGer. */
    fun parseSeriesList(html: String): List<Series> {
        val doc = Jsoup.parse(html, BASE_URL)
        val results = linkedMapOf<String, Series>()

        // DLE: div#dle-content > div.short (primärer Selektor)
        val shortItems = doc.select("div#dle-content div.short")
        for (item in shortItems) {
            val anchor = item.selectFirst("a") ?: continue
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            if (href.isBlank()) continue

            if (!href.contains("/stream/") && !href.contains("/series/")) continue

            val slug = extractKinoGerSlug(href) ?: continue
            if (results.containsKey(slug)) continue

            val title = anchor.text()?.trim()?.ifBlank { null }
                ?: item.selectFirst("img")?.attr("alt")?.ifBlank { null }
                ?: anchor.attr("title")?.ifBlank { null }
                ?: slugToTitle(slug)

            val cover = findCoverInShort(item)

            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = href.substringAfter(BASE_URL).ifBlank { href }
            )
        }

        // Fallback 1: Suche nutzt div.titlecontrol statt div.short
        if (results.isEmpty()) {
            val searchItems = doc.select("div#dle-content div.titlecontrol")
            for (item in searchItems) {
                val anchor = item.selectFirst("a") ?: continue
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (href.isBlank()) continue

                val slug = extractKinoGerSlug(href) ?: continue
                if (results.containsKey(slug)) continue

                val title = anchor.text()?.trim()?.ifBlank { null }
                    ?: item.selectFirst("img")?.attr("alt")?.ifBlank { null }
                    ?: slugToTitle(slug)

                // Cover im nächsten Geschwister-Element suchen
                val cover = findCoverInSearch(item)

                results[slug] = Series(
                    id = slug,
                    title = title,
                    coverUrl = cover,
                    detailUrl = href.substringAfter(BASE_URL).ifBlank { href }
                )
            }
        }

        // Fallback 2: Alle Links mit /stream/ oder /series/ auf der Seite (breitester Fallback)
        if (results.isEmpty()) {
            val allLinks = doc.select("a[href]")
            for (anchor in allLinks) {
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (href.isBlank()) continue
                if (!href.contains("/stream/") && !href.contains("/series/")) continue
                // Skip navigation/category links
                if (href.endsWith("/stream/") || href.endsWith("/series/") ||
                    href.contains("/stream/page/") || href.contains("/series/page/")) continue

                val slug = extractKinoGerSlug(href) ?: continue
                if (results.containsKey(slug)) continue

                val title = anchor.text()?.trim()?.ifBlank { null }
                    ?: anchor.attr("title")?.ifBlank { null }
                    ?: slugToTitle(slug)

                // Cover: suche im Parent Container
                val parent = anchor.parent()
                val cover = parent?.let { findCoverInShort(it) }

                results[slug] = Series(
                    id = slug,
                    title = title,
                    coverUrl = cover,
                    detailUrl = href.substringAfter(BASE_URL).ifBlank { href }
                )
            }
        }

        return results.values.toList()
    }

    private fun findCoverInShort(item: Element): String? {
        // div.content_text img
        val img = item.selectFirst("div.content_text img")
            ?: item.selectFirst("img[data-src]")
            ?: item.selectFirst("img[src]")
        if (img != null) {
            val src = img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
                .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else BASE_URL + src
            }
        }
        return null
    }

    private fun findCoverInSearch(item: Element): String? {
        // Suche im Element und seinen Geschwistern
        findCoverInShort(item)?.let { return it }
        val next = item.nextElementSibling()
        if (next != null) findCoverInShort(next)?.let { return it }
        return null
    }

    // ─── Serien-Detail ─────────────────────────────────────────────────────

    /**
     * Parst die Detail-Seite einer Serie/Film auf KinoGer.
     * KinoGer nutzt ein Script mit kinoger.ru Daten für Episoden-IFrames.
     */
    fun parseSeriesDetail(html: String, slug: String): Pair<Series, List<Season>> {
        val doc = Jsoup.parse(html, BASE_URL)

        val title = doc.selectFirst("h1#news-title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: slugToTitle(slug)

        // Cover: div.images-border img
        var cover: String? = null
        val coverImg = doc.selectFirst("div.images-border img")
            ?: doc.selectFirst("img[data-src]")
            ?: doc.selectFirst("img[src]")
        if (coverImg != null) {
            val src = coverImg.attr("data-src").ifBlank { coverImg.attr("data-lazy-src") }
                .ifBlank { coverImg.absUrl("src") }.ifBlank { coverImg.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                cover = if (src.startsWith("http")) src else BASE_URL + src
            }
        }

        // Beschreibung: div.images-border text oder p nach dem Bild
        val description = doc.selectFirst("div.images-border")?.text()?.trim()
            ?: doc.selectFirst(".description")?.text()?.trim()
            ?: doc.selectFirst("p")?.text()?.trim()

        // Jahr aus Titel extrahieren: "Title (2024)"
        val year = Regex("\\((\\d{4})\\)").find(title)?.groupValues?.get(1)

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/stream/$slug.html",
            description = description,
            year = year
        )

        // Episoden aus dem kinoger.ru Script extrahieren
        val seasons = parseKinoGerSeasons(doc, slug)

        return series to seasons
    }

    /**
     * Parst Episoden aus dem KinoGer JavaScript.
     * KinoGer nutzt ein Script mit kinoger.ru Daten:
     *   var foo = [['iframe1', 'iframe2'], ['iframe3'], ...]
     * Jedes Sub-Array ist eine Staffel, jedes Element eine Episode.
     */
    private fun parseKinoGerSeasons(doc: Document, slug: String): List<Season> {
        val script = doc.selectFirst("script:containsData(kinoger.ru)")
            ?: doc.selectFirst("script:containsData(kinoger)")
            ?: doc.selectFirst("script:containsData(iframe)")

        if (script != null) {
            val data = script.data()
            // Extrahiere das Array aus dem Script
            // Pattern: [['url1','url2'],['url3'],...]
            val arrayContent = extractArrayContent(data)
            if (arrayContent.isNotEmpty()) {
                val seasons = mutableListOf<Season>()
                for ((seasonIdx, seasonArray) in arrayContent.withIndex()) {
                    val episodes = mutableListOf<Episode>()
                    for ((epIdx, iframeUrl) in seasonArray.withIndex()) {
                        episodes.add(Episode(
                            number = epIdx + 1,
                            title = "Folge ${epIdx + 1}",
                            slug = slug,
                            season = seasonIdx + 1,
                            episodeUrl = iframeUrl,
                            hosters = listOf(HosterLink(
                                name = "KinoGer",
                                redirectUrl = iframeUrl,
                                index = 0
                            ))
                        ))
                    }
                    if (episodes.isNotEmpty()) {
                        seasons.add(Season(number = seasonIdx + 1, episodes = episodes))
                    }
                }
                if (seasons.isNotEmpty()) return seasons
            }
        }

        // Fallback: Suche nach iframe-Tags auf der Seite
        val iframes = doc.select("iframe[src]")
        if (iframes.isNotEmpty()) {
            val episodes = iframes.mapIndexed { idx, iframe ->
                val src = iframe.absUrl("src").ifBlank { iframe.attr("src") }
                Episode(
                    number = idx + 1,
                    title = "Folge ${idx + 1}",
                    slug = slug,
                    season = 1,
                    episodeUrl = src,
                    hosters = listOf(HosterLink(
                        name = "KinoGer",
                        redirectUrl = src,
                        index = 0
                    ))
                )
            }
            if (episodes.isNotEmpty()) {
                return listOf(Season(number = 1, episodes = episodes))
            }
        }

        // Letzter Fallback: Film (keine Episoden) - erstelle eine "Episode 1"
        return listOf(Season(number = 1, episodes = listOf(
            Episode(
                number = 1,
                title = "Film",
                slug = slug,
                season = 1,
                episodeUrl = "",
                hosters = listOf(HosterLink(name = "KinoGer", redirectUrl = "", index = 0))
            )
        )))
    }

    /**
     * Extrahiert das verschachtelte Array aus dem KinoGer Script.
     * Format: [['url1','url2'],['url3'],...]
     */
    private fun extractArrayContent(scriptData: String): List<List<String>> {
        val result = mutableListOf<List<String>>()

        // Finde den Array-Teil: nach '[' bis zum letzten ']'
        val startIdx = scriptData.indexOf('[')
        if (startIdx < 0) return result

        // Parse verschachtelte Arrays manuell
        var i = startIdx
        var currentSeason = mutableListOf<String>()
        var currentUrl = StringBuilder()
        var inString = false
        var stringChar: Char? = null
        var depth = 0

        while (i < scriptData.length) {
            val c = scriptData[i]

            if (inString) {
                if (c == stringChar) {
                    inString = false
                    stringChar = null
                    val url = currentUrl.toString().trim()
                    if (url.isNotBlank() && (url.startsWith("http") || url.startsWith("/") || url.contains("kinoger"))) {
                        currentSeason.add(url)
                    }
                    currentUrl = StringBuilder()
                } else if (c == '\\' && i + 1 < scriptData.length) {
                    // Escape character - skip next
                    currentUrl.append(scriptData[i + 1])
                    i++
                } else {
                    currentUrl.append(c)
                }
            } else {
                when (c) {
                    '[', '{' -> depth++
                    ']', '}' -> {
                        depth--
                        if (depth == 1) {
                            // Ende einer Staffel
                            if (currentSeason.isNotEmpty()) {
                                result.add(currentSeason.toList())
                                currentSeason.clear()
                            }
                        } else if (depth == 0) {
                            // Ende des Arrays
                            if (currentSeason.isNotEmpty()) {
                                result.add(currentSeason.toList())
                                currentSeason.clear()
                            }
                            break
                        }
                    }
                    '\'', '"' -> {
                        inString = true
                        stringChar = c
                    }
                }
            }
            i++
        }

        return result
    }

    // ─── Hoster ────────────────────────────────────────────────────────────

    /**
     * Auf KinoGer sind die Hoster direkt als iframe-URLs verfügbar.
     * Die Episode enthält bereits die iframe-URL in episodeUrl.
     */
    fun parseHosters(html: String): List<HosterLink> {
        val doc = Jsoup.parse(html, BASE_URL)
        val hosters = mutableListOf<HosterLink>()

        // iframe-Tags
        val iframes = doc.select("iframe[src]")
        for ((idx, iframe) in iframes.withIndex()) {
            val src = iframe.absUrl("src").ifBlank { iframe.attr("src") }
            if (src.isNotBlank()) {
                val hosterName = extractHosterNameFromUrl(src)
                hosters.add(HosterLink(
                    name = hosterName,
                    redirectUrl = src,
                    index = idx
                ))
            }
        }

        // Fallback: Links die als Hoster dienen könnten
        if (hosters.isEmpty()) {
            doc.select("a[href~=https?://[^/]+/(?:e/|v/|embed/)\\w+]").forEach { a ->
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (href.isNotBlank()) {
                    val hosterName = extractHosterNameFromUrl(href)
                    hosters.add(HosterLink(
                        name = hosterName,
                        redirectUrl = href,
                        index = hosters.size
                    ))
                }
            }
        }

        return hosters
    }

    private fun extractHosterNameFromUrl(url: String): String {
        return when {
            url.contains("voe", ignoreCase = true) -> "VOE"
            url.contains("streamtape", ignoreCase = true) -> "Streamtape"
            url.contains("dood", ignoreCase = true) -> "Doodstream"
            url.contains("vidoza", ignoreCase = true) -> "Vidoza"
            url.contains("filemoon", ignoreCase = true) -> "Filemoon"
            url.contains("speedo", ignoreCase = true) -> "Speedo"
            url.contains("kinoger", ignoreCase = true) -> "KinoGer"
            else -> {
                // Domain extrahieren
                try {
                    val uri = java.net.URI(url)
                    uri.host?.substringBefore(".")?.replaceFirstChar { it.uppercase() } ?: "Unknown"
                } catch (_: Exception) { "Unknown" }
            }
        }
    }

    // ─── Hilfsfunktionen ───────────────────────────────────────────────────

    /** Extrahiert den Slug aus einer KinoGer URL (/stream/12345-title.html → "12345-title"). */
    private fun extractKinoGerSlug(url: String): String? {
        val m = SLUG_PATTERN.matcher(url)
        if (m.find()) {
            val id = m.group(1)
            val slug = m.group(2)
            return "$id-$slug"
        }
        // Fallback: einfacher Pattern
        val simplePattern = Pattern.compile("/(?:stream|series)/([\\w-]+)\\.html")
        val m2 = simplePattern.matcher(url)
        if (m2.find()) return m2.group(1)
        return null
    }

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
