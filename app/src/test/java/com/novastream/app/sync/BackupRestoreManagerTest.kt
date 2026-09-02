package com.novastream.app.sync

import com.google.gson.Gson
import com.novastream.app.data.db.ProfileEntity
import com.novastream.app.data.db.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreManagerTest {

    @Test
    fun backupPayloadV2IncludesProfiles() {
        val payload = BackupRestoreManager.BackupPayload(
            version = BackupRestoreManager.BACKUP_VERSION,
            profiles = listOf(
                BackupRestoreManager.ProfileBackup(
                    profileId = ProfileEntity.DEFAULT_ID,
                    displayName = "Default",
                    isActive = true
                )
            ),
            watchlist = listOf(
                WatchlistItem(
                    itemKey = "default|kinoger|slug-a",
                    profileId = ProfileEntity.DEFAULT_ID,
                    providerId = "kinoger",
                    slug = "slug-a",
                    title = "Title",
                    coverUrl = null
                )
            )
        )
        val json = Gson().toJson(payload)
        val parsed = Gson().fromJson(json, BackupRestoreManager.BackupPayload::class.java)
        assertEquals(2, parsed.version)
        assertEquals(1, parsed.profiles.size)
        assertEquals(1, parsed.watchlist.size)
    }

    @Test
    fun legacyV1PayloadHasEmptyProfiles() {
        val json = """{"version":1,"watchlist":[],"progress":[]}"""
        val parsed = Gson().fromJson(json, BackupRestoreManager.BackupPayload::class.java)
        assertEquals(1, parsed.version)
        assertTrue(parsed.profiles.isEmpty())
    }
}
