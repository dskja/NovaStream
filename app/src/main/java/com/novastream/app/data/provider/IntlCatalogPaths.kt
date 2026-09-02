package com.novastream.app.data.provider

import com.novastream.app.data.scraper.SiteProfile

/**
 * Alternate catalog entry points for international / FMHY [ConfigurableSiteProvider] sites.
 * Many mirrors expose content on /movie, /tv-show, /watch etc. instead of homePath alone.
 */
object IntlCatalogPaths {

    private val extraPaths: Map<String, List<String>> = mapOf(
        // EN
        "sflix" to listOf("/home", "/movie", "/tv-show", "/top-imdb"),
        "ridomovies" to listOf("/home-rd1", "/movie", "/tv", "/"),
        "anikoto" to listOf("/home", "/filter", "/az-list", "/"),
        "hydrahd" to listOf("/series/", "/movie/", "/"),
        "cinezo" to listOf("/", "/movie/", "/tv/"),
        "showsst" to listOf("/", "/watch/tv/", "/watch/movie/"),
        "phantomflix" to listOf("/", "/movie/", "/watch/"),
        "flixer" to listOf("/shows", "/movie/", "/"),
        "dramacool" to listOf("/", "/drama-list", "/page/1/"),
        "pressplay" to listOf("/", "/movie/", "/tv/"),
        "hianime" to listOf("/home", "/recently-updated", "/az-list"),
        "lookmovie2" to listOf("/", "/movies/", "/shows/"),
        "soap2day" to listOf("/", "/movie/", "/tv-show/"),
        "anymovie" to listOf("/", "/movies/", "/tv/"),
        "mkissa" to listOf("/", "/movies/", "/series/"),
        "mkvmovies" to listOf("/", "/movies/", "/category/"),
        "streamingcommunity_en" to listOf("/en/", "/en/titles", "/titles"),
        // FR
        "wiflix" to listOf("/", "/serie-en-streaming/", "/vf/"),
        "frenchstream" to listOf("/", "/s-tv/", "/film/"),
        "frenchanime" to listOf("/", "/animes-vf/", "/animes-vostfr/"),
        "frembed" to listOf("/", "/movie/", "/tv-show/"),
        "voirfilms" to listOf("/", "/film/", "/genre/"),
        "nekosama" to listOf("/", "/anime/", "/catalogue/"),
        "otakufr" to listOf("/", "/anime/", "/catalogue/"),
        // ES / LATAM
        "fanpelis" to listOf("/", "/movies/", "/tvshows/", "/animes/"),
        "animeflv" to listOf("/", "/anime/", "/browse"),
        "jkanime" to listOf("/", "/anime/", "/letras/"),
        "pelisplusto" to listOf("/", "/serie/", "/pelicula/"),
        "doramasflix" to listOf("/", "/doramas-online/", "/"),
        "cuevana3" to listOf("/", "/pelicula/", "/serie/"),
        "pelisflix" to listOf("/", "/pelicula/", "/serie/"),
        "animefenix" to listOf("/", "/anime/", "/"),
        "tioanime" to listOf("/", "/anime/", "/directorio/"),
        "seriesflix" to listOf("/", "/serie/", "/filme/"),
        "flixlatam" to listOf("/", "/pelicula/", "/serie/"),
        "latanime" to listOf("/", "/anime/", "/"),
        "cinecalidad" to listOf("/", "/pelicula/", "/genero/"),
        // IT
        "guardaserie" to listOf("/", "/serie/", "/serietv/"),
        "cb01" to listOf("/", "/serietv/", "/film/"),
        "altadefinizione01" to listOf("/", "/serie-tv/", "/film/"),
        "animeunity" to listOf("/", "/anime/", "/archivio/"),
        "streamingcommunity_it" to listOf("/", "/titles", "/film/"),
        "animeworld" to listOf("/", "/play/", "/search"),
        "filmpertutti" to listOf("/", "/film/", "/categoria/"),
        "cineblog01" to listOf("/", "/serietv/", "/film/"),
        "guardaflix" to listOf("/", "/film/", "/categoria/"),
        // PL
        "filmyonline" to listOf("/", "/titles/", "/seriale/"),
        "zaluknij" to listOf("/", "/serial-online/", "/filmy-online/"),
        // DE site-profile intl registry
        "hdfilme" to listOf("/", "/filme1/", "/serien/"),
        "einschalten" to listOf("/", "/movie/", "/film/"),
        "moflix" to listOf("/", "/stream/", "/filme/")
    )

    fun catalogPaths(providerId: String, profile: SiteProfile): List<String> {
        val paths = linkedSetOf<String>()
        extraPaths[providerId]?.let { paths.addAll(it) }
        if (profile.homePath.isNotBlank()) paths.add(profile.homePath)
        if (profile.moviePath.isNotBlank()) paths.add(profile.moviePath)
        if (paths.isEmpty()) paths.add("/")
        return paths.toList()
    }
}
