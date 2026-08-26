package com.novastream.app.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.theme.*

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onNextEpisode: (Int, Int, String) -> Unit = { _, _, _ -> }
) {
    val vm: PlayerViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Lock to landscape orientation while in player
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val currentSource = state.currentSource
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var showHosters by remember { mutableStateOf(true) }
    var playerVisible by remember { mutableStateOf(false) }
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }

    // Back handler: close hoster panel or next-episode overlay first, then exit
    androidx.activity.compose.BackHandler {
        when {
            showNextEpisodeOverlay -> showNextEpisodeOverlay = false
            showHosters -> showHosters = false
            else -> onBack()
        }
    }

    // Track listener to avoid adding duplicates on player reuse
    val episodeEndListener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    vm.onEpisodeFinished()
                    showNextEpisodeOverlay = true
                }
            }
        }
    }

    // Create player when source changes
    LaunchedEffect(currentSource?.url) {
        showNextEpisodeOverlay = false
        val src = currentSource ?: return@LaunchedEffect

        // Reuse existing player if possible, else create new
        val existing = exoPlayer
        if (existing != null) {
            val mediaItem = MediaItem.Builder()
                .setUri(src.url)
                .setMimeType(src.mimeType)
                .build()
            existing.setMediaItem(mediaItem)
            if (state.resumePositionMs > 0) existing.seekTo(state.resumePositionMs)
            existing.prepare()
            existing.playWhenReady = true
            // Listener already added from initial creation - no need to re-add
            playerVisible = true
            showHosters = false
        } else {
            val player = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(src.url)
                    .setMimeType(src.mimeType)
                    .build()
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                if (state.resumePositionMs > 0) {
                    seekTo(state.resumePositionMs)
                }
            }
            player.addListener(episodeEndListener)
            exoPlayer = player
            playerVisible = true
            showHosters = false
        }
    }

    // Save progress periodically
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        try {
            while (true) {
                kotlinx.coroutines.delay(5000)
                if (!player.isPlaying) continue
                val pos = player.currentPosition
                val dur = player.duration
                if (dur > 0 && pos > 0) {
                    vm.saveProgress(pos, dur)
                }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Expected when leaving screen
        }
    }

    // Save progress on dispose and release player
    DisposableEffect(Unit) {
        onDispose {
            val player = exoPlayer
            if (player != null) {
                val pos = player.currentPosition
                val dur = player.duration
                if (dur > 0 && pos > 0) {
                    vm.saveProgress(pos, dur)
                }
                player.release()
            }
            exoPlayer = null
        }
    }

    val density = LocalDensity.current
    val navBarHeightDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarHeightPx = remember(density, navBarHeightDp) {
        with(density) { navBarHeightDp.toPx() }.toInt()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player
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
                        setPadding(0, 0, 0, navBarHeightPx)
                        if (showHosters) hideController()
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.setPadding(0, 0, 0, navBarHeightPx)
                    if (showHosters) pv.hideController() else pv.showController()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (state.loading) {
            PremiumLoading(label = "Stream wird aufgelöst…")
        } else if (state.error != null && state.hosters.isEmpty()) {
            PremiumError(state.error ?: "Unbekannter Fehler")
        }

        // Loading overlay when switching hosters (player visible but loading new source)
        if (playerVisible && state.loading && exoPlayer != null) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Top overlay: Back + Title + Hoster toggle
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
            Column(Modifier.weight(1f)) {
                if (state.seriesTitle.isNotBlank()) {
                    Text(
                        state.seriesTitle,
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    state.episodeTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (playerVisible && state.hosters.isNotEmpty()) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GlassMedium)
                        .clickable { showHosters = !showHosters },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (showHosters) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        "Hoster",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Bottom overlay: Hoster pills (collapsible)
        AnimatedVisibility(
            visible = showHosters && state.hosters.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.2f to Color(0xAA000000),
                            1f to Color(0xF0000000)
                        )
                    )
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 32.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                    )
            ) {
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
                if (state.loading) {
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

        // Next Episode overlay (shown when episode ends) - with auto-play countdown
        AnimatedVisibility(
            visible = showNextEpisodeOverlay && state.nextEpisode != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val next = state.nextEpisode
            if (next != null) {
                // Auto-play countdown (5 seconds) - cancels if overlay dismissed
                var countdown by remember { mutableStateOf(5) }
                LaunchedEffect(showNextEpisodeOverlay, next) {
                    if (showNextEpisodeOverlay) {
                        countdown = 5
                        try {
                            while (countdown > 0) {
                                kotlinx.coroutines.delay(1000)
                                kotlinx.coroutines.yield()
                                countdown--
                            }
                            // Only auto-play if overlay is still showing
                            if (showNextEpisodeOverlay) {
                                showNextEpisodeOverlay = false
                                onNextEpisode(next.season, next.episode, next.title)
                            }
                        } catch (_: kotlinx.coroutines.CancellationException) {
                            // Overlay dismissed or screen left - countdown cancelled
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xE6000000))
                        .clickable {
                            // Tap anywhere to cancel auto-play and stay
                            showNextEpisodeOverlay = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nächste Folge",
                            color = Color.White.copy(0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "S${next.season} E${next.episode} · ${next.title}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(PrimaryGradient)
                                .clickable {
                                    showNextEpisodeOverlay = false
                                    onNextEpisode(next.season, next.episode, next.title)
                                }
                                .padding(horizontal = 32.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SkipNext, "Nächste", tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Weiter",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "($countdown)",
                                color = Color.White.copy(0.7f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tippen um abzubrechen",
                            color = Color.White.copy(0.4f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
