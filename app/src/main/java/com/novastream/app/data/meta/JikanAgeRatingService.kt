package com.novastream.app.data.meta

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Jikan REST API (MyAnimeList mirror) — free, no API key.
 * https://docs.api.jikan.moe/
 */
object JikanAgeRatingService {

    private const val API = "https://api.jikan.moe/v4/anime"
    private val client get() = NetworkModule.okHttpClient

    private val adultRatingTokens = listOf(
        "rx", "hentai", "r+ -", "r - 17", "r - 18", "mild nudity"
    )

    suspend fun lookup(malId: Int): String? = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext null
        val json = get("$API/$malId") ?: return@withContext null
        val data = JSONObject(json).optJSONObject("data") ?: return@withContext null
        data.optString("rating").takeIf { it.isNotBlank() && it != "null" }
    }

    fun isAdultFromRating(rating: String?): Boolean? {
        if (rating.isNullOrBlank()) return null
        val sample = rating.lowercase()
        if (adultRatingTokens.any { sample.contains(it) }) return true
        if (sample.contains("pg-13") || sample.contains("pg - children") || sample.contains("g - all")) {
            return false
        }
        return null
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
