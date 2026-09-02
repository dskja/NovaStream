package com.novastream.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.novastream.app.R
import com.novastream.app.data.model.Series
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.TvUtils
import com.novastream.app.ui.tv.tvFocusRing
import com.novastream.app.ui.tv.tvFocusable

// ─── Provider Health Banner ─────────────────────────────────────────────────

@Composable
fun ProviderHealthBanner(
    providerName: String,
    loadDurationMs: Long?,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showSlow = loadDurationMs != null && loadDurationMs > 5_000L
    if (error == null && !showSlow) return

    val message = when {
        error != null -> error
        loadDurationMs != null -> stringResource(
            R.string.provider_health_slow,
            providerName,
            (loadDurationMs / 1000.0).toInt().coerceAtLeast(1)
        )
        else -> stringResource(R.string.provider_health_generic, providerName)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Primary.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "⚠",
            color = Primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            modifier = Modifier.weight(1f),
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (error != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRetry) {
                Text(
                    stringResource(R.string.retry),
                    color = Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── Shimmer Loading ────────────────────────────────────────────────────────

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8,
    animate: Boolean = true
) {
    if (!animate) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(ShimmerBase)
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
                    start = androidx.compose.ui.geometry.Offset(
                        translateAnim * 200f, 0f
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        translateAnim * 200f + 400f, 200f
                    )
                )
            )
    )
}

@Composable
fun ShimmerPoster(modifier: Modifier = Modifier, animate: Boolean = true) {
    Column(modifier) {
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(170.dp),
            cornerRadius = 12,
            animate = animate
        )
        Spacer(Modifier.height(8.dp))
        ShimmerBox(
            modifier = Modifier.fillMaxWidth(0.8f).height(14.dp),
            cornerRadius = 4,
            animate = animate
        )
    }
}

@Composable
fun ShimmerRow(animate: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) {
            ShimmerPoster(Modifier.width(120.dp), animate = animate)
        }
    }
}

// ─── Premium Series Poster Card ─────────────────────────────────────────────

@Composable
fun SeriesPosterCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Int = 130,
    inWatchlist: Boolean = false,
    showWatchlistBadge: Boolean = true,
    focusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTvDevice(context) }
    val imageSize = rememberPosterImageDimensions()
    val effectiveWidth = if (isTv) (cardWidth * 1.35f).toInt().coerceAtLeast(cardWidth + 20) else cardWidth
    var isLoading by remember(series.id, series.coverUrl) { mutableStateOf(true) }
    var isError by remember(series.id, series.coverUrl) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(effectiveWidth.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = series.title
            }
            .then(
                if (isTv) {
                    Modifier
                        .tvFocusable(focusRequester = focusRequester)
                        .tvFocusRing(cornerRadius = 12.dp)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && !isError) {
                ShimmerBox(
                    Modifier.fillMaxSize(),
                    cornerRadius = 12
                )
            }

            if (isError || series.coverUrl.isNullOrBlank()) {
                Box(
                    Modifier.fillMaxSize().background(BgCard),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = series.initials,
                        color = Accent,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            } else if (!series.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(series.coverUrl)
                        .crossfade(true)
                        .size(imageSize.width, imageSize.height)
                        .addHeader(
                            "Referer",
                            com.novastream.app.util.MediaUrls.refererFor(series.coverUrl)
                        )
                        .addHeader("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
                        .build(),
                    contentDescription = series.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { state ->
                        isLoading = state is AsyncImagePainter.State.Loading
                        isError = state is AsyncImagePainter.State.Error
                        if (isError && com.novastream.app.BuildConfig.DEBUG) {
                            android.util.Log.w("SeriesPosterCard", "Image load failed: ${series.coverUrl}")
                        }
                    }
                )
            }

            // Movie badge (top-left)
            if (series.isMovie) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Accent.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        stringResource(R.string.movie_badge),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val providerCount = series.availableProviderCount
            if (providerCount != null && providerCount > 1) {
                Box(
                    Modifier
                        .align(if (series.isMovie) Alignment.BottomStart else Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Primary.copy(alpha = 0.92f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        stringResource(R.string.search_providers_count_fmt, providerCount),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Watchlist indicator badge (top-right corner)
            if (inWatchlist && showWatchlistBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.in_watchlist),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = series.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Premium Loading / Error / Empty States ─────────────────────────────────

@Composable
fun PremiumLoading(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.loading)
) {
    Box(
        modifier.fillMaxSize().wrapContentSize(Alignment.Center),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = Primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                label,
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun PremiumError(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.fillMaxSize().wrapContentSize(Alignment.Center),
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
                stringResource(R.string.error_title),
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
            if (onRetry != null) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(PrimaryGradient)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.retry),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumEmpty(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier.fillMaxSize().wrapContentSize(Alignment.Center),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text,
                color = TextTertiary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ─── Section Header ─────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(4.dp, 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PrimaryGradient)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Text(
                text = stringResource(R.string.section_see_all),
                color = Primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        } else {
            trailing?.invoke()
        }
    }
}
