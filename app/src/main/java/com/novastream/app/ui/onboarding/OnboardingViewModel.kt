package com.novastream.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.data.provider.ProviderInfo
import com.novastream.app.data.provider.ProviderManager
import com.novastream.app.data.provider.ProviderRegistry
import com.novastream.app.profile.ProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val providerController: ProviderController,
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _registryReady = MutableStateFlow(false)
    val registryReady: StateFlow<Boolean> = _registryReady.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { ProviderRegistry.ensureBuilt() }
            _registryReady.value = true
        }
    }

    fun providerGroups(languageFilter: ContentLanguage?): Map<ContentLanguage, List<ProviderInfo>> {
        return if (languageFilter != null) {
            mapOf(languageFilter to ProviderManager.getFilteredProviderInfos(language = languageFilter, favoriteIds = emptySet()))
        } else {
            ProviderManager.getProviderInfosGroupedByLanguage()
        }
    }

    suspend fun setupProfile(displayName: String) {
        profileManager.ensureDefaultProfile()
        val trimmed = displayName.trim()
        if (trimmed.isNotBlank()) {
            val profile = profileManager.createProfile(trimmed, pin = null)
            profileManager.switchProfile(profile.profileId)
        }
    }
}
