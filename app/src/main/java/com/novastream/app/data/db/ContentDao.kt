package com.novastream.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContentEntity)

    @Query(
        """
        SELECT * FROM content_mapping
        WHERE canonicalKey = :canonicalKey AND providerId != :excludeProviderId
        ORDER BY providerId ASC
        """
    )
    suspend fun findByCanonicalKeyExcluding(
        canonicalKey: String,
        excludeProviderId: String
    ): List<ContentEntity>

    @Query("SELECT * FROM content_mapping WHERE providerId = :providerId AND slug = :slug LIMIT 1")
    suspend fun findByProviderSlug(providerId: String, slug: String): ContentEntity?
}
