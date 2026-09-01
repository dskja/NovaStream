package com.novastream.app.ui.continuewatching

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.ui.components.ContinueWatchingCard
import com.novastream.app.ui.components.PremiumEmpty
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.PremiumLoading
import androidx.compose.ui.res.stringResource
import com.novastream.app.R
import com.novastream.app.ui.theme.*

@Composable
fun ContinueWatchingScreen(
    onBack: () -> Unit,
    onPlay: (slug: String, season: Int, episode: Int, title: String, seriesTitle: String, coverUrl: String?, isMovie: Boolean) -> Unit
) {
    val vm: ContinueWatchingViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPure)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BgSurfaceElevated)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.continue_watching_back),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.settings_continue_watching),
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading && state.items.isEmpty() -> PremiumLoading(label = stringResource(R.string.loading))
                state.error != null -> PremiumError(state.error ?: stringResource(R.string.error_title))
                state.items.isEmpty() -> PremiumEmpty(stringResource(R.string.continue_watching_nothing))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.episodeKey }) { progress ->
                        ContinueWatchingCard(
                            progress = progress,
                            onClick = {
                                onPlay(
                                    progress.slug,
                                    progress.season,
                                    progress.episode,
                                    progress.episodeTitle,
                                    progress.seriesTitle,
                                    progress.coverUrl,
                                    progress.isMovie
                                )
                            },
                            onRemove = { vm.remove(progress.episodeKey) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
