package com.novastream.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.model.Series
import com.novastream.app.data.repository.NovaStreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Series> = emptyList(),
    val error: String? = null
)

class SearchViewModel(
    private val repo: NovaStreamRepository = NovaStreamRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q, error = null) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(450) // Debounce
            _state.update { it.copy(loading = true) }
            when (val res = repo.search(q)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    // serienstream.to /suche?term= gibt alle Serien zurück (kein server-side Filter)
                    // → client-side Filterung nach Query
                    val filtered = res.data.filter { series ->
                        val title = series.title.lowercase()
                        val query = q.trim().lowercase()
                        title.contains(query) || query.contains(title) ||
                        series.id.replace('-', ' ').lowercase().contains(query)
                    }
                    _state.update { it.copy(loading = false, results = filtered) }
                }
                is NovaStreamRepository.RepoResult.Error ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }
}
