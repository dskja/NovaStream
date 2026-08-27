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
import androidx.compose.foundation.focusable
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
import com.novastream.app.ui.tv.tvPlayerKeyHandler

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

    // Lock to landscape orientation + immersive fullscreen
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        val originalSystemBarsBehavior = controller?.systemBarsBehavior
        controller?.let {
            it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            if (originalSystemBarsBehavior != null) {
                controller?.systemBarsBehavior = originalSystemBarsBehavior
            }
        }
    }

    val currentSource = state.currentSource
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var showHosters by remember { mutableStateOf(true) }
    var playerVisible by remember { mutableStateOf(false) }
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    // Back handler
    androidx.activity.compose.BackHandler {
        when {
            showNextEpisodeOverlay -> showNextEpisodeOverlay = false
            showHosters -> showHosters = false
            else -> onBack()
        }
    }

    // Track listener for episode end
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

    // Create/recreate player when source URL changes
    // KEY FIX: Only release+recreate when we have a NEW valid source URL
    // This prevents black screen during hoster switching when sources is empty
    LaunchedEffect(currentSource?.url) {
        showNextEpisodeOverlay = false
        val src = currentSource ?: return@LaunchedEffect
        val url = src.url
        if (url.isBlank()) return@LaunchedEffect

        // Skip if we already loaded this exact URL
        if (url == lastLoadedUrl && exoPlayer != null) return@LaunchedEffect

        // Release old player
        exoPlayer?.let { old ->
            try {
                old.removeListener(episodeEndListener)
                old.release()
            } catch (_: Exception) {}
        }
        exoPlayer = null

        // Create new player
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
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
        lastLoadedUrl = url
        playerVisible = true
        showHosters = false
    }

    // Save progress periodically (only when position changed - avoids unnecessary DB writes)
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        var lastSavedPos = 0L
        try {
            while (true) {
                kotlinx.coroutines.delay(5000)
                if (!player.isPlaying) continue
                val pos = player.currentPosition
                val dur = player.duration
                if (dur > 0 && pos > 0 && kotlin.math.abs(pos - lastSavedPos) > 3000) {
                    vm.saveProgress(pos, dur)
                    lastSavedPos = pos
                }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {}
    }

    // Save progress on dispose and release player
    DisposableEffect(Unit) {
        onDispose {
            val player = exoPlayer
            if (player != null) {
                try {
                    val pos = player.currentPosition
                    val dur = player.duration
                    if (dur > 0 && pos > 0) {
                        vm.saveProgress(pos, dur)
                    }
                } catch (_: Exception) {}
                try { player.removeListener(episodeEndListener) } catch (_: Exception) {}
                try { player.release() } catch (_: Exception) {}
            }
            exoPlayer = null
            playerVisible = false
            showHosters = false
            lastLoadedUrl = null
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
            .tvPlayerKeyHandler(
                onPlayPause = {
                    val p = exoPlayer
                    if (p != null) {
                        if (p.isPlaying) p.pause() else p.play()
                    }
                },
                onSeekForward = {
                    val p = exoPlayer
                    if (p != null && p.duration > 0) {
                        p.seekTo((p.currentPosition + 10000).coerceAtMost(p.duration))
                    }
                },
                onSeekBackward = {
                    val p = exoPlayer
                    if (p != null) {
                        p.seekTo((p.currentPosition - 10000).coerceAtLeast(0))
                    }
                },
                onBack = { onBack() }
            )
    ) {
        // Player - nur anzeigen wenn wir einen validen Source haben
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
                    pv.isFocusable = true
                    pv.isFocusableInTouchMode = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .focusable()
            )
        } else if (state.loading) {
            PremiumLoading(label = "Stream wird aufgelöst…")
        } else if (state.error != null) {
            PremiumError(state.error ?: "Unbekannter Fehler")
        }

        // Loading overlay when switching hosters (player still visible with old content)
        if (playerVisible && state.loading && exoPlayer != null && state.hosterSwitching) {
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

        // Bottom overlay: Hoster pills
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

        // Next Episode overlay
        AnimatedVisibility(
            visible = showNextEpisodeOverlay && state.nextEpisode != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val next = state.nextEpisode
            if (next != null) {
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
                            if (showNextEpisodeOverlay) {
                                showNextEpisodeOverlay = false
                                onNextEpisode(next.season, next.episode, next.title)
                            }
                        } catch (_: kotlinx.coroutines.CancellationException) {}
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xE6000000))
                        .clickable { showNextEpisodeOverlay = false },
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
                        Spacer(Modifier.height(16.dp))

                        Box(
                            Modifier
                                .size(280.dp, 158.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!next.coverUrl.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(next.coverUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = next.title,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0.4f to Color.Transparent,
                                        1f to Color(0xCC000000)
                                    )
                                )
                            )
                            Column(
                                Modifier.align(Alignment.BottomStart).padding(16.dp)
                            ) {
                                Text(
                                    "S${next.season} E${next.episode}",
                                    color = Color.White.copy(0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    next.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

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
