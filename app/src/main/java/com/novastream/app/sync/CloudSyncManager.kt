package com.novastream.app.sync

import android.content.Context
import android.util.Base64
import com.novastream.app.data.prefs.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Optional anonymous cloud sync via user-supplied REST/WebDAV URL (v13).
 */
class CloudSyncManager(
    private val context: Context,
    private val backupRestore: BackupRestoreManager,
    private val appSettings: AppSettings,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    suspend fun pushToRemote(): SyncResult = withContext(Dispatchers.IO) {
        val url = appSettings.syncUrl.first()
        if (url.isBlank()) return@withContext SyncResult.Error("No sync URL configured")
        try {
            val json = backupRestore.exportToJson()
            val deviceKey = appSettings.syncDeviceKey.first()
            val request = Request.Builder()
                .url(buildRemoteUrl(url, "novastream_backup.json"))
                .put(json.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Basic ${Base64.encodeToString(":$deviceKey".toByteArray(), Base64.NO_WRAP)}")
                .header("User-Agent", "NovaStream/15.0")
                .build()
            val resp = client.newCall(request).execute()
            if (resp.isSuccessful) SyncResult.Success("Uploaded backup")
            else SyncResult.Error("Upload failed: HTTP ${resp.code}")
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun pullFromRemote(merge: Boolean = true): SyncResult = withContext(Dispatchers.IO) {
        val url = appSettings.syncUrl.first()
        if (url.isBlank()) return@withContext SyncResult.Error("No sync URL configured")
        try {
            val deviceKey = appSettings.syncDeviceKey.first()
            val request = Request.Builder()
                .url(buildRemoteUrl(url, "novastream_backup.json"))
                .get()
                .header("Authorization", "Basic ${Base64.encodeToString(":$deviceKey".toByteArray(), Base64.NO_WRAP)}")
                .header("User-Agent", "NovaStream/15.0")
                .build()
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) return@withContext SyncResult.Error("Download failed: HTTP ${resp.code}")
            val json = resp.body?.string() ?: return@withContext SyncResult.Error("Empty response")
            when (val result = backupRestore.importFromJson(json, merge)) {
                is BackupRestoreManager.ImportResult.Success ->
                    SyncResult.Success("Restored ${result.watchlistCount} watchlist + ${result.progressCount} progress items")
                is BackupRestoreManager.ImportResult.Error -> SyncResult.Error(result.message)
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Download failed")
        }
    }

    private fun buildRemoteUrl(baseUrl: String, fileName: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith(".json")) trimmed else "$trimmed/$fileName"
    }

    sealed class SyncResult {
        data class Success(val message: String) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }
}
