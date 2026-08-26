package com.novastream.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.model.Series
import com.novastream.app.data.repository.SerienStreamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = false,
    val popular: List<Series> = emptyList(),
    val newest: List<Series> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    private val repo: SerienStreamRepository = SerienStreamRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repo.loadHome()) {
                is SerienStreamRepository.RepoResult.Success -> {
                    val series = res.data
                    _state.update {
                        it.copy(
                            loading = false,
                            popular = series,
                            newest = series.takeLast(20),
                            error = null
                        )
                    }
                }
                is SerienStreamRepository.RepoResult.Error ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }
}
