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
 * Kitsu JSON:API — free, no API key for public reads.
 * https://kitsu.docs.apiary.io/
 */
object KitsuMetaService {

    private const val BASE = "https://kitsu.io/api/edge"
    private val client get() = NetworkModule.okHttpClient

    suspend fun search(query: String, limit: Int = 10): List<MetaShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("$BASE/anime?filter[text]=$q&page[limit]=${limit.coerceIn(1, 20)}&include=categories")
            ?: return@withContext emptyList()
        parseAnimeList(JSONObject(json))
    }

    suspend fun animeById(id: Int): MetaShow? = withContext(Dispatchers.IO) {
        if (id <= 0) return@withContext null
        val json = get("$BASE/anime/$id?include=categories") ?: return@withContext null
        val root = JSONObject(json)
        val genres = parseIncludedCategoriesMap(root.optJSONArray("included"))
        root.optJSONObject("data")?.let { parseAnime(it, genres) }
    }

    fun isAdultFromAgeRating(ageRating: String?): Boolean? {
        if (ageRating.isNullOrBlank()) return null
        return when (ageRating.uppercase()) {
            "R18", "R18+", "RX" -> true
            "G", "PG" -> false
            else -> null
        }
    }

    fun parseAnimeList(root: JSONObject): List<MetaShow> {
        val data = root.optJSONArray("data") ?: return emptyList()
        val genreMap = parseIncludedCategoriesMap(root.optJSONArray("included"))
        return buildList {
            for (i in 0 until data.length()) {
                parseAnime(data.getJSONObject(i), genreMap)?.let { add(it) }
            }
        }
    }

    fun parseIncludedCategories(included: JSONArray?): List<String> =
        parseIncludedCategoriesMap(included).values.distinct()

    private fun parseIncludedCategoriesMap(included: JSONArray?): Map<String, String> {
        if (included == null) return emptyMap()
        return buildMap {
            for (i in 0 until included.length()) {
                val item = included.getJSONObject(i)
                if (item.optString("type") != "categories") continue
                val id = item.optString("id")
                val name = item.optJSONObject("attributes")?.optString("title")
                if (id.isNotBlank() && !name.isNullOrBlank()) put(id, name)
            }
        }
    }

    fun parseAnime(node: JSONObject, genreMap: Map<String, String> = emptyMap()): MetaShow? {
        val id = node.optString("id").toIntOrNull() ?: return null
        val attrs = node.optJSONObject("attributes") ?: return null
        val title = attrs.optString("canonicalTitle").takeIf { it.isNotBlank() && it != "null" }
            ?: attrs.optJSONObject("titles")?.optString("en")
            ?: return null
        val poster = attrs.optJSONObject("posterImage")?.optString("large")
            ?: attrs.optJSONObject("posterImage")?.optString("medium")
        val cover = attrs.optJSONObject("coverImage")?.optString("large")
            ?: attrs.optJSONObject("coverImage")?.optString("original")
        val rating = attrs.optString("averageRating").toDoubleOrNull()?.div(10.0)
        val ageRating = attrs.optString("ageRating").takeIf { it.isNotBlank() && it != "null" }
        val isAdult = isAdultFromAgeRating(ageRating)
        val startDate = attrs.optString("startDate").takeIf { it.isNotBlank() && it != "null" }
        val relData = node.optJSONObject("relationships")
            ?.optJSONObject("categories")
            ?.optJSONArray("data")
        val genres = buildList {
            if (relData != null) {
                for (i in 0 until relData.length()) {
                    val catId = relData.getJSONObject(i).optString("id")
                    genreMap[catId]?.let { add(it) }
                }
            }
        }
        return MetaShow(
            id = "kitsu-$id",
            title = title,
            summary = attrs.optString("synopsis").takeIf { it.isNotBlank() && it != "null" },
            genres = genres,
            status = attrs.optString("status").takeIf { it.isNotBlank() && it != "null" },
            premiered = startDate,
            rating = rating,
            posterUrl = poster,
            backdropUrl = cover,
            seasonCount = attrs.optInt("episodeCount", -1).takeIf { it > 0 },
            kitsuId = id,
            mediaType = "anime",
            isAdult = isAdult,
            contentRating = ageRating,
            contentRatingSource = if (ageRating != null) "kitsu" else null
        )
    }

    private fun get(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Accept", "application/vnd.api+json")
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
