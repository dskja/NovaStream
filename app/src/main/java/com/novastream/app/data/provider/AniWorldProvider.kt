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

/**
 * Provider für AniWorld.to.
 * AniWorld nutzt das gleiche HTML-Markup wie SerienStream, aber:
 *   - Base-URL: https://aniworld.to
 *   - Content-Pfad: /anime/stream/{slug} statt /serie/{slug}
 *   - URL-Schema: /anime/stream/{slug}/staffel-{n}/episode-{m}
 *
 * Der Scraper (NovaStreamScraper) funktioniert mit kleinen Anpassungen,
 * da die CSS-Selektoren identisch sind.
 */
class AniWorldProvider(
    override val id: String = "aniworld",
    override val displayName: String = "AniWorld",
    override val baseUrl: String = "https://aniworld.to",
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    private val hosterResolver = HosterResolver(baseUrl = baseUrl)

    // AniWorld nutzt /anime/stream/{slug} statt /serie/{slug}
    // Wir bauen ein angepasstes Retrofit-Interface
    private val api: NovaStreamApi = createApi(baseUrl)

    private fun createApi(base: String): NovaStreamApi {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(base + "/")
            .client(com.novastream.app.data.api.NetworkModule.okHttpClient)
            .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
            .build()
        return retrofit.create(NovaStreamApi::class.java)
    }

    // AniWorld-spezifischer Scraper der die gleichen Selektoren nutzt aber /anime/stream/ Pfade
    private fun parseSeriesListAniWorld(html: String): List<Series> {
        val doc = Jsoup.parse(html, baseUrl)
        val results = linkedMapOf<String, Series>()

        // AniWorld nutzt ähnliche Selektoren wie SerienStream
        // Nutze starts-with (^=) statt contains-word (~=) für href Matching
        val anchors = doc.select("a[href^=/anime/stream/]")
        for (a in anchors) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val slug = extractAniWorldSlug(href) ?: continue
            if (results.containsKey(slug)) continue

            val title = a.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
                ?: a.selectFirst("h2")?.text()?.trim()?.ifBlank { null }
                ?: a.attr("title").ifBlank { null }
                ?: a.text().trim().ifBlank { null }
                ?: slug.replace('-', ' ').replaceFirstChar { it.uppercase() }

            val cover = findCoverUrl(a)
            results[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = "/anime/stream/$slug"
            )
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

    private fun findCoverUrl(anchor: org.jsoup.nodes.Element): String? {
        val img = anchor.selectFirst("img[data-src]") ?: anchor.selectFirst("img[src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                return if (src.startsWith("http")) src else baseUrl + src
            }
        }
        // source[srcset]
        val source = anchor.selectFirst("source[srcset]")
        if (source != null) {
            val srcset = source.attr("srcset")
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstUrl.isNullOrBlank()) {
                return if (firstUrl.startsWith("http")) firstUrl else baseUrl + firstUrl
            }
        }
        return null
    }

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        // AniWorld Startseite: /animes für die Anime-Liste
        parseSeriesListAniWorld(fetchUrl("$baseUrl/animes"))
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("AniWorld Startseite konnte nicht geladen werden", it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            // AniWorld nutzt /search?term=... (nicht /suche wie SerienStream)
            // Direkter OkHttp Call da Retrofit @GET("{path}") mit Query-String problematisch sein kann
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
        NovaStreamScraper.parseSeasonEpisodes(html, slug, season)
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error("AniWorld Staffel konnte nicht geladen werden", it) }
    )

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> = runCatching {
        val html = fetchUrl("$baseUrl/anime/stream/${episode.slug}/staffel-${episode.season}/episode-${episode.number}")
        NovaStreamScraper.parseHosters(html)
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

    /** Lädt eine beliebige relative URL via Retrofit raw endpoint. */
    private suspend fun fetchCustomPath(path: String): String {
        // Nutze NovaStreamApi.raw mit angepasstem Pfad
        return api.raw(path.removePrefix("/"))
    }

    /** Lädt eine absolute URL via OkHttp (für Query-String URLs). */
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

    /** Parst AniWorld Detail-Seite (angepasst für /anime/stream/ Pfade). */
    private fun parseAniWorldDetail(html: String, slug: String): Pair<Series, List<Season>> {
        val doc = Jsoup.parse(html, baseUrl)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: slug.replace('-', ' ').replaceFirstChar { it.uppercase() }

        // Cover
        var cover: String? = null
        val img = doc.selectFirst("img[data-src]") ?: doc.selectFirst("img[src]")
        if (img != null) {
            val src = img.absUrl("data-src").ifBlank { img.attr("data-src") }
                .ifBlank { img.absUrl("src") }.ifBlank { img.attr("src") }
            if (src.isNotBlank() && !src.contains("data:image")) {
                cover = if (src.startsWith("http")) src else baseUrl + src
            }
        }
        if (cover == null) {
            val source = doc.selectFirst("source[srcset]")
            if (source != null) {
                val srcset = source.attr("srcset")
                val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                if (!firstUrl.isNullOrBlank()) {
                    cover = if (firstUrl.startsWith("http")) firstUrl else baseUrl + firstUrl
                }
            }
        }

        val description = doc.selectFirst(".description-text")?.text()?.trim()
            ?: doc.selectFirst(".series-description")?.text()?.trim()
            ?: doc.selectFirst("p.seri_des")?.text()?.trim()

        val series = Series(
            id = slug,
            title = title,
            coverUrl = cover,
            detailUrl = "/anime/stream/$slug",
            description = description
        )

        // Staffeln parsen - AniWorld nutzt data-season-pill wie SerienStream
        val seasons = parseAniWorldSeasons(doc, slug)
        return series to seasons
    }

    private fun parseAniWorldSeasons(doc: org.jsoup.nodes.Document, slug: String): List<Season> {
        val seasonNumbers = mutableSetOf<Int>()

        // data-season-pill
        doc.select("a[data-season-pill]").forEach { a ->
            a.attr("data-season-pill").toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
        }

        // Fallback: href pattern /anime/stream/{slug}/staffel-{n}
        if (seasonNumbers.isEmpty()) {
            val pattern = java.util.regex.Pattern.compile("/anime/stream/[\\w%.-]+/staffel-(\\d+)")
            doc.select("a[href^=/anime/stream/]").forEach { a ->
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val m = pattern.matcher(href)
                if (m.find()) m.group(1).toIntOrNull()?.let { if (it > 0) seasonNumbers.add(it) }
            }
        }

        if (seasonNumbers.isEmpty()) seasonNumbers.add(1)

        // Aktuelle Episoden parsen (gleiche Selektoren wie SerienStream)
        val currentEpisodes = NovaStreamScraper.parseSeasonEpisodes(
            doc.toString(), slug, seasonNumbers.minOrNull() ?: 1
        )

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
}
