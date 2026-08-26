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
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val loading: Boolean = true
)

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {
    private val watchRepo = WatchRepository(application)

    private val _state = MutableStateFlow(WatchlistUiState())
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                watchRepo.watchlist().collect { items ->
                    _state.update { it.copy(items = items, loading = false) }
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
}

@Composable
fun WatchlistScreen(
    onSeriesClick: (String) -> Unit
) {
    val vm: WatchlistViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

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
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.items.isEmpty() && !state.loading -> {
                    PremiumEmpty("Deine Watchlist ist leer.\nFüge Serien hinzu die du schauen möchtest.")
                }
                state.items.isEmpty() -> {
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
                                        .clickable { vm.remove(item.slug) },
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
