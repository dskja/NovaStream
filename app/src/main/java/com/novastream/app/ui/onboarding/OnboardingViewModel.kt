package com.novastream.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.novastream.app.data.provider.ProviderController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val providerController: ProviderController
) : ViewModel()
