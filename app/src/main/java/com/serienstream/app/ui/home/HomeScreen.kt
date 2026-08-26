package com.serienstream.app.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.serienstream.app.data.model.Series
import com.serienstream.app.ui.components.PremiumEmpty
import com.serienstream.app.ui.components.PremiumError
import com.serienstream.app.ui.components.PremiumLoading
import com.serienstream.app.ui.components.SectionHeader
import com.serienstream.app.ui.components.SeriesPosterCard
import com.serienstream.app.ui.components.ShimmerPoster
import com.serienstream.app.ui.components.ShimmerRow
import com.serienstream.app.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onSeriesClick: (String) -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(BgPure)
    ) {
        // Hero Banner Karussell
        item {
            if (state.loading && state.popular.isEmpty()) {
                ShimmerBox(
                    Modifier.fillMaxWidth().height(280.dp),
                    cornerRadius = 0
                )
            } else if (state.popular.isNotEmpty()) {
                HeroCarousel(
                    series = state.popular.take(5),
                    onClick = onSeriesClick
                )
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
            }
        }

        // Error State
        if (state.error != null && state.popular.isEmpty()) {
            item {
                PremiumError(
                    message = state.error!!,
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
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.popular, key = { it.id }) { s ->
                        SeriesPosterCard(s, onClick = { onSeriesClick(s.id) })
                    }
                }
            }
        }

        // Neu hinzugefügt
        if (state.newest.isNotEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Neu hinzugefügt")
            }
            item {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(580.dp)
                ) {
                    items(state.newest, key = { it.id }) { s ->
                        SeriesPosterCard(s, onClick = { onSeriesClick(s.id) })
                    }
                }
            }
        }

        // Bottom spacing for BottomBar
        item { Spacer(Modifier.height(80.dp)) }
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
    LaunchedEffect(pagerState) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            val next = (pagerState.currentPage + 1) % series.size
            pagerState.animateScrollToPage(next, animationSpec = tween(800))
        }
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
            var isLoading by remember { mutableStateOf(true) }
            var isError by remember { mutableStateOf(false) }

            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { onClick(s.id) }
            ) {
                if (!s.coverUrl.isNullOrBlank() && !isError) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(s.coverUrl)
                            .crossfade(true)
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
                            s.title.take(2).uppercase(),
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
                Box(
                    Modifier
                        .size(if (selected) 24.dp else 8.dp, 4.dp)
                        .clip(CircleShape)
                        .background(if (selected) Primary else Color(0x66FFFFFF))
                )
            }
        }
    }
}

@Composable
private fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8
) {
    com.serienstream.app.ui.components.ShimmerBox(modifier, cornerRadius)
}
