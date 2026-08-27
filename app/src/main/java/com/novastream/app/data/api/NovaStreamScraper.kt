package com.novastream.app.data.api

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.NovaStreamConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Parst das HTML von serienstream.to mit Jsoup.
 *
 * Getestet gegen die echte Website-Struktur (Stand 2025):
 *   Homepage:  div.home-hero-overlay (Hero), div.card-mini (Tiles), article.trend-card (Angesagt)
 *   Detail:    h1.h2 (Titel), div.show-cover-mobile img (Cover), span.description-text, a[data-season-pill]
 *   Episoden:  tr.episode-row onclick="/serie/{slug}/staffel-{n}/episode-{m}"
 *   Hoster:    button.link-box[data-play-url][data-provider-name][data-language-label]
 */
object NovaStreamScraper {

    private val SLUG_PATTERN = Pattern.compile("/serie/([\\w%.-]+?)(?:/|$)")
    private val EP_URL_PATTERN = Pattern.compile("/serie/[\\w%.-]+/staffel-(\\d+)/episode-(\\d+)")
    private val SEASON_PATTERN = Pattern.compile("/staffel-(\\d+)")

    // ─── Serien-Listen (Home, Suche) ────────────────────────────────────────

    /** Parst eine Liste von Serien (Startseite, Suche). */
    fun parseSeriesList(html: String): List<Series> {
        val doc = Jsoup.parse(html, NovaStreamConfig.BASE_URL)
        val results = linkedMapOf<String, Series>()

        // Phase 1: Hero-Slides (höchste Priorität)
        for (a in doc.select("a.home-hero-overlay[href^=/serie/]")) {
            val slug = extractSlug(a.absUrl("href").ifBlank { a.attr("href") }) ?: continue
            if (results.containsKey(slug)) continue
            val title = a.selectFirst("h2.home-hero-title")?.text()?.trim() ?: slugToTitle(slug)
            val cover = findCoverInContainer(a.closest(".home-hero-slide") ?: a, doc)
            results[slug] = Series(id = slug, title = title, coverUrl = cover, detailUrl = "/serie/$slug")
        }

        // Phase 2: card-mini Tiles (Neu hinzugefügt etc.)
        for (card in doc.select("div.card-mini")) {
            val a = card.selectFirst("a[href^=/serie/]") ?: continue
            val slug = extractSlug(a.absUrl("href").ifBlank { a.attr("href") }) ?: continue
            if (results.containsKey(slug)) continue
            val title = card.selectFirst("h3")?.attr("title")?.ifBlank { null }
                ?: card.selectFirst("h3")?.text()?.trim()
                ?: a.text().trim().ifBlank { slugToTitle(slug) }
            val cover = findCoverInContainer(card, doc)
            results[slug] = Series(id = slug, title = title, coverUrl = cover, detailUrl = "/serie/$slug")
        }

        // Phase 3: trend-cards (Angesagt)
        for (card in doc.select("article.trend-card")) {
            val a = card.selectFirst("a[href^=/serie/]") ?: continue
            val slug = extractSlug(a.absUrl("href").ifBlank { a.attr("href") }) ?: continue
            if (results.containsKey(slug)) continue
            val title = card.selectFirst("h3.trend-title")?.text()?.trim()
                ?: a.text().trim().ifBlank { slugToTitle(slug) }
            val cover = findCoverInContainer(card, doc)
            results[slug] = Series(id = slug, title = title, coverUrl = cover, detailUrl = "/serie/$slug")
        }

        // Phase 4: Generic fallback - alle Serien-Links
        for (a in doc.select("a[href^=/serie/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractSlug(href) ?: continue
            if (results.containsKey(slug)) continue
            // Skip season/episode links
            if (href.contains("/staffel-")) continue
            val title = a.selectFirst("h3")?.attr("title")?.ifBlank { null }
                ?: a.selectFirst("h3")?.text()?.trim()
                ?: a.selectFirst("h2")?.text()?.trim()
                ?: a.attr("title").ifBlank { null }?.substringBefore(" stream")
                ?: a.text().trim().ifBlank { slugToTitle(slug) }
            val cover = findCoverInContainer(a.parent() ?: a, doc)
            results[slug] = Series(id = slug, title = title, coverUrl = cover, detailUrl = "/serie/$slug")
        }

        return results.values.toList()
    }

    /** Sucht das Cover-Bild in einem Container. */
    private fun findCoverInContainer(container: Element, doc: Document): String? {
        // img[data-src] (Lazy Loading)
        val img = container.selectFirst("img[data-src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else NovaStreamConfig.BASE_URL + src
            }
        }
        // img[src]
        val imgSrc = container.selectFirst("img[src]")
        if (imgSrc != null) {
            val src = imgSrc.absUrl("src").ifBlank { imgSrc.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else NovaStreamConfig.BASE_URL + src
            }
        }
        // source[data-srcset] (picture elements)
        val source = container.selectFirst("source[data-srcset]")
        if (source != null) {
            val srcset = source.attr("data-srcset")
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstUrl.isNullOrBlank() && !firstUrl.contains("data:image")) {
                return if (firstUrl.startsWith("http")) firstUrl else NovaStreamConfig.BASE_URL + firstUrl
            }
        }
        // source[srcset]
        val sourceSrcset = container.selectFirst("source[srcset]")
        if (sourceSrcset != null) {
            val srcset = sourceSrcset.attr("srcset")
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstUrl.isNullOrBlank() && !firstUrl.contains("data:image")) {
                return if (firstUrl.startsWith("http")) firstUrl else NovaStreamConfig.BASE_URL + firstUrl
            }
        }
        return null
    }

    // ─── Serien-Detail + Staffeln ───────────────────────────────────────────

    /** Parst die Serien-Detail-/Staffel-Seite. */
    fun parseSeriesDetail(html: String, slug: String): Pair<Series, List<Season>> {
        val doc = Jsoup.parse(html, NovaStreamConfig.BASE_URL)

        val title = doc.selectFirst("h1")?.text()?.trim() ?: slugToTitle(slug)
        val cover = extractDetailCover(doc)

        val description = doc.selectFirst(".description-text")?.text()?.trim()
            ?: doc.selectFirst(".series-description")?.text()?.trim()

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/serie/$slug",
            description = description
        )

        val seasons = parseSeasons(doc, slug)
        return series to seasons
    }

    /** Extrahiert das Cover/Backdrop-Bild aus der Detail-Seite. */
    private fun extractDetailCover(doc: Document): String? {
        // Weg 1: show-cover-mobile img (Serien-Cover auf Detail-Seite)
        val coverImg = doc.selectFirst("div.show-cover-mobile img[data-src]")
            ?: doc.selectFirst("div.show-cover-mobile img[src]")
            ?: doc.selectFirst(".series-cover img[data-src]")
            ?: doc.selectFirst(".series-cover img[src]")
            ?: doc.selectFirst(".cover img[data-src]")
        if (coverImg != null) {
            val src = coverImg.absUrl("data-src").ifBlank { coverImg.attr("data-src") }
                .ifBlank { coverImg.absUrl("src") }.ifBlank { coverImg.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else NovaStreamConfig.BASE_URL + src
            }
        }

        // Weg 2: source[data-srcset] (picture element)
        val source = doc.selectFirst("source[data-srcset]")
        if (source != null) {
            val srcset = source.attr("data-srcset")
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstUrl.isNullOrBlank() && !firstUrl.contains("data:image")) {
                return if (firstUrl.startsWith("http")) firstUrl else NovaStreamConfig.BASE_URL + firstUrl
            }
        }

        // Weg 3: source[srcset]
        val sourceSrcset = doc.selectFirst("source[srcset]")
        if (sourceSrcset != null) {
            val srcset = sourceSrcset.attr("srcset")
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstUrl.isNullOrBlank() && !firstUrl.contains("data:image")) {
                return if (firstUrl.startsWith("http")) firstUrl else NovaStreamConfig.BASE_URL + firstUrl
            }
        }

        // Weg 4: og:image Meta-Tag
        val ogImage = doc.selectFirst("meta[property=og:image]")
        if (ogImage != null) {
            val content = ogImage.attr("content")
            if (content.isNotBlank() && !content.contains("facebook.jpg")) return content
        }

        // Weg 5: Fallback - erstes img mit data-src
        val anyImg = doc.selectFirst("img[data-src]")
        if (anyImg != null) {
            val src = anyImg.absUrl("data-src").ifBlank { anyImg.attr("data-src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else NovaStreamConfig.BASE_URL + src
            }
        }

        return null
    }

    /** Parst Staffeln + Episoden aus der Staffel-Seite. */
    private fun parseSeasons(doc: Document, slug: String): List<Season> {
        val seasonNumbers = mutableSetOf<Int>()

        // Staffel-Links: a[data-season-pill]
        for (a in doc.select("a[data-season-pill]")) {
            a.attr("data-season-pill").toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
        }

        // Fallback: href-Pattern /serie/{slug}/staffel-{n}
        if (seasonNumbers.isEmpty()) {
            for (a in doc.select("a[href~=/serie/[\\w%.-]+/staffel-\\d+]")) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                extractSeasonNumber(href)?.let { if (it > 0) seasonNumbers.add(it) }
            }
        }

        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)

        // Episoden der aktuell geladenen Staffel parsen
        val currentEpisodes = parseEpisodes(doc, slug)

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
    private fun parseEpisodes(doc: Document, slug: String): List<Episode> {
        val episodes = mutableListOf<Episode>()

        // Episoden-Zeilen: tr.episode-row mit onclick
        for (row in doc.select("tr.episode-row")) {
            val onclick = row.attr("onclick")
            val m = EP_URL_PATTERN.matcher(onclick)
            if (m.find()) {
                val season = m.group(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
                val epNum = m.group(2)?.toIntOrNull()?.takeIf { it > 0 } ?: continue
                val epUrl = m.group(0)

                val title = row.selectFirst(".episode-title-ger")?.text()?.trim()?.ifBlank { null }
                    ?: row.selectFirst(".episode-title-eng")?.text()?.trim()?.ifBlank { null }
                    ?: row.selectFirst(".episode-title-cell")?.text()?.trim()?.ifBlank { null }
                    ?: "Folge $epNum"

                // Episode-Thumbnail
                val thumbImg = row.selectFirst("img[data-src]") ?: row.selectFirst("img[src]")
                val thumbnail = thumbImg?.let { img ->
                    val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                        .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
                    if (src.isNotBlank() && !src.contains("data:image")) {
                        if (src.startsWith("http")) src else NovaStreamConfig.BASE_URL + src
                    } else null
                }

                // Hoster-Icons in der Zeile
                val hosterIcons = row.select("img.watch-link")
                val hosters = hosterIcons.mapIndexed { idx, img ->
                    val name = img.attr("title").ifBlank { img.attr("alt") }
                    HosterLink(name = name, redirectUrl = "", index = idx)
                }

                episodes.add(Episode(
                    number = epNum,
                    title = title,
                    hosters = hosters,
                    slug = slug,
                    season = season,
                    episodeUrl = epUrl,
                    thumbnailUrl = thumbnail
                ))
            }
        }

        // Fallback: Links mit /staffel-{n}/episode-{m}
        if (episodes.isEmpty()) {
            val seen = mutableSetOf<String>()
            for (a in doc.select("a[href~=/serie/[\\w%.-]+/staffel-\\d+/episode-\\d+]")) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val m = EP_URL_PATTERN.matcher(href)
                if (m.find()) {
                    val season = m.group(1)?.toIntOrNull() ?: 1
                    val epNum = m.group(2)?.toIntOrNull() ?: continue
                    val key = "$season-$epNum"
                    if (seen.add(key)) {
                        episodes.add(Episode(
                            number = epNum,
                            title = a.text().trim().ifBlank { "Episode $epNum" },
                            slug = slug,
                            season = season,
                            episodeUrl = m.group(0)
                        ))
                    }
                }
            }
        }

        return episodes
    }

    // ─── Episoden einer bestimmten Staffel ──────────────────────────────────

    /** Parst nur die Episoden aus einer Staffel-Seite. */
    fun parseSeasonEpisodes(html: String, slug: String, season: Int): List<Episode> {
        val doc = Jsoup.parse(html, NovaStreamConfig.BASE_URL)
        return parseEpisodes(doc, slug)
    }

    // ─── Hoster einer Episode ───────────────────────────────────────────────

    /**
     * Parst die Hosters einer konkreten Episoden-Seite.
     * Auf serienstream.to sind Hoster als <button class="link-box"> mit:
     *   data-play-url      = Redirect-URL (/r?t=eyJ...)
     *   data-provider-name = Hoster-Name (VOE, Streamtape, ...)
     *   data-language-label = Sprache
     */
    fun parseHosters(html: String): List<HosterLink> {
        val doc = Jsoup.parse(html, NovaStreamConfig.BASE_URL)
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        for (btn in doc.select("button.link-box[data-play-url]")) {
            val playUrl = btn.attr("data-play-url")
            if (playUrl.isBlank()) continue
            val name = btn.attr("data-provider-name").ifBlank {
                btn.selectFirst("img")?.attr("title") ?: btn.selectFirst("img")?.attr("alt") ?: "Unknown"
            }
            val language = btn.attr("data-language-label")
            val linkId = btn.attr("data-link-id")

            val key = "$name-$playUrl"
            if (seen.add(key)) {
                hosters.add(HosterLink(
                    name = name,
                    redirectUrl = playUrl,
                    language = language,
                    linkId = linkId,
                    index = hosters.size
                ))
            }
        }

        // Fallback: img.watch-link mit title-Attribut
        if (hosters.isEmpty()) {
            for (img in doc.select("img.watch-link[title]")) {
                val name = img.attr("title")
                if (name.isNotBlank() && seen.add(name)) {
                    hosters.add(HosterLink(name = name, redirectUrl = "", index = hosters.size))
                }
            }
        }

        return hosters
    }

    // ─── Hilfsfunktionen ────────────────────────────────────────────────────

    private fun extractSlug(url: String): String? {
        val m = SLUG_PATTERN.matcher(url)
        if (!m.find()) return null
        val slug = m.group(1)
        return try { java.net.URLDecoder.decode(slug, "UTF-8") } catch (_: Exception) { slug }
    }

    private fun extractSeasonNumber(url: String): Int? {
        val m = SEASON_PATTERN.matcher(url)
        return if (m.find()) m.group(1)?.toIntOrNull()?.takeIf { it > 0 } else null
    }

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
