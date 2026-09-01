package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room-Cache für Katalog- und Detail-Responses mit TTL. */
@Entity(
    tableName = "catalog_cache",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["expiresAt"])
    ]
)
data class CatalogCacheEntry(
    @PrimaryKey
    val cacheKey: String,
    val providerId: String,
    val cacheType: String,
    val payload: String,
    val cachedAt: Long,
    val expiresAt: Long
) {
    val isExpired: Boolean get() = expiresAt <= System.currentTimeMillis()

    companion object {
        const val TYPE_HOME = "home"
        const val TYPE_CATALOG = "catalog"
        const val TYPE_GENRE = "genre"
        const val TYPE_DETAIL = "detail"
        const val TYPE_SEARCH = "search"
        const val TYPE_LIST = "list"

        fun key(providerId: String, type: String, vararg parts: String): String =
            "$providerId|$type|${parts.joinToString("|")}"
    }
}
