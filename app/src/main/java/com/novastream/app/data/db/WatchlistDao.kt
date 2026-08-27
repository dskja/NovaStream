package com.novastream.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist ORDER BY title ASC")
    fun getAllByTitleAsc(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist ORDER BY title DESC")
    fun getAllByTitleDesc(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist ORDER BY addedAt ASC")
    fun getAllByAddedAsc(): Flow<List<WatchlistItem>>

    @Query("SELECT COUNT(*) FROM watchlist")
    fun count(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE slug = :slug)")
    fun isInWatchlist(slug: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE slug = :slug)")
    suspend fun contains(slug: String): Boolean

    @Query("SELECT slug FROM watchlist")
    suspend fun getAllSlugs(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: WatchlistItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAll(items: List<WatchlistItem>)

    @Query("DELETE FROM watchlist WHERE slug = :slug")
    suspend fun remove(slug: String)

    @Query("DELETE FROM watchlist WHERE slug IN (:slugs)")
    suspend fun removeAll(slugs: List<String>)

    @Query("DELETE FROM watchlist")
    suspend fun clear()
}
