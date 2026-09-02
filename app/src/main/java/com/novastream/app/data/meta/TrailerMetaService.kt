package com.novastream.app.data.meta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves playable trailer URLs from free no-key sources:
 * AniList, Jikan (YouTube), Wikidata (P1651), IMDb video gallery fallback.
 */
object TrailerMetaService {

    fun youtubeWatchUrl(videoId: String): String =
        "https://www.youtube.com/watch?v=${videoId.trim()}"

    fun parseAniListTrailer(trailer: org.json.JSONObject?): String? {
        if (trailer == null) return null
        val site = trailer.optString("site").lowercase()
        val id = trailer.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return when {
            site == "youtube" -> youtubeWatchUrl(id)
            site == "dailymotion" -> "https://www.dailymotion.com/video/$id"
            else -> null
        }
    }

    fun parseJikanTrailer(trailer: org.json.JSONObject?): String? {
        if (trailer == null) return null
        trailer.optString("youtube_id")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let { return youtubeWatchUrl(it) }
        return trailer.optString("url")
            .takeIf { it.isNotBlank() && it != "null" && isDirectPlayable(it) }
    }

    fun isDirectPlayable(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") ||
            lower.contains("youtu.be") ||
            lower.contains("dailymotion.com")
    }

    suspend fun resolve(show: MetaShow, ids: ExternalIds = ExternalIds()): String? = withContext(Dispatchers.IO) {
        show.trailerUrl?.takeIf { isDirectPlayable(it) }?.let { return@withContext it }

        show.anilistId?.let { id ->
            AniListMetaService.trailerForMedia(id)?.let { return@withContext it }
        }

        val malId = show.idMal ?: ids.idMal
        if (malId != null && malId > 0) {
            JikanMetaService.trailerForAnime(malId)?.let { return@withContext it }
        }

        WikidataMetaService.resolveTrailerUrl(
            imdbId = ids.imdbId ?: show.imdbId,
            wikidataId = ids.wikidataId ?: show.wikidataId
        )?.let { return@withContext it }

        show.trailerUrl?.takeIf { it.isNotBlank() }
            ?: FreeMetaService.trailerUrlFor(show)
    }
}
