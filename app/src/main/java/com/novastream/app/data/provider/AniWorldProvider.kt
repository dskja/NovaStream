package com.novastream.app.data.provider

import com.novastream.app.data.api.NovaStreamApi
import com.novastream.app.data.api.NovaStreamScraper
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.util.HosterResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Provider für AniWorld.to.
 * AniWorld nutzt ein komplett anderes HTML-Markup als SerienStream:
 *
 * Homepage:  div.seriesListContainer a[href^=/anime/stream/] (Beliebt)
 *            div.homeContentPromotionBoxPicture (Promotion Boxes)
 * Detail:    div.seriesCoverBox img[data-src] (Cover)
 *            div.series-title h1 (Titel)
 *            p.seri_des (Beschreibung)
 *            a[href^=/anime/stream/{slug}/staffel-] (Staffeln)
 * Episoden:  Staffel-Links und Episoden-Links unter /anime/stream/ Pfad
 * Hoster:    li[data-link-target] mit i.icon Klassen fuer Hoster-Namen
 */
class AniWorldProvider(
    override val id: String = "aniworld",
    override val displayName: String = "AniWorld",
    override val baseUrl: String = "https://aniworld.to",
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    private val hosterResolver = HosterResolver(baseUrl = baseUrl)

    private val api: NovaStreamApi = createApi(baseUrl)

    private fun createApi(base: String): NovaStreamApi {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(base + "/")
            .client(com.novastream.app.data.api.NetworkModule.okHttpClient)
            .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
            .build()
        return retrofit.create(NovaStreamApi::class.java)
    }

    // ─── Homepage Parsing ───────────────────────────────────────────────────

    private fun parseSeriesListAniWorld(html: String): List<Series> {
        val doc = Jsoup.parse(html, baseUrl)
        val results = linkedMapOf<String, Series>()

        // Phase 1: seriesListContainer (Beliebt bei AniWorld) - hat Cover + h3 Titel
        for (a in doc.select("div.seriesListContainer a[href^=/anime/stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractAniWorldSlug(href) ?: continue
            if (results.containsKey(slug)) continue

            val title = a.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
                ?: a.attr("title")?.substringBefore(" stream online")?.ifBlank { null }
                ?: slugToTitle(slug)

            val cover = findAniWorldCover(a)
            results[slug] = Series(id = slug, title = title, coverUrl = cover, detailUrl = "/anime/stream/$slug")
        }

        // Phase 2: homeContentPromotionBoxPicture (Promo Boxes auf Homepage)
        for (a in doc.select("a[href^=/anime/stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractAniWorldSlug(href) ?: continue
            if (results.containsKey(slug)) continue
            // Skip season/episode links
            if (href.contains("/staffel-")) continue

            val title = a.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
                ?: a.selectFirst("h2")?.text()?.trim()?.ifBlank { null }
                ?: a.attr("title")?.substringBefore(" stream online")?.ifBlank { null }
                ?: a.text().trim().ifBlank { slugToTitle(slug) }

            val cover = findAniWorldCover(a)
            results[slug] = Series(id = slug, title = title, coverUrl = cover, detailUrl = "/anime/stream/$slug")
        }

        return results.values.toList()
    }

    private fun extractAniWorldSlug(url: String): String? {
        val pattern = java.util.regex.Pattern.compile("/anime/stream/([\\w%.-]+?)(?:/|$)")
        val m = pattern.matcher(url)
        if (!m.find()) return null
        val slug = m.group(1)
        return try { java.net.URLDecoder.decode(slug, "UTF-8") } catch (_: Exception) { slug }
    }

    private fun findAniWorldCover(anchor: Element): String? {
        // img[data-src] (AniWorld nutzt Lazy Loading)
        val img = anchor.selectFirst("img[data-src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else baseUrl + src
            }
        }
        // img[src]
        val imgSrc = anchor.selectFirst("img[src]")
        if (imgSrc != null) {
            val src = imgSrc.absUrl("src").ifBlank { imgSrc.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else baseUrl + src
            }
        }
        return null
    }

    // ─── Provider Interface ─────────────────────────────────────────────────

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        parseSeriesListAniWorld(fetchUrl(baseUrl))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("AniWorld Startseite konnte nicht geladen werden", it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val html = fetchUrl("$baseUrl/search?term=$encoded")
            parseSeriesListAniWorld(html)
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error("AniWorld Suche fehlgeschlagen", it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> = runCatching {
        val html = fetchUrl("$baseUrl/anime/stream/$slug")
        parseAniWorldDetail(html, slug)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("AniWorld Details konnten nicht geladen werden", it) }
    )

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> = runCatching {
        val html = fetchUrl("$baseUrl/anime/stream/$slug/staffel-$season")
        parseAniWorldEpisodes(html, slug, season)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("AniWorld Staffel konnte nicht geladen werden", it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        val html = fetchUrl("$baseUrl/anime/stream/${episode.slug}/staffel-${episode.season}/episode-${episode.number}")
        parseAniWorldHosters(html)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("AniWorld Hoster konnten nicht geladen werden", it) }
    )

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> = runCatching {
        hosterResolver.resolve(hoster.name, hoster.redirectUrl)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("Stream-URL konnte nicht aufgelöst werden", it) }
    )

    // ─── HTML Parsing ───────────────────────────────────────────────────────

    /** Lädt eine absolute URL via OkHttp. */
    private suspend fun fetchUrl(url: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
                .header("Referer", baseUrl + "/")
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()
            com.novastream.app.data.api.NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: ""
                else ""
            }
        }
    }

    /** Parst AniWorld Detail-Seite. */
    private fun parseAniWorldDetail(html: String, slug: String): Pair<Series, List<Season>> {
        val doc = Jsoup.parse(html, baseUrl)

        // Titel: div.series-title h1
        val title = doc.selectFirst("div.series-title h1")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: slugToTitle(slug)

        // Cover: div.seriesCoverBox img[data-src]
        var cover: String? = null
        val coverImg = doc.selectFirst("div.seriesCoverBox img[data-src]")
            ?: doc.selectFirst("div.seriesCoverBox img[src]")
            ?: doc.selectFirst("img[data-src]")
        if (coverImg != null) {
            val src = coverImg.absUrl("data-src").ifBlank { coverImg.attr("data-src") }
                .ifBlank { coverImg.absUrl("src") }.ifBlank { coverImg.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                cover = if (src.startsWith("http")) src else baseUrl + src
            }
        }

        // Beschreibung: p.seri_des
        val description = doc.selectFirst("p.seri_des")?.attr("data-full-description")?.ifBlank { null }
            ?: doc.selectFirst("p.seri_des")?.text()?.trim()
            ?: doc.selectFirst(".description-text")?.text()?.trim()

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/anime/stream/$slug",
            description = description
        )

        val seasons = parseAniWorldSeasons(doc, slug)
        return series to seasons
    }

    /** Parst Staffeln aus der AniWorld Detail-Seite. */
    private fun parseAniWorldSeasons(doc: org.jsoup.nodes.Document, slug: String): List<Season> {
        val seasonNumbers = mutableSetOf<Int>()

        // Staffel-Links: a[href*=/staffel-] im Staffel-Navigationsbereich
        val pattern = java.util.regex.Pattern.compile("/anime/stream/[\\w%.-]+/staffel-(\\d+)")
        for (a in doc.select("a[href^=/anime/stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = pattern.matcher(href)
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
            }
        }

        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)

        // Episoden der ersten Staffel parsen (die auf der Detail-Seite angezeigt wird)
        // Verwende das bereits geparste Document - kein double parsing
        val currentEpisodes = parseAniWorldEpisodesFromDoc(doc, slug, seasonNumbers.minOrNull() ?: 1)

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

    /** Parst Episoden aus einer AniWorld Staffel-Seite. */
    private fun parseAniWorldEpisodes(html: String, slug: String, season: Int): List<Episode> {
        val doc = Jsoup.parse(html, baseUrl)
        return parseAniWorldEpisodesFromDoc(doc, slug, season)
    }

    /** Parst Episoden aus einem bereits geparsten Jsoup Document (verhindert double parsing). */
    private fun parseAniWorldEpisodesFromDoc(doc: org.jsoup.nodes.Document, slug: String, season: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<Int>()

        // Episoden-Links: a[href~=/anime/stream/.*/staffel-{n}/episode-{m}]
        val epPattern = java.util.regex.Pattern.compile("/anime/stream/[\\w%.-]+/staffel-(\\d+)/episode-(\\d+)")
        for (a in doc.select("a[href^=/anime/stream/]")) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val m = epPattern.matcher(href)
            if (m.find()) {
                val s = m.group(1)?.toIntOrNull() ?: continue
                val ep = m.group(2)?.toIntOrNull() ?: continue
                if (s != season) continue
                if (seen.add(ep)) {
                    val title = a.text()?.trim()?.ifBlank { null }
                        ?: a.attr("title")?.ifBlank { null }
                        ?: "Folge $ep"
                    episodes.add(Episode(
                        number = ep,
                        title = title,
                        slug = slug,
                        season = s,
                        episodeUrl = m.group(0) ?: ""
                    ))
                }
            }
        }

        return episodes.sortedBy { it.number }
    }

    /**
     * Parst Hoster aus einer AniWorld Episoden-Seite.
     * AniWorld nutzt li[data-link-target="/redirect/{id}"] mit i.icon.{HosterName}
     */
    private fun parseAniWorldHosters(html: String): List<HosterLink> {
        val doc = Jsoup.parse(html, baseUrl)
        val hosters = mutableListOf<HosterLink>()
        val seen = mutableSetOf<String>()

        // Hoster-Links: li[data-link-target]
        for (li in doc.select("li[data-link-target]")) {
            val redirectUrl = li.attr("data-link-target")
            if (redirectUrl.isBlank()) continue

            // Hoster-Name aus i.icon.{Name}
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
                else -> ""
            }

            val key = "$name-$redirectUrl"
            if (seen.add(key)) {
                hosters.add(HosterLink(
                    name = name,
                    redirectUrl = redirectUrl,
                    language = language,
                    linkId = li.attr("data-link-id"),
                    index = hosters.size
                ))
            }
        }

        // Fallback: a.watchEpisode mit i.icon
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
