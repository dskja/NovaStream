package com.novastream.app.ui.search

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore("search_prefs")
private val RECENT_SEARCHES_KEY = stringSetPreferencesKey("recent_searches")

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<com.novastream.app.data.model.Series> = emptyList(),
    val error: String? = null,
    val recentSearches: List<String> = emptyList()
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = com.novastream.app.data.repository.NovaStreamRepository()

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        // Load recent searches
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { prefs ->
                val searches = prefs[RECENT_SEARCHES_KEY]?.toList()?.sortedDescending() ?: emptyList()
                _state.update { it.copy(recentSearches = searches) }
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q, error = null) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        _state.update { it.copy(loading = true) }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(450) // Debounce
            if (_state.value.query != q) return@launch  // Veraltete Query
            when (val res = repo.search(q)) {
                is com.novastream.app.data.repository.NovaStreamRepository.RepoResult.Success -> {
                    _state.update { it.copy(loading = false, results = res.data, error = null) }
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
            getApplication<Application>().dataStore.edit { prefs ->
                val current = prefs[RECENT_SEARCHES_KEY] ?: emptySet()
                prefs[RECENT_SEARCHES_KEY] = (current + query).toList().take(10).toSet()
            }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it.remove(RECENT_SEARCHES_KEY) }
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
                modifier = Modifier.weight(1f)
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
                    // Show recent searches when query is blank
                    if (state.recentSearches.isNotEmpty()) {
                        Column {
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
                    } else {
                        PremiumEmpty("Suche nach deiner Lieblingsserie")
                    }
                }
                state.results.isEmpty() -> PremiumEmpty("Keine Treffer für '${state.query}'")
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
