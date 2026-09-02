package com.novastream.app.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.network.ScrapeLimiter
import com.novastream.app.data.meta.CatalogMetaEnricher
import com.novastream.app.data.meta.FreeMetaGraph
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ProviderLanguageManager
import com.novastream.app.data.provider.StreamingProvider
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.profile.ProfileManager
import com.novastream.app.util.ErrorMapper
import com.novastream.app.util.KidsContentFilter
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
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val freeMetaGraph: FreeMetaGraph,
    private val catalogMetaEnricher: CatalogMetaEnricher,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalSearchUiState())
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var kidsMode: Boolean = false

    init {
        viewModelScope.launch {
            appSettings.contentLanguage.collect { tag ->
                val lang = ContentLanguage.fromTag(tag)
                if (_state.value.contentLanguage != lang) {
                    _state.update { it.copy(contentLanguage = lang) }
                }
            }
        }
        viewModelScope.launch {
            profileManager.isKidsProfile().collect { isKids ->
                kidsMode = isKids
                val q = _state.value.query
                if (q.length >= 2) search(q)
            }
        }
    }

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
                    GlobalSearchScope.ACTIVE_PROVIDER -> searchSingleWithMeta(ActiveProvider.get(), trimmed)
                    GlobalSearchScope.CONTENT_LANGUAGE -> searchManyWithMeta(providers, trimmed)
                }
                _state.update {
                    it.copy(
                        loading = false,
                        results = KidsContentFilter.filterSeries(aggregated, kidsMode),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = ErrorMapper.toUserMessage(context, e)) }
            }
        }
    }

    private fun providersForScope(): List<StreamingProvider> =
        when (_state.value.scope) {
            GlobalSearchScope.ACTIVE_PROVIDER -> listOf(ActiveProvider.get())
            GlobalSearchScope.CONTENT_LANGUAGE ->
                ProviderLanguageManager.getProvidersForLanguage(_state.value.contentLanguage)
        }

    private suspend fun searchSingleWithMeta(provider: StreamingProvider, query: String): List<com.novastream.app.data.model.Series> {
        val providerResults = searchSingle(provider, query)
        return mergeWithFreeMeta(query, listOf(provider.id to providerResults))
    }

    private suspend fun searchManyWithMeta(
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
        mergeWithFreeMeta(query, allResults)
    }

    private suspend fun mergeWithFreeMeta(
        query: String,
        providerResults: List<Pair<String, List<com.novastream.app.data.model.Series>>>
    ): List<com.novastream.app.data.model.Series> {
        val preferAnime = query.contains("anime", ignoreCase = true) ||
            ActiveProvider.isAniWorld
        val language = _state.value.contentLanguage
        val enrichedProviders = catalogMetaEnricher.enrichProviderResults(
            providerResults,
            language,
            preferAnime,
            limitPerProvider = 20
        )
        val metaSeries = freeMetaGraph.search(query, preferAnime = preferAnime, limit = 15)
            .map { show ->
                val stub = freeMetaGraph.toSeries(show, "free-meta")
                catalogMetaEnricher.enrichOne(stub, language, preferAnime)
            }
        val combined = if (metaSeries.isNotEmpty()) {
            enrichedProviders + ("free-meta" to metaSeries)
        } else enrichedProviders
        return SearchResultAggregator.aggregate(combined)
    }

    private suspend fun searchSingle(provider: StreamingProvider, query: String): List<com.novastream.app.data.model.Series> {
        val result = ScrapeLimiter.withPermit { provider.search(query) }
        return when (result) {
            is StreamingProvider.ProviderResult.Success ->
                result.data.map { it.copy(providerId = provider.id) }
            else -> emptyList()
        }
    }
}
