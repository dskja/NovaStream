package com.novastream.app.data.provider

import com.novastream.app.data.model.Genre

/**
 * Default genre hubs when a provider does not declare [StreamingProvider.availableGenres].
 * Slugs are best-effort for UniversalHtmlScraper / TVMaze providers.
 */
object ContentLanguageGenres {

    fun forLanguage(language: ContentLanguage): List<Genre> = when (language) {
        ContentLanguage.DE -> listOf(
            Genre("action", "Action"),
            Genre("komoedie", "Komödie"),
            Genre("drama", "Drama"),
            Genre("science-fiction", "Sci-Fi"),
            Genre("thriller", "Thriller"),
            Genre("horror", "Horror")
        )
        ContentLanguage.EN -> listOf(
            Genre("action", "Action"),
            Genre("comedy", "Comedy"),
            Genre("drama", "Drama"),
            Genre("science-fiction", "Sci-Fi"),
            Genre("thriller", "Thriller"),
            Genre("horror", "Horror")
        )
        ContentLanguage.FR -> listOf(
            Genre("action", "Action"),
            Genre("comedie", "Comédie"),
            Genre("drame", "Drame"),
            Genre("science-fiction", "Sci-Fi"),
            Genre("thriller", "Thriller"),
            Genre("horreur", "Horreur")
        )
        ContentLanguage.ES -> listOf(
            Genre("accion", "Acción"),
            Genre("comedia", "Comedia"),
            Genre("drama", "Drama"),
            Genre("ciencia-ficcion", "Ciencia ficción"),
            Genre("suspense", "Suspense"),
            Genre("terror", "Terror")
        )
        ContentLanguage.IT -> listOf(
            Genre("azione", "Azione"),
            Genre("commedia", "Commedia"),
            Genre("dramma", "Dramma"),
            Genre("fantascienza", "Fantascienza"),
            Genre("thriller", "Thriller"),
            Genre("horror", "Horror")
        )
        ContentLanguage.PL -> listOf(
            Genre("akcja", "Akcja"),
            Genre("komedia", "Komedia"),
            Genre("dramat", "Dramat"),
            Genre("science-fiction", "Sci-Fi"),
            Genre("thriller", "Thriller"),
            Genre("horror", "Horror")
        )
        ContentLanguage.AR -> listOf(
            Genre("action", "أكشن"),
            Genre("comedy", "كوميديا"),
            Genre("drama", "دراما"),
            Genre("science-fiction", "خيال علمي"),
            Genre("thriller", "إثارة"),
            Genre("horror", "رعب")
        )
        ContentLanguage.MULTI -> listOf(
            Genre("Action", "Action"),
            Genre("Comedy", "Comedy"),
            Genre("Drama", "Drama"),
            Genre("Science-Fiction", "Sci-Fi"),
            Genre("Thriller", "Thriller"),
            Genre("Horror", "Horror")
        )
    }

    fun resolveForProvider(
        provider: StreamingProvider,
        contentLanguage: ContentLanguage
    ): List<Genre> {
        ProviderGenres.forId(provider.id).takeIf { it.isNotEmpty() }?.let { return it }
        if (provider.availableGenres.isNotEmpty()) return provider.availableGenres
        val lang = when {
            contentLanguage != ContentLanguage.MULTI -> contentLanguage
            else -> ProviderGenres.contentLanguageOf(provider.id)
        }
        return forLanguage(lang)
    }
}
