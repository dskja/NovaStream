package com.novastream.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.novastream.app.R
import com.novastream.app.di.DownloadEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Foreground service for offline downloads (v12).
 */
@UnstableApi
class DownloadForegroundService : Media3DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0
) {

    private val downloadHelper: DownloadManagerHelper by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadEntryPoint::class.java
        ).downloadManagerHelper()
    }

    override fun getDownloadManager(): DownloadManager = downloadHelper.downloadManager

    override fun getScheduler() = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val downloading = downloads.filter { it.state == Download.STATE_DOWNLOADING }
        val active = downloading.size
        val text = when {
            notMetRequirements and Requirements.NETWORK != 0 ->
                getString(R.string.download_waiting_network)
            active == 1 -> {
                val percent = downloading.first().percentDownloaded
                if (percent >= 0f) {
                    getString(R.string.download_progress_fmt, percent.toInt())
                } else {
                    getString(R.string.download_in_progress_fmt, active)
                }
            }
            active > 0 -> getString(R.string.download_in_progress_fmt, active)
            else -> getString(R.string.download_queue_idle)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(active > 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "novastream_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 2001

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
        }
    }
}
