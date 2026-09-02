package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Provider für FilmPalast.to.
 *
 * URL-Schema:
 *   /                          – Startseite
 *   /serien/view               – Serien-Übersicht
 *   /movies/new                – Neue Filme
 *   /stream/{slug}             – Detail / Episode (z.B. reacher-s01e01)
 *   POST /search               – Suche (Form: headerSearchText)
 *
 * Serien-Episoden nutzen Slug-Muster `titel-s01e01`; Filme sind plain Slugs.
 * Hoster: firestream.to, voe.sx, vidaraa.cc, playmate.to, …
 */
class FilmPalastProvider(
    override val id: String = "filmpalast",
    override val displayName: String = "FilmPalast",
    override val baseUrl: String = "https://filmpalast.to",
    override val supportsSeries: Boolean = true,
    private val appContext: Context? = null
) : StreamingProvider {

    private val mirror = MirrorSupport(id, baseUrl, appContext, "/stream/")

    private val hosterResolver get() = HosterResolver(baseUrl = mirror.parseBase())

    private suspend fun activeBaseUrl(): String = mirror.activeBase()

    private fun parseBase(): String = mirror.parseBase()

    private suspend fun fetchUrl(url: String): String = mirror.fetch(url)

    override val supportsMovies: Boolean = true
    override val catalogHint: String? = ProviderCatalogHints.forId(id)
    override val availableGenres: List<com.novastream.app.data.model.Genre>
        get() = ProviderGenres.forId(id)

    private val episodeSlugRegex = Regex("""-s(\d+)e(\d+)$""", RegexOption.IGNORE_CASE)
    private val streamSlugRegex = Regex("""/stream/([\w%.-]+)""", RegexOption.IGNORE_CASE)

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val home = fetchUrl(base)
        val serien = fetchUrl("$base/serien/view")
        val moviesHtml = fetchUrl("$base/movies/new").ifBlank { fetchUrl("$base/movies") }
        val merged = linkedMapOf<String, Series>()
        for (s in parseFilmPalastList(home) + parseFilmPalastList(serien)) {
            if (!merged.containsKey(s.id)) merged[s.id] = s.copy(providerId = id)
        }
        for (s in parseFilmPalastList(moviesHtml)) {
            if (!merged.containsKey(s.id)) merged[s.id] = s.copy(isMovie = true, providerId = id)
        }
        merged.values.toList()
    }

    override suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val html = fetchUrl("$base/movies/new").ifBlank { fetchUrl("$base/movies") }
        parseFilmPalastList(html).map { it.copy(isMovie = true, providerId = id) }
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            val html = searchFilmPalast(query.trim())
            parseFilmPalastList(html)
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatchingProvider {
        val base = activeBaseUrl()
        val episodePageSlug = resolveEpisodePageSlug(slug)
        val html = fetchUrl("$base/stream/$episodePageSlug")
        parseFilmPalastDetail(html, seriesBaseSlug(slug))
    }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatchingProvider {
        val base = activeBaseUrl()
        val episodePageSlug = resolveEpisodePageSlug(slug)
        val html = fetchUrl("$base/stream/$episodePageSlug")
        val (_, seasons) = parseFilmPalastDetail(html, seriesBaseSlug(slug))
        seasons.find { it.number == season }?.episodes ?: emptyList()
    }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatchingProvider {
        val base = activeBaseUrl()
        val url = when {
            episode.episodeUrl.startsWith("http") -> episode.episodeUrl
            episode.episodeUrl.startsWith("/") -> base + episode.episodeUrl
            episode.episodeUrl.isNotBlank() -> "$base/stream/${episode.episodeUrl}"
            else -> {
                val epSlug = "${episode.slug}-s${episode.season.toString().padStart(2, '0')}e${episode.number.toString().padStart(2, '0')}"
                "$base/stream/$epSlug"
            }
        }
        val html = fetchUrl(url)
        parseFilmPalastHosters(html)
    }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatchingProvider {
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        if (genre.trim().isBlank()) emptyList()
        else {
            val base = activeBaseUrl()
            val paths = ProviderGenrePaths.pathsFor(id, genre.trim())
            var results = emptyList<Series>()
            for (path in paths) {
                results = parseFilmPalastList(fetchUrl(base + path))
                if (results.isNotEmpty()) break
            }
            results
        }
    }

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        parseFilmPalastList(fetchUrl("$base/serien/view"))
    }

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        parseFilmPalastList(fetchUrl(base))
    }

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val serienPath = if (page <= 0) "/serien/view" else "/serien/view?page=${page + 1}"
        val moviesPath = if (page <= 0) "/movies/new" else "/movies/new?page=${page + 1}"
        val merged = linkedMapOf<String, Series>()
        for (s in parseFilmPalastList(fetchUrl(base + serienPath))) {
            merged[s.id] = s.copy(providerId = id)
        }
        for (s in parseFilmPalastList(fetchUrl(base + moviesPath))) {
            if (!merged.containsKey(s.id)) merged[s.id] = s.copy(isMovie = true, providerId = id)
        }
        merged.values.toList()
    }

    override suspend fun loadGenrePage(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val basePath = when (genre.trim().lowercase()) {
            "filme", "movies", "movie", "neu", "new" -> "/movies/new"
            else -> "/serien/view"
        }
        val path = if (page <= 0) basePath else "$basePath?page=${page + 1}"
        parseFilmPalastList(fetchUrl(base + path))
    }

    // ─── Networking ─────────────────────────────────────────────────────────

    private suspend fun searchFilmPalast(query: String): String = withContext(Dispatchers.IO) {
        val base = activeBaseUrl()
        val body = FormBody.Builder()
            .add("headerSearchText", query)
            .build()
        val req = Request.Builder()
            .url("$base/search")
            .post(body)
            .header("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
            .header("Referer", "$base/")
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()
        NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
            val html = if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            if (html.isNotBlank()) return@withContext html
        }
        // Fallback: GET
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val getReq = Request.Builder()
            .url("$base/search?headerSearchText=$encoded")
            .header("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
            .header("Referer", "$base/")
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .build()
        NetworkModule.okHttpClient.newCall(getReq).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
    }

    // ─── Parsing ────────────────────────────────────────────────────────────

    /** Parst Katalog-Listen und kollabiert Episode-Slugs zu Serien-IDs. */
    private fun parseFilmPalastList(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val results = linkedMapOf<String, Series>()

        for (a in doc.select("a[href*=/stream/], a[href*=stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val rawSlug = extractStreamSlug(href) ?: continue
            val seriesId = seriesBaseSlug(rawSlug)
            if (results.containsKey(seriesId)) continue

            val rawTitle = a.attr("title").ifBlank { null }
                ?: a.selectFirst("img")?.attr("alt")?.ifBlank { null }
                ?: a.selectFirst("h2, h3, span, .title")?.text()?.trim()?.ifBlank { null }
                ?: a.text().trim().ifBlank { null }
                ?: slugToTitle(rawSlug)

            val title = cleanEpisodeTitle(rawTitle)
            val cover = findCoverNear(a)

            results[seriesId] = Series(
                id = seriesId,
                title = title,
                coverUrl = cover,
                detailUrl = "/stream/$seriesId",
                isMovie = !episodeSlugRegex.containsMatchIn(rawSlug) && seriesId == rawSlug
            )
        }

        return results.values.toList()
    }

    private fun parseFilmPalastDetail(html: String, seriesSlug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(
                id = seriesSlug,
                title = slugToTitle(seriesSlug),
                coverUrl = null,
                detailUrl = "/stream/$seriesSlug"
            ) to emptyList()
        }
        val doc = Jsoup.parse(html, parseBase())

        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.trim()
            ?: slugToTitle(seriesSlug)
        val title = cleanEpisodeTitle(rawTitle)

        val cover = doc.selectFirst("img.cover2")?.let { absImg(it) }
            ?: findCoverFromDoc(doc)

        val description = doc.selectFirst(".description, .plot, .info, #description")?.text()?.trim()
            ?: doc.selectFirst("p")?.text()?.trim()

        val year = Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)

        val allEpisodes = parseGetStaffelStreamEpisodes(doc, seriesSlug)

        val series = Series(
            id = seriesSlug,
            title = title,
            coverUrl = cover,
            detailUrl = "/stream/$seriesSlug",
            description = description,
            year = year,
            seasonCount = allEpisodes.map { it.season }.distinct().size.takeIf { it > 0 },
            isMovie = allEpisodes.isEmpty() || (allEpisodes.size == 1 && allEpisodes.first().season == 1 && allEpisodes.first().number == 1 && !episodeSlugRegex.containsMatchIn(seriesSlug))
        )

        val seasons = if (allEpisodes.isNotEmpty()) {
            allEpisodes.groupBy { it.season }
                .toSortedMap()
                .map { (num, eps) -> Season(number = num, episodes = eps.sortedBy { it.number }) }
        } else {
            // Film / einzelne Stream-Seite ohne Staffel-Links
            listOf(
                Season(
                    number = 1,
                    episodes = listOf(
                        Episode(
                            number = 1,
                            title = title,
                            slug = seriesSlug,
                            season = 1,
                            episodeUrl = "/stream/$seriesSlug"
                        )
                    )
                )
            )
        }

        return series to seasons
    }

    private fun parseGetStaffelStreamEpisodes(doc: Document, seriesSlug: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()

        for (a in doc.select("a.getStaffelStream")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val epSlug = extractStreamSlug(href) ?: continue
            val match = episodeSlugRegex.find(epSlug) ?: continue
            val season = match.groupValues[1].toIntOrNull() ?: continue
            val number = match.groupValues[2].toIntOrNull() ?: continue
            val key = "$season-$number"
            if (!seen.add(key)) continue

            val epTitle = a.text().trim().ifBlank { "S${season}E$number" }
            episodes.add(
                Episode(
                    number = number,
                    title = epTitle,
                    slug = seriesSlug,
                    season = season,
                    episodeUrl = "/stream/$epSlug"
                )
            )
        }

        return episodes.sortedWith(compareBy({ it.season }, { it.number }))
    }

    private fun parseFilmPalastHosters(html: String): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        for (li in doc.select("li.streamPlayBtn")) {
            val a = li.selectFirst("a.button.rb.iconPlay")
                ?: li.selectFirst("a.iconPlay")
                ?: li.selectFirst("a[href], a[data-player-url]")
                ?: continue

            val raw = a.attr("data-player-url").ifBlank { a.absUrl("href") }
                .ifBlank { a.attr("href") }
            if (raw.isBlank() || raw == "#" || raw.startsWith("javascript:", ignoreCase = true)) continue

            val redirectUrl = makeAbsolute(raw)
            val name = extractHosterNameFromUrl(redirectUrl)
            if (!seen.add("$name-$redirectUrl")) continue

            hosters.add(
                HosterLink(
                    name = name,
                    redirectUrl = redirectUrl,
                    index = hosters.size
                )
            )
        }

        // Fallback: direkte Play-Links ohne li-Wrapper
        if (hosters.isEmpty()) {
            for (a in doc.select("a.button.rb.iconPlay, a.iconPlay")) {
                val raw = a.attr("data-player-url").ifBlank { a.absUrl("href") }
                    .ifBlank { a.attr("href") }
                if (raw.isBlank()) continue
                val redirectUrl = makeAbsolute(raw)
                val name = extractHosterNameFromUrl(redirectUrl)
                if (!seen.add("$name-$redirectUrl")) continue
                hosters.add(HosterLink(name = name, redirectUrl = redirectUrl, index = hosters.size))
            }
        }

        return hosters
    }

    // ─── Slug / Title Helpers ───────────────────────────────────────────────

    /**
     * Für Serien-Detail ohne sXXeYY: bevorzugt `{slug}-s01e01`, sonst erste gefundene Episode.
     */
    private suspend fun resolveEpisodePageSlug(slug: String): String {
        if (episodeSlugRegex.containsMatchIn(slug)) return slug
        val base = activeBaseUrl()

        val s01e01 = "$slug-s01e01"
        val probe = fetchUrl("$base/stream/$s01e01")
        if (probe.isNotBlank() && looksLikeStreamPage(probe)) return s01e01

        val direct = fetchUrl("$base/stream/$slug")
        if (direct.isNotBlank()) {
            val doc = Jsoup.parse(direct, parseBase())
            val firstEp = doc.selectFirst("a.getStaffelStream")
            if (firstEp != null) {
                extractStreamSlug(firstEp.absUrl("href").ifBlank { firstEp.attr("href") })?.let { return it }
            }
            if (looksLikeStreamPage(direct)) return slug
        }

        // Suche nach Titel und erste Episode nehmen
        val searchHtml = searchFilmPalast(slugToTitle(slug))
        val searchDoc = Jsoup.parse(searchHtml, parseBase())
        for (a in searchDoc.select("a[href*=/stream/]")) {
            val found = extractStreamSlug(a.absUrl("href").ifBlank { a.attr("href") }) ?: continue
            if (seriesBaseSlug(found).equals(slug, ignoreCase = true)) {
                return if (episodeSlugRegex.containsMatchIn(found)) found else "$found-s01e01"
            }
        }

        return s01e01
    }

    private fun looksLikeStreamPage(html: String): Boolean =
        html.contains("streamPlayBtn") || html.contains("getStaffelStream") || html.contains("cover2")

    private fun seriesBaseSlug(slug: String): String =
        episodeSlugRegex.replace(slug, "")

    private fun extractStreamSlug(url: String): String? {
        val normalized = url.replace("https:", "").replace("http:", "")
        val m = streamSlugRegex.find(normalized) ?: return null
        val slug = m.groupValues[1].trimEnd('/')
        return try {
            java.net.URLDecoder.decode(slug, "UTF-8")
        } catch (_: Exception) {
            slug
        }
    }

    private fun cleanEpisodeTitle(title: String): String {
        val cleaned = title
            .replace(Regex("""\s*S\d+\s*E\d+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Staffel\s*\d+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*$"""), "")
            .trim()
        return cleaned.ifBlank { title.trim() }
    }

    private fun slugToTitle(slug: String): String =
        seriesBaseSlug(slug)
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { c -> c.uppercase() } }

    private fun makeAbsolute(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> parseBase() + url
        else -> "${parseBase()}/$url"
    }

    private fun absImg(img: Element): String? {
        val src = img.absUrl("src").ifBlank { img.attr("src") }
            .ifBlank { img.absUrl("data-src") }.ifBlank { img.attr("data-src") }
        if (src.isBlank() || src.contains("data:image")) return null
        return makeAbsolute(src)
    }

    private fun findCoverNear(element: Element): String? {
        val img = element.selectFirst("img.cover2")
            ?: element.selectFirst("img[data-src]")
            ?: element.selectFirst("img[src]")
            ?: element.parent()?.selectFirst("img")
        return img?.let { absImg(it) }
    }

    private fun findCoverFromDoc(doc: Document): String? {
        doc.selectFirst("img.cover2")?.let { absImg(it) }?.let { return it }
        doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }?.let {
            return makeAbsolute(it)
        }
        for (img in doc.select("img[src], img[data-src]")) {
            absImg(img)?.let { return it }
        }
        return null
    }

    private fun extractHosterNameFromUrl(url: String): String {
        val host = try {
            java.net.URI(makeAbsolute(url)).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
        return when {
            host.contains("voe") -> "VOE"
            host.contains("firestream") -> "FireStream"
            host.contains("vidara") -> "Vidara"
            host.contains("playmate") -> "Playmate"
            host.contains("streamtape") -> "Streamtape"
            host.contains("vidoza") -> "Vidoza"
            host.contains("dood") -> "Doodstream"
            host.contains("filemoon") -> "Filemoon"
            host.isNotBlank() -> {
                val part = host.removePrefix("www.").substringBefore(".")
                part.replaceFirstChar { it.uppercase() }
            }
            else -> "Unknown"
        }
    }
}
