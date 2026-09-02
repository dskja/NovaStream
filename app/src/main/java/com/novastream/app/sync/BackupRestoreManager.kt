package com.novastream.app.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.ProfileEntity
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Privacy-first backup/restore — local JSON export/import with multi-profile support (v2).
 */
class BackupRestoreManager(
    private val context: Context,
    private val db: NovaStreamDatabase,
    private val gson: Gson = Gson()
) {

    data class ProfileBackup(
        @SerializedName("profileId") val profileId: String,
        @SerializedName("displayName") val displayName: String,
        @SerializedName("avatarEmoji") val avatarEmoji: String = "👤",
        @SerializedName("pinHash") val pinHash: String? = null,
        @SerializedName("isKids") val isKids: Boolean = false,
        @SerializedName("isActive") val isActive: Boolean = false,
        @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()
    )

    data class BackupPayload(
        @SerializedName("version") val version: Int = BACKUP_VERSION,
        @SerializedName("exportedAt") val exportedAt: Long = System.currentTimeMillis(),
        @SerializedName("profiles") val profiles: List<ProfileBackup> = emptyList(),
        @SerializedName("watchlist") val watchlist: List<WatchlistItem> = emptyList(),
        @SerializedName("progress") val progress: List<WatchProgress> = emptyList(),
        @SerializedName("uiLocale") val uiLocale: String? = null,
        @SerializedName("contentLanguage") val contentLanguage: String? = null,
        @SerializedName("activeProviderId") val activeProviderId: String? = null
    )

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val settings = AppSettings(context)
        val profiles = db.profileDao().getAllOnce().map { profile ->
            ProfileBackup(
                profileId = profile.profileId,
                displayName = profile.displayName,
                avatarEmoji = profile.avatarEmoji,
                pinHash = profile.pinHash,
                isKids = profile.isKids,
                isActive = profile.isActive,
                createdAt = profile.createdAt
            )
        }
        val payload = BackupPayload(
            profiles = profiles,
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

            if (payload.version >= 2 && payload.profiles.isNotEmpty()) {
                importProfiles(payload, merge)
            } else {
                importLegacyV1(payload, merge)
            }

            val settings = AppSettings(context)
            payload.uiLocale?.let { settings.setUiLocale(it) }
            payload.contentLanguage?.let { settings.setContentLanguage(it) }
            payload.activeProviderId?.let { ProviderManager.setActiveProvider(context, it) }

            ImportResult.Success(
                watchlistCount = payload.watchlist.size,
                progressCount = payload.progress.size,
                profileCount = payload.profiles.size
            )
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Import failed")
        }
    }

    private suspend fun importProfiles(payload: BackupPayload, merge: Boolean) {
        if (!merge) {
            payload.profiles.forEach { backup ->
                if (backup.profileId != ProfileEntity.DEFAULT_ID) {
                    db.watchlistDao().deleteAllForProfileId(backup.profileId)
                    db.watchProgressDao().deleteForProfile(backup.profileId)
                }
            }
        }
        payload.profiles.forEach { backup ->
            db.profileDao().upsert(
                ProfileEntity(
                    profileId = backup.profileId,
                    displayName = backup.displayName,
                    avatarEmoji = backup.avatarEmoji,
                    pinHash = backup.pinHash,
                    isActive = backup.isActive,
                    isKids = backup.isKids,
                    createdAt = backup.createdAt
                )
            )
        }
        db.profileDao().getActive() ?: db.profileDao().setActive(ProfileEntity.DEFAULT_ID)

        payload.watchlist.forEach { item ->
            val profileId = item.profileId.ifBlank { ProfileEntity.DEFAULT_ID }
            db.watchlistDao().upsert(
                item.copy(
                    profileId = profileId,
                    itemKey = WatchlistItem.key(profileId, item.providerId, item.slug)
                )
            )
        }
        payload.progress.forEach { progress ->
            val profileId = progress.profileId.ifBlank { ProfileEntity.DEFAULT_ID }
            db.watchProgressDao().upsert(
                progress.copy(
                    profileId = profileId,
                    episodeKey = WatchProgress.key(
                        profileId,
                        progress.providerId,
                        progress.slug,
                        progress.season,
                        progress.episode
                    )
                )
            )
        }
    }

    private suspend fun importLegacyV1(payload: BackupPayload, merge: Boolean) {
        if (!merge) {
            val activeProfileId = db.profileDao().getActive()?.profileId ?: ProfileEntity.DEFAULT_ID
            db.watchlistDao().deleteAllForProfileId(activeProfileId)
            db.watchProgressDao().deleteForProfile(activeProfileId)
        }
        val activeProfileId = db.profileDao().getActive()?.profileId ?: ProfileEntity.DEFAULT_ID
        payload.watchlist.forEach { item ->
            db.watchlistDao().upsert(
                item.copy(
                    profileId = activeProfileId,
                    itemKey = WatchlistItem.key(activeProfileId, item.providerId, item.slug)
                )
            )
        }
        payload.progress.forEach { progress ->
            db.watchProgressDao().upsert(
                progress.copy(
                    profileId = activeProfileId,
                    episodeKey = WatchProgress.key(
                        activeProfileId,
                        progress.providerId,
                        progress.slug,
                        progress.season,
                        progress.episode
                    )
                )
            )
        }
    }

    sealed class ImportResult {
        data class Success(
            val watchlistCount: Int,
            val progressCount: Int,
            val profileCount: Int = 0
        ) : ImportResult()

        data class Error(val message: String) : ImportResult()
    }

    companion object {
        const val BACKUP_VERSION = 2
    }
}
