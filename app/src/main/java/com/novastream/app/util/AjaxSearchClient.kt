package com.novastream.app.util

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.data.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup

/**
 * Gemeinsame AJAX-/HTML-Suche für s.to-Familie (SerienStream, AniWorld)
 * und generische Fallback-Pfade.
 */
object AjaxSearchClient {

    suspend fun search(
        baseUrl: String,
        query: String,
        linkHint: String? = null,
        isAnime: Boolean = false
    ): List<Series> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        // 1) Klassisches AJAX-JSON (AniWorld / ältere s.to-Spiegel)
        for (path in listOf("/ajax/search", "/ajax/seriesSearch")) {
            val json = postForm("$baseUrl$path", mapOf("keyword" to q))
                ?: get("$baseUrl$path?keyword=${enc(q)}")
            if (!json.isNullOrBlank() && json.trimStart().startsWith("[")) {
                val parsed = parseJsonResults(json, baseUrl, linkHint, isAnime)
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }

        // 2) HTML-Suche (serienstream.to /suche, aniworld Varianten)
        val htmlPaths = buildList {
            add("/suche?term=${enc(q)}")
            add("/suche?q=${enc(q)}")
            add("/search?q=${enc(q)}")
            add("/search?term=${enc(q)}")
            add("/?do=search&subaction=search&story=${enc(q)}")
            if (isAnime) {
                add("/animes?search=${enc(q)}")
                add("/anime/list?search=${enc(q)}")
            }
        }
        for (path in htmlPaths) {
            val html = get("$baseUrl$path") ?: continue
            if (html.isBlank() || html.contains("DDoS-Guard", ignoreCase = true)) continue
            if (html.trimStart().startsWith("[")) {
                val parsed = parseJsonResults(html, baseUrl, linkHint, isAnime)
                if (parsed.isNotEmpty()) return@withContext parsed
            }
            val parsed = parseHtmlResults(html, baseUrl, linkHint, isAnime)
            if (parsed.isNotEmpty()) return@withContext parsed
        }
        emptyList()
    }

    private fun parseJsonResults(
        json: String,
        baseUrl: String,
        linkHint: String?,
        isAnime: Boolean
    ): List<Series> {
        return try {
            val arr = JSONArray(json)
            val out = linkedMapOf<String, Series>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val link = obj.optString("link")
                    .ifBlank { obj.optString("url") }
                    .ifBlank { obj.optString("href") }
                val title = MediaUrls.sanitizeTitle(
                    obj.optString("title").ifBlank { obj.optString("name") }
                )
                if (link.isBlank() && title.isBlank()) continue
                val slug = extractSlug(link, isAnime) ?: continue
                if (linkHint != null && link.isNotBlank() && !link.contains(linkHint)) continue
                val cover = MediaUrls.abs(
                    obj.optString("cover").ifBlank { obj.optString("image") }
                        .ifBlank { obj.optString("poster") },
                    baseUrl
                )
                val detail = when {
                    link.startsWith("http") -> link
                    link.startsWith("/") -> link
                    isAnime -> "/anime/stream/$slug"
                    else -> "/serie/$slug"
                }
                out.putIfAbsent(
                    slug,
                    Series(
                        id = slug,
                        title = title.ifBlank { slug.replace('-', ' ') },
                        coverUrl = cover,
                        detailUrl = detail,
                        description = obj.optString("description").takeIf { it.isNotBlank() }
                    )
                )
            }
            out.values.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseHtmlResults(
        html: String,
        baseUrl: String,
        linkHint: String?,
        isAnime: Boolean
    ): List<Series> {
        val doc = Jsoup.parse(html, baseUrl)
        val out = linkedMapOf<String, Series>()
        val selector = when {
            isAnime -> "a[href*=/anime/stream/]"
            linkHint != null -> "a[href*=$linkHint]"
            else -> "a[href*=/serie/], a[href*=/stream/], a[href*=/movie/], a[href*=/film/]"
        }
        for (a in doc.select(selector)) {
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (href.contains("/staffel-") || href.contains("/episode-") || href.contains("/season")) continue
            val slug = extractSlug(href, isAnime) ?: continue
            if (out.containsKey(slug)) continue
            val title = MediaUrls.sanitizeTitle(
                a.selectFirst("h3, h2, .title, .name")?.text()
                    ?: a.attr("title").ifBlank { null }
                    ?: a.text()
            )
            if (title.length < 2) continue
            val img = a.selectFirst("img[data-src], img[src]")
            val cover = img?.let {
                MediaUrls.abs(
                    it.absUrl("data-src").ifBlank { it.attr("data-src") }
                        .ifBlank { it.absUrl("src") }.ifBlank { it.attr("src") },
                    baseUrl
                )
            }
            out[slug] = Series(
                id = slug,
                title = title,
                coverUrl = cover,
                detailUrl = if (href.startsWith("http") || href.startsWith("/")) {
                    href.substringBefore("?").removePrefix(baseUrl)
                } else if (isAnime) "/anime/stream/$slug" else "/serie/$slug"
            )
        }
        return out.values.toList()
    }

    internal fun extractSlugForTest(url: String, isAnime: Boolean): String? = extractSlug(url, isAnime)

    private fun extractSlug(url: String, isAnime: Boolean): String? {
        val patterns = if (isAnime) {
            listOf(
                Regex("""/anime/stream/([\w%.-]+?)(?:/|$)""", RegexOption.IGNORE_CASE),
                Regex("""/anime/([\w%.-]+?)(?:/|$)""", RegexOption.IGNORE_CASE)
            )
        } else {
            listOf(
                Regex("""/serie/([\w%.-]+?)(?:/|$)""", RegexOption.IGNORE_CASE),
                Regex("""/series/([\w%.-]+?)(?:/|$)""", RegexOption.IGNORE_CASE),
                Regex("""/stream/([\w%.-]+?)(?:\.html|/|$)""", RegexOption.IGNORE_CASE),
                Regex("""/movie/([\w%.-]+?)(?:/|$)""", RegexOption.IGNORE_CASE),
                Regex("""/film/([\w%.-]+?)(?:/|$)""", RegexOption.IGNORE_CASE),
                Regex("""/title/([\w%.-]+?)(?:/|$)""", RegexOption.IGNORE_CASE)
            )
        }
        for (p in patterns) {
            val m = p.find(url) ?: continue
            val slug = m.groupValues.getOrNull(1) ?: continue
            return try {
                java.net.URLDecoder.decode(slug, "UTF-8")
            } catch (_: Exception) {
                slug
            }
        }
        return null
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Accept", "application/json, text/html, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun postForm(url: String, fields: Map<String, String>): String? {
        return try {
            val body = FormBody.Builder().apply {
                fields.forEach { (k, v) -> add(k, v) }
            }.build()
            val req = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Accept", "application/json, text/html, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .build()
            NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}
