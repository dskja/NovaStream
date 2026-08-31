package com.novastream.app.data.repository

import android.content.Context
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.provider.ActiveProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Repository für Watch-Progress und Watchlist.
 * Speichert lokal in Room – immer provider-scoped.
 */
class WatchRepository private constructor(context: Context) {

    private val db = NovaStreamDatabase.get(context)
    private val progressDao = db.watchProgressDao()
    private val watchlistDao = db.watchlistDao()

    companion object {
        @Volatile
        private var INSTANCE: WatchRepository? = null

        fun get(context: Context): WatchRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WatchRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    fun watchProgress(): Flow<List<WatchProgress>> = progressDao.getAll()
        .catch { emit(emptyList()) }

    fun watchProgressForActiveProvider(): Flow<List<WatchProgress>> =
        watchProgress().map { list ->
            val pid = ActiveProvider.id
            list.filter { it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown" }
        }

    suspend fun saveProgress(
        slug: String,
        seriesTitle: String,
        coverUrl: String?,
        season: Int,
        episode: Int,
        episodeTitle: String,
        positionMs: Long,
        durationMs: Long,
        isMovie: Boolean = false,
        providerId: String = ActiveProvider.id
    ) {
        try {
            val key = WatchProgress.key(providerId, slug, season, episode)
            progressDao.upsert(
                WatchProgress(
                    episodeKey = key,
                    providerId = providerId,
                    slug = slug,
                    seriesTitle = seriesTitle,
                    coverUrl = coverUrl,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isMovie = isMovie
                )
            )
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.e("WatchRepository", "saveProgress failed", e)
            }
        }
    }

    suspend fun getProgress(slug: String, season: Int, episode: Int): WatchProgress? {
        val pid = ActiveProvider.id
        return progressDao.get(WatchProgress.key(pid, slug, season, episode))
            ?: progressDao.getByEpisode(slug, season, episode)
    }

    suspend fun getProgressBySlug(slug: String): List<WatchProgress> = try {
        progressDao.getBySlug(slug)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "getProgressBySlug failed", e)
        emptyList()
    }

    suspend fun getLatestProgress(slug: String): WatchProgress? = try {
        progressDao.getLatestForSlug(slug)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "getLatestProgress failed", e)
        null
    }

    suspend fun removeProgress(key: String) {
        try {
            progressDao.delete(key)
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeProgress failed", e)
        }
    }

    suspend fun removeProgressBySlug(slug: String) {
        try {
            progressDao.deleteBySlug(slug)
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeProgressBySlug failed", e)
        }
    }

    suspend fun removeCompleted() {
        try {
            progressDao.deleteCompleted()
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeCompleted failed", e)
        }
    }

    suspend fun clearAllProgress() {
        try {
            progressDao.deleteAll()
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "clearAllProgress failed", e)
        }
    }

    fun watchlist(): Flow<List<WatchlistItem>> = watchlistDao.getAll()
        .catch { emit(emptyList()) }

    fun isInWatchlist(slug: String): Flow<Boolean> =
        watchlistDao.isInWatchlistForProvider(ActiveProvider.id, slug)

    suspend fun addToWatchlist(
        slug: String,
        title: String,
        coverUrl: String?,
        isMovie: Boolean = false,
        providerId: String = ActiveProvider.id
    ) {
        try {
            watchlistDao.add(
                WatchlistItem(
                    itemKey = WatchlistItem.key(providerId, slug),
                    providerId = providerId,
                    slug = slug,
                    title = title,
                    coverUrl = coverUrl,
                    isMovie = isMovie
                )
            )
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "addToWatchlist failed", e)
        }
    }

    suspend fun removeFromWatchlist(slug: String) {
        try {
            watchlistDao.removeForProvider(ActiveProvider.id, slug)
            watchlistDao.removeKey(WatchlistItem.key("unknown", slug))
            watchlistDao.removeBySlug(slug)
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeFromWatchlist failed", e)
        }
    }

    suspend fun removeAllFromWatchlist(slugs: List<String>) {
        try {
            watchlistDao.removeAll(slugs)
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeAllFromWatchlist failed", e)
        }
    }

    suspend fun containsInWatchlist(slug: String): Boolean = try {
        watchlistDao.containsForProvider(ActiveProvider.id, slug)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "containsInWatchlist failed", e)
        false
    }

    suspend fun clearWatchlist() {
        try {
            watchlistDao.clear()
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "clearWatchlist failed", e)
        }
    }
}
