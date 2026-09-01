package com.novastream.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.novastream.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.ui.components.PremiumEmpty
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.components.SeriesPosterCard
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.TvUtils
import com.novastream.app.ui.tv.rememberInitialFocusRequester
import com.novastream.app.ui.tv.tvFocusRing
import com.novastream.app.ui.tv.tvFocusable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onSeriesClick: (String) -> Unit
) {
    val vm: BrowseViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTvDevice(context) }
    val minPoster = if (isTv) 160.dp else 120.dp
    val initialFocus = rememberInitialFocusRequester()
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading, state.items) {
        if (!state.loading && state.items.isNotEmpty()) {
            try {
                initialFocus.requestFocus()
            } catch (_: Exception) {}
        }
    }

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
            state.loading && state.items.isEmpty() -> PremiumLoading(label = stringResource(R.string.browse_loading_catalog))
            state.error != null && state.items.isEmpty() -> PremiumError(state.error ?: stringResource(R.string.error_title), onRetry = vm::refresh)
            !state.loading && state.items.isEmpty() -> PremiumEmpty(
                text = stringResource(R.string.browse_empty_filter),
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minPoster),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.nav_browse),
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                stringResource(R.string.browse_header_subtitle_fmt, state.providerName, state.items.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextTertiary
                            )
                        }
                        Box {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BgSurfaceElevated)
                                    .clickable { showSortMenu = true }
                                    .then(if (isTv) Modifier.tvFocusable().tvFocusRing(cornerRadius = 12.dp) else Modifier)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    stringResource(state.sort.labelRes),
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
                                BrowseSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(option.labelRes),
                                                color = if (state.sort == option) Primary else TextPrimary
                                            )
                                        },
                                        onClick = {
                                            vm.setSort(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
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
                                BrowseFilterChip(
                                    label = when (filter) {
                                        BrowseContentFilter.ALL -> stringResource(R.string.provider_filter_all)
                                        BrowseContentFilter.SERIES -> stringResource(R.string.provider_content_series)
                                        BrowseContentFilter.MOVIES -> stringResource(R.string.provider_content_movies)
                                    },
                                    selected = selected,
                                    onClick = { vm.setContentFilter(filter) },
                                    isTv = isTv
                                )
                            }
                        }
                    }
                }

                if (state.genres.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            BrowseFilterChip(
                                label = stringResource(R.string.provider_filter_all),
                                selected = state.selectedGenre == null,
                                onClick = { vm.selectGenre(null) },
                                isTv = isTv
                            )
                            state.genres.forEach { genre ->
                                BrowseFilterChip(
                                    label = genre.name,
                                    selected = state.selectedGenre == genre.slug,
                                    onClick = { vm.selectGenre(genre.slug) },
                                    isTv = isTv
                                )
                            }
                        }
                    }
                }

                items(state.items, key = { it.id }) { series ->
                    val isFirst = series.id == state.items.firstOrNull()?.id
                    SeriesPosterCard(
                        series = series,
                        onClick = { onSeriesClick(series.id) },
                        cardWidth = if (isTv) 160 else 120,
                        focusRequester = if (isFirst) initialFocus else null
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

@Composable
private fun BrowseFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    isTv: Boolean
) {
    val base = Modifier
        .clip(RoundedCornerShape(20.dp))
        .background(if (selected) Primary.copy(alpha = 0.2f) else BgSurfaceElevated)
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp)

    Box(
        modifier = if (isTv) {
            base.tvFocusable().tvFocusRing(cornerRadius = 20.dp)
        } else {
            base
        },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Primary else TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
