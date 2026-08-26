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

    /** Result-Wrapper für Provider-Operationen. */
    sealed class ProviderResult<out T> {
        data class Success<T>(val data: T) : ProviderResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : ProviderResult<Nothing>()
    }
}
