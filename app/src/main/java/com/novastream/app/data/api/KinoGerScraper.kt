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
 * Getestet gegen die echte Website-Struktur (Stand 2025):
 *   Serien-Liste:  div#dle-content > div.short (jede Serie)
 *                  div.titlecontrol > div.title > div.begin > a (Titel-Link)
 *                  div.content_text img (Cover-Bild)
 *   Detail-Seite:  h1#news-title (Titel)
 *                  div.images-border img (Cover)
 *                  pw.show(N, [['url1','url2'],['url3']]) (Episoden als JS-Array)
 *                  Format: äußeres Array = Staffeln, inneres Array = Episoden
 */
object KinoGerScraper {

    private const val BASE_URL = "https://kinoger.to"

    private val SLUG_PATTERN = Pattern.compile("/(?:stream|series)/(\\d+)-([\\w-]+?)\\.html")

    // ─── Serien-Listen (Home, Suche, Genre) ────────────────────────────────

    /** Parst eine Liste von Serien/Filmen von KinoGer. */
    fun parseSeriesList(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, BASE_URL)
        val results = linkedMapOf<String, Series>()

        // Phase 1: div.short (Standard DLE-Layout)
        for (item in doc.select("div#dle-content div.short")) {
            val anchor = item.selectFirst("div.titlecontrol a") ?: item.selectFirst("a[href]") ?: continue
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            if (href.isBlank()) continue
            if (!href.contains("/stream/") && !href.contains("/series/")) continue
            // Skip category/page links
            if (href.endsWith("/stream/") || href.endsWith("/series/")) continue

            val slug = extractKinoGerSlug(href) ?: continue
            if (results.containsKey(slug)) continue

            val title = anchor.text()?.trim()?.ifBlank { null }
                ?: item.selectFirst("img")?.attr("alt")?.ifBlank { null }
                ?: slugToTitle(slug)

            val cover = findCoverInShort(item)

            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = href.substringAfter(BASE_URL).ifBlank { href }
            )
        }

        // Phase 2: Fallback - alle Links mit /stream/ oder /series/ auf der Seite
        if (results.isEmpty()) {
            for (anchor in doc.select("a[href]")) {
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (href.isBlank()) continue
                if (!href.contains("/stream/") && !href.contains("/series/")) continue
                if (href.endsWith("/stream/") || href.endsWith("/series/")) continue
                if (href.contains("/page/")) continue

                val slug = extractKinoGerSlug(href) ?: continue
                if (results.containsKey(slug)) continue

                val title = anchor.text()?.trim()?.ifBlank { null }
                    ?: anchor.attr("title")?.ifBlank { null }
                    ?: slugToTitle(slug)

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
        // div.content_text img (primärer Selektor für Serien-Listen)
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

    // ─── Serien-Detail ─────────────────────────────────────────────────────

    /** Parst die Detail-Seite einer Serie/Film auf KinoGer. */
    fun parseSeriesDetail(html: String, slug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(id = slug, title = slugToTitle(slug), coverUrl = null, detailUrl = "/stream/$slug.html") to emptyList()
        }
        val doc = Jsoup.parse(html, BASE_URL)

        // Titel: h1#news-title oder h1
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

        // Episoden aus dem JavaScript extrahieren
        val seasons = parseKinoGerSeasons(doc, slug)

        return series to seasons
    }

    /**
     * Parst Episoden aus dem KinoGer JavaScript.
     * KinoGer nutzt: pw.show(N, [['url1','url2'],['url3'], ...])
     * Äußeres Array = Staffeln, inneres Array = Episoden mit iframe-URLs
     */
    private fun parseKinoGerSeasons(doc: Document, slug: String): List<Season> {
        // Suche nach Script mit pw.show() oder fsst.show()
        val scripts = doc.select("script")
        for (script in scripts) {
            val data = script.data()
            // Pattern: pw.show(N, [[...],[...]]) oder fsst.show(N, [[...],[...]])
            val showMatch = Regex("(?:pw|fsst)\\.show\\(\\d+,\\s*(\\[\\[.*?\\]\\])").find(data)
            if (showMatch != null) {
                val arrayStr = showMatch.groupValues[1]
                val seasons = parseJsArray(arrayStr, slug)
                if (seasons.isNotEmpty()) return seasons
            }
        }

        // Fallback: Suche nach allen iframe-URLs
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
                    hosters = listOf(HosterLink(name = "KinoGer", redirectUrl = src, index = 0))
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
     * Parst ein verschachteltes JS-Array: [['url1','url2'],['url3'], ...]
     * Äußeres Array = Staffeln, inneres Array = Episoden
     */
    private fun parseJsArray(arrayStr: String, slug: String): List<Season> {
        val seasons = mutableListOf<Season>()
        val seasonArrays = mutableListOf<List<String>>()

        var i = 0
        var currentSeason = mutableListOf<String>()
        var currentUrl = StringBuilder()
        var inString = false
        var stringChar: Char? = null
        var depth = 0

        while (i < arrayStr.length) {
            val c = arrayStr[i]

            if (inString) {
                if (c == stringChar) {
                    inString = false
                    stringChar = null
                    val url = currentUrl.toString().trim()
                    if (url.isNotBlank() && (url.startsWith("http") || url.startsWith("/"))) {
                        currentSeason.add(url)
                    }
                    currentUrl = StringBuilder()
                } else if (c == '\\' && i + 1 < arrayStr.length) {
                    currentUrl.append(arrayStr[i + 1])
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
                                seasonArrays.add(currentSeason.toList())
                                currentSeason.clear()
                            }
                        } else if (depth == 0) {
                            // Ende des Arrays
                            if (currentSeason.isNotEmpty()) {
                                seasonArrays.add(currentSeason.toList())
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

        // Erstelle Seasons aus den Arrays
        for ((seasonIdx, urls) in seasonArrays.withIndex()) {
            val episodes = mutableListOf<Episode>()
            for ((epIdx, iframeUrl) in urls.withIndex()) {
                val cleanUrl = iframeUrl.trim()
                if (cleanUrl.isNotBlank()) {
                    episodes.add(Episode(
                        number = epIdx + 1,
                        title = "Folge ${epIdx + 1}",
                        slug = slug,
                        season = seasonIdx + 1,
                        episodeUrl = cleanUrl,
                        hosters = listOf(HosterLink(
                            name = extractHosterNameFromUrl(cleanUrl),
                            redirectUrl = cleanUrl,
                            index = 0
                        ))
                    ))
                }
            }
            if (episodes.isNotEmpty()) {
                seasons.add(Season(number = seasonIdx + 1, episodes = episodes))
            }
        }

        return seasons
    }

    // ─── Hoster ────────────────────────────────────────────────────────────

    /** Auf KinoGer sind die Hoster direkt als iframe-URLs verfügbar. */
    fun parseHosters(html: String): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, BASE_URL)
        val hosters = mutableListOf<HosterLink>()

        for ((idx, iframe) in doc.select("iframe[src]").withIndex()) {
            val src = iframe.absUrl("src").ifBlank { iframe.attr("src") }
            if (src.isNotBlank()) {
                hosters.add(HosterLink(
                    name = extractHosterNameFromUrl(src),
                    redirectUrl = src,
                    index = idx
                ))
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
            url.contains("fsst.online", ignoreCase = true) -> "FSST"
            url.contains("kinoger", ignoreCase = true) -> "KinoGer"
            url.contains("mixdrop", ignoreCase = true) -> "Mixdrop"
            url.contains("upstream", ignoreCase = true) -> "Upstream"
            url.contains("streamlare", ignoreCase = true) -> "Streamlare"
            url.contains("ddownload", ignoreCase = true) -> "DDownload"
            url.contains("mega.nz", ignoreCase = true) -> "Mega"
            url.contains("streamzz", ignoreCase = true) -> "StreamZZ"
            url.contains("streamcrypt", ignoreCase = true) -> "StreamCrypt"
            else -> {
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
