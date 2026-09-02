package com.novastream.app.ui.continuewatching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContinueWatchingUiState(
    val items: List<WatchProgress> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ContinueWatchingViewModel @Inject constructor(
    private val watchRepo: WatchRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ContinueWatchingUiState())
    val state: StateFlow<ContinueWatchingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                watchRepo.watchProgress().collect { progress ->
                    val pid = ActiveProvider.id
                    val items = progress
                        .filter {
                            !it.isCompleted &&
                                (it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown")
                        }
                        .sortedByDescending { it.updatedAt }
                    _state.update { it.copy(items = items, loading = false, error = null) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("ContinueWatchingVM", "flow error", e)
                _state.update {
                    it.copy(
                        loading = false,
                        error = com.novastream.app.util.ErrorMapper.toUserMessage(e)
                    )
                }
            }
        }
    }

    fun remove(episodeKey: String) {
        viewModelScope.launch { watchRepo.removeProgress(episodeKey) }
    }
}
