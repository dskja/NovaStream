package com.novastream.app.data.repository

import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.profile.ProfileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository für Watch-Progress und Watchlist.
 * Speichert lokal in Room – provider- und profil-scoped.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class WatchRepository @Inject constructor(
    db: NovaStreamDatabase,
    private val profileManager: ProfileManager
) {

    private val progressDao = db.watchProgressDao()
    private val watchlistDao = db.watchlistDao()

    private suspend fun activeProfileId(): String {
        profileManager.ensureDefaultProfile()
        return profileManager.getActiveProfile().profileId
    }

    fun watchProgress(): Flow<List<WatchProgress>> =
        profileManager.activeProfileId()
            .flatMapLatest { profileId -> progressDao.getAllForProfile(profileId) }
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
        providerId: String = ActiveProvider.id,
        isAdult: Boolean? = null
    ) {
        try {
            val profileId = activeProfileId()
            val key = WatchProgress.key(profileId, providerId, slug, season, episode)
            val existing = progressDao.get(key)
            progressDao.upsert(
                WatchProgress(
                    episodeKey = key,
                    profileId = profileId,
                    providerId = providerId,
                    slug = slug,
                    seriesTitle = seriesTitle,
                    coverUrl = coverUrl,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isMovie = isMovie,
                    isAdult = isAdult ?: existing?.isAdult
                )
            )
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.e("WatchRepository", "saveProgress failed", e)
            }
        }
    }

    suspend fun getProgress(slug: String, season: Int, episode: Int): WatchProgress? {
        val profileId = activeProfileId()
        val pid = ActiveProvider.id
        return progressDao.get(WatchProgress.key(profileId, pid, slug, season, episode))
    }

    suspend fun getProgressBySlug(slug: String): List<WatchProgress> = try {
        val profileId = activeProfileId()
        val pid = ActiveProvider.id
        progressDao.getBySlug(slug).filter {
            it.profileId == profileId &&
                (it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown")
        }
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "getProgressBySlug failed", e)
        emptyList()
    }

    suspend fun getLatestProgress(slug: String): WatchProgress? = try {
        progressDao.getLatestForSlug(slug, ActiveProvider.id, activeProfileId())
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

    suspend fun removeProgressForEpisode(
        slug: String,
        season: Int,
        episode: Int,
        providerId: String = ActiveProvider.id
    ) {
        removeProgress(WatchProgress.key(activeProfileId(), providerId, slug, season, episode))
    }

    suspend fun removeProgressBySlug(slug: String) {
        try {
            progressDao.deleteBySlug(slug, ActiveProvider.id, activeProfileId())
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeProgressBySlug failed", e)
        }
    }

    suspend fun removeCompleted() {
        try {
            progressDao.deleteCompletedForProfile(activeProfileId())
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeCompleted failed", e)
        }
    }

    suspend fun clearProgressForProvider(providerId: String = ActiveProvider.id) {
        try {
            progressDao.clearForProfile(providerId, activeProfileId())
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "clearProgressForProvider failed", e)
        }
    }

    suspend fun clearAllProgress() = clearProgressForProvider(ActiveProvider.id)

    fun watchlist(): Flow<List<WatchlistItem>> =
        profileManager.activeProfileId()
            .flatMapLatest { profileId -> watchlistDao.getAllForProfile(profileId) }
            .catch { emit(emptyList()) }

    fun isInWatchlist(slug: String): Flow<Boolean> =
        profileManager.activeProfileId()
            .flatMapLatest { profileId ->
                watchlistDao.isInWatchlistForProfile(profileId, ActiveProvider.id, slug)
            }
            .catch { emit(false) }

    suspend fun addToWatchlist(
        slug: String,
        title: String,
        coverUrl: String?,
        isMovie: Boolean = false,
        providerId: String = ActiveProvider.id,
        isAdult: Boolean? = null,
        genres: List<String> = emptyList()
    ) {
        try {
            val profileId = activeProfileId()
            watchlistDao.add(
                WatchlistItem(
                    itemKey = WatchlistItem.key(profileId, providerId, slug),
                    profileId = profileId,
                    providerId = providerId,
                    slug = slug,
                    title = title,
                    coverUrl = coverUrl,
                    isMovie = isMovie,
                    isAdult = isAdult,
                    genres = WatchlistItem.genresToCsv(genres)
                )
            )
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "addToWatchlist failed", e)
        }
    }

    suspend fun upsertWatchlistItem(item: WatchlistItem) {
        try {
            watchlistDao.upsert(item)
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "upsertWatchlistItem failed", e)
        }
    }

    suspend fun removeFromWatchlist(slug: String) {
        try {
            val profileId = activeProfileId()
            watchlistDao.removeForProfile(profileId, ActiveProvider.id, slug)
            watchlistDao.removeKey(WatchlistItem.key(profileId, "unknown", slug))
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeFromWatchlist failed", e)
        }
    }

    suspend fun removeAllFromWatchlist(slugs: List<String>) {
        try {
            watchlistDao.removeAllForProfile(slugs, ActiveProvider.id, activeProfileId())
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeAllFromWatchlist failed", e)
        }
    }

    suspend fun containsInWatchlist(slug: String): Boolean = try {
        watchlistDao.containsForProfile(activeProfileId(), ActiveProvider.id, slug)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "containsInWatchlist failed", e)
        false
    }

    suspend fun clearWatchlistForProvider(providerId: String = ActiveProvider.id) {
        try {
            watchlistDao.clearForProfile(providerId, activeProfileId())
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "clearWatchlistForProvider failed", e)
        }
    }

    suspend fun clearWatchlist() = clearWatchlistForProvider(ActiveProvider.id)

    suspend fun countUnknownProviderRows(): Int = try {
        val profileId = activeProfileId()
        watchlistDao.countUnknownProvider(profileId) + progressDao.countUnknownProvider(profileId)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "countUnknownProviderRows failed", e)
        0
    }

    suspend fun cleanupUnknownProviderRows(): Int = try {
        val profileId = activeProfileId()
        watchlistDao.deleteUnknownProvider(profileId) + progressDao.deleteUnknownProvider(profileId)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "cleanupUnknownProviderRows failed", e)
        0
    }
}
