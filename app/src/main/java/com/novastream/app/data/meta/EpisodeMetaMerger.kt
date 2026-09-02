package com.novastream.app.data.meta

import com.novastream.app.data.model.Episode

/** Merges provider-scraped episodes with free metadata (TVMaze/Epguides). */
object EpisodeMetaMerger {

    fun merge(providerEpisodes: List<Episode>, metaEpisodes: List<MetaEpisode>, season: Int, episodeNumberOffset: Int = 0): List<Episode> {
        if (metaEpisodes.isEmpty()) return providerEpisodes
        val byNumber = metaEpisodes
            .filter {
                episodeNumberOffset > 0 || it.season == season || it.season <= 0
            }
            .associateBy { it.number }
        return providerEpisodes.map { ep ->
            val globalNum = ep.number + episodeNumberOffset
            val meta = byNumber[globalNum] ?: byNumber[ep.number] ?: return@map ep
            ep.copy(
                title = ep.title.takeIf { it.isNotBlank() && !it.equals("Episode ${ep.number}", true) }
                    ?: meta.title.ifBlank { ep.title },
                thumbnailUrl = ep.thumbnailUrl ?: meta.imageUrl,
                summary = ep.summary?.takeIf { it.isNotBlank() } ?: meta.summary?.takeIf { it.isNotBlank() },
                airdate = ep.airdate ?: meta.airdate,
                runtime = ep.runtime ?: meta.runtime?.takeIf { it > 0 }
            )
        }
    }
}
