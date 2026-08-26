package com.serienstream.app.ui.player

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.serienstream.app.ui.components.PremiumError
import com.serienstream.app.ui.components.PremiumLoading
import com.serienstream.app.ui.theme.*

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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player area 16:9
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
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

            // Back button overlay (top-left, glassmorphism) – mit Status Bar Inset
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                        start = 12.dp
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

        // Hoster selection
        Column(
            Modifier
                .fillMaxSize()
                .background(BgPure)
        ) {
            if (state.hosters.isNotEmpty()) {
                Text(
                    "Hoster wählen",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(state.hosters) { i, h ->
                        val selected = i == state.selectedHosterIndex
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) PrimaryGradient else Brush.linearGradient(listOf(BgSurfaceElevated, BgSurfaceElevated)))
                                .clickable { vm.selectHoster(i) }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(
                                h.name,
                                color = if (selected) Color.White else TextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (h.language.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    h.language,
                                    color = if (selected) Color.White.copy(0.8f) else TextTertiary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            if (state.sources.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "${state.sources.size} Quelle(n) verfügbar",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (state.error != null && state.hosters.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                PremiumError(state.error!!)
            }
        }
    }
}
