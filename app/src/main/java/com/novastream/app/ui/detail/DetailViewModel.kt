package com.novastream.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.meta.FreeMetaService
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val currentProgress: WatchProgress? = null,
    val metaCast: List<com.novastream.app.data.meta.MetaPerson> = emptyList(),
    val metaRating: Double? = null,
    val metaNetwork: String? = null,
    val imdbId: String? = null,
    val trailerUrl: String? = null,
    val relatedTitles: List<Series> = emptyList()
) {
    val selectedSeason: Season?
        get() = seasons.getOrNull(selectedSeasonIndex)

    /** Count of watched episodes in the currently selected season. */
    val selectedSeasonWatchedCount: Int
        get() {
            val season = selectedSeason ?: return 0
            val seriesSlug = series?.id ?: return 0
            return season.episodes.count { ep ->
                episodeProgress.values.any {
                    it.slug == seriesSlug && it.season == season.number && it.episode == ep.number && it.isCompleted
                }
            }
        }

    /** Total episode count across all seasons. */
    val totalEpisodeCount: Int
        get() = seasons.sumOf { it.episodes.size }

    /** Total watched episodes across all seasons. */
    val totalWatchedCount: Int
        get() {
            val seriesSlug = series?.id ?: return 0
            return seasons.sumOf { season ->
                season.episodes.count { ep ->
                    episodeProgress.values.any {
                        it.slug == seriesSlug && it.season == season.number && it.episode == ep.number && it.isCompleted
                    }
                }
            }
        }

    /** True if any season has episodes loaded. */
    val hasEpisodes: Boolean
        get() = seasons.any { it.episodes.isNotEmpty() }

    fun progressFor(season: Int, episode: Int): WatchProgress? {
        val seriesSlug = series?.id ?: return null
        val pid = ActiveProvider.id
        return episodeProgress.values.find {
            it.slug == seriesSlug &&
                it.season == season &&
                it.episode == episode &&
                (it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown")
        }
    }
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: NovaStreamRepository,
    private val watchRepo: WatchRepository
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle.get<String>("slug")) { "slug required" }

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
                val pid = ActiveProvider.id
                val progressMap = progressList
                    .filter { it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown" }
                    .associateBy { it.episodeKey }
                val current = progressList
                    .filter {
                        it.slug == slug &&
                            (it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown") &&
                            !it.isCompleted
                    }
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
                        enrichMetadata(series)
                        loadRelatedTitles(series)
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
                _state.update { it.copy(loading = false, error = com.novastream.app.util.ErrorMapper.toUserMessage(e)) }
            }
        }
    }

    fun selectSeason(index: Int) {
        if (index < 0 || index >= _state.value.seasons.size) return  // Validate index
        val current = _state.value
        if (current.loadingSeason) return  // Prevent concurrent loads
        if (current.selectedSeasonIndex == index) return  // Already selected
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
                watchRepo.addToWatchlist(
                    slug = series.id,
                    title = series.title,
                    coverUrl = series.coverUrl,
                    isMovie = series.isMovie
                )
            }
        }
    }

    fun removeProgress(episodeKey: String) {
        viewModelScope.launch { watchRepo.removeProgress(episodeKey) }
    }

    /** Markiert eine Episode als gesehen (>90%) oder entfernt den Status. */
    fun toggleEpisodeWatched(season: Int, episode: Int, episodeTitle: String) {
        val existing = _state.value.progressFor(season, episode)
        viewModelScope.launch {
            if (existing != null && existing.isCompleted) {
                watchRepo.removeProgress(existing.episodeKey)
            } else {
                val series = _state.value.series
                watchRepo.saveProgress(
                    slug = slug,
                    seriesTitle = series?.title ?: "",
                    coverUrl = series?.coverUrl,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    positionMs = 1L,
                    durationMs = 1L,
                    isMovie = series?.isMovie == true
                )
            }
        }
    }

    /** Markiert alle Episoden einer Staffel als gesehen. */
    fun markSeasonAsWatched(season: Int) {
        val seasonObj = _state.value.seasons.find { it.number == season } ?: return
        val series = _state.value.series ?: return
        viewModelScope.launch {
            seasonObj.episodes.forEach { ep ->
                val existing = _state.value.progressFor(season, ep.number)
                if (existing == null || !existing.isCompleted) {
                    watchRepo.saveProgress(
                        slug = slug,
                        seriesTitle = series.title,
                        coverUrl = series.coverUrl,
                        season = season,
                        episode = ep.number,
                        episodeTitle = ep.title,
                        positionMs = 1L,
                        durationMs = 1L,
                        isMovie = series.isMovie
                    )
                }
            }
        }
    }

    /** Entfernt den "gesehen" Status für alle Episoden einer Staffel. */
    fun markSeasonAsUnwatched(season: Int) {
        val seasonObj = _state.value.seasons.find { it.number == season } ?: return
        viewModelScope.launch {
            seasonObj.episodes.forEach { ep ->
                _state.value.progressFor(season, ep.number)?.let { watchRepo.removeProgress(it.episodeKey) }
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
                _state.update { it.copy(loadingSeason = false, error = com.novastream.app.util.ErrorMapper.toUserMessage(e)) }
            }
        }
    }

    /** Reichert Detail mit kostenloser TVMaze-Metadata an (kein API-Key). */
    private fun enrichMetadata(series: Series) {
        viewModelScope.launch {
            try {
                val preferAnime = ActiveProvider.isAniWorld ||
                    series.genres.any { it.contains("anime", true) } ||
                    series.detailUrl.contains("/anime/")
                val meta = when {
                    series.id.all { it.isDigit() } ->
                        FreeMetaService.show(series.id)
                    else ->
                        FreeMetaService.enrichByTitle(series.title, preferAnime = preferAnime)
                } ?: return@launch

                // Keine falschen Matches übernehmen
                if (!series.id.all { it.isDigit() } &&
                    !FreeMetaService.titlesSimilar(series.title, meta.title)
                ) {
                    return@launch
                }

                val enriched = series.copy(
                    description = series.description?.takeIf { it.isNotBlank() } ?: meta.summary,
                    // Provider-Cover haben Vorrang – Meta nur als Fallback
                    coverUrl = series.coverUrl ?: meta.posterUrl,
                    backdropUrl = series.backdropUrl ?: meta.backdropUrl,
                    // Genres vom Provider behalten; Meta nur ergänzen wenn leer
                    genres = series.genres.ifEmpty {
                        if (preferAnime && meta.genres.none { it.contains("anime", true) }) {
                            emptyList()
                        } else meta.genres
                    },
                    year = series.year ?: meta.year,
                    rating = series.rating ?: meta.rating?.let { String.format("%.1f", it) },
                    status = series.status ?: meta.status
                )
                _state.update {
                    it.copy(
                        series = enriched,
                        metaCast = meta.cast,
                        metaRating = meta.rating,
                        metaNetwork = meta.network,
                        imdbId = meta.imdbId,
                        trailerUrl = meta.trailerUrl
                    )
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("DetailVM", "meta enrich failed", e)
            }
        }
    }

    private fun loadRelatedTitles(series: Series) {
        viewModelScope.launch {
            try {
                val genre = series.genres.firstOrNull() ?: return@launch
                val provider = ActiveProvider.get()
                val genreSlug = provider.availableGenres
                    .firstOrNull { g ->
                        g.name.equals(genre, ignoreCase = true) ||
                            g.slug.equals(genre, ignoreCase = true) ||
                            genre.contains(g.name, ignoreCase = true)
                    }
                    ?.slug
                    ?: genre.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

                val related = when (val res = repo.loadGenre(genreSlug)) {
                    is NovaStreamRepository.RepoResult.Success ->
                        res.data.filter { it.id != series.id }.distinctBy { it.id }.take(20)
                    else -> emptyList()
                }
                if (related.isNotEmpty()) {
                    _state.update { it.copy(relatedTitles = related) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("DetailVM", "related titles failed", e)
            }
        }
    }
}