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
import com.novastream.app.data.model.Episode
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.components.SectionHeader
import com.novastream.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onPlay: (slug: String, season: Int, episode: Int, title: String) -> Unit
) {
    val vm: DetailViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val series = state.series

    Box(Modifier.fillMaxSize().background(BgPure)) {
        when {
            state.loading -> PremiumLoading(label = "Serie wird geladen…")
            state.error != null -> PremiumError(state.error!!)
            series != null -> DetailContent(
                state = state,
                slug = series.id,
                onBack = onBack,
                onSelectSeason = vm::selectSeason,
                onPlay = onPlay
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    state: DetailUiState,
    slug: String,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPlay: (String, Int, Int, String) -> Unit
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
                            series.title.take(2).uppercase(),
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

                // Back button (glassmorphism) – mit Status Bar Inset
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
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Title + Description
        item {
            Column(Modifier.padding(20.dp)) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                series.description?.let { desc ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Season Tabs
        if (state.seasons.isNotEmpty()) {
            item {
                SectionHeader("Staffeln")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.seasons) { i, season ->
                        val selected = i == state.selectedSeasonIndex
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) PrimaryGradient else Brush.linearGradient(listOf(BgSurfaceElevated, BgSurfaceElevated)))
                                .clickable { onSelectSeason(i) }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                if (season.number == 0) "Filme" else "Staffel ${season.number}",
                                color = if (selected) Color.White else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
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
                PremiumEpisodeRow(
                    episode = ep,
                    onPlay = { onPlay(slug, season.number, ep.number, ep.title) }
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
private fun PremiumEpisodeRow(
    episode: Episode,
    onPlay: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode number in a circle
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(BgSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${episode.number}",
                color = Accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                episode.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
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
        }
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
    Divider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
}
