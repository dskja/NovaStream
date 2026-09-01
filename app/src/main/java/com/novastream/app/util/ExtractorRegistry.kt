package com.novastream.app.util

import com.novastream.app.data.model.StreamSource

/**
 * Registry of known hoster domains with alias URLs and rotating-domain patterns.
 * Facade over [HosterResolver] with domain matching and retry fallback.
 */
object ExtractorRegistry {

    data class ExtractorEntry(
        val name: String,
        val mainDomain: String,
        val aliasDomains: List<String> = emptyList(),
        val rotatingPatterns: List<Regex> = emptyList()
    )

    private val entries: List<ExtractorEntry> = listOf(
        entry("VOE", "voe.sx", listOf("voe-unblock.com", "voe-unblock.net", "jennystece.com")),
        entry("Streamtape", "streamtape.com", listOf("streamtape.net", "streamtape.to")),
        entry("Doodstream", "doodstream.com", listOf("dood.to", "dood.la", "dood.wf", "dood.li", "dood.re")),
        entry("Filemoon", "filemoon.sx", listOf("filemoon.to", "filemoon.in")),
        entry("Vidplay", "vidplay.site", listOf("vidplay.online", "mycloud.lu", "mcloud.to")),
        entry("Rabbitstream", "rabbitstream.net", listOf("megacloud.tv", "dokicloud.one")),
        entry("Mixdrop", "mixdrop.co", listOf("mixdrop.sx", "mixdrop.ag", "mixdrop.ch")),
        entry("Upstream", "upstream.to"),
        entry("Vidoza", "vidoza.net"),
        entry("Vidmoly", "vidmoly.to", listOf("vidmoly.me")),
        entry("Streamhub", "streamhub.to"),
        entry("StreamWish", "streamwish.to", listOf("embedwish.com", "playerwish.com", "swiftplayers.com")),
        entry("VidHide", "vidhide.com", listOf("vidhidepre.com")),
        entry("SaveFiles", "savefiles.com"),
        entry("Mp4Upload", "mp4upload.com"),
        entry("StreamSB", "streamsb.net", listOf("sbplay.org", "sbplay.one")),
        entry("Uqload", "uqload.com", listOf("uqload.co")),
        entry("YourUpload", "yourupload.com"),
        entry("Okru", "ok.ru"),
        entry("MailRu", "my.mail.ru"),
        entry("GoogleDrive", "drive.google.com"),
        entry("Streamlare", "streamlare.com"),
        entry("Supervideo", "supervideo.cc"),
        entry("Dropload", "dropload.io"),
        entry("BigWarp", "bigwarp.io"),
        entry("LoadX", "loadx.ws"),
        entry("Veev", "veev.to"),
        entry("VidGuard", "vidguard.to"),
        entry("NinjaStream", "ninjastream.to"),
        entry("Goodstream", "goodstream.uno"),
        entry("Lamovie", "lamovie.link"),
        entry("Rpmvid", "rpmvid.com"),
        entry("Fsvid", "fsvid.net"),
        entry("Pcloud", "pcloud.com"),
        entry("VidLink", "vidlink.pro"),
        entry("Vidsrc", "vidsrc.me", listOf("vidsrc.net", "vidsrc.to", "vidsrc.ru")),
        entry("TwoEmbed", "2embed.cc", listOf("2embed.to")),
        entry("Closeload", "closeload.com"),
        entry("LuluVdo", "luluvdo.com"),
        entry("Chillx", "chillx.site", listOf("playerx.stream")),
        entry("Moviesapi", "moviesapi.club"),
        entry("MagaSavor", "magasavor.com"),
        entry("VidMoLy", "vidmoly.to"),
        entry("VideoSibNet", "videosibnet.ru"),
        entry("USTR", "ustr.net"),
        entry("Ridoo", "ridoo.net"),
        entry("Uch", "uch.media"),
        entry("VixSrc", "vixsrc.to"),
        entry("PlusPomla", "pluspomla.com"),
        entry("Oneupload", "oneupload.to"),
        entry("Gupload", "gupload.xyz"),
        entry("StreamUp", "streamup.ws"),
        entry("Einschalten", "einschalten.in"),
        entry("Vidflix", "vidflix.io"),
        entry("Vidrock", "vidrock.net"),
        entry("JKPlayer", "jkplayer.com"),
        entry("Nuupload", "nuupload.com"),
        entry("Vtube", "vtube.to"),
        entry("Upzone", "upzone.cc"),
        entry("MStreamDay", "mstreamday.com"),
        entry("MyFileStorage", "myfilestorage.com"),
        entry("Moflix", "moflix-stream.xyz"),
        entry("Nekostream", "nekostream.to"),
        entry("VidPly", "vidply.com"),
        entry("AmazonDrive", "amazon.com"),
        entry("Vidzy", "vidzy.net"),
        entry("VidHidePre", "vidhidepre.com"),
        entry("PremiumEmbeding", "premiumembeding.com")
    )

    private fun entry(
        name: String,
        mainDomain: String,
        aliases: List<String> = emptyList(),
        rotating: List<Regex> = emptyList()
    ) = ExtractorEntry(name, mainDomain, aliases, rotating)

    fun findMatching(url: String, hosterName: String = ""): List<ExtractorEntry> {
        val lower = url.lowercase()
        val nameLower = hosterName.lowercase()
        return entries.filter { e ->
            lower.contains(e.mainDomain) ||
                e.aliasDomains.any { lower.contains(it) } ||
                e.rotatingPatterns.any { it.containsMatchIn(lower) } ||
                nameLower.contains(e.name.lowercase())
        }.ifEmpty {
            entries.filter { nameLower.contains(it.name.lowercase()) }
        }
    }

    suspend fun resolve(hosterName: String, redirectUrl: String, baseUrl: String): List<StreamSource> {
        if (redirectUrl.isBlank()) return emptyList()
        val resolver = HosterResolver(baseUrl = baseUrl)
        val matches = findMatching(redirectUrl, hosterName)
        if (matches.isEmpty()) {
            return resolver.resolve(hosterName, redirectUrl)
        }
        for (entry in matches) {
            val sources = resolver.resolve(entry.name, redirectUrl)
            if (sources.isNotEmpty()) return sources
        }
        return resolver.resolve(hosterName, redirectUrl)
    }

    fun registeredCount(): Int = entries.size
}
