package com.novastream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.novastream.app.data.db.WatchProgress
import androidx.compose.ui.res.stringResource
import com.novastream.app.R
import com.novastream.app.ui.theme.*

/**
 * Karte für "Continue Watching" – zeigt Cover, Titel, Episode und Fortschrittsbalken.
 * Zeigt das tatsächliche Cover-Bild der Serie (nicht grauer Placeholder).
 */
@Composable
fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isError by remember(progress.episodeKey, progress.coverUrl) { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.cw_remove_title)) },
            text = { Text(stringResource(R.string.cw_remove_message_fmt, progress.seriesTitle, progress.episodeTitle)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showConfirmDialog = false
                }) { Text(stringResource(R.string.watchlist_remove_confirm), color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .width(200.dp)
            .clickable(onClick = onClick)
            .focusable()
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.78f)
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard),
            contentAlignment = Alignment.Center
        ) {
            if (!progress.coverUrl.isNullOrBlank() && !isError) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(progress.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = progress.seriesTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { isError = it is AsyncImagePainter.State.Error }
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(BgCard),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = progress.seriesTitle.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "—",
                        color = Accent,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Dark gradient overlay at bottom
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color(0xE6000000)
                    )
                )
            )

            // Progress bar at bottom (BEFORE buttons so buttons are on top)
            LinearProgressIndicator(
                progress = { (progress.progressPercent / 100f).coerceIn(0f, 1f) },
                color = if (progress.isNearEnd) Accent else Primary,
                trackColor = Color(0x44FFFFFF),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            )

            // Play button overlay
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GlassMedium)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Remove button (top-right) - AFTER gradient so it's visible on top
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable { showConfirmDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_remove),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = progress.seriesTitle,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (progress.isMovie) {
                progress.episodeDisplay
            } else {
                "${progress.episodeDisplay} · ${progress.episodeTitle}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (progress.isNearEnd) Accent else TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
