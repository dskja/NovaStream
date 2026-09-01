package com.novastream.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.novastream.app.R
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.data.provider.ProviderFavorites
import com.novastream.app.data.provider.ProviderManager
import com.novastream.app.ui.provider.ProviderLanguageFilterChips
import com.novastream.app.ui.provider.ProviderLanguageSectionHeader
import com.novastream.app.util.LocaleManager
import com.novastream.app.util.findActivity
import com.novastream.app.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.novastream.app.util.ProviderLoadMetrics
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.rememberInitialFocusRequester
import com.novastream.app.ui.tv.tvFocusIfNeeded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val watchlistCount: Int = 0,
    val continueWatchingCount: Int = 0,
    val activeProviderId: String = "serienstream",
    val availableProviders: List<com.novastream.app.data.provider.ProviderInfo> = emptyList(),
    val autoplayNext: Boolean = true,
    val dynamicColor: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val skipIntroButton: Boolean = true,
    val preferredHoster: String = "VOE",
    val preferredLanguage: String = "Deutsch",
    val dataSaverMode: Boolean = false,
    val reduceMotion: Boolean = false,
    val performanceMode: Boolean = false,
    val providerLoadAveragesMs: Map<String, Long> = emptyMap(),
    val message: String? = null,
    val updateChecking: Boolean = false,
    val updateInfo: com.novastream.app.util.UpdateChecker.UpdateInfo? = null,
    val updateChecked: Boolean = false,
    val unknownProviderRowCount: Int = 0,
    val showUnknownProviderCleanup: Boolean = false,
    val uiLocale: String = LocaleManager.SYSTEM_LOCALE,
    val contentLanguageTag: String = ContentLanguage.DE.tag,
    val providerLanguageFilter: ContentLanguage? = null,
    val favoritesOnly: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val groupedProviders: Map<ContentLanguage, List<com.novastream.app.data.provider.ProviderInfo>> = emptyMap()
)

