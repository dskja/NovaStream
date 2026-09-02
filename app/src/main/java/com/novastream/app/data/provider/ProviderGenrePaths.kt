package com.novastream.app.data.provider

/**
 * Site-specific genre URL templates. Many streaming sites do not use the default
 * `/genre/{genre}` pattern from [com.novastream.app.data.scraper.SiteProfile].
 */
object ProviderGenrePaths {

    private data class Config(
        val paths: List<String>,
        val pagedSuffix: String = "?page={page}"
    )

    private val byProvider: Map<String, Config> = mapOf(
        // DE site-profile
        "hdfilme" to Config(listOf("/serien/{genre}", "/filme1/{genre}", "/genre/{genre}")),
        "moflix" to Config(listOf("/genre/{genre}", "/stream/{genre}")),
        "einschalten" to Config(listOf("/genre/{genre}", "/movie-genre/{genre}")),
        // DE custom (FilmPalast uses semantic slugs)
        "filmpalast" to Config(
            paths = listOf("/serien/view", "/movies/new", "/movies"),
            // genre slug selects path in resolvePaths via semantic map
        ),
        "kinoz" to Config(listOf("/Genre/{genre}")),
        "megakino" to Config(listOf("/genre/{genre}", "/filme", "/")),
        "streamkiste" to Config(listOf("/serien", "/filme", "/genre/{genre}")),
        // EN
        "sflix" to Config(listOf("/genre/{genre}", "/search/{genre}")),
        "hianime" to Config(listOf("/genre/{genre}", "/filter?genre={genre}")),
        "anikoto" to Config(listOf("/genre/{genre}", "/filter?keyword={genre}")),
        "dramacool" to Config(listOf("/drama-category/{genre}", "/genre/{genre}")),
        "lookmovie2" to Config(listOf("/movies/genre/{genre}", "/genre/{genre}")),
        "soap2day" to Config(listOf("/movie-genre/{genre}", "/genre/{genre}")),
        "ridomovies" to Config(listOf("/genre/{genre}", "/search?q={genre}")),
        "anymovie" to Config(listOf("/movies/genre/{genre}", "/genre/{genre}")),
        "mkissa" to Config(listOf("/movies/genre/{genre}", "/genre/{genre}")),
        "mkvmovies" to Config(listOf("/movies/genre/{genre}", "/category/{genre}")),
        "streamingcommunity_en" to Config(listOf("/titles/{genre}", "/genre/{genre}")),
        // FR
        "wiflix" to Config(listOf("/serie-en-streaming/{genre}", "/genre/{genre}")),
        "frenchstream" to Config(listOf("/{genre}", "/s-tv/{genre}", "/genre/{genre}")),
        // EN FMHY
        "hydrahd" to Config(listOf("/series/{genre}", "/movie/{genre}", "/genre/{genre}")),
        "cinezo" to Config(listOf("/genre/{genre}", "/search/{genre}")),
        "showsst" to Config(listOf("/genre/{genre}", "/?s={genre}")),
        "phantomflix" to Config(listOf("/watch/{genre}", "/genre/{genre}")),
        "flixer" to Config(listOf("/movie/{genre}", "/genre/{genre}")),
        "pressplay" to Config(listOf("/watch/{genre}", "/genre/{genre}")),
        "frembed" to Config(listOf("/tv-show/{genre}", "/genre/{genre}")),
        "voirfilms" to Config(listOf("/genre/{genre}", "/film/{genre}")),
        "nekosama" to Config(listOf("/genre/{genre}", "/animes-{genre}")),
        "otakufr" to Config(listOf("/genre/{genre}", "/anime/{genre}")),
        "frenchanime" to Config(listOf("/animes-{genre}", "/genre/{genre}")),
        // ES
        "cuevana3" to Config(listOf("/genero/{genre}", "/genre/{genre}")),
        "pelisflix" to Config(listOf("/genero/{genre}", "/pelicula/{genre}")),
        "fanpelis" to Config(listOf("/genero/{genre}", "/serie/{genre}")),
        "pelisplusto" to Config(listOf("/genero/{genre}", "/serie/{genre}")),
        "doramasflix" to Config(listOf("/doramas-online/{genre}", "/genre/{genre}")),
        "animeflv" to Config(listOf("/browse?genre={genre}", "/genre/{genre}")),
        "jkanime" to Config(listOf("/genero/{genre}", "/genre/{genre}")),
        "animefenix" to Config(listOf("/genero/{genre}", "/anime/{genre}")),
        "tioanime" to Config(listOf("/genero/{genre}", "/anime/{genre}")),
        "latanime" to Config(listOf("/genero/{genre}", "/anime/{genre}")),
        "seriesflix" to Config(listOf("/serie/{genre}", "/genero/{genre}")),
        "flixlatam" to Config(listOf("/genero/{genre}", "/pelicula/{genre}")),
        "cinecalidad" to Config(listOf("/genero/{genre}", "/pelicula/{genre}")),
        // IT
        "guardaserie" to Config(listOf("/serie/{genre}", "/serietv/{genre}")),
        "cb01" to Config(listOf("/serietv/{genre}", "/film/{genre}")),
        "altadefinizione01" to Config(listOf("/serietv/{genre}", "/genre/{genre}")),
        "animeunity" to Config(listOf("/genre/{genre}", "/anime/{genre}")),
        "streamingcommunity_it" to Config(listOf("/titles/{genre}", "/genre/{genre}")),
        "animeworld" to Config(listOf("/genre/{genre}", "/play/{genre}")),
        "filmpertutti" to Config(listOf("/film/{genre}", "/genre/{genre}")),
        "cineblog01" to Config(listOf("/serietv/{genre}", "/genre/{genre}")),
        "guardaflix" to Config(listOf("/film/{genre}", "/genre/{genre}")),
        // PL
        "filmyonline" to Config(listOf("/titles/{genre}", "/genre/{genre}")),
        "zaluknij" to Config(listOf("/serial-online/{genre}", "/genre/{genre}"))
    )

