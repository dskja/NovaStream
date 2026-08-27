package com.novastream.app.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.novastream.app.data.model.Series
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.ui.components.ContinueWatchingCard
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.SectionHeader
import com.novastream.app.ui.components.SeriesPosterCard
import com.novastream.app.ui.components.ShimmerBox
import com.novastream.app.ui.components.ShimmerRow
import com.novastream.app.ui.theme.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    onSeriesClick: (String) -> Unit,
    onContinueWatchingClick: (slug: String, season: Int, episode: Int, title: String, seriesTitle: String, coverUrl: String?) -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(BgPure)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(BgPure)
        ) {
            // Hero Banner Karussell
            item {
                if (state.loading && state.hero.isEmpty()) {
                    ShimmerBox(
                        Modifier.fillMaxWidth().height(280.dp),
                        cornerRadius = 0
                    )
                } else if (state.hero.isNotEmpty()) {
                    HeroCarousel(
                        series = state.hero.take(8),
                        onClick = onSeriesClick
                    )
                }
            }

            // Continue Watching Section
            if (state.continueWatching.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Weitersehen")
                }
                item {
                    LazyRow(
                        Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.continueWatching, key = { it.episodeKey }) { progress ->
                            ContinueWatchingCard(
                                progress = progress,
                                onClick = {
                                    onContinueWatchingClick(
                                        progress.slug,
                                        progress.season,
                                        progress.episode,
                                        progress.episodeTitle,
                                        progress.seriesTitle,
                                        progress.coverUrl
                                    )
                                },
                                onRemove = { vm.removeContinueWatching(progress.episodeKey) }
                            )
                        }
                    }
                }
            }

            // Watchlist Preview Section
            if (state.watchlist.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Meine Liste")
                }
                item {
                    LazyRow(
                        Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.watchlist.take(20), key = { it.slug }) { item ->
                            SeriesPosterCard(
                                series = item.toSeries(),
                                onClick = { onSeriesClick(item.slug) }
                            )
                        }
                    }
                }
            }

            // Loading State
            if (state.loading && state.popular.isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Beliebt")
                    ShimmerRow()
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Neu hinzugefügt")
                    ShimmerRow()
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Angesagt")
                    ShimmerRow()
                }
            }

            // Error State
            if (state.error != null && state.popular.isEmpty()) {
                item {
                    PremiumError(
                        message = state.error ?: "Unbekannter Fehler",
                        onRetry = vm::load,
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            }

            // Beliebt
            if (state.popular.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Beliebt")
                }
                item {
                    SeriesRow(state.popular, onSeriesClick)
                }
            }

            // Neu hinzugefügt
            if (state.newest.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Neu hinzugefügt")
                }
                item {
                    SeriesRow(state.newest, onSeriesClick)
                }
            }

            // Angesagt / Trending
            if (state.trending.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Angesagt")
                }
                item {
                    SeriesRow(state.trending, onSeriesClick)
                }
            }

            // Action
            if (state.action.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Action")
                }
                item {
                    SeriesRow(state.action, onSeriesClick)
                }
            }

            // Drama
            if (state.drama.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Drama")
                }
                item {
                    SeriesRow(state.drama, onSeriesClick)
                }
            }

            // Sci-Fi
            if (state.scifi.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Sci-Fi & Fantasy")
                }
                item {
                    SeriesRow(state.scifi, onSeriesClick)
                }
            }

            // Comedy
            if (state.comedy.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Comedy")
                }
                item {
                    SeriesRow(state.comedy, onSeriesClick)
                }
            }

            // Alle Serien (alle restlichen anzeigen)
            if (state.popular.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Alle Serien")
                }
                item {
                    SeriesRow(state.popular + state.newest + state.trending, onSeriesClick)
                }
            }

            // Bottom spacing for BottomBar
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SeriesRow(series: List<Series>, onSeriesClick: (String) -> Unit) {
    LazyRow(
        Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(series.distinctBy { it.id }, key = { it.id }) { s ->
            SeriesPosterCard(s, onClick = { onSeriesClick(s.id) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(
    series: List<Series>,
    onClick: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { series.size })
    val context = LocalContext.current

    // Auto-scroll
    LaunchedEffect(pagerState, series.size) {
        if (series.size <= 1) return@LaunchedEffect
        try {
            while (true) {
                kotlinx.coroutines.delay(5000)
                kotlinx.coroutines.yield()
                if (pagerState.isScrollInProgress) continue
                val next = (pagerState.currentPage + 1) % series.size
                pagerState.animateScrollToPage(next, animationSpec = tween(800))
            }
        } catch (_: kotlinx.coroutines.CancellationException) {}
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val s = series[page]
            var isLoading by remember(s.id) { mutableStateOf(true) }
            var isError by remember(s.id) { mutableStateOf(false) }

            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { onClick(s.id) }
            ) {
                if (!s.coverUrl.isNullOrBlank() && !isError) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(s.coverUrl)
                            .crossfade(false)
                            .build(),
                        contentDescription = s.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onState = { state ->
                            isLoading = state is AsyncImagePainter.State.Loading
                            isError = state is AsyncImagePainter.State.Error
                        }
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(BgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s.title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "??",
                            color = Accent,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Gradient overlays
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color(0x66000000),
                            0.4f to Color.Transparent,
                            0.8f to Color(0xCC08090C),
                            1f to BgPure
                        )
                    )
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Color(0x9908090C),
                            0.5f to Color.Transparent
                        )
                    )
                )

                // Title + Play button
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(PrimaryGradient)
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Ansehen",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ansehen",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Page indicators
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(series.size) { i ->
                val selected = pagerState.currentPage == i
                val width by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (selected) 24.dp else 8.dp,
                    animationSpec = androidx.compose.animation.core.tween(300),
                    label = "indicatorWidth"
                )
                Box(
                    Modifier
                        .size(width, 4.dp)
                        .clip(CircleShape)
                        .background(if (selected) Primary else Color(0x66FFFFFF))
                )
            }
        }
    }
}
