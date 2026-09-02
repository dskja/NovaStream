package com.novastream.app.data.provider

import com.novastream.app.data.model.Genre

/**
 * Per-provider genre hubs for browse/home UI.
 * Explicit entries use site-native slugs; others fall back to [ContentLanguageGenres].
 */
object ProviderGenres {

    private val deSeries = listOf(
        Genre("action", "Action"),
        Genre("comedy", "Comedy"),
        Genre("drama", "Drama"),
        Genre("science-fiction", "Sci-Fi"),
        Genre("thriller", "Thriller"),
        Genre("horror", "Horror"),
        Genre("fantasy", "Fantasy"),
        Genre("krimi", "Krimi"),
        Genre("mystery", "Mystery"),
        Genre("anime", "Anime"),
        Genre("romantik", "Romantik"),
        Genre("abenteuer", "Abenteuer")
    )

    private val deMoviesSeries = listOf(
        Genre("action", "Action"),
        Genre("comedy", "Comedy"),
        Genre("drama", "Drama"),
        Genre("horror", "Horror"),
        Genre("thriller", "Thriller"),
        Genre("science-fiction", "Sci-Fi"),
        Genre("serien", "Serien"),
        Genre("filme", "Filme")
    )

    private val anime = listOf(
        Genre("action", "Action"),
        Genre("adventure", "Abenteuer"),
        Genre("comedy", "Comedy"),
        Genre("drama", "Drama"),
        Genre("fantasy", "Fantasy"),
        Genre("horror", "Horror"),
        Genre("romance", "Romance"),
        Genre("sci-fi", "Sci-Fi"),
        Genre("slice-of-life", "Slice of Life"),
        Genre("supernatural", "Supernatural"),
        Genre("thriller", "Thriller"),
        Genre("mystery", "Mystery")
    )

    private val asianDrama = listOf(
        Genre("drama", "Drama"),
        Genre("romance", "Romance"),
        Genre("comedy", "Comedy"),
        Genre("action", "Action"),
        Genre("thriller", "Thriller"),
        Genre("historical", "Historical"),
        Genre("fantasy", "Fantasy")
    )

    private val explicit: Map<String, List<Genre>> = mapOf(
        // DE custom
        "serienstream" to deSeries,
        "serienstream_cx" to deSeries,
        "burningseries" to deSeries,
        "aniworld" to anime,
        "kinoger" to listOf(
            Genre("action", "Action"),
            Genre("komodie", "Komödie"),
            Genre("drama", "Drama"),
            Genre("horror", "Horror"),
            Genre("thriller", "Thriller"),
            Genre("fantasy", "Fantasy")
        ),
        "megakino" to deMoviesSeries,
        "streamkiste" to listOf(
            Genre("serien", "Serien"),
            Genre("filme", "Filme"),
            Genre("action", "Action"),
            Genre("drama", "Drama"),
            Genre("comedy", "Comedy")
        ),
        "filmpalast" to listOf(
            Genre("serien", "Serien"),
            Genre("filme", "Filme"),
            Genre("action", "Action"),
            Genre("comedy", "Comedy"),
            Genre("drama", "Drama"),
            Genre("horror", "Horror"),
            Genre("thriller", "Thriller"),
            Genre("science-fiction", "Sci-Fi")
        ),
        "kinoz" to listOf(
            Genre("Action", "Action"),
            Genre("Komodie", "Komödie"),
            Genre("Drama", "Drama"),
            Genre("Horror", "Horror"),
            Genre("Thriller", "Thriller"),
            Genre("Science-Fiction", "Sci-Fi")
        ),
        // Intl overrides (site-native slugs)
        "dramacool" to asianDrama,
        "hianime" to anime,
        "anikoto" to anime,
        "animeflv" to anime,
        "jkanime" to anime,
        "animefenix" to anime,
        "tioanime" to anime,
        "latanime" to anime,
        "animeunity" to anime,
        "animeworld" to anime,
        "nekosama" to anime,
        "otakufr" to anime,
        "frenchanime" to anime,
        "wiflix" to listOf(
            Genre("serie", "Séries"),
            Genre("film", "Films"),
            Genre("action", "Action"),
            Genre("comedie", "Comédie"),
            Genre("drame", "Drame")
        ),
        "frenchstream" to listOf(
            Genre("s-tv", "Séries"),
            Genre("film", "Films"),
            Genre("action", "Action"),
            Genre("comedie", "Comédie")
        ),
        "doramasflix" to listOf(
            Genre("doramas-online", "Doramas"),
            Genre("drama", "Drama"),
            Genre("romance", "Romance"),
            Genre("comedy", "Comedy")
        ),
        "guardaserie" to listOf(
            Genre("serie", "Serie TV"),
            Genre("azione", "Azione"),
            Genre("commedia", "Commedia"),
            Genre("dramma", "Dramma")
        ),
        "cb01" to listOf(
            Genre("serietv", "Serie TV"),
            Genre("film", "Film"),
            Genre("azione", "Azione"),
            Genre("commedia", "Commedia")
        ),
        "cuevana3" to listOf(
            Genre("pelicula", "Películas"),
            Genre("serie", "Series"),
            Genre("accion", "Acción"),
            Genre("comedia", "Comedia")
        ),
        "pelisflix" to listOf(
            Genre("pelicula", "Películas"),
            Genre("serie", "Series"),
            Genre("accion", "Acción"),
            Genre("drama", "Drama")
        ),
        "cinecalidad" to listOf(
            Genre("pelicula", "Películas"),
            Genre("accion", "Acción"),
            Genre("drama", "Drama"),
            Genre("comedia", "Comedia")
        )
    )

