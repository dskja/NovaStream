package com.novastream.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun getAllForProfile(profileId: String): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist WHERE providerId = :providerId ORDER BY addedAt DESC")
    fun getAllForProvider(providerId: String): Flow<List<WatchlistItem>>

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

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE itemKey = :itemKey)")
    fun isInWatchlistKey(itemKey: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE slug = :slug)")
    fun isInWatchlistBySlug(slug: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE profileId = :profileId AND providerId = :providerId AND slug = :slug)")
    fun isInWatchlistForProfile(profileId: String, providerId: String, slug: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE providerId = :providerId AND slug = :slug)")
    fun isInWatchlistForProvider(providerId: String, slug: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE slug = :slug)")
    suspend fun containsSlug(slug: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE profileId = :profileId AND providerId = :providerId AND slug = :slug)")
    suspend fun containsForProfile(profileId: String, providerId: String, slug: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE providerId = :providerId AND slug = :slug)")
    suspend fun containsForProvider(providerId: String, slug: String): Boolean

    @Query("SELECT slug FROM watchlist")
    suspend fun getAllSlugs(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: WatchlistItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun addAll(items: List<WatchlistItem>)

    @Query("DELETE FROM watchlist WHERE slug = :slug")
    suspend fun removeBySlug(slug: String)

    @Query("DELETE FROM watchlist WHERE itemKey = :itemKey")
    suspend fun removeKey(itemKey: String)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND providerId = :providerId AND slug = :slug")
    suspend fun removeForProfile(profileId: String, providerId: String, slug: String)

    @Query("DELETE FROM watchlist WHERE providerId = :providerId AND slug = :slug")
    suspend fun removeForProvider(providerId: String, slug: String)

    @Query("DELETE FROM watchlist WHERE slug IN (:slugs) AND providerId = :providerId AND profileId = :profileId")
    @Transaction
    suspend fun removeAllForProfile(slugs: List<String>, providerId: String, profileId: String)

    @Query("DELETE FROM watchlist WHERE slug IN (:slugs) AND providerId = :providerId")
    @Transaction
    suspend fun removeAll(slugs: List<String>, providerId: String)

    @Query("DELETE FROM watchlist WHERE providerId = :providerId AND profileId = :profileId")
    suspend fun clearForProfile(providerId: String, profileId: String)

    @Query("DELETE FROM watchlist WHERE providerId = :providerId")
    suspend fun clearForProvider(providerId: String)

    @Query("DELETE FROM watchlist")
    suspend fun clear()

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    suspend fun getAllOnce(): List<WatchlistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistItem)

    @Query("SELECT COUNT(*) FROM watchlist WHERE providerId = 'unknown' AND profileId = :profileId")
    suspend fun countUnknownProvider(profileId: String): Int

    @Query("DELETE FROM watchlist WHERE providerId = 'unknown' AND profileId = :profileId")
    suspend fun deleteUnknownProvider(profileId: String): Int
}
