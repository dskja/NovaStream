package com.serienstream.app.data.api

import com.serienstream.app.data.model.Episode
import com.serienstream.app.data.model.HosterLink
import com.serienstream.app.data.model.Season
import com.serienstream.app.data.model.Series
import com.serienstream.app.data.model.SerienStreamConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Parst das HTML von SerienStream (serienstream.to) mit Jsoup.
 *
 * URL-Schema (Stand 2025/2026):
 *   /serie/{slug}                     – Serien-Detail (redirectet auf staffel-1)
 *   /serie/{slug}/staffel-{n}         – Staffel-Seite mit Episodenliste
 *   /serie/{slug}/staffel-{n}/episode-{m} – Episoden-Seite mit Hoster-Buttons
 *
 * Die Selektoren basieren auf dem aktuellen Bootstrap-basierten Markup.
 */
object SerienStreamScraper {

    private val SLUG_PATTERN = Pattern.compile("/serie/([\\w%.-]+?)(?:/|$)")

    // ─── Serien-Listen (Home, Suche) ────────────────────────────────────────

    /** Parst eine Liste von Serien (Startseite, Suche). */
    fun parseSeriesList(html: String): List<Series> {
        val doc = Jsoup.parse(html, SerienStreamConfig.BASE_URL)
        val results = linkedMapOf<String, Series>()

        // Alle <a> mit href="/serie/{slug}" (ohne /staffel- oder /episode- im Pfad)
        // Das deckt ab: show-cover, stretched-link, text-decoration-none, card-mini Links, trend Links
        val seriesAnchors = doc.select("a[href~=/serie/[\\w%.-]+\$]")

        for (a in seriesAnchors) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractSlug(href) ?: continue
            if (results.containsKey(slug)) continue

            // Title: versuche show-title h6, dann h3 title, dann anchor title attr, dann anchor text
            val title = a.closest(".cover-card, .card-mini, .card")?.selectFirst(".show-title")?.text()?.trim()?.ifBlank { null }
                ?: a.selectFirst("h3")?.attr("title")?.ifBlank { null }
                ?: a.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
                ?: a.attr("title").ifBlank { null }
                ?: a.text().trim().ifBlank { null }
                ?: slugToTitle(slug)

            // Cover-Bild suchen: im übergeordneten Container ODER im Anchor selbst
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
        // Der Container kann der Parent ODER der Anchor selbst sein (wenn das Bild inside ist)
        val container = anchor.closest(".cover-card, .trend-cover, .card, .card-mini, .col-6, .col-md-4, .col-lg-2, .trend-content")
            ?: anchor.parent()

        // Suche in beiden: Container (parent) und Anchor selbst (inside)
        val searchRoots = listOfNotNull(container, anchor)

        for (root in searchRoots) {
            // Weg 1: <img> mit data-src (Lazy Loading – SerienStream nutzt das primär)
            val img = root.selectFirst("img[data-src]") ?: root.selectFirst("img[src]")
            if (img != null) {
                val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                    .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
                if (src.isNotBlank() && !src.contains("data:image") && src.contains("/media/images/")) return src
            }

            // Weg 2: <source> in <picture> mit srcset (search page nutzt srcset, nicht data-srcset)
            val source = root.selectFirst("source[srcset]")
            if (source != null) {
                val srcset = source.attr("srcset")
                val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                if (!firstUrl.isNullOrBlank() && firstUrl.contains("/media/images/")) {
                    return if (firstUrl.startsWith("http")) firstUrl
                          else SerienStreamConfig.BASE_URL + firstUrl
                }
            }

            // Weg 3: data-srcset auf <source> (home page nutzt data-srcset für lazy loading)
            val dataSrcSet = root.selectFirst("source[data-srcset]")
            if (dataSrcSet != null) {
                val srcset = dataSrcSet.attr("data-srcset")
                val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                if (!firstUrl.isNullOrBlank() && firstUrl.contains("/media/images/")) {
                    return if (firstUrl.startsWith("http")) firstUrl
                          else SerienStreamConfig.BASE_URL + firstUrl
                }
            }
        }

        return null
    }

    // ─── Serien-Detail + Staffeln ───────────────────────────────────────────

    /** Extrahiert das Cover/Backdrop-Bild aus der Detail-Seite. */
    private fun extractDetailCover(doc: Document): String? {
        // Weg 1: <img> mit data-src im Hauptbereich
        val img = doc.selectFirst("img[data-src]") ?: doc.selectFirst("img[src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image") && src.contains("/media/images/")) return src
        }

        // Weg 2: <source> mit srcset
        val source = doc.selectFirst("source[srcset]")
        if (source != null) {
            val srcset = source.attr("srcset")
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstUrl.isNullOrBlank() && firstUrl.contains("/media/images/")) {
                return if (firstUrl.startsWith("http")) firstUrl
                       else SerienStreamConfig.BASE_URL + firstUrl
            }
        }

        // Weg 3: og:image Meta-Tag
        val ogImage = doc.selectFirst("meta[property=og:image]")
        if (ogImage != null) {
            val content = ogImage.attr("content")
            if (content.isNotBlank()) return content
        }

        return null
    }

    /** Parst die Serien-Detail-/Staffel-Seite: Beschreibung, Staffeln, Episoden. */
    fun parseSeriesDetail(html: String, slug: String): Pair<Series, List<Season>> {
        val doc = Jsoup.parse(html, SerienStreamConfig.BASE_URL)

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
            a.attr("data-season-pill").toIntOrNull()?.let { seasonNumbers.add(it) }
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
        val epUrlPattern = Pattern.compile("/serie/[\\w%.-]+/staffel-(\\d+)/episode-(\\d+)")

        for (row in rows) {
            val onclick = row.attr("onclick")
            val m = epUrlPattern.matcher(onclick)
            if (m.find()) {
                val season = m.group(1).toIntOrNull() ?: 1
                val epNum = m.group(2).toIntOrNull() ?: continue
                val epUrl = m.group(0)

                val title = row.selectFirst(".episode-title-ger")?.text()?.trim()?.ifBlank { null }
                    ?: row.selectFirst(".episode-title-eng")?.text()?.trim()?.ifBlank { null }
                    ?: row.selectFirst(".episode-title-cell")?.text()?.trim()?.ifBlank { null }
                    ?: "Folge $epNum"

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
                    episodeUrl = epUrl
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
        val doc = Jsoup.parse(html, SerienStreamConfig.BASE_URL)
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
        val doc = Jsoup.parse(html, SerienStreamConfig.BASE_URL)
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
        return java.net.URLDecoder.decode(slug, "UTF-8")
    }

    private fun extractSeasonNumber(url: String): Int? {
        val m = Pattern.compile("/staffel-(\\d+)").finder(url)
        return if (m.find()) m.group(1)?.toIntOrNull() else null
    }

    private fun Pattern.finder(input: String): java.util.regex.Matcher = matcher(input)

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