private val PREFERRED_HOSTERS = listOf("VOE", "Streamtape", "Doodstream", "Filemoon", "Vidoza", "Vidmoly", "Mixdrop")
private val PREFERRED_LANGUAGES = listOf("Deutsch", "Englisch", "Ger-Sub", "Eng-Sub")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchRepo: WatchRepository,
    private val appSettings: AppSettings,
    val providerController: ProviderController
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            watchRepo.watchlist().collect { list ->
                val pid = ActiveProvider.id
                val scoped = list.filter { it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown" }
                _state.update { it.copy(watchlistCount = scoped.size) }
            }
        }
        viewModelScope.launch {
            watchRepo.watchProgressForActiveProvider().collect { list ->
                _state.update { it.copy(continueWatchingCount = list.count { !it.isCompleted }) }
            }
        }
        _state.update {
            it.copy(
                availableProviders = ProviderManager.getProviderInfos(),
                activeProviderId = ActiveProvider.id,
                groupedProviders = ProviderManager.getProviderInfosGroupedByLanguage()
            )
        }
        viewModelScope.launch {
            providerController.activeProviderId.collect { providerId ->
                _state.update { it.copy(activeProviderId = providerId) }
            }
        }
        viewModelScope.launch {
            appSettings.autoplayNext.collect { v -> _state.update { it.copy(autoplayNext = v) } }
        }
        viewModelScope.launch {
            appSettings.dynamicColor.collect { v -> _state.update { it.copy(dynamicColor = v) } }
        }
        viewModelScope.launch {
            appSettings.playbackSpeed.collect { v -> _state.update { it.copy(playbackSpeed = v) } }
        }
        viewModelScope.launch {
            appSettings.skipIntroButton.collect { v -> _state.update { it.copy(skipIntroButton = v) } }
        }
        viewModelScope.launch {
            appSettings.preferredHoster.collect { v -> _state.update { it.copy(preferredHoster = v) } }
        }
        viewModelScope.launch {
            appSettings.preferredLanguage.collect { v -> _state.update { it.copy(preferredLanguage = v) } }
        }
        viewModelScope.launch {
            appSettings.dataSaverMode.collect { v -> _state.update { it.copy(dataSaverMode = v) } }
        }
        viewModelScope.launch {
            appSettings.reduceMotion.collect { v -> _state.update { it.copy(reduceMotion = v) } }
        }
        viewModelScope.launch {
            appSettings.performanceMode.collect { v -> _state.update { it.copy(performanceMode = v) } }
        }
        viewModelScope.launch {
            appSettings.uiLocale.collect { v -> _state.update { it.copy(uiLocale = v) } }
        }
        viewModelScope.launch {
            appSettings.contentLanguage.collect { v -> _state.update { it.copy(contentLanguageTag = v) } }
        }
        viewModelScope.launch {
            ProviderFavorites.favoriteIdsFlow(context).collect { ids ->
                _state.update { it.copy(favoriteIds = ids) }
                refreshProviderLists()
            }
        }
        _state.update { it.copy(providerLoadAveragesMs = ProviderLoadMetrics.snapshotAverages()) }
        viewModelScope.launch {
            val prompted = appSettings.unknownProviderCleanupPrompted.first()
            val unknownCount = watchRepo.countUnknownProviderRows()
            _state.update {
                it.copy(
                    unknownProviderRowCount = unknownCount,
                    showUnknownProviderCleanup = unknownCount > 0 && !prompted
                )
            }
        }
        // Auto-check for updates once
        checkForUpdates()
    }

    fun dismissUnknownProviderCleanup() {
        viewModelScope.launch {
            appSettings.setUnknownProviderCleanupPrompted(true)
            _state.update { it.copy(showUnknownProviderCleanup = false) }
        }
    }

    fun cleanupUnknownProviderRows() {
        viewModelScope.launch {
            val removed = watchRepo.cleanupUnknownProviderRows()
            appSettings.setUnknownProviderCleanupPrompted(true)
            _state.update {
                it.copy(
                    showUnknownProviderCleanup = false,
                    unknownProviderRowCount = 0,
                    message = if (removed > 0) {
                        context.getString(R.string.settings_unknown_cleanup_removed, removed)
                    } else {
                        context.getString(R.string.settings_unknown_cleanup_none)
                    }
                )
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _state.update { it.copy(updateChecking = true) }
            val info = try {
                com.novastream.app.util.UpdateChecker.check()
            } catch (_: Exception) {
                null
            }
            _state.update {
                it.copy(
                    updateChecking = false,
                    updateChecked = true,
                    updateInfo = info,
                    message = when {
                        info == null -> context.getString(R.string.settings_update_check_failed)
                        info.isNewer -> context.getString(R.string.settings_update_available, info.latestVersion)
                        else -> context.getString(R.string.settings_up_to_date, info.currentVersion)
                    }
                )
            }
        }
    }

    fun clearContinueWatching() {
        viewModelScope.launch {
            watchRepo.clearProgressForProvider()
            _state.update { it.copy(message = context.getString(R.string.settings_continue_cleared)) }
        }
    }

    fun clearWatchlist() {
        viewModelScope.launch {
            watchRepo.clearWatchlistForProvider()
            _state.update { it.copy(message = context.getString(R.string.settings_watchlist_cleared)) }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            watchRepo.removeCompleted()
            _state.update { it.copy(message = context.getString(R.string.settings_completed_cleared)) }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showUrlError() {
        _state.update { it.copy(message = context.getString(R.string.settings_url_error)) }
    }

    fun setProvider(providerId: String) {
        viewModelScope.launch {
            val provider = ProviderManager.getProviderOrNull(providerId)
            if (provider == null) {
                _state.update { it.copy(message = context.getString(R.string.settings_provider_not_found)) }
                return@launch
            }
            providerController.setActiveProvider(providerId)
            _state.update { it.copy(message = context.getString(R.string.settings_provider_switched, provider.displayName)) }
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch { appSettings.setAutoplayNext(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { appSettings.setDynamicColor(enabled) }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { appSettings.setPlaybackSpeed(speed) }
    }

    fun setSkipIntroButton(enabled: Boolean) {
        viewModelScope.launch { appSettings.setSkipIntroButton(enabled) }
    }

    fun setPreferredHoster(hoster: String) {
        viewModelScope.launch { appSettings.setPreferredHoster(hoster) }
    }

    fun setPreferredLanguage(language: String) {
        viewModelScope.launch { appSettings.setPreferredLanguage(language) }
    }

    fun setDataSaverMode(enabled: Boolean) {
        viewModelScope.launch { appSettings.setDataSaverMode(enabled) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { appSettings.setReduceMotion(enabled) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        viewModelScope.launch { appSettings.setPerformanceMode(enabled) }
    }

    fun setUiLocale(localeTag: String) {
        viewModelScope.launch { appSettings.setUiLocale(localeTag) }
    }

    fun setContentLanguage(tag: String) {
        viewModelScope.launch { appSettings.setContentLanguage(tag) }
    }

    fun setProviderLanguageFilter(language: ContentLanguage?) {
        _state.update { it.copy(providerLanguageFilter = language, favoritesOnly = false) }
        refreshProviderLists()
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _state.update { it.copy(favoritesOnly = enabled, providerLanguageFilter = if (enabled) null else it.providerLanguageFilter) }
        refreshProviderLists()
    }

    private fun refreshProviderLists() {
        val s = _state.value
        val filtered = ProviderManager.getFilteredProviderInfos(
            language = s.providerLanguageFilter,
            favoriteIds = s.favoriteIds,
            favoritesOnly = s.favoritesOnly
        )
        val grouped = if (s.providerLanguageFilter == null && !s.favoritesOnly) {
            ProviderManager.getProviderInfosGroupedByLanguage()
        } else emptyMap()
        _state.update { it.copy(availableProviders = filtered, groupedProviders = grouped) }
    }

    fun refreshProviderLoadMetrics() {
        _state.update { it.copy(providerLoadAveragesMs = ProviderLoadMetrics.snapshotAverages()) }
    }
}

@Composable
fun SettingsScreen(
    onOpenMarketplace: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenPlayback: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {}
) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingActionTitle by remember { mutableStateOf("") }
    var pendingProviderId by remember { mutableStateOf<String?>(null) }
    var pendingProviderName by remember { mutableStateOf("") }
    val initialFocus = rememberInitialFocusRequester()

    LaunchedEffect(Unit) {
        vm.refreshProviderLoadMetrics()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    LaunchedEffect(Unit) {
        try {
            initialFocus.requestFocus()
        } catch (_: Exception) {}
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(
                    bottom = 72.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            )
        },
        containerColor = BgPure
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(BgPure)
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 20.dp
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
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Stats Cards
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.settings_continue_watching),
                    value = state.continueWatchingCount,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.settings_watchlist),
                    value = state.watchlistCount,
                    modifier = Modifier.weight(1f)
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_categories))
            SettingsNavigationRow(
                icon = Icons.Default.PlayCircle,
                title = stringResource(R.string.settings_playback),
                subtitle = stringResource(R.string.settings_playback_sub),
                onClick = onOpenPlayback
            )
            SettingsNavigationRow(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_design),
                subtitle = stringResource(R.string.settings_design_sub),
                onClick = onOpenAppearance
            )
            SettingsNavigationRow(
                icon = Icons.Default.Download,
                title = stringResource(R.string.settings_open_downloads),
                subtitle = stringResource(R.string.settings_open_downloads_sub),
                onClick = onOpenDownloads
            )
            SettingsNavigationRow(
                icon = Icons.Default.Stream,
                title = stringResource(R.string.settings_advanced),
                subtitle = stringResource(R.string.settings_advanced_sub),
                onClick = onOpenAdvanced
            )

            Spacer(Modifier.height(8.dp))

            // Section: Streaming Provider
            SettingsSectionHeader(stringResource(R.string.settings_streaming_provider))
            SettingsNavigationRow(
                icon = Icons.Default.Stream,
                title = stringResource(R.string.settings_marketplace_title),
                subtitle = stringResource(R.string.settings_marketplace_subtitle),
                onClick = onOpenMarketplace
            )
            ProviderLanguageFilterChips(
                selectedLanguage = state.providerLanguageFilter,
                favoritesOnly = state.favoritesOnly,
                onLanguageSelected = vm::setProviderLanguageFilter,
                onFavoritesToggle = vm::setFavoritesOnly
            )
            if (state.groupedProviders.isNotEmpty() && !state.favoritesOnly && state.providerLanguageFilter == null) {
                state.groupedProviders.forEach { (lang, providers) ->
                    ProviderLanguageSectionHeader(lang, providers.size)
                    providers.forEach { providerInfo ->
                        ProviderSettingsRow(
                            providerInfo = providerInfo,
                            isSelected = providerInfo.id == state.activeProviderId,
                            context = context,
                            onSelect = {
                                if (providerInfo.id != state.activeProviderId) {
                                    pendingProviderId = providerInfo.id
                                    pendingProviderName = providerInfo.displayName
                                }
                            }
                        )
                    }
                }
            } else {
                state.availableProviders.forEach { providerInfo ->
                    ProviderSettingsRow(
                        providerInfo = providerInfo,
                        isSelected = providerInfo.id == state.activeProviderId,
                        context = context,
                        onSelect = {
                            if (providerInfo.id != state.activeProviderId) {
                                pendingProviderId = providerInfo.id
                                pendingProviderName = providerInfo.displayName
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsSectionHeader(stringResource(R.string.settings_provider_capabilities))
            ProviderCapabilityMatrix(
                providers = state.availableProviders,
                loadAveragesMs = state.providerLoadAveragesMs
            )

            Spacer(Modifier.height(8.dp))

            // Section: Updates
            SettingsSectionHeader(stringResource(R.string.settings_updates))
            val update = state.updateInfo
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (update?.isNewer == true) Primary.copy(alpha = 0.18f)
                        else BgSurfaceElevated
                    )
                    .clickable { vm.checkForUpdates() }
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = if (update?.isNewer == true) Primary else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    state.updateChecking -> stringResource(R.string.settings_update_checking)
                                    update?.isNewer == true -> stringResource(
                                        R.string.settings_update_available,
                                        update.latestVersion
                                    )
                                    update != null -> stringResource(
                                        R.string.settings_update_current,
                                        update.currentVersion
                                    )
                                    else -> stringResource(R.string.settings_update_search)
                                },
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                stringResource(
                                    R.string.settings_update_github_releases,
                                    com.novastream.app.util.UpdateChecker.GITHUB_REPO
                                ),
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (update?.isNewer == true) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val url = update.downloadUrl ?: update.releaseUrl
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {
                                        vm.showUrlError()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Text(
                                    if (update.downloadUrl != null) {
                                        stringResource(R.string.settings_update_download_apk)
                                    } else {
                                        stringResource(R.string.settings_update_open_release)
                                    }
                                )
                            }
                            OutlinedButton(onClick = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(update.releaseUrl)
                                        )
                                    )
                                } catch (_: Exception) {
                                    vm.showUrlError()
                                }
                            }) {
                                Text(stringResource(R.string.settings_update_changelog))
                            }
                        }
                        update.releaseNotes?.take(280)?.let { notes ->
                            Spacer(Modifier.height(8.dp))
                            Text(notes, color = TextSecondary, fontSize = 12.sp, maxLines = 6)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Section: Über NovaStream
            SettingsSectionHeader(stringResource(R.string.settings_about))

            // Premium App Info Card
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Primary.copy(alpha = 0.12f), BgSurface, BgSurface)
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    // Logo + Name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(PrimaryGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("NOVA", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, lineHeight = 16.sp)
                                Text("STREAM", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "NovaStream",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Black
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp, 6.dp)
                                        .clip(CircleShape)
                                        .background(Primary)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Version ${com.novastream.app.BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "·",
                                    color = TextTertiary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Build ${com.novastream.app.BuildConfig.VERSION_CODE}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Description
                    Text(
                        stringResource(R.string.settings_about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    // Tech badges
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TechBadge("Kotlin")
                        TechBadge("Compose")
                        TechBadge("Media3")
                        TechBadge("Room")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TechBadge("Coil")
                        TechBadge("Retrofit")
                        TechBadge("Material 3")
                    }
                }
            }

            // Clickable Links
            ClickableSettingsItem(
                icon = Icons.Default.Code,
                title = stringResource(R.string.settings_source_code),
                subtitle = "github.com/dskja/NovaStream",
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream") { vm.showUrlError() } }
            )
            ClickableSettingsItem(
                icon = Icons.Default.BugReport,
                title = stringResource(R.string.settings_report_bug),
                subtitle = stringResource(R.string.settings_report_bug_sub),
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream/issues/new") { vm.showUrlError() } }
            )
            ClickableSettingsItem(
                icon = Icons.Default.Star,
                title = stringResource(R.string.settings_star_repo),
                subtitle = stringResource(R.string.settings_star_repo_sub),
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream") { vm.showUrlError() } }
            )
            ClickableSettingsItem(
                icon = Icons.Default.Gavel,
                title = stringResource(R.string.settings_license),
                subtitle = stringResource(R.string.settings_license_sub),
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream/blob/main/LICENSE") { vm.showUrlError() } }
            )
            ClickableSettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_sub),
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream#privacy") { vm.showUrlError() } }
            )

            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader(stringResource(R.string.settings_developer))
            ClickableSettingsItem(
                icon = Icons.Default.Code,
                title = "dskja",
                subtitle = stringResource(R.string.settings_github_profile),
                onClick = { openUrl(context, "https://github.com/dskja") { vm.showUrlError() } }
            )

            Spacer(Modifier.height(24.dp))

            // Footer
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_made_with), color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.Favorite, null, tint = Primary, modifier = Modifier.size(12.dp))
                    Text(" by dskja", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_client_disclaimer),
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.settings_version_build_fmt,
                        com.novastream.app.BuildConfig.VERSION_NAME,
                        com.novastream.app.BuildConfig.VERSION_CODE
                    ),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "© 2024-2025 dskja · MIT License",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // One-time dialog: unknown providerId rows from v5→v6 migration
    if (state.showUnknownProviderCleanup) {
        AlertDialog(
            onDismissRequest = { vm.dismissUnknownProviderCleanup() },
            title = {
                Text(stringResource(R.string.settings_unknown_cleanup_title), color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    stringResource(R.string.settings_unknown_cleanup_message, state.unknownProviderRowCount),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.cleanupUnknownProviderRows() }) {
                    Text(stringResource(R.string.confirm), color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissUnknownProviderCleanup() }) {
                    Text(stringResource(R.string.settings_keep), color = TextTertiary)
                }
            },
            containerColor = BgSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Provider switch confirmation
    if (pendingProviderId != null) {
        AlertDialog(
            onDismissRequest = { pendingProviderId = null },
            title = {
                Text(stringResource(R.string.settings_provider_switch_title), color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    stringResource(R.string.settings_provider_switch_message, pendingProviderName),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingProviderId?.let { vm.setProvider(it) }
                        pendingProviderId = null
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingProviderId = null }) {
                    Text(stringResource(R.string.cancel), color = TextTertiary)
                }
            },
            containerColor = BgSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Confirmation Dialog for destructive actions
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(pendingActionTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_confirm_irreversible), color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction?.invoke()
                        pendingAction = null
                    }
                ) { Text(stringResource(R.string.confirm), color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel), color = TextTertiary)
                }
            },
            containerColor = BgSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

