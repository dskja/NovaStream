package com.novastream.app.data.meta

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kostenlose Metadata-API ohne Key – TVMaze (https://www.tvmaze.com/api).
 * Deckt Suche, Katalog, Episoden, Cast, IMDb-IDs für Embed-Player.
 */
object FreeMetaService {

    private const val BASE = "https://api.tvmaze.com"
    private val client get() = NetworkModule.okHttpClient

    suspend fun search(query: String, limit: Int = 40): List<MetaShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("$BASE/search/shows?q=$q") ?: return@withContext emptyList()
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until minOf(arr.length(), limit)) {
                val show = arr.getJSONObject(i).optJSONObject("show") ?: continue
                add(parseShow(show))
            }
        }
    }

    suspend fun singlesearch(query: String): MetaShow? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("$BASE/singlesearch/shows?q=$q") ?: return@withContext null
        parseShow(JSONObject(json))
    }

    suspend fun show(id: String): MetaShow? = withContext(Dispatchers.IO) {
        val json = get("$BASE/shows/$id?embed=cast") ?: return@withContext null
        parseShow(JSONObject(json), withCast = true)
    }

    suspend fun showByImdb(imdbId: String): MetaShow? = withContext(Dispatchers.IO) {
        val id = imdbId.trim()
        if (id.isBlank()) return@withContext null
        val json = get("$BASE/lookup/shows?imdb=$id") ?: return@withContext null
        parseShow(JSONObject(json))
    }

    suspend fun episodes(showId: String): List<MetaEpisode> = withContext(Dispatchers.IO) {
        val json = get("$BASE/shows/$showId/episodes") ?: return@withContext emptyList()
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                add(parseEpisode(arr.getJSONObject(i)))
            }
        }
    }

    suspend fun seasonsWithEpisodes(showId: String): List<MetaSeason> {
        val eps = episodes(showId)
        return eps.groupBy { it.season }
            .toSortedMap()
            .map { (num, list) -> MetaSeason(num, list.sortedBy { it.number }) }
    }

    /** Katalog-Seite (0-basiert). TVMaze liefert ~250 Shows pro Seite. */
    suspend fun catalogPage(page: Int = 0): List<MetaShow> = withContext(Dispatchers.IO) {
        val json = get("$BASE/shows?page=${page.coerceAtLeast(0)}") ?: return@withContext emptyList()
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) add(parseShow(arr.getJSONObject(i)))
        }
    }

    /** Aktuelle Schedule (heute). */
    suspend fun schedule(country: String = "US"): List<MetaShow> = withContext(Dispatchers.IO) {
        val json = get("$BASE/schedule?country=$country") ?: return@withContext emptyList()
        val arr = JSONArray(json)
        val seen = linkedMapOf<String, MetaShow>()
        for (i in 0 until arr.length()) {
            val show = arr.getJSONObject(i).optJSONObject("show") ?: continue
            val parsed = parseShow(show)
            seen.putIfAbsent(parsed.id, parsed)
        }
        seen.values.toList()
    }

    suspend fun enrichByTitle(title: String): MetaShow? {
        if (title.isBlank()) return null
        return try {
            singlesearch(title) ?: search(title, 1).firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShow(obj: JSONObject, withCast: Boolean = false): MetaShow {
        val image = obj.optJSONObject("image")
        val externals = obj.optJSONObject("externals")
        val rating = obj.optJSONObject("rating")?.optDouble("average")?.takeIf { !it.isNaN() }
        val network = obj.optJSONObject("network")?.optString("name")
            ?: obj.optJSONObject("webChannel")?.optString("name")
        val genres = buildList {
            val g = obj.optJSONArray("genres") ?: return@buildList
            for (i in 0 until g.length()) add(g.getString(i))
        }
        val cast = if (withCast) parseCast(obj.optJSONObject("_embedded")) else emptyList()
        return MetaShow(
            id = obj.optInt("id").toString(),
            title = obj.optString("name"),
            summary = stripHtml(obj.optString("summary").takeIf { it.isNotBlank() && it != "null" }),
            genres = genres,
            status = obj.optString("status").takeIf { it.isNotBlank() && it != "null" },
            premiered = obj.optString("premiered").takeIf { it.isNotBlank() && it != "null" },
            rating = rating,
            posterUrl = image?.optString("original")?.ifBlank { null }
                ?: image?.optString("medium")?.ifBlank { null },
            backdropUrl = image?.optString("original"),
            imdbId = externals?.optString("imdb")?.takeIf { it.isNotBlank() && it != "null" },
            thetvdbId = externals?.opt("thetvdb")?.toString()?.takeIf { it != "null" && it != "0" },
            network = network?.takeIf { it.isNotBlank() && it != "null" },
            runtime = obj.optInt("runtime").takeIf { it > 0 },
            language = obj.optString("language").takeIf { it.isNotBlank() && it != "null" },
            officialSite = obj.optString("officialSite").takeIf { it.isNotBlank() && it != "null" },
            cast = cast
        )
    }

    private fun parseCast(embedded: JSONObject?): List<MetaPerson> {
        if (embedded == null) return emptyList()
        val arr = embedded.optJSONArray("cast") ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(arr.length(), 12)) {
                val item = arr.getJSONObject(i)
                val person = item.optJSONObject("person") ?: continue
                val character = item.optJSONObject("character")
                val img = person.optJSONObject("image")?.optString("medium")
                add(
                    MetaPerson(
                        name = person.optString("name"),
                        character = character?.optString("name"),
                        imageUrl = img?.takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    private fun parseEpisode(obj: JSONObject): MetaEpisode {
        val image = obj.optJSONObject("image")
        return MetaEpisode(
            id = obj.optInt("id").toString(),
            season = obj.optInt("season").coerceAtLeast(1),
            number = obj.optInt("number").coerceAtLeast(1),
            title = obj.optString("name").ifBlank { "Episode ${obj.optInt("number")}" },
            summary = stripHtml(obj.optString("summary").takeIf { it.isNotBlank() && it != "null" }),
            airdate = obj.optString("airdate").takeIf { it.isNotBlank() && it != "null" },
            runtime = obj.optInt("runtime").takeIf { it > 0 },
            imageUrl = image?.optString("original")?.ifBlank { null }
                ?: image?.optString("medium")?.ifBlank { null }
        )
    }

    private fun stripHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { null }
    }

    private fun get(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}
