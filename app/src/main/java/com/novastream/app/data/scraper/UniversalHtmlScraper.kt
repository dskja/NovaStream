package com.novastream.app.data.scraper

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ProviderUrls
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/**
 * Universeller HTML-Scraper für ALLE Provider.
 * Arbeitet profilbasiert ([SiteProfile]) – kein SerienStream-only Parsing.
 */
object UniversalHtmlScraper {

    fun parseSeriesList(html: String, profile: SiteProfile): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, profile.baseUrl)
        val results = parseSeriesListFromDoc(doc, profile)
        if (results.isNotEmpty()) return results
        return parseSeriesListFallback(doc, profile)
    }

    private fun parseSeriesListFromDoc(doc: Document, profile: SiteProfile): List<Series> {
        val results = linkedMapOf<String, Series>()
        val linkPattern = profile.seriesLinkPattern.takeIf { it.isNotBlank() }?.let {
            try { Pattern.compile(it, Pattern.CASE_INSENSITIVE) } catch (_: Exception) { null }
        }
        val slugPattern = profile.slugRegex.takeIf { it.isNotBlank() }?.let {
            try { Pattern.compile(it, Pattern.CASE_INSENSITIVE) } catch (_: Exception) { null }
        }

        for (a in doc.select(profile.seriesLinkSelector)) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (href.isBlank() || href == profile.baseUrl || href == "${profile.baseUrl}/") continue
            if (linkPattern != null && !linkPattern.matcher(href).find()) continue

            val slug = extractSlug(href, slugPattern, profile) ?: continue
            if (results.containsKey(slug)) continue

            val title = resolveTitle(a, profile).ifBlank { slugToTitle(slug) }
            if (title.length < 2) continue
            val cover = findCover(a, profile)

            results[slug] = Series(
                id = slug,
                title = cleanTitle(title),
                coverUrl = cover,
                detailUrl = toDetailUrl(href, profile, slug),
                isMovie = href.contains("/movie", ignoreCase = true)
            )
        }
        return results.values.toList()
    }

    /** Broader link scan when profile-specific selectors return nothing (common on intl FMHY sites). */
    private fun parseSeriesListFallback(doc: Document, profile: SiteProfile): List<Series> {
        val fallbackProfile = profile.copy(
            seriesLinkSelector = buildFallbackSelector(profile),
            seriesLinkPattern = profile.seriesLinkPattern.ifBlank { """/([\w-]+)""" }
        )
        return parseSeriesListFromDoc(doc, fallbackProfile)
    }

    private fun buildFallbackSelector(profile: SiteProfile): String {
        val hints = listOf(
            "/movie/", "/tv/", "/tv-show/", "/watch/", "/serie/", "/serietv/",
            "/anime/", "/pelicula/", "/film/", "/titles/", "/play/", "/stream/",
            "/doramas-online/", "/serial-online/", "/show/", "/watchseries/"
        )
        val fromProfile = hints.filter { profile.seriesLinkSelector.contains(it.trim('/'), ignoreCase = true) }
        val chosen = (fromProfile + hints).distinct().take(8)
        return chosen.joinToString(", ") { "a[href*=$it]" }
    }

    fun parseDetail(html: String, profile: SiteProfile, slug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            throw IllegalStateException("Leere Antwort vom Server")
        }
        val doc = Jsoup.parse(html, profile.baseUrl)
        val title = doc.selectFirst(profile.detailTitleSelector)?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("|")?.substringBefore("—")?.trim()
            ?: slugToTitle(slug)
        val description = firstText(doc, profile.detailDescriptionSelector)
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content")
        val cover = doc.selectFirst(profile.detailCoverSelector)?.let { absImg(it, profile.baseUrl) }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        val year = Regex("""\b((?:19|20)\d{2})\b""").find(title + " " + (description ?: ""))?.groupValues?.get(1)

        val episodes = parseEpisodes(doc, profile, slug)
        var seasons = if (episodes.isNotEmpty()) {
            episodes.groupBy { it.season }.toSortedMap().map { (n, eps) ->
                Season(number = n, episodes = eps.sortedBy { it.number })
            }
        } else {
            parseSeasonPlaceholders(doc, profile)
        }

        val detailPath = toDetailUrl("", profile, slug)
        val isMovie = isMovieContent(profile.id, slug, detailPath) ||
            (profile.isMovieFocused && seasons.size <= 1 && (seasons.firstOrNull()?.episodes?.size ?: 1) <= 1)

        if (isMovie && seasons.all { it.episodes.isEmpty() }) {
            val movieUrl = ProviderUrls.movieDetailUrl(profile.id, slug)
            seasons = listOf(
                Season(
                    number = 1,
                    episodes = listOf(
                        Episode(
                            number = 1,
                            title = cleanTitle(title),
                            slug = slug,
                            season = 1,
                            episodeUrl = movieUrl
                        )
                    )
                )
            )
        }

        val series = Series(
            id = slug,
            title = cleanTitle(title),
            coverUrl = cover,
            detailUrl = detailPath,
            description = description,
            year = year,
            seasonCount = seasons.size.takeIf { it > 0 },
            isMovie = isMovie
        )
        return series to seasons
    }

    private fun isMovieContent(providerId: String, slug: String, detailPath: String): Boolean =
        ProviderUrls.isMovieSlug(providerId, slug) ||
            detailPath.contains("/movie", ignoreCase = true) ||
            detailPath.contains("/filme", ignoreCase = true)

    fun parseEpisodesOnly(html: String, profile: SiteProfile, slug: String, season: Int): List<Episode> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, profile.baseUrl)
        val all = parseEpisodes(doc, profile, slug)
        return all.filter { it.season == season }.ifEmpty { all }
    }

    fun parseHosters(html: String, profile: SiteProfile): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, profile.baseUrl)
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        for (el in doc.select(profile.hosterSelector)) {
            val url = sequenceOf(
                el.attr("data-play-url"),
                el.attr("data-video"),
                el.attr("data-link"),
                el.attr("data-src"),
                el.attr("src"),
                el.attr("href")
            ).map { it.trim() }.firstOrNull { it.startsWith("http") || it.startsWith("//") || it.startsWith("/") }
                ?: continue

            val absolute = when {
                url.startsWith("//") -> "https:$url"
                url.startsWith("http") -> url
                else -> profile.baseUrl.trimEnd('/') + "/" + url.trimStart('/')
            }
            if (!seen.add(absolute)) continue
            if (absolute.contains("facebook") || absolute.contains("googletag") || absolute.contains("histats")) continue

            val name = el.attr("title").ifBlank { el.attr("data-provider-name") }
                .ifBlank { hostNameFromUrl(absolute) }
            hosters.add(
                HosterLink(
                    name = name,
                    redirectUrl = absolute,
                    language = el.attr("data-language-label"),
                    linkId = el.attr("data-link-id"),
                    index = hosters.size
                )
            )
        }

        // data-play-url buttons (SerienStream-like)
        for (btn in doc.select("[data-play-url]")) {
            val play = btn.attr("data-play-url")
            if (play.isBlank() || !seen.add(play)) continue
            hosters.add(
                HosterLink(
                    name = btn.attr("data-provider-name").ifBlank { "Hoster" },
                    redirectUrl = if (play.startsWith("http")) play else profile.baseUrl + play,
                    language = btn.attr("data-language-label"),
                    linkId = btn.attr("data-link-id"),
                    index = hosters.size
                )
            )
        }
        return hosters
    }

    private fun parseEpisodes(doc: Document, profile: SiteProfile, slug: String): List<Episode> {
        val out = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()
        val epPattern = profile.episodeLinkPattern.takeIf { it.isNotBlank() }?.let {
            try { Pattern.compile(it, Pattern.CASE_INSENSITIVE) } catch (_: Exception) { null }
        }

        for (a in doc.select(profile.episodeLinkSelector)) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (href.isBlank()) continue
            val (season, number) = extractSeasonEpisode(href, a.text())
            if (number <= 0) continue
            val key = "$season-$number"
            if (!seen.add(key)) continue
            out.add(
                Episode(
                    number = number,
                    title = cleanTitle(a.text().ifBlank { "Episode $number" }),
                    slug = slug,
                    season = season.coerceAtLeast(1),
                    episodeUrl = href
                )
            )
        }

        // Fallback: SxxExx in any link
        if (out.isEmpty()) {
            for (a in doc.select("a[href]")) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val m = Regex("""[Ss](\d+)[Ee](\d+)""").find(href) ?: continue
                val season = m.groupValues[1].toInt()
                val number = m.groupValues[2].toInt()
                val key = "$season-$number"
                if (!seen.add(key)) continue
                out.add(
                    Episode(
                        number = number,
                        title = cleanTitle(a.text().ifBlank { "S${season}E$number" }),
                        slug = slug,
                        season = season,
                        episodeUrl = href
                    )
                )
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }

    private fun parseSeasonPlaceholders(doc: Document, profile: SiteProfile): List<Season> {
        val nums = linkedSetOf<Int>()
        for (el in doc.select(profile.seasonLinkSelector)) {
            val text = el.attr("value").ifBlank { el.text() } + " " + el.attr("href")
            Regex("""(?:season|staffel|s)\s*[-_]?(\d+)""", RegexOption.IGNORE_CASE).find(text)?.let {
                it.groupValues[1].toIntOrNull()?.takeIf { n -> n > 0 }?.let(nums::add)
            }
            el.attr("value").toIntOrNull()?.takeIf { it > 0 }?.let(nums::add)
        }
        if (nums.isEmpty()) nums.add(1)
        return nums.sorted().map { Season(number = it) }
    }

    private fun extractSeasonEpisode(href: String, text: String): Pair<Int, Int> {
        Regex("""[Ss](\d+)[Ee](\d+)""").find(href)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        Regex("""(?:episode|folge|ep)[-_/]?(\d+)""", RegexOption.IGNORE_CASE).find(href)?.let {
            val ep = it.groupValues[1].toInt()
            val season = Regex("""(?:season|staffel)[-_/]?(\d+)""", RegexOption.IGNORE_CASE)
                .find(href)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            return season to ep
        }
        Regex("""[Ss](\d+)\s*[Ee](\d+)""").find(text)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        Regex("""(?:episode|folge)\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.let {
            return 1 to it.groupValues[1].toInt()
        }
        return 1 to 0
    }

    private fun extractSlug(href: String, slugPattern: Pattern?, profile: SiteProfile): String? {
        if (slugPattern != null) {
            val m = slugPattern.matcher(href)
            if (m.find()) {
                // Prefer last numeric/meaningful group
                val g = when {
                    m.groupCount() >= 2 && !m.group(2).isNullOrBlank() -> {
                        val type = m.group(1)
                        val id = m.group(2)
                        if (type != null && (type.equals("tv", true) || type.equals("movie", true))) "$type-$id" else id
                    }
                    else -> m.group(1)
                }
                return g?.let { decode(it) }
            }
        }
        // Generic last path segment
        val path = href.substringAfter(profile.baseUrl).substringBefore("?").trim('/')
        val last = path.substringAfterLast('/').ifBlank { path }
        return last.takeIf { it.isNotBlank() && it.length < 180 }?.let { decode(it) }
    }

    private fun resolveTitle(a: Element, profile: SiteProfile): String {
        if (profile.titleSelector.isNotBlank()) {
            a.selectFirst(profile.titleSelector)?.text()?.trim()?.let { if (it.isNotBlank()) return it }
            a.closest("div, article, li")?.selectFirst(profile.titleSelector)?.text()?.trim()
                ?.let { if (it.isNotBlank()) return it }
        }
        return a.attr("title").ifBlank { null }
            ?: a.selectFirst("img")?.attr("alt")?.ifBlank { null }
            ?: a.selectFirst("h2, h3, h4, .title, .name, .film-name")?.text()?.trim()?.ifBlank { null }
            ?: a.text().trim()
    }

    private fun findCover(a: Element, profile: SiteProfile): String? {
        val scope = a.closest("div, article, li, figure") ?: a
        val img = scope.selectFirst(profile.coverSelector) ?: a.selectFirst("img")
        return img?.let { absImg(it, profile.baseUrl) }
    }

    private fun absImg(img: Element, base: String): String? {
        val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
            .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            .ifBlank {
                img.attr("srcset").split(",").firstOrNull()?.trim()?.substringBefore(" ").orEmpty()
            }
        if (src.isBlank() || src.contains("data:image") || src.endsWith(".svg")) return null
        return when {
            src.startsWith("http") -> src
            src.startsWith("//") -> "https:$src"
            else -> base.trimEnd('/') + "/" + src.trimStart('/')
        }
    }

    private fun firstText(doc: Document, selector: String): String? {
        for (part in selector.split(",")) {
            val el = doc.selectFirst(part.trim()) ?: continue
            val text = if (el.tagName() == "meta") el.attr("content") else el.text()
            if (text.isNotBlank()) return text.trim()
        }
        return null
    }

    private fun toDetailUrl(href: String, profile: SiteProfile, slug: String): String {
        if (href.startsWith("http")) return href.substringAfter(profile.baseUrl).ifBlank { href }
        if (href.startsWith("/")) return href
        return profile.absoluteLinkPrefix.ifBlank { "/$slug" }.replace("{slug}", slug)
    }

    private fun hostNameFromUrl(url: String): String {
        return try {
            val host = java.net.URI(url).host ?: return "Embed"
            host.removePrefix("www.").substringBefore('.').replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            "Embed"
        }
    }

    private fun cleanTitle(title: String): String =
        com.novastream.app.util.MediaUrls.sanitizeTitle(
            title.replace(Regex("\\s+"), " ")
                .replace(Regex("(?i)\\s*online\\s*(free|schauen|stream).*"), "")
                .replace(Regex("(?i)\\s*watch\\s+"), " ")
                .trim()
        ).ifBlank { title.trim() }

    private fun slugToTitle(slug: String): String {
        val cleaned = if (slug.matches(Regex("""(?i)(tv|movie)-\d+"""))) {
            slug.substringAfter("-")
        } else slug
        return cleaned.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun decode(s: String): String =
        try { java.net.URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }
}
