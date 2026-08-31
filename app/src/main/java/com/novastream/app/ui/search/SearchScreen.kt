package com.novastream.app.ui.search

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.height
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore("search_prefs")
private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
private const val SEARCH_SEPARATOR = "\u0001"  // Unit separator - won't appear in search queries

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<com.novastream.app.data.model.Series> = emptyList(),
    val error: String? = null,
    val recentSearches: List<String> = emptyList(),
    val trending: List<com.novastream.app.data.model.Series> = emptyList()
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = com.novastream.app.data.repository.NovaStreamRepository()

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var trendingJob: kotlinx.coroutines.Job? = null
    private var activeProviderId: String? = null

    init {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { prefs ->
                try {
                    val raw = prefs[RECENT_SEARCHES_KEY] ?: ""
                    val searches = if (raw.isBlank()) emptyList() else raw.split(SEARCH_SEPARATOR)
                    _state.update { it.copy(recentSearches = searches) }
                } catch (e: Exception) {
                    if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("SearchVM", "Recent searches parse error, resetting", e)
                    try {
                        getApplication<Application>().dataStore.edit { it.remove(RECENT_SEARCHES_KEY) }
                    } catch (_: Exception) {}
                    _state.update { it.copy(recentSearches = emptyList()) }
                }
            }
        }
        viewModelScope.launch {
            try {
                com.novastream.app.data.provider.ProviderManager.activeProviderIdFlow(application).collect { providerId ->
                    com.novastream.app.data.provider.ActiveProvider.setById(providerId)
                    if (activeProviderId != providerId) {
                        activeProviderId = providerId
                        searchJob?.cancel()
                        _state.update {
                            it.copy(
                                results = emptyList(),
                                trending = emptyList(),
                                error = null,
                                loading = false
                            )
                        }
                        loadTrending()
                        // Aktuelle Query erneut suchen
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
        val expected = com.novastream.app.data.provider.ActiveProvider.id
        trendingJob = viewModelScope.launch {
            try {
                when (val res = repo.loadPopular()) {
                    is com.novastream.app.data.repository.NovaStreamRepository.RepoResult.Success -> {
                        if (com.novastream.app.data.provider.ActiveProvider.id == expected) {
                            _state.update { it.copy(trending = res.data.take(20)) }
                        }
                    }
                    else -> {
                        when (val home = repo.loadHome()) {
                            is com.novastream.app.data.repository.NovaStreamRepository.RepoResult.Success -> {
                                if (com.novastream.app.data.provider.ActiveProvider.id == expected) {
                                    _state.update { it.copy(trending = home.data.take(20)) }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun onQueryChange(q: String) {
        val trimmed = q.trim().take(100)
        _state.update { it.copy(query = trimmed, error = null) }
        searchJob?.cancel()
        if (trimmed.isBlank()) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        if (trimmed.length < 2) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        _state.update { it.copy(loading = true) }
        val expectedProvider = com.novastream.app.data.provider.ActiveProvider.id
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            currentCoroutineContext().ensureActive()
            if (_state.value.query != trimmed) return@launch
            if (com.novastream.app.data.provider.ActiveProvider.id != expectedProvider) return@launch
            when (val res = repo.search(trimmed)) {
                is com.novastream.app.data.repository.NovaStreamRepository.RepoResult.Success -> {
                    if (com.novastream.app.data.provider.ActiveProvider.id != expectedProvider) return@launch
                    _state.update {
                        it.copy(
                            loading = false,
                            results = res.data.filter {
                                it.providerId == null || it.providerId == expectedProvider
                            },
                            error = null
                        )
                    }
                }
                is com.novastream.app.data.repository.NovaStreamRepository.RepoResult.Error -> {
                    _state.update { it.copy(loading = false, error = res.message) }
                }
            }
        }
    }

    fun saveRecentSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                getApplication<Application>().dataStore.edit { prefs ->
                    val raw = prefs[RECENT_SEARCHES_KEY] ?: ""
                    val current = if (raw.isBlank()) emptyList() else raw.split(SEARCH_SEPARATOR)
                    // Remove duplicate if exists, add to front, keep max 10
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
                getApplication<Application>().dataStore.edit { it.remove(RECENT_SEARCHES_KEY) }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("SearchVM", "clearRecentSearches failed", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSeriesClick: (String) -> Unit
) {
    val vm: SearchViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(state.query.isEmpty()) {
        if (state.query.isEmpty()) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPure)
    ) {
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
                contentDescription = "Suche",
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            TextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Serie suchen…", color = TextTertiary) },
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
                        .clickable { vm.onQueryChange(""); focusManager.clearFocus() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Löschen",
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Content
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(6) { ShimmerPoster(Modifier.width(130.dp)) }
                    }
                }
                state.error != null -> PremiumError(state.error ?: "Unbekannter Fehler")
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
                                Text("Letzte Suchen", color = TextTertiary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Löschen",
                                    color = Primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable { vm.clearRecentSearches() }
                                )
                            }
                            state.recentSearches.forEach { search ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.onQueryChange(search) }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Text(search, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        // Trending section
                        if (state.trending.isNotEmpty()) {
                            SectionHeader("Beliebt jetzt", modifier = Modifier.padding(top = 8.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 130.dp),
                                contentPadding = PaddingValues(12.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.height(((state.trending.size / 2 + 1) * 280).dp)
                            ) {
                                items(state.trending, key = { it.id }) { s ->
                                    SeriesPosterCard(s, onClick = { onSeriesClick(s.id) })
                                }
                            }
                        }
                        if (state.recentSearches.isEmpty() && state.trending.isEmpty()) {
                            PremiumEmpty("Suche nach deiner Lieblingsserie", icon = Icons.Default.Search)
                        }
                    }
                }
                state.results.isEmpty() -> PremiumEmpty("Keine Treffer für '${state.query}'", icon = Icons.Default.Search)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    contentPadding = PaddingValues(12.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.results, key = { it.id }) { s ->
                        SeriesPosterCard(s, onClick = {
                            vm.saveRecentSearch(state.query)
                            onSeriesClick(s.id)
                        })
                    }
                }
            }
        }
    }
}
