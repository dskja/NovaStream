package com.novastream.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.model.Genre
import com.novastream.app.data.model.LatestEpisode
import com.novastream.app.data.model.Series
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import com.novastream.app.util.ProviderLoadMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
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
    val providerId: String = "",
    val providerName: String = "",
    val catalogHint: String? = null,
    val supportsMovies: Boolean = false,
    val supportsSeries: Boolean = true,
    val uniqueTitleCount: Int = 0,
    val hero: List<Series> = emptyList(),
    val popular: List<Series> = emptyList(),
    val newest: List<Series> = emptyList(),
    val trending: List<Series> = emptyList(),
    val movies: List<Series> = emptyList(),
    val genreRows: List<Pair<Genre, List<Series>>> = emptyList(),
    val action: List<Series> = emptyList(),
    val comedy: List<Series> = emptyList(),
    val drama: List<Series> = emptyList(),
    val scifi: List<Series> = emptyList(),
    val latestEpisodes: List<LatestEpisode> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val watchlist: List<WatchlistItem> = emptyList(),
    val reduceMotion: Boolean = false,
    val performanceMode: Boolean = false,
    val lastLoadDurationMs: Long? = null,
    val showProviderHealthWarning: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: NovaStreamRepository,
    private val watchRepo: WatchRepository,
    private val appSettings: AppSettings,
    private val providerController: ProviderController
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var genreJob: Job? = null
    private var activeProviderId: String? = null

    init {
        viewModelScope.launch {
            try {
                watchRepo.watchProgress().collect { progress ->
                    val pid = ActiveProvider.id
                    _state.update {
                        it.copy(
                            continueWatching = progress.filter { p ->
                                !p.isCompleted && (p.providerId.isBlank() || p.providerId == pid)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "watchProgress flow error", e)
            }
        }
        viewModelScope.launch {
            try {
                watchRepo.watchlist().collect { list ->
                    val pid = ActiveProvider.id
                    _state.update {
                        it.copy(
                            watchlist = list.filter { w ->
                                w.providerId.isBlank() || w.providerId == pid
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "watchlist flow error", e)
            }
        }
        viewModelScope.launch {
            try {
                appSettings.reduceMotion.collect { enabled ->
                    _state.update { it.copy(reduceMotion = enabled) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "reduceMotion flow error", e)
            }
        }
        viewModelScope.launch {
            try {
                appSettings.performanceMode.collect { enabled ->
                    _state.update { it.copy(performanceMode = enabled) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "performanceMode flow error", e)
            }
        }
        viewModelScope.launch {
            try {
                providerController.activeProviderId.collect { providerId ->
                    if (activeProviderId != providerId) {
                        activeProviderId = providerId
                        clearCatalogForProviderSwitch(providerId)
                        load(force = true)
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "provider flow error", e)
                load(force = true)
            }
        }
    }

    private fun clearCatalogForProviderSwitch(providerId: String) {
        loadJob?.cancel()
        genreJob?.cancel()
        viewModelScope.launch {
            repo.clearCacheForProvider(providerId)
        }
        val provider = ActiveProvider.get()
        _state.update {
            it.copy(
                loading = true,
                error = null,
                providerId = providerId,
                providerName = provider.displayName,
                catalogHint = provider.catalogHint,
                supportsMovies = provider.supportsMovies,
                supportsSeries = provider.supportsSeries,
                uniqueTitleCount = 0,
                hero = emptyList(),
                popular = emptyList(),
                newest = emptyList(),
                trending = emptyList(),
                movies = emptyList(),
                genreRows = emptyList(),
                action = emptyList(),
                comedy = emptyList(),
                drama = emptyList(),
                scifi = emptyList(),
                latestEpisodes = emptyList(),
                lastLoadDurationMs = null,
                showProviderHealthWarning = false
            )
        }
    }

    fun load(force: Boolean = false) {
        if (!force && loadJob?.isActive == true) return
        loadJob?.cancel()
        genreJob?.cancel()
        val expectedProvider = ActiveProvider.id
        _state.update {
            it.copy(
                loading = true,
                error = null,
                providerId = expectedProvider,
                providerName = ActiveProvider.displayName,
                catalogHint = ActiveProvider.catalogHint,
                supportsMovies = ActiveProvider.supportsMovies,
                supportsSeries = ActiveProvider.supportsSeries
            )
        }
        loadJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                coroutineScope {
                    val provider = ActiveProvider.get()
                    if (provider.id != expectedProvider) return@coroutineScope

                    val catalogDef = async { repo.loadHomeCatalog() }
                    val latestDef = async { repo.loadLatestEpisodes() }

                    when (val catalogRes = catalogDef.await()) {
                        is NovaStreamRepository.RepoResult.Success -> {
                            if (ActiveProvider.id != expectedProvider) return@coroutineScope
                            val catalog = catalogRes.data
                            val latest = latestDef.await().okList()

                            val popular = catalog.popular.ifEmpty { catalog.all.take(24) }
                            val newest = catalog.newest.ifEmpty { catalog.all.drop(8).take(24) }
                            val movies = catalog.all.filter { it.isMovie }
                            val trending = catalog.trending.ifEmpty {
                                catalog.topShows.ifEmpty { popular.take(20) }
                            }
                            val merged = catalog.flattened().distinctBy { it.id }

                            val durationMs = System.currentTimeMillis() - startedAt
                            ProviderLoadMetrics.recordLoad(expectedProvider, durationMs)
                            _state.update {
                                it.copy(
                                    loading = false,
                                    isRefreshing = false,
                                    providerId = expectedProvider,
                                    providerName = provider.displayName,
                                    catalogHint = provider.catalogHint,
                                    uniqueTitleCount = merged.size,
                                    hero = catalog.hero.ifEmpty { merged.take(8) },
                                    popular = popular,
                                    newest = newest,
                                    trending = trending,
                                    movies = movies,
                                    genreRows = emptyList(),
                                    action = emptyList(),
                                    comedy = emptyList(),
                                    drama = emptyList(),
                                    scifi = emptyList(),
                                    latestEpisodes = latest.ifEmpty { catalog.latestEpisodes },
                                    lastLoadDurationMs = durationMs,
                                    showProviderHealthWarning = ProviderLoadMetrics.shouldShowHealthWarning(durationMs, false),
                                    error = null
                                )
                            }
                            if (!_state.value.performanceMode) {
                                loadGenreRowsDeferred(provider, expectedProvider)
                            }
                        }
                        is NovaStreamRepository.RepoResult.Error -> {
                            if (ActiveProvider.id != expectedProvider) return@coroutineScope
                            val durationMs = System.currentTimeMillis() - startedAt
                            ProviderLoadMetrics.recordLoad(expectedProvider, durationMs)
                            _state.update {
                                it.copy(
                                    loading = false,
                                    isRefreshing = false,
                                    error = catalogRes.message,
                                    lastLoadDurationMs = durationMs,
                                    showProviderHealthWarning = true
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "load error", e)
                val durationMs = System.currentTimeMillis() - startedAt
                ProviderLoadMetrics.recordLoad(expectedProvider, durationMs)
                _state.update {
                    it.copy(
                        loading = false,
                        isRefreshing = false,
                        error = com.novastream.app.util.ErrorMapper.toUserMessage(e),
                        lastLoadDurationMs = durationMs,
                        showProviderHealthWarning = true
                    )
                }
            }
        }
    }

    private fun loadGenreRowsDeferred(
        provider: com.novastream.app.data.provider.StreamingProvider,
        expectedProvider: String
    ) {
        genreJob?.cancel()
        genreJob = viewModelScope.launch {
            try {
                val genres = provider.availableGenres.take(2)
                if (genres.isEmpty()) return@launch
                val genreRows = genres.mapNotNull { genre ->
                    when (val res = repo.loadGenre(genre.slug)) {
                        is NovaStreamRepository.RepoResult.Success -> {
                            val list = res.data.filter { it.belongsToActiveProvider() || it.providerId == null }
                            if (list.isEmpty()) null else genre to list
                        }
                        else -> null
                    }
                }
                if (ActiveProvider.id != expectedProvider) return@launch
                val action = genreRows.find { it.first.slug.contains("action", true) }?.second.orEmpty()
                val comedy = genreRows.find {
                    it.first.slug.contains("comedy", true) || it.first.slug.contains("komödie", true)
                }?.second.orEmpty()
                val drama = genreRows.find { it.first.slug.contains("drama", true) }?.second.orEmpty()
                val scifi = genreRows.find {
                    it.first.slug.contains("science", true) || it.first.slug.contains("fantasy", true)
                }?.second.orEmpty()
                _state.update {
                    it.copy(
                        genreRows = genreRows,
                        action = action,
                        comedy = comedy,
                        drama = drama,
                        scifi = scifi
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("HomeVM", "genre rows failed", e)
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true, error = null) }
        load(force = true)
    }

    fun removeContinueWatching(episodeKey: String) {
        viewModelScope.launch { watchRepo.removeProgress(episodeKey) }
    }

    private fun NovaStreamRepository.RepoResult<List<LatestEpisode>>.okList(): List<LatestEpisode> =
        (this as? NovaStreamRepository.RepoResult.Success)?.data.orEmpty()
}
