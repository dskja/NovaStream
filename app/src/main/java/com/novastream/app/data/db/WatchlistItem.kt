package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.novastream.app.data.model.Series

/**
 * Watchlist-Eintrag: eine Serie die der User schauen möchte.
 */
@Entity(
    tableName = "watchlist",
    indices = [Index(value = ["addedAt"])]
)
data class WatchlistItem(
    @PrimaryKey
    val slug: String,                // Serien-Slug
    val title: String,
    val coverUrl: String?,
    val addedAt: Long = System.currentTimeMillis()
) {
    /** Konvertiert WatchlistItem zu Series für UI-Komponenten */
    fun toSeries(): Series = Series(
        id = slug,
        title = title,
        coverUrl = coverUrl,
        detailUrl = "/serie/$slug"  // Wird für Navigation nicht genutzt - slug reicht
    )
}

// Extension: WatchlistItem -> Series mit inWatchlist Flag für UI Badges
fun WatchlistItem.toSeriesWithFlag(): Series = toSeries().copy()
