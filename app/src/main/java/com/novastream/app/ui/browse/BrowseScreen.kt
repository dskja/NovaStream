package com.novastream.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.components.SeriesPosterCard
import com.novastream.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onSeriesClick: (String) -> Unit
) {
    val vm: BrowseViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore, state.loading, state.loadingMore, state.hasMore, state.items.size) {
        if (shouldLoadMore && !state.loading && !state.loadingMore && state.hasMore) {
            vm.loadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.loading && state.items.isNotEmpty(),
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize().background(BgPure)
    ) {
        when {
            state.loading && state.items.isEmpty() -> PremiumLoading(label = "Katalog laden…")
            state.error != null && state.items.isEmpty() -> PremiumError(state.error ?: "Fehler", onRetry = vm::refresh)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        Text(
                            "Entdecken",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "${state.providerName} · ${state.items.size} Titel geladen",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary
                        )
                    }
                }

                if (state.supportsMovies && state.supportsSeries) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            BrowseContentFilter.entries.forEach { filter ->
                                val selected = state.contentFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { vm.setContentFilter(filter) },
                                    label = {
                                        Text(
                                            when (filter) {
                                                BrowseContentFilter.ALL -> "Alle"
                                                BrowseContentFilter.SERIES -> "Serien"
                                                BrowseContentFilter.MOVIES -> "Filme"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                if (state.genres.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            FilterChip(
                                selected = state.selectedGenre == null,
                                onClick = { vm.selectGenre(null) },
                                label = { Text("Alle") }
                            )
                            state.genres.take(8).forEach { genre ->
                                FilterChip(
                                    selected = state.selectedGenre == genre.slug,
                                    onClick = { vm.selectGenre(genre.slug) },
                                    label = { Text(genre.name) }
                                )
                            }
                        }
                    }
                }

                items(state.items, key = { it.id }) { series ->
                    SeriesPosterCard(
                        series = series,
                        onClick = { onSeriesClick(series.id) },
                        cardWidth = 120
                    )
                }

                if (state.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
