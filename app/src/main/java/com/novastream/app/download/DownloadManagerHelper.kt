package com.novastream.app.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import com.novastream.app.data.db.DownloadDao
import com.novastream.app.data.db.DownloadEntity
import com.novastream.app.data.db.DownloadStatus
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.data.model.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media3 DownloadManager wrapper (v12).
 * Only supports direct (non-DRM) HLS/MP4 streams.
 */
@Singleton
@UnstableApi
class DownloadManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    db: NovaStreamDatabase
) {
    private val downloadDao: DownloadDao = db.downloadDao()
    private val cacheDir = File(context.filesDir, "downloads_cache")
    private val databaseProvider = StandaloneDatabaseProvider(context)

    val simpleCache: SimpleCache by lazy {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
    }

    val downloadManager: DownloadManager by lazy {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(NovaStreamConfig.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        DownloadManager(
            context,
            databaseProvider,
            simpleCache,
            dataSourceFactory,
            Executors.newFixedThreadPool(2)
        ).apply {
            maxParallelDownloads = 2
        }
    }

    fun observeDownloads(profileId: String) = downloadDao.observeAll(profileId)

    suspend fun enqueueDownload(
        providerId: String,
        slug: String,
        title: String,
        episodeTitle: String,
        season: Int,
        episode: Int,
        coverUrl: String?,
        source: StreamSource,
        profileId: String = com.novastream.app.data.db.ProfileEntity.DEFAULT_ID
    ): String {
        require(source.isPlayable) { "Stream URL not playable" }
        require(!source.url.contains(".m3u8") || source.isHls) {
            "Only direct HLS/MP4 supported"
        }
        val id = DownloadEntity.key(providerId, slug, season, episode)
        val entity = DownloadEntity(
            downloadId = id,
            profileId = profileId,
            providerId = providerId,
            slug = slug,
            title = title,
            episodeTitle = episodeTitle,
            season = season,
            episode = episode,
            coverUrl = coverUrl,
            streamUrl = source.url,
            mimeType = source.mimeType,
            hosterName = source.hoster,
            status = DownloadStatus.QUEUED
        )
        downloadDao.upsert(entity)

        val request = DownloadRequest.Builder(id, android.net.Uri.parse(source.url))
            .setMimeType(source.mimeType)
            .setCustomCacheKey(id)
            .build()
        downloadManager.addDownload(request)
        Media3DownloadService.sendAddDownload(
            context,
            DownloadForegroundService::class.java,
            request,
            false
        )
        return id
    }

    suspend fun removeDownload(id: String) {
        downloadManager.removeDownload(id)
        downloadDao.delete(id)
    }

    suspend fun getStorageUsedBytes(): Long = downloadDao.totalDownloadedBytes()

    suspend fun getDownloadCount(): Int = downloadDao.count()

    fun release() {
        runCatching { downloadManager.release() }
        runCatching { simpleCache.release() }
    }
}
