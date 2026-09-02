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

    @Query(
        """
        SELECT * FROM content_mapping
        WHERE canonicalKey = :canonicalKey AND providerId = :providerId
        LIMIT 1
        """
    )
    suspend fun findByCanonicalKeyAndProvider(canonicalKey: String, providerId: String): ContentEntity?

    @Query(
        """
        SELECT * FROM content_mapping
        WHERE providerId = :providerId AND imdbId = :imdbId
        LIMIT 1
        """
    )
    suspend fun findByImdbAndProvider(imdbId: String, providerId: String): ContentEntity?

    @Query(
        """
        SELECT * FROM content_mapping
        WHERE providerId = :providerId AND tvmazeId = :tvmazeId
        LIMIT 1
        """
    )
    suspend fun findByTvmazeAndProvider(tvmazeId: String, providerId: String): ContentEntity?

    @Query(
        """
        SELECT * FROM content_mapping
        WHERE providerId = :providerId AND anilistId = :anilistId
        LIMIT 1
        """
    )
    suspend fun findByAnilistAndProvider(anilistId: Int, providerId: String): ContentEntity?

    @Query(
        """
        SELECT * FROM content_mapping
        WHERE providerId != :excludeProviderId AND (
            (:imdbId IS NOT NULL AND imdbId = :imdbId) OR
            (:tvmazeId IS NOT NULL AND tvmazeId = :tvmazeId) OR
            (:anilistId IS NOT NULL AND anilistId = :anilistId) OR
            (:wikidataId IS NOT NULL AND wikidataId = :wikidataId)
        )
        """
    )
    suspend fun findRelatedExcluding(
        excludeProviderId: String,
        imdbId: String?,
        tvmazeId: String?,
        anilistId: Int?,
        wikidataId: String?
    ): List<ContentEntity>

    @Query("SELECT * FROM content_mapping WHERE providerId = :providerId AND slug = :slug LIMIT 1")
    suspend fun findByProviderSlug(providerId: String, slug: String): ContentEntity?
}
