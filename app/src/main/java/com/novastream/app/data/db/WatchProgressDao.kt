package com.novastream.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {

    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<WatchProgress>>

    @Query("SELECT * FROM watch_progress WHERE episodeKey = :key LIMIT 1")
    suspend fun get(key: String): WatchProgress?

    @Query("SELECT * FROM watch_progress WHERE slug = :slug AND season = :season AND episode = :episode LIMIT 1")
    suspend fun getByEpisode(slug: String, season: Int, episode: Int): WatchProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgress)

    @Query("DELETE FROM watch_progress WHERE episodeKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM watch_progress WHERE slug = :slug")
    suspend fun deleteBySlug(slug: String)

    @Query("SELECT * FROM watch_progress WHERE durationMs > 0")
    suspend fun getWithProgress(): List<WatchProgress>

    /** Löscht alle Episoden die zu >90% geschaut wurden (direkt in SQL). */
    @Query("DELETE FROM watch_progress WHERE durationMs > 0 AND (positionMs * 100 / durationMs) >= 90")
    suspend fun deleteCompleted()

    @Query("DELETE FROM watch_progress WHERE episodeKey IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    @Query("DELETE FROM watch_progress")
    suspend fun deleteAll()
}
