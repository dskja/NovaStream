package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline download queue entry (v12).
 * Tracks Media3 DownloadManager state for direct (non-DRM) streams.
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["profileId"]),
        Index(value = ["createdAt"])
    ]
)
data class DownloadEntity(
    @PrimaryKey
    val downloadId: String,
    val profileId: String = ProfileEntity.DEFAULT_ID,
    val providerId: String,
    val slug: String,
    val title: String,
    val episodeTitle: String = "",
    val season: Int = 1,
    val episode: Int = 1,
    val coverUrl: String? = null,
    val streamUrl: String,
    val mimeType: String = "application/x-mpegURL",
    val hosterName: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val contentLength: Long = 0L,
    val localPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    val progressPercent: Int
        get() = if (contentLength <= 0L) 0
        else ((bytesDownloaded * 100) / contentLength).toInt().coerceIn(0, 100)

    companion object {
        fun key(providerId: String, slug: String, season: Int, episode: Int): String =
            "$providerId|$slug|S$season|E$episode"
    }
}

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED, PAUSED, REMOVED
}
