package com.novastream.app.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.db.ProfileEntity
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Privacy-first backup/restore (v13) — local JSON export/import FIRST.
 */
class BackupRestoreManager(
    private val context: Context,
    private val db: NovaStreamDatabase,
    private val gson: Gson = Gson()
) {

    data class BackupPayload(
        @SerializedName("version") val version: Int = BACKUP_VERSION,
        @SerializedName("exportedAt") val exportedAt: Long = System.currentTimeMillis(),
        @SerializedName("watchlist") val watchlist: List<WatchlistItem> = emptyList(),
        @SerializedName("progress") val progress: List<WatchProgress> = emptyList(),
        @SerializedName("uiLocale") val uiLocale: String? = null,
        @SerializedName("contentLanguage") val contentLanguage: String? = null,
        @SerializedName("activeProviderId") val activeProviderId: String? = null
    )

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val settings = AppSettings(context)
        val payload = BackupPayload(
            watchlist = db.watchlistDao().getAllOnce(),
            progress = db.watchProgressDao().getAllOnce(),
            uiLocale = settings.uiLocale.first(),
            contentLanguage = settings.contentLanguage.first(),
            activeProviderId = ProviderManager.activeProviderIdFlow(context).first()
        )
        gson.toJson(payload)
    }

    suspend fun exportToFile(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "novastream_backup_${System.currentTimeMillis()}.json")
        file.writeText(exportToJson())
        file
    }

    suspend fun importFromJson(json: String, merge: Boolean = true): ImportResult = withContext(Dispatchers.IO) {
        try {
            val payload = gson.fromJson(json, BackupPayload::class.java)
                ?: return@withContext ImportResult.Error("Invalid JSON")
            if (payload.version > BACKUP_VERSION) {
                return@withContext ImportResult.Error("Backup version too new")
            }
            if (!merge) {
                val activeProfileId = db.profileDao().getActive()?.profileId ?: ProfileEntity.DEFAULT_ID
                db.watchlistDao().deleteAllForProfileId(activeProfileId)
                db.watchProgressDao().deleteForProfile(activeProfileId)
            }
            val activeProfileId = db.profileDao().getActive()?.profileId ?: ProfileEntity.DEFAULT_ID
            payload.watchlist.forEach { item ->
                val scoped = item.copy(
                    profileId = activeProfileId,
                    itemKey = WatchlistItem.key(activeProfileId, item.providerId, item.slug)
                )
                db.watchlistDao().upsert(scoped)
            }
            payload.progress.forEach { progress ->
                db.watchProgressDao().upsert(
                    progress.copy(
                        profileId = activeProfileId,
                        episodeKey = WatchProgress.key(activeProfileId, progress.providerId, progress.slug, progress.season, progress.episode)
                    )
                )
            }
            val settings = AppSettings(context)
            payload.uiLocale?.let { settings.setUiLocale(it) }
            payload.contentLanguage?.let { settings.setContentLanguage(it) }
            payload.activeProviderId?.let {
                ProviderManager.setActiveProvider(context, it)
            }
            ImportResult.Success(
                watchlistCount = payload.watchlist.size,
                progressCount = payload.progress.size
            )
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Import failed")
        }
    }

    sealed class ImportResult {
        data class Success(val watchlistCount: Int, val progressCount: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    companion object {
        const val BACKUP_VERSION = 1
    }
}
