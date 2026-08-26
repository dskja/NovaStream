package com.novastream.app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = false,
    val series: Series? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeasonIndex: Int = 0,
    val loadingSeason: Boolean = false,
    val error: String? = null,
    val inWatchlist: Boolean = false
) {
    val selectedSeason: Season?
        get() = seasons.getOrNull(selectedSeasonIndex)
}

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val slug: String = checkNotNull(savedStateHandle.get<String>("slug"))
    private val repo = NovaStreamRepository()
    private val watchRepo = WatchRepository(application)

    private val _state = MutableStateFlow(DetailUiState(loading = true))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        // Watch watchlist state
        viewModelScope.launch {
            watchRepo.isInWatchlist(slug).collect { inList ->
                _state.update { it.copy(inWatchlist = inList) }
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val res = repo.loadSeriesDetail(slug)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    val (series, seasons) = res.data
                    _state.update {
                        it.copy(loading = false, series = series, seasons = seasons)
                    }
                    val firstWithEps = seasons.indexOfFirst { it.episodes.isNotEmpty() }
                    if (firstWithEps >= 0) {
                        _state.update { it.copy(selectedSeasonIndex = firstWithEps) }
                    } else if (seasons.isNotEmpty()) {
                        loadSeasonEpisodes(seasons.first().number)
                    }
                }
                is NovaStreamRepository.RepoResult.Error ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun selectSeason(index: Int) {
        _state.update { it.copy(selectedSeasonIndex = index) }
        val season = _state.value.seasons.getOrNull(index)
        if (season != null && season.episodes.isEmpty()) {
            loadSeasonEpisodes(season.number)
        }
    }

    fun toggleWatchlist() {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            if (_state.value.inWatchlist) {
                watchRepo.removeFromWatchlist(series.id)
            } else {
                watchRepo.addToWatchlist(series.id, series.title, series.coverUrl)
            }
        }
    }

    private fun loadSeasonEpisodes(seasonNum: Int) {
        _state.update { it.copy(loadingSeason = true) }
        viewModelScope.launch {
            when (val res = repo.loadSeason(slug, seasonNum)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    _state.update { current ->
                        val updated = current.seasons.map { s ->
                            if (s.number == seasonNum) s.copy(episodes = res.data) else s
                        }
                        current.copy(seasons = updated, loadingSeason = false)
                    }
                }
                is NovaStreamRepository.RepoResult.Error ->
                    _state.update { it.copy(loadingSeason = false, error = res.message) }
            }
        }
    }
}
