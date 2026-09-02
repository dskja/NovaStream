package com.novastream.app.data.meta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves TV episode metadata from TVMaze with Epguides fallback (both free, no key).
 */
object MetaEpisodeResolver {

    suspend fun seasonsWithEpisodes(
        title: String,
        tvmazeId: String? = null,
        epguidesKey: String? = null
    ): List<MetaSeason> = withContext(Dispatchers.IO) {
        if (!tvmazeId.isNullOrBlank()) {
            val fromTvmaze = runCatching { FreeMetaService.seasonsWithEpisodes(tvmazeId) }.getOrDefault(emptyList())
            if (fromTvmaze.isNotEmpty()) return@withContext fromTvmaze
        }
        val key = epguidesKey?.takeIf { it.isNotBlank() }
            ?: EpguidesMetaService.guessKeyFromTitle(title)
        val fromEpguides = runCatching { EpguidesMetaService.seasonsWithEpisodes(key) }.getOrDefault(emptyList())
        if (fromEpguides.isNotEmpty()) return@withContext fromEpguides
        emptyList()
    }

    suspend fun episodes(
        title: String,
        tvmazeId: String? = null,
        epguidesKey: String? = null,
        season: Int? = null
    ): List<MetaEpisode> = withContext(Dispatchers.IO) {
        if (!tvmazeId.isNullOrBlank()) {
            val fromTvmaze = runCatching {
                val all = FreeMetaService.episodes(tvmazeId)
                if (season != null) all.filter { it.season == season } else all
            }.getOrDefault(emptyList())
            if (fromTvmaze.isNotEmpty()) return@withContext fromTvmaze
        }
        val key = epguidesKey?.takeIf { it.isNotBlank() }
            ?: EpguidesMetaService.guessKeyFromTitle(title)
        runCatching { EpguidesMetaService.episodes(key, season) }.getOrDefault(emptyList())
    }

    suspend fun resolveEpguidesKey(title: String): String? {
        val results = EpguidesMetaService.search(title, limit = 3)
        return results.firstOrNull { FreeMetaService.titlesSimilar(title, it.title) }?.epguidesKey
            ?: results.firstOrNull()?.epguidesKey
    }
}
