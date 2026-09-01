package com.novastream.app.ui.browse

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.model.Genre
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ProviderManager
import com.novastream.app.data.provider.capabilities
import com.novastream.app.data.repository.NovaStreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrowseContentFilter { ALL, SERIES, MOVIES }

enum class BrowseSort(val label: String) {
    TITLE_ASC("A-Z"),
    NEWEST("Neueste"),
    POPULAR("Beliebt")
}

data class BrowseUiState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val items: List<Series> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val selectedGenre: String? = null,
    val contentFilter: BrowseContentFilter = BrowseContentFilter.ALL,
    val sort: BrowseSort = BrowseSort.POPULAR,
    val section: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val error: String? = null,
    val providerName: String = "",
    val supportsMovies: Boolean = false,
    val supportsSeries: Boolean = true,
    val supportsPagination: Boolean = false
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repo: NovaStreamRepository
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState(loading = true))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var activeProviderId: String = ActiveProvider.id
    private var allItems: List<Series> = emptyList()
    private val initialSection: String? = savedStateHandle.get<String>("section")?.takeIf { it.isNotBlank() }
    private val initialGenre: String? = savedStateHandle.get<String>("genre")?.takeIf { it.isNotBlank() }
    private val initialFilter: BrowseContentFilter = parseContentFilter(savedStateHandle.get<String>("filter"))

    init {
        viewModelScope.launch {
            ProviderManager.activeProviderIdFlow(context).collect { providerId ->
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
        _state.update {
            it.copy(
                selectedGenre = genre,
                section = if (genre.isNullOrBlank()) it.section else "genre",
                page = 0,
                items = emptyList(),
                hasMore = true,
                error = null
            )
        }
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

    fun setSort(sort: BrowseSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort, items = applySort(applyContentFilter(allItems))) }
    }

    fun loadMore() {
        if (_state.value.loadingMore || !_state.value.hasMore || _state.value.loading) return
        if (usesSectionCatalog()) return
        if (!_state.value.supportsPagination && !usesMoviesCatalog()) return
        if (usesMoviesCatalog()) return
        loadPage(reset = false)
    }

    private fun resetAndLoad() {
        val provider = ActiveProvider.get()
        val caps = provider.capabilities()
        allItems = emptyList()
        val section = initialSection
        val resolvedGenre = initialGenre
        _state.update {
            BrowseUiState(
                loading = true,
                genres = provider.availableGenres,
                selectedGenre = resolvedGenre,
                contentFilter = initialFilter,
                section = section,
                providerName = provider.displayName,
                supportsMovies = provider.supportsMovies,
                supportsSeries = provider.supportsSeries,
                supportsPagination = caps.supportsPagination
            )
        }
        reloadForFilter(reset = true)
    }

    private fun usesMoviesCatalog(): Boolean =
        (_state.value.section == "movies" || _state.value.contentFilter == BrowseContentFilter.MOVIES) &&
            _state.value.supportsMovies &&
            _state.value.selectedGenre.isNullOrBlank()

    private fun usesSectionCatalog(): Boolean {
        val section = _state.value.section?.takeIf { it.isNotBlank() } ?: return false
        if (section in setOf("all", "catalog")) return false
        if (section == "genre") return !_state.value.selectedGenre.isNullOrBlank()
        return _state.value.selectedGenre.isNullOrBlank()
    }

    private fun reloadForFilter(reset: Boolean) {
        when {
            usesMoviesCatalog() -> loadMovies(reset)
            usesSectionCatalog() -> loadSection(reset)
            else -> loadPage(reset)
        }
    }

    private fun loadSection(reset: Boolean) {
        loadJob?.cancel()
        val expectedProvider = ActiveProvider.id
        val section = _state.value.section ?: return
        _state.update {
            it.copy(
                loading = reset,
                loadingMore = false,
                error = null
            )
        }
        loadJob = viewModelScope.launch {
            val result = when (section) {
                "popular" -> repo.loadPopular()
                "newest" -> repo.loadNewest()
                "trending" -> repo.loadHomeCatalog().mapCatalog { it.trending.ifEmpty { it.popular } }
                "movies" -> repo.loadMovies()
                "genre" -> {
                    val slug = _state.value.selectedGenre
                    if (!slug.isNullOrBlank()) repo.loadGenre(slug) else repo.loadCatalogPage(0)
                }
                else -> repo.loadCatalogPage(0)
            }
            if (ActiveProvider.id != expectedProvider) return@launch
            when (result) {
                is NovaStreamRepository.RepoResult.Success -> {
                    allItems = result.data
                    publishItems(reset = true, page = 0, hasMore = false)
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
                    publishItems(reset = true, page = 0, hasMore = false)
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
                    publishItems(
                        reset = reset,
                        page = nextPage,
                        hasMore = result.data.isNotEmpty() && _state.value.supportsPagination
                    )
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

    private fun publishItems(reset: Boolean, page: Int, hasMore: Boolean) {
        val filtered = applyContentFilter(allItems)
        _state.update { current ->
            current.copy(
                loading = false,
                loadingMore = false,
                items = applySort(filtered),
                page = page,
                hasMore = hasMore,
                error = null
            )
        }
    }

    private fun applyContentFilter(
        list: List<Series>,
        filter: BrowseContentFilter = _state.value.contentFilter
    ): List<Series> = applyBrowseContentFilter(list, filter)

    private fun applySort(list: List<Series>, sort: BrowseSort = _state.value.sort): List<Series> = when (sort) {
        BrowseSort.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
        BrowseSort.NEWEST -> list.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
        BrowseSort.POPULAR -> list.sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
    }

    private fun NovaStreamRepository.RepoResult<com.novastream.app.data.model.HomeCatalog>.mapCatalog(
        transform: (com.novastream.app.data.model.HomeCatalog) -> List<Series>
    ): NovaStreamRepository.RepoResult<List<Series>> = when (this) {
        is NovaStreamRepository.RepoResult.Success ->
            NovaStreamRepository.RepoResult.Success(transform(data))
        is NovaStreamRepository.RepoResult.Error -> this
    }

    companion object {
        private fun parseContentFilter(raw: String?): BrowseContentFilter = when (raw?.lowercase()) {
            "movies", "movie", "filme" -> BrowseContentFilter.MOVIES
            "series", "serien" -> BrowseContentFilter.SERIES
            else -> BrowseContentFilter.ALL
        }
    }
}

internal fun applyBrowseContentFilter(list: List<Series>, filter: BrowseContentFilter): List<Series> = when (filter) {
    BrowseContentFilter.ALL -> list
    BrowseContentFilter.MOVIES -> list.filter { it.isMovie }
    BrowseContentFilter.SERIES -> list.filter { !it.isMovie }
}
