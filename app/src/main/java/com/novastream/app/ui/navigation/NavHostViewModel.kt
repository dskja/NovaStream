package com.novastream.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NavHostViewModel @Inject constructor(
    watchRepository: WatchRepository,
    providerController: ProviderController
) : ViewModel() {

    val watchlistCount: StateFlow<Int> = combine(
        watchRepository.watchlist(),
        providerController.activeProviderId
    ) { items, providerId ->
        items.count {
            it.providerId.isBlank() || it.providerId == providerId || it.providerId == "unknown"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
