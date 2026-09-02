package com.novastream.app.data.api

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.Genre
import com.novastream.app.data.model.HomeCatalog
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.LatestEpisode
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.NovaStreamConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Massiv ausgebauter Scraper für serienstream.to / serienstream.cx.
 *
 * Abgedeckt (Stand 2026, gegen Live-HTML getestet):
 *   Home:          home-hero-overlay, card-mini, trend-card, latest-episode-row, Top 5
 *   Listen:        show-card (Beliebte, Genre, Katalog /serien)
 *   Detail:        h1, description-text, show-cover-mobile, Genres, Rating, Backdrop
 *   Episoden:      tr.episode-row + Fallback-Links
 *   Hoster:        button.link-box[data-play-url]
 *   Neue Episoden: /neue-episoden (latest-episode-row + Tabellen-Fallback)
 *   Beliebte:      /beliebte-serien (show-card)
 */
object NovaStreamScraper {

    private val SLUG_PATTERN = Pattern.compile("/serie/([\\w%.-]+?)(?:/|$)")
    private val EP_URL_PATTERN = Pattern.compile("/serie/[\\w%.-]+/staffel-(\\d+)/episode-(\\d+)")
    private val SEASON_PATTERN = Pattern.compile("/staffel-(\\d+)")
    private val YEAR_PATTERN = Pattern.compile("\\b((?:19|20)\\d{2})\\b")
    private val RATING_PATTERN = Pattern.compile("(\\d+[.,]\\d+)\\s*/\\s*10|IMDb[^\\d]*(\\d+[.,]\\d+)", Pattern.CASE_INSENSITIVE)

    private val baseUrlOverride = ThreadLocal<String?>()

    /** Temporär die Base-URL für Cover/Parse setzen (pro Coroutine/Thread). */
    fun <T> withBaseUrl(baseUrl: String, block: () -> T): T {
        val prev = baseUrlOverride.get()
        baseUrlOverride.set(baseUrl.trimEnd('/'))
        return try {
            block()
        } finally {
            if (prev == null) baseUrlOverride.remove() else baseUrlOverride.set(prev)
        }
    }

    private fun siteBase(): String =
        baseUrlOverride.get()?.takeIf { it.isNotBlank() } ?: NovaStreamConfig.BASE_URL

    // ─── Home Catalog (strukturierte Sektionen) ─────────────────────────────

    /** Parst die Startseite in strukturierte Sektionen. */
    fun parseHomeCatalog(html: String): HomeCatalog {
        if (html.isBlank()) return HomeCatalog()
        val doc = Jsoup.parse(html, siteBase())

        val hero = parseHeroSeries(doc)
        val cardMini = parseCardMiniSeries(doc)
        val trending = parseTrendSeries(doc)
        val topShows = parseTopShows(doc)
        val latest = parseLatestEpisodeRows(doc)
        val fallback = parseGenericSeriesLinks(doc, excludeSeasonLinks = true)

        val allMap = linkedMapOf<String, Series>()
        for (s in hero + cardMini + trending + topShows + fallback) {
            allMap.putIfAbsent(s.id, s)
        }

        return HomeCatalog(
            hero = hero,
            popular = (topShows + cardMini).distinctBy { it.id }.take(24),
            newest = cardMini.ifEmpty { fallback.take(24) },
            trending = trending.ifEmpty { fallback.drop(8).take(24) },
            latestEpisodes = latest,
            topShows = topShows,
            all = allMap.values.toList()
        )
    }

    /** Parst eine flache Serienliste (Home, Suche, Genre, Beliebte, Katalog). */
    fun parseSeriesList(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, siteBase())
        val results = linkedMapOf<String, Series>()

