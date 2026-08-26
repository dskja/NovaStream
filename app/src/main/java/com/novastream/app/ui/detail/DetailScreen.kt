package com.novastream.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.model.Episode
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.components.SectionHeader
import com.novastream.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onPlay: (slug: String, season: Int, episode: Int, title: String, seriesTitle: String, coverUrl: String?) -> Unit
) {
    val vm: DetailViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val series = state.series

    Box(Modifier.fillMaxSize().background(BgPure)) {
        when {
            state.loading -> PremiumLoading(label = "Serie wird geladen…")
            state.error != null -> PremiumError(state.error ?: "Unbekannter Fehler", onRetry = vm::retry)
            series != null -> DetailContent(
                state = state,
                slug = series.id,
                seriesTitle = series.title,
                coverUrl = series.coverUrl,
                onBack = onBack,
                onSelectSeason = vm::selectSeason,
                onPlay = onPlay,
                onToggleWatchlist = vm::toggleWatchlist,
                onRemoveProgress = vm::removeProgress
            )
        }
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
    onPlay: (String, Int, Int, String, String, String?) -> Unit,
    onToggleWatchlist: () -> Unit,
    onRemoveProgress: (String) -> Unit
) {
    val series = state.series ?: return
    val context = LocalContext.current
    var imageError by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(BgPure)
    ) {
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
                            .crossfade(false)
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
                            series.title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "??",
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
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Zurück",
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
                    onPlay = { onPlay(slug, progress.season, progress.episode, progress.episodeTitle, seriesTitle, coverUrl) },
                    onRemove = { onRemoveProgress(progress.episodeKey) }
                )
            }
        }

        // Title + Description
        item {
            var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
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
                            .clickable(onClick = onToggleWatchlist),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.inWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkAdd,
                            contentDescription = if (state.inWatchlist) "Aus Watchlist entfernen" else "Zur Watchlist hinzufügen",
                            tint = if (state.inWatchlist) Primary else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
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
                    if (!expanded && desc.length > 200) {
                        Text(
                            "Mehr anzeigen",
                            color = Primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { expanded = true }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Seasons
        item { SectionHeader("Staffeln") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.seasons) { i, season ->
                    val selected = i == state.selectedSeasonIndex
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) PrimaryGradient else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
                            .clickable { onSelectSeason(i) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Staffel ${season.number}",
                            color = if (selected) Color.White else TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge
                        )
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
            item { SectionHeader("Episoden") }
            items(season.episodes, key = { it.number }) { ep ->
                val epProgress = state.episodeProgress["$slug-${season.number}-${ep.number}"]
                PremiumEpisodeRow(
                    episode = ep,
                    progress = epProgress,
                    onPlay = { onPlay(slug, season.number, ep.number, ep.title, seriesTitle, coverUrl) }
                )
            }
        } else if (season != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Keine Episoden gefunden", color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
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
                    "WEITERSEHEN",
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
                        contentDescription = "Entfernen",
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
                                .crossfade(false)
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
                                "S${progress.season}E${progress.episode}",
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
                            "Abspielen",
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
                        "Staffel ${progress.season} · Episode ${progress.episode}",
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
                        "${progress.progressPercent.toInt()}% gesehen · Noch ${formatRemaining(progress)} min",
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
private fun PremiumEpisodeRow(
    episode: Episode,
    progress: WatchProgress? = null,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    var thumbError by remember(episode.episodeUrl) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
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
                        .crossfade(false)
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
                        "E${episode.number}",
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
                        Icon(Icons.Default.Check, "Gesehen", tint = Color.White, modifier = Modifier.size(18.dp))
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
                        "Abspielen",
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
                    "Noch ${formatRemaining(progress)} min",
                    color = Primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            } else if (progress != null && progress.isCompleted) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gesehen",
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
                contentDescription = "Abspielen",
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
