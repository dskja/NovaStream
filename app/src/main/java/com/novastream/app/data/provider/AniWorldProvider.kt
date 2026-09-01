package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.Genre
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.AjaxSearchClient
import com.novastream.app.util.HosterResolver
import com.novastream.app.util.MediaUrls
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Provider für AniWorld.to – strikt vom SerienStream-Katalog getrennt.
 * Nutzt Anime-Stream-Pfade, AJAX-Suche und Alphabet-Katalog.
 */
class AniWorldProvider(
    override val id: String = "aniworld",
    override val displayName: String = "AniWorld",
    override val baseUrl: String = "https://aniworld.to",
    override val supportsSeries: Boolean = true,
    private val appContext: Context? = null
) : StreamingProvider {

    private val mirror = MirrorSupport(id, baseUrl, appContext, "/anime/stream/")

    private val hosterResolver get() = HosterResolver(baseUrl = mirror.parseBase())

    private suspend fun activeBaseUrl(): String = mirror.activeBase()

    private fun parseBase(): String = mirror.parseBase()

    override val supportsMovies: Boolean = false

    override val catalogHint: String? = ProviderCatalogHints.forId(id)

    override val availableGenres: List<Genre> = listOf(
        Genre("action", "Action"),
        Genre("adventure", "Abenteuer"),
        Genre("comedy", "Comedy"),
        Genre("drama", "Drama"),
        Genre("fantasy", "Fantasy"),
        Genre("horror", "Horror"),
        Genre("romance", "Romance"),
        Genre("sci-fi", "Sci-Fi"),
        Genre("slice-of-life", "Slice of Life"),
        Genre("supernatural", "Supernatural"),
        Genre("thriller", "Thriller"),
        Genre("mystery", "Mystery")
    )

    private fun tag(series: Series): Series = series.copy(
        providerId = id,
        title = MediaUrls.sanitizeTitle(series.title).ifBlank { series.title },
        coverUrl = MediaUrls.abs(series.coverUrl, parseBase()),
        backdropUrl = MediaUrls.abs(series.backdropUrl, parseBase()),
        detailUrl = series.detailUrl.ifBlank { "/anime/stream/${series.id}" }
    )

    private fun tagAll(list: List<Series>): List<Series> = list.map { tag(it) }

    private fun parseSeriesListAniWorld(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val results = linkedMapOf<String, Series>()

        for (a in doc.select("div.seriesListContainer a[href*=/anime/stream/], a[href*=/anime/stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractAniWorldSlug(href) ?: continue
            if (href.contains("/staffel-") || href.contains("/episode-")) continue
            if (results.containsKey(slug)) continue

            val title = MediaUrls.sanitizeTitle(
                a.selectFirst("h3")?.text()?.trim()
                    ?: a.attr("title")
                    ?: a.selectFirst("h2")?.text()
                    ?: a.text()
            ).ifBlank { slugToTitle(slug) }

            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = findAniWorldCover(a),
                detailUrl = "/anime/stream/$slug",
                providerId = id
            )
        }
        return results.values.toList()
    }

    private fun extractAniWorldSlug(url: String): String? {
        val pattern = java.util.regex.Pattern.compile("/anime/stream/([\\w%.-]+?)(?:/|$)")
        val m = pattern.matcher(url)
        if (!m.find()) return null
        val slug = m.group(1) ?: return null
        // Hard filter: niemals /serie/ von SerienStream
        if (url.contains("/serie/") && !url.contains("/anime/stream/")) return null
        return try {
            java.net.URLDecoder.decode(slug, "UTF-8")
        } catch (_: Exception) {
            slug
        }
    }

    private fun findAniWorldCover(anchor: Element): String? {
        val img = anchor.selectFirst("img[data-src]") ?: anchor.selectFirst("img[src]") ?: return null
        val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
            .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
        return MediaUrls.abs(src, parseBase())
    }

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val home = parseSeriesListAniWorld(fetchUrl(base))
        val popular = parseSeriesListAniWorld(fetchUrl("$base/beliebte-animes").ifBlank {
            fetchUrl("$base/animes")
        })
        tagAll((home + popular).distinctBy { it.id })
    }

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/beliebte-animes").ifBlank { fetchUrl(base) }
        tagAll(parseSeriesListAniWorld(html))
    }

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/neue-episode").ifBlank {
            fetchUrl("$base/neu").ifBlank { fetchUrl(base) }
        }
        tagAll(parseSeriesListAniWorld(html))
    }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.isBlank()) emptyList()
        else {
            val base = activeBaseUrl()
            val slug = genre.trim().lowercase()
            val paths = listOf(
                "$base/genre/$slug",
                "$base/animes?genre=$slug",
                "$base/genre/${slug.replace('-', '_')}"
            )
            var results = emptyList<Series>()
            for (url in paths) {
                val html = fetchUrl(url)
                results = parseSeriesListAniWorld(html)
                if (results.isNotEmpty()) break
            }
            tagAll(results)
        }
    }

    /** Alphabet-Katalog bleibt paginiert über loadCatalogPage – kein voller A–Z-Scrape auf Home. */
    override suspend fun loadExtendedCatalog(): StreamingProvider.ProviderResult<List<Series>> =
        StreamingProvider.ProviderResult.Success(emptyList())

    suspend fun loadByLetter(letter: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        tagAll(loadByLetterInternal(letter))
    }

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val letters = ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() }
        val letter = letters.getOrNull(page)
        if (letter == null) emptyList() else tagAll(loadByLetterInternal(letter))
    }

    override suspend fun loadGenrePage(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> =
        if (page <= 0) loadGenre(genre) else runCatchingProvider {
            val base = activeBaseUrl()
            val slug = genre.trim().lowercase()
            tagAll(parseSeriesListAniWorld(fetchUrl("$base/genre/$slug?page=${page + 1}")))
        }

    suspend fun loadLatestEpisodes(): StreamingProvider.ProviderResult<List<com.novastream.app.data.model.LatestEpisode>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/neue-episode").ifBlank { fetchUrl("$base/neu") }
        val doc = org.jsoup.Jsoup.parse(html, parseBase())
        val results = mutableListOf<com.novastream.app.data.model.LatestEpisode>()
        for (a in doc.select("a[href*=/anime/stream/]")) {
            val href = a.absUrl("href")
            val m = Regex("""/anime/stream/([^/]+)/staffel-(\d+)/episode-(\d+)""").find(href) ?: continue
            val slug = m.groupValues[1]
            val season = m.groupValues[2].toIntOrNull() ?: 1
            val ep = m.groupValues[3].toIntOrNull() ?: 1
            val title = a.text().trim().ifBlank { slug }
            if (results.any { it.seriesSlug == slug && it.season == season && it.episode == ep }) continue
            results.add(
                com.novastream.app.data.model.LatestEpisode(
                    seriesSlug = slug,
                    seriesTitle = title.substringBefore(" S").trim(),
                    season = season,
                    episode = ep,
                    coverUrl = a.selectFirst("img")?.absUrl("src")
                )
            )
            if (results.size >= 24) break
        }
        results
    }

    private suspend fun loadByLetterInternal(letter: String): List<Series> {
        if (letter.isBlank()) return emptyList()
        val base = activeBaseUrl()
        val L = letter.trim().uppercase()
        val paths = listOf(
            "$base/animes?letter=$L",
            "$base/animes?alphabet=$L",
            "$base/animes-$L",
            "$base/anime-list?letter=$L"
        )
        for (url in paths) {
            val html = fetchUrl(url)
            val parsed = parseSeriesListAniWorld(html)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            val base = activeBaseUrl()
            val ajax = AjaxSearchClient.search(
                baseUrl = base,
                query = query.trim(),
                linkHint = "/anime/stream/",
                isAnime = true
            )
            // Harte Isolation: nur Anime-Stream-Links
            val filtered = ajax.filter {
                it.detailUrl.contains("/anime/stream/") ||
                    extractAniWorldSlug(it.detailUrl) != null
            }
            tagAll(filtered)
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/anime/stream/$slug")
        val (series, seasons) = parseAniWorldDetail(html, slug)
        tag(series) to seasons
    }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/anime/stream/$slug/staffel-$season")
        parseAniWorldEpisodes(html, slug, season)
    }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/anime/stream/${episode.slug}/staffel-${episode.season}/episode-${episode.number}")
        parseAniWorldHosters(html)
    }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatchingProvider {
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }

    private suspend fun fetchUrl(url: String): String {
        repeat(3) { attempt ->
            val body = mirror.fetch(url, webViewFallback = attempt >= 1)
            if (body.isNotBlank() && !ProviderHttp.isChallenge(body)) return body
            if (attempt < 2) kotlinx.coroutines.delay(1500L * (attempt + 1))
        }
        return ""
    }

    private fun parseAniWorldDetail(html: String, slug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(
                id = slug,
                title = slugToTitle(slug),
                detailUrl = "/anime/stream/$slug",
                providerId = id
            ) to emptyList()
        }
        val doc = Jsoup.parse(html, parseBase())

        val title = MediaUrls.sanitizeTitle(
            doc.selectFirst("div.series-title h1")?.text()
                ?: doc.selectFirst("h1")?.text()
        ).ifBlank { slugToTitle(slug) }

        var cover: String? = null
        val coverImg = doc.selectFirst("div.seriesCoverBox img[data-src]")
            ?: doc.selectFirst("div.seriesCoverBox img[src]")
            ?: doc.selectFirst("img[data-src]")
        if (coverImg != null) {
            val src = coverImg.absUrl("data-src").ifBlank { coverImg.attr("data-src") }
                .ifBlank { coverImg.absUrl("src") }.ifBlank { coverImg.attr("src") }
            cover = MediaUrls.abs(src, parseBase())
        }

        val description = doc.selectFirst("p.seri_des")?.attr("data-full-description")?.ifBlank { null }
            ?: doc.selectFirst("p.seri_des")?.text()?.trim()
            ?: doc.selectFirst(".description-text")?.text()?.trim()

        val genres = doc.select("a[href*=/genre/]")
            .mapNotNull { it.text().trim().ifBlank { null } }
            .distinct()
            .take(12)

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/anime/stream/$slug",
            description = description,
            genres = genres,
            providerId = id
        )
        return series to parseAniWorldSeasons(doc, slug)
    }

    private fun parseAniWorldSeasons(doc: org.jsoup.nodes.Document, slug: String): List<Season> {
        val seasonNumbers = mutableSetOf<Int>()
        val pattern = java.util.regex.Pattern.compile("/anime/stream/[\\w%.-]+/staffel-(\\d+)")
        for (a in doc.select("a[href*=/anime/stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = pattern.matcher(href)
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
            }
        }
        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)
        val currentEpisodes = parseAniWorldEpisodesFromDoc(doc, slug, seasonNumbers.minOrNull() ?: 1)
        return seasonNumbers.sorted().map { n ->
            Season(
                number = n,
                episodes = if (currentEpisodes.isNotEmpty() && currentEpisodes.first().season == n) {
                    currentEpisodes
                } else emptyList()
            )
        }
    }

    private fun parseAniWorldEpisodes(html: String, slug: String, season: Int): List<Episode> {
        if (html.isBlank()) return emptyList()
        return parseAniWorldEpisodesFromDoc(Jsoup.parse(html, parseBase()), slug, season)
    }

    private fun parseAniWorldEpisodesFromDoc(doc: org.jsoup.nodes.Document, slug: String, season: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<Int>()
        val epPattern = java.util.regex.Pattern.compile("/anime/stream/[\\w%.-]+/staffel-(\\d+)/episode-(\\d+)")
        for (a in doc.select("a[href*=/anime/stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = epPattern.matcher(href)
            if (!m.find()) continue
            val s = m.group(1)?.toIntOrNull() ?: continue
            val ep = m.group(2)?.toIntOrNull() ?: continue
            if (s != season || !seen.add(ep)) continue
            val title = a.text()?.trim()?.ifBlank { null }
                ?: a.attr("title")?.ifBlank { null }
                ?: "Folge $ep"
            episodes.add(
                Episode(
                    number = ep,
                    title = title,
                    slug = slug,
                    season = s,
                    episodeUrl = m.group(0) ?: ""
                )
            )
        }
        return episodes.sortedBy { it.number }
    }

    private fun parseAniWorldHosters(html: String): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        for (li in doc.select("li[data-link-target]")) {
            val redirectUrl = li.attr("data-link-target")
            if (redirectUrl.isBlank()) continue
            val icon = li.selectFirst("i.icon")
            val name = icon?.attr("title")?.replace("Hoster ", "")?.ifBlank { null }
                ?: icon?.className()?.substringAfter("icon ")?.ifBlank { null }
                ?: li.selectFirst("a")?.text()?.trim()
                ?: "Unknown"
            val langKey = li.attr("data-lang-key")
            val language = when (langKey) {
                "1" -> "Deutsch"
                "2" -> "Ger-Sub"
                "3" -> "Eng-Sub"
                "4" -> "Eng-Dub"
                "5" -> "Ger-Dub"
                "6" -> "Jap-Sub"
                else -> langKey.ifBlank { "" }
            }
            val key = "$name-$redirectUrl"
            if (seen.add(key)) {
                hosters.add(
                    HosterLink(
                        name = name,
                        redirectUrl = redirectUrl,
                        language = language,
                        linkId = li.attr("data-link-id"),
                        index = hosters.size
                    )
                )
            }
        }
        if (hosters.isEmpty()) {
            for (a in doc.select("a.watchEpisode")) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (href.isBlank()) continue
                val icon = a.selectFirst("i.icon")
                val name = icon?.attr("title")?.replace("Hoster ", "")?.ifBlank { null }
                    ?: icon?.className()?.substringAfter("icon ")?.ifBlank { null }
                    ?: "Unknown"
                if (seen.add("$name-$href")) {
                    hosters.add(HosterLink(name = name, redirectUrl = href, index = hosters.size))
                }
            }
        }
        return hosters
    }

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
