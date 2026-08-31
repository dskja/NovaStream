package com.novastream.app.util

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Löst Streams über öffentliche Embed-Frontends auf (FMHY-üblich).
 * Arbeitet mit IMDb-IDs (aus TVMaze) – kein TMDb-API-Key nötig.
 *
 * Quellen:
 *  - vidsrc.me  (/vs_src.php → embed URL)
 *  - 2embed.cc
 *  - vidlink.pro (wenn TMDb-ID bekannt)
 *  - player.vidlove.cc
 */
object EmbedStreamResolver {

    private val client get() = NetworkModule.okHttpClient
    private val hosterResolver by lazy { HosterResolver() }

    suspend fun resolveByImdb(
        imdbId: String,
        season: Int = 1,
        episode: Int = 1,
        isMovie: Boolean = false
    ): List<StreamSource> {
        val id = imdbId.trim()
        if (id.isBlank()) return emptyList()
        val hosters = buildHosters(id, season, episode, isMovie, tmdbId = null)
        return resolveHosters(hosters)
    }

    suspend fun resolveByTmdb(
        tmdbId: String,
        season: Int = 1,
        episode: Int = 1,
        isMovie: Boolean = false
    ): List<StreamSource> {
        val id = tmdbId.trim()
        if (id.isBlank()) return emptyList()
        val hosters = buildHosters(imdbId = null, season, episode, isMovie, tmdbId = id)
        return resolveHosters(hosters)
    }

    fun buildHosters(
        imdbId: String?,
        season: Int,
        episode: Int,
        isMovie: Boolean,
        tmdbId: String?
    ): List<HosterLink> {
        val list = mutableListOf<HosterLink>()
        if (!imdbId.isNullOrBlank()) {
            if (isMovie) {
                list += HosterLink("VidSrc", "https://vidsrc.me/embed/movie?imdb=$imdbId", index = list.size)
                list += HosterLink("2Embed", "https://www.2embed.cc/embed/$imdbId", index = list.size)
            } else {
                list += HosterLink(
                    "VidSrc",
                    "https://vidsrc.me/embed/tv?imdb=$imdbId&season=$season&episode=$episode",
                    index = list.size
                )
                list += HosterLink(
                    "2Embed",
                    "https://www.2embed.cc/embedtv/$imdbId&s=$season&e=$episode",
                    index = list.size
                )
            }
        }
        if (!tmdbId.isNullOrBlank()) {
            if (isMovie) {
                list += HosterLink("VidLink", "https://vidlink.pro/movie/$tmdbId", index = list.size)
                list += HosterLink("VidLove", "https://player.vidlove.cc/embed/movie/$tmdbId", index = list.size)
            } else {
                list += HosterLink(
                    "VidLink",
                    "https://vidlink.pro/tv/$tmdbId/$season/$episode",
                    index = list.size
                )
                list += HosterLink(
                    "VidLove",
                    "https://player.vidlove.cc/embed/tv/$tmdbId/$season/$episode",
                    index = list.size
                )
            }
        }
        return list
    }

    private suspend fun resolveHosters(hosters: List<HosterLink>): List<StreamSource> {
        val out = mutableListOf<StreamSource>()
        for (h in hosters) {
            try {
                val sources = when {
                    h.name.contains("VidSrc", true) -> resolveVidSrc(h.redirectUrl, h.name)
                    else -> hosterResolver.resolve(h.name, h.redirectUrl)
                }
                out.addAll(sources)
                if (out.isNotEmpty()) break // erste funktionierende Quelle reicht
            } catch (_: Exception) {
            }
        }
        // Falls nichts gefunden: alle Hosters als Fallback durchprobieren
        if (out.isEmpty()) {
            for (h in hosters) {
                try {
                    out.addAll(hosterResolver.resolve(h.name, h.redirectUrl))
                } catch (_: Exception) {
                }
            }
        }
        return out.distinctBy { it.url }
    }

    /**
     * VidSrc: Embed-Seite enthält data-api="/vs_src.php?..."; Response JSON {src:"..."}.
     * Danach wird die Embed-URL weiter aufgelöst.
     */
    private suspend fun resolveVidSrc(embedUrl: String, hosterName: String): List<StreamSource> =
        withContext(Dispatchers.IO) {
            val html = fetch(embedUrl, referer = "https://vidsrc.me/") ?: return@withContext emptyList()
            val apiPath = Regex("""data-api=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
                ?.replace("&amp;", "&")
            val apiUrl = when {
                apiPath.isNullOrBlank() -> null
                apiPath.startsWith("http") -> apiPath
                else -> "https://vidsrc.me$apiPath"
            }
            val nested = if (apiUrl != null) {
                val json = fetch(apiUrl, referer = embedUrl)
                try {
                    JSONObject(json ?: "").optString("src").takeIf { it.startsWith("http") }
                } catch (_: Exception) {
                    null
                }
            } else null

            val target = nested ?: Regex("""src=["'](https?://[^"']+)["']""").find(html)?.groupValues?.get(1)
            if (target.isNullOrBlank()) {
                return@withContext hosterResolver.resolve(hosterName, embedUrl)
            }
            // Nested embed weiter auflösen
            hosterResolver.resolve(hosterName, target).ifEmpty {
                // Manchmal ist die URL selbst schon ein Stream
                if (NovaStreamConfig.isVideoUrl(target)) {
                    listOf(StreamSource(hosterName, target, isHls = target.contains(".m3u8")))
                } else emptyList()
            }
        }

    private fun fetch(url: String, referer: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "*/*")
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
