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
 * Shikimori REST API (MyAnimeList mirror) — free, no API key.
 * Requires User-Agent header per API rules.
 * https://shikimori.one/api/doc
 */
object ShikimoriMetaService {

    private const val BASE = "https://shikimori.one/api"
    private val client get() = NetworkModule.okHttpClient

    suspend fun search(query: String, limit: Int = 10): List<MetaShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("$BASE/animes?search=$q&limit=${limit.coerceIn(1, 25)}") ?: return@withContext emptyList()
        parseAnimeArray(JSONArray(json))
    }

    suspend fun animeById(id: Int): MetaShow? = withContext(Dispatchers.IO) {
        if (id <= 0) return@withContext null
        val json = get("$BASE/animes/$id") ?: return@withContext null
        parseAnime(JSONObject(json))
    }

    fun parseAnimeArray(arr: JSONArray): List<MetaShow> = buildList {
        for (i in 0 until arr.length()) {
            parseAnime(arr.getJSONObject(i))?.let { add(it) }
        }
    }

    fun parseAnime(obj: JSONObject): MetaShow? {
        val id = obj.optInt("id", -1)
        if (id <= 0) return null
        val title = obj.optString("name").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("russian").takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        val image = obj.optJSONObject("image")
        val poster = image?.optString("original")?.let { if (it.startsWith("http")) it else "https://shikimori.one$it" }
        val score = obj.optString("score").toDoubleOrNull()
        val aired = obj.optJSONObject("aired_on")
        val year = aired?.optString("date")?.takeIf { it.isNotBlank() }?.take(4)
        val episodes = obj.optInt("episodes", -1).takeIf { it > 0 }
        val kind = obj.optString("kind")
        return MetaShow(
            id = "shikimori-$id",
            title = title,
            premiered = year?.let { "$it-01-01" },
            rating = score,
            posterUrl = poster,
            seasonCount = episodes,
            idMal = id,
            shikimoriId = id,
            mediaType = if (kind == "movie") "movie" else "anime",
            status = obj.optString("status").takeIf { it.isNotBlank() && it != "null" }
        )
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
