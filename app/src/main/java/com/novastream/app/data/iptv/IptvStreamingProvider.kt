package com.novastream.app.data.iptv

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.provider.StreamingProvider

/**
 * IPTV streaming provider interface (v14).
 * Separate from VOD [StreamingProvider] — live channels via M3U/Pluto.
 */
interface IptvStreamingProvider {

    val id: String
    val displayName: String
    val languageTag: String
    val logoUrl: String?

    suspend fun loadChannelGroups(): List<IptvChannelGroup>
    suspend fun searchChannels(query: String): List<IptvChannel>
    suspend fun resolveStream(channel: IptvChannel): StreamSource?
}

data class IptvChannel(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val streamUrl: String,
    val userAgent: String? = null,
    val referer: String? = null,
    val tvgId: String? = null
) {
    fun toSeries(): Series = Series(
        id = id,
        title = name,
        coverUrl = logoUrl,
        detailUrl = "iptv://$id",
        isMovie = false,
        providerId = "iptv"
    )

    fun toEpisode(): Episode = Episode(
        number = 1,
        title = name,
        slug = id,
        season = 1,
        hosters = listOf(
            HosterLink(name = "Live", redirectUrl = streamUrl, language = "Live", index = 0)
        )
    )
}

data class IptvChannelGroup(
    val name: String,
    val channels: List<IptvChannel>
)

data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startMs: Long,
    val endMs: Long
)
