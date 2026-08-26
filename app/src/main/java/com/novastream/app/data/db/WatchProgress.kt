package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Speichert den Wiedergabefortschritt einer Episode.
 * Wird für "Continue Watching" verwendet.
 */
@Entity(tableName = "watch_progress")
data class WatchProgress(
    @PrimaryKey
    val episodeKey: String,          // "{slug}-{season}-{episode}" z.B. "reacher-1-1"
    val slug: String,                // Serien-Slug
    val seriesTitle: String,         // Serien-Titel (für Anzeige)
    val coverUrl: String?,           // Cover-URL (für Anzeige)
    val season: Int,
    val episode: Int,
    val episodeTitle: String,        // Episoden-Titel
    val positionMs: Long,            // Aktuelle Position in Millisekunden
    val durationMs: Long,            // Gesamtdauer in Millisekunden
    val updatedAt: Long = System.currentTimeMillis()  // Letztes Update
) {
    /** Fortschritt in Prozent (0-100) */
    val progressPercent: Float
        get() = if (durationMs > 0 && positionMs >= 0) (positionMs.toFloat() / durationMs * 100f).coerceIn(0f, 100f) else 0f

    /** Ob die Episode als "gesehen" gilt (>90% geschaut) */
    val isCompleted: Boolean
        get() = progressPercent >= 90f
}
