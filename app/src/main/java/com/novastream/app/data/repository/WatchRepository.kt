package com.novastream.app.data.repository

import android.content.Context
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository für Watch-Progress und Watchlist.
 * Speichert lokal in Room Database.
 */
class WatchRepository(context: Context) {

    private val db = NovaStreamDatabase.get(context)
    private val progressDao = db.watchProgressDao()
    private val watchlistDao = db.watchlistDao()

    // ─── Continue Watching ──────────────────────────────────────────

    /** Alle Watch-Progress-Einträge (neueste zuerst) als Flow. */
    fun watchProgress(): Flow<List<WatchProgress>> = progressDao.getAll()

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
    }

    /** Lädt den Fortschritt einer bestimmten Episode. */
    suspend fun getProgress(slug: String, season: Int, episode: Int): WatchProgress? =
        progressDao.getByEpisode(slug, season, episode)

    /** Entfernt einen Continue-Watching-Eintrag. */
    suspend fun removeProgress(key: String) = progressDao.delete(key)

    /** Entfernt alle Einträge einer Serie. */
    suspend fun removeProgressBySlug(slug: String) = progressDao.deleteBySlug(slug)

    /** Entfernt alle abgeschlossenen Episoden (>90% geschaut) - direkt in SQL. */
    suspend fun removeCompleted() = progressDao.deleteCompleted()

    /** Löscht alle Watch-Progress-Daten. */
    suspend fun clearAllProgress() = progressDao.deleteAll()

    // ─── Watchlist ──────────────────────────────────────────────────

    /** Alle Watchlist-Einträge als Flow. */
    fun watchlist(): Flow<List<WatchlistItem>> = watchlistDao.getAll()

    /** Prüft ob eine Serie in der Watchlist ist. */
    fun isInWatchlist(slug: String): Flow<Boolean> = watchlistDao.isInWatchlist(slug)

    /** Fügt eine Serie zur Watchlist hinzu. */
    suspend fun addToWatchlist(slug: String, title: String, coverUrl: String?) {
        watchlistDao.add(WatchlistItem(slug = slug, title = title, coverUrl = coverUrl))
    }

    /** Entfernt eine Serie aus der Watchlist. */
    suspend fun removeFromWatchlist(slug: String) = watchlistDao.remove(slug)

    /** Prüft ob eine Serie in der Watchlist ist (suspend). */
    suspend fun containsInWatchlist(slug: String): Boolean = watchlistDao.contains(slug)

    /** Leert die Watchlist. */
    suspend fun clearWatchlist() = watchlistDao.clear()
}
