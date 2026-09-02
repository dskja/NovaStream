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
 * Epguides REST API — free, no API key.
 * TV episode lists, air dates, summaries and stills.
 * https://epguides-api.readthedocs.io/
 */
object EpguidesMetaService {

    private const val BASE = "https://epguides.frecar.no"
    private val client get() = NetworkModule.okHttpClient

    suspend fun search(query: String, limit: Int = 8): List<MetaShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("$BASE/shows/search?query=$q") ?: return@withContext emptyList()
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until minOf(arr.length(), limit)) {
                parseShow(arr.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    suspend fun show(key: String): MetaShow? = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext null
        val encoded = URLEncoder.encode(key, "UTF-8")
        val json = get("$BASE/shows/$encoded") ?: return@withContext null
        parseShow(JSONObject(json))
    }

    suspend fun episodes(showKey: String, season: Int? = null): List<MetaEpisode> = withContext(Dispatchers.IO) {
        if (showKey.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(showKey, "UTF-8")
        val url = if (season != null && season > 0) {
            "$BASE/shows/$encoded/seasons/$season/episodes"
        } else {
            "$BASE/shows/$encoded/episodes"
        }
        val json = get(url) ?: return@withContext emptyList()
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                parseEpisode(arr.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    suspend fun seasonsWithEpisodes(showKey: String): List<MetaSeason> {
        val eps = episodes(showKey)
        return eps.groupBy { it.season }
            .toSortedMap()
            .map { (num, list) -> MetaSeason(num, list.sortedBy { it.number }) }
    }

    fun parseShow(obj: JSONObject): MetaShow? {
        val key = obj.optString("epguides_key").takeIf { it.isNotBlank() }
            ?: obj.optString("slug").takeIf { it.isNotBlank() }
            ?: return null
        val title = obj.optString("title").takeIf { it.isNotBlank() } ?: key
        return MetaShow(
            id = "epguides-$key",
            title = title,
            network = obj.optString("network").takeIf { it.isNotBlank() && it != "null" },
            premiered = obj.optString("start_date").takeIf { it.isNotBlank() && it != "null" },
            posterUrl = obj.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            mediaType = "tv",
            epguidesKey = key
        )
    }

    fun parseEpisode(obj: JSONObject): MetaEpisode? {
        val season = obj.optInt("season", obj.optInt("season_number", 1))
        val number = obj.optInt("episode_number", obj.optInt("number", -1))
        if (number <= 0) return null
        val title = obj.optString("title").takeIf { it.isNotBlank() } ?: "Episode $number"
        return MetaEpisode(
            id = "epguides-${season}x$number",
            season = season,
            number = number,
            title = title,
            summary = obj.optString("summary").takeIf { it.isNotBlank() && it != "null" },
            airdate = obj.optString("release_date").takeIf { it.isNotBlank() && it != "null" },
            runtime = obj.optInt("run_time_min", -1).takeIf { it > 0 },
            imageUrl = obj.optString("poster_url").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    /** Converts a show title to likely epguides key format (PascalCase, no spaces). */
    fun guessKeyFromTitle(title: String): String =
        title.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString("") { word ->
                word.replace(Regex("[^a-zA-Z0-9]"), "")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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
