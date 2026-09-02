@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.novastream.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.novastream.app.R
import com.novastream.app.ui.theme.GlassMedium
import com.novastream.app.ui.theme.Primary
import kotlinx.coroutines.isActive

@Composable
internal fun PlayerControlsOverlay(
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    visible: Boolean,
    isLive: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var subtitleTracks by remember { mutableStateOf(emptyList<SubtitleTrackOption>()) }
    var showSubtitleMenu by remember { mutableStateOf(false) }

    LaunchedEffect(player, visible) {
        while (isActive) {
            if (!isSeeking) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.coerceAtLeast(0L)
                isPlaying = player.isPlaying
                subtitleTracks = collectSubtitleTracks(player)
            }
            kotlinx.coroutines.delay(400)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color(0xAA000000),
                        1f to Color(0xEE000000)
                    )
                )
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 24.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                )
        ) {
            if (!isLive && durationMs > 0L) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatPlaybackTime(positionMs),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = if (isSeeking) seekPosition else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = {
                            isSeeking = true
                            seekPosition = it
                        },
                        onValueChangeFinished = {
                            val target = (seekPosition * durationMs).toLong()
                            player.seekTo(target)
                            positionMs = target
                            isSeeking = false
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    Text(
                        formatPlaybackTime(durationMs),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subtitleTracks.isNotEmpty()) {
                    Box {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GlassMedium)
                                .clickable { showSubtitleMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ClosedCaption,
                                stringResource(R.string.player_subtitles),
                                tint = if (subtitleTracks.any { it.isSelected }) Primary else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showSubtitleMenu,
                            onDismissRequest = { showSubtitleMenu = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.player_subtitles_off),
                                        color = if (subtitleTracks.none { it.isSelected }) Primary else Color.White
                                    )
                                },
                                onClick = {
                                    disableSubtitles(trackSelector)
                                    showSubtitleMenu = false
                                }
                            )
                            subtitleTracks.forEach { track ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            track.label,
                                            color = if (track.isSelected) Primary else Color.White,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectSubtitleTrack(trackSelector, player, track.groupIndex, track.trackIndex)
                                        showSubtitleMenu = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.width(40.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLive && hasPreviousEpisode) {
                        ControlButton(
                            icon = { Icon(Icons.Default.SkipPrevious, stringResource(R.string.player_previous_episode), tint = Color.White) },
                            onClick = onPreviousEpisode
                        )
                    }
                    ControlButton(
                        icon = {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        size = 52.dp,
                        onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    )
                    if (!isLive && hasNextEpisode) {
                        ControlButton(
                            icon = { Icon(Icons.Default.SkipNext, stringResource(R.string.player_next_episode), tint = Color.White) },
                            onClick = onNextEpisode
                        )
                    }
                }

                Spacer(Modifier.width(40.dp))
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassMedium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
internal fun PlayerTapToToggleControls(
    controlsVisible: Boolean,
    onToggle: () -> Unit,
    onSeekBackward: (() -> Unit)? = null,
    onSeekForward: (() -> Unit)? = null,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.pointerInput(isLive, onSeekBackward, onSeekForward) {
            detectTapGestures(
                onTap = { onToggle() },
                onDoubleTap = { offset ->
                    if (isLive) return@detectTapGestures
                    val leftEdge = size.width * 0.4f
                    val rightEdge = size.width * 0.6f
                    when {
                        offset.x < leftEdge -> onSeekBackward?.invoke()
                        offset.x > rightEdge -> onSeekForward?.invoke()
                        else -> onToggle()
                    }
                }
            )
        }
    )
}

@Composable
internal fun PlayerSeekHint(
    hint: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = hint != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color(0x99000000))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                hint.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}
