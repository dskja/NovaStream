package com.novastream.app.profile

import android.content.Context
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

/**
 * Multi-profile manager with PIN protection (v13).
 */
class ProfileManager(
    private val context: Context,
    private val db: NovaStreamDatabase
) {
    private val profileDao = db.profileDao()

    fun observeProfiles(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    fun activeProfileId(): Flow<String> = profileDao.observeAll().map { list ->
        list.firstOrNull { it.isActive }?.profileId ?: ProfileEntity.DEFAULT_ID
    }

    suspend fun ensureDefaultProfile() {
        if (profileDao.count() == 0) {
            profileDao.upsert(ProfileEntity.defaultProfile())
        }
    }

    suspend fun getActiveProfile(): ProfileEntity {
        ensureDefaultProfile()
        return profileDao.getActive() ?: ProfileEntity.defaultProfile()
    }

    suspend fun createProfile(name: String, pin: String? = null, isKids: Boolean = false): ProfileEntity {
        val id = "profile_${System.currentTimeMillis()}"
        val profile = ProfileEntity(
            profileId = id,
            displayName = name,
            pinHash = pin?.let { hashPin(it) },
            isKids = isKids
        )
        profileDao.upsert(profile)
        return profile
    }

    suspend fun switchProfile(profileId: String, pin: String? = null): Boolean {
        val profile = profileDao.getById(profileId) ?: return false
        if (profile.requiresPin && hashPin(pin.orEmpty()) != profile.pinHash) return false
        profileDao.setActive(profileId)
        return true
    }

    suspend fun setPin(profileId: String, pin: String?) {
        val profile = profileDao.getById(profileId) ?: return
        profileDao.upsert(profile.copy(pinHash = pin?.let { hashPin(it) }))
    }

    suspend fun deleteProfile(profileId: String) {
        if (profileId == ProfileEntity.DEFAULT_ID) return
        profileDao.delete(profileId)
        ensureDefaultProfile()
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