private fun openUrl(context: android.content.Context, url: String, onError: () -> Unit = {}) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        if (com.novastream.app.BuildConfig.DEBUG) {
            android.util.Log.e("Settings", "Failed to open URL: $url", e)
        }
        onError()
    }
}

@Composable
private fun TechBadge(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgSurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProviderCapabilityMatrix(
    providers: List<com.novastream.app.data.provider.ProviderInfo>,
    loadAveragesMs: Map<String, Long> = emptyMap()
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurface)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                stringResource(R.string.settings_streaming_provider),
                Modifier.weight(1.4f),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.settings_capability_movies),
                Modifier.weight(0.6f),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.settings_capability_pagination),
                Modifier.weight(0.8f),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.settings_capability_latest),
                Modifier.weight(0.8f),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.settings_capability_load_time),
                Modifier.weight(0.7f),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        providers.forEach { provider ->
            val avgMs = loadAveragesMs[provider.id]
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    provider.displayName,
                    Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    if (provider.supportsMovies) stringResource(R.string.yes) else stringResource(R.string.no),
                    Modifier.weight(0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (provider.supportsMovies) Primary else TextTertiary,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (provider.capabilities.supportsPagination) stringResource(R.string.yes) else stringResource(R.string.no),
                    Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (provider.capabilities.supportsPagination) Primary else TextTertiary,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (provider.capabilities.supportsLatestEpisodes) stringResource(R.string.yes) else stringResource(R.string.no),
                    Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (provider.capabilities.supportsLatestEpisodes) Primary else TextTertiary,
                    textAlign = TextAlign.Center
                )
                Text(
                    when {
                        avgMs == null -> "—"
                        avgMs < 1000 -> "${avgMs}ms"
                        else -> "${avgMs / 1000}s"
                    },
                    Modifier.weight(0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        avgMs == null -> TextTertiary
                        avgMs > 5_000 -> Primary
                        else -> TextSecondary
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun SettingsDropdownRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BgSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(selectedValue, color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BgSurface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = if (option == selectedValue) Primary else TextPrimary,
                            fontWeight = if (option == selectedValue) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ProviderSettingsRow(
    providerInfo: com.novastream.app.data.provider.ProviderInfo,
    isSelected: Boolean,
    context: Context,
    onSelect: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) Primary.copy(alpha = 0.15f) else BgSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSelected) Icons.Default.Check else Icons.Default.Stream,
                contentDescription = null,
                tint = if (isSelected) Primary else TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                providerInfo.displayName,
                color = TextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "${providerInfo.contentLabel(context)} · ${providerInfo.hostLabel}",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (isSelected) {
            Text(
                stringResource(R.string.active),
                color = Primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(BgSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextTertiary)
    }
}

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = TextTertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun StatCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BgSurface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = Primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .tvFocusIfNeeded(cornerRadius = 12.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BgSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ClickableSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .tvFocusIfNeeded(cornerRadius = 12.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BgSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}