        for (s in parseHeroSeries(doc)) results.putIfAbsent(s.id, s)
        for (s in parseCardMiniSeries(doc)) results.putIfAbsent(s.id, s)
        for (s in parseTrendSeries(doc)) results.putIfAbsent(s.id, s)
        for (s in parseShowCards(doc)) results.putIfAbsent(s.id, s)
        for (s in parseTopShows(doc)) results.putIfAbsent(s.id, s)
        for (s in parseGenericSeriesLinks(doc, excludeSeasonLinks = true)) results.putIfAbsent(s.id, s)

        return results.values.toList()
    }

    private fun parseHeroSeries(doc: Document): List<Series> {
        val out = mutableListOf<Series>()
        for (a in doc.select("a.home-hero-overlay[href*=/serie/], a[href*=/serie/].home-hero-overlay")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractSlug(href) ?: continue
            if (href.contains("/staffel-")) continue
            val title = a.selectFirst("h2.home-hero-title, h2")?.text()?.trim()
                ?: a.attr("title").ifBlank { null }
                ?: slugToTitle(slug)
            val container = a.closest(".home-hero-slide, .swiper-slide, .home-hero") ?: a
            val cover = findCoverInContainer(container, doc)
            val backdrop = findBackdropInContainer(container) ?: cover
            out.add(
                Series(
                    id = slug,
                    title = cleanTitle(title),
                    coverUrl = cover,
                    backdropUrl = backdrop,
                    detailUrl = "/serie/$slug"
                )
            )
        }
        return out.distinctBy { it.id }
    }

    private fun parseCardMiniSeries(doc: Document): List<Series> {
        val out = mutableListOf<Series>()
        for (card in doc.select("div.card-mini, .card-mini-tile")) {
            val a = card.selectFirst("a[href*=/serie/]") ?: continue
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractSlug(href) ?: continue
            if (href.contains("/staffel-") || href.contains("/episode-")) continue
            val title = card.selectFirst("h3")?.attr("title")?.ifBlank { null }
                ?: card.selectFirst("h3, .show-title")?.text()?.trim()
                ?: a.text().trim().ifBlank { slugToTitle(slug) }
            val genre = card.selectFirst(".genre, .card-genre, small")?.text()?.trim()
            out.add(
                Series(
                    id = slug,
                    title = cleanTitle(title),
                    coverUrl = findCoverInContainer(card, doc),
                    detailUrl = "/serie/$slug",
                    genres = genre?.takeIf { it.isNotBlank() && it.length < 40 }?.let { listOf(it) } ?: emptyList()
                )
            )
        }
        return out.distinctBy { it.id }
    }

    private fun parseTrendSeries(doc: Document): List<Series> {
        val out = mutableListOf<Series>()
        for (card in doc.select("article.trend-card, .trend-card")) {
            val a = card.selectFirst("a[href*=/serie/]") ?: continue
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractSlug(href) ?: continue
            if (href.contains("/staffel-")) continue
            val title = card.selectFirst("h3.trend-title, h3")?.text()?.trim()
                ?: a.text().trim().ifBlank { slugToTitle(slug) }
            out.add(
                Series(
                    id = slug,
                    title = cleanTitle(title),
                    coverUrl = findCoverInContainer(card, doc),
                    detailUrl = "/serie/$slug"
                )
            )
        }
        return out.distinctBy { it.id }
    }

    private fun parseShowCards(doc: Document): List<Series> {
        val out = mutableListOf<Series>()
        for (card in doc.select("a.show-card[href*=/serie/], .show-card a[href*=/serie/], article.show-card a[href*=/serie/]")) {
            val href = card.absUrl("href").ifBlank { card.attr("href") }
            val slug = extractSlug(href) ?: continue
            // show-card kann auf Staffel verlinken – Serie trotzdem übernehmen
            val container = card.closest(".show-card") ?: card
            val title = container.selectFirst(".show-title, h3, h2, .card-title")?.text()?.trim()
                ?: card.attr("title").ifBlank { null }?.substringBefore(" stream")
                ?: card.text().trim().ifBlank { slugToTitle(slug) }
            val year = extractYear(container.text())
            out.add(
                Series(
                    id = slug,
                    title = cleanTitle(title),
                    coverUrl = findCoverInContainer(container, doc),
                    detailUrl = "/serie/$slug",
                    year = year
                )
            )
        }
        return out.distinctBy { it.id }
    }

    private fun parseTopShows(doc: Document): List<Series> {
        val out = mutableListOf<Series>()
        for (a in doc.select("a.top-shows-separator[href*=/serie/], .top-show-item a[href*=/serie/], .top-shows a[href*=/serie/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractSlug(href) ?: continue
            if (href.contains("/staffel-")) continue
            val container = a.closest(".top-show-item, .top-shows-item, li, article") ?: a
            val title = container.selectFirst(".show-title, h3, h2")?.text()?.trim()
                ?: a.attr("title").ifBlank { null }
                ?: a.text().trim().ifBlank { slugToTitle(slug) }
            out.add(
                Series(
                    id = slug,
                    title = cleanTitle(title),
                    coverUrl = findCoverInContainer(container, doc),
                    detailUrl = "/serie/$slug"
                )
            )
        }
        return out.distinctBy { it.id }
    }

    private fun parseGenericSeriesLinks(doc: Document, excludeSeasonLinks: Boolean): List<Series> {
        val out = mutableListOf<Series>()
        for (a in doc.select("a[href*=/serie/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractSlug(href) ?: continue
            if (excludeSeasonLinks && (href.contains("/staffel-") || href.contains("/episode-"))) continue
            val title = a.selectFirst("h3")?.attr("title")?.ifBlank { null }
                ?: a.selectFirst("h3, h2, .show-title")?.text()?.trim()
                ?: a.attr("title").ifBlank { null }?.substringBefore(" stream")
                ?: a.text().trim().ifBlank { slugToTitle(slug) }
            if (title.length < 2) continue
            out.add(
                Series(
                    id = slug,
                    title = cleanTitle(title),
                    coverUrl = findCoverInContainer(a.parent() ?: a, doc),
                    detailUrl = "/serie/$slug"
                )
            )
        }
        return out.distinctBy { it.id }
    }

    // ─── Neue Episoden ──────────────────────────────────────────────────────

    fun parseLatestEpisodes(html: String): List<LatestEpisode> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, siteBase())
        val fromRows = parseLatestEpisodeRows(doc)
        if (fromRows.isNotEmpty()) return fromRows

        // Tabellen-/Listen-Fallback auf /neue-episoden
        val out = mutableListOf<LatestEpisode>()
        for (a in doc.select("a[href*=/serie/][href*=/episode-]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = EP_URL_PATTERN.matcher(href)
            if (!m.find()) continue
            val slug = extractSlug(href) ?: continue
            val season = m.group(1)?.toIntOrNull() ?: continue
            val episode = m.group(2)?.toIntOrNull() ?: continue
            val title = a.selectFirst(".ep-title-text, .ep-title")?.text()?.trim()
                ?: a.attr("title").ifBlank { null }
                ?: a.text().trim().ifBlank { slugToTitle(slug) }
            out.add(
                LatestEpisode(
                    seriesSlug = slug,
                    seriesTitle = cleanTitle(title),
                    season = season,
                    episode = episode,
                    episodeUrl = m.group(0) ?: href,
                    language = detectLanguageLabel(a)
                )
            )
        }
        return out.distinctBy { "${it.seriesSlug}-${it.season}-${it.episode}" }
    }

    private fun parseLatestEpisodeRows(doc: Document): List<LatestEpisode> {
        val out = mutableListOf<LatestEpisode>()
        for (a in doc.select("a.latest-episode-row[href*=/serie/], .latest-episode-row a[href*=/serie/]")) {
            val row = if (a.hasClass("latest-episode-row")) a else a.closest(".latest-episode-row") ?: a
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = EP_URL_PATTERN.matcher(href)
            if (!m.find()) continue
            val slug = extractSlug(href) ?: continue
            val season = m.group(1)?.toIntOrNull()
                ?: row.selectFirst(".ep-season")?.text()?.filter { it.isDigit() }?.toIntOrNull()
                ?: 1
            val episode = m.group(2)?.toIntOrNull()
                ?: row.selectFirst(".ep-episode")?.text()?.filter { it.isDigit() }?.toIntOrNull()
                ?: continue
            val title = row.selectFirst(".ep-title-text, .ep-title")?.text()?.trim()
                ?: row.attr("title").ifBlank { null }
                ?: slugToTitle(slug)
            out.add(
                LatestEpisode(
                    seriesSlug = slug,
                    seriesTitle = cleanTitle(title),
                    season = season,
                    episode = episode,
                    language = detectLanguageLabel(row),
                    timeLabel = row.selectFirst(".ep-time")?.text()?.trim().orEmpty(),
                    episodeUrl = m.group(0) ?: href
                )
            )
        }
        return out.distinctBy { "${it.seriesSlug}-${it.season}-${it.episode}" }
    }

    private fun detectLanguageLabel(el: Element): String {
        val text = el.selectFirst(".ep-lang, .watch-language")?.attr("title")
            ?: el.select("use").attr("href").ifBlank { el.select("use").attr("xlink:href") }
        return when {
            text.contains("german", ignoreCase = true) || text.contains("deutsch", ignoreCase = true) -> "Deutsch"
            text.contains("english", ignoreCase = true) || text.contains("eng", ignoreCase = true) -> "Englisch"
            text.contains("sub", ignoreCase = true) -> "Subs"
            else -> ""
        }
    }

    // ─── Cover / Media Helpers ──────────────────────────────────────────────

    private fun findCoverInContainer(container: Element, doc: Document): String? {
        val candidates = listOf(
            container.selectFirst("img[data-src]"),
            container.selectFirst("img[src]"),
            container.selectFirst("source[data-srcset]"),
            container.selectFirst("source[srcset]")
        )
        for (el in candidates) {
            if (el == null) continue
            when {
                el.hasAttr("data-src") -> absMedia(el.absUrl("data-src").ifBlank { el.attr("data-src") })?.let { return it }
                el.hasAttr("src") && el.tagName() == "img" -> absMedia(el.absUrl("src").ifBlank { el.attr("src") })?.let { return it }
                el.hasAttr("data-srcset") -> firstFromSrcset(el.attr("data-srcset"))?.let { return it }
                el.hasAttr("srcset") -> firstFromSrcset(el.attr("srcset"))?.let { return it }
            }
        }
        // Background-image style
        val style = container.attr("style")
        Regex("url\\(['\"]?([^'\")]+)['\"]?\\)").find(style)?.groupValues?.getOrNull(1)?.let {
            absMedia(it)?.let { u -> return u }
        }
        return null
    }

    private fun findBackdropInContainer(container: Element): String? {
        val style = container.attr("style") + " " + (container.selectFirst("[style*=background]")?.attr("style") ?: "")
        Regex("url\\(['\"]?([^'\")]+)['\"]?\\)").find(style)?.groupValues?.getOrNull(1)?.let {
            absMedia(it)?.let { u -> return u }
        }
        container.selectFirst("img.backdrop, img.hero-bg, source[data-srcset]")?.let { el ->
            when {
                el.hasAttr("data-srcset") -> return firstFromSrcset(el.attr("data-srcset"))
                el.hasAttr("data-src") -> return absMedia(el.absUrl("data-src").ifBlank { el.attr("data-src") })
                el.hasAttr("src") -> return absMedia(el.absUrl("src").ifBlank { el.attr("src") })
            }
        }
        return null
    }

    private fun firstFromSrcset(srcset: String): String? {
        val firstUrl = srcset.split(",")
            .map { it.trim().split(" ").firstOrNull().orEmpty() }
            .firstOrNull { it.isNotBlank() && !it.contains("data:image") }
            ?: return null
        return absMedia(firstUrl)
    }

    private fun absMedia(src: String?): String? {
        if (src.isNullOrBlank() || src.contains("data:image") || src.endsWith(".svg")) return null
        return if (src.startsWith("http")) src
        else if (src.startsWith("//")) "https:$src"
        else siteBase() + (if (src.startsWith("/")) src else "/$src")
    }

    // ─── Serien-Detail + Staffeln ───────────────────────────────────────────

    fun parseSeriesDetail(html: String, slug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(id = slug, title = slugToTitle(slug), detailUrl = "/serie/$slug") to emptyList()
        }
        val doc = Jsoup.parse(html, siteBase())

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?.substringBefore(" Staffel")
            ?.substringBefore(" | ")
            ?.trim()
            ?: slugToTitle(slug)

        val cover = extractDetailCover(doc)
        val backdrop = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?.takeIf { it.isNotBlank() && !it.contains("logo") && !it.contains("facebook") }

        val description = doc.selectFirst(".description-text, .series-description, .seri_des, [itemprop=description]")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:description], meta[name=description]")?.attr("content")?.trim()

        val genres = doc.select("a[href^=/genre/]")
            .mapNotNull { it.text().trim().ifBlank { null } }
            .filter { it.length in 2..40 && !it.contains("→") && !it.contains("Top-") }
            .distinct()
            .take(12)

        val year = extractYear(
            doc.selectFirst(".series-info, .meta, .show-meta, .year, [itemprop=dateCreated]")?.text()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: ""
        ) ?: extractYear(doc.text().take(2000))

        val rating = extractRating(doc)

        val series = Series(
            id = slug,
            title = cleanTitle(title),
            coverUrl = cover,
            backdropUrl = backdrop,
            detailUrl = "/serie/$slug",
            description = description,
            genres = genres,
            year = year,
            rating = rating,
            seasonCount = parseSeasons(doc, slug).size.takeIf { it > 0 }
        )

        return series to parseSeasons(doc, slug)
    }

    private fun extractDetailCover(doc: Document): String? {
        val selectors = listOf(
            "div.show-cover-mobile img[data-src]",
            "div.show-cover-mobile img[src]",
            ".series-cover img[data-src]",
            ".series-cover img[src]",
            ".cover img[data-src]",
            ".show-cover img[data-src]",
            ".show-cover img[src]",
            "img[itemprop=image]"
        )
        for (sel in selectors) {
            val img = doc.selectFirst(sel) ?: continue
            absMedia(
                img.absUrl("data-src").ifBlank { img.attr("data-src") }
                    .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            )?.let { return it }
        }

        doc.selectFirst("source[data-srcset]")?.attr("data-srcset")?.let { firstFromSrcset(it)?.let { u -> return u } }
        doc.selectFirst("source[srcset]")?.attr("srcset")?.let { firstFromSrcset(it)?.let { u -> return u } }

        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")
        if (!ogImage.isNullOrBlank() && !ogImage.contains("facebook") && !ogImage.contains("logo")) return ogImage

        doc.selectFirst("img[data-src]")?.let { absMedia(it.absUrl("data-src").ifBlank { it.attr("data-src") })?.let { u -> return u } }
        return null
    }

    private fun extractRating(doc: Document): String? {
        val candidates = listOf(
            doc.selectFirst("[itemprop=ratingValue]")?.attr("content")?.ifBlank { null }
                ?: doc.selectFirst("[itemprop=ratingValue]")?.text(),
            doc.selectFirst(".rating, .imdb-rating, .score")?.text(),
            doc.selectFirst("meta[property=og:description]")?.attr("content")
        )
        for (c in candidates) {
            if (c.isNullOrBlank()) continue
            val m = RATING_PATTERN.matcher(c)
            if (m.find()) {
                return (m.group(1) ?: m.group(2))?.replace(',', '.')
            }
        }
        return null
    }

    private fun parseSeasons(doc: Document, slug: String): List<Season> {
        val seasonNumbers = linkedSetOf<Int>()

        for (a in doc.select("a[data-season-pill]")) {
            a.attr("data-season-pill").toIntOrNull()?.takeIf { it > 0 }?.let { seasonNumbers.add(it) }
        }
        for (a in doc.select("a[href*=/staffel-]")) {
            extractSeasonNumber(a.absUrl("href").ifBlank { a.attr("href") })?.let { seasonNumbers.add(it) }
        }
        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)

        val currentEpisodes = parseEpisodes(doc, slug)
        val seasons = mutableListOf<Season>()
        for (n in seasonNumbers.sorted()) {
            val eps = if (currentEpisodes.isNotEmpty() && currentEpisodes.first().season == n) currentEpisodes else emptyList()
            seasons.add(Season(number = n, episodes = eps))
        }
        if (seasons.isEmpty() && currentEpisodes.isNotEmpty()) {
            seasons.add(Season(number = 1, episodes = currentEpisodes))
        }
        return seasons
    }

    private fun parseEpisodes(doc: Document, slug: String): List<Episode> {
        val episodes = mutableListOf<Episode>()

        for (row in doc.select("tr.episode-row, .episode-row")) {
            val onclick = row.attr("onclick")
            val href = row.selectFirst("a[href*=/episode-]")?.absUrl("href")
                ?: row.attr("data-href")
            val source = onclick.ifBlank { href.orEmpty() }
            val m = EP_URL_PATTERN.matcher(source)
            if (!m.find()) continue

            val season = m.group(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
            val epNum = m.group(2)?.toIntOrNull()?.takeIf { it > 0 } ?: continue
            val epUrl = m.group(0) ?: ""

            val title = row.selectFirst(".episode-title-ger")?.text()?.trim()?.ifBlank { null }
                ?: row.selectFirst(".episode-title-eng")?.text()?.trim()?.ifBlank { null }
                ?: row.selectFirst(".episode-title-cell, .episode-title")?.text()?.trim()?.ifBlank { null }
                ?: "Folge $epNum"

            val thumbImg = row.selectFirst("img[data-src]") ?: row.selectFirst("img[src]")
            val thumbnail = thumbImg?.let {
                absMedia(
                    it.absUrl("data-src").ifBlank { it.attr("data-src") }
                        .ifBlank { it.absUrl("src") }.ifBlank { it.attr("src") }
                )
            }

            val hosters = row.select("img.watch-link, .watch-link img").mapIndexed { idx, img ->
                HosterLink(
                    name = img.attr("title").ifBlank { img.attr("alt") }.ifBlank { "Hoster" },
                    redirectUrl = "",
                    index = idx
                )
            }

            episodes.add(
                Episode(
                    number = epNum,
                    title = cleanTitle(title),
                    hosters = hosters,
                    slug = slug,
                    season = season,
                    episodeUrl = epUrl,
                    thumbnailUrl = thumbnail
                )
            )
        }

        if (episodes.isEmpty()) {
            val seen = mutableSetOf<String>()
            for (a in doc.select("a[href*=/staffel-][href*=/episode-]")) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val m = EP_URL_PATTERN.matcher(href)
                if (!m.find()) continue
                val season = m.group(1)?.toIntOrNull() ?: 1
                val epNum = m.group(2)?.toIntOrNull() ?: continue
                val key = "$season-$epNum"
                if (!seen.add(key)) continue
                episodes.add(
                    Episode(
                        number = epNum,
                        title = cleanTitle(a.text().trim().ifBlank { "Episode $epNum" }),
                        slug = slug,
                        season = season,
                        episodeUrl = m.group(0) ?: href
                    )
                )
            }
        }

        return episodes.sortedBy { it.number }
    }

    fun parseSeasonEpisodes(html: String, slug: String, season: Int): List<Episode> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, siteBase())
        return parseEpisodes(doc, slug).map { if (it.season == 0) it.copy(season = season) else it }
            .filter { it.season == season || season <= 0 }
            .ifEmpty { parseEpisodes(doc, slug) }
    }

    // ─── Hoster ─────────────────────────────────────────────────────────────

    fun parseHosters(html: String): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, siteBase())
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        for (btn in doc.select("button.link-box[data-play-url], a.link-box[data-play-url], [data-play-url]")) {
            val playUrl = btn.attr("data-play-url")
            if (playUrl.isBlank()) continue
            val name = btn.attr("data-provider-name").ifBlank {
                btn.selectFirst("img")?.attr("title")
                    ?: btn.selectFirst("img")?.attr("alt")
                    ?: btn.text().trim().ifBlank { "Unknown" }
            }
            val language = btn.attr("data-language-label").ifBlank {
                btn.selectFirst(".lang, .language")?.text()?.trim().orEmpty()
            }
            val linkId = btn.attr("data-link-id")
            val key = "$name|$playUrl|$language"
            if (seen.add(key)) {
                hosters.add(
                    HosterLink(
                        name = name.trim(),
                        redirectUrl = playUrl,
                        language = language,
                        linkId = linkId,
                        index = hosters.size
                    )
                )
            }
        }

        // Fallback: redirect-Links
        if (hosters.isEmpty()) {
            for (a in doc.select("a[href*=/r?], a[href*=/redirect/]")) {
                val href = a.attr("href")
                if (href.isBlank()) continue
                val name = a.attr("title").ifBlank { a.text() }.ifBlank { "Hoster" }
                if (seen.add(href)) {
                    hosters.add(HosterLink(name = name.trim(), redirectUrl = href, index = hosters.size))
                }
            }
        }

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

    // ─── Genres / Convenience ───────────────────────────────────────────────

    fun parseGenreList(html: String): List<Series> = parseSeriesList(html)
    fun parseNewestList(html: String): List<Series> = parseSeriesList(html)
    fun parsePopularList(html: String): List<Series> = parseSeriesList(html)

    fun parseGenres(html: String): List<Genre> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, siteBase())
        val genres = linkedMapOf<String, String>()
        for (a in doc.select("a[href^=/genre/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = href.substringAfter("/genre/").substringBefore("/").substringBefore("?").trim()
            if (slug.isBlank()) continue
            val name = a.text().trim()
                .substringBefore("→").substringBefore("|").trim()
                .ifBlank { slugToTitle(slug) }
            if (name.contains("Top-", ignoreCase = true) || name.contains("Entdecken")) continue
            genres.putIfAbsent(slug, name)
        }
        return genres.map { (slug, name) -> Genre(slug, name) }
    }

    /** @deprecated use [parseGenres] */
    fun parseGenrePairs(html: String): List<Pair<String, String>> =
        parseGenres(html).map { it.slug to it.name }

    // ─── Hilfsfunktionen ────────────────────────────────────────────────────

    private fun extractSlug(url: String): String? {
        val m = SLUG_PATTERN.matcher(url)
        if (!m.find()) return null
        val slug = m.group(1) ?: return null
        return try {
            java.net.URLDecoder.decode(slug, "UTF-8")
        } catch (_: Exception) {
            slug
        }
    }

    private fun extractSeasonNumber(url: String): Int? {
        val m = SEASON_PATTERN.matcher(url)
        return if (m.find()) m.group(1)?.toIntOrNull()?.takeIf { it > 0 } else null
    }

    private fun extractYear(text: String): String? {
        val m = YEAR_PATTERN.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private fun cleanTitle(title: String): String =
        com.novastream.app.util.MediaUrls.sanitizeTitle(
            title.replace(Regex("\\s+"), " ")
                .replace(Regex("(?i)\\s*online\\s*stream(en)?"), "")
                .replace(Regex("(?i)\\s*stream(en)?\\s*kostenlos"), "")
                .trim()
                .ifBlank { title.trim() }
        ).ifBlank { title.trim() }

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
