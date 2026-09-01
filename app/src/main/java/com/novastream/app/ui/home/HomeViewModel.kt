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
import com.novastream.app.data.provider.capabilities
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.novastream.app.util.ProviderLoadMetrics
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
                latestEpisodes = emptyList()
            )
        }
    }

    fun load(force: Boolean = false) {
        if (!force && loadJob?.isActive == true) return
        loadJob?.cancel()
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

                    val performanceMode = _state.value.performanceMode
                    val catalogDef = async { repo.loadHomeCatalog() }
                    val popularDef = async { repo.loadPopular() }
                    val newestDef = async { repo.loadNewest() }
                    val moviesDef = async {
                        if (provider.supportsMovies) repo.loadMovies() else null
                    }
                    val extendedDef = if (performanceMode) null else async { repo.loadExtendedCatalog() }
                    val genres = if (performanceMode) emptyList() else provider.availableGenres.take(4)
                    val genreDefs = genres.map { g ->
                        g to async { repo.loadGenre(g.slug) }
                    }
                    val latestDef = if (performanceMode || !provider.capabilities().supportsLatestEpisodes) {
                        null
                    } else {
                        async { repo.loadLatestEpisodes() }
                    }

                    when (val catalogRes = catalogDef.await()) {
                        is NovaStreamRepository.RepoResult.Success -> {
                            if (ActiveProvider.id != expectedProvider) return@coroutineScope
                            val catalog = catalogRes.data
                            val extended = extendedDef?.await()?.ok().orEmpty()
                            val popular = popularDef.await().ok()
                                .ifEmpty { catalog.popular.ifEmpty { catalog.all.take(24) } }
                            val newest = newestDef.await().ok()
                                .ifEmpty { catalog.newest.ifEmpty { catalog.all.drop(8).take(24) } }
                            val movies = moviesDef.await()?.ok().orEmpty()
                                .ifEmpty { catalog.all.filter { it.isMovie } }
                            val genreRows = genreDefs.mapNotNull { (genre, def) ->
                                val list = def.await().ok()
                                if (list.isEmpty()) null else genre to list
                            }
                            // Legacy fields for HomeScreen rows that still use them
                            val action = genreRows.find { it.first.slug.contains("action", true) }?.second.orEmpty()
                            val comedy = genreRows.find { it.first.slug.contains("comedy", true) || it.first.slug.contains("komödie", true) }?.second.orEmpty()
                            val drama = genreRows.find { it.first.slug.contains("drama", true) }?.second.orEmpty()
                            val scifi = genreRows.find {
                                it.first.slug.contains("science", true) || it.first.slug.contains("fantasy", true)
                            }?.second.orEmpty()
                            val latest = latestDef?.await()?.okList().orEmpty()

                            val merged = (catalog.flattened() + popular + newest + movies + extended +
                                genreRows.flatMap { it.second }).distinctBy { it.id }

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
                                    popular = popular.ifEmpty { extended.take(24) },
                                    newest = newest,
                                    trending = catalog.trending.ifEmpty {
                                        catalog.topShows.ifEmpty { popular.take(20) }
                                    },
                                    movies = movies,
                                    genreRows = genreRows,
                                    action = action,
                                    comedy = comedy,
                                    drama = drama,
                                    scifi = scifi,
                                    latestEpisodes = latest.ifEmpty { catalog.latestEpisodes },
                                    lastLoadDurationMs = durationMs,
                                    showProviderHealthWarning = ProviderLoadMetrics.shouldShowHealthWarning(durationMs, false),
                                    error = null
                                )
                            }
                        }
                        is NovaStreamRepository.RepoResult.Error -> {
                            if (ActiveProvider.id != expectedProvider) return@coroutineScope
                            when (val home = repo.loadHome()) {
                                is NovaStreamRepository.RepoResult.Success -> {
                                    val series = home.data
                                    val movies = moviesDef.await()?.ok().orEmpty()
                                        .ifEmpty { series.filter { it.isMovie } }
                                    val durationMs = System.currentTimeMillis() - startedAt
                                    ProviderLoadMetrics.recordLoad(expectedProvider, durationMs)
                                    _state.update {
                                        it.copy(
                                            loading = false,
                                            isRefreshing = false,
                                            uniqueTitleCount = (series + movies).distinctBy { s -> s.id }.size,
                                            hero = series.take(8),
                                            popular = series.take(20),
                                            newest = series.drop(20).take(20),
                                            trending = series.drop(40).take(20),
                                            movies = movies,
                                            genreRows = emptyList(),
                                            action = emptyList(),
                                            comedy = emptyList(),
                                            drama = emptyList(),
                                            scifi = emptyList(),
                                            lastLoadDurationMs = durationMs,
                                            showProviderHealthWarning = ProviderLoadMetrics.shouldShowHealthWarning(durationMs, false),
                                            error = null
                                        )
                                    }
                                }
                                is NovaStreamRepository.RepoResult.Error -> {
                                    val durationMs = System.currentTimeMillis() - startedAt
                                    ProviderLoadMetrics.recordLoad(expectedProvider, durationMs)
                                    _state.update {
                                        it.copy(
                                            loading = false,
                                            isRefreshing = false,
                                            error = home.message,
                                            lastLoadDurationMs = durationMs,
                                            showProviderHealthWarning = true
                                        )
                                    }
                                }
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

    fun refresh() {
        _state.update { it.copy(isRefreshing = true, error = null) }
        load(force = true)
    }

    fun removeContinueWatching(episodeKey: String) {
        viewModelScope.launch { watchRepo.removeProgress(episodeKey) }
    }

    private fun NovaStreamRepository.RepoResult<List<Series>>.ok(): List<Series> =
        (this as? NovaStreamRepository.RepoResult.Success)?.data.orEmpty()
            .filter { it.belongsToActiveProvider() || it.providerId == null }

    private fun NovaStreamRepository.RepoResult<List<LatestEpisode>>.okList(): List<LatestEpisode> =
        (this as? NovaStreamRepository.RepoResult.Success)?.data.orEmpty()
}
