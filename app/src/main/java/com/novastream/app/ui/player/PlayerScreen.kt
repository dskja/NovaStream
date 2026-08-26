package com.novastream.app.ui.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.theme.*

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val vm: PlayerViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val currentSource = state.currentSource
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(currentSource?.url) {
        exoPlayer?.release()
        exoPlayer = null
        val src = currentSource ?: return@LaunchedEffect
        val player = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(src.url)
                .setMimeType(src.mimeType)
                .build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
        exoPlayer = player
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer?.release(); exoPlayer = null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player fills the entire screen
        val player = exoPlayer
        if (player != null && currentSource != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = true
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
        } else if (state.loading) {
            PremiumLoading(label = "Stream wird aufgelöst…")
        } else if (state.error != null) {
            PremiumError(state.error!!)
        }

        // Top overlay: Back button + Episode title
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xCC000000),
                        1f to Color.Transparent
                    )
                )
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    start = 12.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Box(
                Modifier
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
            Spacer(Modifier.width(12.dp))
            // Episode title
            Text(
                state.episodeTitle,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom overlay: Hoster pills (compact, only when hosters available)
        if (state.hosters.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to Color(0xCC000000),
                            1f to Color(0xE6000000)
                        )
                    )
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 24.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                    )
            ) {
                if (state.hosters.isNotEmpty()) {
                    Text(
                        "Hoster",
                        color = Color.White.copy(0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.hosters) { i, h ->
                            val selected = i == state.selectedHosterIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (selected) PrimaryGradient
                                        else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF)))
                                    )
                                    .clickable { vm.selectHoster(i) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    h.name,
                                    color = if (selected) Color.White else Color.White.copy(0.85f),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (h.language.isNotBlank()) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        h.language,
                                        color = if (selected) Color.White.copy(0.7f) else Color.White.copy(0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
                // Loading indicator while resolving
                if (state.loading && state.hosters.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Hoster wird aufgelöst…",
                            color = Color.White.copy(0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Error overlay (only if no hosters at all)
        if (state.error != null && state.hosters.isEmpty() && !state.loading) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xE6000000))
                    .padding(24.dp)
            ) {
                PremiumError(state.error!!)
            }
        }
    }
}
