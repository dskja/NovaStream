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

    /** True wenn ein Cover-Bild vorhanden ist. */
    val hasCover: Boolean get() = !coverUrl.isNullOrBlank()

    /** Initialen des Titels für Fallback-Anzeige (max 2 Zeichen). */
    val initials: String
        get() = title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "—"

    /** Alter des Eintrags in Tagen. */
    val ageInDays: Int
        get() = ((System.currentTimeMillis() - addedAt) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0)

    /** Formatiertes Datum (z.B. "vor 3 Tagen"). */
    val addedRelative: String
        get() = when (ageInDays) {
            0 -> "Heute hinzugefügt"
            1 -> "Gestern hinzugefügt"
            in 2..6 -> "Vor $ageInDays Tagen hinzugefügt"
            in 7..13 -> "Vor einer Woche hinzugefügt"
            in 14..29 -> "Vor ${ageInDays / 7} Wochen hinzugefügt"
            in 30..364 -> "Vor ${ageInDays / 30} Monaten hinzugefügt"
            else -> "Vor über einem Jahr hinzugefügt"
        }
}
