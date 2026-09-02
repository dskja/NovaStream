package com.novastream.app.data.provider

import com.novastream.app.data.scraper.SiteProfile

/**
 * Content needles used when probing mirror domains for a provider.
 * Empty needle = accept any non-challenge homepage.
 */
object ProviderMirrorNeedles {

    private val explicit: Map<String, String> = mapOf(
        "serienstream" to "/serie/",
        "serienstream_cx" to "/serie/",
        "burningseries" to "/serie/",
        "aniworld" to "/anime/stream/",
        "megakino" to "/films/",
        "kinoger" to "/stream/",
        "streamkiste" to "/serien/",
        "filmpalast" to "/stream/",
        "kinoz" to "/Stream/",
        "wiflix" to "serie",
        "frenchstream" to "s-tv",
        "cuevana3" to "pelicula",
        "sflix" to "/tv-show/",
        "hdfilme" to "stream",
        "moflix" to "stream/",
        "ridomovies" to "/tv/",
        "dramacool" to "/drama/",
        "flixer" to "/movie/",
        "frembed" to "/tv-show/",
        "fanpelis" to "/tvshows/",
        "animeflv" to "/anime/",
        "jkanime" to "jkanime.net/",
        "pelisplusto" to "/serie/",
        "doramasflix" to "doramas-online/",
        "guardaserie" to "/serie/",
        "cb01" to "serietv/",
        "altadefinizione01" to "serie-tv/",
        "streamingcommunity_it" to "/title/",
        "streamingcommunity_en" to "/title/",
        "filmyonline" to "/titles/",
        "zaluknij" to "/serial-online/",
        "lookmovie2" to "/movies/",
        "soap2day" to "/movie/",
        "pelisflix" to "/pelicula/",
        "voirfilms" to "/film/",
        "nekosama" to "/anime/",
        "anikoto" to "/watch/",
        "hianime" to "/watch/",
        "animeworld" to "/play/",
        "animeunity" to "/anime/",
        "einschalten" to "/movie/",
        "frenchanime" to "animes-",
        "filmpertutti" to "film/",
        "cineblog01" to "serietv/",
        "seriesflix" to "/serie/",
        "flixlatam" to "/pelicula/",
        "cinecalidad" to "pelicula/",
        "anymovie" to "/movies/",
        "otakufr" to "/anime/",
        "animefenix" to "/anime/",
        "tioanime" to "/anime/",
        "latanime" to "/anime/",
        "guardaflix" to "film/",
        "mkvmovies" to "movies/",
        "mkissa" to "/movies/",
        "hydrahd" to "/movie/",
        "cinezo" to "/movie/",
        "showsst" to "/tv/",
        "phantomflix" to "/watch/",
        "pressplay" to "/watch/"
    )

    fun needleFor(providerId: String, profile: SiteProfile? = null): String {
        explicit[providerId]?.let { return it }
        profile?.let { return deriveFromProfile(it) }
        return ""
    }

    fun hasMirrors(providerId: String): Boolean =
        ProviderDomainManager.alternateDomains(providerId).isNotEmpty()

    private fun deriveFromProfile(profile: SiteProfile): String {
        val selector = profile.seriesLinkSelector.lowercase()
        val pathHints = listOf(
            "/tv-show/", "/serie/", "/anime/", "/watch/", "/movie/", "/film/",
            "/pelicula/", "/titles/", "/stream/", "/serietv/", "/doramas-online/",
            "/serial-online/", "/title/", "/movies/", "/tv/", "/tvshows/", "/shows/"
        )
        for (hint in pathHints) {
            if (selector.contains(hint.trim('/'))) return hint
        }
        Regex("""/([\w-]+)/""").find(profile.seriesLinkPattern)?.groupValues?.getOrNull(1)?.let { segment ->
            if (segment.length >= 3) return "/$segment/"
        }
        return ""
    }
}
