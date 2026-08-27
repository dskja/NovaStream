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
    val isRefreshing: Boolean = false,
    val hero: List<Series> = emptyList(),
    val popular: List<Series> = emptyList(),
    val newest: List<Series> = emptyList(),
    val trending: List<Series> = emptyList(),
    val action: List<Series> = emptyList(),
    val comedy: List<Series> = emptyList(),
    val drama: List<Series> = emptyList(),
    val scifi: List<Series> = emptyList(),
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
        // Continue watching reactive
        viewModelScope.launch {
            try {
                watchRepo.watchProgress().collect { progress ->
                    _state.update { it.copy(continueWatching = progress.filter { p -> !p.isCompleted }) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "watchProgress flow error", e)
            }
        }
        // Watchlist reactive
        viewModelScope.launch {
            try {
                watchRepo.watchlist().collect { list ->
                    _state.update { it.copy(watchlist = list) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "watchlist flow error", e)
            }
        }
        // Provider changes - reload home
        viewModelScope.launch {
            try {
                com.novastream.app.data.provider.ProviderManager.activeProviderIdFlow(application).collect { providerId ->
                    if (_state.value.popular.isNotEmpty() || _state.value.error != null) {
                        load()
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "provider flow error", e)
            }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                when (val res = repo.loadHome()) {
                    is NovaStreamRepository.RepoResult.Success -> {
                        val series = res.data
                        // Verteile Serien auf mehrere Sektionen für eine längere Homepage
                        val hero = series.take(8)
                        val popular = series.take(20)
                        val newest = series.drop(20).take(20)
                        val trending = series.drop(40).take(20)
                        // Genre-Verteilung: Round-Robin statt Title-Matching (zuverlässiger)
                        // Nimmt Serien aus dem restlichen Pool und verteilt sie cyclisch
                        val rest = series.drop(60).ifEmpty { series.drop(20) }
                        val action = rest.filterIndexed { i, _ -> i % 4 == 0 }.take(15)
                        val comedy = rest.filterIndexed { i, _ -> i % 4 == 1 }.take(15)
                        val drama = rest.filterIndexed { i, _ -> i % 4 == 2 }.take(15)
                        val scifi = rest.filterIndexed { i, _ -> i % 4 == 3 }.take(15)

                        _state.update {
                            it.copy(
                                loading = false,
                                isRefreshing = false,
                                hero = hero,
                                popular = popular,
                                newest = newest,
                                trending = trending,
                                action = action,
                                comedy = comedy,
                                drama = drama,
                                scifi = scifi,
                                error = null
                            )
                        }
                    }
                    is NovaStreamRepository.RepoResult.Error ->
                        _state.update { it.copy(loading = false, isRefreshing = false, error = res.message) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "load error", e)
                _state.update { it.copy(loading = false, isRefreshing = false, error = com.novastream.app.util.ErrorMapper.toUserMessage(e)) }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true, error = null) }
        load()
    }

    fun removeContinueWatching(episodeKey: String) {
        viewModelScope.launch { watchRepo.removeProgress(episodeKey) }
    }
}
