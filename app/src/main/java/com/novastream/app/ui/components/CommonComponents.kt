package com.novastream.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.novastream.app.data.model.Series
import com.novastream.app.ui.theme.*

// ─── Shimmer Loading ────────────────────────────────────────────────────────

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8
) {
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
fun ShimmerPoster(modifier: Modifier = Modifier) {
    Column(modifier) {
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(170.dp),
            cornerRadius = 12
        )
        Spacer(Modifier.height(8.dp))
        ShimmerBox(
            modifier = Modifier.fillMaxWidth(0.8f).height(14.dp),
            cornerRadius = 4
        )
    }
}

@Composable
fun ShimmerRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) {
            ShimmerPoster(Modifier.width(120.dp))
        }
    }
}

// ─── Premium Series Poster Card ─────────────────────────────────────────────

@Composable
fun SeriesPosterCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Int = 130
) {
    val context = LocalContext.current
    var isLoading by remember(series.id, series.coverUrl) { mutableStateOf(true) }
    var isError by remember(series.id, series.coverUrl) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(cardWidth.dp)
            .clickable(onClick = onClick)
            .focusable()
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

            if (!series.coverUrl.isNullOrBlank() && !isError) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(series.coverUrl)
                        .crossfade(false)  // kein Crossfade → weniger Jank beim Scrollen
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
                // Gradient overlay am unteren Rand
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.6f to Color.Transparent,
                                1f to CardGradientBottom
                            )
                        )
                )
            }

            if (isError || series.coverUrl.isNullOrBlank()) {
                Box(
                    Modifier.fillMaxSize().background(BgCard),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = series.title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "??",
                        color = Accent,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
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
    label: String = "Lädt…"
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
                        "Erneut versuchen",
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
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}
