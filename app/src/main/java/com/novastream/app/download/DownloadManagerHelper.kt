package com.novastream.app.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            addListener(downloadListener)
        }.also {
            scope.launch { syncAllFromMedia3() }
        }
    }

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            scope.launch { syncDownload(download, finalException) }
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
        val id = DownloadEntity.key(profileId, providerId, slug, season, episode)
        val secureUrl = com.novastream.app.util.MediaUrls.playbackUrl(source.url)
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
            streamUrl = secureUrl,
            mimeType = source.mimeType,
            hosterName = source.hoster,
            status = DownloadStatus.QUEUED
        )
        downloadDao.upsert(entity)

        val request = DownloadRequest.Builder(id, android.net.Uri.parse(secureUrl))
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

    suspend fun retryDownload(entity: DownloadEntity) {
        downloadManager.removeDownload(entity.downloadId)
        val request = DownloadRequest.Builder(entity.downloadId, android.net.Uri.parse(entity.streamUrl))
            .setMimeType(entity.mimeType)
            .setCustomCacheKey(entity.downloadId)
            .build()
        downloadDao.upsert(
            entity.copy(
                status = DownloadStatus.QUEUED,
                bytesDownloaded = 0L,
                contentLength = 0L,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        )
        downloadManager.addDownload(request)
        Media3DownloadService.sendAddDownload(
            context,
            DownloadForegroundService::class.java,
            request,
            false
        )
    }

    suspend fun removeDownload(id: String) {
        downloadManager.removeDownload(id)
        downloadDao.delete(id)
    }

    suspend fun getStorageUsedBytes(profileId: String? = null): Long =
        if (profileId.isNullOrBlank()) downloadDao.totalDownloadedBytes()
        else downloadDao.totalDownloadedBytes(profileId)

    /** Cache-aware data source for ExoPlayer — reads completed downloads offline. */
    fun cacheDataSourceFactory(): CacheDataSource.Factory {
        downloadManager // ensure initialized
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent(NovaStreamConfig.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun resolvePlaybackUrl(entity: DownloadEntity): String {
        if (entity.status != DownloadStatus.COMPLETED) return entity.streamUrl
        val download = runCatching { downloadManager.downloadIndex.getDownload(entity.downloadId) }.getOrNull()
        if (download != null && download.state == Download.STATE_COMPLETED) {
            return download.request.uri.toString()
        }
        return entity.localPath?.takeIf { it.isNotBlank() } ?: entity.streamUrl
    }

    suspend fun getDownloadCount(): Int = downloadDao.count()

    private suspend fun syncAllFromMedia3() {
        downloadManager.currentDownloads.forEach { syncDownload(it, null) }
    }

    private suspend fun syncDownload(download: Download, error: Exception?) {
        val existing = downloadDao.getById(download.request.id) ?: return
        val status = mapStatus(download.state)
        val localPath = if (status == DownloadStatus.COMPLETED) {
            download.request.uri.toString()
        } else {
            existing.localPath
        }
        downloadDao.updateProgress(
            id = download.request.id,
            status = status,
            bytesDownloaded = download.bytesDownloaded,
            contentLength = download.contentLength,
            errorMessage = error?.localizedMessage,
            localPath = localPath,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun mapStatus(state: Int): DownloadStatus = when (state) {
        Download.STATE_QUEUED -> DownloadStatus.QUEUED
        Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
        Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
        Download.STATE_FAILED -> DownloadStatus.FAILED
        Download.STATE_STOPPED -> DownloadStatus.PAUSED
        Download.STATE_REMOVING -> DownloadStatus.REMOVED
        else -> DownloadStatus.QUEUED
    }

    fun release() {
        runCatching { downloadManager.release() }
        runCatching { simpleCache.release() }
    }
}
