package com.novastream.app.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.network.ScrapeLimiter
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ProviderLanguageManager
import com.novastream.app.data.provider.StreamingProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GlobalSearchScope {
    ACTIVE_PROVIDER,
    CONTENT_LANGUAGE
}

data class GlobalSearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<com.novastream.app.data.model.Series> = emptyList(),
    val error: String? = null,
    val scope: GlobalSearchScope = GlobalSearchScope.ACTIVE_PROVIDER,
    val contentLanguage: ContentLanguage = ContentLanguage.DE,
    val providerCount: Int = 0
)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalSearchUiState())
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    fun setScope(scope: GlobalSearchScope) {
        _state.update { it.copy(scope = scope) }
        val q = _state.value.query
        if (q.length >= 2) search(q)
    }

    fun setContentLanguage(language: ContentLanguage) {
        _state.update { it.copy(contentLanguage = language) }
        if (_state.value.scope == GlobalSearchScope.CONTENT_LANGUAGE) {
            val q = _state.value.query
            if (q.length >= 2) search(q)
        }
    }

    fun onQueryChange(query: String) {
        val limited = query.take(100)
        _state.update { it.copy(query = limited, error = null) }
        val trimmed = limited.trim()
        if (trimmed.length < 2) {
            searchJob?.cancel()
            _state.update { it.copy(results = emptyList(), loading = false, providerCount = 0) }
            return
        }
        search(trimmed)
    }

    fun search(query: String) {
        val trimmed = query.trim().take(100)
        _state.update { it.copy(error = null) }
        searchJob?.cancel()
        if (trimmed.length < 2) {
            _state.update { it.copy(results = emptyList(), loading = false, providerCount = 0) }
            return
        }
        _state.update { it.copy(loading = true) }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            if (_state.value.query.trim() != trimmed) return@launch
            try {
                val providers = providersForScope()
                _state.update { it.copy(providerCount = providers.size) }
                val aggregated = when (_state.value.scope) {
                    GlobalSearchScope.ACTIVE_PROVIDER -> searchSingle(ActiveProvider.get(), trimmed)
                    GlobalSearchScope.CONTENT_LANGUAGE -> searchMany(providers, trimmed)
                }
                _state.update { it.copy(loading = false, results = aggregated, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Search failed") }
            }
        }
    }

    private fun providersForScope(): List<StreamingProvider> =
        when (_state.value.scope) {
            GlobalSearchScope.ACTIVE_PROVIDER -> listOf(ActiveProvider.get())
            GlobalSearchScope.CONTENT_LANGUAGE ->
                ProviderLanguageManager.getProvidersForLanguage(_state.value.contentLanguage)
        }

    private suspend fun searchSingle(provider: StreamingProvider, query: String): List<com.novastream.app.data.model.Series> {
        val result = ScrapeLimiter.withPermit { provider.search(query) }
        return when (result) {
            is StreamingProvider.ProviderResult.Success ->
                result.data.map { it.copy(providerId = provider.id) }
            else -> emptyList()
        }
    }

    private suspend fun searchMany(
        providers: List<StreamingProvider>,
        query: String
    ): List<com.novastream.app.data.model.Series> = coroutineScope {
        val maxConcurrent = 6
        val chunks = providers.chunked(maxConcurrent)
        val allResults = mutableListOf<Pair<String, List<com.novastream.app.data.model.Series>>>()
        for (chunk in chunks) {
            val partial = chunk.map { provider ->
                async {
                    provider.id to searchSingle(provider, query)
                }
            }.awaitAll()
            allResults.addAll(partial)
        }
        SearchResultAggregator.aggregate(allResults)
    }
}
