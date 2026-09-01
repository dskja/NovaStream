package com.novastream.app.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.model.Genre
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ProviderManager
import com.novastream.app.data.repository.NovaStreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrowseContentFilter { ALL, SERIES, MOVIES }

data class BrowseUiState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val items: List<Series> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val selectedGenre: String? = null,
    val contentFilter: BrowseContentFilter = BrowseContentFilter.ALL,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val error: String? = null,
    val providerName: String = "",
    val supportsMovies: Boolean = false,
    val supportsSeries: Boolean = true
)

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NovaStreamRepository()
    private val _state = MutableStateFlow(BrowseUiState(loading = true))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var activeProviderId: String = ActiveProvider.id
    private var allItems: List<Series> = emptyList()

    init {
        viewModelScope.launch {
            ProviderManager.activeProviderIdFlow(application).collect { providerId ->
                ActiveProvider.setById(providerId)
                if (activeProviderId != providerId) {
                    activeProviderId = providerId
                    resetAndLoad()
                }
            }
        }
        resetAndLoad()
    }

    fun refresh() = resetAndLoad()

    fun selectGenre(genre: String?) {
        if (_state.value.selectedGenre == genre) return
        allItems = emptyList()
        _state.update { it.copy(selectedGenre = genre, page = 0, items = emptyList(), hasMore = true, error = null) }
        reloadForFilter(reset = true)
    }

    fun setContentFilter(filter: BrowseContentFilter) {
        if (_state.value.contentFilter == filter) return
        allItems = emptyList()
        _state.update {
            it.copy(
                contentFilter = filter,
                page = 0,
                items = emptyList(),
                hasMore = true,
                error = null
            )
        }
        reloadForFilter(reset = true)
    }

    fun loadMore() {
        if (_state.value.loadingMore || !_state.value.hasMore || _state.value.loading) return
        if (usesMoviesCatalog()) return
        loadPage(reset = false)
    }

    private fun resetAndLoad() {
        val provider = ActiveProvider.get()
        allItems = emptyList()
        _state.update {
            BrowseUiState(
                loading = true,
                genres = provider.availableGenres,
                providerName = provider.displayName,
                supportsMovies = provider.supportsMovies,
                supportsSeries = provider.supportsSeries
            )
        }
        reloadForFilter(reset = true)
    }

    private fun usesMoviesCatalog(): Boolean =
        _state.value.contentFilter == BrowseContentFilter.MOVIES &&
            _state.value.supportsMovies &&
            _state.value.selectedGenre.isNullOrBlank()

    private fun reloadForFilter(reset: Boolean) {
        if (usesMoviesCatalog()) {
            loadMovies(reset)
        } else {
            loadPage(reset)
        }
    }

    private fun loadMovies(reset: Boolean) {
        loadJob?.cancel()
        val expectedProvider = ActiveProvider.id
        _state.update {
            it.copy(
                loading = reset,
                loadingMore = false,
                error = null
            )
        }
        loadJob = viewModelScope.launch {
            when (val result = repo.loadMovies()) {
                is NovaStreamRepository.RepoResult.Success -> {
                    if (ActiveProvider.id != expectedProvider) return@launch
                    allItems = result.data
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            loadingMore = false,
                            items = result.data,
                            page = 0,
                            hasMore = false,
                            error = null
                        )
                    }
                }
                is NovaStreamRepository.RepoResult.Error -> {
                    if (ActiveProvider.id != expectedProvider) return@launch
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            hasMore = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    private fun loadPage(reset: Boolean) {
        loadJob?.cancel()
        val expectedProvider = ActiveProvider.id
        val nextPage = if (reset) 0 else _state.value.page + 1
        _state.update {
            it.copy(
                loading = reset,
                loadingMore = !reset,
                error = null
            )
        }
        loadJob = viewModelScope.launch {
            val genre = _state.value.selectedGenre
            val result = if (!genre.isNullOrBlank()) {
                repo.loadGenrePage(genre, nextPage)
            } else {
                repo.loadCatalogPage(nextPage)
            }
            if (ActiveProvider.id != expectedProvider) return@launch
            when (result) {
                is NovaStreamRepository.RepoResult.Success -> {
                    val merged = if (reset) {
                        result.data
                    } else {
                        (allItems + result.data).distinctBy { it.id }
                    }
                    allItems = merged
                    val filtered = applyContentFilter(merged)
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            loadingMore = false,
                            items = filtered,
                            page = nextPage,
                            hasMore = result.data.isNotEmpty(),
                            error = null
                        )
                    }
                }
                is NovaStreamRepository.RepoResult.Error -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            hasMore = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    private fun applyContentFilter(list: List<Series>, filter: BrowseContentFilter = _state.value.contentFilter): List<Series> = when (filter) {
        BrowseContentFilter.ALL -> list
        BrowseContentFilter.MOVIES -> list.filter { it.isMovie }
        BrowseContentFilter.SERIES -> list.filter { !it.isMovie }
    }
}
