package com.novastream.app.data.provider

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource

/**
 * Abstraktion für einen Streaming-Provider.
 * Jeder Provider (SerienStream, AniWorld, KinoGer, ...) implementiert dieses Interface.
 */
interface StreamingProvider {

    /** Eindeutige ID des Providers (z.B. "serienstream", "aniworld", "kinoger"). */
    val id: String

    /** Anzeigename für die UI. */
    val displayName: String

    /** Basis-URL des Providers. */
    val baseUrl: String

    /** Ob dieser Provider Serien unterstützt. */
    val supportsSeries: Boolean

    /** Ob dieser Provider Filme unterstützt (unabhängig von Serien). */
    val supportsMovies: Boolean
        get() = !supportsSeries

    /** Genres die dieser Provider wirklich anbietet (leere Liste = keine Genre-Sektion). */
    val availableGenres: List<com.novastream.app.data.model.Genre>
        get() = emptyList()

    /** Geschätzte Kataloggröße für UI (null = unbekannt). */
    val catalogHint: String?
        get() = null

    /** Lädt die Startseite mit empfohlenen/populären Titeln. */
    suspend fun loadHome(): ProviderResult<List<Series>>

    /** Sucht nach Serien/Filmen. */
    suspend fun search(query: String): ProviderResult<List<Series>>

    /** Lädt Details + Staffeln einer Serie / eines Films. */
    suspend fun loadSeriesDetail(slug: String): ProviderResult<Pair<Series, List<Season>>>

    /** Lädt Episoden einer bestimmten Staffel. */
    suspend fun loadSeason(slug: String, season: Int): ProviderResult<List<Episode>>

    /** Lädt die Hoster-Links für eine Episode. */
    suspend fun loadHosters(episode: Episode): ProviderResult<List<HosterLink>>

    /** Löst einen Hoster-Link zu Stream-Quellen auf. */
    suspend fun resolveHoster(hoster: HosterLink): ProviderResult<List<StreamSource>>

    /** Lädt Titel nach Genre (optional, default: emptyList). */
    suspend fun loadGenre(genre: String): ProviderResult<List<Series>> =
        ProviderResult.Success(emptyList())

    /** Lädt die neuesten Titel (optional, default: loadHome). */
    suspend fun loadNewest(): ProviderResult<List<Series>> = loadHome()

    /** Lädt die beliebtesten Titel (optional, default: loadHome). */
    suspend fun loadPopular(): ProviderResult<List<Series>> = loadHome()

    /** Lädt Film-Katalog (optional). */
    suspend fun loadMovies(): ProviderResult<List<Series>> =
        ProviderResult.Success(emptyList())

    /** Erweiterter Katalog (z.B. Alphabet-Seiten) für vollere Home-Listen. */
    suspend fun loadExtendedCatalog(): ProviderResult<List<Series>> =
        ProviderResult.Success(emptyList())

    /** Paginierte Katalog-Seite (0-basiert). */
    suspend fun loadCatalogPage(page: Int): ProviderResult<List<Series>> =
        if (page <= 0) loadHome() else ProviderResult.Success(emptyList())

    /** Paginierte Genre-Seite (0-basiert). */
    suspend fun loadGenrePage(genre: String, page: Int): ProviderResult<List<Series>> =
        if (page <= 0) loadGenre(genre) else ProviderResult.Success(emptyList())

    /** Result-Wrapper für Provider-Operationen. */
    sealed class ProviderResult<out T> {
        data class Success<T>(val data: T) : ProviderResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : ProviderResult<Nothing>()

        /** True wenn dies ein Success ist. */
        val isSuccess: Boolean get() = this is Success
        /** True wenn dies ein Error ist. */
        val isError: Boolean get() = this is Error

        /** Mappt den Success-Wert oder gibt null bei Error zurück. */
        inline fun <R> map(transform: (T) -> R): ProviderResult<R> = when (this) {
            is Success -> Success(transform(data))
            is Error -> this
        }

        /** Gibt den Success-Wert oder null zurück. */
        fun getOrNull(): T? = (this as? Success)?.data

        /** Gibt die Fehlermeldung oder null zurück. */
        fun errorOrNull(): String? = (this as? Error)?.message
    }
}

/** Helper Extensions für StreamingProvider. */
val StreamingProvider.isSerienStream: Boolean
    get() = id == "serienstream" || id == "serienstream_cx"
val StreamingProvider.isAniWorld: Boolean get() = id == "aniworld"
val StreamingProvider.isKinoGer: Boolean get() = id == "kinoger"
val StreamingProvider.isBurningSeries: Boolean get() = id == "burningseries"
val StreamingProvider.isMegaKino: Boolean get() = id == "megakino"
val StreamingProvider.isStreamKiste: Boolean get() = id == "streamkiste"
val StreamingProvider.isFilmPalast: Boolean get() = id == "filmpalast"
val StreamingProvider.isKinoZ: Boolean get() = id == "kinoz"
val StreamingProvider.isFreeCatalog: Boolean get() = id == "freecatalog"
val StreamingProvider.isHydraHd: Boolean get() = id == "hydrahd"
val StreamingProvider.isCinezo: Boolean get() = id == "cinezo"
val StreamingProvider.isDramaCool: Boolean get() = id == "dramacool"

/** Provider die sowohl Filme als auch Serien anbieten. */
val StreamingProvider.hasMovies: Boolean get() = supportsMovies
val StreamingProvider.hasSeries: Boolean get() = supportsSeries
