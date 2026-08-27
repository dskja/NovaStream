package com.novastream.app.data.repository

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.StreamingProvider
import kotlinx.coroutines.delay

/**
 * Repository: kapselt den aktiven Streaming-Provider.
 * Liest den aktiven Provider bei JEDEM Aufruf von ActiveProvider (Singleton).
 * So wird sichergestellt dass Provider-Wechsel sofort wirksam werden,
 * auch bei bereits erstellten ViewModels.
 *
 * Fängt alle Fehler als [RepoResult] ab.
 * Beinhaltet automatische Retry-Logik für transient Network-Fehler.
 */
class NovaStreamRepository {

    /** Holt den aktuellen Provider bei jedem Aufruf neu. */
    private val provider: StreamingProvider get() = ActiveProvider.get()

    sealed class RepoResult<out T> {
        data class Success<T>(val data: T) : RepoResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>()
    }

    suspend fun loadHome(): RepoResult<List<Series>> =
        withRetry { provider.loadHome().toRepoResult() }

    suspend fun search(query: String): RepoResult<List<Series>> =
        withRetry { provider.search(query).toRepoResult() }

    suspend fun loadSeriesDetail(slug: String): RepoResult<Pair<Series, List<Season>>> =
        withRetry { provider.loadSeriesDetail(slug).toRepoResult() }

    suspend fun loadSeason(slug: String, season: Int): RepoResult<List<Episode>> =
        withRetry { provider.loadSeason(slug, season).toRepoResult() }

    suspend fun loadHosters(episode: Episode): RepoResult<List<HosterLink>> =
        withRetry { provider.loadHosters(episode).toRepoResult() }

    suspend fun resolveHoster(hoster: HosterLink): RepoResult<List<StreamSource>> =
        provider.resolveHoster(hoster).toRepoResult()

    /**
     * Retry-Wrapper: versucht eine Operation bis zu 2x erneut bei Fehler.
     * Wartet 500ms bzw. 1000ms zwischen Versuchen.
     * Nur für idempotente Lese-Operationen geeignet.
     */
    private suspend fun <T> withRetry(
        maxRetries: Int = 2,
        block: suspend () -> RepoResult<T>
    ): RepoResult<T> {
        var lastError: RepoResult.Error? = null
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) {
                delay(attempt * 500L)
            }
            val result = block()
            if (result is RepoResult.Success) return result
            lastError = result as RepoResult.Error
        }
        return lastError ?: RepoResult.Error("Unbekannter Fehler")
    }

    private fun <T> StreamingProvider.ProviderResult<T>.toRepoResult(): RepoResult<T> =
        when (this) {
            is StreamingProvider.ProviderResult.Success -> RepoResult.Success(data)
            is StreamingProvider.ProviderResult.Error -> RepoResult.Error(message, cause)
        }
}
