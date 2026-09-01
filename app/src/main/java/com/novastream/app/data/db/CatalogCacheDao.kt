package com.novastream.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CatalogCacheDao {

    @Query("SELECT * FROM catalog_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): CatalogCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CatalogCacheEntry)

    @Query("DELETE FROM catalog_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM catalog_cache WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM catalog_cache WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: String)

    @Query("DELETE FROM catalog_cache")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(LENGTH(payload)), 0) FROM catalog_cache")
    suspend fun totalPayloadBytes(): Long

    @Query("SELECT * FROM catalog_cache ORDER BY cachedAt ASC")
    suspend fun listByOldest(): List<CatalogCacheEntry>
}