    private val filmpalastSemantic = mapOf(
        "serien" to "/serien/view",
        "series" to "/serien/view",
        "serie" to "/serien/view",
        "filme" to "/movies/new",
        "movies" to "/movies/new",
        "movie" to "/movies/new",
        "neu" to "/movies/new",
        "new" to "/movies/new"
    )

    /**
     * Returns candidate paths (without base URL) to try for [genre], most specific first.
     */
    fun pathsFor(providerId: String, genre: String, profileDefault: String = "/genre/{genre}"): List<String> {
        val slug = genre.trim()
        if (slug.isBlank()) return emptyList()

        if (providerId == "filmpalast") {
            val semantic = filmpalastSemantic[slug.lowercase()] ?: "/serien/view"
            return listOf(semantic)
        }

        val config = byProvider[providerId]
        val templates = config?.paths ?: listOf(profileDefault)
        return templates.map { fill(it, slug) }.distinct()
    }

    /** Paged genre paths; falls back to [pathsFor] + page query when no dedicated template exists. */
    fun pathsForPage(
        providerId: String,
        genre: String,
        page: Int,
        profileDefault: String = "/genre/{genre}",
        profilePageTemplate: String = ""
    ): List<String> {
        if (page <= 0) return pathsFor(providerId, genre, profileDefault)
        val pageOneBased = (page + 1).toString()
        val slug = genre.trim()
        if (slug.isBlank()) return emptyList()

        if (profilePageTemplate.isNotBlank()) {
            return listOf(
                fill(
                    profilePageTemplate.replace("{page}", pageOneBased),
                    slug
                )
            )
        }

        val config = byProvider[providerId]
        val suffix = config?.pagedSuffix ?: "?page={page}"
        return pathsFor(providerId, genre, profileDefault).map { path ->
            if (path.contains('?')) "$path&page=$pageOneBased"
            else path + fill(suffix, slug).replace("{page}", pageOneBased)
        }.distinct()
    }

    private fun fill(template: String, genre: String): String =
        template.replace("{genre}", genre.replace(" ", "%20"))
}
