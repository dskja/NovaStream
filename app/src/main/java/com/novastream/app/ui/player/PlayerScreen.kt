@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.novastream.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.tvPlayerKeyHandler
import kotlinx.coroutines.isActive

private const val INTRO_SKIP_POSITION_MS = 90_000L

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onNextEpisode: (Int, Int, String) -> Unit = { _, _, _ -> },
    onPreviousEpisode: (Int, Int, String) -> Unit = { _, _, _ -> }
) {
    val vm: PlayerViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

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
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            if (originalSystemBarsBehavior != null) {
                controller?.systemBarsBehavior = originalSystemBarsBehavior
            }
            PlaybackForegroundService.stop(context)
        }
    }

    val currentSource = state.currentSource
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var showHosters by remember { mutableStateOf(true) }
    var playerVisible by remember { mutableStateOf(false) }
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }
    var showSkipIntro by remember { mutableStateOf(false) }

    fun enterPipIfSupported() {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (exoPlayer == null || currentSource == null) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (_: Exception) {}
    }

    DisposableEffect(lifecycleOwner, exoPlayer, currentSource) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && exoPlayer?.isPlaying == true) {
                enterPipIfSupported()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.activity.compose.BackHandler {
        when {
            showNextEpisodeOverlay -> showNextEpisodeOverlay = false
            showHosters -> showHosters = false
            else -> onBack()
        }
    }

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

    LaunchedEffect(currentSource?.url, state.selectedSourceIndex, state.dataSaverMode) {
        showNextEpisodeOverlay = false
        val src = currentSource ?: return@LaunchedEffect
        val url = src.url
        if (url.isBlank()) {
            exoPlayer?.let { old ->
                try {
                    old.removeListener(episodeEndListener)
                    old.release()
                } catch (_: Exception) {}
            }
            exoPlayer = null
            lastLoadedUrl = null
            return@LaunchedEffect
        }

        val sourceKey = "$url|${src.subtitleUrl.orEmpty()}|${state.dataSaverMode}"
        if (sourceKey == lastLoadedUrl && exoPlayer != null) return@LaunchedEffect

        exoPlayer?.let { old ->
            try {
                old.removeListener(episodeEndListener)
                old.release()
            } catch (_: Exception) {}
        }
        exoPlayer = null

        val trackSelector = createPlayerTrackSelector(context, state.dataSaverMode)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMimeType(src.mimeType)

        src.subtitleUrl?.takeIf { it.isNotBlank() }?.let { subUrl ->
            mediaItemBuilder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUrl))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage("de")
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }

        val player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build().apply {
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
                if (state.resumePositionMs > 0) {
                    seekTo(state.resumePositionMs)
                }
            }
        player.addListener(episodeEndListener)
        exoPlayer = player
        lastLoadedUrl = sourceKey
        playerVisible = true
        showHosters = false
        try {
            player.playbackParameters = androidx.media3.common.PlaybackParameters(state.playbackSpeed)
        } catch (_: Exception) {}

        val playbackTitle = state.episodeTitle.ifBlank { state.seriesTitle }.ifBlank { "NovaStream" }
        PlaybackForegroundService.start(context, playbackTitle)
    }

    LaunchedEffect(state.playbackSpeed, exoPlayer) {
        exoPlayer?.playbackParameters = androidx.media3.common.PlaybackParameters(state.playbackSpeed)
    }

    LaunchedEffect(exoPlayer, state.skipIntroButton) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            kotlinx.coroutines.delay(500)
            val pos = player.currentPosition
            showSkipIntro = state.skipIntroButton &&
                player.isPlaying &&
                pos in 1 until INTRO_SKIP_POSITION_MS
        }
    }

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
            PlaybackForegroundService.stop(context)
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
                onNext = {
                    state.nextEpisode?.let { next ->
                        onNextEpisode(next.season, next.episode, next.title)
                    }
                },
                onPrevious = {
                    state.previousEpisode?.let { prev ->
                        onPreviousEpisode(prev.season, prev.episode, prev.title)
                    }
                },
                onBack = { onBack() }
            )
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
                        useController = !showHosters
                        this.player = player
                        setPadding(0, 0, 0, navBarHeightPx)
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.useController = !showHosters
                    pv.setPadding(0, 0, 0, navBarHeightPx)
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
            PlayerErrorOverlay(
                message = state.error ?: "Unbekannter Fehler",
                canTryAlternateHoster = state.hasAlternateHoster,
                onRetry = { vm.retry() },
                onTryAlternateHoster = { vm.tryAlternateHoster() }
            )
        }

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

        AnimatedVisibility(
            visible = showSkipIntro && playerVisible && exoPlayer != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 72.dp
                )
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(PrimaryGradient)
                    .clickable {
                        exoPlayer?.seekTo(INTRO_SKIP_POSITION_MS)
                        showSkipIntro = false
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SkipNext, "Intro überspringen", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Intro überspringen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

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
            if (playerVisible && state.hasMultipleSources) {
                var showQualityMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassMedium)
                            .clickable { showQualityMenu = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            currentSource?.qualityHint ?: "Qualität",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false },
                        modifier = Modifier.background(Color(0xFF1A1A1A))
                    ) {
                        state.sources.forEachIndexed { i, source ->
                            val label = source.qualityHint ?: source.displayName
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        color = if (i == state.selectedSourceIndex) Primary else Color.White,
                                        fontWeight = if (i == state.selectedSourceIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    vm.selectSource(i)
                                    showQualityMenu = false
                                }
                            )
                        }
                    }
                }
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
            if (playerVisible && exoPlayer != null) {
                var showSpeedMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassMedium)
                            .clickable { showSpeedMenu = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${state.playbackSpeed}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                        modifier = Modifier.background(Color(0xFF1A1A1A))
                    ) {
                        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${speed}x",
                                        color = if (kotlin.math.abs(state.playbackSpeed - speed) < 0.01f) Primary else Color.White,
                                        fontWeight = if (kotlin.math.abs(state.playbackSpeed - speed) < 0.01f) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    vm.setPlaybackSpeed(speed)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

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
                    itemsIndexed(state.hosters, key = { idx, h -> "hoster-$idx-${h.name}" }) { i, h ->
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

        AnimatedVisibility(
            visible = !state.isMovie && showNextEpisodeOverlay && state.nextEpisode != null && state.autoplayNext,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val next = state.nextEpisode
            if (next != null) {
                var countdown by remember { mutableIntStateOf(5) }
                LaunchedEffect(showNextEpisodeOverlay, next) {
                    if (showNextEpisodeOverlay) {
                        countdown = 5
                        try {
                            while (countdown > 0) {
                                kotlinx.coroutines.delay(1000)
                                kotlinx.coroutines.yield()
                                if (!showNextEpisodeOverlay) return@LaunchedEffect
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
                                    overflow = TextOverflow.Ellipsis
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

@Composable
private fun PlayerErrorOverlay(
    message: String,
    canTryAlternateHoster: Boolean,
    onRetry: () -> Unit,
    onTryAlternateHoster: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "⚠",
                color = Primary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Etwas ist schiefgelaufen",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            if (canTryAlternateHoster) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(PrimaryGradient)
                        .clickable(onClick = onTryAlternateHoster)
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Anderen Hoster versuchen",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassMedium)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 32.dp, vertical = 12.dp)
            ) {
                Text(
                    "Erneut versuchen",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
