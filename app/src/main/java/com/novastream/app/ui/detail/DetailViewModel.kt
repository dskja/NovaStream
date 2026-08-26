package com.novastream.app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.db.WatchProgress
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
    val inWatchlist: Boolean = false,
    val episodeProgress: Map<String, WatchProgress> = emptyMap(),
    val currentProgress: WatchProgress? = null
) {
    val selectedSeason: Season?
        get() = seasons.getOrNull(selectedSeasonIndex)
}

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val slug: String = checkNotNull(savedStateHandle.get<String>("slug")) { "slug required" }
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
        // Watch all progress for this series
        viewModelScope.launch {
            watchRepo.watchProgress().collect { progressList ->
                val progressMap = progressList.associateBy { it.episodeKey }
                val current = progressList
                    .filter { it.slug == slug && !it.isCompleted }
                    .maxByOrNull { it.updatedAt }
                _state.update { it.copy(episodeProgress = progressMap, currentProgress = current) }
            }
        }
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            try {
                when (val res = repo.loadSeriesDetail(slug)) {
                    is NovaStreamRepository.RepoResult.Success -> {
                        val (series, seasons) = res.data
                        _state.update {
                            it.copy(loading = false, series = series, seasons = seasons, error = null)
                        }
                        val firstWithEps = seasons.indexOfFirst { it.episodes.isNotEmpty() }
                        if (firstWithEps >= 0) {
                            _state.update { it.copy(selectedSeasonIndex = firstWithEps) }
                        } else if (seasons.isNotEmpty()) {
                            _state.update { it.copy(selectedSeasonIndex = 0) }
                            loadSeasonEpisodes(seasons.first().number)
                        }
                    }
                    is NovaStreamRepository.RepoResult.Error ->
                        _state.update { it.copy(loading = false, error = res.message) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("DetailVM", "load error", e)
                _state.update { it.copy(loading = false, error = "Fehler beim Laden") }
            }
        }
    }

    fun selectSeason(index: Int) {
        if (_state.value.loadingSeason) return  // Prevent concurrent loads
        if (index < 0 || index >= _state.value.seasons.size) return  // Validate index
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

    fun removeProgress(episodeKey: String) {
        viewModelScope.launch { watchRepo.removeProgress(episodeKey) }
    }

    /** Markiert eine Episode als gesehen (>90%) oder entfernt den Status. */
    fun toggleEpisodeWatched(season: Int, episode: Int, episodeTitle: String) {
        val key = "$slug-$season-$episode"
        val existing = _state.value.episodeProgress[key]
        viewModelScope.launch {
            if (existing != null && existing.isCompleted) {
                // Already completed - remove progress
                watchRepo.removeProgress(key)
            } else {
                // Mark as completed (100%)
                val series = _state.value.series
                watchRepo.saveProgress(
                    slug = slug,
                    seriesTitle = series?.title ?: "",
                    coverUrl = series?.coverUrl,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    positionMs = 1L,
                    durationMs = 1L  // 100% progress
                )
            }
        }
    }

    private fun loadSeasonEpisodes(seasonNum: Int) {
        _state.update { it.copy(loadingSeason = true) }
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("DetailVM", "loadSeason error", e)
                _state.update { it.copy(loadingSeason = false, error = "Staffel konnte nicht geladen werden") }
            }
        }
    }
}
