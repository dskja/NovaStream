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
            tmdbId: Int? = null
        ): ContentEntity? {
            val key = when {
                !imdbId.isNullOrBlank() -> "imdb:${imdbId.trim()}"
                tmdbId != null && tmdbId > 0 -> "tmdb:$tmdbId"
                !tvmazeId.isNullOrBlank() -> "tvmaze:${tvmazeId.trim()}"
                anilistId != null && anilistId > 0 -> "anilist:$anilistId"
                !wikidataId.isNullOrBlank() -> "wikidata:${wikidataId.trim()}"
                else -> return null
            }
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
