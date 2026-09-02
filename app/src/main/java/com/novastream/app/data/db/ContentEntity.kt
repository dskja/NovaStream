package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * Persistent slug ↔ external ID mapping for cross-provider discovery ("Also on").
 * Uses free metadata IDs only (IMDb, TVMaze, AniList, Wikidata).
 */
@Entity(
    tableName = "content_mapping",
    primaryKeys = ["providerId", "slug"],
    indices = [
        Index("canonicalKey"),
        Index("imdbId"),
        Index("tvmazeId"),
        Index("anilistId")
    ]
)
data class ContentEntity(
    val slug: String,
    val providerId: String,
    val contentType: String,
    val canonicalKey: String,
    val imdbId: String? = null,
    val tvmazeId: String? = null,
    val anilistId: Int? = null,
    val wikidataId: String? = null
) {
    companion object {
        const val TYPE_TV = "tv"
        const val TYPE_MOVIE = "movie"

        fun fromExternalIds(
            slug: String,
            providerId: String,
            contentType: String,
            imdbId: String? = null,
            tvmazeId: String? = null,
            anilistId: Int? = null,
            wikidataId: String? = null,
            tmdbId: Int? = null,
            idMal: Int? = null,
            canonicalKeyOverride: String? = null
        ): ContentEntity? {
            val ids = com.novastream.app.data.meta.ExternalIds(
                imdbId = imdbId,
                tvmazeId = tvmazeId,
                anilistId = anilistId,
                wikidataId = wikidataId,
                tmdbId = tmdbId,
                idMal = idMal
            )
            val key = canonicalKeyOverride?.takeIf { it.isNotBlank() } ?: ids.canonicalKey() ?: return null
            return ContentEntity(
                slug = slug,
                providerId = providerId,
                contentType = contentType,
                canonicalKey = key,
                imdbId = imdbId,
                tvmazeId = tvmazeId,
                anilistId = anilistId,
                wikidataId = wikidataId
            )
        }
    }
}
