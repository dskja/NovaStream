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
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ContentLanguageGenres
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import com.novastream.app.profile.ProfileManager
import com.novastream.app.util.ErrorMapper
import com.novastream.app.util.KidsContentFilter
import com.novastream.app.util.ProviderLoadMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
    val iptvEnabled: Boolean = false,
    val lastLoadDurationMs: Long? = null,
    val showProviderHealthWarning: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: NovaStreamRepository,
    private val watchRepo: WatchRepository,
    private val appSettings: AppSettings,
    private val providerController: ProviderController,
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var genreJob: Job? = null
    private var activeProviderId: String? = null
    private var kidsMode: Boolean = false

    init {
        viewModelScope.launch {
            try {
                watchRepo.watchProgress().collect { progress ->
                    val pid = ActiveProvider.id
                    _state.update {
                        it.copy(
                            continueWatching = KidsContentFilter.filterProgress(
                                progress.filter { p ->
                                    !p.isCompleted && (
                                        p.providerId.isBlank() ||
                                            p.providerId == pid ||
                                            p.providerId == "unknown"
                                        )
                                },
                                kidsMode
                            )
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
                            watchlist = KidsContentFilter.filterWatchlist(
                                list.filter { w ->
                                    w.providerId.isBlank() || w.providerId == pid
                                },
                                kidsMode
                            )
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
                appSettings.iptvEnabled.collect { enabled ->
                    _state.update { it.copy(iptvEnabled = enabled) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "iptvEnabled flow error", e)
            }
        }
        viewModelScope.launch {
            try {
                profileManager.isKidsProfile().collect { isKids ->
                    if (kidsMode != isKids) {
                        kidsMode = isKids
                        if (activeProviderId != null) load(force = true)
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "kids profile flow error", e)
            }
        }
        viewModelScope.launch {
            try {
                providerController.isReady.first { it }
                providerController.activeProviderId.collect { providerId ->
                    if (activeProviderId != providerId) {
                        activeProviderId = providerId
                        clearCatalogForProviderSwitch(providerId)
                        load(force = true)
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("HomeVM", "provider flow error", e)
                _state.update {
                    it.copy(
                        loading = false,
                        isRefreshing = false,
                        error = ErrorMapper.toUserMessage(e)
                    )
                }
            }
        }
    }

    private fun clearCatalogForProviderSwitch(providerId: String) {
        loadJob?.cancel()
        genreJob?.cancel()
        val provider = ActiveProvider.get()
        _state.update {
            it.copy(
                loading = true,
                isRefreshing = true,
                error = null,
                providerId = providerId,
                providerName = provider.displayName,
                catalogHint = provider.catalogHint,
                supportsMovies = provider.supportsMovies,
                supportsSeries = provider.supportsSeries,
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
                uniqueTitleCount = 0,
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

                    val performanceMode = _state.value.performanceMode
                    val catalogDef = async { repo.loadHomeCatalog() }
                    val latestDef = if (performanceMode) {
                        null
                    } else {
                        async { repo.loadLatestEpisodes() }
                    }

                    when (val catalogRes = catalogDef.await()) {
                        is NovaStreamRepository.RepoResult.Success -> {
                            if (ActiveProvider.id != expectedProvider) return@coroutineScope
                            val catalog = KidsContentFilter.filterHomeCatalog(catalogRes.data, kidsMode)
                            val processed = withContext(Dispatchers.Default) {
                                val popular = catalog.popular.ifEmpty { catalog.all.take(24) }
                                val newest = catalog.newest.ifEmpty { catalog.all.drop(8).take(24) }
                                val movies = catalog.all.filter { it.isMovie }
                                val trending = catalog.trending.ifEmpty {
                                    catalog.topShows.ifEmpty { popular.take(20) }
                                }
                                val merged = catalog.flattened().distinctBy { it.id }
                                ProcessedCatalog(
                                    popular = popular,
                                    newest = newest,
                                    movies = movies,
                                    trending = trending,
                                    merged = merged,
                                    hero = catalog.hero.ifEmpty { merged.take(8) },
                                    fallbackLatest = catalog.latestEpisodes
                                )
                            }

                            val durationMs = System.currentTimeMillis() - startedAt
                            ProviderLoadMetrics.recordLoad(expectedProvider, durationMs)
                            _state.update {
                                it.copy(
                                    loading = false,
                                    isRefreshing = false,
                                    providerId = expectedProvider,
                                    providerName = provider.displayName,
                                    catalogHint = provider.catalogHint,
                                    uniqueTitleCount = processed.merged.size,
                                    hero = processed.hero,
                                    popular = processed.popular,
                                    newest = processed.newest,
                                    trending = processed.trending,
                                    movies = processed.movies,
                                    genreRows = emptyList(),
                                    action = emptyList(),
                                    comedy = emptyList(),
                                    drama = emptyList(),
                                    scifi = emptyList(),
                                    latestEpisodes = processed.fallbackLatest,
                                    lastLoadDurationMs = durationMs,
                                    showProviderHealthWarning = ProviderLoadMetrics.shouldShowHealthWarning(durationMs, false),
                                    error = null
                                )
                            }
                            if (!performanceMode) {
                                loadGenreRowsDeferred(provider, expectedProvider)
                            }
                            latestDef?.let { deferred ->
                                launch {
                                    val latest = deferred.await().okList()
                                    if (ActiveProvider.id != expectedProvider) return@launch
                                    val safe = KidsContentFilter.filterLatestEpisodes(latest, kidsMode)
                                    if (safe.isNotEmpty()) {
                                        _state.update { it.copy(latestEpisodes = safe) }
                                    }
                                }
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
                val contentLang = ContentLanguage.fromTag(appSettings.contentLanguage.first())
                val genres = ContentLanguageGenres.resolveForProvider(provider, contentLang).take(2)
                if (genres.isEmpty()) return@launch
                val genreRows = coroutineScope {
                    genres.map { genre ->
                        async {
                            when (val res = repo.loadGenre(genre.slug)) {
                                is NovaStreamRepository.RepoResult.Success -> {
                                    val list = res.data.filter { it.belongsToActiveProvider() || it.providerId == null }
                                    val safe = KidsContentFilter.filterSeries(list, kidsMode)
                                    if (safe.isEmpty()) null else genre to safe
                                }
                                else -> null
                            }
                        }
                    }.mapNotNull { it.await() }
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

private data class ProcessedCatalog(
    val popular: List<com.novastream.app.data.model.Series>,
    val newest: List<com.novastream.app.data.model.Series>,
    val movies: List<com.novastream.app.data.model.Series>,
    val trending: List<com.novastream.app.data.model.Series>,
    val merged: List<com.novastream.app.data.model.Series>,
    val hero: List<com.novastream.app.data.model.Series>,
    val fallbackLatest: List<LatestEpisode>
)
