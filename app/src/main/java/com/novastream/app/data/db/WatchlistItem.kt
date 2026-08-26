package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Watchlist-Eintrag: eine Serie die der User schauen möchte.
 */
@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey
    val slug: String,                // Serien-Slug
    val title: String,
    val coverUrl: String?,
    val addedAt: Long = System.currentTimeMillis()
)
