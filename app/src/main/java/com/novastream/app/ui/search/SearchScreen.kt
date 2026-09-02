package com.novastream.app.ui.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.novastream.app.R
import com.novastream.app.data.meta.CatalogMetaEnricher
import com.novastream.app.data.meta.FreeMetaGraph
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.profile.ProfileManager
import com.novastream.app.util.KidsContentFilter
import com.novastream.app.data.repository.NovaStreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.novastream.app.ui.components.PremiumEmpty
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.components.SectionHeader
import com.novastream.app.ui.components.SeriesPosterCard
import com.novastream.app.ui.components.ShimmerPoster
import com.novastream.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val android.content.Context.dataStore by preferencesDataStore("search_prefs")
private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
private const val SEARCH_SEPARATOR = "\u0001"  // Unit separator - won't appear in search queries

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<com.novastream.app.data.model.Series> = emptyList(),
    val error: String? = null,
    val trendingError: String? = null,
    val recentSearches: List<String> = emptyList(),
    val trending: List<com.novastream.app.data.model.Series> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: NovaStreamRepository,
    private val providerController: ProviderController,
    private val profileManager: ProfileManager,
    private val freeMetaGraph: FreeMetaGraph,
    private val catalogMetaEnricher: CatalogMetaEnricher,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var trendingJob: kotlinx.coroutines.Job? = null
    private var activeProviderId: String? = null
    private var kidsMode: Boolean = false

    init {
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                try {
                    val raw = prefs[RECENT_SEARCHES_KEY] ?: ""
                    val searches = if (raw.isBlank()) emptyList() else raw.split(SEARCH_SEPARATOR)
                    _state.update { it.copy(recentSearches = searches) }
                } catch (e: Exception) {
                    if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("SearchVM", "Recent searches parse error, resetting", e)
                    try {
                        context.dataStore.edit { it.remove(RECENT_SEARCHES_KEY) }
                    } catch (e: Exception) {
                        com.novastream.app.util.DebugLog.w("SearchVM", "reset recent searches failed", e)
                    }
                    _state.update { it.copy(recentSearches = emptyList()) }
                }
            }
        }
        viewModelScope.launch {
            profileManager.isKidsProfile().collect { isKids ->
                kidsMode = isKids
                val q = _state.value.query
                if (q.length >= 2) onQueryChange(q) else loadTrending()
            }
        }
        viewModelScope.launch {
            try {
                providerController.activeProviderId.collect { providerId ->
                    if (activeProviderId != providerId) {
                        activeProviderId = providerId
                        searchJob?.cancel()
                        _state.update {
                            it.copy(
                                results = emptyList(),
                                trending = emptyList(),
                                error = null,
                                trendingError = null,
                                loading = false
                            )
                        }
                        loadTrending()
                        val q = _state.value.query
                        if (q.length >= 2) onQueryChange(q)
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("SearchVM", "provider flow error", e)
                loadTrending()
            }
        }
    }

    private fun loadTrending() {
        trendingJob?.cancel()
        val expected = ActiveProvider.id
        trendingJob = viewModelScope.launch {
            try {
                when (val res = repo.loadHomeCatalog()) {
                    is NovaStreamRepository.RepoResult.Success -> {
                        if (ActiveProvider.id == expected) {
                            val trending = res.data.trending.ifEmpty { res.data.popular }
                            _state.update {
                                it.copy(
                                    trending = KidsContentFilter.filterSeries(trending, kidsMode).take(20),
                                    trendingError = null
                                )
                            }
                        }
                    }
                    else -> {
                        when (val popular = repo.loadPopular()) {
                            is NovaStreamRepository.RepoResult.Success -> {
                                if (ActiveProvider.id == expected) {
                                    _state.update {
                                        it.copy(
                                            trending = KidsContentFilter.filterSeries(popular.data, kidsMode).take(20),
                                            trendingError = null
                                        )
                                    }
                                }
                            }
                            is NovaStreamRepository.RepoResult.Error -> {
                                if (ActiveProvider.id == expected) {
                                    _state.update { it.copy(trendingError = popular.message) }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) {
                    android.util.Log.w("SearchVM", "loadTrending failed", e)
                }
                _state.update {
                    it.copy(trendingError = com.novastream.app.util.ErrorMapper.toUserMessage(e))
                }
            }
        }
    }

    fun onQueryChange(q: String) {
        val limited = q.take(100)
        _state.update { it.copy(query = limited, error = null) }
        searchJob?.cancel()
        val trimmed = limited.trim()
        if (trimmed.isBlank()) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        if (trimmed.length < 2) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        _state.update { it.copy(loading = true) }
        val expectedProvider = ActiveProvider.id
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            currentCoroutineContext().ensureActive()
            if (_state.value.query.trim() != trimmed) return@launch
            if (ActiveProvider.id != expectedProvider) return@launch
            when (val res = repo.search(trimmed)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    if (ActiveProvider.id != expectedProvider) return@launch
                    val providerResults = res.data
                        .filter { it.providerId == null || it.providerId == expectedProvider }
                        .map { it.copy(providerId = expectedProvider) }
                    val language = ContentLanguage.fromTag(appSettings.contentLanguage.first())
                    val preferAnime = trimmed.contains("anime", ignoreCase = true) || ActiveProvider.isAniWorld
                    val merged = mergeWithFreeMeta(trimmed, listOf(expectedProvider to providerResults), language, preferAnime)
                    _state.update {
                        it.copy(
                            loading = false,
                            results = KidsContentFilter.filterSeries(merged, kidsMode),
                            error = null
                        )
                    }
                }
                is NovaStreamRepository.RepoResult.Error -> {
                    _state.update { it.copy(loading = false, error = res.message) }
                }
            }
        }
    }

    fun saveRecentSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                context.dataStore.edit { prefs ->
                    val raw = prefs[RECENT_SEARCHES_KEY] ?: ""
                    val current = if (raw.isBlank()) emptyList() else raw.split(SEARCH_SEPARATOR)
                    val updated = (listOf(query) + current.filter { it != query }).take(10)
                    prefs[RECENT_SEARCHES_KEY] = updated.joinToString(SEARCH_SEPARATOR)
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("SearchVM", "saveRecentSearch failed", e)
            }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            try {
                context.dataStore.edit { it.remove(RECENT_SEARCHES_KEY) }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("SearchVM", "clearRecentSearches failed", e)
            }
        }
    }

    suspend fun ensureProvider(providerId: String) {
        if (providerId.isNotBlank() && providerId != ActiveProvider.id) {
            providerController.setActiveProvider(providerId)
        }
    }

    private suspend fun mergeWithFreeMeta(
        query: String,
        providerResults: List<Pair<String, List<com.novastream.app.data.model.Series>>>,
        language: ContentLanguage,
        preferAnime: Boolean
    ): List<com.novastream.app.data.model.Series> {
        val metaSeries = freeMetaGraph.search(query, preferAnime = preferAnime, limit = 12)
            .map { show ->
                val stub = freeMetaGraph.toSeries(show, "free-meta")
                catalogMetaEnricher.enrichOne(stub, language, preferAnime)
            }
        val combined = if (metaSeries.isNotEmpty()) {
            providerResults + ("free-meta" to metaSeries)
        } else providerResults
        return SearchResultAggregator.aggregate(combined)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSeriesClick: (String) -> Unit
) {
    val vm: SearchViewModel = hiltViewModel()
    val globalVm: GlobalSearchViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val globalState by globalVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    val contentLanguageTag by appSettings.contentLanguage.collectAsStateWithLifecycle(initialValue = ContentLanguage.DE.tag)
    val contentLanguage = ContentLanguage.fromTag(contentLanguageTag)
    val useGlobal = globalState.scope == GlobalSearchScope.CONTENT_LANGUAGE
    val displayResults = if (useGlobal) globalState.results else state.results
    val displayLoading = if (useGlobal) globalState.loading else state.loading
    val displayError = when {
        state.query.isBlank() -> null
        useGlobal -> globalState.error
        else -> state.error
    }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(state.query.isEmpty()) {
        if (state.query.isEmpty()) {
            try { focusRequester.requestFocus() } catch (e: Exception) {
                com.novastream.app.util.DebugLog.w("SearchScreen", "focus request failed", e)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPure)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = globalState.scope == GlobalSearchScope.ACTIVE_PROVIDER,
                onClick = {
                    globalVm.setScope(GlobalSearchScope.ACTIVE_PROVIDER)
                    vm.onQueryChange(state.query)
                },
                label = { Text(stringResource(com.novastream.app.R.string.search_scope_active)) }
            )
            FilterChip(
                selected = globalState.scope == GlobalSearchScope.CONTENT_LANGUAGE,
                onClick = {
                    globalVm.setContentLanguage(contentLanguage)
                    globalVm.setScope(GlobalSearchScope.CONTENT_LANGUAGE)
                    globalVm.onQueryChange(state.query)
                },
                label = {
                    Text(stringResource(com.novastream.app.R.string.search_scope_global, contentLanguage.tag.uppercase()))
                }
            )
        }
        // Search Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 8.dp
                )
                .clip(RoundedCornerShape(28.dp))
                .background(BgSurface)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            TextField(
                value = state.query,
                onValueChange = {
                    vm.onQueryChange(it)
                    if (globalState.scope == GlobalSearchScope.CONTENT_LANGUAGE) globalVm.onQueryChange(it)
                },
                placeholder = { Text(stringResource(com.novastream.app.R.string.search_placeholder), color = TextTertiary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    vm.saveRecentSearch(state.query)
                    focusManager.clearFocus()
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Primary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            if (state.query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable {
                            vm.onQueryChange("")
                            if (useGlobal) globalVm.onQueryChange("")
                            focusManager.clearFocus()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Content
        Box(Modifier.fillMaxSize()) {
            when {
                displayLoading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(6) { ShimmerPoster(Modifier.width(130.dp)) }
                    }
                }
                displayError != null -> PremiumError(
                    displayError ?: stringResource(com.novastream.app.R.string.error_title),
                    onRetry = {
                        vm.onQueryChange(state.query)
                        if (useGlobal) globalVm.search(state.query)
                    }
                )
                state.query.isBlank() -> {
                    // Show recent searches + trending when query is blank
                    Column {
                        if (state.recentSearches.isNotEmpty()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.search_recent), color = TextTertiary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.search_clear),
                                    color = Primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable { vm.clearRecentSearches() }
                                )
                            }
                            state.recentSearches.forEach { search ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.onQueryChange(search)
                                            if (search.trim().length >= 2) {
                                                globalVm.onQueryChange(search)
                                            }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Text(search, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        if (state.trendingError != null) {
                            Text(
                                state.trendingError ?: "",
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // Trending section
                        if (state.trending.isNotEmpty()) {
                            SectionHeader(stringResource(R.string.search_trending), modifier = Modifier.padding(top = 8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(bottom = 80.dp)
                            ) {
                                items(state.trending, key = { it.id }) { s ->
                                    SeriesPosterCard(s, onClick = { onSeriesClick(s.id) })
                                }
                            }
                        }
                        if (state.recentSearches.isEmpty() && state.trending.isEmpty()) {
                            PremiumEmpty(stringResource(R.string.search_empty), icon = Icons.Default.Search)
                        }
                    }
                }
                displayResults.isEmpty() && state.query.isNotBlank() -> PremiumEmpty(
                    if (!useGlobal && ActiveProvider.isBurningSeries) {
                        stringResource(com.novastream.app.R.string.search_bs_blocked)
                    } else {
                        stringResource(com.novastream.app.R.string.search_no_results, state.query)
                    },
                    icon = Icons.Default.Search
                )
                displayResults.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    contentPadding = PaddingValues(12.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(displayResults, key = { "${it.providerId}:${it.id}" }) { s ->
                        SeriesPosterCard(s, onClick = {
                            vm.saveRecentSearch(state.query)
                            scope.launch {
                                if (useGlobal && !s.providerId.isNullOrBlank()) {
                                    vm.ensureProvider(s.providerId)
                                }
                                onSeriesClick(s.id)
                            }
                        })
                    }
                }
            }
        }
    }
}
