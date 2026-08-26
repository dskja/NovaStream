package com.novastream.app.ui.watchlist

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.repository.WatchRepository
import com.novastream.app.ui.components.PremiumEmpty
import com.novastream.app.ui.components.SeriesPosterCard
import com.novastream.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val items: List<WatchlistItem> = emptyList(),
    val loading: Boolean = true,
    val sortOption: SortOption = SortOption.ADDED_DESC
)

enum class SortOption(val label: String) {
    ADDED_DESC("Zuletzt hinzugefügt"),
    ADDED_ASC("Älteste zuerst"),
    TITLE_ASC("Titel A-Z"),
    TITLE_DESC("Titel Z-A")
}

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {
    private val watchRepo = WatchRepository(application)

    private val _state = MutableStateFlow(WatchlistUiState())
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                watchRepo.watchlist().collect { items ->
                    val sorted = sortItems(items, _state.value.sortOption)
                    _state.update { it.copy(items = sorted, loading = false) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchlistVM", "flow error", e)
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun remove(slug: String) {
        viewModelScope.launch { watchRepo.removeFromWatchlist(slug) }
    }

    fun setSortOption(option: SortOption) {
        _state.update { it.copy(sortOption = option) }
    }

    private fun sortItems(items: List<WatchlistItem>, option: SortOption): List<WatchlistItem> {
        return when (option) {
            SortOption.ADDED_DESC -> items.sortedByDescending { it.addedAt }
            SortOption.ADDED_ASC -> items.sortedBy { it.addedAt }
            SortOption.TITLE_ASC -> items.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> items.sortedByDescending { it.title.lowercase() }
        }
    }
}

@Composable
fun WatchlistScreen(
    onSeriesClick: (String) -> Unit
) {
    val vm: WatchlistViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var pendingRemove by remember { mutableStateOf<WatchlistItem?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Confirmation dialog for removal
    pendingRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Entfernen?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Möchtest du '${item.title}' aus deiner Watchlist entfernen?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(item.slug)
                    pendingRemove = null
                }) { Text("Entfernen", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("Abbrechen", color = TextTertiary)
                }
            },
            containerColor = BgSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPure)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(4.dp, 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PrimaryGradient)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Meine Liste",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (state.items.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${state.items.size}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                // Sort button
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurfaceElevated)
                            .clickable { showSortMenu = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            state.sortOption.label,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(BgSurface)
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label, color = if (state.sortOption == option) Primary else TextPrimary) },
                                onClick = {
                                    vm.setSortOption(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.items.isEmpty() && state.loading -> {
                    // Loading
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(6) {
                            com.novastream.app.ui.components.ShimmerPoster(Modifier.width(130.dp))
                        }
                    }
                }
                state.items.isEmpty() -> {
                    PremiumEmpty(
                        "Deine Watchlist ist leer.\nFüge Serien hinzu die du schauen möchtest.",
                        icon = Icons.Filled.Bookmark
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(12.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.items, key = { it.slug }) { item ->
                            Box {
                                SeriesPosterCard(
                                    series = item.toSeries(),
                                    onClick = { onSeriesClick(item.slug) }
                                )
                                // Remove button overlay
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xCC000000))
                                        .clickable { pendingRemove = item },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.BookmarkRemove,
                                        contentDescription = "Entfernen",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
