package com.novastream.app.profile

import android.content.Context
import com.novastream.app.download.DownloadManagerHelper
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-profile manager with PIN protection (PBKDF2, v14+).
 */
class ProfileManager(
    private val context: Context,
    private val db: NovaStreamDatabase,
    private val downloadHelper: DownloadManagerHelper? = null
) {
    private val profileDao = db.profileDao()
    private val pinFailures = ConcurrentHashMap<String, PinFailureState>()

    fun observeProfiles(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    fun activeProfileId(): Flow<String> = profileDao.observeAll().map { list ->
        list.firstOrNull { it.isActive }?.profileId ?: ProfileEntity.DEFAULT_ID
    }

    fun observeActiveProfile(): Flow<ProfileEntity> = profileDao.observeAll().map { list ->
        list.firstOrNull { it.isActive } ?: ProfileEntity.defaultProfile()
    }

    fun isKidsProfile(): Flow<Boolean> =
        observeActiveProfile().map { it.isKids }.distinctUntilChanged()

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
            pinHash = pin?.takeIf { it.isNotBlank() }?.let { PinHasher.hash(it) },
            isKids = isKids
        )
        profileDao.upsert(profile)
        return profile
    }

    suspend fun switchProfile(profileId: String, pin: String? = null): Boolean {
        val profile = profileDao.getById(profileId) ?: return false
        if (profile.requiresPin) {
            if (isPinLocked(profileId)) return false
            if (!PinHasher.verify(pin.orEmpty(), profile.pinHash)) {
                recordPinFailure(profileId)
                return false
            }
            clearPinFailures(profileId)
            if (PinHasher.shouldUpgrade(profile.pinHash)) {
                profileDao.upsert(profile.copy(pinHash = PinHasher.hash(pin.orEmpty())))
            }
        }
        profileDao.setActive(profileId)
        return true
    }

    suspend fun setPin(profileId: String, pin: String?) {
        val profile = profileDao.getById(profileId) ?: return
        profileDao.upsert(
            profile.copy(
                pinHash = pin?.takeIf { it.isNotBlank() }?.let { PinHasher.hash(it) }
            )
        )
        clearPinFailures(profileId)
    }

    suspend fun deleteProfile(profileId: String) {
        if (profileId == ProfileEntity.DEFAULT_ID) return
        db.watchlistDao().deleteAllForProfileId(profileId)
        db.watchProgressDao().deleteForProfile(profileId)
        if (downloadHelper != null) {
            downloadHelper.removeDownloadsForProfile(profileId)
        } else {
            db.downloadDao().deleteForProfile(profileId)
        }
        profileDao.delete(profileId)
        clearPinFailures(profileId)
        ensureDefaultProfile()
        if (profileDao.getActive() == null) {
            profileDao.setActive(ProfileEntity.DEFAULT_ID)
        }
    }

    private fun isPinLocked(profileId: String): Boolean {
        val state = pinFailures[profileId] ?: return false
        if (System.currentTimeMillis() - state.windowStartMs > PIN_WINDOW_MS) {
            pinFailures.remove(profileId)
            return false
        }
        return state.failures >= MAX_PIN_FAILURES
    }

    private fun recordPinFailure(profileId: String) {
        val now = System.currentTimeMillis()
        pinFailures.compute(profileId) { _, existing ->
            if (existing == null || now - existing.windowStartMs > PIN_WINDOW_MS) {
                PinFailureState(failures = 1, windowStartMs = now)
            } else {
                existing.copy(failures = existing.failures + 1)
            }
        }
    }

    private fun clearPinFailures(profileId: String) {
        pinFailures.remove(profileId)
    }

    private data class PinFailureState(val failures: Int, val windowStartMs: Long)

    companion object {
        private const val MAX_PIN_FAILURES = 5
        private const val PIN_WINDOW_MS = 5 * 60 * 1000L
    }
}
