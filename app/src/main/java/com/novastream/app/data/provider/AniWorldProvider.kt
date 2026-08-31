package com.novastream.app.data.provider

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.Genre
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.AjaxSearchClient
import com.novastream.app.util.HosterResolver
import com.novastream.app.util.MediaUrls
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Provider für AniWorld.to – strikt vom SerienStream-Katalog getrennt.
 * Nutzt /anime/stream/* Pfade, AJAX-Suche und Alphabet-Katalog.
 */
class AniWorldProvider(
    override val id: String = "aniworld",
    override val displayName: String = "AniWorld",
    override val baseUrl: String = "https://aniworld.to",
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    override val supportsMovies: Boolean = false

    override val catalogHint: String = "Tausende Animes"

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

    private val hosterResolver = HosterResolver(baseUrl = baseUrl)

    private fun tag(series: Series): Series = series.copy(
        providerId = id,
        title = MediaUrls.sanitizeTitle(series.title).ifBlank { series.title },
        coverUrl = MediaUrls.abs(series.coverUrl, baseUrl),
        backdropUrl = MediaUrls.abs(series.backdropUrl, baseUrl),
        detailUrl = series.detailUrl.ifBlank { "/anime/stream/${series.id}" }
    )

    private fun tagAll(list: List<Series>): List<Series> = list.map { tag(it) }

    // ─── Homepage Parsing ───────────────────────────────────────────────────

    private fun parseSeriesListAniWorld(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
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
        return MediaUrls.abs(src, baseUrl)
    }

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val home = parseSeriesListAniWorld(fetchUrl(baseUrl))
        val popular = parseSeriesListAniWorld(fetchUrl("$baseUrl/beliebte-animes").ifBlank {
            fetchUrl("$baseUrl/animes")
        })
        tagAll((home + popular).distinctBy { it.id })
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val html = fetchUrl("$baseUrl/beliebte-animes").ifBlank { fetchUrl(baseUrl) }
        tagAll(parseSeriesListAniWorld(html))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val html = fetchUrl("$baseUrl/neue-episode").ifBlank {
            fetchUrl("$baseUrl/neu").ifBlank { fetchUrl(baseUrl) }
        }
        tagAll(parseSeriesListAniWorld(html))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        if (genre.isBlank()) return@runCatching emptyList()
        val slug = genre.trim().lowercase()
        val paths = listOf(
            "$baseUrl/genre/$slug",
            "$baseUrl/animes?genre=$slug",
            "$baseUrl/genre/${slug.replace('-', '_')}"
        )
        var results = emptyList<Series>()
        for (url in paths) {
            val html = fetchUrl(url)
            results = parseSeriesListAniWorld(html)
            if (results.isNotEmpty()) break
        }
        tagAll(results)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    /** Alphabet-Katalog – liefert hunderte Einträge statt nur Homepage (~200). */
    override suspend fun loadExtendedCatalog(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        coroutineScope {
            // Bewusst begrenzt: volle A–Z wäre zu langsam für Home; reicht für „tausende“-Gefühl
            val letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "M", "N", "R", "S", "T")
            val all = linkedMapOf<String, Series>()
            letters.chunked(4).forEach { chunk ->
                chunk.map { letter ->
                    async { loadByLetterInternal(letter) }
                }.awaitAll().forEach { list ->
                    list.forEach { s -> all.putIfAbsent(s.id, s) }
                }
            }
            tagAll(all.values.toList())
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    suspend fun loadByLetter(letter: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        tagAll(loadByLetterInternal(letter))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    private suspend fun loadByLetterInternal(letter: String): List<Series> {
        if (letter.isBlank()) return emptyList()
        val L = letter.trim().uppercase()
        val paths = listOf(
            "$baseUrl/animes?letter=$L",
            "$baseUrl/animes?alphabet=$L",
            "$baseUrl/animes-$L",
            "$baseUrl/anime-list?letter=$L"
        )
        for (url in paths) {
            val html = fetchUrl(url)
            val parsed = parseSeriesListAniWorld(html)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            val ajax = AjaxSearchClient.search(
                baseUrl = baseUrl,
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
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        val html = fetchUrl("$baseUrl/anime/stream/$slug")
        val (series, seasons) = parseAniWorldDetail(html, slug)
        tag(series) to seasons
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        val html = fetchUrl("$baseUrl/anime/stream/$slug/staffel-$season")
        parseAniWorldEpisodes(html, slug, season)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        val html = fetchUrl("$baseUrl/anime/stream/${episode.slug}/staffel-${episode.season}/episode-${episode.number}")
        parseAniWorldHosters(html)
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

    private suspend fun fetchUrl(url: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
                .header("Referer", baseUrl + "/")
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()
            com.novastream.app.data.api.NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
        }
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
        val doc = Jsoup.parse(html, baseUrl)

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
            cover = MediaUrls.abs(src, baseUrl)
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
        return parseAniWorldEpisodesFromDoc(Jsoup.parse(html, baseUrl), slug, season)
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
        val doc = Jsoup.parse(html, baseUrl)
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
