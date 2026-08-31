package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ActiveProvider

/**
 * Watchlist-Eintrag: eine Serie/Film die der User schauen möchte.
 * Primary Key ist provider-scoped, damit gleiche Slugs verschiedener Provider
 * sich nicht überschreiben.
 */
@Entity(
    tableName = "watchlist",
    indices = [
        Index(value = ["addedAt"]),
        Index(value = ["providerId"]),
        Index(value = ["slug"])
    ]
)
data class WatchlistItem(
    @PrimaryKey
    val itemKey: String,             // "{providerId}|{slug}"
    val providerId: String = "",
    val slug: String,
    val title: String,
    val coverUrl: String?,
    val isMovie: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSeries(): Series = Series(
        id = slug,
        title = title,
        coverUrl = coverUrl,
        detailUrl = if (isMovie) "/movie/$slug" else ActiveProvider.seriesDetailUrl(slug),
        isMovie = isMovie,
        providerId = providerId
    )

    val hasCover: Boolean get() = !coverUrl.isNullOrBlank()

    val initials: String
        get() = title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "—"

    val ageInDays: Int
        get() = ((System.currentTimeMillis() - addedAt) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0)

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

    companion object {
        fun key(providerId: String, slug: String): String = "$providerId|$slug"
    }
}
