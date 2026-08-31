package com.novastream.app.data.repository

import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HomeCatalog
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.LatestEpisode
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.SerienStreamProvider
import com.novastream.app.data.provider.StreamingProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Repository: kapselt den aktiven Streaming-Provider.
 * Liest den aktiven Provider bei JEDEM Aufruf von ActiveProvider.
 */
class NovaStreamRepository {

    private val provider: StreamingProvider get() = ActiveProvider.get()

    sealed class RepoResult<out T> {
        data class Success<T>(val data: T) : RepoResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>()
    }

    suspend fun loadHome(): RepoResult<List<Series>> =
        withRetry { provider.loadHome().toRepoResult() }

    /**
     * Lädt Home parallel mit Popular/Newest/Genres wenn der Provider das unterstützt.
     * Für SerienStream: echte Sektionen statt Round-Robin.
     */
    suspend fun loadHomeCatalog(): RepoResult<HomeCatalog> = withRetry {
        val p = provider
        if (p is SerienStreamProvider) {
            p.loadHomeCatalog().toRepoResult()
        } else {
            // Generischer Aufbau aus parallel geladenen Listen
            coroutineScope {
                val homeDef = async { p.loadHome() }
                val popularDef = async { p.loadPopular() }
                val newestDef = async { p.loadNewest() }
                val home = homeDef.await().getOrNull().orEmpty()
                val popular = popularDef.await().getOrNull().orEmpty()
                val newest = newestDef.await().getOrNull().orEmpty()
                RepoResult.Success(
                    HomeCatalog(
                        hero = home.take(8),
                        popular = popular.ifEmpty { home.take(24) },
                        newest = newest.ifEmpty { home.drop(8).take(24) },
                        trending = home.drop(16).take(24),
                        all = home
                    )
                )
            }
        }
    }

    suspend fun loadGenre(genre: String): RepoResult<List<Series>> =
        withRetry { provider.loadGenre(genre).toRepoResult() }

    suspend fun loadNewest(): RepoResult<List<Series>> =
        withRetry { provider.loadNewest().toRepoResult() }

    suspend fun loadPopular(): RepoResult<List<Series>> =
        withRetry { provider.loadPopular().toRepoResult() }

    suspend fun loadLatestEpisodes(): RepoResult<List<LatestEpisode>> = withRetry {
        val p = provider
        if (p is SerienStreamProvider) {
            p.loadLatestEpisodes().toRepoResult()
        } else {
            RepoResult.Success(emptyList())
        }
    }

    suspend fun search(query: String): RepoResult<List<Series>> =
        withRetry { provider.search(query).toRepoResult() }

    suspend fun loadSeriesDetail(slug: String): RepoResult<Pair<Series, List<Season>>> =
        withRetry { provider.loadSeriesDetail(slug).toRepoResult() }

    suspend fun loadSeason(slug: String, season: Int): RepoResult<List<Episode>> =
        withRetry { provider.loadSeason(slug, season).toRepoResult() }

    suspend fun loadHosters(episode: Episode): RepoResult<List<HosterLink>> =
        withRetry { provider.loadHosters(episode).toRepoResult() }

    suspend fun resolveHoster(hoster: HosterLink): RepoResult<List<StreamSource>> =
        withRetry(maxRetries = 1) { provider.resolveHoster(hoster).toRepoResult() }

    private suspend fun <T> withRetry(
        maxRetries: Int = 2,
        block: suspend () -> RepoResult<T>
    ): RepoResult<T> {
        var lastError: RepoResult.Error? = null
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) delay(attempt * 500L)
            val result = block()
            if (result is RepoResult.Success) return result
            lastError = result as RepoResult.Error
        }
        return lastError ?: RepoResult.Error("Unbekannter Fehler")
    }

    private fun <T> StreamingProvider.ProviderResult<T>.toRepoResult(): RepoResult<T> =
        when (this) {
            is StreamingProvider.ProviderResult.Success -> RepoResult.Success(data)
            is StreamingProvider.ProviderResult.Error -> RepoResult.Error(
                com.novastream.app.util.ErrorMapper.toUserMessage(cause ?: Exception(message)),
                cause
            )
        }
}
