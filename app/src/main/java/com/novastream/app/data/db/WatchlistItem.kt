package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ProviderUrls

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
        Index(value = ["slug"]),
        Index(value = ["profileId"])
    ]
)
data class WatchlistItem(
    @PrimaryKey
    val itemKey: String,             // "{profileId}|{providerId}|{slug}"
    val profileId: String = ProfileEntity.DEFAULT_ID,
    val providerId: String = "",
    val slug: String,
    val title: String,
    val coverUrl: String?,
    val isMovie: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    /** Tri-state adult flag from scraper/meta when added or enriched. */
    val isAdult: Boolean? = null,
    /** Comma-separated genre list for kids filtering. */
    val genres: String? = null
) {
    fun toSeries(): Series = Series(
        id = slug,
        title = title,
        coverUrl = coverUrl,
        detailUrl = ProviderUrls.detailUrl(providerId.ifBlank { "unknown" }, slug, isMovie),
        isMovie = isMovie,
        providerId = providerId,
        genres = parseGenres(),
        isAdult = isAdult
    )

    fun parseGenres(): List<String> = genres?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    val hasCover: Boolean get() = !coverUrl.isNullOrBlank()

    val initials: String
        get() = title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "—"

    val ageInDays: Int
        get() = ((System.currentTimeMillis() - addedAt) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0)

    companion object {
        fun key(profileId: String, providerId: String, slug: String): String =
            "$profileId|$providerId|$slug"

        fun genresToCsv(genres: List<String>): String? =
            genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",")
    }
}
