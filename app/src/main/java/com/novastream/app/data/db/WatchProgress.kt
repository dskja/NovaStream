package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Speichert den Wiedergabefortschritt einer Episode / eines Films.
 * episodeKey ist provider-scoped: "{providerId}|{slug}-{season}-{episode}"
 */
@Entity(
    tableName = "watch_progress",
    indices = [
        Index(value = ["slug", "season", "episode"]),
        Index(value = ["updatedAt"]),
        Index(value = ["slug"]),
        Index(value = ["providerId"]),
        Index(value = ["profileId"])
    ]
)
data class WatchProgress(
    @PrimaryKey
    val episodeKey: String,
    val profileId: String = ProfileEntity.DEFAULT_ID,
    val providerId: String = "",
    val slug: String,
    val seriesTitle: String,
    val coverUrl: String?,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val isMovie: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Adult flag from meta when progress was saved (kids filter). */
    val isAdult: Boolean? = null
) {
    val progressPercent: Float
        get() = if (durationMs > 0 && positionMs >= 0) (positionMs.toFloat() / durationMs * 100f).coerceIn(0f, 100f) else 0f

    val isCompleted: Boolean
        get() = progressPercent >= 90f

    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)

    val remainingMinutes: Int
        get() = (remainingMs / 60_000L).toInt()

    val isNearStart: Boolean
        get() = progressPercent < 5f

    val isNearEnd: Boolean
        get() = progressPercent >= 80f && progressPercent < 90f

    val episodeDisplay: String
        get() = if (isMovie) episodeTitle.ifBlank { seriesTitle }
        else "S${season}E${episode} - $episodeTitle"

    companion object {
        fun key(profileId: String, providerId: String, slug: String, season: Int, episode: Int): String =
            "$profileId|$providerId|$slug-$season-$episode"
    }
}
