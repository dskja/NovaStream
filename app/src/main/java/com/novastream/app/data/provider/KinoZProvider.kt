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
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject

/**
 * Provider für KinoZ.to (Kinox-Klon; Mirror auch unter kinos.to).
 *
 * URL-Schema:
 *   /                          – Startseite
 *   /Search.html?q=…           – Suche
 *   /Stream/{Slug}.html        – Detail (z.B. /Stream/Reacher.html)
 *   /Genre/{Name}              – Genre-Liste (z.B. /Genre/Action)
 *   /aGET/Mirror/{rel}         – Mirror-JSON → iframe mit /redirect/…
 *
 * Staffeln/Episoden: select#SeasonSelection / select#EpisodeSelection
 * Hoster: li[id^=Hoster_] mit rel="Title&Hoster=…&Season=…&Episode=…"
 */
class KinoZProvider(
    override val id: String = "kinoz",
    override val displayName: String = "KinoZ",
    override val baseUrl: String = "https://kinoz.to",
    override val supportsSeries: Boolean = true,
    private val appContext: Context? = null
) : StreamingProvider {

    private val streamPathRegex = Regex("""/Stream/([^/]+?)\.html""", RegexOption.IGNORE_CASE)

    // LRU-Cache für Detail-HTML (Staffel + Hoster nutzen dieselbe Seite)
    private val detailCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_CACHE_SIZE
    }
    private val cacheLock = Any()

    private val mirror = MirrorSupport(id, baseUrl, appContext, "/Stream/") {
        synchronized(cacheLock) { detailCache.clear() }
    }

    private val hosterResolver get() = HosterResolver(baseUrl = mirror.parseBase())

    private suspend fun activeBaseUrl(): String = mirror.activeBase()

    private fun parseBase(): String = mirror.parseBase()

    private suspend fun fetchUrl(url: String): String = mirror.fetch(url)

    override val supportsMovies: Boolean = true
    override val catalogHint: String? = ProviderCatalogHints.forId(id)
    override val availableGenres: List<com.novastream.app.data.model.Genre>
        get() = ProviderGenres.forId(id)

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        parseKinoZSeriesList(fetchUrl(activeBaseUrl()))
    }

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        guardSearchQuery(query)?.let { return it }
        return runCatchingProvider {
            val base = activeBaseUrl()
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            parseKinoZSeriesList(fetchUrl("$base/Search.html?q=$encoded"))
        }
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatchingProvider {
        val html = fetchDetailPage(slug)
        parseKinoZDetail(html, normalizeSlug(slug))
    }

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatchingProvider {
        val html = fetchDetailPage(slug)
        val (_, seasons) = parseKinoZDetail(html, normalizeSlug(slug))
        seasons.find { it.number == season }?.episodes ?: emptyList()
    }

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatchingProvider {
        val slug = episode.slug.ifBlank { normalizeSlug(episode.episodeUrl) }
        val html = fetchDetailPage(slug)
        resolveMirrorHosters(html, episode.season, episode.number)
    }

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatchingProvider {
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }

    /** Lädt Filme (getrennt vom Serien-Home-Katalog). */
    override suspend fun loadMovies(): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        parseKinoZSeriesList(fetchUrl("$base/Genre/Filme"))
            .ifEmpty { parseKinoZSeriesList(fetchUrl(base)).filter { it.isMovie } }
            .map { it.copy(isMovie = true, providerId = id) }
    }

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val name = genre.trim()
        if (name.isBlank()) emptyList()
        else {
            val base = activeBaseUrl()
            val paths = ProviderGenrePaths.pathsFor(id, name, "/Genre/{genre}")
            var results = emptyList<Series>()
            for (path in paths) {
                results = parseKinoZSeriesList(fetchUrl("$base$path"))
                if (results.isNotEmpty()) break
            }
            results
        }
    }

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = loadHome()

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = loadHome()

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val base = activeBaseUrl()
        val path = when {
            page <= 0 -> ""
            else -> "?page=${page + 1}"
        }
        parseKinoZSeriesList(fetchUrl(base + path))
    }

    override suspend fun loadGenrePage(genre: String, page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatchingProvider {
        val name = genre.trim()
        if (name.isBlank()) emptyList()
        else {
            val base = activeBaseUrl()
            val paths = ProviderGenrePaths.pathsForPage(id, name, page)
            var results = emptyList<Series>()
            for (path in paths) {
                results = parseKinoZSeriesList(fetchUrl("$base$path"))
                if (results.isNotEmpty()) break
            }
            results
        }
    }

    // ─── Networking ─────────────────────────────────────────────────────────

    private suspend fun fetchDetailPage(slug: String): String {
        val key = normalizeSlug(slug)
        synchronized(cacheLock) {
            detailCache[key]?.let { return it }
        }
        val base = activeBaseUrl()
        val html = fetchUrl("$base/Stream/$key.html")
        if (html.isNotBlank()) {
            synchronized(cacheLock) {
                if (detailCache[key] == null) detailCache[key] = html
            }
        }
        return html
    }

    // ─── Parsing ────────────────────────────────────────────────────────────

    private fun parseKinoZSeriesList(html: String): List<Series> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val results = linkedMapOf<String, Series>()

        for (a in doc.select("a[href*=/Stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractStreamSlug(href) ?: continue
            if (results.containsKey(slug)) continue

            val title = a.attr("title").ifBlank { null }
                ?: a.selectFirst("strong, h2, h3, span")?.text()?.trim()?.ifBlank { null }
                ?: a.selectFirst("img")?.attr("alt")?.ifBlank { null }
                ?: a.text().trim().ifBlank { null }
                ?: slugToTitle(slug)

            val cover = findCoverNear(a)
            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = "/Stream/$slug.html"
            )
        }

        return results.values.toList()
    }

    private fun parseKinoZDetail(html: String, slug: String): Pair<Series, List<Season>> {
        if (html.isBlank()) {
            return Series(
                id = slug,
                title = slugToTitle(slug),
                coverUrl = null,
                detailUrl = "/Stream/$slug.html"
            ) to emptyList()
        }
        val doc = Jsoup.parse(html, parseBase())

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("#Content h2, .Relative h1, .Relative h2")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("|")?.trim()
            ?: slugToTitle(slug)

        val cover = findCoverFromDoc(doc)

        val description = doc.selectFirst(".plot, #plot, .Description, .descript")?.text()?.trim()
            ?: doc.select("p").firstOrNull { it.text().length > 40 }?.text()?.trim()

        val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)
            ?: doc.selectFirst(".Year, span.year")?.text()?.trim()

        val seasons = buildSeasonsFromSelects(doc, slug)

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/Stream/$slug.html",
            description = description,
            year = year,
            seasonCount = seasons.size.takeIf { it > 0 },
            isMovie = seasons.size <= 1 && seasons.firstOrNull()?.episodes?.size == 1
        )

        return series to seasons
    }

    private fun buildSeasonsFromSelects(doc: Document, slug: String): List<Season> {
        val seasonOptions = doc.select("select#SeasonSelection option")
        val episodeOptions = doc.select("select#EpisodeSelection option")

        val seasonNumbers = seasonOptions.mapNotNull { it.attr("value").toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .ifEmpty {
                // Manche Kinox-Seiten speichern Staffeln in option-Text / value= Season,1
                seasonOptions.mapNotNull { opt ->
                    Regex("""(\d+)""").find(opt.attr("value").ifBlank { opt.text() })
                        ?.groupValues?.get(1)?.toIntOrNull()
                }.filter { it > 0 }.distinct().sorted()
            }

        val episodeNumbers = episodeOptions.mapNotNull { it.attr("value").toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .ifEmpty {
                episodeOptions.mapNotNull { opt ->
                    Regex("""(\d+)""").find(opt.attr("value").ifBlank { opt.text() })
                        ?.groupValues?.get(1)?.toIntOrNull()
                }.filter { it > 0 }.distinct().sorted()
            }

        // Hoster-rel enthält oft Season/Episode-Paare → zusätzliche Abdeckung
        val fromHosters = mutableMapOf<Int, MutableSet<Int>>()
        for (li in doc.select("li[id^=Hoster_]")) {
            val rel = org.jsoup.parser.Parser.unescapeEntities(li.attr("rel"), false)
            val s = Regex("""Season=(\d+)""", RegexOption.IGNORE_CASE).find(rel)?.groupValues?.get(1)?.toIntOrNull()
            val e = Regex("""Episode=(\d+)""", RegexOption.IGNORE_CASE).find(rel)?.groupValues?.get(1)?.toIntOrNull()
            if (s != null && e != null && s > 0 && e > 0) {
                fromHosters.getOrPut(s) { mutableSetOf() }.add(e)
            }
        }

        val allSeasons = (seasonNumbers + fromHosters.keys).distinct().sorted().ifEmpty { listOf(1) }

        return allSeasons.map { seasonNum ->
            val epsFromSelect = if (seasonNum == (seasonNumbers.minOrNull() ?: 1) || seasonNumbers.size <= 1) {
                episodeNumbers
            } else {
                emptyList()
            }
            val eps = (epsFromSelect + (fromHosters[seasonNum] ?: emptySet()))
                .distinct()
                .sorted()
                .ifEmpty { listOf(1) }

            Season(
                number = seasonNum,
                episodes = eps.map { epNum ->
                    Episode(
                        number = epNum,
                        title = "Folge $epNum",
                        slug = slug,
                        season = seasonNum,
                        episodeUrl = "/Stream/$slug.html"
                    )
                }
            )
        }
    }

    /**
     * Liest MirBtn / Hoster_-Einträge und lädt /aGET/Mirror/{rel},
     * um die absolute /redirect/…-URL als redirectUrl zu setzen.
     */
    private suspend fun resolveMirrorHosters(html: String, season: Int, episode: Int): List<HosterLink> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, parseBase())
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        val hosterLis = doc.select("li[id^=Hoster_], li.MirBtn, .MirBtn")
        for (li in hosterLis) {
            val relRaw = li.attr("rel").ifBlank {
                li.selectFirst("[rel]")?.attr("rel").orEmpty()
            }
            if (relRaw.isBlank()) continue

            val rel = org.jsoup.parser.Parser.unescapeEntities(relRaw, false)

            // Optional: auf Season/Episode filtern, wenn im rel vorhanden
            val relSeason = Regex("""Season=(\d+)""", RegexOption.IGNORE_CASE).find(rel)?.groupValues?.get(1)?.toIntOrNull()
            val relEpisode = Regex("""Episode=(\d+)""", RegexOption.IGNORE_CASE).find(rel)?.groupValues?.get(1)?.toIntOrNull()
            if (relSeason != null && relSeason != season) continue
            if (relEpisode != null && relEpisode != episode) continue

            val fallbackName = extractHosterNameFromLi(li)
            val mirrorPath = "/aGET/Mirror/" + rel.trim()
            val mirrorUrl = makeAbsolute(mirrorPath)

            val mirror = fetchMirror(mirrorUrl) ?: continue
            val redirectUrl = mirror.redirectUrl
            val name = mirror.hosterName?.let { normalizeHosterDisplayName(it) }
                ?: fallbackName.takeIf { it.isNotBlank() && it != "Unknown" }
                ?: extractHosterNameFromUrl(redirectUrl)

            if (!seen.add("$name-$redirectUrl")) continue
            hosters.add(
                HosterLink(
                    name = name,
                    redirectUrl = redirectUrl,
                    index = hosters.size,
                    linkId = li.id()
                )
            )
        }

        return hosters
    }

    private data class MirrorResult(val redirectUrl: String, val hosterName: String?)

    private suspend fun fetchMirror(mirrorUrl: String): MirrorResult? {
        val body = fetchUrl(mirrorUrl)
        if (body.isBlank()) return null

        // JSON: {"Stream":"<iframe src=\"/redirect/...\" ...>","HosterName":"Vidara.to",...}
        return try {
            val json = JSONObject(body)
            val streamHtml = json.optString("Stream", "")
            val iframeSrc = when {
                streamHtml.isNotBlank() -> extractIframeSrc(streamHtml)
                else -> null
            } ?: extractIframeSrc(body) ?: return null
            val hosterName = json.optString("HosterName", "").ifBlank { null }
            MirrorResult(makeAbsolute(iframeSrc), hosterName)
        } catch (_: Exception) {
            val iframeSrc = extractIframeSrc(body) ?: return null
            MirrorResult(makeAbsolute(iframeSrc), null)
        }
    }

    private fun extractIframeSrc(htmlOrFragment: String): String? {
        val unescaped = htmlOrFragment
            .replace("\\\"", "\"")
            .replace("\\/", "/")
        val fromAttr = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(unescaped)?.groupValues?.get(1)
        if (!fromAttr.isNullOrBlank()) return fromAttr

        // Manchmal nur der Redirect-Pfad
        val redirect = Regex("""(/redirect/[^"'\\\s]+)""", RegexOption.IGNORE_CASE)
            .find(unescaped)?.groupValues?.get(1)
        return redirect
    }

    private fun extractHosterNameFromLi(li: Element): String {
        val text = li.text().trim()
        // "Vidara.to Mirror : 1/1" → Vidara
        val beforeMirror = text.substringBefore("Mirror", text).trim()
            .substringBefore(":")
            .trim()
        if (beforeMirror.isNotBlank()) {
            return normalizeHosterDisplayName(beforeMirror)
        }
        return "Unknown"
    }

    private fun normalizeHosterDisplayName(raw: String): String {
        val cleaned = raw.replace(Regex("""\.(to|sx|cc|com|net|tv|io)$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return when {
            cleaned.contains("voe", ignoreCase = true) -> "VOE"
            cleaned.contains("vidara", ignoreCase = true) -> "Vidara"
            cleaned.contains("streamtape", ignoreCase = true) -> "Streamtape"
            cleaned.contains("vidoza", ignoreCase = true) -> "Vidoza"
            cleaned.contains("dood", ignoreCase = true) -> "Doodstream"
            cleaned.contains("filemoon", ignoreCase = true) -> "Filemoon"
            else -> cleaned.replaceFirstChar { it.uppercase() }.ifBlank { "Unknown" }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun normalizeSlug(slug: String): String {
        val fromPath = extractStreamSlug(slug)
        if (fromPath != null) return fromPath
        return slug.removeSuffix(".html").substringAfterLast('/').trim()
    }

    private fun extractStreamSlug(url: String): String? {
        val m = streamPathRegex.find(url) ?: return null
        val slug = m.groupValues[1]
        return try {
            java.net.URLDecoder.decode(slug, "UTF-8")
        } catch (_: Exception) {
            slug
        }
    }

    private fun slugToTitle(slug: String): String =
        slug.replace('_', ' ')
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
        if (src.isBlank() || src.contains("data:image") || src.contains("spacer") || src.contains("pixel")) {
            return null
        }
        return makeAbsolute(src)
    }

    private fun findCoverNear(element: Element): String? {
        val img = element.selectFirst("img[src], img[data-src]")
            ?: element.parent()?.selectFirst("img")
        return img?.let { absImg(it) }
    }

    private fun findCoverFromDoc(doc: Document): String? {
        doc.selectFirst("#Content img, .Relative img, .Grahics img, img.cover")?.let { absImg(it) }?.let { return it }
        doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }?.let {
            return makeAbsolute(it)
        }
        // Erstes „großes“ Bild
        for (img in doc.select("img[src], img[data-src]")) {
            val src = absImg(img) ?: continue
            val w = img.attr("width").toIntOrNull() ?: 0
            val h = img.attr("height").toIntOrNull() ?: 0
            if (w >= 100 || h >= 100 || src.contains("/cover") || src.contains("/Poster")) {
                return src
            }
        }
        return doc.selectFirst("img[src], img[data-src]")?.let { absImg(it) }
    }

    private fun extractHosterNameFromUrl(url: String): String {
        val host = try {
            java.net.URI(makeAbsolute(url)).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
        return when {
            host.contains("voe") -> "VOE"
            host.contains("vidara") -> "Vidara"
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

    fun clearCache() {
        synchronized(cacheLock) { detailCache.clear() }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 20
    }
}
