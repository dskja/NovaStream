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

    /** Ob dieser Provider Serien unterstützt (vs. nur Filme). */
    val supportsSeries: Boolean

    /** Lädt die Startseite mit empfohlenen/populären Serien. */
    suspend fun loadHome(): ProviderResult<List<Series>>

    /** Sucht nach Serien. */
    suspend fun search(query: String): ProviderResult<List<Series>>

    /** Lädt Details + Staffeln einer Serie. */
    suspend fun loadSeriesDetail(slug: String): ProviderResult<Pair<Series, List<Season>>>

    /** Lädt Episoden einer bestimmten Staffel. */
    suspend fun loadSeason(slug: String, season: Int): ProviderResult<List<Episode>>

    /** Lädt die Hoster-Links für eine Episode. */
    suspend fun loadHosters(episode: Episode): ProviderResult<List<HosterLink>>

    /** Löst einen Hoster-Link zu Stream-Quellen auf. */
    suspend fun resolveHoster(hoster: HosterLink): ProviderResult<List<StreamSource>>

    /** Lädt Serien nach Genre (optional, default: emptyList). */
    suspend fun loadGenre(genre: String): ProviderResult<List<Series>> =
        ProviderResult.Success(emptyList())

    /** Lädt die neuesten Serien (optional, default: loadHome). */
    suspend fun loadNewest(): ProviderResult<List<Series>> = loadHome()

    /** Lädt die beliebtesten Serien (optional, default: loadHome). */
    suspend fun loadPopular(): ProviderResult<List<Series>> = loadHome()

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
val StreamingProvider.isSerienStream: Boolean get() = id == "serienstream"
val StreamingProvider.isAniWorld: Boolean get() = id == "aniworld"
val StreamingProvider.isKinoGer: Boolean get() = id == "kinoger"
val StreamingProvider.isBurningSeries: Boolean get() = id == "burningseries"
val StreamingProvider.isMegaKino: Boolean get() = id == "megakino"
val StreamingProvider.isStreamKiste: Boolean get() = id == "streamkiste"

/** True wenn der Provider Filme unterstützt (basierend auf supportsSeries = false). */
val StreamingProvider.supportsMovies: Boolean get() = !supportsSeries
