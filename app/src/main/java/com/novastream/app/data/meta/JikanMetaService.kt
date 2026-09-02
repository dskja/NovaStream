package com.novastream.app.data.meta

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Jikan REST API (MyAnimeList mirror) — free, no API key.
 * Full anime metadata + age ratings.
 * https://docs.api.jikan.moe/
 */
object JikanMetaService {

    private const val BASE = "https://api.jikan.moe/v4"
    private val client get() = NetworkModule.okHttpClient

    suspend fun search(query: String, limit: Int = 10): List<MetaShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("$BASE/anime?q=$q&limit=${limit.coerceIn(1, 25)}") ?: return@withContext emptyList()
        val data = JSONObject(json).optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until data.length()) {
                parseAnime(data.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    suspend fun animeById(malId: Int): MetaShow? = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext null
        val json = get("$BASE/anime/$malId/full") ?: get("$BASE/anime/$malId") ?: return@withContext null
        val data = JSONObject(json).optJSONObject("data") ?: return@withContext null
        parseAnime(data, withGenres = true)
    }

    suspend fun lookupRating(malId: Int): String? = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext null
        val json = get("$BASE/anime/$malId") ?: return@withContext null
        JSONObject(json).optJSONObject("data")
            ?.optString("rating")
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    suspend fun trailerForAnime(malId: Int): String? = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext null
        val json = get("$BASE/anime/$malId/full") ?: get("$BASE/anime/$malId") ?: return@withContext null
        val data = JSONObject(json).optJSONObject("data") ?: return@withContext null
        TrailerMetaService.parseJikanTrailer(data.optJSONObject("trailer"))
    }

    fun isAdultFromRating(rating: String?): Boolean? {
        if (rating.isNullOrBlank()) return null
        val sample = rating.lowercase()
        val adultTokens = listOf("rx", "hentai", "r+ -", "r - 17", "r - 18", "mild nudity")
        if (adultTokens.any { sample.contains(it) }) return true
        if (sample.contains("pg-13") || sample.contains("pg - children") || sample.contains("g - all")) {
            return false
        }
        return null
    }

    fun parseAnime(obj: JSONObject, withGenres: Boolean = false): MetaShow? {
        val malId = obj.optInt("mal_id", obj.optInt("malId", -1))
        if (malId <= 0) return null
        val title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("title_english").takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        val images = obj.optJSONObject("images")?.optJSONObject("jpg")
        val poster = images?.optString("large_image_url")
            ?: images?.optString("image_url")
        val banner = obj.optJSONObject("images")?.optJSONObject("webp")?.optString("large_image_url")
        val score = obj.optDouble("score", -1.0).takeIf { it > 0 }
        val aired = obj.optJSONObject("aired")
        val year = aired?.optString("from")?.take(4)
            ?: obj.optJSONObject("year")?.toString()
        val genres = if (withGenres) parseGenres(obj.optJSONArray("genres")) else emptyList()
        val rating = obj.optString("rating").takeIf { it.isNotBlank() && it != "null" }
        val isAdult = isAdultFromRating(rating)
        val episodes = obj.optInt("episodes", -1).takeIf { it > 0 }
        val runtime = obj.optInt("duration", -1).takeIf { it > 0 }
        val trailerUrl = TrailerMetaService.parseJikanTrailer(obj.optJSONObject("trailer"))
        return MetaShow(
            id = "mal-$malId",
            title = title,
            summary = obj.optString("synopsis").takeIf { it.isNotBlank() && it != "null" },
            genres = genres,
            status = obj.optString("status").takeIf { it.isNotBlank() && it != "null" },
            premiered = year?.let { "$it-01-01" },
            rating = score,
            posterUrl = poster,
            backdropUrl = banner,
            runtime = runtime,
            trailerUrl = trailerUrl,
            seasonCount = episodes,
            idMal = malId,
            mediaType = if (obj.optString("type").equals("movie", true)) "movie" else "anime",
            isAdult = isAdult,
            contentRating = rating,
            contentRatingSource = if (rating != null) "jikan" else null
        )
    }

    private fun parseGenres(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val name = arr.getJSONObject(i).optString("name")
                if (name.isNotBlank()) add(name)
            }
        }
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

    /** Episode list for an anime (MAL id). Episodes are mapped to season 1. */
    suspend fun episodes(malId: Int, maxPages: Int = 8): List<MetaEpisode> = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext emptyList()
        val result = mutableListOf<MetaEpisode>()
        var page = 1
        repeat(maxPages.coerceIn(1, 10)) {
            val json = get("$BASE/anime/$malId/episodes?page=$page") ?: return@repeat
            val root = JSONObject(json)
            val data = root.optJSONArray("data") ?: return@repeat
            val pagination = root.optJSONObject("pagination")
            val perPage = (pagination?.optInt("items", data.length()) ?: data.length()).coerceAtLeast(1)
            val pageOffset = (page - 1) * perPage
            for (i in 0 until data.length()) {
                parseEpisode(data.getJSONObject(i), pageOffset + i + 1)?.let { result.add(it) }
            }
            val hasNext = pagination?.optBoolean("has_next_page", false) == true
            if (!hasNext || data.length() == 0) return@withContext result
            page++
        }
        result
    }

    fun parseEpisode(obj: JSONObject, number: Int): MetaEpisode? {
        val title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val aired = obj.optString("aired").takeIf { it.isNotBlank() && it != "null" }
        val malEpId = obj.optInt("mal_id", -1)
        return MetaEpisode(
            id = if (malEpId > 0) "mal-ep-$malEpId" else "mal-ep-$number",
            season = 1,
            number = number,
            title = title,
            airdate = aired
        )
    }
}
