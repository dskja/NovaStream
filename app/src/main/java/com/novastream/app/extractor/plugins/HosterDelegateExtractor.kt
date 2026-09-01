package com.novastream.app.extractor.plugins

import com.novastream.app.data.model.StreamSource
import com.novastream.app.extractor.StreamExtractor
import com.novastream.app.util.HosterResolver

/**
 * Standard plugin that delegates to [HosterResolver] for HTML-based extraction.
 * Used for ~40 hosters where NovaStream already has resolver logic.
 */
class HosterDelegateExtractor(
    override val name: String,
    override val mainDomain: String,
    override val aliasDomains: List<String> = emptyList(),
    override val rotatingPatterns: List<Regex> = emptyList(),
    override val priority: Int = 0
) : StreamExtractor() {

    override suspend fun extract(url: String, baseUrl: String): List<StreamSource> {
        val resolver = HosterResolver(baseUrl = baseUrl.ifBlank { "https://example.com" })
        return resolver.resolve(name, url)
    }
}

/** Factory for delegate-based hoster plugins. */
object StandardHosterExtractors {

    private data class Def(
        val name: String,
        val domain: String,
        val aliases: List<String> = emptyList(),
        val priority: Int = 0
    )

    private val defs = listOf(
        Def("VOE", "voe.sx", listOf("voe-unblock.com", "voe-unblock.net", "jennystece.com"), 100),
        Def("Streamtape", "streamtape.com", listOf("streamtape.net", "streamtape.to"), 90),
        Def("Doodstream", "doodstream.com", listOf("dood.to", "dood.la", "dood.wf", "dood.li", "dood.re"), 85),
        Def("Filemoon", "filemoon.sx", listOf("filemoon.to", "filemoon.in"), 80),
        Def("Vidplay", "vidplay.site", listOf("vidplay.online", "mycloud.lu", "mcloud.to"), 80),
        Def("Rabbitstream", "rabbitstream.net", listOf("megacloud.tv", "dokicloud.one"), 75),
        Def("Mixdrop", "mixdrop.co", listOf("mixdrop.sx", "mixdrop.ag", "mixdrop.ch"), 75),
        Def("Upstream", "upstream.to"),
        Def("Vidoza", "vidoza.net"),
        Def("Vidmoly", "vidmoly.to", listOf("vidmoly.me")),
        Def("Streamhub", "streamhub.to"),
        Def("StreamWish", "streamwish.to", listOf("embedwish.com", "playerwish.com", "swiftplayers.com")),
        Def("VidHide", "vidhide.com", listOf("vidhidepre.com")),
        Def("SaveFiles", "savefiles.com"),
        Def("Mp4Upload", "mp4upload.com"),
        Def("StreamSB", "streamsb.net", listOf("sbplay.org", "sbplay.one")),
        Def("Uqload", "uqload.com", listOf("uqload.co")),
        Def("YourUpload", "yourupload.com"),
        Def("Okru", "ok.ru"),
        Def("MailRu", "my.mail.ru"),
        Def("GoogleDrive", "drive.google.com"),
        Def("Streamlare", "streamlare.com"),
        Def("Supervideo", "supervideo.cc"),
        Def("Dropload", "dropload.io"),
        Def("BigWarp", "bigwarp.io"),
        Def("LoadX", "loadx.ws"),
        Def("Veev", "veev.to"),
        Def("VidGuard", "vidguard.to"),
        Def("NinjaStream", "ninjastream.to"),
        Def("Goodstream", "goodstream.uno"),
        Def("Lamovie", "lamovie.link"),
        Def("Rpmvid", "rpmvid.com"),
        Def("Fsvid", "fsvid.net"),
        Def("Pcloud", "pcloud.com"),
        Def("VidLink", "vidlink.pro"),
        Def("Vidsrc", "vidsrc.me", listOf("vidsrc.net", "vidsrc.to", "vidsrc.ru")),
        Def("TwoEmbed", "2embed.cc", listOf("2embed.to")),
        Def("Closeload", "closeload.com"),
        Def("LuluVdo", "luluvdo.com"),
        Def("Chillx", "chillx.site", listOf("playerx.stream")),
        Def("Moviesapi", "moviesapi.club"),
        Def("MagaSavor", "magasavor.com"),
        Def("VideoSibNet", "videosibnet.ru"),
        Def("USTR", "ustr.net"),
        Def("Ridoo", "ridoo.net"),
        Def("Uch", "uch.media"),
        Def("VixSrc", "vixsrc.to"),
        Def("PlusPomla", "pluspomla.com"),
        Def("Oneupload", "oneupload.to"),
        Def("Gupload", "gupload.xyz"),
        Def("StreamUp", "streamup.ws"),
        Def("Einschalten", "einschalten.in"),
        Def("Vidflix", "vidflix.io"),
        Def("Vidrock", "vidrock.net"),
        Def("JKPlayer", "jkplayer.com"),
        Def("Nuupload", "nuupload.com"),
        Def("Vtube", "vtube.to"),
        Def("Upzone", "upzone.cc"),
        Def("MStreamDay", "mstreamday.com"),
        Def("MyFileStorage", "myfilestorage.com"),
        Def("Moflix", "moflix-stream.xyz"),
        Def("Nekostream", "nekostream.to"),
        Def("VidPly", "vidply.com"),
        Def("AmazonDrive", "amazon.com"),
        Def("Vidzy", "vidzy.net"),
        Def("PremiumEmbeding", "premiumembeding.com"),
        Def("Vixcloud", "vixcloud.co"),
        Def("Vidnest", "vidnest.io"),
        Def("Streamruby", "streamruby.com"),
        Def("Streamix", "streamix.site"),
        Def("ShareCloudy", "sharecloudy.com"),
        Def("PrimeSrc", "primesrc.me"),
        Def("Videasy", "videasy.net"),
        Def("Vidzee", "vidzee.wtf"),
        Def("Vidora", "vidora.stream"),
        Def("Vidsonic", "vidsonic.pro"),
        Def("Vidara", "vidara.to"),
        Def("VidxGo", "vidxgo.com"),
        Def("Zilla", "zilla.tv"),
        Def("PDrain", "pdrain.com"),
        Def("Maxstream", "maxstream.video"),
        Def("GxPlayer", "gxplayer.net"),
        Def("Dailymotion", "dailymotion.com"),
        Def("Hxfile", "hxfile.co"),
        Def("UpZur", "upzur.com"),
        Def("OnRegardeOu", "onregardeou.com"),
        Def("ApiVoirFilm", "apivoirfilm.com")
    )

    fun all(): List<StreamExtractor> = defs.map { d ->
        HosterDelegateExtractor(d.name, d.domain, d.aliases, priority = d.priority)
    }
}
