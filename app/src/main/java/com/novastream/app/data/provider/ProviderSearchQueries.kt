package com.novastream.app.data.provider

/**
 * Optimized default search queries per provider for higher hit rates.
 */
object ProviderSearchQueries {

    private val queries: Map<String, String> = mapOf(
        // DE
        "serienstream" to "Dark",
        "serienstream_cx" to "Dark",
        "aniworld" to "Naruto",
        "kinoger" to "Avatar",
        "burningseries" to "Game of Thrones",
        "megakino" to "Avatar",
        "streamkiste" to "Breaking Bad",
        "filmpalast" to "Avatar",
        "kinoz" to "Avatar",
        "hdfilme" to "Avatar",
        "moflix" to "Avatar",
        "einschalten" to "Inception",
        // Free
        "freecatalog" to "Breaking Bad",
        "freecatalogbrowse" to "Arcane",
        // EN
        "hydrahd" to "Avatar",
        "cinezo" to "Avatar",
        "showsst" to "Breaking Bad",
        "phantomflix" to "Avatar",
        "flixer" to "Avatar",
        "sflix" to "Avatar",
        "ridomovies" to "Avatar",
        "anikoto" to "Naruto",
        "dramacool" to "Squid Game",
        "pressplay" to "Avatar",
        "streamingcommunity_en" to "Avatar",
        "mkissa" to "Avatar",
        "lookmovie2" to "Avatar",
        "soap2day" to "Avatar",
        "mkvmovies" to "Avatar",
        "hianime" to "Naruto",
        "anymovie" to "Avatar",
        // FR
        "wiflix" to "Dark",
        "frenchstream" to "Dark",
        "frenchanime" to "Naruto",
        "frembed" to "Avatar",
        "voirfilms" to "Avatar",
        "nekosama" to "Naruto",
        "otakufr" to "Naruto",
        // ES
        "fanpelis" to "Avatar",
        "animeflv" to "Naruto",
        "jkanime" to "Naruto",
        "pelisplusto" to "Avatar",
        "doramasflix" to "Squid Game",
        "cuevana3" to "Avatar",
        "pelisflix" to "Avatar",
        "animefenix" to "Naruto",
        "tioanime" to "Naruto",
        "seriesflix" to "Breaking Bad",
        "flixlatam" to "Avatar",
        "latanime" to "Naruto",
        "cinecalidad" to "Avatar",
        // IT
        "guardaserie" to "Breaking Bad",
        "cb01" to "Avatar",
        "altadefinizione01" to "Avatar",
        "animeunity" to "Naruto",
        "streamingcommunity_it" to "Avatar",
        "animeworld" to "Naruto",
        "filmpertutti" to "Avatar",
        "cineblog01" to "Breaking Bad",
        "guardaflix" to "Avatar",
        // PL
        "filmyonline" to "Breaking Bad",
        "zaluknij" to "Breaking Bad"
    )

    fun forProvider(providerId: String): String = queries[providerId] ?: "Dark"

    fun forProvider(provider: StreamingProvider): String = forProvider(provider.id)
}
