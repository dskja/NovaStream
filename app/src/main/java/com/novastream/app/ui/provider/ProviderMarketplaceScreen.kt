package com.novastream.app.ui.provider

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novastream.app.R
import com.novastream.app.data.provider.*
import com.novastream.app.ui.theme.*
import com.novastream.app.util.ProviderHealthMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MarketplaceUiState(
    val providers: List<ProviderInfo> = emptyList(),
    val grouped: Map<ContentLanguage, List<ProviderInfo>> = emptyMap(),
    val activeProviderId: String = "",
    val favoriteIds: Set<String> = emptySet(),
    val selectedLanguage: ContentLanguage? = null,
    val favoritesOnly: Boolean = false
)

@HiltViewModel
class ProviderMarketplaceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerController: ProviderController
) : ViewModel() {

    private val _state = MutableStateFlow(MarketplaceUiState())
    val state: StateFlow<MarketplaceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                providerController.activeProviderId,
                ProviderFavorites.favoriteIdsFlow(context)
            ) { activeId, favorites -> activeId to favorites }
                .collect { (activeId, favorites) -> refresh(activeId, favorites) }
        }
    }

    fun setLanguageFilter(language: ContentLanguage?) {
        _state.update { it.copy(selectedLanguage = language, favoritesOnly = false) }
        refreshLists()
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _state.update { it.copy(favoritesOnly = enabled, selectedLanguage = if (enabled) null else it.selectedLanguage) }
        refreshLists()
    }

    fun toggleFavorite(providerId: String) {
        viewModelScope.launch { ProviderFavorites.toggleFavorite(context, providerId) }
    }

    fun activateProvider(providerId: String) {
        viewModelScope.launch { providerController.setActiveProvider(providerId) }
    }

    private fun refreshLists() {
        val s = _state.value
        refresh(s.activeProviderId, s.favoriteIds)
    }

    private fun refresh(activeId: String, favorites: Set<String>) {
        val s = _state.value
        val filtered = ProviderManager.getFilteredProviderInfos(
            language = s.selectedLanguage,
            favoriteIds = favorites,
            favoritesOnly = s.favoritesOnly
        )
        val grouped = if (s.selectedLanguage == null && !s.favoritesOnly) {
            ProviderManager.getProviderInfosGroupedByLanguage()
        } else {
            emptyMap()
        }
        _state.update {
            it.copy(
                activeProviderId = activeId,
                favoriteIds = favorites,
                providers = filtered,
                grouped = grouped
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderMarketplaceScreen(
    onBack: () -> Unit,
    vm: ProviderMarketplaceViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.marketplace_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPure)
            )
        },
        containerColor = BgPure
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ProviderLanguageFilterChips(
                selectedLanguage = state.selectedLanguage,
                favoritesOnly = state.favoritesOnly,
                onLanguageSelected = vm::setLanguageFilter,
                onFavoritesToggle = vm::setFavoritesOnly
            )

            if (state.grouped.isNotEmpty() && !state.favoritesOnly && state.selectedLanguage == null) {
                state.grouped.forEach { (lang, list) ->
                    ProviderLanguageSectionHeader(lang, list.size)
                    list.forEach { info -> MarketplaceProviderCard(info, state, vm, context) }
                }
            } else {
                state.providers.forEach { info ->
                    MarketplaceProviderCard(info, state, vm, context)
                }
            }
        }
    }
}

@Composable
private fun MarketplaceProviderCard(
    info: ProviderInfo,
    state: MarketplaceUiState,
    vm: ProviderMarketplaceViewModel,
    context: Context
) {
    val isActive = info.id == state.activeProviderId
    val isFavorite = info.id in state.favoriteIds
    val healthWarning = ProviderHealthMonitor.isInCooldown(info.id)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) Primary.copy(alpha = 0.1f) else BgSurface)
            .clickable { vm.activateProvider(info.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Stream, contentDescription = null, tint = Primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(info.displayName, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "${info.contentLabel(context)} · ${info.languageTag.uppercase()} · ${info.hostLabel}",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.supportsMovies) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.settings_capability_movies)) }, leadingIcon = {
                        Icon(Icons.Default.Movie, null, Modifier.size(16.dp))
                    })
                }
                if (info.supportsSeries) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.provider_content_series)) }, leadingIcon = {
                        Icon(Icons.Default.Tv, null, Modifier.size(16.dp))
                    })
                }
            }
            if (healthWarning) {
                Text(
                    stringResource(R.string.provider_health_generic, info.displayName),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        IconButton(onClick = { vm.toggleFavorite(info.id) }) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) Primary else TextTertiary
            )
        }
        if (isActive) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Primary)
        }
    }
}
