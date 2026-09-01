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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.novastream.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.novastream.app.data.model.Series
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.ui.components.ContinueWatchingCard
import com.novastream.app.ui.components.ProviderHealthBanner
import com.novastream.app.ui.components.PremiumEmpty
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.SectionHeader
import com.novastream.app.ui.components.SeriesPosterCard
import com.novastream.app.ui.components.ShimmerBox
import com.novastream.app.ui.components.ShimmerRow
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.rememberInitialFocusRequester
import com.novastream.app.ui.tv.tvFocusRing
import com.novastream.app.ui.tv.tvFocusable
import androidx.compose.ui.focus.onFocusChanged

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    onSeriesClick: (String) -> Unit,
    onContinueWatchingClick: (slug: String, season: Int, episode: Int, title: String, seriesTitle: String, coverUrl: String?, isMovie: Boolean) -> Unit,
    onBrowseSection: (section: String, genre: String?) -> Unit = { _, _ -> },
    onSeeAllWatchlist: () -> Unit = {},
    onSeeAllContinueWatching: () -> Unit = {},
    onLiveTvClick: () -> Unit = {}
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val initialFocus = rememberInitialFocusRequester()
    val activeProviderName = state.providerName.ifBlank {
        com.novastream.app.data.provider.ActiveProvider.displayName
    }
    val catalogEmpty = !state.loading &&
        state.error == null &&
        state.hero.isEmpty() &&
        state.popular.isEmpty() &&
        state.newest.isEmpty() &&
        state.trending.isEmpty() &&
        state.movies.isEmpty() &&
        state.genreRows.isEmpty() &&
        state.action.isEmpty() &&
        state.drama.isEmpty() &&
        state.scifi.isEmpty() &&
        state.comedy.isEmpty()

    LaunchedEffect(state.loading, state.hero, state.popular) {
        if (!state.loading && (state.hero.isNotEmpty() || state.popular.isNotEmpty())) {
            try {
                initialFocus.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val showShimmer = state.loading && !state.reduceMotion
    val shimmerAnimate = !state.reduceMotion
    val iptvEnabled = state.iptvEnabled

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
                .background(BgPure),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Provider Badge - zeigt aktiven Provider
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryGradient)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        activeProviderName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "•",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(8.dp))
                val catalogCount = state.uniqueTitleCount.takeIf { it > 0 }
                    ?: (state.popular + state.newest + state.trending + state.movies)
                        .distinctBy { it.id }.size
                val catalogHint = state.catalogHint
                Text(
                    when {
                        !catalogHint.isNullOrBlank() && catalogCount > 0 ->
                            stringResource(R.string.home_catalog_loaded_fmt, catalogCount, catalogHint)
                        !catalogHint.isNullOrBlank() -> catalogHint
                        state.supportsMovies && state.supportsSeries ->
                            stringResource(R.string.home_titles_available_fmt, catalogCount)
                        state.supportsMovies ->
                            stringResource(R.string.home_movies_available_fmt, catalogCount)
                        else ->
                            stringResource(R.string.home_series_available_fmt, catalogCount)
                    },
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }

        if (state.showProviderHealthWarning || state.error != null) {
            item {
                ProviderHealthBanner(
                    providerName = activeProviderName,
                    loadDurationMs = state.lastLoadDurationMs,
                    error = state.error,
                    onRetry = { vm.refresh() }
                )
            }
        }

        // Hero Banner Karussell
            item {
                if (showShimmer && state.hero.isEmpty()) {
                    ShimmerBox(
                        Modifier.fillMaxWidth().height(280.dp),
                        cornerRadius = 0,
                        animate = shimmerAnimate
                    )
                } else if (state.loading && state.hero.isEmpty() && state.reduceMotion) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(BgCard)
                    )
                } else if (state.hero.isNotEmpty()) {
                    HeroCarousel(
                        series = state.hero.take(if (state.performanceMode) 3 else 8),
                        onClick = onSeriesClick,
                        autoScrollEnabled = !state.reduceMotion && !state.performanceMode,
                        focusRequester = initialFocus
                    )
                }
            }

            // Continue Watching Section
            if (state.continueWatching.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(stringResource(R.string.settings_continue_watching), onSeeAll = onSeeAllContinueWatching)
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
                                        progress.coverUrl,
                                        progress.isMovie
                                    )
                                },
                                onRemove = { vm.removeContinueWatching(progress.episodeKey) }
                            )
                        }
                    }
                }
            }

            // Live TV entry (v14, when IPTV enabled)
            if (iptvEnabled) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(stringResource(R.string.live_tv_title), onSeeAll = onLiveTvClick)
                }
                item {
                    Card(
                        onClick = onLiveTvClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LiveTv, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.live_tv_browse_hint))
                        }
                    }
                }
            }

            // Watchlist Preview Section
            if (state.watchlist.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader(stringResource(R.string.home_my_list), onSeeAll = onSeeAllWatchlist)
                }
                item {
                    LazyRow(
                        Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.watchlist.take(20), key = { it.itemKey }) { item ->
                            SeriesPosterCard(
                                series = item.toSeries(),
                                onClick = { onSeriesClick(item.slug) }
                            )
                        }
                    }
                }
            }

            // Loading State
            if (showShimmer && state.popular.isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader(stringResource(R.string.home_popular))
                    ShimmerRow(animate = shimmerAnimate)
                    Spacer(Modifier.height(24.dp))
                    SectionHeader(stringResource(R.string.home_newest))
                    ShimmerRow(animate = shimmerAnimate)
                    Spacer(Modifier.height(24.dp))
                    SectionHeader(stringResource(R.string.home_trending))
                    ShimmerRow(animate = shimmerAnimate)
                }
            }

            // Error State
            if (state.error != null && state.popular.isEmpty()) {
                item {
                    PremiumError(
                        message = state.error ?: stringResource(R.string.error_unknown),
                        onRetry = vm::load,
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            }

            // Empty catalog
            if (catalogEmpty) {
                item {
                    PremiumEmpty(
                        text = stringResource(R.string.home_catalog_empty),
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .height(280.dp)
                    )
                }
            }

            // Beliebt
            if (state.popular.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(stringResource(R.string.home_popular), onSeeAll = { onBrowseSection("popular", null) })
                }
                item {
                    SeriesRow(state.popular, onSeriesClick, initialFocusRequester = if (state.hero.isEmpty()) initialFocus else null)
                }
            }

            // Neu hinzugefügt
            if (state.newest.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader(stringResource(R.string.home_newest), onSeeAll = { onBrowseSection("newest", null) })
                }
                item {
                    SeriesRow(state.newest, onSeriesClick)
                }
            }

            // Angesagt / Trending
            if (state.trending.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader(stringResource(R.string.home_trending), onSeeAll = { onBrowseSection("trending", null) })
                }
                item {
                    SeriesRow(state.trending, onSeriesClick)
                }
            }

            // Filme (wenn Provider Filme liefert)
            if (state.movies.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader(stringResource(R.string.home_movies_section), onSeeAll = { onBrowseSection("movies", null) })
                }
                item {
                    SeriesRow(state.movies, onSeriesClick)
                }
            }

            // Neue Episoden (echte Scraper-Daten)
            if (state.latestEpisodes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader(stringResource(R.string.home_latest_episodes))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.latestEpisodes.take(30), key = { "${it.seriesSlug}-${it.season}-${it.episode}" }) { ep ->
                            Column(
                                Modifier
                                    .width(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable { onSeriesClick(ep.seriesSlug) }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    ep.seriesTitle,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    ep.shortDisplay + if (ep.language.isNotBlank()) " · ${ep.language}" else "",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                                if (ep.timeLabel.isNotBlank()) {
                                    Text(ep.timeLabel, color = TextTertiary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Dynamische Genre-Reihen vom aktiven Provider
            state.genreRows.forEach { (genre, series) ->
                if (series.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader(genre.displayName, onSeeAll = { onBrowseSection("genre", genre.slug) })
                    }
                    item {
                        SeriesRow(series, onSeriesClick)
                    }
                }
            }

            // Legacy Genre nur wenn keine provider-genres geladen
            if (state.genreRows.isEmpty()) {
                if (state.action.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader(stringResource(R.string.home_genre_action))
                    }
                    item {
                        SeriesRow(state.action, onSeriesClick)
                    }
                }
                if (state.drama.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader(stringResource(R.string.home_genre_drama))
                    }
                    item {
                        SeriesRow(state.drama, onSeriesClick)
                    }
                }
                if (state.scifi.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader(stringResource(R.string.home_genre_scifi))
                    }
                    item {
                        SeriesRow(state.scifi, onSeriesClick)
                    }
                }
                if (state.comedy.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader(stringResource(R.string.home_genre_comedy))
                    }
                    item {
                        SeriesRow(state.comedy, onSeriesClick)
                    }
                }
            }

            // Alle Titel
            if (state.popular.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader(
                        when {
                            state.supportsMovies && state.supportsSeries -> stringResource(R.string.home_all_titles)
                            state.supportsMovies -> stringResource(R.string.home_all_movies)
                            else -> stringResource(R.string.home_all_series)
                        },
                        onSeeAll = { onBrowseSection("all", null) }
                    )
                }
                item {
                    SeriesRow(
                        (state.popular + state.newest + state.trending + state.movies).distinctBy { it.id },
                        onSeriesClick
                    )
                }
            }

            // Bottom spacing for BottomBar
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SeriesRow(
    series: List<Series>,
    onSeriesClick: (String) -> Unit,
    initialFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val distinct = series.distinctBy { it.id }
    LazyRow(
        Modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(distinct, key = { it.id }) { s ->
            val isFirst = s.id == distinct.firstOrNull()?.id
            SeriesPosterCard(
                s,
                onClick = { onSeriesClick(s.id) },
                focusRequester = if (isFirst) initialFocusRequester else null
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(
    series: List<Series>,
    onClick: (String) -> Unit,
    autoScrollEnabled: Boolean = true,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val pagerState = rememberPagerState(pageCount = { series.size })
    val context = LocalContext.current
    var heroFocused by remember { mutableStateOf(false) }

    // Auto-scroll – pausiert bei D-Pad-Fokus auf dem Hero
    LaunchedEffect(pagerState, series.size, autoScrollEnabled, heroFocused) {
        if (!autoScrollEnabled || series.size <= 1 || heroFocused) return@LaunchedEffect
        try {
            while (true) {
                kotlinx.coroutines.delay(5000)
                kotlinx.coroutines.yield()
                if (heroFocused || pagerState.isScrollInProgress) continue
                val next = (pagerState.currentPage + 1) % series.size
                pagerState.animateScrollToPage(next, animationSpec = tween(800))
            }
        } catch (_: kotlinx.coroutines.CancellationException) {}
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .onFocusChanged { heroFocused = it.hasFocus }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val s = series[page]
            var isLoading by remember(s.id) { mutableStateOf(true) }
            var isError by remember(s.id) { mutableStateOf(false) }
            val shouldLoadImage = kotlin.math.abs(page - pagerState.currentPage) <= 1

            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (page == 0 && focusRequester != null) {
                            Modifier.tvFocusable(focusRequester = focusRequester).tvFocusRing(cornerRadius = 0.dp)
                        } else {
                            Modifier.tvFocusable().tvFocusRing(cornerRadius = 0.dp)
                        }
                    )
                    .clickable { onClick(s.id) }
            ) {
                if (!s.coverUrl.isNullOrBlank() && !isError && shouldLoadImage) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(s.coverUrl)
                            .crossfade(true)
                            .size(1280, 720)
                            .addHeader(
                                "Referer",
                                com.novastream.app.util.MediaUrls.refererFor(s.coverUrl)
                            )
                            .addHeader(
                                "User-Agent",
                                com.novastream.app.data.model.NovaStreamConfig.USER_AGENT
                            )
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
                            s.title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "—",
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

                // Loading shimmer overlay
                if (isLoading && !isError) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0x11FFFFFF),
                                        Color(0x22FFFFFF),
                                        Color(0x11FFFFFF)
                                    )
                                )
                            )
                    )
                }

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
                                contentDescription = stringResource(R.string.cd_watch),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_watch),
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
