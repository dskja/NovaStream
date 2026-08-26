package com.novastream.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.model.Series
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = false,
    val popular: List<Series> = emptyList(),
    val newest: List<Series> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val watchlist: List<WatchlistItem> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repo = NovaStreamRepository()
    private val watchRepo = WatchRepository(application)

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // Collect continue watching + watchlist reactively
        viewModelScope.launch {
            try {
                watchRepo.watchProgress().collect { progress ->
                    _state.update { it.copy(continueWatching = progress.filter { p -> !p.isCompleted }) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "watchProgress flow error", e)
            }
        }
        viewModelScope.launch {
            try {
                watchRepo.watchlist().collect { list ->
                    _state.update { it.copy(watchlist = list) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "watchlist flow error", e)
            }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repo.loadHome()) {
                is NovaStreamRepository.RepoResult.Success -> {
                    val series = res.data
                    val popular = series.take(15)
                    val newest = if (series.size > 15) series.drop(15).takeLast(20) else emptyList()
                    _state.update {
                        it.copy(
                            loading = false,
                            popular = popular,
                            newest = newest,
                            error = null
                        )
                    }
                }
                is NovaStreamRepository.RepoResult.Error ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun removeContinueWatching(episodeKey: String) {
        viewModelScope.launch { watchRepo.removeProgress(episodeKey) }
    }
}
