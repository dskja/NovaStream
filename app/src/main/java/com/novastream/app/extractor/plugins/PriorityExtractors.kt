package com.novastream.app.extractor.plugins

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.data.model.StreamSource
import com.novastream.app.extractor.StreamExtractor
import com.novastream.app.util.HosterResolver
import com.novastream.app.util.VoeWebViewResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Priority extractors with hoster-specific logic ported from BetterStreamflix.
 * Falls back to [HosterResolver] when specialized logic fails.
 */

class VoeExtractorPlugin : StreamExtractor() {
    override val name = "VOE"
    override val mainDomain = "voe.sx"
    override val aliasDomains = listOf("voe-unblock.com", "voe-unblock.net", "jennystece.com")
    override val priority = 110

    private val webViewResolver = VoeWebViewResolver()

    override suspend fun extract(url: String, baseUrl: String): List<StreamSource> {
        val webViewResult = webViewResolver.resolve(url, name)
        if (webViewResult.isNotEmpty()) return webViewResult
        return HosterResolver(baseUrl = baseUrl).resolve(name, url)
    }
}

class StreamtapeExtractorPlugin : StreamExtractor() {
    override val name = "Streamtape"
    override val mainDomain = "streamtape.com"
    override val aliasDomains = listOf("streamtape.net", "streamta.site")
    override val priority = 95

    override suspend fun extract(url: String, baseUrl: String): List<StreamSource> {
        return withContext(Dispatchers.IO) {
            try {
                val client = NetworkModule.okHttpClient
                val pageUrl = if (url.startsWith("http")) url else "https://streamtape.com$url"
                val html = client.newCall(
                    Request.Builder().url(pageUrl)
                        .header("User-Agent", NovaStreamConfig.USER_AGENT).build()
                ).execute().use { it.body?.string().orEmpty() }

                val scriptRegex = Regex(
                    "document\\.getElementById\\('botlink'\\)\\.innerHTML\\s*=\\s*'([^']+)'\\s*\\+\\s*\\('([^']+)'\\)\\.substring\\(([0-9]+)\\)"
                )
                val match = scriptRegex.find(html) ?: return@withContext fallback(url, baseUrl)
                val base = match.groupValues[1]
                val params = match.groupValues[2].substring(match.groupValues[3].toInt())
                val id = Regex("id=([^&]+)").find(params)?.groupValues?.get(1) ?: return@withContext fallback(url, baseUrl)
                val expires = Regex("expires=([^&]+)").find(params)?.groupValues?.get(1) ?: return@withContext fallback(url, baseUrl)
                val ip = Regex("ip=([^&]+)").find(params)?.groupValues?.get(1) ?: return@withContext fallback(url, baseUrl)
                val token = Regex("token=([^&]+)").find(params)?.groupValues?.get(1) ?: return@withContext fallback(url, baseUrl)
                val videoUrl = "https://streamtape.com/get_video?id=$id&expires=$expires&ip=$ip&token=$token&stream=1"
                val finalUrl = client.newCall(
                    Request.Builder().url(videoUrl)
                        .header("User-Agent", NovaStreamConfig.USER_AGENT).build()
                ).execute().use { resp ->
                    resp.request.url.toString().takeIf { it.contains(".mp4") || it.contains("streamtape") }
                }
                if (!finalUrl.isNullOrBlank()) {
                    listOf(StreamSource(name, finalUrl, mimeType = "video/mp4", isHls = false))
                } else fallback(url, baseUrl)
            } catch (_: Exception) {
                fallback(url, baseUrl)
            }
        }
    }

    private suspend fun fallback(url: String, baseUrl: String) =
        HosterResolver(baseUrl = baseUrl).resolve(name, url)
}

class DoodstreamExtractorPlugin : StreamExtractor() {
    override val name = "Doodstream"
    override val mainDomain = "doodstream.com"
    override val aliasDomains = listOf("dood.to", "dood.la", "dood.wf", "dood.li", "dood.re", "dood.pm")
    override val priority = 88

    override suspend fun extract(url: String, baseUrl: String): List<StreamSource> =
        HosterResolver(baseUrl = baseUrl).resolve(name, url)
}

class VidplayExtractorPlugin : StreamExtractor() {
    override val name = "Vidplay"
    override val mainDomain = "vidplay.site"
    override val aliasDomains = listOf("vidplay.online", "mycloud.lu", "mcloud.to")
    override val priority = 82

    override suspend fun extract(url: String, baseUrl: String): List<StreamSource> =
        HosterResolver(baseUrl = baseUrl).resolve(name, url)
}

class RabbitstreamExtractorPlugin : StreamExtractor() {
    override val name = "Rabbitstream"
    override val mainDomain = "rabbitstream.net"
    override val aliasDomains = listOf("megacloud.tv", "dokicloud.one", "premiumembeding.com")
    override val priority = 78

    override suspend fun extract(url: String, baseUrl: String): List<StreamSource> =
        HosterResolver(baseUrl = baseUrl).resolve(name, url)
}

object PriorityExtractors {
    fun all(): List<StreamExtractor> = listOf(
        VoeExtractorPlugin(),
        StreamtapeExtractorPlugin(),
        DoodstreamExtractorPlugin(),
        VidplayExtractorPlugin(),
        RabbitstreamExtractorPlugin()
    )
}
