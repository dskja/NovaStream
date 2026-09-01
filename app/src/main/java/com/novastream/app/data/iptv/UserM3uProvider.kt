package com.novastream.app.data.iptv

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * User-supplied M3U playlist provider (v14).
 */
class UserM3uProvider(
    private val playlistUrl: String,
    override val displayName: String = "My M3U"
) : IptvStreamingProvider {

    override val id = "user_m3u"
    override val languageTag = "multi"
    override val logoUrl: String? =
        "https://raw.githubusercontent.com/media-icons/iptv/main/icons/iptv.png"

    override suspend fun loadChannelGroups(): List<IptvChannelGroup> {
        val channels = loadChannels()
        return listOf(IptvChannelGroup(name = displayName, channels = channels))
    }

    override suspend fun searchChannels(query: String): List<IptvChannel> =
        loadChannels().filter { it.name.contains(query, ignoreCase = true) }

    override suspend fun resolveStream(channel: IptvChannel): StreamSource? =
        StreamSource(
            hoster = displayName,
            url = channel.streamUrl,
            mimeType = if (channel.streamUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
            isHls = channel.streamUrl.contains(".m3u8", ignoreCase = true)
        )

    private suspend fun loadChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        if (playlistUrl.isBlank()) return@withContext emptyList()
        val body = NetworkModule.okHttpClient.newCall(
            Request.Builder().url(playlistUrl).build()
        ).execute().use { it.body?.string().orEmpty() }
        M3uParser.parse(body)
    }
}
