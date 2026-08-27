package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Speichert den Wiedergabefortschritt einer Episode.
 * Wird für "Continue Watching" verwendet.
 */
@Entity(
    tableName = "watch_progress",
    indices = [
        Index(value = ["slug", "season", "episode"]),
        Index(value = ["updatedAt"])
    ]
)
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

    /** Ob die Episode als "gesehen" gilt (>=90% geschaut) */
    val isCompleted: Boolean
        get() = progressPercent >= 90f

    /** Verbleibende Millisekunden bis zum Ende. */
    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)

    /** Verbleibende Zeit in Minuten (gerundet). */
    val remainingMinutes: Int
        get() = (remainingMs / 60_000L).toInt()

    /** True wenn die Episode gerade erst begonnen wurde (<5%). */
    val isNearStart: Boolean
        get() = progressPercent < 5f

    /** True wenn die Episode fast fertig ist (>=80% aber <90%). */
    val isNearEnd: Boolean
        get() = progressPercent >= 80f && progressPercent < 90f

    /** Display-Format: "S1E2 - Title". */
    val episodeDisplay: String
        get() = "S${season}E${episode} - $episodeTitle"
}
