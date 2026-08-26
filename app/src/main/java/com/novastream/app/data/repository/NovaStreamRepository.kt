package com.novastream.app.data.repository

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.StreamingProvider

/**
 * Repository: kapselt den aktiven Streaming-Provider.
 * Liest den aktiven Provider von ActiveProvider (Singleton).
 * Fängt alle Fehler als [RepoResult] ab.
 */
class NovaStreamRepository(
    private val provider: StreamingProvider = ActiveProvider.get()
) {

    sealed class RepoResult<out T> {
        data class Success<T>(val data: T) : RepoResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>()
    }

    suspend fun loadHome(): RepoResult<List<Series>> =
        provider.loadHome().toRepoResult()

    suspend fun search(query: String): RepoResult<List<Series>> =
        provider.search(query).toRepoResult()

    suspend fun loadSeriesDetail(slug: String): RepoResult<Pair<Series, List<Season>>> =
        provider.loadSeriesDetail(slug).toRepoResult()

    suspend fun loadSeason(slug: String, season: Int): RepoResult<List<Episode>> =
        provider.loadSeason(slug, season).toRepoResult()

    suspend fun loadHosters(episode: Episode): RepoResult<List<HosterLink>> =
        provider.loadHosters(episode).toRepoResult()

    suspend fun resolveHoster(hoster: HosterLink): RepoResult<List<StreamSource>> =
        provider.resolveHoster(hoster).toRepoResult()

    private fun <T> StreamingProvider.ProviderResult<T>.toRepoResult(): RepoResult<T> =
        when (this) {
            is StreamingProvider.ProviderResult.Success -> RepoResult.Success(data)
            is StreamingProvider.ProviderResult.Error -> RepoResult.Error(message, cause)
        }
}
