package com.novastream.app.data.provider

/**
 * UI catalog size hints for every built-in streaming provider.
 * Shown in provider picker / settings when [StreamingProvider.catalogHint] is null.
 */
object ProviderCatalogHints {

  private val hints: Map<String, String> = mapOf(
    // DE — custom scrapers
    "serienstream" to "Große Serien-Auswahl",
    "serienstream_cx" to "SerienStream Mirror (.cx)",
    "aniworld" to "Tausende Animes",
    "kinoger" to "Filme & Serien",
    "burningseries" to "7000+ Serien",
    "megakino" to "Filme & Serien (DE)",
    "streamkiste" to "Filme & Serien (DE Sync)",
    "filmpalast" to "Filme & Serien",
    "kinoz" to "Filme & Serien",
    "hdfilme" to "Filme & Serien (DE)",
    "moflix" to "Filme & Serien (DE)",
    "einschalten" to "Filme (DE)",
    // Free metadata
    "freecatalog" to "Tausende Serien via TVMaze",
    "freecatalogbrowse" to "Trending TV & Anime (kostenlos)",
    // FMHY / EN
    "hydrahd" to "Movies & TV (EN)",
    "cinezo" to "Movies & TV (EN)",
    "showsst" to "TV Shows (EN)",
    "phantomflix" to "Movies & TV (EN)",
    "flixer" to "Movies & TV (EN)",
    "dramacool" to "Asian Drama (EN)",
    "pressplay" to "Movies & TV (EN)",
    "sflix" to "Movies & TV (EN)",
    "ridomovies" to "Movies & TV (EN)",
    "anikoto" to "Anime (EN)",
    "hianime" to "Anime (EN)",
    "lookmovie2" to "Movies (EN)",
    "soap2day" to "Movies & TV (EN)",
    "mkissa" to "Movies (EN)",
    "mkvmovies" to "Movies (EN)",
    "anymovie" to "Movies (EN)",
    "streamingcommunity_en" to "Movies & TV (EN)",
    // FR
    "wiflix" to "Films & Séries (FR)",
    "frenchstream" to "Films & Séries (FR)",
    "frenchanime" to "Anime (FR)",
    "frembed" to "Embeds FR",
    "voirfilms" to "Films (FR)",
    "nekosama" to "Anime (FR)",
    "otakufr" to "Anime (FR)",
    // ES / LATAM
    "fanpelis" to "Películas & Series (ES)",
    "animeflv" to "Anime (ES)",
    "jkanime" to "Anime (ES)",
    "pelisplusto" to "Películas & Series (ES)",
    "doramasflix" to "Doramas (ES)",
    "cuevana3" to "Películas & Series (LATAM)",
    "pelisflix" to "Películas & Series (ES)",
    "animefenix" to "Anime (ES)",
    "tioanime" to "Anime (ES)",
    "seriesflix" to "Series (ES)",
    "flixlatam" to "Películas & Series (LATAM)",
    "latanime" to "Anime (LATAM)",
    "cinecalidad" to "Películas HD (LATAM)",
    // IT
    "guardaserie" to "Serie TV (IT)",
    "cb01" to "Film & Serie TV (IT)",
    "altadefinizione01" to "Film & Serie TV (IT)",
    "animeunity" to "Anime (IT)",
    "streamingcommunity_it" to "Film & Serie TV (IT)",
    "animeworld" to "Anime (IT)",
    "filmpertutti" to "Film (IT)",
    "cineblog01" to "Film & Serie TV (IT)",
    "guardaflix" to "Film (IT)",
    // PL
    "filmyonline" to "Filmy & Seriale (PL)",
    "zaluknij" to "Seriale online (PL)"
  )

  fun forId(providerId: String): String? = hints[providerId]
}
