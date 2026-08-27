package com.novastream.app.data.repository

import android.content.Context
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Repository für Watch-Progress und Watchlist.
 * Speichert lokal in Room Database.
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

    // ─── Continue Watching ──────────────────────────────────────────

    /** Alle Watch-Progress-Einträge (neueste zuerst) als Flow. */
    fun watchProgress(): Flow<List<WatchProgress>> = progressDao.getAll()
        .catch { e -> emit(emptyList()) }

    /** Speichert/aktualisiert den Fortschritt einer Episode. */
    suspend fun saveProgress(
        slug: String,
        seriesTitle: String,
        coverUrl: String?,
        season: Int,
        episode: Int,
        episodeTitle: String,
        positionMs: Long,
        durationMs: Long
    ) {
        try {
            val key = "$slug-$season-$episode"
            progressDao.upsert(
                WatchProgress(
                    episodeKey = key,
                    slug = slug,
                    seriesTitle = seriesTitle,
                    coverUrl = coverUrl,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            )
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.e("WatchRepository", "saveProgress failed", e)
            }
        }
    }

    /** Lädt den Fortschritt einer bestimmten Episode. */
    suspend fun getProgress(slug: String, season: Int, episode: Int): WatchProgress? =
        progressDao.getByEpisode(slug, season, episode)

    /** Lädt alle Progress-Einträge für eine Serie. */
    suspend fun getProgressBySlug(slug: String): List<WatchProgress> = try {
        progressDao.getBySlug(slug)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "getProgressBySlug failed", e)
        emptyList()
    }

    /** Lädt den aktuellsten Progress-Eintrag für eine Serie. */
    suspend fun getLatestProgress(slug: String): WatchProgress? = try {
        progressDao.getLatestForSlug(slug)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "getLatestProgress failed", e)
        null
    }

    /** Entfernt einen Continue-Watching-Eintrag. */
    suspend fun removeProgress(key: String) {
        try { progressDao.delete(key) } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeProgress failed", e)
        }
    }

    /** Entfernt alle Einträge einer Serie. */
    suspend fun removeProgressBySlug(slug: String) {
        try { progressDao.deleteBySlug(slug) } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeProgressBySlug failed", e)
        }
    }

    /** Entfernt alle abgeschlossenen Episoden (>90% geschaut) - direkt in SQL. */
    suspend fun removeCompleted() {
        try { progressDao.deleteCompleted() } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeCompleted failed", e)
        }
    }

    /** Löscht alle Watch-Progress-Daten. */
    suspend fun clearAllProgress() {
        try { progressDao.deleteAll() } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "clearAllProgress failed", e)
        }
    }

    // ─── Watchlist ──────────────────────────────────────────────────

    /** Alle Watchlist-Einträge als Flow. */
    fun watchlist(): Flow<List<WatchlistItem>> = watchlistDao.getAll()
        .catch { e -> emit(emptyList()) }

    /** Prüft ob eine Serie in der Watchlist ist. */
    fun isInWatchlist(slug: String): Flow<Boolean> = watchlistDao.isInWatchlist(slug)

    /** Fügt eine Serie zur Watchlist hinzu. */
    suspend fun addToWatchlist(slug: String, title: String, coverUrl: String?) {
        try {
            watchlistDao.add(WatchlistItem(slug = slug, title = title, coverUrl = coverUrl))
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "addToWatchlist failed", e)
        }
    }

    /** Entfernt eine Serie aus der Watchlist. */
    suspend fun removeFromWatchlist(slug: String) {
        try { watchlistDao.remove(slug) } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeFromWatchlist failed", e)
        }
    }

    /** Entfernt mehrere Serien gleichzeitig aus der Watchlist (batch). */
    suspend fun removeAllFromWatchlist(slugs: List<String>) {
        try { watchlistDao.removeAll(slugs) } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "removeAllFromWatchlist failed", e)
        }
    }

    /** Prüft ob eine Serie in der Watchlist ist (suspend). */
    suspend fun containsInWatchlist(slug: String): Boolean = try {
        watchlistDao.contains(slug)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "containsInWatchlist failed", e)
        false
    }

    /** Leert die Watchlist. */
    suspend fun clearWatchlist() {
        try { watchlistDao.clear() } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchRepository", "clearWatchlist failed", e)
        }
    }
}
