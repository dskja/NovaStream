package com.novastream.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.novastream.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.data.provider.ProviderManager
import com.novastream.app.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import com.novastream.app.ui.components.PremiumEmpty
import com.novastream.app.ui.components.PremiumError
import com.novastream.app.ui.components.SeriesPosterCard
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.TvUtils
import com.novastream.app.ui.tv.rememberInitialFocusRequester
import com.novastream.app.ui.tv.tvFocusRing
import com.novastream.app.ui.tv.tvFocusable
import com.novastream.app.util.ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WatchlistProviderFilter(@StringRes val labelRes: Int) {
    CURRENT(R.string.watchlist_filter_current),
    ALL(R.string.provider_filter_all)
}

data class WatchlistUiState(
    val items: List<WatchlistItem> = emptyList(),
    val allItems: List<WatchlistItem> = emptyList(),
    val loading: Boolean = true,
    val sortOption: SortOption = SortOption.ADDED_DESC,
    val providerFilter: WatchlistProviderFilter = WatchlistProviderFilter.CURRENT,
    val watchingSlugs: Set<String> = emptySet(),
    val error: String? = null
)

enum class SortOption(@StringRes val labelRes: Int) {
    ADDED_DESC(R.string.watchlist_sort_added_desc),
    ADDED_ASC(R.string.watchlist_sort_added_asc),
    TITLE_ASC(R.string.watchlist_sort_title_asc),
    TITLE_DESC(R.string.watchlist_sort_title_desc)
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchRepo: WatchRepository,
    private val providerController: ProviderController
) : ViewModel() {

    private val _state = MutableStateFlow(WatchlistUiState())
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            providerController.activeProviderId.collect { providerId ->
                val filtered = filterByProvider(_state.value.allItems, _state.value.providerFilter, providerId)
                val sorted = sortItems(filtered, _state.value.sortOption)
                _state.update { it.copy(items = sorted) }
            }
        }
        viewModelScope.launch {
            try {
                watchRepo.watchlist().collect { items ->
                    val pid = providerController.activeProviderId.value
                    val filtered = filterByProvider(items, _state.value.providerFilter, pid)
                    val sorted = sortItems(filtered, _state.value.sortOption)
                    _state.update {
                        it.copy(
                            allItems = items,
                            items = sorted,
                            loading = false
                        )
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchlistVM", "flow error", e)
                _state.update {
                    it.copy(loading = false, error = ErrorMapper.toUserMessage(e))
                }
            }
        }
        viewModelScope.launch {
            try {
                watchRepo.watchProgress().collect { progressList ->
                    val pid = providerController.activeProviderId.value
                    val slugs = progressList
                        .filter {
                            !it.isCompleted &&
                                (it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown")
                        }
                        .map { it.slug }
                        .toSet()
                    _state.update { it.copy(watchingSlugs = slugs) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("WatchlistVM", "progress flow error", e)
            }
        }
    }

    fun remove(slug: String) {
        viewModelScope.launch { watchRepo.removeFromWatchlist(slug) }
    }

    fun clearAll() {
        val slugs = _state.value.items.map { it.slug }
        if (slugs.isEmpty()) return
        viewModelScope.launch {
            watchRepo.removeAllFromWatchlist(slugs)
        }
    }

    fun removeBatch(slugs: List<String>) {
        if (slugs.isEmpty()) return
        viewModelScope.launch {
            watchRepo.removeAllFromWatchlist(slugs)
        }
    }

    fun setSortOption(option: SortOption) {
        _state.update { it.copy(sortOption = option) }
        val sorted = sortItems(_state.value.items, option)
        _state.update { it.copy(items = sorted) }
    }

    fun setProviderFilter(filter: WatchlistProviderFilter) {
        val pid = providerController.activeProviderId.value
        val filtered = filterByProvider(_state.value.allItems, filter, pid)
        val sorted = sortItems(filtered, _state.value.sortOption)
        _state.update { it.copy(providerFilter = filter, items = sorted) }
    }

    fun switchToProvider(providerId: String) {
        viewModelScope.launch {
            if (ProviderManager.getProviderOrNull(providerId) == null) return@launch
            providerController.setActiveProvider(providerId)
            setProviderFilter(WatchlistProviderFilter.CURRENT)
        }
    }

    private fun filterByProvider(
        items: List<WatchlistItem>,
        filter: WatchlistProviderFilter,
        activeProviderId: String
    ): List<WatchlistItem> = when (filter) {
        WatchlistProviderFilter.ALL -> items
        WatchlistProviderFilter.CURRENT -> items.filter {
            it.providerId.isBlank() || it.providerId == activeProviderId || it.providerId == "unknown"
        }
    }

    private fun sortItems(items: List<WatchlistItem>, option: SortOption): List<WatchlistItem> {
        return when (option) {
            SortOption.ADDED_DESC -> items.sortedByDescending { it.addedAt }
            SortOption.ADDED_ASC -> items.sortedBy { it.addedAt }
            SortOption.TITLE_ASC -> items.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> items.sortedByDescending { it.title.lowercase() }
        }
    }
}

private fun providerDisplayName(context: Context, providerId: String): String {
    if (providerId.isBlank() || providerId == "unknown") return context.getString(R.string.watchlist_provider_unknown)
    return ProviderManager.getProviderOrNull(providerId)?.displayName ?: providerId
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun WatchlistScreen(
    onSeriesClick: (String) -> Unit
) {
    val vm: WatchlistViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTvDevice(context) }
    val minPoster = if (isTv) 160.dp else 130.dp
    val initialFocus = rememberInitialFocusRequester()
    var pendingRemove by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<WatchlistItem?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showUnknownProviderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading, state.items) {
        if (!state.loading && state.items.isNotEmpty()) {
            try {
                initialFocus.requestFocus()
            } catch (_: Exception) {}
        }
    }

    pendingRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.watchlist_remove_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.watchlist_remove_message_fmt, item.title), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(item.slug)
                    pendingRemove = null
                }) { Text(stringResource(R.string.watchlist_remove_confirm), color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.cancel), color = TextTertiary)
                }
            },
            containerColor = BgSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    if (showUnknownProviderDialog) {
        AlertDialog(
            onDismissRequest = { showUnknownProviderDialog = false },
            title = { Text(stringResource(R.string.watchlist_unknown_provider_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.watchlist_unknown_provider_message),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showUnknownProviderDialog = false }) {
                    Text(stringResource(R.string.watchlist_ok), color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = BgSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPure)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(4.dp, 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PrimaryGradient)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.home_my_list),
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (state.items.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${state.items.size}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WatchlistProviderFilter.entries.forEach { filter ->
                val selected = state.providerFilter == filter
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Primary.copy(alpha = 0.15f) else BgSurfaceElevated)
                        .then(
                            if (isTv) Modifier.tvFocusable().tvFocusRing(cornerRadius = 20.dp)
                            else Modifier
                        )
                        .clickable { vm.setProviderFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(filter.labelRes),
                        color = if (selected) Primary else TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.items.isNotEmpty()) {
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurfaceElevated)
                            .then(
                                if (isTv) Modifier.tvFocusable().tvFocusRing(cornerRadius = 12.dp)
                                else Modifier
                            )
                            .clickable { showSortMenu = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(state.sortOption.labelRes),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(BgSurface)
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes), color = if (state.sortOption == option) Primary else TextPrimary) },
                                onClick = {
                                    vm.setSortOption(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.error != null -> PremiumError(
                    state.error ?: stringResource(R.string.watchlist_error_loading),
                    modifier = Modifier.fillMaxSize()
                )
                state.items.isEmpty() && state.loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minPoster),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(6) {
                            com.novastream.app.ui.components.ShimmerPoster(Modifier.width(minPoster))
                        }
                    }
                }
                state.items.isEmpty() -> {
                    PremiumEmpty(
                        stringResource(R.string.watchlist_empty),
                        icon = Icons.Filled.Bookmark
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        modifier = Modifier.focusRestorer(),
                        columns = GridCells.Adaptive(minSize = minPoster),
                        contentPadding = PaddingValues(12.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.items, key = { it.itemKey }) { item ->
                            val isFirst = item.itemKey == state.items.firstOrNull()?.itemKey
                            Box {
                                SeriesPosterCard(
                                    series = item.toSeries(),
                                    onClick = { onSeriesClick(item.slug) },
                                    inWatchlist = true,
                                    showWatchlistBadge = false,
                                    cardWidth = if (isTv) 160 else 130,
                                    focusRequester = if (isFirst) initialFocus else null
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BgSurfaceElevated.copy(alpha = 0.95f))
                                        .then(
                                            if (isTv) Modifier.tvFocusable().tvFocusRing(cornerRadius = 6.dp)
                                            else Modifier
                                        )
                                        .clickable {
                                            val pid = item.providerId.ifBlank { "unknown" }
                                            if (pid == "unknown" || ProviderManager.getProviderOrNull(pid) == null) {
                                                showUnknownProviderDialog = true
                                            } else {
                                                vm.switchToProvider(pid)
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        providerDisplayName(context, item.providerId),
                                        color = Primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xCC000000))
                                        .clickable { pendingRemove = item },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.BookmarkRemove,
                                        contentDescription = stringResource(R.string.cd_remove),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (item.slug in state.watchingSlugs) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Primary)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.watchlist_watching_badge),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
