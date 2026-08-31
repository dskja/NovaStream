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
        withRetry { provider.loadHome().tag().toRepoResult() }

    /**
     * Lädt Home parallel mit Popular/Newest/Genres wenn der Provider das unterstützt.
     * Für SerienStream: echte Sektionen statt Round-Robin.
     */
    suspend fun loadHomeCatalog(): RepoResult<HomeCatalog> = withRetry {
        val p = provider
        val expectedId = p.id
        if (p is SerienStreamProvider) {
            p.loadHomeCatalog().map { it.tagAll(expectedId) }.toRepoResult()
        } else {
            coroutineScope {
                val homeDef = async { p.loadHome() }
                val popularDef = async { p.loadPopular() }
                val newestDef = async { p.loadNewest() }
                val moviesDef = async { if (p.supportsMovies) p.loadMovies() else null }
                val extendedDef = async { p.loadExtendedCatalog() }
                val home = homeDef.await().getOrNull().orEmpty().tagAll(expectedId)
                val popular = popularDef.await().getOrNull().orEmpty().tagAll(expectedId)
                val newest = newestDef.await().getOrNull().orEmpty().tagAll(expectedId)
                val movies = moviesDef.await()?.getOrNull().orEmpty().tagAll(expectedId)
                val extended = extendedDef.await().getOrNull().orEmpty().tagAll(expectedId)
                val all = (home + popular + newest + movies + extended).distinctBy { it.id }
                RepoResult.Success(
                    HomeCatalog(
                        hero = home.take(8).ifEmpty { all.take(8) },
                        popular = popular.ifEmpty { home.take(24) },
                        newest = newest.ifEmpty { home.drop(8).take(24) },
                        trending = home.drop(16).take(24).ifEmpty { popular.take(24) },
                        all = all
                    )
                )
            }
        }
    }

    suspend fun loadGenre(genre: String): RepoResult<List<Series>> =
        withRetry { provider.loadGenre(genre).tag().toRepoResult() }

    suspend fun loadNewest(): RepoResult<List<Series>> =
        withRetry { provider.loadNewest().tag().toRepoResult() }

    suspend fun loadPopular(): RepoResult<List<Series>> =
        withRetry { provider.loadPopular().tag().toRepoResult() }

    suspend fun loadMovies(): RepoResult<List<Series>> =
        withRetry { provider.loadMovies().tag().toRepoResult() }

    suspend fun loadExtendedCatalog(): RepoResult<List<Series>> =
        withRetry { provider.loadExtendedCatalog().tag().toRepoResult() }

    suspend fun loadLatestEpisodes(): RepoResult<List<LatestEpisode>> = withRetry {
        val p = provider
        if (p is SerienStreamProvider) {
            p.loadLatestEpisodes().toRepoResult()
        } else {
            RepoResult.Success(emptyList())
        }
    }

    suspend fun search(query: String): RepoResult<List<Series>> =
        withRetry { provider.search(query).tag().toRepoResult() }

    suspend fun loadSeriesDetail(slug: String): RepoResult<Pair<Series, List<Season>>> =
        withRetry {
            provider.loadSeriesDetail(slug).map { (series, seasons) ->
                series.copy(providerId = provider.id) to seasons
            }.toRepoResult()
        }

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

    private fun StreamingProvider.ProviderResult<List<Series>>.tag(): StreamingProvider.ProviderResult<List<Series>> {
        val pid = provider.id
        return map { list -> list.tagAll(pid) }
    }

    private fun List<Series>.tagAll(providerId: String): List<Series> =
        map { s -> if (s.providerId == providerId) s else s.copy(providerId = providerId) }

    private fun HomeCatalog.tagAll(providerId: String): HomeCatalog = copy(
        hero = hero.tagAll(providerId),
        popular = popular.tagAll(providerId),
        newest = newest.tagAll(providerId),
        trending = trending.tagAll(providerId),
        topShows = topShows.tagAll(providerId),
        all = all.tagAll(providerId)
    )
}
