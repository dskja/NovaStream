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
 * Parst das HTML von serienstream.to (serienstream.to) mit Jsoup.
 *
 * URL-Schema (Stand 2025/2026):
 *   /serie/{slug}                     – Serien-Detail (redirectet auf staffel-1)
 *   /serie/{slug}/staffel-{n}         – Staffel-Seite mit Episodenliste
 *   /serie/{slug}/staffel-{n}/episode-{m} – Episoden-Seite mit Hoster-Buttons
 *
 * Die Selektoren basieren auf dem aktuellen Bootstrap-basierten Markup.
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

        // WICHTIG: Jsoup select() mit Komma gibt Elemente in Dokument-Reihenfolge zurück.
        // Die card-mini/trend Links kommen im HTML VOR den hero-links.
        // Um die richtige Reihenfolge zu garantieren (Hero zuerst), selektieren wir
        // in separaten Phasen und nutzen linkedMapOf für Deduplizierung.

        // Phase 1: Hero-Links (höchste Priorität – diese sollen zuerst im Hero-Karussell erscheinen)
        val heroAnchors = doc.select("a[href~=/serie/[\\w%.-]+\$].home-hero-overlay")
        // Phase 2: card-mini Links (Neu hinzugefügt)
        val cardMiniAnchors = doc.select(".card-mini a[href~=/serie/[\\w%.-]+\$]")
        // Phase 3: trend Links (Angesagt)
        val trendAnchors = doc.select("a[href~=/serie/[\\w%.-]+\$].stretched-link")
        // Phase 4: top-shows Links (Top 5)
        val topShowAnchors = doc.select("a[href~=/serie/[\\w%.-]+\$].top-shows-separator")
        // Phase 5: Search results / generic
        val searchAnchors = doc.select("a[href~=/serie/[\\w%.-]+\$].text-decoration-none")
        // Phase 6: Generic series links (catch-all für alle Serien-Links auf der Seite)
        val genericAnchors = doc.select("a[href^=/serie/]")

        val allAnchors = (heroAnchors + cardMiniAnchors + trendAnchors + topShowAnchors + searchAnchors + genericAnchors)

        for (a in allAnchors) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractSlug(href) ?: continue
            if (results.containsKey(slug)) continue

            // Title: versuche show-title h6, dann h3 title, dann h2 title, dann anchor title attr, dann anchor text
            val title = a.closest(".cover-card, .card-mini, .card, .trend-card, .home-hero, .top-show-item")?.selectFirst(".show-title")?.text()?.trim()?.ifBlank { null }
                ?: a.selectFirst("h3")?.attr("title")?.ifBlank { null }
                ?: a.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
                ?: a.selectFirst("h2")?.text()?.trim()?.ifBlank { null }
                ?: a.attr("title").ifBlank { null }
                ?: a.text().trim()?.ifBlank { null }
                ?: slugToTitle(slug)

            // Cover-Bild suchen
            val cover = findCoverUrl(a, doc)

            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = "/serie/$slug"
            )
        }

        return results.values.toList()
    }

    /** Sucht das Cover-Bild für einen Serien-Link. */
    private fun findCoverUrl(anchor: Element, doc: Document): String? {
        // WICHTIG: Suche zuerst IM Anchor selbst (bei top-shows, card-mini ist das Bild inside <a>)
        // Erst danach im Container suchen – aber nur in kleinen Containern, nicht in großen
        // wie .card (das könnte mehrere Serien enthalten und das falsche Bild liefern)

        // Weg 0: Direkt im Anchor suchen (zuverlässigste Methode)
        extractImgFromElement(anchor)?.let { return it }

        // Weg 1: Im nächsten Geschwister-Element oder direkten Parent suchen
        // (bei hero-section liegt das Bild VOR dem Anchor, nicht darin)
        val parent = anchor.parent()
        if (parent != null) {
            // Nur suchen wenn der Parent klein genug ist (kein section/div Container)
            val parentClass = parent.className()
            if (parentClass.contains("trend-content") || parentClass.contains("cover-card") ||
                parentClass.contains("card-mini") || parentClass.contains("home-hero") ||
                parentClass.contains("swiper-slide") || parentClass.contains("top-show-item") ||
                parent.tagName() == "li" || parent.tagName() == "article") {
                extractImgFromElement(parent)?.let { return it }
            }
        }

        // Weg 2: Im Großelter-Element suchen (article, swiper-slide, etc.)
        val grandParent = parent?.parent()
        if (grandParent != null) {
            val gpClass = grandParent.className()
            if (gpClass.contains("trend-card") || gpClass.contains("swiper-slide") ||
                gpClass.contains("home-hero") || gpClass.contains("card-mini") ||
                grandParent.tagName() == "article" || grandParent.tagName() == "li") {
                extractImgFromElement(grandParent)?.let { return it }
            }
        }

        // Weg 3: Fallback – closest mit spezifischen Klassen (kein generisches .card!)
        val container = anchor.closest(".cover-card, .trend-cover, .card-mini, .home-hero, .swiper-slide, .top-show-item, .trend-card")
        if (container != null && container !== parent && container !== grandParent) {
            extractImgFromElement(container)?.let { return it }
        }

        return null
    }

    /** Extrahiert die erste gültige Bild-URL aus einem Element (img/source/picture). */
    private fun extractImgFromElement(elem: Element): String? {
        // Weg 1: <img> mit data-src (Lazy Loading – serienstream.to nutzt das primär)
        val img = elem.selectFirst("img[data-src]") ?: elem.selectFirst("img[src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image") && src.contains("/media/images/")) return src
        }

        // Weg 2: <source> mit data-srcset (home page nutzt data-srcset für lazy loading)
        val dataSrcSet = elem.selectFirst("source[data-srcset]")
        if (dataSrcSet != null) {
            val srcset = dataSrcSet.attr("data-srcset")
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstUrl.isNullOrBlank() && firstUrl.contains("/media/images/")) {
                return if (firstUrl.startsWith("http")) firstUrl
                      else NovaStreamConfig.BASE_URL + firstUrl
            }
        }

        // Weg 3: <source> mit srcset (search page nutzt srcset)
        val source = elem.selectFirst("source[srcset]")
        if (source != null) {
            val srcset = source.attr("srcset")
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstUrl.isNullOrBlank() && firstUrl.contains("/media/images/")) {
                return if (firstUrl.startsWith("http")) firstUrl
                      else NovaStreamConfig.BASE_URL + firstUrl
            }
        }

        return null
    }

    // ─── Serien-Detail + Staffeln ───────────────────────────────────────────

    /** Extrahiert das Cover/Backdrop-Bild aus der Detail-Seite. */
    private fun extractDetailCover(doc: Document): String? {
        // Weg 1: Spezifische Serien-Cover Selektoren (am zuverlässigsten)
        val coverImg = doc.selectFirst(".series-cover img[data-src]")
            ?: doc.selectFirst(".series-cover img[src]")
            ?: doc.selectFirst(".cover img[data-src]")
            ?: doc.selectFirst(".cover img[src]")
            ?: doc.selectFirst("img.series-poster")
            ?: doc.selectFirst(".series-image img")
        if (coverImg != null) {
            val src = coverImg.absUrl("data-src").ifBlank { coverImg.attr("data-src") }
                .ifBlank { coverImg.absUrl("src") }.ifBlank { coverImg.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else NovaStreamConfig.BASE_URL + src
            }
        }

        // Weg 2: <source> mit srcset (größtes Bild nehmen)
        val source = doc.selectFirst("source[srcset]")
        if (source != null) {
            val srcset = source.attr("srcset")
            // Nimm das LETZTE (größte) Bild aus srcset
            val lastUrl = srcset.split(",").lastOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!lastUrl.isNullOrBlank() && !lastUrl.contains("data:image")) {
                return if (lastUrl.startsWith("http")) lastUrl
                       else NovaStreamConfig.BASE_URL + lastUrl
            }
        }

        // Weg 3: og:image Meta-Tag (zuverlässig für Backdrop)
        val ogImage = doc.selectFirst("meta[property=og:image]")
        if (ogImage != null) {
            val content = ogImage.attr("content")
            if (content.isNotBlank()) return content
        }

        // Weg 4: Fallback - erstes img mit /media/images/ Pfad
        val anyImg = doc.selectFirst("img[data-src]") ?: doc.selectFirst("img[src]")
        if (anyImg != null) {
            val src = anyImg.absUrl("data-src").ifBlank { anyImg.attr("data-src") }
                .ifBlank { anyImg.absUrl("src") }.ifBlank { anyImg.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image") && src.contains("/media/images/")) return src
        }

        return null
    }

    /** Parst die Serien-Detail-/Staffel-Seite: Beschreibung, Staffeln, Episoden. */
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

    /** Parst Staffeln + Episoden aus der Staffel-Seite. */
    private fun parseSeasons(doc: Document, slug: String): List<Season> {
        val seasons = mutableListOf<Season>()

        // Staffel-Links: <a href="/serie/{slug}/staffel-{n}" data-season-pill="{n}">
        val seasonLinks = doc.select("a[data-season-pill]")
        val seasonNumbers = mutableSetOf<Int>()

        for (a in seasonLinks) {
            a.attr("data-season-pill").toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
        }

        // Fallback: aus href-Pattern extrahieren
        if (seasonNumbers.isEmpty()) {
            doc.select("a[href~=/serie/[\\w%.-]+/staffel-\\d+]").forEach { a ->
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                extractSeasonNumber(href)?.let { seasonNumbers.add(it) }
            }
        }

        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)

        // Episoden der aktuell geladenen Staffel parsen
        val currentEpisodes = parseEpisodes(doc, slug)

        for (n in seasonNumbers.sorted()) {
            // Wenn es die aktuelle Staffel ist, nimm die geparsten Episoden
            val eps = if (currentEpisodes.isNotEmpty() && currentEpisodes.first().season == n) {
                currentEpisodes
            } else {
                // Für andere Staffeln haben wir nur leere Episoden-Platzhalter
                emptyList()
            }
            seasons.add(Season(number = n, episodes = eps))
        }

        // Fallback: wenn keine Staffeln gefunden wurden, nimm die Episoden als Staffel 1
        if (seasons.isEmpty() && currentEpisodes.isNotEmpty()) {
            seasons.add(Season(number = 1, episodes = currentEpisodes))
        }

        return seasons
    }

    /** Parst Episoden aus einer Staffel-Seite. */
    private fun parseEpisodes(doc: Document, slug: String): List<Episode> {
        val episodes = mutableListOf<Episode>()

        // Episoden-Zeilen: <tr class="episode-row" onclick="window.location='/serie/{slug}/staffel-{n}/episode-{m}'">
        val rows = doc.select("tr.episode-row")
        val epUrlPattern = EP_URL_PATTERN

        for (row in rows) {
            val onclick = row.attr("onclick")
            val m = epUrlPattern.matcher(onclick)
            if (m.find()) {
                val season = m.group(1).toIntOrNull()?.takeIf { it > 0 } ?: 1
                val epNum = m.group(2).toIntOrNull()?.takeIf { it > 0 } ?: continue
                val epUrl = m.group(0)

                val title = row.selectFirst(".episode-title-ger")?.text()?.trim()?.ifBlank { null }
                    ?: row.selectFirst(".episode-title-eng")?.text()?.trim()?.ifBlank { null }
                    ?: row.selectFirst(".episode-title-cell")?.text()?.trim()?.ifBlank { null }
                    ?: "Folge $epNum"

                // Episode-Thumbnail: suche nach Bild in der Zeile
                val thumbImg = row.selectFirst("img[data-src]") ?: row.selectFirst("img[src]")
                val thumbnail = thumbImg?.let { img ->
                    val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                        .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
                    if (src.isNotBlank() && !src.contains("data:image")) {
                        if (src.startsWith("http")) src else NovaStreamConfig.BASE_URL + src
                    } else null
                }

                // Hoster-Icons in der Zeile (nur Anzeige, nicht klickbar hier)
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
            doc.select("a[href~=/serie/[\\w%.-]+/staffel-\\d+/episode-\\d+]").forEach { a ->
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val m = epUrlPattern.matcher(href)
                if (m.find()) {
                    val season = m.group(1).toIntOrNull() ?: 1
                    val epNum = m.group(2).toIntOrNull() ?: return@forEach
                    val key = "$season-$epNum"
                    if (episodes.none { it.season == season && it.number == epNum }) {
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
     * Auf der Episoden-Seite sind Hoster als <button class="link-box"> mit:
     *   data-play-url      = Redirect-URL (/r?t=eyJ...)
     *   data-provider-name = Hoster-Name (VOE, Streamtape, ...)
     *   data-language-label = Sprache
     *   data-link-id       = interne ID
     */
    fun parseHosters(html: String): List<HosterLink> {
        val doc = Jsoup.parse(html, NovaStreamConfig.BASE_URL)
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        val buttons = doc.select("button.link-box[data-play-url]")
        for (btn in buttons) {
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
            doc.select("img.watch-link[title]").forEach { img ->
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
        // URL-decode falls nötig (z.B. "25%20Years%20of%20You")
        return try { java.net.URLDecoder.decode(slug, "UTF-8") } catch (_: Exception) { slug }
    }

    private fun extractSeasonNumber(url: String): Int? {
        val m = SEASON_PATTERN.matcher(url)
        return if (m.find()) m.group(1)?.toIntOrNull()?.takeIf { it > 0 } else null
    }

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
