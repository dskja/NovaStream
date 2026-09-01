package com.novastream.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.novastream.app.data.db.CatalogCacheEntry
import com.novastream.app.data.db.CatalogCacheDao
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HomeCatalog
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.LatestEpisode
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.network.GlobalRequestCoalescer
import com.novastream.app.data.network.ScrapeLimiter
import com.novastream.app.data.meta.FreeMetaService
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.AniWorldProvider
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ContentRegionResolver
import com.novastream.app.data.provider.FreeCatalogProvider
import com.novastream.app.data.provider.MegaKinoProvider
import com.novastream.app.data.provider.ProviderRegistry
import com.novastream.app.data.provider.SerienStreamProvider
import com.novastream.app.data.provider.StreamKisteProvider
import com.novastream.app.data.provider.StreamingProvider
import com.novastream.app.data.provider.capabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Repository: kapselt den aktiven Streaming-Provider.
 * Liest den aktiven Provider bei JEDEM Aufruf von ActiveProvider.
 * Cached Katalog- und Detail-Responses in Room mit TTL.
 */
class NovaStreamRepository private constructor(
    private val cacheDao: CatalogCacheDao?,
    private val appSettings: AppSettings? = null
) {

    private val gson = Gson()

    companion object {
        private const val TTL_HOME_MS = 60L * 60 * 1000
        private const val TTL_CATALOG_MS = 45L * 60 * 1000
        private const val TTL_CATALOG_LETTER_MS = 24L * 60 * 60 * 1000
        private const val TTL_DETAIL_MS = 30L * 60 * 1000
        private const val TTL_SEARCH_MS = 15L * 60 * 1000
        private const val TTL_SEASON_MS = 60L * 60 * 1000
        private const val TTL_HOSTERS_MS = 24L * 60 * 60 * 1000
        private const val MAX_CACHE_PAYLOAD_BYTES = 50L * 1024 * 1024

        @Volatile
        private var INSTANCE: NovaStreamRepository? = null

        fun get(context: Context): NovaStreamRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NovaStreamRepository(
                    NovaStreamDatabase.get(context.applicationContext).catalogCacheDao()
                ).also { INSTANCE = it }
            }

        fun forCache(cacheDao: CatalogCacheDao?, appSettings: AppSettings? = null): NovaStreamRepository =
            NovaStreamRepository(cacheDao, appSettings)
    }

    private val provider: StreamingProvider get() = ActiveProvider.get()

    sealed class RepoResult<out T> {
        data class Success<T>(val data: T) : RepoResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>()
    }

    suspend fun clearCacheForProvider(providerId: String) {
        try {
            cacheDao?.deleteForProvider(providerId)
        } catch (_: Exception) {}
    }

    suspend fun loadHome(): RepoResult<List<Series>> =
        withRetry {
            coalesceNetwork(CatalogCacheEntry.key(provider.id, CatalogCacheEntry.TYPE_LIST, "home-raw")) {
                ScrapeLimiter.withPermit { provider.loadHome().tag().toRepoResult() }
            }
        }

    suspend fun loadHomeCatalog(): RepoResult<HomeCatalog> {
        val live = loadHomeCatalogLive()
        if (live is RepoResult.Success) return live
        val cacheKey = CatalogCacheEntry.key(provider.id, CatalogCacheEntry.TYPE_HOME)
        getCachedHomeStale(cacheKey)?.let { stale ->
            return RepoResult.Success(stale.tagAll(provider.id))
        }
        return live
    }

    private suspend fun loadHomeCatalogLive(): RepoResult<HomeCatalog> = withRetry {
        val p = provider
        val expectedId = p.id
        val cacheKey = CatalogCacheEntry.key(expectedId, CatalogCacheEntry.TYPE_HOME)
        getCachedHome(cacheKey)?.let { cached ->
            return@withRetry RepoResult.Success(cached.tagAll(expectedId))
        }

        coalesceNetwork(cacheKey) {
            getCachedHome(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it.tagAll(expectedId)) }

            val result = if (p is SerienStreamProvider) {
                ScrapeLimiter.withPermit {
                    p.loadHomeCatalog().map { it.tagAll(expectedId) }.toRepoResult()
                }
            } else {
                coroutineScope {
                    val homeDef = async {
                        ScrapeLimiter.withPermit { p.loadHome().getOrNull().orEmpty().tagAll(expectedId) }
                    }
                    val moviesDef = async {
                        if (p.supportsMovies) {
                            ScrapeLimiter.withPermit { p.loadMovies().getOrNull().orEmpty().tagAll(expectedId) }
                        } else {
                            emptyList()
                        }
                    }
                    val home = homeDef.await()
                    val movies = moviesDef.await()
                    val all = (home + movies).distinctBy { it.id }
                    RepoResult.Success(
                        HomeCatalog(
                            hero = home.take(8).ifEmpty { all.take(8) },
                            popular = home.take(24).ifEmpty { all.take(24) },
                            newest = home.drop(8).take(24).ifEmpty { all.drop(8).take(24) },
                            trending = home.drop(16).take(24).ifEmpty { home.take(24) },
                            all = all
                        )
                    )
                }
            }

            if (result is RepoResult.Success) {
                putCached(cacheKey, expectedId, CatalogCacheEntry.TYPE_HOME, result.data, TTL_HOME_MS)
            }
            result
        }
    }

    suspend fun loadGenre(genre: String): RepoResult<List<Series>> = withRetry {
        val pid = provider.id
        val cacheKey = CatalogCacheEntry.key(pid, CatalogCacheEntry.TYPE_GENRE, genre)
        getCachedList(cacheKey)?.let { return@withRetry RepoResult.Success(it.tagAll(pid)) }
        coalesceNetwork(cacheKey) {
            getCachedList(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it.tagAll(pid)) }
            val result = ScrapeLimiter.withPermit { provider.loadGenre(genre).tag().toRepoResult() }
            if (result is RepoResult.Success) {
                putCached(cacheKey, pid, CatalogCacheEntry.TYPE_GENRE, result.data, TTL_CATALOG_MS)
            }
            result
        }
    }

    suspend fun loadNewest(): RepoResult<List<Series>> = cachedListCall(CatalogCacheEntry.TYPE_LIST, "newest") {
        ScrapeLimiter.withPermit { provider.loadNewest().tag().toRepoResult() }
    }

    suspend fun loadPopular(): RepoResult<List<Series>> = cachedListCall(CatalogCacheEntry.TYPE_LIST, "popular") {
        ScrapeLimiter.withPermit { provider.loadPopular().tag().toRepoResult() }
    }

    suspend fun loadMovies(): RepoResult<List<Series>> = cachedListCall(CatalogCacheEntry.TYPE_LIST, "movies") {
        ScrapeLimiter.withPermit { provider.loadMovies().tag().toRepoResult() }
    }

    suspend fun loadExtendedCatalog(): RepoResult<List<Series>> = cachedListCall(CatalogCacheEntry.TYPE_LIST, "extended") {
        ScrapeLimiter.withPermit { provider.loadExtendedCatalog().tag().toRepoResult() }
    }

    suspend fun loadCatalogPage(page: Int): RepoResult<List<Series>> = withRetry {
        val pid = provider.id
        val isLetterPage = provider is AniWorldProvider
        val cacheType = if (isLetterPage) CatalogCacheEntry.TYPE_CATALOG_LETTER else CatalogCacheEntry.TYPE_CATALOG
        val ttlMs = if (isLetterPage) TTL_CATALOG_LETTER_MS else TTL_CATALOG_MS
        val cacheKey = CatalogCacheEntry.key(pid, cacheType, page.toString())
        getCachedList(cacheKey)?.let { return@withRetry RepoResult.Success(it.tagAll(pid)) }
        coalesceNetwork(cacheKey) {
            getCachedList(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it.tagAll(pid)) }
            val result = ScrapeLimiter.withPermit { provider.loadCatalogPage(page).tag().toRepoResult() }
            if (result is RepoResult.Success) {
                putCached(cacheKey, pid, cacheType, result.data, ttlMs)
            }
            result
        }
    }

    suspend fun loadGenrePage(genre: String, page: Int): RepoResult<List<Series>> = withRetry {
        val pid = provider.id
        val cacheKey = CatalogCacheEntry.key(pid, CatalogCacheEntry.TYPE_GENRE, genre, page.toString())
        getCachedList(cacheKey)?.let { return@withRetry RepoResult.Success(it.tagAll(pid)) }
        coalesceNetwork(cacheKey) {
            getCachedList(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it.tagAll(pid)) }
            val result = ScrapeLimiter.withPermit { provider.loadGenrePage(genre, page).tag().toRepoResult() }
            if (result is RepoResult.Success) {
                putCached(cacheKey, pid, CatalogCacheEntry.TYPE_GENRE, result.data, TTL_CATALOG_MS)
            }
            result
        }
    }

    suspend fun loadLatestEpisodes(): RepoResult<List<LatestEpisode>> = withRetry {
        val p = provider
        val pid = p.id
        val cacheKey = CatalogCacheEntry.key(pid, CatalogCacheEntry.TYPE_LATEST, resolvedContentLanguage(p.id).tag)
        getCachedLatest(cacheKey)?.let { return@withRetry RepoResult.Success(it) }
        coalesceNetwork(cacheKey) {
            getCachedLatest(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it) }
            val result = ScrapeLimiter.withPermit { loadLatestEpisodesForProvider(p) }
            if (result is RepoResult.Success) {
                putCached(cacheKey, pid, CatalogCacheEntry.TYPE_LATEST, result.data, TTL_CATALOG_MS)
            }
            result
        }
    }

    private suspend fun loadLatestEpisodesForProvider(p: StreamingProvider): RepoResult<List<LatestEpisode>> {
        if (p.capabilities().supportsLatestEpisodes) {
            when (p) {
                is SerienStreamProvider -> return p.loadLatestEpisodes().toRepoResult()
                is AniWorldProvider -> return p.loadLatestEpisodes().toRepoResult()
                is MegaKinoProvider -> return p.loadLatestEpisodes().toRepoResult()
                is StreamKisteProvider -> return p.loadLatestEpisodes().toRepoResult()
                is FreeCatalogProvider -> return loadFreeCatalogLatest(p)
                else -> { /* fall through to generic newest */ }
            }
        }
        val newest = p.loadNewest().getOrNull().orEmpty()
        return if (newest.isNotEmpty()) {
            RepoResult.Success(seriesToLatestEpisodes(newest))
        } else {
            RepoResult.Success(emptyList())
        }
    }

    private suspend fun loadFreeCatalogLatest(p: FreeCatalogProvider): RepoResult<List<LatestEpisode>> {
        val region = ContentRegionResolver.tvmazeRegionFor(resolvedContentLanguage(p.id))
        return runCatching {
            FreeMetaService.schedule(region).map { show ->
                LatestEpisode(
                    seriesSlug = show.id,
                    seriesTitle = show.title,
                    season = 1,
                    episode = 1,
                    coverUrl = show.posterUrl
                )
            }.take(24)
        }.fold(
            onSuccess = { RepoResult.Success(it) },
            onFailure = {
                val fallback = p.loadNewest().getOrNull().orEmpty()
                if (fallback.isNotEmpty()) {
                    RepoResult.Success(seriesToLatestEpisodes(fallback))
                } else {
                    RepoResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it)
                }
            }
        )
    }

    private fun seriesToLatestEpisodes(series: List<Series>): List<LatestEpisode> =
        series.take(24).map { s ->
            LatestEpisode(
                seriesSlug = s.id,
                seriesTitle = s.title,
                season = 1,
                episode = 1,
                coverUrl = s.coverUrl
            )
        }

    private suspend fun resolvedContentLanguage(providerId: String): ContentLanguage {
        val fromSettings = ContentLanguage.fromTag(
            appSettings?.contentLanguage?.first()
        )
        return if (fromSettings != ContentLanguage.MULTI) {
            fromSettings
        } else {
            ProviderRegistry.contentLanguageOf(providerId)
        }
    }

    suspend fun search(query: String): RepoResult<List<Series>> = withRetry {
        val pid = provider.id
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return@withRetry RepoResult.Success(emptyList())
        val cacheKey = CatalogCacheEntry.key(pid, CatalogCacheEntry.TYPE_SEARCH, normalized)
        getCachedList(cacheKey)?.let { return@withRetry RepoResult.Success(it.tagAll(pid)) }
        coalesceNetwork(cacheKey) {
            getCachedList(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it.tagAll(pid)) }
            val result = ScrapeLimiter.withPermit { provider.search(query).tag().toRepoResult() }
            if (result is RepoResult.Success) {
                putCached(cacheKey, pid, CatalogCacheEntry.TYPE_SEARCH, result.data, TTL_SEARCH_MS)
            }
            result
        }
    }

    suspend fun loadSeriesDetail(slug: String): RepoResult<Pair<Series, List<Season>>> = withRetry {
        val pid = provider.id
        val cacheKey = CatalogCacheEntry.key(pid, CatalogCacheEntry.TYPE_DETAIL, slug)
        getCachedDetail(cacheKey)?.let { (series, seasons) ->
            return@withRetry RepoResult.Success(series.copy(providerId = pid) to seasons)
        }
        coalesceNetwork(cacheKey) {
            getCachedDetail(cacheKey)?.let { (series, seasons) ->
                return@coalesceNetwork RepoResult.Success(series.copy(providerId = pid) to seasons)
            }
            val result = ScrapeLimiter.withPermit {
                provider.loadSeriesDetail(slug).map { (series, seasons) ->
                    series.copy(providerId = pid) to seasons
                }.toRepoResult()
            }
            if (result is RepoResult.Success) {
                putCached(
                    cacheKey,
                    pid,
                    CatalogCacheEntry.TYPE_DETAIL,
                    DetailCache(result.data.first, result.data.second),
                    TTL_DETAIL_MS
                )
            }
            result
        }
    }

    suspend fun loadSeason(slug: String, season: Int): RepoResult<List<Episode>> =
        withRetry {
            val pid = provider.id
            val cacheKey = CatalogCacheEntry.key(pid, CatalogCacheEntry.TYPE_SEASON, slug, season.toString())
            getCachedEpisodes(cacheKey)?.let { return@withRetry RepoResult.Success(it) }
            coalesceNetwork(cacheKey) {
                getCachedEpisodes(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it) }
                val result = ScrapeLimiter.withPermit { provider.loadSeason(slug, season).toRepoResult() }
                if (result is RepoResult.Success) {
                    putCached(cacheKey, pid, CatalogCacheEntry.TYPE_SEASON, result.data, TTL_SEASON_MS)
                }
                result
            }
        }

    suspend fun loadHosters(episode: Episode): RepoResult<List<HosterLink>> =
        withRetry {
            val pid = provider.id
            val cacheKey = CatalogCacheEntry.key(
                pid,
                CatalogCacheEntry.TYPE_HOSTERS,
                episode.slug,
                episode.season.toString(),
                episode.number.toString()
            )
            getCachedHosters(cacheKey)?.let { return@withRetry RepoResult.Success(it) }
            coalesceNetwork(cacheKey) {
                getCachedHosters(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it) }
                val result = ScrapeLimiter.withPermit { provider.loadHosters(episode).toRepoResult() }
                if (result is RepoResult.Success) {
                    putCached(cacheKey, pid, CatalogCacheEntry.TYPE_HOSTERS, result.data, TTL_HOSTERS_MS)
                }
                result
            }
        }

    suspend fun resolveHoster(hoster: HosterLink): RepoResult<List<StreamSource>> =
        withRetry(maxRetries = 1) {
            ScrapeLimiter.withPermit { provider.resolveHoster(hoster).toRepoResult() }
        }

    suspend fun purgeExpiredCache() {
        try {
            cacheDao?.deleteExpired()
            evictLruIfNeeded()
        } catch (_: Exception) {}
    }

    suspend fun evictLruIfNeeded() {
        val dao = cacheDao ?: return
        try {
            var total = dao.totalPayloadBytes()
            if (total <= MAX_CACHE_PAYLOAD_BYTES) return
            for (entry in dao.listByOldest()) {
                if (total <= MAX_CACHE_PAYLOAD_BYTES) break
                dao.delete(entry.cacheKey)
                total -= entry.payload.length
            }
        } catch (_: Exception) {}
    }

    private suspend fun <T> coalesceNetwork(key: String, block: suspend () -> RepoResult<T>): RepoResult<T> =
        GlobalRequestCoalescer.instance.coalesce(key) { block() }

    private suspend fun cachedListCall(
        type: String,
        suffix: String,
        block: suspend () -> RepoResult<List<Series>>
    ): RepoResult<List<Series>> = withRetry {
        val pid = provider.id
        val cacheKey = CatalogCacheEntry.key(pid, type, suffix)
        getCachedList(cacheKey)?.let { return@withRetry RepoResult.Success(it.tagAll(pid)) }
        coalesceNetwork(cacheKey) {
            getCachedList(cacheKey)?.let { return@coalesceNetwork RepoResult.Success(it.tagAll(pid)) }
            val result = block()
            if (result is RepoResult.Success) {
                putCached(cacheKey, pid, type, result.data, TTL_CATALOG_MS)
            }
            result
        }
    }

    private suspend fun getCachedHome(key: String): HomeCatalog? {
        val dao = cacheDao ?: return null
        val entry = dao.get(key) ?: return null
        if (entry.isExpired) {
            dao.delete(key)
            return null
        }
        return decodeHome(entry) ?: run {
            dao.delete(key)
            null
        }
    }

    private suspend fun getCachedHomeStale(key: String): HomeCatalog? {
        val dao = cacheDao ?: return null
        val entry = dao.get(key) ?: return null
        return decodeHome(entry)
    }

    private fun decodeHome(entry: CatalogCacheEntry): HomeCatalog? =
        try {
            gson.fromJson(entry.payload, HomeCatalog::class.java)
        } catch (_: Exception) {
            null
        }

    private suspend fun getCachedList(key: String): List<Series>? {
        val dao = cacheDao ?: return null
        val entry = dao.get(key) ?: return null
        if (entry.isExpired) {
            dao.delete(key)
            return null
        }
        return try {
            val type = object : TypeToken<List<Series>>() {}.type
            gson.fromJson<List<Series>>(entry.payload, type)
        } catch (_: Exception) {
            dao.delete(key)
            null
        }
    }

    private suspend fun getCachedEpisodes(key: String): List<Episode>? {
        val dao = cacheDao ?: return null
        val entry = dao.get(key) ?: return null
        if (entry.isExpired) {
            dao.delete(key)
            return null
        }
        return try {
            val type = object : TypeToken<List<Episode>>() {}.type
            gson.fromJson<List<Episode>>(entry.payload, type)
        } catch (_: Exception) {
            dao.delete(key)
            null
        }
    }

    private suspend fun getCachedHosters(key: String): List<HosterLink>? {
        val dao = cacheDao ?: return null
        val entry = dao.get(key) ?: return null
        if (entry.isExpired) {
            dao.delete(key)
            return null
        }
        return try {
            val type = object : TypeToken<List<HosterLink>>() {}.type
            gson.fromJson<List<HosterLink>>(entry.payload, type)
        } catch (_: Exception) {
            dao.delete(key)
            null
        }
    }

    private suspend fun getCachedLatest(key: String): List<LatestEpisode>? {
        val dao = cacheDao ?: return null
        val entry = dao.get(key) ?: return null
        if (entry.isExpired) {
            dao.delete(key)
            return null
        }
        return try {
            val type = object : TypeToken<List<LatestEpisode>>() {}.type
            gson.fromJson<List<LatestEpisode>>(entry.payload, type)
        } catch (_: Exception) {
            dao.delete(key)
            null
        }
    }

    private suspend fun getCachedDetail(key: String): Pair<Series, List<Season>>? {
        val dao = cacheDao ?: return null
        val entry = dao.get(key) ?: return null
        if (entry.isExpired) {
            dao.delete(key)
            return null
        }
        return try {
            val cached = gson.fromJson(entry.payload, DetailCache::class.java)
            cached.series to cached.seasons
        } catch (_: Exception) {
            dao.delete(key)
            null
        }
    }

    private suspend fun putCached(
        key: String,
        providerId: String,
        cacheType: String,
        data: Any,
        ttlMs: Long
    ) {
        val dao = cacheDao ?: return
        try {
            val now = System.currentTimeMillis()
            dao.upsert(
                CatalogCacheEntry(
                    cacheKey = key,
                    providerId = providerId,
                    cacheType = cacheType,
                    payload = gson.toJson(data),
                    cachedAt = now,
                    expiresAt = now + ttlMs
                )
            )
            evictLruIfNeeded()
        } catch (_: Exception) {}
    }

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

    private data class DetailCache(val series: Series, val seasons: List<Season>)
}