    private val languageByProvider: Map<String, ContentLanguage> = mapOf(
        // DE
        "serienstream" to ContentLanguage.DE,
        "serienstream_cx" to ContentLanguage.DE,
        "aniworld" to ContentLanguage.DE,
        "kinoger" to ContentLanguage.DE,
        "burningseries" to ContentLanguage.DE,
        "megakino" to ContentLanguage.DE,
        "streamkiste" to ContentLanguage.DE,
        "filmpalast" to ContentLanguage.DE,
        "kinoz" to ContentLanguage.DE,
        "hdfilme" to ContentLanguage.DE,
        "moflix" to ContentLanguage.DE,
        "einschalten" to ContentLanguage.DE,
        // Free
        "freecatalog" to ContentLanguage.MULTI,
        "freecatalogbrowse" to ContentLanguage.MULTI,
        // EN
        "hydrahd" to ContentLanguage.EN,
        "cinezo" to ContentLanguage.EN,
        "showsst" to ContentLanguage.EN,
        "phantomflix" to ContentLanguage.EN,
        "flixer" to ContentLanguage.EN,
        "sflix" to ContentLanguage.EN,
        "ridomovies" to ContentLanguage.EN,
        "anikoto" to ContentLanguage.EN,
        "dramacool" to ContentLanguage.EN,
        "pressplay" to ContentLanguage.EN,
        "streamingcommunity_en" to ContentLanguage.EN,
        "mkissa" to ContentLanguage.EN,
        "lookmovie2" to ContentLanguage.EN,
        "soap2day" to ContentLanguage.EN,
        "mkvmovies" to ContentLanguage.EN,
        "hianime" to ContentLanguage.EN,
        "anymovie" to ContentLanguage.EN,
        // FR
        "wiflix" to ContentLanguage.FR,
        "frenchstream" to ContentLanguage.FR,
        "frenchanime" to ContentLanguage.FR,
        "frembed" to ContentLanguage.FR,
        "voirfilms" to ContentLanguage.FR,
        "nekosama" to ContentLanguage.FR,
        "otakufr" to ContentLanguage.FR,
        // ES
        "fanpelis" to ContentLanguage.ES,
        "animeflv" to ContentLanguage.ES,
        "jkanime" to ContentLanguage.ES,
        "pelisplusto" to ContentLanguage.ES,
        "doramasflix" to ContentLanguage.ES,
        "cuevana3" to ContentLanguage.ES,
        "pelisflix" to ContentLanguage.ES,
        "animefenix" to ContentLanguage.ES,
        "tioanime" to ContentLanguage.ES,
        "seriesflix" to ContentLanguage.ES,
        "flixlatam" to ContentLanguage.ES,
        "latanime" to ContentLanguage.ES,
        "cinecalidad" to ContentLanguage.ES,
        // IT
        "guardaserie" to ContentLanguage.IT,
        "cb01" to ContentLanguage.IT,
        "altadefinizione01" to ContentLanguage.IT,
        "animeunity" to ContentLanguage.IT,
        "streamingcommunity_it" to ContentLanguage.IT,
        "animeworld" to ContentLanguage.IT,
        "filmpertutti" to ContentLanguage.IT,
        "cineblog01" to ContentLanguage.IT,
        "guardaflix" to ContentLanguage.IT,
        // PL
        "filmyonline" to ContentLanguage.PL,
        "zaluknij" to ContentLanguage.PL
    )

    fun forId(providerId: String): List<Genre> {
        explicit[providerId]?.let { return it }
        val lang = languageByProvider[providerId] ?: return emptyList()
        return ContentLanguageGenres.forLanguage(lang)
    }

    fun contentLanguageOf(providerId: String): ContentLanguage =
        languageByProvider[providerId] ?: ContentLanguage.MULTI
}
