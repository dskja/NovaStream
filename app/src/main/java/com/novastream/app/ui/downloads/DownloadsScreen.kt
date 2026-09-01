package com.novastream.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.novastream.app.R
import com.novastream.app.data.db.DownloadEntity
import com.novastream.app.data.db.DownloadStatus
import com.novastream.app.download.DownloadManagerHelper
import com.novastream.app.profile.ProfileManager
import com.novastream.app.ui.components.PremiumEmpty
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val loading: Boolean = true,
    val items: List<DownloadEntity> = emptyList(),
    val storageBytes: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadHelper: DownloadManagerHelper,
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profileManager.ensureDefaultProfile()
            profileManager.activeProfileId()
                .flatMapLatest { profileId -> downloadHelper.observeDownloads(profileId) }
                .collect { items ->
                    val bytes = downloadHelper.getStorageUsedBytes()
                    _state.update { it.copy(loading = false, items = items, storageBytes = bytes) }
                }
        }
    }

    fun removeDownload(id: String) {
        viewModelScope.launch { downloadHelper.removeDownload(id) }
    }

    fun retryDownload(item: DownloadEntity) {
        viewModelScope.launch { downloadHelper.retryDownload(item) }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(storageBytes = downloadHelper.getStorageUsedBytes()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (DownloadEntity) -> Unit = {}
) {
    val vm: DownloadsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(BgPure)) {
        TopAppBar(
            title = { Text(stringResource(R.string.downloads_title), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPure)
        )

        if (state.storageBytes > 0) {
            Text(
                stringResource(R.string.downloads_storage_fmt, formatBytes(state.storageBytes)),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium
            )
        }

        when {
            state.loading -> PremiumLoading(label = stringResource(R.string.downloads_loading))
            state.items.isEmpty() -> PremiumEmpty(
                text = stringResource(R.string.downloads_empty),
                icon = Icons.Default.Download
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.downloadId }) { item ->
                    DownloadRow(
                        item = item,
                        onPlay = { onPlay(item) },
                        onRetry = { vm.retryDownload(item) },
                        onRemove = { vm.removeDownload(item.downloadId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.coverUrl,
            contentDescription = item.title,
            modifier = Modifier
                .size(56.dp, 80.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.downloads_episode_fmt, item.episodeTitle, item.season, item.episode),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val statusText = when (item.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    val pct = item.progressPercent.coerceIn(0, 100)
                    if (pct > 0) {
                        "${statusLabel(item.status)} · ${stringResource(R.string.downloads_progress_fmt, pct)}"
                    } else {
                        statusLabel(item.status)
                    }
                }
                else -> statusLabel(item.status)
            }
            Text(
                statusText,
                color = when (item.status) {
                    DownloadStatus.COMPLETED -> Primary
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> TextTertiary
                },
                style = MaterialTheme.typography.labelSmall
            )
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
                val progress = item.progressPercent.coerceIn(0, 100) / 100f
                if (progress > 0f) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Primary,
                        trackColor = BgSurfaceElevated
                    )
                }
            }
        }
        if (item.status == DownloadStatus.COMPLETED) {
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.downloads_play), tint = Primary)
            }
        } else if (item.status == DownloadStatus.FAILED) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.downloads_retry), tint = Primary)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.downloads_remove), tint = TextTertiary)
        }
    }
}

@Composable
private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> stringResource(R.string.downloads_status_queued)
    DownloadStatus.DOWNLOADING -> stringResource(R.string.downloads_status_downloading)
    DownloadStatus.PAUSED -> stringResource(R.string.downloads_status_paused)
    DownloadStatus.COMPLETED -> stringResource(R.string.downloads_status_completed)
    DownloadStatus.FAILED -> stringResource(R.string.downloads_status_failed)
    DownloadStatus.REMOVED -> stringResource(R.string.downloads_status_removed)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
