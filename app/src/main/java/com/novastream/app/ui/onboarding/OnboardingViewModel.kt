package com.novastream.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.profile.ProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val providerController: ProviderController,
    private val profileManager: ProfileManager
) : ViewModel() {

    suspend fun setupProfile(displayName: String) {
        profileManager.ensureDefaultProfile()
        val trimmed = displayName.trim()
        if (trimmed.isNotBlank()) {
            val profile = profileManager.createProfile(trimmed, pin = null)
            profileManager.switchProfile(profile.profileId)
        }
    }
}
