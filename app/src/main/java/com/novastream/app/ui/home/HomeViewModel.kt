package com.novastream.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.model.LatestEpisode
import com.novastream.app.data.model.Series
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val latestEpisodes: List<LatestEpisode> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val watchlist: List<WatchlistItem> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repo = NovaStreamRepository()
    private val watchRepo = WatchRepository.get(application)

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
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
        viewModelScope.launch {
            try {
                com.novastream.app.data.provider.ProviderManager.activeProviderIdFlow(application).collect {
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
                coroutineScope {
                    val catalogDef = async { repo.loadHomeCatalog() }
                    val popularDef = async { repo.loadPopular() }
                    val newestDef = async { repo.loadNewest() }
                    val actionDef = async { repo.loadGenre("action") }
                    val comedyDef = async { repo.loadGenre("comedy") }
                    val dramaDef = async { repo.loadGenre("drama") }
                    val scifiDef = async { repo.loadGenre("science-fiction") }
                    val latestDef = async { repo.loadLatestEpisodes() }

                    when (val catalogRes = catalogDef.await()) {
                        is NovaStreamRepository.RepoResult.Success -> {
                            val catalog = catalogRes.data
                            val popular = popularDef.await().let { r ->
                                (r as? NovaStreamRepository.RepoResult.Success)?.data.orEmpty()
                            }.ifEmpty { catalog.popular.ifEmpty { catalog.all.take(24) } }
                            val newest = newestDef.await().let { r ->
                                (r as? NovaStreamRepository.RepoResult.Success)?.data.orEmpty()
                            }.ifEmpty { catalog.newest.ifEmpty { catalog.all.drop(8).take(24) } }
                            val action = actionDef.await().ok().ifEmpty { catalog.all.filterIndexed { i, _ -> i % 4 == 0 }.take(15) }
                            val comedy = comedyDef.await().ok().ifEmpty { catalog.all.filterIndexed { i, _ -> i % 4 == 1 }.take(15) }
                            val drama = dramaDef.await().ok().ifEmpty { catalog.all.filterIndexed { i, _ -> i % 4 == 2 }.take(15) }
                            val scifi = scifiDef.await().ok().ifEmpty { catalog.all.filterIndexed { i, _ -> i % 4 == 3 }.take(15) }
                            val latest = latestDef.await().okList()

                            _state.update {
                                it.copy(
                                    loading = false,
                                    isRefreshing = false,
                                    hero = catalog.hero.ifEmpty { catalog.all.take(8) },
                                    popular = popular,
                                    newest = newest,
                                    trending = catalog.trending.ifEmpty { catalog.topShows.ifEmpty { popular.take(20) } },
                                    action = action,
                                    comedy = comedy,
                                    drama = drama,
                                    scifi = scifi,
                                    latestEpisodes = latest.ifEmpty { catalog.latestEpisodes },
                                    error = null
                                )
                            }
                        }
                        is NovaStreamRepository.RepoResult.Error -> {
                            // Fallback: flache Home-Liste
                            when (val home = repo.loadHome()) {
                                is NovaStreamRepository.RepoResult.Success -> {
                                    val series = home.data
                                    _state.update {
                                        it.copy(
                                            loading = false,
                                            isRefreshing = false,
                                            hero = series.take(8),
                                            popular = series.take(20),
                                            newest = series.drop(20).take(20),
                                            trending = series.drop(40).take(20),
                                            action = series.drop(60).filterIndexed { i, _ -> i % 4 == 0 }.take(15),
                                            comedy = series.drop(60).filterIndexed { i, _ -> i % 4 == 1 }.take(15),
                                            drama = series.drop(60).filterIndexed { i, _ -> i % 4 == 2 }.take(15),
                                            scifi = series.drop(60).filterIndexed { i, _ -> i % 4 == 3 }.take(15),
                                            error = null
                                        )
                                    }
                                }
                                is NovaStreamRepository.RepoResult.Error ->
                                    _state.update {
                                        it.copy(loading = false, isRefreshing = false, error = home.message)
                                    }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "load error", e)
                _state.update {
                    it.copy(
                        loading = false,
                        isRefreshing = false,
                        error = com.novastream.app.util.ErrorMapper.toUserMessage(e)
                    )
                }
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

    private fun NovaStreamRepository.RepoResult<List<Series>>.ok(): List<Series> =
        (this as? NovaStreamRepository.RepoResult.Success)?.data.orEmpty()

    private fun NovaStreamRepository.RepoResult<List<LatestEpisode>>.okList(): List<LatestEpisode> =
        (this as? NovaStreamRepository.RepoResult.Success)?.data.orEmpty()
}
