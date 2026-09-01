package com.novastream.app.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.model.Episode
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.components.SectionHeader
import com.novastream.app.ui.components.SeriesPosterCard
import com.novastream.app.ui.components.ShimmerBox
import com.novastream.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onPlay: (slug: String, season: Int, episode: Int, title: String, seriesTitle: String, coverUrl: String?, isMovie: Boolean) -> Unit,
    onRelatedClick: (String) -> Unit = {}
) {
    val vm: DetailViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val series = state.series
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val downloadMsg = state.downloadMessage
    val castHelper = remember { com.novastream.app.cast.CastHelper.get(context) }
    var castPlayer by remember { mutableStateOf<androidx.media3.cast.CastPlayer?>(null) }
    val appSettings = remember { com.novastream.app.data.prefs.AppSettings(context) }
    val castEnabled by appSettings.castEnabled.collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(state.castStreamUrl, state.castStreamTitle) {
        val url = state.castStreamUrl ?: return@LaunchedEffect
        val title = state.castStreamTitle ?: "NovaStream"
        if (castEnabled && castHelper.isAvailable) {
            val cp = castPlayer ?: castHelper.createCastPlayer()?.also { castPlayer = it }
            cp?.let {
                castHelper.loadOnCast(it, url, title)
                snackbarHostState.showSnackbar(context.getString(R.string.detail_cast_to_tv_started))
            }
        }
        vm.clearCastRequest()
    }

    LaunchedEffect(downloadMsg) {
        downloadMsg?.let { key ->
            val text = when (key) {
                "detail_download_started" -> context.getString(R.string.detail_download_started)
                "detail_download_failed" -> context.getString(R.string.detail_download_failed)
                "detail_download_no_source" -> context.getString(R.string.detail_download_no_source)
                "detail_cast_to_tv_failed" -> context.getString(R.string.detail_cast_to_tv_failed)
                else -> key
            }
            snackbarHostState.showSnackbar(text)
            vm.clearDownloadMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(BgPure)) {
        when {
            state.loading -> DetailSkeleton()
            state.error != null -> PremiumError(state.error ?: stringResource(R.string.error_unknown), onRetry = vm::retry)
            series != null -> DetailContent(
                state = state,
                slug = series.id,
                seriesTitle = series.title,
                coverUrl = series.coverUrl,
                onBack = onBack,
                onSelectSeason = vm::selectSeason,
                onPlay = onPlay,
                onToggleWatchlist = vm::toggleWatchlist,
                onRemoveProgress = vm::removeProgress,
                onToggleWatched = vm::toggleEpisodeWatched,
                onMarkSeasonWatched = vm::markSeasonAsWatched,
                onMarkSeasonUnwatched = vm::markSeasonAsUnwatched,
                onRelatedClick = onRelatedClick,
                onDownload = vm::downloadCurrentEpisode,
                onCast = vm::castCurrentEpisode,
                castEnabled = castEnabled && castHelper.isAvailable,
                casting = state.casting
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    state: DetailUiState,
    slug: String,
    seriesTitle: String,
    coverUrl: String?,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPlay: (String, Int, Int, String, String, String?, Boolean) -> Unit,
    onToggleWatchlist: () -> Unit,
    onRemoveProgress: (String) -> Unit,
    onToggleWatched: (Int, Int, String) -> Unit,
    onMarkSeasonWatched: (Int) -> Unit,
    onMarkSeasonUnwatched: (Int) -> Unit,
    onRelatedClick: (String) -> Unit,
    onDownload: () -> Unit,
    onCast: () -> Unit,
    castEnabled: Boolean,
    casting: Boolean
) {
    val series = state.series ?: return
    val context = LocalContext.current
    var imageError by remember { mutableStateOf(false) }
    var episodeFilter by remember { mutableStateOf("") }

    // Filter zurücksetzen wenn Staffel oder Serie gewechselt wird
    LaunchedEffect(state.selectedSeasonIndex, series.id) {
        episodeFilter = ""
    }

    LazyColumn(
        Modifier.fillMaxSize().background(BgPure)
    ) {
        if (state.providerMismatch && state.loading) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Primary.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        stringResource(R.string.detail_provider_reloading),
                        color = Primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        // Backdrop Hero
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                if (!series.coverUrl.isNullOrBlank() && !imageError) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(series.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onState = { imageError = it is AsyncImagePainter.State.Error }
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(BgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            series.title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "—",
                            color = Accent,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Gradient overlays
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to BgPure
                        )
                    )
                )

                // Back button
                Box(
                    Modifier
                        .padding(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                            start = 16.dp
                        )
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassMedium)
                        .clickable(onClick = onBack)
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.cd_back_to_overview),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Continue Watching Banner (if there's a saved position)
        val progress = state.currentProgress
        if (progress != null && !progress.isCompleted) {
            item {
                ContinueWatchingBanner(
                    progress = progress,
                    onPlay = { onPlay(slug, progress.season, progress.episode, progress.episodeTitle, seriesTitle, coverUrl, progress.isMovie) },
                    onRemove = { onRemoveProgress(progress.episodeKey) }
                )
            }
        } else {
            // Play button when no continue watching exists
            val firstSeason = state.seasons.firstOrNull { it.episodes.isNotEmpty() }
            val firstEp = firstSeason?.episodes?.firstOrNull()
            if (series.isMovie || firstEp != null) {
                val playSeason = if (series.isMovie) 1 else firstSeason!!.number
                val epNum = if (series.isMovie) 1 else firstEp!!.number
                val epTitle = if (series.isMovie) series.title else firstEp!!.title
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(PrimaryGradient)
                            .clickable {
                                onPlay(slug, playSeason, epNum, epTitle, seriesTitle, coverUrl, series.isMovie)
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (series.isMovie) stringResource(R.string.detail_play_movie) else stringResource(R.string.detail_play_first_episode),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        // Title + Description
        item {
            var expanded by androidx.compose.runtime.saveable.rememberSaveable(slug) { mutableStateOf(false) }
            Column(Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        series.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Watchlist button
                    Box(
                        Modifier
                            .padding(start = 12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BgSurfaceElevated)
                            .clickable(onClick = onToggleWatchlist)
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.inWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkAdd,
                            contentDescription = if (state.inWatchlist) stringResource(R.string.detail_remove_from_watchlist) else stringResource(R.string.detail_add_to_watchlist),
                            tint = if (state.inWatchlist) Primary else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BgSurfaceElevated)
                            .clickable(onClick = onDownload, enabled = !state.downloading)
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.downloading) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Primary)
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.detail_download_episode),
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (castEnabled) {
                        Box(
                            Modifier
                                .padding(start = 8.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BgSurfaceElevated)
                                .clickable(onClick = onCast, enabled = !casting)
                                .focusable(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (casting) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Primary)
                            } else {
                                Icon(
                                    Icons.Default.Cast,
                                    contentDescription = stringResource(R.string.detail_cast_to_tv),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BgSurfaceElevated)
                            .clickable {
                                val deepLink = "novastream://detail/$slug"
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.detail_share_message_fmt, series.title, deepLink))
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.detail_share_chooser)))
                            }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.cd_share),
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (!state.trailerUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(BgSurfaceElevated)
                            .clickable {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(state.trailerUrl)
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SmartDisplay,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.detail_watch_trailer),
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                series.description?.let { desc ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                    if (desc.length > 200) {
                        Text(
                            if (expanded) stringResource(R.string.detail_show_less) else stringResource(R.string.detail_show_more),
                            color = Primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { expanded = !expanded }
                        )
                    }
                }
                // Free metadata (TVMaze) pills
                if (series.genres.isNotEmpty() || state.metaRating != null || state.metaNetwork != null || state.imdbId != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                    ) {
                        state.metaRating?.let { StatPill("★ ${String.format("%.1f", it)}") }
                        series.year?.let { StatPill(it) }
                        state.metaNetwork?.let { StatPill(it) }
                        state.imdbId?.let { StatPill(it) }
                    }
                    if (series.genres.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            series.genres.take(6).joinToString(" · "),
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
                if (state.metaCast.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.detail_cast), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.metaCast.take(8).joinToString(" · ") {
                            if (!it.character.isNullOrBlank()) "${it.name} (${it.character})" else it.name
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.alsoOnProviders.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.detail_also_on), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.alsoOnProviders.joinToString(" · "),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Series stats: seasons and total episodes
                val totalEpisodes = state.seasons.sumOf { it.episodes.size }
                val watchedEpisodes = state.seasons.sumOf { season ->
                    season.episodes.count { ep ->
                        state.progressFor(season.number, ep.number)?.isCompleted == true
                    }
                }
                if (!series.isMovie && state.seasons.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatPill(stringResource(R.string.detail_seasons_count_fmt, state.seasons.size))
                        if (totalEpisodes > 0) {
                            StatPill(stringResource(R.string.detail_episodes_count_fmt, totalEpisodes))
                        }
                        if (watchedEpisodes > 0 && totalEpisodes > 0) {
                            val percent = (watchedEpisodes * 100 / totalEpisodes).coerceIn(0, 100)
                            StatPill(stringResource(R.string.detail_watched_count_fmt, watchedEpisodes, percent))
                        }
                    }
                } else if (series.isMovie) {
                    Spacer(Modifier.height(12.dp))
                    StatPill(stringResource(R.string.movie_badge))
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Seasons & Episodes (series only)
        if (!series.isMovie) {
        item { SectionHeader(stringResource(R.string.detail_seasons_header)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.seasons, key = { idx, s -> "season-$idx-${s.number}" }) { i, season ->
                    val selected = i == state.selectedSeasonIndex
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) PrimaryGradient else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
                            .clickable { onSelectSeason(i) }
                            .focusable()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.detail_season_label_fmt, season.number),
                                color = if (selected) Color.White else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (season.episodes.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${season.episodes.size}",
                                    color = if (selected) Color.White.copy(alpha = 0.7f) else TextTertiary,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color.White.copy(alpha = 0.15f) else Color(0x15FFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Episodes
        val season = state.selectedSeason
        if (state.loadingSeason) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                }
            }
        } else if (season != null && season.episodes.isNotEmpty()) {
            // Episode filter for seasons with many episodes
            val filteredEpisodes = if (episodeFilter.isBlank()) {
                season.episodes
            } else {
                season.episodes.filter { ep ->
                    ep.title.contains(episodeFilter, ignoreCase = true) ||
                    ep.number.toString() == episodeFilter
                }
            }

            item { SectionHeader(stringResource(R.string.detail_episodes_header), trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val watchedCount = state.selectedSeasonWatchedCount
                    val totalCount = season.episodes.size
                    if (watchedCount > 0) {
                        Text(
                            stringResource(R.string.detail_watched_progress_fmt, watchedCount, totalCount),
                            color = Accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        stringResource(R.string.detail_episodes_count_short_fmt, season.episodes.size),
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(12.dp))
                    // Toggle: Alle als gesehen / Alle als ungesehen
                    val allWatched = watchedCount == totalCount && totalCount > 0
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (allWatched) Color(0x22FF4444) else Primary.copy(alpha = 0.12f))
                            .clickable {
                                if (allWatched) onMarkSeasonUnwatched(season.number)
                                else onMarkSeasonWatched(season.number)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (allWatched) stringResource(R.string.detail_mark_all_unwatched) else stringResource(R.string.detail_mark_all_watched),
                            color = if (allWatched) Color(0xFFFF6666) else Primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }) }

            // Filter field (only for seasons with >10 episodes)
            if (season.episodes.size > 10) {
                item {
                    OutlinedTextField(
                        value = episodeFilter,
                        onValueChange = { episodeFilter = it },
                        placeholder = { Text(stringResource(R.string.detail_filter_episodes_placeholder), color = TextTertiary, style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (episodeFilter.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Close,
                                    stringResource(R.string.cd_clear),
                                    tint = TextTertiary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { episodeFilter = "" }
                                )
                            }
                        },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Outline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(filteredEpisodes, key = { "${state.selectedSeason?.number}-${it.number}" }) { ep ->
                val epProgress = state.progressFor(season.number, ep.number)
                PremiumEpisodeRow(
                    episode = ep,
                    progress = epProgress,
                    onPlay = { onPlay(slug, season.number, ep.number, ep.title, seriesTitle, coverUrl, series.isMovie) },
                    onLongPress = { onToggleWatched(season.number, ep.number, ep.title) }
                )
            }

            // No results from filter
            if (filteredEpisodes.isEmpty() && episodeFilter.isNotBlank()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.detail_no_episodes_for_filter_fmt, episodeFilter), color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (season != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.detail_no_episodes), color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
        } // end !series.isMovie

        if (state.relatedTitles.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.detail_related_titles)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.relatedTitles, key = { it.id }) { related ->
                        SeriesPosterCard(
                            series = related,
                            onClick = { onRelatedClick(related.id) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (series.isMovie) {
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ContinueWatchingBanner(
    progress: WatchProgress,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var imgError by remember(progress.episodeKey) { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Primary.copy(alpha = 0.08f), BgSurface, BgSurface)
                )
            )
            .padding(16.dp)
    ) {
        Column {
            // Header row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(4.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PrimaryGradient)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.detail_continue_watching_header),
                    color = Primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                // Remove button
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BgSurfaceElevated)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_remove),
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Content row: thumbnail + info + play
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPlay),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail with play overlay
                Box(
                    Modifier
                        .size(120.dp, 68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (!progress.coverUrl.isNullOrBlank() && !imgError) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(progress.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = progress.seriesTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onState = { imgError = it is AsyncImagePainter.State.Error }
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(BgSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                            stringResource(R.string.detail_episode_compact_fmt, progress.season, progress.episode),
                                color = Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Dark overlay
                    Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                    // Play icon
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            stringResource(R.string.cd_play),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Progress bar on thumbnail
                    LinearProgressIndicator(
                        progress = { progress.progressPercent / 100f },
                        color = Primary,
                        trackColor = Color(0x44FFFFFF),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                // Info
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.detail_season_episode_fmt, progress.season, progress.episode),
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        progress.episodeTitle,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.detail_progress_remaining_fmt, progress.progressPercent.toInt(), formatRemaining(progress)),
                        color = Primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PremiumEpisodeRow(
    episode: Episode,
    progress: WatchProgress? = null,
    onPlay: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current
    var thumbError by remember(episode.episodeUrl) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongPress
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode thumbnail (16:9)
        Box(
            Modifier
                .size(120.dp, 68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!episode.thumbnailUrl.isNullOrBlank() && !thumbError) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(episode.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { thumbError = it is AsyncImagePainter.State.Error }
                )
            } else {
                // Fallback: episode number
                Box(
                    Modifier.fillMaxSize().background(BgSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.detail_episode_fallback_fmt, episode.number),
                        color = Accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress overlay
            if (progress != null && progress.isCompleted) {
                // Completed: checkmark overlay (replaces play icon)
                Box(
                    Modifier.fillMaxSize().background(Color(0x88000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.cd_watched), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                // Play icon overlay (only when not completed)
                Box(
                    Modifier.fillMaxSize().background(Color(0x33000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        stringResource(R.string.cd_play),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Progress bar at bottom (for partially watched, not completed)
            if (progress != null && !progress.isCompleted && progress.positionMs > 0) {
                LinearProgressIndicator(
                    progress = { (progress.progressPercent / 100f).coerceIn(0f, 1f) },
                    color = Primary,
                    trackColor = Color(0x44FFFFFF),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Episode info
        Column(Modifier.weight(1f)) {
            Text(
                "${episode.number}. ${episode.title}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.hosters.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    episode.hosters.take(3).forEach { h ->
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(BgSurfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                h.name,
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
            // Progress text
            if (progress != null && !progress.isCompleted && progress.positionMs > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.detail_remaining_min_fmt, formatRemaining(progress)),
                    color = Primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            } else if (progress != null && progress.isCompleted) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.detail_watched),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Play button
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PrimaryGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_play),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
    HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
}

private fun formatRemaining(progress: WatchProgress): Int {
    if (progress.durationMs <= 0) return 0
    val remainingMs = progress.durationMs - progress.positionMs
    return (remainingMs / 60000).toInt().coerceAtLeast(0)
}

@Composable
private fun StatPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.fillMaxSize().background(BgPure)) {
        // Hero skeleton
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .height(320.dp),
            cornerRadius = 0
        )
        // Title skeleton
        Column(Modifier.padding(20.dp)) {
            ShimmerBox(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(28.dp),
                cornerRadius = 6
            )
            Spacer(Modifier.height(16.dp))
            ShimmerBox(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                cornerRadius = 4
            )
            Spacer(Modifier.height(8.dp))
            ShimmerBox(
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(14.dp),
                cornerRadius = 4
            )
            Spacer(Modifier.height(8.dp))
            ShimmerBox(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp),
                cornerRadius = 4
            )
            Spacer(Modifier.height(16.dp))
            // Stat pills skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.width(100.dp).height(28.dp), cornerRadius = 12)
                ShimmerBox(Modifier.width(120.dp).height(28.dp), cornerRadius = 12)
            }
        }
        // Season chips skeleton
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                ShimmerBox(Modifier.width(110.dp).height(36.dp), cornerRadius = 20)
            }
        }
        // Episode rows skeleton
        repeat(5) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(Modifier.size(120.dp, 68.dp), cornerRadius = 8)
                Spacer(Modifier.width(16.dp))
                Column {
                    ShimmerBox(Modifier.fillMaxWidth(0.8f).height(16.dp), cornerRadius = 4)
                    Spacer(Modifier.height(6.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.5f).height(12.dp), cornerRadius = 4)
                }
            }
        }
    }
}
