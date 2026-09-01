package com.novastream.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local user profile (v13) — separate watchlists with optional PIN.
 */
@Entity(
    tableName = "profiles",
    indices = [Index(value = ["isActive"])]
)
data class ProfileEntity(
    @PrimaryKey
    val profileId: String,
    val displayName: String,
    val avatarEmoji: String = "👤",
    val pinHash: String? = null,
    val isActive: Boolean = false,
    val isKids: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val requiresPin: Boolean get() = !pinHash.isNullOrBlank()

    companion object {
        const val DEFAULT_ID = "default"
        fun defaultProfile() = ProfileEntity(
            profileId = DEFAULT_ID,
            displayName = "Default",
            avatarEmoji = "👤",
            isActive = true
        )
    }
}
