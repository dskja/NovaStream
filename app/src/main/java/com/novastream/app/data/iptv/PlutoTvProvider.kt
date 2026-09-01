package com.novastream.app.data.iptv

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Pluto TV free streams via public M3U playlists (v14) — no API keys.
 * Playlists from BuddyChewChew/app-m3u-generator (GitHub, free).
 */
class PlutoTvProvider(
    override val id: String,
    override val displayName: String,
    override val languageTag: String,
    private val playlistUrl: String,
    override val logoUrl: String = "https://i.ibb.co/qz7jJF1/plutotv.png"
) : IptvStreamingProvider {

    private val mutex = Mutex()
    private var cached: List<IptvChannel>? = null
    private var cacheTime = 0L

    override suspend fun loadChannelGroups(): List<IptvChannelGroup> = withContext(Dispatchers.IO) {
        val channels = loadChannels()
        val grouped = channels.groupBy { it.group ?: "General" }
        grouped.map { (group, list) ->
            IptvChannelGroup(name = group, channels = list.distinctBy { it.name }.take(50))
        }.sortedBy { it.name }
    }

    override suspend fun searchChannels(query: String): List<IptvChannel> =
        loadChannels().filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.group?.contains(query, ignoreCase = true) == true)
        }.distinctBy { it.name }.take(80)

    override suspend fun resolveStream(channel: IptvChannel): StreamSource? =
        StreamSource(
            hoster = displayName,
            url = channel.streamUrl,
            mimeType = "application/x-mpegURL",
            isHls = channel.streamUrl.contains(".m3u8", ignoreCase = true)
        )

    private suspend fun loadChannels(): List<IptvChannel> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (cached != null && now - cacheTime < CACHE_MS) return cached!!
        val body = withContext(Dispatchers.IO) {
            NetworkModule.okHttpClient.newCall(
                Request.Builder().url(playlistUrl)
                    .header("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
                    .build()
            ).execute().use { it.body?.string().orEmpty() }
        }
        val parsed = M3uParser.parse(body)
        cached = parsed
        cacheTime = now
        parsed
    }

    companion object {
        private const val CACHE_MS = 30 * 60 * 1000L
        private const val M3U_BASE =
            "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists"

        fun allRegions(): List<PlutoTvProvider> = listOf(
            PlutoTvProvider("pluto_de", "Pluto TV DE", "de", "$M3U_BASE/plutotv_de.m3u"),
            PlutoTvProvider("pluto_en", "Pluto TV US", "en", "$M3U_BASE/plutotv_us.m3u"),
            PlutoTvProvider("pluto_fr", "Pluto TV FR", "fr", "$M3U_BASE/plutotv_fr.m3u"),
            PlutoTvProvider("pluto_es", "Pluto TV ES", "es", "$M3U_BASE/plutotv_es.m3u"),
            PlutoTvProvider("pluto_it", "Pluto TV IT", "it", "$M3U_BASE/plutotv_it.m3u"),
            PlutoTvProvider("pluto_mx", "Pluto TV MX", "es", "$M3U_BASE/plutotv_mx.m3u"),
            PlutoTvProvider("pluto_ar", "Pluto TV AR", "es", "$M3U_BASE/plutotv_ar.m3u")
        )
    }
}
